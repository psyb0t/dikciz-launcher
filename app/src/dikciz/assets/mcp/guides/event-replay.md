# Replay automation events

Use eventJournalStatus, eventJournalRead and eventJournalConfigure through HTTP, WebSocket, MCP, HTML or Lua. The process-owned journal records actual automation-bus dispatches. Reads do not activate sensors or other native subscriptions.

Read status for journalId and lastSequence. Use cursor=lastSequence for future events or 0 for retained history. Read requires journalId; cursor defaults0, limit1..200(default50), waitMilliseconds0..30000(default0). Optional eventTypes is1..32 unique exact event names. Omit it for all types.

Each events item has sequence and the normal event document. Save nextCursor even when filters return no matches. Follow hasMore before waiting. Cursors are exclusive; duplicate reads replay retained events, and each reader keeps its own cursor. A future cursor is invalid. event_journal_restarted means the process epoch changed: read status and explicitly choose a new cursor.

Defaults: maximumEvents2048, bufferBytes8388608, retentionMinutes60. eventJournalConfigure accepts maximumEvents1..16384, bufferBytes65536..33554432, retentionMinutes1..1440. Omitted fields retain current values. Lower limits evict immediately. Limits/history reset on process death. Byte accounting measures encoded event JSON, not total heap overhead.

Pages contain at most262144 encoded event bytes and split only between events. historyGap/lostEvents report evicted sequence numbers before cursor, including nonmatching types. Documents over65536bytes become omitted:true markers with reason:event_too_large and originalBytes; original payload is absent. omittedEvents counts page markers. This is explicit loss, not silent truncation.

Current Android source access is checked again before disclosure. Missing source permissions or special access use the shared failure/review flow. autoRequestPermissions:true asks owner review where the requirement supports it, then retry. Clipboard replay requires foreground Dikciz. Missing Health Connect steps access returns health_connect_permission_required; grant it in automation settings.

Owner command bindings have the same authority as other direct native APIs. Lua subscription policies continue to control automatic delivery. Replay sees dispatched events after sensor coalescing, cannot recover stopped-process events, and has no durable acknowledgements. Use waitMilliseconds0 inside Lua event handlers to avoid blocking the shared event worker.

[Events](dikciz://docs/guides/events), [Permissions](dikciz://docs/guides/permissions), [Lua](dikciz://docs/guides/lua).
