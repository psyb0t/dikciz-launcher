"""First-run shared-storage handoff through both local control planes."""

from . import *


def set_storage_access(mode: str) -> None:
    assert mode in {STORAGE_ACCESS_ALLOW_MODE, STORAGE_ACCESS_DENY_MODE}
    device_command(
        STORAGE_ACCESS_APP_OP_COMMAND.format(
            package_name=DIKCIZ_DEBUG_PACKAGE,
            app_op=STORAGE_ACCESS_APP_OP,
            mode=mode,
        ),
    )


def storage_access_mcp_call(
    tool_name: str,
    request_id: int,
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
                request_id,
                tool_name,
                tool_arguments,
            ),
        )
    finally:
        MCP.delete_session(arguments, session_id)


def wait_for_storage_access_control() -> socket.socket:
    deadline = time.monotonic() + CONTROL_PLANE_RESTART_TIMEOUT_SECONDS
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        control: socket.socket | None = None
        try:
            control = socket.create_connection(
                (AUTOMATION_HOST, AUTOMATION_PORT),
                timeout=CONTROL_PLANE_RETRY_INTERVAL_SECONDS,
            )
            control.settimeout(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
            AUTOMATION.perform_handshake(control, AUTOMATION_HOST, AUTOMATION_PORT)
            AUTOMATION.hello(control)
            snapshot = AUTOMATION.request(control, AUTOMATION.TYPE_SNAPSHOT)
            if snapshot.get(AUTOMATION.KEY_SCREEN) != STORAGE_ACCESS_SCREEN:
                raise AssertionError("launcher did not render the storage access screen")
            AUTOMATION.require_node(snapshot, STORAGE_ACCESS_GRANT_SEMANTIC_ID)
            return control
        except (AssertionError, OSError, AUTOMATION.WebSocketProtocolError) as exception:
            last_error = exception
            if control is not None:
                control.close()
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("storage access control plane did not start") from last_error


def test_storage_access_handoff_is_semantic_and_configuration_is_still_gated() -> None:
    storage_control: socket.socket | None = None
    try:
        set_storage_access(STORAGE_ACCESS_DENY_MODE)
        run_device_operation("app-stop", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
        run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
        storage_control = wait_for_storage_access_control()

        websocket_snapshot = AUTOMATION.request(storage_control, AUTOMATION.TYPE_SNAPSHOT)
        assert websocket_snapshot[AUTOMATION.KEY_SCREEN] == STORAGE_ACCESS_SCREEN
        assert websocket_snapshot[AUTOMATION.KEY_SELECTED_PAGE_ID] is None
        assert websocket_snapshot[AUTOMATION.KEY_PAGES] == []
        assert websocket_snapshot[STORAGE_ACCESS_WIDGET_REFERENCES_KEY] == []
        grant_node = AUTOMATION.require_node(
            websocket_snapshot,
            STORAGE_ACCESS_GRANT_SEMANTIC_ID,
        )
        assert grant_node[AUTOMATION.KEY_ROLE] == "button"

        with pytest.raises(
            AUTOMATION.WebSocketProtocolError,
            match=STORAGE_ACCESS_CONFIGURATION_UNAVAILABLE_MESSAGE,
        ):
            AUTOMATION.request(storage_control, AUTOMATION.TYPE_CONFIG_GET)

        mcp_snapshot = storage_access_mcp_call(
            MCP.TOOL_SNAPSHOT,
            STORAGE_ACCESS_MCP_SNAPSHOT_REQUEST_ID,
        )
        assert AUTOMATION.require_node(
            mcp_snapshot,
            STORAGE_ACCESS_GRANT_SEMANTIC_ID,
        ) == grant_node
        with pytest.raises(MCP.McpProtocolError):
            storage_access_mcp_call(
                MCP.TOOL_CONFIG_GET,
                STORAGE_ACCESS_MCP_CONFIG_GET_REQUEST_ID,
            )

        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / STORAGE_ACCESS_ARTIFACT_NAME,
        )
        storage_access_mcp_call(
            MCP.TOOL_TAP,
            STORAGE_ACCESS_MCP_TAP_REQUEST_ID,
            {MCP.KEY_SEMANTIC_ID: STORAGE_ACCESS_GRANT_SEMANTIC_ID},
        )
        wait_for_foreground_application(STORAGE_ACCESS_SETTINGS_PACKAGE)
    finally:
        if storage_control is not None:
            storage_control.close()
        set_storage_access(STORAGE_ACCESS_ALLOW_MODE)
        run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
        restored_control = wait_for_restarted_websocket_control()
        try:
            AUTOMATION.require_configuration(
                AUTOMATION.request(restored_control, AUTOMATION.TYPE_CONFIG_GET),
            )
        finally:
            restored_control.close()
