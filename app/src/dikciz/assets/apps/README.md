# Complete bundled apps

Pick a directory and you've got the source for a whole app: its manifest, screen, styles, UI JavaScript and backend. Runtime copies live under `/sdcard/Dikciz/apps/<id>/`, with separate `state.json` and `files/`. APK updates preserve edited copies.

| App | Reference behavior |
| --- | --- |
| `impact-response/` | Watch phone motion while armed and start a cancellable emergency countdown. See [Impact Response](../../../../../docs/app-impact-response.md). |
| `travel-todo/` | Small checklist with explicit geofence setup, matching arrival/custom events and cleanup. Start with the [full tutorial](../../../../../docs/todo-app-tutorial.md). |
| `sos/` | Contact plans, check-in deadlines, dimmed cancellation alerts and tracked message submission. |
| `voice-notes/` | Tap to record, save and play back a note. Hiding the app stops recording. |
| `agenda/` | Calendar reads and links to Android calendar/alarm apps. |
| `device-status/` | Live Android battery, resource and process measurements. |

DikcizCustomAppStore.BUNDLED selects installed sources. New reusable exports contain source only. Apps inherit the Android access granted to Dikciz; review their code before installation. Read [host contracts](../../../../../docs/custom-apps.md), stop active work before editing, and keep app-specific decisions here rather than adding native product screens.
