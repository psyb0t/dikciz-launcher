# Offline search

Use `dikciz_search_query` to find a procedure before browsing the resource catalog. Search a short task, exact API identifier or a few keywords. No embeddings, provider key, network or model download are required.

```json
{"collection":"docs","query":"custom event lua widget","limit":5,"offset":0}
```

Results contain `items`, `total`, `hasMore`, `limit`, `offset`, `corrections` and `engine`. Each item has an exact `id`, `uri`, title, score and excerpt. Read the needed URI with MCP `resources/read`, or call `dikciz_search_read` with its collection and ID for full text. Do not page through unrelated results after finding the procedure.

Use `collection:"tools"` for current executable tool contracts. Each hit includes `tool.inputSchema`. Follow required fields, types and nested schemas. Read-only turns filter mutating tools before retrieval; a known hidden tool ID also fails a direct read. Search does not execute tools or grant Android access.

`docs` indexes the packaged guides, recipes and current public manuals by section. Generated command-reference schemas come from the installed tool catalog instead; release history and screenshots are excluded. Aliases and alternative task descriptions are authored in the repository. Ranking combines weighted BM25, term coverage and exact identifier/title boosts. Queries are plain text, with Unicode/accent normalization, camelCase/snake_case splitting, prefixes and optional bounded typo correction. This is lexical retrieval, not semantic similarity.

`dikciz_search_collections` lists protected and owner collections. `dikciz_search_index` atomically upserts, removes or clears an owner collection. Documents have unique `id`, `title`, `text` and optional `aliases`/`uri`. Nothing crawls personal files automatically. Built-in collections cannot be edited; write access is required for owner-index changes. Read current schemas for limits.

HTTP/WebSocket commands are `searchQuery`, `searchRead`, `searchCollections` and `searchIndex`. HTML and Lua call the same executor. Embedded chat has `phone_search` with string query, optional collection and decimal-string limit/offset. `phone_tools` uses the same ranked tool search. Reuse current contracts and read only the relevant sections.
