"""Explicit documentation capture with live weather and private emulator fixtures."""

import os
import logging

from . import *
from .test_native_data import native_call, grant_permissions, CALENDAR_PERMISSIONS

pytestmark = pytest.mark.skipif(
    os.environ.get("DIKCIZ_GUIDE_CAPTURE") != "true",
    reason="configured documentation capture is explicit and uses live weather",
)

CAPTURE_PROBE = """
window.addEventListener("dikciz-ready", () => {
    let previous = "";
    function publish() {
        const value = {
            text:document.body.innerText,
            icons:[...document.querySelectorAll("#apps img")].filter(image=>image.complete && image.naturalWidth>0).length,
            events:document.querySelectorAll("#events strong").length,
            points:(document.getElementById("cpu-history")?.getAttribute("points") || "").trim().split(/ +/).filter(Boolean).length,
            historyEnd:Number((document.getElementById("cpu-history")?.getAttribute("points") || "0,0").trim().split(/ +/).at(-1).split(",")[0]),
            overflow:document.documentElement.scrollWidth > innerWidth + 1,
            goblinButtonsWidth:document.getElementById("peen")?.parentElement.getBoundingClientRect().width,
            viewportWidth:innerWidth,
            clipped:[...document.querySelectorAll("#clock,#temperature,#flash,#speak,#record,#calendar")].some(node=>{
                const rect=node.getBoundingClientRect();
                return rect.width>0 && rect.height>0 && (rect.left<0 || rect.right>innerWidth+1 || rect.bottom>innerHeight+1);
            }),
        };
        const encoded = JSON.stringify(value);
        if (encoded === previous) return;
        previous = encoded;
        selfWidget.item("capture").patchState(value).catch(error=>{
            document.body.dataset.captureError=error.code || "failed";
        });
    }
    new MutationObserver(publish).observe(document.body,{subtree:true,childList:true,attributes:true});
    document.addEventListener("load",publish,true);
    publish();
});
"""

GOBLIN_WEATHER_ATTEMPTS = 3
GOBLIN_WEATHER_WAIT_SECONDS = 35
GOBLIN_MINIMUM_BUTTON_WIDTH_FRACTION = 0.75


def capture_wait(connection, identifier, predicate, seconds=90):
    deadline = time.monotonic() + seconds
    value = {}
    while time.monotonic() < deadline:
        config = AUTOMATION.require_configuration(AUTOMATION.request(connection, "configGet"))
        widget = configuration_widget(config, identifier)
        value = {**widget["state"], "capture": widget.get("itemStates", {}).get("capture", {})}
        if predicate(value):
            return value
        time.sleep(1)
    raise AssertionError(f"configured capture did not settle for {identifier}: {value!r}")


def capture_image(name):
    time.sleep(1.5)
    run_device_operation("screenshot")
    target = ARTIFACT_DIRECTORY / "showcase-guide"
    target.mkdir(exist_ok=True)
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", target / name)


def capture_goblin_page(connection):
    AUTOMATION.request(connection, "selectPage", pageId="goblin")
    for attempt in range(GOBLIN_WEATHER_ATTEMPTS):
        direct_tap(wait_for_physical_text_bounds("Check outside"))
        try:
            goblin = capture_wait(connection, "goblin-console", lambda state: isinstance(state.get("temperatureC"), (int, float)), seconds=GOBLIN_WEATHER_WAIT_SECONDS)
            break
        except AssertionError:
            if attempt + 1 == GOBLIN_WEATHER_ATTEMPTS:
                raise
            logging.getLogger(__name__).warning("goblin_live_weather_capture_retry", extra={"attempt":attempt + 1})
    assert goblin["capture"]["overflow"] is False
    assert goblin["capture"]["clipped"] is False
    assert goblin["capture"]["goblinButtonsWidth"] >= goblin["capture"]["viewportWidth"] * GOBLIN_MINIMUM_BUTTON_WIDTH_FRACTION
    capture_image("05-goblin-page.png")


def test_capture_goblin_live_weather_and_controls(websocket_control):
    grant_permissions(["android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"])
    run_device_operation("emulator-geo-fix", EMULATOR_GEO_LATITUDE="51.5074", EMULATOR_GEO_LONGITUDE="-0.1278")
    config = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "reset"))
    configuration_widget(config, "goblin-console")["javascript"] += CAPTURE_PROBE
    AUTOMATION.request(websocket_control, "configReplace", config=config)
    capture_goblin_page(websocket_control)


def test_user_guide_showcase_live_weather_and_all_pages(websocket_control):
    grant_permissions((*CALENDAR_PERMISSIONS, "android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"))
    device_command(f"cmd appops set {DIKCIZ_DEBUG_PACKAGE} GET_USAGE_STATS allow")
    run_device_operation("emulator-geo-fix", EMULATOR_GEO_LATITUDE="51.5074", EMULATOR_GEO_LONGITUDE="-0.1278")
    emulator_calendar_fixture_create()
    identifiers = []
    try:
        device_command("dumpsys battery set level 78")
        calendar = next(row for row in native_call("http", "contentQuery", {"resource":"calendars"})["items"] if row["account_name"] == "android-lab-calendar-fixture")
        window = native_call("http", "contentQuery", {"resource":"calendarInstances", "limit":1})
        for index, title in enumerate(("Coffee and a plan", "Build something useful", "Evening walk")):
            start = window["rangeStartMilliseconds"] + 24 * 60 * 60 * 1000 + (9 + index * 3) * 60 * 60 * 1000
            result = native_call("http", "contentWrite", {"resource":"calendarEvents", "operation":"create",
                "values":{"calendar_id":calendar["_id"],"title":title,"dtstart":start,"dtend":start+30*60*1000,"eventTimezone":"UTC"}})
            identifiers.append(result["results"][0]["id"])
        config = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "reset"))
        for page in config["launcher"]["home"]["pages"]:
            for widget in page["widgets"]:
                if widget["type"] == "html":
                    widget["javascript"] += CAPTURE_PROBE
        weather = configuration_widget(config, "time-weather")
        weather["state"] = {"city":{"name":"London","latitude":51.5074,"longitude":-0.1278,"source":"manual"}}
        AUTOMATION.request(websocket_control, "configReplace", config=config)
        restore_system_home()
        saved = capture_wait(websocket_control, "time-weather", lambda state: isinstance(state.get("weather", {}).get("temperature"), (int,float)) and bool(state.get("capture")))
        assert saved["weather"]["fetchedAt"]
        assert not saved["capture"]["overflow"]
        assert not saved["capture"]["clipped"]
        apps = capture_wait(websocket_control, "most-used-apps", lambda state: state.get("capture", {}).get("icons", 0) >= 4)
        assert not apps["capture"]["clipped"]
        capture_image("01-first-launch.png")

        AUTOMATION.request(websocket_control, "selectPage", pageId="tools")
        agenda = capture_wait(websocket_control, "agenda", lambda state: state.get("capture", {}).get("events", 0) >= 3)
        capture_image("03-tools-layout-review.png")
        assert not agenda["capture"]["clipped"]
        direct_tap(wait_for_physical_description_bounds("Voice notes"))
        direct_tap(wait_for_physical_text_bounds("Record voice note"))
        capture_wait(websocket_control, "voice-notes", lambda state: bool(state.get("sessionId")))
        time.sleep(2)
        direct_tap(wait_for_physical_text_bounds("Stop and save"))
        capture_wait(websocket_control, "voice-notes", lambda state: bool(state.get("recordings")))
        capture_image("03-insights-page.png")

        AUTOMATION.request(websocket_control, "selectPage", pageId="device")
        device = capture_wait(websocket_control, "device-status", lambda state: state.get("capture", {}).get("points", 0) >= 3)
        assert device["capture"]["overflow"] is False
        assert device["capture"]["historyEnd"] == 300
        capture_image("04-device-page.png")

        capture_goblin_page(websocket_control)
        AUTOMATION.request(websocket_control, "selectPage", pageId="home")
    finally:
        device_command("dumpsys battery reset")
        for identifier in identifiers:
            native_call("http", "contentWrite", {"resource":"calendarEvents", "operation":"delete", "id":identifier})
        emulator_calendar_fixture_delete()
        device_command(f"cmd appops set {DIKCIZ_DEBUG_PACKAGE} GET_USAGE_STATS default")
