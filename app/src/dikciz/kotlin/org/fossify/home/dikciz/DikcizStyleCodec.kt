package org.fossify.home.dikciz

import org.json.JSONObject

internal object DikcizStyleCodec {
    const val FONT_DIRECTORY_NAME = "fonts"
    const val LOCKED_KEY = "locked"
    const val MAXIMUM_STYLE_DIMENSION_DP = 512
    const val MAXIMUM_TEXT_SIZE_SP = 256
    const val MAXIMUM_OPACITY = 1.0
    const val MINIMUM_OPACITY = 0.0
    const val MINIMUM_STYLE_DIMENSION_DP = 0
    const val MINIMUM_TEXT_SIZE_SP = 1
    const val STYLE_DEFAULTS_KEY = "styleDefaults"
    const val STYLE_KEY = "style"

    private const val ALL_KEY = "all"
    private const val BACKGROUND_KEY = "background"
    private const val BORDER_KEY = "border"
    private const val BORDER_RADIUS_KEY = "radius"
    private const val BORDER_WIDTHS_KEY = "widths"
    private const val COLOR_KEY = "color"
    private const val FONT_ID_KEY = "id"
    private const val FONT_KEY = "font"
    private const val FONT_SOURCE_KEY = "source"
    private const val MARGIN_KEY = "margin"
    private const val MAXIMUM_COLOR_LENGTH = 9
    private const val MAXIMUM_FONT_FILE_NAME_LENGTH = 128
    private const val MAXIMUM_FONT_ID_LENGTH = 32
    private const val OPACITY_KEY = "opacity"
    private const val PADDING_KEY = "padding"
    private const val SIDE_BOTTOM_KEY = "bottom"
    private const val SIDE_LEFT_KEY = "left"
    private const val SIDE_RIGHT_KEY = "right"
    private const val SIDE_TOP_KEY = "top"
    private const val TEXT_KEY = "text"
    private const val TEXT_SIZE_SP_KEY = "sizeSp"
    private const val WIDGET_DEFAULT_KEY = "widget"

    private val BACKGROUND_KEYS = setOf(COLOR_KEY, OPACITY_KEY)
    private val BORDER_KEYS = setOf(COLOR_KEY, BORDER_RADIUS_KEY, BORDER_WIDTHS_KEY)
    private val COLOR_PATTERN = Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")
    private val FONT_FILE_NAME_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]*$")
    private val FONT_KEYS = setOf(FONT_ID_KEY, FONT_SOURCE_KEY)
    private val LEGACY_STYLE_DEFAULT_KEYS = setOf("launcher", "page", WIDGET_DEFAULT_KEY)
    private val STYLE_KEYS = setOf(BACKGROUND_KEY, BORDER_KEY, MARGIN_KEY, PADDING_KEY, TEXT_KEY)
    private val TEXT_KEYS = setOf(COLOR_KEY, FONT_KEY, TEXT_SIZE_SP_KEY)
    private val BOX_EDGE_KEYS = setOf(
        ALL_KEY,
        SIDE_BOTTOM_KEY,
        SIDE_LEFT_KEY,
        SIDE_RIGHT_KEY,
        SIDE_TOP_KEY,
    )

    fun parseOptionalStyleDefaults(
        home: JSONObject,
        context: String,
    ): DikcizStyleDefaults {
        if (!home.has(STYLE_DEFAULTS_KEY)) {
            return DikcizStyleDefaults()
        }
        val defaultsContext = "$context.$STYLE_DEFAULTS_KEY"
        val defaults = home.requireStyleObject(STYLE_DEFAULTS_KEY, context)
        return defaults.parseStyleDefaults(defaultsContext)
    }

    fun parseRequiredThemeWidgetStyle(document: JSONObject, context: String): DikcizStyle {
        val style = document.requireStyleObject(THEME_WIDGET_STYLE_KEY, context)
        style.requireRequiredStyleKeys(STYLE_KEYS, "$context.$THEME_WIDGET_STYLE_KEY")
        return style.parseThemeWidgetStyle("$context.$THEME_WIDGET_STYLE_KEY")
    }

    private fun JSONObject.parseStyleDefaults(context: String): DikcizStyleDefaults {
        requireOnlyStyleKeys(LEGACY_STYLE_DEFAULT_KEYS, context)
        return DikcizStyleDefaults(
            widget = parseOptionalStyle(WIDGET_DEFAULT_KEY, context) ?: DikcizStyle(),
        )
    }

    private fun JSONObject.parseThemeWidgetStyle(context: String): DikcizStyle {
        return DikcizStyle(
            background = parseOptionalBackground(context),
            border = parseOptionalBorder(context),
            margin = parseOptionalBoxEdges(MARGIN_KEY, context),
            padding = parseOptionalBoxEdges(PADDING_KEY, context),
            text = parseOptionalTextStyle(context),
        ).also { style -> validateCompleteStyle(style, context) }
    }

    fun parseOptionalStyle(
        json: JSONObject,
        context: String,
    ): DikcizStyle? {
        return json.parseOptionalStyle(STYLE_KEY, context)
    }

    fun validate(style: DikcizStyle, context: String) {
        style.background?.let { background ->
            background.color?.let { color -> validateColor(color, "$context.$BACKGROUND_KEY.$COLOR_KEY") }
            background.opacity?.let { opacity ->
                if (!opacity.isFinite() || opacity !in MINIMUM_OPACITY..MAXIMUM_OPACITY) {
                    throw HomeConfigException("$context.$BACKGROUND_KEY.$OPACITY_KEY must be 0 through 1")
                }
            }
        }
        style.border?.let { border ->
            border.color?.let { color -> validateColor(color, "$context.$BORDER_KEY.$COLOR_KEY") }
            border.radiusDP?.let { radiusDP ->
                validateStyleDimension(radiusDP, "$context.$BORDER_KEY.$BORDER_RADIUS_KEY")
            }
            border.widths?.let { widths -> validateBoxEdges(widths, "$context.$BORDER_KEY.$BORDER_WIDTHS_KEY") }
        }
        style.margin?.let { margin -> validateBoxEdges(margin, "$context.$MARGIN_KEY") }
        style.padding?.let { padding -> validateBoxEdges(padding, "$context.$PADDING_KEY") }
        style.text?.let { text ->
            text.color?.let { color -> validateColor(color, "$context.$TEXT_KEY.$COLOR_KEY") }
            text.font?.let { font -> validateFont(font, "$context.$TEXT_KEY.$FONT_KEY") }
            text.sizeSP?.let { sizeSP ->
                if (sizeSP !in MINIMUM_TEXT_SIZE_SP..MAXIMUM_TEXT_SIZE_SP) {
                    throw HomeConfigException(
                        "$context.$TEXT_KEY.$TEXT_SIZE_SP_KEY must be $MINIMUM_TEXT_SIZE_SP through $MAXIMUM_TEXT_SIZE_SP",
                    )
                }
            }
        }
    }

    fun isSupportedLocalFontFileName(fileName: String): Boolean {
        val extension = fileName.lowercase().substringAfterLast('.', missingDelimiterValue = "")
        return FONT_FILE_NAME_PATTERN.matches(fileName) && extension in FONT_FILE_EXTENSIONS
    }

    fun DikcizStyleDefaults.toJson(): JSONObject {
        return JSONObject()
            .put(WIDGET_DEFAULT_KEY, widget.toJson())
    }

    fun DikcizStyle.toJson(): JSONObject {
        return JSONObject().apply {
            background?.let { put(BACKGROUND_KEY, it.toJson()) }
            border?.let { put(BORDER_KEY, it.toJson()) }
            margin?.let { put(MARGIN_KEY, it.toJson()) }
            padding?.let { put(PADDING_KEY, it.toJson()) }
            text?.let { put(TEXT_KEY, it.toJson()) }
        }
    }

    private fun JSONObject.parseOptionalStyle(key: String, context: String): DikcizStyle? {
        if (!has(key)) {
            return null
        }
        val styleContext = "$context.$key"
        val style = requireStyleObject(key, context)
        style.requireOnlyStyleKeys(STYLE_KEYS, styleContext)
        return DikcizStyle(
            background = style.parseOptionalBackground(styleContext),
            border = style.parseOptionalBorder(styleContext),
            margin = style.parseOptionalBoxEdges(MARGIN_KEY, styleContext),
            padding = style.parseOptionalBoxEdges(PADDING_KEY, styleContext),
            text = style.parseOptionalTextStyle(styleContext),
        )
    }

    private fun JSONObject.parseOptionalBackground(context: String): DikcizBackgroundStyle? {
        if (!has(BACKGROUND_KEY)) {
            return null
        }
        val backgroundContext = "$context.$BACKGROUND_KEY"
        val background = requireStyleObject(BACKGROUND_KEY, context)
        background.requireOnlyStyleKeys(BACKGROUND_KEYS, backgroundContext)
        return DikcizBackgroundStyle(
            color = background.optionalColor(COLOR_KEY, backgroundContext),
            opacity = background.optionalOpacity(backgroundContext),
        )
    }

    private fun JSONObject.parseOptionalBorder(context: String): DikcizBorderStyle? {
        if (!has(BORDER_KEY)) {
            return null
        }
        val borderContext = "$context.$BORDER_KEY"
        val border = requireStyleObject(BORDER_KEY, context)
        border.requireOnlyStyleKeys(BORDER_KEYS, borderContext)
        return DikcizBorderStyle(
            color = border.optionalColor(COLOR_KEY, borderContext),
            radiusDP = border.optionalStyleDimension(BORDER_RADIUS_KEY, borderContext),
            widths = border.parseOptionalBoxEdges(BORDER_WIDTHS_KEY, borderContext),
        )
    }

    private fun JSONObject.parseOptionalBoxEdges(
        key: String,
        context: String,
    ): DikcizBoxEdges? {
        if (!has(key)) {
            return null
        }
        val edgesContext = "$context.$key"
        val edges = requireStyleObject(key, context)
        edges.requireOnlyStyleKeys(BOX_EDGE_KEYS, edgesContext)
        return DikcizBoxEdges(
            all = edges.optionalStyleDimension(ALL_KEY, edgesContext),
            bottom = edges.optionalStyleDimension(SIDE_BOTTOM_KEY, edgesContext),
            left = edges.optionalStyleDimension(SIDE_LEFT_KEY, edgesContext),
            right = edges.optionalStyleDimension(SIDE_RIGHT_KEY, edgesContext),
            top = edges.optionalStyleDimension(SIDE_TOP_KEY, edgesContext),
        )
    }

    private fun JSONObject.parseOptionalTextStyle(context: String): DikcizTextStyle? {
        if (!has(TEXT_KEY)) {
            return null
        }
        val textContext = "$context.$TEXT_KEY"
        val text = requireStyleObject(TEXT_KEY, context)
        text.requireOnlyStyleKeys(TEXT_KEYS, textContext)
        return DikcizTextStyle(
            color = text.optionalColor(COLOR_KEY, textContext),
            font = text.parseOptionalFont(textContext),
            sizeSP = text.optionalTextSizeSP(textContext),
        )
    }

    private fun JSONObject.parseOptionalFont(context: String): DikcizFontSelection? {
        if (!has(FONT_KEY)) {
            return null
        }
        val fontContext = "$context.$FONT_KEY"
        val font = requireStyleObject(FONT_KEY, context)
        font.requireOnlyStyleKeys(FONT_KEYS, fontContext)
        val sourceValue = font.requireStyleString(FONT_SOURCE_KEY, fontContext, MAXIMUM_FONT_ID_LENGTH)
        val source = DikcizFontSource.fromPersistedValue(sourceValue)
            ?: throw HomeConfigException("$fontContext.$FONT_SOURCE_KEY has an unknown source")
        val id = font.requireStyleString(FONT_ID_KEY, fontContext, MAXIMUM_FONT_FILE_NAME_LENGTH)
        val selection = DikcizFontSelection(id, source)
        validateFont(selection, fontContext)
        return selection
    }

    private fun JSONObject.optionalColor(key: String, context: String): String? {
        if (!has(key)) {
            return null
        }
        val color = requireStyleString(key, context, MAXIMUM_COLOR_LENGTH)
        validateColor(color, "$context.$key")
        return color
    }

    private fun JSONObject.optionalOpacity(context: String): Double? {
        if (!has(OPACITY_KEY)) {
            return null
        }
        val value = get(OPACITY_KEY)
        if (value !is Number || !value.toDouble().isFinite()) {
            throw HomeConfigException("$context.$OPACITY_KEY must be a finite number")
        }
        val opacity = value.toDouble()
        if (opacity !in MINIMUM_OPACITY..MAXIMUM_OPACITY) {
            throw HomeConfigException("$context.$OPACITY_KEY must be 0 through 1")
        }
        return opacity
    }

    private fun JSONObject.optionalStyleDimension(key: String, context: String): Int? {
        if (!has(key)) {
            return null
        }
        val value = get(key)
        if (value !is Number || value.toLong().toDouble() != value.toDouble()) {
            throw HomeConfigException("$context.$key must be an integer")
        }
        return validateStyleDimension(value.toLong(), "$context.$key")
    }

    private fun JSONObject.optionalTextSizeSP(context: String): Int? {
        if (!has(TEXT_SIZE_SP_KEY)) {
            return null
        }
        val value = get(TEXT_SIZE_SP_KEY)
        if (value !is Number || value.toLong().toDouble() != value.toDouble()) {
            throw HomeConfigException("$context.$TEXT_SIZE_SP_KEY must be an integer")
        }
        val sizeSP = value.toLong()
        if (sizeSP !in MINIMUM_TEXT_SIZE_SP.toLong()..MAXIMUM_TEXT_SIZE_SP.toLong()) {
            throw HomeConfigException(
                "$context.$TEXT_SIZE_SP_KEY must be $MINIMUM_TEXT_SIZE_SP through $MAXIMUM_TEXT_SIZE_SP",
            )
        }
        return sizeSP.toInt()
    }

    private fun validateColor(color: String, context: String) {
        if (!COLOR_PATTERN.matches(color)) {
            throw HomeConfigException("$context must be #RRGGBB or #RRGGBBAA")
        }
    }

    private fun validateStyleDimension(value: Int, context: String) {
        validateStyleDimension(value.toLong(), context)
    }

    private fun validateStyleDimension(value: Long, context: String): Int {
        if (value !in MINIMUM_STYLE_DIMENSION_DP.toLong()..MAXIMUM_STYLE_DIMENSION_DP.toLong()) {
            throw HomeConfigException(
                "$context must be $MINIMUM_STYLE_DIMENSION_DP through $MAXIMUM_STYLE_DIMENSION_DP",
            )
        }
        return value.toInt()
    }

    private fun validateBoxEdges(edges: DikcizBoxEdges, context: String) {
        listOf(
            ALL_KEY to edges.all,
            SIDE_BOTTOM_KEY to edges.bottom,
            SIDE_LEFT_KEY to edges.left,
            SIDE_RIGHT_KEY to edges.right,
            SIDE_TOP_KEY to edges.top,
        ).forEach { (name, value) ->
            if (value == null) {
                return@forEach
            }
            validateStyleDimension(value, "$context.$name")
        }
    }

    private fun validateCompleteStyle(style: DikcizStyle, context: String) {
        validate(style, context)
        val background = style.background
            ?: throw HomeConfigException("$context.background is required in a theme")
        if (background.color == null || background.opacity == null) {
            throw HomeConfigException("$context.background must set color and opacity in a theme")
        }
        val border = style.border
            ?: throw HomeConfigException("$context.border is required in a theme")
        if (border.color == null || border.radiusDP == null || !border.widths.hasCompleteValues()) {
            throw HomeConfigException("$context.border must set color, radius, and widths in a theme")
        }
        if (!style.margin.hasCompleteValues()) {
            throw HomeConfigException("$context.margin must set every side in a theme")
        }
        if (!style.padding.hasCompleteValues()) {
            throw HomeConfigException("$context.padding must set every side in a theme")
        }
        val text = style.text
            ?: throw HomeConfigException("$context.text is required in a theme")
        if (text.color == null || text.font == null || text.sizeSP == null) {
            throw HomeConfigException("$context.text must set color, font, and sizeSp in a theme")
        }
    }

    private fun DikcizBoxEdges?.hasCompleteValues(): Boolean {
        val edges = this ?: return false
        return edges.all != null ||
            listOf(edges.bottom, edges.left, edges.right, edges.top).all { value -> value != null }
    }

    private fun validateFont(font: DikcizFontSelection, context: String) {
        when (font.source) {
            DikcizFontSource.Bundled -> {
                if (DikcizBundledFont.fromPersistedValue(font.id) == null) {
                    throw HomeConfigException("$context.$FONT_ID_KEY has an unknown bundled font")
                }
            }

            DikcizFontSource.Local -> {
                if (!isSupportedLocalFontFileName(font.id)) {
                    throw HomeConfigException(
                        "$context.$FONT_ID_KEY must name a TTF, OTF, or TTC file in $FONT_DIRECTORY_NAME",
                    )
                }
            }

            DikcizFontSource.System -> {
                if (DikcizSystemFont.fromPersistedValue(font.id) == null) {
                    throw HomeConfigException("$context.$FONT_ID_KEY has an unknown system font")
                }
            }
        }
    }

    private fun DikcizBackgroundStyle.toJson(): JSONObject {
        return JSONObject().apply {
            color?.let { put(COLOR_KEY, it) }
            opacity?.let { put(OPACITY_KEY, it) }
        }
    }

    private fun DikcizBorderStyle.toJson(): JSONObject {
        return JSONObject().apply {
            color?.let { put(COLOR_KEY, it) }
            radiusDP?.let { put(BORDER_RADIUS_KEY, it) }
            widths?.let { put(BORDER_WIDTHS_KEY, it.toJson()) }
        }
    }

    private fun DikcizBoxEdges.toJson(): JSONObject {
        return JSONObject().apply {
            all?.let { put(ALL_KEY, it) }
            bottom?.let { put(SIDE_BOTTOM_KEY, it) }
            left?.let { put(SIDE_LEFT_KEY, it) }
            right?.let { put(SIDE_RIGHT_KEY, it) }
            top?.let { put(SIDE_TOP_KEY, it) }
        }
    }

    private fun DikcizTextStyle.toJson(): JSONObject {
        return JSONObject().apply {
            color?.let { put(COLOR_KEY, it) }
            font?.let { put(FONT_KEY, it.toJson()) }
            sizeSP?.let { put(TEXT_SIZE_SP_KEY, it) }
        }
    }

    private fun DikcizFontSelection.toJson(): JSONObject {
        return JSONObject()
            .put(FONT_ID_KEY, id)
            .put(FONT_SOURCE_KEY, source.persistedValue)
    }

    private fun JSONObject.requireOnlyStyleKeys(allowedKeys: Set<String>, context: String) {
        val iterator = keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (key !in allowedKeys) {
                throw HomeConfigException("$context has an unknown key: $key")
            }
        }
    }

    private fun JSONObject.requireRequiredStyleKeys(allowedKeys: Set<String>, context: String) {
        requireOnlyStyleKeys(allowedKeys, context)
        allowedKeys.forEach { key ->
            if (!has(key) || isNull(key)) {
                throw HomeConfigException("$context.$key is required in a theme")
            }
        }
    }

    private fun JSONObject.requireStyleObject(key: String, context: String): JSONObject {
        if (!has(key) || isNull(key)) {
            throw HomeConfigException("$context.$key is required")
        }
        val value = get(key)
        if (value !is JSONObject) {
            throw HomeConfigException("$context.$key must be an object")
        }
        return value
    }

    private fun JSONObject.requireStyleString(key: String, context: String, maximumLength: Int): String {
        if (!has(key) || isNull(key)) {
            throw HomeConfigException("$context.$key is required")
        }
        val value = get(key)
        if (value !is String || value.length > maximumLength || value.any { it == NUL_CHARACTER }) {
            throw HomeConfigException("$context.$key has an invalid length or character")
        }
        return value
    }

    private const val NUL_CHARACTER = '\u0000'
    const val THEME_WIDGET_STYLE_KEY = "widgetStyle"
    private val FONT_FILE_EXTENSIONS = setOf("otf", "ttc", "ttf")
}
