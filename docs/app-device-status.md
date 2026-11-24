---
title: "Android Battery, CPU and Memory Dashboard"
description: "Inspect battery, storage, memory, Dikciz process CPU history and available per-core counters in the bundled Device status app."
image: /images/screens/custom-app-device-status.png
permalink: /apps/device-status/
parent: "Custom apps"
grand_parent: "Developer manual"
nav_order: 5
---

# Android battery, CPU and memory dashboard

Device status puts battery, storage, memory and CPU measurements on one screen. Use it to see how much space is free or to follow Dikciz's CPU use while working in the launcher.

Install the [signed Dikciz APK](https://github.com/psyb0t/dikciz-launcher/releases/latest), complete [first-run setup](user-guide.md#install-dikciz-and-make-it-your-home-screen), then open **Dikciz > Dikciz apps > Device status**.

<img src="images/screens/custom-app-device-status.png" alt="Device status showing battery, process CPU history, storage and memory" width="300" loading="lazy">

Read the gauges and CPU history, then expand **Device details** for the additional values Android exposes. Process CPU is the Dikciz process. System CPU and per-core data depend on the device and Android restrictions. An unavailable counter is shown as unavailable. The screen pauses polling when hidden.

Open the [Device status source](https://github.com/psyb0t/dikciz-launcher/tree/main/app/src/dikciz/assets/apps/device-status/) to change the dashboard or reuse its data calls in your own app.
