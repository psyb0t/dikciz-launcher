"""Requested scans distinguish Android completion, cache freshness and policy failures."""

from . import *
from .test_native_data import native_call, PROTOCOLS, grant_permissions
from .test_capability_expansion import wait_native_control

SCAN_PLATFORM_ERRORS = {
    "wifi_scan_rejected", "wifi_scan_failed", "wifi_scan_timeout",
}


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_wifi_control_requested_scan_and_recovery(websocket_control, protocol):
    grant_permissions(("android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"))
    restore_system_home()
    wait_native_control(protocol)
    originally_enabled = native_call(protocol, "wifiScanResults")["enabled"]
    location_setting = device_command("cmd location is-location-enabled").strip()
    assert location_setting in ("true", "false"), location_setting
    try:
        if not originally_enabled:
            emulator_wifi_set(AUTOMATION_SYSTEM_WIFI_CONNECTED)
        device_command("cmd location set-location-enabled false")
        native_call(protocol, "wifiScan", {"timeoutMilliseconds": 1}, error="wifi_location_disabled")
        device_command("cmd location set-location-enabled true")
        result = native_call(protocol, "wifiScan", {"timeoutMilliseconds": 30000, "limit": 1}, allowed_errors=SCAN_PLATFORM_ERRORS)
        if "code" not in result:
            assert result["state"] == "observed" and result["source"] == "scan_completion_cache"
            assert result["completedAtElapsedMicroseconds"] >= result["requestedAtElapsedMicroseconds"]
            assert len(result["items"]) <= 1 and result["total"] >= len(result["items"])
            for item in result["items"]:
                assert item["observedAfterRequest"] == (item["timestampMicroseconds"] >= result["requestedAtElapsedMicroseconds"])
        for _ in range(2):
            repeated = native_call(protocol, "wifiScan", {"timeoutMilliseconds": 1}, allowed_errors=SCAN_PLATFORM_ERRORS)
            assert repeated.get("code") in SCAN_PLATFORM_ERRORS or repeated["state"] == "observed"
        for arguments in (
            {"timeoutMilliseconds": 0}, {"timeoutMilliseconds": 120001},
            {"limit": 0}, {"limit": 201}, {"offset": -1}, {"offset": 1000001},
            {"unexpected": True},
        ):
            native_call(protocol, "wifiScan", arguments, error="validation_failed")
        device_command("pm revoke " + DIKCIZ_DEBUG_PACKAGE + " android.permission.ACCESS_FINE_LOCATION")
        restore_system_home()
        wait_native_control(protocol)
        missing = native_call(protocol, "wifiScan", {"timeoutMilliseconds": 1}, error="native_permission_locationForeground")
        assert missing["permissionRequest"]["requirementId"] == "locationForeground"
    finally:
        grant_permissions(("android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"))
        device_command("cmd location set-location-enabled " + location_setting)
        if not originally_enabled:
            emulator_wifi_set(AUTOMATION_SYSTEM_WIFI_DISCONNECTED)
        restore_system_home()
        wait_native_control(protocol)
