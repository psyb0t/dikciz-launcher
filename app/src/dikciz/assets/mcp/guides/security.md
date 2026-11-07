# Security boundaries

These controls operate with the owner's Dikciz permissions. Reachability is powerful: clients can change the home, read accessible files, run scripts and request shell commands. Bind only to intended interfaces, use a private network, restrict incoming access and enable bearer authentication before network exposure.

The password API reports status but does not return, replace or clear the password remotely. The public authentication record at `/sdcard/Dikciz/control/remote-auth.json` contains the owner bcrypt verifier and generated-token digests rather than plaintext credentials. File commands reject this record, its .bak/.new aliases and lock file and canonical path aliases; management commands also protect ancestor moves/deletes. These restrictions apply across HTTP, WebSocket, MCP, HTML and Lua file calls. An unrestricted app-UID or approved root shell can independently access files its Android identity permits, so the verifier is not ADB-only. Never retrieve or publish authentication records as routine diagnostics.

MCP documentation resources expose only an exact allowlist of packaged Markdown sections. `resources/read` is not a filesystem or URL reader. Unknown URIs, traversal paths and arbitrary schemes are rejected. Resource methods use existing bearer, Origin, session and protocol checks. Documentation itself grants no Android permission.

Permission prompts require owner review and explicit retry. A notification is not a grant. Accessibility, unknown-source installation, notification access and root remain separate Android capabilities. An app shell does not become ADB shell.

Bootloader and fastboot modes run outside Android, so an Android app socket cannot control them while its process is absent. Likewise, a force-stopped launcher cannot revive itself through its stopped socket.

[Permission flow](dikciz://docs/guides/permissions). [Shell scope](dikciz://docs/guides/shell).

## Named token grants

Read `dikciz_remote_access_info` first to learn your identity and effective grants. Tool discovery and collection `tools` search hide unauthorized commands and narrow provider/action schemas. Direct attempts still fail with `remote_permission_denied` before permission prompts or effects. Documentation is reference, not an authorization grant.

Owner credentials or matching explicit command/action grants can use `dikciz_remote_tokens`: catalog/list/create/update/revoke/rotate. Credential creation/update/rotation can delegate owner access. create takes name and a nonempty, unique permissions array and returns the generated secret once. list never returns verifiers or secrets. Examples: sms.read, sms.send, contacts.read, sensors.read, screen.capture and ui.read. Full owner access is the separate owner grant. Revocation and grant updates require no listener restart; reconnect persistent clients after editing their token. MCP sessions belong to the authenticated identity.

Every command has an explicit command.<type> grant. Finite enum selectors add narrower action/resource/setting scopes; inspect catalog IDs. Named legacy grants retain their original boundaries. Explicit shell, SDK, code, configuration and credential grants can delegate broad owner-equivalent authority. Jobs retain caller grants, validate their nested command and restrict records/idempotency to the caller. ui.control has broad authority inside other apps, including messaging; it cannot act on Dikciz components. events.read exposes shared event payloads. Without events.read, WebSocket receives only command replies; without UI read access, hello has an empty restricted snapshot. Android access is checked independently. Built-in chat stays in process and retains its independent read-only enforcement.
