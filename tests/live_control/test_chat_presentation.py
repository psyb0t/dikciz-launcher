"""Native chat geometry, keyboard, empty state and notification presentation."""

import shutil
import threading
import time

from . import *
from . import test_chat_agent as agent
from .test_chat_agent import provider_endpoint
from .test_chat_background import chat_notification_access, active_notification, hide_chat, ACTIVE_TITLE, LONG_RESPONSE
from .ui import (
    center, direct_enter_text, direct_swipe, direct_tap,
    node_bounds, physical_description_text, physical_nodes,
    wait_for_physical_description_bounds, wait_for_physical_text_bounds,
)


def capture(name):
    agent.run_device_operation("screenshot")
    shutil.copyfile(agent.ARTIFACT_DIRECTORY / "screenshot.png", agent.ARTIFACT_DIRECTORY / name)


def expand_working_notification():
    deadline = time.monotonic() + agent.TURN_TIMEOUT
    while time.monotonic() < deadline:
        nodes = physical_nodes()
        if any(node.get("text") == "Stop" for node in nodes):
            return
        for parent in reversed(nodes):
            descendants = list(parent.iter("node"))
            if not any(node.get("text") == ACTIVE_TITLE for node in descendants):
                continue
            expand = next((node for node in descendants if node.get("content-desc") == "Expand"
                           and node.get("resource-id") == "android:id/expand_button"), None)
            if expand is not None:
                direct_tap(node_bounds(expand))
                break
        time.sleep(0.1)
    raise AssertionError("The working notification did not expose Stop after expansion")


def test_chat_presentation_sheet_keyboard_and_notification(websocket_control, provider_endpoint, chat_notification_access):
    _, script, _ = provider_endpoint
    text_gate = threading.Event()
    _, identifier = agent.conversation(provider_endpoint[0])
    _, alternate = agent.conversation(provider_endpoint[0])
    old_keyboard = agent.device_command("settings get secure show_ime_with_hard_keyboard").strip()
    script.update(text="# Ready when you are\n\n**Markdown works while streaming.**\n\n- Inspect a screen\n- Build a widget\n- Stop from the notification\n\n| Control | Purpose |\n| --- | --- |\n| Tune | Model settings |\n| Arrow | Send a message |")
    try:
        agent.chat("rename", conversationId=identifier, title="Dikciz chat")
        agent.send(identifier)
        agent.wait_turn(identifier)
        agent.chat("show", conversationId=identifier)
        half = wait_for_physical_description_bounds("Chat messages")
        composer = wait_for_physical_description_bounds("Chat message")
        assert half["bottom"] <= composer["top"]
        assert not any(node.get("content-desc") == "Chat model" for node in physical_nodes())
        assert half["bottom"] - half["top"] > 100
        assert not any(node.get("content-desc") == "Stop agent turn" for node in physical_nodes())
        capture("chat-sheet-half.png")
        direct_tap(wait_for_physical_description_bounds("Chat options"))
        model = wait_for_physical_description_bounds("Chat model")
        tune = wait_for_physical_description_bounds("Chat model settings")
        assert model["right"] <= tune["left"]
        capture("chat-sheet-options.png")
        direct_tap(wait_for_physical_description_bounds("Chat options"))
        expand = wait_for_physical_description_bounds("Expand chat")
        x, y = center(expand)
        distance = 2 * (expand["bottom"] - expand["top"])
        direct_swipe((x, y), (x, y - distance))
        collapse = wait_for_physical_description_bounds("Collapse chat")
        assert agent.chat("status")["fullScreen"]
        full = wait_for_physical_description_bounds("Chat messages")
        assert full["top"] < half["top"] and full["bottom"] == half["bottom"]
        capture("chat-sheet-full.png")
        x, y = center(collapse)
        direct_swipe((x, y), (x, y + distance))
        expand = wait_for_physical_description_bounds("Expand chat")
        assert not agent.chat("status")["fullScreen"]
        assert wait_for_physical_description_bounds("Chat messages") == half
        direct_tap(expand)
        wait_for_physical_description_bounds("Collapse chat")
        assert agent.chat("status")["fullScreen"]
        agent.device_command("settings put secure show_ime_with_hard_keyboard 1")
        before = wait_for_physical_description_bounds("Chat message")
        direct_enter_text(before, "Draft stays here", description="Chat message")
        after = wait_for_physical_description_bounds("Chat message")
        assert after["bottom"] < before["bottom"]
        assert physical_description_text("Chat message") == "Draft stays here"
        capture("chat-sheet-keyboard.png")
        agent.device_command("input keyevent KEYCODE_HOME")
        wait_for_physical_description_bounds("Open Dikciz launcher controls")
        assert not agent.chat("status")["visible"]
        agent.chat("show", conversationId=identifier)
        wait_for_physical_description_bounds("Expand chat")
        assert physical_description_text("Chat message") == "Draft stays here"
        agent.chat("show", conversationId=alternate)
        wait_for_physical_text_bounds("Fixture conversation")
        direct_enter_text(wait_for_physical_description_bounds("Chat message"), "Different draft", description="Chat message")
        agent.chat("show", conversationId=identifier)
        deadline = time.monotonic() + agent.TURN_TIMEOUT
        while time.monotonic() < deadline and physical_description_text("Chat message") != "Draft stays here":
            time.sleep(0.1)
        assert physical_description_text("Chat message") == "Draft stays here"
        script.update(text=LONG_RESPONSE, delay=0.04, text_gate=text_gate)
        agent.send(identifier, "Keep working while the panel is hidden.")
        hide_chat()
        active_notification()
        agent.device_command("cmd statusbar expand-notifications")
        expand_working_notification()
        stop_bounds = wait_for_physical_text_bounds("Stop")
        capture("chat-progress-notification.png")
        direct_tap(stop_bounds)
        agent.wait_turn(identifier, "cancelled")
        agent.device_command("cmd statusbar collapse")
    finally:
        text_gate.set()
        if old_keyboard == "null":
            agent.device_command("settings delete secure show_ime_with_hard_keyboard")
        else:
            agent.device_command("settings put secure show_ime_with_hard_keyboard " + old_keyboard)
        agent.cleanup(identifier)
        agent.cleanup(alternate)
    agent.chat("show")
    wait_for_physical_description_bounds("Empty chat")
    assert not any(node.get("content-desc") == "Stop agent turn" for node in physical_nodes())
    assert physical_description_text("Chat agent status") == "Ready"
    capture("chat-sheet-empty.png")
    agent.chat("hide")


def test_chat_presentation_reviews_destination_before_switching(websocket_control, provider_endpoint):
    endpoint, _, records = provider_endpoint
    original, identifier = agent.conversation(endpoint)
    destination = agent.chat("profileSave", profile={
        "name": "Provider review fixture",
        "protocol": "openai",
        "baseUrl": endpoint,
        "model": "fixture-model",
    })
    created = None

    def review():
        direct_tap(wait_for_physical_description_bounds("Choose conversation"))
        direct_tap(wait_for_physical_text_bounds("Manage"))
        direct_tap(wait_for_physical_text_bounds("Change provider"))
        direct_tap(wait_for_physical_text_bounds("Provider review fixture"))

    try:
        agent.chat("show", conversationId=identifier)
        review()
        switch = wait_for_physical_text_bounds("Switch provider")
        assert agent.chat("settings", conversationId=identifier)["profileId"] == original["id"]
        capture("chat-provider-review.png")
        direct_tap(switch)
        deadline = time.monotonic() + agent.TURN_TIMEOUT
        while time.monotonic() < deadline:
            if agent.chat("settings", conversationId=identifier)["profileId"] == destination["id"]:
                break
            time.sleep(0.1)
        assert agent.chat("settings", conversationId=identifier)["profileId"] == destination["id"]
        previous = {row["id"] for row in agent.chat("list")["items"]}
        review()
        direct_tap(wait_for_physical_text_bounds("New conversation"))
        deadline = time.monotonic() + agent.TURN_TIMEOUT
        while time.monotonic() < deadline:
            created = next((row for row in agent.chat("list")["items"] if row["id"] not in previous), None)
            if created:
                break
            time.sleep(0.1)
        assert created and created["profile_id"] == destination["id"]
        assert agent.chat("read", conversationId=created["id"])["messages"]["items"] == []
        assert records == []
    finally:
        agent.cleanup(identifier)
        if created:
            agent.cleanup(created["id"])
