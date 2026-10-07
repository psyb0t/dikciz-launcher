package org.fossify.home.dikciz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val GRID_COLUMNS = 4
private const val GRID_ROWS = 3
private const val GRID_GAP_DP = 8
private const val GRID_OUTER_PADDING_DP = 12

private val STANDARD_GRID = DikcizNativeGrid(
    columns = GRID_COLUMNS,
    rows = GRID_ROWS,
    gapDP = GRID_GAP_DP,
    outerPaddingDP = GRID_OUTER_PADDING_DP,
)

private const val DENSITY_SCALE = 1.0f
private const val VIEWPORT_WIDTH_PIXELS = 400
private const val VIEWPORT_HEIGHT_PIXELS = 300

// densityScale is 1.0, so scaled DP equals raw DP.
private const val OUTER_PADDING_PIXELS = GRID_OUTER_PADDING_DP
private const val GAP_PIXELS = GRID_GAP_DP

private val SINGLE_CELL_REQUEST = DikcizGridRectangle(column = 0, row = 0, columnSpan = 1, rowSpan = 1)

private val ROW_RECTANGLE_0 = DikcizGridRectangle(column = 0, row = 0, columnSpan = GRID_COLUMNS, rowSpan = 1)
private val ROW_RECTANGLE_1 = DikcizGridRectangle(column = 0, row = 1, columnSpan = GRID_COLUMNS, rowSpan = 1)
private val ROW_RECTANGLE_2 = DikcizGridRectangle(column = 0, row = 2, columnSpan = GRID_COLUMNS, rowSpan = 1)

/** Fills every one of the grid's 12 cells, leaving nothing free. */
private val FULL_GRID_OCCUPIED = listOf(ROW_RECTANGLE_0, ROW_RECTANGLE_1, ROW_RECTANGLE_2)

private val PARTIAL_LAST_ROW_RECTANGLE = DikcizGridRectangle(
    column = 0,
    row = 2,
    columnSpan = GRID_COLUMNS - 1,
    rowSpan = 1,
)

/** Fills every cell except the last column of the last row. */
private val ONE_FREE_CELL_OCCUPIED = listOf(ROW_RECTANGLE_0, ROW_RECTANGLE_1, PARTIAL_LAST_ROW_RECTANGLE)
private val ONLY_FREE_CELL = DikcizGridRectangle(
    column = GRID_COLUMNS - 1,
    row = GRID_ROWS - 1,
    columnSpan = 1,
    rowSpan = 1,
)

class DikcizNativeGridTest {

    // --- isWithinBounds ---

    @Test
    fun `isWithinBounds accepts a valid rectangle`() {
        val rectangle = DikcizGridRectangle(column = 1, row = 1, columnSpan = 2, rowSpan = 1)

        assertTrue(DikcizGridLayoutEngine.isWithinBounds(STANDARD_GRID, rectangle))
    }

    @Test
    fun `isWithinBounds rejects a zero column span`() {
        val rectangle = DikcizGridRectangle(column = 0, row = 0, columnSpan = 0, rowSpan = 1)

        assertFalse(DikcizGridLayoutEngine.isWithinBounds(STANDARD_GRID, rectangle))
    }

    @Test
    fun `isWithinBounds rejects a negative row span`() {
        val rectangle = DikcizGridRectangle(column = 0, row = 0, columnSpan = 1, rowSpan = -1)

        assertFalse(DikcizGridLayoutEngine.isWithinBounds(STANDARD_GRID, rectangle))
    }

    @Test
    fun `isWithinBounds rejects a negative column`() {
        val rectangle = DikcizGridRectangle(column = -1, row = 0, columnSpan = 1, rowSpan = 1)

        assertFalse(DikcizGridLayoutEngine.isWithinBounds(STANDARD_GRID, rectangle))
    }

    @Test
    fun `isWithinBounds rejects a negative row`() {
        val rectangle = DikcizGridRectangle(column = 0, row = -1, columnSpan = 1, rowSpan = 1)

        assertFalse(DikcizGridLayoutEngine.isWithinBounds(STANDARD_GRID, rectangle))
    }

    @Test
    fun `isWithinBounds rejects a rectangle crossing the right edge`() {
        val rectangle = DikcizGridRectangle(column = GRID_COLUMNS - 1, row = 0, columnSpan = 2, rowSpan = 1)

        assertFalse(DikcizGridLayoutEngine.isWithinBounds(STANDARD_GRID, rectangle))
    }

    @Test
    fun `isWithinBounds rejects a rectangle crossing the bottom edge`() {
        val rectangle = DikcizGridRectangle(column = 0, row = GRID_ROWS - 1, columnSpan = 1, rowSpan = 2)

        assertFalse(DikcizGridLayoutEngine.isWithinBounds(STANDARD_GRID, rectangle))
    }

    @Test
    fun `isWithinBounds accepts a rectangle that exactly fills the page`() {
        val rectangle = DikcizGridRectangle(column = 0, row = 0, columnSpan = GRID_COLUMNS, rowSpan = GRID_ROWS)

        assertTrue(DikcizGridLayoutEngine.isWithinBounds(STANDARD_GRID, rectangle))
    }

    // --- overlaps ---

    @Test
    fun `overlaps is false for two rectangles with no shared cells`() {
        val base = DikcizGridRectangle(column = 1, row = 1, columnSpan = 2, rowSpan = 2)
        val other = DikcizGridRectangle(column = GRID_COLUMNS - 1, row = 0, columnSpan = 1, rowSpan = 1)

        assertFalse(base.overlaps(other))
    }

    @Test
    fun `overlaps is true for a partial overlap from above`() {
        val base = DikcizGridRectangle(column = 1, row = 1, columnSpan = 2, rowSpan = 2)
        val other = DikcizGridRectangle(column = 1, row = 0, columnSpan = 2, rowSpan = 2)

        assertTrue(base.overlaps(other))
    }

    @Test
    fun `overlaps is true for a partial overlap from below`() {
        val base = DikcizGridRectangle(column = 1, row = 1, columnSpan = 2, rowSpan = 2)
        val other = DikcizGridRectangle(column = 1, row = 2, columnSpan = 2, rowSpan = 1)

        assertTrue(base.overlaps(other))
    }

    @Test
    fun `overlaps is true for a partial overlap from the left`() {
        val base = DikcizGridRectangle(column = 1, row = 1, columnSpan = 2, rowSpan = 2)
        val other = DikcizGridRectangle(column = 0, row = 1, columnSpan = 2, rowSpan = 2)

        assertTrue(base.overlaps(other))
    }

    @Test
    fun `overlaps is true for a partial overlap from the right`() {
        val base = DikcizGridRectangle(column = 1, row = 1, columnSpan = 2, rowSpan = 2)
        val other = DikcizGridRectangle(column = 2, row = 1, columnSpan = 2, rowSpan = 2)

        assertTrue(base.overlaps(other))
    }

    @Test
    fun `overlaps is true when one rectangle fully encloses another`() {
        val base = DikcizGridRectangle(column = 1, row = 1, columnSpan = 2, rowSpan = 2)
        val enclosing = DikcizGridRectangle(column = 0, row = 0, columnSpan = GRID_COLUMNS, rowSpan = GRID_ROWS)

        assertTrue(base.overlaps(enclosing))
    }

    @Test
    fun `overlaps is true for identical rectangles`() {
        val base = DikcizGridRectangle(column = 1, row = 1, columnSpan = 2, rowSpan = 2)

        assertTrue(base.overlaps(base.copy()))
    }

    @Test
    fun `overlaps is false for edge-adjacent rectangles`() {
        val base = DikcizGridRectangle(column = 1, row = 1, columnSpan = 2, rowSpan = 2)
        val adjacent = DikcizGridRectangle(column = base.endColumn, row = 1, columnSpan = 1, rowSpan = 1)

        assertFalse(base.overlaps(adjacent))
    }

    // --- findFirstFree ---

    @Test
    fun `findFirstFree returns column 0 row 0 on an empty grid`() {
        val free = DikcizGridLayoutEngine.findFirstFree(STANDARD_GRID, emptyList(), SINGLE_CELL_REQUEST)

        assertEquals(DikcizGridRectangle(column = 0, row = 0, columnSpan = 1, rowSpan = 1), free)
    }

    @Test
    fun `findFirstFree searches in row-major order`() {
        val occupied = listOf(
            DikcizGridRectangle(column = 0, row = 0, columnSpan = 1, rowSpan = 1),
            DikcizGridRectangle(column = 1, row = 0, columnSpan = 1, rowSpan = 1),
        )

        val free = DikcizGridLayoutEngine.findFirstFree(STANDARD_GRID, occupied, SINGLE_CELL_REQUEST)

        // Column-major search would return (0, 1); row-major must finish row 0 first.
        assertEquals(DikcizGridRectangle(column = 2, row = 0, columnSpan = 1, rowSpan = 1), free)
    }

    @Test
    fun `findFirstFree returns null when the requested span is wider than the grid`() {
        val request = DikcizGridRectangle(column = 0, row = 0, columnSpan = GRID_COLUMNS + 1, rowSpan = 1)

        assertNull(DikcizGridLayoutEngine.findFirstFree(STANDARD_GRID, emptyList(), request))
    }

    @Test
    fun `findFirstFree returns null when the requested span is taller than the grid`() {
        val request = DikcizGridRectangle(column = 0, row = 0, columnSpan = 1, rowSpan = GRID_ROWS + 1)

        assertNull(DikcizGridLayoutEngine.findFirstFree(STANDARD_GRID, emptyList(), request))
    }

    @Test
    fun `findFirstFree skips occupied cells`() {
        val occupied = listOf(DikcizGridRectangle(column = 0, row = 0, columnSpan = 1, rowSpan = 1))

        val free = DikcizGridLayoutEngine.findFirstFree(STANDARD_GRID, occupied, SINGLE_CELL_REQUEST)

        assertEquals(DikcizGridRectangle(column = 1, row = 0, columnSpan = 1, rowSpan = 1), free)
    }

    // --- place ---

    @Test
    fun `place with a null request takes the first free single cell`() {
        val occupied = listOf(DikcizGridRectangle(column = 0, row = 0, columnSpan = 1, rowSpan = 1))

        val placement = DikcizGridLayoutEngine.place(STANDARD_GRID, occupied, requested = null)

        val expected = DikcizGridRectangle(column = 1, row = 0, columnSpan = 1, rowSpan = 1)
        assertEquals(DikcizGridPlacement.Placed(expected), placement)
    }

    @Test
    fun `place accepts a chosen free rectangle unchanged`() {
        val occupied = listOf(DikcizGridRectangle(column = 0, row = 0, columnSpan = 1, rowSpan = 1))
        val requested = DikcizGridRectangle(column = 2, row = 1, columnSpan = 1, rowSpan = 1)

        val placement = DikcizGridLayoutEngine.place(STANDARD_GRID, occupied, requested)

        assertEquals(DikcizGridPlacement.Placed(requested), placement)
    }

    @Test
    fun `place rejects a chosen out-of-bounds rectangle with GridBounds`() {
        val requested = DikcizGridRectangle(column = GRID_COLUMNS, row = 0, columnSpan = 1, rowSpan = 1)

        val placement = DikcizGridLayoutEngine.place(STANDARD_GRID, emptyList(), requested)

        assertEquals(DikcizGridPlacement.Rejected(DikcizGridFailure.GridBounds), placement)
    }

    @Test
    fun `place rejects a chosen colliding rectangle with GridCollision when cells remain free`() {
        val occupied = listOf(DikcizGridRectangle(column = 0, row = 0, columnSpan = 1, rowSpan = 1))
        val requested = DikcizGridRectangle(column = 0, row = 0, columnSpan = 1, rowSpan = 1)

        val placement = DikcizGridLayoutEngine.place(STANDARD_GRID, occupied, requested)

        assertEquals(DikcizGridPlacement.Rejected(DikcizGridFailure.GridCollision), placement)
    }

    @Test
    fun `place rejects a chosen colliding rectangle with PageFull when the page is completely full`() {
        val requested = DikcizGridRectangle(column = 0, row = 0, columnSpan = 1, rowSpan = 1)

        val placement = DikcizGridLayoutEngine.place(STANDARD_GRID, FULL_GRID_OCCUPIED, requested)

        assertEquals(DikcizGridPlacement.Rejected(DikcizGridFailure.PageFull), placement)
    }

    // --- placePreferredSpan ---

    @Test
    fun `placePreferredSpan places the preferred span when it fits`() {
        val preferred = DikcizGridSpan(columnSpan = 2, rowSpan = 2)

        val placement = DikcizGridLayoutEngine.placePreferredSpan(STANDARD_GRID, emptyList(), preferred)

        val expected = DikcizGridRectangle(column = 0, row = 0, columnSpan = 2, rowSpan = 2)
        assertEquals(DikcizGridPlacement.Placed(expected), placement)
    }

    @Test
    fun `placePreferredSpan falls back to a free single cell when the preferred span fits nowhere`() {
        val preferred = DikcizGridSpan(columnSpan = 2, rowSpan = 2)

        val placement = DikcizGridLayoutEngine.placePreferredSpan(STANDARD_GRID, ONE_FREE_CELL_OCCUPIED, preferred)

        assertEquals(DikcizGridPlacement.Placed(ONLY_FREE_CELL), placement)
    }

    @Test
    fun `placePreferredSpan rejects with PageFull when nothing is free`() {
        val preferred = DikcizGridSpan(columnSpan = 1, rowSpan = 1)

        val placement = DikcizGridLayoutEngine.placePreferredSpan(STANDARD_GRID, FULL_GRID_OCCUPIED, preferred)

        assertEquals(DikcizGridPlacement.Rejected(DikcizGridFailure.PageFull), placement)
    }

    // --- isPageFull ---

    @Test
    fun `isPageFull is true when no single cell is free`() {
        assertTrue(DikcizGridLayoutEngine.isPageFull(STANDARD_GRID, FULL_GRID_OCCUPIED))
    }

    @Test
    fun `isPageFull is false when exactly one cell is free`() {
        assertFalse(DikcizGridLayoutEngine.isPageFull(STANDARD_GRID, ONE_FREE_CELL_OCCUPIED))
    }

    // --- moveCandidate ---

    @Test
    fun `moveCandidate succeeds when moving onto its own cells`() {
        val current = DikcizGridRectangle(column = 0, row = 0, columnSpan = 2, rowSpan = 2)

        val placement = DikcizGridLayoutEngine.moveCandidate(
            grid = STANDARD_GRID,
            occupied = listOf(current),
            current = current,
            target = current,
        )

        assertEquals(DikcizGridPlacement.Placed(current), placement)
    }

    @Test
    fun `moveCandidate rejects moving onto another item with GridCollision`() {
        val current = DikcizGridRectangle(column = 0, row = 0, columnSpan = 1, rowSpan = 1)
        val other = DikcizGridRectangle(column = 1, row = 0, columnSpan = 1, rowSpan = 1)

        val placement = DikcizGridLayoutEngine.moveCandidate(
            grid = STANDARD_GRID,
            occupied = listOf(current, other),
            current = current,
            target = other,
        )

        assertEquals(DikcizGridPlacement.Rejected(DikcizGridFailure.GridCollision), placement)
    }

    @Test
    fun `moveCandidate rejects moving off the grid with GridBounds`() {
        val current = DikcizGridRectangle(column = 0, row = 0, columnSpan = 1, rowSpan = 1)
        val target = DikcizGridRectangle(column = GRID_COLUMNS, row = 0, columnSpan = 1, rowSpan = 1)

        val placement = DikcizGridLayoutEngine.moveCandidate(
            grid = STANDARD_GRID,
            occupied = listOf(current),
            current = current,
            target = target,
        )

        assertEquals(DikcizGridPlacement.Rejected(DikcizGridFailure.GridBounds), placement)
    }

    // --- resizeCandidate ---

    @Test
    fun `resizeCandidate succeeds when resizing onto its own cells`() {
        val current = DikcizGridRectangle(column = 0, row = 0, columnSpan = 1, rowSpan = 1)
        val target = DikcizGridRectangle(column = 0, row = 0, columnSpan = 2, rowSpan = 1)

        val placement = DikcizGridLayoutEngine.resizeCandidate(
            grid = STANDARD_GRID,
            occupied = listOf(current),
            current = current,
            target = target,
        )

        assertEquals(DikcizGridPlacement.Placed(target), placement)
    }

    @Test
    fun `resizeCandidate rejects resizing onto another item with GridCollision`() {
        val current = DikcizGridRectangle(column = 0, row = 0, columnSpan = 1, rowSpan = 1)
        val other = DikcizGridRectangle(column = 1, row = 0, columnSpan = 1, rowSpan = 1)
        val target = DikcizGridRectangle(column = 0, row = 0, columnSpan = 2, rowSpan = 1)

        val placement = DikcizGridLayoutEngine.resizeCandidate(
            grid = STANDARD_GRID,
            occupied = listOf(current, other),
            current = current,
            target = target,
        )

        assertEquals(DikcizGridPlacement.Rejected(DikcizGridFailure.GridCollision), placement)
    }

    @Test
    fun `resizeCandidate rejects resizing off the grid with GridBounds`() {
        val current = DikcizGridRectangle(column = 0, row = 0, columnSpan = 1, rowSpan = 1)
        val target = DikcizGridRectangle(column = 0, row = 0, columnSpan = GRID_COLUMNS + 1, rowSpan = 1)

        val placement = DikcizGridLayoutEngine.resizeCandidate(
            grid = STANDARD_GRID,
            occupied = listOf(current),
            current = current,
            target = target,
        )

        assertEquals(DikcizGridPlacement.Rejected(DikcizGridFailure.GridBounds), placement)
    }

    // --- pixelBounds ---

    @Test
    fun `pixelBounds lands the last column's right edge and last row's bottom edge on the viewport inset`() {
        val lastCell = DikcizGridRectangle(
            column = GRID_COLUMNS - 1,
            row = GRID_ROWS - 1,
            columnSpan = 1,
            rowSpan = 1,
        )

        val bounds = DikcizGridLayoutEngine.pixelBounds(
            grid = STANDARD_GRID,
            rectangle = lastCell,
            viewportWidthPixels = VIEWPORT_WIDTH_PIXELS,
            viewportHeightPixels = VIEWPORT_HEIGHT_PIXELS,
            densityScale = DENSITY_SCALE,
        )

        assertEquals(VIEWPORT_WIDTH_PIXELS - OUTER_PADDING_PIXELS, bounds.right)
        assertEquals(VIEWPORT_HEIGHT_PIXELS - OUTER_PADDING_PIXELS, bounds.bottom)
    }

    @Test
    fun `pixelBounds separates two horizontally adjacent cells by exactly the gap`() {
        val firstCell = DikcizGridRectangle(column = 0, row = 0, columnSpan = 1, rowSpan = 1)
        val secondCell = DikcizGridRectangle(column = 1, row = 0, columnSpan = 1, rowSpan = 1)

        val firstBounds = DikcizGridLayoutEngine.pixelBounds(
            STANDARD_GRID,
            firstCell,
            VIEWPORT_WIDTH_PIXELS,
            VIEWPORT_HEIGHT_PIXELS,
            DENSITY_SCALE,
        )
        val secondBounds = DikcizGridLayoutEngine.pixelBounds(
            STANDARD_GRID,
            secondCell,
            VIEWPORT_WIDTH_PIXELS,
            VIEWPORT_HEIGHT_PIXELS,
            DENSITY_SCALE,
        )

        assertEquals(GAP_PIXELS, secondBounds.left - firstBounds.right)
    }

    @Test
    fun `pixelBounds of a full-page rectangle spans the whole usable area`() {
        val fullPage = DikcizGridRectangle(column = 0, row = 0, columnSpan = GRID_COLUMNS, rowSpan = GRID_ROWS)

        val bounds = DikcizGridLayoutEngine.pixelBounds(
            STANDARD_GRID,
            fullPage,
            VIEWPORT_WIDTH_PIXELS,
            VIEWPORT_HEIGHT_PIXELS,
            DENSITY_SCALE,
        )

        assertEquals(OUTER_PADDING_PIXELS, bounds.left)
        assertEquals(OUTER_PADDING_PIXELS, bounds.top)
        assertEquals(VIEWPORT_WIDTH_PIXELS - OUTER_PADDING_PIXELS, bounds.right)
        assertEquals(VIEWPORT_HEIGHT_PIXELS - OUTER_PADDING_PIXELS, bounds.bottom)
    }

    @Test
    fun `pixelBounds does not crash on a zero or negative viewport`() {
        val cell = DikcizGridRectangle(column = 0, row = 0, columnSpan = 1, rowSpan = 1)

        val bounds = DikcizGridLayoutEngine.pixelBounds(
            grid = STANDARD_GRID,
            rectangle = cell,
            viewportWidthPixels = 0,
            viewportHeightPixels = -10,
            densityScale = DENSITY_SCALE,
        )

        assertEquals(OUTER_PADDING_PIXELS, bounds.left)
        assertEquals(OUTER_PADDING_PIXELS, bounds.top)
        assertEquals(OUTER_PADDING_PIXELS, bounds.right)
        assertEquals(OUTER_PADDING_PIXELS, bounds.bottom)
    }

    // --- cellAtPixel ---

    @Test
    fun `cellAtPixel maps a pixel inside a known cell to that cell`() {
        val insideKnownCellX = 250
        val insideKnownCellY = 150

        val cell = DikcizGridLayoutEngine.cellAtPixel(
            grid = STANDARD_GRID,
            viewportWidthPixels = VIEWPORT_WIDTH_PIXELS,
            viewportHeightPixels = VIEWPORT_HEIGHT_PIXELS,
            densityScale = DENSITY_SCALE,
            xPixels = insideKnownCellX,
            yPixels = insideKnownCellY,
        )

        assertEquals(2 to 1, cell)
    }

    @Test
    fun `cellAtPixel clamps a pixel before the content to the first index`() {
        val beforeContent = 0

        val cell = DikcizGridLayoutEngine.cellAtPixel(
            grid = STANDARD_GRID,
            viewportWidthPixels = VIEWPORT_WIDTH_PIXELS,
            viewportHeightPixels = VIEWPORT_HEIGHT_PIXELS,
            densityScale = DENSITY_SCALE,
            xPixels = beforeContent,
            yPixels = beforeContent,
        )

        assertEquals(0 to 0, cell)
    }

    @Test
    fun `cellAtPixel clamps a pixel past the end to the last index`() {
        val pastTheEnd = 1000

        val cell = DikcizGridLayoutEngine.cellAtPixel(
            grid = STANDARD_GRID,
            viewportWidthPixels = VIEWPORT_WIDTH_PIXELS,
            viewportHeightPixels = VIEWPORT_HEIGHT_PIXELS,
            densityScale = DENSITY_SCALE,
            xPixels = pastTheEnd,
            yPixels = pastTheEnd,
        )

        assertEquals((GRID_COLUMNS - 1) to (GRID_ROWS - 1), cell)
    }
}
