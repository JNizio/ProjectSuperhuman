package com.projectsuperhuman.next

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

internal enum class TrudySpeechInputStatus {
    IDLE,
    LISTENING,
    PROCESSING,
    NO_INPUT,
    PERMISSION_REQUIRED,
    UNAVAILABLE,
    ERROR
}

internal data class TrudySpeechInputState(
    val status: TrudySpeechInputStatus = TrudySpeechInputStatus.IDLE,
    val partialTranscript: String = "",
    val finalTranscript: String = "",
    val amplitude: Float = 0f,
    val message: String? = null,
    val resultId: Long = 0L
)

internal interface TrudySpeechInputController {
    val state: StateFlow<TrudySpeechInputState>
    fun hasRecordAudioPermission(): Boolean
    fun startListening()
    fun stopListening()
    fun cancel()
    fun acknowledgeResult()
    fun onPermissionDenied()
    fun close()
}

@Composable
internal fun rememberTrudySpeechInputController(): TrudySpeechInputController {
    val context = LocalContext.current.applicationContext
    val controller = remember(context) { AndroidTrudySpeechInputController(context) }
    DisposableEffect(controller) {
        onDispose(controller::close)
    }
    return controller
}

internal class AndroidTrudySpeechInputController(
    private val context: Context
) : TrudySpeechInputController, RecognitionListener {
    private val mutableState = MutableStateFlow(initialState())
    override val state: StateFlow<TrudySpeechInputState> = mutableState.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var lastAmplitudeUpdateMs = 0L
    private var smoothedAmplitude = 0f

    override fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun startListening() {
        if (!hasRecordAudioPermission()) {
            mutableState.value = TrudySpeechInputState(
                status = TrudySpeechInputStatus.PERMISSION_REQUIRED,
                message = "Microphone permission is needed for voice mode."
            )
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            mutableState.value = TrudySpeechInputState(
                status = TrudySpeechInputStatus.UNAVAILABLE,
                message = "Speech recognition is unavailable on this device."
            )
            return
        }

        val activeRecognizer = recognizer ?: createRecognizer()?.also { recognizer = it }

        if (activeRecognizer == null) {
            mutableState.value = TrudySpeechInputState(
                status = TrudySpeechInputStatus.ERROR,
                message = "The microphone could not start. You can continue in text chat."
            )
            return
        }

        runCatching { activeRecognizer.cancel() }
        smoothedAmplitude = 0f
        mutableState.value = TrudySpeechInputState(status = TrudySpeechInputStatus.LISTENING)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        runCatching { activeRecognizer.startListening(intent) }.onFailure {
            mutableState.value = TrudySpeechInputState(
                status = TrudySpeechInputStatus.ERROR,
                message = "The microphone could not start. Tap it to try again."
            )
        }
    }

    override fun stopListening() {
        if (mutableState.value.status == TrudySpeechInputStatus.LISTENING) {
            mutableState.value = mutableState.value.copy(
                status = TrudySpeechInputStatus.PROCESSING,
                amplitude = 0f
            )
            runCatching { recognizer?.stopListening() }
        }
    }

    override fun cancel() {
        runCatching { recognizer?.cancel() }
        smoothedAmplitude = 0f
        mutableState.value = TrudySpeechInputState()
    }

    override fun acknowledgeResult() {
        mutableState.value = mutableState.value.copy(finalTranscript = "", message = null, amplitude = 0f)
    }

    override fun onPermissionDenied() {
        mutableState.value = TrudySpeechInputState(
            status = TrudySpeechInputStatus.PERMISSION_REQUIRED,
            message = "Microphone permission was not granted. Text chat still works normally."
        )
    }

    override fun close() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        mutableState.value = TrudySpeechInputState()
    }

    override fun onReadyForSpeech(params: Bundle?) {
        mutableState.value = mutableState.value.copy(
            status = TrudySpeechInputStatus.LISTENING,
            message = null
        )
    }

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) {
        if (mutableState.value.status != TrudySpeechInputStatus.LISTENING) return
        val now = SystemClock.uptimeMillis()
        if (now - lastAmplitudeUpdateMs < AMPLITUDE_UPDATE_INTERVAL_MS) return
        lastAmplitudeUpdateMs = now
        val raw = normaliseTrudyRmsAmplitude(rmsdB)
        smoothedAmplitude = smoothedAmplitude * .72f + raw * .28f
        mutableState.value = mutableState.value.copy(amplitude = smoothedAmplitude)
    }

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        mutableState.value = mutableState.value.copy(
            status = TrudySpeechInputStatus.PROCESSING,
            amplitude = 0f
        )
    }

    override fun onError(error: Int) {
        smoothedAmplitude = 0f
        mutableState.value = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> TrudySpeechInputState(
                status = TrudySpeechInputStatus.NO_INPUT,
                message = "I didn't catch that. Tap the microphone and try again."
            )
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> TrudySpeechInputState(
                status = TrudySpeechInputStatus.PERMISSION_REQUIRED,
                message = "Microphone permission is needed for voice mode."
            )
            else -> TrudySpeechInputState(
                status = TrudySpeechInputStatus.ERROR,
                message = speechRecognitionErrorMessage(error)
            )
        }
    }

    override fun onResults(results: Bundle?) {
        val transcript = results.bestRecognitionResult()
        if (transcript.isNullOrBlank()) {
            onError(SpeechRecognizer.ERROR_NO_MATCH)
            return
        }
        val nextId = mutableState.value.resultId + 1L
        mutableState.value = TrudySpeechInputState(
            status = TrudySpeechInputStatus.IDLE,
            finalTranscript = transcript.trim(),
            resultId = nextId
        )
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val transcript = partialResults.bestRecognitionResult()?.trim().orEmpty()
        if (transcript.isNotEmpty()) {
            mutableState.value = mutableState.value.copy(
                partialTranscript = transcript,
                message = null
            )
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun initialState(): TrudySpeechInputState = if (SpeechRecognizer.isRecognitionAvailable(context)) {
        TrudySpeechInputState()
    } else {
        TrudySpeechInputState(
            status = TrudySpeechInputStatus.UNAVAILABLE,
            message = "Speech recognition is unavailable on this device."
        )
    }

    private fun Bundle?.bestRecognitionResult(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun createRecognizer(): SpeechRecognizer? = runCatching {
        val speechRecognizer = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        speechRecognizer.also { it.setRecognitionListener(this) }
    }.getOrNull()

    private fun speechRecognitionErrorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "The microphone had trouble hearing you. Tap it to try again."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition is temporarily unavailable. Text chat still works."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The microphone is busy. Wait a moment and try again."
        SpeechRecognizer.ERROR_SERVER -> "Speech recognition could not finish. Tap the microphone to retry."
        else -> "Voice input paused. Tap the microphone to try again."
    }

    private companion object {
        const val AMPLITUDE_UPDATE_INTERVAL_MS = 50L
    }
}

internal fun normaliseTrudyRmsAmplitude(rmsDb: Float): Float =
    if (rmsDb.isFinite()) ((rmsDb + 2f) / 12f).coerceIn(0f, 1f) else 0f
