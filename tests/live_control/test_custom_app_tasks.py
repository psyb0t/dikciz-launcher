"""Custom bundles have independent Android tasks and normal system navigation."""

import time

import pytest

from . import *
from .test_custom_apps import app_factory, control, COMMAND_READ, TIMEOUT_SECONDS, POLL_SECONDS
from .test_native_data import native_call, PROTOCOLS
from .test_launcher_regressions import recent_task

HOST_COMPONENT = DIKCIZ_DEBUG_PACKAGE + "/org.fossify.home.dikciz.DikcizCustomAppHostActivity"


def wait_app(connection, identifier):
    deadline = time.monotonic() + TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        snapshot = AUTOMATION.request(connection, "snapshot")
        if snapshot.get("appId") == identifier and native_call("http", COMMAND_READ, {"appId": identifier})["uiVisible"]:
            return snapshot
        time.sleep(POLL_SECONDS)
    raise AssertionError("Custom app did not become visible")


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_custom_app_tasks_independent_and_reused(websocket_control, app_factory, protocol):
    first, _ = app_factory()
    second, _ = app_factory()
    try:
        control(first, "open", protocol)
        snapshot = wait_app(websocket_control, first)
        ids = {node["semanticId"] for node in snapshot["nodes"]}
        assert "custom-app:back" not in ids and "custom-app:close" not in ids
        first_task = recent_task(HOST_COMPONENT)
        control(second, "open", protocol)
        wait_app(websocket_control, second)
        second_task = recent_task(HOST_COMPONENT)
        assert first_task != second_task
        device_command("input keyevent KEYCODE_HOME")
        assert recent_task(HOST_COMPONENT, first_task) == first_task
        assert recent_task(HOST_COMPONENT, second_task) == second_task
        control(first, "open", protocol)
        wait_app(websocket_control, first)
        assert recent_task(HOST_COMPONENT) == first_task
        device_command("input keyevent KEYCODE_BACK")
        deadline = time.monotonic() + TIMEOUT_SECONDS
        while native_call("http", COMMAND_READ, {"appId": first})["uiVisible"]:
            assert time.monotonic() < deadline
            time.sleep(POLL_SECONDS)
        control(second, "open", protocol)
        wait_app(websocket_control, second)
        assert recent_task(HOST_COMPONENT) == second_task
    finally:
        restore_system_home()


def test_custom_app_tasks_editor_draft_and_import_recovery(websocket_control, app_factory):
    from .test_custom_apps import open_app_manager
    from .test_operations_ux import tap
    from .ui import direct_tap, wait_for_physical_text_bounds
    identifier, fields = app_factory()
    draft = "<h1>Unsaved draft survives rotation</h1>"
    open_app_manager(websocket_control)
    tap(websocket_control, "custom-app:" + identifier + ":more")
    tap(websocket_control, "custom-app:" + identifier + ":edit")
    tap(websocket_control, "custom-app:editor:file")
    direct_tap(wait_for_physical_text_bounds("HTML"))
    wait_for_snapshot_node(websocket_control, "custom-app:editor:html")
    AUTOMATION.request(websocket_control, "setText", semanticId="custom-app:editor:html", text=draft)
    try:
        run_device_operation("emulator-device-orientation-set", EMULATOR_DEVICE_ORIENTATION="landscape")
        snapshot = wait_for_snapshot_node(websocket_control, "custom-app:editor:html")
        assert AUTOMATION.require_node(snapshot, "custom-app:editor:html")["text"] == draft
        device_command("input keyevent KEYCODE_BACK")
        wait_for_snapshot_node(websocket_control, "custom-app:confirm")
        direct_tap(wait_for_physical_text_bounds("Cancel"))
        snapshot = wait_for_snapshot_node(websocket_control, "custom-app:editor:html")
        assert AUTOMATION.require_node(snapshot, "custom-app:editor:html")["text"] == draft
        tap(websocket_control, "custom-app:editor:cancel")
        tap(websocket_control, "custom-app:confirm")
        wait_for_snapshot_node(websocket_control, "custom-app:new")
        assert native_call("http", COMMAND_READ, {"appId": identifier})["html"] == fields["html"]
        tap(websocket_control, "custom-app:import")
        path = "/sdcard/Dikciz/apps/imports/missing.dikciz-app.zip"
        AUTOMATION.request(websocket_control, "setText", semanticId="custom-app:import:path", text=path)
        tap(websocket_control, "custom-app:import:confirm")
        snapshot = wait_for_snapshot_node(websocket_control, "custom-app:import:error")
        assert AUTOMATION.require_node(snapshot, "custom-app:import:path")["text"] == path
        assert AUTOMATION.require_node(snapshot, "custom-app:import:confirm")["enabled"]
    finally:
        run_device_operation("emulator-device-orientation-set", EMULATOR_DEVICE_ORIENTATION="portrait")
        restore_system_home()
