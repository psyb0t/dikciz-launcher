"""Installed HTML widget diagnostics through both local control planes."""

from . import *


HTML_DIAGNOSTICS_EVENT = "html_widget_console"
HTML_WIDGET_LOADING_EVENT = "html_widget_loading"
HTML_DIAGNOSTICS_KIND = "html"
HTML_DIAGNOSTICS_LEVEL = "error"
HTML_DIAGNOSTICS_MESSAGE = "fixture HTML diagnostic"
HTML_DIAGNOSTICS_PAGE_ID = FIXTURE_HOME_PAGE_ID
HTML_DIAGNOSTICS_REASON_SECRET = "token=fixture-secret"
HTML_DIAGNOSTICS_REDACTED_SECRET = "token=[REDACTED]"
HTML_DIAGNOSTICS_TITLE = "HTML diagnostics"
HTML_DIAGNOSTICS_VISIBLE_TEXT = "HTML diagnostics loaded"
HTML_DIAGNOSTICS_WIDGET_ID = "html-diagnostics"
HTML_DIAGNOSTICS_WIDGET_CELL = {"column": 0, "row": 2, "columnSpan": 4, "rowSpan": 1}
HOME_COMPONENT_ID = "DikcizHome"
HTML_DIAGNOSTICS_DOCUMENT = f"<p>{HTML_DIAGNOSTICS_VISIBLE_TEXT}</p>"
HTML_DIAGNOSTICS_CSS = "body{background:#162337;color:#f3f7ff;font:16px system-ui,sans-serif}"
HTML_DIAGNOSTICS_JAVASCRIPT = f"""window.addEventListener('dikciz-ready', () => {{
  console.error('{HTML_DIAGNOSTICS_MESSAGE}, {HTML_DIAGNOSTICS_REASON_SECRET}');
}});"""


def html_diagnostics_widget() -> dict[str, object]:
    return {
        "id": HTML_DIAGNOSTICS_WIDGET_ID,
        "type": "html",
        "title": HTML_DIAGNOSTICS_TITLE,
        "html": HTML_DIAGNOSTICS_DOCUMENT,
        "css": HTML_DIAGNOSTICS_CSS,
        "javascript": HTML_DIAGNOSTICS_JAVASCRIPT,
        "state": {},
        "enabled": True,
        "cell": dict(HTML_DIAGNOSTICS_WIDGET_CELL),
    }


def script_log_record(
    snapshot: dict[str, object],
    *,
    event: str,
    script_id: str,
) -> dict[str, object] | None:
    records = snapshot.get(AUTOMATION.KEY_SCRIPT_LOG_RECORDS)
    if not isinstance(records, list):
        return None
    for record in records:
        if not isinstance(record, dict):
            continue
        if record.get(AUTOMATION.KEY_SCRIPT_LOG_EVENT) != event:
            continue
        if record.get(AUTOMATION.KEY_SCRIPT_LOG_SCRIPT_ID) != script_id:
            continue
        return record
    return None


def wait_for_html_diagnostic_record(connection: socket.socket) -> tuple[dict[str, object], dict[str, object]]:
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    latest_snapshot: dict[str, object] = {}
    while time.monotonic() < deadline:
        latest_snapshot = AUTOMATION.request(connection, AUTOMATION.TYPE_SCRIPT_LOGS)
        record = script_log_record(
            latest_snapshot,
            event=HTML_DIAGNOSTICS_EVENT,
            script_id=HTML_DIAGNOSTICS_WIDGET_ID,
        )
        if record is not None:
            return latest_snapshot, record
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"HTML diagnostic record was not exposed: {latest_snapshot!r}")


def mcp_html_diagnostic_snapshot() -> dict[str, object]:
    arguments = argparse.Namespace(host=AUTOMATION_HOST, port=MCP_PORT, mutate=False)
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        return MCP.require_tool_result(
            MCP.call_tool(arguments, session_id, 1, MCP.TOOL_SCRIPT_LOGS),
        )
    finally:
        MCP.delete_session(arguments, session_id)


def test_html_console_error_is_visible_and_redacted_through_websocket_and_mcp(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = copy.deepcopy(original)
    configuration_page(configured, HTML_DIAGNOSTICS_PAGE_ID)["widgets"].append(
        html_diagnostics_widget(),
    )
    configured["launcher"]["home"]["selectedPageId"] = HTML_DIAGNOSTICS_PAGE_ID
    try:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        )
        wait_for_physical_text_bounds(HTML_DIAGNOSTICS_VISIBLE_TEXT)
        websocket_snapshot, websocket_record = wait_for_html_diagnostic_record(websocket_control)
        assert websocket_record[AUTOMATION.KEY_SCRIPT_LOG_SCRIPT_KIND] == HTML_DIAGNOSTICS_KIND
        assert websocket_record[AUTOMATION.KEY_SCRIPT_LOG_LEVEL] == HTML_DIAGNOSTICS_LEVEL
        reason = websocket_record[AUTOMATION.KEY_SCRIPT_LOG_REASON]
        assert isinstance(reason, str)
        assert HTML_DIAGNOSTICS_MESSAGE in reason
        assert HTML_DIAGNOSTICS_REDACTED_SECRET in reason
        assert HTML_DIAGNOSTICS_REASON_SECRET not in reason
        mcp_snapshot = mcp_html_diagnostic_snapshot()
        mcp_record = script_log_record(
            mcp_snapshot,
            event=HTML_DIAGNOSTICS_EVENT,
            script_id=HTML_DIAGNOSTICS_WIDGET_ID,
        )
        assert mcp_record == websocket_record

        generic_websocket_record = script_log_record(
            websocket_snapshot,
            event=HTML_WIDGET_LOADING_EVENT,
            script_id=HOME_COMPONENT_ID,
        )
        assert generic_websocket_record is not None
        assert generic_websocket_record[AUTOMATION.KEY_SCRIPT_LOG_SCRIPT_KIND] is None
        generic_mcp_record = script_log_record(
            mcp_snapshot,
            event=HTML_WIDGET_LOADING_EVENT,
            script_id=HOME_COMPONENT_ID,
        )
        assert generic_mcp_record == generic_websocket_record
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
