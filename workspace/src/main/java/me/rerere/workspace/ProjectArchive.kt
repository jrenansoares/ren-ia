package me.rerere.workspace

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayOutputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class ProjectArchiveInfo(val projectId: String, val name: String, val formatVersion: Int = 1)

@Serializable
private data class ProjectArchiveManifest(
    val info: ProjectArchiveInfo,
    val links: Map<String, String> = emptyMap(),
    val executables: Set<String> = emptySet(),
)

data class ProjectArchiveProgress(val files: Int, val bytes: Long)

/** Project sources, including dotfiles, empty directories and safe relative symlinks.
 * Linux, per-project HOME, temporary files and app credentials are outside this tree.
 */
object ProjectArchive {
    private const val MANIFEST = "projeto-ia-project.json"
    private const val MAX_ENTRIES = 100_000
    private const val MAX_BYTES = 16L * 1024 * 1024 * 1024
    private const val MAX_MANIFEST = 4 * 1024 * 1024

    fun export(root: File, info: ProjectArchiveInfo, output: OutputStream, progress: (ProjectArchiveProgress) -> Unit = {}) {
        require(info.formatVersion == 1 && info.name.isNotBlank())
        UUID.fromString(info.projectId)
        val base = root.toPath().toAbsolutePath().normalize()
        require(Files.isDirectory(base) && !Files.isSymbolicLink(base))
        val links = linkedMapOf<String, String>()
        val executables = linkedSetOf<String>()
        var entries = 0; var bytes = 0L; var files = 0
        ZipOutputStream(output).use { zip ->
            Files.walkFileTree(base, object : SimpleFileVisitor<Path>() {
                private fun name(path: Path): String = base.relativize(path).toString().replace(File.separatorChar, '/')
                private fun count() {
                    checkCancelled()
                    require(++entries <= MAX_ENTRIES) { "Project archive contains too many entries" }
                }
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (dir != base) {
                        count()
                        val path = name(dir); resolve(base, path)
                        zip.putNextEntry(ZipEntry("files/$path/")); zip.closeEntry()
                    }
                    return FileVisitResult.CONTINUE
                }
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    count()
                    val path = name(file); resolve(base, path)
                    if (attrs.isSymbolicLink) {
                        val target = Files.readSymbolicLink(file).toString()
                        validateLink(base, path, target)
                        links[path] = target
                    } else {
                        require(attrs.isRegularFile) { "Unsupported special file: $path" }
                        zip.putNextEntry(ZipEntry("files/$path"))
                        Files.newInputStream(file).use { source -> copy(source, zip) { n ->
                            bytes += n; require(bytes <= MAX_BYTES) { "Project exceeds the archive size limit" }
                            progress(ProjectArchiveProgress(files, bytes))
                        } }
                        zip.closeEntry()
                        val after = Files.readAttributes(file, BasicFileAttributes::class.java)
                        check(after.size() == attrs.size() && after.lastModifiedTime() == attrs.lastModifiedTime()) { "File changed during backup: $path. Stop execution and try again." }
                        if (Files.isExecutable(file)) executables += path
                        files++; progress(ProjectArchiveProgress(files, bytes))
                    }
                    return FileVisitResult.CONTINUE
                }
            })
            val manifest = Json.encodeToString(ProjectArchiveManifest(info, links, executables)).toByteArray()
            require(manifest.size <= MAX_MANIFEST) { "Project metadata exceeds the archive size limit" }
            zip.putNextEntry(ZipEntry(MANIFEST)); zip.write(manifest); zip.closeEntry()
        }
    }

    /** Extract only into a new, empty staging tree. The caller publishes it after validation. */
    fun restore(input: InputStream, destination: File, progress: (ProjectArchiveProgress) -> Unit = {}): ProjectArchiveInfo {
        require(destination.isDirectory && destination.list().isNullOrEmpty()) { "Restore destination must be empty" }
        val base = destination.toPath().toAbsolutePath().normalize()
        val seen = hashSetOf<String>()
        var manifest: ProjectArchiveManifest? = null
        var bytes = 0L; var files = 0
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    checkCancelled()
                    val entry = zip.nextEntry ?: break
                    require(seen.add(entry.name) && seen.size <= MAX_ENTRIES + 1) { "Duplicate or excessive project entries" }
                    if (entry.name == MANIFEST) {
                        require(!entry.isDirectory)
                        val metadata = ByteArrayOutputStream().also { output -> copy(zip, output) {
                            require(output.size() <= MAX_MANIFEST) { "Project metadata too large" }
                        } }.toByteArray()
                        manifest = Json.decodeFromString<ProjectArchiveManifest>(metadata.toString(Charsets.UTF_8))
                    } else {
                        require(entry.name.startsWith("files/")) { "Not a Projeto IA project archive" }
                        val relative = entry.name.removePrefix("files/").removeSuffix("/")
                        val target = resolve(base, relative)
                        if (entry.isDirectory) Files.createDirectories(target) else {
                            require(!Files.exists(target)) { "Conflicting project entries" }
                            Files.createDirectories(target.parent)
                            Files.newOutputStream(target).use { output -> copy(zip, output) { n ->
                                bytes += n; require(bytes <= MAX_BYTES) { "Project exceeds the archive size limit" }
                                progress(ProjectArchiveProgress(files, bytes))
                            } }
                            files++; progress(ProjectArchiveProgress(files, bytes))
                        }
                    }
                    zip.closeEntry() // Verifies the CRC, including truncated/corrupt entries.
                }
            }
            val metadata = requireNotNull(manifest) { "Project manifest missing" }
            require(metadata.info.formatVersion == 1 && metadata.info.name.isNotBlank() && metadata.info.name.length <= 200) { "Unsupported project manifest" }
            UUID.fromString(metadata.info.projectId)
            require(metadata.links.size + seen.size <= MAX_ENTRIES + 1)
            // Links are created last. No ZIP entry can write through a restored link.
            metadata.links.forEach { (path, link) ->
                checkCancelled()
                val target = resolve(base, path)
                validateLink(base, path, link)
                var parent = target.parent
                while (parent != base) {
                    require(!Files.isSymbolicLink(parent)) { "A link cannot have another link as its parent" }
                    parent = parent.parent
                }
                require(!Files.exists(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)) { "Conflicting project link" }
                Files.createDirectories(target.parent)
                Files.createSymbolicLink(target, Paths.get(link))
            }
            metadata.links.keys.forEach { path ->
                require(resolve(base, path).toFile().canonicalFile.toPath().startsWith(base.toFile().canonicalFile.toPath())) { "Resolved link escapes the project" }
            }
            metadata.executables.forEach { path ->
                val file = resolve(base, path)
                require(Files.isRegularFile(file) && !Files.isSymbolicLink(file)) { "Invalid executable entry" }
                check(file.toFile().setExecutable(true, true)) { "Cannot restore executable permission" }
            }
            return metadata.info
        } catch (error: Throwable) {
            // walkFileTree does not follow links, including when cleanup follows a malformed archive.
            Files.walkFileTree(base, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult { Files.deleteIfExists(file); return FileVisitResult.CONTINUE }
                override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult { if (dir != base) Files.deleteIfExists(dir); return FileVisitResult.CONTINUE }
            })
            throw error
        }
    }

    private fun resolve(base: Path, path: String): Path {
        require(path.isNotBlank() && !path.startsWith('/') && '\\' !in path && '\u0000' !in path && path.split('/').none { it.isBlank() || it == "." || it == ".." }) { "Unsafe project archive path" }
        return base.resolve(path).normalize().also { require(it.startsWith(base) && it != base) }
    }

    private fun validateLink(base: Path, path: String, link: String) {
        val target = Paths.get(link)
        require(!target.isAbsolute && '\\' !in link && link.isNotBlank()) { "External symbolic links cannot be backed up" }
        val resolved = resolve(base, path).parent.resolve(target).normalize()
        require(resolved.startsWith(base)) { "Symbolic link escapes the project" }
    }

    private fun copy(input: InputStream, output: OutputStream, progress: (Long) -> Unit) {
        val buffer = ByteArray(64 * 1024)
        while (true) { checkCancelled(); val n = input.read(buffer); if (n < 0) break; if (n > 0) { output.write(buffer, 0, n); progress(n.toLong()) } }
    }
    private fun checkCancelled() { if (Thread.currentThread().isInterrupted) throw InterruptedException("Project archive cancelled") }
}
