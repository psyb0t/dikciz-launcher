"""Installed WebView renderer-loss recovery through the public local controls."""

import argparse

from . import *


HTML_RENDERER_CRASH_MCP_TOOL = "dikciz_html_widget_renderer_crash"
HTML_RENDERER_CRASH_COMMAND = "htmlWidgetRendererCrash"
HTML_RENDERER_CRASH_OUTCOME = "crash_requested"
HTML_RENDERER_EVENT_GONE = "html_widget_renderer_gone"
HTML_RENDERER_EVENT_RECOVERED = "html_widget_renderer_recovered"
HTML_RENDERER_NOT_RENDERED_OUTCOME = "target_not_rendered"
HTML_RENDERER_REPEAT_OUTCOMES = frozenset(
    (HTML_RENDERER_CRASH_OUTCOME, HTML_RENDERER_NOT_RENDERED_OUTCOME),
)
HTML_RENDERER_PAGE_ID = FIXTURE_HOME_PAGE_ID
HTML_RENDERER_NOT_RENDERED_PAGE_ID = FIXTURE_NOTES_PAGE_ID
HTML_RENDERER_SIBLING_ID = "renderer-sibling"
HTML_RENDERER_SIBLING_ADDRESS = f"1H1V-{HTML_RENDERER_SIBLING_ID}"
HTML_RENDERER_SIBLING_INITIAL_TEXT = "Renderer sibling: ready"
HTML_RENDERER_SIBLING_PATCHED_TEXT = "Renderer sibling: DOM kept"
HTML_RENDERER_TARGET_ID = "renderer-target"
HTML_RENDERER_TARGET_ADDRESS = f"1H1V-{HTML_RENDERER_TARGET_ID}"
HTML_RENDERER_TARGET_TEXT = "Renderer target: recovered"
HTML_RENDERER_REPLACEMENT_TEXT = "Renderer target: replacement won"
HTML_RENDERER_WIDGET_CSS = "body{margin:0;background:#162337;color:#f3f7ff;font:16px system-ui,sans-serif}p{margin:16px}"
HTML_RENDERER_TARGET_CELL = {"column": 0, "row": 2, "columnSpan": 4, "rowSpan": 1}
HTML_RENDERER_SIBLING_CELL = {"column": 0, "row": 3, "columnSpan": 4, "rowSpan": 1}
PATCH_DOM_ACTION_TYPE = "patchDom"
PATCH_DOM_OUTCOME_EXECUTED = "executed"
PATCH_DOM_SELECTOR = "#renderer-sibling"


def renderer_widget(
    *,
    widget_id: str,
    text: str,
    cell: dict[str, int],
    element_id: str,
) -> dict[str, object]:
    return {
        "id": widget_id,
        "type": "html",
        "title": widget_id,
        "html": f'<p id="{element_id}">{text}</p>',
        "css": HTML_RENDERER_WIDGET_CSS,
        "javascript": "",
        "state": {},
        "enabled": True,
        "cell": dict(cell),
    }


def renderer_recovery_configuration(original: dict[str, Any]) -> dict[str, Any]:
    configured = copy.deepcopy(original)
    page = configuration_page(configured, HTML_RENDERER_PAGE_ID)
    page["widgets"].extend(
        (
            renderer_widget(
                widget_id=HTML_RENDERER_TARGET_ID,
                text=HTML_RENDERER_TARGET_TEXT,
                cell=HTML_RENDERER_TARGET_CELL,
                element_id=HTML_RENDERER_TARGET_ID,
            ),
            renderer_widget(
                widget_id=HTML_RENDERER_SIBLING_ID,
                text=HTML_RENDERER_SIBLING_INITIAL_TEXT,
                cell=HTML_RENDERER_SIBLING_CELL,
                element_id=HTML_RENDERER_SIBLING_ID,
            ),
        ),
    )
    configured["launcher"]["home"]["selectedPageId"] = HTML_RENDERER_PAGE_ID
    return configured


def renderer_replacement_configuration(configured: dict[str, Any]) -> dict[str, Any]:
    replacement = copy.deepcopy(configured)
    target = configuration_widget(replacement, HTML_RENDERER_TARGET_ID)
    target["html"] = f'<p id="{HTML_RENDERER_TARGET_ID}">{HTML_RENDERER_REPLACEMENT_TEXT}</p>'
    return replacement


def patch_sibling_dom(connection: socket.socket) -> None:
    result = AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_AUTOMATION_DISPATCH,
        sourceWidgetAddress=HTML_RENDERER_TARGET_ADDRESS,
        actions=[
            {
                "type": PATCH_DOM_ACTION_TYPE,
                "widgetAddress": HTML_RENDERER_SIBLING_ADDRESS,
                "selector": PATCH_DOM_SELECTOR,
                "values": {"text": HTML_RENDERER_SIBLING_PATCHED_TEXT},
            },
        ],
    )
    assert result["actions"] == [
        {"type": PATCH_DOM_ACTION_TYPE, "outcome": PATCH_DOM_OUTCOME_EXECUTED},
    ]


def renderer_records(
    snapshot: dict[str, object],
    event: str,
    widget_id: str,
) -> list[dict[str, object]]:
    records = snapshot.get(AUTOMATION.KEY_SCRIPT_LOG_RECORDS)
    if not isinstance(records, list):
        return []
    return [
        record
        for record in records
        if isinstance(record, dict)
        and record.get(AUTOMATION.KEY_SCRIPT_LOG_EVENT) == event
        and record.get(AUTOMATION.KEY_SCRIPT_LOG_SCRIPT_ID) == widget_id
    ]


def wait_for_renderer_recovery(
    connection: socket.socket,
    *,
    gone_count: int,
    recovered_count: int,
) -> dict[str, object]:
    deadline = time.monotonic() + PROVIDER_RENDER_TIMEOUT_SECONDS
    latest_snapshot: dict[str, object] = {}
    while time.monotonic() < deadline:
        latest_snapshot = AUTOMATION.request(connection, AUTOMATION.TYPE_SCRIPT_LOGS)
        target_gone = renderer_records(
            latest_snapshot,
            HTML_RENDERER_EVENT_GONE,
            HTML_RENDERER_TARGET_ID,
        )
        target_recovered = renderer_records(
            latest_snapshot,
            HTML_RENDERER_EVENT_RECOVERED,
            HTML_RENDERER_TARGET_ID,
        )
        if len(target_gone) > gone_count and len(target_recovered) > recovered_count:
            return latest_snapshot
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"renderer recovery was not logged: {latest_snapshot!r}")


def wait_for_renderer_loss(
    connection: socket.socket,
    *,
    gone_count: int,
) -> dict[str, object]:
    deadline = time.monotonic() + PROVIDER_RENDER_TIMEOUT_SECONDS
    latest_snapshot: dict[str, object] = {}
    while time.monotonic() < deadline:
        latest_snapshot = AUTOMATION.request(connection, AUTOMATION.TYPE_SCRIPT_LOGS)
        target_gone = renderer_records(
            latest_snapshot,
            HTML_RENDERER_EVENT_GONE,
            HTML_RENDERER_TARGET_ID,
        )
        if len(target_gone) > gone_count:
            return latest_snapshot
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"renderer loss was not logged: {latest_snapshot!r}")


def request_renderer_crash_through_mcp() -> dict[str, object]:
    arguments = argparse.Namespace(host=AUTOMATION_HOST, port=MCP_PORT, mutate=False)
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        return MCP.require_tool_result(
            MCP.call_tool(
                arguments,
                session_id,
                1,
                HTML_RENDERER_CRASH_MCP_TOOL,
                {AUTOMATION.KEY_WIDGET_ADDRESS: HTML_RENDERER_TARGET_ADDRESS},
            ),
        )
    finally:
        MCP.delete_session(arguments, session_id)


def test_html_renderer_recovery_recreates_only_affected_widget(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = renderer_recovery_configuration(original)
    try:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        )
        wait_for_physical_text_bounds(HTML_RENDERER_TARGET_TEXT)
        wait_for_physical_text_bounds(HTML_RENDERER_SIBLING_INITIAL_TEXT)
        patch_sibling_dom(websocket_control)
        wait_for_physical_text_bounds(HTML_RENDERER_SIBLING_PATCHED_TEXT)

        before = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SCRIPT_LOGS)
        target_gone_count = len(
            renderer_records(before, HTML_RENDERER_EVENT_GONE, HTML_RENDERER_TARGET_ID),
        )
        target_recovered_count = len(
            renderer_records(before, HTML_RENDERER_EVENT_RECOVERED, HTML_RENDERER_TARGET_ID),
        )
        sibling_gone_count = len(
            renderer_records(before, HTML_RENDERER_EVENT_GONE, HTML_RENDERER_SIBLING_ID),
        )

        assert request_renderer_crash_through_mcp() == {
            AUTOMATION.KEY_WIDGET_ADDRESS: HTML_RENDERER_TARGET_ADDRESS,
            AUTOMATION.KEY_OUTCOME: HTML_RENDERER_CRASH_OUTCOME,
        }
        recovery_logs = wait_for_renderer_recovery(
            websocket_control,
            gone_count=target_gone_count,
            recovered_count=target_recovered_count,
        )
        wait_for_physical_text_bounds(HTML_RENDERER_TARGET_TEXT)
        persisted = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        expected_target = configuration_widget(configured, HTML_RENDERER_TARGET_ID)
        persisted_target = configuration_widget(persisted, HTML_RENDERER_TARGET_ID)
        assert {key: persisted_target[key] for key in expected_target} == expected_target
        assert persisted["launcher"]["home"]["selectedPageId"] == HTML_RENDERER_PAGE_ID

        sibling_was_affected = len(
            renderer_records(
                recovery_logs,
                HTML_RENDERER_EVENT_GONE,
                HTML_RENDERER_SIBLING_ID,
            ),
        ) > sibling_gone_count
        if not sibling_was_affected:
            wait_for_physical_text_bounds(HTML_RENDERER_SIBLING_PATCHED_TEXT)
        run_device_operation("screenshot")
        assert (ARTIFACT_DIRECTORY / "screenshot.png").stat().st_size > 0
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )


def test_html_renderer_crash_requires_a_rendered_widget(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = renderer_recovery_configuration(original)
    try:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        )
        wait_for_physical_text_bounds(HTML_RENDERER_TARGET_TEXT)

        selected = AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_SELECT_PAGE,
            pageId=HTML_RENDERER_NOT_RENDERED_PAGE_ID,
        )
        assert selected[AUTOMATION.KEY_SELECTED_PAGE_ID] == HTML_RENDERER_NOT_RENDERED_PAGE_ID
        wait_for_selected_page(websocket_control, HTML_RENDERER_NOT_RENDERED_PAGE_ID)

        assert AUTOMATION.request(
            websocket_control,
            HTML_RENDERER_CRASH_COMMAND,
            widgetAddress=HTML_RENDERER_TARGET_ADDRESS,
        ) == {
            AUTOMATION.KEY_WIDGET_ADDRESS: HTML_RENDERER_TARGET_ADDRESS,
            AUTOMATION.KEY_OUTCOME: HTML_RENDERER_NOT_RENDERED_OUTCOME,
        }
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )


def test_html_renderer_crash_coalesces_a_repeat_request(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = renderer_recovery_configuration(original)
    try:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        )
        wait_for_physical_text_bounds(HTML_RENDERER_TARGET_TEXT)
        before = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SCRIPT_LOGS)
        target_gone_count = len(
            renderer_records(before, HTML_RENDERER_EVENT_GONE, HTML_RENDERER_TARGET_ID),
        )
        target_recovered_count = len(
            renderer_records(before, HTML_RENDERER_EVENT_RECOVERED, HTML_RENDERER_TARGET_ID),
        )

        first = AUTOMATION.request(
            websocket_control,
            HTML_RENDERER_CRASH_COMMAND,
            widgetAddress=HTML_RENDERER_TARGET_ADDRESS,
        )
        repeated = AUTOMATION.request(
            websocket_control,
            HTML_RENDERER_CRASH_COMMAND,
            widgetAddress=HTML_RENDERER_TARGET_ADDRESS,
        )
        assert first == {
            AUTOMATION.KEY_WIDGET_ADDRESS: HTML_RENDERER_TARGET_ADDRESS,
            AUTOMATION.KEY_OUTCOME: HTML_RENDERER_CRASH_OUTCOME,
        }
        assert repeated[AUTOMATION.KEY_WIDGET_ADDRESS] == HTML_RENDERER_TARGET_ADDRESS
        assert repeated[AUTOMATION.KEY_OUTCOME] in HTML_RENDERER_REPEAT_OUTCOMES

        recovery_logs = wait_for_renderer_recovery(
            websocket_control,
            gone_count=target_gone_count,
            recovered_count=target_recovered_count,
        )
        assert len(
            renderer_records(
                recovery_logs,
                HTML_RENDERER_EVENT_GONE,
                HTML_RENDERER_TARGET_ID,
            ),
        ) == target_gone_count + 1
        assert len(
            renderer_records(
                recovery_logs,
                HTML_RENDERER_EVENT_RECOVERED,
                HTML_RENDERER_TARGET_ID,
            ),
        ) == target_recovered_count + 1
        wait_for_physical_text_bounds(HTML_RENDERER_TARGET_TEXT)
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )


def test_html_renderer_config_replacement_preserves_latest_widget(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = renderer_recovery_configuration(original)
    replacement = renderer_replacement_configuration(configured)
    try:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        )
        wait_for_physical_text_bounds(HTML_RENDERER_TARGET_TEXT)
        before = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SCRIPT_LOGS)
        target_gone_count = len(
            renderer_records(before, HTML_RENDERER_EVENT_GONE, HTML_RENDERER_TARGET_ID),
        )

        assert AUTOMATION.request(
            websocket_control,
            HTML_RENDERER_CRASH_COMMAND,
            widgetAddress=HTML_RENDERER_TARGET_ADDRESS,
        ) == {
            AUTOMATION.KEY_WIDGET_ADDRESS: HTML_RENDERER_TARGET_ADDRESS,
            AUTOMATION.KEY_OUTCOME: HTML_RENDERER_CRASH_OUTCOME,
        }
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=replacement,
        )
        wait_for_physical_text_bounds(HTML_RENDERER_REPLACEMENT_TEXT)
        wait_for_renderer_loss(
            websocket_control,
            gone_count=target_gone_count,
        )
        persisted = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert configuration_widget(persisted, HTML_RENDERER_TARGET_ID)["html"] == (
            f'<p id="{HTML_RENDERER_TARGET_ID}">{HTML_RENDERER_REPLACEMENT_TEXT}</p>'
        )
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
