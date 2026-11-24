package org.fossify.home.dikciz

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.abs
import kotlin.math.roundToInt

internal class DikcizAppGroupMemberDrag(
    private val host: FrameLayout,
    private val dialog: () -> AlertDialog,
    private val showGrid: (Boolean) -> Unit,
    private val canDrop: (Float, Float) -> Boolean,
) {
    private val touchSlop = ViewConfiguration.get(host.context).scaledTouchSlop
    private val iconSize = (ICON_DP * host.resources.displayMetrics.density).roundToInt()
    private val location = IntArray(2)
    private var shadow: ImageView? = null
    private var dimAmount = 0f
    private var decorAlpha = 1f

    // Ordinary taps go to the member's native onTouchEvent, which calls performClick.
    @SuppressLint("ClickableViewAccessibility")
    fun bind(
        member: View,
        icon: Drawable,
        onMenu: () -> Unit,
        onDrop: (Float, Float) -> Unit,
    ) {
        var hasTouch = false
        var armed = false
        var dragging = false
        var startX = 0f
        var startY = 0f

        fun release() {
            member.isPressed = false
            member.scaleX = 1f
            member.scaleY = 1f
            member.parent?.requestDisallowInterceptTouchEvent(false)
            hasTouch = false
            armed = false
            dragging = false
            clearPreview()
        }

        member.setOnLongClickListener {
            if (hasTouch) {
                armed = true
                member.isPressed = true
                member.scaleX = PICKUP_SCALE
                member.scaleY = PICKUP_SCALE
                member.parent?.requestDisallowInterceptTouchEvent(true)
            } else {
                onMenu()
            }
            true
        }
        member.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    hasTouch = true
                    startX = event.rawX
                    startY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!armed) return@setOnTouchListener false
                    if (maxOf(abs(event.rawX - startX), abs(event.rawY - startY)) > touchSlop) {
                        dragging = true
                    }
                    if (dragging) updatePreview(icon, event.rawX, event.rawY)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val wasArmed = armed
                    val moved = dragging
                    val leftFolder = moved && isOutsideFolder(event.rawX, event.rawY)
                    release()
                    if (leftFolder && canDrop(event.rawX, event.rawY)) onDrop(event.rawX, event.rawY)
                    if (wasArmed && !moved) onMenu()
                    wasArmed
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                    val wasArmed = armed
                    release()
                    wasArmed
                }
                else -> false
            }
        }
    }

    private fun isOutsideFolder(x: Float, y: Float): Boolean {
        val decor = dialog().window?.decorView ?: return false
        decor.getLocationOnScreen(location)
        return x < location[0] || x >= location[0] + decor.width ||
            y < location[1] || y >= location[1] + decor.height
    }

    private fun updatePreview(icon: Drawable, x: Float, y: Float) {
        if (!isOutsideFolder(x, y)) {
            clearPreview()
            return
        }
        val preview = shadow ?: startPreview(icon) ?: return
        host.getLocationOnScreen(location)
        val left = (x - location[0] - iconSize / 2f).roundToInt()
        val top = (y - location[1] - iconSize / 2f).roundToInt()
        preview.layout(left, top, left + iconSize, top + iconSize)
        preview.alpha = if (canDrop(x, y)) AVAILABLE_ALPHA else BLOCKED_ALPHA
    }

    private fun startPreview(icon: Drawable): ImageView? {
        val window = dialog().window ?: return null
        dimAmount = window.attributes.dimAmount
        decorAlpha = window.decorView.alpha
        // Android keeps delivering this touch to the dialog window until release.
        window.setDimAmount(0f)
        window.decorView.alpha = 0f
        showGrid(true)
        return ImageView(host.context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            val drawable = icon.constantState?.newDrawable(host.resources)?.mutate() ?: icon
            setImageDrawable(drawable)
            val size = View.MeasureSpec.makeMeasureSpec(iconSize, View.MeasureSpec.EXACTLY)
            measure(size, size)
            host.overlay.add(this)
            shadow = this
        }
    }

    fun clearPreview() {
        val preview = shadow ?: return
        host.overlay.remove(preview)
        shadow = null
        dialog().window?.let { window ->
            window.setDimAmount(dimAmount)
            window.decorView.alpha = decorAlpha
        }
        showGrid(false)
    }

    private companion object {
        const val ICON_DP = 48
        const val PICKUP_SCALE = 1.08f
        const val AVAILABLE_ALPHA = 0.9f
        const val BLOCKED_ALPHA = 0.4f
    }
}
