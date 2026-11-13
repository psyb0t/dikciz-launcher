# Dikciz Launcher

## Product direction

Dikciz is for developers and tinkerers. It is an intentionally hackable
launcher, not a locked-down consumer product. Do not remove useful control,
introspection, scripting, or local automation because a conventional launcher
would hide it.

The release APK is the development-friendly build. Do not put the automation
surface behind a debug-only source set, build flag, hidden setting, account,
remote service, or proprietary tool.

The Android lab always runs the current `dikcizDebug` package
(`eu.psyb0t.dikciz.launcher.debug`) for normal use, reset, and focused tests.
It is the one manually inspectable Dikciz build and must remain debuggable.
Fossify Core is retained only as the upstream source base and build check. It
is never installed as a second launcher for normal Dikciz testing.

## Automation control plane

Every released Dikciz build ships documented, versioned local control planes:
a persistent WebSocket for commands and events, plus Streamable HTTP MCP for
tool calls. They share one command target, so a user can forward either plane
with ADB and drive an emulator or real phone through the same launcher APIs.
Owners can also configure a private-network listener without ADB or USB.
HTTP JSON commands use the same executor and authorization as MCP and WebSocket.

The interface exposes:

- a complete semantic UI snapshot with stable automation IDs, Android resource
  IDs, accessible names, roles, state, bounds, viewport, and current screen;
- page and block identities that do not change when text, locale, or layout
  changes;
- scroll containers with current offset, extent, range, visible item range,
  and `scrollTo` by semantic ID, so automation never guesses swipe distance;
- persistent WebSocket events for screen changes, node changes, scroll
  changes, dialogs, lifecycle changes, and command completion;
- commands and MCP tools for semantic UI inspection and interaction, complete
  configuration get, replacement, seed, reset, bounded wait, app launch,
  scrolling, screenshot, UI dump, diagnostics, app-sandbox shell commands,
  and Android intents;
- structured request IDs, protocol version negotiation, exact errors, bounded
  request sizes, and backpressure so a broken client cannot jam the UI thread.

It is valid for a person with local device access or an ADB tunnel to control
the launcher. The control plane must not bind a device LAN or internet address
by default. It listens locally on the device so users decide when and where to
forward it.

App-sandbox shell commands are deliberately local and explicit, but are not
privileged `adb shell` commands. Android continues to enforce the launcher's
declared permissions for activity and broadcast intents.

The real accessibility tree stays useful to TalkBack and normal UI Automator.
Never turn spoken labels into opaque machine IDs. Machine-stable IDs belong in
the control-plane schema, while Android resource IDs and human labels remain
clear and stable enough for ordinary UI automation.

Every automation-visible product change updates both protocol contracts, the
Android lab driver, and end-to-end tests. Release builds prove that the WebSocket
and MCP, ADB forwarding, persistent event stream, screenshots, UI dumps, and
semantic scrolling work on the isolated emulator. Physical-device verification
is outside this lab and requires a separate, explicitly authorized workflow.

## Physical-device state

During normal development, use only the shared lab emulator. Do not run
incremental builds, focused selectors, inspections, or permission handoffs on
the configured physical phone. Physical-device work is a final development-stage
acceptance pass only, and requires an explicit current user request for that
final pass. Never treat an attached phone as permission to use it.

When the user authorizes a configured physical phone, leave it on the latest
fresh Dikciz build and its current clean configuration. Never back up, restore,
or reapply older Dikciz configuration, package data, Android Home role, or any
other launcher state after an install or test. Physical-device verification
proves the latest launcher state. It is never a rollback workflow.

Never reboot the configured physical phone. It can be lock-screen protected.
When an explicitly authorized physical run supplies
`DIKCIZ_TEST_UNLOCK_PASSWORD` through ignored local environment, the controller
must check keyguard state before entering it. If the phone masks that state, it
must first open Android Home so no unrelated text field can receive the
credential. It must never write or log the credential. For clean Dikciz testing,
uninstall only the fixed Dikciz debug package, remove only `/sdcard/Dikciz` and
the fixed stale test-backup directory, then install the current build.

## Fork boundary

This directory always keeps a complete Fossify Launcher source snapshot as the
maintained Android base. `.fossify/release-marker.txt` identifies the imported
Fossify release. It is generated upstream and must not be hand-edited. Update
the source fork by replacing upstream-owned files from a newer Fossify release
and carrying its release marker with that source.

Treat `app/src/main/`, `app/src/debug/`, `app/src/foss/`, `app/src/gplay/`,
upstream Gradle files, resources, translations, and metadata as read-only
during normal work. Do not add, edit, delete, rename, reformat, or generate
files there. They carry the general launcher behavior and Android-version
compatibility that Dikciz must not maintain as a parallel stack. Replacing
those files is allowed only during an explicitly requested Fossify source
refresh, using the selected upstream source without hand edits.

Keep Dikciz product code additive under `app/src/dikciz/` and combined variant
source sets such as `app/src/dikcizDebug/`. Fossify's fixed grid and database
are reference code, not a foundation for Dikciz pages. A Dikciz override must
live in a Dikciz-owned source set, never in a shared Fossify source set. The
only non-documentation edits outside those source sets are narrow build and
manifest integration points explicitly required to register the Dikciz
flavour. Stop and ask before changing any other upstream-owned file.

Project README files, `docs/`, and local `AGENTS.md` guidance are maintained for
this fork and may be updated when their documented behavior changes.

Every Fossify source refresh must build and test the upstream-compatible core
flavour, build Dikciz, and run focused live Dikciz device tests. Resolve
integration failures at the narrow flavour boundary. Do not fork upstream
platform code into `app/src/dikciz/` as a shortcut.

Run this repository's source compare and dry run before a refresh. The actual
replacement requires an explicitly requested source refresh and
`DIKCIZ_UPSTREAM_REFRESH=1`. It stages the selected workspace-local Fossify
tree, preserves only Dikciz-owned paths and documented local fork files, applies
the reviewed flavour and wrapper patches with zero fuzz, and verifies a backup
under `.android-lab/` before it replaces the live source. A patch conflict or
path collision is a stop condition, never a reason to hand-edit imported
Fossify source.

## Current architecture

- `HomeConfig.kt` owns the in-memory home, page, widget, style, lock, and limit
  model.
- `HomeConfigStore.kt` owns public tree creation, strict parsing, namespace
  preservation, validation, atomic saves, and recovery.
- `DikcizStyleCodec.kt` is the single JSON boundary for widget styles and font
  selections. Do not duplicate its parsing rules in activities or renderers.
- `DikcizStyleRenderer.kt` resolves defaults and local overrides into Android
  drawables and typefaces. `DikcizLauncherBackground.kt` owns the one shared
  background behind all transparent page canvases.
- `DikcizStyleEditor.kt` owns native appearance, color, numeric, and font
  controls. Keep those controls usable through accessibility and the semantic
  automation snapshot.
- `DikcizTheme.kt` owns theme discovery, validation, installation, application,
  and clean, modified, or missing state.
- `DikcizHomeActivity.kt` coordinates the UI, configuration, provider host,
  settings, semantic snapshot, WebSocket, and MCP. Move reusable parsing,
  rendering, editing, or theme logic into the focused classes above.

## Configuration invariants

The editable root is `/sdcard/Dikciz/config.json`. It references
`pages/<page-id>/page.json` and
`pages/<page-id>/widgets/<widget-id>.json`. Themes, fonts, wallpapers, and logs
live in their named sibling directories. Treat every public file as untrusted
input. Reject traversal, unsupported extensions, oversized content, unknown
owned fields, malformed values, and incomplete themes before changing the
running home.

`limits`, `logging`, and `launcher.home` are strict Dikciz-owned objects. Keep
unknown root namespaces and unknown sibling objects under `launcher` intact on
every native edit. A failed parse or save must leave the current UI and saved
document unchanged.

Widget IDs are stable and globally unique across every page without regard to
case. Page positions are unique. `homePageId` and `selectedPageId` must
identify existing pages.
Pages are transparent and never own a style. Widget appearance resolves from a
selected theme, then `styleDefaults.widget`, then a field-level widget override.
Themes are public authoritative files. Configuration stores only
`selectedThemeId`, never a copied theme payload.

Locks block editing, not normal widget actions or page navigation. Keep human
accessibility labels readable. Stable machine IDs belong in the semantic
snapshot and protocol schema.

## Documentation and verification

Update `docs/dikciz-configuration.md` for every home, theme, style, font, lock,
or persistence contract change. Update `docs/automation-control.md` for every
semantic snapshot, command, event, or MCP change.

Choose verification that matches the change. Documentation, comments, and
internal code moves without behavior changes need formatting and diff checks,
not an Android suite. A script change needs syntax validation and one direct
behavior path. A visible or protocol behavior change needs its one focused
compile or device selector. Run a broad suite only for an explicit release or
when the user asks for it. Do not turn small edits into repeated full-test runs.

Run from this repository. Missing pinned lab images are built locally by the Android Lab installer from the `ANDROID_LAB_VERSION` tag, and only when `ANDROID_LAB_ACCEPT_ANDROID_LICENSES=yes`; otherwise Make stops. Never pull lab images from a registry or accept the SDK license on the user's behalf. Git metadata and `.android-lab` state remain in place during upstream refresh.

```bash
make build
make app-test
make app-lint
make gradle GRADLE_TASK=:app:assembleDikcizDebug
make dikciz-test
```

The live test must cover persistence and invalid input as well as the real
visible Android UI. Use the shared noVNC phone, saved screenshot, semantic
layout, raw UI dump, and full pytest log to verify visible behavior.


## Project guides

Read `.agents/rules/documentation.md` for public behavior/documentation changes
and `.agents/rules/ui-design.md` for visible interfaces. Source builds use
`.agents/skills/dikciz-android-lab/SKILL.md`; device operation uses
`.agents/skills/dikciz-launcher/SKILL.md`. `CLAUDE.md` and `.claude` are relative
compatibility links to this guide and `.agents`.

`docs/custom-apps.md` owns app/runtime boundaries. `docs/assistant-library.md`
defines SD-card skills, named agents and rules. Preserve inherited read-only
and tool restrictions in discovery and execution. `docs/backups.md` owns app
and assistant snapshots, coordinated rollback and timer review. Imported
pending work must never replay automatically.

`VERSION` is the canonical version; the source-controlled Codex plugin
manifest moves with it. `make version` prints it. Main pushes run build, lint
and tooling checks. Annotated `vX.Y.Z` tags wait for that commit's main checks
before publishing the signed APK. See `docs/source-development.md`.
