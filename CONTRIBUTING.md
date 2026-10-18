# Contributing to Dikciz

Start with the [Dikciz README](README.md), then read the focused
[configuration](docs/dikciz-configuration.md) and
[automation-control](docs/automation-control.md) guides for any changed public
behavior.

Keep Dikciz product code under `app/src/dikciz/` and combined source sets such
as `app/src/dikcizDebug/`. Fossify-owned source sets stay replaceable. Do not
patch upstream launcher code during normal feature work.

Run builds, tests, and device work through this repository's `Makefile`. The first `make` command that needs the pinned Android Lab images builds them with the Android Lab installer once you set `ANDROID_LAB_ACCEPT_ANDROID_LICENSES=yes` after reading the [Android SDK license](https://developer.android.com/studio/terms). CI uses the same `make build`.
Use the narrowest target that proves the changed behavior. `make dikciz-test`
is the broad shared-emulator suite, not a default after every small edit.

Any public configuration, control-plane, widget, script, theme, or automation
change must update its focused documentation in the same change. Use
`controlStatus` and `automationStatus` as the live contract for clients and
automation.
