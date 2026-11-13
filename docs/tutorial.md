---
title: "Tutorial"
description: "Install Dikciz and follow illustrated exercises for home editing, AI chat, remote commands, event scripts, hardware and Android SDK discovery."
permalink: /tutorial/
nav_order: 2
has_children: true
---

# Dikciz tutorial

Build a home you can edit, connect an AI that can use the phone, and make HTML widgets, Lua or headless JavaScript scripts and complete custom apps react to events. This walkthrough starts with the signed APK and ends with direct Android SDK discovery. Follow it in order for a new installation, or jump to the part you need.

The examples inspect state and use a local custom-event pipeline. Messaging, calls, recordings and device changes have separate opt-in exercises. Exact accepted fields belong to the installed tool schemas and the [command reference](command-reference.md).

Screenshots use a private Android emulator. The starter, native menus, custom apps, provider forms, templates and command-result illustrations were captured from the current development build. The populated starter uses a configured London location, live weather, sample calendar entries and an emulator recording. Chat replies and model lists use a local demonstration API with example model names; the native chat, database and phone tools are real. Provider forms also show the built-in OpenAI and Anthropic host templates. Command-result screens use a temporary HTML inspector to display actual API responses. Click an image for the full capture.

For menus, forms and recovery dialogs, use [Screens and guided workflows](screens.md). It includes app Recents, bundle editing, conversation management, provider/model setup, access tokens, backups and companion forms.

## Contents

1. [Install and grant access](#1-install-and-grant-access)
2. [Explore the starter home](#2-explore-the-starter-home)
3. [Build and customize a page](#3-build-and-customize-a-page)
4. [Connect the on-device assistant](#4-connect-the-on-device-assistant)
5. [Connect without USB](#5-connect-without-usb)
6. [Build an event pipeline](#6-build-an-event-pipeline)
7. [Use Android data and hardware](#7-use-android-data-and-hardware)
8. [Manage files, installs and long-running work](#8-manage-files-installs-and-long-running-work)
9. [Discover public Android SDK calls](#9-discover-public-android-sdk-calls)
10. [Check results and recover](#10-check-results-and-recover)
11. [Complete template gallery](#complete-template-gallery)

## 1. Install and grant access

Download the signed APK from [GitHub Releases](https://github.com/psyb0t/dikciz-launcher/releases/latest). For ongoing updates, add `https://github.com/psyb0t/dikciz-launcher` to [Obtainium](https://github.com/ImranR98/Obtainium). Open Dikciz, choose it as the Android Home app, tap **Grant file access**, allow the requested shared-storage access and return.

Dikciz creates `/sdcard/Dikciz/`. The public home is editable files: root configuration, pages, widget documents and state, scripts, automation policies, libraries, themes and logs. Chat history and provider keys live separately in private app storage.

Open **Settings**, **Automation**, **Set up full device access**. This is a screen you can return to after Android opens another settings screen. Grant the capabilities you intend to use. For cross-app reading and control, enable **Dikciz cross-app automation** in Android Accessibility settings. For most-used app ranking, allow usage access. Location, contacts, notifications, camera and microphone have their own grants.

A permission is not the same as a role, special access or hardware support. Dikciz does not need to become your default SMS app. The [Android access guide](android-access.md) maps each feature to its actual requirements.

**Check:** press Home. Dikciz opens and the starter pages render. Return from an Android permission screen and continue setup without starting the sequence again.

<p><a href="{{ "/images/user-guide/38-device-access-screen.png" | relative_url }}"><img src="{{ "/images/user-guide/38-device-access-screen.png" | relative_url }}" alt="Full device-access setup, with progress and individual Android access steps." width="280"></a><br>Full device-access setup, with progress and individual Android access steps.</p>

<p><a href="{{ "/images/tutorial/permission-contacts-review.png" | relative_url }}"><img src="{{ "/images/tutorial/permission-contacts-review.png" | relative_url }}" alt="A remote request asks the owner to review only the missing contacts access." width="280"></a><br>A remote request asks the owner to review only the missing contacts access.</p>

<p><a href="{{ "/images/tutorial/permission-contacts-android.png" | relative_url }}"><img src="{{ "/images/tutorial/permission-contacts-android.png" | relative_url }}" alt="The owner button opens the actual Android permission prompt. Approval still belongs to the user." width="280"></a><br>The owner button opens the actual Android permission prompt. Approval still belongs to the user.</p>

## 2. Explore the starter home

The bundled home has four pages. Some data needs a grant or an explicit refresh; missing values stay unknown instead of being invented.

| Page | Try this | What it demonstrates |
| --- | --- | --- |
| Home | Read the clock/weather, open an app icon and try a quick action | Time and weather, usage-ranked app icons and a native dock for Phone, Camera, Settings and an installed app store when available |
| Tools | Inspect the agenda and deliberately record a short voice note | Calendar access and a saved recording with its own widget state |
| Device | Read the battery ring, RAM/storage meters and CPU history | Live Android measurements with explicit limits for protected system counters |
| Goblin | Tap speech, alien sound or finite torch pulses, then stop them | JavaScript driving native tools and local audio from an editable document |

Weather can use permitted phone location when no city is set. Check the age and error state of fetched data. Whole-phone CPU counters may be protected even when ordinary permissions are granted; CPU frequency is not CPU utilization.

The library includes nineteen complete HTML templates, including Live sensors, BTC and economic news, app search, notes and combined dashboards. Six Lua examples start disabled: low-battery and charging reminders, lock-on-demand, and the London, heat and speed rules. The [starter catalog](starter-home.md) describes every template, trigger, data source and cleanup behavior.

**Check:** visit all four pages. Try one manual Goblin effect and stop it. Do not enable background rules until you have reviewed their source and triggers.

<p><a href="{{ "/images/user-guide/01-first-launch.png" | relative_url }}"><img src="{{ "/images/user-guide/01-first-launch.png" | relative_url }}" alt="Home with populated weather, app icons and the native dock." width="280"></a><br>Home with populated weather, app icons and the native dock.</p>

<p><a href="{{ "/images/user-guide/03-insights-page.png" | relative_url }}"><img src="{{ "/images/user-guide/03-insights-page.png" | relative_url }}" alt="Tools with sample calendar entries and a saved emulator voice note." width="280"></a><br>Tools with sample calendar entries and a saved emulator voice note.</p>

<p><a href="{{ "/images/user-guide/04-device-page.png" | relative_url }}"><img src="{{ "/images/user-guide/04-device-page.png" | relative_url }}" alt="Device with actual battery, memory, storage and CPU measurements." width="280"></a><br>Device with actual battery, memory, storage and CPU measurements.</p>

<p><a href="{{ "/images/user-guide/05-goblin-page.png" | relative_url }}"><img src="{{ "/images/user-guide/05-goblin-page.png" | relative_url }}" alt="Goblin controls for manual speech, finite light effects and alien sounds." width="280"></a><br>Goblin controls for manual speech, finite light effects and alien sounds.</p>

## 3. Build and customize a page

Tap the Dikciz mark beside the page rail to reach page controls, the app drawer, widgets and Settings. Pages occupy a two-axis map and have stable IDs as well as readable addresses such as `1H1V`. Use the page search control to find one by name or address.

Create a spare page for experiments. Open **Add to this page**. The **Dikciz** category lists complete HTML templates with descriptions. Add **Quick note**, edit it, then add a second copy. Change each note independently.

A template copies the whole HTML, CSS and JavaScript document. Each placed copy keeps independent state. **Save to library** makes the customized source reusable; it does not copy live widget state, and later library changes do not silently overwrite placed copies. An **Empty HTML widget** starts a document from scratch.

Long press the move handle, HTML content or background for the same whole-widget controls. **Edit** exposes the complete HTML, CSS, JavaScript and JSON state. Change an inner section by editing its markup and code. **Delete** removes the entire WebView and its scoped state, never just the DOM section under your finger. See the [HTML editing walkthrough](user-guide.md#edit-an-html-widget).

In **Appearance**, open **Border**, **Widths**, and set **All sides** to 0 to remove the native outline. Set individual edges to 0 too if the widget or theme overrides them. A border drawn by your own HTML must be removed in its CSS. WebViews hide native and CSS scrollbars while preserving scrolling; your document controls overflow and layout.

Saved widget files belong to the exact page/widget instance, so two copies can keep different notes or settings across workspaces. Removing a widget removes its scoped state. Save reusable source or export wanted data before removing an instance. Use stable widget IDs and `selfWidget` in code instead of screen pixels.

Add native app shortcuts or Android provider widgets through the same page controls. Hold an app icon for at least one second to arm dragging; an earlier swipe does not move it. Changing the global grid can shrink full-width widgets and reflow others. Read `evictedWidgets` when changing it programmatically: items that cannot fit are removed from the layout, with their scoped state. Evicting an app shortcut does not uninstall the app.

**Check:** your two notes differ after leaving and returning to the page. Edit one document and confirm the other stays unchanged. See [configuration](dikciz-configuration.md) for the file layout, grid, locks, themes, wallpaper and validation.

### Page and editor screens

<details>
<summary>Show the page, app and widget walkthrough</summary>

<p><a href="{{ "/images/user-guide/06-launcher-controls.png" | relative_url }}"><img src="{{ "/images/user-guide/06-launcher-controls.png" | relative_url }}" alt="Launcher controls open the drawer, widgets, page actions and Settings." width="280"></a><br>Launcher controls open the drawer, widgets, page actions and Settings.</p>

<p><a href="{{ "/images/user-guide/05-create-page.png" | relative_url }}"><img src="{{ "/images/user-guide/05-create-page.png" | relative_url }}" alt="Create a spare page before trying layout changes." width="280"></a><br>Create a spare page before trying layout changes.</p>

<p><a href="{{ "/images/user-guide/20-page-rename.png" | relative_url }}"><img src="{{ "/images/user-guide/20-page-rename.png" | relative_url }}" alt="Give the page a recognizable title." width="280"></a><br>Give the page a recognizable title.</p>

<p><a href="{{ "/images/user-guide/32-page-search-by-name.png" | relative_url }}"><img src="{{ "/images/user-guide/32-page-search-by-name.png" | relative_url }}" alt="Search pages by title or address." width="280"></a><br>Search pages by title or address.</p>

<p><a href="{{ "/images/user-guide/19-page-menu.png" | relative_url }}"><img src="{{ "/images/user-guide/19-page-menu.png" | relative_url }}" alt="Page actions include navigation, layout and editing controls." width="280"></a><br>Page actions include navigation, layout and editing controls.</p>

<p><a href="{{ "/images/user-guide/28-grid-settings.png" | relative_url }}"><img src="{{ "/images/user-guide/28-grid-settings.png" | relative_url }}" alt="The global page grid controls columns, rows and spacing." width="280"></a><br>The global page grid controls columns, rows and spacing.</p>

<p><a href="{{ "/images/user-guide/07-app-drawer.png" | relative_url }}"><img src="{{ "/images/user-guide/07-app-drawer.png" | relative_url }}" alt="Browse installed apps in the drawer." width="280"></a><br>Browse installed apps in the drawer.</p>

<p><a href="{{ "/images/user-guide/08-app-drawer-search.png" | relative_url }}"><img src="{{ "/images/user-guide/08-app-drawer-search.png" | relative_url }}" alt="Filter the app drawer before choosing an app." width="280"></a><br>Filter the app drawer before choosing an app.</p>

<p><a href="{{ "/images/user-guide/09-app-actions.png" | relative_url }}"><img src="{{ "/images/user-guide/09-app-actions.png" | relative_url }}" alt="App actions include launching and adding a native shortcut." width="280"></a><br>App actions include launching and adding a native shortcut.</p>

<p><a href="{{ "/images/user-guide/10-app-group.png" | relative_url }}"><img src="{{ "/images/user-guide/10-app-group.png" | relative_url }}" alt="A real app group after holding an icon before dragging it onto another." width="280"></a><br>A real app group after holding an icon before dragging it onto another.</p>

<p><a href="{{ "/images/user-guide/21-widget-picker.png" | relative_url }}"><img src="{{ "/images/user-guide/21-widget-picker.png" | relative_url }}" alt="Choose the Dikciz library or an Android app widget provider." width="280"></a><br>Choose the Dikciz library or an Android app widget provider.</p>

<p><a href="{{ "/images/user-guide/22-html-package-library.png" | relative_url }}"><img src="{{ "/images/user-guide/22-html-package-library.png" | relative_url }}" alt="The HTML library lists complete documents with descriptions." width="280"></a><br>The HTML library lists complete documents with descriptions.</p>

<p><a href="{{ "/images/user-guide/23-provider-widgets.png" | relative_url }}"><img src="{{ "/images/user-guide/23-provider-widgets.png" | relative_url }}" alt="Android provider widgets use their native binding flow." width="280"></a><br>Android provider widgets use their native binding flow.</p>

<p><a href="{{ "/images/user-guide/11-widget-actions.png" | relative_url }}"><img src="{{ "/images/user-guide/11-widget-actions.png" | relative_url }}" alt="Long press opens whole-widget editing controls." width="280"></a><br>Long press opens whole-widget editing controls.</p>

<p><a href="{{ "/images/user-guide/12-move-grid.png" | relative_url }}"><img src="{{ "/images/user-guide/12-move-grid.png" | relative_url }}" alt="Move an item inside the native page grid." width="280"></a><br>Move an item inside the native page grid.</p>

<p><a href="{{ "/images/user-guide/13-resize-grid.png" | relative_url }}"><img src="{{ "/images/user-guide/13-resize-grid.png" | relative_url }}" alt="Resize using the grid outline and handles." width="280"></a><br>Resize using the grid outline and handles.</p>

<p><a href="{{ "/images/user-guide/16-widget-editor.png" | relative_url }}"><img src="{{ "/images/user-guide/16-widget-editor.png" | relative_url }}" alt="Edit the complete widget source and state." width="280"></a><br>Edit the complete widget source and state.</p>

<p><a href="{{ "/images/user-guide/33-html-saved.png" | relative_url }}"><img src="{{ "/images/user-guide/33-html-saved.png" | relative_url }}" alt="A saved source edit rendered by the WebView." width="280"></a><br>A saved source edit rendered by the WebView.</p>

<p><a href="{{ "/images/user-guide/17-appearance-editor.png" | relative_url }}"><img src="{{ "/images/user-guide/17-appearance-editor.png" | relative_url }}" alt="Appearance controls include native borders, spacing and inherited styles." width="280"></a><br>Appearance controls include native borders, spacing and inherited styles.</p>

<p><a href="{{ "/images/user-guide/15-widget-delete.png" | relative_url }}"><img src="{{ "/images/user-guide/15-widget-delete.png" | relative_url }}" alt="Removing a widget frees its cells and removes its scoped state." width="280"></a><br>Removing a widget frees its cells and removes its scoped state.</p>

<p><a href="{{ "/images/user-guide/24-page-full.png" | relative_url }}"><img src="{{ "/images/user-guide/24-page-full.png" | relative_url }}" alt="A deliberately full page refuses another item." width="280"></a><br>A deliberately full page refuses another item.</p>

</details>

## 4. Connect the on-device assistant

Tap the cheese beside the launcher controls. The chat opens as a half-screen panel; expand it when you need more room.

1. Open the header gear and **Add provider**.
2. Choose **OpenAI**, **Anthropic** or **Custom**. The first two fill the standard host and protocol. Custom accepts your own compatible base URL.
3. Enter the API key and save. Keys are stored separately with Android Keystore encryption and omitted from reads.
4. Create a conversation using that provider. Choose **Chat model** from the API-backed list; refresh it when needed. If discovery cannot list a private model, enter its exact ID under **Tune**.
5. Review the context size, maximum output, reasoning and access setting for this conversation. Several conversations can share one provider connection while using different models and budgets.

The current OpenAI-compatible protocol is streaming Chat Completions; Anthropic-compatible uses streaming Messages. A provider template does not make every model or API feature compatible. Read [provider settings](embedded-chat.md#connect-a-provider) before choosing an endpoint that only supports another protocol.

Known model metadata can fill token limits. Unknown models need manual values. Maximum output is both the provider response cap and a reserve subtracted from total context alongside the safety margin. Choose `drop-oldest` to omit older complete exchanges from outgoing requests, or `summarize` to replace an older prefix with a saved summary. Both keep the full local transcript.

Start with **Read-only phone access** enabled in model settings. Ask the assistant to inspect battery state and explain the result. Read-only mode hides write tools and independently blocks direct write calls in the executor. A model cannot bypass it with an argument. Permission prompts, app launches, shell commands and messaging are writes, so arrange needed grants yourself before this exercise.

For a separate writable exercise, disable read-only while idle and use **Make me a widget** on the spare page. Describe a small note or status widget. Inspect the resulting document and tool results; generation can fail or leave a partial result. **Explain this screen** hides chat, captures the display and accessibility tree, and sends them to an image-capable model. That turn is read-only even in a writable conversation, without changing its saved access setting.

Chat streams Markdown. Scroll up to inspect earlier messages; use **Latest** to resume following output. Hidden work appears in a progress notification. Tap it to return to the exact conversation, or use **Stop** to cancel active and queued work. Completed phone actions remain completed. The quiet **Open chat** notification opens the panel over another app when Android access and notification settings permit it.

An event rule can wake a conversation while chat is closed. Configure one only after testing its trigger and instructions. It can make paid API requests. Stopping a turn does not disable its event rules; clear those separately.

**Check:** send a follow-up question that depends on the previous reply. Confirm continuity, inspect a tool result, then test Stop during a new turn. The [chat guide](embedded-chat.md) covers failures, context recovery, conversations and event rules.

### Chat screens

<p><a href="{{ "/images/tutorial/chat-conversations.png" | relative_url }}"><img src="{{ "/images/tutorial/chat-conversations.png" | relative_url }}" alt="Switch between independent conversations." width="280"></a><br>Switch between independent conversations.</p>

<p><a href="{{ "/images/tutorial/chat-event-rules.png" | relative_url }}"><img src="{{ "/images/tutorial/chat-event-rules.png" | relative_url }}" alt="An event rule can wake this conversation for a scoped read-only task." width="280"></a><br>An event rule can wake this conversation for a scoped read-only task.</p>

<p><a href="{{ "/images/tutorial/chat-phone-tool.png" | relative_url }}"><img src="{{ "/images/tutorial/chat-phone-tool.png" | relative_url }}" alt="The assistant executed a real read-only SDK call through the phone tools." width="280"></a><br>The assistant executed a real read-only SDK call through the phone tools.</p>

<p><a href="{{ "/images/tutorial/chat-tool-result.png" | relative_url }}"><img src="{{ "/images/tutorial/chat-tool-result.png" | relative_url }}" alt="Expand a tool result to inspect the actual response instead of trusting only the reply." width="280"></a><br>Expand a tool result to inspect the actual response instead of trusting only the reply.</p>

<p><a href="{{ "/images/tutorial/chat-provider-settings.png" | relative_url }}"><img src="{{ "/images/tutorial/chat-provider-settings.png" | relative_url }}" alt="The OpenAI template fills its standard API host and protocol." width="280"></a><br>The OpenAI template fills its standard API host and protocol.</p>

<p><a href="{{ "/images/tutorial/chat-provider-templates.png" | relative_url }}"><img src="{{ "/images/tutorial/chat-provider-templates.png" | relative_url }}" alt="Choose OpenAI, Anthropic or Custom before entering connection details." width="280"></a><br>Choose OpenAI, Anthropic or Custom before entering connection details.</p>

<p><a href="{{ "/images/tutorial/chat-anthropic-settings.png" | relative_url }}"><img src="{{ "/images/tutorial/chat-anthropic-settings.png" | relative_url }}" alt="The Anthropic template fills the Messages-compatible provider connection." width="280"></a><br>The Anthropic template fills the Messages-compatible provider connection.</p>

<p><a href="{{ "/images/tutorial/chat-custom-settings.png" | relative_url }}"><img src="{{ "/images/tutorial/chat-custom-settings.png" | relative_url }}" alt="Custom enables your own compatible host and protocol. Replace this example URL with your real server." width="280"></a><br>Custom enables your own compatible host and protocol. Replace this example URL with your real server.</p>

<p><a href="{{ "/images/tutorial/chat-model-dropdown.png" | relative_url }}"><img src="{{ "/images/tutorial/chat-model-dropdown.png" | relative_url }}" alt="Choose a discovered model from the provider catalogue. Names here are demonstration models." width="280"></a><br>Choose a discovered model from the provider catalogue. Names here are demonstration models.</p>

<p><a href="{{ "/images/tutorial/chat-model-settings.png" | relative_url }}"><img src="{{ "/images/tutorial/chat-model-settings.png" | relative_url }}" alt="Set read-only access, total context and the reserved reply/output budget." width="280"></a><br>Set read-only access, total context and the reserved reply/output budget.</p>

<p><a href="{{ "/images/tutorial/chat-sheet-half.png" | relative_url }}"><img src="{{ "/images/tutorial/chat-sheet-half.png" | relative_url }}" alt="Half-screen chat renders streamed Markdown." width="280"></a><br>Half-screen chat renders streamed Markdown.</p>

<p><a href="{{ "/images/tutorial/chat-sheet-full.png" | relative_url }}"><img src="{{ "/images/tutorial/chat-sheet-full.png" | relative_url }}" alt="Expand the same conversation for more reading space." width="280"></a><br>Expand the same conversation for more reading space.</p>

<p><a href="{{ "/images/tutorial/chat-sheet-keyboard.png" | relative_url }}"><img src="{{ "/images/tutorial/chat-sheet-keyboard.png" | relative_url }}" alt="The composer moves above the keyboard and retains its draft." width="280"></a><br>The composer moves above the keyboard and retains its draft.</p>

<p><a href="{{ "/images/tutorial/chat-progress-notification.png" | relative_url }}"><img src="{{ "/images/tutorial/chat-progress-notification.png" | relative_url }}" alt="Background progress offers Stop and a route back to the conversation." width="280"></a><br>Background progress offers Stop and a route back to the conversation.</p>

<p><a href="{{ "/images/tutorial/chat-sheet-empty.png" | relative_url }}"><img src="{{ "/images/tutorial/chat-sheet-empty.png" | relative_url }}" alt="The empty state shows how to start a conversation." width="280"></a><br>The empty state shows how to start a conversation.</p>

### Install skills, rules and a specialist agent

Create `/sdcard/Dikciz/assistant/AGENTS.md` for short owner instructions. Add `skills/inspect-device/SKILL.md` using the [assistant library guide](assistant-library.md), then ask chat for `$inspect-device`. Its next turn loads the skill body; supporting references stay available on demand.

Add `agents/device-reader.md` using the guide's read-only example and ask chat to delegate a device-health check to `device-reader`. Inspect its child conversation transcript. It inherits the parent's access restrictions. Edit a rule and send another turn to use its current instructions.

**Check:** call `assistantLibrary` with `action: "list"` and read a returned relative path. Catalog entries report metadata and a content revision. The guide documents Codex, Claude Code and Hermes directory conventions, fields and limits.

## 5. Connect without USB

Use a reachable private network or Tailscale connection between the controlling computer and phone. Run the client on that computer. Its `127.0.0.1` refers to itself, not the phone.

On the phone:

1. Open **Settings**, **Automation**, **Configure remote bearer authentication**. Set and enable a password.
2. Open **Configure control ports**. Set **Listen address** to `0.0.0.0` for direct IPv4 access and save. The default `127.0.0.1` accepts only phone-local or forwarded connections.
3. Allow computer-to-phone TCP traffic on the chosen ports. Defaults are WebSocket `19001` and MCP/HTTP `19002`.

Listener configuration lives in `control/ports.json` under the public home. Valid address/port changes restart the listeners automatically, including while another app is foreground. `0.0.0.0` is a bind address; clients connect to the actual phone hostname or IP. The listeners supply no TLS themselves, so use a trusted private network or encrypted VPN. Tailscale ping alone does not prove the application ports are reachable.

From a checkout on the controlling computer, load the password into `DIKCIZ_REMOTE_BEARER_TOKEN` through your secret environment. Keep it outside tracked files and command arguments. Replace `phone.example` below with the reachable phone address.

```bash
python3 scripts/dikciz-control.py websocket controlStatus --host phone.example --port 19001 --pretty
python3 scripts/dikciz-control.py http deviceInfo --host phone.example --port 19002 --pretty
python3 scripts/dikciz-control.py mcp-tools --host phone.example --port 19002 --pretty
python3 scripts/dikciz-control.py mcp dikciz_snapshot --host phone.example --port 19002 --pretty
```

| Entrypoint | Address | Use |
| --- | --- | --- |
| MCP | `http://phone.example:19002/mcp` | Tool discovery, typed calls and short documentation resources for agents |
| HTTP | `http://phone.example:19002/v1/automation` | Individual JSON commands and ordinary client integrations |
| WebSocket | `ws://phone.example:19001/v1/automation` | Persistent commands and live events |
| HTML, Lua and JavaScript | Local native bridge | Visible widgets and background event scripts |

These bindings share validation, execution, Android access and outcome semantics. Transport envelopes differ. Discover current names and required arguments instead of guessing their spelling. Search a task with `dikciz_search_query` in collection `docs`, then read the relevant result URI. Collection `tools` returns installed input schemas. The embedded agent exposes `phone_search` directly and uses the same ranked engine for tool discovery. `searchIndex` can also index explicitly supplied owner text in separate persistent collections. See [offline search](search.md) for aliases, spelling correction, pagination and read-only behavior. MCP browsing still starts at `dikciz://docs/index`.

A writable call can opt into owner review with `autoRequestPermissions: true`. For a direct review request, discover `requestDeviceAccess` or MCP `dikciz_request_device_access` and choose the required capability. Read the returned status, review-delivery result and instructions. The user still approves in Android; a review request is not a grant or completed action. Read-only conversations cannot request these prompts.

**Check:** inspect the serving package and version through `deviceInfo`, then read a snapshot over MCP. Both must describe the intended installed instance. Continue with [connection troubleshooting](automation-control.md#connect-without-usb) or [agent setup](agent-integrations.md) if discovery fails.

<p><a href="{{ "/images/user-guide/31-automation-access.png" | relative_url }}"><img src="{{ "/images/user-guide/31-automation-access.png" | relative_url }}" alt="Automation settings collect device access and control-plane configuration." width="280"></a><br>Automation settings collect device access and control-plane configuration.</p>

<p><a href="{{ "/images/user-guide/40-control-ports.png" | relative_url }}"><img src="{{ "/images/user-guide/40-control-ports.png" | relative_url }}" alt="Enter 0.0.0.0 before saving to enable direct IPv4 listening. This capture shows the edit form." width="280"></a><br>Enter 0.0.0.0 before saving to enable direct IPv4 listening. This capture shows the edit form.</p>

## 6. Build an event pipeline

Start with [Build a todo app that reacts to GPS](todo-app-tutorial.md) for the shortest complete app example. **Dikciz > Apps > Travel todo** contains all five editable source files. Its explicit **Watch New York** action creates a native geofence, and its background JavaScript checks off the destination after the matching event. The same list accepts a custom event for another task. The walkthrough covers permissions, source editing, independent copies and stopping monitoring.

<img src="{{ "/images/custom-apps/todo-watching.png" | relative_url }}" alt="Travel todo with an opt-in New York rule" width="280"> <img src="{{ "/images/custom-apps/todo-arrived.png" | relative_url }}" alt="Persisted task completion after a real emulator GPS event" width="280">

For separate widgets and background scripts, continue with the Lua example below. [Headless JavaScript](background-javascript.md) is a second standalone script backend with the same policies, events and command access.

Use the complete [custom-event exercise](scripting-tutorial.md#build-a-complete-custom-event-workflow) to connect your spare HTML widget to a Lua script:

```text
HTML button -> example.pipeline.request -> Lua -> saved widget state
                                              -> example.pipeline.done -> visible HTML update
```

That exercise supplies the HTML, CSS, JavaScript, Lua, policy and subscription documents. Replace its sample target address with your actual widget address. Enable the script, its policy and its subscription, then press the button. Inspect the script log for both the state update and emitted result event.

The widget waits for `dikciz-ready` before using the bridge. It reads `selfWidget.state`, updates saved state through the bridge, and listens for `dikciz-state` without reloading the document. Custom events use the same bus as native sources. External clients can publish them too; discover the installed event-publishing command and its schema before calling it.

HTML receives subscribed events while its page is selected and its WebView exists. Lua and headless JavaScript handle enabled subscriptions in the background. Put work that must survive page changes in a background script or app backend, and visible rendering in JavaScript. Add interval limits and filters for noisy sources such as sensors, location or notifications.

After the custom pipeline works, inspect one of the disabled bundled Lua examples. Start with the low-battery rule before adapting a location or heat rule. Policies authorize script actions, but never grant Android permissions. Trusted script command access includes powerful file and shell operations; review the source before enabling it.

**Check:** publish a request, see the result in the widget and inspect the final script status. Leave and return to the page to verify saved state. Disable the test script/subscription when finished. The [event replay guide](event-replay.md) covers cursors, gaps and reconnecting clients.

<p><a href="{{ "/images/user-guide/25-script-manager.png" | relative_url }}"><img src="{{ "/images/user-guide/25-script-manager.png" | relative_url }}" alt="Review bundled scripts and enable only the ones you want." width="280"></a><br>Review bundled scripts and enable only the ones you want.</p>

<p><a href="{{ "/images/user-guide/26-script-editor.png" | relative_url }}"><img src="{{ "/images/user-guide/26-script-editor.png" | relative_url }}" alt="The native Lua editor exposes source, state and the enable switch." width="280"></a><br>The native Lua editor exposes source, state and the enable switch.</p>

<p><a href="{{ "/images/tutorial/event-running.png" | relative_url }}"><img src="{{ "/images/tutorial/event-running.png" | relative_url }}" alt="The packaged two-input recipe after example.start: Lua and HTML report Running." width="280"></a><br>The packaged two-input recipe after example.start: Lua and HTML report Running.</p>

<p><a href="{{ "/images/tutorial/event-stopped.png" | relative_url }}"><img src="{{ "/images/tutorial/event-stopped.png" | relative_url }}" alt="After example.stop, the same pipeline reports Stopped." width="280"></a><br>After example.stop, the same pipeline reports Stopped.</p>

The two-input screenshots use the [packaged event recipe](mcp-resources.md), which extends the one-button exercise above with separate start/stop inputs.

## 7. Use Android data and hardware

Discover the installed tools for the task, grant its access, call the read operation first and inspect the result. Prefer dedicated commands over screen tapping when a data API exists. For cross-app UI, use a fresh accessibility snapshot and its semantic node IDs; after navigation, read a fresh snapshot rather than reusing stale IDs.

| Task | Exercise and guide |
| --- | --- |
| Contacts and SIMs | Search contacts directly and inspect `simList`. Own numbers can remain unavailable even with the read-number grant. [Native data](native-device-control.md) |
| SMS and calls | For an intentional test, choose an owner-approved recipient and SIM, use tracked SMS with an idempotency key, then inspect sent/delivery status. Calls, call logs and carrier USSD have separate requirements. [Native data](native-device-control.md), [USSD](ussd-control.md) |
| Calendar and alarms | Read occurrences and the next alarm first. Contact/calendar mutations are separate explicit actions. [Native data](native-device-control.md#calendar-occurrences-and-next-alarm) |
| Notifications and media | Inspect active notifications or media sessions. Replying, dismissing, snoozing and playback changes are writes. [Notification management](notification-management.md), [native data](native-device-control.md) |
| Location and sensors | Get a fix with age/accuracy, enumerate actual sensors and subscribe only to needed samples. Stop streams after testing. [Location](location-services.md), [native hardware](native-device-control.md#sensors-camera-microphone-and-speech) |
| Camera | Deliberately capture one JPEG, inspect actual frame metadata and view the output. Bounded video has a separate lifecycle. [Camera](camera-control.md), [native hardware](native-device-control.md) |
| Microphone and sound | Record a short note, stop it, then play it. Live PCM is a managed stream with replay and an owner stop control. [Microphone streams](microphone-streams.md), [audio playback](audio-playback.md) |
| Speech | Speak a short test or recognize microphone speech. Check available recognition languages/models before assuming offline support. [Recognition](speech-recognition.md), [speech models](speech-models.md) |
| Torch, vibration and settings | Use one bounded effect and stop it. Settings changes need their corresponding access. [Hardware controls](hardware-controls.md) |
| Bluetooth | Enumerate, scan and inspect a chosen peer before pairing or starting RFCOMM/GATT work. Close connections afterward. [Bluetooth](bluetooth-control.md) |
| NFC | Present a tag and inspect discovery before a deliberate NDEF write or raw transaction. A bank card is not an unrestricted tag. [NFC](nfc-control.md) |
| USB and Wi-Fi | Request access to the intended peripheral or scan, inspect freshness/status and close transfers/sessions. [USB](usb-control.md), [Wi-Fi](wifi-control.md) |
| Other platform services | Consult declared coverage for Health Connect, inventories, administration and restricted paths. [Coverage](capability-coverage.md) |

**Check:** each result describes the source, freshness and actual outcome. A permission grant cannot create absent hardware or privileged Android access. Ordinary app access does not provide unrestricted cellular-call audio interception/injection, other apps private data or fastboot control.

### Android results on screen

The inspector below displays real command results, with a visible preview notice for long responses. These are demonstration documents, not extra built-in settings screens. Missing emulator hardware remains explicit. NFC transactions, physical Bluetooth/USB peers and carrier delivery require suitable hardware to demonstrate.

<details>
<summary>Show sensor, camera, audio, provider and connectivity results</summary>

<p><a href="{{ "/images/tutorial/api-sensors.png" | relative_url }}"><img src="{{ "/images/tutorial/api-sensors.png" | relative_url }}" alt="Actual bounded sensor readings, including timestamps and availability." width="280"></a><br>Actual bounded sensor readings, including timestamps and availability.</p>

<p><a href="{{ "/images/tutorial/api-location.png" | relative_url }}"><img src="{{ "/images/tutorial/api-location.png" | relative_url }}" alt="A location result with the configured emulator fix and freshness." width="280"></a><br>A location result with the configured emulator fix and freshness.</p>

<p><a href="{{ "/images/tutorial/camera-photo.png" | relative_url }}"><img src="{{ "/images/tutorial/camera-photo.png" | relative_url }}" alt="A JPEG captured through the Android camera and shown in a widget. The scene belongs to the emulator camera." width="280"></a><br>A JPEG captured through the Android camera and shown in a widget. The scene belongs to the emulator camera.</p>

<p><a href="{{ "/images/tutorial/api-camera.png" | relative_url }}"><img src="{{ "/images/tutorial/api-camera.png" | relative_url }}" alt="The camera inventory returned by Android." width="280"></a><br>The camera inventory returned by Android.</p>

<p><a href="{{ "/images/tutorial/api-video.png" | relative_url }}"><img src="{{ "/images/tutorial/api-video.png" | relative_url }}" alt="Finalized bounded video metadata, rather than an accepted-start claim." width="280"></a><br>Finalized bounded video metadata, rather than an accepted-start claim.</p>

<p><a href="{{ "/images/tutorial/api-microphone.png" | relative_url }}"><img src="{{ "/images/tutorial/api-microphone.png" | relative_url }}" alt="A completed short emulator microphone recording." width="280"></a><br>A completed short emulator microphone recording.</p>

<p><a href="{{ "/images/tutorial/api-audio.png" | relative_url }}"><img src="{{ "/images/tutorial/api-audio.png" | relative_url }}" alt="Audio input/output devices exposed by Android." width="280"></a><br>Audio input/output devices exposed by Android.</p>

<p><a href="{{ "/images/tutorial/api-voices.png" | relative_url }}"><img src="{{ "/images/tutorial/api-voices.png" | relative_url }}" alt="Speech voices reported by the installed engine." width="280"></a><br>Speech voices reported by the installed engine.</p>

<p><a href="{{ "/images/tutorial/api-speech-recognition.png" | relative_url }}"><img src="{{ "/images/tutorial/api-speech-recognition.png" | relative_url }}" alt="Recognition availability and listening state." width="280"></a><br>Recognition availability and listening state.</p>

<p><a href="{{ "/images/tutorial/api-contacts.png" | relative_url }}"><img src="{{ "/images/tutorial/api-contacts.png" | relative_url }}" alt="Direct provider query against a synthetic emulator contact." width="280"></a><br>Direct provider query against a synthetic emulator contact.</p>

<p><a href="{{ "/images/tutorial/api-calendar.png" | relative_url }}"><img src="{{ "/images/tutorial/api-calendar.png" | relative_url }}" alt="Direct calendar occurrences from the emulator fixture." width="280"></a><br>Direct calendar occurrences from the emulator fixture.</p>

<p><a href="{{ "/images/tutorial/api-providers.png" | relative_url }}"><img src="{{ "/images/tutorial/api-providers.png" | relative_url }}" alt="Provider discovery describes supported resources and access." width="280"></a><br>Provider discovery describes supported resources and access.</p>

<p><a href="{{ "/images/tutorial/api-notifications.png" | relative_url }}"><img src="{{ "/images/tutorial/api-notifications.png" | relative_url }}" alt="A bounded query of Android notifications." width="280"></a><br>A bounded query of Android notifications.</p>

<p><a href="{{ "/images/tutorial/api-bluetooth.png" | relative_url }}"><img src="{{ "/images/tutorial/api-bluetooth.png" | relative_url }}" alt="Bluetooth inventory and its actual availability on the emulator." width="280"></a><br>Bluetooth inventory and its actual availability on the emulator.</p>

<p><a href="{{ "/images/tutorial/api-wifi.png" | relative_url }}"><img src="{{ "/images/tutorial/api-wifi.png" | relative_url }}" alt="Wi-Fi scan-cache state, including freshness and availability." width="280"></a><br>Wi-Fi scan-cache state, including freshness and availability.</p>

<p><a href="{{ "/images/tutorial/api-sim.png" | relative_url }}"><img src="{{ "/images/tutorial/api-sim.png" | relative_url }}" alt="SIM metadata on this emulator. An empty inventory does not demonstrate a physical SIM or number lookup." width="280"></a><br>SIM metadata on this emulator. An empty inventory does not demonstrate a physical SIM or number lookup.</p>

<p><a href="{{ "/images/tutorial/api-usb.png" | relative_url }}"><img src="{{ "/images/tutorial/api-usb.png" | relative_url }}" alt="USB-host inventory. No attached peripheral means no demonstrated transfer." width="280"></a><br>USB-host inventory. No attached peripheral means no demonstrated transfer.</p>

<p><a href="{{ "/images/tutorial/api-nfc.png" | relative_url }}"><img src="{{ "/images/tutorial/api-nfc.png" | relative_url }}" alt="Actual NFC status. An unavailable emulator adapter is not a successful tag transaction." width="280"></a><br>Actual NFC status. An unavailable emulator adapter is not a successful tag transaction.</p>

</details>

## 8. Manage files, installs and long-running work

Use file discovery to browse paths Android permits, then read or download a chosen file. Upload a small test document to a new path before trying an APK. Transfers expose explicit offsets and completion state; finish or cancel a session deliberately. The authentication hash and private credentials are protected from ordinary remote file access.

For APK updates, upload the standalone APK and request installation. Android owns install permission and confirmation. An accepted install request is not a completed update: inspect final installation status and the serving package/version after reconnecting. Release and debug packages are distinct apps; use the documented handoff procedure rather than uninstalling your active controller and assuming access will survive.

Shell commands default to a 30-second timeout with per-request options. Root is requested by default; set `root: false` for the app sandbox. Android shell is not a desktop Linux environment and app permissions still apply. Read stdout, stderr, exit status and any explicit output-limit marker. A truncated preview is not the full output. Use incremental shell sessions, command jobs or streaming where appropriate instead of raising every response limit.

For work that outlives one request, use a durable command job and retain its identity/idempotency key. Poll the recorded result or cancel the job. After interruption, inspect the outcome before retrying a side effect. Stream logs or shell output with cursors; stop and forget sessions when done. Logcat visibility follows Android privilege limits.

**Check:** download the test file and compare its contents. For an intentional command test, inspect the exit status, not just transport success. See [files, APKs and shell](files-and-apks.md), [shell sessions](shell-sessions.md), [jobs](command-jobs.md) and [update recovery](remote-device-control.md#recover-after-an-apk-update).

<p><a href="{{ "/images/tutorial/api-files.png" | relative_url }}"><img src="{{ "/images/tutorial/api-files.png" | relative_url }}" alt="Directory entries returned by the shared file API." width="280"></a><br>Directory entries returned by the shared file API.</p>

<p><a href="{{ "/images/tutorial/api-shell.png" | relative_url }}"><img src="{{ "/images/tutorial/api-shell.png" | relative_url }}" alt="An unprivileged shell command with its real output and exit status." width="280"></a><br>An unprivileged shell command with its real output and exit status.</p>

<p><a href="{{ "/images/tutorial/api-jobs.png" | relative_url }}"><img src="{{ "/images/tutorial/api-jobs.png" | relative_url }}" alt="A harmless durable job completed and retained its result." width="280"></a><br>A harmless durable job completed and retained its result.</p>

<p><a href="{{ "/images/tutorial/api-event-journal.png" | relative_url }}"><img src="{{ "/images/tutorial/api-event-journal.png" | relative_url }}" alt="Event-journal retention and replay status." width="280"></a><br>Event-journal retention and replay status.</p>

## 9. Discover public Android SDK calls

The generic SDK bridge covers exact public classes and signatures from a pinned API catalogue. Discovery is broader than permission to invoke a method. Prefer a dedicated native tool when it already expresses your task.

After the network checks, discover a battery overload and read it:

```bash
python3 scripts/dikciz-control.py http androidApiDiscover --host phone.example --port 19002 --pretty \
  --arguments '{"className":"android.os.BatteryManager","query":"getIntProperty"}'
python3 scripts/dikciz-control.py http androidApiRead --host phone.example --port 19002 --pretty \
  --arguments '{"className":"android.os.BatteryManager","member":"getIntProperty(int)","target":"service:batterymanager","arguments":[4]}'
```

Property `4` is Android `BATTERY_PROPERTY_CAPACITY`. Compare its returned value with the dedicated battery state. For another method, inspect `bridgePermitted`, `runtimeAvailable`, exact positional types, permission annotations and pagination before calling. Supply `arguments`, including `[]` for a no-argument member; the bridge does not guess overloads.

`androidApiRead` permits only explicitly approved reads, even for writable callers. Writable invocation uses `androidApiInvoke` and requires an idempotency key. Object results use process-scoped handles. Page their contents with `androidApiObjects`; unregister/stop SDK sessions before releasing handles. Callbacks record events and can publish `android_api_callback` on the shared bus. A timeout can mean unknown completion, not cancellation.

**Check:** distinguish a method returning from a downstream Android operation completing. Catalogue presence proves neither hardware availability nor platform privilege. Read the [SDK guide](android-sdk.md) before constructors, callbacks, mutable methods or handle cleanup.

<p><a href="{{ "/images/tutorial/api-sdk.png" | relative_url }}"><img src="{{ "/images/tutorial/api-sdk.png" | relative_url }}" alt="The exact BatteryManager overload returned through androidApiRead." width="280"></a><br>The exact BatteryManager overload returned through androidApiRead.</p>

## 10. Check results and recover

Read the response at the layer that owns the effect. These are different outcomes:

| Reply | What to verify next |
| --- | --- |
| App launch accepted | Observed foreground package or a fresh screenshot/tree |
| SMS submitted | Tracked sent/delivery status, including unsupported delivery evidence |
| APK install requested | Android confirmation, terminal install status and installed version |
| Permission review shown | Actual Android grant, then a deliberate retry |
| Agent turn queued | Conversation state, tool outcomes and final reply |
| SDK method returned | Any separate callback, session or external completion |

Daily UTC JSON Lines logs live under `/sdcard/Dikciz/logs/`. **Settings**, **Configure logging** selects DEBUG, INFO, WARN or ERROR and retention. Defaults are INFO and seven days. Use DEBUG for investigation, inspect script/tool failures and keep sensitive phone data out of shared bug reports.

Valid home/configuration and asset edits reload automatically. Invalid input reports an error rather than making a working configuration proof of a valid new edit. If a widget breaks, edit that document or restore its saved source before resetting the home. APK updates preserve placed custom documents; a new bundled template does not overwrite an old placed copy automatically.

When updating, Dikciz reopens if it is default Home or has overlay access. If Android prevents it, tap **Dikciz updated** or open the app. Recheck device identity and listeners afterward. A home reset and chat-history deletion are separate operations; back up wanted public files and exports before any reset.

Finish an experiment by disabling its scripts and chat event rules, stopping recordings/streams/jobs/effects, unregistering SDK callbacks and restoring settings you changed. Closing a chat panel or leaving a page is not a universal undo.

For a specific failure, use the [documentation index](index.md), [command schemas](command-reference.md) and [capability coverage](capability-coverage.md). Report the installed version, operation, exact structured error and relevant redacted log lines.


### Settings and recovery screens

<p><a href="{{ "/images/user-guide/27-settings.png" | relative_url }}"><img src="{{ "/images/user-guide/27-settings.png" | relative_url }}" alt="Launcher settings collect layout, scripts, appearance and automation." width="280"></a><br>Launcher settings collect layout, scripts, appearance and automation.</p>

<p><a href="{{ "/images/user-guide/29-themes.png" | relative_url }}"><img src="{{ "/images/user-guide/29-themes.png" | relative_url }}" alt="Choose or customize the home theme." width="280"></a><br>Choose or customize the home theme.</p>

<p><a href="{{ "/images/user-guide/39-logging-settings.png" | relative_url }}"><img src="{{ "/images/user-guide/39-logging-settings.png" | relative_url }}" alt="Configure log detail and retention without changing source." width="280"></a><br>Configure log detail and retention without changing source.</p>

<p><a href="{{ "/images/user-guide/30-safe-mode.png" | relative_url }}"><img src="{{ "/images/user-guide/30-safe-mode.png" | relative_url }}" alt="Safe mode offers a recovery path for a broken home." width="280"></a><br>Safe mode offers a recovery path for a broken home.</p>

## Complete template gallery

These captures show the bundled documents as separate WebViews. The starter above shows the normal mixed-size layout. Data sources, required access and intended spans are listed in the [template catalog](starter-home.md#complete-bundled-widget-catalog). Action templates wait for a tap; a screenshot of their controls does not prove the effect ran.

<details>
<summary>Show the captured HTML templates</summary>

<p><a href="{{ "/images/tutorial/template-agenda.png" | relative_url }}"><img src="{{ "/images/tutorial/template-agenda.png" | relative_url }}" alt="Agenda" width="280"></a><br>Agenda</p>

<p><a href="{{ "/images/tutorial/template-app-launcher.png" | relative_url }}"><img src="{{ "/images/tutorial/template-app-launcher.png" | relative_url }}" alt="Android app settings" width="280"></a><br>Android app settings</p>

<p><a href="{{ "/images/tutorial/template-brightness.png" | relative_url }}"><img src="{{ "/images/tutorial/template-brightness.png" | relative_url }}" alt="Android display settings" width="280"></a><br>Android display settings</p>

<p><a href="{{ "/images/tutorial/template-command-deck.png" | relative_url }}"><img src="{{ "/images/tutorial/template-command-deck.png" | relative_url }}" alt="Clock and search" width="280"></a><br>Clock and search</p>

<p><a href="{{ "/images/tutorial/template-device-status.png" | relative_url }}"><img src="{{ "/images/tutorial/template-device-status.png" | relative_url }}" alt="Device status" width="280"></a><br>Device status</p>

<p><a href="{{ "/images/user-guide/05-goblin-page.png" | relative_url }}"><img src="{{ "/images/user-guide/05-goblin-page.png" | relative_url }}" alt="Goblin control" width="280"></a><br>Goblin control</p>

<p><a href="{{ "/images/tutorial/template-home-dashboard.png" | relative_url }}"><img src="{{ "/images/tutorial/template-home-dashboard.png" | relative_url }}" alt="Home dashboard" width="280"></a><br>Home dashboard</p>

<p><a href="{{ "/images/tutorial/template-lock-screen.png" | relative_url }}"><img src="{{ "/images/tutorial/template-lock-screen.png" | relative_url }}" alt="Lock screen" width="280"></a><br>Lock screen</p>

<p><a href="{{ "/images/tutorial/template-markets-news.png" | relative_url }}"><img src="{{ "/images/tutorial/template-markets-news.png" | relative_url }}" alt="BTC and economic news" width="280"></a><br>BTC and economic news. The quote fetched successfully; GDELT returned HTTP 429 for news.</p>

<p><a href="{{ "/images/tutorial/template-most-used-apps.png" | relative_url }}"><img src="{{ "/images/tutorial/template-most-used-apps.png" | relative_url }}" alt="Most used apps" width="280"></a><br>Most used apps</p>

<p><a href="{{ "/images/tutorial/template-notifications.png" | relative_url }}"><img src="{{ "/images/tutorial/template-notifications.png" | relative_url }}" alt="Post a notification" width="280"></a><br>Post a notification</p>

<p><a href="{{ "/images/tutorial/template-quick-apps.png" | relative_url }}"><img src="{{ "/images/tutorial/template-quick-apps.png" | relative_url }}" alt="Find an app" width="280"></a><br>Find an app</p>

<p><a href="{{ "/images/tutorial/template-quick-note.png" | relative_url }}"><img src="{{ "/images/tutorial/template-quick-note.png" | relative_url }}" alt="Quick note" width="280"></a><br>Quick note</p>

<p><a href="{{ "/images/tutorial/template-sensors-dashboard.png" | relative_url }}"><img src="{{ "/images/tutorial/template-sensors-dashboard.png" | relative_url }}" alt="Live sensors" width="280"></a><br>Live sensors</p>

<p><a href="{{ "/images/tutorial/template-system-dashboard.png" | relative_url }}"><img src="{{ "/images/tutorial/template-system-dashboard.png" | relative_url }}" alt="System dashboard" width="280"></a><br>System dashboard</p>

<p><a href="{{ "/images/tutorial/template-system-status.png" | relative_url }}"><img src="{{ "/images/tutorial/template-system-status.png" | relative_url }}" alt="Dikciz diagnostics" width="280"></a><br>Dikciz diagnostics</p>

<p><a href="{{ "/images/tutorial/template-time-weather.png" | relative_url }}"><img src="{{ "/images/tutorial/template-time-weather.png" | relative_url }}" alt="Time and weather" width="280"></a><br>Time and weather</p>

<p><a href="{{ "/images/tutorial/template-voice-notes.png" | relative_url }}"><img src="{{ "/images/tutorial/template-voice-notes.png" | relative_url }}" alt="Voice notes" width="280"></a><br>Voice notes</p>

The optional [Vehicle console](device-connections.md#vehicle-document) adds a paired-adapter dashboard. Its documented capture uses an explicit deterministic adapter fixture.

</details>

## Inspect activity and preserve a setup

Use Settings, Activity & resources to inspect callers, failures and resource measurements. Pause a script, HTML widget or chat event rule there. Create an encrypted backup, preview its counts and confirm the exact archive before replacing home, apps, assistant instructions and chat history. Imported pending or claimed app timers require review and explicit re-arming. See [the guide](backups.md).

## Companion workflows

See [the focused companion guide](companion.md) for safety, geofences, routes, peripherals, health and Wi-Fi, including grants and actual outcome states. SOS is an editable custom app. [Custom apps](custom-apps.md) explains complete UI/backend bundles, durable timers, dimmed urgent modals and source-only exports. [Travel todo](todo-app-tutorial.md) is the small working reference for that platform.


Open **Dikciz > Skills, agents & rules** to edit and validate assistant instructions. The bundled guides teach self-authoring through revision-checked tools. Use **Dikciz > Automation debugger** for paged event, handler and command traces, retained payloads and effect-free Lua/JavaScript simulation. Read [instruction management](assistant-library.md#manage-instructions-on-the-phone) and [the debugger guide](automation-debugger.md).
