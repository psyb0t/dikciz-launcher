package org.fossify.home.dikciz

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.Locale
import org.json.JSONObject

/** Shared urgent app window; app code supplies its text, deadline and cancellation event. */
internal class DikcizSosWarning(
    private val context: Context,
    private val logger: DikcizLogger,
    private val cancel: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val modal = DikcizAlertModal(context, logger)
    private val views = linkedMapOf<String, View>()
    private var root: View? = null
    private var scroll: ScrollView? = null
    private var countdown: TextView? = null
    private var cancelButton: Button? = null
    private var episode = ""
    private var state: JSONObject? = null
    private var presentation = "notification"
    private var tone: ToneGenerator? = null
    private var speech: TextToSpeech? = null
    private var speechState = "idle"
    private var finalAnnouncement = false
    private var nextPulse = 0L
    private var cancelling = false
    private var presentationFailed = false

    fun update(next: JSONObject?) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (next == null) { close(); return }
        val key = next.getString("episodeId")
        if (episode != key) {
            close()
            episode = key
            state = next
            current = WeakReference(this)
            createView()
            startSpeech(key)
        }
        state = next
        if (modal.visibleRoot() == null && !presentationFailed) present()
        val seconds = next.getLong("remainingSeconds")
        countdown?.text = if (seconds > 0) "$seconds seconds" else "Countdown reached"
        views[semanticID("details")]?.let { (it as TextView).text = next.getString("detail") }
        if (!cancelling) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now >= nextPulse) {
                pulse()
                nextPulse = now + PULSE_INTERVAL
            }
            if (seconds in 1..FINAL_SECONDS && !finalAnnouncement && next.optLong("graceSeconds") > FINAL_SECONDS) {
                finalAnnouncement = true
                announce(next.optString("finalAnnouncement").replace("{seconds}", seconds.toString()))
            }
        }
        publish()
    }

    private fun createView() {
        val activity = if (Settings.canDrawOverlays(context)) null else DikcizForegroundActivity.current(context)
        val ui = activity ?: context
        fun dp(value: Int) = (value * ui.resources.displayMetrics.density).toInt()
        val column = LinearLayout(ui).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            background = GradientDrawable().apply { setColor(DikcizUiStyle.BACKGROUND); cornerRadius = dp(24).toFloat() }
        }
        val body = LinearLayout(ui).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(ui).apply { addView(body); contentDescription = "App alert details" }
        column.addView(scroll, LinearLayout.LayoutParams(-1, -2, 1f))
        fun text(id: String, value: String, size: Float, color: Int): TextView = TextView(ui).apply {
            text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8)); views[id] = this
            body.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        text(semanticID("title"), state?.optString("title", "Urgent countdown").orEmpty(), 24f, DikcizUiStyle.ERROR)
        countdown = text(semanticID("countdown"), "Preparing countdown", 40f, DikcizUiStyle.FOREGROUND)
        countdown?.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        text(semanticID("details"), "", 17f, DikcizUiStyle.FOREGROUND)
        cancelButton = Button(ui).apply {
            text = state?.optString("cancelLabel", "Cancel").orEmpty(); contentDescription = text
            DikcizUiStyle.button(this, true); textSize = 22f; minimumHeight = dp(72)
            setOnClickListener {
                if (!cancelling) {
                    cancelling = true
                    isEnabled = false
                    silence()
                    cancel()
                }
            }
            views[semanticID("cancel")] = this
            column.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        }
        root = column.apply { contentDescription = "App cancellation countdown" }
    }

    private fun present() {
        val view = root ?: return
        try {
            if (modal.show(view)) {
                presentation = modal.presentation
            }
        } catch (exception: RuntimeException) {
            presentationFailed = true
            presentation = "notification"
            modal.close()
            logger.warn("sos_warning_window_unavailable", mapOf("error_class" to exception.javaClass.simpleName))
        }
    }

    private fun pulse() {
        if (speech?.isSpeaking != true) {
            try {
                if (tone == null) tone = ToneGenerator(AudioManager.STREAM_ALARM, TONE_VOLUME)
                tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, TONE_MILLISECONDS)
            } catch (exception: RuntimeException) {
                logger.warn("sos_warning_tone_unavailable", mapOf("error_class" to exception.javaClass.simpleName))
            }
        }
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator?.hasVibrator() == true) {
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATION_MILLISECONDS, VibrationEffect.DEFAULT_AMPLITUDE),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    private fun startSpeech(key: String) {
        if (state?.optString("announcement").isNullOrBlank()) { speechState = "disabled"; return }
        speechState = "initializing"
        try {
            speech = TextToSpeech(context) { result -> handler.post {
                if (episode != key) return@post
                val engine = speech
                val voice = engine?.voices?.firstOrNull { !it.isNetworkConnectionRequired &&
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features && it.locale.language == Locale.ENGLISH.language }
                if (result != TextToSpeech.SUCCESS || engine == null || voice == null) {
                    speechState = "unavailable"
                    logger.warn("sos_warning_speech_unavailable", mapOf("reason" to "offline_english_voice_unavailable"))
                } else {
                    if (engine.setVoice(voice) != TextToSpeech.SUCCESS) {
                        speechState = "unavailable"
                        publish()
                        return@post
                    }
                    engine.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String) = changeSpeech(key, "speaking")
                        override fun onDone(id: String) = changeSpeech(key, "spoken")
                        @Deprecated("Required Android callback")
                        override fun onError(id: String) = changeSpeech(key, "unavailable")
                    })
                    speechState = "ready"
                    val seconds = state?.optLong("remainingSeconds") ?: 0
                    announce(state?.optString("announcement").orEmpty().replace("{seconds}", seconds.toString()))
                }
                publish()
            } }
        } catch (exception: RuntimeException) {
            speechState = "unavailable"
            logger.warn("sos_warning_speech_unavailable", mapOf("error_class" to exception.javaClass.simpleName))
        }
    }

    private fun changeSpeech(key: String, value: String) { handler.post {
        if (episode == key) { speechState = value; publish() }
    } }

    private fun announce(text: String) {
        if (text.isBlank()) return
        val engine = speech ?: return
        if (speechState == "initializing" || speechState == "unavailable" || cancelling) return
        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, episode) == TextToSpeech.ERROR) speechState = "unavailable"
        else speechState = "queued"
    }

    private fun publish() {
        val value = state ?: return
        published = JSONObject(value.toString()).put("active", true)
            .put("presentation", presentation).put("screenVisible", modal.visibleRoot() != null &&
                context.getSystemService(android.os.PowerManager::class.java).isInteractive &&
                !context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)
            .put("soundRequested", !cancelling).put("vibrationRequested", !cancelling)
            .put("alarmVolume", context.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_ALARM))
            .put("speechState", speechState).toString()
    }

    fun cancelFailed() { cancelling = false; cancelButton?.isEnabled = true }
    fun semanticViews(): Map<String, View> = modal.visibleRoot()?.let { views + (semanticID("backdrop") to it) } ?: emptyMap()
    fun visibleRoot(): View? = modal.visibleRoot()
    fun scrollState(): JSONObject {
        val view = scroll
        return JSONObject().put("y", view?.scrollY ?: 0).put("range", view?.getChildAt(0)?.height ?: 0)
            .put("extent", view?.height ?: 0)
    }
    fun scrollBy(delta: Int) { scroll?.scrollBy(0, delta) }
    fun scrollTo(view: View) { view.requestRectangleOnScreen(android.graphics.Rect(0, 0, view.width, view.height), true) }


    private fun silence() {
        tone?.stopTone(); tone?.release(); tone = null
        speech?.stop(); speech?.shutdown(); speech = null
        speechState = "idle"
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.cancel()
    }

    fun relayout() {
        val next = state ?: return
        removePresentation()
        presentationFailed = false
        createView()
        update(next)
    }

    private fun removePresentation() {
        modal.close()
        root = null; scroll = null; views.clear(); countdown = null; cancelButton = null
    }

    fun close() {
        episode = ""
        state = null
        silence()
        removePresentation()
        finalAnnouncement = false; nextPulse = 0; cancelling = false; presentationFailed = false; presentation = "notification"
        if (current.get() === this) {
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
            current.clear(); published = IDLE
        }
    }

    private fun semanticID(suffix: String): String {
        val appId = state?.optString("appId").orEmpty()
        val prefix = if (appId == "sos") "sos:warning" else "app:$appId:warning"
        return "$prefix:$suffix"
    }

    companion object {
        const val CHANNEL = "dikciz-sos-warning-v1"
        const val NOTIFICATION_ID = 7316
        private const val TITLE_ID = "sos:warning:title"
        private const val COUNTDOWN_ID = "sos:warning:countdown"
        private const val DETAIL_ID = "sos:warning:details"
        const val CANCEL_ID = "sos:warning:cancel"
        private const val PULSE_INTERVAL = 5000L
        private const val FINAL_SECONDS = 10L
        private const val TONE_VOLUME = 80
        private const val TONE_MILLISECONDS = 350
        private const val VIBRATION_MILLISECONDS = 300L
        private const val BACKDROP_ID = "sos:warning:backdrop"
        private const val IDLE = "{\"active\":false,\"screenVisible\":false,\"speechState\":\"idle\"}"
        @Volatile private var published = IDLE
        @Volatile private var current = WeakReference<DikcizSosWarning>(null)
        fun current() = current.get()
        fun status(): JSONObject {
            val value = JSONObject(published)
            current.get()?.let { warning ->
                value.put("screenVisible", warning.modal.visibleRoot() != null &&
                    warning.context.getSystemService(android.os.PowerManager::class.java).isInteractive &&
                    !warning.context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)
            }
            return value
        }
        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Urgent app countdowns", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Owner-enabled custom app alerts with a visible cancellation countdown"
                    setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                })
        }
        fun dismiss(runId: String) { Handler(Looper.getMainLooper()).post {
            current.get()?.takeIf { it.state?.optString("runId") == runId }?.close()
        } }
    }
}
