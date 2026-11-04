"""Companion commands through real HTTP, WebSocket and MCP listeners."""

import base64
import json
import time
import uuid

import pytest

from . import *
from .test_native_data import native_call, grant_permissions, PROTOCOLS
from . import test_chat_agent as agent
from .test_chat_agent import provider_endpoint
from .test_remote_tokens import token_owner


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_companion_discovery_validation_and_inactive_safety(websocket_control, protocol):
    AUTOMATION.verify_control_status(websocket_control)
    native_call(protocol, "sosControl", {"action": "cancel"})
    plan = {"contacts": [{"address": "5550101", "label": "Emulator fixture"}],
            "checkInSeconds": 60, "graceSeconds": 5, "maximumUpdates": 1}
    saved = native_call(protocol, "sosPlanSave", plan)
    assert saved["armed"] is False
    preview = native_call(protocol, "sosPreview")
    assert preview["dryRun"] and not preview["smsSubmitted"]
    assert preview["contacts"] == plan["contacts"]
    routines = native_call(protocol, "assistantRoutines")["items"]
    assert len(routines) == 5 and all(row["readOnly"] for row in routines)
    assert native_call(protocol, "assistantRoutinePreview", {"routineId": "deviceAudit"})["providerCalled"] is False
    assert isinstance(native_call(protocol, "deviceConnectionsList")["items"], list)
    assert isinstance(native_call(protocol, "wifiConnectionsList")["items"], list)
    health = native_call(protocol, "healthRecordsCatalogue")
    assert len(health["items"]) == 16
    invalid = [
        ("sosPlanSave", {"contacts": []}),
        ("sosPlanSave", {"contacts": [{"address": "5550101", "surprise": True}]}),
        ("sosPlanSave", {"contacts": [{"address": "5550101"}], "checkInSeconds": 59}),
        ("sosControl", {"action": "trigger"}),
        ("geofenceSave", {"title": "x", "latitude": 91, "longitude": 0, "radiusMeters": 100}),
        ("geofenceSave", {"title": "x", "latitude": 0, "longitude": 0, "radiusMeters": 99}),
        ("documentScan", {"pages": []}),
        ("documentScan", {"pages": [{"path": "x", "unexpected": 1}]}),
        ("apkInstallSet", {"apks": []}),
        ("apkInstallSet", {"apks": [{"path": "x", "unknown": True}]}),
        ("wifiConnectionStart", {"ssid": "x", "security": "wpa2", "passphrase": "short"}),
        ("imageRecognize", {"path": "x", "rotationDegrees": 45}),
        ("assistantRoutinePreview", {"routineId": "invented"}),
    ]
    for command, arguments in invalid:
        native_call(protocol, command, arguments, error="validation_failed")
    native_call(protocol, "deviceConnectionRead", {"id": "missing"}, error="device_connection_not_found")
    native_call(protocol, "wifiConnectionStatus", {"id": "missing"}, error="wifi_request_not_found")


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_companion_geofence_pagination(websocket_control, protocol):
    prefix = "fixture-" + uuid.uuid4().hex[:8]
    ids = []
    try:
        for index in range(13):
            identifier = prefix + "-" + str(index)
            native_call(protocol, "geofenceSave", {"id": identifier, "title": "Fixture " + str(index),
                "latitude": 0, "longitude": 0, "radiusMeters": 100, "enabled": False})
            ids.append(identifier)
        found = []
        offset = 0
        while True:
            page = native_call(protocol, "geofencesList", {"offset": offset, "limit": 5})
            found.extend(row["id"] for row in page["items"] if row["id"].startswith(prefix))
            offset += len(page["items"])
            if not page["hasMore"]:
                break
        assert set(found) == set(ids) and len(found) == 13
        assert native_call(protocol, "geofencesList", {"offset": 99999})["items"] == []
    finally:
        for identifier in ids:
            native_call(protocol, "geofenceDelete", {"id": identifier})


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_companion_vision_real_models_and_document(websocket_control, protocol):
    root = "/sdcard/Dikciz/companion-vision-" + uuid.uuid4().hex
    paths = []
    outputs = []
    try:
        for kind in ("text", "qr"):
            path = root + "-" + kind + ".png"
            image = (LAB_ROOT / ("tests/fixtures/companion-vision-" + kind + ".png")).read_bytes()
            native_call(protocol, "fileWrite", {"path": path, "dataBase64": base64.b64encode(image).decode()})
            paths.append(path)
        text = native_call(protocol, "imageRecognize", {"path": paths[0], "mode": "text"})
        assert "428" in text["text"] and "FIXTURE" in text["text"].upper(), text
        assert text["imageUploaded"] is False
        qr = native_call(protocol, "imageRecognize", {"path": paths[1], "mode": "barcodes"})
        assert [code["rawValue"] for code in qr["barcodes"]] == ["DIKCIZ-FIXTURE-428"], qr
        assert not qr["barcodeLimitReached"]
        document = native_call(protocol, "documentScan", {"pages": [{"path": paths[0],
            "corners": [{"x": 0, "y": 0}, {"x": 1, "y": 0}, {"x": 1, "y": 1}, {"x": 0, "y": 1}]}]})
        assert not document["searchableTextLayer"] and not document["imageUploaded"]
        outputs = [document["path"]] + [row["path"] for row in document["pages"]]
        header = native_call(protocol, "fileRead", {"path": document["path"], "limit": 8})
        assert base64.b64decode(header["dataBase64"]).startswith(b"%PDF-")
        native_call(protocol, "imageRecognize", {"path": paths[0], "mode": "text", "maximumTextCharacters": 1},
                    error="vision_text_too_large")
        native_call(protocol, "documentScan", {"pages": [{"path": paths[0], "corners":
            [{"x": 0, "y": 0}] * 4}]}, error="validation_failed")

        if protocol == "http":
            original = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
            source = """function on_event(event)
  if event.type ~= "fixture.companion.bindings" then return end
  return {actions={{type="patchState",values={
    nativeReply=context.requestJson({type="assistantRoutines"})
  }}}}
end"""
            configured = automation_configuration(original,source,{},["manualTrigger"],["patchState"],
                [{"event":"fixture.companion.bindings","minimumIntervalMilliseconds":0}])
            widget = configuration_widget(configured,"welcome")
            widget["javascript"] = """window.addEventListener("dikciz-ready",async()=>{
  try {
    const health=await dikciz.command("healthRecordsCatalogue",{});
    const routines=await dikciz.command("assistantRoutines",{});
    await selfWidget.patchState({healthKinds:health.items.length,routineCount:routines.items.length});
  } catch(error) { await selfWidget.patchState({probeError:error.code||error.message}); }
});"""
            AUTOMATION.request(websocket_control,"configReplace",config=configured)
            time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
            native_call("http","automationDispatch",{"sourceWidgetAddress":"1H1V-welcome",
                "actions":[{"type":"emitEvent","event":"fixture.companion.bindings","payload":{}}]})
            deadline=time.monotonic()+20
            while time.monotonic()<deadline:
                current=AUTOMATION.require_configuration(AUTOMATION.request(websocket_control,"configGet"))
                state=configuration_widget(current,"welcome")["state"]
                lua=next(row for row in current["scripts"] if row["id"]==AUTOMATION_LIVE_SCRIPT_ID)["state"]
                if "nativeReply" in lua and state.get("healthKinds")==16:
                    break
                time.sleep(.25)
            assert state.get("healthKinds")==16 and state.get("routineCount")==5,state
            assert len(json.loads(lua["nativeReply"])["result"]["items"])==5,lua
    finally:
        for path in paths + outputs:
            native_call("http", "fileManage", {"action": "delete", "path": path})


def test_companion_followup_journey(websocket_control):
    grant_permissions(("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION",
                       "android.permission.POST_NOTIFICATIONS", "android.permission.SEND_SMS"))
    restore_system_home()
    native_call("http", "sosControl", {"action": "cancel"})
    native_call("http", "sosPlanSave", {"contacts": [{"address": "5550101"}], "graceSeconds": 5, "checkInSeconds": 60})
    run = native_call("http", "sosControl", {"action": "arm", "confirm": True})
    trip = None
    try:
        assert run["state"] == "armed"
        checked = native_call("mcp", "sosControl", {"action": "checkIn"})
        assert checked["id"] == run["id"] and checked["deadlineAt"] >= run["deadlineAt"]
        native_call("websocket", "sosControl", {"action": "cancel"})
        assert native_call("http", "sosStatus")["run"]["state"] == "cancelled"
        trip = native_call("http", "journeyControl", {"action": "start", "sampleIntervalMilliseconds": 1000})
        emulator_geo_fix(1.0, 1.0)
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            initial = native_call("http", "journeyRead", {"id": trip["id"], "limit": 200})
            if any(abs(point["latitude"] - 1.0) < .00001 for point in initial["items"]):
                break
            time.sleep(.5)
        assert any(abs(point["latitude"] - 1.0) < .00001 for point in initial["items"]), initial
        emulator_geo_fix(1.001, 1.001)
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            route = native_call("mcp", "journeyRead", {"id": trip["id"], "limit": 1})
            if route["total"] >= 2 and route["journey"]["distanceMeters"] > 0:
                break
            time.sleep(1)
        assert route["total"] >= 2 and route["hasMore"], route
        stopped = native_call("websocket", "journeyControl", {"action": "stop", "id": trip["id"]})
        assert stopped["state"] == "completed" and stopped["distanceMeters"] > 0
        native_call("http", "journeyDelete", {"id": trip["id"]}, error="validation_failed")
    finally:
        native_call("http", "sosControl", {"action": "cancel"})
        if trip:
            native_call("http", "journeyControl", {"action": "stop", "id": trip["id"]})
            native_call("http", "journeyDelete", {"id": trip["id"], "confirm": True})
        emulator_geo_fix(0, 0)


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
def test_companion_routines_forced_read_only(websocket_control, provider_endpoint, provider):
    endpoint, script, records = provider_endpoint
    _, identifier = agent.conversation(endpoint, provider)
    writes = [
        ("dikciz_sos_control", {"action": "trigger", "confirm": True}),
        ("dikciz_geofence_save", {}),
        ("dikciz_device_connection_open", {}),
        ("dikciz_device_connection_exchange", {}),
        ("dikciz_document_scan", {}),
        ("dikciz_health_records_write", {}),
        ("dikciz_health_records_delete", {}),
        ("dikciz_health_permissions_request", {}),
        ("dikciz_wifi_connection_start", {}),
        ("dikciz_wifi_hotspot_start", {}),
        ("dikciz_apk_install_set", {}),
        ("dikciz_apk_install_cancel", {}),
    ]
    script.update(text="Routine fixture completed.", tools=writes + [("dikciz_device_state", {})])
    try:
        assert agent.chat("settings", conversationId=identifier)["settings"]["readOnly"] is False
        native_call("http", "assistantRoutineRun", {"routineId": "deviceAudit",
            "conversationId": identifier, "requestKey": uuid.uuid4().hex})
        rows = agent.wait_turn(identifier)
        results = [json.loads(json.loads(row["blocks"])[0]["text"]) for row in rows if row["role"] == "tool"]
        assert len(results) == len(writes) + 1, results
        for result in results[:-1]:
            assert result["isError"] and result.get("structuredContent", result)["code"] == "read_only_violation", result
        assert "batteryLevel" in json.dumps(results[-1])
        assert agent.chat("settings", conversationId=identifier)["settings"]["readOnly"] is False
        agent.assert_tool_pairing(records[-1]["body"])
    finally:
        agent.cleanup(identifier)

def test_companion_native_tabs_version_and_forms(websocket_control):
    import shutil
    from .test_operations_ux import settings, tap, capture
    from .test_launcher_controls import open_launcher_control_sheet

    restore_system_home()
    open_launcher_control_sheet()
    version = wait_for_snapshot_node(websocket_control, "launcher:controls:version")
    assert (LAB_ROOT / "VERSION").read_text().strip() in json.dumps(version)
    capture("launcher-version")
    device_command("input keyevent 4")
    settings(websocket_control)
    tap(websocket_control, "settings:companion")
    wait_for_snapshot_node(websocket_control, "companion:tab:safety")
    for tab in ("safety", "places", "trips", "devices", "scan", "health", "wi-fi"):
        tap(websocket_control, "companion:tab:" + tab)
        time.sleep(.8)
        result = native_call("http", "screenshot")
        assert base64.b64decode(result["pngBase64"]).startswith(b"\x89PNG")
        (ARTIFACT_DIRECTORY / ("companion-" + tab + ".png")).write_bytes(base64.b64decode(result["pngBase64"]))
    tap(websocket_control, "companion:tab:places")
    tap(websocket_control, "companion:fence:new")
    wait_for_snapshot_node(websocket_control, "companion:field:title")
    AUTOMATION.request(websocket_control, "setText", semanticId="companion:field:title", text="Draft fixture")
    device_command("settings put system user_rotation 1")
    device_command("settings put system user_rotation 0")
    tap(websocket_control, "companion:close")

def test_companion_followup_sos_and_fences(websocket_control):
    from .test_event_replay import replay_read
    grant_permissions(("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION",
                       "android.permission.POST_NOTIFICATIONS", "android.permission.SEND_SMS",
                       "android.permission.READ_PHONE_STATE", "android.permission.READ_PHONE_NUMBERS"))
    restore_system_home()
    native_call("http", "sosControl", {"action": "cancel"})
    plan = {"contacts": [{"address": "5550101"}], "graceSeconds": 5, "maximumUpdates": 1}
    sims = native_call("http", "simList")
    if sims["defaultSmsSubscriptionId"] < 0 and sims["items"]:
        plan["subscriptionId"] = sims["items"][0]["subscriptionId"]
    native_call("http", "sosPlanSave", plan)
    journal = native_call("http", "eventJournalStatus")
    fence = "fixture-" + uuid.uuid4().hex
    value = {"id": fence, "title": "Emulator geofence", "latitude": 1, "longitude": 1,
             "radiusMeters": 500, "enabled": True, "dwellMilliseconds": 500}
    try:
        native_call("http", "geofenceSave", value)
        emulator_geo_fix(1, 1)
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            events = replay_read("http", journal, eventTypes=["geofence.enter", "geofence.dwell"])["events"]
            kinds = {row["event"]["type"] for row in events}
            if {"geofence.enter", "geofence.dwell"} <= kinds:
                break
            time.sleep(1)
        assert {"geofence.enter", "geofence.dwell"} <= kinds, events
        current = next(row for row in native_call("http", "geofencesList")["items"] if row["id"] == fence)
        value["title"] = "Renamed fixture"
        renamed = native_call("mcp", "geofenceSave", value)
        assert renamed["state"] == current["state"] and renamed["changedAt"] == current["changedAt"]
        emulator_geo_fix(1.02, 1.02)
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            exits = replay_read("http", journal, eventTypes=["geofence.exit"])["events"]
            if exits:
                break
            time.sleep(.5)
        assert exits
        run = native_call("http", "sosControl", {"action": "trigger", "confirm": True})
        assert run["state"] == "warning"
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            status = native_call("http", "sosStatus")["run"]
            if status["state"] not in ("warning", "active"):
                break
            time.sleep(.5)
        assert status["updates"] == 1 and len(status["receipts"]) == 1, json.dumps(status)
        key = status["receipts"][0]["idempotencyKey"]
        if not sims["items"] and sims["defaultSmsSubscriptionId"] in (-1, 2147483647):
            assert status["state"] == "needs_review", json.dumps(status)
            assert status["receipts"][0]["code"] == "no_default_sms_subscription"
            native_call("mcp","smsStatus",{"idempotencyKey":key},error="sms_status_not_found")
        else:
            assert status["state"] == "completed", json.dumps(status)
            receipt = native_call("mcp", "smsStatus", {"idempotencyKey": key})
            assert receipt["idempotencyKey"] == key
        time.sleep(2)
        assert native_call("http", "sosStatus")["run"]["updates"] == 1
    finally:
        (ARTIFACT_DIRECTORY / "companion-sos-result.json").write_text(json.dumps(native_call("http","sosStatus"),indent=2))
        native_call("http", "sosControl", {"action": "cancel"})
        native_call("http", "geofenceDelete", {"id": fence})
        emulator_geo_fix(0, 0)


def test_companion_extended_health_validation_and_wifi_approval(websocket_control):
    for protocol in PROTOCOLS:
        catalogue = native_call(protocol, "healthRecordsCatalogue")
        assert all("writeShape" in row for row in catalogue["items"])
        now = int(time.time() * 1000)
        native_call(protocol, "healthRecordsWrite", {"kind": "steps", "records": [{
            "clientRecordId": "fixture", "startTimeMilliseconds": now - 1000,
            "endTimeMilliseconds": now, "values": {"count": "wrong"}}]}, error="validation_failed")
        if not catalogue["available"]:
            native_call(protocol, "healthRecordsRead", {"kind": "steps",
                "startTimeMilliseconds": now - 1000, "endTimeMilliseconds": now}, error="health_provider_unavailable")
        else:
            result = native_call(protocol, "healthRecordsRead", {"kind": "steps",
                "startTimeMilliseconds": now - 1000, "endTimeMilliseconds": now})
            assert result.get("state") == "permission_required" or isinstance(result.get("items"), list)
        for arguments in ({"kind": "rfcomm"}, {"kind": "gatt", "address": "bad"},
                          {"kind": "usb"}, {"kind": "nfc", "address": "wrong"}):
            native_call(protocol, "deviceConnectionOpen", arguments, error="validation_failed")
    grant_permissions(("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION",
                       "android.permission.NEARBY_WIFI_DEVICES"))
    restore_system_home()
    saved = native_call("http", "wifiNetworksConfigure", {"action": "save", "ssid": "Dikciz fixture", "security": "open"})
    assert saved["state"] == "user_action_required" and saved["connected"] is False
    wait_for_physical_text_bounds("Review in Android")
    run_device_operation("screenshot")
    import shutil
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "companion-wifi-approval.png")
    from .ui import direct_tap
    direct_tap(wait_for_physical_text_bounds("Cancel"))
    assert native_call("mcp", "wifiConnectionStatus", {"id": saved["id"]})["state"] == "cancelled"
    restore_system_home()


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
def test_companion_extended_event_routine_read_only(websocket_control, provider_endpoint, provider):
    endpoint, script, records = provider_endpoint
    _, identifier = agent.conversation(endpoint, provider)
    event = "fixture.routine." + uuid.uuid4().hex
    script.update(text="Event routine completed.", tools=[("dikciz_sos_control", {"action": "trigger", "confirm": True})])
    try:
        native_call("http", "assistantRoutineSubscribe", {"routineId": "eventReport",
            "conversationId": identifier, "ruleId": "fixture", "subscription": {
                "event": event, "minimumIntervalMilliseconds": 60000}})
        rules = json.loads(agent.chat("read", conversationId=identifier)["conversation"]["subscriptions"])
        assert rules[0]["routineId"] == "eventReport"
        native_call("http", "automationDispatch", {"sourceWidgetAddress": "1H1V-welcome",
            "actions": [{"type": "emitEvent", "event": event, "payload": {"fixture": True}}]})
        deadline = time.monotonic() + 20
        while not records and time.monotonic() < deadline:
            time.sleep(.1)
        assert records
        rows = agent.wait_turn(identifier)
        results = [json.loads(json.loads(row["blocks"])[0]["text"]) for row in rows if row["role"] == "tool"]
        assert len(results) == 1 and results[0].get("structuredContent", results[0])["code"] == "read_only_violation", results
        assert agent.chat("settings", conversationId=identifier)["settings"]["readOnly"] is False
    finally:
        agent.cleanup(identifier)

def test_companion_followup_vehicle(websocket_control):
    import copy
    original = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
    configured = copy.deepcopy(original)
    root = LAB_ROOT / "app/src/dikciz/assets/widget-library/vehicle-console"
    mock = r"""
window.addEventListener("dikciz-ready", () => {
    const actual = dikciz.command.bind(dikciz);
    let pending = "";
    const mockedCommand = async (type, fields) => {
        if (type === "bluetoothDevices") return {items:[{name:"Fixture ELM adapter",address:"00:11:22:33:44:55"}]};
        if (type === "deviceConnectionOpen") return {id:"fixture-adapter"};
        if (type === "deviceConnectionClose") return {state:"closed",hardwareReleaseConfirmed:true};
        if (type !== "deviceConnectionExchange") return actual(type, fields);
        if (fields.dataBase64) {
            const command = atob(fields.dataBase64).trim();
            pending = command === "010C" ? "41 0C 1A F8\r>" :
                      command === "0105" ? "41 05 7B\r>" :
                      command === "ATRV" ? "13.8V\r>" : "OK\r>";
        }
        const value = pending.slice(0, 4);
        pending = pending.slice(4);
        return {dataBase64:btoa(value),bytesRead:value.length};
    };
    window.dikciz = {...window.dikciz, command:mockedCommand};
    setTimeout(() => {
        document.getElementById("refresh").click();
        setTimeout(() => {
            document.getElementById("adapter").value = "00:11:22:33:44:55";
            document.getElementById("connect").click();
        }, 300);
    }, 300);
});
"""
    configuration_page(configured, FIXTURE_HOME_PAGE_ID)["widgets"] = [{
        "id": "vehicle-fixture", "type": "html", "title": "Vehicle fixture", "enabled": True,
        "html": (root / "index.html").read_text(), "css": (root / "style.css").read_text(),
        "javascript": mock + (root / "script.js").read_text(), "state": {},
        "cell": {"column": 0, "row": 0, "columnSpan": 4, "rowSpan": 6},
    }]
    configured["launcher"]["home"]["selectedPageId"] = FIXTURE_HOME_PAGE_ID
    AUTOMATION.request(websocket_control, "configReplace", config=configured)
    deadline = time.monotonic() + 20
    state = {}
    while time.monotonic() < deadline:
        current = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
        state = configuration_widget(current, "vehicle-fixture").get("state", {})
        if state.get("rpm") == 1726:
            break
        time.sleep(.25)
    assert state.get("rpm") == 1726 and state.get("coolant") == 83 and state.get("voltage") == 13.8, state
    run_device_operation("screenshot")
    import shutil
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "companion-vehicle-mocked.png")

def test_companion_followup_split_install(websocket_control):
    import hashlib
    from .test_device_files import tap_installer_positive
    package = "eu.psyb0t.dikciz.fixture"
    assert not device_command("pm list packages " + package).strip(), "Split fixture requires its isolated package to be absent"
    paths = []
    def wait_install(session, expected):
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            result = native_call("http", "apkInstallStatus", {"sessionId": session})
            if result["state"] in expected:
                return result
            assert result["state"] not in ("failed", "cancelled"), result
            time.sleep(.25)
        raise AssertionError(result)
    try:
        for name in ("base.apk", "config.apk"):
            data = (LAB_ROOT / ".testing/split-fixture" / name).read_bytes()
            path = "/sdcard/Dikciz/split-" + uuid.uuid4().hex + ".apk"
            paths.append(path)
            for offset in range(0, len(data), 65536):
                native_call("http", "fileWrite", {"path": path, "offset": offset,
                    "dataBase64": base64.b64encode(data[offset:offset + 65536]).decode()})
        native_call("http", "apkInstallSet", {"apks": [{"path": paths[0]}, {"path": paths[0]}]}, error="validation_failed")
        device_command("appops set " + DIKCIZ_DEBUG_PACKAGE + " REQUEST_INSTALL_PACKAGES allow")
        bad = native_call("http", "apkInstallSet", {"apks": [{"path": paths[0], "sha256": "0" * 64}, {"path": paths[1]}]})
        result = wait_install(bad["sessionId"], {"failed"})
        assert result["reason"] == "apk_checksum_mismatch"
        request = {"apks": [{"path": path} for path in paths]}
        pending = native_call("mcp", "apkInstallSet", request)
        wait_install(pending["sessionId"], {"pending_user_action"})
        cancelled = native_call("websocket", "apkInstallCancel", {"sessionId": pending["sessionId"]})
        assert cancelled["cancellationAccepted"] and cancelled["state"] == "cancelled"
        assert native_call("http", "apkInstallStatus", {"sessionId": pending["sessionId"]})["state"] == "cancelled"
        device_command("input keyevent 4")
        restore_system_home()
        queued = native_call("http", "apkInstallSet", request)
        wait_install(queued["sessionId"], {"pending_user_action"})
        tap_installer_positive()
        installed = wait_install(queued["sessionId"], {"succeeded"})
        assert installed["packageName"] == package and len(installed["apks"]) == 2
        locations = device_command("pm path " + package).strip().splitlines()
        assert len(locations) == 2, locations
        from .test_capability_expansion import wait_native_control
        wait_native_control("mcp")
        assert native_call("mcp", "apkInstallCancel", {"sessionId": queued["sessionId"]})["cancellationAccepted"] is False
    finally:
        if device_command("pm list packages " + package).strip():
            device_command("pm uninstall " + package)
        device_command("appops set " + DIKCIZ_DEBUG_PACKAGE + " REQUEST_INSTALL_PACKAGES default")
        restore_system_home()
        from .test_capability_expansion import wait_native_control
        wait_native_control("http")
        for path in paths:
            native_call("http", "fileManage", {"action": "delete", "path": path})

@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_companion_extended_scoped_token_denial(token_owner, protocol):
    from .test_remote_tokens import create, call
    token, _ = create(token_owner, ["sms.read"])
    for command in AUTOMATION.COMPANION_COMMANDS:
        call(token, command, {}, protocol, "remote_permission_denied")

def test_companion_followup_health(websocket_control):
    from .ui import direct_tap
    restore_system_home()
    requested = native_call("http", "healthPermissionsRequest", {"kinds": ["steps"], "access": "readWrite"})
    assert requested["state"] == "user_action_required"
    direct_tap(wait_for_physical_text_bounds("Review in Health Connect"))
    from .ui import physical_nodes
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        labels = {node.get("text") for node in physical_nodes()}
        if "Allow all" in labels:
            break
        if "Get started" in labels:
            direct_tap(wait_for_physical_text_bounds("Get started"))
        elif "Review in Health Connect" in labels:
            direct_tap(wait_for_physical_text_bounds("Review in Health Connect"))
        elif "App permissions" in labels:
            device_command("input keyevent 4")
        time.sleep(.25)
    direct_tap(wait_for_physical_text_bounds("Allow all"))
    direct_tap(wait_for_physical_text_bounds("Allow"))
    restore_system_home()
    catalogue = native_call("http", "healthRecordsCatalogue")
    steps = next(row for row in catalogue["items"] if row["kind"] == "steps")
    assert steps["readGranted"] and steps["writeGranted"], steps
    end = int(time.time() * 1000) - 1000
    start = end - 60000
    prefix = "fixture-" + uuid.uuid4().hex
    rows = [{"clientRecordId": prefix + "-" + str(index),
             "startTimeMilliseconds": start + index * 2000,
             "endTimeMilliseconds": start + index * 2000 + 1000,
             "values": {"count": 3}} for index in range(13)]
    ids = []
    try:
        result = native_call("http", "healthRecordsWrite", {"kind": "steps", "records": rows})
        ids = result["recordIds"]
        assert len(ids) == 13
        query = {"kind": "steps", "startTimeMilliseconds": start, "endTimeMilliseconds": end, "limit": 5}
        found = []
        while True:
            page = native_call("mcp", "healthRecordsRead", query)
            found.extend(row["id"] for row in page["items"] if row["clientRecordId"].startswith(prefix))
            if not page["hasMore"]:
                break
            query["pageToken"] = page["nextPageToken"]
        assert set(found) == set(ids) and len(found) == 13
        aggregate = native_call("websocket", "healthRecordsAggregate", {"kind": "steps",
            "startTimeMilliseconds": start, "endTimeMilliseconds": end})
        assert aggregate["values"]["count"] >= 39, aggregate
        native_call("http", "healthRecordsDelete", {"kind": "steps", "recordIds": ids}, error="validation_failed")
    finally:
        if ids:
            native_call("http", "healthRecordsDelete", {"kind": "steps", "recordIds": ids, "confirm": True})
