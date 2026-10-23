# Typed actions: Accessibility

Read [dispatch basics](dikciz://docs/guides/actions-state). Owner-enabled Dikciz Accessibility is required. Every record has type; Lua policy action names match these types.

- accessibilityAction: snapshotId, nodeId and action; optional text. Supported actions: click, longClick, focus, clearFocus, scrollForward, scrollBackward, setText and select. setText requires text. Derive nodeId from a fresh accessibilitySnapshot.
- accessibilityGesture: snapshotId, gesture and durationMilliseconds. tap requires x/y; swipe requires startX/startY/endX/endY. Coordinates are integer0..10000; duration integer1..10000ms. Use observed node bounds, not guessed positions.
- accessibilityGlobalAction: action is back, home, recents, notifications, quickSettings, lockScreen or takeScreenshot. Android version and system support apply; global actions do not take a snapshotId.

Node/gesture snapshots expire after five seconds and window changes invalidate them. A refused snapshot is not permission to act on replacement content. Read the resulting screen after an accepted action.

Lua receives context.accessibility only with an accessibilityWindow event. Its service-lifecycle event has no window tree. HTML can request the ordinary snapshot and dispatch the same action fields. A returned Lua action uses its table parser; HTML/network dispatch uses the JSON parser; both feed the common native runtime.

displayScreenshot is the separate ordinary command returning actual image bytes through every binding. Accessibility takeScreenshot asks Android to take a screenshot; it does not replace that image-returning API. Launcher screenshot captures its own window. Secure windows and system limits still apply.

[UI](dikciz://docs/guides/ui), [Permissions](dikciz://docs/guides/permissions). Full guide: https://github.com/psyb0t/dikciz-launcher/blob/main/docs/automation-control.md#cross-app-android-ui
