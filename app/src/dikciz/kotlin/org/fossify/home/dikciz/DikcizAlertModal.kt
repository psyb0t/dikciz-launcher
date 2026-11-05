package org.fossify.home.dikciz

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout

/** A blocking backdrop and bounded alert card, hosted over apps or in the foreground activity. */
internal class DikcizAlertModal(
    private val context: Context,
    private val logger: DikcizLogger,
) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private var overlay: View? = null
    private var dialog: AlertDialog? = null
    private var root: View? = null
    var presentation = "notification"
        private set

    fun show(content: View): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (visibleRoot() != null) return true
        close()
        val useOverlay = Settings.canDrawOverlays(context)
        val activity = if (useOverlay) null else DikcizForegroundActivity.current(context) ?: return false
        val container = Backdrop(activity ?: context, content)
        if (useOverlay) {
            manager.addView(container, WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT,
            ))
            overlay = container
            presentation = "overlay"
        } else {
            dialog = AlertDialog.Builder(activity).setView(container).setCancelable(false).create().also {
                it.show()
                it.window?.apply {
                    setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    decorView.setPadding(0, 0, 0, 0)
                    setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                }
            }
            presentation = "dialog"
        }
        root = container
        container.requestFocus()
        return true
    }

    fun visibleRoot(): View? = root?.takeIf { it.isAttachedToWindow }

    fun close() {
        check(Looper.myLooper() == Looper.getMainLooper())
        dialog?.dismiss()
        dialog = null
        overlay?.let {
            try {
                manager.removeViewImmediate(it)
            } catch (exception: IllegalArgumentException) {
                logger.warn("alert_modal_window_already_removed", mapOf("error_class" to exception.javaClass.simpleName))
            }
        }
        overlay = null
        (root as? ViewGroup)?.removeAllViews()
        root = null
        presentation = "notification"
    }

    private class Backdrop(context: Context, private val card: View) : FrameLayout(context) {
        private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

        init {
            setBackgroundColor(BACKDROP_COLOR)
            isClickable = true
            isFocusableInTouchMode = true
            contentDescription = "Alert modal background"
            setPadding(dp(WINDOW_MARGIN), dp(WINDOW_MARGIN), dp(WINDOW_MARGIN), dp(WINDOW_MARGIN))
            addView(card, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) return true
            return super.dispatchKeyEvent(event)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)
            val cardWidth = (width - paddingLeft - paddingRight).coerceIn(0, dp(MAX_CARD_WIDTH))
            val cardHeight = (height - paddingTop - paddingBottom).coerceAtLeast(0)
            card.measure(
                MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(cardHeight, MeasureSpec.AT_MOST),
            )
            setMeasuredDimension(width, height)
        }
    }

    companion object {
        private val BACKDROP_COLOR = 0xB3000000.toInt()
        private const val WINDOW_MARGIN = 24
        private const val MAX_CARD_WIDTH = 480
    }
}
