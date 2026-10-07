package org.fossify.home.dikciz

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import org.fossify.home.R

internal enum class DikcizLauncherControlAction(
    val semanticAction: String,
    val labelResourceID: Int,
    val iconResourceID: Int,
) {
    AppDrawer(
        "app-drawer",
        R.string.dikciz_launcher_control_app_drawer,
        R.drawable.dikciz_icon_route_app_drawer,
    ),
    AddToPage(
        "add-to-page",
        R.string.dikciz_launcher_control_add_to_page,
        R.drawable.dikciz_icon_route_add,
    ),
    ManagePage(
        "manage-page",
        R.string.dikciz_launcher_control_manage_page,
        R.drawable.dikciz_icon_route_page,
    ),
    DikcizSettings(
        "dikciz-settings",
        R.string.dikciz_launcher_control_settings,
        R.drawable.dikciz_icon_route_settings,
    ),
    ;
}

/**
 * The always-visible launcher affordance. It holds the page-search pill and the launcher
 * controls pill in the bottom chrome row beside the horizontal page indicator instead of
 * over the page canvas, so it never covers a widget card.
 *
 * Both pills are icon only. The chrome row has room for a mark, not for a word, so each
 * pill names itself through its content description for the screen reader and for
 * automation. Page identity stays in the sheets the pills open, never in the chrome.
 */
internal class DikcizLauncherControlView @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null,
    defaultStyleAttribute: Int = 0,
) : LinearLayout(context, attributes, defaultStyleAttribute) {
    var onControlsRequested: (() -> Unit)? = null
    var onPageSearchRequested: (() -> Unit)? = null

    val pageSearchButton: Button
    val controlButton: Button

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        pageSearchButton = createPillButton(
            iconResourceID = R.drawable.dikciz_icon_search,
            descriptionResourceID = R.string.dikciz_page_search_open_description,
            // The shared glyph ships muted for the app drawer's search field, which
            // reads as disabled against the chrome pill.
            iconTintColorResourceID = R.color.dikciz_foreground,
        ) { onPageSearchRequested?.invoke() }
        controlButton = createPillButton(
            iconResourceID = R.drawable.dikciz_brand_mark,
            descriptionResourceID = R.string.dikciz_launcher_control_open_description,
        ) { onControlsRequested?.invoke() }
        addView(pageSearchButton, pillLayoutParameters())
        addView(
            controlButton,
            pillLayoutParameters().apply { marginStart = densityPixels(PILL_GAP_DP) },
        )
    }

    fun setControlsVisible(areControlsVisible: Boolean) {
        visibility = if (areControlsVisible) View.VISIBLE else View.GONE
    }

    private fun createPillButton(
        iconResourceID: Int,
        descriptionResourceID: Int,
        iconTintColorResourceID: Int = NO_TINT,
        onSelected: () -> Unit,
    ): Button {
        return Button(context).apply {
            background = context.getDrawable(R.drawable.dikciz_control_pill)
            gravity = Gravity.CENTER
            // A platform Button carries a 48dp minimum on both axes, which would push the
            // pill past the height the chrome row can spend on it.
            minHeight = densityPixels(CONTROL_HEIGHT_DP)
            minimumHeight = densityPixels(CONTROL_HEIGHT_DP)
            minWidth = NO_SIZE
            minimumWidth = NO_SIZE
            setPadding(
                densityPixels(HORIZONTAL_PADDING_DP),
                NO_SIZE,
                densityPixels(HORIZONTAL_PADDING_DP),
                NO_SIZE,
            )
            contentDescription = context.getString(descriptionResourceID)
            setCompoundDrawablesRelative(
                pillIcon(iconResourceID, iconTintColorResourceID),
                null,
                null,
                null,
            )
            // The pill carries no text, so an icon-to-text gap would only pad one side and
            // push the mark off centre.
            compoundDrawablePadding = NO_SIZE
            setOnClickListener { onSelected() }
        }
    }

    /**
     * Returns a pill-sized copy of a shared vector, recoloured when the caller names a
     * tint. The originals declare their own intrinsic size, the brand mark at 108dp, so
     * the copy is mutated before resizing to keep every other user of the same vector
     * unchanged. A multi-colour mark such as the brand mark passes no tint, because a
     * tint would flatten it to one solid shape.
     */
    private fun pillIcon(iconResourceID: Int, tintColorResourceID: Int): Drawable? {
        return context.getDrawable(iconResourceID)?.mutate()?.apply {
            val iconSizePixels = densityPixels(CONTROL_ICON_SIZE_DP)
            setBounds(NO_SIZE, NO_SIZE, iconSizePixels, iconSizePixels)
            if (tintColorResourceID != NO_TINT) {
                setTint(context.getColor(tintColorResourceID))
            }
        }
    }

    private fun pillLayoutParameters(): LayoutParams {
        return LayoutParams(LayoutParams.WRAP_CONTENT, densityPixels(CONTROL_HEIGHT_DP))
    }

    private fun densityPixels(valueDP: Int): Int {
        return (valueDP * resources.displayMetrics.density).toInt()
    }

    private companion object {
        const val CONTROL_HEIGHT_DP = 34
        const val CONTROL_ICON_SIZE_DP = 18
        const val HORIZONTAL_PADDING_DP = 8
        const val PILL_GAP_DP = 6
        const val NO_SIZE = 0
        const val NO_TINT = 0
    }
}
