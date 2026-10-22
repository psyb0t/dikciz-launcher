# Direct Android data and system APIs

Use these before driving Contacts, Messages or Settings through Accessibility. Call `dikciz_native_data_status` for resources, columns, writable columns, create fields, foreign keys, search columns and actual grants. Every binding uses the same executor.

`dikciz_content_query` accepts `resource`, optional `columns`, exact `filter`, literal substring `query`, `limit` and `offset`. Defaults: 50 rows, offset 0. Maximum: 200 rows, offset 1000000, 32 columns, 4096 characters per text field and 512 KiB per page. Results contain `items`, `limit`, `offset`, `hasMore`. Continue at offset plus returned count. Row-level `truncatedFields` identifies shortened text. Arbitrary URI, SQL and column inputs are rejected.

Resources: `contacts`, `phones`, `emails`, `rawContacts`, `contactData`, `calendars`, `calendarEvents`, `calendarReminders`, `sms`, `callLog`, `images`, `audio`, `videos`.

Contact lookup:

~~~json
{"resource":"phones","filter":{"display_name":"Example Contact"},"columns":["display_name","data1","raw_contact_id"]}
~~~

`data1` is the phone number. Resolve ambiguity before sending.

`dikciz_content_write` takes `resource`, `operation` (`create`, `update`, `delete`), row `id` for update/delete, and `values` for create/update. Only raw contacts, contact data, calendar events and reminders are writable. Unspecified fields stay unchanged. SMS history is read-only; no default SMS role is requested.

`dikciz_content_batch` applies up to 32 operations atomically within one provider. Optional `backReferences` maps foreign-key columns to earlier create-operation indices. Native discovery lists allowed relationships. Preserve unrelated user records.

Other tools: `dikciz_sim_list`, `dikciz_call_control`, `dikciz_clipboard_get`, `dikciz_clipboard_set`, `dikciz_location_get`, `dikciz_notifications_list`, `dikciz_media_sessions_list`, `dikciz_device_state`. SIM, notification and media lists accept page fields.

Location returns last-known data with age, not a fresh fix. Android can restrict background clipboard reads and call operations. Existing event subscriptions provide sensors and live location programmatically.

Missing grants use `native_permission_<requirementId>` and the shared `permissionRequest`. Set `autoRequestPermissions` for owner review, then explicitly retry after checking access. Role or foreground restrictions can produce `native_access_denied`. Do not fall back to root.

[Tracked SMS workflow](dikciz://docs/recipes/contact-sms).

`deviceState.system` reports RAM, app-data filesystem capacity, uptime, logical processors and CPU samples. System CPU may be unavailable under Android restrictions; Dikciz CPU is a separate percentage of one core. Samples need two calls. Sensor records are a bounded hardware inventory, not live readings. Battery temperature is in tenths Celsius and voltage is in millivolts. All transports and HTML use the same executor.
