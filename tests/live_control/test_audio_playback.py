"""Exercise Android MediaPlayer sessions through the production command target."""

import base64
import io
import uuid
import wave

from . import *
from .test_native_data import native_call, PROTOCOLS

PLAYBACK_WAIT_SECONDS = 15
SAMPLE_RATE = 8000


def playback_state(protocol, identifier, expected):
    deadline = time.monotonic() + PLAYBACK_WAIT_SECONDS
    while True:
        result = native_call(protocol, "audioPlayback", {"action": "status", "sessionId": identifier})
        assert result["state"] != "failed", result
        if result["state"] in expected:
            return result
        assert time.monotonic() < deadline, result
        time.sleep(.1)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_native_audio_playback_lifecycle(websocket_control, protocol):
    restore_system_home()
    source = "/sdcard/Dikciz/playback-" + uuid.uuid4().hex + ".wav"
    stream = io.BytesIO()
    with wave.open(stream, "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(SAMPLE_RATE)
        output.writeframes(b"\x00\x00" * SAMPLE_RATE)
    native_call(protocol, "fileWrite", {"path": source, "dataBase64": base64.b64encode(stream.getvalue()).decode()})
    try:
        native_call(protocol, "audioPlayback", {"action": "stop"})
        result = native_call(protocol, "audioPlayback", {"action": "play", "path": source, "loop": True, "volumePercent": 0})
        identifier = result["sessionId"]
        active = playback_state(protocol, identifier, ("playing",))
        assert active["isPlaying"] and 900 <= active["durationMilliseconds"] <= 1100
        native_call(protocol, "audioPlayback", {"action": "play", "path": source}, error="hardware_busy")
        native_call(protocol, "audioPlayback", {"action": "pause", "sessionId": str(uuid.uuid4())}, error="audio_session_not_found")
        paused = native_call(protocol, "audioPlayback", {"action": "pause", "sessionId": identifier})
        assert paused["state"] == "paused" and not paused["isPlaying"]
        assert native_call(protocol, "audioPlayback", {"action": "volume", "sessionId": identifier, "volumePercent": 20})["volumePercent"] == 20
        native_call(protocol, "audioPlayback", {"action": "seek", "sessionId": identifier, "positionMilliseconds": 500})
        deadline = time.monotonic() + PLAYBACK_WAIT_SECONDS
        while True:
            seeked = native_call(protocol, "audioPlayback", {"action": "status", "sessionId": identifier})
            if not seeked["seeking"]:
                assert 400 <= seeked["positionMilliseconds"] <= 600
                break
            assert time.monotonic() < deadline, seeked
            time.sleep(.1)
        native_call(protocol, "audioPlayback", {"action": "seek", "sessionId": identifier, "positionMilliseconds": 2000}, error="validation_failed")
        assert native_call(protocol, "audioPlayback", {"action": "resume", "sessionId": identifier})["state"] == "playing"
        assert native_call(protocol, "audioPlayback", {"action": "stop", "sessionId": identifier})["state"] == "stopped"
        native_call(protocol, "audioPlayback", {"action": "resume", "sessionId": identifier}, error="audio_not_active")
        result = native_call(protocol, "audioPlayback", {"action": "play", "path": source})
        assert playback_state(protocol, result["sessionId"], ("completed",))["isPlaying"] is False
        result = native_call(protocol, "audioPlayback", {"action": "play", "path": source, "loop": True, "maximumDurationMilliseconds": 100})
        assert playback_state(protocol, result["sessionId"], ("expired",))["isPlaying"] is False
        native_call(protocol, "audioPlayback", {"action": "play", "path": source, "outputDeviceId": 2147483647}, error="audio_device_not_found")
        for arguments in (
            {"action": "arbitrary"}, {"action": "play", "path": "../file"},
            {"action": "play", "path": source, "volumePercent": 101},
            {"action": "play", "path": source, "loop": "true"},
            {"action": "play", "path": source, "maximumDurationMilliseconds": 0},
            {"action": "status", "path": source}, {"action": "pause"},
            {"action": "volume", "sessionId": identifier},
            {"action": "status", "sessionId": "../invalid"},
        ):
            native_call(protocol, "audioPlayback", arguments, error="validation_failed")
    finally:
        native_call("http", "audioPlayback", {"action": "stop"})
        native_call("http", "fileManage", {"action": "delete", "path": source})


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_native_audio_microphone_sessions(websocket_control, protocol):
    from .test_native_data import grant_permissions
    from .test_capability_expansion import wait_native_control
    grant_permissions(("android.permission.RECORD_AUDIO",))
    restore_system_home()
    wait_native_control(protocol)
    identifier = None
    saved = []
    try:
        started = native_call(protocol, "microphoneSession", {"action": "start", "maximumDurationMilliseconds": 10000})
        identifier = started["sessionId"]
        assert started["state"] == "recording" and not started["callAudio"]
        native_call(protocol, "microphoneSession", {"action": "start"}, error="hardware_busy")
        native_call(protocol, "microphoneRecord", {"durationMilliseconds": 1000}, error="hardware_busy")
        native_call(protocol, "speechRecognize", {"timeoutMilliseconds": 1}, error="hardware_busy")
        native_call(protocol, "microphoneSession", {"action": "stop", "sessionId": str(uuid.uuid4())}, error="microphone_session_not_found")
        time.sleep(1)
        stopped = native_call(protocol, "microphoneSession", {"action": "stop", "sessionId": identifier})
        assert stopped["state"] == "completed" and stopped["bytes"] > 0, stopped
        saved.append(stopped["path"])
        head = base64.b64decode(native_call(protocol, "fileRead", {"path": stopped["path"], "limit": 32})["dataBase64"])
        assert b"ftyp" in head and stopped["mimeType"] == "audio/mp4"
        assert native_call(protocol, "microphoneSession", {"action": "stop", "sessionId": identifier})["path"] == stopped["path"]
        directory = stopped["path"].rsplit("/", 1)[0]
        before = {item["name"] for item in native_call(protocol, "fileList", {"path": directory})["items"]}
        started = native_call(protocol, "microphoneSession", {"action": "start", "sampleRateHz": 16000})
        identifier = started["sessionId"]
        cancelled = native_call(protocol, "microphoneSession", {"action": "cancel", "sessionId": identifier})
        assert cancelled["state"] == "cancelled" and "path" not in cancelled and not cancelled.get("cleanupFailed")
        after = {item["name"] for item in native_call(protocol, "fileList", {"path": directory})["items"]}
        assert before == after
        started = native_call(protocol, "microphoneSession", {"action": "start", "maximumDurationMilliseconds": 1200})
        identifier = started["sessionId"]
        deadline = time.monotonic() + PLAYBACK_WAIT_SECONDS
        while True:
            completed = native_call(protocol, "microphoneSession", {"action": "status", "sessionId": identifier})
            if completed["state"] != "recording":
                assert completed["state"] == "completed" and completed["reason"] == "maximum_duration", completed
                assert completed["bytes"] > 0
                saved.append(completed["path"])
                break
            assert time.monotonic() < deadline, completed
            time.sleep(.1)
        native_call(protocol, "microphoneSession", {"action": "start", "inputDeviceId": 2147483647}, error="audio_device_not_found")
        for arguments in (
            {"action": "arbitrary"}, {"action": "start", "maximumDurationMilliseconds": 0},
            {"action": "start", "maximumBytes": 4095}, {"action": "start", "sampleRateHz": 12345},
            {"action": "start", "channels": 3}, {"action": "start", "bitRate": 7999},
            {"action": "stop"}, {"action": "status", "inputDeviceId": 0},
            {"action": "status", "sessionId": "../bad"},
        ):
            native_call(protocol, "microphoneSession", arguments, error="validation_failed")
    finally:
        if identifier:
            status = native_call("http", "microphoneSession", {"action": "status"})
            if status["state"] == "recording":
                native_call("http", "microphoneSession", {"action": "cancel", "sessionId": status["sessionId"]})
        for path in saved:
            native_call("http", "fileManage", {"action": "delete", "path": path})
