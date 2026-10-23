"""Recognition-provider support and model callbacks through shared transports."""

import uuid
from concurrent.futures import ThreadPoolExecutor

from . import *
from .test_native_data import native_call, PROTOCOLS


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_speech_models_provider_contract(websocket_control, installed_configurable_provider_fixture, protocol):
    original = device_command("settings get secure voice_recognition_service").strip()
    component = "eu.psyb0t.dikciz.fixture/.FixtureRecognitionService"
    device_command("settings put secure voice_recognition_service " + component)
    try:
        restore_system_home()
        arguments = {"mode": "system", "language": "en-US", "limit": 4}
        rows = []
        offset = 0
        lengths = []
        while True:
            page = native_call(protocol, "speechLanguages", {**arguments, "offset": offset})
            assert page["state"] == "observed" and page["mode"] == "system"
            lengths.append(len(page["items"]))
            rows.extend(page["items"])
            offset += len(page["items"])
            if not page["hasMore"]:
                assert offset == page["total"] == 11
                break
        assert lengths == [4, 4, 3]
        identities = [(row["availability"], row["language"]) for row in rows]
        assert identities == sorted(set(identities))
        assert {row["availability"] for row in rows} == {"installedOnDevice", "pendingOnDevice", "supportedOnDevice", "online"}
        for beyond in (offset, 1000000):
            assert native_call(protocol, "speechLanguages", {**arguments, "offset": beyond})["items"] == []
        native_call(protocol, "speechLanguages", {"mode": "system", "language": "zz"}, error="speech_support_error_12")
        native_call(protocol, "speechLanguages", {"mode": "system", "language": "fr", "timeoutMilliseconds": 100}, error="speech_support_timeout")
        available = native_call(protocol, "speechModelDownload", {"mode": "system", "language": "en"})
        assert available["state"] == "available" and available["progressPercent"] == 100 and available["outcomeKnown"]
        assert native_call(protocol, "speechModelStatus", {"sessionId": available["sessionId"]})["state"] == "available"
        native_call(protocol, "speechModelDownload", {"mode": "system", "language": "zz"}, error="speech_model_error_12")
        assert native_call(protocol, "speechModelStatus")["state"] == "failed"
        native_call(protocol, "speechModelDownload", {"mode": "system", "language": "fr", "timeoutMilliseconds": 100}, error="speech_model_timeout")
        unknown = native_call(protocol, "speechModelStatus")
        assert unknown["state"] == "unknown" and not unknown["outcomeKnown"]
        scheduled = native_call(protocol, "speechModelDownload", {"mode": "system", "language": "es"})
        assert scheduled["state"] == "scheduled" and scheduled["progressPercent"] is None
        with ThreadPoolExecutor(max_workers=1) as executor:
            running = executor.submit(native_call, protocol, "speechModelDownload", {"mode": "system", "language": "de", "timeoutMilliseconds": 10000})
            deadline = time.monotonic() + 5
            while True:
                progress = native_call("http", "speechModelStatus")
                if progress["state"] == "downloading":
                    assert progress["progressPercent"] == 15
                    break
                assert time.monotonic() < deadline, progress
                time.sleep(.1)
            native_call("http", "speechLanguages", {"mode": "system"}, error="hardware_busy")
            assert running.result(timeout=15)["state"] == "available"
        native_call(protocol, "speechModelStatus", {"sessionId": str(uuid.uuid4())}, error="speech_model_not_found")
        for command, fields in (
            ("speechLanguages", {"mode": "arbitrary"}),
            ("speechLanguages", {"language": "../bad"}),
            ("speechLanguages", {"limit": 0}),
            ("speechLanguages", {"offset": -1}),
            ("speechLanguages", {"timeoutMilliseconds": 0}),
            ("speechModelDownload", {}),
            ("speechModelDownload", {"language": ""}),
            ("speechModelStatus", {"sessionId": "../bad"}),
            ("speechModelStatus", {"unexpected": True}),
        ):
            native_call(protocol, command, fields, error="validation_failed")
    finally:
        if original and original != "null":
            device_command("settings put secure voice_recognition_service " + original)
        else:
            device_command("settings delete secure voice_recognition_service")
