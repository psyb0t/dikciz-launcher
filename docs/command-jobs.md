# Durable command jobs

`jobStart`, `jobStatus`, `jobsList` and `jobCancel` share the ordinary command dispatcher across HTTP, WebSocket, MCP, HTML and Lua. Use them when a request should continue after its transport disconnects. Shell streaming remains a connection-owned stream and is not a job.

## Submit and retrieve

Submit a full ordinary command and a unique `idempotencyKey`:

```json
{"type":"jobStart","idempotencyKey":"example-sample-1","command":{"type":"sensorReadings","timeoutMilliseconds":1000}}
```

The response gives `jobId`, `state`, creation/expiry times, cancellation state and `deduplicated`. An identical key and command return the same job. Changed content returns `job_idempotency_conflict`. Retention is part of the original submission; reusing the key does not extend it. Set `retentionHours` from 1 to 720; default 168 hours. There are at most 256 retained jobs and two concurrent workers. Expired terminal records are removed when a job command runs. Full capacity returns `job_capacity`.

Use `jobStatus` with `jobId`. Its `response` preserves the original ordinary command envelope, including result data, errors and permission recovery. `jobsList` accepts `limit` 1 through 200, default 50, and nonnegative `offset` up to 1000000. It returns metadata, `total` and `hasMore`, newest first. Results are omitted from the list. Follow the returned count and fetch each needed result separately.

| State | Meaning |
| --- | --- |
| `queued` | Persisted and waiting for a worker. |
| `running` | The shared dispatcher is executing the command. |
| `completed` | The original command returned a result envelope. Inspect that result to determine its actual effect. |
| `failed` | The original command returned an error, or job execution/storage failed. |
| `cancelled` | Queued work was cancelled before execution. |
| `interrupted` | A previous process stopped before recording a final outcome. Work is not replayed. |

`outcomeKnown` is false when process loss or an unexpected execution failure leaves the effect unknown. A completed command can itself report unavailable, submitted, pending or permission-required state. Job completion does not strengthen that claim.

## Cancellation and recovery

`jobCancel` cancels queued work or requests interruption of a running worker. `cancellationRequested` remains visible. Android operations may already have caused effects, may ignore interruption or may finish concurrently. A cancellation request never rolls back effects. Poll the final result before deciding whether to retry.

Records survive transport disconnects, launcher recreation and process restart. On startup, retained queued/running work becomes `interrupted` with unknown outcome. Automatic replay would duplicate non-idempotent actions, so the caller must inspect the external effect and explicitly submit any replacement work.

Job commands cannot enqueue other job commands or mutate remote authentication. Arguments and results are kept in app-private storage and excluded from operation logs. Requests still use the existing authentication and safe-mode rules. Permissions are checked when work executes. Set `autoRequestPermissions` on the nested command, or inherit it from `jobStart`, to request owner review. Granting permission does not retry a failed job automatically.

MCP tools: `dikciz_job_start`, `dikciz_job_status`, `dikciz_jobs_list`, `dikciz_job_cancel`. HTML uses `dikciz.command`; Lua uses its ordinary Android-command bridge. Read [command discovery](automation-control.md), [permissions](native-device-control.md) and [coverage](capability-coverage.md).

Malformed or oversized retained records fail job initialization with `job_storage_invalid`. Repeated calls keep failing rather than exposing a partial record set. Removing or repairing the damaged app-private record allows a later initialization attempt; this does not replay interrupted work.
