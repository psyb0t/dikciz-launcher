"""Policy-gated thermal-status automation on the isolated Android emulator."""

from . import *


def thermal_script_state(configuration: dict[str, Any]) -> dict[str, Any]:
    script = next(
        item for item in configuration["scripts"] if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
    )
    state = script["state"]
    assert isinstance(state, dict)
    return state


def mcp_thermal_script_state() -> dict[str, Any]:
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
                    AUTOMATION_THERMAL_MCP_CONFIG_GET_REQUEST_ID,
                    "dikciz_config_get",
                ),
            ),
        )
        return thermal_script_state(configuration)
    finally:
        MCP.delete_session(arguments, session_id)


def thermal_configuration(
    configuration: dict[str, Any],
    capabilities: list[str],
) -> dict[str, Any]:
    return automation_configuration(
        configuration,
        AUTOMATION_THERMAL_SOURCE,
        AUTOMATION_THERMAL_INITIAL_STATE,
        capabilities,
        ["patchState"],
        [
            {
                "event": AUTOMATION_THERMAL_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": AUTOMATION_THERMAL_COALESCING_KEY,
            },
        ],
    )


def test_automation_thermal_status_is_policy_gated_and_uses_the_real_emulator_listener(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    denied = thermal_configuration(original, [])
    allowed = thermal_configuration(original, [AUTOMATION_THERMAL_CAPABILITY])
    try:
        emulator_thermal_set(EMULATOR_THERMAL_RESET_STATE)
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=denied,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        emulator_thermal_set(EMULATOR_THERMAL_SEVERE)
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        denied_configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert thermal_script_state(denied_configuration) == AUTOMATION_THERMAL_INITIAL_STATE

        emulator_thermal_set(EMULATOR_THERMAL_RESET_STATE)
        status = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_STATUS)
        assert AUTOMATION_THERMAL_CAPABILITY in status["capabilities"]
        assert AUTOMATION_THERMAL_CAPABILITY not in status["androidAccess"]
        event = next(item for item in status["events"] if item["type"] == AUTOMATION_THERMAL_EVENT)
        assert event["requiredCapability"] == AUTOMATION_THERMAL_CAPABILITY
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
            AUTOMATION_THERMAL_STATE_KEY,
            AUTOMATION_THERMAL_NONE_STATE,
        )
        initial_state = thermal_script_state(initial_configuration)
        assert initial_state[AUTOMATION_THERMAL_LEVEL_KEY] == AUTOMATION_THERMAL_NONE_LEVEL
        emulator_thermal_set(EMULATOR_THERMAL_SEVERE)
        configuration = wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_THERMAL_STATE_KEY,
            AUTOMATION_THERMAL_SEVERE_STATE,
        )
        state = thermal_script_state(configuration)
        assert state[AUTOMATION_THERMAL_LEVEL_KEY] == AUTOMATION_THERMAL_SEVERE_LEVEL
        assert mcp_thermal_script_state() == state
    finally:
        try:
            emulator_thermal_set(EMULATOR_THERMAL_RESET_STATE)
        finally:
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_CONFIG_REPLACE,
                config=original,
            )
