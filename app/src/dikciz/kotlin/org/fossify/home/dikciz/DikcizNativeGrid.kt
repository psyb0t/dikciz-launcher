package org.fossify.home.dikciz

/**
 * The fixed-viewport native page grid.
 *
 * A native page is one visible viewport that never gains canvas height. Every top-level
 * native item owns whole grid cells, and rendered pixels are derived from the current
 * viewport at draw time. The persisted rectangle therefore survives page selection,
 * process recreation, and rotation, while the pixels do not.
 *
 * This file carries no Android types so its edge cases keep fast unit coverage. Policy,
 * persistence, and UI callbacks belong to the caller.
 */
internal data class DikcizNativeGrid(
    val columns: Int,
    val rows: Int,
    val gapDP: Int,
    val outerPaddingDP: Int,
) {
    val cellCount: Int
        get() = columns * rows

    companion object {
        const val DEFAULT_COLUMNS = 4
        const val DEFAULT_GAP_DP = 8
        const val DEFAULT_OUTER_PADDING_DP = 12
        const val DEFAULT_ROWS = 6
        const val MAXIMUM_COLUMNS = 12
        const val MAXIMUM_GAP_DP = 64
        const val MAXIMUM_OUTER_PADDING_DP = 64
        const val MAXIMUM_ROWS = 12
        const val MINIMUM_COLUMNS = 1
        const val MINIMUM_GAP_DP = 0
        const val MINIMUM_OUTER_PADDING_DP = 0
        const val MINIMUM_ROWS = 1

        val BUNDLED_DEFAULT = DikcizNativeGrid(
            columns = DEFAULT_COLUMNS,
            rows = DEFAULT_ROWS,
            gapDP = DEFAULT_GAP_DP,
            outerPaddingDP = DEFAULT_OUTER_PADDING_DP,
        )
    }
}

/**
 * A logical placement on a [DikcizNativeGrid]. `column` and `row` are zero-based cell
 * indexes. The spans are cell counts and are never below [MINIMUM_SPAN].
 */
internal data class DikcizGridRectangle(
    val column: Int,
    val row: Int,
    val columnSpan: Int,
    val rowSpan: Int,
) {
    val endColumn: Int
        get() = column + columnSpan

    val endRow: Int
        get() = row + rowSpan

    val cellCount: Int
        get() = columnSpan * rowSpan

    fun overlaps(other: DikcizGridRectangle): Boolean {
        return column < other.endColumn &&
            other.column < endColumn &&
            row < other.endRow &&
            other.row < endRow
    }

    fun containsCell(cellColumn: Int, cellRow: Int): Boolean {
        return cellColumn in column until endColumn && cellRow in row until endRow
    }

    fun withSpan(span: DikcizGridSpan): DikcizGridRectangle {
        return copy(columnSpan = span.columnSpan, rowSpan = span.rowSpan)
    }

    companion object {
        const val MINIMUM_SPAN = 1
    }
}

/**
 * A requested cell extent with no anchor. Item types declare a comfortable default span,
 * and the placement engine finds where it actually goes.
 */
internal data class DikcizGridSpan(
    val columnSpan: Int,
    val rowSpan: Int,
) {
    fun atOrigin(): DikcizGridRectangle {
        return DikcizGridRectangle(
            column = ORIGIN_INDEX,
            row = ORIGIN_INDEX,
            columnSpan = columnSpan,
            rowSpan = rowSpan,
        )
    }

    /** Clamps the span so it can never exceed the grid it is about to be placed on. */
    fun boundedBy(grid: DikcizNativeGrid): DikcizGridSpan {
        return DikcizGridSpan(
            columnSpan = columnSpan.coerceIn(DikcizGridRectangle.MINIMUM_SPAN, grid.columns),
            rowSpan = rowSpan.coerceIn(DikcizGridRectangle.MINIMUM_SPAN, grid.rows),
        )
    }

    private companion object {
        const val ORIGIN_INDEX = 0
    }
}

/**
 * The finite placement failures. These values are the documented public failure codes and
 * are reported identically by the native UI, the WebSocket plane, and MCP.
 */
internal enum class DikcizGridFailure(
    val persistedValue: String,
) {
    PageFull("page_full"),
    GridCollision("grid_collision"),
    GridBounds("grid_bounds"),
    ;

    companion object {
        fun fromPersistedValue(value: String): DikcizGridFailure? {
            return entries.firstOrNull { it.persistedValue == value }
        }
    }
}

/** One placed item, used when validating a whole page against a candidate grid. */
internal data class DikcizGridItem(
    val id: String,
    val cell: DikcizGridRectangle,
)

/** The first item that a candidate grid cannot hold, and why. */
internal data class DikcizGridConflict(
    val itemID: String,
    val failure: DikcizGridFailure,
)

internal sealed interface DikcizGridPlacement {
    data class Placed(
        val rectangle: DikcizGridRectangle,
    ) : DikcizGridPlacement

    data class Rejected(
        val failure: DikcizGridFailure,
    ) : DikcizGridPlacement
}

/**
 * Bounds derived from a logical rectangle and the current page viewport. These are
 * render-time values only and are never persisted.
 */
internal data class DikcizGridPixelBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int
        get() = right - left

    val height: Int
        get() = bottom - top
}

/**
 * Owns rectangle validation, occupancy, first-fit placement, pixel conversion, and the
 * move and resize candidates for one page.
 */
internal object DikcizGridLayoutEngine {
    /** Whether [rectangle] has legal spans and sits entirely inside [grid]. */
    fun isWithinBounds(grid: DikcizNativeGrid, rectangle: DikcizGridRectangle): Boolean {
        if (rectangle.columnSpan < DikcizGridRectangle.MINIMUM_SPAN) {
            return false
        }
        if (rectangle.rowSpan < DikcizGridRectangle.MINIMUM_SPAN) {
            return false
        }
        if (rectangle.column < FIRST_INDEX || rectangle.row < FIRST_INDEX) {
            return false
        }
        return rectangle.endColumn <= grid.columns && rectangle.endRow <= grid.rows
    }

    /**
     * The first item that does not fit [grid], or null when every item fits.
     *
     * Items are checked in order, so the reported conflict is stable for a given page.
     * Callers use this to accept or reject a grid change as a whole, never partially.
     */
    fun firstConflict(
        grid: DikcizNativeGrid,
        items: List<DikcizGridItem>,
    ): DikcizGridConflict? {
        val placed = mutableListOf<DikcizGridRectangle>()
        items.forEach { item ->
            if (!isWithinBounds(grid, item.cell)) {
                return DikcizGridConflict(item.id, DikcizGridFailure.GridBounds)
            }
            if (collides(item.cell, placed)) {
                return DikcizGridConflict(item.id, DikcizGridFailure.GridCollision)
            }
            placed.add(item.cell)
        }
        return null
    }

    fun collides(
        rectangle: DikcizGridRectangle,
        occupied: List<DikcizGridRectangle>,
    ): Boolean {
        return occupied.any { other -> other.overlaps(rectangle) }
    }

    fun occupiedCellCount(occupied: List<DikcizGridRectangle>): Int {
        return occupied.sumOf { rectangle -> rectangle.cellCount }
    }

    /** True when not one free cell remains, so no further item can be placed at all. */
    fun isPageFull(grid: DikcizNativeGrid, occupied: List<DikcizGridRectangle>): Boolean {
        return findFirstFree(grid, occupied, SINGLE_CELL_RECTANGLE) == null
    }

    /**
     * The first free rectangle of the requested span in row-major order, or null when that
     * span fits nowhere.
     */
    fun findFirstFree(
        grid: DikcizNativeGrid,
        occupied: List<DikcizGridRectangle>,
        requested: DikcizGridRectangle,
    ): DikcizGridRectangle? {
        val columnSpan = requested.columnSpan.coerceAtLeast(DikcizGridRectangle.MINIMUM_SPAN)
        val rowSpan = requested.rowSpan.coerceAtLeast(DikcizGridRectangle.MINIMUM_SPAN)
        if (columnSpan > grid.columns || rowSpan > grid.rows) {
            return null
        }
        for (row in FIRST_INDEX..(grid.rows - rowSpan)) {
            for (column in FIRST_INDEX..(grid.columns - columnSpan)) {
                val candidate = DikcizGridRectangle(column, row, columnSpan, rowSpan)
                if (!collides(candidate, occupied)) {
                    return candidate
                }
            }
        }
        return null
    }

    /**
     * Places [requested] when the caller chose cells, or the first free single cell when it
     * did not. A chosen rectangle is never silently relocated: it is accepted or rejected.
     */
    fun place(
        grid: DikcizNativeGrid,
        occupied: List<DikcizGridRectangle>,
        requested: DikcizGridRectangle?,
    ): DikcizGridPlacement {
        if (requested == null) {
            val placed = findFirstFree(grid, occupied, SINGLE_CELL_RECTANGLE)
                ?: return DikcizGridPlacement.Rejected(DikcizGridFailure.PageFull)
            return DikcizGridPlacement.Placed(placed)
        }
        if (!isWithinBounds(grid, requested)) {
            return DikcizGridPlacement.Rejected(DikcizGridFailure.GridBounds)
        }
        if (!collides(requested, occupied)) {
            return DikcizGridPlacement.Placed(requested)
        }
        if (isPageFull(grid, occupied)) {
            return DikcizGridPlacement.Rejected(DikcizGridFailure.PageFull)
        }
        return DikcizGridPlacement.Rejected(DikcizGridFailure.GridCollision)
    }

    /**
     * Places a new item at its preferred span, falling back to the first free single cell.
     * The add flows ask for a comfortable default span but must still succeed on a page
     * that only has scattered single cells left.
     */
    fun placePreferredSpan(
        grid: DikcizNativeGrid,
        occupied: List<DikcizGridRectangle>,
        preferred: DikcizGridSpan,
    ): DikcizGridPlacement {
        findFirstFree(grid, occupied, preferred.boundedBy(grid).atOrigin())?.let { placed ->
            return DikcizGridPlacement.Placed(placed)
        }
        val fallback = findFirstFree(grid, occupied, SINGLE_CELL_RECTANGLE)
            ?: return DikcizGridPlacement.Rejected(DikcizGridFailure.PageFull)
        return DikcizGridPlacement.Placed(fallback)
    }

    /**
     * Validates moving the item currently at [current] to [target]. The moving item's own
     * cells never count as a collision against itself.
     */
    fun moveCandidate(
        grid: DikcizNativeGrid,
        occupied: List<DikcizGridRectangle>,
        current: DikcizGridRectangle,
        target: DikcizGridRectangle,
    ): DikcizGridPlacement {
        if (!isWithinBounds(grid, target)) {
            return DikcizGridPlacement.Rejected(DikcizGridFailure.GridBounds)
        }
        val others = occupied.filter { rectangle -> rectangle != current }
        if (collides(target, others)) {
            return DikcizGridPlacement.Rejected(DikcizGridFailure.GridCollision)
        }
        return DikcizGridPlacement.Placed(target)
    }

    /** Validates a resize. An anchor or span change follows the same rules as a move. */
    fun resizeCandidate(
        grid: DikcizNativeGrid,
        occupied: List<DikcizGridRectangle>,
        current: DikcizGridRectangle,
        target: DikcizGridRectangle,
    ): DikcizGridPlacement {
        return moveCandidate(grid, occupied, current, target)
    }

    /** The grid cell under a viewport pixel, clamped into the grid. */
    fun cellAtPixel(
        grid: DikcizNativeGrid,
        viewportWidthPixels: Int,
        viewportHeightPixels: Int,
        densityScale: Float,
        xPixels: Int,
        yPixels: Int,
    ): Pair<Int, Int> {
        val outerPaddingPixels = scaled(grid.outerPaddingDP, densityScale)
        val gapPixels = scaled(grid.gapDP, densityScale)
        val column = indexAtPixel(
            positionPixels = xPixels,
            outerPaddingPixels = outerPaddingPixels,
            gapPixels = gapPixels,
            extentPixels = viewportWidthPixels,
            count = grid.columns,
        )
        val row = indexAtPixel(
            positionPixels = yPixels,
            outerPaddingPixels = outerPaddingPixels,
            gapPixels = gapPixels,
            extentPixels = viewportHeightPixels,
            count = grid.rows,
        )
        return column to row
    }

    /**
     * The rendered bounds of [rectangle] inside the given viewport.
     *
     * Edges come from one fractional stride so adjacent cells keep exactly one gap between
     * them and the final column and row land on the viewport inset, instead of
     * accumulating per-cell rounding error.
     */
    fun pixelBounds(
        grid: DikcizNativeGrid,
        rectangle: DikcizGridRectangle,
        viewportWidthPixels: Int,
        viewportHeightPixels: Int,
        densityScale: Float,
    ): DikcizGridPixelBounds {
        val outerPaddingPixels = scaled(grid.outerPaddingDP, densityScale)
        val gapPixels = scaled(grid.gapDP, densityScale)
        val left = edgePixels(
            index = rectangle.column,
            outerPaddingPixels = outerPaddingPixels,
            gapPixels = gapPixels,
            extentPixels = viewportWidthPixels,
            count = grid.columns,
        )
        val right = edgePixels(
            index = rectangle.endColumn,
            outerPaddingPixels = outerPaddingPixels,
            gapPixels = gapPixels,
            extentPixels = viewportWidthPixels,
            count = grid.columns,
        ) - gapPixels
        val top = edgePixels(
            index = rectangle.row,
            outerPaddingPixels = outerPaddingPixels,
            gapPixels = gapPixels,
            extentPixels = viewportHeightPixels,
            count = grid.rows,
        )
        val bottom = edgePixels(
            index = rectangle.endRow,
            outerPaddingPixels = outerPaddingPixels,
            gapPixels = gapPixels,
            extentPixels = viewportHeightPixels,
            count = grid.rows,
        ) - gapPixels
        return DikcizGridPixelBounds(
            left = left,
            top = top,
            right = maxOf(right, left),
            bottom = maxOf(bottom, top),
        )
    }

    private fun edgePixels(
        index: Int,
        outerPaddingPixels: Int,
        gapPixels: Int,
        extentPixels: Int,
        count: Int,
    ): Int {
        val usablePixels = extentPixels - (outerPaddingPixels * EDGE_COUNT)
        if (usablePixels <= NO_PIXELS || count <= NO_CELLS) {
            return outerPaddingPixels
        }
        val stride = (usablePixels + gapPixels).toDouble() / count.toDouble()
        return outerPaddingPixels + Math.round(index * stride).toInt()
    }

    private fun indexAtPixel(
        positionPixels: Int,
        outerPaddingPixels: Int,
        gapPixels: Int,
        extentPixels: Int,
        count: Int,
    ): Int {
        val usablePixels = extentPixels - (outerPaddingPixels * EDGE_COUNT)
        if (usablePixels <= NO_PIXELS || count <= NO_CELLS) {
            return FIRST_INDEX
        }
        val stride = (usablePixels + gapPixels).toDouble() / count.toDouble()
        val offset = (positionPixels - outerPaddingPixels).toDouble()
        val index = Math.floor(offset / stride).toInt()
        return index.coerceIn(FIRST_INDEX, count - LAST_INDEX_OFFSET)
    }

    private fun scaled(valueDP: Int, densityScale: Float): Int {
        return (valueDP * densityScale).toInt()
    }

    private const val EDGE_COUNT = 2
    private const val FIRST_INDEX = 0
    private const val LAST_INDEX_OFFSET = 1
    private const val NO_CELLS = 0
    private const val NO_PIXELS = 0

    private val SINGLE_CELL_RECTANGLE = DikcizGridRectangle(
        column = FIRST_INDEX,
        row = FIRST_INDEX,
        columnSpan = DikcizGridRectangle.MINIMUM_SPAN,
        rowSpan = DikcizGridRectangle.MINIMUM_SPAN,
    )
}
