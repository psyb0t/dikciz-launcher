package org.fossify.home.dikciz

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.ExecutorService
import org.json.JSONArray
import org.json.JSONObject

/** The library and trace screens use the same command boundary as remote clients. */
internal class DikcizOperationsTools(
    private val activity: Activity,
    private val content: LinearLayout,
    private val status: TextView,
    private val views: MutableMap<String, View>,
    private val worker: ExecutorService,
    private val handler: Handler,
    private val modal: (AlertDialog) -> Unit,
    private val refresh: () -> Unit,
) {
    private var editor: AlertDialog? = null
    private var pathInput: EditText? = null
    private var textInput: EditText? = null
    private var editorRevision = ""
    private var editorBundled = false

    private fun command(type: String, arguments: JSONObject) = DikcizUiCommands.execute("operations:tools", type, arguments)
    private fun task(action: () -> JSONObject, done: (JSONObject) -> Unit) {
        worker.execute {
            try {
                val result = action()
                handler.post { if (!activity.isFinishing && !activity.isDestroyed) done(result) }
            } catch (exception: Exception) {
                android.util.Log.w("DikcizOperations", "Tools request failed: " + exception.javaClass.simpleName)
                handler.post { status.text = exception.message ?: "Request failed. Refresh to retry." }
            }
        }
    }

    fun renderLibrary(result: JSONObject) {
        status.text = "Owner instructions on shared storage."
        content.addView(button("New instruction", "operations:library:new") {
            AlertDialog.Builder(activity).setTitle("New instruction").setItems(arrayOf("Skill", "Agent", "Rule")) { _, index ->
                val name = "my-instruction"
                val path = listOf("skills/$name/SKILL.md", "agents/$name.md", "rules/$name.md")[index]
                val text = if (index == 2) "# Owner rule\n\nWrite the rule here.\n" else "---\nname: $name\ndescription: \"Describe when to use this instruction.\"\n---\n\nWrite the instructions here.\n"
                edit(path, text, "", false, true)
            }.setNegativeButton("Cancel", null).show().also(modal)
        })
        val rows = result.getJSONArray("entries")
        content.addView(label("Your instructions"))
        if ((0 until rows.length()).none { !rows.getJSONObject(it).optBoolean("bundled") }) content.addView(label("No owner instructions yet. Create one above."))
        var bundledHeading = false
        if (rows.length() == 0) content.addView(label("No instructions yet. Add a skill, agent or rule."))
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val path = row.getString("path")
            if (row.optBoolean("bundled") && !bundledHeading) {
                content.addView(label("Built-in authoring guides"))
                bundledHeading = true
            }
            val line = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
            val state = when {
                row.optBoolean("bundled") -> "Built-in guide"
                !row.optBoolean("valid") -> "Needs repair: " + row.optString("message")
                !row.optBoolean("enabled") -> "Disabled"
                else -> row.getString("kind").replaceFirstChar(Char::uppercase)
            }
            line.addView(button((if (row.optBoolean("bundled")) row.getString("name").removePrefix("dikciz-").replace('-', ' ').replaceFirstChar(Char::uppercase) else if (row.optString("kind") == "rule") path.removeSuffix(".disabled").substringAfterLast('/').removeSuffix(".md") else row.getString("name")) + "\n" + state + "\n" + row.optString("description"), "operations:library:item:${row.getString("id")}") {
                task({ command(DikcizAssistantLibrary.TYPE, JSONObject().put("action", "read").put("path", path)) }) {
                    edit(path, it.getString("text"), it.getString("revision"), row.optBoolean("bundled"), false)
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            if (!row.optBoolean("bundled")) line.addView(button("More", "operations:library:more:${row.getString("id")}") {
                val toggle = if (row.optBoolean("enabled")) "Disable" else "Enable"
                AlertDialog.Builder(activity).setTitle(row.getString("name")).setItems(arrayOf("Edit", toggle, "Delete instruction")) { _, selected ->
                    if (selected == 0) {
                        task({ command(DikcizAssistantLibrary.TYPE, JSONObject().put("action", "read").put("path", path)) }) {
                            edit(path, it.getString("text"), it.getString("revision"), false, false)
                        }
                    } else {
                        val message = if (selected == 2) "Delete $path? Supporting files remain on shared storage." else "$toggle $path? Changes apply when instructions reload. Existing agent turns keep their current instructions."
                        AlertDialog.Builder(activity).setTitle(if (selected == 2) "Delete instruction?" else "$toggle instruction?").setMessage(message)
                            .setNegativeButton("Cancel", null).setPositiveButton(if (selected == 2) "Delete" else toggle) { _, _ ->
                                task({
                                    command(DikcizAssistantLibrary.TYPE_MANAGE, JSONObject().put("action", if (selected == 2) "delete" else "setEnabled")
                                        .put("path", path).put("expectedRevision", row.getString("revision")).apply { if (selected == 1) put("enabled", !row.optBoolean("enabled")) })
                                }) { refresh() }
                            }.show().also(modal)
                    }
                }.setNegativeButton("Cancel", null).show().also(modal)
            }, LinearLayout.LayoutParams(-2, -2))
            content.addView(line)
            if (!row.optBoolean("bundled")) content.addView(label(path))
        }
    }

    private fun edit(path: String, text: String, revision: String, bundled: Boolean, creating: Boolean) {
        views.keys.filter { it.startsWith("operations:editor:") }.forEach(views::remove)
        editorRevision = revision
        editorBundled = bundled
        val fields = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        fields.addView(label(if (bundled) "Built-in guide. Copy the text to a new owner instruction to customize." else "Save the complete Markdown file. Skills and agents need name and description front matter."))
        pathInput = EditText(activity).apply { setText(path); hint = "Relative path"; contentDescription = "Instruction path"; isEnabled = creating; minHeight = dp(48) }
        textInput = EditText(activity).apply {
            setText(text); hint = "Markdown instructions"; contentDescription = "Instruction text"
            gravity = Gravity.TOP; minLines = 5; maxLines = if (bundled) Int.MAX_VALUE else 12; isEnabled = !bundled
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        val feedback = label("").apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        views["operations:editor:path"] = pathInput!!
        views["operations:editor:text"] = textInput!!
        views["operations:editor:feedback"] = feedback
        fields.addView(pathInput); fields.addView(textInput); fields.addView(feedback)
        val scroll = ScrollView(activity).apply { addView(fields) }
        val builder = AlertDialog.Builder(activity).setTitle(if (bundled) "Authoring guide" else if (creating) "New instruction" else "Edit instruction")
            .setView(scroll).setNegativeButton(if (bundled) "Close" else "Cancel", null)
        if (!bundled) builder.setPositiveButton("Save", null).setNeutralButton("Validate", null)
        val dialog = builder.create()
        dialog.setOnShowListener {
            modal(dialog)
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            fun submit(save: Boolean) {
                val request = JSONObject().put("path", pathInput!!.text.toString()).put("text", textInput!!.text.toString())
                if (save) request.put("action", "save").put("expectedRevision", editorRevision)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = false
                worker.execute {
                    try {
                        val result = command(if (save) DikcizAssistantLibrary.TYPE_MANAGE else DikcizAssistantLibrary.TYPE_VALIDATE, request)
                        handler.post {
                            if (save) { editorRevision = result.getString("revision"); dialog.dismiss(); refresh() }
                            else feedback.text = "Valid instruction. It has not been saved."
                        }
                    } catch (exception: Exception) {
                        android.util.Log.w("DikcizOperations", "Instruction save or validation rejected: " + exception.javaClass.simpleName)
                        handler.post { feedback.text = exception.message ?: "Could not save. Your draft is still here." }
                    } finally { handler.post {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = true
                    } }
                }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener { submit(true) }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { submit(false) }
        }
        editor = dialog
        dialog.show()
    }

    fun save(out: Bundle) {
        if (editor?.isShowing == true) {
            out.putString("instructionPath", pathInput?.text.toString()); out.putString("instructionText", textInput?.text.toString())
            out.putString("instructionRevision", editorRevision); out.putBoolean("instructionBundled", editorBundled)
            out.putBoolean("instructionCreating", pathInput?.isEnabled == true)
        }
    }

    fun restore(saved: Bundle?) {
        if (saved?.containsKey("instructionPath") == true) handler.post {
            edit(saved.getString("instructionPath")!!, saved.getString("instructionText")!!, saved.getString("instructionRevision")!!,
                saved.getBoolean("instructionBundled"), saved.getBoolean("instructionCreating"))
        }
    }

    fun renderHistory(result: JSONObject, older: () -> Unit, newer: () -> Unit) {
        status.text = "${result.getInt("total")} records. Seven days, up to ${result.getInt("maximumRecords")}."
        content.addView(label("Tap an event to see its handlers and commands. Event payloads have separate retention limits."))
        val rows = result.getJSONArray("items")
        if (rows.length() == 0) content.addView(label("No records match. Choose another filter or return to newer records."))
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            content.addView(button(row.getString("name") + "\n" + row.getString("kind") + " · " + row.getString("outcome") + "\n" +
                java.text.DateFormat.getDateTimeInstance().format(java.util.Date(row.getLong("startedAt"))), "operations:history:row:${row.getString("id")}") { detail(row) })
        }
        val navigation = LinearLayout(activity)
        navigation.addView(button("Newer", "operations:history:newer", newer).apply { isEnabled = result.getInt("offset") > 0 }, LinearLayout.LayoutParams(0, -2, 1f))
        navigation.addView(button("Older", "operations:history:older", older).apply { isEnabled = result.getBoolean("hasMore") }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(navigation)
    }

    private fun detail(row: JSONObject, offset: Int = 0) {
        task({ command(DikcizAutomationDebugger.TYPE_HISTORY, JSONObject().put("traceId", row.getString("traceId")).put("limit", TRACE_PAGE_SIZE).put("offset", offset)) }) { trace ->
            var showing: AlertDialog? = null
            val items = trace.getJSONArray("items")
            fun describe(item: JSONObject): String = listOfNotNull(
                "${item.getString("kind")}: ${item.getString("name")}", item.getString("outcome"),
                if (item.isNull("trigger")) null else "Trigger: ${item.getString("trigger")}",
                if (item.isNull("code")) null else "Code: ${item.getString("code")}",
            ).joinToString("\n")
            val message = "Selected record\n" + describe(row) + "\n\nTrace ${row.getString("traceId")}\nRecords ${offset + 1} to ${offset + items.length()} of ${trace.getInt("total")}\n\n" +
                (0 until items.length()).reversed().joinToString("\n\n") { describe(items.getJSONObject(it)) }
            val navigation = LinearLayout(activity)
            navigation.addView(button("Newer", "operations:trace:newer") {
                showing?.dismiss(); detail(row, (offset - TRACE_PAGE_SIZE).coerceAtLeast(0))
            }.apply { isEnabled = offset > 0 }, LinearLayout.LayoutParams(0, -2, 1f))
            navigation.addView(button("Older", "operations:trace:older") {
                showing?.dismiss(); detail(row, offset + TRACE_PAGE_SIZE)
            }.apply { isEnabled = trace.getBoolean("hasMore") }, LinearLayout.LayoutParams(0, -2, 1f))
            val builder = AlertDialog.Builder(activity).setTitle(row.getString("name")).setMessage(message)
                .setView(navigation).setNegativeButton("Close", null)
            if (row.getString("kind") == "event") {
                builder.setNeutralButton("Event payload") { _, _ ->
                    task({ command(DikcizAutomationDebugger.TYPE_EVENT, JSONObject().put("recordId", row.getString("id"))) }) {
                        AlertDialog.Builder(activity).setTitle("Retained event").setMessage(it.toString(2)).setPositiveButton("Close", null).show().also(modal)
                    }
                }
                builder.setPositiveButton("Simulate") { _, _ -> chooseTarget(row.getString("id")) }
            }
            builder.show().also { showing = it; modal(it) }
        }
    }

    private fun chooseTarget(recordId: String) {
        task({
            val config = HomeConfigStore(activity).loadOrCreate()
            val targets = JSONArray(config.scripts.map { JSONObject().put("scriptId", it.id).put("title", it.title + " (script)") })
            DikcizCustomAppStore(activity).ids().forEach { id -> targets.put(JSONObject().put("appId", id).put("title", DikcizCustomAppStore(activity).manifest(id).getString("title") + " (app)")) }
            JSONObject().put("items", targets)
        }) { result ->
            val targets = result.getJSONArray("items")
            if (targets.length() == 0) { status.text = "No scripts or apps to simulate. Install one first."; return@task }
            AlertDialog.Builder(activity).setTitle("Simulate current handler")
                .setItems((0 until targets.length()).map { targets.getJSONObject(it).getString("title") }.toTypedArray()) { _, selected ->
                    val target = targets.getJSONObject(selected)
                    val request = JSONObject().put("recordId", recordId)
                    listOf("scriptId", "appId").filter(target::has).forEach { request.put(it, target.getString(it)) }
                    task({ command(DikcizAutomationDebugger.TYPE_SIMULATE, request) }) {
                        AlertDialog.Builder(activity).setTitle("Simulation only").setMessage(it.toString(2)).setPositiveButton("Close", null).show().also(modal)
                    }
                }.setNegativeButton("Cancel", null).show().also(modal)
        }
    }

    private companion object { const val TRACE_PAGE_SIZE = 50 }

    private fun label(text: String) = TextView(activity).apply {
        this.text = text; textSize = 14f; setTextColor(DikcizUiStyle.MUTED); setPadding(dp(12), dp(8), dp(12), dp(8))
    }
    private fun button(text: String, id: String, action: () -> Unit) = Button(activity).apply {
        this.text = android.text.SpannableString(text).apply {
            val end = text.indexOf('\n').takeIf { it >= 0 } ?: text.length
            setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (end < text.length) setSpan(android.text.style.ForegroundColorSpan(DikcizUiStyle.MUTED), end, text.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        contentDescription = text; isAllCaps = false; DikcizUiStyle.button(this)
        setOnClickListener { action() }; views[id] = this
    }
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
