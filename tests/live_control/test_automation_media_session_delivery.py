"""Rate-limit and duplicate-delivery coverage for media-session automation."""

from . import *
from .media_session import *


def test_automation_media_session_rate_limit_rejects_a_duplicate_fixture_session(
    websocket_control: socket.socket,
    installed_configurable_provider_fixture: None,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    package_name = configurable_provider_fixture_package()
    configuration = media_session_configuration(
        original,
        MEDIA_SESSION_RATE_LIMIT_SOURCE,
        AUTOMATION_MEDIA_SESSION_METADATA_CAPABILITIES,
        AUTOMATION_PATCH_STATE_ACTIONS,
        [],
        minimum_interval_milliseconds=AUTOMATION_MEDIA_SESSION_COMMAND_INTERVAL_MILLISECONDS,
        coalescing_key=MEDIA_SESSION_RATE_LIMIT_COALESCING_KEY,
        initial_state=MEDIA_SESSION_RATE_LIMIT_INITIAL_STATE,
    )
    try:
        device_command(f"pm grant {package_name} android.permission.POST_NOTIFICATIONS")
        device_command(f"cmd notification allow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
        wait_for_notification_listener_access(websocket_control)
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configuration,
        )
        start_fixture_media_session(package_name)
        first_timestamp = wait_for_media_session_delivery_timestamp(websocket_control)
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        record_count_before_duplicate = len(device_log_records())

        start_fixture_media_session(package_name)
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        current = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert media_session_delivery_timestamp(current) == first_timestamp
        assert any(
            record.get("event") == MEDIA_SESSION_DELIVERY_REJECTED_EVENT and
            record.get("reason") == MEDIA_SESSION_RATE_LIMITED_REASON
            for record in device_log_records()[record_count_before_duplicate:]
        )
    finally:
        device_command(f"am force-stop {package_name}")
        device_command(f"cmd notification disallow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
