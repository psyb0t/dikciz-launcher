"""Allowlisted live notification-action control on the isolated emulator."""

from . import *
from .media_session import *
from .notification_action import *


def test_automation_notification_action_is_allowlisted_single_use_and_listener_gated(
    websocket_control: socket.socket,
    installed_configurable_provider_fixture: None,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    package_name = configurable_provider_fixture_package()
    metadata_configuration = metadata_only_notification_configuration(original, package_name)
    collect_configuration = notification_action_configuration(
        original,
        NOTIFICATION_ACTION_SOURCE,
        NOTIFICATION_ACTION_INITIAL_STATE,
        package_name,
        include_battery_subscription=True,
    )
    direct_configuration = notification_action_configuration(
        original,
        NOTIFICATION_ACTION_DIRECT_SOURCE,
        NOTIFICATION_ACTION_INITIAL_STATE,
        package_name,
        include_battery_subscription=False,
    )
    try:
        device_command(f"pm grant {package_name} android.permission.POST_NOTIFICATIONS")
        device_command(f"cmd notification allow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
        status = wait_for_notification_listener_access(websocket_control)
        assert NOTIFICATION_ACTION_ACTION in status["actions"]
        assert_mcp_notification_action_status(status)

        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=metadata_configuration,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        start_fixture_notification_action(package_name)
        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            "hasToken",
            False,
        )
        assert fixture_notification_action_count(package_name) == 0

        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=collect_configuration,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        start_fixture_notification_action(package_name)
        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            "hasActionTokens",
            True,
        )
        wait_for_notification_action_token(websocket_control)
        observed_action_count = fixture_notification_action_count(package_name)
        assert observed_action_count == 0, notification_action_records()

        device_command(f"cmd notification disallow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
        wait_for_notification_listener_absence(websocket_control)
        captured_configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        denied_configuration = notification_action_configuration_with_battery_trigger(
            captured_configuration,
            NOTIFICATION_ACTION_BATTERY_LEVEL,
        )
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=denied_configuration,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        denied_record_count = len(device_log_records())
        device_command(f"dumpsys battery set level {NOTIFICATION_ACTION_BATTERY_PRIMER_LEVEL}")
        device_command(f"dumpsys battery set level {NOTIFICATION_ACTION_BATTERY_LEVEL}")
        wait_for_notification_action_outcome(
            denied_record_count,
            NOTIFICATION_ACTION_REJECTED_EVENT,
            NOTIFICATION_ACTION_BATTERY_EVENT,
            NOTIFICATION_ACTION_OUTCOME_PERMISSION_DENIED,
        )
        assert fixture_notification_action_count(package_name) == 0
        device_command("dumpsys battery reset")

        device_command(f"cmd notification allow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
        wait_for_notification_listener_access(websocket_control)
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=direct_configuration,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        direct_record_count = len(device_log_records())
        start_fixture_notification_action(package_name)
        wait_for_fixture_notification_action_count(package_name, expected_count=1)
        wait_for_notification_action_outcome(
            direct_record_count,
            NOTIFICATION_ACTION_COMPLETED_EVENT,
            NOTIFICATION_ACTION_EVENT,
            NOTIFICATION_ACTION_OUTCOME_EXECUTED,
        )
        wait_for_notification_action_outcome(
            direct_record_count,
            NOTIFICATION_ACTION_REJECTED_EVENT,
            NOTIFICATION_ACTION_EVENT,
            NOTIFICATION_ACTION_OUTCOME_TARGET_UNAVAILABLE,
        )
    finally:
        try:
            device_command("dumpsys battery reset")
        finally:
            try:
                device_command(f"cmd notification disallow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
            finally:
                AUTOMATION.request(
                    websocket_control,
                    AUTOMATION.TYPE_CONFIG_REPLACE,
                    config=original,
                )
                restore_system_home()
