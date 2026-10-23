"""Replay real automation events across reconnectable ordinary bindings."""

import uuid
from concurrent.futures import ThreadPoolExecutor

from . import *
from .test_native_data import PROTOCOLS, native_call
from .test_capability_expansion import wait_native_control


def emit_replay(websocket_control, name, index, padding=""):
    AUTOMATION.request(websocket_control, "automationDispatch", sourceWidgetAddress="1H1V-welcome",
        actions=[{"type": "emitEvent", "event": name, "payload": {"index": index, "padding": padding}}])


def replay_read(protocol, status, cursor=None, **options):
    return native_call(protocol, "eventJournalRead",
        {"journalId": status["journalId"], "cursor": status["lastSequence"] if cursor is None else cursor, **options})


def await_replay(status, name, count):
    deadline = time.monotonic() + 10
    while True:
        result = replay_read("http", status, eventTypes=[name])
        events = list(result["events"])
        while result["hasMore"]:
            result = replay_read("http", status, result["nextCursor"], eventTypes=[name])
            events.extend(result["events"])
        result["events"] = events
        if len(result["events"]) >= count:
            return result
        assert time.monotonic() < deadline, result
        time.sleep(.1)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_event_replay_pagination_and_wait(websocket_control, protocol):
    status = native_call(protocol, "eventJournalStatus")
    name = "fixture.replay." + uuid.uuid4().hex
    for index in range(7):
        emit_replay(websocket_control, name, index)
    await_replay(status, name, 7)
    cursor, indices, sequences = status["lastSequence"], [], []
    for count, more in ((3, True), (3, True), (1, False)):
        page = replay_read(protocol, status, cursor, eventTypes=[name], limit=3)
        assert len(page["events"]) == count and page["hasMore"] is more
        assert not page["historyGap"] and page["lostEvents"] == 0
        indices.extend(row["event"]["payload"]["index"] for row in page["events"])
        sequences.extend(row["sequence"] for row in page["events"])
        cursor = page["nextCursor"]
    assert indices == list(range(7)) and sequences == sorted(set(sequences))
    assert replay_read(protocol, status, cursor, eventTypes=[name])["events"] == []
    duplicate = replay_read(protocol, status, eventTypes=[name], limit=3)
    assert [row["sequence"] for row in duplicate["events"]] == sequences[:3]
    resumed = replay_read("mcp" if protocol != "mcp" else "http", status, sequences[2], eventTypes=[name], limit=3)
    assert [row["sequence"] for row in resumed["events"]] == sequences[3:6]
    before = time.monotonic()
    empty = replay_read(protocol, status, cursor, eventTypes=[name], waitMilliseconds=100)
    assert not empty["events"] and time.monotonic() - before >= .08
    with ThreadPoolExecutor(max_workers=1) as pool:
        waiting = pool.submit(replay_read, protocol, status, cursor, eventTypes=[name], waitMilliseconds=5000)
        time.sleep(.3)
        emit_replay(websocket_control, name, 7)
        result = waiting.result(timeout=10)
        assert result["events"][0]["event"]["payload"]["index"] == 7
    excluded = replay_read(protocol, status, eventTypes=[name + ".absent"])
    assert excluded["events"] == [] and excluded["nextCursor"] >= result["nextCursor"]
    for options in (
        {"cursor": -1}, {"cursor": "0"}, {"cursor": True},
        {"cursor": 9007199254740992}, {"cursor": 9007199254740991},
        {"limit": 0}, {"limit": 201}, {"waitMilliseconds": -1}, {"waitMilliseconds": 30001},
        {"eventTypes": []}, {"eventTypes": [name, name]}, {"eventTypes": ["bad name"]},
        {"eventTypes": [None]}, {"eventTypes": [True]}, {"eventTypes": [name] * 33},
        {"journalId": "invalid"},
    ):
        native_call(protocol, "eventJournalRead", {"journalId": status["journalId"], **options}, error="validation_failed")
    native_call(protocol, "eventJournalRead", {"journalId": str(uuid.uuid4())}, error="event_journal_restarted")


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_event_replay_large_payload_retention_and_byte_limits(websocket_control, protocol):
    original = native_call(protocol, "eventJournalStatus")
    name = "fixture.replay." + uuid.uuid4().hex
    try:
        native_call(protocol, "eventJournalConfigure", {"maximumEvents": 2})
        for index in range(5):
            emit_replay(websocket_control, name, index)
        deadline = time.monotonic() + 10
        while True:
            state = native_call(protocol, "eventJournalStatus")
            if state["lastSequence"] >= original["lastSequence"] + 5:
                break
            assert time.monotonic() < deadline, state
            time.sleep(.1)
        page = replay_read(protocol, original)
        assert state["retainedEvents"] <= 2 and page["historyGap"] and page["lostEvents"] >= 3
        assert page["evictedEvents"] >= original["evictedEvents"] + 3
        native_call(protocol, "eventJournalConfigure", {"maximumEvents": 2048, "bufferBytes": 65536})
        before = native_call(protocol, "eventJournalStatus")
        for index in range(8):
            emit_replay(websocket_control, name, index, ["x" * 8000] * 3)
        deadline = time.monotonic() + 10
        while True:
            state = native_call(protocol, "eventJournalStatus")
            if state["lastSequence"] >= before["lastSequence"] + 8:
                break
            assert time.monotonic() < deadline, state
            time.sleep(.1)
        assert state["retainedBytes"] <= 65536 and state["evictedEvents"] > before["evictedEvents"]
        for fields in ({"maximumEvents": 0}, {"maximumEvents": 16385}, {"bufferBytes": 65535},
                       {"bufferBytes": 33554433}, {"retentionMinutes": 0}, {"retentionMinutes": 1441}):
            native_call(protocol, "eventJournalConfigure", fields, error="validation_failed")
    finally:
        native_call(protocol, "eventJournalConfigure",
            {key: original[key] for key in ("maximumEvents", "bufferBytes", "retentionMinutes")})


def test_event_replay_expiry_and_restart(websocket_control):
    original = native_call("http", "eventJournalStatus")
    name = "fixture.replay." + uuid.uuid4().hex
    try:
        native_call("http", "eventJournalConfigure", {"retentionMinutes": 1})
        emit_replay(websocket_control, name, 0)
        first = await_replay(original, name, 1)["events"][0]["sequence"]
        deadline = time.monotonic() + 65
        while time.monotonic() < deadline:
            state = native_call("http", "eventJournalStatus")
            if state["firstSequence"] > first:
                break
            time.sleep(1)
        assert state["firstSequence"] > first
        expired = replay_read("http", original, first - 1, eventTypes=[name])
        assert expired["events"] == [] and expired["historyGap"]
    finally:
        native_call("http", "eventJournalConfigure",
            {key: original[key] for key in ("maximumEvents", "bufferBytes", "retentionMinutes")})
    device_command("am force-stop " + DIKCIZ_DEBUG_PACKAGE)
    restore_system_home()
    wait_native_control("http")
    current = native_call("http", "eventJournalStatus")
    assert current["journalId"] != original["journalId"]
    native_call("http", "eventJournalRead", {"journalId": original["journalId"]}, error="event_journal_restarted")


def test_event_replay_large_payload_oversize_and_page_bytes(websocket_control):
    status = native_call("http", "eventJournalStatus")
    name = "fixture.replay." + uuid.uuid4().hex
    emit_replay(websocket_control, name, 0, ["\u2603" * 8000] * 3)
    result = await_replay(status, name, 1)
    assert result["omittedEvents"] == 1
    omitted = result["events"][0]["event"]
    assert omitted["omitted"] and omitted["reason"] == "event_too_large" and omitted["originalBytes"] > 65536
    assert "payload" not in omitted
    before = native_call("http", "eventJournalStatus")
    for index in range(7):
        emit_replay(websocket_control, name, index, ["x" * 8000] * 5)
    await_replay(before, name, 7)
    first = replay_read("http", before, eventTypes=[name], limit=200)
    assert first["hasMore"] and 1 <= len(first["events"]) < 7
    second = replay_read("mcp", before, first["nextCursor"], eventTypes=[name], limit=200)
    assert not second["hasMore"]
    assert [row["event"]["payload"]["index"] for row in first["events"] + second["events"]] == list(range(7))


def test_event_replay_notification_revocation(websocket_control, installed_configurable_provider_fixture):
    from .notification_action import start_fixture_notification_action
    from .test_platform_management import fixture_notification
    package = configurable_provider_fixture_package()
    device_command("pm grant " + package + " android.permission.POST_NOTIFICATIONS")
    device_command("cmd notification allow_listener " + AUTOMATION_NOTIFICATION_COMPONENT)
    try:
        deadline = time.monotonic() + 15
        while True:
            try:
                native_call("http", "notificationsList")
                break
            except AssertionError:
                assert time.monotonic() < deadline
                time.sleep(.1)
        status = native_call("http", "eventJournalStatus")
        start_fixture_notification_action(package)
        fixture_notification("http", package)
        deadline = time.monotonic() + 10
        while True:
            page = replay_read("http", status, eventTypes=["notificationPosted"])
            if any(row["event"]["payload"]["packageName"] == package for row in page["events"]):
                break
            assert time.monotonic() < deadline, page
            time.sleep(.1)
        device_command("cmd notification disallow_listener " + AUTOMATION_NOTIFICATION_COMPONENT)
        for protocol in PROTOCOLS:
            error = native_call(protocol, "eventJournalRead",
                {"journalId": status["journalId"], "cursor": status["lastSequence"], "eventTypes": ["notificationPosted"]},
                error="native_permission_notificationListener")
            assert error["permissionRequest"]["requirementId"] == "notificationListener"
            assert package not in json.dumps(error)
    finally:
        device_command("cmd notification disallow_listener " + AUTOMATION_NOTIFICATION_COMPONENT)


def test_event_replay_html_binding(websocket_control):
    from .test_entrypoint_parity import parity_html_configuration, wait_for_parity_response
    original = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
    javascript = """window.addEventListener('dikciz-ready', async () => {
      const journal = await dikciz.command('eventJournalStatus', {});
      const page = await dikciz.command('eventJournalRead', {
        journalId:journal.journalId,cursor:journal.lastSequence,eventTypes:['fixture.binding.none']
      });
      const haptics = await dikciz.command('vibrationControl', {action:'status'});
      let invalidCamera;
      try { await dikciz.command('cameraCapture', {jpegQuality:0}); }
      catch (error) { invalidCamera = error.code; }
      await selfWidget.patchState({parityResponse:{journal,page,haptics,invalidCamera}});
    });"""
    try:
        AUTOMATION.request(websocket_control, "configReplace", config=parity_html_configuration(original, javascript))
        result = wait_for_parity_response(websocket_control)
        assert result["journal"]["journalId"] == result["page"]["journalId"]
        assert result["page"]["events"] == [] and isinstance(result["haptics"]["effects"], list)
        assert result["invalidCamera"] == "validation_failed"
    finally:
        AUTOMATION.request(websocket_control, "configReplace", config=original)


def test_event_replay_lua_binding(websocket_control):
    original = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
    source = """function on_event(event)
      if event.type ~= 'fixture.replay.binding' then return end
      local journal = context.request({type='eventJournalStatus'})
      local page = context.requestJson({type='eventJournalRead',journalId=journal.result.journalId,
        cursor=journal.result.lastSequence,eventTypes={'fixture.binding.none'}})
      local haptics = context.requestJson({type='vibrationControl',action='status'})
      local invalidCamera = context.requestJson({type='cameraCapture',jpegQuality=0})
      return {actions={{type='patchState',values={page=page,haptics=haptics,invalidCamera=invalidCamera}}}}
    end"""
    configured = automation_configuration(original, source, {}, ["manualTrigger"], ["patchState"],
        [{"event": "fixture.replay.binding", "minimumIntervalMilliseconds": 0}])
    try:
        AUTOMATION.request(websocket_control, "configReplace", config=configured)
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        emit_replay(websocket_control, "fixture.replay.binding", 0)
        deadline = time.monotonic() + 15
        while True:
            current = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
            state = next(script["state"] for script in current["scripts"] if script["id"] == AUTOMATION_LIVE_SCRIPT_ID)
            if "page" in state:
                page, haptics = json.loads(state["page"]), json.loads(state["haptics"])
                assert page["type"] == haptics["type"] == "result", state
                assert page["result"]["events"] == [] and haptics["result"]["effects"]
                assert json.loads(state["invalidCamera"])["code"] == "validation_failed"
                break
            assert time.monotonic() < deadline, state
            time.sleep(.1)
    finally:
        AUTOMATION.request(websocket_control, "configReplace", config=original)
