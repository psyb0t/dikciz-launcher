# External Android fixture app

This standalone Gradle project builds an external Android app for the [live control tests](../live_control/README.md). It provides configurable AppWidgets, media sessions, notification actions, and an accessibility window outside the launcher process.

## Source map

Java sources live under `app/src/main/java/eu/psyb0t/dikciz/fixture/`. Layouts, strings, and AppWidget metadata live under `app/src/main/res/`; `app/src/main/AndroidManifest.xml` registers the components.

| Class | Responsibility |
| --- | --- |
| `ConfigurableFixtureWidgetProvider` | RemoteViews with primary and secondary actions, media-style buttons, and a weather refresh state. |
| `FixtureWidgetConfigurationActivity` | Cancel or save configuration, returning the original widget ID. |
| `FixtureMediaSessionActivity` | Publish a controllable media session with track, playback, and seek state. |
| `FixtureNotificationActionActivity` | Post notifications with an action PendingIntent. |
| `FixtureNotificationActionReceiver` | Record delivered actions in the fixture app's preferences. |
| `FixtureAccessibilityActivity` | Expose an external window with an action button and visible counter. |

## Build and lifecycle

The default application ID is `eu.psyb0t.dikciz.fixture`. `app/build.gradle.kts` reads `DIKCIZ_MEDIA_SESSION_FIXTURE_APPLICATION_ID` and `DIKCIZ_MEDIA_SESSION_FIXTURE_BUILD_DIRECTORY` so media tests can build a second installable package with separate output. Both packages retain the fixture namespace and component classes.

Use the root Makefile's focused targets. `make dikciz-test-provider-actions`, `make dikciz-test-provider-media`, and `make dikciz-test-provider-weather` build the fixture and supply its APK. `make dikciz-test-media-sessions` builds primary and secondary packages. `make dikciz-test-notification-action` and `make dikciz-test-accessibility-third-party` use the same app for external Android surfaces.

Shared helpers validate the expected package and APK paths, install the app for a scenario, and uninstall it afterward. Keep component names, resource IDs, action strings, and test constants synchronized. The configuration activity starts with `RESULT_CANCELED` and reports success only after saving, so tests distinguish cancellation from accepted configuration.

See the [configuration guide](../../docs/dikciz-configuration.md) for launcher-side providers and the [automation-control guide](../../docs/automation-control.md) for media, notification, and accessibility commands.

The accessibility fixture activity also has a launcher entry. Package-event tests use its real app-catalogue row and icon, then uninstall only this fixture to verify immediate HTML removal.

## Touch research probe

Build and run the installable fixture with `make dikciz-test-accessibility-third-party`. It verifies that semantic clicks increment the button counter without producing a touch trace, then checks the native events from Dikciz taps, swipes, holds, double taps and two-finger gestures. It then sends one session of exact gestures (`humanize: false`) and one of humanized gestures, scores both with [`scripts/score-dikciz-touch.py`](https://github.com/psyb0t/dikciz-launcher/blob/main/scripts/score-dikciz-touch.py), and requires the exact session to be flagged as scripted and the humanized one to pass. The focused test saves `accessibility-touch-probe.txt` and `accessibility-touch-scorer.txt`, which holds both sessions' traces and scores, in the Android Lab artifacts directory.

For manual experiments, install `tests/configurable-widget-provider/app/build/outputs/apk/debug/app-debug.apk` on a test device and open **External accessibility fixture**. Its `DikcizTouchProbe` logcat tag records one summary per completed or cancelled touch sequence. A summary records elapsed milliseconds, delivered move samples including batched history, maximum simultaneous pointer count, endpoint displacement for one pointer, span change for two pointers, and the result of Android input verification for the terminal event. A `DikcizTouchTrace` line per touch records the first finger's samples as time, x and y in screen pixels, up to 240 samples, plus the touch start time, pointer count, screen density and the action button's bounds. Pass a logcat dump of those lines to the scorer. The fixture records touches only in its own activity. It records no typed text or personal data.

Treat a verified result as confirmation that Android could verify the event. Android can verify injected input, and a missing verification result can occur in normal operation. Use known input sources as labels for research comparisons. Keep physical taps, emulator viewer input, injected gestures, and semantic clicks as separate cases.
