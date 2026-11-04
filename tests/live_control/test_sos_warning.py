"""Owner-visible SOS cancellation before the shared executor submits SMS."""

import base64
import json
import time
import pytest
from . import *
from .test_native_data import grant_permissions, native_call, PROTOCOLS
from .test_operations_ux import tap


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_sos_warning_visible_countdown_cancel(websocket_control, protocol):
    grant_permissions(("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION",
                       "android.permission.POST_NOTIFICATIONS", "android.permission.SEND_SMS"))
    restore_system_home()
    device_command(f"appops set {DIKCIZ_DEBUG_PACKAGE} SYSTEM_ALERT_WINDOW allow")
    native_call("http", "sosControl", {"action": "cancel"})
    native_call(protocol, "sosPlanSave", {"contacts": [{"address": "5550101", "label": "Emulator fixture"}],
                                       "graceSeconds": 60, "maximumUpdates": 1})
    try:
        native_call(protocol, "sosControl", {"action": "trigger", "confirm": True})
        deadline = time.monotonic() + 8
        state = {}
        while time.monotonic() < deadline:
            state = native_call(protocol, "sosStatus")
            if state.get("warning", {}).get("screenVisible"):
                break
            time.sleep(.2)
        assert state.get("warning", {}).get("screenVisible"), state
        assert state["warning"]["presentation"] == "overlay"
        wait_for_snapshot_node(websocket_control, "sos:warning:cancel")
        first = state["warning"]["remainingSeconds"]
        time.sleep(2)
        second = native_call(protocol, "sosStatus")["warning"]
        assert 0 < second["remainingSeconds"] < first
        (ARTIFACT_DIRECTORY / ("sos-warning-" + protocol + ".json")).write_text(json.dumps(second, indent=2))
        assert second["soundRequested"] and second["vibrationRequested"]
        assert second["speechState"] in ("initializing", "queued", "speaking", "spoken", "unavailable")
        capture = native_call("http", "screenshot")
        (ARTIFACT_DIRECTORY / ("sos-warning-" + protocol + ".png")).write_bytes(base64.b64decode(capture["pngBase64"]))
        tap(websocket_control, "sos:warning:cancel")
        deadline = time.monotonic() + 8
        while time.monotonic() < deadline:
            state = native_call(protocol, "sosStatus")
            if state["run"]["state"] == "cancelled" and not state["warning"]["active"]:
                break
            time.sleep(.2)
        assert state["run"]["state"] == "cancelled", state
        assert state["run"]["updates"] == 0 and not state["run"]["receipts"]
        assert not state["warning"]["active"] and not state["warning"]["screenVisible"]
    finally:
        native_call("http", "sosControl", {"action": "cancel"})


def _warning_setup():
    grant_permissions(("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION",
                       "android.permission.POST_NOTIFICATIONS", "android.permission.SEND_SMS"))
    restore_system_home()
    native_call("http", "sosControl", {"action": "cancel"})


def test_sos_warning_followup_dialog_without_overlay(websocket_control):
    _warning_setup()
    device_command(f"appops set {DIKCIZ_DEBUG_PACKAGE} SYSTEM_ALERT_WINDOW ignore")
    native_call("http", "sosPlanSave", {"contacts": [{"address": "5550101"}], "graceSeconds": 60, "maximumUpdates": 1})
    try:
        native_call("http", "sosControl", {"action": "trigger", "confirm": True})
        wait_for_snapshot_node(websocket_control, "sos:warning:cancel")
        state = native_call("http", "sosStatus")
        assert state["warning"]["presentation"] == "dialog", state
        assert state["warning"]["screenVisible"]
        tap(websocket_control, "sos:warning:cancel")
        time.sleep(1)
        state = native_call("http", "sosStatus")
        assert state["run"]["state"] == "cancelled" and not state["run"]["receipts"]
        assert not state["warning"]["active"]
    finally:
        native_call("http", "sosControl", {"action": "cancel"})
        device_command(f"appops set {DIKCIZ_DEBUG_PACKAGE} SYSTEM_ALERT_WINDOW allow")


def test_sos_warning_followup_missed_checkin_over_another_app(websocket_control):
    _warning_setup()
    device_command(f"appops set {DIKCIZ_DEBUG_PACKAGE} SYSTEM_ALERT_WINDOW allow")
    native_call("http", "sosPlanSave", {"contacts": [{"address": "5550101"}], "checkInSeconds": 60,
                                       "graceSeconds": 30, "maximumUpdates": 1})
    try:
        native_call("http", "sosControl", {"action": "arm", "confirm": True})
        assert not native_call("http", "sosStatus")["warning"]["active"]
        device_command("am start -a android.settings.SETTINGS")
        deadline = time.monotonic() + 75
        state = {}
        while time.monotonic() < deadline:
            state = native_call("http", "sosStatus")
            if state["run"]["state"] == "warning" and state["warning"].get("screenVisible"):
                break
            time.sleep(1)
        assert state["warning"]["presentation"] == "overlay" and state["warning"]["screenVisible"], state
        device_command("settings put system font_scale 1.3")
        device_command("settings put system accelerometer_rotation 0")
        device_command("settings put system user_rotation 1")
        time.sleep(2)
        AUTOMATION.request(websocket_control, "scrollTo", semanticId="sos:warning:cancel")
        wait_for_snapshot_node(websocket_control, "sos:warning:cancel")
        capture = native_call("http", "screenshot")
        (ARTIFACT_DIRECTORY / "sos-warning-background.png").write_bytes(base64.b64decode(capture["pngBase64"]))
        native_call("mcp", "sosControl", {"action": "cancel"})
        time.sleep(1)
        state = native_call("http", "sosStatus")
        assert state["run"]["state"] == "cancelled" and not state["run"]["receipts"]
        assert not state["warning"]["active"]
    finally:
        native_call("http", "sosControl", {"action": "cancel"})
        device_command("settings put system font_scale 1.0")
        device_command("settings put system user_rotation 0")
        device_command("settings put system accelerometer_rotation 1")
        restore_system_home()


def test_sos_warning_followup_terminal_cleanup(websocket_control):
    _warning_setup()
    device_command(f"appops set {DIKCIZ_DEBUG_PACKAGE} SYSTEM_ALERT_WINDOW allow")
    native_call("http", "sosPlanSave", {"contacts": [{"address": "5550101"}], "graceSeconds": 5, "maximumUpdates": 1})
    try:
        native_call("http", "sosControl", {"action": "trigger", "confirm": True})
        wait_for_snapshot_node(websocket_control, "sos:warning:cancel")
        deadline = time.monotonic() + 20
        state = {}
        while time.monotonic() < deadline:
            state = native_call("http", "sosStatus")
            if state["run"]["state"] in ("completed", "needs_review") and not state["warning"]["active"]:
                break
            time.sleep(.3)
        assert state["run"]["updates"] == 1 and len(state["run"]["receipts"]) == 1, state
        assert not state["warning"]["active"] and state["warning"]["speechState"] == "idle", state
    finally:
        native_call("http", "sosControl", {"action": "cancel"})
