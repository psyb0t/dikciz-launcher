# Upload and install an APK

1. Confirm the owner authorized the intended app installation. Choose an accessible absolute destination and create its parent directory if needed.
2. Upload the standalone APK in base64 `fileWrite` chunks of at most 256 KiB decoded. Start at offset zero; only overwrite an existing file when intended. Continue at each acknowledged `nextOffset`. The supplied CLI `upload` handles this over HTTP.
3. Call `apkInstall` with the path and optional complete-file SHA-256. If access is missing, request `installPackages` review or opt in with `autoRequestPermissions: true`. Tell the owner to review the phone, read fresh access status and retry explicitly.
4. Preserve `sessionId` from `preparing`. Poll `apkInstallStatus` through any entrypoint. `pending_user_action` requires Android confirmation. `submitted` is not final success.
5. Accept only final `succeeded` as Android installation success. Report `cancelled` or `failed` with the provided reason and status. Check the package version with `packageInfo` afterward.

Updating Dikciz itself can close listeners and invalidate sessions. Reconnect to a fresh MCP session and verify the serving package and version with `deviceInfo`. Do not uninstall the only controller to install its replacement. A force-stopped process cannot service its own recovery requests.

[Transfer contract](dikciz://docs/guides/files). [Restart and recovery](dikciz://docs/recipes/restart).
