"""Do Not Disturb state automation behind explicit Android special access."""

from . import *


AUTOMATION_SETTINGS_SEMANTIC_ID = "settings:automation"
INTERRUPTION_FILTER_ACCESS_ARTIFACT_NAME = "interruption-filter-access.png"
INTERRUPTION_FILTER_ACCESS_LABEL = "Open Do Not Disturb access"
MCP_AUTOMATION_STATUS_COMPARISON_REQUEST_ID = 65
MCP_AUTOMATION_STATUS_REQUEST_ID = 66
MCP_MUTATE = False
NOTIFICATION_POLICY_ACCESS_KEY = "notificationPolicyAccess"


def interruption_filter_script_state(configuration: dict[str, Any]) -> dict[str, Any]:
    script = next(
        item for item in configuration["scripts"] if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
    )
    state = script["state"]
    assert isinstance(state, dict)
    return state


def mcp_interruption_filter_script_state() -> dict[str, Any]:
    arguments = argparse.Namespace(
        host=AUTOMATION_HOST,
        port=MCP_PORT,
        expected_control_port=DEVICE_MCP_PORT,
        mutate=MCP_MUTATE,
    )
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        configuration = MCP.require_configuration(
            MCP.require_tool_result(
                MCP.call_tool(
                    arguments,
                    session_id,
                    AUTOMATION_INTERRUPTION_FILTER_MCP_CONFIG_GET_REQUEST_ID,
                    MCP.TOOL_CONFIG_GET,
                ),
            ),
        )
        return interruption_filter_script_state(configuration)
    finally:
        MCP.delete_session(arguments, session_id)


def assert_mcp_automation_status(expected_status: dict[str, Any]) -> None:
    arguments = argparse.Namespace(
        host=AUTOMATION_HOST,
        port=MCP_PORT,
        expected_control_port=DEVICE_MCP_PORT,
        mutate=MCP_MUTATE,
    )
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        MCP.verify_automation_status(arguments, session_id, MCP_AUTOMATION_STATUS_REQUEST_ID)
        actual_status = MCP.require_tool_result(
            MCP.call_tool(
                arguments,
                session_id,
                MCP_AUTOMATION_STATUS_COMPARISON_REQUEST_ID,
                MCP.TOOL_AUTOMATION_STATUS,
            ),
        )
        assert actual_status == expected_status
    finally:
        MCP.delete_session(arguments, session_id)


def interruption_filter_configuration(
    configuration: dict[str, Any],
    capabilities: list[str],
) -> dict[str, Any]:
    return automation_configuration(
        configuration,
        AUTOMATION_INTERRUPTION_FILTER_SOURCE,
        AUTOMATION_INTERRUPTION_FILTER_INITIAL_STATE,
        capabilities,
        ["patchState"],
        [
            {
                "event": AUTOMATION_INTERRUPTION_FILTER_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": AUTOMATION_INTERRUPTION_FILTER_COALESCING_KEY,
            },
        ],
    )


def test_automation_interruption_filter_requires_notification_policy_access_and_tracks_real_emulator_changes(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    denied = interruption_filter_configuration(original, [])
    allowed = interruption_filter_configuration(
        original,
        [AUTOMATION_INTERRUPTION_FILTER_CAPABILITY],
    )
    try:
        emulator_interruption_filter_set(EMULATOR_INTERRUPTION_FILTER_ALL)
        emulator_notification_policy_access_set(EMULATOR_NOTIFICATION_POLICY_ACCESS_REVOKED)
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=denied,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        emulator_interruption_filter_set(EMULATOR_INTERRUPTION_FILTER_PRIORITY)
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        denied_configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert interruption_filter_script_state(denied_configuration) == AUTOMATION_INTERRUPTION_FILTER_INITIAL_STATE

        status = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_STATUS)
        assert AUTOMATION_INTERRUPTION_FILTER_CAPABILITY in status["capabilities"]
        assert status["androidAccess"][NOTIFICATION_POLICY_ACCESS_KEY] is False
        event = next(
            item for item in status["events"] if item["type"] == AUTOMATION_INTERRUPTION_FILTER_EVENT
        )
        assert event["requiredCapability"] == AUTOMATION_INTERRUPTION_FILTER_CAPABILITY
        assert event["requiredSubscriptionFields"] == []
        assert event["optionalSubscriptionFields"] == []

        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=allowed,
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
        wait_for_physical_text_bounds(INTERRUPTION_FILTER_ACCESS_LABEL)
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / INTERRUPTION_FILTER_ACCESS_ARTIFACT_NAME,
        )
        assert (ARTIFACT_DIRECTORY / INTERRUPTION_FILTER_ACCESS_ARTIFACT_NAME).stat().st_size > 0
        device_command(f"input keyevent {KEYCODE_BACK}")

        emulator_notification_policy_access_set(EMULATOR_NOTIFICATION_POLICY_ACCESS_GRANTED)
        configuration = wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_INTERRUPTION_FILTER_STATE_KEY,
            EMULATOR_INTERRUPTION_FILTER_PRIORITY,
        )
        assert interruption_filter_script_state(configuration) == {
            AUTOMATION_INTERRUPTION_FILTER_STATE_KEY: EMULATOR_INTERRUPTION_FILTER_PRIORITY,
        }
        status = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_STATUS)
        assert status["androidAccess"][NOTIFICATION_POLICY_ACCESS_KEY] is True
        assert_mcp_automation_status(status)

        emulator_interruption_filter_set(EMULATOR_INTERRUPTION_FILTER_ALARMS)
        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_INTERRUPTION_FILTER_STATE_KEY,
            EMULATOR_INTERRUPTION_FILTER_ALARMS,
        )
        emulator_interruption_filter_set(EMULATOR_INTERRUPTION_FILTER_NONE)
        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_INTERRUPTION_FILTER_STATE_KEY,
            EMULATOR_INTERRUPTION_FILTER_NONE,
        )
        emulator_interruption_filter_set(EMULATOR_INTERRUPTION_FILTER_ALL)
        configuration = wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_INTERRUPTION_FILTER_STATE_KEY,
            EMULATOR_INTERRUPTION_FILTER_ALL,
        )
        assert mcp_interruption_filter_script_state() == interruption_filter_script_state(configuration)
    finally:
        try:
            emulator_interruption_filter_set(EMULATOR_INTERRUPTION_FILTER_ALL)
        finally:
            try:
                emulator_notification_policy_access_set(EMULATOR_NOTIFICATION_POLICY_ACCESS_REVOKED)
            finally:
                AUTOMATION.request(
                    websocket_control,
                    AUTOMATION.TYPE_CONFIG_REPLACE,
                    config=original,
                )
