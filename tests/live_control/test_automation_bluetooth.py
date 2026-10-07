"""Permission-gated Bluetooth state automation on the isolated Android emulator."""

from . import *


BLUETOOTH_CAPABILITY = "bluetoothState"
BLUETOOTH_CONNECT_PERMISSION = "android.permission.BLUETOOTH_CONNECT"
BLUETOOTH_DISABLED_STATE = "off"
BLUETOOTH_ENABLED_STATE = "on"
BLUETOOTH_EVENT = "bluetoothState"
BLUETOOTH_INITIAL_STATE = {"enabled": "waiting"}
BLUETOOTH_PERMISSION_ACCESS_KEY = "bluetoothConnect"
BLUETOOTH_SOURCE = (
    'function on_event(event)\n'
    '  if event.type ~= "bluetoothState" then return end\n'
    '  return { type = "patchState", values = { enabled = event.payload.enabled } }\n'
    'end'
)
BLUETOOTH_STATE_KEY = "enabled"
MCP_AUTOMATION_STATUS_COMPARISON_REQUEST_ID = 11
MCP_AUTOMATION_STATUS_REQUEST_ID = 10
MCP_MUTATE = False


@pytest.fixture
def bluetooth_permission_cleanup() -> Generator[None, None, None]:
    try:
        yield
    finally:
        try:
            emulator_bluetooth_set(BLUETOOTH_ENABLED_STATE)
        finally:
            device_command(f"pm revoke {DIKCIZ_DEBUG_PACKAGE} {BLUETOOTH_CONNECT_PERMISSION}")
            # The revoke kills the process, and the launcher fixture tears down
            # next by talking to the control plane. Waiting here means it finds
            # one instead of a socket that closes on the handshake.
            restore_system_home()
            wait_for_restarted_websocket_control().close()


@pytest.fixture
def bluetooth_websocket_control(
    bluetooth_permission_cleanup: None,
    websocket_control: socket.socket,
) -> Generator[socket.socket, None, None]:
    yield websocket_control


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
        MCP.verify_automation_status(
            arguments,
            session_id,
            MCP_AUTOMATION_STATUS_REQUEST_ID,
        )
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


def test_automation_bluetooth_state_requires_permission_and_tracks_real_emulator_changes(
    bluetooth_websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(bluetooth_websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = automation_configuration(
        original,
        BLUETOOTH_SOURCE,
        BLUETOOTH_INITIAL_STATE,
        [BLUETOOTH_CAPABILITY],
        ["patchState"],
        [
            {
                "event": BLUETOOTH_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": BLUETOOTH_EVENT,
            },
        ],
    )
    device_command(f"pm revoke {DIKCIZ_DEBUG_PACKAGE} {BLUETOOTH_CONNECT_PERMISSION}")
    unavailable_status = AUTOMATION.request(
        bluetooth_websocket_control,
        AUTOMATION.TYPE_AUTOMATION_STATUS,
    )
    assert unavailable_status["androidAccess"][BLUETOOTH_PERMISSION_ACCESS_KEY] is False

    AUTOMATION.request(
        bluetooth_websocket_control,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=configured,
    )
    time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
    unavailable_configuration = AUTOMATION.require_configuration(
        AUTOMATION.request(bluetooth_websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    unavailable_script = next(
        script
        for script in unavailable_configuration["scripts"]
        if script["id"] == AUTOMATION_LIVE_SCRIPT_ID
    )
    assert unavailable_script["state"] == BLUETOOTH_INITIAL_STATE

    device_command(f"pm grant {DIKCIZ_DEBUG_PACKAGE} {BLUETOOTH_CONNECT_PERMISSION}")
    available_status = AUTOMATION.request(
        bluetooth_websocket_control,
        AUTOMATION.TYPE_AUTOMATION_STATUS,
    )
    assert BLUETOOTH_CAPABILITY in available_status["capabilities"]
    assert available_status["androidAccess"][BLUETOOTH_PERMISSION_ACCESS_KEY] is True
    event = next(item for item in available_status["events"] if item["type"] == BLUETOOTH_EVENT)
    assert event["requiredCapability"] == BLUETOOTH_CAPABILITY
    assert event["requiredSubscriptionFields"] == []
    assert event["optionalSubscriptionFields"] == []
    assert_mcp_automation_status(available_status)

    AUTOMATION.request(
        bluetooth_websocket_control,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=configured,
    )
    emulator_bluetooth_set(BLUETOOTH_DISABLED_STATE)
    wait_for_automation_script_state(
        bluetooth_websocket_control,
        AUTOMATION_LIVE_SCRIPT_ID,
        BLUETOOTH_STATE_KEY,
        False,
    )
    emulator_bluetooth_set(BLUETOOTH_ENABLED_STATE)
    wait_for_automation_script_state(
        bluetooth_websocket_control,
        AUTOMATION_LIVE_SCRIPT_ID,
        BLUETOOTH_STATE_KEY,
        True,
    )
