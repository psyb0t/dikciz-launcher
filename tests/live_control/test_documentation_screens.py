"""Explicit private-emulator documentation scenes."""

import base64
import os
import shutil
import time
import pytest
from . import *
from . import test_user_guide_capture as guide
from .test_operations_ux import settings, tap
from .test_custom_apps import app_factory, open_app_manager
from .test_remote_tokens import token_owner, create, bearer
from .test_native_data import native_call, grant_permissions
from .test_chat_background import chat_notification_access
from . import test_chat_presentation as presentation
from . import test_chat_agent as agent
from .test_chat_agent import provider_endpoint, conversation, chat, cleanup
from .ui import direct_tap, wait_for_physical_text_bounds, wait_for_physical_description_bounds, physical_nodes, node_bounds

pytestmark = pytest.mark.skipif(os.environ.get("DIKCIZ_GUIDE_CAPTURE") != "true", reason="Explicit documentation capture")


def capture(name):
    if os.environ.get("DIKCIZ_GUIDE_CAPTURE") != "true":
        return
    time.sleep(.5)
    run_device_operation("screenshot")
    directory = ARTIFACT_DIRECTORY / "screen-guide"
    directory.mkdir(exist_ok=True)
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", directory / (name + ".png"))


def text(label):
    direct_tap(wait_for_physical_text_bounds(label))


def description(label):
    direct_tap(wait_for_physical_description_bounds(label))


def back():
    device_command("input keyevent KEYCODE_BACK")



def reveal_form_field(label, minimum_height=150, match_text=False):
    for _ in range(12):
        nodes = physical_nodes()
        scroller = next(node for node in nodes if node.get("class") == "android.widget.ScrollView")
        viewport = node_bounds(scroller)
        assert viewport is not None
        for node in nodes:
            if node.get("content-desc") != label and not (match_text and node.get("text", "").startswith(label)):
                continue
            bounds = node_bounds(node)
            if bounds and bounds["bottom"] - bounds["top"] >= minimum_height and bounds["top"] >= viewport["top"] and bounds["bottom"] <= viewport["bottom"]:
                return
        x = viewport["right"] - 8
        start = viewport["bottom"] - 40
        end = start - min((viewport["bottom"] - viewport["top"]) // 3, 500)
        device_command(f"input swipe {x} {start} {x} {end} 400")
        time.sleep(.3)
    raise AssertionError("Form field did not become physically visible: " + label)

def test_documentation_screens_companion_forms(websocket_control):
    settings(websocket_control)
    tap(websocket_control, "settings:companion")
    tap(websocket_control, "companion:tab:places")
    tap(websocket_control, "companion:fence:new")
    capture("place-editor")
    tap(websocket_control, "companion:back")
    tap(websocket_control, "companion:tab:devices")
    tap(websocket_control, "companion:connection:new")
    for kind in ("rfcomm", "gatt", "usb", "nfc"):
        tap(websocket_control, "companion:choice:kind")
        text(kind)
        capture("connection-" + kind)
    tap(websocket_control, "companion:back")
    tap(websocket_control, "companion:tab:wi-fi")
    tap(websocket_control, "companion:wifi:new")
    capture("wifi-editor")
    tap(websocket_control, "companion:choice:security")
    text("wpa2")
    capture("wifi-password")
    tap(websocket_control, "companion:close")


def test_documentation_screens_chat_dialogs(websocket_control, provider_endpoint):
    profile = chat("profileSave", profile={"name": "Example compatible API", "protocol": "openai", "baseUrl": provider_endpoint[0], "apiKey": "EXAMPLE-DO-NOT-USE", "model": "fixture-model"})
    identifier = chat("create", profileId=profile["id"], title="Weekend plans")["id"]
    try:
        chat("rename", conversationId=identifier, title="Weekend plans")
        chat("show", conversationId=identifier)
        description("Choose conversation")
        capture("conversations")
        text("Manage")
        capture("conversation-actions")
        text("Rename")
        capture("conversation-rename")
        text("Cancel")
        for action, name in (("Change provider", "conversation-provider"), ("Delete", "conversation-delete"), ("Run a routine", "routines")):
            description("Choose conversation")
            text("Manage")
            text(action)
            capture(name)
            if action == "Run a routine":
                node = next(node for node in physical_nodes() if node.get("text", "").startswith("Morning briefing"))
                direct_tap(node_bounds(node))
                capture("routine-confirmation")
            back()
        text("Make me a widget")
        capture("widget-request")
        text("Cancel")
        chat("subscribe", conversationId=identifier, rules=[{"id":"example-report", "subscription":{"event":"example.check", "minimumIntervalMilliseconds":60000}, "instructions":"Read the event and summarize its fields."}])
        description("Choose conversation")
        text("Events")
        capture("event-rules")
        text("Cancel")
        description("New conversation")
        capture("new-conversation")
        back()
        text("Tune")
        description("Context policy: drop-oldest or summarize")
        capture("chat-compaction")
        back()
        node = next(node for node in physical_nodes() if node.get("content-desc") == "Chat model settings form")
        bounds = node_bounds(node)
        assert bounds is not None
        middle = (bounds["left"] + bounds["right"]) // 2
        device_command(f"input swipe {middle} {bounds['bottom'] - 60} {middle} {bounds['top'] + 60} 500")
        capture("chat-model-lower")
        back()
        description("Chat provider settings")
        capture("provider-list")
        text("Add provider")
        capture("provider-openai")
        description("Provider template")
        capture("provider-templates")
        text("Anthropic")
        capture("provider-anthropic")
        description("Provider template")
        text("Custom")
        capture("provider-custom")
        back()
    finally:
        cleanup(identifier)


def test_documentation_screens_backup_preview(websocket_control):
    settings(websocket_control)
    tap(websocket_control, "settings:backups")
    capture("backup-form")
    AUTOMATION.request(websocket_control, "setText", semanticId="backups:passphrase", text="EXAMPLE-DO-NOT-USE")
    tap(websocket_control, "backups:create")
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        snapshot = AUTOMATION.request(websocket_control, "snapshot")
        status = AUTOMATION.require_node(snapshot, "backups:status").get("text", "")
        if status.startswith("Saved encrypted backup:"):
            break
        time.sleep(.2)
    assert status.startswith("Saved encrypted backup:"), status
    capture("backup-saved")
    archive = status.split("\n", 1)[1]
    AUTOMATION.request(websocket_control, "setText", semanticId="backups:path", text=archive)
    AUTOMATION.request(websocket_control, "setText", semanticId="backups:passphrase", text="EXAMPLE-DO-NOT-USE")
    tap(websocket_control, "backups:preview")
    wait_for_snapshot_node(websocket_control, "backups:confirm")
    capture("backup-preview")
    back()


def test_documentation_screens_app_management(websocket_control, app_factory):
    identifier, _ = app_factory()
    open_app_manager(websocket_control)
    tap(websocket_control, "custom-app:" + identifier + ":more")
    tap(websocket_control, "custom-app:" + identifier + ":remove")
    wait_for_snapshot_node(websocket_control, "custom-app:confirm")
    capture("app-remove")
    text("Cancel")
    tap(websocket_control, "custom-app:import")
    capture("app-import")
    text("Cancel")
    tap(websocket_control, "custom-app:new")
    capture("app-create")
    tap(websocket_control, "custom-app:editor:file")
    capture("app-file-picker")
    back()
    tap(websocket_control, "custom-app:editor:cancel")
    tap(websocket_control, "custom-app:close")


def test_documentation_screens_reset_confirmation(websocket_control):
    settings(websocket_control)
    tap(websocket_control, "settings:reset")
    wait_for_snapshot_node(websocket_control, "settings:reset-confirm")
    capture("reset-confirmation")
    back()


def capture_android_approval(websocket_control):
    restore_system_home()
    native_call("http", "healthPermissionsRequest", {"kinds": ["steps"], "access": "read"})
    wait_for_physical_text_bounds("Review in Health Connect")
    capture("health-review")
    text("Close")
    restore_system_home()
    grant_permissions(("android.permission.POST_NOTIFICATIONS", "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION", "android.permission.NEARBY_WIFI_DEVICES"))
    time.sleep(2)
    result = native_call("http", "wifiNetworksConfigure", {"action": "save", "ssid": "Example network", "security": "open"})
    assert result["state"] == "user_action_required", result
    time.sleep(1)
    if not any(node.get("text") == "Review in Android" for node in physical_nodes()):
        device_command("cmd statusbar expand-notifications")
        text("Review Wi-Fi network")
    wait_for_physical_text_bounds("Review in Android")
    capture("wifi-approval")
    text("Cancel")


def test_documentation_screens_sos_countdown(websocket_control):
    restore_system_home()
    grant_permissions(("android.permission.POST_NOTIFICATIONS", "android.permission.SEND_SMS", "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"))
    device_command("appops set " + DIKCIZ_DEBUG_PACKAGE + " SYSTEM_ALERT_WINDOW allow")
    native_call("http", "sosPlanSave", {"contacts": [{"address": "5550101"}], "graceSeconds": 300, "maximumUpdates": 1})
    try:
        native_call("http", "sosControl", {"action": "trigger", "confirm": True})
        wait_for_physical_text_bounds("SOS MESSAGE PENDING")
        capture("sos-countdown")
    finally:
        native_call("http", "sosControl", {"action": "cancel"})
    state = native_call("http", "sosStatus")
    assert not state["run"]["receipts"]


def capture_widget_details(websocket_control):
    guide.reset_to_starter_home()
    guide.open_widget_action_sheet()
    tap(websocket_control, "widget:time-weather:edit")
    for field, label, height in (("css", "CSS", 300), ("javascript", "JavaScript", 300), ("state", "State JSON", 150), ("save-to-library", "Save to library", 40)):
        reveal_form_field(label, height)
        capture("widget-editor-" + field)
    back()
    AUTOMATION.request(websocket_control, "longPress", semanticId="widget:time-weather:move-handle")
    tap(websocket_control, "widget:time-weather:appearance")
    tap(websocket_control, "appearance:widget:time-weather:background-color-picker")
    capture("color-picker")
    back()
    tap(websocket_control, "appearance:widget:time-weather:font-picker")
    capture("font-picker")
    back()
    back()
    guide.reset_to_starter_home()
    guide.open_launcher_control_route(websocket_control, guide.LAUNCHER_CONTROL_MANAGE_PAGE_SEMANTIC_ID)
    text("Change widget locks")
    capture("widget-locks")
    back()
    guide.reset_to_starter_home()
    guide.open_notes_page_with_free_cells(websocket_control)
    guide.open_launcher_control_route(websocket_control, guide.LAUNCHER_CONTROL_ADD_TO_PAGE_SEMANTIC_ID)
    text("Dikciz")
    snapshot = AUTOMATION.request(websocket_control, "snapshot")
    node = next(node for node in snapshot["nodes"] if node.get("text", "").startswith("Quick note"))
    tap(websocket_control, node["semanticId"])
    wait_for_physical_text_bounds("Save note")
    capture("template-preview")
    back()
    guide.reset_to_starter_home()
    guide.open_widget_action_sheet()
    direct_tap(guide.scroll_dialog_to_text(guide.WIDGET_DELETE_LABEL))
    time.sleep(5)
    guide.capture("15-widget-delete.png")
    guide.reset_to_starter_home()



def capture_access_and_logs(websocket_control):
    from .test_device_access_screen import open_setup, close_setup
    restore_system_home()
    open_setup(websocket_control)
    snapshot = AUTOMATION.request(websocket_control, "snapshot")
    for name, requirement in (("device-access-middle", "notificationListener"), ("device-access-bottom", "phoneNumbers")):
        semantic = "settings:device-access:entry:" + requirement
        AUTOMATION.require_node(snapshot, semantic)
        position = AUTOMATION.request(websocket_control, "scrollTo", semanticId=semantic)
        assert position["y"] > 0
        capture(name)
    close_setup()
    settings(websocket_control)
    tap(websocket_control, "settings:script-logs")
    capture("log-viewer")
    back()


def capture_operation_details(websocket_control):
    settings(websocket_control)
    tap(websocket_control, "settings:operations")
    deadline = time.monotonic() + 30
    loaded = False
    while time.monotonic() < deadline:
        snapshot = AUTOMATION.request(websocket_control, "snapshot")
        loaded = any("records" in node.get("text", "") and "retained" in node.get("text", "") for node in snapshot["nodes"])
        if loaded:
            break
        time.sleep(.2)
    assert loaded, snapshot
    capture("activity")
    tap(websocket_control, "operations:filter")
    capture("activity-filter")
    back()
    tap(websocket_control, "operations:close")
    snapshot = AUTOMATION.request(websocket_control, "snapshot")
    if not any(node["semanticId"] == "settings:automation-rule" for node in snapshot["nodes"]):
        settings(websocket_control)
    tap(websocket_control, "settings:automation-rule")
    text("Create a rule")
    wait_for_snapshot_node(websocket_control, "automation-rule:save")
    AUTOMATION.request(websocket_control, "setText", semanticId="automation-rule:title", text="Battery check")
    reveal_form_field("Test sample", 40)
    text("Test sample")
    reveal_form_field("Condition matched.", 10, True)
    capture("rule-editor-test")
    back()


def test_documentation_screens_chat_on_home(websocket_control, provider_endpoint, chat_notification_access):
    guide.reset_to_starter_home()
    document = guide.current_configuration(websocket_control)
    widget = next(widget for page in document["launcher"]["home"]["pages"] for widget in page["widgets"] if widget["id"] == "time-weather")
    widget["state"] = {"city":{"name":"London", "latitude":51.5074, "longitude":-.1278, "source":"manual"}}
    AUTOMATION.request(websocket_control, "configReplace", config=document)
    time.sleep(20)
    presentation.test_chat_presentation_sheet_keyboard_and_notification(websocket_control, provider_endpoint, chat_notification_access)


def test_documentation_screens_chat_tool_result(websocket_control, provider_endpoint):
    _, identifier = conversation(provider_endpoint[0], readOnly=True)
    provider_endpoint[1].update(text="Android returned the battery capacity. Open the tool result to inspect the response.", tools=[("dikciz_android_api_read", {"className":"android.os.BatteryManager", "member":"getIntProperty(int)", "target":"service:batterymanager", "arguments":[4]})])
    try:
        chat("rename", conversationId=identifier, title="Phone status")
        agent.send(identifier, "Read the battery capacity without changing anything.")
        rows = agent.wait_turn(identifier)
        assert any(row["role"] == "tool" for row in rows)
        chat("show", conversationId=identifier, fullScreen=True)
        time.sleep(1)
        capture("chat-phone-tool")
        node = next(node for node in physical_nodes() if node.get("text", "").startswith("Result"))
        direct_tap(node_bounds(node))
        capture("chat-tool-result")
    finally:
        cleanup(identifier)


def test_documentation_screens_recorded_route(websocket_control):
    grant_permissions(("android.permission.POST_NOTIFICATIONS", "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"))
    restore_system_home()
    run_device_operation("emulator-geo-fix", EMULATOR_GEO_LATITUDE="51.5074", EMULATOR_GEO_LONGITUDE="-0.1278")
    route = native_call("http", "journeyControl", {"action":"start", "title":"Example walk", "sampleIntervalMilliseconds":1000})
    identifier = route["id"]
    fence = None
    try:
        for latitude, longitude in (("51.5074", "-0.1278"), ("51.5079", "-0.1272"), ("51.5084", "-0.1266")):
            run_device_operation("emulator-geo-fix", EMULATOR_GEO_LATITUDE=latitude, EMULATOR_GEO_LONGITUDE=longitude)
            time.sleep(2)
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            observed = native_call("http", "journeyRead", {"id":identifier, "limit":20})
            if observed["total"] >= 2 and observed["journey"]["distanceMeters"] > 0:
                break
            time.sleep(.5)
        assert observed["total"] >= 2 and observed["journey"]["distanceMeters"] > 0, observed
        native_call("http", "journeyControl", {"action":"stop", "id":identifier})
        settings(websocket_control)
        tap(websocket_control, "settings:companion")
        tap(websocket_control, "companion:tab:trips")
        wait_for_snapshot_node(websocket_control, "companion:trip:read:" + identifier)
        capture("companion-trips")
        tap(websocket_control, "companion:trip:read:" + identifier)
        capture("route-preview")
        tap(websocket_control, "companion:back")
        fence = native_call("http", "geofenceSave", {"title":"Example meeting point", "latitude":51.5074, "longitude":-.1278, "radiusMeters":250, "enabled":False})["id"]
        tap(websocket_control, "companion:tab:places")
        wait_for_snapshot_node(websocket_control, "companion:fence:" + fence)
        capture("companion-places")
        tap(websocket_control, "companion:fence:" + fence)
        capture("place-editor-saved")
        tap(websocket_control, "companion:close")
    finally:
        native_call("http", "journeyControl", {"action":"stop", "id":identifier})
        native_call("http", "journeyDelete", {"id":identifier, "confirm":True})
        if fence:
            native_call("http", "geofenceDelete", {"id":fence})


def test_documentation_screens_script_archive(websocket_control):
    settings(websocket_control)
    tap(websocket_control, "settings:scripts")
    tap(websocket_control, "script:create:javascript")
    AUTOMATION.request(websocket_control, "setText", semanticId="lua-script:title", text="Example event handler")
    capture("javascript-create")
    tap(websocket_control, "lua-script:save")
    configured = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
    identifier = next(item["id"] for item in configured["scripts"] if item["title"] == "Example event handler")
    tap(websocket_control, "lua-script:" + identifier)
    tap(websocket_control, "lua-script:delete")
    wait_for_snapshot_node(websocket_control, "lua-script:delete-confirm")
    capture("script-delete")
    text("Cancel")
    tap(websocket_control, "lua-script:export")
    exports = device_command("ls /sdcard/Dikciz/scripts/exports").splitlines()
    name = next(item for item in exports if item.startswith(identifier + "-"))
    device_command("mkdir -p /sdcard/Dikciz/scripts/imports")
    device_command("cp /sdcard/Dikciz/scripts/exports/" + name + " /sdcard/Dikciz/scripts/imports/" + name)
    tap(websocket_control, "dialog:close")
    configured["scripts"] = [item for item in configured["scripts"] if item["id"] != identifier]
    AUTOMATION.request(websocket_control, "configReplace", config=configured)
    settings(websocket_control)
    tap(websocket_control, "settings:scripts")
    tap(websocket_control, "lua-script:import")
    wait_for_snapshot_node(websocket_control, "lua-script:import-file:" + name)
    capture("script-import-picker")
    tap(websocket_control, "lua-script:import-file:" + name)
    wait_for_snapshot_node(websocket_control, "lua-script:import-confirm")
    capture("script-import-confirm")
    tap(websocket_control, "lua-script:import-confirm")
    current = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
    assert any(item["id"] == identifier for item in current["scripts"])
    guide.reset_to_starter_home()


def test_documentation_screens_voice_saved(websocket_control):
    from .test_custom_apps import test_custom_apps_voice_note_stops_when_ui_closes
    test_custom_apps_voice_note_stops_when_ui_closes(websocket_control)


def test_documentation_screens_startup():
    from .test_launcher_regressions import test_launcher_regression_splash_displays_running_version_at_bottom
    test_launcher_regression_splash_displays_running_version_at_bottom()


def capture_scan(websocket_control):
    path = "/sdcard/Download/dikciz-document.png"
    image = (LAB_ROOT / "tests/fixtures/companion-vision-text.png").read_bytes()
    native_call("http", "fileWrite", {"path":path, "dataBase64":base64.b64encode(image).decode()})
    try:
        settings(websocket_control)
        tap(websocket_control, "settings:companion")
        tap(websocket_control, "companion:tab:scan")
        tap(websocket_control, "companion:scan:pick")
        description("Show roots")
        text("Downloads")
        node = next(node for node in physical_nodes() if node.get("text", "").startswith("dikciz-document.png") or node.get("content-desc", "").startswith("dikciz-document.png"))
        direct_tap(node_bounds(node))
        wait_for_snapshot_node(websocket_control, "companion:scan:read")
        capture("scan-selected")
        tap(websocket_control, "companion:scan:read")
        wait_for_physical_text_bounds("Recognition result")
        capture("scan-result")
        tap(websocket_control, "companion:scan:pdf")
        wait_for_snapshot_node(websocket_control, "companion:scan:copy")
        AUTOMATION.request(websocket_control, "scrollTo", semanticId="companion:scan:copy")
        capture("scan-pdf")
        tap(websocket_control, "companion:close")
    finally:
        native_call("http", "fileManage", {"action":"delete", "path":path})


def capture_storage():
    from .test_storage_access_controls import test_storage_access_handoff_is_semantic_and_configuration_is_still_gated
    test_storage_access_handoff_is_semantic_and_configuration_is_still_gated()

def test_documentation_screens_token_rotation(token_owner):
    _, identifier = create(token_owner, ["sensors.read"], name="Sensor dashboard")
    with bearer.connected_bearer_websocket(token_owner) as connection:
        capture("remote-authentication")
        AUTOMATION.request(connection, "tap", semanticId="remote-auth:tokens")
        wait_for_snapshot_node(connection, "remote-tokens:create")
        tap(connection, "remote-tokens:" + identifier + ":more")
        capture("access-token-manage")
        tap(connection, "remote-tokens:" + identifier + ":edit")
        capture("access-token-edit")
        text("Cancel")
        tap(connection, "remote-tokens:" + identifier + ":more")
        tap(connection, "remote-tokens:" + identifier + ":rotate")
        capture("access-token-rotate")
        text("Cancel")




def capture_scene(websocket_control, scene):
    if scene == "permission_background":
        from .test_permission_requests import background_permission_review
        background_permission_review(websocket_control, "systemSettings", "system settings")
        return
    if scene == "storage":
        capture_storage()
        return
    scenes = {
        "android_approval": capture_android_approval,
        "operation_details": capture_operation_details,
        "scan": capture_scan,
        "widget_details": capture_widget_details,
        "access_and_logs": capture_access_and_logs,
    }
    scenes[scene](websocket_control)


@pytest.mark.parametrize("scene", ("android_approval", "scan", "widget_details", "access_and_logs", "storage"))
def test_documentation_screens_forms(websocket_control, scene):
    capture_scene(websocket_control, scene)


@pytest.mark.parametrize("scene", ("operation_details", "permission_background"))
def test_documentation_screens_review(websocket_control, scene):
    capture_scene(websocket_control, scene)
