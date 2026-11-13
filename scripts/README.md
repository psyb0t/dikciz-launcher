# Repository scripts

These scripts connect the Dikciz checkout to Android Lab, exercise the control planes, build release artifacts, and maintain the Fossify source boundary. Run them through the root [Makefile](../Makefile), which supplies the tooling image, workspace, package, and device environment. Start with [Contributing](../CONTRIBUTING.md) and `make help`.

## File map

| File | Responsibility |
| --- | --- |
| `install-android-lab.sh` | Fetch the installer from the pinned Android Lab tag and build missing local images after explicit SDK license acceptance. |
| `dikciz-lab.sh` | Android Lab extension for storage access, control readiness, smoke tests, clean installs, and shared-emulator pytest. Android Lab supplies its device and Compose functions. |
| `dikciz-test-real.sh` | Controller for a separately configured physical phone, including focused acceptance, clean installation, and inspection modes. |
| `dikciz-control.py` | WebSocket commands and events, MCP discovery and tools, plain HTTP commands, chunked binary uploads/downloads, screenshots and UI dumps. |
| `test-dikciz-automation.py`, `test-dikciz-mcp.py` | Protocol probes, assertions, optional mutation checks, and helpers reused by live tests. |
| `dikciz-release-apk.sh` | Build the release APK, verify its certificate and `VERSION`, then write the APK and checksum to `dist/`. |
| `dikciz-upstream.sh` | Compare, stage, validate, back up, and replace Fossify source through checked overlays. |
| `test-upstream-refresh.sh` | Exercise refresh preservation and overlay contracts against a temporary source tree. |
| `generate-dikciz-android-sdk.py` | Verify the pinned public SDK source and generate indexed class/member assets and read classifications. See [SDK bridge](../docs/android-sdk.md). |
| `generate-dikciz-search.py` | Deterministically package Markdown sections and reviewed aliases for offline search. Make exposes generation, stale-corpus checks and generator tests. |
| `lint.sh`, `format.sh` | Check or format shell scripts and compile-check Python scripts. |

## Boundaries

The shared test runner validates the checked-in fixture path, waits for both forwarded control planes, runs pytest in the forward service, and restores a clean bundled starter home on normal exit, failure, SIGINT, or SIGTERM. The [test overview](../tests/README.md) explains its isolation rules.

Physical-phone operations use a separate controller and an explicitly selected physical acceptance workflow. They do not restore an older launcher configuration after testing.

The control client reads bearer credentials from `DIKCIZ_REMOTE_BEARER_TOKEN` by default. Keep credentials in ignored local environment files rather than command arguments. Pass `--host` and the device port to connect directly over a private network, or use the host forwarding port for ADB. Run the client on the computer permitted by the network policy, which may differ from the build host. See the [automation-control guide](../docs/automation-control.md#connect-without-usb) for connections and authentication.

The [files, APKs and shell guide](../docs/files-and-apks.md) documents `http`, `upload` and `download` client commands and the Android installer workflow. `mcp-resources` lists one page of packaged documentation; pass its `nextCursor` through `--cursor` for another. `mcp-read <uri>` reads one section, starting with `dikciz://docs/index`. The [resource guide](../docs/mcp-resources.md) owns that workflow. Shell response waits automatically cover the command's `timeoutMilliseconds` plus cleanup and transport time. Pass `timeoutMilliseconds` and `maxOutputBytes` in `--arguments` to override the command defaults. Run `python3 scripts/dikciz-control.py --help` for command-specific options.

Fossify replacement requires `DIKCIZ_UPSTREAM_REFRESH=1`, a newer release marker, clean overlay application, and a verified backup. See the [Fossify source map](../.fossify/README.md) and root [source-update instructions](../README.md#fossify-source-updates).

## Example app verification

The [Travel todo tutorial](../docs/todo-app-tutorial.md) uses the packaged app directly. Run `make build`, then `make dikciz-test-existing-apk DIKCIZ_TEST_SELECTOR=test_travel_todo` to exercise its backend, GPS arrival and screenshots on the private emulator. The source is ordinary bundled app files, without a separate install script.

## Companion fixtures

generate-dikciz-vision-fixtures.py runs through make dikciz-vision-fixtures and writes deterministic text/QR PNGs. Its optional QR source fetch verifies a pinned source hash before import. Ordinary live tests use the committed PNGs.

generate-dikciz-split-fixture.py runs through make dikciz-split-fixture after building the configurable provider. The pinned Android Lab aapt2, zipalign and apksigner create a base/config-split pair with a dedicated emulator-only test key. The generated APKs and key stay in ignored build state. They are never release artifacts.

## Reboot recovery

`make dikciz-test-reboot` builds the debug APK and uses the repository's Android Lab extension to run a real private-emulator reboot drill. `test-dikciz-reboot.py` prepares app state, a binary file and a pending timer, then verifies them after reconnection. The outer lab runner survives the forwarding container restart and restores the starter on exit. Run the target rather than calling its internal phases directly.
