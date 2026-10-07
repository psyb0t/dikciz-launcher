package org.fossify.home.dikciz

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DikcizDebugHealthConnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DikcizAutomationDebugContract.ACTION_HEALTH_DAILY_STEPS) {
            return
        }
        val steps = intent.getLongExtra(
            DikcizAutomationDebugContract.EXTRA_HEALTH_DAILY_STEPS,
            INVALID_STEPS,
        )
        DikcizHealthConnectAutomation.enqueueDailySteps(
            context,
            DikcizHealthConnectAutomation.currentLocalDayStartMilliseconds(),
            steps,
        )
    }

    private companion object {
        const val INVALID_STEPS = -1L
    }
}
