"""Live behavior for the script dashboard and page-addressed Lua actions."""

from . import *


def wait_for_dashboard_script_enabled(
    connection: socket.socket,
    expected_enabled: bool,
) -> dict[str, Any]:
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
        )
        script = next(
            (
                candidate
                for candidate in configuration["scripts"]
                if candidate["id"] == SCRIPT_DASHBOARD_SCRIPT_ID
            ),
            None,
        )
        if isinstance(script, dict) and script.get("enabled") is expected_enabled:
            return configuration
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("script dashboard enable state did not update")


def wait_for_dashboard_patched_widget(
    connection: socket.socket,
) -> dict[str, Any]:
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
        )
        widget = configuration_widget(configuration, "fixture-detail")
        state = widget.get("state")
        if isinstance(state, dict) and state.get("body") == SCRIPT_DASHBOARD_EXPECTED_PATCHED_TEXT:
            return configuration
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("page-addressed patchWidget action did not update the HTML widget state")


def test_script_dashboard_runs_page_addressed_patch_and_exposes_controls(
    websocket_control: socket.socket,
) -> None:
    bundled = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_RESET),
    )
    assert SCRIPT_DASHBOARD_DEFAULT_SCRIPT_IDS <= {
        script["id"] for script in bundled["scripts"]
    }
    notes_page = configuration_page(bundled, "notes")
    assert any(
        widget.get("id") == SCRIPT_DASHBOARD_DEFAULT_WIDGET_ID
        and widget.get("type") == "scriptDashboard"
        for widget in notes_page["widgets"]
    )
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_SELECT_PAGE,
        pageId="notes",
    )
    for script_id in SCRIPT_DASHBOARD_DEFAULT_SCRIPT_IDS:
        wait_for_snapshot_node(
            websocket_control,
            default_script_dashboard_semantic_id(script_id, SCRIPT_DASHBOARD_ACTION_LOGS),
        )
    wait_for_physical_text_bounds("Lock screen")
    wait_for_physical_text_bounds("Current hour notification")
    wait_for_physical_text_bounds("Active notification digest")
    run_device_operation("screenshot")
    default_screenshot_artifact = ARTIFACT_DIRECTORY / SCRIPT_DASHBOARD_DEFAULT_ARTIFACT_NAME
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", default_screenshot_artifact)
    assert default_screenshot_artifact.stat().st_size > 0

    initial = fixture_document()
    configured = copy.deepcopy(initial)
    configuration_page(configured, FIXTURE_HOME_PAGE_ID)["widgets"].append(
        SCRIPT_DASHBOARD_WIDGET_DOCUMENT,
    )
    configured["scripts"] = [SCRIPT_DASHBOARD_SCRIPT_DOCUMENT]
    configured["automation"] = {
        "apiVersion": 1,
        "policies": [SCRIPT_DASHBOARD_POLICY_DOCUMENT],
        "scripts": [SCRIPT_DASHBOARD_SUBSCRIPTION_DOCUMENT],
    }
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=configured,
    )

    for action in (
        SCRIPT_DASHBOARD_ACTION_LOGS,
        SCRIPT_DASHBOARD_ACTION_TOGGLE_ENABLED,
        SCRIPT_DASHBOARD_ACTION_EDIT,
    ):
        wait_for_snapshot_node(
            websocket_control,
            script_dashboard_semantic_id(action),
        )

    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_AUTOMATION_TRIGGER,
        scriptId=SCRIPT_DASHBOARD_SCRIPT_ID,
    )
    persisted = wait_for_dashboard_patched_widget(websocket_control)
    persisted_state = configuration_widget(persisted, "fixture-detail")["state"]
    assert isinstance(persisted_state, dict)
    assert persisted_state["body"] == SCRIPT_DASHBOARD_EXPECTED_PATCHED_TEXT
    wait_for_physical_text_bounds(SCRIPT_DASHBOARD_EXPECTED_PATCHED_TEXT)
    wait_for_physical_text_bounds(SCRIPT_DASHBOARD_STATUS_TEXT)
    run_device_operation("screenshot")
    screenshot_artifact = ARTIFACT_DIRECTORY / SCRIPT_DASHBOARD_ARTIFACT_NAME
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", screenshot_artifact)
    run_device_operation("uiautomator-dump")
    assert screenshot_artifact.stat().st_size > 0
    assert (ARTIFACT_DIRECTORY / "window.xml").stat().st_size > 0

    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=script_dashboard_semantic_id(SCRIPT_DASHBOARD_ACTION_LOGS),
    )
    wait_for_snapshot_node(websocket_control, "settings:script-logs:close")
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId="settings:script-logs:close",
    )

    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=script_dashboard_semantic_id(SCRIPT_DASHBOARD_ACTION_TOGGLE_ENABLED),
    )
    disabled = wait_for_dashboard_script_enabled(websocket_control, False)
    script = next(
        candidate
        for candidate in disabled["scripts"]
        if candidate["id"] == SCRIPT_DASHBOARD_SCRIPT_ID
    )
    assert script["enabled"] is False


def test_native_script_editor_starts_with_an_event_handler(
    websocket_control: socket.socket,
) -> None:
    open_command_sheet()
    wait_for_snapshot_node(websocket_control, COMMAND_SETTINGS_SEMANTIC_ID)
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=COMMAND_SETTINGS_SEMANTIC_ID,
    )
    wait_for_snapshot_node(websocket_control, LUA_MANAGER_SETTINGS_SEMANTIC_ID)
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=LUA_MANAGER_SETTINGS_SEMANTIC_ID,
    )
    wait_for_snapshot_node(websocket_control, LUA_MANAGER_CREATE_SEMANTIC_ID)
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=LUA_MANAGER_CREATE_SEMANTIC_ID,
    )
    snapshot = wait_for_snapshot_node(websocket_control, LUA_MANAGER_SOURCE_SEMANTIC_ID)
    source = AUTOMATION.require_node(snapshot, LUA_MANAGER_SOURCE_SEMANTIC_ID)
    assert source[AUTOMATION.KEY_ROLE] == PAGE_RENAME_ROLE_TEXT_INPUT
    assert source[AUTOMATION.KEY_TEXT] == SCRIPT_EDITOR_DEFAULT_SOURCE
