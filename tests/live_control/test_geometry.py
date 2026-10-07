"""Fixed page viewport, drag, and resize behavior."""

from . import *

def test_direct_bottom_edge_drag_clamps_to_the_fixed_page_viewport(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    initial_cell = configuration_widget(original, "welcome")["cell"]
    assert isinstance(initial_cell, dict)
    initial_snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
    assert_page_viewport_starts_at_top(initial_snapshot)
    page_bounds = physical_page_scroll_bounds()
    move_gesture_bounds = physical_widget_move_gesture_bounds()
    direct_swipe(
        center(move_gesture_bounds),
        (
            center(move_gesture_bounds)[0],
            page_bounds["bottom"] - DIRECT_BOTTOM_EDGE_INSET_PIXELS,
        ),
        duration_milliseconds=DIRECT_BOTTOM_EDGE_DRAG_DURATION_MILLISECONDS,
    )
    saved = wait_for_grid_layout_change(
        websocket_control,
        widget_cells(original),
    )
    moved_cell = configuration_widget(saved, "welcome")["cell"]
    assert isinstance(moved_cell, dict)
    assert moved_cell["row"] > initial_cell["row"]
    moved_snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
    assert_page_viewport_starts_at_top(moved_snapshot)
    moved_bounds = physical_widget_bounds()
    assert moved_bounds["bottom"] <= page_bounds["bottom"]
    direct_page_round_trip(websocket_control)
    restored = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert configuration_widget(restored, "welcome")["cell"] == moved_cell


@pytest.mark.parametrize(
    ("handle_name", "horizontal", "vertical", "expected_width", "expected_height"),
    (
        ("top", "center", "start", "unchanged", "smaller"),
        ("bottom", "center", "end", "unchanged", "larger"),
        ("left", "start", "center", "smaller", "unchanged"),
        ("right", "end", "center", "larger", "unchanged"),
        ("top_left", "start", "start", "smaller", "smaller"),
        ("top_right", "end", "start", "larger", "smaller"),
        ("bottom_left", "start", "end", "smaller", "larger"),
        ("bottom_right", "end", "end", "larger", "larger"),
    ),
)
def test_direct_resize_handles_save_the_expected_dimensions(
    websocket_control: socket.socket,
    handle_name: str,
    horizontal: str,
    vertical: str,
    expected_width: str,
    expected_height: str,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = configure_welcome_cell(original)
    initial_cell = configuration_widget(configured, "welcome")["cell"]
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=configured,
    )
    direct_long_press(physical_widget_move_gesture_bounds())
    wait_for_snapshot_node(websocket_control, RESIZE_ACTION_SEMANTIC_ID)
    direct_tap(physical_resize_action_bounds())
    wait_for_snapshot_node(websocket_control, RESIZE_ACTION_SEMANTIC_ID, should_exist=False)
    wait_for_snapshot_node(websocket_control, WELCOME_MOVE_HANDLE_SEMANTIC_ID)
    assert_move_gesture_spans_widget_top_edge()
    resize_bounds = physical_widget_bounds()
    x, y = resize_handle_coordinate(resize_bounds, horizontal, vertical)
    column_pitch, row_pitch = native_grid_cell_pitch()
    delta_x = 0 if expected_width == "unchanged" else column_pitch
    delta_y = 0 if expected_height == "unchanged" else row_pitch
    direct_swipe(
        (x, y),
        (x + delta_x, y + delta_y),
        duration_milliseconds=DIRECT_BOTTOM_EDGE_DRAG_DURATION_MILLISECONDS,
    )
    if handle_name == "bottom_right":
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / RESIZE_SELECTION_ARTIFACT_NAME,
        )
    indicator_bounds = physical_page_indicator_bounds()
    direct_tap(indicator_bounds)
    saved = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    cell = configuration_widget(saved, "welcome")["cell"]
    assert isinstance(cell, dict), f"{handle_name} did not save a fixed cell"
    wait_for_selected_page(websocket_control, FIXTURE_HOME_PAGE_ID)
    assert_move_gesture_spans_widget_top_edge()
    column_span = cell["columnSpan"]
    row_span = cell["rowSpan"]
    assert isinstance(column_span, int)
    assert isinstance(row_span, int)
    if expected_width == "unchanged":
        assert column_span == initial_cell["columnSpan"]
    if expected_width == "larger":
        assert column_span > initial_cell["columnSpan"]
    if expected_width == "smaller":
        assert column_span < initial_cell["columnSpan"]
    if expected_height == "unchanged":
        assert row_span == initial_cell["rowSpan"]
    if expected_height == "larger":
        assert row_span > initial_cell["rowSpan"]
    if expected_height == "smaller":
        assert row_span < initial_cell["rowSpan"]


def test_direct_resize_keeps_its_rendered_bounds_after_a_page_round_trip(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = configure_welcome_cell(original)
    initial_cell = configuration_widget(configured, "welcome")["cell"]
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=configured,
    )
    direct_long_press(physical_widget_move_gesture_bounds())
    wait_for_snapshot_node(websocket_control, RESIZE_ACTION_SEMANTIC_ID)
    direct_tap(physical_resize_action_bounds())
    wait_for_snapshot_node(websocket_control, RESIZE_ACTION_SEMANTIC_ID, should_exist=False)
    resize_bounds = physical_widget_bounds()
    start_x, start_y = resize_handle_coordinate(resize_bounds, "end", "end")
    direct_swipe(
        (start_x, start_y),
        (
            start_x + DIRECT_DRAG_PIXELS,
            start_y + DIRECT_DRAG_PIXELS,
        ),
        duration_milliseconds=DIRECT_BOTTOM_EDGE_DRAG_DURATION_MILLISECONDS,
    )
    direct_tap(physical_page_indicator_bounds())
    wait_for_selected_page(websocket_control, FIXTURE_HOME_PAGE_ID)
    bounds_before_round_trip = physical_widget_bounds()
    direct_page_round_trip(websocket_control)
    saved_after_round_trip = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    cell_after_round_trip = configuration_widget(saved_after_round_trip, "welcome")["cell"]
    assert isinstance(cell_after_round_trip, dict)
    column_span_after_round_trip = cell_after_round_trip["columnSpan"]
    row_span_after_round_trip = cell_after_round_trip["rowSpan"]
    assert isinstance(column_span_after_round_trip, int)
    assert isinstance(row_span_after_round_trip, int)
    assert column_span_after_round_trip > initial_cell["columnSpan"]
    assert row_span_after_round_trip > initial_cell["rowSpan"]
    bounds_after_round_trip = physical_widget_bounds()
    assert_bounds_unchanged(bounds_after_round_trip, bounds_before_round_trip)
    assert_rendered_widget_has_area(bounds_after_round_trip)


def test_html_resize_overlay_clips_oversized_widget_content(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    try:
        configured = configure_welcome_cell(original)
        welcome = configuration_widget(configured, "welcome")
        welcome["html"] = HTML_RESIZE_OVERFLOW_HTML
        welcome["css"] = HTML_RESIZE_OVERFLOW_CSS
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        )
        wait_for_physical_text_bounds(HTML_RESIZE_OVERFLOW_MARKER)
        direct_long_press(physical_widget_move_gesture_bounds())
        wait_for_snapshot_node(websocket_control, RESIZE_ACTION_SEMANTIC_ID)
        direct_tap(physical_resize_action_bounds())
        wait_for_snapshot_node(websocket_control, RESIZE_FILL_WIDTH_SEMANTIC_ID)
        wait_for_snapshot_node(websocket_control, RESIZE_FILL_HEIGHT_SEMANTIC_ID)
        widget_bounds = physical_widget_bounds()
        assert bounds_contain(physical_page_scroll_bounds(), widget_bounds)
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / HTML_RESIZE_OVERFLOW_ARTIFACT_NAME,
        )
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )


def test_direct_resize_fill_controls_cover_the_fixed_page_viewport(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = configure_welcome_cell(original)
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=configured,
    )
    direct_long_press(physical_widget_move_gesture_bounds())
    wait_for_snapshot_node(websocket_control, RESIZE_ACTION_SEMANTIC_ID)
    direct_tap(physical_resize_action_bounds())
    wait_for_snapshot_node(websocket_control, RESIZE_FILL_WIDTH_SEMANTIC_ID)
    wait_for_snapshot_node(websocket_control, RESIZE_FILL_HEIGHT_SEMANTIC_ID)
    direct_tap(physical_description_bounds("Fill page width for Dikciz"))
    wait_for_snapshot_node(websocket_control, RESIZE_FILL_WIDTH_SEMANTIC_ID)
    wait_for_snapshot_node(websocket_control, RESIZE_FILL_HEIGHT_SEMANTIC_ID)
    before_completion = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert configuration_widget(before_completion, "welcome") == configuration_widget(
        configured,
        "welcome",
    )
    direct_tap(physical_description_bounds("Fill page height for Dikciz"))
    wait_for_snapshot_node(websocket_control, RESIZE_FILL_WIDTH_SEMANTIC_ID)
    wait_for_snapshot_node(websocket_control, RESIZE_FILL_HEIGHT_SEMANTIC_ID)
    snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
    assert_page_viewport_starts_at_top(snapshot)
    filled_bounds = physical_widget_bounds()
    page_bounds = physical_page_scroll_bounds()
    assert_widget_frame_fills_page_with_theme_margin(filled_bounds, page_bounds)
    direct_tap(physical_page_indicator_bounds())
    wait_for_snapshot_node(websocket_control, RESIZE_FILL_WIDTH_SEMANTIC_ID, should_exist=False)
    wait_for_snapshot_node(websocket_control, RESIZE_FILL_HEIGHT_SEMANTIC_ID, should_exist=False)
    height_filled = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    height_filled_welcome = configuration_widget(height_filled, "welcome")
    height_filled_cell = height_filled_welcome["cell"]
    assert isinstance(height_filled_cell, dict)
    assert height_filled_cell == {
        "column": 0,
        "row": 0,
        "columnSpan": NATIVE_GRID_COLUMNS,
        "rowSpan": NATIVE_GRID_ROWS,
    }
    direct_page_round_trip(websocket_control)
    after_round_trip = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    after_round_trip_welcome = configuration_widget(after_round_trip, "welcome")
    assert after_round_trip_welcome["cell"] == height_filled_welcome["cell"]
