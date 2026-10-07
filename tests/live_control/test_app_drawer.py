"""Native app drawer entry, search, launch, and per-app actions."""

from . import *


def settings_row_semantic_id() -> str:
    return APP_DRAWER_APP_PREFIX + ANDROID_SETTINGS_COMPONENT


def open_drawer_through_control() -> None:
    direct_tap(wait_for_physical_description_bounds(LAUNCHER_CONTROL_OPEN_DESCRIPTION))
    direct_tap(wait_for_physical_button_bounds(LAUNCHER_CONTROL_APP_DRAWER_LABEL))
    wait_for_physical_text_bounds(APP_DRAWER_TITLE)


def open_drawer_through_home_gesture() -> None:
    rail = physical_page_indicator_bounds("horizontal")
    gesture_x, gesture_y = center(rail)
    direct_swipe(
        (gesture_x, gesture_y),
        (gesture_x, gesture_y - HOME_GESTURE_TRAVEL_PIXELS),
        HOME_GESTURE_DURATION_MILLISECONDS,
    )
    wait_for_physical_text_bounds(APP_DRAWER_TITLE)


def filter_drawer_to_settings() -> None:
    direct_enter_text(
        wait_for_physical_description_bounds(APP_DRAWER_SEARCH_DESCRIPTION),
        ANDROID_SETTINGS_LABEL_QUERY,
        description=APP_DRAWER_SEARCH_DESCRIPTION,
    )


def close_drawer(connection: socket.socket) -> None:
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_TAP,
        semanticId=APP_DRAWER_CLOSE_SEMANTIC_ID,
    )
    wait_for_snapshot_node(connection, APP_DRAWER_SEARCH_SEMANTIC_ID, should_exist=False)


def test_app_drawer_opens_from_the_visible_control_and_the_upward_home_gesture(
    websocket_control: socket.socket,
) -> None:
    device_command(HOME_CLEAR_TASK_COMMAND)
    wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)
    reentered_control = wait_for_restarted_websocket_control()
    try:
        # Android can create a warm Activity instance after clearing the launcher task.
        # Home must return to the rendered launcher instead of replaying its cold-start brand.
        wait_for_snapshot_node(
            reentered_control,
            STARTUP_DISMISS_SEMANTIC_ID,
            should_exist=False,
        )

        open_drawer_through_control()
        filter_drawer_to_settings()
        control_snapshot = wait_for_snapshot_node(reentered_control, settings_row_semantic_id())
        assert bounds_for(control_snapshot, APP_DRAWER_SEARCH_SEMANTIC_ID)["bottom"] > 0
        assert bounds_for(control_snapshot, settings_row_semantic_id())["bottom"] > 0
        close_drawer(reentered_control)

        open_drawer_through_home_gesture()
        filter_drawer_to_settings()
        gesture_snapshot = wait_for_snapshot_node(reentered_control, settings_row_semantic_id())
        assert bounds_for(gesture_snapshot, settings_row_semantic_id())["bottom"] > 0
        close_drawer(reentered_control)
    finally:
        reentered_control.close()


def test_app_drawer_search_matches_label_and_package_and_shows_an_empty_state(
    websocket_control: socket.socket,
) -> None:
    open_drawer_through_control()
    # The drawer list recycles, so only the rows it has bound carry a semantic node.
    # Settings sorts below the first screenful, which is exactly what search is for.
    wait_for_snapshot_node(websocket_control, APP_DRAWER_SEARCH_SEMANTIC_ID)

    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_SET_TEXT,
        semanticId=APP_DRAWER_SEARCH_SEMANTIC_ID,
        text=ANDROID_SETTINGS_LABEL_QUERY,
    )
    wait_for_snapshot_node(websocket_control, settings_row_semantic_id())

    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_SET_TEXT,
        semanticId=APP_DRAWER_SEARCH_SEMANTIC_ID,
        text=ANDROID_SETTINGS_PACKAGE,
    )
    wait_for_snapshot_node(websocket_control, settings_row_semantic_id())

    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_SET_TEXT,
        semanticId=APP_DRAWER_SEARCH_SEMANTIC_ID,
        text=APP_DRAWER_UNMATCHABLE_QUERY,
    )
    wait_for_snapshot_node(websocket_control, settings_row_semantic_id(), should_exist=False)
    empty_snapshot = wait_for_snapshot_node(websocket_control, APP_DRAWER_EMPTY_SEMANTIC_ID)
    assert bounds_for(empty_snapshot, APP_DRAWER_EMPTY_SEMANTIC_ID)["bottom"] > 0
    close_drawer(websocket_control)


def test_app_drawer_row_tap_launches_the_selected_android_app(
    websocket_control: socket.socket,
) -> None:
    open_drawer_through_control()
    # The list recycles, so Settings carries no semantic node until search
    # brings its row on screen.
    filter_drawer_to_settings()
    wait_for_snapshot_node(websocket_control, settings_row_semantic_id())
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=settings_row_semantic_id(),
    )
    wait_for_foreground_application(ANDROID_SETTINGS_PACKAGE)
    restore_system_home()
    # Launching an app leaves the drawer on the home screen behind it, and the
    # next test would open its page underneath a drawer filtered to one row.
    close_drawer(websocket_control)


def test_app_catalogue_is_ordered_bounded_and_carries_stable_ids_and_labels(
    websocket_control: socket.socket,
) -> None:
    catalogue = AUTOMATION.request(websocket_control, APP_CATALOGUE_COMMAND)
    entries = catalogue["apps"]
    assert isinstance(entries, list)
    assert entries == sorted(entries, key=lambda entry: entry["label"].lower())
    settings_entry = next(
        entry for entry in entries if entry["packageName"] == ANDROID_SETTINGS_PACKAGE
    )
    assert settings_entry["semanticId"] == settings_row_semantic_id()
    assert settings_entry["component"] == ANDROID_SETTINGS_COMPONENT
    action_ids = [action["id"] for action in catalogue["actions"]]
    assert action_ids == [
        APP_ACTION_TYPE_LAUNCH,
        APP_ACTION_TYPE_ADD_SHORTCUT,
        APP_ACTION_TYPE_APP_INFO,
        APP_ACTION_TYPE_UNINSTALL,
        APP_ACTION_TYPE_FORCE_STOP,
    ]
    force_stop = next(
        action for action in catalogue["actions"] if action["id"] == APP_ACTION_TYPE_FORCE_STOP
    )
    assert force_stop["rootRequired"] is True
    assert force_stop["destructive"] is True
    assert force_stop["label"] == APP_ACTION_FORCE_STOP_LABEL

    filtered = AUTOMATION.request(
        websocket_control,
        APP_CATALOGUE_COMMAND,
        query=APP_DRAWER_UNMATCHABLE_QUERY,
    )
    assert filtered["apps"] == []
    assert filtered["entryCount"] == 0

    _, rejected = AUTOMATION.request_error_with_id(
        websocket_control,
        APP_ACTION_COMMAND,
        action=APP_ACTION_TYPE_LAUNCH,
        component=APP_ACTION_INVALID_COMPONENT,
    )
    assert rejected[AUTOMATION.KEY_CODE] == VALIDATION_FAILED_ERROR_CODE


def open_app_actions_sheet(overflow_description: str) -> None:
    """Tap a drawer row's overflow button and wait for its action sheet.

    The filtered list keeps rebinding for a moment, so bounds read too early
    can point at a row that has already moved and the tap lands on nothing.
    A missed tap cannot be waited out, so the press is retried.
    """
    for attempt in range(APP_ACTIONS_OPEN_ATTEMPTS):
        direct_tap(wait_for_physical_description_bounds(overflow_description))
        try:
            wait_for_physical_button_bounds(APP_ACTION_LAUNCH_LABEL)
            return
        except AssertionError:
            if attempt == APP_ACTIONS_OPEN_ATTEMPTS - 1:
                raise


def test_app_action_add_shortcut_persists_a_native_app_widget(
    websocket_control: socket.socket,
) -> None:
    AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_RESET),
    )
    restore_system_home()
    wait_for_physical_description_bounds(STARTER_HOME_WIDGET_TITLE)

    # Every bundled page fills its whole grid, so a shortcut has nowhere to
    # land on one. Adding always targets the selected page, so the test selects
    # an empty page of its own first.
    document = current_configuration(websocket_control)
    append_empty_page(document, SHORTCUT_PAGE_ID, SHORTCUT_PAGE_TITLE, SHORTCUT_PAGE_COLUMN, 0)
    document["launcher"]["home"]["selectedPageId"] = SHORTCUT_PAGE_ID
    AUTOMATION.require_configuration(
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=document,
        ),
    )
    wait_for_selected_page(websocket_control, SHORTCUT_PAGE_ID)

    open_drawer_through_control()
    filter_drawer_to_settings()
    open_app_actions_sheet(APP_DRAWER_SETTINGS_ACTIONS_DESCRIPTION)
    direct_tap(wait_for_physical_button_bounds(APP_ACTION_ADD_SHORTCUT_LABEL))
    wait_for_snapshot_node(websocket_control, APP_DRAWER_SEARCH_SEMANTIC_ID, should_exist=False)

    saved = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    page = configuration_page(saved, selected_page(saved))
    shortcuts = [
        widget
        for widget in page["widgets"]
        if widget["type"] == APP_WIDGET_TYPE
        and widget["component"].startswith(ANDROID_SETTINGS_PACKAGE)
    ]
    assert len(shortcuts) == 1
    shortcut = shortcuts[0]
    assert shortcut["cell"]["columnSpan"] == APP_SHORTCUT_DEFAULT_COLUMN_SPAN
    assert shortcut["cell"]["rowSpan"] == APP_SHORTCUT_DEFAULT_ROW_SPAN
    initial_cell = dict(shortcut["cell"])
    shortcut_bounds = wait_for_physical_description_bounds(
        app_shortcut_content_description(
            ANDROID_SETTINGS_LABEL_QUERY,
            APP_SHORTCUT_DEFAULT_STYLE_LABEL,
        ),
    )
    assert_rendered_widget_has_area(shortcut_bounds)
    move_handle = wait_for_physical_description_bounds(
        WIDGET_MOVE_HANDLE_DESCRIPTION.format(ANDROID_SETTINGS_LABEL_QUERY),
    )
    move_start = center(move_handle)
    direct_swipe(
        move_start,
        (move_start[0] + WIDGET_MOVE_TRAVEL_PIXELS, move_start[1]),
        WIDGET_MOVE_DURATION_MILLISECONDS,
    )

    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        moved = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        moved_shortcut = next(
            widget
            for widget in configuration_page(moved, selected_page(moved))["widgets"]
            if widget["id"] == shortcut["id"]
        )
        if moved_shortcut["cell"] != initial_cell:
            break
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    else:
        raise AssertionError("new app shortcut did not persist its drag cell")


def test_app_action_app_info_opens_the_real_android_package_details_screen(
    websocket_control: socket.socket,
) -> None:
    launched = AUTOMATION.request(
        websocket_control,
        APP_ACTION_COMMAND,
        action=APP_ACTION_TYPE_LAUNCH,
        component=ANDROID_SETTINGS_COMPONENT,
    )
    assert launched["outcome"] == APP_ACTION_OUTCOME_LAUNCHED
    launcher_activity = wait_for_foreground_application(ANDROID_SETTINGS_PACKAGE)
    restore_system_home()

    details = AUTOMATION.request(
        websocket_control,
        APP_ACTION_COMMAND,
        action=APP_ACTION_TYPE_APP_INFO,
        component=ANDROID_SETTINGS_COMPONENT,
    )
    assert details["outcome"] == APP_ACTION_OUTCOME_APP_INFO_OPENED
    details_activity = wait_for_foreground_application(ANDROID_SETTINGS_PACKAGE)
    # App info must reach the package-details screen, not the Settings entry activity.
    assert details_activity != launcher_activity
    restore_system_home()


def test_app_action_force_stop_reports_root_unavailable_and_offers_app_info(
    websocket_control: socket.socket,
) -> None:
    result = AUTOMATION.request(
        websocket_control,
        APP_ACTION_COMMAND,
        action=APP_ACTION_TYPE_FORCE_STOP,
        component=ANDROID_SETTINGS_COMPONENT,
    )
    assert result["outcome"] == APP_ACTION_OUTCOME_ROOT_UNAVAILABLE
    assert "exitCode" not in result

    open_drawer_through_control()
    # The list recycles, so Settings carries no semantic node until search
    # brings its row on screen.
    filter_drawer_to_settings()
    wait_for_snapshot_node(websocket_control, settings_row_semantic_id())
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=APP_DRAWER_APP_ACTIONS_PREFIX + ANDROID_SETTINGS_COMPONENT,
    )
    force_stop_semantic_id = APP_ACTION_SEMANTIC_PREFIX + APP_ACTION_FORCE_STOP_SEMANTIC_SUFFIX
    wait_for_snapshot_node(websocket_control, force_stop_semantic_id)
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=force_stop_semantic_id,
    )
    # Without root the launcher offers the real App info route instead of confirming a
    # stop it cannot perform.
    wait_for_snapshot_node(websocket_control, APP_ACTION_ROOT_APP_INFO_SEMANTIC_ID)
    wait_for_snapshot_node(
        websocket_control,
        APP_ACTION_CONFIRM_SEMANTIC_ID,
        should_exist=False,
    )
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=APP_ACTION_ROOT_APP_INFO_SEMANTIC_ID,
    )
    wait_for_foreground_application(ANDROID_SETTINGS_PACKAGE)
    restore_system_home()
    # Leaving App info leaves the drawer on the home screen behind it.
    close_drawer(websocket_control)
