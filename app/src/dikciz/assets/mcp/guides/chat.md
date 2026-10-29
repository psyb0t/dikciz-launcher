# On-device chat

Use MCP `dikciz_chat`, HTTP/WebSocket `chatControl`, or the matching native binding. Same validated procedure and permission handling. Tap the launcher cheese for the native panel; expand, hide, choose or create conversations there. Markdown streams live. Scroll up to pause following, Latest resumes.

Actions:

- `profiles`, `profileSave` with `profile`, `models` with `profileId`.
- `create` with `profileId` and optional `title` and `settings`; `list` with `limit`/`offset`.
- `read` with `conversationId`, message `limit`/`offset`.
- `rename` with conversationId/title; `settings` with conversationId; `configure` with conversationId and settings and/or profileId; `delete` idle conversation.
- `send` with conversationId/text/requestKey; `stop` with conversationId.
- `status`, `show`, `fullscreen`, `hide`; show/fullscreen accept conversationId.
- `subscribe` with conversationId/rules. Read guides/chat-events.

The native Add provider form offers OpenAI, Anthropic and Custom templates. Standard templates fill the official base URL and protocol. Custom accepts your own base URL and either compatible protocol. Templates are form shortcuts; profileSave still takes name/protocol/baseUrl/apiKey. Switching templates clears a key typed into the form.

Profile example, replace placeholders:

```json
{"name":"My provider","protocol":"openai","baseUrl":"https://PROVIDER_HOST/v1","apiKey":"REPLACE_ME"}
```
Protocol `openai` means Chat Completions; `anthropic` means Messages. Discover or enter models manually. Keys are write-only, Keystore-encrypted and omitted from reads/context. Explicit empty apiKey clears it. Host/protocol changes clear retained credentials unless a replacement is supplied. Public providers should use HTTPS. Redirects are disabled.

A send returns queued state, not completion. Poll read and inspect message/tool outcomes. Retry uncertain effects only after inspection. Reuse requestKey for idempotent submission; different text with the same key is a conflict. Changing provider or deleting requires an idle conversation. SQLite keeps full transcripts despite compaction; uninstall/data clear removes them, Home reset does not.

A provider connection can omit model. Discover its models once using models/profileId, then configure a conversation with settings:{model:"MODEL_ID",contextTokens:131072,maxOutputTokens:8192,reasoning:"medium"}. settings reads effective values and overrides. Selecting a known model without token fields fills its context size and clamps the current reply reserve. Explicit token fields win; unknown models retain their budgets. settings includes modelLimits with known context/output/input ceilings and limitsSource. Other partial patches retain settings; a provider change retains read-only access and resets other old overrides. Credentials and connection fields are forbidden inside settings. Model/token changes require an idle conversation. No selected model means send is rejected before queueing. The native chat has an API-backed model dropdown and a separate Model settings form.

The agent uses phone_tools for schemas, phone_call for execution and phone_resources/phone_resource for documentation. Calls use the shared MCP executor. Phone data and screenshots used in chat go to the selected provider. In writable mode it can hide chat, inspect another app and request chat again. Verify actual visibility and results.

Read guides/chat-context for budgets and compaction, guides/chat-events for autonomous reactions. Full documentation: docs/embedded-chat.md in the repository.

phone_tools is discovery only: pass query and optional offset. Execute through phone_call with name and arguments_json. List phone_resources before reading an exact returned URI with phone_resource. Invalid arguments, unknown wrapper tools and rejected resources return structured errors to the model so it can correct the request without losing the turn. Tool errors never imply permission to retry a side effect.

Set settings:{readOnly:true} on an idle conversation, or use Model settings. Default false. Discovery exposes read-only tools only. A trusted per-turn flag reaches the shared executor; direct writes and autoRequestPermissions:true return read_only_violation. Tool arguments cannot disable it. Shell/job execution, scripts, control actions, permission prompts and mixed read/write tools are blocked. Resources and permitted reads remain available. Grant missing access manually. Chat history and logs still save. Provider switches retain access. Separate owner clients keep their own access.
