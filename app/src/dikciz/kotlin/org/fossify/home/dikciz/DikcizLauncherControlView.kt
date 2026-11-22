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
    DikcizApps(
        "apps",
        R.string.dikciz_launcher_control_apps,
        R.drawable.dikciz_icon_route_app_drawer,
    ),
    AutomationDebugger(
        "automation-debugger",
        R.string.dikciz_launcher_control_debugger,
        R.drawable.dikciz_icon_route_settings,
    ),
    AssistantLibrary(
        "assistant-library",
        R.string.dikciz_launcher_control_library,
        R.drawable.dikciz_icon_route_settings,
    ),
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

internal class DikcizLauncherControlView @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null,
    defaultStyleAttribute: Int = 0,
) : LinearLayout(context, attributes, defaultStyleAttribute) {
    var onControlsRequested: (() -> Unit)? = null
    var onPageSearchRequested: (() -> Unit)? = null
    var onAppDrawerRequested: (() -> Unit)? = null

    val pageSearchButton: Button
    val appDrawerButton: Button
    val controlButton: Button
    val chatButton: Button
    var onChatRequested: (() -> Unit)? = null

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
        appDrawerButton = createPillButton(
            iconResourceID = R.drawable.dikciz_icon_route_app_drawer,
            descriptionResourceID = R.string.dikciz_launcher_control_app_drawer,
        ) { onAppDrawerRequested?.invoke() }
        chatButton = Button(context).apply {
            background = context.getDrawable(R.drawable.dikciz_control_pill)
            gravity = Gravity.CENTER
            minWidth = densityPixels(CONTROL_SIZE_DP)
            minimumWidth = densityPixels(CONTROL_SIZE_DP)
            minHeight = densityPixels(CONTROL_SIZE_DP)
            minimumHeight = densityPixels(CONTROL_SIZE_DP)
            setPadding(densityPixels(HORIZONTAL_PADDING_DP), 0, densityPixels(HORIZONTAL_PADDING_DP), 0)
            setTextColor(context.getColor(R.color.dikciz_brand_cheese))
            textSize = 11f
            val icon = context.getDrawable(R.drawable.dikciz_chat_cheese)?.mutate()
            icon?.setBounds(0, 0, densityPixels(CHAT_ICON_DP), densityPixels(CHAT_ICON_DP))
            setCompoundDrawablesRelative(icon, null, null, null)
            contentDescription = "Open Dikciz chat"
            setOnClickListener { onChatRequested?.invoke() }
        }
        addView(appDrawerButton, pillLayoutParameters())
        addView(pageSearchButton, pillLayoutParameters())
        addView(chatButton, pillLayoutParameters())
        addView(controlButton, pillLayoutParameters())
    }

    fun setChatUnread(count: Int) {
        chatButton.text = if (count == 0) "" else count.coerceAtMost(99).toString()
        chatButton.contentDescription = "Open Dikciz chat. $count unread"
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
            minHeight = densityPixels(CONTROL_SIZE_DP)
            minimumHeight = densityPixels(CONTROL_SIZE_DP)
            minWidth = densityPixels(CONTROL_SIZE_DP)
            minimumWidth = densityPixels(CONTROL_SIZE_DP)
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
        return LayoutParams(LayoutParams.WRAP_CONTENT, densityPixels(CONTROL_SIZE_DP))
    }

    private fun densityPixels(valueDP: Int): Int {
        return (valueDP * resources.displayMetrics.density).toInt()
    }

    private companion object {
        const val CHAT_ICON_DP = 26
        const val CONTROL_SIZE_DP = 48
        const val CONTROL_ICON_SIZE_DP = 18
        const val HORIZONTAL_PADDING_DP = 8
        const val NO_SIZE = 0
        const val NO_TINT = 0
    }
}
