# Apps and running tasks

Use app catalogue discovery or open the app drawer and inspect its semantic nodes. `launchApp` accepts an app node's `semanticId`. App actions and explicit intents have their own schemas. Confirm package identity with `packageInfo` when names are ambiguous.

App launch results distinguish an attempted launch from verified foreground state. Check the returned outcome and a fresh Accessibility snapshot or display screenshot. A backgrounded Home screen does not prove a launch worked. Apps can reject intents, be disabled, crash immediately, or be unavailable in the current Android profile.

The native recent-app strip tracks resumable tasks. Pressing Home should leave a task available to resume, but Android task history, app flags and manufacturer restrictions still determine which tasks exist. Resume the reported task rather than inventing a component. Inspect current controls and the resulting foreground app.

An ordinary Dikciz process cannot silently force-stop every app or inspect another app's private data. Optional root shell execution has a separate access boundary. A reachable socket is not evidence that Accessibility, overlay or usage access is enabled.

[Launch recipe](dikciz://docs/recipes/launch-app). [Permission review](dikciz://docs/guides/permissions).

Use `appIcon` or `dikciz_app_icon` with a launchable `component` from `appCatalogue`. Optional `sizePixels` defaults to 64 and accepts 16 through 128. The shared result includes PNG base64 in `pngBase64`, width, height and MIME type. HTML can use `dikciz.command("appIcon", {component})` and a `data:image/png;base64,` image URL. Cache only the icons needed by the visible list. Android supplies icons from installed package resources.
