package org.fossify.home.dikciz

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class DikcizDebugPhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DikcizAutomationDebugContract.ACTION_PHONE_STATE) {
            return
        }
        val callState = when (intent.getStringExtra(DikcizAutomationDebugContract.EXTRA_PHONE_STATE)) {
            STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
            STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            else -> return
        }
        context.startForegroundService(
            Intent(context, DikcizAutomationService::class.java)
                .setAction(DikcizAutomationDebugContract.ACTION_SERVICE_PHONE_STATE)
                .putExtra(DikcizAutomationDebugContract.EXTRA_CALL_STATE, callState),
        )
    }

    private companion object {
        const val STATE_IDLE = "idle"
        const val STATE_OFFHOOK = "offhook"
        const val STATE_RINGING = "ringing"
    }
}
