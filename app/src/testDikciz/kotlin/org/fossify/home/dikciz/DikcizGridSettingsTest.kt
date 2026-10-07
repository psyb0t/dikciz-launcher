package org.fossify.home.dikciz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val WIDE_COLUMNS = 4
private const val WIDE_ROWS = 6
private const val NARROW_COLUMNS = 2
private const val NARROW_ROWS = 2
private const val GRID_GAP_DP = 8
private const val GRID_OUTER_PADDING_DP = 12

private val WIDE_GRID = DikcizNativeGrid(
    columns = WIDE_COLUMNS,
    rows = WIDE_ROWS,
    gapDP = GRID_GAP_DP,
    outerPaddingDP = GRID_OUTER_PADDING_DP,
)

private val NARROW_GRID = DikcizNativeGrid(
    columns = NARROW_COLUMNS,
    rows = NARROW_ROWS,
    gapDP = GRID_GAP_DP,
    outerPaddingDP = GRID_OUTER_PADDING_DP,
)

private const val FIRST_WIDGET_ID = "welcome"
private const val SECOND_WIDGET_ID = "focus"

private fun item(id: String, column: Int, row: Int, columnSpan: Int, rowSpan: Int) =
    DikcizGridItem(id, DikcizGridRectangle(column, row, columnSpan, rowSpan))

class DikcizGridSettingsTest {
    @Test
    fun `no conflict when every item fits the grid`() {
        val items = listOf(
            item(FIRST_WIDGET_ID, column = 0, row = 0, columnSpan = 2, rowSpan = 1),
            item(SECOND_WIDGET_ID, column = 2, row = 0, columnSpan = 2, rowSpan = 1),
        )
        assertNull(DikcizGridLayoutEngine.firstConflict(WIDE_GRID, items))
    }

    @Test
    fun `no conflict for an empty page`() {
        assertNull(DikcizGridLayoutEngine.firstConflict(NARROW_GRID, emptyList()))
    }

    @Test
    fun `shrinking the grid reports the item that leaves it`() {
        val items = listOf(
            item(FIRST_WIDGET_ID, column = 0, row = 0, columnSpan = 1, rowSpan = 1),
            item(SECOND_WIDGET_ID, column = 2, row = 0, columnSpan = 2, rowSpan = 1),
        )
        val conflict = DikcizGridLayoutEngine.firstConflict(NARROW_GRID, items)
        assertEquals(DikcizGridConflict(SECOND_WIDGET_ID, DikcizGridFailure.GridBounds), conflict)
    }

    @Test
    fun `a row span past the last row reports grid bounds`() {
        val items = listOf(item(FIRST_WIDGET_ID, column = 0, row = 1, columnSpan = 1, rowSpan = 2))
        val conflict = DikcizGridLayoutEngine.firstConflict(NARROW_GRID, items)
        assertEquals(DikcizGridConflict(FIRST_WIDGET_ID, DikcizGridFailure.GridBounds), conflict)
    }

    @Test
    fun `overlapping items report grid collision`() {
        val items = listOf(
            item(FIRST_WIDGET_ID, column = 0, row = 0, columnSpan = 2, rowSpan = 2),
            item(SECOND_WIDGET_ID, column = 1, row = 1, columnSpan = 2, rowSpan = 2),
        )
        val conflict = DikcizGridLayoutEngine.firstConflict(WIDE_GRID, items)
        assertEquals(DikcizGridConflict(SECOND_WIDGET_ID, DikcizGridFailure.GridCollision), conflict)
    }

    @Test
    fun `the first offending item in order is reported`() {
        val items = listOf(
            item(FIRST_WIDGET_ID, column = 3, row = 0, columnSpan = 1, rowSpan = 1),
            item(SECOND_WIDGET_ID, column = 3, row = 1, columnSpan = 1, rowSpan = 1),
        )
        val conflict = DikcizGridLayoutEngine.firstConflict(NARROW_GRID, items)
        assertEquals(DikcizGridConflict(FIRST_WIDGET_ID, DikcizGridFailure.GridBounds), conflict)
    }

    @Test
    fun `grid setting round trips every field`() {
        DikcizGridSetting.entries.forEach { setting ->
            val written = setting.write(WIDE_GRID, setting.minimum)
            assertEquals(setting.minimum, setting.read(written))
        }
    }

    @Test
    fun `grid setting resolves from its persisted value`() {
        DikcizGridSetting.entries.forEach { setting ->
            assertEquals(setting, DikcizGridSetting.fromPersistedValue(setting.persistedValue))
        }
    }

    @Test
    fun `an unknown grid setting value resolves to null`() {
        assertNull(DikcizGridSetting.fromPersistedValue("nope"))
    }
}
