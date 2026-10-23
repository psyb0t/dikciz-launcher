package org.fossify.home.dikciz

import android.content.Context
import android.os.SystemClock
import java.util.ArrayDeque
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Process-owned replay records only events that the automation bus actually dispatches. */
internal object DikcizEventJournal {
    const val TYPE_STATUS = "eventJournalStatus"
    const val TYPE_READ = "eventJournalRead"
    const val TYPE_CONFIGURE = "eventJournalConfigure"
    private const val DEFAULT_EVENTS = 2048
    private const val DEFAULT_BUFFER = 8_388_608
    private const val DEFAULT_RETENTION = 60
    private const val MAXIMUM_EVENT_BYTES = 65_536
    private const val MAXIMUM_PAGE_BYTES = 262_144
    private const val MINUTE_MILLISECONDS = 60_000L
    private const val MAXIMUM_CURSOR = 9_007_199_254_740_991L
    private const val MAXIMUM_FILTERS = 32
    private val lock = Object()
    private val journalId = UUID.randomUUID().toString()
    private val entries = ArrayDeque<Entry>()
    private var maximumEvents = DEFAULT_EVENTS
    private var bufferBytes = DEFAULT_BUFFER
    private var retentionMinutes = DEFAULT_RETENTION
    private var retainedBytes = 0
    private var nextSequence = 1L
    private var evictedEvents = 0L

    private data class Entry(
        val sequence: Long,
        val name: String,
        val capturedAt: Long,
        val serialized: String,
        val bytes: Int,
        val type: DikcizAutomationEventType,
        val precision: DikcizLocationPrecision?,
        val sensorType: Int?,
        val omitted: Boolean,
    )

    fun append(event: DikcizAutomationEvent) {
        val document = event.toDocument().toString()
        val bytes = document.toByteArray(Charsets.UTF_8).size
        synchronized(lock) {
            val sequence = nextSequence++
            val omitted = bytes > MAXIMUM_EVENT_BYTES
            val serialized = if (omitted) {
                JSONObject().put("type", event.name).put("omitted", true)
                    .put("reason", "event_too_large").put("originalBytes", bytes).toString()
            } else {
                document
            }
            val entry = Entry(
                sequence, event.name, SystemClock.elapsedRealtime(), serialized, serialized.toByteArray(Charsets.UTF_8).size,
                event.type, event.locationPrecision,
                if (event.type == DikcizAutomationEventType.Sensor) event.payload.optInt("sensorType", -1) else null,
                omitted,
            )
            entries.addLast(entry)
            retainedBytes += entry.bytes
            prune()
            lock.notifyAll()
        }
    }

    fun execute(context: Context, logger: DikcizLogger, type: String, request: JSONObject): JSONObject {
        if (type == TYPE_READ) return read(context, logger, request)
        synchronized(lock) {
            if (type == TYPE_CONFIGURE) {
                maximumEvents = request.optInt("maximumEvents", maximumEvents)
                bufferBytes = request.optInt("bufferBytes", bufferBytes)
                retentionMinutes = request.optInt("retentionMinutes", retentionMinutes)
            }
            prune()
            lock.notifyAll()
            return status()
        }
    }

    private fun read(context: Context, logger: DikcizLogger, request: JSONObject): JSONObject {
        val cursor = request.optLong("cursor", 0)
        val wait = request.optLong("waitMilliseconds", 0)
        val filter = request.optJSONArray("eventTypes")?.let { values ->
            (0 until values.length()).map { values.getString(it) }.toSet()
        }.orEmpty()
        val selected = mutableListOf<Entry>()
        val response: JSONObject
        synchronized(lock) {
            if (request.getString("journalId") != journalId) {
                DikcizNativeCommands.failure("event_journal_restarted", "The event journal changed; read eventJournalStatus and choose a new cursor")
            }
            if (cursor >= nextSequence) DikcizNativeCommands.invalid("Event cursor is ahead of this journal")
            val deadline = SystemClock.elapsedRealtime() + wait
            while (true) {
                prune()
                val gap = cursor < firstSequence() - 1
                val matching = entries.any { it.sequence > cursor && matches(it, filter) }
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (gap || matching || remaining <= 0) break
                val untilExpiry = entries.peekFirst()?.let {
                    it.capturedAt + retentionMinutes * MINUTE_MILLISECONDS - SystemClock.elapsedRealtime()
                } ?: remaining
                lock.wait(minOf(remaining, maxOf(1L, untilExpiry)))
            }
            var nextCursor = maxOf(cursor, firstSequence() - 1)
            var bytes = 0
            var hasMore = false
            val limit = request.optInt("limit", DikcizNativeCommands.DEFAULT_LIMIT)
            for (entry in entries) {
                if (entry.sequence <= cursor) continue
                if (matches(entry, filter)) {
                    if (selected.size >= limit || bytes + entry.bytes > MAXIMUM_PAGE_BYTES) {
                        hasMore = true
                        break
                    }
                    selected.add(entry)
                    bytes += entry.bytes
                }
                nextCursor = entry.sequence
            }
            response = status().put("events", JSONArray()).put("nextCursor", nextCursor)
                .put("hasMore", hasMore).put("historyGap", cursor < firstSequence() - 1)
                .put("lostEvents", maxOf(0L, firstSequence() - cursor - 1))
                .put("omittedEvents", selected.count { it.omitted })
        }
        val access = DikcizEventReplayAccess(context, logger)
        val rows = JSONArray()
        for (entry in selected) {
            if (!entry.omitted) access.require(entry.type, entry.precision, entry.sensorType)
            rows.put(JSONObject().put("sequence", entry.sequence).put("event", JSONObject(entry.serialized)))
        }
        return response.put("events", rows)
    }

    private fun matches(entry: Entry, filter: Set<String>): Boolean =
        filter.isEmpty() || entry.name in filter

    private fun firstSequence(): Long = entries.peekFirst()?.sequence ?: nextSequence

    private fun prune() {
        val oldest = SystemClock.elapsedRealtime() - retentionMinutes * MINUTE_MILLISECONDS
        while (entries.isNotEmpty() &&
            (entries.size > maximumEvents || retainedBytes > bufferBytes || entries.first.capturedAt <= oldest)) {
            retainedBytes -= entries.removeFirst().bytes
            evictedEvents++
        }
    }

    private fun status(): JSONObject = JSONObject()
        .put("journalId", journalId).put("firstSequence", firstSequence()).put("lastSequence", nextSequence - 1)
        .put("retainedEvents", entries.size).put("retainedBytes", retainedBytes).put("evictedEvents", evictedEvents)
        .put("maximumEvents", maximumEvents).put("bufferBytes", bufferBytes).put("retentionMinutes", retentionMinutes)
        .put("maximumEventBytes", MAXIMUM_EVENT_BYTES).put("maximumPageBytes", MAXIMUM_PAGE_BYTES)
        .put("persistence", "process")

    private val LIMITS = mapOf(
        "maximumEvents" to integer(1, 16384),
        "bufferBytes" to integer(MAXIMUM_EVENT_BYTES.toLong(), 33_554_432),
        "retentionMinutes" to integer(1, 1440),
    )
    val DEFINITIONS = listOf(
        DikcizNativeCommands.Definition(TYPE_STATUS, "dikciz_event_journal_status",
            "Read process-owned automation-event replay epoch, cursors, retention and memory limits. Reading does not activate native subscriptions.", emptyMap()),
        DikcizNativeCommands.Definition(TYPE_READ, "dikciz_event_journal_read",
            "Replay actual automation events after cursor in the required journalId epoch. Optional exact eventTypes filters, bounded long polling and pagination work across every ordinary binding. Inspect historyGap/lostEvents and per-event omitted markers. Current Android source access is rechecked before returning retained payloads.",
            mapOf("journalId" to text(36), "cursor" to integer(0, MAXIMUM_CURSOR).put("default", 0),
                "limit" to integer(1, DikcizNativeCommands.MAXIMUM_LIMIT.toLong()).put("default", DikcizNativeCommands.DEFAULT_LIMIT),
                "waitMilliseconds" to integer(0, 30000).put("default", 0),
                "eventTypes" to JSONObject().put("type", "array").put("minItems", 1).put("maxItems", MAXIMUM_FILTERS)
                    .put("uniqueItems", true).put("items", text(128))), setOf("journalId")),
        DikcizNativeCommands.Definition(TYPE_CONFIGURE, "dikciz_event_journal_configure",
            "Adjust process-owned replay count, byte and age limits. Defaults are 2048 events, 8 MiB and 60 minutes. Lower limits immediately evict old history; limits reset on process death. Does not change native sensor/location subscriptions.",
            LIMITS, readOnly = false),
    )
    val TYPES = setOf(TYPE_STATUS, TYPE_READ, TYPE_CONFIGURE)

    fun validate(type: String, request: JSONObject) {
        if (type != TYPE_READ) return
        val id = request.getString("journalId")
        if (!Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}").matches(id)) {
            DikcizNativeCommands.invalid("Invalid event journal ID")
        }
        val filters = request.optJSONArray("eventTypes") ?: return
        if (filters.length() !in 1..MAXIMUM_FILTERS) DikcizNativeCommands.invalid("Event filters require 1 through 32 names")
        val seen = mutableSetOf<String>()
        for (index in 0 until filters.length()) {
            val value = filters.opt(index)
            if (value !is String || !DikcizAutomationEventNames.isValidCustomName(value) || !seen.add(value)) {
                DikcizNativeCommands.invalid("Event filters require unique exact event names")
            }
        }
    }

    private fun text(maximum: Int) = JSONObject().put("type", "string").put("maxLength", maximum)
    private fun integer(minimum: Long, maximum: Long) =
        JSONObject().put("type", "integer").put("minimum", minimum).put("maximum", maximum)
}
