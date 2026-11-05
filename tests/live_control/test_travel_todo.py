"""The shipped tutorial app, real geofence events and screenshots on the private emulator."""

import base64
import json
import shutil
import time
import pytest
from . import *
from .test_native_data import native_call, grant_permissions, PROTOCOLS
from .test_operations_ux import tap
from .ui import direct_tap, direct_enter_text, direct_hide_keyboard, direct_swipe, physical_nodes, node_bounds, wait_for_physical_text_bounds

APP_ID = "travel-todo"
FENCE_ID = APP_ID + "-nyc"
WAIT_SECONDS = 50


def call(event, payload=None, protocol="http", **options):
    return native_call(protocol, "customAppEvent", {"appId": APP_ID, "event": event, "payload": payload or {}}, **options)


def read():
    return native_call("http", "customAppRead", {"appId": APP_ID})


def fences():
    return native_call("http", "geofencesList", {"limit": 200})["items"]


def capture(name):
    time.sleep(1)
    run_device_operation("screenshot")
    target = ARTIFACT_DIRECTORY / "travel-todo-guide"
    target.mkdir(exist_ok=True)
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", target / name)


def wait_for(predicate):
    deadline = time.monotonic() + WAIT_SECONDS
    while time.monotonic() < deadline:
        value = read()
        if predicate(value):
            return value
        time.sleep(.5)
    raise AssertionError("Travel todo state did not settle: " + json.dumps(read()))


@pytest.fixture
def todo(websocket_control):
    native_call("http", "geofenceDelete", {"id": FENCE_ID}, allowed_errors=("native_record_not_found",))
    native_call("http", "customAppControl", {"appId": APP_ID, "action": "enable", "confirm": True})
    native_call("http", "customAppState", {"appId": APP_ID, "action": "replace", "state": {}})
    call("todo.open")
    yield websocket_control
    call("todo.stop")
    restore_system_home()


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_travel_todo_tasks_validation_and_custom_events(todo, protocol):
    assert not read()["state"]["watching"]
    assert not any(row["id"] == FENCE_ID for row in fences())
    added = call("todo.add", {"title": "Try the local pizza"}, protocol)
    task = added["state"]["tasks"][-1]
    call("todo.toggle", {"id": task["id"]}, protocol)
    assert read()["state"]["tasks"][-1]["done"]
    for title in ("", " " * 4, "x" * 121, 3):
        call("todo.add", {"title": title}, protocol, error="validation_failed")
    call("todo.toggle", {"id": "missing"}, protocol, error="validation_failed")
    native_call(protocol, "automationDispatch", {"sourceWidgetAddress": "1H1V-welcome", "actions": [{"type": "emitEvent", "event": "example.todo.complete", "payload": {"id": "pack"}}]})
    wait_for(lambda app: app["state"]["tasks"][1]["done"])
    call("geofence.enter", {"id": "unrelated"}, protocol)
    assert not read()["state"]["tasks"][0]["done"]


def test_travel_todo_gps_arrival_with_closed_ui_and_persistence(todo):
    grant_permissions(("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION", "android.permission.POST_NOTIFICATIONS"))
    run_device_operation("emulator-geo-fix", EMULATOR_GEO_LATITUDE="51.5074", EMULATOR_GEO_LONGITUDE="-0.1278")
    native_call("http", "customAppControl", {"appId": APP_ID, "action": "open"})
    direct_tap(wait_for_physical_text_bounds("Watch New York"))
    wait_for(lambda app: app["state"].get("watching"))
    capture("todo-watching.png")
    restore_system_home()
    wait_for(lambda app: not app["uiVisible"])
    time.sleep(3)
    assert not read()["state"]["tasks"][0]["done"]
    run_device_operation("emulator-geo-fix", EMULATOR_GEO_LATITUDE="40.7128", EMULATOR_GEO_LONGITUDE="-74.0060")
    arrived = wait_for(lambda app: app["state"]["tasks"][0]["done"])
    assert not arrived["state"]["watching"]
    assert not any(row["id"] == FENCE_ID for row in fences())
    assert not any(item["done"] for item in arrived["state"]["tasks"][1:])
    native_call("http", "customAppControl", {"appId": APP_ID, "action": "open"})
    wait_for_physical_text_bounds("Arrived in New York. Checked automatically by GPS")
    capture("todo-arrived.png")
    saved = native_call("http", "fileRead", {"path": "/sdcard/Dikciz/apps/" + APP_ID + "/state.json"})
    assert json.loads(base64.b64decode(saved["dataBase64"]))["tasks"][0]["done"]
    tap(todo, "custom-app:back")
    wait_for_snapshot_node(todo, "custom-app:new")
    capture("custom-apps.png")
    tap(todo, "custom-app:" + APP_ID + ":edit")
    capture("todo-editor.png")


def test_travel_todo_stop_disable_and_permission_denial(todo):
    grant_permissions(("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION", "android.permission.POST_NOTIFICATIONS"))
    restore_system_home()
    run_device_operation("emulator-geo-fix", EMULATOR_GEO_LATITUDE="51.5074", EMULATOR_GEO_LONGITUDE="-0.1278")
    time.sleep(2)
    call("todo.watch")
    assert any(row["id"] == FENCE_ID for row in fences())
    call("todo.stop")
    assert not any(row["id"] == FENCE_ID for row in fences())
    call("todo.watch")
    native_call("http", "customAppState", {"appId": APP_ID, "action": "replace", "state": {}})
    native_call("http", "customAppControl", {"appId": APP_ID, "action": "disable"})
    assert not any(row["id"] == FENCE_ID for row in fences())
    assert not read()["state"]["watching"]
    native_call("http", "customAppControl", {"appId": APP_ID, "action": "enable", "confirm": True})
    device_command("pm revoke " + DIKCIZ_DEBUG_PACKAGE + " android.permission.ACCESS_FINE_LOCATION")
    device_command("pm revoke " + DIKCIZ_DEBUG_PACKAGE + " android.permission.ACCESS_COARSE_LOCATION")
    try:
        restore_system_home()
        wait_for_restarted_websocket_control().close()
        call("todo.watch", error="native_permission_locationForeground")
        assert not any(row["id"] == FENCE_ID for row in fences())
        assert not read()["state"]["watching"]
    finally:
        grant_permissions(("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"))


def test_travel_todo_manual_ui_and_editor_capture(todo):
    native_call("http", "customAppControl", {"appId": APP_ID, "action": "open"})
    direct_tap(wait_for_physical_text_bounds("Pack my bag"))
    wait_for(lambda app: app["state"]["tasks"][1]["done"])
    web = next(node_bounds(node) for node in physical_nodes() if node.get("class") == "android.webkit.WebView")
    x = (web["left"] + web["right"]) // 2
    direct_swipe((x, web["bottom"] - 30), (x, web["top"] + 30), 500)
    task_input = next(node_bounds(node) for node in physical_nodes() if node.get("class") == "android.widget.EditText")
    direct_enter_text(task_input, "Visit a museum")
    direct_hide_keyboard()
    direct_tap(wait_for_physical_text_bounds("Add"))
    wait_for(lambda app: app["state"]["tasks"][-1]["title"] == "Visit a museum")
    capture("todo-checklist.png")
    tap(todo, "custom-app:back")
    wait_for_snapshot_node(todo, "custom-app:new")
    AUTOMATION.request(todo, "scrollTo", semanticId="custom-app:" + APP_ID + ":edit")
    capture("custom-apps.png")
    tap(todo, "custom-app:" + APP_ID + ":edit")
    capture("todo-editor.png")
