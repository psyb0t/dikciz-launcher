# Files and APKs

Use `fileList`, `fileRead`, `fileWrite` and `fileMkdir` through their discovered MCP tools or the same HTTP/WebSocket commands. Paths are absolute and access is limited by Android permissions granted to Dikciz. File operations can reach shared storage and the app sandbox where permitted.

`fileList` uses `offset`, `limit` and `hasMore`; default 100 entries, maximum 500. Continue by the number returned. Directory mutation can change enumeration order.

`fileRead` returns base64 bytes, `nextOffset` and `eof`. Default chunk is 64 KiB, maximum 256 KiB. Continue at the acknowledged next offset. File downloads return actual bytes, without the script-log viewer's redaction.

`fileWrite` takes `path`, `dataBase64`, optional `offset` and `overwrite`. Start at zero. Existing files are refused unless overwrite is explicitly intended. Later chunks must use the acknowledged `nextOffset`, equal to the current file size. Each decoded chunk is at most 256 KiB. Create parents first with `fileMkdir` and `parents: true`.

Do not construct shell commands from untrusted filenames to transfer bytes. The supplied CLI `upload` and `download` perform bounded HTTP transfers and report byte count and SHA-256.

`apkInstall` accepts a standalone APK path and optional complete-file `sha256`. It stages asynchronously and returns `sessionId`; poll `apkInstallStatus` until a final state. Owner install-source access and Android confirmation can be required. Upload success and `pending_user_action` are not installation success.

[APK recipe](dikciz://docs/recipes/install-apk). [Credential boundaries](dikciz://docs/guides/security).
