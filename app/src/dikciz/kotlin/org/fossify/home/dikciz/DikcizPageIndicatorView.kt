package org.fossify.home.dikciz

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import org.fossify.home.R

internal data class PageIndicatorInteraction(
    val action: PageIndicatorAction,
    val fromPageIndex: Int,
    val toPageIndex: Int,
)

internal enum class PageIndicatorAction(
    val persistedValue: String,
) {
    Drag("drag"),
    LongPress("long_press"),
    Tap("tap"),
    TouchCancelled("touch_cancelled"),
    TouchDown("touch_down"),
    ;
}

internal enum class PageIndicatorAxis {
    Horizontal,
    Vertical,
}

internal enum class PageInsertionSide(
    val semanticID: String,
) {
    Before("before"),
    After("after"),
}

internal class DikcizPageIndicatorView @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null,
    defaultStyleAttribute: Int = 0,
) : View(context, attributes, defaultStyleAttribute) {
    var axis: PageIndicatorAxis = PageIndicatorAxis.Horizontal
        set(value) {
            if (field == value) {
                return
            }
            field = value
            requestLayout()
            invalidate()
    }
    var onPageSelectionRequested: ((Int) -> Unit)? = null
    var onPageEdgeLongPressed: ((PageInsertionSide) -> Unit)? = null
    var onInteraction: ((PageIndicatorInteraction) -> Unit)? = null

    private val activeDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.dikciz_foreground)
    }
    private val inactiveDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.dikciz_muted_foreground)
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val pageTitles = mutableListOf<String>()
    private val longPressRunnable = Runnable { handleLongPress() }

    private var initialTouchX = 0F
    private var initialTouchY = 0F
    private var isLongPressHandled = false
    private var isPageSelectionRequested = false
    private var isTrackingTouch = false
    private var selectedPageIndex = 0

    init {
        isClickable = true
        isLongClickable = true
        minimumHeight = densityPixels(MINIMUM_TOUCH_SIZE_DP)
        minimumWidth = densityPixels(MINIMUM_TOUCH_SIZE_DP)
    }

    fun clear() {
        pageTitles.clear()
        selectedPageIndex = 0
        updateContentDescription()
        invalidate()
    }

    fun setPages(titles: List<String>) {
        pageTitles.clear()
        pageTitles.addAll(titles)
        selectedPageIndex = selectedPageIndex.coerceIn(0, pageTitles.lastIndex.coerceAtLeast(0))
        updateContentDescription()
        invalidate()
    }

    fun setSelectedPageIndex(index: Int) {
        if (pageTitles.isEmpty()) {
            selectedPageIndex = 0
            updateContentDescription()
            return
        }
        selectedPageIndex = index.coerceIn(0, pageTitles.lastIndex)
        updateContentDescription()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = when (axis) {
            PageIndicatorAxis.Horizontal -> resolveSize(suggestedMinimumWidth, widthMeasureSpec)
            PageIndicatorAxis.Vertical -> resolveSize(minimumWidth, widthMeasureSpec)
        }
        val height = when (axis) {
            PageIndicatorAxis.Horizontal -> resolveSize(minimumHeight, heightMeasureSpec)
            PageIndicatorAxis.Vertical -> resolveSize(suggestedMinimumHeight, heightMeasureSpec)
        }
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pageTitles.isEmpty()) {
            return
        }
        pageTitles.indices.forEach { index ->
            val isSelected = index == selectedPageIndex
            canvas.drawCircle(
                dotXCoordinate(index),
                dotYCoordinate(index),
                if (isSelected) activeDotRadius() else inactiveDotRadius(),
                if (isSelected) activeDotPaint else inactiveDotPaint,
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (pageTitles.isEmpty()) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return beginTouch(event)
            MotionEvent.ACTION_MOVE -> return continueTouch(event)
            MotionEvent.ACTION_UP -> {
                val isClick = isTrackingTouch &&
                    !isLongPressHandled &&
                    abs(mainAxisCoordinate(event) - initialMainAxisCoordinate()) < pageSwitchThreshold()
                finishTouch(event)
                if (isClick) {
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> cancelTouch()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun beginTouch(event: MotionEvent): Boolean {
        initialTouchX = event.x
        initialTouchY = event.y
        isLongPressHandled = false
        isPageSelectionRequested = false
        isTrackingTouch = true
        parent?.requestDisallowInterceptTouchEvent(true)
        reportInteraction(PageIndicatorAction.TouchDown, selectedPageIndex)
        mainHandler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
        return true
    }

    private fun continueTouch(event: MotionEvent): Boolean {
        if (!isTrackingTouch || movementIsWithinTouchSlop(event)) {
            return true
        }
        mainHandler.removeCallbacks(longPressRunnable)
        parent?.requestDisallowInterceptTouchEvent(true)
        val distance = mainAxisCoordinate(event) - initialMainAxisCoordinate()
        if (!isPageSelectionRequested && abs(distance) >= pageSwitchThreshold()) {
            isPageSelectionRequested = true
            val offset = if (distance < 0F) NEXT_PAGE_OFFSET else PREVIOUS_PAGE_OFFSET
            requestPageSelection(selectedPageIndex + offset, PageIndicatorAction.Drag)
        }
        return true
    }

    private fun finishTouch(event: MotionEvent): Boolean {
        mainHandler.removeCallbacks(longPressRunnable)
        if (!isTrackingTouch) {
            return true
        }
        val wasLongPressHandled = isLongPressHandled
        val wasPageSelectionRequested = isPageSelectionRequested
        resetTouchTracking()
        if (wasLongPressHandled || wasPageSelectionRequested) {
            return true
        }
        val distance = mainAxisCoordinate(event) - initialMainAxisCoordinate()
        if (abs(distance) >= pageSwitchThreshold()) {
            val offset = if (distance < 0F) NEXT_PAGE_OFFSET else PREVIOUS_PAGE_OFFSET
            requestPageSelection(selectedPageIndex + offset, PageIndicatorAction.Drag)
        } else {
            requestPageSelection(pageIndexForTap(mainAxisCoordinate(event)), PageIndicatorAction.Tap)
        }
        parent?.requestDisallowInterceptTouchEvent(false)
        return true
    }

    private fun cancelTouch() {
        mainHandler.removeCallbacks(longPressRunnable)
        resetTouchTracking()
        reportInteraction(PageIndicatorAction.TouchCancelled, selectedPageIndex)
    }

    private fun handleLongPress() {
        if (!isTrackingTouch) {
            return
        }
        isLongPressHandled = true
        performLongClick()
        reportInteraction(PageIndicatorAction.LongPress, selectedPageIndex)
        val side = pageInsertionSideFor(initialMainAxisCoordinate())
        if (side != null) {
            onPageEdgeLongPressed?.invoke(side)
        }
    }

    private fun pageIndexForTap(mainAxisCoordinate: Float): Int {
        return when (pageInsertionSideFor(mainAxisCoordinate)) {
            PageInsertionSide.Before -> selectedPageIndex + PREVIOUS_PAGE_OFFSET
            PageInsertionSide.After -> selectedPageIndex + NEXT_PAGE_OFFSET
            null -> nearestDotIndex(mainAxisCoordinate)
        }
    }

    private fun movementIsWithinTouchSlop(event: MotionEvent): Boolean {
        val mainAxisDistance = abs(mainAxisCoordinate(event) - initialMainAxisCoordinate())
        val crossAxisDistance = abs(crossAxisCoordinate(event) - initialCrossAxisCoordinate())
        return mainAxisDistance <= touchSlop && crossAxisDistance <= touchSlop
    }

    private fun requestPageSelection(index: Int, action: PageIndicatorAction) {
        val targetIndex = index.coerceIn(0, pageTitles.lastIndex)
        reportInteraction(action, targetIndex)
        if (targetIndex == selectedPageIndex) {
            return
        }
        onPageSelectionRequested?.invoke(targetIndex)
    }

    private fun reportInteraction(action: PageIndicatorAction, targetIndex: Int) {
        onInteraction?.invoke(
            PageIndicatorInteraction(
                action = action,
                fromPageIndex = selectedPageIndex,
                toPageIndex = targetIndex,
            ),
        )
    }

    private fun mainAxisCoordinate(event: MotionEvent): Float {
        return when (axis) {
            PageIndicatorAxis.Horizontal -> event.x
            PageIndicatorAxis.Vertical -> event.y
        }
    }

    private fun crossAxisCoordinate(event: MotionEvent): Float {
        return when (axis) {
            PageIndicatorAxis.Horizontal -> event.y
            PageIndicatorAxis.Vertical -> event.x
        }
    }

    private fun initialMainAxisCoordinate(): Float {
        return when (axis) {
            PageIndicatorAxis.Horizontal -> initialTouchX
            PageIndicatorAxis.Vertical -> initialTouchY
        }
    }

    private fun initialCrossAxisCoordinate(): Float {
        return when (axis) {
            PageIndicatorAxis.Horizontal -> initialTouchY
            PageIndicatorAxis.Vertical -> initialTouchX
        }
    }

    private fun nearestDotIndex(mainAxisCoordinate: Float): Int {
        val rawIndex = ((mainAxisCoordinate - firstDotCoordinate()) / dotGap()).roundToInt()
        return rawIndex.coerceIn(0, pageTitles.lastIndex)
    }

    private fun pageInsertionSideFor(mainAxisCoordinate: Float): PageInsertionSide? {
        val stripStart = firstDotCoordinate() - activeDotRadius()
        val stripEnd = firstDotCoordinate() + dotStripLength() + activeDotRadius()
        if (mainAxisCoordinate < stripStart) {
            return PageInsertionSide.Before
        }
        if (mainAxisCoordinate > stripEnd) {
            return PageInsertionSide.After
        }
        return null
    }

    private fun dotXCoordinate(index: Int): Float {
        return when (axis) {
            PageIndicatorAxis.Horizontal -> firstDotCoordinate() + index * dotGap()
            PageIndicatorAxis.Vertical -> width / 2F
        }
    }

    private fun dotYCoordinate(index: Int): Float {
        return when (axis) {
            PageIndicatorAxis.Horizontal -> height / 2F
            PageIndicatorAxis.Vertical -> firstDotCoordinate() + index * dotGap()
        }
    }

    private fun firstDotCoordinate(): Float {
        return mainAxisLength() / 2F - dotStripLength() / 2F
    }

    private fun mainAxisLength(): Int {
        return when (axis) {
            PageIndicatorAxis.Horizontal -> width
            PageIndicatorAxis.Vertical -> height
        }
    }

    private fun dotStripLength(): Float {
        if (pageTitles.size <= 1) {
            return 0F
        }
        return dotGap() * (pageTitles.size - 1)
    }

    private fun dotGap(): Float = densityPixels(DOT_GAP_DP).toFloat()

    private fun activeDotRadius(): Float = densityPixels(ACTIVE_DOT_RADIUS_DP).toFloat()

    private fun inactiveDotRadius(): Float = densityPixels(INACTIVE_DOT_RADIUS_DP).toFloat()

    private fun pageSwitchThreshold(): Float {
        return max(densityPixels(PAGE_SWITCH_THRESHOLD_DP), touchSlop * TOUCH_SLOP_MULTIPLIER).toFloat()
    }

    private fun resetTouchTracking() {
        isLongPressHandled = false
        isPageSelectionRequested = false
        isTrackingTouch = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun updateContentDescription() {
        if (pageTitles.isEmpty()) {
            contentDescription = null
            return
        }
        contentDescription = context.getString(
            when (axis) {
                PageIndicatorAxis.Horizontal -> R.string.dikciz_horizontal_page_indicator_description
                PageIndicatorAxis.Vertical -> R.string.dikciz_vertical_page_indicator_description
            },
            selectedPageIndex + FIRST_PAGE_POSITION,
            pageTitles.size,
            pageTitles[selectedPageIndex],
        )
    }

    private fun densityPixels(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private companion object {
        const val ACTIVE_DOT_RADIUS_DP = 5
        const val DOT_GAP_DP = 14
        const val FIRST_PAGE_POSITION = 1
        const val INACTIVE_DOT_RADIUS_DP = 3
        const val MINIMUM_TOUCH_SIZE_DP = 24
        const val NEXT_PAGE_OFFSET = 1
        const val PAGE_SWITCH_THRESHOLD_DP = 24
        const val PREVIOUS_PAGE_OFFSET = -1
        const val TOUCH_SLOP_MULTIPLIER = 2
    }
}
