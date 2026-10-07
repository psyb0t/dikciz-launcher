# Dikciz Launcher

[![CI](https://github.com/psyb0t/dikciz-launcher/actions/workflows/pipeline.yml/badge.svg?branch=main)](https://github.com/psyb0t/dikciz-launcher/actions/workflows/pipeline.yml)
[![version](https://raw.githubusercontent.com/psyb0t/dikciz-launcher/badges/version.svg)](https://github.com/psyb0t/dikciz-launcher/releases)
[![license](https://raw.githubusercontent.com/psyb0t/dikciz-launcher/badges/license.svg)](LICENSE)

Dikciz is a programmable Android launcher for people who want to inspect,
modify, and automate their own phones. It keeps Fossify Launcher as an upstream
base and adds Dikciz in an isolated product flavour.

The shipped development-friendly build is `dikcizDebug`. It keeps the local
control planes, public configuration, logs, HTML widget bridge, and automation
surface available. Fossify Core remains a source-compatibility check. It is not
the normal launcher installed by the lab.

## Contents

- [Run it](#run-it)
- [What the home supports](#what-the-home-supports)
- [Navigate and manage the home](#navigate-and-manage-the-home)
- [Configure it](#configure-it)
- [Inspect and control it remotely](#inspect-and-control-it-remotely)
- [Logs and failure reporting](#logs-and-failure-reporting)
- [Fossify source updates](#fossify-source-updates)
- [Verification](#verification)
- [Upstream](#upstream)

## Run it

Clone `https://github.com/psyb0t/dikciz-launcher.git` and run these commands from its checkout. The Makefile requires locally built `android-lab:0.13.0` and `android-lab:0.13.0-emulator-api36` images. Build them once using [Android Lab's local-build instructions](https://github.com/psyb0t/android-lab#build-the-images-locally). If an image is missing, Make stops with that link instead of pulling or rebuilding it.

```bash
make build
make dikciz-run
```

Use `make help` for the operation index. `make check-images` checks the two required tags, `make status` shows the stack, and `make stop` stops it without deleting the saved phone. Builds use your host UID/GID. `.android-lab/` holds the Gradle cache, stable signing identity, emulator disk and artifacts; keep it ignored. An existing shared data-directory symlink is supported through explicit bind mounts.

Open the shared emulator at:

```text
http://127.0.0.1:61326/vnc.html?autoconnect=true&resize=scale
```

`make dikciz-run` grants Dikciz file access on the isolated emulator, selects it as Android Home and preserves its existing configuration. `make dikciz-storage-access` grants the same access without reinstalling or clearing configuration. On a real phone, grant file access through Android's settings instead. `make dikciz-reset`
uninstalls only Dikciz, clears `/sdcard/Dikciz`, installs the current build,
and starts the bundled starter home.

## What the home supports

- Two-axis native pages. A page is always a native grid.
- A page is one fixed viewport and never grows. A new widget takes the first
  free cell, reading left to right and then down, and a page with no free cell
  refuses the add.
- Resizable HTML widgets with local HTML, CSS, JavaScript, state, native long
  press actions, and content-fitted height.
- Native Android app shortcuts and app groups.
- Native third-party Android AppWidget provider cards. They keep their own
  `AppWidgetHostView` and are never wrapped in a WebView.
- A native script dashboard that lists scripts, status, last run, logs, and
  enable, disable, and edit controls.
- Themes, fonts, wallpaper, card style, locks, and one Android Home
  destination page.

Fresh configuration demonstrates the surface without legacy text, toggle, or
Lua widget types:

| Page | Contents |
| --- | --- |
| `1H1V`, Home | Full-page HTML control deck with clock, diagnostics, lock, notification, and notification-digest controls. |
| `2H1V`, Notes | Android Settings shortcut, HTML notes card, and script dashboard. |
| `1H2V`, Workbench | HTML card that points to scripts, Android access, shell, and public files. |

Lua is not a widget type. It runs as a script from the public scripts tree.

## Navigate and manage the home

Two icon-only buttons sit beside the horizontal page rail, outside page content.
The Dikciz mark opens four native routes: the app drawer, add to page, manage
page, and Dikciz settings. An upward swipe that begins on the horizontal rail
opens the same app drawer.

The magnifier beside the Dikciz mark opens page search. It lists every page as
`Name (address)`, for example `Notes (2H1V)`, filters on either half as you
type, and goes to the page you tap. A page is addressed by its `1H1V`
coordinate everywhere else in the product, so the name is what this search is
for. Rename a page from manage page.

The app drawer is native, not a WebView. Search filters installed launchable
apps by label or package name. Tap a result to launch it. Its overflow button,
or a long press on the result, opens its actions: add shortcut, Android app
info, uninstall, and force stop. Android owns app-info and uninstall
confirmation. Force stop is available only when the device grants root. It
never falls back to Dikciz's ordinary app shell.

Adding a shortcut places a native activity tile in the first free cell of the
selected page, reading left to right and then down. A page with no free cell
refuses the add. A tap anywhere on the tile launches the app, and a drag from
anywhere on it moves the tile. Its icon is the largest square the cell allows,
centred with its label. The
[configuration guide](docs/dikciz-configuration.md#native-app-shortcuts-and-app-drawer)
defines its saved fields, dimensions, and display styles.

## Configure it

Every user-editable file lives below `/sdcard/Dikciz/`. `config.json` is the
root. Pages, widgets, scripts, automation policies, themes, fonts, wallpaper,
widget-library packages, bearer-auth configuration, and logs have their own
named paths. Dikciz watches valid edits and applies them without an app restart.

Read the [configuration guide](docs/dikciz-configuration.md) for the full tree,
widget schema, script layout, theme format, recovery behavior, logging, and
Android access setup.

For APK installation, Android Home setup, and native launcher tasks, use the [task-based user guide](docs/user-guide.md). For events, Lua, HTML widgets, state, and typed actions, use the [scripting tutorial](docs/scripting-tutorial.md). The [automation-control guide](docs/automation-control.md) owns the remote protocol.

HTML widgets have `window.dikciz`. They can call the same local commands as
WebSocket and MCP clients, dispatch typed automation actions, read and patch
their own state through `selfWidget`, read pages and widgets through
`dikciz.pages()`, patch another HTML widget's named persistent state through
`dikciz.patchWidgetState(address, values)`, publish custom events, and
subscribe to native or custom events while they are rendered. State patches
update the existing document. They do not reload the WebView.

Lua scripts use the same page and widget addresses, such as
`1H1V-command-deck`, for cross-widget work. They can receive typed Android and
custom events, update their own state, patch HTML widgets, select pages, launch
apps, post notifications, control permitted media sessions, set media volume,
dispatch explicit Android intents, request screen lock, and emit custom events.
Each action must appear in the script's public automation policy.

The Android manifest declares a wide access surface. The phone still owns the
final runtime, role, special-access, Device Administration, and root decisions.
Open **Settings**, then **Automation**, then **Set up full device access** to
see the current status and open the Android system panels needed by enabled
automation.

Cross-app Android UI inspection and action use Dikciz's Accessibility service.
The owner enables it in Android Accessibility settings. Once enabled, local
WebSocket and MCP clients can inspect a bounded active-window tree and invoke
supported node actions. Lua receives the bounded active window as
`context.accessibility` on `accessibilityWindow`. HTML reads it with
`dikciz.command("accessibilitySnapshot")`. Both can send the same typed
`accessibilityAction`, `accessibilityGesture`, and
`accessibilityGlobalAction` records through the standard action runtime. The
[automation-control guide](docs/automation-control.md#cross-app-android-ui)
defines the current commands and limits.

## Inspect and control it remotely

Dikciz exposes versioned device-loopback listeners:

| Interface | Device endpoint | Host endpoint after `make dikciz-run` |
| --- | --- | --- |
| WebSocket | `ws://127.0.0.1:19001/v1/automation` | `ws://127.0.0.1:19101/v1/automation` |
| Streamable HTTP MCP | `http://127.0.0.1:19002/mcp` | `http://127.0.0.1:19102/mcp` |

They expose the same command target. It covers configuration, semantic UI
snapshots and interaction, pages, widgets, screenshots, UI dumps, diagnostics,
script logs, app catalogue and actions, Android intents, and shell commands.
`appCatalogue` lists launchable applications. `appAction` launches, creates a
native shortcut, opens Android app info, requests uninstall, or force stops one
component. Shell commands request `su` by default. Set `root: false` to run as
Dikciz's Android app user. Root availability and approval come from the device.

The phone can require a bearer password for both forwarded control planes. Set,
replace, clear, enable, or disable it in Dikciz Settings. The public config
stores a bcrypt verifier, never the plaintext password.

Use the supplied client after the lab creates a forward:

`make control` runs the client in the tooling image with host networking so it can reach the loopback ADB forwards. It does not mount the Docker socket. `CONTROL_ARGS` is a trusted shell argument list, not input from remote users. If authentication is enabled, export `DIKCIZ_REMOTE_BEARER_TOKEN` from your ignored local environment; never place it in a command argument.

```bash
make control CONTROL_ARGS='websocket controlStatus --port 19101 --pretty'
make control CONTROL_ARGS='mcp-tools --port 19102 --pretty'
make control CONTROL_ARGS='websocket scriptLogs --port 19101 --pretty'
```

The [automation-control guide](docs/automation-control.md) owns request
envelopes, discovery, semantic IDs, MCP tools, HTML bridge calls, and limits.

## Logs and failure reporting

Dikciz writes bounded JSON Lines files below `/sdcard/Dikciz/logs/`. The native
script dashboard, Settings log viewer, WebSocket `scriptLogs`, and MCP
`dikciz_script_logs` show the same safe record projection for Lua scripts,
HTML-widget diagnostics, and launcher components. Component records use their
component name where a script ID would normally appear. HTML console warnings
and errors are tagged with the widget ID. Lua compile and handler failures keep
their stable status and add a bounded, redacted source-location `diagnostic` to
their matching log record. Credential-shaped console values are redacted before
they reach the file or either control plane. A failed Lua run or
rejected background action can post an Android notification. Logging config
controls the notification and its per-script quiet period.

## Fossify source updates

The complete Fossify source stays in this directory. Its upstream-owned source
sets, Gradle build, resources, translations, and metadata remain replaceable.
Dikciz code belongs under `app/src/dikciz/` and combined source sets such as
`app/src/dikcizDebug/`.

Use this repository's checked update route:

```bash
make dikciz-upstream-compare DIKCIZ_UPSTREAM_DIR=path/to/fossify-source
make dikciz-upstream-refresh-dry-run DIKCIZ_UPSTREAM_DIR=path/to/fossify-source
DIKCIZ_UPSTREAM_REFRESH=1 make dikciz-upstream-refresh \
  DIKCIZ_UPSTREAM_DIR=path/to/fossify-source
```

The workflow stages the selected source, verifies a backup, preserves
Dikciz-owned paths, and stops on a collision or patch failure. After a refresh,
build the upstream-compatible core flavour, build Dikciz, then run the focused
live controls affected by the update.

Comparison covers source only. It ignores disposable Gradle state in `.gradle`,
`.kotlin`, and build directories, so local build output cannot disguise or
block a source refresh.

The replacement command requires a selected Fossify release marker newer than
the checked-in marker.

Dikciz does not publish through Google Play. The retained
[`fastlane/README.md`](fastlane/README.md) is auto-generated Fossify upstream
reference material, not a Dikciz install or release route.

## Verification

```bash
make build
make dikciz-test-existing-apk DIKCIZ_TEST_SELECTOR=test_native_page_hosts_individual_html_and_provider_widgets
make dikciz-test-script-diagnostics
make dikciz-test-provider-actions
make dikciz-test-accessibility
make dikciz-test-accessibility-third-party
make dikciz-test-app-drawer-entry
make dikciz-test-app-shortcut
make dikciz-test-launcher-control
```

Choose the narrow target that covers the change. `make dikciz-test` is the
broad shared-emulator UI suite. It resets the same phone to a clean installed
Dikciz build, seeds its HTML-only test fixture, and leaves screenshots, UI
dumps, layouts, and the complete pytest log in `.android-lab/shared/artifacts/`. After pytest returns, fails, or is interrupted, it resets only
Dikciz and `/sdcard/Dikciz` to the bundled starter home. The fixture is never
left visible for manual inspection.

## Upstream

Dikciz is based on [Fossify Launcher](https://github.com/FossifyOrg/Launcher)
and is distributed under the [GNU General Public License v3.0](LICENSE).
