# SIM subscriptions and own phone numbers

Call `dikciz_sim_list` directly. It reads Android subscription APIs without opening Settings or sending a carrier request. There are no required arguments. Optional `limit` (1..200, default50), `offset` (0..1000000, default0) and `autoRequestPermissions` (defaultfalse) follow the shared schemas.

The result has `items`, `limit`, `offset`, `hasMore`, `defaultSmsSubscriptionId` and `phoneNumberPermissionGranted`. Each item has `subscriptionId`, `slotIndex`, `displayName`, `carrierName`, nullable `phoneNumber` and `phoneNumberStatus`:

- `available`: Android supplied a nonempty number.
- `unavailable`: no number was supplied or telephony was temporarily unavailable.
- `permission_required`: the number-reading runtime grant is missing.
- `access_denied`: Android refused the number lookup despite the reported grant.
- `unsupported`: the lookup is unsupported on this device.

`READ_PHONE_STATE` is required to list subscriptions. Numbers additionally need `READ_PHONE_NUMBERS`. Missing number permission preserves metadata and adds `permissionRequest`. Pass `autoRequestPermissions:true` to request owner review, or call `dikciz_request_device_access` with `requirementId:"phoneNumbers"`. That review requests read access, not call placement or answering. Verify the actual grant before retrying. Read-only turns cannot prompt.

Android13+ uses SubscriptionManager.getPhoneNumber; older versions use the subscription number. Android may return no number even when access is granted. Numbers are carrier/device data, not verified identity, and their formatting varies. Do not invent an unavailable number. Use `subscriptionId` to select a SIM for authorized SMS/USSD operations; reading the list does not send anything.
