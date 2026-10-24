"""Starter presentation and shared device telemetry on the private Android lab."""

from . import *
from .test_native_data import native_call, PROTOCOLS


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_home_showcase_cpu_contract(websocket_control, protocol):
    cpu = native_call(protocol, "deviceState")["system"]["cpu"]
    assert cpu["permissionRequestAvailable"] is False
    assert cpu["appScope"] == "dikciz_process"
    assert 1 <= len(cpu["cores"]) <= 64
    assert len({core["id"] for core in cpu["cores"]}) == len(cpu["cores"])
    for core in cpu["cores"]:
        assert core["online"] in (True, False, None)
        assert core["usagePercent"] is None or 0 <= core["usagePercent"] <= 100
        assert core["usageAvailable"] == (core["usagePercent"] is not None)
        assert core["frequencyKilohertz"] is None or core["frequencyKilohertz"] > 0
    if cpu["appSampleAvailable"]:
        assert 0 <= cpu["appPercentOfCapacity"] <= 100
        assert cpu["appSampleWindowMilliseconds"] >= 250
    else:
        assert cpu["appPercentOfCapacity"] is None


SHOWCASE_ROOT = Path("app/src/dikciz/assets/widget-library")
SHOWCASE_WIDGET = "showcase"
SHOWCASE_PROBE = """
window.addEventListener("dikciz-ready", () => {
    let previous = "";
    function publish() {
        const value = {
            status:document.getElementById("status")?.textContent,
            battery:document.getElementById("battery")?.textContent,
            cpu:document.getElementById("app-cpu")?.textContent,
            coreText:document.getElementById("core-grid")?.textContent,
            note:document.getElementById("cpu-note")?.textContent,
            gauge:document.getElementById("battery-gauge")?.getAttribute("aria-valuenow"),
            icons:[...document.querySelectorAll("#apps img")].filter(image => image.complete && image.naturalWidth > 0).length,
            labels:[...document.querySelectorAll("#apps .label")].map(item => item.textContent),
            calls:window.showcaseCalls || [],
            demo:document.getElementById("demo-status")?.textContent,
            overflow:document.documentElement.scrollWidth > innerWidth + 1,
        };
        const encoded = JSON.stringify(value);
        if (encoded === previous) return;
        previous = encoded;
        selfWidget.patchState({...selfWidget.state, probe:value}).catch(error => {
            document.body.dataset.probeError = error.code || "failed";
        });
    }
    new MutationObserver(publish).observe(document.body, {subtree:true,childList:true,attributes:true});
    document.addEventListener("load", publish, true);
    publish();
});
"""


def showcase_document(connection, package, prefix="", columns=8, rows=6):
    configured = AUTOMATION.require_configuration(AUTOMATION.request(connection, "configGet"))
    configured["limits"]["maxTextCharacters"] = 65536
    configured["launcher"]["home"]["nativeGrid"].update(columns=8, rows=6)
    source = SHOWCASE_ROOT / package
    configuration_page(configured, "home")["widgets"] = [{
        "id":SHOWCASE_WIDGET, "type":"html", "title":"Showcase acceptance", "enabled":True,
        "html":(source / "index.html").read_text(), "css":(source / "style.css").read_text(),
        "javascript":prefix + (source / "script.js").read_text() + SHOWCASE_PROBE,
        "state":{}, "cell":{"column":0,"row":0,"columnSpan":columns,"rowSpan":rows},
        "style":{"padding":{"all":0},"margin":{"all":0},"border":{"widths":{"all":0}}},
    }]
    AUTOMATION.request(connection, "configReplace", config=configured)
    return configured


def showcase_probe(connection, predicate):
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    value = {}
    while time.monotonic() < deadline:
        configured = AUTOMATION.require_configuration(AUTOMATION.request(connection, "configGet"))
        value = configuration_widget(configured, SHOWCASE_WIDGET)["state"].get("probe", {})
        if predicate(value):
            return value
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"showcase did not reach expected state: {value!r}")


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_home_showcase_usage_access_contract(websocket_control, protocol):
    try:
        for mode, expected in (("ignore", False), ("allow", True)):
            device_command(f"cmd appops set {DIKCIZ_DEBUG_PACKAGE} GET_USAGE_STATS {mode}")
            result = native_call(protocol, "appCatalogue")
            assert result["usageAvailable"] is expected
            assert result["usageWindowDays"] == 7
            assert result["usageReason"] == (None if expected else "usage_access_required")
            assert all(
                isinstance(app["foregroundMilliseconds"], int) and app["foregroundMilliseconds"] >= 0
                if expected else app["foregroundMilliseconds"] is None
                for app in result["apps"]
            )
    finally:
        device_command(f"cmd appops set {DIKCIZ_DEBUG_PACKAGE} GET_USAGE_STATS default")


def test_home_showcase_native_dock_and_pages(websocket_control):
    configured = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "reset"))
    pages = configured["launcher"]["home"]["pages"]
    assert [page["id"] for page in pages] == ["home", "tools", "device", "goblin"]
    home = configuration_page(configured, "home")
    assert [widget["id"] for widget in home["widgets"] if widget["type"] == "html"] == ["time-weather", "most-used-apps"]
    dock = [widget for widget in home["widgets"] if widget["type"] == "app"]
    assert len(dock) >= 2
    catalogue = AUTOMATION.request(websocket_control, "appCatalogue")["apps"]
    assert all(widget["component"] in {app["component"] for app in catalogue} for widget in dock)
    assert all(widget["cell"]["row"] == 5 and widget["cell"]["rowSpan"] == 1 for widget in dock)
    assert [widget["cell"]["column"] for widget in dock] == sorted(widget["cell"]["column"] for widget in dock)
    store = next((widget for widget in dock if widget["id"] == "dock-3"), None)
    available = {app["packageName"] for app in catalogue}
    expected_store = next((name for name in ("dev.imranr.obtainium", "org.fdroid.fdroid", "com.android.vending") if name in available), None)
    assert (store["component"].split("/")[0] if store else None) == expected_store
    for page in pages:
        cells = set()
        for widget in page["widgets"]:
            cell = widget["cell"]
            occupied = {(x,y) for x in range(cell["column"], cell["column"] + cell["columnSpan"]) for y in range(cell["row"], cell["row"] + cell["rowSpan"])}
            assert all(0 <= x < 8 and 0 <= y < 6 for x,y in occupied)
            assert cells.isdisjoint(occupied)
            cells.update(occupied)


@pytest.mark.parametrize("package", ("device-status", "system-dashboard"))
def test_home_showcase_device_renders_real_metrics(websocket_control, package):
    showcase_document(websocket_control, package)
    value = showcase_probe(websocket_control, lambda value: value.get("status", "").startswith("Updated ") and value.get("gauge") is not None)
    assert 0 <= int(value["gauge"]) <= 100
    assert value["battery"].endswith("%")
    assert "CPU 0" in value["coreText"]
    assert value["overflow"] is False
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / f"showcase-{package}.png")


@pytest.mark.parametrize("reason", ("android_restricted", "warming_up"))
def test_home_showcase_cpu_unknown_is_not_fake_zero(websocket_control, reason):
    payload = {
        "batteryLevel":72,"batteryScale":100,"batteryTemperatureTenthsCelsius":286,
        "batteryVoltageMillivolts":4050,"chargingSource":0,"powerSaveMode":False,
        "wifi":True,"internetValidated":True,
        "system":{"sampledAtMilliseconds":int(time.time()*1000),
            "memory":{"totalBytes":8*1024**3,"usedBytes":3*1024**3,"availableBytes":5*1024**3},
            "storage":{"totalBytes":128*1024**3,"availableBytes":91*1024**3},
            "cpu":{"systemAvailable":False,"reason":reason,"logicalProcessors":8,
                "appSampleAvailable":True,"appPercentOfCapacity":3.5,
                "cores":[{"id":0,"online":True,"frequencyKilohertz":1500000,"maximumFrequencyKilohertz":2400000,"usageAvailable":False,"usagePercent":None}]},
            "sensors":{"count":0,"items":[]}},
    }
    prefix = 'window.addEventListener("dikciz-ready", () => { window.dikciz={...window.dikciz}; const original=dikciz.command; const fixture=' + json.dumps(payload) + '; dikciz.command=(type, fields, options)=>type==="deviceState" ? Promise.resolve(fixture) : original(type, fields, options); });\n'
    showcase_document(websocket_control, "device-status", prefix)
    value = showcase_probe(websocket_control, lambda value: value.get("cpu") == "3.5%")
    assert value["gauge"] == "72"
    assert "1.50 GHz" in value["coreText"]
    assert "0% load" not in value["coreText"]
    assert ("cannot unlock" in value["note"]) is (reason == "android_restricted")


DEMO_PREFIX = """
window.addEventListener("dikciz-ready", () => {
    window.dikciz = {...window.dikciz};
    const original = dikciz.command;
    window.showcaseCalls = [];
    dikciz.command = async (type, fields, options) => {
        if (type === "torchControl" || type === "speechSpeak") {
            window.showcaseCalls.push({type, ...fields});
            return {state:"submitted"};
        }
        return original(type, fields, options);
    };
});
"""


def test_home_showcase_icons_and_manual_demos(websocket_control):
    showcase_document(websocket_control, "most-used-apps", DEMO_PREFIX)
    value = showcase_probe(websocket_control, lambda value: value.get("icons", 0) >= 2)
    assert value["calls"] == []
    assert value["overflow"] is False
    direct_tap(wait_for_physical_text_bounds("ϟ Flash attack"))
    value = showcase_probe(websocket_control, lambda value: value.get("demo") == "Torch pulse requests finished.")
    on = [call for call in value["calls"] if call["type"] == "torchControl" and call["action"] == "on"]
    assert len(on) == 8 and all(call["durationMilliseconds"] == 250 for call in on)
    assert value["calls"][-1]["action"] == "off"
    direct_tap(wait_for_physical_text_bounds("◉ Dikciz time"))
    value = showcase_probe(websocket_control, lambda value: value.get("demo") == "Dikciz time.")
    assert value["calls"][-1]["type"] == "speechSpeak"
    assert value["calls"][-1]["text"] == "It's Dikciz time, bitch!"


def test_home_showcase_flash_stops_on_hide(websocket_control):
    showcase_document(websocket_control, "most-used-apps", DEMO_PREFIX)
    showcase_probe(websocket_control, lambda value: value.get("icons", 0) >= 2)
    direct_tap(wait_for_physical_text_bounds("ϟ Flash attack"))
    showcase_probe(websocket_control, lambda value: any(call.get("action") == "on" for call in value.get("calls", [])))
    AUTOMATION.request(websocket_control, "selectPage", pageId=FIXTURE_NOTES_PAGE_ID)
    time.sleep(1)
    configured = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
    calls = configuration_widget(configured, SHOWCASE_WIDGET)["state"]["probe"]["calls"]
    assert calls[-1]["action"] == "off"
    assert len([call for call in calls if call.get("action") == "on"]) < 8


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_home_showcase_torch_duration_validation_and_cleanup(websocket_control, protocol):
    for fields in (
        {"action":"on", "durationMilliseconds":0},
        {"action":"on", "durationMilliseconds":5001},
        {"action":"off", "durationMilliseconds":250},
        {"action":"status", "durationMilliseconds":250},
    ):
        native_call(protocol, "torchControl", fields, error="validation_failed")
    device_command(f"pm grant {DIKCIZ_DEBUG_PACKAGE} android.permission.CAMERA")
    inventory = native_call(protocol, "torchControl", {"action":"status"})
    if not inventory["cameraIds"]:
        native_call(protocol, "torchControl", {"action":"on", "durationMilliseconds":250}, error="native_record_not_found")
        return
    camera = inventory["cameraIds"][0]
    try:
        result = native_call(protocol, "torchControl", {"action":"on", "cameraId":camera, "durationMilliseconds":250})
        assert result["autoOffAfterMilliseconds"] == 250
        time.sleep(0.4)
        final = native_call(protocol, "torchControl", {"action":"status", "cameraId":camera})
        assert final["items"][0]["enabled"] is False
    finally:
        native_call(protocol, "torchControl", {"action":"off", "cameraId":camera})


def test_home_showcase_template_is_reusable_from_the_flat_picker(websocket_control):
    from .test_html_widget_library import open_html_package_picker, package_picker_entry_semantic_id
    snapshot = open_html_package_picker(websocket_control, "most-used-apps")
    entry = AUTOMATION.require_node(snapshot, package_picker_entry_semantic_id("most-used-apps"))
    assert "Most used apps" in entry["text"]
    assert "Usage access" in entry["text"]
