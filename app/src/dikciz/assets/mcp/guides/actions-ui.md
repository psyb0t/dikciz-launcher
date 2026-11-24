# Typed actions: Accessibility

Read [dispatch basics](dikciz://docs/guides/actions-state). Owner-enabled Dikciz Accessibility is required. Every record has type; Lua policy action names match these types.

- accessibilityAction: snapshotId, nodeId and action; optional text. Use a node's advertised actions, including copy or paste for a focused field. Only setText takes text. Copy uses the selected text, and paste uses the current Android clipboard. Password fields are excluded. Derive nodeId from a fresh accessibilitySnapshot.
- accessibilityGesture takes snapshotId and gesture. Use tap, hold or doubleTap with x/y. Use swipe with startX/startY/endX/endY. Use twoFingerSwipe with the same fields plus secondStartX/secondStartY/secondEndX/secondEndY. Gestures are humanized by default: taps scatter around the point, swipes curve and change speed, and timings vary. For tap, hold and doubleTap, pass targetWidth/targetHeight from the node bounds so the touch lands inside the element. durationMilliseconds is optional (1 to 10000 ms; 1 to 100 ms per doubleTap press). Set humanize false for the exact straight, fixed-time gesture. The result reports humanized and the planned durationMilliseconds. Android still marks the input as injected by an accessibility service. Use fresh observed screen bounds and check the receiving UI after dispatch.
- accessibilityGlobalAction: action is back, home, recents, notifications, quickSettings, lockScreen or takeScreenshot. Android version and system support apply; global actions do not take a snapshotId.

Node/gesture snapshots expire after five seconds and window changes invalidate them. A refused snapshot is not permission to act on replacement content. Read the resulting screen after an accepted action.

Lua receives context.accessibility only with an accessibilityWindow event. Its service-lifecycle event has no window tree. HTML can request the ordinary snapshot and dispatch the same action fields. A returned Lua action uses its table parser; HTML/network dispatch uses the JSON parser; both feed the common native runtime.

displayScreenshot is the separate ordinary command returning actual image bytes through every binding. Accessibility takeScreenshot asks Android to take a screenshot; it does not replace that image-returning API. Launcher screenshot captures its own window. Secure windows and system limits still apply.

[UI](dikciz://docs/guides/ui), [Permissions](dikciz://docs/guides/permissions). Full guide: https://github.com/psyb0t/dikciz-launcher/blob/main/docs/automation-control.md#cross-app-android-ui
