# Lua automation

Scripts use `function on_event(event)`. The event contains `type`, `source`, `timestampMilliseconds`, `coalescingKey` and `payload`. Return a short `status` and typed `actions`, or one direct action. `patchState` saves the current script's state. Device and widget actions are validated before execution.

A script's public directory is `/sdcard/Dikciz/scripts/<id>/`: `script.json`, `main.lua`, `state.json` and generated `run-status.json`. Automation policy and subscription records live under `automation/policies/` and `automation/scripts/`. An enabled script alone does not subscribe it to events. Custom subscriptions require the policy capability `manualTrigger` and each returned action must be allowed by policy.

Use `automationStatus` for configured scripts and Android access, `automationTrigger` for a manual run and `scriptLogs` for outcomes. A trigger acknowledgement is not proof that the handler and every action succeeded. Inspect the final run status and persisted state. Keep returned status text short and avoid credentials or private data in logs.

`context.requestJson` lets an enabled Lua script call the shared owner command API. Its requests still undergo command validation and Android permission checks. Declared policy rules govern returned automation actions; do not assume they isolate arbitrary owner commands or unrestricted shell access.

File observers reload valid source, metadata and automation changes. Preserve the existing manifest and other scripts when installing. The native source editor does not invent an automation policy for a new script.

[Install a script](dikciz://docs/recipes/install-script). [Two-event Lua source](dikciz://docs/recipes/event-lua). [Logs](dikciz://docs/guides/logging).
