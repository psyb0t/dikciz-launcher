# On-device chat

Use MCP `dikciz_chat`, HTTP/WebSocket `chatControl`, or the matching native binding. Same validated procedure and permission handling. Tap the launcher cheese for the native panel; expand, hide, choose or create conversations there. Markdown streams live. Scroll up to pause following, Latest resumes.

Actions:
- `profiles`, `profileSave` with `profile`, `models` with `profileId`.
- `create` with `profileId` and optional `title`; `list` with `limit`/`offset`.
- `read` with `conversationId`, message `limit`/`offset`.
- `rename` with conversationId/title; `configure` with conversationId/profileId; `delete` idle conversation.
- `send` with conversationId/text/requestKey; `stop` with conversationId.
- `status`, `show`, `fullscreen`, `hide`; show/fullscreen accept conversationId.
- `subscribe` with conversationId/rules. Read guides/chat-events.

Profile example, replace placeholders:
```json
{"name":"My provider","protocol":"openai","baseUrl":"https://PROVIDER_HOST/v1","apiKey":"REPLACE_ME","model":"MODEL_ID","contextTokens":131072,"maxOutputTokens":4096,"compaction":"drop-oldest"}
```
Protocol `openai` means Chat Completions; `anthropic` means Messages. Discover or enter models manually. Keys are write-only, Keystore-encrypted and omitted from reads/context. Explicit empty apiKey clears it. Host/protocol changes clear retained credentials unless a replacement is supplied. Public providers should use HTTPS. Redirects are disabled.

A send returns queued state, not completion. Poll read and inspect message/tool outcomes. Retry uncertain effects only after inspection. Reuse requestKey for idempotent submission; different text with the same key is a conflict. Changing provider or deleting requires an idle conversation. SQLite keeps full transcripts despite compaction; uninstall/data clear removes them, Home reset does not.

The agent discovers existing tools using phone_tools, calls phone_call, and reads phone_resources/phone_resource. No parallel device API. It can hide chat, launch and inspect another app, then request chat again. Read status to verify visibility; Android background-launch rules and permissions still apply. Screenshots become provider image attachments. Phone data used in chat goes to its selected provider.

Read guides/chat-context for budgets and compaction, guides/chat-events for autonomous reactions. Full documentation: docs/embedded-chat.md in the repository.
