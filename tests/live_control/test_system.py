"""Android Home role, system navigation, and configuration survival."""

from . import *

CONTROL_STATUS_UNEXPECTED_ARGUMENTS = {"unexpected": True}
DIALOG_CLOSE_ARTIFACT_NAME = "semantic-dialog-close.png"
SEMANTIC_SNAPSHOT_PAGE_SCROLL_ID = "pageScroll"
SEMANTIC_SNAPSHOT_PAGE_SCROLL_RESOURCE_SUFFIX = ":id/dikciz_page_scroll"
SEMANTIC_SNAPSHOT_ROLE_SCROLL_CONTAINER = "scrollContainer"
SEMANTIC_SNAPSHOT_ROLE_WIDGET = "widget"
SEMANTIC_SNAPSHOT_WELCOME_WIDGET_ID = "widget:welcome"


def assert_semantic_snapshot_node_metadata(
    snapshot: dict[str, Any],
    semantic_id: str,
    expected_role: str,
    expected_resource_suffix: str | None,
) -> None:
    node = AUTOMATION.require_node(snapshot, semantic_id)
    assert node[AUTOMATION.KEY_ROLE] == expected_role
    assert AUTOMATION.KEY_RESOURCE_ID in node
    resource_id = node[AUTOMATION.KEY_RESOURCE_ID]
    if expected_resource_suffix is None:
        assert resource_id is None
    else:
        assert isinstance(resource_id, str)
        assert resource_id.endswith(expected_resource_suffix)
    bounds_for(snapshot, semantic_id)


def assert_websocket_event(
    event: dict[str, Any],
    event_type: str,
    fields: dict[str, Any],
) -> None:
    assert set(event) == {
        AUTOMATION.KEY_EVENT,
        AUTOMATION.KEY_FIELDS,
        AUTOMATION.KEY_REVISION,
        AUTOMATION.KEY_TYPE,
    }
    assert event[AUTOMATION.KEY_TYPE] == AUTOMATION.TYPE_EVENT
    assert event[AUTOMATION.KEY_EVENT] == event_type
    assert isinstance(event[AUTOMATION.KEY_REVISION], int)
    assert event[AUTOMATION.KEY_REVISION] > 0
    assert event[AUTOMATION.KEY_FIELDS] == fields


def test_semantic_snapshot_exposes_resource_ids_and_stable_roles_through_both_control_planes(
    websocket_control: socket.socket,
) -> None:
    before = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    websocket_snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
    assert_semantic_snapshot_node_metadata(
        websocket_snapshot,
        SEMANTIC_SNAPSHOT_PAGE_SCROLL_ID,
        SEMANTIC_SNAPSHOT_ROLE_SCROLL_CONTAINER,
        SEMANTIC_SNAPSHOT_PAGE_SCROLL_RESOURCE_SUFFIX,
    )
    assert_semantic_snapshot_node_metadata(
        websocket_snapshot,
        SEMANTIC_SNAPSHOT_WELCOME_WIDGET_ID,
        SEMANTIC_SNAPSHOT_ROLE_WIDGET,
        None,
    )
    arguments = argparse.Namespace(
        host=AUTOMATION_HOST,
        port=MCP_PORT,
        expected_control_port=DEVICE_MCP_PORT,
        mutate=False,
    )
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        mcp_snapshot = MCP.require_tool_result(
            MCP.call_tool(arguments, session_id, 20, MCP.TOOL_SNAPSHOT),
        )
        assert mcp_snapshot == websocket_snapshot
    finally:
        MCP.delete_session(arguments, session_id)
    after = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert after == before


def test_active_native_dialogs_expose_safe_semantic_close_through_both_control_planes(
    websocket_control: socket.socket,
) -> None:
    before = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    open_command_sheet()
    websocket_snapshot = wait_for_snapshot_node(websocket_control, DIALOG_CLOSE_SEMANTIC_ID)
    websocket_close_node = AUTOMATION.require_node(websocket_snapshot, DIALOG_CLOSE_SEMANTIC_ID)
    arguments = argparse.Namespace(
        host=AUTOMATION_HOST,
        port=MCP_PORT,
        expected_control_port=DEVICE_MCP_PORT,
        mutate=False,
    )
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        mcp_snapshot = MCP.require_tool_result(
            MCP.call_tool(arguments, session_id, 21, MCP.TOOL_SNAPSHOT),
        )
        assert AUTOMATION.require_node(mcp_snapshot, DIALOG_CLOSE_SEMANTIC_ID) == websocket_close_node
    finally:
        MCP.delete_session(arguments, session_id)
    run_device_operation("screenshot")
    shutil.copyfile(
        ARTIFACT_DIRECTORY / "screenshot.png",
        ARTIFACT_DIRECTORY / DIALOG_CLOSE_ARTIFACT_NAME,
    )
    assert (ARTIFACT_DIRECTORY / DIALOG_CLOSE_ARTIFACT_NAME).stat().st_size > 0
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=DIALOG_CLOSE_SEMANTIC_ID,
    )
    wait_for_snapshot_node(websocket_control, DIALOG_CLOSE_SEMANTIC_ID, should_exist=False)
    wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)
    open_page_menu_on_selected_page()
    wait_for_snapshot_node(websocket_control, DIALOG_CLOSE_SEMANTIC_ID)
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=DIALOG_CLOSE_SEMANTIC_ID,
    )
    wait_for_snapshot_node(websocket_control, DIALOG_CLOSE_SEMANTIC_ID, should_exist=False)
    after = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert after == before


def test_page_menu_actions_are_individually_scriptable_through_both_control_planes(
    websocket_control: socket.socket,
) -> None:
    before = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    try:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_SELECT_PAGE,
            pageId=FIXTURE_NOTES_PAGE_ID,
        )
        wait_for_selected_page(websocket_control, FIXTURE_NOTES_PAGE_ID)
        open_page_menu_on_selected_page()
        websocket_snapshot = wait_for_snapshot_node(
            websocket_control,
            PAGE_MENU_NOTES_SET_HOME_SEMANTIC_ID,
        )
        websocket_nodes = {
            semantic_id: AUTOMATION.require_node(websocket_snapshot, semantic_id)
            for semantic_id in PAGE_MENU_NOTES_ACTION_SEMANTIC_IDS
        }
        for node in websocket_nodes.values():
            assert node[AUTOMATION.KEY_ROLE] == PAGE_MENU_ACTION_ROLE
            assert node[AUTOMATION.KEY_CLICKABLE] is True
            assert node[AUTOMATION.KEY_ENABLED] is True
        arguments = argparse.Namespace(
            host=AUTOMATION_HOST,
            port=MCP_PORT,
            expected_control_port=DEVICE_MCP_PORT,
            mutate=False,
        )
        session_id = MCP.initialize(arguments)
        try:
            MCP.notification_initialized(arguments, session_id)
            mcp_snapshot = MCP.require_tool_result(
                MCP.call_tool(arguments, session_id, 22, MCP.TOOL_SNAPSHOT),
            )
            assert {
                semantic_id: AUTOMATION.require_node(mcp_snapshot, semantic_id)
                for semantic_id in PAGE_MENU_NOTES_ACTION_SEMANTIC_IDS
            } == websocket_nodes
        finally:
            MCP.delete_session(arguments, session_id)
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / PAGE_MENU_ACTION_ARTIFACT_NAME,
        )
        assert (ARTIFACT_DIRECTORY / PAGE_MENU_ACTION_ARTIFACT_NAME).stat().st_size > 0
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_TAP,
            semanticId=PAGE_MENU_NOTES_SET_HOME_SEMANTIC_ID,
        )
        wait_for_configured_home_page(websocket_control, FIXTURE_NOTES_PAGE_ID)
        for semantic_id in PAGE_MENU_NOTES_ACTION_SEMANTIC_IDS:
            wait_for_snapshot_node(websocket_control, semantic_id, should_exist=False)
        open_page_menu_on_selected_page()
        current_home_snapshot = wait_for_snapshot_node(
            websocket_control,
            PAGE_MENU_NOTES_SET_HOME_SEMANTIC_ID,
        )
        assert (
            AUTOMATION.require_node(
                current_home_snapshot,
                PAGE_MENU_NOTES_SET_HOME_SEMANTIC_ID,
            )[AUTOMATION.KEY_ENABLED]
            is False
        )
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_TAP,
            semanticId=PAGE_MENU_NOTES_SET_HOME_SEMANTIC_ID,
        )
        disabled_snapshot = wait_for_snapshot_node(
            websocket_control,
            PAGE_MENU_NOTES_SET_HOME_SEMANTIC_ID,
        )
        assert (
            AUTOMATION.require_node(
                disabled_snapshot,
                PAGE_MENU_NOTES_SET_HOME_SEMANTIC_ID,
            )[AUTOMATION.KEY_ENABLED]
            is False
        )
        wait_for_configured_home_page(websocket_control, FIXTURE_NOTES_PAGE_ID)
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_TAP,
            semanticId=DIALOG_CLOSE_SEMANTIC_ID,
        )
        wait_for_snapshot_node(
            websocket_control,
            PAGE_MENU_NOTES_SET_HOME_SEMANTIC_ID,
            should_exist=False,
        )
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=before,
        )
        wait_for_selected_page(websocket_control, FIXTURE_HOME_PAGE_ID)
        wait_for_configured_home_page(websocket_control, FIXTURE_HOME_PAGE_ID)


def test_control_status_lists_the_complete_local_contract_through_both_control_planes(
    websocket_control: socket.socket,
) -> None:
    before = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    websocket_status = AUTOMATION.verify_control_status(websocket_control)
    arguments = argparse.Namespace(
        host=AUTOMATION_HOST,
        port=MCP_PORT,
        expected_control_port=DEVICE_MCP_PORT,
        mutate=False,
    )
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        assert MCP.verify_control_status(arguments, session_id, 10) == websocket_status
        with pytest.raises(AUTOMATION.WebSocketProtocolError):
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_CONTROL_STATUS,
                **CONTROL_STATUS_UNEXPECTED_ARGUMENTS,
            )
        with pytest.raises(MCP.McpProtocolError):
            MCP.require_tool_result(
                MCP.call_tool(
                    arguments,
                    session_id,
                    11,
                    MCP.TOOL_CONTROL_STATUS,
                    CONTROL_STATUS_UNEXPECTED_ARGUMENTS,
                ),
            )
    finally:
        MCP.delete_session(arguments, session_id)
    after = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert after == before


def test_websocket_events_are_discoverable_and_correlate_command_and_ui_changes(
    websocket_control: socket.socket,
) -> None:
    before_snapshot = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    with connected_websocket_control() as observer:
        snapshot_request_id, snapshot = AUTOMATION.request_with_id(
            websocket_control,
            AUTOMATION.TYPE_SNAPSHOT,
        )
        assert snapshot[AUTOMATION.KEY_SELECTED_PAGE_ID] == FIXTURE_HOME_PAGE_ID
        assert_websocket_event(
            AUTOMATION.receive_event(observer, WEBSOCKET_EVENT_COMMAND_COMPLETED),
            WEBSOCKET_EVENT_COMMAND_COMPLETED,
            {
                AUTOMATION.KEY_REQUEST_ID: snapshot_request_id,
                AUTOMATION.KEY_COMMAND_TYPE: AUTOMATION.TYPE_SNAPSHOT,
                AUTOMATION.KEY_OUTCOME: WEBSOCKET_EVENT_OUTCOME_SUCCEEDED,
            },
        )
    after_snapshot = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert after_snapshot == before_snapshot
    with connected_websocket_control() as observer:
        rejected_request_id, rejection = AUTOMATION.request_error_with_id(
            websocket_control,
            AUTOMATION.TYPE_SELECT_PAGE,
            pageId=WEBSOCKET_EVENT_UNKNOWN_PAGE_ID,
        )
        assert rejection[AUTOMATION.KEY_CODE] == WEBSOCKET_EVENT_REJECTED_CODE
        assert_websocket_event(
            AUTOMATION.receive_event(observer, WEBSOCKET_EVENT_COMMAND_COMPLETED),
            WEBSOCKET_EVENT_COMMAND_COMPLETED,
            {
                AUTOMATION.KEY_REQUEST_ID: rejected_request_id,
                AUTOMATION.KEY_COMMAND_TYPE: AUTOMATION.TYPE_SELECT_PAGE,
                AUTOMATION.KEY_OUTCOME: WEBSOCKET_EVENT_OUTCOME_REJECTED,
                AUTOMATION.KEY_CODE: WEBSOCKET_EVENT_REJECTED_CODE,
            },
        )
    after_rejection = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert after_rejection == before_snapshot
    with connected_websocket_control() as observer:
        select_page_request_id, selected_snapshot = AUTOMATION.request_with_id(
            websocket_control,
            AUTOMATION.TYPE_SELECT_PAGE,
            pageId=FIXTURE_NOTES_PAGE_ID,
        )
        assert selected_snapshot[AUTOMATION.KEY_SELECTED_PAGE_ID] == FIXTURE_NOTES_PAGE_ID
        assert_websocket_event(
            AUTOMATION.receive_event(observer, WEBSOCKET_EVENT_UI_RENDERED),
            WEBSOCKET_EVENT_UI_RENDERED,
            {
                AUTOMATION.KEY_SCREEN: WEBSOCKET_EVENT_SCREEN_HOME,
                AUTOMATION.KEY_SELECTED_PAGE_ID: FIXTURE_NOTES_PAGE_ID,
            },
        )
        assert_websocket_event(
            AUTOMATION.receive_event(observer, WEBSOCKET_EVENT_COMMAND_COMPLETED),
            WEBSOCKET_EVENT_COMMAND_COMPLETED,
            {
                AUTOMATION.KEY_REQUEST_ID: select_page_request_id,
                AUTOMATION.KEY_COMMAND_TYPE: AUTOMATION.TYPE_SELECT_PAGE,
                AUTOMATION.KEY_OUTCOME: WEBSOCKET_EVENT_OUTCOME_SUCCEEDED,
            },
        )


def test_dikciz_is_android_home_and_system_navigation_returns_to_it() -> None:
    role_holders = [
        line.strip()
        for line in device_command("cmd role get-role-holders android.app.role.HOME").splitlines()
        if line.strip()
    ]
    assert role_holders == [DIKCIZ_DEBUG_PACKAGE]

    resolved_home = device_command(
        "cmd package resolve-activity --brief "
        "-a android.intent.action.MAIN "
        "-c android.intent.category.HOME",
    )
    assert DIKCIZ_DEBUG_PACKAGE in resolved_home
    assert DIKCIZ_DEBUG_ACTIVITY in resolved_home

    device_command(f"am start -W -n {SETTINGS_ACTIVITY_COMPONENT}")
    wait_for_foreground_application("com.android.settings")
    device_command(f"input keyevent {KEYCODE_BACK}")
    wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)

    device_command(f"am start -W -n {SETTINGS_ACTIVITY_COMPONENT}")
    wait_for_foreground_application("com.android.settings")
    device_command(f"input keyevent {KEYCODE_HOME}")
    wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)

    device_command(f"input keyevent {KEYCODE_OVERVIEW}")
    try:
        overview_activity = wait_for_foreground_application(RECENTS_ACTIVITY_CLASS_NAME)
        assert RECENTS_ACTIVITY_CLASS_NAME in overview_activity
        assert any(
            node.get("resource-id", "").endswith(RECENTS_PANEL_RESOURCE_ID_SUFFIX)
            for node in physical_nodes()
        )
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / "system-navigation-overview.png",
        )
    finally:
        device_command(f"input keyevent {KEYCODE_HOME}")
        wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)


def test_dikciz_reclaims_its_home_role_after_removal(
    websocket_control: socket.socket,
) -> None:
    initial_status = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_STATUS)
    assert initial_status[AUTOMATION.KEY_ANDROID_ACCESS][AUTOMATION.KEY_HOME_ROLE] is True
    expected = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    try:
        device_command(DIKCIZ_HOME_ROLE_REMOVE_COMMAND)
        released_status = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_STATUS)
        assert released_status[AUTOMATION.KEY_ANDROID_ACCESS][AUTOMATION.KEY_HOME_ROLE] is False
    finally:
        run_device_operation("home-role-set", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
        run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
    restored_control = wait_for_restarted_websocket_control()
    try:
        restored_status = AUTOMATION.request(restored_control, AUTOMATION.TYPE_AUTOMATION_STATUS)
        assert restored_status[AUTOMATION.KEY_ANDROID_ACCESS][AUTOMATION.KEY_HOME_ROLE] is True
        restored = AUTOMATION.require_configuration(
            AUTOMATION.request(restored_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert restored == expected
        physical_widget_bounds_for_title(FIXTURE_WIDGET_TITLES["welcome"])
    finally:
        restored_control.close()


def test_direct_page_menu_sets_the_android_home_destination(
    websocket_control: socket.socket,
) -> None:
    direct_select_notes_page(websocket_control)
    wait_for_physical_text_bounds(FIXTURE_NOTES_CARD_TEXT)
    run_device_operation("screenshot")
    shutil.copyfile(
        ARTIFACT_DIRECTORY / "screenshot.png",
        ARTIFACT_DIRECTORY / HOME_DESTINATION_NOTES_ARTIFACT_NAME,
    )
    open_page_menu_on_selected_page()
    direct_tap(wait_for_physical_text_bounds(PAGE_MENU_SET_HOME_LABEL))
    configured = wait_for_configured_home_page(
        websocket_control,
        FIXTURE_NOTES_PAGE_ID,
    )
    assert configured["launcher"]["home"]["homePageId"] == FIXTURE_NOTES_PAGE_ID

    device_command(f"am start -W -n {SETTINGS_ACTIVITY_COMPONENT}")
    wait_for_foreground_application("com.android.settings")
    device_command(f"input keyevent {KEYCODE_HOME}")
    wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)
    home_snapshot = wait_for_selected_page(websocket_control, FIXTURE_NOTES_PAGE_ID)
    assert home_snapshot["selectedPageId"] == FIXTURE_NOTES_PAGE_ID
    wait_for_physical_text_bounds(FIXTURE_NOTES_CARD_TEXT)


def test_direct_page_menu_disables_set_home_for_the_current_home_page(
    websocket_control: socket.socket,
) -> None:
    direct_select_home_page(websocket_control)
    before = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert before["launcher"]["home"]["homePageId"] == FIXTURE_HOME_PAGE_ID
    open_page_menu_on_selected_page()
    set_home_node = physical_text_node(PAGE_MENU_SET_HOME_LABEL)
    assert set_home_node.get("enabled") == "false"
    direct_tap(physical_text_bounds(PAGE_MENU_SET_HOME_LABEL))
    after = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert after == before
    device_command(f"input keyevent {KEYCODE_BACK}")
    wait_for_physical_text_bounds(FIXTURE_WIDGET_TITLES["welcome"])


def test_direct_apps_picker_lists_provider_widgets_with_previews(
    websocket_control: socket.socket,
) -> None:
    direct_select_notes_page(websocket_control)
    open_picker_on_selected_page()
    assert_widget_picker_categories()
    direct_tap(wait_for_physical_text_bounds(WIDGET_PICKER_APPS_CATEGORY_LABEL))
    application_description = WIDGET_PICKER_APPLICATION_DESCRIPTION.format(
        DESKCLOCK_CATEGORY_LABEL,
    )
    application_icon_bounds = wait_for_physical_description_bounds(DESKCLOCK_CATEGORY_LABEL)
    application_action_bounds = wait_for_physical_description_bounds(application_description)
    assert application_icon_bounds["right"] <= application_action_bounds["left"]
    provider_entry_description = WIDGET_PICKER_PROVIDER_ENTRY_DESCRIPTION.format(
        DESKCLOCK_PROVIDER_LABEL,
        DESKCLOCK_CATEGORY_LABEL,
    )
    assert all(
        node.get("content-desc") != provider_entry_description
        for node in physical_nodes()
    )
    direct_tap(application_action_bounds)
    assert all(
        node.get("content-desc")
        != WIDGET_PICKER_ENTRY_DESCRIPTION.format(DESKCLOCK_CATEGORY_LABEL)
        for node in physical_nodes()
    )
    provider_bounds = wait_for_physical_description_bounds(provider_entry_description)
    preview_bounds = wait_for_physical_description_bounds(
        WIDGET_PICKER_PROVIDER_PREVIEW_DESCRIPTION.format(DESKCLOCK_PROVIDER_LABEL),
    )
    assert preview_bounds["left"] >= provider_bounds["left"]
    assert preview_bounds["right"] <= provider_bounds["right"]
    assert preview_bounds["bottom"] > preview_bounds["top"]
    run_device_operation("screenshot")
    shutil.copyfile(
        ARTIFACT_DIRECTORY / "screenshot.png",
        ARTIFACT_DIRECTORY / WIDGET_PICKER_PROVIDER_PREVIEW_ARTIFACT_NAME,
    )
    device_command(f"input keyevent {KEYCODE_BACK}")
    wait_for_physical_text_bounds(FIXTURE_NOTES_CARD_TEXT)


def test_private_vnc_keyboard_and_pointer_drive_android_navigation() -> None:
    with connected_private_vnc() as vnc:
        assert vnc.width > 0
        assert vnc.height > NAVIGATION_BAR_HEIGHT_PIXELS

        device_command(f"am start -W -n {SETTINGS_ACTIVITY_COMPONENT}")
        wait_for_foreground_application("com.android.settings")
        vnc.click_home()
        wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)

        device_command(f"am start -W -n {SETTINGS_ACTIVITY_COMPONENT}")
        wait_for_foreground_application("com.android.settings")
        vnc.key_press(RFB_XK_ESCAPE)
        wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)

        vnc.click_overview()
        try:
            overview_activity = wait_for_foreground_application(RECENTS_ACTIVITY_CLASS_NAME)
            assert RECENTS_ACTIVITY_CLASS_NAME in overview_activity
        finally:
            vnc.click_home()
            wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)


def test_home_role_and_config_survive_process_death_and_apk_replacement(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    expected = copy.deepcopy(original)
    configuration_widget(expected, "welcome")["title"] = LIFECYCLE_PERSISTED_TITLE
    persisted = AUTOMATION.require_configuration(
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=expected,
        ),
    )
    assert persisted == expected
    physical_widget_bounds_for_title(LIFECYCLE_PERSISTED_TITLE)

    run_device_operation("app-stop", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
    run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
    restarted_control = wait_for_restarted_websocket_control()
    try:
        restarted = AUTOMATION.require_configuration(
            AUTOMATION.request(restarted_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert restarted == expected
        physical_widget_bounds_for_title(LIFECYCLE_PERSISTED_TITLE)
    finally:
        restarted_control.close()

    run_device_operation("install", APK=DIKCIZ_DEBUG_APK_PATH)
    role_holders = device_command("cmd role get-role-holders android.app.role.HOME")
    assert role_holders.strip() == DIKCIZ_DEBUG_PACKAGE
    run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
    replaced_control = wait_for_restarted_websocket_control()
    try:
        replaced = AUTOMATION.require_configuration(
            AUTOMATION.request(replaced_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert replaced == expected
        physical_widget_bounds_for_title(LIFECYCLE_PERSISTED_TITLE)
    finally:
        replaced_control.close()


def test_dikciz_survives_rotation_dark_mode_and_font_scale(
    websocket_control: socket.socket,
) -> None:
    expected = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    original_accelerometer_rotation = device_system_setting(
        DEVICE_SETTING_ACCELEROMETER_ROTATION,
    )
    original_font_scale = device_system_setting(DEVICE_SETTING_FONT_SCALE)
    original_night_mode = device_night_mode()
    original_user_rotation = device_system_setting(DEVICE_SETTING_USER_ROTATION)
    try:
        set_device_system_setting(
            DEVICE_SETTING_ACCELEROMETER_ROTATION,
            SYSTEM_ROTATION_LOCKED,
        )
        set_device_system_setting(
            DEVICE_SETTING_USER_ROTATION,
            LANDSCAPE_USER_ROTATION,
        )
        wait_for_landscape_page_viewport()
        assert_system_configuration_keeps_saved_home(expected)

        set_device_night_mode("no")
        set_device_night_mode("yes")
        wait_for_physical_text_bounds(FIXTURE_WIDGET_TITLES["welcome"])
        assert_system_configuration_keeps_saved_home(expected)

        set_device_system_setting(DEVICE_SETTING_FONT_SCALE, FONT_SCALE_BASELINE)
        restore_system_home()
        baseline_title_bounds = physical_text_bounds(FIXTURE_WIDGET_TITLES["welcome"])
        set_device_system_setting(DEVICE_SETTING_FONT_SCALE, FONT_SCALE_ENLARGED)
        restore_system_home()
        enlarged_title_bounds = physical_text_bounds(FIXTURE_WIDGET_TITLES["welcome"])
        welcome_bounds = physical_widget_bounds()
        assert enlarged_title_bounds["bottom"] - enlarged_title_bounds["top"] > (
            baseline_title_bounds["bottom"] - baseline_title_bounds["top"]
        )
        assert welcome_bounds["left"] <= enlarged_title_bounds["left"]
        assert enlarged_title_bounds["right"] <= welcome_bounds["right"]
        assert welcome_bounds["top"] <= enlarged_title_bounds["top"]
        assert enlarged_title_bounds["bottom"] <= welcome_bounds["bottom"]
        assert_system_configuration_keeps_saved_home(expected)
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / SYSTEM_CONFIGURATION_ARTIFACT_NAME,
        )
        run_device_operation("uiautomator-dump")
    finally:
        restore_device_system_setting(DEVICE_SETTING_FONT_SCALE, original_font_scale)
        set_device_night_mode(original_night_mode)
        restore_device_system_setting(
            DEVICE_SETTING_USER_ROTATION,
            original_user_rotation,
        )
        restore_device_system_setting(
            DEVICE_SETTING_ACCELEROMETER_ROTATION,
            original_accelerometer_rotation,
        )
        restore_system_home()
