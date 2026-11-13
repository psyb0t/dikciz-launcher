---
title: "Documentation"
description: "Build an editable Android home, write JavaScript and Lua apps, connect an AI, and control phone data through the Dikciz guides and API manuals."
nav_order: 1
permalink: /
seo:
  type: WebSite
---

# Dikciz Launcher documentation

Use the [automation debugger](automation-debugger.md) to inspect retained traces and simulate handlers, and the [instruction manager](assistant-library.md#manage-instructions-on-the-phone) to author skills, agents and rules.

Give the built-in assistant [SD-card skills, named agents and rules](assistant-library.md), and preserve their files together with complete app state through [encrypted backups](backups.md).

Your phone is programmable. Start with an editable Android home, build complete HTML/CSS/JavaScript apps, run Lua or JavaScript in the background, and connect your own AI to the same phone tools.

<div class="docs-routes">
<div><h2>Use your phone</h2><p>Install the APK, choose your home, grant Android access and set up chat.</p><p><a href="{{ '/tutorial/' | relative_url }}">Follow the illustrated tutorial</a></p></div>
<div><h2>Build something</h2><p>Write widgets, background scripts and complete apps with their own saved state.</p><p><a href="{{ '/todo-app-tutorial/' | relative_url }}">Build the GPS todo app</a></p></div>
<div><h2>Connect an agent</h2><p>Reach your phone over a private network and give each client the access it needs.</p><p><a href="{{ '/automation-control/' | relative_url }}">Connect HTTP, WebSocket or MCP</a></p></div>
</div>

<div class="docs-gallery">
<a href="{{ '/user-guide/' | relative_url }}"><img src="{{ '/images/user-guide/01-first-launch.png' | relative_url }}" alt="Dikciz starter home with weather, app icons and native dock" loading="lazy" width="190"></a>
<a href="{{ '/custom-apps/' | relative_url }}"><img src="{{ '/images/custom-apps/todo-watching.png' | relative_url }}" alt="Travel todo custom app with a GPS arrival task" loading="lazy" width="190"></a>
</div>

## Find a guide

Use the [tutorial](tutorial.md) for installation and first steps, or choose a task below. Download the signed APK from [GitHub Releases](https://github.com/psyb0t/dikciz-launcher/releases/latest). The command reference records accepted inputs; the guides explain Android access, effects and recovery. Discover the installed build before using commands added after its release.

| Task | Guide |
| --- | --- |
| Follow short procedures using exact shared commands | [Recipes](recipes.md) |
| Build apps, scripts and controllers from documented APIs | [Developer manual](developer-manual.md), [JavaScript](javascript-api.md), [Lua](lua-api.md) |
| Look up every token grant and exact operation/action/resource scope | [Token permission reference](token-permissions.md) |
| Follow the illustrated setup, chat, scripting and Android-control walkthrough | [Dikciz tutorial](tutorial.md) |
| Find a screen, dialog, menu or recovery step | [Screens and guided workflows](screens.md) |
| Discover and invoke public SDK overloads, handles and callbacks | [Android SDK bridge](android-sdk.md) |
| Install, choose Home, navigate, edit pages/widgets, manage apps and scripts | [Illustrated user guide](user-guide.md) |
| Configure the on-device assistant and event-driven conversations | [Embedded chat](embedded-chat.md) |
| Browse every bundled page, HTML template and Lua example | [Starter home](starter-home.md) |
| Build a checklist that checks a task after GPS arrival | [Travel todo tutorial and source](todo-app-tutorial.md) |
| Create complete UI/backend apps with state, timers and alerts | [Custom apps](custom-apps.md) |
| Edit files, themes, grid, widget state, libraries, policies, logging and listeners | [Configuration](dikciz-configuration.md) |
| Look up every typed action, its fields and Lua policy name | [Typed actions](typed-actions.md) |
| Check Android grants, foreground requirements and privilege boundaries | [Android access](android-access.md) |
| Write Lua and HTML, publish events, use policies and the native bridge | [Scripting tutorial](scripting-tutorial.md) |
| Give clients named credentials with separate grants | [Remote access tokens](remote-access-tokens.md) |
| Connect HTTP/WebSocket/MCP, authenticate, discover and request permissions | [Automation control](automation-control.md) |
| Look up contacts, send tracked SMS, use calendars, calls, clipboard and media | [Native device data](native-device-control.md) |
| Resume custom and native automation events | [Event replay](event-replay.md) |
| Submit an owner-requested carrier USSD code | [USSD](ussd-control.md) |
| Configure camera JPEG capture and inspect actual frame metadata | [Camera controls](camera-control.md) |
| Read sensors, location, camera photos, microphone recordings and speech output | [Native hardware](native-device-control.md#sensors-camera-microphone-and-speech) |
| Geocode addresses/coordinates and observe satellite status | [Location services](location-services.md) |
| Discover Bluetooth peers, pair and exchange RFCOMM/GATT data | [Bluetooth control](bluetooth-control.md) |
| Read, write or transact with a presented NFC tag | [NFC control](nfc-control.md) |
| Stream live microphone PCM with replay and owner stop | [Microphone streams](microphone-streams.md) |
| Play local audio, pause/resume, seek and select an output | [Audio playback](audio-playback.md) |
| Request Wi-Fi scans and inspect cache freshness | [Wi-Fi control](wifi-control.md) |
| Request USB access and transfer peripheral data | [USB control](usb-control.md) |
| Use modern or legacy MCP clients | [MCP protocol](mcp-protocol.md) |
| Inspect recognition languages and request model downloads | [Speech models](speech-models.md) |
| Convert microphone speech to text | [Speech recognition](speech-recognition.md) |
| Use torch, vibration, settings and device inventories | [Hardware controls](hardware-controls.md) |
| Read incremental shell output from any binding and reconnect | [Shell sessions](shell-sessions.md) |
| Browse and transfer files, install APKs, run/stream shell | [Files, APKs and shell](files-and-apks.md) |
| Restart, hand off release/debug control, inspect packages and capture screens | [Remote device control](remote-device-control.md) |
| Connect Claude Code, Codex and other agents | [Agent integrations](agent-integrations.md) |
| Search offline documentation, recipes, tool schemas and owner collections | [Offline search](search.md) |
| Fetch short guides and recipes through MCP | [MCP resources](mcp-resources.md) |
| Use safety plans, journeys, connections, vision, health and Wi-Fi | [Phone companion](companion.md) |
| Inspect activity, pause resources and build visual rules | [Operations](operations.md) |
| Create encrypted portable snapshots | [Backup and restore](backups.md) |
| Stream camera/display frames, partial speech or transcribe recordings | [Live images and speech](visual-streams.md) |
| Submit, poll and cancel durable commands | [Command jobs](command-jobs.md) |
| Dismiss, snooze and reply to notifications | [Notification management](notification-management.md) |
| Look up every discovered MCP input schema | [Command reference](command-reference.md) |
| Check implemented, partial, planned and platform-restricted capabilities | [Coverage](capability-coverage.md) |
| Inspect release changes and migration notes | [Changelog](changelog.md) |
| Build and maintain source or the documentation website | [Source development](source-development.md), [documentation site](documentation-site.md) |
| Build, test and understand product architecture | [Source](https://github.com/psyb0t/dikciz-launcher/blob/main/app/src/dikciz/README.md), [scripts](https://github.com/psyb0t/dikciz-launcher/blob/main/scripts/README.md), [tests](https://github.com/psyb0t/dikciz-launcher/blob/main/tests/README.md), [contributing](https://github.com/psyb0t/dikciz-launcher/blob/main/CONTRIBUTING.md) |

## Outcome rules

A transport reply proves only what its result states. Accepted intents are not verified app launches; submitted SMS is not carrier delivery; an install session is not a completed installation; submitted torch/vibration is not measured physical output. Read the terminal status or observed effect documented for that operation.

Android permissions, special access, roles, hardware, foreground state and system privilege are independent checks. A manifest declaration is not an implementation or grant. Missing capability is explicit. Root shell is optional privileged access, not a portable replacement for ordinary Android APIs.

[Activity, resources and visual rules](operations.md) covers command history, resource measurements, interruption and simulated rule testing.

[Background JavaScript](background-javascript.md) covers the second script backend, headless events, state, limits and archives.
