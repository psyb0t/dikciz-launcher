"""Native Dikciz page and Android shortcut behavior."""

from . import *


@pytest.mark.parametrize(
    ("axis", "expected_position"),
    PAGE_CREATE_DELETE_CASES,
)
def test_direct_page_create_and_delete(
    websocket_control: socket.socket,
    axis: str,
    expected_position: dict[str, int],
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    try:
        original_page_ids = [page["id"] for page in original["launcher"]["home"]["pages"]]
        original_page_count = len(original_page_ids)
        indicator_bounds = physical_page_indicator_bounds(axis)
        indicator_edge = (
            (indicator_bounds["right"] - HANDLE_INSET_PIXELS, center(indicator_bounds)[1])
            if axis == "horizontal"
            else (center(indicator_bounds)[0], indicator_bounds["bottom"] - HANDLE_INSET_PIXELS)
        )
        direct_long_press_at(*indicator_edge)
        created = wait_for_page_count(websocket_control, original_page_count + 1)
        created_page_id = selected_page(created)
        assert created_page_id not in original_page_ids
        created_page = configuration_page(created, created_page_id)
        assert created_page["position"] == expected_position
        assert created_page["widgets"] == []

        # The page is already saved, so leaving it and coming back finds it unchanged.
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_SELECT_PAGE,
            pageId=original_page_ids[0],
        )
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_SELECT_PAGE,
            pageId=created_page_id,
        )
        wait_for_selected_page(websocket_control, created_page_id)
        navigated = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert configuration_page(navigated, created_page_id) == created_page

        open_page_menu_on_selected_page()
        direct_tap(wait_for_physical_text_bounds(PAGE_MENU_DELETE_LABEL))
        after_delete = wait_for_page_count(websocket_control, original_page_count)
        assert after_delete == original
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )


def test_direct_native_app_widget_edit_title_round_trip(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = copy.deepcopy(original)
    configured["launcher"]["home"]["selectedPageId"] = FIXTURE_HOME_PAGE_ID
    configuration_page(configured, FIXTURE_HOME_PAGE_ID)["widgets"].append(
        {
            "id": NATIVE_APP_EDIT_WIDGET_ID,
            "type": "app",
            "title": SETTINGS_APP_LABEL,
            "component": SETTINGS_ACTIVITY_COMPONENT,
            "enabled": True,
            "cell": dict(NATIVE_APP_EDIT_CELL),
        },
    )
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=configured,
    )
    wait_for_snapshot_node(websocket_control, f"widget:{NATIVE_APP_EDIT_WIDGET_ID}")
    physical_widget_bounds_for_title(SETTINGS_APP_LABEL)
    direct_open_widget_editor(SETTINGS_APP_LABEL)
    direct_enter_text(
        wait_for_physical_description_bounds(NATIVE_APP_WIDGET_TITLE_INPUT_DESCRIPTION),
        NATIVE_APP_EDITED_TITLE,
        description=NATIVE_APP_WIDGET_TITLE_INPUT_DESCRIPTION,
    )
    direct_tap(physical_button_bounds("Save"))
    edited = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    edited_widget = configuration_widget(edited, NATIVE_APP_EDIT_WIDGET_ID)
    assert edited_widget["title"] == NATIVE_APP_EDITED_TITLE
    assert edited_widget["component"] == SETTINGS_ACTIVITY_COMPONENT
    assert edited_widget["displayStyle"] == "iconLabel"
    physical_widget_bounds_for_title(NATIVE_APP_EDITED_TITLE)
    direct_open_widget_editor(NATIVE_APP_EDITED_TITLE)
    direct_tap(wait_for_physical_button_bounds("Choose target app"))
    direct_enter_text(
        wait_for_physical_description_bounds("Filter widgets and apps"),
        NATIVE_APP_RETARGET_LABEL,
    )
    direct_tap(
        wait_for_physical_description_bounds(
            f"Use {NATIVE_APP_RETARGET_LABEL}",
        ),
    )
    retargeted = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    retargeted_widget = configuration_widget(retargeted, NATIVE_APP_EDIT_WIDGET_ID)
    assert retargeted_widget["id"] == NATIVE_APP_EDIT_WIDGET_ID
    assert retargeted_widget["title"] == NATIVE_APP_EDITED_TITLE
    assert retargeted_widget["displayStyle"] == "iconLabel"
    retargeted_component = retargeted_widget["component"]
    assert isinstance(retargeted_component, str)
    retargeted_package, _, retargeted_activity = retargeted_component.partition("/")
    assert retargeted_package
    assert retargeted_activity == "com.android.deskclock.DeskClock"
    direct_tap(
        wait_for_physical_description_bounds(
            app_shortcut_content_description(
                NATIVE_APP_EDITED_TITLE,
                APP_SHORTCUT_DEFAULT_STYLE_LABEL,
            ),
        ),
    )
    restored_control: socket.socket | None = None
    try:
        wait_for_foreground_application(retargeted_package)
    finally:
        run_device_operation(
            "app-start",
            PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE,
            ACTIVITY=DIKCIZ_DEBUG_ACTIVITY,
        )
        restored_control = wait_for_restarted_websocket_control()
    assert restored_control is not None
    try:
        physical_widget_bounds_for_title(NATIVE_APP_EDITED_TITLE)
        direct_page_round_trip(restored_control)
        restored = AUTOMATION.require_configuration(
            AUTOMATION.request(restored_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert configuration_widget(restored, NATIVE_APP_EDIT_WIDGET_ID) == retargeted_widget
        physical_widget_bounds_for_title(NATIVE_APP_EDITED_TITLE)
    finally:
        restored_control.close()


@pytest.mark.parametrize(
    ("_case_name", "display_style", "style_label", "artifact_name"),
    NATIVE_APP_SHORTCUT_DISPLAY_STYLE_CASES,
)
def test_direct_app_shortcut_display_style_renders_and_persists(
    websocket_control: socket.socket,
    _case_name: str,
    display_style: str,
    style_label: str,
    artifact_name: str,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = copy.deepcopy(original)
    configured["launcher"]["home"]["selectedPageId"] = FIXTURE_HOME_PAGE_ID
    configuration_page(configured, FIXTURE_HOME_PAGE_ID)["widgets"].append(
        {
            "id": NATIVE_APP_EDIT_WIDGET_ID,
            "type": "app",
            "title": SETTINGS_APP_LABEL,
            "component": SETTINGS_ACTIVITY_COMPONENT,
            "displayStyle": "iconLabel",
            "enabled": True,
            "cell": dict(NATIVE_APP_EDIT_CELL),
        },
    )
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=configured,
    )
    wait_for_snapshot_node(websocket_control, f"widget:{NATIVE_APP_EDIT_WIDGET_ID}")
    direct_open_widget_editor(SETTINGS_APP_LABEL)
    direct_tap(
        wait_for_physical_description_bounds(
            f"App shortcut style: {style_label}",
        ),
    )
    direct_tap(physical_button_bounds("Save"))
    saved = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    saved_widget = configuration_widget(saved, NATIVE_APP_EDIT_WIDGET_ID)
    assert saved_widget["displayStyle"] == display_style
    assert saved_widget["component"] == SETTINGS_ACTIVITY_COMPONENT
    wait_for_physical_description_bounds(
        app_shortcut_content_description(SETTINGS_APP_LABEL, style_label),
    )
    run_device_operation("screenshot")
    shutil.copyfile(
        ARTIFACT_DIRECTORY / "screenshot.png",
        ARTIFACT_DIRECTORY / artifact_name,
    )
    direct_page_round_trip(websocket_control)
    restored = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert configuration_widget(restored, NATIVE_APP_EDIT_WIDGET_ID) == saved_widget
    wait_for_physical_description_bounds(
        app_shortcut_content_description(SETTINGS_APP_LABEL, style_label),
    )
