"""Assistant authoring and causal history through the production Android host."""

import concurrent.futures
import json
import shlex
import time
import uuid

import pytest

from . import *
from .test_native_data import native_call, PROTOCOLS
from .test_custom_apps import app_factory, wait_state
from .test_remote_tokens import token_owner, create as token_create, call as token_call, catalogue as token_catalogue
from .test_chat_agent import provider_endpoint, conversation, chat, send, wait_turn, cleanup
from .test_assistant_platform import write_text, remove_path, ASSISTANT_ROOT
from .test_operations_ux import tap, capture


@pytest.fixture
def workbench_files(websocket_control):
    name = "workbench-" + uuid.uuid4().hex[:12]
    paths = []
    def candidate(kind="skill"):
        path = {"skill": f"skills/{name}/SKILL.md", "agent": f"agents/{name}.md", "rule": f"rules/{name}.md"}[kind]
        paths.append(path)
        text = "# Test rule\n\nDescribe the observed outcome. ☃" if kind == "rule" else f'---\nname: "{name}"\ndescription: "Inspect a private emulator fixture."\n---\n\nRead the current tool schema. ☃'
        return path, text
    yield candidate
    for path in paths:
        device_command("rm -f -- " + shlex.quote(ASSISTANT_ROOT + "/" + path))
        device_command("rm -f -- " + shlex.quote(ASSISTANT_ROOT + "/" + path + ".disabled"))
        if path.startswith("skills/"):
            device_command("rm -rf -- " + shlex.quote(ASSISTANT_ROOT + "/" + path.rsplit("/", 1)[0]))


def library_read(path, protocol="http"):
    return native_call(protocol, "assistantLibrary", {"action": "read", "path": path})


@pytest.mark.parametrize("protocol", PROTOCOLS)
@pytest.mark.parametrize("kind", ["skill", "agent", "rule"])
def test_automation_workbench_authoring_lifecycle(workbench_files, protocol, kind):
    path, text = workbench_files(kind)
    assert native_call(protocol, "assistantLibraryValidate", {"path": path, "text": text})["valid"]
    saved = native_call(protocol, "assistantLibraryManage", {"action": "save", "path": path, "text": text, "expectedRevision": ""})
    assert library_read(path, protocol)["text"] == text
    assert library_read(path)["revision"] == saved["revision"]
    native_call(protocol, "assistantLibraryManage", {"action": "save", "path": path, "text": text + " changed", "expectedRevision": ""}, error="assistant_revision_conflict")
    saved = native_call(protocol, "assistantLibraryManage", {"action": "save", "path": path, "text": text + " changed", "expectedRevision": saved["revision"]})
    disabled = native_call(protocol, "assistantLibraryManage", {"action": "setEnabled", "path": path, "enabled": False, "expectedRevision": saved["revision"]})
    assert disabled["path"] == path + ".disabled"
    assert path not in [row["path"] for row in native_call(protocol, "assistantLibrary", {"action": "list"})["entries"]]
    assert any(row["path"] == disabled["path"] and not row["enabled"] for row in native_call(protocol, "assistantLibrary", {"action": "inspect"})["entries"])
    native_call(protocol, "assistantLibraryManage", {"action": "setEnabled", "path": disabled["path"], "enabled": True, "expectedRevision": saved["revision"]})
    assert library_read(path)["text"] == text + " changed"
    native_call(protocol, "assistantLibraryManage", {"action": "delete", "path": path, "expectedRevision": saved["revision"]})
    native_call(protocol, "assistantLibrary", {"action": "read", "path": path}, error="assistant_file_missing")


@pytest.mark.parametrize("path", ["../AGENTS.md", "/sdcard/AGENTS.md", "rules/../AGENTS.md", "rules\\evil.md", "@builtin/dikciz-write-skill/SKILL.md", "config.json"])
def test_automation_workbench_authoring_paths(workbench_files, path):
    native_call("http", "assistantLibraryManage", {"action": "save", "path": path, "text": "Unsafe", "expectedRevision": ""}, error="validation_failed")


@pytest.mark.parametrize("text", ["", "---\nname: wrong-name\ndescription: Bad.\n---\nBody", "---\nname: broken\n---\nBody", "---\nname: broken\ndescription: Bad.\nhooks: run\n---\nBody", "x" * 65537], ids=["empty", "wrong-name", "missing-description", "hooks", "oversize"])
def test_automation_workbench_authoring_invalid_is_atomic(workbench_files, text):
    path, original = workbench_files()
    saved = native_call("http", "assistantLibraryManage", {"action": "save", "path": path, "text": original, "expectedRevision": ""})
    native_call("http", "assistantLibraryManage", {"action": "save", "path": path, "text": text, "expectedRevision": saved["revision"]}, error=("validation_failed", "assistant_library_too_large"))
    assert library_read(path)["text"] == original


def test_automation_workbench_authoring_repair_support_and_race(workbench_files):
    path, text = workbench_files()
    write_text(ASSISTANT_ROOT + "/" + path, "broken")
    reference = path.rsplit("/", 1)[0] + "/references/check.md"
    write_text(ASSISTANT_ROOT + "/" + reference, "Keep support.")
    assert any(row["path"] == path and not row["valid"] for row in native_call("http", "assistantLibrary", {"action": "inspect"})["entries"])
    revision = library_read(path)["revision"]
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        futures = [pool.submit(native_call, "http", "assistantLibraryManage", {"action": "save", "path": path, "text": text + str(index), "expectedRevision": revision}, allowed_errors=("assistant_revision_conflict",)) for index in range(2)]
        results = [future.result() for future in futures]
    assert sum(result.get("state") == "saved" for result in results) == 1
    assert sum(result.get("code") == "assistant_revision_conflict" for result in results) == 1
    native_call("http", "assistantLibraryManage", {"action": "delete", "path": path, "expectedRevision": library_read(path)["revision"]})
    assert library_read(reference)["text"] == "Keep support."


def test_automation_workbench_bundled_guides(websocket_control):
    rows = native_call("http", "assistantLibrary", {"action": "list"})["builtins"]
    assert {row["name"] for row in rows} == {"dikciz-write-skill", "dikciz-write-agent", "dikciz-write-rule", "dikciz-debug-automation"}
    for row in rows:
        assert row["name"] in library_read(row["path"])["text"]


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_automation_workbench_scoped_read_cannot_write(workbench_files, token_owner, protocol):
    secret, _ = token_create(token_owner, ["command.assistantLibrary.action.inspect"])
    assert "dikciz_assistant_library_manage" not in token_catalogue(secret)
    assert "entries" in token_call(secret, "assistantLibrary", {"action": "inspect"}, protocol=protocol)
    path, text = workbench_files("rule")
    token_call(secret, "assistantLibraryManage", {"action": "save", "path": path, "text": text, "expectedRevision": ""}, protocol=protocol, error="remote_permission_denied")


@pytest.mark.parametrize("language", ["javascript", "lua"])
@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_automation_workbench_simulation_intercepts_effects(app_factory, protocol, language):
    if language == "javascript":
        source = "function onEvent(event){var r=context.request({type:'deviceInfo'});context.request({type:'fileWrite',path:'/sdcard/Dikciz/workbench-must-not-exist',dataBase64:'eA=='});return {result:{seen:r.result.marker},actions:[{type:'patchState',values:{changed:true}},{type:'emitEvent',event:'fixture.noeffect',payload:{}}]};}"
    else:
        source = "function on_event(event) local r=context.request({type='deviceInfo'});context.request({type='fileWrite',path='/sdcard/Dikciz/workbench-must-not-exist',dataBase64='eA=='});return {result={seen=r.result.marker},actions={{type='patchState',values={changed=true}},{type='emitEvent',event='fixture.noeffect',payload={}}}} end"
    identifier, _ = app_factory(source=source, language=language)
    before = native_call("http", "customAppRead", {"appId": identifier})["state"]
    result = native_call(protocol, "automationSimulate", {"appId": identifier, "event": {"type": "fixture.test", "payload": {}}, "replies": [{"type": "deviceInfo", "result": {"marker": 731}}]})
    assert result["simulated"] and result["outcome"] == "completed", result
    assert result["result"] == {"seen": 731}
    assert [command["type"] for command in result["commands"]] == ["deviceInfo", "fileWrite"]
    assert result["actions"] == ["patchState", "emitEvent"]
    assert result["actionDetails"][0] == {"type": "patchState", "values": {"changed": True}}
    assert native_call("http", "customAppRead", {"appId": identifier})["state"] == before
    native_call("http", "fileRead", {"path": "/sdcard/Dikciz/workbench-must-not-exist"}, error="file_not_found")


def wait_history(**fields):
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        result = native_call("http", "automationHistory", fields)
        if result["items"]:
            return result
        time.sleep(.1)
    raise AssertionError("History was not recorded")


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_automation_workbench_trace_and_recorded_simulation(app_factory, protocol):
    name = "fixture.trace." + uuid.uuid4().hex[:10]
    source = "function onEvent(event){context.request({type:'deviceInfo'});return {actions:[{type:'patchState',values:{count:(context.state.count||0)+1}}]};}"
    identifier, _ = app_factory(source=source, events=[name])
    native_call("http", "automationDispatch", {"sourceWidgetAddress": "1H1V-welcome", "actions": [{"type": "emitEvent", "event": name, "payload": {"marker": 147}}]})
    wait_state(identifier, "count", 1)
    event = wait_history(kind="event", name=name)["items"][0]
    rows = native_call(protocol, "automationHistory", {"traceId": event["traceId"]})["items"]
    app = next(row for row in rows if row["kind"] == "app" and row["name"] == identifier)
    command = next(row for row in rows if row["kind"] == "command" and row["name"] == "deviceInfo")
    assert app["parentId"] == event["id"] and command["parentId"] == app["id"]
    assert all(row["outcome"] == "completed" for row in rows)
    assert native_call(protocol, "automationHistoryEvent", {"recordId": event["id"]})["payload"] == {"marker": 147}
    result = native_call(protocol, "automationSimulate", {"appId": identifier, "recordId": event["id"]})
    assert result["simulated"] and result["outcome"] == "completed"
    assert native_call("http", "customAppRead", {"appId": identifier})["state"] == {"count": 1}


def test_automation_workbench_history_pages_and_validation(websocket_control):
    name = "fixture.pages." + uuid.uuid4().hex[:10]
    for index in range(13):
        native_call("http", "automationDispatch", {"sourceWidgetAddress": "1H1V-welcome", "actions": [{"type": "emitEvent", "event": name, "payload": {"index": index}}]})
    deadline = time.monotonic() + 20
    while wait_history(kind="event", name=name)["total"] != 13:
        assert time.monotonic() < deadline
    ids = []
    for offset, count, more in ((0, 5, True), (5, 5, True), (10, 3, False), (13, 0, False), (9999, 0, False)):
        page = native_call("http", "automationHistory", {"kind": "event", "name": name, "limit": 5, "offset": offset})
        assert page["total"] == 13 and len(page["items"]) == count and page["hasMore"] is more
        ids.extend(row["id"] for row in page["items"])
    assert len(set(ids)) == 13
    assert native_call("http", "automationHistory", {"name": "' OR 1=1 --"})["items"] == []
    for fields in ({"limit": 0}, {"limit": 201}, {"offset": -1}, {"offset": None}):
        native_call("http", "automationHistory", fields, error="validation_failed")
    valid = {"source": "function onEvent(event){return {actions:[]};}", "event": {"type": "fixture.sample", "payload": {}}}
    for update in ({"appId": "missing"}, {"event": None}, {"event": {"type": 4}}, {"replies": [{"type": "deviceInfo"}]}, {"language": "python"}):
        native_call("http", "automationSimulate", {**valid, **update}, error="validation_failed")
    result = native_call("http", "automationSimulate", {**valid, "source": "function onEvent(event){ throw new Error('fixture'); }"})
    assert result["outcome"] == "failed" and result["commands"] == []


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
def test_automation_workbench_chat_read_only(workbench_files, provider_endpoint, provider):
    endpoint, script, _ = provider_endpoint
    _, identifier = conversation(endpoint, provider)
    path, text = workbench_files("rule")
    try:
        chat("configure", conversationId=identifier, settings={"readOnly": True})
        request = {"action": "save", "path": path, "text": text, "expectedRevision": ""}
        script.update(wrapper_calls=[("phone_tools", {"query": "assistant library"}),
            ("phone_call", {"name": "dikciz_assistant_library_manage", "arguments_json": json.dumps(request)})], tools=[], text="Read-only checked.")
        send(identifier)
        rows = wait_turn(identifier)
        results = [json.loads(json.loads(row["blocks"])[0]["text"]) for row in rows if row["role"] == "tool"]
        assert "dikciz_assistant_library_manage" not in {tool["name"] for tool in results[0]["tools"]}
        assert all(tool["annotations"]["readOnlyHint"] for tool in results[0]["tools"])
        assert results[1]["isError"] and results[1]["structuredContent"]["code"] == "read_only_violation"
        native_call("http", "assistantLibrary", {"action": "read", "path": path}, error="assistant_file_missing")
    finally:
        cleanup(identifier)


def test_automation_workbench_rendered_manager_and_debugger(workbench_files, websocket_control):
    path, text = workbench_files("rule")
    native_call("http", "assistantLibraryManage", {"action": "save", "path": path, "text": text, "expectedRevision": ""})
    restore_system_home()
    tap(websocket_control, "launcher:controls")
    tap(websocket_control, "launcher:controls:assistant-library")
    wait_for_snapshot_node(websocket_control, "operations:library:new")
    capture("library-manager")
    tap(websocket_control, "operations:library:new")
    tap(websocket_control, "operations:filter:2")
    wait_for_snapshot_node(websocket_control, "operations:editor:text")
    AUTOMATION.request(websocket_control, "setText", semanticId="operations:editor:text", text="")
    tap(websocket_control, "operations:dialog:neutral")
    time.sleep(.5)
    assert wait_for_snapshot_node(websocket_control, "operations:editor:text")
    capture("library-editor-invalid")
    device_command("input keyevent 4")
    device_command("input keyevent 4")
    restore_system_home()
    tap(websocket_control, "launcher:controls")
    tap(websocket_control, "launcher:controls:automation-debugger")
    workbench_wait_history_row(websocket_control)
    capture("automation-debugger")
    tap(websocket_control, workbench_wait_history_row(websocket_control)["semanticId"])
    wait_for_snapshot_node(websocket_control, "operations:dialog:cancel")
    capture("automation-trace")
    device_command("input keyevent 4")
    device_command("input keyevent 4")


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
def test_automation_workbench_chat_authors_and_reloads_rule(workbench_files, provider_endpoint, provider):
    endpoint, script, records = provider_endpoint
    _, identifier = conversation(endpoint, provider)
    path, text = workbench_files("rule")
    try:
        script.update(tools=[("dikciz_assistant_library_manage", {"action": "save", "path": path, "text": text, "expectedRevision": ""})], text="Saved the instruction.")
        send(identifier)
        rows = wait_turn(identifier)
        assert library_read(path)["text"] == text
        system = records[-1]["body"].get("system") if provider == "anthropic" else [item for item in records[-1]["body"]["messages"] if item["role"] == "system"]
        assert "Describe the observed outcome." in json.dumps(system, ensure_ascii=False)
        history = wait_history(kind="chat", name=identifier)["items"][0]
        trace = native_call("http", "automationHistory", {"traceId": history["traceId"]})["items"]
        write = next(row for row in trace if row["name"] == "assistantLibraryManage" and row["kind"] == "command")
        assert write["parentId"] == history["id"]
    finally:
        cleanup(identifier)


def test_automation_workbench_expired_payload_keeps_metadata(websocket_control):
    original = native_call("http", "eventJournalStatus")
    name = "fixture.expiry." + uuid.uuid4().hex[:10]
    try:
        native_call("http", "eventJournalConfigure", {"maximumEvents": 1})
        def publish(value):
            native_call("http", "automationDispatch", {"sourceWidgetAddress": "1H1V-welcome", "actions": [{"type": "emitEvent", "event": name + value, "payload": {}}]})
            return wait_history(kind="event", name=name + value)["items"][0]
        first = publish(".first")
        publish(".second")
        assert native_call("http", "automationHistory", {"recordId": first["id"]})["total"] == 1
        native_call("http", "automationHistoryEvent", {"recordId": first["id"]}, error="event_payload_expired")
    finally:
        native_call("http", "eventJournalConfigure", {key: original[key] for key in ("maximumEvents", "bufferBytes", "retentionMinutes")})


def test_automation_workbench_library_unreadable_recovery(workbench_files):
    path, _ = workbench_files("rule")
    native_call("http", "fileMkdir", {"path": ASSISTANT_ROOT + "/rules", "parents": True})
    native_call("http", "fileWrite", {"path": ASSISTANT_ROOT + "/" + path, "dataBase64": "/w==", "overwrite": True})
    row = next(row for row in native_call("http", "assistantLibrary", {"action": "inspect"})["entries"] if row["path"] == path)
    assert not row["valid"] and len(row["revision"]) == 64
    native_call("http", "assistantLibraryManage", {"action": "setEnabled", "path": path, "enabled": False, "expectedRevision": row["revision"]})
    native_call("http", "assistantLibrary", {"action": "list"})
    native_call("http", "assistantLibraryManage", {"action": "delete", "path": path + ".disabled", "expectedRevision": row["revision"]})


@pytest.mark.parametrize("layout", ["landscape", "large-text"])
def test_automation_workbench_alternate_layout_editor(workbench_files, websocket_control, layout):
    connection = None
    try:
        if layout == "landscape":
            device_command("settings put system accelerometer_rotation 0")
            device_command("settings put system user_rotation 1")
        else:
            device_command("settings put system font_scale 1.5")
        time.sleep(1)
        restore_system_home()
        connection = wait_for_restarted_websocket_control()
        tap(connection, "launcher:controls")
        tap(connection, "launcher:controls:assistant-library")
        wait_for_snapshot_node(connection, "operations:library:new")
        capture("library-" + layout)
        tap(connection, "operations:library:new")
        tap(connection, "operations:filter:2")
        wait_for_snapshot_node(connection, "operations:editor:text")
        AUTOMATION.request(connection, "setText", semanticId="operations:editor:text", text="Draft survives layout changes.")
        tap(connection, "operations:editor:text")
        capture("editor-" + layout)
        tap(connection, "operations:dialog:neutral")
        time.sleep(.5)
        wait_for_snapshot_node(connection, "operations:dialog:confirm")
        device_command("input keyevent 4")
        device_command("input keyevent 4")
    finally:
        if connection is not None:
            connection.close()
        device_command("settings put system user_rotation 0")
        device_command("settings put system accelerometer_rotation 1")
        device_command("settings put system font_scale 1.0")
        restore_system_home()


def test_automation_workbench_real_process_recovery(websocket_control):
    from .test_capability_expansion import wait_native_control
    name = "fixture.restart." + uuid.uuid4().hex[:10]
    native_call("http", "automationDispatch", {"sourceWidgetAddress": "1H1V-welcome", "actions": [{"type": "emitEvent", "event": name, "payload": {"retained": True}}]})
    event = wait_history(kind="event", name=name)["items"][0]
    pool = concurrent.futures.ThreadPoolExecutor(max_workers=1)
    future = pool.submit(native_call, "http", "shell", {"command": "sleep 10", "root": False, "timeoutMilliseconds": 30000})
    try:
        running = wait_history(kind="command", name="shell", outcome="running")["items"][0]
        run_device_operation("app-stop", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
    finally:
        run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
        restored = wait_for_restarted_websocket_control()
        restored.close()
        wait_native_control("http")
        future.exception(timeout=45)
        pool.shutdown()
    retained = native_call("http", "automationHistory", {"recordId": running["id"]})["items"][0]
    assert retained["outcome"] == "interrupted" and retained["code"] == "process_restarted"
    assert native_call("http", "automationHistoryEvent", {"recordId": event["id"]})["payload"] == {"retained": True}


@pytest.mark.parametrize("language", ["javascript", "lua"])
def test_automation_workbench_library_script_binding(workbench_files, app_factory, language):
    path, text = workbench_files("rule")
    request = json.dumps({"type": "assistantLibraryManage", "action": "save", "path": path, "text": text, "expectedRevision": ""})
    if language == "javascript":
        source = "function onEvent(event){var r=context.request(" + request + ");return {result:{saved:r.result.state}};}"
    else:
        source = "function on_event(event) local r=context.request(" + "{type='assistantLibraryManage',action='save',path='" + path + "',text='Binding rule.',expectedRevision=''}" + ");return {result={saved=r.result.state}} end"
    identifier, _ = app_factory(source=source, language=language)
    result = native_call("http", "customAppEvent", {"appId": identifier, "event": "fixture.author"})
    assert result["result"] == {"saved": "saved"}
    assert library_read(path)["text"] == (text if language == "javascript" else "Binding rule.")


def test_automation_workbench_documentation_capture(websocket_control):
    from .test_custom_apps import app_fields
    examples = {
        "skills/inspect-device/SKILL.md": '---\nname: "inspect-device"\ndescription: "Inspect battery, memory and storage when asked about device health."\n---\n\nDiscover the device-state tool and read its schema.\nReport observed battery, memory and storage.\nExplain unavailable measurements. Do not change settings.\n',
        "agents/device-reader.md": '---\nname: "device-reader"\ndescription: "Inspect device health without changing the phone."\nreadOnly: true\ntools: [dikciz_device_state, dikciz_device_info]\n---\n\nReport observed measurements and their limits.\n',
        "rules/precise-results.md": '# Precise results\n\nReport observed results. Explain missing measurements.\n',
    }
    assert native_call("http", "assistantLibrary", {"action": "list"})["entries"] == []
    identifier = "example-device-check"
    fields = app_fields(identifier, source="function onEvent(event){if(event.type!=='example.inspect')return {actions:[]};context.request({type:'deviceInfo'});return {status:'Device info read',actions:[]};}", events=["example.inspect"])
    fields["manifest"]["title"] = "Device check"
    native_call("http", "customAppInstall", fields)
    try:
        for path, text in examples.items():
            native_call("http", "assistantLibraryManage", {"action": "save", "path": path, "text": text, "expectedRevision": ""})
        restore_system_home()
        tap(websocket_control, "launcher:controls")
        capture("guide-controls")
        tap(websocket_control, "launcher:controls:assistant-library")
        wait_for_snapshot_node(websocket_control, "operations:library:new")
        capture("guide-library")
        entries = native_call("http", "assistantLibrary", {"action": "inspect"})["entries"]
        instruction_id = next(item["id"] for item in entries if item["path"] == "skills/inspect-device/SKILL.md")
        tap(websocket_control, "operations:library:item:" + instruction_id)
        wait_for_snapshot_node(websocket_control, "operations:editor:text")
        capture("guide-editor")
        tap(websocket_control, "operations:dialog:cancel")
        device_command("input keyevent 4")
        native_call("http", "automationDispatch", {"sourceWidgetAddress": "1H1V-welcome", "actions": [{"type": "emitEvent", "event": "example.inspect", "payload": {"purpose": "Read device information"}}]})
        wait_history(kind="app", name=identifier)
        restore_system_home()
        tap(websocket_control, "launcher:controls")
        tap(websocket_control, "launcher:controls:automation-debugger")
        workbench_wait_history_row(websocket_control)
        capture("guide-history")
        deadline = time.monotonic() + 20
        while True:
            snapshot = AUTOMATION.request(websocket_control, "snapshot")
            matches = [node for node in snapshot["nodes"] if node.get("semanticId", "").startswith("operations:history:row:") and "example.inspect" in (node.get("text") or "")]
            if matches:
                break
            assert time.monotonic() < deadline
            time.sleep(.2)
        tap(websocket_control, matches[0]["semanticId"])
        wait_for_snapshot_node(websocket_control, "operations:dialog:confirm")
        capture("guide-trace")
        tap(websocket_control, "operations:dialog:cancel")
        device_command("input keyevent 4")
    finally:
        native_call("http", "customAppControl", {"appId": identifier, "action": "remove", "confirm": True})
        for path in examples:
            native_call("http", "assistantLibraryManage", {"action": "delete", "path": path, "expectedRevision": library_read(path)["revision"]})


def test_automation_workbench_dialog_builtin_guide_scroll(websocket_control):
    entries = native_call("http", "assistantLibrary", {"action": "inspect"})["entries"]
    instruction_id = next(item["id"] for item in entries if item["path"] == "@builtin/dikciz-write-skill/SKILL.md")
    restore_system_home()
    tap(websocket_control, "launcher:controls")
    tap(websocket_control, "launcher:controls:assistant-library")
    wait_for_snapshot_node(websocket_control, "operations:library:new")
    tap(websocket_control, "operations:library:item:" + instruction_id)
    wait_for_snapshot_node(websocket_control, "operations:editor:text")
    before = AUTOMATION.request(websocket_control, "scrollBy", deltaY=0)
    after = AUTOMATION.request(websocket_control, "scrollBy", deltaY=1200)
    assert after["y"] > before["y"]
    capture("builtin-guide-scrolled")
    tap(websocket_control, "operations:dialog:cancel")
    device_command("input keyevent 4")


def workbench_wait_history_row(connection):
    deadline = time.monotonic() + 20
    while True:
        snapshot = AUTOMATION.request(connection, "snapshot")
        rows = [node for node in snapshot["nodes"] if node.get("semanticId", "").startswith("operations:history:row:")]
        if rows:
            return rows[0]
        assert time.monotonic() < deadline
        time.sleep(.2)


def workbench_open_history_kind(connection, index):
    restore_system_home()
    tap(connection, "launcher:controls")
    tap(connection, "launcher:controls:automation-debugger")
    wait_for_snapshot_node(connection, "operations:filter")
    tap(connection, "operations:filter")
    tap(connection, "operations:filter:" + str(index))
    workbench_wait_history_row(connection)


def test_automation_workbench_dialog_trace_error_without_trigger(websocket_control):
    native_call("http", "customAppRead", {"appId": "workbench-missing-app"}, error="custom_app_not_found")
    workbench_open_history_kind(websocket_control, 4)
    deadline = time.monotonic() + 20
    while True:
        snapshot = AUTOMATION.request(websocket_control, "snapshot")
        rows = [node for node in snapshot["nodes"] if node.get("semanticId", "").startswith("operations:history:row:") and "customAppRead" in (node.get("text") or "") and "rejected" in (node.get("text") or "")]
        if rows:
            break
        assert time.monotonic() < deadline
        time.sleep(.2)
    tap(websocket_control, rows[0]["semanticId"])
    snapshot = wait_for_snapshot_node(websocket_control, "operations:dialog:message")
    assert "custom_app_not_found" in AUTOMATION.require_node(snapshot, "operations:dialog:message")["text"]
    tap(websocket_control, "operations:dialog:cancel")
    device_command("input keyevent 4")


def test_automation_workbench_dialog_trace_paging(app_factory, websocket_control):
    identifier, _ = app_factory(source="function onEvent(event){if(event.type!=='fixture.page')return {actions:[]};for(var i=0;i<55;i++)context.request({type:'eventJournalStatus'});return {actions:[]};}")
    native_call("http", "customAppEvent", {"appId": identifier, "event": "fixture.page"})
    workbench_open_history_kind(websocket_control, 3)
    deadline = time.monotonic() + 20
    while True:
        snapshot = AUTOMATION.request(websocket_control, "snapshot")
        rows = [node for node in snapshot["nodes"] if node.get("semanticId", "").startswith("operations:history:row:") and identifier in (node.get("text") or "")]
        if rows:
            break
        assert time.monotonic() < deadline
        time.sleep(.2)
    tap(websocket_control, rows[0]["semanticId"])
    wait_for_snapshot_node(websocket_control, "operations:trace:older")
    tap(websocket_control, "operations:trace:older")
    snapshot = wait_for_snapshot_node(websocket_control, "operations:dialog:message")
    assert "Records 51 to" in AUTOMATION.require_node(snapshot, "operations:dialog:message")["text"]
    tap(websocket_control, "operations:trace:newer")
    snapshot = wait_for_snapshot_node(websocket_control, "operations:dialog:message")
    assert "Records 1 to 50" in AUTOMATION.require_node(snapshot, "operations:dialog:message")["text"]
    tap(websocket_control, "operations:dialog:cancel")
    device_command("input keyevent 4")


def test_automation_workbench_omitted_payload(websocket_control):
    from .test_event_replay import emit_replay, await_replay
    status = native_call("http", "eventJournalStatus")
    name = "fixture.omitted." + uuid.uuid4().hex[:10]
    emit_replay(websocket_control, name, 0, ["\u2603" * 8000] * 3)
    assert await_replay(status, name, 1)["omittedEvents"] == 1
    record = wait_history(kind="event", name=name)["items"][0]
    native_call("http", "automationHistoryEvent", {"recordId": record["id"]}, error="event_payload_omitted")
    assert native_call("http", "automationHistory", {"recordId": record["id"]})["total"] == 1
