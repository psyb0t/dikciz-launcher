"""Grant notification posting on either supported Android test device."""

import os
import time

from . import (
    AUTOMATION,
    COMMAND_SETTINGS_SEMANTIC_ID,
    DIKCIZ_DEBUG_PACKAGE,
    device_command,
    direct_tap,
    node_bounds,
    physical_nodes,
    wait_for_snapshot_node,
)


ANDROID_NOTIFICATION_PERMISSION_API = 33
CLOSE_AUTOMATION_SETTINGS_SEMANTIC_ID = "settings:automation:close"
GRANT_NOTIFICATION_PERMISSION_COMMAND = (
    f"pm grant {DIKCIZ_DEBUG_PACKAGE} android.permission.POST_NOTIFICATIONS"
)
GRANT_NOTIFICATIONS_SEMANTIC_ID = "settings:automation:grant-notifications"
NOTIFICATION_POSTING_ACCESS_KEY = "notificationPosting"
PAGE_CANVAS_SEMANTIC_ID = "page:canvas"
PAGE_MENU_COMMANDS_ACTION = "commands"
PAGE_MENU_SEMANTIC_PREFIX = "page:menu:"
PERMISSION_ALLOW_RESOURCE_SUFFIX = ":id/permission_allow_button"
PERMISSION_PROMPT_TIMEOUT_SECONDS = 20
POLL_INTERVAL_SECONDS = 1
SETTINGS_AUTOMATION_SEMANTIC_ID = "settings:automation"
TEST_REAL_ENVIRONMENT_NAME = "TEST_REAL"
TEST_REAL_ENVIRONMENT_VALUE = "1"


def ensure_notification_posting_permission(connection) -> None:
    if os.environ.get(TEST_REAL_ENVIRONMENT_NAME) != TEST_REAL_ENVIRONMENT_VALUE:
        device_command(GRANT_NOTIFICATION_PERMISSION_COMMAND)
        return
    if _notification_posting_is_allowed(connection):
        return
    _grant_notification_posting_through_settings(connection)
    assert _notification_posting_is_allowed(connection)


def _notification_posting_is_allowed(connection) -> bool:
    api_level = int(device_command("getprop ro.build.version.sdk").strip())
    if api_level < ANDROID_NOTIFICATION_PERMISSION_API:
        return True
    status = AUTOMATION.request(connection, AUTOMATION.TYPE_AUTOMATION_STATUS)
    return status["androidAccess"][NOTIFICATION_POSTING_ACCESS_KEY] is True


def _grant_notification_posting_through_settings(connection) -> None:
    snapshot = AUTOMATION.request(connection, AUTOMATION.TYPE_SNAPSHOT)
    selected_page_id = snapshot[AUTOMATION.KEY_SELECTED_PAGE_ID]
    assert isinstance(selected_page_id, str) and selected_page_id
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_LONG_PRESS,
        semanticId=PAGE_CANVAS_SEMANTIC_ID,
    )
    command_menu_semantic_id = (
        f"{PAGE_MENU_SEMANTIC_PREFIX}{selected_page_id}:{PAGE_MENU_COMMANDS_ACTION}"
    )
    _tap_semantic_node(connection, command_menu_semantic_id)
    _tap_semantic_node(connection, COMMAND_SETTINGS_SEMANTIC_ID)
    _tap_semantic_node(connection, SETTINGS_AUTOMATION_SEMANTIC_ID)
    _tap_semantic_node(connection, GRANT_NOTIFICATIONS_SEMANTIC_ID)
    _tap_android_permission_allow_button()
    _tap_semantic_node(connection, CLOSE_AUTOMATION_SETTINGS_SEMANTIC_ID)


def _tap_semantic_node(connection, semantic_id: str) -> None:
    wait_for_snapshot_node(connection, semantic_id)
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=semantic_id)


def _tap_android_permission_allow_button() -> None:
    deadline = time.monotonic() + PERMISSION_PROMPT_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        for node in physical_nodes():
            if not node.get("resource-id", "").endswith(PERMISSION_ALLOW_RESOURCE_SUFFIX):
                continue
            bounds = node_bounds(node)
            assert bounds is not None, node
            direct_tap(bounds)
            return
        time.sleep(POLL_INTERVAL_SECONDS)
    raise AssertionError("Android notification permission allow button was not visible")
