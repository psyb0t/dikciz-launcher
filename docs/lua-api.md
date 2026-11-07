---
title: "Lua API"
description: "Look up Lua event handlers, context methods, JSON conversion, saved state, returned actions, allowed libraries and execution limits."
permalink: /lua-api/
nav_order: 2
parent: "Developer manual"
---

# Lua API

Lua backends run in a fresh LuaJ interpreter for each event. They share phone commands, event subscriptions, typed actions and persisted state with headless JavaScript. They have no WebView or browser DOM. For interfaces use [HTML/CSS/JavaScript](javascript-api.md); for complete UI/backend bundles use [custom apps](custom-apps.md). Every ordinary phone operation is listed in the [command reference](command-reference.md).

## Contents

- [Handler](#handler)
- [Context](#context)
- [Event](#event)
- [Command results and errors](#command-results-and-errors)
- [Returned actions](#returned-actions)
- [State and JSON](#state-and-json)
- [Lifecycle and limits](#lifecycle-and-limits)
- [Available Lua libraries](#available-lua-libraries)

## Handler

Define `function on_event(event)` in `main.lua`. The enabled script needs an enabled policy and matching subscription. A custom-app backend instead uses its app manifest and lifecycle. `main.lua` has no automatic event loop and ordinary globals are not persistent state.

```lua
function on_event(event)
    local count = (context.state.count or 0) + 1
    return {
        status = "Counter updated",
        actions = {
            { type = "patchState", values = { count = count } },
            { type = "emitEvent", event = "example.counter.updated", payload = { count = count } }
        }
    }
end
```

The equivalent JavaScript handler is `onEvent(event)`. Lua uses colon syntax for methods on Lua objects only; the provided request functions take one argument and are called as `context.request({...})`, not `context:request({...})`.

## Context

| Member | Type | Definition |
| --- | --- | --- |
| `context.scriptId` | String | Current script/backend identity. |
| `context.state` | Read-only table | Persisted JSON state observed at invocation. |
| `context.widgets` | Read-only table | Launcher widgets keyed by full script address. Entries include identity, title, type, enabled state and HTML state where applicable. |
| `context.androidAccess` | Read-only table | Current Android access observations. Missing access remains a native command failure or owner-review result. |
| `context.accessibility` | Read-only table or absent | Optional accessibility observation. Enabling a subscription does not grant Accessibility access. |
| `context.request(commandTableOrJsonString)` | Function returning a table | Execute one complete ordinary command synchronously; return the canonical response envelope. |
| `context.requestJson(commandTableOrJsonString)` | Function returning a string | Same operation, returning canonical response JSON text. |

Both command functions take exactly one full command, including `type`. Tables are encoded as JSON with the same validation as network requests. Backend commands use local owner authority; the returned-action allowlist does not restrict direct requests. Enable only trusted source.

```lua
local response = context.request({ type = "deviceState" })
if response.type == "error" then
    return { status = response.code .. ": " .. response.message }
end
local device = response.result
return { status = "Read device state", actions = {
    { type = "patchState", values = { lastDevice = device } }
} }
```

Place this logic inside `on_event` for an executable script. Never assume that a returned object means an external effect completed; inspect its documented operation state.

## Event

| Field | Type | Meaning |
| --- | --- | --- |
| `event.type` | String | Native event name or exact custom event name. |
| `event.source` | String | Source identity. |
| `event.timestampMilliseconds` | Number | Epoch milliseconds of the event. |
| `event.coalescingKey` | String | Event-coalescing identity supplied by the host. |
| `event.payload` | Read-only table | Event-specific JSON payload. |
| `event.time.hour24` | Number | Local hour derived from the event timestamp. |
| `event.time.minute` | Number | Local minute derived from the event timestamp. |

Native event names, payload fields, required access and subscription options are documented in the [event tutorial](scripting-tutorial.md) and [configuration schema](dikciz-configuration.md#lua-scripts-and-automation). Custom events use the exact `event` name in a subscription. Publishing is asynchronous with respect to subscribers. Event replay and acknowledgement use the ordinary journal commands.

## Command results and errors

Success responses contain `type = "result"`, `requestId` and `result`. Launcher responses also include `revision`; app-local native paths can omit it. Canonical failures contain `type = "error"`, `requestId`, `code`, `message` and optional permission recovery. A native operation error normally returns this error table; malformed Lua input, encoding limits, unavailable command host or interpreter failure can instead fail the handler. Inspect script run status and logs for those failures.

Use `autoRequestPermissions = true` in an ordinary command to request an owner-reviewed Android permission handoff. It neither grants access nor retries the command. A background notification is not a completed grant. Request again only after the owner grants access and the relevant command's retry behavior permits it.

## Returned actions

<!-- {% raw %} -->

| Return form | Meaning |
| --- | --- |
| `nil` | Complete without returned actions or status. |
| `{type = "...", ...}` | One typed action. |
| `{{type = "...", ...}, ...}` | One-based sequential action list. |
| `{status = "...", actions = {...}, result = {...}}` | Shared result object. Fields are optional; status is bounded by `maxTitleCharacters`, actions contain at most eight entries, and result is a JSON object for an app-event response. Unknown fields are rejected. |

<!-- {% endraw %} -->

The handler returns a shared result object with optional status and an action list. Use the exact fields from `automationDispatch` in the [command reference](command-reference.md#dikciz_automation_dispatch), and the executable examples in the [scripting tutorial](scripting-tutorial.md). Unsupported action fields fail validation. A standalone script's enabled policy must allow each returned action and its event source.

Returned actions cover page selection, widget patches/state, DOM patches, app launch/actions, notifications, SMS, explicit intents, custom events, media controls, device controls and supported typed automation operations. The [typed action table](typed-actions.md) defines every action's fields and policy name; ordinary phone commands remain available independently through `context.request`.

That complete returned-action list applies to standalone scripts. Custom app backends accept returned `patchState` and `emitEvent`; use `context.request` for their other phone effects. This distinction lets the same background interpreter serve both hosts without inventing app-local widget addresses.

`patchState` merges top-level keys into the calling script/app state. `patchWidget` with a `state` field replaces the selected widget state. `patchDom` changes a currently rendered HTML document through its supported selector/patch contract. `emitEvent` publishes data for subscribers; it is not a function call or delivery receipt.

## State and JSON

Use `context.state` for reads and returned `patchState` actions for persistence. Context/event tables and decoded `request` response tables are read-only. Build new tables for changes. Do not mutate them or depend on global variables surviving another invocation. JSON arrays use Lua's one-based indexing. JSON null becomes Lua `nil`, which omits object keys and can leave holes in arrays. An empty Lua table is also ambiguous between an object and an array. For a lossless unchanged response keep the JSON string from `requestJson`; send JSON strings when exact JSON structure is required. Use JavaScript for transformations that must preserve explicit null values and array positions.

State changes are explicit writes. Keep source, state and event subscriptions separate. Standalone Lua scripts live under `/sdcard/Dikciz/scripts/<id>/` with `script.json`, `main.lua`, `state.json` and run status. Complete apps use `/sdcard/Dikciz/apps/<id>/`. Widget instance paths belong to their page and wrapper identities. Use provided context paths and the [public file schema](dikciz-configuration.md).

## Lifecycle and limits

Standalone subscriptions run through the native background automation host. Android can delay alarms or block background starts. Repeating alarms have a 60000 ms minimum. Complete apps add durable one-shot timers, cancellation and explicit recovery; read [timers and alerts](custom-apps.md#timers-and-alerts).

The shared default execution budgets are 50000 observed instructions, 250 ms setup CPU, 50 ms handler CPU and a 2 MiB observed memory delta. Native command CPU is excluded from interpreter CPU accounting. These are interpreter checks and observations, not an isolated-process heap sandbox. Exact configurable/action limits belong to the configuration guide. Disabled scripts do not run; a missing handler has its own outcome. Syntax/runtime, invalid result, instruction/CPU/memory budget and cancellation failures are recorded in run status.

The host removes unsafe globals and Java integration. Do not use `os`, `io`, package loading or shell libraries as a substitute for the documented command bridge. Android access, remote token grants and assistant read-only state are independent boundaries. [Background JavaScript](background-javascript.md) describes the alternative interpreter and shared operational limits.

## Available Lua libraries

The host provides the bounded base library, `bit32`, `table`, `string` and `math`. It removes `string.format`, `string.gsub`, `string.rep`, `table.concat`, `table.move` and `table.unpack` because their native work bypasses instruction accounting. Dynamic/module loading, raw/metatable/environment access, `print`, manual garbage collection, `debug`, `os`, `io` and Java integration are unavailable. There is no separate JSON module; command tables are encoded by the host, and `requestJson` provides exact serialized responses. Use returned status and the normal run/log commands for diagnostics.
