"""Typed Android media-session automation through notification-listener consent."""

from . import *
from .media_session import *


AUTOMATION_SETTINGS_SEMANTIC_ID = "settings:automation"
AUTOMATION_SHEET_CLOSE_SEMANTIC_ID = "settings:automation:close"
NOTIFICATION_LISTENER_ACCESS_LABEL = "Open notification listener access"
NOTIFICATION_LISTENER_ACCESS_ARTIFACT_NAME = "media-session-listener-access.png"
NOTIFICATION_LISTENER_ACCESS_SEMANTIC_ID = "settings:automation:open-notification-listener"
MCP_ACCESS_SHEET_SNAPSHOT_REQUEST_ID = 73


def mcp_access_sheet_snapshot() -> dict[str, Any]:
    arguments = argparse.Namespace(
        host=AUTOMATION_HOST,
        port=MCP_PORT,
        expected_control_port=DEVICE_MCP_PORT,
        mutate=False,
    )
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        return MCP.require_tool_result(
            MCP.call_tool(
                arguments,
                session_id,
                MCP_ACCESS_SHEET_SNAPSHOT_REQUEST_ID,
                MCP.TOOL_SNAPSHOT,
            ),
        )
    finally:
        MCP.delete_session(arguments, session_id)


def test_automation_media_sessions_redact_content_and_control_only_allowlisted_targets(
    websocket_control: socket.socket,
    installed_configurable_provider_fixture: None,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    package_name = configurable_provider_fixture_package()
    metadata_configuration = media_session_configuration(
        original,
        AUTOMATION_MEDIA_SESSION_METADATA_SOURCE,
        AUTOMATION_MEDIA_SESSION_METADATA_CAPABILITIES,
        AUTOMATION_PATCH_STATE_ACTIONS,
        [],
    )
    denied_control_configuration = media_session_configuration(
        original,
        AUTOMATION_MEDIA_SESSION_CONTROL_SOURCE,
        AUTOMATION_MEDIA_SESSION_CAPABILITIES,
        AUTOMATION_MEDIA_SESSION_ACTIONS,
        [],
    )
    permitted_control_configuration = media_session_configuration(
        original,
        AUTOMATION_MEDIA_SESSION_CONTROL_SOURCE,
        AUTOMATION_MEDIA_SESSION_CAPABILITIES,
        AUTOMATION_MEDIA_SESSION_ACTIONS,
        [package_name],
    )
    try:
        device_command(f"pm grant {package_name} android.permission.POST_NOTIFICATIONS")
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=metadata_configuration,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        open_command_sheet()
        wait_for_snapshot_node(websocket_control, COMMAND_SETTINGS_SEMANTIC_ID)
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_TAP,
            semanticId=COMMAND_SETTINGS_SEMANTIC_ID,
        )
        wait_for_snapshot_node(websocket_control, AUTOMATION_SETTINGS_SEMANTIC_ID)
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_TAP,
            semanticId=AUTOMATION_SETTINGS_SEMANTIC_ID,
        )
        access_sheet_snapshot = wait_for_snapshot_node(
            websocket_control,
            NOTIFICATION_LISTENER_ACCESS_SEMANTIC_ID,
        )
        wait_for_snapshot_node(
            websocket_control,
            AUTOMATION_SHEET_CLOSE_SEMANTIC_ID,
        )
        access_sheet_node = AUTOMATION.require_node(
            access_sheet_snapshot,
            NOTIFICATION_LISTENER_ACCESS_SEMANTIC_ID,
        )
        assert AUTOMATION.require_node(
            mcp_access_sheet_snapshot(),
            NOTIFICATION_LISTENER_ACCESS_SEMANTIC_ID,
        ) == access_sheet_node
        wait_for_physical_text_bounds(NOTIFICATION_LISTENER_ACCESS_LABEL)
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / NOTIFICATION_LISTENER_ACCESS_ARTIFACT_NAME,
        )
        assert (ARTIFACT_DIRECTORY / NOTIFICATION_LISTENER_ACCESS_ARTIFACT_NAME).stat().st_size > 0
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_TAP,
            semanticId=AUTOMATION_SHEET_CLOSE_SEMANTIC_ID,
        )
        wait_for_snapshot_node(
            websocket_control,
            AUTOMATION_SHEET_CLOSE_SEMANTIC_ID,
            should_exist=False,
        )
        wait_for_snapshot_node(
            websocket_control,
            NOTIFICATION_LISTENER_ACCESS_SEMANTIC_ID,
            should_exist=False,
        )
        open_command_sheet()
        wait_for_snapshot_node(websocket_control, COMMAND_SETTINGS_SEMANTIC_ID)
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_TAP,
            semanticId=COMMAND_SETTINGS_SEMANTIC_ID,
        )
        wait_for_snapshot_node(websocket_control, AUTOMATION_SETTINGS_SEMANTIC_ID)
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_TAP,
            semanticId=AUTOMATION_SETTINGS_SEMANTIC_ID,
        )
        wait_for_snapshot_node(
            websocket_control,
            NOTIFICATION_LISTENER_ACCESS_SEMANTIC_ID,
        )
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_TAP,
            semanticId=NOTIFICATION_LISTENER_ACCESS_SEMANTIC_ID,
        )
        assert "com.android.settings" in run_device_operation("app-current")
        device_command(f"input keyevent {KEYCODE_BACK}")
        wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)
        device_command(f"cmd notification allow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
        wait_for_snapshot_node(
            websocket_control,
            AUTOMATION_SHEET_CLOSE_SEMANTIC_ID,
            should_exist=False,
        )
        wait_for_snapshot_node(
            websocket_control,
            NOTIFICATION_LISTENER_ACCESS_SEMANTIC_ID,
            should_exist=False,
        )
        status = wait_for_notification_listener_access(websocket_control)
        assert "mediaSessionsMetadata" in status["capabilities"]
        assert "mediaSessionsContent" in status["capabilities"]
        assert "mediaControl" in status["actions"]
        event = next(specification for specification in status["events"] if specification["type"] == "mediaSession")
        assert event["requiredCapability"] == "mediaSessionsMetadata"
        assert event["optionalSubscriptionFields"] == ["packages"]

        assert AUTOMATION_SERVICE_COMPONENT not in device_command(AUTOMATION_SERVICE_QUERY_COMMAND)
        start_fixture_media_session(package_name)
        metadata_updated = wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            "contentVisible",
            False,
        )
        metadata_script = next(
            script
            for script in metadata_updated["scripts"]
            if script["id"] == AUTOMATION_LIVE_SCRIPT_ID
        )
        assert metadata_script["state"]["contentVisible"] is False
        status = wait_for_media_session_status(websocket_control, expected_active_count=1)
        assert status["activeMediaSessionsTruncated"] is False

        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=denied_control_configuration,
        )
        start_fixture_media_session(package_name)
        denied_updated = wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            "playing",
            False,
        )
        denied_script = next(
            script
            for script in denied_updated["scripts"]
            if script["id"] == AUTOMATION_LIVE_SCRIPT_ID
        )
        assert denied_script["state"]["contentVisible"] is True
        assert any(
            record["event"] == "automation_action_rejected" and
            record.get("outcome") == "target_denied"
            for record in device_log_records()
        )

        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=permitted_control_configuration,
        )
        start_fixture_media_session(package_name)
        permitted_updated = wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            "playing",
            True,
        )
        permitted_script = next(
            script
            for script in permitted_updated["scripts"]
            if script["id"] == AUTOMATION_LIVE_SCRIPT_ID
        )
        assert permitted_script["state"] == {
            "contentVisible": True,
            "playing": True,
        }
    finally:
        device_command(f"am force-stop {package_name}")
        device_command(f"cmd notification disallow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
