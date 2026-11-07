---
title: "Offline search"
description: "Search documentation, recipes and live tool schemas offline with SQLite ranking, keyword aliases and typo correction, or index owner collections."
permalink: /search/
nav_order: 7
parent: "Remote control"
---

# Offline search

Dikciz has one persistent SQLite search service shared by HTTP, WebSocket, MCP, HTML, Lua and embedded chat. It runs on the phone without embeddings, a network connection, API keys or model downloads.

## Collections

| Collection | Content | Update behavior |
| --- | --- | --- |
| `docs` | Packaged operating guides and recipes plus sections from the public manuals | Rebuilt from the serving APK when its corpus fingerprint changes |
| `tools` | Installed tool descriptions, complete input schemas and related authored aliases | Rebuilt when installed contracts or documentation change |
| Owner name | Explicitly submitted text and aliases | Upsert, remove or clear through the shared command API |

The documentation corpus omits the generated command reference, which is represented by live tool schemas, and the changelog. Screenshot gallery markup is omitted. Large sections are split into bounded parts; a part can contain a fragment of a long code example, so fetch adjacent results or the original manual before treating it as a complete script. Short operating recipes remain complete resources.

Collections are separate search scopes. Content is not automatically copied from contacts, messages, chat history or device files. Index data lives in the app's private `local-search.db`, separately from public launcher configuration. Activity recreation and APK updates preserve custom collections; clearing app data or uninstalling removes them. Release and debug packages have separate databases.

## Search and read

HTTP and WebSocket accept an ordinary command:

```json
{"type":"searchQuery","collection":"docs","query":"custom event lua widget","limit":5,"offset":0}
```

MCP uses `dikciz_search_query` with the same arguments except `type`. HTML uses `await dikciz.command("searchQuery", arguments)`; Lua uses `context.requestJson` with the command JSON. All bindings share validation, procedure, results and errors.

The result contains `items`, `collection`, `limit`, `offset`, `total`, `hasMore`, `corrections` and `engine:"sqlite-fts4-bm25"`. Every hit has `id`, `title`, `uri`, `score` and a bounded `excerpt`. Tool hits additionally carry the exact installed `tool` descriptor and `inputSchema`. Scores order one query's results; they are not probabilities or comparable across unrelated queries.

Read full text with `searchRead` / `dikciz_search_read`:

```json
{"collection":"docs","id":"recipes/event-setup"}
```

The response includes the full `text` and `aliases`. MCP can also read an exact documentation URI returned by search through `resources/read`. The browsable resource catalog remains a short guide list; search reaches the additional manual sections without enumerating that catalog.

Queries accept 1 to 16 terms, at most 512 characters overall and 80 per term. They are plain text, not SQL or FTS expressions. Limits are 1 to 20, default 5; offsets are 0 to 10000. Empty or punctuation-only queries fail validation. An unknown query or collection returns an empty page. An unknown document ID returns `search_not_found`.

## Ranking and spelling

SQLite FTS4 retrieves matching documents. Weighted BM25 favors identifiers/titles over aliases and body text. Term coverage and exact identifier/title matches add ranking boosts. Ranking statistics come only from the selected collection and permitted documents. Unrelated collections and hidden tools cannot change its scores. Equal scores use document ID order for stable pagination.

Normalization folds case and accents, and splits camelCase and punctuation-separated identifiers. Prefixes apply to terms of at least three characters. Authored aliases cover domain terminology and alternative task descriptions. Common English filler words do not affect retrieval unless they are the entire query.

With `fuzzy:true`, the default, a missing term of at least four characters can expand to at most three nearby vocabulary terms. Edit distance is at most one, or two for words of at least eight characters. Corrections are limited to the selected collection and permitted documents, and are returned in `corrections`. Set `fuzzy:false` to disable that expansion. Prefixes are not arbitrary substring search, and spelling correction is not semantic understanding. There is no stemming or automatic translation.

## Reuse with owner data

Use a lowercase collection name, up to 64 characters, containing letters, digits, underscores, dots or hyphens. `docs` and `tools` are reserved. Names and document IDs are opaque database keys, never paths.

```json
{
  "collection":"notes",
  "action":"upsert",
  "documents":[
    {"id":"device-checklist","title":"Device checklist","text":"Inspect battery and storage before travel.","aliases":"packing preparation"}
  ]
}
```

Send this through `searchIndex` / `dikciz_search_index`. Upsert replaces each submitted ID in full and leaves other IDs unchanged. Retrying identical content is safe. A batch is atomic and accepts at most 32 unique documents. Each document requires a nonempty title up to 512 characters and nonempty text up to 32768 characters. Optional aliases allow 8192 characters; URI allows 1024. Unknown fields are rejected. Collection capacity is 2000 documents, with 10000 documents overall including built-ins.

Use `action:"remove",ids:["device-checklist"]` to delete selected IDs, or `action:"clear"` to remove the collection. Missing IDs are safe to remove repeatedly. `searchCollections` / `dikciz_search_collections` lists collection names and counts using the same limit/offset pagination. Index mutations return `state:"completed"` only after their transaction commits. Capacity failures return `search_capacity_exceeded` and roll back the batch.

## Assistant and access

Embedded chat exposes `phone_search` directly. Its required `query` and optional `collection`, `limit` and `offset` are strings. `phone_tools` also uses ranked retrieval; an empty tool query still browses schemas. A returned schema can be used directly with `phone_call`. Agents should search once for the relevant procedure, read the needed section and reuse contracts while the supplied toolCatalogueRevision fingerprint and access context remain current.

Trusted turn access filters writable tool documents before searching or reading them. Returned read-only schemas omit permission-prompt parameters. Forced index writes and permission prompts fail with `read_only_violation`; request arguments cannot relax that access. Read-only search may build its derived local cache, just as chat persists its transcript, but it does not mutate owner collections. External owner clients retain their own access. Documentation can describe write procedures without granting execution rights.

## Source and verification

The generic `search/LocalSearchIndex.kt` owns SQLite persistence, collection isolation, FTS retrieval, ranking and corrections. `DikcizSearch.kt` adapts it to the shared commands, protected datasets and trusted access. The engine has no documentation, MCP, chat or provider dependency.

Aliases are reviewed text in [`app/src/dikciz/assets/search/aliases.json`](https://github.com/psyb0t/dikciz-launcher/blob/main/app/src/dikciz/assets/search/aliases.json). `make dikciz-search-docs` deterministically generates the bundled corpus from the current Markdown. Run it after changing documentation or aliases. `make dikciz-search-docs-check` rejects stale assets; normal lint and tooling CI include that check. `make dikciz-search-docs-test` checks generation. Live Android verification uses `make dikciz-test-existing-apk DIKCIZ_TEST_SELECTOR=test_local_search`.

See [MCP resources](mcp-resources.md), [shared control](automation-control.md), [embedded chat](embedded-chat.md) and the generated [command reference](command-reference.md).
