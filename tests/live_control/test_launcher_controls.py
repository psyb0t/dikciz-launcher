"""Visible launcher controls, home chrome, widget move, and dialog reachability."""

from . import *


def open_launcher_control_sheet() -> None:
    direct_tap(wait_for_physical_description_bounds(LAUNCHER_CONTROL_OPEN_DESCRIPTION))
    wait_for_physical_text_bounds(LAUNCHER_CONTROL_SHEET_TITLE)


def test_launcher_control_is_visible_on_a_full_page_and_exposes_every_route(
    websocket_control: socket.socket,
) -> None:
    snapshot = wait_for_snapshot_node(websocket_control, LAUNCHER_CONTROL_SEMANTIC_ID)
    control_bounds = bounds_for(snapshot, LAUNCHER_CONTROL_SEMANTIC_ID)
    assert control_bounds["bottom"] > control_bounds["top"]
    assert control_bounds["right"] > control_bounds["left"]
    assert LAUNCHER_CONTROL_OPEN_DESCRIPTION in physical_visible_texts()

    open_launcher_control_sheet()
    sheet = wait_for_snapshot_node(
        websocket_control,
        LAUNCHER_CONTROL_APP_DRAWER_SEMANTIC_ID,
    )
    for semantic_id in (
        LAUNCHER_CONTROL_APP_DRAWER_SEMANTIC_ID,
        LAUNCHER_CONTROL_ADD_TO_PAGE_SEMANTIC_ID,
        LAUNCHER_CONTROL_MANAGE_PAGE_SEMANTIC_ID,
        LAUNCHER_CONTROL_SETTINGS_SEMANTIC_ID,
    ):
        assert bounds_for(sheet, semantic_id)["bottom"] > 0

    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=LAUNCHER_CONTROL_SETTINGS_SEMANTIC_ID,
    )
    wait_for_physical_text_bounds(DIKCIZ_SETTINGS_TITLE)


def test_home_chrome_carries_no_developer_page_coordinates(
    websocket_control: socket.socket,
) -> None:
    visible = physical_visible_texts()
    coordinates = [text for text in visible if HOME_CHROME_COORDINATE_PATTERN.search(text)]
    assert not coordinates, visible

    # The page stays identifiable through the named route the control opens,
    # not through a label parked in the home chrome. The chrome pill is icon only,
    # so it announces itself through its content description.
    assert LAUNCHER_CONTROL_OPEN_DESCRIPTION in visible, visible
    configuration = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    current_menu_title = page_menu_title(configuration, selected_page(configuration))
    open_launcher_control_sheet()
    wait_for_physical_text_bounds(current_menu_title)


def test_bottom_launcher_control_clears_the_page_canvas_and_the_indicator(
    websocket_control: socket.socket,
) -> None:
    snapshot = wait_for_snapshot_node(websocket_control, LAUNCHER_CONTROL_SEMANTIC_ID)
    control_bounds = bounds_for(snapshot, LAUNCHER_CONTROL_SEMANTIC_ID)
    page_bounds = physical_page_scroll_bounds()
    assert control_bounds["top"] >= page_bounds["bottom"], (control_bounds, page_bounds)

    indicator_bounds = bounds_for(snapshot, HORIZONTAL_PAGE_INDICATOR_SEMANTIC_ID)
    assert control_bounds["left"] >= indicator_bounds["right"], (
        control_bounds,
        indicator_bounds,
    )


def test_page_menu_is_titled_grouped_and_uses_verb_labels(
    websocket_control: socket.socket,
) -> None:
    open_page_menu_on_selected_page()
    configuration = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    current_menu_title = page_menu_title(configuration, selected_page(configuration))
    wait_for_physical_text_bounds(current_menu_title)
    visible = physical_visible_texts()
    assert PAGE_MENU_SECTION_ADD in visible
    assert PAGE_MENU_SECTION_PAGE in visible
    assert PAGE_MENU_SECTION_LAUNCHER in visible
    assert PAGE_MENU_ADD_WIDGET_LABEL in visible
    assert PAGE_MENU_OPEN_SETTINGS_LABEL in visible


def test_widget_action_sheet_exposes_a_visible_move_action(
    websocket_control: socket.socket,
) -> None:
    # UIAutomator dumps only the front window, so once the action sheet is open
    # the widget behind it is no longer in the tree. Its bounds have to be read
    # while the page is still the front window.
    before = physical_widget_bounds_for_title(FIXTURE_WIDGET_TITLES["welcome"])

    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_LONG_PRESS,
        semanticId=WELCOME_MOVE_HANDLE_SEMANTIC_ID,
    )
    move_semantic_id = WELCOME_MOVE_ACTION_SEMANTIC_ID
    snapshot = wait_for_snapshot_node(websocket_control, move_semantic_id)
    assert bounds_for(snapshot, move_semantic_id)["bottom"] > 0
    assert WIDGET_MOVE_ACTION_LABEL in physical_visible_texts()
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=move_semantic_id,
    )
    wait_for_snapshot_node(websocket_control, move_semantic_id, should_exist=False)
    # Row 1 is filled by the fixture-detail widget across all four columns, so
    # the drag has to clear it, and two rows down is the first free landing
    # place. One row of travel is the gap between the two fixture rows, which
    # is a cell plus the grid gap, not a cell on its own.
    # Aim at the middle of the destination row. Landing the widget's top edge
    # exactly on a row boundary leaves it in the row above.
    detail = physical_widget_bounds_for_title(FIXTURE_WIDGET_TITLES["fixture-detail"])
    row_pitch = detail["top"] - before["top"]
    start_x, start_y = center(before)
    row_travel = row_pitch * WIDGET_MOVE_FREE_ROW_OFFSET + row_pitch // 2
    direct_swipe(
        (start_x, start_y),
        (start_x, start_y + row_travel),
        WIDGET_MOVE_DURATION_MILLISECONDS,
    )
    moved = wait_for_moved_widget_top(
        websocket_control,
        FIXTURE_WIDGET_TITLES["welcome"],
        before["top"],
    )
    assert moved > before["top"]


def wait_for_moved_widget_top(
    connection: socket.socket,
    title: str,
    original_top: int,
) -> int:
    deadline = time.monotonic() + CONTROL_PLANE_RESTART_TIMEOUT_SECONDS
    current_top = original_top
    while time.monotonic() < deadline:
        current_top = physical_widget_bounds_for_title(title)["top"]
        if current_top != original_top:
            return current_top
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"widget did not move from top {original_top}: {title}")


def test_settings_and_automation_expose_their_last_control_to_a_real_touch(
    websocket_control: socket.socket,
) -> None:
    open_launcher_control_sheet()
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=LAUNCHER_CONTROL_SETTINGS_SEMANTIC_ID,
    )
    wait_for_physical_text_bounds(DIKCIZ_SETTINGS_TITLE)
    wait_for_physical_text_bounds(SETTINGS_FIRST_CONTROL_TEXT)
    settings_last = scroll_dialog_to_text(SETTINGS_LAST_CONTROL_TEXT)
    assert settings_last["bottom"] > settings_last["top"]

    automation_entry = scroll_dialog_to_text(DIKCIZ_AUTOMATION_BUTTON_TEXT)
    direct_tap(automation_entry)
    wait_for_physical_text_bounds(DIKCIZ_AUTOMATION_TITLE_TEXT)
    automation_last = scroll_dialog_to_text(AUTOMATION_LAST_CONTROL_TEXT)
    assert automation_last["bottom"] > automation_last["top"]
