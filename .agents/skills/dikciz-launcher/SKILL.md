---
name: "dikciz-launcher"
description: "Install a Dikciz Launcher APK with ADB and use its home screen, configuration, HTML widgets, Lua automation, WebSocket controls, and MCP tools on the user's selected Android device."
---

# Dikciz Launcher

Use this skill to install and operate Dikciz, not to develop its Android source or manage Android Lab.

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

Start with `snapshot`, `homeGet`, `configGet`, or the matching discovered MCP read tool. Use returned semantic IDs and page/widget addresses for actions. Read a fresh snapshot after an action to verify the change. Android accessibility actions require the phone owner to enable Dikciz's Accessibility service; ordinary launcher controls do not require it.

The bundled home has pages, HTML widgets, native shortcuts, provider widgets, and a script dashboard. Lua runs as automation scripts, not as a widget type. Android permissions, special access and root remain device-controlled.

## Boundaries and completion

An installation or connection request does not authorize resetting the home, uninstalling apps, replacing all configuration, enabling device administration, or running privileged shell commands. Explain the effect and obtain explicit approval for those operations. `shell` requests root by default; set `root: false` when app-sandbox execution is intended. Do not fall back to root after a denied action.

Preserve `/sdcard/Dikciz/` and the user's existing home. Keep bearer passwords in the user's secret environment, never in command arguments or public files. Forward only the selected device's ports to host loopback.

Finish after the requested installation or action is verified on that device. A queued automation event is not proof its actions succeeded; check script status and logs. Report access denials and connection failures instead of claiming completion.
