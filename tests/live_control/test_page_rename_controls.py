"""Native page-rename dialog controls through the local control planes."""

from . import *


def page_rename_mcp_call(
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
                PAGE_RENAME_MCP_REQUEST_ID,
                tool_name,
                tool_arguments,
            ),
        )
    finally:
        MCP.delete_session(arguments, session_id)


def test_page_rename_dialog_controls_are_semantic_persistent_and_lifecycle_bound(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    try:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_SELECT_PAGE,
            pageId=PAGE_RENAME_PAGE_ID,
        )
        open_page_menu_on_selected_page()
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_TAP,
            semanticId=PAGE_RENAME_PAGE_MENU_SEMANTIC_ID,
        )
        snapshot = wait_for_snapshot_node(
            websocket_control,
            PAGE_RENAME_TITLE_SEMANTIC_ID,
        )
        title = AUTOMATION.require_node(snapshot, PAGE_RENAME_TITLE_SEMANTIC_ID)
        save = AUTOMATION.require_node(snapshot, PAGE_RENAME_SAVE_SEMANTIC_ID)
        assert title[AUTOMATION.KEY_ROLE] == PAGE_RENAME_ROLE_TEXT_INPUT
        assert save[AUTOMATION.KEY_ROLE] == PAGE_RENAME_ROLE_BUTTON
        mcp_snapshot = page_rename_mcp_call(MCP.TOOL_SNAPSHOT)
        assert AUTOMATION.require_node(mcp_snapshot, PAGE_RENAME_TITLE_SEMANTIC_ID) == title
        assert AUTOMATION.require_node(mcp_snapshot, PAGE_RENAME_SAVE_SEMANTIC_ID) == save
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / PAGE_RENAME_ARTIFACT_NAME,
        )
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_SET_TEXT,
            semanticId=PAGE_RENAME_TITLE_SEMANTIC_ID,
            text=PAGE_RENAME_TITLE_REPLACEMENT,
        )
        page_rename_mcp_call(
            MCP.TOOL_TAP,
            {MCP.KEY_SEMANTIC_ID: PAGE_RENAME_SAVE_SEMANTIC_ID},
        )
        wait_for_snapshot_node(
            websocket_control,
            PAGE_RENAME_SAVE_SEMANTIC_ID,
            should_exist=False,
        )
        saved = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert page_title(saved, PAGE_RENAME_PAGE_ID) == PAGE_RENAME_TITLE_REPLACEMENT
        assert configuration_page(saved, PAGE_RENAME_PAGE_ID)["id"] == PAGE_RENAME_PAGE_ID
        with pytest.raises(AUTOMATION.WebSocketProtocolError):
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_TAP,
                semanticId=PAGE_RENAME_SAVE_SEMANTIC_ID,
            )
    finally:
        restored = AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
        assert AUTOMATION.require_configuration(restored) == original
