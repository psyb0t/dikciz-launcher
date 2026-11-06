"""Editable custom apps through the production Android host and all transports."""

import base64
import io
import json
import shutil
import time
import uuid
import zipfile

import pytest

from . import *
from .test_native_data import native_call, PROTOCOLS, grant_permissions
from .test_operations_ux import tap
from .test_remote_tokens import token_owner, create as token_create, call as token_call, catalogue as token_catalogue
from .test_remote_tokens import http_command
from .test_chat_agent import provider_endpoint, conversation, chat, send, wait_turn, cleanup
from .ui import direct_tap, wait_for_physical_text_bounds

COMMAND_LIST = "customAppsList"
COMMAND_READ = "customAppRead"
COMMAND_INSTALL = "customAppInstall"
COMMAND_CONTROL = "customAppControl"
COMMAND_EVENT = "customAppEvent"
COMMAND_STATE = "customAppState"
COMMAND_SCHEDULE = "customAppSchedule"
COMMAND_ALERT = "customAppAlert"
TIMEOUT_SECONDS = 20
POLL_SECONDS = .2


def app_fields(identifier, source=None, language="javascript", commands=None, events=None):
    if source is None:
        source = "function onEvent(event) { return {status:'Counted', result:{language:'javascript', nil:null}, actions:[{type:'patchState',values:{count:(context.state.count || 0)+1}}]}; }"
    return {"manifest": {"version": 1, "id": identifier, "title": "Custom app fixture", "description": "Private emulator fixture",
                         "language": language, "commands": commands or [], "events": events or []},
            "html": '<main><h1>Custom app fixture</h1><p id="value">Ready</p></main>', "css": "body{padding:24px;background:#172017}",
            "javascript": "window.addEventListener('dikciz-state', e => { document.getElementById('value').textContent = JSON.stringify(e.detail); });",
            "backgroundSource": source, "confirm": True}


def control(identifier, action, protocol="http", **fields):
    return native_call(protocol, COMMAND_CONTROL, {"appId": identifier, "action": action, **fields})


@pytest.fixture
def app_factory(websocket_control):
    identifiers = []

    def create(protocol="http", **options):
        identifier = "fixture_" + uuid.uuid4().hex[:12]
        fields = app_fields(identifier, **options)
        native_call(protocol, COMMAND_INSTALL, fields)
        identifiers.append(identifier)
        return identifier, fields

    yield create
    for identifier in identifiers:
        native_call("http", COMMAND_CONTROL, {"appId": identifier, "action": "remove", "confirm": True}, allowed_errors=("custom_app_not_found",))


def wait_state(identifier, key, expected):
    deadline = time.monotonic() + TIMEOUT_SECONDS
    last = {}
    while time.monotonic() < deadline:
        last = native_call("http", COMMAND_READ, {"appId": identifier})
        if last["state"].get(key) == expected:
            return last
        time.sleep(POLL_SECONDS)
    raise AssertionError(f"App state did not reach {key}={expected!r}: {last}")


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_custom_apps_install_enable_invoke_and_native_binding(app_factory, protocol):
    source = "function onEvent(event) { var reply = context.request({type:'deviceInfo'}); var state = context.request({type:'deviceState',autoRequestPermissions:true}); return {result:{native:reply.type==='result' && state.type==='result'},actions:[{type:'patchState',values:{count:(context.state.count||0)+1}}]}; }"
    identifier, fields = app_factory(protocol, source=source)
    assert native_call(protocol, COMMAND_READ, {"appId": identifier})["enabled"]
    reply = native_call(protocol, COMMAND_EVENT, {"appId": identifier, "event": "example.count", "payload": {"text": "Unicode: 🧀"}})
    assert reply["outcome"] == "completed" and reply["result"] == {"native": True}
    assert reply["state"] == {"count": 1}
    saved = native_call(protocol, COMMAND_READ, {"appId": identifier})
    assert saved["manifest"] == fields["manifest"] and saved["backgroundSource"] == source
    assert saved["state"] == {"count": 1} and saved["enabled"]
    assert json.loads(device_command(f"cat /sdcard/Dikciz/apps/{identifier}/state.json")) == {"count": 1}
    assert native_call(protocol, "customAppRuntime")["invocationId"]


def test_custom_apps_lua_result_and_independent_state(app_factory):
    lua = "function on_event(event) return {result={language='lua'},actions={{type='patchState',values={count=(context.state.count or 0)+1}}}} end"
    identifier, _ = app_factory(source=lua, language="lua")
    second, _ = app_factory()
    control(identifier, "enable", confirm=True)
    control(second, "enable", confirm=True)
    result = native_call("http", COMMAND_EVENT, {"appId": identifier, "event": "example.count"})
    assert result["result"] == {"language": "lua"} and result["state"] == {"count": 1}
    result = native_call("http", COMMAND_EVENT, {"appId": second, "event": "example.count"})
    assert result["result"] == {"language": "javascript", "nil": None} and result["state"] == {"count": 1}
    assert native_call("http", COMMAND_READ, {"appId": identifier})["state"] == {"count": 1}


def test_custom_apps_owner_can_prepare_disabled_state(app_factory):
    identifier, _ = app_factory()
    control(identifier, "disable")
    native_call("http", COMMAND_STATE, {"appId": identifier, "action": "replace", "state": {"count": 4}})
    native_call("http", COMMAND_EVENT, {"appId": identifier, "event": "example.count"}, error="custom_app_disabled")
    control(identifier, "enable")
    assert native_call("http", COMMAND_EVENT, {"appId": identifier, "event": "example.count"})["state"] == {"count": 5}


def test_custom_apps_json_patch_preserves_nulls_and_array_positions(app_factory):
    source = "function onEvent(event){return {actions:[{type:'patchState',values:{nullable:null,nested:{empty:null},values:[1,null,3]}}]};}"
    identifier, _ = app_factory(source=source)
    native_call("http", COMMAND_STATE, {"appId": identifier, "action": "replace", "state": {"nullable": "old"}})
    control(identifier, "enable", confirm=True)
    result = native_call("http", COMMAND_EVENT, {"appId": identifier, "event": "example.null"})
    expected = {"nullable": None, "nested": {"empty": None}, "values": [1, None, 3]}
    assert result["state"] == expected
    assert json.loads(device_command(f"cat /sdcard/Dikciz/apps/{identifier}/state.json")) == expected


def test_custom_apps_host_state_patch_merges_existing_fields(app_factory):
    source = "function onEvent(event){context.request({type:'automationDispatch',actions:[{type:'patchState',values:{first:1}}]});context.request({type:'automationDispatch',actions:[{type:'patchState',values:{second:2}}]});return {actions:[]};}"
    identifier, _ = app_factory(source=source)
    native_call("http", COMMAND_STATE, {"appId": identifier, "action": "replace", "state": {"retained": True}})
    control(identifier, "enable", confirm=True)
    result = native_call("http", COMMAND_EVENT, {"appId": identifier, "event": "example.merge"})
    assert result["state"] == {"retained": True, "first": 1, "second": 2}


def test_custom_apps_invalid_subscription_is_rejected_without_permission_approval(app_factory):
    identifier, fields = app_factory()
    fields["manifest"]["eventSubscriptions"] = [{"event": "sensor"}]
    native_call("http", "fileWrite", {"path": f"/sdcard/Dikciz/apps/{identifier}/app.json", "dataBase64": base64.b64encode(json.dumps(fields["manifest"]).encode()).decode(), "overwrite": True})
    native_call("http", COMMAND_CONTROL, {"appId": identifier, "action": "enable", "confirm": True}, error="validation_failed")
    assert native_call("http", COMMAND_READ, {"appId": identifier})["enabled"]


def test_custom_apps_replace_preserves_state_data_and_running_state(app_factory):
    identifier, fields = app_factory()
    control(identifier, "enable", confirm=True)
    native_call("http", COMMAND_EVENT, {"appId": identifier, "event": "example.count"})
    directory = f"/sdcard/Dikciz/apps/{identifier}/files"
    native_call("http", "fileMkdir", {"path": directory, "parents": True})
    data = base64.b64encode(b"\x00private app data\xff").decode()
    native_call("http", "fileWrite", {"path": directory + "/fixture.bin", "dataBase64": data})
    fields["html"] = "<h1>Replaced UI</h1>"
    native_call("http", COMMAND_INSTALL, fields)
    saved = native_call("http", COMMAND_READ, {"appId": identifier})
    assert saved["state"] == {"count": 1} and saved["enabled"]
    assert native_call("http", "fileRead", {"path": directory + "/fixture.bin"})["dataBase64"] == data
    assert native_call("http", COMMAND_EVENT, {"appId": identifier, "event": "example.count"})["state"] == {"count": 2}


@pytest.mark.parametrize("key,value", [("id", "../escape"), ("id", "exports"), ("id", True), ("language", "python"), ("version", True), ("owner", True)])
def test_custom_apps_manifest_validation_is_atomic(websocket_control, key, value):
    identifier = "fixture_" + uuid.uuid4().hex[:12]
    fields = app_fields(identifier)
    fields["manifest"][key] = value
    before = native_call("http", COMMAND_LIST)
    native_call("http", COMMAND_INSTALL, fields, error="validation_failed")
    assert native_call("http", COMMAND_LIST) == before


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_custom_apps_inherit_phone_access_and_can_address_other_apps(app_factory, protocol):
    other, _ = app_factory()
    control(other, "disable")
    source = """
function onEvent(event) {
    var device = context.request({type:'deviceInfo'});
    var apps = context.request({type:'customAppsList'});
    var start = context.request({type:'customAppControl',appId:OTHER,action:'enable'});
    var write = context.request({type:'customAppState',appId:OTHER,action:'replace',state:{shared:true}});
    var read = context.request({type:'customAppRead',appId:OTHER});
    return {result:{device:device.type,apps:apps.type,start:start.type,write:write.type,shared:read.result.state.shared}};
}
""".replace("OTHER", json.dumps(other))
    identifier, fields = app_factory(source=source)
    del fields["manifest"]["commands"]
    native_call(protocol, COMMAND_INSTALL, fields)
    result = native_call(protocol, COMMAND_EVENT, {"appId": identifier, "event": "example.check"})
    assert result["result"] == {"device":"result", "apps":"result", "start":"result", "write":"result", "shared":True}
    assert native_call(protocol, COMMAND_READ, {"appId": other})["state"] == {"shared":True}


def test_custom_apps_archive_round_trip_excludes_state_and_rejects_traversal(app_factory):
    identifier, fields = app_factory()
    control(identifier, "enable", confirm=True)
    native_call("http", COMMAND_STATE, {"appId": identifier, "action": "replace", "state": {"private": "not exported"}})
    exported = control(identifier, "export")
    archive_data = base64.b64decode(native_call("http", "fileRead", {"path": exported["path"]})["dataBase64"])
    with zipfile.ZipFile(io.BytesIO(archive_data)) as archive:
        assert set(archive.namelist()) == {"app.json", "ui.html", "ui.css", "ui.js", "main.js"}
        assert archive.read("main.js").decode() == fields["backgroundSource"]
    control(identifier, "remove", confirm=True)
    native_call("http", COMMAND_CONTROL, {"action": "import", "path": exported["path"], "confirm": True})
    saved = native_call("http", COMMAND_READ, {"appId": identifier})
    assert saved["state"] == {} and saved["enabled"]
    malformed = io.BytesIO()
    with zipfile.ZipFile(malformed, "w") as archive:
        archive.writestr("../escape", "hostile")
    path = f"/sdcard/Dikciz/apps/{identifier}/hostile.dikciz-app.zip"
    native_call("http", "fileWrite", {"path": path, "dataBase64": base64.b64encode(malformed.getvalue()).decode()})
    native_call("http", COMMAND_CONTROL, {"action": "import", "path": path, "confirm": True}, error="validation_failed")
    assert native_call("http", COMMAND_READ, {"appId": identifier})["state"] == {}


@pytest.mark.parametrize("identifier", ["sos", "voice-notes", "agenda", "device-status", "travel-todo"])
def test_custom_apps_bundled_screens_and_editor(websocket_control, identifier):
    restore_system_home()
    control(identifier, "open")
    try:
        wait_for_snapshot_node(websocket_control, "custom-app:back")
        deadline = time.monotonic() + TIMEOUT_SECONDS
        while True:
            snapshot = AUTOMATION.request(websocket_control, "snapshot")
            if snapshot.get("appId") == identifier:
                break
            assert time.monotonic() < deadline, snapshot
            time.sleep(POLL_SECONDS)
        time.sleep(3)
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / ("custom-app-" + identifier + ".png"))
        assert native_call("http", COMMAND_READ, {"appId": identifier})["uiVisible"]
        tap(websocket_control, "custom-app:back")
        wait_for_snapshot_node(websocket_control, "custom-app:new")
        tap(websocket_control, "custom-app:" + identifier + ":edit")
        for field in ("manifest", "html", "css", "javascript", "backgroundSource"):
            AUTOMATION.request(websocket_control, "scrollTo", semanticId="custom-app:editor:" + field)
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / ("custom-app-editor-" + identifier + ".png"))
    finally:
        restore_system_home()


def test_custom_apps_native_editor_saves_complete_bundle(websocket_control, app_factory):
    identifier, fields = app_factory()
    control(identifier, "enable", confirm=True)
    control(identifier, "open")
    try:
        wait_for_snapshot_node(websocket_control, "custom-app:back")
        tap(websocket_control, "custom-app:back")
        wait_for_snapshot_node(websocket_control, "custom-app:new")
        tap(websocket_control, "custom-app:" + identifier + ":edit")
        wait_for_snapshot_node(websocket_control, "custom-app:editor:save")
        replacements = {"html": "<h1>Edited app</h1>", "css": "body{color:gold}",
                        "javascript": "document.body.dataset.edited='yes';", "backgroundSource": fields["backgroundSource"]}
        for field, text in replacements.items():
            AUTOMATION.request(websocket_control, "scrollTo", semanticId="custom-app:editor:" + field)
            AUTOMATION.request(websocket_control, "setText", semanticId="custom-app:editor:" + field, text=text)
        tap(websocket_control, "custom-app:editor:save")
        wait_for_snapshot_node(websocket_control, "custom-app:new")
        saved = native_call("http", COMMAND_READ, {"appId": identifier})
        assert saved["enabled"]
        assert all(saved[field] == text for field, text in replacements.items())
        wait_for_snapshot_node(websocket_control, "custom-app:confirm", should_exist=False)
        assert native_call("http", COMMAND_READ, {"appId": identifier})["enabled"]
    finally:
        restore_system_home()


def test_custom_apps_generic_urgent_modal_cancel(websocket_control, app_factory):
    grant_permissions(("android.permission.POST_NOTIFICATIONS",))
    device_command(f"appops set {DIKCIZ_DEBUG_PACKAGE} SYSTEM_ALERT_WINDOW allow")
    identifier, _ = app_factory()
    control(identifier, "enable", confirm=True)
    native_call("http", COMMAND_ALERT, {"appId": identifier, "action": "show", "episodeId": "fixture",
        "durationMilliseconds": 60000, "title": "Fixture countdown", "detail": "Cancel this private emulator demonstration.",
        "cancelLabel": "Cancel demonstration", "cancelEvent": "example.cancel"})
    try:
        wait_for_snapshot_node(websocket_control, "app:" + identifier + ":warning:cancel")
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "custom-app-modal.png")
        tap(websocket_control, "app:" + identifier + ":warning:cancel")
        wait_state(identifier, "count", 1)
        wait_for_snapshot_node(websocket_control, "app:" + identifier + ":warning:cancel", should_exist=False)
        assert not native_call("http", COMMAND_READ, {"appId": identifier})["alert"]["active"]
    finally:
        native_call("http", COMMAND_ALERT, {"appId": identifier, "action": "close"})


def test_custom_apps_native_battery_source_without_rendered_ui(app_factory):
    source = "function onEvent(event) { return {actions:[{type:'patchState',values:{level:event.payload.level}}]}; }"
    identifier, _ = app_factory(source=source, events=["battery"])
    control(identifier, "enable", confirm=True)
    try:
        time.sleep(2)
        device_command("dumpsys battery set level 73")
        wait_state(identifier, "level", 73)
        assert not native_call("http", COMMAND_READ, {"appId": identifier})["uiVisible"]
    finally:
        device_command("dumpsys battery reset")


def test_custom_apps_edited_sos_preview_cannot_write(websocket_control):
    original = native_call("http", COMMAND_READ, {"appId": "sos"})
    path = "/sdcard/Dikciz/apps/sos/main.js"
    key = "readonly_" + uuid.uuid4().hex
    source = "function onEvent(event) { var send=context.request({type:'smsSend',recipient:'5550101',body:'Never submit',idempotencyKey:'" + key + "'}); var write=context.request({type:'customAppState',action:'replace',state:{changed:true}}); var prompt=context.request({type:'locationGet',autoRequestPermissions:true}); return {result:{send:send.code,write:write.code,prompt:prompt.code}}; }"
    try:
        native_call("http", "fileWrite", {"path": path, "dataBase64": base64.b64encode(source.encode()).decode(), "overwrite": True})
        result = native_call("http", "sosPreview")
        assert result == {"send": "native_read_only", "write": "native_read_only", "prompt": "native_read_only"}
        assert native_call("http", COMMAND_READ, {"appId": "sos"})["state"] == original["state"]
        native_call("http", "smsStatus", {"idempotencyKey": key}, error="sms_status_not_found")
        source = "function onEvent(event) { return {actions:[{type:'patchState',values:{changed:true}}]}; }"
        native_call("http", "fileWrite", {"path": path, "dataBase64": base64.b64encode(source.encode()).decode(), "overwrite": True})
        native_call("http", "sosPreview", error="native_read_only")
        assert native_call("http", COMMAND_READ, {"appId": "sos"})["state"] == original["state"]
    finally:
        native_call("http", "fileWrite", {"path": path, "dataBase64": base64.b64encode(original["backgroundSource"].encode()).decode(), "overwrite": True})


def test_custom_apps_pending_timer_survives_launcher_restart(app_factory):
    identifier, _ = app_factory()
    control(identifier, "enable", confirm=True)
    native_call("http", COMMAND_SCHEDULE, {"appId": identifier, "action": "schedule", "timerId": "restart", "event": "example.timer", "delayMilliseconds": 4000})
    native_call("http", "restart")
    wait_for_restarted_websocket_control().close()
    saved = wait_state(identifier, "count", 1)
    assert len(saved["timers"]) == 1 and saved["timers"][0]["state"] == "completed"
    time.sleep(2)
    assert native_call("http", COMMAND_READ, {"appId": identifier})["state"] == {"count": 1}


def test_custom_apps_process_recovery_preserves_pending_and_never_replays_claimed(app_factory):
    identifier, fields = app_factory(commands=["fileWrite", "shell"])
    marker = f"/sdcard/Dikciz/apps/{identifier}/started.txt"
    fields["backgroundSource"] = """
function onEvent(event) {
    if (event.type === 'app.resume') return {actions:[{type:'patchState',values:{recovered:true}}]};
    if (event.type === 'example.pending') return {actions:[{type:'patchState',values:{count:(context.state.count||0)+1}}]};
    if (event.type === 'example.claimed') {
        context.request({type:'fileWrite',path:MARKER,dataBase64:'eA==',overwrite:true});
        context.request({type:'shell',root:false,command:'sleep 15',timeoutMilliseconds:30000});
        return {actions:[{type:'patchState',values:{completed:true}}]};
    }
    return {actions:[]};
}
""".replace("MARKER", json.dumps(marker))
    native_call("http", COMMAND_INSTALL, fields)
    control(identifier, "enable", confirm=True)
    native_call("http", COMMAND_SCHEDULE, {"appId": identifier, "action": "schedule", "timerId": "pending", "event": "example.pending", "delayMilliseconds": 8000})
    run_device_operation("app-stop", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
    restore_system_home()
    wait_for_restarted_websocket_control().close()
    wait_state(identifier, "count", 1)
    native_call("http", COMMAND_SCHEDULE, {"appId": identifier, "action": "schedule", "timerId": "claimed", "event": "example.claimed", "delayMilliseconds": 1000})
    deadline = time.monotonic() + TIMEOUT_SECONDS
    started = {}
    while time.monotonic() < deadline:
        started = native_call("http", "fileRead", {"path": marker}, allowed_errors=("file_not_found",))
        if started.get("dataBase64") == "eA==": break
        time.sleep(POLL_SECONDS)
    assert started.get("dataBase64") == "eA==", started
    run_device_operation("app-stop", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
    restore_system_home()
    wait_for_restarted_websocket_control().close()
    saved = wait_state(identifier, "recovered", True)
    assert saved["state"] == {"count": 1, "recovered": True}
    timers = {timer["timerId"]: timer for timer in saved["timers"]}
    assert timers["pending"]["state"] == "completed" and timers["claimed"]["state"] == "needs_review"
    time.sleep(2)
    assert native_call("http", COMMAND_READ, {"appId": identifier})["state"] == saved["state"]


def test_custom_apps_location_permission_loss_cancels_pending_work(app_factory):
    identifier, fields = app_factory()
    fields["manifest"]["locationMonitoring"] = True
    native_call("http", COMMAND_INSTALL, fields)
    control(identifier, "enable", confirm=True)
    permissions = ("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION")
    try:
        for permission in permissions:
            device_command(f"pm revoke {DIKCIZ_DEBUG_PACKAGE} {permission}")
        restore_system_home()
        wait_for_restarted_websocket_control().close()
        native_call("http", COMMAND_SCHEDULE, {"appId": identifier, "action": "schedule", "timerId": "denied", "event": "example.timer", "delayMilliseconds": 3000})
        deadline = time.monotonic() + TIMEOUT_SECONDS
        while time.monotonic() < deadline:
            saved = native_call("http", COMMAND_READ, {"appId": identifier})
            if saved["timers"][0]["state"] == "cancelled": break
            time.sleep(POLL_SECONDS)
        assert saved["timers"][0]["state"] == "cancelled", saved
        assert saved["timers"][0]["reason"] == "foreground_start_rejected" and saved["state"] == {}
    finally:
        grant_permissions(permissions)


def test_custom_apps_voice_note_stops_when_ui_closes(websocket_control):
    grant_permissions(("android.permission.RECORD_AUDIO",))
    original = native_call("http", COMMAND_READ, {"appId": "voice-notes"})["state"]
    try:
        control("voice-notes", "open")
        wait_for_snapshot_node(websocket_control, "custom-app:back")
        time.sleep(2)
        direct_tap(wait_for_physical_text_bounds("Record voice note"))
        deadline = time.monotonic() + TIMEOUT_SECONDS
        session = None
        while time.monotonic() < deadline:
            session = native_call("http", COMMAND_READ, {"appId": "voice-notes"})["state"].get("sessionId")
            if session: break
            time.sleep(POLL_SECONDS)
        assert session, "Voice note did not start"
        time.sleep(2)
        tap(websocket_control, "custom-app:back")
        wait_state("voice-notes", "sessionId", None)
        result = native_call("http", "microphoneSession", {"action": "status", "sessionId": session})
        assert result["state"] == "completed", result
        control("voice-notes", "open")
        deadline = time.monotonic() + TIMEOUT_SECONDS
        while time.monotonic() < deadline:
            saved = native_call("http", COMMAND_READ, {"appId": "voice-notes"})["state"]
            if saved.get("recordings") and not saved.get("pendingRecording"): break
            time.sleep(POLL_SECONDS)
        assert saved.get("recordings") and not saved.get("pendingRecording"), saved
        path = "/sdcard/Dikciz/apps/voice-notes/files/" + saved["recordings"][0]["name"]
        assert native_call("http", "fileManage", {"action": "stat", "path": path})
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "custom-app-voice-saved.png")
        native_call("http", "fileManage", {"action": "delete", "path": path})
    finally:
        restore_system_home()
        native_call("http", COMMAND_STATE, {"appId": "voice-notes", "action": "replace", "state": original})


def test_custom_apps_scoped_token_cannot_read_or_execute(token_owner):
    token, _ = token_create(token_owner, ["sms.read"])
    assert not any("custom_app" in name for name in token_catalogue(token))
    for protocol in PROTOCOLS:
        for command, fields in ((COMMAND_LIST, {}), (COMMAND_READ, {"appId": "sos"}),
                                (COMMAND_EVENT, {"appId": "sos", "event": "trigger", "payload": {"confirm": True}}),
                                (COMMAND_CONTROL, {"appId": "sos", "action": "enable", "confirm": True})):
            token_call(token, command, fields, protocol, error="remote_permission_denied")


def test_custom_apps_exact_byte_limits_and_disabled_stop_cleanup(app_factory):
    source = "function onEvent(event){var reply=context.request({type:'smsSend',recipient:'5550101',body:'Never submit'});return {actions:[{type:'patchState',values:{stopped:event.type,blocked:reply.code}}]};}"
    identifier, fields = app_factory(source=source, commands=["smsSend"])
    control(identifier, "enable", confirm=True)
    overhead = len(json.dumps({"padding": ""}, separators=(",", ":")).encode())
    state = {"padding": "x" * (131072 - overhead)}
    status, result = http_command(None, COMMAND_STATE, {"appId": identifier, "action": "replace", "state": state})
    assert status == 200 and result["type"] == "result", result
    status, result = http_command(None, COMMAND_STATE, {"appId": identifier, "action": "replace", "state": {"padding": state["padding"] + "x"}})
    assert status == 200 and result["code"] == "validation_failed", result
    native_call("http", COMMAND_STATE, {"appId": identifier, "action": "replace", "state": {}})
    control(identifier, "disable")
    saved = native_call("http", COMMAND_READ, {"appId": identifier})
    assert not saved["enabled"] and saved["state"] == {"stopped": "app.stopped", "blocked": "custom_app_cancel_requested"}
    fields["backgroundSource"] = " " * 262144
    status, result = http_command(None, COMMAND_INSTALL, fields)
    assert status == 200 and result["type"] == "result", result
    fields["backgroundSource"] += "x"
    status, result = http_command(None, COMMAND_INSTALL, fields)
    assert status == 200 and result["code"] == "validation_failed", result
    assert len(native_call("http", COMMAND_READ, {"appId": identifier})["backgroundSource"]) == 262144


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
def test_custom_apps_read_only_chat_hides_and_blocks_mutations(websocket_control, provider_endpoint, provider):
    endpoint, script, _ = provider_endpoint
    _, identifier = conversation(endpoint, provider)
    try:
        chat("configure", conversationId=identifier, settings={"readOnly": True})
        script.update(text="Discovery complete.", wrapper_calls=[("phone_tools", {"query": "custom app"})])
        send(identifier)
        rows = wait_turn(identifier)
        results = [json.loads(json.loads(row["blocks"])[0]["text"]) for row in rows if row["role"] == "tool"]
        names = {tool["name"] for tool in results[0]["tools"]}
        assert "dikciz_custom_app_read" in names
        assert not names.intersection({"dikciz_custom_app_event", "dikciz_custom_app_install", "dikciz_custom_app_control", "dikciz_custom_app_state", "dikciz_custom_app_schedule", "dikciz_custom_app_alert"})
        assert all(tool["annotations"]["readOnlyHint"] for tool in results[0]["tools"])
    finally:
        cleanup(identifier)
    _, identifier = conversation(endpoint, provider)
    try:
        chat("configure", conversationId=identifier, settings={"readOnly": True})
        writes = [("dikciz_custom_app_" + name, {}) for name in ("event", "install", "control", "state", "schedule", "alert")]
        script.update(wrapper_calls=[], tools=writes + [("dikciz_custom_apps_list", {})], text="Read-only complete.")
        send(identifier)
        rows = wait_turn(identifier)
        results = [json.loads(json.loads(row["blocks"])[0]["text"]) for row in rows if row["role"] == "tool"]
        assert len(results) == len(writes) + 1
        assert all(result["isError"] and result.get("structuredContent", result)["code"] == "read_only_violation" for result in results[:-1])
        assert "items" in results[-1].get("structuredContent", results[-1])
    finally:
        cleanup(identifier)


def test_custom_apps_custom_event_runs_headless_and_timer_cancel(app_factory):
    event_name = "example.apps." + uuid.uuid4().hex[:10]
    identifier, _ = app_factory(events=[event_name])
    control(identifier, "enable", confirm=True)
    native_call("http", "automationDispatch", {"sourceWidgetAddress": "1H1V-welcome", "actions": [{"type": "emitEvent", "event": event_name, "payload": {"value": 1}}]})
    wait_state(identifier, "count", 1)
    native_call("http", COMMAND_SCHEDULE, {"appId": identifier, "action": "schedule", "timerId": "fixture", "delayMilliseconds": 5000, "event": "example.timer"})
    native_call("http", COMMAND_SCHEDULE, {"appId": identifier, "action": "cancel", "timerId": "fixture"})
    time.sleep(6)
    assert native_call("http", COMMAND_READ, {"appId": identifier})["state"] == {"count": 1}
    native_call("http", COMMAND_SCHEDULE, {"appId": identifier, "action": "schedule", "timerId": "fixture", "delayMilliseconds": 1000, "event": "example.timer"})
    try:
        device_command("input keyevent KEYCODE_SLEEP")
        wait_state(identifier, "count", 2)
    finally:
        device_command("input keyevent KEYCODE_WAKEUP")
        device_command("wm dismiss-keyguard")
        restore_system_home()


def test_custom_apps_list_pagination_and_state_boundaries(app_factory):
    for _ in range(6): app_factory()
    expected = native_call("http", COMMAND_LIST, {"limit": 200})["items"]
    observed = []
    offset = 0
    while True:
        page = native_call("http", COMMAND_LIST, {"limit": 3, "offset": offset})
        observed.extend(page["items"])
        assert page["hasMore"] == (offset + len(page["items"]) < len(expected))
        if not page["hasMore"]: break
        offset += 3
    assert observed == expected
    assert native_call("http", COMMAND_LIST, {"limit": 3, "offset": len(expected)})["items"] == []
    assert native_call("http", COMMAND_LIST, {"limit": 3, "offset": 100000})["items"] == []
    identifier, _ = app_factory()
    control(identifier, "enable", confirm=True)
    nested = {}
    for _ in range(18): nested = {"nested": nested}
    native_call("http", COMMAND_STATE, {"appId": identifier, "action": "replace", "state": nested}, error="validation_failed")
    native_call("http", COMMAND_SCHEDULE, {"appId": identifier, "action": "schedule", "timerId": "bad", "delayMilliseconds": 0, "event": "example.bad"}, error="validation_failed")
    assert native_call("http", COMMAND_READ, {"appId": identifier})["state"] == {}


def test_custom_apps_manifest_edits_keep_access_and_stopped_state(app_factory):
    identifier, fields = app_factory()
    fields["manifest"]["title"] = "Edited without approval"
    native_call("http", "fileWrite", {"path":f"/sdcard/Dikciz/apps/{identifier}/app.json", "dataBase64":base64.b64encode(json.dumps(fields["manifest"]).encode()).decode(), "overwrite":True})
    assert native_call("http", COMMAND_EVENT, {"appId":identifier,"event":"example.count"})["state"] == {"count":1}
    control(identifier, "disable")
    native_call("http", COMMAND_INSTALL, fields)
    assert not native_call("http", COMMAND_READ, {"appId":identifier})["enabled"]
    native_call("http", COMMAND_EVENT, {"appId":identifier,"event":"example.count"}, error="custom_app_disabled")
    control(identifier, "enable")
    assert native_call("http", COMMAND_READ, {"appId":identifier})["enabled"]


def test_custom_apps_native_dikciz_menu_route_and_open_restarts(websocket_control, app_factory):
    identifier, _ = app_factory()
    control(identifier, "disable")
    control(identifier, "open")
    wait_for_physical_text_bounds("Custom app fixture")
    assert native_call("http", COMMAND_READ, {"appId":identifier})["enabled"]
    restore_system_home()
    config = AUTOMATION.request(websocket_control, "configGet")["config"]
    home = config["launcher"]["home"]
    home["selectedPageId"] = next((page["id"] for page in home["pages"] if page["title"] == "Device"), home["selectedPageId"])
    AUTOMATION.request(websocket_control, "configReplace", config=config)
    tap(websocket_control, "launcher:controls")
    wait_for_snapshot_node(websocket_control, "launcher:controls:apps")
    wait_for_physical_text_bounds("Apps")
    target = ARTIFACT_DIRECTORY / "open-apps"
    target.mkdir(exist_ok=True)
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", target / "launcher-menu.png")
    tap(websocket_control, "launcher:controls:apps")
    wait_for_snapshot_node(websocket_control, "custom-app:new")
    wait_for_physical_text_bounds("Dikciz apps")
    wait_for_physical_text_bounds("Apps share the Android access granted to Dikciz. Open, edit or stop an app here.")
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", target / "apps-manager.png")
    restore_system_home()
