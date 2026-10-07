"""Cross-app Android accessibility automation through both local control planes."""

from . import *
from .accessibility_support import has_settings_destination, wait_for_settings_destination


def assert_accessibility_snapshot(snapshot: dict[str, Any]) -> None:
    assert set(snapshot) == ACCESSIBILITY_SNAPSHOT_KEYS
    assert snapshot[ACCESSIBILITY_PACKAGE_NAME_KEY] == ACCESSIBILITY_ANDROID_PACKAGE
    assert isinstance(snapshot[AUTOMATION.KEY_SNAPSHOT_ID], str)
    assert isinstance(snapshot["nodeCount"], int)
    assert isinstance(snapshot["truncated"], bool)
    assert isinstance(snapshot["windowId"], int)
    nodes = snapshot["nodes"]
    assert isinstance(nodes, list)
    assert snapshot["nodeCount"] == len(nodes)
    assert nodes
    for node in nodes:
        assert isinstance(node, dict)
        assert set(node) == ACCESSIBILITY_NODE_KEYS
        assert isinstance(node["actions"], list)
        assert isinstance(node["bounds"], dict)
        assert set(node["bounds"]) == ACCESSIBILITY_NODE_BOUNDS_KEYS
        assert all(isinstance(value, int) for value in node["bounds"].values())
        assert isinstance(node["nodeId"], str)
        assert all(
            isinstance(node[key], bool)
            for key in (
                "checkable",
                "checked",
                "clickable",
                "editable",
                "enabled",
                "focusable",
                "scrollable",
                "visibleToUser",
            )
        )


def wait_for_accessibility_snapshot(connection: socket.socket) -> dict[str, Any]:
    deadline = time.monotonic() + ACCESSIBILITY_SERVICE_READY_TIMEOUT_SECONDS
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        try:
            snapshot = AUTOMATION.request(connection, AUTOMATION.TYPE_ACCESSIBILITY_SNAPSHOT)
            if snapshot.get(ACCESSIBILITY_PACKAGE_NAME_KEY) == ACCESSIBILITY_ANDROID_PACKAGE:
                return snapshot
        except AUTOMATION.WebSocketProtocolError as exception:
            last_error = exception
        time.sleep(ACCESSIBILITY_SNAPSHOT_POLL_SECONDS)
    raise AssertionError("accessibility service did not expose Android Settings") from last_error


def wait_for_settings_entry_snapshot(connection: socket.socket) -> dict[str, Any]:
    deadline = time.monotonic() + ACCESSIBILITY_SERVICE_READY_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        snapshot = wait_for_accessibility_snapshot(connection)
        if has_settings_entry(snapshot):
            return snapshot
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("Android Settings did not expose the target entry")


def wait_for_mcp_accessibility_snapshot(
    arguments: argparse.Namespace,
    session_id: str,
) -> dict[str, Any]:
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
        if (
            snapshot.get(ACCESSIBILITY_PACKAGE_NAME_KEY) == ACCESSIBILITY_ANDROID_PACKAGE
            and has_settings_entry(snapshot)
        ):
            return snapshot
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("MCP did not expose the expected Android Settings entry")


def has_settings_entry(snapshot: dict[str, Any]) -> bool:
    return any(
        node["packageName"] == ACCESSIBILITY_ANDROID_PACKAGE
        and node["visibleToUser"]
        and node["text"] == ACCESSIBILITY_SETTINGS_ENTRY_TEXT
        for node in snapshot["nodes"]
    )


def settings_entry(snapshot: dict[str, Any]) -> dict[str, Any]:
    text_nodes = [
        node
        for node in snapshot["nodes"]
        if node["packageName"] == ACCESSIBILITY_ANDROID_PACKAGE
        and node["visibleToUser"]
        and node["text"] == ACCESSIBILITY_SETTINGS_ENTRY_TEXT
    ]
    if not text_nodes:
        raise AssertionError("Settings snapshot did not expose the target entry")
    nodes_by_id = {
        node[AUTOMATION.KEY_NODE_ID]: node
        for node in snapshot["nodes"]
    }
    for text_node in text_nodes:
        node_id = text_node[AUTOMATION.KEY_NODE_ID]
        while ACCESSIBILITY_NODE_ID_SEPARATOR in node_id:
            node_id = node_id.rpartition(ACCESSIBILITY_NODE_ID_SEPARATOR)[0]
            candidate = nodes_by_id.get(node_id)
            if candidate is None:
                continue
            if (
                candidate["visibleToUser"]
                and candidate["enabled"]
                and candidate["clickable"]
                and ACCESSIBILITY_ACTION_CLICK in candidate["actions"]
            ):
                return candidate
    raise AssertionError("Settings target has no enabled clickable ancestor")


def node_center(node: dict[str, Any]) -> tuple[int, int]:
    bounds = node["bounds"]
    return (
        (bounds["left"] + bounds["right"]) // 2,
        (bounds["top"] + bounds["bottom"]) // 2,
    )


def wait_for_accessibility_action_log(record_count: int, action_type: str) -> None:
    deadline = time.monotonic() + ACCESSIBILITY_SERVICE_READY_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        records = device_log_records()[record_count:]
        if any(
            record.get("event") == AUTOMATION_ACTION_COMPLETED_EVENT
            and record.get("action_type") == action_type
            and record.get("event_type") == ACCESSIBILITY_EVENT
            and record.get("outcome") == ACCESSIBILITY_OUTCOME_EXECUTED
            and record.get("script_id") == AUTOMATION_LIVE_SCRIPT_ID
            for record in records
        ):
            return
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("Lua accessibility action did not produce an executed log record")


def assert_accessibility_owner_setup(status: dict[str, Any]) -> None:
    assert ACCESSIBILITY_CAPABILITY in status[AUTOMATION.KEY_CAPABILITIES]
    is_enabled = status[AUTOMATION.KEY_ANDROID_ACCESS][ACCESSIBILITY_CAPABILITY]
    assert isinstance(is_enabled, bool)
    requirement = next(
        item
        for item in status[AUTOMATION.KEY_DEVICE_ACCESS][AUTOMATION.KEY_REQUIREMENTS]
        if item[AUTOMATION.KEY_ID] == ACCESSIBILITY_CAPABILITY
    )
    assert requirement[AUTOMATION.KEY_KIND] == ACCESSIBILITY_DEVICE_ACCESS_KIND
    expected_state = (
        ACCESSIBILITY_DEVICE_ACCESS_GRANTED_STATE
        if is_enabled
        else ACCESSIBILITY_DEVICE_ACCESS_NEEDS_SETUP_STATE
    )
    assert requirement[AUTOMATION.KEY_STATE] == expected_state


def test_accessibility_owner_setup_status_is_typed_and_identical_through_both_control_planes(
    websocket_control: socket.socket,
) -> None:
    AUTOMATION.verify_automation_status(websocket_control)
    websocket_status = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_STATUS)
    assert_accessibility_owner_setup(websocket_status)

    arguments = argparse.Namespace(
        host=AUTOMATION_HOST,
        port=MCP_PORT,
        expected_control_port=DEVICE_MCP_PORT,
        mutate=False,
    )
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        MCP.verify_automation_status(
            arguments,
            session_id,
            ACCESSIBILITY_MCP_STATUS_VERIFICATION_REQUEST_ID,
        )
        mcp_status = MCP.require_tool_result(
            MCP.call_tool(
                arguments,
                session_id,
                ACCESSIBILITY_MCP_STATUS_REQUEST_ID,
                MCP.TOOL_AUTOMATION_STATUS,
            ),
        )
        assert mcp_status == websocket_status
    finally:
        MCP.delete_session(arguments, session_id)


def test_accessibility_service_inspects_and_acts_on_a_real_external_android_window_through_both_control_planes(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = automation_configuration(
        original,
        ACCESSIBILITY_SOURCE,
        ACCESSIBILITY_INITIAL_STATE,
        [ACCESSIBILITY_CAPABILITY],
        ["patchState"],
        ACCESSIBILITY_EVENT_SUBSCRIPTIONS,
    )
    try:
        emulator_dikciz_accessibility_set(False)
        disabled_status = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_STATUS)
        assert disabled_status["androidAccess"][ACCESSIBILITY_CAPABILITY] is False
        _, disabled_error = AUTOMATION.request_error_with_id(
            websocket_control,
            AUTOMATION.TYPE_ACCESSIBILITY_SNAPSHOT,
        )
        assert disabled_error[AUTOMATION.KEY_CODE] == ACCESSIBILITY_ERROR_NOT_ENABLED

        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        emulator_dikciz_accessibility_set(True)
        device_command(f"am force-stop {ACCESSIBILITY_ANDROID_PACKAGE}")
        device_command(f"am start -W -n {ACCESSIBILITY_SETTINGS_COMPONENT}")
        wait_for_foreground_application(ACCESSIBILITY_ANDROID_PACKAGE)

        websocket_snapshot = wait_for_settings_entry_snapshot(websocket_control)
        assert_accessibility_snapshot(websocket_snapshot)
        _, invalid_node_error = AUTOMATION.request_error_with_id(
            websocket_control,
            AUTOMATION.TYPE_ACCESSIBILITY_ACTION,
            action=ACCESSIBILITY_ACTION_CLICK,
            nodeId=ACCESSIBILITY_INVALID_NODE_ID,
            snapshotId=websocket_snapshot[AUTOMATION.KEY_SNAPSHOT_ID],
        )
        assert invalid_node_error[AUTOMATION.KEY_CODE] == ACCESSIBILITY_ERROR_NODE_UNAVAILABLE
        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            ACCESSIBILITY_EVENT_STATE_KEY,
            True,
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
            mcp_snapshot = wait_for_mcp_accessibility_snapshot(arguments, session_id)
            assert_accessibility_snapshot(mcp_snapshot)
            target = settings_entry(mcp_snapshot)
            action_result = MCP.require_tool_result(
                MCP.call_tool(
                    arguments,
                    session_id,
                    ACCESSIBILITY_MCP_ACTION_REQUEST_ID,
                    MCP.TOOL_ACCESSIBILITY_ACTION,
                    {
                        AUTOMATION.KEY_ACTION: ACCESSIBILITY_ACTION_CLICK,
                        AUTOMATION.KEY_NODE_ID: target[AUTOMATION.KEY_NODE_ID],
                        AUTOMATION.KEY_SNAPSHOT_ID: mcp_snapshot[AUTOMATION.KEY_SNAPSHOT_ID],
                    },
                ),
            )
            assert action_result["outcome"] == ACCESSIBILITY_OUTCOME_EXECUTED
            assert action_result[AUTOMATION.KEY_ACTION] == ACCESSIBILITY_ACTION_CLICK
            assert action_result[AUTOMATION.KEY_NODE_ID] == target[AUTOMATION.KEY_NODE_ID]
            assert action_result[AUTOMATION.KEY_SNAPSHOT_ID] == mcp_snapshot[AUTOMATION.KEY_SNAPSHOT_ID]
        finally:
            MCP.delete_session(arguments, session_id)

        wait_for_foreground_application(ACCESSIBILITY_ANDROID_PACKAGE)
        _, stale_snapshot_error = AUTOMATION.request_error_with_id(
            websocket_control,
            AUTOMATION.TYPE_ACCESSIBILITY_ACTION,
            action=ACCESSIBILITY_ACTION_CLICK,
            nodeId=target[AUTOMATION.KEY_NODE_ID],
            snapshotId=mcp_snapshot[AUTOMATION.KEY_SNAPSHOT_ID],
        )
        assert stale_snapshot_error[AUTOMATION.KEY_CODE] == ACCESSIBILITY_ERROR_SNAPSHOT_STALE

        lua_configured = automation_configuration(
            original,
            ACCESSIBILITY_LUA_ACTION_SOURCE,
            {},
            [ACCESSIBILITY_CAPABILITY],
            [ACCESSIBILITY_ACTION_CAPABILITY],
            ACCESSIBILITY_EVENT_SUBSCRIPTIONS,
        )
        log_record_count = len(device_log_records())
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=lua_configured,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        device_command(f"am force-stop {ACCESSIBILITY_ANDROID_PACKAGE}")
        device_command(f"am start -W -n {ACCESSIBILITY_SETTINGS_COMPONENT}")
        wait_for_foreground_application(ACCESSIBILITY_ANDROID_PACKAGE)
        wait_for_settings_destination(websocket_control)
        wait_for_accessibility_action_log(log_record_count, ACCESSIBILITY_ACTION_CAPABILITY)

        lua_gesture_configured = automation_configuration(
            original,
            ACCESSIBILITY_LUA_GESTURE_SOURCE,
            {},
            [ACCESSIBILITY_CAPABILITY],
            [ACCESSIBILITY_GESTURE_ACTION_CAPABILITY],
            ACCESSIBILITY_EVENT_SUBSCRIPTIONS,
        )
        log_record_count = len(device_log_records())
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=lua_gesture_configured,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        device_command(f"am force-stop {ACCESSIBILITY_ANDROID_PACKAGE}")
        device_command(f"am start -W -n {ACCESSIBILITY_SETTINGS_COMPONENT}")
        wait_for_foreground_application(ACCESSIBILITY_ANDROID_PACKAGE)
        wait_for_settings_destination(websocket_control)
        wait_for_accessibility_action_log(
            log_record_count,
            ACCESSIBILITY_GESTURE_ACTION_CAPABILITY,
        )

        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        device_command(f"am force-stop {ACCESSIBILITY_ANDROID_PACKAGE}")
        device_command(f"am start -W -n {ACCESSIBILITY_SETTINGS_COMPONENT}")
        wait_for_foreground_application(ACCESSIBILITY_ANDROID_PACKAGE)
        html_snapshot = wait_for_settings_entry_snapshot(websocket_control)
        html_target = settings_entry(html_snapshot)
        html_target_x, html_target_y = node_center(html_target)
        html_result = AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_AUTOMATION_DISPATCH,
            sourceWidgetAddress=ACCESSIBILITY_HTML_SOURCE_WIDGET_ADDRESS,
            actions=[
                {
                    "type": ACCESSIBILITY_GESTURE_ACTION_CAPABILITY,
                    "snapshotId": html_snapshot[AUTOMATION.KEY_SNAPSHOT_ID],
                    "gesture": ACCESSIBILITY_GESTURE_TAP,
                    "x": html_target_x,
                    "y": html_target_y,
                    "durationMilliseconds": ACCESSIBILITY_GESTURE_DURATION_MILLISECONDS,
                },
            ],
        )
        assert html_result == {
            "actions": [
                {
                    "type": ACCESSIBILITY_GESTURE_ACTION_CAPABILITY,
                    "outcome": ACCESSIBILITY_OUTCOME_EXECUTED,
                },
            ],
        }
        wait_for_settings_destination(websocket_control)
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / "accessibility-settings-action.png",
        )
        assert (ARTIFACT_DIRECTORY / "accessibility-settings-action.png").stat().st_size > 0
        updated_snapshot = wait_for_accessibility_snapshot(websocket_control)
        assert updated_snapshot[AUTOMATION.KEY_SNAPSHOT_ID] != websocket_snapshot[AUTOMATION.KEY_SNAPSHOT_ID]
        time.sleep(ACCESSIBILITY_SNAPSHOT_EXPIRY_WAIT_SECONDS)
        for _ in range(ACCESSIBILITY_MAXIMUM_SNAPSHOT_COUNT):
            assert_accessibility_snapshot(
                AUTOMATION.request(websocket_control, AUTOMATION.TYPE_ACCESSIBILITY_SNAPSHOT),
            )
        _, snapshot_limit_error = AUTOMATION.request_error_with_id(
            websocket_control,
            AUTOMATION.TYPE_ACCESSIBILITY_SNAPSHOT,
        )
        assert snapshot_limit_error[AUTOMATION.KEY_CODE] == ACCESSIBILITY_ERROR_SNAPSHOT_LIMIT
        html_back_result = AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_AUTOMATION_DISPATCH,
            sourceWidgetAddress=ACCESSIBILITY_HTML_SOURCE_WIDGET_ADDRESS,
            actions=[
                {
                    "type": ACCESSIBILITY_GLOBAL_ACTION_CAPABILITY,
                    "action": ACCESSIBILITY_GLOBAL_ACTION_BACK,
                },
            ],
        )
        assert html_back_result == {
            "actions": [
                {
                    "type": ACCESSIBILITY_GLOBAL_ACTION_CAPABILITY,
                    "outcome": ACCESSIBILITY_OUTCOME_EXECUTED,
                },
            ],
        }
        time.sleep(ACCESSIBILITY_SNAPSHOT_EXPIRY_WAIT_SECONDS)
        wait_for_settings_entry_snapshot(websocket_control)

        session_id = MCP.initialize(arguments)
        try:
            MCP.notification_initialized(arguments, session_id)
            mcp_home_result = MCP.require_tool_result(
                MCP.call_tool(
                    arguments,
                    session_id,
                    ACCESSIBILITY_MCP_ACTION_REQUEST_ID,
                    MCP.TOOL_AUTOMATION_DISPATCH,
                    {
                        "sourceWidgetAddress": ACCESSIBILITY_HTML_SOURCE_WIDGET_ADDRESS,
                        "actions": [
                            {
                                "type": ACCESSIBILITY_GLOBAL_ACTION_CAPABILITY,
                                "action": ACCESSIBILITY_GLOBAL_ACTION_HOME,
                            },
                        ],
                    },
                ),
            )
            assert mcp_home_result == {
                "actions": [
                    {
                        "type": ACCESSIBILITY_GLOBAL_ACTION_CAPABILITY,
                        "outcome": ACCESSIBILITY_OUTCOME_EXECUTED,
                    },
                ],
            }
        finally:
            MCP.delete_session(arguments, session_id)
        wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)
    finally:
        try:
            emulator_dikciz_accessibility_set(False)
            device_command(f"input keyevent {KEYCODE_HOME}")
            wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)
        finally:
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_CONFIG_REPLACE,
                config=original,
            )
