"""Fixed transport command coverage for media-session automation."""

from . import *
from .media_session import *


def test_automation_media_session_fixed_commands_reach_visible_fixture_state(
    websocket_control: socket.socket,
    installed_configurable_provider_fixture: None,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    package_name = configurable_provider_fixture_package()
    try:
        device_command(f"pm grant {package_name} android.permission.POST_NOTIFICATIONS")
        device_command(f"cmd notification allow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
        wait_for_notification_listener_access(websocket_control)
        for command, initial_playing, position_milliseconds, expected_status in MEDIA_SESSION_COMMAND_CASES:
            stop_fixture_media_session(package_name)
            wait_for_media_session_status(websocket_control, expected_active_count=0)
            configuration = media_session_configuration(
                original,
                media_session_control_source(package_name, command, position_milliseconds),
                AUTOMATION_MEDIA_SESSION_METADATA_CAPABILITIES,
                AUTOMATION_MEDIA_SESSION_ACTIONS,
                [package_name],
                minimum_interval_milliseconds=AUTOMATION_MEDIA_SESSION_COMMAND_INTERVAL_MILLISECONDS,
                coalescing_key=f"{AUTOMATION_MEDIA_SESSION_COALESCING_KEY}-{command}",
            )
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_CONFIG_REPLACE,
                config=configuration,
            )
            start_fixture_media_session(package_name, initial_playing)
            wait_for_physical_text_bounds(expected_status)
    finally:
        stop_fixture_media_session(package_name)
        device_command(f"cmd notification disallow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
