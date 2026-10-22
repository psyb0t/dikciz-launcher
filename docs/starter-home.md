# Starter home

New installations start with three pages. Existing edited homes keep their pages, widgets and scripts when the APK updates. **Reset Dikciz home** replaces saved pages with this starter and restores matching bundled scripts. Other saved scripts and policies remain; disable or delete unwanted older examples under Settings, Scripts.

The starter uses an 8-column, 6-row grid. Home fills all eight columns; the two Insights widgets also fill the page width. Updates preserve edited grids; resetting the home restores the bundled grid and validation limits too.

## Home

- **Clock and search** shows local time and date. Search opens the browser only after submitting a query.
- **Find an app** shows Android app icons and filters the real installed-app catalogue by name or package and launches a selected result. Up to six matches appear. Tap a result to launch it. The bottom app drawer shows all apps and adds separate native screen shortcuts; those are not inserted into this HTML document.
- **Quick note** saves up to 2,000 characters on this phone when you tap **Save note**. Unsaved edits are marked. Saved text survives switching pages and reopening Dikciz.

Home is one full-page HTML document with clock, app search and note sections. Long press anywhere for the whole WebView's controls. Edit shows its HTML, CSS, JavaScript and JSON state. The document's JavaScript controls its sections; they are not native preset objects. Existing homes and note state remain unchanged on update.

<img src="images/user-guide/01-first-launch.png" alt="Starter Home with clock, app search and quick note" width="280">

## Insights

**Your phone** reads Android battery level, plugged-in state, battery saver, network transport and internet validation. It refreshes when the page opens or when you tap **Refresh phone**. Android reporting an active network does not always mean internet access works.

**Weather and air** tries the phone location when no city is saved. Missing location access opens the owner-review flow. Grant access, then tap **Use my location** to retry. Android supplies a recent cached location or a bounded fresh fix; its place name is used when available. If location is disabled or unavailable, enter a city, tap **Find city**, then choose a match. A saved manual city takes precedence. Device-derived locations are checked again on page opening after five minutes. City search sends the name to Open-Meteo geocoding; weather and air-quality requests send coordinates to Open-Meteo. Contacts and notes are not sent.

It displays temperature, feels-like temperature, wind in km/h, today's rain probability, low/high temperatures and European AQI. Weather and air quality load independently. Missing values appear as unavailable. Saved readings carry their fetch times. Opening the page refreshes missing readings or data older than 15 minutes. There is no background polling. Refresh weather updates immediately; Use my location replaces the selected city only after a successful lookup.

Requests time out after 15 seconds and each response is limited to 64 KiB. A failed city search leaves the selected city unchanged. Choosing another city clears the previous city's readings. Partial service failure retains available readings for the selected city and names the failed service. No matching city, offline service and malformed responses stay recoverable through Find city or Refresh weather.

Data attribution: [Open-Meteo weather](https://open-meteo.com/en/docs), [Open-Meteo geocoding](https://open-meteo.com/en/docs/geocoding-api), and [CAMS ENSEMBLE air-quality data through Open-Meteo](https://open-meteo.com/en/docs/air-quality-api). The public endpoints are for personal, non-commercial use under the provider's terms. A commercial deployment must arrange appropriate service access.

<img src="images/user-guide/03-insights-page.png" alt="Insights with optional city weather and local phone status" width="280">

## Sensors

The third page lists static and connected dynamic Android sensors, including multiple sensors of the same type. Disconnected dynamic sensors disappear on refresh. It shows sampled values, accuracy, timestamp age, reporting mode, range and resolution. Sampling runs while visible, with a half-second sample window followed by a 1.5-second pause. Pause stops future requests. Each request unregisters its listeners when it finishes. Sensors without permission, rejected registrations and sensors waiting for a trigger show their actual status. Review sensor access requests the specific owner-controlled permission. Sensor values use Android units and array ordering.

## Complete WebView templates

The HTML widget picker offers **Home dashboard**, the individual complete clock/search, app search and note documents, **System dashboard**, **Live sensors**, and **BTC and economic news**. Adding a template copies a full HTML/CSS/JavaScript document into an independent WebView with fresh state. **Save to library** reuses a complete customized document. Library changes do not silently update existing widgets.

**System dashboard** reads local Android metrics on opening, every five seconds while visible and when Refresh is tapped. It displays RAM used and available, total RAM, free app-data filesystem capacity, battery and temperature, network state, logical processors and CPU samples. System CPU can be unavailable because Android restricts `/proc/stat`; Dikciz CPU is labeled separately as a percentage of one core and requires two samples. Expand Sensor inventory for the available sensor names and types. This inventory does not claim live sensor values or start listeners.

**BTC and economic news** starts with no external requests. Refresh fetches [Coinbase BTC/USD spot price](https://docs.cdp.coinbase.com/coinbase-app/track-apis/prices) and [GDELT economic coverage](https://blog.gdeltproject.org/gdelt-doc-2-0-api-debuts/) from the preceding 24 hours. It lists up to eight English-language headlines matching economy, inflation or monetary coverage, with publisher links and GDELT discovery times. Discovery time is not publication time. No API keys are required. These public services can rate-limit or fail independently.

Each request times out after 15 seconds and accepts at most 64 KiB. Text is rendered as text, and publisher links accept only HTTP or HTTPS. The widget saves successful results with their fetch times and keeps them dated if another request fails. It never polls in the background. No phone metrics, contacts or notes are sent with the requests.

## Optional Lua utilities

Open **Settings**, **Scripts** to inspect a script before enabling it. A script dashboard can also be added from the widget picker.

| Script | Behavior |
| --- | --- |
| `low-battery` | One notification at 20 percent or lower while discharging. Charging or reaching 30 percent resets it. |
| `charge-ready` | One notification at 80 percent or higher while charging. Unplugging or dropping below 75 percent resets it. |
| `screen-lock` | Explicit manual lock only, with owner-enabled Device Administration. Granting access never triggers it. |

All Lua source records start disabled. Policies and subscriptions are prepared so enabling a source activates its utility without editing JSON. Notification reminders need Android notification-posting permission. The utilities save their reminder state, suppress repeated alerts, and do not depend on page or widget addresses.

See [configuration](dikciz-configuration.md), [Lua scripting](scripting-tutorial.md) and [HTML widgets](user-guide.md#edit-an-html-widget) to customize them.
