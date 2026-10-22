# Sensors, location, camera and microphone

HTTP, WebSocket, MCP, HTML `dikciz.command` and Lua `context.requestJson` share execution, validation, permissions and outcomes. Discover live tool schemas before calling. Android still controls hardware access.

## Live sensors

`dikciz_sensor_readings` (`sensorReadings`) takes optional `sensorId`, `limit`, `offset` and `timeoutMilliseconds`. Defaults: all sensors, 50 items, offset 0, 1000 ms. Maximum page 200; sample timeout 1 through 5000 ms. Follow `hasMore` using offset plus returned count. Sensor IDs address this device inventory, including duplicate types.

Each item includes name, vendor, type, stringType, reportingMode, range and resolution. `available:true` has actual values, accuracy, monotonic timestampNanoseconds and ageMilliseconds. False has an explicit reason, such as permission_required, android_access_denied, registration_rejected, no_sample_in_window or waiting_for_trigger. Units and vector ordering follow Android's sensor type. Every request unregisters listeners. Poll only while needed. The Sensors page does this while visible; background Lua uses existing sensor subscriptions.

If an item has requirementId, request that access with `dikciz_request_device_access`, then explicitly retry. One denied sensor does not hide other sensors. A direct sensorId request for restricted hardware returns the shared permission error; autoRequestPermissions can request owner review.

## Weather location

`dikciz_location_get` (`locationGet`) accepts maxAgeMilliseconds (default 300000) and timeoutMilliseconds (default 10000, 0 through 120000). It uses a recent cached position or requests a fresh fix. Zero timeout reads cache only. Check available, source, age and accuracy. Missing permissions use the common owner-review flow. Disabled location and timeout return explicit unavailable reasons. placeName is optional; coordinates remain usable without reverse geocoding.

## Capture

`dikciz_camera_list` lists cameraId and facing without capture. `dikciz_camera_capture` accepts optional cameraId, maxDimension (default 1280, range 320 through 4096) and timeoutMilliseconds (default 30000, range 1 through 120000). No supported JPEG size within the requested dimension returns an error.

`dikciz_microphone_record` accepts durationMilliseconds (default 3000, range 1000 through 120000). It records microphone AAC audio in an M4A file. Camera and microphone require the appropriate Android permission and foreground eligibility. Open Dikciz before capture. Set autoRequestPermissions to true to request missing access, then explicitly retry. Busy, timeout and Android restrictions are errors.

Capture returns state:saved, path, mimeType and bytes after writing actual media. Read or download the returned path through file APIs. No caller filename is accepted. A transport timeout is not proof that capture failed; do not repeat automatically.

## Speech and cellular calls

`dikciz_speech_speak` requires text (at most 2000 characters) and accepts timeoutMilliseconds (default 30000). It speaks through the configured Android engine and returns state:spoken after completion, or an explicit failure. Device output is not call-stream injection.

`dikciz_call_audio` accepts action status, record or speak. Status reports captureSupported:false and injectionSupported:false on this ordinary app installation. Record and speak return call_audio_unsupported. Android cellular call audio needs privileged system integration. Microphone permission does not grant it. Existing callControl places, answers or ends calls subject to Android access.
