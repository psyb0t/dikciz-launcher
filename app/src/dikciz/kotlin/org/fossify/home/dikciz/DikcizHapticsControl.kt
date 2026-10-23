package org.fossify.home.dikciz

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresApi
import org.json.JSONArray
import org.json.JSONObject

internal object DikcizHapticsControl {
    private const val MAXIMUM_SEGMENTS = 64
    private const val MAXIMUM_DURATION = 10000L
    private const val DEFAULT_DURATION = 250L
    private val EFFECT_NAMES = listOf("click", "doubleClick", "heavyClick", "tick")
    val FIELDS = mapOf(
        "action" to text(16).put("description", "status, vibrate, waveform, effect or cancel."),
        "durationMilliseconds" to integer(1, MAXIMUM_DURATION).put("default", DEFAULT_DURATION),
        "amplitude" to integer(-1, 255).put("default", VibrationEffect.DEFAULT_AMPLITUDE),
        "timingsMilliseconds" to array(integer(0, MAXIMUM_DURATION)),
        "amplitudes" to array(integer(-1, 255)),
        "effect" to text(16).put("description", "click, doubleClick, heavyClick or tick. Android may use its predefined fallback."),
    )

    @Suppress("DEPRECATION")
    fun execute(context: Context, request: JSONObject): JSONObject {
        val vibrator = context.getSystemService(Vibrator::class.java)
            ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_UNAVAILABLE, "Vibrator service unavailable")
        val available = vibrator.hasVibrator()
        val action = request.getString("action")
        if (action == "status") {
            val support = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                vibrator.areEffectsSupported(
                    VibrationEffect.EFFECT_CLICK,
                    VibrationEffect.EFFECT_DOUBLE_CLICK,
                    VibrationEffect.EFFECT_HEAVY_CLICK,
                    VibrationEffect.EFFECT_TICK,
                ).map { value ->
                    when (value) {
                        Vibrator.VIBRATION_EFFECT_SUPPORT_YES -> "supported"
                        Vibrator.VIBRATION_EFFECT_SUPPORT_NO -> "unsupported"
                        else -> "unknown"
                    }
                }
            } else {
                List(EFFECT_NAMES.size) { "unknown" }
            }
            val effects = EFFECT_NAMES.mapIndexed { index, name ->
                JSONObject().put("effect", name).put("nativeSupport", support[index])
            }
            return JSONObject().put("available", available).put("amplitudeControl", vibrator.hasAmplitudeControl())
                .put("effects", JSONArray(effects))
        }
        if (action == "cancel") {
            vibrator.cancel()
            return JSONObject().put("state", "cancelled")
        }
        if (!available) DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_UNAVAILABLE, "Device has no vibrator")
        val result = JSONObject().put("state", "submitted").put("action", action)
        val effect = when (action) {
            "waveform" -> {
                val times = request.getJSONArray("timingsMilliseconds")
                val timings = LongArray(times.length()) { times.getLong(it) }
                result.put("durationMilliseconds", timings.sum()).put("repeating", false)
                if (request.has("amplitudes")) {
                    val values = request.getJSONArray("amplitudes")
                    VibrationEffect.createWaveform(timings, IntArray(values.length()) { values.getInt(it) }, NO_REPEAT)
                } else {
                    VibrationEffect.createWaveform(timings, NO_REPEAT)
                }
            }
            "effect" -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    DikcizNativeCommands.failure("unsupported_android_version", "Predefined vibration effects require Android 10")
                }
                val name = request.getString("effect")
                result.put("effect", name).put("fallbackPossible", true)
                VibrationEffect.createPredefined(effectId(name))
            }
            else -> {
                val duration = request.optLong("durationMilliseconds", DEFAULT_DURATION)
                result.put("durationMilliseconds", duration)
                VibrationEffect.createOneShot(duration, request.optInt("amplitude", VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
        vibrator.vibrate(effect)
        return result
    }

    fun validate(request: JSONObject) {
        val action = request.getString("action")
        val allowed = when (action) {
            "status", "cancel" -> setOf("action")
            "vibrate" -> setOf("action", "durationMilliseconds", "amplitude")
            "waveform" -> setOf("action", "timingsMilliseconds", "amplitudes")
            "effect" -> setOf("action", "effect")
            else -> DikcizNativeCommands.invalid("Unsupported vibration action")
        }
        if (FIELDS.keys.any { it !in allowed && request.has(it) }) {
            DikcizNativeCommands.invalid("Vibration fields do not match the requested action")
        }
        if (action == "vibrate" && request.optInt("amplitude", VibrationEffect.DEFAULT_AMPLITUDE) == 0) {
            DikcizNativeCommands.invalid("One-shot amplitude must be -1 or 1 through 255")
        }
        if (action == "effect" && request.optString("effect") !in EFFECT_NAMES) {
            DikcizNativeCommands.invalid("Unsupported predefined vibration effect")
        }
        if (action != "waveform") return
        val timings = request.optJSONArray("timingsMilliseconds")
            ?: DikcizNativeCommands.invalid("Waveform requires timingsMilliseconds")
        if (timings.length() !in 1..MAXIMUM_SEGMENTS) DikcizNativeCommands.invalid("Waveform requires 1 through 64 segments")
        var total = 0L
        for (index in 0 until timings.length()) {
            total += DikcizNativeCommands.strictInteger(timings.get(index), 0, MAXIMUM_DURATION)
        }
        if (total !in 1..MAXIMUM_DURATION) DikcizNativeCommands.invalid("Waveform duration must be 1 through 10000 milliseconds")
        if (!request.has("amplitudes")) return
        val amplitudes = request.getJSONArray("amplitudes")
        if (amplitudes.length() != timings.length()) DikcizNativeCommands.invalid("Waveform arrays must have matching lengths")
        for (index in 0 until amplitudes.length()) {
            DikcizNativeCommands.strictInteger(amplitudes.get(index), -1, 255)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun effectId(name: String): Int = when (name) {
        "click" -> VibrationEffect.EFFECT_CLICK
        "doubleClick" -> VibrationEffect.EFFECT_DOUBLE_CLICK
        "heavyClick" -> VibrationEffect.EFFECT_HEAVY_CLICK
        "tick" -> VibrationEffect.EFFECT_TICK
        else -> DikcizNativeCommands.invalid("Unsupported predefined vibration effect")
    }

    private const val NO_REPEAT = -1
    private fun text(maximum: Int) = JSONObject().put("type", "string").put("maxLength", maximum)
    private fun integer(minimum: Int, maximum: Long) =
        JSONObject().put("type", "integer").put("minimum", minimum).put("maximum", maximum)
    private fun array(items: JSONObject) = JSONObject().put("type", "array").put("items", items)
        .put("minItems", 1).put("maxItems", MAXIMUM_SEGMENTS)
}
