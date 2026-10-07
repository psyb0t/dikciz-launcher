"""Policy-gated night-mode automation on the isolated Android emulator."""

from . import *


def night_mode_script_state(configuration: dict[str, Any]) -> dict[str, Any]:
    script = next(
        item for item in configuration["scripts"] if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
    )
    state = script["state"]
    assert isinstance(state, dict)
    return state


def mcp_night_mode_script_state() -> dict[str, Any]:
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
                    AUTOMATION_NIGHT_MODE_MCP_CONFIG_GET_REQUEST_ID,
                    MCP.TOOL_CONFIG_GET,
                ),
            ),
        )
        return night_mode_script_state(configuration)
    finally:
        MCP.delete_session(arguments, session_id)


def night_mode_configuration(
    configuration: dict[str, Any],
    capabilities: list[str],
) -> dict[str, Any]:
    return automation_configuration(
        configuration,
        AUTOMATION_NIGHT_MODE_SOURCE,
        AUTOMATION_NIGHT_MODE_INITIAL_STATE,
        capabilities,
        ["patchState"],
        [
            {
                "event": AUTOMATION_NIGHT_MODE_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": AUTOMATION_NIGHT_MODE_COALESCING_KEY,
            },
        ],
    )


def test_automation_night_mode_is_policy_gated_and_uses_the_real_emulator_state(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    original_night_mode = device_night_mode()
    control = websocket_control
    denied = night_mode_configuration(original, [])
    allowed = night_mode_configuration(original, [AUTOMATION_NIGHT_MODE_CAPABILITY])
    try:
        emulator_night_mode_set(EMULATOR_NIGHT_MODE_NO)
        control = wait_for_restarted_websocket_control()
        AUTOMATION.request(
            control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=denied,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        emulator_night_mode_set(EMULATOR_NIGHT_MODE_YES)
        control.close()
        control = wait_for_restarted_websocket_control()
        denied_configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert night_mode_script_state(denied_configuration) == AUTOMATION_NIGHT_MODE_INITIAL_STATE

        emulator_night_mode_set(EMULATOR_NIGHT_MODE_NO)
        control.close()
        control = wait_for_restarted_websocket_control()
        status = AUTOMATION.request(control, AUTOMATION.TYPE_AUTOMATION_STATUS)
        assert AUTOMATION_NIGHT_MODE_CAPABILITY in status["capabilities"]
        assert AUTOMATION_NIGHT_MODE_CAPABILITY not in status["androidAccess"]
        event = next(item for item in status["events"] if item["type"] == AUTOMATION_NIGHT_MODE_EVENT)
        assert event["requiredCapability"] == AUTOMATION_NIGHT_MODE_CAPABILITY
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
            AUTOMATION_NIGHT_MODE_STATE_KEY,
            False,
        )
        assert night_mode_script_state(initial_configuration) == {
            AUTOMATION_NIGHT_MODE_STATE_KEY: False,
        }

        emulator_night_mode_set(EMULATOR_NIGHT_MODE_YES)
        control.close()
        control = wait_for_restarted_websocket_control()
        wait_for_automation_script_state(
            control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_NIGHT_MODE_STATE_KEY,
            True,
        )
        emulator_night_mode_set(EMULATOR_NIGHT_MODE_NO)
        control.close()
        control = wait_for_restarted_websocket_control()
        configuration = wait_for_automation_script_state(
            control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_NIGHT_MODE_STATE_KEY,
            False,
        )
        state = night_mode_script_state(configuration)
        assert mcp_night_mode_script_state() == state
    finally:
        restoration_control: socket.socket | None = None
        try:
            emulator_night_mode_set(original_night_mode)
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
