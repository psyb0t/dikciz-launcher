package org.fossify.home.dikciz

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONObject

internal object DikcizFileTranscription {
    const val TYPE = "speechTranscribeFile"
    private const val MAXIMUM_FILE_BYTES = 33554432L
    private const val MAXIMUM_RESPONSE_BYTES = 262144
    private const val DEFAULT_TIMEOUT = 30000L
    private val FORMATS = mapOf("wav" to "audio/wav", "mp3" to "audio/mpeg", "mp4" to "audio/mp4",
        "mpeg" to "audio/mpeg", "mpga" to "audio/mpeg", "m4a" to "audio/mp4", "ogg" to "audio/ogg",
        "webm" to "audio/webm", "flac" to "audio/flac")
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).build()

    fun execute(context: Context, request: JSONObject): JSONObject {
        val profile = DikcizChatSettings.validate(DikcizChatRuntime.get(context).store.profile(request.getString("profileId")))
        if (profile.getString("protocol") != "openai") {
            DikcizNativeCommands.failure("transcription_provider_unsupported", "Choose an OpenAI-compatible provider with an audio transcription endpoint")
        }
        val file = DikcizFileCommands.file(JSONObject().put("path", request.getString("path")))
        if (!file.isFile || file.length() !in 1..MAXIMUM_FILE_BYTES) {
            DikcizNativeCommands.invalid("Audio file must contain 1 byte to 32 MiB")
        }
        val extension = file.extension.lowercase()
        val mime = FORMATS[extension] ?: DikcizNativeCommands.invalid("Unsupported audio file extension")
        val model = request.getString("model")
        if (model.isBlank() || model.any(Char::isISOControl)) DikcizNativeCommands.invalid("Choose a transcription model")
        val language = request.optString("language")
        if (language.isNotEmpty() && !Regex("[a-z]{2}").matches(language)) DikcizNativeCommands.invalid("Use a two-letter ISO 639-1 language")
        val length = file.length()
        val body = object : RequestBody() {
            override fun contentType() = mime.toMediaType()
            override fun contentLength() = length
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) {
                var copied = 0L
                file.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw IOException("Transcription upload interrupted")
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > length) throw IOException("Audio input changed")
                        sink.write(buffer, 0, count)
                    }
                }
                if (copied != length) throw IOException("Audio input changed")
            }
        }
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", model).addFormDataPart("response_format", "json")
            .addFormDataPart("file", "audio." + extension, body)
        if (language.isNotEmpty()) multipart.addFormDataPart("language", language)
        val key = profile.optString("apiKey")
        val outgoing = Request.Builder().url(profile.getString("baseUrl").trimEnd('/') + "/audio/transcriptions")
            .post(multipart.build()).apply { if (key.isNotEmpty()) header("Authorization", "Bearer " + key) }.build()
        profile.remove("apiKey")
        val timeout = request.optLong("timeoutMilliseconds", DEFAULT_TIMEOUT)
        val transport = client.newBuilder().callTimeout(timeout, TimeUnit.MILLISECONDS)
            .connectTimeout(minOf(timeout, DEFAULT_TIMEOUT), TimeUnit.MILLISECONDS)
            .readTimeout(timeout, TimeUnit.MILLISECONDS).writeTimeout(timeout, TimeUnit.MILLISECONDS).build()
        try {
            transport.newCall(outgoing).execute().use { response ->
                if (!response.isSuccessful) {
                    DikcizNativeCommands.failure("transcription_provider_rejected", "Transcription provider returned HTTP " + response.code)
                }
                val output = ByteArrayOutputStream()
                val input = response.body?.byteStream() ?: DikcizNativeCommands.failure("transcription_response_invalid", "Provider returned no transcription")
                input.use {
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = it.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > MAXIMUM_RESPONSE_BYTES) {
                            DikcizNativeCommands.failure("transcription_response_too_large", "Provider transcription exceeds 256 KiB")
                        }
                        output.write(buffer, 0, count)
                    }
                }
                val document = try { JSONObject(output.toString(Charsets.UTF_8.name())) }
                    catch (_: org.json.JSONException) { DikcizNativeCommands.failure("transcription_response_invalid", "Provider returned invalid JSON") }
                val text = document.opt("text") as? String
                    ?: DikcizNativeCommands.failure("transcription_response_invalid", "Provider response needs text")
                return JSONObject().put("state", "transcribed").put("text", text).put("model", model)
                    .put("profileId", request.getString("profileId")).put("source", "file")
                    .put("uploadedBytes", length).put("microphoneUsed", false)
            }
        } catch (_: IOException) {
            DikcizNativeCommands.failure("transcription_transport_failed", "Transcription upload or response failed. Check provider connectivity and timeout.")
        }
    }

    private fun text(size: Int) = JSONObject().put("type", "string").put("maxLength", size)
    val DEFINITION = DikcizNativeCommands.Definition(TYPE, "dikciz_speech_transcribe_file",
        "Upload one audio file to the explicitly selected OpenAI-compatible provider's audio/transcriptions endpoint. Uses its encrypted saved API key. Requires profileId, model and path. Sends file contents outside the phone; never opens the microphone. Provider support and usage charges apply. Requires the matching explicit operation grant for scoped remote clients.",
        mapOf("path" to text(4096), "profileId" to text(80), "model" to text(256), "language" to text(2),
            "timeoutMilliseconds" to JSONObject().put("type", "integer").put("minimum", 1000).put("maximum", 300000).put("default", DEFAULT_TIMEOUT)),
        setOf("path", "profileId", "model"), readOnly = false)
}
