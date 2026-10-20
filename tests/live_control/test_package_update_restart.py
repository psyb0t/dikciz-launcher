"""Real PackageManager replacement must recover the updated launcher."""

import re
import time

from . import *
from .test_remote_device_control import remote_call


UPDATE_MODES = ("home", "overlay", "notification", "no_notifications")
UPDATE_TITLE = "Dikciz updated"
RECOVERY_SECONDS = 40
NOTIFICATION_PERMISSION = "android.permission.POST_NOTIFICATIONS"
OVERLAY_OPERATION = "SYSTEM_ALERT_WINDOW"
HOME_ROLE_COMMAND = "cmd role get-role-holders --user 0 android.app.role.HOME"


def wait_for_updated_launcher(before):
    deadline = time.monotonic() + RECOVERY_SECONDS
    last_error = None
    while time.monotonic() < deadline:
        try:
            after = remote_call("http", "deviceInfo")["application"]
            if after["instanceId"] != before["instanceId"]:
                assert after["processId"] != before["processId"]
                assert after["packageName"] == before["packageName"]
                assert after["versionName"] == before["versionName"]
                assert after["versionCode"] == before["versionCode"]
                return after
        except AssertionError as exception:
            last_error = exception
        time.sleep(0.5)
    raise AssertionError("updated package did not reopen without an explicit launch") from last_error


@pytest.mark.parametrize("mode", UPDATE_MODES)
def test_package_update_restart_preserves_configuration_and_connections(mode):
    assert DIKCIZ_DEBUG_APK_PATH
    original_home = device_command(HOME_ROLE_COMMAND).strip()
    overlay_state = device_command(f"appops get {DIKCIZ_DEBUG_PACKAGE} {OVERLAY_OPERATION}")
    overlay_match = re.search(r"SYSTEM_ALERT_WINDOW: (allow|ignore|deny|default)", overlay_state)
    original_overlay = overlay_match.group(1) if overlay_match else "default"
    permissions = device_command(f"dumpsys package {DIKCIZ_DEBUG_PACKAGE}")
    notifications_granted = f"{NOTIFICATION_PERMISSION}: granted=true" in permissions
    before = remote_call("http", "deviceInfo")["application"]
    configuration = remote_call("http", "configGet")
    try:
        if mode != "home":
            candidates = device_command(
                "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
            ).splitlines()
            other_home = next(
                line.split("/", 1)[0].strip() for line in candidates
                if "/" in line and not line.strip().startswith(DIKCIZ_DEBUG_PACKAGE)
            )
            run_device_operation("home-role-set", PACKAGE_NAME=other_home)
        overlay_mode = "allow" if mode == "overlay" else "deny"
        device_command(f"appops set {DIKCIZ_DEBUG_PACKAGE} {OVERLAY_OPERATION} {overlay_mode}")
        notification_mode = "revoke" if mode == "no_notifications" else "grant"
        device_command(f"pm {notification_mode} {DIKCIZ_DEBUG_PACKAGE} {NOTIFICATION_PERMISSION}")
        device_command(f"am start -n {SETTINGS_ACTIVITY_COMPONENT}")
        run_device_operation("install", APK=DIKCIZ_DEBUG_APK_PATH)
        if mode == "notification":
            device_command("cmd statusbar expand-notifications")
            direct_tap(wait_for_physical_text_bounds(UPDATE_TITLE))
        elif mode == "no_notifications":
            time.sleep(3)
            device_command("cmd statusbar expand-notifications")
            assert all(node.get("text") != UPDATE_TITLE for node in physical_nodes())
            device_command("cmd statusbar collapse")
            run_device_operation("app-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE,
                                 ACTIVITY="org.fossify.home.dikciz.DikcizHomeActivity")
        after = wait_for_updated_launcher(before)
        assert remote_call("http", "configGet") == configuration
        for protocol in ("websocket", "mcp"):
            assert remote_call(protocol, "deviceInfo")["application"]["instanceId"] == after["instanceId"]
        assert DIKCIZ_DEBUG_PACKAGE in run_device_operation("app-current")
        run_device_operation("screenshot")
        device_command("cmd statusbar expand-notifications")
        assert all(node.get("text") != UPDATE_TITLE for node in physical_nodes())
        device_command("cmd statusbar collapse")
    finally:
        device_command("cmd statusbar collapse")
        device_command(f"appops set {DIKCIZ_DEBUG_PACKAGE} {OVERLAY_OPERATION} {original_overlay}")
        restore_notifications = "grant" if notifications_granted else "revoke"
        device_command(f"pm {restore_notifications} {DIKCIZ_DEBUG_PACKAGE} {NOTIFICATION_PERMISSION}")
        run_device_operation("home-role-set", PACKAGE_NAME=original_home)
        run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
