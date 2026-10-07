package org.fossify.home.dikciz

import android.app.PendingIntent
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import java.util.UUID

internal object DikcizNotificationActionRegistry {
    private val entries = LinkedHashMap<String, Entry>()
    private val tokensByNotificationKey = mutableMapOf<String, MutableSet<String>>()

    fun replace(notification: StatusBarNotification): List<String> = synchronized(this) {
        val now = SystemClock.elapsedRealtime()
        removeExpiredEntries(now)
        removeLocked(notification.key)
        val tokens = mutableListOf<String>()
        notification.notification.actions?.forEach { action ->
            val pendingIntent = action.actionIntent ?: return@forEach
            if (tokens.size == MAXIMUM_ACTIONS_PER_NOTIFICATION) {
                return@forEach
            }
            val token = UUID.randomUUID().toString()
            entries[token] = Entry(
                notificationKey = notification.key,
                packageName = notification.packageName,
                pendingIntent = pendingIntent,
                expiresAtElapsedMilliseconds = now + ACTION_TOKEN_LIFETIME_MILLISECONDS,
            )
            tokensByNotificationKey.getOrPut(notification.key) { mutableSetOf() }.add(token)
            tokens += token
        }
        removeOverflowEntries()
        return@synchronized tokens.filter(entries::containsKey)
    }

    fun consume(token: String): DikcizNotificationActionResult {
        val pendingIntent = synchronized(this) {
            removeExpiredEntries(SystemClock.elapsedRealtime())
            val entry = entries[token] ?: return DikcizNotificationActionResult.TargetUnavailable
            removeEntryLocked(token)
            entry.pendingIntent
        }
        return try {
            pendingIntent.send()
            DikcizNotificationActionResult.Executed
        } catch (_: PendingIntent.CanceledException) {
            DikcizNotificationActionResult.TargetUnavailable
        } catch (_: SecurityException) {
            DikcizNotificationActionResult.AndroidRejected
        }
    }

    fun remove(notificationKey: String) = synchronized(this) {
        removeLocked(notificationKey)
    }

    fun clear() = synchronized(this) {
        entries.clear()
        tokensByNotificationKey.clear()
    }

    private fun removeExpiredEntries(now: Long) {
        entries.entries
            .filter { (_, entry) -> entry.expiresAtElapsedMilliseconds <= now }
            .map(Map.Entry<String, Entry>::key)
            .forEach(::removeEntryLocked)
    }

    private fun removeOverflowEntries() {
        while (entries.size > MAXIMUM_ACTION_TOKENS) {
            removeEntryLocked(entries.entries.first().key)
        }
    }

    private fun removeLocked(notificationKey: String) {
        tokensByNotificationKey.remove(notificationKey)?.toList()?.forEach(::removeEntryLocked)
    }

    private fun removeEntryLocked(token: String) {
        val entry = entries.remove(token) ?: return
        val tokens = tokensByNotificationKey[entry.notificationKey] ?: return
        tokens.remove(token)
        if (tokens.isEmpty()) {
            tokensByNotificationKey.remove(entry.notificationKey)
        }
    }

    private data class Entry(
        val notificationKey: String,
        val packageName: String,
        val pendingIntent: PendingIntent,
        val expiresAtElapsedMilliseconds: Long,
    )

    private const val ACTION_TOKEN_LIFETIME_MILLISECONDS = 60_000L
    private const val MAXIMUM_ACTIONS_PER_NOTIFICATION = 4
    private const val MAXIMUM_ACTION_TOKENS = 128
}

internal enum class DikcizNotificationActionResult {
    Executed,
    TargetUnavailable,
    AndroidRejected,
}
