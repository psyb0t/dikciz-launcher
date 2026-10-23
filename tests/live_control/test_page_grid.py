"""Exercise first-fit grid placement and the editable page grid."""

from . import *
from .test_native_data import PROTOCOLS, native_call
from .test_user_guide_capture import open_launcher_control_route


SCRIPT_DASHBOARD_WIDGET_TYPE = "scriptDashboard"
DASHBOARD_COLUMN_SPAN = 4
DASHBOARD_ROW_SPAN = 3
DASHBOARD_EXPECTED_COLUMN = 0
DASHBOARD_EXPECTED_ROW = 2
PAGE_FULL_CODE = "page_full"
GRID_BOUNDS_CODE = "grid_bounds"
FIRST_CELL_INDEX = 0
FILLED_PAGE_CELL = {
    "column": FIRST_CELL_INDEX,
    "row": FIRST_CELL_INDEX,
    "columnSpan": NATIVE_GRID_COLUMNS,
    "rowSpan": NATIVE_GRID_ROWS,
}
NARROW_GRID_COLUMNS = 2
NARROW_GRID_ROWS = 2
WIDE_GRID_COLUMNS = 6
WIDE_GRID_ROWS = 8
DEFAULT_GAP_DP = 8
DEFAULT_OUTER_PADDING_DP = 12


def selected_page_widgets(document: dict[str, Any]) -> list[dict[str, Any]]:
    return configuration_page(document, selected_page(document))["widgets"]


def selected_page_cells(document: dict[str, Any]) -> list[dict[str, int]]:
    return [widget["cell"] for widget in selected_page_widgets(document)]


def selected_page_cells_by_id(document: dict[str, Any]) -> dict[str, dict[str, int]]:
    """The persisted logical rectangle of every selected-page widget, keyed by ID."""
    return {widget["id"]: widget["cell"] for widget in selected_page_widgets(document)}


def is_landscape_bounds(bounds: dict[str, int]) -> bool:
    return bounds["right"] - bounds["left"] > bounds["bottom"] - bounds["top"]


def cells_overlap(first: dict[str, int], second: dict[str, int]) -> bool:
    return (
        first["column"] < second["column"] + second["columnSpan"]
        and second["column"] < first["column"] + first["columnSpan"]
        and first["row"] < second["row"] + second["rowSpan"]
        and second["row"] < first["row"] + first["rowSpan"]
    )


def cell_exceeds_grid(cell: dict[str, int], columns: int, rows: int) -> bool:
    """Whether a persisted cell's extent falls outside a candidate grid.

    This reads the public `cell` contract only. A cell is orphaned by where it
    ends, not by its span alone: an item with a legal span still falls outside a
    smaller grid when its column or row offset pushes its end past the edge.
    """
    return (
        cell["column"] + cell["columnSpan"] > columns
        or cell["row"] + cell["rowSpan"] > rows
    )


def orphaned_widget_ids(
    document: dict[str, Any],
    columns: int,
    rows: int,
) -> list[str]:
    """Selected-page widget IDs whose persisted cell cannot fit the given grid."""
    return [
        widget["id"]
        for widget in selected_page_widgets(document)
        if cell_exceeds_grid(widget["cell"], columns, rows)
    ]


def fill_selected_page(connection: socket.socket) -> dict[str, Any]:
    """Leave the selected page holding one widget that covers every cell."""
    document = AUTOMATION.require_configuration(
        AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
    )
    filled = copy.deepcopy(document)
    page = configuration_page(filled, selected_page(filled))
    page["widgets"] = page["widgets"][:1]
    page["widgets"][0]["cell"] = copy.deepcopy(FILLED_PAGE_CELL)
    return AUTOMATION.require_configuration(
        AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_REPLACE, config=filled),
    )


def test_add_widget_takes_the_first_fitting_free_cells(
    websocket_control: socket.socket,
) -> None:
    before = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    existing = selected_page_cells(before)

    created = AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_ADD_WIDGET,
        widgetType=SCRIPT_DASHBOARD_WIDGET_TYPE,
    )
    placed = created["cell"]

    # The fixture page fills rows 0 and 1, so the first place a 4x3 dashboard fits is row 2.
    assert placed["columnSpan"] == DASHBOARD_COLUMN_SPAN
    assert placed["rowSpan"] == DASHBOARD_ROW_SPAN
    assert placed["column"] == DASHBOARD_EXPECTED_COLUMN
    assert placed["row"] == DASHBOARD_EXPECTED_ROW
    for cell in existing:
        assert not cells_overlap(placed, cell)

    saved = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    saved_cells = selected_page_cells(saved)
    assert len(saved_cells) == len(existing) + 1
    assert placed in saved_cells


def test_a_full_page_refuses_insertion_and_writes_nothing(
    websocket_control: socket.socket,
) -> None:
    filled = fill_selected_page(websocket_control)
    assert len(selected_page_widgets(filled)) == 1

    _, rejected = AUTOMATION.request_error_with_id(
        websocket_control,
        AUTOMATION.TYPE_ADD_WIDGET,
        widgetType=SCRIPT_DASHBOARD_WIDGET_TYPE,
    )
    assert rejected[AUTOMATION.KEY_CODE] == PAGE_FULL_CODE

    after = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert after == filled


def test_page_grid_change_persists_and_is_reported(
    websocket_control: socket.socket,
) -> None:
    applied = AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_GRID_SET,
        columns=WIDE_GRID_COLUMNS,
        rows=WIDE_GRID_ROWS,
        gapDp=DEFAULT_GAP_DP,
        outerPaddingDp=DEFAULT_OUTER_PADDING_DP,
    )
    assert applied["columns"] == WIDE_GRID_COLUMNS
    assert applied["rows"] == WIDE_GRID_ROWS

    saved = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert saved["launcher"]["home"]["nativeGrid"] == {
        "columns": WIDE_GRID_COLUMNS,
        "rows": WIDE_GRID_ROWS,
        "gapDp": DEFAULT_GAP_DP,
        "outerPaddingDp": DEFAULT_OUTER_PADDING_DP,
    }

    snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
    assert snapshot["grid"]["columns"] == WIDE_GRID_COLUMNS
    assert snapshot["grid"]["rows"] == WIDE_GRID_ROWS


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_page_grid_change_evicts_overflow_items(
    websocket_control: socket.socket, protocol,
) -> None:
    before = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    result = native_call(protocol, AUTOMATION.TYPE_GRID_SET, {
        "columns": 1, "rows": 1, "gapDp": DEFAULT_GAP_DP,
        "outerPaddingDp": DEFAULT_OUTER_PADDING_DP,
    })
    after = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    evicted = []
    for page in before["launcher"]["home"]["pages"]:
        saved = configuration_page(after, page["id"])
        expected = [dict(page["widgets"][0], cell={
            "column": 0, "row": 0, "columnSpan": 1, "rowSpan": 1,
        })] if page["widgets"] else []
        assert saved["widgets"] == expected
        evicted.extend({"pageId": page["id"], "id": widget["id"]} for widget in page["widgets"][1:])
    assert result["evictedWidgets"] == evicted


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_page_grid_shrink_preserves_full_width_html_and_contents(
    websocket_control: socket.socket, protocol,
) -> None:
    before = fill_selected_page(websocket_control)
    before["launcher"]["home"]["nativeGrid"]["columns"] = 8
    selected_page_widgets(before)[0]["cell"]["columnSpan"] = 8
    before = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=before),
    )
    original = selected_page_widgets(before)[0]
    grid = before["launcher"]["home"]["nativeGrid"]
    columns = grid["columns"] - 1
    rows = grid["rows"] - 1
    result = native_call(protocol, AUTOMATION.TYPE_GRID_SET, {
        "columns": columns, "rows": rows, "gapDp": DEFAULT_GAP_DP,
        "outerPaddingDp": DEFAULT_OUTER_PADDING_DP,
    })
    assert result["evictedWidgets"] == []
    after = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    expected = copy.deepcopy(original)
    expected["cell"] = {
        "column": 0, "row": 0, "columnSpan": columns, "rowSpan": rows,
    }
    assert selected_page_widgets(after) == [expected]
    snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
    assert snapshot["grid"]["columns"] == columns
    reference = next(item for item in snapshot["widgetReferences"] if item["id"] == original["id"])
    assert reference["cell"] == expected["cell"]
    assert reference["bounds"]["right"] > reference["bounds"]["left"]
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / ("grid-shrink-" + protocol + ".png"))


def test_rotation_round_trip_keeps_selected_page_cells_unchanged(
    websocket_control: socket.socket,
) -> None:
    control = websocket_control
    try:
        emulator_device_orientation_set(EMULATOR_DEVICE_ORIENTATION_PORTRAIT)
        control.close()
        control = wait_for_restarted_websocket_control()
        portrait_bounds = wait_for_page_viewport_orientation(
            EMULATOR_DEVICE_ORIENTATION_PORTRAIT,
        )
        assert not is_landscape_bounds(portrait_bounds), portrait_bounds
        portrait_cells = selected_page_cells_by_id(
            AUTOMATION.require_configuration(
                AUTOMATION.request(control, AUTOMATION.TYPE_CONFIG_GET),
            ),
        )
        assert portrait_cells

        emulator_device_orientation_set(EMULATOR_DEVICE_ORIENTATION_LANDSCAPE)
        control.close()
        control = wait_for_restarted_websocket_control()
        landscape_bounds = wait_for_page_viewport_orientation(
            EMULATOR_DEVICE_ORIENTATION_LANDSCAPE,
        )
        # The viewport really changed shape, so identical cells prove the saved
        # model is logical rather than a remembered pixel rectangle.
        assert is_landscape_bounds(landscape_bounds), landscape_bounds
        landscape_cells = selected_page_cells_by_id(
            AUTOMATION.require_configuration(
                AUTOMATION.request(control, AUTOMATION.TYPE_CONFIG_GET),
            ),
        )
        assert landscape_cells == portrait_cells

        emulator_device_orientation_set(EMULATOR_DEVICE_ORIENTATION_PORTRAIT)
        control.close()
        control = wait_for_restarted_websocket_control()
        restored_bounds = wait_for_page_viewport_orientation(
            EMULATOR_DEVICE_ORIENTATION_PORTRAIT,
        )
        assert not is_landscape_bounds(restored_bounds), restored_bounds
        restored_cells = selected_page_cells_by_id(
            AUTOMATION.require_configuration(
                AUTOMATION.request(control, AUTOMATION.TYPE_CONFIG_GET),
            ),
        )
        assert restored_cells == portrait_cells
    finally:
        emulator_device_orientation_set(EMULATOR_DEVICE_ORIENTATION_PORTRAIT)
        wait_for_restarted_websocket_control().close()


def test_grid_reflow_settings_shrinks_widget_through_visible_controls(websocket_control):
    before = fill_selected_page(websocket_control)
    original = selected_page_widgets(before)[0]
    columns = before["launcher"]["home"]["nativeGrid"]["columns"] - 1
    open_launcher_control_route(websocket_control, LAUNCHER_CONTROL_SETTINGS_SEMANTIC_ID)
    scroll_dialog_to_text("Columns")
    AUTOMATION.request(
        websocket_control, AUTOMATION.TYPE_SET_TEXT,
        semanticId="settings:grid:columns", text=str(columns),
    )
    direct_tap(scroll_dialog_to_text("Save page grid"))
    saved = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    expected = copy.deepcopy(original)
    expected["cell"]["columnSpan"] = columns
    assert selected_page_widgets(saved) == [expected]
    snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
    assert snapshot["grid"]["columns"] == columns
    assert all(node["semanticId"] != "dialog:close" for node in snapshot["nodes"])
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "grid-shrink-settings.png")
