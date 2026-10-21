# Connect to the phone

The default device ports are WebSocket `19001` and HTTP/MCP `19002`, bound to `127.0.0.1`. Loopback is reachable only on the phone or through forwarding. For direct access, the owner can set `listenAddress` to `0.0.0.0` in `/sdcard/Dikciz/control/ports.json`. See [listener changes](dikciz://docs/recipes/listener).

Connect to the phone's real Wi-Fi or private VPN hostname, never `0.0.0.0`. Tailscale policy must allow traffic from the controlling computer to the phone's TCP ports. The listeners have no built-in TLS. Use a trusted private network and bearer authentication.

| Interface | Endpoint | Delivery |
| --- | --- | --- |
| MCP | `http://phone.example:19002/mcp` | Streamable HTTP, JSON-RPC tools and resources. |
| HTTP | `http://phone.example:19002/v1/automation` | One JSON command per POST; shell streams use SSE. |
| WebSocket | `ws://phone.example:19001/v1/automation` | Commands and persistent event delivery. |

Send `Authorization: Bearer <password>` when enabled. Native clients omit `Origin`; untrusted browser origins are rejected. The supplied client reads `DIKCIZ_REMOTE_BEARER_TOKEN` from the environment. Keep credentials out of command arguments, logs and shared files.

MCP clients initialize with the advertised protocol version, preserve `MCP-Session-Id`, send `notifications/initialized`, and include the required per-request protocol metadata. The supplied client does this automatically. Reinitialize after a restart or APK replacement. Resource reads use the same authentication, Origin and session checks as tools.

CLI: `python3 scripts/dikciz-control.py mcp-read dikciz://docs/index --host phone.example --port 19002 --pretty`.
