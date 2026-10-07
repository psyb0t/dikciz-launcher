"""Policy-gated device-idle automation on the isolated Android emulator."""

from . import *


def device_idle_script_state(configuration: dict[str, Any]) -> dict[str, Any]:
    script = next(
        item for item in configuration["scripts"] if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
    )
    state = script["state"]
    assert isinstance(state, dict)
    return state


def mcp_device_idle_script_state() -> dict[str, Any]:
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
                    AUTOMATION_DEVICE_IDLE_MCP_CONFIG_GET_REQUEST_ID,
                    MCP.TOOL_CONFIG_GET,
                ),
            ),
        )
        return device_idle_script_state(configuration)
    finally:
        MCP.delete_session(arguments, session_id)


def device_idle_configuration(
    configuration: dict[str, Any],
    capabilities: list[str],
) -> dict[str, Any]:
    return automation_configuration(
        configuration,
        AUTOMATION_DEVICE_IDLE_SOURCE,
        AUTOMATION_DEVICE_IDLE_INITIAL_STATE,
        capabilities,
        ["patchState"],
        [
            {
                "event": AUTOMATION_DEVICE_IDLE_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": AUTOMATION_DEVICE_IDLE_COALESCING_KEY,
            },
        ],
    )


def test_automation_device_idle_mode_is_policy_gated_and_uses_the_real_emulator_state(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    denied = device_idle_configuration(original, [])
    allowed = device_idle_configuration(original, [AUTOMATION_DEVICE_IDLE_CAPABILITY])
    try:
        emulator_device_idle_set(EMULATOR_DEVICE_IDLE_MODE_ACTIVE)
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=denied,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        emulator_device_idle_set(EMULATOR_DEVICE_IDLE_MODE_IDLE)
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        denied_configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert device_idle_script_state(denied_configuration) == AUTOMATION_DEVICE_IDLE_INITIAL_STATE

        emulator_device_idle_set(EMULATOR_DEVICE_IDLE_MODE_ACTIVE)
        status = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_STATUS)
        assert AUTOMATION_DEVICE_IDLE_CAPABILITY in status["capabilities"]
        assert AUTOMATION_DEVICE_IDLE_CAPABILITY not in status["androidAccess"]
        event = next(item for item in status["events"] if item["type"] == AUTOMATION_DEVICE_IDLE_EVENT)
        assert event["requiredCapability"] == AUTOMATION_DEVICE_IDLE_CAPABILITY
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
            AUTOMATION_DEVICE_IDLE_STATE_KEY,
            False,
        )
        assert device_idle_script_state(initial_configuration) == {
            AUTOMATION_DEVICE_IDLE_STATE_KEY: False,
        }

        emulator_device_idle_set(EMULATOR_DEVICE_IDLE_MODE_IDLE)
        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_DEVICE_IDLE_STATE_KEY,
            True,
        )
        emulator_device_idle_set(EMULATOR_DEVICE_IDLE_MODE_ACTIVE)
        configuration = wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_DEVICE_IDLE_STATE_KEY,
            False,
        )
        state = device_idle_script_state(configuration)
        assert mcp_device_idle_script_state() == state
    finally:
        try:
            emulator_device_idle_set(EMULATOR_DEVICE_IDLE_MODE_ACTIVE)
        finally:
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_CONFIG_REPLACE,
                config=original,
            )
