"""Default grid dimensions and native spacing beside both pager rails."""

from . import *


@pytest.mark.parametrize("columns,outer_padding", [(4, 0), (4, 12), (8, 12), (8, 64)])
def test_home_grid_defaults_and_pager_spacing(websocket_control, columns, outer_padding):
    configured = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_RESET),
    )
    home = configured["launcher"]["home"]
    assert home["nativeGrid"]["columns"] == 8
    assert home["nativeGrid"]["rows"] == 6
    for page in home["pages"]:
        assert all(widget["cell"]["columnSpan"] == 8 for widget in page["widgets"])

    home.pop("nativeGrid")
    normalized = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured),
    )
    assert normalized["launcher"]["home"]["nativeGrid"]["columns"] == 8
    configured = normalized
    home = configured["launcher"]["home"]
    home["nativeGrid"].update(columns=columns, outerPaddingDp=outer_padding)
    for page in home["pages"]:
        page["widgets"] = []
    page = configuration_page(configured, home["selectedPageId"])
    widget_id = "pager-spacing"
    page["widgets"] = [{
        "id": widget_id, "type": "html", "title": "Pager spacing", "enabled": True,
        "html": "<main>Full grid</main>", "css": "body{margin:0;color:white}",
        "javascript": "", "state": {},
        "style": {"margin": {"all": 0}},
        "cell": {"column": 0, "row": 0, "columnSpan": columns, "rowSpan": 6},
    }]
    saved = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured),
    )
    assert saved["launcher"]["home"]["nativeGrid"]["columns"] == columns
    snapshot = wait_for_snapshot_node(websocket_control, "widget:" + widget_id)
    widget = bounds_for(snapshot, "widget:" + widget_id)
    canvas = bounds_for(snapshot, "page:canvas")
    vertical = bounds_for(snapshot, VERTICAL_PAGE_INDICATOR_SEMANTIC_ID)
    horizontal = bounds_for(snapshot, HORIZONTAL_PAGE_INDICATOR_SEMANTIC_ID)
    controls = bounds_for(snapshot, LAUNCHER_CONTROL_SEMANTIC_ID)
    tolerance = round(2 * device_density_scale())
    assert 0 <= canvas["right"] - widget["right"] <= tolerance, (canvas, widget)
    assert 0 <= canvas["bottom"] - widget["bottom"] <= tolerance, (canvas, widget)
    assert widget["left"] - canvas["left"] >= round(outer_padding * device_density_scale())
    assert widget["top"] - canvas["top"] >= round(outer_padding * device_density_scale())
    vertical_center = (vertical["left"] + vertical["right"]) / 2
    assert abs((vertical_center - widget["right"]) - (vertical["right"] - vertical_center)) <= tolerance
    horizontal_center = (horizontal["top"] + horizontal["bottom"]) / 2
    chrome_bottom = max(horizontal["bottom"], controls["bottom"])
    assert abs((horizontal_center - widget["bottom"]) - (chrome_bottom - horizontal_center)) <= tolerance
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png",
        ARTIFACT_DIRECTORY / f"pager-spacing-{columns}-{outer_padding}.png")

    app = next(entry for entry in AUTOMATION.request(websocket_control, APP_CATALOGUE_COMMAND)["apps"]
        if entry["packageName"] == ANDROID_SETTINGS_PACKAGE)
    page["widgets"] = [{
        "id": "spacing-shortcut", "type": "app", "title": "Settings", "enabled": True,
        "component": app["component"], "displayStyle": "iconLabel",
        "cell": {"column": 0, "row": 0, "columnSpan": 1, "rowSpan": 1},
    }, {
        "id": "spacing-target", "type": "html", "title": "Last cell", "enabled": True,
        "html": "<main>Target</main>", "css": "", "javascript": "", "state": {},
        "cell": {"column": columns - 1, "row": 5, "columnSpan": 1, "rowSpan": 1},
    }]
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    snapshot = wait_for_snapshot_node(websocket_control, "widget:spacing-shortcut:move-handle")
    start = center(bounds_for(snapshot, "widget:spacing-shortcut:move-handle"))
    target = center(bounds_for(snapshot, "widget:spacing-target"))
    direct_swipe(start, target, WIDGET_MOVE_DURATION_MILLISECONDS)
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        moved = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
        source_cell = configuration_widget(moved, "spacing-shortcut")["cell"]
        target_cell = configuration_widget(moved, "spacing-target")["cell"]
        if source_cell["column"] == columns - 1 and source_cell["row"] == 5:
            break
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    else:
        raise AssertionError(f"Trailing cell did not receive the shortcut: {source_cell}")
    assert target_cell["column"] == target_cell["row"] == 0
