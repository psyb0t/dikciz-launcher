# Contributing to Dikciz

Start with the [Dikciz README](README.md), then read the focused
[configuration](docs/dikciz-configuration.md) and
[automation-control](docs/automation-control.md) guides for any changed public
behavior.

Keep Dikciz product code under `app/src/dikciz/` and combined source sets such
as `app/src/dikcizDebug/`. Fossify-owned source sets stay replaceable. Do not
patch upstream launcher code during normal feature work.

Run builds, tests, and device work through this repository's `Makefile`. Build the required local images using [Android Lab's setup instructions](https://github.com/psyb0t/android-lab#build-the-images-locally). `make ci-build` is an explicitly CI-only bootstrap, not the normal local build command.
Use the narrowest target that proves the changed behavior. `make dikciz-test`
is the broad shared-emulator suite, not a default after every small edit.

Any public configuration, control-plane, widget, script, theme, or automation
change must update its focused documentation in the same change. Use
`controlStatus` and `automationStatus` as the live contract for clients and
automation.
