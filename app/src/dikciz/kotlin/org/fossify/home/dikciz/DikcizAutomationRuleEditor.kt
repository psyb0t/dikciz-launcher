package org.fossify.home.dikciz

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

internal class DikcizAutomationRuleEditor(
    private val context: Context,
    private val showDialog: (AlertDialog, () -> Map<String, View>) -> Unit,
) {
    fun manage() {
        val result = try {
            DikcizUiCommands.execute("settings:rules", DikcizAutomationRules.TYPE_LIST, JSONObject().put("limit", 200))
        } catch (exception: Exception) {
            val dialog = AlertDialog.Builder(context).setTitle("Automation rules")
                .setMessage("Could not load rules. Reopen this screen to retry.").setPositiveButton("Close", null).create()
            showDialog(dialog) { emptyMap() }
            return
        }
        val rows = result.getJSONArray("items")
        val choices = listOf("Create a rule") + (0 until rows.length()).map {
            val rule = rows.getJSONObject(it)
            rule.getString("title") + if (rule.optBoolean("enabled")) " · Enabled" else " · Disabled"
        }
        val dialog = AlertDialog.Builder(context).setTitle("Automation rules")
            .setItems(choices.toTypedArray()) { _, index -> if (index == 0) show() else show(rows.getJSONObject(index - 1)) }
            .setNegativeButton("Close", null).create()
        showDialog(dialog) { (0 until dialog.listView.childCount).associate { index ->
            "automation-rules:item:" + index to dialog.listView.getChildAt(index).apply {
                setOnClickListener { dialog.listView.performItemClick(this, index, dialog.listView.adapter.getItemId(index)) }
            }
        } }
    }

    fun show(initial: JSONObject = JSONObject()) {
        val form = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val views = linkedMapOf<String, View>()
        fun heading(title: String, detail: String) {
            form.addView(TextView(context).apply { text = title; textSize = 18f; setPadding(0, 16, 0, 8) })
            form.addView(TextView(context).apply { text = detail; textSize = 13f })
        }
        fun input(key: String, label: String, value: String, number: Boolean = false): EditText {
            form.addView(TextView(context).apply { text = label })
            return EditText(context).apply {
                contentDescription = label; setText(value)
                if (number) inputType = InputType.TYPE_CLASS_NUMBER
                form.addView(this); views["automation-rule:$key"] = this
            }
        }
        fun picker(key: String, label: String, values: List<String>): Spinner {
            form.addView(TextView(context).apply { text = label })
            return Spinner(context).apply {
                contentDescription = label
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, values)
                form.addView(this); views["automation-rule:$key"] = this
            }
        }
        heading("1. Name and trigger", "Rules start disabled. Test a sample before enabling live events.")
        val name = input("title", "Rule name", initial.optString("title"))
        val events = DikcizAutomationRules.EVENTS.map { it.persistedValue }
        val eventLabels = listOf("Custom event", "Time tick", "Battery changed", "Charging changed", "Alarm interval", "Approximate location", "Sensor readings")
        val event = picker("event", "When this event arrives", eventLabels).apply {
            setSelection(events.indexOf(initial.optString("event", "custom")).coerceAtLeast(0))
        }
        val custom = input("custom", "Custom event name, for custom triggers", initial.optString("customEventName", "example.check"))
        val cooldown = input("cooldown", "Minimum seconds between runs", (initial.optLong("minimumIntervalMilliseconds", 60000) / 1000).toString(), true)
        val sensor = input("sensor", "Android sensor type, for sensor triggers", initial.optInt("sensorType", 1).toString(), true)
        heading("2. Optional condition", "Compare one field in the event payload. Numeric comparisons require a numeric value.")
        val field = input("field", "Payload field", initial.optString("conditionField"))
        val operators = listOf("always", "eq", "ne", "gt", "gte", "lt", "lte")
        val operator = picker("operator", "Compare payload", listOf("Always", "Is equal", "Is not equal", "Is greater than", "Is at least", "Is less than", "Is at most")).apply {
            setSelection(operators.indexOf(initial.optString("conditionOperator", "always")).coerceAtLeast(0))
        }
        val value = input("value", "Compare with", initial.opt("conditionValue")?.toString().orEmpty())
        heading("3. Action", "Torch pulses end automatically. Android permission requirements still apply.")
        val actions = listOf("Speak text", "Vibrate briefly", "Pulse torch")
        val savedCommand = initial.optJSONArray("commands")?.optJSONObject(0)
        val action = picker("action", "What to do", actions).apply {
            setSelection(when (savedCommand?.optString("type")) { "vibrationControl" -> 1; "torchControl" -> 2; else -> 0 })
        }
        val speechText = input("text", "Text to speak", savedCommand?.optString("text", "Dikciz automation triggered") ?: "Dikciz automation triggered")
        if ((initial.optJSONArray("commands")?.length() ?: 0) > 1) {
            heading("Advanced rule", "This rule has several actions. Edit its Lua script or use the API to preserve all actions.")
        }
        heading("4. Test and activate", "A sample test runs Lua in simulation and never calls Android.")
        val sample = input("sample", "Sample event payload JSON", "{}").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
        }
        val enabled = Switch(context).apply {
            text = "Enable live events"; isChecked = initial.optBoolean("enabled", false)
            form.addView(this); views["automation-rule:enabled"] = this
        }
        fun showField(view: View, visible: Boolean) {
            val visibility = if (visible) View.VISIBLE else View.GONE
            view.visibility = visibility
            val index = form.indexOfChild(view)
            if (index > 0 && form.getChildAt(index - 1) is TextView) form.getChildAt(index - 1).visibility = visibility
        }
        fun refreshFields() {
            val trigger = events[event.selectedItemPosition]
            showField(custom, trigger == "custom")
            showField(sensor, trigger == "sensor")
            showField(field, operator.selectedItemPosition != 0)
            showField(value, operator.selectedItemPosition != 0)
            showField(speechText, action.selectedItemPosition == 0)
        }
        val selections = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { refreshFields() }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        event.onItemSelectedListener = selections
        operator.onItemSelectedListener = selections
        action.onItemSelectedListener = selections
        refreshFields()
        val feedback = TextView(context).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        form.addView(feedback)
        fun rule(): JSONObject {
            if (name.text.isNullOrBlank()) {
                name.error = "Enter a rule name"
                name.requestFocus()
                throw IllegalArgumentException("Enter a rule name")
            }
            name.error = null
            val seconds = cooldown.text.toString().toLongOrNull()
                ?: throw IllegalArgumentException("Enter whole seconds for the cooldown")
            if (seconds !in 1..86400) throw IllegalArgumentException("Cooldown must be between 1 second and 1 day")
            val command = when (action.selectedItemPosition) {
                0 -> JSONObject().put("type", "speechSpeak").put("text", speechText.text.toString())
                1 -> JSONObject().put("type", "vibrationControl").put("action", "vibrate").put("durationMilliseconds", 200)
                else -> JSONObject().put("type", "torchControl").put("action", "on").put("durationMilliseconds", 500)
            }
            if ((initial.optJSONArray("commands")?.length() ?: 0) > 1) {
                throw IllegalArgumentException("Use the API or Lua editor for rules with several actions")
            }
            return JSONObject().put("title", name.text.toString()).put("event", events[event.selectedItemPosition])
                .put("customEventName", custom.text.toString()).put("minimumIntervalMilliseconds", seconds * 1000)
                .put("alarmIntervalMilliseconds", seconds * 1000).put("sensorType", sensor.text.toString().toIntOrNull() ?: 1)
                .put("conditionField", field.text.toString()).put("conditionOperator", operators[operator.selectedItemPosition])
                .put("conditionValue", value.text.toString().let { it.toDoubleOrNull() ?: when (it) { "true" -> true; "false" -> false; else -> it } })
                .put("commands", JSONArray().put(command)).put("enabled", enabled.isChecked)
                .apply { if (initial.has("id")) put("id", initial.getString("id")) }
        }
        val preview = Button(context).apply {
            text = "Test sample"; contentDescription = text
            setOnClickListener {
                try {
                    val result = DikcizUiCommands.execute("settings:rules", DikcizAutomationRules.TYPE_PREVIEW,
                        JSONObject().put("rule", rule()).put("sample", JSONObject(sample.text.toString())))
                    feedback.text = if (result.getBoolean("matched")) "Condition matched. " +
                        result.getJSONArray("commands").length() + " proposed action. Nothing ran on Android." else "Condition did not match. No actions proposed."
                } catch (exception: Exception) {
                    feedback.text = exception.message ?: "Could not test this rule"
                    feedback.post { feedback.requestRectangleOnScreen(android.graphics.Rect(0, 0, feedback.width, feedback.height), true) }
                }
            }
        }
        form.addView(preview); views["automation-rule:preview"] = preview
        if (initial.has("id")) {
            val remove = Button(context).apply {
                text = "Delete rule"
                setOnClickListener {
                    val confirmation = AlertDialog.Builder(context).setTitle("Delete this rule?")
                        .setMessage("Removes the generated script and its event subscription. In-flight actions may finish.")
                        .setNegativeButton("Keep rule", null)
                        .setPositiveButton("Delete") { _, _ ->
                            try {
                                DikcizUiCommands.execute("settings:rules", DikcizAutomationRules.TYPE_DELETE, JSONObject().put("id", initial.getString("id")))
                                manage()
                            } catch (exception: Exception) {
                                android.util.Log.e("DikcizRules", "Rule deletion failed: " + exception.javaClass.simpleName)
                                val error = AlertDialog.Builder(context).setTitle("Could not delete this rule")
                                    .setMessage("Check storage access and reopen the rule to retry.").setPositiveButton("Close", null).create()
                                showDialog(error) { emptyMap() }
                            }
                        }.create()
                    showDialog(confirmation) {
                        mapOf("automation-rule:delete-confirm" to confirmation.getButton(AlertDialog.BUTTON_POSITIVE))
                    }
                }
            }
            form.addView(remove); views["automation-rule:delete"] = remove
        }
        val dialog = AlertDialog.Builder(context).setTitle("Automation rule")
            .setView(ScrollView(context).apply { addView(form) }).setNegativeButton("Cancel", null)
            .setPositiveButton("Save rule", null).create()
        showDialog(dialog) {
            val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            save.setOnClickListener {
                try {
                    DikcizUiCommands.execute("settings:rules", DikcizAutomationRules.TYPE_SAVE, JSONObject().put("rule", rule()))
                    dialog.dismiss()
                } catch (exception: Exception) {
                    feedback.text = exception.message ?: "Could not save this rule"
                    feedback.post { feedback.requestRectangleOnScreen(android.graphics.Rect(0, 0, feedback.width, feedback.height), true) }
                }
            }
            views + ("automation-rule:save" to save)
        }
    }
}
