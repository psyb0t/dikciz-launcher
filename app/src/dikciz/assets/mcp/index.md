# Dikciz task index

Read only the sections needed for the current task. Each resource is at most 4 KiB of UTF-8 text. Discovery returns eight entries per page; follow `nextCursor`. Read a section with `resources/read` and its exact `uri`. These resources ship with the running APK. Use `tools/list` for that build's executable schemas and `dikciz_control_status` for live limits.

## Guides

- [Connection](dikciz://docs/guides/connect): addresses, authentication and sessions.
- [Discovery](dikciz://docs/guides/discovery): versions and response envelopes.
- [Permissions](dikciz://docs/guides/permissions): owner review and explicit retries.
- [UI](dikciz://docs/guides/ui): semantic nodes, Accessibility and screenshots.
- [Apps](dikciz://docs/guides/apps): verified launches and recent tasks.
- [Pages](dikciz://docs/guides/pages): addresses, layout and locks.
- [HTML](dikciz://docs/guides/html): bridge, state and item controls.
- [Lua](dikciz://docs/guides/lua): scripts, policies and outcomes.
- [Events](dikciz://docs/guides/events): custom events and subscriptions.
- [Files](dikciz://docs/guides/files): binary transfer and APKs.
- [Shell](dikciz://docs/guides/shell): deadlines, privileges and streaming.
- [Logs](dikciz://docs/guides/logging): diagnosis and retention.
- [Security](dikciz://docs/guides/security): access boundaries and credentials.

- [Native data](dikciz://docs/guides/native-data): contacts, SMS, calendars, calls and system state.

- [Hardware](dikciz://docs/guides/hardware): live sensors, location, camera, microphone and speech.

## Recipes

- [Contact and tracked SMS](dikciz://docs/recipes/contact-sms).
- [Inspect and act](dikciz://docs/recipes/inspect-ui).
- [Launch an app](dikciz://docs/recipes/launch-app).
- [Edit part of an HTML document](dikciz://docs/recipes/html-item).
- [Event pipeline setup](dikciz://docs/recipes/event-setup), [Lua source](dikciz://docs/recipes/event-lua), [HTML source](dikciz://docs/recipes/event-widget).
- [Install a script](dikciz://docs/recipes/install-script).
- [Install a widget](dikciz://docs/recipes/install-widget).
- [Install an APK](dikciz://docs/recipes/install-apk).
- [Stream logcat](dikciz://docs/recipes/stream-logs).
- [Restart and reconnect](dikciz://docs/recipes/restart).
- [Change listener endpoints](dikciz://docs/recipes/listener).

An accepted request is not proof of the intended effect. Verify saved state, the current window or the operation's final status. Ask the owner to review required Android access; never claim a prompt granted it.
