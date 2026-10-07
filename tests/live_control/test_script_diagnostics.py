"""Script failure logging, safe in-app diagnostics, and Android notifications."""

from . import *


@pytest.fixture
def script_diagnostics_notification_permission() -> Generator[None, None, None]:
    device_command(SCRIPT_DIAGNOSTICS_GRANT_NOTIFICATION_COMMAND)
    try:
        yield
    finally:
        device_command(SCRIPT_DIAGNOSTICS_REVOKE_NOTIFICATION_COMMAND)


@pytest.fixture
def script_diagnostics_websocket_control(
    script_diagnostics_notification_permission: None,
    websocket_control: socket.socket,
) -> Generator[socket.socket, None, None]:
    yield websocket_control


def wait_for_script_diagnostic_events(
    record_count: int,
    expected_events: set[str],
    expected_notification_reason: str | None = None,
) -> list[dict[str, Any]]:
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    records: list[dict[str, Any]] = []
    while time.monotonic() < deadline:
        records = device_log_records()[record_count:]
        script_records = [
            record
            for record in records
            if record.get("script_id") == SCRIPT_DIAGNOSTICS_SCRIPT_ID
        ]
        events = {record.get("event") for record in script_records}
        has_notification_reason = expected_notification_reason is None or any(
            record.get("notification_reason") == expected_notification_reason
            for record in script_records
        )
        if expected_events <= events and has_notification_reason:
            return records
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(
        f"script diagnostics did not log {sorted(expected_events)!r}: {records!r}",
    )


def assert_safe_script_log_snapshot(snapshot: dict[str, Any]) -> None:
    assert set(snapshot) == {
        AUTOMATION.KEY_SCRIPT_LOG_RECORDS,
        AUTOMATION.KEY_SCRIPT_LOGS_TRUNCATED,
    }
    records = snapshot[AUTOMATION.KEY_SCRIPT_LOG_RECORDS]
    assert isinstance(records, list)
    assert isinstance(snapshot[AUTOMATION.KEY_SCRIPT_LOGS_TRUNCATED], bool)
    for record in records:
        assert set(record) == AUTOMATION.SCRIPT_LOG_RECORD_KEYS
        assert all(
            isinstance(record[key], str)
            for key in AUTOMATION.SCRIPT_LOG_REQUIRED_STRING_KEYS
        )
        assert all(
            value is None or isinstance(value, str)
            for key, value in record.items()
            if key not in AUTOMATION.SCRIPT_LOG_REQUIRED_STRING_KEYS
        )


def mcp_script_log_snapshot() -> dict[str, Any]:
    arguments = argparse.Namespace(host=AUTOMATION_HOST, port=MCP_PORT, mutate=False)
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        return MCP.require_tool_result(
            MCP.call_tool(arguments, session_id, 1, MCP.TOOL_SCRIPT_LOGS),
        )
    finally:
        MCP.delete_session(arguments, session_id)


def test_script_failures_are_logged_viewable_notified_and_rate_limited(
    script_diagnostics_websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(script_diagnostics_websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = copy.deepcopy(original)
    configured["logging"][SCRIPT_DIAGNOSTICS_LOGGING_NOTIFICATION_ENABLED_KEY] = True
    configured["logging"][SCRIPT_DIAGNOSTICS_LOGGING_NOTIFICATION_INTERVAL_KEY] = (
        SCRIPT_DIAGNOSTICS_NOTIFICATION_MINIMUM_INTERVAL_MILLISECONDS
    )
    configured["scripts"] = [
        {
            "id": SCRIPT_DIAGNOSTICS_SCRIPT_ID,
            "title": SCRIPT_DIAGNOSTICS_SCRIPT_TITLE,
            "enabled": True,
            "apiVersion": LUA_SCRIPT_API_VERSION,
            "source": SCRIPT_DIAGNOSTICS_INITIAL_SOURCE,
            "state": {},
        },
    ]
    AUTOMATION.request(
        script_diagnostics_websocket_control,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=configured,
    )

    first_record_count = len(device_log_records())
    first_failure = copy.deepcopy(configured)
    first_failure["scripts"][0]["source"] = LUA_INSTRUCTION_LIMIT_SOURCE
    AUTOMATION.request(
        script_diagnostics_websocket_control,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=first_failure,
    )
    AUTOMATION.request(
        script_diagnostics_websocket_control,
        AUTOMATION.TYPE_AUTOMATION_TRIGGER,
        scriptId=SCRIPT_DIAGNOSTICS_SCRIPT_ID,
    )
    first_records = wait_for_script_diagnostic_events(
        first_record_count,
        {
            SCRIPT_DIAGNOSTICS_FAILURE_EVENT,
            SCRIPT_DIAGNOSTICS_NOTIFICATION_POSTED_EVENT,
        },
    )
    assert SCRIPT_DIAGNOSTICS_NOTIFICATION_CHANNEL_TITLE in device_command(
        SCRIPT_DIAGNOSTICS_NOTIFICATION_DUMP_COMMAND,
    )
    assert sum(
        record.get("event") == SCRIPT_DIAGNOSTICS_NOTIFICATION_POSTED_EVENT
        for record in first_records
    ) == 1

    AUTOMATION.request(
        script_diagnostics_websocket_control,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=configured,
    )
    second_record_count = len(device_log_records())
    second_failure = copy.deepcopy(configured)
    second_failure["scripts"][0]["source"] = SCRIPT_DIAGNOSTICS_SECOND_FAILURE_SOURCE
    AUTOMATION.request(
        script_diagnostics_websocket_control,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=second_failure,
    )
    AUTOMATION.request(
        script_diagnostics_websocket_control,
        AUTOMATION.TYPE_AUTOMATION_TRIGGER,
        scriptId=SCRIPT_DIAGNOSTICS_SCRIPT_ID,
    )
    wait_for_script_diagnostic_events(
        second_record_count,
        {
            SCRIPT_DIAGNOSTICS_FAILURE_EVENT,
            SCRIPT_DIAGNOSTICS_NOTIFICATION_SKIPPED_EVENT,
        },
        SCRIPT_DIAGNOSTICS_NOTIFICATION_RATE_LIMIT_REASON,
    )

    websocket_snapshot = AUTOMATION.request(
        script_diagnostics_websocket_control,
        AUTOMATION.TYPE_SCRIPT_LOGS,
    )
    assert_safe_script_log_snapshot(websocket_snapshot)
    failure_records = [
        record
        for record in websocket_snapshot[AUTOMATION.KEY_SCRIPT_LOG_RECORDS]
        if record[AUTOMATION.KEY_SCRIPT_LOG_SCRIPT_ID] == SCRIPT_DIAGNOSTICS_SCRIPT_ID
    ]
    assert any(
        record[AUTOMATION.KEY_SCRIPT_LOG_EVENT] == SCRIPT_DIAGNOSTICS_FAILURE_EVENT
        for record in failure_records
    )
    mcp_snapshot = mcp_script_log_snapshot()
    assert_safe_script_log_snapshot(mcp_snapshot)
    assert mcp_snapshot == websocket_snapshot

    open_command_sheet()
    wait_for_snapshot_node(script_diagnostics_websocket_control, COMMAND_SETTINGS_SEMANTIC_ID)
    AUTOMATION.request(
        script_diagnostics_websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=COMMAND_SETTINGS_SEMANTIC_ID,
    )
    wait_for_snapshot_node(script_diagnostics_websocket_control, SCRIPT_DIAGNOSTICS_VIEWER_SEMANTIC_ID)
    AUTOMATION.request(
        script_diagnostics_websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=SCRIPT_DIAGNOSTICS_VIEWER_SEMANTIC_ID,
    )
    wait_for_physical_text_bounds(SCRIPT_DIAGNOSTICS_VIEWER_TITLE)
    assert any(
        SCRIPT_DIAGNOSTICS_FAILURE_EVENT in text
        for text in physical_visible_texts()
    )
    run_device_operation("screenshot")
    shutil.copyfile(
        ARTIFACT_DIRECTORY / "screenshot.png",
        ARTIFACT_DIRECTORY / SCRIPT_DIAGNOSTICS_VIEWER_ARTIFACT_NAME,
    )
