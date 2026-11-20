---
title: "Android Calendar Agenda and Next Alarm"
description: "See upcoming Android calendar events and the next system alarm in Dikciz, then open the calendar or clock to make changes."
image: /images/screens/custom-app-agenda.png
permalink: /apps/agenda-and-alarms/
parent: "Custom apps"
grand_parent: "Developer manual"
nav_order: 4
---

# Android calendar agenda and next alarm

Agenda and alarms shows upcoming Android calendar entries and the next alarm Android reports. It gives you a quick way to open the system calendar or alarm app to make changes there.

Install the [signed Dikciz APK](https://github.com/psyb0t/dikciz-launcher/releases/latest), complete [first-run setup](user-guide.md#install-dikciz-and-make-it-your-home-screen), then open **Dikciz > Apps > Agenda and alarms**.

<img src="{{ '/images/screens/custom-app-agenda.png' | relative_url }}" alt="Dikciz Agenda and alarms showing the upcoming events and next alarm sections" width="300" loading="lazy">

- Grant Calendar access, then tap **Refresh agenda**. The agenda reads the events Android exposes to Dikciz.
- Use **Open calendar** to create or edit an event in the Android calendar app.
- Use **Open alarms** to set, edit or confirm alarms in the Android clock app. An empty next-alarm field means Android did not report a next alarm to Dikciz.

The [Agenda and alarms source](https://github.com/psyb0t/dikciz-launcher/tree/main/app/src/dikciz/assets/apps/agenda/) is an editable example of reading device data and delegating edits to system apps.
