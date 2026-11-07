---
title: "Capability coverage"
description: "Check which Android and Dikciz capabilities are implemented, partial or platform restricted, with the corresponding guides and boundaries."
permalink: /capability-coverage/
nav_order: 9
parent: "Developer manual"
---

# Capability coverage

This table describes dedicated Dikciz operations. Generic intents, shell and Accessibility can cover additional tasks, but do not turn them into a typed API. The installed build's discovery is authoritative. New commands in this working version are not present in older APKs.

| Area | Implemented | Remaining |
| --- | --- | --- |
| Launcher | Pages, grid, widgets, shortcuts/groups, themes, editing and full configuration | Continued platform compatibility and focused UI improvements. |
| Custom apps | Complete editable HTML/CSS/JS and Lua/JS bundles, independent state/files, shared Dikciz command access, native/custom subscriptions, persistent timers, urgent modals and source-only archives; bundled SOS, Voice notes, Agenda, Device status and the GPS Travel todo reference | Separate APK identities and inclusion in encrypted home/chat backups are not implemented. See [custom apps](custom-apps.md). |
| On-device assistant | OpenAI/Anthropic-compatible streaming providers, native Markdown chat, SQLite conversations, encrypted keys, context trimming/summarization, shared MCP tools/event rules and five enforced read-only routines | Additional provider wire protocols and exact per-model tokenizers. |
| Offline search | Persistent SQLite collections, weighted ranking, prefixes, reviewed aliases, typo correction, full-text reads, live schemas and trusted read-only filtering across every ordinary binding | Semantic embeddings, automatic translation and automatic private-data crawling are not implemented. |
| Recovery and operations | Persistent metadata timeline, scoped resource measurements and pause controls, visual Lua rule testing, encrypted home/chat backup with preview and rollback recovery | Per-widget battery attribution is unavailable from ordinary Android APIs. |
| Token grants | Compatible named grants plus explicit grants for every command and finite selector combinations; shared discovery/execution enforcement and caller-scoped jobs | Operation grants are device-wide unless an API explicitly binds a resource to a caller. Executable code, shell, SDK and credential administration can delegate broad authority. |
| Shared control | Ordinary HTTP, WebSocket, MCP, HTML and Lua commands; validation, Android permission review and incremental shell sessions and replayable event reads | Additional native stream formats and general push delivery for scripts. |
| Speech input | Provider discovery, strict on-device/system modes, language, bounded recognition, alternatives/confidence, partial-result cursor streams, explicit recorded-file provider transcription, cancellation, provider language inventory and observed model-download requests | Provider-dependent partial support and additional transcription wire protocols. |
| Speech output | Device text-to-speech, voice inventory, voice/language/rate/pitch selection, completion/errors, saved WAV synthesis and explicit stop | Engine-specific advanced synthesis features. |
| Audio | Bounded and managed microphone recording with input preference, foreground-service PCM streaming with cursor replay and owner stop, device inventory, local playback sessions with pause/resume/seek, volume, focus and preferred output | Live audio output and additional recording formats. |
| Camera | Camera inventory, JPEG controls/actual frame metadata, bounded foreground H.264 MP4 with optional AAC, persistent JPEG preview streams and capability discovery | Managed multi-command video recording sessions. |
| Sensors | Static/dynamic inventory, bounded readings, background subscriptions, live starter page and SQLite dispatch replay and acknowledged consumers | Raw sample replay before bus coalescing. |
| Location | Bounded current fix/cache, accuracy/age/source, optional place name, background subscriptions, weather integration, geocoding/GNSS status, accuracy-aware geofences and recorded routes | Raw GNSS diagnostics. Dedicated geofences and route history are described in [journeys](safety-and-journeys.md). |
| Torch/haptics | Bounded torch-state callback observation and on/off submission; vibration/effect support, pulses, finite waveforms, predefined effects and cancellation | Torch strength, advanced effect composition and vendor-specific haptics. |
| System settings | Brightness/mode, rotation/mode and screen timeout read/write/read-back | Additional supported setting families and dedicated power/network control flows. |
| Bluetooth | Adapter state events, paired inventory, BLE/classic discovery, bond requests, bounded and retained RFCOMM/GATT discovery/read/write/notification sessions | Additional Bluetooth profiles. |
| Wi-Fi | Connectivity/scans, Android-approved suggestions/saved networks, local network requests and local-only hotspot | Internet tethering and Wi-Fi Direct workflows. |
| NFC | Adapter status, reader-mode tag discovery, NDEF read/write and one-shot or retained technology/NDEF sessions | EMV application decoding/payment workflows and card emulation. |
| USB | Attached host devices/interfaces/endpoints, owner permission request and bounded and retained control/bulk/interrupt transfers | Isochronous transfers and device-class protocols. |
| Health Connect | Availability/grants, daily-step subscriptions, sixteen record kinds, bounded history/aggregates and owned writes/deletes | Additional Health Connect record kinds and provider-dependent feature support. |
| Notifications | Listing, content events, plain actions, dismiss, snooze and inline reply | Additional Android notification-policy capabilities. |
| Media | Session listing/events and typed controls | Network playback, platform-wide routing and richer external-session controls. |
| Contacts/calendar | Bounded provider queries, writes and atomic same-provider batches | Additional provider fields/resources when explicitly supported. |
| SMS/telephony | SMS history, tracked idempotent SMS sends, SIM inventory and own-number reads with explicit availability, call state/place/answer/end, call-history reads and one-shot USSD with callback/deadline outcomes | MMS, interactive USSD, richer call lifecycle/in-call controls and radio diagnostics. |
| Files | Paginated listing, chunk read/write, directory creation, binary HTTP transfer, stat/copy/move/delete/search and credential-path restrictions | Document-provider workflows and recursive copy. |
| Packages | App discovery/icons/actions, package inspection, standalone/base-split APK install/status, pending-session cancellation and launcher handoff | Richer component inspection. |
| Display/UI | Launcher and actual-display screenshots, semantic/Accessibility controls, JPEG streams, Latin OCR/barcodes and raster PDF creation | Screen video, automatic page-edge detection and searchable PDF text layers. |
| Operations | Persistent WebSocket events, Lua/background sources, shell streams, per-operation statuses and durable command jobs with idempotency, cancellation and reconnectable results, plus process-owned automation-event replay | General command-progress delivery and durable event acknowledgements. |
| SOS | Exact contact plans, dry-run preview, opt-in missed check-ins, cancellation grace, bounded tracked updates and owner stop | Android restrictions prevent guaranteed emergency delivery. |

## Public SDK coverage

The [SDK bridge](android-sdk.md) supplements the dedicated operations above with a pinned public API catalogue, typed exact-overload calls, object handles and callback proxies. Discovery reports runtime availability and bridge exclusions. Read-only conversations hide write tools and members, while execution rejects forged writes before permissions or invocation. This inventory does not relabel dedicated remaining features as fully implemented or device-tested.

## Platform-restricted work

Cellular call-stream recording and injection need privileged system integration. Silent permission grants, arbitrary private app data and protected system settings are not ordinary app capabilities. Owner-enabled Accessibility, special access, a device-owner deployment or root can change specific capabilities, but none grants every Android API automatically. Fastboot/bootloader control is outside a running Android app.

Screen recording through MediaProjection needs the platform consent flow. Camera and microphone have foreground and privacy restrictions. Bluetooth/Wi-Fi/NFC/USB availability depends on hardware and Android access. A missing device is an explicit result, not a reason to report simulated success.

## Documentation contract

Every added command must update discovery, the appropriate guide, agent references and a focused MCP resource. Tests must exercise the shared command target and actual Android effects or a clearly identified external provider boundary. Do not label a remaining item implemented just because permission was declared.
