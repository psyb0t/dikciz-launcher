package org.fossify.home.dikciz

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject

internal class DikcizSpeechRecognition(context: Context) {
    private val context: Context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val current = AtomicReference<Session?>()

    private class Session {
        val complete = CountDownLatch(1)
        val destroyed = CountDownLatch(1)
        val result = AtomicReference<JSONObject?>()
        val error = AtomicReference<String?>()
        var recognizer: SpeechRecognizer? = null
    }

    fun status(): JSONObject = onMain {
        JSONObject().put("recognitionAvailable", SpeechRecognizer.isRecognitionAvailable(context))
            .put("onDeviceAvailable", Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))
            .put("listening", current.get() != null)
            .put("defaultMode", MODE_ON_DEVICE)
    }

    fun stop(): JSONObject {
        val session = current.get() ?: return JSONObject().put("state", "idle")
        main.post { finish(session, "speech_cancelled") }
        if (!session.destroyed.await(CLEANUP_TIMEOUT, TimeUnit.MILLISECONDS)) {
            DikcizNativeCommands.failure("speech_cleanup_timeout", "Speech cancellation is still pending on Android")
        }
        return JSONObject().put("state", "cancelled")
    }

    fun recognize(request: JSONObject): JSONObject {
        DikcizNativeCommands.requirePermission(context, Manifest.permission.RECORD_AUDIO, "cameraAndMicrophone")
        val process = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(process)
        if (process.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
            DikcizNativeCommands.failure("capture_foreground_required", "Open Dikciz before microphone speech recognition")
        }
        val mode = request.optString("mode", MODE_ON_DEVICE)
        val session = Session()
        DikcizMediaCapture.acquireMicrophone()
        if (!current.compareAndSet(null, session)) {
            DikcizMediaCapture.releaseMicrophone()
            DikcizNativeCommands.failure("hardware_busy", "Speech recognition is already running")
        }
        main.post {
            if (current.get() !== session || session.error.get() != null) return@post
            try {
                val offline = mode == MODE_ON_DEVICE
                if (offline && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context))) {
                    finish(session, "speech_on_device_unavailable")
                    return@post
                }
                if (!offline && !SpeechRecognizer.isRecognitionAvailable(context)) {
                    finish(session, "speech_service_unavailable")
                    return@post
                }
                val recognizer = if (offline) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                    else SpeechRecognizer.createSpeechRecognizer(context)
                session.recognizer = recognizer
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onEvent(eventType: Int, params: Bundle) = Unit
                    override fun onPartialResults(partialResults: Bundle) = Unit
                    override fun onError(error: Int) { finish(session, errorCode(error)) }
                    override fun onResults(results: Bundle) {
                        if (current.get() !== session) return
                        val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                            .take(MAXIMUM_ALTERNATIVES).map { it.take(MAXIMUM_TRANSCRIPT) }
                        if (matches.isEmpty() || matches.first().isBlank()) {
                            finish(session, "speech_no_match")
                            return
                        }
                        val confidence = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                        session.result.set(JSONObject().put("state", "recognized").put("text", matches.first())
                            .put("alternatives", JSONArray(matches)).put("mode", mode)
                            .put("confidence", JSONArray(matches.indices.map { index ->
                                val value = confidence?.getOrNull(index)
                                if (value != null && value.isFinite() && value in 0f..1f) value.toDouble() else JSONObject.NULL
                            })))
                        finish(session)
                    }
                })
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAXIMUM_ALTERNATIVES)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, request.optString("language", Locale.getDefault().toLanguageTag()))
                recognizer.startListening(intent)
            } catch (exception: SecurityException) {
                finish(session, "native_access_denied")
            } catch (exception: RuntimeException) {
                finish(session, "speech_start_failed")
            }
        }
        try {
            if (!session.complete.await(request.optLong("timeoutMilliseconds", DEFAULT_TIMEOUT), TimeUnit.MILLISECONDS)) {
                main.post { finish(session, "speech_timeout") }
                if (!session.destroyed.await(CLEANUP_TIMEOUT, TimeUnit.MILLISECONDS)) {
                    DikcizNativeCommands.failure("speech_cleanup_timeout", "Speech timeout cleanup is still pending on Android")
                }
            }
            session.error.get()?.let { DikcizNativeCommands.failure(it, "Android speech recognition did not complete; inspect the error code before retrying") }
            return session.result.get() ?: DikcizNativeCommands.failure("speech_no_match", "Android returned no recognized speech")
        } finally {
            main.post { finish(session, "speech_cancelled") }
        }
    }

    private fun finish(session: Session, error: String? = null) {
        if (current.get() !== session) return
        try {
            if (session.result.get() == null && error != null) session.error.compareAndSet(null, error)
            session.recognizer?.destroy()
        } finally {
            session.recognizer = null
            current.compareAndSet(session, null)
            DikcizMediaCapture.releaseMicrophone()
            session.destroyed.countDown()
            session.complete.countDown()
        }
    }

    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<Result<T>>()
        val complete = CountDownLatch(1)
        main.post { result.set(runCatching(block)); complete.countDown() }
        if (!complete.await(CLEANUP_TIMEOUT, TimeUnit.MILLISECONDS)) {
            DikcizNativeCommands.failure("speech_service_timeout", "Android main thread did not answer speech discovery")
        }
        return result.get().getOrThrow()
    }

    private fun errorCode(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "speech_audio_error"
        SpeechRecognizer.ERROR_CLIENT -> "speech_client_error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "native_permission_cameraAndMicrophone"
        SpeechRecognizer.ERROR_NETWORK -> "speech_network_error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "speech_network_timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "speech_no_match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "hardware_busy"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "speech_too_many_requests"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "speech_server_disconnected"
        SpeechRecognizer.ERROR_SERVER -> "speech_server_error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speech_no_input"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "speech_language_unsupported"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "speech_language_unavailable"
        else -> "speech_android_error_" + error
    }

    companion object {
        const val MODE_ON_DEVICE = "onDevice"
        const val MODE_SYSTEM = "system"
        private const val DEFAULT_TIMEOUT = 30_000L
        private const val CLEANUP_TIMEOUT = 5000L
        private const val MAXIMUM_ALTERNATIVES = 5
        private const val MAXIMUM_TRANSCRIPT = 8192
    }
}
