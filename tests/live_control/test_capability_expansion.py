"""Additional Android operations through each shared network entrypoint."""

import logging
import subprocess

from . import *
from .test_native_data import native_call, PROTOCOLS, grant_permissions


CONTROL_RECOVERY_SECONDS = 30
CONTROL_RECOVERY_INTERVAL_SECONDS = 0.25
LOGGER = logging.getLogger(__name__)


def wait_native_control(protocol):
    deadline = time.monotonic() + CONTROL_RECOVERY_SECONDS
    while True:
        try:
            return native_call(protocol, "speechRecognitionStatus")
        except (AssertionError, json.JSONDecodeError, subprocess.TimeoutExpired) as exception:
            if time.monotonic() >= deadline:
                raise AssertionError("Native control did not recover after permission restart") from exception
            LOGGER.warning("Native control is restarting", extra={"protocol": protocol, "error_class": type(exception).__name__})
            time.sleep(CONTROL_RECOVERY_INTERVAL_SECONDS)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_capability_expansion_discovery_and_validation(websocket_control, protocol):
    AUTOMATION.verify_control_status(websocket_control)
    status = native_call(protocol, "speechRecognitionStatus")
    assert isinstance(status["recognitionAvailable"], bool)
    assert isinstance(status["onDeviceAvailable"], bool)
    assert status["defaultMode"] == "onDevice" and not status["listening"]
    assert native_call(protocol, "speechRecognitionStop")["state"] == "idle"
    assert isinstance(native_call(protocol, "nfcStatus")["available"], bool)
    cameras = native_call(protocol, "cameraList")["items"]
    assert cameras
    details = native_call(protocol, "cameraCapabilities", {"cameraId": cameras[0]["cameraId"]})
    assert details["jpegSizes"] and all(s["width"] > 0 and s["height"] > 0 for s in details["jpegSizes"])
    assert isinstance(native_call(protocol, "torchControl", {"action":"status"})["cameraIds"], list)
    vibrator = native_call(protocol, "vibrationControl", {"action":"status"})
    assert isinstance(vibrator["available"], bool)
    if vibrator["available"]:
        assert native_call(protocol, "vibrationControl", {"action":"vibrate", "durationMilliseconds":1})["state"] == "submitted"
    assert native_call(protocol, "vibrationControl", {"action":"cancel"})["state"] == "cancelled"
    for command, arguments in [
        ("speechRecognize", {"mode":"arbitrary"}),
        ("speechRecognize", {"language":"../../bad"}),
        ("speechRecognize", {"timeoutMilliseconds":0}),
        ("speechRecognize", {"timeoutMilliseconds":120001}),
        ("speechRecognitionStatus", {"unexpected":True}),
        ("torchControl", {"action":"toggle"}),
        ("vibrationControl", {"action":"vibrate", "amplitude":0}),
        ("vibrationControl", {"action":"vibrate", "durationMilliseconds":10001}),
        ("systemSetting", {"action":"set", "setting":"brightness", "value":256}),
        ("systemSetting", {"action":"set", "setting":"rotation", "value":4}),
        ("systemSetting", {"action":"get", "setting":"brightness", "value":1}),
        ("systemSetting", {"action":"set", "setting":"arbitrary", "value":1}),
    ]:
        native_call(protocol, command, arguments, error="validation_failed")
    native_call(protocol, "cameraCapabilities", {"cameraId":"missing"}, error="native_record_not_found")
    for command in ("audioDevices", "usbDevices"):
        rows = []
        offset = 0
        while True:
            page = native_call(protocol, command, {"limit":1, "offset":offset})
            rows.extend(page["items"])
            offset += len(page["items"])
            if not page["hasMore"]:
                assert offset == page["total"]
                break
        assert native_call(protocol, command, {"offset":offset})["items"] == []
        assert native_call(protocol, command, {"offset":1000000})["items"] == []
    assert native_call(protocol, "systemSetting", {"action":"get", "setting":"brightness"})["value"] is not None


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_capability_expansion_permissions_and_unavailable(websocket_control, protocol):
    device_command("pm revoke " + DIKCIZ_DEBUG_PACKAGE + " android.permission.RECORD_AUDIO")
    restore_system_home()
    wait_native_control(protocol)
    native_call(protocol, "speechRecognize", error="native_permission_cameraAndMicrophone")
    grant_permissions(("android.permission.RECORD_AUDIO",))
    restore_system_home()
    status = wait_native_control(protocol)
    if not status["onDeviceAvailable"]:
        native_call(protocol, "speechRecognize", {"timeoutMilliseconds":2000}, error="speech_on_device_unavailable")
        assert not native_call(protocol, "speechRecognitionStatus")["listening"]
    device_command("appops set " + DIKCIZ_DEBUG_PACKAGE + " WRITE_SETTINGS deny")
    denied = native_call(protocol, "systemSetting", {"action":"set", "setting":"brightness", "value":120}, error="native_permission_systemSettings")
    assert denied["permissionRequest"]["requirementId"] == "systemSettings"
    device_command("appops set " + DIKCIZ_DEBUG_PACKAGE + " WRITE_SETTINGS allow")
    original = native_call(protocol, "systemSetting", {"action":"get", "setting":"brightness"})["value"]
    try:
        assert native_call(protocol, "systemSetting", {"action":"set", "setting":"brightness", "value":120})["value"] == 120
        assert native_call(protocol, "systemSetting", {"action":"get", "setting":"brightness"})["value"] == 120
        timeout = native_call(protocol, "systemSetting", {"action":"get", "setting":"screenTimeoutMilliseconds"})["value"]
        assert native_call(protocol, "systemSetting", {"action":"set", "setting":"screenTimeoutMilliseconds", "value":timeout})["value"] == timeout
    finally:
        native_call(protocol, "systemSetting", {"action":"set", "setting":"brightness", "value":original})


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_capability_expansion_speech_provider_lifecycle(websocket_control, installed_configurable_provider_fixture, protocol):
    from concurrent.futures import ThreadPoolExecutor
    grant_permissions(("android.permission.RECORD_AUDIO",))
    original = device_command("settings get secure voice_recognition_service").strip()
    device_command("pm grant eu.psyb0t.dikciz.fixture android.permission.RECORD_AUDIO")
    component = "eu.psyb0t.dikciz.fixture/.FixtureRecognitionService"
    device_command("settings put secure voice_recognition_service " + component)
    try:
        restore_system_home()
        assert native_call(protocol, "speechRecognitionStatus")["recognitionAvailable"]
        def recognize(language, timeout=5000):
            return native_call(protocol,"speechRecognize",{"mode":"system","language":language,"timeoutMilliseconds":timeout})
        result = recognize("en-US")
        assert result["state"] == "recognized" and result["text"] == "Fixture recognized speech"
        assert result["alternatives"] == ["Fixture recognized speech", "Fixture alternative"]
        assert abs(result["confidence"][0] - .9) < .001 and result["confidence"][1] is None
        native_call(protocol,"speechRecognize",{"mode":"system","language":"zz"},error="speech_language_unavailable")
        native_call(protocol,"speechRecognize",{"mode":"system","language":"fr","timeoutMilliseconds":100},error="speech_timeout")
        assert recognize("en-US")["state"] == "recognized"
        with ThreadPoolExecutor(max_workers=1) as executor:
            def pending():
                return native_call(protocol,"speechRecognize",{"mode":"system","language":"fr"},error="speech_cancelled")
            running = executor.submit(pending)
            deadline = time.monotonic() + 5
            while not native_call("http","speechRecognitionStatus")["listening"]:
                assert time.monotonic() < deadline
                time.sleep(.1)
            native_call("http","speechRecognize",{"mode":"system"},error="hardware_busy")
            assert native_call("http","speechRecognitionStop")["state"] == "cancelled"
            running.result(timeout=10)
        assert not native_call(protocol,"speechRecognitionStatus")["listening"]
        assert recognize("en-US")["state"] == "recognized"
    finally:
        if original and original != "null":
            device_command("settings put secure voice_recognition_service " + original)
        else:
            device_command("settings delete secure voice_recognition_service")


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_capability_expansion_speech_output_controls(websocket_control, protocol):
    from concurrent.futures import ThreadPoolExecutor
    assert native_call(protocol,"speechStop")["state"] == "idle"
    inventory = native_call(protocol,"speechVoices",{"limit":1})
    page_size = min(200, max(1, (inventory["total"] + 2) // 3))
    voices = []
    offset = 0
    while True:
        page = native_call(protocol,"speechVoices",{"limit":page_size,"offset":offset})
        voices.extend(page["items"])
        offset += len(page["items"])
        if not page["hasMore"]:
            assert offset == page["total"]
            break
    assert voices and len({v["name"] for v in voices}) == len(voices)
    assert all(isinstance(v["networkRequired"],bool) and v["language"] for v in voices)
    assert native_call(protocol,"speechVoices",{"offset":offset})["items"] == []
    voice = next(v for v in voices if v["name"] == inventory["defaultVoice"])
    result = native_call(protocol,"speechSpeak",{"text":"Test","voiceName":voice["name"],"ratePercent":150,"pitchPercent":100})
    assert result["state"] == "spoken"
    native_call(protocol,"speechSpeak",{"text":"Test","voiceName":"missing voice"},error="native_record_not_found")
    for fields in ({"ratePercent":24},{"pitchPercent":401},{"language":"../bad"},{"voiceName":""}):
        native_call(protocol,"speechSpeak",{"text":"Test",**fields},error="validation_failed")
    with ThreadPoolExecutor(max_workers=1) as executor:
        pending=executor.submit(native_call,protocol,"speechSpeak",{"text":"Test "*300,"ratePercent":25},error="speech_cancelled")
        deadline=time.monotonic()+5
        while native_call("http","speechStop")["state"] == "idle":
            assert time.monotonic()<deadline
            time.sleep(.1)
        pending.result(timeout=15)
    assert native_call(protocol,"speechSpeak",{"text":"Test"})["state"] == "spoken"
