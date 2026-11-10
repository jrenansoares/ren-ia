package br.com.projetoia.app.data.repository

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import br.com.projetoia.app.data.datastore.SettingsStore
import br.com.projetoia.app.data.db.dao.WorkspaceDAO
import br.com.projetoia.app.data.db.entity.WorkspaceEntity
import br.com.projetoia.app.data.model.SshWorkspaceDraft
import br.com.projetoia.app.data.model.SshWorkspaceSettings
import br.com.projetoia.app.utils.JsonInstant
import br.com.projetoia.app.utils.SecretCipher
import me.rerere.workspace.RootfsInstallProgress
import me.rerere.workspace.RootfsInstaller
import me.rerere.workspace.WorkspaceBackend
import me.rerere.workspace.WorkspaceCommandResult
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceLayout
import me.rerere.workspace.WorkspaceManager
import me.rerere.workspace.WorkspaceShellStatus
import me.rerere.workspace.WorkspaceStorageArea
import me.rerere.workspace.WorkspaceType
import me.rerere.workspace.ProjectArchive
import me.rerere.workspace.ProjectArchiveInfo
import me.rerere.workspace.ProjectArchiveProgress
import me.rerere.workspace.ssh.SshAuthType
import me.rerere.workspace.ssh.SshConnectionConfig
import me.rerere.workspace.ssh.SshConnectionPool
import me.rerere.workspace.ssh.SshProbeResult
import me.rerere.workspace.ssh.SshShell
import me.rerere.workspace.ssh.SshWorkspaceBackend
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.file.Files
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

data class InstalledRepositoryInfo(
    val workspaceId: String,
    val workspaceName: String,
    val name: String,
    val path: String,
    val remoteUrl: String?,
    val branch: String?,
)

internal fun recoverWorkspaceShellStatus(status: String, hasRootfs: Boolean, installationActive: Boolean): String = when {
    status == WorkspaceShellStatus.INSTALLING.name && !installationActive ->
        if (hasRootfs) WorkspaceShellStatus.READY.name else WorkspaceShellStatus.DISABLED.name
    status == WorkspaceShellStatus.READY.name && !hasRootfs -> WorkspaceShellStatus.DISABLED.name
    else -> status
}

class WorkspaceRepository(
    private val dao: WorkspaceDAO,
    private val manager: WorkspaceManager,
    private val rootfsInstaller: RootfsInstaller,
    private val settingsStore: SettingsStore,
    private val sshPool: SshConnectionPool,
) {
    private val activeRootfsInstalls = ConcurrentHashMap.newKeySet<String>()
    fun listFlow(): Flow<List<WorkspaceEntity>> = dao.listFlow()

    suspend fun checkIntegrity() = withContext(Dispatchers.IO) {
        val workspaces = dao.getAll()
        for (workspace in workspaces) {
            // 文件不在本机的工作区 (如远程) 没有本地目录和 Rootfs
            if (!workspace.capabilities().localFiles) continue
            val dir = manager.workspaceDir(workspace.root)
            if (!dir.exists()) {
                // 目录缺失时不删除记录(例如恢复备份后工作区文件未随数据库一起恢复),
                // 仅标记为 BROKEN 以保留记录与助手绑定, 避免误删用户工作区
                Log.w(TAG, "Workspace directory missing, marking as broken: id=${workspace.id}, root=${workspace.root}")
                if (workspace.shellStatus != WorkspaceShellStatus.BROKEN.name) {
                    updateShellState(workspace.id, WorkspaceShellStatus.BROKEN.name)
                }
                continue
            }
            val statusName = workspace.shellStatus
            manager.ensureWorkspace(workspace.root)
            val recovered = recoverWorkspaceShellStatus(statusName, manager.hasRootfs(workspace.root), workspace.id in activeRootfsInstalls)
            if (recovered != statusName) {
                updateShellState(workspace.id, recovered)
            }
        }
    }

    suspend fun getById(id: String): WorkspaceEntity? = dao.getById(id)

    suspend fun create(name: String): WorkspaceEntity {
        val id = Uuid.random().toString()
        val now = System.currentTimeMillis()
        val finalName = name.trim().ifBlank { "Workspace" }
        require(!isNameTaken(finalName, excludeId = null)) {
            "Workspace name already exists: $finalName"
        }
        val workspace = WorkspaceEntity(
            id = id,
            name = finalName,
            root = id,
            shellStatus = if (manager.hasRootfs(id)) WorkspaceShellStatus.READY.name else WorkspaceShellStatus.DISABLED.name,
            createdAt = now,
            updatedAt = now,
            lastAccessAt = null,
        )
        manager.ensureWorkspace(workspace.root)
        dao.upsert(workspace)
        return workspace
    }

    /** 用一条临时连接测试 SSH 配置; [id] 不为空时, 表单里留空的凭据沿用该工作区已保存的值 */
    suspend fun probeSsh(id: String?, draft: SshWorkspaceDraft): SshProbeResult {
        val previous = id?.let { dao.getById(it)?.sshSettings() }
        return runInterruptible(Dispatchers.IO) {
            sshPool.probe(draft.toSettings(previous).toConnectionConfig())
        }
    }

    /** 新建 ([id] 为 null) 或更新 SSH 工作区 */
    suspend fun saveSsh(id: String?, draft: SshWorkspaceDraft): WorkspaceEntity {
        val existing = id?.let { dao.getById(it) ?: error("Workspace not found: $it") }
        require(existing == null || existing.isSsh()) { "Not an SSH workspace: $id" }
        val finalName = draft.name.trim().ifBlank { "Workspace" }
        require(!isNameTaken(finalName, excludeId = id)) {
            "Workspace name already exists: $finalName"
        }
        val settings = withContext(Dispatchers.IO) { draft.toSettings(existing?.sshSettings()) }
        val now = System.currentTimeMillis()
        val workspace = existing?.copy(
            name = finalName,
            sshConfig = JsonInstant.encodeToString(settings),
            updatedAt = now,
        ) ?: Uuid.random().toString().let { newId ->
            WorkspaceEntity(
                id = newId,
                name = finalName,
                root = newId,
                // 远程工作区不需要安装 Rootfs, 视为 shell 已就绪
                shellStatus = WorkspaceShellStatus.READY.name,
                createdAt = now,
                updatedAt = now,
                type = WorkspaceType.SSH.name,
                sshConfig = JsonInstant.encodeToString(settings),
            )
        }
        dao.upsert(workspace)
        withContext(Dispatchers.IO) { sshPool.close(workspace.id) }
        return workspace
    }

    suspend fun rename(id: String, name: String): Boolean {
        val workspace = dao.getById(id) ?: return false
        val finalName = name.trim().ifBlank { workspace.name }
        require(!isNameTaken(finalName, excludeId = id)) {
            "Workspace name already exists: $finalName"
        }
        dao.upsert(
            workspace.copy(
                name = finalName,
                updatedAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    /** 名字是否已被其他 workspace 占用（trim 后精确匹配，排除 [excludeId] 自身） */
    suspend fun isNameTaken(name: String, excludeId: String?): Boolean {
        val target = name.trim()
        return dao.getAll().any { it.id != excludeId && it.name.trim() == target }
    }

    suspend fun usesSharedLinux(id: String): Boolean = withContext(Dispatchers.IO) {
        dao.getById(id)?.let { !it.isSsh() && manager.usesSharedLinux(it.root) } == true
    }

    suspend fun useSharedLinux(id: String) = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        check(!workspace.isSsh())
        manager.useSharedLinux(workspace.root)
        updateShellState(workspace, WorkspaceShellStatus.READY.name)
    }

    suspend fun hasPrivateLinux(id: String): Boolean = withContext(Dispatchers.IO) {
        dao.getById(id)?.let { !it.isSsh() && WorkspaceManager.isUsableRootfs(manager.privateLinuxDir(it.root)) } == true
    }

    suspend fun usePrivateLinux(id: String) = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        check(!workspace.isSsh() && WorkspaceManager.isUsableRootfs(manager.privateLinuxDir(workspace.root)))
        manager.usePrivateLinux(workspace.root)
        updateShellState(workspace, WorkspaceShellStatus.READY.name)
    }

    suspend fun refreshSharedLinuxState() = withContext(Dispatchers.IO) {
        val available = manager.hasSharedRootfs()
        dao.getAll().filter { !it.isSsh() && manager.usesSharedLinux(it.root) }.forEach { workspace ->
            if (manager.workspaceDir(workspace.root).isDirectory) {
                val status = if (available) WorkspaceShellStatus.READY.name else WorkspaceShellStatus.DISABLED.name
                if (status != workspace.shellStatus) updateShellState(workspace, status)
            }
        }
    }

    suspend fun setShellCompatibilityMode(id: String, enabled: Boolean) {
        dao.setShellCompatibilityMode(id, enabled, System.currentTimeMillis())
    }

    suspend fun setToolApproval(id: String, toolName: String, needsApproval: Boolean): Boolean {
        val workspace = dao.getById(id) ?: return false
        val overrides = workspace.toolApprovalOverrides() + (toolName to needsApproval)
        dao.upsert(
            workspace.copy(
                toolApprovals = JsonInstant.encodeToString(overrides),
                updatedAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    suspend fun installRootfs(
        id: String,
        url: String,
        expectedSha256: String? = null,
        onProgress: (RootfsInstallProgress) -> Unit = {},
    ): Boolean {
        val workspace = dao.getById(id) ?: return false
        require(workspace.capabilities().rootfs) { "Rootfs is not available for ${workspace.type} workspaces" }
        check(activeRootfsInstalls.add(id)) { "Rootfs installation is already running" }
        try {
            updateShellState(workspace, WorkspaceShellStatus.INSTALLING.name)
            // runInterruptible 让协程取消转成线程中断, 打断 install 内阻塞的下载/解压循环
            runInterruptible(Dispatchers.IO) {
                rootfsInstaller.install(workspace.root, url, expectedSha256 = expectedSha256, onProgress = onProgress)
            }
            updateShellState(workspace, WorkspaceShellStatus.READY.name)
            return true
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                restoreShellState(workspace)
            }
            throw e
        } catch (e: InterruptedException) {
            withContext(NonCancellable) {
                restoreShellState(workspace)
            }
            throw CancellationException("Rootfs install cancelled").also { it.initCause(e) }
        } catch (e: Throwable) {
            Log.e(TAG, "installRootfs failed: workspace=${workspace.id}, root=${workspace.root}, url=$url", e)
            updateShellState(workspace, WorkspaceShellStatus.BROKEN.name)
            throw e
        } finally {
            activeRootfsInstalls.remove(id)
        }
    }

    /** 工作区在执行环境内的目录约定, 工作区不存在时返回 null */
    suspend fun layout(id: String): WorkspaceLayout? = dao.getById(id)?.let { workspace ->
        // 只需要目录约定, 不解密凭据也不建立连接
        if (workspace.isSsh()) {
            workspace.sshSettings()?.let { SshWorkspaceBackend.layoutOf(it.remoteDir) }
        } else {
            manager.layout
        }
    }

    suspend fun listFiles(
        id: String,
        area: WorkspaceStorageArea,
        path: String,
    ): List<WorkspaceFileEntry> {
        val workspace = dao.getById(id) ?: return emptyList()
        return runInterruptible(Dispatchers.IO) { backendOf(workspace).listFiles(area, path) }
    }

    suspend fun readText(
        id: String,
        path: String,
    ): String = readFromBackend(id) { it.readText(path) }

    suspend fun writeText(
        id: String,
        path: String,
        text: String,
        overwrite: Boolean,
    ): WorkspaceFileEntry = withBackend(id) { it.writeText(path, text, overwrite) }

    /**
     * 读取文本用于应用内预览/编辑, 支持两个存储区.
     * FILES 区走 [WorkspaceBackend.readText] (自带大小保护); LINUX 区通过 exportFile 读入内存,
     * 因此这里对 LINUX 区显式做大小限制, 避免大文件撑爆内存.
     */
    suspend fun readTextForPreview(
        id: String,
        area: WorkspaceStorageArea,
        path: String,
    ): String = readFromBackend(id) { backend ->
        when (area) {
            WorkspaceStorageArea.FILES -> backend.readText(path)
            WorkspaceStorageArea.LINUX -> {
                val size = backend.fileSize(area, path)
                require(size <= MAX_PREVIEW_BYTES) {
                    "文件过大, 无法预览 ($size bytes)"
                }
                ByteArrayOutputStream().use { out ->
                    backend.exportFile(area, path, out)
                    out.toString(Charsets.UTF_8.name())
                }
            }
        }
    }

    suspend fun importFile(
        id: String,
        area: WorkspaceStorageArea,
        destinationPath: String,
        fileName: String,
        inputStream: InputStream,
    ): WorkspaceFileEntry = withBackend(id) { it.importFile(area, destinationPath, fileName, inputStream) }

    /** 宿主机上可直接访问的文件; 没有本地落点的工作区返回 null */
    suspend fun resolveFile(
        id: String,
        area: WorkspaceStorageArea,
        path: String,
    ): File? = withBackend(id) { it.localFile(area, path) }

    suspend fun exportFile(
        id: String,
        area: WorkspaceStorageArea,
        path: String,
        outputStream: OutputStream,
    ) = readFromBackend(id) { it.exportFile(area, path, outputStream) }

    /** 按环境内绝对路径读取文件大小, 本地工作区支持 /workspace、bind mount 与 Rootfs 内部路径 */
    suspend fun absoluteFileSize(
        id: String,
        path: String,
    ): Long = readFromBackend(id) { it.absoluteFileSize(path) }

    /** 按环境内绝对路径解析宿主机上的文件, 不存在、不是文件或没有本地落点时返回 null */
    suspend fun resolveAbsoluteFile(
        id: String,
        path: String,
    ): File? = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: return@withContext null
        runCatching { backendOf(workspace).localAbsoluteFile(path) }
            .getOrNull()
            ?.takeIf { it.isFile }
    }

    /** 执行命令实际使用的 shell 路径 */
    suspend fun shell(id: String): String = readFromBackend(id) { it.shell() }

    /** 按环境内绝对路径写入文本; 本地工作区在 Rootfs 内执行 shell 写入, 因此和命令一样支持取消 */
    suspend fun writeAbsoluteText(
        id: String,
        path: String,
        text: String,
        overwrite: Boolean,
    ): WorkspaceFileEntry {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        return runInterruptible(Dispatchers.IO) {
            backendOf(workspace).writeAbsoluteText(path, text, overwrite)
        }
    }

    /** 按环境内绝对路径导出文件内容, 本地工作区支持 /workspace、bind mount 与 Rootfs 内部路径 */
    suspend fun exportAbsoluteFile(
        id: String,
        path: String,
        outputStream: OutputStream,
    ) = readFromBackend(id) { it.exportAbsoluteFile(path, outputStream) }

    suspend fun deleteFile(
        id: String,
        area: WorkspaceStorageArea,
        path: String,
        recursive: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: return@withContext false
        backendOf(workspace).deleteFile(area, path, recursive)
    }

    suspend fun executeCommand(
        id: String,
        command: String,
        cwd: String = "",
        timeoutMillis: Long = WorkspaceManager.DEFAULT_COMMAND_TIMEOUT_MS,
        stdin: ByteArray? = null,
        onOutput: ((String, Boolean) -> Unit)? = null,
    ): WorkspaceCommandResult {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        // runInterruptible 让协程取消转化为线程中断，从而打断阻塞的 Process.waitFor 并杀掉进程
        return runInterruptible(Dispatchers.IO) {
            backendOf(workspace).executeCommand(command, cwd, timeoutMillis, stdin, onOutput)
        }
    }

    /** Scans every local project for Git checkouts, including repositories cloned from a project chat. */
    suspend fun discoverGitRepositories(): List<InstalledRepositoryInfo> = withContext(Dispatchers.IO) {
        val results = mutableListOf<InstalledRepositoryInfo>()
        dao.getAll().filter { !it.isSsh() }.forEach { workspace ->
            val roots = listOf(
                manager.filesDir(workspace.root) to "/workspace",
                manager.homeDir(workspace.root) to "/root",
            )
            roots.forEach { (root, virtualRoot) ->
                if (root.isDirectory) {
                    results += scanGitRepositories(workspace.id, workspace.name, root, virtualRoot)
                }
            }
        }
        results.distinctBy { it.workspaceId to it.path }.sortedWith(
            compareBy({ it.workspaceName.lowercase(Locale.ROOT) }, { it.path.lowercase(Locale.ROOT) })
        )
    }

    /** Installs a public HTTPS repository into a project workspace, never through a credential-bearing URL. */
    suspend fun installGitRepository(
        id: String,
        repositoryUrl: String,
        directoryName: String,
        onOutput: ((String, Boolean) -> Unit)? = null,
    ): WorkspaceCommandResult {
        val workspace = dao.getById(id) ?: error("Project not found")
        require(!workspace.isSsh()) { "Choose a local project" }
        require(workspace.shellStatus == WorkspaceShellStatus.READY.name) { "Prepare the shared Linux environment first" }
        val url = repositoryUrl.trim()
        val parsedUrl = runCatching { URI(url) }.getOrElse { error("Enter a valid HTTPS repository URL") }
        require(
            parsedUrl.scheme.equals("https", ignoreCase = true) && !parsedUrl.host.isNullOrBlank() &&
                parsedUrl.userInfo == null && parsedUrl.rawQuery == null && parsedUrl.rawFragment == null &&
                parsedUrl.path.trim('/').isNotBlank() && url.none { it.isWhitespace() || it.isISOControl() }
        ) { "Use a public HTTPS Git URL without credentials or query parameters" }
        val folder = directoryName.trim()
        require(folder.matches(Regex("[A-Za-z0-9._-]{1,80}")) && folder != "." && folder != "..") {
            "Folder name may contain letters, numbers, dots, underscores and hyphens"
        }
        return executeCommand(
            id = id,
            command = "set -eu; command -v git >/dev/null 2>&1 || { echo 'Git is not installed in Linux' >&2; exit 127; }; test ! -e ${shellQuote(folder)} || { echo 'The destination folder already exists' >&2; exit 73; }; git clone --depth 1 -- ${shellQuote(url)} ${shellQuote(folder)}",
            cwd = "/workspace",
            timeoutMillis = 15 * 60 * 1000L,
            onOutput = onOutput,
        )
    }

    private fun scanGitRepositories(
        workspaceId: String,
        workspaceName: String,
        root: File,
        virtualRoot: String,
    ): List<InstalledRepositoryInfo> {
        val found = mutableListOf<InstalledRepositoryInfo>()
        val rootCanonical = runCatching { root.canonicalFile }.getOrNull() ?: return found
        val pending = ArrayDeque<Pair<File, Int>>()
        val visited = HashSet<String>()
        pending.add(rootCanonical to 0)
        var inspected = 0
        while (pending.isNotEmpty() && inspected < MAX_REPOSITORY_SCAN_ENTRIES) {
            val (directory, depth) = pending.removeFirst()
            val canonical = runCatching { directory.canonicalFile }.getOrNull() ?: continue
            if (!visited.add(canonical.path) || !canonical.isDirectory || Files.isSymbolicLink(directory.toPath())) continue
            inspected++
            val gitMarker = File(canonical, ".git")
            if (gitMarker.exists()) {
                val relative = runCatching { rootCanonical.toPath().relativize(canonical.toPath()).toString() }.getOrDefault("")
                val displayPath = virtualRoot + if (relative.isBlank()) "" else "/" + relative.replace(File.separatorChar, '/')
                val metadata = gitMetadata(canonical, gitMarker, rootCanonical)
                found += InstalledRepositoryInfo(
                    workspaceId = workspaceId,
                    workspaceName = workspaceName,
                    name = canonical.name.ifBlank { "Git repository" },
                    path = displayPath,
                    remoteUrl = metadata.first,
                    branch = metadata.second,
                )
                continue
            }
            if (depth >= MAX_REPOSITORY_SCAN_DEPTH) continue
            canonical.listFiles()?.asSequence()
                ?.filter { it.isDirectory && !Files.isSymbolicLink(it.toPath()) && it.name !in IGNORED_REPOSITORY_SCAN_DIRS }
                ?.forEach { pending.addLast(it to depth + 1) }
        }
        return found
    }

    private fun gitMetadata(repository: File, marker: File, scanRoot: File): Pair<String?, String?> {
        val gitDir = if (marker.isDirectory) marker else {
            val target = marker.readLines().firstOrNull()?.trim()?.removePrefix("gitdir:")?.trim().orEmpty()
            if (target.isBlank()) return null to null
            runCatching { File(repository, target).canonicalFile }.getOrNull() ?: return null to null
        }
        val rootPath = scanRoot.canonicalPath + File.separator
        if (gitDir.canonicalPath != scanRoot.canonicalPath && !gitDir.canonicalPath.startsWith(rootPath)) return null to null
        if (Files.isSymbolicLink(gitDir.toPath())) return null to null
        val config = File(gitDir, "config").takeIf { it.isFile }?.readText().orEmpty()
        val originSection = Regex("(?is)\\[remote\\s+\\\"origin\\\"]([^[]*)").find(config)?.groupValues?.getOrNull(1)
        val rawUrl = originSection?.let { Regex("(?im)^\\s*url\\s*=\\s*(\\S+)").find(it)?.groupValues?.getOrNull(1) }
        val head = File(gitDir, "HEAD").takeIf { it.isFile }?.readText()?.trim().orEmpty()
        val branch = head.removePrefix("ref: refs/heads/").takeIf { head.startsWith("ref: refs/heads/") }
        return sanitizeGitRemote(rawUrl) to branch
    }

    private fun sanitizeGitRemote(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        if (raw.startsWith("git@")) return raw.removePrefix("git@").replace(':', '/')
        return runCatching {
            val uri = URI(raw)
            if (uri.host.isNullOrBlank()) null else URI(uri.scheme, null, uri.host, uri.port, uri.path, null, null).toString()
        }.getOrNull()
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    suspend fun startLocalProcess(id: String, command: String, cwd: String = ""): Process = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found")
        check(!workspace.isSsh() && manager.hasRootfs(workspace.root)) { "A local Linux project is required" }
        manager.startLocalProcess(workspace.root, command, cwd, workspace.shellCompatibilityMode)
    }

    suspend fun exportProjectArchive(id: String, output: OutputStream, progress: (ProjectArchiveProgress) -> Unit) {
        val workspace = dao.getById(id) ?: error("Project not found")
        check(!workspace.isSsh()) { "Only local projects can be archived" }
        runInterruptible(Dispatchers.IO) {
            ProjectArchive.export(manager.filesDir(workspace.root), ProjectArchiveInfo(workspace.id, workspace.name.take(200)), output, progress)
        }
    }

    /** A restore always creates another project. Existing files and conversation bindings are preserved. */
    suspend fun importProjectArchive(input: InputStream, stagingParent: File, progress: (ProjectArchiveProgress) -> Unit): WorkspaceEntity {
        val staging = withContext(Dispatchers.IO) { java.nio.file.Files.createTempDirectory(stagingParent.toPath(), "project-restore-").toFile() }
        var created: WorkspaceEntity? = null
        try {
            val info = runInterruptible(Dispatchers.IO) { ProjectArchive.restore(input, staging, progress) }
            currentCoroutineContext().ensureActive()
            var name = info.name.take(180)
            var suffix = 1
            while (isNameTaken(name, excludeId = null)) { name = "${info.name.take(170)} ($suffix)"; suffix++ }
            val workspace = create(name).also { created = it }
            withContext(Dispatchers.IO) {
                val target = manager.filesDir(workspace.root)
                check(target.delete()) { "The new project directory must be empty" }
                java.nio.file.Files.move(staging.toPath(), target.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            }
            return workspace.also { created = null }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                created?.let { dao.deleteById(it.id); manager.deleteWorkspace(it.root) }
                staging.deleteRecursively()
            }
        }
    }

    suspend fun delete(id: String): Boolean {
        val workspace = dao.getById(id) ?: return false
        dao.deleteById(id)
        withContext(Dispatchers.IO) {
            sshPool.close(id)
            manager.deleteWorkspace(workspace.root)
        }
        cleanupAssistantReferences(id)
        return true
    }

    private suspend fun cleanupAssistantReferences(workspaceId: String) {
        settingsStore.update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.workspaceId?.toString() == workspaceId) {
                        assistant.copy(workspaceId = null)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    /** 打开 SSH 工作区的交互式 shell, 阻塞到连接和 shell 都建立好, 不能在主线程调用 */
    fun openSshShell(workspace: WorkspaceEntity, columns: Int, rows: Int): SshShell {
        val backend = backendOf(workspace) as? SshWorkspaceBackend
            ?: error("Not an SSH workspace: ${workspace.id}")
        return backend.openShell(columns, rows)
    }

    private fun backendOf(workspace: WorkspaceEntity): WorkspaceBackend =
        if (workspace.isSsh()) {
            val settings = workspace.sshSettings() ?: error("SSH workspace config is missing: ${workspace.id}")
            SshWorkspaceBackend(sshPool, workspace.id, settings.toConnectionConfig())
        } else {
            manager.backend(workspace.root, workspace.shellCompatibilityMode)
        }

    private fun SshWorkspaceSettings.toConnectionConfig() = SshConnectionConfig(
        host = host,
        port = port,
        username = username,
        authType = authType,
        password = encryptedPassword?.let(::decryptSecret),
        privateKey = encryptedPrivateKey?.let(::decryptSecret),
        passphrase = encryptedPassphrase?.let(::decryptSecret),
        remoteDir = remoteDir,
        hostKey = hostKey,
    )

    /**
     * 表单转成持久化配置并加密凭据。编辑时留空的凭据沿用 [previous] 的密文;
     * 私钥口令跟随私钥: 换了新私钥就以表单为准, 沿用旧私钥时口令留空也沿用。
     */
    private fun SshWorkspaceDraft.toSettings(previous: SshWorkspaceSettings?): SshWorkspaceSettings {
        val remoteDir = remoteDir.trim().trimEnd('/').ifEmpty { "/" }
        require(remoteDir.startsWith("/")) { "Remote directory must be an absolute path" }
        require(host.isNotBlank()) { "Host is required" }
        require(username.isNotBlank()) { "Username is required" }
        require(port in 1..65535) { "Invalid port: $port" }
        val keepPrivateKey = privateKey.isBlank()
        return SshWorkspaceSettings(
            host = host.trim(),
            port = port,
            username = username.trim(),
            authType = authType,
            remoteDir = remoteDir,
            hostKey = hostKey,
            encryptedPassword = if (authType == SshAuthType.PASSWORD) {
                password.takeIf { it.isNotEmpty() }?.let(SecretCipher::encrypt) ?: previous?.encryptedPassword
            } else {
                null
            },
            encryptedPrivateKey = if (authType == SshAuthType.PRIVATE_KEY) {
                privateKey.takeIf { it.isNotBlank() }?.let { SecretCipher.encrypt(it.trim()) }
                    ?: previous?.encryptedPrivateKey
            } else {
                null
            },
            encryptedPassphrase = when {
                authType != SshAuthType.PRIVATE_KEY -> null
                passphrase.isNotEmpty() -> SecretCipher.encrypt(passphrase)
                keepPrivateKey -> previous?.encryptedPassphrase
                else -> null
            },
        )
    }

    private fun decryptSecret(value: String): String = try {
        SecretCipher.decrypt(value)
    } catch (e: Exception) {
        throw IOException(
            "Saved SSH credentials cannot be decrypted (for example after restoring a backup on another device). " +
                "Re-enter them in the workspace settings.",
            e,
        )
    }

    /**
     * 读操作: 取消时中断线程, 远程读取卡在网络上时也能及时返回。
     * 中途放弃没有副作用; 写操作被打断会留下写了一半的文件, 走 [withBackend] 照常执行完。
     */
    private suspend fun <T> readFromBackend(id: String, block: (WorkspaceBackend) -> T): T {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        return try {
            runInterruptible(Dispatchers.IO) { block(backendOf(workspace)) }
        } catch (e: Exception) {
            // 被中断的读取会抛出 JSch 包装过的 IO 异常, 已取消时统一按取消处理, 免得被当成连接失败
            currentCoroutineContext().ensureActive()
            throw e
        }
    }

    private suspend fun <T> withBackend(id: String, block: (WorkspaceBackend) -> T): T =
        withContext(Dispatchers.IO) {
            val workspace = dao.getById(id) ?: error("Workspace not found: $id")
            block(backendOf(workspace))
        }

    private suspend fun restoreShellState(workspace: WorkspaceEntity) {
        updateShellState(workspace.id, workspace.shellStatus)
    }

    private suspend fun updateShellState(
        workspace: WorkspaceEntity,
        shellStatus: String,
    ) = updateShellState(workspace.id, shellStatus)

    private suspend fun updateShellState(
        workspaceId: String,
        shellStatus: String,
    ) {
        dao.updateShellStatus(
            id = workspaceId,
            shellStatus = shellStatus,
            updatedAt = System.currentTimeMillis(),
        )
    }

    companion object {
        private const val TAG = "WorkspaceRepository"
        private const val MAX_PREVIEW_BYTES = 512L * 1024
        private const val MAX_REPOSITORY_SCAN_ENTRIES = 12_000
        private const val MAX_REPOSITORY_SCAN_DEPTH = 7
        private val IGNORED_REPOSITORY_SCAN_DIRS = setOf(
            "node_modules", "vendor", "target", "build", ".gradle", ".cache", ".npm", ".pnpm-store",
            ".yarn", ".venv", "venv", "__pycache__", "proc", "sys", "dev", "tmp",
        )
    }
}
