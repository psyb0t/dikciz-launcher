"""Exercise HTML item targeting through real touch and native controls."""

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
    texts[name] = element?.textContent.trim() || "";
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


@pytest.fixture(autouse=True)
def close_item_dialog_after_test(
    websocket_control: socket.socket,
    isolated_launcher_fixture: None,
    request: pytest.FixtureRequest,
) -> Generator[None, None, None]:
    yield
    snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
    artifact = "html-item-" + request.node.name.replace("[", "-").replace("]", "")
    run_device_operation("screenshot")
    (ARTIFACT_DIRECTORY / f"{artifact}.png").write_bytes(
        (ARTIFACT_DIRECTORY / "screenshot.png").read_bytes(),
    )
    (ARTIFACT_DIRECTORY / f"{artifact}.json").write_text(
        json.dumps({
            "snapshot": snapshot,
            "configuration": AUTOMATION.require_configuration(
                AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
            ),
        }),
        encoding="utf-8",
    )
    if any(node.get("semanticId") == "dialog:close" for node in snapshot["nodes"]):
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId="dialog:close")


def test_html_item_targeting_delete_preserves_page_widget_and_sibling(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = item_configuration(original)
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    press_html_item(websocket_control)
    wait_for_snapshot_node(websocket_control, f"{ITEM_PREFIX}:delete")
    run_device_operation("screenshot")
    (ARTIFACT_DIRECTORY / "html-item-menu.png").write_bytes(
        (ARTIFACT_DIRECTORY / "screenshot.png").read_bytes(),
    )
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId=f"{ITEM_PREFIX}:delete")
    item_observation(websocket_control, first_text="")
    saved = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    widget = configuration_widget(saved, ITEM_WIDGET_ID)
    assert ITEM_FIRST_TITLE not in widget["html"]
    assert ITEM_SECOND_TITLE in widget["html"]
    assert widget["state"]["preserved"] == "owner state"
    assert len(saved["launcher"]["home"]["pages"]) == len(configured["launcher"]["home"]["pages"])
    assert len(configuration_page(saved, FIXTURE_HOME_PAGE_ID)["widgets"]) == 1
    run_device_operation("screenshot")
    press_html_item(websocket_control, "second")
    wait_for_snapshot_node(websocket_control, f"{ITEM_PREFIX}:delete")
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId=f"{ITEM_PREFIX}:delete")
    item_observation(websocket_control, first_text="", second_text="")
    empty = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    empty_widget = configuration_widget(empty, ITEM_WIDGET_ID)
    assert ITEM_FIRST_TITLE not in empty_widget["html"]
    assert ITEM_SECOND_TITLE not in empty_widget["html"]
    assert len(configuration_page(empty, FIXTURE_HOME_PAGE_ID)["widgets"]) == 1


@pytest.mark.parametrize("operation", ["edit", "empty", "cancel"])
def test_html_item_targeting_edit_and_cancel_are_scoped(
    websocket_control: socket.socket,
    operation: str,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = item_configuration(original)
    before = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured),
    )
    press_html_item(websocket_control)
    wait_for_snapshot_node(websocket_control, f"{ITEM_PREFIX}:edit")
    if operation == "cancel":
        direct_tap(wait_for_physical_button_bounds("Cancel"))
    else:
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId=f"{ITEM_PREFIX}:edit")
        wait_for_snapshot_node(websocket_control, f"{ITEM_PREFIX}:source")
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_SET_TEXT,
            semanticId=f"{ITEM_PREFIX}:source",
            text="" if operation == "empty" else '<article class="card" id="first"><h2>Edited α card</h2></article>',
        )
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId=f"{ITEM_PREFIX}:save")
        item_observation(websocket_control, first_text="" if operation == "empty" else "Edited α card")
    after = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    if operation == "cancel":
        assert without_item_observation(after) == without_item_observation(before)
    else:
        saved_widget = configuration_widget(after, ITEM_WIDGET_ID)
        assert ("Edited α card" in saved_widget["html"]) == (operation == "edit")
        assert ITEM_SECOND_TITLE in saved_widget["html"]
        assert "Nested first label" not in saved_widget["html"]
        configuration_widget(before, ITEM_WIDGET_ID)["html"] = saved_widget["html"]
        assert without_item_observation(after) == without_item_observation(before)


@pytest.mark.parametrize("blocked_by", ["widget", "page", "runtime", "padding"])
def test_html_item_targeting_refuses_locked_and_runtime_only_items(
    websocket_control: socket.socket,
    blocked_by: str,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = item_configuration(original)
    page = configuration_page(configured, FIXTURE_HOME_PAGE_ID)
    widget = page["widgets"][0]
    target = "nested"
    if blocked_by == "widget":
        widget["locked"] = True
    elif blocked_by == "page":
        page["locked"] = True
        target = "second"
    elif blocked_by == "padding":
        widget["style"]["padding"]["all"] = 24
    else:
        widget["javascript"] = (
            "const runtime=document.createElement('article');runtime.className='card';runtime.id='runtime-only';"
            "runtime.textContent='Runtime-only card';document.querySelector('main').prepend(runtime);"
        ) + ITEM_PROBE_JAVASCRIPT
        target = "runtime"
    before = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured),
    )
    if blocked_by == "padding":
        item_observation(websocket_control)
        bounds = bounds_for(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT), f"widget:{ITEM_WIDGET_ID}")
        direct_long_press_at(bounds["left"] + 5, (bounds["top"] + bounds["bottom"]) // 2)
    else:
        press_html_item(websocket_control, target)
    snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
    assert not any(node.get("semanticId") == f"{ITEM_PREFIX}:delete" for node in snapshot["nodes"])
    assert not any(node.get("semanticId") == f"widget:{ITEM_WIDGET_ID}:delete" for node in snapshot["nodes"])
    after = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert without_item_observation(after) == without_item_observation(before)


def test_html_item_targeting_whole_widget_controls_remain_on_move_handle(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=item_configuration(original))
    wait_for_snapshot_node(websocket_control, f"widget:{ITEM_WIDGET_ID}:move-handle")
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_LONG_PRESS,
        semanticId=f"widget:{ITEM_WIDGET_ID}:move-handle",
    )
    wait_for_snapshot_node(websocket_control, f"widget:{ITEM_WIDGET_ID}:delete")
    wait_for_snapshot_node(websocket_control, f"widget:{ITEM_WIDGET_ID}:resize")


def test_html_item_targeting_appended_block_is_editable_without_reload(
    websocket_control: socket.socket,
) -> None:
    from .test_html_block_in_place_append import (
        append_configuration,
        open_block_picker,
        wait_for_live_generation,
    )

    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = append_configuration(original)
    target_widget = configuration_widget(configured, "live-block")
    target_widget["style"] = {"padding": {"all": 0}}
    target_widget["javascript"] += """
let scrollTimer;
window.addEventListener("scroll", () => {
  clearTimeout(scrollTimer);
  scrollTimer = setTimeout(() => {
    const heading = document.querySelector(".dikciz-block-heading h2");
    if (!heading) return;
    const rect = heading.getBoundingClientRect();
    selfWidget.patchState({ ...selfWidget.state, appendedHit: {
      x: rect.x + rect.width / 2,
      y: rect.y + rect.height / 2,
      viewportWidth: innerWidth,
      viewportHeight: innerHeight,
    }}).catch(error => document.body.setAttribute("data-probe-error", error.code || "probe_failed"));
  }, 100);
});
"""
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    generation = wait_for_live_generation(websocket_control)
    open_block_picker(websocket_control)
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId="html-block:confirm")
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    hit = None
    while time.monotonic() < deadline:
        current = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
        hit = configuration_widget(current, "live-block")["state"].get("appendedHit")
        if isinstance(hit, dict) and 0 <= hit["y"] < hit["viewportHeight"]:
            break
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    assert isinstance(hit, dict), "appended heading did not finish scrolling into view"
    physical_nodes()
    current = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
    hit = configuration_widget(current, "live-block")["state"]["appendedHit"]
    assert 0 <= hit["y"] < hit["viewportHeight"], hit
    snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
    bounds = bounds_for(snapshot, "widget:live-block")
    scale = (bounds["right"] - bounds["left"]) / hit["viewportWidth"]
    direct_long_press_at(round(bounds["left"] + hit["x"] * scale), round(bounds["top"] + hit["y"] * scale))
    delete_id = "widget:live-block:html-item:delete"
    try:
        wait_for_snapshot_node(websocket_control, delete_id)
    except AssertionError as exception:
        (ARTIFACT_DIRECTORY / "html-item-touch-windows.txt").write_text(device_command("dumpsys window windows"), encoding="utf-8")
        (ARTIFACT_DIRECTORY / "html-item-touch-logcat.txt").write_text(device_command("logcat -d -v brief -s DikcizHome:D InputDispatcher:V"), encoding="utf-8")
        records = device_log_records()
        hits = [record for record in records if record.get("event") == "html_item_hit_test"][-10:]
        observed = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
        state = configuration_widget(observed, "live-block")["state"]
        raise AssertionError(f"HTML item menu did not open; hit tests: {hits!r}; state: {state!r}; bounds: {bounds!r}") from exception
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId=delete_id)
    saved = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    widget = configuration_widget(saved, "live-block")
    assert "Section title" not in widget["html"]
    assert "Short caption describing this section." not in widget["html"]
    assert 'id="runtime"' in widget["html"]
    assert widget["state"]["generation"] == generation


def test_html_item_targeting_button_long_press_does_not_activate_button(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=item_configuration(original))
    _, observation = item_observation(websocket_control)
    bounds = bounds_for(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT), f"widget:{ITEM_WIDGET_ID}")
    point = observation["targets"]["action"]
    scale = (bounds["right"] - bounds["left"]) / observation["viewportWidth"]
    device_command(f"input tap {round(bounds['left'] + point['x'] * scale)} {round(bounds['top'] + point['y'] * scale)}")
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        configured = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
        if configuration_widget(configured, ITEM_WIDGET_ID)["state"].get("clicks") == 1:
            break
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    assert configuration_widget(configured, ITEM_WIDGET_ID)["state"].get("clicks") == 1
    press_html_item(websocket_control, "action")
    wait_for_snapshot_node(websocket_control, f"{ITEM_PREFIX}:delete")
    configured = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
    assert configuration_widget(configured, ITEM_WIDGET_ID)["state"]["clicks"] == 1


@pytest.mark.parametrize("removed", ['[data-dikciz-preset="quick-apps"]', '[data-dikciz-preset="quick-note"]', '[data-role="clock"]'])
def test_html_item_targeting_bundled_controls_survive_removed_card(
    websocket_control: socket.socket,
    removed: str,
) -> None:
    configured = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_RESET))
    configured["limits"]["maxTextCharacters"] = 65536
    widget = configuration_widget(configured, "home-dashboard")
    original_javascript = widget["javascript"]
    widget["javascript"] = """let starterErrors = 0;
window.addEventListener("error", () => starterErrors++);
window.addEventListener("unhandledrejection", () => starterErrors++);
document.querySelector(%s).remove();
""" % json.dumps(removed) + original_javascript + """
window.addEventListener("dikciz-ready", () => {
  setTimeout(() => selfWidget.patchState({ ...selfWidget.state, starterReady: true,
    starterErrors, starterClock: document.querySelector('[data-role="clock"]')?.textContent || "",
  }).catch(error => document.body.setAttribute("data-probe-error", error.code || "probe_failed")), 100);
});"""
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    observed = {}
    while time.monotonic() < deadline:
        current = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
        observed = configuration_widget(current, "home-dashboard")["state"]
        if observed.get("starterReady"):
            break
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    assert observed.get("starterReady"), observed
    assert observed["starterErrors"] == 0, observed
    if removed != '[data-role="clock"]':
        assert observed["starterClock"] not in {"", "--:--"}, observed


def test_html_item_targeting_stale_menu_cannot_overwrite_changed_source(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = item_configuration(original)
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    press_html_item(websocket_control)
    wait_for_snapshot_node(websocket_control, f"{ITEM_PREFIX}:delete")
    configuration_widget(configured, ITEM_WIDGET_ID)["html"] = '<article class="card">New owner source</article>'
    expected = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured),
    )
    snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
    if any(node.get("semanticId") == f"{ITEM_PREFIX}:delete" for node in snapshot["nodes"]):
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId=f"{ITEM_PREFIX}:delete")
    saved = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert without_item_observation(saved) == without_item_observation(expected)


def test_html_item_targeting_single_main_card_is_editable(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = item_configuration(original)
    configuration_widget(configured, ITEM_WIDGET_ID)["html"] = (
        '<main class="card" id="first"><h2>First HTML card</h2>'
        '<p><span>Nested first label</span></p></main>'
    )
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    press_html_item(websocket_control)
    wait_for_snapshot_node(websocket_control, f"{ITEM_PREFIX}:edit")
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId=f"{ITEM_PREFIX}:delete")
    saved = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    widget = configuration_widget(saved, ITEM_WIDGET_ID)
    assert ITEM_FIRST_TITLE not in widget["html"]
    assert widget["state"]["preserved"] == "owner state"
    assert len(configuration_page(saved, FIXTURE_HOME_PAGE_ID)["widgets"]) == 1
