# Open an app reliably

1. Identify the running build with `deviceInfo` and read the current launcher snapshot.
2. Open the app drawer through its current semantic control. Read a new snapshot, locate the intended app by label and package, and use its exact `semanticId` with the discovered `launchApp` tool. This tests the same drawer action as a physical tap.
3. Read the launch outcome. If verification needs Accessibility, request owner review with `autoRequestPermissions: true` or `requestDeviceAccess`, then retry after a confirmed grant.
4. Inspect a fresh Accessibility snapshot or display screenshot. Confirm the destination package and its visible screen. A launch attempt, scheduled intent or Home remaining visible is not verified success.
5. To test resumption, navigate Home through a supported global action, inspect the native recent-app strip and resume the reported task. Verify the destination again.

If the app is absent, check `packageInfo` and Android profile visibility. If it appears then disappears, inspect crash or activity logs available to Dikciz. Do not claim every app's crash log is available without privileges. Preserve the exact launch error and access state.

[Apps guide](dikciz://docs/guides/apps). [UI guide](dikciz://docs/guides/ui).
