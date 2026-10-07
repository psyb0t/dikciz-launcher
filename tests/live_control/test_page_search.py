"""Page search from the home chrome: filter pages by name or address and jump to one."""

from . import *


def open_page_search_sheet() -> None:
    direct_tap(wait_for_physical_description_bounds(PAGE_SEARCH_OPEN_DESCRIPTION))
    wait_for_physical_text_bounds(PAGE_SEARCH_SHEET_TITLE)


def page_search_entry_semantic_id(page_id: str) -> str:
    return f"{PAGE_SEARCH_PAGE_SEMANTIC_PREFIX}{page_id}"


def set_page_search_query(connection: socket.socket, query: str) -> None:
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_SET_TEXT,
        semanticId=PAGE_SEARCH_QUERY_SEMANTIC_ID,
        text=query,
    )


def test_page_search_filters_by_name_and_selects_the_matching_page(
    websocket_control: socket.socket,
) -> None:
    configuration = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert selected_page(configuration) == STARTER_HOME_PAGE_ID

    open_page_search_sheet()
    # An empty query is the jump list, so every page is reachable without typing.
    for page_id in page_ids(configuration):
        wait_for_snapshot_node(websocket_control, page_search_entry_semantic_id(page_id))

    # An entry prints the name the owner gave the page next to the address the rest
    # of the product speaks, so either half is a way in.
    wait_for_physical_text_bounds(
        page_search_entry_label(configuration, STARTER_NOTES_PAGE_ID),
    )

    set_page_search_query(
        websocket_control,
        page_title(configuration, STARTER_NOTES_PAGE_ID),
    )
    wait_for_snapshot_node(
        websocket_control,
        page_search_entry_semantic_id(STARTER_NOTES_PAGE_ID),
    )
    wait_for_snapshot_node(
        websocket_control,
        page_search_entry_semantic_id(STARTER_HOME_PAGE_ID),
        should_exist=False,
    )

    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=page_search_entry_semantic_id(STARTER_NOTES_PAGE_ID),
    )
    wait_for_selected_page(websocket_control, STARTER_NOTES_PAGE_ID)


def test_page_search_matches_the_page_address_and_reports_no_match(
    websocket_control: socket.socket,
) -> None:
    configuration = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )

    open_page_search_sheet()
    set_page_search_query(
        websocket_control,
        page_script_address(configuration, STARTER_NOTES_PAGE_ID),
    )
    wait_for_snapshot_node(
        websocket_control,
        page_search_entry_semantic_id(STARTER_NOTES_PAGE_ID),
    )
    wait_for_snapshot_node(
        websocket_control,
        page_search_entry_semantic_id(STARTER_HOME_PAGE_ID),
        should_exist=False,
    )

    set_page_search_query(websocket_control, PAGE_SEARCH_UNMATCHABLE_QUERY)
    for page_id in page_ids(configuration):
        wait_for_snapshot_node(
            websocket_control,
            page_search_entry_semantic_id(page_id),
            should_exist=False,
        )
    empty_snapshot = wait_for_snapshot_node(
        websocket_control,
        PAGE_SEARCH_EMPTY_SEMANTIC_ID,
    )
    assert bounds_for(empty_snapshot, PAGE_SEARCH_EMPTY_SEMANTIC_ID)["bottom"] > 0
    wait_for_physical_text_bounds(PAGE_SEARCH_EMPTY_LABEL)

    # A search that matches nothing must not move the launcher off its page.
    current = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert selected_page(current) == STARTER_HOME_PAGE_ID
