# Audio playback

Use audioDevices for output IDs. Upload a local audio file, or use the path from speechSynthesize/microphoneRecord. audioPlayback action=play requires path. Optional volumePercent=0..100 (default 100), loop=false, outputDeviceId and maximumDurationMilliseconds=1..86400000 (default 3600000). URLs are not accepted.

Play returns sessionId and asynchronous preparing state. Poll action=status with sessionId. Verify playing/completed/failed, not just accepted preparation. Failed state has error and optional Android decoder details. A concurrent play fails hardware_busy; stop the current session explicitly.

pause/resume require sessionId. seek requires sessionId and positionMilliseconds within duration; poll seeking=false. volume requires sessionId and volumePercent. stop and status allow no ID to target/inspect the current session. Supplying a stale ID fails instead of affecting a newer session.

Focus denial is explicit. Keep Dikciz foreground for start/resume on restricted Android versions. Focus loss pauses with pauseReason=audio_focus_lost and never auto-resumes. Output selection is a preference, not proof of physical routing or audible output.

Completion, stop, failure and the wall-clock lifetime release player/focus. Lifetime includes pauses and preparation. Sessions end on process death. This is not a background playback service or cellular call-audio injection.

Full guide: https://github.com/psyb0t/dikciz-launcher/blob/main/docs/audio-playback.md

## Recording

microphoneSession action=start returns sessionId and recording state. Open Dikciz and grant Android microphone permission before starting. Configuration fields: maximumDurationMilliseconds=1..86400000 (default 60000), maximumBytes=4096..1073741824 (default 67108864), inputDeviceId, sampleRateHz accepts 8000, 16000, 22050, 44100 or 48000 (default 44100), bitRate=8000..320000 (default 128000), channels=1..2 (default 1).

status optionally selects sessionId; stop/cancel require it. Stop or a duration/file limit finalizes M4A and returns completed with path/bytes/mimeType. Cancel discards output. Foreground loss, silencing and recorder/finalization errors fail and discard output; cleanupFailed exposes failed deletion. Poll final state before claiming a saved recording.

Shares ownership with microphoneRecord and speechRecognize. No overlapping captures, process persistence, background service or cellular call audio.
