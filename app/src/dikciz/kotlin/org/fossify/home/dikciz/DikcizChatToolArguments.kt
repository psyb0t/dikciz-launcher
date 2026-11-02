package org.fossify.home.dikciz

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONException
import org.json.JSONObject

internal object DikcizChatToolArguments {
    private const val EMPTY_OBJECT = "{}"
    private val JSON_NUMBER = Regex("""-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?""")
    private const val INVALID_ARGUMENTS_MESSAGE = "Tool arguments must match the advertised JSON object schema."
    private const val REJECTED_ARGUMENTS_KEY = "dikcizRejectedArguments"

    fun incoming(arguments: String): String = arguments.ifEmpty { EMPTY_OBJECT }

    fun parse(arguments: String): JSONObject {
        val document = objectOrNull(arguments) ?: throw JSONException(INVALID_ARGUMENTS_MESSAGE)
        return JSONObject(document.toString())
    }

    fun forExecution(call: MessagePart.Tool.Call): JSONObject {
        val arguments = parse(call.args)
        val descriptor = DikcizChatProvider.TOOLS.firstOrNull { it.name == call.tool } ?: return arguments
        val required = descriptor.requiredParameters.map { it.name }
        val allowed = required + descriptor.optionalParameters.map { it.name }
        if (required.any { !arguments.has(it) } || arguments.keys().asSequence().any { it !in allowed }) {
            throw JSONException(INVALID_ARGUMENTS_MESSAGE)
        }
        // The compact chat wrappers expose only string parameters.
        if (allowed.any { arguments.has(it) && arguments.get(it) !is String }) {
            throw JSONException(INVALID_ARGUMENTS_MESSAGE)
        }
        return arguments
    }

    fun forProvider(messages: List<Message>): List<Message> = messages.map(::forProviderMessage)

    private fun forProviderMessage(message: Message): Message {
        if (message !is Message.Assistant) return message
        var changed = false
        val parts = message.parts.map { part ->
            if (part !is MessagePart.Tool.Call) return@map part
            val normalized = incoming(part.args)
            val safe = if (objectOrNull(normalized) != null) normalized else buildJsonObject {
                put(REJECTED_ARGUMENTS_KEY, part.args)
            }.toString()
            if (safe == part.args) return@map part
            changed = true
            part.copy(args = safe)
        }
        return if (changed) message.copy(parts = parts, rawResponse = null) else message
    }

    // The JSON tree parser accepts bare words and non-JSON numbers as primitives.
    private fun validValue(element: JsonElement): Boolean = when (element) {
        is JsonObject -> element.values.all(::validValue)
        is JsonArray -> element.all(::validValue)
        is JsonPrimitive -> element.isString || element === JsonNull ||
            element.booleanOrNull != null || JSON_NUMBER.matches(element.content)
    }

    private fun objectOrNull(arguments: String): JsonObject? = try {
        (Json.parseToJsonElement(arguments) as? JsonObject)?.takeIf(::validValue)
    } catch (_: SerializationException) {
        null
    }
}
