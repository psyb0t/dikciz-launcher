# Chat budgets and compaction

Provider profiles configure contextTokens (default131072), maxOutputTokens (4096), safetyMarginTokens (1024), compaction (drop-oldest or summarize), summaryModel (optional same-provider ID), summaryOutputTokens (2048), reasoning, thinkingTokens, outputTokenField, maximumRounds (32), timeoutMilliseconds (300000) and optional systemPrompt.

Available input = contextTokens - maxOutputTokens - safetyMarginTokens. The actual output cap is sent to the provider as well as reserved. OpenAI defaults to max_completion_tokens; outputTokenField:max_tokens selects that field exclusively for compatible hosts. Anthropic uses max_tokens.

The input estimator conservatively counts UTF-8 bytes, framing, tool schemas and image allowances. It is not an exact provider tokenizer or billing counter. Configure the real model context/output limits and enough safety margin for that host.

drop-oldest omits complete old exchanges from outbound context. summarize replaces a completed old prefix with a stored summary/checkpoint, preserving full original transcripts in SQLite. Its request reserves summaryOutputTokens separately. Current user input and its live tool results remain together. An input that cannot fit fails explicitly; it is never silently chopped. Failed summaries preserve original history. A summaryModel must support the configured context limit. Switching provider/model drops incompatible saved reasoning on replay.

reasoning:auto leaves provider defaults. OpenAI explicit efforts are sent as reasoning_effort; actual model support varies. Anthropic supports none (disabled), or low/medium/high/xhigh/max adaptive effort where supported. Explicit Anthropic thinkingTokens requires reasoning:auto, at least1024 and less than maxOutputTokens. Reasoning counts toward provider output budgets. summary requests use default reasoning and no explicit thinking budget.

Stop cancels generation. Timeout/max-round exhaustion is visible in failed messages. Completed tool effects are not rolled back. After process death active/executing messages become interrupted; uncertain tool effects are never replayed automatically. Poll read/status and inspect actual results before continuing.

Anthropic thinking signatures, omitted-text blocks and redacted blocks are preserved for tool continuations and later turns. Opaque thinking stays opaque. Screenshot images use each provider's supported content format.
