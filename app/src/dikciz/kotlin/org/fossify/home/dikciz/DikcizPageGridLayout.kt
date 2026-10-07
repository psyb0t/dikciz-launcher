package org.fossify.home.dikciz

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import org.fossify.home.R

/**
 * The fixed native page canvas.
 *
 * Children are positioned from their logical [DikcizGridRectangle], never from stored
 * pixels, and the canvas is always exactly the viewport it was measured against. There is
 * no vertical growth, so a native page can never hide content below its visible bounds.
 */
internal class DikcizPageGridLayout @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null,
    defaultStyleAttribute: Int = 0,
) : ViewGroup(context, attributes, defaultStyleAttribute) {

    var grid: DikcizNativeGrid = DikcizNativeGrid.BUNDLED_DEFAULT
        set(value) {
            if (field == value) {
                return
            }
            field = value
            requestLayout()
        }

    /**
     * Draws every cell of the grid behind the page content.
     *
     * Placement snaps to whole cells, so a move or a resize is guesswork until the cells
     * are visible. The launcher turns the guides on for the length of that gesture and
     * off again afterwards.
     */
    var isGridGuideVisible: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidate()
        }

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.dikciz_grid_guide)
        style = Paint.Style.STROKE
    }
    private val guideRectangle = RectF()

    init {
        // A ViewGroup skips onDraw until it is told it has something of its own to paint.
        setWillNotDraw(false)
    }

    override fun generateDefaultLayoutParams(): LayoutParams = LayoutParams()

    override fun generateLayoutParams(attributes: AttributeSet): LayoutParams =
        LayoutParams(context, attributes)

    override fun generateLayoutParams(parameters: ViewGroup.LayoutParams): LayoutParams =
        LayoutParams(parameters)

    override fun checkLayoutParams(parameters: ViewGroup.LayoutParams): Boolean =
        parameters is LayoutParams

    /** The pixel rectangle a cell occupies on the current canvas. */
    fun cellBounds(cell: DikcizGridRectangle): DikcizGridPixelBounds {
        return DikcizGridLayoutEngine.pixelBounds(
            grid = grid,
            rectangle = cell,
            viewportWidthPixels = contentWidth(width),
            viewportHeightPixels = contentHeight(height),
            densityScale = resources.displayMetrics.density,
        )
    }

    /** The cell under a canvas pixel, clamped into the grid. */
    fun cellAt(xPixels: Int, yPixels: Int): Pair<Int, Int> {
        return DikcizGridLayoutEngine.cellAtPixel(
            grid = grid,
            viewportWidthPixels = contentWidth(width),
            viewportHeightPixels = contentHeight(height),
            densityScale = resources.displayMetrics.density,
            xPixels = xPixels - paddingLeft,
            yPixels = yPixels - paddingTop,
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val viewportWidth = MeasureSpec.getSize(widthMeasureSpec)
        val viewportHeight = MeasureSpec.getSize(heightMeasureSpec)
        val availableWidth = contentWidth(viewportWidth)
        val availableHeight = contentHeight(viewportHeight)
        val densityScale = resources.displayMetrics.density
        forEachVisibleChild { child, layoutParams ->
            val bounds = DikcizGridLayoutEngine.pixelBounds(
                grid = grid,
                rectangle = layoutParams.cell,
                viewportWidthPixels = availableWidth,
                viewportHeightPixels = availableHeight,
                densityScale = densityScale,
            )
            val childWidth = (bounds.width - layoutParams.leftMargin - layoutParams.rightMargin)
                .coerceAtLeast(NO_SIZE)
            val childHeight = (bounds.height - layoutParams.topMargin - layoutParams.bottomMargin)
                .coerceAtLeast(NO_SIZE)
            child.measure(
                MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY),
            )
            layoutParams.resolvedLeft = paddingLeft + bounds.left + layoutParams.leftMargin
            layoutParams.resolvedTop = paddingTop + bounds.top + layoutParams.topMargin
        }
        // Measuring to the deepest child is what used to grow the page. A fixed screen
        // reports the viewport it was given and nothing else.
        setMeasuredDimension(
            resolveSize(viewportWidth, widthMeasureSpec),
            resolveSize(viewportHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isGridGuideVisible) {
            return
        }
        val densityScale = resources.displayMetrics.density
        val cornerRadius = GUIDE_CORNER_RADIUS_DP * densityScale
        guidePaint.strokeWidth = GUIDE_STROKE_WIDTH_DP * densityScale
        for (column in 0 until grid.columns) {
            for (row in 0 until grid.rows) {
                val bounds = DikcizGridLayoutEngine.pixelBounds(
                    grid = grid,
                    rectangle = DikcizGridRectangle(
                        column = column,
                        row = row,
                        columnSpan = DikcizGridRectangle.MINIMUM_SPAN,
                        rowSpan = DikcizGridRectangle.MINIMUM_SPAN,
                    ),
                    viewportWidthPixels = contentWidth(width),
                    viewportHeightPixels = contentHeight(height),
                    densityScale = densityScale,
                )
                guideRectangle.set(
                    (paddingLeft + bounds.left).toFloat(),
                    (paddingTop + bounds.top).toFloat(),
                    (paddingLeft + bounds.left + bounds.width).toFloat(),
                    (paddingTop + bounds.top + bounds.height).toFloat(),
                )
                canvas.drawRoundRect(guideRectangle, cornerRadius, cornerRadius, guidePaint)
            }
        }
    }

    override fun onLayout(isChanged: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        forEachVisibleChild { child, layoutParams ->
            child.layout(
                layoutParams.resolvedLeft,
                layoutParams.resolvedTop,
                layoutParams.resolvedLeft + child.measuredWidth,
                layoutParams.resolvedTop + child.measuredHeight,
            )
        }
    }

    private fun contentWidth(size: Int): Int {
        return (size - paddingLeft - paddingRight).coerceAtLeast(NO_SIZE)
    }

    private fun contentHeight(size: Int): Int {
        return (size - paddingTop - paddingBottom).coerceAtLeast(NO_SIZE)
    }

    private inline fun forEachVisibleChild(action: (View, LayoutParams) -> Unit) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == GONE) {
                continue
            }
            action(child, child.layoutParams as LayoutParams)
        }
    }

    internal class LayoutParams : MarginLayoutParams {
        var cell: DikcizGridRectangle = SINGLE_CELL
        var resolvedLeft = 0
        var resolvedTop = 0

        constructor() : super(MATCH_PARENT, MATCH_PARENT)

        constructor(cell: DikcizGridRectangle) : super(MATCH_PARENT, MATCH_PARENT) {
            this.cell = cell
        }

        constructor(context: Context, attributes: AttributeSet) : super(context, attributes)

        constructor(source: ViewGroup.LayoutParams) : super(source)

        private companion object {
            val SINGLE_CELL = DikcizGridRectangle(
                column = 0,
                row = 0,
                columnSpan = DikcizGridRectangle.MINIMUM_SPAN,
                rowSpan = DikcizGridRectangle.MINIMUM_SPAN,
            )
        }
    }

    private companion object {
        const val NO_SIZE = 0
        const val GUIDE_CORNER_RADIUS_DP = 6F
        const val GUIDE_STROKE_WIDTH_DP = 1F
    }
}
