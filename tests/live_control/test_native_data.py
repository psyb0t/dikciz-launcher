"""Direct Android data and system operations through production entrypoints."""

import subprocess
import uuid

from . import *


PROTOCOLS = ("websocket", "mcp", "http")
TOOLS = AUTOMATION.CONTROL_STATUS_MCP_TOOLS
PREFIX = "Dikciz native fixture "
NAME_MIME = "vnd.android.cursor.item/name"
PHONE_MIME = "vnd.android.cursor.item/phone_v2"
CONTACT_PERMISSIONS = ("android.permission.READ_CONTACTS", "android.permission.WRITE_CONTACTS")
CALENDAR_PERMISSIONS = ("android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR")
CLIENT = load_script("dikciz_native_client", "scripts/dikciz-control.py")
PHONE = "4085550100"


def native_call(protocol, command, arguments=None, error=None, allowed_errors=None):
    port = AUTOMATION_PORT if protocol == "websocket" else MCP_PORT
    operation = TOOLS[command] if protocol == "mcp" else command
    completed = subprocess.run(
        ["python3", str(LAB_ROOT / "scripts/dikciz-control.py"), protocol, operation,
         "--host", AUTOMATION_HOST, "--port", str(port), "--arguments", json.dumps(arguments or {}),
         "--log-file", "/tmp/dikciz-native-client.log"], capture_output=True, text=True, timeout=45,
    )
    response = json.loads(completed.stdout)
    if error is not None or (allowed_errors is not None and completed.returncode != 0):
        assert completed.returncode != 0, response
        detail = response["response"]
        if protocol == "mcp":
            detail = detail["structuredContent"]
        expected_codes = allowed_errors if error is None else ((error,) if isinstance(error, str) else error)
        assert detail["code"] in expected_codes, response
        return detail
    assert completed.returncode == 0, (completed.stdout, completed.stderr)
    return response["structuredContent" if protocol == "mcp" else "result"]


def grant_permissions(permissions):
    for permission in permissions:
        device_command(f"pm grant {DIKCIZ_DEBUG_PACKAGE} {permission}")


def contact_batch(name, phone):
    return [
        {"resource": "rawContacts", "operation": "create", "values": {"account_name": None, "account_type": None}},
        {"resource": "contactData", "operation": "create", "values": {"mimetype": NAME_MIME, "data1": name}, "backReferences": {"raw_contact_id": 0}},
        {"resource": "contactData", "operation": "create", "values": {"mimetype": PHONE_MIME, "data1": phone, "data2": "2"}, "backReferences": {"raw_contact_id": 0}},
    ]


def delete_raw(identifier):
    return native_call("http", "contentWrite", {"resource": "rawContacts", "operation": "delete", "id": identifier})


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_native_data_discovery_and_system_state(websocket_control, protocol):
    AUTOMATION.verify_control_status(websocket_control)
    status = native_call(protocol, "nativeDataStatus")
    resources = {resource["resource"]: resource for resource in status["resources"]}
    assert {"contacts", "phones", "emails", "rawContacts", "contactData", "calendars", "calendarEvents", "calendarReminders", "sms", "callLog", "images", "audio", "videos"} == resources.keys()
    assert resources["sms"]["writableColumns"] == []
    assert resources["contactData"]["foreignKeys"] == {"raw_contact_id": "rawContacts"}
    assert status["maximumPageSize"] == 200
    state = native_call(protocol, "deviceState")
    assert isinstance(state["screenInteractive"], bool)
    assert isinstance(state["keyguardLocked"], bool)
    assert 0 <= state["batteryLevel"] <= state["batteryScale"]
    assert 0 <= state["mediaVolume"] <= state["mediaVolumeMaximum"]
    native_call(protocol, "smsStatus", {"idempotencyKey": "never-submitted"}, error="sms_status_not_found")


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_native_data_contact_crud_pagination_and_injection(websocket_control, protocol):
    grant_permissions(CONTACT_PERMISSIONS)
    fixture = PREFIX + uuid.uuid4().hex[:8]
    identifiers, expected = [], []
    try:
        for index in range(7):
            name = fixture + " " + str(index) + " snowman \u2603"
            phone = "40855501" + str(index).zfill(2)
            result = native_call(protocol, "contentBatch", {"operations": contact_batch(name, phone)})
            identifiers.append(result["results"][0]["id"])
            expected.append((name, phone))
        rows = []
        for offset, count, more in ((0, 3, True), (3, 3, True), (6, 1, False), (7, 0, False), (10000, 0, False)):
            page = native_call(protocol, "contentQuery", {"resource": "phones", "query": fixture, "columns": ["_id", "display_name", "data1", "raw_contact_id"], "limit": 3, "offset": offset})
            assert page["limit"] == 3 and page["offset"] == offset
            assert len(page["items"]) == count and page["hasMore"] is more, page
            rows.extend(page["items"])
        assert [(row["display_name"], row["data1"]) for row in rows] == expected
        assert len({row["_id"] for row in rows}) == 7
        assert native_call(protocol, "contentQuery", {"resource": "phones", "filter": {"display_name": expected[0][0]}})["items"][0]["data1"] == expected[0][1]
        assert native_call(protocol, "contentQuery", {"resource": "phones", "query": "x%_ OR 1=1 --"})["items"] == []
        native_call(protocol, "contentWrite", {"resource": "contactData", "operation": "update", "id": rows[0]["_id"], "values": {"data1": "4085559999"}})
        assert native_call(protocol, "contentQuery", {"resource": "phones", "filter": {"_id": rows[0]["_id"]}})["items"][0]["data1"] == "4085559999"
    finally:
        for identifier in identifiers:
            delete_raw(identifier)
    assert native_call(protocol, "contentQuery", {"resource": "phones", "query": fixture})["items"] == []


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_native_data_strict_validation_and_atomic_rollback(websocket_control, protocol):
    grant_permissions(CONTACT_PERMISSIONS)
    invalid = [
        ("contentQuery", {}), ("contentQuery", {"resource": "file:///sdcard/Dikciz/control/remote-auth.json"}),
        ("contentQuery", {"resource": "phones", "columns": ["_id; DROP TABLE data"]}),
        ("contentQuery", {"resource": "phones", "columns": ["_id", "_id"]}),
        ("contentQuery", {"resource": "phones", "filter": {"_id OR 1=1": "x"}}),
        ("contentQuery", {"resource": "phones", "filter": {"_id": {"sql": "1"}}}),
        *(("contentQuery", {"resource": "phones", "limit": value}) for value in (None, False, 0, -1, 201, 3.5, "1")),
        *(("contentQuery", {"resource": "phones", "offset": value}) for value in (-1, False, 1000001, "0")),
        ("contentQuery", {"resource": "phones", "query": "x" * 257}),
        ("contentWrite", {"resource": "rawContacts", "operation": "delete"}),
        ("contentWrite", {"resource": "rawContacts", "operation": "delete", "id": 0}),
        ("contentWrite", {"resource": "rawContacts", "operation": "update", "id": 1, "values": {"is_admin": True}}),
        ("contentBatch", {"operations": []}), ("contentBatch", {"operations": [{}] * 33}),
        ("contentBatch", {"operations": [contact_batch("x", PHONE)[1]]}),
        ("contentBatch", {"operations": [contact_batch("x", PHONE)[0], {"resource": "calendarEvents", "operation": "create", "values": {"calendar_id": 1, "title": "x", "dtstart": 1, "eventTimezone": "UTC"}}]}),
        ("smsSend", {"idempotencyKey": "x", "recipient": "123; reboot", "body": "test"}),
        ("smsSend", {"idempotencyKey": "x", "recipient": PHONE, "body": ""}),
        ("callControl", {"action": "place"}), ("callControl", {"action": "state", "recipient": PHONE}),
        ("clipboardSet", {"text": None}), ("locationGet", {"maxAgeMilliseconds": -1}),
    ]
    for command, arguments in invalid:
        native_call(protocol, command, arguments, error="validation_failed")
    native_call(protocol, "contentWrite", {"resource": "sms", "operation": "delete", "id": 1}, error="native_read_only")
    before = native_call(protocol, "contentQuery", {"resource": "rawContacts", "columns": ["_id"], "limit": 200})
    native_call(protocol, "contentBatch", {"operations": [contact_batch("rollback", PHONE)[0], {"resource": "rawContacts", "operation": "delete", "id": 2147483647}]}, error="native_operation_failed")
    assert native_call(protocol, "contentQuery", {"resource": "rawContacts", "columns": ["_id"], "limit": 200}) == before


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_native_data_calendar_and_read_permissions(websocket_control, protocol):
    grant_permissions(CALENDAR_PERMISSIONS + ("android.permission.READ_SMS", "android.permission.READ_CALL_LOG", "android.permission.READ_PHONE_STATE"))
    emulator_calendar_fixture_create()
    event_id = None
    try:
        calendars = native_call(protocol, "contentQuery", {"resource": "calendars"})["items"]
        assert calendars
        title = PREFIX + uuid.uuid4().hex
        values = {"calendar_id": calendars[0]["_id"], "title": title, "dtstart": 1900000000000, "dtend": 1900000060000, "eventTimezone": "UTC"}
        event_id = native_call(protocol, "contentWrite", {"resource": "calendarEvents", "operation": "create", "values": values})["results"][0]["id"]
        rows = native_call(protocol, "contentQuery", {"resource": "calendarEvents", "filter": {"_id": event_id}})["items"]
        assert len(rows) == 1 and rows[0]["title"] == title
        native_call(protocol, "contentWrite", {"resource": "calendarEvents", "operation": "update", "id": event_id, "values": {"description": "updated"}})
        assert native_call(protocol, "contentQuery", {"resource": "calendarEvents", "filter": {"_id": event_id}})["items"][0]["description"] == "updated"
        for resource in ("sms", "callLog"):
            result = native_call(protocol, "contentQuery", {"resource": resource, "limit": 1})
            assert isinstance(result["items"], list) and isinstance(result["hasMore"], bool)
        assert isinstance(native_call(protocol, "simList", {"limit": 1})["items"], list)
        assert isinstance(native_call(protocol, "callControl", {"action": "state"})["inCall"], bool)
    finally:
        if event_id is not None:
            native_call("http", "contentWrite", {"resource": "calendarEvents", "operation": "delete", "id": event_id})
        emulator_calendar_fixture_delete()


@pytest.mark.parametrize("input_form", ("json", "table"))
def test_native_data_lua_direct_contact_lookup_on_custom_event(websocket_control, input_form):
    grant_permissions(CONTACT_PERMISSIONS)
    name = PREFIX + uuid.uuid4().hex + ' quote " slash \\ snowman \u2603'
    created = native_call("http", "contentBatch", {"operations": contact_batch(name, PHONE)})["results"]
    raw = created[0]["id"]
    phone_id = created[2]["id"]
    try:
        original = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
        source = """function on_event(event)
  if event.type ~= "fixture.native.lookup" then return end
  local reply = context.requestJson({type = "contentQuery", resource = "phones", filter = {["_id"] = event.payload.rowId, display_name = event.payload.name}, columns = {"data1"}})
  return { actions = {{ type = "patchState", values = { nativeReply = reply } }} }
end"""
        if input_form == "json":
            payload = json.dumps({"type": "contentQuery", "resource": "phones", "filter": {"_id": phone_id, "display_name": name}, "columns": ["data1"]})
            start = source.index("  local reply =")
            end = source.index("\n", start)
            source = source[:start] + "  local reply = context.requestJson([==[" + payload + "]==])" + source[end:]
        configured = automation_configuration(original, source, {}, ["manualTrigger"], ["patchState"], [{"event": "fixture.native.lookup", "minimumIntervalMilliseconds": 0}])
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_DISPATCH, sourceWidgetAddress="1H1V-welcome", actions=[{"type": "emitEvent", "event": "fixture.native.lookup", "payload": {"name": name, "rowId": phone_id}}])
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            current = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
            script = next(item for item in current["scripts"] if item["id"] == AUTOMATION_LIVE_SCRIPT_ID)
            if "nativeReply" in script["state"]:
                reply = json.loads(script["state"]["nativeReply"])
                assert reply["type"] == "result" and reply["result"]["items"] == [{"data1": PHONE}], reply
                break
            time.sleep(.2)
        else:
            status = device_command(f"cat /sdcard/Dikciz/scripts/{AUTOMATION_LIVE_SCRIPT_ID}/run-status.json")
            logs = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SCRIPT_LOGS)
            raise AssertionError(f"Custom-event Lua lookup did not complete: status={status}, logs={logs}")
    finally:
        delete_raw(raw)

def test_native_data_sms_receipts_are_idempotent_private_and_persistent(websocket_control):
    from concurrent.futures import ThreadPoolExecutor
    from . import test_remote_device_control as remote

    grant_permissions(("android.permission.SEND_SMS", "android.permission.READ_PHONE_STATE"))
    key = "native-test-" + uuid.uuid4().hex
    arguments = {"idempotencyKey": key, "recipient": PHONE, "body": "Native receipt fixture " + key}
    subscriptions = native_call("http", "simList")
    if subscriptions["defaultSmsSubscriptionId"] < 0:
        if not subscriptions["items"]:
            for protocol in PROTOCOLS:
                native_call(protocol, "smsSend", arguments, error="no_default_sms_subscription")
                native_call(protocol, "smsSend", {**arguments, "recipient": "408 555-0100"}, error="no_default_sms_subscription")
                native_call(protocol, "smsStatus", {"idempotencyKey": key}, error="sms_status_not_found")
            return
        arguments["subscriptionId"] = subscriptions["items"][0]["subscriptionId"]
    with ThreadPoolExecutor(max_workers=6) as executor:
        futures = [executor.submit(native_call, protocol, "smsSend", arguments) for protocol in PROTOCOLS * 2]
        receipts = [future.result() for future in futures]
    assert len({receipt["messageId"] for receipt in receipts}) == 1
    assert sum(not receipt["duplicate"] for receipt in receipts) == 1
    assert all(receipt["state"] in {"accepted", "sent", "delivered", "failed", "delivery_failed"} for receipt in receipts)
    assert native_call("http", "smsSend", {**arguments, "recipient": "408 555-0100"})["duplicate"] is True
    for protocol in PROTOCOLS:
        native_call(protocol, "smsSend", {**arguments, "body": "different"}, error="sms_idempotency_conflict")
        status = native_call(protocol, "smsStatus", {"idempotencyKey": key})
        assert status["messageId"] == receipts[0]["messageId"]
        assert PHONE not in json.dumps(status) and arguments["body"] not in json.dumps(status)
        assert status["deliveryConfirmed"] is (status["state"] == "delivered")
    remote.remote_call("http", "restart")
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        try:
            persisted = native_call("http", "smsStatus", {"idempotencyKey": key})
            break
        except (AssertionError, KeyError) as error:
            last_error = error
            time.sleep(.3)
    else:
        raise AssertionError("SMS receipt did not survive launcher recreation") from last_error
    assert persisted["messageId"] == receipts[0]["messageId"]
    assert native_call("mcp", "smsSend", arguments)["duplicate"] is True
    logs = remote.remote_call("http", "scriptLogs")
    assert arguments["body"] not in json.dumps(logs) and PHONE not in json.dumps(logs)


def test_native_data_permission_recovery_is_shared_and_does_not_run_denied_reads():
    originally_granted = next(
        item for item in native_call("http", "nativeDataStatus")["resources"] if item["resource"] == "phones"
    )["readGranted"]
    device_command(f"pm revoke {DIKCIZ_DEBUG_PACKAGE} android.permission.READ_CONTACTS")
    restore_system_home()
    wait_for_restarted_websocket_control().close()
    try:
        for protocol in PROTOCOLS:
            result = native_call(protocol, "contentQuery", {"resource": "phones"}, error="native_permission_contacts")
            recovery = result["permissionRequest"]
            assert recovery["requirementId"] == "contacts"
            assert recovery["state"] == "permission_required"
            assert recovery["notificationPosted"] is False
        result = native_call("mcp", "contentQuery", {"resource": "phones", "autoRequestPermissions": True}, error="native_permission_contacts")
        assert result["permissionRequest"]["state"] == "user_action_required"
        assert result["permissionRequest"]["retryRequired"] is True
        assert "items" not in result
    finally:
        if originally_granted:
            grant_permissions(("android.permission.READ_CONTACTS",))
        restore_system_home()


def test_native_data_html_owner_binding_and_clipboard(websocket_control):
    from .test_entrypoint_parity import parity_html_configuration, wait_for_parity_response

    original = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
    expected = native_call("http", "nativeDataStatus")
    javascript = """window.addEventListener("dikciz-ready", async () => {
  const response = await dikciz.command("nativeDataStatus", {});
  await selfWidget.patchState({parityResponse: response});
});"""
    configured = parity_html_configuration(original, javascript)
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    assert wait_for_parity_response(websocket_control) == expected
    previous = native_call("http", "clipboardGet")
    try:
        assert native_call("mcp", "clipboardSet", {"text": "Native clipboard fixture"})["state"] == "written"
        observed = native_call("websocket", "clipboardGet")
        assert observed["available"] is True and observed["text"] == "Native clipboard fixture"
    finally:
        native_call("http", "clipboardSet", {"text": previous.get("text") or ""})


def test_native_data_location_notifications_and_media(websocket_control):

    grant_permissions(("android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"))
    emulator_geo_fix(12.3, 45.6)
    location = native_call("http", "locationGet", {"maxAgeMilliseconds": 2147483647})
    assert isinstance(location["available"], bool)
    if location["available"]:
        assert location["source"] == "last_known"
        assert location["ageMilliseconds"] >= 0
        assert -90 <= location["latitude"] <= 90
        assert -180 <= location["longitude"] <= 180
    component = f"{DIKCIZ_DEBUG_PACKAGE}/org.fossify.home.dikciz.DikcizNotificationListenerService"
    device_command("cmd notification allow_listener " + component)
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        try:
            notifications = native_call("mcp", "notificationsList", {"limit": 1})
            break
        except AssertionError as error:
            last_error = error
            time.sleep(.3)
    else:
        raise AssertionError("Native notification listener did not connect") from last_error
    assert len(notifications["items"]) <= 1 and isinstance(notifications["hasMore"], bool)
    media = native_call("websocket", "mediaSessionsList", {"limit": 1})
    assert len(media["items"]) <= 1 and isinstance(media["hasMore"], bool)

def test_native_data_http_auth_and_origin_are_checked_before_provider_access(websocket_control):
    from .test_device_files import http_request
    from . import test_remote_bearer_auth as auth

    command = {"requestId": str(uuid.uuid4()), "type": "contentQuery", "resource": "phones"}
    assert http_request(command, headers={"Origin": "https://evil.example"})[0] == 403
    password = auth.fresh_bearer_password()
    configured = False
    try:
        auth.open_remote_auth_settings(websocket_control)
        auth.set_phone_password(websocket_control, password)
        configured = True
        for headers in ({}, {"Authorization": "Bearer EXAMPLE-DO-NOT-USE"}):
            status, _, response = http_request(command, headers=headers)
            assert status == 401, response
        status, _, response = http_request(
            {"requestId": str(uuid.uuid4()), "type": "nativeDataStatus"},
            headers={"Authorization": auth.bearer_authorization(password)},
        )
        assert status == 200 and response["type"] == "result", response
    finally:
        if configured:
            with auth.connected_bearer_websocket(password) as connection:
                auth.clear_phone_password(connection)

@pytest.mark.parametrize("graph", ("cycle", "fanout"))
def test_native_data_lua_table_encoding_budget_rejects_graph_expansion(websocket_control, graph):
    original = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
    setup = "local item = {}; item.child = item"
    if graph == "fanout":
        setup = """local item = {value = "bounded"}
  for depth = 1, 7 do
    local parent = {}
    for index = 1, 128 do parent["field" .. index] = item end
    item = parent
  end"""
    source = """function on_event(event)
  %s
  local ok = pcall(function()
    context.request({type = "nativeDataStatus", extra = item})
  end)
  return {actions = {{type = "patchState", values = {encodingRejected = not ok}}}}
end""" % setup
    configured = automation_configuration(original, source, {}, ["manualTrigger"], ["patchState"], [{"event": "manual", "minimumIntervalMilliseconds": 0}])
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_TRIGGER, scriptId=AUTOMATION_LIVE_SCRIPT_ID)
    wait_for_automation_script_state(websocket_control, AUTOMATION_LIVE_SCRIPT_ID, "encodingRejected", True)
    assert native_call("http", "deviceState")["screenInteractive"] is True
