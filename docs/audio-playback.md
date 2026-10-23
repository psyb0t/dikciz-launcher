# Audio playback sessions

`audioPlayback` / `dikciz_audio_playback` controls one process-owned Android MediaPlayer session through HTTP, WebSocket, MCP, HTML and Lua. It plays a readable local regular file. Upload files through the file API or use the path returned by speech synthesis or microphone recording. URLs are not accepted.

## Start and observe

```json
{"type":"audioPlayback","action":"play","path":"/sdcard/Dikciz/example.wav","volumePercent":50}
```

Start returns a new `sessionId` and asynchronous preparation state. Poll `action: status` with that ID. States are idle, preparing, playing, paused, completed, stopped, expired and failed. A preparing result is not playback success. Failed state includes an error and, for decoder errors, Android error numbers.

Play accepts optional `volumePercent` from 0 through 100, default 100; `loop`, default false; `outputDeviceId` from the output audio inventory; and `maximumDurationMilliseconds` from 1 through 86400000, default 3600000. The deadline includes preparation and paused time. A second play request fails with hardware_busy until the existing session ends or is stopped.

The status reports media duration after preparation, current position, player-reported isPlaying, requested volume and preferred output. Android 9 and later can select an output and report the routed output when available. Preferred output is a request; the actual route and physical audibility remain Android/device controlled.

## Control

| Action | Parameters |
| --- | --- |
| status | Optional sessionId. Without it, inspect the current or last session. |
| pause, resume | Required current sessionId. |
| seek | Required sessionId and positionMilliseconds, no later than the media duration. |
| volume | Required sessionId and volumePercent. |
| stop | Optional sessionId. Repeated stops preserve the terminal state. |

A stale session ID fails rather than affecting a newer session. Controls other than stop reject a preparing player. Seek is asynchronous: poll until seeking is false before relying on the new position. Completion, stop, expiry and failure release the player and audio focus. The last metadata remains available until a new session or process restart. Sessions do not survive process death.

## Audio focus

Playback requests Android media audio focus. Denial becomes audio_focus_denied. Keep Dikciz in front when starting or resuming; Android versions can restrict focus acquisition from the background. Focus loss pauses playback and records pauseReason: audio_focus_lost. It does not automatically resume after another app finishes. Explicit resume requests focus again.

The API does not take a screen-on lock, suppress other apps, grant storage access or provide an always-running playback service. A player-reported playing state is not proof that a speaker was audible.

See [audio inventory](hardware-controls.md), [speech synthesis](speech-recognition.md) and the [command reference](command-reference.md).

## Managed microphone recording

`microphoneSession` / `dikciz_microphone_session` supports start, status, stop and cancel. Start requires foreground eligibility and microphone permission. It returns sessionId and recording state. The existing bounded microphoneRecord and speechRecognize commands share the same ownership slot and cannot overlap a session.

Start options: maximumDurationMilliseconds from 1 through 86400000, default 60000; maximumBytes from 4096 through 1073741824, default 67108864; inputDeviceId from the input inventory on Android 9+; sampleRateHz of 8000, 16000, 22050, 44100 or 48000, default 44100; bitRate from 8000 through 320000, default 128000; and channels of 1 or 2, default 1. Android can reject unsupported encoder/device combinations.

Status optionally selects the current sessionId. Stop and cancel require it, preventing stale requests from affecting a newer session. Successful stop or a reached duration/file limit finalizes M4A and reports completed with path, bytes, mimeType and duration. Poll status to distinguish a successfully saved recording from recording or failed state.

Cancel discards incomplete output and reports cancelled. Foreground loss, Android silencing, recorder errors or finalization failure report failed and remove incomplete output. cleanupFailed reports a file that could not be removed. Recording sessions do not survive process death and are not background services or cellular call recording.
