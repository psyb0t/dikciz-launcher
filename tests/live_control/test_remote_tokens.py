"""Named remote credentials through production listeners and Android storage."""

import concurrent.futures
import http.client
import json
import socket
import uuid

import pytest

from . import *
from . import test_remote_bearer_auth as bearer

PROTOCOLS = ("http", "websocket", "mcp")
DENIED = "remote_permission_denied"


def http_command(secret, command, fields=None):
    connection = http.client.HTTPConnection(AUTOMATION_HOST, MCP_PORT, timeout=45)
    headers = {"Content-Type": "application/json"}
    if secret is not None:
        headers["Authorization"] = bearer.bearer_authorization(secret)
    try:
        connection.request("POST", "/v1/automation", json.dumps({
            "type": command, "requestId": str(uuid.uuid4()), **(fields or {}),
        }), headers)
        response = connection.getresponse()
        return response.status, json.loads(response.read())
    finally:
        connection.close()


def call(secret, command, fields=None, protocol="http", error=None):
    if protocol == "http":
        status, response = http_command(secret, command, fields)
        assert status == 200
    elif protocol == "websocket":
        with bearer.connected_bearer_websocket(secret) as connection:
            request_id = str(uuid.uuid4())
            AUTOMATION.send_frame(connection, AUTOMATION.FRAME_OPCODE_TEXT, json.dumps({
                "type": command, "requestId": request_id, **(fields or {}),
            }).encode())
            response = AUTOMATION.receive_response(connection, request_id)
    else:
        arguments = bearer.mcp_arguments()
        status, _, document = MCP.request(arguments, "POST", {
            "jsonrpc": "2.0", "id": 1, "method": "tools/call",
            "params": {"name": AUTOMATION.CONTROL_STATUS_MCP_TOOLS[command], "arguments": fields or {}, "_meta": protocol_meta()},
        }, {**bearer.mcp_headers(password=secret), "MCP-Protocol-Version": MCP.MCP_PROTOCOL_VERSION, "Mcp-Method": "tools/call",
            "Mcp-Name": AUTOMATION.CONTROL_STATUS_MCP_TOOLS[command]})
        assert status == 200
        result = document["result"]
        if error is not None:
            assert result["isError"] is True
            assert result["structuredContent"]["code"] == error
            return result["structuredContent"]
        assert not result.get("isError", False)
        return result["structuredContent"]
    if error is not None:
        assert response["type"] == "error"
        assert response["code"] == error
        return response
    assert response["type"] == "result"
    return response["result"]


def admin(secret, action, **fields):
    return call(secret, "remoteTokens", {"action": action, **fields})


def create(secret, grants, name="Fixture token"):
    result = admin(secret, "create", name=name, permissions=grants)
    token = result.pop("token")
    assert token.startswith("dkt1.") and len(token) == 65
    assert token not in json.dumps(result)
    return token, result["item"]["id"]


def protocol_meta():
    return {"io.modelcontextprotocol/protocolVersion": MCP.MCP_PROTOCOL_VERSION,
            "io.modelcontextprotocol/clientCapabilities": {}}


def catalogue(secret):
    status, _, response = MCP.request(bearer.mcp_arguments(), "POST", {
        "jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {"_meta": protocol_meta()},
    }, {**bearer.mcp_headers(password=secret), "MCP-Protocol-Version": MCP.MCP_PROTOCOL_VERSION, "Mcp-Method": "tools/list"})
    assert status == 200
    return {row["name"]: row for row in response["result"]["tools"]}


@pytest.fixture
def token_owner(websocket_control):
    password = bearer.fresh_bearer_password()
    try:
        bearer.open_remote_auth_settings(websocket_control)
        bearer.set_phone_password(websocket_control, password)
        yield password
    finally:
        # This isolated emulator record is created by this fixture, never a supplied device.
        # Native UI coverage exercises normal revocation and password clearing separately.
        device_command("rm -f /sdcard/Dikciz/control/remote-auth.json /sdcard/Dikciz/control/remote-auth.json.bak /sdcard/Dikciz/control/remote-auth.json.new")


def test_remote_tokens_owner_credential_and_ui_manager(token_owner):
    token, _ = create(token_owner, ["owner"])
    owner_tools = catalogue(token_owner)
    assert set(catalogue(token)) == set(owner_tools)
    (ARTIFACT_DIRECTORY / "mcp-command-discovery.json").write_text(json.dumps({"tools": list(owner_tools.values())}))
    assert call(token, "configGet")
    created = admin(token, "create", name="Child", permissions=["sensors.read"])
    assert created["item"]["permissions"] == ["sensors.read"]
    with bearer.connected_bearer_websocket(token) as connection:
        AUTOMATION.request(connection, AUTOMATION.TYPE_SET_TEXT,
                           semanticId=bearer.SEMANTIC_REMOTE_AUTH_PASSWORD, text=bearer.fresh_bearer_password())
        snapshot = AUTOMATION.request(connection, AUTOMATION.TYPE_SNAPSHOT)
        password_row = next(row for row in snapshot["nodes"] if row["semanticId"] == bearer.SEMANTIC_REMOTE_AUTH_PASSWORD)
        assert password_row.get("text") is None
        AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId="remote-auth:tokens")
        wait_for_snapshot_node(connection, "remote-tokens:create")
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "access-tokens-list.png")
        AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId="remote-tokens:create")
        wait_for_snapshot_node(connection, "remote-tokens:save")
        AUTOMATION.request(connection, AUTOMATION.TYPE_SET_TEXT, semanticId="remote-tokens:name", text="UI sensor client")
        AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId="remote-tokens:grant:sensors.read")
        checked = AUTOMATION.request(connection, AUTOMATION.TYPE_SNAPSHOT)
        assert next(row for row in checked["nodes"] if row["semanticId"] == "remote-tokens:grant:sensors.read")["checked"] is True
        assert next(row for row in checked["nodes"] if row["semanticId"] == "remote-tokens:save")["visible"] is True
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "access-token-create.png")
        AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId="remote-tokens:save")
        wait_for_snapshot_node(connection, "remote-tokens:copy")
        snapshot = AUTOMATION.request(connection, AUTOMATION.TYPE_SNAPSHOT)
        assert not any("dkt1." in str(row.get("text", "")) for row in snapshot["nodes"])
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "access-token-created.png")
        AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId="remote-tokens:copy")
        copied = call(token, "clipboardGet")["text"]
        identity = call(copied, "remoteAccessInfo")
        assert identity["permissions"] == ["sensors.read"]
        identifier = identity["id"]
        AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId="dialog:close")
        wait_for_snapshot_node(connection, f"remote-tokens:{identifier}:more")
        AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=f"remote-tokens:{identifier}:more")
        wait_for_snapshot_node(connection, f"remote-tokens:{identifier}:edit")
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "access-token-manage.png")
        assert not any(row["semanticId"] == "remote-tokens:copy" for row in AUTOMATION.request(connection, "snapshot")["nodes"])
        AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=f"remote-tokens:{identifier}:edit")
        wait_for_snapshot_node(connection, "remote-tokens:save")
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "access-token-edit.png")
        AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId="remote-tokens:grant:sensors.read")
        AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId="remote-tokens:grant:sms.read")
        AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId="remote-tokens:save")
        assert call(copied, "remoteAccessInfo")["permissions"] == ["sms.read"]
        AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=f"remote-tokens:{identifier}:more")
        AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=f"remote-tokens:{identifier}:toggle")
        assert http_command(copied, "remoteAccessInfo")[0] == 401
        AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=f"remote-tokens:{identifier}:more")
        AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=f"remote-tokens:{identifier}:toggle")
        assert call(copied, "remoteAccessInfo")["id"] == identifier
        AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=f"remote-tokens:{identifier}:more")
        AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=f"remote-tokens:{identifier}:revoke")
        wait_for_snapshot_node(connection, "remote-tokens:confirm-revoke")
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "access-token-revoke.png")
        AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId="remote-tokens:confirm-revoke")
        assert http_command(copied, "remoteAccessInfo")[0] == 401
    assert not any(row["name"] == "UI sensor client" for row in admin(token_owner, "list")["tokens"])


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_remote_tokens_sms_grants_and_bypass_denial(token_owner, protocol):
    token, identifier = create(token_owner, ["sms.read"])
    identity = call(token, "remoteAccessInfo", protocol=protocol)
    assert identity == {"id": identifier, "name": "Fixture token", "owner": False, "permissions": ["sms.read"],
                        "expiresAt": 0, "requestsPerMinute": 0, "smsAddresses": []}
    tools = catalogue(token)
    assert tools["dikciz_content_query"]["inputSchema"]["properties"]["resource"]["enum"] == ["sms"]
    assert "dikciz_sms_send" not in tools and "dikciz_shell" not in tools and "dikciz_chat" not in tools
    state = call(token, "nativeDataStatus", protocol=protocol)
    assert [row["resource"] for row in state["resources"]] == ["sms"]
    assert state["resources"][0]["writeGranted"] is False
    cases = [
        ("contentQuery", {"resource": "contacts"}),
        ("contentWrite", {"resource": "contactData", "operation": "delete", "id": 1}),
        ("contentBatch", {"operations": [{"resource": "rawContacts", "operation": "create", "values": {}}]}),
        ("smsSend", {}),
        ("shell", {"command": "id"}),
        ("shellStream", {"command": "id"}),
        ("shellStreamStop", {"streamId": "no-stream"}),
        ("androidApiRead", {}),
        ("androidApiInvoke", {}),
        ("automationTrigger", {}),
        ("automationDispatch", {}),
        ("configGet", {}),
        ("configReplace", {}),
        ("remoteTokens", {"action": "list"}),
        ("remoteAuthDisable", {}),
        ("jobStart", {}),
        ("fileRead", {"path": "/sdcard/Dikciz/control/remote-auth.json"}),
    ]
    for command, fields in cases:
        call(token, command, fields, protocol, DENIED)
    allowed = call(token, "controlStatus", protocol=protocol)
    assert all(row["type"] not in {"shell", "smsSend", "configReplace"} for row in allowed["commands"])
    assert allowed["events"] == []


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_remote_tokens_provider_resource_and_action_scopes(token_owner, protocol):
    token, _ = create(token_owner, ["contacts.read", "calendar.write", "calls.read"])
    tools = catalogue(token)
    props = lambda name: tools[name]["inputSchema"]["properties"]
    assert set(props("dikciz_content_query")["resource"]["enum"]) == {"contacts", "phones", "emails", "rawContacts", "contactData", "callLog"}
    assert set(props("dikciz_content_write")["resource"]["enum"]) == {"calendarEvents", "calendarReminders"}
    assert props("dikciz_call_control")["action"]["enum"] == ["state"]
    assert tools["dikciz_call_control"]["annotations"]["readOnlyHint"] is True
    call(token, "callControl", {"action": "place", "recipient": "EXAMPLE-DO-NOT-USE"}, protocol, DENIED)
    call(token, "contentQuery", {"resource": "sms"}, protocol, DENIED)
    call(token, "contentBatch", {"operations": [
        {"resource": "rawContacts", "operation": "create", "values": {}},
    ]}, protocol, DENIED)
    call(token, "contentWrite", {"resource": "contactData", "operation": "delete", "id": 1}, protocol, DENIED)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_remote_tokens_scope_filtered_search(token_owner, protocol):
    token, _ = create(token_owner, ["screen.capture"])
    names = set(catalogue(token))
    for text in ("screenshot", "send sms", "shell root", "android api"):
        page = call(token, "searchQuery", {"collection": "tools", "query": text, "limit": 20}, protocol)
        assert all(row["id"] in names for row in page["items"])
    call(token, "searchRead", {"collection": "tools", "id": "dikciz_sms_send"}, protocol, "search_not_found")
    call(token, "searchRead", {"collection": "tools", "id": "dikciz_shell"}, protocol, "search_not_found")
    own = call(token, "searchRead", {"collection": "tools", "id": "dikciz_screenshot"}, protocol)
    assert own["tool"]["name"] == "dikciz_screenshot"
    page = call(token, "searchCollections", {"limit": 1}, protocol)
    assert page["total"] == 2 and len(page["items"]) == 1 and page["hasMore"]
    second = call(token, "searchCollections", {"limit": 1, "offset": 1}, protocol)
    assert len(second["items"]) == 1 and not second["hasMore"]
    assert call(token, "searchCollections", {"limit": 1, "offset": 2}, protocol)["items"] == []
    all_rows = page["items"] + second["items"]
    assert {row["collection"] for row in all_rows} == {"docs", "tools"}
    assert next(row for row in all_rows if row["collection"] == "tools")["documents"] == len(names)
    call(token, "searchRead", {"collection": "private", "id": "secret"}, protocol, DENIED)
    call(token, "searchIndex", {"collection": "private", "action": "clear"}, protocol, DENIED)


def test_remote_tokens_validation_lifecycle_and_live_revocation(token_owner):
    first, identifier = create(token_owner, ["sensors.read"], "Sensor client")
    second, other = create(token_owner, ["screen.capture"], "Screenshot client")
    metadata = admin(token_owner, "list")
    assert first not in json.dumps(metadata) and second not in json.dumps(metadata)
    assert all(set(row) == {"id", "name", "permissions", "enabled", "createdAt", "expiresAt", "requestsPerMinute", "smsAddresses", "expired", "lastUsedAt"} for row in metadata["tokens"])
    call(token_owner, "remoteAuthDisable", error="remote_auth_tokens_exist")
    with bearer.connected_bearer_websocket(first) as connection:
        assert AUTOMATION.request(connection, "remoteAccessInfo")["id"] == identifier
        admin(token_owner, "update", id=identifier, permissions=["sms.read"], name="Changed client")
        assert "dikciz_sensor_readings" not in catalogue(first)
        assert call(first, "remoteAccessInfo")["name"] == "Changed client"
        assert call(second, "remoteAccessInfo")["id"] == other
        _, error = AUTOMATION.request_error_with_id(connection, "sensorReadings")
        assert error["code"] == DENIED
        admin(token_owner, "update", id=identifier, enabled=False)
        _, error = AUTOMATION.request_error_with_id(connection, "remoteAccessInfo")
        assert error["code"] == "remote_auth_required"
        assert http_command(first, "remoteAccessInfo")[0] == 401
        admin(token_owner, "update", id=identifier, enabled=True)
        assert call(first, "remoteAccessInfo")["id"] == identifier
        admin(token_owner, "revoke", id=identifier)
        assert http_command(first, "remoteAccessInfo")[0] == 401
    assert call(second, "remoteAccessInfo")["id"] == other
    assert http_command(None, "remoteAccessInfo")[0] == 401
    assert http_command("dkt1." + uuid.uuid4().hex[:16] + "." + "x" * 43, "remoteAccessInfo")[0] == 401


def test_remote_tokens_invalid_grants_and_names(token_owner):
    cases = [
        {"name": "", "permissions": ["sms.read"]},
        {"name": "x" * 81, "permissions": ["sms.read"]},
        {"name": "bad\nname", "permissions": ["sms.read"]},
        {"name": "Empty", "permissions": []},
        {"name": "Unknown", "permissions": ["sms.admin"]},
        {"name": "Duplicate", "permissions": ["sms.read", "sms.read"]},
        {"name": "Mixed", "permissions": ["owner", "sms.read"]},
        {"name": "Forged", "permissions": ["sms.read"], "owner": True},
        {"name": None, "permissions": ["sms.read"]},
        {"name": 7, "permissions": ["sms.read"]},
        {"name": "Wrong permissions", "permissions": "sms.read"},
        {"name": "Null permission", "permissions": [None]},
        {"name": "Wrong enabled", "permissions": ["sms.read"], "enabled": "true"},
    ]
    for fields in cases:
        call(token_owner, "remoteTokens", {"action": "create", **fields}, error="validation_failed")
        assert admin(token_owner, "list")["tokens"] == []


def test_remote_tokens_mcp_sessions_belong_to_identity(token_owner):
    first, _ = create(token_owner, ["sensors.read"])
    second, _ = create(token_owner, ["sensors.read"])
    arguments = bearer.mcp_arguments()
    session = bearer.mcp_initialize(arguments, first)
    bearer.mcp_initialized(arguments, session, first)
    message = {"jsonrpc": "2.0", "id": 3, "method": "tools/list", "params": {"_meta": protocol_meta()}}
    for credential in (second, token_owner):
        status, _, _ = MCP.request(arguments, "POST", message, bearer.mcp_headers(session, credential))
        assert status == 404
        status, _, _ = MCP.request(arguments, "DELETE", headers=bearer.mcp_headers(session, credential))
        assert status == 404
    status, _, response = MCP.request(arguments, "POST", message, bearer.mcp_headers(session, first))
    assert status == 200 and response["result"]["tools"]
    bearer.mcp_delete_session(arguments, session, first)


def test_remote_tokens_websocket_hello_and_broadcast_confidentiality(token_owner):
    token, _ = create(token_owner, ["sensors.read"])
    with socket.create_connection((AUTOMATION_HOST, AUTOMATION_PORT), timeout=10) as connection:
        connection.settimeout(2)
        AUTOMATION.perform_handshake(connection, AUTOMATION_HOST, AUTOMATION_PORT, bearer.bearer_authorization(token))
        hello = AUTOMATION.hello(connection)
        assert hello["restricted"] is True and hello["nodes"] == []
        call(token_owner, "deviceInfo")
        with pytest.raises(socket.timeout):
            AUTOMATION.receive_exact(connection, 1)
        assert AUTOMATION.request(connection, "remoteAccessInfo")["permissions"] == ["sensors.read"]


def test_remote_tokens_concurrent_creation_and_limit(token_owner):
    admin_token, admin_id = create(token_owner, ["owner"], "Fixture administrator")
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
        created = list(executor.map(lambda index: create(admin_token, ["sms.read"], "Client " + str(index)), range(8)))
    assert len({row[0] for row in created}) == 8 and len({row[1] for row in created}) == 8
    for index in range(23):
        create(admin_token, ["sms.read"], "Extra " + str(index))
    assert len(admin(admin_token, "list")["tokens"]) == 32
    call(admin_token, "remoteTokens", {"action": "create", "name": "Overflow", "permissions": ["sms.read"]}, error="remote_token_limit")
    rows = admin(admin_token, "list")["tokens"]
    for row in sorted(rows, key=lambda row: row["id"] == admin_id):
        admin(admin_token, "revoke", id=row["id"])
    assert admin(token_owner, "list")["tokens"] == []
    with bearer.connected_bearer_websocket(token_owner) as connection:
        bearer.clear_phone_password(connection)
    assert http_command(None, "remoteAuthStatus")[1]["result"]["configured"] is False




def read_auth_record():
    run_device_operation("file-pull", DEVICE_FILE=bearer.REMOTE_AUTH_FILE, ARTIFACT_FILE="tokens-test-record.json")
    return json.loads((ARTIFACT_DIRECTORY / "tokens-test-record.json").read_text())


def write_auth_record(record):
    (ARTIFACT_DIRECTORY / "tokens-test-record.json").write_text(json.dumps(record))
    run_device_operation("file-push", DEVICE_FILE=bearer.REMOTE_AUTH_FILE, ARTIFACT_FILE="tokens-test-record.json")


def test_remote_tokens_hashed_storage_atomic_recovery_and_restart(token_owner):
    token, identifier = create(token_owner, ["sensors.read"])
    record = read_auth_record()
    assert record["version"] == 2 and record["enabled"] is True
    serialized = json.dumps(record)
    assert token not in serialized and token_owner not in serialized
    assert record["tokens"][0]["id"] == identifier
    assert len(record["tokens"][0]["digest"]) == 64
    # A lone legacy atomic backup must recover rather than opening authentication.
    device_command("mv /sdcard/Dikciz/control/remote-auth.json /sdcard/Dikciz/control/remote-auth.json.bak")
    assert http_command(None, "deviceInfo")[0] == 401
    assert call(token, "remoteAccessInfo")["id"] == identifier
    initial_instance = call(token_owner, "deviceInfo")["application"]["instanceId"]
    assert call(token_owner, "restart")["state"] == "scheduled"
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        try:
            status, response = http_command(token, "remoteAccessInfo")
            if status == 200 and response.get("type") == "result":
                assert http_command(None, "deviceInfo")[0] == 401
                assert response["result"]["id"] == identifier
                if call(token, "deviceInfo")["application"]["instanceId"] != initial_instance:
                    break
            time.sleep(0.25)
        except (OSError, http.client.HTTPException):
            time.sleep(0.25)
    else:
        raise AssertionError("Token did not survive launcher recreation")
    assert http_command(None, "deviceInfo")[0] == 401


def test_remote_tokens_invalid_persisted_records_fail_closed(token_owner):
    token, _ = create(token_owner, ["sms.read"])
    original = read_auth_record()
    for mutation in ("unknown_field", "unknown_grant", "duplicate_id", "disabled_auth", "wrong_type", "bad_digest"):
        damaged = json.loads(json.dumps(original))
        if mutation == "unknown_field":
            damaged["tokens"][0]["owner"] = True
        elif mutation == "unknown_grant":
            damaged["tokens"][0]["permissions"] = ["imaginary.admin"]
        elif mutation == "duplicate_id":
            damaged["tokens"].append(damaged["tokens"][0].copy())
        elif mutation == "disabled_auth":
            damaged["enabled"] = False
        elif mutation == "wrong_type":
            damaged["tokens"][0]["enabled"] = "true"
        else:
            damaged["tokens"][0]["digest"] = "invalid"
        try:
            write_auth_record(damaged)
            for credential in (None, token, token_owner):
                assert http_command(credential, "deviceInfo")[0] == 401
        finally:
            write_auth_record(original)
        assert call(token, "remoteAccessInfo")["permissions"] == ["sms.read"]


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_remote_tokens_granted_operations_and_combined_scopes(token_owner, protocol):
    for permission in ("android.permission.READ_SMS", "android.permission.READ_CONTACTS"):
        device_command(f"pm grant {DIKCIZ_DEBUG_PACKAGE} {permission}")
    token, identifier = create(token_owner, ["sms.read", "contacts.read", "sensors.read", "screen.capture", "ui.read"])
    assert isinstance(call(token, "contentQuery", {"resource": "sms", "limit": 1}, protocol)["items"], list)
    assert isinstance(call(token, "contentQuery", {"resource": "contacts", "limit": 1}, protocol)["items"], list)
    assert call(token, "sensorReadings", protocol=protocol)
    image = call(token, "screenshot", protocol=protocol)
    assert image["pngBase64"] and image["width"] > 0 and image["height"] > 0
    assert call(token, "snapshot", protocol=protocol)["nodes"]
    for field in ("access", "permissions", "readOnly"):
        call(token, "remoteAccessInfo", {field: {"owner": True}}, protocol, "validation_failed")
    assert call(token, "remoteAccessInfo", protocol=protocol)["id"] == identifier


def test_remote_tokens_permission_catalogue(token_owner):
    grants = admin(token_owner, "catalog")["permissions"]
    (ARTIFACT_DIRECTORY / "remote-grant-discovery.json").write_text(json.dumps({"permissions": grants}))
    identifiers = {row["id"] for row in grants}
    assert len(identifiers) == len(grants)
    assert {"owner", "sms.read", "sms.send", "screen.capture", "ui.read", "sensors.read"} <= identifiers
    for row in grants:
        assert row["title"] and row["description"]


def test_remote_tokens_ui_control_cannot_administer_dikciz(token_owner):
    emulator_dikciz_accessibility_set(True)
    try:
        token, _ = create(token_owner, ["ui.control"])
        snapshot = call(token, "accessibilitySnapshot")
        assert snapshot["nodes"]
        node = next(row for row in snapshot["nodes"] if row["clickable"] and row["visibleToUser"])
        call(token, "accessibilityAction", {"snapshotId": snapshot["snapshotId"], "nodeId": node["nodeId"],
                                          "action": "click"}, error=DENIED)
        call(token, "tap", {"semanticId": "remote-auth:tokens"}, error=DENIED)
    finally:
        emulator_dikciz_accessibility_set(False)

def test_remote_tokens_sms_send_does_not_read_own_numbers(token_owner):
    device_command(f"pm grant {DIKCIZ_DEBUG_PACKAGE} android.permission.READ_PHONE_STATE")
    device_command(f"pm grant {DIKCIZ_DEBUG_PACKAGE} android.permission.READ_PHONE_NUMBERS")
    token, _ = create(token_owner, ["sms.send"])
    result = call(token, "simList", {"autoRequestPermissions": True})
    assert result["phoneNumberPermissionGranted"] is False and result["phoneNumbersAllowed"] is False
    assert "permissionRequest" not in result
    assert all(row["phoneNumber"] is None and row["phoneNumberStatus"] == "access_denied" for row in result["items"])
    call(token, "contentQuery", {"resource": "sms"}, error=DENIED)



def test_remote_tokens_each_grant_has_discoverable_operations(token_owner):
    cases = {
        "sms.read": {"dikciz_content_query"},
        "sms.send": {"dikciz_sms_send", "dikciz_sms_status", "dikciz_sim_list"},
        "sim.read": {"dikciz_sim_list"},
        "contacts.read": {"dikciz_content_query"},
        "contacts.write": {"dikciz_content_write", "dikciz_content_batch"},
        "calendar.read": {"dikciz_content_query"},
        "calendar.write": {"dikciz_content_write", "dikciz_content_batch"},
        "calls.read": {"dikciz_content_query", "dikciz_call_control"},
        "calls.control": {"dikciz_call_control"},
        "sensors.read": {"dikciz_sensor_readings"},
        "screen.capture": {"dikciz_screenshot", "dikciz_display_screenshot"},
        "ui.read": {"dikciz_snapshot", "dikciz_ui_dump", "dikciz_accessibility_snapshot", "dikciz_find", "dikciz_wait_for"},
        "ui.control": {"dikciz_snapshot", "dikciz_accessibility_action"},
        "apps.read": {"dikciz_app_catalogue", "dikciz_app_icon", "dikciz_package_info"},
        "apps.launch": {"dikciz_launch_app", "dikciz_app_action"},
        "device.read": {"dikciz_device_state", "dikciz_audio_devices", "dikciz_camera_list", "dikciz_camera_capabilities"},
        "device.control": {"dikciz_torch_control", "dikciz_vibration_control", "dikciz_system_setting"},
        "location.read": {"dikciz_location_get", "dikciz_geocode", "dikciz_gnss_status"},
        "notifications.read": {"dikciz_notifications_list"},
        "notifications.control": {"dikciz_notifications_list", "dikciz_notification_manage"},
        "media.read": {"dikciz_content_query", "dikciz_media_sessions_list"},
        "files.read": {"dikciz_file_list", "dikciz_file_read", "dikciz_file_manage"},
        "logs.read": {"dikciz_script_logs"},
        "events.read": {"dikciz_event_journal_status", "dikciz_event_journal_read"},
        "search.read": {"dikciz_search_read", "dikciz_search_query"},
        "search.write": {"dikciz_search_index"},
        "speech.output": {"dikciz_speech_speak", "dikciz_speech_voices", "dikciz_speech_stop"},
        "bluetooth.read": {"dikciz_bluetooth_devices", "dikciz_bluetooth_scan"},
        "wifi.read": {"dikciz_wifi_scan_results", "dikciz_wifi_scan"},
        "usb.read": {"dikciz_usb_devices"},
        "nfc.read": {"dikciz_nfc_status", "dikciz_nfc_read"},
    }
    assert {row["id"] for row in admin(token_owner, "catalog")["permissions"] if not row["id"].startswith("command.")} == {"owner", *cases}
    coverage = {row["id"]: set(row["tools"]) for row in admin(token_owner, "catalog")["permissions"]}
    for permission, expected in cases.items():
        assert expected <= coverage[permission], (permission, expected - coverage[permission])
    admin_token, _ = create(token_owner, ["owner"], "Grant-catalogue administrator")
    for grant, expected in cases.items():
        token, identifier = create(admin_token, [grant], grant)
        visible = set(catalogue(token))
        assert expected <= visible, (grant, expected - visible)
        assert "dikciz_remote_tokens" not in visible
        assert not visible.intersection({"dikciz_shell", "dikciz_android_api_invoke", "dikciz_chat"})
        admin(admin_token, "revoke", id=identifier)


def test_remote_tokens_file_grants_protect_credentials(token_owner):
    token, _ = create(token_owner, ["files.read"])
    tools = catalogue(token)
    assert tools["dikciz_file_manage"]["annotations"]["readOnlyHint"] is True
    assert set(tools["dikciz_file_manage"]["inputSchema"]["properties"]["action"]["enum"]) == {"stat", "search"}
    paths = [bearer.REMOTE_AUTH_FILE + suffix for suffix in ("", ".bak", ".new", ".lock")]
    paths.append("/sdcard/Dikciz/control/../control/remote-auth.json")
    for protocol in PROTOCOLS:
        assert call(token, "fileRead", {"path": "/sdcard/Dikciz/config.json"}, protocol)["dataBase64"]
        for path in paths:
            call(token, "fileRead", {"path": path}, protocol, "file_access_denied")
            call(token, "fileManage", {"action": "stat", "path": path}, protocol, "file_access_denied")
        call(token, "fileWrite", {"path": "/sdcard/Dikciz/config.json"}, protocol, DENIED)
        call(token, "fileManage", {"action": "delete", "path": "/sdcard/Dikciz/config.json"}, protocol, DENIED)

    arguments = bearer.mcp_arguments()
    session = bearer.mcp_initialize(arguments, token)
    bearer.mcp_initialized(arguments, session, token)
    message = {"jsonrpc": "2.0", "id": 3, "method": "tools/list", "params": {"_meta": protocol_meta()}}
    admin(token_owner, "update", id=call(token, "remoteAccessInfo")["id"], permissions=["sms.read"])
    status, _, response = MCP.request(arguments, "POST", message, bearer.mcp_headers(session, token))
    assert status == 200
    names = {row["name"] for row in response["result"]["tools"]}
    assert "dikciz_file_read" not in names and "dikciz_content_query" in names
    identifier = call(token, "remoteAccessInfo")["id"]
    admin(token_owner, "update", id=identifier, enabled=False)
    assert MCP.request(arguments, "POST", message, bearer.mcp_headers(session, token))[0] == 401
    admin(token_owner, "update", id=identifier, enabled=True)
    assert MCP.request(arguments, "POST", message, bearer.mcp_headers(session, token))[0] == 200
    admin(token_owner, "revoke", id=identifier)
    assert MCP.request(arguments, "POST", message, bearer.mcp_headers(session, token))[0] == 401
    owner_tools = catalogue(token_owner)
    (ARTIFACT_DIRECTORY / "mcp-command-discovery.json").write_text(json.dumps({"tools": list(owner_tools.values())}))


def test_remote_tokens_admin_boundaries_and_owner_rotation(token_owner):
    administrator, identifier = create(token_owner, ["owner"], "Boundary administrator")
    def stable_metadata():
        document = admin(administrator, "list")
        for row in document["tokens"]:
            last_used = row.pop("lastUsedAt")
            assert last_used is None or isinstance(last_used, int)
        return document
    baseline = stable_metadata()
    invalid = [
        {},
        {"action": "imaginary"},
        {"action": "list", "id": identifier},
        {"action": "catalog", "permissions": ["owner"]},
        {"action": "update", "id": identifier},
        {"action": "update", "name": "Missing ID"},
        {"action": "update", "id": "bad", "name": "Malformed ID"},
        {"action": "update", "id": 7, "name": "Wrong ID type"},
        {"action": "update", "id": identifier, "enabled": None},
        {"action": "update", "id": identifier, "permissions": None},
        {"action": "update", "id": identifier, "permissions": ["sms.read", "sms.read"]},
        {"action": "update", "id": identifier, "name": " "},
        {"action": "revoke", "id": "../"},
        {"action": "revoke", "id": False},
    ]
    for arguments in invalid:
        call(administrator, "remoteTokens", arguments, error="validation_failed")
        assert stable_metadata() == baseline
    absent = "0" * 16
    assert absent != identifier
    for action, fields in (("revoke", {}), ("update", {"name": "Absent client"})):
        call(administrator, "remoteTokens", {"action": action, "id": absent, **fields}, error="remote_token_not_found")
    admin(token_owner, "update", id=identifier, enabled=False)
    call(token_owner, "remoteAuthDisable", error="remote_auth_tokens_exist")
    with bearer.connected_bearer_websocket(token_owner) as connection:
        bearer.clear_phone_password(connection)
    assert call(token_owner, "remoteAuthStatus")["enabled"] is True
    admin(token_owner, "update", id=identifier, enabled=True)
    replacement = bearer.fresh_bearer_password()
    with bearer.connected_bearer_websocket(token_owner) as connection:
        bearer.set_phone_password(connection, replacement)
    assert http_command(token_owner, "deviceInfo")[0] == 401
    assert call(replacement, "remoteAccessInfo")["owner"] is True
    assert admin(administrator, "list")["tokens"][0]["id"] == identifier
    admin(administrator, "revoke", id=identifier)
    record = read_auth_record()
    assert record["version"] == 1 and set(record) == bearer.REMOTE_AUTH_RECORD_KEYS
    assert http_command(administrator, "remoteAccessInfo")[0] == 401
    assert admin(replacement, "list")["tokens"] == []
