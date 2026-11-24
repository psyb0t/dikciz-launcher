---
title: "SOS and Safety Check-ins for Android"
description: "Set up safety check-ins with exact emergency contacts, an audible cancellation countdown, tracked SMS, and optional location updates in Dikciz."
image: /images/screens/custom-app-sos.png
permalink: /apps/sos-check-ins/
parent: "Custom apps"
grand_parent: "Developer manual"
nav_order: 2
---

# SOS and safety check-ins for Android

SOS and check-ins can text selected contacts after an explicit SOS trigger or a missed check-in. A countdown gives you time to cancel before delivery. Saved plans stay inactive until you arm them.

Install the [signed Dikciz APK](https://github.com/psyb0t/dikciz-launcher/releases/latest), complete [first-run setup](user-guide.md#install-dikciz-and-make-it-your-home-screen), then open **Dikciz > Dikciz apps > SOS and check-ins**.

<img src="images/screens/custom-app-sos.png" alt="SOS and check-ins app with contact and plan controls" width="300" loading="lazy">

1. Add exact contact numbers and save a plan. Preview the message and verify the recipients.
2. Review Android SMS and notification access. Grant location access if you want the plan to include your location.
3. Arm a check-in, or explicitly trigger an SOS. Read the countdown and use its cancel button to stop pending sends.
4. Inspect SMS outcomes in the app. Android access, device state and network coverage affect delivery.

See the [SOS setup and cancellation walkthrough](safety-and-journeys.md#on-the-phone) for plan options and outcome details. [Impact Response](app-impact-response.md) is the separate app for motion-based warnings. The [SOS app source](https://github.com/psyb0t/dikciz-launcher/tree/main/app/src/dikciz/assets/apps/sos/) is editable.
