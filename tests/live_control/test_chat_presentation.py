"""Native chat geometry, keyboard, empty state and notification presentation."""

import shutil
import threading
import time

from . import *
from . import test_chat_agent as agent
from .test_chat_agent import provider_endpoint
from .test_chat_background import chat_notification_access, active_notification, hide_chat, ACTIVE_TITLE, LONG_RESPONSE
from .ui import (
    center, direct_enter_text, direct_hide_keyboard, direct_swipe, direct_tap,
    node_bounds, physical_description_text, physical_nodes,
    wait_for_physical_description_bounds, wait_for_physical_text_bounds,
)

CHAT_HISTORY = "Chats"
CHAT_COMPOSER = "Chat message"
CHAT_TITLE_FIELD = "Conversation title"
CHAT_SEARCH = "Search chats"


def capture(name):
    agent.run_device_operation("screenshot")
    shutil.copyfile(agent.ARTIFACT_DIRECTORY / "screenshot.png", agent.ARTIFACT_DIRECTORY / name)


def history_row(title, state=""):
    prefix = f"Open {title},"
    deadline = time.monotonic() + agent.TURN_TIMEOUT
    while time.monotonic() < deadline:
        for node in physical_nodes():
            label = node.get("content-desc", "")
            if label.startswith(prefix) and state in label:
                return node_bounds(node)
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"History did not show {title!r} with {state!r}")


def open_chat_history():
    direct_tap(wait_for_physical_description_bounds(CHAT_HISTORY))
    wait_for_physical_text_bounds("Conversations")


def test_chat_presentation_history_switch_search_unread_and_actions(websocket_control, provider_endpoint):
    endpoint, script, records = provider_endpoint
    _, first = agent.conversation(endpoint)
    _, second = agent.conversation(endpoint)
    first_title = "Weekend plans"
    second_title = "Reading list"
    first_draft = "Bring a raincoat"
    second_draft = "Read another chapter"
    gate = threading.Event()
    previous_keyboard = agent.device_command("settings get secure show_ime_with_hard_keyboard").strip()
    try:
        agent.device_command("settings put secure show_ime_with_hard_keyboard 1")
        agent.chat("rename", conversationId=first, title=first_title)
        agent.chat("rename", conversationId=second, title=second_title)
        agent.chat("show", conversationId=first)
        wait_for_physical_text_bounds(first_title)
        direct_enter_text(wait_for_physical_description_bounds(CHAT_COMPOSER), first_draft, description=CHAT_COMPOSER)

        open_chat_history()
        history_row(first_title, "Current chat")
        assert not any(node.get("content-desc") == CHAT_COMPOSER for node in physical_nodes())
        assert records == []
        assert not script.get("model_requests")
        direct_enter_text(wait_for_physical_description_bounds(CHAT_SEARCH), "Missing title", description=CHAT_SEARCH)
        wait_for_physical_text_bounds("No conversations match your search")
        direct_tap(wait_for_physical_description_bounds("Clear chat search"))
        direct_enter_text(wait_for_physical_description_bounds(CHAT_SEARCH), "reading", description=CHAT_SEARCH)
        direct_hide_keyboard()
        wait_for_physical_text_bounds("Conversations")
        assert not any(node.get("content-desc", "").startswith(f"Open {first_title},") for node in physical_nodes())
        direct_tap(history_row(second_title))
        wait_for_physical_text_bounds(second_title)
        direct_enter_text(wait_for_physical_description_bounds(CHAT_COMPOSER), second_draft, description=CHAT_COMPOSER)
        open_chat_history()
        direct_tap(history_row(first_title))
        wait_for_physical_description_text(CHAT_COMPOSER, first_draft)

        script.update(text="Pack the raincoat.", text_gate=gate)
        agent.send(first, "What should I pack?")
        wait_for_physical_description_bounds("Stop agent turn")
        open_chat_history()
        history_row(first_title, "In progress")
        gate.set()
        agent.wait_turn(first)
        history_row(first_title, "1 unread")
        before_read = agent.chat("read", conversationId=first)["conversation"]
        assert before_read["unread"] == 1
        agent.send(second, "Add a reading suggestion.")
        agent.wait_turn(second)
        history_row(second_title, "1 unread")
        hide_chat()
        agent.device_command("input keyevent KEYCODE_HOME")
        direct_tap(wait_for_physical_description_bounds("Open Dikciz chat. 2 unread"))
        wait_for_physical_text_bounds("Conversations")
        history_row(first_title, "1 unread")
        assert agent.chat("read", conversationId=first)["conversation"]["unread"] == 1
        capture("chat-history.png")
        direct_tap(history_row(first_title))
        wait_for_physical_text_bounds("Pack the raincoat.")
        wait_for_physical_description_text(CHAT_COMPOSER, first_draft)
        after_read = agent.chat("read", conversationId=first)["conversation"]
        assert after_read["unread"] == 0
        assert agent.chat("read", conversationId=second)["conversation"]["unread"] == 1
        assert after_read["updated"] == before_read["updated"]

        open_chat_history()
        direct_tap(wait_for_physical_description_bounds(f"Actions for {second_title}"))
        direct_tap(wait_for_physical_text_bounds("Delete"))
        wait_for_physical_text_bounds(f'Delete "{second_title}"?')
        direct_tap(wait_for_physical_text_bounds("Cancel"))
        history_row(second_title)
        direct_tap(wait_for_physical_description_bounds(f"Actions for {second_title}"))
        direct_tap(wait_for_physical_text_bounds("Rename"))
        direct_tap(wait_for_physical_description_bounds(CHAT_TITLE_FIELD))
        agent.device_command("input keycombination KEYCODE_CTRL_LEFT KEYCODE_A")
        agent.device_command("input text Books")
        direct_hide_keyboard()
        direct_tap(wait_for_physical_text_bounds("Save"))
        history_row("Books")
        history_row(first_title, "Current chat")
        direct_tap(wait_for_physical_description_bounds("Back to chat"))
        wait_for_physical_description_text(CHAT_COMPOSER, first_draft)
        direct_tap(wait_for_physical_description_bounds("Expand chat"))
        wait_for_physical_description_bounds("Collapse chat")
        open_chat_history()
        direct_tap(history_row("Books"))
        wait_for_physical_description_bounds("Collapse chat")
        wait_for_physical_description_text(CHAT_COMPOSER, second_draft)

        gate.clear()
        agent.send(second, "Wait for cancellation.")
        direct_tap(wait_for_physical_description_bounds("Stop agent turn"))
        agent.wait_turn(second, "cancelled")

        open_chat_history()
        direct_tap(wait_for_physical_description_bounds("Actions for Books"))
        direct_tap(wait_for_physical_text_bounds("Delete"))
        wait_for_physical_text_bounds('Delete "Books"?')
        direct_tap(wait_for_physical_button_bounds("Delete"))
        deadline = time.monotonic() + agent.TURN_TIMEOUT
        while time.monotonic() < deadline:
            remaining = {row["id"] for row in agent.chat("list")["items"]}
            if second not in remaining:
                break
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
        assert first in remaining and second not in remaining
        history_row(first_title)
    finally:
        gate.set()
        if previous_keyboard == "null":
            agent.device_command("settings delete secure show_ime_with_hard_keyboard")
        else:
            agent.device_command("settings put secure show_ime_with_hard_keyboard " + previous_keyboard)
        agent.cleanup(first)
        if second in {row["id"] for row in agent.chat("list")["items"]}:
            agent.cleanup(second)


def wait_for_chat_window(orientation, font_scale):
    expected_rotation = {"portrait": "ROTATION_0", "landscape": "ROTATION_90"}[orientation]
    deadline = time.monotonic() + agent.TURN_TIMEOUT
    while time.monotonic() < deadline:
        lines = agent.device_command("dumpsys window").splitlines()
        config = next((line for line in lines if "mGlobalConfiguration={" in line), "")
        if f"mGlobalConfiguration={{{font_scale} " in config and f"mDisplayRotation={expected_rotation}" in config:
            return
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"Android did not apply {orientation} at {font_scale} text scale")


def test_chat_presentation_history_landscape_large_text(websocket_control, provider_endpoint):
    _, first = agent.conversation(provider_endpoint[0])
    _, second = agent.conversation(provider_endpoint[0])
    try:
        agent.chat("rename", conversationId=second, title="Garden sketch")
        provider_endpoint[1]["text"] = "Plant mint."
        agent.send(second, "Suggest a herb.")
        agent.wait_turn(second)
        agent.device_command("cmd window user-rotation lock 1")
        wait_for_chat_window("landscape", "1.0")
        emulator_font_scale_set(EMULATOR_DEVICE_FONT_SCALE_PERCENT_ENLARGED)
        wait_for_chat_window("landscape", "1.3")
        agent.wait_chat_ready()
        agent.chat("show", conversationId=first)
        wait_for_physical_text_bounds("Fixture conversation")
        bounds = wait_for_physical_description_bounds(CHAT_HISTORY)
        minimum_target = int(48 * device_density_scale())
        assert bounds["right"] - bounds["left"] >= minimum_target
        assert bounds["bottom"] - bounds["top"] >= minimum_target
        open_chat_history()
        wait_for_physical_text_bounds("New chat")
        history_row("Garden sketch", "1 unread")
        capture("chat-history-landscape.png")
        direct_tap(history_row("Garden sketch"))
        wait_for_physical_text_bounds("Garden sketch")
        wait_for_physical_description_bounds(CHAT_COMPOSER)
    finally:
        try:
            agent.device_command("cmd window user-rotation lock 0")
            wait_for_chat_window("portrait", "1.3")
        finally:
            emulator_font_scale_set(EMULATOR_DEVICE_FONT_SCALE_PERCENT_BASELINE)
        wait_for_chat_window("portrait", "1.0")
        agent.wait_chat_ready()
        agent.chat("hide")
        agent.wait_chat_ready()
        for identifier in (first, second):
            agent.chat("delete", conversationId=identifier)


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
        direct_tap(wait_for_physical_description_bounds("Back to chat"))
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
        agent.device_command("settings put secure show_ime_with_hard_keyboard 1")
        before = wait_for_physical_description_bounds("Chat message")
        direct_enter_text(before, "Draft stays here", description="Chat message")
        after = wait_for_physical_description_bounds("Chat message")
        assert after["bottom"] < before["bottom"]
        assert physical_description_text("Chat message") == "Draft stays here"
        assert not agent.chat("status")["fullScreen"]
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
    wait_for_physical_text_bounds("Start a conversation")
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
        if not any(node.get("content-desc") == "Back to chat" for node in physical_nodes()):
            direct_tap(wait_for_physical_description_bounds("Chat options"))
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
