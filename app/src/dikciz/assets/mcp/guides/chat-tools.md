# Assistant tool contracts and access

The embedded assistant has five compact wrappers. Their provider schemas define parameter types:

- `phone_tools`: required string `query`, optional decimal string `offset`. Empty query lists tools; nonempty queries rank tasks and identifiers with aliases and typo correction. Follow `hasMore`/`nextOffset` only when needed.
- `phone_search`: required string `query`, optional string `collection` (default `docs`) and decimal strings `limit`/`offset`. Returns ranked excerpts, exact IDs and URIs. Collection `tools` returns current permitted schemas.
- `phone_call`: required string `name` and string `arguments_json`. Discover the exact native name first. The string contains a strict JSON object matching the native `inputSchema`, including all required fields, types, enums and nested objects. Send `"{}"` for a no-argument tool.
- `phone_resources`: optional string `cursor` from the preceding resource page. No arguments means `{}`.
- `phone_resource`: required string `uri`, copied from search or resource discovery.

Discovery does not execute an operation. Reuse installed contracts while the build and access context remain unchanged. Rediscover after an APK or access change, or a contract rejection. Search for the relevant procedure instead of walking the entire resource catalog. Rejected native calls include `toolContract` when that tool is currently exposed. Follow its actual `inputSchema`; the original error and permission recovery remain available. Unexposed or unknown tools return discovery guidance. Correction does not automatically execute or retry anything. Check uncertain side effects before retrying.

Each model request has current launcher access context. `conversationReadOnly` is the saved setting; `turnReadOnly` is the executor restriction for this turn. `reason` distinguishes `conversation_setting`, `screen_explanation`, `turn_started_read_only` and `writable_conversation`. `writeAccessResumesNextTurn` means the saved conversation permits writes but this turn retains a temporary restriction. A screen task does not change saved settings. Treat current context as authoritative over old assistant claims; do not tell an owner to enable writes when their conversation already allows them. Android grants are separate and must be checked through actual tool results.

Each call rechecks trusted saved access. A configure patch containing only settings:{"readOnly":true} can restrict an active turn immediately; other configuration changes require stopping it first. Model arguments cannot relax access.
