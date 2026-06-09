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

package androidx.compose.ui.graphics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.ceil
import kotlin.math.sqrt

actual fun MeshGradientRenderer(): MeshGradientRenderer {
    return WinUIMeshGradientRenderer()
}

private class WinUIMeshGradientRenderer : MeshGradientRenderer {
    private val paint = Paint()

    override fun DrawScope.draw(
        rows: Int,
        columns: Int,
        positions: FloatArray,
        colors: IntArray,
        leftBezierOffsets: FloatArray?,
        topBezierOffsets: FloatArray?,
        rightBezierOffsets: FloatArray?,
        bottomBezierOffsets: FloatArray?,
        hasBicubicColor: Boolean,
    ) {
        val expectedPositions = (rows + 1) * (columns + 1) * 2
        val expectedColors = (rows + 1) * (columns + 1)
        require(positions.size == expectedPositions) {
            "positions array must have exactly $expectedPositions elements"
        }
        require(colors.size == expectedColors) {
            "colors array must have exactly $expectedColors elements"
        }
        requireOffsetBuffer("leftBezierOffsets", leftBezierOffsets, expectedPositions)
        requireOffsetBuffer("topBezierOffsets", topBezierOffsets, expectedPositions)
        requireOffsetBuffer("rightBezierOffsets", rightBezierOffsets, expectedPositions)
        requireOffsetBuffer("bottomBezierOffsets", bottomBezierOffsets, expectedPositions)

        inferBezierControlPointsIfRequired(
            rows,
            columns,
            positions,
            leftBezierOffsets,
            topBezierOffsets,
            rightBezierOffsets,
            bottomBezierOffsets,
        )

        val (subdivisionsU, subdivisionsV) = calculateSubdivisions(rows, columns, positions, size)
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                drawPatch(
                    row = row,
                    column = column,
                    rows = rows,
                    columns = columns,
                    subdivisionsU = subdivisionsU,
                    subdivisionsV = subdivisionsV,
                    positions = positions,
                    colors = colors,
                    leftBezierOffsets = leftBezierOffsets,
                    topBezierOffsets = topBezierOffsets,
                    rightBezierOffsets = rightBezierOffsets,
                    bottomBezierOffsets = bottomBezierOffsets,
                    hasBicubicColor = hasBicubicColor,
                )
            }
        }
    }

    private fun requireOffsetBuffer(name: String, values: FloatArray?, expectedPositions: Int) {
        require(values == null || values.size == expectedPositions) {
            "$name array must have exactly $expectedPositions elements"
        }
    }

    private fun DrawScope.drawPatch(
        row: Int,
        column: Int,
        rows: Int,
        columns: Int,
        subdivisionsU: Int,
        subdivisionsV: Int,
        positions: FloatArray,
        colors: IntArray,
        leftBezierOffsets: FloatArray?,
        topBezierOffsets: FloatArray?,
        rightBezierOffsets: FloatArray?,
        bottomBezierOffsets: FloatArray?,
        hasBicubicColor: Boolean,
    ) {
        val controlPoints = buildControlPoints(
            row,
            column,
            columns,
            positions,
            leftBezierOffsets,
            topBezierOffsets,
            rightBezierOffsets,
            bottomBezierOffsets,
        )
        val vertexCount = subdivisionsU * subdivisionsV
        val vertexPositions = ArrayList<Offset>(vertexCount)
        val textureCoordinates = ArrayList<Offset>(vertexCount)
        val vertexColors = ArrayList<Color>(vertexCount)
        val indices = ArrayList<Int>((subdivisionsU - 1) * (subdivisionsV - 1) * 6)

        for (uIndex in 0 until subdivisionsU) {
            val u = uIndex / (subdivisionsU - 1).toFloat()
            for (vIndex in 0 until subdivisionsV) {
                val v = vIndex / (subdivisionsV - 1).toFloat()
                val position = evaluateBezierSurface(controlPoints, u, v)
                vertexPositions.add(position)
                textureCoordinates.add(position)
                vertexColors.add(
                    if (hasBicubicColor) {
                        catmullRomColor(row, column, rows, columns, colors, u, v)
                    } else {
                        bilinearColor(row, column, columns, colors, u, v)
                    }
                )
            }
        }

        val stride = subdivisionsV
        for (uIndex in 0 until subdivisionsU - 1) {
            for (vIndex in 0 until subdivisionsV - 1) {
                val topLeft = uIndex * stride + vIndex
                val bottomLeft = uIndex * stride + vIndex + 1
                val topRight = (uIndex + 1) * stride + vIndex
                val bottomRight = (uIndex + 1) * stride + vIndex + 1
                indices.add(topLeft)
                indices.add(topRight)
                indices.add(bottomRight)
                indices.add(topLeft)
                indices.add(bottomRight)
                indices.add(bottomLeft)
            }
        }

        drawContext.canvas.drawVertices(
            Vertices(
                VertexMode.Triangles,
                vertexPositions,
                textureCoordinates,
                vertexColors,
                indices,
            ),
            BlendMode.SrcOver,
            paint,
        )
    }

    private fun DrawScope.buildControlPoints(
        row: Int,
        column: Int,
        columns: Int,
        positions: FloatArray,
        leftBezierOffsets: FloatArray?,
        topBezierOffsets: FloatArray?,
        rightBezierOffsets: FloatArray?,
        bottomBezierOffsets: FloatArray?,
    ): Array<Array<Offset>> {
        val topLeft = position(row, column, columns, positions)
        val topRight = position(row, column + 1, columns, positions)
        val bottomLeft = position(row + 1, column, columns, positions)
        val bottomRight = position(row + 1, column + 1, columns, positions)

        val result = Array(4) { Array(4) { Offset.Zero } }
        result[0][0] = topLeft
        result[0][3] = topRight
        result[3][0] = bottomLeft
        result[3][3] = bottomRight

        result[0][1] = topLeft + offset(row, column, columns, rightBezierOffsets)
        result[0][2] = topRight + offset(row, column + 1, columns, leftBezierOffsets)
        result[3][1] = bottomLeft + offset(row + 1, column, columns, rightBezierOffsets)
        result[3][2] = bottomRight + offset(row + 1, column + 1, columns, leftBezierOffsets)

        result[1][0] = topLeft + offset(row, column, columns, bottomBezierOffsets)
        result[2][0] = bottomLeft + offset(row + 1, column, columns, topBezierOffsets)
        result[1][3] = topRight + offset(row, column + 1, columns, bottomBezierOffsets)
        result[2][3] = bottomRight + offset(row + 1, column + 1, columns, topBezierOffsets)

        result[1][1] = result[0][1] + result[1][0] - result[0][0]
        result[1][2] = result[0][2] + result[1][3] - result[0][3]
        result[2][1] = result[2][0] + result[3][1] - result[3][0]
        result[2][2] = result[2][3] + result[3][2] - result[3][3]
        return result
    }

    private fun DrawScope.position(
        row: Int,
        column: Int,
        columns: Int,
        positions: FloatArray,
    ): Offset {
        val index = pointIndex(row, column, columns) * 2
        return Offset(positions[index] * size.width, positions[index + 1] * size.height)
    }

    private fun DrawScope.offset(
        row: Int,
        column: Int,
        columns: Int,
        offsets: FloatArray?,
    ): Offset {
        if (offsets == null) return Offset.Zero
        val index = pointIndex(row, column, columns) * 2
        return Offset(offsets[index] * size.width, offsets[index + 1] * size.height)
    }

    private fun evaluateBezierSurface(controlPoints: Array<Array<Offset>>, u: Float, v: Float): Offset {
        val uBasis = cubicBezierBasis(u)
        val vBasis = cubicBezierBasis(v)
        var x = 0f
        var y = 0f
        for (uIndex in 0..3) {
            for (vIndex in 0..3) {
                val weight = uBasis[uIndex] * vBasis[vIndex]
                x += controlPoints[vIndex][uIndex].x * weight
                y += controlPoints[vIndex][uIndex].y * weight
            }
        }
        return Offset(x, y)
    }

    private fun bilinearColor(
        row: Int,
        column: Int,
        columns: Int,
        colors: IntArray,
        u: Float,
        v: Float,
    ): Color {
        val topLeft = Color(colors[pointIndex(row, column, columns)])
        val topRight = Color(colors[pointIndex(row, column + 1, columns)])
        val bottomLeft = Color(colors[pointIndex(row + 1, column, columns)])
        val bottomRight = Color(colors[pointIndex(row + 1, column + 1, columns)])
        return lerp(lerp(topLeft, topRight, u), lerp(bottomLeft, bottomRight, u), v)
    }

    private fun catmullRomColor(
        row: Int,
        column: Int,
        rows: Int,
        columns: Int,
        colors: IntArray,
        u: Float,
        v: Float,
    ): Color {
        val samples = Array(4) { sampleRow ->
            Array(4) { sampleColumn ->
                val sourceRow = (row + sampleRow - 1).coerceIn(0, rows)
                val sourceColumn = (column + sampleColumn - 1).coerceIn(0, columns)
                Color(colors[pointIndex(sourceRow, sourceColumn, columns)]).convert(ColorSpaces.Oklab)
            }
        }
        val channels = FloatArray(4)
        for (channel in 0..3) {
            val rowValues = FloatArray(4) { sampleRow ->
                catmullRom(
                    samples[sampleRow][0].channel(channel),
                    samples[sampleRow][1].channel(channel),
                    samples[sampleRow][2].channel(channel),
                    samples[sampleRow][3].channel(channel),
                    u,
                )
            }
            val min = if (channel < 3) ColorSpaces.Oklab.getMinValue(channel) else 0f
            val max = if (channel < 3) ColorSpaces.Oklab.getMaxValue(channel) else 1f
            channels[channel] = catmullRom(rowValues[0], rowValues[1], rowValues[2], rowValues[3], v)
                .coerceIn(min, max)
        }
        return Color(
            red = channels[0],
            green = channels[1],
            blue = channels[2],
            alpha = channels[3],
            colorSpace = ColorSpaces.Oklab,
        ).convert(ColorSpaces.Srgb)
    }

    private fun Color.channel(index: Int): Float = when (index) {
        0 -> red
        1 -> green
        2 -> blue
        else -> alpha
    }

    private fun cubicBezierBasis(t: Float): FloatArray {
        val inverse = 1f - t
        val inverse2 = inverse * inverse
        val t2 = t * t
        return floatArrayOf(
            inverse2 * inverse,
            3f * inverse2 * t,
            3f * inverse * t2,
            t2 * t,
        )
    }

    private fun catmullRom(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5f * (
            2f * p1 +
                (-p0 + p2) * t +
                (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
                (-p0 + 3f * p1 - 3f * p2 + p3) * t3
            )
    }

    private fun pointIndex(row: Int, column: Int, columns: Int): Int =
        row * (columns + 1) + column

    private fun calculateSubdivisions(
        rows: Int,
        columns: Int,
        positions: FloatArray,
        size: Size,
    ): Pair<Int, Int> {
        var maxWidth = 0f
        var maxHeight = 0f
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val topLeft = position(row, column, columns, positions, size)
                val topRight = position(row, column + 1, columns, positions, size)
                val bottomLeft = position(row + 1, column, columns, positions, size)
                val bottomRight = position(row + 1, column + 1, columns, positions, size)

                val patchWidth = (topLeft.distanceTo(topRight) +
                    bottomLeft.distanceTo(bottomRight)) * 0.5f
                val patchHeight = (topLeft.distanceTo(bottomLeft) +
                    topRight.distanceTo(bottomRight)) * 0.5f
                maxWidth = maxOf(maxWidth, patchWidth)
                maxHeight = maxOf(maxHeight, patchHeight)
            }
        }

        val subdivisionsU =
            ceil(maxWidth / TargetPxPerSegment).toInt().coerceIn(MinSubdivision, MaxSubdivision)
        val subdivisionsV =
            ceil(maxHeight / TargetPxPerSegment).toInt().coerceIn(MinSubdivision, MaxSubdivision)
        return subdivisionsU to subdivisionsV
    }

    private fun position(
        row: Int,
        column: Int,
        columns: Int,
        positions: FloatArray,
        size: Size,
    ): Offset {
        val index = pointIndex(row, column, columns) * 2
        return Offset(positions[index] * size.width, positions[index + 1] * size.height)
    }

    private fun Offset.distanceTo(other: Offset): Float {
        val dx = other.x - x
        val dy = other.y - y
        return sqrt(dx * dx + dy * dy)
    }

    private companion object {
        private const val MinSubdivision = 4
        private const val MaxSubdivision = 64
        private const val TargetPxPerSegment = 8f
    }
}
