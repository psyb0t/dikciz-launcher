"""Encrypted portable snapshots, validation and restore on the isolated emulator."""

import base64
import copy
import json
import os
import subprocess
import uuid

import pytest

from . import *
from .test_native_data import native_call, PROTOCOLS
from .test_chat_agent import provider_endpoint, conversation, chat, send, wait_turn

PASSPHRASE = "EXAMPLE-DO-NOT-USE"


def read_archive(path):
    content = bytearray()
    offset = 0
    while True:
        row = native_call("http", "fileRead", {"path": path, "offset": offset, "limit": 65536})
        part = base64.b64decode(row["dataBase64"])
        content.extend(part)
        if row["eof"]:
            return bytes(content)
        offset += len(part)


def upload_archive(content):
    path = "/sdcard/Android/data/" + DIKCIZ_DEBUG_PACKAGE + "/files/" + uuid.uuid4().hex + ".dikciz-backup"
    for offset in range(0, len(content), 65536):
        native_call("http", "fileWrite", {"path": path, "offset": offset,
                    "dataBase64": base64.b64encode(content[offset:offset + 65536]).decode(),
                    "overwrite": offset == 0})
    return path


def transform_archive(tmp_path, mode, content):
    source, destination = tmp_path / "input.bin", tmp_path / "output.bin"
    source.write_bytes(content)
    subprocess.run(["java", str(LAB_ROOT / "tests/fixtures/BackupArchiveFixture.java"),
                    mode, str(source), str(destination)], check=True, capture_output=True, timeout=30,
                   env={**os.environ, "DIKCIZ_BACKUP_FIXTURE_PASSPHRASE": PASSPHRASE})
    return destination.read_bytes()


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_operations_backup_round_trip(websocket_control, provider_endpoint, protocol, tmp_path):
    endpoint, _, _ = provider_endpoint
    profile, identifier = conversation(endpoint)
    send(identifier, "Fixture backup history")
    before = wait_turn(identifier)
    ports = native_call("http", "fileRead", {"path": "/sdcard/Dikciz/control/ports.json"}, allowed_errors=("file_not_found",))
    backup = native_call(protocol, "backupCreate", {"passphrase": PASSPHRASE})
    encoded = read_archive(backup["path"])
    assert encoded.startswith(b"DIKCIZB1") and b"Fixture backup history" not in encoded
    decoded = json.loads(transform_archive(tmp_path, "decrypt", encoded))
    assert not decoded["includesApiKeys"]
    assert all("apiKey" not in row for row in decoded["chat"]["profiles"])
    assert all(not row["path"].startswith("control/") for row in decoded["files"])
    assert any(row["conversation_id"] == identifier for row in decoded["chat"]["messages"])
    preview = native_call(protocol, "backupPreview", {"path": backup["path"], "passphrase": PASSPHRASE})
    assert preview["state"] == "ready_for_confirmation" and preview["conversations"] >= 1
    assert chat("read", conversationId=identifier)["messages"]["items"] == before
    chat("delete", conversationId=identifier)
    native_call(protocol, "backupRestore", {"previewId": preview["previewId"], "sha256": preview["sha256"], "confirm": False},
                error="validation_failed")
    restored = native_call(protocol, "backupRestore", {"previewId": preview["previewId"], "sha256": preview["sha256"], "confirm": True})
    assert restored["state"] == "restored" and not restored["automationActivated"]
    after = chat("read", conversationId=identifier)["messages"]["items"]
    assert [row["blocks"] for row in after] == [row["blocks"] for row in before]
    observed_ports = native_call("http", "fileRead", {"path": "/sdcard/Dikciz/control/ports.json"}, allowed_errors=("file_not_found",))
    if "dataBase64" in ports:
        assert observed_ports["dataBase64"] == ports["dataBase64"]
    else:
        assert observed_ports["code"] == ports["code"] == "file_not_found"
    document = AUTOMATION.request(websocket_control, "configGet")["config"]
    assert not any(row["enabled"] for row in document["scripts"])


def test_operations_backup_validation_and_secret_boundaries(websocket_control, provider_endpoint, tmp_path):
    endpoint, _, _ = provider_endpoint
    profile, _ = conversation(endpoint)
    backup = native_call("http", "backupCreate", {"passphrase": PASSPHRASE, "includeApiKeys": True})
    encoded = read_archive(backup["path"])
    decoded = json.loads(transform_archive(tmp_path, "decrypt", encoded))
    assert decoded["includesApiKeys"]
    assert next(row for row in decoded["chat"]["profiles"] if row["id"] == profile["id"])["apiKey"] == "EXAMPLE-DO-NOT-USE"
    native_call("http", "backupPreview", {"path": backup["path"], "passphrase": "ANOTHER-EXAMPLE-DO-NOT-USE"},
                error="backup_decryption_failed")
    damaged = bytearray(encoded)
    damaged[-1] ^= 1
    native_call("http", "backupPreview", {"path": upload_archive(damaged), "passphrase": PASSPHRASE},
                error="backup_decryption_failed")
    original = AUTOMATION.request(websocket_control, "configGet")["config"]
    for path in ("../outside", "/absolute", "pages/../outside", "control/remote-auth.json"):
        hostile = copy.deepcopy(decoded)
        hostile["files"][0]["path"] = path
        archive = transform_archive(tmp_path, "encrypt", json.dumps(hostile).encode())
        native_call("http", "backupPreview", {"path": upload_archive(archive), "passphrase": PASSPHRASE},
                    error="validation_failed")
    assert AUTOMATION.request(websocket_control, "configGet")["config"] == original
    preview = native_call("http", "backupPreview", {"path": backup["path"], "passphrase": PASSPHRASE})
    private = "/data/user/0/" + DIKCIZ_DEBUG_PACKAGE + "/files/backup-work/previews/" + preview["previewId"] + "/chat.json"
    native_call("http", "fileRead", {"path": private}, error="file_access_denied")
    native_call("http", "backupRestore", {"previewId": preview["previewId"], "sha256": "0" * 64, "confirm": True},
                error="validation_failed")


def backup_checkpoint(identifier):
    import shlex
    path = "files/backup-work/restore.json"
    statement = "mkdir -p files/backup-work && printf %s " + shlex.quote(json.dumps({"id": identifier})) + " > " + path
    device_command("run-as " + DIKCIZ_DEBUG_PACKAGE + " sh -c " + shlex.quote(statement))


def backup_rollback_copy(identifier):
    import shlex
    target = "files/backup-work/rollback/" + identifier
    roots = "pages scripts widget-library themes fonts wallpapers automation config.json .bundled-starter"
    device_command("run-as " + DIKCIZ_DEBUG_PACKAGE + " mkdir -p " + target)
    statement = "set -o pipefail; tar -cf - -C /sdcard/Dikciz " + roots + " | run-as " + DIKCIZ_DEBUG_PACKAGE + " tar -xf - -C " + target
    device_command(statement)


@pytest.mark.parametrize("committed", [False, True])
def test_operations_backup_restart_checkpoint(websocket_control, committed):
    from .test_capability_expansion import wait_native_control
    identifier = str(uuid.uuid4())
    if committed:
        archive = native_call("http", "backupCreate", {"passphrase": PASSPHRASE})
        preview = native_call("http", "backupPreview", {"path": archive["path"], "passphrase": PASSPHRASE})
        identifier = preview["previewId"]
        native_call("http", "backupRestore", {"previewId": identifier, "sha256": preview["sha256"], "confirm": True})
    original = AUTOMATION.request(websocket_control, "configGet")["config"]
    backup_rollback_copy(identifier)
    copied = device_command("run-as " + DIKCIZ_DEBUG_PACKAGE + " cat files/backup-work/rollback/" + identifier + "/config.json")
    copied_document = json.loads(copied)
    assert copied_document["logging"]["level"] == original["logging"]["level"], copied
    (ARTIFACT_DIRECTORY / ("rollback-copy-" + str(committed) + ".json")).write_text(copied)
    changed = copy.deepcopy(original)
    changed["launcher"]["home"]["pages"][0]["title"] = "Checkpoint changed home"
    AUTOMATION.request(websocket_control, "configReplace", config=changed)
    device_command("am force-stop " + DIKCIZ_DEBUG_PACKAGE)
    backup_checkpoint(identifier)
    restore_system_home()
    wait_native_control("http")
    restored = native_call("http", "configGet")["config"]
    (ARTIFACT_DIRECTORY / ("rollback-expected-" + str(committed) + ".json")).write_text(json.dumps(changed if committed else original))
    (ARTIFACT_DIRECTORY / ("rollback-actual-" + str(committed) + ".json")).write_text(json.dumps(restored))
    assert restored == (changed if committed else original)
    assert "restore.json" not in device_command("run-as " + DIKCIZ_DEBUG_PACKAGE + " ls files/backup-work")


def test_operations_backup_damaged_checkpoint_is_recoverable(websocket_control):
    from .test_capability_expansion import wait_native_control
    device_command("am force-stop " + DIKCIZ_DEBUG_PACKAGE)
    backup_checkpoint("invalid")
    try:
        restore_system_home()
        wait_native_control("http")
        assert device_command("pidof " + DIKCIZ_DEBUG_PACKAGE).strip()
        assert native_call("http", "deviceInfo")["application"]["packageName"] == DIKCIZ_DEBUG_PACKAGE
    finally:
        device_command("run-as " + DIKCIZ_DEBUG_PACKAGE + " rm files/backup-work/restore.json")
        device_command("am force-stop " + DIKCIZ_DEBUG_PACKAGE)
        restore_system_home()
        wait_native_control("http")
