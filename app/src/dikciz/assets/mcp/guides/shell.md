# Shell and streaming

`shell` runs `/system/bin/sh`. Pass `root: false` for ordinary app-permission execution. Omitting `root` requests root in the existing contract. `root: true` needs a working, owner-approved `su`; denial does not silently switch execution scope.

Ordinary commands default to `timeoutMilliseconds: 30000` and `maxOutputBytes: 65536`. Requests can choose a timeout from 1 through 2147483647 milliseconds and output from 1 through 1048576 bytes. Allow transport time beyond the process deadline. Read `executionScope`, `exitCode`, `output` and `outputTruncated`. Nonzero exit is a completed process result. A timeout is an error. Truncation means output exceeded the requested byte budget.

Use `shellStream` for continuous output. Its default timeout is zero, meaning run until stopped or disconnected. `chunkBytes` defaults to 4096, maximum 65536. There is no cumulative output ceiling; slow consumers are disconnected. Output is base64 to preserve binary bytes and split UTF-8. Four streams are permitted per listener.

WebSocket delivers stream events on the command connection. HTTP uses SSE. MCP uses SSE `notifications/message` followed by a final tool result. MCP requests must opt in to `info` or `debug` logging in per-request metadata; the supplied client does this.

Keep the originating connection/session and `streamId`. Use `shellStreamStop` from its owner or close the response to stop the process group. Reconnection starts a new process and does not replay output.

An app shell differs from ADB shell. Other apps' private data, full logcat, force-stop and reboot generally require separate privileges. Never claim a shell command succeeded without checking its exit status and intended effect.

[Stream logcat](dikciz://docs/recipes/stream-logs).
