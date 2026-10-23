# Live control tests

These pytest scenarios exercise real Android UI and forwarded WebSocket, MCP and HTTP endpoints. They cover configuration, persistence, widget layout, providers, scripts, Android access, protocol validation, and recovery. Read the [test overview](../README.md) for entry points and the [automation-control guide](../../docs/automation-control.md) for protocol contracts.

## Harness map

| File | Responsibility |
| --- | --- |
| `constants.py` and domain `*_constants.py` | Identifiers, labels, test data, limits, and scenario-specific Lua sources. |
| `device.py` | Android Lab operations, private VNC input, bounds, and imported WebSocket/MCP smoke clients. |
| `ui.py` | Direct taps, text entry, gestures, UI dumps, and physical bounds. |
| `configuration.py` | Fixture loading, configuration inspection, and public-file mutation helpers. |
| `interactions.py` | Shared picker, provider, theme, and control-plane interactions. |
| `fixtures.py` | Per-test isolation, connection restoration, and provider installation lifecycle. |
| `__init__.py` | Shared harness exports consumed by tests. |
| Domain support modules | Accessibility, media sessions, notifications, alarms, and script-status helpers. |
| `test_widget_instance_state.py` | Per-page/wrapper/card ownership, distinct copies, file watcher updates, source-driven cleanup, validation and legacy state migration. |
| `test_html_card_presets.py` | Native intent URI validation through all transports. |
| `test_home_showcase.py` | Shared CPU/core telemetry, usage access, native dock placement, device rendering, manual demos and timed torch behavior. |
| `test_showcase_capture.py` | Explicit live-weather and populated three-page documentation capture on the private emulator. |
| `test_shared_vm_starter.py` | First-use pages, disabled Lua defaults, saved notes and external weather success/failure in the real WebView. |
| `test_ussd_control.py` | USSD validation, permission recovery and explicit missing-SIM/platform failures without claiming carrier acceptance. |
| `test_camera_video.py` | Finalized MP4/AAC through ordinary bindings, permissions, deadlines, cancellation and shared camera/microphone ownership. |
| `test_camera_controls.py` | Real configured JPEG capture, requested/actual metadata, device bounds and shared validation. |
| `test_feedback_controls.py` | Torch callbacks and waveform/predefined haptics with real vibration-service state and cancellation. |
| `test_event_replay.py` | Actual bus event replay, cursor/filter pagination, loss, retention, long polling, restart, revoked Android access and local bindings. |
| `test_shell_sessions.py` | Incremental replay, binary output, process cancellation, capacity and HTML/Lua consumers. |
| `test_native_data.py` | Native provider CRUD, pagination, rollback, validation, permission recovery, SIM/state access, SMS duplicate protection and Lua/HTML commands. |
| `test_device_files.py` | Filesystem pagination, binary transfers, HTTP security, CLI results and actual Android APK install/cancel outcomes. |
| `test_mcp_resources.py` | Resource discovery, every section read, bounded pagination, protocol errors, authentication/session gates, CLI reads and execution of the packaged event recipe. |
| `test_remote_device_control.py` | Production-client restart, instance/version inspection, external-display capture, validation and delayed port-conflict recovery. |
| `test_device_access_screen.py` | Full-access setup task, grant and denial, Settings return, Recents, rotation, process restoration and shared controls across transports. |
| `test_permission_requests.py` | Opt-in recovery across transports, explicit access requests, validation, cooldowns and owner notification/permission UI. |
| `test_activity_launch_verification.py` | Foreground evidence across transports, native routes, HTML and Lua, with disabled access, locked screens, self-finishing targets and separate Settings tasks. |
| `test_logging_settings.py` | Native log settings, quiet launches and target metadata. |
| `test_*.py` | Scenarios grouped by product area. `test_audit.py` runs last through the root collection hook. |

The harness imports helpers in layers: constants, device, UI, configuration, interactions, then fixtures. Many tests import the package exports. Keep domain helpers with related scenarios and avoid import cycles between these layers.

## Test contracts

The autouse fixture replaces configuration with the [checked-in home](../fixtures/README.md), reads it back, and waits for the visible fixture card before yielding. Cleanup resets to the bundled starter home, restores Android Home, and waits for its clock and search card. A separate WebSocket fixture restores the configuration captured when it opened.

Use semantic IDs to inspect launcher state and raw Android accessibility data or real input to prove visible controls work. Verify saved configuration when an action should persist. Protocol-only success does not establish that a visible control is reachable.

Provider tests install the [fixture app](../configurable-widget-provider/README.md) through shared helpers. Some scenarios need a focused Make target to supply its APK or a secondary media-session package. Tests that change Android settings or permissions must restore them in cleanup.

The [user-guide capture test](test_user_guide_capture.py) supplies images for the [user guide](../../docs/user-guide.md). `make dikciz-guide-capture` copies its generated captures into the documentation image directory.

## Signed release handoff fixture

`test_remote_device_handoff_to_signed_release_without_process_kill` temporarily installs the official v0.15.4 release on the private emulator. It refuses to replace an existing release installation and removes its fixture afterwards. Prepare `.testing/handoff-release/dikciz-launcher-v0.15.4.apk` from the official release, verify its published SHA-256 and the certificate fingerprint in the root README, then run the focused selector. This pinned older release proves compatibility with an already installed build. Without that optional compatibility fixture, this one scenario reports a skip. It never selects a physical phone.

## Configured starter screenshots

The optional configured capture requires outbound internet on the private emulator and an installed app store for the complete native dock. It uses live Open-Meteo weather, a chosen London location, temporary Android calendar rows and a real emulator voice recording. It checks visible controls for clipping before capturing them.

After building the debug APK, run:

```bash
DIKCIZ_GUIDE_CAPTURE=true make dikciz-test-existing-apk DIKCIZ_TEST_SELECTOR=test_user_guide_showcase_live_weather_and_all_pages
```

Images remain under `.android-lab/shared/artifacts/showcase-guide/`. Inspect them before copying them into `docs/images/user-guide/`. The regular deterministic suite leaves this live-provider capture disabled. Provider failures fail the capture rather than publishing placeholder weather.
