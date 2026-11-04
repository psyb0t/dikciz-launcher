package org.fossify.home.dikciz

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal object DikcizJourneys {
    const val PLAN = "sosPlanSave"
    const val PREVIEW = "sosPreview"
    const val STATUS = "sosStatus"
    const val CONTROL = "sosControl"
    const val FENCES = "geofencesList"
    const val FENCE_SAVE = "geofenceSave"
    const val FENCE_DELETE = "geofenceDelete"
    const val TRIP_CONTROL = "journeyControl"
    const val TRIPS = "journeysList"
    const val TRIP_READ = "journeyRead"
    const val TRIP_DELETE = "journeyDelete"
    private const val PLAN_KIND = "sos-plan"
    private const val RUN_KIND = "sos-run"
    private const val FENCE_KIND = "geofence"
    private const val TRIP_KIND = "trip"
    private const val SINGLE = "current"
    private const val SECOND = 1000L
    private const val MAXIMUM_AGE = 120000L
    private const val MAXIMUM_POINTS = 10000
    private const val MAXIMUM_TRIPS = 64
    private const val MAXIMUM_FENCES = 32
    private val ACTIVE_SOS = setOf("armed", "warning", "active")
    private val ID = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,79}")
    private var storage: DikcizCompanionStore? = null
    private var lastFix: Location? = null
    private fun db(context: Context) = storage ?: DikcizCompanionStore(context).also { storage = it }
    private fun now() = System.currentTimeMillis()
    private fun boot(context: Context) = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private fun identifier(request: JSONObject): String {
        val id = request.getString("id")
        if (!ID.matches(id)) DikcizNativeCommands.invalid("Invalid resource id")
        return id
    }
    private fun requireLocation(context: Context) {
        DikcizNativeCommands.requirePermission(context, Manifest.permission.ACCESS_FINE_LOCATION, "locationForeground")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            DikcizNativeCommands.requirePermission(context, Manifest.permission.POST_NOTIFICATIONS, "notifications")
        }
    }
    private fun start(context: Context) {
        try {
            context.startForegroundService(Intent(context, DikcizJourneyService::class.java))
        } catch (exception: RuntimeException) {
            DikcizNativeCommands.failure("journey_foreground_required", "Open Dikciz and allow foreground location monitoring before starting")
        }
    }
    @Synchronized
    fun resume(context: Context) {
        val store = db(context)
        val run = store.get(RUN_KIND, SINGLE)
        if (run != null && run.optString("state") in ACTIVE_SOS && run.optInt("boot") != boot(context)) {
            run.put("state", "needs_review").put("reason", "device_rebooted")
            store.put(RUN_KIND, SINGLE, run)
        }
        store.all(TRIP_KIND).filter { it.optString("state") == "recording" && it.optInt("boot") != boot(context) }.forEach {
            it.put("state", "interrupted").put("reason", "device_rebooted")
            store.put(TRIP_KIND, it.getString("id"), it)
        }
        if (active(context)) start(context)
    }
    @Synchronized
    fun deadline(context: Context): Long = db(context).get(RUN_KIND, SINGLE)?.takeIf {
        it.optString("state") in ACTIVE_SOS
    }?.optLong("deadlineAt", 0) ?: 0

    @Synchronized
    fun warningState(context: Context): JSONObject? {
        val store = db(context)
        val run = store.get(RUN_KIND, SINGLE) ?: return null
        if (run.optString("state") !in setOf("warning", "active")) return null
        val plan = store.get(PLAN_KIND, SINGLE) ?: return null
        val remaining = (run.getLong("deadlineElapsed") - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        val grace = plan.getLong("graceSeconds")
        if (run.getString("state") == "active" && remaining > grace * SECOND) return null
        return JSONObject().put("runId", run.getString("id"))
            .put("episodeId", run.getString("id") + "_" + run.getInt("updates"))
            .put("remainingSeconds", (remaining + SECOND - 1) / SECOND)
            .put("deadlineAt", run.getLong("deadlineAt")).put("graceSeconds", grace)
            .put("recipientCount", plan.getJSONArray("contacts").length())
    }
    @Synchronized
    fun active(context: Context): Boolean = db(context).let { store ->
        store.get(RUN_KIND, SINGLE)?.optString("state") in ACTIVE_SOS ||
            store.all(FENCE_KIND).any { it.optBoolean("enabled") } ||
            store.all(TRIP_KIND).any { it.optString("state") == "recording" }
    }
    @Synchronized
    fun execute(context: Context, type: String, request: JSONObject): JSONObject {
        val store = db(context)
        return when (type) {
            PLAN -> {
                if (store.get(RUN_KIND, SINGLE)?.optString("state") in ACTIVE_SOS) {
                    DikcizNativeCommands.failure("sos_active", "Cancel the active check-in before changing its plan")
                }
                val plan = JSONObject(request.toString()).apply { remove("type"); remove("requestId"); remove("autoRequestPermissions") }
                val contacts = plan.getJSONArray("contacts")
                val seen = mutableSetOf<String>()
                for (index in 0 until contacts.length()) {
                    val contact = contacts.getJSONObject(index)
                    val address = DikcizNativeCommands.normalizedRecipient(contact.getString("address"))
                    if (!seen.add(address)) DikcizNativeCommands.invalid("Duplicate SOS address")
                    contact.put("address", address)
                }
                plan.put("checkInSeconds", request.optInt("checkInSeconds", 3600))
                    .put("graceSeconds", request.optInt("graceSeconds", 60))
                    .put("updateSeconds", request.optInt("updateSeconds", 600))
                    .put("maximumUpdates", request.optInt("maximumUpdates", 3))
                    .put("message", request.optString("message", "I need help. Last known location: {location}. Accuracy: {accuracyMeters} m. Fix time: {timestamp}."))
                if (plan.getString("message").isBlank()) DikcizNativeCommands.invalid("SOS message cannot be blank")
                store.put(PLAN_KIND, SINGLE, plan)
                JSONObject().put("state", "saved").put("armed", false).put("plan", plan)
            }
            STATUS -> JSONObject().put("plan", store.get(PLAN_KIND, SINGLE) ?: JSONObject.NULL)
                .put("run", store.get(RUN_KIND, SINGLE) ?: JSONObject.NULL)
                .put("backgroundMonitoring", DikcizJourneyService.running)
                .put("warning", DikcizSosWarning.status())
            PREVIEW -> {
                val plan = store.get(PLAN_KIND, SINGLE) ?: DikcizNativeCommands.failure("sos_plan_missing", "Save an SOS contact plan first")
                val fix = lastFix
                JSONObject().put("dryRun", true).put("smsSubmitted", false).put("contacts", plan.getJSONArray("contacts"))
                    .put("message", message(plan, fix)).put("location", fix?.let(::point) ?: JSONObject.NULL)
            }
            CONTROL -> control(context, request)
            FENCES -> DikcizNativeCommands.page(store.all(FENCE_KIND), request)
            FENCE_SAVE -> {
                val id = request.optString("id").ifBlank { UUID.randomUUID().toString() }
                if (!ID.matches(id)) DikcizNativeCommands.invalid("Invalid geofence id")
                val old = store.get(FENCE_KIND, id)
                if (old == null && store.all(FENCE_KIND).size >= MAXIMUM_FENCES) DikcizNativeCommands.failure("geofence_capacity", "At most 32 geofences can be saved")
                val enabled = request.optBoolean("enabled", false)
                if (enabled) requireLocation(context)
                val fence = JSONObject().put("id", id).put("title", request.getString("title"))
                    .put("latitude", request.getDouble("latitude")).put("longitude", request.getDouble("longitude"))
                    .put("radiusMeters", request.getDouble("radiusMeters")).put("enabled", enabled).put("state", "unknown")
                    .put("dwellMilliseconds", request.optLong("dwellMilliseconds", 0))
                if (old != null && listOf("latitude", "longitude", "radiusMeters").all { old.getDouble(it) == fence.getDouble(it) } &&
                    old.getBoolean("enabled") == fence.getBoolean("enabled") &&
                    old.getLong("dwellMilliseconds") == fence.getLong("dwellMilliseconds")) {
                    listOf("state", "changedAt", "changedElapsed", "observedBoot", "dwellEmitted").forEach { key ->
                        old.opt(key)?.let { fence.put(key, it) }
                    }
                }
                store.put(FENCE_KIND, id, fence)
                if (enabled) {
                    try { start(context) }
                    catch (exception: DikcizAutomationRequestException) {
                        store.put(FENCE_KIND, id, old ?: fence.put("enabled", false))
                        throw exception
                    }
                }
                fence
            }
            FENCE_DELETE -> {
                val id = identifier(request)
                if (store.get(FENCE_KIND, id) == null) DikcizNativeCommands.failure("native_record_not_found", "Geofence not found")
                store.delete(FENCE_KIND, id)
                JSONObject().put("deleted", true).put("id", id)
            }
            TRIP_CONTROL -> {
                val action = request.getString("action")
                if (action == "start") {
                    requireLocation(context)
                    if (store.all(TRIP_KIND).any { it.optString("state") == "recording" }) DikcizNativeCommands.failure("journey_active", "Stop the current journey before starting another")
                    if (store.all(TRIP_KIND).size >= MAXIMUM_TRIPS) DikcizNativeCommands.failure("journey_capacity", "Delete a saved journey before adding another")
                    val id = UUID.randomUUID().toString()
                    val trip = JSONObject().put("id", id).put("title", request.optString("title", "Journey"))
                        .put("state", "recording").put("boot", boot(context)).put("startedAt", now()).put("pointCount", 0)
                        .put("sampleIntervalMilliseconds", request.optLong("sampleIntervalMilliseconds", 10000))
                        .put("distanceMeters", 0.0)
                    store.put(TRIP_KIND, id, trip)
                    try { start(context) }
                    catch (exception: DikcizAutomationRequestException) {
                        trip.put("state", "interrupted").put("reason", exception.code).put("endedAt", now())
                        store.put(TRIP_KIND, id, trip)
                        throw exception
                    }
                    trip
                } else if (action == "stop") {
                    val id = identifier(request)
                    val trip = store.get(TRIP_KIND, id) ?: DikcizNativeCommands.failure("native_record_not_found", "Journey not found")
                    trip.put("state", "completed").put("endedAt", now())
                    store.put(TRIP_KIND, id, trip); trip
                } else DikcizNativeCommands.invalid("Journey action must be start or stop")
            }
            TRIPS -> DikcizNativeCommands.page(store.all(TRIP_KIND).sortedByDescending { it.getLong("startedAt") }, request)
            TRIP_READ -> {
                val id = identifier(request)
                val trip = store.get(TRIP_KIND, id) ?: DikcizNativeCommands.failure("native_record_not_found", "Journey not found")
                val offset = request.optInt("offset", 0); val limit = request.optInt("limit", 50)
                val points = store.points(id, offset, limit)
                JSONObject().put("journey", trip).put("items", JSONArray(points)).put("total", trip.getInt("pointCount"))
                    .put("hasMore", offset.toLong() + points.size < trip.getInt("pointCount"))
            }
            TRIP_DELETE -> {
                val id = identifier(request)
                val trip = store.get(TRIP_KIND, id) ?: DikcizNativeCommands.failure("native_record_not_found", "Journey not found")
                if (trip.optString("state") == "recording") DikcizNativeCommands.failure("journey_active", "Stop the journey before deleting its history")
                if (!request.optBoolean("confirm")) DikcizNativeCommands.invalid("Deleting history requires confirm=true")
                store.delete(TRIP_KIND, id); JSONObject().put("id", id).put("deleted", true)
            }
            else -> DikcizNativeCommands.invalid()
        }
    }
    private fun control(context: Context, request: JSONObject): JSONObject {
        val store = db(context)
        val action = request.getString("action")
        val previous = store.get(RUN_KIND, SINGLE)
        if (action == "cancel") {
            val value = previous ?: JSONObject()
            value.put("state", "cancelled").put("updatedAt", now())
            store.put(RUN_KIND, SINGLE, value)
            DikcizSosWarning.dismiss(value.optString("id"))
            return value
        }
        if (action == "checkIn") {
            val run = previous ?: DikcizNativeCommands.failure("sos_not_armed", "No check-in is armed")
            if (run.optString("state") !in ACTIVE_SOS) DikcizNativeCommands.failure("sos_not_armed", "No check-in is armed")
            val plan = store.get(PLAN_KIND, SINGLE)!!
            run.put("state", "armed").put("deadlineElapsed", SystemClock.elapsedRealtime() + plan.getLong("checkInSeconds") * SECOND)
                .put("deadlineAt", now() + plan.getLong("checkInSeconds") * SECOND).put("updatedAt", now())
            store.put(RUN_KIND, SINGLE, run)
            DikcizSosWarning.dismiss(run.getString("id"))
            return run
        }
        if (action !in setOf("arm", "trigger")) DikcizNativeCommands.invalid("Choose arm, trigger, checkIn or cancel")
        if (!request.optBoolean("confirm")) DikcizNativeCommands.invalid("Arming or triggering SMS requires confirm=true")
        if (previous?.optString("state") in ACTIVE_SOS) DikcizNativeCommands.failure("sos_active", "Use checkIn or cancel for the current run")
        val plan = store.get(PLAN_KIND, SINGLE) ?: DikcizNativeCommands.failure("sos_plan_missing", "Save an SOS contact plan first")
        requireLocation(context)
        if (!context.getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()) {
            DikcizNativeCommands.failure("sos_notifications_disabled", "Enable Dikciz notifications before arming safety monitoring")
        }
        DikcizSosWarning.createChannel(context)
        val channel = context.getSystemService(android.app.NotificationManager::class.java).getNotificationChannel(DikcizSosWarning.CHANNEL)
        if (channel.importance < android.app.NotificationManager.IMPORTANCE_HIGH) {
            DikcizNativeCommands.failure("sos_warning_channel_disabled", "Enable urgent SOS countdown alerts in Android notification settings before arming")
        }
        DikcizNativeCommands.requirePermission(context, Manifest.permission.SEND_SMS, "sms")
        val delay = plan.getLong(if (action == "arm") "checkInSeconds" else "graceSeconds") * SECOND
        val run = JSONObject().put("id", UUID.randomUUID().toString()).put("state", if (action == "arm") "armed" else "warning")
            .put("boot", boot(context)).put("startedAt", now()).put("updatedAt", now())
            .put("deadlineAt", now() + delay).put("deadlineElapsed", SystemClock.elapsedRealtime() + delay)
            .put("updates", 0).put("receipts", JSONArray())
        store.put(RUN_KIND, SINGLE, run)
        try { start(context) } catch (exception: DikcizAutomationRequestException) {
            run.put("state", "needs_review").put("reason", exception.code)
            store.put(RUN_KIND, SINGLE, run); throw exception
        }
        return run
    }
    @Synchronized
    fun tick(context: Context, logger: DikcizLogger): String {
        val store = db(context)
        lastFix?.let { sample(context, it) }
        val run = store.get(RUN_KIND, SINGLE) ?: return "Location monitoring active"
        if (run.optString("state") !in ACTIVE_SOS) return "Location monitoring active"
        if (run.optInt("boot") != boot(context)) {
            run.put("state", "needs_review").put("reason", "device_rebooted")
            store.put(RUN_KIND, SINGLE, run); return "Check-in needs review"
        }
        val remaining = run.getLong("deadlineElapsed") - SystemClock.elapsedRealtime()
        if (remaining > 0) return if (run.getString("state") == "warning") "SOS sends in " + (remaining / SECOND + 1) + " seconds" else "Check-in monitoring active"
        val plan = store.get(PLAN_KIND, SINGLE) ?: return "SOS plan unavailable"
        if (run.getString("state") == "armed") {
            run.put("state", "warning").put("deadlineElapsed", SystemClock.elapsedRealtime() + plan.getLong("graceSeconds") * SECOND)
                .put("deadlineAt", now() + plan.getLong("graceSeconds") * SECOND)
            store.put(RUN_KIND, SINGLE, run); emit(context, "sos.warning", JSONObject().put("runId", run.getString("id")))
            return "Missed check-in. Cancel or check in now"
        }
        val update = run.getInt("updates")
        if (update >= plan.getInt("maximumUpdates")) {
            run.put("state", "completed").put("updatedAt", now())
            store.put(RUN_KIND, SINGLE, run); return "SOS update limit reached"
        }
        val contacts = plan.getJSONArray("contacts")
        val receipts = run.getJSONArray("receipts")
        val fix = lastFix?.takeIf { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos <= MAXIMUM_AGE * 1000000 }
        val batch = run.optJSONArray("pendingBatch") ?: JSONArray().also { values ->
            for (index in 0 until contacts.length()) {
                values.put(JSONObject().put("recipient", contacts.getJSONObject(index).getString("address"))
                    .put("body", message(plan, fix))
                    .put("idempotencyKey", "sos_" + run.getString("id") + "_" + update + "_" + index)
                    .apply { plan.opt("subscriptionId")?.let { put("subscriptionId", it) } })
            }
            run.put("pendingBatch", values)
            store.put(RUN_KIND, SINGLE, run)
        }
        var accepted = 0
        for (index in 0 until batch.length()) {
            val send = batch.getJSONObject(index)
            val key = send.getString("idempotencyKey")
            try {
                val response = DikcizSmsStatus.send(context, send)
                receipts.put(JSONObject().put("idempotencyKey", key).put("state", response.optString("state")))
                if (response.optString("state") in setOf("accepted", "sent", "delivered")) accepted++
            } catch (exception: DikcizAutomationRequestException) {
                receipts.put(JSONObject().put("idempotencyKey", key).put("state", "rejected").put("code", exception.code))
                logger.warn("sos_send_rejected", mapOf("code" to exception.code))
            }
        }
        run.remove("pendingBatch")
        val state = when {
            accepted == 0 -> "needs_review"
            update + 1 >= plan.getInt("maximumUpdates") -> "completed"
            else -> "active"
        }
        run.put("updates", update + 1).put("state", state).put("updatedAt", now())
            .put("deadlineElapsed", SystemClock.elapsedRealtime() + plan.getLong("updateSeconds") * SECOND)
            .put("deadlineAt", now() + plan.getLong("updateSeconds") * SECOND)
        store.put(RUN_KIND, SINGLE, run)
        emit(context, "sos.update", JSONObject().put("runId", run.getString("id")).put("sequence", update + 1))
        return if (accepted == 0) "SOS submission failed. Review permissions and receipts" else "SOS update submitted. Check delivery receipts"
    }
    @Synchronized
    fun sample(context: Context, location: Location) {
        val age = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
        if (!location.hasAccuracy() || location.accuracy > 200 || location.accuracy < 0 ||
            !location.latitude.isFinite() || !location.longitude.isFinite() ||
            location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0 ||
            age < 0 || age > MAXIMUM_AGE * 1000000) return
        if (lastFix?.let { it.elapsedRealtimeNanos > location.elapsedRealtimeNanos } == true) return
        lastFix = Location(location)
        val store = db(context)
        val at = now()
        store.all(FENCE_KIND).filter { it.optBoolean("enabled") }.forEach { fence ->
            val distance = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, fence.getDouble("latitude"), fence.getDouble("longitude"), distance)
            val radius = fence.getDouble("radiusMeters")
            if (location.accuracy > radius / 2) return@forEach
            val previous = if (fence.optInt("observedBoot", -1) == boot(context)) fence.getString("state") else "unknown"
            val margin = maxOf(location.accuracy.toDouble(), 25.0)
            val inside = when {
                previous == "inside" -> distance[0] <= radius + margin
                previous == "outside" -> distance[0] < radius - margin
                else -> distance[0] <= radius
            }
            val state = if (inside) "inside" else "outside"
            if (state != previous) {
                fence.put("state", state).put("changedAt", at).put("changedElapsed", SystemClock.elapsedRealtime())
                    .put("observedBoot", boot(context)).put("dwellEmitted", false)
                if (inside || previous != "unknown") emit(context, "geofence." + if (inside) "enter" else "exit",
                    JSONObject().put("id", fence.getString("id")).put("title", fence.getString("title")).put("location", point(location)))
            }
            if (inside && !fence.optBoolean("dwellEmitted") && fence.getLong("dwellMilliseconds") > 0 &&
                SystemClock.elapsedRealtime() - fence.optLong("changedElapsed", SystemClock.elapsedRealtime()) >= fence.getLong("dwellMilliseconds")) {
                fence.put("dwellEmitted", true)
                emit(context, "geofence.dwell", JSONObject().put("id", fence.getString("id")).put("location", point(location)))
            }
            fence.put("distanceMeters", distance[0].toDouble()).put("observedAt", at)
            store.put(FENCE_KIND, fence.getString("id"), fence)
        }
        store.all(TRIP_KIND).filter { it.optString("state") == "recording" }.forEach { trip ->
            val elapsed = SystemClock.elapsedRealtime()
            if (location.elapsedRealtimeNanos <= trip.optLong("lastFixElapsed", -1) ||
                elapsed - trip.optLong("lastSampleElapsed", 0) < trip.getLong("sampleIntervalMilliseconds")) return@forEach
            val count = trip.getInt("pointCount")
            if (count >= MAXIMUM_POINTS) {
                trip.put("state", "capacity_reached").put("endedAt", at)
            } else {
                val value = point(location).put("sequence", count + 1)
                val previous = if (count > 0) store.points(trip.getString("id"), count - 1, 1).firstOrNull() else null
                if (previous != null) {
                    val distance = FloatArray(1)
                    Location.distanceBetween(previous.getDouble("latitude"), previous.getDouble("longitude"), location.latitude, location.longitude, distance)
                    trip.put("distanceMeters", trip.getDouble("distanceMeters") + distance[0])
                }
                trip.put("pointCount", count + 1).put("lastSampleAt", at).put("lastSampleElapsed", elapsed).put("lastFixElapsed", location.elapsedRealtimeNanos)
                store.addPoint(trip.getString("id"), count + 1, value, trip)
            }
            store.put(TRIP_KIND, trip.getString("id"), trip)
        }
    }
    @Synchronized
    fun stopAll(context: Context, reason: String) {
        val store = db(context)
        store.get(RUN_KIND, SINGLE)?.takeIf { it.optString("state") in ACTIVE_SOS }?.let {
            it.put("state", if (reason == "owner_stopped") "cancelled" else "needs_review").put("reason", reason); store.put(RUN_KIND, SINGLE, it)
        }
        store.all(FENCE_KIND).forEach { it.put("enabled", false); store.put(FENCE_KIND, it.getString("id"), it) }
        store.all(TRIP_KIND).filter { it.optString("state") == "recording" }.forEach {
            it.put("state", "interrupted").put("reason", reason); store.put(TRIP_KIND, it.getString("id"), it)
        }
    }
    private fun point(location: Location) = JSONObject().put("latitude", location.latitude).put("longitude", location.longitude)
        .put("accuracyMeters", location.accuracy.toDouble()).put("timestampMilliseconds", location.time)
    private fun message(plan: JSONObject, fix: Location?): String = plan.getString("message")
        .replace("{location}", fix?.let { "https://maps.google.com/?q=" + it.latitude + "," + it.longitude } ?: "unavailable")
        .replace("{accuracyMeters}", fix?.accuracy?.toInt()?.toString() ?: "unknown")
        .replace("{timestamp}", fix?.let { java.time.Instant.ofEpochMilli(it.time).toString() } ?: "unknown")
    private fun emit(context: Context, name: String, value: JSONObject) {
        if (!DikcizAutomationEventBus.enqueueCustomEvent(context, name, "journeys", value, null, 0)) {
            DikcizNativeCommands.failure("event_rejected", "Journey event was rejected")
        }
    }
    private fun text(max: Int) = JSONObject().put("type", "string").put("minLength", 1).put("maxLength", max)
    private fun integer(min: Long, max: Long, default: Long? = null) = JSONObject().put("type", "integer")
        .put("minimum", min).put("maximum", max).apply { default?.let { put("default", it) } }
    private fun number(min: Double, max: Double) = JSONObject().put("type", "number").put("minimum", min).put("maximum", max)
    private val bool = JSONObject().put("type", "boolean")
    private val page = mapOf("offset" to integer(0, 1000000, 0), "limit" to integer(1, 200, 50))
    private val contact = JSONObject().put("type", "object").put("properties", JSONObject(mapOf("address" to text(64), "label" to text(80))))
        .put("required", JSONArray(listOf("address"))).put("additionalProperties", false)
    val DEFINITIONS = listOf(
        DikcizNativeCommands.Definition(PLAN, "dikciz_sos_plan_save", "Save an inactive SOS plan with exact recipient phone numbers, message template and bounded check-in/update timings. Does not arm or send. Tokens: {location}, {accuracyMeters}, {timestamp}. Cancel an active run before editing. Contacts are never modified.",
            mapOf("contacts" to JSONObject().put("type", "array").put("minItems", 1).put("maxItems", 10).put("items", contact),
                "message" to text(1200), "checkInSeconds" to integer(60, 604800, 3600), "graceSeconds" to integer(5, 3600, 60),
                "updateSeconds" to integer(60, 86400, 600), "maximumUpdates" to integer(1, 24, 3), "subscriptionId" to integer(0, Int.MAX_VALUE.toLong())),
            setOf("contacts"), false),
        DikcizNativeCommands.Definition(PREVIEW, "dikciz_sos_preview", "Preview exact contacts, message and available cached location without sending or arming.", emptyMap()),
        DikcizNativeCommands.Definition(STATUS, "dikciz_sos_status", "Read the saved SOS plan, durable run state, deadline and idempotency keys for smsStatus delivery inspection. Accepted updates are not confirmed delivery. warning reports observed countdown presentation, unlocked-screen visibility, remainingSeconds, alarmVolume and speechState. Warning audio follows Android volume/DND and never proves the owner heard it.", emptyMap()),
        DikcizNativeCommands.Definition(CONTROL, "dikciz_sos_control", "arm starts missed-check-in monitoring; trigger starts the configured cancellation grace; checkIn resets the deadline; cancel stops future sends. arm/trigger require confirm=true, SMS, precise location and notification access. Foreground start required. Android can delay alarms or stop execution. On reboot, an armed run needs owner review. No emergency service is called.",
            mapOf("action" to text(16).put("enum", JSONArray(listOf("arm", "trigger", "checkIn", "cancel"))), "confirm" to bool), setOf("action"), false),
        DikcizNativeCommands.Definition(FENCES, "dikciz_geofences_list", "Read saved geofence states and observed distances, with pagination.", page),
        DikcizNativeCommands.Definition(FENCE_SAVE, "dikciz_geofence_save", "Create/update one real-location geofence. Disabled by default. Enabled monitoring uses a visible foreground service. Emits geofence.enter, geofence.exit and optional geofence.dwell custom events. Accuracy and hysteresis suppress uncertain crossings. Open Dikciz to enable.",
            mapOf("id" to text(80), "title" to text(120), "latitude" to number(-90.0, 90.0), "longitude" to number(-180.0, 180.0),
                "radiusMeters" to number(100.0, 1000000.0), "enabled" to bool, "dwellMilliseconds" to integer(0, 3600000, 0)),
            setOf("title", "latitude", "longitude", "radiusMeters"), false),
        DikcizNativeCommands.Definition(FENCE_DELETE, "dikciz_geofence_delete", "Remove one saved geofence and stop its future event delivery.", mapOf("id" to text(80)), setOf("id"), false),
        DikcizNativeCommands.Definition(TRIP_CONTROL, "dikciz_journey_control", "Start a visible location recording or stop an exact journey ID. One recording, 64 saved journeys and 10000 points per journey. Starting requires location and notifications. Route history persists; it never messages contacts.",
            mapOf("action" to text(16).put("enum", JSONArray(listOf("start", "stop"))), "id" to text(80), "title" to text(120),
                "sampleIntervalMilliseconds" to integer(1000, 60000, 10000)), setOf("action"), false),
        DikcizNativeCommands.Definition(TRIPS, "dikciz_journeys_list", "Read paginated recorded journeys and interruption state.", page),
        DikcizNativeCommands.Definition(TRIP_READ, "dikciz_journey_read", "Read a bounded ordered page of real recorded route points and accuracy. Follow hasMore. No missing location is invented.", page + mapOf("id" to text(80)), setOf("id")),
        DikcizNativeCommands.Definition(TRIP_DELETE, "dikciz_journey_delete", "Delete a stopped journey and its route points. Requires confirm=true.", mapOf("id" to text(80), "confirm" to bool), setOf("id", "confirm"), false),
    )
    val TYPES = DEFINITIONS.map { it.type }.toSet()
}
