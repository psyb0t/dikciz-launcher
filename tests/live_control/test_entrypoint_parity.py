"""Compare operation results through the real control bindings."""

import base64
import subprocess
import uuid

from . import *
from . import test_remote_device_control as REMOTE
from .test_accessibility_revocation import wait_for_disabled_accessibility


PARITY_WIDGET_ID = "entrypoint-parity"
PARITY_PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
PARITY_LUA_BUDGET_SOURCES = {
    "native_results": """function on_event(event)
      for index = 1, 8 do
        local reply = context.requestJson([[{"type":"shell","root":false,"command":"yes x | head -c 524288","maxOutputBytes":524288}]])
      end
      return { status = "budget_was_bypassed" }
    end""",
    "lua_loop": "function on_event(event) while true do end end",
}
PARITY_HTML_RECOVERY_SCRIPT = """window.addEventListener("dikciz-ready", async () => {
  let response;
  try {
    response = await dikciz.command("accessibilitySnapshot", { autoRequestPermissions: false });
  } catch (error) {
    response = { code: error.code, message: error.message, permissionRequest: error.permissionRequest ?? { missingRecovery: true } };
  }
  await selfWidget.patchState({ parityResponse: response });
});"""


def parity_html_configuration(original: dict[str, Any], javascript: str) -> dict[str, Any]:
    configured = copy.deepcopy(original)
    page = configuration_page(configured, FIXTURE_HOME_PAGE_ID)
    page["widgets"] = [{
        "id": PARITY_WIDGET_ID,
        "type": "html",
        "title": "Control parity",
        "html": "<article><h2>Control parity</h2></article>",
        "css": "body{color:white;background:#101722}",
        "javascript": javascript,
        "state": {},
        "enabled": True,
        "cell": {"column": 0, "row": 0, "columnSpan": 4, "rowSpan": 6},
    }]
    configured["launcher"]["home"]["selectedPageId"] = FIXTURE_HOME_PAGE_ID
    return configured


def wait_for_parity_response(connection: socket.socket) -> dict[str, Any]:
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    observed = None
    while time.monotonic() < deadline:
        configured = AUTOMATION.require_configuration(AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET))
        observed = configuration_widget(configured, PARITY_WIDGET_ID)["state"].get("parityResponse")
        if isinstance(observed, dict):
            return observed
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"HTML control reply did not arrive: {observed!r}")


def test_entrypoint_parity_html_preserves_permission_recovery(websocket_control: socket.socket) -> None:
    originally_enabled = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_STATUS)["androidAccess"]["accessibility"]
    original = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
    emulator_dikciz_accessibility_set(False)
    try:
        wait_for_disabled_accessibility(websocket_control)
        _, expected = AUTOMATION.request_error_with_id(
            websocket_control,
            AUTOMATION.TYPE_ACCESSIBILITY_SNAPSHOT,
            autoRequestPermissions=False,
        )
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=parity_html_configuration(original, PARITY_HTML_RECOVERY_SCRIPT),
        )
        response = wait_for_parity_response(websocket_control)
        assert response == {key: expected[key] for key in ("code", "message", "permissionRequest")}
    finally:
        emulator_dikciz_accessibility_set(originally_enabled)


def test_entrypoint_parity_lua_owner_commands_return_canonical_reply(websocket_control: socket.socket) -> None:
    original = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
    payload = json.dumps({"type": "packageInfo", "packageName": DIKCIZ_DEBUG_PACKAGE})
    source = """function on_event(event)
  local reply = context.requestJson([==[%s]==])
  return { actions = {{ type = "patchState", values = { parityReply = reply } }} }
end""" % payload
    configured = automation_configuration(
        original,
        source,
        {},
        ["manualTrigger"],
        ["patchState"],
        [{"event": "manual", "minimumIntervalMilliseconds": 0, "coalescingKey": "owner-commands"}],
    )
    expected = REMOTE.remote_call("websocket", "packageInfo", {"packageName": DIKCIZ_DEBUG_PACKAGE})
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_TRIGGER, scriptId=AUTOMATION_LIVE_SCRIPT_ID)
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    observed = None
    while time.monotonic() < deadline:
        current = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
        script = next(script for script in current["scripts"] if script["id"] == AUTOMATION_LIVE_SCRIPT_ID)
        observed = script["state"].get("parityReply")
        if isinstance(observed, str):
            break
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    if not isinstance(observed, str):
        status = device_command(f"cat /sdcard/Dikciz/scripts/{AUTOMATION_LIVE_SCRIPT_ID}/run-status.json")
        logs = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SCRIPT_LOGS)
        raise AssertionError(f"Lua control reply did not arrive: state={observed!r}, status={status}, logs={logs!r}")
    response = json.loads(observed)
    assert response["type"] == "result"
    assert response["result"] == expected


@pytest.mark.parametrize("limit", tuple(PARITY_LUA_BUDGET_SOURCES))
def test_entrypoint_parity_lua_keeps_execution_and_result_budgets(
    websocket_control: socket.socket,
    limit: str,
) -> None:
    original = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
    configured = automation_configuration(
        original,
        PARITY_LUA_BUDGET_SOURCES[limit],
        {},
        ["manualTrigger"],
        [],
        [{"event": "manual", "minimumIntervalMilliseconds": 0, "coalescingKey": "owner-commands"}],
    )
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_TRIGGER, scriptId=AUTOMATION_LIVE_SCRIPT_ID)
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    status = {}
    while time.monotonic() < deadline:
        status = json.loads(device_command(f"cat /sdcard/Dikciz/scripts/{AUTOMATION_LIVE_SCRIPT_ID}/run-status.json"))
        if status.get("outcome") == "failed":
            break
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    assert status.get("outcome") == "failed", status
    expected = {"memory_budget_exceeded"} if limit == "native_results" else {
        "execution_deadline_exceeded", "instruction_budget_exceeded", "memory_budget_exceeded",
    }
    assert status["status"] in expected, status


def test_entrypoint_parity_lua_restart_saves_response_before_recreation(websocket_control: socket.socket) -> None:
    original = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
    before = REMOTE.remote_call("http", "deviceInfo")["application"]["instanceId"]
    source = """function on_event(event)
      local reply = context.requestJson([[{"type":"restart"}]])
      return { actions = {{ type = "patchState", values = { lifecycleReply = reply } }} }
    end"""
    configured = automation_configuration(
        original, source, {}, ["manualTrigger"], ["patchState"],
        [{"event": "manual", "minimumIntervalMilliseconds": 0, "coalescingKey": "owner-restart"}],
    )
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_TRIGGER, scriptId=AUTOMATION_LIVE_SCRIPT_ID)
    deadline = time.monotonic() + REMOTE.RECOVERY_SECONDS
    current_instance = before
    while time.monotonic() < deadline:
        try:
            current_instance = REMOTE.remote_call("http", "deviceInfo")["application"]["instanceId"]
        except AssertionError:
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
            continue
        if current_instance != before:
            break
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    assert current_instance != before
    saved = AUTOMATION.require_configuration(REMOTE.remote_call("http", "configGet"))
    script = next(script for script in saved["scripts"] if script["id"] == AUTOMATION_LIVE_SCRIPT_ID)
    response = json.loads(script["state"]["lifecycleReply"])
    assert response["type"] == "result"
    assert response["result"]["state"] == "scheduled"
    assert response["result"]["instanceId"] == before


@pytest.mark.parametrize("protocol", ("websocket", "http", "mcp"))
def test_entrypoint_parity_png_cli_round_trip_and_overwrite(
    websocket_control: socket.socket,
    protocol: str,
) -> None:
    path = ARTIFACT_DIRECTORY / ("parity-screen-" + uuid.uuid4().hex + ".png")
    port = AUTOMATION_PORT if protocol == "websocket" else MCP_PORT
    operation = "dikciz_screenshot" if protocol == "mcp" else "screenshot"
    command = [
        "python3", str(LAB_ROOT / "scripts/dikciz-control.py"), protocol, operation,
        "--host", AUTOMATION_HOST, "--port", str(port), "--png-file", str(path),
        "--log-file", "/tmp/dikciz-parity-png-client.log",
    ]
    first = subprocess.run(command, capture_output=True, text=True, timeout=REMOTE.RECOVERY_SECONDS)
    assert first.returncode == 0, (first.stdout, first.stderr)
    response = json.loads(first.stdout)
    result = response["structuredContent" if protocol == "mcp" else "result"]
    encoded = result["pngBase64"]
    image = base64.b64decode(encoded, validate=True)
    assert image.startswith(PARITY_PNG_SIGNATURE)
    assert path.read_bytes() == image
    if protocol == "mcp":
        assert next(item["data"] for item in response["content"] if item["type"] == "image") == encoded
    denied = subprocess.run(command, capture_output=True, text=True, timeout=REMOTE.RECOVERY_SECONDS)
    assert denied.returncode != 0
    assert path.read_bytes() == image
    overwritten = subprocess.run(command + ["--overwrite"], capture_output=True, text=True, timeout=REMOTE.RECOVERY_SECONDS)
    assert overwritten.returncode == 0, (overwritten.stdout, overwritten.stderr)
    result = json.loads(overwritten.stdout)["structuredContent" if protocol == "mcp" else "result"]
    assert path.read_bytes() == base64.b64decode(result["pngBase64"], validate=True)
