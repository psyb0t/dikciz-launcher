"""The background automation service survives rapid start and stop requests."""

from __future__ import annotations

import socket
import time

from . import *


FOREGROUND_START_CRASH = "ForegroundServiceDidNotStartInTimeException"
AUTOMATION_SERVICE_CLASS = "DikcizAutomationService"
RAPID_TOGGLE_COUNT = 20
COLD_START_COUNT = 5
# Android's deadline from startForegroundService to startForeground, plus margin.
FOREGROUND_DEADLINE_SECONDS = 12
SERVICE_STATE_TIMEOUT_SECONDS = 15
SERVICE_STATE_POLL_SECONDS = 0.5


def automation_service_running() -> bool:
    output = device_command(f"dumpsys activity services {DIKCIZ_DEBUG_PACKAGE}")
    return AUTOMATION_SERVICE_CLASS in output


def wait_for_service_state(running: bool) -> None:
    deadline = time.monotonic() + SERVICE_STATE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        if automation_service_running() == running:
            return
        time.sleep(SERVICE_STATE_POLL_SECONDS)
    state = "start" if running else "stop"
    raise AssertionError(f"automation service did not {state}")


def launcher_process_id() -> str:
    return device_command(f"pidof {DIKCIZ_DEBUG_PACKAGE}").strip()


def replace_configuration(connection: socket.socket, configuration: dict[str, Any]) -> None:
    AUTOMATION.require_configuration(
        AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_REPLACE, config=configuration),
    )


def test_automation_service_rapid_start_and_stop_never_crashes_the_launcher(
    websocket_control: socket.socket,
) -> None:
    fixture = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    # The bundled starter home runs a timed clock script, so it needs the background service.
    bundled = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_RESET))
    wait_for_service_state(running=True)
    replace_configuration(websocket_control, fixture)
    wait_for_service_state(running=False)

    device_command("logcat -b crash -c")
    process_id = launcher_process_id()
    assert process_id

    for _ in range(RAPID_TOGGLE_COUNT):
        replace_configuration(websocket_control, bundled)
        replace_configuration(websocket_control, fixture)

    wait_for_service_state(running=False)
    assert FOREGROUND_START_CRASH not in device_command("logcat -d -b crash")
    assert launcher_process_id() == process_id


def test_automation_service_survives_cold_starts_followed_by_immediate_configuration_changes(
    websocket_control: socket.socket,
) -> None:
    fixture = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    bundled = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_RESET))
    device_command("logcat -b crash -c")
    for _ in range(COLD_START_COUNT):
        # The cold start loads the bundled home, which starts the service while the
        # launcher is still creating its first page.
        replace_configuration(websocket_control, bundled)
        run_device_operation("app-stop", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
        run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
        websocket_control.close()
        websocket_control = wait_for_restarted_websocket_control()
        replace_configuration(websocket_control, fixture)
        replace_configuration(websocket_control, bundled)
        wait_for_service_state(running=True)
        time.sleep(FOREGROUND_DEADLINE_SECONDS)
        assert FOREGROUND_START_CRASH not in device_command("logcat -d -b crash")
        assert launcher_process_id()
    replace_configuration(websocket_control, fixture)
    websocket_control.close()
