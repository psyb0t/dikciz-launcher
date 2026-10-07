"""Prove widget-local block insertion preserves the live HTML document."""

from . import *


HTML_BLOCK_APPEND_ADDRESS = "1H1V-live-block"
HTML_BLOCK_APPEND_BLOCK_ID = "heading"
HTML_BLOCK_APPEND_BLOCK_TITLE = "Section title"
HTML_BLOCK_APPEND_CELL = {"column": 0, "row": 0, "columnSpan": 4, "rowSpan": 6}
HTML_BLOCK_APPEND_CLOSE_MARKER = "<!-- /dikciz:block block-"
HTML_BLOCK_APPEND_CONFIRM_SEMANTIC_ID = "html-block:confirm"
HTML_BLOCK_APPEND_EDITOR_HTML_SEMANTIC_ID = "widget:live-block:editor:html"
HTML_BLOCK_APPEND_EDITOR_TITLE_SEMANTIC_ID = "widget:live-block:editor:title"
HTML_BLOCK_APPEND_EVENT = "html_block_appended"
HTML_BLOCK_APPEND_HTML = (
    '<main><p id="runtime">Waiting for the live document.</p>'
    '<div id="spacer"></div><p id="append-result">No block has been added.</p></main>'
)
HTML_BLOCK_APPEND_JAVASCRIPT = """(() => {
  const runtime = document.getElementById("runtime");
  const appendResult = document.getElementById("append-result");
  window.addEventListener("dikciz-ready", () => {
    const generation = selfWidget.state.generation || Math.random().toString(36).slice(2);
    runtime.textContent = `Live document ${generation}`;
    if (!selfWidget.state.generation) {
      selfWidget.patchState({ generation }).catch(() => {
        runtime.textContent = "Live document state update failed.";
      });
    }
  });
  window.addEventListener("dikciz-block-appended", event => {
    appendResult.textContent = `Added ${event.detail.instanceId}`;
  });
})();"""
HTML_BLOCK_APPEND_LOAD_EVENT = "html_widget_loading"
HTML_BLOCK_APPEND_MOVE_HANDLE_SEMANTIC_ID = "widget:live-block:move-handle"
HTML_BLOCK_APPEND_OPEN_MARKER = "<!-- dikciz:block heading block-"
HTML_BLOCK_APPEND_PAGE_ID = FIXTURE_HOME_PAGE_ID
HTML_BLOCK_APPEND_STATE_KEY = "generation"
HTML_BLOCK_APPEND_TARGET_WIDGET_ID = "live-block"
HTML_BLOCK_APPEND_TITLE = "Live block append"
HTML_BLOCK_APPEND_WIDGET_ADD_BLOCK_SEMANTIC_ID = "widget:live-block:add-block"
HTML_BLOCK_APPEND_WIDGET_BLOCK_ENTRY_SEMANTIC_ID = "widget:live-block:add-block:heading"
HTML_BLOCK_APPEND_WIDGET_EDIT_SEMANTIC_ID = "widget:live-block:edit"


def append_widget() -> dict[str, object]:
    return {
        "id": HTML_BLOCK_APPEND_TARGET_WIDGET_ID,
        "type": "html",
        "title": HTML_BLOCK_APPEND_TITLE,
        "html": HTML_BLOCK_APPEND_HTML,
        "css": (
            "html,body{margin:0;min-height:100%;background:#101722;color:#edf4ff;"
            "font:16px system-ui,sans-serif}main{padding:18px}#spacer{height:2400px}"
        ),
        "javascript": HTML_BLOCK_APPEND_JAVASCRIPT,
        "state": {},
        "enabled": True,
        "cell": dict(HTML_BLOCK_APPEND_CELL),
    }


def append_configuration(original: dict[str, Any]) -> dict[str, Any]:
    configured = copy.deepcopy(original)
    page = configuration_page(configured, HTML_BLOCK_APPEND_PAGE_ID)
    page["widgets"] = [append_widget()]
    configured["launcher"]["home"]["selectedPageId"] = HTML_BLOCK_APPEND_PAGE_ID
    return configured


def log_count(snapshot: dict[str, object], event: str) -> int:
    records = snapshot.get(AUTOMATION.KEY_SCRIPT_LOG_RECORDS)
    if not isinstance(records, list):
        return 0
    return sum(
        isinstance(record, dict)
        and record.get(AUTOMATION.KEY_SCRIPT_LOG_EVENT) == event
        for record in records
    )


def target_widget_log_count(snapshot: dict[str, object], event: str) -> int:
    records = snapshot.get(AUTOMATION.KEY_SCRIPT_LOG_RECORDS)
    if not isinstance(records, list):
        return 0
    return sum(
        isinstance(record, dict)
        and record.get(AUTOMATION.KEY_SCRIPT_LOG_EVENT) == event
        and record.get(AUTOMATION.KEY_SCRIPT_LOG_SCRIPT_ID) == HTML_BLOCK_APPEND_TARGET_WIDGET_ID
        for record in records
    )


def wait_for_live_generation(connection: socket.socket) -> str:
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    observed_state: object = None
    while time.monotonic() < deadline:
        configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
        )
        observed_state = configuration_widget(
            configuration,
            HTML_BLOCK_APPEND_TARGET_WIDGET_ID,
        ).get("state")
        if isinstance(observed_state, dict):
            generation = observed_state.get(HTML_BLOCK_APPEND_STATE_KEY)
            if isinstance(generation, str) and generation:
                return generation
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"live HTML generation was not persisted: {observed_state!r}")


def open_block_picker(connection: socket.socket) -> None:
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_LONG_PRESS,
        semanticId=HTML_BLOCK_APPEND_MOVE_HANDLE_SEMANTIC_ID,
    )
    wait_for_snapshot_node(connection, HTML_BLOCK_APPEND_WIDGET_ADD_BLOCK_SEMANTIC_ID)
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_TAP,
        semanticId=HTML_BLOCK_APPEND_WIDGET_ADD_BLOCK_SEMANTIC_ID,
    )
    wait_for_snapshot_node(connection, HTML_BLOCK_APPEND_WIDGET_BLOCK_ENTRY_SEMANTIC_ID)
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_TAP,
        semanticId=HTML_BLOCK_APPEND_WIDGET_BLOCK_ENTRY_SEMANTIC_ID,
    )
    wait_for_snapshot_node(connection, HTML_BLOCK_APPEND_CONFIRM_SEMANTIC_ID)


def open_html_source_editor(connection: socket.socket) -> None:
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_LONG_PRESS,
        semanticId=HTML_BLOCK_APPEND_MOVE_HANDLE_SEMANTIC_ID,
    )
    wait_for_snapshot_node(connection, HTML_BLOCK_APPEND_WIDGET_EDIT_SEMANTIC_ID)
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_TAP,
        semanticId=HTML_BLOCK_APPEND_WIDGET_EDIT_SEMANTIC_ID,
    )
    wait_for_snapshot_node(connection, HTML_BLOCK_APPEND_EDITOR_HTML_SEMANTIC_ID)


def test_html_block_append_stays_in_live_widget_and_scrolls_to_inserted_block(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    try:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=append_configuration(original),
        )
        generation = wait_for_live_generation(websocket_control)
        before = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SCRIPT_LOGS)
        load_count = target_widget_log_count(before, HTML_BLOCK_APPEND_LOAD_EVENT)
        append_count = log_count(before, HTML_BLOCK_APPEND_EVENT)

        open_block_picker(websocket_control)
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_TAP,
            semanticId=HTML_BLOCK_APPEND_CONFIRM_SEMANTIC_ID,
        )
        wait_for_physical_text_bounds(HTML_BLOCK_APPEND_BLOCK_TITLE)

        saved = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        saved_widget = configuration_widget(saved, HTML_BLOCK_APPEND_TARGET_WIDGET_ID)
        saved_html = saved_widget["html"]
        assert isinstance(saved_html, str)
        assert HTML_BLOCK_APPEND_OPEN_MARKER in saved_html
        assert HTML_BLOCK_APPEND_CLOSE_MARKER in saved_html
        assert saved_widget["state"] == {HTML_BLOCK_APPEND_STATE_KEY: generation}
        after = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SCRIPT_LOGS)
        assert log_count(after, HTML_BLOCK_APPEND_EVENT) == append_count + 1
        assert target_widget_log_count(after, HTML_BLOCK_APPEND_LOAD_EVENT) == load_count

        open_html_source_editor(websocket_control)
        editor_snapshot = wait_for_snapshot_node(
            websocket_control,
            HTML_BLOCK_APPEND_EDITOR_HTML_SEMANTIC_ID,
        )
        source_input = AUTOMATION.require_node(
            editor_snapshot,
            HTML_BLOCK_APPEND_EDITOR_HTML_SEMANTIC_ID,
        )
        source_text = source_input[AUTOMATION.KEY_TEXT]
        assert isinstance(source_text, str)
        assert HTML_BLOCK_APPEND_OPEN_MARKER in source_text
        assert "<h2>Section title</h2>" in source_text
        assert HTML_BLOCK_APPEND_CLOSE_MARKER in source_text
        wait_for_snapshot_node(websocket_control, HTML_BLOCK_APPEND_EDITOR_TITLE_SEMANTIC_ID)
        run_device_operation("screenshot")
        run_device_operation("uiautomator-dump")
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
