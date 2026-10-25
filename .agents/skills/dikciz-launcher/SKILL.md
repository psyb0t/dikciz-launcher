---
name: "dikciz-launcher"
description: "Install and operate Dikciz Launcher on an owner-selected Android device. Use when controlling its home, HTML widgets, Lua, Android data, sensors, camera, audio, speech, Bluetooth, NFC, USB, Wi-Fi, files, APKs, shell sessions or command jobs through MCP, HTTP or WebSocket. Source builds use the Android Lab skill."
homepage: "https://github.com/psyb0t/dikciz-launcher"
user-invocable: true
metadata:
  openclaw:
    requires:
      bins: [python3]
permissions:
  network: "Connect only to the owner-selected Dikciz phone endpoint. Requested SMS, USSD, weather, speech and model-download operations can send data to their documented recipients or providers. Assistant conversations send messages and requested phone data to their configured model provider."
  shell: "Run the documented controller or explicitly requested ADB installation. Device shell commands require explicit task scope and remain subject to Android access."
  filesystem: "Read supplied APKs and task inputs; save requested screenshots, transfers and results in the task workspace. Keep bearer credentials in the secret environment."
---

# Dikciz launcher

Use this skill to install and operate Dikciz, not to develop its Android source or manage Android Lab.

## Security & safety

Operate only the selected owner-controlled device and requested resources. Reset, uninstall, SMS, file changes, privileged commands and permission changes require the owner's requested scope. A connection request alone does not authorize them. Use the configured secret environment for bearer credentials; never include them in prompts, command arguments or tracked files. Native commands use Android permissions; root shell is separate and defaults to root unless `root: false` is supplied.

## When to use

Install an existing APK or operate the launcher, files, apps, Android data, sensors, speech and hardware through its discovered APIs. Use direct native commands before UI tapping when they cover the task. The phone runs the MCP server; the client supplies its reachable `/mcp` URL and optional bearer credential.

## When NOT to use

Source edits and builds use the separate Dikciz Android Lab skill. This skill does not grant Android permissions, bypass the lock screen, make private endpoints public or imply unrestricted system access.

## Install or connect

Read [references/setup.md](references/setup.md) for APK installation, Android access, port forwarding, and connection checks. Take the APK and target device from the user. If either is missing or ambiguous, ask. Do not build an APK or start an emulator just to install one.

For an already connected launcher, discover its live contract before sending commands:

```bash
python3 scripts/dikciz-control.py websocket controlStatus --port 19101 --pretty
python3 scripts/dikciz-control.py websocket automationStatus --port 19101 --pretty
python3 scripts/dikciz-control.py mcp-tools --port 19102 --pretty
```

These examples use the repository's client and the forwarding ports in the setup reference. Use the actual client path, host and forwarded ports for the user's installation. A configured MCP client can connect directly to the forwarded `/mcp` endpoint instead.

## Use the launcher

Read [references/capabilities.md](references/capabilities.md) to choose among the supported features. It catalogs the launcher, configuration, event sources, actions, HTML bridge and remote controls, with links to their exact schemas.

- For Home selection, pages, app shortcuts, provider widgets, themes, and the native settings UI, read the [user guide](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/user-guide.md).
- For saved configuration, pages and widget files beneath `/sdcard/Dikciz/`, read the [configuration guide](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/dikciz-configuration.md) before editing them.
- For HTML bridge calls, Lua scripts, events, and automation policies, read the [scripting tutorial](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/scripting-tutorial.md).
- For WebSocket envelopes, MCP tools, semantic UI actions, screenshots, and device commands, read the [automation-control guide](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/automation-control.md). Discover the running build's names, fields and limits through `controlStatus` and `tools/list` rather than inventing them.

For arbitrary file browsing, binary upload/download, APK installation and shell output, read the [files, APKs and shell guide](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/files-and-apks.md). HTTP JSON commands share the MCP listener at `/v1/automation`; they require no MCP session. File APIs use Android permissions granted to Dikciz. An accepted installer session is not proof of successful installation; poll its final status.

Start with `snapshot`, `homeGet`, `configGet`, or the matching discovered MCP read tool. Use returned semantic IDs and page/widget addresses for actions. Read a fresh snapshot after an action to verify the change. Android accessibility actions require the phone owner to enable Dikciz's Accessibility service; ordinary launcher controls do not require it.

The bundled home has four pages: Home combines time/weather, app icons ranked by optional Usage access, and native Phone/Camera/Settings/store shortcuts; Tools has agenda and voice notes; Device has live battery and resource gauges, process CPU history and per-core data where Android exposes it. Home torch and speech demonstrations start only on a tap. Native app/group dragging requires a stationary one-second hold; release after the hold opens edit actions. Updated Most used documents subscribe to packageChanged for immediate removal. widgetGet exposes HTML eventSubscriptions, and JSON patchWidget replaces that array through the saved-configuration validator. appCatalogue reports nullable foregroundMilliseconds and usage availability; deviceState reports bounded per-core fields and an explicit non-requestable system CPU restriction. Native torch `durationMilliseconds` from 1 to 5000 bounds an on request independently of JS timers. Customized homes are preserved; untouched starters may refresh on update. Recording starts only on a tap and saves under its widget directory; deleting that widget removes its saved audio. Long press HTML content or its top handle for whole-document controls. Edit exposes HTML, CSS, JavaScript and state. Complete WebView templates are available in the HTML widget library, including system metrics and BTC/USD with economic news. Goblin adds manual speech, finite local alien audio, timed torch pulses and visible-page weather checks. Six Lua examples start disabled, including London entry, outdoor heat and GPS speed speech rules; a script dashboard can be added through the picker. The native picker can add third-party provider widgets. Lua runs as automation scripts, not as a widget type. Android permissions, special access and root remain device-controlled.

## On-device assistant

Use discovered `dikciz_chat` (native `chatControl`) for shared provider connections, API model discovery, per-conversation model/token settings, messages, stop, event subscriptions and panel show/hide/fullscreen. Read [embedded chat](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/embedded-chat.md), or fetch `dikciz://docs/guides/chat`, `chat-context` and `chat-events` under the same guides prefix. Add a connection once, discover its models, then configure each conversation with independent settings. Read them with the settings action. The native chat exposes a model dropdown and Model settings. API keys are write-only; never place real keys in prompts or command arguments. Queued sends and requested visibility changes require status/transcript verification. Event rules can incur provider usage while hidden; configure only the requested automation and clear rules when finished.

## Complete capability reference

Read [operations](references/operations.md) for files, notification management and durable jobs, [capabilities](references/capabilities.md) for the inventory, [hardware and speech](references/hardware-and-speech.md) for native operation choices, and [setup](references/setup.md) for connection and package identity. The public [documentation index](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/index.md), [command reference](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/command-reference.md) and [coverage table](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/capability-coverage.md) cover accepted fields and implemented/remaining features. The installed server can serve short resources one section at a time. Read the [typed actions](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/typed-actions.md) for action fields and policy names, and [Android access](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/android-access.md) for feature grants and foreground restrictions. New commands require a build that advertises them; do not infer availability from the latest documentation alone.

## Boundaries and completion

An installation or connection request does not authorize resetting the home, uninstalling apps, replacing all configuration, enabling device administration, or running privileged shell commands. Explain the effect and obtain explicit approval for those operations. `shell` requests root by default; set `root: false` when app-sandbox execution is intended. Do not fall back to root after a denied action.

Preserve `/sdcard/Dikciz/` and the user's existing home. Keep bearer passwords in the user's secret environment, never in command arguments or public files. Forward only the selected device's ports to host loopback.

Finish after the requested installation or action is verified on that device. A queued automation event is not proof its actions succeeded; check script status and logs. Report access denials and connection failures instead of claiming completion.

Embedded chat supports per-conversation `settings:{"readOnly":true}`. Discovery hides mutating tools and the trusted executor flag blocks direct writes and permission prompts with `read_only_violation`. Tool arguments cannot disable it. Chat history and logs still save; other owner entrypoints retain their configured access. Read [embedded chat](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/embedded-chat.md) for the full contract.
