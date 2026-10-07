package org.fossify.home.dikciz

import org.json.JSONObject

internal data class HomeConfiguration(
    val version: Int,
    val limits: DikcizLimits,
    val logging: DikcizLoggingConfiguration,
    val rootNamespaces: JSONObject,
    val launcherNamespaces: JSONObject,
    val launcherBackground: DikcizLauncherBackground? = null,
    val homePageID: String,
    val selectedPageID: String,
    val selectedThemeID: String? = null,
    val nativeGrid: DikcizNativeGrid = DikcizNativeGrid.BUNDLED_DEFAULT,
    val styleDefaults: DikcizStyleDefaults = DikcizStyleDefaults(),
    val pages: List<HomePage>,
    val scripts: List<DikcizLuaScript> = emptyList(),
    val automation: DikcizAutomationConfiguration = DikcizAutomationConfiguration(),
) {
    fun requiresBackgroundAutomationService(): Boolean {
        return automation.scripts.any { automationScript ->
            automationScript.enabled &&
                automation.policy(automationScript.policyID)?.enabled == true &&
                scripts.any { script ->
                    script.id == automationScript.scriptID && script.enabled
                } &&
                automationScript.subscriptions.any { subscription ->
                    subscription.event != DikcizAutomationEventType.NotificationPosted &&
                        subscription.event != DikcizAutomationEventType.NotificationRemoved &&
                        subscription.event != DikcizAutomationEventType.MediaSession &&
                        subscription.event != DikcizAutomationEventType.SmsReceived &&
                        subscription.event != DikcizAutomationEventType.DeviceAdminState &&
                        subscription.event != DikcizAutomationEventType.ClipboardChanged &&
                        subscription.event != DikcizAutomationEventType.WidgetChanged &&
                        subscription.event != DikcizAutomationEventType.Custom &&
                        subscription.event != DikcizAutomationEventType.Manual
                }
        } || pages.any { page ->
            page.widgets.filterIsInstance<HtmlHomeWidget>().any { widget ->
                widget.enabled && widget.eventSubscriptions.any { subscription ->
                    subscription.event != DikcizAutomationEventType.Custom
                }
            }
        }
    }
}

internal data class DikcizLoggingConfiguration(
    val level: DikcizLogLevel,
    val retentionDays: Int,
    val maxTotalBytes: Int,
    val notifyOnScriptError: Boolean = DEFAULT_NOTIFY_ON_SCRIPT_ERROR,
    val scriptErrorNotificationMinimumIntervalMilliseconds: Int =
        DEFAULT_SCRIPT_ERROR_NOTIFICATION_MINIMUM_INTERVAL_MILLISECONDS,
) {
    companion object {
        const val DEFAULT_MAXIMUM_TOTAL_BYTES = 16 * 1024 * 1024
        const val DEFAULT_NOTIFY_ON_SCRIPT_ERROR = true
        const val DEFAULT_RETENTION_DAYS = 7
        const val DEFAULT_SCRIPT_ERROR_NOTIFICATION_MINIMUM_INTERVAL_MILLISECONDS = 60_000
    }
}

internal enum class DikcizLogLevel(
    val persistedValue: String,
    val priority: Int,
) {
    Debug("debug", 0),
    Info("info", 1),
    Warn("warn", 2),
    Error("error", 3),
    ;

    companion object {
        fun fromPersistedValue(value: String): DikcizLogLevel? {
            return entries.firstOrNull { it.persistedValue == value }
        }
    }
}

/**
 * Whether the saved public home still is the starter that the installed build packages.
 * `isPristine` stays true only while nothing has saved over the seeded tree.
 */
internal data class DikcizBundledStarterState(
    val packagedDigest: String,
    val seededDigest: String?,
    val isPristine: Boolean,
) {
    val matchesPackagedStarter: Boolean
        get() = seededDigest == packagedDigest
}

internal data class DikcizLimits(
    val maxConfigBytes: Int,
    val maxComponentCharacters: Int,
    val maxHtmlWidgetDocumentBytes: Int,
    val maxHtmlWidgetsPerPage: Int,
    val maxIdentifierCharacters: Int,
    val maxPages: Int,
    val maxTextCharacters: Int,
    val maxTitleCharacters: Int,
    val maxWidgetsPerPage: Int,
)

internal object HtmlWidgetResourceLimits {
    const val HARD_MAXIMUM_DOCUMENT_BYTES = 262_144
    const val HARD_MAXIMUM_RENDERERS_PER_PAGE = 32
    const val MINIMUM_DOCUMENT_BYTES = 1_024
    const val MINIMUM_RENDERERS_PER_PAGE = 0
}

internal data class HomePage(
    val id: String,
    val title: String,
    val position: PagePosition,
    val widgets: List<HomeWidget>,
    val locked: Boolean = false,
)

internal object DikcizLuaScriptApi {
    const val CURRENT_VERSION = 1
}

internal data class DikcizLuaScript(
    val id: String,
    val title: String,
    val enabled: Boolean,
    val source: String,
    val apiVersion: Int = DikcizLuaScriptApi.CURRENT_VERSION,
    val state: JSONObject = JSONObject(),
) {
    val serializedState: String = state.toString()
}

internal data class PagePosition(
    val column: Int,
    val row: Int,
)

internal sealed interface HomeWidget {
    val id: String
    val title: String
    val enabled: Boolean

    /** The logical cells this item owns on its page. Rendered pixels derive from it. */
    val cell: DikcizGridRectangle
    val style: DikcizStyle?
    val locked: Boolean
}

/**
 * The comfortable default extent each item type asks for. The placement engine decides
 * where the item actually lands and may narrow the span on a crowded page.
 */
internal object DikcizWidgetSpans {
    val APP = DikcizGridSpan(columnSpan = 1, rowSpan = 1)
    val APP_GROUP = DikcizGridSpan(columnSpan = 2, rowSpan = 1)
    val HTML = DikcizGridSpan(columnSpan = 2, rowSpan = 2)
    val PROVIDER = DikcizGridSpan(columnSpan = 2, rowSpan = 2)
    val SCRIPT_DASHBOARD = DikcizGridSpan(columnSpan = 4, rowSpan = 3)
}

internal data class HtmlHomeWidget(
    override val id: String,
    override val title: String,
    val html: String,
    val css: String = "",
    val javascript: String = "",
    val state: JSONObject = JSONObject(),
    val heightMode: HtmlWidgetHeightMode = HtmlWidgetHeightMode.Fixed,
    val eventSubscriptions: List<DikcizAutomationSubscription> = emptyList(),
    override val enabled: Boolean,
    override val cell: DikcizGridRectangle,
    override val style: DikcizStyle? = null,
    override val locked: Boolean = false,
) : HomeWidget {
    val serializedState: String = state.toString()
}

internal enum class HtmlWidgetHeightMode(
    val persistedValue: String,
) {
    Fixed("fixed"),
    Content("content"),
    ;

    companion object {
        fun fromPersistedValue(value: String): HtmlWidgetHeightMode? {
            return entries.firstOrNull { it.persistedValue == value }
        }
    }
}

internal data class AppHomeWidget(
    override val id: String,
    override val title: String,
    val component: String,
    val displayStyle: AppWidgetDisplayStyle = AppWidgetDisplayStyle.IconWithLabel,
    override val enabled: Boolean,
    override val cell: DikcizGridRectangle,
    override val style: DikcizStyle? = null,
    override val locked: Boolean = false,
) : HomeWidget

internal data class AppGroupHomeWidget(
    override val id: String,
    override val title: String,
    val components: List<String>,
    override val enabled: Boolean,
    override val cell: DikcizGridRectangle,
    override val style: DikcizStyle? = null,
    override val locked: Boolean = false,
) : HomeWidget

internal object AppGroupLimits {
    const val MAXIMUM_COMPONENTS = 32
    const val PREVIEW_COMPONENTS = 4
}

internal enum class AppWidgetDisplayStyle(
    val persistedValue: String,
) {
    IconWithLabel("iconLabel"),
    TextButton("button"),
    IconAndLabelButton("buttonIconLabel"),
    ;

    companion object {
        fun fromPersistedValue(value: String): AppWidgetDisplayStyle? {
            return entries.firstOrNull { it.persistedValue == value }
        }
    }
}

internal data class ProviderHomeWidget(
    override val id: String,
    override val title: String,
    val provider: String,
    val appWidgetID: Int,
    override val enabled: Boolean,
    override val cell: DikcizGridRectangle,
    override val style: DikcizStyle? = null,
    override val locked: Boolean = false,
) : HomeWidget

internal data class ScriptDashboardHomeWidget(
    override val id: String,
    override val title: String,
    override val enabled: Boolean,
    override val cell: DikcizGridRectangle,
    override val style: DikcizStyle? = null,
    override val locked: Boolean = false,
) : HomeWidget

internal data class DikcizStyleDefaults(
    val widget: DikcizStyle = DikcizStyle(),
)

internal data class DikcizLauncherBackground(
    val color: String = DEFAULT_LAUNCHER_BACKGROUND_COLOR,
    val wallpaper: String? = null,
) {
    companion object {
        const val DEFAULT_LAUNCHER_BACKGROUND_COLOR = "#101318"
    }
}

internal data class DikcizStyle(
    val background: DikcizBackgroundStyle? = null,
    val border: DikcizBorderStyle? = null,
    val margin: DikcizBoxEdges? = null,
    val padding: DikcizBoxEdges? = null,
    val text: DikcizTextStyle? = null,
) {
    fun mergedWith(override: DikcizStyle?): DikcizStyle {
        if (override == null) {
            return this
        }
        return DikcizStyle(
            background = background.mergedWith(override.background),
            border = border.mergedWith(override.border),
            margin = margin.mergedWith(override.margin),
            padding = padding.mergedWith(override.padding),
            text = text.mergedWith(override.text),
        )
    }
}

internal data class DikcizBackgroundStyle(
    val color: String? = null,
    val opacity: Double? = null,
)

internal data class DikcizBorderStyle(
    val color: String? = null,
    val radiusDP: Int? = null,
    val widths: DikcizBoxEdges? = null,
)

internal data class DikcizBoxEdges(
    val all: Int? = null,
    val bottom: Int? = null,
    val left: Int? = null,
    val right: Int? = null,
    val top: Int? = null,
) {
    fun resolveBottom(defaultValue: Int): Int = bottom ?: all ?: defaultValue

    fun resolveLeft(defaultValue: Int): Int = left ?: all ?: defaultValue

    fun resolveRight(defaultValue: Int): Int = right ?: all ?: defaultValue

    fun resolveTop(defaultValue: Int): Int = top ?: all ?: defaultValue
}

internal data class DikcizTextStyle(
    val color: String? = null,
    val font: DikcizFontSelection? = null,
    val sizeSP: Int? = null,
)

internal data class DikcizFontSelection(
    val id: String,
    val source: DikcizFontSource,
)

internal enum class DikcizBundledFont(
    val persistedValue: String,
    val assetFileName: String,
) {
    Inter("inter", "inter.ttf"),
    JetBrainsMono("jetbrains-mono", "jetbrains-mono.ttf"),
    SpaceGrotesk("space-grotesk", "space-grotesk.ttf"),
    ComicNeue("comic-neue", "comic-neue.ttf"),
    ;

    companion object {
        fun fromPersistedValue(value: String): DikcizBundledFont? {
            return entries.firstOrNull { it.persistedValue == value }
        }
    }
}

internal enum class DikcizSystemFont(
    val persistedValue: String,
) {
    Default("default"),
    SansSerif("sans-serif"),
    Serif("serif"),
    Monospace("monospace"),
    Cursive("cursive"),
    ;

    companion object {
        fun fromPersistedValue(value: String): DikcizSystemFont? {
            return entries.firstOrNull { it.persistedValue == value }
        }
    }
}

internal enum class DikcizFontSource(
    val persistedValue: String,
) {
    Bundled("bundled"),
    Local("local"),
    System("system"),
    ;

    companion object {
        fun fromPersistedValue(value: String): DikcizFontSource? {
            return entries.firstOrNull { it.persistedValue == value }
        }
    }
}

private fun DikcizBackgroundStyle?.mergedWith(
    override: DikcizBackgroundStyle?,
): DikcizBackgroundStyle? {
    if (this == null) {
        return override
    }
    if (override == null) {
        return this
    }
    return DikcizBackgroundStyle(
        color = override.color ?: color,
        opacity = override.opacity ?: opacity,
    )
}

private fun DikcizBorderStyle?.mergedWith(
    override: DikcizBorderStyle?,
): DikcizBorderStyle? {
    if (this == null) {
        return override
    }
    if (override == null) {
        return this
    }
    return DikcizBorderStyle(
        color = override.color ?: color,
        radiusDP = override.radiusDP ?: radiusDP,
        widths = widths.mergedWith(override.widths),
    )
}

private fun DikcizBoxEdges?.mergedWith(override: DikcizBoxEdges?): DikcizBoxEdges? {
    if (this == null) {
        return override
    }
    if (override == null) {
        return this
    }
    return DikcizBoxEdges(
        all = override.all ?: all,
        bottom = override.bottom ?: bottom,
        left = override.left ?: left,
        right = override.right ?: right,
        top = override.top ?: top,
    )
}

private fun DikcizTextStyle?.mergedWith(override: DikcizTextStyle?): DikcizTextStyle? {
    if (this == null) {
        return override
    }
    if (override == null) {
        return this
    }
    return DikcizTextStyle(
        color = override.color ?: color,
        font = override.font ?: font,
        sizeSP = override.sizeSP ?: sizeSP,
    )
}
