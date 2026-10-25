package org.fossify.home.dikciz

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import org.fossify.home.dikciz.DikcizChatColors.FOREGROUND
import org.fossify.home.dikciz.DikcizChatColors.MUTED

internal class DikcizChatModelControls(
    private val activity: Activity,
    private val conversation: () -> String?,
    private val command: (JSONObject, (JSONObject) -> Unit) -> Unit,
) {
    val view = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
    private val picker = Button(activity).apply {
        contentDescription = "Chat model"
        text = "Choose a model ▾"
        setTextColor(FOREGROUND)
        textSize = 14f
        isAllCaps = false
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setOnClickListener { openModels() }
    }
    private var modelNames = emptyList<String>()
    private val dropdown = ListPopupWindow(activity).apply {
        anchorView = picker
        isModal = true
        inputMethodMode = ListPopupWindow.INPUT_METHOD_NOT_NEEDED
        setOnItemClickListener { _, _, position, _ ->
            val id = conversation() ?: return@setOnItemClickListener
            val selected = modelNames.getOrNull(position) ?: return@setOnItemClickListener
            dismiss()
            command(JSONObject().put("action", "configure").put("conversationId", id)
                .put("settings", JSONObject().put("model", selected))) { }
        }
    }
    private var selectedConversation: String? = null
    private var selectedProvider: String? = null
    private var requestGeneration = 0L
    private var requested = false

    init {
        view.addView(picker, LinearLayout.LayoutParams(0, -2, 1f))
        view.addView(Button(activity).apply {
            text = "↻"
            contentDescription = "Refresh chat models"
            setOnClickListener { loadModels() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        view.addView(Button(activity).apply {
            text = "Model settings"
            contentDescription = "Chat model settings"
            textSize = 11f
            setOnClickListener { editSettings() }
        }, LinearLayout.LayoutParams(-2, dp(44)))
    }

    fun invalidate() {
        requestGeneration++
        requested = false
        dropdown.dismiss()
        modelNames = emptyList()
    }

    fun update(id: String?, provider: String?, settings: JSONObject?) {
        view.visibility = if (id == null) android.view.View.GONE else android.view.View.VISIBLE
        if (id != selectedConversation || provider != selectedProvider) {
            selectedConversation = id
            selectedProvider = provider
            dropdown.dismiss()
            modelNames = emptyList()
            invalidate()
        }
        val model = settings?.optString("model").orEmpty()
        picker.text = model.ifBlank { "Choose a model" } + " ▾"
        if (id != null && provider != null && !requested) loadModels()
    }

    private fun openModels() {
        if (modelNames.isEmpty()) {
            loadModels(showDropdown = true)
            return
        }
        dropdown.width = maxOf(picker.width, dp(MINIMUM_DROPDOWN_WIDTH_DP)).coerceAtMost(view.width)
        dropdown.show()
    }

    private fun loadModels(showDropdown: Boolean = false) {
        val id = conversation() ?: return
        val provider = selectedProvider ?: return
        val generation = ++requestGeneration
        requested = true
        command(JSONObject().put("action", "models").put("profileId", provider)) { result ->
            if (generation != requestGeneration || conversation() != id || selectedProvider != provider) return@command
            val models = result.getJSONArray("models")
            val names = (0 until models.length()).map { models.getJSONObject(it).getString("id") }.distinct().sorted()
            modelNames = names
            dropdown.setAdapter(ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, names))
            if (showDropdown && names.isNotEmpty()) openModels()
        }
    }

    private fun editSettings() {
        val id = conversation() ?: return
        command(JSONObject().put("action", "settings").put("conversationId", id)) { result ->
            if (conversation() != id) return@command
            settingsDialog(id, result.getJSONObject("settings"), result.getString("protocol"))
        }
    }

    private fun settingsDialog(id: String, initial: JSONObject, protocol: String) {
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        val readOnly = CheckBox(activity).apply {
            text = "Read-only phone access"
            contentDescription = "Read-only phone access"
            setTextColor(FOREGROUND)
            isChecked = initial.getBoolean(DikcizChatSettings.KEY_READ_ONLY)
        }
        body.addView(readOnly)
        body.addView(TextView(activity).apply {
            text = "Hide and block phone changes, shell execution and permission prompts. Chat history still saves."
            setTextColor(MUTED)
            textSize = 12f
        })
        val inputs = linkedMapOf<String, EditText>()
        val fields = listOf(
            "model" to "Model ID",
            "contextTokens" to "Total context tokens",
            "maxOutputTokens" to "Maximum output and reserved reply tokens",
            "compaction" to "Context policy: drop-oldest or summarize",
            "summaryModel" to "Summary model ID. Blank uses the chat model",
            "summaryOutputTokens" to "Maximum summary output tokens",
            "reasoning" to "Reasoning: auto, none, minimal, low, medium, high, xhigh, max",
            "thinkingTokens" to "Anthropic thinking tokens. 0 uses provider defaults",
            "outputTokenField" to "OpenAI output field: max_completion_tokens or max_tokens",
            "maximumRounds" to "Maximum tool rounds per turn",
            "timeoutMilliseconds" to "Turn timeout in milliseconds",
            "safetyMarginTokens" to "Context safety margin",
            "systemPrompt" to "Additional system instructions",
        )
        fields.forEach { (name, label) ->
            body.addView(TextView(activity).apply { text = label; setTextColor(MUTED); textSize = 12f })
            val input = EditText(activity).apply {
                setText(initial.optString(name))
                setTextColor(FOREGROUND)
                contentDescription = label
                setSelectAllOnFocus(true)
                if (name in NUMBER_FIELDS) inputType = InputType.TYPE_CLASS_NUMBER
                CHOICES[name]?.let { all ->
                    val choices = all.filter { !(protocol == "anthropic" && name == "reasoning" && it == "minimal") }.toTypedArray()
                    isFocusable = false
                    isCursorVisible = false
                    setOnClickListener {
                        AlertDialog.Builder(activity).setTitle(label).setItems(choices) { _, position -> setText(choices[position]) }.show()
                    }
                }
            }
            inputs[name] = input
            body.addView(input)
        }
        val scroller = ScrollView(activity).apply { contentDescription = "Chat model settings form"; addView(body) }
        val dialog = AlertDialog.Builder(activity).setTitle("Model and token settings").setView(scroller)
            .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val settings = JSONObject().put(DikcizChatSettings.KEY_READ_ONLY, readOnly.isChecked)
                for ((name, input) in inputs) {
                    val text = input.text.toString()
                    if (name !in NUMBER_FIELDS) {
                        settings.put(name, text)
                        continue
                    }
                    val number = text.toIntOrNull()
                    if (number == null) {
                        input.error = "Enter a whole number"
                        return@setOnClickListener
                    }
                    settings.put(name, number)
                }
                command(JSONObject().put("action", "configure").put("conversationId", id).put("settings", settings)) { dialog.dismiss() }
            }
        }
        dialog.show()
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    private companion object {
        const val MINIMUM_DROPDOWN_WIDTH_DP = 240
        val NUMBER_FIELDS = setOf("contextTokens", "maxOutputTokens", "summaryOutputTokens", "thinkingTokens", "maximumRounds", "timeoutMilliseconds", "safetyMarginTokens")
        val CHOICES = mapOf(
            "compaction" to arrayOf("drop-oldest", "summarize"),
            "reasoning" to arrayOf("auto", "none", "minimal", "low", "medium", "high", "xhigh", "max"),
            "outputTokenField" to arrayOf("max_completion_tokens", "max_tokens"),
        )
    }
}

internal object DikcizChatColors {
    const val FOREGROUND = 0xFFE6E9F0.toInt()
    const val MUTED = 0xFF9AA4B8.toInt()
}
