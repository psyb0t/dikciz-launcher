# Dikciz flavour source

This source set adds the Dikciz home, public configuration, scripting and local control planes to the Fossify Android base. It ships in debug and release builds. The [project overview](../../../README.md) covers installation and development; the [configuration guide](../../../docs/dikciz-configuration.md) owns saved formats and user-visible behavior.

## Layout

| Path | Purpose |
| --- | --- |
| `proguard-rules.pro` | Preserves LuaJ library classes and constructors used through reflection in minified builds. |
| `AndroidManifest.xml` | Registers the Dikciz Home activity, permissions, services and receivers, and replaces the inherited Main activity declaration. |
| `kotlin/org/fossify/home/dikciz/` | Dikciz implementation, retaining the Fossify Kotlin namespace. |
| `res/` | Native home layout, controls, labels, appearance and Android service metadata. |
| `assets/` | [Bundled starter, web API, complete widget templates, themes and fonts](assets/README.md). |

## Main classes

All Kotlin files below live in `kotlin/org/fossify/home/dikciz/`.

| Files | Responsibility |
| --- | --- |
| `DikcizHomeActivity.kt` | Coordinates home lifecycle, configuration reload, settings, dialogs, provider hosting, semantic snapshots and command execution. |
| `HomeConfig.kt`, `HomeConfigStore.kt` | Define the in-memory model and own file-tree loading, validation, persistence, starter reconciliation and script archives. |
| `DikcizNativeGrid.kt`, `DikcizPageGridLayout.kt` | Separate placement and conflict rules from Android view measurement and layout. |
| `DikcizHomeModelOperations.kt`, `DikcizHomeInteractionModels.kt` | Hold page operations, move and resize state, provider views and picker entries. |
| `DikcizLauncherControlView.kt`, `DikcizPageIndicatorView.kt`, `DikcizHomeGestureDetector.kt` | Implement launcher routes, page rails and the drawer gesture. |
| `DikcizAppCatalogue.kt`, `DikcizAppActions.kt`, `DikcizAppDrawerView.kt`, `DikcizAppShortcutTileView.kt` | Discover launchable apps, execute app actions and render the drawer and shortcut tiles. |
| `DikcizActivityLaunches.kt` | Isolates external activities from the Home task and verifies stable foreground windows through Accessibility, with one outcome contract for native, scripted and remote launches. |
| `DikcizStyleCodec.kt`, `DikcizStyleRenderer.kt`, `DikcizStyleEditor.kt` | Parse style and font selections, resolve Android appearance and provide native editing controls. |
| `DikcizTheme.kt`, `DikcizLauncherBackground.kt` | Load and validate themes and the shared launcher background. |
| `DikcizHtmlWidgetView.kt`, `DikcizWebCommandBridge.kt`, `DikcizWebWidgetContext.kt` | Render widget documents and connect JavaScript calls and widget context to native commands. |
| `DikcizHtmlWidgetStateStore.kt` | Stores wrapper and instance state under their page/widget directory, migrates embedded state, and cleans up owned files. |
| `DikcizHtmlWidgetLibrary.kt` | Validates and installs complete WebView templates. |
| `DikcizAutomationControlPlane.kt`, `DikcizMcpControlPlane.kt` | Serve WebSocket, Streamable HTTP MCP and HTTP JSON commands through one `DikcizAutomationTarget`. |
| `DikcizControlStatus.kt`, `DikcizAutomationStatus.kt` | Describe the command protocol and Android capabilities for discovery. |
| `DikcizChatActivity.kt`, `DikcizChatModelControls.kt`, `DikcizChatCommands.kt` | Native resizable Markdown chat, API-backed model dropdown, per-conversation settings and shared command schema. |
| `DikcizChatRuntime.kt`, `DikcizChatAgent.kt`, `DikcizChatService.kt` | Serialized persistent queue, tool loop, context selection, event routing and foreground/reply notifications. |
| `DikcizChatStore.kt`, `DikcizChatSettings.kt`, `DikcizChatProvider.kt`, `DikcizChatAnthropicStream.kt` | SQLite transcripts, Keystore-encrypted credentials, validated budgets and Koog provider/SSE adapters. See [embedded chat](../../../docs/embedded-chat.md). |
| `DikcizMcpResources.kt` | Maps exact documentation URIs to packaged Markdown, validates resource parameters and paginates the fixed catalog. |
| `DikcizDeviceAccessActivity.kt` | Owns the separate full-access setup task, recomputes Android grants on resume and exposes its active controls to the existing Home command target. |
| `DikcizPermissionRequests.kt` | Validates permission-recovery options, persists targeted owner reviews and posts permission notifications without replaying commands. |
| `DikcizControlPorts.kt`, `DikcizRemoteAccessAuth.kt` | Own device listen-address and port settings and bearer-password verification. |
| `DikcizAutomation.kt`, `DikcizAutomationActionDocument.kt` | Define policies, events, subscriptions and typed action documents. |
| `DikcizAutomationService.kt`, `DikcizAutomationRuntime.kt`, `DikcizLuaRuntime.kt` | Collect Android events, dispatch subscriptions, enforce policies and execute Lua. |
| `DikcizScriptWidgetBridge.kt`, `DikcizLuaScriptRunStatus.kt` | Resolve page/widget addresses and represent persisted script outcomes. |
| `DikcizDeviceAccess.kt`, `DikcizPrivilegedShell.kt`, `DikcizAccessibilityAutomationService.kt` | Report Android access, run bounded shell commands and implement cross-app Accessibility inspection and actions. |
| `DikcizDeviceInspection.kt` | Reports actual package versions, Android information and launcher instance identity through native PackageManager APIs. |
| `DikcizNativeCommands.kt`, `DikcizNativeData.kt`, `DikcizContentData.kt` | Define direct Android schemas, permission checks, bounded provider reads and atomic writes shared by every binding. |
| `DikcizCommandJobs.kt`, `DikcizShellSessions.kt`, `DikcizShellStreams.kt` | Own durable command results and process-owned incremental shell sessions; the stream runner owns process groups and cleanup. |
| `DikcizSpeechRecognition.kt`, `DikcizSpeechOutput.kt`, `DikcizSpeechModels.kt` | Adapt recognition, synthesis, voice/language discovery and model callbacks with deadlines and explicit outcomes. |
| `DikcizUssdControl.kt` | Observe one native carrier USSD request with explicit voice-SIM selection and uncancellable timeout semantics. |
| `DikcizEventJournal.kt`, `DikcizEventReplayAccess.kt` | Replay dispatched automation events with bounded process-owned history, epoch-bound cursors and current Android source-access checks. |
| `DikcizTorchControl.kt`, `DikcizHapticsControl.kt` | Observe torch state with bounded callbacks and submit finite haptics through the shared native registry. |
| `DikcizMediaCapture.kt`, `DikcizCameraControls.kt`, `DikcizCameraVideo.kt`, `DikcizAudioPlayback.kt`, `DikcizMicrophoneSession.kt` | Capture media with requested controls and actual frame metadata, and manage local playback/recording. Recognition and recorders share microphone ownership. |
| `DikcizMicrophoneStream.kt`, `DikcizMicrophoneStreams.kt`, `DikcizMicrophoneCaptureService.kt`, `DikcizBoundedReplay.kt` | Own foreground-service PCM capture, bounded cursor replay, notification stop and shared microphone cleanup. |
| `DikcizHardwareControl.kt`, `DikcizWifiControl.kt`, `DikcizLocationServices.kt` | Handle inventories, selected settings, requested scans, geocoding and GNSS observations. |
| `DikcizBluetoothControl.kt`, `DikcizGattControl.kt`, `DikcizNfcControl.kt`, `DikcizUsbControl.kt` | Own bounded peripheral transactions, actual connections and platform-access checks. |
| `DikcizFileManagement.kt`, `DikcizNotificationControl.kt` | Handle file lifecycle/search and notification dismissal, snooze and inline reply through the shared target. |
| `DikcizAppIcon.kt`, `DikcizSystemMetrics.kt`, `DikcizCpuCounters.kt` | Read Android app icons, RAM, storage, bounded per-core clocks/load and process CPU samples through the shared executor. |
| `DikcizAppUsage.kt`, `DikcizStarterApps.kt` | Read owner-granted foreground-use metadata and resolve installed native dock shortcuts for a new starter. |
| `DikcizSmsStatus.kt` | Reserves durable idempotency keys and records per-part sent/delivery callbacks through a private receiver. |
| `DikcizDeviceFiles.kt` | Validate absolute paths, paginate directories and transfer bounded base64 file chunks using Android app permissions. |
| `DikcizApkInstaller.kt` | Stage standalone APKs asynchronously, verify checksums and persist Android installer callback outcomes through its private receiver. |
| `DikcizLogger.kt`, `DikcizScriptFailureNotifier.kt` | Write bounded, redacted logs and report script failures through Android notifications. |

Notification, media, clipboard, SMS, Health Connect and Device Administration integrations have dedicated classes beside these files. Their permission and event contracts belong in the [configuration guide](../../../docs/dikciz-configuration.md) and [scripting tutorial](../../../docs/scripting-tutorial.md).

## Boundaries and invariants

The [complete bundled widget/script catalog](../../../docs/starter-home.md) describes the four starter pages, eighteen complete HTML templates and six disabled Lua examples. Goblin effects use existing native commands; its heat rule consumes weather events emitted by its visible WebView.

Keep product changes in this source set or a Dikciz-specific combined source set. Shared Fossify source sets remain replaceable. [The Gradle integration](../../build.gradle.kts) selects the Dikciz application ID and derives its version from the root `VERSION`; the [Fossify overlays](../../../.fossify/README.md) carry those changes across source refreshes.

`HomeConfigStore` validates owned fields and public paths before saving, preserves unrelated root namespaces and sibling objects under `launcher`, and writes documents with `AtomicFile`. Widget IDs are globally unique without regard to case; page positions are unique and selected/home page IDs must resolve. The store replaces an older bundled starter only while the saved starter remains pristine.

Pages use a fixed native grid and transparent canvases over one launcher background. Placement and grid changes must respect bounds and collisions. Locks prevent editing while preserving widget actions and navigation. Style parsing belongs in `DikcizStyleCodec`, so editors and renderers use the same rules.

Both remote listeners default to device loopback and execute through the same command target. The owner can configure a numeric network listen address in `control/ports.json`. The configuration watcher applies address and port edits by restarting the listeners, without rebuilding the home. Keep human accessibility labels readable and machine identities in the semantic snapshot. The [automation-control guide](../../../docs/automation-control.md) owns commands, events, authentication, origin checks and limits. Debug-only event fixtures live in [the combined debug source set](../dikcizDebug/README.md); the control planes remain in this release source set.

MCP documentation resources use a fixed URI allowlist and immutable APK assets. The catalog returns eight descriptors per page and enforces a 4096-byte UTF-8 budget per section. Asset reads run on MCP workers, with cached content and new response objects per request. Authentication, Origin and the selected modern/legacy protocol checks precede resource dispatch. The [resource guide](../../../docs/mcp-resources.md) owns the public contract.

Ordinary Android command schemas are registered in `DikcizNativeCommands` and dispatched by `DikcizNativeData`. Network, HTML and Lua adapters do not implement separate native procedures. Platform callbacks report observed, submitted, pending or unknown outcomes rather than upgrading acceptance to success. The [command reference](../../../docs/command-reference.md) is generated from installed-build discovery.

Filesystem commands use direct file I/O with bounded reads and writes. They are not confined to the public configuration tree. APK installation uses PackageInstaller sessions, device-granted unknown-source access and Android confirmation. The [files and APK guide](../../../docs/files-and-apks.md) owns transfer rules and installer states.

The [flavour JVM tests](../testDikciz/README.md) cover grid rules. The [live control suite](../../../tests/live_control/README.md) covers Android UI, persistence and public control behavior.

Remote restart runs only after its successful response is flushed, then recreates the activity without killing the process. Failed listener binds retry with a 30-second steady delay after the initial fast attempts. Accessibility display capture runs off the UI thread, closes hardware buffers and drops late results. The [remote device guide](../../../docs/remote-device-control.md) owns the public recovery and privilege contract.
