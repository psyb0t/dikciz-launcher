"""Bounded Health Connect daily-step automation through the debug-only event seam."""

from . import *


ACTION_PATCH_STATE = "patchState"
DEBUG_HEALTH_CONNECT_ACTION = "org.fossify.home.dikciz.action.DEBUG_HEALTH_DAILY_STEPS"
DEBUG_HEALTH_CONNECT_COMPONENT = (
    f"{DIKCIZ_DEBUG_PACKAGE}/org.fossify.home.dikciz.DikcizDebugHealthConnectReceiver"
)
DEBUG_HEALTH_CONNECT_STEPS_EXTRA = "healthDailySteps"
EVENT_DELIVERY_REJECTED = "automation_delivery_rejected"
HEALTH_CAPABILITY = "healthSteps"
HEALTH_EVENT = "healthDailySteps"
HEALTH_INITIAL_STATE = {
    "dayStartMilliseconds": "waiting",
    "steps": "waiting",
}
HEALTH_REFRESH_INTERVAL_MILLISECONDS = 900_000
HEALTH_RATE_LIMIT_MILLISECONDS = 60_000
HEALTH_STEPS_FIRST = 4_242
HEALTH_STEPS_SECOND = 4_243
HEALTH_STATUS_ACCESS_KEY = "healthConnectAvailable"
PAYLOAD_DAY_START_MILLISECONDS = "dayStartMilliseconds"
PAYLOAD_STEPS = "steps"
REASON_CAPABILITY_DENIED = "capability_denied"
REASON_RATE_LIMITED = "rate_limited"
HEALTH_SOURCE = (
    'function on_event(event)\n'
    '  if event.type ~= "healthDailySteps" then return end\n'
    '  return { type = "patchState", values = {\n'
    '    dayStartMilliseconds = event.payload.dayStartMilliseconds,\n'
    '    steps = event.payload.steps\n'
    '  } }\n'
    'end'
)
MCP_AUTOMATION_STATUS_COMPARISON_REQUEST_ID = 51
MCP_AUTOMATION_STATUS_REQUEST_ID = 50
MCP_MUTATE = False
MAXIMUM_STEPS = 1_000_000


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


def health_configuration(
    original: dict[str, Any],
    capabilities: list[str],
    minimum_interval_milliseconds: int,
) -> dict[str, Any]:
    return automation_configuration(
        original,
        HEALTH_SOURCE,
        HEALTH_INITIAL_STATE,
        capabilities,
        [ACTION_PATCH_STATE],
        [
            {
                "event": HEALTH_EVENT,
                "minimumIntervalMilliseconds": minimum_interval_milliseconds,
                "coalescingKey": HEALTH_EVENT,
                "healthRefreshIntervalMilliseconds": HEALTH_REFRESH_INTERVAL_MILLISECONDS,
            },
        ],
    )


def automation_script_state(connection: socket.socket) -> dict[str, Any]:
    configuration = AUTOMATION.require_configuration(
        AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
    )
    script = next(
        item for item in configuration["scripts"] if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
    )
    state = script["state"]
    assert isinstance(state, dict)
    return state


def debug_health_steps(steps: int) -> str:
    assert 0 <= steps <= MAXIMUM_STEPS
    return device_command(
        " ".join(
            (
                "am broadcast",
                "-a",
                DEBUG_HEALTH_CONNECT_ACTION,
                "-n",
                DEBUG_HEALTH_CONNECT_COMPONENT,
                "--el",
                DEBUG_HEALTH_CONNECT_STEPS_EXTRA,
                str(steps),
            ),
        ),
    )


def wait_for_delivery_rejection(record_count: int, reason: str) -> None:
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        records = device_log_records()[record_count:]
        if any(
            record.get("event") == EVENT_DELIVERY_REJECTED
            and record.get("event_type") == HEALTH_EVENT
            and record.get("reason") == reason
            for record in records
        ):
            return
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"Health Connect automation did not reject delivery for {reason}")


def test_automation_health_daily_steps_is_bounded_policy_gated_and_available_through_both_control_planes(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    policy_denied_configuration = health_configuration(original, [], 0)
    record_count_before_denied_delivery = len(device_log_records())
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=policy_denied_configuration,
    )
    time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
    debug_health_steps(HEALTH_STEPS_FIRST)
    wait_for_delivery_rejection(record_count_before_denied_delivery, REASON_CAPABILITY_DENIED)
    assert automation_script_state(websocket_control) == HEALTH_INITIAL_STATE

    available_status = AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_AUTOMATION_STATUS,
    )
    assert HEALTH_CAPABILITY in available_status["capabilities"]
    assert isinstance(available_status["androidAccess"][HEALTH_STATUS_ACCESS_KEY], bool)
    event = next(item for item in available_status["events"] if item["type"] == HEALTH_EVENT)
    assert event["requiredCapability"] == HEALTH_CAPABILITY
    assert event["requiredSubscriptionFields"] == ["healthRefreshIntervalMilliseconds"]
    assert event["optionalSubscriptionFields"] == []
    assert_mcp_automation_status(available_status)

    allowed_configuration = health_configuration(
        original,
        [HEALTH_CAPABILITY],
        HEALTH_RATE_LIMIT_MILLISECONDS,
    )
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=allowed_configuration,
    )
    time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
    debug_health_steps(HEALTH_STEPS_FIRST)
    updated = wait_for_automation_script_state(
        websocket_control,
        AUTOMATION_LIVE_SCRIPT_ID,
        PAYLOAD_STEPS,
        HEALTH_STEPS_FIRST,
    )
    script = next(item for item in updated["scripts"] if item["id"] == AUTOMATION_LIVE_SCRIPT_ID)
    state = script["state"]
    assert isinstance(state[PAYLOAD_DAY_START_MILLISECONDS], int)
    assert state[PAYLOAD_DAY_START_MILLISECONDS] >= 0

    record_count_before_rate_limit = len(device_log_records())
    debug_health_steps(HEALTH_STEPS_SECOND)
    wait_for_delivery_rejection(record_count_before_rate_limit, REASON_RATE_LIMITED)
    assert automation_script_state(websocket_control)[PAYLOAD_STEPS] == HEALTH_STEPS_FIRST
