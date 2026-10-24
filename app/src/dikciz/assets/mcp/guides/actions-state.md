# Typed actions: state and events

Actions differ from ordinary commands. HTML uses dikciz.dispatch({actions}); MCP uses dikciz_automation_dispatch; HTTP/WebSocket use automationDispatch. Network dispatch requires an existing HTML sourceWidgetAddress. Arrays contain 1..8 actions and execute in order without rollback. Inspect each outcome. Lua returns {status="...",actions={...}} and needs enabled subscription/event capability plus policy.actions for each returned action.

Every record has type. Required fields:
- selectPage: pageId.
- patchWidget: widgetAddress and values.
- patchState: values object. Replaces current script state in Lua, source widget state in HTML/network dispatch.
- patchDom: widgetAddress, selector (max512 chars) and values.
- emitEvent: event (valid custom name) and payload object; optional coalescingKey.

Policy names match these types. Saved state replacement is not a merge. Preserve prior keys when needed. JSON null is a saved value, not key deletion; Lua nil omits a key.

For HTML, JSON patchWidget values support title, html, css, javascript, state, itemStates and eventSubscriptions, at most6 fields. The parser accepts text for compatible text targets. Layout is not a patch field. eventSubscriptions replaces the full array, at most128 entries, using the saved-configuration validator. widgetGet returns the current list. Lua returned patchWidget accepts at most5 fields and no itemStates or eventSubscriptions. Use context.request with automationDispatch and an actual HTML source address for the JSON contract, or edit the saved widget record.

patchDom values: text, html, value, className and string-valued attributes (max16). DOM changes are transient. Saved source changes can recreate the WebView. Long press opens whole-widget controls; native Delete removes the entire WebView and owned files.

Enabled Lua context.request and HTML command calls have owner authority subject to Android access. The returned-action policy is not a command sandbox. Keep event handlers' journal reads nonblocking.

[Device actions](dikciz://docs/guides/actions-device), [UI actions](dikciz://docs/guides/actions-ui), [Events](dikciz://docs/guides/events). Full reference: https://github.com/psyb0t/dikciz-launcher/blob/main/docs/typed-actions.md
