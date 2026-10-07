package org.fossify.home.dikciz

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.nio.charset.StandardCharsets
import org.json.JSONException
import org.json.JSONObject

/**
 * One named, portable HTML block.
 *
 * A block is authoring material for an existing HTML widget, not a launcher item. Adding
 * one appends editable source to that single widget's document and consumes no native grid
 * cell, which is what separates it from a widget package.
 */
internal data class DikcizHtmlBlock(
    val id: String,
    val title: String,
    val description: String,
    val previewLabel: String,
    val fragment: String,
)

/**
 * The public block library under `/sdcard/Dikciz/widget-blocks/`.
 *
 * Blocks sit alongside the widget-package library and follow the same rules: bundled
 * content is seeded once, every public file is untrusted, and anything malformed is
 * rejected instead of rendered.
 */
internal class DikcizHtmlBlockLibrary(
    private val context: Context,
    private val directory: File,
) {
    fun installBundledBlocks() {
        ensureDirectory()
        BUNDLED_BLOCK_IDS.forEach { blockID ->
            val target = File(directory, blockID)
            if (target.exists()) {
                return@forEach
            }
            if (!target.mkdirs() && !target.isDirectory) {
                throw HomeConfigException("could not create block directory $blockID")
            }
            BLOCK_FILE_NAMES.forEach { name ->
                writeAtomicFile(
                    File(target, name),
                    context.assets.open("$ASSET_DIRECTORY/$blockID/$name")
                        .bufferedReader(StandardCharsets.UTF_8)
                        .use { reader -> reader.readText() },
                )
            }
        }
    }

    /** Every readable block, in stable ID order. Unreadable blocks are skipped. */
    fun listBlocks(): List<DikcizHtmlBlock> {
        ensureDirectory()
        return (directory.listFiles() ?: emptyArray())
            .filter(File::isDirectory)
            .map(File::getName)
            .filter(BLOCK_ID_PATTERN::matches)
            .sorted()
            .mapNotNull(::loadBlockOrNull)
    }

    /**
     * The block source wrapped in a stable marker so the user can find, edit, or delete
     * exactly this insertion in the normal HTML source editor afterwards.
     */
    fun markedFragment(block: DikcizHtmlBlock, instanceID: String): String {
        return buildString {
            append(MARKER_OPEN_PREFIX)
            append(block.id)
            append(MARKER_SEPARATOR)
            append(instanceID)
            append(MARKER_SUFFIX)
            append(LINE_BREAK)
            append(block.fragment.trim())
            append(LINE_BREAK)
            append(MARKER_CLOSE_PREFIX)
            append(instanceID)
            append(MARKER_SUFFIX)
        }
    }

    private fun loadBlockOrNull(blockID: String): DikcizHtmlBlock? {
        return try {
            loadBlock(blockID)
        } catch (_: HomeConfigException) {
            null
        }
    }

    private fun loadBlock(blockID: String): DikcizHtmlBlock {
        val blockDirectory = File(directory, blockID)
        val manifest = readManifest(File(blockDirectory, MANIFEST_FILE_NAME))
        requireOnlyKeys(manifest, MANIFEST_KEYS)
        if (manifest.requireBoundedInt(KEY_VERSION, MINIMUM_VERSION, CURRENT_VERSION) !=
            CURRENT_VERSION
        ) {
            throw HomeConfigException("block $blockID has an unsupported version")
        }
        if (manifest.requireText(KEY_ID, MAXIMUM_ID_CHARACTERS) != blockID) {
            throw HomeConfigException("block $blockID has a mismatched id")
        }
        val fragment = readSourceFile(File(blockDirectory, FRAGMENT_FILE_NAME))
        if (fragment.isBlank()) {
            throw HomeConfigException("block $blockID has an empty fragment")
        }
        return DikcizHtmlBlock(
            id = blockID,
            title = manifest.requireText(KEY_TITLE, MAXIMUM_TITLE_CHARACTERS),
            description = manifest.requireText(KEY_DESCRIPTION, MAXIMUM_DESCRIPTION_CHARACTERS),
            previewLabel = manifest.requireText(KEY_PREVIEW_LABEL, MAXIMUM_TITLE_CHARACTERS),
            fragment = fragment,
        )
    }

    private fun readManifest(file: File): JSONObject {
        if (!file.isFile || file.length() > MAXIMUM_SOURCE_BYTES) {
            throw HomeConfigException("${file.name} is missing or too large")
        }
        return try {
            JSONObject(file.readText(StandardCharsets.UTF_8))
        } catch (exception: JSONException) {
            throw HomeConfigException("${file.name} is not valid JSON", exception)
        }
    }

    private fun readSourceFile(file: File): String {
        if (!file.isFile || file.length() > MAXIMUM_SOURCE_BYTES) {
            throw HomeConfigException("${file.name} is missing or too large")
        }
        return file.readText(StandardCharsets.UTF_8)
    }

    private fun ensureDirectory() {
        if (directory.isDirectory) {
            return
        }
        if (!directory.mkdirs() && !directory.isDirectory) {
            throw HomeConfigException("could not create $DIRECTORY_LABEL")
        }
    }

    private fun writeAtomicFile(file: File, content: String) {
        val atomicFile = AtomicFile(file)
        val stream = atomicFile.startWrite()
        try {
            stream.write(content.toByteArray(StandardCharsets.UTF_8))
            atomicFile.finishWrite(stream)
        } catch (exception: java.io.IOException) {
            atomicFile.failWrite(stream)
            throw HomeConfigException("could not write ${file.name}", exception)
        }
    }

    private fun requireOnlyKeys(value: JSONObject, allowedKeys: Set<String>) {
        val iterator = value.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (key !in allowedKeys) {
                throw HomeConfigException("block manifest has an unknown key: $key")
            }
        }
    }

    private fun JSONObject.requireText(key: String, maximumLength: Int): String {
        val value = optString(key)
        if (value.isBlank() || value.length > maximumLength || value.contains(NUL_CHARACTER)) {
            throw HomeConfigException("block manifest $key is invalid")
        }
        return value
    }

    private fun JSONObject.requireBoundedInt(key: String, minimum: Int, maximum: Int): Int {
        if (!has(key)) {
            throw HomeConfigException("block manifest is missing $key")
        }
        val value = optInt(key, minimum - OUT_OF_RANGE_OFFSET)
        if (value < minimum || value > maximum) {
            throw HomeConfigException("block manifest $key is out of range")
        }
        return value
    }

    companion object {
        const val DIRECTORY_NAME = "widget-blocks"

        /** A block instance marker is stable and greppable in the saved HTML source. */
        const val MARKER_CLOSE_PREFIX = "<!-- /dikciz:block "
        const val MARKER_OPEN_PREFIX = "<!-- dikciz:block "
        const val MARKER_SEPARATOR = " "
        const val MARKER_SUFFIX = " -->"

        private const val ASSET_DIRECTORY = "widget-blocks"
        private const val CURRENT_VERSION = 1
        private const val DIRECTORY_LABEL = "widget-blocks"
        private const val FRAGMENT_FILE_NAME = "fragment.html"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_ID = "id"
        private const val KEY_PREVIEW_LABEL = "previewLabel"
        private const val KEY_TITLE = "title"
        private const val KEY_VERSION = "version"
        private const val LINE_BREAK = "\n"
        private const val MANIFEST_FILE_NAME = "block.json"
        private const val MAXIMUM_DESCRIPTION_CHARACTERS = 512
        private const val MAXIMUM_ID_CHARACTERS = 80
        private const val MAXIMUM_SOURCE_BYTES = 65_536L
        private const val MAXIMUM_TITLE_CHARACTERS = 120
        private const val MINIMUM_VERSION = 1
        private const val NUL_CHARACTER = '\u0000'
        private const val OUT_OF_RANGE_OFFSET = 1

        private val BLOCK_FILE_NAMES = listOf(MANIFEST_FILE_NAME, FRAGMENT_FILE_NAME)
        private val BLOCK_ID_PATTERN = Regex("^[a-z][a-z0-9-]*$")
        private val BUNDLED_BLOCK_IDS = listOf(
            "heading",
            "status-row",
            "action-button",
            "divider",
        )
        private val MANIFEST_KEYS = setOf(
            KEY_VERSION,
            KEY_ID,
            KEY_TITLE,
            KEY_DESCRIPTION,
            KEY_PREVIEW_LABEL,
        )
    }
}
