package org.fossify.home.dikciz

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.FileObserver
import android.os.SystemClock
import android.provider.Settings
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONArray
import org.json.JSONObject

/** Serialized app execution shares native commands and the Lua/JavaScript interpreters. */
internal object DikcizCustomApps {
    private const val INSTALL_KIND = "app-install"
    internal const val TIMER_KIND = "app-timer"
    internal const val ALERT_KIND = "app-alert"
    private const val RUN_KIND = "app-run"
    private const val PENDING = "pending"
    private const val CLAIMED = "claimed"
    private val lua = DikcizLuaRuntime()
    private val javascript = DikcizJavaScriptRuntime(lua::decodeJsonEventResult)
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val activeCalls = mutableSetOf<String>()
    // Only applicationContext is supplied; native sessions live for this process.
    @android.annotation.SuppressLint("StaticFieldLeak")
    private var native: DikcizNativeData? = null
    private var permissionRequests: DikcizPermissionRequests? = null
    private val cancellations = ConcurrentHashMap<String, AtomicLong>()
    val alertRevision = AtomicLong()
    private val lifecycleWorker = Executors.newSingleThreadExecutor()
    private val resuming = AtomicBoolean(false)
    private data class AppSubscription(val appId: String, val index: Int, val subscription: DikcizAutomationSubscription)
    @Volatile private var appSubscriptions: List<AppSubscription> = emptyList()
    @Volatile private var sourceSubscriptions: List<DikcizAutomationSubscription> = emptyList()
    private val subscriptionObservers = mutableListOf<FileObserver>()
    private val lastDispatch = mutableMapOf<String, Long>()
    private var subscriptionRefresh: Runnable? = null
    fun subscriptions(): List<DikcizAutomationSubscription> = sourceSubscriptions
    fun signalCancellation(id: String) { cancellations.computeIfAbsent(id) { AtomicLong() }.incrementAndGet() }
    fun signalAllCancellations() { cancellations.values.forEach { it.incrementAndGet() } }
    fun resumeAsync(context: Context) {
        if (!resuming.compareAndSet(false, true)) return
        lifecycleWorker.execute {
            try { resume(context.applicationContext) }
            catch (exception: Exception) { logger(context).error("custom_app_resume_failed", mapOf("error_class" to exception.javaClass.simpleName)) }
            finally { resuming.set(false) }
        }
    }
    private fun logger(context: Context) = DikcizLogger("DikcizApps", HomeConfigStore(context).logDirectory)
    private fun database(context: Context) = DikcizCompanionStore(context)
    fun boot(context: Context) = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private fun isEnabled(context: Context, app: DikcizCustomApp): Boolean = database(context).use {
        it.get(INSTALL_KIND, app.id)?.optBoolean("enabled", true) ?: true
    }

    @Synchronized fun ensureBundled(context: Context) {
        val store = DikcizCustomAppStore(context)
        val removed = database(context).use { it.all(INSTALL_KIND).filter { value -> value.optBoolean("removed") }.map { value -> value.getString("appId") }.toSet() }
        for (id in store.installBundled(removed)) {
            database(context).use { it.put(INSTALL_KIND, id, JSONObject().put("enabled", true)) }
        }
        if (SAFETY_ID in store.ids()) migrateSafety(context, store)
    }

    private fun migrateSafety(context: Context, store: DikcizCustomAppStore) {
        val app = store.read(SAFETY_ID)
        if (app.state.optBoolean("legacyMigrated")) return
        val state = JSONObject(app.state.toString()).put("legacyMigrated", true)
        database(context).use { db ->
            db.get("sos-plan", "current")?.let { state.put("plan", it) }
            db.get("sos-run", "current")?.let { run ->
                if (run.optString("state") in setOf("armed", "warning", "active")) run.put("state", "needs_review").put("reason", "custom_app_migration")
                state.put("run", run)
            }
        }
        store.saveState(SAFETY_ID, state)
    }

    @Synchronized fun execute(context: Context, type: String, request: JSONObject): JSONObject {
        DikcizNativeCommands.validate(type, request, true)
        val store = DikcizCustomAppStore(context)
        if (type == DikcizCustomAppCommands.RUNTIME) return runtime(context)
        if (type == DikcizCustomAppCommands.LIST) {
            val apps = store.ids().mapNotNull { id ->
                try { val app = store.read(id); JSONObject().put("appId", id).put("title", app.title).put("description", app.manifest.getString("description")).put("enabled", isEnabled(context, app)) }
                catch (exception: Exception) {
                    logger(context).warn("custom_app_invalid", mapOf("app_id" to id, "error_class" to exception.javaClass.simpleName)); null
                }
            }
            return DikcizNativeCommands.page(apps, request)
        }
        if (type == DikcizCustomAppCommands.INSTALL || type == DikcizCustomAppCommands.CONTROL && request.getString("action") == "import") {
            if (!request.optBoolean("confirm")) DikcizNativeCommands.invalid("Installing an app requires confirm=true")
            val app = if (type == DikcizCustomAppCommands.INSTALL) store.fromRequest(request) else store.import(request.getString("path"))
            parseSubscriptions(context, app.manifest, HomeConfigStore(context).loadOrCreate().limits)
            if (pending(context, app.id) || alert(context)?.optString("appId") == app.id) DikcizNativeCommands.failure("custom_app_active", "Disable app timers and alerts before replacing its bundle")
            val enabled = isEnabled(context, app)
            store.write(app)
            database(context).use { it.put(INSTALL_KIND, app.id, JSONObject().put("enabled", enabled)) }
            changed(context, app.id, true)
            return JSONObject().put("appId", app.id).put("state", "installed").put("enabled", enabled)
        }
        val id = request.getString("appId")
        val app = store.read(id)
        return when (type) {
            DikcizCustomAppCommands.READ -> read(context, app)
            DikcizCustomAppCommands.CONTROL -> when (request.getString("action")) {
                "enable" -> {
                    parseSubscriptions(context, app.manifest, HomeConfigStore(context).loadOrCreate().limits)
                    database(context).use { it.put(INSTALL_KIND, id, JSONObject().put("enabled", true)) }
                    changed(context, id, true); JSONObject().put("enabled", true).put("appId", id)
                }
                "disable", "remove" -> {
                    if (request.getString("action") == "remove" && !request.optBoolean("confirm")) DikcizNativeCommands.invalid("Removing an app requires confirm=true")
                    if (isEnabled(context, app)) {
                        try { invoke(context, app, "app.stopped", JSONObject().put("reason", "owner_disabled"), stopping = true) }
                        catch (exception: Exception) { logger(context).warn("custom_app_stop_handler_failed", mapOf("app_id" to id, "error_class" to exception.javaClass.simpleName)) }
                    }
                    cancelTimers(context, id, "owner_disabled")
                    closeAlert(context, id)
                    database(context).use { it.put(INSTALL_KIND, id, JSONObject().put("enabled", false).put("appId", id).put("removed", request.getString("action") == "remove")) }
                    if (request.getString("action") == "remove") {
                        store.remove(id)
                        database(context).use { db ->
                            db.delete(RUN_KIND, id)
                            timers(context).filter { it.optString("appId") == id }.forEach { db.delete(TIMER_KIND, it.getString("id")) }
                            if (id !in DikcizCustomAppStore.BUNDLED) db.delete(INSTALL_KIND, id)
                        }
                        cancellations.remove(id)
                    }
                    changed(context, id, true); JSONObject().put("enabled", false).put("removed", request.getString("action") == "remove")
                }
                "export" -> store.export(id)
                "open" -> {
                    parseSubscriptions(context, app.manifest, HomeConfigStore(context).loadOrCreate().limits)
                    database(context).use { it.put(INSTALL_KIND, id, JSONObject().put("enabled", true)) }
                    changed(context, id, true)
                    context.startActivity(Intent(context, DikcizCustomAppActivity::class.java).putExtra("appId", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    JSONObject().put("state", "requested").put("appId", id)
                }
                else -> DikcizNativeCommands.invalid()
            }
            DikcizCustomAppCommands.EVENT -> invoke(context, app, request.getString("event"), request.optJSONObject("payload") ?: JSONObject())
            DikcizCustomAppCommands.STATE -> {
                if (request.getString("action") == "replace") {
                    store.saveState(id, request.getJSONObject("state")); changed(context, id)
                }
                JSONObject().put("state", store.read(id).state)
            }
            DikcizCustomAppCommands.SCHEDULE -> schedule(context, app, request)
            DikcizCustomAppCommands.ALERT -> showAlert(context, app, request)
            else -> DikcizNativeCommands.invalid()
        }
    }

    private fun read(context: Context, app: DikcizCustomApp) = JSONObject().put("manifest", app.manifest).put("enabled", isEnabled(context, app))
        .put("html", app.html).put("css", app.css).put("javascript", app.javascript).put("backgroundSource", app.source).put("state", app.state)
        .put("directory", DikcizCustomAppStore(context).directory(app.id).absolutePath)
        .put("uiVisible", DikcizCustomAppActivity.current()?.appId() == app.id)
        .put("run", database(context).use { it.get(RUN_KIND, app.id) } ?: JSONObject.NULL)
        .put("timers", JSONArray(timers(context).filter { it.optString("appId") == app.id }))
        .put("alert", DikcizSosWarning.status().takeIf { it.optString("appId") == app.id }
            ?: JSONObject().put("active", false).put("screenVisible", false).put("speechState", "idle"))

    private fun requireEnabled(context: Context, app: DikcizCustomApp) {
        if (!isEnabled(context, app)) DikcizNativeCommands.failure("custom_app_disabled", "This app is stopped. Open or start it to resume execution")
    }

    @Synchronized fun query(context: Context, id: String, event: String, payload: JSONObject): JSONObject =
        invoke(context, DikcizCustomAppStore(context).read(id), event, payload, true)

    private fun invoke(context: Context, app: DikcizCustomApp, name: String, payload: JSONObject, readOnly: Boolean = false, stopping: Boolean = false): JSONObject {
        requireEnabled(context, app)
        if (!activeCalls.add(app.id)) DikcizNativeCommands.failure("custom_app_reentrant", "An app cannot invoke itself during a running handler")
        val revision = cancellations.computeIfAbsent(app.id) { AtomicLong() }.get() - if (stopping) 1 else 0
        try {
            val configuration = HomeConfigStore(context).loadOrCreate()
            val script = DikcizLuaScript(app.id, app.title, true, app.source, 1, app.state, app.language)
            val event = DikcizAutomationEvent(DikcizAutomationEventType.Custom, "app:" + app.id, System.currentTimeMillis(), name,
                JSONObject(payload.toString()), customEventName = name)
            val replies = mutableListOf<DikcizLocalCommands.Reply>()
            val execute = if (app.language == DikcizScriptLanguage.JavaScript) javascript::executeEvent else lua::executeEvent
            val result = execute(script, event, configuration.limits, JSONObject(), null, DikcizAutomationStatus.androidAccessDocument(context)) { raw ->
                scopedCommand(context, app.id, JSONObject(raw), readOnly, revision).also { replies.add(it) }.response.toString()
            }
            val run = JSONObject().put("lastRunAt", System.currentTimeMillis()).put("event", name)
            when (result) {
                is DikcizLuaAutomationResult.Actions -> {
                    result.result?.let { DikcizCustomAppStore.validateState(it) }
                    result.result?.optJSONObject("error")?.let { failure ->
                        val code = failure.opt("code") as? String ?: "custom_app_error"
                        val message = failure.opt("message") as? String ?: "App operation was rejected"
                        if (!DikcizCustomAppCommands.EVENT_PATTERN.matches(code) || message.length > 2000) DikcizNativeCommands.invalid("Invalid app error response")
                        database(context).use { it.put(RUN_KIND, app.id, run.put("outcome", "rejected").put("status", code)) }
                        throw DikcizAutomationRequestException(code, message)
                    }
                    if (readOnly && result.actions.isNotEmpty()) DikcizNativeCommands.failure("native_read_only", "Read-only app queries cannot return actions")
                    if (cancellations[app.id]?.get() != revision && result.actions.any { it is DikcizLuaAutomationAction.EmitEvent }) {
                        DikcizNativeCommands.failure("custom_app_cancel_requested", "Owner cancellation blocks further emitted effects")
                    }
                    if (result.actions.any { it !is DikcizLuaAutomationAction.PatchState && it !is DikcizLuaAutomationAction.EmitEvent }) {
                        DikcizNativeCommands.failure("custom_app_action_denied", "App actions support patchState and emitEvent; use phone commands for other effects")
                    }
                    val state = DikcizCustomAppStore(context).read(app.id).state
                    result.actions.filterIsInstance<DikcizLuaAutomationAction.PatchState>().forEach { patch ->
                        patch.values.keys().forEach { key -> state.put(key, patch.values.get(key)) }
                    }
                    if (!readOnly) DikcizCustomAppStore(context).saveState(app.id, state)
                    run.put("outcome", "completed").put("status", result.status ?: JSONObject.NULL)
                    result.actions.filterIsInstance<DikcizLuaAutomationAction.EmitEvent>().forEach { action ->
                        if (!DikcizAutomationEventBus.enqueueCustomEvent(context, action.eventName, "app:" + app.id, action.payload, action.coalescingKey, 1)) {
                            DikcizNativeCommands.failure("event_rejected", "App output event was rejected")
                        }
                    }
                    database(context).use { it.put(RUN_KIND, app.id, run) }
                    replies.forEach { it.delivered() }
                    if (!readOnly) changed(context, app.id)
                    logger(context).info("custom_app_event_completed", mapOf("app_id" to app.id, "event_type" to name))
                    return JSONObject(run.toString()).put("result", result.result ?: JSONObject()).put("state", state)
                }
                else -> {
                    run.put("outcome", "failed").put("status", (result as? DikcizLuaAutomationResult.Failure)?.reason?.persistedValue ?: "no_handler")
                    database(context).use { it.put(RUN_KIND, app.id, run) }
                    logger(context).error("custom_app_event_failed", mapOf("app_id" to app.id, "status" to run.getString("status")))
                    DikcizNativeCommands.failure("custom_app_execution_failed", "App handler failed: " + run.getString("status"))
                }
            }
        } finally { activeCalls.remove(app.id) }
    }

    @Synchronized fun scopedCommand(context: Context, id: String, incoming: JSONObject, readOnly: Boolean = false, cancellationRevision: Long? = null): DikcizLocalCommands.Reply {
        val request = JSONObject(incoming.toString())
        val requestId = request.opt("requestId") ?: UUID.randomUUID().toString()
        request.put("requestId", requestId)
        try {
            val app = DikcizCustomAppStore(context).read(id)
            requireEnabled(context, app)
            val type = request.getString("type")
            val cleanup = type == DikcizJourneys.FENCE_DELETE || type == DikcizCustomAppCommands.STATE || type == DikcizCustomAppCommands.ALERT && request.optString("action") == "close" ||
                type == DikcizCustomAppCommands.SCHEDULE && request.optString("action") in setOf("cancel", "cancelAll") ||
                type == DikcizMicrophoneSession.TYPE && request.optString("action") in setOf("stop", "cancel")
            fun checkCancellation() {
                if (cancellationRevision != null && cancellations[id]?.get() != cancellationRevision && !cleanup &&
                    type !in DikcizNativeCommands.READ_ONLY_TYPES && type !in DikcizFileCommands.READ_ONLY_TYPES) {
                    DikcizNativeCommands.failure("custom_app_cancel_requested", "Owner cancellation blocks further effects from this handler")
                }
            }
            checkCancellation()
            if (readOnly && (incoming.optBoolean(DikcizPermissionRequests.KEY_AUTO_REQUEST) ||
                    type !in DikcizNativeCommands.READ_ONLY_TYPES && type !in DikcizFileCommands.READ_ONLY_TYPES)) {
                DikcizNativeCommands.failure("native_read_only", "Read-only app queries cannot execute mutating commands")
            }
            if (type == "automationDispatch" && !request.has("sourceWidgetAddress")) {
                val actions = request.optJSONArray("actions") ?: DikcizNativeCommands.invalid("actions must be an array")
                if (actions.length() != 1 || actions.getJSONObject(0).optString("type") != "patchState" ||
                    actions.getJSONObject(0).keys().asSequence().any { it !in setOf("type", "values") }) {
                    DikcizNativeCommands.failure("custom_app_action_denied", "App-local dispatch supports one patchState; use phone commands or an HTML sourceWidgetAddress for other effects")
                }
                val state = JSONObject(app.state.toString())
                val values = actions.getJSONObject(0).getJSONObject("values")
                values.keys().forEach { key -> state.put(key, values.get(key)) }
                return scopedCommand(context, id, JSONObject().put("type", DikcizCustomAppCommands.STATE).put("action", "replace")
                    .put("state", state).put("requestId", requestId))
            }
            if (type in DikcizCustomAppCommands.SCOPED && !request.has("appId")) {
                request.put("appId", id)
            }
            if (type in DikcizNativeCommands.FIELDS) {
                val command = DikcizPermissionRequests.command(type, request).copy(readOnly = readOnly)
                val call = {
                    checkCancellation()
                    DikcizOperations.withActor("app:" + id) {
                        DikcizOperations.trace(request, null, false) {
                            val executor = native ?: DikcizNativeData(context.applicationContext, logger(context)).also { native = it }
                            executor.execute(command)
                        }
                    }
                }
                val result = if (type in DikcizNativeCommands.BACKGROUND_TYPES || android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) call()
                else onMainThread(call)
                return DikcizLocalCommands.Reply(JSONObject().put("type", "result").put("requestId", requestId).put("result", result), type) {}
            }
            if (type in DikcizFileCommands.FIELDS) {
                val command = DikcizPermissionRequests.command(type, request).copy(readOnly = readOnly)
                val result = DikcizOperations.withActor("app:" + id) {
                    DikcizOperations.trace(request, null, false) {
                        DikcizDeviceFiles(context.applicationContext, logger(context)).execute(command)
                    }
                }
                return DikcizLocalCommands.Reply(JSONObject().put("type", "result").put("requestId", requestId).put("result", result), type) {}
            }
            return DikcizLocalCommands.execute("app:" + id, request)
        } catch (exception: DikcizAutomationRequestException) {
            val reply = DikcizLocalCommands.failure(requestId, exception.code, exception.message ?: "App command failed")
            val recovery = permissionRequests ?: DikcizPermissionRequests(context.applicationContext, logger(context)) {}.also { permissionRequests = it }
            recovery.forFailure(exception.code, incoming.optBoolean("autoRequestPermissions"))?.let { reply.response.put("permissionRequest", it) }
            return reply
        } catch (exception: Exception) {
            logger(context).warn("custom_app_command_failed", mapOf("app_id" to id, "error_class" to exception.javaClass.simpleName))
            return DikcizLocalCommands.failure(requestId, "custom_app_command_failed", "App command could not complete")
        }
    }

    private fun onMainThread(call: () -> JSONObject): JSONObject {
        val task = FutureTask(call)
        main.post(task)
        return try { task.get(DikcizAutomationControlPlane.UI_THREAD_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS) }
        catch (exception: ExecutionException) { throw exception.cause ?: exception }
        catch (exception: TimeoutException) {
            task.cancel(false)
            throw DikcizAutomationRequestException(DikcizAutomationControlPlane.ERROR_TIMEOUT, DikcizAutomationControlPlane.MESSAGE_UI_THREAD_TIMEOUT).apply { initCause(exception) }
        } catch (exception: InterruptedException) {
            task.cancel(false)
            Thread.currentThread().interrupt()
            throw DikcizAutomationRequestException("interrupted", "App command wait was interrupted; an already started effect cannot be recalled").apply { initCause(exception) }
        }
    }

    private fun schedule(context: Context, app: DikcizCustomApp, request: JSONObject): JSONObject {
        requireEnabled(context, app)
        if (request.getString("action") == "cancelAll") {
            cancelTimers(context, app.id, "cancelled"); return JSONObject().put("state", "cancelled")
        }
        val timerId = request.getString("timerId")
        val key = app.id + ":" + timerId
        if (request.getString("action") == "cancel") {
            database(context).use { db -> db.get(TIMER_KIND, key)?.let { db.put(TIMER_KIND, key, it.put("state", "cancelled")) } }
            DikcizCustomAppService.wake(context)
            return JSONObject().put("state", "cancelled").put("timerId", timerId)
        }
        val saved = timers(context)
        if (saved.none { it.optString("id") == key && it.optString("state") == PENDING } && saved.count { it.optString("state") == PENDING } >= DikcizCustomAppCommands.MAXIMUM_TIMERS) {
            DikcizNativeCommands.failure("custom_app_timer_capacity", "Too many pending app timers")
        }
        val delay = request.getLong("delayMilliseconds")
        val timer = JSONObject().put("appId", app.id).put("timerId", timerId).put("id", key).put("generation", UUID.randomUUID().toString())
            .put("event", request.getString("event")).put("payload", request.optJSONObject("payload") ?: JSONObject()).put("state", PENDING)
            .put("boot", boot(context)).put("deadlineAt", System.currentTimeMillis() + delay).put("deadlineElapsed", SystemClock.elapsedRealtime() + delay)
        database(context).use { db ->
            if (saved.none { it.optString("id") == key } && saved.size >= MAXIMUM_TIMER_HISTORY) {
                val discard = saved.firstOrNull { it.optString("state") !in setOf(PENDING, CLAIMED) }
                    ?: DikcizNativeCommands.failure("custom_app_timer_capacity", "Timer history is full")
                db.delete(TIMER_KIND, discard.getString("id"))
            }
            db.put(TIMER_KIND, key, timer)
        }
        try { DikcizCustomAppService.start(context) }
        catch (exception: RuntimeException) {
            database(context).use { it.put(TIMER_KIND, key, timer.put("state", "needs_review").put("reason", "foreground_start_rejected")) }
            DikcizNativeCommands.failure("custom_app_foreground_required", "Open Dikciz before starting background app work")
        }
        return timer
    }

    @Synchronized fun timers(context: Context): List<JSONObject> = database(context).use { it.all(TIMER_KIND) }
    @Synchronized fun pending(context: Context, id: String? = null): Boolean = timers(context).any {
        it.optString("state") == PENDING && (id == null || it.optString("appId") == id)
    }
    @Synchronized fun cancelTimers(context: Context, id: String?, reason: String) {
        database(context).use { db -> timers(context).filter { it.optString("state") == PENDING && (id == null || it.optString("appId") == id) }
            .forEach { db.put(TIMER_KIND, it.getString("id"), it.put("state", "cancelled").put("reason", reason)) } }
    }

    @Synchronized fun deliverDue(context: Context) {
        for (timer in timers(context).filter { it.optString("state") == PENDING }) {
            if (timer.getInt("boot") != boot(context)) {
                finishTimer(context, timer, "needs_review", "device_rebooted"); continue
            }
            if (timer.getLong("deadlineElapsed") > SystemClock.elapsedRealtime()) continue
            finishTimer(context, timer, CLAIMED, "delivery_started")
            try {
                invoke(context, DikcizCustomAppStore(context).read(timer.getString("appId")), timer.getString("event"),
                    JSONObject(timer.getJSONObject("payload").toString()).put("timerId", timer.getString("timerId")).put("timerGeneration", timer.getString("generation")))
                finishTimer(context, timer, "completed", "handler_completed")
            } catch (exception: Exception) {
                finishTimer(context, timer, "needs_review", "handler_failed")
                logger(context).error("custom_app_timer_failed", mapOf("app_id" to timer.getString("appId"), "error_class" to exception.javaClass.simpleName))
            }
        }
    }

    private fun finishTimer(context: Context, timer: JSONObject, state: String, reason: String) {
        database(context).use { db ->
            val current = db.get(TIMER_KIND, timer.getString("id")) ?: return
            if (current.optString("generation") == timer.getString("generation")) {
                db.put(TIMER_KIND, timer.getString("id"), JSONObject(timer.toString()).put("state", state).put("reason", reason))
            }
        }
    }

    @Synchronized fun recover(context: Context) {
        val interrupted = mutableSetOf<String>()
        for (timer in timers(context)) {
            if (timer.optString("state") == CLAIMED || timer.optString("state") == PENDING && timer.optInt("boot") != boot(context)) {
                finishTimer(context, timer, "needs_review", "interrupted_or_rebooted")
                interrupted.add(timer.getString("appId"))
            }
        }
        for (id in interrupted) {
            try { invoke(context, DikcizCustomAppStore(context).read(id), "app.resume", JSONObject().put("interrupted", true)) }
            catch (exception: Exception) { logger(context).warn("custom_app_recovery_handler_failed", mapOf("app_id" to id, "error_class" to exception.javaClass.simpleName)) }
        }
    }

    @Synchronized fun resume(context: Context) {
        ensureBundled(context)
        refreshSubscriptions(context)
        recover(context)
        if (pending(context) || alert(context) != null) DikcizCustomAppService.start(context)
    }

    @Synchronized fun pausePending(context: Context) {
        val ids = timers(context).filter { it.optString("state") == PENDING }.map { it.getString("appId") }.toMutableSet()
        alert(context)?.let { ids.add(it.getString("appId")) }
        for (id in ids) {
            try { invoke(context, DikcizCustomAppStore(context).read(id), "app.stopped", JSONObject().put("reason", "owner_paused"), stopping = true) }
            catch (exception: Exception) { logger(context).warn("custom_app_pause_handler_failed", mapOf("app_id" to id, "error_class" to exception.javaClass.simpleName)) }
            database(context).use { it.put(INSTALL_KIND, id, JSONObject().put("enabled", false)) }
        }
        cancelTimers(context, null, "owner_paused")
        closeAlert(context, null)
        refreshSubscriptions(context)
    }

    @Synchronized fun onEvent(context: Context, event: DikcizAutomationEvent) {
        val matches = appSubscriptions.filter { it.subscription.matches(event) }
        if (matches.isEmpty()) return
        val store = DikcizCustomAppStore(context)
        for ((id, subscriptions) in matches.groupBy(AppSubscription::appId)) {
            try {
                val at = SystemClock.elapsedRealtime()
                val ready = subscriptions.filter { item ->
                    at - (lastDispatch[id + ":" + item.index] ?: Long.MIN_VALUE / 2) >= item.subscription.minimumIntervalMilliseconds
                }
                if (ready.isEmpty()) continue
                val app = store.read(id)
                if (isEnabled(context, app)) {
                    ready.forEach { lastDispatch[id + ":" + it.index] = at }
                    invoke(context, app, event.name, event.payload)
                    main.post { DikcizCustomAppActivity.current()?.deliverEvent(id, event.toDocument()) }
                }
            } catch (exception: Exception) {
                logger(context).warn("custom_app_subscription_failed", mapOf("app_id" to id, "error_class" to exception.javaClass.simpleName))
            }
        }
    }

    private fun showAlert(context: Context, app: DikcizCustomApp, request: JSONObject): JSONObject {
        requireEnabled(context, app)
        if (request.getString("action") == "close") { closeAlert(context, app.id); return JSONObject().put("state", "closed") }
        val previous = alert(context)
        if (previous != null && previous.getString("appId") != app.id) DikcizNativeCommands.failure("custom_app_alert_busy", "Another app already owns the urgent alert")
        DikcizSosWarning.createChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled() || manager.getNotificationChannel(DikcizSosWarning.CHANNEL).importance < NotificationManager.IMPORTANCE_HIGH) {
            DikcizNativeCommands.failure("custom_app_notifications_required", "Enable notifications and the urgent countdown channel before showing an alert")
        }
        val value = JSONObject(request.toString()).put("appId", app.id).put("runId", app.id).put("boot", boot(context))
            .put("deadlineAt", System.currentTimeMillis() + request.getLong("durationMilliseconds"))
            .put("deadlineElapsed", SystemClock.elapsedRealtime() + request.getLong("durationMilliseconds"))
            .put("graceSeconds", request.getLong("durationMilliseconds") / DikcizCustomAppCommands.SECOND)
        database(context).use { it.put(ALERT_KIND, "current", value) }
        alertRevision.incrementAndGet()
        DikcizCustomAppService.start(context)
        return JSONObject().put("state", "requested").put("episodeId", value.getString("episodeId"))
    }

    @Synchronized fun alert(context: Context): JSONObject? = database(context).use { it.get(ALERT_KIND, "current") }
    @Synchronized fun closeAlert(context: Context, id: String?) {
        val current = alert(context) ?: return
        if (id == null || current.optString("appId") == id) {
            database(context).use { it.delete(ALERT_KIND, "current") }
            alertRevision.incrementAndGet()
            main.post { DikcizSosWarning.current()?.close() }
        }
    }

    @Synchronized fun locationMonitoring(context: Context): Boolean = timers(context).filter { it.optString("state") == PENDING }.any {
        val app = DikcizCustomAppStore(context).read(it.getString("appId"))
        isEnabled(context, app) && app.manifest.optBoolean("locationMonitoring")
    }

    fun runtime(context: Context): JSONObject {
        DikcizSosWarning.createChannel(context)
        val notifications = context.getSystemService(NotificationManager::class.java)
        return JSONObject().put("nowMilliseconds", System.currentTimeMillis()).put("elapsedMilliseconds", SystemClock.elapsedRealtime()).put("boot", boot(context))
            .put("invocationId", UUID.randomUUID().toString())
            .put("androidAccess", DikcizAutomationStatus.androidAccessDocument(context)).put("notificationsEnabled", notifications.areNotificationsEnabled())
            .put("urgentChannelEnabled", notifications.getNotificationChannel(DikcizSosWarning.CHANNEL).importance >= NotificationManager.IMPORTANCE_HIGH)
            .put("backgroundRunning", DikcizCustomAppService.running)
            .put("exactAlarmsAllowed", Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms())
    }

    private fun parseSubscriptions(context: Context, manifest: JSONObject, limits: DikcizLimits): List<DikcizAutomationSubscription> {
        val values = JSONArray()
        manifest.getJSONArray("events").strings().forEach { values.put(JSONObject().put("event", it).put("minimumIntervalMilliseconds", 0)) }
        manifest.optJSONArray("eventSubscriptions")?.let { list -> repeat(list.length()) { values.put(list.get(it)) } }
        return try { HomeConfigStore(context).parseHtmlEventSubscriptions(JSONObject().put("eventSubscriptions", values), limits) }
        catch (exception: HomeConfigException) { throw DikcizAutomationRequestException("validation_failed", "Invalid app event subscription: " + exception.message).apply { initCause(exception) } }
    }

    @Synchronized private fun refreshSubscriptions(context: Context) {
        val store = DikcizCustomAppStore(context)
        val configuration = HomeConfigStore(context).loadOrCreate()
        val entries = mutableListOf<AppSubscription>()
        for (id in store.ids()) {
            try {
                val manifest = store.manifest(id)
                val app = DikcizCustomApp(manifest, "", "", "", "", JSONObject())
                if (isEnabled(context, app)) parseSubscriptions(context, manifest, configuration.limits).forEachIndexed { index, subscription -> entries.add(AppSubscription(id, index, subscription)) }
                else {
                    cancelTimers(context, id, "app_stopped")
                    closeAlert(context, id)
                }
            } catch (exception: Exception) {
                cancelTimers(context, id, "invalid_manifest")
                closeAlert(context, id)
                logger(context).warn("custom_app_subscriptions_rejected", mapOf("app_id" to id, "error_class" to exception.javaClass.simpleName))
            }
        }
        val changed = appSubscriptions != entries
        appSubscriptions = entries.toList()
        sourceSubscriptions = entries.map(AppSubscription::subscription)
        lastDispatch.keys.retainAll(entries.map { it.appId + ":" + it.index }.toSet())
        subscriptionObservers.forEach(FileObserver::stopWatching)
        subscriptionObservers.clear()
        val root = store.directory("watch-root").parentFile!!
        for (directory in listOf(root) + store.ids().map(store::directory)) {
            @Suppress("DEPRECATION")
            val observer = object : FileObserver(directory.absolutePath, SUBSCRIPTION_WATCH_EVENTS) {
                override fun onEvent(event: Int, name: String?) {
                    if (event and SUBSCRIPTION_WATCH_EVENTS == 0 || directory != root && name != null && name != "app.json") return
                    main.post {
                        subscriptionRefresh?.let(main::removeCallbacks)
                        subscriptionRefresh = Runnable { lifecycleWorker.execute { refreshSubscriptions(context.applicationContext) } }
                        main.postDelayed(subscriptionRefresh!!, SUBSCRIPTION_DEBOUNCE_MILLISECONDS)
                    }
                }
            }
            observer.startWatching(); subscriptionObservers.add(observer)
        }
        if (changed) main.post {
            DikcizClipboardMonitor.sync(context.applicationContext, configuration)
            try { context.startForegroundService(Intent(context, DikcizAutomationService::class.java)) }
            catch (exception: RuntimeException) { logger(context).warn("custom_app_event_sources_rejected", mapOf("error_class" to exception.javaClass.simpleName)) }
        }
    }

    private fun changed(context: Context, id: String, registry: Boolean = false) {
        if (registry) refreshSubscriptions(context)
        main.post { DikcizCustomAppActivity.current()?.stateChanged(id) }
    }

    const val SAFETY_ID = "sos"
    private const val MAXIMUM_TIMER_HISTORY = 256
    private const val SUBSCRIPTION_DEBOUNCE_MILLISECONDS = 150L
    private const val SUBSCRIPTION_WATCH_EVENTS = FileObserver.CLOSE_WRITE or FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_TO or FileObserver.MOVED_FROM or FileObserver.DELETE_SELF or FileObserver.MOVE_SELF
}
