---
name: dikciz-write-script
description: Author and verify Lua or background JavaScript with explicit events, state and intercepted simulation.
---

# Author an automation script

1. Read the selected script, policy and subscription, or choose a new unique ID. A Lua source file alone does not enable an event. Background JavaScript uses a custom app manifest and the custom app runtime.
2. Call dikciz_automation_simulate with source, language and a representative event before enabling. Check its outcome and captured commands. Look up the specific bridge method when needed. For an edit, preserve existing state and the previous valid source when a draft fails.
3. Save source and matching subscription and policy together. Set only the event and capabilities the requested behavior needs. When editing via configuration replacement, start from a fresh full configuration and preserve every unrelated record.
4. Trigger a safe fixture event if supported. Read structured run results, script logs and persisted state. Treat queue acceptance and skipped or intercepted actions as distinct outcomes. On failure, inspect the actual script ID, event and error before retrying.
