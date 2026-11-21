package org.fossify.home.dikciz

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import java.security.MessageDigest
import kotlinx.serialization.json.JsonPrimitive

internal object DikcizChatAnthropicPrefix {
    const val METADATA_KEY = "dikcizAnthropicPrefix"

    val MODELS = setOf(
        "claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5",
        "claude-fable-5-1", "claude-fable-5-2", "claude-mythos-5-2",
    )

    data class Request(val messages: List<Message>, val prefix: String)

    // Edited instructions, tool lists and compaction invalidate signatures bound to the earlier prefix.
    fun prepare(messages: List<Message>, tools: List<ToolDescriptor>): Request {
        val digest = MessageDigest.getInstance("SHA-256")
        var prefix = digest.digest(tools.toString().toByteArray(Charsets.UTF_8))
        val prepared = buildList {
            for (message in messages) {
                val assistant = message as? Message.Assistant
                val reasoning = assistant?.parts?.any { it is MessagePart.Reasoning } == true
                val bound = assistant?.metaInfo?.metadata?.get(METADATA_KEY) as? JsonPrimitive
                val replay = if (assistant != null && reasoning && bound?.content != prefix.hex()) {
                    val visible = assistant.parts.filterNot { it is MessagePart.Reasoning }
                    if (visible.isEmpty()) continue
                    assistant.copy(parts = visible, rawResponse = null)
                } else message
                add(replay)
                digest.update(prefix)
                prefix = digest.digest(DikcizChatProvider.encode(replay).toByteArray(Charsets.UTF_8))
            }
        }
        return Request(prepared, prefix.hex())
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
}
