"""Two-axis page navigation, page menus, and command-sheet behavior."""

from . import *

def test_direct_page_indicator_swipes_change_and_restore_pages(
    websocket_control: socket.socket,
) -> None:
    direct_page_round_trip(websocket_control)


def test_direct_page_canvas_long_press_opens_page_menu_before_release(
    websocket_control: socket.socket,
) -> None:
    direct_select_home_page(websocket_control)
    page_bounds = physical_page_scroll_bounds()
    release_deadline = start_held_long_press_at(*center(page_bounds))
    try:
        widgets_bounds = wait_for_physical_text_bounds(PAGE_MENU_ADD_WIDGET_LABEL)
        assert time.monotonic() < release_deadline
        set_home_bounds = wait_for_physical_text_bounds(PAGE_MENU_SET_HOME_LABEL)
        assert widgets_bounds["top"] < set_home_bounds["top"]
    finally:
        remaining_hold_seconds = release_deadline - time.monotonic()
        if remaining_hold_seconds > 0:
            time.sleep(remaining_hold_seconds)
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_TAP,
            semanticId=LUA_MANAGER_CLOSE_SEMANTIC_ID,
        )
        wait_for_physical_text_bounds(FIXTURE_WIDGET_TITLES["welcome"])


def test_direct_page_indicator_dot_long_press_does_not_open_page_menu(
    websocket_control: socket.socket,
) -> None:
    direct_select_home_page(websocket_control)
    horizontal_bounds = physical_page_indicator_bounds("horizontal")
    direct_long_press_at(
        *page_indicator_dot_center(
            horizontal_bounds,
            dot_index=0,
            page_count=2,
        ),
    )
    wait_for_direct_page_selection(
        websocket_control,
        FIXTURE_HOME_PAGE_ID,
        axis="horizontal",
    )
    assert PAGE_MENU_ADD_WIDGET_LABEL not in physical_visible_texts()


def test_direct_page_indicator_dots_select_exact_pages_and_blank_rails_step_adjacent_pages(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = copy.deepcopy(original)
    append_empty_page(
        configured,
        FIXTURE_THIRD_COLUMN_PAGE_ID,
        FIXTURE_THIRD_COLUMN_PAGE_TITLE,
        column=2,
        row=0,
    )
    append_empty_page(
        configured,
        FIXTURE_THIRD_ROW_PAGE_ID,
        FIXTURE_THIRD_ROW_PAGE_TITLE,
        column=0,
        row=2,
    )
    try:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        )
        wait_for_direct_page_selection(
            websocket_control,
            FIXTURE_HOME_PAGE_ID,
            axis="horizontal",
        )

        horizontal_bounds = physical_page_indicator_bounds("horizontal")
        direct_tap_at(
            *page_indicator_dot_center(
                horizontal_bounds,
                dot_index=1,
                page_count=3,
            ),
        )
        wait_for_direct_page_selection(
            websocket_control,
            FIXTURE_NOTES_PAGE_ID,
            axis="horizontal",
        )
        direct_tap_at(*page_indicator_empty_rail_point("horizontal", after_dots=True))
        wait_for_direct_page_selection(
            websocket_control,
            FIXTURE_THIRD_COLUMN_PAGE_ID,
            axis="horizontal",
        )
        direct_tap_at(*page_indicator_empty_rail_point("horizontal", after_dots=True))
        wait_for_direct_page_selection(
            websocket_control,
            FIXTURE_THIRD_COLUMN_PAGE_ID,
            axis="horizontal",
        )
        direct_tap_at(
            *page_indicator_dot_center(
                physical_page_indicator_bounds("horizontal"),
                dot_index=0,
                page_count=3,
            ),
        )
        wait_for_direct_page_selection(
            websocket_control,
            FIXTURE_HOME_PAGE_ID,
            axis="horizontal",
        )
        direct_tap_at(*page_indicator_empty_rail_point("horizontal", after_dots=False))
        wait_for_direct_page_selection(
            websocket_control,
            FIXTURE_HOME_PAGE_ID,
            axis="horizontal",
        )

        direct_tap_at(*page_indicator_empty_rail_point("vertical", after_dots=True))
        wait_for_direct_page_selection(
            websocket_control,
            FIXTURE_ACTIVITY_PAGE_ID,
            axis="vertical",
        )
        direct_tap_at(*page_indicator_empty_rail_point("vertical", after_dots=True))
        wait_for_direct_page_selection(
            websocket_control,
            FIXTURE_THIRD_ROW_PAGE_ID,
            axis="vertical",
        )
        direct_tap_at(*page_indicator_empty_rail_point("vertical", after_dots=True))
        wait_for_direct_page_selection(
            websocket_control,
            FIXTURE_THIRD_ROW_PAGE_ID,
            axis="vertical",
        )
        direct_tap_at(
            *page_indicator_dot_center(
                physical_page_indicator_bounds("vertical"),
                dot_index=0,
                page_count=3,
                axis="vertical",
            ),
        )
        wait_for_direct_page_selection(
            websocket_control,
            FIXTURE_HOME_PAGE_ID,
            axis="vertical",
        )
        direct_tap_at(*page_indicator_empty_rail_point("vertical", after_dots=False))
        wait_for_direct_page_selection(
            websocket_control,
            FIXTURE_HOME_PAGE_ID,
            axis="vertical",
        )
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )


def assert_held_page_indicator_drag_selects_before_pointer_release(
    websocket_control: socket.socket,
    axis: str,
    destination_page_id: str,
) -> None:
    direct_select_home_page(websocket_control)
    indicator_bounds = physical_page_indicator_bounds(axis)
    x, y = center(indicator_bounds)
    distance = round(VERTICAL_PAGE_SWIPE_DISTANCE_DP * device_density_scale())
    start, end = (
        ((x + distance, y), (x - distance, y))
        if axis == "horizontal"
        else ((x, y + distance), (x, y - distance))
    )
    with connected_private_vnc() as vnc:
        assert end[0] < vnc.width
        assert end[1] < vnc.height
        vnc.pointer_down(*start)
        vnc.pointer_move(*end)
        release_deadline = time.monotonic() + DIRECT_HELD_PAGE_SWIPE_MILLISECONDS / 1_000
        try:
            wait_for_selected_page(websocket_control, destination_page_id)
            assert time.monotonic() < release_deadline
            saved = AUTOMATION.require_configuration(
                AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
            )
            assert saved["launcher"]["home"]["selectedPageId"] == destination_page_id
        finally:
            vnc.pointer_up(*end)
    wait_for_physical_selected_page(
        selected_page_title(websocket_control, destination_page_id),
        axis=axis,
    )
    if axis == "horizontal":
        direct_select_home_page(websocket_control)
    else:
        direct_select_home_page_from_activity(websocket_control)


def test_page_indicator_selection_is_immediate(
    websocket_control: socket.socket,
) -> None:
    assert_held_page_indicator_drag_selects_before_pointer_release(
        websocket_control,
        axis="horizontal",
        destination_page_id=FIXTURE_NOTES_PAGE_ID,
    )
    assert_held_page_indicator_drag_selects_before_pointer_release(
        websocket_control,
        axis="vertical",
        destination_page_id=FIXTURE_ACTIVITY_PAGE_ID,
    )
    direct_select_home_page(websocket_control)
    log_record_count = len(device_log_records())
    tap_point = page_indicator_dot_center(
        physical_page_indicator_bounds("horizontal"),
        dot_index=1,
        page_count=2,
    )
    with connected_private_vnc() as vnc:
        selection_deadline = time.monotonic() + DIRECT_PAGE_SELECTION_VISIBILITY_SECONDS
        vnc.left_click(*tap_point)
    wait_for_selected_page(websocket_control, FIXTURE_NOTES_PAGE_ID)
    assert time.monotonic() < selection_deadline
    wait_for_physical_selected_page(
        selected_page_title(websocket_control, FIXTURE_NOTES_PAGE_ID),
        axis="horizontal",
    )
    records = device_log_records()[log_record_count:]
    assert not any(str(record.get("event", "")).startswith("page_transition_") for record in records)
    run_device_operation("screenshot")
    run_device_operation("uiautomator-dump")
    screenshot_artifact = ARTIFACT_DIRECTORY / "page-selection-direct-tap.png"
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", screenshot_artifact)
    assert screenshot_artifact.stat().st_size > 0
    assert (ARTIFACT_DIRECTORY / "window.xml").stat().st_size > 0


def test_direct_permanent_two_axis_indicators_select_fixed_pages(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    try:
        pages = original["launcher"]["home"]["pages"]
        assert {(page["position"]["column"], page["position"]["row"]) for page in pages} == {
            (0, 0),
            (0, 1),
            (1, 0),
        }
        snapshot = wait_for_snapshot_node(websocket_control, HORIZONTAL_PAGE_INDICATOR_SEMANTIC_ID)
        wait_for_snapshot_node(websocket_control, VERTICAL_PAGE_INDICATOR_SEMANTIC_ID)
        assert all(
            widget["type"] != "pageSwitcher"
            for page in pages
            for widget in page["widgets"]
        )
        horizontal_bounds = physical_page_indicator_bounds("horizontal")
        vertical_bounds = physical_page_indicator_bounds("vertical")
        page_bounds = physical_page_scroll_bounds()
        assert_compact_page_indicator_bounds(horizontal_bounds, vertical_bounds)
        assert_page_chrome_reaches_screen_edges(horizontal_bounds, vertical_bounds, page_bounds)
        assert page_bounds["bottom"] <= horizontal_bounds["top"]
        assert vertical_bounds["left"] == page_bounds["right"]
        assert vertical_bounds["right"] > page_bounds["right"]
        direct_select_notes_page(websocket_control)
        direct_select_home_page(websocket_control)
        direct_select_activity_page(websocket_control)
        direct_select_home_page_from_activity(websocket_control)
        restored_snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
        assert restored_snapshot["selectedPageId"] == FIXTURE_HOME_PAGE_ID
        assert_page_viewport_starts_at_top(restored_snapshot)
        run_device_operation("screenshot")
        run_device_operation("uiautomator-dump")
        assert (ARTIFACT_DIRECTORY / "screenshot.png").stat().st_size > 0
        assert (ARTIFACT_DIRECTORY / "window.xml").stat().st_size > 0
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )


def test_websocket_command_sheet_filters_page_commands_and_keeps_no_matches_read_only(
    websocket_control: socket.socket,
) -> None:
    initial = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    open_command_sheet()
    initial_catalogue_snapshot = wait_for_snapshot_node(
        websocket_control,
        COMMAND_SEARCH_SEMANTIC_ID,
    )
    visible_catalogue_entries = [
        node
        for node in initial_catalogue_snapshot["nodes"]
        if str(node.get("semanticId", "")).startswith(COMMAND_APP_ENTRY_PREFIX)
        and node.get("visible") is True
    ]
    assert visible_catalogue_entries
    assert all(
        DIKCIZ_DEBUG_PACKAGE not in str(node["semanticId"])
        for node in visible_catalogue_entries
    )
    visible_catalogue_labels = [
        str(node["text"])
        for node in sorted(
            visible_catalogue_entries,
            key=lambda node: int(node["bounds"]["top"]),
        )
    ]
    assert visible_catalogue_labels == sorted(visible_catalogue_labels, key=str.casefold)
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_SET_TEXT,
        semanticId=COMMAND_SEARCH_SEMANTIC_ID,
        text=FIXTURE_NOTES_PAGE_ID,
    )
    snapshot = wait_for_snapshot_node(websocket_control, COMMAND_SELECT_NOTES_SEMANTIC_ID)
    page_command = AUTOMATION.require_node(snapshot, COMMAND_SELECT_NOTES_SEMANTIC_ID)
    assert page_command["text"] == "Switch to Notes"
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=COMMAND_SELECT_NOTES_SEMANTIC_ID,
    )
    wait_for_selected_page(websocket_control, FIXTURE_NOTES_PAGE_ID)
    after_page_selection = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert selected_page(after_page_selection) == FIXTURE_NOTES_PAGE_ID
    assert [page["widgets"] for page in after_page_selection["launcher"]["home"]["pages"]] == [
        page["widgets"] for page in initial["launcher"]["home"]["pages"]
    ]
    open_command_sheet()
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_SET_TEXT,
        semanticId=COMMAND_SEARCH_SEMANTIC_ID,
        text=COMMAND_SETTINGS_PACKAGE_QUERY,
    )
    settings_entry = wait_for_command_app_entry(websocket_control, SETTINGS_APP_LABEL)
    assert settings_entry.startswith(COMMAND_APP_ENTRY_PREFIX)
    settings_entry_bounds = wait_for_physical_button_bounds(SETTINGS_APP_LABEL)
    assert settings_entry_bounds["bottom"] - settings_entry_bounds["top"] <= round(
        COMMAND_APP_ENTRY_MAXIMUM_HEIGHT_DP * device_density_scale(),
    )
    run_device_operation("screenshot")
    shutil.copyfile(
        ARTIFACT_DIRECTORY / "screenshot.png",
        ARTIFACT_DIRECTORY / COMMAND_APP_ICON_ARTIFACT_NAME,
    )
    assert (ARTIFACT_DIRECTORY / COMMAND_APP_ICON_ARTIFACT_NAME).stat().st_size > 0
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_SET_TEXT,
        semanticId=COMMAND_SEARCH_SEMANTIC_ID,
        text="no-such-dikciz-command",
    )
    wait_for_physical_text_bounds(COMMAND_NO_RESULTS_LABEL)
    no_match_snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
    assert all(
        not str(node.get("semanticId", "")).startswith(COMMAND_APP_ENTRY_PREFIX)
        for node in no_match_snapshot["nodes"]
    )
    after_no_match = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert after_no_match == after_page_selection


def test_websocket_settings_renames_pages_without_changing_coordinates(
    websocket_control: socket.socket,
) -> None:
    open_command_sheet()
    wait_for_snapshot_node(websocket_control, COMMAND_SETTINGS_SEMANTIC_ID)
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=COMMAND_SETTINGS_SEMANTIC_ID,
    )
    wait_for_snapshot_node(websocket_control, SETTINGS_HOME_TITLE_SEMANTIC_ID)
    wait_for_physical_text_bounds("Dikciz settings")
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_SET_TEXT,
        semanticId=SETTINGS_HOME_TITLE_SEMANTIC_ID,
        text=SETTINGS_RENAMED_PAGE_TITLE,
    )
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=SETTINGS_SAVE_PAGES_SEMANTIC_ID,
    )
    wait_for_snapshot_node(
        websocket_control,
        SETTINGS_SAVE_PAGES_SEMANTIC_ID,
        should_exist=False,
    )
    saved = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert page_title(saved, FIXTURE_HOME_PAGE_ID) == SETTINGS_RENAMED_PAGE_TITLE
    assert configuration_page(saved, FIXTURE_HOME_PAGE_ID)["position"] == {"column": 0, "row": 0}
