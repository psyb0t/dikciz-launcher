"""Exercise Android's visible permission flow before posting a clock notification."""

import json
import shutil
import time

from . import (
    ARTIFACT_DIRECTORY, AUTOMATION, COMMAND_SETTINGS_SEMANTIC_ID,
    DIKCIZ_DEBUG_PACKAGE, device_command, direct_tap, node_bounds,
    open_command_sheet, physical_nodes, run_device_operation, wait_for_snapshot_node,
    websocket_control,
)
from .test_alarm_cadence import CLOCK_TITLE, clock_notification
from .script_status import wait_script_result


PROMPT_TIMEOUT_SECONDS = 20
POLL_SECONDS = 1
NOTIFICATION_PERMISSION_API = 33
GRANT_NOTIFICATIONS = "settings:automation:grant-notifications"
CLOSE_ACCESS = "settings:automation:close"
OPEN_LISTENER = "settings:automation:open-notification-listener"
SCRIPT_ID = "hourly-clock"
SETTINGS_AUTOMATION = "settings:automation"


def capture_screen(name):
    run_device_operation("screenshot")
    shutil.copyfile(
        ARTIFACT_DIRECTORY / "screenshot.png",
        ARTIFACT_DIRECTORY / f"clock-permission-{name}.png",
    )


def tap_android_permission_button(resource_suffix):
    deadline = time.monotonic() + PROMPT_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        for node in physical_nodes():
            resource = node.get("resource-id", "")
            if not resource.endswith(resource_suffix):
                continue
            bounds = node_bounds(node)
            assert bounds is not None, resource
            capture_screen(resource_suffix.rsplit("/", 1)[-1])
            direct_tap(bounds)
            return
        time.sleep(POLL_SECONDS)
    capture_screen("unexpected-prompt")
    raise AssertionError(f"Android permission button not visible: {resource_suffix}")


def tap_access(connection, semantic_id):
    wait_for_snapshot_node(connection, semantic_id)
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=semantic_id)


def open_automation_settings(connection):
    open_command_sheet()
    tap_access(connection, COMMAND_SETTINGS_SEMANTIC_ID)
    tap_access(connection, SETTINGS_AUTOMATION)


def test_clock_notification_permission_ui(websocket_control):
    connection = websocket_control
    evidence = {"result": "running"}
    try:
        device_command("cmd statusbar collapse")
        AUTOMATION.request(connection, AUTOMATION.TYPE_RESET)
        api = int(device_command("getprop ro.build.version.sdk").strip())
        evidence["androidApi"] = api
        open_automation_settings(connection)
        capture_screen("requirements")
        if api >= NOTIFICATION_PERMISSION_API:
            tap_access(connection, GRANT_NOTIFICATIONS)
            tap_android_permission_button(":id/permission_deny_button")
            tap_access(connection, CLOSE_ACCESS)
            AUTOMATION.request(connection, AUTOMATION.TYPE_AUTOMATION_TRIGGER, scriptId=SCRIPT_ID)
            evidence["deniedScript"] = wait_script_result(SCRIPT_ID, "rejected")
            assert clock_notification() is None, "Denied script posted a notification"
            open_automation_settings(connection)
            tap_access(connection, GRANT_NOTIFICATIONS)
            tap_android_permission_button(":id/permission_allow_button")
            evidence["runtimePrompt"] = "denied_then_allowed_through_android_ui"
        else:
            evidence["runtimePrompt"] = "not_available_before_android_13"
        tap_access(connection, CLOSE_ACCESS)
        AUTOMATION.request(connection, AUTOMATION.TYPE_AUTOMATION_TRIGGER, scriptId=SCRIPT_ID)
        evidence["allowedScript"] = wait_script_result(SCRIPT_ID, "completed")
        deadline = time.monotonic() + PROMPT_TIMEOUT_SECONDS
        notification = clock_notification()
        while notification is None and time.monotonic() < deadline:
            time.sleep(POLL_SECONDS)
            notification = clock_notification()
        assert notification is not None, "Allowed script did not post an Android notification"
        evidence["notification"] = notification
        device_command("cmd statusbar expand-notifications")
        evidence["clockTitleVisible"] = any(
            CLOCK_TITLE in node.get("text", "") for node in physical_nodes()
        )
        assert evidence["clockTitleVisible"], "Posted clock notification is not visible in the shade"
        capture_screen("notification-shade")
        device_command("cmd statusbar collapse")
        open_automation_settings(connection)
        tap_access(connection, OPEN_LISTENER)
        nodes = physical_nodes()
        assert any(node.get("package") == "com.android.settings" for node in nodes)
        capture_screen("notification-listener-settings")
        evidence["listenerSettingsOpened"] = True
        evidence["result"] = "passed"
    finally:
        if evidence["result"] == "running":
            evidence["result"] = "failed"
        (ARTIFACT_DIRECTORY / "clock-permission-ui.json").write_text(
            json.dumps(evidence, indent=2), encoding="utf-8",
        )
        device_command("cmd statusbar collapse")
        run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
