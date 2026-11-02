'''Public SDK execution through shared entrypoints and trusted assistant access.'''

import json
import uuid
import threading
import time

import pytest

from . import test_chat_agent as agent
from .test_chat_agent import provider_endpoint
from .test_native_data import PROTOCOLS, native_call

websocket_control = agent.websocket_control


def sdk(protocol, class_name, member, arguments=None, write=False, **options):
    request = {"className": class_name, "member": member, "arguments": arguments or [], **options}
    if write:
        request.setdefault("idempotencyKey", uuid.uuid4().hex)
    return native_call(protocol, "androidApiInvoke" if write else "androidApiRead", request)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_android_sdk_native_contracts(websocket_control, protocol):
    listing = native_call(protocol, "androidApiDiscover", {"query": "android.telephony.SubscriptionManager", "limit": 5})
    assert listing["catalogue"]["classCount"] > 5000
    assert listing["catalogue"]["defaultAccess"] == "write"
    assert any(row["name"] == "android.telephony.SubscriptionManager" for row in listing["items"])
    page = native_call(protocol, "androidApiDiscover", {"className": "android.os.SystemClock"})
    assert any(m["id"] == "elapsedRealtime()" and m["readOnly"] and m["runtimeAvailable"] for m in page["items"])
    assert sdk(protocol, "java.lang.Math", "max(int,int)", [4, 19])["result"] == 19
    assert sdk(protocol, "android.os.SystemClock", "uptimeMillis()")["result"] > 0
    assert sdk(protocol, "android.os.BatteryManager", "getIntProperty(int)", [4], target="service:batterymanager")["state"] == "completed"
    memory = sdk(protocol, "android.app.ActivityManager.MemoryInfo", "new()")["result"]["objectRef"]
    try:
        sdk(protocol, "android.app.ActivityManager", "getMemoryInfo(android.app.ActivityManager.MemoryInfo)", [{"objectRef": memory}], target="service:activity")
        assert sdk(protocol, "android.app.ActivityManager.MemoryInfo", "field:totalMem", target=memory)["result"] > 0
        native_call(protocol, "androidApiRead", {"className": "android.app.ActivityManager.MemoryInfo", "member": "set:totalMem", "arguments": [0], "target": "nonexistent"}, error="read_only_violation")
    finally:
        native_call(protocol, "androidApiRelease", {"objectRef": memory})
    for arguments in ([1], [1, 2, 3], [1.5, 2], [None, 2], [2**40, 2], [{"objectRef": "missing"}, 2]):
        native_call(protocol, "androidApiRead", {"className": "java.lang.Math", "member": "max(int,int)", "arguments": arguments}, error=("validation_failed", "android_api_object_not_found"))
    for limit, offset in [(0, 0), (201, 0), (1, -1)]:
        native_call(protocol, "androidApiDiscover", {"limit": limit, "offset": offset}, error="validation_failed")
    native_call(protocol, "androidApiRead", {"className": "android.os.SystemClock", "member": "sleep(long)", "arguments": [{"objectRef": "missing"}]}, error="read_only_violation")
    native_call(protocol, "androidApiInvoke", {"className": "java.lang.Runtime", "member": "getRuntime()", "arguments": [], "idempotencyKey": uuid.uuid4().hex}, error="android_api_bridge_restricted")
    native_call(protocol, "androidApiDiscover", {"className": "org.fossify.home.dikciz.DikcizChatSettings"}, error="android_api_class_not_found")
    for path in ("/sdcard/Dikciz/control/remote-auth.json", "file:///sdcard/Dikciz/control/remote%2Dauth.json"):
        native_call(protocol, "androidApiInvoke", {"className": "android.net.Uri", "member": "parse(java.lang.String)", "arguments": [path], "idempotencyKey": uuid.uuid4().hex}, error="android_api_protected_path")
    native_call(protocol, "androidApiInvoke", {"className": "android.os.Debug", "member": "dumpHprofData(java.lang.String)", "arguments": ["/sdcard/Dikciz/heap.hprof"], "idempotencyKey": uuid.uuid4().hex}, error="android_api_bridge_restricted")



@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_android_sdk_idempotency_callback_and_pagination(websocket_control, protocol):
    invalid_key = uuid.uuid4().hex
    native_call(protocol, "androidApiInvoke", {"className": "android.os.Bundle", "member": "new()", "arguments": [1], "idempotencyKey": invalid_key}, error="validation_failed")
    recovered = sdk(protocol, "android.os.Bundle", "new()", write=True, idempotencyKey=invalid_key)
    native_call(protocol, "androidApiRelease", {"objectRef": recovered["result"]["objectRef"]})
    key = uuid.uuid4().hex
    first = sdk(protocol, "android.os.Bundle", "new()", write=True, idempotencyKey=key)
    second = sdk(protocol, "android.os.Bundle", "new()", write=True, idempotencyKey=key)
    assert second["replayed"] is True and first["result"] == second["result"]
    native_call(protocol, "androidApiInvoke", {"className": "android.os.Bundle", "member": "new(int)", "arguments": [1], "idempotencyKey": key}, error="idempotency_conflict")
    # The constructor is writable even when its instance can later be read.
    native_call(protocol, "androidApiRead", {"className": "android.os.Bundle", "member": "new()", "arguments": []}, error="read_only_violation")
    ref = first["result"]["objectRef"]
    callback = native_call(protocol, "androidApiCallback", {"className": "java.lang.Runnable"})["objectRef"]
    try:
        expected = ["value" + str(i) for i in range(13)]
        sdk(protocol, "android.os.Bundle", "putStringArray(java.lang.String,java.lang.String[])", ["fixture", expected], target=ref, write=True)
        array = sdk(protocol, "android.os.Bundle", "getStringArray(java.lang.String)", ["fixture"], target=ref, write=True)["result"]["objectRef"]
        try:
            values = []
            for offset, count in [(0, 5), (5, 5), (10, 3), (13, 0), (999999, 0)]:
                page = native_call(protocol, "androidApiObjects", {"objectRef": array, "offset": offset, "limit": 5})
                assert len(page["items"]) == count
                assert page["hasMore"] is (offset < 10)
                values.extend(item["value"] for item in page["items"])
            assert values == expected
        finally:
            native_call(protocol, "androidApiRelease", {"objectRef": array})
        sdk(protocol, "java.lang.Runnable", "run()", target=callback, write=True)
        events = native_call(protocol, "androidApiObjects", {"objectRef": callback})
        sdk_callback = native_call(protocol, "androidApiCallback", {"className": "android.hardware.SensorManager.DynamicSensorCallback"})["objectRef"]
        try:
            assert native_call(protocol, "androidApiObjects", {"objectRef": sdk_callback})["items"] == []
        finally:
            native_call(protocol, "androidApiRelease", {"objectRef": sdk_callback})
        assert events["items"][0]["member"] == "run()" and events["items"][0]["arguments"] == []
        assert native_call(protocol, "androidApiObjects", {"objectRef": callback, "afterSequence": 1})["items"] == []
        collected = []
        for offset in range(0, 1000, 5):
            page = native_call(protocol, "androidApiDiscover", {"className": "java.lang.String", "limit": 5, "offset": offset})
            collected.extend(m["id"] for m in page["items"])
            if not page["hasMore"]:
                break
        else:
            raise AssertionError("SDK pagination did not terminate")
        assert len(collected) == len(set(collected)) > 5
        assert native_call(protocol, "androidApiDiscover", {"className": "java.lang.String", "offset": len(collected)})["items"] == []
        assert native_call(protocol, "androidApiDiscover", {"className": "java.lang.String", "offset": 999999})["items"] == []
    finally:
        for handle in (ref, callback):
            native_call(protocol, "androidApiRelease", {"objectRef": handle})
    native_call(protocol, "androidApiRelease", {"objectRef": ref}, error="android_api_object_not_found")


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
def test_android_sdk_assistant_read_only(websocket_control, provider_endpoint, provider):
    endpoint, script, records = provider_endpoint
    _, identifier = agent.conversation(endpoint, provider)
    try:
        agent.chat("configure", conversationId=identifier, settings={"readOnly": True})
        calls = [
            ("dikciz_android_api_discover", {"className": "android.os.SystemClock"}),
            ("dikciz_android_api_read", {"className": "android.os.SystemClock", "member": "sleep(long)", "arguments": [{"objectRef": "missing"}]}),
            ("dikciz_android_api_invoke", {"className": "android.os.SystemClock", "member": "sleep(long)", "arguments": [0], "idempotencyKey": uuid.uuid4().hex, "readOnly": False}),
            ("dikciz_android_api_callback", {"className": "java.lang.Runnable"}),
            ("dikciz_android_api_release", {"objectRef": "missing"}),
            ("dikciz_android_api_read", {"className": "android.os.SystemClock", "member": "uptimeMillis()", "arguments": [], "autoRequestPermissions": True}),
        ]
        script.update(text="Read-only execution checked.", wrapper_calls=[("phone_tools", {"query": "android_api"})] + [
            ("phone_call", {"name": name, "arguments_json": json.dumps(arguments)}) for name, arguments in calls
        ])
        agent.send(identifier)
        rows = agent.wait_turn(identifier)
        results = [json.loads(json.loads(row["blocks"])[0]["text"]) for row in rows if row["role"] == "tool"]
        assert len(results) == len(calls) + 1
        names = {t["name"] for t in results[0]["tools"]}
        assert names == {"dikciz_android_api_discover", "dikciz_android_api_read", "dikciz_android_api_objects"}
        assert all("autoRequestPermissions" not in t["inputSchema"]["properties"] for t in results[0]["tools"])
        discovery = results[1].get("structuredContent", results[1])
        assert discovery["readOnly"] is True
        assert all(m["readOnly"] for m in discovery["items"])
        assert "sleep(long)" not in {m["id"] for m in discovery["items"]}
        assert all(r.get("structuredContent", r)["code"] == "read_only_violation" for r in results[2:])
        assert agent.chat("settings", conversationId=identifier)["settings"]["readOnly"] is True
    finally:
        agent.cleanup(identifier)


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
def test_android_sdk_read_only_revoked_during_turn(websocket_control, provider_endpoint, provider):
    endpoint, script, records = provider_endpoint
    _, identifier = agent.conversation(endpoint, provider)
    path = "/sdcard/Dikciz/sdk-access-revocation-" + uuid.uuid4().hex + ".txt"
    arrived = threading.Event()
    release = threading.Event()
    arguments = {"name": "dikciz_file_write", "arguments_json": json.dumps({"path": path, "dataBase64": "eA=="})}

    class DelayedArguments(list):
        def __getitem__(self, index):
            if index == 0:
                arrived.set()
                assert release.wait(agent.TURN_TIMEOUT)
            return super().__getitem__(index)

        def __iter__(self):
            arrived.set()
            assert release.wait(agent.TURN_TIMEOUT)
            return super().__iter__()

    script.update(tools=[("dikciz_file_write", {})], argument_chunks=DelayedArguments([json.dumps(arguments)]), text="Access revocation checked.")
    try:
        agent.send(identifier)
        assert arrived.wait(agent.TURN_TIMEOUT)
        agent.chat("configure", conversationId=identifier, settings={"readOnly": True})
        release.set()
        rows = agent.wait_turn(identifier)
        result = next(json.loads(json.loads(row["blocks"])[0]["text"]) for row in rows if row["role"] == "tool")
        assert result.get("structuredContent", result).get("code") == "read_only_violation", result
        native_call("http", "fileRead", {"path": path}, error="file_not_found")
    finally:
        release.set()
        agent.cleanup(identifier)


@pytest.mark.parametrize("binding", ["html", "lua"])
def test_android_sdk_acceptance_embedded_bindings(websocket_control, binding):
    from .test_entrypoint_parity import parity_html_configuration, wait_for_parity_response

    original = native_call("http", "configGet")["config"]
    request = {"type": "androidApiRead", "className": "java.lang.Math", "member": "max(int,int)", "arguments": [5, 13]}
    denied = {"type": "androidApiRead", "className": "android.os.SystemClock", "member": "sleep(long)", "arguments": [1]}
    try:
        if binding == "html":
            javascript = 'window.addEventListener("dikciz-ready", async () => {const read = await dikciz.request(' + json.dumps(request) + '); const denied = await dikciz.request(' + json.dumps(denied) + '); await selfWidget.patchState({parityResponse:{read,denied}});});'
            native_call("http", "configReplace", {"config": parity_html_configuration(original, javascript)})
            result = wait_for_parity_response(websocket_control)
        else:
            source = '''function on_event(event)
  local read = context.requestJson({type="androidApiRead",className="java.lang.Math",member="max(int,int)",arguments={5,13}})
  local denied = context.requestJson({type="androidApiRead",className="android.os.SystemClock",member="sleep(long)",arguments={1}})
  return {actions={{type="patchState",values={sdkRead=read,sdkDenied=denied}}}}
end'''
            configured = agent.automation_configuration(original, source, {}, ["manualTrigger"], ["patchState"],
                [{"event": "manual", "minimumIntervalMilliseconds": 0, "coalescingKey": "sdk-contract"}])
            native_call("http", "configReplace", {"config": configured})
            native_call("http", "automationTrigger", {"scriptId": agent.AUTOMATION_LIVE_SCRIPT_ID})
            deadline = time.monotonic() + agent.TURN_TIMEOUT
            while time.monotonic() < deadline:
                config = native_call("http", "configGet")["config"]
                state = next(item["state"] for item in config["scripts"] if item["id"] == agent.AUTOMATION_LIVE_SCRIPT_ID)
                if "sdkRead" in state and "sdkDenied" in state:
                    result = {"read": json.loads(state["sdkRead"]), "denied": json.loads(state["sdkDenied"])}
                    break
                time.sleep(0.2)
            else:
                raise AssertionError("SDK Lua result was not persisted")
        assert result["read"]["type"] == "result"
        assert result["read"]["result"]["result"] == 13
        assert result["denied"]["type"] == "error"
        assert result["denied"]["code"] == "read_only_violation"
    finally:
        native_call("http", "configReplace", {"config": original})


def test_android_sdk_acceptance_packaged_sections(websocket_control):
    from .test_mcp_resources import test_mcp_resources_walk_all_pages_and_read_each_section
    test_mcp_resources_walk_all_pages_and_read_each_section(websocket_control)


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
@pytest.mark.parametrize("saved_read_only", [False, True])
def test_android_sdk_acceptance_screen_access_context(websocket_control, provider_endpoint, provider, saved_read_only):
    from .test_sim_numbers_and_chat_contract import test_sim_numbers_chat_access_context_survives_screen_task
    test_sim_numbers_chat_access_context_survives_screen_task(websocket_control, provider_endpoint, provider, saved_read_only)
