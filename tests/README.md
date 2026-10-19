# Test layout

This directory contains live Android acceptance tests, their data, and external-app fixtures. Local Kotlin tests live separately in the [Dikciz grid test source set](../app/src/testDikciz/README.md). The [contribution guide](../CONTRIBUTING.md) explains how to choose verification.

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

The selector must match `test_` followed by letters, digits, or underscores. The runner passes it to pytest's `-k` filter, so a shared name prefix can select several tests. Use `make help` for focused targets that also build required fixture APKs.

`scripts/dikciz-lab.sh` runs pytest in Android Lab's forward service and saves the complete `dikciz-test.log` and JUnit `dikciz-test.xml` alongside screenshots and UI captures in the lab artifact directory. The root [verification overview](../README.md#verification) describes artifact access.

## Isolation

The normal live suite requires the shared emulator and forward-service environment. Direct host pytest does not supply the device operations. `conftest.py` accepts the separate physical controller only when it sets `TEST_REAL=1`; physical acceptance requires an explicitly authorized workflow.

Each test installs the checked-in configuration through `configReplace` and restores the bundled starter home afterward. The outer shell runner also resets the debug installation and public configuration on exit or interruption. Tests must restore Android state they change and remove fixture packages they install.
