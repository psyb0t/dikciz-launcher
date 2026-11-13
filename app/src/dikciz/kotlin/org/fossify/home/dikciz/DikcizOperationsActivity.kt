package org.fossify.home.dikciz

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Rect
import java.lang.ref.WeakReference
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.Executors
import org.json.JSONObject

internal class DikcizOperationsActivity : Activity() {
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var status: TextView
    private val views = linkedMapOf<String, View>()
    private var modal: AlertDialog? = null
    private var labelSequence = 0
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var resourcesTab = false
    private var mode = "activity"
    private var historyOffset = 0
    private var historyKind = "event"
    private lateinit var tools: DikcizOperationsTools
    private lateinit var sectionButton: Button
    private var failuresOnly = false
    private var filterMode = 0
    private lateinit var filterButton: Button
    private lateinit var activityTab: Button
    private lateinit var resourceTab: Button
    private lateinit var activityTabs: LinearLayout
    private var previousActivity = ""
    private var refreshing = false
    private var visible = false
    private val tick = object : Runnable {
        override fun run() {
            if (!visible) return
            refresh()
            handler.postDelayed(this, REFRESH_MILLISECONDS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = savedInstanceState?.getString("mode") ?: intent.getStringExtra("section") ?: "activity"
        historyOffset = savedInstanceState?.getInt("historyOffset") ?: 0
        historyKind = savedInstanceState?.getString("historyKind") ?: "event"
        resourcesTab = savedInstanceState?.getBoolean("resources") ?: false
        failuresOnly = savedInstanceState?.getBoolean("failures") ?: false
        filterMode = savedInstanceState?.getInt("filter") ?: 0
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val root = column().apply { setPadding(dp(20), dp(12), dp(20), 0); setBackgroundColor(BACKGROUND) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("Dikciz tools", 24, TEXT, true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("Close") { finish() }.also { views["operations:close"] = it })
        root.addView(header)
        sectionButton = button("Choose section") {
            AlertDialog.Builder(this).setTitle("Dikciz tools")
                .setItems(arrayOf("Automation debugger", "Skills, agents & rules", "Command activity", "Resources")) { _, selected ->
                    mode = listOf("history", "library", "activity", "resources")[selected]
                    resourcesTab = mode == "resources"; historyOffset = 0; updateTabs(); refresh(true)
                }.setNegativeButton("Cancel", null).show().also(::showModal)
        }
        views["operations:section"] = sectionButton
        root.addView(sectionButton)
        val tabs = LinearLayout(this).apply { setPadding(0, dp(12), 0, dp(12)) }
        activityTab = button("Activity") { mode = "activity"; resourcesTab = false; updateTabs(); refresh(true) }
        resourceTab = button("Resources") { mode = "resources"; resourcesTab = true; updateTabs(); refresh(true) }
        tabs.addView(activityTab, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(4) })
        tabs.addView(resourceTab, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(4) })
        views["operations:activity"] = activityTab
        views["operations:resources"] = resourceTab
        activityTabs = tabs
        tabs.visibility = if (mode in setOf("activity", "resources")) View.VISIBLE else View.GONE
        root.addView(tabs)
        filterButton = button(FILTERS[filterMode] + " ▾") {
            if (mode == "history") {
                AlertDialog.Builder(this).setTitle("Show history")
                    .setItems(arrayOf("All records", "Events", "Scripts", "Apps", "Commands", "Assistant turns", "Returned actions")) { _, selected ->
                        historyKind = listOf("", "event", "script", "app", "command", "chat", "action")[selected]
                        historyOffset = 0; updateTabs(); refresh(true)
                    }.setNegativeButton("Cancel", null).show().also(::showModal)
                return@button
            }
            AlertDialog.Builder(this).setTitle("Show activity")
                .setSingleChoiceItems(FILTERS.toTypedArray(), filterMode) { dialog, selected ->
                    filterMode = selected
                    filterButton.text = FILTERS[selected] + " ▾"
                    dialog.dismiss()
                    refresh(true)
                }.setNegativeButton("Cancel", null).show().also(::showModal)
        }
        views["operations:filter"] = filterButton
        root.addView(filterButton)
        updateTabs()
        status = label("Loading…", 13, MUTED)
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        root.addView(status)
        content = column()
        scroll = ScrollView(this).apply { isFillViewport = true; addView(content); contentDescription = "Activity history" }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        tools = DikcizOperationsTools(this, content, status, views, worker, handler, ::showModal) { refresh(true) }
        tools.restore(savedInstanceState)
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(20) + bars.left, dp(12) + bars.top, dp(20) + bars.right, bars.bottom)
            insets
        }
    }

    override fun onResume() { super.onResume(); foreground = WeakReference(this); visible = true; handler.post(tick) }
    override fun onPause() { foreground = null; visible = false; handler.removeCallbacks(tick); super.onPause() }
    override fun onDestroy() { modal?.dismiss(); handler.removeCallbacksAndMessages(null); worker.shutdown(); super.onDestroy() }
    override fun onSaveInstanceState(out: Bundle) {
        tools.save(out)
        out.putString("mode", mode); out.putInt("historyOffset", historyOffset); out.putString("historyKind", historyKind)
        out.putBoolean("resources", resourcesTab); out.putBoolean("failures", failuresOnly); out.putInt("filter", filterMode)
        super.onSaveInstanceState(out)
    }

    private fun refresh(force: Boolean = false) {
        if (refreshing || isFinishing || isDestroyed || (!force && modal?.isShowing == true) || (!force && ::scroll.isInitialized && scroll.scrollY > 0)) return
        refreshing = true
        val showingMode = mode
        val showingResources = mode == "resources"
        val selectedFilter = filterMode
        worker.execute {
            try {
                val request = JSONObject()
                if (showingMode == "library") request.put("action", "inspect")
                if (showingMode == "history") {
                    request.put("limit", HISTORY_PAGE_SIZE).put("offset", historyOffset)
                    if (historyKind.isNotBlank()) request.put("kind", historyKind)
                }
                if (showingMode == "activity") {
                    request.put("limit", PAGE_SIZE)
                    if (selectedFilter == 0) request.put("mutatingOnly", true)
                    if (selectedFilter == 2) request.put("outcome", "problem")
                    if (selectedFilter == 3) request.put("outcome", "running")
                }
                val result = DikcizUiCommands.execute("settings:operations",
                    when (showingMode) {
                        "library" -> DikcizAssistantLibrary.TYPE
                        "history" -> DikcizAutomationDebugger.TYPE_HISTORY
                        "resources" -> DikcizOperations.TYPE_RESOURCES
                        else -> DikcizOperations.TYPE_READ
                    }, request,
                )
                handler.post {
                    refreshing = false
                    if (!visible || showingMode != mode || selectedFilter != filterMode) return@post
                    val fingerprint = result.toString() + (System.currentTimeMillis() / DateUtils.MINUTE_IN_MILLIS)
                    if (!showingResources && !force && fingerprint == previousActivity) return@post
                    previousActivity = fingerprint
                    val y = scroll.scrollY
                    views.keys.filter { it.startsWith("operations:row:") || it.startsWith("operations:label:") || it.startsWith("operations:library:") || it.startsWith("operations:history:") }.forEach(views::remove)
                    labelSequence = 0
                    content.removeAllViews()
                    when (showingMode) {
                        "library" -> tools.renderLibrary(result)
                        "history" -> tools.renderHistory(result,
                            { historyOffset += HISTORY_PAGE_SIZE; scroll.scrollTo(0, 0); refresh(true) },
                            { historyOffset = (historyOffset - HISTORY_PAGE_SIZE).coerceAtLeast(0); scroll.scrollTo(0, 0); refresh(true) })
                        "resources" -> renderResources(result)
                        else -> renderActivity(result)
                    }
                    scroll.post { scroll.scrollTo(0, y) }
                }
            } catch (exception: Exception) {
                android.util.Log.e("DikcizOperations", "Activity view refresh failed", exception)
                handler.post { refreshing = false; status.text = exception.message ?: "Could not load this section. Choose it again to retry." }
            }
        }
    }

    private fun renderActivity(result: JSONObject) {
        val rows = result.getJSONArray("items")
        status.text = "Activity · " + result.getInt("total") + " records · retained for 7 days"
        if (rows.length() == 0) {
            card("Nothing here yet", if (filterMode == 0) "Actions will appear here when a client changes something. Choose All activity to include reads." else "No activity matches this filter.")
        }
        for (index in 0 until rows.length()) {
            val row = rows.getJSONObject(index)
            val outcome = row.getString("outcome")
            val command = row.getString("command").replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").replaceFirstChar(Char::uppercase)
            val caller = row.optString("actorName").ifBlank { row.getString("actor") }
            val box = card(command, friendlyActor(caller) + " · " +
                DateUtils.getRelativeTimeSpanString(row.getLong("startedAt"), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS))
            box.addView(label(outcome.replaceFirstChar(Char::uppercase) + " · " + row.getLong("durationMilliseconds") + " ms",
                14, if (outcome in setOf("failed", "rejected", "interrupted")) ERROR else ACCENT))
            if (!row.isNull("code")) box.addView(label(row.getString("code"), 13, MUTED))
            if (outcome == "running") box.addView(button("Stop this operation") {
                AlertDialog.Builder(this).setTitle("Stop this operation?")
                    .setMessage("Requests interruption. Android work may already have completed. Completed effects will remain.")
                    .setPositiveButton("Stop") { _, _ ->
                        worker.execute {
                            try {
                                val result = DikcizUiCommands.execute("settings:operations", DikcizOperations.TYPE_CANCEL, JSONObject().put("activityId", row.getString("id")))
                                handler.post { status.text = result.getString("message"); refresh() }
                            } catch (exception: Exception) {
                                android.util.Log.e("DikcizOperations", "Operation cancellation failed: " + exception.javaClass.simpleName)
                                handler.post { status.text = "Could not request cancellation. Check the operation's current state." }
                            }
                        }
                    }.setNegativeButton("Keep running", null).show().also(::showModal)
            }.also { views["operations:row:" + row.getString("id") + ":stop"] = it })
        }
        if (result.optBoolean("hasMore")) content.addView(label("Showing the latest $PAGE_SIZE records. Use activityRead to page through older history.", 13, MUTED))
    }

    private fun renderResources(result: JSONObject) {
        status.text = "Resources · updates while this screen is open"
        val process = result.getJSONObject("process")
        val cpu = process.getJSONObject("cpu")
        card("Dikciz process", if (cpu.optBoolean("appSampleAvailable")) {
            "%.1f%% CPU capacity".format(cpu.getDouble("appPercentOfCapacity"))
        } else "Measuring CPU. A second sample is needed.")
        val memory = process.getJSONObject("memory")
        card("Device memory", "${memory.getLong("availableBytes") / MEBIBYTE} MiB available of ${memory.getLong("totalBytes") / MEBIBYTE} MiB")
        card("Dikciz network", "Received ${result.getLong("uidReceivedBytes") / KIBIBYTE} KiB · sent ${result.getLong("uidSentBytes") / KIBIBYTE} KiB since boot")
        val scripts = result.getJSONArray("scripts")
        for (index in 0 until scripts.length()) {
            val script = scripts.getJSONObject(index)
            if (!script.getBoolean("enabled")) continue
            val box = card(script.getString("title"), if (script.getBoolean("enabled")) "Enabled · " +
                script.getJSONArray("subscriptions").toString() else "Disabled")
            if (script.getBoolean("enabled")) box.addView(button("Disable script") {
                AlertDialog.Builder(this).setTitle("Disable this script?")
                    .setMessage("Future event deliveries will stop. An in-flight action may finish.")
                    .setPositiveButton("Disable") { _, _ -> worker.execute {
                        try {
                            DikcizUiCommands.execute("settings:operations", DikcizOperations.TYPE_CONTROL, JSONObject().put("scriptId", script.getString("id")))
                            handler.post { refresh() }
                        } catch (exception: Exception) {
                            android.util.Log.e("DikcizOperations", "Script disable failed", exception)
                            handler.post { status.text = "Could not disable the script. Check storage access and retry." }
                        }
                    } }.setNegativeButton("Cancel", null).show().also(::showModal)
            }.also { views["operations:row:" + script.getString("id") + ":disable"] = it })
        }
        val widgets = result.getJSONArray("widgets")
        for (index in 0 until widgets.length()) {
            val widget = widgets.getJSONObject(index)
            if (!widget.getBoolean("enabled")) continue
            val box = card(widget.getString("title"), "HTML widget · " + if (widget.getBoolean("enabled")) "Enabled" else "Disabled")
            if (widget.getBoolean("enabled")) resourceButton(box, "Pause widget", "widget", widget.getString("id"), widget.getString("pageId"))
        }
        val conversations = result.getJSONObject("conversations").getJSONArray("items")
        for (index in 0 until conversations.length()) {
            val conversation = conversations.getJSONObject(index)
            val activeConversation = result.getJSONObject("assistant").optString("activeConversationId")
            if (conversation.optString("subscriptions", "[]") == "[]" && conversation.getString("id") != activeConversation) continue
            val box = card(conversation.getString("title"), "Assistant conversation")
            resourceButton(box, "Pause event rules and stop", "conversation", conversation.getString("id"))
        }
        val captures = result.getJSONObject("imageStreams").getJSONArray("items")
        for (index in 0 until captures.length()) {
            val capture = captures.getJSONObject(index)
            if (!capture.getBoolean("active")) continue
            val box = card("Live " + capture.getString("source"), capture.getString("state"))
            resourceButton(box, "Stop capture", "imageStream", capture.getString("streamId"))
        }
        card("Recorded callers", "Top five by executor CPU over retained history. Read-only calls are included.")
        val actors = result.getJSONArray("actors")
        for (index in 0 until minOf(5, actors.length())) {
            val item = actors.getJSONObject(index)
            card(friendlyActor(item.getString("actor")), "${item.getLong("commands")} commands · ${item.getLong("running")} running\n${item.getLong("executorThreadCpuMilliseconds")} ms executor CPU")
        }
        card("What Android can measure", result.getString("limitations"))
    }

    private fun resourceButton(box: LinearLayout, title: String, target: String, id: String, pageId: String? = null) {
        box.addView(button(title) {
            AlertDialog.Builder(this).setTitle(title + "?")
                .setMessage("Future work stops. Completed effects remain and in-flight actions may finish.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(title) { _, _ ->
                    worker.execute {
                        try {
                            val request = JSONObject().put("target", target).put("id", id)
                            if (pageId != null) request.put("pageId", pageId)
                            DikcizUiCommands.execute("settings:resources", DikcizOperations.TYPE_CONTROL, request)
                            handler.post { refresh(true) }
                        } catch (exception: Exception) {
                            android.util.Log.e("DikcizOperations", "Resource control failed: " + exception.javaClass.simpleName)
                            handler.post { status.text = "Could not stop this resource. Refresh and retry." }
                        }
                    }
                }.show().also(::showModal)
        }.also { views["operations:row:" + target + ":" + id + ":pause"] = it })
    }

    private fun card(title: String, detail: CharSequence): LinearLayout {
        val box = column().apply {
            background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(SURFACE) }
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        box.addView(label(title, 17, TEXT, true))
        box.addView(label(detail, 14, MUTED))
        content.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        return box
    }

    private fun label(value: CharSequence, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); setPadding(0, dp(4), 0, dp(4))
        if (bold) typeface = Typeface.DEFAULT_BOLD
        views[(if (::content.isInitialized) "operations:label:" else "operations:header:") + labelSequence++] = this
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; contentDescription = value; isAllCaps = false; minHeight = dp(48)
        DikcizUiStyle.button(this)
        setOnClickListener { action() }
    }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()

    private fun updateTabs() {
        DikcizUiStyle.button(activityTab, !resourcesTab)
        DikcizUiStyle.button(resourceTab, resourcesTab)
        activityTabs.visibility = if (mode in setOf("activity", "resources")) View.VISIBLE else View.GONE
        sectionButton.text = when (mode) {
            "library" -> "Skills, agents & rules ▾"
            "history" -> "Automation debugger ▾"
            "resources" -> "Resources ▾"
            else -> "Command activity ▾"
        }
        filterButton.text = if (mode == "history") (if (historyKind == "event") "Events" else historyKind.ifBlank { "All records" }.replaceFirstChar(Char::uppercase)) + " ▾" else FILTERS[filterMode] + " ▾"
        filterButton.visibility = if (mode in setOf("resources", "library")) View.GONE else View.VISIBLE
    }

    private fun friendlyActor(value: String): String = when {
        value in setOf("owner", "Owner", "local-owner") -> "You"
        value.startsWith("html:") -> "Widget · " + value.substringAfter('-').replace('-', ' ')
        value.startsWith("lua:") -> "Script · " + value.substringAfter(':').replace('-', ' ')
        value == "chat" || value.startsWith("chat:") -> "Dikciz chat"
        value == "remote-owner" -> "Remote owner"
        value == "settings:rules" -> "Automation rules"
        value == "settings:backups" -> "Backup settings"
        value in setOf("settings:resources", "settings:operations") -> "Activity & resources"
        else -> value
    }

    private fun showModal(dialog: AlertDialog) {
        modal = dialog
        DikcizUiStyle.dialog(dialog)
        dialog.setOnDismissListener { if (modal === dialog) modal = null }
    }

    fun semanticViews(): Map<String, View> {
        val dialog = modal?.takeIf { it.isShowing } ?: return views
        val result = linkedMapOf<String, View>()
        for ((name, buttonId) in mapOf("confirm" to AlertDialog.BUTTON_POSITIVE, "cancel" to AlertDialog.BUTTON_NEGATIVE)) {
            dialog.getButton(buttonId)?.takeIf { it.visibility == View.VISIBLE }?.let { result["operations:dialog:" + name] = it }
        }
        views.filterKeys { (it.startsWith("operations:editor:") || it.startsWith("operations:trace:")) }.forEach { (key, view) -> if (view.isAttachedToWindow) result[key] = view }
        dialog.findViewById<TextView>(android.R.id.message)?.let { result["operations:dialog:message"] = it }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.takeIf { it.visibility == View.VISIBLE }?.let { result["operations:dialog:neutral"] = it }
        dialog.listView?.let { list ->
            for (index in 0 until list.childCount) {
                val position = list.firstVisiblePosition + index
                result["operations:filter:" + position] = list.getChildAt(index).apply {
                    setOnClickListener { list.performItemClick(this, position, list.adapter.getItemId(position)) }
                }
            }
        }
        return result
    }
    fun foregroundDialog(): AlertDialog? = modal?.takeIf { it.isShowing }
    private fun dialogScroll(view: View?): ScrollView? {
        if (view is ScrollView && view.isShown) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) dialogScroll(view.getChildAt(index))?.let { return it }
        return null
    }
    private fun activeScroll(): ScrollView = dialogScroll(foregroundDialog()?.window?.decorView) ?: scroll
    fun scrollState(): JSONObject {
        val target = activeScroll()
        return JSONObject().put("y", target.scrollY).put("range", target.getChildAt(0)?.height ?: 0).put("extent", target.height)
    }
    fun scrollBy(delta: Int) { activeScroll().scrollBy(0, delta) }
    fun scrollTo(view: View) { view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true) }

    companion object {
        private var foreground: WeakReference<DikcizOperationsActivity>? = null
        fun current(): DikcizOperationsActivity? = foreground?.get()?.takeUnless { it.isFinishing || it.isDestroyed }
        private val FILTERS = listOf("Actions", "All activity", "Problems", "Running")
        const val REFRESH_MILLISECONDS = 3000L
        const val PAGE_SIZE = 100
        private const val HISTORY_PAGE_SIZE = 30
        const val MEBIBYTE = 1048576L
        const val KIBIBYTE = 1024L
        const val BACKGROUND = 0xFF111310.toInt()
        const val SURFACE = 0xFF20241E.toInt()
        const val TEXT = 0xFFF1F3EB.toInt()
        const val MUTED = 0xFFB3BBAA.toInt()
        const val ACCENT = 0xFFF6C85F.toInt()
        const val ERROR = 0xFFFFA6A0.toInt()
    }
}
