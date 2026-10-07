"""Package-filter and listener-reconnect coverage for media-session automation."""

from . import *
from .media_session import *


def test_automation_media_session_filter_reconfigures_between_two_real_packages(
    websocket_control: socket.socket,
    installed_configurable_provider_fixture: None,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    primary_package = configurable_provider_fixture_package()
    secondary_package = install_secondary_media_session_fixture()
    primary_filter = media_session_configuration(
        original,
        MEDIA_SESSION_FILTER_SOURCE,
        AUTOMATION_MEDIA_SESSION_METADATA_CAPABILITIES,
        AUTOMATION_MEDIA_SESSION_ACTIONS,
        [],
        subscription_packages=[primary_package],
        initial_state=MEDIA_SESSION_FILTER_INITIAL_STATE,
    )
    secondary_filter = media_session_configuration(
        original,
        MEDIA_SESSION_FILTER_SOURCE,
        AUTOMATION_MEDIA_SESSION_METADATA_CAPABILITIES,
        AUTOMATION_MEDIA_SESSION_ACTIONS,
        [],
        subscription_packages=[secondary_package],
        initial_state=MEDIA_SESSION_FILTER_INITIAL_STATE,
    )
    try:
        device_command(f"pm grant {primary_package} android.permission.POST_NOTIFICATIONS")
        device_command(f"pm grant {secondary_package} android.permission.POST_NOTIFICATIONS")
        device_command(f"cmd notification allow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
        wait_for_notification_listener_access(websocket_control)
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=primary_filter,
        )
        start_fixture_media_session(secondary_package)
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        unchanged = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert media_session_script_state(unchanged) == MEDIA_SESSION_FILTER_INITIAL_STATE
        start_fixture_media_session(primary_package)
        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            "packageName",
            primary_package,
        )

        stop_fixture_media_session(primary_package)
        stop_fixture_media_session(secondary_package)
        wait_for_media_session_status(websocket_control, expected_active_count=0)
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=secondary_filter,
        )
        start_fixture_media_session(primary_package)
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        unchanged = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert media_session_script_state(unchanged) == MEDIA_SESSION_FILTER_INITIAL_STATE
        start_fixture_media_session(secondary_package)
        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            "packageName",
            secondary_package,
        )
    finally:
        stop_fixture_media_session(primary_package)
        stop_fixture_media_session(secondary_package)
        device_command(f"cmd notification disallow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
        uninstall_secondary_media_session_fixture(secondary_package)
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )


def test_automation_media_session_listener_reconnect_rebuilds_the_monitor(
    websocket_control: socket.socket,
    installed_configurable_provider_fixture: None,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    package_name = configurable_provider_fixture_package()
    configuration = media_session_configuration(
        original,
        MEDIA_SESSION_FILTER_SOURCE,
        AUTOMATION_MEDIA_SESSION_METADATA_CAPABILITIES,
        AUTOMATION_PATCH_STATE_ACTIONS,
        [],
        initial_state=MEDIA_SESSION_FILTER_INITIAL_STATE,
    )
    try:
        device_command(f"pm grant {package_name} android.permission.POST_NOTIFICATIONS")
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configuration,
        )
        device_command(f"cmd notification disallow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
        wait_for_notification_listener_absence(websocket_control)
        start_fixture_media_session(package_name)
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        unchanged = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert media_session_script_state(unchanged) == MEDIA_SESSION_FILTER_INITIAL_STATE
        device_command(f"cmd notification allow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
        wait_for_notification_listener_access(websocket_control)
        start_fixture_media_session(package_name)
        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            "packageName",
            package_name,
        )
    finally:
        stop_fixture_media_session(package_name)
        device_command(f"cmd notification disallow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
