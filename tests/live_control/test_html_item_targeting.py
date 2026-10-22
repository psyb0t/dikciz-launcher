"""Whole-document HTML editing through real touch and native controls."""

from . import *


ITEM_WIDGET_ID = "html-items"
ITEM_PREFIX = f"widget:{ITEM_WIDGET_ID}:html-item"
ITEM_FIRST_TITLE = "First HTML card"
ITEM_SECOND_TITLE = "Second HTML card"
ITEM_SOURCE = (
    '<main><article class="card" id="first"><h2>First HTML card</h2>'
    '<p><span>Nested first label</span></p><ul><li>Nested list entry</li></ul>'
    '<button id="item-action">Tap action</button></article>'
    '<article class="card" id="second"><h2>Second HTML card</h2>'
    '<p>Keep this sibling</p></article></main>'
)


def item_configuration(original: dict[str, Any]) -> dict[str, Any]:
    configured = copy.deepcopy(original)
    page = configuration_page(configured, FIXTURE_HOME_PAGE_ID)
    page["widgets"] = [{
        "id": ITEM_WIDGET_ID,
        "type": "html",
        "title": "HTML item regression",
        "html": ITEM_SOURCE,
        "css": (
            "html,body{margin:0;background:#101722;color:white;font:16px sans-serif}"
            "main{padding:12px}.card{padding:12px;margin-bottom:16px;background:#20334a}"
            "h2,p,ul{margin:4px}h2{font-size:18px}"
        ),
        "javascript": ITEM_PROBE_JAVASCRIPT,
        "style": {"padding": {"all": 0}},
        "state": {"preserved": "owner state"},
        "enabled": True,
        "cell": {"column": 0, "row": 0, "columnSpan": 4, "rowSpan": 6},
    }]
    configured["launcher"]["home"]["selectedPageId"] = FIXTURE_HOME_PAGE_ID
    return configured


ITEM_PROBE_JAVASCRIPT = """window.addEventListener("dikciz-ready", () => {
  const selectors = { nested: "#first span", first: "#first", second: "#second", runtime: "#runtime-only", action: "#item-action" };
  const targets = {};
  const texts = {};
  for (const [name, selector] of Object.entries(selectors)) {
    const element = document.querySelector(selector);
    texts[name] = name === "background" ? "" : (element?.textContent.trim() || "");
    if (!element) continue;
    const rect = element.getBoundingClientRect();
    targets[name] = { x: rect.x + rect.width / 2, y: rect.y + rect.height / 2 };
  }
  selfWidget.patchState({ ...selfWidget.state, itemObservation: { targets, texts, viewportWidth: innerWidth } }).catch(error => {
    document.body.setAttribute("data-probe-error", error.code || "probe_failed");
  });
  document.getElementById("item-action")?.addEventListener("click", () => {
    selfWidget.patchState({ ...selfWidget.state, clicks: (selfWidget.state.clicks || 0) + 1 }).catch(error => {
      document.body.setAttribute("data-probe-error", error.code || "probe_failed");
    });
  });
});"""


def item_observation(
    connection: socket.socket,
    *,
    first_text: str | None = None,
    second_text: str | None = None,
) -> tuple[dict[str, Any], dict[str, Any]]:
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    observed = None
    while time.monotonic() < deadline:
        configured = AUTOMATION.require_configuration(AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET))
        widget = configuration_widget(configured, ITEM_WIDGET_ID)
        observed = widget["state"].get("itemObservation")
        if (
            isinstance(observed, dict)
            and (first_text is None or observed["texts"]["first"] == first_text)
            and (second_text is None or observed["texts"]["second"] == second_text)
        ):
            return configured, observed
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"rendered HTML observation did not arrive: {observed!r}")


def press_html_item(connection: socket.socket, target: str = "nested") -> None:
    _, observation = item_observation(connection)
    snapshot = AUTOMATION.request(connection, AUTOMATION.TYPE_SNAPSHOT)
    bounds = bounds_for(snapshot, f"widget:{ITEM_WIDGET_ID}")
    point = observation["targets"][target]
    scale = (bounds["right"] - bounds["left"]) / observation["viewportWidth"]
    direct_long_press_at(round(bounds["left"] + point["x"] * scale), round(bounds["top"] + point["y"] * scale))


def without_item_observation(configuration: dict[str, Any]) -> dict[str, Any]:
    configured = copy.deepcopy(configuration)
    configuration_widget(configured, ITEM_WIDGET_ID)["state"].pop("itemObservation", None)
    return configured



@pytest.mark.parametrize("target", ["nested", "first", "second", "action", "background", "runtime"])
def test_html_document_content_opens_whole_widget_controls(websocket_control, target):
    configured = item_configuration(AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET)))
    widget = configuration_widget(configured, ITEM_WIDGET_ID)
    widget["javascript"] = (
        'const runtime = document.createElement("div"); runtime.id = "runtime-only"; runtime.textContent = "Runtime content"; document.body.append(runtime);'
        + ITEM_PROBE_JAVASCRIPT.replace('const selectors = {', 'const selectors = { background:"body",')
    )
    widget["css"] += "#runtime-only{height:70px}body{min-height:450px}"
    # A point in the document background avoids the native move handle.
    widget["javascript"] = widget["javascript"].replace(
        'const rect = element.getBoundingClientRect();',
        'const rect = element.getBoundingClientRect(); if(name === "background") { targets[name] = {x:20,y:innerHeight-64}; continue; }')
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    before, _ = item_observation(websocket_control)
    press_html_item(websocket_control, target)
    snapshot = wait_for_snapshot_node(websocket_control, f"widget:{ITEM_WIDGET_ID}:edit")
    ids = {node.get("semanticId", "") for node in snapshot["nodes"]}
    assert f"widget:{ITEM_WIDGET_ID}:appearance" in ids
    assert f"widget:{ITEM_WIDGET_ID}:delete" in ids
    assert not any(":html-item:" in value or ":add-block" in value for value in ids)
    after = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
    assert after == before
    assert "clicks" not in configuration_widget(after, ITEM_WIDGET_ID)["state"]
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId=f"widget:{ITEM_WIDGET_ID}:edit")
    prefix = f"widget:{ITEM_WIDGET_ID}:editor"
    editor = wait_for_snapshot_node(websocket_control, prefix + ":html")
    for field in ("html", "css", "javascript", "state"):
        node = AUTOMATION.require_node(editor, prefix + ":" + field)
        expected = configuration_widget(before, ITEM_WIDGET_ID)[field]
        text = node[AUTOMATION.KEY_TEXT]
        if field == "state":
            assert json.loads(text) == expected
        else:
            assert text == expected
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / ("html-document-editor-" + target + ".png"))


@pytest.mark.parametrize("locked", ["widget", "page"])
def test_html_document_locks_prevent_content_editing(websocket_control, locked):
    configured = item_configuration(AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET)))
    target = configuration_page(configured, FIXTURE_HOME_PAGE_ID) if locked == "page" else configuration_widget(configured, ITEM_WIDGET_ID)
    target["locked"] = True
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    before, _ = item_observation(websocket_control)
    press_html_item(websocket_control)
    snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
    assert not any(node.get("semanticId") == f"widget:{ITEM_WIDGET_ID}:edit" for node in snapshot["nodes"])
    assert AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET)) == before
