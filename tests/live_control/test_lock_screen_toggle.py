"""Verify lock actions never open Device Administration implicitly."""

from . import *
from .test_automation_device_administration import (
    ACTION_LOCK_DEVICE,
    DEVICE_ADMIN_COMPONENT,
    DEVICE_ADMIN_CAPABILITY,
    DUMPSYS_DEVICE_POLICY_COMMAND,
    DPM_SET_ADMIN_COMMAND,
    KEYCODE_WAKEUP_COMMAND,
    POWER_ASLEEP_STATE,
    POWER_DUMP_COMMAND,
    device_administration_cleanup,
    automation_configuration,
    remove_device_administrator,
)


KEYCODE_DISMISS_KEYGUARD_COMMAND = "wm dismiss-keyguard"
ACTION_RESULTS_KEY = "actions"
ACTION_TYPE_KEY = "type"
ACTION_OUTCOME_KEY = "outcome"
AUTOMATION_TRIGGER_EVENT = "manual"
LOCK_ACTION_SOURCE_WIDGET_ADDRESS = "1H1V-welcome"
LOCK_ACTION_RESULT_EXECUTED = "executed"
LOCK_ACTION_RESULT_ADMIN_INACTIVE = "device_admin_inactive"
MANUAL_LOCK_SOURCE = (
    "function on_event(event)\n"
    f"  if event.type ~= \"{AUTOMATION_TRIGGER_EVENT}\" then return end\n"
    "  return { actions = { { type = \"lockDevice\" } } }\n"
    "end"
)
MANUAL_LOCK_SUBSCRIPTION = [
    {
        "event": AUTOMATION_TRIGGER_EVENT,
        "minimumIntervalMilliseconds": 0,
        "coalescingKey": "manual-lock",
    },
]


def wait_for_power_state(expected_asleep: bool) -> None:
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        asleep = POWER_ASLEEP_STATE in device_command(POWER_DUMP_COMMAND)
        if asleep == expected_asleep:
            return
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"Device did not reach asleep={expected_asleep}")


def lock_action_configuration(original: dict[str, Any]) -> dict[str, Any]:
    return automation_configuration(
        original,
        MANUAL_LOCK_SOURCE,
        {},
        [DEVICE_ADMIN_CAPABILITY, "manualTrigger"],
        [ACTION_LOCK_DEVICE],
        MANUAL_LOCK_SUBSCRIPTION,
    )


def dispatch_lock_action(connection: socket.socket) -> dict[str, object]:
    return AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_AUTOMATION_DISPATCH,
        sourceWidgetAddress=LOCK_ACTION_SOURCE_WIDGET_ADDRESS,
        actions=[{ACTION_TYPE_KEY: ACTION_LOCK_DEVICE}],
    )


def assert_lock_action_outcome(result: dict[str, object], expected_outcome: str) -> None:
    assert result == {
        ACTION_RESULTS_KEY: [
            {
                ACTION_TYPE_KEY: ACTION_LOCK_DEVICE,
                ACTION_OUTCOME_KEY: expected_outcome,
            },
        ],
    }


def wait_for_manual_lock_outcome(record_count: int, expected_outcome: str) -> None:
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        records = device_log_records()[record_count:]
        if any(
            record.get("event_type") == AUTOMATION_TRIGGER_EVENT
            and record.get("action_type") == ACTION_LOCK_DEVICE
            and record.get("outcome") == expected_outcome
            for record in records
        ):
            return
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"Manual lock action did not report {expected_outcome}")


def test_lock_actions_report_real_outcomes_without_opening_android_settings(
    websocket_control: socket.socket,
    device_administration_cleanup: None,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    try:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=lock_action_configuration(original),
        )
        device_command(DPM_SET_ADMIN_COMMAND)
        assert DEVICE_ADMIN_COMPONENT in device_command(DUMPSYS_DEVICE_POLICY_COMMAND)
        wait_for_power_state(expected_asleep=False)

        assert_lock_action_outcome(
            dispatch_lock_action(websocket_control),
            LOCK_ACTION_RESULT_EXECUTED,
        )
        wait_for_power_state(expected_asleep=True)
        run_device_operation("screenshot")
        device_command(KEYCODE_WAKEUP_COMMAND)
        device_command(KEYCODE_DISMISS_KEYGUARD_COMMAND)

        remove_device_administrator()
        assert DEVICE_ADMIN_COMPONENT not in device_command(DUMPSYS_DEVICE_POLICY_COMMAND)
        assert_lock_action_outcome(
            dispatch_lock_action(websocket_control),
            LOCK_ACTION_RESULT_ADMIN_INACTIVE,
        )
        wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)

        record_count = len(device_log_records())
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_AUTOMATION_TRIGGER,
            scriptId=AUTOMATION_LIVE_SCRIPT_ID,
        )
        wait_for_manual_lock_outcome(record_count, LOCK_ACTION_RESULT_ADMIN_INACTIVE)
        wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)
        run_device_operation("screenshot")
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
