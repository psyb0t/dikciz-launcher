"""Allowed automation actions executed against the isolated Android emulator."""

from . import *


def automation_action_records() -> list[dict[str, Any]]:
    return [
        record
        for record in device_log_records()
        if record.get("event") == AUTOMATION_ACTION_COMPLETED_EVENT
        and record.get("script_id") == AUTOMATION_LIVE_SCRIPT_ID
    ]


def test_automation_allowed_actions_change_their_real_android_targets(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = automation_configuration(
        original,
        AUTOMATION_ACTION_SOURCE,
        AUTOMATION_ACTION_INITIAL_STATE,
        AUTOMATION_ACTION_CAPABILITIES,
        AUTOMATION_ACTIONS,
        [
            {
                "event": "battery",
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": AUTOMATION_ACTION_BATTERY_COALESCING_KEY,
            },
            {
                "event": "alarm",
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": AUTOMATION_ACTION_ALARM_COALESCING_KEY,
                "alarmIntervalMilliseconds": AUTOMATION_ALARM_MINIMUM_INTERVAL_MILLISECONDS,
            },
        ],
    )
    try:
        _, rejected = AUTOMATION.request_error_with_id(
            websocket_control,
            AUTOMATION.TYPE_AUTOMATION_DISPATCH,
            sourceWidgetAddress=AUTOMATION_ACTION_HTML_WIDGET_ADDRESS,
            actions=[{"type": "toggle", "widgetId": AUTOMATION_ACTION_HTML_WIDGET_ID}],
        )
        assert rejected["code"] == "validation_failed"

        device_command(
            f"pm grant {DIKCIZ_DEBUG_PACKAGE} android.permission.POST_NOTIFICATIONS",
        )
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)

        device_command(f"dumpsys battery set level {AUTOMATION_ACTION_BATTERY_LEVEL}")
        updated = wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_ACTION_EXPLICIT_BROADCAST_STATE_KEY,
            True,
        )
        script = next(
            item
            for item in updated["scripts"]
            if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
        )
        assert script["state"][AUTOMATION_ACTION_BATTERY_STATE_KEY] is True
        assert updated["launcher"]["home"]["selectedPageId"] == FIXTURE_NOTES_PAGE_ID
        focus_widget = next(
            widget
            for widget in configuration_page(updated, FIXTURE_HOME_PAGE_ID)["widgets"]
            if widget["id"] == AUTOMATION_ACTION_HTML_WIDGET_ID
        )
        focus_state = focus_widget["state"]
        assert isinstance(focus_state, dict)
        assert focus_state["checked"] is True
        wait_for_foreground_application(COMMAND_SETTINGS_PACKAGE_QUERY)

        notification_dump = device_command("dumpsys notification --noredact")
        assert AUTOMATION_ACTION_NOTIFICATION_TITLE in notification_dump
        assert AUTOMATION_ACTION_NOTIFICATION_TEXT in notification_dump
        assert {
            record.get("action_type")
            for record in automation_action_records()
        } >= set(AUTOMATION_ACTIONS)
    finally:
        restoration_connection: socket.socket | None = None
        try:
            device_command("dumpsys battery reset")
            restoration_connection = wait_for_restarted_websocket_control()
            AUTOMATION.request(
                restoration_connection,
                AUTOMATION.TYPE_CONFIG_REPLACE,
                config=original,
            )
        finally:
            if restoration_connection is not None:
                restoration_connection.close()
            device_command(
                f"pm revoke {DIKCIZ_DEBUG_PACKAGE} android.permission.POST_NOTIFICATIONS",
            )
            restore_system_home()
