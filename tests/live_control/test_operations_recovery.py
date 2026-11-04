"""Durable replay and real Lua simulation through the shared command target."""

import uuid

import pytest

from . import *
from .test_native_data import PROTOCOLS, native_call
from .test_event_replay import emit_replay, await_replay
from .test_capability_expansion import wait_native_control
from .test_chat_agent import provider_endpoint


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_operations_durable_consumer_acknowledgements(websocket_control, protocol):
    before = native_call(protocol, "eventJournalStatus")
    name = "fixture.consumer." + uuid.uuid4().hex
    event = "fixture.ack." + uuid.uuid4().hex
    native_call(protocol, "eventConsumerAck", {"name": name, "cursor": before["lastSequence"]})
    try:
        for index in range(3):
            emit_replay(websocket_control, event, index)
        await_replay(before, event, 3)
        fields = {"name": name, "eventTypes": [event], "limit": 2}
        first = native_call(protocol, "eventConsumerRead", fields)
        duplicate = native_call(protocol, "eventConsumerRead", fields)
        assert [row["sequence"] for row in first["events"]] == [row["sequence"] for row in duplicate["events"]]
        assert len(first["events"]) == 2 and first["hasMore"]
        native_call(protocol, "eventConsumerAck", {"name": name, "cursor": first["nextCursor"]})
        last = native_call(protocol, "eventConsumerRead", fields)
        assert len(last["events"]) == 1 and not last["hasMore"]
        native_call(protocol, "eventConsumerAck", {"name": name, "cursor": last["nextCursor"]})
        assert native_call(protocol, "eventConsumerRead", fields)["events"] == []
        native_call(protocol, "eventConsumerAck", {"name": name, "cursor": first["nextCursor"]},
                    error="event_ack_regression")
        native_call(protocol, "eventConsumerAck", {"name": name, "cursor": 9007199254740991},
                    error="validation_failed")
        saved = native_call(protocol, "eventConsumersList", {"limit": 1})
        assert saved["total"] >= 1 and saved["items"]
    finally:
        native_call(protocol, "eventConsumerForget", {"name": name})


def test_operations_event_replay_survives_process_death(websocket_control):
    before = native_call("http", "eventJournalStatus")
    name = "fixture.restart." + uuid.uuid4().hex
    event = "fixture.persisted." + uuid.uuid4().hex
    native_call("http", "eventConsumerAck", {"name": name, "cursor": before["lastSequence"]})
    emit_replay(websocket_control, event, 23)
    await_replay(before, event, 1)
    device_command("am force-stop " + DIKCIZ_DEBUG_PACKAGE)
    restore_system_home()
    wait_native_control("http")
    current = native_call("http", "eventJournalStatus")
    assert current["journalId"] == before["journalId"] and current["persistence"] == "sqlite"
    resumed = native_call("mcp", "eventConsumerRead", {"name": name, "eventTypes": [event]})
    assert [row["event"]["payload"]["index"] for row in resumed["events"]] == [23]
    native_call("http", "eventConsumerForget", {"name": name})


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_operations_visual_rules_simulate_real_lua(websocket_control, protocol):
    phrase = "Fixture text ]=] with quotes and unicode ☃"
    rule = {"title": "Fixture visual rule", "event": "custom", "customEventName": "fixture.rule",
            "minimumIntervalMilliseconds": 60000, "conditionField": "value", "conditionOperator": "gte",
            "conditionValue": 12, "commands": [{"type": "speechSpeak", "text": phrase}]}
    preview = native_call(protocol, "automationRulePreview", {"rule": rule, "sample": {"value": 12}})
    assert preview["simulated"] and preview["matched"]
    assert preview["commands"] == rule["commands"]
    assert native_call(protocol, "automationRulePreview", {"rule": rule, "sample": {"value": 11}})["commands"] == []
    assert native_call(protocol, "automationRulePreview", {"rule": rule, "sample": {}})["commands"] == []
    saved = native_call(protocol, "automationRuleSave", {"rule": rule})
    assert saved["rule"]["enabled"] is False
    document = AUTOMATION.request(websocket_control, "configGet")["config"]
    script = next(row for row in document["scripts"] if row["id"] == saved["scriptId"])
    assert script["enabled"] is False and script["source"] == preview["source"]
    assert any(row["id"] == saved["scriptId"] for row in native_call(protocol, "automationRulesList")["items"])
    assert native_call(protocol, "automationRuleDelete", {"id": saved["scriptId"]})["deleted"]
    assert all(row["id"] != saved["scriptId"] for row in native_call(protocol, "automationRulesList")["items"])
    native_call(protocol, "automationRuleDelete", {"id": saved["scriptId"]}, error="native_record_not_found")
    native_call(protocol, "automationRuleSave", {"rule": {**rule, "event": "alarm", "alarmIntervalMilliseconds": 1000}}, error="validation_failed")
    native_call(protocol, "automationRulePreview", {"rule": {**rule, "conditionOperator": "untrusted"}},
                error="validation_failed")
    native_call(protocol, "automationRuleSave", {"rule": {**rule, "commands": [{"type": "shell", "command": "true"}]}},
                error="validation_failed")
    native_call(protocol, "automationRuleSave", {"rule": {**rule, "commands": [{"type": "torchControl", "action": "on"}]}},
                error="validation_failed")


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_operations_resource_controls(websocket_control, provider_endpoint, protocol):
    from .test_chat_agent import conversation, chat
    config = AUTOMATION.request(websocket_control, "configGet")["config"]
    page = next(page for page in config["launcher"]["home"]["pages"] if any(widget["type"] == "html" for widget in page["widgets"]))
    widget = next(widget for widget in page["widgets"] if widget["type"] == "html")
    native_call(protocol, "resourceControl", {"target": "widget", "id": widget["id"]}, error="validation_failed")
    native_call(protocol, "resourceControl", {"target": "widget", "id": widget["id"], "pageId": "missing"}, error="native_record_not_found")
    native_call(protocol, "resourceControl", {"target": "widget", "id": widget["id"], "pageId": page["id"]})
    current = AUTOMATION.request(websocket_control, "configGet")["config"]
    updated = next(row for row in current["launcher"]["home"]["pages"] if row["id"] == page["id"])
    assert next(row for row in updated["widgets"] if row["id"] == widget["id"])["enabled"] is False
    for row in updated["widgets"]:
        if row["id"] != widget["id"]:
            assert row == next(original for original in page["widgets"] if original["id"] == row["id"])
    profile, identifier = conversation(provider_endpoint[0])
    chat("subscribe", conversationId=identifier, rules=[{"id": "fixture-pause", "subscription": {"event": "fixture.pause", "minimumIntervalMilliseconds": 60000},
         "instructions": "Report the fixture event"}])
    paused = native_call(protocol, "resourceControl", {"target": "conversation", "id": identifier})
    assert paused["eventRulesDisabled"] and chat("read", conversationId=identifier)["conversation"]["subscriptions"] == "[]"
    native_call(protocol, "resourceControl", {"target": "arbitrary", "id": "missing"}, error="validation_failed")
    native_call(protocol, "resourceControl", {}, error="validation_failed")
    native_call(protocol, "resourceControl", {"target": "widget", "scriptId": "missing"}, error="validation_failed")
    resources = native_call(protocol, "resourceUsage")
    assert resources["widgets"] and resources["conversations"]["items"]
    actual = AUTOMATION.request(websocket_control, "configGet")["config"]
    expected_ids = {widget["id"] for page in actual["launcher"]["home"]["pages"] for widget in page["widgets"] if widget["type"] == "html"}
    assert {widget["id"] for widget in resources["widgets"]} == expected_ids
    assert {script["id"] for script in resources["scripts"]} == {script["id"] for script in actual["scripts"]}
    assert "items" in resources["imageStreams"]


def test_operations_rapid_event_pause_keeps_services_alive(websocket_control, provider_endpoint):
    from .test_chat_agent import conversation, chat, cleanup
    identifier = conversation(provider_endpoint[0])[1]
    before = native_call("http", "deviceInfo")["application"]["instanceId"]
    rules = [{"id": "fixture-toggle", "subscription": {"event": "fixture.toggle", "minimumIntervalMilliseconds": 60000},
              "instructions": "Report the fixture event"}]
    try:
        for index in range(12):
            protocol = PROTOCOLS[index % len(PROTOCOLS)]
            chat("subscribe", conversationId=identifier, rules=rules)
            native_call(protocol, "resourceControl", {"target": "conversation", "id": identifier})
        time.sleep(7)
        assert native_call("http", "deviceInfo")["application"]["instanceId"] == before
        assert chat("read", conversationId=identifier)["conversation"]["subscriptions"] == "[]"
    finally:
        cleanup(identifier)


def test_operations_cancel_interrupts_the_selected_worker(websocket_control):
    import concurrent.futures
    from .test_remote_tokens import http_command
    started = time.monotonic()
    with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
        pending = pool.submit(http_command, None, "shell", {"command": "sleep 5", "root": False, "timeoutMilliseconds": 10000})
        deadline = time.monotonic() + 4
        while True:
            rows = native_call("http", "activityRead", {"outcome": "running"})["items"]
            row = next((item for item in rows if item["command"] == "shell"), None)
            if row is not None:
                break
            assert time.monotonic() < deadline, rows
        response = native_call("http", "activityCancel", {"activityId": row["id"]})
        assert response["cancellationRequested"] and response["state"] == "requested"
        status, result = pending.result(timeout=4)
        assert status == 200 and result["type"] in ("result", "error"), result
        assert time.monotonic() - started < 5
    completed = native_call("http", "activityRead")["items"]
    final = next(item for item in completed if item["id"] == row["id"])
    assert final["outcome"] != "running" and not final["cancellationRequested"]
    assert native_call("http", "deviceInfo")["application"]["packageName"] == DIKCIZ_DEBUG_PACKAGE
