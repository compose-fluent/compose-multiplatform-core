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

import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.ReusableGraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.isIdentity
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.invertTo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

internal class WinUIOwnerLayer(
    private var drawBlock: (canvas: Canvas, parentLayer: GraphicsLayer?) -> Unit,
    private var invalidateParentLayer: () -> Unit,
) : OwnedLayer {
    private val matrix = Matrix()
    private val inverseMatrix = Matrix()
    private var isInverseMatrixDirty = true
    private var isInverseMatrixValid = true
    private var isIdentity = true
    private var isDestroyed = false
    private var position = IntOffset.Zero
    private var size = IntSize.Zero
    private var transformOrigin = TransformOrigin.Center
    private var translationX = 0f
    private var translationY = 0f
    private var rotationX = 0f
    private var rotationY = 0f
    private var rotationZ = 0f
    private var scaleX = 1f
    private var scaleY = 1f
    private var clip = false
    private var isDirty = true
    private var displayListUpdateCount = 0

    override fun updateLayerProperties(scope: ReusableGraphicsLayerScope) {
        transformOrigin = scope.transformOrigin
        translationX = scope.translationX
        translationY = scope.translationY
        rotationX = scope.rotationX
        rotationY = scope.rotationY
        rotationZ = scope.rotationZ
        scaleX = scope.scaleX
        scaleY = scope.scaleY
        clip = scope.clip
        updateMatrix()
        invalidate()
    }

    override fun isInLayer(position: Offset): Boolean {
        if (!clip) return true
        return position.x >= 0f &&
            position.y >= 0f &&
            position.x < size.width &&
            position.y < size.height
    }

    override fun move(position: IntOffset) {
        if (position == this.position) return
        this.position = position
        invalidateParentLayer()
    }

    override fun resize(size: IntSize) {
        if (size == this.size) return
        this.size = size
        updateMatrix()
        invalidate()
    }

    override fun drawLayer(canvas: Canvas, parentLayer: GraphicsLayer?) {
        updateDisplayList()
        canvas.save()
        canvas.concat(matrix)
        canvas.translate(position.x.toFloat(), position.y.toFloat())
        if (clip) {
            canvas.clipRect(Rect(0f, 0f, size.width.toFloat(), size.height.toFloat()))
        }
        drawBlock(canvas, parentLayer)
        canvas.restore()
    }

    override fun updateDisplayList() {
        if (!isDirty) return
        displayListUpdateCount++
        isDirty = false
    }

    override fun invalidate() {
        if (!isDestroyed) {
            isDirty = true
            invalidateParentLayer()
        }
    }

    override fun destroy() {
        isDestroyed = true
        isDirty = false
    }

    override fun mapOffset(point: Offset, inverse: Boolean): Offset {
        val targetMatrix = if (inverse) {
            getInverseMatrix() ?: return Offset.Infinite
        } else {
            matrix
        }
        return if (isIdentity) point else targetMatrix.map(point)
    }

    override fun mapBounds(rect: MutableRect, inverse: Boolean) {
        val targetMatrix = if (inverse) getInverseMatrix() else matrix
        if (!isIdentity) {
            if (targetMatrix == null) {
                rect.set(0f, 0f, 0f, 0f)
            } else {
                targetMatrix.map(rect)
            }
        }
    }

    override fun reuseLayer(
        drawBlock: (canvas: Canvas, parentLayer: GraphicsLayer?) -> Unit,
        invalidateParentLayer: () -> Unit,
    ) {
        this.drawBlock = drawBlock
        this.invalidateParentLayer = invalidateParentLayer
        isDestroyed = false
        resetLayerState()
        invalidate()
    }

    override fun transform(matrix: Matrix) {
        matrix.timesAssign(this.matrix)
    }

    override val underlyingMatrix: Matrix get() = matrix

    override var frameRate: Float = 0f

    override var isFrameRateFromParent: Boolean = false

    override fun inverseTransform(matrix: Matrix) {
        getInverseMatrix()?.let { matrix.timesAssign(it) }
    }

    private fun updateMatrix() {
        val pivotX = transformOrigin.pivotFractionX * size.width
        val pivotY = transformOrigin.pivotFractionY * size.height
        matrix.resetToPivotedTransform(
            pivotX = pivotX,
            pivotY = pivotY,
            translationX = translationX,
            translationY = translationY,
            rotationX = rotationX,
            rotationY = rotationY,
            rotationZ = rotationZ,
            scaleX = scaleX,
            scaleY = scaleY,
        )
        isIdentity = matrix.isIdentity()
        isInverseMatrixDirty = true
    }

    private fun getInverseMatrix(): Matrix? {
        if (!isInverseMatrixDirty) {
            return if (isInverseMatrixValid) inverseMatrix else null
        }
        isInverseMatrixDirty = false
        if (isIdentity) {
            inverseMatrix.reset()
            isInverseMatrixValid = true
            return inverseMatrix
        }
        isInverseMatrixValid = matrix.invertTo(inverseMatrix)
        return if (isInverseMatrixValid) inverseMatrix else null
    }

    private fun resetLayerState() {
        position = IntOffset.Zero
        size = IntSize.Zero
        transformOrigin = TransformOrigin.Center
        translationX = 0f
        translationY = 0f
        rotationX = 0f
        rotationY = 0f
        rotationZ = 0f
        scaleX = 1f
        scaleY = 1f
        clip = false
        frameRate = 0f
        isFrameRateFromParent = false
        matrix.reset()
        inverseMatrix.reset()
        isIdentity = true
        isInverseMatrixDirty = false
        isInverseMatrixValid = true
        isDirty = true
        displayListUpdateCount = 0
    }

    internal fun stateForTest(): WinUIOwnerLayerState =
        WinUIOwnerLayerState(
            isDirty = isDirty,
            isDestroyed = isDestroyed,
            displayListUpdateCount = displayListUpdateCount,
            position = position,
            size = size,
        )
}

internal data class WinUIOwnerLayerState(
    val isDirty: Boolean,
    val isDestroyed: Boolean,
    val displayListUpdateCount: Int,
    val position: IntOffset,
    val size: IntSize,
)
