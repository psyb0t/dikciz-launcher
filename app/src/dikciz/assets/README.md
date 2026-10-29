# Bundled home assets

These assets provide the starter home and authoring libraries packaged with the [Dikciz flavour](../README.md). Runtime copies belong to the editable public tree described in the [configuration guide](../../../../docs/dikciz-configuration.md). The [scripting tutorial](../../../../docs/scripting-tutorial.md) explains the HTML and Lua APIs.

| Path | Runtime role |
| --- | --- |
| `config.json` | Complete starter document that `HomeConfigStore` expands into root, page, widget, script and automation files. |
| `web/page-api.js` | Native bridge API injected into HTML widget documents. |
| `chat-model-limits.json` | Exact-ID token limits with source links for chat discovery and automatic model settings. Provider metadata takes precedence. |
| `mcp/` | Small documentation resources served from an exact catalog, beginning with [the task index](mcp/index.md). These remain packaged assets rather than editable runtime copies. |
| `widget-library/` | Complete packages that the picker adds as new HTML widgets with their own native grid placement. |
| `themes/` | Bundled theme JSON discovered and installed by `DikcizThemeStore`. |
| `fonts/` | Packaged typefaces loaded by `DikcizStyleRenderer`. |
| `licenses/` | Licenses for the packaged fonts. |

Each bundled widget package contains `widget.json`, `index.html`, `style.css` and `script.js`. Manifest IDs must match directory names. `DikcizHtmlWidgetLibrary` validates public copies and skips unreadable entries when listing them. Its bundled ID list explicitly selects which complete document templates to install.

Bundled packages install only when their public target directory is absent. Keep installed user edits intact. The starter configuration has a separate digest and pristine-state check: a new starter replaces a saved one only while that saved home remains untouched. Font selection and theme validation use the same codecs and stores as native edits; the configuration guide owns their schemas and limits.

The starter has four pages. Home combines time/weather, most-used app icons and native dock shortcuts. Tools pairs agenda and voice notes. Device displays live system metrics. Goblin provides tap-operated speech, finite torch pulses, synthesized alien sounds and outdoor weather checks. Each of the six HTML documents is also a complete reusable package with independent state and files.

The library contains eighteen templates, including optional combined dashboards, app search, notes, sensors and market/news documents. Six Lua examples start disabled: low-battery and charge-ready notifications, manual screen lock, London arrival speech, outdoor heat commentary and GPS speed warnings. The [complete bundled catalog](../../../../docs/starter-home.md#complete-bundled-widget-catalog) describes every template; the [Lua utility guide](../../../../docs/starter-home.md#optional-lua-utilities) gives script triggers, permissions and cooldowns.
