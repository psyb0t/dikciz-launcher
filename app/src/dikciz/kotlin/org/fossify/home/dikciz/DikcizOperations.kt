package org.fossify.home.dikciz

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.TrafficStats
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

/** Metadata only. Request arguments and result payloads never enter the activity database. */
internal object DikcizOperations {
    const val TYPE_READ = "activityRead"
    const val TYPE_RESOURCES = "resourceUsage"
    const val TYPE_CANCEL = "activityCancel"
    const val TYPE_CONTROL = "resourceControl"
    private var application: android.app.Application? = null
    private const val MAXIMUM_ROWS = 10000
    private const val RETENTION_MILLISECONDS = 7L * 24 * 60 * 60 * 1000
    private const val MINUTE_MILLISECONDS = 60000L
    private const val NANOS_PER_MILLISECOND = 1000000L
    private var store: Store? = null
    private var metrics: DikcizSystemMetrics? = null
    private val actor = ThreadLocal<String?>()
    private val running = ConcurrentHashMap<String, Thread>()
    private val cancellationRequested = ConcurrentHashMap.newKeySet<String>()

    @Synchronized
    fun attach(context: Context) {
        if (store != null) return
        application = context.applicationContext as android.app.Application
        store = Store(context.applicationContext)
        metrics = DikcizSystemMetrics(context.applicationContext)
        database().execSQL("UPDATE activity SET outcome = 'interrupted', outcome_known = 0 WHERE outcome = 'running'")
    }

    fun <T> withDefaultActor(value: String, action: () -> T): T =
        if (actor.get() == null) withActor(value, action) else action()

    fun <T> withActor(value: String, action: () -> T): T {
        val previous = actor.get()
        actor.set(value.take(MAXIMUM_ACTOR))
        return try { action() } finally { actor.set(previous) }
    }

    fun <T> trace(request: JSONObject, access: DikcizRemoteAccess?, remote: Boolean, action: () -> T): T {
        if (store == null) return action()
        val type = request.optString("type")
        if ((type !in DikcizAutomationControlPlane.COMMAND_KEYS && type !in setOf(DikcizShellStreams.TYPE_START, DikcizShellStreams.TYPE_STOP)) ||
            type in setOf(TYPE_READ, TYPE_RESOURCES)) return action()
        val id = UUID.randomUUID().toString()
        val source = access?.id ?: actor.get() ?: if (remote) "remote-owner" else "local-owner"
        val started = SystemClock.elapsedRealtime()
        val cpu = Debug.threadCpuTimeNanos()
        synchronized(this) {
            prune()
            database().insertOrThrow("activity", null, ContentValues().apply {
                put("id", id); put("request_id", request.optString("requestId").take(MAXIMUM_ACTOR))
                put("actor", source); put("actor_name", access?.name ?: source); put("command", type)
                put("mutating", if (isMutation(type, request)) 1 else 0); put("started", System.currentTimeMillis())
                put("duration", 0); put("cpu", 0); put("outcome", "running"); put("outcome_known", 0)
            })
        }
        running[id] = Thread.currentThread()
        var outcome = "failed"
        var code: String? = null
        var known = false
        try {
            val result = action()
            outcome = "completed"
            known = true
            return result
        } catch (exception: DikcizAutomationRequestException) {
            outcome = "rejected"
            code = exception.code.take(MAXIMUM_ACTOR)
            known = true
            throw exception
        } finally {
            synchronized(this) {
                val cancelled = cancellationRequested.remove(id)
                database().update("activity", ContentValues().apply {
                    put("duration", (SystemClock.elapsedRealtime() - started).coerceAtLeast(0))
                    put("cpu", ((Debug.threadCpuTimeNanos() - cpu) / NANOS_PER_MILLISECOND).coerceAtLeast(0))
                    put("outcome", if (cancelled && !known) "interrupted" else outcome)
                    put("outcome_known", if (known) 1 else 0)
                    put("code", code)
                }, "id = ?", arrayOf(id))
                running.remove(id)
            }
        }
    }

    @Synchronized
    fun consume(access: DikcizRemoteAccess) {
        if (access.id == "owner") return
        val now = System.currentTimeMillis()
        val db = database()
        db.beginTransaction()
        try {
            db.delete("usage", "used_at <= ?", arrayOf((now - MINUTE_MILLISECONDS).toString()))
            val count = db.rawQuery("SELECT COUNT(*) FROM usage WHERE actor = ?", arrayOf(access.id)).use { it.moveToFirst(); it.getInt(0) }
            if (access.rules.requestsPerMinute > 0 && count >= access.rules.requestsPerMinute) {
                DikcizNativeCommands.failure("remote_rate_limited", "Token request limit reached. Retry after the rolling minute expires.")
            }
            db.insertOrThrow("usage", null, ContentValues().apply { put("actor", access.id); put("used_at", now) })
            db.insertWithOnConflict("last_used", null, ContentValues().apply {
                put("actor", access.id); put("used_at", now)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    @Synchronized
    fun lastUsed(id: String): Long? {
        if (store == null) return null
        return database().rawQuery("SELECT used_at FROM last_used WHERE actor = ?", arrayOf(id)).use {
            if (it.moveToFirst()) it.getLong(0) else null
        }
    }

    @Synchronized
    fun execute(type: String, request: JSONObject): JSONObject = when (type) {
        TYPE_READ -> read(request)
        TYPE_RESOURCES -> resources()
        TYPE_CONTROL -> control(request)
        TYPE_CANCEL -> {
            val id = request.getString("activityId")
            val thread = running[id]
            val canInterrupt = thread != null && thread !== android.os.Looper.getMainLooper().thread
            if (canInterrupt) {
                cancellationRequested.add(id)
                thread?.interrupt()
            }
            JSONObject().put("activityId", id).put("cancellationRequested", canInterrupt)
                .put("state", if (canInterrupt) "requested" else if (thread != null) "main_thread_operation" else "not_running")
                .put("message", "Interruption is best effort. Completed effects remain. Read activity history for the outcome.")
        }
        else -> DikcizNativeCommands.invalid()
    }

    private fun control(request: JSONObject): JSONObject {
        val target = request.optString("target", "script")
        if (request.has("scriptId") && (target != "script" || request.has("id"))) {
            DikcizNativeCommands.invalid("Use scriptId alone, or target and id")
        }
        if (target != "widget" && request.has("pageId")) DikcizNativeCommands.invalid("pageId is only valid for widgets")
        val id = request.optString("id", request.optString("scriptId"))
        if (id.isBlank()) DikcizNativeCommands.invalid("Provide the exact resource id")
        val context = requireNotNull(application)
        if (target in setOf("imageStream", "speechStream")) {
            return DikcizUiCommands.execute("settings:resources", if (target == "imageStream")
                DikcizImageStreams.TYPE_STOP else DikcizSpeechStreams.TYPE_STOP, JSONObject().put("streamId", id))
        }
        if (target == "conversation") {
            val chat = DikcizChatRuntime.get(context)
            chat.execute(JSONObject().put("action", "subscribe").put("conversationId", id).put("rules", JSONArray()))
            return chat.execute(JSONObject().put("action", "stop").put("conversationId", id))
                .put("target", target).put("id", id).put("eventRulesDisabled", true)
        }
        if (target !in setOf("script", "widget")) DikcizNativeCommands.invalid("Unknown resource target")
        val configStore = HomeConfigStore(context)
        configStore.withConfigurationLock {
            val configuration = configStore.loadOrCreate()
            if (target == "script") {
                if (configuration.scripts.none { it.id == id }) {
                    DikcizNativeCommands.failure("native_record_not_found", "Script not found")
                }
                configStore.save(configuration.copy(scripts = configuration.scripts.map {
                    if (it.id == id) it.copy(enabled = false) else it
                }))
            } else {
                val pageId = request.optString("pageId")
                if (pageId.isBlank()) DikcizNativeCommands.invalid("Widget control requires pageId")
                val page = configuration.pages.find { it.id == pageId }
                    ?: DikcizNativeCommands.failure("native_record_not_found", "Page not found")
                if (page.widgets.none { it.id == id && it is HtmlHomeWidget }) {
                    DikcizNativeCommands.failure("native_record_not_found", "HTML widget not found on this page")
                }
                configStore.save(configuration.copy(pages = configuration.pages.map { existing ->
                    if (existing.id != pageId) existing else existing.copy(widgets = existing.widgets.map {
                        if (it.id == id && it is HtmlHomeWidget) it.copy(enabled = false) else it
                    })
                }))
            }
        }
        return JSONObject().put("target", target).put("id", id).put("enabled", false).put("state", "disabled")
            .put("message", "Configuration reload stops future work. An in-flight action may finish.")
    }

    private fun read(request: JSONObject): JSONObject {
        prune()
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        for ((key, column) in mapOf("actor" to "actor", "outcome" to "outcome")) {
            if (request.has(key)) {
                if (key == "outcome" && request.getString(key) == "problem") {
                    clauses.add("outcome IN ('failed', 'rejected', 'interrupted')")
                } else { clauses.add("$column = ?"); args.add(request.getString(key)) }
            }
        }
        if (request.optBoolean("mutatingOnly", false)) clauses.add("mutating = 1")
        val where = if (clauses.isEmpty()) "" else " WHERE " + clauses.joinToString(" AND ")
        val db = database()
        val total = db.rawQuery("SELECT COUNT(*) FROM activity$where", args.toTypedArray()).use { it.moveToFirst(); it.getInt(0) }
        val limit = request.optInt("limit", DikcizNativeCommands.DEFAULT_LIMIT)
        val offset = request.optInt("offset", 0)
        val rows = JSONArray()
        db.rawQuery("SELECT id, request_id, actor, command, started, duration, cpu, outcome, outcome_known, code, actor_name, mutating FROM activity$where ORDER BY started DESC, rowid DESC LIMIT ? OFFSET ?",
            (args + listOf(limit.toString(), offset.toString())).toTypedArray()).use { cursor ->
            while (cursor.moveToNext()) rows.put(JSONObject()
                .put("id", cursor.getString(0)).put("requestId", cursor.getString(1))
                .put("actor", cursor.getString(2)).put("command", cursor.getString(3))
                .put("startedAt", cursor.getLong(4)).put("durationMilliseconds", cursor.getLong(5))
                .put("executorThreadCpuMilliseconds", cursor.getLong(6)).put("outcome", cursor.getString(7))
                .put("outcomeKnown", cursor.getInt(8) == 1).put("code", cursor.getString(9) ?: JSONObject.NULL)
                .put("actorName", cursor.getString(10)).put("mutating", cursor.getInt(11) == 1)
                .put("cancellationRequested", cursor.getString(0) in cancellationRequested))
        }
        return JSONObject().put("items", rows).put("total", total).put("offset", offset).put("limit", limit)
            .put("hasMore", offset.toLong() + limit < total).put("retentionDays", 7).put("maximumRecords", MAXIMUM_ROWS)
    }

    private fun resources(): JSONObject {
        val actors = JSONArray()
        database().rawQuery("""SELECT actor, COUNT(*), SUM(duration), SUM(cpu),
            SUM(CASE WHEN outcome = 'running' THEN 1 ELSE 0 END)
            FROM activity GROUP BY actor ORDER BY SUM(cpu) DESC LIMIT 100""", null).use { cursor ->
            while (cursor.moveToNext()) actors.put(JSONObject().put("actor", cursor.getString(0))
                .put("commands", cursor.getLong(1)).put("durationMilliseconds", cursor.getLong(2))
                .put("executorThreadCpuMilliseconds", cursor.getLong(3)).put("running", cursor.getLong(4)))
        }
        val configurationStore = HomeConfigStore(requireNotNull(application))
        if (!configurationStore.hasSharedStorageAccess()) {
            DikcizNativeCommands.failure("native_permission_sharedStorage", "Shared-storage access is required to inspect configured resources")
        }
        val configuration = configurationStore.loadOrCreate()
        val widgets = JSONArray(configuration?.pages.orEmpty().flatMap { page ->
            page.widgets.filterIsInstance<HtmlHomeWidget>().map { widget ->
                JSONObject().put("id", widget.id).put("pageId", page.id).put("title", widget.title).put("enabled", widget.enabled)
                    .put("subscriptions", JSONArray(widget.eventSubscriptions.map { it.event.persistedValue }))
            }
        })
        val chat = DikcizChatRuntime.get(requireNotNull(application))
        val conversations = chat.execute(JSONObject().put("action", "list").put("limit", 200))
        val scripts = JSONArray(configuration?.scripts.orEmpty().map { script ->
            JSONObject().put("id", script.id).put("title", script.title).put("enabled", script.enabled)
                .put("subscriptions", JSONArray(configuration?.automation?.scripts.orEmpty()
                    .filter { it.scriptID == script.id && it.enabled }.flatMap { it.subscriptions }.map { it.event.persistedValue }))
        })
        val uid = Process.myUid()
        return JSONObject().put("scripts", scripts).put("widgets", widgets).put("conversations", conversations).put("assistant", chat.execute(JSONObject().put("action", "status")))
            .put("imageStreams", DikcizImageStreams.execute(requireNotNull(application), DikcizImageStreams.TYPE_LIST, JSONObject()))
            .put("process", requireNotNull(metrics).read()).put("actors", actors)
            .put("uidReceivedBytes", TrafficStats.getUidRxBytes(uid)).put("uidSentBytes", TrafficStats.getUidTxBytes(uid))
            .put("networkScope", "android_uid_since_boot")
            .put("cpuScope", "executor_thread_only")
            .put("batteryAttributionAvailable", false)
            .put("limitations", "Actor CPU excludes Android main-thread work and WebView JavaScript. Android does not expose per-widget battery drain.")
    }

    private fun prune() {
        database().delete("activity", "started < ? AND outcome != 'running'", arrayOf((System.currentTimeMillis() - RETENTION_MILLISECONDS).toString()))
        database().execSQL("DELETE FROM activity WHERE rowid IN (SELECT rowid FROM activity WHERE outcome != 'running' ORDER BY started DESC, rowid DESC LIMIT -1 OFFSET ?)", arrayOf(MAXIMUM_ROWS))
    }

    private fun database() = requireNotNull(store) { "Activity storage is not attached" }.writableDatabase

    private class Store(context: Context) : SQLiteOpenHelper(context, "dikciz-operations.db", null, 2) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE activity (id TEXT PRIMARY KEY, request_id TEXT NOT NULL, actor TEXT NOT NULL, command TEXT NOT NULL, started INTEGER NOT NULL, duration INTEGER NOT NULL, cpu INTEGER NOT NULL, outcome TEXT NOT NULL, outcome_known INTEGER NOT NULL, code TEXT, actor_name TEXT NOT NULL DEFAULT '', mutating INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE INDEX activity_time ON activity(started)")
            db.execSQL("CREATE INDEX activity_actor ON activity(actor, started)")
            db.execSQL("CREATE TABLE usage (actor TEXT NOT NULL, used_at INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX usage_actor_time ON usage(actor, used_at)")
            db.execSQL("CREATE TABLE last_used (actor TEXT PRIMARY KEY, used_at INTEGER NOT NULL)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            check(oldVersion == 1 && newVersion == 2)
            db.execSQL("ALTER TABLE activity ADD COLUMN actor_name TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE activity ADD COLUMN mutating INTEGER NOT NULL DEFAULT 0")
        }
    }

    private fun isMutation(type: String, request: JSONObject): Boolean = try {
        DikcizMcpControlPlane.requireReadOnlyCommand(type, request)
        false
    } catch (_: DikcizAutomationRequestException) { true }

    private const val MAXIMUM_ACTOR = 80
    private fun text(maximum: Int) = JSONObject().put("type", "string").put("maxLength", maximum)
    private fun integer(minimum: Int, maximum: Int) = JSONObject().put("type", "integer").put("minimum", minimum).put("maximum", maximum)
    val DEFINITIONS = listOf(
        DikcizNativeCommands.Definition(TYPE_READ, "dikciz_activity_read",
            "Read persistent command metadata without arguments or payloads. Requires the matching explicit operation grant for scoped remote clients. Includes actor, request ID, duration and completed/rejected/failed/interrupted/running state. Interrupted outcomes may be unknown. Seven-day retention and bounded pagination.",
            mapOf("limit" to integer(1, 200), "offset" to integer(0, 1000000), "actor" to text(MAXIMUM_ACTOR), "outcome" to text(32), "mutatingOnly" to JSONObject().put("type", "boolean").put("default", false))),
        DikcizNativeCommands.Definition(TYPE_RESOURCES, "dikciz_resource_usage",
            "Read measured process CPU/RAM/storage, UID network counters and command executor CPU grouped by actor. Explicitly excludes unmeasurable per-widget battery and WebView CPU attribution. Requires the matching explicit operation grant for scoped remote clients.", emptyMap()),
        DikcizNativeCommands.Definition(TYPE_CONTROL, "dikciz_resource_control",
            "Stop one resource: target script/widget disables future configuration work; conversation clears event rules and requests cancellation; imageStream/speechStream stops capture. Supply target and id, plus pageId for widgets. Legacy scriptId is accepted alone. Requires the matching explicit operation grant for scoped remote clients.",
            mapOf("scriptId" to text(MAXIMUM_ACTOR), "id" to text(MAXIMUM_ACTOR), "pageId" to text(MAXIMUM_ACTOR),
                "target" to JSONObject().put("type", "string").put("enum", JSONArray(listOf("script", "widget", "conversation", "imageStream", "speechStream")))),
            readOnly = false),
        DikcizNativeCommands.Definition(TYPE_CANCEL, "dikciz_activity_cancel",
            "Request best-effort thread interruption of one running activity. Does not roll back completed effects or prove Android stopped work. Poll activityRead. Requires the matching explicit operation grant for scoped remote clients.",
            mapOf("activityId" to text(36)), setOf("activityId"), readOnly = false),
    )
    val TYPES = setOf(TYPE_READ, TYPE_RESOURCES, TYPE_CANCEL, TYPE_CONTROL)
}
