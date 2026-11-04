package org.fossify.home.dikciz

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors
import org.json.JSONObject

internal class DikcizBackupSettings(
    private val context: Context,
    private val showDialog: (AlertDialog, () -> Map<String, View>) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())

    fun show(initialPath: String = "") {
        val form = column()
        val views = linkedMapOf<String, View>()
        form.addView(TextView(context).apply {
            text = "Encrypted backups include your home, widget files, scripts, conversations and provider settings. Keep the passphrase safe. Remote credentials and control ports stay on this device."
        })
        val password = EditText(context).apply {
            hint = "Backup passphrase, at least 12 characters"; contentDescription = hint
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        form.addView(password); views["backups:passphrase"] = password
        val includeKeys = CheckBox(context).apply {
            text = "Include provider API keys inside the encrypted backup"; isChecked = false
        }
        form.addView(includeKeys); views["backups:include-keys"] = includeKeys
        val feedback = TextView(context).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        form.addView(feedback); views["backups:status"] = feedback
        val create = Button(context).apply { text = "Create encrypted backup"; isAllCaps = false }
        form.addView(create); views["backups:create"] = create
        create.setOnClickListener {
            val request = JSONObject().put("passphrase", password.text.toString()).put("includeApiKeys", includeKeys.isChecked)
            password.text.clear()
            create.isEnabled = false
            feedback.text = "Encrypting backup…"
            execute(DikcizBackups.TYPE_CREATE, request) { result, error ->
                create.isEnabled = true
                feedback.text = error ?: "Saved encrypted backup:\n" + result!!.getString("path")
            }
        }
        form.addView(TextView(context).apply { text = "Restore"; textSize = 20f; setPadding(0, 24, 0, 8) })
        val path = EditText(context).apply { hint = "Archive path"; contentDescription = hint; maxLines = 3; setText(initialPath) }
        form.addView(path); views["backups:path"] = path
        val choose = Button(context).apply {
            text = "Choose a saved backup"; isAllCaps = false
            setOnClickListener {
                execute(DikcizBackups.TYPE_LIST, JSONObject().put("limit", 200)) { result, error ->
                    if (error != null) { feedback.text = error; return@execute }
                    val rows = result!!.getJSONArray("items")
                    if (rows.length() == 0) { feedback.text = "No encrypted backups saved yet."; return@execute }
                    val files = (0 until rows.length()).map { rows.getJSONObject(it).getString("path") }
                    password.text.clear()
                    val picker = AlertDialog.Builder(context).setTitle("Choose backup")
                        .setItems(files.map { it.substringAfterLast('/') }.toTypedArray()) { _, index -> show(files[index]) }
                        .setNegativeButton("Cancel") { _, _ -> show() }.create()
                    showDialog(picker) { emptyMap() }
                }
            }
        }
        form.addView(choose); views["backups:choose"] = choose
        val preview = Button(context).apply { text = "Preview restore"; isAllCaps = false }
        form.addView(preview); views["backups:preview"] = preview
        preview.setOnClickListener {
            val request = JSONObject().put("path", path.text.toString()).put("passphrase", password.text.toString())
            password.text.clear()
            preview.isEnabled = false
            feedback.text = "Decrypting and validating…"
            execute(DikcizBackups.TYPE_PREVIEW, request) { result, error ->
                preview.isEnabled = true
                if (error != null) feedback.text = error else confirm(result!!)
            }
        }
        val dialog = AlertDialog.Builder(context).setTitle("Backup and restore")
            .setView(ScrollView(context).apply { addView(form) }).setNegativeButton("Close", null).create()
        showDialog(dialog) { views }
    }

    private fun confirm(preview: JSONObject) {
        val form = column()
        form.addView(TextView(context).apply {
            text = "${preview.getInt("pages")} pages · ${preview.getInt("widgets")} widgets\n" +
                "${preview.getInt("scripts")} scripts · ${preview.getInt("conversations")} conversations\n" +
                "${preview.getInt("profiles")} provider profiles\n\n" +
                "This replaces your current home and chat history. Control listener addresses and remote credentials stay unchanged. Active assistant work must be stopped first."
        })
        val activate = CheckBox(context).apply { text = "Activate restored scripts and chat event rules"; isChecked = false }
        form.addView(activate)
        val status = TextView(context).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        form.addView(status)
        val dialog = AlertDialog.Builder(context).setTitle("Restore this backup?")
            .setView(form).setPositiveButton("Restore", null).setNegativeButton("Cancel", null).create()
        showDialog(dialog) {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                button.isEnabled = false
                status.text = "Restoring validated snapshot…"
                execute(DikcizBackups.TYPE_RESTORE, JSONObject().put("previewId", preview.getString("previewId"))
                    .put("sha256", preview.getString("sha256")).put("confirm", true)
                    .put("activateAutomation", activate.isChecked)) { _, error ->
                    button.isEnabled = true
                    status.text = error ?: "Restored. Return to Home to view the restored layout."
                    if (error == null) { button.visibility = View.GONE; dialog.getButton(AlertDialog.BUTTON_NEGATIVE).text = "Done" }
                }
            }
            mapOf("backups:confirm" to button, "backups:activate" to activate, "backups:restore-status" to status)
        }
    }

    private fun execute(type: String, request: JSONObject, complete: (JSONObject?, String?) -> Unit) {
        worker.execute {
            try {
                val result = DikcizUiCommands.execute("settings:backups", type, request)
                handler.post { complete(result, null) }
            } catch (exception: Exception) {
                android.util.Log.e("DikcizBackups", "Backup operation failed: " + exception.javaClass.simpleName)
                val message = if (exception is DikcizAutomationRequestException) exception.message else null
                handler.post { complete(null, message ?: "Backup operation failed. Check the passphrase and storage, then retry.") }
            } finally { request.remove("passphrase") }
        }
    }

    private fun column() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val padding = (16 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)
    }

    private companion object {
        val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "dikciz-backups").apply { isDaemon = true } }
    }
}
