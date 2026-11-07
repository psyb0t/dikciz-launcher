---
title: "JavaScript API"
description: "Look up every Dikciz WebView bridge, widget helper and headless JavaScript context method, including state, events, results, errors and limits."
permalink: /javascript-api/
nav_order: 1
parent: "Developer manual"
---

# JavaScript API

This is the reference for the injected WebView API and the separate headless JavaScript context. Start with the [developer manual](developer-manual.md) to choose a runtime. The [command reference](command-reference.md) lists every phone operation, required field, default and bound. Phone commands use `dikciz.command(type, fields)` in a WebView and `context.request({type, ...fields})` in a backend. There is no generated `dikciz.smsSend()` method.

## Contents

- [Runtime and readiness](#runtime-and-readiness)
- [Command transport](#command-transport)
- [Home and widget helpers](#home-and-widget-helpers)
- [Current widget](#current-widget)
- [Item state and files](#item-state-and-files)
- [Custom app interfaces](#custom-app-interfaces)
- [Events](#events)
- [Background JavaScript](#background-javascript)
- [Errors, timeouts and authority](#errors-timeouts-and-authority)

## Runtime and readiness

HTML widgets and custom app interfaces run in Android WebView. They have ordinary browser DOM, CSS, JavaScript and browser timers. The bridge becomes available after the `dikciz-ready` window event. Register listeners immediately when the document loads; send commands from the ready callback. `dikciz.version` is the bridge API version, currently 4, rather than the APK version.

```javascript
window.addEventListener("dikciz-ready", async () => {
    try {
        const device = await dikciz.command("deviceState");
        document.querySelector("#status").textContent = JSON.stringify(device);
    } catch (error) {
        document.querySelector("#status").textContent = error.message;
    }
});
```

An HTML widget's WebView and event listeners depend on its rendered page. Background scripts and app backends run without that WebView. Put durable timers and background subscriptions in a backend. Stop unnecessary polling when `selfWidget.visible` becomes false. See [background lifetime](background-javascript.md#background-lifetime-and-limits).

## Command transport

| Member | Parameters | Return | Behavior |
| --- | --- | --- | --- |
| `dikciz.command(type, fields = {}, options = {})` | Ordinary command type; JSON object of that command's fields; local response options | `Promise<result>` | Generates a request ID. Resolves with the canonical result object. Rejects canonical errors as `Error` objects. `fields.type` cannot override the first argument. |
| `dikciz.request(document, options = {})` | Complete command object containing `type`; local response options | `Promise<response>` | Resolves the complete `type: "result"` or `type: "error"` envelope. A canonical operation error is a resolved response here. Bridge/transport failures reject. |
| `dikciz.dispatch(documentOrActions)` | Nonempty action array or `{actions, autoRequestPermissions?}` | `Promise<result>` | Calls `automationDispatch` with the shared typed-action schema. This is not a generic command array. |
| `dikciz.emitEvent(event, payload = {}, coalescingKey?)` | Custom event name, JSON object payload, optional coalescing key | `Promise<result>` | Dispatches one `emitEvent` action. Acceptance does not prove subscribers completed. |

`options.responseTimeoutMilliseconds` is a local reply deadline, an integer from 0 through 2147483647. Zero disables this local timer. Default is the greater of 60000 ms and the command's `timeoutMilliseconds`/`durationMilliseconds` plus 5000 ms. It does not alter the native operation's timeout or cancel work. A command's own timeout belongs in `fields`, with the units and bounds from its schema.

At most 32 commands may await replies. Serialized requests are limited to 1048576 bytes. The native host independently validates every command. A reply timeout does not prove a side effect failed; inspect the operation's retained status before retrying.

```javascript
const response = await dikciz.request({type: "deviceState"});
if (response.type === "error") {
    console.error(response.code, response.message);
} else {
    console.log(response.requestId, response.revision, response.result);
}
```

## Home and widget helpers

| Member | Parameters | Return | Behavior |
| --- | --- | --- | --- |
| `dikciz.pages()` | None | Frozen page map | Page map injected for this document, including the home alias. This is not a fresh configuration read. |
| `dikciz.home()` | None | `Promise<homeGet result>` | Reads the current home through `homeGet`. |
| `dikciz.widget(address)` | Full widget address | `Promise<widgetGet result>` | Reads the current saved widget record. |
| `dikciz.patchWidget(address, values)` | Full widget address, supported widget patch object | `Promise<automationDispatch result>` | Dispatches `patchWidget`; unsupported fields fail validation. |
| `dikciz.patchWidgetState(address, values)` | Full widget address, state object | `Promise<automationDispatch result>` | Sends `patchWidget` with `{state: values}`. This replaces the target state field. |
| `dikciz.patchDom(address, selector, values)` | Full widget address, CSS selector, supported DOM patch | `Promise<automationDispatch result>` | Dispatches `patchDom` to a rendered HTML document. See [typed actions](typed-actions.md). |
| `dikciz.fitContent()` | None | `undefined` | Requests a fresh content-height measurement. Native sizing still follows the widget's height mode and limits. |
| `dikciz.selfWidget` | Read-only object | Same object as `window.selfWidget` | Context and helpers for this instance. |

A widget address is `<horizontal>H<vertical>V-<widget-id>`, such as `1H1V-example-card`. Page positions and IDs are different concepts. Use `selfWidget` for self so moving a widget does not leave a stale hardcoded address. `patchState` and `patchWidgetState` have different merge semantics.

## Current widget

| Member | Return | Meaning |
| --- | --- | --- |
| `selfWidget.id` | String | Stable widget or custom app identity. |
| `selfWidget.title`, `selfWidget.type`, `selfWidget.enabled` | String, string, Boolean | Injected page-widget title, widget type and enabled flag. These snapshot fields are not persistence setters. App-local hosts omit page-widget metadata. |
| `selfWidget.appId` | String or absent | App-local identity in a custom app WebView. Absent in ordinary page widgets. |
| `selfWidget.page`, `selfWidget.address`, `selfWidget.cell`, `selfWidget.pages` | Injected context | `page` is a page document with its address, ID, title, position and widgets; `address` is the full widget address; `cell` is the grid rectangle; `pages` is the page map. Custom app hosts supply their own context. |
| `selfWidget.storagePath` | String | Native instance storage root. Use this supplied value rather than constructing a path from a title. |
| `selfWidget.state` | JSON object | Latest state delivered to this document. Treat it as observed state and persist through helpers. |
| `selfWidget.itemStates` | JSON object | Latest delivered map of item IDs to their states. |
| `selfWidget.visible` | Boolean | Native observed visibility. Also consider `document.hidden` when polling. |
| `selfWidget.get()` | `Promise<widgetGet result>` | Fresh saved record. |
| `selfWidget.patch(values)` | `Promise<automationDispatch result>` | Patch this wrapper using the supported widget fields. |
| `selfWidget.patchState(values)` | `Promise<automationDispatch result>` | Dispatch `patchState`. Page widgets replace their state object; custom apps merge top-level keys. Explicit null stays a saved null. |
| `selfWidget.patchDom(selector, values)` | `Promise<automationDispatch result>` | Patch the rendered document's matching DOM nodes. |
| `selfWidget.item(id)` | Frozen item helper | Select an instance-local item state/file namespace. This creates a helper, not a separate runtime or preset. |
| `selfWidget.itemFor(element)` | Frozen item helper | Use the nearest ancestor with `data-dikciz-item-id`. Throws if there is no valid item ID. |

`selfWidget.cell` contains zero-based `column`/`row` and positive `columnSpan`/`rowSpan`. The page document contains `id`, `address`, `title`, one-based `position.horizontal`/`position.vertical` and a `widgets` map keyed by ID and full address. The page map also accepts IDs, addresses and `home`. These are injected observations; use commands for fresh saved data.

Widgets keep their complete HTML/CSS/JS package. Item helpers are optional namespaces inside that document. A `data-*` attribute does not run a clock, fetch weather or attach an independent script. The document's own JavaScript owns that behavior.

## Item state and files

| Member | Parameters | Return | Behavior |
| --- | --- | --- | --- |
| `item.id` | None | String | Selected item ID. |
| `item.storagePath` | None | String | `<selfWidget.storagePath>/items/<id>`. |
| `item.state` | None | JSON object | Last delivered state, or `{}`. |
| `item.getState()` | None | `Promise<object>` | Reads fresh wrapper state and selects this item. |
| `item.patchState(values)` | JSON object | `Promise<result>` | Replaces this item's state while preserving sibling items. Writes serialize within this document. |
| `item.readFile(name, options = {})` | Basename; `fileRead` options | `Promise<fileRead result>` | Forces the path to this item directory. |
| `item.writeFile(name, dataBase64, options = {})` | Basename, Base64 bytes, `fileWrite` options | `Promise<fileWrite result>` | Creates the item directory, registers state if absent, then writes. Defaults to overwrite. Supplied path/data cannot redirect the helper. |

Item IDs start with an ASCII letter/digit and contain up to 80 ASCII letters, digits, underscores, dots or hyphens. File basenames have the same character set and a 120-character limit. A basename cannot contain `/`. Use `patchState` for `state.json`; `writeFile` rejects that reserved name. These helpers select a location; trusted document code can independently call the broader phone file APIs.

Instance storage belongs to the exact page/widget identity. Independent copies have separate state. Removing the owning widget removes its state and instance files. See [configuration and instance storage](dikciz-configuration.md).

## Custom app interfaces

Custom app WebViews receive the same bridge object, with an app-local command host. `selfWidget.id`/`appId` identify the app, `state` is its delivered state, `address` is the app ID and `storagePath` ends in `/apps/<app-id>/files`. Page/cell metadata is absent. Page-address helpers such as `selfWidget.get`, `patch`, `patchDom` and item helpers are page-widget operations; their presence on the shared object does not make an app ID a valid widget address. Use `customAppRead`, `customAppState` and ordinary file commands for the app's own data.

`selfWidget.patchState` works for app-local state and merges top-level keys. App-local `dispatch` without an explicit HTML `sourceWidgetAddress` accepts exactly one `patchState` action. Other page-action helpers, including `emitEvent`, require a real HTML source widget or an app backend that returns `emitEvent`. Use `dikciz.command("customAppEvent", {event: "example.clicked", payload: {}})` to invoke the app backend. The host fills omitted `appId` for app-local read/state/event/schedule/alert commands. Their complete schemas are in the command reference.

App-local native results can omit the launcher's `revision`; consumers must not require that optional metadata. `command()` still unwraps `result` and `request()` still returns the envelope. See [app lifecycle, timers and alerts](custom-apps.md).

## Events

| API/event | Payload | Behavior |
| --- | --- | --- |
| `dikciz.onEvent(listener)` | Callback receives the canonical event object | Returns an unsubscribe function; a non-function listener throws TypeError. The document also needs a matching enabled native/custom subscription. |
| `dikciz-ready` | No required detail | The bridge port is ready. |
| `dikciz-event` | `event.detail` is the canonical event | Browser alternative to `onEvent`. Registering both runs both callbacks. |
| `dikciz-state` | `event.detail` is the current instance state | Native persisted state update. |
| `dikciz-item-state` | `event.detail` is the item-state map | Native item-state update. |
| `dikciz-visibility` | `{visible: boolean}` | Native visibility change. |

`dikciz.receiveEvent`, `receiveState` and `receiveItemStates` accept JSON strings and are native delivery hooks. Applications should subscribe rather than call them to simulate persisted state. Browser globals prefixed `dikcizSelfWidget`, `dikcizWidgetState` and `dikcizWidgetVisible` are host injection details, not persistence APIs.

```javascript
let unsubscribe;
window.addEventListener("dikciz-ready", () => {
    unsubscribe = dikciz.onEvent(event => {
        if (event.type === "example.updated") {
            document.querySelector("#status").textContent = JSON.stringify(event.payload);
        }
    });
});
window.addEventListener("pagehide", () => unsubscribe?.());
```

Event fields, source subscriptions and typed actions are shared with [Lua](lua-api.md). The [scripting tutorial](scripting-tutorial.md) builds a complete HTML/event/backend round trip.

## Background JavaScript

| Member | Parameters | Return | Meaning |
| --- | --- | --- | --- |
| `onEvent(event)` | Canonical event | Synchronous action-result object | Entry point in `main.js`. No Promise return. |
| `context.scriptId` | None | String | Current script/backend identity. |
| `context.state` | None | JSON object | Persisted state snapshot for this invocation. |
| `context.widgets` | None | Address-keyed object | Observed launcher widget context. |
| `context.androidAccess` | None | JSON object | Observed Android access. It does not grant access. |
| `context.accessibility` | None | Object or absent | Optional current accessibility context. |
| `context.request(objectOrJsonString)` | One complete command | Canonical response object | Synchronous native command. Check `response.type` and the operation's own outcome. |
| `context.requestJson(objectOrJsonString)` | One complete command | JSON response string | Same execution, serialized response. |

The background interpreter has no `window`, DOM, browser timers, `fetch`, Node.js `require` or Java class access. Globals are fresh for each invocation. Context and event objects are recursively frozen. Return `null`/`undefined` for no actions, one typed action, an action array or `{status, actions, result}` with the shared Lua contract; at most eight returned actions are accepted. `result` is an optional JSON object, used for an app-event response. The handler must finish synchronously. Use native commands, subscriptions and custom-app timers. [Background JavaScript](background-javascript.md) owns exact instruction, CPU, memory, stack and serialization limits. The same returned-action contract and policies as Lua apply.

## Errors, timeouts and authority

`command()` rejects operation errors with `.code`, `.message`, `.requestId`, `.revision`, `.permissionRequest` and `.response` when supplied by native code. Local bridge errors include `not_ready`, `busy`, `validation_failed`, `timeout` and `page_closed`. Closing the document rejects pending replies; it does not undo submitted native effects. `request()` and backend `context.request()` return canonical operation errors as data. Native malformed commands, absent Android access, unavailable hardware and explicit cancellation remain distinct outcomes.

HTML, Lua and JavaScript are direct local owner bindings. They do not need a bearer token. Remote token scopes govern remote callers; installing or enabling code can confer the Android access Dikciz already has. Returned-action policies do not sandbox `context.request`. Chat read-only mode remains a separate executor restriction. Review the [grant reference](token-permissions.md) before delegating remote execution or source installation.
