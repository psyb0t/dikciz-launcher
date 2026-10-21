# Custom events

Event names start with a letter, contain 1 through 128 characters, and use letters, digits, `.`, `_`, `:` or `-`. Use a namespace such as `example.start`. Payloads are JSON values accepted by the current action schema; use small objects with stable keys.

Publish through MCP `dikciz_automation_dispatch`, HTTP or WebSocket `automationDispatch`, or HTML `dikciz.emitEvent`. A Lua handler can return an `emitEvent` action. A network dispatch needs a valid HTML `sourceWidgetAddress` and an action such as:

```json
{"type":"emitEvent","event":"example.start","payload":{}}
```

Lua receives custom events only through enabled subscription records backed by an enabled policy with `manualTrigger` and the required action allowlist. An HTML widget receives matching `eventSubscriptions` through `dikciz.onEvent` only while rendered. Events are not a replayable message queue.

Subscription records specify `event` and `minimumIntervalMilliseconds`. Use the current schema for filters and coalescing fields. Rate limits and coalescing can intentionally skip repeated work. Avoid emitting the same input event from its own handler.

WebSocket subscribers can observe `automation_event` records. MCP's resources are static documentation; they do not subscribe to automation events. After MCP or HTTP dispatch, inspect script status, saved state or a widget acknowledgement. An `executed` publish outcome confirms publication, not completion of every asynchronous consumer.

Read [pipeline setup](dikciz://docs/recipes/event-setup), then its linked Lua and widget sections for a complete two-input example.
