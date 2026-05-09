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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextMotion

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
