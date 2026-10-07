package org.fossify.home.dikciz

import org.json.JSONObject

internal data class DikcizScriptWidgetAddress(
    val horizontalPage: Int,
    val verticalPage: Int,
    val widgetID: String,
) {
    val persistedValue: String = "${horizontalPage}H${verticalPage}V-${widgetID}"

    companion object {
        fun parse(value: String): DikcizScriptWidgetAddress? {
            val match = ADDRESS_PATTERN.matchEntire(value) ?: return null
            val horizontalPage = match.groupValues[1].toIntOrNull() ?: return null
            val verticalPage = match.groupValues[2].toIntOrNull() ?: return null
            if (horizontalPage <= 0 || verticalPage <= 0) {
                return null
            }
            return DikcizScriptWidgetAddress(
                horizontalPage = horizontalPage,
                verticalPage = verticalPage,
                widgetID = match.groupValues[3],
            )
        }

        private val ADDRESS_PATTERN = Regex(
            "^([1-9][0-9]*)H([1-9][0-9]*)V-([A-Za-z][A-Za-z0-9_.-]*)$",
        )
    }
}

internal data class DikcizScriptWidgetTarget(
    val pageID: String,
    val widgetID: String,
)

/**
 * The page half of a widget address, for example `1H2V`.
 *
 * Scripts, HTML, WebSocket and MCP already name a page with this prefix, so a
 * native surface showing it prints the same text the owner types back.
 */
internal fun HomePage.scriptPageAddress(): String {
    val horizontalPage = position.column + FIRST_SCRIPT_PAGE_COORDINATE
    val verticalPage = position.row + FIRST_SCRIPT_PAGE_COORDINATE
    return "${horizontalPage}H${verticalPage}V"
}

internal fun HomePage.scriptWidgetAddress(widget: HomeWidget): DikcizScriptWidgetAddress {
    return DikcizScriptWidgetAddress(
        horizontalPage = position.column + FIRST_SCRIPT_PAGE_COORDINATE,
        verticalPage = position.row + FIRST_SCRIPT_PAGE_COORDINATE,
        widgetID = widget.id,
    )
}

internal fun HomeConfiguration.scriptWidgetTarget(address: String): DikcizScriptWidgetTarget? {
    val parsedAddress = DikcizScriptWidgetAddress.parse(address) ?: return null
    val page = pages.firstOrNull { candidate ->
        candidate.position.column + FIRST_SCRIPT_PAGE_COORDINATE == parsedAddress.horizontalPage &&
            candidate.position.row + FIRST_SCRIPT_PAGE_COORDINATE == parsedAddress.verticalPage
    } ?: return null
    val widget = page.widgets.firstOrNull { candidate -> candidate.id == parsedAddress.widgetID } ?: return null
    return DikcizScriptWidgetTarget(page.id, widget.id)
}

internal fun HomeConfiguration.scriptWidgetContext(): JSONObject {
    return JSONObject().apply {
        pages.forEach { page ->
            page.widgets.forEach { widget ->
                val address = page.scriptWidgetAddress(widget).persistedValue
                put(
                    address,
                    JSONObject()
                        .put(FIELD_ADDRESS, address)
                        .put(FIELD_ENABLED, widget.enabled)
                        .put(FIELD_ID, widget.id)
                        .put(FIELD_TITLE, widget.title)
                        .put(FIELD_TYPE, widget.typeReferenceValue())
                        .also { snapshot ->
                            when (widget) {
                                is HtmlHomeWidget -> snapshot.put(FIELD_STATE, JSONObject(widget.state.toString()))
                                is AppHomeWidget,
                                is AppGroupHomeWidget,
                                is ProviderHomeWidget,
                                is ScriptDashboardHomeWidget,
                                -> Unit
                            }
                        },
                )
            }
        }
    }
}

private const val FIRST_SCRIPT_PAGE_COORDINATE = 1
private const val FIELD_ADDRESS = "address"
private const val FIELD_ENABLED = "enabled"
private const val FIELD_ID = "id"
private const val FIELD_STATE = "state"
private const val FIELD_TITLE = "title"
private const val FIELD_TYPE = "type"
