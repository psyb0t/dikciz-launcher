"""Permission-gated phone-state automation using the debug Android fixture."""

from . import *


PHONE_STATE_CAPABILITY = "phoneState"
PHONE_STATE_EVENT = "phoneState"
PHONE_STATE_INITIAL = {"state": "waiting"}
PHONE_STATE_PERMISSION = "android.permission.READ_PHONE_STATE"
PHONE_STATE_PERMISSION_ACCESS_KEY = "phoneStateRead"
PHONE_STATE_STATE_KEY = "state"
PHONE_STATE_IDLE = "idle"
PHONE_STATE_OFFHOOK = "offhook"
PHONE_STATE_RINGING = "ringing"
PHONE_STATE_SOURCE = (
    'function on_event(event)\n'
    '  if event.type ~= "phoneState" then return end\n'
    '  return { type = "patchState", values = { state = event.payload.state } }\n'
    'end'
)
MCP_AUTOMATION_STATUS_COMPARISON_REQUEST_ID = 31
MCP_AUTOMATION_STATUS_REQUEST_ID = 30
MCP_MUTATE = False
DEBUG_PHONE_STATE_ACTION = "org.fossify.home.dikciz.action.DEBUG_PHONE_STATE"
DEBUG_PHONE_STATE_COMPONENT = (
    f"{DIKCIZ_DEBUG_PACKAGE}/org.fossify.home.dikciz.DikcizDebugPhoneStateReceiver"
)
DEBUG_PHONE_STATE_EXTRA = "phoneState"
DEBUG_PHONE_STATE_STATES = {PHONE_STATE_IDLE, PHONE_STATE_OFFHOOK, PHONE_STATE_RINGING}
DEBUG_PHONE_STATE_AM_COMMAND = "am broadcast"


@pytest.fixture
def phone_state_permission_cleanup() -> Generator[None, None, None]:
    try:
        yield
    finally:
        device_command(f"pm revoke {DIKCIZ_DEBUG_PACKAGE} {PHONE_STATE_PERMISSION}")


@pytest.fixture
def phone_state_websocket_control(
    phone_state_permission_cleanup: None,
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


def debug_phone_state_set(state: str) -> str:
    assert state in DEBUG_PHONE_STATE_STATES
    return device_command(
        " ".join(
            (
                DEBUG_PHONE_STATE_AM_COMMAND,
                "-a",
                DEBUG_PHONE_STATE_ACTION,
                "-n",
                DEBUG_PHONE_STATE_COMPONENT,
                "--es",
                DEBUG_PHONE_STATE_EXTRA,
                state,
            ),
        ),
    )


def test_automation_phone_state_requires_permission_and_tracks_debug_platform_states(
    phone_state_websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(phone_state_websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = automation_configuration(
        original,
        PHONE_STATE_SOURCE,
        PHONE_STATE_INITIAL,
        [PHONE_STATE_CAPABILITY],
        ["patchState"],
        [
            {
                "event": PHONE_STATE_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": PHONE_STATE_EVENT,
            },
        ],
    )
    try:
        device_command(f"pm revoke {DIKCIZ_DEBUG_PACKAGE} {PHONE_STATE_PERMISSION}")
        unavailable_status = AUTOMATION.request(
            phone_state_websocket_control,
            AUTOMATION.TYPE_AUTOMATION_STATUS,
        )
        assert unavailable_status["androidAccess"][PHONE_STATE_PERMISSION_ACCESS_KEY] is False
        AUTOMATION.request(
            phone_state_websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        debug_phone_state_set(PHONE_STATE_RINGING)
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        unavailable_configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(phone_state_websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        unavailable_script = next(
            script
            for script in unavailable_configuration["scripts"]
            if script["id"] == AUTOMATION_LIVE_SCRIPT_ID
        )
        assert unavailable_script["state"] == PHONE_STATE_INITIAL
        debug_phone_state_set(PHONE_STATE_IDLE)

        device_command(f"pm grant {DIKCIZ_DEBUG_PACKAGE} {PHONE_STATE_PERMISSION}")
        available_status = AUTOMATION.request(
            phone_state_websocket_control,
            AUTOMATION.TYPE_AUTOMATION_STATUS,
        )
        assert PHONE_STATE_CAPABILITY in available_status["capabilities"]
        assert available_status["androidAccess"][PHONE_STATE_PERMISSION_ACCESS_KEY] is True
        event = next(item for item in available_status["events"] if item["type"] == PHONE_STATE_EVENT)
        assert event["requiredCapability"] == PHONE_STATE_CAPABILITY
        assert event["requiredSubscriptionFields"] == []
        assert event["optionalSubscriptionFields"] == []
        assert_mcp_automation_status(available_status)

        AUTOMATION.request(
            phone_state_websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        )
        debug_phone_state_set(PHONE_STATE_RINGING)
        wait_for_automation_script_state(
            phone_state_websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            PHONE_STATE_STATE_KEY,
            PHONE_STATE_RINGING,
        )
        debug_phone_state_set(PHONE_STATE_OFFHOOK)
        wait_for_automation_script_state(
            phone_state_websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            PHONE_STATE_STATE_KEY,
            PHONE_STATE_OFFHOOK,
        )
        debug_phone_state_set(PHONE_STATE_IDLE)
        wait_for_automation_script_state(
            phone_state_websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            PHONE_STATE_STATE_KEY,
            PHONE_STATE_IDLE,
        )
    finally:
        debug_phone_state_set(PHONE_STATE_IDLE)
