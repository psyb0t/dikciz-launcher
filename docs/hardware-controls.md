# Hardware controls and discovery

These commands use the same registry, schemas, native executor and Android permission recovery across HTTP, WebSocket, MCP, HTML and Lua. Inventory reads do not enable radios, pair devices, record audio or request transfers. All fields are strict; unknown fields and invalid values fail before device work.

## Torch

`torchControl` / `dikciz_torch_control` requires `action`: `status`, `on` or `off`. Status returns flash-capable `cameraIds` and one `items` entry per selected camera. It registers CameraManager callbacks for up to `timeoutMilliseconds` (1 through 5000, default 1000), then unregisters. Entries report `enabled`, `disabled`, `unavailable` or `unknown`, with `observed`, nullable `enabled` and a callback delivery timestamp in elapsed milliseconds. Unknown means no callback arrived within the window. `complete` means every selected camera was observed. Optional `cameraId` selects one camera; no flash hardware returns an empty complete result. Timeout applies only to status. On accepts optional `durationMilliseconds` from 1 through 5000 for a native automatic off request independent of WebView timers. A later on/off request replaces the pending timer for that camera. The response includes nullable `autoOffAfterMilliseconds`; automatic-off failures are logged. Duration is rejected for status/off. On/off accept optional `cameraId`, defaulting to the first flash camera. Use IDs from this result or `cameraList`. Android camera permission is required for changes. The response is `state: submitted`, `cameraId` and requested `enabled`; it confirms Android accepted the call, not that the light was physically observed. Another camera user or Android policy can make the torch unavailable.

## Vibration

`vibrationControl` / `dikciz_vibration_control` requires `action`: `status`, `vibrate`, `waveform`, `effect` or `cancel`. Status returns `available`, `amplitudeControl` and named predefined `effects` with native support `supported`, `unsupported` or `unknown`. Support queries require Android 11; older devices report unknown. Vibrate accepts `durationMilliseconds` from 1 through 10000, default 250, and `amplitude` of -1 for Android's default or 1 through 255. Zero is invalid. Submission returns `state: submitted` and duration. Android sound/haptic settings can suppress the physical effect. Cancel stops Dikciz vibration and returns `state: cancelled`. Devices without a vibrator report unavailable and reject vibration.

Waveform requires `timingsMilliseconds`, 1 through 64 integer segments. Each is 0 through 10000 ms and their total must be 1 through 10000 ms. Without amplitudes, segments alternate off/on starting with off. Optional `amplitudes` must match the timing array; each is -1 for Android default, 0 off or 1 through 255. Segments with zero duration do not add time. Waveforms never repeat; cancel stops a submitted pattern.

Effect requires `effect`: `click`, `doubleClick`, `heavyClick` or `tick`, on Android 10 or newer. Android may use its predefined fallback when the exact hardware effect is unsupported. Submission returns the name and `fallbackPossible: true`; it does not claim native playback or measured output. Fields belonging to another action are rejected.

## System settings

`systemSetting` / `dikciz_system_setting` requires `action: get` or `set` and `setting`. Set also requires integer `value`; get rejects a value. Only the following settings are accepted:

| Setting | Allowed values |
| --- | --- |
| `brightness` | 0 through 255. Automatic brightness may override the effect. |
| `automaticBrightness` | 0 manual, 1 automatic. |
| `automaticRotation` | 0 fixed, 1 automatic. |
| `rotation` | 0 through 3 quarter-turns. Automatic rotation may override it. |
| `screenTimeoutMilliseconds` | 1 through 2147483647. Android policy may constrain the effective timeout. |

Reads return `setting`, stored `value` or null, and `writeAccess`. Writes require owner-granted Modify system settings, then return the actual stored read-back. Missing access uses `native_permission_systemSettings` and the standard owner-review flow. `settings_write_rejected` means the platform rejected the write. This is not an arbitrary Settings provider interface and does not grant protected settings privileges.

## Inventories

| Command | MCP tool | Results and requirements |
| --- | --- | --- |
| `bluetoothDevices` | `dikciz_bluetooth_devices` | Paired devices: address, name, type and bond state; adapter available/enabled. Requires Bluetooth access on Android 12+. No discovery scan or connection. |
| `usbDevices` | `dikciz_usb_devices` | Attached host devices: ID, name, vendor/product, class, interfaces and whether USB permission is already granted. See [USB access and transfers](usb-control.md). |
| `wifiScanResults` | `dikciz_wifi_scan_results` | Cached SSID/BSSID, frequency MHz, signal dBm, security capabilities and Android timestamp. Requires fine location. Does not trigger a new scan. Android may restrict or empty the cache. |
| `audioDevices` | `dikciz_audio_devices` | Device ID/type/name, input/output direction, sample rates and channel counts. No routing or playback. |
| `nfcStatus` | `dikciz_nfc_status` | Adapter availability and enabled state. No tag transaction. |
| `cameraCapabilities` | `dikciz_camera_capabilities` | Required `cameraId`. JPEG sizes, maximum digital zoom, exposure compensation bounds/EV step, sensor orientation and autofocus modes. See [camera controls](camera-control.md). |

Bluetooth, USB, Wi-Fi and audio inventories accept `limit` (1 through 200, default 50) and `offset` (0 through 1000000, default 0). Follow `hasMore` using offset plus returned item count. Their result includes `total`; device changes can change membership between requests. Bluetooth without an adapter returns `available: false` and an empty list. Camera IDs are strings; audio and USB device IDs are integers.

## Examples

```json
{"type":"torchControl","action":"status"}
{"type":"vibrationControl","action":"vibrate","durationMilliseconds":250}
{"type":"systemSetting","action":"get","setting":"brightness"}
{"type":"audioDevices","limit":5,"offset":0}
```

See [Bluetooth transactions](bluetooth-control.md), [NFC tags](nfc-control.md), [audio playback](audio-playback.md), [sensor and capture commands](native-device-control.md#sensors-camera-microphone-and-speech), [speech recognition](speech-recognition.md) and the [capability coverage](capability-coverage.md).
