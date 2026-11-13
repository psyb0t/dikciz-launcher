---
title: "Custom apps"
description: "Build editable HTML, CSS and JavaScript apps with Lua or JavaScript backends, independent state, events, native timers and urgent alerts."
permalink: /custom-apps/
nav_order: 3
parent: "Developer manual"
---

# Custom apps

Open **Dikciz**, **Apps** to open, create, edit, enable, disable, export or remove a complete app. Each app combines its own HTML/CSS/JavaScript interface with a Lua or headless JavaScript backend and persistent state. Closing its WebView leaves subscribed events and scheduled backend work available through the native host.

Each app opens in its own Android task, with its title in Recents and its full interface below the system bars. There is no permanent Apps/Close toolbar around the app. Home leaves it available in Recents; Back exits its interface. Reopening the same app reuses its task. Return to **Dikciz > Apps** to manage bundles.

These are apps inside Dikciz. Android does not install a separate APK or grant them separate Android identities. Every app inherits the Android access granted to Dikciz, including its phone commands, files, shell and SDK access. There is no separate app permission screen, command allowlist or manifest approval. Android still enforces the permissions and special access granted to the Dikciz package.

## Bundled apps

| App | Behavior |
| --- | --- |
| Travel todo | Editable checklist with an opt-in GPS arrival rule and a custom-event input. Follow the [complete tutorial](todo-app-tutorial.md). |
| SOS check-ins | Editable contact plans, preview, explicit arming, cancellation countdown, tracked SMS and periodic location updates. All workflow decisions live in `apps/sos/main.js`; the UI lives beside it. |
| Voice notes | Record, save and replay local audio. Recording begins on a tap and stops when the app is hidden. Saved files belong to this app. |
| Agenda and alarms | Upcoming calendar entries, next Android alarm and links to Android calendar/alarm apps. Calendar access is optional and requested explicitly. |
| Device status | Live battery, memory, storage, process CPU history and Android-exposed per-core data. Restricted system counters remain explicit. |

Bundled source installs only when absent. Updates preserve customized bundles. Removing a bundled app records that choice, so reopening Home does not silently reinstall it. Voice notes, Agenda and Device status reuse the complete widget documents as independent apps; their UI polling stops when hidden. SOS uses its backend for deadlines and outgoing effects. Geofences, route capture and the visual automation editor remain native features.

## Files and state

```text
/sdcard/Dikciz/apps/<app-id>/
  app.json
  ui.html
  ui.css
  ui.js
  main.js          # main.lua when language is lua
  state.json
  files/          # recordings and other app-owned data
```

`app.json` declares version 1, ID, title, description, language and events. The optional legacy `commands` array is informational metadata and never restricts access. IDs start with a letter and contain up to 64 letters, digits, underscores or hyphens. `imports` and `exports` are reserved. Example:

```json
{
  "version": 1,
  "id": "example-counter",
  "title": "Event counter",
  "description": "Count custom events without keeping a page open.",
  "language": "javascript",
  "events": ["example.count"]
}
```

Optional `eventSubscriptions` uses the existing [automation subscription format](dikciz-configuration.md#lua-scripts-and-automation), including native source filters and intervals. `events` is shorthand for exact event names with no interval. Optional `locationMonitoring: true` requests a visible location foreground service while timers are pending; it grants no location permission.

State belongs to the exact app ID, independent of page/widget state. Duplicate the bundle with a new manifest ID to get independent state and files. Removing an app deletes its entire public directory. Replacing its bundle preserves state, data and its running or stopped state. Export includes only manifest and source, excluding state, recordings and native execution records. Complete encrypted backups include source, state, recordings/files and installation/timer/run metadata. Restore leaves apps disabled by default and marks imported pending/claimed timers for review. Source-only exports remain useful for sharing an app without private data. See [backup and recovery](backups.md).

Source and manifest edits require no permission approval. UI source changes reload the visible app; state-file writes update its live state. Background source loads fresh for the next invocation. Invalid files and subscriptions report validation errors. Stop and Start control background execution, not permissions.

## Backend

JavaScript exports synchronous `onEvent(event)`; Lua exports `on_event(event)`. Both receive immutable `context.state`, `context.scriptId` and Android access context. JavaScript has no DOM, Node.js, fetch or browser timers. Its execution limits are the same as [background scripts](background-javascript.md#background-lifetime-and-limits). Globals are fresh on every call.

```javascript
function onEvent(event) {
    const count = (context.state.count || 0) + 1;
    return {
        status: "Counted",
        result: {count: count},
        actions: [{type: "patchState", values: {count: count}}]
    };
}
```

`result` is an optional JSON object returned to the caller. `patchState` merges fields; `emitEvent` publishes to the ordinary shared event bus. Other returned action types are rejected. Use `context.request({type: ...})` for phone commands using Dikciz's granted Android access and inspect the canonical `type: result` or `type: error` reply. Returning `result: {error: {code, message}}` rejects a request with that bounded app-defined error.

App-local `customAppRead`, `customAppState`, `customAppEvent`, `customAppSchedule` and `customAppAlert` bind to the calling app ID. Omitting `appId` selects self; an explicit `appId` can address another app. `customAppRuntime` supplies native wall/elapsed clocks, boot count, invocation ID and access/background observations. Apps can use the same app installation and control commands as other owner code.

## Interface

The [illustrated app workflow](screens.md#apps-and-complete-bundles) shows the manager overflow, Android Recents, file selector, keyboard and recovery dialogs. To edit, return to Dikciz > Apps and use the overflow beside the app title; the running app itself has no permanent management toolbar.

`ui.html`, `ui.css` and `ui.js` use the existing injected `window.dikciz` bridge and app-local command binding. `selfWidget` exposes this app's state and `storagePath`, ending in `/apps/<id>/files`. Its state patch merges top-level fields into app state, preserving unrelated keys and explicit nulls. Use ordinary DOM listeners and await bridge results. An app can call `customAppEvent` to invoke its own backend while its UI is open. Subscribe to `dikciz-state` for persisted state changes and ordinary event callbacks for subscribed native/custom events.

For a complete working reference, read [Travel todo](todo-app-tutorial.md) and its linked source files.

Tap an app's name to open it. Its **More actions** overflow contains editing, start/stop, export and removal. The native manager's **Edit bundle** exposes the complete manifest, HTML, CSS, UI JavaScript and backend source. Choose a file above the editor; switching files and rotating the screen preserve unsaved edits. Back asks before discarding changes. Failed saves keep the editor open; failed imports retain the archive path for correction and retry. State can be edited with `customAppState` or the public file. Saving preserves the app's running or stopped state. New apps start enabled. **Start app** and **Disable and stop** control execution without a permission dialog. Opening a stopped app starts it. The native manager is a separate activity, reachable again after Android permission screens take the foreground. Hiding or closing its interface sends `app.ui.hidden` to its backend. Voice notes uses that event to stop its recorder and preserve a pending save for reopening; other apps decide their own hidden behavior.

## Timers and alerts

`customAppSchedule` schedules a one-shot event with `action: schedule`, `timerId`, `event`, `delayMilliseconds` and optional object `payload`. Delay ranges from 1000 through 604800000 ms. `cancel` selects a timer ID; `cancelAll` selects the app. The host persists timers and owns native wake-up alarms and a visible foreground service. At most 64 timers may be pending across apps; completed history is bounded to 256 records.

Timers are claimed durably before invoking the backend. A handler may reschedule the same ID, which creates a new generation. Pending timers can survive process restart within the same boot. An interrupted claimed timer or a pending timer from another boot becomes `needs_review`; its original effect is not replayed. The host sends `app.resume` with `interrupted: true` for app-defined recovery. Android may delay alarms or reject background starts. Inspect timer state and the latest app run rather than treating scheduling as completed execution.

`customAppAlert` shows an urgent dimmed modal using `action: show`, `episodeId`, `durationMilliseconds`, `title`, `detail`, `cancelLabel` and `cancelEvent`. Duration is 1000 through 3600000 ms. Optional `announcement` and `finalAnnouncement` use `{seconds}`. Only one app can own the urgent alert. Android notification access and an urgent countdown channel are required. Overlay access allows presentation over other apps; otherwise an active Dikciz activity can host a dialog. Keyguard, volume, DND and background restrictions remain Android-controlled. `action: close` releases it.

The modal's cancellation button invokes the app's declared cancel event, then closes. Its native host owns window/audio/vibration cleanup. The app must cancel its pending timers and update its own state. The foreground notification's **Pause apps** invokes `app.stopped`, disables affected apps and cancels pending timers/alerts. For concurrent owner cancellation, `customAppEvent` accepts `interruptRunning: true`, which blocks further mutating calls from the current handler except state and cleanup commands, including geofence deletion. Already submitted effects cannot be recalled.

## Remote and assistant access

HTTP, WebSocket and MCP expose the same nine commands: `customAppsList`, `customAppRead`, `customAppInstall`, `customAppControl`, `customAppEvent`, `customAppState`, `customAppSchedule`, `customAppAlert` and `customAppRuntime`. Discover exact fields in the [command reference](command-reference.md). Install/import requires `confirm: true` and starts a new app enabled. Replacing preserves its running or stopped state. Start needs no confirmation; removal retains its destructive-action confirmation. Archives use `.dikciz-app.zip`, exactly five manifest/source entries, no directory entries, no traversal and a 2 MiB compressed-file ceiling. Each source is bounded to 256 KiB; state to 128 KiB and depth 16. At most 64 apps are installed.

These APIs accept full owner authority or explicit command/action grants. Existing named read grants do not gain execution access. App installation, enabling and event invocation can execute code with Dikciz Android access; those grants are not a code sandbox. See the [complete grant table](token-permissions.md). Read-only chat sees only list, read and runtime; the trusted executor rejects hidden mutations even when called directly. Read-only SOS preview also passes its read-only restriction into app command execution and rejects returned actions. Listing or reading records does not invoke source code. `customAppControl: open` reports a request, while `customAppRead.uiVisible` and `snapshot` report observed presentation.

The legacy `sosPlanSave`, `sosPreview`, `sosControl` and `sosStatus` commands delegate to the bundled SOS app. Existing saved plans migrate once into its public state. Active pre-migration runs become `needs_review`, with no automatic outgoing messages. See [SOS setup and outcomes](safety-and-journeys.md).
