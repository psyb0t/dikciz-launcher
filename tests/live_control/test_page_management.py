"""Durable rail-edge page creation and explicit page-menu reordering."""

from . import *


def page_positions(document: dict[str, Any]) -> dict[str, dict[str, int]]:
    """Every page's persisted coordinate, keyed by page ID."""
    return {
        page["id"]: page["position"]
        for page in document["launcher"]["home"]["pages"]
    }


def page_records_without_positions(document: dict[str, Any]) -> dict[str, dict[str, Any]]:
    """Every page minus its coordinate, so a reorder can be proved to change nothing else."""
    return {
        page["id"]: {key: value for key, value in page.items() if key != "position"}
        for page in document["launcher"]["home"]["pages"]
    }



def apply_configuration(
    connection: socket.socket,
    document: dict[str, Any],
) -> dict[str, Any]:
    applied = AUTOMATION.require_configuration(
        AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_REPLACE, config=document),
    )
    assert applied == document
    return applied


def document_with_selected_page(
    document: dict[str, Any],
    page_id: str,
) -> dict[str, Any]:
    prepared = copy.deepcopy(document)
    prepared["launcher"]["home"]["selectedPageId"] = page_id
    return prepared


def document_with_locked_page(
    document: dict[str, Any],
    page_id: str,
) -> dict[str, Any]:
    prepared = copy.deepcopy(document)
    configuration_page(prepared, page_id)["locked"] = True
    return prepared


def document_at_maximum_page_count(
    document: dict[str, Any],
    selected_page_id: str,
) -> dict[str, Any]:
    """The fixture grown to exactly `limits.maxPages` pages, with one page selected.

    Filler pages take free coordinates of a rectangle that is only as tall as it needs
    to be, so every position stays unique and far inside the bounds the launcher accepts.
    """
    prepared = document_with_selected_page(document, selected_page_id)
    pages = prepared["launcher"]["home"]["pages"]
    maximum = prepared["limits"]["maxPages"]
    columns = MAX_PAGES_GRID_COLUMNS
    rows = (maximum + columns - 1) // columns
    taken = {(page["position"]["column"], page["position"]["row"]) for page in pages}
    free = [
        (column, row)
        for column in range(columns)
        for row in range(rows)
        if (column, row) not in taken
    ]
    needed = maximum - len(pages)
    assert 0 <= needed <= len(free), (needed, len(free))
    for column, row in free[:needed]:
        append_empty_page(
            prepared,
            MAX_PAGES_FILLER_ID_FORMAT.format(column=column, row=row),
            MAX_PAGES_FILLER_TITLE_FORMAT.format(column=column + 1, row=row + 1),
            column=column,
            row=row,
        )
    assert len(prepared["launcher"]["home"]["pages"]) == maximum
    return prepared


def assert_no_removed_provisional_controls(connection: socket.socket) -> None:
    """The created page is final, so no cancel control and no provisional node exists."""
    snapshot = AUTOMATION.request(connection, AUTOMATION.TYPE_SNAPSHOT)
    provisional = [
        node[AUTOMATION.KEY_SEMANTIC_ID]
        for node in snapshot[PAGE_MANAGEMENT_NODES_KEY]
        if node[AUTOMATION.KEY_SEMANTIC_ID].startswith(
            REMOVED_PAGE_PROVISIONAL_SEMANTIC_PREFIX,
        )
    ]
    assert not provisional, provisional
    visible = physical_visible_texts()
    assert REMOVED_NEW_PAGE_CANCEL_LABEL not in visible, visible


def wait_for_page_positions(
    connection: socket.socket,
    expected: dict[str, dict[str, int]],
) -> dict[str, Any]:
    deadline = time.monotonic() + CONTROL_PLANE_RESTART_TIMEOUT_SECONDS
    document = current_configuration(connection)
    while time.monotonic() < deadline:
        document = current_configuration(connection)
        if page_positions(document) == expected:
            return document
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"page positions stayed {page_positions(document)}, wanted {expected}")


def open_selected_page_menu(connection: socket.socket, page_id: str) -> None:
    """Long press the real page canvas and wait for that page's own menu to render.

    The menu is titled with the page it acts on, so waiting for that exact title also
    proves the long press reached the intended page rather than a stale canvas. The
    title carries the page address, and a move changes that address, so the expected
    title comes from the live configuration rather than from a caller's snapshot.
    """
    direct_long_press_at(*center(physical_page_scroll_bounds()))
    wait_for_physical_text_bounds(page_menu_title(current_configuration(connection), page_id))


def open_page_menu_and_require_node(
    connection: socket.socket,
    page_id: str,
    action: str,
) -> tuple[str, dict[str, Any]]:
    """Open the real page menu by long press and read one move control from it."""
    open_selected_page_menu(connection, page_id)
    semantic_id = page_menu_action_semantic_id(page_id, action)
    snapshot = wait_for_snapshot_node(connection, semantic_id)
    return semantic_id, AUTOMATION.require_node(snapshot, semantic_id)


def tap_page_menu_action(connection: socket.socket, semantic_id: str) -> None:
    """Tap one page-menu control and wait for the menu it closed to disappear."""
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=semantic_id)
    wait_for_snapshot_node(connection, semantic_id, should_exist=False)


def dismiss_page_menu(connection: socket.socket, semantic_id: str) -> None:
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=DIALOG_CLOSE_SEMANTIC_ID)
    wait_for_snapshot_node(connection, semantic_id, should_exist=False)


def restore_fixture(connection: socket.socket, fixture: dict[str, Any]) -> None:
    """Put the launcher back on the fixture even when an assertion already failed.

    A failed page assertion leaves a real page layout, and sometimes an open modal,
    on the shared phone. Both are cleared here so the next test starts from the same
    place as the first one.
    """
    snapshot = AUTOMATION.request(connection, AUTOMATION.TYPE_SNAPSHOT)
    if any(
        node[AUTOMATION.KEY_SEMANTIC_ID] == DIALOG_CLOSE_SEMANTIC_ID
        for node in snapshot[PAGE_MANAGEMENT_NODES_KEY]
    ):
        AUTOMATION.request(
            connection,
            AUTOMATION.TYPE_TAP,
            semanticId=DIALOG_CLOSE_SEMANTIC_ID,
        )
    apply_configuration(connection, fixture)


def test_page_management_rail_edge_creates_a_durable_page_at_the_global_extremity(
    websocket_control: socket.socket,
) -> None:
    fixture = current_configuration(websocket_control)
    fixture_page_ids = page_ids(fixture)
    try:
        for case in PAGE_EDGE_INSERTION_CASES:
            selected_page_id = case["selected_page_id"]
            prepared = document_with_selected_page(fixture, selected_page_id)
            apply_configuration(websocket_control, prepared)
            wait_for_physical_selected_page(
                page_title(prepared, selected_page_id),
                case["axis"],
            )

            direct_long_press_at(
                *page_indicator_empty_rail_point(case["axis"], case["after_dots"]),
            )
            created = wait_for_page_count(websocket_control, len(fixture_page_ids) + 1)

            new_page_id = selected_page(created)
            assert new_page_id not in fixture_page_ids, (case["id"], new_page_id)
            assert configuration_page(created, new_page_id)["widgets"] == [], case["id"]
            expected_positions = {
                **case["shifted_positions"],
                new_page_id: case["new_page_position"],
            }
            assert page_positions(created) == expected_positions, case["id"]

            assert_no_removed_provisional_controls(websocket_control)

            # Navigating away and back must find the same saved page, because creation
            # already committed it rather than leaving it pending on this screen.
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_SELECT_PAGE,
                pageId=selected_page_id,
            )
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_SELECT_PAGE,
                pageId=new_page_id,
            )
            wait_for_selected_page(websocket_control, new_page_id)
            navigated = current_configuration(websocket_control)
            assert page_positions(navigated) == expected_positions, case["id"]
            assert_no_removed_provisional_controls(websocket_control)
    finally:
        restore_fixture(websocket_control, fixture)


def test_page_management_menu_moves_swap_only_with_an_existing_neighbour(
    websocket_control: socket.socket,
) -> None:
    fixture = current_configuration(websocket_control)
    fixture_positions = page_positions(fixture)
    fixture_records = page_records_without_positions(fixture)
    home_page_id = fixture["launcher"]["home"]["homePageId"]

    try:
        labelled = apply_configuration(
            websocket_control,
            document_with_selected_page(fixture, FIXTURE_HOME_PAGE_ID),
        )
        wait_for_physical_selected_page(page_title(labelled, FIXTURE_HOME_PAGE_ID))
        open_selected_page_menu(websocket_control, FIXTURE_HOME_PAGE_ID)
        visible = physical_visible_texts()
        assert PAGE_MENU_SECTION_ARRANGE in visible, visible
        for label in PAGE_MOVE_ACTION_LABELS.values():
            assert label in visible, (label, visible)
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / PAGE_MANAGEMENT_ARTIFACT_NAME,
        )
        dismiss_page_menu(
            websocket_control,
            page_menu_action_semantic_id(FIXTURE_HOME_PAGE_ID, PAGE_MOVE_RIGHT_ACTION),
        )

        for case in PAGE_MOVE_CASES:
            moved_page_id = case["moved_page_id"]
            neighbour_page_id = case["neighbour_page_id"]
            prepared = apply_configuration(
                websocket_control,
                document_with_selected_page(fixture, moved_page_id),
            )
            wait_for_physical_selected_page(
                page_title(prepared, moved_page_id),
                case["select_axis"],
            )

            semantic_id, node = open_page_menu_and_require_node(
                websocket_control,
                moved_page_id,
                case["action"],
            )
            assert node[AUTOMATION.KEY_ENABLED] is True, case["id"]
            tap_page_menu_action(websocket_control, semantic_id)

            swapped = wait_for_page_positions(
                websocket_control,
                {
                    **fixture_positions,
                    moved_page_id: case["moved_position"],
                    neighbour_page_id: case["neighbour_position"],
                },
            )
            # Only the two coordinates changed: IDs, titles, locks, widgets, the home page,
            # and the selected page all survive the swap.
            assert page_records_without_positions(swapped) == fixture_records, case["id"]
            assert swapped["launcher"]["home"]["homePageId"] == home_page_id, case["id"]
            assert selected_page(swapped) == moved_page_id, case["id"]

            return_semantic_id, return_node = open_page_menu_and_require_node(
                websocket_control,
                moved_page_id,
                case["return_action"],
            )
            assert return_node[AUTOMATION.KEY_ENABLED] is True, case["id"]
            tap_page_menu_action(websocket_control, return_semantic_id)
            restored = wait_for_page_positions(websocket_control, fixture_positions)
            assert restored == prepared, case["id"]

        cornered = apply_configuration(
            websocket_control,
            document_with_selected_page(fixture, PAGE_MOVE_UNAVAILABLE_PAGE_ID),
        )
        wait_for_physical_selected_page(
            page_title(cornered, PAGE_MOVE_UNAVAILABLE_PAGE_ID),
            PAGE_MOVE_UNAVAILABLE_SELECT_AXIS,
        )
        for action in PAGE_MOVE_UNAVAILABLE_ACTIONS:
            semantic_id, node = open_page_menu_and_require_node(
                websocket_control,
                PAGE_MOVE_UNAVAILABLE_PAGE_ID,
                action,
            )
            assert node[AUTOMATION.KEY_ENABLED] is False, action
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId=semantic_id)
            assert current_configuration(websocket_control) == cornered, action
            dismiss_page_menu(websocket_control, semantic_id)
    finally:
        restore_fixture(websocket_control, fixture)


def test_page_management_rail_edge_is_refused_at_the_maximum_page_count(
    websocket_control: socket.socket,
) -> None:
    fixture = current_configuration(websocket_control)
    try:
        filled = apply_configuration(
            websocket_control,
            document_at_maximum_page_count(fixture, MAX_PAGES_SELECTED_PAGE_ID),
        )
        filled_title = page_title(filled, MAX_PAGES_SELECTED_PAGE_ID)
        assert len(page_ids(filled)) == filled["limits"]["maxPages"]
        wait_for_physical_selected_page(filled_title, MAX_PAGES_SELECT_AXIS)

        for case in MAX_PAGES_EDGE_CASES:
            direct_long_press_at(
                *page_indicator_empty_rail_point(case["axis"], case["after_dots"]),
            )
            # A refused creation writes nothing, so the saved document stays identical
            # and the same page stays selected.
            wait_for_physical_selected_page(filled_title, MAX_PAGES_SELECT_AXIS)
            assert current_configuration(websocket_control) == filled, case["id"]
    finally:
        restore_fixture(websocket_control, fixture)


def test_page_management_locked_page_creates_no_page_and_offers_no_move(
    websocket_control: socket.socket,
) -> None:
    fixture = current_configuration(websocket_control)
    locked_page_id = selected_page(fixture)
    try:
        locked = apply_configuration(
            websocket_control,
            document_with_locked_page(fixture, locked_page_id),
        )
        wait_for_physical_selected_page(page_title(locked, locked_page_id))

        direct_long_press_at(*page_indicator_empty_rail_point("horizontal", True))
        direct_long_press_at(*page_indicator_empty_rail_point("vertical", True))
        # A locked page cannot create one, so the saved document has to stay identical.
        wait_for_physical_selected_page(page_title(locked, locked_page_id))
        assert current_configuration(websocket_control) == locked

        direct_tap(wait_for_physical_description_bounds(LAUNCHER_CONTROL_OPEN_DESCRIPTION))
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_TAP,
            semanticId=LAUNCHER_CONTROL_MANAGE_PAGE_SEMANTIC_ID,
        )
        wait_for_physical_text_bounds(page_menu_title(locked, locked_page_id))
        snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
        present = [
            node[AUTOMATION.KEY_SEMANTIC_ID]
            for node in snapshot[PAGE_MANAGEMENT_NODES_KEY]
            if node[AUTOMATION.KEY_SEMANTIC_ID] in {
                page_menu_action_semantic_id(locked_page_id, action)
                for action in PAGE_MOVE_ACTION_LABELS
            }
        ]
        assert not present, present
        visible = physical_visible_texts()
        assert PAGE_MENU_SECTION_ARRANGE not in visible, visible
        for label in PAGE_MOVE_ACTION_LABELS.values():
            assert label not in visible, (label, visible)
        assert current_configuration(websocket_control) == locked
    finally:
        restore_fixture(websocket_control, fixture)
