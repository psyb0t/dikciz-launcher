# Agent integrations

The Android APK is the MCP server. Each owner supplies a reachable phone endpoint such as `http://phone.example:19002/mcp` and, when enabled, its bearer password. There is no shared hosted phone service. The plugin supplies operating instructions; the MCP connection points directly to the installed launcher. Use [private-network setup](automation-control.md#connect-without-usb) first.

## Codex

The repository skill is available from `.agents/skills/dikciz-launcher/`. The Codex plugin manifest packages those instructions. Add the phone as a separate Streamable HTTP server using the real endpoint:

```bash
codex mcp add dikciz --url http://phone.example:19002/mcp --bearer-token-env-var DIKCIZ_REMOTE_BEARER_TOKEN
```

Load the token into that environment variable from your secret store before starting Codex. Omit the bearer option only when phone authentication is disabled. This command configures the client, not the phone listener. It does not install the APK or grant Android access.

## Claude Code

The Claude plugin declares `mcp_url` and sensitive `bearer_token` user configuration. Set the URL to your phone's complete `/mcp` endpoint and the token to its bearer password. Its MCP mapping uses those values, not a maintainer endpoint. Without the plugin, configure a remote HTTP MCP entry with the same URL and `Authorization` header in your private client configuration. Do not place actual credentials in repository settings.

From a repository checkout, load the local plugin with:

```bash
claude --plugin-dir .agents
```

The manifests are packaged under `.agents/`. Marketplace publication requires the corresponding central catalog entries to be released. Do not treat an unlisted plugin as already installable by marketplace name.

## Other MCP clients

Use Streamable HTTP at `/mcp`. Modern clients use stateless discovery; legacy clients initialize and use their negotiated session. List tools and fetch `dikciz://docs/index` through resources. Follow resource pagination. Prefer focused guides and recipes over reading every section. Authentication is a static bearer credential, not OAuth. Endpoints have no built-in TLS; use an encrypted private network or a correctly configured HTTPS gateway.

## Operating contract

1. Read `deviceInfo` and `controlStatus` to identify package, version, supported commands and listeners.
2. Inspect `automationStatus` for Android access. Set `autoRequestPermissions` only when owner review is intended, then explicitly retry after access changes.
3. Read fresh snapshots and use returned identities. Prefer direct data and hardware commands to UI tapping when implemented.
4. Treat acceptance, submission, completion and actual observed effects separately. Poll SMS and APK status. Reconnect and compare launcher instance IDs after restart.
5. Read the requested outcome back. Respect unavailable hardware, secure windows, app sandboxing and Android privilege boundaries.

No plugin lifecycle hook runs a local shell or installs an Android package. Explicit MCP tool calls can act with the phone's granted permissions. File writes, shell, SMS, app installation and configuration reset require the owner's requested scope. Sensitive phone data stays between the phone and the configured client except when a requested operation sends it elsewhere, such as SMS or the selected system speech provider.

## Directory metadata

`glama.json` identifies the repository maintainer for Glama ownership. It is not proof that a listing has been submitted, reviewed or approved. A private phone address is not a public hosted MCP endpoint. Public catalog submissions must describe installation of the APK and user-supplied connection settings accurately.

The official registry descriptor uses its embedded-application format and links to installation instructions. It advertises no shared remote URL and no invented npm or Docker package. A tag-gated OIDC job publishes metadata only after APK release. Schema validity does not establish publication or directory approval; those are checked after release.
