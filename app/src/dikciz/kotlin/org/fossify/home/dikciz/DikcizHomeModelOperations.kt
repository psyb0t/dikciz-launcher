package org.fossify.home.dikciz

import java.util.Locale

internal fun HomeConfiguration.selectedPage(): HomePage? {
    return pages.firstOrNull { it.id == selectedPageID }
}

internal fun HomeConfiguration.pagesByColumn(): List<List<HomePage>> {
    return pages.groupBy { page -> page.position.column }
        .toSortedMap()
        .values
        .map { pages -> pages.sortedBy { page -> page.position.row } }
}

internal fun HomeConfiguration.pagesInColumn(column: Int): List<HomePage> {
    return pages.filter { page -> page.position.column == column }
        .sortedBy { page -> page.position.row }
}

internal fun HomeConfiguration.columnIndex(page: HomePage): Int {
    return pagesByColumn().indexOfFirst { pages -> pages.any { it.id == page.id } }
        .coerceAtLeast(MINIMUM_PAGE_INDEX)
}

internal fun HomeConfiguration.rowIndex(page: HomePage): Int {
    return pagesInColumn(page.position.column).indexOfFirst { candidate -> candidate.id == page.id }
        .coerceAtLeast(MINIMUM_PAGE_INDEX)
}

/** One new page placed at a rail extremity, together with the pages it displaced. */
internal data class PageRailInsertion(
    val position: PagePosition,
    val pages: List<HomePage>,
)

/**
 * Where a rail long press puts a new page, and how the existing pages move for it.
 *
 * A horizontal insertion lands beyond the first or last column of the whole home, and a
 * vertical insertion beyond the first or last row of the selected page's column. Neither
 * one looks at where the selected page sits inside that rail, so holding the rail always
 * extends the home at its extremity rather than splitting it next to the current page.
 *
 * The extremity is reached by shifting the rail rather than by using a negative
 * coordinate, so page positions stay non-negative and contiguous.
 */
internal fun HomeConfiguration.pageRailInsertion(
    selectedPage: HomePage,
    axis: PageNavigationAxis,
    side: PageInsertionSide,
): PageRailInsertion {
    return when (axis) {
        PageNavigationAxis.Horizontal -> horizontalRailInsertion(side)
        PageNavigationAxis.Vertical -> verticalRailInsertion(selectedPage.position.column, side)
    }
}

/**
 * The single existing page one step away from this page along the given direction.
 *
 * A move is a swap with that exact neighbour, so a direction whose coordinate holds no
 * page has no move to make.
 */
internal fun HomeConfiguration.adjacentPage(
    page: HomePage,
    direction: PageMoveDirection,
): HomePage? {
    val neighbouringPosition = PagePosition(
        column = page.position.column + direction.columnOffset,
        row = page.position.row + direction.rowOffset,
    )
    return pages.firstOrNull { candidate -> candidate.position == neighbouringPosition }
}

/**
 * The same pages with two of them exchanging coordinates.
 *
 * Only `position` changes, so page IDs, titles, locks, widgets, and every ID-based
 * reference such as the home and selected page survive the move untouched.
 */
internal fun HomeConfiguration.withExchangedPagePositions(
    page: HomePage,
    neighbour: HomePage,
): HomeConfiguration {
    return copy(
        pages = pages.map { candidate ->
            when (candidate.id) {
                page.id -> candidate.copy(position = neighbour.position)
                neighbour.id -> candidate.copy(position = page.position)
                else -> candidate
            }
        },
    )
}

private fun HomeConfiguration.horizontalRailInsertion(
    side: PageInsertionSide,
): PageRailInsertion {
    if (side == PageInsertionSide.After) {
        val lastColumn = pages.maxOf { page -> page.position.column }
        return PageRailInsertion(
            position = PagePosition(lastColumn + NEXT_PAGE_OFFSET, MINIMUM_PAGE_INDEX),
            pages = pages,
        )
    }
    val firstColumn = pages.minOf { page -> page.position.column }
    return PageRailInsertion(
        position = PagePosition(firstColumn, MINIMUM_PAGE_INDEX),
        pages = pages.map { page ->
            page.copy(position = page.position.copy(column = page.position.column + NEXT_PAGE_OFFSET))
        },
    )
}

private fun HomeConfiguration.verticalRailInsertion(
    column: Int,
    side: PageInsertionSide,
): PageRailInsertion {
    val columnRows = pagesInColumn(column).map { page -> page.position.row }
    if (side == PageInsertionSide.After) {
        val lastRow = columnRows.max()
        return PageRailInsertion(
            position = PagePosition(column, lastRow + NEXT_PAGE_OFFSET),
            pages = pages,
        )
    }
    val firstRow = columnRows.min()
    return PageRailInsertion(
        position = PagePosition(column, firstRow),
        pages = pages.map { page ->
            if (page.position.column != column) {
                page
            } else {
                page.copy(position = page.position.copy(row = page.position.row + NEXT_PAGE_OFFSET))
            }
        },
    )
}

internal fun List<HomePage>.pageDeletionAxis(page: HomePage): PageNavigationAxis {
    return if (any { candidate -> candidate.position.column == page.position.column && candidate.id != page.id }) {
        PageNavigationAxis.Vertical
    } else {
        PageNavigationAxis.Horizontal
    }
}

internal fun List<HomePage>.compactPageCoordinatesAfterDeletion(
    deletedPage: HomePage,
    axis: PageNavigationAxis,
): List<HomePage> {
    return map { page ->
        when {
            axis == PageNavigationAxis.Horizontal &&
                page.position.column > deletedPage.position.column -> {
                page.copy(position = page.position.copy(column = page.position.column - NEXT_PAGE_OFFSET))
            }

            axis == PageNavigationAxis.Vertical &&
                page.position.column == deletedPage.position.column &&
                page.position.row > deletedPage.position.row -> {
                page.copy(position = page.position.copy(row = page.position.row - NEXT_PAGE_OFFSET))
            }

            else -> page
        }
    }
}

internal fun HomeConfiguration.providerAppWidgetIDs(): Set<Int> {
    return pages.flatMap { page ->
        page.widgets.filterIsInstance<ProviderHomeWidget>().map { widget -> widget.appWidgetID }
    }.toSet()
}

internal fun HomePage.findWidget(widgetID: String): HomeWidget? {
    return widgets.firstOrNull { widget -> widget.id == widgetID }
}

internal fun HomePage.widgetLockState(widgetID: String): Boolean? {
    return findWidget(widgetID)?.locked
}

internal fun HomePage.referenceLocation(): String {
    return String.format(
        Locale.ROOT,
        PAGE_REFERENCE_LOCATION_FORMAT,
        position.column + FIRST_PAGE_REFERENCE_INDEX,
        position.row + FIRST_PAGE_REFERENCE_INDEX,
    )
}

internal fun HomeWidget.typeReferenceValue(): String {
    return when (this) {
        is HtmlHomeWidget -> WIDGET_TYPE_HTML
        is AppHomeWidget -> WIDGET_TYPE_APP
        is AppGroupHomeWidget -> WIDGET_TYPE_APP_GROUP
        is ProviderHomeWidget -> WIDGET_TYPE_PROVIDER
        is ScriptDashboardHomeWidget -> WIDGET_TYPE_SCRIPT_DASHBOARD
    }
}

internal fun HomeConfiguration.replacePage(
    pageID: String,
    transform: (HomePage) -> HomePage,
): HomeConfiguration {
    return copy(pages = pages.map { page ->
        if (page.id == pageID) transform(page) else page
    })
}

internal fun HomePage.replaceWidget(
    widgetID: String,
    transform: (HomeWidget) -> HomeWidget,
): HomePage {
    return copy(widgets = widgets.map { widget ->
        if (widget.id == widgetID) transform(widget) else widget
    })
}

internal fun HomePage.widgetsInPageOrder(): List<HomeWidget> = widgets

/** The cells every top-level item on this page owns, optionally excluding one item. */
internal fun HomePage.occupiedCells(excludedWidgetID: String? = null): List<DikcizGridRectangle> {
    return widgets.filterNot { widget -> widget.id == excludedWidgetID }
        .map(HomeWidget::cell)
}

internal fun HomePage.moveWidget(
    widgetID: String,
    movedCell: DikcizGridRectangle,
): HomePage {
    return replaceWidget(widgetID) { widget -> widget.withCell(movedCell) }
}

internal fun HomeWidget.withCell(cell: DikcizGridRectangle): HomeWidget {
    return when (this) {
        is HtmlHomeWidget -> copy(cell = cell)
        is AppHomeWidget -> copy(cell = cell)
        is AppGroupHomeWidget -> copy(cell = cell)
        is ProviderHomeWidget -> copy(cell = cell)
        is ScriptDashboardHomeWidget -> copy(cell = cell)
    }
}

internal fun HomeWidget.withLocked(isLocked: Boolean): HomeWidget {
    return when (this) {
        is HtmlHomeWidget -> copy(locked = isLocked)
        is AppHomeWidget -> copy(locked = isLocked)
        is AppGroupHomeWidget -> copy(locked = isLocked)
        is ProviderHomeWidget -> copy(locked = isLocked)
        is ScriptDashboardHomeWidget -> copy(locked = isLocked)
    }
}

internal fun HomeWidget.withStyle(style: DikcizStyle?): HomeWidget {
    return when (this) {
        is HtmlHomeWidget -> copy(style = style)
        is AppHomeWidget -> copy(style = style)
        is AppGroupHomeWidget -> copy(style = style)
        is ProviderHomeWidget -> copy(style = style)
        is ScriptDashboardHomeWidget -> copy(style = style)
    }
}

internal fun HomeWidget.withRequestedLocks(requestedLocks: Map<String, Boolean>): HomeWidget {
    val isLocked = requestedLocks[id] ?: locked
    return when (this) {
        is HtmlHomeWidget -> copy(locked = isLocked)
        is AppHomeWidget -> copy(locked = isLocked)
        is AppGroupHomeWidget -> copy(locked = isLocked)
        is ProviderHomeWidget -> copy(locked = isLocked)
        is ScriptDashboardHomeWidget -> copy(locked = isLocked)
    }
}

/**
 * Whether dragging [source] onto [target] should trade their cells.
 *
 * Anything that cannot merge trades places instead, which is what dropping a
 * widget on an occupied cell means in a launcher. Both must cover the same
 * number of cells, otherwise the trade could push either widget out of the grid
 * or into a neighbour, and that drop stays refused.
 */
internal fun canSwapWidgetCells(source: HomeWidget, target: HomeWidget): Boolean {
    if (groupedComponents(source, target) != null) {
        return false
    }
    return source.cell.columnSpan == target.cell.columnSpan &&
        source.cell.rowSpan == target.cell.rowSpan
}

/** Trades the cells of two widgets on this page, leaving everything else alone. */
internal fun HomePage.withSwappedWidgetCells(firstID: String, secondID: String): HomePage {
    val first = findWidget(firstID) ?: return this
    val second = findWidget(secondID) ?: return this
    return copy(
        widgets = widgets.map { widget ->
            when (widget.id) {
                firstID -> widget.withCell(second.cell)
                secondID -> widget.withCell(first.cell)
                else -> widget
            }
        },
    )
}

/** The widget whose cell covers the given column and row, ignoring [excludedWidgetID]. */
internal fun HomePage.widgetAtCell(
    column: Int,
    row: Int,
    excludedWidgetID: String,
): HomeWidget? {
    return widgets.firstOrNull { widget ->
        widget.id != excludedWidgetID &&
            column >= widget.cell.column &&
            column < widget.cell.column + widget.cell.columnSpan &&
            row >= widget.cell.row &&
            row < widget.cell.row + widget.cell.rowSpan
    }
}

/**
 * The app components a drop would put in one group, target first, in drop order.
 *
 * Returns null when the pair cannot be grouped. Only an app tile is a valid
 * source, and only an app tile or an existing group is a valid target, because
 * every other widget owns its own content and has nothing to merge.
 */
internal fun groupedComponents(source: HomeWidget, target: HomeWidget): List<String>? {
    if (source !is AppHomeWidget) {
        return null
    }
    val existing = when (target) {
        is AppHomeWidget -> listOf(target.component)
        is AppGroupHomeWidget -> target.components
        else -> return null
    }
    if (source.component in existing) {
        return existing
    }
    val combined = existing + source.component
    if (combined.size > AppGroupLimits.MAXIMUM_COMPONENTS) {
        return null
    }
    return combined
}

/**
 * Replaces [targetID] with a group of [components] and drops [sourceID] from the page.
 *
 * The group keeps the target's cell, style, and lock, so a drop never
 * moves the tile that was already there. [groupID] is used only when the target
 * was a plain app tile and a new group has to be minted.
 */
internal fun HomePage.withCombinedAppGroup(
    sourceID: String,
    targetID: String,
    groupID: String,
    groupTitle: String,
    components: List<String>,
): HomePage {
    val target = findWidget(targetID) ?: return this
    val group = when (target) {
        is AppGroupHomeWidget -> target.copy(components = components)
        is AppHomeWidget -> AppGroupHomeWidget(
            id = groupID,
            title = groupTitle,
            components = components,
            enabled = target.enabled,
            cell = target.cell,
            style = target.style,
            locked = target.locked,
        )
        else -> return this
    }
    return copy(
        widgets = widgets.mapNotNull { widget ->
            when (widget.id) {
                sourceID -> null
                targetID -> group
                else -> widget
            }
        },
    )
}

/**
 * Removes one component from a group, collapsing the group when one is left.
 *
 * A group of one is just an app tile, so the last removal replaces the group
 * with an [AppHomeWidget] in the same cell rather than leaving a group that
 * opens a popup holding a single icon. The widget keeps its ID, so a script or
 * an HTML widget addressing that cell keeps working across the collapse.
 */
internal fun HomePage.withoutAppGroupMember(
    groupID: String,
    component: String,
): HomePage {
    val group = findWidget(groupID) as? AppGroupHomeWidget ?: return this
    val remaining = group.components.filterNot { candidate -> candidate == component }
    if (remaining.isEmpty() || remaining.size == group.components.size) {
        return this
    }
    if (remaining.size > SINGLE_APP_GROUP_MEMBER) {
        return replaceWidget(groupID) { group.copy(components = remaining) }
    }
    return replaceWidget(groupID) {
        AppHomeWidget(
            id = groupID,
            title = group.title,
            component = remaining.first(),
            displayStyle = AppWidgetDisplayStyle.IconWithLabel,
            enabled = group.enabled,
            cell = group.cell,
            style = group.style,
            locked = group.locked,
        )
    }
}

private const val SINGLE_APP_GROUP_MEMBER = 1
private const val FIRST_PAGE_REFERENCE_INDEX = 1
private const val MINIMUM_PAGE_INDEX = 0
private const val NEXT_PAGE_OFFSET = 1
private const val PAGE_REFERENCE_LOCATION_FORMAT = "%dH%dV"
private const val WIDGET_TYPE_APP = "app"
private const val WIDGET_TYPE_APP_GROUP = "appGroup"
private const val WIDGET_TYPE_HTML = "html"
private const val WIDGET_TYPE_PROVIDER = "provider"
private const val WIDGET_TYPE_SCRIPT_DASHBOARD = "scriptDashboard"
