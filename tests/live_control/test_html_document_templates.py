"""Complete templates, shared native metrics and bounded external data in real WebViews."""

from . import *
from .test_native_data import native_call, PROTOCOLS
from .test_html_widget_library import open_html_package_picker, package_picker_entry_semantic_id

TEMPLATE_WIDGET_ID = "document-template"
TEMPLATE_ROOT = Path("app/src/dikciz/assets/widget-library")
MARKET_FIXTURE = """
window.fetch = async url => {
    if (String(url).includes("coinbase")) return new Response(JSON.stringify({data:{base:"BTC",currency:"USD",amount:"123.45"}}));
    return new Response(JSON.stringify({articles:[
        {title:"Economic fixture headline",url:"https://example.invalid/economy",seendate:"fixture discovery time"},
        {title:"<img src=x onerror=alert(1)>",url:"https://example.invalid/literal"},
        {title:"Invalid scheme",url:"javascript:alert(1)"}
    ]}));
};
"""


def template_configuration(connection, package_id, prefix=""):
    configured = AUTOMATION.require_configuration(AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET))
    source = TEMPLATE_ROOT / package_id
    page = configuration_page(configured, FIXTURE_HOME_PAGE_ID)
    page["widgets"] = [{
        "id":TEMPLATE_WIDGET_ID,"type":"html","title":"Template acceptance","enabled":True,
        "html":(source / "index.html").read_text(),"css":(source / "style.css").read_text(),
        "javascript":prefix + (source / "script.js").read_text(), "state":{},
        "cell":{"column":0,"row":0,"columnSpan":4,"rowSpan":6},
    }]
    configured["launcher"]["home"]["selectedPageId"] = FIXTURE_HOME_PAGE_ID
    AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    return configured


def wait_template_state(connection, predicate):
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    state = None
    while time.monotonic() < deadline:
        configured = AUTOMATION.require_configuration(AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET))
        state = configuration_widget(configured, TEMPLATE_WIDGET_ID)["state"]
        if predicate(state): return state
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"Template did not persist expected result: {state!r}")


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_html_document_system_metrics_shared_and_bounded(websocket_control, protocol):
    result = native_call(protocol, "deviceState")
    system = result["system"]
    memory = system["memory"]
    assert memory["totalBytes"] > 0
    assert 0 <= memory["usedBytes"] <= memory["totalBytes"]
    assert memory["availableBytes"] + memory["usedBytes"] == memory["totalBytes"]
    storage = system["storage"]
    assert storage["scope"] == "app_data_filesystem"
    assert 0 <= storage["availableBytes"] <= storage["freeBytes"] <= storage["totalBytes"]
    assert system["cpu"]["logicalProcessors"] >= 1
    if system["cpu"]["systemAvailable"]:
        assert 0 <= system["cpu"]["systemPercent"] <= 100
    else:
        assert system["cpu"]["systemPercent"] is None
        assert system["cpu"]["reason"] in {"android_restricted","warming_up","invalid_cpu_counters"}
    sensors = system["sensors"]
    assert sensors["readingsAvailable"] is False and sensors["reason"] == "inventory_only"
    assert len(sensors["items"]) <= 64
    assert sensors["count"] >= len(sensors["items"])
    assert all(len(sensor["name"]) <= 128 for sensor in sensors["items"])
    second = native_call(protocol, "deviceState")["system"]
    assert second["sampledAtMilliseconds"] >= system["sampledAtMilliseconds"]


@pytest.mark.parametrize("package_id", ["system-dashboard", "sensors-dashboard", "markets-news"])
def test_html_document_complete_templates_in_picker(websocket_control, package_id):
    snapshot = open_html_package_picker(websocket_control, package_id)
    AUTOMATION.require_node(snapshot, package_picker_entry_semantic_id(package_id))


def test_html_document_system_dashboard_renders_real_data(websocket_control):
    configured = template_configuration(websocket_control, "system-dashboard")
    widget = configuration_widget(configured, TEMPLATE_WIDGET_ID)
    widget["javascript"] += """
window.addEventListener("dikciz-ready", () => {
    const status = document.getElementById("status");
    new MutationObserver(() => {
        selfWidget.patchState({probe:{title:document.querySelector("h1").textContent,
            ram:document.getElementById("ram").textContent,status:status.textContent}})
            .catch(error => { document.body.dataset.probeError = error.code || "failed"; });
    }).observe(status, {childList:true});
});
"""
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    state = wait_template_state(websocket_control, lambda value: "GiB" in value.get("probe",{}).get("ram","") and value.get("probe",{}).get("status","").startswith("Updated "))
    assert state["probe"]["title"] == "Your system"
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "system-dashboard-template.png")


@pytest.mark.parametrize("scenario", ["success", "news-offline", "oversized"])
def test_html_document_dynamic_market_template_saves_partial_and_validates_text(websocket_control, scenario):
    mock = MARKET_FIXTURE
    if scenario == "news-offline":
        mock = mock.replace('return new Response(JSON.stringify({articles:', 'throw new Error("fixture news offline"); return new Response(JSON.stringify({articles:')
    elif scenario == "oversized":
        mock = mock.replace('return new Response(JSON.stringify({articles:', 'return new Response("x".repeat(70000)); return new Response(JSON.stringify({articles:')
    if scenario == "success":
        mock += """
window.addEventListener("dikciz-ready", () => {
    document.getElementById("refresh").addEventListener("click", () => {
    let remaining = 100;
    const timer = setInterval(() => {
        const buttons = [...document.querySelectorAll("#news button")];
        if (--remaining <= 0) clearInterval(timer);
        if (!buttons.length || !selfWidget.state.market) return;
        clearInterval(timer);
        selfWidget.patchState({...selfWidget.state, renderedNews:{
            titles:buttons.map(button => button.textContent),images:document.querySelectorAll("#news img").length
        }}).catch(error => { document.body.dataset.probeError = error.code || "failed"; });
    }, 50);
    }, {once:true, capture:true});
});
"""
    template_configuration(websocket_control, "markets-news", mock)
    wait_for_physical_text_bounds("Markets and economy")
    direct_tap(wait_for_physical_text_bounds("Refresh price and news"))
    state = wait_template_state(websocket_control, lambda value: value.get("market", {}).get("amount") == "123.45" and (scenario != "success" or "renderedNews" in value))
    assert state["market"]["fetchedAt"]
    if scenario == "success":
        assert len(state["news"]["articles"]) == 2
        assert state["news"]["articles"][1]["title"] == "<img src=x onerror=alert(1)>"
        assert state["renderedNews"] == {"titles":["Economic fixture headline","<img src=x onerror=alert(1)>"], "images":0}
    else:
        assert "news" not in state
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / ("market-template-" + scenario + ".png"))
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SELECT_PAGE, pageId=FIXTURE_NOTES_PAGE_ID)
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SELECT_PAGE, pageId=FIXTURE_HOME_PAGE_ID)
    assert wait_template_state(websocket_control, lambda value: value == state) == state


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_html_document_editor_saves_all_source_fields_through_each_entrypoint(websocket_control, protocol, monkeypatch):
    from . import test_remote_device_control as remote

    for operation, tool in (("longPress","dikciz_long_press"),("tap","dikciz_tap"),("setText","dikciz_set_text")):
        monkeypatch.setitem(remote.TOOLS, operation, tool)
    configured = template_configuration(websocket_control, "markets-news")
    prefix = "widget:" + TEMPLATE_WIDGET_ID
    remote.remote_call(protocol, "longPress", {"semanticId":prefix + ":move-handle"})
    wait_for_snapshot_node(websocket_control, prefix + ":edit")
    remote.remote_call(protocol, "tap", {"semanticId":prefix + ":edit"})
    wait_for_snapshot_node(websocket_control, prefix + ":editor:html")
    changes = {"html":"<main>Whole document edited</main>", "css":"main{color:#fff}",
               "javascript":"document.body.dataset.fixture = String(1);", "state":json.dumps({"wholeDocument":True})}
    for field, text in changes.items():
        remote.remote_call(protocol, "setText", {"semanticId":prefix + ":editor:" + field, "text":text})
    remote.remote_call(protocol, "tap", {"semanticId":prefix + ":editor:save"})
    saved = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
    widget = configuration_widget(saved, TEMPLATE_WIDGET_ID)
    for field, value in changes.items():
        assert widget[field] == (json.loads(value) if field == "state" else value)
    assert {page["id"] for page in saved["launcher"]["home"]["pages"]} == {page["id"] for page in configured["launcher"]["home"]["pages"]}
    wait_for_physical_text_bounds("Whole document edited")


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_html_document_app_icons_are_png_and_validate_requests(websocket_control, protocol):
    import base64
    import struct

    catalogue = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_APP_CATALOGUE)
    component = catalogue["apps"][0]["component"]
    icon = native_call(protocol, "appIcon", {"component":component})
    png = base64.b64decode(icon["pngBase64"], validate=True)
    assert png.startswith(bytes([137,80,78,71,13,10,26,10]))
    assert struct.unpack(">II",png[16:24]) == (64,64)
    assert icon["mimeType"] == "image/png"
    assert icon["component"] == component
    assert icon["width"] == icon["height"] == 64
    for fields in ({"component":""},{"component":component,"sizePixels":0},
                   {"component":component,"sizePixels":129},{"component":component,"sizePixels":64.5},
                   {"component":component,"unexpected":True}):
        native_call(protocol, "appIcon", fields, error="validation_failed")
    native_call(protocol, "appIcon", {"component":"invalid.missing/invalid.missing.Main"}, error="native_record_not_found")


def test_html_document_starter_apps_render_icons(websocket_control):
    configured = template_configuration(websocket_control, "quick-apps")
    widget = configuration_widget(configured, TEMPLATE_WIDGET_ID)
    widget["javascript"] += """
window.addEventListener("dikciz-ready", () => {
    let completed = false;
    const check = () => {
        if (completed) return;
        const images = [...document.querySelectorAll("img")];
        if (!images.some(image => image.complete && image.naturalWidth > 0)) return;
        completed = true;
        observer.disconnect();
        selfWidget.patchState({iconProbe:images.map(image => ({loaded:image.complete && image.naturalWidth>0,source:image.src.startsWith("data:image/png;base64,")}))})
            .catch(error => { document.body.dataset.probeError = error.code || "failed"; });
    };
    const observer = new MutationObserver(check);
    document.addEventListener("load", check, true);
    observer.observe(document.body, {subtree:true,attributes:true,childList:true});
});
"""
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    state = wait_template_state(websocket_control, lambda value: any(icon["loaded"] and icon["source"] for icon in value.get("iconProbe",[])))
    assert len(state["iconProbe"]) <= 6
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "html-app-icons.png")
