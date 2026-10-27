package org.fossify.home.dikciz

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.fossify.home.dikciz.DikcizChatColors.FOREGROUND
import org.fossify.home.dikciz.DikcizChatColors.MUTED

internal class DikcizChatActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var runtime: DikcizChatRuntime
    private lateinit var root: FrameLayout
    private lateinit var panel: LinearLayout
    private lateinit var messages: LinearLayout
    private lateinit var scroll: ChatScrollView
    private lateinit var composer: EditText
    private lateinit var latest: Button
    private lateinit var title: Button
    private lateinit var status: TextView
    private lateinit var markwon: Markwon
    private lateinit var modelControls: DikcizChatModelControls
    private val rendered = mutableMapOf<Long, MessageView>()
    private var conversationID: String? = null
    private var fullScreen = false
    private var followLatest = true
    private var rendering = false
    private var renderAgain = false
    private val observer: () -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = DikcizChatRuntime.get(this)
        markwon = Markwon.builder(this).usePlugin(TablePlugin.create(this)).usePlugin(StrikethroughPlugin.create()).build()
        conversationID = savedInstanceState?.getString("conversationId") ?: intent.getStringExtra("conversationId")
        fullScreen = savedInstanceState?.getBoolean("fullScreen") ?: intent.getBooleanExtra("fullScreen", false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        root = FrameLayout(this).apply { setBackgroundColor(DIM); setOnClickListener { finish() } }
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isClickable = true
        }
        root.addView(panel, FrameLayout.LayoutParams(-1, -1, Gravity.BOTTOM))
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        title = button("Dikciz", "Choose conversation") { chooseConversation() }
        header.addView(title, LinearLayout.LayoutParams(0, dp(44), 1f))
        header.addView(button("+", "New conversation") { newConversation() })
        header.addView(button("Settings", "Chat provider settings") { chooseProfile() })
        header.addView(button("↕", "Expand or reduce chat") {
            fullScreen = !fullScreen
            resize()
        })
        header.addView(button("×", "Hide Dikciz chat") { finish() })
        panel.addView(header)
        status = textView().apply { textSize = 11f; setTextColor(MUTED); contentDescription = "Chat agent status" }
        panel.addView(status)
        modelControls = DikcizChatModelControls(this, { conversationID }) { request, completed -> command(request, completed) }
        panel.addView(modelControls.view)
        val content = FrameLayout(this)
        scroll = ChatScrollView(this).apply {
            isFillViewport = true
            contentDescription = "Chat messages"
            onReadingOlder = { followLatest = false; updateLatest() }
            onUserScroll = {
                if (height + scrollY >= getChildAt(0).height - dp(32)) followLatest = true
                updateLatest()
            }
        }
        messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(messages, FrameLayout.LayoutParams(-1, -2))
        content.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        latest = button("Latest ↓", "Jump to latest message") { followLatest = true; scrollLatest(); updateLatest() }
        content.addView(latest, FrameLayout.LayoutParams(-2, dp(44), Gravity.BOTTOM or Gravity.END))
        panel.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        composer = EditText(this).apply {
            hint = "Ask Dikciz to do something"
            contentDescription = "Chat message"
            setTextColor(FOREGROUND); setHintTextColor(MUTED)
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(ASSISTANT_BUBBLE) }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 4
        }
        controls.addView(composer, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(button("Send", "Send chat message") { send() })
        controls.addView(button("Stop", "Stop agent turn") { conversationID?.let { command(JSONObject().put("action", "stop").put("conversationId", it)) } })
        panel.addView(controls)
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            insets
        }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> resize(); if (followLatest) scrollLatest() }
        if (conversationID.isNullOrBlank()) conversationID = runtime.store.conversations().firstOrNull()?.getString("id")
        resize()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("conversationId")?.takeIf(String::isNotBlank)?.let { selectConversation(it) }
        fullScreen = intent.getBooleanExtra("fullScreen", fullScreen)
        resize()
    }

    override fun onResume() {
        super.onResume()
        runtime.observe(observer)
        val selected = runtime.attachActivity(this, conversationID)
        if (conversationID != selected) {
            conversationID = selected
            rendered.clear()
            messages.removeAllViews()
        }
        render()
    }

    override fun onPause() {
        runtime.unobserve(observer)
        runtime.detachActivity(this)
        super.onPause()
    }

    override fun onDestroy() {
        scope.cancel()
        if (::modelControls.isInitialized) modelControls.invalidate()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("conversationId", conversationID)
        outState.putBoolean("fullScreen", fullScreen)
        super.onSaveInstanceState(outState)
    }

    private fun resize() {
        if (!::panel.isInitialized || root.height == 0) return
        runtime.setWindowMode(fullScreen)
        val available = root.height - root.paddingTop - root.paddingBottom
        val height = if (fullScreen) available else maxOf(available / 2, minOf(dp(360), available))
        val parameters = panel.layoutParams as FrameLayout.LayoutParams
        if (parameters.height != height) { parameters.height = height; panel.layoutParams = parameters }
    }

    private fun render() {
        if (isFinishing) return
        if (rendering) { renderAgain = true; return }
        rendering = true
        scope.launch {
            try {
                val id = conversationID
                val state = withContext(Dispatchers.IO) {
                    val conversation = id?.let { runtime.store.conversation(it) }
                    Triple(conversation, if (id == null) emptyList() else runtime.store.displayMessages(id), id?.let { runtime.store.settings(it).getJSONObject("settings") })
                }
                if (id != conversationID) return@launch
                title.text = state.first?.getString("title") ?: "Dikciz"
                modelControls.update(id, state.first?.getString("profile_id"), state.third)
                status.text = (if (state.third?.optBoolean(DikcizChatSettings.KEY_READ_ONLY) == true) "Read-only · " else "") + if (runtime.busy()) "Agent working · tools and replies appear here" else "Your phone assistant · tap Settings to configure a provider"
                val anchor = (0 until messages.childCount).map(messages::getChildAt).firstOrNull { it.bottom > scroll.scrollY }
                val anchorOffset = anchor?.let { scroll.scrollY - it.top } ?: 0
                for (row in state.second) {
                    val rowID = row.getLong("id")
                    val view = rendered.getOrPut(rowID) { MessageView().also { messages.addView(it.container) } }
                    view.bind(row)
                }
                scroll.post {
                    if (followLatest) scrollLatest()
                    else if (!scroll.userDragging && anchor != null) scroll.scrollTo(0, anchor.top + anchorOffset)
                    updateLatest()
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                status.text = "Chat could not be loaded: ${exception.javaClass.simpleName}"
            } finally {
                rendering = false
                if (renderAgain) { renderAgain = false; render() }
            }
        }
    }

    private fun send() {
        val text = composer.text.toString()
        if (text.isBlank()) return
        val id = conversationID
        if (id == null) { newConversation(); return }
        followLatest = true
        command(JSONObject().put("action", "send").put("conversationId", id).put("text", text)
            .put("requestKey", UUID.randomUUID().toString())) { composer.setText("") }
    }

    private fun command(request: JSONObject, completed: (JSONObject) -> Unit = {}) {
        scope.launch {
            try {
                val reply = withContext(Dispatchers.IO) {
                    DikcizLocalCommands.execute("chat-ui", JSONObject(request.toString()).put("type", DikcizChatCommands.TYPE))
                }
                if (reply.response.optString("type") == "error") throw IllegalStateException(reply.response.optString("message"))
                completed(reply.response.getJSONObject("result"))
                reply.delivered()
                render()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                AlertDialog.Builder(this@DikcizChatActivity).setTitle("Dikciz chat").setMessage(exception.message ?: "Operation failed").setPositiveButton("Close", null).show()
            }
        }
    }

    private fun chooseConversation() {
        val records = runtime.store.conversations()
        AlertDialog.Builder(this).setTitle("Conversations")
            .setItems(records.map { "${it.getString("title")} (${it.getInt("unread")} unread)" }.toTypedArray()) { _, index -> selectConversation(records[index].getString("id")) }
            .setNeutralButton("Events") { _, _ -> editEvents() }
            .setPositiveButton("Manage") { _, _ -> manageConversation() }
            .setNegativeButton("Close", null).show()
    }

    private fun manageConversation() {
        val id = conversationID ?: return
        AlertDialog.Builder(this).setTitle("Manage conversation")
            .setItems(arrayOf("Rename", "Change provider", "Delete")) { _, action ->
                when (action) {
                    0 -> {
                        val editor = EditText(this).apply { setText(runtime.store.conversation(id).getString("title")) }
                        AlertDialog.Builder(this).setTitle("Conversation title").setView(editor)
                            .setPositiveButton("Save") { _, _ -> command(JSONObject().put("action", "rename").put("conversationId", id).put("title", editor.text.toString())) }
                            .setNegativeButton("Cancel", null).show()
                    }
                    1 -> {
                        val profiles = runtime.store.profiles()
                        AlertDialog.Builder(this).setTitle("Conversation provider")
                            .setItems((0 until profiles.length()).map { profiles.getJSONObject(it).getString("name") }.toTypedArray()) { _, index ->
                                command(JSONObject().put("action", "configure").put("conversationId", id).put("profileId", profiles.getJSONObject(index).getString("id")))
                            }.show()
                    }
                    2 -> AlertDialog.Builder(this).setTitle("Delete this conversation?")
                        .setMessage("This removes its transcript and event rules.")
                        .setPositiveButton("Delete") { _, _ ->
                            command(JSONObject().put("action", "delete").put("conversationId", id))
                        }.setNegativeButton("Cancel", null).show()
                }
            }.show()
    }

    private fun selectConversation(id: String) {
        conversationID = runtime.attachActivity(this, id)
        rendered.clear(); messages.removeAllViews(); followLatest = true
        render()
    }

    internal fun onConversationDeleted(id: String) {
        if (conversationID != id) return
        conversationID = null
        rendered.clear()
        messages.removeAllViews()
        runtime.attachActivity(this, null)
        render()
    }

    private fun newConversation() {
        val profiles = runtime.store.profiles()
        if (profiles.length() == 0) { editProfile(null); return }
        AlertDialog.Builder(this).setTitle("Provider for new conversation")
            .setItems((0 until profiles.length()).map { profiles.getJSONObject(it).getString("name") }.toTypedArray()) { _, index ->
                command(JSONObject().put("action", "create").put("profileId", profiles.getJSONObject(index).getString("id"))) { selectConversation(it.getString("id")) }
            }.show()
    }

    private fun chooseProfile() {
        val profiles = runtime.store.profiles()
        AlertDialog.Builder(this).setTitle("Provider profiles")
            .setItems((0 until profiles.length()).map { profiles.getJSONObject(it).getString("name") }.toTypedArray()) { _, index -> editProfile(profiles.getJSONObject(index)) }
            .setPositiveButton("Add provider") { _, _ -> editProfile(null) }.setNegativeButton("Close", null).show()
    }

    private fun editProfile(existing: JSONObject?) {
        val initial = existing ?: JSONObject()
            .put("name", PROVIDER_TEMPLATES.first().label)
            .put("protocol", PROVIDER_TEMPLATES.first().protocol)
            .put("baseUrl", PROVIDER_TEMPLATES.first().baseUrl)
        var selectedTemplate = PROVIDER_TEMPLATES.indexOfFirst {
            it.baseUrl != null && it.baseUrl == initial.optString("baseUrl").trimEnd('/') &&
                it.protocol == initial.optString("protocol")
        }.takeIf { it >= 0 } ?: PROVIDER_TEMPLATES.lastIndex
        var customUrl = if (selectedTemplate == PROVIDER_TEMPLATES.lastIndex) initial.optString("baseUrl") else ""
        var customProtocol = initial.optString("protocol", "openai")
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        val inputs = linkedMapOf<String, EditText>()
        val fields = listOf(
            Triple("name", "Provider name", ""), Triple("protocol", "Protocol: openai or anthropic", "openai"),
            Triple("baseUrl", "API base URL", ""), Triple("apiKey", "API key. Blank retains it for the same host and protocol", ""),
        )
        fields.forEach { (name, label, default) ->
            body.addView(textView().apply { text = label; textSize = 12f; setTextColor(MUTED) })
            inputs[name] = EditText(this).apply {
                setText(if (name == "apiKey") "" else initial.optString(name, default))
                contentDescription = label
                setSelectAllOnFocus(true)
                if (name == "apiKey") inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                SETTING_CHOICES[name]?.let { choices ->
                    isFocusable = false
                    isCursorVisible = false
                    setOnClickListener {
                        val available = if (name == "reasoning" && inputs["protocol"]?.text.toString() == "anthropic") choices.filter { it != "minimal" } else choices.toList()
                        AlertDialog.Builder(this@DikcizChatActivity).setTitle(label)
                            .setItems(available.toTypedArray()) { _, index -> setText(available[index]) }.show()
                    }
                }
                body.addView(this)
            }
        }
        val templatePicker = Spinner(this).apply {
            contentDescription = "Provider template"
            adapter = ArrayAdapter(
                this@DikcizChatActivity,
                android.R.layout.simple_spinner_dropdown_item,
                PROVIDER_TEMPLATES.map { it.label },
            )
            setSelection(selectedTemplate)
        }
        body.addView(textView().apply { text = "Provider template"; textSize = 12f; setTextColor(MUTED) }, 0)
        body.addView(templatePicker, 1)
        fun updateConnectionFields() {
            val isCustom = PROVIDER_TEMPLATES[selectedTemplate].baseUrl == null
            inputs.getValue("protocol").isEnabled = isCustom
            inputs.getValue("baseUrl").isEnabled = isCustom
        }
        updateConnectionFields()
        templatePicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == selectedTemplate) return
                val previous = PROVIDER_TEMPLATES[selectedTemplate]
                if (previous.baseUrl == null) {
                    customUrl = inputs.getValue("baseUrl").text.toString()
                    customProtocol = inputs.getValue("protocol").text.toString()
                }
                val selected = PROVIDER_TEMPLATES[position]
                val name = inputs.getValue("name")
                if (name.text.isBlank() || name.text.toString() == previous.label) {
                    name.setText(if (selected.baseUrl == null) "" else selected.label)
                }
                inputs.getValue("protocol").setText(selected.protocol ?: customProtocol)
                inputs.getValue("baseUrl").setText(selected.baseUrl ?: customUrl)
                // A typed key must not follow a template switch to another endpoint.
                inputs.getValue("apiKey").setText("")
                selectedTemplate = position
                updateConnectionFields()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        val scroller = ScrollView(this).apply { contentDescription = "Chat provider form"; addView(body) }
        val dialog = AlertDialog.Builder(this).setTitle("Provider settings").setView(scroller)
            .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val profile = profileDocument(initial, inputs)
                command(JSONObject().put("action", "profileSave").put("profile", profile)) { dialog.dismiss(); modelControls.invalidate(); if (conversationID == null) newConversation() }
            }
        }
        dialog.show()
    }

    private fun profileDocument(initial: JSONObject, inputs: Map<String, EditText>): JSONObject {
        val result = JSONObject(initial.toString())
        initial.optString("id").takeIf(String::isNotBlank)?.let { result.put("id", it) }
        inputs.forEach { (name, input) ->
            val value = input.text.toString()
            if (name == "apiKey" && value.isEmpty() && result.has("id")) return@forEach
            result.put(name, value)
        }
        return result
    }

    private fun editEvents() {
        val id = conversationID ?: return
        val editor = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(runtime.store.conversation(id).getString("subscriptions"))
            contentDescription = "Conversation event rules JSON"
        }
        AlertDialog.Builder(this).setTitle("Event rules")
            .setMessage("Each rule has subscription and instructions. Example: [{\"subscription\":{\"event\":\"example.check\",\"minimumIntervalMilliseconds\":60000},\"instructions\":\"Report the event details\"}]")
            .setView(editor).setPositiveButton("Save") { _, _ ->
                try { command(JSONObject().put("action", "subscribe").put("conversationId", id).put("rules", JSONArray(editor.text.toString()))) }
                catch (_: org.json.JSONException) { status.text = "Event rules must be a JSON array" }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun updateLatest() { latest.visibility = if (followLatest) View.GONE else View.VISIBLE }
    private fun scrollLatest() { scroll.post { if (followLatest && !scroll.userDragging) scroll.scrollTo(0, messages.height) } }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun textView() = TextView(this).apply { setTextColor(FOREGROUND); textSize = 15f; setPadding(0, dp(4), 0, dp(4)) }
    private fun button(label: String, description: String, action: () -> Unit) = Button(this).apply {
        text = label; contentDescription = description; minWidth = 0; minimumWidth = 0
        isAllCaps = false
        backgroundTintList = null
        background = RippleDrawable(ColorStateList.valueOf(BUTTON_RIPPLE),
            GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(OWNER_BUBBLE) }, null)
        setPadding(dp(8), 0, dp(8), 0); textSize = 12f; setTextColor(ACCENT)
        setOnClickListener { action() }
    }

    private inner class MessageView {
        val container = LinearLayout(this@DikcizChatActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(6), dp(4), dp(6)) }
        private val label = textView().apply { textSize = 11f; setTextColor(ACCENT); container.addView(this) }
        private val blocks = mutableListOf<TextView>()
        private var previous = ""
        fun bind(row: JSONObject) {
            val encoded = row.getString("blocks") + row.getString("state")
            if (previous == encoded) return
            previous = encoded
            val entries = JSONArray(row.getString("blocks"))
            val isOwner = row.getString("role") == "user"
            val role = if (isOwner && entries.optJSONObject(0)?.optString("source") == "event") "Event" else if (isOwner) "You" else if (row.getString("role") == "tool") "Phone tool" else "Dikciz"
            val state = row.getString("state")
            label.text = if (state == "complete") role else "$role · $state"
            container.background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(if (isOwner) OWNER_BUBBLE else ASSISTANT_BUBBLE) }
            container.setPadding(dp(12), dp(8), dp(12), dp(8))
            container.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(if (isOwner) dp(24) else 0, dp(4), if (isOwner) 0 else dp(12), dp(4)) }
            for (index in 0 until entries.length()) {
                val entry = entries.getJSONObject(index)
                val view = blocks.getOrNull(index) ?: textView().also {
                    blocks.add(it); container.addView(it, LinearLayout.LayoutParams(-1, -2))
                }
                val kind = entry.getString("kind")
                val text = entry.optString("text")
                if (kind == "text") {
                    view.setTextIsSelectable(true)
                    markwon.setMarkdown(view, text)
                } else if (kind == "tool" || kind == "toolResult" || kind == "reasoning") {
                    view.typeface = Typeface.MONOSPACE; view.textSize = 12f; view.setTextColor(MUTED)
                    val label = if (kind == "reasoning") "Reasoning" else if (kind == "toolResult") "Tool result" else "Tool call"
                    val expanded = view.tag == true
                    view.text = if (expanded) "$label\n$text" else "$label ▸"
                    view.setOnClickListener { view.tag = view.tag != true; previous = ""; bind(row) }
                } else {
                    view.text = text; view.setTextColor(ERROR)
                }
            }
            if (row.getString("state") in setOf("interrupted", "cancelled", "failed")) container.contentDescription = "${row.getString("role")} message ${row.getString("state")}"
        }
    }

    private class ChatScrollView(context: android.content.Context) : ScrollView(context) {
        var onReadingOlder: (() -> Unit)? = null
        var onUserScroll: (() -> Unit)? = null
        var userDragging = false
            private set
        private var initialY = 0f
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { userDragging = true; initialY = event.y }
                MotionEvent.ACTION_MOVE -> if (event.y - initialY > touchSlop) onReadingOlder?.invoke()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { userDragging = false; onUserScroll?.invoke() }
            }
            return super.dispatchTouchEvent(event)
        }
        override fun onScrollChanged(left: Int, top: Int, oldLeft: Int, oldTop: Int) {
            super.onScrollChanged(left, top, oldLeft, oldTop)
            if (userDragging) onUserScroll?.invoke()
        }
    }

    private data class ProviderTemplate(val label: String, val protocol: String?, val baseUrl: String?)

    private companion object {
        val PROVIDER_TEMPLATES = listOf(
            ProviderTemplate("OpenAI", "openai", "https://api.openai.com/v1"),
            ProviderTemplate("Anthropic", "anthropic", "https://api.anthropic.com/v1"),
            ProviderTemplate("Custom", null, null),
        )
        const val BACKGROUND = 0xFF111725.toInt()
        const val ACCENT = 0xFFF6C85F.toInt()
        const val ERROR = 0xFFFF9696.toInt()
        const val DIM = 0x55000000
        const val OWNER_BUBBLE = 0xFF27334B.toInt()
        const val ASSISTANT_BUBBLE = 0xFF192131.toInt()
        const val BUTTON_RIPPLE = 0x44F6C85F
        val SETTING_CHOICES = mapOf(
            "protocol" to arrayOf("openai", "anthropic"),
        )
    }
}
