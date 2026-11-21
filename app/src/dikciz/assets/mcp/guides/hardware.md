# Sensors, location, camera and microphone

HTTP, WebSocket, MCP, HTML `dikciz.command` and Lua `context.requestJson` share execution, validation, permissions and outcomes. Discover live tool schemas before calling. Android still controls hardware access.

## Live sensors

`dikciz_sensor_readings` (`sensorReadings`) takes optional `sensorId`, `limit`, `offset` and `timeoutMilliseconds`. Defaults: all sensors, 50 items, offset 0, 1000 ms. Maximum page 200; sample timeout 1 through 5000 ms. Follow `hasMore` using offset plus returned count. Sensor IDs address this device inventory, including duplicate types.

Items carry sensor identity, reportingMode, range and resolution. Available readings include values, accuracy, monotonic timestampNanoseconds and ageMilliseconds. Unavailable items give a reason. Units and vector order follow the Android sensor type. Requests unregister their listeners. The Sensors page polls while visible. Background Lua uses sensor subscriptions.

If an item has requirementId, request that access with `dikciz_request_device_access`, then explicitly retry. One denied sensor does not hide other sensors. A direct sensorId request for restricted hardware returns the shared permission error; autoRequestPermissions can request owner review.

## Weather location

`dikciz_location_get` (`locationGet`) accepts maxAgeMilliseconds (default 300000) and timeoutMilliseconds (default 10000, 0 through 120000). It uses a recent cached position or requests a fresh fix. Zero timeout reads cache only. Check available, source, age and accuracy. Missing permissions use the common owner-review flow. Disabled location and timeout return explicit unavailable reasons. placeName is optional; coordinates remain usable without reverse geocoding.

## Capture

`dikciz_camera_list` lists cameraId and facing without capture. `dikciz_camera_capture` accepts optional cameraId, maxDimension (default 1280, range 320 through 4096) and timeoutMilliseconds (default 30000, range 1 through 120000). Quality defaults to 95 (1..100); optional jpegOrientation is 0/90/180/270. cameraCapabilities supplies zoom/exposure/focus limits for zoomPercent (100=1x), exposureCompensation steps and autofocusMode. Unsupported controls fail before opening hardware. Success waits for JPEG bytes and Android completion; requestedControls and captureMetadata distinguish submitted values from actual frame results, with null for missing metadata. No focus convergence is claimed.

`dikciz_microphone_record` accepts durationMilliseconds (default 3000, range 1000 through 120000). It records microphone AAC audio in an M4A file. Camera and microphone require the appropriate Android permission and foreground eligibility. Open Dikciz before capture. Set autoRequestPermissions to true to request missing access, then explicitly retry. Busy, timeout and Android restrictions are errors.

Capture returns state:saved, path, mimeType and bytes after writing media. Download the returned path through file APIs. Inspect the outcome before retrying a timed-out capture.

## Speech and cellular calls

`dikciz_speech_speak` requires text (at most 2000 characters) and accepts timeoutMilliseconds (default 30000). It speaks through the configured Android engine and returns state:spoken after completion, or an explicit failure. Device output is not call-stream injection.

`dikciz_call_audio` status reports whether cellular capture or injection is supported. On an ordinary install, record and speak return call_audio_unsupported. Cellular audio needs privileged system integration. Use callControl to place, answer or end calls.

## Raw sensor sessions

Start dikciz_sensor_stream_start with an ID from sensorReadings. Set sampling, history, lease or background capture as needed. Read with dikciz_sensor_stream_read using nextSequence. Check lostSamples, discardedSamples, observed rate and terminal status. Stop with dikciz_sensor_stream_stop. Custom apps can subscribe to owner-only raw batches and run their own detection.
