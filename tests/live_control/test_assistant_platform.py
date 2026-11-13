"""Assistant instruction files and complete app recovery on the private emulator."""

import base64
import copy
import json
import shlex
import threading
import time
import uuid

import pytest

from . import *
from .test_native_data import native_call, PROTOCOLS
from .test_chat_agent import provider_endpoint, conversation, chat, send, wait_turn, cleanup
from .test_custom_apps import app_factory, control, wait_state, COMMAND_READ, COMMAND_STATE, COMMAND_SCHEDULE
from .test_operations_backups import PASSPHRASE, read_archive, upload_archive, transform_archive
from .test_operations_backups import backup_rollback_copy
from .test_remote_tokens import token_owner, create as token_create, call as token_call, catalogue as token_catalogue
from .test_capability_expansion import wait_native_control
from .test_storage_access_controls import wait_for_storage_access_control

ASSISTANT_ROOT = "/sdcard/Dikciz/assistant"
SKILL = "fixture-skill"
AGENT = "fixture-agent"


def write_text(path, text):
    native_call("http", "fileMkdir", {"path": path.rsplit("/", 1)[0], "parents": True})
    return native_call("http", "fileWrite", {"path": path, "dataBase64": base64.b64encode(text.encode()).decode(), "overwrite": True})


def remove_path(path):
    native_call("http", "fileManage", {"action": "delete", "path": path, "recursive": True}, allowed_errors=("file_not_found",))


@pytest.fixture
def instruction_files(websocket_control):
    assert native_call("http", "assistantLibrary", {"action": "list"})["entries"] == []
    write_text(ASSISTANT_ROOT + "/AGENTS.md", "Owner fixture instruction: describe outcomes precisely.")
    write_text(ASSISTANT_ROOT + "/skills/" + SKILL + "/SKILL.md",
               "---\nname: fixture-skill\ndescription: >\n  Inspect a fixture using native APIs.\n---\nSkill body marker 731. Read references/check.md before proceeding.")
    write_text(ASSISTANT_ROOT + "/skills/" + SKILL + "/references/check.md", "Reference marker 419. Unicode snowman: ☃")
    write_text(ASSISTANT_ROOT + "/agents/" + AGENT + ".md",
               "---\nname: fixture-agent\ndescription: Inspect a fixture independently.\nreadOnly: true\ntools: [dikciz_device_info]\n---\nNamed agent instruction marker 927.")
    write_text(ASSISTANT_ROOT + "/.claude/rules/fixture.md", "---\npaths: [apps/**]\n---\nScoped rule marker 613.")
    yield
    wait_native_control("http")
    remove_path(ASSISTANT_ROOT)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_assistant_platform_library_contract(instruction_files, protocol):
    library = native_call(protocol, "assistantLibrary", {"action": "list"})
    entries = {row["name"]: row for row in library["entries"]}
    assert entries[SKILL]["description"] == "Inspect a fixture using native APIs."
    assert "body" not in entries[SKILL]
    assert entries[AGENT]["kind"] == "agent"
    reference = native_call(protocol, "assistantLibrary", {"action": "read", "path": "skills/fixture-skill/references/check.md"})
    assert reference["text"].endswith("☃")
    for path in ("../config.json", "/absolute", "skills/../AGENTS.md", "skills\\escape"):
        native_call(protocol, "assistantLibrary", {"action": "read", "path": path}, error="validation_failed")
    native_call(protocol, "assistantLibrary", {"action": "read", "path": "missing.md"}, error="assistant_file_missing")


@pytest.mark.parametrize("case", ["missing", "duplicate", "oversized", "hooks", "symlink", "invalid_utf8"])
def test_assistant_platform_library_rejects_invalid(instruction_files, case):
    path = ASSISTANT_ROOT + "/skills/broken/SKILL.md"
    if case == "missing":
        write_text(path, "---\nname: broken\n---\nNo description.")
    elif case == "duplicate":
        path = ASSISTANT_ROOT + "/.claude/skills/fixture-skill/SKILL.md"
        write_text(path, "---\nname: fixture-skill\ndescription: Duplicate.\n---\nBody.")
    elif case == "oversized":
        write_text(ASSISTANT_ROOT + "/AGENTS.md", "x" * 65537)
    elif case == "hooks":
        write_text(path, "---\nname: broken\ndescription: Unsupported executable metadata.\nhooks: command\n---\nBody.")
    elif case == "symlink":
        # Emulated shared storage disallows symlinks; exercise an intermediate app-private link instead.
        private = "/data/user/0/" + DIKCIZ_DEBUG_PACKAGE + "/files/library-link"
        device_command("run-as " + DIKCIZ_DEBUG_PACKAGE + " ln -s /sdcard/Dikciz/assistant " + private)
        try:
            native_call("http", "assistantLibrary", {"action": "read", "path": "../library-link/AGENTS.md"}, error="validation_failed")
        finally:
            device_command("run-as " + DIKCIZ_DEBUG_PACKAGE + " rm " + private)
        return
    else:
        native_call("http", "fileWrite", {"path": ASSISTANT_ROOT + "/AGENTS.md", "dataBase64": "/w==", "overwrite": True})
    expected = "assistant_library_too_large" if case == "oversized" else "validation_failed"
    native_call("http", "assistantLibrary", {"action": "list"}, error=expected)


@pytest.mark.parametrize("protocol", ["openai", "anthropic"])
def test_assistant_platform_prompt_and_on_demand_skill(instruction_files, provider_endpoint, protocol):
    endpoint, script, records = provider_endpoint
    _, identifier = conversation(endpoint, protocol)
    script["wrapper_calls"] = [("phone_library", {"action": "read", "path": "skills/fixture-skill/SKILL.md"})]
    try:
        send(identifier, "Inspect the fixture")
        messages = wait_turn(identifier)
        first = json.dumps(records[0]["body"])
        assert "Owner fixture instruction" in first and "Scoped rule marker 613" in first
        assert "Inspect a fixture using native APIs" in first
        assert "Skill body marker 731" not in first
        assert "Skill body marker 731" in json.dumps(messages)
        write_text(ASSISTANT_ROOT + "/AGENTS.md", "Updated owner instruction marker 815.")
        records.clear()
        script["wrapper_calls"] = []
        send(identifier, "$fixture-skill use the installed instructions")
        wait_turn(identifier)
        assert "Updated owner instruction marker 815" in json.dumps(records[0]["body"])
        assert "Skill body marker 731" in json.dumps(records[0]["body"])
    finally:
        cleanup(identifier)


@pytest.mark.parametrize("access", ["inherited", "agent", "tools"])
def test_assistant_platform_agents_inherit_access_and_depth(instruction_files, provider_endpoint, access):
    endpoint, script, records = provider_endpoint
    tools = "dikciz_device_info" if access == "tools" else "dikciz_device_info, dikciz_file_write"
    write_text(ASSISTANT_ROOT + "/agents/" + AGENT + ".md",
               "---\nname: fixture-agent\ndescription: Inspect a fixture independently.\nreadOnly: " +
               ("false" if access in ("inherited", "task") else "true") + "\ntools: [" + tools + "]\n---\nNamed agent instruction marker 927.")
    _, identifier = conversation(endpoint, readOnly=access in ("inherited", "tools"), maximumRounds=8)
    marker = "/sdcard/Dikciz/assistant/forbidden.txt"
    script["text"] = "Agent fixture completed."
    script["wrapper_calls"] = [("phone_agent", {"name": AGENT, "prompt": "Inspect the fixture"}),
        ("phone_call", {"name": "dikciz_file_write", "arguments_json": json.dumps({"path": marker, "dataBase64": "eA==", "overwrite": True})})]
    before = {row["id"] for row in chat("list")["items"]}
    try:
        if access == "task":
            chat("task", conversationId=identifier, task="deviceAudit", requestKey=str(uuid.uuid4()))
        else:
            send(identifier)
        messages = wait_turn(identifier)
        agents = [row for row in chat("list")["items"] if row["id"] not in before]
        assert len(agents) == 2, agents
        for row in agents:
            saved = chat("read", conversationId=row["id"])
            assert saved["settings"]["readOnly"] is True
        bodies = json.dumps([row["body"] for row in records])
        assert "Named agent instruction marker 927" in bodies
        assert "agent_depth_exceeded" in bodies
        assert ("agent_tool_denied" if access == "tools" else "read_only_violation") in bodies
        if access == "agent":
            assert native_call("http", "fileRead", {"path": marker})["dataBase64"] == "eA=="
        else:
            native_call("http", "fileRead", {"path": marker}, error="file_not_found")
        assert "Agent fixture completed" in json.dumps(messages)
    finally:
        for row in chat("list")["items"]:
            if row["id"] not in before:
                cleanup(row["id"])
        cleanup(identifier)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_assistant_platform_backup_app_round_trip(instruction_files, app_factory, protocol, tmp_path):
    identifier, fields = app_factory()
    state = {"todo": [{"title": "Fixture task", "done": False}], "unicode": "☃"}
    native_call("http", COMMAND_STATE, {"appId": identifier, "action": "replace", "state": state})
    path = "/sdcard/Dikciz/apps/" + identifier + "/files/binary.dat"
    native_call("http", "fileMkdir", {"path": path.rsplit("/", 1)[0], "parents": True})
    native_call("http", "fileWrite", {"path": path, "dataBase64": "AP8BAg==", "overwrite": True})
    native_call("http", COMMAND_SCHEDULE, {"appId": identifier, "action": "schedule", "timerId": "saved", "delayMilliseconds": 600000, "event": "fixture.later"})
    backup = native_call(protocol, "backupCreate", {"passphrase": PASSPHRASE})
    decoded = json.loads(transform_archive(tmp_path, "decrypt", read_archive(backup["path"])))
    assert decoded["version"] == 2 and decoded["appRuntime"]
    assert any(row["path"] == "apps/" + identifier + "/files/binary.dat" for row in decoded["files"])
    preview = native_call(protocol, "backupPreview", {"path": backup["path"], "passphrase": PASSPHRASE})
    assert preview["customApps"] >= 1
    native_call("http", COMMAND_STATE, {"appId": identifier, "action": "replace", "state": {"changed": True}})
    write_text(ASSISTANT_ROOT + "/AGENTS.md", "Changed instruction.")
    native_call(protocol, "backupRestore", {"previewId": preview["previewId"], "sha256": preview["sha256"], "confirm": True})
    app = native_call(protocol, COMMAND_READ, {"appId": identifier})
    assert app["state"] == state and app["enabled"] is False
    assert app["timers"][0]["state"] == "needs_review"
    assert app["timers"][0]["reason"] == "backup_restored"
    assert native_call("http", "fileRead", {"path": path})["dataBase64"] == "AP8BAg=="
    assert native_call("http", "assistantLibrary", {"action": "read", "path": "AGENTS.md"})["text"].startswith("Owner fixture")
    assert app["backgroundSource"] == fields["backgroundSource"]


def test_assistant_platform_backup_v1_preserves_new_roots(instruction_files, app_factory, tmp_path):
    identifier, _ = app_factory()
    backup = native_call("http", "backupCreate", {"passphrase": PASSPHRASE})
    decoded = json.loads(transform_archive(tmp_path, "decrypt", read_archive(backup["path"])))
    decoded["version"] = 1
    decoded.pop("appRuntime")
    decoded["files"] = [row for row in decoded["files"] if row["path"].split("/")[0] not in ("apps", "assistant")]
    legacy = upload_archive(transform_archive(tmp_path, "encrypt", json.dumps(decoded).encode()))
    preview = native_call("http", "backupPreview", {"path": legacy, "passphrase": PASSPHRASE})
    native_call("http", "backupRestore", {"previewId": preview["previewId"], "sha256": preview["sha256"], "confirm": True})
    assert native_call("http", COMMAND_READ, {"appId": identifier})["enabled"] is True
    assert native_call("http", "assistantLibrary", {"action": "list"})["entries"]


@pytest.mark.parametrize("transition", ["update", "process", "offline_idle"])
def test_assistant_platform_recovery_matrix(app_factory, transition):
    identifier, _ = app_factory()
    native_call("http", COMMAND_STATE, {"appId": identifier, "action": "replace", "state": {"saved": True}})
    native_call("http", COMMAND_SCHEDULE, {"appId": identifier, "action": "schedule", "timerId": "recover", "delayMilliseconds": 15000, "event": "fixture.timer"})
    if transition == "update":
        apk = next((LAB_ROOT / "app/build/outputs/apk/dikciz/debug").glob("*.apk"))
        run_device_operation("install", APK=str(apk.relative_to(LAB_ROOT)))
        restore_system_home()
    elif transition == "process":
        run_device_operation("app-stop", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
        restore_system_home()
    else:
        try:
            emulator_wifi_set(AUTOMATION_SYSTEM_WIFI_DISCONNECTED)
            emulator_power_save_set(EMULATOR_POWER_SAVE_ON)
            emulator_device_idle_set(EMULATOR_DEVICE_IDLE_MODE_IDLE)
            time.sleep(16)
        finally:
            emulator_device_idle_set(EMULATOR_DEVICE_IDLE_MODE_ACTIVE)
            emulator_power_save_set(EMULATOR_POWER_SAVE_OFF)
            emulator_wifi_set(AUTOMATION_SYSTEM_WIFI_CONNECTED)
    wait_native_control("http")
    app = wait_state(identifier, "count", 1)
    assert app["state"] == {"saved": True, "count": 1}
    assert app["timers"][0]["state"] == "completed"
    time.sleep(2)
    assert native_call("http", COMMAND_READ, {"appId": identifier})["state"] == app["state"]


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_assistant_platform_scoped_library(instruction_files, token_owner, protocol):
    secret, _ = token_create(token_owner, ["command.assistantLibrary.action.read"])
    tools = token_catalogue(secret)
    assert "dikciz_assistant_library" in tools and "dikciz_file_write" not in tools
    result = token_call(secret, "assistantLibrary", {"action": "read", "path": "AGENTS.md"}, protocol=protocol)
    assert result["text"].startswith("Owner fixture")
    token_call(secret, "assistantLibrary", {"action": "list"}, protocol=protocol, error="remote_permission_denied")
    token_call(secret, "fileWrite", {"path": ASSISTANT_ROOT + "/AGENTS.md", "dataBase64": "eA==", "overwrite": True},
               protocol=protocol, error="remote_permission_denied")


def test_assistant_platform_backup_regression(app_factory, tmp_path):
    identifier, _ = app_factory()
    native_call("http", COMMAND_STATE, {"appId": identifier, "action": "replace", "state": {"mustSurvive": True}})
    backup = native_call("http", "backupCreate", {"passphrase": PASSPHRASE})
    decoded = json.loads(transform_archive(tmp_path, "decrypt", read_archive(backup["path"])))
    assert any(row["path"] == "apps/" + identifier + "/state.json" for row in decoded["files"]), "App state was omitted from the encrypted backup"
    assert decoded["version"] == 2 and "appRuntime" in decoded


@pytest.mark.parametrize("case", ["manifest", "runtime_kind", "runtime_duplicate", "installation"])
def test_assistant_platform_backup_invalid_app_preview(app_factory, tmp_path, case):
    import hashlib
    identifier, _ = app_factory()
    backup = native_call("http", "backupCreate", {"passphrase": PASSPHRASE})
    decoded = json.loads(transform_archive(tmp_path, "decrypt", read_archive(backup["path"])))
    before = native_call("http", COMMAND_READ, {"appId": identifier})
    if case == "manifest":
        entry = next(row for row in decoded["files"] if row["path"] == "apps/" + identifier + "/app.json")
        payload = json.dumps({"version": 1, "id": "wrong"}).encode()
        entry.update(data=base64.b64encode(payload).decode(), sha256=hashlib.sha256(payload).hexdigest())
    elif case == "runtime_kind":
        decoded["appRuntime"][0]["kind"] = "sos-plan"
    elif case == "runtime_duplicate":
        decoded["appRuntime"].append(copy.deepcopy(decoded["appRuntime"][0]))
    else:
        entry = next(row for row in decoded["appRuntime"] if row["kind"] == "app-install")
        entry["document"]["enabled"] = "yes"
    archive = upload_archive(transform_archive(tmp_path, "encrypt", json.dumps(decoded).encode()))
    native_call("http", "backupPreview", {"path": archive, "passphrase": PASSPHRASE}, error="validation_failed")
    assert native_call("http", COMMAND_READ, {"appId": identifier})["state"] == before["state"]


def private_json(path, value):
    encoded = base64.b64encode(json.dumps(value).encode()).decode()
    statement = "printf %s " + shlex.quote(encoded) + " | base64 -d > " + shlex.quote(path)
    device_command("run-as " + DIKCIZ_DEBUG_PACKAGE + " sh -c " + shlex.quote(statement))


@pytest.mark.parametrize("committed", [False, True])
def test_assistant_platform_backup_restore_checkpoint_coordinates_runtime(instruction_files, app_factory, tmp_path, committed):
    app_id, _ = app_factory(source="function onEvent(event) { return {actions:[]}; }")
    native_call("http", COMMAND_STATE, {"appId": app_id, "action": "replace", "state": {"snapshot": "original"}})
    native_call("http", COMMAND_SCHEDULE, {"appId": app_id, "action": "schedule", "timerId": "saved", "delayMilliseconds": 600000, "event": "fixture.later"})
    backup = native_call("http", "backupCreate", {"passphrase": PASSPHRASE})
    decoded = json.loads(transform_archive(tmp_path, "decrypt", read_archive(backup["path"])))
    identifier = str(uuid.uuid4())
    if committed:
        preview = native_call("http", "backupPreview", {"path": backup["path"], "passphrase": PASSPHRASE})
        identifier = preview["previewId"]
        native_call("http", "backupRestore", {"previewId": identifier, "sha256": preview["sha256"], "confirm": True, "activateAutomation": True})
    backup_rollback_copy(identifier)
    target = "files/backup-work/rollback/" + identifier
    device_command("set -o pipefail; tar -cf - -C /sdcard/Dikciz apps assistant | run-as " + DIKCIZ_DEBUG_PACKAGE + " tar -xf - -C " + target)
    private_json(target + "/app-runtime.json", decoded["appRuntime"])
    native_call("http", COMMAND_STATE, {"appId": app_id, "action": "replace", "state": {"snapshot": "changed"}})
    control(app_id, "disable")
    write_text(ASSISTANT_ROOT + "/AGENTS.md", "Changed instruction.")
    run_device_operation("app-stop", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
    private_json("files/backup-work/restore.json", {"id": identifier, "version": 2})
    restore_system_home()
    wait_native_control("http")
    app = native_call("http", COMMAND_READ, {"appId": app_id})
    assert app["state"] == {"snapshot": "changed" if committed else "original"}
    assert app["enabled"] is (not committed)
    if not committed:
        assert app["timers"][0]["state"] == "pending"
    expected = "Changed instruction." if committed else "Owner fixture instruction: describe outcomes precisely."
    assert native_call("http", "assistantLibrary", {"action": "read", "path": "AGENTS.md"})["text"] == expected
    assert "restore.json" not in device_command("run-as " + DIKCIZ_DEBUG_PACKAGE + " ls files/backup-work")


def test_assistant_platform_agent_cancellation(instruction_files, provider_endpoint):
    endpoint, script, records = provider_endpoint
    _, identifier = conversation(endpoint)
    before = {row["id"] for row in chat("list")["items"]}
    gate = threading.Event()
    script["text_gate"] = gate
    script["wrapper_calls"] = [("phone_agent", {"name": AGENT, "prompt": "Inspect a fixture"})]
    try:
        send(identifier)
        deadline = time.monotonic() + 20
        while len(records) < 4 and time.monotonic() < deadline:
            time.sleep(.1)
        assert len(records) >= 4
        chat("stop", conversationId=identifier)
        wait_turn(identifier, "cancelled")
        children = [row for row in chat("list")["items"] if row["id"] not in before]
        assert children
        for row in children:
            saved = chat("read", conversationId=row["id"])["messages"]["items"]
            assert not any(item["state"] in ("running", "executing", "queued") for item in saved)
    finally:
        gate.set()
        for row in chat("list")["items"]:
            if row["id"] not in before:
                cleanup(row["id"])
        cleanup(identifier)


@pytest.mark.parametrize("layout", ["portrait", "landscape", "large_text"])
def test_assistant_platform_backup_confirmation_ui(instruction_files, app_factory, websocket_control, layout):
    import shutil
    from .test_operations_ux import settings, tap
    app_factory()
    try:
        if layout == "landscape":
            emulator_device_orientation_set("landscape")
        if layout == "large_text":
            emulator_font_scale_set(130)
        wait_native_control("http")
        with connected_websocket_control() as connection:
            settings(connection)
            tap(connection, "settings:backups")
            run_device_operation("screenshot")
            shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / ("backup-apps-form-" + layout + ".png"))
            AUTOMATION.request(connection, "setText", semanticId="backups:passphrase", text=PASSPHRASE)
            tap(connection, "backups:create")
            deadline = time.monotonic() + 30
            status = ""
            while time.monotonic() < deadline:
                snapshot = AUTOMATION.request(connection, "snapshot")
                status = AUTOMATION.require_node(snapshot, "backups:status").get("text", "")
                if status.startswith("Saved encrypted backup:"):
                    break
                time.sleep(.2)
            assert status.startswith("Saved encrypted backup:"), status
            run_device_operation("screenshot")
            shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / ("backup-apps-saved-" + layout + ".png"))
            AUTOMATION.request(connection, "setText", semanticId="backups:path", text=status.split("\n", 1)[1])
            AUTOMATION.request(connection, "setText", semanticId="backups:passphrase", text=PASSPHRASE)
            tap(connection, "backups:preview")
            wait_for_snapshot_node(connection, "backups:confirm")
            snapshot = AUTOMATION.request(connection, "snapshot")
            assert "custom apps" in AUTOMATION.require_node(snapshot, "backups:summary")["text"]
            tap(connection, "backups:activate")
            snapshot = AUTOMATION.request(connection, "snapshot")
            assert AUTOMATION.require_node(snapshot, "backups:activate")["checked"]
            tap(connection, "backups:activate")
            assert not AUTOMATION.require_node(AUTOMATION.request(connection, "snapshot"), "backups:activate")["checked"]
            AUTOMATION.request(connection, "scrollTo", semanticId="backups:confirm")
            run_device_operation("screenshot")
            shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / ("backup-apps-preview-" + layout + ".png"))
            device_command("input keyevent KEYCODE_BACK")
    finally:
        if layout == "landscape":
            emulator_device_orientation_set("portrait")
        if layout == "large_text":
            emulator_font_scale_set(100)
        restore_system_home()
        wait_native_control("http")


def test_assistant_platform_library_revision(instruction_files):
    before = native_call("http", "assistantLibrary", {"action": "list"})["entries"]
    revision = next(row["sha256"] for row in before if row["name"] == SKILL)
    assert len(revision) == 64
    write_text(ASSISTANT_ROOT + "/skills/" + SKILL + "/SKILL.md",
               "---\nname: fixture-skill\ndescription: Inspect a fixture using native APIs.\n---\nUpdated body.")
    after = native_call("http", "assistantLibrary", {"action": "list"})["entries"]
    assert next(row["sha256"] for row in after if row["name"] == SKILL) != revision


def test_assistant_platform_library_storage_permission(instruction_files):
    storage_control = None
    try:
        device_command("appops set --uid " + DIKCIZ_DEBUG_PACKAGE + " MANAGE_EXTERNAL_STORAGE deny")
        run_device_operation("app-stop", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
        run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
        storage_control = wait_for_storage_access_control()
        result = native_call("http", "assistantLibrary", {"action": "list"}, error="file_access_denied")
        assert "permissionRequest" in result and result["permissionRequest"]["requirementId"] == "sharedStorage"
    finally:
        if storage_control is not None:
            storage_control.close()
        device_command("appops set --uid " + DIKCIZ_DEBUG_PACKAGE + " MANAGE_EXTERNAL_STORAGE allow")
        run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
        restored = wait_for_restarted_websocket_control()
        try:
            AUTOMATION.require_configuration(AUTOMATION.request(restored, AUTOMATION.TYPE_CONFIG_GET))
        finally:
            restored.close()


@pytest.mark.parametrize("language", ["javascript", "lua"])
def test_assistant_platform_library_native_backends(instruction_files, app_factory, language):
    if language == "javascript":
        source = "function onEvent(event) { var r=context.request({type:'assistantLibrary',action:'list'}); return {result:{count:r.result.entries.length}}; }"
    else:
        source = "function on_event(event) local r=context.request({type='assistantLibrary',action='list'}); return {result={count=#r.result.entries}} end"
    identifier, _ = app_factory(source=source, language=language)
    result = native_call("http", "customAppEvent", {"appId": identifier, "event": "fixture.library"})
    assert result["result"]["count"] == 4


@pytest.mark.parametrize("language", ["javascript", "lua"])
def test_assistant_platform_library_app_backup_callback_is_busy(app_factory, language):
    if language == "javascript":
        source = "function onEvent(event) { var r=context.request({type:'backupCreate',passphrase:'EXAMPLE-DO-NOT-USE'}); return {result:{code:r.code}}; }"
    else:
        source = "function on_event(event) local r=context.request({type='backupCreate',passphrase='EXAMPLE-DO-NOT-USE'}); return {result={code=r.code}} end"
    identifier, _ = app_factory(source=source, language=language)
    result = native_call("http", "customAppEvent", {"appId": identifier, "event": "fixture.backup"})
    assert result["result"]["code"] == "backup_busy"


def test_assistant_platform_library_temporary_agent_access(instruction_files, provider_endpoint):
    test_assistant_platform_agents_inherit_access_and_depth(instruction_files, provider_endpoint, "task")


def stage_library_files(files):
    import io
    import tarfile
    archive = ARTIFACT_DIRECTORY / ("assistant-boundary-" + uuid.uuid4().hex + ".tar")
    directories = set()
    for name in files:
        parts = name.split("/")[:-1]
        directories.update("/".join(parts[:index + 1]) for index in range(len(parts)))
    with tarfile.open(archive, "w") as output:
        for name in sorted(directories, key=lambda item: (item.count("/"), item)):
            entry = tarfile.TarInfo(name)
            entry.type = tarfile.DIRTYPE
            entry.mode = 0o755
            output.addfile(entry)
        for name, data in files.items():
            entry = tarfile.TarInfo(name)
            entry.size = len(data)
            entry.mode = 0o644
            output.addfile(entry, io.BytesIO(data))
    destination = "/sdcard/Dikciz/assistant-boundary.tar"
    native_call("http", "fileMkdir", {"path": ASSISTANT_ROOT, "parents": True})
    run_device_operation("file-push", ARTIFACT_FILE=archive.name, DEVICE_FILE=destination)
    device_command("tar -xf " + destination + " -C " + ASSISTANT_ROOT)
    remove_path(destination)


@pytest.mark.parametrize("boundary", ["files", "entries", "bytes", "depth"])
def test_assistant_platform_library_exact_limits(websocket_control, boundary):
    assert native_call("http", "assistantLibrary", {"action": "list"})["entries"] == []
    if boundary == "files":
        files = {"assets/file" + str(index): b"x" for index in range(512)}
        excess = "assets/extra"
    elif boundary == "entries":
        files = {"skills/skill-" + str(index) + "/SKILL.md":
                 ("---\nname: skill-" + str(index) + "\ndescription: Boundary fixture.\n---\nInstruction.").encode()
                 for index in range(128)}
        excess = "skills/extra/SKILL.md"
    elif boundary == "bytes":
        files = {"rules/rule" + str(index) + ".md": b"x" * 65536 for index in range(8)}
        excess = "rules/extra.md"
    else:
        files = {"/".join(["nested"] * 12) + "/data": b"x"}
        excess = "/".join(["nested"] * 13) + "/data"
    try:
        stage_library_files(files)
        native_call("http", "assistantLibrary", {"action": "list"})
        text = "---\nname: extra\ndescription: Boundary fixture.\n---\nInstruction." if boundary == "entries" else "x"
        write_text(ASSISTANT_ROOT + "/" + excess, text)
        native_call("http", "assistantLibrary", {"action": "list"}, error="assistant_library_too_large")
    finally:
        remove_path(ASSISTANT_ROOT)


def test_assistant_platform_library_prompt_limit(websocket_control, provider_endpoint):
    endpoint, script, records = provider_endpoint
    _, identifier = conversation(endpoint)
    try:
        stage_library_files({"rules/one.md": b"x" * 65536, "rules/two.md": b"y" * 65536})
        assert len(native_call("http", "assistantLibrary", {"action": "list"})["entries"]) == 2
        send(identifier)
        messages = wait_turn(identifier, "failed")
        assert not records and "Owner instructions exceed the prompt budget" in json.dumps(messages)
    finally:
        remove_path(ASSISTANT_ROOT)
        cleanup(identifier)


def test_assistant_platform_library_long_backup_path(websocket_control):
    path = "assets/" + "/".join(["a" * 90] * 6) + "/reference.md"
    try:
        write_text(ASSISTANT_ROOT + "/" + path, "Long path fixture.")
        assert native_call("http", "assistantLibrary", {"action": "read", "path": path})["text"] == "Long path fixture."
        backup = native_call("http", "backupCreate", {"passphrase": PASSPHRASE})
        preview = native_call("http", "backupPreview", {"path": backup["path"], "passphrase": PASSPHRASE})
        assert preview["state"] == "ready_for_confirmation"
        remove_path(ASSISTANT_ROOT)
        native_call("http", "backupRestore", {"previewId": preview["previewId"], "sha256": preview["sha256"], "confirm": True})
        assert native_call("http", "assistantLibrary", {"action": "read", "path": path})["text"] == "Long path fixture."
    finally:
        remove_path(ASSISTANT_ROOT)


def test_assistant_platform_library_search_restrictions(websocket_control, provider_endpoint):
    endpoint, script, records = provider_endpoint
    _, identifier = conversation(endpoint, toolAllowlist=["dikciz_device_info"])
    script["text"] = "Restricted search fixture."
    script["wrapper_calls"] = [("phone_search", {"query": "fixture", "collection": "fixture-private"}),
                                ("phone_search", {"query": "battery", "collection": "docs"})]
    try:
        send(identifier)
        messages = wait_turn(identifier)
        tools = [row for row in messages if row["role"] == "tool"]
        assert len(tools) == 2
        assert "agent_tool_denied" in tools[0]["blocks"]
        assert "agent_tool_denied" not in tools[1]["blocks"]
    finally:
        cleanup(identifier)


@pytest.mark.parametrize("size", [2048, 2049])
def test_assistant_platform_library_archive_path_budget(websocket_control, size):
    prefix = "assistant/assets/" + "/".join(["a" * 200] * 3 + ["☃" * 70] * 6) + "/"
    relative = prefix + "r" * (size - len(prefix.encode()))
    assert len(relative.encode()) == size
    try:
        write_text("/sdcard/Dikciz/" + relative, "Archive path fixture.")
        before = native_call("http", "backupsList")["total"]
        if size > 2048:
            native_call("http", "backupCreate", {"passphrase": PASSPHRASE}, error="validation_failed")
            assert native_call("http", "backupsList")["total"] == before
        else:
            backup = native_call("http", "backupCreate", {"passphrase": PASSPHRASE})
            preview = native_call("http", "backupPreview", {"path": backup["path"], "passphrase": PASSPHRASE})
            assert preview["state"] == "ready_for_confirmation"
    finally:
        remove_path(ASSISTANT_ROOT)


def test_assistant_platform_library_lua_failure_keeps_control_responsive(app_factory):
    identifier, _ = app_factory(language="lua", source="function on_event(event) local r=context.request({type='deviceInfo'}); error('fixture failure') end")
    native_call("http", "customAppEvent", {"appId": identifier, "event": "fixture.failure"}, error="custom_app_execution_failed")
    assert native_call("http", "deviceInfo")
    native_call("websocket", "customAppRead", {"appId": identifier})
