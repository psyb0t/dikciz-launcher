# Remote access tokens

Create separate bearer credentials for clients that need different phone access. HTTP, WebSocket and MCP enforce the same grants in the shared executor, including direct calls to hidden tools. Android permissions remain a separate requirement.

## Set up a client

1. Open **Settings**, **Automation**, **Configure remote bearer authentication**. Set an owner password and enable authentication.
2. Open **Manage access tokens**, then **Create token**. Name the client and select its grants.
3. Copy the generated token once into the client's secret storage. Closing the copy screen discards the recoverable value. Revoke and create another token if it is lost.
4. Send `Authorization: Bearer <token>` on every HTTP/MCP request and the WebSocket upgrade. The controller reads `DIKCIZ_REMOTE_BEARER_TOKEN` from its environment. Never put credentials in URLs, prompts or command arguments.
5. Read `remoteAccessInfo` or `dikciz_remote_access_info`, then discover the permitted tools.

The existing owner password still grants full access. **Full owner access** tokens have the same authority, including creating or revoking other tokens. Keep them private. A scoped token cannot administer tokens or change authentication.

## Choose grants

Grants are additive. Select **Full owner access** alone, or combine specific grants. The native picker and `remoteTokens` action `catalog` provide the current list and descriptions.

| Need | Grants |
| --- | --- |
| Read SMS history only | `sms.read` |
| Read and send SMS | `sms.read`, `sms.send` |
| Resolve a recipient from contacts and send SMS | `contacts.read`, `sms.send` |
| Read sensor samples only | `sensors.read` |
| Capture the visible screen | `screen.capture` |
| Inspect visible UI text/components | `ui.read` |
| Inspect and act on other apps' components | `ui.control` |
| Discover and open installed apps | `apps.launch` |
| Read own SIM numbers | `sim.read` |

Other grants cover contact/calendar reads and edits, call history/control, app metadata, device readings/settings, location, notification reads/actions, media metadata, file reads, script logs, all automation events, custom search indexes, speech output, Bluetooth scans, Wi-Fi scans, USB inventory and NFC reads. Check the catalog for exact IDs.

Provider access is checked by resource and action. An SMS reader can query only `sms`, even though `contentQuery` serves several providers. Write grants do not imply provider read grants. `sms.send` includes send-status and SIM selection; it hides own phone numbers unless `sim.read` is also granted.

Some grants confer broad authority. `ui.control` can send messages or change settings through another app's UI. It blocks component actions inside Dikciz itself. `notifications.control` can send inline replies. `files.read` can read every Android-accessible file outside protected credentials. `events.read` receives the shared event feed, including other operations' payloads. Combine grants only for trusted clients.

Shell, arbitrary SDK calls, Lua/HTML installation, intents, configuration changes, durable jobs and background capture/session APIs require full owner access. These operations could execute arbitrary code or retain authority beyond a narrow grant. A token grant does not bypass Android restrictions or turn a normal shell into ADB/root.

## Administration API

All three network transports use the same procedure:

| Action | Required fields | Result |
| --- | --- | --- |
| `catalog` | `action` | Available permission IDs, titles and descriptions. |
| `list` | `action` | Token metadata, never secrets or digests. |
| `create` | `action`, `name`, `permissions` | Metadata plus `token`, returned once. |
| `update` | `action`, `id`; at least one of `name`, `permissions`, `enabled` | Updated metadata. |
| `revoke` | `action`, `id` | Remaining metadata. Revocation is permanent. |

HTTP/WebSocket use command `remoteTokens`; MCP uses `dikciz_remote_tokens`. Example arguments, sent with an existing owner credential:

```json
{"action":"create","name":"Sensor dashboard","permissions":["sensors.read"]}
```

Names contain 1 to 80 printable characters. At most 32 tokens may exist, including disabled tokens. Grants must be known, nonempty and unique. Unknown fields are rejected. Each token has a public ID, display name, grant list, enabled flag and creation timestamp in epoch milliseconds.

Updates and revocations take effect on the next authorization check without restarting listeners. In-flight side effects cannot be rolled back. Streams stop when their authorization monitor fails. Reconnect WebSocket after editing its token and initialize a fresh MCP session when changing credentials. MCP sessions are bound to the authenticated identity; another valid token cannot reuse or delete them.

Revoke every token before disabling or clearing authentication. Disabled tokens still count because opening the listeners would bypass their restrictions. Replacing the owner password preserves tokens. After the last token is revoked, the existing enable/disable and phone password-clear flows work normally.

## Discovery and errors

`tools/list`, `controlStatus` and installed-schema search in collection `tools` filter commands by grants. Mixed provider/action schemas narrow their enums. Tool search filters before ranking and pagination. Custom collection names and documents require `search.read`; index changes require `search.write`. Packaged documentation remains readable as reference.

Restricted WebSocket hello replies contain an empty, marked snapshot unless UI reads are granted. Clients without `events.read` receive their own command replies, but no shared broadcasts. Owner clients retain full snapshots and event feeds.

A valid credential calling an unauthorized operation gets `remote_permission_denied`. A missing, malformed, disabled or revoked credential gets HTTP 401 at the network boundary, or `remote_auth_required` on an established WebSocket. An out-of-scope tool document is absent and returns `search_not_found`. A denied command does not trigger Android permission prompts or execute the operation.

## Storage and local bindings

The protected `/sdcard/Dikciz/control/remote-auth.json` record retains the owner's bcrypt cost-12 verifier. Generated tokens contain 256 random secret bits; only their SHA-256 digests and metadata are stored. File APIs reject the credential record, its atomic aliases and lock file. Full shell/SDK authority is an independent privilege and can reach files available to its Android identity.

Existing password-only records remain compatible. Token records use an extended version, saved atomically under a shared record lock. Invalid records fail closed. Tokens survive activity recreation and app updates.

The built-in chat, HTML bridge and enabled Lua scripts keep their direct in-process owner bindings. They do not store a bearer token or make a loopback MCP connection. Chat's read-only mode still hides write tools and independently blocks writes in the executor. Remote token grants do not weaken that check.
