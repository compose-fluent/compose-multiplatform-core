/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.ui.text

import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import org.jetbrains.skia.Font as SkFont
import org.jetbrains.skia.FontMetrics
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontSlant
import org.jetbrains.skia.FontStyle as SkFontStyle
import org.jetbrains.skia.FontWidth
import org.jetbrains.skia.Paint as SkPaint
import org.jetbrains.skia.Typeface as SkTypeface

internal actual fun String.findPrecedingBreak(index: Int): Int = (index - 1).coerceAtLeast(0)

internal actual fun String.findFollowingBreak(index: Int): Int = (index + 1).coerceAtMost(length)

actual sealed interface Paragraph {
    actual val width: Float
    actual val height: Float
    actual val minIntrinsicWidth: Float
    actual val maxIntrinsicWidth: Float
    actual val firstBaseline: Float
    actual val lastBaseline: Float
    actual val didExceedMaxLines: Boolean
    actual val lineCount: Int
    actual val placeholderRects: List<Rect?>
    actual fun getPathForRange(start: Int, end: Int): Path
    actual fun getCursorRect(offset: Int): Rect
    actual fun getLineLeft(lineIndex: Int): Float
    actual fun getLineRight(lineIndex: Int): Float
    actual fun getLineTop(lineIndex: Int): Float
    actual fun getLineBaseline(lineIndex: Int): Float
    actual fun getLineBottom(lineIndex: Int): Float
    actual fun getLineHeight(lineIndex: Int): Float
    actual fun getLineWidth(lineIndex: Int): Float
    actual fun getLineStart(lineIndex: Int): Int
    actual fun getLineEnd(lineIndex: Int, visibleEnd: Boolean): Int
    actual fun isLineEllipsized(lineIndex: Int): Boolean
    actual fun getLineForOffset(offset: Int): Int
    actual fun getHorizontalPosition(offset: Int, usePrimaryDirection: Boolean): Float
    actual fun getParagraphDirection(offset: Int): ResolvedTextDirection
    actual fun getBidiRunDirection(offset: Int): ResolvedTextDirection
    actual fun getLineForVerticalPosition(vertical: Float): Int
    actual fun getOffsetForPosition(position: Offset): Int
    actual fun getRangeForRect(
        rect: Rect,
        granularity: TextGranularity,
        inclusionStrategy: TextInclusionStrategy,
    ): TextRange
    actual fun getBoundingBox(offset: Int): Rect
    actual fun fillBoundingBoxes(range: TextRange, array: FloatArray, arrayStart: Int)
    actual fun getWordBoundary(offset: Int): TextRange
    actual fun paint(canvas: Canvas, color: Color, shadow: Shadow?, textDecoration: TextDecoration?)
    actual fun paint(
        canvas: Canvas,
        color: Color,
        shadow: Shadow?,
        textDecoration: TextDecoration?,
        drawStyle: DrawStyle?,
        blendMode: BlendMode,
    )
    actual fun paint(
        canvas: Canvas,
        brush: Brush,
        alpha: Float,
        shadow: Shadow?,
        textDecoration: TextDecoration?,
        drawStyle: DrawStyle?,
        blendMode: BlendMode,
    )
}

internal class EmptyWinUIParagraph(
    override val width: Float = 0f,
    override val height: Float = 0f,
) : Paragraph {
    override val minIntrinsicWidth: Float = width
    override val maxIntrinsicWidth: Float = width
    override val firstBaseline: Float = 0f
    override val lastBaseline: Float = 0f
    override val didExceedMaxLines: Boolean = false
    override val lineCount: Int = 1
    override val placeholderRects: List<Rect?> = emptyList()
    override fun getPathForRange(start: Int, end: Int): Path = Path()
    override fun getCursorRect(offset: Int): Rect = Rect.Zero
    override fun getLineLeft(lineIndex: Int): Float = 0f
    override fun getLineRight(lineIndex: Int): Float = width
    override fun getLineTop(lineIndex: Int): Float = 0f
    override fun getLineBaseline(lineIndex: Int): Float = 0f
    override fun getLineBottom(lineIndex: Int): Float = height
    override fun getLineHeight(lineIndex: Int): Float = height
    override fun getLineWidth(lineIndex: Int): Float = width
    override fun getLineStart(lineIndex: Int): Int = 0
    override fun getLineEnd(lineIndex: Int, visibleEnd: Boolean): Int = 0
    override fun isLineEllipsized(lineIndex: Int): Boolean = false
    override fun getLineForOffset(offset: Int): Int = 0
    override fun getHorizontalPosition(offset: Int, usePrimaryDirection: Boolean): Float = 0f
    override fun getParagraphDirection(offset: Int): ResolvedTextDirection = ResolvedTextDirection.Ltr
    override fun getBidiRunDirection(offset: Int): ResolvedTextDirection = ResolvedTextDirection.Ltr
    override fun getLineForVerticalPosition(vertical: Float): Int = 0
    override fun getOffsetForPosition(position: Offset): Int = 0
    override fun getRangeForRect(
        rect: Rect,
        granularity: TextGranularity,
        inclusionStrategy: TextInclusionStrategy,
    ): TextRange = TextRange.Zero
    override fun getBoundingBox(offset: Int): Rect = Rect.Zero
    override fun fillBoundingBoxes(range: TextRange, array: FloatArray, arrayStart: Int) = Unit
    override fun getWordBoundary(offset: Int): TextRange = TextRange(offset, offset)
    override fun paint(canvas: Canvas, color: Color, shadow: Shadow?, textDecoration: TextDecoration?) = Unit
    override fun paint(
        canvas: Canvas,
        color: Color,
        shadow: Shadow?,
        textDecoration: TextDecoration?,
        drawStyle: DrawStyle?,
        blendMode: BlendMode,
    ) = Unit
    override fun paint(
        canvas: Canvas,
        brush: Brush,
        alpha: Float,
        shadow: Shadow?,
        textDecoration: TextDecoration?,
        drawStyle: DrawStyle?,
        blendMode: BlendMode,
    ) = Unit
}

internal class WinUIParagraph(
    private val text: String,
    private val style: TextStyle,
    private val density: Density,
    private val typeface: SkTypeface,
    override val width: Float,
    private val maxLines: Int,
) : Paragraph {
    private val lines: List<String> = text.split('\n').let { split ->
        split.ifEmpty { listOf("") }.take(maxLines.coerceAtLeast(1))
    }
    private val fontSize = style.fontSize.toWinUIPx(density)
    private val font = SkFont(typeface, fontSize)
    private val metrics: FontMetrics = font.metrics
    private val lineHeightPx = style.lineHeight
        .takeUnless { it.isUnspecified }
        ?.toWinUIPx(density)
        ?.coerceAtLeast(1f)
        ?: (metrics.descent - metrics.ascent + metrics.leading).coerceAtLeast(fontSize)
    private val measuredLineWidths = lines.map(::measureText)

    override val height: Float = lineHeightPx * lines.size
    override val minIntrinsicWidth: Float = measuredLineWidths.maxOrNull() ?: 0f
    override val maxIntrinsicWidth: Float = minIntrinsicWidth
    override val firstBaseline: Float = -metrics.ascent
    override val lastBaseline: Float = firstBaseline + lineHeightPx * (lines.size - 1)
    override val didExceedMaxLines: Boolean = text.count { it == '\n' } + 1 > lines.size
    override val lineCount: Int = lines.size
    override val placeholderRects: List<Rect?> = emptyList()

    override fun getPathForRange(start: Int, end: Int): Path = Path()
    override fun getCursorRect(offset: Int): Rect {
        val line = getLineForOffset(offset)
        val lineStart = getLineStart(line)
        val x = measureText(lines[line].take((offset - lineStart).coerceIn(0, lines[line].length)))
        val top = getLineTop(line)
        return Rect(x, top, x + 1f, top + lineHeightPx)
    }

    override fun getLineLeft(lineIndex: Int): Float = 0f
    override fun getLineRight(lineIndex: Int): Float = getLineWidth(lineIndex)
    override fun getLineTop(lineIndex: Int): Float = lineIndex.coerceLineIndex() * lineHeightPx
    override fun getLineBaseline(lineIndex: Int): Float = firstBaseline + lineIndex.coerceLineIndex() * lineHeightPx
    override fun getLineBottom(lineIndex: Int): Float = getLineTop(lineIndex) + lineHeightPx
    override fun getLineHeight(lineIndex: Int): Float = lineHeightPx
    override fun getLineWidth(lineIndex: Int): Float = measuredLineWidths[lineIndex.coerceLineIndex()]
    override fun getLineStart(lineIndex: Int): Int {
        val target = lineIndex.coerceLineIndex()
        var start = 0
        for (i in 0 until target) {
            start += lines[i].length + 1
        }
        return start
    }

    override fun getLineEnd(lineIndex: Int, visibleEnd: Boolean): Int =
        getLineStart(lineIndex) + lines[lineIndex.coerceLineIndex()].length

    override fun isLineEllipsized(lineIndex: Int): Boolean = false
    override fun getLineForOffset(offset: Int): Int {
        var start = 0
        lines.forEachIndexed { index, line ->
            val end = start + line.length
            if (offset <= end) return index
            start = end + 1
        }
        return lines.lastIndex
    }

    override fun getHorizontalPosition(offset: Int, usePrimaryDirection: Boolean): Float =
        getCursorRect(offset).left

    override fun getParagraphDirection(offset: Int): ResolvedTextDirection = ResolvedTextDirection.Ltr
    override fun getBidiRunDirection(offset: Int): ResolvedTextDirection = ResolvedTextDirection.Ltr
    override fun getLineForVerticalPosition(vertical: Float): Int =
        (vertical / lineHeightPx).toInt().coerceLineIndex()

    override fun getOffsetForPosition(position: Offset): Int {
        val lineIndex = getLineForVerticalPosition(position.y)
        val line = lines[lineIndex]
        var bestOffset = 0
        var bestDistance = Float.POSITIVE_INFINITY
        for (i in 0..line.length) {
            val distance = kotlin.math.abs(measureText(line.take(i)) - position.x)
            if (distance < bestDistance) {
                bestDistance = distance
                bestOffset = i
            }
        }
        return getLineStart(lineIndex) + bestOffset
    }

    override fun getRangeForRect(
        rect: Rect,
        granularity: TextGranularity,
        inclusionStrategy: TextInclusionStrategy,
    ): TextRange = TextRange(getOffsetForPosition(rect.topLeft), getOffsetForPosition(rect.bottomRight))

    override fun getBoundingBox(offset: Int): Rect = getCursorRect(offset)
    override fun fillBoundingBoxes(range: TextRange, array: FloatArray, arrayStart: Int) {
        var index = arrayStart
        for (offset in range.min until range.max) {
            if (index + 3 >= array.size) return
            val rect = getCursorRect(offset)
            array[index++] = rect.left
            array[index++] = rect.top
            array[index++] = rect.right
            array[index++] = rect.bottom
        }
    }

    override fun getWordBoundary(offset: Int): TextRange {
        val bounded = offset.coerceIn(0, text.length)
        val start = text.lastIndexOf(' ', (bounded - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val end = text.indexOf(' ', bounded).let { if (it < 0) text.length else it }
        return TextRange(start, end)
    }

    override fun paint(canvas: Canvas, color: Color, shadow: Shadow?, textDecoration: TextDecoration?) =
        paint(canvas, color, shadow, textDecoration, null, BlendMode.SrcOver)

    override fun paint(
        canvas: Canvas,
        color: Color,
        shadow: Shadow?,
        textDecoration: TextDecoration?,
        drawStyle: DrawStyle?,
        blendMode: BlendMode,
    ) {
        val paint = SkPaint().apply {
            this.color = color.takeIf { it.isSpecified }?.toArgb()
                ?: style.color.takeIf { it.isSpecified }?.toArgb()
                ?: Color.Black.toArgb()
            isAntiAlias = true
        }
        lines.forEachIndexed { index, line ->
            canvas.skiaCanvas.drawString(line, 0f, getLineBaseline(index), font, paint)
        }
    }

    override fun paint(
        canvas: Canvas,
        brush: Brush,
        alpha: Float,
        shadow: Shadow?,
        textDecoration: TextDecoration?,
        drawStyle: DrawStyle?,
        blendMode: BlendMode,
    ) = paint(canvas, style.color.copy(alpha = alpha), shadow, textDecoration, drawStyle, blendMode)

    private fun measureText(value: String): Float = font.measureText(value).width
    private fun Int.coerceLineIndex(): Int = coerceIn(0, lines.lastIndex)
}

internal fun TextStyle.winUITypeface(): SkTypeface {
    val weight = fontWeight ?: FontWeight.Normal
    val skStyle = SkFontStyle(
        weight = weight.weight,
        width = FontWidth.NORMAL,
        slant = if (fontStyle == FontStyle.Italic) FontSlant.ITALIC else FontSlant.UPRIGHT,
    )
    val familyName = when (fontFamily) {
        androidx.compose.ui.text.font.FontFamily.Serif -> "Times New Roman"
        androidx.compose.ui.text.font.FontFamily.Monospace -> "Consolas"
        androidx.compose.ui.text.font.FontFamily.Cursive -> "Comic Sans MS"
        else -> "Segoe UI"
    }
    return FontMgr.default.matchFamilyStyle(familyName, skStyle)
        ?: FontMgr.default.legacyMakeTypeface(familyName, skStyle)
        ?: FontMgr.default.matchFamilyStyle("Arial", skStyle)
        ?: error("Unable to load WinUI paragraph font '$familyName'.")
}

private fun TextUnit.toWinUIPx(density: Density): Float =
    if (isUnspecified) {
        with(density) { 16.sp.toPx() }
    } else {
        with(density) { toPx() }
    }

actual class PlatformTextStyle {
    actual val spanStyle: PlatformSpanStyle?
    actual val paragraphStyle: PlatformParagraphStyle?

    constructor(spanStyle: PlatformSpanStyle?, paragraphStyle: PlatformParagraphStyle?) {
        this.spanStyle = spanStyle
        this.paragraphStyle = paragraphStyle
    }
}

internal actual fun createPlatformTextStyle(
    spanStyle: PlatformSpanStyle?,
    paragraphStyle: PlatformParagraphStyle?,
): PlatformTextStyle = PlatformTextStyle(spanStyle, paragraphStyle)

actual class PlatformParagraphStyle {
    actual companion object {
        actual val Default: PlatformParagraphStyle = PlatformParagraphStyle()
    }

    actual fun merge(other: PlatformParagraphStyle?): PlatformParagraphStyle = other ?: this
}

actual class PlatformSpanStyle {
    actual companion object {
        actual val Default: PlatformSpanStyle = PlatformSpanStyle()
    }

    actual fun merge(other: PlatformSpanStyle?): PlatformSpanStyle = other ?: this
}

actual fun lerp(
    start: PlatformParagraphStyle,
    stop: PlatformParagraphStyle,
    fraction: Float,
): PlatformParagraphStyle = if (fraction < 0.5f) start else stop

actual fun lerp(start: PlatformSpanStyle, stop: PlatformSpanStyle, fraction: Float): PlatformSpanStyle =
    if (fraction < 0.5f) start else stop

internal actual val PlatformParagraphStyle.Companion.Saver: Saver<PlatformParagraphStyle, Any>
    get() = Saver(save = { 0 }, restore = { PlatformParagraphStyle.Default })

internal actual val LineBreak.Companion.Saver: Saver<LineBreak, Any>
    get() = Saver(save = { it.mask }, restore = { LineBreak.Simple })

internal actual val TextMotion.Companion.Saver: Saver<TextMotion, Any>
    get() = Saver(save = { 0 }, restore = { TextMotion.Static })
