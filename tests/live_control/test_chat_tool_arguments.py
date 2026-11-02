"""Provider argument framing and durable conversation recovery regressions."""

import base64
import json
import shlex
import uuid

import pytest

from . import *
from . import test_chat_agent as agent
from .test_chat_agent import provider_endpoint
from .test_native_data import PROTOCOLS, native_call

REJECTED_ARGUMENTS_KEY = "dikcizRejectedArguments"
VALID_CASES = [
    ("phone_resources", {}, []),
    ("phone_resources", {}, ["{}"]),
    ("phone_resources", {}, ["{", "}"]),
    ("phone_resources", {}, ["", "{}", ""]),
    ("phone_tools", {"query": "widget"}, ['{"qu', 'ery":"wid', 'get"}']),
    ("phone_tools", {"query": "café"}, ['{"query":"c', 'afé"}']),
]
MALFORMED_CASES = [
    " ", "{", "[]", "null", "true", '"fixture"', "{'unexpected':1}",
    '{"cursor":foo}', '{"cursor":01}', '{"cursor":NaN}', '{"cursor":{"value":[foo]}}',
]
SCHEMA_CASES = [
    ("phone_tools", {"query": None}),
    ("phone_tools", {"query": "file", "offset": 0}),
    ("phone_tools", {"query": "file", "unknown": "field"}),
    ("phone_call", {"name": "dikciz_device_state", "arguments_json": {}}),
    ("phone_call", {"name": 1, "arguments_json": "{}"}),
    ("phone_resources", {"cursor": False}),
    ("phone_resource", {"uri": None}),
]


def provider_calls(body):
    calls = []
    for message in body["messages"]:
        for call in message.get("tool_calls", []):
            calls.append((call["function"]["name"], json.loads(call["function"]["arguments"])))
        content = message.get("content", [])
        if isinstance(content, list):
            calls.extend(
                (part["name"], part["input"]) for part in content
                if isinstance(part, dict) and part.get("type") == "tool_use"
            )
    return calls


def provider_results(body):
    results = []
    for message in body["messages"]:
        if message.get("role") == "tool":
            results.append(json.loads(message["content"]))
        content = message.get("content", [])
        if isinstance(content, list):
            for part in content:
                if isinstance(part, dict) and part.get("type") == "tool_result":
                    results.extend(json.loads(item["text"]) for item in part["content"] if item.get("type") == "text")
    return results


def tool_results(rows):
    return [
        json.loads(block["text"]) for row in rows if row["role"] == "tool"
        for block in json.loads(row["blocks"])
    ]


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
def test_chat_tool_arguments_empty_delta(websocket_control, provider_endpoint, provider, subtests):
    endpoint, script, records = provider_endpoint
    script.update(wrapper_calls=[("phone_resources", {})], argument_chunks=[""], text="Resource discovery completed.")
    for entrypoint in PROTOCOLS:
        with subtests.test(entrypoint=entrypoint):
            records.clear()
            _, identifier = agent.conversation(endpoint, provider)
            try:
                agent.chat(
                    "send", protocol=entrypoint, conversationId=identifier,
                    text="Discover documentation.", requestKey=str(uuid.uuid4()),
                )
                rows = agent.wait_turn(identifier)
                results = tool_results(rows)
                assert len(results) == 1 and results[0].get("isError") is not True
                assert len(records) == 2
                agent.assert_tool_pairing(records[-1]["body"])
                agent.send(identifier, "Continue the documentation conversation.")
                agent.wait_turn(identifier)
                assert len(records) == 3
                assert "Discover documentation." in json.dumps(records[-1]["body"])
            finally:
                agent.cleanup(identifier)


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
def test_chat_tool_arguments_valid_framing(websocket_control, provider_endpoint, provider, subtests):
    endpoint, script, records = provider_endpoint
    for wrapper, arguments, chunks in VALID_CASES:
        with subtests.test(wrapper=wrapper, chunks=chunks):
            records.clear()
            script.update(wrapper_calls=[(wrapper, arguments)], argument_chunks=chunks, text="Discovery complete.")
            _, identifier = agent.conversation(endpoint, provider)
            try:
                agent.send(identifier)
                rows = agent.wait_turn(identifier)
                assert all(result.get("isError") is not True for result in tool_results(rows))
                assert provider_calls(records[-1]["body"]) == [(wrapper, arguments)]
                agent.assert_tool_pairing(records[-1]["body"])
            finally:
                agent.cleanup(identifier)


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
def test_chat_tool_arguments_malformed_recovery(websocket_control, provider_endpoint, provider, subtests):
    endpoint, script, records = provider_endpoint
    for raw in MALFORMED_CASES:
        with subtests.test(raw=raw):
            records.clear()
            script.update(wrapper_calls=[("phone_resources", {})], argument_chunks=[raw], text="Rejected input safely.")
            _, identifier = agent.conversation(endpoint, provider)
            try:
                agent.send(identifier)
                rows = agent.wait_turn(identifier)
                results = tool_results(rows)
                assert len(results) == 1
                assert results[0]["isError"] is True and results[0]["code"] == "validation_failed"
                call = next(
                    json.loads(block["text"]) for row in rows for block in json.loads(row["blocks"])
                    if block["kind"] == "tool"
                )
                assert call["arguments"] == raw
                assert provider_calls(records[-1]["body"]) == [("phone_resources", {REJECTED_ARGUMENTS_KEY: raw})]
                agent.assert_tool_pairing(records[-1]["body"])
                agent.send(identifier, "Continue after the rejected input.")
                agent.wait_turn(identifier)
                assert len(records) == 3
            finally:
                agent.cleanup(identifier)


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
def test_chat_tool_arguments_missing_required_recovery(websocket_control, provider_endpoint, provider):
    endpoint, script, records = provider_endpoint
    script.update(wrapper_calls=[("phone_call", {})], argument_chunks=[""], text="Missing fields rejected.")
    _, identifier = agent.conversation(endpoint, provider)
    try:
        agent.send(identifier)
        rows = agent.wait_turn(identifier)
        result = tool_results(rows)[0]
        assert result["isError"] is True and result["code"] == "validation_failed"
        assert provider_calls(records[-1]["body"]) == [("phone_call", {})]
        agent.send(identifier, "Continue without executing a missing request.")
        agent.wait_turn(identifier)
        assert len(records) == 3
    finally:
        agent.cleanup(identifier)


def database(sql, **parameters):
    command = ["run-as", agent.DIKCIZ_DEBUG_PACKAGE, "sqlite3", "-json"]
    for name, value in parameters.items():
        expression = "CAST(X'" + str(value).encode().hex() + "' AS TEXT)"
        command.extend(["-cmd", '.parameter set @' + name + ' "' + expression + '"'])
    command.extend(["databases/dikciz-chat.sqlite", sql])
    output = agent.device_command(shlex.join(command)).strip()
    return json.loads(output) if output else []


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
@pytest.mark.parametrize("policy", ["drop-oldest", "summarize"])
def test_chat_tool_arguments_legacy_replay(websocket_control, provider_endpoint, provider, policy, subtests):
    endpoint, script, records = provider_endpoint
    if provider == "anthropic":
        script["thinking"] = "signed"
    options = {"thinkingTokens": 1024} if provider == "anthropic" else {}
    for raw in ("", "{"):
        with subtests.test(raw=raw):
            records.clear()
            path = "/sdcard/Dikciz/argument-replay-" + uuid.uuid4().hex + ".txt"
            data = base64.b64encode(b"fixture write completed once").decode()
            script.update(
                tools=[("dikciz_file_write", {"path": path, "dataBase64": data})],
                text="The fixture write completed.",
            )
            _, identifier = agent.conversation(endpoint, provider, compaction=policy, **options)
            created = False
            stopped = False
            try:
                agent.send(identifier, "Keep the completed fixture operation in history.")
                first = agent.wait_turn(identifier)
                assert native_call("http", "fileRead", {"path": path})["dataBase64"] == data
                created = True
                agent.chat("configure", conversationId=identifier, settings={"readOnly": True})
                agent.run_device_operation("app-stop", PACKAGE_NAME=agent.DIKCIZ_DEBUG_PACKAGE)
                stopped = True
                calls = database(
                    """SELECT m.id, j.fullkey AS path
                    FROM messages m, json_tree(CASE WHEN m.document='' THEN '{}' ELSE m.document END) j
                    WHERE m.conversation_id=@conversation AND m.role='assistant' AND j.key='args'""",
                    conversation=identifier,
                )
                assert len(calls) == 1
                database(
                    """UPDATE messages SET document=json_set(document,@path,@arguments)
                    WHERE id=@row AND conversation_id=@conversation""",
                    path=calls[0]["path"], arguments=raw, row=calls[0]["id"], conversation=identifier,
                )
                database(
                    """UPDATE messages SET state='failed'
                    WHERE conversation_id=@conversation AND (role='user' OR
                    id=(SELECT max(id) FROM messages WHERE conversation_id=@conversation))""",
                    conversation=identifier,
                )
                agent.run_device_operation("home-start", PACKAGE_NAME=agent.DIKCIZ_DEBUG_PACKAGE)
                agent.wait_chat_ready()
                stopped = False
                before = len(records)
                agent.send(identifier, "Continue without repeating the completed file operation.")
                rows = agent.wait_turn(identifier)
                assert len(records) == before + 1
                body = records[before]["body"]
                agent.assert_tool_pairing(body)
                expected = {} if raw == "" else {REJECTED_ARGUMENTS_KEY: raw}
                assert provider_calls(body) == [("phone_call", expected)]
                if provider == "anthropic":
                    blocks = [
                        part for message in body["messages"] for part in message.get("content", [])
                        if isinstance(part, dict)
                    ]
                    assert any(
                        part.get("type") == "thinking" and part.get("signature") == "EXAMPLE-DO-NOT-USE-SIGNATURE"
                        for part in blocks
                    )
                assert "Keep the completed fixture operation in history." in json.dumps(body)
                assert provider_results(body) == tool_results(first)
                assert len([row for row in rows if row["role"] == "tool"]) == len([row for row in first if row["role"] == "tool"]) == 1
                assert native_call("http", "fileRead", {"path": path})["dataBase64"] == data
                assert agent.chat("settings", conversationId=identifier)["settings"]["readOnly"] is True
            finally:
                if stopped:
                    agent.run_device_operation("home-start", PACKAGE_NAME=agent.DIKCIZ_DEBUG_PACKAGE)
                    agent.wait_chat_ready()
                agent.cleanup(identifier)
                if created:
                    native_call("http", "fileManage", {"action": "delete", "path": path})


@pytest.mark.parametrize("provider", ["openai", "anthropic"])
def test_chat_tool_arguments_schema_recovery(websocket_control, provider_endpoint, provider, subtests):
    endpoint, script, records = provider_endpoint
    for wrapper, arguments in SCHEMA_CASES:
        with subtests.test(wrapper=wrapper, arguments=arguments):
            records.clear()
            script.update(wrapper_calls=[(wrapper, arguments)], text="Invalid wrapper fields rejected.")
            _, identifier = agent.conversation(endpoint, provider)
            try:
                agent.send(identifier)
                rows = agent.wait_turn(identifier)
                result = tool_results(rows)[0]
                assert result["isError"] is True and result["code"] == "validation_failed"
                agent.assert_tool_pairing(records[-1]["body"])
                agent.send(identifier, "Continue after the rejected fields.")
                agent.wait_turn(identifier)
                assert len(records) == 3
            finally:
                agent.cleanup(identifier)
