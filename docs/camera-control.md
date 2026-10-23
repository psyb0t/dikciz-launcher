# Camera capture controls

Use `cameraList`, then `cameraCapabilities` for the selected `cameraId`. Ordinary HTTP, WebSocket, MCP, HTML and Lua use the same `cameraCapture` command. Camera access and Android foreground eligibility are required. Missing access uses the common permission-review flow; explicitly retry after the owner grants it.

## Request

| Field | Behavior |
| --- | --- |
| `cameraId` | Camera inventory ID. Defaults to the first camera. |
| `maxDimension` | Largest allowed JPEG dimension, 320 through 4096, default 1280. The largest supported fitting size wins. |
| `timeoutMilliseconds` | Overall capture wait, 1 through 120000, default 30000. |
| `jpegQuality` | JPEG quality, 1 through 100, default 95. |
| `jpegOrientation` | Optional 0, 90, 180 or 270 degrees. Android may rotate pixels or encode EXIF orientation. |
| `zoomPercent` | Optional centered sensor crop, 100 through 10000, bounded by `maximumDigitalZoom × 100` for this camera. It does not select another physical lens. |
| `exposureCompensation` | Optional integer steps within the camera's reported range. Multiply by `exposureCompensationStepEv` to obtain EV. |
| `autofocusMode` | Optional Android mode from `autofocusModes`: 0 off, 1 auto, 2 macro, 3 continuous video, 4 continuous picture, 5 extended depth of field. |

~~~json
{"type":"cameraCapture","cameraId":"0","jpegQuality":90,"jpegOrientation":90,"zoomPercent":150}
~~~

Select the camera and zoom from actual discovery rather than assuming that this example ID or zoom exists. Unspecified focus, orientation, exposure and zoom retain the still-capture template defaults. The command makes one still request; it does not run a preview or a separate autofocus/exposure convergence sequence.

## Result and files

Success requires both written JPEG bytes and Android's completed capture callback. The result includes `state:saved`, generated `path`, `mimeType:image/jpeg`, `bytes`, `cameraId`, configured output `width` and `height`, `requestedControls` and `captureMetadata`.

Requested controls describe the submitted settings. Metadata contains Android's actual frame number, sensor timestamp, JPEG quality/orientation, exposure compensation, autofocus mode/state, auto-exposure state and crop rectangle. Missing result fields are JSON null. Sensor timestamps are monotonic camera timestamps, not UTC. Requested values do not prove focus convergence, exposure quality or physical scene contents. Android can adjust a requested crop to match output aspect ratios.

Read or download the file through the ordinary file APIs, then remove it when finished. Capture filenames are generated under app-specific external capture storage. No caller output path is accepted. Width and height describe the configured camera output; clients must honor JPEG/EXIF orientation when displaying it.

Unsupported device ranges or modes return `camera_control_unsupported` before opening the camera. Invalid types and generic bounds return `validation_failed`. Busy, disconnected, configuration, image-write, capture and deadline failures are explicit. Failed or timed-out requests close the session/device/reader and remove incomplete files. Late open callbacks close their device before releasing ownership. A client disconnect does not prove capture cancellation; inspect a durable command job when reconnectable results are needed.

Preview streams and managed multi-command recording sessions remain separate capability gaps.

## Bounded video

`cameraVideoRecord` / `dikciz_camera_video_record` saves foreground H.264 video in an MP4 container. Discover `videoSizes` and `frameRateRanges` through `cameraCapabilities` first. Size/rate/encoder combinations can still be rejected by Android.

| Field | Behavior |
| --- | --- |
| `cameraId` | Defaults to the first discovered camera. |
| `durationMilliseconds` | 1000 through 120000, default 3000. |
| `timeoutMilliseconds` | 1 through 300000, default 30000. Must cover duration and allow camera startup. |
| `maxDimension` | 320 through 1920, default 1280. Largest fitting recorder size wins. |
| `frameRate` | 5 through 60, default 30. Chooses the narrowest advertised camera range containing it. |
| `bitRate` | H.264 bits per second, 500000 through 20000000, default 4000000. |
| `orientation` | MP4 playback hint 0, 90, 180 or 270, default 0. Source pixels are not rotated. |
| `withAudio` | Default false. True additionally requires and owns microphone access, recording mono AAC. |

Photo and video requests share camera ownership. Audio-enabled video shares microphone ownership with the existing recording and recognition APIs. Competing requests return `hardware_busy`. Android foreground loss, camera/recorder errors and deadlines fail the operation and discard incomplete files.

Success requires recorder finalization, completed camera frames and Android media inspection. Results include the saved path, `mimeType:video/mp4`, bytes, actual `durationMilliseconds`, dimensions, rotation and `hasAudio`. Requested duration/frame rate and the chosen camera FPS range remain separate from observed metadata. `observedCaptureFrames` counts camera callbacks, not encoded output frames.

Read/download the completed file through ordinary file APIs and remove it when finished. Native startup and recording checks respect the overall deadline; blocking platform stop/cleanup calls may take additional time. A finalized container proves encoded media exists, not physical image or sound quality. No caller output path, background recording service or preview is provided.

Use [durable jobs](command-jobs.md) for reconnectable results. Cancel requests interruption; poll status and confirm cleanup before replacement work. In-process calls from HTML and Lua use the same command, permissions and result. Lua's native host request time is excluded from its script execution budget.
