package org.fossify.home.dikciz

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.io.IOException
import android.os.Bundle
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject

internal class DikcizSpeechOutput(context: Context) {
    private val context: Context = context.applicationContext
    private val active = AtomicReference<Utterance?>()

    private class Utterance {
        val lock = Any()
        val complete = CountDownLatch(1)
        val result = AtomicReference(STATE_PENDING)
        var engine: TextToSpeech? = null
    }

    fun voices(request: JSONObject): JSONObject {
        val engine = engine()
        try {
            val voices = engine.voices.orEmpty().sortedBy { it.name }
            return DikcizNativeCommands.page(voices, request) { voice ->
                JSONObject().put("name", voice.name).put("language", voice.locale.toLanguageTag())
                    .put("networkRequired", voice.isNetworkConnectionRequired)
                    .put("quality", voice.quality).put("latency", voice.latency)
            }.put("total", voices.size).put("defaultVoice", engine.voice?.name ?: JSONObject.NULL)
        } finally {
            engine.shutdown()
        }
    }

    fun stop(): JSONObject {
        val utterance = active.get() ?: return JSONObject().put("state", "idle")
        synchronized(utterance.lock) {
            if (!utterance.result.compareAndSet(STATE_PENDING, STATE_CANCELLED)) {
                return JSONObject().put("state", "idle")
            }
            utterance.engine?.stop()
            utterance.complete.countDown()
        }
        return JSONObject().put("state", "cancelled")
    }

    fun speak(request: JSONObject, fileOutput: Boolean = false): JSONObject {
        val text = request.getString(DikcizNativeCommands.KEY_TEXT)
        if (text.isBlank()) DikcizNativeCommands.invalid("Speech text cannot be blank")
        val utterance = Utterance()
        if (!active.compareAndSet(null, utterance)) {
            DikcizNativeCommands.failure("hardware_busy", "Speech output is already active")
        }
        var speech: TextToSpeech? = null
        var outputFile: File? = null
        var fileCompleted = false
        try {
            if (fileOutput) {
                val directory = context.getExternalFilesDir("captures")
                    ?: DikcizNativeCommands.failure("capture_storage_unavailable", "App capture storage unavailable")
                outputFile = File.createTempFile("speech-", ".wav", directory)
            }
            val output = engine()
            speech = output
            synchronized(utterance.lock) {
                utterance.engine = speech
                if (utterance.result.get() == STATE_CANCELLED) {
                    DikcizNativeCommands.failure("speech_cancelled", "Speech was cancelled")
                }
                if (request.has(KEY_LANGUAGE)) {
                    val status = output.setLanguage(Locale.forLanguageTag(request.getString(KEY_LANGUAGE)))
                    if (status < TextToSpeech.LANG_AVAILABLE) {
                        DikcizNativeCommands.failure("speech_language_unavailable", "Android speech engine cannot use the requested language")
                    }
                }
                if (request.has(KEY_VOICE)) {
                    val voice = output.voices.orEmpty().firstOrNull { it.name == request.getString(KEY_VOICE) }
                        ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_NOT_FOUND, "Speech voice not found")
                    if (output.setVoice(voice) != TextToSpeech.SUCCESS) {
                        DikcizNativeCommands.failure("speech_voice_unavailable", "Android rejected the requested voice")
                    }
                }
                if (output.setSpeechRate(request.optInt(KEY_RATE, DEFAULT_PERCENT) / PERCENT_SCALE) != TextToSpeech.SUCCESS ||
                    output.setPitch(request.optInt(KEY_PITCH, DEFAULT_PERCENT) / PERCENT_SCALE) != TextToSpeech.SUCCESS) {
                    DikcizNativeCommands.failure("speech_configuration_failed", "Android rejected speech rate or pitch")
                }
                output.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String) = Unit
                    override fun onDone(id: String) {
                        utterance.result.compareAndSet(STATE_PENDING, STATE_SPOKEN)
                        utterance.complete.countDown()
                    }
                    @Deprecated("Required by Android")
                    override fun onError(id: String) {
                        utterance.result.compareAndSet(STATE_PENDING, STATE_FAILED)
                        utterance.complete.countDown()
                    }
                })
                val id = UUID.randomUUID().toString()
                val submitted = outputFile?.let { output.synthesizeToFile(text, Bundle(), it, id) }
                    ?: output.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
                if (submitted != TextToSpeech.SUCCESS) {
                    DikcizNativeCommands.failure("speech_failed", "Android rejected speech")
                }
            }
            if (!utterance.complete.await(request.optLong("timeoutMilliseconds", DEFAULT_TIMEOUT), TimeUnit.MILLISECONDS)) {
                DikcizNativeCommands.failure("speech_timeout", "Speech did not finish before the timeout")
            }
            if (utterance.result.get() == STATE_CANCELLED) DikcizNativeCommands.failure("speech_cancelled", "Speech was cancelled")
            if (utterance.result.get() != STATE_SPOKEN) DikcizNativeCommands.failure("speech_failed", "Speech synthesis failed")
            outputFile?.let { file ->
                if (!file.isFile || file.length() == 0L) DikcizNativeCommands.failure("speech_empty_file", "Speech engine returned no audio file")
                fileCompleted = true
                return JSONObject().put("state", "synthesized").put("path", file.absolutePath)
                    .put("sizeBytes", file.length()).put("mimeType", "audio/wav")
            }
            return JSONObject().put("state", STATE_SPOKEN).put("route", "device_output").put("injectedIntoCall", false)
        } finally {
            synchronized(utterance.lock) {
                speech?.stop()
                speech?.shutdown()
                utterance.engine = null
                active.compareAndSet(utterance, null)
                if (!fileCompleted) outputFile?.let { file ->
                    if (file.exists() && !file.delete()) throw IOException("Could not remove incomplete speech output")
                }
            }
        }
    }

    private fun engine(): TextToSpeech {
        val complete = CountDownLatch(1)
        val status = AtomicReference<Int>()
        val speech = TextToSpeech(context) { code -> status.set(code); complete.countDown() }
        try {
            if (!complete.await(INIT_TIMEOUT, TimeUnit.MILLISECONDS) || status.get() != TextToSpeech.SUCCESS) {
                DikcizNativeCommands.failure("speech_unavailable", "Android speech engine is unavailable")
            }
            return speech
        } catch (exception: Exception) {
            speech.shutdown()
            throw exception
        }
    }

    companion object {
        const val TYPE_VOICES = "speechVoices"
        const val TYPE_STOP = "speechStop"
        const val KEY_LANGUAGE = "language"
        const val KEY_VOICE = "voiceName"
        const val KEY_RATE = "ratePercent"
        const val KEY_PITCH = "pitchPercent"
        private const val DEFAULT_PERCENT = 100
        private const val PERCENT_SCALE = 100f
        private const val DEFAULT_TIMEOUT = 30_000L
        private const val INIT_TIMEOUT = 10_000L
        private const val STATE_PENDING = "pending"
        private const val STATE_CANCELLED = "cancelled"
        private const val STATE_SPOKEN = "spoken"
        private const val STATE_FAILED = "failed"
    }
}
