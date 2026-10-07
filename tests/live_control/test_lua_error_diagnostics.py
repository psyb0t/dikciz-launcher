"""Installed Lua failure diagnostics across each public log reader."""

import argparse
import re

from . import *
from .device import automation_configuration


def assert_script_log_snapshot(snapshot: dict[str, Any]) -> list[dict[str, Any]]:
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
    return records


def mcp_script_logs() -> dict[str, Any]:
    arguments = argparse.Namespace(host=AUTOMATION_HOST, port=MCP_PORT, mutate=False)
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        return MCP.require_tool_result(
            MCP.call_tool(arguments, session_id, 1, MCP.TOOL_SCRIPT_LOGS),
        )
    finally:
        MCP.delete_session(arguments, session_id)


def log_identity(record: dict[str, Any]) -> tuple[tuple[str, str | None], ...]:
    return tuple(sorted((key, value) for key, value in record.items()))


def wait_for_lua_failure(
    connection: socket.socket,
    previous_records: list[dict[str, Any]],
) -> tuple[dict[str, Any], dict[str, Any]]:
    previous_identities = {log_identity(record) for record in previous_records}
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        snapshot = AUTOMATION.request(connection, AUTOMATION.TYPE_SCRIPT_LOGS)
        records = assert_script_log_snapshot(snapshot)
        for record in records:
            if log_identity(record) in previous_identities:
                continue
            if record[AUTOMATION.KEY_SCRIPT_LOG_SCRIPT_ID] != AUTOMATION_LIVE_SCRIPT_ID:
                continue
            if record[AUTOMATION.KEY_SCRIPT_LOG_EVENT] != LUA_ERROR_DIAGNOSTIC_EVENT:
                continue
            if record[AUTOMATION.KEY_SCRIPT_LOG_REASON] != LUA_ERROR_DIAGNOSTIC_REASON:
                continue
            diagnostic = record[AUTOMATION.KEY_SCRIPT_LOG_DIAGNOSTIC]
            if isinstance(diagnostic, str):
                return record, snapshot
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("Lua failure did not publish a new diagnostic record")


def assert_source_location(diagnostic: str) -> None:
    assert re.search(
        rf"{re.escape(LUA_ERROR_DIAGNOSTIC_LOCATION_PREFIX)}\d+",
        diagnostic,
    )


def install_failing_script(
    connection: socket.socket,
    original: dict[str, Any],
    source: str,
) -> None:
    configured = automation_configuration(
        original,
        source,
        {},
        LUA_ERROR_DIAGNOSTIC_CAPABILITIES,
        LUA_ERROR_DIAGNOSTIC_ACTIONS,
        LUA_ERROR_DIAGNOSTIC_SUBSCRIPTIONS,
    )
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=configured,
    )
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_AUTOMATION_TRIGGER,
        scriptId=AUTOMATION_LIVE_SCRIPT_ID,
    )


def scroll_viewer_to_lua_diagnostic() -> str:
    visible_text = ""
    for _ in range(LUA_ERROR_DIAGNOSTIC_VIEWER_SCROLL_ATTEMPTS):
        visible_text = "\n".join(physical_visible_texts())
        if LUA_ERROR_DIAGNOSTIC_REDACTED_VALUE in visible_text:
            return visible_text
        scroll_bounds = physical_bounds_for(
            lambda node: node.get("class")
            == LUA_ERROR_DIAGNOSTIC_VIEWER_SCROLL_VIEW_CLASS_NAME,
        )
        x, _ = center(scroll_bounds)
        direct_swipe(
            (x, scroll_bounds["bottom"] - DIRECT_BOTTOM_EDGE_INSET_PIXELS),
            (x, scroll_bounds["top"] + DIRECT_BOTTOM_EDGE_INSET_PIXELS),
        )
    raise AssertionError("native script-log viewer did not expose the Lua diagnostic")


def test_lua_failure_diagnostics_keep_source_lines_redacted_and_visible(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    initial_records = assert_script_log_snapshot(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SCRIPT_LOGS),
    )
    install_failing_script(
        websocket_control,
        original,
        LUA_ERROR_DIAGNOSTIC_RUNTIME_SOURCE,
    )
    runtime_record, runtime_snapshot = wait_for_lua_failure(
        websocket_control,
        initial_records,
    )
    runtime_diagnostic = runtime_record[AUTOMATION.KEY_SCRIPT_LOG_DIAGNOSTIC]
    assert isinstance(runtime_diagnostic, str)
    assert_source_location(runtime_diagnostic)
    assert LUA_ERROR_DIAGNOSTIC_REDACTED_VALUE in runtime_diagnostic
    assert LUA_ERROR_DIAGNOSTIC_RUNTIME_SECRET not in runtime_diagnostic

    install_failing_script(
        websocket_control,
        original,
        LUA_ERROR_DIAGNOSTIC_SYNTAX_SOURCE,
    )
    syntax_record, syntax_snapshot = wait_for_lua_failure(
        websocket_control,
        assert_script_log_snapshot(runtime_snapshot),
    )
    syntax_diagnostic = syntax_record[AUTOMATION.KEY_SCRIPT_LOG_DIAGNOSTIC]
    assert isinstance(syntax_diagnostic, str)
    assert_source_location(syntax_diagnostic)
    assert syntax_diagnostic != runtime_diagnostic

    mcp_records = assert_script_log_snapshot(mcp_script_logs())
    assert log_identity(runtime_record) in {log_identity(record) for record in mcp_records}
    assert log_identity(syntax_record) in {log_identity(record) for record in mcp_records}
    open_command_sheet()
    wait_for_snapshot_node(websocket_control, COMMAND_SETTINGS_SEMANTIC_ID)
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=COMMAND_SETTINGS_SEMANTIC_ID,
    )
    wait_for_snapshot_node(websocket_control, SCRIPT_DIAGNOSTICS_VIEWER_SEMANTIC_ID)
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=SCRIPT_DIAGNOSTICS_VIEWER_SEMANTIC_ID,
    )
    wait_for_physical_text_bounds(SCRIPT_DIAGNOSTICS_VIEWER_TITLE)
    visible_text = scroll_viewer_to_lua_diagnostic()
    assert LUA_ERROR_DIAGNOSTIC_RUNTIME_SECRET not in visible_text
    assert LUA_ERROR_DIAGNOSTIC_LOCATION_PREFIX in visible_text
    run_device_operation("screenshot")
    shutil.copyfile(
        ARTIFACT_DIRECTORY / "screenshot.png",
        ARTIFACT_DIRECTORY / LUA_ERROR_DIAGNOSTIC_VIEWER_ARTIFACT_NAME,
    )
