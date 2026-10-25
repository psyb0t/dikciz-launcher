# Dikciz automation control

Dikciz exposes WebSocket, Streamable HTTP MCP and plain HTTP commands through two listeners. They call the same launcher command target.

Ordinary MCP, WebSocket, HTTP, HTML and enabled Lua command calls share validation, execution and Android permission recovery. Network calls also enforce bearer authentication and browser Origin rules. Enabling a Lua script grants owner command access independently of its returned-action policy. See the [Lua command reference](scripting-tutorial.md#return-device-and-launcher-actions-from-lua). Live `shellStream` and `shellStreamStop` use network transports only; HTML and Lua have no stream delivery channel.

| Interface | Device listener | Lab host listener after `make dikciz-run` |
| --- | --- | --- |
| WebSocket | `ws://127.0.0.1:19001/v1/automation` | `ws://127.0.0.1:19101/v1/automation` |
| Streamable HTTP MCP | `http://127.0.0.1:19002/mcp` | `http://127.0.0.1:19102/mcp` |
| HTTP JSON commands | `http://127.0.0.1:19002/v1/automation` | `http://127.0.0.1:19102/v1/automation` |

Both listeners default to device loopback. Set their listen address and ports through `control/ports.json` or Settings as described in [Control ports](dikciz-configuration.md#control-ports). Use ADB forwarding with loopback binding, or configure a network address for direct private-network access without USB. `make forward` creates generic host-loopback forwards on `19001` and `19002`. `make dikciz-run` owns its separate `19101` and `19102` forwards.

## On-device assistant

`chatControl` and MCP `dikciz_chat` manage the same provider connections, per-conversation model/token settings, queue, event rules and panel used by the native cheese button. Sends report queue state; poll the transcript and actual tool outcomes. The agent discovers and invokes existing MCP handlers in process. See [chat setup, actions and context budgets](embedded-chat.md).

## Direct Android data

Use [native device control](native-device-control.md) for contacts, tracked SMS, calendars, call history, SIMs, clipboard, location, notifications, media sessions and device state. Commands call Android APIs through the shared target. Start with `nativeDataStatus` for resources and grants. MCP resources `dikciz://docs/guides/native-data` and `dikciz://docs/recipes/contact-sms` cover discovery and the SMS workflow.

## Connect without USB

A signed release APK includes both interfaces. Direct access needs Python on the controlling computer or a native Streamable HTTP MCP client, a reachable private network, and the phone's own Android access grants. It does not need Android Lab, an emulator, ADB, root or a USB cable.

### Set up the phone

1. Install Dikciz, select it as Home, and grant shared-storage access so it can read `/sdcard/Dikciz/`.
2. If using Tailscale, connect both devices to the tailnet. Check its access policy for traffic **from the computer running the client to the phone**, on TCP ports `19001` and `19002` or your configured replacements. An allowed connection in the reverse direction is insufficient.
3. In Dikciz, open **Settings**, **Automation**, **Configure remote bearer authentication**. Set a password and enable authentication before choosing a network binding.
4. Open **Configure control ports**. Set **Listen address** to `0.0.0.0`, keep distinct WebSocket and MCP ports, and save. Alternatively, save this complete record at `/sdcard/Dikciz/control/ports.json`:

```json
{
  "version": 1,
  "listenAddress": "0.0.0.0",
  "websocketPort": 19001,
  "mcpPort": 19002
}
```

`0.0.0.0` binds all IPv4 interfaces, including Wi-Fi and a reachable VPN interface. It is not the address to enter in a client. A numeric address assigned to one local interface also works; it must exist when the listener binds. IPv6 bindings use numeric addresses such as `::1` or `::`. Existing files without `listenAddress` keep device loopback. The [configuration reference](dikciz-configuration.md#control-ports) defines validation and fallback behavior.

Endpoint changes restart both listeners automatically, close current connections and invalidate MCP sessions. This also works while another Android app is in front. Reconnect and repeat protocol initialization at the new endpoint. Saving identical endpoints leaves existing connections open.

### Connect from the controlling computer

Replace `phone.example` below with the phone's reachable private hostname or IP. For Tailscale, use the phone's name shown in your tailnet or its Tailscale IP. Run the client on the computer your access policy allows. A build server or another SSH host can have different access rules even when it sees the same online phone.

The Python client reads `DIKCIZ_REMOTE_BEARER_TOKEN` from its environment. Load the password from your secret store or ignored environment file. In an interactive Bash terminal, this prompts without echoing it or putting it in shell history:

```bash
read -r -s -p 'Dikciz bearer password: ' DIKCIZ_REMOTE_BEARER_TOKEN
printf '\n'
export DIKCIZ_REMOTE_BEARER_TOKEN
python3 scripts/dikciz-control.py websocket controlStatus --host phone.example --port 19001 --pretty
python3 scripts/dikciz-control.py mcp-tools --host phone.example --port 19002 --pretty
python3 scripts/dikciz-control.py mcp dikciz_snapshot --host phone.example --port 19002 --pretty
```

Run from a checkout so the client's companion Python modules are available. It uses the Python standard library and handles protocol negotiation itself. Successful discovery should report `listenAddress: "0.0.0.0"` and the configured ports. The MCP snapshot should return the current launcher screen.

Configure a native MCP client with transport **Streamable HTTP**, URL `http://phone.example:19002/mcp`, and an `Authorization: Bearer <password>` header through that client's secret configuration. The WebSocket endpoint is `ws://phone.example:19001/v1/automation`. Use the phone's device ports, not the `19101` and `19102` host ports used by the lab's ADB forwards.

The endpoints do not provide TLS themselves. An encrypted VPN protects traffic over its tunnel; a direct Wi-Fi connection needs a trusted network. A wildcard bind also makes the ports available to reachable Wi-Fi clients. Bearer authentication and network access rules remain separate controls.

### Diagnose a failed connection

| Symptom | Check |
| --- | --- |
| Hostname fails, IP works | Name resolution on the computer running the client. Use the phone's reachable private IP while fixing DNS. |
| TCP connection times out | The phone's binding, client-to-phone access policy, routes and filtering. A Tailscale tunnel ping alone does not prove application reachability. |
| Connection refused | Dikciz is running, both ports are correct, and no other app owns a selected port. Read the listener status and logs on the phone. |
| `401 Unauthorized` | Authentication is enabled and the client has the current bearer password. Network reachability is already working. |
| `403 Forbidden` from a browser | Browser Origin restrictions apply on network bindings too. Use a native client rather than weakening those checks. |
| `GET /mcp` returns `405` | Expected. Use a valid modern or legacy MCP POST; `mcp-tools` checks the connection. |
| MCP tools connect but an action is denied | Check `automationStatus`, the relevant Android permission or special access, and any automation policy required for the action. |

From the controlling computer, `nc -vz phone.example 19001` and `nc -vz phone.example 19002` test TCP reachability when netcat is installed. If debugging through an authorized ADB connection, `adb -s "$SERIAL" shell 'nc -n -z -w 3 127.0.0.1 19001'` checks the listener internally. Repeat for port `19002`, and use the phone's interface IP to distinguish a loopback-only binding. Internal success does not prove another device can connect.

If direct access is unwanted, keep `127.0.0.1` and use [ADB forwarding](../.agents/skills/dikciz-launcher/references/setup.md#forward-the-control-ports). Deleting `control/ports.json` or choosing **Reset to defaults** restores loopback and the default device ports.

## Files, APKs and CLI results

All three APIs support `fileList`, `fileRead`, `fileWrite`, `fileMkdir`, `apkInstall`, `apkInstallStatus` and `shell`. MCP exposes their corresponding `dikciz_file_*`, `dikciz_apk_*` and `dikciz_shell` tools. File commands accept absolute paths anywhere Android permits Dikciz access. Use bounded binary chunks for transfers and poll the Android installer session for its final outcome.

Live command output uses `shellStream` and `shellStreamStop`, with `dikciz_shell_stream` and `dikciz_shell_stream_stop` on MCP. WebSocket pushes correlated events; MCP and HTTP use SSE. Read [streaming commands](files-and-apks.md#stream-logs-and-long-running-commands) for logcat, cancellation, timeouts and client examples.

Plain HTTP accepts POST JSON command envelopes at `/v1/automation` on the MCP listener, with the same bearer and Origin checks. It requires no MCP session and returns the WebSocket result/error envelope. The client supports `http`, `upload` and `download` commands. Read the [file, APK and shell reference](files-and-apks.md) for schemas, limits, examples and installation states.

## Who can connect

The default device-loopback binding accepts local software. An explicitly configured network address also accepts clients that can reach that interface and port. For example, `0.0.0.0` listens on every IPv4 interface. Tailscale access depends on the controlling computer being allowed to reach the phone and the chosen TCP ports in the tailnet access policy. Rules can allow phone-to-computer traffic while denying computer-to-phone traffic.

- Web pages: browsers send an `Origin` header that page scripts cannot change. Both planes refuse every origin except `http(s)://127.0.0.1` and `http(s)://localhost` on any port. A WebSocket upgrade from another origin, including the opaque `null` origin, gets `403 Forbidden`. A page served from a permitted loopback origin can connect; a page served from an external origin cannot.
- Native clients: the Dikciz client, clients reaching a configured network interface or ADB forward, Termux scripts and other apps send no `Origin` and are accepted.
- Installed apps: any app on the phone can open a socket to `127.0.0.1`. While the bearer password is off, that app can run every command Dikciz offers with Dikciz's permissions, including shell commands, intents, SMS and Accessibility input. Turn the bearer password on if you install apps you do not trust.

## Discover the current build

Use the live contract instead of copying command names into a client:

```bash
python3 scripts/dikciz-control.py websocket controlStatus --port 19101 --pretty
python3 scripts/dikciz-control.py websocket automationStatus --port 19101 --pretty
python3 scripts/dikciz-control.py mcp-tools --port 19102 --pretty
```

`controlStatus.transports` reports `websocket`, `mcp` and `http` endpoints with their current `listenAddress`, `port` and `path`. HTTP shares the MCP listener. File edits restart the affected listeners automatically. `controlStatus` lists the protocol version, commands, fields, matching MCP tools, input limits, safe-mode state, and persistent event schema. `automationStatus` lists current Android access, policy vocabulary, event subscription fields, and available sensors. Treat these responses as the source of truth for a running build.

The `automationStatus.deviceAccess` inventory contains ordinary SMS runtime permissions, without a default SMS role or a `role` kind.

The supplied client is dependency-free:

```bash
python3 scripts/dikciz-control.py websocket snapshot --port 19101 --pretty
python3 scripts/dikciz-control.py mcp dikciz_script_logs --port 19102 --pretty
python3 scripts/dikciz-control.py events --port 19101 --count 10
```

`--arguments` accepts JSON or `@path/to/file.json`. The `events` command emits one WebSocket event per JSON line.

## Log levels and app-action records

The public `logging` object controls the minimum level and retention. Native **Settings**, **Configure logging** edits the same values and applies them immediately. Defaults are `info` and seven days. Valid levels are `debug`, `info`, `warn` and `error`; retention is 1 through 90 days. DEBUG includes every emitted severity. Logs rotate by UTC day beneath `/sdcard/Dikciz/logs/`.

`scriptLogs` and `dikciz_script_logs` expose recent bounded records. Each record includes nullable `componentPackage` for an app target and `actionType` for its action. `app_action_completed` with `outcome: "launch_requested"` records acceptance only. The later `activity_launch_observed` record carries the terminal outcome and target package; `launched` means a visible target window was observed. The native viewer shows this metadata too. Older records without a package remain readable with `componentPackage: null`. Successful launches do not generate UI popups; failures still provide visible feedback. Secret redaction applies even at DEBUG.

## Bearer password

Remote access starts open for deliberate local forwarding. On the phone, open **Settings**, then **Automation**, then **Configure remote bearer authentication** to set a password. The phone can replace or clear it without the old password. The public record stores a bcrypt verifier and enabled state at `/sdcard/Dikciz/control/remote-auth.json`, never the plaintext password.

When enabled, WebSocket upgrades and all MCP requests need:

```text
Authorization: Bearer <password>
```

The client reads the value from `DIKCIZ_REMOTE_BEARER_TOKEN` by default. The remote-auth commands inspect, enable and disable the requirement; they cannot retrieve, replace or clear the password. File commands protect the verifier, while independent shell authority has separate limits; see [credential boundaries](android-access.md#credentials-and-privilege-limits). Enabling or disabling it requires the configured password even while the requirement is disabled. An invalid auth record refuses connections; repair or clear it from the phone.

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
| Device | `shell`, `shellStream`, `shellStreamStop`, `intent` | `dikciz_shell`, `dikciz_shell_stream`, `dikciz_shell_stream_stop`, `dikciz_intent`. |
| Device inspection | `deviceInfo`, `packageInfo` | `dikciz_device_info`, `dikciz_package_info`. |
| Launcher recovery | `restart`, `launcherHandoff` | `dikciz_restart`, `dikciz_launcher_handoff`. |
| Visible Android display | `displayScreenshot` | `dikciz_display_screenshot`. |
| Bearer setting | `remoteAuthEnable`, `remoteAuthDisable` | Matching tool listed by `controlStatus`. |

`configReplace` and `configSeed` require one complete assembled `config` object. They validate and save atomically. `reset` restores the bundled home and bundled automation files.

`tap`, `longPress`, and `launchApp` require `semanticId`. `selectPage` needs `pageId`. `addWidget` needs `widgetType`. `widgetMove` and `widgetResize` need `widgetAddress` and `cell`. `gridSet` needs `columns`, `rows`, `gapDp`, and `outerPaddingDp`. `setText` needs `semanticId` and `text`. `automationTrigger` needs `scriptId`. `automationDispatch` needs `sourceWidgetAddress` and `actions`. Read `controlStatus` for all current field and range rules.

`appCatalogue` lists the launchable Android apps with their semantic IDs, human labels, package names, and flattened components, plus the supported app actions. It also reports `usageAvailable`, `usageReason` and `usageWindowDays: 7`. With Android Usage access, each app has `foregroundMilliseconds` over that window; otherwise it is null. The catalogue retains label ordering; clients can rank by foreground time. Reading the catalogue never requests Usage access automatically. Its optional `query` filters case-insensitively on label and package name. `appAction` needs `action` and a flattened `component`. The actions are `launch`, `addShortcut`, `appInfo`, `uninstall`, and `forceStop`. `addShortcut` places an app shortcut tile in the first free grid cell of the selected page, and reports `page_full` when the page has no free cell. `uninstall` and `appInfo` hand off to Android, which runs its own confirmation. `forceStop` needs root and reports `root_unavailable` when `su` is missing or denied. Dikciz never falls back to the app shell for it. Lua action records have no selected-page context, so they must not send `appAction` with `addShortcut`.

Every external activity launch uses one task-isolated launcher, including drawer and shortcut taps, `launchApp`, `appAction`, activity intents, Lua and HTML. Commands that start activities return an `activityLaunches` array. Each record contains `launchId`, `component`, `expectedPackageName`, nullable `observedPackageName`, `accepted`, `verified`, `outcome`, nullable `reason` and `durationMilliseconds`. WebSocket, MCP, HTTP and HTML command replies wait off the Android UI thread for the same observation. `accepted: true` means the Android launch call returned without throwing. It does not prove Android displayed or allowed the target.

A `launched` outcome and `verified: true` require the target package to remain the active visible Accessibility window for at least 300 milliseconds, observed within five seconds of the request. `appAction` uses that terminal outcome for launches, and reports `app_info_opened` only after observing Android Settings; `launchApp.launched` is true only after verification. A target that never appears returns `launch_not_observed`, with `reason: "target_not_foreground"`. A locked phone returns `device_locked`. Missing Accessibility, unresolved targets, interrupted observation and observation-worker timeouts return `launch_unverified` with an explicit reason. `launch_not_observed` means no stable target window was observed within the deadline; it does not prove that the target can never open. No missing observation becomes success, no permission is silently granted, and no failed observation automatically retries the launch. Verification describes the observation interval; an app can still close or crash afterward.

Native taps use the same observer asynchronously and log the terminal `activity_launch_observed` record with a unique `launch_id`. Lua and direct typed activity actions return the same terminal outcome vocabulary. Broadcast dispatch remains an acceptance result because it does not open an activity. Android-owned result flows, including widget binding, keep their own result callbacks.


On the device an app group is made by dropping one app tile onto another, not from the widget picker. Automation still builds one directly: `addWidget` takes `appGroup`, and `configReplace` accepts a group with its `components` listed.

The native app drawer uses this same catalogue and action runtime. It opens from the launcher controls route or an upward swipe that begins on the horizontal page rail. Its own search is case-insensitive on label and package name. Tap a row to launch its app. Use the overflow button or a long press to expose the remaining app actions.

`openAutomationSetup` opens native Automation settings. Its **Set up full device access** button opens the separate **Full device access** task, which the owner can return to through Recents after switching apps or reviewing an Android panel. The checklist refreshes from actual grants on resume.

While that screen is resumed, `snapshot.screen` is `deviceAccess`. Semantic snapshots, `find`, `tap`, `scrollBy`, `scrollTo`, UI dumps and launcher screenshots use its views through WebSocket, MCP and HTTP. Stable controls are `settings:device-access:next`, `settings:device-access:close`, `settings:device-access:progress` and `settings:device-access:scroll`. Each requirement has a `settings:device-access:entry:<requirementId>` label; missing requirements also have a `settings:device-access:setup:<requirementId>` button. Closing or leaving setup removes its controls from the current semantic target. Targeted remote permission reviews continue to request only their selected capability.

`shell` uses `/system/bin/sh -c` when `root: false`. When `root` is absent or true, Dikciz first checks `su -c id -u`. Only exactly `0` allows the requested command to run and report `executionScope: "root"`. A missing, denied, malformed, or timed-out probe returns WebSocket error `root_unavailable` or an MCP tool error. Dikciz never relabels or falls back to the app shell. Optional `timeoutMilliseconds` and `maxOutputBytes` set the command timeout and captured output budget. Defaults are 30000 milliseconds and 65536 bytes. MCP schemas and `controlStatus.limits` advertise their ranges and defaults. The [shell reference](files-and-apks.md#execute-android-cli-commands) includes examples and client wait behavior. Successful results include `executionScope`, `exitCode`, `output`, and `outputTruncated`. `intent` supports `activity` and `broadcast`. Android decides whether the requested component can run.

`automationTrigger` queues one `manual` event for an enabled script with an enabled manual subscription. Acceptance means the event was queued. Read script status and logs to see whether its actions completed.

`htmlWidgetRendererCrash` and `dikciz_html_widget_renderer_crash` take a rendered HTML `widgetAddress`. They call Android WebView's documented renderer crash URL for that widget so a developer can verify renderer-loss recovery. The immediate result is `crash_requested`. Read `scriptLogs` or `dikciz_script_logs` for `html_widget_renderer_gone` followed by `html_widget_renderer_recovered`. Android can place several WebViews in one renderer process, so it can report loss for more than one widget. Dikciz recreates each reported widget in place. It does not rebuild the selected page or write configuration.

Read [remote device operations](remote-device-control.md) for build identification, acknowledged restart, listener recovery, full-display screenshots and Android privilege boundaries. `restart` writes its reply before recreating the launcher and listeners. Reconnect and verify a changed `deviceInfo.application.instanceId`; do not kill the process through its own shell.

## MCP protocol modes

Connect a Streamable HTTP client to `/mcp`. Modern protocol 2026-07-28 supports stateless server/discover, tool and resource requests with per-request metadata and mirrored HTTP method/name headers. Legacy clients negotiate an initialized session with protocol 2025-11-25, 2025-06-18 or 2025-03-26; their calls do not require modern metadata. The supplied client manages its own connection. See the [complete protocol contract](mcp-protocol.md).

JSON is the ordinary response format. Shell streaming returns SSE for that tool call. GET returns 405; WebSocket provides persistent launcher events. Authentication, Origin validation, schemas and Android access apply in both MCP modes.

## Cross-app Android UI

Enable **Dikciz cross-app automation** in Android's Accessibility settings before requesting a cross-app tree. The Automation access sheet opens that system panel. Dikciz can report the missing access and open the panel, but Android requires the phone owner to turn the service on. Neither a WebSocket or MCP client nor a Lua or HTML widget can enable it.

`accessibilitySnapshot` and `dikciz_accessibility_snapshot` return the active Android window as one bounded document:

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

Each node has a generated path ID such as `node.2.1`, nullable `text`, `contentDescription`, `viewIdResourceName`, finite supported `actions`, state, and screen bounds. A child that contains a visible label can have no action of its own. Walk its generated parent path to find the enclosing clickable row. The snapshot contains at most 256 nodes, descends at most 16 levels, and bounds copied text fields to 512 characters. Dikciz retains at most 32 live snapshot descriptors. The next snapshot request returns `accessibility_snapshot_limit` until a five-second descriptor expires.

`accessibilityAction` and `dikciz_accessibility_action` require `snapshotId`, `nodeId`, and one action: `click`, `longClick`, `focus`, `clearFocus`, `scrollForward`, `scrollBackward`, `setText`, or `select`. `setText` also requires `text`, with a maximum of 8,192 characters. The snapshot ID lasts five seconds. Before acting, Dikciz resolves the current root and node again. It rejects expired or changed windows, including a visible update elsewhere in the captured tree, instead of trusting a retained Android node. An expired descriptor returns `accessibility_snapshot_expired`; a descriptor that still exists but no longer matches its active window returns `accessibility_snapshot_stale`. An accepted action reports `executed`; Android can still return `android_rejected`. If the Accessibility service is unavailable, `accessibility_not_enabled` takes precedence over a retained descriptor's expiry. That result tells an unattended script to report the access problem instead of a stale local snapshot.

A rejected action from an enabled automation script writes the normal action and script-failure records to `/sdcard/Dikciz/logs/`. WebSocket `scriptLogs` and MCP `dikciz_script_logs` return the same bounded projection. When `logging.notifyOnScriptError` is enabled and Android allows notifications, the existing per-script quiet period also applies to rejected actions. A direct WebSocket or MCP action returns its exact synchronous error without creating a background script-error notification.

The focused `make dikciz-test-accessibility-third-party` target installs a separate local Android fixture and proves both remote transports act on its visible native button. It also proves that an update anywhere in the captured fixture tree invalidates an earlier descriptor before another action can report success. A physical-phone run can prove owner-state parity after a clean current install, but direct cross-app actions remain unavailable until the owner enables Accessibility in Android settings.

`automationDispatch` and `dikciz_automation_dispatch` also accept two typed cross-app actions. `accessibilityGesture` requires a current `snapshotId`, a `gesture`, and `durationMilliseconds`. A `tap` supplies integral `x` and `y`. A `swipe` supplies integral `startX`, `startY`, `endX`, and `endY`. Coordinates must be visible screen coordinates. Duration is one through 10,000 milliseconds. Dikciz rechecks the snapshot root before asking Android to run the gesture, then returns `executed`, `gesture_cancelled`, `gesture_timeout`, or `android_rejected`.

`accessibilityGlobalAction` takes one `action`: `back`, `home`, `recents`, `notifications`, `quickSettings`, `lockScreen`, or `takeScreenshot`. Android decides whether a particular global action is currently available. Dikciz returns its actual result. Accessibility does not provide arbitrary hardware key injection.

The commands return exact errors for disabled access, unavailable windows, unknown, expired, stale, or over-limit snapshots, missing nodes, unsupported actions, and invalid text. `automationStatus.androidAccess.accessibility` and the `deviceAccess` record show the actual owner-controlled state. The two transports return the same status document.

The Accessibility service publishes `accessibilityWindow` into the automation event bus. Its bounded payload contains `packageName`, `className`, `eventType`, and `windowId`. It also publishes `accessibilityServiceState` with a fixed `state` of `connected`, `interrupted`, or `disconnected`, plus the current `enabled` Boolean. A policy needs the `accessibility` capability to subscribe to either event. Lifecycle events do not contain an Android window snapshot.

Lua receives `context.androidAccess` on every event. It is a read-only copy of the `automationStatus.androidAccess` map, so a script can report an owner-controlled missing capability before it tries an action. During an `accessibilityWindow` event, Lua also receives the bounded active-window document as `context.accessibility`. It contains `snapshotId` and `nodes`, not a retained Android object. Return an action in the normal event result:

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

Lua can return the same gesture and global-action records. For a coordinate tap, derive integer coordinates from a visible node's `bounds` in the copied snapshot. HTML does the same through `dikciz.dispatch({ actions: [...] })`:

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

An HTML widget first calls `await dikciz.command("accessibilitySnapshot")`, then sends the same action record through `dikciz.dispatch({ actions: [...] })`. HTML and network dispatch share the JSON action parser; Lua returned actions have their own table parser. Both feed the same live node re-resolution, action runtime, outcome log and Android access checks. The [typed action reference](typed-actions.md) documents field and policy differences. A stale or invalid direct action returns its exact accessibility outcome in the action result. It does not retain a node or click a replacement control.

## Events and semantic UI

Persistent WebSocket events include `automation_command_completed`, `automation_ui_rendered`, `html_widget_event`, and `automation_event`. `automation_ui_rendered` reports the selected page after an immediate native page selection. `automation_event.fields.event` is the same copied automation event document that scripts and HTML widgets receive: `type`, `source`, `timestampMilliseconds`, `coalescingKey`, and `payload`. Dikciz continues publishing it while its native Activity is behind another Android screen, as long as the control plane remains running. MCP has no persistent launcher-event subscription. Its shell tool can stream SSE; ordinary event-journal commands provide bounded cursor replay and long polling through every binding. Use `automationStatus`, configuration and script-log tools to verify consumers. `controlStatus` describes current event fields.

Read a fresh `snapshot` after any native screen change. Dialog, picker, provider-child, resize, and page-menu semantic IDs can disappear immediately.

`longPress` on `page:navigation:horizontal:edge:before`, `:edge:after`, or the matching `page:navigation:vertical:` IDs creates one saved, selected, empty page at that rail's far end. There is no provisional page and no cancel control to tap afterwards. The page menu carries `page:menu:<pageId>:move-left`, `:move-right`, `:move-up`, and `:move-down`. Each reports `enabled` false when no page exists at the neighbouring coordinate, and tapping a disabled one writes nothing. A successful reorder logs `page_moved` with `page_direction`, `page_column`, and `page_row`; a refused one logs `page_move_rejected`.

The bottom chrome carries two icon-only buttons. `launcher:controls` opens the launcher routes. `launcher:page-search` opens the page jump list, which exposes `launcher:page-search:query` for `setText`, one `launcher:page-search:page:<pageId>` button per match, and `launcher:page-search:empty` when nothing matches. A query matches a case-insensitive substring of either the page title or its `1H1V` address, and an empty query lists every page. Tapping an entry performs the same selection as `selectPage`. Opening the sheet logs `page_search_opened` with `page_count`. Dikciz never logs the query text or the page titles.

A snapshot contains a stable `semanticId`, role, enabled and checked state, clickability, visibility, bounds, text, content description, and an Android resource ID where one exists. Use semantic IDs for automation. Do not derive coordinate taps from text. Provider child IDs include an opaque render token and expire after provider updates, page changes, and removals.

While the cold-start brand covers the screen, the snapshot includes `startup:dismiss`, which a `tap` skips, `startup:message`, whose text is the current line, and `startup:version`, whose text identifies the running version and build type. All disappear when the brand does. Until then a physical touch only dismisses the brand.

A snapshot also reports the page grid. `grid` carries `columns`, `rows`, `gapDp`, and `outerPaddingDp`. Every entry in `widgetReferences` carries its logical `cell` with `column`, `row`, `columnSpan`, and `rowSpan`, plus the rendered screen `bounds` when that widget is on the selected page, or null when it is not. Only the cell is persistent; the bounds are render-time values.

`homeGet` returns pages keyed by page ID, one-based page address such as `1H2V`, and the `home` alias. Each page contains widgets by simple ID and full address. That is also the map exposed to HTML widgets.

The current page snapshot includes `scroll.y`, `scroll.extent`, and `scroll.range`. They report the actual native page scroll position and bounds, not a simulated value. `scrollBy` accepts integral `deltaY`. `scrollTo` accepts a current semantic ID.

A native page is one fixed viewport and does not grow, so on a normal home `scroll.range` equals `scroll.extent` and both commands are no-ops. Scrolling belongs to an HTML widget's own document or to an Android provider that owns its scrolling, never to native placement.

## Placement commands

`addWidget` creates one top-level item on the selected page. It takes only `widgetType`, one of `html`, `appGroup`, or `scriptDashboard`, and always places the item in the first fitting free cells, reading left to right and then down. There is no cell argument: placement is not chosen at insertion. Use `widgetMove` and `widgetResize` afterwards to place it.

App shortcuts keep the existing `appAction` route with `addShortcut`, and Android provider widgets keep the native picker because Android owns their binding step.

`widgetMove` and `widgetResize` take a `widgetAddress` and an explicit `cell` with `column`, `row`, `columnSpan`, and `rowSpan`. A chosen rectangle is accepted or rejected, never silently relocated.

```bash
python3 scripts/dikciz-control.py websocket addWidget \
  --arguments '{"widgetType":"scriptDashboard"}' --port 19101 --pretty
python3 scripts/dikciz-control.py websocket widgetMove \
  --arguments '{"widgetAddress":"1H1V-device-card","cell":{"column":2,"row":0,"columnSpan":2,"rowSpan":1}}' \
  --port 19101 --pretty
```

All three return the widget document, including its saved `cell`. A rejection returns one of the finite placement failures, writes nothing to the public tree, and emits one bounded diagnostic:

| Code | Meaning |
| --- | --- |
| `page_full` | The page has no free cell for any further item. |
| `grid_collision` | The requested cells are already used. |
| `grid_bounds` | The rectangle leaves the configured grid. |

`controlStatus` publishes the same finite list as `placementFailures`. The WebSocket error envelope carries the value in `code`. The matching MCP tools `dikciz_add_widget`, `dikciz_widget_move`, and `dikciz_widget_resize` return `isError` with the same value in `structuredContent.code`, so both planes report one taxonomy.

## Page grid

`gridSet` replaces the grid used by every page. It takes `columns`, `rows`, `gapDp`, and `outerPaddingDp`, and returns the saved grid. The MCP form is `dikciz_grid_set`.

Grid changes reflow widgets across every page before saving. Full-width HTML shrinks with the column count. Collisions relocate to the first fitting free cells; items that cannot fit are evicted in saved order. Apps remain installed when their shortcuts are evicted. The result includes `evictedWidgets`, an array of `{"pageId":"home","id":"shortcut-id"}` records, empty when nothing was removed. Widget removal also cleans up its scoped state. Settings and all command bindings use this same procedure.

```bash
python3 scripts/dikciz-control.py websocket gridSet \
  --arguments '{"columns":4,"rows":6,"gapDp":8,"outerPaddingDp":12}' --port 19101 --pretty
```

The same four fields are editable in the launcher under **Settings**, in the **Page grid** section.

## HTML widget JavaScript

A placed HTML widget receives `window.dikciz`, `selfWidget`, and `dikciz-ready` once its native message channel connects.

```javascript
window.addEventListener("dikciz-ready", async () => {
    const result = await dikciz.command("diagnostics");
    document.querySelector("#status").textContent = Object.keys(result).length;
});
```

`dikciz.request(document, options)` returns the complete command result/error envelope. `dikciz.command(type, fields, options)` returns its result or rejects with an error retaining `code`, `message`, `requestId`, `revision`, `permissionRequest` and the raw `response`. Both accept `options.responseTimeoutMilliseconds`; the default is at least 60 seconds or the operation timeout plus five seconds, and zero disables the response timer. A response timeout does not cancel dispatched work. `dikciz.dispatch({ actions })` uses the same typed action executor as Lua. `dikciz.fitContent()` asks the native frame to measure the current DOM.

`diagnostics` includes `htmlWidgetResources`: active renderer count, total UTF-8 bytes for generated documents on the selected page, and the configured per-page renderer and document-byte limits. These are launcher-owned resource inputs, not a claim about opaque Chromium process memory.

`selfWidget` and `dikciz.selfWidget` expose the current ID, page, full address, logical `cell`, state, and `get()`, `patch(values)`, and `patchState(values)` helpers. A widget document reports `cell`, never device pixels, so a widget author never persists a coordinate that rotation would invalidate. `dikciz.pages()` returns pages by ID, page address, and `home`. `await dikciz.home()` and `await dikciz.widget(address)` read current records after a configuration change.

Use `await dikciz.patchWidget(address, values)` for a full named-widget patch, or `await dikciz.patchWidgetState(address, values)` for its persisted state. Addresses are human-readable, for example `2H1V-weather-card`. A target on the current page receives `dikciz-state` without a document reload. A target on a different page is not treated as live DOM. Its saved state is available through `selfWidget.state` when that page is selected and its widget renders.

State patches keep the current document alive. Listen for `dikciz-state`:

```javascript
window.addEventListener("dikciz-state", event => {
    document.querySelector("#status").textContent = event.detail.status;
});
```

`dikciz.onEvent(listener)` receives matching native and custom events while the widget is rendered. It returns an unsubscribe function. The same document is sent as a `dikciz-event` window event. Select events in the widget's `eventSubscriptions` JSON field.

Publish a custom event with:

```javascript
await dikciz.emitEvent("weather.refresh.completed", { updatedAt: Date.now() });
```

Custom names are 1 through 128 characters, start with a letter, and use letters, digits, `.`, `_`, `:`, or `-`. Lua uses its matching `emitEvent` action.

The direct action vocabulary is `accessibilityAction`, `accessibilityGesture`, `accessibilityGlobalAction`, `selectPage`, `patchWidget`, `patchState`, `patchDom`, `launchApp`, `appAction`, `postNotification`, `sendSms`, `explicitIntent`, `emitEvent`, `mediaControl`, `setMediaVolume`, `notificationControl`, and `lockDevice`. `appAction` takes an `action` and a `component`. A script action has no selected page, so it rejects `addShortcut`. `sendSms` takes a dialable `recipient` and a `body`, and needs the `SEND_SMS` permission that `automationStatus` reports as `smsSend`. Android can save messages sent by non-default SMS apps into its SMS provider. This legacy typed action confirms submission only; use tracked `smsSend` and `smsStatus` for duplicate protection and sent/delivery evidence. `accessibilityAction` requires the current `snapshotId`, a `nodeId`, and one supported `action`; `setText` also requires `text`. Current policy and Android access decide whether an action can complete.

`patchDom` takes `widgetAddress`, a CSS `selector`, and a `values` object containing one or more of `text`, `html`, `value`, `className`, or `attributes`. It changes only a currently rendered HTML document and does not persist source or state. A target that is not rendered returns `target_not_rendered`.

For a complete event, Lua, HTML bridge, state, and action walkthrough, read the [scripting tutorial](scripting-tutorial.md).

`lockDevice` returns `device_admin_inactive` when Android Device Administration is not active. It does not open Android Settings. Open **Settings**, then **Automation**, then **Set up full device access** to request that access.

The native bridge holds at most 32 waiting requests and accepts at most one MiB of UTF-8 JSON per request. The supplied JavaScript API separately allows at most 32 outstanding promises, so ordinary `dikciz.command()` calls are backpressured before native saturation. Raw channel users can still receive `busy` when the native queue is full. Every rejected HTML command is logged as `web_command_rejected` with the HTML widget ID, HTML kind, and error reason, and is available through both log transports. A state patch does not reload the WebView. Replacing HTML, CSS, JavaScript, or the whole configuration can destroy the calling document. Lifecycle commands acknowledge their response after promise handlers run, before destroying that document.

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

Both smoke targets probe the shared Dikciz forward that `make dikciz-run` creates, so they default to host ports `19101` and `19102`. The default device ports are `19001` and `19002`, unless changed through the phone's control-port settings. The smoke probes also verify the expected device ports. When using custom ports, set `ANDROID_LAB_FORWARD_DEVICE_PORT` and `ANDROID_LAB_MCP_DEVICE_PORT` to match. Point them at another forward by overriding the host ports:

```bash
make dikciz-automation-smoke ANDROID_LAB_FORWARD_HOST_PORT=19001 ANDROID_LAB_MCP_HOST_PORT=19002
```

The mutation checks restore the original configuration. Use a focused `make dikciz-test-*` target for a particular UI or Android behavior.

The [client and script map](../scripts/README.md), [live-test map](../tests/README.md), and [Dikciz source map](../app/src/dikciz/README.md) describe the implementations behind these commands.

## MCP documentation resources

MCP advertises packaged guides and recipes through `resources/list` and `resources/read`. Start at `dikciz://docs/index`, follow discovery cursors and fetch only the sections needed. Each page lists at most eight entries; each section contains at most 4096 UTF-8 bytes. The [resource guide](mcp-resources.md) owns the protocol, CLI examples and access contract. Tools remain the executable command interface.

## Request missing Android access

Recognized permission failures retain their error code and include `permissionRequest`. Ordinary commands accept the optional Boolean `autoRequestPermissions`, default `false`. Without opt-in, `state` is `permission_required` and no notification or dialog is created. With `autoRequestPermissions: true`, Dikciz queues owner review. This option is available on WebSocket, MCP, HTTP and HTML bridge commands. WebSocket and HTTP put it in the error envelope; MCP puts it in `structuredContent` and its text instructions. Direct action results include it beside the denied action. Policy refusals and protected private files do not trigger prompts.

Use `requestDeviceAccess` on WebSocket or HTTP, or `dikciz_request_device_access` on MCP, with `requirementId` from `automationStatus.deviceAccess.requirements`. For Accessibility, pass `{"requirementId":"accessibility"}`. This explicit command starts owner review without attempting another action. Storage is `sharedStorage`, location is `locationForeground` or `locationBackground`, the Home selector is `homeApp`, and unknown-source installation is `installPackages`. Arbitrary permission names, messages and intents are rejected.

A missing grant returns `state: "user_action_required"`, `requirementId`, `notificationPosted`, `deduplicated`, `retryRequired`, `autoRequested`, `permissionName`, `packageName`, `promptState` and `instructions`. The phone receives an **Allow access** notification and a foreground consent dialog. If notifications are blocked, open Dikciz to see the pending request. Same-access requests are deduplicated for 30 seconds. If the owner closed the review, another request during that cooldown returns `state: "request_dismissed"` and `promptState: "dismissed"`, without claiming delivery or opening another window.

Tell the owner to check the phone and review the named access. **Allow access** opens the Android permission or special-access screen. The owner chooses whether to grant it. Background location requires foreground location first. Granted, unavailable and on-demand capabilities cause no prompt. This flow cannot grant root to an unrooted phone. After a grant, read fresh status and explicitly retry the authorized operation. For a batch, inspect each action outcome and retry only the denied actions, because earlier actions may already have completed. Nothing runs automatically.

Consent controls expose `permission:allow` and `permission:dismiss`. A notification is not proof of a grant. Shell permission failures remain ordinary exit status and output; clients can identify the needed capability and call `requestDeviceAccess` explicitly.

### HTML document controls

An HTML widget is one programmable WebView document. Long press its content, background or `widget:<widget-id>:move-handle` to open the same whole-widget menu. Edit opens the saved HTML, CSS, JavaScript and state using `widget:<widget-id>:editor:<field>` controls. Page and widget locks apply. There are no `html-item:*`, `add-block` or card-preset controls. Whole-widget Delete removes the complete document and its owned files.

To edit only part of a document programmatically, read its saved source, change the intended markup or rendering code, and submit the widget update through the shared command path. Preserve unrelated source and state. DOM patch actions remain available for live changes; durable changes belong in saved source or state.

`deviceState` also returns battery temperature in tenths Celsius, voltage in millivolts and a `system` object. `memory` contains total, available and used bytes and low-memory state. `storage` identifies the app-data filesystem and reports total, free and available bytes. `cpu` reports available logical processors, system percentage when readable, and process-only `appPercentOfOneCore` plus `appPercentOfCapacity`. One-core percent can exceed 100 with parallel work; capacity percent divides by available processors. `appScope` is `dikciz_process`, excluding other apps and WebView renderer processes. `appSampleWindowMilliseconds` describes the interval; samples need at least 250 milliseconds. `cores` has at most 64 records with ID, nullable online state, current/maximum frequency in kilohertz, and nullable usage percent. Frequencies are clocks, not utilization estimates. Protected system counters report `reason: android_restricted` and `permissionRequestAvailable: false`; ordinary Android permissions cannot unlock `/proc/stat`. Core inventory source and truncation are explicit. `sensors` contains total count, at most 64 hardware records and a truncation flag. It explicitly identifies this as inventory without live readings. All entrypoints use the same native executor.

The `intent` command also accepts optional `data`, an absolute Android intent URI of at most 2048 characters without control characters. Browser search cards use `intentType: "activity"`, `action: "android.intent.action.VIEW"` and an encoded HTTPS search URL in `data`. URI dispatch uses the same activity-launch observer and Android permissions across all entrypoints. An installed app must handle the action and URI; dispatch acceptance is separate from verified foreground launch.

HTML widget `state` and `itemStates` are assembled from page/widget-owned state files. `automationDispatch` with `patchWidget` accepts `values.itemStates`, a map of instance ID to state object; merge the current map when updating just one instance. `widgetGet` returns `itemStates`, `eventSubscriptions` and the wrapper `storagePath`. JSON `patchWidget` can replace `eventSubscriptions` with a strictly validated array of at most 128 entries; preserve existing entries when extending it. HTML offers `selfWidget.itemFor(element).patchState(values)` for a queued update that preserves siblings. The same validator and state store serve Lua, HTML, WebSocket, MCP and HTTP. Removing an optional namespace marker and state entry in a source update removes its owned files; native widget deletion removes the complete document and all its state files. See [instance ownership](dikciz-configuration.md#html-instance-state-and-files).

### App icons

`appIcon` accepts a launchable `component` from `appCatalogue` and optional `sizePixels` from 16 through 128, default 64. It returns `component`, `mimeType: "image/png"`, `width`, `height` and `pngBase64`. Missing launchable activities return `native_record_not_found`; malformed components and sizes fail validation. Icons come from Android package resources. No APK extraction or storage permission is needed.

HTML uses `await dikciz.command("appIcon", {component: app.component})`, then sets an image's `src` to `"data:image/png;base64," + result.pngBase64`. Request only visible app icons and cache them within the document. WebSocket and HTTP use the same command; MCP exposes `dikciz_app_icon`.

### Hardware capture and live readings

Hardware sensor snapshots, current location, camera capture, microphone recording and speech are shared native commands across network, HTML and Lua bindings. See [hardware commands](native-device-control.md#sensors-camera-microphone-and-speech) for schemas, capture paths and Android restrictions.

HTML receives `dikciz-visibility` with `{visible}` when its native page or window visibility changes. `selfWidget.visible` exposes the current value. Use this for foreground work because neighboring pages can be preloaded before selection.

### Speech and additional hardware commands

[Speech recognition](speech-recognition.md) documents availability, modes, language, deadlines, cancellation, results and Android errors. [Hardware controls](hardware-controls.md) covers torch, vibration, settings read-back, Bluetooth, USB, NFC, Wi-Fi, audio and camera discovery. These use the same ordinary command path in HTTP, WebSocket, MCP, HTML and Lua. The [command reference](command-reference.md) lists discovered schemas.

[Shell sessions](shell-sessions.md) provide incremental output through every ordinary binding, including HTML and Lua, with reconnectable cursor reads and explicit history gaps. [Speech models](speech-models.md), [audio sessions](audio-playback.md), [durable jobs](command-jobs.md), [notification management](notification-management.md) and [peripheral guides](index.md) describe the additional procedures. JSON state patches preserve nullable API results using the same saved-state format.

Embedded chat supports per-conversation `settings:{"readOnly":true}`. Discovery hides mutating tools and the trusted executor flag blocks direct writes and permission prompts with `read_only_violation`. Tool arguments cannot disable it. Chat history and logs still save; other owner entrypoints retain their configured access. See the embedded chat guide for the full contract.
