#!/usr/bin/env python3

"""Call any documented Dikciz local WebSocket or MCP control-plane command."""

from __future__ import annotations

import argparse
import base64
import binascii
import hashlib
import http.client
import json
import logging
import logging.handlers
import os
import secrets
import socket
import time
import struct
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

DEFAULT_HOST = "127.0.0.1"
DEFAULT_EVENT_IDLE_TIMEOUT_SECONDS = 0.0
DEFAULT_BEARER_TOKEN_ENV = "DIKCIZ_REMOTE_BEARER_TOKEN"
DEFAULT_MCP_PORT = 19_002
DEFAULT_TIMEOUT_SECONDS = 10.0
DEFAULT_WEBSOCKET_PORT = 19_001
FRAME_FIN_BIT = 0x80
FRAME_MASK_BIT = 0x80
FRAME_OPCODE_CLOSE = 0x8
FRAME_OPCODE_MASK = 0x0F
FRAME_OPCODE_PING = 0x9
FRAME_OPCODE_PONG = 0xA
FRAME_OPCODE_TEXT = 0x1
HANDSHAKE_MAXIMUM_BYTES = 8_192
HANDSHAKE_TERMINATOR = b"\r\n\r\n"
HEADER_ACCEPT = "Accept"
HEADER_AUTHORIZATION = "Authorization"
HEADER_CONTENT_TYPE = "Content-Type"
HEADER_MCP_PROTOCOL_VERSION = "MCP-Protocol-Version"
HEADER_MCP_SESSION_ID = "MCP-Session-Id"
HEADER_ORIGIN = "Origin"
HTTP_METHOD_POST = "POST"
HTTP_STATUS_ACCEPTED = 202
HTTP_STATUS_OK = 200
JSON_RPC_VERSION = "2.0"
KEY_ARGUMENTS = "arguments"
KEY_CONTENT = "content"
KEY_DATA = "data"
KEY_ID = "id"
KEY_IS_ERROR = "isError"
KEY_METHOD = "method"
KEY_MIME_TYPE = "mimeType"
KEY_NAME = "name"
KEY_PARAMS = "params"
KEY_PNG_BASE64 = "pngBase64"
KEY_PROTOCOL_VERSION = "protocolVersion"
KEY_REQUEST_ID = "requestId"
KEY_RESULT = "result"
KEY_TYPE = "type"
MAXIMUM_FRAME_BYTES = 8 * 1024 * 1024
MAXIMUM_HTTP_RESPONSE_BYTES = 16 * 1024 * 1024
# A request is answered while the control plane keeps streaming events, so
# the wait is bounded by time rather than by how many unrelated events
# happen to arrive first.
RESPONSE_TIMEOUT_SECONDS = 30
MCP_PATH = "/mcp"
MCP_PROTOCOL_VERSION = "2026-07-28"
METHOD_INITIALIZE = "initialize"
METHOD_INITIALIZED = "notifications/initialized"
METHOD_TOOLS_CALL = "tools/call"
METHOD_TOOLS_LIST = "tools/list"
MIME_TYPE_EVENT_STREAM = "text/event-stream"
MIME_TYPE_JSON = "application/json"
MIME_TYPE_PNG = "image/png"
ORIGIN_LOCAL = "http://127.0.0.1"
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
PROTOCOL_VERSION = 1
RESERVED_WEBSOCKET_ARGUMENT_KEYS = frozenset((KEY_REQUEST_ID, KEY_TYPE))
TYPE_ERROR = "error"
TYPE_EVENT = "event"
TYPE_HELLO = "hello"
TYPE_RESULT = "result"
WEBSOCKET_ACCEPT_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
WEBSOCKET_PATH = "/v1/automation"

EXIT_CLIENT_FAILURE = 1
EXIT_REMOTE_REJECTION = 2
EXIT_SUCCESS = 0

LOGGER = logging.getLogger(__name__)
DEFAULT_LOG_PATH = Path(__file__).resolve().parents[1] / ".android-lab" / "dikciz-control.log"


class DikcizControlError(RuntimeError):
    """The local Dikciz control plane could not complete a client request."""


class RemoteControlError(DikcizControlError):
    """Dikciz returned a structured rejected or failed command response."""

    def __init__(self, response: dict[str, Any]) -> None:
        super().__init__("Dikciz rejected the control request")
        self.response = response


class JsonLogFormatter(logging.Formatter):
    """Render only fixed, non-secret client diagnostics as JSON Lines."""

    def format(self, record: logging.LogRecord) -> str:
        timestamp = datetime.fromtimestamp(record.created, timezone.utc).isoformat(
            timespec="milliseconds",
        )
        payload: dict[str, Any] = {
            "time": timestamp.replace("+00:00", "Z"),
            "level": record.levelname.lower(),
            "file": record.filename,
            "line": record.lineno,
            "func": record.funcName,
            "msg": record.getMessage(),
        }
        for field in ("operation", "error_type"):
            value = getattr(record, field, None)
            if value is not None:
                payload[field] = value
        return json.dumps(payload, separators=(",", ":"), ensure_ascii=False)


def configure_logging(log_path: Path) -> None:
    log_path.parent.mkdir(parents=True, exist_ok=True)
    formatter = JsonLogFormatter()
    stderr_handler = logging.StreamHandler(sys.stderr)
    file_handler = logging.handlers.RotatingFileHandler(
        log_path,
        maxBytes=1_048_576,
        backupCount=3,
        encoding="utf-8",
    )
    for handler in (stderr_handler, file_handler):
        handler.setFormatter(formatter)
    logging.basicConfig(level=logging.INFO, handlers=(stderr_handler, file_handler))


def parse_positive_timeout(value: str) -> float:
    try:
        timeout = float(value)
    except ValueError as exception:
        raise argparse.ArgumentTypeError("timeout must be a positive number") from exception
    if timeout <= 0:
        raise argparse.ArgumentTypeError("timeout must be a positive number")
    return timeout


def parse_nonnegative_timeout(value: str) -> float:
    try:
        timeout = float(value)
    except ValueError as exception:
        raise argparse.ArgumentTypeError("timeout must be a non-negative number") from exception
    if timeout < 0:
        raise argparse.ArgumentTypeError("timeout must be a non-negative number")
    return timeout


def parse_nonnegative_count(value: str) -> int:
    try:
        count = int(value)
    except ValueError as exception:
        raise argparse.ArgumentTypeError("count must be a non-negative integer") from exception
    if count < 0:
        raise argparse.ArgumentTypeError("count must be a non-negative integer")
    return count


def parse_port(value: str) -> int:
    try:
        port = int(value)
    except ValueError as exception:
        raise argparse.ArgumentTypeError("port must be an integer from 1 through 65535") from exception
    if not 1 <= port <= 65_535:
        raise argparse.ArgumentTypeError("port must be an integer from 1 through 65535")
    return port


def parse_json_object(value: str) -> dict[str, Any]:
    if value.startswith("@"):
        path = Path(value[1:])
        try:
            raw = path.read_bytes()
        except OSError as exception:
            raise DikcizControlError("could not read JSON arguments file") from exception
    else:
        raw = value.encode("utf-8")
    if not raw or len(raw) > MAXIMUM_FRAME_BYTES:
        raise DikcizControlError("JSON arguments exceed the supported size")
    try:
        decoded = raw.decode("utf-8")
        parsed = json.loads(decoded)
    except (UnicodeDecodeError, json.JSONDecodeError) as exception:
        raise DikcizControlError("JSON arguments must be a UTF-8 object") from exception
    if not isinstance(parsed, dict):
        raise DikcizControlError("JSON arguments must be an object")
    return parsed


def reject_websocket_envelope_arguments(command_arguments: dict[str, Any]) -> None:
    if RESERVED_WEBSOCKET_ARGUMENT_KEYS.isdisjoint(command_arguments):
        return
    raise DikcizControlError("JSON arguments cannot override WebSocket envelope fields")


def emit_json(value: Any, pretty: bool) -> None:
    if pretty:
        sys.stdout.write(json.dumps(value, indent=2, ensure_ascii=False))
    else:
        sys.stdout.write(json.dumps(value, separators=(",", ":"), ensure_ascii=False))
    sys.stdout.write("\n")


def receive_exact(connection: socket.socket, size: int) -> bytes:
    chunks: list[bytes] = []
    remaining = size
    while remaining:
        chunk = connection.recv(remaining)
        if not chunk:
            raise DikcizControlError("Dikciz closed the WebSocket early")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)


def remote_authorization(arguments: argparse.Namespace) -> str | None:
    token = os.environ.get(arguments.bearer_token_env)
    if token is None:
        return None
    if not token or "\r" in token or "\n" in token:
        raise DikcizControlError("remote bearer token environment value is invalid")
    return f"Bearer {token}"


def perform_websocket_handshake(
    connection: socket.socket,
    host: str,
    port: int,
    authorization: str | None,
) -> None:
    key = base64.b64encode(secrets.token_bytes(16)).decode("ascii")
    request_lines = [
        f"GET {WEBSOCKET_PATH} HTTP/1.1",
        f"Host: {host}:{port}",
        "Upgrade: websocket",
        "Connection: Upgrade",
        f"Sec-WebSocket-Key: {key}",
        "Sec-WebSocket-Version: 13",
    ]
    if authorization is not None:
        request_lines.append(f"{HEADER_AUTHORIZATION}: {authorization}")
    request = "\r\n".join((*request_lines, "", ""))
    connection.sendall(request.encode("ascii"))
    response = bytearray()
    while not response.endswith(HANDSHAKE_TERMINATOR):
        if len(response) >= HANDSHAKE_MAXIMUM_BYTES:
            raise DikcizControlError("Dikciz WebSocket handshake is oversized")
        response.extend(receive_exact(connection, 1))
    try:
        lines = response.decode("ascii").split("\r\n")
    except UnicodeDecodeError as exception:
        raise DikcizControlError("Dikciz WebSocket handshake is not ASCII") from exception
    if lines[0] != "HTTP/1.1 101 Switching Protocols":
        raise DikcizControlError("Dikciz did not accept the WebSocket upgrade")
    headers = {
        name.strip().lower(): header_value.strip()
        for line in lines[1:]
        if line and ":" in line
        for name, header_value in (line.split(":", maxsplit=1),)
    }
    expected_accept = base64.b64encode(
        hashlib.sha1(f"{key}{WEBSOCKET_ACCEPT_GUID}".encode("ascii")).digest(),
    ).decode("ascii")
    if headers.get("sec-websocket-accept") != expected_accept:
        raise DikcizControlError("Dikciz WebSocket returned an invalid accept key")


def send_websocket_frame(connection: socket.socket, opcode: int, payload: bytes) -> None:
    payload_size = len(payload)
    if payload_size > MAXIMUM_FRAME_BYTES:
        raise DikcizControlError("WebSocket request exceeds the supported size")
    mask = secrets.token_bytes(4)
    header = bytearray((FRAME_FIN_BIT | opcode,))
    if payload_size <= 125:
        header.append(FRAME_MASK_BIT | payload_size)
    elif payload_size <= 65_535:
        header.extend((FRAME_MASK_BIT | 126,))
        header.extend(struct.pack("!H", payload_size))
    else:
        header.extend((FRAME_MASK_BIT | 127,))
        header.extend(struct.pack("!Q", payload_size))
    masked_payload = bytes(
        byte ^ mask[index % len(mask)] for index, byte in enumerate(payload)
    )
    connection.sendall(bytes(header) + mask + masked_payload)


def receive_websocket_frame(connection: socket.socket) -> tuple[int, bytes]:
    first_byte, second_byte = receive_exact(connection, 2)
    if first_byte & FRAME_FIN_BIT == 0 or second_byte & FRAME_MASK_BIT:
        raise DikcizControlError("Dikciz sent an invalid WebSocket frame")
    payload_size = second_byte & 0x7F
    if payload_size == 126:
        payload_size = struct.unpack("!H", receive_exact(connection, 2))[0]
    elif payload_size == 127:
        payload_size = struct.unpack("!Q", receive_exact(connection, 8))[0]
    if payload_size > MAXIMUM_FRAME_BYTES:
        raise DikcizControlError("Dikciz sent an oversized WebSocket frame")
    return first_byte & FRAME_OPCODE_MASK, receive_exact(connection, payload_size)


def receive_websocket_json(connection: socket.socket) -> dict[str, Any]:
    while True:
        opcode, payload = receive_websocket_frame(connection)
        if opcode == FRAME_OPCODE_PING:
            send_websocket_frame(connection, FRAME_OPCODE_PONG, payload)
            continue
        if opcode == FRAME_OPCODE_CLOSE:
            raise DikcizControlError("Dikciz closed the WebSocket")
        if opcode != FRAME_OPCODE_TEXT:
            raise DikcizControlError("Dikciz sent an unexpected WebSocket frame")
        try:
            response = json.loads(payload.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exception:
            raise DikcizControlError("Dikciz WebSocket response is not JSON") from exception
        if not isinstance(response, dict):
            raise DikcizControlError("Dikciz WebSocket response is not an object")
        return response


def send_websocket_json(connection: socket.socket, message: dict[str, Any]) -> None:
    encoded = json.dumps(message, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    send_websocket_frame(connection, FRAME_OPCODE_TEXT, encoded)


def receive_websocket_response(connection: socket.socket, request_id: str) -> dict[str, Any]:
    deadline = time.monotonic() + RESPONSE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        response = receive_websocket_json(connection)
        if response.get(KEY_TYPE) == TYPE_EVENT:
            continue
        if response.get(KEY_REQUEST_ID) != request_id:
            raise DikcizControlError("Dikciz WebSocket response has an unexpected request ID")
        return response
    raise DikcizControlError("Dikciz did not return a response in time")


def start_websocket_session(arguments: argparse.Namespace) -> socket.socket:
    try:
        connection = socket.create_connection(
            (arguments.host, arguments.port),
            timeout=arguments.timeout_seconds,
        )
        connection.settimeout(arguments.timeout_seconds)
    except OSError as exception:
        raise DikcizControlError("could not connect to the Dikciz WebSocket") from exception
    try:
        perform_websocket_handshake(
            connection,
            arguments.host,
            arguments.port,
            remote_authorization(arguments),
        )
        request_id = str(uuid.uuid4())
        send_websocket_json(
            connection,
            {
                KEY_REQUEST_ID: request_id,
                KEY_TYPE: TYPE_HELLO,
                KEY_PROTOCOL_VERSION: PROTOCOL_VERSION,
            },
        )
        response = receive_websocket_response(connection, request_id)
        if response.get(KEY_TYPE) != TYPE_HELLO:
            raise RemoteControlError(response)
        return connection
    except (DikcizControlError, OSError):
        connection.close()
        raise


def call_websocket(arguments: argparse.Namespace) -> dict[str, Any]:
    command_arguments = parse_json_object(arguments.arguments)
    reject_websocket_envelope_arguments(command_arguments)
    with start_websocket_session(arguments) as connection:
        request_id = str(uuid.uuid4())
        message = {KEY_REQUEST_ID: request_id, KEY_TYPE: arguments.command_type}
        message.update(command_arguments)
        send_websocket_json(connection, message)
        response = receive_websocket_response(connection, request_id)
    if response.get(KEY_TYPE) == TYPE_ERROR:
        raise RemoteControlError(response)
    if response.get(KEY_TYPE) != TYPE_RESULT:
        raise DikcizControlError("Dikciz returned an invalid WebSocket command response")
    return response


def watch_websocket_events(arguments: argparse.Namespace) -> int:
    emitted_count = 0
    with start_websocket_session(arguments) as connection:
        connection.settimeout(arguments.idle_timeout_seconds or None)
        while arguments.count == 0 or emitted_count < arguments.count:
            response = receive_websocket_json(connection)
            if response.get(KEY_TYPE) != TYPE_EVENT:
                continue
            emit_json(response, pretty=False)
            emitted_count += 1
    return 0


def encode_json(value: dict[str, Any]) -> bytes:
    encoded = json.dumps(value, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    if len(encoded) > MAXIMUM_FRAME_BYTES:
        raise DikcizControlError("MCP request exceeds the supported size")
    return encoded


def mcp_headers(
    session_id: str | None = None,
    authorization: str | None = None,
) -> dict[str, str]:
    headers = {
        HEADER_ACCEPT: f"{MIME_TYPE_JSON}, {MIME_TYPE_EVENT_STREAM}",
        HEADER_CONTENT_TYPE: MIME_TYPE_JSON,
        HEADER_ORIGIN: ORIGIN_LOCAL,
    }
    if session_id is not None:
        headers[HEADER_MCP_PROTOCOL_VERSION] = MCP_PROTOCOL_VERSION
        headers[HEADER_MCP_SESSION_ID] = session_id
    if authorization is not None:
        headers[HEADER_AUTHORIZATION] = authorization
    return headers


def mcp_request(
    arguments: argparse.Namespace,
    message: dict[str, Any],
    headers: dict[str, str],
    expected_status: int,
) -> tuple[dict[str, str], dict[str, Any] | None]:
    connection = http.client.HTTPConnection(
        arguments.host,
        arguments.port,
        timeout=arguments.timeout_seconds,
    )
    try:
        connection.request(
            HTTP_METHOD_POST,
            MCP_PATH,
            body=encode_json(message),
            headers=headers,
        )
        response = connection.getresponse()
        raw_body = response.read(MAXIMUM_HTTP_RESPONSE_BYTES + 1)
        response_headers = {name.lower(): value for name, value in response.getheaders()}
    except (OSError, http.client.HTTPException) as exception:
        raise DikcizControlError("could not complete the Dikciz MCP request") from exception
    finally:
        connection.close()
    if len(raw_body) > MAXIMUM_HTTP_RESPONSE_BYTES:
        raise DikcizControlError("Dikciz MCP response exceeds the supported size")
    if response.status != expected_status:
        raise DikcizControlError("Dikciz MCP returned an unexpected HTTP status")
    if not raw_body:
        return response_headers, None
    try:
        body = json.loads(raw_body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exception:
        raise DikcizControlError("Dikciz MCP response is not JSON") from exception
    if not isinstance(body, dict):
        raise DikcizControlError("Dikciz MCP response is not an object")
    return response_headers, body


def require_mcp_result(response: dict[str, Any], request_id: int) -> dict[str, Any]:
    if response.get(KEY_ID) != request_id:
        raise DikcizControlError("Dikciz MCP response has an unexpected request ID")
    if "error" in response:
        raise RemoteControlError(response)
    result = response.get(KEY_RESULT)
    if not isinstance(result, dict):
        raise DikcizControlError("Dikciz MCP response has no result object")
    if result.get(KEY_IS_ERROR) is True:
        raise RemoteControlError(result)
    return result


def start_mcp_session(arguments: argparse.Namespace) -> str:
    authorization = remote_authorization(arguments)
    request_id = 1
    initialize = {
        KEY_ID: request_id,
        KEY_METHOD: METHOD_INITIALIZE,
        KEY_PARAMS: {
            "capabilities": {},
            "clientInfo": {KEY_NAME: "dikciz-control", "version": "1"},
            KEY_PROTOCOL_VERSION: MCP_PROTOCOL_VERSION,
        },
        "jsonrpc": JSON_RPC_VERSION,
    }
    response_headers, response = mcp_request(
        arguments,
        initialize,
        mcp_headers(authorization=authorization),
        HTTP_STATUS_OK,
    )
    if response is None:
        raise DikcizControlError("Dikciz MCP initialization returned no response")
    result = require_mcp_result(response, request_id)
    if result.get(KEY_PROTOCOL_VERSION) != MCP_PROTOCOL_VERSION:
        raise DikcizControlError("Dikciz MCP did not negotiate the requested protocol")
    session_id = response_headers.get(HEADER_MCP_SESSION_ID.lower())
    if not session_id:
        raise DikcizControlError("Dikciz MCP did not return a session ID")
    initialized = {
        KEY_METHOD: METHOD_INITIALIZED,
        KEY_PARAMS: {},
        "jsonrpc": JSON_RPC_VERSION,
    }
    _, initialized_response = mcp_request(
        arguments,
        initialized,
        mcp_headers(session_id, authorization),
        HTTP_STATUS_ACCEPTED,
    )
    if initialized_response is not None:
        raise DikcizControlError("Dikciz MCP initialization returned an unexpected body")
    return session_id


def call_mcp(arguments: argparse.Namespace, method: str, params: dict[str, Any]) -> dict[str, Any]:
    session_id = start_mcp_session(arguments)
    authorization = remote_authorization(arguments)
    request_id = 2
    request_params = dict(params)
    request_params["_meta"] = {
        "io.modelcontextprotocol/clientCapabilities": {},
        "io.modelcontextprotocol/protocolVersion": MCP_PROTOCOL_VERSION,
    }
    request = {
        KEY_ID: request_id,
        KEY_METHOD: method,
        KEY_PARAMS: request_params,
        "jsonrpc": JSON_RPC_VERSION,
    }
    _, response = mcp_request(
        arguments,
        request,
        mcp_headers(session_id, authorization),
        HTTP_STATUS_OK,
    )
    if response is None:
        raise DikcizControlError("Dikciz MCP request returned no response")
    return require_mcp_result(response, request_id)


def call_mcp_tool(arguments: argparse.Namespace) -> dict[str, Any]:
    tool_arguments = parse_json_object(arguments.arguments)
    return call_mcp(
        arguments,
        METHOD_TOOLS_CALL,
        {KEY_NAME: arguments.tool, KEY_ARGUMENTS: tool_arguments},
    )


def list_mcp_tools(arguments: argparse.Namespace) -> dict[str, Any]:
    return call_mcp(arguments, METHOD_TOOLS_LIST, {})


def png_base64_from_websocket(response: dict[str, Any]) -> str:
    result = response.get(KEY_RESULT)
    if not isinstance(result, dict):
        raise DikcizControlError("WebSocket screenshot response has no result")
    png_base64 = result.get(KEY_PNG_BASE64)
    if not isinstance(png_base64, str):
        raise DikcizControlError("WebSocket response has no PNG image")
    return png_base64


def png_base64_from_mcp(response: dict[str, Any]) -> str:
    content = response.get(KEY_CONTENT)
    if not isinstance(content, list):
        raise DikcizControlError("MCP screenshot response has no image content")
    for item in content:
        if not isinstance(item, dict):
            continue
        if item.get(KEY_MIME_TYPE) != MIME_TYPE_PNG:
            continue
        png_base64 = item.get(KEY_DATA)
        if isinstance(png_base64, str):
            return png_base64
    raise DikcizControlError("MCP response has no PNG image")


def write_png(png_base64: str, path: Path, overwrite: bool) -> None:
    try:
        image = base64.b64decode(png_base64, validate=True)
    except binascii.Error as exception:
        raise DikcizControlError("Dikciz returned an invalid PNG encoding") from exception
    if not image.startswith(PNG_SIGNATURE):
        raise DikcizControlError("Dikciz response is not a PNG image")
    path.parent.mkdir(parents=True, exist_ok=True)
    mode = "wb" if overwrite else "xb"
    try:
        with path.open(mode) as output:
            output.write(image)
    except FileExistsError as exception:
        raise DikcizControlError("PNG output already exists. Pass --overwrite to replace it") from exception
    except OSError as exception:
        raise DikcizControlError("could not write PNG output") from exception


def maybe_write_png(arguments: argparse.Namespace, response: dict[str, Any]) -> None:
    png_path = arguments.png_file
    if png_path is None:
        return
    if arguments.operation == "websocket":
        png_base64 = png_base64_from_websocket(response)
    else:
        png_base64 = png_base64_from_mcp(response)
    write_png(png_base64, png_path, arguments.overwrite)


def add_connection_arguments(
    parser: argparse.ArgumentParser,
    default_port: int,
) -> None:
    parser.add_argument("--host", default=DEFAULT_HOST)
    parser.add_argument("--port", default=default_port, type=parse_port)
    parser.add_argument(
        "--timeout-seconds",
        default=DEFAULT_TIMEOUT_SECONDS,
        type=parse_positive_timeout,
    )
    parser.add_argument("--log-file", default=DEFAULT_LOG_PATH, type=Path)
    parser.add_argument(
        "--bearer-token-env",
        default=DEFAULT_BEARER_TOKEN_ENV,
        help="environment variable containing the phone-configured remote bearer password",
    )


def add_png_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--png-file", type=Path)
    parser.add_argument("--overwrite", action="store_true")


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="operation", required=True)

    websocket = subparsers.add_parser("websocket", help="send one WebSocket command")
    websocket.add_argument("command_type")
    websocket.add_argument("--arguments", default="{}")
    websocket.add_argument("--pretty", action="store_true")
    add_png_arguments(websocket)
    add_connection_arguments(websocket, DEFAULT_WEBSOCKET_PORT)

    events = subparsers.add_parser("events", help="stream persistent WebSocket events as JSON Lines")
    events.add_argument("--count", default=0, type=parse_nonnegative_count)
    add_connection_arguments(events, DEFAULT_WEBSOCKET_PORT)
    events.add_argument(
        "--idle-timeout-seconds",
        default=DEFAULT_EVENT_IDLE_TIMEOUT_SECONDS,
        type=parse_nonnegative_timeout,
    )

    mcp = subparsers.add_parser("mcp", help="call one MCP tool")
    mcp.add_argument("tool")
    mcp.add_argument("--arguments", default="{}")
    mcp.add_argument("--pretty", action="store_true")
    add_png_arguments(mcp)
    add_connection_arguments(mcp, DEFAULT_MCP_PORT)

    mcp_tools = subparsers.add_parser("mcp-tools", help="list MCP tools")
    mcp_tools.add_argument("--pretty", action="store_true")
    add_connection_arguments(mcp_tools, DEFAULT_MCP_PORT)

    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    try:
        configure_logging(arguments.log_file)
    except OSError:
        sys.stderr.write("dikciz-control: could not configure diagnostics\n")
        return EXIT_CLIENT_FAILURE
    try:
        if arguments.operation == "websocket":
            response = call_websocket(arguments)
            maybe_write_png(arguments, response)
            emit_json(response, arguments.pretty)
            return EXIT_SUCCESS
        if arguments.operation == "events":
            return watch_websocket_events(arguments)
        if arguments.operation == "mcp":
            response = call_mcp_tool(arguments)
            maybe_write_png(arguments, response)
            emit_json(response, arguments.pretty)
            return EXIT_SUCCESS
        if arguments.operation == "mcp-tools":
            emit_json(list_mcp_tools(arguments), arguments.pretty)
            return EXIT_SUCCESS
        raise DikcizControlError("unknown Dikciz control operation")
    except RemoteControlError as exception:
        LOGGER.error(
            "Dikciz rejected control request",
            extra={"operation": arguments.operation, "error_type": type(exception).__name__},
        )
        emit_json({"error": str(exception), "response": exception.response}, pretty=False)
        return EXIT_REMOTE_REJECTION
    except (DikcizControlError, OSError) as exception:
        LOGGER.error(
            "Dikciz control client failed",
            extra={"operation": arguments.operation, "error_type": type(exception).__name__},
        )
        sys.stderr.write(f"dikciz-control: {exception}\n")
        return EXIT_CLIENT_FAILURE


if __name__ == "__main__":
    raise SystemExit(main())
