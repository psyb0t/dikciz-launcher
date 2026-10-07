# Dikciz configuration

Dikciz keeps its editable home under `/sdcard/Dikciz/`. The directory is the
source of truth for the launcher. Valid changes reload automatically, within a
couple of seconds of the write, whether the edit came from the launcher, a
script, adb, or a file manager. An invalid file leaves the last accepted home
visible and records the failure in the local log.

## Public file tree

```text
/sdcard/Dikciz/
├── config.json
├── pages/<page-id>/page.json
├── pages/<page-id>/widgets/<widget-id>.json
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
├── themes/<theme-id>.json
├── fonts/
├── wallpapers/
└── logs/YYYY-MM-DD.log
```

On first run, Android must grant Dikciz access to this directory. The storage
screen links to Android's own access panel. Until Android grants it, the
control planes still expose the storage screen for inspection but cannot load
or change configuration.

Each file has one job. `config.json` owns global settings and the home
manifest. Pages own their metadata and widget references. Widgets, scripts,
automation rules, themes, and library packages remain separate so a normal edit
does not turn the root into one large document.

## Edit and inspect configuration

Use Android file tools, an ADB tunnel, or the control planes. In the lab, move
only an artifact through the provided targets:

```bash
DEVICE_FILE=/sdcard/Dikciz/config.json ARTIFACT_FILE=config.json make adb-pull
# Edit .android-lab/shared/artifacts/config.json.
DEVICE_FILE=/sdcard/Dikciz/config.json ARTIFACT_FILE=config.json make adb-push
```

The control planes expose a complete assembled document. They validate a full
replacement atomically, write the matching public tree, and reload the home:

| Interface | Read | Replace | Restore bundled home |
| --- | --- | --- | --- |
| WebSocket | `configGet` | `configReplace` or `configSeed` | `reset` |
| MCP | `dikciz_config_get` | `dikciz_config_replace` or `dikciz_config_seed` | `dikciz_config_reset` |

`configReplace` is a complete replacement, never a partial patch. Start by
reading `configGet`, edit the returned document, then replace it. The command
result contains the normalized saved configuration and a fresh semantic UI
snapshot.

## Root document

`config.json` has a version and Dikciz-owned namespaces:

```json
{
  "version": 1,
  "limits": {},
  "logging": {},
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

The assembled view returned by `configGet` also contains scripts and automation
objects. The persisted tree keeps their named source and policy files separate.
Dikciz preserves root namespaces that it does not own when it performs a native
edit.

`limits` bounds document size, identifiers, titles, page count, widgets per
page, component strings, and HTML renderer resources. The bundled limits are a
one MiB configuration, 24 pages, 128 widgets per page, 65,536 text characters,
120-character titles, and 80-character identifiers. `maxHtmlWidgetsPerPage`
defaults to eight enabled HTML widgets. Native shortcuts, app
groups, provider widgets, and the native script dashboard do not consume that
budget. `maxHtmlWidgetDocumentBytes` defaults to 196,608 UTF-8 bytes for the
fully generated HTML document, including the widget context Dikciz injects at
load time. The parser also applies non-configurable upper limits before reading
a file.

`logging` controls level, retained days, disk budget, failed-script
notifications, and the notification quiet period. See [Logs](#logs).

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

Page positions are zero-based in files. Human-facing addresses are one-based:
`1H1V` is column one, row one. A widget's complete address combines that page
address and widget ID, for example `1H1V-command-deck`. The address changes
when a widget moves to another page. Its ID does not.

Long press the empty part of a page rail, past its dots, to create a page.
The new page lands at the far end of that rail, never beside the page you are
on. Holding the horizontal rail before the dots inserts a new first column and
shifts every existing column right; holding it after the dots appends a new
last column. The vertical rail does the same at the top and the bottom of the
current column. The page is saved and selected immediately, so there is nothing
to confirm and nothing to lose by navigating away.

Reorder pages from the page menu under **Move this page**: **Move left**,
**Move right**, **Move up**, and **Move down**. Each one exchanges this page's
coordinate with the single page already sitting at the neighbouring coordinate.
A direction with no page there is shown disabled and changes nothing. A move
rewrites only `position`, so page IDs, titles, locks, widgets, `homePageId`,
and `selectedPageId` all survive it. A locked page can neither create a page nor
be moved.

Every widget has `id`, `type`, `enabled`, `cell`, `locked`, and optional
`style`. Visible cards also have `title`. IDs are global and case-insensitively
unique across all pages. Native creation makes readable lowercase slugs with a
numeric suffix. Widgets never stack. `zIndex` is not a supported widget key, and
Dikciz rejects a configuration that contains it.

Supported widget types are:

| Type | Purpose |
| --- | --- |
| `html` | A resizable WebView with local HTML, CSS, JavaScript, and state. |
| `app` | One explicit Android activity shortcut. |
| `appGroup` | A native group of up to 32 Android activity components, made by dropping one app tile onto another. |
| `provider` | A native third-party Android AppWidget provider card. |
| `scriptDashboard` | Native view for script state, logs, enablement, and editing. |

`text`, `toggle`, and `lua` are not widget types. Create visible controls as
HTML widgets. Lua stays in the script tree and appears in a script dashboard.

### The native page grid

A native page is one fixed viewport. It never gains canvas height, and adding a
widget never scrolls the page below an existing widget.

`launcher.home.nativeGrid` describes the grid every page is laid out on:

```json
"nativeGrid": { "columns": 4, "rows": 6, "gapDp": 8, "outerPaddingDp": 12 }
```

`columns` and `rows` accept 1 to 12. `gapDp` and `outerPaddingDp` accept 0 to
64. The bundled default is 4 columns by 6 rows. The record is optional; a
document without it uses that default.

Every top-level widget owns whole grid cells through one `cell` record:

```json
"cell": { "column": 0, "row": 0, "columnSpan": 2, "rowSpan": 1 }
```

`column` and `row` are zero-based cell indexes. Both spans are cell counts and
are at least 1. The rectangle must fit inside the configured grid, and two
widgets on one page may never claim the same cell. Rendered pixels are derived
from the current viewport at draw time, so the same logical rectangle survives
page selection, process recreation, and rotation.

`cell` is the only placement a widget carries. Any other placement key is an
unknown key and the strict parser rejects the document.

### Placement failures

Dropping a widget on a cell another widget already holds is not always a
failure. The tile under the pointer decides what the drop means:

- two app tiles merge into one `appGroup`, and an app tile dropped on a group
  joins it;
- two widgets that cover the same number of cells trade places;
- anything else is refused.

Both outcomes are shown while the finger is still down. A merge target swells,
and a trade moves both tiles at once, so leaving the cell puts the other widget
back before the drop.

Placement reports one of three finite failures. A rejected operation writes
nothing to the public tree and emits one bounded diagnostic.

| Code | Meaning |
| --- | --- |
| `page_full` | The page has no free cell for any further item. |
| `grid_collision` | The requested cells are already used. |
| `grid_bounds` | The rectangle leaves the configured grid. |

### Editing the page grid

The grid is editable in **Settings**, under **Page grid**, and through the
`gridSet` command and the `dikciz_grid_set` tool. One grid applies to every
page.

A change is validated against every placed widget before anything is written.
If a single widget would leave the grid or collide under the new geometry, the
whole change is rejected with `grid_bounds` or `grid_collision`, the offending
page and widget are named, and the saved document is untouched. Move or resize
that widget first, then change the grid again.

While a widget is being moved or resized, the page draws every cell of the grid
behind the content. Placement snaps to whole cells, so the guides show where the
widget can land. They appear when a move is armed or a resize starts, and they
go away when the gesture ends.

There is no layout migration and no compatibility mode. A document that does
not carry the grid schema is rejected on load with the usual validation error.

## Native app shortcuts and app drawer

The Dikciz mark beside the horizontal page rail opens the launcher controls,
and from there the native app drawer. An upward swipe that starts on that rail opens it too. Search filters
launchable installed apps by label or package name. Tap a result to launch it.
Use its overflow button, or long press the row, for add shortcut, Android app
info, uninstall, and force stop.

Android owns the confirmation for app info and uninstall. Force stop requests
root and reports `root_unavailable` when `su` is unavailable or denied. It
never falls back to the ordinary Dikciz app shell.

Adding an app places an `app` tile in the first free grid cell, row-major from
the top left. A page with no free cell rejects the request with `page_full` and
adds nothing. The placed tile is left selected so its move and resize controls
work straight away. A tap anywhere on the tile launches the activity, and a drag
from anywhere on it moves the tile to another free rectangle. A widget that owns
its own content, an HTML document, an Android provider view, or the script
dashboard, is dragged from its top `24Dp` instead, because its body needs the
touches for itself.

An app tile draws its icon as the largest square the cell allows, measured
against the narrower side, with the icon and its label centred in the cell as a
pair. Resizing the tile resizes the icon with it.

Dropping one app tile onto another makes an `appGroup` holding both, in drop
order, at the target's cell. The group opens as a grid of its member icons. A
tap launches a member and a long press removes it. Removing down to a single
member replaces the group with a plain `app` widget for the survivor, keeping
the cell and the widget ID, so a script addressing that widget keeps working.
A group is never saved with fewer than two members.

An `app` widget stores one flattened Android `component` and one `displayStyle`:

| `displayStyle` | Appearance |
| --- | --- |
| `iconLabel` | App icon and label. This is the default. |
| `button` | Text button. |
| `buttonIconLabel` | Button with icon and label. |

The native drawer and the WebSocket and MCP `appCatalogue` and `appAction`
commands share the same installed-app catalogue and action runtime. Remote
`appAction` calls use a flattened component. `addShortcut` needs a selected
native page and is therefore rejected from Lua, which has no selected-page
context.

## HTML widgets

An HTML widget is a normal, resizable home widget:

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

`heightMode` is `fixed` when omitted. `content` fits the rendered document
inside the widget's assigned row span. It never expands the widget past its
cells and never grows the page, so it changes nothing in the saved document.
Long press an HTML widget for native edit, appearance, resize, fit-content, add
block, lock, and delete actions. Dikciz consumes that long press, so the
WebView does not start browser text selection or browser controls.

The native WebView viewport is clipped to the widget frame even when document
CSS overflows. During resize, Dikciz draws the native outline and handles over
the WebView, so the frame stays visible and remains the resize boundary.

State changes do not reload the WebView. The document receives
`dikciz-state`, and `selfWidget.state` becomes the full replacement state.
Update only the DOM nodes that changed.

The native editor is a source editor, not an HTML preview. Its labeled `HTML`,
`CSS`, and `JavaScript` fields use code text and a boxed editor frame. They
show literal source, including tags, comments, and entities. For
example, a divider is shown as `<hr class="dikciz-block dikciz-block-divider" />`,
not as a rendered rule. Opening **Edit HTML widget** always reads the current
saved widget record, including blocks added after the long-press menu opened.

HTML packages are reusable starters under `widget-library/<package-id>/`. A
package has strict metadata in `widget.json`, `index.html`, and optional
`style.css` and `script.js`. `widget.json` is at version 2 and declares a
`span` with `columnSpan` and `rowSpan` instead of a pixel `size`. The picker
previews a package, then copies its source into a new sibling widget placed on
the grid. Choosing a package never edits an existing widget's source. Later package changes do not silently modify
placed widgets. Save an edited widget to make or replace a package. Dikciz
stages and validates a complete replacement before it publishes it. If a save
is interrupted, the next library read either completes a valid retained stage
or restores the last valid package. The picker never shows a partial package.

### HTML blocks

Blocks are widget-local authoring material under `widget-blocks/<block-id>/`,
alongside the package library. A block has `block.json` with `version`, `id`,
`title`, `description`, and `previewLabel`, plus a `fragment.html` source
fragment.

Open **Add block** from an HTML widget's own long press. The picker previews the
block in that widget's theme and cell bounds, then one Add action appends the
fragment to that single widget's document. A block consumes no grid cell and
creates no second widget.

Dikciz saves the marked source first, then inserts that same fragment into the
already-live target document. The target stays in place, keeps its JavaScript
runtime and state, and scrolls to the new block. It does not rebuild the native
page or reload the WebView.

Inserted source is wrapped in a stable marker so it stays editable:

```html
<!-- dikciz:block heading block-<instance-id> -->
<section class="dikciz-block dikciz-block-heading">...</section>
<!-- /dikciz:block block-<instance-id> -->
```

Edit or delete that exact markup in the normal HTML source editor afterwards.

A fresh public tree contains four editable starter blocks: `heading`,
`status-row`, `action-button`, and `divider`.

A fresh public tree contains five editable starter packages: `app-launcher`,
`brightness`, `lock-screen`, `notifications`, and `system-status`. They show
the package ID in their visible header. The lock and notification packages call
the direct typed Android action path, not a bundled Lua script. Every starter
package also has **Latest log**, which reads the same bounded safe log
projection available through the script dashboard, WebSocket, and MCP.

Dikciz installs a bundled package only when that package ID has no public
directory yet. It never overwrites an existing package during an app update,
so a user-edited starter remains the user's version. Save an edited widget to
replace that package deliberately, or delete that one package directory before
the next fresh installation if the bundled version is wanted again.

The JavaScript bridge and event model are documented in
[Automation control](automation-control.md#html-widget-javascript).

## Lua scripts and automation

Lua scripts are background or event-driven logic, not home widgets. Each script
has four files:

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

`capabilities` names the events the script may receive. `actions` names what it
may do, and Dikciz enforces it when the action runs, not only when the file
loads. An action a script returns that its policy does not list is refused with
the outcome `capability_denied`, the launcher does nothing, and the refusal is
logged with that `reason`. A script whose policy is missing gets no actions at
all. Actions a Dikciz HTML widget dispatches directly are not policy gated,
because the owner invokes those on the device.

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
manualTrigger
```

The current action names are:

```text
accessibilityAction, accessibilityGesture, accessibilityGlobalAction,
selectPage, patchWidget, patchState, patchDom, launchApp, appAction,
postNotification, sendSms, explicitIntent, emitEvent, mediaControl,
mediaVolume, notificationControl, lockDevice
```

Read the [scripting tutorial](scripting-tutorial.md) for end-to-end event subscriptions, custom events, HTML bridge calls, cross-widget state, and action results.

`sendSms` takes a `recipient` and a `body`. The recipient is a dialable number
of up to 20 characters: digits with an optional leading `+` and the usual
dialling punctuation. The body uses the configured text limit and is divided
into parts when it is longer than one message segment.

Dikciz is not the default SMS app. A sent message leaves the device, but Dikciz
does not write it to the Android SMS provider, so it does not appear in the
owner's messaging history. Android also limits a non-default app to about thirty
messages in thirty minutes and then asks the owner to confirm each send. Dikciz
reports what Android returned and does not work around that prompt.

The action needs the `SEND_SMS` Android permission, which `automationStatus`
reports as `smsSend` under `androidAccess`. Without it the action returns
`android_permission_denied` and sends nothing. A policy that does not declare
`sendSms` is refused before Android is reached.

Event subscriptions use the published event names: `time`, `battery`,
`charging`, `powerSaveMode`, `deviceIdleMode`, `nightMode`,
`deviceConfiguration`, `packageChanged`, `ringerMode`, `interruptionFilter`,
`connectivity`, `thermalStatus`, `bluetoothState`, `screen`, `userPresent`,
`alarm`, `sensor`, `healthDailySteps`, `location`, `calendarEvent`,
`contactsChanged`, `phoneState`, `smsReceived`, `notificationPosted`,
`notificationRemoved`, `mediaSession`, `clipboardChanged`, `widgetChanged`,
`deviceAdminState`, `accessibilityWindow`, `accessibilityServiceState`, and
`manual`.

Use a new valid event name for a custom event, for example:

```json
{
  "event": "weather.refresh.completed",
  "minimumIntervalMilliseconds": 0
}
```

Both Lua and HTML can emit it. Subscribers receive the name in `event.type`
and the JSON object in `event.payload`.

`minimumIntervalMilliseconds` is a per-script-subscription monotonic delivery
floor. It suppresses a matching event when that subscription was delivered too
recently. Alarm subscriptions also keep their own due time: Dikciz wakes at
the shortest active alarm interval, but a longer subscription runs only when
its own deadline is due. A configuration replacement begins a new schedule
window. After a process restart, the retained Android alarm causes one recovery
delivery and starts the next deadline from that delivery. Late alarms run once
and move their next due time ahead from that delivery. Dikciz does not replay
missed intervals in a burst. Turning off either the script or its automation
entry stops its Android sources and cancels its alarm schedule.

A `location` subscription needs `locationPrecision`, either `approximate` or
`precise`, and the value decides both the capability it needs and what the
event carries. A precise subscription needs `locationPrecise` and the Android
fine-location permission, and its payload carries `provider`, `latitude`,
`longitude`, `accuracy`, `altitude`, `bearing`, and `speed`. An approximate
subscription needs `locationApproximate` and either Android location
permission, and its payload carries only `provider` and a `latitude` and
`longitude` rounded to two decimal places, about a kilometre. Dikciz reads the
device location once and then builds one event per subscribed precision, so an
approximate subscription receives the approximate event even while the
launcher holds the fine permission for some other subscription. A precise
subscription without the fine permission registers nothing and receives
nothing.

Lua receives a bounded event table plus read-only `context.state`,
`context.widgets`, and `context.androidAccess`. The last object is the same
current Android access map that `automationStatus.androidAccess` returns. It
contains Booleans only, not Android object handles. During `accessibilityWindow`,
Lua also receives the bounded active Android tree in `context.accessibility`.
Return a status and actions.
`accessibilityServiceState` has only its fixed event payload:
`state` is `connected`, `interrupted`, or `disconnected`, and `enabled` is the
current Accessibility owner-setting Boolean. It does not include
`context.accessibility` because it has no active-window snapshot.
`accessibilityAction` requires its current `snapshotId`, a generated `nodeId`,
and a supported action. `accessibilityGesture` uses its current `snapshotId`
with a bounded `tap` or `swipe` document. `accessibilityGlobalAction` uses one
supported Android global action name. `patchWidget` targets a complete widget
 address such as `1H1V-command-deck`. A Lua script cannot run arbitrary shell
 text or obtain Android object handles. Use the HTML bridge or local control
 planes for shell commands, intents, semantic UI input, and other launcher
 commands.

### Lua runtime surface

The bundled Workbench shows this same quick reference on a fresh install. It
is the supported script surface, rather than a promise that every LuaJ or
Android API is present. Core globals are `assert`, `error`, `ipairs`, `next`,
`pairs`, `pcall`, `select`, `tonumber`, `tostring`, `type`, and `xpcall`.
Available libraries are `math`, `string`, `table`, and `bit32`.

The runtime removes dynamic loading and host escape routes: `debug`, `io`,
`os`, `package`, `require`, `load`, `dofile`, `loadfile`, and `print` are not
available. It also removes `string.format`, `string.gsub`, `string.rep`,
`table.concat`, `table.move`, and `table.unpack` to keep script work bounded.
Scripts receive no Android object handles and cannot execute arbitrary shell
text. The typed action list above is the supported route to launcher and
Android behavior.

Fresh Dikciz includes four scripts:

| Script | What it does |
| --- | --- |
| `screen-lock` | Locks the screen when Device Administration is already active. Otherwise it records `device_admin_inactive`. Open Automation setup yourself to grant that Android access. |
| `hourly-clock` | Requests a current-time notification each minute while Android allows the scheduler to run. |
| `notification-digest` | Watches notification events and patches the `1H1V-command-deck` unread list. |
| `settings-walkthrough` | A manual cross-app starter. It reports when Accessibility is off. With the owner-enabled service, it opens Android Settings and selects Network and internet from the current bounded window snapshot. |

Use the native Script dashboard or Settings to enable, disable, edit, import,
export, delete, inspect status, and read logs. The dashboard, Settings,
WebSocket `scriptLogs`, and MCP `dikciz_script_logs` read the same safe log
projection. A failed Lua compile or handler run keeps `invalid_lua` as its
stable run status. Its matching log record includes a bounded, sanitized
`diagnostic` with the public `scripts/<script-id>/main.lua` location and line
when LuaJ provides one. Credential-shaped values are redacted before the
native viewer or either remote control plane receives the record.

## Android access

The Dikciz manifest declares a broad Android access surface. It includes
location, contacts, calendar, phone and call state, SMS, camera, microphone,
Bluetooth, nearby Wi-Fi, media, notifications, activity and body sensors,
Health Connect, files, exact alarms, overlays, writable system settings, usage
access, package installation, battery-optimization exemption, Device
Administration, and notification-listener support.

The declaration allows Dikciz to request access. Android, the installer, the
selected device role, the OEM, and root manager still decide whether it is
available. Enable the automation policy first, then use **Settings**, then
**Automation**, then **Set up full device access**. The screen shows the current
state and opens the relevant Android panel. `automationStatus` and
`dikciz_automation_status` return the same machine-readable status.

Cross-app Android UI automation is an Accessibility service requirement. Its
record is `deviceAccess.requirements[id="accessibility"]`; its current Boolean
is `automationStatus.androidAccess.accessibility`. The state is `needsSetup`
until the phone owner enables Dikciz in Android's Accessibility settings, then
becomes `granted`. This is not a runtime permission and is not configurable in
`config.json`, a Lua policy, an HTML widget, WebSocket, or MCP. Those surfaces
can inspect the state and open the Android panel only.

The local `shell` command and `dikciz_shell` tool request `su` by default.
Dikciz first runs `su -c id -u`; only an exact UID of `0` permits the requested
command and `executionScope: "root"`. A missing, denied, malformed, or timed
out root probe returns `root_unavailable` through WebSocket and an MCP tool
error. It never falls back to Dikciz's app user. Pass `root: false` for the
ordinary `/system/bin/sh` route, whose result reports
`executionScope: "app_sandbox"`, exit code, output, and truncation state.

## Remote bearer password

`control/remote-auth.json` is separate from `config.json`. It stores version,
bcrypt verifier, cost, and enabled state. It never stores the plaintext
password. Set, replace, clear, enable, or disable the password from **Settings**,
then **Automation**. The phone does not ask for the old password when changing
it.

When enabled, forwarded WebSocket and MCP requests need
`Authorization: Bearer <password>`. A remote client can check, enable, or
disable the setting with the documented remote-auth commands. It cannot read,
replace, or clear the password.

## Themes, fonts, and logs

`launcher.home.selectedThemeId` selects a reusable JSON file under `themes/`.
Theme data is never copied into every widget. `styleDefaults.widget` supplies
the baseline native frame, then a widget's `style` object overrides individual
background, border, spacing, text, and font fields.

Fonts can be bundled, Android system choices, or a plain `.ttf`, `.otf`, or
`.ttc` filename in `fonts/`. Wallpapers are PNG, JPEG, or WebP files in
`wallpapers/`.

Dikciz writes JSON Lines diagnostics under `logs/YYYY-MM-DD.log`. The default
keeps seven days and 16 MiB. The logging object can enable failed-script Android
notifications and set the minimum interval for each script's failure alert.
HTML widget console warnings and errors are included as HTML-kind records with
the stable widget ID. Assignment-shaped credentials and bearer values in console
text are redacted before persistence. If an Android WebView renderer is lost,
Dikciz destroys and recreates only that widget in its existing native frame.
The Settings viewer and control planes return a bounded
safe record list, including launcher-component records that use their component
name in the record's script-ID field for compatibility. They do not return raw
script source, state, widget text, or configuration.

## Recovery

**Reset home** replaces the bundled home, its scripts, policies, and
subscriptions. It keeps other root namespaces and unrelated Android launcher
data. It removes obsolete Dikciz AppWidget allocations.

**Safe mode** starts the bundled first page without writing the current public
configuration. It keeps read-only diagnostics, snapshots, logs, screenshot,
UI dump, reset, and normal-restart recovery paths available while it blocks
normal home edits and device mutation commands.
