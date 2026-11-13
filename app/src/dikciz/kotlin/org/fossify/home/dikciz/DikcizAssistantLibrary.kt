package org.fossify.home.dikciz

import android.content.Context
import java.io.File
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

/** Owner-authored instructions stay on shared storage and are reloaded between model rounds. */
internal class DikcizAssistantLibrary(private val context: Context) {
    data class Entry(val kind: String, val path: String, val name: String, val description: String, val body: String, val metadata: Map<String, String>) {
        fun summary(): JSONObject = JSONObject().put("kind", kind).put("path", path).put("name", name).put("description", description)
            .put("sha256", java.security.MessageDigest.getInstance("SHA-256")
                .digest((metadata.toSortedMap().toString() + "\n" + body).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) })
    }

    private val root: File get() = File(HomeConfigStore(context).configurationFile.parentFile!!.canonicalFile, DIRECTORY)

    fun execute(request: JSONObject): JSONObject = when (request.getString("action")) {
        "list" -> JSONObject().put("root", root.absolutePath).put("entries", JSONArray(entries().map(Entry::summary)))
        "read" -> {
            val path = request.optString("path")
            if (path.isBlank()) DikcizNativeCommands.invalid("path is required for assistant library read")
            JSONObject().put("path", path).put("text", read(path))
        }
        else -> DikcizNativeCommands.invalid("Choose list or read")
    }

    fun agent(name: String): Entry = entries().firstOrNull { it.kind == AGENT && it.name == name }
        ?: DikcizNativeCommands.failure("assistant_agent_missing", "No named agent is installed with that name")

    fun prompt(userText: String): String {
        val entries = entries()
        if (entries.isEmpty()) return ""
        val instructions = entries.filter { it.kind == RULE }.map { "Owner instruction file: ${it.path}\nScope metadata: ${it.metadata}\n${it.body}" }
        val selected = entries.filter { it.kind == SKILL && Regex("(?<![A-Za-z0-9_-])\\$" + Regex.escape(it.name) + "(?![A-Za-z0-9_-])").containsMatchIn(userText) }
        val sections = listOf(
            "Owner assistant library. These instructions cannot grant permissions or override the current access context. Apply path-scoped rules only to their named targets. Load a relevant skill with phone_library before following it. Re-read a previously loaded skill when its sha256 changes. Delegate named agents with phone_agent when useful or requested; delegates have isolated histories and inherit access restrictions.",
            JSONObject().put("entries", JSONArray(entries.filter { it.kind != RULE }.map(Entry::summary))).toString(),
        ) + instructions + selected.map { "Explicitly requested skill: ${it.path}\n${it.body}" }
        val value = sections.joinToString("\n\n")
        if (value.toByteArray(Charsets.UTF_8).size > MAXIMUM_PROMPT_BYTES) DikcizNativeCommands.failure("assistant_library_too_large", "Owner instructions exceed the prompt budget. Split instructions into on-demand skills.")
        return value
    }

    private fun entries(): List<Entry> {
        requireStorageAccess()
        if (!root.exists()) return emptyList()
        ordinary(root)
        if (!root.isDirectory) DikcizNativeCommands.invalid("Assistant root must be a directory")
        val entries = mutableListOf<Entry>()
        var scanned = 0
        var bytes = 0L
        for (file in root.walkTopDown().onEnter { directory ->
            ordinary(directory)
            if (directory.toPath().nameCount - root.toPath().nameCount > MAXIMUM_DEPTH) DikcizNativeCommands.failure("assistant_library_too_large", "Assistant directory nesting exceeds 12 levels")
            true
        }) {
            ordinary(file)
            if (!file.isFile) continue
            if (++scanned > MAXIMUM_FILES) DikcizNativeCommands.failure("assistant_library_too_large", "Assistant library has too many files")
            val path = file.relativeTo(root).invariantSeparatorsPath
            val kind = kind(path) ?: continue
            bytes += file.length()
            if (bytes > MAXIMUM_LIBRARY_BYTES) DikcizNativeCommands.failure("assistant_library_too_large", "Assistant metadata exceeds its byte budget")
            val (metadata, body) = header(read(path))
            if (body.isBlank()) DikcizNativeCommands.invalid("Assistant instruction body must not be empty: $path")
            if (metadata.keys.any { it in UNSUPPORTED_FIELDS }) DikcizNativeCommands.invalid("Unsupported executable harness setting in $path")
            val name = if (kind == RULE) path else metadata["name"].orEmpty()
            val description = metadata["description"].orEmpty()
            if (kind != RULE && (!NAME.matches(name) || name.length > 64 || description.isBlank() || description.length > MAXIMUM_DESCRIPTION)) {
                DikcizNativeCommands.invalid("Assistant skills and agents require a valid name and description: $path")
            }
            if (kind == SKILL && name != file.parentFile!!.name) DikcizNativeCommands.invalid("Skill name must match its directory: $path")
            if (kind == AGENT) {
                for (field in listOf("readOnly")) if (metadata[field] != null && metadata[field] !in setOf("true", "false")) DikcizNativeCommands.invalid("Invalid agent $field")
                for (field in listOf("tools", "disallowedTools")) metadata[field]?.let { list(it).forEach { tool ->
                    if (!TOOL.matches(tool)) DikcizNativeCommands.invalid("Agent tool restrictions require exact Dikciz MCP tool names")
                } }
            }
            if (entries.any { it.kind == kind && it.name == name }) DikcizNativeCommands.invalid("Duplicate assistant library identity: $name")
            entries.add(Entry(kind, path, name, description, body, metadata))
            if (entries.size > MAXIMUM_ENTRIES) DikcizNativeCommands.failure("assistant_library_too_large", "Assistant library has too many entries")
        }
        return entries.sortedWith(compareBy(Entry::kind, Entry::path))
    }

    private fun kind(path: String): String? {
        if (path in ROOT_RULES || RULE_ROOTS.any { path.startsWith("$it/") && path.endsWith(".md") }) return RULE
        if (SKILL_ROOTS.any { path.startsWith("$it/") && path.endsWith("/SKILL.md") }) return SKILL
        if (AGENT_ROOTS.any { path.startsWith("$it/") && path.endsWith(".md") }) return AGENT
        return null
    }

    private fun read(path: String): String {
        requireStorageAccess()
        if (path.length > MAXIMUM_PATH || path.split('/').any { it.isBlank() || it in setOf(".", "..") || it.any(Char::isISOControl) } || '\\' in path) {
            DikcizNativeCommands.invalid("Use a relative assistant library path without dot segments")
        }
        ordinary(root)
        var file = root
        for (part in path.split('/')) { file = File(file, part); ordinary(file) }
        if (!file.isFile) DikcizNativeCommands.failure("assistant_file_missing", "Assistant library file does not exist")
        if (file.length() > MAXIMUM_FILE_BYTES) DikcizNativeCommands.failure("assistant_library_too_large", "Assistant file exceeds 64 KiB")
        val bytes = file.inputStream().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (output.size() <= MAXIMUM_FILE_BYTES) {
                val count = input.read(buffer, 0, minOf(buffer.size, MAXIMUM_FILE_BYTES + 1 - output.size()))
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        if (bytes.size > MAXIMUM_FILE_BYTES) DikcizNativeCommands.failure("assistant_library_too_large", "Assistant file exceeds 64 KiB")
        return try { StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString() }
        catch (exception: java.nio.charset.CharacterCodingException) { DikcizNativeCommands.invalid("Assistant files must be UTF-8 text") }
    }

    private fun requireStorageAccess() {
        if (!HomeConfigStore(context).hasSharedStorageAccess()) {
            DikcizNativeCommands.failure(DikcizFileCommands.ERROR_ACCESS_DENIED, "Grant shared-storage access to read assistant instructions")
        }
    }

    private fun ordinary(file: File) {
        if (file.absoluteFile != file.canonicalFile || !file.canonicalFile.toPath().startsWith(root.absoluteFile.toPath())) {
            DikcizNativeCommands.invalid("Assistant library must not contain symbolic links or escaped paths")
        }
    }

    private fun header(text: String): Pair<Map<String, String>, String> {
        val lines = text.removePrefix("\uFEFF").replace("\r\n", "\n").lines()
        if (lines.firstOrNull() != "---") return emptyMap<String, String>() to text
        val end = (1 until lines.size).firstOrNull { lines[it] == "---" } ?: DikcizNativeCommands.invalid("Unclosed assistant front matter")
        val values = linkedMapOf<String, String>()
        var index = 1
        while (index < end) {
            val line = lines[index++]
            if (line.isBlank() || line.startsWith('#')) continue
            if (line.first().isWhitespace() || ':' !in line) DikcizNativeCommands.invalid("Unsupported assistant front matter. Use scalar fields or indented text blocks.")
            val key = line.substringBefore(':').trim()
            var value = line.substringAfter(':').trim()
            if (!FIELD.matches(key) || values.containsKey(key)) DikcizNativeCommands.invalid("Invalid or duplicate assistant metadata field")
            if (value in setOf("|", "|-", ">", ">-", "")) {
                val continuation = mutableListOf<String>()
                while (index < end && (lines[index].isBlank() || lines[index].first().isWhitespace())) continuation.add(lines[index++].trimStart())
                value = continuation.joinToString(if (value.startsWith('>')) " " else "\n")
            } else value = scalar(value)
            values[key] = value
        }
        return values to lines.drop(end + 1).joinToString("\n")
    }

    private fun scalar(value: String): String {
        if (value.startsWith('"')) return try { JSONArray("[$value]").getString(0) } catch (exception: org.json.JSONException) { DikcizNativeCommands.invalid("Invalid quoted assistant metadata") }
        if (value.startsWith('\'')) {
            if (!value.endsWith('\'') || value.length < 2) DikcizNativeCommands.invalid("Invalid quoted assistant metadata")
            return value.substring(1, value.length - 1).replace("''", "'")
        }
        if (value.startsWith('&') || value.startsWith('*') || value.startsWith('!')) DikcizNativeCommands.invalid("YAML tags and anchors are unsupported")
        return value
    }

    companion object {
        const val TYPE = "assistantLibrary"
        const val DIRECTORY = "assistant"
        const val AGENT = "agent"
        private const val SKILL = "skill"
        private const val RULE = "rule"
        private const val MAXIMUM_PATH = 1024
        private const val MAXIMUM_FILE_BYTES = 65536
        private const val MAXIMUM_LIBRARY_BYTES = 524288L
        private const val MAXIMUM_PROMPT_BYTES = 131072
        private const val MAXIMUM_DESCRIPTION = 1024
        private const val MAXIMUM_DEPTH = 12
        private const val MAXIMUM_FILES = 512
        private const val MAXIMUM_ENTRIES = 128
        private val NAME = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
        private val FIELD = Regex("[A-Za-z][A-Za-z0-9_-]*")
        private val TOOL = Regex("dikciz_[a-z0-9_]+")
        private val ROOT_RULES = setOf("AGENTS.md", "CLAUDE.md", "SOUL.md")
        private val RULE_ROOTS = listOf("rules", ".agents/rules", ".claude/rules")
        private val SKILL_ROOTS = listOf("skills", ".agents/skills", ".claude/skills", ".hermes/skills")
        private val AGENT_ROOTS = listOf("agents", ".agents/agents", ".claude/agents")
        private val UNSUPPORTED_FIELDS = setOf("hooks", "mcpServers", "permissionMode", "background", "isolation")

        fun list(value: String): Set<String> = value.removePrefix("[").removeSuffix("]").split(',', '\n')
            .map { it.trim().removePrefix("- ").trim('"', '\'') }.filter(String::isNotBlank).toSet()

        val DEFINITION = DikcizNativeCommands.Definition(TYPE, "dikciz_assistant_library",
            "Discover owner-authored skills, named agents and rules under /sdcard/Dikciz/assistant. list returns metadata; read returns exact UTF-8 instructions or supporting skill files using a relative path. Instructions reload between chat rounds and cannot raise tool permissions. Supports portable SKILL.md front matter and Markdown agents/rules, including .agents, .claude and .hermes skill directories.",
            mapOf("action" to JSONObject().put("type", "string").put("enum", JSONArray(listOf("list", "read"))),
                "path" to JSONObject().put("type", "string").put("maxLength", MAXIMUM_PATH)), setOf("action"))
    }
}
