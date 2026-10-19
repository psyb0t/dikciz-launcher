"""Live control-plane address and ports in /sdcard/Dikciz/control/ports.json."""

from __future__ import annotations

import json
import re
import shlex
import shutil
import socket
import time

from . import *


CONTROL_PORTS_DIRECTORY = "/sdcard/Dikciz/control"
CONTROL_PORTS_FILE = f"{CONTROL_PORTS_DIRECTORY}/ports.json"
CONTROL_PORTS_VERSION = 1
CONTROL_PORTS_ARTIFACT_NAME = "control-ports-settings.png"
DEFAULT_WEBSOCKET_PORT = 19001
DEFAULT_MCP_PORT = 19002
SETTINGS_WEBSOCKET_PORT = 19011
SETTINGS_MCP_PORT = 19012
FILE_WEBSOCKET_PORT = 19021
FILE_MCP_PORT = 19022
PRIVILEGED_PORT = 80
LISTENER_TIMEOUT_SECONDS = 30
LISTENER_POLL_SECONDS = 1
CONFIGURATION_SETTLE_SECONDS = 3
LISTENER_PATTERN = re.compile(r"(?P<address>127\.0\.0\.1|0\.0\.0\.0|\[?::1?\]?):(?P<port>\d+)\s+\S+\s+LISTEN")
FILE_PRESENT = "present"
FILE_ABSENT = "absent"
SEMANTIC_PAGE_MENU_SETTINGS = f"page:menu:{FIXTURE_HOME_PAGE_ID}:settings"
SEMANTIC_SETTINGS_AUTOMATION = "settings:automation"
SEMANTIC_SETTINGS_CONTROL_PORTS = "settings:automation:control-ports"
SEMANTIC_CONTROL_PORTS_WEBSOCKET = "control-ports:websocket"
SEMANTIC_CONTROL_PORTS_MCP = "control-ports:mcp"
SEMANTIC_CONTROL_PORTS_SAVE = "control-ports:save"
SEMANTIC_CONTROL_LISTEN_ADDRESS = "control-ports:listen-address"


def listening_ports(address: str = "127.0.0.1") -> set[int]:
    output = device_command("netstat -ltn")
    addresses = {address, "::"} if address == "0.0.0.0" else {address}
    return {
        int(match.group("port"))
        for match in LISTENER_PATTERN.finditer(output)
        if match.group("address").strip("[]") in addresses
    }


def wait_for_listeners(present: set[int], absent: set[int], address: str = "127.0.0.1") -> None:
    deadline = time.monotonic() + LISTENER_TIMEOUT_SECONDS
    ports: set[int] = set()
    while time.monotonic() < deadline:
        ports = listening_ports(address)
        if present <= ports and not absent & ports:
            return
        time.sleep(LISTENER_POLL_SECONDS)
    raise AssertionError(f"expected listeners {sorted(present)} without {sorted(absent)}, saw {sorted(ports)}")


def ports_file_state() -> str:
    return device_command(f"test -e {CONTROL_PORTS_FILE} && echo {FILE_PRESENT} || echo {FILE_ABSENT}").strip()


def write_ports_file(websocket_port: int, mcp_port: int, listen_address: object = "legacy", atomic: bool = False) -> None:
    record = {"version": CONTROL_PORTS_VERSION, "websocketPort": websocket_port, "mcpPort": mcp_port}
    if listen_address != "legacy":
        record["listenAddress"] = listen_address
    payload = json.dumps(
        record,
        separators=(",", ":"),
    )
    destination = f"{CONTROL_PORTS_FILE}.pending" if atomic else CONTROL_PORTS_FILE
    device_command(f"mkdir -p {CONTROL_PORTS_DIRECTORY} && printf %s {shlex.quote(payload)} > {destination}")
    if atomic:
        device_command(f"mv {destination} {CONTROL_PORTS_FILE}")


def restart_dikciz() -> None:
    run_device_operation("app-stop", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
    run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)


def restore_default_ports() -> None:
    device_command(f"rm -f {CONTROL_PORTS_FILE}")
    restart_dikciz()
    wait_for_restarted_websocket_control().close()
    wait_for_listeners({DEFAULT_WEBSOCKET_PORT, DEFAULT_MCP_PORT}, set())


def reported_ports(connection: socket.socket) -> tuple[int, int]:
    status = AUTOMATION.request(connection, AUTOMATION.TYPE_CONTROL_STATUS)
    transports = status["transports"]
    return transports["webSocket"]["port"], transports["mcp"]["port"]


def open_control_ports_settings(connection: socket.socket) -> None:
    open_page_menu_on_selected_page()
    wait_for_snapshot_node(connection, SEMANTIC_PAGE_MENU_SETTINGS)
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=SEMANTIC_PAGE_MENU_SETTINGS)
    wait_for_snapshot_node(connection, SEMANTIC_SETTINGS_AUTOMATION)
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=SEMANTIC_SETTINGS_AUTOMATION)
    wait_for_snapshot_node(connection, SEMANTIC_SETTINGS_CONTROL_PORTS)
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=SEMANTIC_SETTINGS_CONTROL_PORTS)
    wait_for_snapshot_node(connection, SEMANTIC_CONTROL_PORTS_SAVE)


def submit_ports(connection: socket.socket, websocket_port: int, mcp_port: int) -> None:
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_SET_TEXT,
        semanticId=SEMANTIC_CONTROL_PORTS_WEBSOCKET,
        text=str(websocket_port),
    )
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_SET_TEXT,
        semanticId=SEMANTIC_CONTROL_PORTS_MCP,
        text=str(mcp_port),
    )
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=SEMANTIC_CONTROL_PORTS_SAVE)


def test_control_ports_settings_reject_bad_ports_and_move_both_listeners(
    websocket_control: socket.socket,
) -> None:
    assert reported_ports(websocket_control) == (DEFAULT_WEBSOCKET_PORT, DEFAULT_MCP_PORT)
    assert ports_file_state() == FILE_ABSENT
    try:
        open_control_ports_settings(websocket_control)
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / CONTROL_PORTS_ARTIFACT_NAME)

        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_SET_TEXT,
            semanticId=SEMANTIC_CONTROL_LISTEN_ADDRESS,
            text="localhost",
        )
        submit_ports(websocket_control, SETTINGS_WEBSOCKET_PORT, SETTINGS_MCP_PORT)
        assert ports_file_state() == FILE_ABSENT
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_SET_TEXT,
            semanticId=SEMANTIC_CONTROL_LISTEN_ADDRESS,
            text="127.0.0.1",
        )
        submit_ports(websocket_control, SETTINGS_WEBSOCKET_PORT, SETTINGS_WEBSOCKET_PORT)
        assert ports_file_state() == FILE_ABSENT
        submit_ports(websocket_control, PRIVILEGED_PORT, SETTINGS_MCP_PORT)
        assert ports_file_state() == FILE_ABSENT
        wait_for_listeners({DEFAULT_WEBSOCKET_PORT, DEFAULT_MCP_PORT}, set())

        submit_ports(websocket_control, SETTINGS_WEBSOCKET_PORT, SETTINGS_MCP_PORT)
        wait_for_listeners(
            {SETTINGS_WEBSOCKET_PORT, SETTINGS_MCP_PORT},
            {DEFAULT_WEBSOCKET_PORT, DEFAULT_MCP_PORT},
        )
        saved = json.loads(device_command(f"cat {CONTROL_PORTS_FILE}"))
        assert saved == {
            "version": CONTROL_PORTS_VERSION,
            "listenAddress": "127.0.0.1",
            "websocketPort": SETTINGS_WEBSOCKET_PORT,
            "mcpPort": SETTINGS_MCP_PORT,
        }
    finally:
        restore_default_ports()


def test_control_ports_file_edits_apply_live_and_invalid_files_fall_back(
    websocket_control: socket.socket,
) -> None:
    try:
        write_ports_file(FILE_WEBSOCKET_PORT, FILE_MCP_PORT)
        wait_for_listeners(
            {FILE_WEBSOCKET_PORT, FILE_MCP_PORT},
            {DEFAULT_WEBSOCKET_PORT, DEFAULT_MCP_PORT},
        )

        # Equal ports are invalid, so Dikciz uses the defaults instead of locking the user out.
        write_ports_file(FILE_WEBSOCKET_PORT, FILE_WEBSOCKET_PORT)
        wait_for_listeners({DEFAULT_WEBSOCKET_PORT, DEFAULT_MCP_PORT}, {FILE_WEBSOCKET_PORT, FILE_MCP_PORT})
        restarted = wait_for_restarted_websocket_control()
        try:
            assert reported_ports(restarted) == (DEFAULT_WEBSOCKET_PORT, DEFAULT_MCP_PORT)
        finally:
            restarted.close()
        wait_for_listeners(
            {DEFAULT_WEBSOCKET_PORT, DEFAULT_MCP_PORT},
            {FILE_WEBSOCKET_PORT, FILE_MCP_PORT},
        )
    finally:
        restore_default_ports()

def test_control_ports_address_edits_rebind_both_planes_without_app_restart(
    websocket_control: socket.socket,
) -> None:
    from .test_remote_bearer_auth import connected_mcp, mcp_tool, websocket_upgrade_response

    process_id = device_command(f"pidof {DIKCIZ_DEBUG_PACKAGE}").strip()
    device_command("am start -a android.settings.SETTINGS")
    try:
        write_ports_file(DEFAULT_WEBSOCKET_PORT, DEFAULT_MCP_PORT, "0.0.0.0", atomic=True)
        wait_for_listeners({DEFAULT_WEBSOCKET_PORT, DEFAULT_MCP_PORT}, set(), "0.0.0.0")
        connection = wait_for_restarted_websocket_control()
        try:
            status = AUTOMATION.request(connection, AUTOMATION.TYPE_CONTROL_STATUS)
            assert all(transport["listenAddress"] == "0.0.0.0" for transport in status["transports"].values())
            assert device_command(f"pidof {DIKCIZ_DEBUG_PACKAGE}").strip() == process_id
            assert websocket_upgrade_response(origin="https://evil.example")[0] == "HTTP/1.1 403 Forbidden"
            with connected_mcp() as (arguments, session_id):
                mcp_status = mcp_tool(arguments, session_id, 3, "dikciz_control_status")
                assert mcp_status["transports"] == status["transports"]
            write_ports_file(DEFAULT_WEBSOCKET_PORT, DEFAULT_MCP_PORT, "0.0.0.0")
            time.sleep(CONFIGURATION_SETTLE_SECONDS)
            assert reported_ports(connection) == (DEFAULT_WEBSOCKET_PORT, DEFAULT_MCP_PORT)
        finally:
            connection.close()
        device_command(f"rm -f {CONTROL_PORTS_FILE}")
        wait_for_listeners({DEFAULT_WEBSOCKET_PORT, DEFAULT_MCP_PORT}, set())
        assert not {DEFAULT_WEBSOCKET_PORT, DEFAULT_MCP_PORT} & listening_ports("0.0.0.0")
    finally:
        restore_default_ports()


@pytest.mark.parametrize("address", ["", None, 12, "localhost", " 0.0.0.0", "999.1.1.1", "abc::def::1", "x" * 257])
def test_control_ports_invalid_addresses_fall_back_to_loopback(address: object) -> None:
    try:
        write_ports_file(FILE_WEBSOCKET_PORT, FILE_MCP_PORT, "0.0.0.0")
        wait_for_listeners({FILE_WEBSOCKET_PORT, FILE_MCP_PORT}, set(), "0.0.0.0")
        write_ports_file(FILE_WEBSOCKET_PORT, FILE_MCP_PORT, address)
        time.sleep(CONFIGURATION_SETTLE_SECONDS)
        wait_for_listeners({DEFAULT_WEBSOCKET_PORT, DEFAULT_MCP_PORT}, {FILE_WEBSOCKET_PORT, FILE_MCP_PORT})
    finally:
        restore_default_ports()


@pytest.mark.parametrize("address", ["::1", "::"])
def test_control_ports_ipv6_listeners_apply_live(address: str) -> None:
    try:
        write_ports_file(FILE_WEBSOCKET_PORT, FILE_MCP_PORT, address)
        wait_for_listeners({FILE_WEBSOCKET_PORT, FILE_MCP_PORT}, set(), address)
    finally:
        restore_default_ports()
