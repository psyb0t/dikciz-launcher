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
| `test_device_files.py` | Filesystem pagination, binary transfers, HTTP security, CLI results and actual Android APK install/cancel outcomes. |
| `test_logging_settings.py` | Native log settings, quiet launches and target metadata. |
| `test_*.py` | Scenarios grouped by product area. `test_audit.py` runs last through the root collection hook. |

The harness imports helpers in layers: constants, device, UI, configuration, interactions, then fixtures. Many tests import the package exports. Keep domain helpers with related scenarios and avoid import cycles between these layers.

## Test contracts

The autouse fixture replaces configuration with the [checked-in home](../fixtures/README.md), reads it back, and waits for the visible fixture card before yielding. Cleanup resets to the bundled starter home, restores Android Home, and waits for its control deck. A separate WebSocket fixture restores the configuration captured when it opened.

Use semantic IDs to inspect launcher state and raw Android accessibility data or real input to prove visible controls work. Verify saved configuration when an action should persist. Protocol-only success does not establish that a visible control is reachable.

Provider tests install the [fixture app](../configurable-widget-provider/README.md) through shared helpers. Some scenarios need a focused Make target to supply its APK or a secondary media-session package. Tests that change Android settings or permissions must restore them in cleanup.

The [user-guide capture test](test_user_guide_capture.py) supplies images for the [user guide](../../docs/user-guide.md). `make dikciz-guide-capture` copies its generated captures into the documentation image directory.
