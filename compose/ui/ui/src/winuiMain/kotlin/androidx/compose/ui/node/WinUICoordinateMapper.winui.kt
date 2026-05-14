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

package androidx.compose.ui.node

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import microsoft.ui.xaml.UIElement
import windows.foundation.Point
import windows.graphics.PointInt32

internal class WinUICoordinateMapper(
    private val calculatePositionInWindow: (Offset) -> Offset = { it },
    private val calculateLocalPosition: (Offset) -> Offset = { it },
    private val localToScreen: (Offset) -> Offset = { it },
    private val screenToLocal: (Offset) -> Offset = { it },
) {
    fun calculatePositionInWindow(localPosition: Offset): Offset =
        calculatePositionInWindow.invoke(localPosition)

    fun calculateLocalPosition(positionInWindow: Offset): Offset =
        calculateLocalPosition.invoke(positionInWindow)

    fun localToScreen(localPosition: Offset): Offset =
        localToScreen.invoke(localPosition)

    fun screenToLocal(positionOnScreen: Offset): Offset =
        screenToLocal.invoke(positionOnScreen)

    fun localToScreen(localTransform: Matrix) {
        val screenOrigin = localToScreen(Offset.Zero)
        localTransform.translate(screenOrigin.x, screenOrigin.y)
    }

    companion object {
        fun forRoot(root: UIElement): WinUICoordinateMapper =
            WinUICoordinateMapper(
                calculatePositionInWindow = { root.calculatePositionInWindow(it) },
                calculateLocalPosition = { root.calculateLocalPosition(it) },
                localToScreen = { root.localToScreen(it) },
                screenToLocal = { root.screenToLocal(it) },
            )

        private fun UIElement.calculatePositionInWindow(localPosition: Offset): Offset {
            val transform = rootTransformToWindow() ?: return localPosition
            return transform.transformPoint(localPosition.toWinRtPoint()).toOffset()
        }

        private fun UIElement.calculateLocalPosition(positionInWindow: Offset): Offset {
            val transform = rootTransformToWindow()?.inverse ?: return positionInWindow
            return transform.transformPoint(positionInWindow.toWinRtPoint()).toOffset()
        }

        private fun UIElement.localToScreen(localPosition: Offset): Offset {
            val xamlRoot = runCatching { xamlRoot }.getOrNull() ?: return localPosition
            val positionInWindow = calculatePositionInWindow(localPosition)
            return xamlRoot.coordinateConverter
                .convertLocalToScreen(positionInWindow.toWinRtPoint())
                .toOffset()
        }

        private fun UIElement.screenToLocal(positionOnScreen: Offset): Offset {
            val xamlRoot = runCatching { xamlRoot }.getOrNull() ?: return positionOnScreen
            val positionInWindow = xamlRoot.coordinateConverter
                .convertScreenToLocal(positionOnScreen.toWinRtPointInt32())
                .toOffset()
            return calculateLocalPosition(positionInWindow)
        }

        private fun UIElement.rootTransformToWindow() = runCatching {
            transformToVisual(xamlRoot.content as UIElement)
        }.getOrNull()

        private fun Offset.toWinRtPoint(): Point = Point(x, y)

        private fun Offset.toWinRtPointInt32(): PointInt32 =
            PointInt32(x.toInt(), y.toInt())

        private fun Point.toOffset(): Offset = Offset(x, y)

        private fun PointInt32.toOffset(): Offset = Offset(x.toFloat(), y.toFloat())
    }
}
