"""Owner task shortcuts through production chat commands and Android UI."""

import copy
import json
import shutil
import time
import uuid

import pytest

from . import test_chat_agent as agent
from .configuration import append_empty_page
from .test_accessibility_automation import wait_for_accessibility_snapshot
from .test_native_data import PROTOCOLS, native_call
from .ui import direct_enter_text, direct_hide_keyboard, direct_tap, wait_for_physical_button_bounds, wait_for_physical_description_bounds, wait_for_physical_text_bounds

websocket_control = agent.websocket_control
provider_endpoint = agent.provider_endpoint
TASK_TEXT = "Create an independent test widget with a saved counter."


def task(identifier, kind, protocol="http", key=None, **values):
    return agent.chat("task", protocol, conversationId=identifier, task=kind,
                      requestKey=key or str(uuid.uuid4()), **values)


@pytest.mark.parametrize("entrypoint", PROTOCOLS)
@pytest.mark.parametrize("provider", ["openai", "anthropic"])
def test_chat_quick_tasks_widget_persists(websocket_control, provider_endpoint, entrypoint, provider):
    original = native_call("http", "configGet")["config"]
    configured = agent.fixture_document()
    page_id = "chat-task-" + uuid.uuid4().hex[:8]
    append_empty_page(configured, page_id, "Generated widget fixture", 7, 7)
    configured["launcher"]["home"]["selectedPageId"] = page_id
    native_call("http", "configReplace", {"config": configured})
    configured = native_call("http", "configGet")["config"]
    endpoint, script, records = provider_endpoint
    _, identifier = agent.conversation(endpoint, provider)
    generated = copy.deepcopy(configured)
    widget = {"id": "chat-generated-" + uuid.uuid4().hex[:8], "type": "html", "title": "Generated counter",
              "html": '<p id="counter">Counter ready</p>', "css": "body{background:#162337;color:white}",
              "javascript": "window.addEventListener('dikciz-ready',()=>{document.getElementById('counter').textContent='Counter '+selfWidget.state.count;selfWidget.patchState({count:selfWidget.state.count,ready:true});});",
              "state": {"count": 3}, "enabled": True,
              "cell": {"column": 0, "row": 0, "columnSpan": 2, "rowSpan": 2}}
    generated["launcher"]["home"]["pages"][-1]["widgets"].append(widget)
    script.update(text="Created the fixture counter.", tools=[("dikciz_config_replace", {"config": generated})])
    key = str(uuid.uuid4())
    try:
        queued = task(identifier, "widget", entrypoint, key, text=TASK_TEXT)
        rows = agent.wait_turn(identifier)
        block = json.loads(rows[0]["blocks"])[0]
        assert block["task"] == {"kind": "widget", "pageId": page_id}
        assert page_id in json.dumps(records[0]["body"])
        saved = native_call("http", "configGet")["config"]
        assert saved["launcher"]["home"]["pages"][:-1] == configured["launcher"]["home"]["pages"][:-1]
        actual = saved["launcher"]["home"]["pages"][-1]["widgets"]
        assert len(actual) == 1
        for field in ("html", "css", "javascript"):
            assert actual[0][field] == widget[field]
        deadline = time.monotonic() + agent.TURN_TIMEOUT
        while time.monotonic() < deadline:
            saved = native_call("http", "configGet")["config"]
            state = saved["launcher"]["home"]["pages"][-1]["widgets"][0]["state"]
            if state.get("ready") is True:
                break
            time.sleep(0.2)
        assert state == {"count": 3, "ready": True}
        native_call("http", "selectPage", {"pageId": configured["launcher"]["home"]["pages"][0]["id"]})
        assert task(identifier, "widget", entrypoint, key, text=TASK_TEXT)["turn_id"] == queued["turn_id"]
        count = len(records)
        native_call("http", "restart")
        agent.wait_chat_ready()
        assert native_call("http", "configGet")["config"]["launcher"]["home"]["pages"][-1]["widgets"][0]["state"] == {"count": 3, "ready": True}
        assert len(records) == count
    finally:
        agent.cleanup(identifier)
        native_call("http", "configReplace", {"config": original})


@pytest.mark.parametrize("entrypoint", PROTOCOLS)
@pytest.mark.parametrize("provider", ["openai", "anthropic"])
def test_chat_quick_tasks_screen_capture_and_read_only(websocket_control, provider_endpoint, entrypoint, provider):
    endpoint, script, records = provider_endpoint
    _, identifier = agent.conversation(endpoint, provider)
    vision = entrypoint != "http"
    agent.chat("configure", conversationId=identifier, settings={"supportsVision": vision})
    script.update(text="The fixture shows Android settings.", tools=[("dikciz_restart", {})])
    try:
        agent.emulator_dikciz_accessibility_set(True)
        agent.device_command(f"appops set {agent.DIKCIZ_DEBUG_PACKAGE} SYSTEM_ALERT_WINDOW allow")
        native_call("http", "intent", {"intentType": "activity", "action": "android.settings.SETTINGS"})
        wait_for_accessibility_snapshot(websocket_control)
        agent.chat("show", conversationId=identifier)
        direct_tap(wait_for_physical_description_bounds("Chat options"))
        wait_for_physical_text_bounds("Explain this screen")
        key = str(uuid.uuid4())
        queued = task(identifier, "explainScreen", entrypoint, key, text="Explain this settings screen.")
        rows = agent.wait_turn(identifier)
        first = records[0]["body"]
        contents = [part for message in first["messages"] for part in message.get("content", []) if isinstance(part, dict)]
        assert any(part.get("type") in ("image", "image_url") for part in contents) == vision
        serialized = json.dumps(first)
        assert "com.android.settings" in serialized
        assert "Chat options list" not in serialized
        results = [json.loads(json.loads(row["blocks"])[0]["text"]) for row in rows if row["role"] == "tool"]
        assert results[0]["isError"] is True
        assert results[0]["structuredContent"]["code"] == "read_only_violation"
        assert agent.chat("settings", conversationId=identifier)["settings"]["readOnly"] is False
        wait_for_physical_description_bounds("Chat message")
        assert agent.chat("status")["visible"] is True
        if vision:
            agent.chat("configure", conversationId=identifier, settings={"supportsVision": False})
            script.update(tools=[], text="Follow-up complete.")
            agent.send(identifier, "Continue using text.")
            agent.wait_turn(identifier)
            replay = json.dumps(records[-1]["body"]["messages"])
            assert "Image omitted for this text-only model" in replay
            assert "data:image" not in replay and '"type": "base64"' not in replay
        count = len(records)
        assert task(identifier, "explainScreen", entrypoint, key, text="Explain this settings screen.")["turn_id"] == queued["turn_id"]
        assert len(records) == count
        native_call(entrypoint, "chatControl", {"action": "task", "task": "explainScreen", "conversationId": identifier,
                    "requestKey": key, "text": "Changed question"}, error="chat_request_conflict")
    finally:
        agent.cleanup(identifier)
        agent.emulator_dikciz_accessibility_set(False)
        agent.device_command(f"appops set {agent.DIKCIZ_DEBUG_PACKAGE} SYSTEM_ALERT_WINDOW default")
        agent.run_device_operation("home-start", PACKAGE_NAME=agent.DIKCIZ_DEBUG_PACKAGE)


@pytest.mark.parametrize("entrypoint", PROTOCOLS)
def test_chat_quick_tasks_validation_and_access(websocket_control, provider_endpoint, entrypoint):
    _, identifier = agent.conversation(provider_endpoint[0])
    try:
        for values in ({}, {"task": "unknown"}, {"task": "widget"}, {"task": "widget", "text": " "}, {"task": None}):
            native_call(entrypoint, "chatControl", {"action": "task", "conversationId": identifier,
                        "requestKey": str(uuid.uuid4()), **values}, error="validation_failed")
        agent.chat("configure", conversationId=identifier, settings={"readOnly": True})
        native_call(entrypoint, "chatControl", {"action": "task", "task": "widget", "conversationId": identifier,
                    "requestKey": str(uuid.uuid4()), "text": TASK_TEXT}, error="read_only_violation")
        assert agent.chat("read", conversationId=identifier)["messages"]["items"] == []
    finally:
        agent.cleanup(identifier)


def test_chat_quick_tasks_missing_capture_access(websocket_control, provider_endpoint):
    _, identifier = agent.conversation(provider_endpoint[0])
    try:
        agent.emulator_dikciz_accessibility_set(False)
        task(identifier, "explainScreen")
        rows = agent.wait_turn(identifier, "failed")
        assert "Screen capture failed" in rows[-1]["blocks"]
        assert provider_endpoint[2] == []
    finally:
        agent.cleanup(identifier)


def test_chat_quick_tasks_native_buttons(websocket_control, provider_endpoint):
    _, identifier = agent.conversation(provider_endpoint[0])
    try:
        agent.chat("show", conversationId=identifier)
        direct_tap(wait_for_physical_description_bounds("Chat options"))
        direct_tap(wait_for_physical_text_bounds("Make me a widget"))
        direct_enter_text(wait_for_physical_description_bounds("Widget request"), "Create a fixture note widget.")
        direct_hide_keyboard()
        direct_tap(wait_for_physical_button_bounds("Create"))
        rows = agent.wait_turn(identifier)
        assert json.loads(rows[0]["blocks"])[0]["task"]["kind"] == "widget"
        agent.run_device_operation("screenshot")
        shutil.copyfile(agent.ARTIFACT_DIRECTORY / "screenshot.png", agent.ARTIFACT_DIRECTORY / "chat-quick-tasks.png")
        direct_tap(wait_for_physical_description_bounds("Chat options"))
        direct_tap(wait_for_physical_text_bounds("Explain this screen"))
        rows = agent.wait_turn(identifier, "failed")
        assert any(json.loads(row["blocks"])[0].get("task", {}).get("kind") == "explainScreen" for row in rows if row["role"] == "user")
    finally:
        agent.cleanup(identifier)


@pytest.mark.parametrize("entrypoint", PROTOCOLS)
def test_chat_quick_tasks_busy_and_cancel(websocket_control, provider_endpoint, entrypoint):
    endpoint, script, records = provider_endpoint
    _, identifier = agent.conversation(endpoint)
    script.update(text="Slow fixture answer. " * 100, delay=0.02)
    try:
        key = str(uuid.uuid4())
        queued = task(identifier, "widget", entrypoint, key, text=TASK_TEXT)
        deadline = time.monotonic() + agent.TURN_TIMEOUT
        while not records and time.monotonic() < deadline:
            time.sleep(0.05)
        assert records
        assert task(identifier, "widget", entrypoint, key, text=TASK_TEXT)["turn_id"] == queued["turn_id"]
        native_call(entrypoint, "chatControl", {"action": "task", "task": "explainScreen", "conversationId": identifier,
                    "requestKey": str(uuid.uuid4())}, error="chat_busy")
        agent.chat("stop", conversationId=identifier)
        agent.wait_turn(identifier, "cancelled")
        assert len(records) == 1
    finally:
        agent.cleanup(identifier)


def test_chat_quick_tasks_cancelled_screen_stays_hidden(websocket_control, provider_endpoint):
    endpoint, script, records = provider_endpoint
    _, identifier = agent.conversation(endpoint)
    script.update(text="Slow fixture answer. " * 100, delay=0.02)
    try:
        agent.emulator_dikciz_accessibility_set(True)
        agent.chat("hide")
        task(identifier, "explainScreen")
        deadline = time.monotonic() + agent.TURN_TIMEOUT
        while not records and time.monotonic() < deadline:
            time.sleep(0.1)
        assert records
        agent.chat("stop", conversationId=identifier)
        agent.wait_turn(identifier, "cancelled")
        time.sleep(0.5)
        assert agent.chat("status")["visible"] is False
    finally:
        agent.cleanup(identifier)
        agent.emulator_dikciz_accessibility_set(False)


def test_chat_home_group_count_in_one_read(websocket_control, provider_endpoint):
    original = native_call("http", "configGet")["config"]
    configured = agent.fixture_document()
    page_id = "chat-app-group"
    append_empty_page(configured, page_id, "Chat group fixture", 7, 7)
    components = ["com.android.settings/.Settings", "com.android.deskclock/.DeskClock"]
    address = "8H8V-fixture-group"
    configured["launcher"]["home"]["pages"][-1]["widgets"].append({
        "id": "fixture-group", "type": "appGroup", "title": "Fixture apps",
        "enabled": True, "components": components,
        "cell": {"column": 0, "row": 0, "columnSpan": 1, "rowSpan": 1},
    })
    endpoint, script, records = provider_endpoint
    _, identifier = agent.conversation(endpoint, "anthropic")
    script.update(wrapper_calls=[("dikciz_home_get", {})], text="There are two apps.")
    try:
        native_call("http", "configReplace", {"config": configured})
        native_call("http", "selectPage", {"pageId": page_id})
        agent.send(identifier, "How many apps are in Fixture apps?")
        rows = agent.wait_turn(identifier)
        results = [json.loads(json.loads(row["blocks"])[0]["text"]) for row in rows if row["role"] == "tool"]
        assert len(records) == 2
        assert len(results) == 1
        group = results[0]["structuredContent"]["pages"][page_id]["widgets"]["fixture-group"]
        assert group["memberCount"] == len(components)
        assert native_call("mcp", "widgetGet", {"widgetAddress": address})["components"] == components
    finally:
        agent.cleanup(identifier)
        native_call("http", "configReplace", {"config": original})


def test_chat_agentic_creates_native_group_with_one_write(websocket_control, provider_endpoint):
    original = native_call("http", "configGet")["config"]
    configured = agent.fixture_document()
    page_id = "chat-group-" + uuid.uuid4().hex[:8]
    append_empty_page(configured, page_id, "Group target", 7, 7)
    configured["launcher"]["home"]["homePageId"] = page_id
    endpoint, script, records = provider_endpoint
    _, identifier = agent.conversation(endpoint, "openai")
    group_title = "Settings apps"

    component = "com.android.settings/com.android.settings.Settings"
    script.update(wrapper_calls=[
        ("dikciz_app_catalogue", {}),
        ("dikciz_add_widget", {
            "widgetType": "appGroup", "pageId": page_id, "title": group_title, "components": [component],
        }),
    ], text="Saved the settings app group.")
    try:
        native_call("http", "configReplace", {"config": configured})
        before = native_call("http", "configGet")["config"]["launcher"]["home"]["pages"]
        agent.send(identifier, "Group the settings apps on my main Home.")
        rows = agent.wait_turn(identifier)
        results = [json.loads(json.loads(row["blocks"])[0]["text"]) for row in rows if row["role"] == "tool"]
        assert len(records) == 3
        assert len(results) == 2
        assert all(not result.get("isError") for result in results)
        assert component in {app["component"] for app in results[0]["structuredContent"]["apps"]}
        created = results[-1]["structuredContent"]
        assert created["title"] == group_title
        assert created["type"] == "appGroup"
        assert created["memberCount"] == 1
        tool = next(
            tool["function"] for tool in records[0]["body"]["tools"]
            if tool["function"]["name"] == "dikciz_add_widget"
        )
        assert tool["parameters"]["properties"]["components"]["items"]["type"] == "string"
        after = native_call("http", "configGet")["config"]["launcher"]["home"]["pages"]
        assert after[:-1] == before[:-1]
        group = after[-1]["widgets"][0]
        assert group["components"] == created["components"]
        assert group["cell"] == created["cell"]
        assert len(after[-1]["widgets"]) == 1
    finally:
        agent.cleanup(identifier)
        native_call("http", "configReplace", {"config": original})


def test_chat_authoring_typed_widget_from_builtin_skill(websocket_control, provider_endpoint):
    original = native_call("http", "configGet")["config"]
    configured = agent.fixture_document()
    page_id = "chat-typed-" + uuid.uuid4().hex[:8]
    append_empty_page(configured, page_id, "Typed widget target", 7, 7)
    native_call("http", "configReplace", {"config": configured})
    configured = native_call("http", "configGet")["config"]
    assert configured["launcher"]["home"]["selectedPageId"] != page_id
    endpoint, script, records = provider_endpoint
    _, identifier = agent.conversation(endpoint, "anthropic", maximumRounds=6)
    values = {
        "title": "Typed counter",
        "html": '<p id="counter">Fixture</p>',
        "css": "body{color:orange}",
        "javascript": "window.addEventListener('dikciz-ready',()=>selfWidget.patchState({ready:true}));",
    }

    def widget_args(body):
        result = next(part for message in body["messages"] for part in message.get("content", [])
                      if isinstance(part, dict) and part.get("tool_use_id") == "fixture-call-1")
        return {"widgetAddress": json.loads(result["content"][0]["text"])["structuredContent"]["address"]}

    def patch_args(body):
        address = widget_args(body)["widgetAddress"]
        return {"sourceWidgetAddress": address, "actions": [
            {"type": "patchWidget", "widgetAddress": address, "values": values},
        ]}

    script.update(wrapper_calls=[
        ("dikciz_home_get", {}),
        ("dikciz_add_widget", {"widgetType": "html", "pageId": page_id}),
        ("dikciz_automation_dispatch", patch_args),
        ("dikciz_widget_get", widget_args),
    ], text="Fixture checked its saved widget.")
    try:
        agent.chat("show", conversationId=identifier)
        wait_for_physical_description_bounds("Chats")
        agent.send(identifier, "Use $dikciz-write-widget to create one counter on " + page_id)
        rows = agent.wait_turn(identifier)
        assert len(records) == 5
        first = records[0]["body"]
        assert "@builtin/dikciz-write-widget/SKILL.md" in json.dumps(first["system"])
        assert "Author an HTML widget" in json.dumps(first["system"])
        schemas = {tool["name"]: tool["input_schema"] for tool in first["tools"]}
        assert schemas["dikciz_add_widget"]["required"] == ["widgetType"]
        assert schemas["dikciz_add_widget"]["properties"]["pageId"]["type"] == "string"
        dispatch = schemas["dikciz_automation_dispatch"]["properties"]["actions"]
        assert dispatch["type"] == "array" and dispatch["items"]["type"] == "object"
        pending = [json.loads(json.loads(row["blocks"])[0]["text"]) for row in rows if row["role"] == "tool"]
        assert len(pending) == 4 and all(not row.get("isError") for row in pending)
        widget_result = pending[-1]["structuredContent"]
        assert widget_result["title"] == values["title"]
        assert widget_result["source"] == {key: values[key] for key in ("html", "css", "javascript")}
        agent.chat("hide")
        deadline = time.monotonic() + agent.TURN_TIMEOUT
        while time.monotonic() < deadline:
            saved = native_call("http", "configGet")["config"]
            pages = saved["launcher"]["home"]["pages"]
            assert pages[:-1] == configured["launcher"]["home"]["pages"][:-1]
            actual = pages[-1]["widgets"]
            assert len(actual) == 1
            if actual[0]["state"].get("ready"):
                break
            time.sleep(0.2)
        assert all(actual[0][key] == value for key, value in values.items())
        assert actual[0]["state"] == {"ready": True}
        agent.assert_tool_pairing(records[-1]["body"])
    finally:
        agent.cleanup(identifier)
        native_call("http", "configReplace", {"config": original})
