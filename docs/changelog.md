# Changelog

This history includes the releases made while Dikciz and Android Lab shared one repository.

## [Unreleased]

## [0.27.0] - 2026-10-23

### Added

- Three redesigned starter pages with clock/weather, real app icons and usage-based ranking, a native bottom dock, agenda, voice notes, and a visual battery/CPU dashboard. Reusable documents include reduced-motion and hidden-page behavior.
- Bounded per-core CPU inventory, readable clock/load samples and normalized process CPU metadata across the shared command target. Protected system counters remain explicitly unknown.
- Optional seven-day foreground-use metadata in appCatalogue and native timed torch off requests, shared across entrypoints. Home quick actions demonstrate owner-started torch pulses and speech.

### Fixed

- Honor explicit padding on unframed native app shortcuts, including the bottom dock.
- Preserve voice-note stop requests that arrive during a microphone status poll.
- List complete HTML templates directly in the Dikciz widget picker with searchable descriptions. Empty HTML widget inserts a blank document without another menu; saved templates and package previews remain available.

### Documentation

- Replace the starter screenshots with configured Home, Tools and Device pages showing live weather, sample agenda entries and a saved recording.

### Migration

Customized homes and existing library packages are preserved. Reset Dikciz home to try the new layout. The dock uses installed apps; it omits missing handlers. Usage ranking needs Android Usage access, and torch/speech demonstrations start only after a tap.

## [0.26.0] - 2026-10-23

### Added

- Four independent half-page starter WebViews: time/weather and agenda on Home, voice notes and device status on Tools. Each is a complete reusable HTML/CSS/JavaScript library template with separate state.
- Shared read-only calendar occurrence queries for the current seven-day window, with Android recurrence expansion and pagination, plus the next alarm timestamp in device state.
- Tap-operated voice recording with stop/save, widget-owned audio, recent playback and save recovery. Existing homes remain unchanged on APK update.

### Documentation

- Correct typed action names and policy mappings, HTML whole-document editing, credential-file restrictions, event replay and process-versus-activity lifecycle guidance.
- Add the complete typed-action and Android access references, including NFC payment-card protocol and session limits. Package feature access and all typed-action fields as four additional short MCP resources.

### Migration

Fresh installations and Reset Dikciz home now use Home and Tools with four independent HTML widgets. Existing saved homes remain unchanged on update. Add the new templates individually or reset the home to use the demo layout. Saved voice recordings belong to their widget and are deleted with it; copy recordings elsewhere before removing that widget. Calendar and microphone actions require Android access.

## [0.25.0] - 2026-10-23

### Fixed

- Grid dimension changes resize widgets to fit, relocate collisions and evict overflow items instead of refusing smaller grids. Full-width HTML follows the column count; all control bindings share the procedure and report evictions.
- Shared state patches accept JSON null consistently with saved widget and script state, including nullable API results.

### Added

- Live microphone PCM streaming with a dedicated Android foreground service, owner Stop action, bounded reconnectable replay, shared microphone ownership and explicit lifecycle/cleanup results.
- Native one-shot USSD requests with voice-SIM selection, shared phone-access recovery, configurable response/observation budgets and explicit uncancellable network outcomes.
- Bounded foreground camera MP4 recording with optional microphone AAC, discovered sizes/FPS ranges, shared hardware ownership, finalized-media inspection and cancellation cleanup.
- Replayable process-owned automation events with epoch-bound cursors, filters, long polling, adjustable retention, explicit loss and current Android source-access checks.
- Bounded torch-state callback observation, finite vibration waveforms and predefined effects with native-support discovery and strict action-specific validation.
- Configurable JPEG quality, orientation, sensor crop zoom, exposure compensation and autofocus, with actual Android completion metadata and device-range validation.
- Process-owned shell sessions with incremental cursor reads across every ordinary binding, bounded replay history, explicit gaps, cancellation, retention and release controls.
- Recognition language inventory and model-download requests with provider progress, scheduled/available distinctions, bounded observation and status.
- Requested Wi-Fi scans with explicit throttling/failure, bounded completion, cleanup and per-result elapsed-time freshness.
- Managed microphone recording with explicit start/status/stop/cancel, shared hardware ownership, input preference, encoding options and automatic duration/file limits.
- Local audio playback sessions with asynchronous status, pause/resume/seek/stop, volume, audio focus, output preference and expiry.
- NFC tag reading, NDEF writing and raw technology transactions with optional tag selection; Bluetooth BLE/classic discovery, pairing requests, bounded RFCOMM and GATT discovery, attributes and observations.
- Shared notification dismissal, snooze and inline reply, file lifecycle/search operations with protected credential paths, durable asynchronous command jobs, USB owner access and control/bulk transfers, saved speech audio, geocoding and GNSS status.
- Standard modern stateless MCP discovery and legacy protocol negotiation, with mirrored-header validation and focused interoperability tests.
- Speech voice discovery, voice/language/rate/pitch selection and output cancellation.
- Shared microphone speech recognition with on-device/system modes, provider discovery, language selection, deadlines, alternatives/confidence and cancellation.
- Torch and vibration controls, allowlisted system-setting read/write/read-back, camera capability discovery and Bluetooth, USB, NFC, Wi-Fi and audio inventories.
- Claude Code and Codex plugin manifests, Glama maintainer metadata and focused agent connection documentation for owner-supplied phone endpoints.
- A documentation index, capability coverage table, complete discovered command reference and focused speech/hardware MCP guides.

### Migration

Updates preserve existing home files. Changing grid dimensions now reflows widgets and removes overflow items in saved order; back up crowded pages before shrinking them. Apps remain installed when their shortcuts are removed. New device operations require the corresponding Android access and actual hardware. Microphone streaming starts while Dikciz is foreground and uses process-owned replay, which is lost on app process death or APK replacement. Cellular call audio remains platform-restricted.

## [0.24.0] - 2026-10-22

### Added

- Shared hardware sensor samples, camera discovery and JPEG capture, microphone recording, device speech and explicit cellular call-audio capability reporting through HTTP, WebSocket, MCP, HTML and Lua.
- A bundled Sensors page and reusable complete Live sensors WebView template with live readings and per-sensor unavailable states.

### Fixed

- Home reset restores bundled validation limits so older text limits cannot reject bundled widget documents.
- HTML exposes native page and window visibility so preloaded pages can defer permission requests and pause live work while hidden.
- Weather uses permitted phone location when no city is saved, with bounded fresh-fix lookup and manual search fallback. Saved manual cities keep precedence.

### Changed

- Tag pipelines wait for the same commit's successful main push pipeline before publishing the signed APK and launcher skill. Build, lint and tooling tests run on main and ready pull requests without repeating on tags. Release badges update after APK publication.

### Migration

Updates preserve edited homes. Use Reset Dikciz home to load the three-page starter, or add the Live sensors template to an existing page. Camera and microphone capture require Android grants and foreground eligibility. Cellular call recording and audio injection remain unavailable without privileged system integration.

## [0.23.0] - 2026-10-22

### Fixed

- New app shortcuts finish insertion without a stuck selection outline. Move and explicit resize controls remain available.
- Widget drops use the finger position at release, so delayed move events cannot leave a shortcut in the preceding cell.
- Page content no longer adds a second grid outer inset beside the right and bottom pager rails. Widget style margins remain configurable.

### Changed

- New and reset homes use an 8-column, 6-row grid with full-width Home and Insights widgets. Complete dashboard templates also span eight columns. Existing explicit grid dimensions remain unchanged on update.

### Migration

Updates preserve edited homes and their saved grid sizes. Choose 8 columns and 6 rows in grid settings to change an existing layout, or use Reset Dikciz home to replace its pages and grid with the bundled starter.

## [0.22.1] - 2026-10-22

### Fixed

- The pipeline accepts manual runs on a selected branch or tag. Selecting a release tag runs its build checks and release jobs.

## [0.22.0] - 2026-10-22

### Fixed

- Intent dispatch accepts requests without the optional data URI, including broadcasts, through every entrypoint.

### Changed

- HTML widgets are complete programmable documents. Content and background long presses open the same whole-widget controls, and Edit exposes HTML, CSS, JavaScript and JSON state. Removed native card menus, card preset objects and fragment insertion. Existing documents and saved state remain intact.
- The HTML library adds complete System dashboard and BTC and economic news templates. The system template labels Android-restricted CPU data and sensor inventory; the market template keeps dated results and refreshes external data explicitly.
- The shared device-state command reports RAM, storage, CPU samples, battery temperature and voltage, and bounded sensor inventory through all entrypoints.
- HTML app lists display Android icons through the shared `appIcon` command and MCP tool. The hint now distinguishes app launching from native drawer shortcuts.

### Migration

Automation using `html-item:*` or `add-block` UI controls must edit the containing document through its widget editor or saved source. Native Delete removes the whole WebView. Existing custom files and optional item-state APIs remain available. Old fragment-library directories are left on disk and are no longer loaded.

## [0.21.1] - 2026-10-22

### Fixed

- Long pressing an HTML document background or empty container padding opens its widget controls instead of a missing-item message. Saved cards retain their scoped controls; runtime-only targets remain protected.

### Changed

- HTML widgets hide native and nested HTML scrollbars while preserving scrolling.

## [0.21.0] - 2026-10-22

### Fixed

- Browser search passes its URL through the shared native intent command. The optional data URI is validated and available through every entrypoint.
- Saved single-card HTML documents using `main.card` support item editing while document containers and empty padding remain protected.
- Concurrent native edits and automation state patches preserve unrelated state and cannot restore a deleted wrapper from an older configuration.

### Changed

- HTML state belongs to page/widget directories. Wrapper state uses `state.json`; card instances use `items/<instance-id>/state.json` and can keep custom files beside it. Legacy embedded state migrates, file edits refresh the owning view, and deleting an instance or wrapper removes its owned files.
- The starter Home is one full-page WebView with three editable cards. Existing homes retain their layout.
- Clock, installed-app search and notes are working card presets and standalone HTML widget packages. The complete dashboard is also a package.
- Saved cards offer Save as preset. Presets retain HTML, CSS and JavaScript and can be inserted without reloading the document. Repeated built-in notes keep separate saved state. Saving refuses duplicate IDs and excludes personal widget state.

### Migration

HTML widget state now lives in page/widget directories instead of the widget JSON record. Existing embedded state migrates automatically. File-based integrations must write the new state files. Reset the home or add the Home dashboard preset to use the combined starter on an existing installation.

## [0.20.0] - 2026-10-22

### Added

- A two-page starter home with clock and web search, installed-app search, saved quick notes, local phone status and optional city-based weather and air quality. Three utility Lua examples start disabled.
- Direct Android commands shared by WebSocket, MCP, HTTP, HTML and Lua. Discover provider resources, read bounded pages, edit contacts and calendars atomically, inspect SIMs, calls, clipboard, last-known location, notifications, media sessions and device state.
- Tracked SMS submission with durable duplicate protection, SIM selection and separate sent/delivery receipts. Packaged MCP guides document native data and the contact/SMS workflow.

### Fixed

- SMS documentation describes Android provider persistence for messages sent by non-default apps and distinguishes submission from sending or delivery.

### Changed

- Fresh homes use Home and Insights instead of the previous demonstration pages. Edited homes keep their saved content on update. Reset Dikciz home installs the new pages and restores the three disabled utility scripts; other saved scripts and policies remain. Disable or delete unwanted older examples in Settings, Scripts.
- Lua owner-command requests accept object tables as well as JSON strings, with bounded encoding for nested values.

## [0.19.0] - 2026-10-21

### Added

- MCP documentation resources provide 26 short guides and recipes, an index, paginated discovery and one-section reads. Each section is limited to 4096 UTF-8 bytes. The client adds `mcp-resources` and `mcp-read` commands.

### Fixed

- Full-device permission setup uses a dedicated Android task that remains in Recents after Settings or another app takes the foreground. Returning refreshes actual grants and the next setup action, including after activity or process recreation. The same setup controls and screenshots are available through WebSocket, MCP and HTTP.

### Migration

- UI clients must accept `snapshot.screen: "deviceAccess"` while full-device setup is open. Existing setup control IDs remain available.

## [0.18.0] - 2026-10-21

### Added

- Enabled Lua scripts can call ordinary launcher commands through `context.request` and `context.requestJson`, receiving canonical results and permission recovery. Enabling a script grants owner command access; Android permissions and root approval remain required.

### Fixed

- Long presses inside HTML content select the pressed card or block for a native item editor and scoped deletion. Removing an HTML item preserves its containing widget, page and siblings. Whole-widget controls remain on the top move handle.
- The bundled command deck tolerates missing card elements so remaining controls can initialize after a card is removed.
- APK update recovery starts the automation foreground service before reopening Home, restoring configured background event sources and schedules when Android prevents background activity launch.
- Ordinary MCP operations use the shared command validator and executor. MCP screenshot results retain their PNG payload alongside image content, and the HTTP client supports PNG export and explicit overwrite.
- Remote text editing accepts empty strings, allowing clients to clear an input like the native editor.
- HTML errors retain permission recovery metadata. HTML and Lua lifecycle requests deliver their response before recreating the launcher.

### Migration

- Review enabled Lua scripts before upgrading. Their new owner command functions can access files and shell commands independently of the policy's returned-action list. Disable scripts you do not trust. Event delivery and returned typed actions still require their configured policy grants.
- Use the top move handle for whole-widget HTML controls. Long presses inside HTML now edit or delete the nearest saved item.
- Exact MCP screenshot schemas must accept `pngBase64` in structured results alongside the image content block. HTML requests now allow one MiB of UTF-8 JSON. Live shell streaming remains available through WebSocket, MCP and HTTP only.

## [0.17.0] - 2026-10-20

### Added

- Opt-in permission recovery through `autoRequestPermissions` and explicit `requestDeviceAccess` commands on WebSocket, MCP, HTTP and the HTML bridge. Phone notifications and consent dialogs identify the requested access. Replies distinguish missing access, queued owner review, notification delivery and already-granted or unavailable access.

### Fixed

- External activity launches share task isolation and foreground verification across native controls, Lua, HTML, WebSocket, MCP and HTTP. Replies distinguish accepted requests, observed launches, missing access, locked screens and targets that never appear.
- Debug builds display `Dikciz (debug)` in Android Home selection, app info and the app drawer, distinguishing them from the release app named `Dikciz`.

### Migration

- Launch clients must accept the new `activityLaunches` result array and terminal outcomes `launch_unverified`, `launch_not_observed` and `device_locked`. `outcome: "launched"` and `launchApp.launched: true` require observed foreground evidence. Missing Accessibility no longer reports a verified launch. `app_action_completed` records request acceptance; `activity_launch_observed` records verification.
- Exact command and discovery schemas must accept `autoRequestPermissions`, `requestDeviceAccess`, its MCP tool and `permissionRequest` recovery details. Device-access inventories include `homeApp` and `installPackages`. APK installation no longer opens unknown-source settings automatically; opt in to owner review or request the access explicitly.

## [0.16.2] - 2026-10-20

### Added

- Live shell and logcat output through WebSocket, MCP and HTTP, with explicit cancellation, configurable deadlines and binary-safe chunks. Streaming defaults to no deadline; ordinary shell commands retain their 30-second default.
- The Python client consumes live output as JSON Lines or raw bytes with `--stream-raw`.
- The startup screen shows the running version and build type.

### Fixed

- Apps opened from Dikciz use independent Android tasks, so Home and Recent apps preserve their running state.
- Android Back handling uses the system callback on Android 13 and newer while keeping the legacy path for older devices.
- Android lint now runs in the required CI code job. Permission declarations, exact-alarm checks and move-handle accessibility pass its checks.

### Migration

- Exact control schemas must accept `shellStream`, `shellStreamStop`, their MCP tools and the new streaming limits. WebSocket clients must accept `shellStreamEvent` frames. Clients must consume SSE for HTTP and MCP streaming calls. MCP callers must opt in to request-scoped log notifications; the supplied client does this automatically.
- Continuous commands stop when their originating connection closes, authorization changes or the listener stops. Full device logcat requires privileged access.

## [0.16.1] - 2026-10-20

### Fixed

- APK updates now reopen Dikciz and its remote listeners when it is the default Home app or has overlay access. Saved configuration and authentication are preserved.
- When background reopening is unavailable, an update notification opens the new launcher directly and clears on startup. If notifications are disabled, open Dikciz manually.

## [0.16.0] - 2026-10-20

### Removed

- Removed the default SMS role from permission setup, its request and result handling, and the public device-access inventory. The wizard no longer gets stuck retrying that role. Ordinary SMS read, receive and send permissions remain available.

### Migration

- Clients that validate the exact `automationStatus.deviceAccess` inventory must remove the `smsRole` requirement and unused `role` kind. Progress totals cover the remaining supported controls. No saved configuration change is required.

## [0.15.5] - 2026-10-20

### Added

- WebSocket, MCP and HTTP expose `deviceInfo`, `packageInfo` and an acknowledged `restart` that recreates the launcher and listeners while preserving saved configuration and the process.
- `displayScreenshot` captures the visible Android display through enabled Accessibility on Android 11 or newer. Existing `screenshot` retains launcher-window capture.
- Diagnostics identify the serving package, installed version, process and activity instance.
- `launcherHandoff` switches listener ownership between installed debug and release builds without killing the serving process. Target file access and listener support are checked before shutdown.

### Fixed

- Listener bind failures continue retrying every 30 seconds after their initial fast attempts, so a released port conflict no longer requires manually restarting the app.
- Failed binds close their sockets before retrying.
- The Python MCP client deletes sessions after successful and rejected calls. Cleanup tolerates restart-invalidated sessions, preventing repeated CLI calls from exhausting the session limit.

### Changed

- Scheduled issue synchronization uses repository-specific hours to spread workflow load throughout the day.

### Migration

- Exact control schemas must accept the five new commands, matching MCP tools and the diagnostics `application` object. A restart closes old connections and MCP sessions; reconnect and verify a changed instance ID.

## [0.15.4] - 2026-10-19

### Added

- WebSocket, MCP and HTTP commands browse arbitrary app-accessible paths, read and write bounded binary chunks and create directories. The Python client adds HTTP commands and chunked `upload` and `download` helpers.
- Standalone APK installation stages uploads asynchronously, optionally verifies SHA-256 and exposes Android permission, confirmation, success, cancellation and failure states by session ID.
- Plain HTTP POST `/v1/automation` shares the MCP listener, authentication, browser-Origin checks and validated command target, including shell output and exit status.
- **Settings**, **Configure logging** selects DEBUG, INFO, WARN or ERROR and retention from 1 to 90 days. Saving applies immediately; Cancel leaves the saved configuration unchanged. Defaults remain INFO and seven days.
- The native log viewer, WebSocket `scriptLogs` and MCP `dikciz_script_logs` include the target app as nullable `componentPackage`. App actions expose their `actionType`, and direct shortcut launches record their target package too.

### Changed

- Shell commands now default to a 30-second timeout, increased from five seconds, and accept per-request `timeoutMilliseconds` and `maxOutputBytes` through WebSocket, MCP and HTTP. The output default remains 64 KiB. Discovery reports defaults and supported ranges, and the supplied client waits for the requested command budget.
- Successful app launches no longer show a popup. App-action logs retain the action, target and outcome; failures still show feedback.
- The guides document daily UTC log files at `/sdcard/Dikciz/logs/YYYY-MM-DD.log`, level filtering and retention controls.

### Migration

- Exact discovery schemas must accept `transports.http`, the six file/APK commands and their matching MCP tools, and the shell timeout/output fields in commands, tool schemas and limits. HTTP uses the configured MCP port; existing endpoint settings need no change.
- Clients that validate exact log-record keys must accept `componentPackage`, which is null for records without an app target.

## [0.15.3] - 2026-10-19

### Added

- WebSocket and MCP listeners accept a configurable numeric IPv4 or IPv6 `listenAddress` in `control/ports.json` and the native control-port settings. Set `0.0.0.0` for direct IPv4 connections over a private network or VPN without USB forwarding. Existing records retain the `127.0.0.1` default.
- `controlStatus.transports` reports each listener's `listenAddress` alongside its port and path.

### Changed

- Editing, atomically replacing or deleting `control/ports.json` automatically rebinds both listeners when their effective address or ports change, including while another Android app is in front. Rewriting unchanged endpoints keeps current connections open.
- The README, connection walkthrough, configuration reference, user guide and agent setup describe direct network access, bearer authentication, directional network policies and connection troubleshooting.

### Migration

- Existing installations keep their loopback binding and ADB forwards. For direct access, enable bearer authentication, choose a network listen address, and connect to the phone's reachable hostname or IP using its device ports. Allow traffic from the controlling computer to those ports in the network policy.
- Invalid listener configuration falls back to `127.0.0.1:19001` and `127.0.0.1:19002`. A changed endpoint closes current connections; reconnect using the new settings.

## [0.15.2] - 2026-10-19

### Fixed

- Lua automation no longer crashes minified builds with `NoClassDefFoundError` while initializing LuaJ's bit library. Dikciz preserves the library function classes and their constructors during release shrinking.

### Added

- `make dikciz-test-minified-lua` runs the Lua context and failure diagnostics tests with release shrinking enabled. Its debug-signed APK uses the debug package and can install alongside the signed release.

## [0.15.1] - 2026-10-19

### Changed

- The startup screen lasts five seconds instead of ten. Each second it shows a different line picked at random from a hundred hacker and programmer jokes, and no line repeats within one launch. The current line is the `startup:message` semantic node.
- The lab pins Android Lab 0.14.1, whose emulator container has a 12g memory cap instead of 8g. At 8g, long test runs could get the emulator killed. `ANDROID_LAB_EMULATOR_MEMORY_LIMIT` passes through Make to override the cap. Run `make restart` once to recreate the emulator with the new cap.

### Fixed

- The app icon opens the Dikciz home. Before, it opened Fossify's own launcher, which could show Fossify's "fake version" warning and send you to the Play Store. The Dikciz build no longer contains any Fossify screen. Fossify's launcher icons, home, settings and hidden-icons activities and the Fossify library's about, customization, FAQ, license, contributors, donation, app-lock and blocked-numbers activities are removed from the manifest, along with the library's exported receiver for Fossify's shared theme.
- Without shared storage access, the prompt fills the page and its **Grant file access** button can be tapped. Before, the prompt was squeezed into the top-left grid cell, its text was cut off and the button was out of reach. The configuration error screen had the same fault and fills the page too.
- The debug log no longer records app component names from the app drawer. Drawer taps log `drawer:app` or `drawer:app-actions` instead of the full semantic ID.
- The cold-start brand stays dismissable through the control planes while the storage prompt is showing.
- The live tests tap the storage prompt's button at its on-screen position and cover the app icon, the removed Fossify screens and the startup lines. The log audit no longer expects an event the launcher stopped writing, and the Lua access test waits for the widget patch that follows the state patch.

## [0.15.0] - 2026-10-19

### Added

- The WebSocket and MCP device ports are configurable in `/sdcard/Dikciz/control/ports.json` and under **Settings**, **Automation**, **Configure control ports**. Saving in Settings restarts both listeners. Direct file edits apply when the listeners next start. Ports must be 1024 to 65535 and differ; an invalid file falls back to 19001 and 19002. No control-plane command can change them, and `controlStatus` reports the ports in use.
- The page menu's Cancel button has the semantic ID `page:menu:<page-id>:close`, so control clients can close the menu.

### Security

- The WebSocket control plane refuses browser origins other than device loopback with `403 Forbidden`, the rule MCP already applied. Before, any web page open in the phone's browser could connect to `ws://127.0.0.1:19001` and run launcher commands while the bearer password was off. Native clients, ADB forwards and apps without an `Origin` header connect as before.
- An automation script receives an event only when its policy is enabled and grants that event's capability. A metadata capability without its content counterpart hides the content fields: clipboard `text` and `textTruncated`, calendar `title`, `description` and `location`, SMS `messages`, notification `title` and `text`, and media-session `title`, `artist` and `album`. Before, every subscribed script received full event content whatever its policy granted. A refused delivery logs `automation_delivery_rejected` with reason `capability_denied`.
- `automationTrigger` refuses a script whose policy is disabled or does not grant `manualTrigger`.

### Fixed

- A reset to the bundled home outside safe mode keeps every bundled page. Before, the running launcher kept only the home page until the next reload, so selecting another page failed with `page ID was not found`.
- Granting Do Not Disturb access delivers the current interruption filter to subscribed scripts and widgets. Before, Dikciz did not notice the grant until the automation service restarted.
- A Lua script that hits the instruction, time or memory limit fails with its `instruction_budget_exceeded`, `execution_deadline_exceeded` or `memory_budget_exceeded` reason, logs the failure and sends the failed-script notification. Before, LuaJ reported the stopped run as a success, so the script showed `completed` with no event handler.
- The cold-start brand stays in the semantic snapshot as `startup:dismiss` until it is gone, so a control client can dismiss it. Before, the first home render removed the node while the brand still covered the screen and took the next touch.
- A delivery dropped by `minimumIntervalMilliseconds` logs `automation_delivery_rejected` with reason `rate_limited` at debug level.
- HTML widgets draw their native card frame. The theme and the Appearance editor set their background, border, margin and padding, as the configuration guide describes. Before, Dikciz saved these settings for an HTML widget and drew none of them.
- Dikciz no longer crashes with `ForegroundServiceDidNotStartInTimeException` when automation is switched off right after it starts, or when a cold start keeps the launcher busy. The visible launcher starts the automation service as a normal service, and a stop request reaches the service, which leaves the foreground before it ends.
- The live test suite runs again. Its conftest pointed at the combined repository root and Android Lab's old in-repository device script, so every test failed at collection since the split. Tests that still expected removed widgets, older layouts or a fixed event order now follow the current launcher.

### Migration

- Check each automation policy against its script's subscriptions. A script needs the capability of every event it subscribes to, the content capability to keep reading content fields, and `manualTrigger` for `automationTrigger`. The bundled policies already declare theirs.

### Documentation

- "Who can connect" in the automation control guide and a README note explain that other installed apps can reach the control planes unless the bearer password is on.
- Source, script, test and Fossify directories have their own README files, and the root README, configuration, automation and user guides match the current code. The automation guide documents MCP sessions and corrects the `patchDom` argument shape to a `values` object. A Fossify source refresh keeps `.fossify/README.md`.

## [0.14.1] - 2026-10-18

### Fixed

- The app is named Dikciz in every language. Before, phones set to a language with a Fossify translation, such as English (UK) or German, showed it as "Launcher". The automation service notification uses the same name.

## [0.14.0] - 2026-10-18

### Added

- Tag pushes build a signed `dikcizRelease` APK and attach `dikciz-launcher-vX.Y.Z.apk` and its `.sha256` to the GitHub Release, so Obtainium can install and update Dikciz. The README publishes the signing certificate fingerprint.
- `make release-apk` builds the signed release APK into `dist/` and fails unless it is signed with the published certificate and reports the version in `VERSION`.

### Changed

- The `dikciz` flavour reports `VERSION` as its `versionName` and derives `versionCode` as `major * 10000 + minor * 100 + patch`, instead of Fossify's `1.10.0` and `16`. Builds fail when `VERSION` is not `X.Y.Z` with minor and patch below 100.
- The Makefile pins Android Lab 0.14.0. A missing pinned image is built with that release's installer once `ANDROID_LAB_ACCEPT_ANDROID_LICENSES=yes` is set. CI runs the same `make build`, and the CI-only `make ci-build` bootstrap is gone.
- The Makefile derives the Dikciz APK paths from Fossify's `VERSION_CODE` instead of hardcoding them.

### Fixed

- A Fossify source refresh keeps `VERSION`, `.gitattributes`, `.gitleaks.toml`, `.dockerignore` and the `app/src/testDikciz` unit tests, and the build overlay patch now carries the `testDikciz` source set and test dependency. The refresh test fails when the live build file has Dikciz lines the overlay does not carry.

### Migration

- Existing `dikcizDebug` installs report version code 16. Debug builds now report 1400 for 0.14.0, so they install over them as an update.
- Release APKs use the package `eu.psyb0t.dikciz.launcher` and install next to the debug build.

## [0.13.0] - 2026-10-07

### Added

- The README displays the Dikciz logo and links to the APK installation and launcher operation skill.
- A separate lab integration skill links to Android Lab's canonical instructions when its skills are not installed.
- ClawHub tag publishing selects only the launcher usage skill and excludes lab integration skills and plugins.

- Codeberg and GitLab mirrors, scheduled mirror issue sync, and public archive jobs.

### Changed

- Generic Android Lab and delegation skills no longer ship as copies in the launcher repository. Android Lab owns its reusable skills.

- Dikciz is a standalone source checkout. Its Makefile consumes versioned Android Lab images built locally, without pulling or silently rebuilding them.
- Product controls, tests and Fossify refresh scripts belong to this repository. Android Lab supplies the generic toolchain and emulator.
- Fossify refresh preserves the checkout's Git metadata and shared state directory while verifying a source backup before replacement.

### Fixed

- Shell lint and formatting explicitly use four-space indentation, so CI and local checkouts agree without an EditorConfig file.
- Tooling creates the local state directory as the host user before Docker mounts it, so fresh checkouts can write Gradle state.
- Normal emulator launch grants Dikciz the file access needed to load its configuration, matching reset behavior without clearing saved data.

### Migration

- Build the required Android Lab images from its separate repository before running launcher Make targets. Existing combined-repository workflows must use the launcher and lab checkouts separately.

## [0.12.0] - 2026-10-06

### Changed

- Widgets use one non-stacking page-grid order. Moving a widget changes only its cell. `zIndex` no longer controls display order or belongs in widget configuration.
- The Apps and commands sheet constrains installed application icons to a fixed 32dp box, so package artwork cannot expand a command row.
- The development image pins the current Alpine `python3` package.

### Migration

- Remove `zIndex` from every widget document under `/sdcard/Dikciz/pages/*/widgets/` before upgrading. Dikciz rejects the removed key and keeps the current home instead of applying that configuration.

## [0.11.1] - 2026-09-23

### Added

- A Dikciz scripting tutorial explains every native event, custom events, Lua policies, HTML bridge calls, self and cross-widget state, typed actions, logs, and the Android access outcomes that can stop an automation.
- The user guide now starts with APK installation and Android Home setup, then shows the resulting page search, saved HTML, appended blocks, script notification, script log, and notification digest screens.

### Changed

- The module README and user guide direct people to the scripting tutorial for events and widget automation, while the automation-control guide remains the WebSocket and MCP protocol reference.
- The bundled Workbench action list now includes `patchDom`, `appAction`, and `sendSms` with the actions it already supports.

### Fixed

- The automation and configuration references no longer omit `patchDom` from the typed action vocabulary.

## [0.11.0] - 2026-09-22

### Added

- `make dikciz-control-ready` waits for both local Dikciz control planes to
  answer through the shared forward. `make dikciz-run` now calls it after the
  launcher starts, so it does not report success before the WebSocket and MCP
  clients can connect.
- The Dikciz user guide is organized as complete tasks, with fresh emulator
  evidence for named page search, saved HTML, appended HTML blocks, script
  notifications, script logs, and notification-driven dashboard updates.

### Fixed

- `make dikciz-run` now starts the shared emulator before it installs and
  launches Dikciz. It no longer waits forever for ADB when the emulator was
  previously stopped.
- First-run control readiness now proves the live WebSocket and MCP listeners
  without requiring storage access. The full smoke tests still require the
  configured Home screen.
- The shared forward now joins the lab's edge network as well as its private
  device network. Docker now publishes the requested WebSocket and MCP ports
  on host loopback, while they remain unavailable from the LAN.

## [0.10.0] - 2026-09-21

### Added

- `make dikciz-guide-capture` regenerates every screenshot in the user guide.
  It resets the shared emulator to the bundled home, performs each documented
  step on the real device, photographs the result, and writes the images into
  `docs/images/user-guide/`. A documented step that stops working now fails the
  capture instead of leaving a stale picture in the guide.
- Page search in the home chrome. The magnifier beside the Dikciz mark opens a
  jump list of every page, each row labelled `Name (address)`. Typing filters
  on either half, so `Notes` and `2H1V` both reach the same page, and tapping a
  row goes there. Automation drives it through `launcher:page-search`,
  `launcher:page-search:query`, and `launcher:page-search:page:<pageId>`.
- Native app shortcuts can merge into an app group by dropping one tile onto
  another. Groups open as an icon grid and collapse back to a shortcut when one
  member remains.

### Changed

- The user guide is a twenty-nine step walkthrough, from first launch through
  app groups, the grid, HTML widget authoring, pages, provider widgets,
  scripts, and the system sheets, to the two control planes. Every screenshot
  in it comes from the capture command. The thirteen hand-made images it used
  before are gone.
- The launcher controls button in the bottom chrome is the Dikciz mark alone.
  The `Controls` text is gone, and the button keeps its
  `Open Dikciz launcher controls` content description for the screen reader and
  for automation.
- The page menu and the launcher control sheet print the page address beside
  the page name, for example `Page: Home (1H1V)`. That address is how Lua,
  HTML, WebSocket, and MCP already name the page.
- An app tile draws its icon as the largest square its grid cell allows,
  measured against the narrower side, with the icon and its label centred in
  the cell as a pair. The icon used to be a fixed 48dp pinned to the top of the
  cell, which left a large tile mostly empty.
- App groups are made by dropping one app tile onto another, the way a launcher
  makes a folder. The picker's `App group` entry and its checkbox form are
  gone. Dropping a third tile adds it. A group opens as a grid of its member
  icons: tap one to launch it, long press one to remove it. Removing down to a
  single app collapses the group back into a plain app shortcut in the same
  cell, keeping the widget ID so a script addressing it keeps working. The
  `appGroup` type stays in the public schema and in `addWidget`.
- Dropping a widget on an occupied cell now merges or trades instead of always
  being refused. Two app tiles merge into a group. Two widgets of the same size
  trade places, and both tiles show the trade while the finger is still down.
  A drop between different sizes is still refused. The tile under the pointer
  decides, not the corner of the dragged widget.
- An app tile is dragged from anywhere on it, and a tap anywhere on it still
  launches the app. A widget that owns its content, an HTML document, an
  Android provider view, or the script dashboard, keeps its top `24Dp` drag
  strip, because its body needs the touches for itself.

### Fixed

- The launcher's own automation-service notification is no longer an
  automation event. Android requires `startForeground` on every start command
  the service receives and each call re-posts that notification, so a script
  subscribed to notifications was handed an event the launcher caused by
  configuring itself. A script that wrote anything in response closed a loop:
  the write reloaded the configuration, the reload restarted the service, and
  the service posted the notification again. One bundled script reached two
  hundred and eighty-six status writes in six minutes this way. Notifications
  the launcher posts on a script's behalf are ordinary content and are still
  reported.
- A read that only decides whether a write can be skipped no longer ends the
  launcher. `writeAtomicFileIfChanged` compared the stored file against the
  content about to replace it without guarding the read, so a public file
  left half written by an interrupted process returned an I/O error and took
  the process down from a background thread. A failed comparison now answers
  that the content differs and the write goes ahead.
- A change written to `/sdcard/Dikciz/` from outside the launcher now reloads.
  The launcher watched the tree with Android's `FileObserver`, which reports
  the FUSE view of shared storage and never saw a file that adb, a computer
  over MTP, or the launcher's own process wrote. An edit made the documented
  way could sit on disk until something unrelated happened to trigger a
  reload. A cheap stat poll of the watched directories now backs the observers
  up while the home screen is running.
- `make dikciz-test` no longer reports failures its own suite caused. Two
  tests named a fixture their module never imported and could not be collected
  at all. Two app drawer tests launched an app and returned home without
  closing the drawer, so later tests drew their pages underneath it. The
  accessibility waits polled `accessibilitySnapshot` ten times a second, which
  exhausts the documented budget of thirty-two live descriptors in about three
  seconds and refuses the next request. A screen-off check read wakefulness
  once and caught the `Dozing` step on the way to `Asleep`. The log audit
  asserted the day's log covers every control surface the suite exercises
  while its file name sorted it ahead of most of them, so it is collected last
  now.
- The lab accepts every emulator console credential the emulator can generate.
  The credential is base64, and the check rejected the `+`, `/`, and `=` that
  alphabet contains, so a randomly drawn token broke GPS injection, sensor
  injection, incoming SMS, and every other console operation until the
  emulator was recreated.
- Deleting a widget or a page removes its public document. The store only ever
  wrote files, so every removed widget and every removed page left a document
  behind that nothing loaded. Making a group removes two widgets at once, which
  made the stale files pile up quickly. Only documents Dikciz writes are
  removed, so a foreign file keeps its directory.
- Android no longer kills the launcher process over the automation service.
  `onStartCommand` could return without ever reaching `startForeground`, once
  by skipping the call when nothing about the configuration had changed, once
  by skipping it while the service believed it was already in the foreground,
  and once by ignoring a refusal. Any of the three let the platform kill the
  whole process five seconds after `startForegroundService`, taking the home
  screen and both control planes down with it, and the belief was the worst of
  them because Android ends the foreground state on its own and the service
  had no way to hear about it. Every start command now reaches
  `startForeground`. A refusal is caught instead of escaping, and whether it
  is survivable is answered from the platform's record of the posted
  foreground notification rather than from a field the service maintains.
- Dismissing a widget's action sheet forgets every control it registered. The
  two dismissal paths kept their own lists, which had already drifted, so
  `move` survived both and `addBlock` survived one. A remote client kept seeing
  buttons for a sheet that was no longer on screen, and a tap on one was
  accepted.
- A widget hovered as a merge target no longer wears the refusal styling. The
  drop lands on an occupied cell, which the grid otherwise refuses, so the
  target looked rejected right up until the drop made a group instead.
- The page draws its grid while a widget is being moved or resized. Placement
  snaps to whole cells, so the gesture was guesswork without them.

## [0.9.0] - 2026-09-19

### Added

- Automation scripts can send an SMS. The `sendSms` action takes a dialable
  `recipient` and a `body`, divides a long body into message parts, and needs
  the `SEND_SMS` Android permission that `automationStatus` reports as
  `smsSend`. With the existing `smsReceived` event a script can now read an
  incoming message, decide a reply, and send it. Dikciz is not the default SMS
  app, so a sent message does not reach the Android SMS provider or the owner's
  messaging history, and Android limits a non-default app to about thirty
  messages in thirty minutes before it asks the owner to confirm each send.

### Changed

- A policy's `actions` list is now enforced when a script action runs. An
  action the policy does not declare is refused as `capability_denied` and
  nothing happens. A script whose policy is missing gets no actions at all.
  Actions a Dikciz HTML widget dispatches directly are unchanged, because the
  owner invokes those on the device.
- A refused action now records a machine-readable `reason` beside its outcome,
  so a log reader can select refusals without parsing outcomes.

### Fixed

- Android's Home action now saves the page it selects. The launcher returned to
  the home page on screen but kept the previously selected page in
  `config.json`, so the next process start reopened on that page.
- Only the horizontal page rail starts the app-drawer gesture. An upward drag
  that began in the page itself opened the drawer instead of scrolling page
  content, because the page viewport was an activation area and the activation
  band reached above its own rail.
- The bundled pages fill the whole page grid. Their widgets stopped one row
  short of the six-row grid, which left an empty strip under the last card and
  clipped its content.

### Migration

- Review each automation policy before upgrading. A script that returns an
  action its policy does not list stops working and records
  `capability_denied`. Add the action to the policy's `actions` list to restore
  it. The bundled scripts already declare every action they use.

## [0.8.0] - 2026-09-19

### Added

- A Dikciz HTML widget can append a named block from its own menu. The block
  saves its marked source into the already-live document, scrolls that widget
  to the new block, and does not reload the WebView. Bundled blocks cover a
  heading, a status row, an action button, and a divider.
- Settings can edit the native page grid. Columns, rows, gap, and outer padding
  apply to every page. The change is all or nothing, and it is refused with the
  offending page and widget when an existing item would collide or leave the
  new grid.
- The page menu can move the current page left, right, up, or down. A move
  swaps with the page at the immediately adjacent coordinate and leaves every
  other page in place. A direction with no adjacent page is disabled.
- A long press on the blank page rail before the first dot or after the last
  dot creates and selects a saved page at that edge of the rail.
- WebSocket and MCP expose a `gridSet` command that applies the same page-grid
  change with the same all-or-nothing result.

### Changed

- A native page is one fixed viewport. It no longer grows downward, and adding
  an item never pushes content below the visible page.
- Every top-level item owns explicit grid cells. The old `position` with `xDp`
  and `yDp` is replaced by `cell` with `column`, `row`, `columnSpan`, and
  `rowSpan`. The `minimized` flag is gone.
- Adding an item first-fits row-major on the current page. When no rectangle
  fits, the action writes nothing and reports `page_full`.
- Move and resize snap to whole cells. A move or resize that would overlap
  another item or leave the page is rejected before anything is saved.
- Page creation saves and selects the new page in one step. The provisional
  page and its confirm and cancel controls are removed.

### Migration

- The grid schema is the only layout schema Dikciz reads. A `/sdcard/Dikciz/`
  tree written by 0.7.0 or earlier carries `position`, old `size` records, or
  `minimized`, and the strict parser rejects all three. Copy the old tree first
  if you want to keep it, then either push a grid-shaped document through
  WebSocket or MCP `configReplace`, or run `make dikciz-reset` to return to the
  bundled starter home.

## [0.7.0] - 2026-09-17

### Added

- A screen-by-screen Dikciz guide now covers the bundled home, launcher
  controls, app drawer, widget library, page manager, settings, automation
  access, script dashboard, public files, ADB, WebSocket, and MCP with shared
  emulator screenshots.

### Changed

- Native page content can grow downward. Adding a widget or app shortcut now
  uses the normal gutter below the lowest current widget and scrolls to reveal
  the new item.
- WebSocket and MCP page-scroll state now reports the real native offset and
  range. Clients can use `scrollBy` and `scrollTo` to inspect and navigate
  extended page content.
- Fossify refresh comparison ignores generated Gradle output, preserves the
  fork contribution guide, and accepts only an upstream release marker newer
  than the imported marker.

### Fixed

- A full-height HTML widget no longer causes a newly added widget or shortcut
  to render on top of it.

## [0.6.0] - 2026-09-14

### Added

- Dikciz now has an always-visible native Controls route and app drawer. The
  drawer searches launchable apps by label or package name, launches a selected
  app, and exposes shortcut, app-info, uninstall, and root-backed force-stop
  actions.
- WebSocket and MCP now expose the shared app catalogue and typed app-action
  commands. The semantic UI and focused emulator checks cover the controls,
  drawer entry, search, launch, and shortcut placement.

### Changed

- New activity shortcuts are visible native tiles at the current page's usable
  top-left position. They are no longer obscured by full-page HTML content, and
  their top edge is the explicit move zone.
- Dikciz documentation now separates its release notes from retained Fossify
  upstream history and documents the native controls, app drawer, saved
  shortcuts, remote actions, and current focused verification routes.

### Fixed

- Re-entering Android Home no longer replays the ten-second Dikciz startup
  screen. An upward gesture from the horizontal page rail opens the app drawer
  instead of being lost to page navigation.

## [0.5.0] - 2026-09-13

### Added

- Dikciz now has native two-axis pages that combine HTML widgets, app
  shortcuts, app groups, third-party Android AppWidgets, and the native script
  dashboard. HTML widgets support local HTML, CSS, JavaScript, persistent
  state, automatic content height, native edit actions, and a bundled widget
  library.
- The HTML and Lua APIs now share page and widget addresses, typed Android and
  custom events, cross-widget state updates, diagnostics, and the automation
  action runtime. HTML updates patch the current document without reloading the
  widget.
- Local control now includes configured bearer authentication, device-access
  inventory, accessibility snapshots and actions, custom automation dispatch,
  script and widget diagnostics, and bounded renderer recovery.
- The bundled starter home demonstrates device status, clock, lock, app launch,
  brightness, notification, and script controls. Dikciz has its own launcher
  icon and startup screen.

### Changed

- The shared emulator is the one persistent manual and automated test phone.
  Clean resets remove only Dikciz and `/sdcard/Dikciz`, then restore the bundled
  starter home after a passed, failed, or interrupted test.
- Automation setup now presents the current Android permission, role, special
  access, accessibility, and root-shell state in one device-access inventory.
  The device remains the authority for every grant.

### Fixed

- HTML widget resize controls draw above widget content and stay within the
  native widget bounds. Page gutters and HTML card borders no longer disappear
  at viewport edges.
- HTML renderer limits reject excess widgets and documents, recover from a
  failed renderer, and keep minimized widgets out of renderer capacity.

### Migration

- Replace `text`, `toggle`, and Lua widget entries with `html`, `app`,
  `appGroup`, `provider`, or `scriptDashboard` entries before upgrading. Lua
  remains available as scripts under the public scripts tree, not as a widget
  type.

## [0.4.0] - 2026-09-10

### Added

- Dikciz now runs validated local Lua scripts from public files. Policies can
  subscribe scripts to typed Android and launcher events, then grant only the
  declared bounded actions.
- Local control planes now expose automation status, manual triggers,
  persistent command and UI events, script logs, diagnostics, screenshots,
  semantic UI dumps, and a supplied dependency-free shell client.
- Script failures are retained in bounded public logs, visible in Settings and
  the control planes, and can post a rate-limited Android notification when
  the user grants notification permission.
- Provider widgets can expose their current attached actions through stable
  semantic IDs. The lab includes a configurable fixture for provider actions,
  media controls, weather recovery, and notification actions.
- The lab can stage, compare, dry-run, and explicitly refresh the maintained
  Fossify source base from a selected local source tree.

### Changed

- Dikciz automation policies now cover typed sensors, location, Calendar,
  contacts, phone state, Health Connect daily steps, incoming SMS, clipboard,
  notification and media sessions, device administration, and selected Android
  system state. Android remains the authority for every permission and special
  access grant.
- The live-control test suite is split by behavior. Release checks use focused
  device selectors instead of source-text assertions.
- Gradle output from the provider fixture is ignored so generated files cannot
  enter a release.

### Fixed

- Page, widget, picker, resize, lock, and appearance controls keep their
  semantic state consistent through native UI and both local control planes.

## [0.3.0] - 2026-09-06

### Added

- The Dikciz home surface reflows across portrait and landscape orientation.

### Changed

- Widgets move from an invisible zone along their top edge. The visible drag
  grip is gone, and card content no longer reserves side space for it.
- The bundled `dikciz-dark` and `terminal` themes use 8 dp widget padding.
- Page indicator rails keep an equal 16 dp chrome inset on both sides, and the
  vertical rail ends level with the horizontal rail.
- Fill-page resize keeps a theme's outer widget margin inside the page
  allocation.
- Dikciz's storage-access prompt uses the bundled Dikciz Dark theme, so the
  first screen after installation matches the bundled home.
- `make dikciz-test` builds the upstream-compatible Fossify core flavour
  alongside the Dikciz flavour, so the Home-role tests install a core build
  from the current source instead of whichever APK was already on disk.
- The physical-device check installs through Android's package manager for the
  primary user and forwards the MCP endpoint alongside the WebSocket.

### Fixed

- Android's provider bind request carries the provider's user profile, so
  binding works on devices that expose more than one profile.
- The physical-device runner waits for Android to publish Dikciz's Home
  activity and recreate its debug app sandbox after a clean installation.
- Android Home starts now require the requested package to hold the verified
  Home role before the system resolver is used.
- The physical-device check stops and uninstalls only Dikciz's debug package,
  removes only its public configuration tree, then installs the current build.
  It never reboots the configured phone.
- The physical-device check re-enables only the current Dikciz package after
  replacement, so a retained disabled-package state cannot hide its Home
  activity.

### Migration

- The physical-device check no longer backs up or restores Dikciz files, the
  Android Home role, or package data. It replace-installs the current build
  before it clears the app's standard private data and public tree, resets to
  the bundled home, and leaves Dikciz holding the Home role. Copy
  `/sdcard/Dikciz` yourself first when you need the previous configuration.

## [0.2.0] - 2026-09-06

### Added

- Public Dikciz configuration tree rooted at `/sdcard/Dikciz/config.json`,
  with separate page, widget, and reusable theme files.
- Native page and widget controls for app shortcuts, provider widgets, editing,
  styling, locking, resizing, page creation, and two-axis navigation.
- Control-plane seed, reset, and bounded semantic wait commands through both
  WebSocket and MCP.
- An opt-in, single-device physical-phone check that fresh-installs the debug
  build, preserves the user's Dikciz files and Home role, and grants storage
  access through Android Settings.

### Changed

- Widgets remain inside the page viewport. Fill-page resize actions keep the
  active resize selection visible until the user finishes it.
- Page rails cycle one page at a time when their empty track is tapped, and
  their dot spacing no longer wastes page space.
- The lab clears only stale Android AVD lock files after an unexpected emulator
  exit.

### Fixed

- Dikciz's storage-access and configuration-error prompts now lay out their
  title, message, and action separately.

### Migration

- On first start after upgrading, Dikciz converts
  `/sdcard/Dikciz/dikciz.config.json` into the public tree and archives the
  old file as `dikciz.config.migrated-v2.json`.

## [0.1.0] - 2026-09-04

### Added

- Docker-only Android build, emulator, browser inspection, and device-control
  workflow.
- Dikciz, a programmable launcher flavour with a JSON-defined two-axis home,
  native and Android provider widgets, local WebSocket and MCP controls, and
  semantic UI automation.
- Launcher themes, wallpaper backgrounds, per-widget appearance, bundled and
  local fonts, page and widget locks, default Home-page selection, and
  persistent widget references.

### Changed

- Browser VNC now presents the full Android viewport with visible navigation
  controls and keyboard forwarding.
- Dikciz remains additive to the tracked Fossify Launcher source base so future
  Android and launcher updates come from the upstream source fork.
