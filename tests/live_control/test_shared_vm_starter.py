"""Bundled first-use home, saved notes, optional data and disabled automation."""

import base64

from . import *


STARTER_WIDGET_IDS = ["time-weather", "agenda"]
STARTER_SCRIPT_IDS = {"low-battery", "charge-ready", "screen-lock"}
NOTE_TEXT = "remember_milk"


def starter_reset(connection):
    return AUTOMATION.require_configuration(
        AUTOMATION.request(connection, AUTOMATION.TYPE_RESET),
    )


def starter_replace(connection, configured):
    return AUTOMATION.require_configuration(
        AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured),
    )


def starter_wait_state(connection, widget_id, key, expected):
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    observed = None
    while time.monotonic() < deadline:
        configured = AUTOMATION.require_configuration(
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
        )
        widget = configuration_widget(configured, widget_id)
        observed = widget.get("itemStates", {}) if key == "quick-note" else widget.get("state", {})
        if observed.get(key) == expected:
            return observed
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"{widget_id}.{key}: expected {expected!r}, got {observed!r}")


def test_targeted_run_cleanup_restores_the_bundled_starter_home(websocket_control):
    fixture = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert FIXTURE_WIDGET_IDS[0] in [
        widget["id"] for widget in configuration_page(fixture, FIXTURE_HOME_PAGE_ID)["widgets"]
    ]
    restored = starter_reset(websocket_control)
    home = restored["launcher"]["home"]
    assert [page["id"] for page in home["pages"]] == ["home", "tools"]
    assert [widget["id"] for widget in configuration_page(restored, "home")["widgets"]] == STARTER_WIDGET_IDS
    restore_system_home()
    wait_for_physical_description_bounds(STARTER_HOME_WIDGET_TITLE)
    for title in FIXTURE_WIDGET_TITLES.values():
        assert title not in physical_visible_texts()


def test_bundled_starter_scripts_are_quiet_and_optional_notes_persist(websocket_control):
    restored = starter_reset(websocket_control)
    assert {script["id"] for script in restored["scripts"]} == STARTER_SCRIPT_IDS
    assert all(script["enabled"] is False for script in restored["scripts"])
    assert all(entry["enabled"] for entry in restored["automation"]["scripts"])
    source = Path("app/src/dikciz/assets/widget-library/quick-note")
    configuration_page(restored, "home")["widgets"] = [{
        "id":"optional-note", "type":"html", "title":"Optional quick note", "enabled":True,
        "html":(source / "index.html").read_text(), "css":(source / "style.css").read_text(),
        "javascript":(source / "script.js").read_text(), "state":{},
        "cell":{"column":0,"row":0,"columnSpan":8,"rowSpan":6},
    }]
    starter_replace(websocket_control, restored)
    restore_system_home()
    wait_for_physical_text_bounds("Quick note")
    note_bounds = physical_bounds_from_nodes(list(reversed(physical_nodes())), lambda node: node.get("class") == "android.widget.EditText" and node.get("hint") == "Quick note")
    direct_enter_text(note_bounds, NOTE_TEXT)
    direct_hide_keyboard()
    direct_tap(wait_for_physical_button_bounds("Save note"))
    starter_wait_state(websocket_control, "optional-note", "quick-note", {"text": NOTE_TEXT})
    wait_for_physical_text_bounds("Note saved on this phone.")
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "starter-home.png")
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SELECT_PAGE, pageId="tools")
    wait_for_physical_text_bounds("Device status")
    device_state = AUTOMATION.request(websocket_control, "deviceState")
    assert isinstance(device_state["internetValidated"], bool)
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SELECT_PAGE, pageId="home")
    wait_for_physical_text_bounds(NOTE_TEXT)


WEATHER_FIXTURE = {
    "current": {"temperature_2m": 18, "apparent_temperature": 17, "wind_speed_10m": 8},
    "daily": {"temperature_2m_max": [21], "temperature_2m_min": [12], "precipitation_probability_max": [25]},
}
WEATHER_CITY = {"name": "Fixture City", "latitude": 50, "longitude": 10}


@pytest.fixture
def weather_render_observer():
    with connected_websocket_control() as observer:
        yield observer


def wait_for_weather_render(observer, expected_status, expected_temperature, scenario):
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        event = AUTOMATION.receive_event(observer,"automation_event")["fields"]["event"]
        if event.get("type") != "fixture.weather.render":
            continue
        payload = event["payload"]
        if payload["status"] == expected_status and payload["temperature"] == expected_temperature:
            return payload
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / ("weather-failure-" + scenario + ".png"))
    raise AssertionError("Weather DOM did not report expected status and temperature")


@pytest.mark.parametrize("scenario", ["success", "air-offline", "invalid", "offline", "empty", "oversized", "coordinates"])
def test_bundled_starter_weather_handles_provider_results(websocket_control, weather_render_observer, scenario):
    configured = starter_reset(websocket_control)
    weather = configuration_widget(configured, "time-weather")
    weather["state"] = {
        "city":{"name":"Previous city","latitude":40,"longitude":20},
        "weather":{"fetchedAt":int(time.time()*1000)},
    }
    # Only the external HTTP boundary is substituted. Rendering, the bridge,
    # state storage and page navigation use the installed launcher.
    mock = """
window.fetch = async url => {
    if (url.includes("geocoding-api")) {
        if (SCENARIO === "offline") throw new TypeError("offline");
        if (SCENARIO === "oversized") return new Response("x".repeat(70000));
        return new Response(JSON.stringify({results: SCENARIO === "empty" ? [] : [SCENARIO === "coordinates" ? {...CITY, latitude:999} : CITY]}));
    }
    if (url.includes("air-quality-api")) {
        if (SCENARIO === "air-offline") throw new TypeError("offline");
        return new Response(JSON.stringify({current:{european_aqi:14}}));
    }
    if (url.includes("api.open-meteo.com/v1/forecast")) {
        return new Response(JSON.stringify(SCENARIO === "invalid" ? {current:{temperature_2m:null}} : WEATHER));
    }
    throw new Error("Unexpected outbound request");
};
""".replace("SCENARIO", json.dumps(scenario)).replace("CITY", json.dumps(WEATHER_CITY)).replace("WEATHER", json.dumps(WEATHER_FIXTURE))
    exercise = """
window.addEventListener("dikciz-ready", () => {
    const sent = new Set();
    const publish = () => requestAnimationFrame(() => {
        const status = document.getElementById("status").textContent;
        const temperature = document.getElementById("temperature").textContent;
        if (!["Saved latest readings", "Air quality unavailable", "Weather unavailable", "City search unavailable", "No matching cities"].some(prefix => status.startsWith(prefix))) return;
        const key = status + "|" + temperature;
        if (sent.has(key)) return;
        sent.add(key);
        dikciz.emitEvent("fixture.weather.render", {status, temperature})
            .catch(error => console.error("weather_render_report_failed",error.name));
    });
    const observer = new MutationObserver(publish);
    for (const id of ["status","temperature"]) {
        observer.observe(document.getElementById(id), {childList:true,characterData:true,subtree:true});
    }
    const city = document.getElementById("city");
    city.value = "fixture";
    const submit = setInterval(() => {
        if (document.getElementById("find").disabled) return;
        clearInterval(submit);
        document.getElementById("city-search").requestSubmit();
    }, 50);
    setTimeout(() => clearInterval(submit), 15000);
    const interval = setInterval(() => {
        const choice = document.querySelector("#choices button");
        if (!choice || choice.disabled) return;
        clearInterval(interval);
        choice.click();
    }, 50);
    setTimeout(() => clearInterval(interval), 5000);
});
"""
    configured["limits"]["maxTextCharacters"] = 65536
    weather["javascript"] = mock + weather["javascript"] + exercise
    configured["launcher"]["home"]["selectedPageId"] = "home"
    configured["launcher"]["home"]["homePageId"] = "home"
    restore_system_home()
    starter_replace(websocket_control, configured)
    if scenario in ("offline", "oversized", "empty", "coordinates"):
        expected_status = (
            "City search unavailable. Check your connection and retry."
            if scenario in ("offline", "oversized")
            else "No matching cities. Try another name."
        )
        wait_for_weather_render(weather_render_observer, expected_status, "Unavailable", scenario)
        state = configuration_widget(AUTOMATION.require_configuration(AUTOMATION.request(websocket_control,"configGet")),"time-weather")["state"]
        assert state["city"] == weather["state"]["city"],state
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / ("starter-insights-" + scenario + ".png"))
        return
    expected = "Air quality unavailable" if scenario == "air-offline" else "Weather unavailable" if scenario == "invalid" else None
    state = starter_wait_state(websocket_control, "time-weather", "city", WEATHER_CITY)
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        state = configuration_widget(AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET)), "time-weather")["state"]
        weather_ready = bool(state.get("weather", {}).get("fetchedAt"))
        air_ready = bool(state.get("air", {}).get("fetchedAt"))
        if (
            (scenario == "success" and weather_ready and air_ready)
            or (scenario == "air-offline" and weather_ready)
            or (scenario == "invalid" and air_ready)
        ):
            break
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    assert (state["weather"] == {}) == (scenario == "invalid"), state
    assert (state["air"] == {}) == (scenario == "air-offline"), state
    if scenario != "invalid":
        assert state["weather"]["temperature"] == 18
    if scenario != "air-offline":
        assert state["air"]["aqi"] == 14
    expected_status = (
        expected + ". Saved readings are shown when available. Try Refresh weather."
        if expected else "Saved latest readings. Refresh when needed."
    )
    wait_for_weather_render(weather_render_observer, expected_status, "Unavailable" if scenario == "invalid" else "18°C", scenario)
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / ("starter-insights-" + scenario + ".png"))


@pytest.mark.parametrize(
    ("script_id", "charging_status", "trigger_level", "reset_level"),
    [("low-battery", 3, 20, 40), ("charge-ready", 2, 80, 60)],
)
def test_bundled_starter_battery_utilities_are_opt_in_and_suppress_repeats(
    websocket_control, script_id, charging_status, trigger_level, reset_level,
):
    configured = starter_reset(websocket_control)
    access = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_STATUS)["androidAccess"]
    had_permission = access["notificationPosting"]
    if not had_permission:
        device_command("pm grant " + DIKCIZ_DEBUG_PACKAGE + " android.permission.POST_NOTIFICATIONS")
    try:
        device_command("dumpsys battery set status " + str(charging_status))
        device_command("dumpsys battery set level " + str(reset_level))
        script = next(script for script in configured["scripts"] if script["id"] == script_id)
        script["enabled"] = True
        starter_replace(websocket_control, configured)
        wait_for_automation_script_state(websocket_control, script_id, "alerted", False)
        device_command("dumpsys battery set level " + str(trigger_level))
        wait_for_automation_script_state(websocket_control, script_id, "alerted", True)
        device_command("dumpsys battery set level " + str(trigger_level - 1 if script_id == "low-battery" else trigger_level + 1))
        deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
        observed = {}
        while time.monotonic() < deadline:
            record = AUTOMATION.request(
                websocket_control, "fileRead",
                path="/sdcard/Dikciz/scripts/" + script_id + "/run-status.json",
            )
            observed = json.loads(base64.b64decode(record["dataBase64"]))
            if observed.get("status") == "No reminder needed":
                break
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
        assert observed["status"] == "No reminder needed", observed
        assert observed["outcome"] == "completed", observed
        device_command("dumpsys battery set level " + str(reset_level))
        wait_for_automation_script_state(websocket_control, script_id, "alerted", False)
    finally:
        device_command("dumpsys battery reset")
        if not had_permission:
            device_command("pm revoke " + DIKCIZ_DEBUG_PACKAGE + " android.permission.POST_NOTIFICATIONS")


def test_bundled_starter_manual_lock_reports_missing_access_without_locking(websocket_control):
    configured = starter_reset(websocket_control)
    access = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_STATUS)["androidAccess"]
    assert access["deviceAdminActive"] is False
    next(script for script in configured["scripts"] if script["id"] == "screen-lock")["enabled"] = True
    starter_replace(websocket_control, configured)
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_TRIGGER, scriptId="screen-lock")
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    observed = {}
    while time.monotonic() < deadline:
        try:
            record = AUTOMATION.request(websocket_control, "fileRead", path="/sdcard/Dikciz/scripts/screen-lock/run-status.json")
        except AUTOMATION.WebSocketProtocolError as error:
            if "file_not_found" not in str(error):
                raise
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
            continue
        observed = json.loads(base64.b64decode(record["dataBase64"]))
        if observed.get("status") == "Enable Device Administration in Automation settings first":
            break
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    assert observed["outcome"] == "completed", observed
    assert observed["status"] == "Enable Device Administration in Automation settings first", observed
    assert AUTOMATION.request(websocket_control, "deviceState")["screenInteractive"] is True
