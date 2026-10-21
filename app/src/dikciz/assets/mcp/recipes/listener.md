# Change listener endpoints

Read `/sdcard/Dikciz/control/ports.json` and `controlStatus` before changing the endpoint. Plan the next reachable address and ports first. If direct access is needed, configure and enable bearer authentication through the owner's native settings before exposing a network interface.

Save one complete port record:

```json
{"version":1,"listenAddress":"0.0.0.0","websocketPort":19001,"mcpPort":19002}
```

Use distinct valid ports and a numeric listen address. `0.0.0.0` listens on IPv4 interfaces. It is not a client destination. Use the phone's reachable hostname or IP in clients. A specific interface address must exist when binding. Deleting the record restores loopback defaults and can cut off network access.

The configuration watcher restarts listeners after a valid endpoint change, including while another app is in front. Existing sockets and MCP sessions can close. Reconnect at the new endpoint, initialize and inspect `controlStatus`. Verify the reported binding and actual connection from the intended controlling computer.

Tailscale policy must permit computer-to-phone TCP traffic on both selected ports. A tunnel ping or allowed reverse connection is insufficient. Keep phone and computer connected to the intended private network. A lab emulator port forward cannot prove the physical phone's direct network path works.

[Connection guide](dikciz://docs/guides/connect).
