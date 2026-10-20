"""Owner permission handoff through the production remote controls."""

import http.client
import shlex
import shutil

from . import *
from . import test_remote_device_control as REMOTE
from . import test_remote_bearer_auth as AUTH
from .test_shell_streaming import ShellStream, rejected_command


REQUEST = "requestDeviceAccess"
REMOTE.TOOLS[REQUEST] = "dikciz_request_device_access"
REMOTE.TOOLS["automationDispatch"] = "dikciz_automation_dispatch"
REMOTE.TOOLS["automationStatus"] = "dikciz_automation_status"
REMOTE.TOOLS["accessibilitySnapshot"] = "dikciz_accessibility_snapshot"
PROTOCOLS = ("websocket", "mcp", "http")
SCREENSHOT_ARTIFACT_NAME = "screenshot.png"
PERMISSION_COOLDOWN_SECONDS = 31


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_permission_request_automatic_accessibility_handoff(websocket_control, protocol):
    time.sleep(PERMISSION_COOLDOWN_SECONDS)
    previous = device_command("settings get secure enabled_accessibility_services").strip()
    try:
        device_command("settings put secure enabled_accessibility_services null")
        detail = REMOTE.remote_call(protocol, "accessibilitySnapshot", {"autoRequestPermissions": True}, error="accessibility_not_enabled")
        recovery = detail["permissionRequest"]
        assert recovery["requirementId"] == "accessibility"
        assert recovery["state"] == "user_action_required"
        assert recovery["retryRequired"] is True
        assert isinstance(recovery["notificationPosted"], bool)
        assert "phone" in recovery["instructions"].lower()
    finally:
        device_command("settings put secure enabled_accessibility_services " + shlex.quote(previous))
        restore_system_home()


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_permission_request_validation_and_no_prompt_for_granted_access(websocket_control, protocol):
    for arguments in ({}, {"requirementId": None}, {"requirementId": ""},
                      {"requirementId": 3}, {"requirementId": "invalid"},
                      {"requirementId": "not-a-permission-☃"},
                      {"requirementId": "x" * 257},
                      {"requirementId": "contacts", "message": "untrusted"}):
        REMOTE.remote_call(protocol, REQUEST, arguments, error="validation_failed")
    granted = REMOTE.remote_call(protocol, REQUEST, {"requirementId": "sharedStorage"})
    assert granted["state"] == "granted"
    assert granted["notificationPosted"] is False
    root = REMOTE.remote_call(protocol, REQUEST, {"requirementId": "rootShell"})
    assert root["state"] == "onDemand"
    assert root["notificationPosted"] is False


def test_permission_request_owner_click_opens_only_requested_android_access(websocket_control):
    snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
    if any(node.get("semanticId") == "permission:dismiss" for node in snapshot["nodes"]):
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId="permission:dismiss")
    device_command("pm revoke " + DIKCIZ_DEBUG_PACKAGE + " android.permission.READ_CONTACTS")
    device_command("pm revoke " + DIKCIZ_DEBUG_PACKAGE + " android.permission.WRITE_CONTACTS")
    result = REMOTE.remote_call("http", REQUEST, {"requirementId": "contacts"})
    assert result["state"] == "user_action_required"
    snapshot = wait_for_snapshot_node(websocket_control, "permission:allow")
    assert "contacts" in str(snapshot).lower()
    assert "Allow access" in str(snapshot)
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / SCREENSHOT_ARTIFACT_NAME, ARTIFACT_DIRECTORY / "permission-contacts-review.png")
    status = REMOTE.remote_call("http", "automationStatus")
    assert status["androidAccess"]["contactsRead"] is False
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId="permission:allow")
    wait_for_foreground_application("com.android.permissioncontroller")
    wait_for_physical_text_bounds("Allow")
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / SCREENSHOT_ARTIFACT_NAME, ARTIFACT_DIRECTORY / "permission-contacts-android.png")
    device_command("input keyevent KEYCODE_BACK")
    restore_system_home()


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_permission_request_untrusted_origin_cannot_prompt(websocket_control, protocol):
    if protocol == "websocket":
        line, _ = AUTH.websocket_upgrade_response(origin="https://evil.example")
        assert "403" in line
    else:
        connection = http.client.HTTPConnection(AUTOMATION_HOST, MCP_PORT, timeout=10)
        try:
            path = "/mcp" if protocol == "mcp" else "/v1/automation"
            connection.request("POST", path,
                               body=json.dumps({"type": REQUEST, "requestId": "invalid", "requirementId": "contacts"}),
                               headers={"Origin": "https://evil.example", "Content-Type": "application/json"})
            response = connection.getresponse()
            response.read()
            assert response.status == 403
        finally:
            connection.close()


def test_permission_request_disabled_notifications_and_duplicate_fallback(websocket_control):
    previous = device_command("settings get secure enabled_accessibility_services").strip()
    try:
        device_command("settings put secure enabled_accessibility_services null")
        device_command("appops set " + DIKCIZ_DEBUG_PACKAGE + " POST_NOTIFICATION deny")
        result = REMOTE.remote_call("http", REQUEST, {"requirementId": "accessibility"})
        assert result["state"] == "user_action_required"
        assert result["notificationPosted"] is False
        assert "open Dikciz" in result["instructions"]
        duplicate = REMOTE.remote_call("mcp", REQUEST, {"requirementId": "accessibility"})
        assert duplicate["deduplicated"] is True
        wait_for_snapshot_node(websocket_control, "permission:allow")
    finally:
        device_command("appops set " + DIKCIZ_DEBUG_PACKAGE + " POST_NOTIFICATION allow")
        device_command("settings put secure enabled_accessibility_services " + shlex.quote(previous))
        restore_system_home()


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_permission_request_opt_in_and_boolean_validation(websocket_control, protocol):
    previous = device_command("settings get secure enabled_accessibility_services").strip()
    try:
        device_command("settings put secure enabled_accessibility_services null")
        snapshot = REMOTE.remote_call(protocol, "snapshot")
        if any(node.get("semanticId") == "permission:dismiss" for node in snapshot["nodes"]):
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId="permission:dismiss")
        for arguments in ({}, {"autoRequestPermissions": False}):
            result = REMOTE.remote_call(protocol, "accessibilitySnapshot", arguments,
                                        error="accessibility_not_enabled")["permissionRequest"]
            assert result["state"] == "permission_required"
            assert result["autoRequested"] is False
            assert result["notificationPosted"] is False
            snapshot = REMOTE.remote_call(protocol, "snapshot")
            assert not any(node.get("semanticId") == "permission:allow" for node in snapshot["nodes"])
        for value in (None, "true", 1, [], {}):
            REMOTE.remote_call(protocol, "snapshot", {"autoRequestPermissions": value}, error="validation_failed")
        assert REMOTE.remote_call(protocol, "snapshot", {"autoRequestPermissions": True})["screen"] == "home"
    finally:
        device_command("settings put secure enabled_accessibility_services " + shlex.quote(previous))
        restore_system_home()



def test_permission_request_dismissed_review_reports_cooldown(websocket_control):
    result = REMOTE.remote_call("http", REQUEST, {"requirementId": "overlay"})
    assert result["state"] == "user_action_required"
    wait_for_snapshot_node(websocket_control, "permission:allow")
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId="permission:dismiss")
    result = REMOTE.remote_call("http", REQUEST, {"requirementId": "overlay"})
    assert result["state"] == "request_dismissed"
    assert result["promptState"] == "dismissed"
    assert result["notificationPosted"] is False
    snapshot = REMOTE.remote_call("http", "snapshot")
    assert not any(node.get("semanticId") == "permission:allow" for node in snapshot["nodes"])


def test_permission_request_background_notification_button_opens_correct_target(websocket_control):
    was_granted = REMOTE.remote_call("http", "automationStatus")["androidAccess"]["notificationPosting"]
    device_command("pm grant " + DIKCIZ_DEBUG_PACKAGE + " android.permission.POST_NOTIFICATIONS")
    device_command("appops set " + DIKCIZ_DEBUG_PACKAGE + " POST_NOTIFICATION allow")
    try:
        device_command("am start -a android.settings.SETTINGS")
        wait_for_foreground_application("com.android.settings")
        result = REMOTE.remote_call("mcp", REQUEST, {"requirementId": "locationForeground"})
        assert result["state"] == "user_action_required"
        assert result["notificationPosted"] is True
        assert result["promptState"] == "notification_posted"
        deadline = time.monotonic() + PROVIDER_RENDER_TIMEOUT_SECONDS
        while time.monotonic() < deadline:
            notifications = device_command("dumpsys notification --noredact")
            if "channel=dikciz_permission_requests" in notifications:
                break
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
        else:
            raise AssertionError("Android did not retain the permission notification")
        (ARTIFACT_DIRECTORY / "permission-notification-dump.txt").write_text(notifications)
        device_command("cmd statusbar expand-notifications")
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / SCREENSHOT_ARTIFACT_NAME, ARTIFACT_DIRECTORY / "permission-notification-shade.png")
        bounds = wait_for_physical_text_bounds("Allow access")
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / SCREENSHOT_ARTIFACT_NAME, ARTIFACT_DIRECTORY / "permission-notification.png")
        direct_tap(bounds)
        wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)
        snapshot = wait_for_snapshot_node(websocket_control, "permission:allow")
        message = AUTOMATION.require_node(snapshot, "permission:message")
        assert "location" in message["text"].lower()
        run_device_operation("screenshot")
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId="permission:dismiss")
    finally:
        device_command("cmd statusbar collapse")
        if not was_granted:
            device_command("pm revoke " + DIKCIZ_DEBUG_PACKAGE + " android.permission.POST_NOTIFICATIONS")
        restore_system_home()



def test_permission_request_command_discovery(websocket_control):
    AUTOMATION.verify_control_status(websocket_control)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_permission_request_option_preserves_shell_streams(websocket_control, protocol):
    for value in (None, "true", 1):
        error = rejected_command(protocol, "shellStream", {
            "command": "printf unused", "root": False, "autoRequestPermissions": value,
        })
        assert error["code"] == "validation_failed"
    stream = ShellStream(protocol, "printf permission-stream", autoRequestPermissions=True)
    try:
        complete, output = stream.complete()
        assert complete["state"] == "exited"
        assert complete["exitCode"] == 0
        assert output == b"permission-stream"
    finally:
        stream.close()



@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_permission_request_typed_action_keeps_denial_and_recovery(websocket_control, protocol):
    status = REMOTE.remote_call(protocol, "automationStatus")
    assert status["androidAccess"]["deviceAdminActive"] is False
    snapshot = REMOTE.remote_call(protocol, "snapshot")
    widget = next(item for item in snapshot["widgetReferences"] if item["type"] == "html")
    arguments = {"sourceWidgetAddress": widget["pageLocation"] + "-" + widget["id"],
                 "actions": [{"type": "lockDevice"}]}
    for auto_show in (False, True):
        result = REMOTE.remote_call(protocol, "automationDispatch", {
            **arguments, "autoRequestPermissions": auto_show,
        })
        action = result["actions"][0]
        assert action["outcome"] == "device_admin_inactive"
        recovery = action["permissionRequest"]
        assert recovery["requirementId"] == "deviceAdministration"
        assert recovery["autoRequested"] is auto_show
        if not auto_show:
            assert recovery["state"] == "permission_required"
    if recovery["state"] == "user_action_required":
        wait_for_snapshot_node(websocket_control, "permission:allow")
    else:
        assert recovery["state"] == "request_dismissed"
        assert recovery["promptState"] == "dismissed"
        assert recovery["notificationPosted"] is False
