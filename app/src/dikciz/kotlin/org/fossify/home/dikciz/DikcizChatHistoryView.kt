package org.fossify.home.dikciz

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged
import org.fossify.home.R
import org.json.JSONObject

internal class DikcizChatHistoryView(
    context: Context,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onRetry: () -> Unit,
    private val onOpen: (String) -> Unit,
    private val onManage: (Entry, View) -> Unit,
) : LinearLayout(context) {
    data class Entry(
        val id: String,
        val title: String,
        val updated: Long,
        val unread: Int,
    ) {
        companion object {
            fun from(record: JSONObject) = Entry(
                record.getString("id"),
                record.getString("title").ifBlank { "Untitled chat" },
                record.getLong("updated"),
                record.getInt("unread"),
            )
        }
    }

    private val search = EditText(context).apply {
        hint = "Search chats"
        contentDescription = "Search chats"
        isSingleLine = true
        textSize = 16f
        minimumHeight = dp(48)
        setTextColor(DikcizUiStyle.FOREGROUND)
        setHintTextColor(DikcizUiStyle.MUTED)
        setPaddingRelative(dp(14), dp(6), dp(6), dp(6))
        background = null
    }
    private val clear = icon(R.drawable.dikciz_icon_clear, "Clear chat search") { search.text.clear() }
    private val empty = TextView(context).apply {
        setTextColor(DikcizUiStyle.MUTED)
        gravity = Gravity.CENTER
        textSize = 16f
        setPadding(dp(8), dp(12), dp(8), dp(12))
    }
    private val retry = Button(context).apply {
        text = "Retry"
        DikcizUiStyle.button(this)
        visibility = View.GONE
        setOnClickListener { onRetry() }
    }
    private val adapter = HistoryAdapter()
    private val list = ListView(context).apply {
        contentDescription = "Saved conversations"
        adapter = this@DikcizChatHistoryView.adapter
        divider = null
        itemsCanFocus = true
        savedViewPadding()
    }
    private var entries: List<Entry>? = null
    private var selectedID: String? = null
    private var runningID: String? = null
    private var failed = false

    init {
        orientation = VERTICAL
        isFocusableInTouchMode = true
        ViewCompat.setAccessibilityPaneTitle(this, "Conversations")
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(Button(context).apply {
            text = "‹"
            contentDescription = "Back to chat"
            DikcizUiStyle.button(this)
            textSize = 29f
            gravity = Gravity.CENTER
            setOnClickListener { onBack() }
        }, LayoutParams(dp(48), dp(48)))
        header.addView(TextView(context).apply {
            text = "Conversations"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(DikcizUiStyle.FOREGROUND)
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
            ViewCompat.setAccessibilityHeading(this, true)
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        header.addView(Button(context).apply {
            text = "New chat"
            DikcizUiStyle.button(this, primary = true)
            setOnClickListener { onNew() }
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val searchBar = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = surface(DikcizUiStyle.SURFACE, 12)
            addView(search, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(clear, LayoutParams(dp(48), dp(48)))
        }
        addView(searchBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
            bottomMargin = dp(8)
        })
        addView(FrameLayout(context).apply {
            addView(list, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER
                addView(empty, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                addView(retry)
            }, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        search.doAfterTextChanged { refresh() }
        refresh()
    }

    fun query(): String = search.text.toString()

    fun setQuery(value: String) {
        if (query() != value) search.setText(value)
    }

    fun update(rows: List<Entry>, selected: String?, running: String?) {
        if (!failed && entries == rows && selected == selectedID && running == runningID) return
        entries = rows
        selectedID = selected
        runningID = running
        failed = false
        refresh()
    }

    fun showError() {
        failed = true
        refresh()
    }

    private fun refresh() {
        val query = query().trim()
        val matches = entries.orEmpty().filter { it.title.contains(query, ignoreCase = true) }
        val needsEmpty = matches.isEmpty()
        list.visibility = if (needsEmpty) View.GONE else View.VISIBLE
        empty.visibility = if (needsEmpty) View.VISIBLE else View.GONE
        retry.visibility = if (failed && needsEmpty) View.VISIBLE else View.GONE
        clear.visibility = if (this.query().isEmpty()) View.INVISIBLE else View.VISIBLE
        empty.text = when {
            failed -> "Couldn't load conversations"
            entries == null -> "Loading conversations…"
            entries.isNullOrEmpty() -> "No saved conversations yet"
            else -> "No conversations match your search"
        }
        adapter.rows = matches.sortedByDescending { it.unread > 0 }
        adapter.notifyDataSetChanged()
    }

    private inner class HistoryAdapter : BaseAdapter() {
        var rows = emptyList<Entry>()
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            (convertView as? HistoryRow ?: HistoryRow()).apply { bind(getItem(position)) }
    }

    private inner class HistoryRow : LinearLayout(this@DikcizChatHistoryView.context) {
        private val title = TextView(context).apply {
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        private val details = TextView(context).apply {
            textSize = 13f
            setTextColor(DikcizUiStyle.MUTED)
        }
        private val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            isFocusable = true
            setPaddingRelative(dp(12), dp(8), dp(4), dp(8))
            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                addView(title)
                addView(details)
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        private val unreadBadge = TextView(context).apply {
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 14f
            gravity = Gravity.CENTER
            minimumWidth = dp(26)
            setPaddingRelative(dp(6), dp(2), dp(6), dp(2))
            background = surface(DikcizUiStyle.ACCENT, 14)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        private val overflow = icon(R.drawable.dikciz_icon_overflow, "Chat actions") {}

        init {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, dp(3))
            title.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            details.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            ViewCompat.setScreenReaderFocusable(row, true)
            addView(row, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            row.addView(unreadBadge)
            addView(overflow, LayoutParams(dp(48), dp(48)))
        }

        fun bind(entry: Entry) {
            val isCurrent = entry.id == selectedID
            val metadata = buildList {
                if (isCurrent) add("Current chat")
                if (entry.id == runningID) add("In progress")
                if (entry.unread > 0) add("${entry.unread} unread")
                add(DateUtils.getRelativeTimeSpanString(
                    entry.updated, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                ).toString())
            }.joinToString(" · ")
            unreadBadge.visibility = if (entry.unread > 0) View.VISIBLE else View.GONE
            unreadBadge.text = entry.unread.toString()
            title.typeface = if (entry.unread > 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            title.text = entry.title
            title.setTextColor(if (isCurrent) DikcizUiStyle.ACCENT else DikcizUiStyle.FOREGROUND)
            details.text = metadata
            row.background = ripple(if (isCurrent) DikcizUiStyle.SURFACE else Color.TRANSPARENT)
            row.contentDescription = "Open ${entry.title}, $metadata"
            row.isSelected = isCurrent
            row.setOnClickListener { onOpen(entry.id) }
            overflow.contentDescription = "Actions for ${entry.title}"
            overflow.setOnClickListener { onManage(entry, overflow) }
        }
    }

    private fun ListView.savedViewPadding() {
        setPadding(0, 0, 0, dp(8))
        clipToPadding = false
    }

    private fun icon(resource: Int, description: String, action: () -> Unit) = ImageButton(context).apply {
        setImageResource(resource)
        contentDescription = description
        background = ripple(Color.TRANSPARENT)
        setOnClickListener { action() }
    }

    private fun ripple(color: Int) = RippleDrawable(
        ColorStateList.valueOf(RIPPLE), surface(color, 12), surface(Color.WHITE, 12),
    )

    private fun surface(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val RIPPLE = 0x33F6C85F
    }
}
