package org.fossify.home.dikciz

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Build
import android.text.InputType
import android.text.TextUtils
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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.PopupMenu
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
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
import org.fossify.home.R

internal const val EXTRA_UNREAD_CHATS = "showUnreadChats"

internal class DikcizChatActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var runtime: DikcizChatRuntime
    private lateinit var root: FrameLayout
    private lateinit var panel: LinearLayout
    private lateinit var conversationPane: LinearLayout
    private lateinit var history: DikcizChatHistoryView
    private lateinit var optionsPane: LinearLayout
    private lateinit var conversationOptions: LinearLayout
    private lateinit var historyButton: Button
    private lateinit var optionsButton: ImageButton
    private lateinit var sheetHandle: ImageButton
    private lateinit var messages: LinearLayout
    private lateinit var scroll: ChatScrollView
    private lateinit var composer: EditText
    private lateinit var latest: Button
    private lateinit var title: TextView
    private lateinit var modelLabel: TextView
    private lateinit var status: TextView
    private lateinit var statusRow: LinearLayout
    private lateinit var sendButton: Button
    private lateinit var stop: Button
    private lateinit var emptyState: ScrollView
    private lateinit var emptyTitle: TextView
    private lateinit var emptyMessage: TextView
    private lateinit var emptyAction: Button
    private lateinit var composerRow: LinearLayout
    private lateinit var markwon: Markwon
    private lateinit var modelControls: DikcizChatModelControls
    private var pane = Pane.CONVERSATION
    private var keyboardVisible = false
    private var hasModel = false
    private var needsInitialConversation = false
    private var screenResumed = false
    private var systemBackCallback: OnBackInvokedCallback? = null
    private var loadingDraft = false
    private val drafts by lazy { getSharedPreferences(DIKCIZ_CHAT_DRAFTS, Context.MODE_PRIVATE) }
    private val pendingSends = mutableSetOf<String>()
    private val rendered = mutableMapOf<Long, MessageView>()
    private var conversationID: String? = null
    private var fullScreen = false
    private var sheetProgress = 0f
    private var sheetAnimation: ValueAnimator? = null
    private var followLatest = true
    private var rendering = false
    private var renderAgain = false
    private val observer: () -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = DikcizChatRuntime.get(this)
        markwon = Markwon.builder(this).usePlugin(TablePlugin.create(this)).usePlugin(StrikethroughPlugin.create()).build()
        conversationID = savedInstanceState?.getString("conversationId") ?: intent.getStringExtra("conversationId")
        needsInitialConversation = savedInstanceState == null && conversationID == null
        pane = Pane.entries.firstOrNull { it.name == savedInstanceState?.getString("chatPane") } ?: if (intent.getBooleanExtra(EXTRA_UNREAD_CHATS, false)) Pane.HISTORY else Pane.CONVERSATION
        needsInitialConversation = needsInitialConversation && pane == Pane.CONVERSATION
        fullScreen = savedInstanceState?.getBoolean("fullScreen") ?: intent.getBooleanExtra("fullScreen", false)
        sheetProgress = if (fullScreen) 1f else 0f
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        root = FrameLayout(this).apply { setBackgroundColor(DIM); setOnClickListener { finish() } }
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), dp(24).toFloat(), 0f, 0f, 0f, 0f)
                setColor(BACKGROUND)
                setStroke(dp(1), PANEL_EDGE)
            }
            setPadding(dp(12), 0, dp(12), dp(10))
            isClickable = true
            isFocusableInTouchMode = true
        }
        root.addView(panel, FrameLayout.LayoutParams(-1, -1, Gravity.BOTTOM))
        sheetHandle = createSheetHandle()
        panel.addView(sheetHandle, LinearLayout.LayoutParams(dp(72), dp(48)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        modelControls = DikcizChatModelControls(this, { conversationID }) { request, completed ->
            command(request, failed = { message ->
                if (pane == Pane.OPTIONS) showCommandError(message)
            }, completed = completed)
        }
        conversationPane = createConversationPane()
        optionsPane = createOptionsPane()
        history = DikcizChatHistoryView(
            context = this,
            onBack = ::returnToChat,
            onNew = ::newConversation,
            onRetry = ::render,
            onOpen = ::selectConversation,
            onManage = ::manageConversation,
        ).apply { setQuery(savedInstanceState?.getString("historyQuery").orEmpty()) }
        for (page in listOf(conversationPane, history, optionsPane)) {
            panel.addView(page, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            resize()
            insets
        }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> resize() }
        scroll.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> scrollLatest() }
        showPane()
        panel.requestFocus()
    }

    private fun createConversationPane() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val header = LinearLayout(this@DikcizChatActivity).apply { gravity = Gravity.CENTER_VERTICAL }
        historyButton = button("Chats", "Chats") { openPane(Pane.HISTORY) }.apply {
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
        }
        header.addView(historyButton, LinearLayout.LayoutParams(-2, dp(48)).apply { marginEnd = dp(10) })
        val heading = LinearLayout(this@DikcizChatActivity).apply {
            orientation = LinearLayout.VERTICAL
            title = textView().apply {
                text = "Dikciz"
                textSize = 17f
                typeface = Typeface.DEFAULT_BOLD
                setSingleLine()
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, 0, 0, 0)
                ViewCompat.setAccessibilityHeading(this, true)
            }
            modelLabel = textView().apply {
                visibility = View.GONE
                textSize = 12f
                setTextColor(MUTED)
                setSingleLine()
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, 0, 0, 0)
            }
            addView(title)
            addView(modelLabel)
        }
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        optionsButton = iconButton(R.drawable.dikciz_icon_overflow, "Chat options") { openPane(Pane.OPTIONS) }
        header.addView(optionsButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(iconButton(R.drawable.dikciz_icon_close, "Hide Dikciz chat") { finish() },
            LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(header)
        addView(divider(), LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(6) })
        val content = FrameLayout(this@DikcizChatActivity)
        scroll = ChatScrollView(this@DikcizChatActivity).apply {
            isFillViewport = true
            contentDescription = "Chat messages"
            setPadding(0, dp(8), 0, dp(8))
            clipToPadding = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            onReadingOlder = { followLatest = false; updateLatest() }
            onUserScroll = {
                if (height + scrollY >= getChildAt(0).height - dp(32)) followLatest = true
                updateLatest()
            }
        }
        messages = LinearLayout(this@DikcizChatActivity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(messages, FrameLayout.LayoutParams(-1, -2))
        content.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        emptyState = createEmptyState()
        content.addView(emptyState, FrameLayout.LayoutParams(-1, -1))
        latest = button("Latest ↓", "Jump to latest message") { followLatest = true; scrollLatest(); updateLatest() }
        content.addView(latest, FrameLayout.LayoutParams(-2, dp(48), Gravity.BOTTOM or Gravity.END))
        addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        status = textView().apply {
            textSize = 13f
            setTextColor(MUTED)
            contentDescription = "Chat agent status"
        }
        stop = button("Stop", "Stop agent turn") {
            conversationID?.let { command(JSONObject().put("action", "stop").put("conversationId", it)) }
        }.apply { setTextColor(ERROR) }
        statusRow = LinearLayout(this@DikcizChatActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(status, LinearLayout.LayoutParams(0, -2, 1f))
            addView(stop, LinearLayout.LayoutParams(-2, dp(48)))
            visibility = View.GONE
        }
        addView(statusRow)
        composerRow = LinearLayout(this@DikcizChatActivity).apply {
            gravity = Gravity.BOTTOM
            visibility = View.GONE
        }
        composer = EditText(this@DikcizChatActivity).apply {
            hint = "Message Dikciz…"
            textSize = 16f
            minHeight = dp(48)
            contentDescription = "Chat message"
            setTextColor(FOREGROUND); setHintTextColor(MUTED)
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(ASSISTANT_BUBBLE) }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = COMPOSER_LINES
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
        }
        composerRow.addView(composer, LinearLayout.LayoutParams(0, -2, 1f))
        composer.doAfterTextChanged { text ->
            if (loadingDraft) return@doAfterTextChanged
            val id = conversationID ?: return@doAfterTextChanged
            val value = text.toString()
            if (value.isEmpty()) drafts.edit().remove(id).apply()
            else drafts.edit().putString(id, UUID.randomUUID().toString() + "\n" + value).apply()
            composer.error = null
            updateSendButton()
        }
        sendButton = button("↑", "Send chat message") { send() }.apply {
            isEnabled = false
            DikcizUiStyle.button(this, primary = true)
            setPadding(0, 0, 0, 0)
            textSize = 24f
            val states = arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf())
            setTextColor(ColorStateList(states, intArrayOf(MUTED, BACKGROUND)))
            background = RippleDrawable(ColorStateList.valueOf(BUTTON_RIPPLE),
                GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(ColorStateList(states, intArrayOf(ASSISTANT_BUBBLE, ACCENT)))
                }, null)
        }
        composerRow.addView(sendButton, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) })
        addView(composerRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
    }

    private fun createEmptyState() = ScrollView(this).apply {
        isFillViewport = true
        visibility = View.GONE
        contentDescription = "Empty chat"
        addView(LinearLayout(this@DikcizChatActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            addView(ImageView(this@DikcizChatActivity).apply {
                setImageResource(R.drawable.dikciz_chat_cheese)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(36), dp(36)))
            emptyTitle = textView().apply {
                textSize = 20f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            emptyMessage = textView().apply {
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(MUTED)
            }
            emptyAction = button("New chat", "New chat") { newConversation() }.apply {
                DikcizUiStyle.button(this, primary = true)
            }
            addView(emptyTitle, LinearLayout.LayoutParams(-1, -2))
            addView(emptyMessage, LinearLayout.LayoutParams(-1, -2))
            addView(emptyAction, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        })
    }

    private fun createOptionsPane() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        ViewCompat.setAccessibilityPaneTitle(this, "Chat options")
        val header = LinearLayout(this@DikcizChatActivity).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(button("‹", "Back to chat", ::returnToChat).apply { textSize = 26f },
            LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(10) })
        header.addView(textView().apply {
            text = "Chat options"
            typeface = Typeface.DEFAULT_BOLD
            textSize = 20f
            ViewCompat.setAccessibilityHeading(this, true)
        })
        addView(header)
        addView(divider(), LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(6) })
        addView(ScrollView(this@DikcizChatActivity).apply {
            contentDescription = "Chat options list"
            addView(LinearLayout(this@DikcizChatActivity).apply {
                orientation = LinearLayout.VERTICAL
                conversationOptions = LinearLayout(this@DikcizChatActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(optionHeading("Model"))
                    addView(modelControls.view)
                    addView(optionHeading("Quick tasks"))
                    addView(optionButton("Explain this screen") {
                        startTask(DikcizChatTasks.EXPLAIN_SCREEN,
                            composer.text.toString().ifBlank { DikcizChatTasks.DEFAULT_SCREEN_QUESTION })
                    })
                    addView(optionButton("Make me a widget", ::widgetTask))
                    addView(optionButton("Run a routine", ::chooseRoutine))
                    addView(optionHeading("Conversation"))
                    addView(optionButton("Change provider", ::chooseConversationProvider))
                    addView(optionButton("Event rules", ::editEvents))
                }
                addView(conversationOptions)
                addView(optionHeading("Connections"))
                addView(optionButton("Provider profiles", ::chooseProfile).apply {
                    contentDescription = "Chat provider settings"
                })
            })
        }, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun optionHeading(label: String) = textView().apply {
        text = label
        textSize = 13f
        setTextColor(MUTED)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(12), dp(14), dp(12), dp(4))
        ViewCompat.setAccessibilityHeading(this, true)
    }

    private fun optionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        DikcizUiStyle.button(this)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun divider() = View(this).apply { setBackgroundColor(PANEL_EDGE) }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_UNREAD_CHATS, false)) openPane(Pane.HISTORY)
        intent.getStringExtra("conversationId")?.takeIf(String::isNotBlank)?.let { selectConversation(it) }
        settleSheet(intent.getBooleanExtra("fullScreen", fullScreen), animate = false)
    }

    override fun onResume() {
        super.onResume()
        screenResumed = true
        runtime.observe(observer)
        if (pane == Pane.CONVERSATION) {
            val selected = runtime.attachActivity(this, conversationID)
            if (selected != conversationID) changeConversation(selected)
            else loadDraft()
        } else {
            runtime.attachActivity(this, null)
            loadDraft()
        }
        if (needsInitialConversation) {
            needsInitialConversation = false
            scope.launch {
                try {
                    val initial = withContext(Dispatchers.IO) {
                        runtime.store.conversations().firstOrNull()?.getString("id")
                    }
                    if (initial != null && screenResumed && conversationID == null && pane == Pane.CONVERSATION) {
                        selectConversation(initial)
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    if (screenResumed && pane == Pane.CONVERSATION) showCommandError("Couldn't load saved conversations.")
                }
            }
        }
        render()
    }

    override fun onPause() {
        screenResumed = false
        runtime.unobserve(observer)
        runtime.detachActivity(this)
        super.onPause()
    }

    override fun onDestroy() {
        sheetAnimation?.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            systemBackCallback?.let(onBackInvokedDispatcher::unregisterOnBackInvokedCallback)
        }
        scope.cancel()
        if (::modelControls.isInitialized) modelControls.invalidate()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("conversationId", conversationID)
        outState.putBoolean("fullScreen", fullScreen)
        outState.putString("chatPane", pane.name)
        outState.putString("historyQuery", history.query())
        super.onSaveInstanceState(outState)
    }

    @SuppressLint("GestureBackNavigation")
    @Deprecated("Android 13 and newer use the system Back callback.")
    override fun onBackPressed() {
        if (pane != Pane.CONVERSATION) {
            returnToChat()
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    private fun openPane(destination: Pane) {
        if (pane == destination) return
        dismissKeyboard()
        pane = destination
        if (destination == Pane.HISTORY) history.setQuery("")
        runtime.attachActivity(this, null)
        showPane()
        if (destination == Pane.HISTORY) history.requestFocus() else panel.requestFocus()
        render()
    }

    private fun returnToChat() {
        val previous = pane
        if (previous == Pane.HISTORY && conversationID == null && intent.getBooleanExtra(EXTRA_UNREAD_CHATS, false)) {
            finish()
            return
        }
        dismissKeyboard()
        pane = Pane.CONVERSATION
        val selected = runtime.attachActivity(this, conversationID)
        if (selected != conversationID) changeConversation(selected) else loadDraft()
        showPane()
        if (previous == Pane.HISTORY) historyButton.requestFocus() else optionsButton.requestFocus()
        render()
    }

    private fun dismissKeyboard() {
        WindowCompat.getInsetsController(window, root).hide(WindowInsetsCompat.Type.ime())
        currentFocus?.clearFocus()
    }

    private fun showPane() {
        if (pane != Pane.OPTIONS) modelControls.dismissPicker()
        conversationPane.visibility = if (pane == Pane.CONVERSATION) View.VISIBLE else View.GONE
        history.visibility = if (pane == Pane.HISTORY) View.VISIBLE else View.GONE
        optionsPane.visibility = if (pane == Pane.OPTIONS) View.VISIBLE else View.GONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            systemBackCallback?.let(onBackInvokedDispatcher::unregisterOnBackInvokedCallback)
            systemBackCallback = null
            if (pane != Pane.CONVERSATION) {
                systemBackCallback = OnBackInvokedCallback(::returnToChat).also {
                    onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, it)
                }
            }
        }
        resize()
    }

    private fun createSheetHandle(): ImageButton {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var startX = 0f
        var startY = 0f
        var startProgress = 0f
        var verticalDrag = false
        var ignored = false
        return ImageButton(this).apply {
            contentDescription = handleDescription()
            scaleType = ImageView.ScaleType.CENTER
            setPadding(0, 0, 0, 0)
            background = RippleDrawable(
                ColorStateList.valueOf(BUTTON_RIPPLE),
                null,
                GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.WHITE) },
            )
            setImageDrawable(GradientDrawable().apply {
                setSize(dp(36), dp(4))
                cornerRadius = dp(2).toFloat()
                setColor(MUTED)
            })
            setOnClickListener { settleSheet(!fullScreen) }
            setOnTouchListener { handle, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        sheetAnimation?.cancel()
                        startX = event.rawX
                        startY = event.rawY
                        startProgress = sheetProgress
                        verticalDrag = false
                        ignored = false
                        handle.isPressed = true
                    }
                    MotionEvent.ACTION_MOVE -> if (!ignored) {
                        val horizontal = abs(event.rawX - startX)
                        val vertical = startY - event.rawY
                        if (!verticalDrag && maxOf(horizontal, abs(vertical)) > touchSlop) {
                            verticalDrag = abs(vertical) > horizontal
                            ignored = !verticalDrag
                            handle.isPressed = false
                        }
                        val travel = availableHeight() - compactHeight(availableHeight())
                        if (verticalDrag && travel > 0) {
                            sheetProgress = (startProgress + vertical / travel).coerceIn(0f, 1f)
                            resize()
                        }
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        ignored = true
                        handle.isPressed = false
                        settleSheet(fullScreen)
                    }
                    MotionEvent.ACTION_UP -> {
                        handle.isPressed = false
                        if (ignored) {
                            settleSheet(fullScreen)
                        } else if (verticalDrag) {
                            val distance = startY - event.rawY
                            settleSheet(if (abs(distance) >= dp(SHEET_SWIPE_DISTANCE_DP)) distance > 0 else fullScreen)
                        } else {
                            handle.performClick()
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        handle.isPressed = false
                        settleSheet(fullScreen)
                    }
                }
                true
            }
        }
    }

    private fun handleDescription() = if (fullScreen) "Collapse chat" else "Expand chat"

    private fun settleSheet(expanded: Boolean, animate: Boolean = true) {
        sheetAnimation?.cancel()
        fullScreen = expanded
        sheetHandle.contentDescription = handleDescription()
        val target = if (expanded) 1f else 0f
        if (!animate || sheetProgress == target) {
            sheetProgress = target
            resize()
            return
        }
        runtime.setWindowMode(fullScreen)
        sheetAnimation = ValueAnimator.ofFloat(sheetProgress, target).apply {
            duration = SHEET_SETTLE_MILLISECONDS
            addUpdateListener {
                sheetProgress = it.animatedValue as Float
                resize()
            }
            start()
        }
    }

    private fun availableHeight() = (root.height - root.paddingTop - root.paddingBottom).coerceAtLeast(0)

    private fun compactHeight(available: Int) = maxOf(available / 2, minOf(dp(360), available))

    private fun resize() {
        if (root.height == 0) return
        runtime.setWindowMode(fullScreen)
        val available = availableHeight()
        val compact = compactHeight(available)
        val hasSecondaryPane = pane != Pane.CONVERSATION
        val height = if (hasSecondaryPane || keyboardVisible) available else
            compact + ((available - compact) * sheetProgress).roundToInt()
        sheetHandle.visibility = if (hasSecondaryPane || available < dp(MINIMUM_HANDLE_SPACE_DP)) View.GONE else View.VISIBLE
        val composerLines = if (available < dp(COMPACT_COMPOSER_SPACE_DP)) COMPACT_COMPOSER_LINES else COMPOSER_LINES
        if (composer.maxLines != composerLines) composer.maxLines = composerLines
        val parameters = panel.layoutParams as FrameLayout.LayoutParams
        if (parameters.height != height) {
            parameters.height = height
            panel.layoutParams = parameters
        }
    }

    private fun render() {
        if (isFinishing) return
        if (rendering) { renderAgain = true; return }
        rendering = true
        scope.launch {
            try {
                val id = conversationID
                val currentPane = pane
                if (currentPane == Pane.HISTORY) {
                    val entries = withContext(Dispatchers.IO) {
                        runtime.store.conversations().map { DikcizChatHistoryView.Entry.from(it) }
                    }
                    if (pane == currentPane) history.update(entries, conversationID, runtime.progress().conversationID)
                    return@launch
                }
                val state = withContext(Dispatchers.IO) {
                    val conversation = id?.let { runtime.store.conversation(it) }
                    ChatPaneData(
                        conversation = conversation,
                        messages = if (id != null && currentPane == Pane.CONVERSATION) runtime.store.displayMessages(id) else emptyList(),
                        settings = id?.let { runtime.store.settings(it).getJSONObject("settings") },
                        hasProfiles = id != null || runtime.store.profiles().length() > 0,
                    )
                }
                if (id != conversationID || pane != currentPane) return@launch
                val model = state.settings?.optString("model").orEmpty()
                val readOnly = state.settings?.optBoolean(DikcizChatSettings.KEY_READ_ONLY) == true
                hasModel = model.isNotBlank()
                title.text = state.conversation?.getString("title") ?: "Dikciz"
                modelLabel.text = when {
                    id == null -> ""
                    model.isBlank() -> "Choose a model"
                    readOnly -> "Read-only · $model"
                    else -> model
                }
                modelLabel.visibility = if (id == null) View.GONE else View.VISIBLE
                modelControls.update(id, state.conversation?.getString("profile_id"), state.settings)
                conversationOptions.visibility = if (id == null) View.GONE else View.VISIBLE
                if (currentPane == Pane.OPTIONS) return@launch
                val progress = runtime.progress()
                val working = id != null && progress.conversationID == id
                status.text = if (working) progress.text else ""
                statusRow.visibility = if (working) View.VISIBLE else View.GONE
                stop.visibility = if (working) View.VISIBLE else View.GONE
                emptyState.visibility = if (state.messages.isEmpty()) View.VISIBLE else View.GONE
                scroll.visibility = if (state.messages.isEmpty()) View.INVISIBLE else View.VISIBLE
                updateEmptyState(id, state.hasProfiles)
                composerRow.visibility = if (id == null) View.GONE else View.VISIBLE
                updateSendButton()
                val anchor = (0 until messages.childCount).map(messages::getChildAt).firstOrNull { it.bottom > scroll.scrollY }
                val anchorOffset = anchor?.let { scroll.scrollY - it.top } ?: 0
                for (row in state.messages) {
                    val rowID = row.getLong("id")
                    val view = rendered.getOrPut(rowID) { MessageView().also { messages.addView(it.container) } }
                    view.bind(row)
                }
                scroll.post {
                    if (conversationID != id || pane != Pane.CONVERSATION) return@post
                    if (followLatest) scrollLatest()
                    else if (!scroll.userDragging && anchor != null && anchor.parent === messages) {
                        scroll.scrollTo(0, anchor.top + anchorOffset)
                    }
                    updateLatest()
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (pane == Pane.HISTORY) {
                    history.showError()
                } else {
                    status.text = "Chat could not be loaded: ${exception.javaClass.simpleName}"
                    statusRow.visibility = View.VISIBLE
                }
            } finally {
                rendering = false
                if (renderAgain) { renderAgain = false; render() }
            }
        }
    }

    private fun updateEmptyState(id: String?, hasProfiles: Boolean) {
        val action = when {
            id == null && !hasProfiles -> "Add provider"
            id == null -> "New chat"
            !hasModel -> "Choose a model"
            else -> null
        }
        emptyTitle.text = when (action) {
            "Add provider" -> "Connect a provider"
            "New chat" -> "Start a conversation"
            "Choose a model" -> "Choose a model"
            else -> "What's on your mind?"
        }
        emptyMessage.text = when (action) {
            "Add provider" -> "Connect an OpenAI or Anthropic compatible API to start chatting."
            "New chat" -> "A new chat starts with its own history and draft."
            "Choose a model" -> "Pick a model for this conversation. You can draft a message now."
            else -> "Ask a question or give Dikciz a task."
        }
        emptyAction.visibility = if (action == null) View.GONE else View.VISIBLE
        emptyAction.text = action.orEmpty()
        emptyAction.contentDescription = action
        emptyAction.setOnClickListener {
            if (id != null) openPane(Pane.OPTIONS) else newConversation()
        }
    }

    private fun updateSendButton() {
        val enabled = hasModel && conversationID != null && conversationID !in pendingSends && !composer.text.isBlank()
        sendButton.isEnabled = enabled
    }

    private fun send() {
        val text = composer.text.toString()
        if (text.isBlank() || !hasModel) return
        val id = conversationID ?: return
        if (!pendingSends.add(id)) return
        updateSendButton()
        val submitted = drafts.getString(id, null)
        val requestKey = submitted?.substringBefore('\n') ?: UUID.randomUUID().toString()
        followLatest = true
        command(JSONObject().put("action", "send").put("conversationId", id).put("text", text)
            .put("requestKey", requestKey), failed = {
                pendingSends.remove(id)
                if (conversationID == id) composer.error = it
                updateSendButton()
            }) {
            pendingSends.remove(id)
            if (drafts.getString(id, null) == submitted) {
                drafts.edit().remove(id).apply()
                if (conversationID == id) loadDraft()
            }
            updateSendButton()
        }
    }

    private fun loadDraft() {
        loadingDraft = true
        val id = conversationID
        val record = id?.let { drafts.getString(it, null) }
        val text = if (id != null && record != null && runtime.store.request(id, record.substringBefore('\n')) != null) {
            drafts.edit().remove(id).apply()
            ""
        } else record?.substringAfter('\n').orEmpty()
        composer.setText(text)
        composer.setSelection(text.length)
        composer.error = null
        loadingDraft = false
        updateSendButton()
    }

    private fun widgetTask() {
        val id = conversationID ?: return
        val input = EditText(this).apply {
            hint = "Describe what your widget should do"
            contentDescription = "Widget request"
            setText(composer.text)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val dialog = AlertDialog.Builder(this).setTitle("Make me a widget").setView(input)
            .setPositiveButton("Create", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (input.text.isBlank()) { input.error = "Describe the widget"; return@setOnClickListener }
                if (conversationID != id) {
                    input.error = "The conversation changed. Reopen this task."
                    return@setOnClickListener
                }
                val submit = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                submit.isEnabled = false
                startTask(DikcizChatTasks.WIDGET, input.text.toString(), failed = { error ->
                    input.error = error
                    submit.isEnabled = true
                }) {
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
        DikcizUiStyle.dialog(dialog)
    }

    private fun startTask(
        task: String,
        text: String,
        failed: ((String) -> Unit)? = null,
        completed: (JSONObject) -> Unit = {},
    ) {
        val id = conversationID ?: run { newConversation(); return }
        followLatest = true
        command(JSONObject().put("action", DikcizChatTasks.ACTION).put("task", task)
            .put("conversationId", id).put("text", text).put("requestKey", UUID.randomUUID().toString()),
            failed) { result ->
            completed(result)
            if (task != DikcizChatTasks.EXPLAIN_SCREEN && id == conversationID && pane == Pane.OPTIONS) returnToChat()
        }
    }

    private fun command(
        request: JSONObject,
        failed: ((String) -> Unit)? = null,
        completed: (JSONObject) -> Unit = {},
    ) {
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
                val message = exception.message ?: "Operation failed"
                (failed ?: ::showCommandError)(message)
            }
        }
    }

    private fun showCommandError(message: String) {
        AlertDialog.Builder(this).setTitle("Dikciz chat").setMessage(message)
            .setPositiveButton("Close", null).show().also(DikcizUiStyle::dialog)
    }

    private fun manageConversation(entry: DikcizChatHistoryView.Entry, anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Rename").setOnMenuItemClickListener {
                renameConversation(entry)
                true
            }
            menu.add("Delete").setOnMenuItemClickListener {
                confirmDeleteConversation(entry)
                true
            }
            show()
        }
    }

    private fun renameConversation(entry: DikcizChatHistoryView.Entry) {
        val input = EditText(this).apply {
            setText(entry.title)
            contentDescription = "Conversation title"
            isSingleLine = true
            setSelectAllOnFocus(true)
        }
        val dialog = AlertDialog.Builder(this).setTitle("Rename chat").setView(input)
            .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            save.setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    input.error = "Enter a title"
                    return@setOnClickListener
                }
                save.isEnabled = false
                command(JSONObject().put("action", "rename").put("conversationId", entry.id).put("title", name),
                    failed = {
                        input.error = it
                        save.isEnabled = true
                    }) { dialog.dismiss() }
            }
        }
        dialog.show()
        DikcizUiStyle.dialog(dialog)
    }

    private fun confirmDeleteConversation(entry: DikcizChatHistoryView.Entry) {
        AlertDialog.Builder(this).setTitle("Delete \"${entry.title}\"?")
            .setMessage("This permanently removes its messages, draft and event rules.")
            .setPositiveButton("Delete") { _, _ ->
                command(JSONObject().put("action", "delete").put("conversationId", entry.id))
            }.setNegativeButton("Cancel", null).show().also(DikcizUiStyle::dialog)
    }

    private fun chooseConversationProvider() {
        val id = conversationID ?: return
        val profiles = runtime.store.profiles()
        AlertDialog.Builder(this).setTitle("Conversation provider")
            .setItems((0 until profiles.length()).map { profiles.getJSONObject(it).getString("name") }.toTypedArray()) { _, index ->
                reviewProviderChange(id, profiles.getJSONObject(index))
            }.show().also(DikcizUiStyle::dialog)
    }

    private fun reviewProviderChange(id: String, profile: JSONObject) {
        val profileID = profile.getString("id")
        val name = profile.getString("name")
        val host = android.net.Uri.parse(profile.optString("baseUrl")).host
        val destination = if (host.isNullOrBlank()) name else "$name ($host)"
        AlertDialog.Builder(this).setTitle("Use $destination?")
            .setMessage("Future requests can send eligible conversation history, attachments, and tool results to this provider. Start a new conversation to keep this history here.")
            .setPositiveButton("Switch provider") { _, _ ->
                command(JSONObject().put("action", "configure").put("conversationId", id).put("profileId", profileID))
            }
            .setNeutralButton("New conversation") { _, _ ->
                command(JSONObject().put("action", "create").put("profileId", profileID)) { selectConversation(it.getString("id")) }
            }
            .setNegativeButton("Cancel", null).show().also(DikcizUiStyle::dialog)
    }

    private fun chooseRoutine() {
        val routines = DikcizAssistantRoutines.routines
        AlertDialog.Builder(this).setTitle("Read-only routines")
            .setItems(routines.map { it.title + "\n" + it.description }.toTypedArray()) { _, index ->
                val routine = routines[index]
                AlertDialog.Builder(this).setTitle(routine.title)
                    .setMessage(routine.description + "\n\nThis turn can only read. Missing Android access stays explicit.")
                    .setPositiveButton("Run") { _, _ -> startTask(routine.id, "Run " + routine.title + ".") }
                    .setNegativeButton("Cancel", null).show().also(DikcizUiStyle::dialog)
            }.setNegativeButton("Close", null).show().also(DikcizUiStyle::dialog)
    }

    private fun selectConversation(id: String) {
        val previousPane = pane
        val selected = runtime.attachActivity(this, id)
        if (selected == null) {
            if (previousPane == Pane.CONVERSATION) runtime.attachActivity(this, conversationID)
            render()
            showCommandError("This conversation no longer exists.")
            return
        }
        dismissKeyboard()
        pane = Pane.CONVERSATION
        if (selected != conversationID) changeConversation(selected) else loadDraft()
        if (previousPane != Pane.CONVERSATION) showPane()
        panel.requestFocus()
        render()
    }

    internal fun onConversationDeleted(id: String) {
        if (conversationID != id) {
            if (pane == Pane.HISTORY) render()
            return
        }
        changeConversation(null)
        runtime.attachActivity(this, null)
        render()
    }

    private fun changeConversation(id: String?) {
        conversationID = id
        hasModel = false
        rendered.clear()
        messages.removeAllViews()
        followLatest = true
        scroll.scrollTo(0, 0)
        updateLatest()
        loadDraft()
    }

    private fun newConversation() {
        val profiles = runtime.store.profiles()
        if (profiles.length() == 0) { editProfile(null); return }
        AlertDialog.Builder(this).setTitle("Provider for new conversation")
            .setItems((0 until profiles.length()).map { profiles.getJSONObject(it).getString("name") }.toTypedArray()) { _, index ->
                command(JSONObject().put("action", "create").put("profileId", profiles.getJSONObject(index).getString("id"))) { selectConversation(it.getString("id")) }
            }.show().also(DikcizUiStyle::dialog)
    }

    private fun chooseProfile() {
        val profiles = runtime.store.profiles()
        AlertDialog.Builder(this).setTitle("Provider profiles")
            .setItems((0 until profiles.length()).map { profiles.getJSONObject(it).getString("name") }.toTypedArray()) { _, index -> editProfile(profiles.getJSONObject(index)) }
            .setPositiveButton("Add provider") { _, _ -> editProfile(null) }.setNegativeButton("Close", null).show().also(DikcizUiStyle::dialog)
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
                            .setItems(available.toTypedArray()) { _, index -> setText(available[index]) }.show().also(DikcizUiStyle::dialog)
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
        DikcizUiStyle.dialog(dialog)
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
        val dialog = AlertDialog.Builder(this).setTitle("Event rules")
            .setMessage("Each rule has subscription and instructions. Example: [{\"subscription\":{\"event\":\"example.check\",\"minimumIntervalMilliseconds\":60000},\"instructions\":\"Report the event details\"}]")
            .setView(editor).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            save.setOnClickListener {
                val rules = try {
                    JSONArray(editor.text.toString())
                } catch (_: org.json.JSONException) {
                    editor.error = "Event rules must be a JSON array"
                    return@setOnClickListener
                }
                save.isEnabled = false
                command(JSONObject().put("action", "subscribe").put("conversationId", id).put("rules", rules),
                    failed = {
                        editor.error = it
                        save.isEnabled = true
                    }) { dialog.dismiss() }
            }
        }
        dialog.show()
        DikcizUiStyle.dialog(dialog)
    }

    private fun updateLatest() { latest.visibility = if (followLatest) View.GONE else View.VISIBLE }
    private fun scrollLatest() {
        if (!followLatest || pane != Pane.CONVERSATION) return
        val id = conversationID
        scroll.post {
            if (id == conversationID && pane == Pane.CONVERSATION && followLatest && !scroll.userDragging) {
                scroll.scrollTo(0, messages.height)
            }
        }
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun textView() = TextView(this).apply { setTextColor(FOREGROUND); textSize = 16f; setLineSpacing(dp(3).toFloat(), 1f); setPadding(0, dp(4), 0, dp(4)) }
    private fun button(label: String, description: String, action: () -> Unit) = Button(this).apply {
        text = label
        contentDescription = description
        minWidth = dp(48)
        minimumWidth = dp(48)
        isAllCaps = false
        minimumHeight = dp(48)
        setSingleLine()
        ellipsize = TextUtils.TruncateAt.END
        backgroundTintList = null
        background = RippleDrawable(ColorStateList.valueOf(BUTTON_RIPPLE),
            GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(ASSISTANT_BUBBLE) }, null)
        setPadding(dp(8), 0, dp(8), 0); textSize = 12f; setTextColor(ACCENT)
        setOnClickListener { action() }
    }

    private fun iconButton(resource: Int, description: String, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(resource)
        contentDescription = description
        background = RippleDrawable(ColorStateList.valueOf(BUTTON_RIPPLE), null,
            GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.WHITE) })
        setOnClickListener { action() }
    }

    private inner class MessageView {
        val container = LinearLayout(this@DikcizChatActivity).apply { orientation = LinearLayout.VERTICAL }
        private val label = textView().apply { textSize = 12f; setTextColor(MUTED); container.addView(this) }
        private val blocks = mutableListOf<TextView>()
        private var previous = ""
        private var ownerStyle: Boolean? = null
        fun bind(row: JSONObject) {
            val encoded = row.getString("blocks") + row.getString("state")
            if (previous == encoded) return
            previous = encoded
            val entries = JSONArray(row.getString("blocks"))
            val isOwner = row.getString("role") == "user"
            val role = if (isOwner && entries.optJSONObject(0)?.optString("source") == "event") "Event" else if (isOwner) "You" else if (row.getString("role") == "tool") "Phone tool" else "Dikciz"
            val state = row.getString("state")
            label.text = if (state == "complete") role else "$role · $state"
            if (ownerStyle != isOwner) {
                ownerStyle = isOwner
                container.background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(if (isOwner) OWNER_BUBBLE else Color.TRANSPARENT)
                }
                val horizontal = dp(if (isOwner) 12 else 4)
                container.setPaddingRelative(horizontal, dp(6), horizontal, dp(6))
                container.layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    marginStart = if (isOwner) dp(32) else 0
                    topMargin = dp(4)
                    bottomMargin = dp(4)
                }
            }
            while (blocks.size > entries.length()) container.removeView(blocks.removeAt(blocks.lastIndex))
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
                    view.typeface = Typeface.MONOSPACE
                    view.textSize = 13f
                    view.setTextColor(MUTED)
                    view.minimumHeight = dp(48)
                    view.gravity = Gravity.CENTER_VERTICAL
                    val label = if (kind == "reasoning") "Reasoning" else if (kind == "toolResult") {
                        "Result · " + entry.optString("name", "Phone tool").removePrefix("dikciz_").replace('_', ' ')
                    } else {
                        val tool = if (entry.optString("text").startsWith("{")) {
                            try { JSONObject(text).optString("name", "Phone tool") } catch (_: org.json.JSONException) { "Preparing tool" }
                        } else "Preparing tool"
                        "Tool · " + tool.removePrefix("dikciz_").replace('_', ' ')
                    }
                    val expanded = view.tag == true
                    view.text = if (expanded) "$label\n$text" else "$label ▸"
                    ViewCompat.setStateDescription(view, if (expanded) "Expanded" else "Collapsed")
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

    private data class ChatPaneData(
        val conversation: JSONObject?,
        val messages: List<JSONObject>,
        val settings: JSONObject?,
        val hasProfiles: Boolean,
    )

    private enum class Pane { CONVERSATION, HISTORY, OPTIONS }

    private companion object {
        val PROVIDER_TEMPLATES = listOf(
            ProviderTemplate("OpenAI", "openai", "https://api.openai.com/v1"),
            ProviderTemplate("Anthropic", "anthropic", "https://api.anthropic.com/v1"),
            ProviderTemplate("Custom", null, null),
        )
        const val PANEL_EDGE = 0xFF343430.toInt()
        const val BACKGROUND = 0xFF151614.toInt()
        const val ACCENT = 0xFFF6C85F.toInt()
        const val ERROR = 0xFFFF9696.toInt()
        const val DIM = 0x55000000
        const val OWNER_BUBBLE = 0xFF333126.toInt()
        const val ASSISTANT_BUBBLE = 0xFF22231F.toInt()
        const val BUTTON_RIPPLE = 0x44F6C85F
        const val SHEET_SWIPE_DISTANCE_DP = 28
        const val SHEET_SETTLE_MILLISECONDS = 180L
        const val MINIMUM_HANDLE_SPACE_DP = 240
        const val COMPACT_COMPOSER_SPACE_DP = 360
        const val COMPOSER_LINES = 4
        const val COMPACT_COMPOSER_LINES = 2
        val SETTING_CHOICES = mapOf(
            "protocol" to arrayOf("openai", "anthropic"),
        )
    }
}
