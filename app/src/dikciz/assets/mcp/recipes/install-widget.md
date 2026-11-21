# Install an HTML widget

1. Read the target page and its layout. Confirm the page is editable and has room.
2. Call `addWidget` with `widgetType: "html"` and the target `pageId`. Omit `pageId` to use the selected page. The command uses the first fitting free cells and returns the widget ID and address.
3. Use `widgetMove` and `widgetResize` with logical `cell` values if a different placement is needed. Check bounds and collision outcomes.
4. Patch the widget through a typed `patchWidget` action using its actual `widgetAddress` and `values` containing `title`, `html`, `css`, `javascript`, `state` or `itemStates`.
5. Set `eventSubscriptions` with a JSON `patchWidget` action. Read the current array through `widgetGet` and send the complete array, including subscriptions you want to keep.
6. Read the saved widget and inspect its rendered page. Use a persisted bridge acknowledgement when testing event delivery.

Do not replace the full home with a one-widget example. Read and preserve the current configuration before a complete replacement. Source edits can recreate the WebView, so wait for `dikciz-ready` in widget JavaScript. State-only patches send `dikciz-state` or `dikciz-item-state` without reloading the document. Give reusable cards unique `data-dikciz-item-id` values and use `selfWidget.itemFor(element)` for their state and files.

[HTML guide](dikciz://docs/guides/html). [Event receiver source](dikciz://docs/recipes/event-widget).
