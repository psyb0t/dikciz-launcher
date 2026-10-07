"""Bundled cross-app Settings walkthrough on the installed launcher."""

from . import *
from .accessibility_support import wait_for_settings_destination
from .script_status import wait_script_result


def bundled_settings_walkthrough(configuration: dict[str, Any]) -> dict[str, Any]:
    scripts = configuration["scripts"]
    return next(
        script
        for script in scripts
        if script["id"] == SETTINGS_WALKTHROUGH_SCRIPT_ID
    )


def wait_for_settings_walkthrough_action(record_count: int) -> None:
    deadline = time.monotonic() + ACCESSIBILITY_SERVICE_READY_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        records = device_log_records()[record_count:]
        if any(
            record.get("event") == AUTOMATION_ACTION_COMPLETED_EVENT
            and record.get("action_type") == SETTINGS_WALKTHROUGH_ACCESSIBILITY_ACTION
            and record.get("event_type") == ACCESSIBILITY_EVENT
            and record.get("outcome") == ACCESSIBILITY_OUTCOME_EXECUTED
            and record.get("script_id") == SETTINGS_WALKTHROUGH_SCRIPT_ID
            for record in records
        ):
            return
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("Bundled Settings walkthrough did not log its semantic action")


def test_bundled_settings_walkthrough_reports_owner_setup_and_acts_on_live_settings_node(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    try:
        emulator_dikciz_accessibility_set(False)
        bundled = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_RESET),
        )
        script = bundled_settings_walkthrough(bundled)
        assert script["enabled"] is True

        disabled_record_count = len(device_log_records())
        assert AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_AUTOMATION_TRIGGER,
            scriptId=SETTINGS_WALKTHROUGH_SCRIPT_ID,
        ) == {"scriptId": SETTINGS_WALKTHROUGH_SCRIPT_ID}
        disabled_result = wait_script_result(SETTINGS_WALKTHROUGH_SCRIPT_ID, "completed")
        assert disabled_result["status"] == SETTINGS_WALKTHROUGH_DISABLED_STATUS
        assert not any(
            record.get("script_id") == SETTINGS_WALKTHROUGH_SCRIPT_ID
            and record.get("event") == AUTOMATION_ACTION_COMPLETED_EVENT
            for record in device_log_records()[disabled_record_count:]
        )
        wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)

        action_record_count = len(device_log_records())
        emulator_dikciz_accessibility_set(True)
        assert AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_AUTOMATION_TRIGGER,
            scriptId=SETTINGS_WALKTHROUGH_SCRIPT_ID,
        ) == {"scriptId": SETTINGS_WALKTHROUGH_SCRIPT_ID}
        wait_for_foreground_application(ACCESSIBILITY_ANDROID_PACKAGE)
        wait_for_settings_destination(websocket_control)
        wait_for_settings_walkthrough_action(action_record_count)

        run_device_operation("screenshot")
        screenshot_path = ARTIFACT_DIRECTORY / "screenshot.png"
        walkthrough_screenshot_path = ARTIFACT_DIRECTORY / "settings-walkthrough.png"
        shutil.copyfile(screenshot_path, walkthrough_screenshot_path)
        assert walkthrough_screenshot_path.stat().st_size > 0
    finally:
        try:
            emulator_dikciz_accessibility_set(False)
        finally:
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_CONFIG_REPLACE,
                config=original,
            )
            restore_system_home()
