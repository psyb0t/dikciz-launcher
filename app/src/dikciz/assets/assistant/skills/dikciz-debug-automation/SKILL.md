---
name: "dikciz-debug-automation"
description: "Trace a failed or surprising automation and simulate its current Lua or JavaScript handler without real phone effects."
---

# Debug a phone automation

1. Discover automationHistory, automationHistoryEvent and automationSimulate schemas. Browse history with an exact kind, name or outcome filter and limit/offset. Follow hasMore instead of assuming one page is complete.
2. Read a record's traceId, then query that trace to link dispatched event, script or app handler, and commands through parentId. Interrupted work has an unknown final effect. A completed command means its executor returned; inspect provider-specific completion evidence when needed.
3. Read a retained event only when the task needs its payload. Source permissions are checked again. Payloads have separate time/count/byte retention and may be expired or omitted. History contains bounded metadata, not every Android action since the phone was made.
4. Simulate exactly one current scriptId, appId or source plus language against an event sample or event recordId. All command callbacks are intercepted, returned actions are not applied, and state is not saved. Recorded replay uses current source and current state snapshots, not historical source or subscription-policy decisions.
5. For branches that depend on command results, supply ordered replies with exact command type and a result object. These are fake results. Never present them as observations from the phone. No real command is executed, even a read.
6. Inspect commands, returned action kinds, result, outcome and sourceRevision. Compare that revision with the original handler history before blaming current source for old behavior. A missing handler, runtime error or absent fixture is evidence to fix, not permission to run live effects.
7. If a source change is requested, read its current file, preserve unrelated behavior, validate through simulation and report the actual result. Only execute effects when the owner has requested those effects.
