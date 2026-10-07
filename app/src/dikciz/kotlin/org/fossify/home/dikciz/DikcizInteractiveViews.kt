package org.fossify.home.dikciz

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.ScrollView

class DikcizPageScrollView @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null,
    defaultStyleAttribute: Int = 0,
) : ScrollView(context, attributes, defaultStyleAttribute) {
    fun verticalScrollRange(): Int = computeVerticalScrollRange()

    override fun measureChildWithMargins(
        child: android.view.View,
        parentWidthMeasureSpec: Int,
        widthUsed: Int,
        parentHeightMeasureSpec: Int,
        heightUsed: Int,
    ) {
        val viewportHeight = MeasureSpec.getSize(parentHeightMeasureSpec)
        val childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
            (viewportHeight - paddingTop - paddingBottom - heightUsed).coerceAtLeast(0),
            MeasureSpec.AT_MOST,
        )
        val childWidthMeasureSpec = getChildMeasureSpec(
            parentWidthMeasureSpec,
            paddingLeft + paddingRight + widthUsed,
            child.layoutParams.width,
        )
        child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

class DikcizWidgetFrameLayout(context: Context) : FrameLayout(context) {
    var resizeTouchCapture: ((MotionEvent) -> Boolean)? = null
    var resizeOverlay: Drawable? = null
        set(value) {
            field = value
            invalidate()
        }

    init {
        clipChildren = true
        clipToPadding = true
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (resizeTouchCapture?.invoke(event) == true) {
            return true
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val overlay = resizeOverlay ?: return
        overlay.setBounds(0, 0, width, height)
        overlay.draw(canvas)
    }
}
