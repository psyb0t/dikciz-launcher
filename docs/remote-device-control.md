# Remote device operations

Connect over a private network with the [USB-free setup](automation-control.md#connect-without-usb). WebSocket, MCP and HTTP expose the same commands and Android access checks. Discover the installed build before acting, especially when release and debug packages are both installed.

## Identify the running build

```bash
python3 scripts/dikciz-control.py http deviceInfo --host phone.example --port 19002 --pretty
python3 scripts/dikciz-control.py mcp dikciz_package_info --host phone.example --port 19002 --arguments '{"packageName":"eu.psyb0t.dikciz.launcher"}' --pretty
```

`deviceInfo` returns `application`, `android` and `uptimeMilliseconds`. `application` identifies the serving package, installed version, process ID and launcher `instanceId`. The Android object reports SDK level, release, manufacturer and model. `packageInfo` reports a requested installed package, including its version, enabled/debuggable flags, target SDK and nullable launch component. It returns `package_not_found` when the package is absent or invisible. Package names are validated before PackageManager access.

Release uses `eu.psyb0t.dikciz.launcher`; debug uses `eu.psyb0t.dikciz.launcher.debug`. Updating one does not update the other. Both read the public listener settings, so running both can cause port conflicts. A listener that cannot bind retries quickly at first and then every 30 seconds while the activity remains alive. Once the conflict clears, both listeners recover without reopening the launcher.

## Restart without dropping the only recovery path

```bash
python3 scripts/dikciz-control.py websocket restart --host phone.example --port 19001 --pretty
python3 scripts/dikciz-control.py mcp dikciz_restart --host phone.example --port 19002 --pretty
python3 scripts/dikciz-control.py http restart --host phone.example --port 19002 --pretty
```

Use one of these commands. Each accepts no arguments. Lifecycle commands (`restart` and `launcherHandoff`) require WebSocket, MCP or HTTP; the HTML widget bridge returns `unsupported_transport`. The server writes `{ "state": "scheduled", "instanceId": "..." }` before recreating the launcher activity and both listeners. Saved configuration, widget state, files and the app process remain intact. The command also permits recovery from safe mode or a missing-storage screen and opens normal mode.

Reconnect using the same endpoint. Start a fresh MCP session. Read `deviceInfo` until its `application.instanceId` differs from the acknowledged value, then verify the home or configuration. A scheduled reply alone does not prove recovery. `restart` recreates the running package, so inspect its package name before requesting it. It does not switch between release and debug packages.

Do not kill the launcher through its own shell to restart it. An in-process socket cannot revive an Android force-stopped package. If Android has stopped the whole app, open it on the phone or use a separately authorized privileged controller.

## Switch between debug and release

```bash
python3 scripts/dikciz-control.py mcp dikciz_launcher_handoff --host phone.example --port 19002 --arguments '{"packageName":"eu.psyb0t.dikciz.launcher"}' --pretty
```

`launcherHandoff` accepts only the installed Dikciz release and debug package names. It requires a target version of 0.15.3 or newer and checks that the target has file access before releasing the current listeners. Missing access returns `target_access_required`; an older build returns `unsupported_launcher_version`. Neither refusal disconnects the serving build. Grant file access to each build once through Android setup. A same-package request returns `already_active` and changes nothing.

A cross-package request writes its `scheduled` reply, closes the serving listeners, stops the source automation service and launches the target without killing the source process. It probes both target ports, including a real interface for wildcard bindings. Once the ports respond it finishes the source activity. If startup fails or the target does not respond within 30 seconds, the source retries its original listener configuration and resumes its permitted automation service. Reconnect and inspect `deviceInfo` or the actual snapshot package to verify the requested build took control. The acknowledgement alone is not proof. This changes the running build, not the Android default Home role.

## See and operate other apps

Enable **Dikciz cross-app automation** in Android Accessibility settings. `accessibilitySnapshot` and `accessibilityAction` inspect and act on the current Android window. Gesture and global navigation actions are available through `automationDispatch`. Android keeps permission dialogs, protected windows and other restrictions under its control.

```bash
python3 scripts/dikciz-control.py websocket displayScreenshot --host phone.example --port 19001 --png-file ./display.png
python3 scripts/dikciz-control.py mcp dikciz_display_screenshot --host phone.example --port 19002 --png-file ./display.png
```

`displayScreenshot` captures the visible Android display, including other apps, on Android 11 or newer with enabled Accessibility access. The result contains bounded PNG data and dimensions. MCP also returns the standard image content block. Existing `screenshot` remains a capture of the launcher window. HTTP returns the PNG as `pngBase64` in its result object.

Missing Accessibility returns `accessibility_not_enabled`; older Android returns `unsupported_android_version`. Platform screenshot rejection returns `display_screenshot_failed`, including the Android status number. Android rate limits and secure-window restrictions still apply. A callback timeout returns `timeout`.

## Existing operations without a computer-side ADB connection

| Operation | Control | Android requirement |
| --- | --- | --- |
| Read version and package details | `deviceInfo`, `packageInfo` | Installed package visibility. |
| Restart the running launcher | `restart` | Reachable running listener. |
| Switch debug/release controller | `launcherHandoff` | Installed target with file access and shared listener support. |
| Browse, upload and download files | `fileList`, `fileRead`, `fileWrite`, `fileMkdir` | Read/write access to the path. |
| Install or update an APK | `apkInstall`, `apkInstallStatus` | Unknown-source access and Android confirmation. |
| Inspect logs | `scriptLogs`, file APIs | Dikciz logs; other apps and system logs have separate restrictions. |
| Launch apps and Android settings | `appAction`, `intent` | Exported destination and Android access. |
| UI inspection, input and navigation | Accessibility commands and typed actions | Owner-enabled Accessibility service. |
| Capture the visible display | `displayScreenshot` | Android 11+ and Accessibility. |
| Execute ordinary CLI commands | `shell` with `root: false` | Dikciz app permissions. |
| Execute privileged commands, force-stop other apps or request reboot modes | `shell` with `root: true` | A working, approved root provider. |

An ordinary app shell has different permissions from `adb shell`. It cannot silently grant protected permissions, read other apps or system logs, force-stop arbitrary packages, or reboot the device. Root is optional and never silently substituted after a denial. Dikciz currently has no Shizuku backend. Use native controls where available and read `automationStatus` for missing Android access. The [files, APKs and shell guide](files-and-apks.md) owns timeout, output and installer contracts. Bootloader and fastboot operation happens outside Android and cannot be served by an Android app socket.
