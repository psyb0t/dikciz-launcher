package org.fossify.home.dikciz

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.widget.TextView
import java.io.File
import org.fossify.home.R

internal class DikcizStyleRenderer(
    private val context: Context,
    private val fontDirectory: File,
    private val onFontFallback: (DikcizFontSelection) -> Unit,
) {
    private val typefaceCache = mutableMapOf<DikcizFontSelection, Typeface>()

    fun resolveWidgetFrameStyle(
        style: DikcizStyle,
        isEnabled: Boolean,
        defaultCornerRadiusPixels: Float,
    ): DikcizResolvedFrameStyle {
        val defaultBackgroundColor = context.getColor(
            if (isEnabled) {
                R.color.dikciz_widget_background
            } else {
                R.color.dikciz_widget_disabled_background
            },
        )
        return resolveFrameStyle(
            style = style,
            defaultCornerRadiusPixels = defaultCornerRadiusPixels,
            defaultBackgroundColor = defaultBackgroundColor,
            defaultBorderColor = context.getColor(R.color.dikciz_widget_border),
            defaultBorderWidthDP = DEFAULT_BORDER_WIDTH_DP,
        )
    }

    fun resolvePageFrameStyle(
        style: DikcizStyle,
        defaultCornerRadiusPixels: Float,
    ): DikcizResolvedFrameStyle {
        return resolveFrameStyle(
            style = style,
            defaultCornerRadiusPixels = defaultCornerRadiusPixels,
            defaultBackgroundColor = Color.TRANSPARENT,
            defaultBorderColor = Color.TRANSPARENT,
            defaultBorderWidthDP = NO_SPACING_PIXELS,
        )
    }

    private fun resolveFrameStyle(
        style: DikcizStyle,
        defaultCornerRadiusPixels: Float,
        defaultBackgroundColor: Int,
        defaultBorderColor: Int,
        defaultBorderWidthDP: Int,
    ): DikcizResolvedFrameStyle {
        val backgroundColor = style.background?.color?.let(::parseCssColor) ?: defaultBackgroundColor
        val opacity = style.background?.opacity ?: FULL_OPACITY
        return DikcizResolvedFrameStyle(
            backgroundColor = applyOpacity(backgroundColor, opacity),
            borderColor = style.border?.color?.let(::parseCssColor) ?: defaultBorderColor,
            borderWidths = resolveEdges(style.border?.widths, defaultBorderWidthDP),
            cornerRadiusPixels = style.border?.radiusDP
                ?.let(::densityPixels)
                ?.toFloat()
                ?: defaultCornerRadiusPixels,
            margin = resolveEdges(style.margin, NO_SPACING_PIXELS),
            padding = resolveEdges(style.padding, NO_SPACING_PIXELS),
        )
    }

    fun applyMargins(layoutParams: ViewGroup.MarginLayoutParams, style: DikcizResolvedFrameStyle) {
        layoutParams.leftMargin = style.margin.left
        layoutParams.topMargin = style.margin.top
        layoutParams.rightMargin = style.margin.right
        layoutParams.bottomMargin = style.margin.bottom
    }

    fun applyTextStyle(textView: TextView, style: DikcizStyle) {
        style.text?.color?.let { textView.setTextColor(parseCssColor(it)) }
        style.text?.sizeSP?.let { textView.textSize = it.toFloat() }
        style.text?.font?.let { font ->
            textView.typeface = resolveTypeface(font)
        }
    }

    fun typefaceForPreview(font: DikcizFontSelection): Typeface = resolveTypeface(font)

    fun clearTypefaceCache() {
        typefaceCache.clear()
    }

    fun createBackground(style: DikcizResolvedFrameStyle): Drawable {
        return DikcizStyledFrameDrawable(style)
    }

    private fun resolveTypeface(font: DikcizFontSelection): Typeface {
        return typefaceCache.getOrPut(font) {
            loadTypeface(font) ?: run {
                onFontFallback(font)
                Typeface.DEFAULT
            }
        }
    }

    private fun loadTypeface(font: DikcizFontSelection): Typeface? {
        return try {
            when (font.source) {
                DikcizFontSource.Bundled -> {
                    val bundledFont = DikcizBundledFont.fromPersistedValue(font.id) ?: return null
                    Typeface.createFromAsset(context.assets, "$FONT_ASSET_DIRECTORY/${bundledFont.assetFileName}")
                }

                DikcizFontSource.Local -> {
                    val fontFile = File(fontDirectory, font.id)
                    if (!fontFile.isFile) {
                        return null
                    }
                    Typeface.createFromFile(fontFile)
                }

                DikcizFontSource.System -> {
                    val systemFont = DikcizSystemFont.fromPersistedValue(font.id) ?: return null
                    Typeface.create(systemFont.persistedValue, Typeface.NORMAL)
                }
            }
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun resolveEdges(edges: DikcizBoxEdges?, defaultValue: Int): DikcizResolvedEdges {
        return DikcizResolvedEdges(
            left = densityPixels(edges?.resolveLeft(defaultValue) ?: defaultValue),
            top = densityPixels(edges?.resolveTop(defaultValue) ?: defaultValue),
            right = densityPixels(edges?.resolveRight(defaultValue) ?: defaultValue),
            bottom = densityPixels(edges?.resolveBottom(defaultValue) ?: defaultValue),
        )
    }

    private fun densityPixels(valueDP: Int): Int {
        return (valueDP * context.resources.displayMetrics.density).toInt()
    }

    private fun parseCssColor(color: String): Int {
        if (color.length != RGBA_COLOR_LENGTH) {
            return Color.parseColor(color)
        }
        return Color.parseColor("#${color.substring(RGB_COLOR_OFFSET)}${color.substring(HEX_PREFIX_LENGTH, RGB_COLOR_OFFSET)}")
    }

    private fun applyOpacity(color: Int, opacity: Double): Int {
        val alpha = (Color.alpha(color) * opacity).toInt().coerceIn(MINIMUM_COLOR_ALPHA, MAXIMUM_COLOR_ALPHA)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private companion object {
        const val DEFAULT_BORDER_WIDTH_DP = 1
        const val FONT_ASSET_DIRECTORY = "fonts"
        const val FULL_OPACITY = 1.0
        const val HEX_PREFIX_LENGTH = 1
        const val MAXIMUM_COLOR_ALPHA = 255
        const val MINIMUM_COLOR_ALPHA = 0
        const val NO_SPACING_PIXELS = 0
        const val RGBA_COLOR_LENGTH = 9
        const val RGB_COLOR_OFFSET = 7
    }
}

internal data class DikcizResolvedFrameStyle(
    val backgroundColor: Int,
    val borderColor: Int,
    val borderWidths: DikcizResolvedEdges,
    val cornerRadiusPixels: Float,
    val margin: DikcizResolvedEdges,
    val padding: DikcizResolvedEdges,
)

internal data class DikcizResolvedEdges(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    companion object {
        val ZERO = DikcizResolvedEdges(
            left = 0,
            top = 0,
            right = 0,
            bottom = 0,
        )
    }
}

private class DikcizStyledFrameDrawable(
    private val frameStyle: DikcizResolvedFrameStyle,
) : Drawable() {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = frameStyle.backgroundColor }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = frameStyle.borderColor }
    private val backgroundBaseAlpha = Color.alpha(frameStyle.backgroundColor)
    private val borderBaseAlpha = Color.alpha(frameStyle.borderColor)

    override fun draw(canvas: Canvas) {
        val frame = RectF(bounds)
        val cornerRadius = frameStyle.cornerRadiusPixels.coerceIn(
            MINIMUM_CORNER_RADIUS_PIXELS,
            minOf(frame.width(), frame.height()) / HALF_SIZE_DIVISOR,
        )
        canvas.drawRoundRect(
            frame,
            cornerRadius,
            cornerRadius,
            backgroundPaint,
        )
        drawBorder(canvas, frame, cornerRadius)
    }

    override fun setAlpha(alpha: Int) {
        backgroundPaint.alpha = scaledAlpha(backgroundBaseAlpha, alpha)
        borderPaint.alpha = scaledAlpha(borderBaseAlpha, alpha)
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        backgroundPaint.colorFilter = colorFilter
        borderPaint.colorFilter = colorFilter
    }

    @Deprecated("Android Drawable API")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private fun drawBorder(canvas: Canvas, frame: RectF, cornerRadius: Float) {
        val borderWidths = frameStyle.borderWidths
        if (borderWidths == DikcizResolvedEdges.ZERO) {
            return
        }
        val innerFrame = RectF(
            frame.left + borderWidths.left,
            frame.top + borderWidths.top,
            frame.right - borderWidths.right,
            frame.bottom - borderWidths.bottom,
        )
        val maximumBorderWidth = maxOf(
            borderWidths.bottom,
            borderWidths.left,
            borderWidths.right,
            borderWidths.top,
        ).toFloat()
        val innerCornerRadius = (cornerRadius - maximumBorderWidth)
            .coerceAtLeast(MINIMUM_CORNER_RADIUS_PIXELS)
        val borderPath = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRoundRect(frame, cornerRadius, cornerRadius, Path.Direction.CW)
            if (innerFrame.width() > NO_BORDER_PIXELS && innerFrame.height() > NO_BORDER_PIXELS) {
                addRoundRect(innerFrame, innerCornerRadius, innerCornerRadius, Path.Direction.CW)
            }
        }
        canvas.drawPath(borderPath, borderPaint)
    }

    private fun scaledAlpha(baseAlpha: Int, drawableAlpha: Int): Int {
        return (baseAlpha * drawableAlpha / MAXIMUM_ALPHA).coerceIn(MINIMUM_ALPHA, MAXIMUM_ALPHA)
    }

    private companion object {
        const val HALF_SIZE_DIVISOR = 2F
        const val MAXIMUM_ALPHA = 255
        const val MINIMUM_ALPHA = 0
        const val MINIMUM_CORNER_RADIUS_PIXELS = 0F
        const val NO_BORDER_PIXELS = 0
    }
}
