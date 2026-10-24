# On-device Dikciz assistant

Tap the cheese beside the launcher controls to open chat. It starts at half height; the resize button expands it to the available screen. Closing the panel leaves a running turn active. Hidden replies and subscribed events add an unread badge; reply notifications reopen the conversation. Android notification access controls whether those notifications appear.

The native panel renders streamed Markdown, including headings, lists, code fences, tables and strikethrough. Reasoning and tool records expand on a tap. Scrolling up stops automatic scrolling; **Latest** resumes it. The panel resizes above the keyboard. Conversation selection, creation, renaming, provider changes and deletion are available from its header. Stop a busy conversation before changing its provider or deleting it.

## Connect a provider

Open **Settings**, then **Add provider**. Enter a profile name, protocol, API base URL, API key and model ID. **Load models** queries the endpoint; manual model IDs also work when discovery is unavailable. Profiles can be shared by multiple conversations. Saving a profile changes subsequent requests in all conversations using it.

| Setting | Behavior |
| --- | --- |
| Protocol | `openai` uses streaming Chat Completions; `anthropic` uses streaming Messages. These are wire protocols, including compatible hosts. |
| API base URL | Owner-selected HTTP or HTTPS host, optionally ending in `/v1`. Use HTTPS for public providers. HTTP supports private local/VPN endpoints. Credentials in the URL, query strings and fragments are rejected; redirects are disabled. |
| API key | Stored separately from public profile data, encrypted with Android Keystore AES-GCM. Reads omit it. Blank in an existing profile's UI retains the key. An explicit empty `apiKey` in the API clears it. Changing host or protocol without explicitly supplying a replacement clears the old key. |
| Model ID | Exact provider model ID. Discovery metadata does not replace the owner's context/output settings. |
| Total context tokens | Combined input and output allowance, default `131072`. Set it to the actual model limit. |
| Maximum output | Actual provider output cap and reserved reply allowance, default `4096`. |
| Context policy | `drop-oldest` or `summarize`. See below. |
| Summary model | Optional model ID at the same provider. Blank uses the chat model. It must support the configured context size. |
| Summary output | Summary response cap, default `2048`. |
| Reasoning | `auto` leaves provider defaults. OpenAI-compatible hosts receive an explicit `reasoning_effort` for other values; supported values depend on the model. Anthropic accepts `none`, or adaptive thinking with `low`, `medium`, `high`, `xhigh` or `max`, where the model supports that effort. |
| Thinking tokens | Anthropic explicit thinking budget. Use at least `1024`, less than maximum output, with reasoning `auto`. `0` leaves the provider default. |
| OpenAI output field | [`max_completion_tokens`](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create) by default. Choose `max_tokens` for compatible hosts requiring it. Only the selected field is sent. |
| Maximum rounds | Default `32` provider/tool rounds per turn. Exhaustion is a visible failure. |
| Turn timeout | Default `300000` milliseconds for the complete turn, including tool work and compaction. |
| Context safety margin | Default `1024` reserved tokens beyond the output allowance. |
| Additional system instructions | Owner instructions appended to the built-in Dikciz identity and phone-operation rules. |

No provider is contacted until a model-discovery request, owner message or configured event requires it. A model with different reasoning or token constraints can reject a profile; inspect the visible failed turn and correct its settings.

## Conversations and context

Android SQLite stores profiles, conversations, full messages, reasoning, tool calls/results, queue state and summaries in private app storage. Outbound context selection never deletes the transcript. Uninstalling or clearing app data removes this database and its keys. Home-page resets do not reset chat history.

Before every provider request, Dikciz subtracts maximum output and the safety margin from total context. It estimates input conservatively using UTF-8 bytes, framing and tool-schema allowances, plus an image allowance. This is not the provider's exact tokenizer or billing count. Set a larger margin when a host has extra framing or image costs.

`drop-oldest` removes complete old exchanges from the outgoing request until it fits. `summarize` sends a completed old prefix to the summary model, saves its summary and checkpoint, then continues with that summary and recent complete exchanges. The summary request reserves its own output allowance. Current user input and current tool results stay together. If they cannot fit, the turn fails explicitly. Failed summaries leave original history available. Switching provider/model omits incompatible saved reasoning on replay. Anthropic thinking signatures, omitted-text blocks and redacted blocks are retained for tool continuations and later turns; opaque thinking is never decrypted. See the provider's [thinking contract](https://platform.claude.com/docs/en/build-with-claude/thinking).

Queue records survive process death. Active generation or tool execution becomes **interrupted**, with no automatic replay of uncertain side effects. Queued work resumes when the launcher/service reconnects. A foreground service keeps active work and configured event listeners visible to Android; force-stop, system restrictions, revoked access or unavailable networking can still interrupt them. Read actual tool results before retrying.

## Phone tools and visibility

The model gets four compact tools: discover phone tool schemas, call a discovered tool, list documentation resources and read one resource. Calls run through the same local MCP handlers used by external clients, including input checks, Android permission handling and outcome reporting. API keys are never added to model context. Requested phone data, screen captures and tool results are sent to the selected provider as conversation context.

The agent can call `dikciz_chat` to hide its panel, inspect another app through existing launch/UI/screenshot tools, and request chat again. Screenshot PNG results become image attachments for the provider. Visibility requests report `show_requested` or `hide_requested`; poll status or inspect the UI to confirm the result. Android can block background activity launches without the required access. These requests do not unlock the phone or bypass Android permissions.

## Event-driven conversations

Open a conversation's **Events** editor or use `subscribe`. Rules replace that conversation's complete rule list. Each has an optional unique ID, a canonical automation `subscription`, and task `instructions`. For example:

```json
{
  "action": "subscribe",
  "conversationId": "CONVERSATION_ID",
  "rules": [{
    "id": "owner-check",
    "subscription": {"event": "example.check", "minimumIntervalMilliseconds": 60000},
    "instructions": "Read this event and report the relevant phone state."
  }]
}
```

Native and custom events use the existing event names and filters described in [automation control](automation-control.md). Rules persist across panel closure and launcher restart. They can cause paid provider requests while the panel is hidden. The default interval is 60 seconds, minimum one second; each conversation admits at most one queued event turn while work is active. Dikciz excludes its own notification events to avoid reply loops. Clear rules with `rules: []` to disable automation. The model can configure rules through `dikciz_chat` when the owner's task calls for it.

## Shared command API

Use WebSocket `chatControl`, HTTP `/v1/automation` with `type: "chatControl"`, MCP `dikciz_chat`, or the existing Lua/HTML native-command bindings. They share the command implementation and response/error contract. Discover schemas from the installed build before use.

| Action | Fields and result |
| --- | --- |
| `status` | Busy/visible/full-screen state, active conversation and total unread count. |
| `profiles` / `profileSave` | List public profiles, or save validated `profile`. Credentials are write-only. |
| `models` | Discover models for `profileId`. |
| `create` | Create with `profileId`, optional `title`. |
| `list` | Conversation pages with `limit` and `offset`. |
| `read` | `conversationId`, optional message `limit`/`offset`; returns conversation metadata and display messages. |
| `rename` / `configure` | Set `title` or `profileId` for `conversationId`. |
| `delete` | Delete an idle conversation and its messages/rules. |
| `send` | `conversationId`, `text`, unique `requestKey`. Returns queue identity and state. Reusing the key with the same text returns the existing turn; different text fails with `chat_request_conflict`. |
| `stop` | Cancel active and queued work for `conversationId`. Completed effects remain completed. |
| `subscribe` | Replace `rules` for `conversationId`. |
| `show` / `fullscreen` / `hide` | Control the panel independently of running work. `show` accepts optional `conversationId` and `fullScreen`. |

Queue acceptance is not completion. Poll `read` for complete, failed, cancelled or interrupted messages and inspect tool outcomes. Common errors include `validation_failed`, `chat_profile_missing`, `chat_conversation_missing`, `chat_provider_error`, `chat_busy` and `chat_request_conflict`.

## Development and tests

[Koog 1.3.0](https://github.com/JetBrains/koog) supplies provider protocol clients and streaming frames; [Markwon 4.6.2](https://github.com/noties/Markwon) renders native Markdown. Both use Apache-2.0. The Ktor adapter uses its OkHttp engine on Android. A small Dikciz adapter preserves Anthropic signature/redacted blocks and places OpenAI screenshot images in user content after the tool results. Dikciz owns SQLite persistence, context policies, the durable queue, event routing and its MCP adapter. The native UI follows [Chatz](https://github.com/psyb0t/chatz)'s chat behavior.

The private Android Lab suite serves fake OpenAI/Anthropic model discovery and SSE responses through a lab-only reverse mapping to Android loopback, so tests work without emulator Wi-Fi. It exercises the real Android database, shared commands, native panel and MCP tools without a paid API key:

```bash
make dikciz-test DIKCIZ_TEST_SELECTOR=test_chat_agent
```

See the [source architecture](../app/src/dikciz/README.md) and [test guide](../tests/README.md) for the pinned build and lab workflow.
