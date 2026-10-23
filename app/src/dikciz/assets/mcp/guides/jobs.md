# Durable command jobs

Use `dikciz_job_start` with a unique `idempotencyKey` and `command`, a full ordinary command including `type`. It returns `jobId`. The same key and command deduplicate; changed content conflicts. Optional retentionHours is 1..720, default 168. Two workers and at most 256 retained records. Jobs cannot enqueue other jobs, mutate remote authentication or turn shellStream into a durable stream.

Read `dikciz_job_status` with jobId. The persisted response is the original command envelope, including Android permission recovery. completed means a result envelope exists; inspect its actual state. failed contains an error. queued/running are pending. cancelled means queued work never ran. interrupted means a previous process ended without a final outcome. Do not assume the side effect did or did not happen.

`dikciz_jobs_list` pages metadata newest first with offset, limit (default 50, max 200), total and hasMore. Fetch large results individually. Results survive disconnects and process restart until retention expiry. Expired terminal records are pruned on subsequent job operations. App data clearing removes jobs.

`dikciz_job_cancel` cancels queued work or sets cancellationRequested and interrupts a running worker. It does not roll back effects or guarantee Android stopped. Poll final state. No interrupted work is replayed automatically.

Use autoRequestPermissions on the nested command, or inherit it from jobStart, when owner review is intended. Granting access does not rerun a failed job. Explicitly inspect and retry only the authorized operation. Arguments/results stay in app-private storage and are excluded from ordinary operation logs.
