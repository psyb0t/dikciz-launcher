"""Policy-gated outgoing SMS automation on the isolated Android emulator."""

import json
import shlex

from . import *


ACTION_REJECTED_EVENT = "automation_action_rejected"
DEBUG_SMS_ACTION = "org.fossify.home.dikciz.action.DEBUG_SMS_RECEIVED"
DEBUG_SMS_BODY_EXTRA = "smsBody"
DEBUG_SMS_COMPONENT = (
    f"{DIKCIZ_DEBUG_PACKAGE}/org.fossify.home.dikciz.DikcizDebugSmsReceiver"
)
DEBUG_SMS_SENDER_EXTRA = "smsSender"
OUTCOME_EXECUTED = "executed"
REASON_CAPABILITY_DENIED = "capability_denied"
SEND_SMS_ACTION = "sendSms"
SEND_SMS_INITIAL_STATE = {"replied": "waiting"}
SEND_SMS_PERMISSION = "android.permission.SEND_SMS"
SEND_SMS_PERMISSION_ACCESS_KEY = "smsSend"
SEND_SMS_RECIPIENT = "4085550111"
SEND_SMS_REPLY_BODY = "Dikciz automated reply"
SMS_EVENT = "smsReceived"
SMS_FIXTURE_BODY = "Dikciz send fixture"
SMS_FIXTURE_SENDER = "4085555555"
SMS_METADATA_CAPABILITY = "smsMetadata"
SMS_RECEIVE_PERMISSION = "android.permission.RECEIVE_SMS"

# The script is the intended product loop in miniature. An inbound message
# arrives, the script decides a reply, and the reply leaves through sendSms.
SEND_SMS_SOURCE = (
    'function on_event(event)\n'
    '  if event.type ~= "smsReceived" then return end\n'
    f'  return {{ type = "sendSms", recipient = "{SEND_SMS_RECIPIENT}",\n'
    f'    body = "{SEND_SMS_REPLY_BODY}" }}\n'
    'end'
)


@pytest.fixture
def send_sms_permission_cleanup() -> Generator[None, None, None]:
    try:
        yield
    finally:
        device_command(f"pm revoke {DIKCIZ_DEBUG_PACKAGE} {SEND_SMS_PERMISSION}")
        device_command(f"pm revoke {DIKCIZ_DEBUG_PACKAGE} {SMS_RECEIVE_PERMISSION}")
        # Revoking a runtime permission force-stops the app and nothing brings
        # it back on its own. Later teardown, the autouse starter-home restore
        # included, needs Home running before the control plane listens again.
        restore_system_home()
        wait_for_restarted_websocket_control().close()


@pytest.fixture
def send_sms_websocket_control(
    send_sms_permission_cleanup: None,
    websocket_control: socket.socket,
) -> Generator[socket.socket, None, None]:
    # Revoking a runtime permission restarts the app and drops the control
    # plane, so the socket has to close before the revoke runs. Depending on the
    # cleanup fixture first puts it last in teardown.
    yield websocket_control


def send_sms_configuration(
    original: dict[str, Any],
    actions: list[str],
) -> dict[str, Any]:
    return automation_configuration(
        original,
        SEND_SMS_SOURCE,
        SEND_SMS_INITIAL_STATE,
        [SMS_METADATA_CAPABILITY],
        actions,
        [
            {
                "event": SMS_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": SMS_EVENT,
            },
        ],
    )


def deliver_fixture_sms() -> None:
    device_command(
        "am broadcast"
        f" -a {DEBUG_SMS_ACTION}"
        f" -n {DEBUG_SMS_COMPONENT}"
        f" --es {DEBUG_SMS_SENDER_EXTRA} {shlex.quote(SMS_FIXTURE_SENDER)}"
        f" --es {DEBUG_SMS_BODY_EXTRA} {shlex.quote(SMS_FIXTURE_BODY)}"
    )


def wait_for_send_sms_outcome(record_count: int, event: str, outcome: str) -> None:
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        records = device_log_records()[record_count:]
        if any(
            record.get("event") == event
            and record.get("action_type") == SEND_SMS_ACTION
            and record.get("outcome") == outcome
            for record in records
        ):
            return
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"no {SEND_SMS_ACTION} record with outcome {outcome}")


def assert_no_message_content_logged() -> None:
    serialized = json.dumps(device_log_records())
    assert SEND_SMS_RECIPIENT not in serialized
    assert SEND_SMS_REPLY_BODY not in serialized
    assert SMS_FIXTURE_BODY not in serialized


def test_send_sms_action_is_permission_and_policy_gated(
    send_sms_websocket_control: socket.socket,
) -> None:
    websocket_control = send_sms_websocket_control
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    try:
        device_command(f"pm grant {DIKCIZ_DEBUG_PACKAGE} {SMS_RECEIVE_PERMISSION}")
        device_command(f"pm grant {DIKCIZ_DEBUG_PACKAGE} {SEND_SMS_PERMISSION}")

        status = AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_AUTOMATION_STATUS,
        )
        assert status[AUTOMATION.KEY_ANDROID_ACCESS][SEND_SMS_PERMISSION_ACCESS_KEY] is True
        assert SEND_SMS_ACTION in status[AUTOMATION.KEY_ACTIONS]

        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=send_sms_configuration(original, [SEND_SMS_ACTION]),
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        accepted_record_count = len(device_log_records())
        deliver_fixture_sms()
        wait_for_send_sms_outcome(
            accepted_record_count,
            AUTOMATION_ACTION_COMPLETED_EVENT,
            OUTCOME_EXECUTED,
        )

        # The same script, with sendSms withheld from its policy.
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=send_sms_configuration(original, []),
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        denied_record_count = len(device_log_records())
        deliver_fixture_sms()
        wait_for_send_sms_outcome(
            denied_record_count,
            ACTION_REJECTED_EVENT,
            REASON_CAPABILITY_DENIED,
        )

        assert_no_message_content_logged()
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
