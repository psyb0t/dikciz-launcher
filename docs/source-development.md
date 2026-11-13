---
title: Source development
description: Build and test Dikciz with Docker, Make and Android Lab, understand source ownership, regenerate references and contribute verified changes.
permalink: /source-development/
nav_order: 9
has_children: true
---

# Source development

The signed APK is the normal installation path. Source development needs Docker, Make, Git and a shell. Clone [the repository](https://github.com/psyb0t/dikciz-launcher), then run Make from its root. [Android Lab](https://github.com/psyb0t/android-lab) supplies the pinned Android toolchain and private emulator.

## Build and run

Read the [Android SDK license](https://developer.android.com/studio/terms) before accepting it. Missing lab images are built by the installer pinned in the [Makefile](https://github.com/psyb0t/dikciz-launcher/blob/main/Makefile), and retained for subsequent builds.

```bash
ANDROID_LAB_ACCEPT_ANDROID_LICENSES=yes make build
make dikciz-run
```

Open `http://127.0.0.1:61326/vnc.html?autoconnect=true&resize=scale` for the private emulator. `make dikciz-run` installs the debug package, grants shared-storage access and selects it as Home. It preserves existing configuration. `make dikciz-reset` clears Dikciz and its public files on that emulator. Keep `.android-lab/` for emulator disks, Gradle cache, signing identity and artifacts.

`make help` lists every operation. `make status` inspects the stack. `make stop` stops the private lab and preserves its disk. Tool containers use up to ten CPUs; set `ANDROID_LAB_TOOL_CPU_LIMIT` and `ANDROID_LAB_GRADLE_MAX_WORKERS` for a smaller budget.

## Recovery drills

```bash
make dikciz-test-existing-apk DIKCIZ_TEST_SELECTOR=test_assistant_platform
make dikciz-test-reboot
```

Build the debug APK first when using `dikciz-test-existing-apk`. The first selector covers app backup/restore, library instructions, agent access/cancellation, updates, process interruption, disconnected Wi-Fi and idle/battery restrictions. The reboot target builds the current debug APK and verifies persisted files/state and non-replay of pending app timers across an actual Android guest reboot. Its outer Docker runner reconnects forwarding after boot; running this drill inside the forwarding container would terminate its own test process. Both targets reset the private emulator and restore its starter afterward. They never select a physical device. Logs, screenshots and the two-phase reboot record stay in the lab artifacts directory.

## Source boundaries

The [product source map](https://github.com/psyb0t/dikciz-launcher/blob/main/app/src/dikciz/README.md) describes configuration, rendering, scripts, custom apps, Android data and the shared control executor. Dikciz source stays in its additive source sets. The [Fossify integration map](https://github.com/psyb0t/dikciz-launcher/blob/main/.fossify/README.md) describes the replaceable upstream base and checked overlays.

The [script map](https://github.com/psyb0t/dikciz-launcher/blob/main/scripts/README.md) documents build tooling and the Python control client. The [test guide](https://github.com/psyb0t/dikciz-launcher/blob/main/tests/README.md) lists focused live scenarios, fixtures and physical-device boundaries. [Contributing](https://github.com/psyb0t/dikciz-launcher/blob/main/CONTRIBUTING.md) defines change and verification requirements.

## Verify a change

```bash
make lint
make test-tooling
make gradle GRADLE_TASK=:app:assembleDikcizDebug
```

Choose the focused live selector that covers a visible or protocol change. `make dikciz-test` is the broad shared-emulator suite. Physical-device work is separate and requires explicitly selected hardware. Documentation changes use [the website checks](documentation-site.md).

## Regenerate references

Capture `tools/list` and remote permission discovery from the intended installed build before generating the command and token tables. `make dikciz-command-docs` and `make dikciz-permission-docs` use those captures. `make dikciz-search-docs` packages current guides and manuals for offline phone search. The generator targets list their inputs in `make help`.

The [command reference](command-reference.md), [token reference](token-permissions.md) and packaged search corpus are generated. Edit their source or generator, then regenerate; do not patch generated output.

## Releases and upgrades

Main pushes run builds, lint and tooling tests. A version tag waits for the matching main commit to pass before producing its signed APK and checksum. `make release-apk` verifies the signing certificate and canonical [VERSION](https://github.com/psyb0t/dikciz-launcher/blob/main/VERSION). Signing material remains outside tracked files. See the [changelog](changelog.md) for migration notes and [update recovery](remote-device-control.md#recover-after-an-apk-update) for Android background-launch limits.
