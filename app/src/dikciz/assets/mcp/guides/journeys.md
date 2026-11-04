# Safety and journeys

sosPlanSave stores contacts [{address,label?}] and optional message/checkInSeconds/graceSeconds/updateSeconds/maximumUpdates/subscriptionId. It stays inactive. sosPreview is a dry run.

sosControl action arm or trigger requires confirm true, foreground location, SEND_SMS and notification access. arm waits the check-in period, trigger begins the grace. checkIn resets the deadline, cancel stops future sends but cannot recall SMS. sosStatus returns run state and receipt keys. Poll smsStatus per key for carrier results. Default check-in 3600s, grace 60s, update 600s, maximumUpdates 3. Minimum check-in/update 60s, grace 5s; maximumUpdates 1..24. At most ten exact contacts. Message tokens: {location}, {accuracyMeters}, {timestamp}.

A visible foreground service offers Stop all. Android can delay or stop monitoring. Rebooted runs need review and never automatically resume outgoing SMS. Do not claim emergency-service reliability. Recipient/update idempotency keys persist before submission.

geofenceSave requires title/latitude/longitude/radiusMeters, optional id/enabled/dwellMilliseconds. Disabled by default. Radius >=100m, max32 fences. Accurate real fixes plus hysteresis produce geofence.enter/exit/dwell custom events. geofencesList pages results; geofenceDelete needs exact id.

journeyControl action start records one real route, optional title/sampleIntervalMilliseconds (1000..60000, default10000). Stop by id. journeysList and journeyRead use offset/limit/hasMore. Maximum64 journeys,10000 points each. Reboot interrupts recording. journeyDelete needs id and confirm true after stopping. The native preview draws recorded points locally.

Private companion data is not currently included in home/chat backups.

During cancellation grace, the shared service shows a countdown with Cancel SOS using an owner-enabled overlay, or a foreground dialog. An urgent notification offers the same cancellation. Alarm pulses, vibration and available offline English speech explain the pending send. Android volume/DND, lock-screen and overlay restrictions apply. sosStatus.warning reports presentation, remainingSeconds, screenVisible, alarmVolume and speechState. Cancel clears warning resources and future sends, not completed SMS or unrelated route monitoring.
