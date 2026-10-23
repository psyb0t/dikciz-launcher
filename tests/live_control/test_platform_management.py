"""File lifecycle and real notification operations through the shared APIs."""

import base64
import uuid
from . import *
from .test_native_data import native_call, PROTOCOLS
from .notification_action import start_fixture_notification_action


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_capability_expansion_file_management(websocket_control, protocol):
    root = "/sdcard/Android/data/" + DIKCIZ_DEBUG_PACKAGE + "/files/platform-" + uuid.uuid4().hex
    native_call("http", "fileMkdir", {"path": root})
    payload = b"Platform file fixture"
    try:
        for index in range(7):
            native_call("http", "fileWrite", {"path": root + "/fixture-" + str(index), "dataBase64": base64.b64encode(payload).decode()})
        result = native_call(protocol, "fileManage", {"action": "stat", "path": root + "/fixture-0"})
        assert result["size"] == len(payload) and result["kind"] == "file"
        copied = root + "/copied"
        native_call(protocol, "fileManage", {"action": "copy", "path": root + "/fixture-0", "destination": copied})
        native_call(protocol, "fileManage", {"action": "copy", "path": root + "/fixture-0", "destination": copied}, error="file_io_failed")
        moved = root + "/moved"
        native_call(protocol, "fileManage", {"action": "move", "path": copied, "destination": moved})
        assert base64.b64decode(native_call("http", "fileRead", {"path": moved})["dataBase64"]) == payload
        found = []
        for offset, count, more in ((0, 3, True), (3, 3, True), (6, 1, False), (7, 0, False), (10000, 0, False)):
            page = native_call(protocol, "fileManage", {"action": "search", "path": root, "query": "fixture-", "offset": offset, "limit": 3})
            assert len(page["items"]) == count and page["hasMore"] is more
            found.extend(row["path"] for row in page["items"])
        assert len(set(found)) == 7
        native_call(protocol, "fileManage", {"action": "delete", "path": root}, error="file_io_failed")
        native_call(protocol, "fileManage", {"action": "delete", "path": root, "recursive": True, "maximumEntries": 2}, error="file_tree_limit")
        assert native_call(protocol, "fileManage", {"action": "stat", "path": moved})["size"] == len(payload)
        for action, fields in (("stat", {"destination": moved}), ("delete", {"recursive": "yes"}), ("copy", {}), ("search", {})):
            native_call(protocol, "fileManage", {"action": action, "path": root, **fields}, error="validation_failed")
        for path in ("/sdcard/Dikciz/control/remote-auth.json", "/storage/emulated/0/Dikciz/control/remote-auth.json", "/sdcard/Dikciz/control/remote-auth.json.bak"):
            native_call(protocol, "fileRead", {"path": path}, error="file_access_denied")
            native_call(protocol, "fileManage", {"action": "stat", "path": path}, error="file_access_denied")
        native_call(protocol, "fileManage", {"action": "delete", "path": "/sdcard/Dikciz", "recursive": True}, error="file_access_denied")
    finally:
        native_call("http", "fileManage", {"action": "delete", "path": root, "recursive": True})


def fixture_notification(protocol, package):
    deadline = time.monotonic() + 15
    while True:
        rows = native_call(protocol, "notificationsList", {"limit": 200})["items"]
        notification = next((row for row in rows if row["packageName"] == package), None)
        if notification is not None:
            return notification
        assert time.monotonic() < deadline, rows
        time.sleep(.1)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_capability_expansion_notification_management(websocket_control, installed_configurable_provider_fixture, protocol):
    package = configurable_provider_fixture_package()
    device_command("pm grant " + package + " android.permission.POST_NOTIFICATIONS")
    device_command("cmd notification allow_listener " + AUTOMATION_NOTIFICATION_COMPONENT)
    try:
        deadline = time.monotonic() + 15
        while True:
            try:
                native_call(protocol, "notificationsList")
                break
            except AssertionError:
                if time.monotonic() >= deadline:
                    raise
                time.sleep(.1)
        start_fixture_notification_action(package)
        notification = fixture_notification(protocol, package)
        plain = next(action for action in notification["actions"] if not action["supportsReply"])
        reply = next(action for action in notification["actions"] if action["supportsReply"])
        native_call(protocol, "notificationManage", {"action": "reply", "actionToken": plain["actionToken"], "text": "Test"}, error="notification_reply_unsupported")
        result = native_call(protocol, "notificationManage", {"action": "reply", "actionToken": reply["actionToken"], "text": "Fixture reply"})
        assert result["state"] == "submitted" and result["delivered"] is False
        deadline = time.monotonic() + 10
        while "Fixture reply" not in device_command("run-as " + package + " cat shared_prefs/dikciz_notification_action.xml"):
            assert time.monotonic() < deadline
            time.sleep(.1)
        native_call(protocol, "notificationManage", {"action": "reply", "actionToken": reply["actionToken"], "text": "Duplicate"}, error="notification_action_unavailable")
        for action in ("snooze", "dismiss"):
            if action == "dismiss":
                start_fixture_notification_action(package)
                notification = fixture_notification(protocol, package)
            fields = {"action": action, "notificationKey": notification["key"]}
            if action == "snooze": fields["durationMilliseconds"] = 60000
            assert native_call(protocol, "notificationManage", fields)["state"] == "submitted"
            deadline = time.monotonic() + 10
            while any(row["key"] == notification["key"] for row in native_call(protocol, "notificationsList", {"limit":200})["items"]):
                assert time.monotonic() < deadline
                time.sleep(.1)
        native_call(protocol, "notificationManage", {"action": "dismiss", "notificationKey": "missing"}, error="native_record_not_found")
        native_call(protocol, "notificationManage", {"action": "reply", "actionToken": "missing", "text": ""}, error="validation_failed")
    finally:
        device_command("cmd notification disallow_listener " + AUTOMATION_NOTIFICATION_COMPONENT)
        restore_system_home()
