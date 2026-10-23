"""Discover and execute packaged MCP documentation through a live Android build."""

import argparse
import logging
import re
import subprocess
import sys

from . import *
from . import test_remote_bearer_auth as AUTH
from .test_custom_events import wait_for_widget_state_value


CLIENT = load_script("dikciz_resource_client", "scripts/dikciz-control.py")
RESOURCE_PREFIX = "dikciz://docs/"
INDEX_URI = RESOURCE_PREFIX + "index"
PAGE_SIZE = 8
SECTION_BYTES = 4096
SECTION_COUNT = 44
INVALID_PARAMS = -32602
READ_METHOD = "resources/read"
LIST_METHOD = "resources/list"
TEMPLATES_METHOD = "resources/templates/list"
WAIT_SECONDS = 20
SCRIPT_ID = "example-events"
WIDGET_ID = "resource-event-widget"
WIDGET_ADDRESS = "1H1V-" + WIDGET_ID
CLI_PATH = LAB_ROOT / "scripts/dikciz-control.py"


def client_arguments():
    return argparse.Namespace(
        host=AUTOMATION_HOST, port=MCP_PORT, timeout_seconds=WAIT_SECONDS,
        bearer_token_env="DIKCIZ_RESOURCE_TEST_UNUSED_TOKEN", stream_raw=False,
    )


def resource_request(arguments, session, method, params, password=None, extra_headers=None):
    message = {
        "jsonrpc": "2.0", "id": 2, "method": method,
        "params": {**params, "_meta": {
            "io.modelcontextprotocol/clientCapabilities": {},
            "io.modelcontextprotocol/protocolVersion": MCP.MCP_PROTOCOL_VERSION,
        }},
    }
    headers = AUTH.mcp_headers(session, password)
    if session is None:
        headers["MCP-Protocol-Version"] = MCP.MCP_PROTOCOL_VERSION
        headers["Mcp-Method"] = method
        if method == READ_METHOD:
            headers["Mcp-Name"] = params["uri"]
    return MCP.request(
        arguments, MCP.HTTP_METHOD_POST, message,
        {**headers, **(extra_headers or {})},
    )


def read_resource(slug):
    result = CLIENT.call_mcp(client_arguments(), READ_METHOD, {"uri": RESOURCE_PREFIX + slug})
    assert result["resultType"] == "complete"
    contents = result["contents"]
    assert len(contents) == 1
    assert contents[0]["uri"] == RESOURCE_PREFIX + slug
    assert contents[0]["mimeType"] == "text/markdown"
    return contents[0]["text"]


def fenced_source(text, language):
    matches = re.findall(r"```" + language + r"\n(.*?)\n```", text, re.DOTALL)
    assert len(matches) == 1
    return matches[0]


def test_mcp_resources_initialize_advertises_bounded_documentation():
    arguments = AUTH.mcp_arguments()
    status, headers, response = MCP.request(
        arguments, MCP.HTTP_METHOD_POST,
        {"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {
            "protocolVersion": MCP.MCP_PROTOCOL_VERSION,
            "capabilities": {}, "clientInfo": {"name": "resource-test", "version": "1"},
        }},
        AUTH.mcp_headers(),
    )
    assert status == 200, response
    session = headers[MCP.HEADER_MCP_SESSION_ID.lower()]
    try:
        result = response["result"]
        assert result["capabilities"]["resources"] == {}
        assert INDEX_URI in result["instructions"]
        assert "tools" in result["capabilities"]
        for method, params in ((LIST_METHOD, {}), (READ_METHOD, {"uri": INDEX_URI}), (TEMPLATES_METHOD, {})):
            assert resource_request(arguments, session, method, params)[0] == 400
    finally:
        AUTH.mcp_delete_session(arguments, session)


def test_mcp_resources_walk_all_pages_and_read_each_section(websocket_control):
    original = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
    descriptors = []
    cursors = set()
    params = {}
    page_lengths = []
    while True:
        result = CLIENT.call_mcp(client_arguments(), LIST_METHOD, params)
        assert result["resultType"] == "complete"
        page = result["resources"]
        assert 0 < len(page) <= PAGE_SIZE
        page_lengths.append(len(page))
        descriptors.extend(page)
        cursor = result.get("nextCursor")
        if cursor is None:
            break
        assert isinstance(cursor, str) and cursor not in cursors
        cursors.add(cursor)
        params = {"cursor": cursor}
    full_pages, remainder = divmod(SECTION_COUNT, PAGE_SIZE)
    assert page_lengths == [PAGE_SIZE] * full_pages + ([remainder] if remainder else [])
    uris = {item["uri"] for item in descriptors}
    assert len(uris) == len(descriptors) == SECTION_COUNT
    assert descriptors[0]["uri"] == INDEX_URI
    for descriptor in descriptors:
        assert descriptor["name"] and descriptor["title"] and descriptor["description"]
        text = read_resource(descriptor["name"])
        size = len(text.encode("utf-8"))
        assert 0 < size <= SECTION_BYTES
        assert descriptor["size"] == size
        assert descriptor["mimeType"] == "text/markdown"
        assert set(re.findall(r"dikciz://docs/[a-z/-]+", text)) <= uris
    assert CLIENT.call_mcp(client_arguments(), LIST_METHOD, {})["resources"] == descriptors[:PAGE_SIZE]
    if SECTION_COUNT % PAGE_SIZE == 0:
        terminal = CLIENT.call_mcp(client_arguments(), LIST_METHOD, {"cursor": "docs1:" + str(SECTION_COUNT)})
        assert terminal["resources"] == [] and "nextCursor" not in terminal
    templates = CLIENT.call_mcp(client_arguments(), TEMPLATES_METHOD, {})
    assert templates["resultType"] == "complete" and templates["resourceTemplates"] == []
    assert templates["cacheScope"] == "private" and templates["ttlMs"] == 0
    assert templates["_meta"]["io.modelcontextprotocol/serverInfo"]["name"] == "dikciz"
    tools = CLIENT.call_mcp(client_arguments(), "tools/list", {})["tools"]
    assert any(tool["name"] == "dikciz_control_status" for tool in tools)
    after = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
    assert after == original


def test_mcp_resources_reject_invalid_uris_cursors_and_fields():
    invalid = [
        (READ_METHOD, {}),
        *((READ_METHOD, {"uri": value}) for value in (
            None, False, 7, [], {}, "", " ", "x" * 256, "x" * 257,
            RESOURCE_PREFIX + "unknown-\u2603",
            RESOURCE_PREFIX + "missing", RESOURCE_PREFIX + "../control/remote-auth.json",
            "file:///sdcard/Dikciz/control/remote-auth.json", "https://example.invalid/",
            RESOURCE_PREFIX + "guides/%2e%2e/security", RESOURCE_PREFIX + "index?path=/sdcard",
        )),
        *((LIST_METHOD, {"cursor": value}) for value in (
            None, False, 8, [], {}, "", "0", "docs1:-8", "docs1:1", "docs1:08",
            "docs1:+8", "docs1:8.0", "docs1:" + str((SECTION_COUNT // PAGE_SIZE + 1) * PAGE_SIZE), "docs2:8", "docs1:" + "9" * 100,
        )),
        (READ_METHOD, {"uri": INDEX_URI, "path": "/sdcard"}),
        (LIST_METHOD, {"limit": 1000}),
        (TEMPLATES_METHOD, {"uri": INDEX_URI}),
    ]
    with AUTH.connected_mcp() as (arguments, session):
        for method, params in invalid:
            status, _, response = resource_request(arguments, session, method, params)
            assert status == 200, (method, params, response)
            assert response["id"] == 2
            assert response["error"]["code"] == INVALID_PARAMS, (params, response)
            assert "result" not in response
        unknown = RESOURCE_PREFIX + "absent"
        response = resource_request(arguments, session, READ_METHOD, {"uri": unknown})[2]
        assert response["error"]["data"]["uri"] == unknown
        for method in ("resources/subscribe", "resources/unsubscribe"):
            response = resource_request(arguments, session, method, {"uri": INDEX_URI})[2]
            assert response["error"]["code"] == -32601


def test_mcp_resources_enforce_origin_and_optional_session():
    for method, params in ((LIST_METHOD, {}), (READ_METHOD, {"uri": INDEX_URI}), (TEMPLATES_METHOD, {})):
        assert resource_request(AUTH.mcp_arguments(), None, method, params)[0] == 200
        assert resource_request(AUTH.mcp_arguments(), "unknown-resource-session", method, params)[0] == 404
        with AUTH.connected_mcp() as (arguments, session):
            assert resource_request(arguments, session, method, params, extra_headers={"Origin": "https://evil.example"})[0] == 403
            assert resource_request(arguments, session, method, params)[0] == 200


def test_mcp_resources_enforce_bearer_authentication(websocket_control):
    password = AUTH.fresh_bearer_password()
    configured = False
    try:
        AUTH.open_remote_auth_settings(websocket_control)
        AUTH.set_phone_password(websocket_control, password)
        configured = True
        with AUTH.connected_mcp(password) as (arguments, session):
            for method, params in ((LIST_METHOD, {}), (READ_METHOD, {"uri": INDEX_URI}), (TEMPLATES_METHOD, {})):
                assert resource_request(arguments, session, method, params)[0] == 401
                assert resource_request(arguments, session, method, params, "EXAMPLE-DO-NOT-USE")[0] == 401
                status, _, response = resource_request(arguments, session, method, params, password)
                assert status == 200 and "result" in response
    finally:
        if configured:
            with AUTH.connected_bearer_websocket(password) as connection:
                AUTH.clear_phone_password(connection)


def test_mcp_resources_cli_lists_pages_and_reads_one_section():
    def run_cli(*arguments):
        completed = subprocess.run(
            [sys.executable, str(CLI_PATH), *arguments,
             "--host", AUTOMATION_HOST, "--port", str(MCP_PORT),
             "--log-file", str(ARTIFACT_DIRECTORY / "resource-client.log")],
            capture_output=True, text=True, check=False, timeout=WAIT_SECONDS,
        )
        assert completed.returncode == 0, completed.stderr + completed.stdout
        return json.loads(completed.stdout)

    first = run_cli("mcp-resources", "--pretty")
    second = run_cli("mcp-resources", "--cursor", first["nextCursor"])
    assert first["resources"][0]["uri"] != second["resources"][0]["uri"]
    assert run_cli("mcp-read", INDEX_URI)["contents"][0]["text"] == read_resource("index")


def test_mcp_resources_documented_two_event_recipe_runs_on_phone(websocket_control):
    setup = read_resource("recipes/event-setup")
    records = [json.loads(value) for value in re.findall(r"```json\n(.*?)\n```", setup, re.DOTALL)]
    policy, subscription, widget_subscriptions, dispatch = records
    lua = fenced_source(read_resource("recipes/event-lua"), "lua")
    widget_document = read_resource("recipes/event-widget")
    html = fenced_source(widget_document, "html")
    javascript = fenced_source(widget_document, "javascript")
    original = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
    configured = copy.deepcopy(original)
    configured["launcher"]["home"]["selectedPageId"] = FIXTURE_HOME_PAGE_ID
    configuration_page(configured, FIXTURE_HOME_PAGE_ID)["widgets"] = [{
        "id": WIDGET_ID, "type": "html", "title": "Resource event recipe",
        "html": html, "css": "", "javascript": javascript, "state": {},
        "eventSubscriptions": widget_subscriptions, "enabled": True,
        "cell": {"column": 0, "row": 0, "columnSpan": 4, "rowSpan": 6},
    }]
    configured["scripts"].append({
        "id": SCRIPT_ID, "title": "Resource recipe", "enabled": True,
        "apiVersion": LUA_SCRIPT_API_VERSION, "source": lua, "state": {},
    })
    configured["automation"]["policies"].append(policy)
    configured["automation"]["scripts"].append(subscription)
    try:
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
        direct_tap(wait_for_physical_description_bounds("Resource event recipe"))
        try:
            wait_for_physical_text_bounds("Waiting")
        except AssertionError:
            try:
                run_device_operation("screenshot")
                current = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET))
                widget = configuration_widget(current, WIDGET_ID)
                logging.getLogger(__name__).error("Recipe widget did not expose its initial text",
                    extra={"widget_state": widget.get("state"), "widget_cell": widget.get("cell")})
            except (AssertionError, RuntimeError, OSError) as diagnostic_error:
                logging.getLogger(__name__).warning("Recipe failure capture unavailable",
                    extra={"error_class": type(diagnostic_error).__name__})
            raise
        dispatch["sourceWidgetAddress"] = WIDGET_ADDRESS
        for event, running, visible in (("example.start", True, "Running"), ("example.stop", False, "Stopped")):
            dispatch["actions"][0]["event"] = event
            result = CLIENT.call_mcp(client_arguments(), "tools/call", {
                "name": "dikciz_automation_dispatch", "arguments": dispatch,
            })
            assert result.get("isError", False) is False, result
            wait_for_automation_script_state(websocket_control, SCRIPT_ID, "running", running)
            wait_for_widget_state_value(websocket_control, WIDGET_ID, "running", running)
            wait_for_physical_text_bounds(visible)
        run_device_operation("screenshot")
        assert (ARTIFACT_DIRECTORY / "screenshot.png").stat().st_size > 0
    finally:
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=original)
