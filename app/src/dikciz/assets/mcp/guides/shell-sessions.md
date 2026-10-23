# Shell sessions on all bindings

Use shellSessionStart/Read/Stop/Forget and shellSessionsList through HTTP, WebSocket, MCP, HTML or Lua. They reuse the existing shell-stream runner. Sessions survive transport disconnects within the app process, not process death.

Start requires command. root defaults true; select false for the app sandbox. timeoutMilliseconds defaults 30000, zero explicitly disables the lifetime deadline. chunkBytes defaults 4096, maximum 65536. bufferBytes defaults 1 MiB, range 64 KiB..8 MiB and at least twice chunkBytes plus 1024. maximumEvents defaults 1024, range 1..4096. retentionMinutes defaults 60, range 1..1440 after completion. Four active sessions, sixteen retained records and 32 MiB total reserved buffer are enforced.

Read requires streamId. cursor starts at 0; limit defaults 50, maximum 200. waitMilliseconds=0..30000 long-polls when caught up. Follow nextCursor until active=false and hasMore=false. The response-byte budget can shorten a page below limit.

Output events carry outputBase64. Concatenate decoded bytes in sequence; a UTF-8 character may span chunks. Started/heartbeat/complete events carry no output. Inspect terminal state, exitCode and code; accepted startup or a successful read does not prove command success.

historyGap/lostEvents expose overwritten history. firstSequence identifies the earliest retained event. Never claim full output after a gap. Increase start-time limits or read sooner. A cursor ahead of output is invalid.

Stop requests owned-process cancellation; poll completion. Forget releases only a terminal record. List pages metadata without output and can recover IDs after reconnect. Expired records are pruned on subsequent session commands.

Network clients can still use connection-owned shellStream or MCP SSE. Use sessions for ordinary cross-binding access and reconnectable output.

Full guide: https://github.com/psyb0t/dikciz-launcher/blob/main/docs/shell-sessions.md
