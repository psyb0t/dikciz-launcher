package org.fossify.home.dikciz

import android.Manifest
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import org.json.JSONObject

internal class DikcizNativeData(
    private val context: Context,
    private val logger: DikcizLogger,
) {
    private val content = DikcizContentData(context)
    private val sensorReadings = DikcizSensorReadings(context)
    private val mediaCapture = DikcizMediaCapture(context)
    private val systemMetrics = DikcizSystemMetrics(context)

    fun execute(command: DikcizAutomationCommand): JSONObject {
        DikcizNativeCommands.validate(command.type, command.request, true)
        try {
            val result = when (command.type) {
                DikcizNativeCommands.TYPE_STATUS -> content.status()
                DikcizNativeCommands.TYPE_QUERY -> content.query(command.request)
                DikcizNativeCommands.TYPE_WRITE -> content.write(command.request, false)
                DikcizNativeCommands.TYPE_BATCH -> content.write(command.request, true)
                DikcizNativeCommands.TYPE_SMS_SEND -> DikcizSmsStatus.send(context, command.request)
                DikcizNativeCommands.TYPE_SMS_STATUS -> DikcizSmsStatus.status(context, command.request)
                DikcizNativeCommands.TYPE_SIM_LIST -> subscriptions(command.request)
                DikcizNativeCommands.TYPE_CALL -> call(command.request)
                DikcizNativeCommands.TYPE_CLIPBOARD_GET -> clipboard()
                DikcizNativeCommands.TYPE_CLIPBOARD_SET -> {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Dikciz", command.request.getString(DikcizNativeCommands.KEY_TEXT)))
                    JSONObject().put("state", "written")
                }
                DikcizNativeCommands.TYPE_SENSOR_READINGS -> sensorReadings.read(command.request)
                DikcizNativeCommands.TYPE_CAMERAS -> mediaCapture.cameras()
                DikcizNativeCommands.TYPE_CAMERA_CAPTURE -> mediaCapture.photo(command.request)
                DikcizNativeCommands.TYPE_MICROPHONE_RECORD -> mediaCapture.microphone(command.request)
                DikcizNativeCommands.TYPE_SPEAK -> mediaCapture.speak(command.request)
                DikcizNativeCommands.TYPE_CALL_AUDIO -> mediaCapture.callAudio(command.request)
                DikcizNativeCommands.TYPE_LOCATION -> location(command.request)
                DikcizNativeCommands.TYPE_NOTIFICATIONS -> notifications(command.request)
                DikcizNativeCommands.TYPE_MEDIA -> media(command.request)
                DikcizNativeCommands.TYPE_DEVICE_STATE -> deviceState()
                DikcizNativeCommands.TYPE_APP_ICON -> DikcizAppIcon.read(context, command.request)
                else -> DikcizNativeCommands.invalid()
            }
            val fields = mapOf("command_type" to command.type)
            if (command.type in DikcizNativeCommands.READ_ONLY_TYPES) {
                logger.debug("native_command_completed", fields)
            } else {
                logger.info("native_command_completed", fields)
            }
            return result
        } catch (exception: DikcizAutomationRequestException) {
            throw exception
        } catch (exception: SecurityException) {
            logger.warn("native_access_denied", mapOf("command_type" to command.type, "error_class" to exception.javaClass.simpleName))
            throw DikcizAutomationRequestException("native_access_denied", "Android denied this native operation; permissions, app roles or foreground restrictions may apply").apply { initCause(exception) }
        } catch (exception: Exception) {
            logger.error("native_command_failed", mapOf("command_type" to command.type, "error_class" to exception.javaClass.simpleName))
            throw DikcizAutomationRequestException(DikcizNativeCommands.ERROR_FAILED, "Android native operation failed; no success is confirmed").apply { initCause(exception) }
        }
    }

    private fun subscriptions(request: JSONObject): JSONObject {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                    DikcizNativeCommands.failure("native_permission_telephony", "Android phone permission is required")
                }
        val items = context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty().map { info ->
            JSONObject().put("subscriptionId", info.subscriptionId).put("slotIndex", info.simSlotIndex)
                .put("displayName", info.displayName?.toString()?.take(DikcizNativeCommands.MAXIMUM_TEXT) ?: JSONObject.NULL)
                .put("carrierName", info.carrierName?.toString()?.take(DikcizNativeCommands.MAXIMUM_TEXT) ?: JSONObject.NULL)
        }.sortedBy { it.getInt("subscriptionId") }
        return DikcizNativeCommands.page(items, request).put("defaultSmsSubscriptionId", SubscriptionManager.getDefaultSmsSubscriptionId())
    }

    @Suppress("DEPRECATION")
    private fun call(request: JSONObject): JSONObject {
        val manager = context.getSystemService(TelecomManager::class.java) ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_UNAVAILABLE, "Telecom service unavailable")
        val action = request.getString("action")
        when (action) {
            "state" -> {
                if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                    DikcizNativeCommands.failure("native_permission_telephony", "Android phone permission is required")
                }
                return JSONObject().put("inCall", manager.isInCall).put("inManagedCall", manager.isInManagedCall)
            }
            "place" -> {
                if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    DikcizNativeCommands.failure("native_permission_telephony", "Android phone permission is required")
                }
                manager.placeCall(Uri.fromParts("tel", DikcizNativeCommands.normalizedRecipient(request.getString(DikcizNativeCommands.KEY_RECIPIENT)), null), Bundle())
                return JSONObject().put("state", "accepted").put("connected", false)
            }
            "answer" -> {
                if (context.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
                    DikcizNativeCommands.failure("native_permission_telephony", "Android phone permission is required")
                }
                manager.acceptRingingCall()
                return JSONObject().put("state", "accepted")
            }
            "end" -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                    DikcizNativeCommands.failure("unsupported_android_version", "Ending calls requires Android 9 or newer")
                }
                if (context.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
                    DikcizNativeCommands.failure("native_permission_telephony", "Android phone permission is required")
                }
                return JSONObject().put("state", if (manager.endCall()) "ended" else "not_ended")
            }
            else -> DikcizNativeCommands.invalid()
        }
    }

    private fun clipboard(): JSONObject {
        val manager = context.getSystemService(ClipboardManager::class.java)
        val clip = manager.primaryClip ?: return JSONObject().put("available", false).put("reason", "empty_or_android_restricted")
        val text = if (clip.itemCount > 0) clip.getItemAt(0).text?.toString() else null
        return JSONObject().put("available", text != null).put("text", text?.take(DikcizNativeCommands.MAXIMUM_TEXT) ?: JSONObject.NULL)
            .put("truncated", (text?.length ?: 0) > DikcizNativeCommands.MAXIMUM_TEXT)
    }

    private fun location(request: JSONObject): JSONObject = DikcizLocation.read(context, request, logger)

    private fun notifications(request: JSONObject): JSONObject {
        requireNotificationAccess()
        val notifications = DikcizNotificationListenerService.currentNotifications()
            ?: DikcizNativeCommands.failure("notification_listener_unavailable", "Android notification listener is not connected")
        return DikcizNativeCommands.page(notifications.sortedBy { it.key }, request) { notification ->
            val extras = notification.notification.extras
            JSONObject().put("key", notification.key).put("packageName", notification.packageName)
                .put("postTimeMilliseconds", notification.postTime)
                .put("title", extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()?.take(DikcizNativeCommands.MAXIMUM_TEXT) ?: JSONObject.NULL)
                .put("text", extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()?.take(DikcizNativeCommands.MAXIMUM_TEXT) ?: JSONObject.NULL)
                .put("actionTokens", org.json.JSONArray(DikcizNotificationActionRegistry.tokens(notification)))
        }
    }

    private fun media(request: JSONObject): JSONObject {
        requireNotificationAccess()
        val manager = context.getSystemService(MediaSessionManager::class.java) ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_UNAVAILABLE, "Media session service unavailable")
        val controllers = manager.getActiveSessions(ComponentName(context, DikcizNotificationListenerService::class.java))
        val items = controllers.sortedBy { it.packageName }.map { controller ->
            JSONObject().put("packageName", controller.packageName).put("playbackState", controller.playbackState?.state ?: JSONObject.NULL)
                .put("positionMilliseconds", controller.playbackState?.position ?: JSONObject.NULL)
                .put("title", controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.take(DikcizNativeCommands.MAXIMUM_TEXT) ?: JSONObject.NULL)
                .put("artist", controller.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.take(DikcizNativeCommands.MAXIMUM_TEXT) ?: JSONObject.NULL)
                .put("album", controller.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)?.take(DikcizNativeCommands.MAXIMUM_TEXT) ?: JSONObject.NULL)
                .put("durationMilliseconds", controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: JSONObject.NULL)
        }
        return DikcizNativeCommands.page(items, request)
    }

    private fun requireNotificationAccess() {
        if (!DikcizAutomationStatus.hasNotificationListenerAccess(context)) {
            DikcizNativeCommands.failure("native_permission_notificationListener", "Enable Android notification-listener access")
        }
    }

    private fun deviceState(): JSONObject {
        val power = context.getSystemService(PowerManager::class.java)
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val audio = context.getSystemService(AudioManager::class.java)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        return JSONObject().put("system", systemMetrics.read()).put("screenInteractive", power.isInteractive).put("keyguardLocked", keyguard.isKeyguardLocked)
            .put("deviceLocked", keyguard.isDeviceLocked).put("powerSaveMode", power.isPowerSaveMode)
            .put("batteryLevel", battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: JSONObject.NULL)
            .put("batteryScale", battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: JSONObject.NULL)
            .put("batteryTemperatureTenthsCelsius", battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: JSONObject.NULL)
            .put("batteryVoltageMillivolts", battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: JSONObject.NULL)
            .put("chargingSource", battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: JSONObject.NULL)
            .put("ringerMode", audio.ringerMode).put("mediaVolume", audio.getStreamVolume(AudioManager.STREAM_MUSIC))
            .put("mediaVolumeMaximum", audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            .put("networkAvailable", network != null).put("internetValidated", network?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false)
            .put("wifi", network?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false)
            .put("cellular", network?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false)
            .put("vpn", network?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ?: false)
    }

}
