"""Public configuration inspection and mutation helpers."""

from .ui import *

def fixture_document() -> dict[str, Any]:
    document = json.loads(FIXTURE_CONFIGURATION_FILE.read_text(encoding="utf-8"))
    assert isinstance(document, dict)
    assert document.get("version") == FIXTURE_CONFIGURATION_VERSION
    return document


def configuration_page(document: dict[str, Any], page_id: str) -> dict[str, Any]:
    home = document["launcher"]["home"]
    pages = home["pages"]
    assert isinstance(pages, list)
    for page in pages:
        if page["id"] == page_id:
            assert isinstance(page, dict)
            return page
    raise AssertionError(f"configuration has no page: {page_id}")


def append_empty_page(
    document: dict[str, Any],
    page_id: str,
    title: str,
    column: int,
    row: int,
) -> None:
    """Append the default unlocked page in the shape the public configuration returns.

    The launcher always writes `locked` back on every page, so a generated page that
    omits it is not equal to the document `configReplace` answers with. Building the
    complete default here keeps a caller free to compare its own document against the
    live response.
    """
    home = document["launcher"]["home"]
    pages = home["pages"]
    assert isinstance(pages, list)
    pages.append(
        {
            "id": page_id,
            "title": title,
            "position": {"column": column, "row": row},
            "locked": False,
            "widgets": [],
        },
    )


def fixture_widget_cells() -> dict[str, dict[str, int]]:
    document = fixture_document()
    home = document["launcher"]["home"]
    pages = home["pages"]
    assert isinstance(pages, list)
    page = next(item for item in pages if item["id"] == FIXTURE_HOME_PAGE_ID)
    widgets = page["widgets"]
    assert isinstance(widgets, list)
    cells: dict[str, dict[str, int]] = {}
    for widget in widgets:
        assert isinstance(widget, dict)
        if widget["id"] not in FIXTURE_WIDGET_IDS:
            continue
        cell = widget["cell"]
        assert isinstance(cell, dict)
        widget_id = widget["id"]
        assert isinstance(widget_id, str)
        for field in WIDGET_CELL_FIELDS:
            assert isinstance(cell[field], int)
        cells[widget_id] = {field: cell[field] for field in WIDGET_CELL_FIELDS}
    return cells


def visible_fixture_widget_bounds() -> dict[str, dict[str, int]]:
    nodes = physical_nodes()
    bounds_by_title: dict[str, dict[str, int]] = {}
    for node in nodes:
        title = node.get("content-desc")
        if title not in FIXTURE_WIDGET_TITLES.values():
            continue
        raw_bounds = node.get("bounds")
        if raw_bounds is None:
            continue
        values = [int(value) for value in raw_bounds.replace("][", ",").strip("[]").split(",")]
        assert len(values) == 4
        bounds_by_title[title] = dict(zip(("left", "top", "right", "bottom"), values, strict=True))
    missing_titles = set(FIXTURE_WIDGET_TITLES.values()).difference(bounds_by_title)
    assert not missing_titles, f"UIAutomator did not expose fixture cards: {sorted(missing_titles)}"
    return {
        widget_id: bounds_by_title[title]
        for widget_id, title in FIXTURE_WIDGET_TITLES.items()
    }


def widget_cells(document: dict[str, Any]) -> dict[str, dict[str, int]]:
    cells: dict[str, dict[str, int]] = {}
    for widget_id in FIXTURE_WIDGET_IDS:
        cell = configuration_widget(document, widget_id)["cell"]
        assert isinstance(cell, dict), f"{widget_id} has no fixed cell"
        for field in WIDGET_CELL_FIELDS:
            assert isinstance(cell[field], int)
        cells[widget_id] = {field: cell[field] for field in WIDGET_CELL_FIELDS}
    return cells


def assert_visible_configured_layout(document: dict[str, Any]) -> None:
    """Checks rendered geometry against logical cells, not stored dp.

    Widgets sharing a grid row line up on top and height; a later row sits
    strictly below an earlier one; a wider column span renders wider.
    """
    bounds_by_id = visible_fixture_widget_bounds()
    cells = widget_cells(document)
    for bounds in bounds_by_id.values():
        assert bounds["right"] > bounds["left"]
        assert bounds["bottom"] > bounds["top"]
    for first_id, first_bounds in bounds_by_id.items():
        first_cell = cells[first_id]
        first_height = first_bounds["bottom"] - first_bounds["top"]
        first_width = first_bounds["right"] - first_bounds["left"]
        for second_id, second_bounds in bounds_by_id.items():
            if first_id == second_id:
                continue
            second_cell = cells[second_id]
            if first_cell["row"] == second_cell["row"]:
                second_height = second_bounds["bottom"] - second_bounds["top"]
                assert abs(first_bounds["top"] - second_bounds["top"]) <= PHYSICAL_BOUNDS_TOLERANCE_PIXELS
                assert abs(first_height - second_height) <= PHYSICAL_BOUNDS_TOLERANCE_PIXELS
            if first_cell["row"] < second_cell["row"]:
                assert first_bounds["top"] < second_bounds["top"]
            if first_cell["columnSpan"] > second_cell["columnSpan"]:
                second_width = second_bounds["right"] - second_bounds["left"]
                assert first_width > second_width


def assert_bounds_unchanged(
    actual: dict[str, int],
    expected: dict[str, int],
) -> None:
    for edge, expected_value in expected.items():
        actual_value = actual[edge]
        assert abs(actual_value - expected_value) <= PHYSICAL_BOUNDS_TOLERANCE_PIXELS


def assert_rendered_widget_has_area(bounds: dict[str, int]) -> None:
    assert bounds["right"] > bounds["left"]
    assert bounds["bottom"] > bounds["top"]


def wait_for_snapshot_node(
    connection: socket.socket,
    semantic_id: str,
    should_exist: bool = True,
) -> dict[str, Any]:
    deadline = time.monotonic() + CONTROL_PLANE_RESTART_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        snapshot = AUTOMATION.request(connection, AUTOMATION.TYPE_SNAPSHOT)
        found = any(
            isinstance(node, dict) and node.get("semanticId") == semantic_id
            for node in snapshot.get("nodes", [])
        )
        if found == should_exist:
            return snapshot
        time.sleep(0.1)
    expectation = "appear" if should_exist else "disappear"
    raise AssertionError(f"semantic ID did not {expectation}: {semantic_id}")


def wait_for_selected_page(connection: socket.socket, page_id: str) -> dict[str, Any]:
    deadline = time.monotonic() + CONTROL_PLANE_RESTART_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        snapshot = AUTOMATION.request(connection, AUTOMATION.TYPE_SNAPSHOT)
        if snapshot.get("selectedPageId") == page_id:
            return snapshot
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"selected page did not become: {page_id}")


def wait_for_configured_home_page(
    connection: socket.socket,
    page_id: str,
) -> dict[str, Any]:
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
        )
        if configuration["launcher"]["home"]["homePageId"] == page_id:
            return configuration
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"configured home page did not become: {page_id}")


def wait_for_automation_script_state(
    connection: socket.socket,
    script_id: str,
    state_key: str,
    expected_value: Any,
    timeout_seconds: float = CONFIGURATION_SAVE_TIMEOUT_SECONDS,
) -> dict[str, Any]:
    deadline = time.monotonic() + timeout_seconds
    observed_value: Any = None
    while time.monotonic() < deadline:
        configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
        )
        script = next(
            (item for item in configuration["scripts"] if item["id"] == script_id),
            None,
        )
        if isinstance(script, dict):
            observed_value = script.get("state", {}).get(state_key)
        if observed_value == expected_value:
            return configuration
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(
        f"automation script state did not update: {script_id}.{state_key} observed={observed_value!r}",
    )


def selected_page_title(connection: socket.socket, page_id: str) -> str:
    configuration = AUTOMATION.require_configuration(
        AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
    )
    title = configuration_page(configuration, page_id)["title"]
    assert isinstance(title, str)
    return title


def wait_for_physical_selected_page(
    page_title: str,
    axis: str = "horizontal",
) -> dict[str, int]:
    expected_fragment = PAGE_INDICATOR_SELECTED_TITLE_FRAGMENT.format(page_title)
    description_prefix = {
        "horizontal": HORIZONTAL_PAGE_INDICATOR_DESCRIPTION_PREFIX,
        "vertical": VERTICAL_PAGE_INDICATOR_DESCRIPTION_PREFIX,
    }[axis]
    deadline = time.monotonic() + PROVIDER_RENDER_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        try:
            return physical_bounds_for(
                lambda node: node.get("content-desc", "").startswith(description_prefix)
                and expected_fragment in node.get("content-desc", ""),
            )
        except AssertionError:
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    return physical_bounds_for(
        lambda node: node.get("content-desc", "").startswith(description_prefix)
        and expected_fragment in node.get("content-desc", ""),
    )


def direct_select_notes_page(connection: socket.socket) -> None:
    indicator_bounds = physical_page_indicator_bounds()
    y = center(indicator_bounds)[1]
    direct_swipe(
        (indicator_bounds["right"] - HANDLE_INSET_PIXELS, y),
        (indicator_bounds["left"] + HANDLE_INSET_PIXELS, y),
    )
    wait_for_selected_page(connection, FIXTURE_NOTES_PAGE_ID)
    wait_for_physical_selected_page(selected_page_title(connection, FIXTURE_NOTES_PAGE_ID))


def direct_select_home_page(connection: socket.socket) -> None:
    indicator_bounds = physical_page_indicator_bounds()
    y = center(indicator_bounds)[1]
    direct_swipe(
        (indicator_bounds["left"] + HANDLE_INSET_PIXELS, y),
        (indicator_bounds["right"] - HANDLE_INSET_PIXELS, y),
    )
    wait_for_selected_page(connection, FIXTURE_HOME_PAGE_ID)
    wait_for_physical_selected_page(selected_page_title(connection, FIXTURE_HOME_PAGE_ID))


def direct_select_activity_page(connection: socket.socket) -> None:
    indicator_bounds = physical_page_indicator_bounds("vertical")
    x = center(indicator_bounds)[0]
    y = center(indicator_bounds)[1]
    distance = round(VERTICAL_PAGE_SWIPE_DISTANCE_DP * device_density_scale())
    direct_swipe(
        (x, y + distance),
        (x, y - distance),
    )
    wait_for_selected_page(connection, FIXTURE_ACTIVITY_PAGE_ID)
    wait_for_physical_selected_page(
        selected_page_title(connection, FIXTURE_ACTIVITY_PAGE_ID),
        axis="vertical",
    )


def direct_select_home_page_from_activity(connection: socket.socket) -> None:
    indicator_bounds = physical_page_indicator_bounds("vertical")
    x = center(indicator_bounds)[0]
    y = center(indicator_bounds)[1]
    distance = round(VERTICAL_PAGE_SWIPE_DISTANCE_DP * device_density_scale())
    direct_swipe(
        (x, y - distance),
        (x, y + distance),
    )
    wait_for_selected_page(connection, FIXTURE_HOME_PAGE_ID)
    wait_for_physical_selected_page(
        selected_page_title(connection, FIXTURE_HOME_PAGE_ID),
        axis="vertical",
    )


def wait_for_direct_page_selection(
    connection: socket.socket,
    page_id: str,
    axis: str,
) -> None:
    wait_for_selected_page(connection, page_id)
    wait_for_physical_selected_page(selected_page_title(connection, page_id), axis=axis)


def direct_page_round_trip(connection: socket.socket) -> None:
    selected_page_id = AUTOMATION.request(connection, AUTOMATION.TYPE_SNAPSHOT).get("selectedPageId")
    if selected_page_id == FIXTURE_HOME_PAGE_ID:
        direct_select_notes_page(connection)
        direct_select_home_page(connection)
        return
    if selected_page_id == FIXTURE_NOTES_PAGE_ID:
        direct_select_home_page(connection)
        direct_select_notes_page(connection)
        return
    if selected_page_id == FIXTURE_ACTIVITY_PAGE_ID:
        direct_select_home_page_from_activity(connection)
        direct_select_activity_page(connection)
        return
    raise AssertionError(f"fixture page round trip does not support page: {selected_page_id}")


def configuration_widget(document: dict[str, Any], widget_id: str) -> dict[str, Any]:
    home = document["launcher"]["home"]
    for page in home["pages"]:
        widgets = page["widgets"]
        assert isinstance(widgets, list)
        for widget in widgets:
            if widget.get("id") == widget_id:
                return widget
    raise AssertionError(f"configuration has no widget: {widget_id}")


def configuration_with_cross_page_duplicate_widget_id(
    document: dict[str, Any],
) -> dict[str, Any]:
    invalid = copy.deepcopy(document)
    duplicate = copy.deepcopy(configuration_widget(invalid, FIXTURE_WIDGET_IDS[0]))
    configuration_page(invalid, FIXTURE_NOTES_PAGE_ID)["widgets"].append(duplicate)
    return invalid


def configuration_with_case_insensitive_duplicate_widget_id(
    document: dict[str, Any],
) -> dict[str, Any]:
    invalid = copy.deepcopy(document)
    duplicate = copy.deepcopy(configuration_widget(invalid, FIXTURE_WIDGET_IDS[0]))
    widget_id = duplicate["id"]
    assert isinstance(widget_id, str)
    duplicate["id"] = widget_id.upper()
    configuration_page(invalid, FIXTURE_NOTES_PAGE_ID)["widgets"].append(duplicate)
    return invalid


def configuration_with_case_insensitive_duplicate_page_id(
    document: dict[str, Any],
) -> dict[str, Any]:
    invalid = copy.deepcopy(document)
    duplicate = configuration_page(invalid, FIXTURE_NOTES_PAGE_ID)
    duplicate["id"] = FIXTURE_HOME_PAGE_ID.upper()
    return invalid


@dataclass(frozen=True)
class InvalidConfigurationCase:
    name: str
    apply: Callable[[dict[str, Any]], None]


def replace_welcome_cell(
    field: str,
    value: object,
) -> Callable[[dict[str, Any]], None]:
    def apply(document: dict[str, Any]) -> None:
        configuration_widget(document, "welcome")["cell"][field] = value

    return apply


def remove_welcome_cell(field: str) -> Callable[[dict[str, Any]], None]:
    def apply(document: dict[str, Any]) -> None:
        del configuration_widget(document, "welcome")["cell"][field]

    return apply


def replace_welcome_property(
    property_name: str,
    value: object,
) -> Callable[[dict[str, Any]], None]:
    def apply(document: dict[str, Any]) -> None:
        configuration_widget(document, "welcome")[property_name] = value

    return apply


def configure_welcome_style(
    document: dict[str, Any],
    style: dict[str, Any],
) -> None:
    configuration_widget(document, "welcome")["style"] = style


def configure_default_font(
    document: dict[str, Any],
    font_id: str,
) -> None:
    home = document["launcher"]["home"]
    home["styleDefaults"] = {
        "widget": {"text": {"font": {"source": "bundled", "id": font_id}}},
    }


INVALID_CONFIGURATION_CASES = (
    InvalidConfigurationCase(
        "column_below_minimum",
        replace_welcome_cell("column", -1),
    ),
    InvalidConfigurationCase(
        "column_above_maximum",
        replace_welcome_cell("column", NATIVE_GRID_COLUMNS),
    ),
    InvalidConfigurationCase(
        "column_fractional",
        replace_welcome_cell("column", 0.5),
    ),
    InvalidConfigurationCase(
        "column_boolean",
        replace_welcome_cell("column", True),
    ),
    InvalidConfigurationCase(
        "column_missing",
        remove_welcome_cell("column"),
    ),
    InvalidConfigurationCase(
        "row_below_minimum",
        replace_welcome_cell("row", -1),
    ),
    InvalidConfigurationCase(
        "row_above_maximum",
        replace_welcome_cell("row", NATIVE_GRID_ROWS),
    ),
    InvalidConfigurationCase(
        "row_fractional",
        replace_welcome_cell("row", 0.5),
    ),
    InvalidConfigurationCase(
        "row_boolean",
        replace_welcome_cell("row", True),
    ),
    InvalidConfigurationCase(
        "row_missing",
        remove_welcome_cell("row"),
    ),
    InvalidConfigurationCase(
        "locked_string",
        replace_welcome_property("locked", "true"),
    ),
    InvalidConfigurationCase(
        "style_unknown_key",
        replace_welcome_property("style", {"madeUp": True}),
    ),
    InvalidConfigurationCase(
        "style_invalid_color",
        replace_welcome_property("style", {"background": {"color": "blue"}}),
    ),
    InvalidConfigurationCase(
        "style_opacity_above_maximum",
        replace_welcome_property("style", {"background": {"opacity": 1.01}}),
    ),
    InvalidConfigurationCase(
        "style_unknown_bundled_font",
        replace_welcome_property(
            "style",
            {"text": {"font": {"source": "bundled", "id": "not-a-font"}}},
        ),
    ),
    InvalidConfigurationCase(
        "style_local_font_path_traversal",
        replace_welcome_property(
            "style",
            {"text": {"font": {"source": "local", "id": "../escape.ttf"}}},
        ),
    ),
    InvalidConfigurationCase(
        "removed_z_index",
        replace_welcome_property("zIndex", 0),
    ),
)

CSS_STYLE_INVALID_CASES = (
    InvalidConfigurationCase(
        "border_radius_above_maximum",
        replace_welcome_property(
            "style",
            {"border": {"radius": MAXIMUM_STYLE_DIMENSION_DP + 1}},
        ),
    ),
    InvalidConfigurationCase(
        "legacy_logical_edge",
        replace_welcome_property("style", {"margin": {"start": 8}}),
    ),
)


def page_widgets_with_type(
    document: dict[str, Any],
    page_id: str,
    widget_type: str,
) -> list[dict[str, Any]]:
    widgets = configuration_page(document, page_id)["widgets"]
    assert isinstance(widgets, list)
    return [widget for widget in widgets if widget.get("type") == widget_type]


def wait_for_new_page_widget(
    connection: socket.socket,
    page_id: str,
    widget_type: str,
    known_widget_ids: set[str] | None = None,
) -> dict[str, Any]:
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        document = AUTOMATION.require_configuration(
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
        )
        widgets = page_widgets_with_type(document, page_id, widget_type)
        for widget in reversed(widgets):
            widget_id = widget.get("id")
            if isinstance(widget_id, str) and (
                known_widget_ids is None or widget_id not in known_widget_ids
            ):
                return widget
        time.sleep(0.1)
    raise AssertionError(f"{widget_type} widget did not appear on page {page_id}")


def wait_for_configuration_widget(
    connection: socket.socket,
    widget_id: str,
    matches: Callable[[dict[str, Any]], bool],
) -> dict[str, Any]:
    deadline = time.monotonic() + PROVIDER_RENDER_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        document = AUTOMATION.require_configuration(
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
        )
        widget = configuration_widget(document, widget_id)
        if matches(widget):
            return widget
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    document = AUTOMATION.require_configuration(
        AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
    )
    return configuration_widget(document, widget_id)


def provider_widget_by_id(
    document: dict[str, Any],
    page_id: str,
    provider_id: str,
) -> dict[str, Any]:
    return next(
        widget
        for widget in page_widgets_with_type(document, page_id, "provider")
        if widget["id"] == provider_id
    )


def wait_for_provider_position_change(
    connection: socket.socket,
    provider_id: str,
    initial_cell: Any,
) -> dict[str, Any]:
    deadline = time.monotonic() + CONTROL_PLANE_RESTART_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        document = AUTOMATION.require_configuration(
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
        )
        provider = provider_widget_by_id(document, FIXTURE_NOTES_PAGE_ID, provider_id)
        cell = provider["cell"]
        assert isinstance(cell, dict)
        if cell != initial_cell:
            return provider
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("the provider widget drag did not save a new cell")


def drag_new_provider_to_lower_viewport_edge(
    connection: socket.socket,
    provider: dict[str, Any],
    expected_content: str | None = None,
) -> dict[str, Any]:
    provider_id = provider["id"]
    provider_title = provider["title"]
    app_widget_id = provider["appWidgetId"]
    initial_provider_cell = copy.deepcopy(provider["cell"])
    assert isinstance(provider_id, str)
    assert isinstance(provider_title, str)
    assert isinstance(app_widget_id, int)
    assert isinstance(initial_provider_cell, dict)
    for field in WIDGET_CELL_FIELDS:
        assert isinstance(initial_provider_cell.get(field), int)
    page_bounds = bounds_for(
        AUTOMATION.request(connection, AUTOMATION.TYPE_SNAPSHOT),
        "pageScroll",
    )
    provider_move_handle_bounds = physical_description_bounds(
        WIDGET_MOVE_HANDLE_DESCRIPTION.format(provider_title),
    )
    direct_swipe(
        center(provider_move_handle_bounds),
        (
            center(provider_move_handle_bounds)[0],
            page_bounds["bottom"] - DIRECT_BOTTOM_EDGE_INSET_PIXELS,
        ),
        duration_milliseconds=DIRECT_BOTTOM_EDGE_DRAG_DURATION_MILLISECONDS,
    )
    moved_provider = wait_for_provider_position_change(
        connection,
        provider_id,
        initial_provider_cell,
    )
    assert moved_provider["appWidgetId"] == app_widget_id
    moved_cell = moved_provider["cell"]
    assert isinstance(moved_cell, dict)
    assert moved_cell["row"] > 0
    wait_for_snapshot_node(connection, f"widget:{provider_id}")
    wait_for_visible_provider_content(expected_content)
    return moved_provider


def drag_provider_within_initial_viewport(
    connection: socket.socket,
    provider: dict[str, Any],
) -> dict[str, Any]:
    provider_id = provider["id"]
    provider_title = provider["title"]
    initial_cell = copy.deepcopy(provider["cell"])
    assert isinstance(provider_id, str)
    assert isinstance(provider_title, str)
    assert isinstance(initial_cell, dict)
    move_handle_bounds = physical_description_bounds(
        WIDGET_MOVE_HANDLE_DESCRIPTION.format(provider_title),
    )
    move_start_x, move_start_y = center(move_handle_bounds)
    direct_swipe(
        (move_start_x, move_start_y),
        (
            move_start_x + DIRECT_DRAG_PIXELS,
            move_start_y + DIRECT_DRAG_PIXELS,
        ),
        duration_milliseconds=DIRECT_BOTTOM_EDGE_DRAG_DURATION_MILLISECONDS,
    )
    return wait_for_provider_position_change(connection, provider_id, initial_cell)


def wait_for_page_count(connection: socket.socket, expected_count: int) -> dict[str, Any]:
    deadline = time.monotonic() + CONTROL_PLANE_RESTART_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        document = AUTOMATION.require_configuration(
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
        )
        pages = document["launcher"]["home"]["pages"]
        assert isinstance(pages, list)
        if len(pages) == expected_count:
            return document
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"page count did not become {expected_count}")


@contextmanager
def connected_websocket_control() -> Generator[socket.socket, None, None]:
    """Open a control-plane connection, waiting for one to be available.

    The connection crosses an ADB forward, which accepts the socket before it
    knows whether it can still reach the device port. A launcher that has just
    been restarted, by a permission change or by a test that kills it, leaves
    the forward answering and then closing, so a single attempt reports a dead
    control plane for one that is on its way back.
    """
    with wait_for_restarted_websocket_control() as connection:
        yield connection


def wait_for_restarted_websocket_control() -> socket.socket:
    deadline = time.monotonic() + CONTROL_PLANE_RESTART_TIMEOUT_SECONDS
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        connection: socket.socket | None = None
        try:
            connection = socket.create_connection((AUTOMATION_HOST, AUTOMATION_PORT), timeout=10)
            connection.settimeout(10)
            AUTOMATION.perform_handshake(connection, AUTOMATION_HOST, AUTOMATION_PORT)
            AUTOMATION.hello(connection)
            AUTOMATION.require_configuration(
                AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
            )
            return connection
        except (OSError, AUTOMATION.WebSocketProtocolError) as exception:
            last_error = exception
            if connection is not None:
                connection.close()
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("Dikciz control plane did not become reachable") from last_error


def wait_for_foreground_application(package_name: str) -> str:
    deadline = time.monotonic() + CONTROL_PLANE_RESTART_TIMEOUT_SECONDS
    activity_state = ""
    while time.monotonic() < deadline:
        activity_state = run_device_operation("app-current")
        if package_name in activity_state:
            return activity_state
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(
        f"foreground activity did not belong to {package_name!r}: {activity_state}",
    )


def restore_system_home() -> None:
    run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
    wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)


def current_configuration(connection: socket.socket) -> dict[str, Any]:
    return AUTOMATION.require_configuration(
        AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
    )
