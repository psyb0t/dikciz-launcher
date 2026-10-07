package org.fossify.home.dikciz

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.nio.charset.StandardCharsets
import org.json.JSONException
import org.json.JSONObject

internal data class DikcizTheme(
    val id: String,
    val title: String,
    val launcherBackground: DikcizLauncherBackground,
    val widgetStyle: DikcizStyle,
)

internal data class DikcizThemeCatalog(
    val themes: List<DikcizTheme>,
    val rejectedThemeCount: Int,
)

internal enum class DikcizThemeState {
    None,
    Clean,
    Missing,
    Modified,
}

internal class DikcizThemeStore(
    private val context: Context,
    private val themeDirectory: File,
) {
    fun loadDefaultBundledTheme(): DikcizTheme = loadBundledTheme(DEFAULT_BUNDLED_THEME_ID)

    fun loadBundledTheme(themeID: String): DikcizTheme {
        if (!THEME_ID_PATTERN.matches(themeID)) {
            throw HomeConfigException("bundled theme ID is invalid")
        }
        val assetName = "$themeID$THEME_FILE_SUFFIX"
        return readTheme(themeID, readBundledThemeContent(assetName))
    }

    fun loadCatalog(): DikcizThemeCatalog {
        ensureThemeDirectory()
        installBundledThemes()
        var rejectedThemeCount = 0
        val themes = themeDirectory.listFiles().orEmpty()
            .asSequence()
            .filter { file -> file.isFile && file.extension.equals(THEME_FILE_EXTENSION, ignoreCase = true) }
            .filter { file -> THEME_ID_PATTERN.matches(file.nameWithoutExtension) }
            .sortedBy { file -> file.name.lowercase() }
            .mapNotNull { file ->
                try {
                    readTheme(file)
                } catch (_: HomeConfigException) {
                    rejectedThemeCount += 1
                    null
                }
            }
            .toList()
        return DikcizThemeCatalog(themes, rejectedThemeCount)
    }

    private fun ensureThemeDirectory() {
        if (themeDirectory.exists() && !themeDirectory.isDirectory) {
            throw HomeConfigException("themes directory is unavailable")
        }
        if (!themeDirectory.exists() && !themeDirectory.mkdirs()) {
            throw HomeConfigException("themes directory could not be created")
        }
    }

    private fun installBundledThemes() {
        bundledThemeNames()
            .filter { name -> name.endsWith(THEME_FILE_SUFFIX) }
            .sorted()
            .forEach { name ->
                val target = File(themeDirectory, name)
                if (target.exists()) {
                    return@forEach
                }
                writeTheme(target, readBundledThemeContent(name))
            }
    }

    private fun bundledThemeNames(): List<String> {
        return try {
            context.assets.list(BUNDLED_THEME_DIRECTORY)
                ?.map { name -> name }
                ?: emptyList()
        } catch (exception: Exception) {
            throw HomeConfigException("bundled themes are unavailable", exception)
        }
    }

    private fun readBundledThemeContent(name: String): String {
        return try {
            context.assets.open("$BUNDLED_THEME_DIRECTORY/$name")
                .bufferedReader(StandardCharsets.UTF_8)
                .use { reader -> reader.readText() }
        } catch (exception: Exception) {
            throw HomeConfigException("bundled theme could not be read", exception)
        }
    }

    private fun writeTheme(target: File, content: String) {
        val atomicFile = AtomicFile(target)
        val output = try {
            atomicFile.startWrite()
        } catch (exception: Exception) {
            throw HomeConfigException("theme could not be installed", exception)
        }
        try {
            output.write(content.toByteArray(StandardCharsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (exception: Exception) {
            atomicFile.failWrite(output)
            throw HomeConfigException("theme could not be installed", exception)
        }
    }

    private fun readTheme(file: File): DikcizTheme {
        if (file.length() > MAXIMUM_THEME_DOCUMENT_BYTES) {
            throw HomeConfigException("theme exceeds the size limit")
        }
        val content = try {
            file.readText(StandardCharsets.UTF_8)
        } catch (exception: Exception) {
            throw HomeConfigException("theme could not be read", exception)
        }
        return readTheme(file.nameWithoutExtension, content)
    }

    private fun readTheme(themeID: String, content: String): DikcizTheme {
        val document = try {
            JSONObject(content)
        } catch (exception: JSONException) {
            throw HomeConfigException("theme is not valid JSON", exception)
        } catch (exception: Exception) {
            throw HomeConfigException("theme could not be read", exception)
        }
        document.requireOnlyThemeKeys(THEME_KEYS)
        val version = document.requireThemeInteger(THEME_VERSION_KEY)
        if (version != CURRENT_THEME_VERSION) {
            throw HomeConfigException("theme has an unsupported version")
        }
        val title = document.requireThemeTitle(THEME_TITLE_KEY)
        return DikcizTheme(
            id = themeID,
            title = title,
            launcherBackground = DikcizLauncherBackgroundCodec.parseRequired(document, "theme.$themeID"),
            widgetStyle = DikcizStyleCodec.parseRequiredThemeWidgetStyle(document, "theme.$themeID"),
        )
    }

    private fun JSONObject.requireOnlyThemeKeys(allowedKeys: Set<String>) {
        val iterator = keys()
        while (iterator.hasNext()) {
            if (iterator.next() !in allowedKeys) {
                throw HomeConfigException("theme contains an unsupported field")
            }
        }
    }

    private fun JSONObject.requireThemeInteger(key: String): Int {
        if (!has(key) || isNull(key)) {
            throw HomeConfigException("theme version is required")
        }
        val value = get(key)
        if (value !is Number || value.toLong().toDouble() != value.toDouble()) {
            throw HomeConfigException("theme version must be an integer")
        }
        return value.toInt()
    }

    private fun JSONObject.requireThemeTitle(key: String): String {
        if (!has(key) || isNull(key)) {
            throw HomeConfigException("theme title is required")
        }
        val value = get(key)
        if (
            value !is String ||
            value.isBlank() ||
            value.length > MAXIMUM_THEME_TITLE_CHARACTERS ||
            value.contains(NUL_CHARACTER)
        ) {
            throw HomeConfigException("theme title is invalid")
        }
        return value.trim()
    }

    private companion object {
        const val BUNDLED_THEME_DIRECTORY = "themes"
        const val CURRENT_THEME_VERSION = 1
        const val DEFAULT_BUNDLED_THEME_ID = "dikciz-dark"
        const val MAXIMUM_THEME_DOCUMENT_BYTES = 65_536
        const val MAXIMUM_THEME_TITLE_CHARACTERS = 120
        const val NUL_CHARACTER = '\u0000'
        const val THEME_FILE_EXTENSION = "json"
        const val THEME_FILE_SUFFIX = ".$THEME_FILE_EXTENSION"
        const val THEME_TITLE_KEY = "title"
        const val THEME_VERSION_KEY = "version"
        val THEME_ID_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_.-]*$")
        val THEME_KEYS = setOf(
            THEME_VERSION_KEY,
            THEME_TITLE_KEY,
            DikcizLauncherBackgroundCodec.BACKGROUND_KEY,
            DikcizStyleCodec.THEME_WIDGET_STYLE_KEY,
        )
    }
}

internal fun HomeConfiguration.applyTheme(theme: DikcizTheme): HomeConfiguration {
    return copy(
        selectedThemeID = theme.id,
        launcherBackground = null,
        styleDefaults = DikcizStyleDefaults(),
        pages = pages.map { page ->
            page.copy(
                widgets = page.widgets.map(HomeWidget::withoutStyleOverride),
            )
        },
    )
}

internal fun HomeConfiguration.themeState(catalog: DikcizThemeCatalog): DikcizThemeState {
    val themeID = selectedThemeID ?: return DikcizThemeState.None
    val theme = catalog.themes.firstOrNull { candidate -> candidate.id == themeID }
        ?: return DikcizThemeState.Missing
    if (launcherBackground != null || styleDefaults != DikcizStyleDefaults()) {
        return DikcizThemeState.Modified
    }
    val hasWidgetOverrides = pages.any { page ->
        page.widgets.any { widget -> widget.style != null }
    }
    return if (hasWidgetOverrides) DikcizThemeState.Modified else DikcizThemeState.Clean
}

internal val DikcizThemeState.persistedValue: String
    get() = when (this) {
        DikcizThemeState.None -> "none"
        DikcizThemeState.Clean -> "clean"
        DikcizThemeState.Missing -> "missing"
        DikcizThemeState.Modified -> "modified"
    }

internal fun HomeConfiguration.themeWidgetCount(): Int {
    return pages.sumOf { page -> page.widgets.sumOf { widget -> widget.themeWidgetCount() } }
}

private fun HomeWidget.themeWidgetCount(): Int = 1

private fun HomeWidget.withoutStyleOverride(): HomeWidget {
    return when (this) {
        is HtmlHomeWidget -> copy(style = null)
        is AppHomeWidget -> copy(style = null)
        is AppGroupHomeWidget -> copy(style = null)
        is ProviderHomeWidget -> copy(style = null)
        is ScriptDashboardHomeWidget -> copy(style = null)
    }
}
