# Typed actions: apps, media and messages

Read [dispatch basics](dikciz://docs/guides/actions-state). Every record has type. Lua policy.actions uses the same name except setMediaVolume uses mediaVolume.

- launchApp requires component, a flattened Android component. Check activityLaunches. Accepted launch is not foreground verification.
- appAction requires component and action: launch, appInfo, uninstall or forceStop. Typed actions refuse addShortcut. Force stop needs approved root.
- postNotification requires title and text within configured title/text limits. Android posting access applies.
- sendSms requires dialable recipient (max20chars) and body within configured text limits. Legacy submission only; Android can show confirmation or rate-limit it. Android can persist a non-default app's sent SMS. Prefer ordinary smsSend/smsStatus for SIM choice, durable idempotency and receipts.
- explicitIntent requires intentType activity/broadcast and component; optional action. This typed form has no data URI. Use the ordinary intent command for its broader schema.
- mediaControl requires packageName and command: play, pause, playPause, skipNext, skipPrevious or seekTo. Only seekTo requires/allows positionMilliseconds (integer0..86400000). Notification-listener access is required.
- setMediaVolume requires levelPercent (integer0..100). Policy capability is mediaVolume, not setMediaVolume. Results/logs may use mediaVolume as the capability name.
- notificationControl requires an unexpired plain actionToken from notificationsList. Use ordinary notificationManage for inline reply, dismiss or snooze.
- lockDevice has no extra fields. Owner-enabled Device Administration is required.

Volume example:
~~~json
{"type":"setMediaVolume","levelPercent":35}
~~~

Lua returns that record under actions and its policy allows "actions":["mediaVolume"]. The percentage maps to discrete Android media-volume indices; it is not sound pressure. Read deviceState.mediaVolume/mediaVolumeMaximum before/after. Per-player audioPlayback.volumePercent is a separate gain setting. Fixed volume or rejected read-back is an explicit outcome.

None of these actions silently grants Android access. Message submission is not delivery, notification invocation is not remote application success, and a dispatch array is not transactional. Only perform external effects the owner requested.

[UI actions](dikciz://docs/guides/actions-ui), [Tracked SMS](dikciz://docs/recipes/contact-sms), [Audio](dikciz://docs/guides/audio).
