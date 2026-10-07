package org.fossify.home.dikciz

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

class DikcizDebugClipboardReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DikcizAutomationDebugContract.ACTION_CLIPBOARD_SET_TEXT) {
            return
        }
        val text = intent.getStringExtra(DikcizAutomationDebugContract.EXTRA_CLIPBOARD_TEXT) ?: return
        if (text.length > MAXIMUM_DEBUG_CLIPBOARD_TEXT_CHARACTERS) {
            return
        }
        val clipboardManager = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboardManager.setPrimaryClip(ClipData.newPlainText(EMPTY_CLIP_LABEL, text))
    }

    private companion object {
        const val EMPTY_CLIP_LABEL = ""
        const val MAXIMUM_CLIPBOARD_TEXT_CHARACTERS = 1_024
        const val MAXIMUM_DEBUG_CLIPBOARD_TEXT_CHARACTERS = MAXIMUM_CLIPBOARD_TEXT_CHARACTERS * 2
    }
}
