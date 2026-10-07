"""Semantic page-canvas and custom rail-edge controls."""

from . import *


def page_canvas_mcp_call(
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
                PAGE_CANVAS_MCP_REQUEST_ID,
                tool_name,
                tool_arguments,
            ),
        )
    finally:
        MCP.delete_session(arguments, session_id)


def page_canvas_menu_semantic_id(page_id: str, action: str) -> str:
    return f"{PAGE_CANVAS_PAGE_MENU_PREFIX}{page_id}:{action}"


def test_page_canvas_and_custom_rail_edges_are_semantic_and_reversible(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    original_page_ids = page_ids(original)
    original_selected_page_id = selected_page(original)
    try:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_LONG_PRESS,
            semanticId=PAGE_CANVAS_HORIZONTAL_AFTER_EDGE_SEMANTIC_ID,
        )
        created = wait_for_page_count(websocket_control, len(original_page_ids) + 1)
        created_page_id = selected_page(created)
        assert created_page_id not in original_page_ids
        # The rail edge saves and selects in one step, so nothing provisional survives it.
        created_snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
        assert len(created_snapshot[AUTOMATION.KEY_PAGES]) == len(original_page_ids) + 1
        assert created_snapshot[AUTOMATION.KEY_SELECTED_PAGE_ID] == created_page_id
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / PAGE_CANVAS_ARTIFACT_NAME,
        )

        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_LONG_PRESS,
            semanticId=PAGE_CANVAS_PAGE_CANVAS_SEMANTIC_ID,
        )
        menu_semantic_id = page_canvas_menu_semantic_id(
            created_page_id,
            PAGE_CANVAS_PAGE_MENU_ACTION,
        )
        menu_snapshot = wait_for_snapshot_node(websocket_control, menu_semantic_id)
        assert AUTOMATION.require_node(
            menu_snapshot,
            menu_semantic_id,
        )[AUTOMATION.KEY_ROLE] == PAGE_CANVAS_PAGE_MENU_ROLE

        delete_semantic_id = page_canvas_menu_semantic_id(
            created_page_id,
            PAGE_CANVAS_PAGE_MENU_DELETE_ACTION,
        )
        assert AUTOMATION.require_node(
            menu_snapshot,
            delete_semantic_id,
        )[AUTOMATION.KEY_ROLE] == PAGE_CANVAS_PAGE_MENU_ROLE
        page_canvas_mcp_call(MCP.TOOL_TAP, {MCP.KEY_SEMANTIC_ID: delete_semantic_id})
        restored = wait_for_page_count(websocket_control, len(original_page_ids))
        assert page_ids(restored) == original_page_ids
        assert selected_page(restored) == original_selected_page_id
    finally:
        restored = AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
        assert AUTOMATION.require_configuration(restored) == original
