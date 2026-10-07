"""Shared checks for persisted Lua script outcomes."""

from . import *


SCRIPT_STATUS_TIMEOUT_SECONDS = 80
SCRIPT_STATUS_POLL_SECONDS = 1


def wait_script_result(script_id: str, outcome: str) -> dict[str, Any]:
    deadline = time.monotonic() + SCRIPT_STATUS_TIMEOUT_SECONDS
    path = f"/sdcard/Dikciz/scripts/{script_id}/run-status.json"
    while time.monotonic() < deadline:
        document = json.loads(device_command(f"if test -f {path}; then cat {path}; else echo '{{}}'; fi"))
        if document.get("outcome") == outcome:
            return document
        time.sleep(SCRIPT_STATUS_POLL_SECONDS)
    raise AssertionError(f"Bundled script {script_id} did not report {outcome}")
