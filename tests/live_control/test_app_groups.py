"""App groups built by dropping one app tile onto another on the shared emulator.

Covers the accepted path, one app tile dropped onto another becoming a group,
and the refused path, a group dropped on an app tile trading cells instead of
merging. Both run the real drag on the real device and read the saved public
configuration back through the control plane.
"""

import base64

from . import *


GROUP_PAGE_ID = "groups"
GROUP_PAGE_TITLE = "Groups"
FIRST_APP_WIDGET_ID = "settings-tile"
SECOND_APP_WIDGET_ID = "clock-tile"
GROUP_WIDGET_ID = "tools-group"
GROUP_WIDGET_TITLE = "Tools"
SETTINGS_TILE_TITLE = "Settings"
CLOCK_TILE_TITLE = "Clock"
SETTINGS_COMPONENT = "com.android.settings/.Settings"
CLOCK_COMPONENT = "com.google.android.deskclock/com.android.deskclock.DeskClock"
APP_WIDGET_TYPE_NAME = "app"
APP_GROUP_WIDGET_TYPE_NAME = "appGroup"
WIDGET_SETTLE_SECONDS = 2.0
ANDROID_DEFAULT_HOLD_SECONDS = 0.65
QUICK_SWIPE_MILLISECONDS = 120
FOLDER_HOVER_SECONDS = 0.5
FIRST_COLUMN = 0
SECOND_COLUMN = 1


def app_widget(widget_id: str, title: str, component: str, column: int) -> dict[str, Any]:
    return {
        "id": widget_id,
        "title": title,
        "enabled": True,
        "locked": False,
        "cell": {"column": column, "row": 0, "columnSpan": 1, "rowSpan": 1},
        "type": APP_WIDGET_TYPE_NAME,
        "component": component,
        "displayStyle": "iconLabel",
    }


def app_group_widget(column: int) -> dict[str, Any]:
    return {
        "id": GROUP_WIDGET_ID,
        "title": GROUP_WIDGET_TITLE,
        "enabled": True,
        "locked": False,
        "cell": {"column": column, "row": 0, "columnSpan": 1, "rowSpan": 1},
        "type": APP_GROUP_WIDGET_TYPE_NAME,
        "components": [SETTINGS_COMPONENT, CLOCK_COMPONENT],
    }


def document_with_group_page(
    original: dict[str, Any],
    widgets: list[dict[str, Any]],
) -> dict[str, Any]:
    """A single-page document holding only the widgets this test drags."""
    document = json.loads(json.dumps(original))
    home = document["launcher"]["home"]
    home["pages"] = [
        {
            "id": GROUP_PAGE_ID,
            "title": GROUP_PAGE_TITLE,
            "position": {"column": 0, "row": 0},
            "locked": False,
            "widgets": widgets,
        },
    ]
    home["selectedPageId"] = GROUP_PAGE_ID
    home["homePageId"] = GROUP_PAGE_ID
    return document


def apply_group_page(
    connection: socket.socket,
    widgets: list[dict[str, Any]],
) -> None:
    original = current_configuration(connection)
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=document_with_group_page(original, widgets),
    )
    wait_for_selected_page(connection, GROUP_PAGE_ID)


def group_page_widgets(connection: socket.socket) -> list[dict[str, Any]]:
    page = configuration_page(current_configuration(connection), GROUP_PAGE_ID)
    widgets = page["widgets"]
    assert isinstance(widgets, list)
    return widgets


def drag_widget_onto(source_title: str, target_title: str) -> None:
    """Hold an icon to arm movement, then drop it on another tile."""
    source = physical_description_bounds(
        WIDGET_MOVE_HANDLE_DESCRIPTION.format(source_title),
    )
    target = physical_description_bounds(
        WIDGET_MOVE_HANDLE_DESCRIPTION.format(target_title),
    )
    direct_hold_then_drag(
        center(source),
        (center(target)[0], center(source)[1]),
        hold_seconds=ANDROID_DEFAULT_HOLD_SECONDS,
        hover_seconds=FOLDER_HOVER_SECONDS,
    )
    time.sleep(WIDGET_SETTLE_SECONDS)


def test_app_groups_drag_tracks_subcell_motion_and_settles_without_saving_a_move(
    websocket_control: socket.socket,
) -> None:
    apply_group_page(
        websocket_control,
        [app_widget(FIRST_APP_WIDGET_ID, SETTINGS_TILE_TITLE, SETTINGS_COMPONENT, FIRST_COLUMN)],
    )
    original = group_page_widgets(websocket_control)
    semantic_id = "widget:" + FIRST_APP_WIDGET_ID
    snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
    original_bounds = AUTOMATION.require_node(snapshot, semantic_id)["bounds"]
    start_x, start_y = center(original_bounds)
    pitch_x, pitch_y = native_grid_cell_pitch()
    delta_x, delta_y = pitch_x // 3, pitch_y // 3
    end_x, end_y = start_x + delta_x, start_y + delta_y
    device_command(
        f"input motionevent DOWN {start_x} {start_y} && sleep {ANDROID_DEFAULT_HOLD_SECONDS} "
        f"&& input motionevent MOVE {end_x} {end_y}"
    )
    try:
        snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
        moved_bounds = AUTOMATION.require_node(snapshot, semantic_id)["bounds"]
        moved_x, moved_y = center(moved_bounds)
        assert abs(moved_x - start_x - delta_x) <= 2, moved_bounds
        assert abs(moved_y - start_y - delta_y) <= 2, moved_bounds
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "grid-motion-subcell.png")
    finally:
        device_command(f"input motionevent UP {end_x} {end_y}")
    time.sleep(WIDGET_SETTLE_SECONDS)
    assert group_page_widgets(websocket_control) == original
    snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
    assert AUTOMATION.require_node(snapshot, semantic_id)["bounds"] == original_bounds


def test_dropping_an_app_tile_on_another_builds_one_group(
    websocket_control: socket.socket,
) -> None:
    apply_group_page(
        websocket_control,
        [
            app_widget(FIRST_APP_WIDGET_ID, SETTINGS_TILE_TITLE, SETTINGS_COMPONENT, FIRST_COLUMN),
            app_widget(SECOND_APP_WIDGET_ID, CLOCK_TILE_TITLE, CLOCK_COMPONENT, SECOND_COLUMN),
        ],
    )

    original = group_page_widgets(websocket_control)
    source = center(physical_description_bounds(
        WIDGET_MOVE_HANDLE_DESCRIPTION.format(CLOCK_TILE_TITLE),
    ))
    target = center(physical_description_bounds(
        WIDGET_MOVE_HANDLE_DESCRIPTION.format(SETTINGS_TILE_TITLE),
    ))
    direct_swipe(source, target, QUICK_SWIPE_MILLISECONDS)
    assert group_page_widgets(websocket_control) == original

    direct_hold_then_drag(source, target, hold_seconds=ANDROID_DEFAULT_HOLD_SECONDS)
    assert group_page_widgets(websocket_control) == original

    drag_widget_onto(CLOCK_TILE_TITLE, SETTINGS_TILE_TITLE)

    widgets = group_page_widgets(websocket_control)
    assert len(widgets) == 1, widgets
    group = widgets[0]
    assert group["type"] == APP_GROUP_WIDGET_TYPE_NAME, group
    # The target keeps its cell, and both apps are members in drop order.
    assert group["cell"]["column"] == FIRST_COLUMN, group
    assert group["components"] == [SETTINGS_COMPONENT, CLOCK_COMPONENT], group


def test_dropping_a_group_on_an_app_tile_trades_their_cells(
    websocket_control: socket.socket,
) -> None:
    apply_group_page(
        websocket_control,
        [
            app_group_widget(FIRST_COLUMN),
            app_widget(SECOND_APP_WIDGET_ID, CLOCK_TILE_TITLE, CLOCK_COMPONENT, SECOND_COLUMN),
        ],
    )

    device_command(f"dumpsys gfxinfo {DIKCIZ_DEBUG_PACKAGE} reset")
    drag_widget_onto(GROUP_WIDGET_TITLE, CLOCK_TILE_TITLE)
    (ARTIFACT_DIRECTORY / "grid-motion-rendering.txt").write_text(
        device_command(f"dumpsys gfxinfo {DIKCIZ_DEBUG_PACKAGE} framestats"),
        encoding="utf-8",
    )

    # A group never merges into an app tile. Both widgets survive, swapped.
    widgets = group_page_widgets(websocket_control)
    assert len(widgets) == 2, widgets
    columns = {widget["id"]: widget["cell"]["column"] for widget in widgets}
    assert columns[GROUP_WIDGET_ID] == SECOND_COLUMN, widgets
    assert columns[SECOND_APP_WIDGET_ID] == FIRST_COLUMN, widgets
    group = next(widget for widget in widgets if widget["id"] == GROUP_WIDGET_ID)
    assert group["components"] == [SETTINGS_COMPONENT, CLOCK_COMPONENT], group


def test_tapping_an_app_tile_launches_its_app(
    websocket_control: socket.socket,
) -> None:
    """A tap anywhere on an icon tile launches it.

    The whole tile is the drag zone, and the activity resolves the release
    before the tile sees it, so the launch runs from the gesture rather than
    from the tile's own click listener. Nothing covered this, which is how a
    tile that could no longer be tapped once shipped.
    """
    apply_group_page(
        websocket_control,
        [
            app_widget(FIRST_APP_WIDGET_ID, SETTINGS_TILE_TITLE, SETTINGS_COMPONENT, FIRST_COLUMN),
        ],
    )

    tile = physical_description_bounds(
        WIDGET_MOVE_HANDLE_DESCRIPTION.format(SETTINGS_TILE_TITLE),
    )
    direct_tap_at(*center(tile))

    wait_for_foreground_application(ANDROID_SETTINGS_PACKAGE)
    restore_system_home()
    wait_for_selected_page(websocket_control, GROUP_PAGE_ID)


def test_launcher_touch_drags_folder_member_to_an_empty_home_cell(
    websocket_control: socket.socket,
) -> None:
    apply_group_page(
        websocket_control,
        [
            app_group_widget(FIRST_COLUMN),
            app_widget(SECOND_APP_WIDGET_ID, CLOCK_TILE_TITLE, CLOCK_COMPONENT, SECOND_COLUMN),
        ],
    )
    original = group_page_widgets(websocket_control)
    occupied = center(physical_description_bounds(
        WIDGET_MOVE_HANDLE_DESCRIPTION.format(CLOCK_TILE_TITLE),
    ))
    pitch, _ = native_grid_cell_pitch()
    empty = (occupied[0] + pitch, occupied[1])

    direct_tap_at(*center(physical_description_bounds(
        WIDGET_MOVE_HANDLE_DESCRIPTION.format(GROUP_WIDGET_TITLE),
    )))
    member = wait_for_physical_description_bounds(SETTINGS_TILE_TITLE)
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "folder-drag-before.png")
    direct_hold_then_drag(center(member), occupied)
    assert group_page_widgets(websocket_control) == original

    member = wait_for_physical_description_bounds(SETTINGS_TILE_TITLE)
    start_x, start_y = center(member)
    target_x, target_y = empty
    device_command(f"input motionevent DOWN {start_x} {start_y} && sleep 1.1 && input motionevent MOVE {target_x} {target_y}")
    try:
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "folder-drag-in-progress.png")
    finally:
        device_command(f"input motionevent UP {target_x} {target_y}")
    wait_for_physical_description_bounds(WIDGET_MOVE_HANDLE_DESCRIPTION.format(SETTINGS_TILE_TITLE))
    by_id = {widget["id"]: widget for widget in group_page_widgets(websocket_control)}
    assert len(by_id) == 3, by_id
    assert by_id[GROUP_WIDGET_ID]["type"] == APP_WIDGET_TYPE_NAME
    assert by_id[GROUP_WIDGET_ID]["component"] == CLOCK_COMPONENT
    assert by_id[GROUP_WIDGET_ID]["title"] == CLOCK_TILE_TITLE
    assert by_id[GROUP_WIDGET_ID]["cell"]["column"] == FIRST_COLUMN
    assert by_id[SECOND_APP_WIDGET_ID] == original[1]
    extracted = next(widget for widget in by_id.values() if widget["component"] == SETTINGS_COMPONENT)
    assert extracted["cell"] == {"column": SECOND_COLUMN + 1, "row": 0, "columnSpan": 1, "rowSpan": 1}
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "folder-drag-after.png")

    apply_group_page(websocket_control, [app_group_widget(FIRST_COLUMN)])
    direct_tap_at(*center(physical_description_bounds(
        WIDGET_MOVE_HANDLE_DESCRIPTION.format(GROUP_WIDGET_TITLE),
    )))
    direct_long_press(wait_for_physical_description_bounds(SETTINGS_TILE_TITLE))
    direct_tap(wait_for_physical_text_bounds("Move to page"))
    wait_for_physical_description_bounds(WIDGET_MOVE_HANDLE_DESCRIPTION.format(SETTINGS_TILE_TITLE))
    extracted = next(widget for widget in group_page_widgets(websocket_control) if widget["component"] == SETTINGS_COMPONENT)
    assert extracted["cell"]["column"] == SECOND_COLUMN and extracted["cell"]["row"] == 0


def test_app_groups_continuous_drag_persists_the_landing_cell(websocket_control):
    apply_group_page(
        websocket_control,
        [app_widget(FIRST_APP_WIDGET_ID, SETTINGS_TILE_TITLE, SETTINGS_COMPONENT, FIRST_COLUMN)],
    )
    source = center(physical_description_bounds(
        WIDGET_MOVE_HANDLE_DESCRIPTION.format(SETTINGS_TILE_TITLE),
    ))
    pitch_x, _ = native_grid_cell_pitch()
    target_column = 3
    target_x = source[0] + target_column * pitch_x
    device_command(f"dumpsys gfxinfo {DIKCIZ_DEBUG_PACKAGE} reset")
    device_command(f"input draganddrop {source[0]} {source[1]} {target_x} {source[1]} 1200")
    time.sleep(WIDGET_SETTLE_SECONDS)
    widgets = group_page_widgets(websocket_control)
    assert len(widgets) == 1
    assert widgets[0]["cell"]["column"] == target_column, widgets
    assert widgets[0]["cell"]["row"] == 0, widgets
    (ARTIFACT_DIRECTORY / "grid-motion-continuous-rendering.txt").write_text(
        device_command(f"dumpsys gfxinfo {DIKCIZ_DEBUG_PACKAGE} framestats"),
        encoding="utf-8",
    )


def test_app_groups_drop_rejects_malformed_storage_without_overwriting_it(
    websocket_control: socket.socket,
) -> None:
    apply_group_page(
        websocket_control,
        [app_widget(FIRST_APP_WIDGET_ID, SETTINGS_TILE_TITLE, SETTINGS_COMPONENT, FIRST_COLUMN)],
    )
    valid = current_configuration(websocket_control)
    semantic_id = "widget:" + FIRST_APP_WIDGET_ID
    original_bounds = AUTOMATION.require_node(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT), semantic_id,
    )["bounds"]
    source_x, source_y = center(physical_description_bounds(
        WIDGET_MOVE_HANDLE_DESCRIPTION.format(SETTINGS_TILE_TITLE),
    ))
    pitch_x, _ = native_grid_cell_pitch()
    target_x = source_x + 3 * pitch_x
    path = f"/sdcard/Dikciz/pages/{GROUP_PAGE_ID}/widgets/{FIRST_APP_WIDGET_ID}.json"
    try:
        device_command(
            f"input motionevent DOWN {source_x} {source_y} "
            f"&& sleep {ANDROID_DEFAULT_HOLD_SECONDS} "
            f"&& input motionevent MOVE {target_x} {source_y} "
            f"&& printf '{{' > {path} "
            f"&& input motionevent UP {target_x} {source_y}"
        )
        time.sleep(WIDGET_SETTLE_SECONDS)
        raw = AUTOMATION.request(websocket_control, "fileRead", path=path)
        assert base64.b64decode(raw["dataBase64"]) == b"{", raw
        snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
        assert AUTOMATION.require_node(snapshot, semantic_id)["bounds"] == original_bounds
    finally:
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=valid)
    assert group_page_widgets(websocket_control)[0]["cell"]["column"] == FIRST_COLUMN
