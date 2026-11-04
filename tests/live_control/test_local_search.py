"""Offline retrieval through real Android SQLite and every owner binding."""

import concurrent.futures
import json
import time
import uuid

import pytest

from . import *
from . import test_chat_agent as agent
from . import test_entrypoint_parity as parity
from .test_chat_agent import provider_endpoint
from .test_native_data import PROTOCOLS, native_call

websocket_control = agent.websocket_control
COLLECTION_PREFIX = "search-fixture-"


def collection_name():
    return COLLECTION_PREFIX + uuid.uuid4().hex[:12]


def write(protocol, collection, documents):
    return native_call(protocol, "searchIndex", {
        "collection": collection, "action": "upsert", "documents": documents,
    })


def clear(collection):
    native_call("http", "searchIndex", {"collection": collection, "action": "clear"})


def query(protocol, text, collection="docs", **options):
    return native_call(protocol, "searchQuery", {"query": text, "collection": collection, **options})


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_local_search_docs_and_installed_contracts(websocket_control, protocol):
    docs = query(protocol, "custom event lua widget", limit=5)
    assert docs["items"] and docs["engine"] == "sqlite-fts4-bm25"
    assert any("event" in item["id"] for item in docs["items"]), docs
    assert all("excerpt" in item and "score" in item for item in docs["items"])
    item = docs["items"][0]
    full = native_call(protocol, "searchRead", {"id": item["id"]})
    assert full["text"] and full["uri"] == item["uri"]
    from .test_mcp_resources import read_resource
    assert read_resource(item["uri"].removeprefix("dikciz://docs/")) == full["text"]
    library = query(protocol, "context output reserve", limit=20)
    public = next(row for row in library["items"] if row["id"].startswith("library/"))
    assert read_resource(public["uri"].removeprefix("dikciz://docs/"))
    tools = query(protocol, "dikciz_sim_list", "tools")
    assert tools["items"][0]["id"] == "dikciz_sim_list", tools
    contract = tools["items"][0]["tool"]
    assert contract["name"] == "dikciz_sim_list" and "inputSchema" in contract
    assert query(protocol, "own telephone number", "tools")["items"]
    writable = query(protocol, "dikciz_sms_send", "tools")["items"][0]
    assert writable["id"] == "dikciz_sms_send" and writable["tool"]["annotations"]["readOnlyHint"] is False
    assert native_call(protocol, "searchRead", {"collection": "tools", "id": writable["id"]})["tool"]["name"] == writable["id"]
    native_call(protocol, "searchRead", {"id": "missing-search-document"}, error="search_not_found")


def test_local_search_recipe_relevance(websocket_control):
    cases = [
        ("flashlight torch", ("hardware", "typed-actions")),
        ("permissoins", ("permissions", "android-access", "access")),
        ("persistent terminal output", ("shell",)),
        ("send text message", ("contact-sms", "native-data", "native-device-control", "typed-actions")),
        ("own telephone number", ("sim", "native-device-control")),
        ("speech transcription", ("speech", "native-device-control", "hardware")),
    ]
    for text, expected in cases:
        page = query("http", text, limit=5)
        assert any(any(fragment in item["id"] for fragment in expected) for item in page["items"]), (text, page)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_local_search_custom_collections_pagination_and_mutations(websocket_control, protocol):
    collection = collection_name()
    docs = [{"id": "item-" + str(index).zfill(2), "title": "Starship archive",
             "text": "Stargate telemetry inventory", "aliases": "spaceship diagnostic"} for index in range(13)]
    try:
        assert query(protocol, "telemetry", collection)["total"] == 0
        write(protocol, collection, docs[:1])
        single = query(protocol, "telemetry", collection, limit=1)
        assert len(single["items"]) == 1 and single["hasMore"] is False
        write(protocol, collection, docs[:5])
        exact = query(protocol, "telemetry", collection, limit=5)
        assert len(exact["items"]) == 5 and exact["hasMore"] is False
        assert write(protocol, collection, docs)["state"] == "completed"
        write(protocol, collection, docs)
        identities = []
        for offset, count, more in ((0, 5, True), (5, 5, True), (10, 3, False), (13, 0, False), (10000, 0, False)):
            page = query(protocol, "telemetry", collection, limit=5, offset=offset)
            assert len(page["items"]) == count and page["hasMore"] is more, page
            assert page["total"] == 13 and page["limit"] == 5 and page["offset"] == offset
            identities.extend(item["id"] for item in page["items"])
        assert identities == [doc["id"] for doc in docs]
        alias = query(protocol, "spaceship diagnostic", collection)
        assert alias["total"] == 13
        typo = query(protocol, "stargatte", collection)
        assert typo["total"] == 13 and typo["corrections"]["stargatte"] == ["stargate"]
        assert query(protocol, "stargatte", collection, fuzzy=False)["items"] == []
        assert query(protocol, "telem", collection)["total"] == 13
        assert query(protocol, "unknownuniquelexeme", collection)["items"] == []
        write(protocol, collection, [{"id": "accent", "title": "Café", "text": "accelerometerReading"}])
        assert query(protocol, "cafe", collection)["items"][0]["id"] == "accent"
        assert query(protocol, "accelerometer reading", collection)["items"][0]["id"] == "accent"
        assert query(protocol, "item-03", collection)["items"][0]["id"] == "item-03"
        write(protocol, collection, [{"id": "item-03", "title": "Replaced", "text": "differentuniquecontent"}])
        assert query(protocol, "telemetry", collection)["total"] == 12
        native_call(protocol, "searchIndex", {"collection": collection, "action": "remove", "ids": ["item-03"]})
        native_call(protocol, "searchRead", {"collection": collection, "id": "item-03"}, error="search_not_found")
        native_call(protocol, "restart")
        agent.wait_chat_ready()
        assert query(protocol, "telemetry", collection)["total"] == 12
        page = native_call(protocol, "searchCollections", {"limit": 20})
        assert any(item["collection"] == collection for item in page["items"])
    finally:
        clear(collection)
    assert query(protocol, "telemetry", collection)["items"] == []


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_local_search_validation_and_atomicity(websocket_control, protocol):
    collection = collection_name()
    invalid = [
        ("searchQuery", {}), ("searchQuery", {"query": ""}),
        ("searchQuery", {"query": "***"}), ("searchQuery", {"query": "x" * 513}),
        ("searchQuery", {"query": "x " * 17}), ("searchQuery", {"query": "a" * 81}),
        ("searchQuery", {"query": "screen", "readOnly": False}),
        ("searchQuery", {"query": "screen", "collection": "../docs"}),
        *[("searchQuery", {"query": "screen", "limit": value}) for value in (0, -1, 21, True, None, "1", 1.5)],
        *[("searchQuery", {"query": "screen", "offset": value}) for value in (-1, 10001, True, "0")],
        ("searchIndex", {"collection": "docs", "action": "clear"}),
        ("searchIndex", {"collection": "tools", "action": "clear"}),
        ("searchIndex", {"collection": collection, "action": "unknown"}),
        ("searchIndex", {"collection": collection, "action": "upsert", "documents": []}),
        ("searchIndex", {"collection": collection, "action": "upsert", "documents": [{"id": str(i), "title": "x", "text": "x"} for i in range(33)]}),
        ("searchIndex", {"collection": collection, "action": "upsert", "documents": [{"id": "oversize", "title": "x", "text": "x" * 32769}]}),
        ("searchIndex", {"collection": collection, "action": "upsert", "documents": [{"id": "duplicate", "title": "x", "text": "x"}] * 2}),
        ("searchIndex", {"collection": collection, "action": "upsert", "documents": [{"id": "a", "title": "x", "text": "x", "readOnly": False}]}),
        ("searchIndex", {"collection": collection, "action": "remove", "ids": [None]}),
        ("searchIndex", {"collection": collection, "action": "clear", "documents": []}),
    ]
    try:
        for command, arguments in invalid:
            native_call(protocol, command, arguments, error="validation_failed")
        write(protocol, collection, [{"id": "original", "title": "Atomic", "text": "retainedvalue"}])
        native_call(protocol, "searchIndex", {"collection": collection, "action": "upsert", "documents": [
            {"id": "new", "title": "Valid", "text": "newvalue"},
            {"id": "bad", "title": "", "text": "invalid"},
        ]}, error="validation_failed")
        assert query(protocol, "newvalue", collection)["items"] == []
        assert query(protocol, "retainedvalue", collection)["total"] == 1
        query(protocol, 'x\" OR 1=1 DROP TABLE documents', collection)
        assert query(protocol, "retainedvalue", collection)["total"] == 1
        assert query(protocol, "retainedvalue", collection_name())["items"] == []
    finally:
        clear(collection)


def test_local_search_rank_and_concurrent_updates(websocket_control):
    collection = collection_name()
    try:
        write("http", collection, [
            {"id": "title", "title": "Sensor inventory", "text": "Read hardware."},
            {"id": "body", "title": "Other", "text": "sensor inventory"},
            {"id": "partial", "title": "Sensor", "text": "Other."},
        ])
        results = query("http", "sensor inventory", collection)
        assert [item["id"] for item in results["items"]] == ["title", "body", "partial"]
        other = collection_name()
        try:
            write("http", other, [{"id": "unrelated", "title": "Sensor inventory", "text": "sensor inventory " * 100}])
            assert query("http", "sensor inventory", collection) == results
        finally:
            clear(other)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
            writes = list(pool.map(lambda index: write("http", collection, [
                {"id": "parallel-" + str(index), "title": "Parallel", "text": "concurrencyfixture"},
            ]), range(6)))
        assert all(result["state"] == "completed" for result in writes)
        assert query("http", "concurrencyfixture", collection)["total"] == 6
    finally:
        clear(collection)


@pytest.mark.parametrize("provider", ("openai", "anthropic"))
def test_local_search_agent_wrappers_and_read_only(websocket_control, provider_endpoint, provider):
    endpoint, script, records = provider_endpoint
    collection = collection_name()
    _, identifier = agent.conversation(endpoint, provider)
    script.update(text="Search fixture finished.", wrapper_calls=[
        ("phone_search", {"query": "event pipeline"}),
        ("phone_tools", {"query": "file"}),
        ("phone_call", {"name": "dikciz_search_query", "arguments_json": json.dumps({"collection": "tools", "query": "file"})}),
        ("phone_call", {"name": "dikciz_search_read", "arguments_json": json.dumps({"collection": "tools", "id": "dikciz_file_write"})}),
        ("phone_call", {"name": "dikciz_search_index", "arguments_json": json.dumps({"collection": collection, "action": "clear"})}),
    ])
    try:
        agent.chat("configure", conversationId=identifier, settings={"readOnly": True})
        agent.send(identifier)
        rows = agent.wait_turn(identifier)
        results = [json.loads(json.loads(row["blocks"])[0]["text"]) for row in rows if row["role"] == "tool"]
        assert results[0]["items"] and any("event" in item["id"] for item in results[0]["items"])
        contracts = results[1]["tools"]
        contracts += [item["tool"] for item in results[2]["structuredContent"]["items"]]
        assert contracts and all(tool["annotations"]["readOnlyHint"] for tool in contracts)
        assert all("autoRequestPermissions" not in tool["inputSchema"]["properties"] for tool in contracts)
        assert all(tool["name"] != "dikciz_file_write" for tool in contracts)
        assert results[3]["structuredContent"]["code"] == "search_not_found"
        assert results[4]["structuredContent"]["code"] == "read_only_violation"
        assert records
        from .test_sim_numbers_and_chat_contract import request_access_context
        revision = request_access_context(records[0]["body"])["toolCatalogueRevision"]
        assert len(revision) == 64 and all(character in "0123456789abcdef" for character in revision)
    finally:
        agent.cleanup(identifier)


def test_local_search_html_and_lua_use_shared_executor(websocket_control):
    original = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
    expected = query("http", "custom event lua")
    javascript = """window.addEventListener("dikciz-ready", async () => {
      const result = await dikciz.command("searchQuery", {query:"custom event lua"});
      await selfWidget.patchState({parityResponse:result});
    });"""
    configured = parity.parity_html_configuration(original, javascript)
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    result = parity.wait_for_parity_response(websocket_control)
    assert result == expected
    payload = json.dumps({"type": "searchQuery", "query": "custom event lua"})
    source = """function on_event(event)
      local reply = context.requestJson([==[%s]==])
      return { actions = {{ type = "patchState", values = { parityReply = reply } }} }
    end""" % payload
    configured = automation_configuration(original, source, {}, ["manualTrigger"], ["patchState"],
        [{"event": "manual", "minimumIntervalMilliseconds": 0, "coalescingKey": "search"}])
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_TRIGGER, scriptId=AUTOMATION_LIVE_SCRIPT_ID)
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        current = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
        script = next(row for row in current["scripts"] if row["id"] == AUTOMATION_LIVE_SCRIPT_ID)
        reply = script["state"].get("parityReply")
        if isinstance(reply, str):
            assert json.loads(reply)["result"] == expected
            return
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("Lua search result did not arrive")


def test_local_search_boundaries_capacity(websocket_control):
    collection = collection_name()
    try:
        for start in range(0, 2000, 32):
            write("http", collection, [
                {"id": "capacity-" + str(index).zfill(4), "title": "Capacity", "text": "capacityfixture"}
                for index in range(start, min(start + 32, 2000))
            ])
        assert query("http", "capacityfixture", collection)["total"] == 2000
        native_call("http", "searchIndex", {
            "collection": collection, "action": "upsert", "documents": [
                {"id": "capacity-0000", "title": "Modified", "text": "mustnotsave"},
                {"id": "overflow", "title": "Overflow", "text": "overflowfixture"},
            ],
        }, error="search_capacity_exceeded")
        original = native_call("http", "searchRead", {"collection": collection, "id": "capacity-0000"})
        assert original["title"] == "Capacity" and original["text"] == "capacityfixture"
        assert query("http", "mustnotsave", collection, fuzzy=False)["items"] == []
        native_call("http", "searchRead", {"collection": collection, "id": "overflow"}, error="search_not_found")
        assert query("http", "capacityfixture", collection)["total"] == 2000
        native_call("http", "searchIndex", {"collection": collection, "action": "remove", "ids": ["capacity-0001"]})
        write("http", collection, [{"id": "replacement", "title": "Replacement", "text": "capacityfixture"}])
        assert query("http", "capacityfixture", collection)["total"] == 2000
    finally:
        clear(collection)


def test_local_search_boundaries_collection_pages(websocket_control):
    collections = [collection_name() for _ in range(11)]
    try:
        for name in collections:
            write("http", name, [{"id": "entry", "title": "Collection", "text": "collectionfixture"}])
        identifiers = []
        for offset, count, more in ((0, 5, True), (5, 5, True), (10, 3, False), (13, 0, False), (10000, 0, False)):
            page = native_call("http", "searchCollections", {"limit": 5, "offset": offset})
            assert page["total"] == 13 and page["hasMore"] is more, page
            assert len(page["items"]) == count
            identifiers.extend(row["collection"] for row in page["items"])
        assert identifiers == sorted(["docs", "tools", *collections])
    finally:
        for name in collections:
            clear(name)
