# Security boundaries

These controls operate with the owner's Dikciz permissions. Reachability is powerful: clients can change the home, read accessible files, run scripts and request shell commands. Bind only to intended interfaces, use a private network, restrict incoming access and enable bearer authentication before network exposure.

The password API reports status but does not return, replace or clear the password remotely. The public authentication record at `/sdcard/Dikciz/control/remote-auth.json` contains a bcrypt verifier rather than the plaintext password. File commands reject this record, its .bak/.new aliases and canonical path aliases; management commands also protect ancestor moves/deletes. These restrictions apply across HTTP, WebSocket, MCP, HTML and Lua file calls. An unrestricted app-UID or approved root shell can independently access files its Android identity permits, so the verifier is not ADB-only. Never retrieve or publish authentication records as routine diagnostics.

MCP documentation resources expose only an exact allowlist of packaged Markdown sections. `resources/read` is not a filesystem or URL reader. Unknown URIs, traversal paths and arbitrary schemes are rejected. Resource methods use existing bearer, Origin, session and protocol checks. Documentation itself grants no Android permission.

Permission prompts require owner review and explicit retry. A notification is not a grant. Accessibility, unknown-source installation, notification access and root remain separate Android capabilities. An app shell does not become ADB shell.

Bootloader and fastboot modes run outside Android, so an Android app socket cannot control them while its process is absent. Likewise, a force-stopped launcher cannot revive itself through its stopped socket.

[Permission flow](dikciz://docs/guides/permissions). [Shell scope](dikciz://docs/guides/shell).
