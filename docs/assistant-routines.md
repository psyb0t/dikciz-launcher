---
title: "Assistant routines"
description: "Run read-only assistant routines for the current screen, device health and phone data, with explicit results and Android access requirements."
permalink: /assistant-routines/
nav_order: 5
parent: "Tutorial"
---

# Assistant routines

Built-in routines provide repeatable read-only tasks using an existing conversation and saved provider. List them with `assistantRoutines`, inspect exact instructions with `assistantRoutinePreview`, or select **Run a routine** in the chat conversation menu.

| ID | Reads |
| --- | --- |
| morningBriefing | Agenda, next alarm, device readiness and available weather. |
| messageDigest | A bounded unread-SMS digest without replies or marking read. |
| travelBriefing | Permitted location with accuracy/age, battery and connectivity. |
| deviceAudit | Current resource measurements, access and recent failures. |
| eventReport | An actual event and relevant read-only context. |

`assistantRoutineRun` requires routineId, conversationId and a unique requestKey. It queues the task through the existing chat runtime; poll chatControl read/status for results. The key prevents duplicate queueing. It invokes the saved model and can incur provider charges.

The whole routine turn is forcibly read-only at tool discovery and execution, even if the conversation is writable. It cannot send SMS, start tracking, request permissions, modify files or disable its access restriction. Missing data stays explicit. The saved conversation's ordinary access setting is unchanged.

`assistantRoutineSubscribe` requires routineId, conversationId, ruleId and a canonical subscription object. It adds or replaces that rule while preserving other rules. Event runs retain read-only task metadata and use the existing background chat activity/Stop notification. Inspect, edit or remove rules through chatControl subscribe. Ordinary provider configuration, context budgets and event cooldowns still apply.
