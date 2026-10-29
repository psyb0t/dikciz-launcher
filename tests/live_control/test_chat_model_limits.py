"""Automatic model limits through the real chat command and native UI."""

import json
import shutil
import uuid
import time

import pytest

from . import test_chat_agent as agent
from .test_native_data import PROTOCOLS, native_call
from .ui import (
    direct_enter_text,
    direct_hide_keyboard,
    direct_tap,
    physical_description_bounds,
    physical_description_text,
    wait_for_physical_button_bounds,
    wait_for_physical_description_bounds,
    wait_for_physical_text_bounds,
)

websocket_control = agent.websocket_control
provider_endpoint = agent.provider_endpoint


@pytest.mark.parametrize("entrypoint", PROTOCOLS)
@pytest.mark.parametrize("provider,model,context", [
    ("openai", "gpt-6.1-sol", 1050000),
    ("openai", "gpt-4o", 128000),
    ("openai", "gpt-4", 8192),
    ("anthropic", "claude-opus-5-5", 1000000),
    ("anthropic", "claude-sonnet-4-6", 1000000),
])
def test_chat_model_limits_catalog(websocket_control, provider_endpoint, entrypoint, provider, model, context):
    profile = agent.chat("profileSave", entrypoint, profile={
        "name": "Limits fixture " + uuid.uuid4().hex, "protocol": provider,
        "baseUrl": provider_endpoint[0], "model": model,
    })
    identifier = agent.chat("create", entrypoint, profileId=profile["id"])["id"]
    try:
        result = agent.chat("settings", entrypoint, conversationId=identifier)
        assert result["settings"]["contextTokens"] == context
        assert result["modelLimits"]["contextTokens"] == context
        assert result["modelLimits"]["limitsSource"] == "catalog"
        assert result["settings"]["maxOutputTokens"] <= result["modelLimits"]["maxOutputTokens"]
        assert result["settings"]["contextTokens"] > sum(result["settings"][key] for key in ("maxOutputTokens", "summaryOutputTokens", "safetyMarginTokens"))
        agent.chat("configure", entrypoint, conversationId=identifier, settings={"contextTokens": 32768, "maxOutputTokens": 1024, "readOnly": True})
        agent.chat("configure", entrypoint, conversationId=identifier, settings={"model": model})
        settings = agent.chat("settings", entrypoint, conversationId=identifier)["settings"]
        assert settings["contextTokens"] == context
        assert settings["maxOutputTokens"] == 1024
        assert settings["readOnly"] is True
        agent.chat("configure", entrypoint, conversationId=identifier, settings={"model": model, "contextTokens": 32768, "maxOutputTokens": 512})
        assert agent.chat("settings", entrypoint, conversationId=identifier)["settings"]["contextTokens"] == 32768
    finally:
        agent.cleanup(identifier)


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
@pytest.mark.parametrize("entrypoint", PROTOCOLS)
def test_chat_model_limits_provider_pages_and_restart(websocket_control, provider_endpoint, provider, entrypoint):
    endpoint, script, _ = provider_endpoint
    context_field = "max_input_tokens" if provider == "anthropic" else "context_length"
    script.update(models=[{"id": "fixture-limits-" + str(index), context_field: 65536 + index,
                           "max_tokens": 2048} for index in range(13)], model_page_size=5)
    profile, identifier = agent.conversation(endpoint, provider)
    try:
        rows = agent.chat("models", entrypoint, profileId=profile["id"])["models"]
        assert len(rows) == 13
        assert [row["id"] for row in rows] == [row["id"] for row in script["models"]]
        assert [request["cursor"] for request in script["model_requests"]] == [None, "fixture-limits-4", "fixture-limits-9"]
        assert all(row["limitsSource"] == "provider" for row in rows)
        agent.chat("configure", entrypoint, conversationId=identifier, settings={"model": "fixture-limits-12"})
        before = agent.chat("settings", entrypoint, conversationId=identifier)
        assert before["settings"]["contextTokens"] == 65548
        assert before["settings"]["maxOutputTokens"] == 2048
        assert "EXAMPLE-DO-NOT-USE" not in json.dumps(before)
        native_call("http", "restart")
        agent.wait_chat_ready()
        assert agent.chat("settings", entrypoint, conversationId=identifier) == before
        agent.chat("profileSave", profile={**profile, "apiKey": ""})
        assert agent.chat("settings", entrypoint, conversationId=identifier)["modelLimits"]["limitsSource"] == "unknown"
    finally:
        agent.cleanup(identifier)


@pytest.mark.parametrize("invalid", [None, 0, -1, 1.5, "65536", 10000001, True])
def test_chat_model_limits_unknown_and_invalid_metadata(websocket_control, provider_endpoint, invalid):
    endpoint, script, _ = provider_endpoint
    script["models"] = [{"id": "fixture-unknown", "context_length": invalid, "max_tokens": invalid}]
    profile, identifier = agent.conversation(endpoint)
    try:
        row = agent.chat("models", profileId=profile["id"])["models"][0]
        assert row["contextTokens"] is None and row["maxOutputTokens"] is None
        assert row["limitsSource"] == "unknown"
        agent.chat("configure", conversationId=identifier, settings={"model": "fixture-unknown", "contextTokens": 32768, "maxOutputTokens": 512})
        agent.chat("configure", conversationId=identifier, settings={"model": "gpt-unknown-future"})
        assert agent.chat("settings", conversationId=identifier)["settings"]["contextTokens"] == 32768
    finally:
        agent.cleanup(identifier)


def test_chat_model_limits_repeated_cursor_rejected(websocket_control, provider_endpoint):
    endpoint, script, _ = provider_endpoint
    script.update(models=["fixture-model"], model_repeat_cursor=True)
    profile, identifier = agent.conversation(endpoint)
    try:
        native_call("http", "chatControl", {"action": "models", "profileId": profile["id"]}, error="chat_provider_error")
        assert len(script["model_requests"]) == 2
    finally:
        agent.cleanup(identifier)


def test_chat_model_limits_native_defaults_and_override(websocket_control, provider_endpoint):
    endpoint, script, _ = provider_endpoint
    script["models"] = ["fixture-model", "gpt-6.1-sol"]
    _, identifier = agent.conversation(endpoint)
    try:
        agent.chat("show", conversationId=identifier)
        direct_tap(wait_for_physical_description_bounds("Chat model"))
        direct_tap(wait_for_physical_text_bounds("gpt-6.1-sol"))
        direct_tap(wait_for_physical_description_bounds("Chat model settings"))
        wait_for_physical_description_bounds("Total context tokens")
        assert physical_description_text("Total context tokens") == "1050000"
        assert "1050000" in physical_description_text("Known model token limits")
        agent.run_device_operation("screenshot")
        shutil.copyfile(agent.ARTIFACT_DIRECTORY / "screenshot.png", agent.ARTIFACT_DIRECTORY / "chat-model-limits-settings.png")
        direct_enter_text(physical_description_bounds("Model ID"), "gpt-4o")
        direct_hide_keyboard()
        wait_for_physical_description_bounds("Total context tokens")
        assert physical_description_text("Total context tokens") == "128000"
        direct_enter_text(physical_description_bounds("Total context tokens"), "65536")
        direct_hide_keyboard()
        direct_tap(wait_for_physical_button_bounds("Save"))
        deadline = time.monotonic() + agent.TURN_TIMEOUT
        while agent.chat("settings", conversationId=identifier)["settings"]["contextTokens"] != 65536 and time.monotonic() < deadline:
            time.sleep(0.1)
        saved = agent.chat("settings", conversationId=identifier)["settings"]
        assert saved["contextTokens"] == 65536 and saved["model"] == "gpt-4o"
        agent.run_device_operation("screenshot")
    finally:
        agent.cleanup(identifier)


@pytest.mark.parametrize("policy", ["drop-oldest", "summarize"])
def test_chat_model_limits_input_ceiling_blocks_oversized_request(websocket_control, provider_endpoint, policy):
    endpoint, script, records = provider_endpoint
    script["models"] = [{"id": "fixture-input-cap", "context_length": 65536,
                         "max_input_tokens": 16384, "max_tokens": 2048}]
    profile, identifier = agent.conversation(endpoint, compaction=policy)
    try:
        agent.chat("models", profileId=profile["id"])
        agent.chat("configure", conversationId=identifier, settings={"model": "fixture-input-cap"})
        agent.chat("send", conversationId=identifier, text="x" * 20000, requestKey=str(uuid.uuid4()))
        agent.wait_turn(identifier, expected="failed")
        assert records == []
    finally:
        agent.cleanup(identifier)


@pytest.mark.parametrize("count", [0, 1])
def test_chat_model_limits_empty_and_single_page(websocket_control, provider_endpoint, count):
    endpoint, script, _ = provider_endpoint
    script["models"] = ["fixture-single"] * count
    profile, identifier = agent.conversation(endpoint)
    try:
        assert len(agent.chat("models", profileId=profile["id"])["models"]) == count
        assert script["model_requests"] == [{"cursor": None}]
    finally:
        agent.cleanup(identifier)


@pytest.mark.parametrize("old_version", [1, 2])
def test_chat_model_limits_native_upgrade_retains_data(websocket_control, provider_endpoint, old_version):
    endpoint, _, records = provider_endpoint
    _, identifier = agent.conversation(endpoint, contextTokens=32768, maxOutputTokens=512)
    try:
        agent.send(identifier)
        agent.wait_turn(identifier)
        before = agent.chat("read", conversationId=identifier)
        agent.chat("hide")
        agent.run_device_operation("app-stop", PACKAGE_NAME=agent.DIKCIZ_DEBUG_PACKAGE)
        changes = {
            1: "PRAGMA wal_checkpoint(TRUNCATE); DROP TABLE model_catalog; ALTER TABLE conversations DROP COLUMN settings; PRAGMA user_version=1;",
            2: "PRAGMA wal_checkpoint(TRUNCATE); DROP TABLE model_catalog; PRAGMA user_version=2;",
        }
        agent.device_command("run-as eu.psyb0t.dikciz.launcher.debug sqlite3 databases/dikciz-chat.sqlite '" + changes[old_version] + "'")
        agent.run_device_operation("home-start", PACKAGE_NAME=agent.DIKCIZ_DEBUG_PACKAGE)
        agent.wait_chat_ready()
        after = agent.chat("read", conversationId=identifier)
        assert after["messages"]["items"] == before["messages"]["items"]
        assert after["settings"] == before["settings"]
        agent.send(identifier)
        agent.wait_turn(identifier)
        assert records[-1]["usedFixtureKey"]
        assert agent.device_command("run-as eu.psyb0t.dikciz.launcher.debug sqlite3 databases/dikciz-chat.sqlite 'PRAGMA user_version;'").strip() == "3"
    finally:
        agent.cleanup(identifier)
