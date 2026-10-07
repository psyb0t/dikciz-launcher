"""Policy-gated manual automation triggers through local control planes."""

from . import *


MANUAL_TRIGGER_CAPABILITY = "manualTrigger"
MANUAL_TRIGGER_ERROR_CODE = "validation_failed"
MANUAL_TRIGGER_EVENT = "manual"
MANUAL_TRIGGER_INITIAL_STATE = {"manual": "waiting"}
MANUAL_TRIGGER_MCP_REQUEST_ID = 60
MANUAL_TRIGGER_SOURCE = (
    'function on_event(event)\n'
    '  if event.type ~= "manual" then return end\n'
    '  return { type = "patchState", values = { manual = "triggered" } }\n'
    'end'
)
MANUAL_TRIGGER_STATE_KEY = "manual"
MANUAL_TRIGGER_STATE_VALUE = "triggered"


def manual_trigger_configuration(
    configuration: dict[str, Any],
    capabilities: list[str],
) -> dict[str, Any]:
    return automation_configuration(
        configuration,
        MANUAL_TRIGGER_SOURCE,
        MANUAL_TRIGGER_INITIAL_STATE,
        capabilities,
        ["patchState"],
        [
            {
                "event": MANUAL_TRIGGER_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": MANUAL_TRIGGER_EVENT,
            },
        ],
    )


def trigger_through_mcp() -> dict[str, Any]:
    arguments = argparse.Namespace(
        host=AUTOMATION_HOST,
        port=MCP_PORT,
        expected_control_port=DEVICE_MCP_PORT,
        mutate=False,
    )
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        return MCP.require_tool_result(
            MCP.call_tool(
                arguments,
                session_id,
                MANUAL_TRIGGER_MCP_REQUEST_ID,
                MCP.TOOL_AUTOMATION_TRIGGER,
                {MCP.KEY_SCRIPT_ID: AUTOMATION_LIVE_SCRIPT_ID},
            ),
        )
    finally:
        MCP.delete_session(arguments, session_id)


def test_manual_trigger_is_policy_gated_targeted_and_available_through_both_control_planes(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    denied_configuration = manual_trigger_configuration(original, [])
    allowed_configuration = manual_trigger_configuration(original, [MANUAL_TRIGGER_CAPABILITY])
    try:
        status = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_STATUS)
        assert MANUAL_TRIGGER_CAPABILITY in status["capabilities"]
        event = next(item for item in status["events"] if item["type"] == MANUAL_TRIGGER_EVENT)
        assert event["requiredCapability"] == MANUAL_TRIGGER_CAPABILITY
        assert event["requiredSubscriptionFields"] == []
        assert event["optionalSubscriptionFields"] == []

        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=denied_configuration,
        )
        _, denied_response = AUTOMATION.request_error_with_id(
            websocket_control,
            AUTOMATION.TYPE_AUTOMATION_TRIGGER,
            scriptId=AUTOMATION_LIVE_SCRIPT_ID,
        )
        assert denied_response["code"] == MANUAL_TRIGGER_ERROR_CODE

        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=allowed_configuration,
        )
        websocket_result = AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_AUTOMATION_TRIGGER,
            scriptId=AUTOMATION_LIVE_SCRIPT_ID,
        )
        assert websocket_result == {"scriptId": AUTOMATION_LIVE_SCRIPT_ID}
        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            MANUAL_TRIGGER_STATE_KEY,
            MANUAL_TRIGGER_STATE_VALUE,
        )

        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=allowed_configuration,
        )
        assert trigger_through_mcp() == {MCP.KEY_SCRIPT_ID: AUTOMATION_LIVE_SCRIPT_ID}
        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            MANUAL_TRIGGER_STATE_KEY,
            MANUAL_TRIGGER_STATE_VALUE,
        )

        _, unknown_response = AUTOMATION.request_error_with_id(
            websocket_control,
            AUTOMATION.TYPE_AUTOMATION_TRIGGER,
            scriptId="missing-script",
        )
        assert unknown_response["code"] == MANUAL_TRIGGER_ERROR_CODE
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
