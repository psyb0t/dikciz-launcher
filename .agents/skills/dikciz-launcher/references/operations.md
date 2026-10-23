# Files, notifications and durable jobs

Use discovered ordinary commands across HTTP, WebSocket, MCP, HTML and Lua. Shared validation and Android permission review apply to all of them. Prefer native operations over screen navigation.

## Files

fileManage supports stat, copy, move, delete and search. Select action and an absolute path. Copy and move require destination and never overwrite. Delete defaults to nonrecursive; recursive=true explicitly permits a tree. maximumEntries bounds recursive work. Search matches literal names and supports offset/limit pagination. Follow hasMore, and inspect scanComplete. Symlinks are not followed during recursive work.

Credential records and their atomic backup aliases are denied by the file APIs, including ancestor moves/deletes. This is not a sandbox for an independently authorized root shell. Read the [file guide](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/files-and-apks.md) before modifying files.

## Notifications

notificationsList returns active keys and action metadata. notificationManage supports dismiss/snooze by notificationKey and inline reply by actionToken plus nonblank text. Reply tokens are single-use and must support free-form input. Plain action execution cannot substitute for inline reply. Android notification-listener access is required. Submitted replies are not delivery receipts.

See [notification management](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/notification-management.md).

## Durable work

jobStart accepts a full ordinary command and caller idempotencyKey. Identical content deduplicates; changed content conflicts. Poll jobStatus by jobId for the original complete response, including permission recovery. jobsList pages metadata; fetch results separately.

Two workers run concurrently. Up to 256 records persist for retentionHours, default 168 and range 1..720. jobs cannot enqueue jobs or change remote authentication. Job completion does not strengthen the nested operation's claim.

jobCancel prevents queued execution or requests interruption of running work. It never rolls back effects. After process loss, queued/running records become interrupted with unknown outcome and are never replayed. Inspect external effects before explicitly submitting replacement work.

See [durable jobs](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/command-jobs.md), or MCP resource dikciz://docs/guides/jobs.

## Peripheral and media work

Read [hardware and speech](hardware-and-speech.md), then the focused Bluetooth, NFC, USB, location or audio guide. Bluetooth transactions use explicit peer addresses and UUIDs. NFC writes can select a previously read tag ID. Writes may have partial effects after failure. A transport result is not proof a peripheral applied a command.

## Event replay

Use eventJournalStatus, eventJournalRead and eventJournalConfigure to resume actual custom/native automation events through any ordinary binding. Save journalId and nextCursor. Inspect historyGap, lostEvents, omittedEvents and restart errors. Current Android source access is checked before retained content is returned. Reads do not enable subscriptions. Use nonblocking reads inside Lua event handlers. Read [event replay](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/event-replay.md) or MCP resource dikciz://docs/guides/event-replay.

## Incremental shell output

Use shellSessionStart, shellSessionRead, shellSessionStop, shellSessionsList and shellSessionForget for the same incremental output procedure across HTTP, WebSocket, MCP, HTML and Lua. Follow nextCursor and inspect historyGap/lostEvents, terminal state and exitCode. Sessions survive transport disconnects within one app process. Read [shell sessions](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/shell-sessions.md) for bounds, retention and explicit release.

## Live microphone audio

Use microphoneStreamStart/Read/Stop, microphoneStreamsList and microphoneStreamForget for shared PCM16 capture and cursor replay. Start while Dikciz is foreground; the dedicated microphone foreground service can continue behind another app. Inspect historyGap, terminal state and hardwareReleaseConfirmed. Owner Stop is available through its notification, subject to Android notification access. Read [microphone streams](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/microphone-streams.md) or MCP resource dikciz://docs/guides/microphone-streams. Audio is process-owned memory; explicit saves or durable jobs may persist returned samples. This does not capture cellular calls.
