package org.fossify.home.dikciz

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * An app shortcut tile: one icon above one label, centred together in the grid cell.
 *
 * The icon takes the largest square left once the label has its line, measured against
 * the narrower side of the cell. A tile is a grid cell rather than a fixed box, so one
 * fixed icon size would sit marooned in a large cell and overflow a small one.
 *
 * A LinearLayout cannot centre the pair. Its weight accounting reserves the space it
 * handed the icon rather than the smaller square the icon took, which leaves the group
 * against the top of the cell.
 *
 * The first child is the icon and the last is the label. A tile whose app has no icon
 * carries the label alone.
 */
internal class DikcizAppShortcutTileView(
    context: Context,
    private val iconLabelGapPixels: Int,
) : ViewGroup(context) {

    private val iconView: View?
        get() = if (childCount > SINGLE_CHILD) getChildAt(FIRST_CHILD_INDEX) else null

    private val labelView: View?
        get() = if (childCount > NO_CHILDREN) getChildAt(childCount - 1) else null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
        val label = labelView
        label?.measure(
            MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.AT_MOST),
        )
        val icon = iconView
        if (icon != null) {
            val labelHeight = label?.measuredHeight ?: NO_SIZE
            val iconSide = minOf(
                availableWidth,
                availableHeight - labelHeight - gapFor(icon, label),
            ).coerceAtLeast(NO_SIZE)
            val iconSpec = MeasureSpec.makeMeasureSpec(iconSide, MeasureSpec.EXACTLY)
            icon.measure(iconSpec, iconSpec)
        }
        setMeasuredDimension(availableWidth, availableHeight)
    }

    override fun onLayout(isChanged: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val height = bottom - top
        val icon = iconView
        val label = labelView
        val iconSide = icon?.measuredHeight ?: NO_SIZE
        val labelHeight = label?.measuredHeight ?: NO_SIZE
        val gap = gapFor(icon, label)
        var childTop = ((height - iconSide - gap - labelHeight) / HALF).coerceAtLeast(NO_SIZE)
        if (icon != null) {
            val iconLeft = ((width - iconSide) / HALF).coerceAtLeast(NO_SIZE)
            icon.layout(iconLeft, childTop, iconLeft + iconSide, childTop + iconSide)
            childTop += iconSide + gap
        }
        if (label == null) {
            return
        }
        val labelLeft = ((width - label.measuredWidth) / HALF).coerceAtLeast(NO_SIZE)
        label.layout(
            labelLeft,
            childTop,
            labelLeft + label.measuredWidth,
            childTop + labelHeight,
        )
    }

    private fun gapFor(icon: View?, label: View?): Int {
        if (icon == null || label == null || label.measuredHeight <= NO_SIZE) {
            return NO_SIZE
        }
        return iconLabelGapPixels
    }

    private companion object {
        const val NO_SIZE = 0
        const val NO_CHILDREN = 0
        const val SINGLE_CHILD = 1
        const val FIRST_CHILD_INDEX = 0
        const val HALF = 2
    }
}
