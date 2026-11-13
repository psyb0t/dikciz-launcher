package org.fossify.home.dikciz

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Bounded causal metadata. Event payloads remain in the permission-checked journal. */
internal object DikcizAutomationDebugger {
    const val TYPE_HISTORY = "automationHistory"
    const val TYPE_EVENT = "automationHistoryEvent"
    const val TYPE_SIMULATE = "automationSimulate"
    private const val MAXIMUM_RECORDS = 20000
    private const val RETENTION_DAYS = 7
    private const val RETENTION_MILLISECONDS = RETENTION_DAYS * 86400000L
    private const val MAXIMUM_NAME = 160
    private const val MAXIMUM_COMMANDS = 100
    private const val MAXIMUM_OUTPUT_BYTES = 262144
    @Volatile private var store: Store? = null
    private var nextPruneAt = 0L
    private var recordsSincePrune = 0
    private const val PRUNE_RECORD_INTERVAL = 128
    private const val PRUNE_INTERVAL_MILLISECONDS = 60000L
    private val current = ThreadLocal<Span?>()

    data class Span(val id: String, val traceId: String, val previous: Span?, var outcome: String = "completed", var code: String? = null)

    @Synchronized fun attach(context: Context) {
        if (store != null) return
        store = Store(context.applicationContext)
        database().execSQL("UPDATE history SET outcome = 'interrupted', code = 'process_restarted' WHERE outcome = 'running'")
    }

    fun parent(): String? = current.get()?.id
    fun capture(): Span? = current.get()?.let { Span(it.id, it.traceId, null) }
    fun <T> withParent(parent: Span?, action: () -> T): T {
        val previous = current.get()
        current.set(parent)
        try { return action() } finally { current.set(previous) }
    }

    fun begin(kind: String, name: String, revision: String? = null, event: JSONObject? = null, trigger: String? = null): Span? {
        if (store == null) return null
        val previous = current.get()
        val id = UUID.randomUUID().toString()
        val span = Span(id, previous?.traceId ?: id, previous)
        synchronized(this) {
            prune()
            database().insertOrThrow("history", null, ContentValues().apply {
                put("id", id); put("trace_id", span.traceId); put("parent_id", previous?.id)
                put("kind", kind); put("name", name.take(MAXIMUM_NAME)); put("revision", revision); put("trigger", trigger?.take(MAXIMUM_NAME))
                put("started", System.currentTimeMillis()); put("finished", 0); put("outcome", "running")
                put("journal_id", event?.optString("journalId")); put("sequence", event?.optLong("sequence"))
            })
        }
        current.set(span)
        return span
    }

    fun mark(outcome: String, code: String? = null) {
        current.get()?.let { it.outcome = outcome; it.code = code?.take(MAXIMUM_NAME) }
    }

    fun finish(span: Span?, outcome: String? = null, code: String? = null) {
        if (span == null) return
        try {
            synchronized(this) {
                database().update("history", ContentValues().apply {
                    put("finished", System.currentTimeMillis()); put("outcome", outcome ?: span.outcome)
                    put("code", (code ?: span.code)?.take(MAXIMUM_NAME))
                }, "id = ?", arrayOf(span.id))
            }
        } finally { current.set(span.previous) }
    }

    fun <T> dispatch(reference: JSONObject, name: String, action: () -> T): T {
        val span = begin("event", name, event = reference)
        try { return action() }
        catch (exception: Exception) { mark("failed", exception.javaClass.simpleName); throw exception }
        finally { finish(span) }
    }

    suspend fun turn(parentId: String?, name: String, action: suspend () -> String) {
        val parent = parentId?.let { id ->
            synchronized(this) {
                database().rawQuery("SELECT trace_id FROM history WHERE id = ?", arrayOf(id)).use {
                    if (it.moveToFirst()) Span(id, it.getString(0), null) else null
                }
            }
        }
        val span = withParent(parent) { begin("chat", name) }
        // The coroutine element must hold the new span across suspension, not its parent.
        withContext(current.asContextElement(span)) {
            try { mark(action()) }
            catch (exception: Exception) { mark("interrupted", exception.javaClass.simpleName); throw exception }
            finally { finish(span) }
        }
    }

    fun revision(source: String): String = MessageDigest.getInstance("SHA-256")
        .digest(source.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun execute(context: Context, type: String, request: JSONObject): JSONObject {
        attach(context)
        if (type == TYPE_HISTORY) return history(request)
        if (type == TYPE_EVENT) return retainedEvent(context, request.getString("recordId"))
        return simulate(context, request)
    }

    @Synchronized private fun history(request: JSONObject): JSONObject {
        prune(force = true)
        val filters = mutableListOf<String>()
        val args = mutableListOf<String>()
        for ((key, column) in mapOf("kind" to "kind", "name" to "name", "outcome" to "outcome", "traceId" to "trace_id", "recordId" to "id")) {
            if (request.has(key)) { filters.add("$column = ?"); args.add(request.getString(key)) }
        }
        val where = if (filters.isEmpty()) "" else " WHERE " + filters.joinToString(" AND ")
        val limit = request.optInt("limit", DikcizNativeCommands.DEFAULT_LIMIT)
        val offset = request.optInt("offset", 0)
        val db = database()
        db.beginTransactionNonExclusive()
        try {
            val total = db.rawQuery("SELECT COUNT(*) FROM history INDEXED BY history_filter$where", args.toTypedArray()).use { it.moveToFirst(); it.getInt(0) }
            val rows = JSONArray()
            db.rawQuery("SELECT id, trace_id, parent_id, kind, name, revision, started, finished, outcome, code, journal_id, sequence, trigger FROM history$where ORDER BY started DESC, rowid DESC LIMIT ? OFFSET ?",
                (args + listOf(limit.toString(), offset.toString())).toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    val row = JSONObject()
                    listOf("id", "traceId", "parentId", "kind", "name", "sourceRevision").forEachIndexed { index, key -> row.put(key, cursor.getString(index) ?: JSONObject.NULL) }
                    row.put("startedAt", cursor.getLong(6)).put("finishedAt", cursor.getLong(7)).put("outcome", cursor.getString(8))
                        .put("code", cursor.getString(9) ?: JSONObject.NULL).put("journalId", cursor.getString(10) ?: JSONObject.NULL)
                        .put("sequence", if (cursor.isNull(11)) JSONObject.NULL else cursor.getLong(11))
                    row.put("trigger", cursor.getString(12) ?: JSONObject.NULL)
                    rows.put(row)
                }
            }
            db.setTransactionSuccessful()
            return JSONObject().put("items", rows).put("total", total).put("limit", limit).put("offset", offset)
                .put("hasMore", offset.toLong() + rows.length() < total).put("retentionDays", RETENTION_DAYS).put("maximumRecords", MAXIMUM_RECORDS)
                .put("payloadRetention", "Event payloads have separate journal time, count and byte limits. Old payloads may be expired.")
                .put("scope", "Dispatched events, script/app handlers and shared commands since this feature was installed. No request arguments or results.")
        } finally { db.endTransaction() }
    }

    private fun retainedEvent(context: Context, id: String): JSONObject {
        val row = history(JSONObject().put("recordId", id)).getJSONArray("items").optJSONObject(0)
            ?: DikcizNativeCommands.failure("automation_history_missing", "This history record has expired or does not exist")
        if (row.getString("kind") != "event") DikcizNativeCommands.invalid("Choose an event record")
        return DikcizEventJournal.replay(context, row.getString("journalId"), row.getLong("sequence"))
    }

    private fun simulate(context: Context, request: JSONObject): JSONObject {
        val configuration = HomeConfigStore(context).loadOrCreate()
        if (listOf("scriptId", "appId", "source").count(request::has) != 1) DikcizNativeCommands.invalid("Supply exactly one of scriptId, appId or source")
        val script = when {
            request.has("scriptId") -> configuration.scripts.firstOrNull { it.id == request.getString("scriptId") }
                ?: DikcizNativeCommands.failure("native_record_not_found", "Script does not exist")
            request.has("appId") -> DikcizCustomAppStore(context).read(request.getString("appId")).let {
                DikcizLuaScript(it.id, it.title, true, it.source, 1, it.state, it.language)
            }
            else -> DikcizLuaScript("simulation", "Simulation", true, request.getString("source"), language =
                if (request.optString("language", "javascript") == "lua") DikcizScriptLanguage.Lua else DikcizScriptLanguage.JavaScript)
        }.copy(enabled = true)
        if (request.has("recordId") && request.has("event")) DikcizNativeCommands.invalid("Supply a recorded event or a sample, not both")
        if (!request.has("recordId") && !request.has("event")) DikcizNativeCommands.invalid("Supply recordId or event")
        val document = if (request.has("recordId")) retainedEvent(context, request.getString("recordId")) else request.getJSONObject("event")
        if (document.keys().asSequence().any { it !in setOf("type", "source", "timestampMilliseconds", "coalescingKey", "payload") } ||
            document.opt("type") !is String || document.has("payload") && document.opt("payload") !is JSONObject ||
            document.has("source") && document.opt("source") !is String || document.has("coalescingKey") && document.opt("coalescingKey") !is String ||
            document.has("timestampMilliseconds") && document.opt("timestampMilliseconds") !is Number) DikcizNativeCommands.invalid("Malformed event sample")
        val name = document.getString("type")
        if (name.isBlank() || name.length > MAXIMUM_NAME) DikcizNativeCommands.invalid("Invalid event type")
        val eventType = DikcizAutomationEventType.entries.firstOrNull { it.persistedValue == name } ?: DikcizAutomationEventType.Custom
        val event = DikcizAutomationEvent(eventType, document.optString("source", "simulation"),
            document.optLong("timestampMilliseconds", System.currentTimeMillis()), document.optString("coalescingKey", "simulation"),
            document.optJSONObject("payload") ?: JSONObject(), customEventName = name.takeIf { eventType == DikcizAutomationEventType.Custom })
        val commands = JSONArray()
        val fixtures = request.optJSONArray("replies") ?: JSONArray()
        for (index in 0 until fixtures.length()) {
            val fixture = fixtures.optJSONObject(index) ?: DikcizNativeCommands.invalid("Each fixture must be an object")
            if (fixture.opt("type") !is String || fixture.opt("result") !is JSONObject || fixture.keys().asSequence().any { it !in setOf("type", "result") }) DikcizNativeCommands.invalid("Fixture requires only type and result")
        }
        var outputBytes = 0
        val lua = DikcizLuaRuntime()
        val execute = if (script.language == DikcizScriptLanguage.JavaScript) DikcizJavaScriptRuntime(lua::decodeJsonEventResult)::executeEvent else lua::executeEvent
        val result = execute(script, event, configuration.limits, configuration.scriptWidgetContext(), null, DikcizAutomationStatus.androidAccessDocument(context)) { raw ->
            if (commands.length() >= MAXIMUM_COMMANDS || outputBytes + raw.toByteArray(Charsets.UTF_8).size > MAXIMUM_OUTPUT_BYTES) {
                DikcizNativeCommands.failure("automation_simulation_limit", "Simulation command output limit reached")
            }
            outputBytes += raw.toByteArray(Charsets.UTF_8).size
            val command = JSONObject(raw)
            commands.put(command)
            val fixture = fixtures.optJSONObject(commands.length() - 1)
            if (fixture != null && fixture.getString("type") != command.optString("type")) DikcizNativeCommands.invalid("Fixture command type does not match the intercepted command")
            JSONObject().put("type", "result").put("result", fixture?.getJSONObject("result") ?: JSONObject().put("state", "simulated"))
                .put("requestId", command.opt("requestId") ?: JSONObject.NULL).toString()
        }
        val response = JSONObject().put("simulated", true).put("androidAccessSource", "current_android_grants").put("commands", commands).put("sourceRevision", revision(script.source))
            .put("message", "No phone commands, state writes, schedules or output events were executed. Uses current source and a state snapshot; subscription policies are not replayed.")
        when (result) {
            is DikcizLuaAutomationResult.Failure -> response.put("outcome", "failed").put("code", result.reason.persistedValue)
            is DikcizLuaAutomationResult.Actions -> {
                response.put("outcome", "completed").put("status", result.status ?: JSONObject.NULL).put("result", result.result ?: JSONObject.NULL)
                    .put("actions", JSONArray(result.actions.map(DikcizAutomationActionDocument::type)))
                    .put("actionDetails", JSONArray(result.actions.map(::actionDocument)))
            }
            DikcizLuaAutomationResult.NoHandler -> response.put("outcome", "no_handler")
            DikcizLuaAutomationResult.Disabled -> response.put("outcome", "disabled")
        }
        if (response.toString().toByteArray(Charsets.UTF_8).size > MAXIMUM_OUTPUT_BYTES) DikcizNativeCommands.failure("automation_simulation_limit", "Simulation result exceeds its byte limit")
        return response
    }

    private fun actionDocument(action: DikcizLuaAutomationAction): JSONObject {
        val value = JSONObject().put("type", DikcizAutomationActionDocument.type(action))
        when (action) {
            is DikcizLuaAutomationAction.PatchState -> value.put("values", action.values)
            is DikcizLuaAutomationAction.PatchWidget -> value.put("widgetAddress", action.widgetAddress).put("values", action.values)
            is DikcizLuaAutomationAction.PatchDom -> value.put("widgetAddress", action.widgetAddress).put("selector", action.selector).put("values", action.values)
            is DikcizLuaAutomationAction.SelectPage -> value.put("pageId", action.pageID)
            is DikcizLuaAutomationAction.LaunchApp -> value.put("component", action.component)
            is DikcizLuaAutomationAction.AppAction -> value.put("component", action.component).put("action", action.action.persistedValue)
            is DikcizLuaAutomationAction.PostNotification -> value.put("title", action.title).put("text", action.text)
            is DikcizLuaAutomationAction.SendSms -> value.put("recipient", action.recipient).put("body", action.body)
            is DikcizLuaAutomationAction.ExplicitIntent -> value.put("intentType", action.intentType.persistedValue).put("component", action.component).put("action", action.action ?: JSONObject.NULL)
            is DikcizLuaAutomationAction.EmitEvent -> value.put("event", action.eventName).put("payload", action.payload).put("coalescingKey", action.coalescingKey ?: JSONObject.NULL)
            is DikcizLuaAutomationAction.MediaControl -> value.put("packageName", action.packageName).put("command", action.command.persistedValue).put("positionMilliseconds", action.positionMilliseconds ?: JSONObject.NULL)
            is DikcizLuaAutomationAction.SetMediaVolume -> value.put("levelPercent", action.levelPercent)
            is DikcizLuaAutomationAction.NotificationControl -> value.put("actionToken", "[REDACTED]")
            is DikcizLuaAutomationAction.AccessibilityAction -> value.put("snapshotId", action.snapshotID).put("nodeId", action.nodeID).put("action", action.action).put("text", action.text ?: JSONObject.NULL)
            is DikcizLuaAutomationAction.AccessibilityGlobalAction -> value.put("action", action.action.persistedValue)
            is DikcizLuaAutomationAction.AccessibilityGesture -> action.gesture.let {
                value.put("snapshotId", it.snapshotID).put("gesture", it.kind.persistedValue).put("durationMilliseconds", it.durationMilliseconds)
                if (it.kind == DikcizAccessibilityGestureKind.Tap) value.put("x", it.startX).put("y", it.startY)
                else value.put("startX", it.startX).put("startY", it.startY).put("endX", it.endX).put("endY", it.endY)
            }
            DikcizLuaAutomationAction.LockDevice -> Unit
        }
        return value
    }

    private fun prune(force: Boolean = false) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now < nextPruneAt && ++recordsSincePrune < PRUNE_RECORD_INTERVAL) return
        recordsSincePrune = 0
        nextPruneAt = now + PRUNE_INTERVAL_MILLISECONDS
        database().delete("history", "started < ? AND outcome != 'running'", arrayOf((System.currentTimeMillis() - RETENTION_MILLISECONDS).toString()))
        database().execSQL("DELETE FROM history WHERE rowid IN (SELECT rowid FROM history WHERE outcome != 'running' ORDER BY started DESC, rowid DESC LIMIT -1 OFFSET ?)", arrayOf(MAXIMUM_RECORDS))
    }

    private fun database() = requireNotNull(store).writableDatabase
    private class Store(context: Context) : SQLiteOpenHelper(context, "dikciz-automation-history.db", null, 2) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE history(id TEXT PRIMARY KEY, trace_id TEXT NOT NULL, parent_id TEXT, kind TEXT NOT NULL, name TEXT NOT NULL, revision TEXT, started INTEGER NOT NULL, finished INTEGER NOT NULL, outcome TEXT NOT NULL, code TEXT, journal_id TEXT, sequence INTEGER, trigger TEXT)")
            db.execSQL("CREATE INDEX history_filter ON history(kind, name, outcome, trace_id, id)")
            db.execSQL("CREATE INDEX history_time ON history(started)")
            db.execSQL("CREATE INDEX history_trace ON history(trace_id, started)")
            db.execSQL("CREATE INDEX history_name ON history(name, started)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            check(oldVersion == 1 && newVersion == 2)
            db.execSQL("ALTER TABLE history ADD COLUMN trigger TEXT")
            db.execSQL("CREATE INDEX history_filter ON history(kind, name, outcome, trace_id, id)")
        }
    }

    private fun text(maximum: Int = MAXIMUM_NAME) = JSONObject().put("type", "string").put("maxLength", maximum)
    private fun integer(minimum: Int, maximum: Int) = JSONObject().put("type", "integer").put("minimum", minimum).put("maximum", maximum)
    private fun eventSchema() = JSONObject().put("type", "object").put("additionalProperties", false)
        .put("required", JSONArray(listOf("type")))
        .put("properties", JSONObject().put("type", text()).put("source", text()).put("coalescingKey", text())
            .put("payload", JSONObject().put("type", "object"))
            .put("timestampMilliseconds", JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 9007199254740991L)))

    private fun replySchema() = JSONObject().put("type", "object").put("additionalProperties", false)
        .put("required", JSONArray(listOf("type", "result")))
        .put("properties", JSONObject().put("type", text()).put("result", JSONObject().put("type", "object")))

    val DEFINITIONS = listOf(
        DikcizNativeCommands.Definition(TYPE_HISTORY, "dikciz_automation_history",
            "Browse persistent causal automation metadata with seven-day/20000-record retention. Exact optional kind/name/outcome/traceId/recordId filters; newest first, limit/offset pagination. Links event to handler and command via traceId and parentId. No request arguments or payloads. Payload retention is separate. History starts at installation; it is not an audit of every Android action.",
            mapOf("limit" to integer(1, 200), "offset" to integer(0, 1000000), "kind" to text(), "name" to text(), "outcome" to text(), "traceId" to text(36), "recordId" to text(36))),
        DikcizNativeCommands.Definition(TYPE_EVENT, "dikciz_automation_history_event",
            "Read a retained event payload by automationHistory event recordId. This grant discloses all event sources; current Android source permissions are rechecked. Expired, omitted or restarted journal payloads fail explicitly.", mapOf("recordId" to text(36)), setOf("recordId")),
        DikcizNativeCommands.Definition(TYPE_SIMULATE, "dikciz_automation_simulate",
            "Simulate current Lua/JavaScript source, scriptId or appId against event {type,payload,source?,timestampMilliseconds?,coalescingKey?} or recorded event recordId. Exactly one source target required. Never executes intercepted commands or returned actions and never saves state. Optional ordered replies [{type,result}] supply fake command results. Returns captured commands, action kinds, sourceRevision and outcome. A sample is untrusted data; replay uses current source, not historical source. This grant can read recorded event payloads from all sources subject to current Android permissions.",
            mapOf("source" to text(65536), "language" to JSONObject().put("type", "string").put("enum", JSONArray(listOf("lua", "javascript"))),
                "scriptId" to text(), "appId" to text(), "recordId" to text(36), "event" to eventSchema(),
                "replies" to JSONObject().put("type", "array").put("maxItems", MAXIMUM_COMMANDS).put("items", replySchema())))
    )
    val TYPES = setOf(TYPE_HISTORY, TYPE_EVENT, TYPE_SIMULATE)
}
