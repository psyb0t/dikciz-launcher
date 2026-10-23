package org.fossify.home.dikciz

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.ModelDownloadListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject

internal object DikcizSpeechModels {
    const val TYPE_LANGUAGES = "speechLanguages"
    const val TYPE_DOWNLOAD = "speechModelDownload"
    const val TYPE_STATUS = "speechModelStatus"
    private const val KEY_MODE = "mode"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_TIMEOUT = "timeoutMilliseconds"
    private const val KEY_SESSION = "sessionId"
    private const val KEY_STATE = "state"
    private const val KEY_PROGRESS = "progressPercent"
    private const val DEFAULT_TIMEOUT = 30_000L
    private const val CLEANUP_TIMEOUT = 5000L
    private const val MAXIMUM_LANGUAGES = 10000
    private const val MAXIMUM_LANGUAGE_LENGTH = 128
    private val main = Handler(Looper.getMainLooper())
    private val busy = AtomicBoolean()
    private var lastDownload = JSONObject().put(KEY_STATE, "idle")

    fun execute(context: Context, logger: DikcizLogger, type: String, request: JSONObject): JSONObject {
        if (type == TYPE_STATUS) return synchronized(this) {
            if (request.has(KEY_SESSION) && request.getString(KEY_SESSION) != lastDownload.optString(KEY_SESSION)) {
                DikcizNativeCommands.failure("speech_model_not_found", "Model request is no longer current")
            }
            JSONObject(lastDownload.toString())
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            DikcizNativeCommands.failure("unsupported_android_version", "Speech support queries require Android 13")
        }
        if (type == TYPE_DOWNLOAD && Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            DikcizNativeCommands.failure("unsupported_android_version", "Observed model downloads require Android 14")
        }
        if (!busy.compareAndSet(false, true)) DikcizNativeCommands.failure("hardware_busy", "A speech support/model request is already active")
        val complete = CountDownLatch(1)
        val cleaned = CountDownLatch(1)
        val active = AtomicBoolean(true)
        val terminal = AtomicBoolean()
        fun acceptTerminal() = active.get() && terminal.compareAndSet(false, true)
        val result = AtomicReference<JSONObject?>()
        val failure = AtomicReference<String?>()
        val recognizer = AtomicReference<SpeechRecognizer?>()
        val mode = request.optString(KEY_MODE, DikcizSpeechRecognition.MODE_ON_DEVICE)
        val language = request.optString(KEY_LANGUAGE, Locale.getDefault().toLanguageTag())
        if (type == TYPE_DOWNLOAD) synchronized(this) {
            lastDownload = JSONObject().put(KEY_SESSION, UUID.randomUUID().toString()).put(KEY_STATE, "requested")
                .put(KEY_MODE, mode).put(KEY_LANGUAGE, language).put(KEY_PROGRESS, JSONObject.NULL)
                .put("requestedAtMilliseconds", System.currentTimeMillis())
        }
        try {
            main.post {
                if (!active.get()) return@post
                try {
                    val offline = mode == DikcizSpeechRecognition.MODE_ON_DEVICE
                    val created = if (offline) {
                        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                            DikcizNativeCommands.failure("speech_on_device_unavailable", "No on-device recognition service is installed")
                        }
                        SpeechRecognizer.createOnDeviceSpeechRecognizer(context.applicationContext)
                    } else {
                        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                            DikcizNativeCommands.failure("speech_service_unavailable", "No recognition service is installed")
                        }
                        SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                    }
                    recognizer.set(created)
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                    if (type == TYPE_LANGUAGES) {
                        created.checkRecognitionSupport(intent, { main.post(it) }, object : RecognitionSupportCallback {
                            override fun onSupportResult(support: RecognitionSupport) {
                                if (!acceptTerminal()) return
                                try { result.set(languages(support, request).put(KEY_MODE, mode).put(KEY_LANGUAGE, language)) }
                                catch (exception: RuntimeException) {
                                    failure.set(if (exception is DikcizAutomationRequestException) exception.code else "speech_support_invalid")
                                    logger.warn("speech_support_result_invalid", mapOf("error_class" to exception.javaClass.simpleName))
                                } finally { complete.countDown() }
                            }
                            override fun onError(error: Int) {
                                if (!acceptTerminal()) return
                                failure.set(if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) "native_permission_cameraAndMicrophone" else "speech_support_error_" + error)
                                complete.countDown()
                            }
                        })
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        created.triggerModelDownload(intent, { main.post(it) }, object : ModelDownloadListener {
                            override fun onProgress(completedPercent: Int) {
                                if (!active.get() || terminal.get()) return
                                if (completedPercent !in 0..100) {
                                    if (!acceptTerminal()) return
                                    failure.set("speech_model_invalid_progress")
                                    complete.countDown()
                                    return
                                }
                                synchronized(this@DikcizSpeechModels) {
                                    lastDownload.put(KEY_STATE, "downloading").put(KEY_PROGRESS, completedPercent)
                                }
                            }
                            override fun onSuccess() = finish("available")
                            override fun onScheduled() = finish("scheduled")
                            override fun onError(error: Int) {
                                if (!acceptTerminal()) return
                                failure.set(if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) "native_permission_cameraAndMicrophone" else "speech_model_error_" + error)
                                complete.countDown()
                            }
                            private fun finish(state: String) {
                                if (!acceptTerminal()) return
                                synchronized(this@DikcizSpeechModels) {
                                    lastDownload.put(KEY_STATE, state).put("outcomeKnown", true)
                                    if (state == "available") lastDownload.put(KEY_PROGRESS, 100)
                                    result.set(JSONObject(lastDownload.toString()))
                                }
                                complete.countDown()
                            }
                        })
                    }
                } catch (exception: RuntimeException) {
                    failure.set(if (exception is DikcizAutomationRequestException) exception.code else "speech_support_start_failed")
                    logger.warn("speech_support_start_failed", mapOf("error_class" to exception.javaClass.simpleName))
                    complete.countDown()
                }
            }
            if (!complete.await(request.optLong(KEY_TIMEOUT, DEFAULT_TIMEOUT), TimeUnit.MILLISECONDS)) {
                terminal.set(true)
                failure.set(if (type == TYPE_DOWNLOAD) "speech_model_timeout" else "speech_support_timeout")
            }
            failure.get()?.let { code ->
                if (type == TYPE_DOWNLOAD) synchronized(this) {
                    lastDownload.put(KEY_STATE, if (code == "speech_model_timeout") "unknown" else "failed")
                        .put("error", code).put("outcomeKnown", false)
                }
                DikcizNativeCommands.failure(code, "Speech provider did not confirm the requested support/model outcome")
            }
            return result.get() ?: DikcizNativeCommands.failure("speech_support_empty", "Speech provider returned no support result")
        } finally {
            active.set(false)
            if (type == TYPE_DOWNLOAD && result.get() == null && failure.get() == null) synchronized(this) {
                lastDownload.put(KEY_STATE, "unknown").put("error", "speech_model_observation_interrupted").put("outcomeKnown", false)
            }
            main.post {
                try { recognizer.getAndSet(null)?.destroy() }
                catch (exception: RuntimeException) {
                    logger.warn("speech_support_cleanup_failed", mapOf("error_class" to exception.javaClass.simpleName))
                } finally {
                    busy.set(false)
                    cleaned.countDown()
                }
            }
            if (!cleaned.await(CLEANUP_TIMEOUT, TimeUnit.MILLISECONDS)) {
                logger.warn("speech_support_cleanup_pending")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun languages(support: RecognitionSupport, request: JSONObject): JSONObject {
        val rows = listOf(
            "installedOnDevice" to support.installedOnDeviceLanguages,
            "pendingOnDevice" to support.pendingOnDeviceLanguages,
            "supportedOnDevice" to support.supportedOnDeviceLanguages,
            "online" to support.onlineLanguages,
        ).flatMap { (availability, values) ->
            if (values.size > MAXIMUM_LANGUAGES || values.any { it.length > MAXIMUM_LANGUAGE_LENGTH }) {
                DikcizNativeCommands.failure("speech_support_too_large", "Speech language inventory exceeds its response limits")
            }
            values.distinct().map { language -> JSONObject().put(KEY_LANGUAGE, language).put("availability", availability) }
        }
        if (rows.size > MAXIMUM_LANGUAGES) DikcizNativeCommands.failure("speech_support_too_large", "Speech language inventory exceeds its response limits")
        val sorted = rows.sortedWith(compareBy({ it.getString("availability") }, { it.getString(KEY_LANGUAGE) }))
        return DikcizNativeCommands.page(sorted, request).put("total", sorted.size).put(KEY_STATE, "observed")
    }

    private val OPTIONS = mapOf(
        KEY_MODE to text(16).put("description", "onDevice by default; system explicitly selects the installed system provider."),
        KEY_LANGUAGE to text(64).put("description", "BCP-47 recognition language; inventory can depend on this request."),
        KEY_TIMEOUT to integer(1, 120000).put("default", DEFAULT_TIMEOUT),
    )
    val DEFINITIONS = listOf(
        DikcizNativeCommands.Definition(TYPE_LANGUAGES, "dikciz_speech_languages",
            "Query the selected recognition provider for paginated installed, pending, downloadable on-device and online languages. Android 13+. No microphone capture. Provider support and errors are explicit.",
            OPTIONS + mapOf("offset" to integer(0, DikcizNativeCommands.MAXIMUM_OFFSET).put("default", 0), "limit" to integer(1, DikcizNativeCommands.MAXIMUM_LIMIT).put("default", DikcizNativeCommands.DEFAULT_LIMIT))),
        DikcizNativeCommands.Definition(TYPE_DOWNLOAD, "dikciz_speech_model_download",
            "Explicitly request recognition-model download and wait for available/scheduled/error. May use network data and Android owner confirmation. Android 14+. Scheduled does not mean available. Timeout cannot cancel provider work. Poll speechModelStatus for progress while this request runs.",
            OPTIONS, setOf(KEY_LANGUAGE), readOnly = false),
        DikcizNativeCommands.Definition(TYPE_STATUS, "dikciz_speech_model_status",
            "Read current/last process-owned model request and provider progress. Does not resume observation after timeout, scheduled callback or process death.",
            mapOf(KEY_SESSION to text(36))),
    )
    val TYPES = setOf(TYPE_LANGUAGES, TYPE_DOWNLOAD, TYPE_STATUS)

    fun validate(type: String, request: JSONObject) {
        if (type != TYPE_STATUS) DikcizNativeCommands.validateRecognitionOptions(request)
        if (request.has(KEY_SESSION)) {
            val id = request.getString(KEY_SESSION)
            try {
                if (UUID.fromString(id).toString() != id) DikcizNativeCommands.invalid("Invalid model request ID")
            } catch (exception: IllegalArgumentException) {
                if (exception is DikcizAutomationRequestException) throw exception
                throw DikcizAutomationRequestException("validation_failed", "Invalid model request ID").apply { initCause(exception) }
            }
        }
    }

    private fun text(maximum: Int) = JSONObject().put("type", "string").put("maxLength", maximum)
    private fun integer(minimum: Int, maximum: Int) = JSONObject().put("type", "integer").put("minimum", minimum).put("maximum", maximum)
}
