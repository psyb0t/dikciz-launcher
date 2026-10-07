"""Installed HTML bridge backpressure through its visible native widget."""

from . import *


HTML_QUEUE_BUSY_CODE = "busy"
HTML_QUEUE_EVENT_REJECTED = "web_command_rejected"
HTML_QUEUE_KIND = "html"
HTML_QUEUE_PAGE_ID = FIXTURE_HOME_PAGE_ID
HTML_QUEUE_REQUEST_COUNT = 64
HTML_QUEUE_REQUEST_TIMEOUT_MILLISECONDS = 5_000
HTML_QUEUE_STATUS_ELEMENT_ID = "queue-status"
HTML_QUEUE_STATUS_TEXT = "Queue limit reached"
HTML_QUEUE_TITLE = "Bridge queue pressure"
HTML_QUEUE_UNKNOWN_SEMANTIC_ID = "queue:missing"
HTML_QUEUE_WIDGET_ID = "html-queue-pressure"
HTML_QUEUE_WIDGET_CELL = {"column": 0, "row": 0, "columnSpan": 4, "rowSpan": 1}
HTML_QUEUE_WIDGET_CSS = (
    "body{margin:0;background:#162337;color:#f3f7ff;"
    "font:16px system-ui,sans-serif}p{margin:16px}"
)
HTML_QUEUE_DOCUMENT = f'<p id="{HTML_QUEUE_STATUS_ELEMENT_ID}">Queue waiting</p>'
HTML_QUEUE_JAVASCRIPT = f"""window.addEventListener("message", event => {{
  if (event.data !== "dikciz-slots" || event.ports.length !== 1) return;
  const channel = event.ports[0];
  const status = document.getElementById("{HTML_QUEUE_STATUS_ELEMENT_ID}");
  channel.addEventListener("message", reply => {{
    const response = JSON.parse(reply.data);
    if (response.code === "{HTML_QUEUE_BUSY_CODE}") {{
      status.textContent = "{HTML_QUEUE_STATUS_TEXT}";
    }}
  }});
  channel.start();
  for (let index = 0; index < {HTML_QUEUE_REQUEST_COUNT}; index += 1) {{
    channel.postMessage(JSON.stringify({{
      type: "waitFor",
      requestId: crypto.randomUUID(),
      semanticId: "{HTML_QUEUE_UNKNOWN_SEMANTIC_ID}",
      timeoutMilliseconds: {HTML_QUEUE_REQUEST_TIMEOUT_MILLISECONDS},
    }}));
  }}
}});"""


def html_queue_widget() -> dict[str, object]:
    return {
        "id": HTML_QUEUE_WIDGET_ID,
        "type": "html",
        "title": HTML_QUEUE_TITLE,
        "html": HTML_QUEUE_DOCUMENT,
        "css": HTML_QUEUE_WIDGET_CSS,
        "javascript": HTML_QUEUE_JAVASCRIPT,
        "state": {},
        "enabled": True,
        "cell": dict(HTML_QUEUE_WIDGET_CELL),
    }


def html_queue_configuration(original: dict[str, Any]) -> dict[str, Any]:
    configured = copy.deepcopy(original)
    page = configuration_page(configured, HTML_QUEUE_PAGE_ID)
    page["widgets"] = [html_queue_widget()]
    configured["launcher"]["home"]["selectedPageId"] = HTML_QUEUE_PAGE_ID
    return configured


def queue_rejection_record(snapshot: dict[str, object]) -> dict[str, object] | None:
    records = snapshot.get(AUTOMATION.KEY_SCRIPT_LOG_RECORDS)
    if not isinstance(records, list):
        return None
    for record in records:
        if not isinstance(record, dict):
            continue
        if record.get(AUTOMATION.KEY_SCRIPT_LOG_EVENT) != HTML_QUEUE_EVENT_REJECTED:
            continue
        if record.get(AUTOMATION.KEY_SCRIPT_LOG_SCRIPT_ID) != HTML_QUEUE_WIDGET_ID:
            continue
        if record.get(AUTOMATION.KEY_SCRIPT_LOG_REASON) != HTML_QUEUE_BUSY_CODE:
            continue
        return record
    return None


def wait_for_queue_rejection(
    connection: socket.socket,
) -> tuple[dict[str, object], dict[str, object]]:
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    latest_snapshot: dict[str, object] = {}
    while time.monotonic() < deadline:
        latest_snapshot = AUTOMATION.request(connection, AUTOMATION.TYPE_SCRIPT_LOGS)
        record = queue_rejection_record(latest_snapshot)
        if record is not None:
            return latest_snapshot, record
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"HTML bridge queue rejection was not logged: {latest_snapshot!r}")


def mcp_queue_logs() -> dict[str, object]:
    arguments = argparse.Namespace(host=AUTOMATION_HOST, port=MCP_PORT, mutate=False)
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        return MCP.require_tool_result(
            MCP.call_tool(arguments, session_id, 1, MCP.TOOL_SCRIPT_LOGS),
        )
    finally:
        MCP.delete_session(arguments, session_id)


def test_html_widget_queue_backpressure_is_visible_and_logged(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = html_queue_configuration(original)
    try:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        )
        installed = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        wait_for_physical_text_bounds(HTML_QUEUE_STATUS_TEXT)
        _, websocket_record = wait_for_queue_rejection(websocket_control)
        assert websocket_record[AUTOMATION.KEY_SCRIPT_LOG_SCRIPT_KIND] == HTML_QUEUE_KIND
        assert AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        ) == installed

        mcp_record = queue_rejection_record(mcp_queue_logs())
        assert mcp_record == websocket_record
        run_device_operation("screenshot")
        assert (ARTIFACT_DIRECTORY / "screenshot.png").stat().st_size > 0
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
