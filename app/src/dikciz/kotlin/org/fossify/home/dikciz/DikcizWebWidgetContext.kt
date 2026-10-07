package org.fossify.home.dikciz

import org.json.JSONObject

internal object DikcizWebWidgetContext {
    fun selfWidget(
        configuration: HomeConfiguration,
        page: HomePage,
        widget: HtmlHomeWidget,
    ): JSONObject {
        return widgetDocument(page, widget)
            .put(FIELD_PAGE, pageDocument(page))
            .put(FIELD_PAGES, pageMap(configuration))
    }

    fun pageMap(configuration: HomeConfiguration): JSONObject {
        val pages = JSONObject()
        configuration.pages.forEach { page ->
            val document = pageDocument(page)
            pages.put(page.id, document)
            pages.put(pageAddress(page), JSONObject(document.toString()))
        }
        val homePage = configuration.pages.first { page -> page.id == configuration.homePageID }
        pages.put(HOME_PAGE_ALIAS, JSONObject(pageDocument(homePage).toString()))
        return pages
    }

    fun widgetDocument(
        page: HomePage,
        widget: HomeWidget,
    ): JSONObject {
        return JSONObject()
            .put(FIELD_ADDRESS, page.scriptWidgetAddress(widget).persistedValue)
            .put(FIELD_ENABLED, widget.enabled)
            .put(FIELD_ID, widget.id)
            .put(FIELD_CELL, cellDocument(widget.cell))
            .put(FIELD_TITLE, widget.title)
            .put(FIELD_TYPE, widget.typeReferenceValue())
            .also { document ->
                when (widget) {
                    is HtmlHomeWidget -> document.put(FIELD_STATE, JSONObject(widget.state.toString()))
                    is AppHomeWidget,
                    is AppGroupHomeWidget,
                    is ProviderHomeWidget,
                    is ScriptDashboardHomeWidget,
                    -> Unit
                }
            }
    }

    private fun pageDocument(page: HomePage): JSONObject {
        val widgets = JSONObject()
        page.widgets.forEach { widget ->
            val document = widgetDocument(page, widget)
            widgets.put(widget.id, document)
            widgets.put(page.scriptWidgetAddress(widget).persistedValue, JSONObject(document.toString()))
        }
        return JSONObject()
            .put(FIELD_ADDRESS, pageAddress(page))
            .put(FIELD_ID, page.id)
            .put(FIELD_TITLE, page.title)
            .put(
                FIELD_POSITION,
                JSONObject()
                    .put(FIELD_HORIZONTAL, page.position.column + FIRST_PAGE_COORDINATE)
                    .put(FIELD_VERTICAL, page.position.row + FIRST_PAGE_COORDINATE),
            )
            .put(FIELD_WIDGETS, widgets)
    }

    private fun pageAddress(page: HomePage): String {
        return "${page.position.column + FIRST_PAGE_COORDINATE}H${page.position.row + FIRST_PAGE_COORDINATE}V"
    }

    /**
     * The widget's stable logical placement. Device pixels are render-time only and are
     * deliberately absent, so a widget author never persists a coordinate that rotation or
     * a different screen would invalidate.
     */
    private fun cellDocument(cell: DikcizGridRectangle): JSONObject {
        return JSONObject()
            .put(FIELD_COLUMN, cell.column)
            .put(FIELD_ROW, cell.row)
            .put(FIELD_COLUMN_SPAN, cell.columnSpan)
            .put(FIELD_ROW_SPAN, cell.rowSpan)
    }

    private const val FIELD_ADDRESS = "address"
    private const val FIELD_CELL = "cell"
    private const val FIELD_COLUMN = "column"
    private const val FIELD_COLUMN_SPAN = "columnSpan"
    private const val FIELD_ENABLED = "enabled"
    private const val FIELD_HORIZONTAL = "horizontal"
    private const val FIELD_ID = "id"
    private const val FIELD_PAGE = "page"
    private const val FIELD_PAGES = "pages"
    private const val FIELD_POSITION = "position"
    private const val FIELD_ROW = "row"
    private const val FIELD_ROW_SPAN = "rowSpan"
    private const val FIELD_STATE = "state"
    private const val FIELD_TITLE = "title"
    private const val FIELD_TYPE = "type"
    private const val FIELD_VERTICAL = "vertical"
    private const val FIELD_WIDGETS = "widgets"
    private const val FIRST_PAGE_COORDINATE = 1
    private const val HOME_PAGE_ALIAS = "home"
}
