package org.fossify.home.dikciz

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.TextView

internal object DikcizUiStyle {
    const val BACKGROUND = 0xFF111310.toInt()
    const val SURFACE = 0xFF20241E.toInt()
    const val FOREGROUND = 0xFFF1F3EB.toInt()
    const val MUTED = 0xFFB3BBAA.toInt()
    const val ACCENT = 0xFFF6C85F.toInt()
    const val ERROR = 0xFFFFA6A0.toInt()
    private const val EDGE = 0xFF3C4336.toInt()
    private const val RIPPLE = 0x44F6C85F

    fun button(view: Button, primary: Boolean = false) {
        val density = view.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        view.backgroundTintList = null
        view.background = RippleDrawable(ColorStateList.valueOf(RIPPLE), GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(if (primary) ACCENT else android.graphics.Color.TRANSPARENT)
        }, null)
        view.isAllCaps = false
        view.textSize = 16f
        view.gravity = if (primary) android.view.Gravity.CENTER else
            android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
        view.minHeight = dp(48)
        view.minimumHeight = dp(48)
        view.minWidth = dp(48)
        view.minimumWidth = dp(48)
        view.setPadding(dp(12), dp(8), dp(12), dp(8))
        (view.layoutParams as? android.widget.LinearLayout.LayoutParams)?.let { layout ->
            layout.topMargin = maxOf(layout.topMargin, dp(3))
            layout.bottomMargin = maxOf(layout.bottomMargin, dp(3))
            if (layout.width in 1 until dp(48)) layout.width = dp(48)
            view.layoutParams = layout
        }
        view.setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(MUTED, if (primary) BACKGROUND else FOREGROUND)))
    }

    fun dialog(dialog: AlertDialog) {
        val root = dialog.window?.decorView ?: return
        val density = root.resources.displayMetrics.density
        dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
            cornerRadius = 24 * density
            setColor(BACKGROUND)
        })
        styleTree(root)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { button(it, primary = true) }
    }

    private fun styleTree(view: View) {
        when (view) {
            is android.widget.Spinner -> {
                view.minimumHeight = (48 * view.resources.displayMetrics.density).toInt()
                view.minimumWidth = (48 * view.resources.displayMetrics.density).toInt()
            }
            is CompoundButton -> {
                view.buttonTintList = ColorStateList.valueOf(ACCENT)
                view.setTextColor(FOREGROUND)
                view.minHeight = (48 * view.resources.displayMetrics.density).toInt()
                view.isAllCaps = false
            }
            is Button -> button(view)
            is EditText -> {
                val padding = (12 * view.resources.displayMetrics.density).toInt()
                view.setTextColor(FOREGROUND); view.setHintTextColor(MUTED)
                view.textSize = 16f
                view.setPadding(padding, padding, padding, padding)
                view.backgroundTintList = null
                view.background = GradientDrawable().apply {
                    cornerRadius = padding.toFloat(); setColor(SURFACE)
                    setStroke((view.resources.displayMetrics.density).toInt().coerceAtLeast(1), EDGE)
                }
                view.minHeight = (48 * view.resources.displayMetrics.density).toInt()
            }
            is TextView -> view.setTextColor(FOREGROUND)
        }
        if (view is ViewGroup) for (index in 0 until view.childCount) styleTree(view.getChildAt(index))
    }
}
