# On-device Dikciz assistant

Tap the cheese beside the launcher controls to open chat. It starts at half height; the resize button expands it to the available screen. Closing the panel leaves a running turn active. Hidden replies and subscribed events add an unread badge; reply notifications reopen the conversation. Android notification access controls whether those notifications appear.

The native panel renders streamed Markdown, including headings, lists, code fences, tables and strikethrough. Reasoning and tool records expand on a tap. Scrolling up stops automatic scrolling; **Latest** resumes it. The panel resizes above the keyboard. Quick-task buttons sit above a divider; the conversation scrolls within its own padded viewport, keeping message text clear of the controls. Conversation selection, creation, renaming, provider changes and deletion are available from its header. Stop a busy conversation before changing its provider or deleting it.

## Background activity and Stop

Open the notification shade in any app and tap **Open chat** on **Dikciz chat**. This quiet ongoing shortcut opens the normal half-screen conversation over that app, including while the assistant is idle. It does not keep an extra service running and does not require overlay access for a user-initiated tap. Opening it does not send a message. With no configured conversation, it opens the first-run chat instead.

Tap **Explain this screen** from that panel to dismiss chat and capture the app underneath. Notification permission and the **Chat shortcut** channel must be enabled. Android channel settings can hide the shortcut; reopening Home refreshes it after permission changes or dismissal. Android force-stop and device restart still follow normal app/notification lifecycle rules.

While a turn runs, the **Dikciz is working** notification shows its current phase: thinking, writing a reply, finding tools, reading the screen or running a phone tool. It updates quietly in place and contains no prompt, tool arguments, screen contents or reply text. Tap it to reopen that exact conversation, including when another conversation was previously selected.

The notification's **Stop** action uses the same cancellation path as the chat panel and shared command API. It shows **Stopping** and cancels active and queued work in that conversation. Already completed phone actions remain completed, and a synchronous Android operation may finish before cancellation takes effect. A notification from an earlier turn cannot cancel a newer turn. Stop does not delete history, disable event subscriptions or undo phone changes.

After work ends, the active notification disappears when no event rules remain, or returns to waiting for configured events. Hidden replies still have their separate notification. Android's notification permission and channel settings must allow notifications to see these controls; denying them does not prevent opening chat and stopping work there.

## Quick tasks

**Make me a widget** opens a description field. Describe the result, then tap **Create**. Dikciz saves the current page identity with the queued request and asks the selected model to build a complete HTML/CSS/JavaScript document with independent widget state through the existing phone tools. It preserves existing content and reports missing, locked or full pages. Widget creation requires writable conversation access. Inspect the generated widget and tool results; the model can fail or leave a partial result.

**Explain this screen** uses an optional question from the composer. It hides chat, waits for dismissal, captures the visible Android display and reads its accessibility tree before contacting the provider. The capture becomes an image attachment in the private chat transcript. This turn exposes and executes only read-only tools even if the conversation normally permits writes. It does not change that conversation's saved access setting. Chat requests reopening when the turn finishes; Android background-activity restrictions still apply. A new screen task is rejected while other assistant work is active, so it cannot silently capture a much later screen. Missing screenshot access fails visibly without a provider request. Secure windows, lock screens and Android capture limits still apply.

These shortcuts use the configured model and can incur API usage. Screen explanation requires a model that accepts image input. Captured screen data goes to that provider and remains in the private conversation history. Opening chat from Home explains Home; to inspect another app, open that app and request the task through the remote API or show chat over it through the shared visibility command.

All ordinary entrypoints accept the same task request:

```json
{"action":"task","task":"widget","conversationId":"CONVERSATION_ID","requestKey":"UNIQUE_REQUEST_KEY","text":"Create a small editable note widget."}
```

Use `task: "explainScreen"` with an optional `text` question for screen explanation. HTTP adds `type: "chatControl"`; MCP uses `dikciz_chat`. The response reports queue identity, not completion. Poll `read`. Reusing a key with the same task and text returns the existing turn without recapture or generation; changed content returns `chat_request_conflict`. Capture and tool failures remain in the transcript. Cancelling a screen task does not request reopening.

## Connect a provider

Tap the header gear, then **Add provider**. Choose **OpenAI**, **Anthropic** or **Custom** from **Provider template**. OpenAI and Anthropic fill their standard API base URL and protocol. Custom lets you enter your own HTTP or HTTPS base URL and choose the compatible protocol. Enter the API key and save the connection. Switching templates clears any key typed into the form; existing saved keys still follow the host/protocol retention rules below. Create or select a conversation using that connection, then use the **Chat model** dropdown to choose from its API catalogue. **Refresh chat models** fetches the current list again. Each conversation can choose a different model from the same provider without copying its connection or key. If discovery is unavailable or a private model is not listed, enter its exact ID under **Tune** (model settings). A conversation without a model cannot send messages or enable event rules.

Model limits come first from provider discovery, then from the bundled exact-ID catalog. OpenAI's Models API does not report token limits, so its documented models use that catalog. Anthropic and compatible hosts can supply context and output metadata directly. Unknown IDs show unavailable limits and keep manual budgets. Discovery follows all bounded model pages; refreshing discovery or opening a conversation never rewrites its saved budgets. Cached provider limits survive launcher restarts and are invalidated when the connection is edited.

The native model settings show the known context/output ceilings. Selecting a model or typing a known ID fills its context size. The editable reply reserve stays at the current value when it fits; it is reduced when needed to leave room for the safety margin and summary. An inherited thinking budget that no longer fits is disabled. Providing explicit context/output values in `create` or `configure` keeps those values. Re-selecting a known model without token fields restores its context default. A separate documented input ceiling also bounds outbound chat and summary requests.

| Setting | Behavior |
| --- | --- |
| Protocol | `openai` uses streaming Chat Completions; `anthropic` uses streaming Messages. These are wire protocols, including compatible hosts. |
| API base URL | Owner-selected HTTP or HTTPS host, optionally ending in `/v1`. Use HTTPS for public providers. HTTP supports private local/VPN endpoints. Credentials in the URL, query strings and fragments are rejected; redirects are disabled. |
| API key | Stored separately from public profile data, encrypted with Android Keystore AES-GCM. Reads omit it. Blank in an existing profile's UI retains the key. An explicit empty `apiKey` in the API clears it. Changing host or protocol without explicitly supplying a replacement clears the old key. |
| Read-only phone access | Boolean `readOnly`, default `false`. Filters tool discovery and enforces access in the executor for this conversation. |
| Model ID | Per-conversation model selected in the API-backed dropdown or entered manually. Selecting a known model fills its context window and clamps the current reply reserve to its output ceiling and available input space. Explicit context/output values in the same request win. Unknown models retain the current budgets. |
| Total context tokens | Combined input and output allowance, filled from known model limits when selecting a model. Unknown models start at `131072`; manual values remain editable. |
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

**Tune** opens model settings for the current conversation and edits its context size, maximum output/reply reserve, reasoning, thinking budget, compaction, summary model/output, timeout, tool-round limit, safety margin and additional instructions. These values do not change another conversation or create another provider. Changes require an idle conversation; stop active work first. Switching to a different provider retains read-only access and resets the other conversation overrides to that provider's defaults so incompatible model/thinking settings do not carry over.

No provider is contacted until a model-discovery request, owner message or configured event requires it. A model with different reasoning or token constraints can reject a profile; inspect the visible failed turn and correct its settings.

## Conversations and context

Android SQLite stores shared provider connections, independent conversation settings, full messages, reasoning, tool calls/results, queue state and summaries in private app storage. Outbound context selection never deletes the transcript. Uninstalling or clearing app data removes this database and its keys. Home-page resets do not reset chat history.

Existing profiles keep their model and budget defaults. The database upgrade adds conversation overrides without deleting messages, subscriptions, provider IDs or encrypted credentials. Provider edits retain existing defaults; models and tokens are edited per conversation in the native UI. A running turn uses one connection/settings snapshot.

Before every provider request, Dikciz subtracts maximum output and the safety margin from total context. It estimates input conservatively using UTF-8 bytes, framing and tool-schema allowances, plus an image allowance. This is not the provider's exact tokenizer or billing count. Set a larger margin when a host has extra framing or image costs.

`drop-oldest` removes complete old exchanges from the outgoing request until it fits. `summarize` sends a completed old prefix to the summary model, saves its summary and checkpoint, then continues with that summary and recent complete exchanges. The summary request reserves its own output allowance. Current user input and current tool results stay together. If they cannot fit, the turn fails explicitly. Failed summaries leave original history available. Switching provider/model omits incompatible saved reasoning on replay. Anthropic thinking signatures, omitted-text blocks and redacted blocks are retained for tool continuations and later turns; opaque thinking is never decrypted. See the provider's [thinking contract](https://platform.claude.com/docs/en/build-with-claude/thinking).

Failed, cancelled and interrupted requests remain available to later turns under both context policies, together with completed tool results. Replayed tool calls always have a matching result. A call interrupted before its result was saved receives an explicit unknown-outcome result, which is context only and never re-executes the action. Queued future requests stay out of the current turn.

Queue records survive process death. Active generation or tool execution becomes **interrupted**, with no automatic replay of uncertain side effects. Queued work resumes when the launcher/service reconnects. A foreground service keeps active work and configured event listeners visible to Android; force-stop, system restrictions, revoked access or unavailable networking can still interrupt them. Read actual tool results before retrying.

## Phone tools and visibility

Enable **Read-only phone access** in **Model settings**, or configure `settings:{"readOnly":true}` through any entrypoint. It defaults to `false`. Only tools explicitly marked read-only are exposed to that conversation. The turn's trusted access flag also travels through MCP to the shared command executor, which rejects write/control calls with `read_only_violation` before execution. Tool arguments cannot override this flag. Shell execution, jobs that start commands, scripts, settings changes, app launches, messaging, permission prompts and mixed read/write tools are blocked, even when called directly without discovery. Documentation and permitted phone reads remain available. Read-only reads cannot set `autoRequestPermissions:true`; grant missing access manually. Chat history, summaries and logs still persist. This setting restricts the embedded agent, not separately authenticated owner clients. An API settings patch containing only `readOnly:true` can restrict an active turn immediately. Other access changes require stopping active work first.

Each provider request includes a toolCatalogueRevision fingerprint of the installed contracts and trusted launcher access context: `conversationReadOnly` is the saved setting, `turnReadOnly` is the effective restriction, `reason` distinguishes a saved restriction from a screen explanation, and `writeAccessResumesNextTurn` identifies a temporary restriction on an otherwise writable conversation. The executor applies that turn's restriction independently. Screen explanation never rewrites saved access. This current context takes precedence over access claims in older conversation messages.

The model gets five compact tools: search documentation and collections, discover phone tool schemas, call a discovered tool, list documentation resources and read one resource. Calls run through the same local MCP handlers used by external clients, including input checks, Android permission handling and outcome reporting. API keys are never added to model context. Requested phone data, screen captures and tool results are sent to the selected provider as conversation context.

The assistant is instructed to rediscover relevant installed contracts for each new owner request and follow every `inputSchema` requirement, type, enum and nested field. On a rejected `phone_call`, its result includes the current `toolContract` when that native tool is exposed for the current turn. The original error and permission recovery remain intact. Unexposed tools receive discovery guidance instead of an invented schema. This supplies correction data; it never retries a phone action automatically or guarantees that a model will follow the contract.

`phone_tools` only discovers schemas; `phone_call` executes a discovered tool using its exact name and JSON arguments. Use `phone_search` for ranked offline guides and recipes, or collection `tools` for installed schemas. Its optional collection and pagination parameters are strings. `phone_tools` shares the ranked search engine. Read a returned URI directly with `phone_resource`; listing `phone_resources` first is unnecessary. Reuse discovered contracts while the installed build and current access context are unchanged. See [offline search](search.md) for owner collections, aliases, spelling correction and limits. Rejected JSON arguments, unknown tools and invalid or missing resources return structured tool errors, allowing the model to correct its request within the same turn. They do not authorize automatic execution or retries. Provider failures, cancellation, timeouts and round exhaustion still end the turn visibly.

Empty streamed tool arguments become `{}` before they are saved. Nonempty arguments must be strict JSON objects; malformed input is rejected without executing an action. Provider replay retains rejected text in a valid JSON object together with the recorded tool outcome. Existing conversations with empty or malformed saved arguments can continue after an update or restart without deleting history or repeating completed operations.

In writable mode, the agent can call `dikciz_chat` to hide its panel, inspect another app through existing launch/UI/screenshot tools, and request chat again. Screenshot PNG results become image attachments for the provider. Visibility requests report `show_requested` or `hide_requested`; poll status or inspect the UI to confirm the result. Android can block background activity launches without the required access. These requests do not unlock the phone or bypass Android permissions.

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

Native and custom events use the existing event names and filters described in [automation control](automation-control.md). Rules persist across panel closure and launcher restart. They can cause paid provider requests while the panel is hidden. The default interval is 60 seconds, minimum one second; each conversation admits at most one queued event turn while work is active. Dikciz excludes its own notification events to avoid reply loops. Clear rules with `rules: []` to disable automation. In writable mode, the model can configure rules through `dikciz_chat` when the owner's task calls for it.

## Shared command API

Use WebSocket `chatControl`, HTTP `/v1/automation` with `type: "chatControl"`, MCP `dikciz_chat`, or the existing Lua/HTML native-command bindings. They share the command implementation and response/error contract. Discover schemas from the installed build before use.

| Action | Fields and result |
| --- | --- |
| `status` | Busy/visible/full-screen state, active conversation and total unread count. |
| `profiles` / `profileSave` | List public profiles, or save validated `profile`. Credentials are write-only. |
| `models` | Discover models for `profileId`. |
| `create` | Create with `profileId`, optional `title` and `settings` overrides. |
| `list` | Conversation pages with `limit` and `offset`. |
| `read` | `conversationId`, optional message `limit`/`offset`; returns conversation metadata, display messages and effective `settings`. |
| `rename` | Set `title` for `conversationId`. |
| `settings` | Read effective `settings`, saved `overrides`, `profileId` and protocol for `conversationId`; no credentials. |
| `configure` | Patch `settings` and/or change `profileId` for an idle `conversationId`. A patch containing only `readOnly:true` may restrict an active conversation. Settings accept model and execution fields only. A provider change retains read-only access and clears other prior overrides before applying the patch. |
| `delete` | Delete an idle conversation and its messages/rules. |
| `send` | `conversationId`, `text`, unique `requestKey`. Returns queue identity and state. Reusing the key with the same text returns the existing turn; different text fails with `chat_request_conflict`. |
| `task` | `conversationId`, `requestKey`, and `task` (`widget` or `explainScreen`). Widget creation requires `text`; screen explanation accepts an optional question. See Quick tasks. |
| `stop` | Cancel active and queued work for `conversationId`. Completed effects remain completed. |
| `subscribe` | Replace `rules` for `conversationId`. |
| `show` / `fullscreen` / `hide` | Control the panel independently of running work. `show` accepts optional `conversationId` and `fullScreen`. |

A connection-only profile needs `name`, `protocol`, `baseUrl` and optional `apiKey`. Legacy model/budget defaults remain accepted for existing clients. After `models` discovery, configure the selected conversation through any binding:

```json
{"action":"configure","conversationId":"CONVERSATION_ID","settings":{"model":"MODEL_ID","contextTokens":131072,"maxOutputTokens":8192,"reasoning":"medium"}}
```

Partial settings patches preserve the other overrides. Credentials, host and protocol are rejected inside `settings`; use `profileSave` to edit a connection. Invalid budgets leave persisted settings unchanged.

Queue acceptance is not completion. Poll `read` for complete, failed, cancelled or interrupted messages and inspect tool outcomes. Common errors include `validation_failed`, `chat_profile_missing`, `chat_conversation_missing`, `chat_provider_error`, `chat_busy` and `chat_request_conflict`.

## Development and tests

[Koog 1.3.0](https://github.com/JetBrains/koog) supplies provider protocol clients and streaming frames; [Markwon 4.6.2](https://github.com/noties/Markwon) renders native Markdown. Both use Apache-2.0. The Ktor adapter uses its OkHttp engine on Android. A small Dikciz adapter preserves Anthropic signature/redacted blocks and places OpenAI screenshot images in user content after the tool results. Dikciz owns SQLite persistence, context policies, the durable queue, event routing and its MCP adapter. The native UI follows [Chatz](https://github.com/psyb0t/chatz)'s chat behavior.

The private Android Lab suite serves fake OpenAI/Anthropic model discovery and SSE responses through a lab-only reverse mapping to Android loopback, so tests work without emulator Wi-Fi. It exercises the real Android database, shared commands, native panel and MCP tools without a paid API key:

```bash
make dikciz-test DIKCIZ_TEST_SELECTOR=test_chat_agent
```

See the [source architecture](../app/src/dikciz/README.md) and [test guide](../tests/README.md) for the pinned build and lab workflow.

## Companion workflows

See [the focused companion guide](assistant-routines.md) for the new shared commands, native screens, required access and actual outcome states.
