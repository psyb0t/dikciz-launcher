package org.fossify.home.dikciz

import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import org.json.JSONException
import org.json.JSONObject

internal class DikcizHtmlWidgetStateStore(private val pagesDirectory: File) {
    fun needsMigration(pageID: String, widgetID: String): Boolean = storage {
        val directory = widgetDirectory(pageID, widgetID)
        !child(directory, STATE_FILE).isFile || !child(directory, OWNER_FILE).isFile
    }

    fun expand(pageID: String, widgetID: String, document: JSONObject) {
        storage {
            val target = widgetDirectory(pageID, widgetID)
            val directory = retainedDirectory(pageID, widgetID, target) ?: target
            val state = child(directory, STATE_FILE)
            if (state.exists()) document.put(STATE_FIELD, readObject(state))
            val items = child(directory, ITEMS_DIRECTORY)
            val states = document.optJSONObject(ITEM_STATES_FIELD) ?: JSONObject()
            val directories = items.listFiles().orEmpty().filter(File::isDirectory)
            if (directories.size > MAXIMUM_ITEMS) throw HomeConfigException("HTML widget has too many item states")
            directories.forEach { item ->
                validateID(item.name)
                val file = child(child(items, item.name), STATE_FILE)
                states.put(item.name, if (file.exists()) readObject(file) else JSONObject())
            }
            document.put(ITEM_STATES_FIELD, states)
        }
    }

    fun save(pageID: String, widget: HtmlHomeWidget, document: JSONObject) {
        storage {
            val directory = widgetDirectory(pageID, widget.id)
            if (!directory.exists()) {
                retainedDirectory(pageID, widget.id, directory)?.let { retained ->
                    ensureDirectory(directory.parentFile ?: throw HomeConfigException("HTML widget state parent is missing"))
                    if (!retained.renameTo(directory)) throw HomeConfigException("could not move HTML widget state to its page")
                }
            }
            ensureDirectory(directory)
            write(child(directory, OWNER_FILE), "$pageID/${widget.id}")
            write(child(directory, STATE_FILE), widget.state.toString(JSON_INDENT))
            val items = child(directory, ITEMS_DIRECTORY)
            ensureDirectory(items)
            val kept = widget.itemStates.keys().asSequence().toSet()
            kept.forEach { itemID ->
                validateID(itemID)
                val item = child(items, itemID)
                ensureDirectory(item)
                write(child(item, STATE_FILE), widget.itemStates.getJSONObject(itemID).toString(JSON_INDENT))
            }
            items.listFiles().orEmpty().filter(File::isDirectory).forEach { item ->
                if (item.name !in kept) deleteTree(item, items.canonicalFile)
            }
            document.remove(STATE_FIELD)
            document.remove(ITEM_STATES_FIELD)
        }
    }

    fun prune(pageID: String, keptWidgetIDs: Set<String>) {
        if (!OWNER_ID_PATTERN.matches(pageID)) return
        storage {
            val widgets = widgetsDirectory(pageID)
            widgets.listFiles().orEmpty().filter(File::isDirectory).forEach { directory ->
                if (directory.name in keptWidgetIDs || !isOwned(directory, pageID)) return@forEach
                deleteTree(directory, widgets.canonicalFile)
            }
        }
    }

    fun watchDirectories(pageID: String, widgetID: String): List<File> {
        return storage {
            val widget = widgetDirectory(pageID, widgetID)
            val items = child(widget, ITEMS_DIRECTORY)
            listOf(widget, items) + items.listFiles().orEmpty()
                .filter(File::isDirectory)
                .map { item -> child(items, item.name) }
        }
    }

    private fun widgetsDirectory(pageID: String): File {
        if (!OWNER_ID_PATTERN.matches(pageID)) throw HomeConfigException("HTML state page ID is invalid")
        val directory = pagesDirectory.parentFile ?: throw HomeConfigException("HTML widget state root is unavailable")
        val parent = directory.parentFile?.canonicalFile ?: throw HomeConfigException("HTML widget state root is unavailable")
        if (directory.canonicalFile != File(parent, directory.name)) throw HomeConfigException("HTML widget state root contains a symbolic link")
        val pages = child(directory, pagesDirectory.name)
        return child(child(pages, pageID), WIDGETS_DIRECTORY)
    }

    private fun widgetDirectory(pageID: String, widgetID: String): File {
        if (!OWNER_ID_PATTERN.matches(widgetID)) throw HomeConfigException("HTML state wrapper ID is invalid")
        return child(widgetsDirectory(pageID), widgetID)
    }

    private fun retainedDirectory(pageID: String, widgetID: String, target: File): File? {
        if (target.exists()) return null
        val retained = pagesDirectory.listFiles().orEmpty()
            .filter { page -> page.isDirectory && page.name != pageID && OWNER_ID_PATTERN.matches(page.name) }
            .map { page -> page.name to widgetDirectory(page.name, widgetID) }
            .filter { (page, directory) -> directory.isDirectory && isOwned(directory, page) }
        if (retained.size > 1) throw HomeConfigException("HTML widget state has ambiguous retained owners")
        return retained.firstOrNull()?.second
    }

    private fun child(parent: File, name: String): File {
        val root = parent.canonicalFile
        val candidate = File(root, name)
        if (candidate.canonicalFile != candidate.absoluteFile) {
            throw HomeConfigException("HTML widget state path contains a symbolic link")
        }
        return candidate
    }

    private fun validateID(value: String) {
        if (!ID_PATTERN.matches(value)) throw HomeConfigException("HTML item state ID is invalid")
    }

    private fun ensureDirectory(directory: File) {
        if ((!directory.exists() && !directory.mkdirs()) || !directory.isDirectory) {
            throw HomeConfigException("HTML widget state directory is unavailable")
        }
    }

    private fun readObject(file: File): JSONObject {
        if (!file.isFile || file.length() > MAXIMUM_STATE_BYTES) {
            throw HomeConfigException("HTML widget state file is missing or too large")
        }
        return try {
            JSONObject(file.readText(StandardCharsets.UTF_8))
        } catch (exception: JSONException) {
            throw HomeConfigException("HTML widget state must be a JSON object", exception)
        }
    }

    private fun write(file: File, value: String) {
        if (file.exists() && file.length() <= MAXIMUM_STATE_BYTES && file.readText(StandardCharsets.UTF_8) == value) return
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            output.write(value.toByteArray(StandardCharsets.UTF_8))
            atomic.finishWrite(output)
        } catch (exception: IOException) {
            atomic.failWrite(output)
            throw exception
        }
    }

    private fun isOwned(directory: File, pageID: String): Boolean {
        if (!OWNER_ID_PATTERN.matches(directory.name)) return false
        if (directory.canonicalFile != File(directory.parentFile?.canonicalFile, directory.name)) return false
        val marker = child(directory, OWNER_FILE)
        return marker.isFile && marker.length() <= MAXIMUM_OWNER_BYTES &&
            marker.readText(StandardCharsets.UTF_8) == "$pageID/${directory.name}"
    }

    private fun deleteTree(file: File, parent: File) {
        val expected = File(parent, file.name)
        if (file.canonicalFile == expected && file.isDirectory) {
            file.listFiles().orEmpty().forEach { child -> deleteTree(child, expected) }
        }
        if (!file.delete()) throw HomeConfigException("could not remove owned HTML widget state")
    }

    private fun <T> storage(action: () -> T): T {
        return try {
            action()
        } catch (exception: IOException) {
            throw HomeConfigException("HTML widget state storage failed", exception)
        } catch (exception: SecurityException) {
            throw HomeConfigException("HTML widget state storage access was denied", exception)
        }
    }

    companion object {
        const val ITEM_STATES_FIELD = "itemStates"
        const val MAXIMUM_ITEMS = 1024
        private val ITEM_ID_ATTRIBUTE = Regex("data-dikciz-item-id=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

        fun initialStates(html: String, current: JSONObject = JSONObject()): JSONObject {
            val result = JSONObject(current.toString())
            ITEM_ID_ATTRIBUTE.findAll(html).forEach { match ->
                val id = match.groupValues[1]
                if (ID_PATTERN.matches(id) && !result.has(id)) result.put(id, JSONObject())
            }
            return result
        }

        val ID_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]{0,79}$")
        private val OWNER_ID_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_.-]{0,255}$")
        private const val STATE_FIELD = "state"
        private const val STATE_FILE = "state.json"
        private const val ITEMS_DIRECTORY = "items"
        private const val WIDGETS_DIRECTORY = "widgets"
        private const val OWNER_FILE = ".dikciz-widget"
        private const val MAXIMUM_OWNER_BYTES = 1024L
        private const val MAXIMUM_STATE_BYTES = 1_048_576L
        private const val JSON_INDENT = 2
    }
}
