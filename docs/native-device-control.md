# Direct Android data and control

Use native commands instead of opening Contacts, Messages or Settings and tapping their UI. WebSocket, HTTP, MCP, HTML and enabled Lua use the same validator, executor, permission recovery and result. These operations call Android APIs without root or a shell.

Start with `nativeDataStatus` or MCP `dikciz_native_data_status`. Its `resources` list reports each resource's columns, writable columns, required create fields, foreign keys, search fields and current grants. Discover executable schemas through `controlStatus` and MCP `tools/list`.

## Read provider data

`contentQuery` and MCP `dikciz_content_query` accept:

| Field | Meaning |
| --- | --- |
| `resource` | Required name from native data discovery. Arbitrary URIs and authorities are rejected. |
| `columns` | Optional array of unique allowlisted columns, at most 32. Defaults to the first 12 declared columns. |
| `filter` | Exact equality filters combined with AND. Null matches SQL NULL. |
| `query` | Literal substring search over the resource's declared search fields. SQL wildcards are escaped. |
| `limit`, `offset` | Integer page size and row offset. Defaults 50 and 0. Ranges 1 through 200 and 0 through 1000000. |

Results are `{resource, items, limit, offset, hasMore}`, ordered by `_id` ascending. Continue at `offset + items.length` while `hasMore` is true. Concurrent edits can change page membership. A page is limited to 512 KiB and text fields to 4096 characters. Shortened fields appear in each row's `truncatedFields` array. Empty results return an empty array. Binary provider columns are excluded.

Resources cover `contacts`, `phones`, `emails`, `rawContacts`, `contactData`, `calendars`, `calendarEvents`, `calendarReminders`, `sms`, `callLog`, `images`, `audio` and `videos`. Provider column names remain native. For phone rows, `display_name` is the contact name and `data1` is the number.

~~~json
{"type":"contentQuery","resource":"phones","filter":{"display_name":"Example Contact"},"columns":["contact_id","raw_contact_id","display_name","data1"]}
~~~

Use `query` for partial name or number search. Resolve multiple matches before choosing a recipient.

## Write records atomically

`contentWrite` and MCP `dikciz_content_write` accept `resource`, `operation`, optional `id` and `values`. Operations are `create`, `update` and `delete`. Create omits `id`; update and delete require one positive row ID. Updates preserve unspecified columns. Delete accepts no values. No delete-all or raw selection argument exists.

Writable resources are raw contacts, contact data, calendar events and reminders. Other resources are read-only. Dikciz never requests the default SMS role to edit message history.

`contentBatch` and MCP `dikciz_content_batch` accept up to 32 write records in `operations`. Every operation must use the same provider. A failed operation rolls back the entire batch. Batch records may add `backReferences`, mapping an allowlisted foreign-key column to an earlier create-operation index.

Create a local contact, name and phone number:

~~~json
{"type":"contentBatch","operations":[
  {"resource":"rawContacts","operation":"create","values":{"account_name":null,"account_type":null}},
  {"resource":"contactData","operation":"create","values":{"mimetype":"vnd.android.cursor.item/name","data1":"Example Contact"},"backReferences":{"raw_contact_id":0}},
  {"resource":"contactData","operation":"create","values":{"mimetype":"vnd.android.cursor.item/phone_v2","data1":"4085550100","data2":"2"},"backReferences":{"raw_contact_id":0}}
]}
~~~

Results contain one `results` entry per operation, with a created `id` or changed-row `count`. Save IDs before updates or deletes. To remove this contact, delete its raw-contact ID; Android removes dependent data. Synced-account rules still apply.

Calendar values use native fields including `calendar_id`, `title`, `dtstart`, `dtend`, `eventTimezone`, `allDay`, `description`, `eventLocation`, `rrule` and `duration`. Read `calendars` first and choose a writable one. Timestamps use Unix milliseconds. Use `UTC` or an IANA zone in `eventTimezone`. Android validates recurrence and calendar-specific requirements.

## Send SMS and inspect receipts

`smsSend` and MCP `dikciz_sms_send` require `idempotencyKey`, `recipient` and `body`; `subscriptionId` is optional. Numbers use digits and an optional leading plus. Common spaces, parentheses, dots and hyphens from contact formatting are normalized before sending and duplicate checks. Bodies are limited to 4096 characters and 32 segments. Read `simList` to select a SIM. Missing defaults return `no_default_sms_subscription`.

~~~json
{"type":"smsSend","idempotencyKey":"example-message-1","recipient":"4085550100","body":"Example message"}
~~~

Use the same key for retries. Dikciz reserves it durably before the modem call. Identical retries return the same receipt with `duplicate: true`. Different content or an explicitly different SIM returns `sms_idempotency_conflict`. A changed system default never turns an identical retry into another send.

Poll `smsStatus` or MCP `dikciz_sms_status` with that key. Receipts return message ID, selected subscription, segment counts, per-part callback codes and state, without recipient or body:

| State | Evidence |
| --- | --- |
| `submission_unknown` | The durable reservation exists; completion of the modem call is unknown. |
| `accepted` | Android accepted submission. Sent callbacks are pending. |
| `sent` | Every part received a successful Android sent callback. Delivery is unconfirmed. |
| `delivered` | Successful carrier delivery reports confirm every part. |
| `failed` | Submission threw or a sent callback reported failure. |
| `delivery_failed` | A delivery report explicitly reported failure. |

Delivery reports depend on the carrier and can remain absent. `deliveryConfirmed` is true only for `delivered`. Never resend an uncertain submission under a new key without checking what happened. Receipts survive recreation and process restart. The private store retains up to 512 keys until app data is cleared and refuses new sends when full rather than discarding duplicate protection. SMS can cost money.

Read permitted SMS history through `contentQuery` resource `sms`. Android can save messages sent by non-default SMS apps into its SMS provider. The legacy typed `sendSms` action confirms submission only; use tracked `smsSend` when receipts and duplicate protection are needed.

## Direct system commands

| Command | MCP tool | Behavior |
| --- | --- | --- |
| `simList` | `dikciz_sim_list` | Active subscription IDs, slots, display/carrier names and default SMS SIM. No numbers or private SIM identifiers. |
| `callControl` | `dikciz_call_control` | `action` is `state`, `place`, `answer` or `end`. Place requires `recipient` and reports acceptance, not connection. Android may restrict call control by role. |
| `clipboardGet` | `dikciz_clipboard_get` | Plain text and availability. Android can restrict background reads. |
| `clipboardSet` | `dikciz_clipboard_set` | Required plain `text`, including an empty string. |
| `locationGet` | `dikciz_location_get` | Recent permitted position, accuracy, age, source and optional place name. `maxAgeMilliseconds` defaults to 300000. If needed, waits for a new fix for `timeoutMilliseconds` (default 10000, range 0 through 120000). Zero reads cache only. Disabled location, no cached fix and timeout return `available: false` with a reason. |
| `notificationsList` | `dikciz_notifications_list` | Current notification text and short-lived action tokens through the connected listener. |
| `mediaSessionsList` | `dikciz_media_sessions_list` | Active packages, playback state, position and bounded track metadata. |
| `deviceState` | `dikciz_device_state` | Screen/keyguard, battery/charging, power save, volume, ringer and connectivity state. |

SIM, notification and media lists accept page fields. Existing typed actions control notifications, media, volume, intents and locking. Existing subscriptions provide sensors, live location, Health Connect, Bluetooth and other events programmatically.

## Sensors, camera, microphone and speech

All commands below share the same schemas, execution and permission recovery in WebSocket, HTTP, MCP, HTML `dikciz.command` and Lua `context.request` or `context.requestJson`.

| Command | MCP tool | Behavior |
| --- | --- | --- |
| `sensorReadings` | `dikciz_sensor_readings` | Sample all sensors or one `sensorId` from the current device inventory. Accepts `limit` and `offset`; follow `hasMore`. `timeoutMilliseconds` defaults to 1000 and accepts 1 through 5000. |
| `cameraList` | `dikciz_camera_list` | Camera IDs, front/back/external facing and flash availability. Does not start capture. |
| `cameraCapture` | `dikciz_camera_capture` | Save an actual JPEG. Optional `cameraId` defaults to the first camera. `maxDimension` defaults to 1280 and accepts 320 through 4096; no fitting camera size returns an explicit error. Quality, orientation, crop zoom, exposure and autofocus controls return requested settings and actual capture metadata. See [camera controls](camera-control.md). |
| `microphoneRecord` | `dikciz_microphone_record` | Save AAC audio in an M4A container. `durationMilliseconds` defaults to 3000 and accepts 1000 through 120000. |
| `speechSpeak` | `dikciz_speech_speak` | Speak required `text` through Android device output, at most 2000 characters. Reports completion or failure. |
| `callAudio` | `dikciz_call_audio` | `action: status` reports cellular audio capture and injection support. `record` and `speak` return `call_audio_unsupported` without privileged system integration. |

Sensor discovery includes static and connected dynamic sensors. Records include ID, type, name, vendor, dynamic status, reporting mode, range, resolution and power. Actual samples contain `values`, Android accuracy, monotonic `timestampNanoseconds` and sample age. Unavailable samples contain a reason. All-sensor pages report permission failures per sensor, so one restricted sensor does not hide others. Targeting a restricted sensor by ID uses the shared permission error and optional owner-review flow. Trigger-only sensors can remain `waiting_for_trigger`. IDs address the current device inventory; rediscover after hardware changes. Every request releases its listeners. Poll only while data is needed; background Lua subscriptions remain available for event-driven use.

Camera and microphone require their Android grants and foreground eligibility. Open Dikciz before capture. Concurrent requests for the same capture resource return `hardware_busy`. Successful capture returns `state: saved`, `path`, `mimeType` and byte count. Paths are generated under app-specific external `captures` storage. Read or download them through the existing file APIs. Incomplete files are removed. Android microphone silencing returns an explicit failure when reported by the recorder.

Camera and speech accept `timeoutMilliseconds`, default 30000, range 1 through 120000. The supplied client adjusts HTTP, WebSocket and MCP response waits to cover requested hardware durations. Other clients must allow the same time. Timeouts and interruption release capture resources. Speech uses the configured Android engine and fails explicitly if unavailable. Device speaker output is not cellular audio injection. Stock Android reserves call-stream capture for privileged components; granting microphone permission does not grant call-stream access.

## Permissions and limits

Missing grants return `native_permission_<requirementId>` with the shared `permissionRequest`. Opt into owner review with `autoRequestPermissions`, then explicitly retry after checking the grant. `native_access_denied` means Android rejected the operation despite declared grants, for example because of a role, ownership or foreground restriction.

Malformed fields, columns, types, ranges, authorities and references return `validation_failed`. Read-only writes return `native_read_only`. Provider failures return `native_operation_failed` or `native_unavailable`. Logs omit contact and message values.

## Capability gaps

| Operation | Current route |
| --- | --- |
| Contact/calendar data, SMS/history, call history, SIMs and system snapshots | Direct commands above. |
| Apps, packages, files, APK installation, shell, screenshots and restart | Existing direct commands. |
| Notifications/media control, volume, intents and lock | Existing typed actions. |
| Hardware sensor snapshots and current location | Shared direct commands and existing background subscriptions. |
| Camera, microphone and device speech | Shared direct commands above, subject to Android foreground access. |
| Health Connect and other background sources | Existing event subscriptions. |
| Proprietary app features without a public API | Accessibility/UI automation or an API supplied by that app. |
| Silent permission grants, arbitrary private app databases, unrestricted clipboard and protected settings | Android privilege and foreground restrictions apply. |
| Fastboot and bootloader | Outside the running Android app. |

Arbitrary third-party databases, account management and privileged system settings are not implemented by these commands. Permission declarations are not a promise that every possible feature exists. Discover the running build's commands.

## Additional native commands

See [USSD requests](ussd-control.md), [camera capture/video](camera-control.md), [event replay](event-replay.md), [speech recognition and synthesis](speech-recognition.md), [audio playback and recording sessions](audio-playback.md), [hardware controls](hardware-controls.md), [Bluetooth](bluetooth-control.md), [NFC](nfc-control.md), [USB](usb-control.md), [Wi-Fi](wifi-control.md), [location services](location-services.md), [notification management](notification-management.md) and [durable jobs](command-jobs.md) for their dedicated operations. The [coverage table](capability-coverage.md) separates implemented operations from remaining work and Android privilege requirements.
