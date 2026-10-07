package org.fossify.home.dikciz

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import org.fossify.home.R

/**
 * The native launcher app drawer. It is a recycling [ListView] over the real Android
 * launcher catalogue and never hosts a WebView, so app enumeration stays a native surface.
 */
internal class DikcizAppDrawerView @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null,
    defaultStyleAttribute: Int = 0,
) : FrameLayout(context, attributes, defaultStyleAttribute) {
    var onLaunchRequested: ((DikcizLaunchableApp) -> Unit)? = null
    var onActionsRequested: ((DikcizLaunchableApp) -> Unit)? = null
    var onCloseRequested: (() -> Unit)? = null
    var onRowBound: ((DikcizLaunchableApp, View, View) -> Unit)? = null
    var onResultsRendered: ((Int) -> Unit)? = null

    val searchInput: EditText
    val closeButton: ImageButton
    val emptyStateView: TextView

    private val appList: ListView
    private val adapter = ApplicationAdapter()
    private val clearSearchButton: ImageButton
    private val countView: TextView
    private val emptyState: View
    private var applications: List<DikcizLaunchableApp> = emptyList()
    private var visibleApplications: List<DikcizLaunchableApp> = emptyList()

    val isOpen: Boolean
        get() = visibility == View.VISIBLE

    init {
        // The drawer covers the page, so it must swallow touches that would otherwise
        // reach the page canvas underneath it.
        isClickable = true
        isFocusable = true
        setBackgroundColor(context.getColor(R.color.dikciz_drawer_background))
        visibility = View.GONE

        val title = TextView(context).apply {
            setTextColor(context.getColor(R.color.dikciz_foreground))
            setTypeface(typeface, Typeface.BOLD)
            text = context.getString(R.string.dikciz_app_drawer_title)
            textSize = TITLE_TEXT_SIZE_SP
        }
        countView = TextView(context).apply {
            setSingleLine()
            setTextColor(context.getColor(R.color.dikciz_muted_foreground))
            textSize = COUNT_TEXT_SIZE_SP
        }
        val titleColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(countView)
        }
        closeButton = iconButton(R.drawable.dikciz_icon_close).apply {
            contentDescription = context.getString(R.string.dikciz_app_drawer_close)
            setOnClickListener { onCloseRequested?.invoke() }
        }
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            addView(
                titleColumn,
                LinearLayout.LayoutParams(
                    NO_SIZE,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    TITLE_WEIGHT,
                ),
            )
            addView(
                closeButton,
                LinearLayout.LayoutParams(
                    densityPixels(ICON_BUTTON_SIZE_DP),
                    densityPixels(ICON_BUTTON_SIZE_DP),
                ),
            )
        }

        searchInput = EditText(context).apply {
            // The field's boundary is drawn by the surrounding pill, so the platform
            // underline would read as a second, competing edge.
            background = null
            contentDescription = context.getString(R.string.dikciz_app_drawer_search)
            hint = context.getString(R.string.dikciz_app_drawer_search)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setHintTextColor(context.getColor(R.color.dikciz_muted_foreground))
            setPadding(NO_SIZE, NO_SIZE, NO_SIZE, NO_SIZE)
            setSingleLine()
            setTextColor(context.getColor(R.color.dikciz_foreground))
            textSize = SEARCH_TEXT_SIZE_SP
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    value: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(
                    value: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    applyQuery(value?.toString().orEmpty())
                }

                override fun afterTextChanged(value: Editable?) = Unit
            })
        }
        clearSearchButton = iconButton(R.drawable.dikciz_icon_clear).apply {
            contentDescription = context.getString(R.string.dikciz_app_drawer_search_clear)
            visibility = View.GONE
            setOnClickListener { searchInput.setText(EMPTY_QUERY) }
        }
        val searchField = LinearLayout(context).apply {
            background = context.getDrawable(R.drawable.dikciz_search_field)
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                densityPixels(SEARCH_HORIZONTAL_PADDING_DP),
                NO_SIZE,
                densityPixels(SEARCH_HORIZONTAL_PADDING_DP),
                NO_SIZE,
            )
            addView(
                ImageView(context).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    setImageDrawable(context.getDrawable(R.drawable.dikciz_icon_search))
                },
                LinearLayout.LayoutParams(
                    densityPixels(SEARCH_ICON_SIZE_DP),
                    densityPixels(SEARCH_ICON_SIZE_DP),
                ),
            )
            addView(
                searchInput,
                LinearLayout.LayoutParams(
                    NO_SIZE,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    SEARCH_INPUT_WEIGHT,
                ).apply {
                    marginStart = densityPixels(SEARCH_ICON_GAP_DP)
                    marginEnd = densityPixels(SEARCH_ICON_GAP_DP)
                },
            )
            addView(
                clearSearchButton,
                LinearLayout.LayoutParams(
                    densityPixels(SEARCH_CLEAR_SIZE_DP),
                    densityPixels(SEARCH_CLEAR_SIZE_DP),
                ),
            )
        }

        emptyStateView = TextView(context).apply {
            contentDescription = context.getString(R.string.dikciz_app_drawer_empty)
            gravity = Gravity.CENTER
            setTextColor(context.getColor(R.color.dikciz_foreground))
            setTypeface(typeface, Typeface.BOLD)
            text = context.getString(R.string.dikciz_app_drawer_empty)
            textSize = EMPTY_TITLE_TEXT_SIZE_SP
        }
        emptyState = LinearLayout(context).apply {
            background = context.getDrawable(R.drawable.dikciz_empty_state)
            gravity = Gravity.CENTER
            orientation = LinearLayout.VERTICAL
            setPadding(
                densityPixels(EMPTY_PADDING_DP),
                densityPixels(EMPTY_PADDING_DP),
                densityPixels(EMPTY_PADDING_DP),
                densityPixels(EMPTY_PADDING_DP),
            )
            visibility = View.GONE
            addView(emptyStateView)
            addView(
                TextView(context).apply {
                    gravity = Gravity.CENTER
                    setTextColor(context.getColor(R.color.dikciz_muted_foreground))
                    text = context.getString(R.string.dikciz_app_drawer_empty_hint)
                    textSize = EMPTY_HINT_TEXT_SIZE_SP
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = densityPixels(EMPTY_HINT_GAP_DP) },
            )
        }

        appList = ListView(context).apply {
            divider = null
            dividerHeight = NO_SIZE
            isVerticalScrollBarEnabled = true
            adapter = this@DikcizAppDrawerView.adapter
        }
        val listArea = FrameLayout(context).apply {
            addView(
                appList,
                LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                emptyState,
                LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ).apply {
                    marginStart = densityPixels(EMPTY_SIDE_MARGIN_DP)
                    marginEnd = densityPixels(EMPTY_SIDE_MARGIN_DP)
                },
            )
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                densityPixels(CONTENT_PADDING_DP),
                densityPixels(CONTENT_PADDING_DP),
                densityPixels(CONTENT_PADDING_DP),
                NO_SIZE,
            )
            addView(header)
            addView(
                searchField,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    densityPixels(SEARCH_FIELD_HEIGHT_DP),
                ).apply { topMargin = densityPixels(SEARCH_FIELD_TOP_MARGIN_DP) },
            )
            addView(
                listArea,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    NO_SIZE,
                    LIST_WEIGHT,
                ).apply { topMargin = densityPixels(LIST_TOP_MARGIN_DP) },
            )
        }
        addView(
            content,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }

    fun open(loadedApplications: List<DikcizLaunchableApp>) {
        applications = loadedApplications
        searchInput.setText(EMPTY_QUERY)
        visibility = View.VISIBLE
        bringToFront()
        applyQuery(EMPTY_QUERY)
    }

    fun close() {
        visibility = View.GONE
        applications = emptyList()
        visibleApplications = emptyList()
        adapter.notifyDataSetChanged()
    }

    fun visibleApplication(index: Int): DikcizLaunchableApp? = visibleApplications.getOrNull(index)

    private fun applyQuery(query: String) {
        visibleApplications = applications.filter { application ->
            DikcizAppCatalogue.matchesQuery(
                query,
                application.label,
                application.component.packageName,
            )
        }
        val resultCount = visibleApplications.size
        countView.text = resources.getQuantityString(
            R.plurals.dikciz_app_drawer_count,
            resultCount,
            resultCount,
        )
        clearSearchButton.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
        emptyState.visibility = if (visibleApplications.isEmpty()) View.VISIBLE else View.GONE
        appList.visibility = if (visibleApplications.isEmpty()) View.GONE else View.VISIBLE
        adapter.notifyDataSetChanged()
        onResultsRendered?.invoke(resultCount)
    }

    private fun iconButton(iconResourceID: Int): ImageButton {
        return ImageButton(context).apply {
            background = context.getDrawable(R.drawable.dikciz_icon_button)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setImageDrawable(context.getDrawable(iconResourceID))
            setPadding(
                densityPixels(ICON_BUTTON_PADDING_DP),
                densityPixels(ICON_BUTTON_PADDING_DP),
                densityPixels(ICON_BUTTON_PADDING_DP),
                densityPixels(ICON_BUTTON_PADDING_DP),
            )
        }
    }

    private fun densityPixels(valueDP: Int): Int {
        return (valueDP * resources.displayMetrics.density).toInt()
    }

    private inner class ApplicationAdapter : BaseAdapter() {
        override fun getCount(): Int = visibleApplications.size

        override fun getItem(position: Int): Any? = visibleApplications.getOrNull(position)

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView as? LinearLayout ?: createRow()
            val application = visibleApplications[position]
            val icon = row.getChildAt(ROW_ICON_INDEX) as ImageView
            val labels = row.getChildAt(ROW_LABELS_INDEX) as LinearLayout
            val overflow = row.getChildAt(ROW_OVERFLOW_INDEX) as ImageButton
            val label = labels.getChildAt(LABEL_TITLE_INDEX) as TextView
            val packageLabel = labels.getChildAt(LABEL_PACKAGE_INDEX) as TextView
            icon.setImageDrawable(application.icon)
            label.text = application.label
            packageLabel.text = application.component.packageName
            row.contentDescription = context.getString(
                R.string.dikciz_app_drawer_entry_description,
                application.label,
                application.component.packageName,
            )
            // The row owns its own click instead of the list item click, so a real touch
            // and an automation tap on the row take the same path and the pressed state
            // renders on the row itself.
            row.setOnClickListener { onLaunchRequested?.invoke(application) }
            row.setOnLongClickListener {
                onActionsRequested?.invoke(application)
                true
            }
            overflow.contentDescription = context.getString(
                R.string.dikciz_app_drawer_entry_actions_description,
                application.label,
            )
            overflow.setOnClickListener { onActionsRequested?.invoke(application) }
            onRowBound?.invoke(application, row, overflow)
            return row
        }

        private fun createRow(): LinearLayout {
            val row = LinearLayout(context).apply {
                background = context.getDrawable(R.drawable.dikciz_drawer_row)
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = densityPixels(ROW_MINIMUM_HEIGHT_DP)
                orientation = LinearLayout.HORIZONTAL
                setPadding(
                    densityPixels(ROW_HORIZONTAL_PADDING_DP),
                    densityPixels(ROW_VERTICAL_PADDING_DP),
                    densityPixels(ROW_HORIZONTAL_PADDING_DP),
                    densityPixels(ROW_VERTICAL_PADDING_DP),
                )
            }
            row.addView(
                ImageView(context),
                LinearLayout.LayoutParams(
                    densityPixels(ROW_ICON_SIZE_DP),
                    densityPixels(ROW_ICON_SIZE_DP),
                ),
            )
            val labels = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    densityPixels(ROW_LABEL_GAP_DP),
                    NO_SIZE,
                    densityPixels(ROW_LABEL_GAP_DP),
                    NO_SIZE,
                )
            }
            labels.addView(
                TextView(context).apply {
                    ellipsize = TextUtils.TruncateAt.END
                    setSingleLine()
                    setTextColor(context.getColor(R.color.dikciz_foreground))
                    textSize = ROW_LABEL_TEXT_SIZE_SP
                },
            )
            labels.addView(
                TextView(context).apply {
                    // The package is the row's disambiguator, so it is truncated in the
                    // middle to keep both the vendor prefix and the app segment visible.
                    ellipsize = TextUtils.TruncateAt.MIDDLE
                    setSingleLine()
                    setTextColor(context.getColor(R.color.dikciz_muted_foreground))
                    textSize = ROW_PACKAGE_TEXT_SIZE_SP
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = densityPixels(ROW_PACKAGE_GAP_DP) },
            )
            row.addView(
                labels,
                LinearLayout.LayoutParams(
                    NO_SIZE,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    LABELS_WEIGHT,
                ),
            )
            row.addView(
                iconButton(R.drawable.dikciz_icon_overflow),
                LinearLayout.LayoutParams(
                    densityPixels(ICON_BUTTON_SIZE_DP),
                    densityPixels(ICON_BUTTON_SIZE_DP),
                ),
            )
            return row
        }
    }

    private companion object {
        const val CONTENT_PADDING_DP = 16
        const val COUNT_TEXT_SIZE_SP = 13F
        const val EMPTY_HINT_GAP_DP = 6
        const val EMPTY_HINT_TEXT_SIZE_SP = 13F
        const val EMPTY_PADDING_DP = 24
        const val EMPTY_QUERY = ""
        const val EMPTY_SIDE_MARGIN_DP = 8
        const val EMPTY_TITLE_TEXT_SIZE_SP = 16F
        const val ICON_BUTTON_PADDING_DP = 8
        const val ICON_BUTTON_SIZE_DP = 44
        const val LABELS_WEIGHT = 1F
        const val LABEL_PACKAGE_INDEX = 1
        const val LABEL_TITLE_INDEX = 0
        const val LIST_TOP_MARGIN_DP = 10
        const val LIST_WEIGHT = 1F
        const val NO_SIZE = 0
        const val ROW_HORIZONTAL_PADDING_DP = 8
        const val ROW_ICON_INDEX = 0
        const val ROW_ICON_SIZE_DP = 46
        const val ROW_LABELS_INDEX = 1
        const val ROW_LABEL_GAP_DP = 14
        const val ROW_LABEL_TEXT_SIZE_SP = 17F
        const val ROW_MINIMUM_HEIGHT_DP = 72
        const val ROW_OVERFLOW_INDEX = 2
        const val ROW_PACKAGE_GAP_DP = 2
        const val ROW_PACKAGE_TEXT_SIZE_SP = 12F
        const val ROW_VERTICAL_PADDING_DP = 10
        const val SEARCH_CLEAR_SIZE_DP = 32
        const val SEARCH_FIELD_HEIGHT_DP = 50
        const val SEARCH_FIELD_TOP_MARGIN_DP = 14
        const val SEARCH_HORIZONTAL_PADDING_DP = 12
        const val SEARCH_ICON_GAP_DP = 10
        const val SEARCH_ICON_SIZE_DP = 20
        const val SEARCH_INPUT_WEIGHT = 1F
        const val SEARCH_TEXT_SIZE_SP = 16F
        const val TITLE_TEXT_SIZE_SP = 24F
        const val TITLE_WEIGHT = 1F
    }
}
