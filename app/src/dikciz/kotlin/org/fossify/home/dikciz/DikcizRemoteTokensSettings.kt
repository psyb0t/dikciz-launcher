package org.fossify.home.dikciz

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.text.InputType
import android.text.format.DateUtils
import org.fossify.home.dikciz.DikcizAutomationControlPlane.Companion.KEY_ACTION
import org.fossify.home.dikciz.DikcizRemoteAccessAuth.Companion.KEY_ENABLED
import org.fossify.home.dikciz.DikcizRemotePolicy.ACTION_CREATE
import org.fossify.home.dikciz.DikcizRemotePolicy.ACTION_LIST
import org.fossify.home.dikciz.DikcizRemotePolicy.ACTION_REVOKE
import org.fossify.home.dikciz.DikcizRemotePolicy.ACTION_UPDATE
import org.fossify.home.dikciz.DikcizRemotePolicy.KEY_ID
import org.fossify.home.dikciz.DikcizRemotePolicy.KEY_NAME
import org.fossify.home.dikciz.DikcizRemotePolicy.KEY_PERMISSIONS
import org.fossify.home.dikciz.DikcizRemotePolicy.KEY_TOKEN
import org.fossify.home.dikciz.DikcizRemotePolicy.KEY_TOKENS
import org.json.JSONArray
import org.json.JSONObject

/** Native owner UI. Generated secrets remain outside the visible and Accessibility trees. */
internal class DikcizRemoteTokensSettings(
    private val context: Context,
    private val auth: DikcizRemoteAccessAuth,
    private val showDialog: (AlertDialog, () -> Map<String, View>) -> Unit,
) {
    fun show() {
        val form = column()
        val views = linkedMapOf<String, View>()
        val message = TextView(context).apply {
            text = "Named bearer tokens share these HTTP, WebSocket and MCP listeners. Each token has its own grants. Enable owner authentication first."
        }
        form.addView(message)
        fun error(exception: Exception) {
            message.text = exception.message ?: "Could not manage access tokens"
        }
        val create = button("Create token") { edit(null) }
        form.addView(create)
        views["remote-tokens:create"] = create
        try {
            val rows = auth.tokenCommandFromPhone(JSONObject().put(KEY_ACTION, ACTION_LIST)).getJSONArray(KEY_TOKENS)
            for (position in 0 until rows.length()) {
                val item = rows.getJSONObject(position)
                val id = item.getString(KEY_ID)
                val entry = column().apply {
                    val inset = (12 * resources.displayMetrics.density).toInt()
                    setPadding(inset, inset, inset, inset)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = inset.toFloat(); setColor(DikcizUiStyle.SURFACE)
                    }
                }
                val actions = LinearLayout(context).apply { visibility = View.GONE }
                val destructive = LinearLayout(context).apply { visibility = View.GONE }
                val more = button("Manage " + item.getString(KEY_NAME)) {
                    val expanded = actions.visibility != View.VISIBLE
                    actions.visibility = if (expanded) View.VISIBLE else View.GONE
                    destructive.visibility = actions.visibility
                }
                views["remote-tokens:$id:more"] = more
                form.addView(entry, LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = (12 * context.resources.displayMetrics.density).toInt()
                })
                val label = TextView(context).apply {
                    text = item.getString(KEY_NAME) + " · " + (if (item.getBoolean(KEY_ENABLED)) "Enabled" else "Disabled") +
                        "\n" + (0 until item.getJSONArray(KEY_PERMISSIONS).length())
                            .joinToString(", ") { item.getJSONArray(KEY_PERMISSIONS).getString(it) }
                }
                entry.addView(label)
                entry.addView(TextView(context).apply {
                    val expiry = item.optLong(DikcizTokenRules.KEY_EXPIRES)
                    val used = item.optLong("lastUsedAt")
                    val rate = item.optInt(DikcizTokenRules.KEY_RATE)
                    text = (if (item.optBoolean("expired")) "Expired" else if (expiry == 0L) "No expiry" else
                        "Expires " + DateUtils.getRelativeTimeSpanString(expiry, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)) +
                        "\n" + (if (used == 0L) "Not used yet" else "Last used " +
                            DateUtils.getRelativeTimeSpanString(used, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)) +
                        "\n" + (if (rate == 0) "No request limit" else "$rate requests per minute")
                })
                val rotate = button("Rotate token") {
                    val confirmation = AlertDialog.Builder(context).setTitle("Rotate " + item.getString(KEY_NAME) + "?")
                        .setMessage("The old token stops working immediately. Copy the replacement before closing the next screen.")
                        .setPositiveButton("Rotate") { _, _ ->
                            try {
                                val replacement = auth.tokenCommandFromPhone(JSONObject().put(KEY_ACTION, DikcizRemotePolicy.ACTION_ROTATE).put(KEY_ID, id))
                                showCreated(replacement.getString(KEY_TOKEN))
                            } catch (exception: Exception) { error(exception) }
                        }.setNegativeButton("Cancel", null).create()
                    showDialog(confirmation) { emptyMap() }
                }
                actions.addView(rotate, LinearLayout.LayoutParams(0, -2, 1f))
                views["remote-tokens:$id:rotate"] = rotate
                val edit = button("Edit " + item.getString(KEY_NAME)) { edit(item) }
                actions.addView(edit, 0, LinearLayout.LayoutParams(0, -2, 1f))
                entry.addView(more)
                entry.addView(actions)
                views["remote-tokens:$id:edit"] = edit
                val toggle = button(if (item.getBoolean(KEY_ENABLED)) "Disable" else "Enable") {
                    try {
                        auth.tokenCommandFromPhone(JSONObject().put(KEY_ACTION, ACTION_UPDATE).put(KEY_ID, id)
                            .put(KEY_ENABLED, !item.getBoolean(KEY_ENABLED)))
                        show()
                    } catch (exception: Exception) {
                        error(exception)
                    }
                }
                destructive.addView(toggle, LinearLayout.LayoutParams(0, -2, 1f))
                views["remote-tokens:$id:toggle"] = toggle
                val revoke = button("Revoke") {
                    val confirmation = AlertDialog.Builder(context).setTitle("Revoke " + item.getString(KEY_NAME) + "?")
                        .setMessage("Connected clients lose access. This cannot be undone.")
                        .setPositiveButton("Revoke") { _, _ ->
                            try {
                                auth.tokenCommandFromPhone(JSONObject().put(KEY_ACTION, ACTION_REVOKE).put(KEY_ID, id))
                                show()
                            } catch (exception: Exception) {
                                error(exception)
                            }
                        }.setNegativeButton("Cancel", null).create()
                    showDialog(confirmation) {
                        mapOf("remote-tokens:confirm-revoke" to confirmation.getButton(AlertDialog.BUTTON_POSITIVE))
                    }
                }
                destructive.addView(revoke, LinearLayout.LayoutParams(0, -2, 1f))
                entry.addView(destructive)
                views["remote-tokens:$id:revoke"] = revoke
            }
        } catch (exception: Exception) {
            error(exception)
        }
        val dialog = AlertDialog.Builder(context).setTitle("Access tokens").setView(scroll(form))
            .setNegativeButton("Close", null).create()
        showDialog(dialog) { views }
    }

    private fun edit(item: JSONObject?) {
        val form = column()
        val name = EditText(context).apply {
            hint = "Token name"
            contentDescription = hint
            setText(item?.getString(KEY_NAME).orEmpty())
        }
        form.addView(name)
        val message = TextView(context)
        form.addView(message)
        form.addView(TextView(context).apply { text = "Expiry" })
        val durations = listOf(0L, 3600000L, 86400000L, 604800000L, 2592000000L)
        val expiry = Spinner(context).apply {
            contentDescription = "Token expiry"
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                listOf(if (item == null) "No expiry" else "Keep existing expiry", "1 hour", "1 day", "7 days", "30 days", "Remove expiry"))
        }
        form.addView(expiry)
        form.addView(TextView(context).apply { text = "Requests per minute. Zero means unlimited." })
        val rate = EditText(context).apply {
            contentDescription = "Token requests per minute"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(item?.optInt(DikcizTokenRules.KEY_RATE)?.toString() ?: "0")
        }
        form.addView(rate)
        form.addView(TextView(context).apply { text = "Allowed SMS addresses, one per line. Blank allows all. Restricted reads must specify filter.address." })
        val addresses = EditText(context).apply {
            contentDescription = "Allowed SMS addresses"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            val values = item?.optJSONArray(DikcizTokenRules.KEY_SMS)
            setText(if (values == null) "" else (0 until values.length()).joinToString("\n") { values.getString(it) })
        }
        form.addView(addresses)
        form.addView(TextView(context).apply { text = "Permissions"; textSize = 18f })
        val chosen = item?.getJSONArray(KEY_PERMISSIONS)?.let { rows ->
            (0 until rows.length()).map(rows::getString).toSet()
        }.orEmpty()
        val checks = DikcizRemotePolicy.GRANTS.associate { grant ->
            grant.id to CheckBox(context).apply {
                text = grant.title + "\n" + grant.description
                isChecked = grant.id in chosen
                form.addView(this)
            }
        }
        checks.forEach { (id, check) ->
            check.setOnCheckedChangeListener { _, checked ->
                if (!checked) return@setOnCheckedChangeListener
                if (id == DikcizRemotePolicy.OWNER) {
                    checks.filterKeys { it != id }.values.forEach { it.isChecked = false }
                } else checks.getValue(DikcizRemotePolicy.OWNER).isChecked = false
            }
        }
        val save: () -> Unit = {
            try {
                val request = JSONObject().put(KEY_ACTION, if (item == null) ACTION_CREATE else ACTION_UPDATE)
                    .put(KEY_NAME, name.text.toString())
                    .put(KEY_PERMISSIONS, JSONArray(checks.filterValues { it.isChecked }.keys.toList()))
                item?.let { request.put(KEY_ID, it.getString(KEY_ID)) }
                val rateValue = rate.text.toString().toIntOrNull()
                    ?: throw IllegalArgumentException("Enter a whole request limit")
                request.put(DikcizTokenRules.KEY_RATE, rateValue)
                request.put(DikcizTokenRules.KEY_SMS, JSONArray(addresses.text.toString().lineSequence()
                    .map(String::trim).filter(String::isNotEmpty).toList()))
                val selection = expiry.selectedItemPosition
                if (selection > 0) request.put(DikcizTokenRules.KEY_EXPIRES,
                    if (selection == durations.size) 0L else System.currentTimeMillis() + durations[selection])
                val result = auth.tokenCommandFromPhone(request)
                if (item == null) showCreated(result.getString(KEY_TOKEN)) else show()
            } catch (exception: Exception) {
                message.text = exception.message ?: "Could not save token"
            }
        }
        val views = linkedMapOf<String, View>("remote-tokens:name" to name,
            "remote-tokens:expiry" to expiry, "remote-tokens:rate" to rate, "remote-tokens:sms-addresses" to addresses)
        checks.forEach { (id, view) -> views["remote-tokens:grant:$id"] = view }
        val dialog = AlertDialog.Builder(context).setTitle(if (item == null) "Create access token" else "Edit access token")
            .setView(scroll(form)).setPositiveButton(if (item == null) "Create" else "Save", null)
            .setNegativeButton("Cancel") { _, _ -> show() }.create()
        showDialog(dialog) {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener { save() }
            views + ("remote-tokens:save" to saveButton)
        }
    }

    private fun showCreated(secret: String) {
        val form = column()
        form.addView(TextView(context).apply {
            text = "Token created. Copy it now. The stored verifier cannot reveal this token later."
        })
        val copy = button("Copy token") {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Dikciz access token", secret)
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
            clipboard.setPrimaryClip(clip)
        }
        form.addView(copy)
        val dialog = AlertDialog.Builder(context).setTitle("Copy access token").setView(form)
            .setNegativeButton("Done") { _, _ -> show() }.create()
        showDialog(dialog) { mapOf("remote-tokens:copy" to copy) }
    }

    private fun column() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val padding = (16 * resources.displayMetrics.density).toInt()
        setPadding(padding, 0, padding, 0)
    }
    private fun scroll(content: View) = ScrollView(context).apply { addView(content) }
    private fun button(label: String, action: () -> Unit) = Button(context).apply {
        text = label
        contentDescription = label
        setOnClickListener { action() }
    }
}
