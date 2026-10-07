"""Native appearance-editor actions through the local control planes."""

from . import *


def appearance_mcp_call(
    tool_name: str,
    tool_arguments: dict[str, Any] | None = None,
) -> dict[str, Any]:
    arguments = argparse.Namespace(
        host=AUTOMATION_HOST,
        port=MCP_PORT,
        expected_control_port=DEVICE_MCP_PORT,
        mutate=False,
    )
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        return MCP.require_tool_result(
            MCP.call_tool(
                arguments,
                session_id,
                APPEARANCE_ACTIONS_MCP_REQUEST_ID,
                tool_name,
                tool_arguments,
            ),
        )
    finally:
        MCP.delete_session(arguments, session_id)


def open_widget_appearance_editor(websocket_control: socket.socket) -> dict[str, Any]:
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_LONG_PRESS,
        semanticId=APPEARANCE_ACTIONS_WIDGET_SEMANTIC_ID,
    )
    wait_for_snapshot_node(websocket_control, APPEARANCE_WIDGET_ACTION_SEMANTIC_ID)
    appearance_mcp_call(
        MCP.TOOL_TAP,
        {MCP.KEY_SEMANTIC_ID: APPEARANCE_WIDGET_ACTION_SEMANTIC_ID},
    )
    return wait_for_snapshot_node(websocket_control, APPEARANCE_ACTIONS_WIDGET_SAVE_SEMANTIC_ID)


def open_default_widget_appearance_editor(websocket_control: socket.socket) -> dict[str, Any]:
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_SELECT_PAGE,
        pageId=FIXTURE_HOME_PAGE_ID,
    )
    open_page_menu_on_selected_page()
    appearance_mcp_call(
        MCP.TOOL_TAP,
        {MCP.KEY_SEMANTIC_ID: APPEARANCE_ACTIONS_PAGE_MENU_SETTINGS_SEMANTIC_ID},
    )
    wait_for_snapshot_node(websocket_control, SETTINGS_WIDGET_APPEARANCE_SEMANTIC_ID)
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=SETTINGS_WIDGET_APPEARANCE_SEMANTIC_ID,
    )
    return wait_for_snapshot_node(websocket_control, APPEARANCE_ACTIONS_DEFAULT_SAVE_SEMANTIC_ID)


def test_appearance_actions_are_semantic_persistent_and_lifecycle_bound(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    try:
        widget_snapshot = open_widget_appearance_editor(websocket_control)
        widget_save = AUTOMATION.require_node(
            widget_snapshot,
            APPEARANCE_ACTIONS_WIDGET_SAVE_SEMANTIC_ID,
        )
        widget_reset = AUTOMATION.require_node(
            widget_snapshot,
            APPEARANCE_ACTIONS_WIDGET_RESET_SEMANTIC_ID,
        )
        assert widget_save[AUTOMATION.KEY_ROLE] == APPEARANCE_ACTIONS_ROLE_BUTTON
        assert widget_reset[AUTOMATION.KEY_ROLE] == APPEARANCE_ACTIONS_ROLE_BUTTON
        mcp_snapshot = appearance_mcp_call(MCP.TOOL_SNAPSHOT)
        assert AUTOMATION.require_node(
            mcp_snapshot,
            APPEARANCE_ACTIONS_WIDGET_SAVE_SEMANTIC_ID,
        ) == widget_save
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / APPEARANCE_ACTIONS_ARTIFACT_NAME,
        )
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_SET_TEXT,
            semanticId=APPEARANCE_ACTIONS_WIDGET_BACKGROUND_COLOR_SEMANTIC_ID,
            text=APPEARANCE_WIDGET_BACKGROUND_COLOR,
        )
        appearance_mcp_call(
            MCP.TOOL_TAP,
            {MCP.KEY_SEMANTIC_ID: APPEARANCE_ACTIONS_WIDGET_SAVE_SEMANTIC_ID},
        )
        wait_for_snapshot_node(
            websocket_control,
            APPEARANCE_ACTIONS_WIDGET_SAVE_SEMANTIC_ID,
            should_exist=False,
        )
        saved = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert configuration_widget(saved, APPEARANCE_ACTIONS_WIDGET_ID)["style"] == {
            "background": {"color": APPEARANCE_WIDGET_BACKGROUND_COLOR},
        }
        with pytest.raises(AUTOMATION.WebSocketProtocolError):
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_TAP,
                semanticId=APPEARANCE_ACTIONS_WIDGET_SAVE_SEMANTIC_ID,
            )

        default_snapshot = open_default_widget_appearance_editor(websocket_control)
        default_save = AUTOMATION.require_node(
            default_snapshot,
            APPEARANCE_ACTIONS_DEFAULT_SAVE_SEMANTIC_ID,
        )
        default_reset = AUTOMATION.require_node(
            default_snapshot,
            APPEARANCE_ACTIONS_DEFAULT_RESET_SEMANTIC_ID,
        )
        assert default_save[AUTOMATION.KEY_ROLE] == APPEARANCE_ACTIONS_ROLE_BUTTON
        assert default_reset[AUTOMATION.KEY_ROLE] == APPEARANCE_ACTIONS_ROLE_BUTTON
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_SET_TEXT,
            semanticId=APPEARANCE_ACTIONS_DEFAULT_BACKGROUND_COLOR_SEMANTIC_ID,
            text=APPEARANCE_WIDGET_DEFAULT_BACKGROUND_COLOR,
        )
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_TAP,
            semanticId=APPEARANCE_ACTIONS_DEFAULT_SAVE_SEMANTIC_ID,
        )
        saved_defaults = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert saved_defaults["launcher"]["home"]["styleDefaults"]["widget"] == {
            "background": {"color": APPEARANCE_WIDGET_DEFAULT_BACKGROUND_COLOR},
        }

        open_default_widget_appearance_editor(websocket_control)
        appearance_mcp_call(
            MCP.TOOL_TAP,
            {MCP.KEY_SEMANTIC_ID: APPEARANCE_ACTIONS_DEFAULT_RESET_SEMANTIC_ID},
        )
        wait_for_snapshot_node(
            websocket_control,
            APPEARANCE_ACTIONS_DEFAULT_RESET_SEMANTIC_ID,
            should_exist=False,
        )
        reset = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert reset["launcher"]["home"]["styleDefaults"]["widget"] == {}
        with pytest.raises(AUTOMATION.WebSocketProtocolError):
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_TAP,
                semanticId=APPEARANCE_ACTIONS_DEFAULT_RESET_SEMANTIC_ID,
            )
    finally:
        restored = AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
        assert AUTOMATION.require_configuration(restored) == original
