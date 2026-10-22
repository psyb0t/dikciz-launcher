package org.fossify.home.dikciz

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject

internal object DikcizSmsStatus {
    private const val PREFERENCES = "dikciz_sms_status"
    private const val PREFIX = "record_"
    private const val MAXIMUM_RECORDS = 512
    private const val FINGERPRINT = "fingerprint"
    private const val MESSAGE_ID = "messageId"
    private const val PARTS = "parts"
    private const val SENT = "sentResults"
    private const val DELIVERY = "deliveryResults"
    private const val SUBMITTED = "submitted"
    private const val FAILED = "submissionFailed"
    private const val CREATED = "acceptedAtMilliseconds"
    private const val RESULT = "resultCode"
    private const val DELIVERY_STATUS = "deliveryStatus"
    private const val STAGE_SENT = "sent"
    private const val STAGE_DELIVERY = "delivery"
    private const val SCHEME = "dikciz-sms"
    private const val HOST = "callback"
    private const val ACTION = "org.fossify.home.dikciz.SMS_STATUS"
    private val NUL = 0.toChar().toString()

    @Synchronized
    fun send(context: Context, request: JSONObject): JSONObject {
        DikcizNativeCommands.requirePermission(context, Manifest.permission.SEND_SMS, "sms")
        val messagingFeature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) PackageManager.FEATURE_TELEPHONY_MESSAGING else PackageManager.FEATURE_TELEPHONY
        if (!context.packageManager.hasSystemFeature(messagingFeature)) {
            DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_UNAVAILABLE, "Device has no SMS support")
        }
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val key = request.getString(DikcizNativeCommands.KEY_REQUEST_KEY)
        val recipient = DikcizNativeCommands.normalizedRecipient(request.getString(DikcizNativeCommands.KEY_RECIPIENT))
        val storageKey = PREFIX + digest(key)
        // Compare the requested SIM, so a changed default cannot resend a retry.
        val requestedSIM = request.optInt(DikcizNativeCommands.KEY_SUBSCRIPTION, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        val fingerprint = digest(recipient + NUL + request.getString(DikcizNativeCommands.KEY_BODY) + NUL + requestedSIM)
        preferences.getString(storageKey, null)?.let { serialized ->
            val existing = JSONObject(serialized)
            if (existing.getString(FINGERPRINT) != fingerprint) DikcizNativeCommands.failure("sms_idempotency_conflict", "This key identifies a different SMS request")
            return document(existing).put("duplicate", true)
        }
        if (preferences.all.keys.count { it.startsWith(PREFIX) } >= MAXIMUM_RECORDS) {
            DikcizNativeCommands.failure("sms_status_capacity", "SMS receipt storage is full; nothing was submitted")
        }
        val subscription = if (requestedSIM != SubscriptionManager.INVALID_SUBSCRIPTION_ID) requestedSIM else SubscriptionManager.getDefaultSmsSubscriptionId()
        if (subscription < 0 || subscription == SubscriptionManager.DEFAULT_SUBSCRIPTION_ID) DikcizNativeCommands.failure("no_default_sms_subscription", "Select an active subscriptionId from simList")
        if (requestedSIM != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                DikcizNativeCommands.failure("native_permission_telephony", "Android phone-state permission is required")
            }
            val active = context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty()
            if (active.none { it.subscriptionId == subscription }) DikcizNativeCommands.invalid("subscriptionId is not active")
        }
        val base = context.getSystemService(SmsManager::class.java) ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_UNAVAILABLE, "SMS service unavailable")
        @Suppress("DEPRECATION")
        val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) base.createForSubscriptionId(subscription) else SmsManager.getSmsManagerForSubscriptionId(subscription)
        val parts = manager.divideMessage(request.getString(DikcizNativeCommands.KEY_BODY))
        if (parts.size !in 1..DikcizSmsReceipt.MAXIMUM_PARTS) DikcizNativeCommands.invalid("SMS requires too many segments")
        val record = JSONObject().put(MESSAGE_ID, UUID.randomUUID().toString()).put(FINGERPRINT, fingerprint)
            .put(DikcizNativeCommands.KEY_REQUEST_KEY, key).put(DikcizNativeCommands.KEY_SUBSCRIPTION, subscription)
            .put(PARTS, parts.size).put(SENT, JSONObject()).put(DELIVERY, JSONObject())
            .put(SUBMITTED, false).put(FAILED, false).put(CREATED, System.currentTimeMillis())
        // Persist before the irreversible modem call. An interrupted submission stays uncertain.
        if (!preferences.edit().putString(storageKey, record.toString()).commit()) DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_FAILED, "Could not persist SMS receipt; nothing was submitted")
        val sent = ArrayList(parts.indices.map { callback(context, record, STAGE_SENT, it) })
        val delivered = ArrayList(parts.indices.map { callback(context, record, STAGE_DELIVERY, it) })
        try {
            if (parts.size == 1) manager.sendTextMessage(recipient, null, parts[0], sent[0], delivered[0])
            else manager.sendMultipartTextMessage(recipient, null, parts, sent, delivered)
            record.put(SUBMITTED, true)
        } catch (exception: RuntimeException) {
            record.put(FAILED, true).put("failureClass", exception.javaClass.simpleName)
            logger(context).warn("sms_submission_failed", mapOf("error_class" to exception.javaClass.simpleName))
        }
        if (!preferences.edit().putString(storageKey, record.toString()).commit()) DikcizNativeCommands.failure("sms_status_persistence_failed", "SMS may have been submitted. Do not resend with a new key")
        logger(context).info("sms_submitted", mapOf("outcome" to document(record).getString(DikcizNativeCommands.KEY_STATE), "part_count" to parts.size))
        return document(record).put("duplicate", false)
    }

    @Synchronized
    fun status(context: Context, request: JSONObject): JSONObject {
        val serialized = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(PREFIX + digest(request.getString(DikcizNativeCommands.KEY_REQUEST_KEY)), null)
            ?: DikcizNativeCommands.failure("sms_status_not_found", "No receipt exists for this key")
        return document(JSONObject(serialized))
    }

    @Synchronized
    fun receive(context: Context, intent: Intent, resultCode: Int) {
        val uri = intent.data ?: return
        if (intent.action != ACTION || uri.scheme != SCHEME || uri.host != HOST || uri.pathSegments.size != 3) return
        val (messageID, stage, partValue) = uri.pathSegments
        if (stage !in setOf(STAGE_SENT, STAGE_DELIVERY)) return
        val part = partValue.toIntOrNull() ?: return
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val found = preferences.all.entries.firstOrNull { (key, value) -> key.startsWith(PREFIX) && value is String && JSONObject(value).optString(MESSAGE_ID) == messageID } ?: return
        val record = JSONObject(found.value as String)
        if (part !in 0 until record.getInt(PARTS)) return
        val outcome = JSONObject().put(RESULT, resultCode)
        if (intent.hasExtra("errorCode")) outcome.put("networkErrorCode", intent.getIntExtra("errorCode", 0))
        if (stage == STAGE_DELIVERY) {
            val pdu = intent.getByteArrayExtra("pdu")
            val format = intent.getStringExtra("format")
            if (pdu != null && format != null) {
                val message = try { SmsMessage.createFromPdu(pdu, format) } catch (exception: RuntimeException) {
                    logger(context).warn("sms_delivery_report_invalid", mapOf("error_class" to exception.javaClass.simpleName))
                    null
                }
                message?.let { outcome.put(DELIVERY_STATUS, it.status) }
            }
        }
        record.getJSONObject(if (stage == STAGE_SENT) SENT else DELIVERY).put(part.toString(), outcome)
        if (!preferences.edit().putString(found.key, record.toString()).commit()) {
            logger(context).error("sms_callback_save_failed")
            return
        }
        logger(context).info("sms_callback_received", mapOf("stage" to stage, "outcome" to document(record).getString(DikcizNativeCommands.KEY_STATE)))
    }

    private fun callback(context: Context, record: JSONObject, stage: String, part: Int): PendingIntent {
        val intent = Intent(context, DikcizSmsStatusReceiver::class.java).setAction(ACTION)
            .setData(Uri.Builder().scheme(SCHEME).authority(HOST).appendPath(record.getString(MESSAGE_ID)).appendPath(stage).appendPath(part.toString()).build())
        // Android fills delivery PDU data into the explicit private broadcast intent.
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
    }

    private fun document(record: JSONObject): JSONObject {
        val parts = record.getInt(PARTS)
        val sent = record.getJSONObject(SENT)
        val delivery = record.getJSONObject(DELIVERY)
        val outcomes = sent.keys().asSequence().associate { it.toInt() to sent.getJSONObject(it).getInt(RESULT) }
        val reports = delivery.keys().asSequence().associate { key ->
            val report = delivery.getJSONObject(key)
            key.toInt() to DikcizSmsDeliveryResult(report.getInt(RESULT), if (report.has(DELIVERY_STATUS)) report.getInt(DELIVERY_STATUS) else null)
        }
        val summary = DikcizSmsReceipt.summarize(parts, record.optBoolean(SUBMITTED), record.optBoolean(FAILED), outcomes, reports)
        return JSONObject().put(DikcizNativeCommands.KEY_STATE, summary.state).put(MESSAGE_ID, record.getString(MESSAGE_ID))
            .put(DikcizNativeCommands.KEY_REQUEST_KEY, record.getString(DikcizNativeCommands.KEY_REQUEST_KEY))
            .put(DikcizNativeCommands.KEY_SUBSCRIPTION, record.getInt(DikcizNativeCommands.KEY_SUBSCRIPTION))
            .put(PARTS, parts).put("sentParts", summary.sentParts).put("deliveredParts", summary.deliveredParts)
            .put(SENT, JSONObject(sent.toString())).put(DELIVERY, JSONObject(delivery.toString()))
            .put(CREATED, record.getLong(CREATED)).put("deliveryConfirmed", summary.deliveryConfirmed)
    }
    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { byte -> "%02x".format(byte) }
    private fun logger(context: Context) = DikcizLogger("DikcizSmsStatus", HomeConfigStore(context).logDirectory)
}

internal class DikcizSmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DikcizSmsStatus.receive(context.applicationContext, intent, resultCode)
    }
}
