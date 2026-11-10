package br.com.projetoia.app.data.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.rerere.asr.ASRController
import me.rerere.asr.ASRState
import me.rerere.asr.ASRStatus
import java.util.Locale

/** System dictation without a separate API key. Offline availability is device-dependent. */
class AndroidAsrController(private val context: Context) : ASRController {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var disposed = false
    private val mutableState =
        MutableStateFlow(ASRState(isAvailable = SpeechRecognizer.isRecognitionAvailable(context)))
    override val state: StateFlow<ASRState> = mutableState

    override fun start(onTranscriptChange: (String) -> Unit) {
        main.post {
            if (disposed || mutableState.value.isRecording || !mutableState.value.isAvailable) return@post
            try {
                val speech =
                    recognizer
                        ?: if (Build.VERSION.SDK_INT >= 31 &&
                            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                        } else {
                            SpeechRecognizer.createSpeechRecognizer(context)
                        }
                recognizer = speech
                speech.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        mutableState.value =
                            mutableState.value.copy(status = ASRStatus.Listening)
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        mutableState.value =
                            mutableState.value.copy(status = ASRStatus.Stopping)
                    }
                    override fun onError(error: Int) {
                        mutableState.value =
                            mutableState.value.copy(
                                status = ASRStatus.Error,
                                errorMessage = "Não foi possível reconhecer a fala ($error). Tente novamente."
                            )
                    }
                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )?.firstOrNull().orEmpty()
                        mutableState.value = mutableState.value.copy(status = ASRStatus.Idle, transcript = text)
                        if (text.isNotBlank()) onTranscriptChange(text)
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val text = partialResults?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )?.firstOrNull().orEmpty()
                        if (text.isNotBlank()) {
                            mutableState.value = mutableState.value.copy(transcript = text)
                            onTranscriptChange(text)
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                mutableState.value = ASRState(status = ASRStatus.Connecting, isAvailable = true)
                speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                })
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(status = ASRStatus.Error, errorMessage = error.message)
            }
        }
    }

    override fun stop() {
        main.post { recognizer?.stopListening() }
    }
    override fun dispose() {
        disposed = true
        main.post {
            recognizer?.destroy()
            recognizer = null
            mutableState.value = ASRState()
        }
    }
}
