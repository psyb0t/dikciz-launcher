"""Installed HTML DOM and cross-page state delivery through public bridges."""

import argparse
import json

from . import *


SOURCE_WIDGET_ID = "cross-state-source"
SOURCE_WIDGET_TITLE = "Cross-page source"
DOM_SOURCE_WIDGET_ID = "dom-patch-source"
DOM_SOURCE_VISIBLE_TEXT = "HTML bridge result: executed"
DOM_TARGET_WIDGET_ID = "dom-patch-target"
DOM_TARGET_INITIAL_TEXT = "DOM target: waiting"
DOM_TARGET_HTML_TEXT = "DOM target: updated by HTML"
DOM_TARGET_DIRECT_TEXT = "DOM target: updated by WebSocket"
DOM_TARGET_LUA_TEXT = "DOM target: updated by Lua"
DOM_TARGET_ATTRIBUTE = "data-patch-source"
DOM_TARGET_ATTRIBUTE_VALUE = "html"
DOM_LUA_SCRIPT_ID = "dom-patch-lua"
DOM_LUA_STATUS = "Lua DOM patch completed"
TARGET_WIDGET_ID = "cross-state-target"
TARGET_WIDGET_ADDRESS = f"2H1V-{TARGET_WIDGET_ID}"
TARGET_WIDGET_TITLE = "Cross-page target"
TARGET_WIDGET_INITIAL_TEXT = "Target state: waiting"
TARGET_WIDGET_UPDATED_TEXT = "Target state: delivered from Home"
TARGET_WIDGET_STATE_KEY = "message"
TARGET_WIDGET_STATE_VALUE = "delivered from Home"
WIDGET_CSS = "body{margin:0;background:#162337;color:#f3f7ff;font:16px system-ui,sans-serif}p{margin:16px}"
DOM_SOURCE_WIDGET_ADDRESS = f"1H1V-{DOM_SOURCE_WIDGET_ID}"
DOM_TARGET_WIDGET_ADDRESS = f"1H1V-{DOM_TARGET_WIDGET_ID}"
DOM_PATCH_POLICY_ID = "dom-patch-policy"
DOM_PATCH_SELECTOR = "#dom-target"
DOM_PATCH_ATTRIBUTE_VALUES = {DOM_TARGET_ATTRIBUTE: DOM_TARGET_ATTRIBUTE_VALUE}
DOM_PATCH_HTML_VALUES = {
    "text": DOM_TARGET_HTML_TEXT,
    "attributes": DOM_PATCH_ATTRIBUTE_VALUES,
}
DOM_PATCH_LUA_ATTRIBUTE_VALUE = "lua"
DOM_PATCH_NON_INPUT_VALUES = {"value": "unavailable"}
DOM_PATCH_EMPTY_VALUES: dict[str, object] = {}
DOM_PATCH_MISSING_SELECTOR = "#missing-dom-target"
DOM_PATCH_INVALID_SELECTOR = "["
DOM_PATCH_OFFSCREEN_ADDRESS = "2H1V-notes-intro"
DOM_PATCH_OUTCOME_EXECUTED = "executed"
DOM_PATCH_OUTCOME_NOT_RENDERED = "target_not_rendered"
DOM_PATCH_OUTCOME_PROPERTY_UNAVAILABLE = "target_property_unavailable"
DOM_PATCH_OUTCOME_SELECTOR_INVALID = "selector_invalid"
DOM_PATCH_OUTCOME_SELECTOR_NOT_FOUND = "selector_not_found"
DOM_PATCH_VALIDATION_ERROR = "validation_failed"
DOM_PATCH_ACTION_TYPE = "patchDom"
DOM_RESULT_ACTIONS_FIELD = "actions"
DOM_RESULT_OUTCOME_FIELD = "outcome"
DOM_RESULT_TYPE_FIELD = "type"
DOM_LOG_EVENT_COMPLETED = "automation_action_completed"
DOM_SOURCE_WIDGET_CELL = {"column": 0, "row": 3, "columnSpan": 4, "rowSpan": 1}
DOM_TARGET_WIDGET_CELL = {"column": 0, "row": 2, "columnSpan": 4, "rowSpan": 1}
CROSS_STATE_SOURCE_CELL = {"column": 0, "row": 4, "columnSpan": 4, "rowSpan": 1}


def source_widget() -> dict[str, object]:
    return {
        "id": SOURCE_WIDGET_ID,
        "type": "html",
        "title": SOURCE_WIDGET_TITLE,
        "html": '<p id="source-status">Patch outcome: waiting</p>',
        "css": WIDGET_CSS,
        "javascript": f"""window.addEventListener('dikciz-ready', async () => {{
  const result = await dikciz.patchWidgetState('{TARGET_WIDGET_ADDRESS}', {{
    {TARGET_WIDGET_STATE_KEY}: '{TARGET_WIDGET_STATE_VALUE}',
  }});
  document.getElementById('source-status').textContent = `Patch outcome: ${{result.actions[0].outcome}}`;
}});""",
        "state": {},
        "enabled": True,
        "cell": {"column": 0, "row": 4, "columnSpan": 4, "rowSpan": 1},
    }


def dom_source_widget() -> dict[str, object]:
    return {
        "id": DOM_SOURCE_WIDGET_ID,
        "type": "html",
        "title": "DOM patch source",
        "html": '<p id="dom-source-status">HTML bridge result: waiting</p>',
        "css": WIDGET_CSS,
        "javascript": f"""window.addEventListener('dikciz-ready', () => {{
  const run = async () => {{
    let result;
    for (let attempt = 0; attempt < 20; attempt += 1) {{
      result = await dikciz.patchDom('{DOM_TARGET_WIDGET_ADDRESS}', '{DOM_PATCH_SELECTOR}', {json.dumps(DOM_PATCH_HTML_VALUES)});
      if (!['{DOM_PATCH_OUTCOME_NOT_RENDERED}', '{DOM_PATCH_OUTCOME_SELECTOR_NOT_FOUND}'].includes(result.actions[0].outcome)) break;
      await new Promise(resolve => window.setTimeout(resolve, 100));
    }}
    document.getElementById('dom-source-status').textContent = `HTML bridge result: ${{result.actions[0].outcome}}`;
  }};
  run().catch(() => {{
    document.getElementById('dom-source-status').textContent = 'HTML bridge result: dom_operation_failed';
  }});
}});""",
        "state": {},
        "enabled": True,
        "cell": dict(DOM_SOURCE_WIDGET_CELL),
    }


def dom_target_widget() -> dict[str, object]:
    return {
        "id": DOM_TARGET_WIDGET_ID,
        "type": "html",
        "title": "DOM patch target",
        "html": f'<p id="dom-target">{DOM_TARGET_INITIAL_TEXT}</p>',
        "css": WIDGET_CSS,
        "javascript": "",
        "state": {},
        "enabled": True,
        "cell": dict(DOM_TARGET_WIDGET_CELL),
    }


def dom_lua_script() -> dict[str, object]:
    return {
        "id": DOM_LUA_SCRIPT_ID,
        "title": "Lua DOM patch",
        "enabled": True,
        "apiVersion": LUA_SCRIPT_API_VERSION,
    "source": f"""function on_event(event)
  if event.type ~= "manual" then return end
  return {{
    status = "{DOM_LUA_STATUS}",
    actions = {{
      {{
        type = "{DOM_PATCH_ACTION_TYPE}",
        widgetAddress = "{DOM_TARGET_WIDGET_ADDRESS}",
        selector = "{DOM_PATCH_SELECTOR}",
        values = {{
          text = "{DOM_TARGET_LUA_TEXT}",
          attributes = {{["{DOM_TARGET_ATTRIBUTE}"] = "{DOM_PATCH_LUA_ATTRIBUTE_VALUE}"}}
        }}
      }}
    }}
  }}
end""",
        "state": {},
    }


def dom_automation_configuration(original: dict[str, Any]) -> dict[str, Any]:
    configured = copy.deepcopy(original)
    home_page = configuration_page(configured, FIXTURE_HOME_PAGE_ID)
    home_page["widgets"].append(dom_target_widget())
    home_page["widgets"].append(dom_source_widget())
    cross_page_source = source_widget()
    cross_page_source["cell"] = dict(CROSS_STATE_SOURCE_CELL)
    home_page["widgets"].append(cross_page_source)
    configuration_page(configured, FIXTURE_NOTES_PAGE_ID)["widgets"].append(target_widget())
    configured["scripts"].append(dom_lua_script())
    configured["automation"] = {
        "apiVersion": 1,
        "policies": [
            {
                "id": DOM_PATCH_POLICY_ID,
                "title": "DOM patch policy",
                "enabled": True,
                "capabilities": ["manualTrigger"],
                "actions": [DOM_PATCH_ACTION_TYPE],
            },
        ],
        "scripts": [
            {
                "scriptId": DOM_LUA_SCRIPT_ID,
                "policyId": DOM_PATCH_POLICY_ID,
                "enabled": True,
                "subscriptions": [{"event": "manual", "minimumIntervalMilliseconds": 0}],
            },
        ],
    }
    return configured


def target_widget() -> dict[str, object]:
    return {
        "id": TARGET_WIDGET_ID,
        "type": "html",
        "title": TARGET_WIDGET_TITLE,
        "html": f'<p id="target-status">{TARGET_WIDGET_INITIAL_TEXT}</p>',
        "css": WIDGET_CSS,
        "javascript": """window.addEventListener('dikciz-ready', () => {
  const status = document.getElementById('target-status');
  const render = state => {
    status.textContent = `Target state: ${state.message || 'waiting'}`;
  };
  render(selfWidget.state);
  window.addEventListener('dikciz-state', event => render(event.detail || {}));
});""",
        "state": {TARGET_WIDGET_STATE_KEY: "waiting"},
        "enabled": True,
        "cell": {"column": 0, "row": 1, "columnSpan": 4, "rowSpan": 1},
    }


def wait_for_target_state(connection: socket.socket) -> dict[str, Any]:
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    observed_state: object = None
    while time.monotonic() < deadline:
        configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
        )
        target = configuration_widget(configuration, TARGET_WIDGET_ID)
        observed_state = target.get("state")
        if (
            isinstance(observed_state, dict)
            and observed_state.get(TARGET_WIDGET_STATE_KEY) == TARGET_WIDGET_STATE_VALUE
        ):
            return configuration
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"offscreen target state was not persisted: {observed_state!r}")


def dom_patch_result(outcome: str) -> dict[str, object]:
    return {
        DOM_RESULT_ACTIONS_FIELD: [
            {
                DOM_RESULT_TYPE_FIELD: DOM_PATCH_ACTION_TYPE,
                DOM_RESULT_OUTCOME_FIELD: outcome,
            },
        ],
    }


def dispatch_dom_patch(
    connection: socket.socket,
    *,
    target_address: str,
    selector: str,
    values: dict[str, object],
) -> dict[str, object]:
    return AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_AUTOMATION_DISPATCH,
        sourceWidgetAddress=DOM_SOURCE_WIDGET_ADDRESS,
        actions=[
            {
                "type": DOM_PATCH_ACTION_TYPE,
                "widgetAddress": target_address,
                "selector": selector,
                "values": values,
            },
        ],
    )


def matching_dom_action_log(
    records: object,
    *,
    script_id: str,
) -> dict[str, object] | None:
    if not isinstance(records, list):
        return None
    for record in records:
        if not isinstance(record, dict):
            continue
        if record.get(AUTOMATION.KEY_SCRIPT_LOG_EVENT) != DOM_LOG_EVENT_COMPLETED:
            continue
        if record.get(AUTOMATION.KEY_SCRIPT_LOG_SCRIPT_ID) != script_id:
            continue
        if record.get(AUTOMATION.KEY_SCRIPT_LOG_ACTION_TYPE) != DOM_PATCH_ACTION_TYPE:
            continue
        if record.get(AUTOMATION.KEY_SCRIPT_LOG_OUTCOME) == DOM_PATCH_OUTCOME_EXECUTED:
            return record
    return None


def wait_for_dom_action_log(
    connection: socket.socket,
    *,
    script_id: str,
) -> dict[str, object]:
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    snapshot: dict[str, object] = {}
    while time.monotonic() < deadline:
        snapshot = AUTOMATION.request(connection, AUTOMATION.TYPE_SCRIPT_LOGS)
        record = matching_dom_action_log(
            snapshot.get(AUTOMATION.KEY_SCRIPT_LOG_RECORDS),
            script_id=script_id,
        )
        if record is not None:
            return record
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"DOM patch action was not logged: {snapshot!r}")


def mcp_script_logs() -> dict[str, object]:
    arguments = argparse.Namespace(host=AUTOMATION_HOST, port=MCP_PORT, mutate=False)
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        return MCP.require_tool_result(
            MCP.call_tool(arguments, session_id, 1, MCP.TOOL_SCRIPT_LOGS),
        )
    finally:
        MCP.delete_session(arguments, session_id)


def moved_target_configuration(configuration: dict[str, Any]) -> dict[str, Any]:
    moved = copy.deepcopy(configuration)
    notes_page = configuration_page(moved, FIXTURE_NOTES_PAGE_ID)
    target = configuration_widget(moved, TARGET_WIDGET_ID)
    notes_page["widgets"] = [
        widget
        for widget in notes_page["widgets"]
        if widget.get("id") != TARGET_WIDGET_ID
    ]
    configuration_page(moved, FIXTURE_ACTIVITY_PAGE_ID)["widgets"].append(target)
    return moved


def test_html_dom_patch_and_offscreen_state_paths_use_real_widgets(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = dom_automation_configuration(original)
    try:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        )
        wait_for_physical_text_bounds(DOM_SOURCE_VISIBLE_TEXT)
        wait_for_physical_text_bounds(DOM_TARGET_HTML_TEXT)
        initial_dom = configuration_widget(
            AUTOMATION.require_configuration(
                AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
            ),
            DOM_TARGET_WIDGET_ID,
        )
        assert initial_dom["html"] == f'<p id="dom-target">{DOM_TARGET_INITIAL_TEXT}</p>'
        assert initial_dom["state"] == {}

        direct_result = dispatch_dom_patch(
            websocket_control,
            target_address=DOM_TARGET_WIDGET_ADDRESS,
            selector=DOM_PATCH_SELECTOR,
            values={"text": DOM_TARGET_DIRECT_TEXT},
        )
        assert direct_result == dom_patch_result(DOM_PATCH_OUTCOME_EXECUTED)
        wait_for_physical_text_bounds(DOM_TARGET_DIRECT_TEXT)
        assert dispatch_dom_patch(
            websocket_control,
            target_address=DOM_TARGET_WIDGET_ADDRESS,
            selector=DOM_PATCH_SELECTOR,
            values=DOM_PATCH_NON_INPUT_VALUES,
        ) == dom_patch_result(DOM_PATCH_OUTCOME_PROPERTY_UNAVAILABLE)
        assert dispatch_dom_patch(
            websocket_control,
            target_address=DOM_TARGET_WIDGET_ADDRESS,
            selector=DOM_PATCH_MISSING_SELECTOR,
            values={"text": DOM_TARGET_HTML_TEXT},
        ) == dom_patch_result(DOM_PATCH_OUTCOME_SELECTOR_NOT_FOUND)
        assert dispatch_dom_patch(
            websocket_control,
            target_address=DOM_TARGET_WIDGET_ADDRESS,
            selector=DOM_PATCH_INVALID_SELECTOR,
            values={"text": DOM_TARGET_HTML_TEXT},
        ) == dom_patch_result(DOM_PATCH_OUTCOME_SELECTOR_INVALID)
        assert dispatch_dom_patch(
            websocket_control,
            target_address=DOM_PATCH_OFFSCREEN_ADDRESS,
            selector=DOM_PATCH_SELECTOR,
            values={"text": DOM_TARGET_HTML_TEXT},
        ) == dom_patch_result(DOM_PATCH_OUTCOME_NOT_RENDERED)
        _, rejected = AUTOMATION.request_error_with_id(
            websocket_control,
            AUTOMATION.TYPE_AUTOMATION_DISPATCH,
            sourceWidgetAddress=DOM_SOURCE_WIDGET_ADDRESS,
            actions=[
                {
                    "type": DOM_PATCH_ACTION_TYPE,
                    "widgetAddress": DOM_TARGET_WIDGET_ADDRESS,
                    "selector": DOM_PATCH_SELECTOR,
                    "values": DOM_PATCH_EMPTY_VALUES,
                },
            ],
        )
        assert rejected[AUTOMATION.KEY_CODE] == DOM_PATCH_VALIDATION_ERROR

        html_log = wait_for_dom_action_log(
            websocket_control,
            script_id=DOM_SOURCE_WIDGET_ADDRESS,
        )
        assert matching_dom_action_log(
            mcp_script_logs().get(AUTOMATION.KEY_SCRIPT_LOG_RECORDS),
            script_id=DOM_SOURCE_WIDGET_ADDRESS,
        ) == html_log

        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_AUTOMATION_TRIGGER,
            scriptId=DOM_LUA_SCRIPT_ID,
        )
        wait_for_physical_text_bounds(DOM_TARGET_LUA_TEXT)
        lua_log = wait_for_dom_action_log(websocket_control, script_id=DOM_LUA_SCRIPT_ID)
        assert lua_log[AUTOMATION.KEY_SCRIPT_LOG_OUTCOME] == DOM_PATCH_OUTCOME_EXECUTED
        run_device_operation("screenshot")
        assert (ARTIFACT_DIRECTORY / "screenshot.png").stat().st_size > 0

        persisted = wait_for_target_state(websocket_control)
        target = configuration_widget(persisted, TARGET_WIDGET_ID)
        assert target["state"] == {TARGET_WIDGET_STATE_KEY: TARGET_WIDGET_STATE_VALUE}

        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=moved_target_configuration(persisted),
        )
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_SELECT_PAGE,
            pageId=FIXTURE_ACTIVITY_PAGE_ID,
        )
        wait_for_selected_page(websocket_control, FIXTURE_ACTIVITY_PAGE_ID)
        wait_for_physical_text_bounds(TARGET_WIDGET_UPDATED_TEXT)
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
