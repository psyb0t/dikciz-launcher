# Typed automation actions

Typed actions are separate from ordinary commands. HTML uses `dikciz.dispatch({actions})`; HTTP/WebSocket use `automationDispatch`; MCP uses `dikciz_automation_dispatch`. Network dispatch requires an existing HTML `sourceWidgetAddress`. Lua returns `{status="...",actions={...}}` from `on_event`. An enabled Lua policy must allow the event capability and each returned action's policy name.

A dispatch contains 1 through 8 actions. Lua may also return one action, an action array, status without actions, or nil. Unknown fields and unsupported values fail validation. Actions execute in order, but the array is not a transaction: inspect every outcome and retry only work that did not complete. An Android permission prompt never grants access or retries work.

## Every action and its fields

Every record has `type`. Fields below are required unless marked optional. Most policy names match `type`; the volume exception is explicit.

| Type | Fields beyond type | Lua policy action | Effect and constraints |
| --- | --- | --- | --- |
| `selectPage` | `pageId` | `selectPage` | Select an existing stable page ID. |
| `patchWidget` | `widgetAddress`, `values` | `patchWidget` | Persist supported fields in another widget. See field limits below. |
| `patchState` | `values` object | `patchState` | Replace current script state in Lua; replace the source HTML widget's state in HTML/network dispatch. |
| `patchDom` | `widgetAddress`, `selector`, `values` | `patchDom` | Change rendered HTML without saving source. Selector is at most 512 characters. |
| `launchApp` | `component` | `launchApp` | Launch a flattened Android component. Inspect foreground verification separately from acceptance. |
| `appAction` | `component`, `action` | `appAction` | `launch`, `appInfo`, `uninstall` or `forceStop`; no `addShortcut` in typed actions. Force stop requires approved root. |
| `postNotification` | `title`, `text` | `postNotification` | Post a Dikciz notification subject to Android posting access. |
| `sendSms` | `recipient`, `body` | `sendSms` | Legacy submission-only SMS. Prefer the ordinary `smsSend` command for SIM choice, idempotency and receipts. |
| `explicitIntent` | `intentType`, `component`; optional `action` | `explicitIntent` | Explicit `activity` or `broadcast`. For data URIs and ordinary intent fields, use the `intent` command. |
| `emitEvent` | `event`, `payload` object; optional `coalescingKey` | `emitEvent` | Publish a valid custom event. Publication is not consumer completion. |
| `mediaControl` | `packageName`, `command`; conditional `positionMilliseconds` | `mediaControl` | Existing media session: `play`, `pause`, `playPause`, `skipNext`, `skipPrevious` or `seekTo`. Only `seekTo` requires and accepts integer position 0..86400000. Notification-listener access is required. |
| `setMediaVolume` | integer `levelPercent`, 0..100 | `mediaVolume` | Set Android's media stream volume, mapping the percentage to its discrete minimum/maximum indices. |
| `notificationControl` | `actionToken` | `notificationControl` | Invoke an unexpired plain action from `notificationsList`. Use `notificationManage` for inline reply, dismissal or snooze. |
| `lockDevice` | None | `lockDevice` | Lock with owner-enabled Device Administration. |
| `accessibilityAction` | `snapshotId`, `nodeId`, `action`; optional `text` | `accessibilityAction` | Act on a fresh Android Accessibility node; `setText` needs text. See the Accessibility guide for supported actions and freshness. |
| `accessibilityGesture` | `snapshotId`, `gesture`, `durationMilliseconds`; coordinates below | `accessibilityGesture` | `tap` needs `x`, `y`; `swipe` needs `startX`, `startY`, `endX`, `endY`. Coordinates are integer 0..10000; duration is integer 1..10000 ms. |
| `accessibilityGlobalAction` | `action` | `accessibilityGlobalAction` | `back`, `home`, `recents`, `notifications`, `quickSettings`, `lockScreen` or `takeScreenshot`. Android version and service access apply. |

## Volume example

The request's action type is `setMediaVolume`. The policy capability is `mediaVolume`, and result/log records can use that capability name. Do not use `mediaVolume` as an action type.

```json
{"sourceWidgetAddress":"1H1V-example-widget","actions":[{"type":"setMediaVolume","levelPercent":35}]}
```

Replace the address with an actual HTML widget. Pass these arguments to MCP dispatch, add `type: "automationDispatch"` for HTTP/WebSocket, or pass just the actions to the HTML dispatcher.

The corresponding Lua policy includes `"actions":["mediaVolume"]`, and its handler returns:

```lua
return {status="Volume requested",actions={{type="setMediaVolume",levelPercent=35}}}
```

Read `deviceState.mediaVolume` and `mediaVolumeMaximum` before and after changes. The percentage is rounded to a device volume step, not a measured sound level. Fixed-volume devices or a rejected read-back return an explicit refusal. Per-player `audioPlayback.volumePercent` is a separate gain control and does not replace the media-stream setting.

## Widget fields and state

For an HTML target, JSON `patchWidget.values` supports `title`, `html`, `css`, `javascript`, `state`, `itemStates` and `eventSubscriptions`. The JSON parser also accepts `text` for compatible text targets. A single JSON patch accepts at most 6 fields. Target-specific validation, editing locks, configured text/document limits and state limits still apply. Layout is not a widget-patch field. `eventSubscriptions` replaces the complete array, accepts at most 128 entries, and uses the same subscription validation as saved configuration. Read the current list with `widgetGet` before preserving or extending it.

The Lua returned-action parser accepts at most 5 fields per `patchWidget` and does not accept `itemStates` or `eventSubscriptions`. It supports `title`, `text`, `html`, `css`, `javascript` and object `state` subject to the target. To use the JSON action contract from Lua, call ordinary `automationDispatch` through `context.request` with an actual HTML source address, or edit the saved widget record. These command calls have owner authority independently of the returned-action policy.

State patches replace the object rather than merge individual keys. Preserve existing keys when needed. JSON null is a saved value, not key deletion. Lua nil omits fields; use a JSON string through the command bridge when explicit null is needed. `patchDom.values` permits `text`, `html`, `value`, `className` and string-valued `attributes` (at most 16). A hidden/missing target or invalid selector is an explicit outcome. DOM changes disappear on rendering recreation; source or saved state changes persist.

Source edits can recreate the calling WebView. Wait for `dikciz-ready` before further bridge calls. Whole-widget native Delete removes owned files; DOM sections do not have independent native Delete menus.

## Validation bounds

The saved configuration supplies the text, title, component and identifier limits. Bundled defaults are 65536 text characters, 120 title characters, 512 component characters and 80 identifier characters; inspect configGet for the current values. Widget addresses use the configured identifier budget plus page-address overhead.

State/payload validation permits at most eight nesting levels and 128 entries in each object or array. Numbers must be finite. State keys use the identifier grammar: a letter followed by letters, digits, underscores, dots or hyphens. JSON string fields cannot contain NUL. DOM attribute names start with a letter and contain at most 64 ASCII letters, digits, colons, underscores or hyphens. Event names have their own 1..128-character grammar in the scripting tutorial.

Explicit intent action strings are at most 256 characters; media package names 255, media commands 32, notification tokens 36, and Accessibility snapshot/node IDs 128. The Accessibility runtime additionally enforces its 8192-character setText ceiling. Device support and target identity are checked separately from syntactic bounds.

## Authority and verification

Lua returned actions are policy-gated. Enabled Lua `context.request` and HTML command calls have owner command authority subject to Android access; the Lua action allowlist is not a sandbox for those calls. Network calls additionally enforce authentication and Origin policy.

Android acceptance, observed foreground launch, carrier delivery and audible/physical effects are different outcomes. Read `actions` results, script run status and the relevant operation's read-back. A queued trigger or an executed `emitEvent` proves neither the Lua handler nor its widget receiver completed. Use [event replay](event-replay.md) or persisted acknowledgements when reconnecting.

See [scripting](scripting-tutorial.md), [Android access](android-access.md), [Accessibility](automation-control.md#cross-app-android-ui) and the [ordinary command schemas](command-reference.md).
