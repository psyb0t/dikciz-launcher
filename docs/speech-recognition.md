# Speech recognition

Speech recognition is microphone input converted to text. `speechSpeak` is separate text-to-speech output; `microphoneRecord` saves an audio file without transcribing it.

## Commands

| Command | MCP tool | Fields |
| --- | --- | --- |
| `speechRecognitionStatus` | `dikciz_speech_recognition_status` | None. |
| `speechRecognize` | `dikciz_speech_recognize` | Optional `mode`, `language`, `timeoutMilliseconds`. |
| `speechRecognitionStop` | `dikciz_speech_recognition_stop` | None. |

All ordinary HTTP, WebSocket, MCP, HTML and Lua calls share validation, Android access and results. The supplied Python client extends its response wait to cover the recognition deadline. Other clients must configure their own tool timeout accordingly.

## Availability and privacy

Check `recognitionAvailable` and `onDeviceAvailable` in status before starting. They report the installed Android recognition services, not a built-in Dikciz model. `listening` describes an active Dikciz recognition request. A language model may still be unavailable even when its service exists.

`mode` defaults to `onDevice`. This strictly uses Android's on-device recognition service and never falls back to the system network recognizer. It fails with `speech_on_device_unavailable` if unsupported. `mode: system` explicitly selects the installed default recognition service, which may send audio to its provider. Provider availability, connectivity and installed language models determine its behavior.

Open Dikciz before starting. Android microphone permission and foreground eligibility are required. Missing permission uses `native_permission_cameraAndMicrophone` and the common `permissionRequest`; use `autoRequestPermissions: true` for owner review, then explicitly retry. Recognition does not grant access or click Android consent automatically.

## Request and result

`language` is an optional BCP-47 tag such as `en-US`; otherwise Android's current device language is used. `timeoutMilliseconds` defaults to 30000 and accepts 1 through 120000. A second concurrent request returns `hardware_busy`.

```json
{"type":"speechRecognize","mode":"onDevice","language":"en-US","timeoutMilliseconds":30000}
```

Successful completion returns `state: recognized`, `text`, `alternatives`, `confidence` and the requested `mode`. At most five alternatives are returned, each limited to 8192 characters. Confidence entries are numbers between zero and one when supplied by Android, otherwise null. Text is returned as data and is not executed or logged as a command.

Recognition is one utterance, not an always-listening assistant. This command does not transcribe an existing file or publish partial results. Use `speechRecognitionStop` from another connection to cancel. It returns `cancelled` after recognizer cleanup, or `idle` if nothing is active. The original recognition call returns `speech_cancelled`. Completion, errors and deadlines destroy the Android recognizer.

## Speech output controls

`speechVoices` / `dikciz_speech_voices` lists the installed TTS engine's voices. Use `limit` and `offset`, follow `hasMore`, and inspect `total`. Each item has `name`, BCP-47 `language`, `networkRequired`, `quality` and `latency`. `defaultVoice` names the engine's current choice when available. Listing voices does not download a model or play audio.

`speechSpeak` accepts optional `voiceName`, `language`, `ratePercent` and `pitchPercent` alongside text and timeout. Defaults preserve the engine's voice selection with rate and pitch at 100 percent. Rate and pitch accept 25 through 400. An explicit voice takes precedence over language; choose a returned name. Network-required voices may send text to the engine provider. Unsupported language, missing voice and rejected configuration return distinct errors.

`speechStop` / `dikciz_speech_stop` stops active Dikciz output. It returns `cancelled`, or `idle` when no pending utterance exists; the original speak call returns `speech_cancelled`. A cancelled request may still be releasing its engine briefly, so wait for its result before starting another. Output completion is the Android engine callback, not acoustic proof that the speaker was audible.

## Saved speech audio

`speechSynthesize` / `dikciz_speech_synthesize` uses the same text, voice, language, rate, pitch and timeout fields as speechSpeak. It synthesizes a WAV without playing it and returns state=synthesized, path, sizeBytes and mimeType. Read the actual file through fileRead/download. speechStop also cancels synthesis. Incomplete files are removed after cancellation/failure; successful files remain until explicitly removed. Synthesis and spoken output share one active TTS owner.

## Errors

| Code | Meaning |
| --- | --- |
| `speech_on_device_unavailable`, `speech_service_unavailable` | No matching installed recognition service. |
| `speech_language_unsupported`, `speech_language_unavailable` | Language unsupported or model unavailable. |
| `speech_no_input`, `speech_no_match` | No speech or no recognized match. |
| `speech_audio_error`, `speech_client_error`, `speech_server_error`, `speech_server_disconnected`, `speech_too_many_requests` | Android recognizer failure. |
| `speech_network_error`, `speech_network_timeout` | System provider could not complete its network work. |
| `speech_timeout`, `speech_cancelled` | Dikciz deadline or explicit cancellation. |
| `speech_service_timeout`, `speech_cleanup_timeout` | Main-thread discovery or cleanup did not finish within five seconds. |
| `speech_start_failed`, `native_access_denied` | Android rejected startup. |
| `speech_android_error_<number>` | Additional Android provider error. |

Do not report recognized speech when the operation failed. A timed-out client connection is not proof the Android operation has stopped; inspect status or explicitly cancel it.

## Examples

```bash
python3 scripts/dikciz-control.py mcp dikciz_speech_recognition_status --host phone.example --port 19002
python3 scripts/dikciz-control.py mcp dikciz_speech_recognize --host phone.example --port 19002 --arguments '{"mode":"onDevice","language":"en-US"}'
python3 scripts/dikciz-control.py http speechRecognitionStop --host phone.example --port 19002
```

HTML uses `await dikciz.command("speechRecognize", {mode:"onDevice"})` after `dikciz-ready`. Lua uses `context.requestJson({type="speechRecognize",mode="onDevice"})` and reads the returned command envelope. Android access applies equally to both.

Reference: [Android SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer).

## Languages and recognition models

Use speechLanguages for provider language inventory, speechModelDownload for an explicit download request and speechModelStatus for current progress. Android version and provider support apply. See [language and model management](speech-models.md) for pagination, consent, scheduled/available states and timeout behavior.
