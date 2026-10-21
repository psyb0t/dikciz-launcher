"""Full-device setup survives real Android window and lifecycle handoffs."""

import base64
import logging
import shlex
import shutil

from . import *
from . import test_remote_device_control as REMOTE
from .test_device_access_setup import FULL_ACCESS, NEXT_ACCESS, CLOSE_ACCESS, OPEN_SETUP, ROLE_COMMAND
from .test_launcher_regressions import recent_task


REMOTE.TOOLS.update({
    "tap": "dikciz_tap", "find": "dikciz_find", "automationStatus": "dikciz_automation_status",
    "screenshot": "dikciz_screenshot",
    "scrollTo": "dikciz_scroll_to", "scrollBy": "dikciz_scroll_by",
})
logger = logging.getLogger(__name__)
SETUP_ACTIVITY = "org.fossify.home.dikciz.DikcizDeviceAccessActivity"
SETUP_COMPONENT = DIKCIZ_DEBUG_PACKAGE + "/" + SETUP_ACTIVITY
SETUP_SCREEN = "deviceAccess"
SETUP_TITLE = "Full device access"
ENTRY_PREFIX = "settings:device-access:setup:"
PROGRESS = "settings:device-access:progress"
WAIT_SECONDS = 60
CONTACT_PERMISSIONS = ("android.permission.READ_CONTACTS", "android.permission.WRITE_CONTACTS")
BATTERY_ACCESS = "ignoreBatteryOptimizations"


def open_setup(connection):
    AUTOMATION.request(connection, OPEN_SETUP)
    wait_for_snapshot_node(connection, FULL_ACCESS)
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=FULL_ACCESS)
    return wait_for_snapshot_node(connection, NEXT_ACCESS)


def setup_task():
    dump = device_command("dumpsys activity activities")
    resumed = [line for line in dump.splitlines() if "topResumedActivity=" in line]
    assert resumed and SETUP_ACTIVITY in resumed[0], dump
    return recent_task(SETUP_COMPONENT)


def close_setup():
    snapshot = REMOTE.remote_call("http", "snapshot")
    if snapshot["screen"] == SETUP_SCREEN:
        REMOTE.remote_call("http", "tap", {"semanticId": CLOSE_ACCESS})
    restore_system_home()


def access_entry(requirement):
    status = REMOTE.remote_call("http", "automationStatus")
    return next(entry for entry in status["deviceAccess"]["requirements"] if entry["id"] == requirement)


def wait_setup():
    deadline = time.monotonic() + WAIT_SECONDS
    last_error = None
    while time.monotonic() < deadline:
        try:
            result = REMOTE.remote_call("http", "snapshot")
            if result["screen"] == SETUP_SCREEN:
                return result
        except AssertionError as exception:
            last_error = exception
            logger.debug("Waiting for restored setup command host", extra={"error_class": type(exception).__name__})
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
            continue
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("Setup did not resume through the running command host") from last_error


@pytest.mark.parametrize("protocol", ("websocket", "mcp", "http"))
def test_device_access_screen_is_a_standard_task_with_shared_controls(websocket_control, protocol):
    role = device_command(ROLE_COMMAND).strip()
    configuration = REMOTE.remote_call("http", "configGet")
    try:
        snapshot = open_setup(websocket_control)
        assert snapshot["screen"] == SETUP_SCREEN
        task = setup_task()
        wait_for_physical_text_bounds(SETUP_TITLE)
        snapshot = REMOTE.remote_call(protocol, "snapshot")
        assert snapshot["screen"] == SETUP_SCREEN
        status = REMOTE.remote_call("http", "automationStatus")["deviceAccess"]
        progress = AUTOMATION.require_node(snapshot, PROGRESS)["text"]
        assert progress.startswith(str(status["grantedCount"]) + " of " + str(status["requirementCount"]))
        assert all(not node["semanticId"].startswith("widget:") for node in snapshot["nodes"])
        assert REMOTE.remote_call(protocol, "find", {"semanticId": CLOSE_ACCESS})["found"] is True
        assert recent_task(SETUP_COMPONENT, task) == task
        captured = REMOTE.remote_call(protocol, "screenshot")
        image = base64.b64decode(captured["pngBase64"], validate=True)
        assert image.startswith(b"\x89PNG\r\n\x1a\n")
        (ARTIFACT_DIRECTORY / ("device-access-api-" + protocol + ".png")).write_bytes(image)
        last_access = ENTRY_PREFIX + "installPackages"
        position = REMOTE.remote_call(protocol, "scrollTo", {"semanticId": last_access})
        assert position["y"] > 0
        row = REMOTE.remote_call(protocol, "find", {"semanticId": last_access})["node"]
        assert row["visible"] is True
        wait_for_physical_button_bounds(row["text"])
        REMOTE.remote_call(protocol, "scrollBy", {"deltaY": -position["y"]})
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / ("device-access-" + protocol + ".png"))
        REMOTE.remote_call(protocol, "tap", {"semanticId": CLOSE_ACCESS})
        wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)
        assert REMOTE.remote_call(protocol, "snapshot")["screen"] == "home"
        assert REMOTE.remote_call(protocol, "find", {"semanticId": CLOSE_ACCESS})["found"] is False
        assert device_command(ROLE_COMMAND).strip() == role
        assert REMOTE.remote_call("http", "configGet") == configuration
    finally:
        close_setup()


def test_device_access_screen_battery_dialog_cancel_grant_and_return(websocket_control):
    was_granted = access_entry(BATTERY_ACCESS)["state"] == "granted"
    try:
        device_command("dumpsys deviceidle whitelist -" + DIKCIZ_DEBUG_PACKAGE)
        open_setup(websocket_control)
        task = setup_task()
        before = REMOTE.remote_call("http", "automationStatus")["deviceAccess"]["grantedCount"]
        for grant in (False, True):
            REMOTE.remote_call("mcp", "tap", {"semanticId": ENTRY_PREFIX + BATTERY_ACCESS})
            wait_for_physical_text_bounds("Allow")
            if grant:
                direct_tap(wait_for_physical_button_bounds("Allow"))
            else:
                device_command("input keyevent KEYCODE_BACK")
            snapshot = wait_setup()
            assert setup_task() == task
            assert access_entry(BATTERY_ACCESS)["state"] == ("granted" if grant else "needsSetup")
            after = REMOTE.remote_call("http", "automationStatus")["deviceAccess"]["grantedCount"]
            assert after >= before + int(grant)
            progress = AUTOMATION.require_node(snapshot, PROGRESS)["text"]
            assert progress.startswith(str(after) + " of ")
            assert any(node["semanticId"] == NEXT_ACCESS for node in snapshot["nodes"])
    finally:
        device_command("dumpsys deviceidle whitelist " + ("+" if was_granted else "-") + DIKCIZ_DEBUG_PACKAGE)
        close_setup()


def test_device_access_screen_runtime_permission_refreshes_in_place(websocket_control):
    assert access_entry("contacts")["state"] == "needsSetup"
    try:
        open_setup(websocket_control)
        task = setup_task()
        REMOTE.remote_call("http", "tap", {"semanticId": ENTRY_PREFIX + "contacts"})
        wait_for_foreground_application("com.android.permissioncontroller")
        device_command("input keyevent KEYCODE_BACK")
        wait_setup()
        assert access_entry("contacts")["state"] == "needsSetup"
        REMOTE.remote_call("http", "tap", {"semanticId": ENTRY_PREFIX + "contacts"})
        direct_tap(wait_for_physical_button_bounds("Allow"))
        wait_setup()
        assert setup_task() == task
        assert access_entry("contacts")["state"] == "granted"
        assert REMOTE.remote_call("http", "find", {"semanticId": ENTRY_PREFIX + "contacts"})["found"] is False
    finally:
        close_setup()
        for permission in CONTACT_PERMISSIONS:
            device_command("pm revoke " + DIKCIZ_DEBUG_PACKAGE + " " + permission)
        restore_system_home()


def test_device_access_screen_survives_app_switch_recents_and_recreation(websocket_control):
    previous = device_command("settings get global always_finish_activities").strip()
    rotation = device_system_setting(DEVICE_SETTING_USER_ROTATION)
    accelerometer = device_system_setting(DEVICE_SETTING_ACCELEROMETER_ROTATION)
    try:
        open_setup(websocket_control)
        task = setup_task()
        device_command("input keyevent KEYCODE_HOME")
        wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)
        assert recent_task(SETUP_COMPONENT, task) == task
        device_command("input keyevent KEYCODE_APP_SWITCH")
        run_device_operation("uiautomator-dump")
        shutil.copyfile(ARTIFACT_DIRECTORY / "window.xml", ARTIFACT_DIRECTORY / "device-access-recents.xml")
        cards = [node for node in physical_nodes()
                 if node.get("resource-id", "").endswith(":id/snapshot")
                 and node.get("content-desc", "").endswith(SETUP_TITLE)]
        assert len(cards) == 1, [node.attrib for node in physical_nodes()]
        direct_tap(wait_for_physical_description_bounds(cards[0].get("content-desc")))
        wait_setup()
        assert setup_task() == task
        set_device_system_setting(DEVICE_SETTING_ACCELEROMETER_ROTATION, "0")
        set_device_system_setting(DEVICE_SETTING_USER_ROTATION, "1")
        wait_setup()
        assert setup_task() == task
        landscape = REMOTE.remote_call("http", "screenshot")
        assert landscape["width"] > landscape["height"]
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "device-access-landscape.png")
        set_device_system_setting(DEVICE_SETTING_USER_ROTATION, "0")
        wait_setup()
        device_command("settings put global always_finish_activities 1")
        REMOTE.remote_call("http", "tap", {"semanticId": ENTRY_PREFIX + "usageAccess"})
        wait_for_foreground_application("com.android.settings")
        device_command("settings put global always_finish_activities " + shlex.quote(previous))
        device_command("input keyevent KEYCODE_BACK")
        wait_for_physical_text_bounds(SETUP_TITLE)
        assert wait_setup()["screen"] == SETUP_SCREEN
        assert setup_task() == task
        assert access_entry("usageAccess")["state"] == "needsSetup"
        configuration = REMOTE.remote_call("http", "configGet")
        REMOTE.remote_call("http", "tap", {"semanticId": ENTRY_PREFIX + "usageAccess"})
        wait_for_foreground_application("com.android.settings")
        process = device_command("pidof " + DIKCIZ_DEBUG_PACKAGE).strip()
        assert process.isdigit(), process
        device_command("run-as " + DIKCIZ_DEBUG_PACKAGE + " kill -9 " + process)
        device_command("input keyevent KEYCODE_BACK")
        wait_setup()
        assert setup_task() == task
        assert device_command("pidof " + DIKCIZ_DEBUG_PACKAGE).strip() != process
        assert REMOTE.remote_call("http", "configGet") == configuration
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "device-access-restored.png")
    finally:
        restore_device_system_setting(DEVICE_SETTING_USER_ROTATION, rotation)
        restore_device_system_setting(DEVICE_SETTING_ACCELEROMETER_ROTATION, accelerometer)
        device_command("settings put global always_finish_activities " + shlex.quote(previous))
        close_setup()
