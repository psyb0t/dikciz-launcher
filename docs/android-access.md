# Android access and execution boundaries

Dikciz acts with the Android identity and grants of its installed package. HTTP, WebSocket, MCP, HTML and enabled Lua ordinary commands share validation, Android access checks and outcomes. Root, network reachability, bearer authentication, Android permissions, special access, foreground state and hardware availability are separate checks.

Read `deviceInfo` for the serving package/version, `controlStatus` or `tools/list` for supported commands, `automationStatus.deviceAccess.requirements` for current setup and `nativeDataStatus` for provider columns and grants. Discover the installed build, not just the latest repository documentation. Release and debug packages have independent Android grants.

## Feature access map

This map describes prerequisites, not permission grants. Android version, hardware, provider ownership and device policy can further restrict an operation.

| Feature | Access and execution requirements |
| --- | --- |
| Home, pages, files, widgets and Lua | Shared-storage access for the public home; file APIs also use app-sandbox access. Scripts and widgets execute only when enabled and configured. Native provider widgets need Android binding/configuration approval. |
| Remote controls | A reachable running listener, allowed network route, configured bearer credential and accepted Origin. The default bind is phone loopback; direct private access requires an appropriate listen address. |
| Contacts and calendars | Corresponding read/write Android grants and the allowlisted resource/columns. Only raw contacts, contact data, calendar events and reminders are writable. Account/provider rules remain in force. |
| SMS | Read SMS for history, Receive SMS for incoming events, Send SMS for outgoing messages; SIM discovery can additionally need phone-state access. No default SMS role is requested. Tracked sends need a caller idempotency key. |
| Calls, call history and USSD | Phone/call-history grants as reported by discovery. Place/USSD need call access; answering/ending depend on Android version, role and policy. USSD is a real carrier operation and may have an unknown outcome after timeout. |
| Clipboard | Android foreground/default-input-method rules can block reads. A declared grant does not create unrestricted background clipboard access. |
| Notifications and media sessions | Owner-enabled notification-listener special access for reading/control; Android posting access for Dikciz notifications. Do Not Disturb policy access is separate. Inline replies can send messages through the originating app. |
| UI across apps | Owner-enabled Dikciz Accessibility service. Node/gesture actions need fresh snapshots; stale windows are refused. Secure windows and Android UI restrictions still apply. |
| Screenshots | Launcher-window screenshot is distinct from actual-display capture. `displayScreenshot` needs Android 11+ and Accessibility; it cannot bypass secure-window restrictions. |
| Locking | `lockDevice` needs Device Administration; Accessibility global lock uses that separate service and supported Android version. |
| Sensors and Health Connect | Ordinary sensors may need no runtime grant; restricted sensors can need body-sensor/activity-recognition access. Inspect each sensor's requirement/reason. Health Connect availability and daily-step read consent are separate from generic body-sensor permission. |
| Location, geocoding and GNSS | Appropriate coarse/fine location access and enabled providers; background subscriptions can need separate background access. GNSS status needs precise access and an actual callback. Geocoding can send queries/coordinates to Android's provider. |
| Photos and video | Camera grant, foreground eligibility and available camera. Audio-enabled video additionally owns microphone access. These commands provide bounded capture, not a preview or background video service. |
| Microphone and recognition | Microphone grant, foreground eligibility at start and exclusive microphone ownership. Managed/bounded recording and recognition remain foreground operations. PCM streaming can continue under its dedicated microphone foreground service; Android privacy controls still apply. |
| Speech and local playback | Available TTS engine or readable audio file and permitted audio focus/routing. Network-required voices and system speech recognition can use a provider's network service. Strict on-device recognition never silently falls back to it. |
| Torch and vibration | Flash/vibrator hardware. Torch changes need camera access; Android haptic settings can suppress vibration. Submission is not measured physical output. |
| Selected system settings | Owner-granted Modify system settings for writes to the explicit allowlist. Read-back shows stored values, not proof that automatic brightness/rotation or device policy obeyed them. |
| Bluetooth | Enabled adapter; Android 12+ scan/connect grants as needed, older scan location requirements, and peer pairing/security. No silent radio enablement or persistent connections. |
| Wi-Fi | Fine location and applicable location/scan services. Android throttling remains. Cached results are separate from a requested scan; neither API changes networks. |
| NFC | Available/enabled adapter, foreground unlocked Dikciz and a presented compatible tag. NDEF and raw ISO-DEP are different formats. No EMV decoder, persistent tag sessions or card emulation. |
| USB host | Attached peripheral, Android approval for that device and supported endpoint/interface. Per-device access expires on disconnect. This is not ADB or fastboot access to the phone. |
| APK install/update | Owner-approved installation from Dikciz plus Android installer confirmation when required. Standalone APK support does not imply split-APK support or silent installation. |
| Shell, root and other apps' data | `root:false` uses the app UID, not the ADB shell UID. Root defaults true and requires a working approved `su`; denial does not fall back. No Shizuku backend is implemented. |

Use [native data](native-device-control.md), [hardware controls](hardware-controls.md) and the [documentation index](index.md) for each operation's fields, errors and limits.

## Request missing access

Use requirement IDs from live discovery. `requestDeviceAccess` asks for one requirement explicitly. Ordinary commands default to `autoRequestPermissions:false`; opting into true requests owner review only for missing access encountered by that operation. It does not open every permission screen.

Inspect `permissionRequest.state`, `requirementId`, `notificationPosted`, `promptState`, `retryRequired`, `deduplicated` and `instructions`. Tell the owner which review to complete. Check access again and explicitly retry the authorized operation. A notification or closed Android dialog is not proof of a grant. Partial batches can already have completed actions.

The complete **Settings**, **Automation**, **Set up full device access** checklist is a separate task that survives another Settings panel taking the foreground. It refreshes actual grants on return. Optional or unsupported requirements can remain unmet; that does not imply unrelated commands are broken.

## Entrypoints and persistence

| Interface or operation | Delivery and lifetime |
| --- | --- |
| HTTP/WebSocket/MCP ordinary commands | Shared command results and errors inside transport-specific envelopes. MCP resources are packaged documentation and exist only on MCP. |
| Persistent WebSocket events | Push while the connection and listener remain alive. Disconnect is not durable delivery. |
| Network `shellStream` | Connection/session-owned push output; HTTP/MCP use SSE. Disconnect stops the owned process. HTML/Lua cannot receive this streaming transport. |
| Shell sessions, event journal and PCM streams | Ordinary cursor reads, including HTML/Lua, with bounded process-memory replay and explicit gaps. They survive network reconnects; process death loses history. Each feature has its own capacity and cleanup rules. |
| Durable command jobs | App-private persisted arguments/results and caller idempotency; interrupted work is not replayed automatically. Cancellation does not undo external effects. |
| HTML rendering and Lua | HTML visible interaction/polling uses native visibility. Lua handles configured background events. Neither creates an Android permission or an unlimited background-execution exemption. |
| Activity restart and APK update | `restart` recreates the activity within the same process. APK replacement kills the old process; listeners need reconnection and process-owned histories are lost. Public saved configuration survives compatible updates. |

Read the operation's terminal status. Request acceptance is not verified app launch, message delivery, successful install or measured camera/audio quality. A client timeout can leave external work with an unknown outcome; do not repeat it blindly.

## Credentials and privilege limits

File commands deny `/sdcard/Dikciz/control/remote-auth.json`, its atomic `.bak`/`.new` aliases and canonical aliases. File management also refuses moves/deletes of containing ancestors. HTTP binary transfers use the same file-command checks. This restriction covers those APIs, not an independently authorized shell: an app-UID or root shell can access what its Android identity permits. The verifier is not guaranteed ADB-only.

Enabling a Lua script or installing trusted HTML gives its command binding owner authority. Lua returned-action policies control automatic delivery and returned typed actions; they do not sandbox `context.request`. Keep untrusted code out of enabled scripts/widgets. Safe mode blocks ordinary mutations while preserving documented diagnostics and recovery.

Dikciz does not expose every Android SDK method. Ordinary permissions do not grant cellular call-audio capture/injection, silent grants, arbitrary private databases or protected system settings. Camera/microphone privacy controls remain active. A stopped app cannot revive itself over its absent listener; fastboot/bootloader modes run outside the Android process. The [coverage table](capability-coverage.md) lists remaining implementations separately from platform restrictions.
