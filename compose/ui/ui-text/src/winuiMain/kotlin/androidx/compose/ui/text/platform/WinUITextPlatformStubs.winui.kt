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

package androidx.compose.ui.text.platform

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.EmptyWinUIParagraph
import androidx.compose.ui.text.MultiParagraph
import androidx.compose.ui.text.Paragraph
import androidx.compose.ui.text.ParagraphIntrinsics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlatformStringDelegate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density

@PublishedApi internal actual class SynchronizedObject

internal actual inline fun makeSynchronizedObject(ref: Any?): SynchronizedObject = SynchronizedObject()

@PublishedApi internal actual inline fun <R> synchronized(
    lock: SynchronizedObject,
    block: () -> R,
): R = block()

internal actual fun ActualStringDelegate(): PlatformStringDelegate = WinUIStringDelegate

private object WinUIStringDelegate : PlatformStringDelegate {
    override fun toUpperCase(string: String, locale: Locale): String = string.uppercase()
    override fun toLowerCase(string: String, locale: Locale): String = string.lowercase()
    override fun capitalize(string: String, locale: Locale): String =
        string.replaceFirstChar { it.uppercase() }
    override fun decapitalize(string: String, locale: Locale): String =
        string.replaceFirstChar { it.lowercase() }
}

internal actual fun ActualParagraph(
    text: String,
    style: TextStyle,
    annotations: List<AnnotatedString.Range<out AnnotatedString.Annotation>>,
    placeholders: List<AnnotatedString.Range<Placeholder>>,
    maxLines: Int,
    ellipsis: Boolean,
    width: Float,
    density: Density,
    resourceLoader: Font.ResourceLoader,
): Paragraph = EmptyWinUIParagraph(width = width)

internal actual fun ActualParagraph(
    text: String,
    style: TextStyle,
    annotations: List<AnnotatedString.Range<out AnnotatedString.Annotation>>,
    placeholders: List<AnnotatedString.Range<Placeholder>>,
    maxLines: Int,
    overflow: TextOverflow,
    constraints: Constraints,
    density: Density,
    fontFamilyResolver: FontFamily.Resolver,
): Paragraph = EmptyWinUIParagraph(width = constraints.maxWidth.toFloat())

internal actual fun ActualParagraph(
    paragraphIntrinsics: ParagraphIntrinsics,
    maxLines: Int,
    overflow: TextOverflow,
    constraints: Constraints,
): Paragraph = EmptyWinUIParagraph(width = constraints.maxWidth.toFloat())

internal actual fun ActualParagraphIntrinsics(
    text: String,
    style: TextStyle,
    annotations: List<AnnotatedString.Range<out AnnotatedString.Annotation>>,
    placeholders: List<AnnotatedString.Range<Placeholder>>,
    density: Density,
    fontFamilyResolver: FontFamily.Resolver,
): ParagraphIntrinsics = object : ParagraphIntrinsics {
    override val minIntrinsicWidth: Float = 0f
    override val maxIntrinsicWidth: Float = 0f
    override val hasStaleResolvedFonts: Boolean = false
}

internal actual fun MultiParagraph.drawMultiParagraph(
    canvas: Canvas,
    brush: Brush,
    alpha: Float,
    shadow: Shadow?,
    decoration: TextDecoration?,
    drawStyle: DrawStyle?,
    blendMode: BlendMode,
) = Unit
