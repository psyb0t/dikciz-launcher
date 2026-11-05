"""Headless JavaScript shares script persistence, events and validated effects with Lua."""

import base64
import copy
import json
import time
import zipfile

import pytest

from . import *
from .test_native_data import native_call, PROTOCOLS
from .script_status import wait_script_result

JS_SOURCE = """
function onEvent(event) {
    const reply = context.request({type: "deviceInfo"});
    if (reply.type !== "result") throw new Error("Native command failed");
    const count = (context.state.count || 0) + 1;
    return {status: "JS completed", actions: [
        {type: "patchState", values: {count: count, headless: typeof window === "undefined" && typeof document === "undefined",
            javaHidden: typeof java === "undefined" && typeof Packages === "undefined",
            version: reply.result.application.versionName}},
        {type: "emitEvent", event: "example.backend.done", payload: {count: count}}
    ]};
}
"""


def js_configuration(connection, source=JS_SOURCE, actions=None, subscriptions=None):
    original = AUTOMATION.require_configuration(AUTOMATION.request(connection, "configGet"))
    original["scripts"] = [item for item in original["scripts"] if item["id"] != AUTOMATION_LIVE_SCRIPT_ID]
    configured = automation_configuration(original, source, {"count": 0}, ["manualTrigger"],
        actions if actions is not None else ["patchState", "emitEvent"],
        subscriptions if subscriptions is not None else [{"event": "manual", "minimumIntervalMilliseconds": 0}])
    configured["scripts"][-1]["language"] = "javascript"
    return configured


def install_js(connection, source=JS_SOURCE, **kwargs):
    configured = js_configuration(connection, source, **kwargs)
    AUTOMATION.request(connection, "configReplace", config=configured)
    return configured


def saved_script(connection, identifier=AUTOMATION_LIVE_SCRIPT_ID):
    configured = AUTOMATION.require_configuration(AUTOMATION.request(connection, "configGet"))
    return next(script for script in configured["scripts"] if script["id"] == identifier)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_background_javascript_manual_native_commands_and_cross_language_events(websocket_control, protocol):
    configured = js_configuration(websocket_control)
    configured["scripts"].append({"id": "js-event-observer", "title": "Lua observer", "enabled": True, "apiVersion": 1,
        "source": 'function on_event(event) return {actions={{type="patchState",values={seen=event.payload.count}}}} end', "state": {}})
    configured["automation"]["scripts"].append({"scriptId": "js-event-observer", "policyId": AUTOMATION_LIVE_POLICY_ID,
        "enabled": True, "subscriptions": [{"event": "example.backend.done", "minimumIntervalMilliseconds": 0}]})
    native_call(protocol, "configReplace", {"config": configured})
    native_call(protocol, "automationTrigger", {"scriptId": AUTOMATION_LIVE_SCRIPT_ID})
    try:
        wait_for_automation_script_state(websocket_control, AUTOMATION_LIVE_SCRIPT_ID, "count", 1)
    except AssertionError as failure:
        status = device_command(f"cat /sdcard/Dikciz/scripts/{AUTOMATION_LIVE_SCRIPT_ID}/run-status.json")
        raise AssertionError(f"JavaScript did not update state: {status}") from failure
    wait_for_automation_script_state(websocket_control, "js-event-observer", "seen", 1)
    result = saved_script(websocket_control)
    assert result["language"] == "javascript"
    assert result["state"]["headless"] and result["state"]["javaHidden"] and result["state"]["version"]
    assert device_command(f"cat /sdcard/Dikciz/scripts/{AUTOMATION_LIVE_SCRIPT_ID}/main.js") == result["source"]
    metadata = json.loads(device_command(f"cat /sdcard/Dikciz/scripts/{AUTOMATION_LIVE_SCRIPT_ID}/script.json"))
    assert metadata["language"] == "javascript"
    assert wait_script_result(AUTOMATION_LIVE_SCRIPT_ID, "completed")["status"] == "JS completed"
