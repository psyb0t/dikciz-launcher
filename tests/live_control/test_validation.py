"""Configuration validation and protocol rejection behavior."""

from . import *


REMOVED_WIDGET_Z_INDEX_KEY = "zIndex"
CURRENT_WIDGET_TYPES = (
    "html",
    "app",
    "appGroup",
    "provider",
    "scriptDashboard",
)
REMOVED_WIDGET_Z_INDEX_TITLE = "Removed z index"
REMOVED_WIDGET_Z_INDEX_HTML = "<p>Removed z index</p>"
REMOVED_WIDGET_Z_INDEX_DISPLAY_STYLE = "iconLabel"
REMOVED_WIDGET_Z_INDEX_APP_WIDGET_ID = 1
REMOVED_WIDGET_Z_INDEX_TYPE_PROPERTIES = {
    "html": {"html": REMOVED_WIDGET_Z_INDEX_HTML},
    "app": {
        "component": SETTINGS_ACTIVITY_COMPONENT,
        "displayStyle": REMOVED_WIDGET_Z_INDEX_DISPLAY_STYLE,
    },
    "appGroup": {"components": [SETTINGS_ACTIVITY_COMPONENT]},
    "provider": {
        "provider": UNAVAILABLE_PROVIDER_COMPONENT,
        "appWidgetId": REMOVED_WIDGET_Z_INDEX_APP_WIDGET_ID,
    },
    "scriptDashboard": {},
}


@pytest.mark.parametrize(
    "case",
    INVALID_CONFIGURATION_CASES,
    ids=lambda case: case.name,
)
def test_websocket_rejects_invalid_persisted_widget_values(
    websocket_control: socket.socket,
    case: InvalidConfigurationCase,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    invalid = copy.deepcopy(original)
    case.apply(invalid)
    with pytest.raises(AUTOMATION.WebSocketProtocolError):
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=invalid,
        )
    current = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert current == original


def test_websocket_rejects_removed_z_index_for_every_widget_type(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    for widget_type in CURRENT_WIDGET_TYPES:
        invalid = copy.deepcopy(original)
        widget = configuration_widget(invalid, "welcome")
        cell = copy.deepcopy(widget["cell"])
        widget.clear()
        widget.update(
            {
                "id": "welcome",
                "type": widget_type,
                "title": REMOVED_WIDGET_Z_INDEX_TITLE,
                "enabled": True,
                "cell": cell,
                REMOVED_WIDGET_Z_INDEX_KEY: 0,
                **REMOVED_WIDGET_Z_INDEX_TYPE_PROPERTIES[widget_type],
            },
        )
        with pytest.raises(
            AUTOMATION.WebSocketProtocolError,
            match=f"unknown key: {REMOVED_WIDGET_Z_INDEX_KEY}",
        ):
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_CONFIG_REPLACE,
                config=invalid,
            )
        current = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert current == original, widget_type


@pytest.mark.parametrize(
    "case",
    CSS_STYLE_INVALID_CASES,
    ids=lambda case: case.name,
)
def test_websocket_rejects_invalid_css_style_properties(
    websocket_control: socket.socket,
    case: InvalidConfigurationCase,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    invalid = copy.deepcopy(original)
    case.apply(invalid)
    with pytest.raises(AUTOMATION.WebSocketProtocolError):
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=invalid,
        )
    current = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert current == original


def test_websocket_rejects_removed_group_widgets_and_keeps_configuration(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    invalid = copy.deepcopy(original)
    configuration_page(invalid, FIXTURE_HOME_PAGE_ID)["widgets"].append(
        {
            "id": "removed-group",
            "type": "group",
            "title": "Removed group",
            "enabled": True,
            "cell": {"column": 0, "row": 0, "columnSpan": 1, "rowSpan": 1},
            "widgets": [],
        },
    )
    with pytest.raises(AUTOMATION.WebSocketProtocolError, match="unknown widget type"):
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=invalid,
        )
    current = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert current == original


def test_mcp_rejects_removed_group_widgets_and_keeps_configuration() -> None:
    arguments = argparse.Namespace(host=AUTOMATION_HOST, port=MCP_PORT, mutate=False)
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        original = MCP.require_configuration(
            MCP.require_tool_result(
                MCP.call_tool(
                    arguments,
                    session_id,
                    MCP_REMOVED_WIDGET_CONFIG_GET_REQUEST_ID,
                    "dikciz_config_get",
                ),
            ),
        )
        invalid = copy.deepcopy(original)
        configuration_page(invalid, FIXTURE_HOME_PAGE_ID)["widgets"].append(
            {
                "id": "removed-group",
                "type": "group",
                "title": "Removed group",
                "enabled": True,
                "cell": {"column": 0, "row": 0, "columnSpan": 1, "rowSpan": 1},
                "widgets": [],
            },
        )
        result = MCP.call_tool(
            arguments,
            session_id,
            MCP_REMOVED_WIDGET_CONFIG_REPLACE_REQUEST_ID,
            "dikciz_config_replace",
            {"config": invalid},
        )
        assert result["isError"] is True
        current = MCP.require_configuration(
            MCP.require_tool_result(
                MCP.call_tool(
                    arguments,
                    session_id,
                    MCP_REMOVED_WIDGET_CONFIG_GET_REQUEST_ID,
                    "dikciz_config_get",
                ),
            ),
        )
        assert current == original
    finally:
        MCP.delete_session(arguments, session_id)
def test_websocket_rejects_cross_page_duplicate_widget_ids(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    invalid = configuration_with_cross_page_duplicate_widget_id(original)
    with pytest.raises(AUTOMATION.WebSocketProtocolError, match="duplicate widget id"):
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=invalid,
        )
    current = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert current == original


@pytest.mark.parametrize(
    "invalid_configuration",
    (
        configuration_with_case_insensitive_duplicate_widget_id,
        configuration_with_case_insensitive_duplicate_page_id,
    ),
    ids=("widget_id", "page_id"),
)
def test_websocket_rejects_case_insensitive_duplicate_identifiers(
    websocket_control: socket.socket,
    invalid_configuration: Callable[[dict[str, Any]], dict[str, Any]],
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    with pytest.raises(AUTOMATION.WebSocketProtocolError, match="case-insensitive duplicate"):
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=invalid_configuration(original),
        )
    current = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert current == original


def test_mcp_rejects_cross_page_duplicate_widget_ids() -> None:
    arguments = argparse.Namespace(host=AUTOMATION_HOST, port=MCP_PORT, mutate=False)
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        original = MCP.require_configuration(
            MCP.require_tool_result(
                MCP.call_tool(
                    arguments,
                    session_id,
                    MCP_WIDGET_REFERENCE_CONFIG_GET_REQUEST_ID,
                    "dikciz_config_get",
                ),
            ),
        )
        result = MCP.call_tool(
            arguments,
            session_id,
            MCP_WIDGET_REFERENCE_CONFIG_REPLACE_REQUEST_ID,
            "dikciz_config_replace",
            {"config": configuration_with_cross_page_duplicate_widget_id(original)},
        )
        assert result["isError"] is True
        current = MCP.require_configuration(
            MCP.require_tool_result(
                MCP.call_tool(
                    arguments,
                    session_id,
                    MCP_WIDGET_REFERENCE_CONFIG_GET_REQUEST_ID,
                    "dikciz_config_get",
                ),
            ),
        )
        assert current == original
    finally:
        MCP.delete_session(arguments, session_id)


@pytest.mark.parametrize(
    ("request_id", "invalid_configuration"),
    (
        (
            MCP_CASE_INSENSITIVE_WIDGET_REPLACE_REQUEST_ID,
            configuration_with_case_insensitive_duplicate_widget_id,
        ),
        (
            MCP_CASE_INSENSITIVE_PAGE_REPLACE_REQUEST_ID,
            configuration_with_case_insensitive_duplicate_page_id,
        ),
    ),
    ids=("widget_id", "page_id"),
)
def test_mcp_rejects_case_insensitive_duplicate_identifiers(
    request_id: int,
    invalid_configuration: Callable[[dict[str, Any]], dict[str, Any]],
) -> None:
    arguments = argparse.Namespace(host=AUTOMATION_HOST, port=MCP_PORT, mutate=False)
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        original = MCP.require_configuration(
            MCP.require_tool_result(
                MCP.call_tool(
                    arguments,
                    session_id,
                    MCP_CASE_INSENSITIVE_CONFIG_GET_REQUEST_ID,
                    "dikciz_config_get",
                ),
            ),
        )
        result = MCP.call_tool(
            arguments,
            session_id,
            request_id,
            "dikciz_config_replace",
            {"config": invalid_configuration(original)},
        )
        assert result["isError"] is True
        current = MCP.require_configuration(
            MCP.require_tool_result(
                MCP.call_tool(
                    arguments,
                    session_id,
                    MCP_CASE_INSENSITIVE_CONFIG_GET_REQUEST_ID,
                    "dikciz_config_get",
                ),
            ),
        )
        assert current == original
    finally:
        MCP.delete_session(arguments, session_id)
