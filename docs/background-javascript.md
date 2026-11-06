# Background JavaScript

JavaScript is a second background script language alongside Lua. It runs in an embedded interpreter on the automation worker, without a WebView, DOM or selected home page. Both languages share event subscriptions, repeating alarms, typed actions, state persistence, Android access and run-status logs.

The [Travel todo tutorial](todo-app-tutorial.md) shows a complete JavaScript app reacting to a native GPS event and a custom event, including its full source.

For a complete interface/backend bundle, use [custom apps](custom-apps.md). They reuse this interpreter and add app-local state, shared Dikciz command access, one-shot native timers and urgent modal hosting. Standalone automation scripts retain the lifecycle described below.

## Create a script

Open **Settings**, **Scripts**, **New JavaScript script**. The editor starts with `function onEvent(event)`. Saved JavaScript records include `"language": "javascript"`; existing records without `language` remain Lua. The only accepted language values are `lua` and `javascript`.

The public directory is `/sdcard/Dikciz/scripts/<id>/`. `script.json` contains `id`, `title`, `enabled`, `apiVersion` and optional `language`; source lives in `main.js` for JavaScript or `main.lua` for Lua. `state.json` stores explicit state and `run-status.json` records the latest outcome. Valid file changes reload through the existing watcher. Configuration replacement includes `language`, `source` and `state` in the expanded script object.

## Handle an event

```javascript
function onEvent(event) {
    const count = (context.state.count || 0) + 1;
    return {
        status: "Counter updated",
        actions: [
            {type: "patchState", values: {count: count}},
            {type: "emitEvent", event: "example.counter.updated", payload: {count: count}}
        ]
    };
}
```

This handler needs an enabled policy allowing `manualTrigger` and actions `patchState` and `emitEvent`, plus an enabled subscription. Use `event: "manual"` for `automationTrigger`, or the exact custom event name for another component's input. Do not add a separate `customEventName` field to subscription files. Read the [policy and subscription schema](dikciz-configuration.md#lua-scripts-and-automation) and [event pipeline](scripting-tutorial.md#build-a-complete-custom-event-workflow).

`event` contains `type`, `source`, `timestampMilliseconds`, `coalescingKey`, `payload` and local `time.hour24`/`time.minute`. `context` contains `scriptId`, `state`, `widgets`, `androidAccess` and optional `accessibility`. Input objects are read-only. Return typed actions to persist changes; `patchState` merges fields into this script's state and preserves explicit JSON nulls and array positions. A null value clears the saved value while retaining its key. Globals are fresh for each invocation. Lua uses `on_event`; JavaScript uses `onEvent`. The returned action schema is shared.

## Call the phone

`context.request(objectOrJsonString)` synchronously returns the canonical command response as a JavaScript object. `context.requestJson` returns its JSON text. Inspect `type`, errors, permission recovery and the actual operation outcome. Returned actions follow the script policy. These direct commands have the same owner authority as Lua commands and are not restricted by the returned-action allowlist. Enable only trusted code.

Use existing commands for application launch, speech, notifications, files and other Android operations. Use `emitEvent` to invoke other subscribed Lua scripts or rendered HTML widgets. HTTP, WebSocket, MCP and embedded chat manage and trigger the same script records through the existing executor. Remote token grants and chat read-only restrictions still govern those entrypoints.

## Background lifetime and limits

The existing foreground automation service delivers events even when Home is behind another app. A repeating `alarm` subscription uses `alarmIntervalMilliseconds`, minimum 60000. Android may delay wake-up alarms or deny background service starts. This backend does not add one-shot deadlines, cron or automatic replay of interrupted effects. HTML widget JavaScript remains a separate rendering context; its listeners still depend on a rendered WebView.

Handlers are synchronous. Returning a Promise fails with `invalid_result`. There is no browser event loop, `window`, `document`, `fetch`, Node.js `require` or browser timer API. Use event subscriptions and phone commands for those operations.

Execution shares Lua's 50000 observed-instruction budget, 250 ms setup CPU budget, 50 ms handler CPU budget and 2 MiB observed memory delta. Native command CPU is excluded; returned host data remains bounded. JavaScript also limits interpreter stack depth to 128 and serialized results to one MiB. These are interpreter checks and memory observations, not an isolated process heap limit. Java class access is disabled.

Syntax and execution errors record `invalid_javascript`; rejected results record `invalid_result`. Unexpected engine failures record `runtime_unavailable`. Instruction, CPU, memory and cancellation failures retain their shared status names. Inspect status and script logs after triggering. A publish acknowledgement does not prove another component finished.

## Transfer scripts

Native import/export uses the existing `.dikciz-script.zip` extension. A JavaScript archive contains `archive.json` with kind `dikciz-javascript-script`, `script.json` with the language, `main.js` and `state.json`. Lua archives retain kind `dikciz-lua-script` and `main.lua`. Import rejects a mismatched kind, source name, language or unexpected entry. Import does not create event subscriptions or grant Android permissions.
