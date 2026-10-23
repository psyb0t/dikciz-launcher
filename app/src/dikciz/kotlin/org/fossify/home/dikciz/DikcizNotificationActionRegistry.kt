package org.fossify.home.dikciz

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
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
                title = action.title?.toString()?.take(DikcizNativeCommands.MAXIMUM_TEXT).orEmpty(),
                remoteInputs = action.remoteInputs.orEmpty().filter { it.allowFreeFormInput }.toTypedArray(),
                expiresAtElapsedMilliseconds = now + ACTION_TOKEN_LIFETIME_MILLISECONDS,
            )
            tokensByNotificationKey.getOrPut(notification.key) { mutableSetOf() }.add(token)
            tokens += token
        }
        removeOverflowEntries()
        return@synchronized tokens.filter(entries::containsKey)
    }

    fun tokens(notification: StatusBarNotification): List<String> = synchronized(this) {
        removeExpiredEntries(SystemClock.elapsedRealtime())
        val current = tokensByNotificationKey[notification.key].orEmpty().filter(entries::containsKey)
        if (current.isNotEmpty()) return@synchronized current
        replace(notification)
    }

    fun details(notification: StatusBarNotification): JSONArray = synchronized(this) {
        JSONArray(tokens(notification).mapNotNull { token ->
            entries[token]?.let { entry ->
                JSONObject().put("actionToken", token).put("title", entry.title)
                    .put("supportsReply", entry.remoteInputs.isNotEmpty())
            }
        })
    }

    fun reply(context: Context, token: String, text: String): DikcizNotificationActionResult {
        val entry = synchronized(this) {
            removeExpiredEntries(SystemClock.elapsedRealtime())
            val target = entries[token] ?: return DikcizNotificationActionResult.TargetUnavailable
            if (target.remoteInputs.isEmpty()) return DikcizNotificationActionResult.ReplyUnsupported
            removeEntryLocked(token)
            target
        }
        val result = Bundle().apply { entry.remoteInputs.forEach { putCharSequence(it.resultKey, text) } }
        val fillIn = Intent()
        RemoteInput.addResultsToIntent(entry.remoteInputs, fillIn, result)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            RemoteInput.setResultsSource(fillIn, RemoteInput.SOURCE_FREE_FORM_INPUT)
        }
        return try {
            entry.pendingIntent.send(context, 0, fillIn)
            DikcizNotificationActionResult.Executed
        } catch (_: PendingIntent.CanceledException) {
            DikcizNotificationActionResult.TargetUnavailable
        } catch (_: SecurityException) {
            DikcizNotificationActionResult.AndroidRejected
        }
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
        val title: String,
        val remoteInputs: Array<RemoteInput>,
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
    ReplyUnsupported,
}
