# Restart and reconnect

1. Read `deviceInfo` and confirm the serving package is the intended release or debug installation.
2. Call `dikciz_restart` with no arguments. Preserve its `state: "scheduled"` and `instanceId` before the connection closes.
3. Reconnect to the same host and port, initialize a new MCP session and read `deviceInfo` until `application.instanceId` differs from the acknowledged value.
4. Verify the expected version, configuration and current Home state. A scheduled acknowledgement is not completed recovery.

Restart recreates the activity and listeners without killing the process or clearing saved configuration. It does not switch debug and release or alter Android's Home role. Use the separately discovered `launcherHandoff` operation for an authorized switch between installed builds and verify which package answers afterward.

APK replacement stops the old process. The package-replaced receiver restores automation and requests Home startup; default Home or overlay access permits automatic reopening when Android allows it. Manufacturer restrictions can still require tapping **Dikciz updated** or opening Dikciz manually. A socket cannot revive a force-stopped package.

Do not use the launcher's own shell to kill its only recovery path. [Discovery](dikciz://docs/guides/discovery) explains instance identity. [Connection](dikciz://docs/guides/connect) explains session recovery.
