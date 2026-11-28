---
title: "GPS To-do List for Android"
description: "Check off a task automatically when you arrive near a destination, or complete it with a custom event in the editable Travel todo app."
image: /images/screens/custom-app-travel-todo.png
permalink: /apps/travel-todo/
parent: "Custom apps"
grand_parent: "Developer manual"
nav_order: 6
---

# GPS to-do list for Android

Put **Go to New York** on your list and let the phone cross it off when you get there. That's the bundled arrival rule. For groceries and the rest of the boring stuff, tick the box yourself.

Install the [signed Dikciz APK](https://github.com/psyb0t/dikciz-launcher/releases/latest), complete [first-run setup](user-guide.md#install-dikciz-and-make-it-your-home-screen), then open **Dikciz > Dikciz apps > Travel todo**.

<img src="images/screens/custom-app-travel-todo.png" alt="Travel todo checklist with a Go to New York task and a location-watch control" width="300" loading="lazy">

1. Add a task or tick an existing one. The checklist works without location access.
2. To try arrival automation, grant the requested location access and tap **Watch New York**. The arrival rule can run while the app screen is closed, subject to Android's location rules.
3. Stop the watch from the app when finished. Change the destination or send a custom event from the [full tutorial](todo-app-tutorial.md).

The [Travel todo bundle](https://github.com/psyb0t/dikciz-launcher/tree/main/app/src/dikciz/assets/apps/travel-todo/) shows how a geofence event or a custom event can update persistent app state.
