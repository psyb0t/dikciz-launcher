"""Shared installed-device checks for Android Accessibility scenarios."""

from . import *


def has_settings_destination(snapshot: dict[str, Any]) -> bool:
    return any(
        node["packageName"] == ACCESSIBILITY_ANDROID_PACKAGE
        and node["visibleToUser"]
        and node["contentDescription"] == ACCESSIBILITY_SETTINGS_ENTRY_TEXT
        for node in snapshot["nodes"]
    )


def wait_for_settings_destination(connection: socket.socket) -> dict[str, Any]:
    deadline = time.monotonic() + ACCESSIBILITY_SERVICE_READY_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        snapshot = AUTOMATION.request(connection, AUTOMATION.TYPE_ACCESSIBILITY_SNAPSHOT)
        if has_settings_destination(snapshot):
            return snapshot
        time.sleep(ACCESSIBILITY_SNAPSHOT_POLL_SECONDS)
    raise AssertionError("Android Settings did not open the target destination")
