"""Widget-lock dialog controls through the local WebSocket and MCP planes."""

from . import *


def widget_locks_mcp_snapshot() -> dict[str, Any]:
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
                WIDGET_LOCKS_MCP_SNAPSHOT_REQUEST_ID,
                MCP.TOOL_SNAPSHOT,
            ),
        )
    finally:
        MCP.delete_session(arguments, session_id)


def tap_widget_locks_input_through_mcp() -> None:
    arguments = argparse.Namespace(
        host=AUTOMATION_HOST,
        port=MCP_PORT,
        expected_control_port=DEVICE_MCP_PORT,
        mutate=False,
    )
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        MCP.require_tool_result(
            MCP.call_tool(
                arguments,
                session_id,
                WIDGET_LOCKS_MCP_TAP_REQUEST_ID,
                MCP.TOOL_TAP,
                {MCP.KEY_SEMANTIC_ID: WIDGET_LOCKS_NOTES_WIDGET_SEMANTIC_ID},
            ),
        )
    finally:
        MCP.delete_session(arguments, session_id)


def test_widget_lock_inputs_and_save_are_scriptable_through_both_control_planes(
    websocket_control: socket.socket,
) -> None:
    assert FIXTURE_NOTES_PAGE_ID == WIDGET_LOCKS_NOTES_PAGE_ID
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    original_widget = configuration_widget(original, WIDGET_LOCKS_NOTES_WIDGET_ID)
    original_locked = original_widget["locked"]
    assert isinstance(original_locked, bool)
    try:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_SELECT_PAGE,
            pageId=FIXTURE_NOTES_PAGE_ID,
        )
        wait_for_selected_page(websocket_control, FIXTURE_NOTES_PAGE_ID)
        open_page_menu_on_selected_page()
        wait_for_physical_text_bounds(PAGE_MENU_WIDGET_LOCKS_LABEL)
        direct_tap(wait_for_physical_text_bounds(PAGE_MENU_WIDGET_LOCKS_LABEL))
        websocket_snapshot = wait_for_snapshot_node(
            websocket_control,
            WIDGET_LOCKS_NOTES_WIDGET_SEMANTIC_ID,
        )
        websocket_input = AUTOMATION.require_node(
            websocket_snapshot,
            WIDGET_LOCKS_NOTES_WIDGET_SEMANTIC_ID,
        )
        websocket_save = AUTOMATION.require_node(
            websocket_snapshot,
            WIDGET_LOCKS_NOTES_SAVE_SEMANTIC_ID,
        )
        assert websocket_input[AUTOMATION.KEY_ROLE] == WIDGET_LOCKS_ROLE_SWITCH
        assert websocket_input[AUTOMATION.KEY_CHECKED] is original_locked
        assert websocket_save[AUTOMATION.KEY_ROLE] == WIDGET_LOCKS_ROLE_BUTTON
        mcp_snapshot = widget_locks_mcp_snapshot()
        assert AUTOMATION.require_node(
            mcp_snapshot,
            WIDGET_LOCKS_NOTES_WIDGET_SEMANTIC_ID,
        ) == websocket_input
        assert AUTOMATION.require_node(
            mcp_snapshot,
            WIDGET_LOCKS_NOTES_SAVE_SEMANTIC_ID,
        ) == websocket_save
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / WIDGET_LOCKS_ARTIFACT_NAME,
        )
        assert (ARTIFACT_DIRECTORY / WIDGET_LOCKS_ARTIFACT_NAME).stat().st_size > 0
        tap_widget_locks_input_through_mcp()
        toggled_snapshot = wait_for_snapshot_node(
            websocket_control,
            WIDGET_LOCKS_NOTES_WIDGET_SEMANTIC_ID,
        )
        assert AUTOMATION.require_node(
            toggled_snapshot,
            WIDGET_LOCKS_NOTES_WIDGET_SEMANTIC_ID,
        )[AUTOMATION.KEY_CHECKED] is not original_locked
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_TAP,
            semanticId=WIDGET_LOCKS_NOTES_SAVE_SEMANTIC_ID,
        )
        wait_for_snapshot_node(
            websocket_control,
            WIDGET_LOCKS_NOTES_WIDGET_SEMANTIC_ID,
            should_exist=False,
        )
        wait_for_snapshot_node(
            websocket_control,
            WIDGET_LOCKS_NOTES_SAVE_SEMANTIC_ID,
            should_exist=False,
        )
        saved = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert configuration_widget(saved, WIDGET_LOCKS_NOTES_WIDGET_ID)["locked"] is not original_locked
        with pytest.raises(AUTOMATION.WebSocketProtocolError):
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_TAP,
                semanticId=WIDGET_LOCKS_NOTES_SAVE_SEMANTIC_ID,
            )
    finally:
        restored = AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
        assert AUTOMATION.require_configuration(restored) == original
