"""Bundled Goblin rules and complete WebView through the real Android runtime."""

from . import *
import logging
from .test_shared_vm_starter import starter_reset
from .test_native_data import grant_permissions
from .test_html_widget_library import open_html_package_picker, package_picker_entry_semantic_id

EVENT = "fixture.goblin.sample"
SCRIPT_IDS = {"goblin-london", "goblin-heat", "goblin-speed"}


def emit(connection, kind, payload, case):
    AUTOMATION.request(connection, "automationDispatch", sourceWidgetAddress="1H1V-time-weather", actions=[{
        "type":"emitEvent", "event":EVENT, "payload":{"kind":kind,"sample":payload,"case":case},
    }])
    configuration = wait_for_automation_script_state(connection, AUTOMATION_LIVE_SCRIPT_ID, "sampleCase", case)
    return next(script["state"] for script in configuration["scripts"] if script["id"] == AUTOMATION_LIVE_SCRIPT_ID)


def install_rule(connection, kind):
    configuration = starter_reset(connection)
    source = next(script["source"] for script in configuration["scripts"] if script["id"] == "goblin-"+kind)
    source += '''
local evaluate = on_event
function on_event(event)
  local outcome = evaluate({type = event.payload.kind, timestampMilliseconds = event.timestampMilliseconds, payload = event.payload.sample})
  outcome.actions = outcome.actions or {}
  table.insert(outcome.actions, {type = "patchState", values = {sampleCase = event.payload.case, observedStatus = outcome.status}})
  return outcome
end
'''
    configured = automation_configuration(configuration, source, {}, ["manualTrigger"], ["patchState"], [{"event":EVENT,"minimumIntervalMilliseconds":0}])
    AUTOMATION.request(connection, "configReplace", config=configured)
    time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
    for attempt in range(3):
        try:
            emit(connection,"fixture.ready",{},"ready-"+str(time.monotonic_ns()))
            break
        except AssertionError:
            if attempt == 2:
                raise
            logging.getLogger(__name__).debug("goblin_fixture_waiting_for_event_subscriber")


@pytest.mark.parametrize("kind", ["london", "speed", "heat"])
def test_goblin_starter_lua_thresholds_rearm_and_repeat_suppression(websocket_control, kind):
    install_rule(websocket_control, kind)
    now = int(time.time()*1000)
    if kind == "london":
        event_kind, high, low = "location", {"latitude":51.5074,"longitude":-0.1278,"accuracy":10}, {"latitude":50,"longitude":0,"accuracy":10}
        invalid = [{**high,"latitude":91}, {**high,"accuracy":501}, {**high,"longitude":"bad"}]
        boundary = {**high,"latitude":51.5074+22/111.2}
    elif kind == "speed":
        event_kind, high, low = "location", {"speed":81/3.6,"accuracy":10}, {"speed":64/3.6,"accuracy":10}
        invalid = [{**high,"speed":-1}, {**high,"speed":121}, {**high,"accuracy":101}, {**high,"speed":"fast"}]
        boundary = {**high,"speed":80/3.6}
    else:
        event_kind, high, low = "goblin.weather", {"temperatureC":35.1,"observedAtMilliseconds":now}, {"temperatureC":31.9,"observedAtMilliseconds":now}
        invalid = [{**high,"temperatureC":None}, {**high,"temperatureC":100}, {**high,"observedAtMilliseconds":now-3600001}, {**high,"observedAtMilliseconds":now+120000}]
        boundary = {**high,"temperatureC":35}
    for index, payload in enumerate(invalid+[boundary]):
        state = emit(websocket_control, event_kind, payload, "invalid-"+str(index))
        assert "lastSpokenMilliseconds" not in state, state
    first = emit(websocket_control, event_kind, high, "first")
    assert first["observedStatus"].startswith("Spoken:"), first
    assert first["alerted"] is True and first["lastSpokenMilliseconds"] > 0
    duplicate = emit(websocket_control, event_kind, high, "repeat")
    assert duplicate["observedStatus"].startswith("Already announced"), duplicate
    assert duplicate["lastSpokenMilliseconds"] == first["lastSpokenMilliseconds"]
    reset = emit(websocket_control, event_kind, low, "reset")
    assert reset["alerted"] is False
    cooldown = emit(websocket_control, event_kind, high, "cooldown")
    assert cooldown["observedStatus"] == "Speech cooldown active", cooldown
    assert cooldown["lastSpokenMilliseconds"] == first["lastSpokenMilliseconds"]


def test_goblin_starter_defaults_picker_and_local_effects(websocket_control):
    configuration = starter_reset(websocket_control)
    assert [page["id"] for page in configuration["launcher"]["home"]["pages"]] == ["home","tools","device","goblin"]
    assert SCRIPT_IDS <= {script["id"] for script in configuration["scripts"]}
    assert all(not script["enabled"] for script in configuration["scripts"])
    AUTOMATION.request(websocket_control, "selectPage", pageId="goblin")
    wait_for_physical_text_bounds("Goblin control")
    direct_tap(wait_for_physical_button_bounds("Alien transmission"))
    wait_for_physical_text_bounds("Alien transmission complete.")
    direct_tap(wait_for_physical_button_bounds("Alien transmission"))
    direct_tap(wait_for_physical_button_bounds("Stop this widget"))
    wait_for_physical_text_bounds("Stopped this widget's sounds and lights.")
    direct_tap(wait_for_physical_button_bounds("Say the thing"))
    wait_for_physical_text_bounds("Spoken. Your phone has opinions.")
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY/"screenshot.png", ARTIFACT_DIRECTORY/"goblin-starter.png")
    AUTOMATION.request(websocket_control,"selectPage",pageId="home")
    snapshot = open_html_package_picker(websocket_control, "goblin-console")
    AUTOMATION.require_node(snapshot, package_picker_entry_semantic_id("goblin-console"))


@pytest.mark.parametrize("scenario", ["success","stale","invalid","offline","oversized"])
def test_goblin_starter_weather_boundary(websocket_control, scenario):
    configuration = starter_reset(websocket_control)
    grant_permissions(["android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"])
    run_device_operation("emulator-geo-fix", EMULATOR_GEO_LATITUDE="51.5074", EMULATOR_GEO_LONGITUDE="-0.1278")
    widget = configuration_widget(configuration,"goblin-console")
    fixture = {"current":{"temperature_2m":36,"time":int(time.time())}}
    if scenario == "stale": fixture["current"]["time"] -= 7200
    if scenario == "invalid": fixture["current"]["temperature_2m"] = None
    mock = "window.fetch = async () => {" + (
        "throw new TypeError('offline');" if scenario == "offline" else
        "return new Response('x'.repeat(70000));" if scenario == "oversized" else
        "return new Response("+json.dumps(json.dumps(fixture))+");"
    ) + "};\n"
    widget["javascript"] = mock + widget["javascript"]
    configuration["launcher"]["home"]["selectedPageId"] = "goblin"
    AUTOMATION.request(websocket_control,"configReplace",config=configuration)
    direct_tap(wait_for_physical_button_bounds("Check outside"))
    if scenario == "success":
        wait_for_physical_text_bounds("36.0°C")
        deadline=time.monotonic()+20
        while time.monotonic()<deadline:
            saved=AUTOMATION.require_configuration(AUTOMATION.request(websocket_control,"configGet"))
            state=configuration_widget(saved,"goblin-console")["state"]
            if state.get("temperatureC")==36: break
            time.sleep(.1)
        assert state["temperatureC"]==36
    else:
        expected = {"stale":"missing, invalid or stale", "invalid":"missing, invalid or stale", "offline":"offline", "oversized":"response too large"}[scenario]
        deadline=time.monotonic()+25
        while time.monotonic()<deadline:
            text=physical_visible_texts()
            if any(value.startswith("Weather failed:") and expected in value for value in text): break
            time.sleep(.2)
        assert any(value.startswith("Weather failed:") and expected in value for value in text), text
        saved=AUTOMATION.require_configuration(AUTOMATION.request(websocket_control,"configGet"))
        assert "temperatureC" not in configuration_widget(saved,"goblin-console")["state"]


def test_goblin_starter_external_state_refreshes_without_network(websocket_control):
    starter_reset(websocket_control)
    AUTOMATION.request(websocket_control,"selectPage",pageId="goblin")
    wait_for_physical_text_bounds("Goblin control")
    AUTOMATION.request(websocket_control,"automationDispatch",sourceWidgetAddress="4H1V-goblin-console",actions=[{
        "type":"patchWidget","widgetAddress":"4H1V-goblin-console","values":{"state":{"temperatureC":19.5,"fetchedAt":int(time.time()*1000),"watchWeather":False}},
    }])
    wait_for_physical_text_bounds("19.5°C")


def test_goblin_starter_native_gps_delivery(websocket_control):
    configuration = starter_reset(websocket_control)
    grant_permissions(["android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"])
    device_command("cmd location set-location-enabled true")
    next(script for script in configuration["scripts"] if script["id"] == "goblin-london")["enabled"] = True
    AUTOMATION.request(websocket_control,"configReplace",config=configuration)
    time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
    emulator_geo_fix(-0.1278,51.5074)
    observed = wait_for_automation_script_state(websocket_control,"goblin-london","alerted",True,timeout_seconds=45)
    state = next(script["state"] for script in observed["scripts"] if script["id"] == "goblin-london")
    assert state["lastSpokenMilliseconds"] > 0


def wait_for_goblin_torch_calls(connection, predicate):
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    calls = []
    while time.monotonic() < deadline:
        current = AUTOMATION.require_configuration(AUTOMATION.request(connection, "configGet"))
        calls = configuration_widget(current, "goblin-console")["state"].get("probeTorchCalls", [])
        if predicate(calls):
            return calls
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"Torch trace did not settle: {calls!r}")


def test_goblin_starter_torch_pulses_are_finite_and_stop_on_hide(websocket_control):
    configured = starter_reset(websocket_control)
    widget = configuration_widget(configured,"goblin-console")
    widget["javascript"] = '''
window.addEventListener("dikciz-ready", () => {
    window.dikciz = {...window.dikciz};
    const original = dikciz.command, calls = [];
    dikciz.command = async (type, fields, options) => {
        if (type !== "torchControl") return original(type, fields, options);
        calls.push({...fields});
        selfWidget.patchState({...selfWidget.state, probeTorchCalls:calls.map(call => ({...call}))}).catch(error => {
            document.getElementById("status").textContent = "Torch probe failed: " + error.message;
        });
        return {state:"submitted"};
    };
});
''' + widget["javascript"]
    configured["launcher"]["home"]["selectedPageId"] = "goblin"
    AUTOMATION.request(websocket_control,"configReplace",config=configured)
    direct_tap(wait_for_physical_button_bounds("Flash attack"))
    wait_for_physical_text_bounds("Flash attack finished.")
    calls = wait_for_goblin_torch_calls(websocket_control, lambda calls: len(calls) >= 9 and calls[-1]["action"] == "off")
    on=[call for call in calls if call["action"]=="on"]
    assert len(on)==8 and all(call["durationMilliseconds"]==250 for call in on)
    direct_tap(wait_for_physical_button_bounds("Flash attack"))
    wait_for_goblin_torch_calls(websocket_control, lambda calls: sum(call["action"] == "on" for call in calls) > 8)
    AUTOMATION.request(websocket_control,"selectPage",pageId="home")
    calls = wait_for_goblin_torch_calls(websocket_control, lambda calls: len(calls) > 9 and calls[-1]["action"] == "off")
    assert calls[-1]["action"]=="off"
    assert sum(call["action"] == "on" for call in calls) < 16
    count=len(calls)
    time.sleep(1)
    current=AUTOMATION.require_configuration(AUTOMATION.request(websocket_control,"configGet"))
    assert len(configuration_widget(current,"goblin-console")["state"]["probeTorchCalls"])==count
