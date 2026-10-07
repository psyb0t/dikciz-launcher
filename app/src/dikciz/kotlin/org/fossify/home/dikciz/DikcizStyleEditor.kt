package org.fossify.home.dikciz

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.Locale
import org.fossify.home.R

internal class DikcizStyleEditor(
    private val context: Context,
    private val fontDirectory: File,
    private val typefaceForPreview: (DikcizFontSelection) -> Typeface,
    style: DikcizStyle,
    private val standardPaddingPixels: Int,
) {
    val view: ScrollView

    private val controls = mutableMapOf<DikcizStyleEditorControl, View>()
    private val backgroundColorInput: EditText
    private val backgroundOpacityInput: EditText
    private val borderColorInput: EditText
    private val borderRadiusInput: EditText
    private val borderWidthsEditor: DikcizEdgeEditor
    private val fontPicker: DikcizFontPicker
    private val marginEditor: DikcizEdgeEditor
    private val paddingEditor: DikcizEdgeEditor
    private val textColorInput: EditText
    private val textSizeInput: EditText

    init {
        val form = LinearLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            orientation = LinearLayout.VERTICAL
            setPadding(
                standardPaddingPixels,
                NO_PADDING_PIXELS,
                standardPaddingPixels,
                standardPaddingPixels,
            )
        }
        view = ScrollView(context).apply {
            contentDescription = context.getString(R.string.dikciz_appearance_editor_description)
            isFillViewport = true
            addView(form)
        }

        form.addView(createDescription())
        form.addView(createSectionTitle(R.string.dikciz_appearance_background))
        backgroundColorInput = addColorInput(
            container = form,
            labelResourceID = R.string.dikciz_appearance_background_color,
            control = DikcizStyleEditorControl.BackgroundColor,
            initialValue = style.background?.color.orEmpty(),
        )
        backgroundOpacityInput = addDecimalStepper(
            container = form,
            labelResourceID = R.string.dikciz_appearance_background_opacity,
            control = DikcizStyleEditorControl.BackgroundOpacity,
            initialValue = style.background?.opacity,
        )

        form.addView(createSectionTitle(R.string.dikciz_appearance_border))
        borderColorInput = addColorInput(
            container = form,
            labelResourceID = R.string.dikciz_appearance_border_color,
            control = DikcizStyleEditorControl.BorderColor,
            initialValue = style.border?.color.orEmpty(),
        )
        borderRadiusInput = addIntegerStepper(
            container = form,
            labelResourceID = R.string.dikciz_appearance_border_radius,
            control = DikcizStyleEditorControl.BorderRadius,
            initialValue = style.border?.radiusDP,
            minimumValue = DikcizStyleCodec.MINIMUM_STYLE_DIMENSION_DP,
            maximumValue = DikcizStyleCodec.MAXIMUM_STYLE_DIMENSION_DP,
            emptyIncreaseValue = FIRST_STYLE_DIMENSION_DP,
        )
        borderWidthsEditor = DikcizEdgeEditor(
            context = context,
            container = form,
            standardPaddingPixels = standardPaddingPixels,
            titleResourceID = R.string.dikciz_appearance_border_widths,
            controls = BORDER_WIDTH_CONTROLS,
            initialEdges = style.border?.widths,
        )

        form.addView(createSectionTitle(R.string.dikciz_appearance_spacing))
        paddingEditor = DikcizEdgeEditor(
            context = context,
            container = form,
            standardPaddingPixels = standardPaddingPixels,
            titleResourceID = R.string.dikciz_appearance_padding,
            controls = PADDING_CONTROLS,
            initialEdges = style.padding,
        )
        marginEditor = DikcizEdgeEditor(
            context = context,
            container = form,
            standardPaddingPixels = standardPaddingPixels,
            titleResourceID = R.string.dikciz_appearance_margin,
            controls = MARGIN_CONTROLS,
            initialEdges = style.margin,
        )

        form.addView(createSectionTitle(R.string.dikciz_appearance_text))
        textColorInput = addColorInput(
            container = form,
            labelResourceID = R.string.dikciz_appearance_text_color,
            control = DikcizStyleEditorControl.TextColor,
            initialValue = style.text?.color.orEmpty(),
        )
        textSizeInput = addIntegerStepper(
            container = form,
            labelResourceID = R.string.dikciz_appearance_text_size,
            control = DikcizStyleEditorControl.TextSize,
            initialValue = style.text?.sizeSP,
        )
        fontPicker = DikcizFontPicker(
            context = context,
            fontDirectory = fontDirectory,
            initialSelection = style.text?.font,
            typefaceForPreview = typefaceForPreview,
        )
        form.addView(fontPicker.view)
        controls[DikcizStyleEditorControl.FontPicker] = fontPicker.button
    }

    fun registerControls(register: (DikcizStyleEditorControl, View) -> Unit) {
        controls.forEach(register)
        borderWidthsEditor.registerControls(register)
        marginEditor.registerControls(register)
        paddingEditor.registerControls(register)
    }

    fun readStyle(): DikcizStyle {
        val background = DikcizBackgroundStyle(
            color = backgroundColorInput.optionalText(),
            opacity = backgroundOpacityInput.optionalDecimal(
                R.string.dikciz_appearance_background_opacity,
            ),
        ).takeUnless(DikcizBackgroundStyle::isEmpty)
        val border = DikcizBorderStyle(
            color = borderColorInput.optionalText(),
            radiusDP = borderRadiusInput.optionalInteger(R.string.dikciz_appearance_border_radius),
            widths = borderWidthsEditor.readEdges(),
        ).takeUnless(DikcizBorderStyle::isEmpty)
        val text = DikcizTextStyle(
            color = textColorInput.optionalText(),
            font = fontPicker.selectedFont(),
            sizeSP = textSizeInput.optionalInteger(R.string.dikciz_appearance_text_size),
        ).takeUnless(DikcizTextStyle::isEmpty)
        return DikcizStyle(
            background = background,
            border = border,
            margin = marginEditor.readEdges(),
            padding = paddingEditor.readEdges(),
            text = text,
        )
    }

    private fun createDescription(): TextView {
        return TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = standardPaddingPixels
            }
            text = context.getString(R.string.dikciz_appearance_description)
            setTextColor(context.getColor(R.color.dikciz_muted_foreground))
        }
    }

    private fun createSectionTitle(labelResourceID: Int): TextView {
        return TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = standardPaddingPixels
            }
            text = context.getString(labelResourceID)
            setTextColor(context.getColor(R.color.dikciz_foreground))
            textSize = SECTION_TITLE_SIZE_SP
        }
    }

    private fun addColorInput(
        container: LinearLayout,
        labelResourceID: Int,
        control: DikcizStyleEditorControl,
        initialValue: String,
        pickerControl: DikcizStyleEditorControl = when (control) {
            DikcizStyleEditorControl.BackgroundColor -> DikcizStyleEditorControl.BackgroundColorPicker
            DikcizStyleEditorControl.BorderColor -> DikcizStyleEditorControl.BorderColorPicker
            DikcizStyleEditorControl.TextColor -> DikcizStyleEditorControl.TextColorPicker
            else -> error("A color picker is required for $control")
        },
    ): EditText {
        val label = context.getString(labelResourceID)
        val row = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            gravity = android.view.Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        val swatch = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                COLOR_SWATCH_SIZE_PIXELS,
                COLOR_SWATCH_SIZE_PIXELS,
            ).apply {
                marginEnd = FIELD_CONTROL_GAP_PIXELS
            }
        }
        val input = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                NO_EXPLICIT_SIZE,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                INPUT_WEIGHT,
            )
            contentDescription = label
            hint = context.getString(R.string.dikciz_appearance_inherited_hint)
            inputType = INPUT_TYPE_TEXT
            setSingleLine()
            setSelectAllOnFocus(true)
            setText(initialValue)
        }
        val picker = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginStart = FIELD_CONTROL_GAP_PIXELS
            }
            contentDescription = context.getString(
                R.string.dikciz_appearance_choose_color_description,
                label,
            )
            isAllCaps = false
            text = context.getString(R.string.dikciz_appearance_pick_color)
            setOnClickListener {
                DikcizColorPicker(
                    context = context,
                    label = label,
                    initialValue = input.optionalText(),
                    onColorSelected = input::setText,
                ).show()
            }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(value: Editable?) {
                updateColorSwatch(swatch, label, value?.toString().orEmpty())
            }

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
            ) = Unit
        })
        updateColorSwatch(swatch, label, initialValue)
        container.addView(createFieldLabel(labelResourceID))
        row.addView(swatch)
        row.addView(input)
        row.addView(picker)
        container.addView(row)
        controls[control] = input
        controls[pickerControl] = picker
        return input
    }

    private fun addDecimalStepper(
        container: LinearLayout,
        labelResourceID: Int,
        control: DikcizStyleEditorControl,
        initialValue: Double?,
    ): EditText {
        val label = context.getString(labelResourceID)
        container.addView(createFieldLabel(labelResourceID))
        val input = addStepperRow(
            container = container,
            label = label,
            initialValue = initialValue?.let(::formatDecimal).orEmpty(),
            hint = context.getString(R.string.dikciz_appearance_inherited_hint),
            inputType = INPUT_TYPE_DECIMAL,
            onDecrease = { value ->
                (value.toDoubleOrNull() ?: DikcizStyleCodec.MINIMUM_OPACITY)
                    .minus(OPACITY_STEP)
                    .coerceIn(DikcizStyleCodec.MINIMUM_OPACITY, DikcizStyleCodec.MAXIMUM_OPACITY)
                    .let(::formatDecimal)
            },
            onIncrease = { value ->
                (value.toDoubleOrNull() ?: NO_OPACITY)
                    .plus(OPACITY_STEP)
                    .coerceIn(DikcizStyleCodec.MINIMUM_OPACITY, DikcizStyleCodec.MAXIMUM_OPACITY)
                    .let(::formatDecimal)
            },
        )
        controls[control] = input
        return input
    }

    private fun addIntegerStepper(
        container: LinearLayout,
        labelResourceID: Int,
        control: DikcizStyleEditorControl,
        initialValue: Int?,
        minimumValue: Int = DikcizStyleCodec.MINIMUM_TEXT_SIZE_SP,
        maximumValue: Int = DikcizStyleCodec.MAXIMUM_TEXT_SIZE_SP,
        emptyIncreaseValue: Int = DikcizStyleCodec.MINIMUM_TEXT_SIZE_SP,
    ): EditText {
        container.addView(createFieldLabel(labelResourceID))
        val input = addIntegerStepperRow(
            context = context,
            container = container,
            label = context.getString(labelResourceID),
            initialValue = initialValue,
            minimumValue = minimumValue,
            maximumValue = maximumValue,
            emptyIncreaseValue = emptyIncreaseValue,
            hint = context.getString(R.string.dikciz_appearance_inherited_hint),
        )
        controls[control] = input
        return input
    }

    private fun addStepperRow(
        container: LinearLayout,
        label: String,
        initialValue: String,
        hint: String,
        inputType: Int,
        onDecrease: (String) -> String,
        onIncrease: (String) -> String,
    ): EditText {
        val row = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            gravity = android.view.Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        val input = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                NO_EXPLICIT_SIZE,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                INPUT_WEIGHT,
            )
            contentDescription = label
            this.inputType = inputType
            this.hint = hint
            setSingleLine()
            setSelectAllOnFocus(true)
            setText(initialValue)
        }
        row.addView(createStepperButton(label, isIncrease = false) {
            input.setText(onDecrease(input.optionalText().orEmpty()))
        })
        row.addView(input)
        row.addView(createStepperButton(label, isIncrease = true) {
            input.setText(onIncrease(input.optionalText().orEmpty()))
        })
        container.addView(row)
        return input
    }

    private fun createStepperButton(
        label: String,
        isIncrease: Boolean,
        onClick: () -> Unit,
    ): Button {
        return Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                STEPPER_BUTTON_WIDTH_PIXELS,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            isAllCaps = false
            contentDescription = context.getString(
                if (isIncrease) {
                    R.string.dikciz_appearance_increase_value
                } else {
                    R.string.dikciz_appearance_decrease_value
                },
                label,
            )
            text = context.getString(
                if (isIncrease) {
                    R.string.dikciz_appearance_stepper_increase_symbol
                } else {
                    R.string.dikciz_appearance_stepper_decrease_symbol
                },
            )
            setOnClickListener { onClick() }
        }
    }

    private fun updateColorSwatch(
        swatch: TextView,
        label: String,
        candidate: String,
    ) {
        val color = parseColor(candidate) ?: Color.TRANSPARENT
        swatch.background = GradientDrawable().apply {
            cornerRadius = COLOR_SWATCH_CORNER_RADIUS_PIXELS
            setColor(color)
            setStroke(COLOR_SWATCH_BORDER_WIDTH_PIXELS, context.getColor(R.color.dikciz_widget_border))
        }
        swatch.contentDescription = context.getString(
            R.string.dikciz_appearance_current_color_description,
            label,
            candidate.ifBlank { context.getString(R.string.dikciz_appearance_inherited) },
        )
    }

    private fun createFieldLabel(labelResourceID: Int): TextView {
        return TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = FIELD_LABEL_TOP_MARGIN_PIXELS
            }
            text = context.getString(labelResourceID)
        }
    }

    private fun EditText.optionalDecimal(labelResourceID: Int): Double? {
        val value = optionalText() ?: return null
        val parsed = value.toDoubleOrNull()
        if (parsed != null) {
            return parsed
        }
        val message = context.getString(
            R.string.dikciz_appearance_decimal_required,
            context.getString(labelResourceID),
        )
        error = message
        throw DikcizStyleEditorInputException(message)
    }

    private fun EditText.optionalInteger(labelResourceID: Int): Int? {
        val value = optionalText() ?: return null
        val parsed = value.toIntOrNull()
        if (parsed != null) {
            return parsed
        }
        val message = context.getString(
            R.string.dikciz_appearance_integer_required,
            context.getString(labelResourceID),
        )
        error = message
        throw DikcizStyleEditorInputException(message)
    }

    private fun EditText.optionalText(): String? = text.toString().trim().ifEmpty { null }

    private companion object {
        const val FIELD_LABEL_TOP_MARGIN_PIXELS = 8
        const val COLOR_SWATCH_BORDER_WIDTH_PIXELS = 2
        const val COLOR_SWATCH_CORNER_RADIUS_PIXELS = 6F
        const val COLOR_SWATCH_SIZE_PIXELS = 48
        const val FIELD_CONTROL_GAP_PIXELS = 8
        val INPUT_TYPE_DECIMAL = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        const val INPUT_TYPE_INTEGER = InputType.TYPE_CLASS_NUMBER
        const val INPUT_TYPE_TEXT = InputType.TYPE_CLASS_TEXT
        const val NO_PADDING_PIXELS = 0
        const val NO_EXPLICIT_SIZE = 0
        const val NO_OPACITY = 0.0
        const val OPACITY_STEP = 0.05
        const val INPUT_WEIGHT = 1F
        const val SECTION_TITLE_SIZE_SP = 18F
        const val STEPPER_BUTTON_WIDTH_PIXELS = 72
        val BORDER_WIDTH_CONTROLS = DikcizEdgeEditorControls(
            all = DikcizStyleEditorControl.BorderWidthAll,
            bottom = DikcizStyleEditorControl.BorderWidthBottom,
            left = DikcizStyleEditorControl.BorderWidthLeft,
            individualEdges = DikcizStyleEditorControl.BorderWidthIndividualEdges,
            right = DikcizStyleEditorControl.BorderWidthRight,
            top = DikcizStyleEditorControl.BorderWidthTop,
        )
        val MARGIN_CONTROLS = DikcizEdgeEditorControls(
            all = DikcizStyleEditorControl.MarginAll,
            bottom = DikcizStyleEditorControl.MarginBottom,
            left = DikcizStyleEditorControl.MarginLeft,
            individualEdges = DikcizStyleEditorControl.MarginIndividualEdges,
            right = DikcizStyleEditorControl.MarginRight,
            top = DikcizStyleEditorControl.MarginTop,
        )
        val PADDING_CONTROLS = DikcizEdgeEditorControls(
            all = DikcizStyleEditorControl.PaddingAll,
            bottom = DikcizStyleEditorControl.PaddingBottom,
            left = DikcizStyleEditorControl.PaddingLeft,
            individualEdges = DikcizStyleEditorControl.PaddingIndividualEdges,
            right = DikcizStyleEditorControl.PaddingRight,
            top = DikcizStyleEditorControl.PaddingTop,
        )
    }
}

internal enum class DikcizStyleEditorControl(
    val persistedValue: String,
) {
    BackgroundColor("background-color"),
    BackgroundColorPicker("background-color-picker"),
    BackgroundOpacity("background-opacity"),
    BorderColor("border-color"),
    BorderColorPicker("border-color-picker"),
    BorderRadius("border-radius"),
    BorderWidthAll("border-width-all"),
    BorderWidthBottom("border-width-bottom"),
    BorderWidthLeft("border-width-left"),
    BorderWidthIndividualEdges("border-width-individual-edges"),
    BorderWidthRight("border-width-right"),
    BorderWidthTop("border-width-top"),
    FontPicker("font-picker"),
    MarginAll("margin-all"),
    MarginBottom("margin-bottom"),
    MarginLeft("margin-left"),
    MarginIndividualEdges("margin-individual-edges"),
    MarginRight("margin-right"),
    MarginTop("margin-top"),
    PaddingAll("padding-all"),
    PaddingBottom("padding-bottom"),
    PaddingLeft("padding-left"),
    PaddingIndividualEdges("padding-individual-edges"),
    PaddingRight("padding-right"),
    PaddingTop("padding-top"),
    TextColor("text-color"),
    TextColorPicker("text-color-picker"),
    TextSize("text-size"),
    ;
}

private data class DikcizEdgeEditorControls(
    val all: DikcizStyleEditorControl,
    val bottom: DikcizStyleEditorControl,
    val left: DikcizStyleEditorControl,
    val individualEdges: DikcizStyleEditorControl,
    val right: DikcizStyleEditorControl,
    val top: DikcizStyleEditorControl,
)

private class DikcizEdgeEditor(
    private val context: Context,
    container: LinearLayout,
    standardPaddingPixels: Int,
    titleResourceID: Int,
    controls: DikcizEdgeEditorControls,
    initialEdges: DikcizBoxEdges?,
) {
    private val controlsByField = mutableMapOf<DikcizStyleEditorControl, View>()
    private val allInput: EditText
    private val bottomInput: EditText
    private val individualEdgesInput: CheckBox
    private val leftInput: EditText
    private val rightInput: EditText
    private val sideInputs: LinearLayout
    private val topInput: EditText

    init {
        container.addView(TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = standardPaddingPixels
            }
            text = context.getString(titleResourceID)
        })
        allInput = addEdgeInput(
            container = container,
            labelResourceID = R.string.dikciz_appearance_all,
            control = controls.all,
            initialValue = initialEdges?.all,
        )
        individualEdgesInput = CheckBox(context).apply {
            text = context.getString(R.string.dikciz_appearance_individual_edges)
            contentDescription = text
            isChecked = initialEdges.hasIndividualEdges()
        }
        controlsByField[controls.individualEdges] = individualEdgesInput
        container.addView(individualEdgesInput)
        sideInputs = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (individualEdgesInput.isChecked) View.VISIBLE else View.GONE
        }
        leftInput = addEdgeInput(
            container = sideInputs,
            labelResourceID = R.string.dikciz_appearance_left,
            control = controls.left,
            initialValue = initialEdges?.left,
        )
        topInput = addEdgeInput(
            container = sideInputs,
            labelResourceID = R.string.dikciz_appearance_top,
            control = controls.top,
            initialValue = initialEdges?.top,
        )
        rightInput = addEdgeInput(
            container = sideInputs,
            labelResourceID = R.string.dikciz_appearance_right,
            control = controls.right,
            initialValue = initialEdges?.right,
        )
        bottomInput = addEdgeInput(
            container = sideInputs,
            labelResourceID = R.string.dikciz_appearance_bottom,
            control = controls.bottom,
            initialValue = initialEdges?.bottom,
        )
        container.addView(sideInputs)
        individualEdgesInput.setOnCheckedChangeListener { _, isChecked ->
            sideInputs.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
    }

    fun readEdges(): DikcizBoxEdges? {
        val edges = DikcizBoxEdges(
            all = allInput.optionalInteger(),
            bottom = if (individualEdgesInput.isChecked) bottomInput.optionalInteger() else null,
            left = if (individualEdgesInput.isChecked) leftInput.optionalInteger() else null,
            right = if (individualEdgesInput.isChecked) rightInput.optionalInteger() else null,
            top = if (individualEdgesInput.isChecked) topInput.optionalInteger() else null,
        )
        return edges.takeUnless(DikcizBoxEdges::isEmpty)
    }

    fun registerControls(register: (DikcizStyleEditorControl, View) -> Unit) {
        controlsByField.forEach(register)
    }

    private fun addEdgeInput(
        container: LinearLayout,
        labelResourceID: Int,
        control: DikcizStyleEditorControl,
        initialValue: Int?,
    ): EditText {
        container.addView(TextView(context).apply {
            text = context.getString(labelResourceID)
        })
        return addIntegerStepperRow(
            context = context,
            container = container,
            label = context.getString(labelResourceID),
            initialValue = initialValue,
            minimumValue = DikcizStyleCodec.MINIMUM_STYLE_DIMENSION_DP,
            maximumValue = DikcizStyleCodec.MAXIMUM_STYLE_DIMENSION_DP,
            emptyIncreaseValue = FIRST_STYLE_DIMENSION_DP,
            hint = context.getString(R.string.dikciz_appearance_inherited_hint),
        ).also { input ->
            controlsByField[control] = input
        }
    }

    private fun EditText.optionalInteger(): Int? {
        val value = text.toString().trim()
        if (value.isEmpty()) {
            return null
        }
        val parsed = value.toIntOrNull()
        if (parsed != null) {
            return parsed
        }
        val message = context.getString(R.string.dikciz_appearance_integer_required, contentDescription)
        error = message
        throw DikcizStyleEditorInputException(message)
    }
}

private class DikcizColorPicker(
    private val context: Context,
    private val label: String,
    initialValue: String?,
    private val onColorSelected: (String) -> Unit,
) {
    private val colors = buildList {
        initialValue?.takeIf(::isColorValue)?.let(::add)
        addAll(COLOR_PALETTE)
    }.distinct()

    fun show() {
        val palette = GridLayout(context).apply {
            columnCount = COLOR_PICKER_COLUMN_COUNT
            useDefaultMargins = true
        }
        lateinit var dialog: AlertDialog
        colors.forEach { value ->
            val color = parseColor(value) ?: return@forEach
            palette.addView(Button(context).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    height = context.densityPixels(COLOR_PICKER_BUTTON_HEIGHT_DP)
                    width = context.densityPixels(COLOR_PICKER_BUTTON_WIDTH_DP)
                }
                background = GradientDrawable().apply {
                    cornerRadius = context.densityPixels(COLOR_PICKER_CORNER_RADIUS_DP).toFloat()
                    setColor(color)
                    setStroke(
                        context.densityPixels(COLOR_PICKER_BORDER_WIDTH_DP),
                        context.getColor(R.color.dikciz_widget_border),
                    )
                }
                contentDescription = context.getString(R.string.dikciz_appearance_use_color_description, value)
                isAllCaps = false
                setTextColor(color.contrastTextColor())
                text = value
                textSize = COLOR_PICKER_TEXT_SIZE_SP
                typeface = Typeface.MONOSPACE
                setOnClickListener {
                    onColorSelected(value)
                    dialog.dismiss()
                }
            })
        }
        dialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.dikciz_appearance_choose_color_title, label))
            .setView(ScrollView(context).apply { addView(palette) })
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .create()
        dialog.show()
    }
}

private class DikcizFontPicker(
    private val context: Context,
    private val fontDirectory: File,
    initialSelection: DikcizFontSelection?,
    private val typefaceForPreview: (DikcizFontSelection) -> Typeface,
) {
    val view: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }
    val button = Button(context)
    private var selection = initialSelection

    init {
        view.addView(TextView(context).apply {
            text = context.getString(R.string.dikciz_appearance_font_label)
        })
        button.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            isAllCaps = false
            setOnClickListener { showPicker() }
        }
        view.addView(button)
        updateButton()
    }

    fun selectedFont(): DikcizFontSelection? = selection

    private fun showPicker() {
        val options = fontOptions()
        val choices = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        lateinit var dialog: AlertDialog
        options.forEach { option ->
            choices.addView(Button(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                contentDescription = context.getString(
                    R.string.dikciz_appearance_use_font_description,
                    option.displayName,
                )
                isAllCaps = false
                text = option.displayName
                typeface = option.selection?.let(typefaceForPreview) ?: Typeface.DEFAULT
                setOnClickListener {
                    selection = option.selection
                    updateButton()
                    dialog.dismiss()
                }
            })
        }
        dialog = AlertDialog.Builder(context)
            .setTitle(R.string.dikciz_appearance_choose_font_title)
            .setView(ScrollView(context).apply { addView(choices) })
            .setNegativeButton(R.string.dikciz_dialog_cancel, null)
            .create()
        dialog.show()
    }

    private fun fontOptions(): List<DikcizFontOption> {
        val options = buildList {
            add(DikcizFontOption(null, context.getString(R.string.dikciz_appearance_inherited)))
            DikcizBundledFont.entries.forEach { font ->
                add(
                    DikcizFontOption(
                        selection = DikcizFontSelection(font.persistedValue, DikcizFontSource.Bundled),
                        displayName = context.getString(
                            R.string.dikciz_appearance_font_bundled_option,
                            font.displayName(context),
                        ),
                    ),
                )
            }
            DikcizSystemFont.entries.forEach { font ->
                add(
                    DikcizFontOption(
                        selection = DikcizFontSelection(font.persistedValue, DikcizFontSource.System),
                        displayName = context.getString(
                            R.string.dikciz_appearance_font_system_option,
                            font.displayName(context),
                        ),
                    ),
                )
            }
            fontDirectory.listFiles()
                .orEmpty()
                .asSequence()
                .filter(File::isFile)
                .map(File::getName)
                .filter(DikcizStyleCodec::isSupportedLocalFontFileName)
                .sorted()
                .forEach { fileName ->
                    add(
                        DikcizFontOption(
                            selection = DikcizFontSelection(fileName, DikcizFontSource.Local),
                            displayName = context.getString(
                                R.string.dikciz_appearance_font_local_option,
                                fileName,
                            ),
                        ),
                    )
                }
        }.toMutableList()
        selection?.takeIf { selected -> options.none { it.selection == selected } }?.let { selected ->
            options.add(
                DikcizFontOption(
                    selection = selected,
                    displayName = selected.displayName(context),
                ),
            )
        }
        return options
    }

    private fun updateButton() {
        val option = selection?.let { selected ->
            DikcizFontOption(selected, selected.displayName(context))
        } ?: DikcizFontOption(null, context.getString(R.string.dikciz_appearance_inherited))
        button.contentDescription = context.getString(
            R.string.dikciz_appearance_choose_font_description,
            option.displayName,
        )
        button.text = option.displayName
        button.typeface = option.selection?.let(typefaceForPreview) ?: Typeface.DEFAULT
    }
}

private data class DikcizFontOption(
    val selection: DikcizFontSelection?,
    val displayName: String,
)

private fun addIntegerStepperRow(
    context: Context,
    container: LinearLayout,
    label: String,
    initialValue: Int?,
    minimumValue: Int,
    maximumValue: Int,
    emptyIncreaseValue: Int,
    hint: String,
): EditText {
    val row = LinearLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        gravity = android.view.Gravity.CENTER_VERTICAL
        orientation = LinearLayout.HORIZONTAL
    }
    val input = EditText(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            NO_EXPLICIT_SIZE,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            INPUT_WEIGHT,
        )
        contentDescription = label
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER
        setSingleLine()
        setSelectAllOnFocus(true)
        setText(initialValue?.toString().orEmpty())
    }
    row.addView(createIntegerStepperButton(context, label, isIncrease = false) {
        val value = input.optionalIntegerValue()
        val nextValue = (value ?: minimumValue).minus(INTEGER_STEPPER_INCREMENT)
            .coerceIn(minimumValue, maximumValue)
        input.setText(nextValue.toString())
    })
    row.addView(input)
    row.addView(createIntegerStepperButton(context, label, isIncrease = true) {
        val value = input.optionalIntegerValue()
        val nextValue = if (value == null) {
            emptyIncreaseValue
        } else {
            value.plus(INTEGER_STEPPER_INCREMENT).coerceIn(minimumValue, maximumValue)
        }
        input.setText(nextValue.toString())
    })
    container.addView(row)
    return input
}

private fun createIntegerStepperButton(
    context: Context,
    label: String,
    isIncrease: Boolean,
    onClick: () -> Unit,
): Button {
    return Button(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            STEPPER_BUTTON_WIDTH_PIXELS,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        contentDescription = context.getString(
            if (isIncrease) {
                R.string.dikciz_appearance_increase_value
            } else {
                R.string.dikciz_appearance_decrease_value
            },
            label,
        )
        text = context.getString(
            if (isIncrease) {
                R.string.dikciz_appearance_stepper_increase_symbol
            } else {
                R.string.dikciz_appearance_stepper_decrease_symbol
            },
        )
        setOnClickListener { onClick() }
    }
}

private fun DikcizBundledFont.displayName(context: Context): String {
    return when (this) {
        DikcizBundledFont.Inter -> context.getString(R.string.dikciz_font_inter)
        DikcizBundledFont.JetBrainsMono -> context.getString(R.string.dikciz_font_jetbrains_mono)
        DikcizBundledFont.SpaceGrotesk -> context.getString(R.string.dikciz_font_space_grotesk)
        DikcizBundledFont.ComicNeue -> context.getString(R.string.dikciz_font_comic_neue)
    }
}

private fun DikcizSystemFont.displayName(context: Context): String {
    return when (this) {
        DikcizSystemFont.Default -> context.getString(R.string.dikciz_font_android_default)
        DikcizSystemFont.SansSerif -> context.getString(R.string.dikciz_font_sans_serif)
        DikcizSystemFont.Serif -> context.getString(R.string.dikciz_font_serif)
        DikcizSystemFont.Monospace -> context.getString(R.string.dikciz_font_monospace)
        DikcizSystemFont.Cursive -> context.getString(R.string.dikciz_font_cursive)
    }
}

private fun DikcizFontSelection.displayName(context: Context): String {
    return when (source) {
        DikcizFontSource.Bundled -> {
            DikcizBundledFont.fromPersistedValue(id)?.displayName(context) ?: id
        }

        DikcizFontSource.Local -> context.getString(R.string.dikciz_appearance_font_local_option, id)
        DikcizFontSource.System -> DikcizSystemFont.fromPersistedValue(id)?.displayName(context) ?: id
    }
}

private fun EditText.optionalIntegerValue(): Int? = text.toString().trim().toIntOrNull()

private fun parseColor(value: String): Int? {
    if (!isColorValue(value)) {
        return null
    }
    return try {
        if (value.length == RGBA_COLOR_LENGTH) {
            Color.parseColor("#${value.substring(RGBA_ALPHA_OFFSET)}${value.substring(HEX_PREFIX_LENGTH, RGBA_ALPHA_OFFSET)}")
        } else {
            Color.parseColor(value)
        }
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun isColorValue(value: String): Boolean = COLOR_VALUE_PATTERN.matches(value)

private fun formatDecimal(value: Double): String = String.format(Locale.ROOT, DECIMAL_FORMAT, value)

private fun Context.densityPixels(valueDP: Int): Int = (valueDP * resources.displayMetrics.density).toInt()

private fun Int.contrastTextColor(): Int {
    val luminance = (COLOR_RED_WEIGHT * Color.red(this)) +
        (COLOR_GREEN_WEIGHT * Color.green(this)) +
        (COLOR_BLUE_WEIGHT * Color.blue(this))
    return if (luminance < CONTRAST_LUMINANCE_THRESHOLD) Color.WHITE else Color.BLACK
}

private const val COLOR_PICKER_BORDER_WIDTH_DP = 1
private const val COLOR_PICKER_BUTTON_HEIGHT_DP = 48
private const val COLOR_PICKER_BUTTON_WIDTH_DP = 96
private const val COLOR_PICKER_COLUMN_COUNT = 3
private const val COLOR_PICKER_CORNER_RADIUS_DP = 6
private const val COLOR_PICKER_TEXT_SIZE_SP = 10F
private const val COLOR_RED_WEIGHT = 0.2126
private const val COLOR_GREEN_WEIGHT = 0.7152
private const val COLOR_BLUE_WEIGHT = 0.0722
private const val CONTRAST_LUMINANCE_THRESHOLD = 128.0
private const val DECIMAL_FORMAT = "%.2f"
private const val FIRST_STYLE_DIMENSION_DP = 1
private const val HEX_PREFIX_LENGTH = 1
private const val INPUT_WEIGHT = 1F
private const val INTEGER_STEPPER_INCREMENT = 1
private const val NO_EXPLICIT_SIZE = 0
private const val RGBA_ALPHA_OFFSET = 7
private const val RGBA_COLOR_LENGTH = 9
private const val STEPPER_BUTTON_WIDTH_PIXELS = 72
private val COLOR_PALETTE = listOf(
    "#101010",
    "#242424",
    "#FFFFFF",
    "#D0D0D0",
    "#173A5E",
    "#3A78B4",
    "#7F1D1D",
    "#B91C1C",
    "#14532D",
    "#22C55E",
    "#713F12",
    "#F59E0B",
)
private val COLOR_VALUE_PATTERN = Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")

private fun DikcizBackgroundStyle.isEmpty(): Boolean = color == null && opacity == null

private fun DikcizBorderStyle.isEmpty(): Boolean = color == null && radiusDP == null && widths == null

private fun DikcizBoxEdges?.hasIndividualEdges(): Boolean {
    return this?.let { edges ->
        edges.bottom != null || edges.left != null || edges.right != null || edges.top != null
    } ?: false
}

private fun DikcizBoxEdges.isEmpty(): Boolean {
    return all == null && bottom == null && left == null && right == null && top == null
}

private fun DikcizTextStyle.isEmpty(): Boolean = color == null && font == null && sizeSP == null

internal class DikcizStyleEditorInputException(
    message: String,
) : IllegalArgumentException(message)
