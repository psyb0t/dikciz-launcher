# Capability coverage

This table describes dedicated Dikciz operations. Generic intents, shell and Accessibility can cover additional tasks, but do not turn them into a typed API. The installed build's discovery is authoritative. New commands in this working version are not present in older APKs.

| Area | Implemented | Remaining |
| --- | --- | --- |
| Launcher | Pages, grid, widgets, shortcuts/groups, themes, editing and full configuration | Continued platform compatibility and focused UI improvements. |
| Shared control | Ordinary HTTP, WebSocket, MCP, HTML and Lua commands; validation, Android permission review and incremental shell sessions and replayable event reads | Additional native stream formats and general push delivery for scripts. |
| Speech input | Provider discovery, strict on-device/system modes, language, bounded recognition, alternatives/confidence, cancellation, provider language inventory and observed model-download requests | Partial-result streaming and file transcription. |
| Speech output | Device text-to-speech, voice inventory, voice/language/rate/pitch selection, completion/errors, saved WAV synthesis and explicit stop | Engine-specific advanced synthesis features. |
| Audio | Bounded and managed microphone recording with input preference, foreground-service PCM streaming with cursor replay and owner stop, device inventory, local playback sessions with pause/resume/seek, volume, focus and preferred output | Live audio output and additional recording formats. |
| Camera | Camera inventory, JPEG controls/actual frame metadata, bounded foreground H.264 MP4 with optional AAC and capability discovery | Preview streams and managed multi-command recording sessions. |
| Sensors | Static/dynamic inventory, bounded readings, background subscriptions, live starter page and process-owned dispatch replay | Raw sample replay before bus coalescing. |
| Location | Bounded current fix/cache, accuracy/age/source, optional place name, background subscriptions, weather integration, standalone geocoding and GNSS satellite observation | Dedicated tracking jobs, geofences, route history and raw GNSS diagnostics. |
| Torch/haptics | Bounded torch-state callback observation and on/off submission; vibration/effect support, pulses, finite waveforms, predefined effects and cancellation | Torch strength, advanced effect composition and vendor-specific haptics. |
| System settings | Brightness/mode, rotation/mode and screen timeout read/write/read-back | Additional supported setting families and dedicated power/network control flows. |
| Bluetooth | Adapter state events, paired inventory, BLE/classic discovery, bond requests, bounded RFCOMM and GATT discovery/read/write/observation | Persistent connections and additional Bluetooth profiles. |
| Wi-Fi | Connectivity state, cached scans and requested scans with deadlines/freshness | Network request/selection, hotspot and Wi-Fi Direct workflows. |
| NFC | Adapter status, reader-mode tag discovery, NDEF read/write and one raw technology command per connection | EMV application decoding/payment workflows, card emulation, persistent tag sessions and command batches. |
| USB | Attached host devices/interfaces/endpoints, owner permission request and bounded control/bulk transfers | Persistent multi-command sessions and additional transfer families. |
| Health Connect | Availability, grants and daily-step subscriptions | General history, aggregation, other record types and writes. |
| Notifications | Listing, content events, plain actions, dismiss, snooze and inline reply | Additional Android notification-policy capabilities. |
| Media | Session listing/events and typed controls | Network playback, platform-wide routing and richer external-session controls. |
| Contacts/calendar | Bounded provider queries, writes and atomic same-provider batches | Additional provider fields/resources when explicitly supported. |
| SMS/telephony | SMS history, tracked idempotent SMS sends, SIM inventory, call state/place/answer/end, call-history reads and one-shot USSD with callback/deadline outcomes | MMS, interactive USSD, richer call lifecycle/in-call controls and radio diagnostics. |
| Files | Paginated listing, chunk read/write, directory creation, binary HTTP transfer, stat/copy/move/delete/search and credential-path restrictions | Document-provider workflows and recursive copy. |
| Packages | App discovery/icons/actions, package inspection, standalone APK install/status and launcher handoff | Split APKs, install cancellation and richer component inspection. |
| Display/UI | Launcher and actual-display screenshots, semantic/Accessibility controls and gestures | Screen video/streaming and OCR/barcode/document recognition. |
| Operations | Persistent WebSocket events, Lua/background sources, shell streams, per-operation statuses and durable command jobs with idempotency, cancellation and reconnectable results, plus process-owned automation-event replay | General command-progress delivery and durable event acknowledgements. |
| SOS | Location and tracked SMS primitives | Contact selection, missed check-ins, explicit trigger/cancel and coordinated emergency workflow. |

## Platform-restricted work

Cellular call-stream recording and injection need privileged system integration. Silent permission grants, arbitrary private app data and protected system settings are not ordinary app capabilities. Owner-enabled Accessibility, special access, a device-owner deployment or root can change specific capabilities, but none grants every Android API automatically. Fastboot/bootloader control is outside a running Android app.

Screen recording through MediaProjection needs the platform consent flow. Camera and microphone have foreground and privacy restrictions. Bluetooth/Wi-Fi/NFC/USB availability depends on hardware and Android access. A missing device is an explicit result, not a reason to report simulated success.

## Documentation contract

Every added command must update discovery, the appropriate guide, agent references and a focused MCP resource. Tests must exercise the shared command target and actual Android effects or a clearly identified external provider boundary. Do not label a remaining item implemented just because permission was declared.
