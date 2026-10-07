package org.fossify.home.dikciz

import org.json.JSONObject

internal object DikcizLauncherBackgroundCodec {
    const val BACKGROUND_KEY = "background"
    const val WALLPAPERS_DIRECTORY_NAME = "wallpapers"

    private const val COLOR_KEY = "color"
    private const val MAXIMUM_WALLPAPER_FILE_NAME_LENGTH = 128
    private const val WALLPAPER_KEY = "wallpaper"

    private val BACKGROUND_KEYS = setOf(COLOR_KEY, WALLPAPER_KEY)
    private val COLOR_PATTERN = Regex("^#[0-9A-Fa-f]{6}$")
    private val WALLPAPER_FILE_NAME_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]*$")
    private val WALLPAPER_FILE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    fun parseOptional(home: JSONObject, context: String): DikcizLauncherBackground? {
        if (!home.has(BACKGROUND_KEY)) {
            return null
        }
        val backgroundContext = "$context.$BACKGROUND_KEY"
        val document = home.requireBackgroundObject(context)
        document.requireOnlyBackgroundKeys(backgroundContext)
        val color = document.requireColor(backgroundContext)
        val wallpaper = document.optionalWallpaper(backgroundContext)
        return DikcizLauncherBackground(color, wallpaper)
    }

    fun parseRequired(document: JSONObject, context: String): DikcizLauncherBackground {
        if (!document.has(BACKGROUND_KEY) || document.isNull(BACKGROUND_KEY)) {
            throw HomeConfigException("$context.$BACKGROUND_KEY is required")
        }
        return parseOptional(document, context)
            ?: throw HomeConfigException("$context.$BACKGROUND_KEY is required")
    }

    fun validate(background: DikcizLauncherBackground, context: String) {
        validateColor(background.color, "$context.$COLOR_KEY")
        background.wallpaper?.let { wallpaper ->
            if (!isSupportedWallpaperFileName(wallpaper)) {
                throw HomeConfigException(
                    "$context.$WALLPAPER_KEY must name a PNG, JPEG, or WebP file in $WALLPAPERS_DIRECTORY_NAME",
                )
            }
        }
    }

    fun isSupportedWallpaperFileName(fileName: String): Boolean {
        if (
            fileName.length > MAXIMUM_WALLPAPER_FILE_NAME_LENGTH ||
            !WALLPAPER_FILE_NAME_PATTERN.matches(fileName)
        ) {
            return false
        }
        return fileName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase() in WALLPAPER_FILE_EXTENSIONS
    }

    fun DikcizLauncherBackground.toJson(): JSONObject {
        return JSONObject()
            .put(COLOR_KEY, color)
            .apply {
                wallpaper?.let { value -> put(WALLPAPER_KEY, value) }
            }
    }

    private fun JSONObject.requireOnlyBackgroundKeys(context: String) {
        val iterator = keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (key !in BACKGROUND_KEYS) {
                throw HomeConfigException("$context has an unknown key: $key")
            }
        }
    }

    private fun JSONObject.requireBackgroundObject(context: String): JSONObject {
        if (!has(BACKGROUND_KEY) || isNull(BACKGROUND_KEY)) {
            throw HomeConfigException("$context.$BACKGROUND_KEY is required")
        }
        val value = get(BACKGROUND_KEY)
        if (value !is JSONObject) {
            throw HomeConfigException("$context.$BACKGROUND_KEY must be an object")
        }
        return value
    }

    private fun JSONObject.requireColor(context: String): String {
        if (!has(COLOR_KEY) || isNull(COLOR_KEY)) {
            throw HomeConfigException("$context.$COLOR_KEY is required")
        }
        val value = get(COLOR_KEY)
        if (value !is String) {
            throw HomeConfigException("$context.$COLOR_KEY must be a color")
        }
        validateColor(value, "$context.$COLOR_KEY")
        return value
    }

    private fun JSONObject.optionalWallpaper(context: String): String? {
        if (!has(WALLPAPER_KEY)) {
            return null
        }
        val value = get(WALLPAPER_KEY)
        if (value !is String || !isSupportedWallpaperFileName(value)) {
            throw HomeConfigException(
                "$context.$WALLPAPER_KEY must name a PNG, JPEG, or WebP file in $WALLPAPERS_DIRECTORY_NAME",
            )
        }
        return value
    }

    private fun validateColor(color: String, context: String) {
        if (!COLOR_PATTERN.matches(color)) {
            throw HomeConfigException("$context must be #RRGGBB")
        }
    }
}
