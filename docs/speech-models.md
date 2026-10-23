# Recognition languages and models

These operations inspect or prepare the installed Android recognition provider. They do not capture microphone audio. HTTP, WebSocket, MCP, HTML and Lua use the same command target. Language support depends on the selected provider and recognition request.

## Language inventory

`speechLanguages` / `dikciz_speech_languages` requires Android 13 or later. Optional mode defaults to onDevice, with no system-provider fallback. Explicit system selects the installed system recognizer. Optional language is a BCP-47 tag, defaulting to device language.

The paginated result contains language and availability: installedOnDevice, pendingOnDevice, supportedOnDevice or online. The same language can occur in several categories. Limit defaults to 50, maximum 200; offset defaults to zero, maximum 1000000. Follow hasMore using the returned item count. Each page queries the provider again, so membership can change after model downloads or provider updates.

Timeout defaults to 30000 ms, range 1 through 120000. Unsupported provider queries and provider errors fail explicitly. No empty inventory is invented to hide a failed query.

## Model request

`speechModelDownload` / `dikciz_speech_model_download` requires Android 14 or later and a language. It uses the same mode and timeout options. This is an explicit request that may consume network data and cause Android/provider confirmation. It does not grant consent itself.

| Result state | Meaning |
| --- | --- |
| available | The provider reported the model available. It may already have been installed. |
| scheduled | The provider scheduled work and ended observation. The model is not confirmed available. |
| failed | Startup or the provider failed. Inspect error. |
| unknown | Observation timed out or was interrupted. Provider work may continue. |

The command waits for a terminal callback or the selected deadline. Its response includes the process-owned sessionId, language, mode and provider progress when supplied. Use a durable job when the client cannot hold the request open.

## Observe progress

Call `speechModelStatus` / `dikciz_speech_model_status` from another connection while a model request is running. Without a sessionId it returns the latest request, including its ID. An optional sessionId refuses a different current request. States requested and downloading describe active observation; progressPercent is null until the provider reports progress.

A single support/model request owns the provider slot. A concurrent language query or model request returns hardware_busy. Cleanup releases that slot. The status is process-local and retains only the latest model request. It does not resume observation after a timeout, scheduled callback or process restart. Query speechLanguages again to verify later availability.

Android provides no cancellation guarantee for a provider model download. Closing the recognizer or cancelling a surrounding job ends Dikciz observation; it does not roll back network work. Never retry blindly after an unknown result.

Errors include speech_support_timeout, speech_model_timeout, speech_support_error_<Android code> and speech_model_error_<Android code>. A provider's insufficient-permissions callback uses the common permission-review flow. Platform version, installed provider and language support still apply.

See [recognition](speech-recognition.md), [durable jobs](command-jobs.md) and [command schemas](command-reference.md).
