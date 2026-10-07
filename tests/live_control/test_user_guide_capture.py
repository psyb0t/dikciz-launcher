"""Walk the user guide on the shared emulator and capture its screenshots.

Every image in `docs/user-guide.md` is produced here, by performing the step the
guide describes on a real device and photographing the result. A screenshot can
therefore never drift from the prose beside it: when a documented step stops
working, this walk fails instead of quietly shipping a stale picture.

Run it through `make dikciz-guide-capture`. The walk starts from the bundled
starter home rather than the UI test fixture, because the guide documents the
product a new owner actually sees.
"""

import json
import os
import shutil

from . import *
from .script_status import wait_script_result
from .test_alarm_cadence import CLOCK_TITLE, clock_notification


# The forward container mounts the workspace read-only, so the walk writes into
# the artifacts volume and `make dikciz-guide-capture` copies the result into
# docs/images/user-guide afterwards.
GUIDE_IMAGE_DIRECTORY = ARTIFACT_DIRECTORY / "user-guide"
GUIDE_CAPTURE_ENVIRONMENT_VARIABLE = "DIKCIZ_GUIDE_CAPTURE"
GUIDE_CAPTURE_ENABLED_VALUE = "true"
GUIDE_SETTLE_SECONDS = 1.5
WIDGET_SHEET_OPEN_ATTEMPTS = 3
GUIDE_DRAG_DURATION_MILLISECONDS = 1200
GUIDE_CLOCK_WIDGET_ID = "guide-clock"
GUIDE_CLOCK_TITLE = "Clock"
GUIDE_CLOCK_COMPONENT = "com.google.android.deskclock/com.android.deskclock.DeskClock"
SETTINGS_SCROLL_ATTEMPTS = 12
SETTINGS_SCROLL_EDGE_INSET_PIXELS = 40
SETTINGS_SCROLL_TRAVEL_PIXELS = 1100
SETTINGS_SCROLL_VIEW_CLASS = "android.widget.ScrollView"
SCREENSHOT_ARTIFACT_NAME = "screenshot.png"
DECK_MOVE_ACTION_SEMANTIC_ID = f"widget:{STARTER_HOME_WIDGET_ID}:move"
DECK_RESIZE_ACTION_SEMANTIC_ID = f"widget:{STARTER_HOME_WIDGET_ID}:resize"
NOTES_APP_TILE_WIDGET_ID = "android-settings"
NOTES_SCRIPT_DASHBOARD_WIDGET_ID = "script-dashboard"
TILE_MOVE_HANDLE_SEMANTIC_ID = f"widget:{NOTES_APP_TILE_WIDGET_ID}:move-handle"
TILE_MOVE_ACTION_SEMANTIC_ID = f"widget:{NOTES_APP_TILE_WIDGET_ID}:move"
TILE_RESIZE_ACTION_SEMANTIC_ID = f"widget:{NOTES_APP_TILE_WIDGET_ID}:resize"
STARTER_WORKBENCH_PAGE_ID = "activity"
DECK_EDIT_ACTION_SEMANTIC_ID = f"widget:{STARTER_HOME_WIDGET_ID}:edit"
DECK_APPEARANCE_ACTION_SEMANTIC_ID = f"widget:{STARTER_HOME_WIDGET_ID}:appearance"
WIDGET_PICKER_DIKCIZ_CATEGORY_LABEL = "Dikciz"
WIDGET_ADD_BLOCK_LABEL = "Add block"
WIDGET_APPEARANCE_LABEL = "Appearance"
WIDGET_EDIT_LABEL = "Edit"
PAGE_RENAME_LABEL = "Rename this page"
PAGE_FULL_NOTICE = "Page is full. Resize, move, or remove a widget first."
PROVIDER_CATEGORY_DESCRIPTION = "Browse Clock widgets"
WIDGET_PICKER_APPS_CATEGORY_LABEL = "Apps"
SETTINGS_SCRIPTS_SEMANTIC_ID = "settings:scripts"
SETTINGS_THEMES_SEMANTIC_ID = "settings:themes"
SETTINGS_GRID_COLUMNS_LABEL = "Columns"
SETTINGS_SAFE_MODE_LABEL = "Restart in safe mode"
WIDGET_DELETE_LABEL = "Delete"
RAIL_LONG_PRESS_INSET_PIXELS = 60
APP_DRAWER_SEARCH_TERM = "Settings"
GUIDE_CLOCK_NOTIFICATION_POLL_SECONDS = 1
GUIDE_CLOCK_NOTIFICATION_TIMEOUT_SECONDS = 20
GUIDE_CLOCK_SCRIPT_ID = "hourly-clock"
GUIDE_COMMAND_DECK_EDITOR_HTML_SEMANTIC_ID = "widget:command-deck:editor:html"
GUIDE_COMMAND_DECK_EDITOR_SAVE_SEMANTIC_ID = "widget:command-deck:editor:save"
GUIDE_HTML_BLOCK_CONFIRM_SEMANTIC_ID = "html-block:confirm"
GUIDE_HTML_RESULT_TEXT = "Guide HTML change saved"
GUIDE_HTML_RESULT_SOURCE = f"<main><h1>{GUIDE_HTML_RESULT_TEXT}</h1></main>"
GUIDE_NOTIFICATION_EVENT_TAG = "dikciz-guide-event"
GUIDE_NOTIFICATION_EVENT_TEXT = "This notification updated the home digest."
GUIDE_NOTIFICATION_EVENT_TITLE = "Dikciz guide event"
GUIDE_PAGE_RENAME_PREFIX = "page:rename:"
GUIDE_PAGE_RENAME_SAVE_SUFFIX = ":save"
GUIDE_PAGE_RENAME_TITLE_SUFFIX = ":title"
GUIDE_WORKSPACE_PAGE_TITLE = "Guide workspace"
GUIDE_WORKSPACE_SEARCH_SCREENSHOT = "32-page-search-by-name.png"
GUIDE_HTML_SAVE_SCREENSHOT = "33-html-saved.png"
GUIDE_HTML_BLOCK_SCREENSHOT = "34-html-block-appended.png"
GUIDE_CLOCK_NOTIFICATION_SCREENSHOT = "35-script-notification.png"
GUIDE_CLOCK_LOG_SCREENSHOT = "36-script-log.png"
GUIDE_NOTIFICATION_DIGEST_SCREENSHOT = "37-notification-digest.png"

# The walk rewrites tracked documentation images, so an ordinary suite run must
# not trigger it. Only `make dikciz-guide-capture` sets the variable.
pytestmark = pytest.mark.skipif(
    os.environ.get(GUIDE_CAPTURE_ENVIRONMENT_VARIABLE) != GUIDE_CAPTURE_ENABLED_VALUE,
    reason="user guide capture runs only through make dikciz-guide-capture",
)


def capture(name: str) -> None:
    """Photograph the current screen into the guide's image directory."""
    time.sleep(GUIDE_SETTLE_SECONDS)
    run_device_operation("screenshot")
    GUIDE_IMAGE_DIRECTORY.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ARTIFACT_DIRECTORY / SCREENSHOT_ARTIFACT_NAME, GUIDE_IMAGE_DIRECTORY / name)


def reset_to_starter_home() -> None:
    with connected_websocket_control() as connection:
        AUTOMATION.require_configuration(AUTOMATION.request(connection, AUTOMATION.TYPE_RESET))
    restore_system_home()
    wait_for_physical_description_bounds(STARTER_HOME_WIDGET_TITLE)


@pytest.fixture(autouse=True)
def isolated_launcher_fixture() -> Generator[None, None, None]:
    """Replace the package fixture so the walk runs on the bundled starter home.

    The shared autouse fixture installs the UI test document. The guide has to
    show what the product ships with, so this override resets to the bundled
    home instead, and resets again afterwards to leave the device as it was.
    """
    reset_to_starter_home()
    try:
        yield
    finally:
        reset_to_starter_home()


def close_active_dialog(connection: socket.socket) -> None:
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=DIALOG_CLOSE_SEMANTIC_ID)


def open_launcher_controls() -> None:
    direct_tap(wait_for_physical_description_bounds(LAUNCHER_CONTROL_OPEN_DESCRIPTION))
    wait_for_physical_text_bounds(LAUNCHER_CONTROL_SHEET_TITLE)


def open_launcher_control_route(connection: socket.socket, semantic_id: str) -> None:
    open_launcher_controls()
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=semantic_id)


def open_page_search() -> None:
    direct_tap(wait_for_physical_description_bounds(PAGE_SEARCH_OPEN_DESCRIPTION))
    wait_for_physical_text_bounds(PAGE_SEARCH_SHEET_TITLE)


def go_to_page(connection: socket.socket, page_id: str) -> None:
    open_page_search()
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_TAP,
        semanticId=f"{PAGE_SEARCH_PAGE_SEMANTIC_PREFIX}{page_id}",
    )
    wait_for_selected_page(connection, page_id)


def open_widget_action_sheet() -> None:
    """Long press the bundled control deck and wait for its own action sheet.

    The wait is physical, because the sheet's semantic nodes outlive its
    dismissal and a snapshot wait would return on a sheet already gone.

    The deck is a WebView. A long press that lands while it is still reloading
    is swallowed by the document, so the press is retried a few times.
    """
    for attempt in range(WIDGET_SHEET_OPEN_ATTEMPTS):
        direct_long_press_at(*center(physical_page_scroll_bounds()))
        try:
            wait_for_physical_text_bounds(WIDGET_MOVE_ACTION_LABEL)
            return
        except AssertionError:
            if attempt == WIDGET_SHEET_OPEN_ATTEMPTS - 1:
                raise
            time.sleep(GUIDE_SETTLE_SECONDS)


def start_widget_gesture(connection: socket.socket, semantic_id: str) -> None:
    """Start the named gesture from an already open widget action sheet.

    The sheet's own semantic nodes outlive its dismissal, so the wait is on the
    sheet leaving the screen rather than on its nodes leaving the snapshot.
    """
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=semantic_id)
    wait_for_physical_text_absent(WIDGET_MOVE_ACTION_LABEL)


def test_capture_user_guide_screenshots(websocket_control: socket.socket) -> None:
    capture_first_launch()
    capture_page_search(websocket_control)
    capture_second_page(websocket_control)
    capture_workbench_page(websocket_control)
    capture_page_creation(websocket_control)
    capture_named_page_search(websocket_control)
    capture_launcher_controls(websocket_control)
    capture_app_drawer(websocket_control)
    capture_app_drawer_search(websocket_control)
    capture_app_actions(websocket_control)
    capture_app_group(websocket_control)
    capture_widget_actions_and_grid_guides(websocket_control)
    capture_widget_delete(websocket_control)
    capture_widget_editor(websocket_control)
    capture_widget_appearance(websocket_control)
    capture_html_block_picker(websocket_control)
    capture_html_edit_result(websocket_control)
    capture_html_block_result(websocket_control)
    capture_page_menu(websocket_control)
    capture_page_rename(websocket_control)
    capture_widget_picker(websocket_control)
    capture_html_package_library(websocket_control)
    capture_provider_widgets(websocket_control)
    capture_page_full(websocket_control)
    capture_script_manager(websocket_control)
    capture_script_notification_and_log(websocket_control)
    capture_settings(websocket_control)
    capture_grid_settings(websocket_control)
    capture_themes(websocket_control)
    capture_safe_mode(websocket_control)
    capture_automation_access(websocket_control)
    capture_notification_digest(websocket_control)



def capture_first_launch() -> None:
    wait_for_physical_description_bounds(STARTER_HOME_WIDGET_TITLE)
    capture("01-first-launch.png")


def capture_page_search(connection: socket.socket) -> None:
    open_page_search()
    capture("02-page-search.png")
    close_active_dialog(connection)


def capture_second_page(connection: socket.socket) -> None:
    """Jump to Notes, which carries an app shortcut tile and the script dashboard."""
    go_to_page(connection, STARTER_NOTES_PAGE_ID)
    capture("03-notes-page.png")
    go_to_page(connection, STARTER_HOME_PAGE_ID)


def capture_launcher_controls(connection: socket.socket) -> None:
    open_launcher_controls()
    capture("06-launcher-controls.png")
    close_active_dialog(connection)


def capture_app_drawer(connection: socket.socket) -> None:
    open_launcher_control_route(connection, LAUNCHER_CONTROL_APP_DRAWER_SEMANTIC_ID)
    wait_for_snapshot_node(connection, APP_DRAWER_SEARCH_SEMANTIC_ID)
    capture("07-app-drawer.png")
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=APP_DRAWER_CLOSE_SEMANTIC_ID)
    wait_for_snapshot_node(connection, APP_DRAWER_SEARCH_SEMANTIC_ID, should_exist=False)


def capture_widget_actions_and_grid_guides(connection: socket.socket) -> None:
    """Photograph the widget sheet, then the grid the move and resize gestures draw.

    Every bundled page fills all of its cells, and the guides are drawn behind
    the page content, so on a full page there is nothing to see. The gesture
    scenes therefore clear the Notes page's script dashboard first, which is
    also the state an owner is in when they are arranging a page.
    """
    open_widget_action_sheet()
    capture("11-widget-actions.png")
    reset_to_starter_home()

    open_notes_page_with_free_cells(connection)
    start_tile_gesture(connection, TILE_MOVE_ACTION_SEMANTIC_ID)
    capture("12-move-grid.png")
    reset_to_starter_home()

    open_notes_page_with_free_cells(connection)
    start_tile_gesture(connection, TILE_RESIZE_ACTION_SEMANTIC_ID)
    capture("13-resize-grid.png")
    reset_to_starter_home()


def open_notes_page_with_free_cells(connection: socket.socket) -> None:
    """Select Notes with its script dashboard removed, leaving five rows free."""
    document = json.loads(json.dumps(current_configuration(connection)))
    home = document["launcher"]["home"]
    for page in home["pages"]:
        if page["id"] != STARTER_NOTES_PAGE_ID:
            continue
        page["widgets"] = [
            widget
            for widget in page["widgets"]
            if widget["id"] != NOTES_SCRIPT_DASHBOARD_WIDGET_ID
        ]
    home["selectedPageId"] = STARTER_NOTES_PAGE_ID
    AUTOMATION.require_configuration(
        AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_REPLACE, config=document),
    )
    wait_for_selected_page(connection, STARTER_NOTES_PAGE_ID)


def start_tile_gesture(connection: socket.socket, semantic_id: str) -> None:
    """Open the app tile's action sheet and start the named gesture from it."""
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_LONG_PRESS,
        semanticId=TILE_MOVE_HANDLE_SEMANTIC_ID,
    )
    wait_for_physical_text_bounds(WIDGET_MOVE_ACTION_LABEL)
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=semantic_id)
    wait_for_physical_text_absent(WIDGET_MOVE_ACTION_LABEL)


def capture_page_menu(connection: socket.socket) -> None:
    """Reach the page menu through launcher controls.

    The bundled home is covered by its control deck, so there is no blank page
    space to long press. The named route is the reliable way in.
    """
    open_launcher_control_route(connection, LAUNCHER_CONTROL_MANAGE_PAGE_SEMANTIC_ID)
    configuration = AUTOMATION.require_configuration(
        AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
    )
    wait_for_physical_text_bounds(page_menu_title(configuration, selected_page(configuration)))
    capture("19-page-menu.png")
    close_active_dialog(connection)


def capture_widget_picker(connection: socket.socket) -> None:
    """The picker on a page that still has room."""
    open_notes_page_with_free_cells(connection)
    open_launcher_control_route(connection, LAUNCHER_CONTROL_ADD_TO_PAGE_SEMANTIC_ID)
    wait_for_physical_text_bounds(WIDGET_PICKER_DIKCIZ_CATEGORY_LABEL)
    capture("21-widget-picker.png")
    close_active_dialog(connection)
    reset_to_starter_home()


def capture_page_full(connection: socket.socket) -> None:
    """The refusal a full page gives instead of placing a widget somewhere wrong."""
    open_launcher_control_route(connection, LAUNCHER_CONTROL_ADD_TO_PAGE_SEMANTIC_ID)
    wait_for_physical_text_bounds(PAGE_FULL_NOTICE)
    capture("24-page-full.png")
    close_active_dialog(connection)


def capture_settings(connection: socket.socket) -> None:
    open_launcher_control_route(connection, LAUNCHER_CONTROL_SETTINGS_SEMANTIC_ID)
    wait_for_physical_text_bounds(DIKCIZ_SETTINGS_TITLE)
    capture("27-settings.png")
    close_active_dialog(connection)


def capture_automation_access(connection: socket.socket) -> None:
    open_launcher_control_route(connection, LAUNCHER_CONTROL_SETTINGS_SEMANTIC_ID)
    wait_for_physical_text_bounds(DIKCIZ_SETTINGS_TITLE)
    direct_tap(scroll_dialog_to_text(DIKCIZ_AUTOMATION_BUTTON_TEXT))
    wait_for_physical_text_bounds(DIKCIZ_AUTOMATION_TITLE_TEXT)
    capture("31-automation-access.png")
    close_active_dialog(connection)


def scroll_settings_along_left_edge(text: str) -> None:
    """Scroll the settings sheet until the named control is on screen.

    Two things make this sheet awkward. Its grid fields are text inputs, so a
    swipe down the middle lands on one and the input keeps the gesture. And its
    buttons render their label in capitals, so the match ignores case.
    """
    expected = text.casefold()
    for _ in range(SETTINGS_SCROLL_ATTEMPTS):
        if any(visible.casefold() == expected for visible in physical_visible_texts()):
            return
        sheet = physical_bounds_for(
            lambda node: node.get("class") == SETTINGS_SCROLL_VIEW_CLASS,
        )
        scroll_x = sheet["left"] + SETTINGS_SCROLL_EDGE_INSET_PIXELS
        scroll_y = center(sheet)[1]
        direct_swipe(
            (scroll_x, scroll_y + SETTINGS_SCROLL_TRAVEL_PIXELS // 2),
            (scroll_x, scroll_y - SETTINGS_SCROLL_TRAVEL_PIXELS // 2),
            DIALOG_SCROLL_DURATION_MILLISECONDS,
        )
    raise AssertionError(f"settings never scrolled to {text!r}")


def capture_workbench_page(connection: socket.socket) -> None:
    """The third bundled page, which points at the public files and scripts."""
    go_to_page(connection, STARTER_WORKBENCH_PAGE_ID)
    capture("04-workbench-page.png")
    go_to_page(connection, STARTER_HOME_PAGE_ID)


def capture_app_drawer_search(connection: socket.socket) -> None:
    open_launcher_control_route(connection, LAUNCHER_CONTROL_APP_DRAWER_SEMANTIC_ID)
    wait_for_snapshot_node(connection, APP_DRAWER_SEARCH_SEMANTIC_ID)
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_SET_TEXT,
        semanticId=APP_DRAWER_SEARCH_SEMANTIC_ID,
        text=APP_DRAWER_SEARCH_TERM,
    )
    capture("08-app-drawer-search.png")
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=APP_DRAWER_CLOSE_SEMANTIC_ID)
    wait_for_snapshot_node(connection, APP_DRAWER_SEARCH_SEMANTIC_ID, should_exist=False)


def capture_app_actions(connection: socket.socket) -> None:
    """The per-app action sheet the drawer's overflow button opens."""
    open_launcher_control_route(connection, LAUNCHER_CONTROL_APP_DRAWER_SEMANTIC_ID)
    wait_for_snapshot_node(connection, APP_DRAWER_SEARCH_SEMANTIC_ID)
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_SET_TEXT,
        semanticId=APP_DRAWER_SEARCH_SEMANTIC_ID,
        text=APP_DRAWER_SEARCH_TERM,
    )
    direct_tap(wait_for_physical_description_bounds(APP_DRAWER_SETTINGS_ACTIONS_DESCRIPTION))
    wait_for_physical_text_bounds(APP_ACTION_FORCE_STOP_LABEL)
    capture("09-app-actions.png")
    # The drawer is a view inside the activity, so the Home intent a reset sends
    # does not close it. It has to be dismissed before the next scene starts.
    close_active_dialog(connection)
    wait_for_physical_text_absent(APP_ACTION_FORCE_STOP_LABEL)
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=APP_DRAWER_CLOSE_SEMANTIC_ID)
    wait_for_snapshot_node(connection, APP_DRAWER_SEARCH_SEMANTIC_ID, should_exist=False)
    reset_to_starter_home()


def capture_widget_editor(connection: socket.socket) -> None:
    """The HTML widget editor, where a widget's source and state are edited."""
    open_widget_action_sheet()
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=DECK_EDIT_ACTION_SEMANTIC_ID)
    wait_for_physical_text_absent(WIDGET_MOVE_ACTION_LABEL)
    capture("16-widget-editor.png")
    reset_to_starter_home()


def capture_widget_appearance(connection: socket.socket) -> None:
    open_widget_action_sheet()
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_TAP,
        semanticId=DECK_APPEARANCE_ACTION_SEMANTIC_ID,
    )
    wait_for_physical_description_bounds(APPEARANCE_EDITOR_DESCRIPTION)
    capture("17-appearance-editor.png")
    reset_to_starter_home()


def capture_html_block_picker(connection: socket.socket) -> None:
    """Add block appends source inside one widget instead of creating a new one."""
    open_widget_action_sheet()
    direct_tap(scroll_dialog_to_text(WIDGET_ADD_BLOCK_LABEL))
    wait_for_physical_text_absent(WIDGET_MOVE_ACTION_LABEL)
    capture("18-html-block-picker.png")
    reset_to_starter_home()


def capture_html_edit_result(connection: socket.socket) -> None:
    """Save a visible HTML edit and photograph the rendered document."""
    open_widget_action_sheet()
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=DECK_EDIT_ACTION_SEMANTIC_ID)
    wait_for_snapshot_node(connection, GUIDE_COMMAND_DECK_EDITOR_HTML_SEMANTIC_ID)
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_SET_TEXT,
        semanticId=GUIDE_COMMAND_DECK_EDITOR_HTML_SEMANTIC_ID,
        text=GUIDE_HTML_RESULT_SOURCE,
    )
    html_snapshot = wait_for_snapshot_node(
        connection,
        GUIDE_COMMAND_DECK_EDITOR_HTML_SEMANTIC_ID,
    )
    html_input = AUTOMATION.require_node(
        html_snapshot,
        GUIDE_COMMAND_DECK_EDITOR_HTML_SEMANTIC_ID,
    )
    assert html_input[AUTOMATION.KEY_TEXT] == GUIDE_HTML_RESULT_SOURCE
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_TAP,
        semanticId=GUIDE_COMMAND_DECK_EDITOR_SAVE_SEMANTIC_ID,
    )
    wait_for_physical_text_bounds(GUIDE_HTML_RESULT_TEXT)
    capture(GUIDE_HTML_SAVE_SCREENSHOT)
    reset_to_starter_home()


def test_capture_html_edit_result_in_isolation(websocket_control: socket.socket) -> None:
    """Keep the saved-HTML evidence scene independently runnable on failure."""
    capture_html_edit_result(websocket_control)


def capture_html_block_result(connection: socket.socket) -> None:
    """Append a real block and photograph the owning document after insertion."""
    open_widget_action_sheet()
    direct_tap(scroll_dialog_to_text(WIDGET_ADD_BLOCK_LABEL))
    direct_tap(wait_for_physical_text_bounds("Section heading"))
    wait_for_snapshot_node(connection, GUIDE_HTML_BLOCK_CONFIRM_SEMANTIC_ID)
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_TAP,
        semanticId=GUIDE_HTML_BLOCK_CONFIRM_SEMANTIC_ID,
    )
    wait_for_physical_text_bounds("Section title")
    capture(GUIDE_HTML_BLOCK_SCREENSHOT)
    reset_to_starter_home()


def capture_page_rename(connection: socket.socket) -> None:
    open_launcher_control_route(connection, LAUNCHER_CONTROL_MANAGE_PAGE_SEMANTIC_ID)
    direct_tap(scroll_dialog_to_text(PAGE_RENAME_LABEL))
    capture("20-page-rename.png")
    reset_to_starter_home()


def capture_html_package_library(connection: socket.socket) -> None:
    """The bundled HTML packages the picker offers under its Dikciz category."""
    open_notes_page_with_free_cells(connection)
    open_launcher_control_route(connection, LAUNCHER_CONTROL_ADD_TO_PAGE_SEMANTIC_ID)
    direct_tap(scroll_dialog_to_text(WIDGET_PICKER_DIKCIZ_CATEGORY_LABEL))
    capture("22-html-package-library.png")
    close_active_dialog(connection)
    reset_to_starter_home()


def capture_page_creation(connection: socket.socket) -> None:
    """A rail-edge long press creates a saved page at that end."""
    original = current_configuration(connection)
    indicator = physical_page_indicator_bounds()
    direct_long_press_at(
        indicator["right"] - RAIL_LONG_PRESS_INSET_PIXELS,
        center(indicator)[1],
    )
    created = wait_for_page_count(connection, len(original["launcher"]["home"]["pages"]) + 1)
    page_id = selected_page(created)
    open_launcher_control_route(connection, LAUNCHER_CONTROL_MANAGE_PAGE_SEMANTIC_ID)
    wait_for_physical_text_bounds(page_menu_title(created, page_id))
    capture("05-create-page.png")
    close_active_dialog(connection)
    reset_to_starter_home()


def capture_named_page_search(connection: socket.socket) -> None:
    """Create and name a page, then find it by that exact saved name."""
    initial = current_configuration(connection)
    indicator = physical_page_indicator_bounds()
    direct_long_press_at(
        indicator["right"] - RAIL_LONG_PRESS_INSET_PIXELS,
        center(indicator)[1],
    )
    created = wait_for_page_count(connection, len(initial["launcher"]["home"]["pages"]) + 1)
    page_id = selected_page(created)

    open_launcher_control_route(connection, LAUNCHER_CONTROL_MANAGE_PAGE_SEMANTIC_ID)
    rename_action = f"{PAGE_MENU_ACTION_PREFIX}{page_id}:rename"
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=rename_action)
    title_input = f"{GUIDE_PAGE_RENAME_PREFIX}{page_id}{GUIDE_PAGE_RENAME_TITLE_SUFFIX}"
    save_action = f"{GUIDE_PAGE_RENAME_PREFIX}{page_id}{GUIDE_PAGE_RENAME_SAVE_SUFFIX}"
    wait_for_snapshot_node(connection, title_input)
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_SET_TEXT,
        semanticId=title_input,
        text=GUIDE_WORKSPACE_PAGE_TITLE,
    )
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=save_action)

    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        saved = current_configuration(connection)
        if page_title(saved, page_id) == GUIDE_WORKSPACE_PAGE_TITLE:
            break
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    else:
        raise AssertionError("the named guide page was not saved")

    open_page_search()
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_SET_TEXT,
        semanticId=PAGE_SEARCH_QUERY_SEMANTIC_ID,
        text=GUIDE_WORKSPACE_PAGE_TITLE,
    )
    page_entry = f"{PAGE_SEARCH_PAGE_SEMANTIC_PREFIX}{page_id}"
    wait_for_snapshot_node(connection, page_entry)
    capture(GUIDE_WORKSPACE_SEARCH_SCREENSHOT)
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=page_entry)
    wait_for_selected_page(connection, page_id)
    reset_to_starter_home()


def capture_app_group(connection: socket.socket) -> None:
    """Build a group the way an owner does, by dropping one app tile on another."""
    document = json.loads(json.dumps(current_configuration(connection)))
    home = document["launcher"]["home"]
    for page in home["pages"]:
        if page["id"] != STARTER_NOTES_PAGE_ID:
            continue
        page["widgets"] = [
            widget
            for widget in page["widgets"]
            if widget["id"] == NOTES_APP_TILE_WIDGET_ID
        ]
        page["widgets"].append(
            {
                "id": GUIDE_CLOCK_WIDGET_ID,
                "title": GUIDE_CLOCK_TITLE,
                "enabled": True,
                "locked": False,
                "cell": {"column": 1, "row": 0, "columnSpan": 1, "rowSpan": 1},
                "type": "app",
                "component": GUIDE_CLOCK_COMPONENT,
                "displayStyle": "iconLabel",
            },
        )
    home["selectedPageId"] = STARTER_NOTES_PAGE_ID
    AUTOMATION.require_configuration(
        AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_REPLACE, config=document),
    )
    wait_for_selected_page(connection, STARTER_NOTES_PAGE_ID)

    source = physical_description_bounds(
        WIDGET_MOVE_HANDLE_DESCRIPTION.format(GUIDE_CLOCK_TITLE),
    )
    target = physical_description_bounds(
        WIDGET_MOVE_HANDLE_DESCRIPTION.format(STARTER_ANDROID_SETTINGS_TITLE),
    )
    direct_swipe(
        center(source),
        (center(target)[0], center(source)[1]),
        GUIDE_DRAG_DURATION_MILLISECONDS,
    )
    time.sleep(GUIDE_SETTLE_SECONDS)
    direct_tap_at(*center(target))
    capture("10-app-group.png")
    reset_to_starter_home()


def capture_widget_delete(connection: socket.socket) -> None:
    """Delete the bundled deck and photograph the resulting empty page."""
    open_widget_action_sheet()
    direct_tap(scroll_dialog_to_text(WIDGET_DELETE_LABEL))
    capture("15-widget-delete.png")
    reset_to_starter_home()


def capture_provider_widgets(connection: socket.socket) -> None:
    """Third-party AppWidget providers keep their own host view."""
    open_notes_page_with_free_cells(connection)
    open_launcher_control_route(connection, LAUNCHER_CONTROL_ADD_TO_PAGE_SEMANTIC_ID)
    # Installed apps that publish widgets sit under their own collapsed category.
    direct_tap(scroll_dialog_to_text(WIDGET_PICKER_APPS_CATEGORY_LABEL))
    direct_tap(wait_for_physical_description_bounds(PROVIDER_CATEGORY_DESCRIPTION))
    capture("23-provider-widgets.png")
    reset_to_starter_home()


def capture_script_manager(connection: socket.socket) -> None:
    """Lua scripts are managed from settings, not as a widget type."""
    open_launcher_control_route(connection, LAUNCHER_CONTROL_SETTINGS_SEMANTIC_ID)
    wait_for_physical_text_bounds(DIKCIZ_SETTINGS_TITLE)
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=SETTINGS_SCRIPTS_SEMANTIC_ID)
    capture("25-script-manager.png")
    capture_script_editor(connection)


def capture_script_editor(connection: socket.socket) -> None:
    """The saved clock script's real source, state, and enable switch."""
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_TAP,
        semanticId=f"lua-script:{GUIDE_CLOCK_SCRIPT_ID}",
    )
    capture("26-script-editor.png")
    reset_to_starter_home()


def wait_for_clock_notification() -> dict[str, Any]:
    deadline = time.monotonic() + GUIDE_CLOCK_NOTIFICATION_TIMEOUT_SECONDS
    notification = clock_notification()
    while notification is None and time.monotonic() < deadline:
        time.sleep(GUIDE_CLOCK_NOTIFICATION_POLL_SECONDS)
        notification = clock_notification()
    if notification is None:
        raise AssertionError("the guide clock script did not post a notification")
    return notification


def capture_script_notification_and_log(connection: socket.socket) -> None:
    """Trigger a bundled script, then show its Android effect and native log."""
    device_command(f"pm grant {DIKCIZ_DEBUG_PACKAGE} android.permission.POST_NOTIFICATIONS")
    device_command("cmd statusbar collapse")
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_AUTOMATION_TRIGGER,
        scriptId=GUIDE_CLOCK_SCRIPT_ID,
    )
    assert wait_script_result(GUIDE_CLOCK_SCRIPT_ID, "completed")
    assert wait_for_clock_notification()["body"]
    device_command("cmd statusbar expand-notifications")
    wait_for_physical_text_bounds(CLOCK_TITLE)
    capture(GUIDE_CLOCK_NOTIFICATION_SCREENSHOT)
    device_command("cmd statusbar collapse")

    go_to_page(connection, STARTER_NOTES_PAGE_ID)
    log_action = default_script_dashboard_semantic_id(
        GUIDE_CLOCK_SCRIPT_ID,
        SCRIPT_DASHBOARD_ACTION_LOGS,
    )
    wait_for_snapshot_node(connection, log_action)
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=log_action)
    wait_for_physical_text_bounds("Script logs")
    capture(GUIDE_CLOCK_LOG_SCREENSHOT)
    close_active_dialog(connection)
    reset_to_starter_home()


def capture_notification_digest(connection: socket.socket) -> None:
    """Deliver a real notification event and show the starter digest changing."""
    device_command(f"cmd notification allow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
    try:
        deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
        while time.monotonic() < deadline:
            status = AUTOMATION.request(connection, AUTOMATION.TYPE_AUTOMATION_STATUS)
            if status["androidAccess"]["notificationListener"] is True:
                break
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
        else:
            raise AssertionError("the guide notification listener did not become active")

        device_command(
            "cmd notification post "
            f"-t '{GUIDE_NOTIFICATION_EVENT_TITLE}' {GUIDE_NOTIFICATION_EVENT_TAG} "
            f"'{GUIDE_NOTIFICATION_EVENT_TEXT}'",
        )
        deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
        while time.monotonic() < deadline:
            configuration = current_configuration(connection)
            state = configuration_widget(configuration, STARTER_HOME_WIDGET_ID).get("state")
            if isinstance(state, dict) and state.get("notificationCount") == 1:
                break
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
        else:
            raise AssertionError("the starter notification digest did not update")
        capture(GUIDE_NOTIFICATION_DIGEST_SCREENSHOT)
    finally:
        device_command(f"cmd notification disallow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
        reset_to_starter_home()


def test_capture_remaining_result_scenes_in_isolation(
    websocket_control: socket.socket,
) -> None:
    """Run the remaining new outcome scenes without repeating the guide walk."""
    capture_html_block_result(websocket_control)
    capture_script_notification_and_log(websocket_control)
    capture_notification_digest(websocket_control)


def capture_grid_settings(connection: socket.socket) -> None:
    open_launcher_control_route(connection, LAUNCHER_CONTROL_SETTINGS_SEMANTIC_ID)
    wait_for_physical_text_bounds(DIKCIZ_SETTINGS_TITLE)
    scroll_dialog_to_text(SETTINGS_GRID_COLUMNS_LABEL)
    capture("28-grid-settings.png")
    close_active_dialog(connection)


def capture_themes(connection: socket.socket) -> None:
    open_launcher_control_route(connection, LAUNCHER_CONTROL_SETTINGS_SEMANTIC_ID)
    wait_for_physical_text_bounds(DIKCIZ_SETTINGS_TITLE)
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=SETTINGS_THEMES_SEMANTIC_ID)
    capture("29-themes.png")
    # A reset re-renders the page behind an open sheet without dismissing it,
    # and the next scene needs the page canvas back to scroll against.
    close_active_dialog(connection)
    wait_for_physical_description_bounds(STARTER_HOME_WIDGET_TITLE)
    reset_to_starter_home()


def capture_safe_mode(connection: socket.socket) -> None:
    """Safe mode is the recovery route when a configuration stops the home."""
    open_launcher_control_route(connection, LAUNCHER_CONTROL_SETTINGS_SEMANTIC_ID)
    wait_for_physical_text_bounds(DIKCIZ_SETTINGS_TITLE)
    scroll_settings_along_left_edge(SETTINGS_SAFE_MODE_LABEL)
    capture("30-safe-mode.png")
    close_active_dialog(connection)
