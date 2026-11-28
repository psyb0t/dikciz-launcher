# Pages and layout

Read `configGet` or `homeGet` before editing. Each page has a stable ID and a two-axis position. Widget addresses combine page position with widget ID, for example `1H1V-example-widget`. Discover the current full address from returned records. Do not guess it from an old screenshot.

Use `selectPage` with `pageId`, `addWidget` with `widgetType`, and `widgetMove` or `widgetResize` with `widgetAddress` and logical `cell`. Grid cells use `column`, `row`, `columnSpan` and `rowSpan`, not device pixels. Read `controlStatus` for allowed types and limits. Native mutations validate page locks, bounds, overlap and placement rules before saving.

To put a populated app group on main Home, take the ID of the `home` alias from `homeGet` as `pageId`. Read the installed app catalogue for exact component strings. For a category of apps, start with one unfiltered catalogue read. Call `addWidget` (MCP `dikciz_add_widget`) with `widgetType: "appGroup"`, `pageId`, a `components` array of 1 to 32 unique strings in display order and an optional `title`. Verify the returned `cell` and members. If the page is full or locked, ask the owner how to proceed before editing other items.

Configuration lives under `/sdcard/Dikciz/`: `config.json` references pages; `pages/<page-id>/page.json` references widget records in its `widgets/` directory. Valid file changes reload the affected consumer. Invalid changes leave the last accepted home visible and record an error.

`configReplace` and `configSeed` require the complete assembled document, not a partial patch. Preserve unrelated pages, scripts, automation and root namespaces. Read first, change only the intended object, then submit once and inspect the normalized configuration. `reset` restores the bundled home and can replace user work; use it only when explicitly authorized.

Native Delete removes the whole HTML widget. To remove one section, edit the document source as described in [document editing](dikciz://docs/recipes/html-item).
