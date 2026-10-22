# Dikciz capabilities

Use this catalog to choose the right feature. Use the running build's `controlStatus`, `automationStatus` and MCP tool schemas for exact command arguments, input limits, permissions and current availability. A declared Android permission does not imply an implemented action or a granted capability.

## Launcher and appearance

- Use a two-axis grid of native pages. Create, rename, reorder, select, remove, search and lock pages; select the Android Home destination. Each page has a fixed viewport and cell grid.
- Configure rows, columns, gaps and padding. Add, move and resize items within the grid. Invalid placement or a full page returns a refusal instead of overlap or off-screen placement.
- Search installed launchable apps by label or package. Launch them, add shortcuts, open Android app info, request uninstall, or force stop with device-granted root.
- Create native app groups by dropping one shortcut onto another; manage group members.
- Host real third-party Android AppWidgets, including their Android configuration flow.
- Create HTML widgets, install library packages, append working card presets or source blocks, save custom cards as presets, edit HTML/CSS/JavaScript/state, fit content within assigned cells, and change native widget appearance.
- Select themes, custom or system fonts, wallpapers and widget-style overrides. Locks prevent edits without blocking normal interaction.
- Manage Lua scripts through the native dashboard: create, edit, enable, disable, run, import/export, inspect status and logs.

Read the [user guide](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/user-guide.md) for native UI steps. Page and widget identities come from the current home, not from example names or guessed screen coordinates.

## Configuration and recovery

The public root is `/sdcard/Dikciz/`. `config.json` references separate page and widget documents. Scripts have metadata, Lua source, state and run-status files. Automation policies and subscriptions, widget-library packages, HTML blocks, themes, fonts, wallpapers, control-port and remote-auth configuration, and logs have their own paths.

Valid home and rendering-asset edits are watched and applied without restarting the app. Direct control-port edits apply when the listeners restart. Configuration replacement validates and saves atomically. Preserve unrelated root namespaces and avoid whole-document replacement for a narrow change. Read the [configuration guide](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/dikciz-configuration.md) before editing files or assembling a replacement.

Safe mode opens the bundled first page without overwriting saved configuration. Diagnostics, snapshots, logs, screenshots, UI dumps and recovery remain available, while normal home edits and device mutations are blocked. Reset restores bundled home and automation files, so obtain approval before using it.

## HTML bridge

HTML widgets receive `window.dikciz`, `window.selfWidget` and `dikciz-ready`. Start bridge work after that event.

| API | Capability |
| --- | --- |
| `dikciz.command(type, fields)` | Call a launcher command. |
| `dikciz.dispatch({ actions })` | Run typed actions and inspect each result. |
| `dikciz.emitEvent(name, payload, coalescingKey)` | Publish a custom event. |
| `dikciz.onEvent(listener)` | Subscribe while this widget renders; returns an unsubscribe function. |
| `dikciz.fitContent()` | Remeasure content-fitted height within its assigned cells. |
| `dikciz.pages()`, `dikciz.home()`, `dikciz.widget(address)` | Read the page map and saved records. |
| `dikciz.patchWidget(address, values)` | Persist supported HTML widget fields. Replacing source can destroy the calling WebView. |
| `dikciz.patchWidgetState(address, values)` | Persist another HTML widget's state. |
| `dikciz.patchDom(address, selector, values)` | Change rendered DOM without saving it. |
| `selfWidget.get()`, `selfWidget.patch(values)` | Read or patch this widget without hardcoding its address. |
| `selfWidget.patchState(values)`, `selfWidget.patchDom(selector, values)` | Change this widget's saved state or rendered DOM. |

Card instances use `selfWidget.itemFor(element)` or `selfWidget.item(id)` for independent state and files under the owning page/widget directory. Their methods include `getState`, `patchState`, `readFile` and `writeFile`. Deleting a card removes its owned files; deleting a wrapper removes all its instance files.

Browser events include `dikciz-ready`, `dikciz-state`, `dikciz-event`, `dikciz-item-state` and `dikciz-block-appended`. HTML receives subscribed events only while its page is selected and its WebView exists. Use background Lua for durable event processing. Read the [HTML bridge reference](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/scripting-tutorial.md#html-bridge-reference) for signatures and examples.

## Lua and event sources

An enabled script needs an enabled subscription and a policy permitting both the event capability and its returned actions. It receives an event and returns status plus typed actions. It can persist script state, update HTML widget state and publish custom events.

Built-in event names:

| Domain | Events |
| --- | --- |
| Time and power | `time`, `alarm`, `battery`, `charging`, `powerSaveMode`, `deviceIdleMode`, `thermalStatus` |
| Screen and system | `screen`, `userPresent`, `nightMode`, `deviceConfiguration`, `packageChanged`, `ringerMode`, `interruptionFilter` |
| Connectivity and sensors | `connectivity`, `bluetoothState`, `sensor`, `location`, `healthDailySteps` |
| Personal data and communication | `calendarEvent`, `contactsChanged`, `phoneState`, `smsReceived`, `clipboardChanged` |
| Notifications and media | `notificationPosted`, `notificationRemoved`, `mediaSession` |
| Accessibility and administration | `accessibilityWindow`, `accessibilityServiceState`, `deviceAdminState` |
| Launcher and explicit triggers | `widgetChanged`, `manual`, user-defined custom events |

Subscriptions support per-subscriber delivery intervals and optional coalescing keys. Alarm, sensor, location and Health Connect subscriptions require their own fields; notification and media subscriptions can filter packages. Metadata and content capabilities differ for personal-data sources. Do not request content access for a metadata-only task.

Lua has a bounded runtime and owner command access through `context.request(json)` and `context.requestJson(json)`. Enabling a script grants access to ordinary control commands, including app-accessible files and shell; Android permissions and root approval still apply. The returned-action policy does not restrict those command functions. Lua receives no Android object handles. `io`, `os`, `package`, `require`, dynamic loaders, `debug` and `print` are unavailable. Use typed actions, script state and logs. Read [every built-in event](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/scripting-tutorial.md#every-built-in-event) and [special subscription fields](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/scripting-tutorial.md#the-special-subscription-fields) before writing an automation.

## Typed actions

These actions are available through the documented Lua/HTML action executor. Lua policies must explicitly permit them; Android access checks still apply to HTML actions.

| Action | Capability |
| --- | --- |
| `selectPage` | Select a page. |
| `patchWidget`, `patchState`, `patchDom` | Change saved widget fields, script state, or rendered HTML. |
| `launchApp`, `appAction` | Open a component or run supported app actions. Lua cannot use `addShortcut`. |
| `postNotification`, `sendSms` | Post a notification or send an SMS with Android approval and access. |
| `explicitIntent` | Dispatch an explicit activity or broadcast intent. |
| `emitEvent` | Publish a custom event. |
| `mediaControl`, `mediaVolume` | Control an allowed media session or volume. |
| `notificationControl` | Invoke a supported notification action token. |
| `lockDevice` | Lock the screen with enabled Device Administration. |
| `accessibilityAction`, `accessibilityGesture`, `accessibilityGlobalAction` | Act on another app's fresh accessibility state or use supported system navigation actions. |

Read [typed actions](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/scripting-tutorial.md#return-device-and-launcher-actions-from-lua) for fields, allowed targets and outcomes. SMS can cost money. Notification actions, intents and cross-app interactions can affect other apps; keep them within the user's requested task.

## Remote controls and UI automation

MCP also exposes packaged documentation through `resources/list` and `resources/read`. Start with `dikciz://docs/index`, then fetch the needed guides or recipes individually. Discovery returns eight entries per page; follow `nextCursor`. Each section is at most 4096 UTF-8 bytes. The supplied client supports `mcp-resources` and `mcp-read <uri>`. Read the [resource guide](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/mcp-resources.md) for the protocol and examples.

WebSocket, Streamable HTTP MCP and plain HTTP expose the same launcher command target:

- Discover protocol, command fields, matching tools, limits, automation access, policy vocabulary, sensors and event schemas.
- Identify the serving package and version with `deviceInfo`, inspect installed packages with `packageInfo`, and restart with `restart`. Reconnect and verify a new `application.instanceId`; do not kill the process through its own shell.
- Use `launcherHandoff` to transfer control between installed Dikciz release and debug packages. Verify target package and version after reconnecting. Each target needs file access; same-package handoff changes nothing.
- Capture the actual visible Android display with `displayScreenshot` on Android 11+ with owner-enabled Accessibility. Existing `screenshot` captures the launcher window.
- Inspect semantic UI snapshots, find or wait for nodes, obtain home/widget/config records, logs, diagnostics, screenshots and UI dumps.
- Tap, long press, select pages, scroll, set text, launch apps, place/move/resize widgets and configure the page grid.
- Read app catalogue and execute supported app actions.
- Replace or seed complete configuration, or reset the home after approval.
- Trigger enabled scripts, dispatch typed actions, synchronize automation services and open Android access setup.
- Use `autoRequestPermissions: true` on commands to request owner review when Android access is missing. Default calls return permission status without prompting. Use `requestDeviceAccess` or `dikciz_request_device_access` with one `requirementId` to ask directly. Read the returned permission state, `notificationPosted` and `instructions`, tell the owner to check the phone, then verify access before explicitly retrying an authorized operation.
- Inspect another app through Accessibility and invoke supported node actions. Obtain a fresh snapshot; its ID expires after five seconds and changed windows invalidate it.
- Browse, read, upload and create files or directories anywhere Android grants Dikciz access. Transfer binary files in bounded chunks.
- Request installation of an uploaded standalone APK and poll its Android session until success, cancellation or failure. Android controls unknown-source permission and confirmation. Read [files, APKs and shell](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/files-and-apks.md).
- Run device shell commands and explicit Android intents. `shell` requests root by default; use `root: false` for app-sandbox commands. Shell defaults are 30000 milliseconds and 65536 output bytes; set `timeoutMilliseconds` and `maxOutputBytes` per call and inspect the live schemas for supported ranges.
- Stream live shell output, including logcat, with `shellStream` or `dikciz_shell_stream`. A zero timeout keeps streaming until stopped or disconnected. Use the supplied client for automatic MCP SSE log opt-in and `--stream-raw` for output bytes. Stop only an owned stream with `shellStreamStop` or `dikciz_shell_stream_stop`. Read the [streaming guide](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/files-and-apks.md#stream-logs-and-long-running-commands).
- Inspect remote bearer-auth state and enable or disable it after approval. Password creation, replacement and clearing are phone-side operations.
- Subscribe to the persistent WebSocket event schema advertised by `controlStatus`, including command completion, UI rendering, HTML-widget events and automation events.
- Deliberately crash one HTML renderer to test recovery through `htmlWidgetRendererCrash`. This is a diagnostic, not a normal widget operation.

For exact commands and MCP tools, read [automation control](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/automation-control.md) and query the live contract. Do not manufacture a remote command by copying a Lua action name. Match WebSocket replies by request ID while handling interleaved events. For MCP, honor initialization and session headers.

## Direct Android data

Use `nativeDataStatus` or `dikciz_native_data_status` before navigating Contacts, Messages or Settings. Direct commands query contact, SMS, calendar, call-log and media providers, write contact/calendar rows atomically, list SIMs and inspect calls, clipboard, location, notifications, media sessions and device state. `smsSend` requires an idempotency key; `smsStatus` distinguishes submission, sent callbacks and carrier delivery. Read [native device control](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/native-device-control.md) or the MCP native-data and contact-SMS resources. Prefer these commands when they cover the task.

## Access, logs and verification

Android owns runtime permissions, special access, roles and root approval. Notification listening, Do Not Disturb control, Accessibility, Health Connect, exact alarms, battery exemptions, file access and Device Administration have separate setup requirements. Use `automationStatus` and the phone's Automation access screen to distinguish unsupported, unavailable and not-yet-granted access. A broad manifest declaration is not permission to grant everything.

Daily UTC JSON Lines files at `/sdcard/Dikciz/logs/YYYY-MM-DD.log` cover Lua failures, HTML console warnings/errors and launcher components. **Settings**, **Configure logging** selects the minimum level and retention; defaults are INFO and seven days. The logging object also configures the 16 MiB cleanup budget and failed-script alerts. The viewer and remote log tools expose bounded safe records, including app targets as nullable `componentPackage` and their `actionType`. Successful app launches stay logged without a popup. Read the [logging reference](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/dikciz-configuration.md#logs) for filtering, retention and redaction.

For app and activity launches, inspect every `activityLaunches` record. Only `verified: true` with `outcome: "launched"` proves the target was observed in the foreground; acceptance, missing Accessibility and timeouts do not. Native launches write the same terminal observation to logs. Verify requested work through the fresh UI or read-back configuration, each action's outcome, and script status/logs. State patches persist; DOM patches do not. A queued event, successful transport response or missing error is not proof the intended Android effect occurred.
