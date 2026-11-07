package org.fossify.home.dikciz

import org.json.JSONArray
import org.json.JSONObject

/** One command contract for custom application installation, execution and host services. */
internal object DikcizCustomAppCommands {
    const val LIST = "customAppsList"
    const val READ = "customAppRead"
    const val INSTALL = "customAppInstall"
    const val CONTROL = "customAppControl"
    const val EVENT = "customAppEvent"
    const val STATE = "customAppState"
    const val SCHEDULE = "customAppSchedule"
    const val ALERT = "customAppAlert"
    const val RUNTIME = "customAppRuntime"
    const val MAXIMUM_SOURCE_BYTES = 262144
    const val MAXIMUM_STATE_BYTES = 131072
    const val MAXIMUM_APPS = 64
    const val MAXIMUM_TIMERS = 64
    const val SECOND = 1000L
    const val MAXIMUM_DELAY = 604800000L
    val ID_PATTERN = Regex("[A-Za-z][A-Za-z0-9_-]{0,63}")
    val EVENT_PATTERN = Regex("[A-Za-z][A-Za-z0-9_.-]{0,95}")
    private fun text(maximum: Int) = JSONObject().put("type", "string").put("maxLength", maximum)
    private fun choice(vararg values: String) = text(96).put("enum", JSONArray(values))
    private fun integer(minimum: Long, maximum: Long) = JSONObject().put("type", "integer").put("minimum", minimum).put("maximum", maximum)
    private val objectValue = JSONObject().put("type", "object")
    private val id = mapOf("appId" to text(64).put("pattern", ID_PATTERN.pattern))
    private val confirmation = mapOf("confirm" to JSONObject().put("type", "boolean"))
    private val manifest = JSONObject().put("type", "object").put("additionalProperties", false)
        .put("properties", JSONObject().put("version", integer(1, 1)).put("id", text(64).put("pattern", ID_PATTERN.pattern))
            .put("title", text(120).put("minLength", 1)).put("description", text(2000).put("minLength", 1))
            .put("language", choice("lua", "javascript"))
            .put("commands", JSONObject().put("description", "Optional legacy metadata; apps inherit Dikciz Android access without command restrictions.").put("type", "array").put("maxItems", 128).put("uniqueItems", true).put("items", text(96).put("pattern", EVENT_PATTERN.pattern)))
            .put("events", JSONObject().put("type", "array").put("maxItems", 128).put("uniqueItems", true).put("items", text(96).put("pattern", EVENT_PATTERN.pattern)))
            .put("eventSubscriptions", JSONObject().put("type", "array").put("maxItems", 128).put("items", objectValue)
                .put("description", "Canonical event subscription objects, as in HTML widget eventSubscriptions. Use event, minimumIntervalMilliseconds and event-specific sensorTypes/samplingPeriodMicroseconds/locationPrecision/alarmIntervalMilliseconds/healthRefreshIntervalMilliseconds/packageNames. See the event recipe."))
            .put("locationMonitoring", JSONObject().put("type", "boolean")))
        .put("required", JSONArray(listOf("version", "id", "title", "description", "language", "events")))
    val DEFINITIONS = listOf(
        DikcizNativeCommands.Definition(LIST, "dikciz_custom_apps_list", "List installed editable Dikciz apps. Follow hasMore; disabled apps cannot execute background code. Apps are independent of Android APKs.",
            mapOf("offset" to integer(0, 1000000), "limit" to integer(1, 200))),
        DikcizNativeCommands.Definition(READ, "dikciz_custom_app_read", "Read one app manifest, full HTML/CSS/JS, optional Lua/JS background source, state, run status and durable timers. Sources live under /sdcard/Dikciz/apps/<appId>. This read can contain private app data and requires an explicit operation grant for scoped remote clients.", id, setOf("appId")),
        DikcizNativeCommands.Definition(INSTALL, "dikciz_custom_app_install", "Install or replace a complete app bundle after confirm=true. manifest has version:1,id,title,description,language (lua/javascript), optional commands (legacy metadata), events (event names), optional locationMonitoring boolean. Sources use html/css/javascript/backgroundSource. New apps start enabled and inherit Dikciz Android access. Replacing requires no pending timers or alert and preserves data and running/stopped state. All entrypoints share validation.",
            mapOf("manifest" to manifest, "html" to text(MAXIMUM_SOURCE_BYTES), "css" to text(MAXIMUM_SOURCE_BYTES),
                "javascript" to text(MAXIMUM_SOURCE_BYTES), "backgroundSource" to text(MAXIMUM_SOURCE_BYTES)) + confirmation,
            setOf("manifest", "html", "confirm"), false),
        DikcizNativeCommands.Definition(CONTROL, "dikciz_custom_app_control", "open starts an app and opens its UI; enable starts it without permission approval; disable stops it and cancels timers and alerts; remove requires confirm=true and deletes only this app's directory; export writes a bounded .dikciz-app.zip; import requires path and confirm=true and installs a new enabled app. Apps inherit Dikciz Android access. Export excludes state and runtime files. To copy an app, install its source with a different manifest.id for an independent instance.",
            id + mapOf("action" to choice("open", "enable", "disable", "remove", "export", "import"), "path" to text(4096)) + confirmation,
            setOf("action"), false),
        DikcizNativeCommands.Definition(EVENT, "dikciz_custom_app_event", "Synchronously invoke an enabled app's Lua/JS on_event/onEvent handler. event is a name, payload an object. Returns outcome/status/result and current state. A handler result.error with code/message becomes the canonical command error. Shared budgets apply. Calls serialize with timers and cancellation. interruptRunning=true blocks subsequent effects from an already running handler before this event acquires its execution lock. No automatic retry of effects.",
            id + mapOf("event" to text(96).put("pattern", EVENT_PATTERN.pattern), "payload" to objectValue,
                "interruptRunning" to JSONObject().put("type", "boolean").put("description", "Immediately refuse further mutating host calls from the running handler before queuing this event, for cancellation controls.")), setOf("appId", "event"), false),
        DikcizNativeCommands.Definition(STATE, "dikciz_custom_app_state", "Read or atomically replace app state.json. replace requires state. Backends and UI share this state. Script patchState actions merge keys. Use customAppRead for a read-only agent. App bindings default to their own appId and may explicitly address another app.",
            id + mapOf("action" to choice("read", "replace"), "state" to objectValue), setOf("appId", "action"), false),
        DikcizNativeCommands.Definition(SCHEDULE, "dikciz_custom_app_schedule", "Schedule/cancel one durable app timer by timerId, or cancelAll. schedule needs delayMilliseconds (1000..604800000), event and optional payload. Same ID replaces only pending work; a unique generation identifies each delivery. Android can delay alarms. A claimed timer is never automatically replayed after a crash; interrupted/rebooted work needs review. Timer cancellation cannot recall submitted effects. Scheduling starts a visible service with Stop.",
            id + mapOf("action" to choice("schedule", "cancel", "cancelAll"), "timerId" to text(64).put("pattern", ID_PATTERN.pattern),
                "delayMilliseconds" to integer(SECOND, MAXIMUM_DELAY), "event" to text(96).put("pattern", EVENT_PATTERN.pattern), "payload" to objectValue),
            setOf("appId", "action"), false),
        DikcizNativeCommands.Definition(ALERT, "dikciz_custom_app_alert", "Show/close a reusable dimmed urgent countdown modal with a notification fallback and cancel button. show needs episodeId, durationMilliseconds, title, detail, cancelLabel, cancelEvent. Optional announcement/finalAnnouncement are spoken via offline TTS, {seconds} is substituted. Cancellation dispatches cancelEvent to this app. Requires enabled notifications and high-priority alert channel. The response reports requested presentation; inspect customAppRead.alert for observed state. Android protected screens may hide overlays.",
            id + mapOf("action" to choice("show", "close"), "episodeId" to text(120), "durationMilliseconds" to integer(SECOND, 3600000),
                "title" to text(120), "detail" to text(2000), "cancelLabel" to text(80), "cancelEvent" to text(96).put("pattern", EVENT_PATTERN.pattern),
                "announcement" to text(2000), "finalAnnouncement" to text(2000)), setOf("appId", "action"), false),
        DikcizNativeCommands.Definition(RUNTIME, "dikciz_custom_app_runtime", "Read native clock/boot identity, Android access, notification/channel availability and app service state. Elapsed time is monotonic within boot. Does not grant access or start effects.", emptyMap()),
    )
    val TYPES = DEFINITIONS.map { it.type }.toSet()
    val SCOPED = setOf(READ, EVENT, STATE, SCHEDULE, ALERT)

    fun validate(type: String, request: JSONObject) {
        if (type !in setOf(LIST, INSTALL, RUNTIME) && request.has("appId") && !ID_PATTERN.matches(request.getString("appId"))) {
            DikcizNativeCommands.invalid("Invalid appId")
        }
        when (type) {
            STATE -> if (request.getString("action") == "replace" && !request.has("state")) DikcizNativeCommands.invalid("replace requires state")
            SCHEDULE -> {
                val required = when (request.getString("action")) {
                    "schedule" -> setOf("timerId", "event", "delayMilliseconds")
                    "cancel" -> setOf("timerId")
                    else -> emptySet()
                }
                if (required.any { !request.has(it) }) DikcizNativeCommands.invalid("Timer action is missing required fields")
            }
            ALERT -> if (request.getString("action") == "show" && setOf("episodeId", "durationMilliseconds", "title", "detail", "cancelLabel", "cancelEvent").any { !request.has(it) }) {
                DikcizNativeCommands.invalid("Alert show is missing required fields")
            }
            CONTROL -> if (request.getString("action") == "import") {
                if (!request.has("path") || !request.optBoolean("confirm")) DikcizNativeCommands.invalid("import requires path and confirm=true")
            } else if (!request.has("appId")) DikcizNativeCommands.invalid("App action requires appId")
        }
    }
}
