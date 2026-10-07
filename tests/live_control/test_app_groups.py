"""App groups built by dropping one app tile onto another on the shared emulator.

Covers the accepted path, one app tile dropped onto another becoming a group,
and the refused path, a group dropped on an app tile trading cells instead of
merging. Both run the real drag on the real device and read the saved public
configuration back through the control plane.
"""

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
GROUP_DRAG_DURATION_MILLISECONDS = 1200
WIDGET_SETTLE_SECONDS = 2.0
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
    """Drag one icon tile onto another.

    An icon tile needs no menu first. A press that moves is the drag, and a
    press that stays still is what opens the menu instead.
    """
    source = physical_description_bounds(
        WIDGET_MOVE_HANDLE_DESCRIPTION.format(source_title),
    )
    target = physical_description_bounds(
        WIDGET_MOVE_HANDLE_DESCRIPTION.format(target_title),
    )
    direct_swipe(
        center(source),
        (center(target)[0], center(source)[1]),
        GROUP_DRAG_DURATION_MILLISECONDS,
    )
    time.sleep(WIDGET_SETTLE_SECONDS)


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

    drag_widget_onto(GROUP_WIDGET_TITLE, CLOCK_TILE_TITLE)

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
