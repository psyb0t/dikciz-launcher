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
