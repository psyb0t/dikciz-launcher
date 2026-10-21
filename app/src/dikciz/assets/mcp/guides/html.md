# HTML widgets

An HTML widget stores `html`, `css`, `javascript`, `state`, layout and optional `eventSubscriptions`. Dikciz injects the native bridge. Register bridge work on `dikciz-ready`; read `selfWidget` for the current widget's address and state.

`dikciz.request(document, options)` returns the full command envelope. `dikciz.command(type, fields, options)` returns the result or rejects with the preserved error. `dikciz.dispatch({actions})` uses typed automation actions. The same command validation and Android access checks apply as network entrypoints.

`selfWidget.patchState(values)` persists a replacement state. `dikciz-state` reports state replacement without destroying the document. Use `dikciz.patchWidget(address, values)` for another saved widget; only `title`, `html`, `css`, `javascript` and `state` are patchable through that action. Subscription changes belong in the widget's saved JSON record or editor. Source changes may recreate the WebView.

`dikciz.emitEvent(name, payload)` publishes an event. `dikciz.onEvent(listener)` receives events matching this widget's configured `eventSubscriptions` while rendered. A hidden page is not a durable background worker. Put durable event handling in Lua. See [events](dikciz://docs/guides/events).

For native card controls, saved DOM items must be identifiable. Target the intended item with the current HTML item controls, preserving its containing widget and siblings. Script-generated content may need a source or render-function change to survive regeneration. Read the [item recipe](dikciz://docs/recipes/html-item).

Renderer budgets and document-byte limits are reported by `diagnostics.htmlWidgetResources` and configuration limits. Console warnings, bridge refusals and renderer failures appear in bounded script logs.
