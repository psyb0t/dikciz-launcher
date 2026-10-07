"""Permission-gated Calendar event automation on the isolated Android emulator."""

from . import *


CALENDAR_CONTENT_CAPABILITY = "calendarEventsContent"
CALENDAR_DESCRIPTION_KEY = "description"
CALENDAR_EVENT = "calendarEvent"
CALENDAR_EVENT_ID_KEY = "eventId"
CALENDAR_METADATA_CAPABILITY = "calendarEventsMetadata"
CALENDAR_PERMISSION = "android.permission.READ_CALENDAR"
CALENDAR_PERMISSION_ACCESS_KEY = "calendarRead"
CALENDAR_RECURRING_KEY = "recurring"
CALENDAR_REDACTED_VALUE = "redacted"
CALENDAR_STATE_INITIAL = {
    CALENDAR_DESCRIPTION_KEY: "waiting",
    "location": "waiting",
    "title": "waiting",
}
CALENDAR_UPDATED_DESCRIPTION = "Dikciz calendar fixture updated description"
CALENDAR_UPDATED_LOCATION = "Dikciz calendar fixture updated location"
CALENDAR_UPDATED_TITLE = "Dikciz calendar fixture updated title"
CALENDAR_SOURCE = (
    'function on_event(event)\n'
    '  if event.type ~= "calendarEvent" then return end\n'
    '  return { type = "patchState", values = {\n'
    '    eventId = event.payload.eventId,\n'
    '    calendarId = event.payload.calendarId,\n'
    '    allDay = event.payload.allDay,\n'
    '    recurring = event.payload.recurring,\n'
    '    description = event.payload.description or "redacted",\n'
    '    location = event.payload.location or "redacted",\n'
    '    title = event.payload.title or "redacted"\n'
    '  } }\n'
    'end'
)
MCP_AUTOMATION_STATUS_COMPARISON_REQUEST_ID = 21
MCP_AUTOMATION_STATUS_REQUEST_ID = 20
MCP_MUTATE = False


@pytest.fixture
def calendar_permission_cleanup() -> Generator[None, None, None]:
    try:
        yield
    finally:
        try:
            emulator_calendar_fixture_delete()
        finally:
            device_command(f"pm revoke {DIKCIZ_DEBUG_PACKAGE} {CALENDAR_PERMISSION}")


@pytest.fixture
def calendar_websocket_control(
    calendar_permission_cleanup: None,
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


def automation_script_state(connection: socket.socket) -> dict[str, Any]:
    configuration = AUTOMATION.require_configuration(
        AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
    )
    script = next(item for item in configuration["scripts"] if item["id"] == AUTOMATION_LIVE_SCRIPT_ID)
    state = script["state"]
    assert isinstance(state, dict)
    return state


def assert_metadata_state(state: dict[str, Any]) -> None:
    assert isinstance(state[CALENDAR_EVENT_ID_KEY], int)
    assert isinstance(state["calendarId"], int)
    assert state["allDay"] is False
    assert state[CALENDAR_RECURRING_KEY] is False
    assert state[CALENDAR_DESCRIPTION_KEY] == CALENDAR_REDACTED_VALUE
    assert state["location"] == CALENDAR_REDACTED_VALUE
    assert state["title"] == CALENDAR_REDACTED_VALUE


def test_automation_calendar_events_require_permission_and_redact_content_by_policy(
    calendar_websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(calendar_websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    metadata_configuration = automation_configuration(
        original,
        CALENDAR_SOURCE,
        CALENDAR_STATE_INITIAL,
        [CALENDAR_METADATA_CAPABILITY],
        ["patchState"],
        [
            {
                "event": CALENDAR_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": CALENDAR_EVENT,
            },
        ],
    )
    device_command(f"pm revoke {DIKCIZ_DEBUG_PACKAGE} {CALENDAR_PERMISSION}")
    unavailable_status = AUTOMATION.request(
        calendar_websocket_control,
        AUTOMATION.TYPE_AUTOMATION_STATUS,
    )
    assert unavailable_status["androidAccess"][CALENDAR_PERMISSION_ACCESS_KEY] is False
    AUTOMATION.request(
        calendar_websocket_control,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=metadata_configuration,
    )
    time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
    emulator_calendar_fixture_create()
    time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
    assert automation_script_state(calendar_websocket_control) == CALENDAR_STATE_INITIAL
    emulator_calendar_fixture_delete()

    device_command(f"pm grant {DIKCIZ_DEBUG_PACKAGE} {CALENDAR_PERMISSION}")
    available_status = AUTOMATION.request(
        calendar_websocket_control,
        AUTOMATION.TYPE_AUTOMATION_STATUS,
    )
    assert CALENDAR_CONTENT_CAPABILITY in available_status["capabilities"]
    assert CALENDAR_METADATA_CAPABILITY in available_status["capabilities"]
    assert available_status["androidAccess"][CALENDAR_PERMISSION_ACCESS_KEY] is True
    event = next(item for item in available_status["events"] if item["type"] == CALENDAR_EVENT)
    assert event["requiredCapability"] == CALENDAR_METADATA_CAPABILITY
    assert event["requiredSubscriptionFields"] == []
    assert event["optionalSubscriptionFields"] == []
    assert_mcp_automation_status(available_status)

    AUTOMATION.request(
        calendar_websocket_control,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=metadata_configuration,
    )
    time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
    emulator_calendar_fixture_create()
    wait_for_automation_script_state(
        calendar_websocket_control,
        AUTOMATION_LIVE_SCRIPT_ID,
        "title",
        CALENDAR_REDACTED_VALUE,
    )
    assert_metadata_state(automation_script_state(calendar_websocket_control))

    content_configuration = automation_configuration(
        original,
        CALENDAR_SOURCE,
        CALENDAR_STATE_INITIAL,
        [CALENDAR_METADATA_CAPABILITY, CALENDAR_CONTENT_CAPABILITY],
        ["patchState"],
        [
            {
                "event": CALENDAR_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": CALENDAR_EVENT,
            },
        ],
    )
    AUTOMATION.request(
        calendar_websocket_control,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=content_configuration,
    )
    time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
    emulator_calendar_fixture_update()
    wait_for_automation_script_state(
        calendar_websocket_control,
        AUTOMATION_LIVE_SCRIPT_ID,
        "title",
        CALENDAR_UPDATED_TITLE,
    )
    content_state = automation_script_state(calendar_websocket_control)
    assert content_state[CALENDAR_DESCRIPTION_KEY] == CALENDAR_UPDATED_DESCRIPTION
    assert content_state["location"] == CALENDAR_UPDATED_LOCATION
