# Install and connect to Dikciz

## Install an APK

Use the Dikciz APK supplied by the user. This is an APK installation, not an Android Lab setup or a source build. The host needs ADB and an Android device with USB debugging enabled. The phone owner must accept the host's debugging authorization prompt.

```bash
adb devices -l
```

Select the device whose serial the user identifies. It must show `device`, not `offline` or `unauthorized`. Use that serial on every command, even when only one device is listed.

```bash
SERIAL='REPLACE_WITH_DEVICE_SERIAL'
APK='/absolute/path/to/dikciz.apk'
adb -s "$SERIAL" install -r "$APK"
```

`-r` replaces an existing compatible installation while retaining app data. If Android rejects a signing mismatch, stop and explain it; do not uninstall the old app or clear its data. If the supplied APK is marked test-only, `install -r -t` permits that APK. Use it only after identifying `INSTALL_FAILED_TEST_ONLY` as the error.

The current `dikcizDebug` package is `eu.psyb0t.dikciz.launcher.debug`. Do not assume another APK uses the same package. Verify the supplied artifact's package and version before acting on an existing installation.

Open Dikciz on the phone. Choose it as the default Home app when Android asks, or select it in Android's default Home app settings. Follow **Grant file access** to allow it to create and watch `/sdcard/Dikciz/`. Existing pages, widgets, themes, scripts and logs live there. Grant only the additional Android access needed for the user's requested features through Dikciz's **Settings**, **Automation**, **Set up full device access** screen.

Installation is complete when ADB reports success, the selected device lists the installed package, and Dikciz opens and can load its home. Do not claim the phone's UI or Home selection was verified from the ADB install result alone.

## Forward the control ports

Dikciz listens on the Android device's loopback interfaces:

| Interface | Device listener | Host endpoint with these forwards |
| --- | --- | --- |
| WebSocket | `127.0.0.1:19001` | `ws://127.0.0.1:19101/v1/automation` |
| Streamable HTTP MCP | `127.0.0.1:19002` | `http://127.0.0.1:19102/mcp` |

Check existing forwards before choosing the host ports. Do not overwrite another device's mapping.

```bash
adb forward --list
adb -s "$SERIAL" forward --no-rebind tcp:19101 tcp:19001
adb -s "$SERIAL" forward --no-rebind tcp:19102 tcp:19002
```

If a host port is occupied, choose another unused port and use it in the client. The device ports stay `19001` and `19002`. These endpoints belong to the computer running the ADB server. Inside a separate container or on another computer, `127.0.0.1` is not automatically that host; use an explicitly configured private connection to it rather than publishing the ports publicly.

When done, remove only the mappings created for this operation, if the user does not want them left active:

```bash
adb -s "$SERIAL" forward --remove tcp:19101
adb -s "$SERIAL" forward --remove tcp:19102
```

Do not use `--remove-all` or stop the shared ADB server.

## Discover and use controls

The repository includes a dependency-free Python client at [scripts/dikciz-control.py](https://github.com/psyb0t/dikciz-launcher/blob/main/scripts/dikciz-control.py). Use that file from the existing checkout or a user-provided copy. Its path is not relative to the installed skill. These examples run from the repository root; adjust the path when using a standalone copy. No Docker image is needed to run this client.

```bash
python3 scripts/dikciz-control.py --help
python3 scripts/dikciz-control.py websocket controlStatus --port 19101 --pretty
python3 scripts/dikciz-control.py websocket automationStatus --port 19101 --pretty
python3 scripts/dikciz-control.py websocket snapshot --port 19101 --pretty
python3 scripts/dikciz-control.py mcp-tools --port 19102 --pretty
```

For a native MCP client, configure Streamable HTTP at `http://127.0.0.1:19102/mcp`, initialize the connection, then list tools. Use the returned tool schemas. The Python client handles the MCP initialization and session for its own calls; a bare POST without the handshake is not a connection check.

Select the bundled Home page through WebSocket:

```bash
python3 scripts/dikciz-control.py websocket selectPage \
  --arguments '{"pageId":"1H1V"}' --port 19101 --pretty
python3 scripts/dikciz-control.py websocket snapshot --port 19101 --pretty
```

Use an actual page ID from the current home, not the bundled example, when the user has changed the configuration. Inspect script logs through MCP:

```bash
python3 scripts/dikciz-control.py mcp dikciz_script_logs --port 19102 --pretty
```

Watch a bounded event stream:

```bash
python3 scripts/dikciz-control.py events --port 19101 --count 10 --idle-timeout-seconds 30
```

`--arguments` accepts a JSON object or `@path/to/file.json`. Use `--host` and `--port` for the actual private endpoint. Read [automation control](https://github.com/psyb0t/dikciz-launcher/blob/main/docs/automation-control.md) for errors, supported actions and limits.

## Authentication and permission failures

The phone owner can configure bearer authentication in **Settings**, **Automation**, **Configure remote bearer authentication**. When enabled, both protocols require `Authorization: Bearer <password>`. The client reads `DIKCIZ_REMOTE_BEARER_TOKEN` from the environment. Obtain it from the user's secret store or ignored local environment, not from the public configuration. The phone stores a bcrypt verifier, not a recoverable plaintext password.

An authorization failure does not authorize disabling authentication. A denied Android capability does not authorize silently granting all permissions. Explain the missing access and use the specific Android settings handoff. For `root_unavailable`, report that the requested privileged operation did not run.
