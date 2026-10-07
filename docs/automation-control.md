# Dikciz automation control

Dikciz exposes two versioned local control planes. They call the same launcher command target.

| Interface | Device listener | Lab host listener after `make dikciz-run` |
| --- | --- | --- |
| WebSocket | `ws://127.0.0.1:19001/v1/automation` | `ws://127.0.0.1:19101/v1/automation` |
| Streamable HTTP MCP | `http://127.0.0.1:19002/mcp` | `http://127.0.0.1:19102/mcp` |

Both listeners bind only to device loopback. ADB forwarding is the normal remote route. `make forward` creates generic host-loopback forwards on `19001` and `19002`. `make dikciz-run` owns its separate `19101` and `19102` forwards.

## Discover the current build

Use the live contract instead of copying command names into a client:

```bash
python3 scripts/dikciz-control.py websocket controlStatus --port 19101 --pretty
python3 scripts/dikciz-control.py websocket automationStatus --port 19101 --pretty
python3 scripts/dikciz-control.py mcp-tools --port 19102 --pretty
```

`controlStatus` lists the protocol version, commands, fields, matching MCP tools, input limits, safe-mode state, and persistent event schema. `automationStatus` lists current Android access, policy vocabulary, event subscription fields, and available sensors. Treat these responses as the source of truth for a running build.

The supplied client is dependency-free:

```bash
python3 scripts/dikciz-control.py websocket snapshot --port 19101 --pretty
python3 scripts/dikciz-control.py mcp dikciz_script_logs --port 19102 --pretty
python3 scripts/dikciz-control.py events --port 19101 --count 10
```

`--arguments` accepts JSON or `@path/to/file.json`. The `events` command emits one WebSocket event per JSON line.

## Bearer password

Remote access starts open for deliberate local forwarding. On the phone, open **Settings**, then **Automation**, then **Configure remote bearer authentication** to set a password. The phone can replace or clear it without the old password. The public record stores a bcrypt verifier and enabled state at `/sdcard/Dikciz/control/remote-auth.json`, never the plaintext password.

When enabled, WebSocket upgrades and all MCP requests need:

```text
Authorization: Bearer <password>
```

The client reads the value from `DIKCIZ_REMOTE_BEARER_TOKEN` by default. Remote clients can inspect, enable, and disable the requirement but cannot retrieve, replace, or clear the password.

## WebSocket

Start each connection with `hello`:

```json
{
  "requestId": "00000000-0000-4000-8000-000000000001",
  "type": "hello",
  "protocolVersion": 1
}
```

Each later request has a UUID `requestId` and a `type`. Replies carry that same ID. Clients must match by ID instead of receive order. A successful reply has `type: "result"`. A rejected request has `type: "error"`, a finite `code`, and a message.

| Group | WebSocket commands | MCP form |
| --- | --- | --- |
| Discover | `controlStatus`, `automationStatus` | `dikciz_control_status`, `dikciz_automation_status`. |
| Read | `snapshot`, `find`, `waitFor`, `homeGet`, `widgetGet`, `configGet`, `scriptLogs`, `diagnostics`, `screenshot`, `uiDump`, `remoteAuthStatus` | Matching tool listed by `controlStatus`. |
| Configuration | `configReplace`, `configSeed`, `reset` | `dikciz_config_replace`, `dikciz_config_seed`, `dikciz_config_reset`. |
| Semantic UI | `tap`, `longPress`, `selectPage`, `scrollBy`, `scrollTo`, `setText`, `launchApp` | Matching tool listed by `controlStatus`. |
| Placement | `addWidget`, `widgetMove`, `widgetResize` | `dikciz_add_widget`, `dikciz_widget_move`, `dikciz_widget_resize`. |
| Page grid | `gridSet` | `dikciz_grid_set`. |
| Launcher apps | `appCatalogue`, `appAction` | `dikciz_app_catalogue`, `dikciz_app_action`. |
| Automation | `automationTrigger`, `automationDispatch`, `automationServiceSync`, `openAutomationSetup` | Matching tool listed by `controlStatus`. |
| Renderer diagnostic | `htmlWidgetRendererCrash` | `dikciz_html_widget_renderer_crash`. |
| Cross-app Android UI | `accessibilitySnapshot`, `accessibilityAction` | `dikciz_accessibility_snapshot`, `dikciz_accessibility_action`. |
| Device | `shell`, `intent` | `dikciz_shell`, `dikciz_intent`. |
| Bearer setting | `remoteAuthEnable`, `remoteAuthDisable` | Matching tool listed by `controlStatus`. |

`configReplace` and `configSeed` require one complete assembled `config` object. They validate and save atomically. `reset` restores the bundled home and bundled automation files.

`tap`, `longPress`, and `launchApp` require `semanticId`. `selectPage` needs `pageId`. `addWidget` needs `widgetType`. `widgetMove` and `widgetResize` need `widgetAddress` and `cell`. `gridSet` needs `columns`, `rows`, `gapDp`, and `outerPaddingDp`. `setText` needs `semanticId` and `text`. `automationTrigger` needs `scriptId`. `automationDispatch` needs `sourceWidgetAddress` and `actions`. Read `controlStatus` for all current field and range rules.

`appCatalogue` lists the launchable Android apps with their semantic IDs, human
labels, package names, and flattened components, plus the supported app actions.
Its optional `query` filters case-insensitively on label and package name.
`appAction` needs `action` and a flattened `component`. The actions are `launch`,
`addShortcut`, `appInfo`, `uninstall`, and `forceStop`. `addShortcut` places an app
shortcut tile in the first free grid cell of the selected page, and reports
`page_full` when the page has no free cell. `uninstall` and `appInfo` hand off to
Android, which runs its own confirmation. `forceStop` needs root and reports
`root_unavailable` when `su` is missing or denied. Dikciz never falls back to
the app shell for it. Lua action records have no selected-page context, so they
must not send `appAction` with `addShortcut`.

On the device an app group is made by dropping one app tile onto another, not
from the widget picker. Automation still builds one directly: `addWidget` takes
`appGroup`, and `configReplace` accepts a group with its `components` listed.

The native app drawer uses this same catalogue and action runtime. It opens from
the launcher controls route or an upward swipe that begins on the horizontal
page rail. Its own search is case-insensitive on label and package name. Tap a
row to launch its app. Use the overflow button or a long press to expose the
remaining app actions.

`openAutomationSetup` opens the native Automation access sheet so the phone owner
can grant an Android access that a denied action needs.

`shell` uses `/system/bin/sh -c` when `root: false`. When `root` is absent or true, Dikciz first checks `su -c id -u`. Only exactly `0` allows the requested command to run and report `executionScope: "root"`. A missing, denied, malformed, or timed-out probe returns WebSocket error `root_unavailable` or an MCP tool error. Dikciz never relabels or falls back to the app shell. Successful results include `executionScope`, `exitCode`, `output`, and `outputTruncated`. `intent` supports `activity` and `broadcast`. Android decides whether the requested component can run.

`automationTrigger` queues one `manual` event for an enabled script with an enabled manual subscription. Acceptance means the event was queued. Read script status and logs to see whether its actions completed.

`htmlWidgetRendererCrash` and `dikciz_html_widget_renderer_crash` take a
rendered HTML `widgetAddress`. They call Android WebView's documented renderer
crash URL for that widget so a developer can verify renderer-loss recovery.
The immediate result is `crash_requested`. Read `scriptLogs` or
`dikciz_script_logs` for `html_widget_renderer_gone` followed by
`html_widget_renderer_recovered`. Android can place several WebViews in one
renderer process, so it can report loss for more than one widget. Dikciz
recreates each reported widget in place. It does not rebuild the selected page
or write configuration.

## Cross-app Android UI

Enable **Dikciz cross-app automation** in Android's Accessibility settings
before requesting a cross-app tree. The Automation access sheet opens that
system panel. Dikciz can report the missing access and open the panel, but
Android requires the phone owner to turn the service on. Neither a WebSocket or
MCP client nor a Lua or HTML widget can enable it.

`accessibilitySnapshot` and `dikciz_accessibility_snapshot` return the active
Android window as one bounded document:

```json
{
  "snapshotId": "opaque-id",
  "packageName": "com.example.app",
  "windowId": 12,
  "nodeCount": 4,
  "truncated": false,
  "nodes": []
}
```

Each node has a generated path ID such as `node.2.1`, nullable `text`,
`contentDescription`, `viewIdResourceName`, finite supported `actions`, state,
and screen bounds. A child that contains a visible label can have no action of
its own. Walk its generated parent path to find the enclosing clickable row.
The snapshot contains at most 256 nodes, descends at most 16 levels, and bounds
copied text fields to 512 characters. Dikciz retains at most 32 live snapshot
descriptors. The next snapshot request returns `accessibility_snapshot_limit`
until a five-second descriptor expires.

`accessibilityAction` and `dikciz_accessibility_action` require `snapshotId`,
`nodeId`, and one action: `click`, `longClick`, `focus`, `clearFocus`,
`scrollForward`, `scrollBackward`, `setText`, or `select`. `setText` also
requires `text`, with a maximum of 8,192 characters. The snapshot ID lasts five
seconds. Before acting, Dikciz resolves the current root and node again. It
rejects expired or changed windows, including a visible update elsewhere in the
captured tree, instead of trusting a retained Android node.
An expired descriptor returns `accessibility_snapshot_expired`; a descriptor
that still exists but no longer matches its active window returns
`accessibility_snapshot_stale`.
An accepted action reports `executed`; Android can still return
`android_rejected`.
If the Accessibility service is unavailable, `accessibility_not_enabled` takes
precedence over a retained descriptor's expiry. That result tells an unattended
script to report the access problem instead of a stale local snapshot.

A rejected action from an enabled automation script writes the normal action
and script-failure records to `/sdcard/Dikciz/logs/`. WebSocket `scriptLogs` and
MCP `dikciz_script_logs` return the same bounded projection. When
`logging.notifyOnScriptError` is enabled and Android allows notifications, the
existing per-script quiet period also applies to rejected actions. A direct
WebSocket or MCP action returns its exact synchronous error without creating a
background script-error notification.

The focused `make dikciz-test-accessibility-third-party` target installs a
separate local Android fixture and proves both remote transports act on its
visible native button. It also proves that an update anywhere in the captured
fixture tree invalidates an earlier descriptor before another action can report
success. A physical-phone run can prove owner-state parity after a clean current
install, but direct cross-app actions remain unavailable until the owner enables
Accessibility in Android settings.

`automationDispatch` and `dikciz_automation_dispatch` also accept two typed
cross-app actions. `accessibilityGesture` requires a current `snapshotId`, a
`gesture`, and `durationMilliseconds`. A `tap` supplies integral `x` and `y`.
A `swipe` supplies integral `startX`, `startY`, `endX`, and `endY`. Coordinates
must be visible screen coordinates. Duration is one through 10,000
milliseconds. Dikciz rechecks the snapshot root before asking Android to run
the gesture, then returns `executed`, `gesture_cancelled`, `gesture_timeout`,
or `android_rejected`.

`accessibilityGlobalAction` takes one `action`: `back`, `home`, `recents`,
`notifications`, `quickSettings`, `lockScreen`, or `takeScreenshot`. Android
decides whether a particular global action is currently available. Dikciz
returns its actual result. Accessibility does not provide arbitrary hardware
key injection.

The commands return exact errors for disabled access, unavailable windows,
unknown, expired, stale, or over-limit snapshots, missing nodes, unsupported
actions, and invalid text. `automationStatus.androidAccess.accessibility` and the
`deviceAccess` record show the actual owner-controlled state. The two
transports return the same status document.

The Accessibility service publishes `accessibilityWindow` into the automation
event bus. Its bounded payload contains `packageName`, `className`,
`eventType`, and `windowId`. It also publishes
`accessibilityServiceState` with a fixed `state` of `connected`, `interrupted`,
or `disconnected`, plus the current `enabled` Boolean. A policy needs the
`accessibility` capability to subscribe to either event. Lifecycle events do
not contain an Android window snapshot.

Lua receives `context.androidAccess` on every event. It is a read-only copy of
the `automationStatus.androidAccess` map, so a script can report an
owner-controlled missing capability before it tries an action. During an
`accessibilityWindow` event, Lua also receives the bounded active-window
document as `context.accessibility`. It contains `snapshotId` and `nodes`, not
a retained Android object. Return an action in the normal event result:

```lua
return {
  actions = {
    {
      type = "accessibilityAction",
      snapshotId = context.accessibility.snapshotId,
      nodeId = "node.2",
      action = "click"
    }
  }
}
```

Lua can return the same gesture and global-action records. For a coordinate
tap, derive integer coordinates from a visible node's `bounds` in the copied
snapshot. HTML does the same through `dikciz.dispatch({ actions: [...] })`:

```javascript
const snapshot = await dikciz.command("accessibilitySnapshot");
await dikciz.dispatch({
    actions: [{
        type: "accessibilityGesture",
        snapshotId: snapshot.snapshotId,
        gesture: "tap",
        x: 200,
        y: 400,
        durationMilliseconds: 100,
    }],
});
```

An HTML widget first calls `await dikciz.command("accessibilitySnapshot")`,
then sends the same action record through `dikciz.dispatch({ actions: [...] })`.
Lua and HTML both use the same parser, live node re-resolution, action runtime,
outcome log, and Android access checks as WebSocket and MCP. A stale or invalid
direct action returns its exact accessibility outcome in the action result. It
does not retain a node or click a replacement control.

## Events and semantic UI

Persistent WebSocket events include `automation_command_completed`,
`automation_ui_rendered`, `html_widget_event`, and `automation_event`.
`automation_ui_rendered` reports the selected page after an immediate native
page selection. `automation_event.fields.event` is the same copied
automation event document that scripts and HTML widgets receive: `type`,
`source`, `timestampMilliseconds`, `coalescingKey`, and `payload`. Dikciz
continues publishing it while its native Activity is behind another Android
screen, as long as the control plane remains running. MCP is request and
response only. Use `automationStatus`, configuration, and script-log tools to
read the resulting state. `controlStatus` describes current event fields.

Read a fresh `snapshot` after any native screen change. Dialog, picker, provider-child, resize, and page-menu semantic IDs can disappear immediately.

`longPress` on `page:navigation:horizontal:edge:before`, `:edge:after`, or the
matching `page:navigation:vertical:` IDs creates one saved, selected, empty page
at that rail's far end. There is no provisional page and no cancel control to
tap afterwards. The page menu carries `page:menu:<pageId>:move-left`,
`:move-right`, `:move-up`, and `:move-down`. Each reports `enabled` false when
no page exists at the neighbouring coordinate, and tapping a disabled one writes
nothing. A successful reorder logs `page_moved` with `page_direction`,
`page_column`, and `page_row`; a refused one logs `page_move_rejected`.

The bottom chrome carries two icon-only buttons. `launcher:controls` opens the
launcher routes. `launcher:page-search` opens the page jump list, which exposes
`launcher:page-search:query` for `setText`, one
`launcher:page-search:page:<pageId>` button per match, and
`launcher:page-search:empty` when nothing matches. A query matches a
case-insensitive substring of either the page title or its `1H1V` address, and
an empty query lists every page. Tapping an entry performs the same selection as
`selectPage`. Opening the sheet logs `page_search_opened` with `page_count`.
Dikciz never logs the query text or the page titles.

A snapshot contains a stable `semanticId`, role, enabled and checked state, clickability, visibility, bounds, text, content description, and an Android resource ID where one exists. Use semantic IDs for automation. Do not derive coordinate taps from text. Provider child IDs include an opaque render token and expire after provider updates, page changes, and removals.

A snapshot also reports the page grid. `grid` carries `columns`, `rows`,
`gapDp`, and `outerPaddingDp`. Every entry in `widgetReferences` carries its
logical `cell` with `column`, `row`, `columnSpan`, and `rowSpan`, plus the
rendered screen `bounds` when that widget is on the selected page, or null when
it is not. Only the cell is persistent; the bounds are render-time values.

`homeGet` returns pages keyed by page ID, one-based page address such as `1H2V`, and the `home` alias. Each page contains widgets by simple ID and full address. That is also the map exposed to HTML widgets.

The current page snapshot includes `scroll.y`, `scroll.extent`, and
`scroll.range`. They report the actual native page scroll position and bounds,
not a simulated value. `scrollBy` accepts integral `deltaY`. `scrollTo` accepts
a current semantic ID.

A native page is one fixed viewport and does not grow, so on a normal home
`scroll.range` equals `scroll.extent` and both commands are no-ops. Scrolling
belongs to an HTML widget's own document or to an Android provider that owns
its scrolling, never to native placement.

## Placement commands

`addWidget` creates one top-level item on the selected page. It takes only
`widgetType`, one of `html`, `appGroup`, or `scriptDashboard`, and always places
the item in the first fitting free cells, reading left to right and then down.
There is no cell argument: placement is not chosen at insertion. Use
`widgetMove` and `widgetResize` afterwards to place it.

App shortcuts keep the existing `appAction` route with `addShortcut`, and
Android provider widgets keep the native picker because Android owns their
binding step.

`widgetMove` and `widgetResize` take a `widgetAddress` and an explicit `cell`
with `column`, `row`, `columnSpan`, and `rowSpan`. A chosen rectangle is
accepted or rejected, never silently relocated.

```bash
python3 scripts/dikciz-control.py websocket addWidget \
  --arguments '{"widgetType":"scriptDashboard"}' --port 19101 --pretty
python3 scripts/dikciz-control.py websocket widgetMove \
  --arguments '{"widgetAddress":"1H1V-device-card","cell":{"column":2,"row":0,"columnSpan":2,"rowSpan":1}}' \
  --port 19101 --pretty
```

All three return the widget document, including its saved `cell`. A rejection
returns one of the finite placement failures, writes nothing to the public
tree, and emits one bounded diagnostic:

| Code | Meaning |
| --- | --- |
| `page_full` | The page has no free cell for any further item. |
| `grid_collision` | The requested cells are already used. |
| `grid_bounds` | The rectangle leaves the configured grid. |

`controlStatus` publishes the same finite list as `placementFailures`. The
WebSocket error envelope carries the value in `code`. The matching MCP tools
`dikciz_add_widget`, `dikciz_widget_move`, and `dikciz_widget_resize` return
`isError` with the same value in `structuredContent.code`, so both planes
report one taxonomy.

## Page grid

`gridSet` replaces the grid used by every page. It takes `columns`, `rows`,
`gapDp`, and `outerPaddingDp`, and returns the saved grid. The MCP form is
`dikciz_grid_set`.

Validation covers every placed widget before anything is written. A widget that
would leave the grid or collide under the new geometry rejects the whole change
with `grid_bounds` or `grid_collision`, names the offending page and widget, and
leaves the saved document untouched.

```bash
python3 scripts/dikciz-control.py websocket gridSet \
  --arguments '{"columns":4,"rows":6,"gapDp":8,"outerPaddingDp":12}' --port 19101 --pretty
```

The same four fields are editable in the launcher under **Settings**, in the
**Page grid** section.

## HTML widget JavaScript

A placed HTML widget receives `window.dikciz`, `selfWidget`, and `dikciz-ready` once its native message channel connects.

```javascript
window.addEventListener("dikciz-ready", async () => {
    const result = await dikciz.command("diagnostics");
    document.querySelector("#status").textContent = Object.keys(result).length;
});
```

`dikciz.command(type, fields)` returns the same result as the WebSocket command. `dikciz.dispatch({ actions })` uses the same typed action executor as Lua. `dikciz.fitContent()` asks the native frame to measure the current DOM.

`diagnostics` includes `htmlWidgetResources`: active renderer count, total
UTF-8 bytes for generated documents on the selected page, and the configured
per-page renderer and document-byte limits. These are launcher-owned resource
inputs, not a claim about opaque Chromium process memory.

`selfWidget` and `dikciz.selfWidget` expose the current ID, page, full address, logical `cell`, state, and `get()`, `patch(values)`, and `patchState(values)` helpers. A widget document reports `cell`, never device pixels, so a widget author never persists a coordinate that rotation would invalidate. `dikciz.pages()` returns pages by ID, page address, and `home`. `await dikciz.home()` and `await dikciz.widget(address)` read current records after a configuration change.

Use `await dikciz.patchWidget(address, values)` for a full named-widget patch,
or `await dikciz.patchWidgetState(address, values)` for its persisted state.
Addresses are human-readable, for example `2H1V-weather-card`. A target on the
current page receives `dikciz-state` without a document reload. A target on a
different page is not treated as live DOM. Its saved state is available through
`selfWidget.state` when that page is selected and its widget renders.

State patches keep the current document alive. Listen for `dikciz-state`:

```javascript
window.addEventListener("dikciz-state", event => {
    document.querySelector("#status").textContent = event.detail.status;
});
```

When the launcher adds a block through that widget's native Add block action,
the same live document receives `dikciz-block-appended`. Its
`detail.instanceId` is the stable instance ID used in the source markers. The
event occurs after the marked source has been saved and inserted, and the
widget then scrolls to that new block without a document reload.

```javascript
window.addEventListener("dikciz-block-appended", event => {
    console.log(`Added block ${event.detail.instanceId}`);
});
```

`dikciz.onEvent(listener)` receives matching native and custom events while the widget is rendered. It returns an unsubscribe function. The same document is sent as a `dikciz-event` window event. Select events in the widget's `eventSubscriptions` JSON field.

Publish a custom event with:

```javascript
await dikciz.emitEvent("weather.refresh.completed", { updatedAt: Date.now() });
```

Custom names are 1 through 128 characters, start with a letter, and use letters, digits, `.`, `_`, `:`, or `-`. Lua uses its matching `emitEvent` action.

The direct action vocabulary is `accessibilityAction`, `accessibilityGesture`,
`accessibilityGlobalAction`, `selectPage`, `patchWidget`,
`patchState`, `patchDom`, `launchApp`, `appAction`, `postNotification`, `sendSms`,
`explicitIntent`, `emitEvent`, `mediaControl`, `mediaVolume`,
`notificationControl`, and
`lockDevice`. `appAction` takes an `action` and a `component`. A script action has
no selected page, so it rejects `addShortcut`.
`sendSms` takes a dialable `recipient` and a `body`, and needs the `SEND_SMS`
permission that `automationStatus` reports as `smsSend`. Dikciz is not the
default SMS app, so a sent message does not reach the Android SMS provider or
the owner's messaging history.
`accessibilityAction` requires the current `snapshotId`, a `nodeId`, and one
supported `action`; `setText` also requires `text`. Current policy and Android
access decide whether an action can complete.

`patchDom` takes `widgetAddress`, a CSS `selector`, and one or more of `text`, `html`, `value`, `className`, or `attributes`. It changes only a currently rendered HTML document and does not persist source or state. A target that is not rendered returns `target_not_rendered`.

For a complete event, Lua, HTML bridge, state, and action walkthrough, read the [scripting tutorial](scripting-tutorial.md).

`lockDevice` returns `device_admin_inactive` when Android Device Administration
is not active. It does not open Android Settings. Open **Settings**, then
**Automation**, then **Set up full device access** to request that access.

The native bridge holds at most 32 waiting requests and accepts at most 65,536
characters per request. The supplied JavaScript API separately allows at most
32 outstanding promises, so ordinary `dikciz.command()` calls are backpressured
before native saturation. Raw channel users can still receive `busy` when the
native queue is full. Every rejected HTML command is logged as
`web_command_rejected` with the HTML widget ID, HTML kind, and error reason,
and is available through both log transports. A state patch does not reload the
WebView. Replacing HTML, CSS, JavaScript, or the whole configuration can destroy
the calling document.

## Logs and recovery

`scriptLogs` and `dikciz_script_logs` return at most 100 sanitized records from recognized local log files. They return timestamps, event names, script or HTML-widget IDs, kind, policy ID, action type, outcome, reason, and an optional diagnostic where present. Lua compile and handler failures retain the bounded, sanitized source-location message in `diagnostic` while their stable `reason` remains `invalid_lua`. Launcher-component records also appear, using their component name in the compatible `scriptId` field and a null kind. HTML console warnings and errors use the HTML widget ID and kind. Credential-shaped console values are redacted before either transport reads them. A renderer loss logs its widget ID. Dikciz releases and recreates only the reported HTML widget in its existing native frame. The APIs do not return raw log lines, Lua source, script state, widget text, configuration, or event payloads.

Safe mode permits diagnostics, snapshots, logs, screenshots, UI dumps, reset, and normal restart. It rejects normal home edits, configuration replacement, shell, intent, and device mutation commands until Dikciz restarts normally.

## Lab proof

```bash
make dikciz-automation-smoke
make dikciz-mcp-smoke
DIKCIZ_AUTOMATION_MUTATE=true make dikciz-automation-smoke
DIKCIZ_MCP_MUTATE=true make dikciz-mcp-smoke
make dikciz-test-accessibility
```

Both smoke targets probe the shared Dikciz forward that `make dikciz-run`
creates, so they default to host ports `19101` and `19102`. The device ports
stay `19001` and `19002`, because the launcher always listens there. Point them
at another forward by overriding the host ports:

```bash
make dikciz-automation-smoke ANDROID_LAB_FORWARD_HOST_PORT=19001 ANDROID_LAB_MCP_HOST_PORT=19002
```

The mutation checks restore the original configuration. Use a focused `make dikciz-test-*` target for a particular UI or Android behavior.
