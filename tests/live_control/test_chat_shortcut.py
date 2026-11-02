"""Idle notification chat access over another app without overlay permission."""

import json
import re
import shutil
import time

import pytest

from . import *
from . import test_chat_agent as agent
from .test_chat_agent import provider_endpoint
from .test_chat_background import chat_notification_access
from .test_native_data import native_call
from .ui import direct_tap, wait_for_physical_text_bounds, wait_for_physical_description_bounds

SHORTCUT_TITLE = "Dikciz chat"


@pytest.mark.parametrize("configured", [True, False])
def test_chat_shortcut_opens_over_another_app(websocket_control, provider_endpoint, chat_notification_access, configured):
    endpoint, script, records = provider_endpoint
    identifier = agent.conversation(endpoint)[1] if configured else None
    overlay = agent.device_command("appops get " + agent.DIKCIZ_DEBUG_PACKAGE + " SYSTEM_ALERT_WINDOW")
    match = re.search(r"SYSTEM_ALERT_WINDOW: (allow|ignore|deny|default)", overlay)
    old_overlay = match.group(1) if match else "default"
    try:
        agent.device_command("appops set " + agent.DIKCIZ_DEBUG_PACKAGE + " SYSTEM_ALERT_WINDOW deny")
        agent.emulator_dikciz_accessibility_set(True)
        agent.device_command("am start -a android.settings.SETTINGS")
        agent.run_device_operation("home-start", PACKAGE_NAME=agent.DIKCIZ_DEBUG_PACKAGE)
        agent.device_command("am start -a android.settings.SETTINGS")
        deadline = time.monotonic() + agent.TURN_TIMEOUT
        while time.monotonic() < deadline:
            notifications = native_call("http", "notificationsList", {"limit": 200})["items"]
            shortcut = next((item for item in notifications if item["packageName"] == agent.DIKCIZ_DEBUG_PACKAGE
                             and item["title"] == SHORTCUT_TITLE), None)
            if shortcut is not None:
                break
            time.sleep(0.1)
        assert shortcut is not None
        assert [item["title"] for item in shortcut["actions"]] == ["Open chat"]
        assert agent.chat("status")["busy"] is False
        services = agent.device_command("dumpsys activity services " + agent.DIKCIZ_DEBUG_PACKAGE)
        assert not re.search(r"ServiceRecord[^\n]*DikcizChatService", services)
        agent.device_command("cmd statusbar expand-notifications")
        direct_tap(wait_for_physical_text_bounds("Open chat"))
        wait_for_physical_description_bounds("Chat message")
        assert agent.chat("status")["visible"] is True
        assert records == []
        agent.run_device_operation("screenshot")
        shutil.copyfile(agent.ARTIFACT_DIRECTORY / "screenshot.png", agent.ARTIFACT_DIRECTORY / ("chat-shortcut-configured.png" if configured else "chat-shortcut-first-run.png"))
        if configured:
            script.update(text="The captured app is Android Settings.")
            direct_tap(wait_for_physical_description_bounds("Explain this screen"))
            agent.wait_turn(identifier)
            body = records[0]["body"]
            assert "com.android.settings" in json.dumps(body)
            assert "Choose conversation" not in json.dumps(body)
            contents = [part for message in body["messages"] for part in message.get("content", []) if isinstance(part, dict)]
            assert any(part.get("type") == "image_url" for part in contents)
            assert agent.chat("settings", conversationId=identifier)["settings"]["readOnly"] is False
        else:
            wait_for_physical_description_bounds("Empty chat")
    finally:
        agent.device_command("cmd statusbar collapse")
        agent.device_command("appops set " + agent.DIKCIZ_DEBUG_PACKAGE + " SYSTEM_ALERT_WINDOW " + old_overlay)
        agent.emulator_dikciz_accessibility_set(False)
        if identifier is not None:
            agent.cleanup(identifier)
        else:
            agent.chat("hide")
        agent.run_device_operation("home-start", PACKAGE_NAME=agent.DIKCIZ_DEBUG_PACKAGE)
