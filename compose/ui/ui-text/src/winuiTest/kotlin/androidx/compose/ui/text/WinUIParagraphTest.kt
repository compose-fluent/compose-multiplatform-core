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

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinUIParagraphTest {
    @Test
    fun paragraphMeasuresAndDrawsTextIntoImageBitmap() {
        val paragraph = Paragraph(
            text = "WinUI text",
            style = TextStyle(fontSize = 24.sp, color = Color.Black),
            constraints = Constraints(maxWidth = 200),
            density = Density(1f),
            fontFamilyResolver = createFontFamilyResolver(),
        )

        assertTrue(paragraph.minIntrinsicWidth > 0f)
        assertTrue(paragraph.height > 0f)
        assertTrue(paragraph.firstBaseline > 0f)

        val bitmap = ImageBitmap(220, 80)
        paragraph.paint(Canvas(bitmap), Color.Black, null, null)

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.readPixels(pixels)
        assertFalse(pixels.all { it == 0 })
    }

    @Test
    fun paragraphReportsLineAndSelectionGeometry() {
        val paragraph = Paragraph(
            text = "WinUI\ntext",
            style = TextStyle(fontSize = 20.sp, color = Color.Black),
            constraints = Constraints(maxWidth = 200),
            density = Density(1f),
            fontFamilyResolver = createFontFamilyResolver(),
        )

        assertEquals(2, paragraph.lineCount)
        assertEquals(0, paragraph.getLineStart(0))
        assertEquals(5, paragraph.getLineEnd(0, visibleEnd = true))
        assertEquals(6, paragraph.getLineStart(1))
        assertEquals(10, paragraph.getLineEnd(1, visibleEnd = true))
        assertTrue(paragraph.getLineBottom(1) > paragraph.getLineBottom(0))

        val firstCursor = paragraph.getCursorRect(0)
        val secondCursor = paragraph.getCursorRect(1)
        val firstGlyphBounds = paragraph.getBoundingBox(0)
        val selectionPath = paragraph.getPathForRange(0, 3)
        val boxes = FloatArray(12)

        paragraph.fillBoundingBoxes(TextRange(0, 3), boxes, arrayStart = 0)

        assertTrue(secondCursor.left > firstCursor.left)
        assertTrue(firstGlyphBounds.right > firstGlyphBounds.left)
        assertFalse(selectionPath.isEmpty)
        assertTrue(boxes.any { it > 0f })
    }

    @Test
    fun paragraphResolvesCommonWinUIFontFamilies() {
        listOf(
            FontFamily.Default,
            FontFamily.Serif,
            FontFamily.Monospace,
        ).forEach { fontFamily ->
            val paragraph = Paragraph(
                text = "WinUI font",
                style = TextStyle(
                    fontSize = 18.sp,
                    color = Color.Black,
                    fontFamily = fontFamily,
                ),
                constraints = Constraints(maxWidth = 240),
                density = Density(1f),
                fontFamilyResolver = createFontFamilyResolver(),
            )

            assertTrue(paragraph.minIntrinsicWidth > 0f)
            assertTrue(paragraph.height > 0f)
        }
    }
}
