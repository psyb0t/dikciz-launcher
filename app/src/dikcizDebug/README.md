# Debug event fixtures

This combined source set adds shell-driven test receivers to `dikcizDebug`. The launcher and its WebSocket, MCP and scripting interfaces live in the shared [Dikciz flavour](../dikciz/README.md), including release builds.

`AndroidManifest.xml` exports each fixture receiver with the `android.permission.DUMP` sender requirement. `res/values/strings.xml` overrides both launcher-name resources to `Dikciz (debug)` for the debug variant. Android Home selection, app info and the app drawer distinguish it from the release app named `Dikciz`.

The receiver classes in `kotlin/org/fossify/home/dikciz/` accept only actions named by `DikcizAutomationDebugContract` in the shared flavour:

| Receiver | Test operation |
| --- | --- |
| `DikcizDebugPhoneStateReceiver` | Delivers idle, off-hook or ringing state to the automation service. |
| `DikcizDebugSmsReceiver` | Enqueues a bounded synthetic incoming SMS after checking `RECEIVE_SMS`. |
| `DikcizDebugHealthConnectReceiver` | Enqueues a synthetic daily step count. |
| `DikcizDebugDeviceAdministrationReceiver` | Deactivates Dikciz Device Administration for fixture cleanup. |
| `DikcizDebugClipboardReceiver` | Sets bounded clipboard text. |

Keep fixture actions explicit and bounded. These receivers supplement the [live control tests](../../../tests/live_control/README.md); they do not replace Android's permission checks or the public [automation contract](../../../docs/automation-control.md).
