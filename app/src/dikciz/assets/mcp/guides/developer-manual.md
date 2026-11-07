# Developer manual

Use the documentation search tools to read bounded manual sections. Search collection docs for JavaScript API, Lua API, Token permission reference or an exact ordinary command name. Read the returned URI with resources/read or search_read; library sections are packaged resources.

WebView: await dikciz.command(type, fields) unwraps result; dikciz.request({type,...fields}) returns the envelope. Wait for dikciz-ready. selfWidget supplies instance context. Page widget patchState replaces state; app-local patchState merges keys. Page-address helpers do not accept app IDs.

Headless JavaScript: onEvent(event) uses context.request(command) synchronously. Lua: on_event(event) uses context.request(commandTableOrJsonString). Neither background runtime has a WebView. Custom apps combine either backend with HTML/CSS/JS and durable native timers. Returned app actions support patchState/emitEvent; other effects use ordinary commands.

Every current command has an exact command.<type> token grant. Finite string enum selectors add narrower grants. Read remoteTokens catalog and tool _meta.dikciz.command/operationGrants rather than guessing IDs. Existing feature groups remain compatible. Explicit executable-code, shell, SDK and credential grants can delegate broad authority. Job wrappers retain caller grants and reject unauthorized nested commands.

Public references: https://github.com/psyb0t/dikciz-launcher/blob/main/docs/developer-manual.md, docs/javascript-api.md, docs/lua-api.md and docs/token-permissions.md. Installed schemas remain authoritative for this build.
