# Pages and layout

Read `configGet` or `homeGet` before editing. Each page has a stable ID and a two-axis position. Widget addresses combine page position with widget ID, for example `1H1V-example-widget`. Discover the current full address from returned records. Do not guess it from an old screenshot.

Use `selectPage` with `pageId`, `addWidget` with `widgetType`, and `widgetMove` or `widgetResize` with `widgetAddress` and logical `cell`. Grid cells use `column`, `row`, `columnSpan` and `rowSpan`, not device pixels. Read `controlStatus` for allowed types and limits. Native mutations validate page locks, bounds, overlap and placement rules before saving.

Configuration lives under `/sdcard/Dikciz/`: `config.json` references pages; `pages/<page-id>/page.json` references widget records in its `widgets/` directory. Valid file changes reload the affected consumer. Invalid changes leave the last accepted home visible and record an error.

`configReplace` and `configSeed` require the complete assembled document, not a partial patch. Preserve unrelated pages, scripts, automation and root namespaces. Read first, change only the intended object, then submit once and inspect the normalized configuration. `reset` restores the bundled home and can replace user work; use it only when explicitly authorized.

Native Delete removes the whole HTML widget. To remove one section, edit the document source as described in [document editing](dikciz://docs/recipes/html-item).
