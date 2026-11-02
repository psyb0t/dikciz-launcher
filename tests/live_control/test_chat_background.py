"""Background assistant progress and cancellation through real Android notifications."""

import json
import shutil
import threading
import time
import uuid

import pytest

from . import *
from . import test_chat_agent as agent
from .test_chat_agent import provider_endpoint
from .test_native_data import PROTOCOLS, native_call
from .ui import direct_tap, physical_nodes, wait_for_physical_text_bounds, wait_for_physical_description_text, wait_for_physical_description_bounds

ACTIVE_TITLE = "Dikciz is working"
POST_PERMISSION = "android.permission.POST_NOTIFICATIONS"
LONG_RESPONSE = "Background fixture reply. " * 400


def active_notification(text=None):
    deadline = time.monotonic() + agent.TURN_TIMEOUT
    last = []
    while time.monotonic() < deadline:
        last = native_call("http", "notificationsList", {"limit": 200})["items"]
        selected = next((item for item in last if item["packageName"] == agent.DIKCIZ_DEBUG_PACKAGE
                         and item["title"] == ACTIVE_TITLE and (text is None or item["text"] == text)), None)
        if selected is not None:
            return selected
        time.sleep(0.1)
    raise AssertionError("Active chat notification did not reach expected state: " + json.dumps(last))


def hide_chat():
    agent.chat("hide")
    deadline = time.monotonic() + agent.TURN_TIMEOUT
    while time.monotonic() < deadline:
        if not agent.chat("status")["visible"] and not any(
            node.get("content-desc") == "Chat message" for node in physical_nodes()
        ):
            return
        time.sleep(0.1)
    raise AssertionError("Chat did not finish hiding before opening the notification shade")


@pytest.fixture
def chat_notification_access():
    original = agent.device_command("settings get secure enabled_notification_listeners").strip()
    granted = POST_PERMISSION + ": granted=true" in agent.device_command("dumpsys package " + agent.DIKCIZ_DEBUG_PACKAGE)
    agent.device_command("pm grant " + agent.DIKCIZ_DEBUG_PACKAGE + " " + POST_PERMISSION)
    agent.device_command("cmd notification allow_listener " + AUTOMATION_NOTIFICATION_COMPONENT)
    try:
        yield
    finally:
        agent.device_command("cmd statusbar collapse")
        if AUTOMATION_NOTIFICATION_COMPONENT not in original.split(":"):
            agent.device_command("cmd notification disallow_listener " + AUTOMATION_NOTIFICATION_COMPONENT)
        if not granted:
            agent.device_command("pm revoke " + agent.DIKCIZ_DEBUG_PACKAGE + " " + POST_PERMISSION)


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
@pytest.mark.parametrize("entrypoint", PROTOCOLS)
def test_chat_background_progress_reopen_and_stop(websocket_control, provider_endpoint, chat_notification_access, provider, entrypoint):
    endpoint, script, records = provider_endpoint
    _, identifier = agent.conversation(endpoint, provider)
    _, other = agent.conversation(endpoint, provider)
    text_gate = threading.Event()
    script["text_gate"] = text_gate
    title = "Working fixture " + uuid.uuid4().hex[:6]
    agent.chat("rename", conversationId=identifier, title=title)
    script.update(text=LONG_RESPONSE, delay=0.04,
                  tools=[("dikciz_shell", {"root": False, "command": "sleep 8", "timeoutMilliseconds": 15000})])
    try:
        agent.chat("show", conversationId=other)
        wait_for_physical_description_bounds("Choose conversation")
        hide_chat()
        agent.chat("send", protocol=entrypoint, conversationId=identifier,
                   text="Run the fixture command and explain the result.", requestKey=str(uuid.uuid4()))
        notification = active_notification("Using shell")
        assert [item["title"] for item in notification["actions"]] == ["Stop"]
        assert "sleep" not in notification["text"] and "15000" not in notification["text"]
        agent.device_command("cmd statusbar expand-notifications")
        wait_for_physical_text_bounds("Stop")
        direct_tap(wait_for_physical_text_bounds(ACTIVE_TITLE))
        wait_for_physical_description_bounds("Choose conversation")
        wait_for_physical_description_text("Choose conversation", title)
        assert agent.chat("status")["visible"] is True
        agent.run_device_operation("screenshot")
        shutil.copyfile(agent.ARTIFACT_DIRECTORY / "screenshot.png", agent.ARTIFACT_DIRECTORY / ("chat-background-open-" + provider + "-" + entrypoint + ".png"))
        hide_chat()
        agent.device_command("cmd statusbar expand-notifications")
        stop_bounds = wait_for_physical_text_bounds("Stop")
        agent.run_device_operation("screenshot")
        shutil.copyfile(agent.ARTIFACT_DIRECTORY / "screenshot.png", agent.ARTIFACT_DIRECTORY / ("chat-background-notification-" + provider + "-" + entrypoint + ".png"))
        direct_tap(stop_bounds)
        rows = agent.wait_turn(identifier, "cancelled")
        assert rows[0]["state"] == "cancelled"
        deadline = time.monotonic() + agent.TURN_TIMEOUT
        while agent.chat("status")["busy"] and time.monotonic() < deadline:
            time.sleep(0.1)
        assert agent.chat("status")["busy"] is False
        agent.device_command("cmd statusbar collapse")
        text_gate.set()
        script.update(text="Resumed after stopping.", delay=0.002, tools=[])
        agent.send(identifier, "Continue after cancellation.")
        rows = agent.wait_turn(identifier)
        assert "Resumed after stopping." in rows[-1]["blocks"]
    except AssertionError:
        agent.run_device_operation("screenshot")
        shutil.copyfile(agent.ARTIFACT_DIRECTORY / "screenshot.png", agent.ARTIFACT_DIRECTORY / ("chat-background-failure-" + provider + "-" + entrypoint + ".png"))
        (agent.ARTIFACT_DIRECTORY / "chat-background-failure-status.json").write_text(json.dumps(agent.chat("status")))
        raise
    finally:
        text_gate.set()
        agent.cleanup(identifier)
        agent.cleanup(other)


def test_chat_background_notifications_denied(websocket_control, provider_endpoint):
    _, identifier = agent.conversation(provider_endpoint[0])
    provider_endpoint[1].update(text="Completed without notification permission.")
    granted = POST_PERMISSION + ": granted=true" in agent.device_command("dumpsys package " + agent.DIKCIZ_DEBUG_PACKAGE)
    agent.device_command("pm revoke " + agent.DIKCIZ_DEBUG_PACKAGE + " " + POST_PERMISSION)
    try:
        agent.chat("hide")
        agent.send(identifier)
        rows = agent.wait_turn(identifier)
        assert "Completed without notification permission." in rows[-1]["blocks"]
    finally:
        agent.cleanup(identifier)
        if granted:
            agent.device_command("pm grant " + agent.DIKCIZ_DEBUG_PACKAGE + " " + POST_PERMISSION)


def test_chat_background_stale_stop_preserves_new_turn(websocket_control, provider_endpoint, chat_notification_access):
    endpoint, script, records = provider_endpoint
    _, identifier = agent.conversation(endpoint)
    script.update(text=LONG_RESPONSE, delay=0.04)
    try:
        agent.send(identifier)
        active_notification()
        old_turn = agent.chat("read", conversationId=identifier)["messages"]["items"][0]["turn_id"]
        agent.chat("stop", conversationId=identifier)
        agent.wait_turn(identifier, "cancelled")
        deadline = time.monotonic() + agent.TURN_TIMEOUT
        while agent.chat("status")["busy"] and time.monotonic() < deadline:
            time.sleep(0.1)
        agent.send(identifier, "A later fixture turn.")
        active_notification()
        component = agent.DIKCIZ_DEBUG_PACKAGE + "/org.fossify.home.dikciz.DikcizChatService"
        command = "run-as " + agent.DIKCIZ_DEBUG_PACKAGE + " am startservice --user 0 -n " + component
        command += " -a org.fossify.home.dikciz.chat.STOP --es conversationId " + identifier
        for suffix in (" --es turnId " + old_turn, "", " --es turnId missing"):
            output = agent.device_command(command + suffix)
            assert "Error" not in output and "Exception" not in output, output
            assert agent.chat("status")["activeConversationId"] == identifier
            rows = agent.chat("read", conversationId=identifier)["messages"]["items"]
            owner = next(row for row in reversed(rows) if row["role"] == "user")
            assert owner["state"] == "running"
        agent.chat("stop", conversationId=identifier)
        agent.wait_turn(identifier, "cancelled")
    finally:
        agent.cleanup(identifier)


def test_chat_background_event_listener_returns_to_waiting(websocket_control, provider_endpoint, chat_notification_access):
    _, identifier = agent.conversation(provider_endpoint[0])
    try:
        agent.chat("subscribe", conversationId=identifier, rules=[{
            "subscription": {"event": "fixture.chat.progress", "minimumIntervalMilliseconds": 60000},
            "instructions": "Report this fixture event.",
        }])
        agent.send(identifier)
        agent.wait_turn(identifier)
        deadline = time.monotonic() + agent.TURN_TIMEOUT
        while time.monotonic() < deadline:
            rows = native_call("http", "notificationsList", {"limit": 200})["items"]
            waiting = next((row for row in rows if row["packageName"] == agent.DIKCIZ_DEBUG_PACKAGE
                            and row["title"] == "Dikciz event listener"), None)
            if waiting is not None:
                assert waiting["actions"] == []
                assert waiting["text"] == "Waiting for your configured events"
                break
            time.sleep(0.1)
        else:
            raise AssertionError("Chat foreground service did not return to its event-listener state")
    finally:
        agent.chat("subscribe", conversationId=identifier, rules=[])
        agent.cleanup(identifier)
