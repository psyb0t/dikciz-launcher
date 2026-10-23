# Dikciz configuration

Dikciz keeps its editable home under `/sdcard/Dikciz/`. The directory is the source of truth for the launcher. Valid changes reload automatically, within a couple of seconds of the write, whether the edit came from the launcher, a script, adb, or a file manager. An invalid file leaves the last accepted home visible and records the failure in the local log.

## Public file tree

```text
/sdcard/Dikciz/
├── config.json
├── pages/<page-id>/page.json
├── pages/<page-id>/widgets/<widget-id>.json
├── pages/<page-id>/widgets/<widget-id>/state.json
├── pages/<page-id>/widgets/<widget-id>/items/<instance-id>/state.json
├── pages/<page-id>/widgets/<widget-id>/items/<instance-id>/<custom-file>
├── scripts/<script-id>/script.json
├── scripts/<script-id>/main.lua
├── scripts/<script-id>/state.json
├── scripts/<script-id>/run-status.json
├── scripts/imports/
├── scripts/exports/
├── widget-library/<package-id>/widget.json
├── widget-library/<package-id>/index.html
├── widget-library/<package-id>/style.css
├── widget-library/<package-id>/script.js
├── automation/policies/<policy-id>.json
├── automation/scripts/<script-id>.json
├── control/remote-auth.json
├── control/ports.json
├── themes/<theme-id>.json
├── fonts/
├── wallpapers/
└── logs/YYYY-MM-DD.log
```

On first run, Android must grant Dikciz access to this directory. The storage screen links to Android's own access panel. Until Android grants it, the control planes still expose the storage screen for inspection but cannot load or change configuration.

Each file has one job. `config.json` owns global settings and the home manifest. Pages own their metadata and widget references. Widgets, scripts, automation rules, themes, and library packages remain separate so a normal edit does not turn the root into one large document.

## Edit and inspect configuration

Use Android file tools, an ADB tunnel, or the control planes. In the lab, move only an artifact through the provided targets:

```bash
DEVICE_FILE=/sdcard/Dikciz/config.json ARTIFACT_FILE=config.json make adb-pull
# Edit .android-lab/shared/artifacts/config.json.
DEVICE_FILE=/sdcard/Dikciz/config.json ARTIFACT_FILE=config.json make adb-push
```

The control planes expose a complete assembled document. They validate a full replacement atomically, write the matching public tree, and reload the home:

| Interface | Read | Replace | Restore bundled home |
| --- | --- | --- | --- |
| WebSocket | `configGet` | `configReplace` or `configSeed` | `reset` |
| MCP | `dikciz_config_get` | `dikciz_config_replace` or `dikciz_config_seed` | `dikciz_config_reset` |

`configReplace` is a complete replacement, never a partial patch. Start by reading `configGet`, edit the returned document, then replace it. The command result contains the normalized saved configuration and a fresh semantic UI snapshot.

## Root document

`config.json` has a version and Dikciz-owned namespaces:

```json
{
  "version": 4,
  "limits": {
    "maxConfigBytes": 1048576,
    "maxComponentCharacters": 512,
    "maxHtmlWidgetDocumentBytes": 196608,
    "maxHtmlWidgetsPerPage": 8,
    "maxIdentifierCharacters": 80,
    "maxPages": 24,
    "maxTextCharacters": 65536,
    "maxTitleCharacters": 120,
    "maxWidgetsPerPage": 128
  },
  "logging": { "level": "info", "retentionDays": 7, "maxTotalBytes": 16777216 },
  "launcher": {
    "home": {
      "homePageId": "home",
      "selectedPageId": "home",
      "selectedThemeId": "dikciz-dark",
      "styleDefaults": { "widget": {} },
      "pages": ["home"]
    }
  }
}
```

The assembled view returned by `configGet` also contains scripts and automation objects. The persisted tree keeps their named source and policy files separate. Dikciz preserves root namespaces and sibling objects under `launcher` that it does not own when it performs a native edit. Version 4 is required; older documents are rejected rather than migrated.

`limits` bounds document size, identifiers, titles, page count, widgets per page, component strings, and HTML renderer resources. The bundled limits are a one MiB configuration, 24 pages, 128 widgets per page, 65,536 text characters, 120-character titles, and 80-character identifiers. `maxHtmlWidgetsPerPage` defaults to eight enabled HTML widgets. Native shortcuts, app groups, provider widgets, and the native script dashboard do not consume that budget. `maxHtmlWidgetDocumentBytes` defaults to 196,608 UTF-8 bytes for the fully generated HTML document, including the widget context Dikciz injects at load time. The parser also applies non-configurable upper limits before reading a file.

`logging` controls level, retained days, disk budget, failed-script notifications, and the notification quiet period. See [Logs](#logs).

## Pages and widget files

A page has a stable ID, title, two-axis position, lock state, and widget IDs:

```json
{
  "id": "home",
  "title": "Home",
  "position": { "column": 0, "row": 0 },
  "locked": false,
  "widgets": ["command-deck"]
}
```

Page positions are zero-based in files. Human-facing addresses are one-based: `1H1V` is column one, row one. A widget's complete address combines that page address and widget ID, for example `1H1V-command-deck`. The address changes when a widget moves to another page. Its ID does not.

Long press the empty part of a page rail, past its dots, to create a page. The new page lands at the far end of that rail, never beside the page you are on. Holding the horizontal rail before the dots inserts a new first column and shifts every existing column right; holding it after the dots appends a new last column. The vertical rail does the same at the top and the bottom of the current column. The page is saved and selected immediately, so there is nothing to confirm and nothing to lose by navigating away.

Reorder pages from the page menu under **Move this page**: **Move left**, **Move right**, **Move up**, and **Move down**. Each one exchanges this page's coordinate with the single page already sitting at the neighbouring coordinate. A direction with no page there is shown disabled and changes nothing. A move rewrites only `position`, so page IDs, titles, locks, widgets, `homePageId`, and `selectedPageId` all survive it. A locked page can neither create a page nor be moved.

Every widget has `id`, `type`, `enabled`, `cell`, `locked`, and optional `style`. Visible cards also have `title`. IDs are global and case-insensitively unique across all pages. Native creation makes readable lowercase slugs with a numeric suffix. Widgets never stack. `zIndex` is not a supported widget key, and Dikciz rejects a configuration that contains it.

Supported widget types are:

| Type | Purpose |
| --- | --- |
| `html` | A resizable WebView with local HTML, CSS, JavaScript, and state. |
| `app` | One explicit Android activity shortcut. |
| `appGroup` | A native group of up to 32 Android activity components, made by dropping one app tile onto another. |
| `provider` | A native third-party Android AppWidget provider card. |
| `scriptDashboard` | Native view for script state, logs, enablement, and editing. |

`text`, `toggle`, and `lua` are not widget types. Create visible controls as HTML widgets. Lua stays in the script tree and appears in a script dashboard.

### The native page grid

A native page is one fixed viewport. It never gains canvas height, and adding a widget never scrolls the page below an existing widget.

`launcher.home.nativeGrid` describes the grid every page is laid out on:

```json
{ "nativeGrid": { "columns": 8, "rows": 6, "gapDp": 8, "outerPaddingDp": 12 } }
```

`columns` and `rows` accept 1 to 12. `gapDp` and `outerPaddingDp` accept 0 to
64. The bundled default is 8 columns by 6 rows. The record is optional; a
document without it uses that default.

The right and bottom pager rails provide the trailing outer spacing. The page renderer applies `outerPaddingDp` at the left and top without adding it again beside those rails. Widget style margins remain additional spacing. Explicit saved grid dimensions remain unchanged on update.

Every top-level widget owns whole grid cells through one `cell` record:

```json
{ "cell": { "column": 0, "row": 0, "columnSpan": 2, "rowSpan": 1 } }
```

`column` and `row` are zero-based cell indexes. Both spans are cell counts and are at least 1. The rectangle must fit inside the configured grid, and two widgets on one page may never claim the same cell. Rendered pixels are derived from the current viewport at draw time, so the same logical rectangle survives page selection, process recreation, and rotation.

`cell` is the only placement a widget carries. Any other placement key is an unknown key and the strict parser rejects the document.

### Placement failures

Dropping a widget on a cell another widget already holds is not always a failure. The tile under the pointer decides what the drop means:

- two app tiles merge into one `appGroup`, and an app tile dropped on a group joins it;
- two widgets that cover the same number of cells trade places;
- anything else is refused.

Both outcomes are shown while the finger is still down. A merge target swells, and a trade moves both tiles at once, so leaving the cell puts the other widget back before the drop.

Placement reports one of three finite failures. A rejected operation writes nothing to the public tree and emits one bounded diagnostic.

| Code | Meaning |
| --- | --- |
| `page_full` | The page has no free cell for any further item. |
| `grid_collision` | The requested cells are already used. |
| `grid_bounds` | The rectangle leaves the configured grid. |

### Editing the page grid

The grid is editable in **Settings**, under **Page grid**, and through the `gridSet` command and the `dikciz_grid_set` tool. One grid applies to every page.

Changing dimensions reflows every page, including locked and unselected pages. Shrinking scales widget edges to the smaller grid, with at least one cell per axis. Full-width and full-height widgets follow the corresponding grid dimension when it grows too. Items are processed in saved order; a collision uses the first free rectangle of the adjusted size. An item with no fitting space is removed from the page. Removing an app shortcut leaves its app installed; removing a widget also removes its scoped state. The new grid and adjusted pages are saved together. Spacing-only changes preserve placements.

While a widget is being moved or resized, the page draws every cell of the grid behind the content. Placement snaps to whole cells, so the guides show where the widget can land. They appear when a move is armed or a resize starts, and they go away when the gesture ends.

Direct configuration replacement remains strict: each widget must carry a valid `cell` record. Use Settings or `gridSet` to reflow an existing layout. Omitting `nativeGrid` uses the bundled grid; it does not make legacy pixel placement valid.

## Native app shortcuts and app drawer

The Dikciz mark beside the horizontal page rail opens the launcher controls, and from there the native app drawer. An upward swipe that starts on that rail opens it too. Search filters launchable installed apps by label or package name. Tap a result to launch it. Use its overflow button, or long press the row, for add shortcut, Android app info, uninstall, and force stop.

Android owns the confirmation for app info and uninstall. Force stop requests root and reports `root_unavailable` when `su` is unavailable or denied. It never falls back to the ordinary Dikciz app shell.

Adding an app places an `app` tile in the first free grid cell, row-major from the top left. A page with no free cell rejects the request with `page_full` and adds nothing. The placed tile is left selected so its move and resize controls work straight away. A tap anywhere on the tile launches the activity, and a drag from anywhere on it moves the tile to another free rectangle. A widget that owns its own content, an HTML document, an Android provider view, or the script dashboard, is dragged from its top `24Dp` instead, because its body needs the touches for itself.

An app tile draws its icon as the largest square the cell allows, measured against the narrower side, with the icon and its label centred in the cell as a pair. Resizing the tile resizes the icon with it.

Dropping one app tile onto another makes an `appGroup` holding both, in drop order, at the target's cell. The group opens as a grid of its member icons. A tap launches a member and a long press removes it. Removing down to a single member replaces the group with a plain `app` widget for the survivor, keeping the cell and the widget ID, so a script addressing that widget keeps working. A group is never saved with fewer than two members.

An `app` widget stores one flattened Android `component` and one `displayStyle`:

| `displayStyle` | Appearance |
| --- | --- |
| `iconLabel` | App icon and label. This is the default. |
| `button` | Text button. |
| `buttonIconLabel` | Button with icon and label. |

The native drawer and the `appCatalogue` and `appAction` commands share the same installed-app catalogue and action runtime. Command calls use a flattened component. `addShortcut` needs a selected native page and is rejected from Lua's returned typed actions, which have no selected-page context. Lua owner command calls use the serving launcher's selected page.

## HTML widgets

The assembled HTML widget record returned by `configGet` is a normal, resizable home widget:

```json
{
  "id": "device-card",
  "type": "html",
  "title": "Device card",
  "html": "<main><output id=\"status\">Loading</output></main>",
  "css": "main { padding: 16px; color: white; }",
  "javascript": "window.addEventListener('dikciz-ready', () => {});",
  "state": { "status": "ready" },
  "heightMode": "content",
  "enabled": true,
  "cell": { "column": 0, "row": 0, "columnSpan": 2, "rowSpan": 2 }
}
```

`heightMode` is `fixed` when omitted. `content` fits the rendered document inside the widget's assigned row span without expanding its cells or the page. Long press the move handle, content or background for the same whole-widget edit, appearance, resize, fit-content, lock and delete actions. Edit exposes HTML, CSS, JavaScript and JSON state together. HTML elements and `data-*` attributes are document content, not native preset objects. Page and widget locks protect editing. Dikciz consumes long presses so the WebView does not start browser text selection or browser controls.

HTML widgets hide native and CSS scrollbars, including those in nested scroll containers. Touch, keyboard and programmatic scrolling still work. In **Appearance**, set **Border / Widths / All sides** to `0` to remove the native outline (`style.border.widths.all: 0`). Set individual side widths to `0` too if the widget or theme supplies them; individual widths take precedence over `all`. Borders drawn by the HTML itself are controlled by its CSS.

The native WebView viewport is clipped to the widget frame even when document CSS overflows. During resize, Dikciz draws the native outline and handles over the WebView, so the frame stays visible and remains the resize boundary.

The public widget descriptor `<widget-id>.json` stores source and layout. Wrapper `state` is stored separately in `<widget-id>/state.json`. Card `itemStates` are split into `<widget-id>/items/<instance-id>/state.json`, under that widget's page directory. `configGet` and `widgetGet` assemble those values into the same record. Legacy state embedded in a widget descriptor migrates after the complete configuration passes validation.

JSON state patches preserve null values in objects and arrays; null does not remove a key.

State changes do not reload the WebView. The document receives `dikciz-state`, and `selfWidget.state` becomes the full replacement state. Update only the DOM nodes that changed.

The native editor is a source editor, not an HTML preview. Its labeled `HTML`, `CSS`, and `JavaScript` fields use code text and a boxed editor frame. They show literal source, including tags, comments, and entities. For example, a divider is shown as `<hr class="dikciz-block dikciz-block-divider" />`, not as a rendered rule. Opening **Edit HTML widget** always reads the current saved widget record, including source changed after the long-press menu opened.

HTML packages are reusable starters under `widget-library/<package-id>/`. A package has strict metadata in `widget.json`, `index.html`, and optional `style.css` and `script.js`. `widget.json` is at version 2 and declares a `span` with `columnSpan` and `rowSpan` instead of a pixel `size`. The picker previews a package, then copies its source into a new sibling widget placed on the grid. Choosing a package never edits an existing widget's source. Later package changes do not silently modify placed widgets. Save an edited widget to make or replace a package. Dikciz stages and validates a complete replacement before it publishes it. If a save is interrupted, the next library read either completes a valid retained stage or restores the last valid package. The picker never shows a partial package.

### Complete WebView templates

The library contains whole documents: `time-weather`, `agenda`, `voice-notes`, `device-status`, `home-dashboard`, `command-deck`, `quick-apps`, `quick-note`, `system-dashboard`, `sensors-dashboard`, `markets-news`, `app-launcher`, `brightness`, `lock-screen`, `notifications` and `system-status`. Each placed widget owns its full HTML, CSS, JavaScript and state. Library source is copied at insertion; there are no native card preset objects or fragment picker. Ordinary DOM sections remain programmable through the document's own JavaScript.

The system template reads `deviceState.system` for RAM, app-data filesystem capacity, CPU samples and a bounded sensor inventory. CPU availability and scope are explicit. Sensor inventory lists hardware, not live readings. The market template uses explicit Coinbase and GDELT requests and retains dated results in its widget state. See [starter templates](starter-home.md#complete-webview-templates) for sources and refresh behavior.

Dikciz installs a bundled package only when that package ID has no public directory yet. It never overwrites an existing package during an app update, so a user-edited starter remains the user's version. Save an edited widget to replace that package deliberately, or delete that one package directory before the next fresh installation if the bundled version is wanted again.

The JavaScript bridge and event model are documented in [Automation control](automation-control.md#html-widget-javascript).

## Lua scripts and automation

Lua scripts are background or event-driven logic, not home widgets. Each script has four files:

```text
scripts/<script-id>/script.json      metadata and enabled state
scripts/<script-id>/main.lua         source
scripts/<script-id>/state.json       explicit script input and saved state
scripts/<script-id>/run-status.json  latest result written by Dikciz
```

Each enabled script requires a referenced automation policy and subscription:

```json
{
  "id": "status",
  "title": "Status automation",
  "enabled": true,
  "capabilities": ["battery", "locationPrecise"],
  "actions": ["patchState", "patchWidget", "postNotification"]
}
```

`capabilities` names the events the script may receive. `actions` names the typed actions it may return, and Dikciz enforces that list when the action runs. Enabled scripts also have owner command access through `context.request` and `context.requestJson`; this command access is not restricted by the returned-action list. An action a script returns that its policy does not list is refused with the outcome `capability_denied`, the launcher does nothing, and the refusal is logged with that `reason`. A script whose policy is missing gets no actions at all. Actions a Dikciz HTML widget dispatches directly are not policy gated, because the owner invokes those on the device.

```json
{
  "scriptId": "status",
  "policyId": "status",
  "enabled": true,
  "subscriptions": [
    {
      "event": "battery",
      "minimumIntervalMilliseconds": 60000,
      "coalescingKey": "battery-status"
    }
  ]
}
```

The current capability names are:

```text
time, battery, charging, powerSaveState, deviceIdleState, nightModeState,
deviceConfiguration, appPackagesMetadata, ringerModeState,
interruptionFilterState, connectivity, thermalState, bluetoothState, screen,
userPresence, alarm, sensors, healthSteps, locationApproximate,
locationPrecise, calendarEventsMetadata, calendarEventsContent,
contactsMetadata, phoneState, smsMetadata, smsContent, notificationsMetadata,
notificationsContent, mediaSessionsMetadata, mediaSessionsContent,
clipboardMetadata, clipboardContent, widgetState, deviceAdministration,
manualTrigger, accessibility
```

The current policy action capability names are:

```text
accessibilityAction, accessibilityGesture, accessibilityGlobalAction,
selectPage, patchWidget, patchState, patchDom, launchApp, appAction,
postNotification, sendSms, explicitIntent, emitEvent, mediaControl,
mediaVolume, notificationControl, lockDevice
```

The list above contains policy capability names. The volume action record uses `type: "setMediaVolume"` and integer `levelPercent` from 0 through 100, while its policy allows `mediaVolume`. See the [complete typed action reference](typed-actions.md) for fields and policy mappings.

Read the [scripting tutorial](scripting-tutorial.md) for end-to-end event subscriptions, custom events, HTML bridge calls, cross-widget state, and action results.

`sendSms` takes a `recipient` and a `body`. The recipient is a dialable number of up to 20 characters: digits with an optional leading `+` and the usual dialling punctuation. The body uses the configured text limit and is divided into parts when it is longer than one message segment.

Dikciz does not request the default SMS role. It does not directly insert sent messages into Android's SMS provider, but Android can persist messages sent by non-default SMS apps, so they can appear in the owner's messaging history. See [Android's SMS provider behavior](https://android-developers.googleblog.com/2013/10/getting-your-sms-apps-ready-for-kitkat.html). Android can rate-limit sends or ask the owner for confirmation. The limits depend on the device. Dikciz reports what Android returned and does not work around that prompt.

The action needs the `SEND_SMS` Android permission, which `automationStatus` reports as `smsSend` under `androidAccess`. Without it the action returns `android_permission_denied` and sends nothing. A policy that does not declare `sendSms` is refused before Android is reached.

Event subscriptions use the published event names: `time`, `battery`, `charging`, `powerSaveMode`, `deviceIdleMode`, `nightMode`, `deviceConfiguration`, `packageChanged`, `ringerMode`, `interruptionFilter`, `connectivity`, `thermalStatus`, `bluetoothState`, `screen`, `userPresent`, `alarm`, `sensor`, `healthDailySteps`, `location`, `calendarEvent`, `contactsChanged`, `phoneState`, `smsReceived`, `notificationPosted`, `notificationRemoved`, `mediaSession`, `clipboardChanged`, `widgetChanged`, `deviceAdminState`, `accessibilityWindow`, `accessibilityServiceState`, and `manual`.

Use a new valid event name for a custom event, for example:

```json
{
  "event": "weather.refresh.completed",
  "minimumIntervalMilliseconds": 0
}
```

Both Lua and HTML can emit it. Subscribers receive the name in `event.type` and the JSON object in `event.payload`.

`minimumIntervalMilliseconds` is a per-script-subscription monotonic delivery floor. It suppresses a matching event when that subscription was delivered too recently. Alarm subscriptions also keep their own due time: Dikciz wakes at the shortest active alarm interval, but a longer subscription runs only when its own deadline is due. A configuration replacement begins a new schedule window. After a process restart, the retained Android alarm causes one recovery delivery and starts the next deadline from that delivery. Late alarms run once and move their next due time ahead from that delivery. Dikciz does not replay missed intervals in a burst. Turning off either the script or its automation entry stops its Android sources and cancels its alarm schedule.

A `location` subscription needs `locationPrecision`, either `approximate` or `precise`, and the value decides both the capability it needs and what the event carries. A precise subscription needs `locationPrecise` and the Android fine-location permission, and its payload carries `provider`, `latitude`, `longitude`, `accuracy`, `altitude`, `bearing`, and `speed`. An approximate subscription needs `locationApproximate` and either Android location permission, and its payload carries only `provider` and a `latitude` and `longitude` rounded to two decimal places, about a kilometre. Dikciz reads the device location once and then builds one event per subscribed precision, so an approximate subscription receives the approximate event even while the launcher holds the fine permission for some other subscription. A precise subscription without the fine permission registers nothing and receives nothing.

Lua receives a bounded event table plus read-only `context.state`, `context.widgets`, and `context.androidAccess`. The last object is the same current Android access map that `automationStatus.androidAccess` returns. It contains Booleans only, not Android object handles. During `accessibilityWindow`, Lua also receives the bounded active Android tree in `context.accessibility`. Return a status and actions. `accessibilityServiceState` has only its fixed event payload: `state` is `connected`, `interrupted`, or `disconnected`, and `enabled` is the current Accessibility owner-setting Boolean. It does not include `context.accessibility` because it has no active-window snapshot. `accessibilityAction` requires its current `snapshotId`, a generated `nodeId`, and a supported action. `accessibilityGesture` uses its current `snapshotId` with a bounded `tap` or `swipe` document. `accessibilityGlobalAction` uses one supported Android global action name. `patchWidget` targets a complete widget address such as `1H1V-command-deck`. Enabled scripts can use `context.request` or `context.requestJson` for ordinary launcher commands, including shell, files, intents and semantic UI input, with the same validation and Android access checks as the local control planes. These functions accept JSON strings or object tables and return canonical result/error responses. Lua receives no Android object handles. See the [Lua command reference](scripting-tutorial.md#return-device-and-launcher-actions-from-lua).

### Lua runtime surface

The [scripting tutorial](scripting-tutorial.md) documents the supported runtime surface. Core globals are `assert`, `error`, `ipairs`, `next`, `pairs`, `pcall`, `select`, `tonumber`, `tostring`, `type`, and `xpcall`. Available libraries are `math`, `string`, `table`, and `bit32`.

The runtime removes dynamic loading and host escape routes: `debug`, `io`, `os`, `package`, `require`, `load`, `dofile`, `loadfile`, and `print` are not available. It also removes `string.format`, `string.gsub`, `string.rep`, `table.concat`, `table.move`, and `table.unpack` to keep script work bounded. Scripts receive no Android object handles. Use the typed action list above or the owner command functions for launcher and Android behavior.

Fresh Dikciz includes three disabled utility scripts. Their enabled policies and subscriptions become active only after the owner enables the corresponding Lua source record.

| Script | What it does after enabling |
| --- | --- |
| `low-battery` | Notifies once at 20 percent or lower while discharging. Charging or reaching 30 percent resets the reminder. |
| `charge-ready` | Notifies once at 80 percent or higher while charging. Unplugging or dropping below 75 percent resets it. |
| `screen-lock` | Locks only when explicitly run, after checking Device Administration. It never runs when access changes. |

No bundled script patches a particular widget or subscribes to Accessibility, notification contents, location or a periodic alarm. The [starter home guide](starter-home.md) covers the two demo pages, automatic weather location, read-only agenda, voice recordings and device status. Live sensors remain an optional template.

Use the native Script dashboard or Settings to enable, disable, edit, import, export, delete, inspect status, and read logs. The dashboard, Settings, WebSocket `scriptLogs`, and MCP `dikciz_script_logs` read the same safe log projection. A failed Lua compile or handler run keeps `invalid_lua` as its stable run status. Its matching log record includes a bounded, sanitized `diagnostic` with the public `scripts/<script-id>/main.lua` location and line when LuaJ provides one. Credential-shaped values are redacted before the native viewer or either remote control plane receives the record.

## Android access

The Dikciz manifest declares a broad Android access surface. It includes location, contacts, calendar, phone and call state, SMS, camera, microphone, Bluetooth, nearby Wi-Fi, media, notifications, activity and body sensors, Health Connect, files, exact alarms, overlays, writable system settings, usage access, package installation, battery-optimization exemption, Device Administration, and notification-listener support.

The declaration allows Dikciz to request access. Android, the installer, the selected device role, the OEM, and root manager still decide whether it is available. Enable the automation policy first, then use **Settings**, then **Automation**, then **Set up full device access**. Setup opens a separate **Full device access** task in Recents. Its checklist refreshes when you return from an Android access panel or another app, including after activity recreation. Closing it removes the setup task. Each missing access has its own button, and the next setup button follows current Android grants. `automationStatus` and `dikciz_automation_status` return the same machine-readable status.

The `deviceAccess` inventory includes the `sms` runtime-permission entry for reading, receiving and sending SMS. It does not include a default SMS role or request replacing the default messaging app.

Cross-app Android UI automation is an Accessibility service requirement. Its record is `deviceAccess.requirements[id="accessibility"]`; its current Boolean is `automationStatus.androidAccess.accessibility`. The state is `needsSetup` until the phone owner enables Dikciz in Android's Accessibility settings, then becomes `granted`. This is not a runtime permission and is not configurable in `config.json`, a Lua policy, an HTML widget, WebSocket, or MCP. Those surfaces can inspect the state and open the Android panel only.

The local `shell` command and `dikciz_shell` tool request `su` by default. Dikciz first runs `su -c id -u`; only an exact UID of `0` permits the requested command and `executionScope: "root"`. A missing, denied, malformed, or timed out root probe returns `root_unavailable` through WebSocket and an MCP tool error. It never falls back to Dikciz's app user. Pass `root: false` for the ordinary `/system/bin/sh` route, whose result reports `executionScope: "app_sandbox"`, exit code, output, and truncation state.

## Remote bearer password

`control/remote-auth.json` is separate from `config.json`. It stores version, bcrypt verifier, cost, and enabled state. It never stores the plaintext password. Set, replace, clear, enable, or disable the password from **Settings**, then **Automation**. The phone does not ask for the old password when changing it.

When enabled, WebSocket and MCP requests on every interface need `Authorization: Bearer <password>`. A remote client can check, enable, or disable the setting with the documented remote-auth commands. The remote-auth commands cannot retrieve, replace or clear the password. File APIs deny the verifier and its atomic aliases, but an independently authorized app-UID/root shell is not restricted by those file-command guards; see [Android access](android-access.md#credentials-and-privilege-limits). Enabling or disabling remotely requires the configured password even while authentication is disabled. An invalid auth record refuses connections until repaired or cleared on the phone.

## Control ports

`control/ports.json` sets the listen address and ports of both listeners. Plain HTTP JSON commands share `mcpPort` at `/v1/automation`; MCP uses `/mcp` on that port. Both use the same bearer and Origin checks. The [files, APKs and shell guide](files-and-apks.md) documents HTTP requests.

Example:

```json
{ "version": 1, "listenAddress": "0.0.0.0", "websocketPort": 19001, "mcpPort": 19002 }
```

Change them in **Settings**, then **Automation**, then **Configure control ports**. Saving a changed address or port restarts both listeners and closes their open connections. **Reset to defaults** deletes the file. Direct file edits also apply automatically, including atomic file replacements and writes through ADB. The configuration watcher restarts both listeners when their address or ports change, without restarting the launcher or rebuilding the home. Rewriting the same endpoints keeps existing connections open. Deleting the file restores the defaults.

`listenAddress` is an optional numeric IPv4 or IPv6 address. It defaults to `127.0.0.1` for existing records. Set it to `0.0.0.0` to accept connections on all IPv4 interfaces, including Wi-Fi and a reachable VPN interface, or choose a specific local interface address. Hostnames, empty values and malformed addresses are rejected. Network binding preserves the existing bearer authentication and browser Origin checks.

Ports must be whole numbers from 1024 to 65535 and must differ. Without the file, Dikciz uses `127.0.0.1` on ports 19001 and 19002. If the file is invalid, Dikciz logs `control_ports_invalid`, uses the defaults and says so in Settings, and reports the fallback in its status. A port conflict can still prevent a listener from starting. There is no dedicated command for changing listener settings. `controlStatus` reports each listener's `listenAddress` and `port` under `transports`.

For direct access, connect to the phone's reachable private hostname or IP and the configured device ports. Enable bearer authentication and allow computer-to-phone traffic in the network policy. `0.0.0.0` is a binding value, not a client destination; it also accepts reachable Wi-Fi clients. Follow [Connect without USB](automation-control.md#connect-without-usb) for setup and troubleshooting.

If using ADB forwarding, update the mappings after a port change, for example `make forward ANDROID_LAB_FORWARD_DEVICE_PORT=<websocket-port> ANDROID_LAB_MCP_DEVICE_PORT=<mcp-port>`.

## Themes and fonts

`launcher.home.selectedThemeId` selects a reusable JSON file under `themes/`. Theme data is never copied into every widget. The selected theme supplies the base widget style, `styleDefaults.widget` overrides it, and a widget's `style` object overrides individual background, border, spacing, text, and font fields. Applying a theme from the picker clears the launcher background override, style defaults, and widget style overrides; later appearance edits mark the theme as modified. A missing selected theme is reported as missing.

Fonts can be bundled, Android system choices, or a plain `.ttf`, `.otf`, or `.ttc` filename in `fonts/`. Wallpapers are PNG, JPEG, or WebP files in `wallpapers/`.

## Logs

Dikciz writes JSON Lines diagnostics under `/sdcard/Dikciz/logs/YYYY-MM-DD.log`, with UTC dates in filenames and complete UTC timestamps in each record. Open **Settings**, **Configure logging** to choose the minimum level (`debug`, `info`, `warn` or `error`) and retention from 1 to 90 days. Saving applies immediately; Cancel does not change configuration. DEBUG includes all levels; INFO includes INFO, WARN and ERROR. Native edits preserve the other logging values. Existing file-based logging edits also reload automatically. Defaults are INFO, seven days and a 16 MiB cleanup budget. Cleanup removes older daily files; the active UTC day's file stays even if it exceeds the budget. The logging object can enable failed-script Android notifications and set the minimum interval for each script's failure alert. HTML widget console warnings and errors are included as HTML-kind records with the stable widget ID. Assignment-shaped credentials and bearer values in console text are redacted before persistence. If an Android WebView renderer is lost, Dikciz destroys and recreates only that widget in its existing native frame. The Settings viewer and control planes return a bounded safe record list, including launcher-component records that use their component name in the record's script-ID field for compatibility. App-action records retain the action and target as `actionType` and nullable `componentPackage` in the viewer and both control planes. The raw files use `action` and `component_package`. Successful app launches remain logged without a success popup. They do not return raw script source, state, widget text, or configuration.

## Recovery

**Reset Dikciz home** restores the bundled home, grid, validation limits, scripts, policies, and subscriptions. It keeps other root namespaces and unrelated Android launcher data. It removes obsolete Dikciz AppWidget allocations.

**Safe mode** starts the bundled first page without writing the current public configuration. It keeps read-only file browsing and downloads, install-status queries, diagnostics, snapshots, logs, screenshot, UI dump, reset, and normal-restart recovery paths available while it blocks normal home edits and device mutation commands.

## Source map

The [Dikciz source map](../app/src/dikciz/README.md) identifies the configuration store, style codec, theme loader, widget libraries, and automation runtime that implement this contract.

### HTML instance state and files

The complete ownership path is `/sdcard/Dikciz/pages/<page-id>/widgets/<webview-id>/items/<instance-id>/`. `selfWidget.storagePath` identifies the wrapper directory. `selfWidget.itemFor(element)` resolves the nearest saved `data-dikciz-item-id`, while `selfWidget.item(id)` selects that instance explicitly. An instance exposes `state`, `getState()`, `patchState(values)`, `storagePath`, `readFile(name, options)` and `writeFile(name, dataBase64, options)`. State patches replace that instance's object while preserving its siblings. Native edits and automation state patches read the latest saved configuration inside a shared file transaction, preserving unrelated state during concurrent changes. File methods accept basenames; use `patchState` for the reserved `state.json`.

Card state changes deliver `dikciz-item-state`, whose detail is the wrapper's current item-state map. `selfWidget.itemStates` and each instance's `state` getter update without reloading the WebView. Valid external edits to a wrapper or card state file use the same watcher. Malformed state retains the last valid running home. Instance IDs use 1 to 80 ASCII letters, digits, underscores, dots or hyphens, starting with a letter or digit. A wrapper accepts at most 1024 instance states; normal configuration size and state-value limits also apply.

Optional item-state namespaces remain available to document scripts. Removing their HTML marker and state entry removes their owned files on save. Deleting a wrapper removes its descriptor and owned state directory. Moving the same wrapper ID to another page moves its owned files with it. Adding a complete template creates fresh state; saving a library package excludes live state. The `.dikciz-widget` marker identifies wrapper directories owned by Dikciz for cleanup. Files outside those owned directories remain untouched.
