"""Real visual capture and external speech/provider boundary tests."""

import base64
import io
import time
import uuid
import wave

import pytest

from . import *
from .test_native_data import native_call, PROTOCOLS, grant_permissions
from .test_chat_agent import provider_endpoint, conversation


def await_stream(protocol, kind, identifier, terminal=False):
    deadline = time.monotonic() + 20
    while True:
        page = native_call(protocol, kind + "StreamRead", {"streamId": identifier, "waitMilliseconds": 1000, "limit": 20})
        if (terminal and not page["active"]) or (not terminal and page["events"]):
            return page
        assert time.monotonic() < deadline, page


@pytest.mark.parametrize("protocol", PROTOCOLS)
@pytest.mark.parametrize("source", ("camera", "display"))
def test_operations_media_visual_stream(websocket_control, protocol, source):
    grant_permissions(("android.permission.CAMERA", "android.permission.POST_NOTIFICATIONS"))
    if source == "display":
        emulator_dikciz_accessibility_set(True)
    restore_system_home()
    stream = native_call(protocol, "imageStreamStart", {"source": source, "maxDimension": 640,
                         "frameIntervalMilliseconds": 500, "maximumDurationMilliseconds": 15000})
    identifier = stream["streamId"]
    try:
        first = await_stream(protocol, "image", identifier)
        frames = [row for row in first["events"] if row["event"] == "frame"]
        assert frames and first["active"], first
        image = frames[0]
        content = base64.b64decode(image["dataBase64"], validate=True)
        assert content.startswith(b"\xff\xd8") and content.endswith(b"\xff\xd9") and len(content) > 500
        assert max(image["width"], image["height"]) <= 640 and image["mimeType"] == "image/jpeg"
        duplicate = native_call(protocol, "imageStreamRead", {"streamId": identifier})
        assert duplicate["events"][0]["sequence"] == first["events"][0]["sequence"]
        native_call(protocol, "imageStreamForget", {"streamId": identifier}, error="image_stream_active")
        if source == "camera":
            native_call("http", "cameraCapture", {"maxDimension": 640}, error="hardware_busy")
        else:
            device_command("am start -a android.settings.SETTINGS")
            page = native_call(protocol, "imageStreamRead", {"streamId": identifier,
                               "cursor": first["nextCursor"], "waitMilliseconds": 3000})
            assert page["events"] and page["active"]
    finally:
        stopped = native_call("http", "imageStreamStop", {"streamId": identifier})
        assert not stopped["active"] and stopped["hardwareReleaseConfirmed"], stopped
        restore_system_home()
        if source == "display":
            emulator_dikciz_accessibility_set(False)
    assert native_call(protocol, "imageStreamRead", {"streamId": identifier})["active"] is False
    native_call(protocol, "imageStreamForget", {"streamId": identifier})
    native_call(protocol, "imageStreamRead", {"streamId": identifier}, error="image_stream_not_found")


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_operations_media_partial_speech_and_stop(websocket_control, installed_configurable_provider_fixture, protocol):
    grant_permissions(("android.permission.RECORD_AUDIO",))
    original = device_command("settings get secure voice_recognition_service").strip()
    device_command("pm grant eu.psyb0t.dikciz.fixture android.permission.RECORD_AUDIO")
    device_command("settings put secure voice_recognition_service eu.psyb0t.dikciz.fixture/.FixtureRecognitionService")
    try:
        restore_system_home()
        stream = native_call(protocol, "speechStreamStart", {"mode": "system", "language": "en-US", "timeoutMilliseconds": 5000})
        page = await_stream(protocol, "speech", stream["streamId"], terminal=True)
        assert [row["event"] for row in page["events"]] == ["partial", "final"]
        assert page["events"][0]["text"] == "Fixture partial speech"
        assert page["events"][1]["result"]["text"] == "Fixture recognized speech"
        assert page["hardwareReleaseConfirmed"]
        native_call(protocol, "speechStreamForget", {"streamId": stream["streamId"]})
        slow = native_call(protocol, "speechStreamStart", {"mode": "system", "language": "fr", "timeoutMilliseconds": 30000})
        deadline = time.monotonic() + 5
        while not native_call("http", "speechRecognitionStatus")["listening"]:
            assert time.monotonic() < deadline
        stopped = native_call(protocol, "speechStreamStop", {"streamId": slow["streamId"]})
        assert not stopped["active"] and stopped["hardwareReleaseConfirmed"]
        assert not native_call("http", "speechRecognitionStatus")["listening"]
        native_call(protocol, "speechStreamForget", {"streamId": slow["streamId"]})
    finally:
        if original and original != "null":
            device_command("settings put secure voice_recognition_service " + original)
        else:
            device_command("settings delete secure voice_recognition_service")


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_operations_media_file_transcription(websocket_control, provider_endpoint, protocol):
    endpoint, script, records = provider_endpoint
    profile, _ = conversation(endpoint)
    audio = io.BytesIO()
    with wave.open(audio, "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(16000)
        output.writeframes(b"\0\0" * 1600)
    content = audio.getvalue()
    path = "/sdcard/Android/data/" + DIKCIZ_DEBUG_PACKAGE + "/files/private-name-" + uuid.uuid4().hex + ".wav"
    native_call("http", "fileWrite", {"path": path, "dataBase64": base64.b64encode(content).decode()})
    arguments = {"path": path, "profileId": profile["id"], "model": "fixture-audio", "language": "en"}
    result = native_call(protocol, "speechTranscribeFile", arguments)
    assert result["text"] == "Fixture audio transcript" and not result["microphoneUsed"]
    assert result["uploadedBytes"] == len(content)
    assert records[-1]["usedFixtureKey"] and records[-1]["audioFields"]["file"] == content
    assert records[-1]["audioFields"]["model"] == b"fixture-audio"
    assert records[-1]["audioFields"]["language"] == b"en"
    script["mode"] = "transcription_redirect"
    native_call(protocol, "speechTranscribeFile", arguments, error="transcription_provider_rejected")
    assert not any(row["path"] == "/unexpected-redirect" for row in records)
    script["mode"] = "transcription_large"
    native_call(protocol, "speechTranscribeFile", arguments, error="transcription_response_too_large")
    script["mode"] = "transcription_delay"
    native_call(protocol, "speechTranscribeFile", {**arguments, "timeoutMilliseconds": 1000}, error="transcription_transport_failed")


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_operations_media_validation(websocket_control, protocol):
    for command, arguments in [
        ("imageStreamStart", {"source": "arbitrary"}),
        ("imageStreamStart", {"source": "camera", "frameIntervalMilliseconds": 499}),
        ("imageStreamRead", {"streamId": "../bad"}),
        ("speechStreamStart", {"mode": "arbitrary"}),
        ("speechStreamStart", {"timeoutMilliseconds": 120001}),
        ("speechStreamRead", {"streamId": "../bad"}),
        ("speechTranscribeFile", {}),
        ("speechTranscribeFile", {"profileId": "missing", "path": "/missing.wav", "model": "fixture", "timeoutMilliseconds": 0}),
    ]:
        native_call(protocol, command, arguments, error="validation_failed")
