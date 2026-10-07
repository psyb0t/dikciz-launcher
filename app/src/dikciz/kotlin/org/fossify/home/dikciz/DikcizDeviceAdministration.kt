package org.fossify.home.dikciz

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import org.fossify.home.R
import org.json.JSONObject

internal object DikcizDeviceAdministration {
    fun activationIntent(context: Context): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, receiverComponent(context))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getString(R.string.dikciz_device_admin_explanation),
            )
    }

    fun isActive(context: Context): Boolean {
        val manager = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        return manager.isAdminActive(receiverComponent(context))
    }

    fun lock(context: Context): DikcizDeviceLockResult {
        val manager = context.getSystemService(DevicePolicyManager::class.java)
            ?: return DikcizDeviceLockResult.TargetUnavailable
        if (!manager.isAdminActive(receiverComponent(context))) {
            return DikcizDeviceLockResult.AdminInactive
        }
        return try {
            manager.lockNow()
            DikcizDeviceLockResult.Executed
        } catch (_: SecurityException) {
            DikcizDeviceLockResult.AndroidRejected
        }
    }

    fun deactivate(context: Context) {
        val manager = context.getSystemService(DevicePolicyManager::class.java) ?: return
        val receiver = receiverComponent(context)
        if (!manager.isAdminActive(receiver)) {
            return
        }
        manager.removeActiveAdmin(receiver)
    }

    fun publishState(context: Context, active: Boolean) {
        DikcizAutomationEventBus.enqueue(
            context,
            DikcizAutomationEvent(
                type = DikcizAutomationEventType.DeviceAdminState,
                source = DEVICE_ADMINISTRATION_EVENT_SOURCE,
                timestampMilliseconds = System.currentTimeMillis(),
                coalescingKey = DEVICE_ADMINISTRATION_EVENT_COALESCING_KEY,
                payload = JSONObject().put(PAYLOAD_ACTIVE, active),
            ),
        )
    }

    private fun receiverComponent(context: Context): ComponentName {
        return ComponentName(context, DikcizDeviceAdminReceiver::class.java)
    }

    private const val DEVICE_ADMINISTRATION_EVENT_COALESCING_KEY = "device-administration"
    private const val DEVICE_ADMINISTRATION_EVENT_SOURCE = "android-device-administration"
    private const val PAYLOAD_ACTIVE = "active"
}

internal enum class DikcizDeviceLockResult {
    Executed,
    AdminInactive,
    TargetUnavailable,
    AndroidRejected,
}

internal class DikcizDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        DikcizDeviceAdministration.publishState(context, true)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        DikcizDeviceAdministration.publishState(context, false)
    }
}
