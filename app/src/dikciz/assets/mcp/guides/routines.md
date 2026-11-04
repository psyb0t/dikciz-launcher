# Read-only assistant routines

assistantRoutines lists morningBriefing,messageDigest,travelBriefing,deviceAudit,eventReport. assistantRoutinePreview with routineId returns exact instructions without a provider call.

assistantRoutineRun needs routineId,conversationId,requestKey. It queues using the saved provider and can incur its normal charges. Poll chatControl read/status. A repeated key deduplicates.

The whole turn forcibly hides mutating tools and rejects forged writes before execution regardless of the conversation's saved writable setting. It cannot grant itself permissions or change its access. Missing data stays explicit. The ordinary conversation setting remains unchanged.

assistantRoutineSubscribe needs routineId,conversationId,ruleId,subscription. subscription uses canonical automation events and cooldowns. It preserves other rules. Event runs carry read-only routine metadata and normal background chat notifications/Stop. Manage/removal through chatControl subscribe.

Native chat conversation menu: Run a routine. Morning briefing reads agenda/alarm/readiness; message digest reads bounded unread SMS; travel reads available location/battery/connectivity; device audit reads resource/access/failure data; event report interprets the actual event.
