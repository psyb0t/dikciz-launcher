"""Provider-boundary and native UI tests for the on-device assistant."""

import http.server
import logging
import os
import subprocess
import threading
import uuid

from . import *
from .test_native_data import native_call, PROTOCOLS

TURN_TIMEOUT = 40
PROVIDER_PORT_ATTEMPTS = 8
LOGGER = logging.getLogger(__name__)
FIXTURE_TEXT = "# Streaming answer\n\n**Bold text** and a list:\n\n- first\n- second\n\n```python\nprint('fixture')\n```\n\n| Name | Value |\n| --- | --- |\n| fixture | ready |\n"


def chat(action, protocol="http", **arguments):
    return native_call(protocol, "chatControl", {"action": action, **arguments})


def provider_reverse(*arguments):
    assert os.environ.get("DIKCIZ_TEST_IN_FORWARD") == "true"
    assert os.environ.get("ANDROID_LAB_INSTANCE") == "shared"
    return subprocess.run(["adb", "-s", "emulator:5556", "reverse", *arguments],
        capture_output=True, text=True, timeout=20, check=True)


@pytest.fixture
def provider_endpoint():
    records = []
    script = {"mode": "text", "delay": 0.002, "text": FIXTURE_TEXT, "conversation": None, "tools": [], "wrapper_calls": [], "models": ["fixture-model"]}

    class Handler(http.server.BaseHTTPRequestHandler):
        def log_message(self, _format, *args):
            return

        def do_GET(self):
            if script["mode"] == "models_unauthorized":
                self.send_response(401)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(b'{"error":{"message":"Fixture model catalogue unavailable"}}')
                return
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            if self.headers.get("anthropic-version"):
                response = {"data": [{"id": model, "type": "model", "display_name": model, "created_at": "2020-01-01T00:00:00Z"} for model in script["models"]], "first_id": next(iter(script["models"]), None), "last_id": next(iter(reversed(script["models"])), None), "has_more": False}
            else:
                response = {"object": "list", "data": [{"id": model, "object": "model", "created": 1, "owned_by": "fixture"} for model in script["models"]]}
            self.wfile.write(json.dumps(response).encode())

        def do_POST(self):
            body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            records.append({"path": self.path, "body": body, "usedFixtureKey": self.headers.get("Authorization") == "Bearer EXAMPLE-DO-NOT-USE" or self.headers.get("x-api-key") == "EXAMPLE-DO-NOT-USE"})
            if script["mode"] in ("unauthorized", "rate_limit"):
                self.send_response(401 if script["mode"] == "unauthorized" else 429)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(b'{"error":{"message":"Fixture credential rejected","type":"authentication_error"}}')
                return
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.end_headers()
            summary = str(body.get("system", "")).startswith("Summarize this conversation") or any(
                message.get("role") == "system" and str(message.get("content", "")).startswith("Summarize this conversation")
                for message in body.get("messages", [])
            )
            text = "Summary of completed fixture exchanges." if summary else script["text"]
            try:
                if self.path.endswith("/messages"):
                    self.anthropic(body, text)
                else:
                    self.openai(body, text, summary)
            except (BrokenPipeError, ConnectionResetError):
                LOGGER.debug("fixture_provider_client_disconnected")

        def event(self, document, name=None):
            if name:
                self.wfile.write(("event: " + name + "\n").encode())
            self.wfile.write(("data: " + json.dumps(document) + "\n\n").encode())
            self.wfile.flush()
            time.sleep(script["delay"])

        def openai(self, body, text, summary):
            def chunk(delta, finish=None):
                self.event({"id": "fixture-completion", "object": "chat.completion.chunk", "created": 1,
                            "model": "fixture-model", "choices": [{"index": 0, "delta": delta, "finish_reason": finish}]})
            tools = script["wrapper_calls"] or script["tools"]
            tool_results = sum(message.get("role") == "tool" for message in body.get("messages", []))
            if tools and tool_results < len(tools) and not summary:
                name, arguments = tools[tool_results]
                wrapper = name if script["wrapper_calls"] else "phone_call"
                encoded = json.dumps(arguments if script["wrapper_calls"] else {"name": name, "arguments_json": json.dumps(arguments)})
                chunk({"role": "assistant", "tool_calls": [{"index": 0, "id": "fixture-call-" + str(tool_results), "type": "function", "function": {"name": wrapper, "arguments": encoded[:7]}}]})
                chunk({"tool_calls": [{"index": 0, "function": {"arguments": encoded[7:]}}]})
                chunk({}, "tool_calls")
            else:
                chunk({"role": "assistant"})
                for offset in range(0, len(text), 3):
                    chunk({"content": text[offset:offset+3]})
                if script["mode"] == "broken":
                    return
                chunk({}, "stop")
            self.wfile.write(b"data: [DONE]\n\n")
            self.wfile.flush()

        def anthropic(self, body, text):
            self.event({"type": "message_start", "message": {"id": "fixture-message", "type": "message", "role": "assistant", "content": [], "model": "fixture-model", "stop_reason": None, "stop_sequence": None, "usage": {"input_tokens": 20, "output_tokens": 0}}}, "message_start")
            index = 0
            if script.get("thinking"):
                if script["thinking"] == "redacted":
                    self.event({"type": "content_block_start", "index": index, "content_block": {"type": "redacted_thinking", "data": "EXAMPLE-DO-NOT-USE-OPAQUE"}}, "content_block_start")
                else:
                    self.event({"type": "content_block_start", "index": index, "content_block": {"type": "thinking", "thinking": "", "signature": ""}}, "content_block_start")
                    if script["thinking"] != "omitted":
                        self.event({"type": "content_block_delta", "index": index, "delta": {"type": "thinking_delta", "thinking": "Fixture reasoning."}}, "content_block_delta")
                    for fragment in ("EXAMPLE-", "DO-NOT-USE-SIGNATURE"):
                        self.event({"type": "content_block_delta", "index": index, "delta": {"type": "signature_delta", "signature": fragment}}, "content_block_delta")
                self.event({"type": "content_block_stop", "index": index}, "content_block_stop")
                index += 1
            tool_results = sum(block.get("type") == "tool_result" for message in body.get("messages", []) for block in message.get("content", []) if isinstance(block, dict))
            tools = script["wrapper_calls"] or script["tools"]
            reason = "end_turn"
            if tools and tool_results < len(tools):
                name, arguments = tools[tool_results]
                wrapper = name if script["wrapper_calls"] else "phone_call"
                encoded = json.dumps(arguments if script["wrapper_calls"] else {"name": name, "arguments_json": json.dumps(arguments)})
                self.event({"type": "content_block_start", "index": index, "content_block": {"type": "tool_use", "id": "fixture-call-" + str(tool_results), "name": wrapper, "input": {}}}, "content_block_start")
                for fragment in (encoded[:8], encoded[8:]):
                    self.event({"type": "content_block_delta", "index": index, "delta": {"type": "input_json_delta", "partial_json": fragment}}, "content_block_delta")
                reason = "tool_use"
            else:
                self.event({"type": "content_block_start", "index": index, "content_block": {"type": "text", "text": ""}}, "content_block_start")
                for offset in range(0, len(text), 3):
                    self.event({"type": "content_block_delta", "index": index, "delta": {"type": "text_delta", "text": text[offset:offset+3]}}, "content_block_delta")
            if script["mode"] == "broken":
                return
            self.event({"type": "content_block_stop", "index": index}, "content_block_stop")
            self.event({"type": "message_delta", "delta": {"stop_reason": reason, "stop_sequence": None}, "usage": {"output_tokens": 30}}, "message_delta")
            self.event({"type": "message_stop"}, "message_stop")

    server = http.server.ThreadingHTTPServer(("0.0.0.0", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    endpoint = "tcp:" + str(server.server_port)
    started = False
    mapped = False
    try:
        for attempt in range(PROVIDER_PORT_ATTEMPTS):
            try:
                provider_reverse("--no-rebind", endpoint, endpoint)
                mapped = True
                break
            except subprocess.CalledProcessError as exception:
                if "Address already in use" not in exception.stderr or attempt + 1 == PROVIDER_PORT_ATTEMPTS:
                    raise
                LOGGER.warning("fixture_provider_port_collision", extra={"attempt": attempt + 1})
                server.server_close()
                server = http.server.ThreadingHTTPServer(("0.0.0.0", 0), Handler)
                endpoint = "tcp:" + str(server.server_port)
                thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        started = True
        yield f"http://127.0.0.1:{server.server_port}/v1", script, records
    finally:
        if started:
            server.shutdown()
            thread.join(timeout=5)
        server.server_close()
        if mapped:
            provider_reverse("--remove", endpoint)


def conversation(endpoint, protocol="openai", **overrides):
    profile = chat("profileSave", profile={"name": "Fixture " + uuid.uuid4().hex, "protocol": protocol,
        "baseUrl": endpoint, "apiKey": "EXAMPLE-DO-NOT-USE", "model": "fixture-model", **overrides})
    return profile, chat("create", profileId=profile["id"], title="Fixture conversation")["id"]


def wait_turn(identifier, expected="complete"):
    deadline = time.monotonic() + TURN_TIMEOUT
    observed = None
    while time.monotonic() < deadline:
        try:
            observed = chat("read", conversationId=identifier)["messages"]["items"]
        except (OSError, json.JSONDecodeError):
            LOGGER.debug("fixture_waiting_for_chat_after_restart")
            time.sleep(0.2)
            continue
        if observed and not any(row["state"] in ("queued", "running", "executing") for row in observed):
            assert observed[-1]["state"] == expected, observed
            return observed
        time.sleep(0.1)
    raise AssertionError(f"Chat turn did not settle: {observed!r}")


def assert_tool_pairing(body):
    pending = set()
    for message in body["messages"]:
        if message["role"] == "tool":
            assert message["tool_call_id"] in pending
            pending.remove(message["tool_call_id"])
            continue
        content = message.get("content", [])
        content = content if isinstance(content, list) else []
        results = [part for part in content if isinstance(part, dict) and part.get("type") == "tool_result"]
        for result in results:
            assert result["tool_use_id"] in pending
            pending.remove(result["tool_use_id"])
        assert not pending, message
        calls = message.get("tool_calls", [])
        pending.update(call["id"] for call in calls)
        pending.update(part["id"] for part in content if isinstance(part, dict) and part.get("type") == "tool_use")
    assert not pending


def send(identifier, text="Read the fixture", request_key=None):
    return chat("send", conversationId=identifier, text=text, requestKey=request_key or str(uuid.uuid4()))


def cleanup(identifier):
    wait_chat_ready()
    chat("hide")
    chat("stop", conversationId=identifier)
    chat("subscribe", conversationId=identifier, rules=[])
    deadline = time.monotonic() + TURN_TIMEOUT
    while chat("status")["busy"] and time.monotonic() < deadline:
        time.sleep(0.1)
    chat("delete", conversationId=identifier)


def wait_chat_ready():
    deadline = time.monotonic() + TURN_TIMEOUT
    consecutive = 0
    while time.monotonic() < deadline:
        try:
            chat("status")
            consecutive += 1
            if consecutive == 3:
                return
        except (AssertionError, OSError, json.JSONDecodeError):
            LOGGER.debug("fixture_waiting_for_chat_listener")
            consecutive = 0
        time.sleep(0.2)
    raise AssertionError("Chat listener did not stabilize")


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
def test_chat_agent_provider_stream_and_models(websocket_control, provider_endpoint, provider):
    endpoint, script, records = provider_endpoint
    profile, identifier = conversation(endpoint, provider)
    try:
        assert "apiKey" not in profile
        assert "fixture-model" in {model["id"] for model in chat("models", profileId=profile["id"])["models"]}
        send(identifier)
        rows = wait_turn(identifier)
        assert json.loads(rows[-1]["blocks"])[0]["text"] == FIXTURE_TEXT
        assert "EXAMPLE-DO-NOT-USE" not in json.dumps(chat("profiles"))
        body = records[-1]["body"]
        assert body["stream"] is True
        assert body.get("max_tokens", body.get("max_completion_tokens")) == 4096
        assert body["tools"]
    finally:
        cleanup(identifier)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_chat_agent_entrypoint_contract_and_pagination(websocket_control, provider_endpoint, protocol):
    profile, identifier = conversation(provider_endpoint[0])
    identifiers = [identifier]
    try:
        for index in range(4):
            identifiers.append(chat("create", protocol, profileId=profile["id"], title=f"Fixture {index}")["id"])
        read_ids = []
        offset = 0
        while True:
            page = chat("list", protocol, limit=2, offset=offset)
            read_ids.extend(row["id"] for row in page["items"])
            if not page["hasMore"]:
                break
            offset += len(page["items"])
        assert set(identifiers) <= set(read_ids)
        assert len(read_ids) == len(set(read_ids))
        assert chat("list", protocol, offset=1000000, limit=2)["items"] == []
        bad = {"name": "Bad", "protocol": "openai", "baseUrl": provider_endpoint[0], "model": "fixture-model", "contextTokens": 4096, "maxOutputTokens": 4096}
        native_call(protocol, "chatControl", {"action": "profileSave", "profile": bad}, error="validation_failed")
        native_call(protocol, "chatControl", {"action": "send", "conversationId": identifier}, error="validation_failed")
        native_call(protocol, "chatControl", {"action": "read", "conversationId": "missing-conversation"}, error="chat_conversation_missing")
        for profile_change in ({"contextTokens": None}, {"maxOutputTokens": -1}, {"maximumRounds": 1.5}, {"unexpected": True}, {"baseUrl": "https://user:password@example.test/v1"}, {"baseUrl": "https://example.test/v1?key=REPLACE_ME"}):
            malformed = {**profile, **profile_change}
            native_call(protocol, "chatControl", {"action": "profileSave", "profile": malformed}, error="validation_failed")
    finally:
        for current in identifiers:
            cleanup(current)


def test_chat_agent_real_mcp_tool_continuation(websocket_control, provider_endpoint):
    endpoint, script, records = provider_endpoint
    script["tools"] = [("dikciz_device_state", {})]
    _, identifier = conversation(endpoint, outputTokenField="max_tokens")
    try:
        key = str(uuid.uuid4())
        first = send(identifier, request_key=key)
        assert send(identifier, request_key=key)["turn_id"] == first["turn_id"]
        native_call("http", "chatControl", {"action": "send", "conversationId": identifier, "text": "Different input", "requestKey": key}, error="chat_request_conflict")
        rows = wait_turn(identifier)
        assert [row["role"] for row in rows] == ["user", "assistant", "tool", "assistant"]
        assert any("batteryLevel" in json.dumps(message) for message in records[-1]["body"]["messages"] if message["role"] == "tool")
        assert records[0]["body"]["max_tokens"] == 4096
        assert "max_completion_tokens" not in records[0]["body"]
    finally:
        cleanup(identifier)


@pytest.mark.parametrize("policy", ["drop-oldest", "summarize"])
def test_chat_agent_context_policy_preserves_transcript(websocket_control, provider_endpoint, policy):
    endpoint, script, records = provider_endpoint
    script["text"] = "Fixture answer."
    _, identifier = conversation(endpoint, compaction=policy, contextTokens=12000, maxOutputTokens=512, summaryOutputTokens=256, safetyMarginTokens=256)
    try:
        for index in range(5):
            send(identifier, f"Fixture turn {index}\n" + "context " * 550)
            rows = wait_turn(identifier)
        assert len(rows) == 10
        assert len(records[-1]["body"]["messages"]) < 11
        summary = chat("read", conversationId=identifier)["conversation"]["summary"]
        assert bool(summary) is (policy == "summarize")
    finally:
        cleanup(identifier)


@pytest.mark.parametrize("provider,mode", [("openai", "unauthorized"), ("openai", "broken"), ("anthropic", "broken"), ("openai", "rate_limit")])
def test_chat_agent_provider_failures_are_visible(websocket_control, provider_endpoint, provider, mode):
    endpoint, script, _ = provider_endpoint
    script["mode"] = mode
    _, identifier = conversation(endpoint, provider)
    try:
        send(identifier)
        rows = wait_turn(identifier, "failed")
        assert any(block["kind"] == "error" for row in rows for block in json.loads(row["blocks"]))
        assert "EXAMPLE-DO-NOT-USE" not in json.dumps(rows)
    finally:
        cleanup(identifier)


def test_chat_agent_panel_markdown_and_modes(websocket_control, provider_endpoint):
    endpoint, script, _ = provider_endpoint
    _, identifier = conversation(endpoint)
    try:
        send(identifier)
        wait_turn(identifier)
        chat("show", conversationId=identifier)
        wait_for_physical_description_bounds("Chat message")
        wait_for_physical_description_text("Chat agent status", "Your phone assistant · tap Settings to configure a provider")
        nodes = physical_nodes()
        text = "\n".join(node.get("text", "") for node in nodes)
        assert "Bold text" in text and "**Bold text**" not in text
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "chat-half-screen.png")
        chat("fullscreen", conversationId=identifier)
        wait_for_physical_description_bounds("Chat message")
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "chat-full-screen.png")
        assert chat("read", conversationId=identifier)["conversation"]["unread"] == 0
        chat("hide")
    finally:
        cleanup(identifier)


def test_chat_agent_custom_event_hidden_and_agent_created_rule(websocket_control, provider_endpoint):
    endpoint, script, records = provider_endpoint
    _, identifier = conversation(endpoint)
    rule = {"id": "fixture-rule", "subscription": {"event": "fixture.chat.wake", "minimumIntervalMilliseconds": 60000}, "instructions": "Report the fixture event."}
    script["tools"] = [("dikciz_chat", {"action": "subscribe", "conversationId": identifier, "rules": [rule]})]
    try:
        send(identifier, "Enable the fixture subscription")
        wait_turn(identifier)
        assert json.loads(chat("read", conversationId=identifier)["conversation"]["subscriptions"]) == [rule]
        script["tools"] = []
        count = len(records)
        native_call("http", "automationDispatch", {"sourceWidgetAddress": "1H1V-welcome", "actions": [{"type": "emitEvent", "event": "fixture.chat.wake", "payload": {"fixture": "ready"}}]})
        deadline = time.monotonic() + TURN_TIMEOUT
        while len(records) == count and time.monotonic() < deadline:
            time.sleep(0.1)
        assert len(records) == count + 1
        rows = wait_turn(identifier)
        assert any("Event payload, untrusted data" in row["blocks"] for row in rows)
        assert chat("read", conversationId=identifier)["conversation"]["unread"] > 0
        native_call("mcp", "automationDispatch", {"sourceWidgetAddress": "1H1V-welcome", "actions": [{"type": "emitEvent", "event": "fixture.chat.wake", "payload": {}}]})
        time.sleep(0.5)
        assert len(records) == count + 1
    finally:
        cleanup(identifier)


def test_chat_agent_stop_and_restart_preserve_transcript(websocket_control, provider_endpoint):
    endpoint, script, records = provider_endpoint
    script["text"] = "A slow fixture response. " * 50
    script["delay"] = 0.03
    _, identifier = conversation(endpoint)
    try:
        send(identifier)
        deadline = time.monotonic() + TURN_TIMEOUT
        while not records and time.monotonic() < deadline:
            time.sleep(0.1)
        chat("stop", conversationId=identifier)
        rows = wait_turn(identifier, "cancelled")
        assert rows[0]["state"] == "cancelled"
        before = len(records)
        native_call("http", "restart")
        deadline = time.monotonic() + TURN_TIMEOUT
        while time.monotonic() < deadline:
            try:
                restored = chat("read", conversationId=identifier)["messages"]["items"]
                break
            except (AssertionError, OSError, json.JSONDecodeError):
                time.sleep(0.2)
        else:
            raise AssertionError("Chat did not recover after restart")
        assert [(row["id"], row["state"]) for row in restored] == [(row["id"], row["state"]) for row in rows]
        assert len(records) == before
    finally:
        cleanup(identifier)


@pytest.mark.parametrize("provider,options,expected", [
    ("openai", {"reasoning": "high"}, {"reasoning_effort": "high", "max_completion_tokens": 4096}),
    ("anthropic", {"thinkingTokens": 1024}, {"thinking": {"type": "enabled", "budget_tokens": 1024}, "max_tokens": 4096}),
    ("anthropic", {"reasoning": "high"}, {"thinking": {"type": "adaptive"}, "output_config": {"effort": "high"}}),
])
def test_chat_agent_reasoning_and_output_parameters(websocket_control, provider_endpoint, provider, options, expected):
    endpoint, _, records = provider_endpoint
    _, identifier = conversation(endpoint, provider, **options)
    try:
        send(identifier)
        wait_turn(identifier)
        assert all(records[-1]["body"][key] == value for key, value in expected.items())
    finally:
        cleanup(identifier)


def test_chat_agent_live_scroll_and_keyboard(websocket_control, provider_endpoint):
    endpoint, script, _ = provider_endpoint
    script["text"] = "\n\n".join(f"## Fixture section {index}\n\nParagraph with **streamed Markdown**." for index in range(40))
    _, identifier = conversation(endpoint)
    old_keyboard = device_command("settings get secure show_ime_with_hard_keyboard").strip()
    try:
        send(identifier)
        wait_turn(identifier)
        chat("show", conversationId=identifier)
        bounds = wait_for_physical_description_bounds("Chat messages")
        x, y = center(bounds)
        direct_swipe((x, y), (x, bounds["bottom"] - 30), 700)
        wait_for_physical_description_bounds("Jump to latest message")
        script["delay"] = 0.004
        send(identifier, "Continue the fixture")
        wait_turn(identifier)
        wait_for_physical_description_bounds("Jump to latest message")
        direct_tap(wait_for_physical_description_bounds("Jump to latest message"))
        deadline = time.monotonic() + TURN_TIMEOUT
        while any(node.get("content-desc") == "Jump to latest message" for node in physical_nodes()) and time.monotonic() < deadline:
            time.sleep(0.1)
        assert not any(node.get("content-desc") == "Jump to latest message" for node in physical_nodes())
        device_command("settings put secure show_ime_with_hard_keyboard 1")
        before = wait_for_physical_description_bounds("Chat message")
        direct_enter_text(before, "Keyboard fixture", description="Chat message")
        after = wait_for_physical_description_bounds("Chat message")
        assert after["bottom"] < before["bottom"]
        direct_hide_keyboard()
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "chat-stream-scroll.png")
    finally:
        if old_keyboard == "null":
            device_command("settings delete secure show_ime_with_hard_keyboard")
        else:
            device_command("settings put secure show_ime_with_hard_keyboard " + old_keyboard)
        cleanup(identifier)


def test_chat_agent_process_loss_does_not_replay_work(websocket_control, provider_endpoint):
    endpoint, script, records = provider_endpoint
    script["text"] = "Interrupted fixture. " * 100
    script["delay"] = 0.04
    _, identifier = conversation(endpoint)
    try:
        send(identifier)
        deadline = time.monotonic() + TURN_TIMEOUT
        while not records and time.monotonic() < deadline:
            time.sleep(0.1)
        assert records
        run_device_operation("app-stop", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
        run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
        rows = wait_turn(identifier, "interrupted")
        assert rows[0]["state"] == "interrupted"
        assert len(records) == 1
    finally:
        cleanup(identifier)


def test_chat_agent_hides_reads_external_app_and_reopens(websocket_control, provider_endpoint):
    endpoint, script, records = provider_endpoint
    _, identifier = conversation(endpoint)
    try:
        emulator_dikciz_accessibility_set(True)
        device_command(f"appops set {DIKCIZ_DEBUG_PACKAGE} SYSTEM_ALERT_WINDOW allow")
        chat("show", conversationId=identifier)
        wait_for_physical_description_bounds("Chat message")
        script["tools"] = [
            ("dikciz_chat", {"action": "hide"}),
            ("dikciz_intent", {"action": "android.settings.SETTINGS", "intentType": "activity"}),
            ("dikciz_accessibility_snapshot", {}),
            ("dikciz_chat", {"action": "show", "conversationId": identifier}),
        ]
        send(identifier, "Inspect the fixture settings app and return")
        rows = wait_turn(identifier)
        results = [row for row in rows if row["role"] == "tool"]
        assert len(results) == 4
        assert "com.android.settings" in results[2]["blocks"]
        wait_for_physical_description_bounds("Chat message")
        assert chat("status")["visible"] is True
        assert len(records) == 5
    finally:
        cleanup(identifier)
        device_command(f"appops set {DIKCIZ_DEBUG_PACKAGE} SYSTEM_ALERT_WINDOW default")
        emulator_dikciz_accessibility_set(False)
        run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)


def test_chat_agent_provider_change_clears_retained_credential(websocket_control, provider_endpoint):
    endpoint, _, records = provider_endpoint
    profile, identifier = conversation(endpoint)
    try:
        send(identifier)
        wait_turn(identifier)
        assert records[-1]["usedFixtureKey"]
        changed = {**profile, "baseUrl": endpoint.replace("/v1", "/proxy/v1")}
        chat("profileSave", profile=changed)
        send(identifier, "Use the changed endpoint")
        wait_turn(identifier)
        assert not records[-1]["usedFixtureKey"]
        assert records[-1]["path"].startswith("/proxy/")
    finally:
        cleanup(identifier)


def test_chat_agent_tool_restart_runs_after_result_is_recorded(websocket_control, provider_endpoint):
    endpoint, script, _ = provider_endpoint
    _, identifier = conversation(endpoint)
    previous = native_call("http", "deviceInfo")["application"]["instanceId"]
    script["tools"] = [("dikciz_restart", {})]
    try:
        send(identifier, "Restart the fixture launcher and continue")
        rows = wait_turn(identifier)
        assert [row["role"] for row in rows] == ["user", "assistant", "tool", "assistant"]
        wait_chat_ready()
        assert native_call("http", "deviceInfo")["application"]["instanceId"] != previous
    finally:
        cleanup(identifier)


@pytest.mark.parametrize("thinking", ["signed", "redacted", "omitted"])
def test_chat_anthropic_thinking_survives_tools_and_replay(websocket_control, provider_endpoint, thinking):
    endpoint, script, records = provider_endpoint
    script["thinking"] = thinking
    script["tools"] = [("dikciz_device_state", {})]
    _, identifier = conversation(endpoint, "anthropic", thinkingTokens=1024)
    try:
        send(identifier)
        rows = wait_turn(identifier)
        assert any(block["kind"] == "reasoning" for row in rows for block in json.loads(row["blocks"]))
        send(identifier, "Continue from the saved fixture turn")
        wait_turn(identifier)
        blocks = [block for message in records[-1]["body"]["messages"] for block in message.get("content", []) if isinstance(block, dict)]
        if thinking in ("signed", "omitted"):
            assert any(block.get("type") == "thinking" and block.get("signature") == "EXAMPLE-DO-NOT-USE-SIGNATURE" for block in blocks)
            if thinking == "omitted":
                assert any(block.get("type") == "thinking" and block.get("thinking") == "" for block in blocks)
        else:
            assert any(block == {"type": "redacted_thinking", "data": "EXAMPLE-DO-NOT-USE-OPAQUE"} for block in blocks)
    finally:
        cleanup(identifier)


def test_chat_agent_documentation_resources(websocket_control):
    from . import test_mcp_resources as resources
    resources.test_mcp_resources_walk_all_pages_and_read_each_section(websocket_control)
    resources.test_mcp_resources_reject_invalid_uris_cursors_and_fields()
    resources.test_mcp_resources_enforce_origin_and_optional_session()
    for section in ("chat", "chat-context", "chat-events"):
        assert resources.read_resource("guides/" + section)


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
def test_chat_agent_screenshot_is_a_provider_image(websocket_control, provider_endpoint, provider):
    endpoint, script, records = provider_endpoint
    script["tools"] = [("dikciz_screenshot", {})]
    _, identifier = conversation(endpoint, provider)
    try:
        send(identifier, "Inspect the fixture screen")
        rows = wait_turn(identifier)
        body = records[-1]["body"]
        parts = [part for message in body["messages"] for part in message.get("content", []) if isinstance(part, dict)]
        if provider == "openai":
            assert any(part.get("type") == "image_url" and part["image_url"]["url"].startswith("data:image/png;base64,") for part in parts)
        else:
            images = [image for part in parts if part.get("type") == "tool_result" for image in part.get("content", []) if isinstance(image, dict)]
            assert any(image.get("type") == "image" and image["source"]["media_type"] == "image/png" and image["source"]["data"] for image in images)
        assert not any("pngBase64" in row["blocks"] for row in rows)
    finally:
        cleanup(identifier)


@pytest.mark.parametrize("limit", ["context", "timeout", "rounds"])
def test_chat_agent_limits_report_terminal_failure(websocket_control, provider_endpoint, limit):
    endpoint, script, records = provider_endpoint
    options = {}
    text = "Exercise the fixture limit"
    if limit == "context":
        options = {"contextTokens": 8192, "maxOutputTokens": 512, "summaryOutputTokens": 128, "safetyMarginTokens": 128}
        text = "Oversized fixture input " * 1000
    elif limit == "timeout":
        options = {"timeoutMilliseconds": 1000}
        script["text"] = "Slow fixture output " * 500
        script["delay"] = 0.01
    else:
        options = {"maximumRounds": 1}
        script["tools"] = [("dikciz_device_state", {})]
    _, identifier = conversation(endpoint, **options)
    try:
        send(identifier, text)
        rows = wait_turn(identifier, "failed")
        assert json.loads(rows[-1]["blocks"])[0]["kind"] == "error"
        if limit == "context":
            assert not records
        elif limit == "rounds":
            assert len(records) == 1 and any(row["role"] == "tool" and row["state"] == "complete" for row in rows)
        else:
            assert "timed out" in rows[-1]["blocks"]
    finally:
        cleanup(identifier)


def test_chat_agent_provider_settings_native_ui(websocket_control, provider_endpoint):
    endpoint, _, _ = provider_endpoint
    _, identifier = conversation(endpoint)
    name = "UI-fixture-" + uuid.uuid4().hex[:8]
    previous_keyboard = device_command("settings get secure show_ime_with_hard_keyboard").strip()

    def field(description):
        for _ in range(15):
            try:
                return physical_description_bounds(description)
            except AssertionError:
                bounds = physical_description_bounds("Chat provider form")
                x, _ = center(bounds)
                direct_swipe((x, bounds["bottom"] - 30), (x, bounds["top"] + 30), 500)
        raise AssertionError("Provider field was not reachable: " + description)

    try:
        device_command("settings put secure show_ime_with_hard_keyboard 1")
        chat("show", conversationId=identifier)
        direct_tap(wait_for_physical_description_bounds("Chat provider settings"))
        direct_tap(wait_for_physical_button_bounds("Add provider"))
        for description, value in (("Provider name", name), ("API base URL", endpoint),
                ("API key. Blank retains it for the same host and protocol", "EXAMPLE-DO-NOT-USE")):
            direct_enter_text(field(description), value)
            direct_hide_keyboard()
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / "chat-provider-settings.png")
        direct_tap(wait_for_physical_button_bounds("Save"))
        deadline = time.monotonic() + TURN_TIMEOUT
        saved = []
        while time.monotonic() < deadline:
            saved = [profile for profile in chat("profiles")["profiles"] if profile["name"] == name]
            if saved:
                break
            time.sleep(0.1)
        assert len(saved) == 1 and saved[0]["baseUrl"] == endpoint
        assert saved[0]["model"] == "" and saved[0]["compaction"] == "drop-oldest"
        assert "apiKey" not in saved[0]
    finally:
        cleanup(identifier)
        if previous_keyboard == "null":
            device_command("settings delete secure show_ime_with_hard_keyboard")
        else:
            device_command("settings put secure show_ime_with_hard_keyboard " + previous_keyboard)


def test_chat_agent_deleting_visible_conversation_clears_panel(websocket_control, provider_endpoint):
    _, identifier = conversation(provider_endpoint[0])
    deleted = False
    try:
        chat("show", conversationId=identifier)
        wait_for_physical_description_bounds("Chat message")
        chat("delete", conversationId=identifier)
        deleted = True
        device_command("input keyevent KEYCODE_HOME")
        chat("show")
        wait_for_physical_description_bounds("Chat message")
        assert chat("status")["visible"]
        assert identifier not in {row["id"] for row in chat("list")["items"]}
        direct_tap(wait_for_physical_description_bounds("Choose conversation"))
        wait_for_physical_text_bounds("Conversations")
        device_command("input keyevent KEYCODE_BACK")
    finally:
        if not deleted:
            cleanup(identifier)
        else:
            chat("hide")


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
@pytest.mark.parametrize("wrapper,arguments,code", [
    ("phone_resource", {"uri": "dikciz://docs/missing-section"}, "resource_not_found"),
    ("phone_resources", {"cursor": "invalid-cursor"}, "validation_failed"),
    ("phone_tools", {"name": "dikciz_device_state", "arguments_json": "{}"}, "validation_failed"),
    ("phone_call", {"name": "dikciz_device_state", "arguments_json": "{"}, "validation_failed"),
    ("missing_wrapper", {}, "unknown_tool"),
    ("phone_call", {"name": "dikciz_nonexistent_tool", "arguments_json": "{}"}, "unknown_tool"),
])
def test_chat_agent_recovery_wrapper_errors(websocket_control, provider_endpoint, provider, wrapper, arguments, code):
    endpoint, script, records = provider_endpoint
    script["wrapper_calls"] = [(wrapper, arguments), ("phone_call", {"name": "dikciz_device_state", "arguments_json": "{}"})]
    script["text"] = "Recovered after the rejected tool request."
    _, identifier = conversation(endpoint, provider)
    try:
        send(identifier)
        rows = wait_turn(identifier)
        tools = [row for row in rows if row["role"] == "tool"]
        assert len(tools) == 2
        first = json.loads(json.loads(tools[0]["blocks"])[0]["text"])
        assert first["isError"] is True
        assert first.get("structuredContent", first)["code"] == code
        assert "batteryLevel" in tools[1]["blocks"]
        if wrapper == "phone_tools":
            assert "phone_call" in first["message"]
        assert len(records) == 3
        assert_tool_pairing(records[-1]["body"])
        assert json.loads(rows[-1]["blocks"])[0]["text"] == script["text"]
    finally:
        cleanup(identifier)


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
@pytest.mark.parametrize("policy", ["drop-oldest", "summarize"])
@pytest.mark.parametrize("failure", ["unauthorized", "broken", "rounds", "cancelled"])
def test_chat_agent_recovery_failed_turn_context(websocket_control, provider_endpoint, provider, policy, failure):
    endpoint, script, records = provider_endpoint
    options = {"compaction": policy}
    if failure == "rounds":
        options["maximumRounds"] = 1
        script["tools"] = [("dikciz_device_state", {})]
    else:
        script["mode"] = failure if failure != "cancelled" else "text"
    if failure == "cancelled":
        script["delay"] = 0.03
        script["text"] = "Pending answer " * 100
    profile, identifier = conversation(endpoint, provider, **options)
    marker = "Remember fixture marker " + uuid.uuid4().hex + " café"
    try:
        send(identifier, marker)
        if failure == "cancelled":
            deadline = time.monotonic() + TURN_TIMEOUT
            while not records and time.monotonic() < deadline:
                time.sleep(0.1)
            assert records
            chat("stop", conversationId=identifier)
        wait_turn(identifier, "cancelled" if failure == "cancelled" else "failed")
        if failure == "rounds":
            chat("profileSave", profile={**profile, "maximumRounds": 32})
        script.update(mode="text", tools=[], text="Continuing the earlier request.", delay=0.002)
        before = len(records)
        send(identifier, "Continue from my earlier request")
        rows = wait_turn(identifier)
        body = records[before]["body"]
        assert_tool_pairing(body)
        assert marker in json.dumps(body["messages"], ensure_ascii=False)
        assert len(records) == before + 1
        if failure == "rounds":
            assert "batteryLevel" in json.dumps(body["messages"])
            assert len([row for row in rows if row["role"] == "tool"]) == 1
        assert json.loads(rows[-1]["blocks"])[0]["text"] == script["text"]
    finally:
        cleanup(identifier)


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
def test_chat_agent_recovery_interrupted_tool_has_unknown_outcome(websocket_control, provider_endpoint, provider):
    endpoint, script, records = provider_endpoint
    script["tools"] = [("dikciz_device_state", {}), ("dikciz_shell", {"root": False, "command": "sleep 30", "timeoutMilliseconds": 35000})]
    _, identifier = conversation(endpoint, provider)
    try:
        send(identifier, "Remember the interrupted fixture request")
        deadline = time.monotonic() + TURN_TIMEOUT
        while time.monotonic() < deadline:
            rows = chat("read", conversationId=identifier)["messages"]["items"]
            if rows and rows[-1]["role"] == "tool" and rows[-1]["state"] == "executing":
                blocks = json.loads(rows[-1]["blocks"])
                if blocks[0]["name"] == "phone_call" and len([row for row in rows if row["role"] == "tool"]) == 2:
                    break
            time.sleep(0.1)
        else:
            raise AssertionError("Fixture shell never reached executing state")
        run_device_operation("app-stop", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
        script.update(tools=[], text="Inspecting earlier outcomes before continuing.")
        run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
        wait_chat_ready()
        before = len(records)
        send(identifier, "Continue without repeating the interrupted command")
        wait_turn(identifier)
        body = records[before]["body"]
        assert_tool_pairing(body)
        encoded = json.dumps(body["messages"])
        assert "Remember the interrupted fixture request" in encoded
        assert "batteryLevel" in encoded
        assert "tool_outcome_unknown" in encoded
        assert len(records) == before + 1
    finally:
        cleanup(identifier)


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
def test_chat_agent_recovery_cancelled_queue_retains_owner_text(websocket_control, provider_endpoint, provider):
    endpoint, script, records = provider_endpoint
    script.update(delay=0.03, text="Pending fixture response " * 100)
    _, identifier = conversation(endpoint, provider)
    marker = "Queued fixture request " + uuid.uuid4().hex
    try:
        send(identifier, "Start the first fixture turn")
        deadline = time.monotonic() + TURN_TIMEOUT
        while not records and time.monotonic() < deadline:
            time.sleep(0.1)
        assert records
        send(identifier, marker)
        chat("stop", conversationId=identifier)
        rows = wait_turn(identifier, "cancelled")
        assert rows[-1]["role"] == "user"
        script.update(delay=0.002, text="Continuing after cancellation.")
        before = len(records)
        send(identifier, "Remember my queued request")
        wait_turn(identifier)
        assert marker in json.dumps(records[before]["body"]["messages"])
    finally:
        cleanup(identifier)


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
@pytest.mark.parametrize("policy", ["drop-oldest", "summarize"])
def test_chat_agent_recovery_queued_history_keeps_trailing_results(websocket_control, provider_endpoint, provider, policy):
    endpoint, script, records = provider_endpoint
    script.update(delay=1, text="Fixture acknowledged.", tools=[("dikciz_device_info", {})])
    options = {"compaction": policy}
    if policy == "summarize":
        options.update(contextTokens=12000, maxOutputTokens=512, summaryOutputTokens=256, safetyMarginTokens=256)
    _, identifier = conversation(endpoint, provider, **options)
    marker = "Queued fixture marker " + uuid.uuid4().hex
    try:
        send(identifier, "Inspect the fixture device. " + "fixture " * 250)
        deadline = time.monotonic() + TURN_TIMEOUT
        while not records and time.monotonic() < deadline:
            time.sleep(0.05)
        assert records
        send(identifier, marker)
        script["delay"] = 0.002
        rows = wait_turn(identifier)
        owners = [row for row in rows if row["role"] == "user"]
        tools = [row for row in rows if row["role"] == "tool"]
        assert len(owners) == 2 and len(tools) == 1
        assert owners[1]["id"] < tools[0]["id"], "Fixture did not queue before the first tool result"
        assert marker not in json.dumps(records[1]["body"]["messages"])
        second = records[2]["body"]
        assert_tool_pairing(second)
        assert "versionName" in json.dumps(second["messages"])
        if policy == "summarize":
            script["tools"] = []
            send(identifier, "Continue the fixture. " + "context " * 375)
            wait_turn(identifier)
            assert chat("read", conversationId=identifier)["conversation"]["summary"]
            assert marker in json.dumps(records[-1]["body"]["messages"])
            assert_tool_pairing(records[-1]["body"])
    finally:
        cleanup(identifier)


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
@pytest.mark.parametrize("entrypoint", PROTOCOLS)
def test_chat_provider_models_connection_without_model(websocket_control, provider_endpoint, entrypoint, provider):
    endpoint, script, _ = provider_endpoint
    script["models"] = ["fixture-small", "fixture-large"]
    before = len(chat("profiles")["profiles"])
    profile = chat("profileSave", entrypoint, profile={"name": "Shared fixture", "protocol": provider, "baseUrl": endpoint, "apiKey": "EXAMPLE-DO-NOT-USE"})
    identifier = chat("create", entrypoint, profileId=profile["id"])["id"]
    try:
        assert {m["id"] for m in chat("models", entrypoint, profileId=profile["id"])["models"]} == set(script["models"])
        native_call(entrypoint, "chatControl", {"action": "send", "conversationId": identifier, "text": "No selected model", "requestKey": str(uuid.uuid4())}, error="validation_failed")
        assert chat("read", entrypoint, conversationId=identifier)["messages"]["items"] == []
        chat("configure", entrypoint, conversationId=identifier, settings={"model": "fixture-small", "contextTokens": 32768, "maxOutputTokens": 1024})
        settings = chat("settings", entrypoint, conversationId=identifier)["settings"]
        assert settings["model"] == "fixture-small" and settings["maxOutputTokens"] == 1024
        chat("configure", entrypoint, conversationId=identifier, settings={"model": "fixture-large"})
        assert chat("settings", entrypoint, conversationId=identifier)["settings"]["maxOutputTokens"] == 1024
        assert len(chat("profiles")["profiles"]) == before + 1
        assert "EXAMPLE-DO-NOT-USE" not in json.dumps(chat("read", entrypoint, conversationId=identifier))
    finally:
        cleanup(identifier)


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
def test_chat_provider_models_independent_requests(websocket_control, provider_endpoint, provider):
    endpoint, script, records = provider_endpoint
    script["text"] = "Fixture response."
    profile, first = conversation(endpoint, provider)
    second = chat("create", profileId=profile["id"])["id"]
    try:
        chat("configure", conversationId=first, settings={"model": "fixture-small", "contextTokens": 32768, "maxOutputTokens": 512, "reasoning": "low"})
        chat("configure", conversationId=second, settings={"model": "fixture-large", "contextTokens": 65536, "maxOutputTokens": 1024, "reasoning": "high"})
        for identifier, model, output, effort in [(first,"fixture-small",512,"low"),(second,"fixture-large",1024,"high")]:
            send(identifier)
            wait_turn(identifier)
            body=records[-1]["body"]
            assert body["model"] == model
            assert body.get("max_tokens",body.get("max_completion_tokens")) == output
            if provider == "openai": assert body["reasoning_effort"] == effort
            else: assert body["output_config"]["effort"] == effort
            assert records[-1]["usedFixtureKey"]
        chat("configure", conversationId=first, settings={"model": "fixture-large"})
        assert chat("read", conversationId=first)["settings"]["maxOutputTokens"] == 512
        assert chat("read", conversationId=second)["settings"]["maxOutputTokens"] == 1024
        assert next(p for p in chat("profiles")["profiles"] if p["id"] == profile["id"])["model"] == "fixture-model"
    finally:
        cleanup(first); cleanup(second)


def test_chat_provider_models_settings_validation_is_atomic(websocket_control, provider_endpoint):
    _, identifier = conversation(provider_endpoint[0])
    try:
        chat("configure", conversationId=identifier, settings={"model":"fixture-small", "maxOutputTokens": 512})
        previous=chat("settings", conversationId=identifier)
        for patch in [{"apiKey":"EXAMPLE-DO-NOT-USE"},{"baseUrl":"https://example.test"},{"protocol":"anthropic"},{"unexpected":True},{"contextTokens":None},{"maxOutputTokens":-1},{"maxOutputTokens":131072},{"maxOutputTokens":1.5},{"thinkingTokens":1024}]:
            native_call("http","chatControl",{"action":"configure","conversationId":identifier,"settings":patch},error="validation_failed")
            assert chat("settings",conversationId=identifier)==previous
        native_call("http","chatControl",{"action":"configure","conversationId":identifier},error="validation_failed")
        native_call("http","chatControl",{"action":"configure","conversationId":identifier,"settings":None},error="validation_failed")
        send(identifier); wait_turn(identifier)
        native_call("http","restart"); wait_chat_ready()
        assert chat("settings",conversationId=identifier)==previous
    finally:
        cleanup(identifier)



def test_chat_provider_models_native_dropdown_and_tokens(websocket_control, provider_endpoint):
    endpoint, script, records = provider_endpoint
    script.update(models=["fixture-model","fixture-alternate"], text="Fixture answer.")
    profile, identifier = conversation(endpoint)
    try:
        chat("show",conversationId=identifier)
        wait_for_physical_description_bounds("Chat model")
        direct_tap(physical_description_bounds("Chat model"))
        try:
            target = wait_for_physical_text_bounds("fixture-alternate")
        except AssertionError:
            run_device_operation("screenshot")
            shutil.copyfile(ARTIFACT_DIRECTORY/"screenshot.png", ARTIFACT_DIRECTORY/"chat-model-dropdown-failed.png")
            raise
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY/"screenshot.png", ARTIFACT_DIRECTORY/"chat-model-dropdown.png")
        direct_tap(target)
        deadline=time.monotonic()+TURN_TIMEOUT
        while chat("settings",conversationId=identifier)["settings"]["model"]!="fixture-alternate" and time.monotonic()<deadline:
            time.sleep(0.1)
        assert chat("settings",conversationId=identifier)["settings"]["model"]=="fixture-alternate"
        direct_tap(wait_for_physical_description_bounds("Chat model settings"))
        direct_tap(wait_for_physical_description_bounds("Read-only phone access"))
        direct_enter_text(wait_for_physical_description_bounds("Total context tokens"),"65536")
        direct_hide_keyboard()
        direct_enter_text(physical_description_bounds("Maximum output and reserved reply tokens"),"1024")
        direct_hide_keyboard()
        run_device_operation("screenshot")
        shutil.copyfile(ARTIFACT_DIRECTORY/"screenshot.png",ARTIFACT_DIRECTORY/"chat-model-settings.png")
        direct_tap(wait_for_physical_button_bounds("Save"))
        deadline=time.monotonic()+TURN_TIMEOUT
        while chat("settings",conversationId=identifier)["settings"]["maxOutputTokens"]!=1024 and time.monotonic()<deadline:
            time.sleep(0.1)
        selected=chat("settings",conversationId=identifier)["settings"]
        assert selected["contextTokens"]==65536 and selected["maxOutputTokens"]==1024
        assert selected["readOnly"] is True
        send(identifier);wait_turn(identifier)
        assert records[-1]["body"]["model"]=="fixture-alternate"
        assert records[-1]["body"]["max_completion_tokens"]==1024
        assert next(p for p in chat("profiles")["profiles"] if p["id"]==profile["id"])["maxOutputTokens"]==4096
    finally:
        cleanup(identifier)


def test_chat_provider_models_busy_and_provider_change(websocket_control,provider_endpoint):
    endpoint,script,records=provider_endpoint
    profile,identifier=conversation(endpoint)
    other=chat("profileSave",profile={"name":"Other fixture","protocol":"openai","baseUrl":endpoint,"model":"fixture-other"})
    try:
        chat("configure",conversationId=identifier,settings={"model":"fixture-selected","maxOutputTokens":512,"readOnly":True})
        before=chat("settings",conversationId=identifier)
        script.update(text="Pending fixture response "*100,delay=0.03)
        send(identifier)
        deadline=time.monotonic()+TURN_TIMEOUT
        while not records and time.monotonic()<deadline:time.sleep(0.1)
        assert records
        native_call("mcp","chatControl",{"action":"configure","conversationId":identifier,"settings":{"model":"fixture-other","readOnly":False}},error="chat_busy")
        assert chat("settings",conversationId=identifier)==before
        chat("stop",conversationId=identifier);wait_turn(identifier,"cancelled")
        deadline=time.monotonic()+TURN_TIMEOUT
        while chat("status")["busy"] and time.monotonic()<deadline:time.sleep(0.1)
        chat("configure",conversationId=identifier,profileId=other["id"])
        selected=chat("settings",conversationId=identifier)
        assert selected["settings"]["model"]=="fixture-other"
        assert selected["settings"]["maxOutputTokens"]==4096 and selected["overrides"]=={"readOnly":True}
        assert selected["settings"]["readOnly"] is True
    finally:
        cleanup(identifier)



@pytest.mark.parametrize("provider", ["openai", "anthropic"])
def test_chat_read_only_executor(websocket_control, provider_endpoint, provider):
    endpoint, script, records = provider_endpoint
    path = "/sdcard/Dikciz/chat-readonly-" + uuid.uuid4().hex + ".txt"
    _, identifier = conversation(endpoint, provider)
    writes = [
        ("dikciz_file_write", {"path": path, "dataBase64": "eA==", "overwrite": True}),
        ("dikciz_file_write", {"path": path, "dataBase64": "eA==", "readOnly": False}),
        ("dikciz_shell", {"root": False, "command": "echo x", "timeoutMilliseconds": 1000}),
        ("dikciz_chat", {"action": "configure", "conversationId": identifier, "settings": {"readOnly": False}}),
        ("dikciz_device_info", {"autoRequestPermissions": True}),
        ("dikciz_restart", {}),
        ("dikciz_job_start", {}),
        ("dikciz_automation_dispatch", {}),
        ("dikciz_request_device_access", {"requirement": "contacts"}),
    ]
    script.update(text="Read-only fixture finished.", tools=writes + [("dikciz_device_state", {})])
    try:
        assert chat("settings", conversationId=identifier)["settings"]["readOnly"] is False
        chat("configure", conversationId=identifier, settings={"readOnly": True})
        send(identifier)
        rows = wait_turn(identifier)
        tools = [json.loads(json.loads(row["blocks"])[0]["text"]) for row in rows if row["role"] == "tool"]
        assert len(tools) == len(writes) + 1
        for result in tools[:-1]:
            assert result["isError"] is True
            assert result.get("structuredContent", result)["code"] == "read_only_violation"
        assert "batteryLevel" in json.dumps(tools[-1])
        assert chat("settings", conversationId=identifier)["settings"]["readOnly"] is True
        native_call("http", "fileRead", {"path": path}, error="file_not_found")
        assert_tool_pairing(records[-1]["body"])
        chat("configure", conversationId=identifier, settings={"readOnly": False})
        script["tools"] = [("dikciz_file_write", {"path": path, "dataBase64": "eA=="})]
        # A new conversation avoids counting tool results from the earlier turn.
        writable = chat("create", profileId=chat("read", conversationId=identifier)["conversation"]["profile_id"])["id"]
        try:
            send(writable)
            results = wait_turn(writable)
            assert len([row for row in results if row["role"] == "tool"]) == 1
            assert native_call("http", "fileRead", {"path": path})["dataBase64"] == "eA=="
        finally:
            cleanup(writable)
            native_call("http", "fileManage", {"action": "delete", "path": path})
    finally:
        cleanup(identifier)


@pytest.mark.parametrize("entrypoint", PROTOCOLS)
def test_chat_read_only_discovery_and_validation(websocket_control, provider_endpoint, entrypoint):
    endpoint, script, _ = provider_endpoint
    _, identifier = conversation(endpoint)
    try:
        previous = chat("settings", conversationId=identifier)
        for value in [None, "true", 1, {}, []]:
            native_call(entrypoint, "chatControl", {"action": "configure", "conversationId": identifier, "settings": {"readOnly": value}}, error="validation_failed")
            assert chat("settings", conversationId=identifier) == previous
        chat("configure", entrypoint, conversationId=identifier, settings={"readOnly": True})
        chat("configure", entrypoint, conversationId=identifier, settings={"model": "fixture-alternate"})
        assert chat("settings", conversationId=identifier)["settings"]["readOnly"] is True
        script.update(text="Discovery done.", wrapper_calls=[("phone_tools", {"query": "file"}), ("phone_tools", {"query": "shell"}), ("phone_resources", {})])
        send(identifier)
        rows = wait_turn(identifier)
        results = [json.loads(json.loads(row["blocks"])[0]["text"]) for row in rows if row["role"] == "tool"]
        assert "dikciz_file_read" in json.dumps(results[0])
        assert "dikciz_file_write" not in json.dumps(results[0])
        assert all(tool["annotations"]["readOnlyHint"] for result in results[:2] for tool in result["tools"])
        assert "dikciz_shell" not in {tool["name"] for tool in results[1]["tools"]}
        assert all("autoRequestPermissions" not in tool["inputSchema"]["properties"] for result in results[:2] for tool in result["tools"])
        assert "resources" in results[2]
        native_call("http", "restart")
        wait_chat_ready()
        assert chat("settings", conversationId=identifier)["settings"]["readOnly"] is True
    finally:
        cleanup(identifier)



def test_chat_provider_models_manual_fallback(websocket_control, provider_endpoint):
    endpoint, script, records = provider_endpoint
    script.update(mode="models_unauthorized", text="Manual model accepted.")
    profile = chat("profileSave", profile={"name": "Manual fixture", "protocol": "openai", "baseUrl": endpoint})
    identifier = chat("create", profileId=profile["id"])["id"]
    try:
        chat("show", conversationId=identifier)
        direct_tap(wait_for_physical_button_bounds("Close"))
        direct_tap(wait_for_physical_description_bounds("Chat model settings"))
        direct_enter_text(wait_for_physical_description_bounds("Model ID"), "fixture-manual")
        direct_hide_keyboard()
        direct_tap(wait_for_physical_button_bounds("Save"))
        deadline = time.monotonic() + TURN_TIMEOUT
        while chat("settings", conversationId=identifier)["settings"]["model"] != "fixture-manual" and time.monotonic() < deadline:
            time.sleep(0.1)
        assert chat("settings", conversationId=identifier)["settings"]["model"] == "fixture-manual"
        send(identifier)
        wait_turn(identifier)
        assert records[-1]["body"]["model"] == "fixture-manual"
    finally:
        cleanup(identifier)
