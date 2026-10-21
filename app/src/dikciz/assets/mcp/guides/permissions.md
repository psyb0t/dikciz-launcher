# Request Android access

Read `dikciz_automation_status`. Choose `requirementId` from `deviceAccess.requirements`; do not invent Android permission names. Examples include `sharedStorage`, `accessibility`, `locationForeground`, `locationBackground`, `homeApp` and `installPackages`.

Call `dikciz_request_device_access` with `{ "requirementId": "accessibility" }` to request owner review directly. Ordinary commands can opt in with `autoRequestPermissions: true`. Default `false` reports missing access without opening permission UI.

`permissionRequest` preserves the underlying error and reports `state`, `requirementId`, `notificationPosted`, `promptState`, `retryRequired`, `deduplicated` and `instructions`. `user_action_required` means the owner must review the phone. Tell them which access is needed and to tap **Allow access**. The notification opens Android's access screen. If notifications are blocked, opening Dikciz exposes the pending review.

The owner decides whether to grant access. Requests are deduplicated for 30 seconds. `request_dismissed` reports a dismissed review during that cooldown. A displayed notification does not prove access was granted. Background location requires foreground location first.

After owner confirmation, read fresh status and retry the authorized operation explicitly. Nothing retries automatically. In a batch, retry only refused actions because earlier actions may have completed. Policy refusals do not queue Android prompts. Shell failures retain ordinary output and exit status; identify the required access and request it explicitly. A permission flow cannot create root on an unrooted device.

For the complete checklist, open **Settings**, **Automation**, **Set up full device access**. It opens a separate **Full device access** task in Recents. Return to it after Android Settings or another app takes the foreground. The checklist refreshes from actual grants; each missing access has a setup button. While resumed, `dikciz_snapshot` reports `screen: "deviceAccess"`, and `dikciz_screenshot` captures this screen. This checklist is separate from a targeted remote review.

[Security boundaries](dikciz://docs/guides/security).
