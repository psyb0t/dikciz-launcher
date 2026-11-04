package org.fossify.home.dikciz.search

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln

/** Persistent, collection-scoped lexical retrieval. No network or model dependency. */
internal class LocalSearchIndex(context: Context) : SQLiteOpenHelper(
    context.applicationContext, "local-search.db", null, DATABASE_VERSION,
) {
    data class Document(
        val id: String,
        val title: String,
        val text: String,
        val aliases: String = "",
        val uri: String = "",
        val payload: String = "{}",
        val readOnly: Boolean = true,
    )
    data class Hit(val document: Document, val score: Double, val excerpt: String)
    private data class Statistics(val count: Int, val averages: DoubleArray)
    data class Page(val hits: List<Hit>, val total: Int, val corrections: Map<String, List<String>>)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE documents (
            rowid INTEGER PRIMARY KEY, collection TEXT NOT NULL, identifier TEXT NOT NULL,
            title TEXT NOT NULL, body TEXT NOT NULL, aliases TEXT NOT NULL, uri TEXT NOT NULL,
            payload TEXT NOT NULL, read_only INTEGER NOT NULL,
            title_length INTEGER NOT NULL, body_length INTEGER NOT NULL, aliases_length INTEGER NOT NULL,
            UNIQUE(collection, identifier))""")
        db.execSQL("CREATE VIRTUAL TABLE search_text USING fts4(title, body, aliases, tokenize=unicode61, prefix='2,3,4')")
        db.execSQL("CREATE TABLE vocabulary (document_id INTEGER NOT NULL, term TEXT NOT NULL, PRIMARY KEY(document_id, term))")
        db.execSQL("CREATE INDEX vocabulary_term ON vocabulary(term)")
        db.execSQL("CREATE TABLE revisions (collection TEXT PRIMARY KEY, revision TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Unsupported search database migration")
    }

    @Synchronized
    fun ensure(collection: String, revision: String, documents: List<Document>) {
        val db = writableDatabase
        val current = db.rawQuery("SELECT revision FROM revisions WHERE collection=?", arrayOf(collection)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }
        if (current == revision) return
        transaction {
            removeCollection(db, collection)
            documents.forEach { put(db, collection, it) }
            db.execSQL("INSERT OR REPLACE INTO revisions(collection,revision) VALUES(?,?)", arrayOf(collection, revision))
        }
    }

    @Synchronized
    fun upsert(collection: String, documents: List<Document>) {
        transaction {
            documents.forEach { put(writableDatabase, collection, it) }
            check(count(collection) <= MAXIMUM_COLLECTION_DOCUMENTS && count() <= MAXIMUM_TOTAL_DOCUMENTS) {
                "Search index capacity exceeded"
            }
        }
    }

    @Synchronized
    fun remove(collection: String, ids: List<String>) {
        transaction { ids.forEach { removeDocument(writableDatabase, collection, it) } }
    }

    @Synchronized
    fun clear(collection: String) {
        transaction { removeCollection(writableDatabase, collection) }
    }

    @Synchronized
    fun collections(): List<Pair<String, Int>> = readableDatabase.rawQuery(
        "SELECT collection,COUNT(*) FROM documents GROUP BY collection ORDER BY collection", emptyArray(),
    ).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getInt(1)) }
    }

    // Android rawQuery binds selectionArgs as text. Scalar comparisons need explicit numeric casts.
    @Synchronized
    fun read(collection: String, id: String, readOnly: Boolean): Document? = readableDatabase.rawQuery(
        """SELECT identifier,title,body,aliases,uri,payload,read_only FROM documents
            WHERE collection=? AND identifier=? AND (CAST(? AS INTEGER)=0 OR read_only=1)""",
        arrayOf(collection, id, if (readOnly) "1" else "0"),
    ).use { if (it.moveToFirst()) document(it) else null }

    @Synchronized
    fun search(
        collection: String,
        query: String,
        limit: Int,
        offset: Int,
        readOnly: Boolean,
        fuzzy: Boolean,
    ): Page {
        val rawTokens = tokens(query).distinct()
        val terms = rawTokens.filterNot(STOP_WORDS::contains).ifEmpty { rawTokens }.take(MAXIMUM_QUERY_TERMS)
        if (terms.isEmpty()) return Page(emptyList(), 0, emptyMap())
        val corrections = if (fuzzy) corrections(collection, terms, readOnly) else emptyMap()
        val expansions = terms.flatMap { listOf(it) + corrections[it].orEmpty() }.distinct()
        // Query syntax is never accepted from callers. Only normalized alphanumeric tokens reach MATCH.
        val expression = expansions.joinToString(" OR ") { term ->
            "\"" + term + (if (term.length >= PREFIX_MINIMUM) "*" else "") + "\""
        }
        val statistics = readableDatabase.rawQuery(
            """SELECT COUNT(*),AVG(title_length),AVG(body_length),AVG(aliases_length) FROM documents
                WHERE collection=? AND (CAST(? AS INTEGER)=0 OR read_only=1)""",
            arrayOf(collection, if (readOnly) "1" else "0"),
        ).use { cursor ->
            cursor.moveToFirst()
            Statistics(cursor.getInt(0), DoubleArray(COLUMN_WEIGHTS.size) { cursor.getDouble(it + 1).coerceAtLeast(1.0) })
        }
        val candidates = readableDatabase.rawQuery(
            """SELECT d.identifier,d.title,d.body,d.aliases,d.uri,d.payload,d.read_only,
                matchinfo(search_text,'pcnalx') FROM search_text
                JOIN documents d ON d.rowid=search_text.docid
                WHERE search_text MATCH ? AND d.collection=? AND (CAST(? AS INTEGER)=0 OR d.read_only=1)""",
            arrayOf(expression, collection, if (readOnly) "1" else "0"),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val buffer = ByteBuffer.wrap(cursor.getBlob(7)).order(ByteOrder.nativeOrder()).asIntBuffer()
                    val info = IntArray(buffer.remaining())
                    buffer.get(info)
                    add(document(cursor) to info)
                }
            }
        }
        val frequencies = IntArray(expansions.size * COLUMN_WEIGHTS.size)
        candidates.forEach { (_, info) ->
            val hitsStart = 3 + info[1] * 2
            for (phrase in 0 until info[0]) {
                for (column in 0 until info[1]) {
                    val position = phrase * info[1] + column
                    if (info[hitsStart + position * MATCHINFO_HIT_FIELDS] > 0) frequencies[position] += 1
                }
            }
        }
        val hits = candidates.map { (doc, info) ->
            val score = rank(info, expansions, terms, corrections, statistics, frequencies) + exactBoost(doc, query)
            Hit(doc, score, excerpt(doc.text, expansions))
        }.sortedWith(compareByDescending<Hit> { it.score }.thenBy { it.document.id })
        return Page(hits.drop(offset).take(limit), hits.size, corrections)
    }

    private fun corrections(collection: String, terms: List<String>, readOnly: Boolean): Map<String, List<String>> {
        val access = if (readOnly) "1" else "0"
        val missing = terms.filter { term ->
            term.length >= FUZZY_MINIMUM && !readableDatabase.rawQuery(
                """SELECT 1 FROM vocabulary v JOIN documents d ON d.rowid=v.document_id
                    WHERE v.term GLOB ? AND d.collection=? AND (CAST(? AS INTEGER)=0 OR d.read_only=1) LIMIT 1""",
                arrayOf(term + "*", collection, access),
            ).use { it.moveToFirst() }
        }
        if (missing.isEmpty()) return emptyMap()
        val minimumLength = missing.minOf { it.length } - MAXIMUM_EDIT_DISTANCE
        val maximumLength = missing.maxOf { it.length } + MAXIMUM_EDIT_DISTANCE
        val words = readableDatabase.rawQuery(
            """SELECT DISTINCT v.term FROM vocabulary v JOIN documents d ON d.rowid=v.document_id
                WHERE d.collection=? AND (CAST(? AS INTEGER)=0 OR d.read_only=1) AND length(v.term) BETWEEN CAST(? AS INTEGER) AND CAST(? AS INTEGER)
                ORDER BY v.term""",
            arrayOf(collection, access, minimumLength.toString(), maximumLength.toString()),
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        return buildMap {
            for (term in missing) {
                val distance = if (term.length >= LONG_FUZZY_MINIMUM) MAXIMUM_EDIT_DISTANCE else 1
                val candidates = words.asSequence().filter { kotlin.math.abs(it.length - term.length) <= distance }
                    .map { it to editDistance(term, it, distance) }.filter { it.second <= distance }
                    .sortedWith(compareBy<Pair<String, Int>> { it.second }.thenBy { it.first })
                    .take(MAXIMUM_CORRECTIONS).map { it.first }.toList()
                if (candidates.isNotEmpty()) put(term, candidates)
            }
        }
    }

    private fun rank(
        info: IntArray,
        expansions: List<String>,
        terms: List<String>,
        corrections: Map<String, List<String>>,
        statistics: Statistics,
        frequencies: IntArray,
    ): Double {
        val phrases = info[0]
        val columns = info[1]
        val documents = statistics.count.toDouble()
        val averagesStart = 3
        val lengthsStart = averagesStart + columns
        val hitsStart = lengthsStart + columns
        var score = 0.0
        val matched = mutableSetOf<String>()
        for (phrase in 0 until phrases) {
            for (column in 0 until columns) {
                val base = hitsStart + (phrase * columns + column) * MATCHINFO_HIT_FIELDS
                val frequency = info[base].toDouble()
                if (frequency == 0.0) continue
                expansions.getOrNull(phrase)?.let(matched::add)
                val documentFrequency = frequencies[phrase * columns + column].toDouble()
                val inverseFrequency = ln(1.0 + (documents - documentFrequency + 0.5) / (documentFrequency + 0.5))
                val average = statistics.averages[column]
                val length = info[lengthsStart + column].toDouble()
                score += COLUMN_WEIGHTS[column] * inverseFrequency * frequency * (BM25_K + 1.0) /
                    (frequency + BM25_K * (1.0 - BM25_B + BM25_B * length / average))
            }
        }
        val coverage = terms.count { term -> term in matched || corrections[term].orEmpty().any(matched::contains) }
        return score + COVERAGE_BOOST * coverage / terms.size
    }

    private fun exactBoost(document: Document, query: String): Double {
        val normalized = tokens(query).joinToString(" ")
        if (normalized.isEmpty()) return 0.0
        if (tokens(document.id).joinToString(" ") == normalized) return IDENTIFIER_BOOST
        if (tokens(document.title).joinToString(" ").contains(normalized)) return TITLE_BOOST
        return 0.0
    }

    private fun excerpt(text: String, terms: List<String>): String {
        val position = terms.map { text.lowercase(Locale.ROOT).indexOf(it) }.filter { it >= 0 }.minOrNull() ?: 0
        val start = (position - EXCERPT_CONTEXT).coerceAtLeast(0)
        return (if (start > 0) "…" else "") + text.drop(start).take(EXCERPT_CHARACTERS) +
            if (start + EXCERPT_CHARACTERS < text.length) "…" else ""
    }

    private fun put(db: SQLiteDatabase, collection: String, doc: Document) {
        removeDocument(db, collection, doc.id)
        val title = tokens(doc.id + " " + doc.title)
        val body = tokens(doc.text)
        val aliases = tokens(doc.aliases)
        val row = db.insertOrThrow("documents", null, ContentValues().apply {
            put("collection", collection); put("identifier", doc.id); put("title", doc.title)
            put("body", doc.text); put("aliases", doc.aliases); put("uri", doc.uri)
            put("payload", doc.payload); put("read_only", if (doc.readOnly) 1 else 0)
            put("title_length", title.size)
            put("body_length", body.size)
            put("aliases_length", aliases.size)
        })
        db.execSQL("INSERT INTO search_text(docid,title,body,aliases) VALUES(?,?,?,?)",
            arrayOf<Any>(row, title.joinToString(" "), body.joinToString(" "), aliases.joinToString(" ")))
        (title + body + aliases).distinct().forEach { term ->
            db.execSQL("INSERT INTO vocabulary(document_id,term) VALUES(?,?)", arrayOf<Any>(row, term))
        }
    }

    private fun removeDocument(db: SQLiteDatabase, collection: String, id: String) {
        val args = arrayOf(collection, id)
        db.execSQL("DELETE FROM vocabulary WHERE document_id IN (SELECT rowid FROM documents WHERE collection=? AND identifier=?)", args)
        db.execSQL("DELETE FROM search_text WHERE docid IN (SELECT rowid FROM documents WHERE collection=? AND identifier=?)", args)
        db.delete("documents", "collection=? AND identifier=?", args)
    }

    private fun removeCollection(db: SQLiteDatabase, collection: String) {
        db.execSQL("DELETE FROM vocabulary WHERE document_id IN (SELECT rowid FROM documents WHERE collection=?)", arrayOf(collection))
        db.execSQL("DELETE FROM search_text WHERE docid IN (SELECT rowid FROM documents WHERE collection=?)", arrayOf(collection))
        db.delete("documents", "collection=?", arrayOf(collection))
        db.delete("revisions", "collection=?", arrayOf(collection))
    }

    private fun count(collection: String? = null): Int {
        val sql = if (collection == null) "SELECT COUNT(*) FROM documents" else "SELECT COUNT(*) FROM documents WHERE collection=?"
        return readableDatabase.rawQuery(sql, if (collection == null) emptyArray() else arrayOf(collection))
            .use { it.moveToFirst(); it.getInt(0) }
    }

    private fun transaction(action: () -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            action()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun document(cursor: android.database.Cursor) = Document(
        cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3),
        cursor.getString(4), cursor.getString(5), cursor.getInt(6) != 0,
    )

    companion object {
        private const val DATABASE_VERSION = 1
        const val MAXIMUM_COLLECTION_DOCUMENTS = 2000
        const val MAXIMUM_TOTAL_DOCUMENTS = 10000
        const val MAXIMUM_QUERY_TERMS = 16
        private const val MAXIMUM_EDIT_DISTANCE = 2
        private const val MAXIMUM_CORRECTIONS = 3
        private const val PREFIX_MINIMUM = 3
        private const val FUZZY_MINIMUM = 4
        private const val LONG_FUZZY_MINIMUM = 8
        private const val MATCHINFO_HIT_FIELDS = 3
        private const val BM25_K = 1.2
        private const val BM25_B = 0.75
        private const val COVERAGE_BOOST = 20.0
        private const val IDENTIFIER_BOOST = 100.0
        private const val TITLE_BOOST = 12.0
        private const val EXCERPT_CONTEXT = 100
        private const val EXCERPT_CHARACTERS = 900
        private val COLUMN_WEIGHTS = doubleArrayOf(4.0, 1.0, 2.0)
        private val CAMEL_BOUNDARY = Regex("([a-z0-9])([A-Z])")
        private val DIACRITICS = Regex("\\p{M}+")
        private val TOKEN = Regex("[\\p{L}\\p{N}]+")
        private val STOP_WORDS = setOf(
            "a", "an", "the", "to", "of", "for", "and", "or", "is", "it", "in", "on",
            "my", "me", "i", "you", "how", "can", "do", "does", "please", "show", "get",
            "what", "which", "with", "this", "that", "want", "need", "tell", "about",
        )

        fun tokens(value: String): List<String> {
            val spaced = CAMEL_BOUNDARY.replace(value, "$1 $2")
            val normalized = DIACRITICS.replace(Normalizer.normalize(spaced, Normalizer.Form.NFD), "")
                .lowercase(Locale.ROOT)
            return TOKEN.findAll(normalized).map { it.value }.toList()
        }

        private fun editDistance(left: String, right: String, maximum: Int): Int {
            var previous = IntArray(right.length + 1) { it }
            for (i in left.indices) {
                val current = IntArray(right.length + 1)
                current[0] = i + 1
                for (j in right.indices) {
                    current[j + 1] = minOf(
                        current[j] + 1, previous[j + 1] + 1,
                        previous[j] + if (left[i] == right[j]) 0 else 1,
                    )
                }
                if (current.minOrNull()!! > maximum) return maximum + 1
                previous = current
            }
            return previous[right.length]
        }
    }
}
