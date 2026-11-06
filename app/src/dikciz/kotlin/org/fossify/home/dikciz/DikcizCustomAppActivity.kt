package org.fossify.home.dikciz

import android.app.Activity
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
internal class DikcizCustomAppActivity : Activity() {
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
            view.setPadding(dp(16) + bars.left, bars.top, dp(16) + bars.right, bars.bottom)
            insets
        }
        render()
    }
    override fun onNewIntent(value: Intent) { super.onNewIntent(value); intent = value; selected = value.getStringExtra("appId"); render() }
    override fun onResume() { super.onResume(); foreground = WeakReference(this); if (web != null) renderedAppId = selected }
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
            }
    }

    private fun render() {
        hidden()
        observer?.stopWatching(); observer = null
        generation++
        views.clear(); root.removeAllViews(); web = null; scroll = null
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label(if (selected == null) "Dikciz apps" else selected!!, 22f).also { views["custom-app:title"] = it }, LinearLayout.LayoutParams(0, -2, 1f))
        if (selected != null) header.addView(button("custom-app:back", "Apps") { selected = null; render() })
        header.addView(button("custom-app:close", "Close") { finish() })
        root.addView(header)
        status = label("Loading apps...").apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        views["custom-app:status"] = status; root.addView(status)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        perform({ DikcizCustomApps.ensureBundled(applicationContext); if (selected == null) owner(DikcizCustomAppCommands.LIST, JSONObject().put("limit", 200)) else owner(DikcizCustomAppCommands.READ, JSONObject().put("appId", selected)) }) { result ->
            if (selected == null) manager(result) else app(result)
        }
    }

    private fun manager(result: JSONObject) {
        status.text = "Apps share the Android access granted to Dikciz. Open, edit or stop an app here."
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply { addView(list); isFillViewport = true }
        content.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        list.addView(button("custom-app:new", "Create app") { editor(null) })
        list.addView(button("custom-app:import", "Import app archive") { importDialog() })
        val items = result.getJSONArray("items")
        repeat(items.length()) { index ->
            val app = items.getJSONObject(index)
            val id = app.getString("appId")
            list.addView(label(app.getString("title") + if (app.getBoolean("enabled")) "" else " (disabled)", 20f))
            list.addView(label(app.getString("description")))
            list.addView(button("custom-app:$id:open", "Open " + app.getString("title")) {
                perform({ control(id, "enable") }) { selected = id; render() }
            })
            list.addView(button("custom-app:$id:edit", "Edit bundle") {
                perform({ owner(DikcizCustomAppCommands.READ, JSONObject().put("appId", id)) }) { editor(it) }
            })
            if (app.getBoolean("enabled")) list.addView(button("custom-app:$id:disable", "Disable and stop") { perform({ control(id, "disable") }) })
            else list.addView(button("custom-app:$id:enable", "Start app") { perform({ control(id, "enable") }) })
            list.addView(button("custom-app:$id:export", "Export reusable bundle") {
                perform({ control(id, "export") }) { status.text = "Saved " + it.getString("path") + ". State and recordings are excluded." }
            })
            list.addView(button("custom-app:$id:remove", "Remove app and its state") {
                confirm("Remove app?", "This removes this app's files and state. Export a reusable bundle first if needed.") { perform({ control(id, "remove", true) }) }
            })
        }
    }

    private fun app(data: JSONObject) {
        if (!data.getBoolean("enabled")) { status.text = "This app is stopped. Return to Apps to open or start it."; return }
        val manifest = data.getJSONObject("manifest")
        val id = manifest.getString("id")
        (views["custom-app:title"] as? TextView)?.text = manifest.getString("title")
        status.visibility = View.GONE
        val widget = HtmlHomeWidget(id, manifest.getString("title"), data.getString("html"), data.getString("css"), data.getString("javascript"),
            data.getJSONObject("state"), enabled = true, cell = DikcizGridRectangle(0, 0, 1, 1))
        web = DikcizHtmlWidgetView(this, widget, JSONObject().put("id", id).put("appId", id).put("address", id)
            .put("state", widget.state).put("storagePath", data.getString("directory") + "/files"), logger,
            { request -> DikcizCustomApps.scopedCommand(applicationContext, id, request) }, {},
            { status.visibility = View.VISIBLE; status.text = "Edit this app's complete bundle from Apps > Edit bundle." },
            { status.visibility = View.VISIBLE; status.text = "App renderer stopped. Return to Apps to reopen it." })
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

    private fun editor(data: JSONObject?) {
        generation++; views.clear(); root.removeAllViews(); web = null
        root.addView(button("custom-app:editor:cancel", "Back to apps") { selected = null; render() })
        status = label("Edit this app's complete bundle. Saving preserves its running or stopped state."); root.addView(status)
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply { addView(form) }; root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val manifest = data?.getJSONObject("manifest") ?: JSONObject().put("version", 1).put("id", "my-app-" + java.util.UUID.randomUUID().toString().take(8)).put("title", "My app")
            .put("description", "An editable custom app").put("language", "javascript").put("events", JSONArray())
        val fields = linkedMapOf<String, EditText>()
        val values = linkedMapOf("manifest" to manifest.toString(2), "html" to (data?.getString("html") ?: "<h1>My app</h1>"),
            "css" to (data?.getString("css") ?: "body{padding:24px}"), "javascript" to (data?.getString("javascript") ?: ""),
            "backgroundSource" to (data?.getString("backgroundSource") ?: "function onEvent(event) { return {status: 'Ready', actions: []}; }"))
        for ((name, value) in values) {
            form.addView(label(name, 18f))
            fields[name] = EditText(this).apply {
                setText(value); setTextColor(DikcizUiStyle.FOREGROUND); typeface = android.graphics.Typeface.MONOSPACE
                setSingleLine(false); minLines = 3; maxLines = 10; gravity = Gravity.TOP; contentDescription = name
                views["custom-app:editor:" + name] = this; form.addView(this, LinearLayout.LayoutParams(-1, -2))
            }
        }
        root.addView(button("custom-app:editor:save", "Save bundle") {
            try {
                val request = JSONObject().put("manifest", JSONObject(fields.getValue("manifest").text.toString())).put("confirm", true)
                fields.filterKeys { it != "manifest" }.forEach { (name, input) -> request.put(name, input.text.toString()) }
                perform({ owner(DikcizCustomAppCommands.INSTALL, request) }) { selected = null; render() }
            } catch (exception: org.json.JSONException) { status.text = "Manifest must be a JSON object." }
        })
    }

    private fun importDialog() {
        val path = EditText(this).apply { hint = "/sdcard/Dikciz/apps/imports/example.dikciz-app.zip"; contentDescription = "App archive path" }
        modal = AlertDialog.Builder(this).setTitle("Import app bundle").setView(path).setNegativeButton("Cancel", null)
            .setPositiveButton("Import app") { _, _ -> perform({ owner(DikcizCustomAppCommands.CONTROL, JSONObject().put("action", "import").put("path", path.text.toString()).put("confirm", true)) }) }
            .create().also { dialog ->
                views["custom-app:import:path"] = path
                dialog.setOnShowListener { views["custom-app:import:confirm"] = dialog.getButton(AlertDialog.BUTTON_POSITIVE) }
                dialog.setOnDismissListener { views.remove("custom-app:import:path"); views.remove("custom-app:import:confirm"); modal = null }
                dialog.show()
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
