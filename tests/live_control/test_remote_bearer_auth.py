"""Remote bearer authentication through the phone, WebSocket, and MCP."""

from __future__ import annotations

import argparse
from contextlib import contextmanager
import json
import secrets
import socket
from typing import Any, Generator

from . import *


AUTHORIZATION_HEADER = "Authorization"
AUTHORIZATION_SCHEME = "Bearer"
AUTHORIZATION_WWW_CHALLENGE = 'Bearer realm="Dikciz"'
HTTP_STATUS_UNAUTHORIZED = 401
REMOTE_AUTH_ARTIFACT_FILE = "remote-auth.json"
REMOTE_AUTH_ARTIFACT_NAME = "remote-bearer-auth-settings.png"
REMOTE_AUTH_FILE = "/sdcard/Dikciz/control/remote-auth.json"
REMOTE_AUTH_PASSWORD_BYTES = 24
REMOTE_AUTH_RECORD_KEYS = {"algorithm", "cost", "enabled", "verifier", "version"}
REMOTE_AUTH_STATUS_DISABLED = "disabled"
REMOTE_AUTH_STATUS_ENABLED = "enabled"
REMOTE_AUTH_STATUS_KEYS = {"configured", "enabled", "file", "state", "valid"}
REMOTE_AUTH_STATUS_UNCONFIGURED = "unconfigured"
REMOTE_AUTH_VERSION = 1
SEMANTIC_PAGE_MENU_SETTINGS = f"page:menu:{FIXTURE_HOME_PAGE_ID}:settings"
SEMANTIC_SETTINGS_AUTOMATION = "settings:automation"
SEMANTIC_SETTINGS_REMOTE_AUTH = "settings:automation:remote-auth"
SEMANTIC_REMOTE_AUTH_CLEAR = "remote-auth:clear-password"
SEMANTIC_REMOTE_AUTH_PASSWORD = "remote-auth:password"
SEMANTIC_REMOTE_AUTH_PASSWORD_CONFIRMATION = "remote-auth:password-confirmation"
SEMANTIC_REMOTE_AUTH_SET_PASSWORD = "remote-auth:set-password"
TOOL_REMOTE_AUTH_DISABLE = "dikciz_remote_auth_disable"
TOOL_REMOTE_AUTH_STATUS = "dikciz_remote_auth_status"
TYPE_REMOTE_AUTH_ENABLE = "remoteAuthEnable"
TYPE_REMOTE_AUTH_STATUS = "remoteAuthStatus"
# RFC 6455's published sample key is a valid deterministic test handshake.
WEBSOCKET_HANDSHAKE_KEY = "dGhlIHNhbXBsZSBub25jZQ=="  # gitleaks:allow
WEBSOCKET_RESPONSE_MAXIMUM_BYTES = 8_192
WEBSOCKET_RESPONSE_TERMINATOR = b"\r\n\r\n"


def bearer_authorization(password: str) -> str:
    return f"{AUTHORIZATION_SCHEME} {password}"


def fresh_bearer_password() -> str:
    return secrets.token_urlsafe(REMOTE_AUTH_PASSWORD_BYTES)


def mcp_arguments() -> argparse.Namespace:
    return argparse.Namespace(
        host=AUTOMATION_HOST,
        port=MCP_PORT,
        expected_control_port=DEVICE_MCP_PORT,
        mutate=False,
    )


def mcp_headers(
    session_id: str | None = None,
    password: str | None = None,
) -> dict[str, str]:
    headers = MCP.mcp_headers(session_id)
    if password is not None:
        headers[AUTHORIZATION_HEADER] = bearer_authorization(password)
    return headers


def mcp_initialize(arguments: argparse.Namespace, password: str | None = None) -> str:
    message = {
        MCP.KEY_ID: 1,
        MCP.KEY_METHOD: MCP.METHOD_INITIALIZE,
        MCP.KEY_PARAMS: {
            MCP.KEY_CAPABILITIES: {},
            MCP.KEY_CLIENT_INFO: {MCP.KEY_NAME: "remote-bearer-auth", "version": "1"},
            MCP.KEY_PROTOCOL_VERSION: MCP.MCP_PROTOCOL_VERSION,
        },
        "jsonrpc": MCP.JSON_RPC_VERSION,
    }
    status, headers, response = MCP.request(
        arguments,
        MCP.HTTP_METHOD_POST,
        message,
        mcp_headers(password=password),
    )
    MCP.require_status(status, MCP.HTTP_STATUS_OK)
    assert isinstance(response, dict)
    result = response.get(MCP.KEY_RESULT)
    assert isinstance(result, dict)
    assert result[MCP.KEY_PROTOCOL_VERSION] == MCP.MCP_PROTOCOL_VERSION
    session_id = headers.get(MCP.HEADER_MCP_SESSION_ID.lower())
    assert isinstance(session_id, str) and session_id
    return session_id


def mcp_initialized(
    arguments: argparse.Namespace,
    session_id: str,
    password: str | None = None,
) -> None:
    message = {
        MCP.KEY_METHOD: MCP.METHOD_INITIALIZED,
        MCP.KEY_PARAMS: {},
        "jsonrpc": MCP.JSON_RPC_VERSION,
    }
    status, _, response = MCP.request(
        arguments,
        MCP.HTTP_METHOD_POST,
        message,
        mcp_headers(session_id, password),
    )
    MCP.require_status(status, MCP.HTTP_STATUS_ACCEPTED)
    assert response is None


def mcp_tool(
    arguments: argparse.Namespace,
    session_id: str,
    request_id: int,
    tool_name: str,
    password: str | None = None,
) -> dict[str, Any]:
    message = {
        MCP.KEY_ID: request_id,
        MCP.KEY_METHOD: MCP.METHOD_TOOLS_CALL,
        MCP.KEY_PARAMS: {
            MCP.KEY_ARGUMENTS: {},
            MCP.KEY_META: {
                "io.modelcontextprotocol/clientCapabilities": {},
                "io.modelcontextprotocol/protocolVersion": MCP.MCP_PROTOCOL_VERSION,
            },
            MCP.KEY_NAME: tool_name,
        },
        "jsonrpc": MCP.JSON_RPC_VERSION,
    }
    status, _, response = MCP.request(
        arguments,
        MCP.HTTP_METHOD_POST,
        message,
        mcp_headers(session_id, password),
    )
    MCP.require_status(status, MCP.HTTP_STATUS_OK)
    assert isinstance(response, dict)
    assert response[MCP.KEY_ID] == request_id
    result = response.get(MCP.KEY_RESULT)
    assert isinstance(result, dict)
    return MCP.require_tool_result(result)


def mcp_delete_session(
    arguments: argparse.Namespace,
    session_id: str,
    password: str | None = None,
) -> None:
    status, _, response = MCP.request(
        arguments,
        MCP.HTTP_METHOD_DELETE,
        headers=mcp_headers(session_id, password),
    )
    MCP.require_status(status, MCP.HTTP_STATUS_NO_CONTENT)
    assert response is None


@contextmanager
def connected_mcp(password: str | None = None) -> Generator[tuple[argparse.Namespace, str], None, None]:
    arguments = mcp_arguments()
    session_id = mcp_initialize(arguments, password)
    mcp_initialized(arguments, session_id, password)
    try:
        yield arguments, session_id
    finally:
        mcp_delete_session(arguments, session_id, password)


@contextmanager
def connected_bearer_websocket(password: str) -> Generator[socket.socket, None, None]:
    connection = socket.create_connection((AUTOMATION_HOST, AUTOMATION_PORT), timeout=10)
    connection.settimeout(10)
    try:
        AUTOMATION.perform_handshake(
            connection,
            AUTOMATION_HOST,
            AUTOMATION_PORT,
            bearer_authorization(password),
        )
        AUTOMATION.hello(connection)
        yield connection
    finally:
        connection.close()


def websocket_upgrade_response(password: str | None = None) -> tuple[str, dict[str, str]]:
    request_lines = [
        f"GET {AUTOMATION.AUTOMATION_PATH} HTTP/1.1",
        f"Host: {AUTOMATION_HOST}:{AUTOMATION_PORT}",
        "Upgrade: websocket",
        "Connection: Upgrade",
        f"Sec-WebSocket-Key: {WEBSOCKET_HANDSHAKE_KEY}",
        "Sec-WebSocket-Version: 13",
    ]
    if password is not None:
        request_lines.append(f"{AUTHORIZATION_HEADER}: {bearer_authorization(password)}")
    with socket.create_connection((AUTOMATION_HOST, AUTOMATION_PORT), timeout=10) as connection:
        connection.settimeout(10)
        connection.sendall("\r\n".join((*request_lines, "", "")).encode("ascii"))
        response = bytearray()
        while not response.endswith(WEBSOCKET_RESPONSE_TERMINATOR):
            assert len(response) < WEBSOCKET_RESPONSE_MAXIMUM_BYTES
            response.extend(AUTOMATION.receive_exact(connection, 1))
    lines = response.decode("ascii").split("\r\n")
    headers = {
        name.strip().lower(): value.strip()
        for line in lines[1:]
        if line and ":" in line
        for name, value in (line.split(":", maxsplit=1),)
    }
    return lines[0], headers


def assert_remote_auth_status(
    status: dict[str, Any],
    configured: bool,
    enabled: bool,
    state: str,
) -> None:
    assert set(status) == REMOTE_AUTH_STATUS_KEYS
    assert status["configured"] is configured
    assert status["enabled"] is enabled
    assert status["valid"] is True
    assert status["state"] == state
    assert isinstance(status["file"], str)
    assert status["file"].endswith(REMOTE_AUTH_ARTIFACT_FILE)


def open_remote_auth_settings(connection: socket.socket) -> None:
    open_page_menu_on_selected_page()
    wait_for_snapshot_node(connection, SEMANTIC_PAGE_MENU_SETTINGS)
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=SEMANTIC_PAGE_MENU_SETTINGS)
    wait_for_snapshot_node(connection, SEMANTIC_SETTINGS_AUTOMATION)
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=SEMANTIC_SETTINGS_AUTOMATION)
    wait_for_snapshot_node(connection, SEMANTIC_SETTINGS_REMOTE_AUTH)
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=SEMANTIC_SETTINGS_REMOTE_AUTH)
    wait_for_snapshot_node(connection, SEMANTIC_REMOTE_AUTH_SET_PASSWORD)


def set_phone_password(connection: socket.socket, password: str) -> None:
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_SET_TEXT,
        semanticId=SEMANTIC_REMOTE_AUTH_PASSWORD,
        text=password,
    )
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_SET_TEXT,
        semanticId=SEMANTIC_REMOTE_AUTH_PASSWORD_CONFIRMATION,
        text=password,
    )
    AUTOMATION.request(
        connection,
        AUTOMATION.TYPE_TAP,
        semanticId=SEMANTIC_REMOTE_AUTH_SET_PASSWORD,
    )


def clear_phone_password(connection: socket.socket) -> None:
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=SEMANTIC_REMOTE_AUTH_CLEAR)


def test_phone_managed_remote_bearer_auth_controls_websocket_and_mcp(
    websocket_control: socket.socket,
) -> None:
    first_password = fresh_bearer_password()
    replacement_password = fresh_bearer_password()
    current_password = first_password
    is_configured = False
    try:
        initial_status = AUTOMATION.request(websocket_control, TYPE_REMOTE_AUTH_STATUS)
        assert_remote_auth_status(
            initial_status,
            configured=False,
            enabled=False,
            state=REMOTE_AUTH_STATUS_UNCONFIGURED,
        )
        open_remote_auth_settings(websocket_control)
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / REMOTE_AUTH_ARTIFACT_NAME,
        )
        assert (ARTIFACT_DIRECTORY / REMOTE_AUTH_ARTIFACT_NAME).stat().st_size > 0
        set_phone_password(websocket_control, first_password)
        is_configured = True

        status_line, headers = websocket_upgrade_response()
        assert status_line == "HTTP/1.1 401 Unauthorized"
        assert headers.get("www-authenticate") == AUTHORIZATION_WWW_CHALLENGE

        mcp_status, mcp_response_headers, _ = MCP.request(
            mcp_arguments(),
            MCP.HTTP_METHOD_POST,
            {
                MCP.KEY_ID: 1,
                MCP.KEY_METHOD: MCP.METHOD_INITIALIZE,
                MCP.KEY_PARAMS: {},
                "jsonrpc": MCP.JSON_RPC_VERSION,
            },
            mcp_headers(),
        )
        assert mcp_status == HTTP_STATUS_UNAUTHORIZED
        assert mcp_response_headers.get("www-authenticate") == AUTHORIZATION_WWW_CHALLENGE

        run_device_operation(
            "file-pull",
            DEVICE_FILE=REMOTE_AUTH_FILE,
            ARTIFACT_FILE=REMOTE_AUTH_ARTIFACT_FILE,
        )
        record = json.loads((ARTIFACT_DIRECTORY / REMOTE_AUTH_ARTIFACT_FILE).read_text())
        assert set(record) == REMOTE_AUTH_RECORD_KEYS
        assert record["version"] == REMOTE_AUTH_VERSION
        assert record["algorithm"] == "bcrypt"
        assert record["cost"] == 12
        assert record["enabled"] is True
        assert record["verifier"].startswith("$2a$12$")

        with connected_bearer_websocket(first_password) as connection:
            assert_remote_auth_status(
                AUTOMATION.request(connection, TYPE_REMOTE_AUTH_STATUS),
                configured=True,
                enabled=True,
                state=REMOTE_AUTH_STATUS_ENABLED,
            )

        with connected_mcp(first_password) as (arguments, session_id):
            assert_remote_auth_status(
                mcp_tool(arguments, session_id, 2, TOOL_REMOTE_AUTH_STATUS, first_password),
                configured=True,
                enabled=True,
                state=REMOTE_AUTH_STATUS_ENABLED,
            )
            assert_remote_auth_status(
                mcp_tool(arguments, session_id, 3, TOOL_REMOTE_AUTH_DISABLE, first_password),
                configured=True,
                enabled=False,
                state=REMOTE_AUTH_STATUS_DISABLED,
            )

        status_line, _ = websocket_upgrade_response()
        assert status_line == "HTTP/1.1 101 Switching Protocols"
        with socket.create_connection((AUTOMATION_HOST, AUTOMATION_PORT), timeout=10) as connection:
            connection.settimeout(10)
            AUTOMATION.perform_handshake(connection, AUTOMATION_HOST, AUTOMATION_PORT)
            AUTOMATION.hello(connection)
            error = AUTOMATION.request_error_with_id(connection, TYPE_REMOTE_AUTH_ENABLE)[1]
            assert error[AUTOMATION.KEY_CODE] == "remote_auth_required"

        with connected_bearer_websocket(first_password) as connection:
            assert_remote_auth_status(
                AUTOMATION.request(connection, TYPE_REMOTE_AUTH_ENABLE),
                configured=True,
                enabled=True,
                state=REMOTE_AUTH_STATUS_ENABLED,
            )
            wait_for_snapshot_node(connection, SEMANTIC_REMOTE_AUTH_SET_PASSWORD)
            set_phone_password(connection, replacement_password)
        current_password = replacement_password

        old_status_line, _ = websocket_upgrade_response(first_password)
        assert old_status_line == "HTTP/1.1 401 Unauthorized"
        with connected_bearer_websocket(replacement_password) as connection:
            assert_remote_auth_status(
                AUTOMATION.request(connection, TYPE_REMOTE_AUTH_STATUS),
                configured=True,
                enabled=True,
                state=REMOTE_AUTH_STATUS_ENABLED,
            )
            clear_phone_password(connection)
        is_configured = False

        with connected_mcp() as (arguments, session_id):
            assert_remote_auth_status(
                mcp_tool(arguments, session_id, 4, TOOL_REMOTE_AUTH_STATUS),
                configured=False,
                enabled=False,
                state=REMOTE_AUTH_STATUS_UNCONFIGURED,
            )
    finally:
        if is_configured:
            with connected_bearer_websocket(current_password) as connection:
                clear_phone_password(connection)
