"""Foreground-only, policy-gated, bounded clipboard automation."""

from . import *


CLIPBOARD_CAPABILITY_CONTENT = "clipboardContent"
CLIPBOARD_CAPABILITY_METADATA = "clipboardMetadata"
CLIPBOARD_EVENT = "clipboardChanged"
CLIPBOARD_MONITOR_ACCESS_KEY = "clipboardMonitorActive"
CLIPBOARD_STATE_HAS_TEXT = "hasText"
CLIPBOARD_STATE_ITEM_COUNT = "itemCount"
CLIPBOARD_STATE_TEXT = "text"
CLIPBOARD_STATE_TEXT_TRUNCATED = "textTruncated"
CLIPBOARD_SOURCE = (
    "function on_event(event)\n"
    "  if event.type ~= \"clipboardChanged\" then return end\n"
    "  return { type = \"patchState\", values = {\n"
    "    hasText = event.payload.hasText,\n"
    "    itemCount = event.payload.itemCount,\n"
    "    text = event.payload.text or \"redacted\",\n"
    "    textTruncated = event.payload.textTruncated == true\n"
    "  } }\n"
    "end"
)
DEBUG_CLIPBOARD_ACTION = "org.fossify.home.dikciz.action.DEBUG_CLIPBOARD_SET_TEXT"
DEBUG_CLIPBOARD_COMPONENT = (
    f"{DIKCIZ_DEBUG_PACKAGE}/org.fossify.home.dikciz.DikcizDebugClipboardReceiver"
)
DEBUG_CLIPBOARD_EXTRA = "clipboardText"
MAXIMUM_CLIPBOARD_TEXT_CHARACTERS = 1_024
MAXIMUM_DEBUG_CLIPBOARD_TEXT_CHARACTERS = MAXIMUM_CLIPBOARD_TEXT_CHARACTERS * 2
METADATA_TEXT = "clipboard-metadata"
LONG_TEXT = "x" * (MAXIMUM_CLIPBOARD_TEXT_CHARACTERS + 1)
MCP_AUTOMATION_STATUS_COMPARISON_REQUEST_ID = 91
MCP_AUTOMATION_STATUS_REQUEST_ID = 90
MCP_MUTATE = False
REASON_CAPABILITY_DENIED = "capability_denied"
AUTOMATION_DELIVERY_REJECTED_EVENT = "automation_delivery_rejected"
DENIED_INITIAL_STATE = {
    CLIPBOARD_STATE_HAS_TEXT: "waiting",
    CLIPBOARD_STATE_ITEM_COUNT: "waiting",
    CLIPBOARD_STATE_TEXT: "waiting",
    CLIPBOARD_STATE_TEXT_TRUNCATED: "waiting",
}


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
        MCP.verify_automation_status(arguments, session_id, MCP_AUTOMATION_STATUS_REQUEST_ID)
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


def clipboard_configuration(
    original: dict[str, Any],
    capabilities: list[str],
) -> dict[str, Any]:
    return automation_configuration(
        original,
        CLIPBOARD_SOURCE,
        DENIED_INITIAL_STATE,
        capabilities,
        ["patchState"],
        [
            {
                "event": CLIPBOARD_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": CLIPBOARD_EVENT,
            },
        ],
    )


def set_clipboard_text(text: str) -> str:
    assert text
    assert len(text) <= MAXIMUM_DEBUG_CLIPBOARD_TEXT_CHARACTERS
    return device_command(
        " ".join(
            (
                "am broadcast",
                "-a",
                DEBUG_CLIPBOARD_ACTION,
                "-n",
                DEBUG_CLIPBOARD_COMPONENT,
                "--es",
                DEBUG_CLIPBOARD_EXTRA,
                text,
            ),
        ),
    )


def current_script_state(websocket_control: socket.socket) -> dict[str, Any]:
    configuration = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    script = next(
        script
        for script in configuration["scripts"]
        if script["id"] == AUTOMATION_LIVE_SCRIPT_ID
    )
    return script["state"]


def wait_for_capability_denied(record_count: int) -> None:
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        records = device_log_records()[record_count:]
        if any(
            record.get("event") == AUTOMATION_DELIVERY_REJECTED_EVENT
            and record.get("event_type") == CLIPBOARD_EVENT
            and record.get("reason") == REASON_CAPABILITY_DENIED
            for record in records
        ):
            return
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("clipboard policy denial was not recorded")


def test_automation_clipboard_is_foreground_only_policy_gated_redacted_and_bounded(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    try:
        metadata_configuration = clipboard_configuration(
            original,
            [CLIPBOARD_CAPABILITY_METADATA],
        )
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=metadata_configuration,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        metadata_status = AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_AUTOMATION_STATUS,
        )
        assert CLIPBOARD_CAPABILITY_METADATA in metadata_status["capabilities"]
        assert CLIPBOARD_CAPABILITY_CONTENT in metadata_status["capabilities"]
        assert metadata_status["androidAccess"][CLIPBOARD_MONITOR_ACCESS_KEY] is True
        clipboard_event = next(
            event
            for event in metadata_status["events"]
            if event["type"] == CLIPBOARD_EVENT
        )
        assert clipboard_event["requiredCapability"] == CLIPBOARD_CAPABILITY_METADATA
        assert clipboard_event["requiredSubscriptionFields"] == []
        assert clipboard_event["optionalSubscriptionFields"] == []
        assert_mcp_automation_status(metadata_status)
        set_clipboard_text(METADATA_TEXT)
        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            CLIPBOARD_STATE_TEXT,
            "redacted",
        )
        assert current_script_state(websocket_control) == {
            CLIPBOARD_STATE_HAS_TEXT: True,
            CLIPBOARD_STATE_ITEM_COUNT: 1,
            CLIPBOARD_STATE_TEXT: "redacted",
            CLIPBOARD_STATE_TEXT_TRUNCATED: False,
        }

        content_configuration = clipboard_configuration(
            original,
            [CLIPBOARD_CAPABILITY_METADATA, CLIPBOARD_CAPABILITY_CONTENT],
        )
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=content_configuration,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        set_clipboard_text(LONG_TEXT)
        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            CLIPBOARD_STATE_TEXT,
            LONG_TEXT[:MAXIMUM_CLIPBOARD_TEXT_CHARACTERS],
        )
        assert current_script_state(websocket_control) == {
            CLIPBOARD_STATE_HAS_TEXT: True,
            CLIPBOARD_STATE_ITEM_COUNT: 1,
            CLIPBOARD_STATE_TEXT: LONG_TEXT[:MAXIMUM_CLIPBOARD_TEXT_CHARACTERS],
            CLIPBOARD_STATE_TEXT_TRUNCATED: True,
        }

        denied_configuration = clipboard_configuration(original, [])
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=denied_configuration,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        record_count = len(device_log_records())
        set_clipboard_text(METADATA_TEXT)
        wait_for_capability_denied(record_count)
        assert current_script_state(websocket_control) == DENIED_INITIAL_STATE
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
