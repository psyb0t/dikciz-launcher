"""Device inspection and acknowledged restart through production clients."""

import base64
import subprocess
import time

from . import *


PROTOCOLS = ("websocket", "mcp", "http")
TOOLS = {
    "deviceInfo": "dikciz_device_info",
    "launcherHandoff": "dikciz_launcher_handoff",
    "displayScreenshot": "dikciz_display_screenshot",
    "packageInfo": "dikciz_package_info",
    "restart": "dikciz_restart",
    "configGet": "dikciz_config_get",
    "snapshot": "dikciz_snapshot",
}
RECOVERY_SECONDS = 45


def remote_call(protocol, operation, arguments=None, error=None):
    command = TOOLS[operation] if protocol == "mcp" else operation
    port = AUTOMATION_PORT if protocol == "websocket" else MCP_PORT
    completed = subprocess.run(
        ["python3", str(LAB_ROOT / "scripts/dikciz-control.py"), protocol, command,
         "--host", AUTOMATION_HOST, "--port", str(port), "--arguments", json.dumps(arguments or {}),
         "--log-file", "/tmp/dikciz-remote-device-client.log"],
        capture_output=True, text=True, timeout=RECOVERY_SECONDS,
    )
    if error is not None:
        assert completed.returncode != 0, completed.stdout
        response = json.loads(completed.stdout)
        detail = response["response"]
        if protocol == "mcp":
            detail = detail["structuredContent"]
        assert detail["code"] == error, response
        return detail
    assert completed.returncode == 0, (completed.stdout, completed.stderr)
    response = json.loads(completed.stdout)
    result = response["structuredContent" if protocol == "mcp" else "result"]
    if protocol == "mcp" and operation == "displayScreenshot":
        result["pngBase64"] = next(item["data"] for item in response["content"] if item["type"] == "image")
    return result


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_remote_device_inspection(websocket_control, protocol):
    if protocol == "websocket":
        AUTOMATION.verify_control_status(websocket_control)
    info = remote_call(protocol, "deviceInfo")
    app = info["application"]
    assert app["packageName"] == DIKCIZ_DEBUG_PACKAGE
    assert app["versionName"]
    assert app["versionCode"] > 0
    assert app["processId"] > 0
    assert len(app["instanceId"]) == 36
    assert info["android"]["sdkInt"] >= 26
    package = remote_call(protocol, "packageInfo", {"packageName": DIKCIZ_DEBUG_PACKAGE})
    assert package["versionName"] == app["versionName"]
    assert package["packageName"] == app["packageName"]
    assert remote_call(protocol, "packageInfo", {"packageName": "android"})["packageName"] == "android"
    remote_call(protocol, "packageInfo", {"packageName": "invalid.absent.package"}, error="package_not_found")


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_remote_device_package_validation(websocket_control, protocol):
    for arguments in (
        {}, {"packageName": None}, {"packageName": ""}, {"packageName": 3},
        {"packageName": "a;id"}, {"packageName": "a/../b"},
        {"packageName": "a." + "b" * 254}, {"packageName": "a.b", "extra": True},
    ):
        remote_call(protocol, "packageInfo", arguments, error="validation_failed")

@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_remote_device_restart_acknowledged_and_preserves_home(websocket_control, protocol):
    before = remote_call(protocol, "deviceInfo")["application"]
    configuration = remote_call(protocol, "configGet")
    acknowledgement = remote_call(protocol, "restart")
    assert acknowledgement["state"] == "scheduled"
    assert acknowledgement["instanceId"] == before["instanceId"]
    deadline = time.monotonic() + RECOVERY_SECONDS
    last_error = None
    while time.monotonic() < deadline:
        try:
            after = remote_call("http", "deviceInfo")["application"]
        except AssertionError as exception:
            last_error = exception
            time.sleep(0.25)
            continue
        if after["instanceId"] != before["instanceId"]:
            break
        time.sleep(0.25)
    else:
        raise AssertionError("launcher did not recreate and reconnect") from last_error
    assert after["processId"] == before["processId"]
    assert remote_call("http", "configGet") == configuration
    assert remote_call("mcp", "snapshot")["screen"] == "home"
    assert remote_call("websocket", "deviceInfo")["application"]["instanceId"] == after["instanceId"]


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_remote_device_display_screenshot_requires_access_and_captures_external_app(websocket_control, protocol):
    from .test_accessibility_automation import wait_for_accessibility_snapshot

    emulator_dikciz_accessibility_set(False)
    remote_call(protocol, "displayScreenshot", error="accessibility_not_enabled")
    try:
        emulator_dikciz_accessibility_set(True)
        AUTOMATION.request(
            websocket_control, AUTOMATION.TYPE_INTENT,
            intentType="activity", action="android.settings.SETTINGS",
        )
        snapshot = wait_for_accessibility_snapshot(websocket_control)
        assert snapshot["packageName"] == "com.android.settings"
        screenshot = remote_call(protocol, "displayScreenshot")
        png = base64.b64decode(screenshot["pngBase64"], validate=True)
        assert png[:8] == bytes([137, 80, 78, 71, 13, 10, 26, 10])
        assert int.from_bytes(png[16:20], "big") == screenshot["width"]
        assert int.from_bytes(png[20:24], "big") == screenshot["height"]
        (ARTIFACT_DIRECTORY / f"display-screenshot-{protocol}.png").write_bytes(png)
    finally:
        emulator_dikciz_accessibility_set(False)
        run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_remote_device_no_argument_commands_reject_unknown_fields(websocket_control, protocol):
    for operation in ("restart", "deviceInfo", "displayScreenshot"):
        remote_call(protocol, operation, {"extra": True}, error="validation_failed")

def test_remote_device_listener_recovers_after_port_contention(websocket_control):
    listener_pid = None
    run_device_operation("app-stop", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
    try:
        output = device_command(
            "nohup toybox nc -s 127.0.0.1 -p 19001 -l </dev/null >/dev/null 2>&1 & echo $!"
        ).strip()
        assert output.isdecimal(), output
        listener_pid = int(output)
        device_command(f"kill -0 {listener_pid}")
        run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
        time.sleep(4)
        device_command(f"kill -0 {listener_pid}")
    finally:
        if listener_pid is not None:
            device_command(f"kill -TERM {listener_pid}")
    deadline = time.monotonic() + RECOVERY_SECONDS
    last_error = None
    while time.monotonic() < deadline:
        try:
            info = remote_call("http", "deviceInfo")
        except AssertionError as exception:
            last_error = exception
            time.sleep(0.5)
            continue
        assert info["application"]["packageName"] == DIKCIZ_DEBUG_PACKAGE
        break
    else:
        raise AssertionError("listeners did not recover after the conflicting socket closed") from last_error
    assert remote_call("websocket", "snapshot")["screen"] == "home"
    assert remote_call("mcp", "deviceInfo")["application"]["packageName"] == DIKCIZ_DEBUG_PACKAGE


def test_remote_device_mcp_client_releases_sessions(websocket_control):
    for _ in range(70):
        assert remote_call("mcp", "deviceInfo")["application"]["packageName"] == DIKCIZ_DEBUG_PACKAGE
    remote_call("mcp", "packageInfo", {"packageName": "invalid.absent.package"}, error="package_not_found")
    assert remote_call("mcp", "snapshot")["screen"] == "home"


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_remote_device_handoff_rejects_other_apps_and_keeps_same_build(websocket_control, protocol):
    before = remote_call(protocol, "deviceInfo")["application"]["instanceId"]
    remote_call(protocol, "launcherHandoff", {"packageName": "com.android.settings"}, error="unsupported_launcher_package")
    response = remote_call(protocol, "launcherHandoff", {"packageName": DIKCIZ_DEBUG_PACKAGE})
    assert response["state"] == "already_active"
    assert remote_call(protocol, "deviceInfo")["application"]["instanceId"] == before


def test_remote_device_handoff_to_signed_release_without_process_kill(websocket_control):
    release = "eu.psyb0t.dikciz.launcher"
    apk = ".testing/handoff-release/dikciz-launcher-v0.15.4.apk"
    if not (LAB_ROOT / apk).is_file():
        pytest.skip("signed release compatibility fixture requires the documented handoff fixture setup")
    assert f"package:{release}" not in device_command(f"pm list packages {release}").splitlines(), "handoff fixture refuses to replace an existing release installation"
    run_device_operation("install", APK=apk)
    try:
        before = remote_call("http", "deviceInfo")["application"]
        remote_call("mcp", "launcherHandoff", {"packageName": release}, error="target_access_required")
        assert remote_call("http", "deviceInfo")["application"]["instanceId"] == before["instanceId"]
        device_command(f"appops set {release} MANAGE_EXTERNAL_STORAGE allow")
        response = remote_call("mcp", "launcherHandoff", {"packageName": release})
        assert response["state"] == "scheduled"
        assert response["instanceId"] == before["instanceId"]
        deadline = time.monotonic() + RECOVERY_SECONDS
        last_error = None
        while time.monotonic() < deadline:
            try:
                snapshot = remote_call("http", "snapshot")
            except AssertionError as exception:
                last_error = exception
                time.sleep(0.25)
                continue
            resource_ids = [node.get("resourceId", "") or "" for node in snapshot["nodes"]]
            if any(identifier.startswith(release + ":") for identifier in resource_ids):
                break
            time.sleep(0.25)
        else:
            raise AssertionError("signed release did not take over controls") from last_error
        # The original process stays alive. Its activity yields listener ownership.
        assert device_command(f"pidof {DIKCIZ_DEBUG_PACKAGE}").strip() == str(before["processId"])
    finally:
        run_device_operation("app-stop", PACKAGE_NAME=release)
        assert "Success" in device_command(f"pm uninstall {release}")
        run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)


def test_remote_device_auth_and_origin_reject_before_effects(websocket_control):
    import uuid
    from .test_device_files import http_request
    from .test_remote_bearer_auth import (
        fresh_bearer_password, open_remote_auth_settings, set_phone_password,
        clear_phone_password, connected_bearer_websocket,
    )

    before = remote_call("http", "deviceInfo")["application"]["instanceId"]
    body = {"requestId": str(uuid.uuid4()), "type": "restart"}
    status, _, _ = http_request(body, headers={"Origin": "https://example.invalid"})
    assert status == 403
    assert remote_call("http", "deviceInfo")["application"]["instanceId"] == before
    password = fresh_bearer_password()
    configured = False
    try:
        open_remote_auth_settings(websocket_control)
        set_phone_password(websocket_control, password)
        configured = True
        for operation, fields in (
            ("restart", {}), ("deviceInfo", {}), ("displayScreenshot", {}),
            ("packageInfo", {"packageName": DIKCIZ_DEBUG_PACKAGE}),
            ("launcherHandoff", {"packageName": DIKCIZ_DEBUG_PACKAGE}),
        ):
            message = {"requestId": str(uuid.uuid4()), "type": operation, **fields}
            for headers in ({}, {"Authorization": "Bearer EXAMPLE-DO-NOT-USE"}):
                status, _, _ = http_request(message, headers=headers)
                assert status == 401
        status, _, response = http_request(
            {"requestId": str(uuid.uuid4()), "type": "deviceInfo"},
            headers={"Authorization": "Bearer " + password},
        )
        assert status == 200
        assert response["result"]["application"]["instanceId"] == before
    finally:
        if configured:
            with connected_bearer_websocket(password) as connection:
                clear_phone_password(connection)


def test_remote_device_html_restart_delivers_response_before_recreation(websocket_control):
    from .test_html_widget_command_queue import html_queue_configuration

    before = remote_call("http", "deviceInfo")["application"]["instanceId"]
    original = AUTOMATION.require_configuration(remote_call("http", "configGet"))
    configured = html_queue_configuration(original)
    widget = configuration_page(configured, FIXTURE_HOME_PAGE_ID)["widgets"][0]
    storage_key = f"restart-regression-{time.monotonic_ns()}"
    widget["javascript"] = """window.addEventListener("dikciz-ready", async () => {
  try {
    const key = %s;
    const stored = localStorage.getItem(key);
    if (stored) {
      await selfWidget.patchState({ lifecycleReply: JSON.parse(stored) });
      return;
    }
    const response = await dikciz.request({ type: "restart" });
    localStorage.setItem(key, JSON.stringify(response));
  } catch (error) {
    document.getElementById("queue-status").textContent = `Restart failed: ${error.code}`;
  }
});""" % json.dumps(storage_key)
    try:
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
        deadline = time.monotonic() + RECOVERY_SECONDS
        observed = None
        while time.monotonic() < deadline:
            try:
                current = AUTOMATION.require_configuration(remote_call("http", "configGet"))
            except AssertionError:
                time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
                continue
            observed = configuration_page(current, FIXTURE_HOME_PAGE_ID)["widgets"][0]["state"].get("lifecycleReply")
            if isinstance(observed, dict):
                break
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
        assert isinstance(observed, dict), observed
        assert observed["type"] == "result"
        assert observed["result"]["state"] == "scheduled"
        assert observed["result"]["instanceId"] == before
        assert remote_call("http", "deviceInfo")["application"]["instanceId"] != before
    finally:
        with connected_websocket_control() as connection:
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_REPLACE, config=original)
