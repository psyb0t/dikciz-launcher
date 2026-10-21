# Discover the running build

Call `dikciz_device_info` first. Its `application` reports the serving package, version, process and `instanceId`. Release is `eu.psyb0t.dikciz.launcher`; debug adds `.debug`. Updating one does not update the other. Both can read the same public configuration and compete for listener ports.

Call `tools/list` for exact argument schemas. Call `dikciz_control_status` for live command contracts, ports, events and limits. Call `dikciz_automation_status` for script and Android access state. Do not infer tool names by converting camel case; use discovery.

MCP `tools/call` wraps a command result in `structuredContent` and text blocks. Inspect `isError`, the command envelope, each action outcome and any `permissionRequest`. An HTTP success status only means the protocol request reached the server. A batch may contain both completed and refused actions.

WebSocket and plain HTTP use `{ "requestId": "<UUID>", "type": "<command>", ...fields }`. They return a correlated `result` or `error` envelope. MCP, HTTP, WebSocket, HTML bridge and Lua owner requests share the command target's procedures and access checks. Transport envelopes and streaming delivery differ.

Use fresh `snapshot` results after UI changes. Semantic IDs and Accessibility snapshots describe a particular screen. Verify a resulting state before continuing a dependent operation. For delayed operations such as APK installation and restart, poll the documented completion signal.

[UI guide](dikciz://docs/guides/ui). [Permissions](dikciz://docs/guides/permissions).
