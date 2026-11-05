package org.fossify.home.dikciz

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

internal data class DikcizCustomApp(
    val manifest: JSONObject,
    val html: String,
    val css: String,
    val javascript: String,
    val source: String,
    val state: JSONObject,
) {
    val id: String get() = manifest.getString("id")
    val title: String get() = manifest.getString("title")
    val language: DikcizScriptLanguage get() = if (manifest.getString("language") == "lua") DikcizScriptLanguage.Lua else DikcizScriptLanguage.JavaScript
    val commands: Set<String> get() = manifest.getJSONArray("commands").strings().toSet()
    val events: Set<String> get() = manifest.getJSONArray("events").strings().toSet()
    val approval: String get() = MessageDigest.getInstance("SHA-256").digest(manifest.toString().toByteArray(UTF_8)).joinToString("") { "%02x".format(it) }
}

internal fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

/** Public, bounded files with transactional bundle replacement and exact archive entries. */
internal class DikcizCustomAppStore(private val context: Context) {
    private val root: File get() {
        val candidate = File(HomeConfigStore(context).configurationFile.parentFile!!.canonicalFile, "apps")
        if (candidate.canonicalFile != candidate.absoluteFile) DikcizNativeCommands.invalid("App root cannot be a symlink")
        return candidate
    }
    fun directory(id: String): File {
        if (!DikcizCustomAppCommands.ID_PATTERN.matches(id) || id in RESERVED_IDS) DikcizNativeCommands.invalid("Invalid appId")
        val base = root.canonicalFile
        val target = File(base, id)
        if (target.canonicalFile != target.absoluteFile) DikcizNativeCommands.invalid("App directory cannot be a symlink")
        return target
    }

    fun ids(): List<String> = root.listFiles()?.filter { it.isDirectory && DikcizCustomAppCommands.ID_PATTERN.matches(it.name) && File(it, MANIFEST).isFile }
        ?.map { it.name }?.sorted() ?: emptyList()

    fun manifest(id: String): JSONObject {
        val value = parseObject(readFile(File(directory(id), MANIFEST)))
        validateManifest(value)
        if (value.getString("id") != id) DikcizNativeCommands.invalid("Manifest id does not match app directory")
        return value
    }

    fun read(id: String): DikcizCustomApp {
        recover(id)
        val dir = directory(id)
        if (!File(dir, MANIFEST).isFile) DikcizNativeCommands.failure("custom_app_not_found", "Custom app is not installed")
        val manifest = parseObject(readFile(File(dir, MANIFEST)))
        validateManifest(manifest)
        if (manifest.getString("id") != id) DikcizNativeCommands.invalid("Manifest id does not match app directory")
        val source = if (manifest.getString("language") == "lua") LUA else JAVASCRIPT
        val state = File(dir, STATE).let { if (it.isFile) parseObject(readFile(it, DikcizCustomAppCommands.MAXIMUM_STATE_BYTES)) else JSONObject() }
        validateState(state)
        return DikcizCustomApp(manifest, readFile(File(dir, HTML)), readFile(File(dir, CSS)), readFile(File(dir, UI_JAVASCRIPT)), readFile(File(dir, source)), state)
    }

    fun fromRequest(request: JSONObject): DikcizCustomApp {
        val manifest = JSONObject(request.getJSONObject("manifest").toString())
        validateManifest(manifest)
        val values = listOf("html", "css", "javascript", "backgroundSource").map { request.optString(it, "") }
        values.forEach { if (it.toByteArray(UTF_8).size > DikcizCustomAppCommands.MAXIMUM_SOURCE_BYTES) DikcizNativeCommands.invalid("App source exceeds its byte budget") }
        return DikcizCustomApp(manifest, values[0], values[1], values[2], values[3], JSONObject())
    }

    fun write(app: DikcizCustomApp, preserveState: Boolean = true) {
        val dir = directory(app.id)
        recover(app.id)
        if (!dir.exists() && ids().size >= DikcizCustomAppCommands.MAXIMUM_APPS) DikcizNativeCommands.failure("custom_app_capacity", "Delete an app before installing another")
        val state = if (preserveState && dir.exists()) read(app.id).state else app.state
        val stage = File(root, ".${app.id}.staging")
        val backup = File(root, ".${app.id}.backup")
        if (stage.exists() || backup.exists()) DikcizNativeCommands.failure("custom_app_recovery_required", "An unfinished app replacement needs recovery")
        if (!stage.mkdirs()) throw java.io.IOException("Could not create app staging directory")
        try {
            if (dir.exists()) copyData(dir, stage)
            val values = files(app) + (STATE to state.toString())
            values.forEach { (name, value) -> writeFile(File(stage, name), value) }
            values.forEach { (name, value) -> check(readFile(File(stage, name)) == value) }
            if (dir.exists() && !dir.renameTo(backup)) throw java.io.IOException("Could not preserve current app")
            if (!stage.renameTo(dir)) {
                if (backup.exists() && !backup.renameTo(dir)) throw java.io.IOException("App replacement rollback failed")
                throw java.io.IOException("Could not install app")
            }
            if (backup.exists()) removeTree(backup)
        } finally {
            if (stage.exists()) removeTree(stage)
        }
    }

    private fun recover(id: String) {
        val dir = directory(id)
        val backup = File(root, ".$id.backup")
        val stage = File(root, ".$id.staging")
        if (backup.exists()) {
            if (!dir.exists()) {
                if (!backup.renameTo(dir)) throw java.io.IOException("App recovery failed")
            } else removeTree(backup)
        }
        if (stage.exists()) removeTree(stage)
    }

    fun saveState(id: String, state: JSONObject) {
        validateState(state)
        writeFile(File(directory(id), STATE), state.toString())
    }

    fun remove(id: String) {
        read(id)
        removeTree(directory(id))
    }

    fun export(id: String): JSONObject {
        val app = read(id)
        val dir = File(root, "exports")
        if (!dir.isDirectory && !dir.mkdirs()) throw java.io.IOException("App export directory unavailable")
        val file = File(dir, "${app.id}-${UUID.randomUUID()}.dikciz-app.zip")
        ZipOutputStream(file.outputStream()).use { zip -> files(app).forEach { (name, value) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray(UTF_8)); zip.closeEntry()
        } }
        return JSONObject().put("path", file.absolutePath).put("includesState", false)
    }

    fun import(path: String): DikcizCustomApp {
        val file = File(path).canonicalFile
        if (!file.isFile || !file.name.endsWith(".dikciz-app.zip") || file.length() > MAXIMUM_ARCHIVE_BYTES ||
            DikcizFileCommands.isCredentialPath(file)) {
            DikcizNativeCommands.invalid("Expected a readable bounded .dikciz-app.zip")
        }
        val entries = linkedMapOf<String, String>()
        try { ZipInputStream(file.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || entry.name !in ENTRY_NAMES || entries.containsKey(entry.name)) DikcizNativeCommands.invalid("Unexpected or duplicate app archive entry")
                val bytes = readBounded(zip, DikcizCustomAppCommands.MAXIMUM_SOURCE_BYTES)
                if (bytes.size > DikcizCustomAppCommands.MAXIMUM_SOURCE_BYTES) DikcizNativeCommands.invalid("App archive entry exceeds its byte budget")
                entries[entry.name] = bytes.toString(UTF_8)
                zip.closeEntry()
            }
        }
        } catch (exception: java.util.zip.ZipException) {
            throw DikcizAutomationRequestException("validation_failed", "Invalid app archive").apply { initCause(exception) }
        }
        val manifest = entries[MANIFEST]?.let(::parseObject) ?: DikcizNativeCommands.invalid("Missing app manifest")
        validateManifest(manifest)
        val source = if (manifest.getString("language") == "lua") LUA else JAVASCRIPT
        if (entries.keys != setOf(MANIFEST, HTML, CSS, UI_JAVASCRIPT, source)) DikcizNativeCommands.invalid("Archive entries do not match app language")
        return fromRequest(JSONObject().put("manifest", manifest).put("html", entries.getValue(HTML)).put("css", entries.getValue(CSS))
            .put("javascript", entries.getValue(UI_JAVASCRIPT)).put("backgroundSource", entries.getValue(source)))
    }

    fun installBundled(excluded: Set<String> = emptySet()): List<String> {
        val installed = mutableListOf<String>()
        for (id in BUNDLED) {
            if (id in excluded) continue
            if (File(directory(id), MANIFEST).exists()) continue
            fun asset(name: String) = context.assets.open("apps/$id/$name").bufferedReader(UTF_8).use { it.readText() }
            val manifest = JSONObject(asset(MANIFEST))
            val app = fromRequest(JSONObject().put("manifest", manifest).put("html", asset(HTML)).put("css", asset(CSS))
                .put("javascript", asset(UI_JAVASCRIPT)).put("backgroundSource", asset(if (manifest.getString("language") == "lua") LUA else JAVASCRIPT)))
            write(app, false)
            installed.add(id)
        }
        return installed
    }

    private fun files(app: DikcizCustomApp) = linkedMapOf(MANIFEST to app.manifest.toString(2), HTML to app.html, CSS to app.css,
        UI_JAVASCRIPT to app.javascript, app.language.sourceFileName to app.source)

    private fun readFile(file: File, maximum: Int = DikcizCustomAppCommands.MAXIMUM_SOURCE_BYTES): String {
        if (!file.isFile || file.canonicalFile != file.absoluteFile || file.length() > maximum) DikcizNativeCommands.invalid("Missing, linked or oversized app file")
        return file.inputStream().use { stream ->
            val bytes = readBounded(stream, maximum)
            if (bytes.size > maximum) DikcizNativeCommands.invalid("App file exceeds its byte budget")
            bytes.toString(UTF_8)
        }
    }

    private fun writeFile(file: File, value: String) {
        if (file.canonicalFile != file.absoluteFile) DikcizNativeCommands.invalid("App file cannot be a symlink")
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try { stream.write(value.toByteArray(UTF_8)); atomic.finishWrite(stream) }
        catch (exception: Exception) { atomic.failWrite(stream); throw exception }
    }

    private fun copyData(source: File, target: File) {
        Files.walk(source.toPath()).use { paths -> paths.forEach { path ->
            if (Files.isSymbolicLink(path)) DikcizNativeCommands.invalid("App data cannot contain symlinks during replacement")
            val relative = source.toPath().relativize(path)
            if (relative.toString().isEmpty() || relative.toString() in ENTRY_NAMES + setOf(STATE, "$STATE.bak", "$STATE.new")) return@forEach
            val destination = target.toPath().resolve(relative)
            if (Files.isDirectory(path)) Files.createDirectories(destination)
            else {
                Files.copy(path, destination)
                fun digest(file: File): ByteArray = file.inputStream().use { input ->
                    val checksum = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(8192)
                    while (true) { val count = input.read(buffer); if (count < 0) break; checksum.update(buffer, 0, count) }
                    checksum.digest()
                }
                check(digest(path.toFile()).contentEquals(digest(destination.toFile())))
            }
        } }
    }

    private fun removeTree(directory: File) {
        if (directory.canonicalFile != directory.absoluteFile) DikcizNativeCommands.invalid("Cannot remove a linked app directory")
        Files.walk(directory.toPath()).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }

    private fun readBounded(input: InputStream, maximum: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, maximum + 1 - output.size()))
            if (count < 0) return output.toByteArray()
            output.write(buffer, 0, count)
            if (output.size() > maximum) DikcizNativeCommands.invalid("App file exceeds its byte budget")
        }
    }

    companion object {
        const val MANIFEST = "app.json"
        const val HTML = "ui.html"
        const val CSS = "ui.css"
        const val UI_JAVASCRIPT = "ui.js"
        const val JAVASCRIPT = "main.js"
        const val LUA = "main.lua"
        const val STATE = "state.json"
        private const val MAXIMUM_ARCHIVE_BYTES = 2097152L
        private val ENTRY_NAMES = setOf(MANIFEST, HTML, CSS, UI_JAVASCRIPT, JAVASCRIPT, LUA)
        private val RESERVED_IDS = setOf("imports", "exports")
        val BUNDLED = listOf("sos", "voice-notes", "agenda", "device-status", "travel-todo")

        fun validateManifest(value: JSONObject) {
            if (value.keys().asSequence().any { it !in setOf("version", "id", "title", "description", "language", "commands", "events", "eventSubscriptions", "locationMonitoring") }) DikcizNativeCommands.invalid("Unknown app manifest field")
            if (value.opt("version") !is Int || value.getInt("version") != 1 || value.opt("id") !is String ||
                !DikcizCustomAppCommands.ID_PATTERN.matches(value.getString("id")) || value.getString("id") in RESERVED_IDS) DikcizNativeCommands.invalid("Invalid app manifest identity or version")
            for ((key, maximum) in mapOf("title" to 120, "description" to 2000)) {
                val text = value.opt(key) as? String ?: DikcizNativeCommands.invalid("Missing app title or description")
                if (text.isBlank() || text.length > maximum || text.any(Char::isISOControl)) DikcizNativeCommands.invalid("Invalid app text")
            }
            if (value.opt("language") !in setOf("lua", "javascript")) DikcizNativeCommands.invalid("App language must be lua or javascript")
            for (key in listOf("commands", "events")) {
                val items = value.optJSONArray(key) ?: DikcizNativeCommands.invalid("App commands and events must be arrays")
                if (items.length() > 128) DikcizNativeCommands.invalid("Too many app declarations")
                val names = (0 until items.length()).map { items.opt(it) as? String ?: DikcizNativeCommands.invalid("Invalid app declaration") }
                if (names.distinct().size != names.size || names.any { !DikcizCustomAppCommands.EVENT_PATTERN.matches(it) }) DikcizNativeCommands.invalid("Invalid or duplicate app declaration")
                if (key == "commands" && names.any { it in setOf(DikcizCustomAppCommands.INSTALL, DikcizCustomAppCommands.CONTROL) }) DikcizNativeCommands.invalid("Apps cannot install, enable or remove apps")
            }
            if (value.has("locationMonitoring") && value.opt("locationMonitoring") !is Boolean) DikcizNativeCommands.invalid("locationMonitoring must be boolean")
            if (value.has("eventSubscriptions") && value.opt("eventSubscriptions") !is JSONArray) DikcizNativeCommands.invalid("eventSubscriptions must be an array")
        }

        fun validateState(value: JSONObject) {
            if (value.toString().toByteArray(UTF_8).size > DikcizCustomAppCommands.MAXIMUM_STATE_BYTES) DikcizNativeCommands.invalid("App state exceeds its byte budget")
            fun walk(item: Any?, depth: Int) {
                if (depth > 16) DikcizNativeCommands.invalid("App state is too deeply nested")
                when (item) {
                    is JSONObject -> item.keys().forEach { walk(item.get(it), depth + 1) }
                    is JSONArray -> repeat(item.length()) { walk(item.get(it), depth + 1) }
                    is Double -> if (!item.isFinite()) DikcizNativeCommands.invalid("App state numbers must be finite")
                }
            }
            walk(value, 0)
        }

        fun parseObject(text: String): JSONObject {
            var quoted = false
            var escaped = false
            var depth = 0
            for (character in text) {
                if (escaped) { escaped = false; continue }
                if (quoted && character == '\\') { escaped = true; continue }
                if (character == '"') quoted = !quoted
                if (!quoted) {
                    if (character == '{' || character == '[') depth++
                    if (character == '}' || character == ']') depth--
                    if (depth > 16 || depth < 0) DikcizNativeCommands.invalid("App JSON nesting is invalid")
                }
            }
            return try { JSONObject(text) }
            catch (exception: org.json.JSONException) {
                throw DikcizAutomationRequestException("validation_failed", "App JSON must be a valid object").apply { initCause(exception) }
            }
        }
    }
}
