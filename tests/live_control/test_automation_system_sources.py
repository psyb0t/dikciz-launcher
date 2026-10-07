"""System-broadcast automation sources on the isolated Android emulator."""

from . import *


def automation_system_script_state(configuration: dict[str, Any]) -> dict[str, Any]:
    script = next(
        item for item in configuration["scripts"] if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
    )
    state = script["state"]
    assert isinstance(state, dict)
    return state


def mcp_automation_system_script_state() -> dict[str, Any]:
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
                    AUTOMATION_SYSTEM_MCP_CONFIG_GET_REQUEST_ID,
                    "dikciz_config_get",
                ),
            ),
        )
        return automation_system_script_state(configuration)
    finally:
        MCP.delete_session(arguments, session_id)


def test_automation_status_is_typed_bounded_and_available_through_both_control_planes(
    websocket_control: socket.socket,
) -> None:
    AUTOMATION.verify_automation_status(websocket_control)
    websocket_status = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_STATUS)
    assert websocket_status[AUTOMATION.KEY_ANDROID_ACCESS][AUTOMATION.KEY_SHARED_STORAGE] is True
    arguments = argparse.Namespace(
        host=AUTOMATION_HOST,
        port=MCP_PORT,
        expected_control_port=DEVICE_MCP_PORT,
        mutate=False,
    )
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        MCP.verify_automation_status(arguments, session_id, 10)
        mcp_status = MCP.require_tool_result(
            MCP.call_tool(arguments, session_id, 11, MCP.TOOL_AUTOMATION_STATUS),
        )
        assert mcp_status[MCP.KEY_ANDROID_ACCESS][MCP.KEY_SHARED_STORAGE] is True
    finally:
        MCP.delete_session(arguments, session_id)


def test_automation_time_user_presence_and_connectivity_events_use_isolated_android_wifi(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = automation_configuration(
        original,
        AUTOMATION_SYSTEM_SOURCE,
        AUTOMATION_SYSTEM_INITIAL_STATE,
        AUTOMATION_SYSTEM_CAPABILITIES,
        ["patchState"],
        [
            {
                "event": "time",
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": AUTOMATION_SYSTEM_TIME_COALESCING_KEY,
            },
            {
                "event": "userPresent",
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": AUTOMATION_SYSTEM_USER_PRESENT_COALESCING_KEY,
            },
            {
                "event": "connectivity",
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": AUTOMATION_SYSTEM_CONNECTIVITY_COALESCING_KEY,
            },
        ],
    )
    try:
        emulator_wifi_set(AUTOMATION_SYSTEM_WIFI_DISCONNECTED)
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)

        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_SYSTEM_TIME_STATE_KEY,
            AUTOMATION_SYSTEM_TIME_ACTION,
            timeout_seconds=AUTOMATION_SYSTEM_TIME_TICK_TIMEOUT_SECONDS,
        )

        device_command(f"input keyevent {AUTOMATION_SCREEN_TOGGLE_KEYCODE}")
        device_command(f"input keyevent {AUTOMATION_SCREEN_WAKE_KEYCODE}")
        device_command(AUTOMATION_SYSTEM_UNLOCK_COMMAND)
        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_SYSTEM_USER_PRESENT_STATE_KEY,
            True,
        )

        emulator_wifi_set(AUTOMATION_SYSTEM_WIFI_CONNECTED)
        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_SYSTEM_CONNECTIVITY_STATE_KEY,
            AUTOMATION_SYSTEM_CONNECTIVITY_CONNECTED_STATE,
        )
        connected_state = automation_system_script_state(
            AUTOMATION.require_configuration(
                AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
            ),
        )
        assert (
            connected_state[AUTOMATION_SYSTEM_CONNECTIVITY_VALIDATED_STATE_KEY]
            is AUTOMATION_SYSTEM_CONNECTIVITY_WIFI_VALIDATED
        )
        assert connected_state[AUTOMATION_SYSTEM_CONNECTIVITY_METERED_STATE_KEY] is False
        assert connected_state[AUTOMATION_SYSTEM_CONNECTIVITY_HAS_WIFI_STATE_KEY] is True
        assert mcp_automation_system_script_state() == connected_state
        emulator_wifi_set(AUTOMATION_SYSTEM_WIFI_DISCONNECTED)
        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_SYSTEM_CONNECTIVITY_STATE_KEY,
            AUTOMATION_SYSTEM_CONNECTIVITY_DISCONNECTED_STATE,
        )
        disconnected_state = automation_system_script_state(
            AUTOMATION.require_configuration(
                AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
            ),
        )
        assert disconnected_state[AUTOMATION_SYSTEM_CONNECTIVITY_VALIDATED_STATE_KEY] is False
        assert disconnected_state[AUTOMATION_SYSTEM_CONNECTIVITY_METERED_STATE_KEY] is False
        assert disconnected_state[AUTOMATION_SYSTEM_CONNECTIVITY_HAS_WIFI_STATE_KEY] is False
    finally:
        try:
            device_command(f"input keyevent {AUTOMATION_SCREEN_WAKE_KEYCODE}")
        finally:
            try:
                emulator_wifi_set(AUTOMATION_SYSTEM_WIFI_DISCONNECTED)
            finally:
                AUTOMATION.request(
                    websocket_control,
                    AUTOMATION.TYPE_CONFIG_REPLACE,
                    config=original,
                )
