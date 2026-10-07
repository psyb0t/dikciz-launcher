"""Policy-gated ringer-mode automation on the isolated Android emulator."""

from . import *


def ringer_mode_script_state(configuration: dict[str, Any]) -> dict[str, Any]:
    script = next(
        item for item in configuration["scripts"] if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
    )
    state = script["state"]
    assert isinstance(state, dict)
    return state


def mcp_ringer_mode_script_state() -> dict[str, Any]:
    arguments = argparse.Namespace(
        host=AUTOMATION_HOST,
        port=MCP_PORT,
        expected_control_port=DEVICE_MCP_PORT,
        mutate=False,
    )
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        configuration = MCP.require_configuration(
            MCP.require_tool_result(
                MCP.call_tool(
                    arguments,
                    session_id,
                    AUTOMATION_RINGER_MODE_MCP_CONFIG_GET_REQUEST_ID,
                    "dikciz_config_get",
                ),
            ),
        )
        return ringer_mode_script_state(configuration)
    finally:
        MCP.delete_session(arguments, session_id)


def ringer_mode_configuration(
    configuration: dict[str, Any],
    capabilities: list[str],
) -> dict[str, Any]:
    return automation_configuration(
        configuration,
        AUTOMATION_RINGER_MODE_SOURCE,
        AUTOMATION_RINGER_MODE_INITIAL_STATE,
        capabilities,
        ["patchState"],
        [
            {
                "event": AUTOMATION_RINGER_MODE_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": AUTOMATION_RINGER_MODE_COALESCING_KEY,
            },
        ],
    )


def test_automation_ringer_mode_is_policy_gated_and_uses_the_real_emulator_state(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    denied = ringer_mode_configuration(original, [])
    allowed = ringer_mode_configuration(original, [AUTOMATION_RINGER_MODE_CAPABILITY])
    try:
        emulator_ringer_mode_set(EMULATOR_RINGER_MODE_NORMAL)
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=denied,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        emulator_ringer_mode_set(EMULATOR_RINGER_MODE_VIBRATE)
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        denied_configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert ringer_mode_script_state(denied_configuration) == AUTOMATION_RINGER_MODE_INITIAL_STATE

        emulator_ringer_mode_set(EMULATOR_RINGER_MODE_NORMAL)
        status = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_STATUS)
        assert AUTOMATION_RINGER_MODE_CAPABILITY in status["capabilities"]
        assert AUTOMATION_RINGER_MODE_CAPABILITY not in status["androidAccess"]
        event = next(item for item in status["events"] if item["type"] == AUTOMATION_RINGER_MODE_EVENT)
        assert event["requiredCapability"] == AUTOMATION_RINGER_MODE_CAPABILITY
        assert event["requiredSubscriptionFields"] == []
        assert event["optionalSubscriptionFields"] == []

        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=allowed,
        )
        initial_configuration = wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_RINGER_MODE_STATE_KEY,
            EMULATOR_RINGER_MODE_NORMAL,
        )
        assert ringer_mode_script_state(initial_configuration) == {
            AUTOMATION_RINGER_MODE_STATE_KEY: EMULATOR_RINGER_MODE_NORMAL,
        }

        emulator_ringer_mode_set(EMULATOR_RINGER_MODE_VIBRATE)
        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_RINGER_MODE_STATE_KEY,
            EMULATOR_RINGER_MODE_VIBRATE,
        )
        emulator_ringer_mode_set(EMULATOR_RINGER_MODE_SILENT)
        configuration = wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_RINGER_MODE_STATE_KEY,
            EMULATOR_RINGER_MODE_SILENT,
        )
        state = ringer_mode_script_state(configuration)
        assert mcp_ringer_mode_script_state() == state
    finally:
        try:
            emulator_ringer_mode_set(EMULATOR_RINGER_MODE_NORMAL)
        finally:
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_CONFIG_REPLACE,
                config=original,
            )
