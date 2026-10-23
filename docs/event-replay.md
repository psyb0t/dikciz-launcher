# Replay automation events

`eventJournalStatus`, `eventJournalRead` and `eventJournalConfigure` work through ordinary HTTP, WebSocket, MCP, HTML and Lua. The journal records events actually dispatched by the automation bus, including custom events and configured native sources. Reading it does not activate sensors, location or other subscriptions.

## Read and resume

1. Read status and save `journalId`. Use `lastSequence` as the cursor to start with future events, or 0 to request retained history.
2. Read with that `journalId` and `cursor`. Optional `eventTypes` contains 1 through 32 unique exact names. Omit it for every event type.
3. Process `events`, each containing `sequence` and the ordinary `event` document. Persist `nextCursor`, including when no matching events were returned.
4. Continue while `hasMore` is true. Otherwise poll again or set `waitMilliseconds`, 0 through 30000, default 0. `limit` is 1 through 200, default 50.

~~~json
{"type":"eventJournalRead","journalId":"00000000-0000-0000-0000-000000000000","cursor":0,"eventTypes":["example.refresh"],"waitMilliseconds":30000}
~~~

Replace the example epoch with status from the running app. Cursors are exclusive sequence numbers. Duplicate reads replay the same retained events; different clients keep independent cursors. Filters advance over unmatched events. A cursor ahead of the journal is invalid. `event_journal_restarted` means the process epoch changed; inspect status and choose a new starting point rather than silently treating old cursors as current.

## Bounds and loss

Defaults are 2048 events, 8388608 encoded JSON bytes and 60 minutes. Configure `maximumEvents` (1 through 16384), `bufferBytes` (65536 through 33554432) and `retentionMinutes` (1 through 1440). Omitted fields retain their current values. Lower limits immediately evict old history. Configuration and history live in memory and reset with the process.

Status reports retained count/encoded bytes, eviction count, first/last sequence and limits. The byte budget measures encoded event documents, not total Java heap overhead. A page contains at most 262144 encoded event bytes. Large pages split between whole events and set `hasMore`. No event is split.

`historyGap` and `lostEvents` report sequence numbers no longer retained before the supplied cursor. With a filter, the loss count includes all evicted event types; it cannot prove how many matching events were lost. Individual documents over 65536 encoded bytes become a small `omitted:true` marker with `reason:event_too_large` and `originalBytes`. The page reports `omittedEvents`. Markers do not contain the original payload.

## Access and delivery

The journal rechecks current Android source access before returning retained payloads. Revoked contacts/calendar/SMS/phone/location/Bluetooth/sensor, notification-listener or Accessibility access fails the read through the shared error path. `autoRequestPermissions:true` uses the existing owner-review flow for supported requirements; grant access and explicitly retry. Retained clipboard content requires Dikciz in the foreground. Health Connect step history checks current provider grants and returns `health_connect_permission_required` when missing; grant step reading through the existing automation settings screen.

These are owner commands with the same authority as other direct native commands. Installing and enabling a script or widget grants its command binding that authority. Lua event-subscription policies still govern automatic delivery; a native owner command is a separate explicit read.

Replay observes dispatched events, after sensor coalescing. It cannot recover sensor samples discarded before dispatch, native callbacks that never arrived or events from a stopped process. It provides at-least-once consumption within retained history, not durable delivery or acknowledgements. Keep Lua reads nonblocking while handling an event because Lua and native event dispatch share the event worker.

For sources and policies, see [scripting](scripting-tutorial.md). For custom event setup, see [MCP resources](mcp-resources.md).
