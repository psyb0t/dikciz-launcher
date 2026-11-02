"""Direct SIM numbers and current embedded-assistant API contracts."""

import json
import time
import uuid

import pytest

from . import test_chat_agent as agent
from .test_chat_agent import provider_endpoint
from .test_native_data import PROTOCOLS, grant_permissions, native_call

PHONE_STATE = "android.permission.READ_PHONE_STATE"
PHONE_NUMBERS = "android.permission.READ_PHONE_NUMBERS"
ACCESS_MARKER = "Current launcher access context:\n"
websocket_control = agent.websocket_control


def reopen_home():
    agent.run_device_operation("app-start", PACKAGE_NAME=agent.DIKCIZ_DEBUG_PACKAGE,
                               ACTIVITY=agent.DIKCIZ_DEBUG_ACTIVITY)
    agent.wait_chat_ready()
    deadline = time.monotonic() + agent.TURN_TIMEOUT
    while time.monotonic() < deadline:
        try:
            snapshot = native_call("http", "snapshot")
            if any(node.get("semanticId") == "permission:dismiss" for node in snapshot["nodes"]):
                native_call("http", "tap", {"semanticId": "permission:dismiss"})
            return
        except AssertionError:
            agent.LOGGER.debug("fixture_waiting_for_home_ui_after_permission_change")
            time.sleep(0.2)
    raise AssertionError("Home UI did not recover after the permission change")


def request_access_context(body):
    system = body.get("system")
    if system is None:
        system = [message["content"] for message in body["messages"] if message["role"] == "system"]

    def text(value):
        if isinstance(value, str):
            return value
        if isinstance(value, list):
            return "\n".join(text(part) for part in value)
        return value.get("text", "")

    return json.loads(text(system).split(ACCESS_MARKER)[-1])


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_sim_numbers_native_permission_and_pagination(websocket_control, protocol):
    package = agent.DIKCIZ_DEBUG_PACKAGE
    previous = agent.device_command("dumpsys package " + package)
    granted = {permission: permission + ": granted=true" in previous for permission in (PHONE_STATE, PHONE_NUMBERS)}
    try:
        grant_permissions((PHONE_STATE,))
        agent.device_command("pm revoke " + package + " " + PHONE_NUMBERS)
        reopen_home()
        partial = native_call(protocol, "simList")
        assert partial["phoneNumberPermissionGranted"] is False
        assert partial["permissionRequest"]["requirementId"] == "phoneNumbers"
        assert partial["permissionRequest"]["autoRequested"] is False
        assert isinstance(partial["items"], list)
        for item in partial["items"]:
            assert item["phoneNumber"] is None
            assert item["phoneNumberStatus"] == "permission_required"
        review = native_call(protocol, "simList", {"autoRequestPermissions": True})
        assert review["permissionRequest"]["requestedRequirementId"] == "phoneNumbers"
        assert review["permissionRequest"]["autoRequested"] is True
        grant_permissions((PHONE_NUMBERS,))
        complete = native_call(protocol, "simList")
        assert complete["phoneNumberPermissionGranted"] is True
        assert "permissionRequest" not in complete
        assert [item["subscriptionId"] for item in complete["items"]] == [item["subscriptionId"] for item in partial["items"]]
        for item in complete["items"]:
            assert item["phoneNumberStatus"] in {"available", "unavailable", "access_denied", "unsupported"}
            if item["phoneNumberStatus"] == "available":
                assert isinstance(item["phoneNumber"], str) and item["phoneNumber"].strip()
            else:
                assert item["phoneNumber"] is None
        agent.device_command("appops set " + package + " READ_PHONE_NUMBERS ignore")
        denied = native_call(protocol, "simList")
        assert denied["phoneNumberPermissionGranted"] is True
        for item in denied["items"]:
            assert item["phoneNumber"] is None
            assert item["phoneNumberStatus"] in {"unavailable", "access_denied"}
        agent.device_command("appops set " + package + " READ_PHONE_NUMBERS allow")
        identifiers = []
        for offset in range(len(complete["items"]) + 1):
            page = native_call(protocol, "simList", {"limit": 1, "offset": offset})
            identifiers.extend(item["subscriptionId"] for item in page["items"])
            assert page["hasMore"] is (offset + 1 < len(complete["items"]))
        assert identifiers == [item["subscriptionId"] for item in complete["items"]]
        assert native_call(protocol, "simList", {"offset": 10000})["items"] == []
        for invalid in ({"limit": 0}, {"limit": 201}, {"offset": -1}, {"unknown": True}):
            native_call(protocol, "simList", invalid, error="validation_failed")
        agent.device_command("pm revoke " + package + " " + PHONE_STATE)
        reopen_home()
        native_call(protocol, "simList", error="native_permission_telephony")
    finally:
        agent.device_command("appops set " + package + " READ_PHONE_NUMBERS default")
        for permission, was_granted in granted.items():
            operation = "grant" if was_granted else "revoke"
            agent.device_command("pm " + operation + " " + package + " " + permission)
        reopen_home()


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
@pytest.mark.parametrize("arguments_json", ["{}", "{"])
def test_sim_numbers_chat_rejected_call_has_live_contract(websocket_control, provider_endpoint, provider, arguments_json):
    endpoint, script, records = provider_endpoint
    script.update(wrapper_calls=[("phone_call", {"name": "dikciz_content_query", "arguments_json": arguments_json})],
                  text="The rejected call supplied its current required parameters.")
    _, identifier = agent.conversation(endpoint, provider)
    try:
        agent.send(identifier)
        rows = agent.wait_turn(identifier)
        result = next(row for row in rows if row["role"] == "tool")
        result = json.loads(json.loads(result["blocks"])[0]["text"])
        assert result["isError"] is True
        assert result.get("structuredContent", result)["code"] == "validation_failed"
        contract = result["toolContract"]
        assert contract["name"] == "dikciz_content_query"
        assert "resource" in contract["inputSchema"]["required"]
        assert contract["inputSchema"]["properties"]["resource"]["type"] == "string"
        assert contract["inputSchema"]["additionalProperties"] is False
        assert "toolContract" in json.dumps(records[-1]["body"])
        assert rows[-1]["state"] == "complete"
    finally:
        agent.cleanup(identifier)


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
@pytest.mark.parametrize("saved_read_only", [False, True])
def test_sim_numbers_chat_access_context_survives_screen_task(websocket_control, provider_endpoint, provider, saved_read_only):
    endpoint, script, records = provider_endpoint
    script.update(text="The captured screen is available.")
    _, identifier = agent.conversation(endpoint, provider, readOnly=saved_read_only)
    try:
        agent.emulator_dikciz_accessibility_set(True)
        agent.chat("task", conversationId=identifier, task="explainScreen", requestKey=str(uuid.uuid4()))
        agent.wait_turn(identifier)
        context = request_access_context(records[0]["body"])
        assert context["conversationReadOnly"] is saved_read_only
        assert context["turnReadOnly"] is True
        assert context["reason"] == ("conversation_setting" if saved_read_only else "screen_explanation")
        assert context["writeAccessResumesNextTurn"] is not saved_read_only
        assert agent.chat("settings", conversationId=identifier)["settings"]["readOnly"] is saved_read_only
        previous_count = len(records)
        agent.send(identifier, "Continue with the current access context.")
        agent.wait_turn(identifier)
        context = request_access_context(records[previous_count]["body"])
        assert context["conversationReadOnly"] is saved_read_only
        assert context["turnReadOnly"] is saved_read_only
        assert context["writeAccessResumesNextTurn"] is False
        assert context["reason"] == ("conversation_setting" if saved_read_only else "writable_conversation")
    finally:
        agent.cleanup(identifier)
        agent.emulator_dikciz_accessibility_set(False)


@pytest.mark.parametrize("binding", ["html", "lua"])
def test_sim_numbers_embedded_bindings_match_native_result(websocket_control, binding):
    from .test_entrypoint_parity import parity_html_configuration, wait_for_parity_response

    package = agent.DIKCIZ_DEBUG_PACKAGE
    previous = agent.device_command("dumpsys package " + package)
    granted = {permission: permission + ": granted=true" in previous for permission in (PHONE_STATE, PHONE_NUMBERS)}
    grant_permissions((PHONE_STATE, PHONE_NUMBERS))
    original = native_call("http", "configGet")["config"]
    expected = native_call("http", "simList")
    try:
        if binding == "html":
            javascript = """window.addEventListener("dikciz-ready", async () => {
  const response = await dikciz.command("simList", {});
  await selfWidget.patchState({parityResponse: response});
});"""
            configured = parity_html_configuration(original, javascript)
            native_call("http", "configReplace", {"config": configured})
            assert wait_for_parity_response(websocket_control) == expected
        else:
            source = """function on_event(event)
  local reply = context.requestJson({type = "simList"})
  return { actions = {{ type = "patchState", values = { parityReply = reply } }} }
end"""
            configured = agent.automation_configuration(original, source, {}, ["manualTrigger"], ["patchState"],
                [{"event": "manual", "minimumIntervalMilliseconds": 0, "coalescingKey": "sim-contract"}])
            native_call("http", "configReplace", {"config": configured})
            native_call("http", "automationTrigger", {"scriptId": agent.AUTOMATION_LIVE_SCRIPT_ID})
            deadline = time.monotonic() + agent.TURN_TIMEOUT
            observed = None
            while time.monotonic() < deadline:
                config = native_call("http", "configGet")["config"]
                script = next(item for item in config["scripts"] if item["id"] == agent.AUTOMATION_LIVE_SCRIPT_ID)
                observed = script["state"].get("parityReply")
                if isinstance(observed, str):
                    break
                time.sleep(0.2)
            assert isinstance(observed, str), "The Lua SIM read did not save its response"
            response = json.loads(observed)
            assert response["type"] == "result"
            assert response["result"] == expected
    finally:
        native_call("http", "configReplace", {"config": original})
        for permission, was_granted in granted.items():
            operation = "grant" if was_granted else "revoke"
            agent.device_command("pm " + operation + " " + package + " " + permission)
        reopen_home()
