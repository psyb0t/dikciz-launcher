package org.fossify.home.dikciz

import android.app.usage.UsageStatsManager
import android.content.Context

internal data class DikcizAppUsage(
    val available: Boolean,
    val reason: String?,
    val foregroundMilliseconds: Map<String, Long>,
) {
    companion object {
        const val WINDOW_DAYS = 7
        private const val WINDOW_MILLISECONDS = WINDOW_DAYS * 24L * 60 * 60 * 1000

        fun read(context: Context): DikcizAppUsage {
            if (DikcizDeviceAccess.state(context, DikcizDeviceAccessRequirement.UsageAccess) !=
                DikcizDeviceAccessState.Granted
            ) {
                return DikcizAppUsage(false, "usage_access_required", emptyMap())
            }
            val manager = context.getSystemService(UsageStatsManager::class.java)
                ?: return DikcizAppUsage(false, "usage_service_unavailable", emptyMap())
            val now = System.currentTimeMillis()
            return try {
                val usage = manager.queryAndAggregateUsageStats(now - WINDOW_MILLISECONDS, now)
                DikcizAppUsage(true, null, usage.mapValues { it.value.totalTimeInForeground.coerceAtLeast(0) })
            } catch (_: SecurityException) {
                DikcizAppUsage(false, "usage_access_required", emptyMap())
            }
        }
    }
}
