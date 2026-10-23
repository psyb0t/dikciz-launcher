"""Focused starter documents through real WebViews and Android providers."""

import re
import uuid

from . import *
from .test_native_data import native_call, grant_permissions, CALENDAR_PERMISSIONS, PROTOCOLS

RECORDER_ID = "voice-notes"
RECORDER_PATH = "/sdcard/Dikciz/pages/tools/widgets/voice-notes"


def demo_reset(connection, page="home"):
    configured = AUTOMATION.require_configuration(AUTOMATION.request(connection, "reset"))
    configured["launcher"]["home"]["selectedPageId"] = page
    configured["launcher"]["home"]["homePageId"] = page
    AUTOMATION.request(connection, "configReplace", config=configured)
    restore_system_home()
    return configured


def demo_state(connection, identifier, predicate):
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    state = None
    while time.monotonic() < deadline:
        configured = AUTOMATION.require_configuration(AUTOMATION.request(connection, "configGet"))
        state = configuration_widget(configured, identifier)["state"]
        if predicate(state):
            return state
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"Demo state did not settle: {state!r}")


def test_starter_demo_default_layout_and_independent_removal(websocket_control):
    configured = demo_reset(websocket_control)
    home = configured["launcher"]["home"]
    assert [(page["id"], [widget["id"] for widget in page["widgets"] if widget["type"] == "html"]) for page in home["pages"]] == [
        ("home", ["time-weather", "most-used-apps"]), ("tools", ["agenda", "voice-notes"]), ("device", ["device-status"]),
    ]
    assert all(not script["enabled"] for script in configured["scripts"])
    expected_cells = {"time-weather":(0,2), "most-used-apps":(2,3), "agenda":(0,3), "voice-notes":(3,3), "device-status":(0,6)}
    for page in home["pages"]:
        for widget in page["widgets"]:
            if widget["type"] != "html":
                continue
            row, rows = expected_cells[widget["id"]]
            assert widget["cell"] == {"column":0, "row":row, "columnSpan":8, "rowSpan":rows}
            assert all(widget[field] for field in ("html", "css", "javascript"))
    wait_for_physical_description_bounds("Time and weather")
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "starter-demo-home.png")
    AUTOMATION.request(websocket_control, "selectPage", pageId="tools")
    wait_for_physical_description_bounds("Voice notes")
    wait_for_physical_description_bounds("Agenda")
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "starter-demo-tools.png")
    # Removing one document preserves the other complete document on that page.
    configured = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
    original = configuration_widget(configured, "time-weather")
    configuration_page(configured, "home")["widgets"] = [original]
    saved = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configReplace", config=configured))
    assert configuration_widget(saved, "time-weather") == original
    assert len(configuration_page(saved, "tools")["widgets"]) == 2


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_starter_demo_calendar_occurrences_pagination_and_access(websocket_control, protocol):
    grant_permissions(CALENDAR_PERMISSIONS)
    emulator_calendar_fixture_create()
    # Remove only generated rows from an earlier failed run of this test.
    stale = native_call("http", "contentQuery", {"resource":"calendarEvents", "query":"Demo occurrence ", "columns":["_id", "title"], "limit":200})
    for row in stale["items"]:
        if re.fullmatch(r"Demo occurrence [a-f0-9-]{36} <b>literal</b> ☃", row["title"] or ""):
            native_call("http", "contentWrite", {"resource":"calendarEvents", "operation":"delete", "id":row["_id"]})
    identifier = None
    try:
        calendar = next(row for row in native_call(protocol, "contentQuery", {"resource":"calendars"})["items"] if row["account_name"] == "android-lab-calendar-fixture")
        # The device clock, rather than the host clock, determines the live window.
        window = native_call(protocol, "contentQuery", {"resource":"calendarInstances", "limit":1})
        start = window["rangeStartMilliseconds"] + 12 * 60 * 60 * 1000
        title = "Demo occurrence " + str(uuid.uuid4()) + " <b>literal</b> ☃"
        values = {"calendar_id":calendar["_id"], "title":title, "dtstart":start,
                  "duration":"PT1H", "eventTimezone":"UTC", "rrule":"FREQ=DAILY;COUNT=3"}
        identifier = native_call(protocol, "contentWrite", {"resource":"calendarEvents", "operation":"create", "values":values})["results"][0]["id"]
        seen = []
        for offset, count, more in ((0,2,True), (2,1,False), (3,0,False), (10000,0,False)):
            result = native_call(protocol, "contentQuery", {"resource":"calendarInstances", "filter":{"event_id":identifier}, "limit":2, "offset":offset})
            assert len(result["items"]) == count and result["hasMore"] is more, result
            assert result["rangeStartMilliseconds"] == window["rangeStartMilliseconds"]
            assert result["rangeEndMilliseconds"] == window["rangeEndMilliseconds"]
            seen.extend(result["items"])
        assert len(seen) == 3 and [row["title"] for row in seen] == [title] * 3
        assert [row["begin"] for row in seen] == sorted({row["begin"] for row in seen})
        native_call(protocol, "contentWrite", {"resource":"calendarInstances", "operation":"delete", "id":identifier}, error="native_read_only")
        native_call(protocol, "contentQuery", {"resource":"calendarInstances", "columns":["begin; DROP TABLE events"]}, error="validation_failed")
        state = native_call(protocol, "deviceState")
        assert state["nextAlarmAtMilliseconds"] is None or isinstance(state["nextAlarmAtMilliseconds"], int)
        configured = demo_reset(websocket_control, "tools")
        agenda = configuration_widget(configured, "agenda")
        agenda["javascript"] += """
window.addEventListener("dikciz-ready", () => {
    let sent = false;
    const report = () => {
        const titles = [...document.querySelectorAll("#events strong")].map(node => node.textContent);
        if (sent || !titles.includes(EXPECTED_TITLE)) return;
        sent = true;
        selfWidget.patchState({renderedAgenda:{titles, injectedMarkup:document.querySelectorAll("#events b").length}})
            .catch(error => { document.body.dataset.probeError = error.code || "failed"; });
    };
    new MutationObserver(report).observe(document.getElementById("events"), {subtree:true, childList:true, characterData:true});
    report();
});
""".replace("EXPECTED_TITLE", json.dumps(title))
        AUTOMATION.request(websocket_control, "configReplace", config=configured)
        rendered = demo_state(websocket_control, "agenda", lambda state: title in state.get("renderedAgenda", {}).get("titles", []))
        assert rendered["renderedAgenda"]["injectedMarkup"] == 0
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / ("starter-demo-agenda-" + protocol + ".png"))
        device_command("pm revoke " + DIKCIZ_DEBUG_PACKAGE + " android.permission.READ_CALENDAR")
        restore_system_home()
        wait_for_restarted_websocket_control().close()
        native_call(protocol, "contentQuery", {"resource":"calendarInstances"}, error="native_permission_calendar")
        grant_permissions(CALENDAR_PERMISSIONS)
    finally:
        grant_permissions(CALENDAR_PERMISSIONS)
        restore_system_home()
        wait_for_restarted_websocket_control().close()
        if identifier is not None:
            native_call("http", "contentWrite", {"resource":"calendarEvents", "operation":"delete", "id":identifier})
        emulator_calendar_fixture_delete()


@pytest.mark.parametrize("leave_page", (False, True))
def test_starter_demo_voice_record_save_reopen_and_owned_cleanup(websocket_control, leave_page):
    grant_permissions(("android.permission.RECORD_AUDIO",))
    demo_reset(websocket_control, "tools")
    # Focus the WebView so Android exposes its virtual button nodes consistently.
    direct_tap(wait_for_physical_description_bounds("Voice notes"))
    direct_tap(wait_for_physical_text_bounds("Record voice note"))
    demo_state(websocket_control, RECORDER_ID, lambda state: bool(state.get("sessionId")))
    time.sleep(1.5)
    if leave_page:
        AUTOMATION.request(websocket_control, "selectPage", pageId="home")
    else:
        direct_tap(wait_for_physical_text_bounds("Stop and save"))
    saved = demo_state(websocket_control, RECORDER_ID, lambda state: bool(state.get("recordings")))
    assert not saved.get("sessionId") and not saved.get("pendingRecording"), saved
    note = saved["recordings"][0]
    path = RECORDER_PATH + "/" + note["name"]
    metadata = native_call("http", "fileManage", {"action":"stat", "path":path})
    assert metadata["size"] > 0, metadata
    AUTOMATION.request(websocket_control, "selectPage", pageId="home")
    AUTOMATION.request(websocket_control, "selectPage", pageId="tools")
    demo_state(websocket_control, RECORDER_ID, lambda state: state.get("recordings") == saved["recordings"])
    play = next(node for node in physical_nodes() if node.get("text", "").startswith("Play "))
    direct_tap(physical_bounds_from_nodes([play], lambda node: True))
    wait_for_physical_text_bounds("Stop playback")
    direct_tap(wait_for_physical_text_bounds("Stop playback"))
    configuration = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
    page = configuration_page(configuration, "tools")
    page["widgets"] = [widget for widget in page["widgets"] if widget["id"] != RECORDER_ID]
    AUTOMATION.request(websocket_control, "configReplace", config=configuration)
    native_call("http", "fileRead", {"path":path}, error="file_not_found")
    assert configuration_widget(AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet")), "device-status")


def test_starter_demo_all_day_survives_utc_midnight_with_local_day_unchanged(websocket_control):
    grant_permissions(CALENDAR_PERMISSIONS)
    emulator_calendar_fixture_create()
    identifier = None
    try:
        calendar = next(row for row in native_call("http", "contentQuery", {"resource":"calendars"})["items"] if row["account_name"] == "android-lab-calendar-fixture")
        window = native_call("http", "contentQuery", {"resource":"calendarInstances", "limit":1})
        day_ms = 24 * 60 * 60 * 1000
        day = window["rangeStartMilliseconds"] // day_ms * day_ms
        title = "All-day midnight fixture"
        values = {"calendar_id":calendar["_id"], "title":title, "dtstart":day, "dtend":day+day_ms, "eventTimezone":"UTC", "allDay":1}
        identifier = native_call("http", "contentWrite", {"resource":"calendarEvents", "operation":"create", "values":values})["results"][0]["id"]
        configured = demo_reset(websocket_control, "tools")
        agenda = configuration_widget(configured, "agenda")
        prefix = """
const OriginalDate = Date;
const localDay = new OriginalDate(DAY_VALUE);
const simulatedNow = DAY_VALUE + 25 * 60 * 60 * 1000;
window.Date = class extends OriginalDate {
    constructor(...args) { super(...(args.length ? args : [simulatedNow])); }
    static now() { return simulatedNow; }
    getFullYear() { return this.valueOf() === simulatedNow ? localDay.getUTCFullYear() : super.getFullYear(); }
    getMonth() { return this.valueOf() === simulatedNow ? localDay.getUTCMonth() : super.getMonth(); }
    getDate() { return this.valueOf() === simulatedNow ? localDay.getUTCDate() : super.getDate(); }
};
""".replace("DAY_VALUE", str(day))
        probe = """
window.addEventListener("dikciz-ready", () => {
    let sent = false;
    const observe = () => {
        const titles = [...document.querySelectorAll("#events strong")].map(node => node.textContent);
        if (sent || !titles.includes("All-day midnight fixture")) return;
        sent = true;
        selfWidget.patchState({allDayRendered:true}).catch(error => { document.body.dataset.probeError = error.code || "failed"; });
    };
    new MutationObserver(observe).observe(document.getElementById("events"), {subtree:true,childList:true,characterData:true});
    observe();
});
"""
        agenda["javascript"] = prefix + agenda["javascript"] + probe
        AUTOMATION.request(websocket_control, "configReplace", config=configured)
        demo_state(websocket_control, "agenda", lambda state: state.get("allDayRendered") is True)
    finally:
        if identifier is not None:
            native_call("http", "contentWrite", {"resource":"calendarEvents", "operation":"delete", "id":identifier})
        emulator_calendar_fixture_delete()
