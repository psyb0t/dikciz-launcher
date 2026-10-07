package org.fossify.home.dikciz

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject

internal data class DikcizIncomingSmsMessage(
    val body: String,
    val sender: String,
    val timestampMilliseconds: Long,
)

internal object DikcizSmsAutomation {
    const val PAYLOAD_MESSAGE_BODY = "body"
    const val PAYLOAD_MESSAGE_BODY_TRUNCATED = "bodyTruncated"
    const val PAYLOAD_MESSAGE_COUNT = "messageCount"
    const val PAYLOAD_MESSAGE_SENDER = "sender"
    const val PAYLOAD_MESSAGE_SENDER_TRUNCATED = "senderTruncated"
    const val PAYLOAD_MESSAGE_TIMESTAMP_MILLISECONDS = "timestampMilliseconds"
    const val PAYLOAD_MESSAGES = "messages"
    const val PAYLOAD_MESSAGES_TRUNCATED = "messagesTruncated"

    fun enqueue(context: Context, messages: List<DikcizIncomingSmsMessage>) {
        if (messages.isEmpty()) {
            return
        }
        val boundedMessages = messages.take(MAXIMUM_SMS_MESSAGE_COUNT)
        val payload = JSONObject()
            .put(PAYLOAD_MESSAGE_COUNT, boundedMessages.size)
            .put(PAYLOAD_MESSAGES_TRUNCATED, messages.size > boundedMessages.size)
            .put(
                PAYLOAD_MESSAGES,
                JSONArray().apply {
                    boundedMessages.forEach { message -> put(messagePayload(message)) }
                },
            )
        logger(context).info(
            EVENT_SMS_RECEIVED,
            mapOf(
                FIELD_MESSAGE_COUNT to boundedMessages.size,
                FIELD_MESSAGES_TRUNCATED to (messages.size > boundedMessages.size),
            ),
        )
        DikcizAutomationEventBus.enqueue(
            context,
            DikcizAutomationEvent(
                type = DikcizAutomationEventType.SmsReceived,
                source = SOURCE_SMS,
                timestampMilliseconds = System.currentTimeMillis(),
                coalescingKey = COALESCING_SMS_RECEIVED,
                payload = payload,
            ),
        )
    }

    private fun messagePayload(message: DikcizIncomingSmsMessage): JSONObject {
        return JSONObject()
            .put(PAYLOAD_MESSAGE_BODY, message.body.take(MAXIMUM_SMS_BODY_CHARACTERS))
            .put(
                PAYLOAD_MESSAGE_BODY_TRUNCATED,
                message.body.length > MAXIMUM_SMS_BODY_CHARACTERS,
            )
            .put(PAYLOAD_MESSAGE_SENDER, message.sender.take(MAXIMUM_SMS_SENDER_CHARACTERS))
            .put(
                PAYLOAD_MESSAGE_SENDER_TRUNCATED,
                message.sender.length > MAXIMUM_SMS_SENDER_CHARACTERS,
            )
            .put(PAYLOAD_MESSAGE_TIMESTAMP_MILLISECONDS, message.timestampMilliseconds)
    }

    private fun logger(context: Context): DikcizLogger {
        val store = HomeConfigStore(context.applicationContext)
        return DikcizLogger(LOG_TAG, store.logDirectory)
    }

    private const val COALESCING_SMS_RECEIVED = "sms-received"
    private const val EVENT_SMS_RECEIVED = "automation_sms_received"
    private const val FIELD_MESSAGE_COUNT = "message_count"
    private const val FIELD_MESSAGES_TRUNCATED = "messages_truncated"
    private const val LOG_TAG = "DikcizSmsReceiver"
    private const val MAXIMUM_SMS_BODY_CHARACTERS = 512
    private const val MAXIMUM_SMS_MESSAGE_COUNT = 16
    private const val MAXIMUM_SMS_SENDER_CHARACTERS = 128
    private const val SOURCE_SMS = "sms"
}

internal class DikcizSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }
        if (
            context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            logger(context).warn(
                EVENT_SMS_RECEIPT_REJECTED,
                mapOf(FIELD_REASON to REASON_PERMISSION_MISSING),
            )
            return
        }
        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (exception: RuntimeException) {
            logger(context).warn(
                EVENT_SMS_RECEIPT_REJECTED,
                mapOf(
                    FIELD_ERROR_CLASS to exception::class.java.simpleName,
                    FIELD_REASON to REASON_PLATFORM_PAYLOAD_INVALID,
                ),
            )
            return
        }
        if (messages.isEmpty()) {
            logger(context).warn(
                EVENT_SMS_RECEIPT_REJECTED,
                mapOf(FIELD_REASON to REASON_MESSAGES_MISSING),
            )
            return
        }
        DikcizSmsAutomation.enqueue(
            context,
            messages.map { message ->
                DikcizIncomingSmsMessage(
                    body = message.messageBody.orEmpty(),
                    sender = message.originatingAddress.orEmpty(),
                    timestampMilliseconds = message.timestampMillis,
                )
            },
        )
    }

    private fun logger(context: Context): DikcizLogger {
        val store = HomeConfigStore(context.applicationContext)
        return DikcizLogger(LOG_TAG, store.logDirectory)
    }

    private companion object {
        const val EVENT_SMS_RECEIPT_REJECTED = "automation_sms_receipt_rejected"
        const val FIELD_ERROR_CLASS = "error_class"
        const val FIELD_REASON = "reason"
        const val LOG_TAG = "DikcizSmsReceiver"
        const val REASON_MESSAGES_MISSING = "messages_missing"
        const val REASON_PERMISSION_MISSING = "permission_missing"
        const val REASON_PLATFORM_PAYLOAD_INVALID = "platform_payload_invalid"
    }
}
