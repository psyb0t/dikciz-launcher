"""Exercise native pages that contain individual HTML and provider widgets."""

from . import *


HTML_WIDGET_ID = "html-grid-control"
HTML_WIDGET_TITLE = "HTML grid control"
HTML_WIDGET_INITIAL_LABEL = "HTML works"
HTML_WIDGET_CHANGED_LABEL = "HTML clicked"
HTML_WIDGET_BRIDGE_LABEL = "Check bridge"
HTML_WIDGET_BRIDGE_RESULT = "Bridge works"
HTML_WIDGET_CELL = {"column": 2, "row": 1, "columnSpan": 2, "rowSpan": 2}
MINIMIZE_ACTION_SUFFIX = ":minimize"
HTML_WIDGET_DOCUMENT = """
<button id="html-action" type="button">HTML works</button>
<button id="bridge-action" type="button" disabled>Check bridge</button>
<p id="bridge-status">Waiting for bridge</p>
"""
HTML_WIDGET_CSS = """
body { background: #16304a; color: white; font-family: sans-serif; }
button { margin: 8px; min-height: 40px; }
"""
HTML_WIDGET_JAVASCRIPT = """
document.getElementById('html-action').addEventListener('click', event => {
    event.target.textContent = 'HTML clicked';
});
window.addEventListener('dikciz-ready', () => {
    const action = document.getElementById('bridge-action');
    const status = document.getElementById('bridge-status');
    action.disabled = false;
    action.addEventListener('click', async () => {
        try {
            const result = await window.dikciz.command('configGet');
            if (!Array.isArray(result.config.launcher.home.pages)) {
                throw new Error('Missing pages');
            }
            status.textContent = 'Bridge works';
        } catch (error) {
            status.textContent = `Bridge failed: ${error.message}`;
        }
    });
});
"""
APP_GROUP_WIDGET_ID = "system-tools"
APP_GROUP_TITLE = "System tools"
APP_GROUP_CELL = {"column": 0, "row": 3, "columnSpan": 2, "rowSpan": 1}
APP_GROUP_ACTION_SUFFIX = ":app-group"
HTML_WIDGET_DELETE_ACTION_SEMANTIC_ID = f"widget:{HTML_WIDGET_ID}:delete"
HTML_WIDGET_EDIT_ACTION_SEMANTIC_ID = f"widget:{HTML_WIDGET_ID}:edit"
HTML_WIDGET_LOCK_ACTION_SEMANTIC_ID = f"widget:{HTML_WIDGET_ID}:lock"
HTML_WIDGET_RESIZE_ACTION_SEMANTIC_ID = f"widget:{HTML_WIDGET_ID}:resize"
REMOVED_HTML_WIDGET_Z_INDEX_ACTION_SEMANTIC_ID = f"widget:{HTML_WIDGET_ID}:z-index-input"


def native_html_widget() -> dict[str, object]:
    return {
        "id": HTML_WIDGET_ID,
        "type": "html",
        "title": HTML_WIDGET_TITLE,
        "html": HTML_WIDGET_DOCUMENT,
        "css": HTML_WIDGET_CSS,
        "javascript": HTML_WIDGET_JAVASCRIPT,
        "enabled": True,
        "cell": dict(HTML_WIDGET_CELL),
    }


def wait_for_provider_resize(connection: socket.socket, provider_id: str) -> dict[str, int]:
    deadline = time.monotonic() + PROVIDER_RENDER_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
        )
        provider = provider_widget_by_id(configuration, FIXTURE_NOTES_PAGE_ID, provider_id)
        cell = provider["cell"]
        if isinstance(cell, dict):
            return cell
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("the native provider resize did not persist")


def test_native_page_hosts_individual_html_and_provider_widgets() -> None:
    with connected_websocket_control() as initial_connection:
        start_deskclock_provider_addition(initial_connection)
    connection = wait_for_restarted_websocket_control()
    try:
        provider = wait_for_new_page_widget(connection, FIXTURE_NOTES_PAGE_ID, "provider")
        configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
        )
        notes_widget = configuration_widget(configuration, SEEDED_NOTES_WIDGET_ID)
        notes_title = notes_widget["title"]
        assert isinstance(notes_title, str)
        wait_for_physical_text_bounds(FIXTURE_NOTES_CARD_TEXT)
        wait_for_visible_provider_content()
        notes_bounds = physical_widget_bounds_for_title(notes_title)
        provider_bounds = provider_host_bounds()
        assert notes_bounds["bottom"] <= provider_bounds["top"]
        notes_page = configuration_page(configuration, FIXTURE_NOTES_PAGE_ID)
        notes_page["widgets"].append(native_html_widget())
        configuration["launcher"]["home"]["selectedPageId"] = FIXTURE_NOTES_PAGE_ID
        AUTOMATION.request(
            connection,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configuration,
        )
        wait_for_snapshot_node(connection, f"widget:{HTML_WIDGET_ID}")
        wait_for_snapshot_node(connection, f"widget:{provider['id']}")
        wait_for_snapshot_node(
            connection,
            f"widget:{HTML_WIDGET_ID}{MINIMIZE_ACTION_SUFFIX}",
            should_exist=False,
        )
        wait_for_snapshot_node(
            connection,
            f"widget:{provider['id']}{MINIMIZE_ACTION_SUFFIX}",
            should_exist=False,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)

        direct_long_press(wait_for_physical_text_bounds(HTML_WIDGET_INITIAL_LABEL))
        for semantic_id in (
            HTML_WIDGET_EDIT_ACTION_SEMANTIC_ID,
            HTML_WIDGET_RESIZE_ACTION_SEMANTIC_ID,
            HTML_WIDGET_LOCK_ACTION_SEMANTIC_ID,
            HTML_WIDGET_DELETE_ACTION_SEMANTIC_ID,
        ):
            wait_for_snapshot_node(connection, semantic_id)
        wait_for_snapshot_node(
            connection,
            REMOVED_HTML_WIDGET_Z_INDEX_ACTION_SEMANTIC_ID,
            should_exist=False,
        )
        direct_hide_keyboard()
        wait_for_snapshot_node(
            connection,
            HTML_WIDGET_RESIZE_ACTION_SEMANTIC_ID,
            should_exist=False,
        )
        direct_tap(wait_for_physical_text_bounds(HTML_WIDGET_INITIAL_LABEL))
        wait_for_physical_text_bounds(HTML_WIDGET_CHANGED_LABEL)
        direct_tap(wait_for_physical_text_bounds(HTML_WIDGET_BRIDGE_LABEL))
        wait_for_physical_text_bounds(HTML_WIDGET_BRIDGE_RESULT)
        wait_for_visible_provider_content()

        page_bounds = physical_page_scroll_bounds()
        provider_bounds = provider_host_bounds()
        assert bounds_contain(page_bounds, provider_bounds)
        direct_long_press(
            physical_description_bounds(
                WIDGET_MOVE_HANDLE_DESCRIPTION.format(provider["title"]),
            ),
        )
        direct_tap(physical_resize_action_bounds())
        provider_widget_bounds = provider_host_bounds()
        resize_start_x, resize_start_y = resize_handle_coordinate(
            provider_widget_bounds,
            "end",
            "end",
        )
        direct_swipe(
            (resize_start_x, resize_start_y),
            (
                resize_start_x + DIRECT_DRAG_PIXELS,
                resize_start_y + DIRECT_DRAG_PIXELS,
            ),
            duration_milliseconds=DIRECT_BOTTOM_EDGE_DRAG_DURATION_MILLISECONDS,
        )
        direct_tap(physical_page_indicator_bounds())
        resized = wait_for_provider_resize(connection, provider["id"])
        assert resized["columnSpan"] > 0
        assert resized["rowSpan"] > 0

        direct_select_home_page(connection)
        direct_select_notes_page(connection)
        wait_for_physical_text_bounds(HTML_WIDGET_INITIAL_LABEL)
        wait_for_physical_text_bounds(HTML_WIDGET_BRIDGE_LABEL)
        wait_for_visible_provider_content()
        final_configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
        )
        final_provider = provider_widget_by_id(
            final_configuration,
            FIXTURE_NOTES_PAGE_ID,
            provider["id"],
        )
        assert final_provider["appWidgetId"] == provider["appWidgetId"]
        assert final_provider["cell"] == resized
        verify_native_app_group_opens_saved_member(connection)
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / "native-page-grid.png",
        )
        run_device_operation("uiautomator-dump")
    finally:
        connection.close()


def verify_native_app_group_opens_saved_member(connection: socket.socket) -> None:
    configuration = AUTOMATION.require_configuration(
        AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
    )
    configuration_page(configuration, FIXTURE_NOTES_PAGE_ID)["widgets"].append(
        {
            "id": APP_GROUP_WIDGET_ID,
            "type": "appGroup",
            "title": APP_GROUP_TITLE,
            "components": [SETTINGS_ACTIVITY_COMPONENT],
            "enabled": True,
            "cell": dict(APP_GROUP_CELL),
        },
    )
    configuration["launcher"]["home"]["selectedPageId"] = FIXTURE_NOTES_PAGE_ID
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=configuration,
    )
    group_semantic_id = f"widget:{APP_GROUP_WIDGET_ID}"
    group_action_semantic_id = f"{group_semantic_id}{APP_GROUP_ACTION_SUFFIX}"
    wait_for_snapshot_node(connection, group_action_semantic_id)
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_TAP,
        semanticId=group_action_semantic_id,
    )
    direct_tap(wait_for_physical_text_bounds(SETTINGS_APP_LABEL))
    wait_for_foreground_application("com.android.settings")
    run_device_operation(
        "app-start",
        PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE,
        ACTIVITY=DIKCIZ_DEBUG_ACTIVITY,
    )
    wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)
