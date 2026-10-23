# Starter home

Fresh installations and **Reset Dikciz home** use two pages with four independent HTML WebViews. Existing saved homes are preserved on APK update. Reset replaces pages and restores matching bundled utility scripts; other saved scripts and policies remain. It restores the bundled 8-column, 6-row grid.

Each widget spans eight columns and three rows. Long press it or its handle to edit the complete HTML, CSS, JavaScript and JSON state, change appearance, resize, move or delete it. Each owns separate state and files under `/sdcard/Dikciz/pages/<page-id>/widgets/<widget-id>/`. Removing one preserves its neighboring WebView and removes its own state and files.

## Home

**Time and weather** shows local time and date with city and temperature. Expand **Weather details and city** for feels-like temperature, wind, rain probability, low/high, European AQI and city controls. It tries already-permitted phone location when no city is saved. Missing access stays inline until you tap **Use my location**. Grant access, then tap again. A manual city takes precedence. Device locations are checked again after five minutes when visible.

City search sends the name to Open-Meteo; weather and air-quality requests send coordinates. The services load independently, retain dated successful readings on failure and refresh missing or fifteen-minute-old weather when opened. There is no hidden-page polling. Requests time out after fifteen seconds and accept at most 64 KiB each. Calendar, recordings and device metrics are not sent.

Attribution: [Open-Meteo weather](https://open-meteo.com/en/docs), [geocoding](https://open-meteo.com/en/docs/geocoding-api), and [CAMS ENSEMBLE air quality through Open-Meteo](https://open-meteo.com/en/docs/air-quality-api). Public endpoints are for personal, non-commercial use under the provider terms.

**Agenda** shows the next Android alarm and up to three upcoming calendar occurrences in the current seven-day window. Android expands recurring events. All-day dates retain their calendar date. Calendar data stays on the phone. Tap **Allow calendar access**, return from Android and tap **Refresh agenda**. It refreshes once a minute while visible and when reopened. The bounded preview scans up to ten pages of fifty occurrences and directs you to Calendar when more remain. **Open calendar** and **Open alarms** request Android activities. Missing handlers produce an explicit error. The widget never changes events or alarms.

<img src="images/user-guide/01-first-launch.png" alt="Independent time/weather and agenda widgets on Home" width="280">

## Tools

**Voice notes** starts only when you tap **Record voice note**. If Android access is missing, review it and tap Record again. Tap **Stop and save** to finalize an AAC M4A. Recording has a five-minute maximum and stops when its page becomes hidden. This is foreground microphone capture, not a background service or cellular call recorder. Android interruptions report failure instead of claiming a saved recording.

Completed audio moves from temporary capture storage into this WebView directory with generated filenames. The five most recent recordings have playback buttons; earlier files and metadata remain. **Stop playback** releases playback. A failed move or metadata save leaves **Retry saving** available. Saved notes survive reopening and are removed when their widget is deleted. Copy them elsewhere before deleting the widget to keep them. Recorder sessions do not survive app process death; saved files do.

**Device status** shows battery, charging/battery saver, available RAM, free app-data filesystem capacity and network transport. It refreshes every thirty seconds while visible or on **Refresh device**. Network presence does not guarantee working internet. It starts no sensor listeners and requests no root access.

<img src="images/user-guide/03-insights-page.png" alt="Independent voice notes and device status widgets on Tools" width="280">

## Optional complete templates

The picker includes all four starter templates with fresh independent state. The combined **Home dashboard**, app search, notes, detailed **System dashboard**, **Live sensors**, and **BTC and economic news** remain optional. Adding a template copies the whole HTML/CSS/JavaScript document. **Save to library** makes a customized document reusable. Library edits do not silently change placed widgets.

The detailed sensor template samples actual hardware while visible. Markets and news fetch on explicit Refresh, save dated successful data and report provider failures. See [HTML editing](user-guide.md#edit-an-html-widget), [configuration](dikciz-configuration.md) and [API coverage](capability-coverage.md).

## Optional Lua utilities

Three example sources start disabled. Review them in **Settings**, **Scripts** before enabling them. Policies and subscriptions are prepared; none depends on a particular page or widget.

| Script | Behavior |
| --- | --- |
| `low-battery` | One notification at 20 percent or lower while discharging. Charging or reaching 30 percent resets it. |
| `charge-ready` | One notification at 80 percent or higher while charging. Unplugging or dropping below 75 percent resets it. |
| `screen-lock` | Explicit manual lock with owner-enabled Device Administration. Granting access never triggers it. |

Notification reminders need Android notification permission. Add a native script dashboard from the picker when wanted. See [Lua scripting](scripting-tutorial.md).
