package org.fossify.home.dikciz

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicParams
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicThinking
import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.collect
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject

@OptIn(kotlin.time.ExperimentalTime::class)
internal class DikcizChatProvider(private val profile: JSONObject) : AutoCloseable {
    private val isAnthropic = profile.getString("protocol") == "anthropic"
    private val transport = HttpClient(OkHttp) {
        followRedirects = false
        engine { config { followRedirects(false); followSslRedirects(false) } }
    }
    private val anthropicStream = DikcizChatAnthropicStream()
    private val factory = KtorKoogHttpClient.Factory(transport).let { if (isAnthropic) anthropicStream.factory(it) else it }
    private val baseURL = profile.getString("baseUrl").trimEnd('/').removeSuffix("/v1")
    private val timeout = ConnectionTimeoutConfig(
        requestTimeoutMillis = profile.getLong("timeoutMilliseconds"),
        connectTimeoutMillis = minOf(profile.getLong("timeoutMilliseconds"), CONNECTION_TIMEOUT_MILLISECONDS),
        socketTimeoutMillis = profile.getLong("timeoutMilliseconds"),
    )
    private val model = LLModel(
        provider = if (isAnthropic) LLMProvider.Anthropic else LLMProvider.OpenAI,
        id = profile.getString("model"),
        capabilities = listOf(LLMCapability.Completion, LLMCapability.Tools, LLMCapability.Temperature,
            LLMCapability.OpenAIEndpoint.Completions, LLMCapability.Vision.Image, LLMCapability.Thinking),
        contextLength = profile.getLong("contextTokens"),
        maxOutputTokens = profile.getLong("maxOutputTokens"),
    )
    private val client: LLMClient = if (isAnthropic) {
        AnthropicLLMClient(profile.optString("apiKey"),
            AnthropicClientSettings(baseUrl = baseURL, modelVersionsMap = mapOf(model to model.id), timeoutConfig = timeout), factory)
    } else {
        OpenAILLMClient(profile.optString("apiKey"), OpenAIClientSettings(baseUrl = baseURL, timeoutConfig = timeout), factory)
    }

    suspend fun models(): JSONArray = JSONArray(client.models().map { candidate ->
        JSONObject().put("id", candidate.id).put("contextTokens", candidate.contextLength)
            .put("maxOutputTokens", candidate.maxOutputTokens)
    })

    suspend fun stream(
        messages: List<Message>,
        onBlock: (String, Int?, String, Boolean) -> Unit,
        withTools: Boolean = true,
        maximumOutput: Int = profile.getInt("maxOutputTokens"),
    ): Message.Assistant {
        anthropicStream.reset()
        val parts = linkedMapOf<Pair<String, Int?>, MessagePart.ResponsePart>()
        val partialText = linkedMapOf<Int?, StringBuilder>()
        var metadata = ResponseMetaInfo.Empty
        var finishReason: String? = null
        var ended = false
        client.executeStreaming(
            Prompt(if (isAnthropic) messages else openAIImages(messages), "dikciz-chat", parameters(maximumOutput)),
            model,
            if (withTools) TOOLS else emptyList(),
        ).collect { frame ->
            when (frame) {
                is StreamFrame.TextDelta -> {
                    partialText.getOrPut(frame.index) { StringBuilder() }.append(frame.text)
                    onBlock("text", frame.index, frame.text, false)
                }
                is StreamFrame.TextComplete -> {
                    parts["text" to frame.index] = MessagePart.Text(frame.text)
                    onBlock("text", frame.index, frame.text, true)
                }
                is StreamFrame.ReasoningDelta -> onBlock("reasoning", frame.index, frame.text ?: frame.summary.orEmpty(), false)
                is StreamFrame.ReasoningComplete -> {
                    val reasoning = MessagePart.Reasoning(frame.content, frame.summary, frame.encrypted, frame.id)
                    parts["reasoning" to frame.index] = if (isAnthropic) anthropicStream.reasoning(frame.index, reasoning) else reasoning
                    onBlock("reasoning", frame.index, frame.content.joinToString("\n"), true)
                }
                is StreamFrame.ToolCallDelta -> onBlock("tool", frame.index, frame.content.orEmpty(), false)
                is StreamFrame.ToolCallComplete -> {
                    parts["tool" to frame.index] = MessagePart.Tool.Call(frame.id, frame.name, frame.content)
                    onBlock("tool", frame.index, JSONObject().put("id", frame.id).put("name", frame.name).put("arguments", frame.content).toString(), true)
                }
                is StreamFrame.End -> {
                    ended = true
                    metadata = frame.metaInfo
                    finishReason = frame.finishReason
                }
            }
        }
        check(ended && !finishReason.isNullOrBlank()) { "Provider stream ended without a completion reason" }
        check(!isAnthropic || anthropicStream.completed) { "Anthropic stream ended without message_stop" }
        if (isAnthropic) {
            anthropicStream.completeReasoning().forEach { (index, reasoning) ->
                parts["reasoning" to index] = reasoning
                val text = reasoning.content.joinToString("\n").ifBlank { "Thinking text was not returned by the provider." }
                onBlock("reasoning", index, text, true)
            }
        }
        partialText.forEach { (index, text) -> parts.putIfAbsent("text" to index, MessagePart.Text(text.toString())) }
        check(parts.isNotEmpty()) { "Provider returned no text or tool calls" }
        metadata = metadata.copy(modelId = model.id, metadata = buildJsonObject {
            metadata.metadata?.forEach { (name, value) -> put(name, value) }
            put("dikcizProviderIdentity", identity(profile))
        })
        val ordered = if (isAnthropic) parts.entries.sortedBy { it.key.second ?: Int.MAX_VALUE }.map { it.value } else parts.values.toList()
        return Message.Assistant(ordered, metadata, finishReason)
    }

    private fun parameters(maximumOutput: Int): LLMParams {
        if (isAnthropic) {
            val thinking = profile.optInt("thinkingTokens", 0)
            val effort = profile.optString("reasoning", "auto")
            return AnthropicParams(maxTokens = maximumOutput,
                thinking = if (thinking > 0) AnthropicThinking.Enabled(thinking) else if (effort == "none") AnthropicThinking.Disabled() else null,
                additionalProperties = if (effort !in setOf("auto", "none")) mapOf(
                    "thinking" to buildJsonObject { put("type", "adaptive") },
                    "output_config" to buildJsonObject { put("effort", effort) },
                ) else null)
        }
        val reasoning = profile.optString("reasoning", "auto")
        val additional = buildMap {
            if (profile.optString("outputTokenField", "max_completion_tokens") == "max_tokens") put("max_tokens", JsonPrimitive(maximumOutput))
            if (reasoning != "auto") put("reasoning_effort", JsonPrimitive(reasoning))
        }
        return OpenAIChatParams(
            maxTokens = if (additional.containsKey("max_tokens")) null else maximumOutput,
            additionalProperties = additional,
        )
    }

    private fun openAIImages(messages: List<Message>): List<Message> {
        val result = mutableListOf<Message>()
        val images = mutableListOf<MessagePart.RequestPart>()
        fun flushImages() {
            if (images.isEmpty()) return
            result.add(Message.User(images.toList(), RequestMetaInfo.Empty))
            images.clear()
        }
        for (message in messages) {
            if (message !is Message.User || message.parts.none { it is MessagePart.Tool.Result }) {
                flushImages()
                result.add(message)
                continue
            }
            val parts = message.parts.map { part ->
                if (part !is MessagePart.Tool.Result) return@map part
                images.addAll(part.parts.filterIsInstance<MessagePart.Attachment>())
                part.copy(parts = part.parts.filterNot { it is MessagePart.Attachment })
            }
            result.add(message.copy(parts = parts))
        }
        flushImages()
        return result
    }

    override fun close() { client.close(); transport.close() }

    companion object {
        private const val CONNECTION_TIMEOUT_MILLISECONDS = 60000L
        val JSON = Json { ignoreUnknownKeys = true }
        fun identity(profile: JSONObject): String = profile.getString("protocol") + ":" + profile.getString("baseUrl").trimEnd('/') + ":" + profile.getString("model")
        fun encode(message: Message): String = JSON.encodeToString(Message.serializer(), message)
        fun decode(document: String): Message = JSON.decodeFromString(Message.serializer(), document)
        private fun parameter(name: String, description: String) = ToolParameterDescriptor(name, description, ToolParameterType.String)
        val TOOLS = listOf(
            ToolDescriptor("phone_tools", "Discover the phone's real MCP tools and their input schemas. Search by task before calling a tool.",
                requiredParameters = listOf(parameter("query", "Case-insensitive tool name or description search; empty lists the first page.")),
                optionalParameters = listOf(parameter("offset", "Decimal offset for the next page, initially 0."))),
            ToolDescriptor("phone_call", "Call a discovered phone MCP tool. Returns its real result, errors and permission recovery. Never assume acceptance means completion.",
                requiredParameters = listOf(parameter("name", "Exact discovered MCP tool name."), parameter("arguments_json", "A JSON object matching the discovered input schema."))),
            ToolDescriptor("phone_resources", "List the phone's short MCP documentation resources and recipes.", optionalParameters = listOf(parameter("cursor", "Opaque nextCursor from the previous resource page."))),
            ToolDescriptor("phone_resource", "Read one discovered MCP documentation resource.", requiredParameters = listOf(parameter("uri", "Exact URI from phone_resources."))),
        )
    }
}
