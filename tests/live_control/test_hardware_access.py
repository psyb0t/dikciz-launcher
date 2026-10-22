"""Real hardware samples and explicit capture outcomes through shared entrypoints."""

import base64
import math

from . import *
from .test_native_data import native_call, PROTOCOLS, grant_permissions
from .test_shared_vm_starter import starter_reset, starter_replace, starter_wait_state, WEATHER_FIXTURE


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_shared_hardware_access(websocket_control, protocol):
    AUTOMATION.verify_control_status(websocket_control)
    items = []
    offset = 0
    while True:
        page = native_call(protocol, "sensorReadings", {"limit":5, "offset":offset, "timeoutMilliseconds":100})
        assert page["offset"] == offset and page["limit"] == 5
        assert len(page["items"]) <= 5
        items.extend(page["items"])
        offset += len(page["items"])
        if not page["hasMore"]:
            assert offset == page["total"]
            break
    assert items, "The Android Lab emulator must expose virtual sensors"
    assert len({sensor["sensorId"] for sensor in items}) == len(items)
    for sensor in items:
        assert isinstance(sensor["available"], bool) and isinstance(sensor["dynamic"], bool)
        assert sensor["values"] or sensor["reason"]
        assert all(value is None or math.isfinite(value) for value in sensor["values"])
    assert native_call(protocol, "sensorReadings", {"offset":offset, "timeoutMilliseconds":1})["items"] == []
    assert native_call(protocol, "sensorReadings", {"offset":1000000, "timeoutMilliseconds":1})["items"] == []
    accelerometer = next(sensor for sensor in items if sensor["type"] == 1)
    try:
        emulator_sensor_set("acceleration", "1:2:3")
        first = native_call(protocol, "sensorReadings", {"sensorId":accelerometer["sensorId"], "timeoutMilliseconds":1000})["items"][0]
        emulator_sensor_set("acceleration", "4:5:6")
        second = native_call(protocol, "sensorReadings", {"sensorId":accelerometer["sensorId"], "timeoutMilliseconds":1000})["items"][0]
        assert first["available"] and second["available"], (first,second)
        assert second["timestampNanoseconds"] > first["timestampNanoseconds"]
        assert first["values"] != second["values"], (first,second)
    finally:
        emulator_sensor_set("acceleration", "0:9.81:0")
    for command, fields in [
        ("sensorReadings", {"timeoutMilliseconds":0}),
        ("sensorReadings", {"sensorId":-1}),
        ("sensorReadings", {"limit":201}),
        ("cameraCapture", {"maxDimension":319}),
        ("microphoneRecord", {"durationMilliseconds":999}),
        ("speechSpeak", {"text":""}),
        ("callAudio", {"action":"arbitrary"}),
        ("cameraList", {"extra":True}),
    ]:
        native_call(protocol,command,fields,error="validation_failed")
    native_call(protocol,"sensorReadings",{"sensorId":2147483647},error="native_record_not_found")
    call = native_call(protocol,"callAudio",{"action":"status"})
    assert not call["captureSupported"] and not call["injectionSupported"]
    native_call(protocol,"callAudio",{"action":"record"},error="call_audio_unsupported")
    native_call(protocol,"callAudio",{"action":"speak","text":"Test"},error="call_audio_unsupported")
    cameras = native_call(protocol,"cameraList")["items"]
    assert cameras and all(camera["cameraId"] for camera in cameras)
    for permission in ("android.permission.CAMERA","android.permission.RECORD_AUDIO"):
        device_command("pm revoke " + DIKCIZ_DEBUG_PACKAGE + " " + permission)
    restore_system_home()
    with connected_websocket_control() as reconnected:
        AUTOMATION.request(reconnected,"snapshot")
    for command in ("cameraCapture", "microphoneRecord"):
        error = native_call(protocol,command,error="native_permission_cameraAndMicrophone")
        assert error["permissionRequest"]["requirementId"] == "cameraAndMicrophone"
    grant_permissions(("android.permission.CAMERA","android.permission.RECORD_AUDIO"))
    restore_system_home()
    photo = native_call(protocol,"cameraCapture",{"cameraId":cameras[0]["cameraId"],"maxDimension":1280})
    image = native_call("http","fileRead",{"path":photo["path"],"limit":1024})
    assert photo["state"] == "saved" and photo["bytes"] > 0
    assert base64.b64decode(image["dataBase64"]).startswith(bytes.fromhex("ffd8"))
    audio = native_call(protocol,"microphoneRecord",{"durationMilliseconds":1000})
    recording = native_call("http","fileRead",{"path":audio["path"],"limit":128})
    assert audio["bytes"] > 0 and audio["callAudio"] is False
    assert b"ftyp" in base64.b64decode(recording["dataBase64"])[:32]


def test_hardware_sensors_page_and_weather_location(websocket_control):
    configured = starter_reset(websocket_control)
    assert [page["id"] for page in configured["launcher"]["home"]["pages"]] == ["home","insights","sensors"]
    time.sleep(1)
    assert not any(node["semanticId"]=="permission:allow" for node in AUTOMATION.request(websocket_control,"snapshot")["nodes"])
    sensor = configuration_widget(configured,"live-sensors")
    sensor["javascript"] += """
window.addEventListener("dikciz-ready", () => {
    let count = 0;
    const observer = new MutationObserver(() => {
        if (!document.getElementById("status").textContent.includes("with readings")) return;
        selfWidget.patchState({sampleCount:++count, nativeVisible:selfWidget.visible})
            .catch(error => console.error("sensor_probe_failed",error.name));
    });
    observer.observe(document.getElementById("status"), {childList:true,characterData:true,subtree:true});
});
"""
    starter_replace(websocket_control,configured)
    AUTOMATION.request(websocket_control,"selectPage",pageId="sensors")
    wait_for_physical_text_bounds("Sensors")
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        if any("with readings" in value for value in physical_visible_texts()):
            break
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    else:
        raise AssertionError("Sensor page did not render real hardware readings")
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "live-sensors.png")
    AUTOMATION.request(websocket_control,"selectPage",pageId="home")
    time.sleep(1)
    def sensor_state():
        current = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control,"configGet"))
        return configuration_widget(current,"live-sensors")["state"]
    hidden = sensor_state()
    assert hidden["sampleCount"] > 0
    time.sleep(3)
    assert sensor_state() == hidden, "Hidden sensors continued sampling"
    AUTOMATION.request(websocket_control,"selectPage",pageId="sensors")
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        if sensor_state().get("sampleCount",0)>hidden["sampleCount"]: break
        time.sleep(.3)
    else: raise AssertionError("Sensor sampling did not resume")
    weather = configuration_widget(configured,"weather-air")
    # Substitute only the weather provider. Device location uses the real bridge.
    weather["javascript"] = "window.fetch = async url => new Response(JSON.stringify(String(url).includes('air-quality') ? {current:{european_aqi:14}} : " + json.dumps(WEATHER_FIXTURE) + "));\n" + weather["javascript"]
    grant_permissions(("android.permission.ACCESS_COARSE_LOCATION","android.permission.ACCESS_FINE_LOCATION"))
    emulator_geo_fix(10,50)
    fix = native_call("http","locationGet",{"maxAgeMilliseconds":0,"timeoutMilliseconds":10000})
    assert fix["available"] and fix["source"]=="current_fix",fix
    assert -90 <= fix["latitude"] <= 90 and -180 <= fix["longitude"] <= 180,fix
    configured["launcher"]["home"]["selectedPageId"]="insights"
    starter_replace(websocket_control,configured)
    deadline=time.monotonic()+30
    while time.monotonic()<deadline:
        current=AUTOMATION.require_configuration(AUTOMATION.request(websocket_control,"configGet"))
        state=configuration_widget(current,"weather-air")["state"]
        if state.get("weather",{}).get("fetchedAt"):
            break
        time.sleep(.3)
    else:
        raise AssertionError("Weather did not automatically use permitted location: " + str(state))
    assert state["city"]["source"]=="device"
    assert abs(state["city"]["latitude"]-fix["latitude"])<.01 and abs(state["city"]["longitude"]-fix["longitude"])<.01
    assert state["weather"]["temperature"]==18
    wait_for_physical_text_bounds("18°C")
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "weather-device-location.png")


def test_hardware_lua_uses_shared_commands(websocket_control):
    original = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control,"configGet"))
    source = """function on_event(event)
  if event.type ~= "fixture.hardware" then return end
  local sensors = context.requestJson({type="sensorReadings",limit=1,timeoutMilliseconds=100})
  local cameras = context.requestJson({type="cameraList"})
  return {actions={{type="patchState",values={sensors=sensors,cameras=cameras}}}}
end"""
    configured = automation_configuration(original,source,{},["manualTrigger"],["patchState"],[{"event":"fixture.hardware","minimumIntervalMilliseconds":0}])
    AUTOMATION.request(websocket_control,"configReplace",config=configured)
    time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
    AUTOMATION.request(websocket_control,"automationDispatch",sourceWidgetAddress="1H1V-welcome",actions=[{"type":"emitEvent","event":"fixture.hardware","payload":{}}])
    deadline=time.monotonic()+20
    while time.monotonic()<deadline:
        current=AUTOMATION.require_configuration(AUTOMATION.request(websocket_control,"configGet"))
        script=next(item for item in current["scripts"] if item["id"]==AUTOMATION_LIVE_SCRIPT_ID)
        if "sensors" in script["state"]:
            for key in ("sensors","cameras"):
                response=json.loads(script["state"][key])
                assert response["type"]=="result" and response["result"]["items"],response
            return
        time.sleep(.2)
    raise AssertionError("Lua did not receive native hardware results")


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_hardware_device_speech(websocket_control, protocol):
    restore_system_home()
    result = native_call(protocol,"speechSpeak",{"text":"Dikciz speech test","timeoutMilliseconds":10000})
    assert result == {"state":"spoken","route":"device_output","injectedIntoCall":False}


def test_hardware_capture_lifecycle(websocket_control):
    from concurrent.futures import ThreadPoolExecutor
    grant_permissions(("android.permission.CAMERA","android.permission.RECORD_AUDIO"))
    restore_system_home()
    native_call("http","cameraCapture",{"timeoutMilliseconds":1},error="capture_timeout")
    deadline=time.monotonic()+10
    while True:
        try:
            photo=native_call("http","cameraCapture",{"timeoutMilliseconds":10000})
            break
        except AssertionError as error:
            if "hardware_busy" not in str(error) or time.monotonic()>=deadline: raise
            time.sleep(.1)
    assert photo["state"]=="saved" and photo["bytes"]>0
    with ThreadPoolExecutor(max_workers=1) as executor:
        first=executor.submit(native_call,"http","microphoneRecord",{"durationMilliseconds":3000})
        time.sleep(1)
        native_call("mcp","microphoneRecord",{"durationMilliseconds":1000},error="hardware_busy")
        assert first.result()["state"]=="saved"
    assert native_call("websocket","microphoneRecord",{"durationMilliseconds":1000})["state"]=="saved"
