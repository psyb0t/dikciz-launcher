# MCP protocol compatibility

The phone serves Streamable HTTP at `/mcp`. Authentication and browser-Origin checks apply before either protocol mode. POST uses JSON content and accepts both application/json and text/event-stream. GET returns 405 because Dikciz does not provide a general server event stream there; WebSocket carries persistent launcher events. Shell streaming is a per-tool SSE response.

## Modern requests

Protocol 2026-07-28 uses stateless requests. No initialize handshake or session ID is required. Every request carries:

- `MCP-Protocol-Version: 2026-07-28` and `Mcp-Method` matching its method.
- `Mcp-Name` matching the tool name for tools/call or the URI for resources/read.
- params._meta containing io.modelcontextprotocol/protocolVersion and an object io.modelcontextprotocol/clientCapabilities. Client identity is optional.

Start with server/discover or directly call tools/list. Discovery returns supportedVersions, capabilities, instructions and server identity in result._meta. Discovery/list/resource results include resultType, ttlMs and cacheScope. These results are private to the authorized connection context; current caching TTL is zero.

```json
{"jsonrpc":"2.0","id":1,"method":"server/discover","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}}}
```

The HTTP headers must mirror that body. Missing/mismatched required headers return HTTP 400 and JSON-RPC -32020. Unsupported stateless versions return HTTP 400 and -32022, with requested/supported versions in error.data. Unknown modern methods return HTTP 404 and -32601. Mcp-Name supports the standard `=?base64?<UTF-8 bytes encoded as Base64>?=` sentinel when plain ASCII is insufficient; decoded values must match the body.

## Legacy clients

Initialize supports 2025-11-25, 2025-06-18 and 2025-03-26. Keep the returned Mcp-Session-Id and send notifications/initialized. Subsequent requests carry that session and negotiated MCP-Protocol-Version. Modern params._meta is optional for these legacy sessions. Unknown offered initialization versions receive the latest supported legacy version; clients must check the negotiated response.

Legacy sessions expire after ten idle minutes, with at most eight sessions. DELETE with the session ID closes one session and its owned streams. Reinitialize after a process restart or session expiry. Legacy ping returns an ordinary result.

Earlier Dikciz custom clients used an initialized session with version 2026-07-28. That compatibility path remains accepted, including its request metadata. New clients should use the standard stateless modern flow or a negotiated legacy version. The supplied Python client manages its connection automatically.

## Results and operation access

Tool results preserve ordinary command data in structuredContent and add text/image content for MCP clients. isError identifies a tool failure; its structured code and permissionRequest match the shared dispatcher. Successful protocol delivery does not prove the requested external effect. Poll the documented operation status or read the observed state.

Read [resources](mcp-resources.md), [agent setup](agent-integrations.md), [commands](command-reference.md) and [connection setup](automation-control.md#connect-without-usb). Public standards: [modern version negotiation](https://modelcontextprotocol.io/specification/2026-07-28/basic/versioning), [Streamable HTTP](https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/streamable-http) and [legacy lifecycle](https://modelcontextprotocol.io/specification/2025-11-25/basic/lifecycle).
