"""Rendered settings, native operations and form reachability on the lab phone."""

import shutil
import time

import pytest

from . import *
from .test_launcher_controls import open_launcher_control_sheet
from .ui import direct_tap, wait_for_physical_text_bounds


def capture(name):
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / ("operations-ux-" + name + ".png"))


def settings(connection):
    restore_system_home()
    open_launcher_control_sheet()
    AUTOMATION.request(connection, "tap", semanticId=LAUNCHER_CONTROL_SETTINGS_SEMANTIC_ID)
    wait_for_physical_text_bounds(DIKCIZ_SETTINGS_TITLE)


def tap(connection, semantic_id):
    AUTOMATION.request(connection, "scrollTo", semanticId=semantic_id)
    AUTOMATION.request(connection, "tap", semanticId=semantic_id)


@pytest.mark.parametrize("semantic_id,name", [
    ("settings:getting-started", "getting-started"),
    ("settings:appearance:widget", "appearance"),
    ("settings:themes", "themes"),
    ("settings:scripts", "scripts"),
    ("settings:logging", "logging"),
    ("settings:automation", "automation"),
    ("settings:backups", "backups"),
])
def test_operations_ux_settings_surfaces(websocket_control, semantic_id, name):
    settings(websocket_control)
    capture("settings")
    tap(websocket_control, semantic_id)
    snapshot = AUTOMATION.request(websocket_control, "snapshot")
    assert snapshot["nodes"]
    capture(name)
    device_command("input keyevent 4")


def test_operations_ux_visual_rule_form(websocket_control):
    settings(websocket_control)
    tap(websocket_control, "settings:automation-rule")
    capture("rule-list")
    direct_tap(wait_for_physical_text_bounds("Create a rule"))
    wait_for_snapshot_node(websocket_control, "automation-rule:save")
    capture("rule-editor-top")
    AUTOMATION.request(websocket_control, "setText", semanticId="automation-rule:title", text="Fixture visible rule")
    tap(websocket_control, "automation-rule:preview")
    capture("rule-editor-test")
    tap(websocket_control, "automation-rule:save")
    from .test_native_data import native_call
    rules = native_call("http", "automationRulesList")["items"]
    assert any(rule["title"] == "Fixture visible rule" for rule in rules)


def test_operations_ux_activity_and_resources(websocket_control):
    settings(websocket_control)
    tap(websocket_control, "settings:operations")
    wait_for_snapshot_node(websocket_control, "operations:filter")
    capture("activity")
    AUTOMATION.request(websocket_control, "tap", semanticId="operations:filter")
    wait_for_snapshot_node(websocket_control, "operations:filter:1")
    AUTOMATION.request(websocket_control, "tap", semanticId="operations:filter:1")
    wait_for_snapshot_node(websocket_control, "operations:filter")
    capture("activity-all")
    AUTOMATION.request(websocket_control, "tap", semanticId="operations:resources")
    deadline = time.monotonic() + 20
    while True:
        snapshot = AUTOMATION.request(websocket_control, "snapshot")
        candidates = [row for row in snapshot["nodes"] if row["semanticId"].startswith("operations:row:widget:")]
        if candidates:
            break
        assert time.monotonic() < deadline, snapshot
        time.sleep(.1)
    time.sleep(3.2)
    capture("resources")
    tap(websocket_control, candidates[0]["semanticId"])
    wait_for_snapshot_node(websocket_control, "operations:dialog:confirm")
    capture("resource-confirmation")
    import base64
    from .test_native_data import native_call
    image = native_call("http", "screenshot")
    assert base64.b64decode(image["pngBase64"]).startswith(b"\x89PNG")
    (ARTIFACT_DIRECTORY / "operations-ux-confirmation-api.png").write_bytes(base64.b64decode(image["pngBase64"]))
    AUTOMATION.request(websocket_control, "tap", semanticId="operations:dialog:cancel")
    wait_for_snapshot_node(websocket_control, "operations:resources")
    AUTOMATION.request(websocket_control, "tap", semanticId="operations:close")
