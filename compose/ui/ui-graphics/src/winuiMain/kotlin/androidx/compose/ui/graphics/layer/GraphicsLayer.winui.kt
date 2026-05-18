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

package androidx.compose.ui.graphics.layer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

actual class GraphicsLayer {
    actual var compositingStrategy: CompositingStrategy = CompositingStrategy.Auto
    actual var topLeft: IntOffset = IntOffset.Zero
    actual var size: IntSize = IntSize.Zero
        private set
    actual var pivotOffset: Offset = Offset.Unspecified
    actual var alpha: Float = 1f
    actual var scaleX: Float = 1f
    actual var scaleY: Float = 1f
    actual var translationX: Float = 0f
    actual var translationY: Float = 0f
    actual var shadowElevation: Float = 0f
    actual var ambientShadowColor: Color = Color.Black
    actual var spotShadowColor: Color = Color.Black
    actual var blendMode: BlendMode = BlendMode.SrcOver
    actual var colorFilter: ColorFilter? = null
    actual val outline: Outline
        get() = currentOutline ?: Outline.Rectangle(
            Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
        )
    actual var rotationX: Float = 0f
    actual var rotationY: Float = 0f
    actual var rotationZ: Float = 0f
    actual var cameraDistance: Float = DefaultCameraDistance
    actual var clip: Boolean = false
    actual var renderEffect: RenderEffect? = null
    actual var isReleased: Boolean = false

    private var currentOutline: Outline? = null

    actual fun setOutsets(left: Int, top: Int, right: Int, bottom: Int) = Unit

    actual fun setPathOutline(path: Path) {
        currentOutline = Outline.Generic(path)
    }

    actual fun setRoundRectOutline(topLeft: Offset, size: Size, cornerRadius: Float) {
        setRectOutline(topLeft, size)
    }

    actual fun setRectOutline(topLeft: Offset, size: Size) {
        val resolvedSize = if (size.isSpecified) {
            size
        } else {
            Size(this.size.width.toFloat(), this.size.height.toFloat())
        }
        currentOutline = Outline.Rectangle(
            Rect(
                topLeft.x,
                topLeft.y,
                topLeft.x + resolvedSize.width,
                topLeft.y + resolvedSize.height,
            )
        )
    }

    actual fun record(
        density: Density,
        layoutDirection: LayoutDirection,
        size: IntSize,
        block: DrawScope.() -> Unit,
    ) {
        this.size = size
    }

    actual suspend fun toImageBitmap(): ImageBitmap = ImageBitmap(
        width = size.width.coerceAtLeast(1),
        height = size.height.coerceAtLeast(1),
    )

    internal actual fun draw(canvas: Canvas, parentLayer: GraphicsLayer?) = Unit
}
