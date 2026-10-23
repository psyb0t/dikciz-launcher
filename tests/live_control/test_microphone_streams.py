"""Actual microphone PCM, replay, ownership and foreground service behavior."""

import base64
import uuid

from . import *
from .test_native_data import native_call, PROTOCOLS, grant_permissions
from .test_capability_expansion import wait_native_control


WAIT_SECONDS = 15
READ_WAIT = 300
MICROPHONE_PERMISSION = "android.permission.RECORD_AUDIO"
NOTIFICATION_PERMISSION = "android.permission.POST_NOTIFICATIONS"
TERMINAL = ("stopped", "expired", "failed")


def prepare_microphone(protocol):
    grant_permissions((MICROPHONE_PERMISSION, NOTIFICATION_PERMISSION))
    restore_system_home()
    wait_native_control(protocol)


def stream_read(protocol, identifier, cursor=0, wait=0, limit=200):
    return native_call(protocol, "microphoneStreamRead", {
        "streamId": identifier, "cursor": cursor, "waitMilliseconds": wait, "limit": limit,
    })


def finish_stream(protocol, identifier, stop=True):
    if stop:
        native_call(protocol, "microphoneStreamStop", {"streamId": identifier})
    deadline = time.monotonic() + WAIT_SECONDS
    while True:
        result = stream_read(protocol, identifier)
        if not result["active"] and not result["foregroundService"]:
            assert result["hardwareReleaseConfirmed"] and not result["cleanupFailed"], result
            assert any(event["event"] == "exit" for event in result["events"]), result
            return result
        assert time.monotonic() < deadline, result
        time.sleep(.1)


def cleanup_streams(protocol="http"):
    for row in native_call(protocol, "microphoneStreamsList", {"limit": 200})["items"]:
        finish_stream(protocol, row["streamId"])
        native_call(protocol, "microphoneStreamForget", {"streamId": row["streamId"]})


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_microphone_stream_pcm_replay_and_shared_ownership(websocket_control, protocol):
    prepare_microphone(protocol)
    cleanup_streams()
    started = native_call(protocol, "microphoneStreamStart", {"maximumDurationMilliseconds": 15000})
    identifier = started["streamId"]
    try:
        assert started["state"] == "recording" and started["foregroundService"], started
        assert started["encoding"] == "pcm_s16le" and not started["callAudio"]
        deadline = time.monotonic() + WAIT_SECONDS
        cursor, audio, seen = 0, [], []
        while len(audio) < 2:
            result = stream_read(protocol, identifier, cursor, READ_WAIT)
            assert not result["historyGap"], result
            seen.extend(result["events"])
            audio.extend(event for event in result["events"] if event["event"] == "audio")
            cursor = result["nextCursor"]
            assert time.monotonic() < deadline, result
        for event in audio:
            raw = base64.b64decode(event["audioBase64"], validate=True)
            assert len(raw) == event["frames"] * result["channels"] * 2
            assert event["observedAtElapsedNanoseconds"] > 0
        assert [row["sequence"] for row in seen] == sorted({row["sequence"] for row in seen})
        replay = stream_read("http", identifier)
        assert replay["events"][:len(seen)] == seen
        for command, fields in (
            ("microphoneStreamStart", {}),
            ("microphoneSession", {"action": "start"}),
            ("microphoneRecord", {"durationMilliseconds": 1000}),
            ("speechRecognize", {"timeoutMilliseconds": 100}),
        ):
            native_call(protocol, command, fields, error="hardware_busy")
        native_call(protocol, "microphoneStreamForget", {"streamId": identifier}, error="microphone_stream_active")
        stopped = finish_stream(protocol, identifier)
        assert stopped["state"] == "stopped" and stopped["totalFrames"] > 0
        assert native_call(protocol, "microphoneStreamStop", {"streamId": identifier})["cancellationRequested"] is False
        native_call(protocol, "microphoneStreamForget", {"streamId": identifier})
        native_call(protocol, "microphoneStreamRead", {"streamId": identifier}, error="microphone_stream_not_found")
    finally:
        cleanup_streams()


def test_microphone_stream_gap_validation_and_retained_pages(websocket_control):
    protocol = "http"
    prepare_microphone(protocol)
    cleanup_streams()
    for fields in (
        {"channels": True}, {"channels": 0}, {"sampleRateHz": 3999},
        {"sampleRateHz": 192001}, {"chunkMilliseconds": 19}, {"chunkMilliseconds": 1001},
        {"maximumDurationMilliseconds": 99}, {"startupTimeoutMilliseconds": 0},
        {"bufferBytes": 65535}, {"bufferBytes": 8388609},
        {"maximumEvents": 0}, {"maximumEvents": 4097}, {"retentionMinutes": 0},
        {"retentionMinutes": 1441}, {"inputDeviceId": -1},
        {"sampleRateHz": 192000, "channels": 2, "chunkMilliseconds": 1000},
    ):
        native_call(protocol, "microphoneStreamStart", fields, error="validation_failed")
    native_call(protocol, "microphoneStreamRead", {"streamId": "../invalid"}, error="validation_failed")
    native_call(protocol, "microphoneStreamRead", {"streamId": str(uuid.uuid4())}, error="microphone_stream_not_found")
    try:
        started = native_call(protocol, "microphoneStreamStart", {
            "maximumDurationMilliseconds": 800, "maximumEvents": 2, "bufferBytes": 65536,
        })
        identifier = started["streamId"]
        native_call(protocol, "microphoneStreamRead", {"streamId": identifier, "cursor": 9007199254740991}, error="validation_failed")
        ended = finish_stream(protocol, identifier, stop=False)
        assert ended["state"] == "expired" and ended["reason"] == "duration_limit"
        assert ended["historyGap"] and ended["lostEvents"] > 0
        assert ended["retainedEvents"] <= 2 and ended["retainedBytes"] <= 65536
        native_call(protocol, "microphoneStreamForget", {"streamId": identifier})
        identifiers = []
        for _ in range(7):
            row = native_call(protocol, "microphoneStreamStart", {"maximumDurationMilliseconds": 100})
            identifiers.append(row["streamId"])
            finish_stream(protocol, row["streamId"], stop=False)
        found = []
        for offset, count, more in ((0, 3, True), (3, 3, True), (6, 1, False), (7, 0, False), (1000, 0, False)):
            page = native_call(protocol, "microphoneStreamsList", {"offset": offset, "limit": 3})
            assert page["total"] == 7 and len(page["items"]) == count and page["hasMore"] is more
            found.extend(row["streamId"] for row in page["items"])
        assert found == list(reversed(identifiers))
        last = native_call(protocol, "microphoneStreamStart", {"maximumDurationMilliseconds": 100})
        finish_stream(protocol, last["streamId"], stop=False)
        native_call(protocol, "microphoneStreamStart", {}, error="microphone_stream_capacity")
    finally:
        cleanup_streams()


def test_microphone_stream_background_and_owner_stop(websocket_control):
    protocol = "http"
    prepare_microphone(protocol)
    cleanup_streams()
    started = native_call(protocol, "microphoneStreamStart", {"maximumDurationMilliseconds": 30000})
    identifier = started["streamId"]
    try:
        baseline = stream_read(protocol, identifier)["totalFrames"]
        device_command("am start -a android.settings.SETTINGS")
        wait_for_foreground_application(ANDROID_SETTINGS_PACKAGE)
        deadline = time.monotonic() + WAIT_SECONDS
        while True:
            observed = stream_read(protocol, identifier, wait=READ_WAIT)
            assert observed["state"] == "recording" and observed["foregroundService"], observed
            if observed["totalFrames"] > baseline:
                break
            assert time.monotonic() < deadline, observed
        service = device_command("dumpsys activity services " + DIKCIZ_DEBUG_PACKAGE)
        assert "DikcizMicrophoneCaptureService" in service and "isForeground=true" in service
        device_command("cmd statusbar expand-notifications")
        direct_tap(wait_for_physical_text_bounds("Stop recording"))
        finished = finish_stream(protocol, identifier, stop=False)
        assert finished["state"] == "stopped"
        run_device_operation("screenshot")
    finally:
        device_command("cmd statusbar collapse")
        restore_system_home()
        cleanup_streams()


def test_microphone_stream_permission_and_startup_failure_cleanup(websocket_control):
    protocol = "http"
    device_command("pm revoke " + DIKCIZ_DEBUG_PACKAGE + " " + MICROPHONE_PERMISSION)
    restore_system_home()
    wait_native_control(protocol)
    denied = native_call(protocol, "microphoneStreamStart", {}, error="native_permission_cameraAndMicrophone")
    assert denied["permissionRequest"]["requirementId"] == "cameraAndMicrophone"
    prepare_microphone(protocol)
    try:
        native_call(protocol, "microphoneStreamStart", {"inputDeviceId": 2147483647}, error="audio_device_not_found")
        cleanup_streams()
        try:
            result = native_call(protocol, "microphoneStreamStart", {
                "startupTimeoutMilliseconds": 1, "maximumDurationMilliseconds": 100,
            }, allowed_errors=("microphone_stream_start_timeout", "microphone_stream_start_cancelled"))
            if "streamId" in result:
                finish_stream(protocol, result["streamId"], stop=False)
        finally:
            cleanup_streams()
        good = native_call(protocol, "microphoneStreamStart", {"maximumDurationMilliseconds": 100})
        assert finish_stream(protocol, good["streamId"], stop=False)["state"] == "expired"
    finally:
        cleanup_streams()


def test_microphone_stream_local_bindings_and_release_discovery(websocket_control):
    from .test_entrypoint_parity import parity_html_configuration, wait_for_parity_response
    from .test_mcp_resources import CLIENT, client_arguments
    prepare_microphone("http")
    cleanup_streams()
    AUTOMATION.verify_control_status(websocket_control)
    version = (LAB_ROOT / "VERSION").read_text().strip()
    assert native_call("http", "deviceInfo")["application"]["versionName"] == version
    params, uris, counts = {}, [], []
    while True:
        page = CLIENT.call_mcp(client_arguments(), "resources/list", params)
        counts.append(len(page["resources"]))
        uris.extend(item["uri"] for item in page["resources"])
        if "nextCursor" not in page:
            break
        params = {"cursor": page["nextCursor"]}
    assert counts == [8, 8, 8, 8, 8, 4] and len(set(uris)) == 44
    assert "dikciz://docs/guides/microphone-streams" in uris
    for uri in uris:
        resource = CLIENT.call_mcp(client_arguments(), "resources/read", {"uri": uri})
        assert 0 < len(resource["contents"][0]["text"].encode()) <= 4096
    original = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
    javascript = """window.addEventListener('dikciz-ready', async () => {
      try {
        const started = await dikciz.command('microphoneStreamStart', {maximumDurationMilliseconds:800});
        let cursor = 0, frames = 0, exited = false, terminal;
        do {
          terminal = await dikciz.command('microphoneStreamRead', {streamId:started.streamId,cursor});
          for (const event of terminal.events) {
            if (event.event === 'audio') {
              if (atob(event.audioBase64).length !== event.frames * terminal.channels * 2) throw new Error('Invalid PCM frame');
              frames += event.frames;
            }
            if (event.event === 'exit') exited = true;
          }
          cursor = terminal.nextCursor;
          if (terminal.active || terminal.foregroundService) await new Promise(resolve => setTimeout(resolve,100));
        } while (terminal.active || terminal.hasMore || terminal.foregroundService);
        const forgotten = await dikciz.command('microphoneStreamForget', {streamId:started.streamId});
        await selfWidget.patchState({parityResponse:{frames,exited,state:terminal.state,forgotten:forgotten.state}});
      } catch (error) {
        await selfWidget.patchState({parityResponse:{failure:{code:error.code || 'javascript_error',message:error.message}}});
      }
    });"""
    try:
        AUTOMATION.request(websocket_control, "configReplace", config=parity_html_configuration(original, javascript))
        result = wait_for_parity_response(websocket_control)
        assert "failure" not in result, result
        assert result["frames"] > 0 and result["exited"] and result["state"] == "expired"
        assert result["forgotten"] == "forgotten"
        AUTOMATION.request(websocket_control, "configReplace", config=original)
        source = """function on_event(event)
          if event.type ~= 'fixture.microphone' then return end
          if event.payload.phase == 'start' then
            local result = context.requestJson({type='microphoneStreamStart',maximumDurationMilliseconds=800})
            return {actions={{type='patchState',values={started=result}}}}
          end
          local result = context.requestJson({type='microphoneStreamRead',streamId=event.payload.streamId,cursor=1,limit=1})
          local forgotten = context.requestJson({type='microphoneStreamForget',streamId=event.payload.streamId})
          return {actions={{type='patchState',values={read=result,forgotten=forgotten}}}}
        end"""
        configured = automation_configuration(original, source, {}, ["manualTrigger"], ["patchState"],
            [{"event": "fixture.microphone", "minimumIntervalMilliseconds": 0}])
        AUTOMATION.request(websocket_control, "configReplace", config=configured)
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        def emit(payload):
            AUTOMATION.request(websocket_control, "automationDispatch", sourceWidgetAddress="1H1V-welcome",
                actions=[{"type": "emitEvent", "event": "fixture.microphone", "payload": payload}])
        def await_state(key):
            deadline = time.monotonic() + WAIT_SECONDS
            while True:
                document = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
                state = next(script["state"] for script in document["scripts"] if script["id"] == AUTOMATION_LIVE_SCRIPT_ID)
                if key in state:
                    return state
                assert time.monotonic() < deadline, state
                time.sleep(.1)
        emit({"phase": "start"})
        started = json.loads(await_state("started")["started"])
        assert started["type"] == "result", started
        identifier = started["result"]["streamId"]
        finish_stream("http", identifier, stop=False)
        emit({"phase": "inspect", "streamId": identifier})
        state = await_state("forgotten")
        read, forgotten = json.loads(state["read"]), json.loads(state["forgotten"])
        assert read["type"] == forgotten["type"] == "result", state
        event = read["result"]["events"][0]
        assert event["event"] == "audio" and event["frames"] > 0
        assert len(base64.b64decode(event["audioBase64"], validate=True)) == event["frames"] * read["result"]["channels"] * 2
        assert forgotten["result"]["state"] == "forgotten"
    finally:
        AUTOMATION.request(websocket_control, "configReplace", config=original)
        cleanup_streams()
