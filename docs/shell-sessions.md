# Shell sessions for every binding

Shell sessions expose incremental output through ordinary HTTP, WebSocket, MCP, HTML and Lua commands. They reuse the same process-group runner as shellStream. They continue across client disconnects and launcher recreation within the same process. App process death ends the session and its retained history.

## Start

```json
{"type":"shellSessionStart","command":"printf first; sleep 1; printf second","root":false}
```

Root defaults to true, matching shell commands. Select root:false for the app sandbox. Android privileges and owner approval still apply. Start returns a streamId and current metadata; acceptance is not successful command execution. Read the terminal event for the actual outcome.

| Option | Default | Range |
| --- | --- | --- |
| timeoutMilliseconds | 30000 | 0 through 2147483647; zero explicitly runs until stopped or process exit. |
| chunkBytes | 4096 | 1 through 65536. |
| bufferBytes | 1048576 | 65536 through 8388608; must fit twice chunkBytes plus 1024 metadata bytes. |
| maximumEvents | 1024 | 1 through 4096 retained events. |
| retentionMinutes | 60 | 1 through 1440 after completion. |

At most four sessions run concurrently. At most sixteen active/terminal records remain in the process, with a total reserved buffer budget of 32 MiB. Forget terminal records or wait for retention expiry to free capacity. Expired records are pruned when another session command runs.

## Read and reconnect

shellSessionRead requires streamId. Start cursor at zero. Optional limit is 1 through 200, default 50. Optional waitMilliseconds is 0 through 30000, default zero; it waits for another event or completion when caught up. Follow nextCursor until active is false and hasMore is false. Pages also respect the shared response-byte budget, so fewer than limit events can be returned.

Events preserve sequence, event kind and binary outputBase64. Decode each output event and concatenate its bytes in order. Do not concatenate independently decoded UTF-8 strings when a character can span chunks. Started, heartbeat and complete events are not output bytes.

Completed metadata and the complete event report state, exitCode and total outputBytes. A nonzero command exit remains a normal exited session with that exit code. Failed startup can include a specific code such as root unavailability. Inspect it rather than treating a successful read as successful execution.

The ring retains the newest bounded history. historyGap and lostEvents explicitly report a cursor older than retained data; firstSequence identifies the earliest available event. Never claim complete output after a gap. Increase start-time buffer/event limits or consume sooner. A cursor ahead of produced events is invalid. Retained reads are repeatable and do not consume history.

## Stop, inspect and release

shellSessionStop requests cancellation by streamId. Keep reading until active is false. Cancellation kills the owned process group using the existing runner and does not undo prior effects.

shellSessionsList pages metadata newest first with offset/limit, total and hasMore. It exposes no output contents. This can recover IDs after a transport reconnect.

shellSessionForget removes a terminal session and its retained output. It refuses an active session; stop and observe completion first. Unknown, expired or forgotten IDs return shell_session_not_found. IDs and data do not survive process death.

MCP names: dikciz_shell_session_start, dikciz_shell_session_read, dikciz_shell_sessions_list, dikciz_shell_session_stop and dikciz_shell_session_forget. HTML uses dikciz.command and Lua uses its ordinary command bridge. Existing connection-owned shellStream/SSE streaming remains available to network clients. See [shell and files](files-and-apks.md) and [durable jobs](command-jobs.md).
