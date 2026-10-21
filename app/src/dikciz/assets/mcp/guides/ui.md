# Inspect and control UI

Use `dikciz_snapshot` for the launcher's semantic screen, including controls, IDs and bounds. `find` and `waitFor` locate launcher nodes. Use `tap`, `longPress` or `setText` with an exact current `semanticId`; inspect tool schemas before calling them. A fresh snapshot after each meaningful change prevents stale targeting.

For another Android app, use `accessibilitySnapshot` and `accessibilityAction`. They require owner-enabled **Dikciz cross-app automation**. Accessibility actions refer to the current `snapshotId` and node. Read the current schema rather than guessing node fields. Global navigation and coordinate gestures are typed `automationDispatch` actions; gestures require a current Accessibility snapshot and visible screen coordinates.

`screenshot` captures the launcher window. `displayScreenshot` captures the visible Android display, including other apps, on Android 11 or newer with Accessibility enabled. All transports preserve PNG bytes and dimensions. MCP adds an image content block. A secure window, Android throttling or a platform denial can prevent capture. Read the returned error instead of claiming an image exists.

The supplied client supports `--png-file ./screen.png`; it refuses an existing file unless `--overwrite` is passed. UI dumps and screenshots can contain private information. Fetch only what the authorized task needs.

Do not treat a successful input request as proof the destination appeared. Inspect the new window, visible package, UI nodes or saved state. Prefer semantic controls to coordinates. The [inspect recipe](dikciz://docs/recipes/inspect-ui) describes that loop. The [HTML card recipe](dikciz://docs/recipes/html-item) distinguishes a DOM item from its containing widget.
