package org.fossify.home.dikciz

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class HomeConfigStore(
    private val context: Context,
) {
    val configurationFile: File = File(
        sharedStorageRoot(),
        "$CONFIGURATION_DIRECTORY_NAME/$CONFIGURATION_FILE_NAME",
    )
    val fontDirectory: File = File(
        sharedStorageRoot(),
        "$CONFIGURATION_DIRECTORY_NAME/${DikcizStyleCodec.FONT_DIRECTORY_NAME}",
    )
    val logDirectory: File = File(
        sharedStorageRoot(),
        "$CONFIGURATION_DIRECTORY_NAME/$LOGS_DIRECTORY_NAME",
    )
    val scriptDirectory: File = File(
        sharedStorageRoot(),
        "$CONFIGURATION_DIRECTORY_NAME/$SCRIPTS_DIRECTORY_NAME",
    )
    val scriptImportDirectory: File = File(scriptDirectory, SCRIPT_IMPORT_DIRECTORY_NAME)
    val scriptExportDirectory: File = File(scriptDirectory, SCRIPT_EXPORT_DIRECTORY_NAME)
    val automationDirectory: File = File(
        sharedStorageRoot(),
        "$CONFIGURATION_DIRECTORY_NAME/$AUTOMATION_DIRECTORY_NAME",
    )
    val automationPolicyDirectory: File = File(automationDirectory, AUTOMATION_POLICIES_DIRECTORY_NAME)
    val automationScriptDirectory: File = File(automationDirectory, AUTOMATION_SCRIPTS_DIRECTORY_NAME)
    val themeDirectory: File = File(
        sharedStorageRoot(),
        "$CONFIGURATION_DIRECTORY_NAME/$THEMES_DIRECTORY_NAME",
    )
    val wallpaperDirectory: File = File(
        sharedStorageRoot(),
        "$CONFIGURATION_DIRECTORY_NAME/${DikcizLauncherBackgroundCodec.WALLPAPERS_DIRECTORY_NAME}",
    )
    val widgetLibraryDirectory: File = File(
        sharedStorageRoot(),
        "$CONFIGURATION_DIRECTORY_NAME/$WIDGET_LIBRARY_DIRECTORY_NAME",
    )
    val widgetBlockDirectory: File = File(
        sharedStorageRoot(),
        "$CONFIGURATION_DIRECTORY_NAME/${DikcizHtmlBlockLibrary.DIRECTORY_NAME}",
    )
    private val htmlWidgetLibrary by lazy {
        DikcizHtmlWidgetLibrary(context, widgetLibraryDirectory)
    }
    private val htmlBlockLibrary by lazy {
        DikcizHtmlBlockLibrary(context, widgetBlockDirectory)
    }
    private val pagesDirectory = File(
        sharedStorageRoot(),
        "$CONFIGURATION_DIRECTORY_NAME/$PAGES_DIRECTORY_NAME",
    )
    private val starterIdentityFile = File(
        sharedStorageRoot(),
        "$CONFIGURATION_DIRECTORY_NAME/$STARTER_IDENTITY_FILE_NAME",
    )

    fun hasSharedStorageAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
        return Environment.isExternalStorageManager()
    }

    fun loadOrCreate(): HomeConfiguration {
        requireSharedStorageAccess()
        ensureConfigurationDirectory()
        ensurePublicDirectory(pagesDirectory, "pages")
        ensurePublicDirectory(fontDirectory, "fonts")
        ensurePublicDirectory(scriptDirectory, SCRIPTS_DIRECTORY_NAME)
        ensurePublicDirectory(automationDirectory, AUTOMATION_DIRECTORY_NAME)
        ensurePublicDirectory(automationPolicyDirectory, AUTOMATION_POLICIES_DIRECTORY_NAME)
        ensurePublicDirectory(automationScriptDirectory, AUTOMATION_SCRIPTS_DIRECTORY_NAME)
        ensurePublicDirectory(themeDirectory, "themes")
        ensurePublicDirectory(widgetLibraryDirectory, WIDGET_LIBRARY_DIRECTORY_NAME)
        ensurePublicDirectory(widgetBlockDirectory, DikcizHtmlBlockLibrary.DIRECTORY_NAME)
        ensureWallpaperDirectory()
        htmlWidgetLibrary.installBundledPackages()
        htmlBlockLibrary.installBundledBlocks()
        if (!configurationFile.exists()) {
            saveBundledStarter()
        } else {
            reconcileBundledStarter()
        }
        if (!configurationFile.isFile) {
            throw HomeConfigException("${configurationFile.absolutePath} is not a file")
        }
        if (configurationFile.length() > MAXIMUM_CONFIGURATION_DOCUMENT_BYTES) {
            throw HomeConfigException(
                "${CONFIGURATION_FILE_NAME} exceeds the $MAXIMUM_CONFIGURATION_DOCUMENT_BYTES-byte recovery limit",
            )
        }

        val configuration = loadConfigurationTree()
        if (serializeConfiguration(configuration).toByteArray(StandardCharsets.UTF_8).size >
            configuration.limits.maxConfigBytes
        ) {
            throw HomeConfigException(
                "${CONFIGURATION_FILE_NAME} exceeds its configured maxConfigBytes limit",
            )
        }
        return configuration
    }

    fun loadBundledConfiguration(): HomeConfiguration {
        return parseConfiguration(readBundledConfiguration())
    }

    /**
     * Identity of the bundled starter that produced the saved home, plus whether the saved
     * home is still exactly that starter.
     */
    fun bundledStarterState(): DikcizBundledStarterState {
        val recorded = readStarterIdentity()
        return DikcizBundledStarterState(
            packagedDigest = bundledStarterDigest(),
            seededDigest = recorded?.digest,
            isPristine = recorded?.isPristine ?: false,
        )
    }

    private fun saveBundledStarter() {
        save(loadBundledConfiguration())
        writeStarterIdentity(bundledStarterDigest(), isPristine = true)
    }

    /**
     * The public tree is seeded once and then owned by the user, so a newer build would
     * otherwise keep rendering a home produced by an older bundled starter forever. Replace
     * it only while it is still untouched, so a customised home is never silently discarded.
     */
    private fun reconcileBundledStarter() {
        val state = bundledStarterState()
        if (state.matchesPackagedStarter) {
            return
        }
        if (!state.isPristine) {
            return
        }
        saveBundledStarter()
    }

    private fun readStarterIdentity(): StarterIdentity? {
        if (!starterIdentityFile.isFile ||
            starterIdentityFile.length() > MAXIMUM_STARTER_IDENTITY_BYTES
        ) {
            return null
        }
        return try {
            val document = JSONObject(starterIdentityFile.readText(StandardCharsets.UTF_8))
            StarterIdentity(
                digest = document.getString(STARTER_DIGEST_KEY),
                isPristine = document.getBoolean(STARTER_PRISTINE_KEY),
            )
        } catch (_: JSONException) {
            null
        } catch (_: java.io.IOException) {
            null
        }
    }

    private fun writeStarterIdentity(digest: String, isPristine: Boolean) {
        val document = JSONObject()
            .put(STARTER_DIGEST_KEY, digest)
            .put(STARTER_PRISTINE_KEY, isPristine)
        try {
            writeAtomicFile(starterIdentityFile, document.toString())
        } catch (_: HomeConfigException) {
            // The starter marker is a recovery hint. A home that saved correctly must not
            // fail because the hint could not be written.
        }
    }

    private fun bundledStarterDigest(): String {
        val digest = MessageDigest.getInstance(STARTER_DIGEST_ALGORITHM)
            .digest(readBundledConfiguration().toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString(EMPTY_DIGEST_SEPARATOR) { byte ->
            String.format(Locale.ROOT, DIGEST_BYTE_FORMAT, byte)
        }
    }

    private data class StarterIdentity(
        val digest: String,
        val isPristine: Boolean,
    )

    fun resetToBundledConfiguration(
        currentConfiguration: HomeConfiguration?,
    ): HomeConfiguration {
        requireSharedStorageAccess()
        val bundledConfiguration = loadBundledConfiguration()
        val configuration = currentConfiguration?.copy(
            launcherBackground = bundledConfiguration.launcherBackground,
            homePageID = bundledConfiguration.homePageID,
            selectedPageID = bundledConfiguration.selectedPageID,
            selectedThemeID = bundledConfiguration.selectedThemeID,
            styleDefaults = bundledConfiguration.styleDefaults,
            pages = bundledConfiguration.pages,
            scripts = resetScripts(
                currentScripts = currentConfiguration.scripts,
                bundledScripts = bundledConfiguration.scripts,
            ),
            automation = resetAutomation(
                currentAutomation = currentConfiguration.automation,
                bundledAutomation = bundledConfiguration.automation,
            ),
        ) ?: bundledConfiguration
        save(configuration)
        htmlWidgetLibrary.installBundledPackages()
        return configuration
    }

    fun htmlWidgetPackages(): List<DikcizHtmlWidgetPackage> {
        requireSharedStorageAccess()
        return htmlWidgetLibrary.listPackages()
    }

    fun htmlBlocks(): List<DikcizHtmlBlock> {
        requireSharedStorageAccess()
        htmlBlockLibrary.installBundledBlocks()
        return htmlBlockLibrary.listBlocks()
    }

    fun markedHtmlBlockFragment(block: DikcizHtmlBlock, instanceID: String): String {
        return htmlBlockLibrary.markedFragment(block, instanceID)
    }

    fun loadHtmlWidgetPackage(packageID: String): DikcizHtmlWidgetPackage {
        requireSharedStorageAccess()
        return htmlWidgetLibrary.loadPackage(packageID)
    }

    fun saveHtmlWidgetPackage(
        widget: HtmlHomeWidget,
        packageID: String,
    ): DikcizHtmlWidgetPackage {
        requireSharedStorageAccess()
        return htmlWidgetLibrary.savePackage(widget, packageID)
    }

    private fun resetScripts(
        currentScripts: List<DikcizLuaScript>,
        bundledScripts: List<DikcizLuaScript>,
    ): List<DikcizLuaScript> {
        return bundledScripts + currentScripts.filter { currentScript ->
            bundledScripts.none { bundledScript ->
                bundledScript.id.equals(currentScript.id, ignoreCase = true)
            }
        }
    }

    private fun resetAutomation(
        currentAutomation: DikcizAutomationConfiguration,
        bundledAutomation: DikcizAutomationConfiguration,
    ): DikcizAutomationConfiguration {
        return bundledAutomation.copy(
            policies = bundledAutomation.policies + currentAutomation.policies.filter { currentPolicy ->
                bundledAutomation.policies.none { bundledPolicy ->
                    bundledPolicy.id.equals(currentPolicy.id, ignoreCase = true)
                }
            },
            scripts = bundledAutomation.scripts + currentAutomation.scripts.filter { currentSubscription ->
                bundledAutomation.scripts.none { bundledSubscription ->
                    bundledSubscription.scriptID.equals(currentSubscription.scriptID, ignoreCase = true)
                }
            },
        )
    }

    fun save(configuration: HomeConfiguration) {
        requireSharedStorageAccess()
        validateConfiguration(configuration)
        val serializedConfiguration = serializeConfiguration(configuration)
        val configurationBytes = serializedConfiguration.toByteArray(StandardCharsets.UTF_8)
        if (configurationBytes.size > configuration.limits.maxConfigBytes) {
            throw HomeConfigException(
                "${CONFIGURATION_FILE_NAME} exceeds its configured maxConfigBytes limit",
            )
        }
        ensureConfigurationDirectory()
        writeConfigurationTree(configuration)
        markStarterSuperseded()
    }

    /**
     * A selection changes only the root manifest, so it must not rewrite every public
     * page, widget, script, and automation document before the destination renders.
     */
    fun saveSelectedPage(configuration: HomeConfiguration) {
        requireSharedStorageAccess()
        if (configuration.pages.none { page -> page.id == configuration.selectedPageID }) {
            throw HomeConfigException(SELECTED_PAGE_ID_MISSING_MESSAGE)
        }
        val manifest = serializeManifest(configuration)
        if (manifest.toByteArray(StandardCharsets.UTF_8).size > configuration.limits.maxConfigBytes) {
            throw HomeConfigException(
                "${CONFIGURATION_FILE_NAME} exceeds its configured maxConfigBytes limit",
            )
        }
        ensureConfigurationDirectory()
        writeAtomicFileIfChanged(configurationFile, manifest)
        markStarterSuperseded()
    }

    private fun markStarterSuperseded() {
        val recorded = readStarterIdentity() ?: return
        if (!recorded.isPristine) {
            return
        }
        writeStarterIdentity(recorded.digest, isPristine = false)
    }

    fun readLuaScriptRunStatus(
        scriptID: String,
        limits: DikcizLimits,
    ): DikcizLuaScriptRunStatus {
        return try {
            requireSharedStorageAccess()
            validateIdentifier(scriptID, limits, "script id")
            val file = scriptRunStatusFile(scriptID)
            if (!file.isFile || file.length() > MAXIMUM_SCRIPT_RUN_STATUS_BYTES) {
                return DikcizLuaScriptRunStatus.NotRun
            }
            parseLuaScriptRunStatus(
                readJsonDocument(file, "$SCRIPTS_DIRECTORY_NAME/$scriptID/$SCRIPT_RUN_STATUS_FILE_NAME"),
                limits,
            )
        } catch (_: HomeConfigException) {
            DikcizLuaScriptRunStatus.NotRun
        }
    }

    fun writeLuaScriptRunStatus(
        scriptID: String,
        status: DikcizLuaScriptRunStatus,
        limits: DikcizLimits,
    ) {
        requireSharedStorageAccess()
        validateIdentifier(scriptID, limits, "script id")
        validateLuaScriptRunStatus(status, limits)
        writeAtomicFileIfChanged(
            scriptRunStatusFile(scriptID),
            status.toJson().toString(JSON_INDENTATION_SPACES),
        )
    }

    fun readConfigurationDocument(): JSONObject {
        val configuration = loadOrCreate()
        return configurationDocument(configuration)
    }

    fun configurationDocument(configuration: HomeConfiguration): JSONObject =
        JSONObject(serializeConfiguration(configuration))

    fun hasSameSerializedConfiguration(
        first: HomeConfiguration,
        second: HomeConfiguration,
    ): Boolean = serializeConfiguration(first) == serializeConfiguration(second)

    fun hasSameStaticConfiguration(
        first: HomeConfiguration,
        second: HomeConfiguration,
    ): Boolean = serializeConfiguration(first.withoutRuntimeState()) ==
        serializeConfiguration(second.withoutRuntimeState())

    fun replaceConfigurationDocument(document: JSONObject): HomeConfiguration {
        requireSharedStorageAccess()
        val serializedConfiguration = document.toString(JSON_INDENTATION_SPACES)
        if (serializedConfiguration.toByteArray(StandardCharsets.UTF_8).size > MAXIMUM_CONFIGURATION_DOCUMENT_BYTES) {
            throw HomeConfigException(
                "$CONFIGURATION_FILE_NAME exceeds the $MAXIMUM_CONFIGURATION_DOCUMENT_BYTES-byte recovery limit",
            )
        }
        val configuration = parseConfiguration(serializedConfiguration)
        save(configuration)
        return configuration
    }

    fun availableLuaScriptArchives(): List<File> {
        requireSharedStorageAccess()
        ensurePublicDirectory(scriptImportDirectory, SCRIPT_IMPORT_DIRECTORY_NAME)
        return scriptImportDirectory.listFiles()
            ?.filter(::isLuaScriptArchiveFile)
            ?.sortedBy { file -> file.name.lowercase(Locale.ROOT) }
            .orEmpty()
    }

    fun exportLuaScriptArchive(
        script: DikcizLuaScript,
        limits: DikcizLimits,
    ): File {
        requireSharedStorageAccess()
        validateScripts(listOf(script), limits)
        ensurePublicDirectory(scriptExportDirectory, SCRIPT_EXPORT_DIRECTORY_NAME)
        val target = nextScriptArchiveFile(script.id)
        val atomicFile = AtomicFile(target)
        val output = try {
            atomicFile.startWrite()
        } catch (exception: Exception) {
            throw HomeConfigException("could not create script archive", exception)
        }
        try {
            ZipOutputStream(output).apply {
                writeScriptArchiveEntry(
                    SCRIPT_ARCHIVE_MANIFEST_FILE_NAME,
                    JSONObject()
                        .put(SCRIPT_ARCHIVE_VERSION_KEY, CURRENT_SCRIPT_ARCHIVE_VERSION)
                        .put(SCRIPT_ID_KEY, script.id)
                        .put(SCRIPT_ARCHIVE_KIND_KEY, SCRIPT_ARCHIVE_KIND)
                        .toString(JSON_INDENTATION_SPACES),
                )
                writeScriptArchiveEntry(
                    SCRIPT_METADATA_FILE_NAME,
                    script.toMetadataJson().toString(JSON_INDENTATION_SPACES),
                )
                writeScriptArchiveEntry(SCRIPT_SOURCE_FILE_NAME, script.source)
                writeScriptArchiveEntry(
                    SCRIPT_STATE_FILE_NAME,
                    script.serializedState,
                )
                finish()
            }
            atomicFile.finishWrite(output)
        } catch (exception: Exception) {
            atomicFile.failWrite(output)
            throw HomeConfigException("could not export script archive", exception)
        }
        return target
    }

    fun readLuaScriptArchive(
        archive: File,
        limits: DikcizLimits,
    ): DikcizLuaScript {
        requireSharedStorageAccess()
        requireScriptArchiveImportFile(archive)
        if (archive.length() > MAXIMUM_SCRIPT_ARCHIVE_BYTES) {
            throw HomeConfigException("script archive exceeds the size limit")
        }
        val entries = readLuaScriptArchiveEntries(archive)
        val manifest = parseScriptArchiveJson(
            entries.getValue(SCRIPT_ARCHIVE_MANIFEST_FILE_NAME),
            SCRIPT_ARCHIVE_MANIFEST_FILE_NAME,
        )
        manifest.requireOnlyKeys(SCRIPT_ARCHIVE_MANIFEST_KEYS, SCRIPT_ARCHIVE_MANIFEST_FILE_NAME)
        if (
            manifest.requireInt(SCRIPT_ARCHIVE_VERSION_KEY, SCRIPT_ARCHIVE_MANIFEST_FILE_NAME) !=
            CURRENT_SCRIPT_ARCHIVE_VERSION
        ) {
            throw HomeConfigException("script archive version is unsupported")
        }
        if (
            manifest.requireString(
                SCRIPT_ARCHIVE_KIND_KEY,
                SCRIPT_ARCHIVE_MANIFEST_FILE_NAME,
                MAXIMUM_SCRIPT_ARCHIVE_KIND_LENGTH,
            ) != SCRIPT_ARCHIVE_KIND
        ) {
            throw HomeConfigException("script archive kind is unsupported")
        }
        val manifestScriptID = manifest.requireIdentifier(
            SCRIPT_ID_KEY,
            SCRIPT_ARCHIVE_MANIFEST_FILE_NAME,
            limits,
        )
        val metadata = parseScriptArchiveJson(
            entries.getValue(SCRIPT_METADATA_FILE_NAME),
            SCRIPT_METADATA_FILE_NAME,
        )
        metadata.requireOnlyKeys(SCRIPT_METADATA_KEYS, SCRIPT_METADATA_FILE_NAME)
        if (metadata.requireIdentifier(ID_KEY, SCRIPT_METADATA_FILE_NAME, limits) != manifestScriptID) {
            throw HomeConfigException("script archive identifiers do not match")
        }
        return JSONObject(metadata.toString())
            .put(SCRIPT_SOURCE_KEY, String(entries.getValue(SCRIPT_SOURCE_FILE_NAME), StandardCharsets.UTF_8))
            .put(
                STATE_KEY,
                parseScriptArchiveJson(
                    entries.getValue(SCRIPT_STATE_FILE_NAME),
                    SCRIPT_STATE_FILE_NAME,
                ),
            )
            .parseScript(SCRIPT_ARCHIVE_MANIFEST_FILE_NAME, limits)
    }

    private fun requireScriptArchiveImportFile(archive: File) {
        val importDirectory = canonicalScriptArchiveFile(scriptImportDirectory)
        val archiveParentDirectory = archive.parentFile?.let(::canonicalScriptArchiveFile)
        if (
            archiveParentDirectory != importDirectory ||
            !isLuaScriptArchiveFile(archive)
        ) {
            throw HomeConfigException("script archive must be in the public imports directory")
        }
    }

    private fun canonicalScriptArchiveFile(file: File): File {
        return try {
            file.canonicalFile
        } catch (exception: Exception) {
            throw HomeConfigException("script archive path could not be resolved", exception)
        }
    }

    private fun isLuaScriptArchiveFile(file: File): Boolean {
        return file.isFile &&
            file.name.length <= MAXIMUM_SCRIPT_ARCHIVE_FILE_NAME_CHARACTERS &&
            SCRIPT_ARCHIVE_FILE_NAME_PATTERN.matches(file.name)
    }

    private fun readLuaScriptArchiveEntries(archive: File): Map<String, ByteArray> {
        return try {
            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(archive.inputStream()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (entry.isDirectory || entry.name !in SCRIPT_ARCHIVE_ENTRY_NAMES) {
                        throw HomeConfigException("script archive has an invalid entry")
                    }
                    if (entries.containsKey(entry.name)) {
                        throw HomeConfigException("script archive has a duplicate entry")
                    }
                    entries[entry.name] = readScriptArchiveEntry(input, entry)
                    input.closeEntry()
                }
            }
            if (entries.keys != SCRIPT_ARCHIVE_ENTRY_NAMES) {
                throw HomeConfigException("script archive is incomplete")
            }
            entries
        } catch (exception: HomeConfigException) {
            throw exception
        } catch (exception: Exception) {
            throw HomeConfigException("script archive could not be read", exception)
        }
    }

    private fun readScriptArchiveEntry(
        input: ZipInputStream,
        entry: ZipEntry,
    ): ByteArray {
        val maximumBytes = maximumScriptArchiveEntryBytes(entry.name)
        val output = ByteArrayOutputStream(maximumBytes)
        val buffer = ByteArray(SCRIPT_ARCHIVE_READ_BUFFER_BYTES)
        while (true) {
            val bytesRead = input.read(buffer)
            if (bytesRead == END_OF_STREAM) {
                return output.toByteArray()
            }
            if (output.size() + bytesRead > maximumBytes) {
                throw HomeConfigException("script archive entry exceeds the size limit")
            }
            output.write(buffer, 0, bytesRead)
        }
    }

    private fun maximumScriptArchiveEntryBytes(entryName: String): Int {
        return when (entryName) {
            SCRIPT_ARCHIVE_MANIFEST_FILE_NAME -> MAXIMUM_SCRIPT_ARCHIVE_MANIFEST_BYTES
            SCRIPT_METADATA_FILE_NAME -> MAXIMUM_SCRIPT_ARCHIVE_METADATA_BYTES
            SCRIPT_SOURCE_FILE_NAME -> MAXIMUM_LUA_SCRIPT_SOURCE_BYTES.toInt()
            SCRIPT_STATE_FILE_NAME -> MAXIMUM_SCRIPT_STATE_BYTES
            else -> throw HomeConfigException("script archive has an invalid entry")
        }
    }

    private fun parseScriptArchiveJson(bytes: ByteArray, context: String): JSONObject {
        return try {
            JSONObject(String(bytes, StandardCharsets.UTF_8))
        } catch (exception: JSONException) {
            throw HomeConfigException("$context is not valid JSON", exception)
        }
    }

    private fun nextScriptArchiveFile(scriptID: String): File {
        repeat(MAXIMUM_SCRIPT_ARCHIVE_NAME_ATTEMPTS) { index ->
            val file = File(
                scriptExportDirectory,
                "$scriptID-${index + FIRST_SCRIPT_ARCHIVE_INDEX}$SCRIPT_ARCHIVE_FILE_EXTENSION",
            )
            if (!file.exists()) {
                return file
            }
        }
        throw HomeConfigException("could not allocate a script archive name")
    }

    private fun ZipOutputStream.writeScriptArchiveEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(StandardCharsets.UTF_8))
        closeEntry()
    }

    fun configurationWatchDirectories(configuration: HomeConfiguration): List<File> {
        return buildList {
            add(configurationFile.parentFile ?: return@buildList)
            add(pagesDirectory)
            add(scriptDirectory)
            add(automationDirectory)
            add(automationPolicyDirectory)
            add(automationScriptDirectory)
            add(themeDirectory)
            add(wallpaperDirectory)
            add(fontDirectory)
            configuration.pages.forEach { page ->
                val pageDirectory = pageDirectory(page.id)
                add(pageDirectory)
                add(widgetDirectory(page.id))
            }
            configuration.scripts.forEach { script -> add(scriptDirectory(script.id)) }
        }.distinctBy(File::getAbsolutePath)
    }

    private fun requireSharedStorageAccess() {
        if (!hasSharedStorageAccess()) {
            throw HomeConfigException("shared-storage access is not granted")
        }
    }

    private fun ensureConfigurationDirectory() {
        val directory = configurationFile.parentFile
            ?: throw HomeConfigException("configuration directory is unavailable")
        if (directory.exists() && !directory.isDirectory) {
            throw HomeConfigException("${directory.absolutePath} is not a directory")
        }
        if (!directory.exists() && !directory.mkdirs()) {
            throw HomeConfigException("could not create ${directory.absolutePath}")
        }
    }

    private fun ensurePublicDirectory(directory: File, name: String) {
        if (directory.exists() && !directory.isDirectory) {
            throw HomeConfigException("$name directory is unavailable")
        }
        if (!directory.exists() && !directory.mkdirs()) {
            throw HomeConfigException("$name directory could not be created")
        }
    }

    private fun ensureWallpaperDirectory() {
        if (wallpaperDirectory.exists() && !wallpaperDirectory.isDirectory) {
            throw HomeConfigException("wallpapers directory is unavailable")
        }
        if (!wallpaperDirectory.exists() && !wallpaperDirectory.mkdirs()) {
            throw HomeConfigException("wallpapers directory could not be created")
        }
    }

    private fun readBundledConfiguration(): String {
        return context.assets.open(BUNDLED_CONFIGURATION_FILE_NAME)
            .bufferedReader(StandardCharsets.UTF_8)
            .use { it.readText() }
    }

    private fun writeAtomicFile(target: File, content: String) {
        target.parentFile?.let { parent -> ensurePublicDirectory(parent, parent.name) }
        val atomicFile = AtomicFile(target)
        val output = try {
            atomicFile.startWrite()
        } catch (exception: Exception) {
            throw configurationWriteFailure(target, exception)
        }
        try {
            output.write(content.toByteArray(StandardCharsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (exception: Exception) {
            atomicFile.failWrite(output)
            throw configurationWriteFailure(target, exception)
        }
    }

    private fun configurationWriteFailure(target: File, exception: Exception): HomeConfigException {
        val detail = exception.message.orEmpty().take(MAXIMUM_WRITE_FAILURE_DETAIL_CHARACTERS)
        return HomeConfigException(
            "could not save ${target.absolutePath}: ${exception::class.java.simpleName}: $detail",
            exception,
        )
    }

    private fun writeAtomicFileIfChanged(target: File, content: String) {
        if (hasSameStoredContent(target, content)) {
            return
        }
        writeAtomicFile(target, content)
    }

    /**
     * Answers whether the file on disk already holds exactly this content.
     *
     * The answer only decides whether a write can be skipped, so a read that
     * fails answers no and the write goes ahead. Shared storage is allowed to
     * fail: a public file can be replaced or truncated by anyone, and a
     * process killed mid-write leaves one that returns an I/O error on the
     * next read. Letting that escape from a comparison would end the launcher
     * over work it was about to redo anyway.
     */
    private fun hasSameStoredContent(target: File, content: String): Boolean {
        if (!target.isFile || target.length() > MAXIMUM_CONFIGURATION_DOCUMENT_BYTES) {
            return false
        }
        return try {
            target.readText(StandardCharsets.UTF_8) == content
        } catch (_: IOException) {
            false
        }
    }

    private fun loadConfigurationTree(): HomeConfiguration {
        val root = readJsonDocument(configurationFile, CONFIGURATION_FILE_NAME)
        val limits = root.requireObject(LIMITS_NAMESPACE_KEY, CONFIGURATION_FILE_NAME).parseLimits()
        val launcher = root.requireObject(LAUNCHER_NAMESPACE_KEY, CONFIGURATION_FILE_NAME)
        val home = launcher.requireObject(HOME_NAMESPACE_KEY, LAUNCHER_NAMESPACE_KEY)
        val pageReferences = home.requireArray(
            PAGES_KEY,
            "$LAUNCHER_NAMESPACE_KEY.$HOME_NAMESPACE_KEY",
        )
        val expandedPages = JSONArray()
        repeat(pageReferences.length()) { index ->
            val pageID = pageReferences.requireIdentifierAt(index, "$PAGES_KEY[$index]", limits)
            expandedPages.put(loadExpandedPage(pageID, limits))
        }
        val scriptReferences = root.optJSONArray(SCRIPTS_NAMESPACE_KEY) ?: JSONArray()
        val expandedScripts = JSONArray()
        repeat(scriptReferences.length()) { index ->
            val scriptID = scriptReferences.requireIdentifierAt(index, "$SCRIPTS_NAMESPACE_KEY[$index]", limits)
            expandedScripts.put(loadExpandedScript(scriptID, limits))
        }
        val automation = root.optJSONObject(AUTOMATION_NAMESPACE_KEY) ?: JSONObject()
        val expandedAutomation = loadExpandedAutomation(automation, limits)
        val expandedHome = JSONObject(home.toString()).put(PAGES_KEY, expandedPages)
        val expandedLauncher = JSONObject(launcher.toString()).put(HOME_NAMESPACE_KEY, expandedHome)
        val expandedRoot = JSONObject(root.toString())
            .put(LAUNCHER_NAMESPACE_KEY, expandedLauncher)
            .put(SCRIPTS_NAMESPACE_KEY, expandedScripts)
            .put(AUTOMATION_NAMESPACE_KEY, expandedAutomation)
        return parseConfiguration(expandedRoot.toString())
    }

    private fun loadExpandedAutomation(root: JSONObject, limits: DikcizLimits): JSONObject {
        if (root.length() == EMPTY_OBJECT_LENGTH) {
            return JSONObject()
        }
        root.requireOnlyKeys(AUTOMATION_MANIFEST_KEYS, AUTOMATION_NAMESPACE_KEY)
        val policyReferences = root.requireArray(AUTOMATION_POLICIES_KEY, AUTOMATION_NAMESPACE_KEY)
        val scriptReferences = root.requireArray(AUTOMATION_SCRIPTS_KEY, AUTOMATION_NAMESPACE_KEY)
        val policies = JSONArray()
        repeat(policyReferences.length()) { index ->
            val policyID = policyReferences.requireIdentifierAt(
                index,
                "$AUTOMATION_NAMESPACE_KEY.$AUTOMATION_POLICIES_KEY[$index]",
                limits,
            )
            policies.put(loadExpandedAutomationPolicy(policyID, limits))
        }
        val scripts = JSONArray()
        repeat(scriptReferences.length()) { index ->
            val scriptID = scriptReferences.requireIdentifierAt(
                index,
                "$AUTOMATION_NAMESPACE_KEY.$AUTOMATION_SCRIPTS_KEY[$index]",
                limits,
            )
            scripts.put(loadExpandedAutomationScript(scriptID, limits))
        }
        return JSONObject()
            .put(AUTOMATION_API_VERSION_KEY, root.requireInt(AUTOMATION_API_VERSION_KEY, AUTOMATION_NAMESPACE_KEY))
            .put(AUTOMATION_POLICIES_KEY, policies)
            .put(AUTOMATION_SCRIPTS_KEY, scripts)
    }

    private fun loadExpandedAutomationPolicy(policyID: String, limits: DikcizLimits): JSONObject {
        val policy = readJsonDocument(
            File(automationPolicyDirectory, "$policyID.$JSON_FILE_EXTENSION"),
            "$AUTOMATION_DIRECTORY_NAME/$AUTOMATION_POLICIES_DIRECTORY_NAME/$policyID.$JSON_FILE_EXTENSION",
        )
        policy.requireOnlyKeys(AUTOMATION_POLICY_KEYS, "automation policy $policyID")
        if (policy.requireIdentifier(ID_KEY, "automation policy $policyID", limits) != policyID) {
            throw HomeConfigException("automation policy reference $policyID does not match its id")
        }
        return policy
    }

    private fun loadExpandedAutomationScript(scriptID: String, limits: DikcizLimits): JSONObject {
        val script = readJsonDocument(
            File(automationScriptDirectory, "$scriptID.$JSON_FILE_EXTENSION"),
            "$AUTOMATION_DIRECTORY_NAME/$AUTOMATION_SCRIPTS_DIRECTORY_NAME/$scriptID.$JSON_FILE_EXTENSION",
        )
        script.requireOnlyKeys(AUTOMATION_SCRIPT_KEYS, "automation script $scriptID")
        if (script.requireIdentifier(SCRIPT_ID_KEY, "automation script $scriptID", limits) != scriptID) {
            throw HomeConfigException("automation script reference $scriptID does not match its scriptId")
        }
        return script
    }

    private fun loadExpandedPage(pageID: String, limits: DikcizLimits): JSONObject {
        val pageFile = File(pageDirectory(pageID), PAGE_FILE_NAME)
        val page = readJsonDocument(pageFile, "$PAGES_DIRECTORY_NAME/$pageID/$PAGE_FILE_NAME")
        val persistedPageID = page.requireIdentifier(ID_KEY, PAGE_FILE_NAME, limits)
        if (persistedPageID != pageID) {
            throw HomeConfigException("page reference $pageID does not match page id $persistedPageID")
        }
        val widgetReferences = page.requireArray(WIDGETS_KEY, "page $pageID")
        val expandedWidgets = JSONArray()
        repeat(widgetReferences.length()) { index ->
            val widgetID = widgetReferences.requireIdentifierAt(
                index,
                "$WIDGETS_KEY[$index]",
                limits,
            )
            val widgetFile = File(widgetDirectory(pageID), "$widgetID.$JSON_FILE_EXTENSION")
            val widget = readJsonDocument(
                widgetFile,
                "$PAGES_DIRECTORY_NAME/$pageID/$WIDGETS_DIRECTORY_NAME/$widgetID.$JSON_FILE_EXTENSION",
            )
            val persistedWidgetID = widget.requireIdentifier(ID_KEY, "widget $widgetID", limits)
            if (persistedWidgetID != widgetID) {
                throw HomeConfigException(
                    "widget reference $widgetID does not match widget id $persistedWidgetID",
                )
            }
            expandedWidgets.put(widget)
        }
        return JSONObject(page.toString()).put(WIDGETS_KEY, expandedWidgets)
    }

    private fun readJsonDocument(file: File, context: String): JSONObject {
        if (!file.isFile) {
            throw HomeConfigException("$context is missing")
        }
        if (file.length() > MAXIMUM_CONFIGURATION_DOCUMENT_BYTES) {
            throw HomeConfigException("$context exceeds the document size limit")
        }
        return try {
            JSONObject(file.readText(StandardCharsets.UTF_8))
        } catch (exception: JSONException) {
            throw HomeConfigException("$context is not valid JSON", exception)
        } catch (exception: Exception) {
            throw HomeConfigException("$context could not be read", exception)
        }
    }

    private fun loadExpandedScript(scriptID: String, limits: DikcizLimits): JSONObject {
        val metadata = readJsonDocument(
            File(scriptDirectory(scriptID), SCRIPT_METADATA_FILE_NAME),
            "$SCRIPTS_DIRECTORY_NAME/$scriptID/$SCRIPT_METADATA_FILE_NAME",
        )
        metadata.requireOnlyKeys(SCRIPT_METADATA_KEYS, SCRIPT_METADATA_FILE_NAME)
        val persistedScriptID = metadata.requireIdentifier(ID_KEY, SCRIPT_METADATA_FILE_NAME, limits)
        if (persistedScriptID != scriptID) {
            throw HomeConfigException("script reference $scriptID does not match script id $persistedScriptID")
        }
        val sourceFile = File(scriptDirectory(scriptID), SCRIPT_SOURCE_FILE_NAME)
        if (!sourceFile.isFile) {
            throw HomeConfigException("$SCRIPTS_DIRECTORY_NAME/$scriptID/$SCRIPT_SOURCE_FILE_NAME is missing")
        }
        if (sourceFile.length() > maximumScriptSourceBytes(limits)) {
            throw HomeConfigException("script $scriptID exceeds the source size limit")
        }
        val source = try {
            sourceFile.readText(StandardCharsets.UTF_8)
        } catch (exception: Exception) {
            throw HomeConfigException("script $scriptID source could not be read", exception)
        }
        val state = loadExpandedScriptState(scriptID, metadata.has(API_VERSION_KEY), limits)
        return JSONObject(metadata.toString()).apply {
            if (!has(API_VERSION_KEY)) {
                put(API_VERSION_KEY, DikcizLuaScriptApi.CURRENT_VERSION)
            }
            put(SCRIPT_SOURCE_KEY, source)
            put(STATE_KEY, state)
        }
    }

    private fun loadExpandedScriptState(
        scriptID: String,
        requiresStateFile: Boolean,
        limits: DikcizLimits,
    ): JSONObject {
        val file = File(scriptDirectory(scriptID), SCRIPT_STATE_FILE_NAME)
        if (!file.exists() && !requiresStateFile) {
            return JSONObject()
        }
        if (!file.isFile) {
            throw HomeConfigException("$SCRIPTS_DIRECTORY_NAME/$scriptID/$SCRIPT_STATE_FILE_NAME is missing")
        }
        if (file.length() > MAXIMUM_SCRIPT_STATE_BYTES) {
            throw HomeConfigException("script $scriptID state exceeds the byte limit")
        }
        return readJsonDocument(
            file,
            "$SCRIPTS_DIRECTORY_NAME/$scriptID/$SCRIPT_STATE_FILE_NAME",
        ).also { state ->
            validateScriptState(state, "script $scriptID state", limits)
        }
    }

    private fun parseLuaScriptRunStatus(
        value: JSONObject,
        limits: DikcizLimits,
    ): DikcizLuaScriptRunStatus {
        value.requireOnlyKeys(SCRIPT_RUN_STATUS_KEYS, SCRIPT_RUN_STATUS_FILE_NAME)
        val outcome = DikcizLuaScriptRunOutcome.fromPersistedValue(
            value.requireString(
                SCRIPT_RUN_OUTCOME_KEY,
                SCRIPT_RUN_STATUS_FILE_NAME,
                MAXIMUM_SCRIPT_RUN_OUTCOME_CHARACTERS,
            ),
        ) ?: throw HomeConfigException("$SCRIPT_RUN_STATUS_FILE_NAME has an unknown outcome")
        val lastRunAt = if (value.isNull(SCRIPT_RUN_LAST_RUN_AT_KEY)) {
            null
        } else {
            value.requireString(
                SCRIPT_RUN_LAST_RUN_AT_KEY,
                SCRIPT_RUN_STATUS_FILE_NAME,
                MAXIMUM_SCRIPT_RUN_TIMESTAMP_CHARACTERS,
            )
        }
        val status = value.requireString(
            SCRIPT_RUN_STATUS_KEY,
            SCRIPT_RUN_STATUS_FILE_NAME,
            limits.maxTitleCharacters,
        )
        val result = DikcizLuaScriptRunStatus(lastRunAt, outcome, status)
        validateLuaScriptRunStatus(result, limits)
        return result
    }

    private fun validateLuaScriptRunStatus(
        status: DikcizLuaScriptRunStatus,
        limits: DikcizLimits,
    ) {
        status.lastRunAt?.let { value ->
            if (value.isBlank() || value.length > MAXIMUM_SCRIPT_RUN_TIMESTAMP_CHARACTERS) {
                throw HomeConfigException("$SCRIPT_RUN_STATUS_FILE_NAME has an invalid timestamp")
            }
        }
        validateTitle(status.status, limits, "script run status")
    }

    private fun writeConfigurationTree(configuration: HomeConfiguration) {
        configuration.automation.policies.forEach { policy ->
            writeAtomicFileIfChanged(
                File(automationPolicyDirectory, "${policy.id}.$JSON_FILE_EXTENSION"),
                policy.toJson().toString(JSON_INDENTATION_SPACES),
            )
        }
        configuration.automation.scripts.forEach { script ->
            writeAtomicFileIfChanged(
                File(automationScriptDirectory, "${script.scriptID}.$JSON_FILE_EXTENSION"),
                script.toJson().toString(JSON_INDENTATION_SPACES),
            )
        }
        configuration.scripts.forEach { script ->
            writeAtomicFileIfChanged(
                File(scriptDirectory(script.id), SCRIPT_METADATA_FILE_NAME),
                script.toMetadataJson().toString(JSON_INDENTATION_SPACES),
            )
            writeAtomicFileIfChanged(
                File(scriptDirectory(script.id), SCRIPT_SOURCE_FILE_NAME),
                script.source,
            )
            writeAtomicFileIfChanged(
                File(scriptDirectory(script.id), SCRIPT_STATE_FILE_NAME),
                script.state.toString(JSON_INDENTATION_SPACES),
            )
        }
        configuration.pages.forEach { page ->
            page.widgets.forEach { widget ->
                writeAtomicFileIfChanged(
                    File(widgetDirectory(page.id), "${widget.id}.$JSON_FILE_EXTENSION"),
                    widget.toJson().toString(JSON_INDENTATION_SPACES),
                )
            }
            writeAtomicFileIfChanged(
                File(pageDirectory(page.id), PAGE_FILE_NAME),
                page.toManifestJson().toString(JSON_INDENTATION_SPACES),
            )
        }
        writeAtomicFileIfChanged(
            configurationFile,
            serializeManifest(configuration),
        )
        pruneRemovedPageDocuments(configuration)
    }

    /**
     * Removes the page and widget documents the saved configuration no longer names.
     *
     * Writing alone would leave a file behind for every deleted widget and every
     * deleted page, so the public tree would keep growing with documents that
     * nothing loads. Combining two app tiles into a group removes two widgets at
     * once, which made the stale files easy to collect.
     *
     * Only documents this store writes are removed. A foreign file keeps its
     * directory alive, because the public tree is allowed to carry namespaces
     * this launcher does not own.
     */
    private fun pruneRemovedPageDocuments(configuration: HomeConfiguration) {
        val pageIDs = configuration.pages.mapTo(mutableSetOf(), HomePage::id)
        configuration.pages.forEach { page ->
            val keptWidgetFiles = page.widgets.mapTo(mutableSetOf()) { widget ->
                "${widget.id}.$JSON_FILE_EXTENSION"
            }
            deleteOwnedJsonFiles(widgetDirectory(page.id), keptWidgetFiles)
        }
        pagesDirectory.listFiles().orEmpty().forEach { directory ->
            if (!directory.isDirectory || directory.name in pageIDs) {
                return@forEach
            }
            deleteOwnedJsonFiles(File(directory, WIDGETS_DIRECTORY_NAME), emptySet())
            File(directory, WIDGETS_DIRECTORY_NAME).takeIf(::isEmptyDirectory)?.delete()
            File(directory, PAGE_FILE_NAME).delete()
            directory.takeIf(::isEmptyDirectory)?.delete()
        }
    }

    private fun deleteOwnedJsonFiles(directory: File, keptFileNames: Set<String>) {
        directory.listFiles().orEmpty().forEach { file ->
            if (!file.isFile || !file.name.endsWith(".$JSON_FILE_EXTENSION")) {
                return@forEach
            }
            if (file.name in keptFileNames) {
                return@forEach
            }
            file.delete()
        }
    }

    private fun isEmptyDirectory(directory: File): Boolean {
        return directory.isDirectory && directory.listFiles().orEmpty().isEmpty()
    }

    private fun pageDirectory(pageID: String): File = File(pagesDirectory, pageID)

    private fun scriptDirectory(scriptID: String): File = File(scriptDirectory, scriptID)

    private fun scriptRunStatusFile(scriptID: String): File {
        return File(scriptDirectory(scriptID), SCRIPT_RUN_STATUS_FILE_NAME)
    }

    private fun widgetDirectory(pageID: String): File = File(pageDirectory(pageID), WIDGETS_DIRECTORY_NAME)

    private fun parseConfiguration(rawConfiguration: String): HomeConfiguration {
        val root = try {
            JSONObject(rawConfiguration)
        } catch (exception: JSONException) {
            throw HomeConfigException("$CONFIGURATION_FILE_NAME is not valid JSON", exception)
        }
        val limits = root.requireObject(LIMITS_NAMESPACE_KEY, CONFIGURATION_FILE_NAME).parseLimits()
        val logging = root.parseLogging()
        val launcher = root.requireObject(LAUNCHER_NAMESPACE_KEY, CONFIGURATION_FILE_NAME)
        val home = launcher.requireObject(HOME_NAMESPACE_KEY, LAUNCHER_NAMESPACE_KEY)
        home.requireOnlyKeys(HOME_KEYS, "$LAUNCHER_NAMESPACE_KEY.$HOME_NAMESPACE_KEY")
        val selectedPageID = home.requireIdentifier(
            SELECTED_PAGE_ID_KEY,
            "$LAUNCHER_NAMESPACE_KEY.$HOME_NAMESPACE_KEY",
            limits,
        )
        val configuration = HomeConfiguration(
            version = root.requireInt(VERSION_KEY, CONFIGURATION_FILE_NAME),
            limits = limits,
            logging = logging,
            rootNamespaces = root.copyNamespacesExcept(
                setOf(
                    AUTOMATION_NAMESPACE_KEY,
                    VERSION_KEY,
                    LIMITS_NAMESPACE_KEY,
                    LOGGING_NAMESPACE_KEY,
                    LAUNCHER_NAMESPACE_KEY,
                    SCRIPTS_NAMESPACE_KEY,
                ),
                CONFIGURATION_FILE_NAME,
            ),
            launcherNamespaces = launcher.copyNamespacesExcept(
                setOf(HOME_NAMESPACE_KEY),
                LAUNCHER_NAMESPACE_KEY,
            ),
            launcherBackground = DikcizLauncherBackgroundCodec.parseOptional(
                home,
                "$LAUNCHER_NAMESPACE_KEY.$HOME_NAMESPACE_KEY",
            ),
            homePageID = home.optionalIdentifier(
                HOME_PAGE_ID_KEY,
                "$LAUNCHER_NAMESPACE_KEY.$HOME_NAMESPACE_KEY",
                limits,
            ) ?: selectedPageID,
            selectedPageID = selectedPageID,
            selectedThemeID = home.optionalIdentifier(
                SELECTED_THEME_ID_KEY,
                "$LAUNCHER_NAMESPACE_KEY.$HOME_NAMESPACE_KEY",
                limits,
            ),
            nativeGrid = home.parseNativeGrid("$LAUNCHER_NAMESPACE_KEY.$HOME_NAMESPACE_KEY"),
            styleDefaults = DikcizStyleCodec.parseOptionalStyleDefaults(
                home,
                "$LAUNCHER_NAMESPACE_KEY.$HOME_NAMESPACE_KEY",
            ),
            pages = home.requireArray(
                PAGES_KEY,
                "$LAUNCHER_NAMESPACE_KEY.$HOME_NAMESPACE_KEY",
            ).parsePages(limits),
            scripts = root.parseScripts(limits),
            automation = root.parseAutomation(limits),
        )
        validateConfiguration(configuration)
        return configuration
    }

    private fun JSONObject.parseLogging(): DikcizLoggingConfiguration {
        if (!has(LOGGING_NAMESPACE_KEY)) {
            return DEFAULT_LOGGING_CONFIGURATION
        }
        val logging = requireObject(LOGGING_NAMESPACE_KEY, CONFIGURATION_FILE_NAME)
        logging.requireOnlyKeys(LOGGING_KEYS, LOGGING_NAMESPACE_KEY)
        val levelValue = logging.requireString(
            LOG_LEVEL_KEY,
            LOGGING_NAMESPACE_KEY,
            MAXIMUM_LOG_LEVEL_LENGTH,
        )
        val level = DikcizLogLevel.fromPersistedValue(levelValue)
            ?: throw HomeConfigException("$LOGGING_NAMESPACE_KEY.$LOG_LEVEL_KEY has an unknown level")
        return DikcizLoggingConfiguration(
            level = level,
            retentionDays = logging.requireBoundedInt(
                LOG_RETENTION_DAYS_KEY,
                LOGGING_NAMESPACE_KEY,
                MINIMUM_LOG_RETENTION_DAYS,
                HARD_MAXIMUM_LOG_RETENTION_DAYS,
            ),
            maxTotalBytes = logging.requireBoundedInt(
                LOG_MAXIMUM_TOTAL_BYTES_KEY,
                LOGGING_NAMESPACE_KEY,
                MINIMUM_LOG_MAXIMUM_TOTAL_BYTES,
                HARD_MAXIMUM_LOG_MAXIMUM_TOTAL_BYTES,
            ),
            notifyOnScriptError = logging.optionalBoolean(
                LOG_NOTIFY_ON_SCRIPT_ERROR_KEY,
                LOGGING_NAMESPACE_KEY,
                DikcizLoggingConfiguration.DEFAULT_NOTIFY_ON_SCRIPT_ERROR,
            ),
            scriptErrorNotificationMinimumIntervalMilliseconds = logging.optionalBoundedInt(
                LOG_SCRIPT_ERROR_NOTIFICATION_MINIMUM_INTERVAL_MILLISECONDS_KEY,
                LOGGING_NAMESPACE_KEY,
                MINIMUM_SCRIPT_ERROR_NOTIFICATION_INTERVAL_MILLISECONDS,
                HARD_MAXIMUM_SCRIPT_ERROR_NOTIFICATION_INTERVAL_MILLISECONDS,
                DikcizLoggingConfiguration.DEFAULT_SCRIPT_ERROR_NOTIFICATION_MINIMUM_INTERVAL_MILLISECONDS,
            ),
        )
    }

    private fun JSONObject.parseAutomation(limits: DikcizLimits): DikcizAutomationConfiguration {
        if (!has(AUTOMATION_NAMESPACE_KEY)) {
            return DikcizAutomationConfiguration()
        }
        val automation = requireObject(AUTOMATION_NAMESPACE_KEY, CONFIGURATION_FILE_NAME)
        if (automation.length() == EMPTY_OBJECT_LENGTH) {
            return DikcizAutomationConfiguration()
        }
        automation.requireOnlyKeys(AUTOMATION_KEYS, AUTOMATION_NAMESPACE_KEY)
        val apiVersion = automation.requireBoundedInt(
            AUTOMATION_API_VERSION_KEY,
            AUTOMATION_NAMESPACE_KEY,
            DikcizAutomationApi.CURRENT_VERSION,
            DikcizAutomationApi.CURRENT_VERSION,
        )
        val policies = automation.requireArray(
            AUTOMATION_POLICIES_KEY,
            AUTOMATION_NAMESPACE_KEY,
        ).parseAutomationPolicies(limits)
        val scripts = automation.requireArray(
            AUTOMATION_SCRIPTS_KEY,
            AUTOMATION_NAMESPACE_KEY,
        ).parseAutomationScripts(limits)
        return DikcizAutomationConfiguration(apiVersion, policies, scripts)
    }

    private fun JSONArray.parseAutomationPolicies(limits: DikcizLimits): List<DikcizAutomationPolicy> {
        if (length() > MAXIMUM_AUTOMATION_POLICIES) {
            throw HomeConfigException("$AUTOMATION_NAMESPACE_KEY has too many policies")
        }
        return List(length()) { index ->
            val context = "$AUTOMATION_NAMESPACE_KEY.$AUTOMATION_POLICIES_KEY[$index]"
            getJSONObjectAt(index, context).parseAutomationPolicy(context, limits)
        }
    }

    private fun JSONObject.parseAutomationPolicy(
        context: String,
        limits: DikcizLimits,
    ): DikcizAutomationPolicy {
        requireOnlyKeys(AUTOMATION_POLICY_KEYS, context)
        val actions = requireAutomationActions(context)
        return DikcizAutomationPolicy(
            id = requireIdentifier(ID_KEY, context, limits),
            title = requireTitle(TITLE_KEY, context, limits),
            enabled = requireBoolean(ENABLED_KEY, context),
            capabilities = requireAutomationCapabilities(context),
            actions = actions,
            mediaSessionPackages = optionalAutomationMediaSessionPackages(context),
            notificationActionPackages = optionalAutomationNotificationActionPackages(context),
        )
    }

    private fun JSONObject.requireAutomationCapabilities(
        context: String,
    ): Set<DikcizAutomationCapability> {
        val values = requireArray(AUTOMATION_CAPABILITIES_KEY, context)
        if (values.length() > DikcizAutomationCapability.entries.size) {
            throw HomeConfigException("$context.$AUTOMATION_CAPABILITIES_KEY has too many values")
        }
        return buildSet {
            repeat(values.length()) { index ->
                val value = values.getStringAt(
                    index,
                    "$context.$AUTOMATION_CAPABILITIES_KEY[$index]",
                    MAXIMUM_AUTOMATION_VALUE_CHARACTERS,
                )
                val capability = DikcizAutomationCapability.fromPersistedValue(value)
                    ?: throw HomeConfigException("$context has an unknown automation capability")
                if (!add(capability)) {
                    throw HomeConfigException("$context has a duplicate automation capability")
                }
            }
        }
    }

    private fun JSONObject.requireAutomationActions(
        context: String,
    ): Set<DikcizAutomationActionCapability> {
        val values = requireArray(AUTOMATION_ACTIONS_KEY, context)
        if (values.length() > DikcizAutomationActionCapability.entries.size) {
            throw HomeConfigException("$context.$AUTOMATION_ACTIONS_KEY has too many values")
        }
        return buildSet {
            repeat(values.length()) { index ->
                val value = values.getStringAt(
                    index,
                    "$context.$AUTOMATION_ACTIONS_KEY[$index]",
                    MAXIMUM_AUTOMATION_VALUE_CHARACTERS,
                )
                val action = DikcizAutomationActionCapability.fromPersistedValue(value)
                    ?: throw HomeConfigException("$context has an unknown automation action")
                if (!add(action)) {
                    throw HomeConfigException("$context has a duplicate automation action")
                }
            }
        }
    }

    private fun JSONArray.parseAutomationScripts(limits: DikcizLimits): List<DikcizAutomationScript> {
        if (length() > MAXIMUM_AUTOMATION_SCRIPTS) {
            throw HomeConfigException("$AUTOMATION_NAMESPACE_KEY has too many script files")
        }
        return List(length()) { index ->
            val context = "$AUTOMATION_NAMESPACE_KEY.$AUTOMATION_SCRIPTS_KEY[$index]"
            getJSONObjectAt(index, context).parseAutomationScript(context, limits)
        }
    }

    private fun JSONObject.parseAutomationScript(
        context: String,
        limits: DikcizLimits,
    ): DikcizAutomationScript {
        requireOnlyKeys(AUTOMATION_SCRIPT_KEYS, context)
        val subscriptions = requireArray(AUTOMATION_SUBSCRIPTIONS_KEY, context)
        if (subscriptions.length() > MAXIMUM_AUTOMATION_SUBSCRIPTIONS) {
            throw HomeConfigException("$context has too many subscriptions")
        }
        return DikcizAutomationScript(
            scriptID = requireIdentifier(SCRIPT_ID_KEY, context, limits),
            policyID = requireIdentifier(AUTOMATION_POLICY_ID_KEY, context, limits),
            enabled = requireBoolean(ENABLED_KEY, context),
            subscriptions = List(subscriptions.length()) { index ->
                subscriptions.getJSONObjectAt(index, "$context.$AUTOMATION_SUBSCRIPTIONS_KEY[$index]")
                    .parseAutomationSubscription(
                        "$context.$AUTOMATION_SUBSCRIPTIONS_KEY[$index]",
                        limits,
                    )
            },
        )
    }

    private fun JSONObject.parseAutomationSubscription(
        context: String,
        limits: DikcizLimits,
    ): DikcizAutomationSubscription {
        requireOnlyKeys(AUTOMATION_SUBSCRIPTION_KEYS, context)
        val eventName = requireString(
            AUTOMATION_EVENT_KEY,
            context,
            DikcizAutomationEventNames.MAXIMUM_CUSTOM_EVENT_NAME_CHARACTERS,
        )
        val knownEvent = DikcizAutomationEventType.fromPersistedValue(eventName)
        val event = knownEvent ?: DikcizAutomationEventType.Custom
        val customEventName = if (knownEvent == null) {
            if (!DikcizAutomationEventNames.isValidCustomName(eventName)) {
                throw HomeConfigException("$context has an invalid custom event")
            }
            eventName
        } else {
            null
        }
        val sensorTypes = optionalAutomationSensorTypes(context)
        val samplingPeriod = if (has(AUTOMATION_SAMPLING_PERIOD_MICROSECONDS_KEY)) {
            requireBoundedInt(
                AUTOMATION_SAMPLING_PERIOD_MICROSECONDS_KEY,
                context,
                MINIMUM_AUTOMATION_SAMPLING_PERIOD_MICROSECONDS,
                MAXIMUM_AUTOMATION_SAMPLING_PERIOD_MICROSECONDS,
            )
        } else {
            null
        }
        val alarmInterval = optionalBoundedLong(
            AUTOMATION_ALARM_INTERVAL_MILLISECONDS_KEY,
            context,
            MINIMUM_AUTOMATION_ALARM_INTERVAL_MILLISECONDS,
            MAXIMUM_AUTOMATION_ALARM_INTERVAL_MILLISECONDS,
        )
        val healthRefreshInterval = optionalBoundedLong(
            AUTOMATION_HEALTH_REFRESH_INTERVAL_MILLISECONDS_KEY,
            context,
            MINIMUM_AUTOMATION_HEALTH_REFRESH_INTERVAL_MILLISECONDS,
            MAXIMUM_AUTOMATION_HEALTH_REFRESH_INTERVAL_MILLISECONDS,
        )
        val locationPrecision = optionalAutomationLocationPrecision(context)
        val packageNames = optionalAutomationPackageNames(context)
        validateAutomationSubscriptionShape(
            context,
            event,
            packageNames,
            sensorTypes,
            samplingPeriod,
            alarmInterval,
            locationPrecision,
            healthRefreshInterval,
        )
        return DikcizAutomationSubscription(
            event = event,
            minimumIntervalMilliseconds = requireBoundedLong(
                AUTOMATION_MINIMUM_INTERVAL_MILLISECONDS_KEY,
                context,
                MINIMUM_AUTOMATION_INTERVAL_MILLISECONDS,
                MAXIMUM_AUTOMATION_INTERVAL_MILLISECONDS,
            ),
            coalescingKey = optionalIdentifier(AUTOMATION_COALESCING_KEY, context, limits),
            packageNames = packageNames,
            sensorTypes = sensorTypes,
            samplingPeriodMicroseconds = samplingPeriod,
            alarmIntervalMilliseconds = alarmInterval,
            locationPrecision = locationPrecision,
            healthRefreshIntervalMilliseconds = healthRefreshInterval,
            customEventName = customEventName,
        )
    }

    private fun JSONObject.optionalAutomationSensorTypes(context: String): List<Int> {
        if (!has(AUTOMATION_SENSOR_TYPES_KEY)) {
            return emptyList()
        }
        val values = requireArray(AUTOMATION_SENSOR_TYPES_KEY, context)
        if (values.length() > MAXIMUM_AUTOMATION_SENSOR_TYPES) {
            throw HomeConfigException("$context has too many sensor types")
        }
        return List(values.length()) { index ->
            values.requireBoundedIntAt(
                index,
                "$context.$AUTOMATION_SENSOR_TYPES_KEY[$index]",
                MINIMUM_AUTOMATION_SENSOR_TYPE,
                MAXIMUM_AUTOMATION_SENSOR_TYPE,
            )
        }.also { types ->
            if (types.distinct().size != types.size) {
                throw HomeConfigException("$context has duplicate sensor types")
            }
        }
    }

    private fun JSONObject.optionalAutomationPackageNames(context: String): List<String> {
        if (!has(AUTOMATION_PACKAGES_KEY)) {
            return emptyList()
        }
        return requireArray(AUTOMATION_PACKAGES_KEY, context).parseAutomationPackageNames(
            "$context.$AUTOMATION_PACKAGES_KEY",
        )
    }

    private fun JSONObject.optionalAutomationMediaSessionPackages(context: String): List<String> {
        if (!has(AUTOMATION_MEDIA_SESSION_PACKAGES_KEY)) {
            return emptyList()
        }
        return requireArray(AUTOMATION_MEDIA_SESSION_PACKAGES_KEY, context).parseAutomationPackageNames(
            "$context.$AUTOMATION_MEDIA_SESSION_PACKAGES_KEY",
        )
    }

    private fun JSONObject.optionalAutomationNotificationActionPackages(context: String): List<String> {
        if (!has(AUTOMATION_NOTIFICATION_ACTION_PACKAGES_KEY)) {
            return emptyList()
        }
        return requireArray(AUTOMATION_NOTIFICATION_ACTION_PACKAGES_KEY, context).parseAutomationPackageNames(
            "$context.$AUTOMATION_NOTIFICATION_ACTION_PACKAGES_KEY",
        )
    }

    private fun JSONArray.parseAutomationPackageNames(context: String): List<String> {
        val packages = this
        if (packages.length() > MAXIMUM_AUTOMATION_PACKAGES) {
            throw HomeConfigException("$context has too many package names")
        }
        return List(packages.length()) { index ->
            val packageName = packages.getStringAt(
                index,
                "$context[$index]",
                MAXIMUM_AUTOMATION_PACKAGE_NAME_CHARACTERS,
            )
            if (!ANDROID_PACKAGE_NAME_PATTERN.matches(packageName)) {
                throw HomeConfigException("$context has an invalid package name")
            }
            packageName
        }.also { names ->
            if (names.map(String::lowercase).distinct().size != names.size) {
                throw HomeConfigException("$context has duplicate package names")
            }
        }
    }

    private fun JSONObject.optionalAutomationLocationPrecision(context: String): DikcizLocationPrecision? {
        if (!has(AUTOMATION_LOCATION_PRECISION_KEY)) {
            return null
        }
        val value = requireString(
            AUTOMATION_LOCATION_PRECISION_KEY,
            context,
            MAXIMUM_AUTOMATION_VALUE_CHARACTERS,
        )
        return DikcizLocationPrecision.fromPersistedValue(value)
            ?: throw HomeConfigException("$context has an unknown location precision")
    }

    private fun validateAutomationSubscriptionShape(
        context: String,
        event: DikcizAutomationEventType,
        packageNames: List<String>,
        sensorTypes: List<Int>,
        samplingPeriod: Int?,
        alarmInterval: Long?,
        locationPrecision: DikcizLocationPrecision?,
        healthRefreshInterval: Long?,
    ) {
        if (event == DikcizAutomationEventType.Sensor && sensorTypes.isEmpty()) {
            throw HomeConfigException("$context sensor subscriptions need sensorTypes")
        }
        if (event == DikcizAutomationEventType.Sensor && samplingPeriod == null) {
            throw HomeConfigException("$context sensor subscriptions need samplingPeriodMicroseconds")
        }
        if (event == DikcizAutomationEventType.Alarm && alarmInterval == null) {
            throw HomeConfigException("$context alarm subscriptions need alarmIntervalMilliseconds")
        }
        if (event == DikcizAutomationEventType.Location && locationPrecision == null) {
            throw HomeConfigException("$context location subscriptions need locationPrecision")
        }
        if (event == DikcizAutomationEventType.HealthDailySteps && healthRefreshInterval == null) {
            throw HomeConfigException("$context health subscriptions need healthRefreshIntervalMilliseconds")
        }
        if (event != DikcizAutomationEventType.Sensor && (sensorTypes.isNotEmpty() || samplingPeriod != null)) {
            throw HomeConfigException("$context sensor options only apply to sensor events")
        }
        if (event != DikcizAutomationEventType.Alarm && alarmInterval != null) {
            throw HomeConfigException("$context alarm options only apply to alarm events")
        }
        if (event != DikcizAutomationEventType.Location && locationPrecision != null) {
            throw HomeConfigException("$context location options only apply to location events")
        }
        if (event != DikcizAutomationEventType.HealthDailySteps && healthRefreshInterval != null) {
            throw HomeConfigException("$context health options only apply to health events")
        }
        if (
            event != DikcizAutomationEventType.NotificationPosted &&
            event != DikcizAutomationEventType.NotificationRemoved &&
            event != DikcizAutomationEventType.MediaSession &&
            packageNames.isNotEmpty()
        ) {
            throw HomeConfigException("$context package filters only apply to notification or media-session events")
        }
    }

    private fun JSONObject.parseScripts(limits: DikcizLimits): List<DikcizLuaScript> {
        if (!has(SCRIPTS_NAMESPACE_KEY)) {
            return emptyList()
        }
        val legacyScripts = opt(SCRIPTS_NAMESPACE_KEY)
        if (legacyScripts is JSONObject && legacyScripts.length() == EMPTY_OBJECT_LENGTH) {
            return emptyList()
        }
        val scripts = requireArray(SCRIPTS_NAMESPACE_KEY, CONFIGURATION_FILE_NAME)
        if (scripts.length() > MAXIMUM_SCRIPT_COUNT) {
            throw HomeConfigException("$SCRIPTS_NAMESPACE_KEY has more than $MAXIMUM_SCRIPT_COUNT scripts")
        }
        return List(scripts.length()) { index ->
            val context = "$SCRIPTS_NAMESPACE_KEY[$index]"
            scripts.getJSONObjectAt(index, context).parseScript(context, limits)
        }
    }

    private fun JSONObject.parseScript(
        context: String,
        limits: DikcizLimits,
    ): DikcizLuaScript {
        requireOnlyKeys(SCRIPT_KEYS, context)
        val source = requireText(SCRIPT_SOURCE_KEY, context, limits)
        validateScriptSource(source, context, limits)
        if (source.isBlank()) {
            throw HomeConfigException("$context.$SCRIPT_SOURCE_KEY must not be blank")
        }
        return DikcizLuaScript(
            id = requireIdentifier(ID_KEY, context, limits),
            title = requireTitle(TITLE_KEY, context, limits),
            enabled = requireBoolean(ENABLED_KEY, context),
            source = source,
            apiVersion = requireScriptApiVersion(context),
            state = parseScriptState(context, limits),
        )
    }

    private fun JSONObject.requireScriptApiVersion(context: String): Int {
        val apiVersion = requireBoundedInt(
            API_VERSION_KEY,
            context,
            MINIMUM_LUA_API_VERSION,
            DikcizLuaScriptApi.CURRENT_VERSION,
        )
        if (apiVersion != DikcizLuaScriptApi.CURRENT_VERSION) {
            throw HomeConfigException("$context.$API_VERSION_KEY is unsupported")
        }
        return apiVersion
    }

    private fun JSONObject.parseScriptState(context: String, limits: DikcizLimits): JSONObject {
        val state = requireObject(STATE_KEY, context)
        validateScriptState(state, "$context.$STATE_KEY", limits)
        return JSONObject(state.toString())
    }

    private fun validateScriptState(
        state: JSONObject,
        context: String,
        limits: DikcizLimits,
    ) {
        if (state.toString().toByteArray(StandardCharsets.UTF_8).size > MAXIMUM_SCRIPT_STATE_BYTES) {
            throw HomeConfigException("$context exceeds the byte limit")
        }
        validateScriptStateObject(state, context, limits, ROOT_SCRIPT_STATE_DEPTH)
    }

    private fun maximumScriptSourceBytes(limits: DikcizLimits): Long {
        return minOf(
            limits.maxTextCharacters.toLong(),
            MAXIMUM_LUA_SCRIPT_SOURCE_BYTES,
        )
    }

    private fun validateScriptSource(
        source: String,
        context: String,
        limits: DikcizLimits,
    ) {
        validateText(source, limits)
        if (source.toByteArray(StandardCharsets.UTF_8).size.toLong() > maximumScriptSourceBytes(limits)) {
            throw HomeConfigException("$context.$SCRIPT_SOURCE_KEY exceeds the source size limit")
        }
    }

    private fun validateScriptStateObject(
        value: JSONObject,
        context: String,
        limits: DikcizLimits,
        depth: Int,
    ) {
        if (depth > MAXIMUM_SCRIPT_STATE_DEPTH) {
            throw HomeConfigException("$context exceeds the nesting limit")
        }
        if (value.length() > MAXIMUM_SCRIPT_STATE_ENTRIES) {
            throw HomeConfigException("$context has too many entries")
        }
        val keys = value.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!SCRIPT_STATE_KEY_PATTERN.matches(key)) {
                throw HomeConfigException("$context has an invalid state key")
            }
            validateScriptStateValue(value.get(key), "$context.$key", limits, depth + ONE_STATE_LEVEL)
        }
    }

    private fun validateScriptStateArray(
        value: JSONArray,
        context: String,
        limits: DikcizLimits,
        depth: Int,
    ) {
        if (depth > MAXIMUM_SCRIPT_STATE_DEPTH) {
            throw HomeConfigException("$context exceeds the nesting limit")
        }
        if (value.length() > MAXIMUM_SCRIPT_STATE_ENTRIES) {
            throw HomeConfigException("$context has too many entries")
        }
        repeat(value.length()) { index ->
            validateScriptStateValue(value.get(index), "$context[$index]", limits, depth + ONE_STATE_LEVEL)
        }
    }

    private fun validateScriptStateValue(
        value: Any,
        context: String,
        limits: DikcizLimits,
        depth: Int,
    ) {
        when (value) {
            is JSONObject -> validateScriptStateObject(value, context, limits, depth)
            is JSONArray -> validateScriptStateArray(value, context, limits, depth)
            is String -> {
                if (value.length > limits.maxTextCharacters || value.contains(NUL_CHARACTER)) {
                    throw HomeConfigException("$context has an invalid string")
                }
            }

            is Number -> {
                if (!value.toDouble().isFinite()) {
                    throw HomeConfigException("$context has an invalid number")
                }
            }

            is Boolean,
            JSONObject.NULL,
            -> Unit

            else -> throw HomeConfigException("$context has an unsupported value")
        }
    }

    private fun JSONObject.parseLimits(): DikcizLimits {
        requireOnlyKeys(LIMIT_KEYS, LIMITS_NAMESPACE_KEY)
        return DikcizLimits(
            maxConfigBytes = requireBoundedInt(
                MAX_CONFIG_BYTES_KEY,
                LIMITS_NAMESPACE_KEY,
                MINIMUM_CONFIG_BYTES,
                MAXIMUM_CONFIGURATION_DOCUMENT_BYTES,
            ),
            maxComponentCharacters = requireBoundedInt(
                MAX_COMPONENT_CHARACTERS_KEY,
                LIMITS_NAMESPACE_KEY,
                MINIMUM_COMPONENT_CHARACTERS,
                HARD_MAXIMUM_COMPONENT_CHARACTERS,
            ),
            maxHtmlWidgetDocumentBytes = requireBoundedInt(
                MAX_HTML_WIDGET_DOCUMENT_BYTES_KEY,
                LIMITS_NAMESPACE_KEY,
                HtmlWidgetResourceLimits.MINIMUM_DOCUMENT_BYTES,
                HtmlWidgetResourceLimits.HARD_MAXIMUM_DOCUMENT_BYTES,
            ),
            maxHtmlWidgetsPerPage = requireBoundedInt(
                MAX_HTML_WIDGETS_PER_PAGE_KEY,
                LIMITS_NAMESPACE_KEY,
                HtmlWidgetResourceLimits.MINIMUM_RENDERERS_PER_PAGE,
                HtmlWidgetResourceLimits.HARD_MAXIMUM_RENDERERS_PER_PAGE,
            ),
            maxIdentifierCharacters = requireBoundedInt(
                MAX_IDENTIFIER_CHARACTERS_KEY,
                LIMITS_NAMESPACE_KEY,
                MINIMUM_IDENTIFIER_CHARACTERS,
                HARD_MAXIMUM_IDENTIFIER_CHARACTERS,
            ),
            maxPages = requireBoundedInt(
                MAX_PAGES_KEY,
                LIMITS_NAMESPACE_KEY,
                MINIMUM_PAGE_COUNT,
                HARD_MAXIMUM_PAGE_COUNT,
            ),
            maxTextCharacters = requireBoundedInt(
                MAX_TEXT_CHARACTERS_KEY,
                LIMITS_NAMESPACE_KEY,
                MINIMUM_TEXT_CHARACTERS,
                HARD_MAXIMUM_TEXT_CHARACTERS,
            ),
            maxTitleCharacters = requireBoundedInt(
                MAX_TITLE_CHARACTERS_KEY,
                LIMITS_NAMESPACE_KEY,
                MINIMUM_TITLE_CHARACTERS,
                HARD_MAXIMUM_TITLE_CHARACTERS,
            ),
            maxWidgetsPerPage = requireBoundedInt(
                MAX_WIDGETS_PER_PAGE_KEY,
                LIMITS_NAMESPACE_KEY,
                MINIMUM_WIDGET_COUNT,
                HARD_MAXIMUM_WIDGET_COUNT,
            ),
        )
    }

    private fun JSONArray.parsePages(limits: DikcizLimits): List<HomePage> {
        if (length() !in MINIMUM_PAGE_COUNT..limits.maxPages) {
            throw HomeConfigException(
                "$LAUNCHER_NAMESPACE_KEY.$HOME_NAMESPACE_KEY.$PAGES_KEY must contain " +
                    "$MINIMUM_PAGE_COUNT through ${limits.maxPages} pages",
            )
        }
        return List(length()) { index ->
            getJSONObjectAt(index, "$PAGES_KEY[$index]").parsePage(index, limits)
        }
    }

    private fun JSONObject.parsePage(index: Int, limits: DikcizLimits): HomePage {
        val context = "$PAGES_KEY[$index]"
        requireOnlyKeys(PAGE_KEYS, context)
        return HomePage(
            id = requireIdentifier(ID_KEY, context, limits),
            title = requireTitle(TITLE_KEY, context, limits),
            position = requirePagePosition(context, limits),
            widgets = requireArray(WIDGETS_KEY, context).parseWidgets(context, limits),
            locked = optionalBoolean(DikcizStyleCodec.LOCKED_KEY, context),
        )
    }

    private fun JSONObject.requirePagePosition(
        pageContext: String,
        limits: DikcizLimits,
    ): PagePosition {
        val context = "$pageContext.$POSITION_KEY"
        val value = requireObject(POSITION_KEY, pageContext)
        value.requireOnlyKeys(PAGE_POSITION_KEYS, context)
        return PagePosition(
            column = value.requireBoundedInt(
                PAGE_COLUMN_KEY,
                context,
                PAGE_POSITION_MINIMUM_INDEX,
                limits.maxPages - 1,
            ),
            row = value.requireBoundedInt(
                PAGE_ROW_KEY,
                context,
                PAGE_POSITION_MINIMUM_INDEX,
                limits.maxPages - 1,
            ),
        )
    }

    private fun JSONArray.parseWidgets(
        pageContext: String,
        limits: DikcizLimits,
    ): List<HomeWidget> {
        if (length() > limits.maxWidgetsPerPage) {
            throw HomeConfigException("$pageContext has more than ${limits.maxWidgetsPerPage} widgets")
        }
        return List(length()) { index ->
            val context = "$pageContext.$WIDGETS_KEY[$index]"
            getJSONObjectAt(index, context).parseWidget(context, limits)
        }
    }

    private fun JSONObject.parseWidget(
        context: String,
        limits: DikcizLimits,
    ): HomeWidget {
        return when (val type = requireString(TYPE_KEY, context, MAXIMUM_TYPE_LENGTH)) {
            HTML_WIDGET_TYPE -> parseHtmlWidget(context, limits)
            APP_WIDGET_TYPE -> parseAppWidget(context, limits)
            APP_GROUP_WIDGET_TYPE -> parseAppGroupWidget(context, limits)
            PROVIDER_WIDGET_TYPE -> parseProviderWidget(context, limits)
            SCRIPT_DASHBOARD_WIDGET_TYPE -> parseScriptDashboardWidget(context, limits)
            else -> throw HomeConfigException("$context has an unknown widget type: $type")
        }
    }

    private fun JSONObject.parseHtmlWidget(
        context: String,
        limits: DikcizLimits,
    ): HtmlHomeWidget {
        requireOnlyKeys(HTML_WIDGET_KEYS, context)
        val html = requireText(HTML_KEY, context, limits)
        if (html.isBlank()) {
            throw HomeConfigException("$context.$HTML_KEY must not be blank")
        }
        return HtmlHomeWidget(
            id = requireIdentifier(ID_KEY, context, limits),
            title = requireTitle(TITLE_KEY, context, limits),
            html = html,
            css = optionalText(CSS_KEY, context, limits),
            javascript = optionalText(JAVASCRIPT_KEY, context, limits),
            state = parseHtmlWidgetState(context, limits),
            heightMode = optionalHtmlWidgetHeightMode(context),
            eventSubscriptions = parseHtmlWidgetEventSubscriptions(context, limits),
            enabled = requireBoolean(ENABLED_KEY, context),
            cell = requireWidgetCell(context),
            style = DikcizStyleCodec.parseOptionalStyle(this, context),
            locked = optionalBoolean(DikcizStyleCodec.LOCKED_KEY, context),
        )
    }

    private fun JSONObject.parseHtmlWidgetState(
        context: String,
        limits: DikcizLimits,
    ): JSONObject {
        if (!has(STATE_KEY)) {
            return JSONObject()
        }
        val state = requireObject(STATE_KEY, context)
        validateHtmlWidgetState(state, "$context.$STATE_KEY", limits)
        return JSONObject(state.toString())
    }

    private fun JSONObject.optionalHtmlWidgetHeightMode(context: String): HtmlWidgetHeightMode {
        if (!has(HTML_HEIGHT_MODE_KEY)) {
            return HtmlWidgetHeightMode.Fixed
        }
        val persistedValue = requireString(HTML_HEIGHT_MODE_KEY, context, MAXIMUM_TYPE_LENGTH)
        return HtmlWidgetHeightMode.fromPersistedValue(persistedValue)
            ?: throw HomeConfigException("$context.$HTML_HEIGHT_MODE_KEY is invalid")
    }

    private fun JSONObject.parseHtmlWidgetEventSubscriptions(
        context: String,
        limits: DikcizLimits,
    ): List<DikcizAutomationSubscription> {
        if (!has(HTML_EVENT_SUBSCRIPTIONS_KEY)) {
            return emptyList()
        }
        val subscriptions = requireArray(HTML_EVENT_SUBSCRIPTIONS_KEY, context)
        if (subscriptions.length() > MAXIMUM_AUTOMATION_SUBSCRIPTIONS) {
            throw HomeConfigException("$context has too many HTML event subscriptions")
        }
        return List(subscriptions.length()) { index ->
            subscriptions.getJSONObjectAt(index, "$context.$HTML_EVENT_SUBSCRIPTIONS_KEY[$index]")
                .parseAutomationSubscription("$context.$HTML_EVENT_SUBSCRIPTIONS_KEY[$index]", limits)
        }
    }

    private fun JSONObject.parseAppWidget(
        context: String,
        limits: DikcizLimits,
    ): AppHomeWidget {
        requireOnlyKeys(APP_WIDGET_KEYS, context)
        val component = requireString(
            COMPONENT_KEY,
            context,
            limits.maxComponentCharacters,
        )
        if (ComponentName.unflattenFromString(component) == null) {
            throw HomeConfigException("$context has an invalid explicit Android component")
        }
        return AppHomeWidget(
            id = requireIdentifier(ID_KEY, context, limits),
            title = requireTitle(TITLE_KEY, context, limits),
            component = component,
            displayStyle = optionalAppWidgetDisplayStyle(context),
            enabled = requireBoolean(ENABLED_KEY, context),
            cell = requireWidgetCell(context),
            style = DikcizStyleCodec.parseOptionalStyle(this, context),
            locked = optionalBoolean(DikcizStyleCodec.LOCKED_KEY, context),
        )
    }

    private fun JSONObject.parseAppGroupWidget(
        context: String,
        limits: DikcizLimits,
    ): AppGroupHomeWidget {
        requireOnlyKeys(APP_GROUP_WIDGET_KEYS, context)
        val components = requireArray(COMPONENTS_KEY, context).parseAppGroupComponents(context, limits)
        return AppGroupHomeWidget(
            id = requireIdentifier(ID_KEY, context, limits),
            title = requireTitle(TITLE_KEY, context, limits),
            components = components,
            enabled = requireBoolean(ENABLED_KEY, context),
            cell = requireWidgetCell(context),
            style = DikcizStyleCodec.parseOptionalStyle(this, context),
            locked = optionalBoolean(DikcizStyleCodec.LOCKED_KEY, context),
        )
    }

    private fun JSONArray.parseAppGroupComponents(
        context: String,
        limits: DikcizLimits,
    ): List<String> {
        if (length() !in MINIMUM_APP_GROUP_COMPONENTS..AppGroupLimits.MAXIMUM_COMPONENTS) {
            throw HomeConfigException(
                "$context.$COMPONENTS_KEY must contain $MINIMUM_APP_GROUP_COMPONENTS through " +
                    "${AppGroupLimits.MAXIMUM_COMPONENTS} Android components",
            )
        }
        val components = List(length()) { index ->
            val component = getStringAt(
                index,
                "$context.$COMPONENTS_KEY[$index]",
                limits.maxComponentCharacters,
            )
            if (ComponentName.unflattenFromString(component) == null) {
                throw HomeConfigException("$context.$COMPONENTS_KEY[$index] has an invalid Android component")
            }
            component
        }
        if (components.toSet().size != components.size) {
            throw HomeConfigException("$context.$COMPONENTS_KEY has duplicate Android components")
        }
        return components
    }

    private fun JSONObject.parseProviderWidget(
        context: String,
        limits: DikcizLimits,
    ): ProviderHomeWidget {
        requireOnlyKeys(PROVIDER_WIDGET_KEYS, context)
        val provider = requireString(
            PROVIDER_KEY,
            context,
            limits.maxComponentCharacters,
        )
        if (ComponentName.unflattenFromString(provider) == null) {
            throw HomeConfigException("$context has an invalid app widget provider")
        }
        return ProviderHomeWidget(
            id = requireIdentifier(ID_KEY, context, limits),
            title = requireTitle(TITLE_KEY, context, limits),
            provider = provider,
            appWidgetID = requireBoundedInt(
                APP_WIDGET_ID_KEY,
                context,
                MINIMUM_APP_WIDGET_ID,
                Int.MAX_VALUE,
            ),
            enabled = requireBoolean(ENABLED_KEY, context),
            cell = requireWidgetCell(context),
            style = DikcizStyleCodec.parseOptionalStyle(this, context),
            locked = optionalBoolean(DikcizStyleCodec.LOCKED_KEY, context),
        )
    }

    private fun JSONObject.parseScriptDashboardWidget(
        context: String,
        limits: DikcizLimits,
    ): ScriptDashboardHomeWidget {
        requireOnlyKeys(SCRIPT_DASHBOARD_WIDGET_KEYS, context)
        return ScriptDashboardHomeWidget(
            id = requireIdentifier(ID_KEY, context, limits),
            title = requireTitle(TITLE_KEY, context, limits),
            enabled = requireBoolean(ENABLED_KEY, context),
            cell = requireWidgetCell(context),
            style = DikcizStyleCodec.parseOptionalStyle(this, context),
            locked = optionalBoolean(DikcizStyleCodec.LOCKED_KEY, context),
        )
    }

    private fun JSONObject.requireWidgetCell(context: String): DikcizGridRectangle {
        val value = requireObject(CELL_KEY, context)
        val cellContext = "$context.$CELL_KEY"
        value.requireOnlyKeys(WIDGET_CELL_KEYS, cellContext)
        return DikcizGridRectangle(
            column = value.requireBoundedInt(
                CELL_COLUMN_KEY,
                cellContext,
                MINIMUM_CELL_INDEX,
                DikcizNativeGrid.MAXIMUM_COLUMNS - CELL_INDEX_LIMIT_OFFSET,
            ),
            row = value.requireBoundedInt(
                CELL_ROW_KEY,
                cellContext,
                MINIMUM_CELL_INDEX,
                DikcizNativeGrid.MAXIMUM_ROWS - CELL_INDEX_LIMIT_OFFSET,
            ),
            columnSpan = value.requireBoundedInt(
                CELL_COLUMN_SPAN_KEY,
                cellContext,
                DikcizGridRectangle.MINIMUM_SPAN,
                DikcizNativeGrid.MAXIMUM_COLUMNS,
            ),
            rowSpan = value.requireBoundedInt(
                CELL_ROW_SPAN_KEY,
                cellContext,
                DikcizGridRectangle.MINIMUM_SPAN,
                DikcizNativeGrid.MAXIMUM_ROWS,
            ),
        )
    }

    private fun JSONObject.parseNativeGrid(context: String): DikcizNativeGrid {
        if (!has(NATIVE_GRID_KEY)) {
            return DikcizNativeGrid.BUNDLED_DEFAULT
        }
        val value = requireObject(NATIVE_GRID_KEY, context)
        val gridContext = "$context.$NATIVE_GRID_KEY"
        value.requireOnlyKeys(NATIVE_GRID_KEYS, gridContext)
        return DikcizNativeGrid(
            columns = value.requireBoundedInt(
                GRID_COLUMNS_KEY,
                gridContext,
                DikcizNativeGrid.MINIMUM_COLUMNS,
                DikcizNativeGrid.MAXIMUM_COLUMNS,
            ),
            rows = value.requireBoundedInt(
                GRID_ROWS_KEY,
                gridContext,
                DikcizNativeGrid.MINIMUM_ROWS,
                DikcizNativeGrid.MAXIMUM_ROWS,
            ),
            gapDP = value.requireBoundedInt(
                GRID_GAP_DP_KEY,
                gridContext,
                DikcizNativeGrid.MINIMUM_GAP_DP,
                DikcizNativeGrid.MAXIMUM_GAP_DP,
            ),
            outerPaddingDP = value.requireBoundedInt(
                GRID_OUTER_PADDING_DP_KEY,
                gridContext,
                DikcizNativeGrid.MINIMUM_OUTER_PADDING_DP,
                DikcizNativeGrid.MAXIMUM_OUTER_PADDING_DP,
            ),
        )
    }

    private fun validateConfiguration(configuration: HomeConfiguration) {
        if (configuration.version != CURRENT_CONFIGURATION_VERSION) {
            throw HomeConfigException(
                "$CONFIGURATION_FILE_NAME version must be $CURRENT_CONFIGURATION_VERSION",
            )
        }
        validateLimits(configuration.limits)
        validateLogging(configuration.logging)
        configuration.launcherBackground?.let { background ->
            DikcizLauncherBackgroundCodec.validate(
                background,
                "$LAUNCHER_NAMESPACE_KEY.$HOME_NAMESPACE_KEY.${DikcizLauncherBackgroundCodec.BACKGROUND_KEY}",
            )
        }
        DikcizStyleCodec.validate(
            configuration.styleDefaults.widget,
            "$LAUNCHER_NAMESPACE_KEY.$HOME_NAMESPACE_KEY.${DikcizStyleCodec.STYLE_DEFAULTS_KEY}.widget",
        )
        validateNamespaces(configuration.rootNamespaces, CONFIGURATION_FILE_NAME)
        validateNamespaces(configuration.launcherNamespaces, LAUNCHER_NAMESPACE_KEY)
        if (configuration.pages.size !in MINIMUM_PAGE_COUNT..configuration.limits.maxPages) {
            throw HomeConfigException("$CONFIGURATION_FILE_NAME has an invalid page count")
        }
        if (configuration.pages.none { it.id == configuration.selectedPageID }) {
            throw HomeConfigException(SELECTED_PAGE_ID_MISSING_MESSAGE)
        }
        if (configuration.pages.none { it.id == configuration.homePageID }) {
            throw HomeConfigException("homePageId does not identify a page")
        }
        configuration.selectedThemeID?.let { themeID ->
            validateIdentifier(themeID, configuration.limits, "selectedThemeId")
        }

        val scriptIDs = validateScripts(configuration.scripts, configuration.limits)
        validateAutomation(configuration.automation, scriptIDs, configuration.limits)
        val pageIDs = mutableSetOf<String>()
        val pagePositions = mutableSetOf<PagePosition>()
        val widgetIDs = mutableSetOf<String>()
        configuration.pages.forEach { page ->
            validateIdentifier(page.id, configuration.limits, "page id")
            validateTitle(page.title, configuration.limits, "page title")
            if (!pageIDs.add(canonicalIdentifier(page.id))) {
                throw HomeConfigException(
                    "$CONFIGURATION_FILE_NAME has a case-insensitive duplicate page id: ${page.id}",
                )
            }
            if (!pagePositions.add(page.position)) {
                throw HomeConfigException(
                    "$CONFIGURATION_FILE_NAME has duplicate page position: ${page.position.column},${page.position.row}",
                )
            }
            validateWidgets(page, configuration.limits, widgetIDs)
            validatePageGrid(page, configuration.nativeGrid)
        }
        validateHtmlWidgetResources(configuration)
    }

    private fun validateScripts(
        scripts: List<DikcizLuaScript>,
        limits: DikcizLimits,
    ): Set<String> {
        if (scripts.size > MAXIMUM_SCRIPT_COUNT) {
            throw HomeConfigException("$SCRIPTS_NAMESPACE_KEY has more than $MAXIMUM_SCRIPT_COUNT scripts")
        }
        val scriptIDs = mutableSetOf<String>()
        scripts.forEach { script ->
            validateIdentifier(script.id, limits, "script id")
            validateTitle(script.title, limits, "script title")
            validateScriptSource(script.source, "script ${script.id}", limits)
            if (script.apiVersion != DikcizLuaScriptApi.CURRENT_VERSION) {
                throw HomeConfigException("script ${script.id} has an unsupported API version")
            }
            validateScriptState(script.state, "script ${script.id} state", limits)
            if (script.source.isBlank()) {
                throw HomeConfigException("script ${script.id} source must not be blank")
            }
            val canonicalID = canonicalIdentifier(script.id)
            if (!scriptIDs.add(canonicalID)) {
                throw HomeConfigException(
                    "$CONFIGURATION_FILE_NAME has a case-insensitive duplicate script id: ${script.id}",
                )
            }
        }
        return scriptIDs
    }

    private fun validateAutomation(
        automation: DikcizAutomationConfiguration,
        knownScriptIDs: Set<String>,
        limits: DikcizLimits,
    ) {
        if (automation.apiVersion != DikcizAutomationApi.CURRENT_VERSION) {
            throw HomeConfigException("$AUTOMATION_NAMESPACE_KEY has an unsupported API version")
        }
        if (automation.policies.size > MAXIMUM_AUTOMATION_POLICIES) {
            throw HomeConfigException("$AUTOMATION_NAMESPACE_KEY has too many policies")
        }
        if (automation.scripts.size > MAXIMUM_AUTOMATION_SCRIPTS) {
            throw HomeConfigException("$AUTOMATION_NAMESPACE_KEY has too many script files")
        }
        val policyIDs = mutableSetOf<String>()
        automation.policies.forEach { policy ->
            validateIdentifier(policy.id, limits, "automation policy id")
            validateTitle(policy.title, limits, "automation policy title")
            if (!policyIDs.add(canonicalIdentifier(policy.id))) {
                throw HomeConfigException("$AUTOMATION_NAMESPACE_KEY has duplicate policy ids")
            }
            if (
                DikcizAutomationCapability.MediaSessionsContent in policy.capabilities &&
                DikcizAutomationCapability.MediaSessionsMetadata !in policy.capabilities
            ) {
                throw HomeConfigException("automation media-session content requires metadata capability")
            }
            if (
                DikcizAutomationCapability.CalendarEventsContent in policy.capabilities &&
                DikcizAutomationCapability.CalendarEventsMetadata !in policy.capabilities
            ) {
                throw HomeConfigException("automation calendar-event content requires metadata capability")
            }
            if (
                DikcizAutomationCapability.SmsContent in policy.capabilities &&
                DikcizAutomationCapability.SmsMetadata !in policy.capabilities
            ) {
                throw HomeConfigException("automation SMS content requires metadata capability")
            }
            if (
                policy.mediaSessionPackages.isNotEmpty() &&
                DikcizAutomationActionCapability.MediaControl !in policy.actions
            ) {
                throw HomeConfigException("automation media-session packages require mediaControl action")
            }
            if (
                DikcizAutomationActionCapability.NotificationControl in policy.actions &&
                DikcizAutomationCapability.NotificationsMetadata !in policy.capabilities
            ) {
                throw HomeConfigException("notificationControl requires notificationsMetadata capability")
            }
            if (
                DikcizAutomationActionCapability.NotificationControl in policy.actions &&
                policy.notificationActionPackages.isEmpty()
            ) {
                throw HomeConfigException("notificationControl requires notification action packages")
            }
            if (
                policy.notificationActionPackages.isNotEmpty() &&
                DikcizAutomationActionCapability.NotificationControl !in policy.actions
            ) {
                throw HomeConfigException("notification action packages require notificationControl action")
            }
        }
        val automationScriptIDs = mutableSetOf<String>()
        automation.scripts.forEach { script ->
            validateIdentifier(script.scriptID, limits, "automation script id")
            validateIdentifier(script.policyID, limits, "automation policy id")
            if (!knownScriptIDs.contains(canonicalIdentifier(script.scriptID))) {
                throw HomeConfigException("automation script ${script.scriptID} has no Lua script")
            }
            if (!policyIDs.contains(canonicalIdentifier(script.policyID))) {
                throw HomeConfigException("automation script ${script.scriptID} has no policy")
            }
            if (!automationScriptIDs.add(canonicalIdentifier(script.scriptID))) {
                throw HomeConfigException("$AUTOMATION_NAMESPACE_KEY has duplicate script references")
            }
            validateAutomationSubscriptions(script, policyIDs)
        }
    }

    private fun validateAutomationSubscriptions(
        script: DikcizAutomationScript,
        policyIDs: Set<String>,
    ) {
        val subscriptionKeys = mutableSetOf<String>()
        script.subscriptions.forEach { subscription ->
            validateAutomationSubscription(subscription, "automation script ${script.scriptID}")
            val key = subscriptionIdentity(subscription)
            if (!subscriptionKeys.add(key)) {
                throw HomeConfigException("automation script ${script.scriptID} has duplicate subscriptions")
            }
        }
        if (!policyIDs.contains(canonicalIdentifier(script.policyID))) {
            throw HomeConfigException("automation script ${script.scriptID} has no policy")
        }
    }

    private fun validateAutomationSubscription(
        subscription: DikcizAutomationSubscription,
        context: String,
    ) {
        if (subscription.minimumIntervalMilliseconds !in MINIMUM_AUTOMATION_INTERVAL_MILLISECONDS..MAXIMUM_AUTOMATION_INTERVAL_MILLISECONDS) {
            throw HomeConfigException("$context has an invalid minimum interval")
        }
        if (subscription.event == DikcizAutomationEventType.Location && subscription.locationPrecision == null) {
            throw HomeConfigException("$context has no location precision")
        }
        if (
            subscription.event == DikcizAutomationEventType.Custom &&
            !DikcizAutomationEventNames.isValidCustomName(subscription.customEventName.orEmpty())
        ) {
            throw HomeConfigException("$context has an invalid custom event")
        }
        if (
            subscription.event != DikcizAutomationEventType.Custom &&
            subscription.customEventName != null
        ) {
            throw HomeConfigException("$context assigns a custom name to a native event")
        }
    }

    private fun subscriptionIdentity(subscription: DikcizAutomationSubscription): String {
        return listOf(
            subscription.customEventName ?: subscription.event.persistedValue,
            subscription.coalescingKey.orEmpty(),
            subscription.sensorTypes.joinToString(","),
            subscription.healthRefreshIntervalMilliseconds?.toString().orEmpty(),
        ).joinToString(":")
    }

    private fun validateLimits(limits: DikcizLimits) {
        validateLimit(
            limits.maxConfigBytes,
            MINIMUM_CONFIG_BYTES,
            MAXIMUM_CONFIGURATION_DOCUMENT_BYTES,
            MAX_CONFIG_BYTES_KEY,
        )
        validateLimit(
            limits.maxComponentCharacters,
            MINIMUM_COMPONENT_CHARACTERS,
            HARD_MAXIMUM_COMPONENT_CHARACTERS,
            MAX_COMPONENT_CHARACTERS_KEY,
        )
        validateLimit(
            limits.maxHtmlWidgetDocumentBytes,
            HtmlWidgetResourceLimits.MINIMUM_DOCUMENT_BYTES,
            HtmlWidgetResourceLimits.HARD_MAXIMUM_DOCUMENT_BYTES,
            MAX_HTML_WIDGET_DOCUMENT_BYTES_KEY,
        )
        validateLimit(
            limits.maxHtmlWidgetsPerPage,
            HtmlWidgetResourceLimits.MINIMUM_RENDERERS_PER_PAGE,
            HtmlWidgetResourceLimits.HARD_MAXIMUM_RENDERERS_PER_PAGE,
            MAX_HTML_WIDGETS_PER_PAGE_KEY,
        )
        validateLimit(
            limits.maxIdentifierCharacters,
            MINIMUM_IDENTIFIER_CHARACTERS,
            HARD_MAXIMUM_IDENTIFIER_CHARACTERS,
            MAX_IDENTIFIER_CHARACTERS_KEY,
        )
        validateLimit(limits.maxPages, MINIMUM_PAGE_COUNT, HARD_MAXIMUM_PAGE_COUNT, MAX_PAGES_KEY)
        validateLimit(
            limits.maxTextCharacters,
            MINIMUM_TEXT_CHARACTERS,
            HARD_MAXIMUM_TEXT_CHARACTERS,
            MAX_TEXT_CHARACTERS_KEY,
        )
        validateLimit(
            limits.maxTitleCharacters,
            MINIMUM_TITLE_CHARACTERS,
            HARD_MAXIMUM_TITLE_CHARACTERS,
            MAX_TITLE_CHARACTERS_KEY,
        )
        validateLimit(
            limits.maxWidgetsPerPage,
            MINIMUM_WIDGET_COUNT,
            HARD_MAXIMUM_WIDGET_COUNT,
            MAX_WIDGETS_PER_PAGE_KEY,
        )
    }

    private fun validateLogging(logging: DikcizLoggingConfiguration) {
        validateLimit(
            logging.retentionDays,
            MINIMUM_LOG_RETENTION_DAYS,
            HARD_MAXIMUM_LOG_RETENTION_DAYS,
            LOG_RETENTION_DAYS_KEY,
            LOGGING_NAMESPACE_KEY,
        )
        validateLimit(
            logging.maxTotalBytes,
            MINIMUM_LOG_MAXIMUM_TOTAL_BYTES,
            HARD_MAXIMUM_LOG_MAXIMUM_TOTAL_BYTES,
            LOG_MAXIMUM_TOTAL_BYTES_KEY,
            LOGGING_NAMESPACE_KEY,
        )
        validateLimit(
            logging.scriptErrorNotificationMinimumIntervalMilliseconds,
            MINIMUM_SCRIPT_ERROR_NOTIFICATION_INTERVAL_MILLISECONDS,
            HARD_MAXIMUM_SCRIPT_ERROR_NOTIFICATION_INTERVAL_MILLISECONDS,
            LOG_SCRIPT_ERROR_NOTIFICATION_MINIMUM_INTERVAL_MILLISECONDS_KEY,
            LOGGING_NAMESPACE_KEY,
        )
    }

    private fun validateLimit(
        value: Int,
        minimum: Int,
        maximum: Int,
        name: String,
        namespace: String = LIMITS_NAMESPACE_KEY,
    ) {
        if (value !in minimum..maximum) {
            throw HomeConfigException("$namespace.$name must be $minimum through $maximum")
        }
    }

    private fun validateNamespaces(namespaces: JSONObject, context: String) {
        val iterator = namespaces.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            validateNamespaceName(key, context)
            if (namespaces.get(key) !is JSONObject) {
                throw HomeConfigException("$context.$key must be an object")
            }
        }
    }

    private fun validateWidgets(
        page: HomePage,
        limits: DikcizLimits,
        widgetIDs: MutableSet<String>,
    ) {
        if (page.widgets.size > limits.maxWidgetsPerPage) {
            throw HomeConfigException("page ${page.id} has more than ${limits.maxWidgetsPerPage} widgets")
        }
        page.widgets.forEach { widget ->
            validateWidget(widget, page, limits, widgetIDs)
        }
    }

    private fun validateHtmlWidgetResources(configuration: HomeConfiguration) {
        configuration.pages.forEach { page ->
            val renderedWidgets = page.widgets
                .filterIsInstance<HtmlHomeWidget>()
                .filter { widget -> widget.enabled }
            if (renderedWidgets.size > configuration.limits.maxHtmlWidgetsPerPage) {
                throw HomeConfigException(
                    "page ${page.id} has more than ${configuration.limits.maxHtmlWidgetsPerPage} " +
                        "enabled HTML widgets",
                )
            }
            renderedWidgets.forEach { widget ->
                val widgetContext = DikcizWebWidgetContext.selfWidget(configuration, page, widget)
                val documentBytes = DikcizHtmlWidgetDocument.byteCount(widget, widgetContext)
                if (documentBytes > configuration.limits.maxHtmlWidgetDocumentBytes) {
                    throw HomeConfigException(
                        "widget ${widget.id} exceeds the HTML widget document byte limit",
                    )
                }
            }
        }
    }

    private fun validateWidget(
        widget: HomeWidget,
        page: HomePage,
        limits: DikcizLimits,
        widgetIDs: MutableSet<String>,
    ) {
        validateIdentifier(widget.id, limits, "widget id")
        validateTitle(widget.title, limits, "widget title")
        widget.style?.let { style ->
            DikcizStyleCodec.validate(style, "widget ${widget.id}.${DikcizStyleCodec.STYLE_KEY}")
        }
        if (!widgetIDs.add(canonicalIdentifier(widget.id))) {
            throw HomeConfigException(
                "$CONFIGURATION_FILE_NAME has a case-insensitive duplicate widget id: ${widget.id}",
            )
        }
        when (widget) {
            is HtmlHomeWidget -> validateHtmlWidget(widget, limits)
            is AppHomeWidget -> validateComponent(widget, limits)
            is AppGroupHomeWidget -> validateAppGroup(widget, limits)
            is ProviderHomeWidget -> validateProviderWidget(widget, limits)
            is ScriptDashboardHomeWidget -> Unit
        }
    }

    private fun validateHtmlWidget(
        widget: HtmlHomeWidget,
        limits: DikcizLimits,
    ) {
        validateText(widget.html, limits)
        if (widget.html.isBlank()) {
            throw HomeConfigException("widget ${widget.id} html must not be blank")
        }
        validateText(widget.css, limits)
        validateText(widget.javascript, limits)
        validateHtmlWidgetState(widget.state, "widget ${widget.id} state", limits)
        validateHtmlWidgetEventSubscriptions(widget, limits)
    }

    private fun validateHtmlWidgetEventSubscriptions(
        widget: HtmlHomeWidget,
        limits: DikcizLimits,
    ) {
        val subscriptionKeys = mutableSetOf<String>()
        widget.eventSubscriptions.forEach { subscription ->
            validateAutomationSubscription(subscription, "widget ${widget.id}")
            val key = subscriptionIdentity(subscription)
            if (!subscriptionKeys.add(key)) {
                throw HomeConfigException("widget ${widget.id} has duplicate event subscriptions")
            }
        }
    }

    private fun validateHtmlWidgetState(
        state: JSONObject,
        context: String,
        limits: DikcizLimits,
    ) {
        validateScriptState(state, context, limits)
    }

    /**
     * Every item must sit inside the configured grid, and two items may never claim the
     * same cell. An external edit that breaks either rule is rejected before it can render.
     */
    private fun validatePageGrid(page: HomePage, grid: DikcizNativeGrid) {
        val placed = mutableListOf<DikcizGridRectangle>()
        page.widgets.forEach { widget ->
            val cell = widget.cell
            if (!DikcizGridLayoutEngine.isWithinBounds(grid, cell)) {
                throw HomeConfigException(
                    "page ${page.id} widget ${widget.id} $CELL_KEY leaves the " +
                        "${grid.columns}x${grid.rows} native grid " +
                        "(${DikcizGridFailure.GridBounds.persistedValue})",
                )
            }
            if (DikcizGridLayoutEngine.collides(cell, placed)) {
                throw HomeConfigException(
                    "page ${page.id} widget ${widget.id} $CELL_KEY overlaps another widget " +
                        "(${DikcizGridFailure.GridCollision.persistedValue})",
                )
            }
            placed.add(cell)
        }
    }

    private fun validateComponent(widget: AppHomeWidget, limits: DikcizLimits) {
        if (widget.component.length > limits.maxComponentCharacters) {
            throw HomeConfigException("widget ${widget.id} has an oversized component")
        }
        if (ComponentName.unflattenFromString(widget.component) == null) {
            throw HomeConfigException("widget ${widget.id} has an invalid component")
        }
    }

    private fun validateAppGroup(widget: AppGroupHomeWidget, limits: DikcizLimits) {
        if (widget.components.size !in MINIMUM_APP_GROUP_COMPONENTS..AppGroupLimits.MAXIMUM_COMPONENTS) {
            throw HomeConfigException("widget ${widget.id} has an invalid app group component count")
        }
        val components = mutableSetOf<String>()
        widget.components.forEach { component ->
            if (component.length > limits.maxComponentCharacters) {
                throw HomeConfigException("widget ${widget.id} has an oversized app group component")
            }
            if (ComponentName.unflattenFromString(component) == null) {
                throw HomeConfigException("widget ${widget.id} has an invalid app group component")
            }
            if (!components.add(component)) {
                throw HomeConfigException("widget ${widget.id} has duplicate app group components")
            }
        }
    }

    private fun validateProviderWidget(widget: ProviderHomeWidget, limits: DikcizLimits) {
        if (widget.provider.length > limits.maxComponentCharacters) {
            throw HomeConfigException("widget ${widget.id} has an oversized app widget provider")
        }
        if (ComponentName.unflattenFromString(widget.provider) == null) {
            throw HomeConfigException("widget ${widget.id} has an invalid app widget provider")
        }
        if (widget.appWidgetID < MINIMUM_APP_WIDGET_ID) {
            throw HomeConfigException("widget ${widget.id} has an invalid app widget ID")
        }
    }

    private fun serializeConfiguration(configuration: HomeConfiguration): String {
        val root = configuration.rootNamespaces.copyObject().apply {
            put(VERSION_KEY, configuration.version)
            put(LIMITS_NAMESPACE_KEY, configuration.limits.toJson())
            put(LOGGING_NAMESPACE_KEY, configuration.logging.toJson())
            put(AUTOMATION_NAMESPACE_KEY, configuration.automation.toJson())
            put(SCRIPTS_NAMESPACE_KEY, configuration.scripts.toJson())
            put(
                LAUNCHER_NAMESPACE_KEY,
                configuration.launcherNamespaces.copyObject().apply {
                    put(HOME_NAMESPACE_KEY, configuration.toHomeJson())
                },
            )
        }
        return root.toString(JSON_INDENTATION_SPACES)
    }

    private fun HomeConfiguration.withoutRuntimeState(): HomeConfiguration {
        return copy(
            scripts = scripts.map { script -> script.copy(state = JSONObject()) },
            pages = pages.map { page ->
                page.copy(
                    widgets = page.widgets.map { widget ->
                        if (widget is HtmlHomeWidget) {
                            widget.copy(state = JSONObject())
                        } else {
                            widget
                        }
                    },
                )
            },
        )
    }

    private fun serializeManifest(configuration: HomeConfiguration): String {
        val root = configuration.rootNamespaces.copyObject().apply {
            put(VERSION_KEY, configuration.version)
            put(LIMITS_NAMESPACE_KEY, configuration.limits.toJson())
            put(LOGGING_NAMESPACE_KEY, configuration.logging.toJson())
            put(AUTOMATION_NAMESPACE_KEY, configuration.automation.toManifestJson())
            put(SCRIPTS_NAMESPACE_KEY, configuration.scripts.toManifestJson())
            put(
                LAUNCHER_NAMESPACE_KEY,
                configuration.launcherNamespaces.copyObject().apply {
                    put(HOME_NAMESPACE_KEY, configuration.toHomeManifestJson())
                },
            )
        }
        return root.toString(JSON_INDENTATION_SPACES)
    }

    private fun DikcizLimits.toJson(): JSONObject {
        return JSONObject()
            .put(MAX_CONFIG_BYTES_KEY, maxConfigBytes)
            .put(MAX_COMPONENT_CHARACTERS_KEY, maxComponentCharacters)
            .put(MAX_HTML_WIDGET_DOCUMENT_BYTES_KEY, maxHtmlWidgetDocumentBytes)
            .put(MAX_HTML_WIDGETS_PER_PAGE_KEY, maxHtmlWidgetsPerPage)
            .put(MAX_IDENTIFIER_CHARACTERS_KEY, maxIdentifierCharacters)
            .put(MAX_PAGES_KEY, maxPages)
            .put(MAX_TEXT_CHARACTERS_KEY, maxTextCharacters)
            .put(MAX_TITLE_CHARACTERS_KEY, maxTitleCharacters)
            .put(MAX_WIDGETS_PER_PAGE_KEY, maxWidgetsPerPage)
    }

    private fun DikcizLoggingConfiguration.toJson(): JSONObject {
        return JSONObject()
            .put(LOG_LEVEL_KEY, level.persistedValue)
            .put(LOG_RETENTION_DAYS_KEY, retentionDays)
            .put(LOG_MAXIMUM_TOTAL_BYTES_KEY, maxTotalBytes)
            .put(LOG_NOTIFY_ON_SCRIPT_ERROR_KEY, notifyOnScriptError)
            .put(
                LOG_SCRIPT_ERROR_NOTIFICATION_MINIMUM_INTERVAL_MILLISECONDS_KEY,
                scriptErrorNotificationMinimumIntervalMilliseconds,
            )
    }

    private fun DikcizAutomationConfiguration.toJson(): JSONObject = JSONObject()
        .put(AUTOMATION_API_VERSION_KEY, apiVersion)
        .put(AUTOMATION_POLICIES_KEY, JSONArray().apply {
            policies.forEach { policy -> put(policy.toJson()) }
        })
        .put(AUTOMATION_SCRIPTS_KEY, JSONArray().apply {
            scripts.forEach { script -> put(script.toJson()) }
        })

    private fun DikcizAutomationConfiguration.toManifestJson(): JSONObject {
        if (policies.isEmpty() && scripts.isEmpty()) {
            return JSONObject()
        }
        return JSONObject()
            .put(AUTOMATION_API_VERSION_KEY, apiVersion)
            .put(AUTOMATION_POLICIES_KEY, JSONArray().apply {
                policies.forEach { policy -> put(policy.id) }
            })
            .put(AUTOMATION_SCRIPTS_KEY, JSONArray().apply {
                scripts.forEach { script -> put(script.scriptID) }
            })
    }

    private fun DikcizAutomationPolicy.toJson(): JSONObject = JSONObject()
        .put(ID_KEY, id)
        .put(TITLE_KEY, title)
        .put(ENABLED_KEY, enabled)
        .put(AUTOMATION_CAPABILITIES_KEY, JSONArray().apply {
            capabilities.sortedBy(DikcizAutomationCapability::persistedValue).forEach { capability ->
                put(capability.persistedValue)
            }
        })
        .put(AUTOMATION_ACTIONS_KEY, JSONArray().apply {
            actions.sortedBy(DikcizAutomationActionCapability::persistedValue).forEach { action ->
                put(action.persistedValue)
            }
        })
        .apply {
            if (mediaSessionPackages.isNotEmpty()) {
                put(AUTOMATION_MEDIA_SESSION_PACKAGES_KEY, JSONArray(mediaSessionPackages))
            }
            if (notificationActionPackages.isNotEmpty()) {
                put(AUTOMATION_NOTIFICATION_ACTION_PACKAGES_KEY, JSONArray(notificationActionPackages))
            }
        }

    private fun DikcizAutomationScript.toJson(): JSONObject = JSONObject()
        .put(SCRIPT_ID_KEY, scriptID)
        .put(AUTOMATION_POLICY_ID_KEY, policyID)
        .put(ENABLED_KEY, enabled)
        .put(AUTOMATION_SUBSCRIPTIONS_KEY, JSONArray().apply {
            subscriptions.forEach { subscription -> put(subscription.toJson()) }
        })

    private fun DikcizAutomationSubscription.toJson(): JSONObject = JSONObject()
        .put(AUTOMATION_EVENT_KEY, customEventName ?: event.persistedValue)
        .put(AUTOMATION_MINIMUM_INTERVAL_MILLISECONDS_KEY, minimumIntervalMilliseconds)
        .apply {
            coalescingKey?.let { put(AUTOMATION_COALESCING_KEY, it) }
            if (packageNames.isNotEmpty()) {
                put(AUTOMATION_PACKAGES_KEY, JSONArray(packageNames))
            }
            if (sensorTypes.isNotEmpty()) {
                put(AUTOMATION_SENSOR_TYPES_KEY, JSONArray(sensorTypes))
            }
            samplingPeriodMicroseconds?.let { put(AUTOMATION_SAMPLING_PERIOD_MICROSECONDS_KEY, it) }
            alarmIntervalMilliseconds?.let { put(AUTOMATION_ALARM_INTERVAL_MILLISECONDS_KEY, it) }
            locationPrecision?.let { put(AUTOMATION_LOCATION_PRECISION_KEY, it.persistedValue) }
            healthRefreshIntervalMilliseconds?.let {
                put(AUTOMATION_HEALTH_REFRESH_INTERVAL_MILLISECONDS_KEY, it)
            }
        }

    private fun List<DikcizLuaScript>.toJson(): JSONArray = JSONArray().apply {
        this@toJson.forEach { script -> put(script.toJson()) }
    }

    private fun List<DikcizLuaScript>.toManifestJson(): JSONArray = JSONArray().apply {
        this@toManifestJson.forEach { script -> put(script.id) }
    }

    private fun DikcizLuaScript.toJson(): JSONObject = toMetadataJson()
        .put(SCRIPT_SOURCE_KEY, source)
        .put(STATE_KEY, JSONObject(state.toString()))

    private fun DikcizLuaScript.toMetadataJson(): JSONObject = JSONObject()
        .put(ID_KEY, id)
        .put(TITLE_KEY, title)
        .put(ENABLED_KEY, enabled)
        .put(API_VERSION_KEY, apiVersion)

    private fun HomeConfiguration.toHomeJson(): JSONObject {
        return JSONObject()
            .put(HOME_PAGE_ID_KEY, homePageID)
            .put(SELECTED_PAGE_ID_KEY, selectedPageID)
            .put(NATIVE_GRID_KEY, nativeGrid.toJson())
            .put(DikcizStyleCodec.STYLE_DEFAULTS_KEY, DikcizStyleCodec.run { styleDefaults.toJson() })
            .put(PAGES_KEY, JSONArray().apply {
                pages.forEach { put(it.toJson()) }
            })
            .apply {
                launcherBackground?.let { background ->
                    put(
                        DikcizLauncherBackgroundCodec.BACKGROUND_KEY,
                        DikcizLauncherBackgroundCodec.run { background.toJson() },
                    )
                }
                selectedThemeID?.let { put(SELECTED_THEME_ID_KEY, it) }
            }
    }

    private fun HomeConfiguration.toHomeManifestJson(): JSONObject {
        return JSONObject()
            .put(HOME_PAGE_ID_KEY, homePageID)
            .put(SELECTED_PAGE_ID_KEY, selectedPageID)
            .put(NATIVE_GRID_KEY, nativeGrid.toJson())
            .put(DikcizStyleCodec.STYLE_DEFAULTS_KEY, DikcizStyleCodec.run { styleDefaults.toJson() })
            .put(PAGES_KEY, JSONArray().apply {
                pages.forEach { page -> put(page.id) }
            })
            .apply {
                launcherBackground?.let { background ->
                    put(
                        DikcizLauncherBackgroundCodec.BACKGROUND_KEY,
                        DikcizLauncherBackgroundCodec.run { background.toJson() },
                    )
                }
                selectedThemeID?.let { put(SELECTED_THEME_ID_KEY, it) }
            }
    }

    private fun HomePage.toJson(): JSONObject {
        val json = JSONObject()
            .put(ID_KEY, id)
            .put(TITLE_KEY, title)
            .put(POSITION_KEY, position.toJson())
            .put(DikcizStyleCodec.LOCKED_KEY, locked)
            .put(WIDGETS_KEY, JSONArray().apply {
                widgets.forEach { put(it.toJson()) }
            })
        return json
    }

    private fun HomePage.toManifestJson(): JSONObject {
        return JSONObject()
            .put(ID_KEY, id)
            .put(TITLE_KEY, title)
            .put(POSITION_KEY, position.toJson())
            .put(DikcizStyleCodec.LOCKED_KEY, locked)
            .put(WIDGETS_KEY, JSONArray().apply {
                widgets.forEach { widget -> put(widget.id) }
            })
    }

    private fun PagePosition.toJson(): JSONObject {
        return JSONObject()
            .put(PAGE_COLUMN_KEY, column)
            .put(PAGE_ROW_KEY, row)
    }

    private fun HomeWidget.toJson(): JSONObject {
        val json = JSONObject()
            .put(ID_KEY, id)
            .put(TITLE_KEY, title)
            .put(ENABLED_KEY, enabled)
            .put(DikcizStyleCodec.LOCKED_KEY, locked)
            .put(CELL_KEY, cell.toJson())
        style?.let { value ->
            json.put(DikcizStyleCodec.STYLE_KEY, DikcizStyleCodec.run { value.toJson() })
        }
        when (this) {
            is HtmlHomeWidget -> {
                json.put(TYPE_KEY, HTML_WIDGET_TYPE)
                json.put(HTML_KEY, html)
                json.put(CSS_KEY, css)
                json.put(JAVASCRIPT_KEY, javascript)
                json.put(STATE_KEY, JSONObject(state.toString()))
                json.put(HTML_HEIGHT_MODE_KEY, heightMode.persistedValue)
                json.put(HTML_EVENT_SUBSCRIPTIONS_KEY, JSONArray().apply {
                    eventSubscriptions.forEach { subscription -> put(subscription.toJson()) }
                })
            }
            is AppHomeWidget -> {
                json.put(TYPE_KEY, APP_WIDGET_TYPE)
                json.put(COMPONENT_KEY, component)
                json.put(DISPLAY_STYLE_KEY, displayStyle.persistedValue)
            }
            is AppGroupHomeWidget -> {
                json.put(TYPE_KEY, APP_GROUP_WIDGET_TYPE)
                json.put(COMPONENTS_KEY, JSONArray().apply {
                    components.forEach(::put)
                })
            }
            is ProviderHomeWidget -> {
                json.put(TYPE_KEY, PROVIDER_WIDGET_TYPE)
                json.put(PROVIDER_KEY, provider)
                json.put(APP_WIDGET_ID_KEY, appWidgetID)
            }
            is ScriptDashboardHomeWidget -> json.put(TYPE_KEY, SCRIPT_DASHBOARD_WIDGET_TYPE)
        }
        return json
    }

    private fun DikcizGridRectangle.toJson(): JSONObject {
        return JSONObject()
            .put(CELL_COLUMN_KEY, column)
            .put(CELL_ROW_KEY, row)
            .put(CELL_COLUMN_SPAN_KEY, columnSpan)
            .put(CELL_ROW_SPAN_KEY, rowSpan)
    }

    private fun DikcizNativeGrid.toJson(): JSONObject {
        return JSONObject()
            .put(GRID_COLUMNS_KEY, columns)
            .put(GRID_ROWS_KEY, rows)
            .put(GRID_GAP_DP_KEY, gapDP)
            .put(GRID_OUTER_PADDING_DP_KEY, outerPaddingDP)
    }

    private fun DikcizLuaScriptRunStatus.toJson(): JSONObject {
        return JSONObject()
            .put(SCRIPT_RUN_LAST_RUN_AT_KEY, lastRunAt ?: JSONObject.NULL)
            .put(SCRIPT_RUN_OUTCOME_KEY, outcome.persistedValue)
            .put(SCRIPT_RUN_STATUS_KEY, status)
    }

    private fun JSONObject.requireOnlyKeys(allowedKeys: Set<String>, context: String) {
        val iterator = keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (key !in allowedKeys) {
                throw HomeConfigException("$context has an unknown key: $key")
            }
        }
    }

    private fun JSONObject.requireArray(key: String, context: String): JSONArray {
        val value = requireValue(key, context)
        if (value !is JSONArray) {
            throw HomeConfigException("$context.$key must be an array")
        }
        return value
    }

    private fun JSONObject.requireObject(key: String, context: String): JSONObject {
        val value = requireValue(key, context)
        if (value !is JSONObject) {
            throw HomeConfigException("$context.$key must be an object")
        }
        return value
    }

    private fun JSONObject.requireString(key: String, context: String, maximumLength: Int): String {
        val value = requireValue(key, context)
        if (value !is String) {
            throw HomeConfigException("$context.$key must be a string")
        }
        if (value.length > maximumLength || value.contains(NUL_CHARACTER)) {
            throw HomeConfigException("$context.$key has an invalid length or character")
        }
        return value
    }

    private fun JSONObject.requireIdentifier(
        key: String,
        context: String,
        limits: DikcizLimits,
    ): String {
        val value = requireString(key, context, limits.maxIdentifierCharacters)
        validateIdentifier(value, limits, "$context.$key")
        return value
    }

    private fun JSONObject.optionalIdentifier(
        key: String,
        context: String,
        limits: DikcizLimits,
    ): String? {
        if (!has(key)) {
            return null
        }
        return requireIdentifier(key, context, limits)
    }

    private fun JSONObject.requireTitle(
        key: String,
        context: String,
        limits: DikcizLimits,
    ): String {
        val value = requireString(key, context, limits.maxTitleCharacters)
        validateTitle(value, limits, "$context.$key")
        return value
    }

    private fun JSONObject.requireText(
        key: String,
        context: String,
        limits: DikcizLimits,
    ): String {
        val value = requireString(key, context, limits.maxTextCharacters)
        validateText(value, limits)
        return value
    }

    private fun JSONObject.optionalText(
        key: String,
        context: String,
        limits: DikcizLimits,
    ): String {
        if (!has(key)) {
            return ""
        }
        return requireText(key, context, limits)
    }

    private fun JSONObject.requireBoolean(key: String, context: String): Boolean {
        val value = requireValue(key, context)
        if (value !is Boolean) {
            throw HomeConfigException("$context.$key must be a boolean")
        }
        return value
    }

    private fun JSONObject.optionalBoolean(
        key: String,
        context: String,
        defaultValue: Boolean = false,
    ): Boolean {
        if (!has(key)) {
            return defaultValue
        }
        return requireBoolean(key, context)
    }

    private fun JSONObject.optionalAppWidgetDisplayStyle(
        context: String,
    ): AppWidgetDisplayStyle {
        if (!has(DISPLAY_STYLE_KEY)) {
            return AppWidgetDisplayStyle.IconWithLabel
        }
        val persistedValue = requireString(
            DISPLAY_STYLE_KEY,
            context,
            MAXIMUM_APP_WIDGET_DISPLAY_STYLE_LENGTH,
        )
        return AppWidgetDisplayStyle.fromPersistedValue(persistedValue)
            ?: throw HomeConfigException(
                "$context has an unknown app widget display style: $persistedValue",
            )
    }

    private fun JSONObject.requireInt(key: String, context: String): Int {
        val value = requireValue(key, context)
        if (value !is Number || value.toLong().toDouble() != value.toDouble()) {
            throw HomeConfigException("$context.$key must be an integer")
        }
        val number = value.toLong()
        if (number !in Int.MIN_VALUE..Int.MAX_VALUE) {
            throw HomeConfigException("$context.$key is outside the integer range")
        }
        return number.toInt()
    }

    private fun JSONObject.requireBoundedInt(
        key: String,
        context: String,
        minimum: Int,
        maximum: Int,
    ): Int {
        val value = requireInt(key, context)
        if (value !in minimum..maximum) {
            throw HomeConfigException("$context.$key must be $minimum through $maximum")
        }
        return value
    }

    private fun JSONObject.optionalBoundedInt(
        key: String,
        context: String,
        minimum: Int,
        maximum: Int,
        defaultValue: Int,
    ): Int {
        if (!has(key)) {
            return defaultValue
        }
        return requireBoundedInt(key, context, minimum, maximum)
    }

    private fun JSONObject.requireBoundedLong(
        key: String,
        context: String,
        minimum: Long,
        maximum: Long,
    ): Long {
        val value = requireValue(key, context)
        if (value !is Number || value.toLong().toDouble() != value.toDouble()) {
            throw HomeConfigException("$context.$key must be an integer")
        }
        val number = value.toLong()
        if (number !in minimum..maximum) {
            throw HomeConfigException("$context.$key must be $minimum through $maximum")
        }
        return number
    }

    private fun JSONObject.optionalBoundedLong(
        key: String,
        context: String,
        minimum: Long,
        maximum: Long,
    ): Long? {
        if (!has(key)) {
            return null
        }
        return requireBoundedLong(key, context, minimum, maximum)
    }

    private fun JSONObject.requireNonNegativeInt(key: String, context: String): Int {
        val value = requireInt(key, context)
        if (value < 0) {
            throw HomeConfigException("$context.$key cannot be negative")
        }
        return value
    }

    private fun JSONObject.requireValue(key: String, context: String): Any {
        if (!has(key) || isNull(key)) {
            throw HomeConfigException("$context.$key is required")
        }
        return get(key)
    }

    private fun JSONArray.getJSONObjectAt(index: Int, context: String): JSONObject {
        val value = get(index)
        if (value !is JSONObject) {
            throw HomeConfigException("$context must be an object")
        }
        return value
    }

    private fun JSONArray.getStringAt(index: Int, context: String, maximumLength: Int): String {
        val value = get(index)
        if (value !is String || value.length > maximumLength || value.contains(NUL_CHARACTER)) {
            throw HomeConfigException("$context must be a valid string")
        }
        return value
    }

    private fun JSONArray.requireBoundedIntAt(
        index: Int,
        context: String,
        minimum: Int,
        maximum: Int,
    ): Int {
        val value = get(index)
        if (value !is Number || value.toLong().toDouble() != value.toDouble()) {
            throw HomeConfigException("$context must be an integer")
        }
        val number = value.toLong()
        if (number !in minimum.toLong()..maximum.toLong()) {
            throw HomeConfigException("$context must be $minimum through $maximum")
        }
        return number.toInt()
    }

    private fun JSONArray.requireIdentifierAt(
        index: Int,
        context: String,
        limits: DikcizLimits,
    ): String {
        val value = get(index)
        if (value !is String) {
            throw HomeConfigException("$context must be an identifier string")
        }
        validateIdentifier(value, limits, context)
        return value
    }

    private fun JSONObject.copyNamespacesExcept(
        excludedKeys: Set<String>,
        context: String,
    ): JSONObject {
        val copy = JSONObject()
        val iterator = keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (key in excludedKeys) {
                continue
            }
            validateNamespaceName(key, context)
            val value = get(key)
            if (value !is JSONObject) {
                throw HomeConfigException("$context.$key must be an object")
            }
            copy.put(key, value)
        }
        return copy
    }

    private fun JSONObject.copyObject(): JSONObject {
        val copy = JSONObject()
        val iterator = keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            copy.put(key, get(key))
        }
        return copy
    }

    private fun validateNamespaceName(value: String, context: String) {
        if (!NAMESPACE_PATTERN.matches(value)) {
            throw HomeConfigException("$context has an invalid namespace: $value")
        }
    }

    private fun validateIdentifier(value: String, limits: DikcizLimits, context: String) {
        if (value.length > limits.maxIdentifierCharacters || !IDENTIFIER_PATTERN.matches(value)) {
            throw HomeConfigException("$context has an invalid identifier")
        }
    }

    private fun canonicalIdentifier(value: String): String = value.lowercase(Locale.ROOT)

    private fun validateTitle(value: String, limits: DikcizLimits, context: String) {
        if (value.isBlank() || value.length > limits.maxTitleCharacters || value.contains(NUL_CHARACTER)) {
            throw HomeConfigException("$context cannot be blank or contain an invalid character")
        }
    }

    private fun validateText(value: String, limits: DikcizLimits) {
        if (value.length > limits.maxTextCharacters || value.contains(NUL_CHARACTER)) {
            throw HomeConfigException("text has an invalid length or character")
        }
    }

    @Suppress("DEPRECATION")
    private fun sharedStorageRoot(): File {
        return Environment.getExternalStorageDirectory()
    }

    companion object {
        const val CONFIGURATION_DIRECTORY_NAME = "Dikciz"
        const val STARTER_IDENTITY_FILE_NAME = ".bundled-starter"
        const val CONFIGURATION_FILE_NAME = "config.json"
        const val LOGS_DIRECTORY_NAME = "logs"
        const val THEMES_DIRECTORY_NAME = "themes"
        const val WIDGET_LIBRARY_DIRECTORY_NAME = "widget-library"

        private const val API_VERSION_KEY = "apiVersion"
        private const val APP_WIDGET_TYPE = "app"
        private const val APP_GROUP_WIDGET_TYPE = "appGroup"
        private const val APP_WIDGET_ID_KEY = "appWidgetId"
        private const val AUTOMATION_ACTIONS_KEY = "actions"
        private const val AUTOMATION_ALARM_INTERVAL_MILLISECONDS_KEY = "alarmIntervalMilliseconds"
        private const val AUTOMATION_API_VERSION_KEY = "apiVersion"
        private const val AUTOMATION_CAPABILITIES_KEY = "capabilities"
        private const val AUTOMATION_COALESCING_KEY = "coalescingKey"
        private const val AUTOMATION_DIRECTORY_NAME = "automation"
        private const val AUTOMATION_EVENT_KEY = "event"
        private const val AUTOMATION_HEALTH_REFRESH_INTERVAL_MILLISECONDS_KEY =
            "healthRefreshIntervalMilliseconds"
        private const val AUTOMATION_LOCATION_PRECISION_KEY = "locationPrecision"
        private const val AUTOMATION_MEDIA_SESSION_PACKAGES_KEY = "mediaSessionPackages"
        private const val AUTOMATION_MINIMUM_INTERVAL_MILLISECONDS_KEY = "minimumIntervalMilliseconds"
        private const val AUTOMATION_NAMESPACE_KEY = "automation"
        private const val AUTOMATION_NOTIFICATION_ACTION_PACKAGES_KEY = "notificationActionPackages"
        private const val AUTOMATION_PACKAGES_KEY = "packages"
        private const val AUTOMATION_POLICIES_DIRECTORY_NAME = "policies"
        private const val AUTOMATION_POLICIES_KEY = "policies"
        private const val AUTOMATION_POLICY_ID_KEY = "policyId"
        private const val AUTOMATION_SAMPLING_PERIOD_MICROSECONDS_KEY = "samplingPeriodMicroseconds"
        private const val AUTOMATION_SCRIPTS_DIRECTORY_NAME = "scripts"
        private const val AUTOMATION_SCRIPTS_KEY = "scripts"
        private const val AUTOMATION_SENSOR_TYPES_KEY = "sensorTypes"
        private const val AUTOMATION_SUBSCRIPTIONS_KEY = "subscriptions"
        private const val BUNDLED_CONFIGURATION_FILE_NAME = "config.json"
        private const val DIGEST_BYTE_FORMAT = "%02x"
        private const val EMPTY_DIGEST_SEPARATOR = ""
        private const val MAXIMUM_STARTER_IDENTITY_BYTES = 4_096L
        private const val STARTER_DIGEST_ALGORITHM = "SHA-256"
        private const val STARTER_DIGEST_KEY = "bundledStarterDigest"
        private const val STARTER_PRISTINE_KEY = "isPristine"
        private const val COMPONENT_KEY = "component"
        private const val COMPONENTS_KEY = "components"
        private const val CSS_KEY = "css"
        private const val CELL_INDEX_LIMIT_OFFSET = 1
        private const val CURRENT_CONFIGURATION_VERSION = 4
        private const val MINIMUM_CELL_INDEX = 0
        private const val DISPLAY_STYLE_KEY = "displayStyle"
        private const val ENABLED_KEY = "enabled"
        private const val HTML_HEIGHT_MODE_KEY = "heightMode"
        private const val HTML_EVENT_SUBSCRIPTIONS_KEY = "eventSubscriptions"
        private const val HTML_KEY = "html"
        private const val HTML_WIDGET_TYPE = "html"
        private const val HARD_MAXIMUM_COMPONENT_CHARACTERS = 8_192
        const val MAXIMUM_CONFIGURATION_DOCUMENT_BYTES = 8_388_608
        private const val HARD_MAXIMUM_IDENTIFIER_CHARACTERS = 256
        private const val HARD_MAXIMUM_PAGE_COUNT = 256
        private const val HARD_MAXIMUM_TEXT_CHARACTERS = 1_000_000
        private const val HARD_MAXIMUM_TITLE_CHARACTERS = 8_192
        private const val HARD_MAXIMUM_WIDGET_COUNT = 1_024
        private const val HOME_NAMESPACE_KEY = "home"
        private const val HOME_PAGE_ID_KEY = "homePageId"
        private const val ID_KEY = "id"
        private const val JSON_INDENTATION_SPACES = 2
        private const val LAUNCHER_NAMESPACE_KEY = "launcher"
        private const val LIMITS_NAMESPACE_KEY = "limits"
        private const val LOGGING_NAMESPACE_KEY = "logging"
        private const val LOG_LEVEL_KEY = "level"
        private const val LOG_MAXIMUM_TOTAL_BYTES_KEY = "maxTotalBytes"
        private const val LOG_NOTIFY_ON_SCRIPT_ERROR_KEY = "notifyOnScriptError"
        private const val LOG_RETENTION_DAYS_KEY = "retentionDays"
        private const val LOG_SCRIPT_ERROR_NOTIFICATION_MINIMUM_INTERVAL_MILLISECONDS_KEY =
            "scriptErrorNotificationMinimumIntervalMilliseconds"
        private const val MAX_COMPONENT_CHARACTERS_KEY = "maxComponentCharacters"
        private const val MAX_CONFIG_BYTES_KEY = "maxConfigBytes"
        private const val MAX_HTML_WIDGET_DOCUMENT_BYTES_KEY = "maxHtmlWidgetDocumentBytes"
        private const val MAX_HTML_WIDGETS_PER_PAGE_KEY = "maxHtmlWidgetsPerPage"
        private const val MAX_IDENTIFIER_CHARACTERS_KEY = "maxIdentifierCharacters"
        private const val MAX_PAGES_KEY = "maxPages"
        private const val MAX_TEXT_CHARACTERS_KEY = "maxTextCharacters"
        private const val MAX_TITLE_CHARACTERS_KEY = "maxTitleCharacters"
        private const val MAX_WIDGETS_PER_PAGE_KEY = "maxWidgetsPerPage"
        private const val MAXIMUM_SIZE_LENGTH = 16
        private const val MAXIMUM_LOG_LEVEL_LENGTH = 16
        private const val MAXIMUM_APP_WIDGET_DISPLAY_STYLE_LENGTH = 24
        private const val MAXIMUM_SCRIPT_ARCHIVE_MANIFEST_BYTES = 1_024
        private const val MAXIMUM_SCRIPT_ARCHIVE_METADATA_BYTES = 8_192
        private const val MAXIMUM_SCRIPT_ARCHIVE_KIND_LENGTH = 64
        private const val MAXIMUM_SCRIPT_ARCHIVE_FILE_NAME_CHARACTERS = 128
        private const val MAXIMUM_SCRIPT_ARCHIVE_NAME_ATTEMPTS = 1_000
        private const val MAXIMUM_SCRIPT_ARCHIVE_BYTES = 196_608L
        private const val MAXIMUM_SCRIPT_STATE_BYTES = 65_536
        private const val MAXIMUM_SCRIPT_RUN_OUTCOME_CHARACTERS = 16
        private const val MAXIMUM_SCRIPT_RUN_STATUS_BYTES = 8_192
        private const val MAXIMUM_SCRIPT_RUN_TIMESTAMP_CHARACTERS = 64
        private const val MAXIMUM_SCRIPT_STATE_DEPTH = 8
        private const val MAXIMUM_SCRIPT_STATE_ENTRIES = 128
        private const val MAXIMUM_LUA_SCRIPT_SOURCE_BYTES = 65_536L
        private const val MAXIMUM_TYPE_LENGTH = 16
        private const val MAXIMUM_AUTOMATION_ALARM_INTERVAL_MILLISECONDS = 86_400_000L
        private const val MAXIMUM_AUTOMATION_HEALTH_REFRESH_INTERVAL_MILLISECONDS = 86_400_000L
        private const val MAXIMUM_AUTOMATION_INTERVAL_MILLISECONDS = 86_400_000L
        private const val MAXIMUM_AUTOMATION_PACKAGE_NAME_CHARACTERS = 255
        private const val MAXIMUM_AUTOMATION_PACKAGES = 64
        private const val MAXIMUM_AUTOMATION_POLICIES = 256
        private const val MAXIMUM_AUTOMATION_SAMPLING_PERIOD_MICROSECONDS = 1_000_000
        private const val MAXIMUM_AUTOMATION_SCRIPTS = 256
        private const val MAXIMUM_AUTOMATION_SENSOR_TYPES = 64
        private const val MAXIMUM_AUTOMATION_SENSOR_TYPE = 65_536
        private const val MAXIMUM_AUTOMATION_SUBSCRIPTIONS = 128
        private const val MAXIMUM_AUTOMATION_VALUE_CHARACTERS = 64
        private const val EMPTY_OBJECT_LENGTH = 0
        private const val END_OF_STREAM = -1
        private const val FIRST_SCRIPT_ARCHIVE_INDEX = 1
        private const val MINIMUM_COMPONENT_CHARACTERS = 1
        private const val MINIMUM_APP_WIDGET_ID = 1
        private const val MINIMUM_APP_GROUP_COMPONENTS = 1
        private const val MINIMUM_AUTOMATION_ALARM_INTERVAL_MILLISECONDS = 60_000L
        private const val MINIMUM_AUTOMATION_HEALTH_REFRESH_INTERVAL_MILLISECONDS = 900_000L
        private const val MINIMUM_AUTOMATION_INTERVAL_MILLISECONDS = 0L
        private const val MINIMUM_AUTOMATION_SAMPLING_PERIOD_MICROSECONDS = 1_000
        private const val MINIMUM_AUTOMATION_SENSOR_TYPE = 1
        private const val MINIMUM_CONFIG_BYTES = 1_024
        private const val MINIMUM_IDENTIFIER_CHARACTERS = 1
        private const val MINIMUM_PAGE_COUNT = 1
        private const val MINIMUM_LOG_MAXIMUM_TOTAL_BYTES = 65_536
        private const val MINIMUM_LOG_RETENTION_DAYS = 1
        private const val MINIMUM_SCRIPT_ERROR_NOTIFICATION_INTERVAL_MILLISECONDS = 10_000
        private const val MINIMUM_LUA_API_VERSION = 1
        private const val MINIMUM_TEXT_CHARACTERS = 0
        private const val MINIMUM_TITLE_CHARACTERS = 1
        private const val MINIMUM_WIDGET_COUNT = 0
        private const val MAXIMUM_WRITE_FAILURE_DETAIL_CHARACTERS = 512
        private const val JSON_FILE_EXTENSION = "json"
        private const val JAVASCRIPT_KEY = "javascript"
        private const val NUL_CHARACTER = '\u0000'
        private const val PAGE_FILE_NAME = "page.json"
        private const val PAGES_KEY = "pages"
        private const val PAGES_DIRECTORY_NAME = "pages"
        private const val POSITION_KEY = "position"
        private const val CELL_KEY = "cell"
        private const val CELL_COLUMN_KEY = "column"
        private const val CELL_COLUMN_SPAN_KEY = "columnSpan"
        private const val CELL_ROW_KEY = "row"
        private const val CELL_ROW_SPAN_KEY = "rowSpan"
        private const val GRID_COLUMNS_KEY = "columns"
        private const val GRID_GAP_DP_KEY = "gapDp"
        private const val GRID_OUTER_PADDING_DP_KEY = "outerPaddingDp"
        private const val GRID_ROWS_KEY = "rows"
        private const val NATIVE_GRID_KEY = "nativeGrid"
        private const val PAGE_COLUMN_KEY = "column"
        private const val PAGE_POSITION_MINIMUM_INDEX = 0
        private const val PAGE_ROW_KEY = "row"
        private const val SELECTED_PAGE_ID_KEY = "selectedPageId"
        private const val SELECTED_PAGE_ID_MISSING_MESSAGE = "selectedPageId does not identify a page"
        private const val SELECTED_THEME_ID_KEY = "selectedThemeId"
        private const val SCRIPT_ID_KEY = "scriptId"
        private const val SCRIPT_ARCHIVE_FILE_EXTENSION = ".dikciz-script.zip"
        private const val SCRIPT_ARCHIVE_KIND = "dikciz-lua-script"
        private const val SCRIPT_ARCHIVE_KIND_KEY = "kind"
        private const val SCRIPT_ARCHIVE_MANIFEST_FILE_NAME = "archive.json"
        private const val SCRIPT_ARCHIVE_READ_BUFFER_BYTES = 4_096
        private const val SCRIPT_ARCHIVE_VERSION_KEY = "archiveVersion"
        private const val SCRIPT_METADATA_FILE_NAME = "script.json"
        private const val SCRIPT_SOURCE_FILE_NAME = "main.lua"
        private const val SCRIPT_SOURCE_KEY = "source"
        private const val SCRIPT_STATE_FILE_NAME = "state.json"
        private const val SCRIPT_RUN_LAST_RUN_AT_KEY = "lastRunAt"
        private const val SCRIPT_RUN_OUTCOME_KEY = "outcome"
        private const val SCRIPT_RUN_STATUS_FILE_NAME = "run-status.json"
        private const val SCRIPT_RUN_STATUS_KEY = "status"
        private const val SCRIPTS_DIRECTORY_NAME = "scripts"
        private const val SCRIPT_EXPORT_DIRECTORY_NAME = "exports"
        private const val SCRIPT_IMPORT_DIRECTORY_NAME = "imports"
        private const val SCRIPTS_NAMESPACE_KEY = "scripts"
        private const val STATE_KEY = "state"
        private const val TITLE_KEY = "title"
        private const val TYPE_KEY = "type"
        private const val PROVIDER_KEY = "provider"
        private const val PROVIDER_WIDGET_TYPE = "provider"
        private const val VERSION_KEY = "version"
        private const val WIDGETS_KEY = "widgets"
        private const val WIDGETS_DIRECTORY_NAME = "widgets"
        private const val SCRIPT_DASHBOARD_WIDGET_TYPE = "scriptDashboard"
        private const val MAXIMUM_SCRIPT_COUNT = 256
        private const val ONE_STATE_LEVEL = 1
        private const val ROOT_SCRIPT_STATE_DEPTH = 0
        private const val CURRENT_SCRIPT_ARCHIVE_VERSION = 1

        private val IDENTIFIER_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_.-]*$")
        private val ANDROID_PACKAGE_NAME_PATTERN = Regex(
            "^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$",
        )
        private val NAMESPACE_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_.-]*$")
        private val SCRIPT_ARCHIVE_FILE_NAME_PATTERN = Regex(
            "^[A-Za-z0-9][A-Za-z0-9_. -]*\\.dikciz-script\\.zip$",
        )
        private val SCRIPT_STATE_KEY_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_.-]*$")
        private val HOME_KEYS = setOf(
            DikcizLauncherBackgroundCodec.BACKGROUND_KEY,
            HOME_PAGE_ID_KEY,
            SELECTED_PAGE_ID_KEY,
            SELECTED_THEME_ID_KEY,
            DikcizStyleCodec.STYLE_DEFAULTS_KEY,
            NATIVE_GRID_KEY,
            PAGES_KEY,
        )
        private val AUTOMATION_KEYS = setOf(
            AUTOMATION_API_VERSION_KEY,
            AUTOMATION_POLICIES_KEY,
            AUTOMATION_SCRIPTS_KEY,
        )
        private val AUTOMATION_MANIFEST_KEYS = AUTOMATION_KEYS
        private val AUTOMATION_POLICY_KEYS = setOf(
            ID_KEY,
            TITLE_KEY,
            ENABLED_KEY,
            AUTOMATION_CAPABILITIES_KEY,
            AUTOMATION_ACTIONS_KEY,
            AUTOMATION_MEDIA_SESSION_PACKAGES_KEY,
            AUTOMATION_NOTIFICATION_ACTION_PACKAGES_KEY,
        )
        private val AUTOMATION_SCRIPT_KEYS = setOf(
            SCRIPT_ID_KEY,
            AUTOMATION_POLICY_ID_KEY,
            ENABLED_KEY,
            AUTOMATION_SUBSCRIPTIONS_KEY,
        )
        private val AUTOMATION_SUBSCRIPTION_KEYS = setOf(
            AUTOMATION_EVENT_KEY,
            AUTOMATION_MINIMUM_INTERVAL_MILLISECONDS_KEY,
            AUTOMATION_COALESCING_KEY,
            AUTOMATION_PACKAGES_KEY,
            AUTOMATION_SENSOR_TYPES_KEY,
            AUTOMATION_SAMPLING_PERIOD_MICROSECONDS_KEY,
            AUTOMATION_ALARM_INTERVAL_MILLISECONDS_KEY,
            AUTOMATION_LOCATION_PRECISION_KEY,
            AUTOMATION_HEALTH_REFRESH_INTERVAL_MILLISECONDS_KEY,
        )
        private val SCRIPT_DASHBOARD_WIDGET_KEYS = setOf(
            ID_KEY,
            TITLE_KEY,
            TYPE_KEY,
            ENABLED_KEY,
            CELL_KEY,
            DikcizStyleCodec.STYLE_KEY,
            DikcizStyleCodec.LOCKED_KEY,
        )
        private val LOGGING_KEYS = setOf(
            LOG_LEVEL_KEY,
            LOG_RETENTION_DAYS_KEY,
            LOG_MAXIMUM_TOTAL_BYTES_KEY,
            LOG_NOTIFY_ON_SCRIPT_ERROR_KEY,
            LOG_SCRIPT_ERROR_NOTIFICATION_MINIMUM_INTERVAL_MILLISECONDS_KEY,
        )
        private val LIMIT_KEYS = setOf(
            MAX_CONFIG_BYTES_KEY,
            MAX_COMPONENT_CHARACTERS_KEY,
            MAX_HTML_WIDGET_DOCUMENT_BYTES_KEY,
            MAX_HTML_WIDGETS_PER_PAGE_KEY,
            MAX_IDENTIFIER_CHARACTERS_KEY,
            MAX_PAGES_KEY,
            MAX_TEXT_CHARACTERS_KEY,
            MAX_TITLE_CHARACTERS_KEY,
            MAX_WIDGETS_PER_PAGE_KEY,
        )
        private val SCRIPT_KEYS = setOf(
            API_VERSION_KEY,
            ENABLED_KEY,
            ID_KEY,
            SCRIPT_SOURCE_KEY,
            STATE_KEY,
            TITLE_KEY,
        )
        private val SCRIPT_METADATA_KEYS = setOf(
            API_VERSION_KEY,
            ENABLED_KEY,
            ID_KEY,
            TITLE_KEY,
        )
        private val SCRIPT_RUN_STATUS_KEYS = setOf(
            SCRIPT_RUN_LAST_RUN_AT_KEY,
            SCRIPT_RUN_OUTCOME_KEY,
            SCRIPT_RUN_STATUS_KEY,
        )
        private val SCRIPT_ARCHIVE_ENTRY_NAMES = setOf(
            SCRIPT_ARCHIVE_MANIFEST_FILE_NAME,
            SCRIPT_METADATA_FILE_NAME,
            SCRIPT_SOURCE_FILE_NAME,
            SCRIPT_STATE_FILE_NAME,
        )
        private val SCRIPT_ARCHIVE_MANIFEST_KEYS = setOf(
            SCRIPT_ARCHIVE_KIND_KEY,
            SCRIPT_ARCHIVE_VERSION_KEY,
            SCRIPT_ID_KEY,
        )
        private val PAGE_KEYS = setOf(
            ID_KEY,
            TITLE_KEY,
            POSITION_KEY,
            DikcizStyleCodec.LOCKED_KEY,
            WIDGETS_KEY,
        )
        private val HTML_WIDGET_KEYS = setOf(
            ID_KEY,
            TYPE_KEY,
            TITLE_KEY,
            HTML_KEY,
            CSS_KEY,
            JAVASCRIPT_KEY,
            STATE_KEY,
            HTML_HEIGHT_MODE_KEY,
            HTML_EVENT_SUBSCRIPTIONS_KEY,
            ENABLED_KEY,
            CELL_KEY,
            DikcizStyleCodec.STYLE_KEY,
            DikcizStyleCodec.LOCKED_KEY,
        )
        private val APP_WIDGET_KEYS = setOf(
            ID_KEY,
            TYPE_KEY,
            TITLE_KEY,
            COMPONENT_KEY,
            DISPLAY_STYLE_KEY,
            ENABLED_KEY,
            CELL_KEY,
            DikcizStyleCodec.STYLE_KEY,
            DikcizStyleCodec.LOCKED_KEY,
        )
        private val APP_GROUP_WIDGET_KEYS = setOf(
            ID_KEY,
            TYPE_KEY,
            TITLE_KEY,
            COMPONENTS_KEY,
            ENABLED_KEY,
            CELL_KEY,
            DikcizStyleCodec.STYLE_KEY,
            DikcizStyleCodec.LOCKED_KEY,
        )
        private val PROVIDER_WIDGET_KEYS = setOf(
            ID_KEY,
            TYPE_KEY,
            TITLE_KEY,
            PROVIDER_KEY,
            APP_WIDGET_ID_KEY,
            ENABLED_KEY,
            CELL_KEY,
            DikcizStyleCodec.STYLE_KEY,
            DikcizStyleCodec.LOCKED_KEY,
        )
        private val WIDGET_CELL_KEYS = setOf(
            CELL_COLUMN_KEY,
            CELL_ROW_KEY,
            CELL_COLUMN_SPAN_KEY,
            CELL_ROW_SPAN_KEY,
        )
        private val NATIVE_GRID_KEYS = setOf(
            GRID_COLUMNS_KEY,
            GRID_ROWS_KEY,
            GRID_GAP_DP_KEY,
            GRID_OUTER_PADDING_DP_KEY,
        )
        private val PAGE_POSITION_KEYS = setOf(PAGE_COLUMN_KEY, PAGE_ROW_KEY)
        private val DEFAULT_LOGGING_CONFIGURATION = DikcizLoggingConfiguration(
            level = DikcizLogLevel.Info,
            retentionDays = DikcizLoggingConfiguration.DEFAULT_RETENTION_DAYS,
            maxTotalBytes = DikcizLoggingConfiguration.DEFAULT_MAXIMUM_TOTAL_BYTES,
        )
        private const val HARD_MAXIMUM_LOG_MAXIMUM_TOTAL_BYTES = 134_217_728
        private const val HARD_MAXIMUM_LOG_RETENTION_DAYS = 90
        private const val HARD_MAXIMUM_SCRIPT_ERROR_NOTIFICATION_INTERVAL_MILLISECONDS = 3_600_000
    }
}

internal class HomeConfigException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
