package org.fossify.home.dikciz

import android.content.res.AssetManager
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject
import org.fossify.home.dikciz.DikcizMcpControlPlane.Companion.KEY_DESCRIPTION
import org.fossify.home.dikciz.DikcizMcpControlPlane.Companion.KEY_META
import org.fossify.home.dikciz.DikcizMcpControlPlane.Companion.KEY_MIME_TYPE
import org.fossify.home.dikciz.DikcizMcpControlPlane.Companion.KEY_NAME
import org.fossify.home.dikciz.DikcizMcpControlPlane.Companion.KEY_RESULT_TYPE
import org.fossify.home.dikciz.DikcizMcpControlPlane.Companion.KEY_TEXT
import org.fossify.home.dikciz.DikcizMcpControlPlane.Companion.KEY_TITLE
import org.fossify.home.dikciz.DikcizMcpControlPlane.Companion.RESULT_TYPE_COMPLETE

internal class DikcizMcpResources(private val assets: AssetManager) {
    private val sections by lazy {
        DEFINITIONS.map { definition ->
            val text = assets.open(ASSET_PREFIX + definition.first + MARKDOWN_EXTENSION)
                .bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val bytes = text.toByteArray(StandardCharsets.UTF_8).size
            check(bytes in 1..MAXIMUM_SECTION_BYTES) { MESSAGE_INVALID_ASSET }
            Section(definition.first, definition.second, text, bytes)
        }
    }

    fun list(params: JSONObject): JSONObject {
        validateFields(params, setOf(KEY_CURSOR))
        val offset = if (params.has(KEY_CURSOR)) cursorOffset(params.opt(KEY_CURSOR)) else 0
        val page = sections.drop(offset).take(PAGE_SIZE)
        val result = complete().put(KEY_RESOURCES, JSONArray().apply {
            page.forEach { put(it.descriptor()) }
        })
        val next = offset + page.size
        if (next < sections.size) {
            result.put(KEY_NEXT_CURSOR, CURSOR_PREFIX + next)
        }
        return result
    }

    fun read(params: JSONObject): JSONObject {
        validateFields(params, setOf(KEY_URI))
        val uri = params.opt(KEY_URI)
        if (uri !is String || uri.isBlank() || uri.length > MAXIMUM_URI_CHARACTERS) {
            throw DikcizMcpResourceException(MESSAGE_INVALID_URI)
        }
        val section = sections.firstOrNull { it.uri == uri }
            ?: throw DikcizMcpResourceException(MESSAGE_NOT_FOUND, uri)
        return complete().put(KEY_CONTENTS, JSONArray().put(JSONObject()
            .put(KEY_URI, section.uri)
            .put(KEY_MIME_TYPE, MIME_TYPE_MARKDOWN)
            .put(KEY_TEXT, section.text)))
    }

    fun templates(params: JSONObject): JSONObject {
        validateFields(params, emptySet())
        return complete().put(KEY_RESOURCE_TEMPLATES, JSONArray())
    }

    private fun cursorOffset(value: Any?): Int {
        if (value !is String || value.length > MAXIMUM_CURSOR_CHARACTERS || !value.startsWith(CURSOR_PREFIX)) {
            throw DikcizMcpResourceException(MESSAGE_INVALID_CURSOR)
        }
        val offset = value.removePrefix(CURSOR_PREFIX).toIntOrNull()
        if (offset == null || value != CURSOR_PREFIX + offset || offset !in 0..sections.size || offset % PAGE_SIZE != 0) {
            throw DikcizMcpResourceException(MESSAGE_INVALID_CURSOR)
        }
        return offset
    }

    private fun validateFields(params: JSONObject, allowed: Set<String>) {
        if (params.keys().asSequence().any { it != KEY_META && it !in allowed }) {
            throw DikcizMcpResourceException(MESSAGE_UNEXPECTED_FIELD)
        }
    }

    private fun complete() = JSONObject().put(KEY_RESULT_TYPE, RESULT_TYPE_COMPLETE)

    private data class Section(val slug: String, val title: String, val text: String, val bytes: Int) {
        val uri = URI_PREFIX + slug

        fun descriptor() = JSONObject()
            .put(KEY_URI, uri)
            .put(KEY_NAME, slug)
            .put(KEY_TITLE, title)
            .put(KEY_DESCRIPTION, title)
            .put(KEY_MIME_TYPE, MIME_TYPE_MARKDOWN)
            .put(KEY_SIZE, bytes)
    }

    companion object {
        const val METHOD_LIST = "resources/list"
        const val METHOD_READ = "resources/read"
        const val METHOD_TEMPLATES = "resources/templates/list"
        const val KEY_RESOURCES = "resources"
        const val KEY_URI = "uri"
        const val JSON_RPC_INVALID_PARAMS = -32602
        const val INSTRUCTIONS = "Read dikciz://docs/index for short task guides and recipes. List resources with resources/list; follow nextCursor. Read only needed sections with resources/read. Discover current command schemas with tools/list."
        private const val URI_PREFIX = "dikciz://docs/"
        private const val ASSET_PREFIX = "mcp/"
        private const val MARKDOWN_EXTENSION = ".md"
        private const val MIME_TYPE_MARKDOWN = "text/markdown"
        private const val PAGE_SIZE = 8
        private const val MAXIMUM_SECTION_BYTES = 4096
        private const val MAXIMUM_URI_CHARACTERS = 256
        private const val MAXIMUM_CURSOR_CHARACTERS = 64
        private const val CURSOR_PREFIX = "docs1:"
        private const val MESSAGE_INVALID_CURSOR = "Invalid documentation cursor"
        private const val MESSAGE_INVALID_ASSET = "Invalid packaged documentation size"
        private const val MESSAGE_INVALID_URI = "uri must be a non-empty string of at most 256 characters"
        private const val MESSAGE_NOT_FOUND = "Resource not found"
        private const val MESSAGE_UNEXPECTED_FIELD = "Resource parameters contain an unsupported field"
        private const val KEY_CURSOR = "cursor"
        private const val KEY_NEXT_CURSOR = "nextCursor"
        private const val KEY_CONTENTS = "contents"
        private const val KEY_RESOURCE_TEMPLATES = "resourceTemplates"
        private const val KEY_SIZE = "size"
        private val DEFINITIONS = listOf(
            "index" to "Start here",
            "guides/connect" to "Connect to the phone",
            "guides/discovery" to "Discover the running build",
            "guides/permissions" to "Android permissions",
            "guides/ui" to "Inspect and control UI",
            "guides/apps" to "Apps and running tasks",
            "guides/pages" to "Pages and layout",
            "guides/html" to "HTML widgets",
            "guides/lua" to "Lua automation",
            "guides/events" to "Custom events",
            "guides/files" to "Files and APKs",
            "guides/shell" to "Shell and streaming",
            "guides/logging" to "Logs and diagnosis",
            "guides/security" to "Security boundaries",
            "guides/native-data" to "Direct Android data and system APIs",
            "recipes/inspect-ui" to "Inspect and act",
            "recipes/launch-app" to "Open an app reliably",
            "recipes/html-item" to "Edit one HTML card",
            "recipes/event-setup" to "Custom-event pipeline setup",
            "recipes/event-lua" to "Custom-event Lua source",
            "recipes/event-widget" to "Custom-event HTML source",
            "recipes/install-script" to "Install a Lua script",
            "recipes/install-widget" to "Install an HTML widget",
            "recipes/install-apk" to "Transfer and install an APK",
            "recipes/stream-logs" to "Stream logcat",
            "recipes/restart" to "Restart and reconnect",
            "recipes/listener" to "Change listener endpoints",
            "recipes/contact-sms" to "Look up a contact and send tracked SMS",
        )
    }
}

internal class DikcizMcpResourceException(message: String, val uri: String? = null) : Exception(message)
