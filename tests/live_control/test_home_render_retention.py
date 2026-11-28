"""Live Home documents survive layout edits and cached page navigation."""

import base64

from . import *
from .test_app_groups import (
    app_widget, document_with_group_page, SETTINGS_COMPONENT, SETTINGS_TILE_TITLE,
    FIRST_APP_WIDGET_ID, GROUP_PAGE_ID,
)

DOCUMENT_ID = "retained-document"
SECOND_DOCUMENT_ID = "retained-neighbor"
EDITED_WIDGET_ID = "retained-edit-target"
NEIGHBOR_PAGE_ID = "retained-neighbor-page"
INITIAL_TEXT = "Live document ready"
UNSAVED_TEXT = "Unsaved document must survive"
SECOND_UNSAVED_TEXT = "Neighbor unsaved document survives"
REPLACEMENT_TEXT = "Edited source loaded"
SETTLE_SECONDS = 1.0
LOCK_LANDSCAPE = "cmd window user-rotation lock 1"
LOCK_PORTRAIT = "cmd window user-rotation lock 0"


def retained_widget(widget_id=DOCUMENT_ID):
    return {
        "id": widget_id, "title": widget_id, "type": "html",
        "html": f'<p id="retained">{INITIAL_TEXT}</p>',
        "css": "body{margin:0;background:#162337;color:white;font:18px sans-serif}p{padding:18px}",
        "javascript": "window.addEventListener('dikciz-state',()=>{document.querySelector('#retained').textContent='Unexpected state delivery'});",
        "state": {}, "enabled": True,
        "cell": {"column": 0, "row": 2, "columnSpan": 4, "rowSpan": 1},
    }


def install_retention_home(connection, document_row_span=1):
    original = current_configuration(connection)
    configured = document_with_group_page(original, [
        app_widget(FIRST_APP_WIDGET_ID, SETTINGS_TILE_TITLE, SETTINGS_COMPONENT, 0),
        retained_widget(),
    ])
    configured["launcher"]["home"]["pages"].append({
        "id": NEIGHBOR_PAGE_ID, "title": "Neighbor",
        "position": {"column": 1, "row": 0}, "locked": False,
        "widgets": [retained_widget(SECOND_DOCUMENT_ID)],
    })
    configuration_widget(configured, DOCUMENT_ID)["cell"]["rowSpan"] = document_row_span
    AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    wait_for_physical_text_bounds(INITIAL_TEXT)


def patch_unsaved_dom(connection, widget_id=DOCUMENT_ID, text=UNSAVED_TEXT, column=1):
    address = f"{column}H1V-{widget_id}"
    result = AUTOMATION.request(
        connection, AUTOMATION.TYPE_AUTOMATION_DISPATCH,
        sourceWidgetAddress=address,
        actions=[{"type": "patchDom", "widgetAddress": address,
                  "selector": "#retained", "values": {"text": text}}],
    )
    assert result["actions"] == [{"type": "patchDom", "outcome": "executed"}], result
    wait_for_physical_text_bounds(text)


def capture_retention(name):
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / name)


def test_home_render_retention_icon_drop_keeps_unsaved_dom(websocket_control):
    exercise_icon_drop_retention(websocket_control)


def exercise_icon_drop_retention(websocket_control, document_row_span=1):
    install_retention_home(websocket_control, document_row_span)
    patch_unsaved_dom(websocket_control)
    capture_retention("home-retention-before.png")
    device_command(f"dumpsys gfxinfo {DIKCIZ_DEBUG_PACKAGE} reset")
    for column in (2, 1):
        snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
        source = center(AUTOMATION.require_node(snapshot, "widget:" + FIRST_APP_WIDGET_ID)["bounds"])
        current = configuration_widget(current_configuration(websocket_control), FIRST_APP_WIDGET_ID)
        pitch, _ = native_grid_cell_pitch()
        target = (source[0] + (column - current["cell"]["column"]) * pitch, source[1])
        direct_hold_then_drag(source, target)
        time.sleep(SETTLE_SECONDS)
        (ARTIFACT_DIRECTORY / "home-retention-drop-framestats.txt").write_text(
            device_command(f"dumpsys gfxinfo {DIKCIZ_DEBUG_PACKAGE} framestats"), encoding="utf-8",
        )
        capture_retention("home-retention-after-drop.png")
        assert configuration_widget(current_configuration(websocket_control), FIRST_APP_WIDGET_ID)["cell"]["column"] == column
        wait_for_physical_text_bounds(UNSAVED_TEXT)
    (ARTIFACT_DIRECTORY / "home-retention-drop-framestats.txt").write_text(
        device_command(f"dumpsys gfxinfo {DIKCIZ_DEBUG_PACKAGE} framestats"), encoding="utf-8",
    )
    capture_retention("home-retention-after-drop.png")


def test_home_render_retention_page_roundtrips_keep_both_documents(websocket_control):
    install_retention_home(websocket_control)
    patch_unsaved_dom(websocket_control)
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SELECT_PAGE, pageId=NEIGHBOR_PAGE_ID)
    wait_for_physical_text_bounds(INITIAL_TEXT)
    patch_unsaved_dom(websocket_control, SECOND_DOCUMENT_ID, SECOND_UNSAVED_TEXT, 2)
    latencies = []
    device_command(f"dumpsys gfxinfo {DIKCIZ_DEBUG_PACKAGE} reset")
    for page_id, text in ((GROUP_PAGE_ID, UNSAVED_TEXT), (NEIGHBOR_PAGE_ID, SECOND_UNSAVED_TEXT), (GROUP_PAGE_ID, UNSAVED_TEXT)):
        started = time.monotonic()
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SELECT_PAGE, pageId=page_id)
        latencies.append(time.monotonic() - started)
        wait_for_selected_page(websocket_control, page_id)
        wait_for_physical_text_bounds(text)
    capture_retention("home-retention-after-navigation.png")
    (ARTIFACT_DIRECTORY / "home-retention-navigation.json").write_text(json.dumps(latencies), encoding="utf-8")
    (ARTIFACT_DIRECTORY / "home-retention-navigation-framestats.txt").write_text(
        device_command(f"dumpsys gfxinfo {DIKCIZ_DEBUG_PACKAGE} framestats"), encoding="utf-8",
    )


@pytest.mark.parametrize("entrypoint", ["configReplace", "fileWrite"])
def test_home_render_retention_source_edit_reloads_only_changed_widget(websocket_control, entrypoint):
    install_retention_home(websocket_control)
    original = current_configuration(websocket_control)
    sibling = retained_widget(EDITED_WIDGET_ID)
    sibling["cell"]["row"] = 3
    configuration_page(original, GROUP_PAGE_ID)["widgets"].append(sibling)
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=original)
    patch_unsaved_dom(websocket_control)
    if entrypoint == "configReplace":
        changed = current_configuration(websocket_control)
        configuration_widget(changed, EDITED_WIDGET_ID)["html"] = f'<p>{REPLACEMENT_TEXT}</p>'
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=changed)
    else:
        path = f"/sdcard/Dikciz/pages/{GROUP_PAGE_ID}/widgets/{EDITED_WIDGET_ID}.json"
        saved = AUTOMATION.request(websocket_control, "fileRead", path=path)
        changed = json.loads(base64.b64decode(saved["dataBase64"]))
        changed["html"] = f'<p>{REPLACEMENT_TEXT}</p>'
        AUTOMATION.request(websocket_control, "fileWrite", path=path, overwrite=True,
                           dataBase64=base64.b64encode(json.dumps(changed).encode()).decode())
    wait_for_physical_text_bounds(REPLACEMENT_TEXT)
    wait_for_physical_text_bounds(UNSAVED_TEXT)


def test_home_render_retention_moved_html_keeps_dom_and_current_drag_cell(websocket_control):
    install_retention_home(websocket_control)
    patch_unsaved_dom(websocket_control)
    for row in (3, 2):
        source = center(physical_description_bounds(WIDGET_MOVE_HANDLE_DESCRIPTION.format(DOCUMENT_ID)))
        current = configuration_widget(current_configuration(websocket_control), DOCUMENT_ID)
        _, pitch = native_grid_cell_pitch()
        target = (source[0], source[1] + (row - current["cell"]["row"]) * pitch)
        direct_swipe(source, target)
        time.sleep(SETTLE_SECONDS)
        assert configuration_widget(current_configuration(websocket_control), DOCUMENT_ID)["cell"]["row"] == row
        wait_for_physical_text_bounds(UNSAVED_TEXT)



def test_home_render_retention_rapid_selection_persists_the_last_page(websocket_control):
    install_retention_home(websocket_control)
    patch_unsaved_dom(websocket_control)
    for page_id in (NEIGHBOR_PAGE_ID, GROUP_PAGE_ID, NEIGHBOR_PAGE_ID, GROUP_PAGE_ID):
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SELECT_PAGE, pageId=page_id)
    deadline = time.monotonic() + PROVIDER_RENDER_TIMEOUT_SECONDS
    selected = None
    while time.monotonic() < deadline:
        saved = AUTOMATION.request(websocket_control, "fileRead", path="/sdcard/Dikciz/config.json")
        manifest = json.loads(base64.b64decode(saved["dataBase64"]))
        selected = manifest["launcher"]["home"]["selectedPageId"]
        if selected == GROUP_PAGE_ID:
            break
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    assert selected == GROUP_PAGE_ID
    wait_for_selected_page(websocket_control, GROUP_PAGE_ID)
    wait_for_physical_text_bounds(UNSAVED_TEXT)



def set_retention_font_scale(percent):
    emulator_font_scale_set(percent)
    deadline = time.monotonic() + DEVICE_UI_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        configuration = device_command("dumpsys window")
        match = re.search(r"mGlobalConfiguration=\{([0-9.]+)\s", configuration)
        if match and round(float(match.group(1)) * 100) == percent:
            return
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"Android did not apply font scale {percent}")


def test_home_render_retention_large_font_landscape():
    try:
        device_command(LOCK_LANDSCAPE)
        wait_for_page_viewport_orientation(EMULATOR_DEVICE_ORIENTATION_LANDSCAPE)
        # Apply one system configuration change at a time. A font change can reapply the previous angle.
        set_retention_font_scale(EMULATOR_DEVICE_FONT_SCALE_PERCENT_ENLARGED)
        wait_for_page_viewport_orientation(EMULATOR_DEVICE_ORIENTATION_LANDSCAPE)
        with connected_websocket_control() as connection:
            exercise_icon_drop_retention(connection, document_row_span=3)
            capture_retention("home-retention-landscape-large-font.png")
    finally:
        try:
            device_command(LOCK_PORTRAIT)
            wait_for_page_viewport_orientation(EMULATOR_DEVICE_ORIENTATION_PORTRAIT)
        finally:
            set_retention_font_scale(EMULATOR_DEVICE_FONT_SCALE_PERCENT_BASELINE)
        wait_for_page_viewport_orientation(EMULATOR_DEVICE_ORIENTATION_PORTRAIT)



@pytest.mark.parametrize("widget_id", [DOCUMENT_ID, FIRST_APP_WIDGET_ID])
def test_home_render_retention_resize_clears_controls_without_reloading(websocket_control, widget_id):
    install_retention_home(websocket_control)
    patch_unsaved_dom(websocket_control)
    AUTOMATION.request(websocket_control, "longPress", semanticId=f"widget:{widget_id}:move-handle")
    wait_for_snapshot_node(websocket_control, f"widget:{widget_id}:resize")
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId=f"widget:{widget_id}:resize")
    fill_id = f"widget:{widget_id}:resize-fill-height"
    wait_for_snapshot_node(websocket_control, fill_id)
    snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
    bounds = AUTOMATION.require_node(snapshot, "widget:" + widget_id)["bounds"]
    resize_start = resize_handle_coordinate(bounds, "end", "end")
    pitch, vertical_pitch = native_grid_cell_pitch()
    direct_swipe(resize_start, (resize_start[0], resize_start[1] + vertical_pitch))
    snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
    icon = center(AUTOMATION.require_node(snapshot, "widget:" + FIRST_APP_WIDGET_ID)["bounds"])
    direct_tap_at(icon[0] + 3 * pitch, icon[1])
    time.sleep(SETTLE_SECONDS)
    saved = configuration_widget(current_configuration(websocket_control), widget_id)
    assert saved["cell"]["rowSpan"] > 1
    wait_for_snapshot_node(websocket_control, fill_id, should_exist=False)
    wait_for_physical_text_bounds(UNSAVED_TEXT)
    source = center(physical_description_bounds(WIDGET_MOVE_HANDLE_DESCRIPTION.format(SETTINGS_TILE_TITLE)))
    direct_hold_then_drag(source, (source[0] + pitch, source[1]))
    time.sleep(SETTLE_SECONDS)
    assert configuration_widget(current_configuration(websocket_control), FIRST_APP_WIDGET_ID)["cell"]["column"] == 1
    wait_for_physical_text_bounds(UNSAVED_TEXT)
    capture_retention(f"home-retention-resized-{widget_id}.png")
