"""Accessibility lifecycle events from the installed Android service."""

from . import *


def lifecycle_script_state(configuration: dict[str, Any]) -> dict[str, Any]:
    script = next(
        item for item in configuration["scripts"] if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
    )
    state = script["state"]
    assert isinstance(state, dict)
    return state


def mcp_configuration() -> dict[str, Any]:
    arguments = argparse.Namespace(
        host=AUTOMATION_HOST,
        port=MCP_PORT,
        expected_control_port=DEVICE_MCP_PORT,
        mutate=False,
    )
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        return MCP.require_configuration(
            MCP.require_tool_result(
                MCP.call_tool(
                    arguments,
                    session_id,
                    ACCESSIBILITY_LIFECYCLE_MCP_CONFIG_REQUEST_ID,
                    MCP.TOOL_CONFIG_GET,
                ),
            ),
        )
    finally:
        MCP.delete_session(arguments, session_id)


def mcp_automation_status() -> dict[str, Any]:
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
                ACCESSIBILITY_LIFECYCLE_MCP_STATUS_REQUEST_ID,
                MCP.TOOL_AUTOMATION_STATUS,
            ),
        )
    finally:
        MCP.delete_session(arguments, session_id)


def receive_lifecycle_event(
    observer: socket.socket,
    expected_state: str,
    expected_enabled: bool,
) -> dict[str, Any]:
    deadline = time.monotonic() + ACCESSIBILITY_SERVICE_READY_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        websocket_event = AUTOMATION.receive_event(
            observer,
            ACCESSIBILITY_LIFECYCLE_WEBSOCKET_EVENT,
        )
        document = websocket_event[AUTOMATION.KEY_FIELDS][AUTOMATION.KEY_EVENT]
        if document.get("type") != ACCESSIBILITY_LIFECYCLE_EVENT:
            continue
        assert set(document) == ACCESSIBILITY_LIFECYCLE_DOCUMENT_KEYS
        assert document["source"] == ACCESSIBILITY_LIFECYCLE_EVENT_SOURCE
        assert isinstance(document["timestampMilliseconds"], int)
        assert document["payload"] == {
            ACCESSIBILITY_LIFECYCLE_ENABLED_KEY: expected_enabled,
            ACCESSIBILITY_LIFECYCLE_PAYLOAD_STATE_KEY: expected_state,
        }
        return document
    raise AssertionError(f"did not receive Accessibility lifecycle state {expected_state!r}")


def test_accessibility_lifecycle_reaches_scripts_and_websocket_while_launcher_is_backgrounded(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = automation_configuration(
        original,
        ACCESSIBILITY_LIFECYCLE_LUA_SOURCE,
        {ACCESSIBILITY_LIFECYCLE_SCRIPT_STATE_KEY: None},
        ACCESSIBILITY_LIFECYCLE_CAPABILITIES,
        ACCESSIBILITY_LIFECYCLE_ACTIONS,
        ACCESSIBILITY_LIFECYCLE_SUBSCRIPTIONS,
    )
    try:
        emulator_dikciz_accessibility_set(False)
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        )
        device_command(f"am force-stop {ACCESSIBILITY_ANDROID_PACKAGE}")
        device_command(f"am start -W -n {ACCESSIBILITY_SETTINGS_COMPONENT}")
        wait_for_foreground_application(ACCESSIBILITY_ANDROID_PACKAGE)
        with connected_websocket_control() as observer:
            emulator_dikciz_accessibility_set(True)
            connected_event = receive_lifecycle_event(
                observer,
                ACCESSIBILITY_LIFECYCLE_CONNECTED,
                True,
            )
            assert connected_event["coalescingKey"].endswith(
                ACCESSIBILITY_LIFECYCLE_CONNECTED,
            )
            wait_for_automation_script_state(
                websocket_control,
                AUTOMATION_LIVE_SCRIPT_ID,
                ACCESSIBILITY_LIFECYCLE_SCRIPT_STATE_KEY,
                ACCESSIBILITY_LIFECYCLE_CONNECTED,
            )

            emulator_dikciz_accessibility_set(False)
            disconnected_event = receive_lifecycle_event(
                observer,
                ACCESSIBILITY_LIFECYCLE_DISCONNECTED,
                False,
            )
            assert disconnected_event["coalescingKey"].endswith(
                ACCESSIBILITY_LIFECYCLE_DISCONNECTED,
            )

        websocket_configuration = wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            ACCESSIBILITY_LIFECYCLE_SCRIPT_STATE_KEY,
            ACCESSIBILITY_LIFECYCLE_DISCONNECTED,
        )
        assert lifecycle_script_state(websocket_configuration) == {
            ACCESSIBILITY_LIFECYCLE_SCRIPT_STATE_KEY: ACCESSIBILITY_LIFECYCLE_DISCONNECTED,
        }
        assert lifecycle_script_state(mcp_configuration()) == lifecycle_script_state(
            websocket_configuration,
        )
        websocket_status = AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_AUTOMATION_STATUS,
        )
        assert websocket_status[AUTOMATION.KEY_ANDROID_ACCESS][ACCESSIBILITY_CAPABILITY] is False
        assert mcp_automation_status() == websocket_status
    finally:
        try:
            emulator_dikciz_accessibility_set(False)
            restore_system_home()
        finally:
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_CONFIG_REPLACE,
                config=original,
            )
