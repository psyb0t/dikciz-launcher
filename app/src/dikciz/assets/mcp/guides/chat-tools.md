# Assistant tool contracts and access

The embedded assistant has four compact wrappers. Their provider schemas define parameter types:

- `phone_tools`: required string `query`, optional decimal string `offset`. Empty query lists tools; search one keyword and follow `hasMore`/`nextOffset`.
- `phone_call`: required string `name` and string `arguments_json`. Discover the exact native name first. The string contains a strict JSON object matching the native `inputSchema`, including all required fields, types, enums and nested objects. Send `"{}"` for a no-argument tool.
- `phone_resources`: optional string `cursor` from the preceding resource page. No arguments means `{}`.
- `phone_resource`: required string `uri`, copied from resource discovery.

Discovery does not execute an operation. Refresh relevant installed schemas for each new owner request; an older transcript may describe a different APK or access mode. Rejected native calls include `toolContract` when that tool is currently exposed. Follow its actual `inputSchema`; the original error and permission recovery remain available. Unexposed or unknown tools return discovery guidance. Correction does not automatically execute or retry anything. Check uncertain side effects before retrying.

Each model request has current launcher access context. `conversationReadOnly` is the saved setting; `turnReadOnly` is the executor restriction for this turn. `reason` distinguishes `conversation_setting`, `screen_explanation`, `turn_started_read_only` and `writable_conversation`. `writeAccessResumesNextTurn` means the saved conversation permits writes but this turn retains a temporary restriction. A screen task does not change saved settings. Treat current context as authoritative over old assistant claims; do not tell an owner to enable writes when their conversation already allows them. Android grants are separate and must be checked through actual tool results.

Each call rechecks trusted saved access. A configure patch containing only settings:{"readOnly":true} can restrict an active turn immediately; other configuration changes require stopping it first. Model arguments cannot relax access.
