"""Installed HTML-widget limits through public configuration and diagnostics."""

from . import *


HTML_RESOURCE_ACTIVE_RENDERER_COUNT_KEY = "activeRendererCount"
HTML_RESOURCE_DIAGNOSTICS_KEY = "htmlWidgetResources"
HTML_RESOURCE_DOCUMENT_BYTES_KEY = "documentBytes"
HTML_RESOURCE_DOCUMENT_LIMIT_BYTES = 16_384
HTML_RESOURCE_ENABLED_WIDGET_ID = "resource-enabled"
HTML_RESOURCE_ENABLED_WIDGET_TEXT = "Renderer limit: enabled"
HTML_RESOURCE_MAX_DOCUMENT_BYTES_KEY = "maxDocumentBytes"
HTML_RESOURCE_MAX_RENDERERS_KEY = "maxRenderersPerPage"
HTML_RESOURCE_MAX_RENDERERS_PER_PAGE = 1
HTML_RESOURCE_MALFORMED_TEXT = "Renderer limit: malformed"
HTML_RESOURCE_MALFORMED_HTML = (
    f'<section><p id="resource-malformed">{HTML_RESOURCE_MALFORMED_TEXT}'
)
HTML_RESOURCE_OVERSIZED_HTML = (
    "<p>" + ("x" * HTML_RESOURCE_DOCUMENT_LIMIT_BYTES) + "</p>"
)
HTML_RESOURCE_PAGE_ID = FIXTURE_HOME_PAGE_ID
HTML_RESOURCE_TEXT_LIMIT = HTML_RESOURCE_DOCUMENT_LIMIT_BYTES * 2
HTML_RESOURCE_DISABLED_WIDGET_ID = "resource-disabled"
HTML_RESOURCE_DISABLED_WIDGET_TEXT = "Renderer limit: disabled"
HTML_RESOURCE_DISABLED_WIDGET_CELL = {"column": 0, "row": 3, "columnSpan": 4, "rowSpan": 1}
HTML_RESOURCE_WIDGET_CSS = (
    "body{margin:0;background:#162337;color:#f3f7ff;"
    "font:16px system-ui,sans-serif}p{margin:16px}"
)
HTML_RESOURCE_WIDGET_CELL = {"column": 0, "row": 2, "columnSpan": 4, "rowSpan": 1}


def resource_widget(
    *,
    widget_id: str,
    text: str,
    enabled: bool,
    cell: dict[str, int],
) -> dict[str, object]:
    return {
        "id": widget_id,
        "type": "html",
        "title": widget_id,
        "html": f"<p>{text}</p>",
        "css": HTML_RESOURCE_WIDGET_CSS,
        "javascript": "",
        "state": {},
        "enabled": enabled,
        "cell": dict(cell),
    }


def resource_limited_configuration(original: dict[str, Any]) -> dict[str, Any]:
    configured = copy.deepcopy(original)
    configured["limits"]["maxHtmlWidgetDocumentBytes"] = (
        HTML_RESOURCE_DOCUMENT_LIMIT_BYTES
    )
    configured["limits"]["maxHtmlWidgetsPerPage"] = (
        HTML_RESOURCE_MAX_RENDERERS_PER_PAGE
    )
    configured["limits"]["maxTextCharacters"] = HTML_RESOURCE_TEXT_LIMIT
    configuration_page(configured, HTML_RESOURCE_PAGE_ID)["widgets"] = [
        resource_widget(
            widget_id=HTML_RESOURCE_ENABLED_WIDGET_ID,
            text=HTML_RESOURCE_ENABLED_WIDGET_TEXT,
            enabled=True,
            cell=HTML_RESOURCE_WIDGET_CELL,
        ),
        resource_widget(
            widget_id=HTML_RESOURCE_DISABLED_WIDGET_ID,
            text=HTML_RESOURCE_DISABLED_WIDGET_TEXT,
            enabled=False,
            cell=HTML_RESOURCE_DISABLED_WIDGET_CELL,
        ),
    ]
    configured["launcher"]["home"]["selectedPageId"] = HTML_RESOURCE_PAGE_ID
    return configured


def html_widget_resources(connection: socket.socket) -> dict[str, object]:
    diagnostics = AUTOMATION.request(connection, AUTOMATION.TYPE_DIAGNOSTICS)
    resources = diagnostics.get(HTML_RESOURCE_DIAGNOSTICS_KEY)
    assert isinstance(resources, dict)
    return resources


def test_html_widget_resource_limits_reject_excess_renderers_and_documents(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    requested = resource_limited_configuration(original)
    try:
        configured = AUTOMATION.require_configuration(
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_CONFIG_REPLACE,
                config=requested,
            ),
        )
        wait_for_physical_text_bounds(HTML_RESOURCE_ENABLED_WIDGET_TEXT)
        resources = html_widget_resources(websocket_control)
        assert resources[HTML_RESOURCE_ACTIVE_RENDERER_COUNT_KEY] == 1
        document_bytes = resources[HTML_RESOURCE_DOCUMENT_BYTES_KEY]
        assert isinstance(document_bytes, int)
        assert 0 < document_bytes <= HTML_RESOURCE_DOCUMENT_LIMIT_BYTES
        assert resources[HTML_RESOURCE_MAX_DOCUMENT_BYTES_KEY] == (
            HTML_RESOURCE_DOCUMENT_LIMIT_BYTES
        )
        assert resources[HTML_RESOURCE_MAX_RENDERERS_KEY] == (
            HTML_RESOURCE_MAX_RENDERERS_PER_PAGE
        )

        malformed_document = copy.deepcopy(configured)
        configuration_widget(
            malformed_document,
            HTML_RESOURCE_ENABLED_WIDGET_ID,
        )["html"] = HTML_RESOURCE_MALFORMED_HTML
        malformed = AUTOMATION.require_configuration(
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_CONFIG_REPLACE,
                config=malformed_document,
            ),
        )
        wait_for_physical_text_bounds(HTML_RESOURCE_MALFORMED_TEXT)
        assert html_widget_resources(websocket_control)[
            HTML_RESOURCE_ACTIVE_RENDERER_COUNT_KEY
        ] == HTML_RESOURCE_MAX_RENDERERS_PER_PAGE
        assert AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        ) == malformed

        configured = AUTOMATION.require_configuration(
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_CONFIG_REPLACE,
                config=configured,
            ),
        )
        wait_for_physical_text_bounds(HTML_RESOURCE_ENABLED_WIDGET_TEXT)

        excess_renderers = copy.deepcopy(configured)
        configuration_widget(
            excess_renderers,
            HTML_RESOURCE_DISABLED_WIDGET_ID,
        )["enabled"] = True
        with pytest.raises(AUTOMATION.WebSocketProtocolError, match="enabled HTML widgets"):
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_CONFIG_REPLACE,
                config=excess_renderers,
            )
        assert AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        ) == configured

        oversized_document = copy.deepcopy(configured)
        configuration_widget(
            oversized_document,
            HTML_RESOURCE_ENABLED_WIDGET_ID,
        )["html"] = HTML_RESOURCE_OVERSIZED_HTML
        with pytest.raises(AUTOMATION.WebSocketProtocolError, match="document byte limit"):
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_CONFIG_REPLACE,
                config=oversized_document,
            )
        assert AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        ) == configured
        run_device_operation("screenshot")
        assert (ARTIFACT_DIRECTORY / "screenshot.png").stat().st_size > 0
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
