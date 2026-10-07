package org.fossify.home.dikciz

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class DikcizDebugSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DikcizAutomationDebugContract.ACTION_SMS_RECEIVED) {
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val sender = intent.getStringExtra(DikcizAutomationDebugContract.EXTRA_SMS_SENDER) ?: return
        val body = intent.getStringExtra(DikcizAutomationDebugContract.EXTRA_SMS_BODY) ?: return
        if (!sender.matches(SENDER_PATTERN) || !isValidBody(body)) {
            return
        }
        DikcizSmsAutomation.enqueue(
            context,
            listOf(
                DikcizIncomingSmsMessage(
                    body = body,
                    sender = sender,
                    timestampMilliseconds = System.currentTimeMillis(),
                ),
            ),
        )
    }

    private fun isValidBody(body: String): Boolean {
        return body.isNotEmpty() &&
            body.length <= MAXIMUM_BODY_CHARACTERS &&
            '\n' !in body &&
            '\r' !in body
    }

    private companion object {
        const val MAXIMUM_BODY_CHARACTERS = 512
        val SENDER_PATTERN = Regex("[0-9]{1,32}")
    }
}
