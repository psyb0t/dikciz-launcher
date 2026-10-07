package org.fossify.home.dikciz

internal object DikcizAutomationDebugContract {
    const val ACTION_PHONE_STATE = "org.fossify.home.dikciz.action.DEBUG_PHONE_STATE"
    const val ACTION_DEVICE_ADMINISTRATION_DEACTIVATE =
        "org.fossify.home.dikciz.action.DEBUG_DEVICE_ADMINISTRATION_DEACTIVATE"
    const val ACTION_CLIPBOARD_SET_TEXT = "org.fossify.home.dikciz.action.DEBUG_CLIPBOARD_SET_TEXT"
    const val ACTION_HEALTH_DAILY_STEPS = "org.fossify.home.dikciz.action.DEBUG_HEALTH_DAILY_STEPS"
    const val ACTION_SMS_RECEIVED = "org.fossify.home.dikciz.action.DEBUG_SMS_RECEIVED"
    const val ACTION_SERVICE_PHONE_STATE = "org.fossify.home.dikciz.action.AUTOMATION_DEBUG_PHONE_STATE"
    const val EXTRA_CALL_STATE = "callState"
    const val EXTRA_CLIPBOARD_TEXT = "clipboardText"
    const val EXTRA_HEALTH_DAILY_STEPS = "healthDailySteps"
    const val EXTRA_PHONE_STATE = "phoneState"
    const val EXTRA_SMS_BODY = "smsBody"
    const val EXTRA_SMS_SENDER = "smsSender"
}
