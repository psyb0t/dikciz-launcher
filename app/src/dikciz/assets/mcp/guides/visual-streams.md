# Live images and speech

imageStreamStart needs source=camera/display and foreground startup. Camera requires Camera permission; display needs Accessibility. Notifications must remain enabled for owner Stop. Defaults: 640-pixel JPEG, 1000 ms interval, 60 seconds. Maximum duration one hour. Only one visual stream is active.

Read imageStreamRead with streamId and cursor. Consume frame dataBase64, dimensions and capturedAt, then retain nextCursor. Observe loss and terminal errors. imageStreamStop and the notification Stop close capture. Check hardwareReleaseConfirmed. imageStreamsList and imageStreamForget manage memory-only retained sessions.

speechStreamStart uses onDevice/system mode and optional language/timeoutMilliseconds. Microphone permission and a provider are required. speechStreamRead returns partial drafts and a final result; partial callbacks depend on the provider. Stop with speechStreamStop; list/forget use speechStreamsList/speechStreamForget.

Visual and speech replay expires five minutes after completion and is lost on process death. Read/list never start capture. Start/stop/forget are hidden and blocked in read-only mode.

speechTranscribeFile uploads an existing readable recording to profileId's OpenAI-compatible audio/transcriptions endpoint. Supply path and model. It never activates the microphone. Maximum file 32 MiB; timeoutMilliseconds defaults 30000, allowed 1000..300000. Optional language is two letters. Redirects and automatic retries are disabled. Check returned text and uploadedBytes. Use only the owner's intended provider.
