"""Saved HTML cards, portable presets and a single starter document."""

from . import *
from .test_shared_vm_starter import starter_reset

DASHBOARD_ID = "home-dashboard"
DASHBOARD_PREFIX = "widget:home-dashboard"
CUSTOM_PRESET_ID = "saved-note-fixture"
CUSTOM_PRESET_TITLE = "Saved note fixture"
PROBE = """
window.addEventListener("dikciz-ready", async () => {
    await dikciz.command("appCatalogue");
    await new Promise(requestAnimationFrame);
    const card = document.querySelector('[data-dikciz-preset="quick-note"] h2');
    const bounds = card.getBoundingClientRect();
    selfWidget.patchState({...selfWidget.state, presetProbe: {
        x:bounds.x + bounds.width / 2, y:bounds.y + bounds.height / 2,
        width:innerWidth
    }}).catch(error => { document.body.setAttribute("data-probe-error", error.code || "failed"); });
});
"""


@pytest.fixture(autouse=True)
def custom_preset_evidence(websocket_control, isolated_launcher_fixture, request):
    yield
    if "save_custom" not in request.node.name:
        return
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "custom-preset-screen.png")
    run_device_operation("uiautomator-dump")
    shutil.copyfile(ARTIFACT_DIRECTORY / "window.xml", ARTIFACT_DIRECTORY / "custom-preset-window.xml")
    (ARTIFACT_DIRECTORY / "custom-preset-config.json").write_text(
        json.dumps(AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))),
        encoding="utf-8",
    )


def current_dashboard(connection):
    configuration = AUTOMATION.require_configuration(
        AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
    )
    return configuration_widget(configuration, DASHBOARD_ID)


def open_note_menu(connection):
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        probe = current_dashboard(connection).get("state", {}).get("presetProbe")
        if probe:
            break
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    else:
        raise AssertionError("Starter card did not render its probe")
    snapshot = AUTOMATION.request(connection, AUTOMATION.TYPE_SNAPSHOT)
    bounds = bounds_for(snapshot, DASHBOARD_PREFIX)
    scale = (bounds["right"] - bounds["left"]) / probe["width"]
    direct_long_press_at(round(bounds["left"] + probe["x"] * scale), round(bounds["top"] + probe["y"] * scale))
    return wait_for_snapshot_node(connection, DASHBOARD_PREFIX + ":html-item:save-preset")


def prepare_dashboard(connection):
    configuration = starter_reset(connection)
    widget = configuration_widget(configuration, DASHBOARD_ID)
    assert len(configuration_page(configuration, "home")["widgets"]) == 1
    assert widget["cell"] == {"column":0, "row":0, "columnSpan":4, "rowSpan":6}
    configuration["limits"]["maxTextCharacters"] = 65536
    widget["javascript"] += PROBE
    AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_REPLACE, config=configuration)
    restore_system_home()
    wait_for_physical_text_bounds("Quick note")


def add_card_preset(connection, preset_id):
    AUTOMATION.request(connection, AUTOMATION.TYPE_LONG_PRESS, semanticId=DASHBOARD_PREFIX + ":move-handle")
    wait_for_snapshot_node(connection, DASHBOARD_PREFIX + ":add-block")
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=DASHBOARD_PREFIX + ":add-block")
    wait_for_snapshot_node(connection, DASHBOARD_PREFIX + ":add-block:" + preset_id)
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=DASHBOARD_PREFIX + ":add-block:" + preset_id)
    wait_for_snapshot_node(connection, "html-block:confirm")
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId="html-block:confirm")


def test_html_card_presets_save_custom_and_insert_independent_note(websocket_control):
    prepare_dashboard(websocket_control)
    open_note_menu(websocket_control)
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId=DASHBOARD_PREFIX + ":html-item:save-preset")
    prefix = DASHBOARD_PREFIX + ":html-item:preset"
    wait_for_snapshot_node(websocket_control, prefix + ":id")
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SET_TEXT, semanticId=prefix + ":title", text=CUSTOM_PRESET_TITLE)
    for invalid_id in ("../invalid", "", "quick-note", "x" * 81):
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SET_TEXT, semanticId=prefix + ":id", text=invalid_id)
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId=prefix + ":save")
        wait_for_snapshot_node(websocket_control, prefix + ":save")
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SET_TEXT, semanticId=prefix + ":id", text=CUSTOM_PRESET_ID)
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId=prefix + ":save")
    wait_for_snapshot_node(websocket_control, prefix + ":save", should_exist=False)
    assert len(current_dashboard(websocket_control)["state"]) == 1
    add_card_preset(websocket_control, CUSTOM_PRESET_ID)
    widget = current_dashboard(websocket_control)
    assert widget["html"].count('data-dikciz-preset="quick-note"') == 2
    assert widget["html"].count('data-dikciz-item-id="quick-note"') == 1
    assert "presetProbe" in widget["state"]
    # The append scrolls to the new card. Its controls run in the original document.
    note_bounds = physical_bounds_from_nodes(list(reversed(physical_nodes())), lambda node: node.get("class") == "android.widget.EditText" and node.get("hint") == "Quick note")
    direct_enter_text(note_bounds, "independent_note")
    direct_hide_keyboard()
    save_bounds = physical_bounds_from_nodes(list(reversed(physical_nodes())), lambda node: node.get("class") == "android.widget.Button" and node.get("text") == "Save note")
    direct_tap(save_bounds)
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        state = current_dashboard(websocket_control)["itemStates"]
        notes = {key:value for key,value in state.items() if key.startswith("block-")}
        if notes and next(iter(notes.values())).get("text") == "independent_note":
            break
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    else:
        raise AssertionError(f"New note preset did not save independently: {state!r}")
    assert state["quick-note"] == {}
    assert "presetProbe" in current_dashboard(websocket_control)["state"]
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "html-card-presets-custom.png")
    run_device_operation("uiautomator-dump")
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SELECT_PAGE, pageId="insights")
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SELECT_PAGE, pageId="home")
    assert current_dashboard(websocket_control)["itemStates"] == state


@pytest.mark.parametrize("preset_id", ["command-deck", "quick-apps", "quick-note"])
def test_html_card_presets_bundled_cards_are_ready_to_add(websocket_control, preset_id):
    starter_reset(websocket_control)
    add_card_preset(websocket_control, preset_id)
    widget = current_dashboard(websocket_control)
    assert widget["html"].count('data-dikciz-preset="' + preset_id + '"') == 2
    assert "<!-- dikciz:block " + preset_id in widget["html"]
    assert len(widget["state"]) == 0


@pytest.mark.parametrize("protocol", ["websocket", "mcp", "http"])
def test_html_card_presets_native_intent_uri_is_shared_and_validated(websocket_control, protocol, monkeypatch):
    from . import test_remote_device_control as remote

    monkeypatch.setitem(remote.TOOLS, "intent", "dikciz_intent")
    arguments = {"intentType":"broadcast", "action":"eu.psyb0t.dikciz.fixture.PRESET_URI"}
    # A private test broadcast exercises native URI dispatch without opening a browser.
    for uri in ("https://example.invalid/?q=fixture%20search", "x:" + "a" * 2046):
        result = remote.remote_call(protocol, "intent", {**arguments, "data":uri})
        assert result["dispatched"] is True
    for invalid in ("", "relative/path", "https://example.invalid/\n", "x:" + "a" * 2047):
        remote.remote_call(protocol, "intent", {**arguments, "data":invalid}, error="validation_failed")
