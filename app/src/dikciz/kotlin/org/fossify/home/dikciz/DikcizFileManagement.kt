package org.fossify.home.dikciz

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import org.json.JSONArray
import org.json.JSONObject

internal class DikcizFileManagement {
    @Synchronized
    fun execute(request: JSONObject): JSONObject {
        val source = DikcizFileCommands.file(request)
        val sourcePath = source.toPath()
        try {
            return when (request.getString(KEY_ACTION)) {
                ACTION_STAT -> metadata(source)
                ACTION_COPY, ACTION_MOVE -> {
                    val destination = DikcizFileCommands.file(JSONObject().put("path", request.getString(KEY_DESTINATION)))
                    if (Files.isSymbolicLink(sourcePath)) DikcizNativeCommands.invalid("Symbolic links cannot be copied or moved")
                    if (request.getString(KEY_ACTION) == ACTION_COPY) {
                        if (!Files.isRegularFile(sourcePath, LinkOption.NOFOLLOW_LINKS)) DikcizNativeCommands.invalid("Copy requires a regular file")
                        Files.copy(sourcePath, destination.toPath())
                    } else {
                        requireUnprotectedTree(source)
                        Files.move(sourcePath, destination.toPath())
                    }
                    metadata(destination).put("state", "completed")
                }
                ACTION_DELETE -> {
                    requireUnprotectedTree(source)
                    if (request.optBoolean(KEY_RECURSIVE, false) && source.isDirectory) {
                        val tree = boundedTree(source, request.optInt(KEY_MAXIMUM_ENTRIES, DEFAULT_MAXIMUM_ENTRIES))
                        tree.asReversed().forEach { Files.delete(it.toPath()) }
                    } else {
                        Files.delete(sourcePath)
                    }
                    JSONObject().put("state", "deleted").put("path", source.path)
                }
                ACTION_SEARCH -> search(source, request)
                else -> DikcizNativeCommands.invalid()
            }
        } catch (exception: IOException) {
            throw DikcizFileCommands.failure(DikcizFileCommands.ERROR_IO, exception)
        }
    }

    private fun search(root: File, request: JSONObject): JSONObject {
        if (!root.isDirectory) DikcizNativeCommands.failure(DikcizFileCommands.ERROR_NOT_DIRECTORY, "Search requires a directory")
        val offset = request.optInt("offset", 0)
        val limit = request.optInt("limit", DikcizNativeCommands.DEFAULT_LIMIT)
        val maximum = request.optInt(KEY_MAXIMUM_ENTRIES, DEFAULT_MAXIMUM_ENTRIES)
        val query = request.getString(KEY_QUERY)
        var scanned = 0
        var matched = 0
        var complete = true
        val items = JSONArray()
        Files.walk(root.toPath()).use { tree ->
            val iterator = tree.iterator()
            while (iterator.hasNext()) {
                if (scanned >= maximum) DikcizNativeCommands.failure("file_tree_limit", "Search exceeds maximumEntries; increase the bound or search a narrower directory")
                val entry = iterator.next().toFile()
                scanned++
                if (!entry.name.contains(query, ignoreCase = true) || !isUnprotected(entry)) continue
                if (matched++ < offset) continue
                if (items.length() >= limit) { complete = false; break }
                items.put(metadata(entry))
            }
        }
        return JSONObject().put("items", items).put("offset", offset).put("limit", limit)
            .put("hasMore", matched > offset + items.length()).put("scanComplete", complete)
            .put("scannedEntries", scanned).put("maximumEntries", maximum)
    }

    private fun boundedTree(root: File, maximum: Int): List<File> {
        val result = ArrayList<File>()
        Files.walk(root.toPath()).use { tree ->
            val iterator = tree.iterator()
            while (iterator.hasNext()) {
                if (result.size >= maximum) DikcizNativeCommands.failure("file_tree_limit", "Directory exceeds maximumEntries; no files were deleted")
                val file = iterator.next().toFile()
                if (Files.isSymbolicLink(file.toPath())) DikcizNativeCommands.invalid("Recursive deletion refuses symbolic links")
                requireUnprotectedTree(file)
                result.add(file)
            }
        }
        return result
    }

    private fun metadata(file: File): JSONObject {
        val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        return JSONObject().put("path", file.absolutePath).put("name", file.name)
            .put("kind", if (attributes.isDirectory) "directory" else if (attributes.isSymbolicLink) "symlink" else "file")
            .put("size", attributes.size()).put("modifiedAtMilliseconds", attributes.lastModifiedTime().toMillis())
            .put("readable", file.canRead()).put("writable", file.canWrite())
    }

    private fun isUnprotected(file: File): Boolean = !DikcizFileCommands.isCredentialPath(file)

    private fun requireUnprotectedTree(file: File) {
        if (DikcizFileCommands.containsCredentials(file)) {
            DikcizNativeCommands.failure(DikcizFileCommands.ERROR_ACCESS_DENIED, "Credential files cannot be accessed through file commands")
        }
    }

    companion object {
        const val TYPE = "fileManage"
        const val KEY_ACTION = "action"
        const val KEY_DESTINATION = "destination"
        const val KEY_RECURSIVE = "recursive"
        const val KEY_QUERY = "query"
        const val KEY_MAXIMUM_ENTRIES = "maximumEntries"
        const val ACTION_STAT = "stat"
        const val ACTION_COPY = "copy"
        const val ACTION_MOVE = "move"
        const val ACTION_DELETE = "delete"
        const val ACTION_SEARCH = "search"
        private const val DEFAULT_MAXIMUM_ENTRIES = 10_000
        val DEFINITIONS = listOf(DikcizNativeCommands.Definition(
            TYPE, "dikciz_file_manage",
            "Stat, copy one regular file, move without overwrite, explicitly delete, or search accessible files by literal name substring. Recursive deletion is preflighted, bounded and refuses symlinks and credential ancestors. Search does not follow symlinks. Android filesystem access applies.",
            mapOf(
                KEY_ACTION to text("stat, copy, move, delete or search.", 16),
                "path" to text("Absolute source file or directory path.", DikcizFileCommands.MAXIMUM_PATH_CHARACTERS),
                KEY_DESTINATION to text("New absolute path, copy/move only. Existing destinations are never overwritten.", DikcizFileCommands.MAXIMUM_PATH_CHARACTERS),
                KEY_RECURSIVE to JSONObject().put("type", "boolean").put("default", false),
                KEY_QUERY to text("Literal case-insensitive name substring, search only.", 256),
                KEY_MAXIMUM_ENTRIES to integer(1, 100_000, DEFAULT_MAXIMUM_ENTRIES),
                "offset" to integer(0, DikcizNativeCommands.MAXIMUM_OFFSET, 0),
                "limit" to integer(1, DikcizNativeCommands.MAXIMUM_LIMIT, DikcizNativeCommands.DEFAULT_LIMIT),
            ), setOf(KEY_ACTION, "path"), readOnly = false,
        ))

        fun validate(request: JSONObject) {
            DikcizFileCommands.file(request, false)
            val action = request.getString(KEY_ACTION)
            val fields = when (action) {
                ACTION_STAT -> emptySet()
                ACTION_COPY, ACTION_MOVE -> setOf(KEY_DESTINATION)
                ACTION_DELETE -> setOf(KEY_RECURSIVE, KEY_MAXIMUM_ENTRIES)
                ACTION_SEARCH -> setOf(KEY_QUERY, KEY_MAXIMUM_ENTRIES, "offset", "limit")
                else -> DikcizNativeCommands.invalid("Unsupported file action")
            }
            val optional = setOf(KEY_DESTINATION, KEY_RECURSIVE, KEY_MAXIMUM_ENTRIES, KEY_QUERY, "offset", "limit")
            if (optional.any { request.has(it) && it !in fields }) DikcizNativeCommands.invalid("File fields do not match action")
            if (action in setOf(ACTION_COPY, ACTION_MOVE)) {
                if (!request.has(KEY_DESTINATION)) DikcizNativeCommands.invalid("Destination is required")
                DikcizFileCommands.file(JSONObject().put("path", request.getString(KEY_DESTINATION)), false)
            }
            if (action == ACTION_SEARCH && !request.has(KEY_QUERY)) DikcizNativeCommands.invalid("Search query is required")
        }

        private fun text(description: String, maximum: Int) = JSONObject().put("type", "string").put("description", description).put("maxLength", maximum)
        private fun integer(minimum: Int, maximum: Int, default: Int) = JSONObject().put("type", "integer").put("minimum", minimum).put("maximum", maximum).put("default", default)
    }
}
