# Stream logcat

Use a bounded diagnostic window or retain the stream's ownership information so it can be stopped. For ordinary app-visible logs:

```json
{"command":"logcat -v threadtime","root":false,"timeoutMilliseconds":60000}
```

Pass this to MCP `dikciz_shell_stream`, or HTTP/WebSocket `shellStream`. The supplied CLI supports `--stream-raw` to print decoded bytes as they arrive. Without it, the CLI emits JSON Lines. Ordinary `shell` is a single bounded response and has a 30-second default deadline.

An MCP client must keep the same session and request `info` or `debug` in `params._meta["io.modelcontextprotocol/logLevel"]`, alongside the negotiated protocol metadata. Read SSE `notifications/message` records from logger `dikciz.shell`, then the final tool result. The supplied client handles this automatically.

Preserve the `streamId` from `started`. Output events carry increasing `sequence` and base64 `outputBase64`. A complete event includes final state, exit code and output bytes. Stop through `dikciz_shell_stream_stop` in the same MCP session, or close the originating response. Closing the stream stops its owned process group. A new session cannot cancel another session's stream.

Unprivileged logcat reports only what Android permits the app to read. Request root only when authorized and available. Streams do not replay after reconnect. Keep credentials and unrelated private logs out of shared diagnostics.

[Streaming contract](dikciz://docs/guides/shell).
