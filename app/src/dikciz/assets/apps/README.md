# Complete bundled apps

Each directory is an independent manifest, HTML, CSS, UI JavaScript and backend source bundle. Runtime copies live under `/sdcard/Dikciz/apps/<id>/`, with separate `state.json` and `files/`. APK updates preserve edited copies.

| App | Reference behavior |
| --- | --- |
| `travel-todo/` | Small checklist with explicit geofence setup, matching arrival/custom events and cleanup. Start with the [full tutorial](../../../../../docs/todo-app-tutorial.md). |
| `sos/` | Contact plans, check-in deadlines, dimmed cancellation alerts and tracked message submission. |
| `voice-notes/` | Explicit recording, save/playback and stop-on-hide lifecycle. |
| `agenda/` | Calendar reads and links to Android calendar/alarm apps. |
| `device-status/` | Live Android battery, resource and process measurements. |

DikcizCustomAppStore.BUNDLED selects installed sources. New reusable exports contain source only. Installation/import does not authorize monitoring or outgoing effects. Review [host contracts](../../../../../docs/custom-apps.md), stop active work before editing, and keep app-specific decisions here rather than adding native product screens.
