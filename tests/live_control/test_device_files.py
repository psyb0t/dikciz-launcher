"""Real files, HTTP, shell and Android installer behavior through remote controls."""

import base64
from contextlib import ExitStack
import http.client
import subprocess
import uuid

from . import *
from .test_remote_bearer_auth import (
    clear_phone_password,
    connected_bearer_websocket,
    connected_mcp,
    fresh_bearer_password,
    open_remote_auth_settings,
    set_phone_password,
)


PROTOCOLS = ("websocket", "mcp", "http")
HTTP_COMMAND_PATH = "/v1/automation"
FILE_CHUNK_BYTES = 65_536
MAXIMUM_CHUNK_BYTES = 262_144
TEST_ROOT_PREFIX = f"/sdcard/Android/data/{DIKCIZ_DEBUG_PACKAGE}/files/dikciz-files-"
PRIVATE_ROOT_PREFIX = f"/data/user/0/{DIKCIZ_DEBUG_PACKAGE}/files/dikciz-files-"
TOOL_NAMES = {
    "fileList": "dikciz_file_list",
    "fileRead": "dikciz_file_read",
    "fileWrite": "dikciz_file_write",
    "fileMkdir": "dikciz_file_mkdir",
    "apkInstall": "dikciz_apk_install",
    "apkInstallStatus": "dikciz_apk_install_status",
    "shell": "dikciz_shell",
    "controlStatus": "dikciz_control_status",
}
BINARY_DATA = bytes(range(256)) * 513
CLI_PATH = LAB_ROOT / "scripts/dikciz-control.py"
INSTALL_WAIT_SECONDS = 90


def http_request(message, headers=None, method="POST", path=HTTP_COMMAND_PATH):
    connection = http.client.HTTPConnection(AUTOMATION_HOST, MCP_PORT, timeout=20)
    try:
        request_headers = {"Content-Type": "application/json", **(headers or {})}
        connection.request(method, path, body=json.dumps(message).encode(), headers=request_headers)
        response = connection.getresponse()
        raw = response.read()
        return response.status, dict(response.getheaders()), json.loads(raw) if raw else None
    finally:
        connection.close()


@pytest.fixture
def file_controls(websocket_control):
    root = TEST_ROOT_PREFIX + uuid.uuid4().hex
    private_root = PRIVATE_ROOT_PREFIX + uuid.uuid4().hex
    # The outer runner clears both app-owned roots when it reinstalls the debug package.
    with ExitStack() as sessions:
        mcp = sessions.enter_context(connected_mcp())
        controls = {
            "ws": websocket_control, "mcp": mcp, "counter": 100,
            "root": root, "private": private_root, "close_mcp": sessions.close,
        }
        request_file(controls, "websocket", "fileMkdir", path=root, parents=True)
        yield controls


def request_file(controls, protocol, operation, error=None, **fields):
    if protocol == "websocket":
        if error is None:
            return AUTOMATION.request(controls["ws"], operation, **fields)
        response = AUTOMATION.request_error_with_id(controls["ws"], operation, **fields)[1]
        assert response["code"] == error, response
        return response
    if protocol == "http":
        status, _, response = http_request({"requestId": str(uuid.uuid4()), "type": operation, **fields})
        assert status == 200, response
        if error is None:
            assert response["type"] == "result", response
            return response["result"]
        assert response["type"] == "error", response
        assert response["code"] == error, response
        return response
    arguments, session_id = controls["mcp"]
    controls["counter"] += 1
    response = MCP.call_tool(arguments, session_id, controls["counter"], TOOL_NAMES[operation], fields)
    if error is None:
        return MCP.require_tool_result(response)
    assert response["isError"] is True, response
    assert response["structuredContent"]["code"] == error, response
    return response


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_device_files_binary_round_trip_and_private_paths(file_controls, protocol):
    root = file_controls["root"]
    path = root + "/binary-$literal;name.bin"
    for offset in range(0, len(BINARY_DATA), FILE_CHUNK_BYTES):
        chunk = BINARY_DATA[offset:offset + FILE_CHUNK_BYTES]
        result = request_file(
            file_controls, protocol, "fileWrite", path=path, offset=offset,
            dataBase64=base64.b64encode(chunk).decode(),
        )
        assert result["bytesWritten"] == len(chunk)
        assert result["nextOffset"] == offset + len(chunk)
    observed = bytearray()
    offset = 0
    while True:
        result = request_file(file_controls, protocol, "fileRead", path=path, offset=offset, limit=FILE_CHUNK_BYTES)
        observed.extend(base64.b64decode(result["dataBase64"], validate=True))
        offset = result["nextOffset"]
        if result["eof"]:
            break
    assert observed == BINARY_DATA
    for offset in (len(BINARY_DATA), len(BINARY_DATA) + 1):
        result = request_file(file_controls, protocol, "fileRead", path=path, offset=offset, limit=1)
        assert result["dataBase64"] == "" and result["eof"] is True
    private = file_controls["private"]
    request_file(file_controls, protocol, "fileMkdir", path=private, parents=True)
    private_path = private + "/private-\u03bb.txt"
    request_file(file_controls, protocol, "fileWrite", path=private_path, dataBase64="eA==")
    assert request_file(file_controls, protocol, "fileRead", path=private_path)["dataBase64"] == "eA=="
    request_file(file_controls, protocol, "fileWrite", path=path, dataBase64="", overwrite=True)
    result = request_file(file_controls, protocol, "fileRead", path=path)
    assert result["dataBase64"] == "" and result["eof"] is True


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_device_files_directory_pagination_walk(file_controls, protocol):
    path = file_controls["root"] + "/pages"
    request_file(file_controls, protocol, "fileMkdir", path=path)
    assert request_file(file_controls, protocol, "fileList", path=path, limit=5)["items"] == []
    for number in range(13):
        request_file(file_controls, protocol, "fileWrite", path=f"{path}/entry-{number:02}.bin", dataBase64="")
        if number in (0, 4):
            page = request_file(file_controls, protocol, "fileList", path=path, limit=5)
            assert len(page["items"]) == number + 1
            assert page["hasMore"] is False
    whole = request_file(file_controls, protocol, "fileList", path=path, limit=50)["items"]
    pages = [request_file(file_controls, protocol, "fileList", path=path, limit=5, offset=offset) for offset in (0, 5, 10)]
    assert [len(page["items"]) for page in pages] == [5, 5, 3]
    assert [page["hasMore"] for page in pages] == [True, True, False]
    assert [item["name"] for page in pages for item in page["items"]] == [item["name"] for item in whole]
    assert {item["name"] for item in whole} == {f"entry-{number:02}.bin" for number in range(13)}
    for offset in (13, 999):
        page = request_file(file_controls, protocol, "fileList", path=path, limit=5, offset=offset)
        assert page["items"] == [] and page["hasMore"] is False


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_device_files_validation_and_write_conflicts(file_controls, protocol):
    path = file_controls["root"] + "/target.bin"
    request_file(file_controls, protocol, "fileWrite", path=path, dataBase64="eA==")
    request_file(file_controls, protocol, "fileWrite", error="file_exists", path=path, dataBase64="eQ==")
    request_file(file_controls, protocol, "fileWrite", error="file_offset_mismatch", path=path, offset=3, dataBase64="eQ==")
    request_file(file_controls, protocol, "fileWrite", error="invalid_base64", path=path, dataBase64="%%%", overwrite=True)
    for fields in ({"path": "../relative"}, {"path": None}, {"path": ""}, {"path": path, "root": True},
                   {"path": path, "offset": -1}, {"path": path, "offset": True}, {"path": path, "offset": 1.5},
                   {"path": path, "offset": 2**63}, {"path": path, "limit": 0}, {"path": path, "limit": MAXIMUM_CHUNK_BYTES + 1}):
        request_file(file_controls, protocol, "fileRead", error="validation_failed", **fields)
    for limit in (0, 501):
        request_file(file_controls, protocol, "fileList", error="validation_failed", path=file_controls["root"], limit=limit)
    request_file(file_controls, protocol, "fileRead", error="not_a_file", path=file_controls["root"])
    request_file(file_controls, protocol, "fileList", error="not_a_directory", path=path)
    request_file(file_controls, protocol, "fileRead", error="file_not_found", path=path + ".missing")
    large = base64.b64encode(bytes(MAXIMUM_CHUNK_BYTES + 1)).decode()
    request_file(file_controls, protocol, "fileWrite", error="file_chunk_too_large", path=path, dataBase64=large, overwrite=True)
    assert request_file(file_controls, protocol, "fileRead", path=path)["dataBase64"] == "eA=="
    exact = bytes(MAXIMUM_CHUNK_BYTES)
    request_file(file_controls, protocol, "fileWrite", path=path, dataBase64=base64.b64encode(exact).decode(), overwrite=True)
    result = request_file(file_controls, protocol, "fileRead", path=path, limit=MAXIMUM_CHUNK_BYTES)
    assert base64.b64decode(result["dataBase64"]) == exact and result["eof"] is True
    request_file(file_controls, protocol, "apkInstall", error="invalid_apk", path=path)
    request_file(file_controls, protocol, "apkInstallStatus", error="apk_install_not_found", sessionId=2147483647)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_device_files_shell_output_and_exit_status(file_controls, protocol):
    result = request_file(file_controls, protocol, "shell", root=False, command="printf dikciz-cli-result; exit 7")
    assert result["output"] == "dikciz-cli-result"
    assert result["exitCode"] == 7
    assert result["outputTruncated"] is False
    denied = request_file(file_controls, protocol, "shell", root=False, command="cat /data/system/packages.xml")
    assert denied["exitCode"] != 0


def test_device_files_http_client_upload_download_and_route_security(file_controls):
    source = ARTIFACT_DIRECTORY / ("file-source-" + uuid.uuid4().hex + ".bin")
    destination = ARTIFACT_DIRECTORY / ("file-download-" + uuid.uuid4().hex + ".bin")
    source.write_bytes(BINARY_DATA)
    remote = file_controls["root"] + "/client.bin"
    common = ["--host", AUTOMATION_HOST, "--port", str(MCP_PORT), "--log-file", str(ARTIFACT_DIRECTORY / "file-client.log")]
    for operation, first, second in (("upload", str(source), remote), ("download", remote, str(destination))):
        completed = subprocess.run(["python3", str(CLI_PATH), operation, first, second, *common], capture_output=True, text=True, timeout=60)
        assert completed.returncode == 0, completed.stderr + completed.stdout
        result = json.loads(completed.stdout)
        assert result["bytes"] == len(BINARY_DATA)
        assert result["sha256"] == hashlib.sha256(BINARY_DATA).hexdigest()
    assert destination.read_bytes() == BINARY_DATA
    command = {"requestId": str(uuid.uuid4()), "type": "fileList", "path": file_controls["root"]}
    status, _, _ = http_request(command, headers={"Origin": "https://evil.example"})
    assert status == 403
    status, headers, _ = http_request(command, method="GET")
    assert status == 405 and headers.get("Allow") == "POST"
    status, _, _ = http_request(command, path="/v1/missing")
    assert status == 404


def test_device_files_http_obeys_phone_bearer_auth(websocket_control):
    password = fresh_bearer_password()
    configured = False
    try:
        open_remote_auth_settings(websocket_control)
        set_phone_password(websocket_control, password)
        configured = True
        message = {"requestId": str(uuid.uuid4()), "type": "fileList", "path": "/sdcard"}
        for headers in ({}, {"Authorization": "Bearer EXAMPLE-DO-NOT-USE"}):
            status, _, _ = http_request(message, headers=headers)
            assert status == 401
        status, _, body = http_request(message, headers={"Authorization": "Bearer " + password})
        assert status == 200 and body["type"] == "result"
    finally:
        if configured:
            with connected_bearer_websocket(password) as connection:
                clear_phone_password(connection)


def wait_for_install_state(controls, session_id, expected):
    deadline = time.monotonic() + INSTALL_WAIT_SECONDS
    state = None
    while time.monotonic() < deadline:
        state = request_file(controls, "http", "apkInstallStatus", sessionId=session_id)
        if state["state"] in expected:
            return state
        assert state["state"] not in {"failed", "cancelled"}, state
        time.sleep(0.25)
    raise AssertionError(f"APK installation did not reach {expected}: {state}")


def tap_installer_positive():
    deadline = time.monotonic() + INSTALL_WAIT_SECONDS
    while time.monotonic() < deadline:
        for label in ("Install", "Update"):
            try:
                bounds = physical_button_bounds(label)
            except AssertionError:
                continue
            run_device_operation("screenshot")
            shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "apk-install-confirmation.png")
            direct_tap(bounds)
            return
        time.sleep(0.25)
    raise AssertionError("Android installer did not show Install or Update")


def test_device_files_apk_confirmation_success_cancel_and_checksum(file_controls):
    apk = LAB_ROOT / CONFIGURABLE_PROVIDER_FIXTURE_APK_PATH
    assert apk.is_file(), "Build the configurable provider fixture before this selector"
    package = CONFIGURABLE_PROVIDER_FIXTURE_EXPECTED_PACKAGE
    already_installed = device_command("pm path " + package + " || true").strip().startswith("package:")
    path = file_controls["root"] + "/fixture.apk"
    data = apk.read_bytes()
    for offset in range(0, len(data), FILE_CHUNK_BYTES):
        request_file(file_controls, "mcp", "fileWrite", path=path, offset=offset,
                     dataBase64=base64.b64encode(data[offset:offset + FILE_CHUNK_BYTES]).decode())
    try:
        device_command("appops set " + DIKCIZ_DEBUG_PACKAGE + " REQUEST_INSTALL_PACKAGES deny")
        permission = request_file(file_controls, "mcp", "apkInstall", path=path)
        assert permission["state"] == "permission_required"
        wait_for_foreground_application(ANDROID_SETTINGS_PACKAGE)
        wait_for_physical_text_bounds("Allow from this source")
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "apk-unknown-source-access.png")
        device_command("appops set " + DIKCIZ_DEBUG_PACKAGE + " REQUEST_INSTALL_PACKAGES allow")
        restore_system_home()
        bad = request_file(file_controls, "http", "apkInstall", path=path, sha256="0" * 64)
        failed = wait_for_install_state(file_controls, bad["sessionId"], {"failed"})
        assert failed["reason"] == "apk_checksum_mismatch"
        submitted = request_file(file_controls, "mcp", "apkInstall", path=path, sha256=hashlib.sha256(data).hexdigest())
        assert submitted["state"] == "preparing"
        session_id = submitted["sessionId"]
        wait_for_install_state(file_controls, session_id, {"pending_user_action"})
        tap_installer_positive()
        completed = wait_for_install_state(file_controls, session_id, {"succeeded"})
        assert completed["packageName"] == package
        assert completed["sha256"] == hashlib.sha256(data).hexdigest()
        assert device_command("pm path " + package).strip().startswith("package:")
        restore_system_home()
        second = request_file(file_controls, "websocket", "apkInstall", path=path)
        wait_for_install_state(file_controls, second["sessionId"], {"pending_user_action"})
        direct_tap(wait_for_physical_button_bounds("Cancel"))
        wait_for_install_state(file_controls, second["sessionId"], {"cancelled"})
    finally:
        try:
            # Release the session before restoring access can restart the listeners.
            file_controls["close_mcp"]()
        finally:
            device_command("appops set " + DIKCIZ_DEBUG_PACKAGE + " REQUEST_INSTALL_PACKAGES default")
            restore_system_home()
            if not already_installed:
                uninstall_configurable_provider_fixture(package)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_shell_request_defaults_allow_more_than_five_seconds(file_controls, protocol):
    result = request_file(
        file_controls, protocol, "shell",
        command="sleep 6; printf shell-default-complete", root=False,
    )
    assert result["output"] == "shell-default-complete"
    assert result["exitCode"] == 0 and result["outputTruncated"] is False


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_shell_request_options_boundaries_and_timeout(file_controls, protocol):
    for timeout in (1, 100):
        request_file(
            file_controls, protocol, "shell", error="timeout",
            command="exec sleep 2", root=False, timeoutMilliseconds=timeout,
        )
    for limit, text, expected, truncated in (
        (1, "ab", "a", True),
        (4, "abcd", "abcd", False),
        (4, "abcde", "abcd", True),
        (1048576, "abcd", "abcd", False),
    ):
        result = request_file(
            file_controls, protocol, "shell", root=False,
            command="printf " + text, maxOutputBytes=limit,
        )
        assert result["output"] == expected
        assert result["outputTruncated"] is truncated
    default_output = request_file(
        file_controls, protocol, "shell", root=False,
        command="head -c 65537 /dev/zero",
    )
    assert len(default_output["output"]) == 65536
    assert default_output["outputTruncated"] is True
    increased = request_file(
        file_controls, protocol, "shell", root=False,
        command="head -c 70000 /dev/zero", maxOutputBytes=70000,
    )
    assert len(increased["output"]) == 70000
    assert increased["outputTruncated"] is False
    for field, maximum in (("timeoutMilliseconds", 2147483647), ("maxOutputBytes", 1048576)):
        for invalid in (None, True, 1.5, "1000", -1, 0, maximum + 1, 2**63):
            request_file(
                file_controls, protocol, "shell", error="validation_failed",
                command="printf must-not-run", root=False, **{field: invalid},
            )
    maximum_timeout = request_file(
        file_controls, protocol, "shell", root=False,
        command="printf maximum-accepted", timeoutMilliseconds=2147483647,
    )
    assert maximum_timeout["output"] == "maximum-accepted"
    status = request_file(file_controls, protocol, "controlStatus")
    assert status["limits"]["defaultShellTimeoutMilliseconds"] == 30000
    assert status["limits"]["defaultShellOutputBytes"] == 65536
    assert status["limits"]["maximumShellOutputBytes"] == 1048576


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_shell_request_client_waits_beyond_thirty_seconds(file_controls, protocol):
    fields = {"command": "sleep 31; printf extended-shell-complete",
              "root": False, "timeoutMilliseconds": 40000}
    if protocol == "mcp":
        operation = ["mcp", "dikciz_shell"]
    else:
        operation = [protocol, "shell"]
    port = AUTOMATION_PORT if protocol == "websocket" else MCP_PORT
    completed = subprocess.run(
        ["python3", str(CLI_PATH), *operation, "--host", AUTOMATION_HOST,
         "--port", str(port), "--arguments", json.dumps(fields),
         "--log-file", str(ARTIFACT_DIRECTORY / "shell-client.log")],
        capture_output=True, text=True, timeout=55,
    )
    assert completed.returncode == 0, completed.stdout + completed.stderr
    response = json.loads(completed.stdout)
    result = response["structuredContent"] if protocol == "mcp" else response["result"]
    assert result["output"] == "extended-shell-complete"
    assert result["exitCode"] == 0


def test_shell_request_default_expires_at_thirty_seconds(file_controls):
    fields = {"command": "exec sleep 32", "root": False}
    started = time.monotonic()
    completed = subprocess.run(
        ["python3", str(CLI_PATH), "http", "shell",
         "--host", AUTOMATION_HOST, "--port", str(MCP_PORT),
         "--arguments", json.dumps(fields),
         "--log-file", str(ARTIFACT_DIRECTORY / "shell-default-timeout.log")],
        capture_output=True, text=True, timeout=45,
    )
    duration = time.monotonic() - started
    assert completed.returncode != 0
    error = json.loads(completed.stdout)
    assert error["response"]["code"] == "timeout", completed.stderr
    assert 29 <= duration < 40
