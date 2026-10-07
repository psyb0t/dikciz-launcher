package org.fossify.home.dikciz

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.RemoteViews
import org.fossify.home.R

internal data class ProviderAutomationView(
    val view: View,
    val hostView: AppWidgetHostView,
) {
    fun isCurrent(): Boolean {
        if (!view.isAttachedToWindow || !hostView.isAttachedToWindow) {
            return false
        }
        var ancestor: View? = view
        while (ancestor != null) {
            if (ancestor === hostView) {
                return true
            }
            ancestor = ancestor.parent as? View
        }
        return false
    }
}

internal class DikcizProviderWidgetHostView(
    context: Context,
    private val nextAutomationRenderToken: () -> Long,
) : AppWidgetHostView(context) {
    var automationRenderToken = nextAutomationRenderToken()
        private set

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)
        automationRenderToken = nextAutomationRenderToken()
    }
}

internal data class PageInsertionTarget(
    val axis: PageNavigationAxis,
    val side: PageInsertionSide,
)

internal data class ActiveWidgetResize(
    val widgetID: String,
    val widgetView: View,
    val isWidgetEnabled: Boolean,
    val resizeHandles: Set<WidgetResizeHandle>,
    val initialCell: DikcizGridRectangle,
    var targetCell: DikcizGridRectangle,
    var hasChanged: Boolean = false,
)

internal data class PendingWidgetMove(
    val widget: HomeWidget,
    val widgetView: View,
    val initialRawX: Float,
    val initialRawY: Float,
    val longPressRunnable: Runnable,
    /**
     * What a press that neither dragged nor became a long press should do.
     *
     * The gesture is resolved in the activity's own touch dispatch, which
     * answers the release before the view under the finger sees it, so the tap
     * has to be run from here rather than from that view's click listener.
     */
    val onTap: (() -> Unit)? = null,
)

internal data class ActiveWidgetMove(
    val widgetID: String,
    val widgetView: View,
    val initialCell: DikcizGridRectangle,
    val initialBounds: GridWidgetBounds,
    val initialRawX: Float,
    val initialRawY: Float,
    val initialTranslationX: Float,
    val initialTranslationY: Float,
    var lastRawX: Float,
    var lastRawY: Float,
    var targetCell: DikcizGridRectangle,
    var targetFailure: DikcizGridFailure? = null,
    /**
     * The widget the drag is currently offering to trade places with.
     *
     * Set while the pointer sits over a widget this one can swap with, so the
     * page can show the trade before the finger lifts, and cleared as soon as
     * the pointer leaves that widget so the other tile springs back.
     */
    var swapPartnerID: String? = null,
    /**
     * The widget the drag is currently offering to merge into.
     *
     * Set while the pointer sits on a tile this one would join as an app group,
     * so the target can show that it is about to absorb the drag instead of
     * showing the refusal a plain collision shows.
     */
    var combineTargetID: String? = null,
)

/**
 * The four editable page-grid fields. Each knows how to read its current value and how to
 * fold an edited value back into a grid, so the settings form stays one loop.
 */
internal enum class DikcizGridSetting(
    val persistedValue: String,
    val labelResourceID: Int,
    val minimum: Int,
    val maximum: Int,
) {
    Columns(
        "columns",
        R.string.dikciz_settings_grid_columns,
        DikcizNativeGrid.MINIMUM_COLUMNS,
        DikcizNativeGrid.MAXIMUM_COLUMNS,
    ),
    Rows(
        "rows",
        R.string.dikciz_settings_grid_rows,
        DikcizNativeGrid.MINIMUM_ROWS,
        DikcizNativeGrid.MAXIMUM_ROWS,
    ),
    GapDP(
        "gapDp",
        R.string.dikciz_settings_grid_gap,
        DikcizNativeGrid.MINIMUM_GAP_DP,
        DikcizNativeGrid.MAXIMUM_GAP_DP,
    ),
    OuterPaddingDP(
        "outerPaddingDp",
        R.string.dikciz_settings_grid_outer_padding,
        DikcizNativeGrid.MINIMUM_OUTER_PADDING_DP,
        DikcizNativeGrid.MAXIMUM_OUTER_PADDING_DP,
    ),
    ;

    fun read(grid: DikcizNativeGrid): Int {
        return when (this) {
            Columns -> grid.columns
            Rows -> grid.rows
            GapDP -> grid.gapDP
            OuterPaddingDP -> grid.outerPaddingDP
        }
    }

    fun write(grid: DikcizNativeGrid, value: Int): DikcizNativeGrid {
        return when (this) {
            Columns -> grid.copy(columns = value)
            Rows -> grid.copy(rows = value)
            GapDP -> grid.copy(gapDP = value)
            OuterPaddingDP -> grid.copy(outerPaddingDP = value)
        }
    }

    companion object {
        fun fromPersistedValue(value: String): DikcizGridSetting? {
            return entries.firstOrNull { it.persistedValue == value }
        }
    }
}

internal data class GridWidgetBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

internal enum class PageNavigationAxis(
    val persistedValue: String,
    val indicatorAxis: PageIndicatorAxis,
) {
    Horizontal("horizontal", PageIndicatorAxis.Horizontal),
    Vertical("vertical", PageIndicatorAxis.Vertical),
    ;
}

internal enum class AppearanceDefaultScope(
    val semanticID: String,
    val titleResourceID: Int,
) {
    Widget("widget", R.string.dikciz_appearance_widget_default_title),
    ;
}

internal enum class PageMenuSection(
    val labelResourceID: Int,
) {
    Add(R.string.dikciz_page_menu_section_add),
    Page(R.string.dikciz_page_menu_section_page),
    Arrange(R.string.dikciz_page_menu_section_arrange),
    Launcher(R.string.dikciz_page_menu_section_launcher),
    ;
}

/**
 * One step along a page rail.
 *
 * A move swaps this page with the single page that already sits at the neighbouring
 * coordinate. It never shifts a rail and never creates a coordinate, so a direction with
 * no existing neighbour simply has nothing to swap with.
 */
internal enum class PageMoveDirection(
    val persistedValue: String,
    val columnOffset: Int,
    val rowOffset: Int,
) {
    Left("left", -1, 0),
    Right("right", 1, 0),
    Up("up", 0, -1),
    Down("down", 0, 1),
    ;
}

internal enum class PageMenuAction(
    val semanticAction: String,
    val labelResourceID: Int,
    val section: PageMenuSection,
    val moveDirection: PageMoveDirection? = null,
) {
    Widgets("widgets", R.string.dikciz_page_menu_widgets_verb, PageMenuSection.Add),
    Commands("commands", R.string.dikciz_page_menu_commands_verb, PageMenuSection.Add),
    HtmlWidgets("html-widgets", R.string.dikciz_page_menu_html_widgets_verb, PageMenuSection.Page),
    SetHome("set-home", R.string.dikciz_page_menu_set_home_verb, PageMenuSection.Page),
    Rename("rename", R.string.dikciz_page_menu_rename_verb, PageMenuSection.Page),
    Lock("lock", R.string.dikciz_page_lock_verb, PageMenuSection.Page),
    Unlock("unlock", R.string.dikciz_page_unlock_verb, PageMenuSection.Page),
    WidgetLocks("widget-locks", R.string.dikciz_page_menu_widget_locks_verb, PageMenuSection.Page),
    Delete("delete", R.string.dikciz_page_menu_delete_verb, PageMenuSection.Page),
    MoveLeft(
        "move-left",
        R.string.dikciz_page_menu_move_left_verb,
        PageMenuSection.Arrange,
        PageMoveDirection.Left,
    ),
    MoveRight(
        "move-right",
        R.string.dikciz_page_menu_move_right_verb,
        PageMenuSection.Arrange,
        PageMoveDirection.Right,
    ),
    MoveUp(
        "move-up",
        R.string.dikciz_page_menu_move_up_verb,
        PageMenuSection.Arrange,
        PageMoveDirection.Up,
    ),
    MoveDown(
        "move-down",
        R.string.dikciz_page_menu_move_down_verb,
        PageMenuSection.Arrange,
        PageMoveDirection.Down,
    ),
    Settings("settings", R.string.dikciz_page_menu_settings_verb, PageMenuSection.Launcher),
    ;
}

internal data class ActiveWidgetEdit(
    val widgetID: String,
    val widgetView: View,
    val actionsView: LinearLayout,
    val actionsPopup: PopupWindow,
)

internal data class WidgetResizeGesture(
    val handle: WidgetResizeHandle,
    val initialRawX: Float,
    val initialRawY: Float,
)

internal enum class ResizeDirection(
    val multiplier: Int,
) {
    Negative(-1),
    None(0),
    Positive(1),
    ;
}

internal enum class ResizeHandlePosition(
    val fraction: Float,
) {
    Start(0F),
    Center(0.5F),
    End(1F),
    ;

    fun coordinate(sizePixels: Int, pointRadiusPixels: Float): Float {
        return when (this) {
            Start -> pointRadiusPixels
            Center -> sizePixels * fraction
            End -> sizePixels - pointRadiusPixels
        }
    }
}

internal enum class WidgetResizeHandle(
    val semanticSuffix: String,
    val horizontalPosition: ResizeHandlePosition,
    val verticalPosition: ResizeHandlePosition,
    val horizontalDirection: ResizeDirection,
    val verticalDirection: ResizeDirection,
) {
    Top(
        "top",
        ResizeHandlePosition.Center,
        ResizeHandlePosition.Start,
        ResizeDirection.None,
        ResizeDirection.Negative,
    ),
    Bottom(
        "bottom",
        ResizeHandlePosition.Center,
        ResizeHandlePosition.End,
        ResizeDirection.None,
        ResizeDirection.Positive,
    ),
    Left(
        "left",
        ResizeHandlePosition.Start,
        ResizeHandlePosition.Center,
        ResizeDirection.Negative,
        ResizeDirection.None,
    ),
    Right(
        "right",
        ResizeHandlePosition.End,
        ResizeHandlePosition.Center,
        ResizeDirection.Positive,
        ResizeDirection.None,
    ),
    TopLeft(
        "top_left",
        ResizeHandlePosition.Start,
        ResizeHandlePosition.Start,
        ResizeDirection.Negative,
        ResizeDirection.Negative,
    ),
    TopRight(
        "top_right",
        ResizeHandlePosition.End,
        ResizeHandlePosition.Start,
        ResizeDirection.Positive,
        ResizeDirection.Negative,
    ),
    BottomLeft(
        "bottom_left",
        ResizeHandlePosition.Start,
        ResizeHandlePosition.End,
        ResizeDirection.Negative,
        ResizeDirection.Positive,
    ),
    BottomRight(
        "bottom_right",
        ResizeHandlePosition.End,
        ResizeHandlePosition.End,
        ResizeDirection.Positive,
        ResizeDirection.Positive,
    ),
    ;
}

internal class WidgetResizeSelectionDrawable(
    private val cornerRadiusPixels: Float,
    private val outlineWidthPixels: Float,
    private val pointRadiusPixels: Float,
    private val pointColor: Int,
    private val rejectedColor: Int,
    private val resizeHandles: Set<WidgetResizeHandle>,
) : Drawable() {
    /** Turns the outline to the rejection colour while a candidate cell is illegal. */
    var isRejected: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            val color = if (value) rejectedColor else pointColor
            pointPaint.color = color
            outlinePaint.color = color
            invalidateSelf()
        }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pointColor }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pointColor
        style = Paint.Style.STROKE
        strokeWidth = outlineWidthPixels
    }

    override fun draw(canvas: Canvas) {
        val outlineInset = outlineWidthPixels / 2F
        val outlineBounds = RectF(bounds).apply {
            inset(outlineInset, outlineInset)
        }
        val outlineCornerRadius = (cornerRadiusPixels - outlineInset).coerceAtLeast(0F)
        canvas.drawRoundRect(
            outlineBounds,
            outlineCornerRadius,
            outlineCornerRadius,
            outlinePaint,
        )
        resizeHandles.forEach { handle ->
            val x = bounds.left + handle.horizontalPosition.coordinate(
                bounds.width(),
                pointRadiusPixels,
            )
            val y = bounds.top + handle.verticalPosition.coordinate(
                bounds.height(),
                pointRadiusPixels,
            )
            canvas.drawCircle(x, y, pointRadiusPixels, pointPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        pointPaint.alpha = alpha
        outlinePaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        pointPaint.colorFilter = colorFilter
        outlinePaint.colorFilter = colorFilter
    }

    @Deprecated("Android Drawable API")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

internal data class PendingProviderWidget(
    val target: WidgetInsertionTarget,
    val providerInfo: AppWidgetProviderInfo,
    val widget: ProviderHomeWidget,
)

internal data class WidgetInsertionTarget(
    val pageID: String,
    val insertionIndex: Int,
)

internal data class WidgetPickerCategory(
    val automationID: String,
    val label: String,
    val icon: Drawable,
    val entries: List<WidgetPickerEntry>,
    val applications: List<WidgetPickerApplication> = emptyList(),
)

internal data class WidgetPickerApplication(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val providers: List<ProviderWidgetPickerEntry>,
) {
    val entries: List<WidgetPickerEntry>
        get() = providers
}

internal sealed interface WidgetPickerEntry {
    val label: String
    val automationID: String
}

internal data class NativeWidgetPickerEntry(
    val type: NativeWidgetType,
    override val label: String,
) : WidgetPickerEntry {
    override val automationID: String = "native:${type.automationID}"
}

internal enum class NativeWidgetType(val automationID: String) {
    Html("html"),
    ScriptDashboard("script-dashboard"),
}

internal data class ProviderWidgetPickerEntry(
    val providerInfo: AppWidgetProviderInfo,
    override val label: String,
    val preview: Drawable?,
) : WidgetPickerEntry {
    override val automationID: String = "provider:${providerInfo.provider.packageName}:" +
        providerInfo.provider.className
}

internal data class AppWidgetPickerEntry(
    val application: DikcizLaunchableApp,
) : WidgetPickerEntry {
    override val label: String = application.label
    override val automationID: String = "app:${application.component.packageName}:" +
        application.component.className
}
