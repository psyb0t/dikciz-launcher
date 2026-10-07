package org.fossify.home.dikciz

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import org.json.JSONObject

internal object DikcizClipboardMonitor {
    private var activeMonitor: ActiveClipboardMonitor? = null

    @Synchronized
    fun sync(context: Context, configuration: HomeConfiguration) {
        if (!hasEnabledSubscription(configuration)) {
            stop()
            return
        }
        if (activeMonitor != null) {
            return
        }
        val application = context.applicationContext as? Application ?: return
        val clipboardManager = application.getSystemService(ClipboardManager::class.java) ?: return
        activeMonitor = ActiveClipboardMonitor(
            application,
            clipboardManager,
        ).also(ActiveClipboardMonitor::start)
    }

    @Synchronized
    fun stop() {
        activeMonitor?.stop()
        activeMonitor = null
    }

    @Synchronized
    fun isActive(): Boolean = activeMonitor != null

    private fun hasEnabledSubscription(configuration: HomeConfiguration): Boolean {
        return configuration.automation.scripts.any { script ->
            script.enabled && configuration.automation.policy(script.policyID)?.enabled == true &&
                script.subscriptions.any { subscription ->
                    subscription.event == DikcizAutomationEventType.ClipboardChanged
                }
        }
    }

    private class ActiveClipboardMonitor(
        private val application: Application,
        private val clipboardManager: ClipboardManager,
    ) {
        private val listener = ClipboardManager.OnPrimaryClipChangedListener(::dispatchClipboardChange)
        @Volatile
        private var registered = false

        fun start() {
            clipboardManager.addPrimaryClipChangedListener(listener)
            registered = true
        }

        fun stop() {
            registered = false
            clipboardManager.removePrimaryClipChangedListener(listener)
        }

        private fun dispatchClipboardChange() {
            if (!registered) {
                return
            }
            val clip = try {
                clipboardManager.primaryClip
            } catch (_: SecurityException) {
                return
            } ?: return
            DikcizAutomationEventBus.enqueue(
                application,
                automationEvent(clip),
            )
        }

        private fun automationEvent(clip: ClipData): DikcizAutomationEvent {
            val directText = clip.getItemAt(FIRST_CLIPBOARD_ITEM_INDEX).text?.toString()
            val payload = JSONObject()
                .put(PAYLOAD_HAS_TEXT, directText != null)
                .put(PAYLOAD_ITEM_COUNT, clip.itemCount.coerceAtMost(MAXIMUM_CLIPBOARD_ITEM_COUNT))
            directText?.let { text ->
                payload.put(PAYLOAD_TEXT, text.take(MAXIMUM_CLIPBOARD_TEXT_CHARACTERS))
                payload.put(PAYLOAD_TEXT_TRUNCATED, text.length > MAXIMUM_CLIPBOARD_TEXT_CHARACTERS)
            }
            return DikcizAutomationEvent(
                type = DikcizAutomationEventType.ClipboardChanged,
                source = SOURCE_CLIPBOARD,
                timestampMilliseconds = System.currentTimeMillis(),
                coalescingKey = COALESCING_KEY_CLIPBOARD,
                payload = payload,
            )
        }
    }

    private const val COALESCING_KEY_CLIPBOARD = "clipboard"
    private const val FIRST_CLIPBOARD_ITEM_INDEX = 0
    private const val MAXIMUM_CLIPBOARD_ITEM_COUNT = 16
    private const val MAXIMUM_CLIPBOARD_TEXT_CHARACTERS = 1_024
    private const val PAYLOAD_HAS_TEXT = "hasText"
    private const val PAYLOAD_ITEM_COUNT = "itemCount"
    private const val PAYLOAD_TEXT = "text"
    private const val PAYLOAD_TEXT_TRUNCATED = "textTruncated"
    private const val SOURCE_CLIPBOARD = "clipboard"
}
