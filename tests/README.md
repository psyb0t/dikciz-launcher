# Test layout

This directory contains live Android acceptance tests, their data, and external-app fixtures. Local Kotlin tests live separately in the [Dikciz unit test source set](../app/src/testDikciz/README.md). The [contribution guide](../CONTRIBUTING.md) explains how to choose verification.

## File map

| Path | Responsibility |
| --- | --- |
| `conftest.py` | Guard Android Lab device operations, pass their environment, report failures, and collect the log audit last. |
| [Live controls](live_control/README.md) | Pytest scenarios and helpers for real Android UI, persistence, automation, and both control planes. |
| [Fixtures](fixtures/README.md) | Version 4 configuration used as the isolated starting home. |
| [Configurable widget provider](configurable-widget-provider/README.md) | Separate Android app for AppWidget, media-session, notification-action, and accessibility tests. |

## Running tests

Run from the repository root through Make. `make dikciz-test` builds the debug APK, resets the shared emulator's Dikciz installation, and runs the live suite. To select a scenario against an already-built APK:

```bash
make dikciz-test-existing-apk DIKCIZ_TEST_SELECTOR=test_native_page_hosts_individual_html_and_provider_widgets
```

Chat scenarios use local fake OpenAI/Anthropic model-discovery and SSE endpoints with real Android SQLite, native Markdown UI and production MCP tools. The mock endpoints use a guarded, lab-only reverse mapping to Android loopback; emulator Wi-Fi is not required. No real provider key is needed. Run `make dikciz-test-existing-apk DIKCIZ_TEST_SELECTOR=test_chat_agent` after building the APK. Provider connections, model discovery, per-chat budgets, dropdown selection and settings validation have the focused selector `test_chat_provider_models`. Read-only discovery, executor enforcement and denied permission prompts use `test_chat_read_only`.

The control-listener scenarios cover Settings validation, legacy records, automatic address and port changes, atomic replacements, unchanged endpoint writes, deletion, malformed addresses, IPv6 bindings, browser Origin rejection and both protocols while Android Settings stays in front:

```bash
make dikciz-test-existing-apk DIKCIZ_TEST_SELECTOR=test_control_ports_
```

The logging scenarios cover native level selection, retention validation and boundaries, Cancel behavior, quiet app launches and target metadata through both protocols:

```bash
make dikciz-test-existing-apk DIKCIZ_TEST_SELECTOR=test_logging_
```

The file scenarios cover all three APIs, binary and private-path transfers, pagination, malformed arguments, write conflicts, bearer/Origin checks, CLI output and Android APK installation with checksum rejection, confirmation and cancellation. Build the [provider fixture](configurable-widget-provider/README.md) before running the APK case.

```bash
make dikciz-test-existing-apk DIKCIZ_TEST_SELECTOR=test_device_files_
```

Shell limit scenarios verify the 30-second default, longer per-command timeouts, client waits across all three APIs, output truncation, larger output budgets and invalid parameters:

```bash
make dikciz-test-existing-apk DIKCIZ_TEST_SELECTOR=test_shell_request_
```

Launcher regression checks cover Android Back events in button and gesture navigation modes, app tasks after Home, state retention in a standard third-party activity, Recent apps resumption and the splash version. Build both APKs, then run against minified Dikciz:

```bash
make gradle GRADLE_TASK=:app:assembleDikcizMinified
make gradle ANDROID_PROJECT=tests/configurable-widget-provider GRADLE_TASK=:app:assembleDebug
make dikciz-test-existing-apk \
  APK=app/build/outputs/apk/dikciz/minified/launcher-16-dikciz-minified.apk \
  DIKCIZ_TEST_SELECTOR=test_launcher_regression_ \
  DIKCIZ_TEST_CONFIGURABLE_PROVIDER_APK=tests/configurable-widget-provider/app/build/outputs/apk/debug/app-debug.apk \
  DIKCIZ_TEST_CONFIGURABLE_PROVIDER_PACKAGE=eu.psyb0t.dikciz.fixture
```

The Back checks verify drawer dismissal through native Back key events in both navigation modes. An edge swipe probe also fails in Android Settings on the private API 36 image, so physical edge gesture recognition needs a separate device check.

Live shell streaming cases cover output before exit, real logcat markers, process-group cancellation and disconnect cleanup, explicit deadlines, nonzero exit status, binary chunks and larger output through WebSocket, MCP SSE and HTTP SSE:

```bash
make dikciz-test-existing-apk DIKCIZ_TEST_SELECTOR=test_shell_streaming_
```

The automation public-file reload scenario also edits scripts while another Android app is in front. Direct private-network reachability still needs a connection check from the intended controlling computer; an emulator forward cannot prove a tailnet policy permits that computer to reach a physical phone.

`make dikciz-test-minified-lua` runs the existing Lua context and failure diagnostics scenarios against `dikcizMinified`, which inherits release optimization and uses the debug signing key and package. Override `DIKCIZ_TEST_SELECTOR` to run another focused scenario against that build. Debug-only platform event fixtures are not included.

The selector must match `test_` followed by letters, digits, or underscores. The runner passes it to pytest's `-k` filter, so a shared name prefix can select several tests. Use `make help` for focused targets that also build required fixture APKs.

`scripts/dikciz-lab.sh` runs pytest in Android Lab's forward service and saves the complete `dikciz-test.log` and JUnit `dikciz-test.xml` alongside screenshots and UI captures in the lab artifact directory. The root [verification overview](../README.md#verification) describes artifact access.

MCP documentation checks walk every discovery page, read every bounded section, validate errors and access gates, exercise CLI reads and run the packaged two-event Lua/HTML recipe on the shared emulator:

```bash
make dikciz-test-existing-apk DIKCIZ_TEST_SELECTOR=test_mcp_resources_
```

The full-access setup scenarios cover the separate Android task, actual permission grant and denial, return from Android Settings, app switching, Recents, rotation, process restoration and shared semantic controls through all three APIs:

```bash
make dikciz-test-existing-apk DIKCIZ_TEST_SELECTOR=test_device_access_screen_
```

Native data checks cover contacts and calendar CRUD, atomic rollback, multi-page queries, validation, shared permission recovery, system reads and Lua/HTML binding. SMS checks exercise no-SIM rejection on the lab image; real carrier callbacks need a separately authorized phone run. JVM tests cover receipt-state aggregation.

```bash
make dikciz-test-existing-apk DIKCIZ_TEST_SELECTOR=test_native_data
```

## Isolation

The normal live suite requires the shared emulator and forward-service environment. Direct host pytest does not supply the device operations. `conftest.py` accepts the separate physical controller only when it sets `TEST_REAL=1`; physical acceptance requires an explicitly authorized workflow.

Each test installs the checked-in configuration through `configReplace` and restores the bundled starter home afterward. The outer shell runner also resets the debug installation and public configuration on exit or interruption. Tests must restore Android state they change and remove fixture packages they install.

## Remote permission handoff

`make dikciz-test-existing-apk DIKCIZ_TEST_SELECTOR=test_permission_request_` covers explicit permission requests, default suppression, opt-in recovery on all three network transports, strict Boolean and requirement validation, untrusted Origin rejection, notification denial, deduplication and the owner button opening Android permission UI. Tests use only the private emulator.

## Command reference

`make dikciz-command-docs-test` validates full-schema rendering and malformed discovery inputs without a device. The CI `make test-tooling` target includes it. Capture discovery from the intended APK, then use `make dikciz-command-docs` to regenerate the reference.


## Native capability sessions

Focused selectors cover configured JPEG capture and bounded MP4 video (`test_camera_video`), actual frame metadata and validation (`test_camera_controls`), torch callbacks and real Android vibration-service playback/cancellation (`test_feedback_controls`), microphone/playback lifecycle (`test_native_audio_`), provider speech models (`test_speech_models`), requested Wi-Fi scans (`test_wifi_control`) and reconnectable shell sessions (`test_shell_sessions`).

`test_event_replay` exercises real custom/native bus events, filtered cursor pagination, reconnects, long polling, count/byte eviction, oversized-event markers, one-minute expiry, process restart, current notification access and HTML/Lua bindings. Build the provider fixture and pass its APK/package for the notification-revocation case. Replay tests adjust only the private lab's process-owned journal and restore its limits.

Peripheral selectors validate NFC tag selection and unavailable hardware, Bluetooth discovery/GATT deadlines and resource cleanup. Lab results do not prove successful writes to a physical tag, USB device or Bluetooth peer. Those require an explicitly selected device and authorized transaction.

`test_microphone_stream_` covers actual PCM byte/frame accounting, cross-transport replay, hardware ownership, explicit replay loss, pagination/capacity, duration expiry, permission/startup cleanup, background capture and the owner notification Stop action. Use the minified APK override to check release-mode behavior. Physical audio quality and carrier call audio are separate from these emulator checks.
