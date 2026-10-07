"""Accessibility-revocation failures through the installed Android runtime."""

from . import *
from .script_status import wait_script_result


ACCESSIBILITY_REVOCATION_ACTION = "accessibilityAction"
ACCESSIBILITY_REVOCATION_CAPABILITIES = ["accessibility", "manualTrigger"]
ACCESSIBILITY_REVOCATION_EVENT = "manual"
ACCESSIBILITY_REVOCATION_LOG_EVENT = "automation_action_rejected"
ACCESSIBILITY_REVOCATION_LOGGING_NOTIFY_ON_SCRIPT_ERROR_KEY = "notifyOnScriptError"
ACCESSIBILITY_REVOCATION_MCP_REQUEST_ID = 4201
ACCESSIBILITY_REVOCATION_NODE_ACTION = "click"
ACCESSIBILITY_REVOCATION_NOTIFICATION_EVENT = "script_error_notification_posted"
ACCESSIBILITY_REVOCATION_NOTIFICATION_TITLE = "Dikciz script failed"
ACCESSIBILITY_REVOCATION_OUTCOME = "accessibility_not_enabled"
ACCESSIBILITY_REVOCATION_SCRIPT_STATUS = "Action rejected: accessibility_not_enabled"


def revocation_source(snapshot_id: str, node_id: str) -> str:
    return "\n".join(
        (
            "function on_event(event)",
            '  if event.type ~= "manual" then return { status = "ignored" } end',
            "  return {",
            '    status = "opening Android Settings",',
            "    actions = { {",
            '      type = "accessibilityAction",',
            f'      snapshotId = "{snapshot_id}",',
            f'      nodeId = "{node_id}",',
            '      action = "click"',
            "    } }",
            "  }",
            "end",
        ),
    )


def wait_for_settings_snapshot(connection: socket.socket) -> tuple[dict[str, Any], dict[str, Any]]:
    deadline = time.monotonic() + ACCESSIBILITY_SERVICE_READY_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        snapshot = AUTOMATION.request(connection, AUTOMATION.TYPE_ACCESSIBILITY_SNAPSHOT)
        nodes = {node[AUTOMATION.KEY_NODE_ID]: node for node in snapshot["nodes"]}
        for node in nodes.values():
            if node["text"] != ACCESSIBILITY_SETTINGS_ENTRY_TEXT:
                continue
            node_id = node[AUTOMATION.KEY_NODE_ID]
            while ACCESSIBILITY_NODE_ID_SEPARATOR in node_id:
                node_id = node_id.rpartition(ACCESSIBILITY_NODE_ID_SEPARATOR)[0]
                candidate = nodes.get(node_id)
                if candidate is None:
                    continue
                if (
                    candidate["enabled"]
                    and candidate["clickable"]
                    and ACCESSIBILITY_REVOCATION_NODE_ACTION in candidate["actions"]
                ):
                    return snapshot, candidate
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("Accessibility snapshot did not expose the Android Settings action")


def wait_for_disabled_accessibility(connection: socket.socket) -> None:
    deadline = time.monotonic() + ACCESSIBILITY_SERVICE_READY_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        status = AUTOMATION.request(connection, AUTOMATION.TYPE_AUTOMATION_STATUS)
        if status["androidAccess"][ACCESSIBILITY_CAPABILITY] is False:
            return
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("Android Accessibility service remained enabled after revocation")


def wait_for_revocation_records(record_count: int) -> list[dict[str, Any]]:
    deadline = time.monotonic() + ACCESSIBILITY_SERVICE_READY_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        records = device_log_records()[record_count:]
        script_records = [
            record
            for record in records
            if record.get("script_id") == AUTOMATION_LIVE_SCRIPT_ID
        ]
        events = {record.get("event") for record in script_records}
        if {
            ACCESSIBILITY_REVOCATION_LOG_EVENT,
            ACCESSIBILITY_REVOCATION_NOTIFICATION_EVENT,
        } <= events:
            return script_records
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("Accessibility revocation did not produce action and notification logs")


def mcp_script_logs() -> dict[str, Any]:
    arguments = argparse.Namespace(host=AUTOMATION_HOST, port=MCP_PORT, mutate=False)
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        return MCP.require_tool_result(
            MCP.call_tool(
                arguments,
                session_id,
                ACCESSIBILITY_REVOCATION_MCP_REQUEST_ID,
                MCP.TOOL_SCRIPT_LOGS,
            ),
        )
    finally:
        MCP.delete_session(arguments, session_id)


def test_accessibility_revocation_rejects_background_action_and_notifies(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    try:
        device_command(f"pm grant {DIKCIZ_DEBUG_PACKAGE} android.permission.POST_NOTIFICATIONS")
        emulator_dikciz_accessibility_set(True)
        device_command(f"am force-stop {ACCESSIBILITY_ANDROID_PACKAGE}")
        device_command(f"am start -W -n {ACCESSIBILITY_SETTINGS_COMPONENT}")
        snapshot, target = wait_for_settings_snapshot(websocket_control)
        configured = automation_configuration(
            original,
            revocation_source(
                snapshot[AUTOMATION.KEY_SNAPSHOT_ID],
                target[AUTOMATION.KEY_NODE_ID],
            ),
            {},
            ACCESSIBILITY_REVOCATION_CAPABILITIES,
            [ACCESSIBILITY_REVOCATION_ACTION],
            [
                {
                    "event": ACCESSIBILITY_REVOCATION_EVENT,
                    "minimumIntervalMilliseconds": 0,
                    "coalescingKey": ACCESSIBILITY_REVOCATION_EVENT,
                },
            ],
        )
        configured["logging"][ACCESSIBILITY_REVOCATION_LOGGING_NOTIFY_ON_SCRIPT_ERROR_KEY] = True
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        )
        emulator_dikciz_accessibility_set(False)
        wait_for_disabled_accessibility(websocket_control)

        direct_record_count = len(device_log_records())
        _, direct_error = AUTOMATION.request_error_with_id(
            websocket_control,
            AUTOMATION.TYPE_ACCESSIBILITY_ACTION,
            action=ACCESSIBILITY_REVOCATION_NODE_ACTION,
            nodeId=target[AUTOMATION.KEY_NODE_ID],
            snapshotId=snapshot[AUTOMATION.KEY_SNAPSHOT_ID],
        )
        assert direct_error[AUTOMATION.KEY_CODE] == ACCESSIBILITY_REVOCATION_OUTCOME
        assert not any(
            record.get("event") == ACCESSIBILITY_REVOCATION_NOTIFICATION_EVENT
            and record.get("script_id") == AUTOMATION_LIVE_SCRIPT_ID
            for record in device_log_records()[direct_record_count:]
        )

        record_count = len(device_log_records())
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_AUTOMATION_TRIGGER,
            scriptId=AUTOMATION_LIVE_SCRIPT_ID,
        )
        records = wait_for_revocation_records(record_count)
        rejected = next(
            record
            for record in records
            if record["event"] == ACCESSIBILITY_REVOCATION_LOG_EVENT
        )
        assert rejected["action_type"] == ACCESSIBILITY_REVOCATION_ACTION
        assert rejected["outcome"] == ACCESSIBILITY_REVOCATION_OUTCOME
        assert wait_script_result(AUTOMATION_LIVE_SCRIPT_ID, "rejected")["status"] == (
            ACCESSIBILITY_REVOCATION_SCRIPT_STATUS
        )
        assert ACCESSIBILITY_REVOCATION_NOTIFICATION_TITLE in device_command(
            SCRIPT_DIAGNOSTICS_NOTIFICATION_DUMP_COMMAND,
        )

        websocket_logs = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SCRIPT_LOGS)
        notification_records = [
            record
            for record in websocket_logs[AUTOMATION.KEY_SCRIPT_LOG_RECORDS]
            if record[AUTOMATION.KEY_SCRIPT_LOG_EVENT]
            == ACCESSIBILITY_REVOCATION_NOTIFICATION_EVENT
            and record[AUTOMATION.KEY_SCRIPT_LOG_SCRIPT_ID] == AUTOMATION_LIVE_SCRIPT_ID
        ]
        assert notification_records
        # Reading the log appends to it, so the second plane asked always sees
        # the request that asked first. What the planes owe each other is the
        # records that already existed, not an identical snapshot.
        mcp_logs = mcp_script_logs()
        assert mcp_logs.keys() == websocket_logs.keys()
        mcp_records = mcp_logs[AUTOMATION.KEY_SCRIPT_LOG_RECORDS]
        assert all(record in mcp_records for record in notification_records)
    finally:
        emulator_dikciz_accessibility_set(False)
        device_command(f"pm revoke {DIKCIZ_DEBUG_PACKAGE} android.permission.POST_NOTIFICATIONS")
        restore_system_home()
