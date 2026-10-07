"""Top-level widget action behavior through WebSocket and MCP."""

from . import *


REMOVED_WIDGET_Z_INDEX_KEY = "zIndex"
REMOVED_WIDGET_Z_INDEX_ACTION_SEMANTIC_ID = "widget:welcome:z-index-input"


def test_websocket_long_press_exposes_and_removes_resize_action(
    websocket_control: socket.socket,
) -> None:
    AUTOMATION.request(
        websocket_control,
        "longPress",
        semanticId=WELCOME_MOVE_HANDLE_SEMANTIC_ID,
    )
    snapshot = wait_for_snapshot_node(websocket_control, RESIZE_ACTION_SEMANTIC_ID)
    assert bounds_for(snapshot, RESIZE_ACTION_SEMANTIC_ID)["bottom"] > 0
    wait_for_snapshot_node(
        websocket_control,
        REMOVED_WIDGET_Z_INDEX_ACTION_SEMANTIC_ID,
        should_exist=False,
    )
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=RESIZE_ACTION_SEMANTIC_ID,
    )
    wait_for_snapshot_node(websocket_control, RESIZE_ACTION_SEMANTIC_ID, should_exist=False)


def test_websocket_deletes_a_top_level_widget(
    websocket_control: socket.socket,
) -> None:
    AUTOMATION.request(
        websocket_control,
        "longPress",
        semanticId=WELCOME_MOVE_HANDLE_SEMANTIC_ID,
    )
    wait_for_snapshot_node(websocket_control, DELETE_ACTION_SEMANTIC_ID)
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=DELETE_ACTION_SEMANTIC_ID,
    )
    wait_for_snapshot_node(websocket_control, "widget:welcome", should_exist=False)
    saved = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    with pytest.raises(AssertionError, match="configuration has no widget: welcome"):
        configuration_widget(saved, "welcome")


def test_mcp_long_press_exposes_and_removes_resize_action() -> None:
    arguments = argparse.Namespace(host=AUTOMATION_HOST, port=MCP_PORT, mutate=False)
    session_id = MCP.initialize(arguments)
    original: dict[str, Any] | None = None
    try:
        MCP.notification_initialized(arguments, session_id)
        original = MCP.require_configuration(
            MCP.require_tool_result(
                MCP.call_tool(arguments, session_id, 100, "dikciz_config_get"),
            ),
        )
        MCP.require_tool_result(
            MCP.call_tool(
                arguments,
                session_id,
                101,
                "dikciz_long_press",
                {"semanticId": WELCOME_MOVE_HANDLE_SEMANTIC_ID},
            ),
        )
        snapshot = MCP.require_tool_result(
            MCP.call_tool(arguments, session_id, 102, "dikciz_snapshot"),
        )
        assert bounds_for(snapshot, RESIZE_ACTION_SEMANTIC_ID)["bottom"] > 0
        MCP.require_tool_result(
            MCP.call_tool(
                arguments,
                session_id,
                103,
                "dikciz_tap",
                {"semanticId": RESIZE_ACTION_SEMANTIC_ID},
            ),
        )
        find_result = MCP.require_tool_result(
            MCP.call_tool(
                arguments,
                session_id,
                104,
                "dikciz_find",
                {"semanticId": RESIZE_ACTION_SEMANTIC_ID},
            ),
        )
        assert find_result["found"] is False
    finally:
        if original is not None:
            MCP.require_tool_result(
                MCP.call_tool(
                    arguments,
                    session_id,
                    105,
                    "dikciz_config_replace",
                    {"config": original},
                ),
            )
        MCP.delete_session(arguments, session_id)
def test_mcp_configuration_rejects_removed_z_index_and_keeps_current_home() -> None:
    arguments = argparse.Namespace(host=AUTOMATION_HOST, port=MCP_PORT, mutate=False)
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        original = MCP.require_configuration(
            MCP.require_tool_result(
                MCP.call_tool(arguments, session_id, 110, "dikciz_config_get"),
            ),
        )
        pages = original["launcher"]["home"]["pages"]
        assert isinstance(pages, list)
        assert all(
            REMOVED_WIDGET_Z_INDEX_KEY not in widget
            for page in pages
            for widget in page["widgets"]
        )
        configured = copy.deepcopy(original)
        configuration_widget(configured, "welcome")[REMOVED_WIDGET_Z_INDEX_KEY] = 0
        rejected = MCP.call_tool(
            arguments,
            session_id,
            111,
            "dikciz_config_replace",
            {"config": configured},
        )
        assert rejected["isError"] is True
        current = MCP.require_configuration(
            MCP.require_tool_result(
                MCP.call_tool(arguments, session_id, 112, "dikciz_config_get"),
            ),
        )
        assert current == original
    finally:
        MCP.delete_session(arguments, session_id)


def test_mcp_configuration_replace_persists_widget_style_and_lock() -> None:
    arguments = argparse.Namespace(host=AUTOMATION_HOST, port=MCP_PORT, mutate=False)
    session_id = MCP.initialize(arguments)
    original: dict[str, Any] | None = None
    try:
        MCP.notification_initialized(arguments, session_id)
        original = MCP.require_configuration(
            MCP.require_tool_result(
                MCP.call_tool(
                    arguments,
                    session_id,
                    MCP_STYLE_LOCK_CONFIG_GET_REQUEST_ID,
                    "dikciz_config_get",
                ),
            ),
        )
        configured = copy.deepcopy(original)
        configure_welcome_style(
            configured,
            {
                "background": {"color": "#173A5E", "opacity": 0.75},
                "border": {"color": "#FFFFFFFF", "widths": {"all": 1}},
                "text": {"font": {"source": "bundled", "id": "comic-neue"}},
            },
        )
        configuration_widget(configured, "welcome")["locked"] = True
        persisted = MCP.require_configuration(
            MCP.require_tool_result(
                MCP.call_tool(
                    arguments,
                    session_id,
                    MCP_STYLE_LOCK_CONFIG_REPLACE_REQUEST_ID,
                    "dikciz_config_replace",
                    {"config": configured},
                ),
            ),
        )
        persisted_welcome = configuration_widget(persisted, "welcome")
        assert persisted_welcome["locked"] is True
        assert persisted_welcome["style"] == configuration_widget(configured, "welcome")["style"]
        with connected_websocket_control() as websocket_control:
            wait_for_snapshot_node(
                websocket_control,
                WELCOME_MOVE_HANDLE_SEMANTIC_ID,
                should_exist=False,
            )
        physical_widget_bounds()
    finally:
        if original is not None:
            restored = MCP.require_configuration(
                MCP.require_tool_result(
                    MCP.call_tool(
                        arguments,
                        session_id,
                        MCP_STYLE_LOCK_CONFIG_RESTORE_REQUEST_ID,
                        "dikciz_config_replace",
                        {"config": original},
                    ),
                ),
            )
            assert restored == original
        MCP.delete_session(arguments, session_id)


def test_mcp_deletes_a_top_level_widget() -> None:
    arguments = argparse.Namespace(host=AUTOMATION_HOST, port=MCP_PORT, mutate=False)
    session_id = MCP.initialize(arguments)
    original: dict[str, Any] | None = None
    try:
        MCP.notification_initialized(arguments, session_id)
        original = MCP.require_configuration(
            MCP.require_tool_result(
                MCP.call_tool(arguments, session_id, 200, "dikciz_config_get"),
            ),
        )
        MCP.require_tool_result(
            MCP.call_tool(
                arguments,
                session_id,
                201,
                "dikciz_long_press",
                {"semanticId": WELCOME_MOVE_HANDLE_SEMANTIC_ID},
            ),
        )
        snapshot = MCP.require_tool_result(
            MCP.call_tool(arguments, session_id, 202, "dikciz_snapshot"),
        )
        assert bounds_for(snapshot, DELETE_ACTION_SEMANTIC_ID)["bottom"] > 0
        MCP.require_tool_result(
            MCP.call_tool(
                arguments,
                session_id,
                203,
                "dikciz_tap",
                {"semanticId": DELETE_ACTION_SEMANTIC_ID},
            ),
        )
        saved = MCP.require_configuration(
            MCP.require_tool_result(
                MCP.call_tool(arguments, session_id, 204, "dikciz_config_get"),
            ),
        )
        with pytest.raises(AssertionError, match="configuration has no widget: welcome"):
            configuration_widget(saved, "welcome")
    finally:
        if original is not None:
            MCP.require_tool_result(
                MCP.call_tool(
                    arguments,
                    session_id,
                    205,
                    "dikciz_config_replace",
                    {"config": original},
                ),
            )
        MCP.delete_session(arguments, session_id)
