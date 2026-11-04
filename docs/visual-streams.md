# Live images and speech

Camera/display frames, partial speech and recorded-file transcription use the same commands on HTTP, WebSocket, MCP, HTML and Lua. Streams use start, cursor read, stop and forget operations. A successful start is not a frame or transcript. Read the stream's actual events and terminal state.

## Camera and display frames

Call imageStreamStart with source=camera or source=display. Start while Dikciz is in the foreground. Camera requires Camera permission; display requires the owner-enabled Accessibility service. Notifications must be allowed so a foreground capture notification can show **Stop**.

The camera uses one persistent Camera2 session for successive JPEG frames. Display frames capture the visible Android screen, including other apps. Android secure windows and screenshot throttling still apply. Neither source records microphone audio.

Read imageStreamRead with streamId and an exclusive cursor. Each frame has dataBase64, mimeType=image/jpeg, width, height and capturedAt. Camera frames also report sensorOrientation. Use nextCursor for the next read. Cursor replay is bounded and reports loss; do not silently treat missing frames as continuous footage.

Defaults are one frame per second, 640-pixel maximum edge and a 60-second maximum duration. The schema allows intervals from 500 to 10,000 ms, edges from 320 to 1280 pixels and durations up to one hour. At most one visual stream is active. The default replay buffer is 1 MiB with 16 events, configurable within the installed schema. JPEG frames are bounded to 192 KiB.

Use imageStreamStop or the owner notification's **Stop**, then inspect active and hardwareReleaseConfirmed. Late camera closure can make release confirmation lag cancellation. Retained frames remain readable for five minutes after completion or until imageStreamForget. imageStreamsList lists active and retained sessions. Frames stay in process memory and do not survive process death.

## Partial speech

Call speechStreamStart with mode=onDevice or mode=system and optional language and timeoutMilliseconds. Android microphone permission, foreground startup and an available recognition provider are required. Other recording and recognition commands share microphone ownership.

Read speechStreamRead for partial events and a final event containing the ordinary recognition result. Providers may emit no partial callbacks. A final result or explicit failure is authoritative; a partial draft can change.

Use speechStreamStop to cancel, then inspect active and hardwareReleaseConfirmed. speechStreamsList and speechStreamForget manage retained sessions. Replay holds up to 64 events and 1 MiB, expires five minutes after completion and is lost on process death.

## Transcribe an existing recording

speechTranscribeFile takes path, profileId and model. It uploads that file to the saved OpenAI-compatible provider's audio/transcriptions endpoint using its encrypted API key. It does not activate the microphone. Anthropic chat profiles are not audio transcription endpoints.

Files must be readable by Dikciz, use a supported audio extension and fit within 32 MiB. Optional language is a two-letter code. timeoutMilliseconds defaults to 30 seconds and can be set from 1 to 300 seconds. Redirects and automatic retries are disabled, so a redirect cannot forward the credential or recording to another host. Responses are capped at 256 KiB. Returned text, uploadedBytes and microphoneUsed=false describe the completed request.

Only upload recordings the owner intends to send to that provider. File transcription, stream start, stop and forget are mutating operations and are hidden and blocked in read-only assistant mode. Reading an existing stream or listing sessions starts no capture.

## Resource controls

Activity & resources lists active visual captures. resourceControl can target imageStream or speechStream with its exact id. It uses the same stop operation as the dedicated commands. See [activity and resources](operations.md) and the [command reference](command-reference.md) for discovery and limits.
