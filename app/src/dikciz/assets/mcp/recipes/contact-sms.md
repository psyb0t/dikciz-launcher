# Contact lookup and tracked SMS

Obtain the owner's intended contact and exact message first. SMS can cost money. This workflow needs contacts-read and SMS-send access. Read [native data](dikciz://docs/guides/native-data).

1. Query `phones` with `dikciz_content_query`, matching `display_name` exactly or using literal substring `query`. Read `data1`, the number. Resolve multiple distinct matches before sending.
2. Read `dikciz_sim_list`. Select the intended active `subscriptionId` when no valid default exists or when the owner specifies a SIM.
3. Call `dikciz_sms_send` with `idempotencyKey`, `recipient`, `body`, and optional `subscriptionId`:

~~~json
{"idempotencyKey":"example-request-1","recipient":"4085550100","body":"Example message"}
~~~

4. Poll `dikciz_sms_status` with the same key. `accepted` means submission; `sent` confirms successful callbacks for all parts; `delivered` confirms carrier delivery reports for all parts. Missing reports leave delivery unconfirmed.
5. Read permitted history through `dikciz_content_query` resource `sms` when needed. Android can save outgoing messages sent by a non-default SMS app.

Retry identical requests with the same key. Different content fails with `sms_idempotency_conflict`. A persisted `submission_unknown` must not be resent under a new key without checking. Receipts survive launcher/process restart and expose no body or recipient. Capacity is 512 retained keys; full storage rejects new sends instead of losing duplicate protection.

For a Lua custom event, enable a script, policy and namespaced subscription with `manualTrigger`. Pass a bounded Lua object table to `context.request` for `contentQuery`, require an unambiguous result, then call `smsSend`. The runtime escapes table values safely. The reply is the canonical envelope: inspect `type`, `result` or `code`. Use a stable caller-supplied request key and poll `smsStatus` through the same function. Do not concatenate unescaped names or message text into JSON.

Publish network events through `dikciz_automation_dispatch` with a valid HTML `sourceWidgetAddress` and an `emitEvent` action. See [event setup](dikciz://docs/recipes/event-setup). Publication is asynchronous; inspect script status and the SMS receipt.
