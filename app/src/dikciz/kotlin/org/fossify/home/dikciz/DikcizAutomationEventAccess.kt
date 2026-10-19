package org.fossify.home.dikciz

import org.json.JSONObject

/**
 * Decides what an automation policy lets a script see of one event.
 *
 * The event's capability, or its content counterpart, admits delivery. Content
 * fields reach the script only when the policy also grants the content
 * capability. Without it the script receives a copy of the payload with those
 * fields removed.
 */
internal object DikcizAutomationEventAccess {
    fun allowsDelivery(
        policy: DikcizAutomationPolicy,
        event: DikcizAutomationEvent,
    ): Boolean {
        val requiredCapability = event.locationPrecision?.capability ?: event.type.capability
        if (requiredCapability in policy.capabilities) {
            return true
        }
        val contentCapability = CONTENT_RULES[event.type]?.capability ?: return false
        return contentCapability in policy.capabilities
    }

    fun visibleEvent(
        policy: DikcizAutomationPolicy,
        event: DikcizAutomationEvent,
    ): DikcizAutomationEvent {
        val contentRule = CONTENT_RULES[event.type] ?: return event
        if (contentRule.capability in policy.capabilities) {
            return event
        }
        val visiblePayload = JSONObject(event.payload.toString())
        contentRule.payloadKeys.forEach(visiblePayload::remove)
        return event.copy(payload = visiblePayload)
    }

    private data class ContentRule(
        val capability: DikcizAutomationCapability,
        val payloadKeys: Set<String>,
    )

    private const val PAYLOAD_ALBUM = "album"
    private const val PAYLOAD_ARTIST = "artist"
    private const val PAYLOAD_DESCRIPTION = "description"
    private const val PAYLOAD_LOCATION = "location"
    private const val PAYLOAD_MESSAGES = "messages"
    private const val PAYLOAD_TEXT = "text"
    private const val PAYLOAD_TEXT_TRUNCATED = "textTruncated"
    private const val PAYLOAD_TITLE = "title"

    private val NOTIFICATION_CONTENT = ContentRule(
        DikcizAutomationCapability.NotificationsContent,
        setOf(PAYLOAD_TITLE, PAYLOAD_TEXT),
    )

    private val CONTENT_RULES = mapOf(
        DikcizAutomationEventType.CalendarEvent to ContentRule(
            DikcizAutomationCapability.CalendarEventsContent,
            setOf(PAYLOAD_TITLE, PAYLOAD_DESCRIPTION, PAYLOAD_LOCATION),
        ),
        DikcizAutomationEventType.ClipboardChanged to ContentRule(
            DikcizAutomationCapability.ClipboardContent,
            setOf(PAYLOAD_TEXT, PAYLOAD_TEXT_TRUNCATED),
        ),
        DikcizAutomationEventType.MediaSession to ContentRule(
            DikcizAutomationCapability.MediaSessionsContent,
            setOf(PAYLOAD_TITLE, PAYLOAD_ARTIST, PAYLOAD_ALBUM),
        ),
        DikcizAutomationEventType.NotificationPosted to NOTIFICATION_CONTENT,
        DikcizAutomationEventType.NotificationRemoved to NOTIFICATION_CONTENT,
        DikcizAutomationEventType.SmsReceived to ContentRule(
            DikcizAutomationCapability.SmsContent,
            setOf(PAYLOAD_MESSAGES),
        ),
    )
}
