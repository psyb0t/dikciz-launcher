"""Opt-in Device Administration state and screen-lock automation."""

from . import *


ACTION_LOCK_DEVICE = "lockDevice"
ACTION_PATCH_STATE = "patchState"
AUTOMATION_ACTION_COMPLETED_EVENT = "automation_action_completed"
AUTOMATION_ACTION_REJECTED_EVENT = "automation_action_rejected"
DEVICE_ADMIN_CAPABILITY = "deviceAdministration"
DEVICE_ADMIN_COMPONENT = f"{DIKCIZ_DEBUG_PACKAGE}/org.fossify.home.dikciz.DikcizDeviceAdminReceiver"
DEVICE_ADMIN_EVENT = "deviceAdminState"
DEVICE_ADMIN_STATE_ACCESS_KEY = "deviceAdminActive"
DEVICE_ADMIN_STATE_KEY = "active"
DEVICE_ADMIN_STATE_WAITING = "waiting"
DEVICE_ADMIN_SOURCE = (
    'function on_event(event)\n'
    '  if event.type ~= "deviceAdminState" then return end\n'
    '  return {\n'
    '    { type = "patchState", values = { active = event.payload.active } },\n'
    '    { type = "lockDevice" }\n'
    '  }\n'
    'end'
)
DUMPSYS_DEVICE_POLICY_COMMAND = "dumpsys device_policy"
DPM_SET_ADMIN_COMMAND = f"dpm set-active-admin --user 0 {DEVICE_ADMIN_COMPONENT}"
DEBUG_DEVICE_ADMINISTRATION_DEACTIVATE_ACTION = (
    "org.fossify.home.dikciz.action.DEBUG_DEVICE_ADMINISTRATION_DEACTIVATE"
)
DEBUG_DEVICE_ADMINISTRATION_DEACTIVATE_COMPONENT = (
    f"{DIKCIZ_DEBUG_PACKAGE}/org.fossify.home.dikciz.DikcizDebugDeviceAdministrationReceiver"
)
DEBUG_DEVICE_ADMINISTRATION_DEACTIVATE_COMMAND = " ".join(
    (
        "am broadcast",
        "-a",
        DEBUG_DEVICE_ADMINISTRATION_DEACTIVATE_ACTION,
        "-n",
        DEBUG_DEVICE_ADMINISTRATION_DEACTIVATE_COMPONENT,
    ),
)
KEYCODE_WAKEUP_COMMAND = "input keyevent KEYCODE_WAKEUP"
MCP_AUTOMATION_STATUS_COMPARISON_REQUEST_ID = 81
MCP_AUTOMATION_STATUS_REQUEST_ID = 80
MCP_MUTATE = False
POWER_DUMP_COMMAND = "dumpsys power"
POWER_ASLEEP_STATE = "mWakefulness=Asleep"
REASON_CAPABILITY_DENIED = "capability_denied"


@pytest.fixture
def device_administration_cleanup() -> Generator[None, None, None]:
    remove_device_administrator()
    device_command(KEYCODE_WAKEUP_COMMAND)
    try:
        yield
    finally:
        remove_device_administrator()
        device_command(KEYCODE_WAKEUP_COMMAND)


def assert_mcp_automation_status(expected_status: dict[str, Any]) -> None:
    arguments = argparse.Namespace(
        host=AUTOMATION_HOST,
        port=MCP_PORT,
        expected_control_port=DEVICE_MCP_PORT,
        mutate=MCP_MUTATE,
    )
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        MCP.verify_automation_status(arguments, session_id, MCP_AUTOMATION_STATUS_REQUEST_ID)
        actual_status = MCP.require_tool_result(
            MCP.call_tool(
                arguments,
                session_id,
                MCP_AUTOMATION_STATUS_COMPARISON_REQUEST_ID,
                MCP.TOOL_AUTOMATION_STATUS,
            ),
        )
        assert actual_status == expected_status
    finally:
        MCP.delete_session(arguments, session_id)


def device_administration_configuration(
    original: dict[str, Any],
    capabilities: list[str],
    actions: list[str],
) -> dict[str, Any]:
    return automation_configuration(
        original,
        DEVICE_ADMIN_SOURCE,
        {DEVICE_ADMIN_STATE_KEY: DEVICE_ADMIN_STATE_WAITING},
        capabilities,
        actions,
        [
            {
                "event": DEVICE_ADMIN_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": DEVICE_ADMIN_EVENT,
            },
        ],
    )


def remove_device_administrator() -> None:
    if DEVICE_ADMIN_COMPONENT not in device_command(DUMPSYS_DEVICE_POLICY_COMMAND):
        return
    device_command(DEBUG_DEVICE_ADMINISTRATION_DEACTIVATE_COMMAND)
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        if DEVICE_ADMIN_COMPONENT not in device_command(DUMPSYS_DEVICE_POLICY_COMMAND):
            return
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("Dikciz Device Administration receiver remained active after debug cleanup")


def wait_for_action(record_count: int, event: str, outcome: str) -> None:
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        records = device_log_records()[record_count:]
        if any(
            record.get("event") == event
            and record.get("event_type") == DEVICE_ADMIN_EVENT
            and record.get("action_type") == ACTION_LOCK_DEVICE
            and record.get("outcome") == outcome
            for record in records
        ):
            return
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"Device Administration action did not report {event}: {outcome}")


def test_automation_device_administration_is_opt_in_policy_gated_and_uses_real_device_policy_manager(
    websocket_control: socket.socket,
    device_administration_cleanup: None,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    denied_configuration = device_administration_configuration(
        original,
        [],
        [ACTION_PATCH_STATE, ACTION_LOCK_DEVICE],
    )
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=denied_configuration,
    )
    time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
    device_command(DPM_SET_ADMIN_COMMAND)
    time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
    denied_configuration_after_event = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    denied_script = next(
        script
        for script in denied_configuration_after_event["scripts"]
        if script["id"] == AUTOMATION_LIVE_SCRIPT_ID
    )
    assert denied_script["state"] == {DEVICE_ADMIN_STATE_KEY: DEVICE_ADMIN_STATE_WAITING}
    assert POWER_ASLEEP_STATE not in device_command(POWER_DUMP_COMMAND)

    active_status = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_STATUS)
    assert DEVICE_ADMIN_CAPABILITY in active_status["capabilities"]
    assert ACTION_LOCK_DEVICE in active_status["actions"]
    assert active_status["androidAccess"][DEVICE_ADMIN_STATE_ACCESS_KEY] is True
    event = next(item for item in active_status["events"] if item["type"] == DEVICE_ADMIN_EVENT)
    assert event["requiredCapability"] == DEVICE_ADMIN_CAPABILITY
    assert event["requiredSubscriptionFields"] == []
    assert event["optionalSubscriptionFields"] == []
    assert_mcp_automation_status(active_status)
    remove_device_administrator()
    device_command(KEYCODE_WAKEUP_COMMAND)

    action_denied_configuration = device_administration_configuration(
        original,
        [DEVICE_ADMIN_CAPABILITY],
        [ACTION_PATCH_STATE],
    )
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=action_denied_configuration,
    )
    time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
    action_denied_record_count = len(device_log_records())
    device_command(DPM_SET_ADMIN_COMMAND)
    wait_for_automation_script_state(
        websocket_control,
        AUTOMATION_LIVE_SCRIPT_ID,
        DEVICE_ADMIN_STATE_KEY,
        True,
    )
    wait_for_action(
        action_denied_record_count,
        AUTOMATION_ACTION_REJECTED_EVENT,
        REASON_CAPABILITY_DENIED,
    )
    assert POWER_ASLEEP_STATE not in device_command(POWER_DUMP_COMMAND)
    remove_device_administrator()
    device_command(KEYCODE_WAKEUP_COMMAND)

    allowed_configuration = device_administration_configuration(
        original,
        [DEVICE_ADMIN_CAPABILITY],
        [ACTION_PATCH_STATE, ACTION_LOCK_DEVICE],
    )
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=allowed_configuration,
    )
    time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
    allowed_record_count = len(device_log_records())
    device_command(DPM_SET_ADMIN_COMMAND)
    wait_for_automation_script_state(
        websocket_control,
        AUTOMATION_LIVE_SCRIPT_ID,
        DEVICE_ADMIN_STATE_KEY,
        True,
    )
    wait_for_action(allowed_record_count, AUTOMATION_ACTION_COMPLETED_EVENT, "executed")
    assert POWER_ASLEEP_STATE in device_command(POWER_DUMP_COMMAND)
    device_command(KEYCODE_WAKEUP_COMMAND)
    remove_device_administrator()
    wait_for_automation_script_state(
        websocket_control,
        AUTOMATION_LIVE_SCRIPT_ID,
        DEVICE_ADMIN_STATE_KEY,
        False,
    )
    inactive_status = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_STATUS)
    assert inactive_status["androidAccess"][DEVICE_ADMIN_STATE_ACCESS_KEY] is False
    assert_mcp_automation_status(inactive_status)
