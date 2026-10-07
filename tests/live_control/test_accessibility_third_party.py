"""Cross-app accessibility actions against the installed fixture application."""

from . import *


def third_party_component(package_name: str) -> str:
    return f"{package_name}/{ACCESSIBILITY_THIRD_PARTY_ACTIVITY_CLASS}"


def third_party_action_count_text(action_count: int) -> str:
    return ACCESSIBILITY_THIRD_PARTY_ACTION_COUNT_FORMAT.format(action_count)


def third_party_visible_node(
    snapshot: dict[str, Any],
    package_name: str,
    text: str,
) -> dict[str, Any]:
    for node in snapshot["nodes"]:
        if (
            node["packageName"] == package_name
            and node["visibleToUser"]
            and node["text"] == text
        ):
            return node
    raise AssertionError(f"fixture snapshot did not expose visible text: {text!r}")


def third_party_action_node(
    snapshot: dict[str, Any],
    package_name: str,
) -> dict[str, Any]:
    node = third_party_visible_node(
        snapshot,
        package_name,
        ACCESSIBILITY_THIRD_PARTY_ACTION_TEXT,
    )
    assert node["clickable"]
    assert node["enabled"]
    assert ACCESSIBILITY_ACTION_CLICK in node["actions"]
    return node


def wait_for_third_party_snapshot(
    connection: socket.socket,
    package_name: str,
    action_count: int,
) -> dict[str, Any]:
    expected_status = third_party_action_count_text(action_count)
    deadline = time.monotonic() + ACCESSIBILITY_SERVICE_READY_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        snapshot = AUTOMATION.request(connection, AUTOMATION.TYPE_ACCESSIBILITY_SNAPSHOT)
        if snapshot[ACCESSIBILITY_PACKAGE_NAME_KEY] != package_name:
            time.sleep(ACCESSIBILITY_SNAPSHOT_POLL_SECONDS)
            continue
        third_party_visible_node(snapshot, package_name, expected_status)
        return snapshot
    raise AssertionError("accessibility service did not expose the third-party fixture")


def wait_for_mcp_third_party_snapshot(
    arguments: argparse.Namespace,
    session_id: str,
    package_name: str,
    action_count: int,
) -> dict[str, Any]:
    expected_status = third_party_action_count_text(action_count)
    deadline = time.monotonic() + ACCESSIBILITY_SERVICE_READY_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        snapshot = MCP.require_tool_result(
            MCP.call_tool(
                arguments,
                session_id,
                ACCESSIBILITY_MCP_SNAPSHOT_REQUEST_ID,
                MCP.TOOL_ACCESSIBILITY_SNAPSHOT,
            ),
        )
        if snapshot[ACCESSIBILITY_PACKAGE_NAME_KEY] != package_name:
            time.sleep(ACCESSIBILITY_SNAPSHOT_POLL_SECONDS)
            continue
        third_party_visible_node(snapshot, package_name, expected_status)
        return snapshot
    raise AssertionError("MCP did not expose the third-party fixture")


def assert_third_party_action_result(
    result: dict[str, Any],
    target: dict[str, Any],
    snapshot: dict[str, Any],
) -> None:
    assert result == {
        "outcome": ACCESSIBILITY_OUTCOME_EXECUTED,
        AUTOMATION.KEY_ACTION: ACCESSIBILITY_ACTION_CLICK,
        AUTOMATION.KEY_NODE_ID: target[AUTOMATION.KEY_NODE_ID],
        AUTOMATION.KEY_SNAPSHOT_ID: snapshot[AUTOMATION.KEY_SNAPSHOT_ID],
    }


def test_accessibility_service_acts_on_a_real_third_party_window_through_both_control_planes(
    websocket_control: socket.socket,
    installed_configurable_provider_fixture: None,
) -> None:
    package_name = configurable_provider_fixture_package()
    try:
        emulator_dikciz_accessibility_set(True)
        device_command(f"am force-stop {package_name}")
        device_command(f"am start -W -n {third_party_component(package_name)}")
        wait_for_foreground_application(package_name)

        websocket_snapshot = wait_for_third_party_snapshot(
            websocket_control,
            package_name,
            ACCESSIBILITY_THIRD_PARTY_ACTION_COUNT_INITIAL,
        )
        websocket_target = third_party_action_node(websocket_snapshot, package_name)
        websocket_result = AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_ACCESSIBILITY_ACTION,
            action=ACCESSIBILITY_ACTION_CLICK,
            nodeId=websocket_target[AUTOMATION.KEY_NODE_ID],
            snapshotId=websocket_snapshot[AUTOMATION.KEY_SNAPSHOT_ID],
        )
        assert_third_party_action_result(
            websocket_result,
            websocket_target,
            websocket_snapshot,
        )
        wait_for_third_party_snapshot(
            websocket_control,
            package_name,
            ACCESSIBILITY_THIRD_PARTY_ACTION_COUNT_WEBSOCKET,
        )

        arguments = argparse.Namespace(
            host=AUTOMATION_HOST,
            port=MCP_PORT,
            expected_control_port=DEVICE_MCP_PORT,
            mutate=False,
        )
        session_id = MCP.initialize(arguments)
        try:
            MCP.notification_initialized(arguments, session_id)
            mcp_snapshot = wait_for_mcp_third_party_snapshot(
                arguments,
                session_id,
                package_name,
                ACCESSIBILITY_THIRD_PARTY_ACTION_COUNT_WEBSOCKET,
            )
            mcp_target = third_party_action_node(mcp_snapshot, package_name)
            mcp_result = MCP.require_tool_result(
                MCP.call_tool(
                    arguments,
                    session_id,
                    ACCESSIBILITY_MCP_ACTION_REQUEST_ID,
                    MCP.TOOL_ACCESSIBILITY_ACTION,
                    {
                        AUTOMATION.KEY_ACTION: ACCESSIBILITY_ACTION_CLICK,
                        AUTOMATION.KEY_NODE_ID: mcp_target[AUTOMATION.KEY_NODE_ID],
                        AUTOMATION.KEY_SNAPSHOT_ID: mcp_snapshot[AUTOMATION.KEY_SNAPSHOT_ID],
                    },
                ),
            )
            assert_third_party_action_result(mcp_result, mcp_target, mcp_snapshot)
        finally:
            MCP.delete_session(arguments, session_id)

        wait_for_third_party_snapshot(
            websocket_control,
            package_name,
            ACCESSIBILITY_THIRD_PARTY_ACTION_COUNT_MCP,
        )
        run_device_operation("screenshot")
        screenshot_path = ARTIFACT_DIRECTORY / "screenshot.png"
        third_party_screenshot_path = (
            ARTIFACT_DIRECTORY / ACCESSIBILITY_THIRD_PARTY_ACTION_SCREENSHOT
        )
        shutil.copyfile(screenshot_path, third_party_screenshot_path)
        assert third_party_screenshot_path.stat().st_size > 0
        _, stale_error = AUTOMATION.request_error_with_id(
            websocket_control,
            AUTOMATION.TYPE_ACCESSIBILITY_ACTION,
            action=ACCESSIBILITY_ACTION_CLICK,
            nodeId=mcp_target[AUTOMATION.KEY_NODE_ID],
            snapshotId=mcp_snapshot[AUTOMATION.KEY_SNAPSHOT_ID],
        )
        assert stale_error[AUTOMATION.KEY_CODE] == ACCESSIBILITY_ERROR_SNAPSHOT_STALE
        expiration_snapshot = wait_for_third_party_snapshot(
            websocket_control,
            package_name,
            ACCESSIBILITY_THIRD_PARTY_ACTION_COUNT_MCP,
        )
        time.sleep(ACCESSIBILITY_SNAPSHOT_EXPIRY_WAIT_SECONDS)
        _, expired_error = AUTOMATION.request_error_with_id(
            websocket_control,
            AUTOMATION.TYPE_ACCESSIBILITY_ACTION,
            action=ACCESSIBILITY_ACTION_CLICK,
            nodeId=mcp_target[AUTOMATION.KEY_NODE_ID],
            snapshotId=expiration_snapshot[AUTOMATION.KEY_SNAPSHOT_ID],
        )
        assert expired_error[AUTOMATION.KEY_CODE] == ACCESSIBILITY_ERROR_SNAPSHOT_EXPIRED
    finally:
        try:
            emulator_dikciz_accessibility_set(False)
        finally:
            restore_system_home()
