"""Real Android torch callbacks and bounded haptic submissions."""

from . import *
from .test_native_data import PROTOCOLS, native_call, grant_permissions
from .test_capability_expansion import wait_native_control


def wait_vibration(active):
    deadline = time.monotonic() + 5
    while True:
        dump = device_command("dumpsys vibrator_manager")
        current = dump.split("CurrentVibration:", 1)[1].split("NextVibration:", 1)[0]
        observed = DIKCIZ_DEBUG_PACKAGE in current
        if observed == active:
            return
        assert time.monotonic() < deadline, current
        time.sleep(.05)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_feedback_controls_haptics(websocket_control, protocol):
    status = native_call(protocol, "vibrationControl", {"action": "status"})
    assert status["available"]
    assert {item["effect"] for item in status["effects"]} == {"click", "doubleClick", "heavyClick", "tick"}
    assert all(item["nativeSupport"] in ("supported", "unsupported", "unknown") for item in status["effects"])
    try:
        result = native_call(protocol, "vibrationControl", {"action": "waveform", "timingsMilliseconds": [0, 10000]})
        assert result["state"] == "submitted" and result["durationMilliseconds"] == 10000 and not result["repeating"]
        wait_vibration(True)
    finally:
        native_call(protocol, "vibrationControl", {"action": "cancel"})
    wait_vibration(False)
    result = native_call(protocol, "vibrationControl", {"action": "waveform", "timingsMilliseconds": [0] + [1] * 63,
                                                      "amplitudes": [0] + [-1, 1, 255] * 21})
    assert result["state"] == "submitted" and result["durationMilliseconds"] == 63
    for effect in ("click", "doubleClick", "heavyClick", "tick"):
        result = native_call(protocol, "vibrationControl", {"action": "effect", "effect": effect})
        assert result["state"] == "submitted" and result["effect"] == effect and result["fallbackPossible"]
    native_call(protocol, "vibrationControl", {"action": "cancel"})
    for fields in (
        {"action": "waveform"}, {"action": "waveform", "timingsMilliseconds": []},
        {"action": "waveform", "timingsMilliseconds": [0, 0]},
        {"action": "waveform", "timingsMilliseconds": [1] * 65},
        {"action": "waveform", "timingsMilliseconds": [1, 10000]},
        {"action": "waveform", "timingsMilliseconds": [True]},
        {"action": "waveform", "timingsMilliseconds": [1.5]},
        {"action": "waveform", "timingsMilliseconds": [-1]},
        {"action": "waveform", "timingsMilliseconds": ["1"]},
        {"action": "waveform", "timingsMilliseconds": [None]},
        {"action": "waveform", "timingsMilliseconds": [1], "amplitudes": []},
        {"action": "waveform", "timingsMilliseconds": [1], "amplitudes": [256]},
        {"action": "waveform", "timingsMilliseconds": [1], "amplitudes": [-2]},
        {"action": "waveform", "timingsMilliseconds": [1], "amplitudes": [True]},
        {"action": "waveform", "timingsMilliseconds": [1], "amplitudes": [1.5]},
        {"action": "effect"}, {"action": "effect", "effect": "unknown"},
        {"action": "status", "durationMilliseconds": 1},
        {"action": "cancel", "amplitude": 1},
        {"action": "vibrate", "timingsMilliseconds": [1]},
        {"action": "effect", "effect": "click", "amplitude": 1},
    ):
        native_call(protocol, "vibrationControl", fields, error="validation_failed")


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_feedback_controls_torch(websocket_control, protocol):
    grant_permissions(("android.permission.CAMERA",))
    restore_system_home()
    wait_native_control(protocol)
    status = native_call(protocol, "torchControl", {"action": "status"})
    assert len(status["items"]) == len(status["cameraIds"])
    for item in status["items"]:
        assert item["state"] in ("enabled", "disabled", "unavailable", "unknown")
        assert item["observed"] == (item["state"] != "unknown")
        if item["observed"]:
            assert item["observedAtElapsedMilliseconds"] > 0
    for fields in ({"action": "status", "timeoutMilliseconds": 0},
                   {"action": "status", "timeoutMilliseconds": 5001},
                   {"action": "on", "timeoutMilliseconds": 1},
                   {"action": "status", "cameraId": " "}):
        native_call(protocol, "torchControl", fields, error="validation_failed")
    native_call(protocol, "torchControl", {"action": "status", "cameraId": "missing"},
                error="native_record_not_found")
    if not status["cameraIds"]:
        assert status["complete"] and status["items"] == []
        return
    camera_id = status["cameraIds"][0]
    try:
        assert native_call(protocol, "torchControl", {"action": "on", "cameraId": camera_id})["state"] == "submitted"
        current = native_call(protocol, "torchControl", {"action": "status", "cameraId": camera_id})
        assert current["complete"] and current["items"][0]["state"] == "enabled", current
    finally:
        native_call(protocol, "torchControl", {"action": "off", "cameraId": camera_id})
    current = native_call(protocol, "torchControl", {"action": "status", "cameraId": camera_id})
    assert current["complete"] and current["items"][0]["state"] == "disabled", current
