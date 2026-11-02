"""Native chat geometry, keyboard, empty state and notification presentation."""

import shutil
import time

from . import *
from . import test_chat_agent as agent
from .test_chat_agent import provider_endpoint
from .test_chat_background import chat_notification_access, active_notification, LONG_RESPONSE
from .ui import direct_tap, direct_enter_text, direct_hide_keyboard, physical_nodes, physical_description_text, wait_for_physical_description_bounds, wait_for_physical_text_bounds


def capture(name):
    agent.run_device_operation("screenshot")
    shutil.copyfile(agent.ARTIFACT_DIRECTORY / "screenshot.png", agent.ARTIFACT_DIRECTORY / name)


def test_chat_presentation_sheet_keyboard_and_notification(websocket_control, provider_endpoint, chat_notification_access):
    _, script, _ = provider_endpoint
    _, identifier = agent.conversation(provider_endpoint[0])
    old_keyboard = agent.device_command("settings get secure show_ime_with_hard_keyboard").strip()
    script.update(text="# Ready when you are\n\n**Markdown works while streaming.**\n\n- Inspect a screen\n- Build a widget\n- Stop from the notification\n\n| Control | Purpose |\n| --- | --- |\n| Tune | Model settings |\n| Arrow | Send a message |")
    try:
        agent.chat("rename", conversationId=identifier, title="Dikciz chat")
        agent.send(identifier)
        agent.wait_turn(identifier)
        agent.chat("show", conversationId=identifier)
        half = wait_for_physical_description_bounds("Chat messages")
        composer = wait_for_physical_description_bounds("Chat message")
        model = wait_for_physical_description_bounds("Chat model")
        tune = wait_for_physical_description_bounds("Chat model settings")
        assert half["bottom"] <= composer["top"] and model["right"] <= tune["left"]
        assert half["bottom"] - half["top"] > 100
        assert not any(node.get("content-desc") == "Stop agent turn" for node in physical_nodes())
        capture("chat-sheet-half.png")
        direct_tap(wait_for_physical_description_bounds("Expand or reduce chat"))
        deadline = time.monotonic() + agent.TURN_TIMEOUT
        while time.monotonic() < deadline:
            full = wait_for_physical_description_bounds("Chat messages")
            if full["bottom"] - full["top"] > half["bottom"] - half["top"]:
                break
        assert full["bottom"] - full["top"] > half["bottom"] - half["top"]
        capture("chat-sheet-full.png")
        agent.device_command("settings put secure show_ime_with_hard_keyboard 1")
        before = wait_for_physical_description_bounds("Chat message")
        direct_enter_text(before, "Draft stays here", description="Chat message")
        after = wait_for_physical_description_bounds("Chat message")
        assert after["bottom"] < before["bottom"]
        assert physical_description_text("Chat message") == "Draft stays here"
        capture("chat-sheet-keyboard.png")
        direct_hide_keyboard()
        script.update(text=LONG_RESPONSE, delay=0.04)
        agent.send(identifier, "Keep working while the panel is hidden.")
        agent.chat("hide")
        active_notification()
        agent.device_command("cmd statusbar expand-notifications")
        stop_bounds = wait_for_physical_text_bounds("Stop")
        capture("chat-progress-notification.png")
        direct_tap(stop_bounds)
        agent.wait_turn(identifier, "cancelled")
        agent.device_command("cmd statusbar collapse")
    finally:
        if old_keyboard == "null":
            agent.device_command("settings delete secure show_ime_with_hard_keyboard")
        else:
            agent.device_command("settings put secure show_ime_with_hard_keyboard " + old_keyboard)
        agent.cleanup(identifier)
    agent.chat("show")
    wait_for_physical_description_bounds("Empty chat")
    assert not any(node.get("content-desc") == "Stop agent turn" for node in physical_nodes())
    assert physical_description_text("Chat agent status") == "Ready"
    capture("chat-sheet-empty.png")
    agent.chat("hide")
