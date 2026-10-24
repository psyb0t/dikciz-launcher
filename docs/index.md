# Documentation

Start with the [README](../README.md) for installation, or choose the task below. The command reference records accepted inputs; the guides explain Android access, effects and recovery. Discover the installed build before using commands added after its release.

| Task | Guide |
| --- | --- |
| Install, choose Home, navigate, edit pages/widgets, manage apps and scripts | [Illustrated user guide](user-guide.md) |
| Configure the on-device assistant and event-driven conversations | [Embedded chat](embedded-chat.md) |
| Browse every bundled page, HTML template and Lua example | [Starter home](starter-home.md) |
| Edit files, themes, grid, widget state, libraries, policies, logging and listeners | [Configuration](dikciz-configuration.md) |
| Look up every typed action, its fields and Lua policy name | [Typed actions](typed-actions.md) |
| Check Android grants, foreground requirements and privilege boundaries | [Android access](android-access.md) |
| Write Lua and HTML, publish events, use policies and the native bridge | [Scripting tutorial](scripting-tutorial.md) |
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
| Fetch short guides and recipes through MCP | [MCP resources](mcp-resources.md) |
| Submit, poll and cancel durable commands | [Command jobs](command-jobs.md) |
| Dismiss, snooze and reply to notifications | [Notification management](notification-management.md) |
| Look up every discovered MCP input schema | [Command reference](command-reference.md) |
| Check implemented, partial, planned and platform-restricted capabilities | [Coverage](capability-coverage.md) |
| Inspect release changes and migration notes | [Changelog](changelog.md) |
| Build, test and understand product architecture | [Source](../app/src/dikciz/README.md), [scripts](../scripts/README.md), [tests](../tests/README.md), [contributing](../CONTRIBUTING.md) |

## Outcome rules

A transport reply proves only what its result states. Accepted intents are not verified app launches; submitted SMS is not carrier delivery; an install session is not a completed installation; submitted torch/vibration is not measured physical output. Read the terminal status or observed effect documented for that operation.

Android permissions, special access, roles, hardware, foreground state and system privilege are independent checks. A manifest declaration is not an implementation or grant. Missing capability is explicit. Root shell is optional privileged access, not a portable replacement for ordinary Android APIs.
