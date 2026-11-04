package org.fossify.home.dikciz

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import org.json.JSONObject

internal class DikcizSimSubscriptions(
    private val context: Context,
    private val logger: DikcizLogger,
) {
    fun read(request: JSONObject, readNumbers: Boolean = true): JSONObject {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            DikcizNativeCommands.failure(ERROR_PHONE_STATE_PERMISSION, "Android phone-state permission is required")
        }
        val manager = context.getSystemService(SubscriptionManager::class.java)
            ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_UNAVAILABLE, "Android subscription service is unavailable")
        val canReadNumbers = readNumbers && context.checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
        val items = manager.activeSubscriptionInfoList.orEmpty().map { info ->
            (if (readNumbers) number(manager, info) else JSONObject()
                .put(KEY_NUMBER, JSONObject.NULL).put(KEY_NUMBER_STATUS, "access_denied"))
                .put("subscriptionId", info.subscriptionId)
                .put("slotIndex", info.simSlotIndex)
                .put("displayName", info.displayName?.toString()?.take(DikcizNativeCommands.MAXIMUM_TEXT) ?: JSONObject.NULL)
                .put("carrierName", info.carrierName?.toString()?.take(DikcizNativeCommands.MAXIMUM_TEXT) ?: JSONObject.NULL)
        }.sortedBy { it.getInt("subscriptionId") }
        return DikcizNativeCommands.page(items, request)
            .put("defaultSmsSubscriptionId", SubscriptionManager.getDefaultSmsSubscriptionId())
            .put(KEY_NUMBER_PERMISSION_GRANTED, canReadNumbers)
    }

    private fun number(manager: SubscriptionManager, subscription: SubscriptionInfo): JSONObject {
        val permitted = context.checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
        val result = readSimPhoneNumber(permitted) { lookupNumber(manager, subscription) }
        result.failure?.let { failure ->
            val fields = mapOf("number_status" to result.status.value, "error_class" to failure.javaClass.simpleName)
            if (result.status == DikcizSimNumberStatus.Unsupported) {
                logger.debug("sim_phone_number_lookup_failed", fields)
            } else {
                logger.warn("sim_phone_number_lookup_failed", fields)
            }
        }
        return JSONObject()
            .put(KEY_NUMBER, result.number ?: JSONObject.NULL)
            .put(KEY_NUMBER_STATUS, result.status.value)
    }

    @Suppress("DEPRECATION")
    private fun lookupNumber(manager: SubscriptionManager, subscription: SubscriptionInfo): String? {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Android phone-number access was revoked")
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getPhoneNumber(subscription.subscriptionId)
        } else {
            subscription.number
        }
    }

    companion object {
        const val ERROR_PHONE_STATE_PERMISSION = "native_permission_telephony"
        const val KEY_NUMBER_PERMISSION_GRANTED = "phoneNumberPermissionGranted"
        private const val KEY_NUMBER = "phoneNumber"
        private const val KEY_NUMBER_STATUS = "phoneNumberStatus"
    }
}
