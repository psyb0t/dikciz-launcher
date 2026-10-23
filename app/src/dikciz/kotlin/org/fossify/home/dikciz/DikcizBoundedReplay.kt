package org.fossify.home.dikciz

import android.os.SystemClock
import java.util.ArrayDeque
import org.json.JSONArray
import org.json.JSONObject

internal object DikcizReplayFields {
    const val STREAM_ID = "streamId"
    const val STATE = "state"
    const val EVENT = "event"
    const val SEQUENCE = "sequence"
    const val EVENTS = "events"
    const val CURSOR = "cursor"
    const val LIMIT = "limit"
    const val WAIT = "waitMilliseconds"
    const val NEXT_CURSOR = "nextCursor"
    const val FIRST_SEQUENCE = "firstSequence"
    const val LAST_SEQUENCE = "lastSequence"
    const val HAS_MORE = "hasMore"
    const val HISTORY_GAP = "historyGap"
    const val LOST_EVENTS = "lostEvents"
    const val RETAINED_EVENTS = "retainedEvents"
    const val RETAINED_BYTES = "retainedBytes"
    const val ACTIVE = "active"
}

/** Whole-event replay makes loss visible without coupling a producer to a transport. */
internal class DikcizBoundedReplay(
    private val maximumBytes: Int,
    private val maximumEvents: Int,
) {
    private data class Entry(val sequence: Long, val serialized: String, val bytes: Int)
    private val lock = Object()
    private val entries = ArrayDeque<Entry>()
    private var retainedBytes = 0
    private var nextSequence = 1L
    private var finished = false

    fun append(event: JSONObject) {
        val serialized = event.toString()
        val bytes = serialized.toByteArray(Charsets.UTF_8).size
        require(bytes <= maximumBytes && bytes <= MAXIMUM_PAGE_BYTES) { MESSAGE_EVENT_TOO_LARGE }
        synchronized(lock) {
            check(!finished) { MESSAGE_FINISHED }
            entries.addLast(Entry(nextSequence++, serialized, bytes))
            retainedBytes += bytes
            while (entries.size > maximumEvents || retainedBytes > maximumBytes) {
                retainedBytes -= entries.removeFirst().bytes
            }
            lock.notifyAll()
        }
    }

    fun finish() {
        synchronized(lock) {
            finished = true
            lock.notifyAll()
        }
    }

    fun metadata(): JSONObject = synchronized(lock) {
        JSONObject().put(DikcizReplayFields.FIRST_SEQUENCE, firstSequence())
            .put(DikcizReplayFields.LAST_SEQUENCE, nextSequence - 1)
            .put(DikcizReplayFields.RETAINED_EVENTS, entries.size)
            .put(DikcizReplayFields.RETAINED_BYTES, retainedBytes)
    }

    fun read(cursor: Long, limit: Int, waitMilliseconds: Long): JSONObject = synchronized(lock) {
        if (cursor >= nextSequence) DikcizNativeCommands.invalid(MESSAGE_FUTURE_CURSOR)
        val deadline = SystemClock.elapsedRealtime() + waitMilliseconds
        while (cursor >= nextSequence - 1 && !finished) {
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0) break
            lock.wait(remaining)
        }
        val rows = JSONArray()
        var bytes = 0
        var nextCursor = maxOf(cursor, firstSequence() - 1)
        for (entry in entries) {
            if (entry.sequence <= cursor) continue
            if (rows.length() >= limit || bytes + entry.bytes > MAXIMUM_PAGE_BYTES) break
            rows.put(JSONObject(entry.serialized).put(DikcizReplayFields.SEQUENCE, entry.sequence))
            bytes += entry.bytes
            nextCursor = entry.sequence
        }
        metadata().put(DikcizReplayFields.EVENTS, rows).put(DikcizReplayFields.NEXT_CURSOR, nextCursor)
            .put(DikcizReplayFields.HAS_MORE, nextCursor < nextSequence - 1)
            .put(DikcizReplayFields.HISTORY_GAP, cursor < firstSequence() - 1)
            .put(DikcizReplayFields.LOST_EVENTS, maxOf(0L, firstSequence() - cursor - 1))
    }

    private fun firstSequence(): Long = entries.peekFirst()?.sequence ?: nextSequence

    companion object {
        const val MAXIMUM_PAGE_BYTES = 393216
        private const val MESSAGE_EVENT_TOO_LARGE = "Replay event exceeds its complete-event budget"
        private const val MESSAGE_FINISHED = "Replay producer has finished"
        private const val MESSAGE_FUTURE_CURSOR = "Replay cursor is ahead of this stream"
    }
}
