# HTML widgets

An HTML widget stores `html`, `css`, `javascript`, `state`, `itemStates`, layout and optional `eventSubscriptions`. Dikciz injects the native bridge. Register bridge work on `dikciz-ready`; read `selfWidget` for the current widget's address and state.

`dikciz.request(document, options)` returns the full command envelope. `dikciz.command(type, fields, options)` returns the result or rejects with the preserved error. `dikciz.dispatch({actions})` uses typed automation actions. The same command validation and Android access checks apply as network entrypoints.

`selfWidget.patchState(values)` persists a replacement state. `dikciz-state` reports state replacement without destroying the document. Use `dikciz.patchWidget(address, values)` for another saved widget; only `title`, `html`, `css`, `javascript`, `state` and `itemStates` are patchable through that action. Subscription changes belong in the widget's saved JSON record or editor. Source changes may recreate the WebView.

`dikciz.emitEvent(name, payload)` publishes an event. `dikciz.onEvent(listener)` receives events matching this widget's configured `eventSubscriptions` while rendered. A hidden page is not a durable background worker. Put durable event handling in Lua. See [events](dikciz://docs/guides/events).

A WebView owns one complete HTML/CSS/JavaScript document and its state. Long press content, background or the top handle for the same whole-widget controls. Edit exposes all source fields. There are no native card preset objects. To change one section, edit its saved markup and rendering code while preserving siblings. Read the [document editing recipe](dikciz://docs/recipes/html-item).

Renderer budgets and document-byte limits are reported by `diagnostics.htmlWidgetResources` and configuration limits. Console warnings, bridge refusals and renderer failures appear in bounded script logs.

The HTML library copies complete templates into independent WebViews. It includes Home dashboard, clock/search, app search, notes, System dashboard and BTC and economic news. Save to library stores a complete source document without live state. The system template uses local `deviceState`; the market template contacts Coinbase and GDELT only on explicit refresh and keeps dated saved results.

State ownership is `pages/<page-id>/widgets/<webview-id>/state.json` for the wrapper and `items/<instance-id>/state.json` beneath it for each card. Use stable `data-dikciz-item-id` markers and `selfWidget.itemFor(element)` or `selfWidget.item(id)`. An item exposes `state`, `getState()`, `patchState(values)`, `storagePath`, `readFile(name)` and `writeFile(name, dataBase64)`. Document source and state edits can remove a namespace; native Delete removes the whole WebView and its files. `itemStates` patches and valid file edits deliver `dikciz-item-state` without reloading the WebView. Presets never carry live state.
