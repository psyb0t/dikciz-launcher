# Activity and recovery

Use dikciz_activity_read for persistent command metadata. Filter by exact actor or outcome and follow offset/hasMore. The problem outcome filter includes rejected, failed and interrupted work. Interrupted work has an unknown outcome; never replay uncertain writes automatically.

The dikciz_resource_usage tool reports process measurements and executor-thread CPU by caller, with explicit attribution limits. The dikciz_resource_control tool disables future Lua deliveries by scriptId. Use dedicated chat, recording, shell or job stop operations where possible. The dikciz_activity_cancel tool requests best-effort interruption by activityId and never rolls back effects.

Event replay survives process death. The dikciz_event_consumer_read tool reads after a named durable cursor without acknowledging. After successfully handling events, acknowledge the page's nextCursor through dikciz_event_consumer_ack. Check historyGap/lostEvents first. Re-reading without acknowledgement may return duplicates. Keep side effects idempotent.

Visual rule tools expose nested schemas: preview compiles real Lua and simulates a sample without Android calls; save persists a disabled-by-default rule. Explicit enabled=true activates future events. The native editor shares these procedures.

Activity/resource/rule administration requires owner access. Read-only chat hides mutation tools and rejects forged writes.

resourceControl accepts target and id: script, widget (also pageId), conversation, imageStream or speechStream. Conversation pause clears event rules and requests stop. No completed effect is rolled back. automationRuleDelete removes a visual rule and its generated script/subscription.
