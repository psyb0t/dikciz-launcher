"""Permission-gated contacts-change automation on the isolated Android emulator."""

from . import *


CONTACTS_CAPABILITY = "contactsMetadata"
CONTACTS_EVENT = "contactsChanged"
CONTACTS_PERMISSION = "android.permission.READ_CONTACTS"
CONTACTS_PERMISSION_ACCESS_KEY = "contactsRead"
CONTACTS_SOURCE = (
    'function on_event(event)\n'
    '  if event.type ~= "contactsChanged" then return end\n'
    '  return { type = "patchState", values = { change = event.payload.change } }\n'
    'end'
)
CONTACTS_STATE_INITIAL = {"change": "waiting"}
CONTACTS_STATE_CHANGED = {"change": "changed"}
MCP_AUTOMATION_STATUS_COMPARISON_REQUEST_ID = 31
MCP_AUTOMATION_STATUS_REQUEST_ID = 30
MCP_MUTATE = False


@pytest.fixture
def contacts_cleanup() -> Generator[None, None, None]:
    emulator_contacts_fixture_delete()
    try:
        yield
    finally:
        try:
            emulator_contacts_fixture_delete()
        finally:
            device_command(f"pm revoke {DIKCIZ_DEBUG_PACKAGE} {CONTACTS_PERMISSION}")


@pytest.fixture
def contacts_websocket_control(
    contacts_cleanup: None,
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


def test_automation_contacts_changed_requires_permission_and_uses_the_real_provider(
    contacts_websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(contacts_websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    denied_configuration = automation_configuration(
        original,
        CONTACTS_SOURCE,
        CONTACTS_STATE_INITIAL,
        [],
        ["patchState"],
        [
            {
                "event": CONTACTS_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": "contacts",
            },
        ],
    )
    device_command(f"pm revoke {DIKCIZ_DEBUG_PACKAGE} {CONTACTS_PERMISSION}")
    unavailable_status = AUTOMATION.request(
        contacts_websocket_control,
        AUTOMATION.TYPE_AUTOMATION_STATUS,
    )
    assert unavailable_status["androidAccess"][CONTACTS_PERMISSION_ACCESS_KEY] is False
    AUTOMATION.request(
        contacts_websocket_control,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=denied_configuration,
    )
    time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
    emulator_contacts_fixture_create()
    time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
    assert automation_script_state(contacts_websocket_control) == CONTACTS_STATE_INITIAL
    emulator_contacts_fixture_delete()

    device_command(f"pm grant {DIKCIZ_DEBUG_PACKAGE} {CONTACTS_PERMISSION}")
    available_status = AUTOMATION.request(
        contacts_websocket_control,
        AUTOMATION.TYPE_AUTOMATION_STATUS,
    )
    assert CONTACTS_CAPABILITY in available_status["capabilities"]
    assert available_status["androidAccess"][CONTACTS_PERMISSION_ACCESS_KEY] is True
    event = next(item for item in available_status["events"] if item["type"] == CONTACTS_EVENT)
    assert event["requiredCapability"] == CONTACTS_CAPABILITY
    assert event["requiredSubscriptionFields"] == []
    assert event["optionalSubscriptionFields"] == []
    assert_mcp_automation_status(available_status)

    allowed_configuration = automation_configuration(
        original,
        CONTACTS_SOURCE,
        CONTACTS_STATE_INITIAL,
        [CONTACTS_CAPABILITY],
        ["patchState"],
        [
            {
                "event": CONTACTS_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": "contacts",
            },
        ],
    )
    AUTOMATION.request(
        contacts_websocket_control,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=allowed_configuration,
    )
    time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
    emulator_contacts_fixture_create()
    wait_for_automation_script_state(
        contacts_websocket_control,
        AUTOMATION_LIVE_SCRIPT_ID,
        "change",
        CONTACTS_STATE_CHANGED["change"],
    )
    assert automation_script_state(contacts_websocket_control) == CONTACTS_STATE_CHANGED
