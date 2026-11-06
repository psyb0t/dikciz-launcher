package org.fossify.home.dikciz

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

/** Native app management and a WebView host; app behavior remains in editable bundle files. */
internal open class DikcizCustomAppActivity : androidx.activity.ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val views = linkedMapOf<String, View>()
    private lateinit var content: LinearLayout
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var logger: DikcizLogger
    private lateinit var commands: DikcizNativeData
    private var web: DikcizHtmlWidgetView? = null
    private var scroll: ScrollView? = null
    private var selected: String? = null
    private var renderedAppId: String? = null
    private var modal: AlertDialog? = null
    private var generation = 0
    private var observer: FileObserver? = null
    private var editorFields: Map<String, EditText>? = null
    private var editorOriginal: Map<String, String>? = null
    private var editorFile = 0
    private var managerLoaded = false
    private val editorBack = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { leaveEditor() }
    }
    private data class EditorDraft(val values: Map<String, String>, val original: Map<String, String>, val file: Int)
    private val reload = Runnable { if (!isDestroyed) render() }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        logger = DikcizLogger("DikcizAppScreen", HomeConfigStore(this).logDirectory)
        commands = DikcizNativeData(applicationContext, logger)
        selected = state?.getString("appId") ?: intent.getStringExtra("appId")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(DikcizUiStyle.BACKGROUND) }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            val margin = if (selected == null) dp(16) else 0
            view.setPadding(margin + bars.left, bars.top, margin + bars.right, bars.bottom)
            insets
        }
        onBackPressedDispatcher.addCallback(this, editorBack)
        val draft = lastCustomNonConfigurationInstance as? EditorDraft
        if (draft == null) render() else editor(null, draft)
    }
    override fun onNewIntent(value: Intent) {
        super.onNewIntent(value)
        intent = value
        val next = value.getStringExtra("appId")
        if (next == selected) return
        selected = next
        render()
    }
    override fun onResume() {
        super.onResume()
        foreground = WeakReference(this)
        if (selected == null && editorFields == null && modal == null && managerLoaded) render()
        if (web != null) {
            renderedAppId = selected
            selected?.let { stateChanged(it) }
        }
    }
    override fun onRetainCustomNonConfigurationInstance(): Any? = editorFields?.let { fields ->
        EditorDraft(fields.mapValues { it.value.text.toString() }, editorOriginal.orEmpty(), editorFile)
    }
    private fun leaveEditor() {
        val dirty = editorFields?.any { (name, field) -> field.text.toString() != editorOriginal?.get(name) } == true
        if (!dirty) { render(); return }
        confirm("Discard changes?", "Your unsaved bundle edits will be discarded.") { render() }
    }
    override fun onPause() { hidden(); if (foreground.get() === this) foreground.clear(); super.onPause() }
    override fun onSaveInstanceState(state: Bundle) { state.putString("appId", selected); super.onSaveInstanceState(state) }
    override fun onDestroy() { hidden(); generation++; observer?.stopWatching(); handler.removeCallbacks(reload); root.removeAllViews(); modal?.dismiss(); worker.shutdown(); super.onDestroy() }

    private fun hidden() {
        val id = renderedAppId ?: return
        renderedAppId = null
        worker.execute {
            try { owner(DikcizCustomAppCommands.EVENT, JSONObject().put("appId", id).put("event", "app.ui.hidden")) }
            catch (exception: Exception) { logger.warn("custom_app_hidden_handler_failed", mapOf("app_id" to id, "error_class" to exception.javaClass.simpleName)) }
        }
    }

    private fun owner(type: String, fields: JSONObject = JSONObject()): JSONObject {
        val request = JSONObject(fields.toString()).put("type", type)
        return DikcizOperations.withActor("apps:settings") {
            DikcizOperations.trace(request, null, false) { commands.execute(DikcizAutomationCommand(type, request)) }
        }
    }
    private fun perform(action: () -> JSONObject, complete: (JSONObject) -> Unit = { render() }) {
        val expected = generation
        worker.execute {
            try {
                val result = action()
                handler.post { if (!isDestroyed && expected == generation) complete(result) }
            } catch (exception: Exception) {
                logger.warn("custom_app_screen_operation_failed", mapOf("error_class" to exception.javaClass.simpleName))
                handler.post { if (!isDestroyed && expected == generation) { status.visibility = View.VISIBLE; status.text = exception.message ?: "App operation failed" } }
            }
        }
    }
    private fun label(value: String, size: Float = 16f) = TextView(this).apply {
        text = value; textSize = size; setTextColor(DikcizUiStyle.FOREGROUND); setPadding(0, dp(8), 0, dp(8))
    }
    private fun button(id: String, title: String, action: () -> Unit) = Button(this).apply {
        text = title; contentDescription = title; isAllCaps = false; DikcizUiStyle.button(this)
        setOnClickListener { action() }; views[id] = this
    }
    private fun control(id: String, action: String, confirm: Boolean = false) = owner(DikcizCustomAppCommands.CONTROL,
        JSONObject().put("appId", id).put("action", action).put("confirm", confirm))
    private fun confirm(title: String, detail: String, action: () -> Unit) {
        modal = AlertDialog.Builder(this).setTitle(title).setMessage(detail).setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ -> action() }.create().also { dialog ->
                dialog.setOnShowListener { views["custom-app:confirm"] = dialog.getButton(AlertDialog.BUTTON_POSITIVE) }
                dialog.setOnDismissListener { views.remove("custom-app:confirm"); modal = null }
                dialog.show()
                DikcizUiStyle.dialog(dialog)
            }
    }

    private fun render() {
        hidden()
        observer?.stopWatching(); observer = null
        generation++
        views.clear(); root.removeAllViews(); web = null; scroll = null
        editorBack.isEnabled = false
        managerLoaded = false
        editorFields = null
        editorOriginal = null
        if (selected == null) {
            val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            header.addView(label("Dikciz apps", 22f).also { views["custom-app:title"] = it }, LinearLayout.LayoutParams(0, -2, 1f))
            header.addView(button("custom-app:close", "Close") { finish() })
            root.addView(header)
        }
        status = label("Loading apps...").apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        views["custom-app:status"] = status; root.addView(status)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        perform({ DikcizCustomApps.ensureBundled(applicationContext); if (selected == null) owner(DikcizCustomAppCommands.LIST, JSONObject().put("limit", 200)) else owner(DikcizCustomAppCommands.READ, JSONObject().put("appId", selected)) }) { result ->
            if (selected == null) manager(result) else app(result)
        }
    }

    private fun manager(result: JSONObject) {
        managerLoaded = true
        status.text = "Apps share the Android access granted to Dikciz. Open, edit or stop an app here."
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply { addView(list); isFillViewport = true }
        content.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        toolbar.addView(button("custom-app:new", "Create app") { editor(null) }.apply {
            DikcizUiStyle.button(this, primary = true)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(button("custom-app:import", "Import") { importDialog() }, LinearLayout.LayoutParams(0, -2, 1f))
        list.addView(toolbar)

        val items = result.getJSONArray("items")
        repeat(items.length()) { index ->
            val app = items.getJSONObject(index)
            val id = app.getString("appId")
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12), 0, 0)
            }
            row.addView(button("custom-app:$id:open", app.getString("title")) {
                perform({ control(id, "open") }) { }
            }.apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                textSize = 18f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(button("custom-app:$id:more", "\u22ee") {
                val actions = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(8), dp(16), dp(8))
                }
                actions.addView(button("custom-app:$id:edit", "Edit bundle") {
                    modal?.dismiss()
                    perform({ owner(DikcizCustomAppCommands.READ, JSONObject().put("appId", id)) }) { editor(it) }
                })
                if (app.getBoolean("enabled")) actions.addView(button("custom-app:$id:disable", "Disable and stop") { modal?.dismiss(); perform({ control(id, "disable") }) })
                else actions.addView(button("custom-app:$id:enable", "Start app") { modal?.dismiss(); perform({ control(id, "enable") }) })
                actions.addView(button("custom-app:$id:export", "Export reusable bundle") {
                    modal?.dismiss()
                    perform({ control(id, "export") }) { status.text = "Saved " + it.getString("path") + ". State and recordings are excluded." }
                })
                actions.addView(button("custom-app:$id:remove", "Remove app and its state") {
                    modal?.dismiss()
                    confirm("Remove app?", "This removes this app's files and state. Export a reusable bundle first if needed.") { modal?.dismiss(); perform({ control(id, "remove", true) }) }
                })
                val dialog = AlertDialog.Builder(this).setTitle(app.getString("title"))
                    .setView(ScrollView(this).apply { addView(actions) })
                    .setNegativeButton("Cancel", null).create()
                modal = dialog
                dialog.setOnDismissListener { if (modal === dialog) modal = null }
                dialog.show()
                DikcizUiStyle.dialog(dialog)
            }.apply { contentDescription = "Actions for " + app.getString("title") }, LinearLayout.LayoutParams(dp(48), dp(48)))
            list.addView(row)
            list.addView(label(app.getString("description") + if (app.getBoolean("enabled")) "" else "\nStopped").apply {
                setTextColor(DikcizUiStyle.MUTED)
            })
        }
    }

    private fun app(data: JSONObject) {
        if (!data.getBoolean("enabled")) {
            status.text = "This app is stopped."
            content.addView(button("custom-app:restart", "Start app") { perform({ control(selected!!, "enable") }) })
            return
        }
        val manifest = data.getJSONObject("manifest")
        val id = manifest.getString("id")
        title = manifest.getString("title")
        @Suppress("DEPRECATION")
        setTaskDescription(android.app.ActivityManager.TaskDescription(title.toString()))
        status.visibility = View.GONE
        val widget = HtmlHomeWidget(id, manifest.getString("title"), data.getString("html"), data.getString("css"), data.getString("javascript"),
            data.getJSONObject("state"), enabled = true, cell = DikcizGridRectangle(0, 0, 1, 1))
        web = DikcizHtmlWidgetView(this, widget, JSONObject().put("id", id).put("appId", id).put("address", id)
            .put("state", widget.state).put("storagePath", data.getString("directory") + "/files"), logger,
            { request -> DikcizCustomApps.scopedCommand(applicationContext, id, request) }, {},
            {},
            {
                status.visibility = View.VISIBLE
                status.text = "App renderer stopped."
                content.removeAllViews()
                content.addView(button("custom-app:retry", "Reload app") { render() })
            })
        content.addView(web, LinearLayout.LayoutParams(-1, -1))
        renderedAppId = id
        @Suppress("DEPRECATION")
        val watcher = object : FileObserver(data.getString("directory"), FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or FileObserver.CREATE or FileObserver.DELETE_SELF or FileObserver.MOVE_SELF) {
            override fun onEvent(event: Int, name: String?) {
                if (event and (FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or FileObserver.CREATE or FileObserver.DELETE_SELF or FileObserver.MOVE_SELF) == 0) return
                handler.post {
                    if (isDestroyed || selected != id) return@post
                    if (name == "state.json") stateChanged(id)
                    else if (name == null || name in setOf("app.json", "ui.html", "ui.css", "ui.js")) {
                        handler.removeCallbacks(reload); handler.postDelayed(reload, FILE_DEBOUNCE_MILLISECONDS)
                    }
                }
            }
        }
        observer = watcher
        watcher.startWatching()
    }

    private fun editor(data: JSONObject?, draft: EditorDraft? = null) {
        editorBack.isEnabled = true
        generation++; views.clear(); root.removeAllViews(); web = null
        root.addView(button("custom-app:editor:cancel", "Back to apps") { leaveEditor() })
        status = label("Edit this app's complete bundle. Saving preserves its running or stopped state."); root.addView(status)
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply { addView(form) }; root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val manifest = data?.getJSONObject("manifest") ?: JSONObject().put("version", 1).put("id", "my-app-" + java.util.UUID.randomUUID().toString().take(8)).put("title", "My app")
            .put("description", "An editable custom app").put("language", "javascript").put("events", JSONArray())
        val fields = linkedMapOf<String, EditText>()
        val values = linkedMapOf("manifest" to manifest.toString(2), "html" to (data?.getString("html") ?: "<h1>My app</h1>"),
            "css" to (data?.getString("css") ?: "body{padding:24px}"), "javascript" to (data?.getString("javascript") ?: ""),
            "backgroundSource" to (data?.getString("backgroundSource") ?: "function onEvent(event) { return {status: 'Ready', actions: []}; }"))
        draft?.values?.let { values.putAll(it) }
        editorOriginal = draft?.original ?: values.toMap()
        editorFields = fields
        editorFile = draft?.file ?: 0
        val names = values.keys.toList()
        val picker = android.widget.Spinner(this).apply {
            contentDescription = "Bundle file"
            minimumHeight = dp(48)
            adapter = android.widget.ArrayAdapter(this@DikcizCustomAppActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Manifest", "HTML", "CSS", "UI JavaScript", "Background source"))
        }
        root.addView(picker, 2)
        views["custom-app:editor:file"] = picker
        for ((name, value) in values) {
            fields[name] = EditText(this).apply {
                setText(value); setTextColor(DikcizUiStyle.FOREGROUND); typeface = android.graphics.Typeface.MONOSPACE
                setSingleLine(false); minLines = 16; maxLines = 30; gravity = Gravity.TOP; contentDescription = name
                views["custom-app:editor:" + name] = this; form.addView(this, LinearLayout.LayoutParams(-1, -2))
            }
        }
        fun selectFile(position: Int) {
            editorFile = position
            fields.forEach { (name, input) -> input.visibility = if (name == names[position]) View.VISIBLE else View.GONE }
            scroll?.scrollTo(0, 0)
        }
        picker.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectFile(position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        picker.setSelection(editorFile)
        selectFile(editorFile)
        root.addView(button("custom-app:editor:save", "Save bundle") {
            try {
                val request = JSONObject().put("manifest", JSONObject(fields.getValue("manifest").text.toString())).put("confirm", true)
                fields.filterKeys { it != "manifest" }.forEach { (name, input) -> request.put(name, input.text.toString()) }
                perform({ owner(DikcizCustomAppCommands.INSTALL, request) }) { selected = null; render() }
            } catch (exception: org.json.JSONException) { status.text = "Manifest must be a JSON object." }
        }.apply { DikcizUiStyle.button(this, primary = true) })
    }

    private fun importDialog() {
        val path = EditText(this).apply { hint = "/sdcard/Dikciz/apps/imports/example.dikciz-app.zip"; contentDescription = "App archive path" }
        val error = label("").apply { visibility = View.GONE; setTextColor(DikcizUiStyle.ERROR) }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(path)
            addView(error)
        }
        val dialog = AlertDialog.Builder(this).setTitle("Import app bundle")
            .setView(form).setNegativeButton("Cancel", null).setPositiveButton("Import app", null).create()
        modal = dialog
        views["custom-app:import:path"] = path
        views["custom-app:import:error"] = error
        dialog.setOnDismissListener {
            views.remove("custom-app:import:path")
            views.remove("custom-app:import:confirm")
            views.remove("custom-app:import:error")
            if (modal === dialog) modal = null
        }
        dialog.show()
        DikcizUiStyle.dialog(dialog)
        val submit = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        views["custom-app:import:confirm"] = submit
        submit.setOnClickListener {
            val archive = path.text.toString()
            submit.isEnabled = false
            worker.execute {
                try {
                    owner(DikcizCustomAppCommands.CONTROL, JSONObject().put("action", "import").put("path", archive).put("confirm", true))
                    handler.post { if (!isDestroyed && dialog.isShowing) { dialog.dismiss(); render() } }
                } catch (exception: Exception) {
                    logger.warn("custom_app_import_failed", mapOf("error_class" to exception.javaClass.simpleName))
                    handler.post {
                        if (!isDestroyed && dialog.isShowing) {
                            error.text = exception.message ?: "Import failed. Check the archive path and try again."
                            error.visibility = View.VISIBLE
                            submit.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    fun deliverEvent(id: String, event: JSONObject) {
        if (selected == id) web?.deliverAutomationEvent(event)
    }
    fun stateChanged(id: String) {
        if (selected != id || web == null) return
        perform({ owner(DikcizCustomAppCommands.READ, JSONObject().put("appId", id)) }) { data ->
            if (!data.getBoolean("enabled")) render() else web?.deliverState(data.getJSONObject("state"))
        }
    }
    fun appId() = selected
    fun semanticViews(): Map<String, View> = views.filterValues { it.isShown && it.isAttachedToWindow }
    fun foregroundDialog() = modal
    fun scrollState() = JSONObject().put("y", scroll?.scrollY ?: 0).put("range", scroll?.getChildAt(0)?.height ?: 0).put("extent", scroll?.height ?: 0)
    fun scrollBy(delta: Int) { scroll?.scrollBy(0, delta) }
    fun scrollTo(view: View) { view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object {
        private const val FILE_DEBOUNCE_MILLISECONDS = 250L
        private var foreground = WeakReference<DikcizCustomAppActivity>(null)
        fun current() = foreground.get()?.takeUnless { it.isDestroyed || it.isFinishing }
    }
}
