"""Permission-gated incoming SMS automation on the isolated Android emulator."""

import shlex
import shutil

from . import *


SMS_CONTENT_CAPABILITY = "smsContent"
SMS_EVENT = "smsReceived"
SMS_FIXTURE_SENDER = "4085555555"
SMS_METADATA_CAPABILITY = "smsMetadata"
SMS_PERMISSION = "android.permission.RECEIVE_SMS"
SMS_PERMISSION_ACCESS_KEY = "smsReceive"
SMS_RATE_LIMIT_MILLISECONDS = 60_000
SMS_RESTRICTED_PERMISSION_ARTIFACT_NAME = "sms-restricted-permission-guidance.png"
SMS_RESTRICTED_PERMISSION_MESSAGE = (
    "Incoming SMS is hard restricted. Your installer must allowlist it before Android can grant it."
)
AUTOMATION_SETTINGS_SEMANTIC_ID = "settings:automation"
SMS_STATE_INITIAL = {
    "body": "waiting",
    "content": "waiting",
    "messageCount": "waiting",
    "sender": "waiting",
}
SMS_METADATA_BODY = "Dikciz metadata fixture"
SMS_CONTENT_BODY = "Dikciz content fixture Ω"
SMS_RATE_FIRST_BODY = "Dikciz rate first"
SMS_RATE_SECOND_BODY = "Dikciz rate second"
DEBUG_SMS_ACTION = "org.fossify.home.dikciz.action.DEBUG_SMS_RECEIVED"
DEBUG_SMS_AM_COMMAND = "am broadcast"
DEBUG_SMS_BODY_EXTRA = "smsBody"
DEBUG_SMS_COMPONENT = (
    f"{DIKCIZ_DEBUG_PACKAGE}/org.fossify.home.dikciz.DikcizDebugSmsReceiver"
)
DEBUG_SMS_SENDER_EXTRA = "smsSender"
SMS_METADATA_SOURCE = (
    'function on_event(event)\n'
    '  if event.type ~= "smsReceived" then return end\n'
    '  return { type = "patchState", values = {\n'
    '    content = event.payload.messages and "visible" or "redacted",\n'
    '    messageCount = event.payload.messageCount\n'
    '  } }\n'
    'end'
)
SMS_CONTENT_SOURCE = (
    'function on_event(event)\n'
    '  if event.type ~= "smsReceived" then return end\n'
    '  local message = event.payload.messages[1]\n'
    '  return { type = "patchState", values = {\n'
    '    body = message.body,\n'
    '    content = "visible",\n'
    '    messageCount = event.payload.messageCount,\n'
    '    sender = message.sender\n'
    '  } }\n'
    'end'
)
MCP_AUTOMATION_STATUS_COMPARISON_REQUEST_ID = 41
MCP_AUTOMATION_STATUS_REQUEST_ID = 40
MCP_MUTATE = False


@pytest.fixture
def sms_permission_cleanup() -> Generator[None, None, None]:
    try:
        yield
    finally:
        device_command(f"pm revoke {DIKCIZ_DEBUG_PACKAGE} {SMS_PERMISSION}")


@pytest.fixture
def sms_websocket_control(
    sms_permission_cleanup: None,
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


def sms_configuration(
    original: dict[str, Any],
    source: str,
    capabilities: list[str],
    coalescing_key: str,
    minimum_interval_milliseconds: int = 0,
) -> dict[str, Any]:
    return automation_configuration(
        original,
        source,
        SMS_STATE_INITIAL,
        capabilities,
        ["patchState"],
        [
            {
                "event": SMS_EVENT,
                "minimumIntervalMilliseconds": minimum_interval_milliseconds,
                "coalescingKey": coalescing_key,
            },
        ],
    )


def replace_configuration(connection: socket.socket, configuration: dict[str, Any]) -> None:
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=configuration,
    )
    time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)


def debug_sms_send(sender: str, body: str) -> str:
    assert sender.isdecimal()
    assert 1 <= len(sender) <= 32
    assert body
    assert len(body) <= 512
    assert "\n" not in body
    assert "\r" not in body
    return device_command(
        " ".join(
            (
                DEBUG_SMS_AM_COMMAND,
                "-a",
                DEBUG_SMS_ACTION,
                "-n",
                DEBUG_SMS_COMPONENT,
                "--es",
                DEBUG_SMS_SENDER_EXTRA,
                sender,
                "--es",
                DEBUG_SMS_BODY_EXTRA,
                shlex.quote(body),
            ),
        ),
    )


def test_automation_sms_reception_requires_permission_and_redacts_content_by_policy(
    sms_websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(sms_websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    metadata_configuration = sms_configuration(
        original,
        SMS_METADATA_SOURCE,
        [SMS_METADATA_CAPABILITY],
        "sms-metadata",
    )
    device_command(f"pm revoke {DIKCIZ_DEBUG_PACKAGE} {SMS_PERMISSION}")
    unavailable_status = AUTOMATION.request(
        sms_websocket_control,
        AUTOMATION.TYPE_AUTOMATION_STATUS,
    )
    assert unavailable_status["androidAccess"][SMS_PERMISSION_ACCESS_KEY] is False
    replace_configuration(sms_websocket_control, metadata_configuration)
    open_command_sheet()
    wait_for_snapshot_node(sms_websocket_control, COMMAND_SETTINGS_SEMANTIC_ID)
    AUTOMATION.request(
        sms_websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=COMMAND_SETTINGS_SEMANTIC_ID,
    )
    wait_for_snapshot_node(sms_websocket_control, AUTOMATION_SETTINGS_SEMANTIC_ID)
    AUTOMATION.request(
        sms_websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=AUTOMATION_SETTINGS_SEMANTIC_ID,
    )
    wait_for_physical_text_bounds(SMS_RESTRICTED_PERMISSION_MESSAGE)
    run_device_operation("screenshot")
    shutil.copyfile(
        ARTIFACT_DIRECTORY / "screenshot.png",
        ARTIFACT_DIRECTORY / SMS_RESTRICTED_PERMISSION_ARTIFACT_NAME,
    )
    assert (ARTIFACT_DIRECTORY / SMS_RESTRICTED_PERMISSION_ARTIFACT_NAME).stat().st_size > 0

    debug_sms_send(SMS_FIXTURE_SENDER, SMS_METADATA_BODY)
    time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
    assert automation_script_state(sms_websocket_control) == SMS_STATE_INITIAL

    device_command(f"pm grant {DIKCIZ_DEBUG_PACKAGE} {SMS_PERMISSION}")
    available_status = AUTOMATION.request(
        sms_websocket_control,
        AUTOMATION.TYPE_AUTOMATION_STATUS,
    )
    assert SMS_CONTENT_CAPABILITY in available_status["capabilities"]
    assert SMS_METADATA_CAPABILITY in available_status["capabilities"]
    assert available_status["androidAccess"][SMS_PERMISSION_ACCESS_KEY] is True
    event = next(item for item in available_status["events"] if item["type"] == SMS_EVENT)
    assert event["requiredCapability"] == SMS_METADATA_CAPABILITY
    assert event["requiredSubscriptionFields"] == []
    assert event["optionalSubscriptionFields"] == []
    assert_mcp_automation_status(available_status)

    replace_configuration(sms_websocket_control, metadata_configuration)
    debug_sms_send(SMS_FIXTURE_SENDER, SMS_METADATA_BODY)
    wait_for_automation_script_state(
        sms_websocket_control,
        AUTOMATION_LIVE_SCRIPT_ID,
        "content",
        "redacted",
    )
    metadata_state = automation_script_state(sms_websocket_control)
    assert metadata_state["messageCount"] == 1
    assert metadata_state["sender"] == SMS_STATE_INITIAL["sender"]
    assert metadata_state["body"] == SMS_STATE_INITIAL["body"]

    content_configuration = sms_configuration(
        original,
        SMS_CONTENT_SOURCE,
        [SMS_METADATA_CAPABILITY, SMS_CONTENT_CAPABILITY],
        "sms-content",
    )
    replace_configuration(sms_websocket_control, content_configuration)
    debug_sms_send(SMS_FIXTURE_SENDER, SMS_CONTENT_BODY)
    wait_for_automation_script_state(
        sms_websocket_control,
        AUTOMATION_LIVE_SCRIPT_ID,
        "body",
        SMS_CONTENT_BODY,
    )
    content_state = automation_script_state(sms_websocket_control)
    assert content_state["messageCount"] == 1
    assert content_state["content"] == "visible"
    assert content_state["sender"] == SMS_FIXTURE_SENDER

    rate_limited_configuration = sms_configuration(
        original,
        SMS_CONTENT_SOURCE,
        [SMS_METADATA_CAPABILITY, SMS_CONTENT_CAPABILITY],
        "sms-rate",
        SMS_RATE_LIMIT_MILLISECONDS,
    )
    replace_configuration(sms_websocket_control, rate_limited_configuration)
    record_count_before_first_delivery = len(device_log_records())
    debug_sms_send(SMS_FIXTURE_SENDER, SMS_RATE_FIRST_BODY)
    wait_for_automation_script_state(
        sms_websocket_control,
        AUTOMATION_LIVE_SCRIPT_ID,
        "body",
        SMS_RATE_FIRST_BODY,
    )
    debug_sms_send(SMS_FIXTURE_SENDER, SMS_RATE_SECOND_BODY)
    time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
    assert automation_script_state(sms_websocket_control)["body"] == SMS_RATE_FIRST_BODY
    new_records = device_log_records()[record_count_before_first_delivery:]
    assert any(
        record.get("event") == "automation_delivery_rejected"
        and record.get("event_type") == SMS_EVENT
        and record.get("reason") == "rate_limited"
        for record in new_records
    )
