"""Incremental shell output uses the shared runner across ordinary bindings."""

import base64
import uuid

from . import *
from .test_native_data import native_call, PROTOCOLS

SESSION_WAIT_SECONDS = 20
SESSION_RESPONSE_BYTES = 524288


def drain_session(protocol, identifier, cursor=0):
    deadline = time.monotonic() + SESSION_WAIT_SECONDS
    events = []
    while True:
        result = native_call(protocol, "shellSessionRead", {
            "streamId": identifier, "cursor": cursor, "limit": 200, "waitMilliseconds": 1000,
        })
        assert len(json.dumps(result).encode()) < SESSION_RESPONSE_BYTES
        events.extend(result["events"])
        assert result["nextCursor"] >= cursor
        cursor = result["nextCursor"]
        if not result["active"] and not result["hasMore"]:
            return result, events
        assert time.monotonic() < deadline, result


def clear_sessions(identifiers):
    for identifier in identifiers:
        native_call("http", "shellSessionStop", {"streamId": identifier})
        drain_session("http", identifier)
        native_call("http", "shellSessionForget", {"streamId": identifier})


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_shell_sessions_output_pagination_and_exit(websocket_control, protocol):
    identifiers = []
    try:
        for index in range(7):
            command = "printf first; sleep 1; printf second; exit 7" if index == 0 else "printf session" + str(index)
            started = native_call(protocol, "shellSessionStart", {"command": command, "root": False})
            identifier = started["streamId"]
            identifiers.append(identifier)
            assert started["root"] is False
            terminal, events = drain_session(protocol, identifier)
            assert terminal["state"] == "exited" and not terminal["active"]
            output = b"".join(base64.b64decode(event["outputBase64"]) for event in events if event["event"] == "output")
            assert output == (b"firstsecond" if index == 0 else ("session" + str(index)).encode())
            assert terminal["exitCode"] == (7 if index == 0 else 0)
            assert [event["sequence"] for event in events] == list(range(len(events)))
            assert events[0]["event"] == "started" and events[-1]["event"] == "complete"
            end = native_call(protocol, "shellSessionRead", {"streamId": identifier, "cursor": terminal["nextCursor"]})
            assert end["events"] == [] and not end["hasMore"] and not end["historyGap"]
            native_call(protocol, "shellSessionRead", {"streamId": identifier, "cursor": terminal["nextCursor"] + 1}, error="validation_failed")
        pages, seen = [], []
        offset = 0
        while True:
            page = native_call(protocol, "shellSessionsList", {"offset": offset, "limit": 3})
            pages.append(len(page["items"]))
            seen.extend(item["streamId"] for item in page["items"])
            assert page["total"] == 7
            offset += len(page["items"])
            if not page["hasMore"]:
                break
        assert pages == [3, 3, 1] and seen == list(reversed(identifiers))
        for beyond in (7, 1000000):
            assert native_call(protocol, "shellSessionsList", {"offset": beyond})["items"] == []
        for command, arguments in (
            ("shellSessionStart", {"command": ""}),
            ("shellSessionStart", {"command": "true", "root": "false"}),
            ("shellSessionStart", {"command": "true", "root": False, "timeoutMilliseconds": -1}),
            ("shellSessionStart", {"command": "true", "root": False, "chunkBytes": 65536, "bufferBytes": 65536}),
            ("shellSessionStart", {"command": "true", "root": False, "maximumEvents": 0}),
            ("shellSessionRead", {"streamId": identifiers[0], "limit": 0}),
            ("shellSessionRead", {"streamId": identifiers[0], "waitMilliseconds": 30001}),
            ("shellSessionRead", {"streamId": "../bad"}),
        ):
            native_call(protocol, command, arguments, error="validation_failed")
        native_call(protocol, "shellSessionRead", {"streamId": str(uuid.uuid4())}, error="shell_session_not_found")
    finally:
        clear_sessions(identifiers)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_shell_sessions_replay_gaps_and_response_pages(websocket_control, protocol):
    identifiers = []
    try:
        started = native_call(protocol, "shellSessionStart", {
            "command": "yes x | head -c 700000", "root": False, "chunkBytes": 65536,
        })
        identifiers.append(started["streamId"])
        terminal, events = drain_session(protocol, identifiers[-1])
        output = b"".join(base64.b64decode(event["outputBase64"]) for event in events if event["event"] == "output")
        assert output == b"x\n" * 350000
        assert terminal["outputBytes"] == len(output) and terminal["exitCode"] == 0
        first = native_call(protocol, "shellSessionRead", {"streamId": identifiers[-1], "cursor": 0, "limit": 200})
        assert first["hasMore"] and not first["historyGap"]
        started = native_call(protocol, "shellSessionStart", {
            "command": "yes z | head -c 200000", "root": False, "maximumEvents": 1,
        })
        identifiers.append(started["streamId"])
        terminal, _ = drain_session(protocol, identifiers[-1])
        page = native_call(protocol, "shellSessionRead", {"streamId": identifiers[-1], "cursor": 0})
        assert page["historyGap"] and page["lostEvents"] > 0
        assert [event["event"] for event in page["events"]] == ["complete"]
        assert page["outputBytes"] == 200000 and not page["hasMore"]
    finally:
        clear_sessions(identifiers)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_shell_sessions_cancel_timeout_and_forget(websocket_control, protocol):
    identifiers = []
    try:
        started = native_call(protocol, "shellSessionStart", {"command": "echo $$; sleep 60", "root": False})
        identifier = started["streamId"]
        identifiers.append(identifier)
        native_call(protocol, "shellSessionForget", {"streamId": identifier}, error="shell_session_active")
        cursor = 0
        deadline = time.monotonic() + SESSION_WAIT_SECONDS
        while True:
            page = native_call(protocol, "shellSessionRead", {"streamId": identifier, "cursor": cursor, "waitMilliseconds": 1000})
            output = b"".join(base64.b64decode(event["outputBase64"]) for event in page["events"] if event["event"] == "output")
            cursor = page["nextCursor"]
            if output:
                process_id = int(output.strip())
                assert process_id > 1
                break
            assert time.monotonic() < deadline, page
        stopped = native_call(protocol, "shellSessionStop", {"streamId": identifier})
        assert stopped["cancellationRequested"]
        terminal, _ = drain_session(protocol, identifier, cursor)
        assert terminal["state"] == "cancelled"
        state = device_command("if [ -d /proc/" + str(process_id) + " ]; then echo alive; else echo gone; fi").strip()
        assert state == "gone"
        assert native_call(protocol, "shellSessionForget", {"streamId": identifier})["state"] == "forgotten"
        identifiers.remove(identifier)
        native_call(protocol, "shellSessionRead", {"streamId": identifier}, error="shell_session_not_found")
        timed = native_call(protocol, "shellSessionStart", {"command": "sleep 60", "root": False, "timeoutMilliseconds": 100})
        identifiers.append(timed["streamId"])
        assert drain_session(protocol, timed["streamId"])[0]["state"] == "timed_out"
    finally:
        clear_sessions(identifiers)


def test_shell_sessions_capacity_and_buffer_budget(websocket_control):
    identifiers = []
    try:
        for _ in range(4):
            started = native_call("http", "shellSessionStart", {"command": "sleep 60", "root": False})
            identifiers.append(started["streamId"])
        native_call("http", "shellSessionStart", {"command": "true", "root": False}, error="shell_stream_limit")
    finally:
        clear_sessions(identifiers)
    identifiers = []
    try:
        for _ in range(4):
            started = native_call("http", "shellSessionStart", {"command": "true", "root": False, "bufferBytes": 8388608})
            identifiers.append(started["streamId"])
            drain_session("http", identifiers[-1])
        native_call("http", "shellSessionStart", {"command": "true", "root": False}, error="shell_session_buffer_capacity")
    finally:
        clear_sessions(identifiers)
    identifiers = []
    try:
        for _ in range(16):
            started = native_call("http", "shellSessionStart", {"command": "true", "root": False})
            identifiers.append(started["streamId"])
            drain_session("http", identifiers[-1])
        native_call("http", "shellSessionStart", {"command": "true", "root": False}, error="shell_session_capacity")
    finally:
        clear_sessions(identifiers)


def test_shell_sessions_html_binding(websocket_control):
    from .test_entrypoint_parity import parity_html_configuration, wait_for_parity_response
    original = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
    javascript = """window.addEventListener('dikciz-ready', async () => {
      let stage = 'start';
      try {
        const started = await dikciz.command('shellSessionStart', {command:'printf from-html',root:false});
        let cursor = 0;
        const events = [];
        let terminal;
        stage = 'read';
        do {
          terminal = await dikciz.command('shellSessionRead', {streamId:started.streamId,cursor,waitMilliseconds:1000});
          events.push(...terminal.events);
          cursor = terminal.nextCursor;
        } while (terminal.active || terminal.hasMore);
        stage = 'forget';
        const forgotten = await dikciz.command('shellSessionForget', {streamId:started.streamId});
        stage = 'save';
        await selfWidget.patchState({parityResponse:{terminal,events,forgotten}});
      } catch (error) {
        await selfWidget.patchState({parityResponse:{failure:{stage,code:error.code || 'javascript_error',message:error.message}}});
      }
    });"""
    try:
        AUTOMATION.request(websocket_control, "configReplace", config=parity_html_configuration(original, javascript))
        result = wait_for_parity_response(websocket_control)
        assert "failure" not in result, result
        assert result["terminal"]["state"] == "exited" and result["terminal"]["exitCode"] == 0
        output = b"".join(base64.b64decode(event["outputBase64"]) for event in result["events"] if event["event"] == "output")
        assert output == b"from-html" and result["forgotten"]["state"] == "forgotten"
    finally:
        AUTOMATION.request(websocket_control, "configReplace", config=original)


def test_shell_sessions_lua_binding(websocket_control):
    original = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
    source = """function on_event(event)
      if event.type ~= 'fixture.sessions' then return end
      if event.payload.phase == 'start' then
        local result = context.requestJson({type='shellSessionStart',command='printf from-lua',root=false})
        return {actions={{type='patchState',values={started=result}}}}
      end
      local result = context.requestJson({type='shellSessionRead',streamId=event.payload.streamId})
      local forgotten = context.requestJson({type='shellSessionForget',streamId=event.payload.streamId})
      return {actions={{type='patchState',values={read=result,forgotten=forgotten}}}}
    end"""
    configured = automation_configuration(original, source, {}, ["manualTrigger"], ["patchState"],
        [{"event": "fixture.sessions", "minimumIntervalMilliseconds": 0}])
    identifier = None
    forgotten = False
    def script_state():
        document = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
        return next(script["state"] for script in document["scripts"] if script["id"] == AUTOMATION_LIVE_SCRIPT_ID)
    def emit(payload):
        AUTOMATION.request(websocket_control, "automationDispatch", sourceWidgetAddress="1H1V-welcome",
            actions=[{"type": "emitEvent", "event": "fixture.sessions", "payload": payload}])
    try:
        AUTOMATION.request(websocket_control, "configReplace", config=configured)
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        emit({"phase": "start"})
        deadline = time.monotonic() + SESSION_WAIT_SECONDS
        while True:
            state = script_state()
            if "started" in state:
                response = json.loads(state["started"])
                assert response["type"] == "result", response
                identifier = response["result"]["streamId"]
                break
            assert time.monotonic() < deadline, state
            time.sleep(.1)
        drain_session("http", identifier)
        emit({"phase": "inspect", "streamId": identifier})
        while True:
            state = script_state()
            if "forgotten" in state:
                response = json.loads(state["read"])
                released = json.loads(state["forgotten"])
                assert response["type"] == released["type"] == "result", state
                assert released["result"]["state"] == "forgotten"
                forgotten = True
                result = response["result"]
                output = b"".join(base64.b64decode(event["outputBase64"]) for event in result["events"] if event["event"] == "output")
                assert output == b"from-lua" and result["state"] == "exited" and result["exitCode"] == 0
                break
            assert time.monotonic() < deadline, state
            time.sleep(.1)
    finally:
        AUTOMATION.request(websocket_control, "configReplace", config=original)
        if identifier and not forgotten:
            clear_sessions([identifier])
