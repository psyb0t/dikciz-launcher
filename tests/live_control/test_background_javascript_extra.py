"""Background JavaScript validation and lifecycle on the isolated emulator."""

import base64
import json
import time

import pytest

from . import *
from .test_background_javascript import install_js, js_configuration, saved_script
from .test_native_data import native_call

RESULT_TIMEOUT_SECONDS = 20
RESULT_POLL_SECONDS = .2
ALARM_INTERVAL_MILLISECONDS = 60000


def trigger_and_wait_result(connection, outcome):
    path = f"/sdcard/Dikciz/scripts/{AUTOMATION_LIVE_SCRIPT_ID}/run-status.json"
    command = f"if test -f {path}; then cat {path}; else echo '{{}}'; fi"
    before = json.loads(device_command(command)).get("lastRunAt", 0)
    AUTOMATION.request(connection, "automationTrigger", scriptId=AUTOMATION_LIVE_SCRIPT_ID)
    deadline = time.monotonic() + RESULT_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        result = json.loads(device_command(command))
        if result.get("lastRunAt", 0) > before and result.get("outcome") == outcome:
            return result
        time.sleep(RESULT_POLL_SECONDS)
    raise AssertionError(f"Fresh {outcome} outcome missing: {result}")


@pytest.mark.parametrize("language", [None, False, 7, "", "python", "JavaScript"])
def test_background_javascript_language_validation_is_atomic(websocket_control, language):
    configured = js_configuration(websocket_control)
    configured["scripts"][-1]["language"] = language
    before = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
    _, error = AUTOMATION.request_error_with_id(websocket_control, "configReplace", config=configured)
    assert error["code"] == "validation_failed"
    assert AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet")) == before


@pytest.mark.parametrize("source,reasons", [
    ("function onEvent( {", {"invalid_javascript"}),
    ("function onEvent(event) { return java.lang.System.getProperties(); }", {"invalid_javascript"}),
    ("function onEvent(event) { return context.request.getClass(); }", {"invalid_javascript"}),
    ('"use strict"; function onEvent(event) { context.state.count = 42; }', {"invalid_javascript"}),
    ("function onEvent(event) { while (true) {} }", {"instruction_budget_exceeded", "execution_deadline_exceeded"}),
    ("function onEvent(event) { return onEvent(event); }", {"invalid_javascript", "instruction_budget_exceeded", "execution_deadline_exceeded"}),
    ("function onEvent(event) { return 42; }", {"invalid_result"}),
    ("function onEvent(event) { return Promise.resolve({status: 'wrong'}); }", {"invalid_result"}),
])
def test_background_javascript_failures_are_bounded_and_recorded(websocket_control, source, reasons):
    install_js(websocket_control, source)
    result = trigger_and_wait_result(websocket_control, "failed")
    assert result["status"] in reasons, result
    assert saved_script(websocket_control)["state"] == {"count": 0}
    assert native_call("http", "deviceInfo")["application"]["enabled"]


def test_background_javascript_fresh_scope_policy_and_source_reload(websocket_control):
    source = "var localCount = 0; function onEvent(event) { localCount++; return {actions: [{type: 'patchState', values: {local: localCount, saved: (context.state.saved || 0) + 1}}]}; }"
    install_js(websocket_control, source)
    trigger_and_wait_result(websocket_control, "completed")
    trigger_and_wait_result(websocket_control, "completed")
    assert saved_script(websocket_control)["state"] == {"count": 0, "local": 1, "saved": 2}
    replacement = "function onEvent(event) { return {actions: [{type: 'patchState', values: {updated: true}}]}; }"
    encoded = base64.b64encode(replacement.encode()).decode()
    device_command(f"printf %s {encoded} | base64 -d > /sdcard/Dikciz/scripts/{AUTOMATION_LIVE_SCRIPT_ID}/main.js")
    deadline = time.monotonic() + RESULT_TIMEOUT_SECONDS
    while time.monotonic() < deadline and saved_script(websocket_control)["source"] != replacement:
        time.sleep(RESULT_POLL_SECONDS)
    assert saved_script(websocket_control)["source"] == replacement
    trigger_and_wait_result(websocket_control, "completed")
    assert saved_script(websocket_control)["state"] == {"count": 0, "local": 1, "saved": 2, "updated": True}
    AUTOMATION.request(websocket_control, "configReplace", config=js_configuration(websocket_control, replacement, actions=[]))
    trigger_and_wait_result(websocket_control, "rejected")
    assert saved_script(websocket_control)["state"] == {"count": 0}


def test_background_javascript_alarm_runs_with_screen_off(websocket_control):
    source = "function onEvent(event) { return {actions: [{type: 'patchState', values: {alarm: event.type, headless: typeof window === 'undefined'}}]}; }"
    configured = js_configuration(websocket_control, source, actions=["patchState"], subscriptions=[{
        "event": "alarm", "minimumIntervalMilliseconds": 0, "alarmIntervalMilliseconds": ALARM_INTERVAL_MILLISECONDS,
    }])
    configured["automation"]["policies"][0]["capabilities"] = ["alarm"]
    AUTOMATION.request(websocket_control, "configReplace", config=configured)
    device_command(f"am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p {DESKCLOCK_APP_PACKAGE}")
    try:
        device_command("input keyevent KEYCODE_SLEEP")
        wait_for_automation_script_state(websocket_control, AUTOMATION_LIVE_SCRIPT_ID, "alarm", "alarm", timeout_seconds=100)
        assert saved_script(websocket_control)["state"]["headless"]
    finally:
        device_command("input keyevent KEYCODE_WAKEUP")
        device_command("wm dismiss-keyguard")
        restore_system_home()
