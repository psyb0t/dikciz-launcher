"""Policy-gated Battery Saver automation on the isolated Android emulator."""

from . import *


def power_save_script_state(configuration: dict[str, Any]) -> dict[str, Any]:
    script = next(
        item for item in configuration["scripts"] if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
    )
    state = script["state"]
    assert isinstance(state, dict)
    return state


def mcp_power_save_script_state() -> dict[str, Any]:
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
                    AUTOMATION_POWER_SAVE_MCP_CONFIG_GET_REQUEST_ID,
                    "dikciz_config_get",
                ),
            ),
        )
        return power_save_script_state(configuration)
    finally:
        MCP.delete_session(arguments, session_id)


def power_save_configuration(
    configuration: dict[str, Any],
    capabilities: list[str],
) -> dict[str, Any]:
    return automation_configuration(
        configuration,
        AUTOMATION_POWER_SAVE_SOURCE,
        AUTOMATION_POWER_SAVE_INITIAL_STATE,
        capabilities,
        ["patchState"],
        [
            {
                "event": AUTOMATION_POWER_SAVE_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": AUTOMATION_POWER_SAVE_COALESCING_KEY,
            },
        ],
    )


def test_automation_power_save_mode_is_policy_gated_and_uses_the_real_emulator_state(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    control = websocket_control
    denied = power_save_configuration(original, [])
    allowed = power_save_configuration(original, [AUTOMATION_POWER_SAVE_CAPABILITY])
    try:
        emulator_power_set(EMULATOR_POWER_AC_OFF)
        emulator_power_save_set(EMULATOR_POWER_SAVE_OFF)
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=denied,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        emulator_power_save_set(EMULATOR_POWER_SAVE_ON)
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        control = wait_for_restarted_websocket_control()
        denied_configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert power_save_script_state(denied_configuration) == AUTOMATION_POWER_SAVE_INITIAL_STATE

        emulator_power_save_set(EMULATOR_POWER_SAVE_OFF)
        control.close()
        control = wait_for_restarted_websocket_control()
        status = AUTOMATION.request(control, AUTOMATION.TYPE_AUTOMATION_STATUS)
        assert AUTOMATION_POWER_SAVE_CAPABILITY in status["capabilities"]
        assert AUTOMATION_POWER_SAVE_CAPABILITY not in status["androidAccess"]
        event = next(item for item in status["events"] if item["type"] == AUTOMATION_POWER_SAVE_EVENT)
        assert event["requiredCapability"] == AUTOMATION_POWER_SAVE_CAPABILITY
        assert event["requiredSubscriptionFields"] == []
        assert event["optionalSubscriptionFields"] == []

        AUTOMATION.request(
            control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=allowed,
        )
        initial_configuration = wait_for_automation_script_state(
            control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_POWER_SAVE_ENABLED_KEY,
            False,
        )
        assert power_save_script_state(initial_configuration) == {
            AUTOMATION_POWER_SAVE_ENABLED_KEY: False,
        }
        emulator_power_save_set(EMULATOR_POWER_SAVE_ON)
        control.close()
        control = wait_for_restarted_websocket_control()
        configuration = wait_for_automation_script_state(
            control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_POWER_SAVE_ENABLED_KEY,
            True,
        )
        state = power_save_script_state(configuration)
        assert mcp_power_save_script_state() == state
    finally:
        restoration_control: socket.socket | None = None
        try:
            emulator_power_save_set(EMULATOR_POWER_SAVE_OFF)
            emulator_power_set(EMULATOR_POWER_AC_ON)
            restoration_control = wait_for_restarted_websocket_control()
            AUTOMATION.request(
                restoration_control,
                AUTOMATION.TYPE_CONFIG_REPLACE,
                config=original,
            )
        finally:
            if restoration_control is not None:
                restoration_control.close()
            if control is not websocket_control:
                control.close()
