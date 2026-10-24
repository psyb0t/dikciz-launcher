package org.fossify.home.dikciz

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.message.MessagePart
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import org.json.JSONObject

internal class DikcizChatAnthropicStream {
    private val signatures = mutableMapOf<Int, String>()
    private val texts = mutableMapOf<Int, StringBuilder>()
    var completed = false
        private set

    fun reset() {
        signatures.clear()
        texts.clear()
        completed = false
    }

    fun reasoning(index: Int?, part: MessagePart.Reasoning): MessagePart.Reasoning {
        return part.copy(encrypted = signatures[index] ?: part.encrypted)
    }

    fun completeReasoning(): Map<Int, MessagePart.Reasoning> {
        return texts.mapValues { (index, text) ->
            val signature = signatures[index]
            check(!signature.isNullOrBlank()) { "Anthropic thinking block has no replay signature" }
            MessagePart.Reasoning(content = listOf(text.toString()), encrypted = signature)
        }
    }

    fun factory(delegate: KoogHttpClient.Factory): KoogHttpClient.Factory {
        return object : KoogHttpClient.Factory by delegate {
            override fun create(
                clientName: String,
                baseUrl: String,
                headers: Map<String, String>,
                queryParameters: Map<String, String>,
                requestTimeoutMillis: Long,
                connectTimeoutMillis: Long,
                socketTimeoutMillis: Long,
                json: Json,
            ): KoogHttpClient {
                val client = delegate.create(clientName, baseUrl, headers, queryParameters,
                    requestTimeoutMillis, connectTimeoutMillis, socketTimeoutMillis, json)
                return StreamClient(client, this@DikcizChatAnthropicStream)
            }
        }
    }

    private fun observe(data: String): String {
        val event = JSONObject(data)
        val index = event.optInt("index", -1)
        when (event.optString("type")) {
            "content_block_start" -> {
                val block = event.getJSONObject("content_block")
                when (block.optString("type")) {
                    "thinking" -> {
                        signatures[index] = block.optString("signature")
                        texts[index] = StringBuilder(block.optString("thinking"))
                        if (!block.has("signature")) block.put("signature", "")
                    }
                    "redacted_thinking" -> {
                        val signature = REDACTED_PREFIX + block.getString("data")
                        signatures[index] = signature
                        texts[index] = StringBuilder(REDACTED_LABEL)
                        event.put("content_block", JSONObject().put("type", "thinking")
                            .put("thinking", REDACTED_LABEL).put("signature", signature))
                    }
                }
            }
            "content_block_delta" -> {
                val delta = event.getJSONObject("delta")
                if (delta.optString("type") == "signature_delta") {
                    signatures[index] = signatures[index].orEmpty() + delta.getString("signature")
                }
                if (delta.optString("type") == "thinking_delta") {
                    texts.getOrPut(index) { StringBuilder() }.append(delta.getString("thinking"))
                }
            }
            "message_stop" -> completed = true
        }
        return event.toString()
    }

    private fun request(body: String): String {
        val document = JSONObject(body)
        val messages = document.optJSONArray("messages") ?: return body
        for (messageIndex in 0 until messages.length()) {
            val content = messages.getJSONObject(messageIndex).optJSONArray("content") ?: continue
            for (index in 0 until content.length()) {
                val block = content.optJSONObject(index) ?: continue
                val signature = block.optString("signature")
                if (block.optString("type") == "thinking" && signature.startsWith(REDACTED_PREFIX)) {
                    content.put(index, JSONObject().put("type", "redacted_thinking")
                        .put("data", signature.removePrefix(REDACTED_PREFIX)))
                }
            }
        }
        return document.toString()
    }

    private class StreamClient(
        private val delegate: KoogHttpClient,
        private val stream: DikcizChatAnthropicStream,
    ) : KoogHttpClient by delegate {
        @Suppress("UNCHECKED_CAST")
        override fun <T : Any, R : Any, O : Any> sse(
            path: String,
            requestBody: T,
            requestBodyType: KClass<T>,
            dataFilter: (String?) -> Boolean,
            decodeStreamingResponse: (String) -> R,
            processStreamingChunk: (R) -> O?,
            parameters: Map<String, String>,
            headers: Map<String, String>,
        ): Flow<O> {
            check(requestBody is String) { "Anthropic requests must use the provider JSON encoder" }
            return delegate.sse(path, stream.request(requestBody) as T, requestBodyType, dataFilter,
                { data -> decodeStreamingResponse(stream.observe(data)) }, processStreamingChunk, parameters, headers)
        }
    }

    private companion object {
        // Koog 1.3.0 drops signature deltas and has no redacted-thinking content type.
        const val REDACTED_PREFIX = "dikciz:redacted:"
        const val REDACTED_LABEL = "Thinking withheld by the provider."
    }
}
