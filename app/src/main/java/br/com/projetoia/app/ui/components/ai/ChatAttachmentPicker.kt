package br.com.projetoia.app.ui.components.ai

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessagePart
import br.com.projetoia.app.R
import br.com.projetoia.app.data.datastore.Settings
import br.com.projetoia.app.data.files.FileFolders
import br.com.projetoia.app.data.files.FilesManager
import br.com.projetoia.app.data.files.chatVideoMimeType
import br.com.projetoia.app.ui.components.ui.permission.PermissionCamera
import br.com.projetoia.app.ui.components.ui.permission.PermissionManager
import br.com.projetoia.app.ui.components.ui.permission.rememberPermissionState
import br.com.projetoia.app.ui.context.LocalToaster
import br.com.projetoia.app.ui.hooks.ChatInputState
import br.com.projetoia.app.utils.isAllowedFileType
import me.rerere.ui.sketch.SketchDialog
import me.rerere.ui.sketch.SketchResult
import me.rerere.ui.toast.ToastType
import org.koin.compose.koinInject
import java.io.File
import kotlin.uuid.Uuid

internal data class ChatAttachmentPickerActions(
    val onTakePicture: () -> Unit,
    val onPickImage: () -> Unit,
    val onPickVideo: () -> Unit,
    val onPickAudio: () -> Unit,
    val onPickFile: () -> Unit,
    val onSketch: () -> Unit,
)

private data class AttachmentImportProgress(val index: Int, val count: Int, val name: String, val bytes: Long = 0L)

@Composable
internal fun rememberChatAttachmentPickerActions(
    inputState: ChatInputState,
    setting: Settings,
    allowVideoAttachments: Boolean,
    onAttachmentAdded: () -> Unit,
): ChatAttachmentPickerActions {
    val context = LocalContext.current
    val resources = LocalResources.current
    val toaster = LocalToaster.current
    val filesManager: FilesManager = koinInject()
    val scope = rememberCoroutineScope()
    val importProgress = remember { MutableStateFlow<AttachmentImportProgress?>(null) }
    val importing by importProgress.collectAsStateWithLifecycle()
    var importJob by remember { mutableStateOf<Job?>(null) }

    fun importFiles(uris: List<Uri>, forceVideo: Boolean = false) {
        if (uris.isEmpty() || inputState.importingAttachments) return
        inputState.importingAttachments = true
        importJob = scope.launch {
            try {
                uris.forEachIndexed { index, uri ->
                    importProgress.value =
                        AttachmentImportProgress(
                            index + 1,
                            uris.size,
                            resources.getString(R.string.chat_input_importing_files)
                        )
                    val (fileName, mime) = withContext(Dispatchers.IO) {
                        (filesManager.getFileNameFromUri(uri) ?: "file") to
                            (filesManager.getFileMimeType(uri) ?: "application/octet-stream")
                    }
                    val videoMime =
                        chatVideoMimeType(fileName, mime) ?: if (!forceVideo && !isAllowedFileType(fileName, mime)) {
                            withContext(Dispatchers.IO) {
                                val header = runCatching {
                                    context.contentResolver.openInputStream(uri)?.use { source ->
                                        val bytes = ByteArray(64)
                                        var length = 0
                                        while (length <
                                            bytes.size) {
                                            val n = source.read(bytes, length, bytes.size - length)
                                            if (n <=
                                                0) {
                                                break
                                            }
                                            length += n
                                        }
                                        bytes.copyOf(length)
                                    }
                                }.getOrNull() ?: byteArrayOf()
                                chatVideoMimeType(fileName, mime, header)
                            }
                        } else {
                            null
                        }
                    val video = forceVideo || videoMime != null
                    when {
                        video && !allowVideoAttachments -> toaster.show(
                            resources.getString(R.string.chat_input_video_requires_programming),
                            type = ToastType.Error
                        )
                        !video && !isAllowedFileType(
                            fileName,
                            mime
                        ) -> toaster.show(
                            resources.getString(R.string.chat_input_unsupported_file_type, fileName),
                            type = ToastType.Error
                        )
                        else -> {
                            importProgress.value = AttachmentImportProgress(index + 1, uris.size, fileName)
                            try {
                                val saved = filesManager.saveManagedFromUri(
                                    FileFolders.UPLOAD,
                                    uri,
                                    fileName,
                                    videoMime ?: mime
                                ) { received ->
                                    importProgress.update { it?.copy(bytes = received) }
                                }
                                val local = filesManager.getFile(saved).toUri()
                                if (video) {
                                    inputState.addVideos(listOf(local))
                                } else {
                                    inputState.addFiles(
                                        listOf(UIMessagePart.Document(local.toString(), fileName, mime))
                                    )
                                }
                                onAttachmentAdded()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                Log.e("ChatAttachmentPicker", "Attachment import failed", error)
                                toaster.show(
                                    resources.getString(R.string.chat_input_file_read_failed, fileName),
                                    type = ToastType.Error
                                )
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("ChatAttachmentPicker", "Unable to read attachment metadata", error)
                toaster.show(resources.getString(R.string.chat_input_import_failed), type = ToastType.Error)
            } finally {
                inputState.importingAttachments = false
                importProgress.value = null
            }
        }
        importJob?.invokeOnCompletion {
            inputState.importingAttachments = false
            importProgress.value = null
        }
    }

    importing?.let { progress ->
        AlertDialog(
            onDismissRequest = { importJob?.cancel() },
            title = { Text(resources.getString(R.string.chat_input_importing_files)) },
            text = {
                Column {
                    Text(progress.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(resources.getString(
                        R.string.chat_input_import_progress,
                        progress.index,
                        progress.count,
                        progress.bytes / 1_048_576.0
                    ))
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { importJob?.cancel() }
                , shapes = androidx.compose.material3.ButtonDefaults.shapes(shape = androidx.compose.ui.graphics.RectangleShape, pressedShape = androidx.compose.ui.graphics.RectangleShape),
                    elevation = null,) { Text(resources.getString(R.string.cancel)) }
            },
        )
    }
    val cameraPermission = rememberPermissionState(PermissionCamera)
    PermissionManager(permissionState = cameraPermission)

    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
    var cameraOutputFile by remember { mutableStateOf<File?>(null) }
    val launchCameraCrop = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            inputState.addImages(filesManager.createChatFilesByContents(listOf(croppedUri)))
            onAttachmentAdded()
        },
        onCleanup = {
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    )
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captureSuccessful ->
        if (captureSuccessful && cameraOutputUri != null) {
            if (setting.displaySetting.skipCropImage) {
                inputState.addImages(filesManager.createChatFilesByContents(listOf(cameraOutputUri!!)))
                cameraOutputFile?.delete()
                cameraOutputFile = null
                cameraOutputUri = null
                onAttachmentAdded()
            } else {
                launchCameraCrop(cameraOutputUri!!)
            }
        } else {
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    }
    val onTakePicture: () -> Unit = {
        if (cameraPermission.allRequiredPermissionsGranted) {
            cameraOutputFile = context.cacheDir.resolve("camera_${Uuid.random()}.jpg")
            cameraOutputUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cameraOutputFile!!
            )
            cameraLauncher.launch(cameraOutputUri!!)
        } else {
            cameraPermission.requestPermissions()
        }
    }

    val launchImageCrop = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            inputState.addImages(filesManager.createChatFilesByContents(listOf(croppedUri)))
            onAttachmentAdded()
        },
    )
    val imagePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            if (selectedUris.isNotEmpty()) {
                Log.d("ImagePickButton", "Selected URIs: $selectedUris")
                if (!setting.displaySetting.skipCropImage && selectedUris.size == 1) {
                    launchImageCrop(selectedUris.first())
                } else {
                    inputState.addImages(filesManager.createChatFilesByContents(selectedUris))
                    onAttachmentAdded()
                }
            } else {
                Log.d("ImagePickButton", "No images selected")
            }
        }

    val videoPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            importFiles(selectedUris, forceVideo = true)
        }

    val audioPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            if (selectedUris.isNotEmpty()) {
                inputState.addAudios(filesManager.createChatFilesByContents(selectedUris))
                onAttachmentAdded()
            }
        }

    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            importFiles(uris)
        }

    var showSketch by remember { mutableStateOf(false) }
    if (showSketch) {
        SketchDialog(
            onDismiss = { showSketch = false },
            onConfirm = { result ->
                showSketch = false
                scope.launch {
                    inputState.addImages(listOfNotNull(filesManager.createChatFileBySketch(result)))
                    onAttachmentAdded()
                }
            },
        )
    }

    return ChatAttachmentPickerActions(
        onTakePicture = onTakePicture,
        onPickImage = { imagePickerLauncher.launch("image/*") },
        onPickVideo = { videoPickerLauncher.launch("video/*") },
        onPickAudio = { audioPickerLauncher.launch("audio/*") },
        onPickFile = { filePickerLauncher.launch(arrayOf("*/*")) },
        onSketch = { showSketch = true },
    )
}

/** 把画板的结果存成聊天附件。 */
internal suspend fun FilesManager.createChatFileBySketch(result: SketchResult): Uri? = withContext(Dispatchers.IO) {
    createChatFilesByByteArrays(
        byteArrays = listOf(result.encode()),
        displayName = "sketch.${result.extension}",
        mimeType = result.mimeType,
    ).firstOrNull()
}
