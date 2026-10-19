# Files, APK installation and shell commands

Dikciz exposes filesystem commands through WebSocket, MCP and plain HTTP. They use Android permissions granted to Dikciz and accept absolute filesystem paths, including shared storage and the app sandbox. They cover paths outside `/sdcard/Dikciz/`. Android still controls access to protected locations.

Use the [connection guide](automation-control.md#connect-without-usb) to configure a reachable listen address and bearer authentication. HTTP commands share the MCP listener and its configured port, normally `19002`. `controlStatus.transports.http` reports the endpoint in use.

## Browse and read files

```bash
python3 scripts/dikciz-control.py http fileList --host phone.example --port 19002 --arguments '{"path":"/sdcard/Download","offset":0,"limit":100}' --pretty
python3 scripts/dikciz-control.py mcp dikciz_file_list --host phone.example --port 19002 --arguments '{"path":"/sdcard/Download","offset":0,"limit":100}' --pretty
python3 scripts/dikciz-control.py websocket fileRead --host phone.example --port 19001 --arguments '{"path":"/sdcard/Dikciz/config.json","offset":0,"limit":65536}' --pretty
```

| WebSocket or HTTP command | MCP tool | Arguments |
| --- | --- | --- |
| `fileList` | `dikciz_file_list` | Required `path`; optional `offset` and `limit`. |
| `fileRead` | `dikciz_file_read` | Required `path`; optional byte `offset` and byte `limit`. |
| `fileWrite` | `dikciz_file_write` | Required `path` and `dataBase64`; optional byte `offset` and `overwrite`. |
| `fileMkdir` | `dikciz_file_mkdir` | Required `path`; optional `parents`. |
| `apkInstall` | `dikciz_apk_install` | Required APK `path`; optional complete-file `sha256`. |
| `apkInstallStatus` | `dikciz_apk_install_status` | Required `sessionId` returned by `apkInstall`. |
| `shell` | `dikciz_shell` | Required `command`; optional `root`, `timeoutMilliseconds` and `maxOutputBytes`. Use `root: false` for the app shell. |

`fileList` returns `path`, `parent`, `items`, `offset`, `limit` and `hasMore`. Entries include their name, absolute path, kind, byte size, modification time and read/write flags. An unavailable entry carries an error instead of fabricated metadata. The default limit is 100, with a maximum of 500. Continue with the previous offset plus the number of returned items. An empty page has `items: []` and `hasMore: false`.

Directory entries follow filesystem enumeration order. Changes to a directory between requests can change that order. Start a new walk when a consistent listing matters.

`fileRead` returns `dataBase64`, `bytesRead`, `offset`, `nextOffset`, `eof` and file metadata. Continue at `nextOffset` until `eof` is true. The default chunk is 64 KiB, with a 256 KiB maximum. Reading at or beyond EOF returns empty data and `eof: true`. Empty files work too. File tools return the requested bytes; log-viewer redaction does not alter file downloads.

## Upload and download binary files

The supplied client transfers files in bounded HTTP chunks and reports the byte count and SHA-256:

```bash
python3 scripts/dikciz-control.py upload ./example.apk /sdcard/Download/example.apk --host phone.example --port 19002
python3 scripts/dikciz-control.py download /sdcard/Dikciz/config.json ./phone-config.json --host phone.example --port 19002
```

An existing destination is refused by default. Pass `--overwrite` when replacement is intended. The client reads the bearer password from `DIKCIZ_REMOTE_BEARER_TOKEN`, as it does for WebSocket and MCP.

A client using MCP or WebSocket can make the same transfer with `fileWrite` and `fileRead`. Encode each upload chunk as base64 and start at offset zero. For later chunks, send the acknowledged `nextOffset`. Appending requires the offset to match the current file size, which prevents duplicate or out-of-order chunks from silently changing the file. Each upload chunk can contain at most 256 KiB of decoded data.

`fileWrite` at offset zero creates a new file. `overwrite: true` permits replacing an existing file there. An empty `dataBase64` creates an empty file. Parent directories must exist; use `fileMkdir` with `parents: true` to create missing parents. File writes never execute the uploaded content.

## Install an uploaded APK

Upload one standalone APK, then submit its absolute phone path:

```bash
python3 scripts/dikciz-control.py mcp dikciz_apk_install --host phone.example --port 19002 --arguments '{"path":"/sdcard/Download/example.apk"}' --pretty
```

The `sha256` argument is optional. Supply the checksum returned by the upload when the installer must verify the exact uploaded bytes. Android verifies the APK and its signing/update rules during installation. The maximum APK size is 512 MiB. APK bundles and split-package sets need their own packaging workflow.

If Android has not granted installation from Dikciz, the result is `permission_required` and Dikciz requests the Android unknown-source settings screen. Grant the access on the phone, return to Dikciz and retry `apkInstall` against the same uploaded path.

Revoking APK-install access can make Android restart Dikciz and invalidate existing MCP sessions. Reconnect after that permission change.

An accepted request returns `preparing` with a `sessionId`. Staging and checksum verification run in the background. Poll that ID through any transport:

```bash
python3 scripts/dikciz-control.py http apkInstallStatus --host phone.example --port 19002 --arguments '{"sessionId":123}' --pretty
```

| State | Meaning |
| --- | --- |
| `preparing` | Dikciz is staging and checking the APK. |
| `submitted` | The completed session was submitted to Android. |
| `pending_user_action` | Android requires confirmation and Dikciz requests its installer screen. |
| `succeeded` | Android reported successful installation. |
| `cancelled` | Android reported an aborted installation, including cancellation. |
| `failed` | Staging or Android installation failed. Read `reason`, `statusCode` or `message` when present. |

Uploading a file, accepting a request and displaying confirmation do not prove installation succeeded. Only the final `succeeded` state reports Android success. A staging checksum mismatch reports `failed` with reason `apk_checksum_mismatch`. Status records are private to the installer and retain up to 64 sessions.

## Execute Android CLI commands

```bash
python3 scripts/dikciz-control.py mcp dikciz_shell --host phone.example --port 19002 --arguments '{"command":"id; ls -la /sdcard/Download","root":false}' --pretty
python3 scripts/dikciz-control.py http shell --host phone.example --port 19002 --arguments '{"command":"printf example; exit 7","root":false}' --pretty
```

The command timeout defaults to 30 seconds. Each request can set `timeoutMilliseconds` from 1 to 2147483647 and `maxOutputBytes` from 1 to 1048576. The output default is 65536 bytes, shared by stdout and stderr. These are per-command options; changing one request does not change later commands. The output ceiling keeps even JSON-escaped results within the supplied clients' frame and response budgets.

```bash
python3 scripts/dikciz-control.py mcp dikciz_shell --host phone.example --port 19002 --arguments '{"command":"sleep 35; printf done","root":false,"timeoutMilliseconds":60000,"maxOutputBytes":262144}' --pretty
```

The same arguments work with WebSocket and HTTP `shell`. MCP tool schemas advertise the defaults and ranges; `controlStatus.limits` reports them too. The supplied client extends its response wait to the requested command timeout plus cleanup and transport time. Other clients must allow that time themselves.

The result includes `executionScope`, `exitCode`, `output` and `outputTruncated`. A nonzero process exit is still a completed command result. `outputTruncated: true` means the combined output exceeded the requested byte budget; Dikciz returns its initial bytes and discards the rest. A command exceeding its timeout returns error code `timeout`. Commands use `/system/bin/sh`; available programs depend on the binaries installed on Android. Root requests require a working, approved `su` and return `root_unavailable` when it is missing or denied. The existing shell behavior requests root when `root` is omitted, so pass `root: false` for normal app-permission commands.

## Plain HTTP protocol

POST a JSON command to `http://phone.example:19002/v1/automation` with `Content-Type: application/json` and the configured bearer header. The body and response use the WebSocket command envelope:

```json
{"requestId":"00000000-0000-4000-8000-000000000001","type":"fileList","path":"/sdcard/Download","offset":0,"limit":100}
```

Each request needs a UUID `requestId`. Responses preserve it and return either `type: "result"` with a result object or `type: "error"` with a finite code and message. The HTTP endpoint needs no MCP initialization or session. It dispatches the same validated command target, including shell commands and normal launcher actions.

This is a JSON command API. It uses the same bearer and browser-Origin rules as the other controls. Browser origins outside device loopback are refused; native clients can omit `Origin`. The endpoint has no built-in TLS, so use the private-network setup described in the connection guide.

Common file errors are `file_access_denied`, `file_not_found`, `not_a_file`, `not_a_directory`, `file_exists`, `file_offset_mismatch`, `invalid_base64`, `file_chunk_too_large`, `file_io_failed` and `validation_failed`. A failed command does not claim a completed write or installation. File contents, APK bytes, bearer credentials and shell output are excluded from operation logs.
