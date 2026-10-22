# Inspect and act

1. Read `dikciz_device_info` to identify the serving package and instance.
2. Read `dikciz_snapshot`. Find the intended semantic node and inspect its current state. Use discovery to locate the exact tap, text or long-press tool.
3. If another app is visible, use an Accessibility snapshot instead. If access is missing, follow [permission review](dikciz://docs/guides/permissions), then retry explicitly.
4. Act once on the current target. Preserve the returned command envelope and per-action outcome.
5. Read a fresh snapshot or display screenshot. Check the intended screen, package, value or persisted change before the next dependent action.

Use `waitFor` for a launcher node when rendering is asynchronous. A coordinate fallback needs a fresh Accessibility snapshot and visible screen coordinates. Never reuse coordinates after rotation, scrolling or window changes.

For screenshots, `dikciz_display_screenshot` returns the visible display with Accessibility access. `dikciz_screenshot` returns the launcher window. An image error is not evidence of the screen state.

If a click returns success but the screen does not change, inspect logs and the target's actual enabled/actionable state. Do not repeat a potentially destructive click blindly. For content inside an HTML widget, use [document source editing](dikciz://docs/recipes/html-item) rather than deleting the containing widget.
