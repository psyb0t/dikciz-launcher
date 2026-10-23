# Notification management

`notificationsList` returns active notifications with their Android key, package, title, text, post time and action tokens. Its new `actions` array describes each exposed action with `actionToken`, bounded `title` and `supportsReply`. Tokens expire after 60 seconds, are single-use and become unavailable when the notification changes or disappears.

Use `notificationManage`, MCP tool `dikciz_notification_manage`, through any ordinary binding. Notification-listener access is required. Set `autoRequestPermissions: true` for the shared owner-review flow when access is missing, then explicitly retry after granting it.

| Action | Required fields | Effect |
| --- | --- | --- |
| `dismiss` | `notificationKey` | Ask Android to cancel that active notification. |
| `snooze` | `notificationKey`, `durationMilliseconds` | Snooze for 1000 through 604800000 milliseconds. |
| `reply` | `actionToken`, `text` | Submit a nonblank free-form inline reply through an action advertising `supportsReply`. Text is at most 4096 characters. |

Fields belonging to another action are rejected. Dismiss/snooze return `state: submitted`. Poll `notificationsList` or observe notification events to verify disappearance; Android can restrict removal of ongoing notifications. Snoozed notifications can return after the duration or an app update. No API success claims a remote message was delivered.

Reply returns `state: submitted`, `delivered: false` after Android accepts its PendingIntent. The receiving application owns processing, network transmission and delivery. Resolve the correct notification and action before submitting. Tokens are consumed even if Android later rejects dispatch, preventing accidental duplicate retries.

A missing active key returns `native_record_not_found`. Expired/consumed tokens return `notification_action_unavailable`. Plain actions return `notification_reply_unsupported` without consuming the token. Android rejection returns `notification_action_rejected`. A disconnected listener returns `notification_listener_unavailable`.

The existing Lua `notificationControl` action remains the policy-gated way to invoke a plain action token. The shared `notificationManage` command adds typed dismissal, snooze and reply without changing that action's contract.
