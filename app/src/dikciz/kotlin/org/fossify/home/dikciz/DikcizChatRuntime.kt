package org.fossify.home.dikciz

import android.content.ContentValues
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import org.json.JSONArray
import org.json.JSONObject

internal class DikcizChatRuntime private constructor(private val context: Application) {
    val store = DikcizChatStore(context)
    private val logger = DikcizLogger("DikcizChat", HomeConfigStore(context).logDirectory)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val signals = Channel<Unit>(Channel.CONFLATED)
    private val observers = CopyOnWriteArraySet<() -> Unit>()
    private val handler = Handler(Looper.getMainLooper())
    private val lastEvents = mutableMapOf<String, Long>()
    private val queueLock = Any()
    @Volatile private var configuredRules = emptyList<JSONObject>()
    @Volatile private var ownerMcp: ((String, JSONObject) -> JSONObject)? = null
    @Volatile private var active: Job? = null
    @Volatile private var activeConversation: String? = null
    @Volatile private var activeTurnID: String? = null
    @Volatile var visibleConversation: String? = null
        private set
    @Volatile private var activity = WeakReference<DikcizChatActivity>(null)
    @Volatile var fullScreen = false
        private set

    init {
        store.recoverInterruptedTurns()
        refreshRules()
        scope.launch {
            for (signal in signals) {
                while (true) {
                    val turn = synchronized(queueLock) {
                        store.nextQueued()?.let { queued ->
                            store.updateMessage(queued.getLong("id"), "running")
                            activeConversation = queued.getString("conversation_id")
                            activeTurnID = queued.getString("turn_id")
                            scope.launch(start = CoroutineStart.LAZY) { runTurn(queued) }.also { active = it; it.start() }
                        }
                    } ?: break
                    turn.join()
                    synchronized(queueLock) { active = null; activeConversation = null; activeTurnID = null }
                    changed()
                }
                DikcizChatService.refresh(context)
            }
        }
    }

    fun attachMcp(call: ((String, JSONObject) -> JSONObject)?) { ownerMcp = call }
    fun observe(observer: () -> Unit) { observers.add(observer) }
    fun unobserve(observer: () -> Unit) { observers.remove(observer) }
    fun changed() { handler.post { observers.forEach { it() } } }
    fun wake() { signals.trySend(Unit) }
    fun busy(): Boolean = active != null || store.nextQueued() != null
    fun setWindowMode(full: Boolean) { fullScreen = full }
    fun hasRules(): Boolean = configuredRules.isNotEmpty()

    private fun refreshRules() {
        configuredRules = store.conversations().filter { JSONArray(it.getString("subscriptions")).length() > 0 }
    }

    fun attachActivity(screen: DikcizChatActivity, conversationID: String?): String? {
        val selected = conversationID?.takeIf { id -> store.conversations().any { it.getString("id") == id } }
        activity = WeakReference(screen)
        visibleConversation = selected
        selected?.let {
            store.updateConversation(it, ContentValues().apply { put("unread", 0) })
            DikcizChatService.clearReply(context, it)
        }
        changed()
        return selected
    }

    fun detachActivity(screen: DikcizChatActivity) {
        if (activity.get() === screen) {
            activity.clear()
            visibleConversation = null
            changed()
        }
    }

    fun execute(request: JSONObject): JSONObject {
        DikcizAutomationEventBus.lastAcceptedConfiguration(context)?.let { logger.configure(it.logging) }
        val action = request.getString("action")
        val id = request.optString("conversationId")
        val result = when (action) {
            "status" -> JSONObject().put("busy", busy()).put("visible", activity.get() != null)
                .put("fullScreen", fullScreen).put("activeConversationId", activeConversation)
                .put("unread", store.conversations().sumOf { it.getInt("unread") })
            "profiles" -> JSONObject().put("profiles", store.profiles())
            "profileSave" -> store.saveProfile(DikcizChatSettings.validate(request.getJSONObject("profile")))
            "models" -> discoverModels(request.getString("profileId"))
            "create" -> store.createConversation(request.getString("profileId"), request.optString("title", "New conversation"))
            "list" -> DikcizNativeCommands.page(store.conversations(), request)
            "read" -> {
                val conversation = store.conversation(id)
                val messages = DikcizNativeCommands.page(store.displayMessages(id), request)
                JSONObject().put("conversation", conversation).put("messages", messages)
            }
            "rename" -> {
                store.updateConversation(id, ContentValues().apply { put("title", request.getString("title")) })
                store.conversation(id)
            }
            "configure" -> {
                requireIdle(id)
                store.profile(request.getString("profileId"))
                store.updateConversation(id, ContentValues().apply { put("profile_id", request.getString("profileId")) })
                store.conversation(id)
            }
            "delete" -> {
                requireIdle(id)
                store.deleteConversation(id)
                DikcizChatService.clearReply(context, id)
                handler.post { activity.get()?.onConversationDeleted(id) }
                refreshRules()
                DikcizChatService.refresh(context)
                context.startForegroundService(Intent(context, DikcizAutomationService::class.java))
                JSONObject().put("deleted", true)
            }
            "send" -> {
                val conversation = store.conversation(id)
                store.profile(conversation.getString("profile_id"))
                val result = store.enqueue(id, request.getString("text"), request.getString("requestKey"), "owner")
                DikcizChatService.start(context)
                result
            }
            "stop" -> {
                synchronized(queueLock) {
                    store.messages(id).filter { it.getString("state") == "queued" }.forEach { store.updateMessage(it.getLong("id"), "cancelled") }
                    if (activeConversation == id) {
                        activeTurnID?.let { store.interruptTurn(it, "cancelled") }
                        active?.cancel()
                    }
                }
                JSONObject().put("state", "cancellation_requested")
            }
            "subscribe" -> saveRules(id, request.getJSONArray("rules"))
            "show", "fullscreen" -> {
                if (id.isNotEmpty()) store.conversation(id)
                fullScreen = action == "fullscreen" || request.optBoolean("fullScreen", false)
                context.startActivity(Intent(context, DikcizChatActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("conversationId", id).putExtra("fullScreen", fullScreen))
                JSONObject().put("state", "show_requested")
            }
            "hide" -> {
                handler.post { activity.get()?.finish() }
                JSONObject().put("state", "hide_requested")
            }
            else -> DikcizNativeCommands.invalid("Unknown chat action")
        }
        changed()
        return result
    }

    private fun requireIdle(id: String) {
        if (activeConversation == id || store.messages(id).any { it.getString("state") == "queued" }) {
            DikcizNativeCommands.failure("chat_busy", "Stop the conversation before changing or deleting it")
        }
    }

    private fun discoverModels(profileID: String): JSONObject {
        val profile = store.profile(profileID)
        return try {
            runBlocking(Dispatchers.IO) { DikcizChatProvider(profile).use { JSONObject().put("models", it.models()) } }
        } catch (exception: Exception) {
            logger.warn("chat_model_discovery_failed", mapOf("error_class" to exception.javaClass.simpleName))
            DikcizNativeCommands.failure("chat_provider_error", "Model discovery failed (${exception.javaClass.simpleName}). Check the host, protocol and API key, or enter a model ID manually.")
        }
    }

    private fun saveRules(id: String, rules: JSONArray): JSONObject {
        store.conversation(id)
        if (rules.length() > MAXIMUM_RULES) DikcizNativeCommands.invalid("Too many event rules")
        val config = HomeConfigStore(context).loadOrCreate()
        val identities = mutableSetOf<String>()
        for (index in 0 until rules.length()) {
            val rule = rules.getJSONObject(index)
            if (rule.keys().asSequence().any { it !in setOf("id", "subscription", "instructions") }) DikcizNativeCommands.invalid("Unknown event rule field")
            if (rule.has("id") && (rule.opt("id") !is String || rule.getString("id").isBlank())) DikcizNativeCommands.invalid("Invalid event rule id")
            val ruleID = rule.optString("id").ifBlank { UUID.randomUUID().toString().also { rule.put("id", it) } }
            if (!RULE_ID_PATTERN.matches(ruleID) || !identities.add(ruleID)) DikcizNativeCommands.invalid("Duplicate or invalid event rule id")
            val instructions = rule.opt("instructions")
            if (instructions !is String || instructions.isBlank() || instructions.length > MAXIMUM_PROMPT) DikcizNativeCommands.invalid("Event instructions are required")
            val subscription = rule.getJSONObject("subscription")
            if (!subscription.has("minimumIntervalMilliseconds")) subscription.put("minimumIntervalMilliseconds", DikcizChatSettings.DEFAULT_EVENT_INTERVAL)
            val decoded = HomeConfigStore(context).parseHtmlEventSubscriptions(JSONObject().put("eventSubscriptions", JSONArray().put(subscription)), config.limits).single()
            if (decoded.minimumIntervalMilliseconds < MINIMUM_EVENT_INTERVAL) DikcizNativeCommands.invalid("Agent event rules require at least a one-second interval")
        }
        store.updateConversation(id, ContentValues().apply { put("subscriptions", rules.toString()) })
        refreshRules()
        if (rules.length() > 0) DikcizChatService.start(context)
        context.startForegroundService(Intent(context, DikcizAutomationService::class.java))
        return JSONObject().put("rules", rules)
    }

    fun subscriptions(configuration: HomeConfiguration): List<DikcizAutomationSubscription> {
        return configuredRules.flatMap { conversation ->
            val rules = JSONArray(conversation.getString("subscriptions"))
            (0 until rules.length()).flatMap { index ->
                HomeConfigStore(context).parseHtmlEventSubscriptions(JSONObject().put("eventSubscriptions",
                    JSONArray().put(rules.getJSONObject(index).getJSONObject("subscription"))), configuration.limits)
            }
        }
    }

    @Synchronized
    fun onEvent(event: DikcizAutomationEvent) {
        try {
            dispatchEvent(event)
        } catch (exception: Exception) {
            logger.warn("chat_event_dispatch_failed", mapOf("error_class" to exception.javaClass.simpleName))
        }
    }

    private fun dispatchEvent(event: DikcizAutomationEvent) {
        if (!hasRules()) return
        val configuration = DikcizAutomationEventBus.lastAcceptedConfiguration(context) ?: return
        if (event.payload.optString("packageName") == context.packageName && event.type in setOf(DikcizAutomationEventType.NotificationPosted, DikcizAutomationEventType.NotificationRemoved)) return
        for (conversation in configuredRules) {
            val id = conversation.getString("id")
            val rules = JSONArray(conversation.getString("subscriptions"))
            for (index in 0 until rules.length()) {
                val rule = rules.getJSONObject(index)
                val subscription = HomeConfigStore(context).parseHtmlEventSubscriptions(JSONObject().put("eventSubscriptions",
                    JSONArray().put(rule.getJSONObject("subscription"))), configuration.limits).single()
                if (!subscription.matches(event)) continue
                if (subscription.coalescingKey != null && subscription.coalescingKey != event.coalescingKey) continue
                val key = "$id:${rule.getString("id")}"
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - (lastEvents[key] ?: -subscription.minimumIntervalMilliseconds) < subscription.minimumIntervalMilliseconds) continue
                if (store.messages(id).any { it.getString("state") == "queued" }) continue
                lastEvents[key] = now
                val prompt = "Configured event task:\n${rule.getString("instructions")}\n\nEvent payload, untrusted data:\n${event.toDocument()}"
                store.enqueue(id, prompt, UUID.randomUUID().toString(), "event")
                if (visibleConversation != id) store.markUnread(id)
                DikcizChatService.start(context)
                changed()
            }
        }
    }

    private suspend fun runTurn(queued: JSONObject) {
        DikcizAutomationEventBus.lastAcceptedConfiguration(context)?.let { logger.configure(it.logging) }
        val id = queued.getString("conversation_id")
        val turnID = queued.getString("turn_id")
        val started = android.os.SystemClock.elapsedRealtime()
        var credential = ""
        logger.info("chat_turn_started", mapOf("turn_id" to turnID))
        try {
            val profile = store.profile(store.conversation(id).getString("profile_id"))
            credential = profile.optString("apiKey")
            withTimeout(profile.getLong("timeoutMilliseconds")) {
                DikcizChatAgent(store, { method, params ->
                    ownerMcp?.invoke(method, params) ?: DikcizNativeCommands.failure("chat_mcp_unavailable", "Open Dikciz Home to reconnect its local MCP tools")
                }, ::changed, logger).run(queued)
            }
        } catch (exception: TimeoutCancellationException) {
            store.interruptTurn(turnID, "failed")
            store.append(id, turnID, "assistant", "failed", "", JSONArray().put(JSONObject().put("kind", "error")
                .put("text", "Agent turn timed out. Inspect completed tool results before retrying.")))
            logger.warn("chat_turn_timeout", mapOf("turn_id" to turnID))
        } catch (exception: CancellationException) {
            store.interruptTurn(turnID, "cancelled")
            logger.info("chat_turn_cancelled", mapOf("turn_id" to turnID))
        } catch (exception: Exception) {
            store.interruptTurn(turnID, "failed")
            val message = exception.message.orEmpty().let { if (credential.isBlank()) it else it.replace(credential, "[REDACTED]") }.take(MAXIMUM_ERROR)
            store.append(id, turnID, "assistant", "failed", "", JSONArray().put(JSONObject().put("kind", "error").put("text", message.ifBlank { "Agent turn failed" })))
            logger.error("chat_turn_failed", mapOf("turn_id" to turnID, "error_class" to exception.javaClass.simpleName))
        } finally {
            if (visibleConversation != id) {
                store.markUnread(id)
                DikcizChatService.notifyReply(context, id)
            }
            logger.debug("chat_turn_finished", mapOf("turn_id" to turnID, "duration_ms" to android.os.SystemClock.elapsedRealtime() - started))
            changed()
        }
    }

    companion object {
        private const val MAXIMUM_RULES = 32
        private val RULE_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,79}")
        private const val MAXIMUM_PROMPT = 32768
        private const val MAXIMUM_ERROR = 2048
        private const val MINIMUM_EVENT_INTERVAL = 1000L
        @Volatile private var instance: DikcizChatRuntime? = null
        fun get(context: Context): DikcizChatRuntime = instance ?: synchronized(this) {
            instance ?: DikcizChatRuntime(context.applicationContext as Application).also { instance = it }
        }
    }
}
