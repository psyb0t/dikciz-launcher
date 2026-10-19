# Dikciz flavour source

This source set adds the Dikciz home, public configuration, scripting and local control planes to the Fossify Android base. It ships in debug and release builds. The [project overview](../../../README.md) covers installation and development; the [configuration guide](../../../docs/dikciz-configuration.md) owns saved formats and user-visible behavior.

## Layout

| Path | Purpose |
| --- | --- |
| `AndroidManifest.xml` | Registers the Dikciz Home activity, permissions, services and receivers, and replaces the inherited Main activity declaration. |
| `kotlin/org/fossify/home/dikciz/` | Dikciz implementation, retaining the Fossify Kotlin namespace. |
| `res/` | Native home layout, controls, labels, appearance and Android service metadata. |
| `assets/` | [Bundled starter, web API, widget packages, blocks, themes and fonts](assets/README.md). |

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
| `DikcizStyleCodec.kt`, `DikcizStyleRenderer.kt`, `DikcizStyleEditor.kt` | Parse style and font selections, resolve Android appearance and provide native editing controls. |
| `DikcizTheme.kt`, `DikcizLauncherBackground.kt` | Load and validate themes and the shared launcher background. |
| `DikcizHtmlWidgetView.kt`, `DikcizWebCommandBridge.kt`, `DikcizWebWidgetContext.kt` | Render widget documents and connect JavaScript calls and widget context to native commands. |
| `DikcizHtmlWidgetLibrary.kt`, `DikcizHtmlBlockLibrary.kt` | Validate and install complete widget packages and reusable fragments. |
| `DikcizAutomationControlPlane.kt`, `DikcizMcpControlPlane.kt` | Serve WebSocket and Streamable HTTP MCP through one `DikcizAutomationTarget`. |
| `DikcizControlStatus.kt`, `DikcizAutomationStatus.kt` | Describe the command protocol and Android capabilities for discovery. |
| `DikcizControlPorts.kt`, `DikcizRemoteAccessAuth.kt` | Own device listener-port settings and bearer-password verification. |
| `DikcizAutomation.kt`, `DikcizAutomationActionDocument.kt` | Define policies, events, subscriptions and typed action documents. |
| `DikcizAutomationService.kt`, `DikcizAutomationRuntime.kt`, `DikcizLuaRuntime.kt` | Collect Android events, dispatch subscriptions, enforce policies and execute Lua. |
| `DikcizScriptWidgetBridge.kt`, `DikcizLuaScriptRunStatus.kt` | Resolve page/widget addresses and represent persisted script outcomes. |
| `DikcizDeviceAccess.kt`, `DikcizPrivilegedShell.kt`, `DikcizAccessibilityAutomationService.kt` | Report Android access, run bounded shell commands and implement cross-app Accessibility inspection and actions. |
| `DikcizLogger.kt`, `DikcizScriptFailureNotifier.kt` | Write bounded, redacted logs and report script failures through Android notifications. |

Notification, media, clipboard, SMS, Health Connect and Device Administration integrations have dedicated classes beside these files. Their permission and event contracts belong in the [configuration guide](../../../docs/dikciz-configuration.md) and [scripting tutorial](../../../docs/scripting-tutorial.md).

## Boundaries and invariants

Keep product changes in this source set or a Dikciz-specific combined source set. Shared Fossify source sets remain replaceable. [The Gradle integration](../../build.gradle.kts) selects the Dikciz application ID and derives its version from the root `VERSION`; the [Fossify overlays](../../../.fossify/README.md) carry those changes across source refreshes.

`HomeConfigStore` validates owned fields and public paths before saving, preserves unrelated root namespaces and sibling objects under `launcher`, and writes documents with `AtomicFile`. Widget IDs are globally unique without regard to case; page positions are unique and selected/home page IDs must resolve. The store replaces an older bundled starter only while the saved starter remains pristine.

Pages use a fixed native grid and transparent canvases over one launcher background. Placement and grid changes must respect bounds and collisions. Locks prevent editing while preserving widget actions and navigation. Style parsing belongs in `DikcizStyleCodec`, so editors and renderers use the same rules.

Both remote listeners bind to device loopback and execute through the same command target. Keep human accessibility labels readable and machine identities in the semantic snapshot. The [automation-control guide](../../../docs/automation-control.md) owns commands, events, authentication, origin checks and limits. Debug-only event fixtures live in [the combined debug source set](../dikcizDebug/README.md); the control planes remain in this release source set.

The [flavour JVM tests](../testDikciz/README.md) cover grid rules. The [live control suite](../../../tests/live_control/README.md) covers Android UI, persistence and public control behavior.
