---
title: "Impact Response for Android"
description: "Use phone motion to trigger a configurable impact warning and emergency-contact SMS, with a cancellation countdown and optional photos, calls and WhatsApp."
image: /images/screens/custom-app-impact-response.png
permalink: /apps/impact-response/
parent: "Custom apps"
grand_parent: "Developer manual"
nav_order: 1
---

# Impact Response for Android

Impact Response watches for a possible hard impact using your phone's linear acceleration sensor. After a 20-second cancellation window it can text one contact you chose. It can also listen for hard braking with precise GPS and phone motion if you turn that option on.

Impact Response ships inside Dikciz Launcher and opens disarmed. Install the [signed Dikciz APK](https://github.com/psyb0t/dikciz-launcher/releases/latest), follow [first-run setup](user-guide.md#install-dikciz-and-make-it-your-home-screen), then open **Dikciz > Apps > Impact response**.

<figure>
  <img src="{{ '/images/screens/custom-app-impact-response.png' | relative_url }}" alt="Impact Response open and disarmed, with an emergency contact form and motion threshold settings" width="300" loading="lazy">
  <figcaption>Fresh setup with monitoring disarmed.</figcaption>
</figure>

## Set up and arm

1. Enter an international phone number with its country code, or find a contact by name and select the exact number. Contact lookup asks for Contacts access.
2. Set an acceleration threshold from 1 to 12 g and a duration from 0.01 to 168 hours. The defaults are 4 g and 2 hours. Decimal hours work, including `2.5` for 2 hours 30 minutes. Choose an SMS SIM if needed and tap **Save settings**.
3. Open **Review Android access** and enable SMS and notifications, including Dikciz's urgent countdown channel. Keep the app open when arming. A phone without an available linear acceleration sensor cannot arm.
4. Tap **Preview message** to read a sample with invented measurements. Preview sends nothing. Tap **Arm for … hours** to begin, then check for **Live motion sensor confirmed**. Tap **Disarm** to stop.

While armed, the app watches for one incident. An impact warning includes the detection time, measured acceleration and threshold. Recent supported GPS readings can add a speed estimate. Android controls the lock screen, audio, and visibility of the warning. Use **Cancel pending alerts** during the countdown. After the countdown, **Close alert and stop repeats** stops pending steps. Android cannot recall SMS, calls or shares already submitted.

## Optional actions

| Setting | What it needs and does |
| --- | --- |
| Recent GPS speed or hard braking | Precise Android location and timely GPS fixes. Hard braking also needs corroborating phone motion. When speed cannot be established, the message says it is unavailable. |
| Short call | Android phone access, cross-app accessibility and a compatible, visible dialer. The app attempts to end the call after about two seconds and reports if hang-up could not be verified. |
| Save front and rear photos | Camera access and an available camera. Successful captures include the saved file path. Android can restrict camera capture while locked or in the background. |
| Send photos via WhatsApp | Enable photos and enter the exact WhatsApp chat title and the device storage label shown in the Android file picker. The phone must be awake and unlocked, with cross-app accessibility enabled. SMS is submitted first. WhatsApp layout changes or ambiguous matches can stop the share. |

Check **Latest incident** for action outcomes and **Refresh SMS status** for Android's SMS result. A submitted message does not prove the contact received it. Impact Response measures motion of the phone. It can miss real impacts or trigger from ordinary motion, and it cannot confirm a crash or guarantee delivery.

## Read or edit the detector

The [complete app source](https://github.com/psyb0t/dikciz-launcher/tree/main/app/src/dikciz/assets/apps/impact-response/) is an editable custom app. Its backend requests 5 ms sampling, a two-second raw history and batches about every 100 ms. Android determines the delivered rate. The app evaluates samples, stops or renews the capture lease, and disarms when readings are lost or capture ends unexpectedly. Fast background capture can use extra battery. See [raw sensor sessions](native-device-control.md#raw-sensor-sessions) and [app source and state](custom-apps.md#files-and-state).

If you already installed an app with ID `impact-response`, Dikciz keeps that copy and its settings. The bundled version fills an absent installation and respects previous removal.
