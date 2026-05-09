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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

@Deprecated("Use direct reference to platform type instead of typealias")
actual class NativePaint

actual fun Paint(): Paint = WinUIPaint()

private class WinUIPaint : Paint {
    @Suppress("DEPRECATION")
    override fun asFrameworkPaint(): NativePaint = NativePaint()

    override var alpha: Float = DefaultAlpha
    override var isAntiAlias: Boolean = true
    override var color: Color = Color.Black
    override var blendMode: BlendMode = BlendMode.SrcOver
    override var style: PaintingStyle = PaintingStyle.Fill
    override var strokeWidth: Float = 0f
    override var strokeCap: StrokeCap = StrokeCap.Butt
    override var strokeJoin: StrokeJoin = StrokeJoin.Miter
    override var strokeMiterLimit: Float = 4f
    override var filterQuality: FilterQuality = FilterQuality.Low
    override var shader: Shader? = null
    override var colorFilter: ColorFilter? = null
    override var pathEffect: PathEffect? = null
}

actual fun BlendMode.isSupported(): Boolean = true

actual fun TileMode.isSupported(): Boolean = true

@Deprecated("Use direct reference to platform type instead of typealias")
actual class NativeCanvas

internal actual fun ActualCanvas(image: ImageBitmap): Canvas = WinUICanvas()

private class WinUICanvas : Canvas {
    override fun save() = Unit
    override fun restore() = Unit
    override fun saveLayer(bounds: Rect, paint: Paint) = Unit
    override fun translate(dx: Float, dy: Float) = Unit
    override fun scale(sx: Float, sy: Float) = Unit
    override fun rotate(degrees: Float) = Unit
    override fun skew(sx: Float, sy: Float) = Unit
    override fun concat(matrix: Matrix) = Unit
    override fun clipRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        clipOp: ClipOp,
    ) = Unit
    override fun clipPath(path: Path, clipOp: ClipOp) = Unit
    override fun drawLine(p1: Offset, p2: Offset, paint: Paint) = Unit
    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
    override fun drawRoundRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radiusX: Float,
        radiusY: Float,
        paint: Paint,
    ) = Unit
    override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
    override fun drawCircle(center: Offset, radius: Float, paint: Paint) = Unit
    override fun drawArc(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        startAngle: Float,
        sweepAngle: Float,
        useCenter: Boolean,
        paint: Paint,
    ) = Unit
    override fun drawPath(path: Path, paint: Paint) = Unit
    override fun drawImage(image: ImageBitmap, topLeftOffset: Offset, paint: Paint) = Unit
    override fun drawImageRect(
        image: ImageBitmap,
        srcOffset: IntOffset,
        srcSize: IntSize,
        dstOffset: IntOffset,
        dstSize: IntSize,
        paint: Paint,
    ) = Unit
    override fun drawPoints(pointMode: PointMode, points: List<Offset>, paint: Paint) = Unit
    override fun drawRawPoints(pointMode: PointMode, points: FloatArray, paint: Paint) = Unit
    override fun drawVertices(vertices: Vertices, blendMode: BlendMode, paint: Paint) = Unit
    override fun enableZ() = Unit
    override fun disableZ() = Unit
}

actual fun Path(): Path = WinUIPath()

private class WinUIPath : Path {
    override var fillType: PathFillType = PathFillType.NonZero
    override val isConvex: Boolean = true
    override val isEmpty: Boolean = true

    override fun moveTo(x: Float, y: Float) = Unit
    override fun relativeMoveTo(dx: Float, dy: Float) = Unit
    override fun lineTo(x: Float, y: Float) = Unit
    override fun relativeLineTo(dx: Float, dy: Float) = Unit
    override fun quadraticBezierTo(x1: Float, y1: Float, x2: Float, y2: Float) = Unit
    override fun relativeQuadraticBezierTo(dx1: Float, dy1: Float, dx2: Float, dy2: Float) = Unit
    override fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = Unit
    override fun relativeCubicTo(dx1: Float, dy1: Float, dx2: Float, dy2: Float, dx3: Float, dy3: Float) = Unit
    override fun arcTo(rect: Rect, startAngleDegrees: Float, sweepAngleDegrees: Float, forceMoveTo: Boolean) = Unit
    @Suppress("DEPRECATION")
    override fun addRect(rect: Rect) = addRect(rect, Path.Direction.CounterClockwise)
    override fun addRect(rect: Rect, direction: Path.Direction) = Unit
    @Suppress("DEPRECATION")
    override fun addOval(oval: Rect) = addOval(oval, Path.Direction.CounterClockwise)
    override fun addOval(oval: Rect, direction: Path.Direction) = Unit
    @Suppress("DEPRECATION")
    override fun addRoundRect(roundRect: RoundRect) = addRoundRect(roundRect, Path.Direction.CounterClockwise)
    override fun addRoundRect(roundRect: RoundRect, direction: Path.Direction) = Unit
    override fun addArcRad(oval: Rect, startAngleRadians: Float, sweepAngleRadians: Float) = Unit
    override fun addArc(oval: Rect, startAngleDegrees: Float, sweepAngleDegrees: Float) = Unit
    override fun addPath(path: Path, offset: Offset) = Unit
    override fun close() = Unit
    override fun reset() = Unit
    override fun translate(offset: Offset) = Unit
    override fun getBounds(): Rect = Rect.Zero
    override fun op(path1: Path, path2: Path, operation: PathOperation): Boolean = false
}

actual fun PathMeasure(): PathMeasure = WinUIPathMeasure()

private class WinUIPathMeasure : PathMeasure {
    override val length: Float = 0f
    override fun getSegment(
        startDistance: Float,
        stopDistance: Float,
        destination: Path,
        startWithMoveTo: Boolean,
    ): Boolean = false
    override fun setPath(path: Path?, forceClosed: Boolean) = Unit
    override fun getPosition(distance: Float): Offset = Offset.Unspecified
    override fun getTangent(distance: Float): Offset = Offset.Unspecified
}

actual fun PathIterator(
    path: Path,
    conicEvaluation: PathIterator.ConicEvaluation,
    tolerance: Float,
): PathIterator = WinUIPathIterator(path, conicEvaluation, tolerance)

private class WinUIPathIterator(
    override val path: Path,
    override val conicEvaluation: PathIterator.ConicEvaluation,
    override val tolerance: Float,
) : PathIterator {
    override fun calculateSize(includeConvertedConics: Boolean): Int = 0
    override fun hasNext(): Boolean = false
    override fun next(outPoints: FloatArray, offset: Int): PathSegment.Type = PathSegment.Type.Done
    override fun next(): PathSegment = DoneSegment
}

internal actual fun ActualImageBitmap(
    width: Int,
    height: Int,
    config: ImageBitmapConfig,
    hasAlpha: Boolean,
    colorSpace: ColorSpace,
): ImageBitmap = WinUIImageBitmap(width, height, config, hasAlpha, colorSpace)

private class WinUIImageBitmap(
    override val width: Int,
    override val height: Int,
    override val config: ImageBitmapConfig,
    override val hasAlpha: Boolean,
    override val colorSpace: ColorSpace,
) : ImageBitmap {
    override fun readPixels(
        buffer: IntArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        bufferOffset: Int,
        stride: Int,
    ) = Unit

    override fun prepareToDraw() = Unit
}

internal actual fun createImageBitmap(bytes: ByteArray): ImageBitmap =
    ActualImageBitmap(1, 1, ImageBitmapConfig.Argb8888, true, ColorSpaces.Srgb)

actual class Shader

internal actual class TransformShader actual constructor() {
    actual var shader: Shader? = null
    actual fun transform(matrix: Matrix?) = Unit
}

internal actual fun ActualLinearGradientShader(
    from: Offset,
    to: Offset,
    colors: List<Color>,
    colorStops: List<Float>?,
    tileMode: TileMode,
): Shader = Shader()

internal actual fun ActualRadialGradientShader(
    center: Offset,
    radius: Float,
    colors: List<Color>,
    colorStops: List<Float>?,
    tileMode: TileMode,
): Shader = Shader()

internal actual fun ActualSweepGradientShader(
    center: Offset,
    colors: List<Color>,
    colorStops: List<Float>?,
): Shader = Shader()

internal actual fun ActualImageShader(
    image: ImageBitmap,
    tileModeX: TileMode,
    tileModeY: TileMode,
): Shader = Shader()

internal actual fun ActualCompositeShader(dst: Shader, src: Shader, blendMode: BlendMode): Shader =
    Shader()

private object WinUIPathEffect : PathEffect

internal actual fun actualCornerPathEffect(radius: Float): PathEffect = WinUIPathEffect

internal actual fun actualDashPathEffect(intervals: FloatArray, phase: Float): PathEffect =
    WinUIPathEffect

internal actual fun actualChainPathEffect(outer: PathEffect, inner: PathEffect): PathEffect =
    WinUIPathEffect

internal actual fun actualStampedPathEffect(
    shape: Path,
    advance: Float,
    phase: Float,
    style: StampedPathEffectStyle,
): PathEffect = WinUIPathEffect

internal actual class NativeColorFilter

internal actual fun actualTintColorFilter(color: Color, blendMode: BlendMode): NativeColorFilter =
    NativeColorFilter()

internal actual fun actualColorMatrixColorFilter(colorMatrix: ColorMatrix): NativeColorFilter =
    NativeColorFilter()

internal actual fun actualLightingColorFilter(multiply: Color, add: Color): NativeColorFilter =
    NativeColorFilter()

internal actual fun actualColorMatrixFromFilter(filter: NativeColorFilter): ColorMatrix =
    ColorMatrix()

actual sealed class RenderEffect actual constructor() {
    actual open fun isSupported(): Boolean = false
}

actual class BlurEffect actual constructor(
    val renderEffect: RenderEffect?,
    val radiusX: Float,
    val radiusY: Float,
    val edgeTreatment: TileMode,
) : RenderEffect()

actual class OffsetEffect actual constructor(
    val renderEffect: RenderEffect?,
    val offset: Offset,
) : RenderEffect()
