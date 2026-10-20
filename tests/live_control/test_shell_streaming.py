"""Live shell output through production WebSocket and SSE transports."""

import base64
import http.client
import json
import socket
import subprocess
import time
import uuid

from . import *
from . import test_remote_bearer_auth as AUTH


CLIENT = load_script("dikciz_stream_client", "scripts/dikciz-control.py")
PROTOCOLS = ("websocket", "mcp", "http")
START = "shellStream"
STOP = "shellStreamStop"
TOOL_START = "dikciz_shell_stream"
TOOL_STOP = "dikciz_shell_stream_stop"
EVENT = "event"
OUTPUT = "outputBase64"
COMPLETE = "complete"
STREAM_ID = "streamId"
WAIT_SECONDS = 15
MARKER_TAG = "DikcizStreamTest"


def client_arguments(protocol):
    return argparse.Namespace(
        host=AUTOMATION_HOST,
        port=AUTOMATION_PORT if protocol == "websocket" else MCP_PORT,
        timeout_seconds=WAIT_SECONDS,
        bearer_token_env="DIKCIZ_STREAM_TEST_UNUSED_TOKEN",
        stream_raw=False,
    )


def shell(command):
    return CLIENT.http_command(client_arguments("http"), "shell", {
        "command": command,
        "root": False,
    })["result"]


class ShellStream:
    def __init__(self, protocol, command, websocket_connection=None, **options):
        self.protocol = protocol
        self.arguments = client_arguments(protocol)
        self.session = None
        self.connection = None
        self.response = None
        self.events = None
        self.id = None
        fields = {"command": command, "root": False, **options}
        self.request_id = str(uuid.uuid4())
        if protocol == "websocket":
            self.connection = websocket_connection or CLIENT.start_websocket_session(self.arguments)
            CLIENT.send_websocket_json(self.connection, {
                "type": START, "requestId": self.request_id, **fields,
            })
        else:
            self.connection = http.client.HTTPConnection(
                self.arguments.host, self.arguments.port, timeout=WAIT_SECONDS,
            )
            if protocol == "mcp":
                self.session = CLIENT.start_mcp_session(self.arguments)
                message = {
                    "jsonrpc": "2.0", "id": self.request_id, "method": "tools/call",
                    "params": {
                        "name": TOOL_START, "arguments": fields,
                        "_meta": {
                            "io.modelcontextprotocol/protocolVersion": CLIENT.MCP_PROTOCOL_VERSION,
                            "io.modelcontextprotocol/clientCapabilities": {},
                            "io.modelcontextprotocol/logLevel": "info",
                        },
                    },
                }
                headers = CLIENT.mcp_headers(self.session, CLIENT.remote_authorization(self.arguments))
                path = CLIENT.MCP_PATH
            else:
                message = {"type": START, "requestId": self.request_id, **fields}
                headers = {"Content-Type": "application/json", "Accept": "text/event-stream"}
                authorization = CLIENT.remote_authorization(self.arguments)
                if authorization is not None:
                    headers["Authorization"] = authorization
                path = CLIENT.HTTP_COMMAND_PATH
            self.connection.request("POST", path, body=CLIENT.encode_json(message), headers=headers)
            self.response = self.connection.getresponse()
            assert self.response.status == 200
            if self.response.getheader("Content-Type", "").split(";", 1)[0] != "text/event-stream":
                raise AssertionError(self.response.read().decode())
            self.events = CLIENT.sse_messages(self.response)
        started = self.next()
        assert started[EVENT] == "started", started
        self.id = started[STREAM_ID]

    def next(self):
        while True:
            document = CLIENT.receive_websocket_json(self.connection) if self.protocol == "websocket" else next(self.events)
            if self.protocol == "mcp":
                if "id" in document:
                    assert document["id"] == self.request_id
                    return document["result"]["structuredContent"]
                return document["params"]["data"]
            if document.get("type") == "error":
                raise AssertionError(document)
            if document.get("requestId") != self.request_id:
                continue
            return document["result"]

    def read_output(self, expected):
        output = bytearray()
        deadline = time.monotonic() + WAIT_SECONDS
        while expected not in output and time.monotonic() < deadline:
            event = self.next()
            assert event[EVENT] != COMPLETE, event
            if OUTPUT in event:
                output.extend(base64.b64decode(event[OUTPUT], validate=True))
        assert expected in output, output
        return bytes(output)

    def complete(self):
        output = bytearray()
        while True:
            event = self.next()
            if OUTPUT in event:
                output.extend(base64.b64decode(event[OUTPUT], validate=True))
            if event[EVENT] == COMPLETE:
                return event, bytes(output)

    def stop(self):
        if self.protocol == "websocket":
            CLIENT.send_websocket_json(self.connection, {
                "type": STOP, "requestId": str(uuid.uuid4()), "streamId": self.id,
            })
            return
        if self.protocol == "http":
            CLIENT.http_command(self.arguments, STOP, {STREAM_ID: self.id})
            return
        request = {
            "jsonrpc": "2.0", "id": str(uuid.uuid4()), "method": "tools/call",
            "params": {
                "name": TOOL_STOP, "arguments": {STREAM_ID: self.id},
                "_meta": {
                    "io.modelcontextprotocol/protocolVersion": CLIENT.MCP_PROTOCOL_VERSION,
                    "io.modelcontextprotocol/clientCapabilities": {},
                },
            },
        }
        _, response = CLIENT.mcp_request(self.arguments, request, CLIENT.mcp_headers(self.session, CLIENT.remote_authorization(self.arguments)), 200)
        assert response["result"]["isError"] is False, response

    def close(self, close_session=True):
        if self.response is not None:
            self.response.close()
        if self.connection is not None:
            self.connection.close()
        if close_session and self.session is not None:
            CLIENT.close_mcp_session(self.arguments, self.session, CLIENT.remote_authorization(self.arguments))
            self.session = None


def wait_dead(pids):
    deadline = time.monotonic() + WAIT_SECONDS
    while time.monotonic() < deadline:
        if all(shell(f"kill -0 {pid}")["exitCode"] != 0 for pid in pids):
            return
        time.sleep(0.1)
    raise AssertionError(f"stream processes survived cleanup: {pids}")


def process_ids(stream):
    output = stream.read_output(b"CHILD:")
    while output.count(b"\n") < 2:
        event = stream.next()
        assert event[EVENT] != COMPLETE, event
        output += base64.b64decode(event.get(OUTPUT, ""))
    lines = output.decode().splitlines()
    return [int(line.split(":", 1)[1]) for line in lines if line.startswith(("PARENT:", "CHILD:"))]


PROCESS_TREE = "printf 'PARENT:%s\n' \"$$\"; sleep 90 & printf 'CHILD:%s\n' \"$!\"; wait"


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_shell_streaming_output_arrives_before_exit(websocket_control, protocol):
    gate = f"/data/user/0/{DIKCIZ_DEBUG_PACKAGE}/cache/stream-gate-{uuid.uuid4().hex}"
    command = f"printf first; while [ ! -e {gate} ]; do sleep 0.1; done; printf second"
    stream = ShellStream(protocol, command)
    try:
        assert stream.read_output(b"first") == b"first"
        assert shell(f"touch {gate}")["exitCode"] == 0
        completed, output = stream.complete()
        assert completed["state"] == "exited", completed
        assert completed["exitCode"] == 0
        assert output == b"second"
        assert completed["outputBytes"] == len(b"firstsecond")
    finally:
        stream.close()
        shell(f"rm -f {gate}")


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_shell_streaming_cancel_kills_process_group(websocket_control, protocol):
    stream = ShellStream(protocol, PROCESS_TREE)
    try:
        pids = process_ids(stream)
        assert len(pids) == 2
        stream.stop()
        completed, _ = stream.complete()
        assert completed["state"] == "cancelled", completed
        wait_dead(pids)
    finally:
        stream.close()


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_shell_streaming_disconnect_kills_process_group(websocket_control, protocol):
    stream = ShellStream(protocol, PROCESS_TREE)
    try:
        pids = process_ids(stream)
        stream.close(close_session=False)
        wait_dead(pids)
    finally:
        stream.close()


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_shell_streaming_timeout_and_nonzero_exit(websocket_control, protocol):
    stream = ShellStream(protocol, "printf partial; sleep 90", timeoutMilliseconds=400)
    try:
        completed, output = stream.complete()
        assert output == b"partial"
        assert completed["state"] == "timed_out", completed
    finally:
        stream.close()
    stream = ShellStream(protocol, "printf stdout; printf stderr >&2; exit 7")
    try:
        completed, output = stream.complete()
        assert output == b"stdoutstderr"
        assert completed["state"] == "exited"
        assert completed["exitCode"] == 7
    finally:
        stream.close()


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_shell_streaming_logcat_continues_until_stopped(websocket_control, protocol):
    stream = ShellStream(protocol, f"logcat -v brief -s {MARKER_TAG}:I '*:S'")
    try:
        for _ in range(2):
            marker = uuid.uuid4().hex
            assert shell(f"log -p i -t {MARKER_TAG} {marker}")["exitCode"] == 0
            stream.read_output(marker.encode())
        stream.stop()
        completed, _ = stream.complete()
        assert completed["state"] == "cancelled"
    finally:
        stream.close()


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_shell_streaming_binary_and_large_output(websocket_control, protocol):
    stream = ShellStream(protocol, "head -c 131073 /dev/zero", chunkBytes=65536)
    try:
        completed, output = stream.complete()
        assert output == bytes(131073)
        assert completed["outputBytes"] == len(output)
        assert completed["state"] == "exited"
    finally:
        stream.close()
    stream = ShellStream(protocol, "printf 'héllo'", chunkBytes=1)
    try:
        completed, output = stream.complete()
        assert output.decode() == "héllo"
        assert completed["outputBytes"] == len(output)
    finally:
        stream.close()


INVALID_OPTIONS = (
    {"command": None}, {"command": ""}, {"command": "   "}, {"command": "\u0000"}, {"command": "x" * 8193},
    {"root": None}, {"root": "false"}, {"timeoutMilliseconds": -1},
    {"timeoutMilliseconds": 2147483648}, {"timeoutMilliseconds": True},
    {"chunkBytes": 0}, {"chunkBytes": 65537}, {"chunkBytes": 1.5},
    {"unexpected": True},
)


def rejected_command(protocol, command, fields):
    args = client_arguments(protocol)
    args.arguments = json.dumps(fields)
    with pytest.raises(CLIENT.RemoteControlError) as rejection:
        if protocol == "websocket":
            args.command_type = command
            CLIENT.call_websocket(args)
        elif protocol == "http":
            CLIENT.http_command(args, command, fields)
        else:
            args.tool = TOOL_START if command == START else TOOL_STOP
            CLIENT.call_mcp_tool(args)
    response = rejection.value.response
    return response.get("structuredContent", response)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_shell_streaming_rejects_invalid_arguments(websocket_control, protocol):
    for options in INVALID_OPTIONS:
        error = rejected_command(protocol, START, {"command": "printf unused", "root": False, **options})
        assert error["code"] == "validation_failed", error
    for stream_id in (None, "", "invalid", str(uuid.uuid4())):
        error = rejected_command(protocol, STOP, {STREAM_ID: stream_id})
        expected = "shell_stream_not_found" if isinstance(stream_id, str) and len(stream_id) == 36 else "validation_failed"
        assert error["code"] == expected, error


@pytest.mark.parametrize("protocol", ("websocket", "mcp"))
def test_shell_streaming_other_client_cannot_stop_stream(websocket_control, protocol):
    stream = ShellStream(protocol, PROCESS_TREE)
    try:
        pids = process_ids(stream)
        error = rejected_command(protocol, STOP, {STREAM_ID: stream.id})
        assert error["code"] == "shell_stream_not_found", error
        assert all(shell(f"kill -0 {pid}")["exitCode"] == 0 for pid in pids)
        stream.stop()
        completed, _ = stream.complete()
        assert completed["state"] == "cancelled"
        wait_dead(pids)
    finally:
        stream.close()


@pytest.mark.parametrize("protocol", ("websocket", "mcp"))
def test_shell_streaming_capacity_is_bounded(websocket_control, protocol):
    streams = []
    shared_connection = CLIENT.start_websocket_session(client_arguments(protocol)) if protocol == "websocket" else None
    try:
        for _ in range(4):
            streams.append(ShellStream(protocol, "sleep 90", websocket_connection=shared_connection))
        error = rejected_command(protocol, START, {"command": "sleep 90", "root": False})
        assert error["code"] == "shell_stream_limit", error
        assert shell("printf control-ready")["output"] == "control-ready"
        streams[0].stop()
        completed, _ = streams[0].complete()
        assert completed["state"] == "cancelled", completed
    finally:
        for stream in streams:
            stream.close()
        if shared_connection is not None:
            shared_connection.close()


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_shell_streaming_production_client_raw_bytes(websocket_control, protocol):
    args = client_arguments(protocol)
    command = TOOL_START if protocol == "mcp" else START
    completed = subprocess.run([
        "python3", str(LAB_ROOT / "scripts/dikciz-control.py"), protocol, command,
        "--host", args.host, "--port", str(args.port), "--stream-raw",
        "--arguments", json.dumps({"command": "head -c 131073 /dev/zero", "root": False}),
        "--log-file", "/tmp/dikciz-stream-cli.log",
    ], capture_output=True, timeout=WAIT_SECONDS)
    assert completed.returncode == 0, completed.stderr
    assert completed.stdout == bytes(131073)


@pytest.mark.parametrize("protocol", ("websocket", "mcp"))
def test_shell_streaming_restart_cleans_processes(websocket_control, protocol):
    stream = ShellStream(protocol, PROCESS_TREE)
    try:
        pids = process_ids(stream)
        CLIENT.http_command(client_arguments("http"), "restart", {})
        connection = wait_for_restarted_websocket_control()
        connection.close()
        wait_dead(pids)
    finally:
        stream.close()


@pytest.mark.parametrize("protocol", ("websocket", "mcp"))
def test_shell_streaming_slow_consumer_is_stopped(websocket_control, protocol):
    stream = ShellStream(protocol, "printf 'PARENT:%s\n' \"$$\"; yes stream-data", chunkBytes=65536)
    try:
        output = stream.read_output(b"\n")
        pid = int(output.splitlines()[0].split(b":", 1)[1])
        wait_dead([pid])
    finally:
        stream.close()


def test_shell_streaming_default_continues_beyond_finite_shell_timeout(websocket_control):
    streams = []
    try:
        for protocol in PROTOCOLS:
            streams.append(ShellStream(protocol, f"logcat -v brief -s {MARKER_TAG}:I '*:S'"))
        time.sleep(31)
        for stream in streams:
            marker = uuid.uuid4().hex
            assert shell(f"log -p i -t {MARKER_TAG} {marker}")["exitCode"] == 0
            stream.read_output(marker.encode())
            stream.stop()
            completed, _ = stream.complete()
            assert completed["state"] == "cancelled"
    finally:
        for stream in streams:
            stream.close()


def test_shell_streaming_auth_revocation_stops_streams_and_rejects_origins(websocket_control, monkeypatch):
    streams = []
    configured = False
    password = AUTH.fresh_bearer_password()
    try:
        pids = []
        for protocol in PROTOCOLS:
            stream = ShellStream(protocol, PROCESS_TREE)
            streams.append(stream)
            pids.extend(process_ids(stream))
        AUTH.open_remote_auth_settings(websocket_control)
        AUTH.set_phone_password(websocket_control, password)
        configured = True
        monkeypatch.setenv("DIKCIZ_STREAM_TEST_UNUSED_TOKEN", password)
        wait_dead(pids)
        timings = {}
        for protocol in PROTOCOLS:
            started = time.monotonic()
            authenticated = ShellStream(protocol, "head -c 1048577 /dev/zero", chunkBytes=65536)
            try:
                completed, output = authenticated.complete()
                assert completed["state"] == "exited", completed
                assert len(output) == 1048577
                assert set(output) == {0}
                timings[protocol] = time.monotonic() - started
            finally:
                authenticated.close()
        (ARTIFACT_DIRECTORY / "authenticated-stream-timings.json").write_text(json.dumps(timings))
        for path in (CLIENT.HTTP_COMMAND_PATH, CLIENT.MCP_PATH):
            cases = (
                ({}, 401),
                ({"Authorization": "Bearer EXAMPLE-DO-NOT-USE"}, 401),
                ({"Authorization": AUTH.bearer_authorization(password), "Origin": "https://evil.example"}, 403),
            )
            for extra_headers, expected in cases:
                connection = http.client.HTTPConnection(AUTOMATION_HOST, MCP_PORT, timeout=WAIT_SECONDS)
                try:
                    connection.request("POST", path, body=CLIENT.encode_json({
                        "type": START, "requestId": str(uuid.uuid4()), "command": "printf denied", "root": False,
                    }), headers={"Content-Type": "application/json", **extra_headers})
                    response = connection.getresponse()
                    assert response.status == expected
                    response.read()
                finally:
                    connection.close()
    finally:
        if configured:
            with AUTH.connected_bearer_websocket(password) as connection:
                AUTH.clear_phone_password(connection)
        for stream in streams:
            stream.close()


def test_shell_streaming_mcp_requires_log_opt_in(websocket_control):
    args = client_arguments("mcp")
    session = CLIENT.start_mcp_session(args)
    try:
        for level in (None, "warning", "invalid"):
            meta = {
                "io.modelcontextprotocol/protocolVersion": CLIENT.MCP_PROTOCOL_VERSION,
                "io.modelcontextprotocol/clientCapabilities": {},
            }
            if level is not None:
                meta["io.modelcontextprotocol/logLevel"] = level
            request = {
                "jsonrpc": "2.0", "id": str(uuid.uuid4()), "method": "tools/call",
                "params": {"name": TOOL_START, "arguments": {"command": "printf unused", "root": False}, "_meta": meta},
            }
            _, response = CLIENT.mcp_request(args, request, CLIENT.mcp_headers(session), 200)
            assert response["result"]["isError"] is True, response
            assert response["result"]["structuredContent"]["code"] == "validation_failed"
    finally:
        CLIENT.close_mcp_session(args, session, None)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_shell_streaming_command_stdin_is_eof(websocket_control, protocol):
    stream = ShellStream(protocol, "cat; printf eof")
    try:
        completed, output = stream.complete()
        assert output == b"eof"
        assert completed["state"] == "exited"
        assert completed["exitCode"] == 0
    finally:
        stream.close()


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_shell_streaming_app_death_cleans_processes(websocket_control, protocol):
    stream = ShellStream(protocol, PROCESS_TREE)
    try:
        pids = process_ids(stream)
        run_device_operation("app-stop", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
        run_device_operation(
            "app-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE,
            ACTIVITY="org.fossify.home.dikciz.DikcizHomeActivity",
        )
        connection = wait_for_restarted_websocket_control()
        connection.close()
        wait_dead(pids)
    finally:
        stream.close()


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_shell_streaming_accepts_option_boundaries(websocket_control, protocol):
    prefix = "printf boundary; #"
    command = prefix + "x" * (8192 - len(prefix))
    stream = ShellStream(protocol, command, timeoutMilliseconds=2147483647, chunkBytes=65536)
    try:
        completed, output = stream.complete()
        assert output == b"boundary"
        assert completed["state"] == "exited"
        assert completed["exitCode"] == 0
    finally:
        stream.close()
