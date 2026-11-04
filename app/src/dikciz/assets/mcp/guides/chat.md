# On-device chat

MCP dikciz_chat, HTTP/WebSocket chatControl and native bindings share validation, permissions and results. The launcher cheese opens half-screen chat with streaming Markdown, model selection, settings and conversation management. Expand or hide it; scroll up to pause following and Latest to resume.

Actions:

- profiles; profileSave with profile; models with profileId.
- create with profileId and optional title/settings; list with limit/offset.
- read with conversationId and message limit/offset; rename with conversationId/title.
- settings with conversationId; configure with conversationId and settings and/or profileId; delete an idle conversation.
- send with conversationId/text/requestKey; stop with conversationId.
- task with conversationId/requestKey/task and optional text. widget requires a description and writable access; it preserves the selected page target. explainScreen uses an optional question, hides chat, captures the display before contacting the provider and forces read-only tools for that turn. Chat requests reopening afterward. Missing capture access fails without a provider request; busy assistants reject a new capture. Native buttons use these same actions.
- status; show/fullscreen with optional conversationId; hide.
- subscribe with conversationId/rules. Read guides/chat-events.

Add provider offers OpenAI, Anthropic and Custom templates. Standard templates fill their API URL/protocol; Custom accepts your compatible host. Changing templates clears the typed key. profileSave takes name, protocol, baseUrl and optional apiKey; model may be omitted. Protocol openai uses Chat Completions, anthropic uses Messages. Keys are write-only and Keystore-encrypted. Explicit empty apiKey clears it; a changed host/protocol clears a retained key unless replaced. Use HTTPS for public providers. Redirects are disabled.

Discover models once, then configure each conversation independently, for example settings:{model:"MODEL_ID",contextTokens:131072,maxOutputTokens:8192,reasoning:"medium"}. Known model selection fills context and clamps the reply reserve; explicit token fields win. Unknown models retain manual budgets. settings returns effective values, overrides and modelLimits. Discovery never rewrites saved budgets. Changing provider retains readOnly and resets other overrides. Credentials and connection fields are forbidden in settings. Model/settings changes require an idle conversation; a missing model blocks generation. Read guides/chat-context for token fields and compaction.

Queue acceptance is not completion. Poll read and inspect actual tool outcomes. Reuse requestKey for the same send/task without repeating it; changed text or task conflicts. SQLite retains full transcripts despite compaction. Uninstall/data clear removes them; Home reset does not. Screenshots become provider image inputs and persist privately. Screen explanation requires an image-capable model. Cancellation does not request chat reopening.

[Tool contracts and access](dikciz://docs/guides/chat-tools). phone_tools discovers schemas using query and optional offset. Execute with phone_call using name and arguments_json. Use phone_search for relevant guides and recipes, or collection tools for ranked schemas. Read an exact returned URI with phone_resource. Catalog listing is optional. Reuse contracts while the installed build and current access context are unchanged. Tool names are not resource URIs. Rejected arguments/resources return recoverable errors; uncertain effects require inspection before retrying.

settings:{readOnly:true} hides write tools and passes a trusted access flag into the executor. Direct writes and autoRequestPermissions:true fail with read_only_violation. Tool arguments cannot disable it. Grant missing access manually. History/logs still persist; other owner clients retain their access. Writable agents can hide chat and inspect other apps through existing tools. Phone data goes to the selected provider. See docs/embedded-chat.md for the full contract.
