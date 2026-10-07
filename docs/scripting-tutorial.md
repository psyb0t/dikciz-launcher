# Dikciz scripting tutorial

Dikciz has two ways to make the home react to the phone. HTML widgets draw and handle visible interaction. Lua scripts run in the background when an event arrives. Both use the same event bus, page and widget addresses, action executor, Android access state, and logs.

This tutorial builds one small pipeline that you can copy and extend:

```text
HTML button -> custom event -> Lua script -> saved widget state + custom result event -> HTML update
```

The same shape works for battery, location, notifications, calendar changes, sensors, media sessions, Android accessibility events, and the other native event sources listed below.

The [configuration guide](dikciz-configuration.md) owns the public file schema. [Automation control](automation-control.md) owns the exact WebSocket and MCP protocol. This guide explains how to combine them into working automations.

## Start with the right pieces

Install Dikciz, make it your Android Home app, and grant shared-storage access. The first run creates `/sdcard/Dikciz/`, which holds every script, policy, subscription, widget, package, state file, and log.

Use the native launcher for the visible parts:

1. Add an **HTML widget** from **Add to this page** when you need a visible control or display.
2. Open **Settings**, then **Scripts**, then **New Lua script** when you need background or event-driven logic.
3. Open **Settings**, then **Automation**, then **Set up full device access** before using an Android source or action that needs owner approval.

The script editor writes a script's title, enabled state, source, and state. The public automation files choose which events it receives and which actions it may return. A script without both an enabled policy and an enabled subscription can show source in the editor but cannot complete an automation action.

## Know what talks to what

Every native event and custom event has the same envelope:

```json
{
  "type": "notificationPosted",
  "source": "notification-listener",
  "timestampMilliseconds": 0,
  "coalescingKey": "optional-key",
  "payload": {}
}
```

`type` selects the event. `payload` carries the event-specific data. Lua receives the event as its `event` argument. An HTML widget receives the same record through `dikciz.onEvent(...)` and the browser `dikciz-event` event.

The delivery path is deliberately simple:

```text
Android source or custom event
          |
          +--> enabled Lua subscription --> on_event(event) --> typed actions
          |                                           |              |
          |                                           |              +--> Android or launcher work
          |                                           |              +--> saved widget state
          |                                           |              +--> custom result event
          |
          +--> rendered HTML widget subscription --> dikciz.onEvent(listener) --> DOM update
```

An HTML widget only receives events while its page is selected and its WebView exists. Lua continues to receive enabled subscriptions in the background. Put durable work in Lua. Put rendering and taps in HTML.

## Use stable page and widget addresses

A page has a human-readable address such as `1H1V`. A widget address joins that page and the widget ID:

```text
1H1V-command-deck
2H1V-weather-card
```

Use a full address only when talking to another widget. An HTML widget should use `selfWidget` for itself so moving it to another page does not break its code.

```javascript
console.log(selfWidget.id);       // command-deck
console.log(selfWidget.page);     // 1H1V
console.log(selfWidget.address);  // 1H1V-command-deck
console.log(selfWidget.cell);     // { column, row, columnSpan, rowSpan }
```

Use `dikciz.pages()` to inspect the current page map. It exposes page IDs, addresses, and the `home` alias. Use `await dikciz.home()` or `await dikciz.widget(address)` after a configuration change when you need a fresh saved record.

```javascript
const pages = dikciz.pages();
const home = pages.home;
const target = await dikciz.widget("2H1V-weather-card");
```

Do not save pixel coordinates. `cell` is the persistent grid rectangle. Screen bounds change with rotation, density, and the current device.

## Subscribe to events

Each Lua subscription, and each HTML widget `eventSubscriptions` entry, has this common shape:

```json
{
  "event": "battery",
  "minimumIntervalMilliseconds": 60000,
  "coalescingKey": "battery-card"
}
```

`minimumIntervalMilliseconds` is a per-subscription delivery floor. It prevents that one subscriber from receiving the same source too often. `coalescingKey` is optional and helps make repeated work identifiable. It is not a global throttle.

Lua also needs the matching policy capability. HTML widgets do not use Lua policy files, but Android still requires the relevant permission or special access.

### Every built-in event

| Event | Lua capability | Extra subscription fields or notes |
| --- | --- | --- |
| `time` | `time` | Current time source. |
| `battery` | `battery` | Battery level and status changes. |
| `charging` | `charging` | Charging state changes. |
| `powerSaveMode` | `powerSaveState` | Power Saver changes. |
| `deviceIdleMode` | `deviceIdleState` | Device idle changes. |
| `nightMode` | `nightModeState` | System night-mode changes. |
| `deviceConfiguration` | `deviceConfiguration` | Device configuration changes, including font scale. |
| `packageChanged` | `appPackagesMetadata` | Installed package metadata changes. |
| `ringerMode` | `ringerModeState` | Ringer-mode changes. |
| `interruptionFilter` | `interruptionFilterState` | Do Not Disturb interruption-filter changes. |
| `connectivity` | `connectivity` | Network state changes. |
| `thermalStatus` | `thermalState` | Thermal-status changes. |
| `bluetoothState` | `bluetoothState` | Bluetooth state changes. |
| `screen` | `screen` | Screen on and off changes. |
| `userPresent` | `userPresence` | Android reports that the user is present. |
| `alarm` | `alarm` | Requires `alarmIntervalMilliseconds`. |
| `sensor` | `sensors` | Requires `sensorTypes` and `samplingPeriodMicroseconds`. |
| `healthDailySteps` | `healthSteps` | Requires `healthRefreshIntervalMilliseconds`. |
| `location` | `locationApproximate` or `locationPrecise` | Requires `locationPrecision`, either `approximate` or `precise`. |
| `calendarEvent` | `calendarEventsMetadata` or `calendarEventsContent` | Content capability grants the richer calendar record. |
| `contactsChanged` | `contactsMetadata` | Contacts change notification. |
| `phoneState` | `phoneState` | Phone call-state changes. |
| `smsReceived` | `smsMetadata` or `smsContent` | Content capability grants the richer SMS record. |
| `notificationPosted` | `notificationsMetadata` or `notificationsContent` | Optional `packages` filter. |
| `notificationRemoved` | `notificationsMetadata` or `notificationsContent` | Optional `packages` filter. |
| `mediaSession` | `mediaSessionsMetadata` or `mediaSessionsContent` | Optional `packages` filter. |
| `clipboardChanged` | `clipboardMetadata` or `clipboardContent` | Clipboard change notification. |
| `accessibilityWindow` | `accessibility` | Requires the owner to enable Dikciz in Android Accessibility settings. |
| `accessibilityServiceState` | `accessibility` | Reports `connected`, `interrupted`, or `disconnected`. |
| `widgetChanged` | `widgetState` | A Dikciz widget state change. |
| `deviceAdminState` | `deviceAdministration` | Device Administration state changes. |
| `manual` | `manualTrigger` | Produced by the script dashboard, an HTML command, WebSocket, or MCP trigger. |

Any other valid event name is a custom event. It must start with a letter, be 1 through 128 characters, and use only letters, digits, `.`, `_`, `:`, or `-`. Lua custom subscriptions use the `manualTrigger` capability.

### The special subscription fields

Use these fields only on the event that supports them:

```json
{
  "event": "alarm",
  "minimumIntervalMilliseconds": 0,
  "alarmIntervalMilliseconds": 60000
}
```

```json
{
  "event": "location",
  "minimumIntervalMilliseconds": 300000,
  "locationPrecision": "approximate"
}
```

```json
{
  "event": "sensor",
  "minimumIntervalMilliseconds": 0,
  "sensorTypes": [1],
  "samplingPeriodMicroseconds": 200000
}
```

```json
{
  "event": "notificationPosted",
  "minimumIntervalMilliseconds": 0,
  "packages": ["com.example.app"]
}
```

An approximate location payload rounds latitude and longitude to two decimal places. A precise subscription needs fine-location access and carries provider, latitude, longitude, accuracy, altitude, bearing, and speed. A precise subscription without fine location receives no event.

## Build a complete custom-event workflow

This example makes an HTML button ask a Lua script to update the widget and report back. It proves the full pipe without relying on a device sensor or third-party app.

### 1. Add an HTML widget

Use **Add to this page**, choose **Empty HTML widget**, then long press it and choose **Edit**. Set its HTML to:

```html
<main>
  <button id="ping">Run event pipeline</button>
  <output id="status">Waiting</output>
</main>
```

Set its CSS to:

```css
main { display: grid; gap: 12px; padding: 16px; font: 16px system-ui; }
button { min-height: 48px; }
```

Add this `eventSubscriptions` field to the widget's JSON record. It tells Dikciz to send the Lua result event into this rendered widget:

```json
[
  {
    "event": "example.pipeline.done",
    "minimumIntervalMilliseconds": 0
  }
]
```

Set the widget's JavaScript to:

```javascript
const status = document.querySelector("#status");

function show(message) {
    status.textContent = message;
}

window.addEventListener("dikciz-state", event => {
    show(event.detail.message || "Waiting");
});

window.addEventListener("dikciz-ready", () => {
    show(selfWidget.state.message || "Ready");

    dikciz.onEvent(event => {
        if (event.type !== "example.pipeline.done") return;
        show(event.payload.message);
    });

    document.querySelector("#ping").addEventListener("click", async () => {
        show("Sending event...");
        try {
            await dikciz.emitEvent("example.pipeline.request", {
                message: "The HTML widget started this run"
            });
        } catch (error) {
            show(`Event failed: ${error.message}`);
        }
    });
});
```

`dikciz-ready` means the native message channel is connected. `dikciz-state` carries a saved state replacement without a WebView reload. `dikciz.onEvent` handles a subscribed native or custom event while this widget is rendered.

### 2. Create the Lua script and policy

Open **Settings**, then **Scripts**, then **New Lua script**. Name it `example-pipeline` and paste this source:

```lua
function on_event(event)
  if event.type ~= "example.pipeline.request" then
    return { status = "Waiting for a pipeline request" }
  end

  local message = event.payload.message or "Pipeline request received"
  return {
    status = "Updated the widget",
    actions = {
      {
        type = "patchWidget",
        widgetAddress = "1H1V-example-widget",
        values = { state = { message = message } }
      },
      {
        type = "emitEvent",
        event = "example.pipeline.done",
        payload = { message = "Lua finished: " .. message }
      }
    }
  }
end
```

Replace `1H1V-example-widget` with the HTML widget's actual full address. Keep the code's `status` short. Dikciz shows it under the script name and saves it in the run status.

Create the matching public policy at `/sdcard/Dikciz/automation/policies/example-pipeline.json`:

```json
{
  "id": "example-pipeline",
  "title": "Example event pipeline",
  "enabled": true,
  "capabilities": ["manualTrigger"],
  "actions": ["patchWidget", "emitEvent"]
}
```

Create the subscription at `/sdcard/Dikciz/automation/scripts/example-pipeline.json`:

```json
{
  "scriptId": "example-pipeline",
  "policyId": "example-pipeline",
  "enabled": true,
  "subscriptions": [
    {
      "event": "example.pipeline.request",
      "minimumIntervalMilliseconds": 0,
      "coalescingKey": "example-pipeline"
    }
  ]
}
```

Save the script and ensure its switch is enabled. Tap the HTML button. The HTML widget emits `example.pipeline.request`, Lua receives it, saves the widget state, emits `example.pipeline.done`, and the widget renders the result without a page or WebView reload.

### 3. Check the result

Open the native script dashboard or **Settings**, then **Scripts**, and open the script logs. You should see the event, its final status, both action outcomes, and any reason for a failure. The dashboard, Settings log viewer, WebSocket `scriptLogs`, and MCP `dikciz_script_logs` show the same bounded safe log projection.

## Read and change widget state

Use saved state for data that must survive page changes, a renderer restart, or a phone restart.

### Current widget state

```javascript
const current = selfWidget.state;
await selfWidget.patchState({ refreshedAt: Date.now() });
```

`selfWidget.patchState(values)` persists a replacement state for the current widget. The current rendered document receives `dikciz-state`. It stays alive.

### Another widget's saved state

```javascript
const result = await dikciz.patchWidgetState(
    "2H1V-weather-card",
    { temperature: "18 C", updatedAt: Date.now() },
);

console.log(result.actions[0].outcome);
```

The target does not need to be rendered for a saved-state update. If it is on the selected page, it receives `dikciz-state` immediately. Otherwise it sees the saved state when its page renders.

Lua uses the same action shape:

```lua
return {
  status = "Weather updated",
  actions = {
    {
      type = "patchWidget",
      widgetAddress = "2H1V-weather-card",
      values = { state = { temperature = "18 C" } }
    }
  }
}
```

### Patch the current DOM only

`patchDom` changes a selected, rendered HTML target immediately. It does not save source or state. Use it for a transient visible value. Use saved state as well when the value must survive a render.

```javascript
const result = await dikciz.patchDom(
    "1H1V-device-card",
    "#temperature",
    {
        text: "18 C",
        className: "fresh",
        attributes: { "data-updated": "now" }
    },
);
```

The allowed patch values are `text`, `html`, `value`, `className`, and `attributes`. `attributes` is an object whose values are strings. A target on another page reports `target_not_rendered`. Other useful outcomes are `selector_invalid`, `selector_not_found`, and `target_property_unavailable`.

Lua returns the same action:

```lua
{
  type = "patchDom",
  widgetAddress = "1H1V-device-card",
  selector = "#temperature",
  values = { text = "18 C", className = "fresh" }
}
```

## HTML bridge reference

The native bridge creates `window.dikciz`, `window.selfWidget`, and a `dikciz-ready` event. Start bridge work from that event. Do not call the transport-only `receiveEvent` or `receiveState` functions yourself.

| API | Use it for |
| --- | --- |
| `dikciz.command(type, fields)` | Run one launcher command and receive its result or rejected promise. Examples: `diagnostics`, `automationStatus`, `homeGet`, `widgetGet`, `scriptLogs`, `accessibilitySnapshot`, `shell`, and `intent`. The full command list is in [Automation control](automation-control.md). |
| `dikciz.dispatch({ actions })` | Run one non-empty array of typed actions through the same executor Lua uses. Read `result.actions` for each outcome. |
| `dikciz.emitEvent(name, payload, coalescingKey)` | Emit one custom event. This is shorthand for a one-action dispatch. |
| `dikciz.onEvent(listener)` | Receive matching configured events while the widget renders. It returns an unsubscribe function. |
| `dikciz.fitContent()` | Ask the native frame to remeasure a `heightMode: "content"` widget. It never expands past the assigned grid cells. |
| `dikciz.pages()` | Read the current page map by ID, address, and `home`. |
| `dikciz.home()` | Read the current saved home document. |
| `dikciz.widget(address)` | Read one current saved widget record. |
| `dikciz.patchWidget(address, values)` | Persist a full allowed widget-field patch. HTML widgets accept `title`, `html`, `css`, `javascript`, and `state`. Replacing source can destroy the calling WebView. |
| `dikciz.patchWidgetState(address, values)` | Persist only another HTML widget's `state`. |
| `dikciz.patchDom(address, selector, values)` | Change a rendered HTML target without saving it. |
| `selfWidget.get()` | Read this widget's current saved record. |
| `selfWidget.patch(values)` | Persist an allowed patch to this widget. |
| `selfWidget.patchState(values)` | Persist this widget's state and receive `dikciz-state`. |
| `selfWidget.patchDom(selector, values)` | Change a selected, rendered element inside this widget. |

The browser also receives these events:

| Event | Meaning |
| --- | --- |
| `dikciz-ready` | The native bridge is ready. |
| `dikciz-state` | This widget's saved state was replaced. Read `event.detail`. |
| `dikciz-event` | A matching native or custom automation event arrived. Read `event.detail`. |
| `dikciz-block-appended` | Native **Add block** saved and inserted a source block. `event.detail.instanceId` identifies it. |

## Return device and launcher actions from Lua

Lua returns one status plus an `actions` array. Each action must appear in the script policy's `actions` list. HTML can dispatch the same actions directly because its owner pressed a visible widget control, but Android access checks still apply.

| Action | What it does |
| --- | --- |
| `selectPage` | Select a page by `pageId`. |
| `patchWidget` | Persist allowed HTML widget fields or state by full widget address. |
| `patchState` | Persist the current script's state. |
| `patchDom` | Change a currently rendered HTML target without saving it. |
| `launchApp` | Open a flattened Android component. |
| `appAction` | Launch, open app info, uninstall, or force stop a component. Lua cannot use `addShortcut` because it has no selected-page context. |
| `postNotification` | Post an Android notification. |
| `sendSms` | Send an SMS when Android grants `SEND_SMS`. Android may show its own confirmation or rate-limit a non-default SMS app. |
| `explicitIntent` | Send an explicit Android activity or broadcast intent. Android decides whether it accepts it. |
| `emitEvent` | Publish a custom event with a JSON payload. |
| `mediaControl` | Control an allowed media session. |
| `mediaVolume` | Set the allowed media volume. |
| `notificationControl` | Invoke a supported notification action token. |
| `lockDevice` | Lock the screen after the owner enables Dikciz Device Administration. |
| `accessibilityAction` | Act on a fresh bounded Accessibility snapshot node. |
| `accessibilityGesture` | Send a bounded tap or swipe tied to a fresh Accessibility snapshot. |
| `accessibilityGlobalAction` | Ask Accessibility to perform back, home, recents, notifications, quick settings, lock screen, or screenshot. |

For cross-app actions, first obtain a fresh `accessibilitySnapshot`. Its `snapshotId` expires after five seconds. Dikciz rechecks the active window and rejects an expired or changed snapshot instead of acting on stale UI.

Lua has no arbitrary shell, filesystem, Android-object, or dynamic-load access. Its supported globals are `assert`, `error`, `ipairs`, `next`, `pairs`, `pcall`, `select`, `tonumber`, `tostring`, `type`, and `xpcall`, plus `math`, `string`, `table`, and `bit32`. `io`, `os`, `package`, `require`, `load`, `dofile`, `loadfile`, `debug`, and `print` are unavailable.

## Trigger work and diagnose failures

There are four normal ways to start a workflow:

1. Run a script from the dashboard. It produces a `manual` event.
2. Dispatch an action from an HTML button.
3. Emit a custom event from HTML, Lua, WebSocket, or MCP.
4. Subscribe to an Android source such as time, location, notification, sensor, or Accessibility.

When a result surprises you, check the script's status and logs first. Common outcomes are:

| Outcome | Meaning and next step |
| --- | --- |
| `capability_denied` | Add the event capability or action to the enabled policy. |
| `android_permission_denied` | Open **Settings**, then **Automation**, then grant the Android access. |
| `device_admin_inactive` | Enable Dikciz Device Administration before `lockDevice`. |
| `accessibility_not_enabled` | Enable Dikciz cross-app automation in Android Accessibility settings. |
| `root_unavailable` | The device did not grant `su`. Root-only work did not run as the app user. |
| `target_not_rendered` | `patchDom` targeted a widget that is not currently rendered. Use saved state or select the page first. |
| `invalid_lua` | Open the script log. Its diagnostic names the source line when LuaJ provides it. |

Finish every workflow by checking all three layers:

1. The visible widget result or Android effect.
2. The script dashboard status and logs.
3. The saved files under `/sdcard/Dikciz/` when your flow persists state.

That gives you a stable loop: subscribe, inspect `event`, return typed actions, observe their exact outcome, and keep visible HTML state separate from background Lua logic.
