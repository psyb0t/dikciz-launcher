"""Explicit operation grants and nested execution through real device transports."""

import json
import shutil
import time
import uuid
import pytest
from . import *
from .test_remote_tokens import token_owner, create, call, catalogue, admin, bearer, DENIED, PROTOCOLS, protocol_meta
from .ui import direct_tap, wait_for_physical_description_bounds


def test_granular_tokens_complete_catalogue(token_owner):
    tools = catalogue(token_owner)
    grants = admin(token_owner, "catalog")["permissions"]
    identifiers = [grant["id"] for grant in grants]
    assert len(identifiers) == len(set(identifiers))
    for tool in tools.values():
        definition = tool["_meta"]["dikciz"]
        assert "command." + definition["command"] in identifiers
        assert definition["operationGrants"]
        assert all(grant["description"] and grant["mcpTool"] == tool["name"] for grant in definition["operationGrants"])
    (ARTIFACT_DIRECTORY / "mcp-command-discovery.json").write_text(json.dumps({"tools": list(tools.values())}))
    (ARTIFACT_DIRECTORY / "remote-grant-discovery.json").write_text(json.dumps({"permissions": grants}))


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_granular_tokens_command_and_action(token_owner, protocol):
    token, _ = create(token_owner, ["command.deviceState", "command.fileManage.action.stat"])
    assert isinstance(call(token, "deviceState", protocol=protocol)["screenInteractive"], bool)
    assert "dikciz_file_manage" in catalogue(token)
    assert catalogue(token)["dikciz_file_manage"]["inputSchema"]["properties"]["action"]["enum"] == ["stat"]
    path = "/sdcard/Download/granular-" + uuid.uuid4().hex + ".txt"
    call(token_owner, "fileWrite", {"path": path, "dataBase64": "RXhhbXBsZSBkYXRh"})
    try:
        call(token, "fileManage", {"action": "stat", "path": path}, protocol)
        call(token, "fileManage", {"action": "delete", "path": path}, protocol, DENIED)
        call(token, "fileManage", {"path": path}, protocol, DENIED)
        call(token_owner, "fileManage", {"action": "stat", "path": path})
        call(token, "clipboardSet", {"text": "Example text", "autoRequestPermissions": True}, protocol, DENIED)
        call(token, "cameraCapture", {}, protocol, DENIED)
    finally:
        call(token_owner, "fileManage", {"action": "delete", "path": path})


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_granular_tokens_resource_union(token_owner, protocol):
    token, _ = create(token_owner, ["contacts.read", "command.contentQuery.resource.sms"])
    resources = catalogue(token)["dikciz_content_query"]["inputSchema"]["properties"]["resource"]["enum"]
    assert set(resources) == {"contacts", "phones", "emails", "rawContacts", "contactData", "sms"}
    call(token, "contentQuery", {"resource": "calendarEvents"}, protocol, DENIED)
    call(token, "contentWrite", {"resource": "contactData", "operation": "delete", "id": 1}, protocol, DENIED)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_granular_tokens_jobs_keep_authority(token_owner, protocol):
    permissions = ["command.jobStart", "command.jobStatus", "command.jobsList", "command.jobCancel", "command.deviceState"]
    first, first_id = create(token_owner, permissions)
    second, _ = create(token_owner, permissions)
    request = {"command": {"type": "deviceState"}, "idempotencyKey": "example-" + uuid.uuid4().hex}
    call(first, "jobStart", {"command": {"type": "remoteTokens", "action": "create", "name": "Denied", "permissions": ["owner"]}, "idempotencyKey": "auth-" + uuid.uuid4().hex}, protocol, "validation_failed")
    one = call(first, "jobStart", request, protocol)
    two = call(second, "jobStart", request, protocol)
    assert one["jobId"] != two["jobId"]
    assert call(first, "jobStart", request, protocol)["jobId"] == one["jobId"]
    deadline = time.monotonic() + 20
    while True:
        status = call(first, "jobStatus", {"jobId": one["jobId"]}, protocol)
        if status["state"] not in ("queued", "running"):
            break
        assert time.monotonic() < deadline, status
        time.sleep(.1)
    assert status["state"] == "completed", status
    assert status["actorId"] == first_id
    assert status["response"]["type"] == "result"
    call(second, "jobStatus", {"jobId": one["jobId"]}, protocol, DENIED)
    call(second, "jobCancel", {"jobId": one["jobId"]}, protocol, DENIED)
    page = call(first, "jobsList", {"limit": 1}, protocol)
    assert page["total"] == 1 and page["items"][0]["jobId"] == one["jobId"]
    assert call(first, "jobsList", {"offset": 1}, protocol)["items"] == []
    identity = call(first, "jobStart", {"command": {"type": "remoteAccessInfo"}, "idempotencyKey": "identity-" + uuid.uuid4().hex}, protocol)
    deadline = time.monotonic() + 20
    while True:
        status = call(first, "jobStatus", {"jobId": identity["jobId"]}, protocol)
        if status["state"] not in ("queued", "running"):
            break
        assert time.monotonic() < deadline, status
        time.sleep(.1)
    assert status["state"] == "completed", status
    assert status["response"]["result"]["id"] == first_id
    assert status["response"]["result"]["owner"] is False
    call(first, "jobStart", {"idempotencyKey": "denied-" + uuid.uuid4().hex, "command": {"type": "clipboardSet", "text": "Example text"}}, protocol, DENIED)


def test_granular_tokens_picker_filter_keeps_selection(token_owner):
    with bearer.connected_bearer_websocket(token_owner) as connection:
        AUTOMATION.request(connection, "tap", semanticId="remote-auth:tokens")
        wait_for_snapshot_node(connection, "remote-tokens:create")
        AUTOMATION.request(connection, "tap", semanticId="remote-tokens:create")
        wait_for_snapshot_node(connection, "remote-tokens:filter")
        AUTOMATION.request(connection, "setText", semanticId="remote-tokens:name", text="Example command client")
        AUTOMATION.request(connection, "setText", semanticId="remote-tokens:filter", text="command.cameraCapture")
        AUTOMATION.request(connection, "tap", semanticId="remote-tokens:grant:command.cameraCapture")
        AUTOMATION.request(connection, "setText", semanticId="remote-tokens:filter", text="sms.read")
        AUTOMATION.request(connection, "tap", semanticId="remote-tokens:grant:sms.read")
        AUTOMATION.request(connection, "setText", semanticId="remote-tokens:filter", text="command.cameraCapture")
        snapshot = AUTOMATION.request(connection, "snapshot")
        assert AUTOMATION.require_node(snapshot, "remote-tokens:grant:command.cameraCapture")["checked"]
        AUTOMATION.request(connection, "scrollTo", semanticId="remote-tokens:filter")
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "token-permission-search.png")
        AUTOMATION.request(connection, "tap", semanticId="remote-tokens:save")
        wait_for_snapshot_node(connection, "remote-tokens:copy")
    items = admin(token_owner, "list")["tokens"]
    item = next(row for row in items if row["name"] == "Example command client")
    assert set(item["permissions"]) == {"command.cameraCapture", "sms.read"}



def test_granular_tokens_all_operations_validate_before_effects(token_owner):
    tools = catalogue(token_owner)
    grants = ["command." + tool["_meta"]["dikciz"]["command"] for tool in tools.values()]
    token, _ = create(token_owner, grants)
    assert set(catalogue(token)) == set(tools)
    for tool in tools.values():
        command = tool["_meta"]["dikciz"]["command"]
        call(token, command, {"unexpected": True}, error="validation_failed")


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_granular_tokens_correlated_selectors(token_owner, protocol):
    token, _ = create(token_owner, ["command.systemSetting.action.get.setting.brightness", "command.systemSetting.action.set.setting.rotation"])
    schema = catalogue(token)["dikciz_system_setting"]["inputSchema"]
    assert len(schema["allOf"][0]["anyOf"]) == 2
    call(token, "systemSetting", {"action": "get", "setting": "brightness"}, protocol)
    call(token, "systemSetting", {"action": "get", "setting": "rotation"}, protocol, DENIED)
    call(token, "systemSetting", {"action": "set", "setting": "brightness", "value": 1}, protocol, DENIED)


def test_granular_tokens_large_grant_sets_remain_valid(token_owner):
    grants = [item["id"] for item in admin(token_owner, "catalog")["permissions"] if item["id"] != "owner"]
    created = [create(token_owner, grants, "Example broad client " + str(index))[0] for index in range(4)]
    assert len(admin(token_owner, "list")["tokens"]) == 4
    for token in created:
        info = call(token, "remoteAccessInfo")
        assert not info["owner"] and set(info["permissions"]) == set(grants)


def test_granular_tokens_manual_matches_discovery(token_owner):
    tools = {"tools": list(catalogue(token_owner).values())}
    permissions = admin(token_owner, "catalog")
    commands = load_script("manual_command_generator", "scripts/generate-dikciz-command-reference.py")
    grants = load_script("manual_permission_generator", "scripts/generate-dikciz-permission-reference.py")
    assert commands.render(tools) == (LAB_ROOT / "docs/command-reference.md").read_text()
    assert grants.render(permissions, tools) == (LAB_ROOT / "docs/token-permissions.md").read_text()
    for query, title, phrase in (
        ("gsub", "Lua API: Available Lua libraries", "string.gsub"),
        ("responseTimeoutMilliseconds", "JavaScript API: Command transport", "responseTimeoutMilliseconds"),
        ("fileManage stat", "Token permission reference: fileManage", "command.fileManage.action.stat"),
    ):
        page = call(token_owner, "searchQuery", {"collection": "docs", "query": query, "limit": 20})
        section = next(row for row in page["items"] if row["title"] == title)
        uri = section["uri"]
        assert uri.startswith("dikciz://docs/library/")
        status, _, response = MCP.request(bearer.mcp_arguments(), "POST", {
            "jsonrpc": "2.0", "id": 1, "method": "resources/read",
            "params": {"uri": uri, "_meta": protocol_meta()},
        }, {**bearer.mcp_headers(password=token_owner), "MCP-Protocol-Version": MCP.MCP_PROTOCOL_VERSION,
            "Mcp-Method": "resources/read", "Mcp-Name": uri})
        assert status == 200 and "result" in response, response
        text = response["result"]["contents"][0]["text"]
        assert phrase in text and len(text) <= 12000



@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_granular_tokens_batch_unions_legacy_and_explicit(token_owner, protocol):
    token, _ = create(token_owner, ["contacts.write", "command.contentBatch.resource.calendarEvents.operation.update"])
    status = call(token, "nativeDataStatus", protocol=protocol)
    event_resource = next(row for row in status["resources"] if row["resource"] == "calendarEvents")
    assert event_resource["readGranted"] is False
    assert event_resource["writableColumns"]
    operations = [
        {"resource": "rawContacts", "operation": "update", "id": 1, "values": {}},
        {"resource": "calendarEvents", "operation": "update", "id": 1, "values": {}},
    ]
    call(token, "contentBatch", {"operations": operations, "unexpected": True}, protocol, "validation_failed")
    operations[1]["operation"] = "delete"
    call(token, "contentBatch", {"operations": operations}, protocol, DENIED)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_granular_tokens_recovery_adaptive_picker(token_owner, protocol):
    try:
        device_command("settings put system font_scale 1.3")
        device_command("settings put system accelerometer_rotation 0")
        device_command("settings put system user_rotation 1")
        device_command("settings put secure show_ime_with_hard_keyboard 1")
        device_command("input keyevent 3")
        time.sleep(2)
        with bearer.connected_bearer_websocket(token_owner) as connection:
            bearer.open_remote_auth_settings(connection)
            AUTOMATION.request(connection, "tap", semanticId="remote-auth:tokens")
            wait_for_snapshot_node(connection, "remote-tokens:create")
            AUTOMATION.request(connection, "tap", semanticId="remote-tokens:create")
            wait_for_snapshot_node(connection, "remote-tokens:filter")
            state = call(token_owner, "scrollTo", {"semanticId": "remote-tokens:filter"}, protocol)
            assert state["y"] > 0 and state["range"] > state["extent"], state
            node = AUTOMATION.require_node(AUTOMATION.request(connection, "snapshot"), "remote-tokens:filter")
            assert node["visible"] and node["bounds"]["bottom"] > node["bounds"]["top"]
            call(token_owner, "tap", {"semanticId": "remote-tokens:filter"}, protocol)
            AUTOMATION.request(connection, "setText", semanticId="remote-tokens:filter", text="sms.read")
            wait_for_snapshot_node(connection, "remote-tokens:grant:sms.read")
            run_device_operation("screenshot")
            shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / ("token-permission-landscape-keyboard-" + protocol + ".png"))
            device_command("input keyevent 4")
            AUTOMATION.request(connection, "scrollTo", semanticId="remote-tokens:grant:sms.read")
            AUTOMATION.request(connection, "tap", semanticId="remote-tokens:grant:sms.read")
            assert AUTOMATION.require_node(AUTOMATION.request(connection, "snapshot"), "remote-tokens:grant:sms.read")["checked"]
            device_command("input keyevent 4")
            assert not admin(token_owner, "list")["tokens"]
    finally:
        device_command("settings put system font_scale 1.0")
        device_command("settings put system user_rotation 0")
        device_command("settings put system accelerometer_rotation 1")
        device_command("settings put secure show_ime_with_hard_keyboard 0")


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_granular_tokens_recovery_jobs_keep_sms_rules(token_owner, protocol):
    created = admin(token_owner, "create", name="Example SMS job client", permissions=["command.jobStart", "sms.send"], smsAddresses=["5550101"])
    token = created["token"]
    before = call(token_owner, "jobsList")["total"]
    call(token, "jobStart", {"idempotencyKey": "sms-" + uuid.uuid4().hex,
         "command": {"type": "smsSend", "requestKey": str(uuid.uuid4()), "recipient": "5550102", "body": "Example message"}}, protocol, DENIED)
    assert call(token_owner, "jobsList")["total"] == before


def test_granular_tokens_recovery_queued_job_rechecks_revocation(token_owner):
    token, identifier = create(token_owner, ["command.jobStart", "command.shell", "command.deviceState"])
    blockers = [call(token, "jobStart", {"command": {"type": "shell", "command": "sleep 15", "root": False, "timeoutMilliseconds": 20000},
                "idempotencyKey": "block-" + uuid.uuid4().hex}) for _ in range(2)]
    pending = call(token, "jobStart", {"command": {"type": "deviceState"}, "idempotencyKey": "queued-" + uuid.uuid4().hex})
    assert pending["state"] == "queued", pending
    admin(token_owner, "revoke", id=identifier)
    deadline = time.monotonic() + 20
    while True:
        status = call(token_owner, "jobStatus", {"jobId": pending["jobId"]})
        if status["state"] not in ("queued", "running"):
            break
        assert time.monotonic() < deadline, status
        time.sleep(.1)
    assert status["state"] == "failed", status
    assert status["response"]["code"] == "remote_auth_required", status


def test_granular_tokens_recovery_edit_picker(token_owner):
    _, identifier = create(token_owner, ["command.cameraCapture"], "Example camera client")
    with bearer.connected_bearer_websocket(token_owner) as connection:
        AUTOMATION.request(connection, "tap", semanticId="remote-auth:tokens")
        wait_for_snapshot_node(connection, "remote-tokens:" + identifier + ":more")
        AUTOMATION.request(connection, "tap", semanticId="remote-tokens:" + identifier + ":more")
        AUTOMATION.request(connection, "tap", semanticId="remote-tokens:" + identifier + ":edit")
        wait_for_snapshot_node(connection, "remote-tokens:filter")
        AUTOMATION.request(connection, "setText", semanticId="remote-tokens:filter", text="command.cameraCapture")
        AUTOMATION.request(connection, "scrollTo", semanticId="remote-tokens:filter")
        assert AUTOMATION.require_node(AUTOMATION.request(connection, "snapshot"), "remote-tokens:grant:command.cameraCapture")["checked"]
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "token-permission-edit.png")
        device_command("input keyevent 4")
    item = next(row for row in admin(token_owner, "list")["tokens"] if row["id"] == identifier)
    assert item["permissions"] == ["command.cameraCapture"]


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_granular_tokens_recovery_legacy_write_resources(token_owner, protocol):
    token, _ = create(token_owner, ["calendar.write"])
    tools = catalogue(token)
    for name in ("dikciz_content_write", "dikciz_content_batch"):
        properties = tools[name]["inputSchema"]["properties"]
        if "operations" in properties:
            properties = properties["operations"]["items"]["properties"]
        assert set(properties["resource"]["enum"]) == {"calendarEvents", "calendarReminders"}
    for resource in ("calendars", "calendarInstances"):
        call(token, "contentWrite", {"resource": resource, "operation": "delete", "id": 1}, protocol, DENIED)


def test_granular_tokens_recovery_legacy_lifecycle(token_owner):
    from .test_remote_tokens import test_remote_tokens_validation_lifecycle_and_live_revocation
    test_remote_tokens_validation_lifecycle_and_live_revocation(token_owner)


def test_granular_tokens_recovery_legacy_administration(token_owner):
    from .test_remote_tokens import test_remote_tokens_admin_boundaries_and_owner_rotation
    test_remote_tokens_admin_boundaries_and_owner_rotation(token_owner)


def test_granular_tokens_recovery_legacy_restart(token_owner):
    from .test_remote_tokens import test_remote_tokens_hashed_storage_atomic_recovery_and_restart
    test_remote_tokens_hashed_storage_atomic_recovery_and_restart(token_owner)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_granular_tokens_recovery_write_effect(token_owner, protocol):
    token, _ = create(token_owner, ["command.clipboardSet"])
    previous = call(token_owner, "clipboardGet").get("text") or ""
    text = "Example clipboard " + uuid.uuid4().hex
    try:
        call(token, "clipboardSet", {"text": text}, protocol)
        assert call(token_owner, "clipboardGet")["text"] == text
        call(token, "clipboardGet", protocol=protocol, error=DENIED)
        call(token, "clipboardSet", {"text": text, "readOnly": False}, protocol, "validation_failed")
    finally:
        call(token_owner, "clipboardSet", {"text": previous})
