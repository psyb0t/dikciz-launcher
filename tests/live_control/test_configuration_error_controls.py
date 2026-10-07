"""Fatal public-configuration recovery through both local control planes."""

from . import *


def configuration_error_mcp_call(
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


def wait_for_configuration_error_control() -> socket.socket:
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
            if snapshot.get(AUTOMATION.KEY_SCREEN) != CONFIGURATION_ERROR_SCREEN:
                raise AssertionError("launcher did not render the configuration error screen")
            return control
        except (AssertionError, OSError, AUTOMATION.WebSocketProtocolError) as exception:
            last_error = exception
            if control is not None:
                control.close()
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("configuration error control plane did not start") from last_error


def test_fatal_configuration_error_is_inspectable_and_recovers_through_both_local_planes() -> None:
    error_control: socket.socket | None = None
    original_artifact = ARTIFACT_DIRECTORY / CONFIGURATION_ERROR_ORIGINAL_CONFIG_ARTIFACT_NAME
    invalid_artifact = ARTIFACT_DIRECTORY / CONFIGURATION_ERROR_INVALID_CONFIG_ARTIFACT_NAME
    run_device_operation(
        "file-pull",
        DEVICE_FILE=CONFIG_DEVICE_PATH,
        ARTIFACT_FILE=CONFIGURATION_ERROR_ORIGINAL_CONFIG_ARTIFACT_NAME,
    )
    invalid_artifact.write_text(CONFIG_INVALID_CONTENT, encoding="utf-8")
    try:
        run_device_operation(
            "file-push",
            DEVICE_FILE=CONFIG_DEVICE_PATH,
            ARTIFACT_FILE=CONFIGURATION_ERROR_INVALID_CONFIG_ARTIFACT_NAME,
        )
        run_device_operation("app-stop", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
        run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
        error_control = wait_for_configuration_error_control()

        websocket_snapshot = AUTOMATION.request(error_control, AUTOMATION.TYPE_SNAPSHOT)
        assert websocket_snapshot[AUTOMATION.KEY_SCREEN] == CONFIGURATION_ERROR_SCREEN
        assert websocket_snapshot[AUTOMATION.KEY_SELECTED_PAGE_ID] is None
        assert websocket_snapshot[AUTOMATION.KEY_PAGES] == []
        assert websocket_snapshot[CONFIGURATION_ERROR_WIDGET_REFERENCES_KEY] == []
        assert websocket_snapshot[CONFIGURATION_ERROR_NODES_KEY] == []
        with pytest.raises(
            AUTOMATION.WebSocketProtocolError,
            match=CONFIGURATION_ERROR_CONFIGURATION_UNAVAILABLE_MESSAGE,
        ):
            AUTOMATION.request(error_control, AUTOMATION.TYPE_CONFIG_GET)

        mcp_snapshot = configuration_error_mcp_call(
            MCP.TOOL_SNAPSHOT,
            CONFIGURATION_ERROR_MCP_SNAPSHOT_REQUEST_ID,
        )
        assert mcp_snapshot == websocket_snapshot
        with pytest.raises(MCP.McpProtocolError):
            configuration_error_mcp_call(
                MCP.TOOL_CONFIG_GET,
                CONFIGURATION_ERROR_MCP_CONFIG_GET_REQUEST_ID,
            )

        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / CONFIGURATION_ERROR_ARTIFACT_NAME,
        )
    finally:
        if error_control is not None:
            error_control.close()
        run_device_operation(
            "file-push",
            DEVICE_FILE=CONFIG_DEVICE_PATH,
            ARTIFACT_FILE=CONFIGURATION_ERROR_ORIGINAL_CONFIG_ARTIFACT_NAME,
        )
        restored_control = wait_for_restarted_websocket_control()
        try:
            AUTOMATION.require_configuration(
                AUTOMATION.request(restored_control, AUTOMATION.TYPE_CONFIG_GET),
            )
        finally:
            restored_control.close()
