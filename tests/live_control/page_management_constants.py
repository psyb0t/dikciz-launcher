"""Fixtures for durable rail-edge page creation and page-menu reordering."""

from .constants import (
    FIXTURE_ACTIVITY_PAGE_ID,
    FIXTURE_HOME_PAGE_ID,
    FIXTURE_NOTES_PAGE_ID,
    PAGE_MENU_ACTION_PREFIX,
)


PAGE_MANAGEMENT_ARTIFACT_NAME = "page-management-menu.png"
PAGE_MANAGEMENT_NODES_KEY = "nodes"
PAGE_MENU_SECTION_ARRANGE = "Move this page"
PAGE_MOVE_DOWN_ACTION = "move-down"
PAGE_MOVE_LEFT_ACTION = "move-left"
PAGE_MOVE_RIGHT_ACTION = "move-right"
PAGE_MOVE_UP_ACTION = "move-up"
PAGE_MOVE_ACTION_LABELS = {
    PAGE_MOVE_LEFT_ACTION: "Move left",
    PAGE_MOVE_RIGHT_ACTION: "Move right",
    PAGE_MOVE_UP_ACTION: "Move up",
    PAGE_MOVE_DOWN_ACTION: "Move down",
}

# The provisional page was removed from page creation. Its rendered control and its
# semantic namespace must not come back, so both are asserted absent from the live UI.
REMOVED_NEW_PAGE_CANCEL_LABEL = "Cancel new page"
REMOVED_PAGE_PROVISIONAL_SEMANTIC_PREFIX = "page:provisional:"

# Each leg selects one fixture page, holds one rail edge, and states where every page
# ends up. The fixture home is `home` at 1H1V, `notes` at 2H1V, and `activity` at 1H2V.
# A leg that holds an edge next to the selected page would land somewhere else, which is
# what makes these expectations prove the global extremity.
PAGE_EDGE_INSERTION_CASES = (
    {
        "id": "horizontal-before-from-the-second-column",
        "axis": "horizontal",
        "after_dots": False,
        "selected_page_id": FIXTURE_NOTES_PAGE_ID,
        "new_page_position": {"column": 0, "row": 0},
        "shifted_positions": {
            FIXTURE_HOME_PAGE_ID: {"column": 1, "row": 0},
            FIXTURE_NOTES_PAGE_ID: {"column": 2, "row": 0},
            FIXTURE_ACTIVITY_PAGE_ID: {"column": 1, "row": 1},
        },
    },
    {
        "id": "horizontal-after-from-the-first-column",
        "axis": "horizontal",
        "after_dots": True,
        "selected_page_id": FIXTURE_HOME_PAGE_ID,
        "new_page_position": {"column": 2, "row": 0},
        "shifted_positions": {
            FIXTURE_HOME_PAGE_ID: {"column": 0, "row": 0},
            FIXTURE_NOTES_PAGE_ID: {"column": 1, "row": 0},
            FIXTURE_ACTIVITY_PAGE_ID: {"column": 0, "row": 1},
        },
    },
    {
        "id": "vertical-before-from-the-first-row",
        "axis": "vertical",
        "after_dots": False,
        "selected_page_id": FIXTURE_HOME_PAGE_ID,
        "new_page_position": {"column": 0, "row": 0},
        "shifted_positions": {
            FIXTURE_HOME_PAGE_ID: {"column": 0, "row": 1},
            FIXTURE_NOTES_PAGE_ID: {"column": 1, "row": 0},
            FIXTURE_ACTIVITY_PAGE_ID: {"column": 0, "row": 2},
        },
    },
    {
        "id": "vertical-after-from-the-first-row",
        "axis": "vertical",
        "after_dots": True,
        "selected_page_id": FIXTURE_HOME_PAGE_ID,
        "new_page_position": {"column": 0, "row": 2},
        "shifted_positions": {
            FIXTURE_HOME_PAGE_ID: {"column": 0, "row": 0},
            FIXTURE_NOTES_PAGE_ID: {"column": 1, "row": 0},
            FIXTURE_ACTIVITY_PAGE_ID: {"column": 0, "row": 1},
        },
    },
)

# One leg per direction, so the shared three-page fixture proves left, right, up, and
# down. Each leg selects its own page first, because the page menu always acts on the
# selected page. `select_axis` names the rail whose spoken description carries that
# page's title, which is how the leg waits for the right page to be on screen.
# Every leg swaps once and swaps straight back, so the configuration has to come back
# to the prepared document exactly.
PAGE_MOVE_CASES = (
    {
        "id": "home-right-and-back-along-the-horizontal-rail",
        "moved_page_id": FIXTURE_HOME_PAGE_ID,
        "select_axis": "horizontal",
        "action": PAGE_MOVE_RIGHT_ACTION,
        "return_action": PAGE_MOVE_LEFT_ACTION,
        "neighbour_page_id": FIXTURE_NOTES_PAGE_ID,
        "moved_position": {"column": 1, "row": 0},
        "neighbour_position": {"column": 0, "row": 0},
    },
    {
        "id": "home-down-and-back-along-the-vertical-rail",
        "moved_page_id": FIXTURE_HOME_PAGE_ID,
        "select_axis": "horizontal",
        "action": PAGE_MOVE_DOWN_ACTION,
        "return_action": PAGE_MOVE_UP_ACTION,
        "neighbour_page_id": FIXTURE_ACTIVITY_PAGE_ID,
        "moved_position": {"column": 0, "row": 1},
        "neighbour_position": {"column": 0, "row": 0},
    },
    {
        "id": "notes-left-and-back-along-the-horizontal-rail",
        "moved_page_id": FIXTURE_NOTES_PAGE_ID,
        "select_axis": "horizontal",
        "action": PAGE_MOVE_LEFT_ACTION,
        "return_action": PAGE_MOVE_RIGHT_ACTION,
        "neighbour_page_id": FIXTURE_HOME_PAGE_ID,
        "moved_position": {"column": 0, "row": 0},
        "neighbour_position": {"column": 1, "row": 0},
    },
    {
        "id": "activity-up-and-back-along-the-vertical-rail",
        "moved_page_id": FIXTURE_ACTIVITY_PAGE_ID,
        "select_axis": "vertical",
        "action": PAGE_MOVE_UP_ACTION,
        "return_action": PAGE_MOVE_DOWN_ACTION,
        "neighbour_page_id": FIXTURE_HOME_PAGE_ID,
        "moved_position": {"column": 0, "row": 0},
        "neighbour_position": {"column": 0, "row": 1},
    },
)

# The fixture home page sits at the top-left corner, so these two directions have no
# existing neighbour to swap with.
PAGE_MOVE_UNAVAILABLE_PAGE_ID = FIXTURE_HOME_PAGE_ID
PAGE_MOVE_UNAVAILABLE_SELECT_AXIS = "horizontal"
PAGE_MOVE_UNAVAILABLE_ACTIONS = (PAGE_MOVE_LEFT_ACTION, PAGE_MOVE_UP_ACTION)

# A home grown to exactly `limits.maxPages` pages. The filler pages take the free
# coordinates of a narrow rectangle rather than one long rail, so every position stays
# unique and small, and both rails keep empty room past their dots for a real edge hold.
MAX_PAGES_FILLER_ID_FORMAT = "max-pages-{column}-{row}"
MAX_PAGES_FILLER_TITLE_FORMAT = "Filler {column}H{row}V"
MAX_PAGES_GRID_COLUMNS = 4
MAX_PAGES_SELECTED_PAGE_ID = FIXTURE_HOME_PAGE_ID
MAX_PAGES_SELECT_AXIS = "horizontal"
MAX_PAGES_EDGE_CASES = (
    {
        "id": "horizontal-after-edge-at-the-maximum",
        "axis": "horizontal",
        "after_dots": True,
    },
    {
        "id": "vertical-after-edge-at-the-maximum",
        "axis": "vertical",
        "after_dots": True,
    },
)


def page_menu_action_semantic_id(page_id: str, action: str) -> str:
    return f"{PAGE_MENU_ACTION_PREFIX}{page_id}:{action}"
