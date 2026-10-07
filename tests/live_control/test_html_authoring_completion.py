"""Exercise HTML editing through renderer recovery and direct state delivery."""

from . import *


HTML_AUTHORING_ADDRESS = "1H1V-authoring-surface"
HTML_AUTHORING_CSS = (
    "body{margin:0;background:#162337;color:#f3f7ff;font:16px system-ui,sans-serif}"
    "p{margin:12px}"
)
HTML_AUTHORING_DIRECT_MESSAGE = "direct state applied"
HTML_AUTHORING_EDITOR_HTML_SEMANTIC_ID = "widget:authoring-surface:editor:html"
HTML_AUTHORING_EDITOR_SAVE_SEMANTIC_ID = "widget:authoring-surface:editor:save"
HTML_AUTHORING_EDITOR_TITLE_SEMANTIC_ID = "widget:authoring-surface:editor:title"
HTML_AUTHORING_EDITED_DOCUMENT_TEXT = "Edited document after renderer recovery"
HTML_AUTHORING_EDITED_HTML = f"<p>{HTML_AUTHORING_EDITED_DOCUMENT_TEXT}</p>"
HTML_AUTHORING_EDITED_TITLE = "Edited after renderer recovery"
HTML_AUTHORING_INITIAL_DOCUMENT_TEXT = "HTML authoring document ready"
HTML_AUTHORING_JAVASCRIPT = f"""window.addEventListener('dikciz-ready', () => {{
  const stateOutput = document.getElementById('authoring-state');
  const render = state => {{
    stateOutput.textContent = `State: ${{state.message || 'waiting'}}`;
  }};
  window.addEventListener('dikciz-state', event => render(event.detail || {{}}));
  render(selfWidget.state);
  dikciz.patchWidgetState('{HTML_AUTHORING_ADDRESS}', {{
    readyGeneration: Math.random().toString(36).slice(2),
  }}).catch(() => {{ stateOutput.textContent = 'State: generation update failed'; }});
}});"""
HTML_AUTHORING_MOVE_HANDLE_SEMANTIC_ID = "widget:authoring-surface:move-handle"
HTML_AUTHORING_PAGE_ID = FIXTURE_HOME_PAGE_ID
HTML_AUTHORING_CELL = {"column": 0, "row": 2, "columnSpan": 4, "rowSpan": 2}
HTML_AUTHORING_READY_GENERATION_KEY = "readyGeneration"
HTML_AUTHORING_RENDERER_CRASH_COMMAND = "htmlWidgetRendererCrash"
HTML_AUTHORING_RENDERER_CRASH_OUTCOME = "crash_requested"
HTML_AUTHORING_RENDERER_GONE_EVENT = "html_widget_renderer_gone"
HTML_AUTHORING_RENDERER_RECOVERED_EVENT = "html_widget_renderer_recovered"
HTML_AUTHORING_STATE_MESSAGE_KEY = "message"
HTML_AUTHORING_STATE_TEXT = f"State: {HTML_AUTHORING_DIRECT_MESSAGE}"
HTML_AUTHORING_WIDGET_EDIT_SEMANTIC_ID = "widget:authoring-surface:edit"
HTML_AUTHORING_WIDGET_ID = "authoring-surface"
HTML_AUTHORING_WIDGET_TITLE = "HTML authoring surface"
OUTCOME_EXECUTED = "executed"
PATCH_STATE_ACTION_TYPE = "patchState"
RESULT_ACTIONS_KEY = "actions"
RESULT_OUTCOME_KEY = "outcome"


def authoring_widget() -> dict[str, object]:
    return {
        "id": HTML_AUTHORING_WIDGET_ID,
        "type": "html",
        "title": HTML_AUTHORING_WIDGET_TITLE,
        "html": (
            f"<p>{HTML_AUTHORING_INITIAL_DOCUMENT_TEXT}</p>"
            '<p id="authoring-state">State: waiting</p>'
        ),
        "css": HTML_AUTHORING_CSS,
        "javascript": HTML_AUTHORING_JAVASCRIPT,
        "state": {},
        "enabled": True,
        "cell": dict(HTML_AUTHORING_CELL),
    }


def authoring_configuration(original: dict[str, Any]) -> dict[str, Any]:
    configured = copy.deepcopy(original)
    configuration_page(configured, HTML_AUTHORING_PAGE_ID)["widgets"].append(authoring_widget())
    configured["launcher"]["home"]["selectedPageId"] = HTML_AUTHORING_PAGE_ID
    return configured


def widget_state(
    connection: socket.socket,
    expected: dict[str, str],
) -> tuple[dict[str, Any], dict[str, object]]:
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    observed_state: object = None
    while time.monotonic() < deadline:
        configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
        )
        candidate = configuration_widget(configuration, HTML_AUTHORING_WIDGET_ID)
        observed_state = candidate.get("state")
        if isinstance(observed_state, dict) and all(
            observed_state.get(key) == value
            for key, value in expected.items()
        ):
            return configuration, observed_state
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"HTML authoring state was not persisted: {observed_state!r}")


def renderer_event_count(snapshot: dict[str, object], event: str) -> int:
    records = snapshot.get(AUTOMATION.KEY_SCRIPT_LOG_RECORDS)
    if not isinstance(records, list):
        return 0
    return sum(
        isinstance(record, dict)
        and record.get(AUTOMATION.KEY_SCRIPT_LOG_EVENT) == event
        and record.get(AUTOMATION.KEY_SCRIPT_LOG_SCRIPT_ID) == HTML_AUTHORING_WIDGET_ID
        for record in records
    )


def wait_for_renderer_recovery(
    connection: socket.socket,
    *,
    gone_count: int,
    recovered_count: int,
) -> None:
    deadline = time.monotonic() + PROVIDER_RENDER_TIMEOUT_SECONDS
    latest_snapshot: dict[str, object] = {}
    while time.monotonic() < deadline:
        latest_snapshot = AUTOMATION.request(connection, AUTOMATION.TYPE_SCRIPT_LOGS)
        if (
            renderer_event_count(latest_snapshot, HTML_AUTHORING_RENDERER_GONE_EVENT) > gone_count
            and renderer_event_count(latest_snapshot, HTML_AUTHORING_RENDERER_RECOVERED_EVENT) > recovered_count
        ):
            return
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"HTML authoring renderer did not recover: {latest_snapshot!r}")


def direct_state_result(
    connection: socket.socket,
    generation: str,
) -> dict[str, object]:
    return AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_AUTOMATION_DISPATCH,
        sourceWidgetAddress=HTML_AUTHORING_ADDRESS,
        actions=[
            {
                "type": PATCH_STATE_ACTION_TYPE,
                "values": {
                    HTML_AUTHORING_READY_GENERATION_KEY: generation,
                    HTML_AUTHORING_STATE_MESSAGE_KEY: HTML_AUTHORING_DIRECT_MESSAGE,
                },
            },
        ],
    )


def open_authoring_editor(connection: socket.socket) -> None:
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_LONG_PRESS,
        semanticId=HTML_AUTHORING_MOVE_HANDLE_SEMANTIC_ID,
    )
    wait_for_snapshot_node(connection, HTML_AUTHORING_WIDGET_EDIT_SEMANTIC_ID)
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_TAP,
        semanticId=HTML_AUTHORING_WIDGET_EDIT_SEMANTIC_ID,
    )
    wait_for_snapshot_node(connection, HTML_AUTHORING_EDITOR_TITLE_SEMANTIC_ID)


def test_html_authoring_survives_renderer_loss_and_direct_state_without_reload(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    try:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=authoring_configuration(original),
        )
        wait_for_physical_text_bounds(HTML_AUTHORING_INITIAL_DOCUMENT_TEXT)
        _, initial_state = widget_state(websocket_control, {})
        generation = initial_state.get(HTML_AUTHORING_READY_GENERATION_KEY)
        assert isinstance(generation, str) and generation

        direct_result = direct_state_result(websocket_control, generation)
        actions = direct_result.get(RESULT_ACTIONS_KEY)
        assert isinstance(actions, list) and len(actions) == 1
        assert actions[0][RESULT_OUTCOME_KEY] == OUTCOME_EXECUTED
        _, updated_state = widget_state(
            websocket_control,
            {
                HTML_AUTHORING_READY_GENERATION_KEY: generation,
                HTML_AUTHORING_STATE_MESSAGE_KEY: HTML_AUTHORING_DIRECT_MESSAGE,
            },
        )
        assert updated_state == {
            HTML_AUTHORING_READY_GENERATION_KEY: generation,
            HTML_AUTHORING_STATE_MESSAGE_KEY: HTML_AUTHORING_DIRECT_MESSAGE,
        }
        wait_for_physical_text_bounds(HTML_AUTHORING_STATE_TEXT)

        open_authoring_editor(websocket_control)
        before = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SCRIPT_LOGS)
        gone_count = renderer_event_count(before, HTML_AUTHORING_RENDERER_GONE_EVENT)
        recovered_count = renderer_event_count(before, HTML_AUTHORING_RENDERER_RECOVERED_EVENT)
        assert AUTOMATION.request(
            websocket_control,
            HTML_AUTHORING_RENDERER_CRASH_COMMAND,
            widgetAddress=HTML_AUTHORING_ADDRESS,
        ) == {
            AUTOMATION.KEY_WIDGET_ADDRESS: HTML_AUTHORING_ADDRESS,
            AUTOMATION.KEY_OUTCOME: HTML_AUTHORING_RENDERER_CRASH_OUTCOME,
        }
        wait_for_renderer_recovery(
            websocket_control,
            gone_count=gone_count,
            recovered_count=recovered_count,
        )
        wait_for_snapshot_node(websocket_control, HTML_AUTHORING_EDITOR_TITLE_SEMANTIC_ID)
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_SET_TEXT,
            semanticId=HTML_AUTHORING_EDITOR_TITLE_SEMANTIC_ID,
            text=HTML_AUTHORING_EDITED_TITLE,
        )
        title_snapshot = wait_for_snapshot_node(
            websocket_control,
            HTML_AUTHORING_EDITOR_TITLE_SEMANTIC_ID,
        )
        title_input = AUTOMATION.require_node(
            title_snapshot,
            HTML_AUTHORING_EDITOR_TITLE_SEMANTIC_ID,
        )
        assert title_input[AUTOMATION.KEY_TEXT] == HTML_AUTHORING_EDITED_TITLE
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_SET_TEXT,
            semanticId=HTML_AUTHORING_EDITOR_HTML_SEMANTIC_ID,
            text=HTML_AUTHORING_EDITED_HTML,
        )
        html_snapshot = wait_for_snapshot_node(
            websocket_control,
            HTML_AUTHORING_EDITOR_HTML_SEMANTIC_ID,
        )
        html_input = AUTOMATION.require_node(
            html_snapshot,
            HTML_AUTHORING_EDITOR_HTML_SEMANTIC_ID,
        )
        assert html_input[AUTOMATION.KEY_TEXT] == HTML_AUTHORING_EDITED_HTML
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_TAP,
            semanticId=HTML_AUTHORING_EDITOR_SAVE_SEMANTIC_ID,
        )
        wait_for_physical_text_bounds(HTML_AUTHORING_EDITED_DOCUMENT_TEXT)
        persisted = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        saved = configuration_widget(persisted, HTML_AUTHORING_WIDGET_ID)
        assert saved["title"] == HTML_AUTHORING_EDITED_TITLE
        assert saved["html"] == HTML_AUTHORING_EDITED_HTML
        run_device_operation("screenshot")
        run_device_operation("uiautomator-dump")
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
