# MCP documentation resources

Dikciz packages short operating guides and recipes in the APK. MCP clients can discover and read them without downloading the repository or placing its full documentation in a model's context. Start with `dikciz://docs/index`, then fetch the linked sections needed for the task. The [packaged index](../app/src/dikciz/assets/mcp/index.md) is the content source.

## Discover and read

Initialization advertises `capabilities.resources: {}` and instructions pointing to the index. `resources/list` returns at most eight descriptors, each with `uri`, `name`, `title`, `description`, `mimeType` and UTF-8 byte `size`. Follow the opaque `nextCursor` until it is absent. Do not synthesize or alter cursors.

Call `resources/read` with one exact URI. It returns one `text/markdown` content record. Every section is at most 4096 UTF-8 bytes. A read does not bundle linked sections automatically. The 48 resources include connection, permissions, UI, apps, pages, HTML, Lua, events, files, shell, logs, security, native Android data, sensors, location, camera, microphone, speech and focused recipes. Separate resources cover feature access and typed actions for state/events, device operations and Accessibility. The contact/SMS recipe covers direct lookup, SIM selection, duplicate protection and receipts. The custom-event example splits setup, Lua source and HTML source into separate resources.

```json
{"method":"resources/read","params":{"uri":"dikciz://docs/recipes/event-setup"}}
```

This shows the method and parameters only. A real request needs the JSON-RPC envelope and its protocol mode: modern request metadata/routing headers, or a negotiated legacy session. Existing clients handle those details. `resources/templates/list` returns an empty template list. These packaged resources do not advertise subscriptions or list-change notifications.

## Use the supplied client

Run from a checkout so companion modules are available. Replace `phone.example` and the port with the phone's actual endpoint. The client reads bearer credentials from `DIKCIZ_REMOTE_BEARER_TOKEN`.

```bash
python3 scripts/dikciz-control.py mcp-resources --host phone.example --port 19002 --pretty
python3 scripts/dikciz-control.py mcp-read dikciz://docs/index --host phone.example --port 19002 --pretty
python3 scripts/dikciz-control.py mcp-read dikciz://docs/recipes/event-setup --host phone.example --port 19002 --pretty
```

Pass the returned `nextCursor` to `mcp-resources --cursor '<returned-cursor>'` for another page. Each CLI operation manages its own connection. The supplied client uses modern stateless requests; legacy clients initialize and close negotiated sessions.

## Version and access

Content describes the serving APK. Use `deviceInfo` to identify that package and version, `tools/list` for executable schemas, and `controlStatus` for live limits. Recipes require verifying persisted or visible effects after a request. The event recipe proves Lua state and a rendered HTML acknowledgement separately.

Resource requests use the existing bearer, browser-Origin and protocol-mode checks. Reads accept only catalogued `dikciz://docs/` URIs. They never read device paths, follow external URLs or expose credentials. Invalid parameters and unknown URIs return JSON-RPC `-32602`; unknown-resource errors include the URI in `error.data.uri`. Packaged-content load failures return `-32603`.

These are MCP resource methods. HTTP and WebSocket command APIs retain the shared command contract described in [automation control](automation-control.md). See [connection setup](automation-control.md#connect-without-usb) and the [source map](../app/src/dikciz/README.md) for transport and implementation details.

Speech recognition and hardware controls have separate focused guides. Read `dikciz://docs/guides/speech` for recognition modes and cancellation, or `dikciz://docs/guides/hardware-controls` for torch, vibration, settings and device inventories.
