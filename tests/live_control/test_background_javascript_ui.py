"""Create, edit and transfer JavaScript scripts through native controls."""

import json
import zipfile

from . import *
from .test_background_javascript import saved_script


def open_script_manager(connection):
    snapshot = AUTOMATION.request(connection, AUTOMATION.TYPE_SNAPSHOT)
    if not AUTOMATION.snapshot_has_node(snapshot, "script:create:javascript"):
        open_command_sheet()
        AUTOMATION.request(connection, "tap", semanticId=COMMAND_SETTINGS_SEMANTIC_ID)
        wait_for_snapshot_node(connection, "settings:scripts")
        AUTOMATION.request(connection, "tap", semanticId="settings:scripts")
    wait_for_snapshot_node(connection, "script:create:javascript")


def test_background_javascript_native_editor_and_archive_round_trip(websocket_control):
    open_script_manager(websocket_control)
    AUTOMATION.request(websocket_control, "tap", semanticId="script:create:javascript")
    snapshot = wait_for_snapshot_node(websocket_control, "lua-script:source")
    source = AUTOMATION.require_node(snapshot, "lua-script:source")["text"]
    assert "function onEvent(event)" in source
    run_device_operation("screenshot")
    AUTOMATION.request(websocket_control, "setText", semanticId="lua-script:title", text="JavaScript UI fixture")
    AUTOMATION.request(websocket_control, "tap", semanticId="lua-script:save")
    configured = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
    script = next(item for item in configured["scripts"] if item["title"] == "JavaScript UI fixture")
    assert script["language"] == "javascript" and script["source"] == source
    identifier = script["id"]
    AUTOMATION.request(websocket_control, "tap", semanticId="lua-script:" + identifier)
    wait_for_snapshot_node(websocket_control, "lua-script:export")
    AUTOMATION.request(websocket_control, "tap", semanticId="lua-script:export")
    exports = device_command("ls /sdcard/Dikciz/scripts/exports").splitlines()
    name = next(item for item in exports if item.startswith(identifier + "-"))
    run_device_operation("file-pull", DEVICE_FILE="/sdcard/Dikciz/scripts/exports/" + name, ARTIFACT_FILE="javascript-export.zip")
    with zipfile.ZipFile(ARTIFACT_DIRECTORY / "javascript-export.zip") as archive:
        assert set(archive.namelist()) == {"archive.json", "script.json", "main.js", "state.json"}
        assert json.loads(archive.read("script.json"))["language"] == "javascript"
        assert archive.read("main.js").decode() == source
    device_command("mkdir -p /sdcard/Dikciz/scripts/imports")
    device_command("cp /sdcard/Dikciz/scripts/exports/" + name + " /sdcard/Dikciz/scripts/imports/" + name)
    AUTOMATION.request(websocket_control, "tap", semanticId="dialog:close")
    configured["scripts"] = [item for item in configured["scripts"] if item["id"] != identifier]
    AUTOMATION.request(websocket_control, "configReplace", config=configured)
    open_script_manager(websocket_control)
    AUTOMATION.request(websocket_control, "tap", semanticId="lua-script:import")
    wait_for_snapshot_node(websocket_control, "lua-script:import-file:" + name)
    AUTOMATION.request(websocket_control, "tap", semanticId="lua-script:import-file:" + name)
    wait_for_snapshot_node(websocket_control, "lua-script:import-confirm")
    AUTOMATION.request(websocket_control, "tap", semanticId="lua-script:import-confirm")
    result = saved_script(websocket_control, identifier)
    assert result["language"] == "javascript" and result["source"] == source
