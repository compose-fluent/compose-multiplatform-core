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

package androidx.compose.ui.platform

import androidx.compose.ui.geometry.Rect
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PictureRecorder
import org.jetbrains.skia.Rect as SkRect
import org.jetbrains.skiko.SkikoRenderDelegate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WinUIDrawRectRenderDecoratorTest {
    @Test
    fun recordsNonEmptyDrawBounds() {
        val drawRects = mutableListOf<Rect>()
        val decorator = WinUIDrawRectRenderDecorator(
            decorated = DrawRectDelegate(SkRect.makeXYWH(4f, 5f, 20f, 10f)),
            onDrawRectChange = { drawRects += it },
        )

        try {
            renderWith(decorator)
        } finally {
            decorator.close()
        }

        assertEquals(listOf(Rect(4f, 5f, 24f, 15f)), drawRects)
    }

    @Test
    fun reportsZeroBoundsAfterDrawsStop() {
        val drawRects = mutableListOf<Rect>()
        val delegate = SwitchingDrawDelegate()
        val decorator = WinUIDrawRectRenderDecorator(
            decorated = delegate,
            onDrawRectChange = { drawRects += it },
        )

        try {
            delegate.draw = true
            renderWith(decorator)
            delegate.draw = false
            renderWith(decorator)
        } finally {
            decorator.close()
        }

        assertEquals(Rect(0f, 0f, 8f, 8f), drawRects.first())
        assertEquals(Rect.Zero, drawRects.last())
    }

    @Test
    fun closedDecoratorForwardsRenderWithoutUpdatingDrawBounds() {
        val drawRects = mutableListOf<Rect>()
        val delegate = CountingDrawDelegate()
        val decorator = WinUIDrawRectRenderDecorator(
            decorated = delegate,
            onDrawRectChange = { drawRects += it },
        )

        decorator.close()
        renderWith(decorator)

        assertEquals(1, delegate.renderCount)
        assertTrue(drawRects.isEmpty())
    }
}

private fun renderWith(delegate: SkikoRenderDelegate) {
    val recorder = PictureRecorder()
    val pictureCanvas = recorder.beginRecording(SkRect.makeWH(128f, 128f), null)
    try {
        delegate.onRender(pictureCanvas, width = 128, height = 128, nanoTime = 0L)
    } finally {
        recorder.finishRecordingAsPicture().close()
        recorder.close()
    }
}

private class DrawRectDelegate(
    private val rect: SkRect,
) : SkikoRenderDelegate {
    override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
        val paint = Paint()
        try {
            canvas.drawRect(rect, paint)
        } finally {
            paint.close()
        }
    }
}

private class SwitchingDrawDelegate : SkikoRenderDelegate {
    var draw: Boolean = false

    override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
        if (draw) {
            val paint = Paint()
            try {
                canvas.drawRect(SkRect.makeXYWH(0f, 0f, 8f, 8f), paint)
            } finally {
                paint.close()
            }
        }
    }
}

private class CountingDrawDelegate : SkikoRenderDelegate {
    var renderCount: Int = 0

    override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
        renderCount += 1
        val paint = Paint()
        try {
            canvas.drawRect(SkRect.makeXYWH(0f, 0f, 8f, 8f), paint)
        } finally {
            paint.close()
        }
    }
}
