package org.fossify.home.dikciz

import android.content.Context
import java.security.MessageDigest
import org.fossify.home.dikciz.search.LocalSearchIndex
import org.json.JSONArray
import org.json.JSONObject

/** Adapts the generic local index to shared command contracts and installed documentation. */
internal class DikcizSearch private constructor(context: Context, private val logger: DikcizLogger) {
    private val assets = context.applicationContext.assets
    private val index = LocalSearchIndex(context)
    private var builtInsReady = false

    @Synchronized
    fun execute(command: DikcizAutomationCommand): JSONObject {
        ensureBuiltIns()
        val request = command.request
        val collection = request.optString(KEY_COLLECTION, COLLECTION_DOCS)
        val access = command.access?.takeUnless { it.isOwner }
        access?.requireCommand(command.type, request)
        val effectiveCollection = if (access != null && collection == COLLECTION_TOOLS) {
            ensureScopedTools(access)
            DikcizRemotePolicy.SCOPED_TOOLS_COLLECTION
        } else collection
        return when (command.type) {
            TYPE_QUERY -> {
                val limit = request.optInt(KEY_LIMIT, DEFAULT_LIMIT)
                val offset = request.optInt(KEY_OFFSET, 0)
                val page = index.search(
                    effectiveCollection, request.getString(KEY_QUERY), limit, offset,
                    command.readOnly, request.optBoolean(KEY_FUZZY, true),
                )
                JSONObject().put(KEY_ITEMS, JSONArray(page.hits.map {
                    val document = documentJson(it.document, command.readOnly, false)
                    val excerpt = if (command.readOnly && document.has("tool")) {
                        document.getJSONObject("tool").getString("description").take(MAXIMUM_EXCERPT)
                    } else it.excerpt
                    document.put("score", it.score).put("excerpt", excerpt)
                })).put(KEY_LIMIT, limit).put(KEY_OFFSET, offset).put("total", page.total)
                    .put("hasMore", offset.toLong() + limit < page.total)
                    .put(KEY_COLLECTION, collection).put("corrections", JSONObject(page.corrections))
                    .put("engine", ENGINE)
            }
            TYPE_READ -> {
                val doc = index.read(effectiveCollection, request.getString(KEY_ID), command.readOnly)
                    ?: DikcizNativeCommands.failure(ERROR_NOT_FOUND, "Search document not found")
                documentJson(doc, command.readOnly, true).put(KEY_COLLECTION, collection)
            }
            TYPE_COLLECTIONS -> {
                val all = index.collections().filter {
                    it.first != DikcizRemotePolicy.SCOPED_TOOLS_COLLECTION &&
                        (access == null || "search.read" in access.permissions || it.first in BUILT_INS)
                }.map {
                    if (access != null && it.first == COLLECTION_TOOLS) {
                        it.first to DikcizRemotePolicy.filterCatalogue(DikcizMcpControlPlane.toolCatalogue(command.readOnly), access)
                            .getJSONArray("tools").length()
                    } else it
                }
                val limit = request.optInt(KEY_LIMIT, DEFAULT_LIMIT)
                val offset = request.optInt(KEY_OFFSET, 0)
                JSONObject().put(KEY_ITEMS, JSONArray(all.drop(offset).take(limit).map {
                    JSONObject().put(KEY_COLLECTION, it.first).put("documents", it.second)
                        .put("protected", it.first in BUILT_INS)
                })).put(KEY_LIMIT, limit).put(KEY_OFFSET, offset).put("total", all.size)
                    .put("hasMore", offset.toLong() + limit < all.size).put("engine", ENGINE)
            }
            TYPE_INDEX -> {
                val action = request.getString("action")
                try {
                    when (action) {
                        "upsert" -> index.upsert(collection, documents(request.getJSONArray("documents")))
                        "remove" -> index.remove(collection, strings(request.getJSONArray("ids")))
                        "clear" -> index.clear(collection)
                    }
                } catch (exception: IllegalStateException) {
                    throw DikcizAutomationRequestException("search_capacity_exceeded", "Search collection capacity exceeded; no changes saved")
                        .apply { initCause(exception) }
                }
                logger.info("search_index_changed", mapOf("action" to action))
                JSONObject().put("state", "completed").put(KEY_COLLECTION, collection)
            }
            else -> DikcizNativeCommands.invalid()
        }
    }

    private fun ensureScopedTools(access: DikcizRemoteAccess) {
        val tools = DikcizRemotePolicy.filterCatalogue(DikcizMcpControlPlane.toolCatalogue(false), access).getJSONArray("tools")
        val documents = (0 until tools.length()).map {
            val tool = tools.getJSONObject(it)
            val name = tool.getString("name")
            val original = index.read(COLLECTION_TOOLS, name, false)
            LocalSearchIndex.Document(
                name, tool.optString("title", name),
                tool.getString("description") + "\n" + tool.getJSONObject("inputSchema").toString(),
                original?.aliases.orEmpty(), "dikciz://tools/" + name, tool.toString(),
                tool.optJSONObject("annotations")?.optBoolean("readOnlyHint") == true,
            )
        }
        index.ensure(DikcizRemotePolicy.SCOPED_TOOLS_COLLECTION, digest(tools.toString()), documents)
    }

    private fun ensureBuiltIns() {
        if (builtInsReady) return
        val corpus = assets.open(CORPUS_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val docs = documents(JSONArray(corpus))
        index.ensure(COLLECTION_DOCS, digest(corpus), docs)
        val tools = DikcizMcpControlPlane.toolCatalogue(false).getJSONArray("tools")
        val toolDocs = (0 until tools.length()).map { position ->
            val tool = tools.getJSONObject(position)
            val name = tool.getString("name")
            val schema = tool.getJSONObject("inputSchema")
            val aliases = docs.filter { it.text.contains(name) || it.text.contains(tool.optString("title", name)) || it.aliases.contains(name) }
                .joinToString(" ") { it.title + " " + it.aliases }
            LocalSearchIndex.Document(
                name, tool.optString("title", name),
                tool.getString("description") + "\n" + schema.toString(),
                aliases.take(MAXIMUM_ALIASES), "dikciz://tools/" + name, tool.toString(),
                tool.optJSONObject("annotations")?.optBoolean("readOnlyHint") == true,
            )
        }
        index.ensure(COLLECTION_TOOLS, digest(tools.toString() + corpus), toolDocs)
        builtInsReady = true
        logger.info("search_index_ready", mapOf("documentation_sections" to docs.size, "tool_contracts" to toolDocs.size))
    }

    private fun documentJson(doc: LocalSearchIndex.Document, readOnly: Boolean, full: Boolean): JSONObject {
        val result = JSONObject().put(KEY_ID, doc.id).put("title", doc.title).put("uri", doc.uri)
        if (full) result.put("text", doc.text).put("aliases", doc.aliases)
        if (doc.payload != "{}") {
            val tool = JSONObject(doc.payload)
            if (readOnly) tool.getJSONObject("inputSchema").getJSONObject("properties")
                .remove(DikcizPermissionRequests.KEY_AUTO_REQUEST)
            result.put("tool", tool)
            if (full && readOnly) result.put("text", tool.getString("description"))
        }
        return result
    }

    companion object {
        const val TYPE_QUERY = "searchQuery"
        const val TYPE_READ = "searchRead"
        const val TYPE_INDEX = "searchIndex"
        const val TYPE_COLLECTIONS = "searchCollections"
        const val COLLECTION_DOCS = "docs"
        const val COLLECTION_TOOLS = "tools"
        const val CORPUS_ASSET = "search/documentation.json"
        const val KEY_COLLECTION = "collection"
        private const val KEY_QUERY = "query"
        private const val KEY_LIMIT = "limit"
        private const val KEY_OFFSET = "offset"
        private const val KEY_ID = "id"
        private const val KEY_ITEMS = "items"
        private const val KEY_FUZZY = "fuzzy"
        const val DEFAULT_LIMIT = 5
        private const val MAXIMUM_LIMIT = 20
        private const val MAXIMUM_OFFSET = 10000
        private const val MAXIMUM_EXCERPT = 900
        private const val MAXIMUM_QUERY = 512
        private const val MAXIMUM_TEXT = 32768
        private const val MAXIMUM_ALIASES = 8192
        private const val MAXIMUM_BATCH = 32
        private const val ENGINE = "sqlite-fts4-bm25"
        private const val ERROR_NOT_FOUND = "search_not_found"
        private val BUILT_INS = setOf(COLLECTION_DOCS, COLLECTION_TOOLS)
        private val ID = Regex("[A-Za-z0-9][A-Za-z0-9_.:/#-]{0,255}")
        private val COLLECTION_ID = Regex("[a-z][a-z0-9_.-]{0,63}")
        private val DOCUMENT_FIELDS = mapOf(
            KEY_ID to identifier(), "title" to text(512).put("minLength", 1).put("description", "Human-readable document title."),
            "text" to text(MAXIMUM_TEXT).put("minLength", 1).put("description", "Nonempty searchable document text."),
            "aliases" to text(MAXIMUM_ALIASES).put("description", "Optional synonyms and alternative task descriptions."),
            "uri" to text(1024).put("description", "Optional source reference, never fetched automatically."),
        )
        private val PAGE_FIELDS = mapOf(
            KEY_LIMIT to integer(1, MAXIMUM_LIMIT).put("default", DEFAULT_LIMIT),
            KEY_OFFSET to integer(0, MAXIMUM_OFFSET).put("default", 0),
        )
        val DEFINITIONS = listOf(
            DikcizNativeCommands.Definition(
                TYPE_QUERY, "dikciz_search_query",
                "Search offline collections with weighted BM25, exact identifier boosts, prefixes, authored aliases and bounded typo correction. No model or network. collection defaults to docs; tools returns current input schemas permitted by trusted turn access. Results include excerpts and IDs for searchRead. Plain text only, no SQL or FTS operators. Follow total/hasMore with limit and offset.",
                PAGE_FIELDS + mapOf(
                    KEY_COLLECTION to collection().put("default", COLLECTION_DOCS),
                    KEY_QUERY to text(MAXIMUM_QUERY).put("minLength", 1).put("description", "Plain text task, identifier or keywords, up to 16 normalized terms."),
                    KEY_FUZZY to JSONObject().put("type", "boolean").put("default", true),
                ),
                setOf(KEY_QUERY),
            ),
            DikcizNativeCommands.Definition(
                TYPE_READ, "dikciz_search_read",
                "Read the full text of one exact search result ID in a collection. docs is the default. tools returns a live contract only when permitted by trusted turn access. Never reads paths or external URLs.",
                mapOf(KEY_COLLECTION to collection().put("default", COLLECTION_DOCS), KEY_ID to identifier()),
                setOf(KEY_ID),
            ),
            DikcizNativeCommands.Definition(
                TYPE_COLLECTIONS, "dikciz_search_collections",
                "List local search collections and document counts. docs and tools are protected, rebuilt from the installed APK. Other collections contain only explicitly indexed owner content.",
                PAGE_FIELDS,
            ),
            DikcizNativeCommands.Definition(
                TYPE_INDEX, "dikciz_search_index",
                "Atomically upsert, remove or clear owner search documents. Reserved docs/tools collections cannot be changed. Upsert replaces IDs and accepts at most 32 documents with id, title, text and optional aliases/uri. Remove requires ids; clear removes one collection. Search indexes do not crawl files or private data. Maximum 2000 documents per custom collection, 10000 overall. Mutations are unavailable in read-only mode.",
                mapOf(
                    KEY_COLLECTION to collection(),
                    "action" to text(16).put("enum", JSONArray(listOf("upsert", "remove", "clear"))),
                    "documents" to JSONObject().put("type", "array").put("minItems", 1).put("maxItems", MAXIMUM_BATCH)
                        .put("items", JSONObject().put("type", "object").put("properties", JSONObject(DOCUMENT_FIELDS))
                            .put("required", JSONArray(listOf(KEY_ID, "title", "text"))).put("additionalProperties", false)),
                    "ids" to JSONObject().put("type", "array").put("minItems", 1).put("maxItems", MAXIMUM_BATCH).put("items", identifier()),
                ),
                setOf(KEY_COLLECTION, "action"), readOnly = false,
            ),
        )
        val TYPES = DEFINITIONS.map { it.type }.toSet()
        @Volatile private var instance: DikcizSearch? = null

        fun get(context: Context, logger: DikcizLogger): DikcizSearch = instance ?: synchronized(this) {
            instance ?: DikcizSearch(context.applicationContext, logger).also { instance = it }
        }

        fun validate(type: String, request: JSONObject) {
            if (request.has(KEY_COLLECTION) && !COLLECTION_ID.matches(request.getString(KEY_COLLECTION))) {
                DikcizNativeCommands.invalid("Invalid search collection")
            }
            if (type == TYPE_READ && !ID.matches(request.getString(KEY_ID))) DikcizNativeCommands.invalid("Invalid document ID")
            if (type == TYPE_QUERY) {
                val query = request.getString(KEY_QUERY)
                val tokens = LocalSearchIndex.tokens(query)
                if (query.isBlank() || tokens.isEmpty() || tokens.size > LocalSearchIndex.MAXIMUM_QUERY_TERMS ||
                    tokens.any { it.length > MAXIMUM_TOKEN }) {
                    DikcizNativeCommands.invalid("Use 1 to 16 search terms of at most 80 characters")
                }
            }
            if (type != TYPE_INDEX) return
            if (request.getString(KEY_COLLECTION) in BUILT_INS) DikcizNativeCommands.invalid("Built-in search collections are protected")
            when (request.getString("action")) {
                "upsert" -> {
                    if (!request.has("documents") || request.has("ids")) DikcizNativeCommands.invalid("upsert requires documents only")
                    val rows = request.getJSONArray("documents")
                    if (rows.length() !in 1..MAXIMUM_BATCH) DikcizNativeCommands.invalid("Invalid search document batch")
                    val seen = mutableSetOf<String>()
                    for (position in 0 until rows.length()) {
                        val doc = rows.optJSONObject(position) ?: DikcizNativeCommands.invalid("Each document must be an object")
                        DikcizNativeCommands.validateFields(doc, DOCUMENT_FIELDS, setOf(KEY_ID, "title", "text"))
                        if (!ID.matches(doc.getString(KEY_ID)) || !seen.add(doc.getString(KEY_ID)) ||
                            doc.getString("title").isBlank() || doc.getString("text").isBlank()) {
                            DikcizNativeCommands.invalid("Documents require unique IDs, nonempty titles and text")
                        }
                        if (LocalSearchIndex.tokens(doc.getString("text")).any { it.length > MAXIMUM_TOKEN }) {
                            DikcizNativeCommands.invalid("Document tokens exceed 80 characters")
                        }
                    }
                }
                "remove" -> {
                    if (!request.has("ids") || request.has("documents")) DikcizNativeCommands.invalid("remove requires ids only")
                    val ids = strings(request.getJSONArray("ids"))
                    if (ids.size !in 1..MAXIMUM_BATCH || ids.distinct().size != ids.size || ids.any { !ID.matches(it) }) {
                        DikcizNativeCommands.invalid("Invalid document IDs")
                    }
                }
                "clear" -> if (request.has("documents") || request.has("ids")) DikcizNativeCommands.invalid("clear accepts no documents or ids")
                else -> DikcizNativeCommands.invalid("Unknown search index action")
            }
        }

        private const val MAXIMUM_TOKEN = 80
        private fun collection() = text(64).put("pattern", "^" + COLLECTION_ID.pattern + "$")
            .put("description", "Lowercase collection name. docs and tools are protected built-ins; other names scope explicitly indexed owner text.")
        private fun identifier() = text(256).put("pattern", "^" + ID.pattern + "$")
            .put("description", "Exact opaque document ID. It is a database key, never a file path.")
        private fun text(maximum: Int) = JSONObject().put("type", "string").put("maxLength", maximum)
        private fun integer(minimum: Int, maximum: Int) = JSONObject().put("type", "integer").put("minimum", minimum).put("maximum", maximum)
        private fun strings(array: JSONArray): List<String> = (0 until array.length()).map {
            array.opt(it) as? String ?: DikcizNativeCommands.invalid("IDs must be strings")
        }
        private fun documents(array: JSONArray): List<LocalSearchIndex.Document> = (0 until array.length()).map {
            val doc = array.getJSONObject(it)
            LocalSearchIndex.Document(
                doc.getString(KEY_ID), doc.getString("title"), doc.getString("text"),
                doc.optString("aliases"), doc.optString("uri"),
            )
        }
        private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
