"""Official modern and legacy MCP wire shapes against the real Android server."""

import http.client
from . import *

MODERN_VERSION = "2026-07-28"
LEGACY_VERSION = "2025-11-25"


def rpc(method, params, *, session=None, version=MODERN_VERSION, modern=True, request_id=1, extra_headers=None):
    fields = dict(params)
    if modern:
        fields["_meta"] = {"io.modelcontextprotocol/protocolVersion": version, "io.modelcontextprotocol/clientCapabilities": {}}
    message = {"jsonrpc": "2.0", "method": method, "params": fields}
    if request_id is not None: message["id"] = request_id
    headers = {"Content-Type": "application/json", "Accept": "application/json, text/event-stream"}
    if modern or session:
        headers["MCP-Protocol-Version"] = version
    if modern:
        headers["Mcp-Method"] = method
        if method == "tools/call": headers["Mcp-Name"] = fields["name"]
        if method == "resources/read": headers["Mcp-Name"] = fields["uri"]
    if session: headers["Mcp-Session-Id"] = session
    headers.update(extra_headers or {})
    connection = http.client.HTTPConnection(AUTOMATION_HOST, MCP_PORT, timeout=20)
    try:
        connection.request("POST", "/mcp", json.dumps(message).encode(), headers)
        response = connection.getresponse()
        data = response.read()
        return response.status, dict(response.getheaders()), json.loads(data) if data else None
    finally:
        connection.close()


def test_mcp_interoperability_modern(websocket_control):
    status, headers, body = rpc("server/discover", {})
    assert status == 200, body
    assert "Mcp-Session-Id" not in headers
    result = body["result"]
    assert MODERN_VERSION in result["supportedVersions"] and LEGACY_VERSION in result["supportedVersions"]
    assert result["resultType"] == "complete" and result["ttlMs"] >= 0 and result["cacheScope"] == "private"
    assert result["_meta"]["io.modelcontextprotocol/serverInfo"]["name"]
    status, _, body = rpc("tools/list", {})
    assert status == 200 and body["result"]["tools"]
    assert body["result"]["resultType"] == "complete"
    assert body["result"]["ttlMs"] >= 0 and body["result"]["cacheScope"] == "private"
    status, _, body = rpc("tools/call", {"name": "dikciz_device_state", "arguments": {}})
    assert status == 200 and isinstance(body["result"]["structuredContent"]["screenInteractive"], bool)
    status, _, body = rpc("resources/read", {"uri": "dikciz://docs/index"})
    assert status == 200 and body["result"]["contents"][0]["text"]
    status, _, body = rpc("tools/list", {}, extra_headers={"Mcp-Method":"tools/call"})
    assert status == 400 and body["error"]["code"] == -32020
    status, _, body = rpc("tools/call", {"name":"dikciz_device_state","arguments":{}}, extra_headers={"Mcp-Name":"wrong"})
    assert status == 400 and body["error"]["code"] == -32020
    status, _, body = rpc("server/discover", {}, version="1900-01-01")
    assert status == 400 and body["error"]["code"] == -32022
    assert MODERN_VERSION in body["error"]["data"]["supported"]
    status, _, body = rpc("unknown/method", {})
    assert status == 404 and body["error"]["code"] == -32601


def test_mcp_interoperability_legacy(websocket_control):
    status, headers, body = rpc("initialize", {"protocolVersion":LEGACY_VERSION,"capabilities":{},"clientInfo":{"name":"fixture","version":"1"}}, modern=False)
    assert status == 200 and body["result"]["protocolVersion"] == LEGACY_VERSION, body
    session = next(value for name,value in headers.items() if name.lower() == "mcp-session-id")
    status, _, _ = rpc("notifications/initialized", {}, session=session, version=LEGACY_VERSION, modern=False, request_id=None)
    assert status == 202
    status, _, body = rpc("tools/list", {}, session=session, version=LEGACY_VERSION, modern=False)
    assert status == 200 and body["result"]["tools"]
    status, _, body = rpc("tools/call", {"name":"dikciz_device_state","arguments":{}}, session=session, version=LEGACY_VERSION, modern=False)
    assert status == 200 and isinstance(body["result"]["structuredContent"]["screenInteractive"], bool)
    status, _, body = rpc("ping", {}, session=session, version=LEGACY_VERSION, modern=False)
    assert status == 200 and "result" in body
