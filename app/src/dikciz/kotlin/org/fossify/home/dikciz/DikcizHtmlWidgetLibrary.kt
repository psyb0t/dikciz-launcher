package org.fossify.home.dikciz

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.nio.charset.StandardCharsets
import org.json.JSONException
import org.json.JSONObject

internal data class DikcizHtmlWidgetPackage(
    val id: String,
    val title: String,
    val description: String,
    val html: String,
    val css: String,
    val javascript: String,
    val defaultSpan: DikcizGridSpan,
    val defaultHeightMode: HtmlWidgetHeightMode,
)

internal class DikcizHtmlWidgetLibrary(
    private val context: Context,
    private val directory: File,
) {
    fun installBundledPackages() {
        ensureDirectory(directory, DIRECTORY_LABEL)
        BUNDLED_PACKAGE_IDS.forEach { packageID ->
            recoverInterruptedReplacement(packageID)
            val target = packageDirectory(packageID)
            if (target.exists()) {
                return@forEach
            }
            ensureDirectory(target, packageID)
            PACKAGE_FILE_NAMES.forEach { name ->
                writeAtomicFile(
                    File(target, name),
                    context.assets.open("$ASSET_DIRECTORY/$packageID/$name")
                        .bufferedReader(StandardCharsets.UTF_8)
                        .use { reader -> reader.readText() },
                )
            }
        }
    }

    fun listPackages(): List<DikcizHtmlWidgetPackage> {
        ensureDirectory(directory, DIRECTORY_LABEL)
        return libraryPackageIDs()
            ?.asSequence()
            ?.mapNotNull(::loadPackageOrNull)
            ?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { item -> item.title })
            ?.toList()
            ?: emptyList()
    }

    fun loadPackage(packageID: String): DikcizHtmlWidgetPackage {
        validatePackageID(packageID)
        ensureDirectory(directory, DIRECTORY_LABEL)
        recoverInterruptedReplacement(packageID)
        return readPackage(packageDirectory(packageID), packageID)
    }

    fun savePackage(widget: HtmlHomeWidget, packageID: String): DikcizHtmlWidgetPackage {
        validatePackageID(packageID)
        ensureDirectory(directory, DIRECTORY_LABEL)
        recoverInterruptedReplacement(packageID)
        val target = packageDirectory(packageID)
        val staging = recoveryDirectory(packageID, STAGING_SUFFIX)
        val backup = recoveryDirectory(packageID, BACKUP_SUFFIX)
        if (staging.exists()) {
            throw HomeConfigException("widget package $packageID has unfinished staging data")
        }
        if (backup.exists()) {
            throw HomeConfigException("widget package $packageID has unfinished backup data")
        }
        ensureDirectory(staging, "widget package staging")
        val packageValue = DikcizHtmlWidgetPackage(
            id = packageID,
            title = widget.title,
            description = widget.title,
            html = widget.html,
            css = widget.css,
            javascript = widget.javascript,
            defaultSpan = DikcizGridSpan(widget.cell.columnSpan, widget.cell.rowSpan),
            defaultHeightMode = widget.heightMode,
        )
        writePackage(staging, packageValue)
        readPackage(staging, packageID)
        publishStagedPackage(target, staging, backup, packageID)
        return packageValue
    }

    private fun loadPackageOrNull(packageID: String): DikcizHtmlWidgetPackage? {
        return try {
            loadPackage(packageID)
        } catch (exception: HomeConfigException) {
            null
        }
    }

    private fun readPackage(
        packageDirectory: File,
        expectedPackageID: String,
    ): DikcizHtmlWidgetPackage {
        validatePackageFiles(packageDirectory)
        val manifest = readManifest(File(packageDirectory, MANIFEST_FILE_NAME))
        requireExactKeys(manifest, MANIFEST_KEYS, "widget package manifest")
        val packageID = requirePackageID(manifest, KEY_ID)
        if (packageID != expectedPackageID) {
            throw HomeConfigException("widget package manifest ID does not match its directory")
        }
        if (manifest.requireInt(KEY_VERSION, MINIMUM_VERSION, CURRENT_VERSION) != CURRENT_VERSION) {
            throw HomeConfigException("widget package has an unsupported version")
        }
        return DikcizHtmlWidgetPackage(
            id = packageID,
            title = manifest.requireText(KEY_TITLE, MAXIMUM_TITLE_CHARACTERS),
            description = manifest.requireText(KEY_DESCRIPTION, MAXIMUM_DESCRIPTION_CHARACTERS),
            html = readSourceFile(packageDirectory, HTML_FILE_NAME, required = true),
            css = readSourceFile(packageDirectory, CSS_FILE_NAME, required = false),
            javascript = readSourceFile(packageDirectory, JAVASCRIPT_FILE_NAME, required = false),
            defaultSpan = manifest.requireSpan(),
            defaultHeightMode = manifest.optionalHeightMode(),
        )
    }

    private fun writePackage(directory: File, value: DikcizHtmlWidgetPackage) {
        writeAtomicFile(File(directory, HTML_FILE_NAME), value.html)
        if (value.css.isNotEmpty()) {
            writeAtomicFile(File(directory, CSS_FILE_NAME), value.css)
        }
        if (value.javascript.isNotEmpty()) {
            writeAtomicFile(File(directory, JAVASCRIPT_FILE_NAME), value.javascript)
        }
        writeAtomicFile(
            File(directory, MANIFEST_FILE_NAME),
            JSONObject()
                .put(KEY_VERSION, CURRENT_VERSION)
                .put(KEY_ID, value.id)
                .put(KEY_TITLE, value.title)
                .put(KEY_DESCRIPTION, value.description)
                .put(KEY_HEIGHT_MODE, value.defaultHeightMode.persistedValue)
                .put(KEY_SPAN, JSONObject()
                    .put(KEY_COLUMN_SPAN, value.defaultSpan.columnSpan)
                    .put(KEY_ROW_SPAN, value.defaultSpan.rowSpan))
                .toString(JSON_INDENTATION_SPACES),
        )
    }

    private fun readManifest(file: File): JSONObject {
        val content = readTextFile(file, MANIFEST_FILE_NAME)
        return try {
            JSONObject(content)
        } catch (exception: JSONException) {
            throw HomeConfigException("$MANIFEST_FILE_NAME is not valid JSON", exception)
        }
    }

    private fun readSourceFile(directory: File, name: String, required: Boolean): String {
        val file = File(directory, name)
        if (!file.exists() && !required) {
            return ""
        }
        val source = readTextFile(file, name)
        if (required && source.isBlank()) {
            throw HomeConfigException("$name must not be blank")
        }
        return source
    }

    private fun readTextFile(file: File, label: String): String {
        if (!file.isFile) {
            throw HomeConfigException("$label is missing")
        }
        if (file.length() > MAXIMUM_SOURCE_BYTES) {
            throw HomeConfigException("$label exceeds the source size limit")
        }
        return try {
            file.readText(StandardCharsets.UTF_8).also { source ->
                if (source.contains(NUL_CHARACTER)) {
                    throw HomeConfigException("$label contains an invalid character")
                }
            }
        } catch (exception: HomeConfigException) {
            throw exception
        } catch (exception: Exception) {
            throw HomeConfigException("could not read $label", exception)
        }
    }

    private fun JSONObject.requireSpan(): DikcizGridSpan {
        val value = opt(KEY_SPAN)
        if (value !is JSONObject) {
            throw HomeConfigException("widget package span must be an object")
        }
        requireExactKeys(value, SPAN_KEYS, "widget package span")
        return DikcizGridSpan(
            columnSpan = value.requireInt(
                KEY_COLUMN_SPAN,
                DikcizGridRectangle.MINIMUM_SPAN,
                DikcizNativeGrid.MAXIMUM_COLUMNS,
            ),
            rowSpan = value.requireInt(
                KEY_ROW_SPAN,
                DikcizGridRectangle.MINIMUM_SPAN,
                DikcizNativeGrid.MAXIMUM_ROWS,
            ),
        )
    }

    private fun JSONObject.optionalHeightMode(): HtmlWidgetHeightMode {
        if (!has(KEY_HEIGHT_MODE)) {
            return HtmlWidgetHeightMode.Fixed
        }
        val value = opt(KEY_HEIGHT_MODE)
        if (value !is String) {
            throw HomeConfigException("widget package $KEY_HEIGHT_MODE is invalid")
        }
        return HtmlWidgetHeightMode.fromPersistedValue(value)
            ?: throw HomeConfigException("widget package $KEY_HEIGHT_MODE is invalid")
    }

    private fun JSONObject.requireInt(key: String, minimum: Int, maximum: Int): Int {
        val value = opt(key)
        if (value !is Number || value.toLong().toDouble() != value.toDouble()) {
            throw HomeConfigException("widget package $key must be an integer")
        }
        val number = value.toLong()
        if (number !in minimum.toLong()..maximum.toLong()) {
            throw HomeConfigException("widget package $key is out of range")
        }
        return number.toInt()
    }

    private fun JSONObject.requireText(key: String, maximumLength: Int): String {
        val value = opt(key)
        if (value !is String || value.isBlank() || value.length > maximumLength || value.contains(NUL_CHARACTER)) {
            throw HomeConfigException("widget package $key is invalid")
        }
        return value
    }

    private fun requirePackageID(value: JSONObject, key: String): String {
        val packageID = value.requireText(key, MAXIMUM_PACKAGE_ID_CHARACTERS)
        validatePackageID(packageID)
        return packageID
    }

    private fun requireExactKeys(value: JSONObject, allowed: Set<String>, context: String) {
        val keys = value.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key !in allowed) {
                throw HomeConfigException("$context has an unknown key: $key")
            }
        }
    }

    private fun validatePackageFiles(packageDirectory: File) {
        val files = packageDirectory.listFiles()
            ?: throw HomeConfigException("widget package files are unavailable")
        files.forEach { file ->
            if (!file.isFile || file.name !in PACKAGE_FILE_NAMES) {
                throw HomeConfigException("widget package has an unsupported file: ${file.name}")
            }
        }
    }

    private fun validatePackageID(packageID: String) {
        if (!PACKAGE_ID_PATTERN.matches(packageID)) {
            throw HomeConfigException("widget package ID must be a readable lowercase slug")
        }
    }

    private fun libraryPackageIDs(): Set<String>? {
        return directory.listFiles()
            ?.asSequence()
            ?.mapNotNull { file ->
                when {
                    file.isDirectory && PACKAGE_ID_PATTERN.matches(file.name) -> file.name
                    file.isDirectory -> recoveryPackageID(file.name)
                    else -> null
                }
            }
            ?.toSet()
    }

    private fun recoveryPackageID(directoryName: String): String? {
        return RECOVERY_DIRECTORY_PATTERN.matchEntire(directoryName)
            ?.groupValues
            ?.get(RECOVERY_PACKAGE_ID_GROUP)
    }

    private fun recoverInterruptedReplacement(packageID: String) {
        val target = packageDirectory(packageID)
        val staging = recoveryDirectory(packageID, STAGING_SUFFIX)
        val backup = recoveryDirectory(packageID, BACKUP_SUFFIX)
        when {
            target.exists() -> {
                readPackage(target, packageID)
                discardRecoveryDirectory(staging, "staging")
                discardRecoveryDirectory(backup, "backup")
            }

            staging.exists() && backup.exists() -> {
                if (isReadablePackage(staging, packageID)) {
                    publishRecoveredPackage(staging, target, packageID, "staging")
                    discardRecoveryDirectory(backup, "backup")
                } else {
                    publishRecoveredPackage(backup, target, packageID, "backup")
                    discardRecoveryDirectory(staging, "staging")
                }
            }

            staging.exists() -> publishRecoveredPackage(staging, target, packageID, "staging")
            backup.exists() -> publishRecoveredPackage(backup, target, packageID, "backup")
        }
    }

    private fun isReadablePackage(directory: File, packageID: String): Boolean {
        return try {
            readPackage(directory, packageID)
            true
        } catch (exception: HomeConfigException) {
            false
        }
    }

    private fun publishStagedPackage(
        target: File,
        staging: File,
        backup: File,
        packageID: String,
    ) {
        if (!target.exists()) {
            publishRecoveredPackage(staging, target, packageID, "staging")
            return
        }
        readPackage(target, packageID)
        if (!target.renameTo(backup)) {
            throw HomeConfigException("could not retain widget package $packageID before replacement")
        }
        if (!staging.renameTo(target)) {
            if (!backup.renameTo(target)) {
                throw HomeConfigException("could not restore widget package $packageID after replacement failure")
            }
            throw HomeConfigException("could not publish widget package $packageID")
        }
        readPackage(target, packageID)
        discardRecoveryDirectory(backup, "backup")
    }

    private fun publishRecoveredPackage(
        source: File,
        target: File,
        packageID: String,
        sourceLabel: String,
    ) {
        val packageValue = readPackage(source, packageID)
        if (source.renameTo(target)) {
            readPackage(target, packageID)
            return
        }
        val publish = recoveryDirectory(packageID, PUBLISH_SUFFIX)
        discardRecoveryDirectory(publish, "temporary publish")
        ensureDirectory(publish, "widget package temporary publish")
        writePackage(publish, packageValue)
        readPackage(publish, packageID)
        if (!publish.renameTo(target)) {
            throw HomeConfigException("could not recover widget package $packageID")
        }
        readPackage(target, packageID)
        discardRecoveryDirectory(source, sourceLabel)
    }

    private fun discardRecoveryDirectory(recovery: File, label: String) {
        if (!recovery.exists()) {
            return
        }
        if (!recovery.isDirectory) {
            throw HomeConfigException("widget package $label data is unavailable")
        }
        val files = recovery.listFiles()
            ?: throw HomeConfigException("widget package $label data is unavailable")
        if (files.any { file -> !file.isFile || file.name !in RECOVERY_FILE_NAMES }) {
            throw HomeConfigException("widget package $label data is unsafe to discard")
        }
        files.forEach { file ->
            if (!file.delete()) {
                throw HomeConfigException("could not discard widget package $label file")
            }
        }
        if (!recovery.delete()) {
            throw HomeConfigException("could not discard widget package $label directory")
        }
    }

    private fun packageDirectory(packageID: String): File {
        val root = directory.canonicalFile
        val candidate = File(root, packageID).canonicalFile
        if (candidate.parentFile != root) {
            throw HomeConfigException("widget package path escapes the library")
        }
        return candidate
    }

    private fun recoveryDirectory(packageID: String, suffix: String): File {
        return packageDirectory("$RECOVERY_DIRECTORY_PREFIX$packageID$suffix")
    }

    private fun ensureDirectory(directory: File, label: String) {
        if (directory.exists() && !directory.isDirectory) {
            throw HomeConfigException("$label directory is unavailable")
        }
        if (!directory.exists() && !directory.mkdirs()) {
            throw HomeConfigException("$label directory could not be created")
        }
    }

    private fun writeAtomicFile(target: File, content: String) {
        val atomicFile = AtomicFile(target)
        val output = try {
            atomicFile.startWrite()
        } catch (exception: Exception) {
            throw HomeConfigException("could not save ${target.name}", exception)
        }
        try {
            output.write(content.toByteArray(StandardCharsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (exception: Exception) {
            atomicFile.failWrite(output)
            throw HomeConfigException("could not save ${target.name}", exception)
        }
    }

    private companion object {
        const val ASSET_DIRECTORY = "widget-library"
        const val BACKUP_SUFFIX = ".backup"
        const val CSS_FILE_NAME = "style.css"
        const val CURRENT_VERSION = 2
        const val DIRECTORY_LABEL = "widget-library"
        const val HTML_FILE_NAME = "index.html"
        const val JAVASCRIPT_FILE_NAME = "script.js"
        const val JSON_INDENTATION_SPACES = 2
        const val KEY_DESCRIPTION = "description"
        const val KEY_HEIGHT_MODE = "heightMode"
        const val KEY_COLUMN_SPAN = "columnSpan"
        const val KEY_ID = "id"
        const val KEY_ROW_SPAN = "rowSpan"
        const val KEY_TITLE = "title"
        const val KEY_VERSION = "version"
        const val KEY_SPAN = "span"
        const val MANIFEST_FILE_NAME = "widget.json"
        const val MAXIMUM_DESCRIPTION_CHARACTERS = 512
        const val MAXIMUM_PACKAGE_ID_CHARACTERS = 80
        const val MAXIMUM_SOURCE_BYTES = 65_536L
        const val MAXIMUM_TITLE_CHARACTERS = 120
        const val MINIMUM_VERSION = 1
        const val NUL_CHARACTER = '\u0000'
        const val PUBLISH_SUFFIX = ".publish"
        const val RECOVERY_DIRECTORY_PREFIX = "."
        const val RECOVERY_PACKAGE_ID_GROUP = 1
        const val STAGING_SUFFIX = ".staging"
        val BUNDLED_PACKAGE_IDS = listOf(
            "app-launcher",
            "brightness",
            "lock-screen",
            "notifications",
            "system-status",
        )
        val MANIFEST_KEYS = setOf(
            KEY_VERSION,
            KEY_ID,
            KEY_TITLE,
            KEY_DESCRIPTION,
            KEY_HEIGHT_MODE,
            KEY_SPAN,
        )
        val PACKAGE_FILE_NAMES = listOf(HTML_FILE_NAME, CSS_FILE_NAME, JAVASCRIPT_FILE_NAME, MANIFEST_FILE_NAME)
        val PACKAGE_ID_PATTERN = Regex("^[a-z][a-z0-9-]*$")
        val RECOVERY_DIRECTORY_PATTERN = Regex(
            "^\\.([a-z][a-z0-9-]*)\\.(?:staging|backup)$",
        )
        val RECOVERY_FILE_NAMES = PACKAGE_FILE_NAMES + PACKAGE_FILE_NAMES.map { name -> "$name.new" }
        val SPAN_KEYS = setOf(KEY_COLUMN_SPAN, KEY_ROW_SPAN)
    }
}
