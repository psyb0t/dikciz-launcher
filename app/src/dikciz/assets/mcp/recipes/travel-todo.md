# GPS todo app

Dikciz > Apps > Travel todo opens a complete editable example under /sdcard/Dikciz/apps/travel-todo/. The manifest, ui.html, ui.css, ui.js and main.js are the reference source. Initial tasks include Go to New York. No geofence is created on installation or opening.

Read customAppRead appId travel-todo before edits. Use customAppControl action open to request UI; inspect uiVisible/snapshot for actual presentation. The UI sends customAppEvent events todo.open, todo.add payload {title}, todo.toggle payload {id}, todo.watch and todo.stop. Backend returned patchState persists the full list. ui.js listens for dikciz-state and redraws it.

Only start monitoring when the owner asks. Grant precise foreground location using requestDeviceAccess requirementId locationForeground and enable device location. todo.watch creates geofenceSave id travel-todo-nyc, latitude 40.7128, longitude -74.0060, radiusMeters 20000, enabled true. This circle is an example destination, not an exact city boundary. Android location and foreground-service restrictions apply.

The manifest subscribes to geofence.enter. Its payload has id/title/location. main.js ignores other fence IDs and inactive watches, checks only task new-york and deletes its fence after arrival. This runs with the UI closed. Reopen to inspect persisted state. The notification shows native location monitoring. todo.stop and app.stopped delete the owned fence without changing other geofences.

For another event-driven task, publish example.todo.complete with payload {id:"pack"} through automationDispatch with an actual HTML sourceWidgetAddress and an emitEvent action. The backend checks that exact task. Such custom events are owner automation inputs, not proof of physical arrival. Do not publish synthetic geofence events and describe them as GPS evidence.

No messages, calendar entries or external model calls are needed. Task text is rendered using textContent. It uses geofenceSave/geofenceDelete/requestDeviceAccess; legacy command metadata does not restrict its shared Dikciz access. Duplicate with a different app ID for separate state and a separate <app-id>-nyc fence. Export sources only; current encrypted home/chat backups exclude apps. Full walkthrough: docs/todo-app-tutorial.md in the repository.
