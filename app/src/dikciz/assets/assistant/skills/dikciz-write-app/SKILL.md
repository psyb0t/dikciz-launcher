---
name: dikciz-write-app
description: Author and verify an editable Dikciz custom app with web UI, background handlers and isolated state.
---

# Author a custom app

1. Resolve the installed app by ID or choose a new ID. On an edit, call dikciz_custom_app_read and retain the returned sourceRevision, manifest and source. Change only the requested parts. Keep the app ID and compatible state.
2. Prepare the manifest with version 1, id, title, description, language (javascript or lua), and events (an array of event names). Put HTML, CSS, browser JavaScript and background code in the separate html, css, javascript and backgroundSource fields of dikciz_custom_app_install. Install requires confirm true. Set expectedRevision to "" when creating an app or to the observed sourceRevision when editing. After a conflict, read again and reapply only your intended changes. A JavaScript backend implements synchronous onEvent(event), reads context.state and returns actions. For example, return {actions: [{type: "patchState", values: {count: (context.state.count || 0) + 1}}]} to increment a saved counter.
3. Simulate an unsaved draft with dikciz_automation_simulate using source, language, and event with type and optional payload. Use source for the draft or appId for the currently installed backend. Supply exactly one target. Inspect outcome and actionDetails. Install the bundle after the draft behaves as expected. Use actual events only when the owner asked for them and their effects are safe.
4. Read the saved app with dikciz_custom_app_read using the same appId. Use dikciz_custom_app_event with appId and event for a requested fixture run, then verify its saved state. To show the app, use phone_call with name dikciz_custom_app_control and arguments_json containing action open and appId. The open result says requested. Read the app again and check uiVisible before reporting it opened. Report the actual app ID and outcome.
