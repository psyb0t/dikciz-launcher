---
title: "Developer manual"
description: "Choose a Dikciz runtime and find the complete JavaScript, Lua, phone command and token permission contracts for widgets, apps and controllers."
permalink: /developer-manual/
nav_order: 3
has_children: true
---

# Developer manual

Build interfaces, background automations and complete apps using documented Dikciz APIs. The platform shares one command executor across JavaScript, Lua, HTTP, WebSocket and MCP. Android permissions and hardware availability still govern each operation.

## Pick a runtime

| Build | Interface | Background logic | State and lifetime | Start here |
| --- | --- | --- | --- | --- |
| Home widget | HTML/CSS/browser JavaScript | A separate script or app backend when needed | Page/widget instance state; WebView follows page visibility | [JavaScript API](javascript-api.md) |
| Standalone automation | Optional separate widget | Lua `on_event` or headless JavaScript `onEvent` | Script state, policy and event subscriptions | [Lua API](lua-api.md), [background JavaScript](background-javascript.md) |
| Complete custom app | HTML/CSS/browser JavaScript | Lua or headless JavaScript | Independent app state/files, subscriptions, native one-shot timers and alerts | [App lifecycle and bundle format](custom-apps.md) |
| External controller or agent | Your client | Your process | Scoped bearer identity, transport sessions and operation-specific retained work | [Remote connection](automation-control.md), [token grants](token-permissions.md) |

The browser API is `window.dikciz` and `window.selfWidget`. A headless backend uses `context`, not `window.dikciz`. JavaScript UI calls return Promises; Lua/headless JavaScript command calls are synchronous. All use the same ordinary command names and JSON fields. Their helper names and result-unwrapping behavior differ, as the language references explain.

## Complete references

| Reference | Defines |
| --- | --- |
| [JavaScript API](javascript-api.md) | Every injected bridge/helper, WebView readiness, current widget/item state, file helpers, events, Promise/error behavior and background context. |
| [Lua API](lua-api.md) | Handler, context functions/fields, event envelope, JSON/table conversion, state, actions and runtime limits. |
| [Command reference](command-reference.md) | Every discovered MCP tool, ordinary command type, complete input schema and parameters, token scopes and language invocation pattern. |
| [Token permission reference](token-permissions.md) | Every grant ID and definition, exact operation/action/resource coverage, broad authority and limits. |
| [Configuration schema](dikciz-configuration.md) | Home/page/widget/script files, policies, subscriptions, validation, persistence and reload. |
| [Android access](android-access.md) | Runtime grants, special access, foreground requirements, roles and platform limits. |
| [Public Android SDK](android-sdk.md) | Class/overload discovery, typed values, handles, callbacks and read-only execution. |
| [MCP resources](mcp-resources.md) | Short packaged guides and searchable manual sections for agents. |

Schemas from the installed build are authoritative. A new APK can add commands that an older build does not expose. Unknown commands/fields and unsupported enum values are errors. Treat optional defaults as defined by each schema, rather than inventing arguments from a similar API.

## Build an app step by step

1. Follow the [GPS todo tutorial](todo-app-tutorial.md) for a complete five-file app whose background location event updates its UI state.
2. Follow the [scripting tutorial](scripting-tutorial.md) for a custom event flowing from an HTML button to Lua and back into a widget.
3. Choose a [bundled template](starter-home.md), add an independent copy, then edit its complete source and state.
4. For a remote client, create only the scopes needed, inspect `remoteAccessInfo`, discover visible tools and use their exact input schemas. Direct hidden-tool calls are checked too.
5. Check the operation's own result, retained status and Activity/logs. A queued command, submitted intent or accepted SMS does not prove the external effect completed.

## Phone API families

| Family | Detailed behavior and results |
| --- | --- |
| Home, semantic UI, configuration and events | [Automation control](automation-control.md), [HTML DOM](automation-control.md#html-document-controls), [event scripts](scripting-tutorial.md) |
| Apps, files, APK installation, shell and jobs | [Apps](custom-apps.md), [files/APKs](files-and-apks.md), [shell sessions](shell-sessions.md), [jobs](command-jobs.md) |
| Contacts, calendar, SMS, calls, SIM and clipboard | [Native data and provider resources](native-device-control.md) |
| Sensors, camera, audio, speech and location | [Hardware](native-device-control.md), [camera](camera-control.md), [audio](audio-playback.md), [speech](speech-recognition.md), [location](location-services.md) |
| Bluetooth, Wi-Fi, NFC and USB | [Bluetooth](bluetooth-control.md), [Wi-Fi](wifi-control.md), [NFC](nfc-control.md), [USB](usb-control.md) |
| Notifications, media, device settings and SDK | [Notifications](notification-management.md), [hardware](hardware-controls.md), [SDK](android-sdk.md) |
| Safety, journeys, health and document capture | [Companion](companion.md), [journeys](safety-and-journeys.md), [coverage](capability-coverage.md) |
| Chat, search, operations and recovery | [Chat](embedded-chat.md), [search](search.md), [operations](operations.md), [backups](backups.md) |

## Result contract

Ordinary success responses contain `type: "result"`, matching `requestId` and an operation-specific `result`. Launcher responses also include `revision`; app-local native command paths can omit it. Ordinary failures contain `type: "error"`, `requestId`, stable `code`, human-readable `message` and optional permission-recovery data. MCP exposes the canonical result as `structuredContent` and marks operation errors with `isError`. HTTP authentication failures can occur before an ordinary command response. WebSocket also carries independent event messages.

Use the operation's focused guide above for its result fields and terminal states. Read `hasMore`, cursor/history-gap and completion/cleanup fields where present. Camera availability, carrier delivery, external apps and Android permission screens are observed separately from command acceptance. Read-only hints describe operations; the trusted executor independently enforces read-only execution.

## Authority and persistence

Remote scopes authorize commands and finite action/resource choices. They do not grant Android permissions. Code installation, shell, generic SDK calls, arbitrary intents and credential administration can delegate broad authority; selecting a narrow operation name does not sandbox the code or object it controls. Full definitions are in the token table.

Local HTML, Lua and JavaScript bindings use the owner authority already granted to Dikciz. A custom app is not a separately isolated Android package. Keep secrets out of shared state, screenshots and reusable app source. Use the existing protected provider/authentication storage for credentials. A token's scope cannot undo effects already submitted to Android.
