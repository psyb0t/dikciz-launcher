"""The shared phone must end every targeted run on the bundled starter home."""

from . import *


def test_targeted_run_cleanup_restores_the_bundled_starter_home(
    websocket_control: socket.socket,
) -> None:
    # The autouse fixture installs the test fixture home before this body runs.
    fixture_configuration = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    fixture_page = configuration_page(fixture_configuration, FIXTURE_HOME_PAGE_ID)
    fixture_widget_ids = [widget["id"] for widget in fixture_page["widgets"]]
    assert FIXTURE_WIDGET_IDS[0] in fixture_widget_ids

    restored = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_RESET),
    )
    starter_page = configuration_page(restored, STARTER_HOME_PAGE_ID)
    starter_widget_ids = [widget["id"] for widget in starter_page["widgets"]]
    assert starter_widget_ids == [STARTER_HOME_WIDGET_ID]
    for fixture_widget_id in FIXTURE_WIDGET_IDS:
        assert fixture_widget_id not in starter_widget_ids

    restore_system_home()
    wait_for_physical_description_bounds(STARTER_HOME_WIDGET_TITLE)
    visible = physical_visible_texts()
    for fixture_title in FIXTURE_WIDGET_TITLES.values():
        assert fixture_title not in visible


def test_bundled_starter_names_android_settings_and_offers_setup_and_retry(
    websocket_control: socket.socket,
) -> None:
    restored = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_RESET),
    )
    notes_page = configuration_page(restored, STARTER_NOTES_PAGE_ID)
    settings_shortcut = next(
        widget for widget in notes_page["widgets"] if widget["type"] == APP_WIDGET_TYPE
    )
    # The prominent starter shortcut must not read as the launcher's own settings.
    assert settings_shortcut["title"] == STARTER_ANDROID_SETTINGS_TITLE
    assert settings_shortcut["component"].startswith(ANDROID_SETTINGS_PACKAGE)

    restore_system_home()
    wait_for_physical_description_bounds(STARTER_HOME_WIDGET_TITLE)
    notify_button = wait_for_physical_button_bounds(STARTER_NOTIFY_LABEL)
    direct_tap(notify_button)
    setup_button = wait_for_physical_button_bounds(STARTER_RECOVERY_SETUP_LABEL)
    wait_for_physical_button_bounds(STARTER_RECOVERY_RETRY_LABEL)

    direct_tap(setup_button)
    wait_for_physical_text_bounds(DIKCIZ_AUTOMATION_TITLE_TEXT)
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=AUTOMATION_SETTINGS_CLOSE_SEMANTIC_ID,
    )
    wait_for_physical_description_bounds(STARTER_HOME_WIDGET_TITLE)
    direct_tap(wait_for_physical_button_bounds(STARTER_RECOVERY_RETRY_LABEL))
    wait_for_physical_button_bounds(STARTER_RECOVERY_SETUP_LABEL)
