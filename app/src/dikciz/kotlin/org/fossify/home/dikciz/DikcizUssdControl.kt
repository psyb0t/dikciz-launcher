package org.fossify.home.dikciz

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject

internal object DikcizUssdControl {
    const val TYPE = "ussdRequest"
    private const val DEFAULT_TIMEOUT = 30000L
    private const val DEFAULT_RESPONSE_CHARACTERS = 4096
    private const val MAXIMUM_CODE_LENGTH = 182
    private val codePattern = Regex("[*#][0-9*#+]*#")
    private val busy = AtomicBoolean()
    private val main = Handler(Looper.getMainLooper())

    fun execute(context: Context, request: JSONObject): JSONObject {
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            DikcizNativeCommands.failure("native_permission_telephony", "Android phone-call permission is required for USSD")
        }
        val subscription = if (request.has("subscriptionId")) request.getInt("subscriptionId")
            else SubscriptionManager.getDefaultVoiceSubscriptionId()
        if (subscription <= SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            DikcizNativeCommands.failure("no_default_voice_subscription", "Choose an active voice subscription explicitly")
        }
        val manager = context.getSystemService(TelephonyManager::class.java)
            ?: DikcizNativeCommands.failure("ussd_unavailable", "Android telephony service is unavailable")
        if (!busy.compareAndSet(false, true)) {
            DikcizNativeCommands.failure("hardware_busy", "Another Dikciz USSD observation is active")
        }
        val active = AtomicBoolean(true)
        val terminal = AtomicBoolean()
        val completed = CountDownLatch(1)
        val response = AtomicReference<String?>()
        val failure = AtomicReference<String?>()
        try {
            manager.createForSubscriptionId(subscription).sendUssdRequest(
                request.getString("code"),
                object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(
                        telephonyManager: TelephonyManager,
                        originalRequest: String?,
                        received: CharSequence?,
                    ) {
                        if (!active.get() || !terminal.compareAndSet(false, true)) return
                        val text = received?.toString()
                        if (text == null) failure.set("ussd_empty_response") else response.set(text)
                        completed.countDown()
                    }

                    override fun onReceiveUssdResponseFailed(
                        telephonyManager: TelephonyManager,
                        originalRequest: String?,
                        failureCode: Int,
                    ) {
                        if (!active.get() || !terminal.compareAndSet(false, true)) return
                        failure.set(when (failureCode) {
                            TelephonyManager.USSD_RETURN_FAILURE -> "ussd_network_rejected"
                            TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL -> "ussd_service_unavailable"
                            else -> "ussd_android_error_" + failureCode
                        })
                        completed.countDown()
                    }
                },
                main,
            )
            if (!completed.await(request.optLong("timeoutMilliseconds", DEFAULT_TIMEOUT), TimeUnit.MILLISECONDS)) {
                DikcizNativeCommands.failure("ussd_timeout", "USSD network outcome is unknown; Android cannot cancel this request")
            }
            failure.get()?.let { DikcizNativeCommands.failure(it, "Android did not confirm successful USSD completion") }
            val text = response.get() ?: DikcizNativeCommands.failure("ussd_empty_response", "Android returned no USSD response")
            val maximum = request.optInt("maximumResponseCharacters", DEFAULT_RESPONSE_CHARACTERS)
            val bounded = boundedUssdResponse(text, maximum)
            return JSONObject().put("state", "responded").put("subscriptionId", subscription)
                .put("response", bounded.text).put("responseTruncated", bounded.truncated)
                .put("responseCharacters", bounded.characters).put("cancellationSupported", false)
        } catch (exception: UnsupportedOperationException) {
            throw DikcizAutomationRequestException("ussd_unavailable", "This device does not support Android USSD requests")
                .apply { initCause(exception) }
        } finally {
            active.set(false)
            busy.set(false)
        }
    }

    val DEFINITION = DikcizNativeCommands.Definition(TYPE, "dikciz_ussd_request",
        "Send one complete numeric star/hash USSD code through Android TelephonyManager and observe its carrier response. Requires CALL_PHONE. Optional subscriptionId defaults to the voice SIM. USSD can change carrier services or incur charges. Timeout/interruption cannot cancel network work; do not repeat an uncertain request automatically. Use jobStart for reconnectable duplicate-protected submission.",
        mapOf(
            "code" to JSONObject().put("type", "string").put("minLength", 2).put("maxLength", MAXIMUM_CODE_LENGTH)
                .put("description", "Full USSD code beginning with star/hash and ending with hash; digits, star, hash and plus only."),
            "subscriptionId" to integer(0, Int.MAX_VALUE),
            "timeoutMilliseconds" to integer(1, 120000).put("default", DEFAULT_TIMEOUT),
            "maximumResponseCharacters" to integer(1, MAXIMUM_USSD_RESPONSE_CHARACTERS).put("default", DEFAULT_RESPONSE_CHARACTERS).put("description", "Maximum returned Unicode code points; responseTruncated reports shortening."),
        ), setOf("code"), readOnly = false)

    fun validate(request: JSONObject) {
        if (!codePattern.matches(request.getString("code")) || request.getString("code").none { it in '0'..'9' }) {
            DikcizNativeCommands.invalid("USSD requires a complete numeric star/hash code")
        }
    }

    private fun integer(minimum: Int, maximum: Int) =
        JSONObject().put("type", "integer").put("minimum", minimum).put("maximum", maximum)
}

internal const val MAXIMUM_USSD_RESPONSE_CHARACTERS = 65536

internal data class DikcizUssdResponseText(val text: String, val characters: Int, val truncated: Boolean)

internal fun boundedUssdResponse(text: String, maximum: Int): DikcizUssdResponseText {
    require(maximum in 1..MAXIMUM_USSD_RESPONSE_CHARACTERS)
    val characters = text.codePointCount(0, text.length)
    val end = if (characters <= maximum) text.length else text.offsetByCodePoints(0, maximum)
    return DikcizUssdResponseText(text.substring(0, end), characters, characters > maximum)
}
