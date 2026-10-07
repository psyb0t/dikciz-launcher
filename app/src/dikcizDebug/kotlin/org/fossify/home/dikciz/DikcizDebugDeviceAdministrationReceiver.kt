package org.fossify.home.dikciz

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DikcizDebugDeviceAdministrationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DikcizAutomationDebugContract.ACTION_DEVICE_ADMINISTRATION_DEACTIVATE) {
            return
        }
        DikcizDeviceAdministration.deactivate(context)
    }
}
