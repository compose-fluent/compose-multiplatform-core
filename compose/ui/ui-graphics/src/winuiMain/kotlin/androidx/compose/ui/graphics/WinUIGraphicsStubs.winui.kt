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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color4f
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Gradient
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Canvas as SkCanvas
import org.jetbrains.skia.Paint as SkPaint
import org.jetbrains.skia.PaintMode as SkPaintMode
import org.jetbrains.skia.PaintStrokeCap as SkPaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin as SkPaintStrokeJoin
import org.jetbrains.skia.Path as SkPath
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.PathDirection
import org.jetbrains.skia.Rect as SkRect
import org.jetbrains.skia.Shader as SkShader
import org.jetbrains.skia.impl.use

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

    fun asSkiaPaint(): SkPaint = SkPaint().also {
        it.color = color.copy(alpha = color.alpha * alpha).toArgb()
        it.isAntiAlias = isAntiAlias
        it.shader = shader?.skiaShader
        it.mode = when (style) {
            PaintingStyle.Fill -> SkPaintMode.FILL
            PaintingStyle.Stroke -> SkPaintMode.STROKE
            else -> SkPaintMode.FILL
        }
        it.strokeWidth = strokeWidth
        it.strokeCap = when (strokeCap) {
            StrokeCap.Butt -> SkPaintStrokeCap.BUTT
            StrokeCap.Round -> SkPaintStrokeCap.ROUND
            StrokeCap.Square -> SkPaintStrokeCap.SQUARE
            else -> SkPaintStrokeCap.BUTT
        }
        it.strokeJoin = when (strokeJoin) {
            StrokeJoin.Miter -> SkPaintStrokeJoin.MITER
            StrokeJoin.Round -> SkPaintStrokeJoin.ROUND
            StrokeJoin.Bevel -> SkPaintStrokeJoin.BEVEL
            else -> SkPaintStrokeJoin.MITER
        }
        it.strokeMiter = strokeMiterLimit
    }
}

actual fun BlendMode.isSupported(): Boolean = true

actual fun TileMode.isSupported(): Boolean = true

@Deprecated("Use direct reference to platform type instead of typealias")
actual class NativeCanvas

internal actual fun ActualCanvas(image: ImageBitmap): Canvas {
    val skiaBitmap = image.asWinUISkiaBitmap()
    require(!skiaBitmap.isImmutable) {
        "Cannot draw on immutable ImageBitmap"
    }
    return WinUICanvas(SkCanvas(skiaBitmap))
}

fun SkCanvas.asComposeCanvas(): Canvas = WinUICanvas(this)

private class WinUICanvas(
    private val skiaCanvas: SkCanvas? = null,
) : Canvas {
    override fun save() {
        skiaCanvas?.save()
    }

    override fun restore() {
        skiaCanvas?.restore()
    }

    override fun saveLayer(bounds: Rect, paint: Paint) {
        skiaCanvas?.saveLayer(
            SkRect.makeLTRB(bounds.left, bounds.top, bounds.right, bounds.bottom),
            paint.asSkiaPaint(),
        )
    }

    override fun translate(dx: Float, dy: Float) {
        skiaCanvas?.translate(dx, dy)
    }

    override fun scale(sx: Float, sy: Float) {
        skiaCanvas?.scale(sx, sy)
    }

    override fun rotate(degrees: Float) {
        skiaCanvas?.rotate(degrees)
    }

    override fun skew(sx: Float, sy: Float) {
        skiaCanvas?.skew(sx, sy)
    }

    override fun concat(matrix: Matrix) = Unit

    override fun clipRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        clipOp: ClipOp,
    ) {
        skiaCanvas?.clipRect(SkRect.makeLTRB(left, top, right, bottom))
    }

    override fun clipPath(path: Path, clipOp: ClipOp) {
        (path as? WinUIPath)?.skiaPath?.use {
            skiaCanvas?.clipPath(it)
        }
    }

    override fun drawLine(p1: Offset, p2: Offset, paint: Paint) {
        skiaCanvas?.drawLine(p1.x, p1.y, p2.x, p2.y, paint.asSkiaPaint())
    }

    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        skiaCanvas?.drawRect(SkRect.makeLTRB(left, top, right, bottom), paint.asSkiaPaint())
    }

    override fun drawRoundRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radiusX: Float,
        radiusY: Float,
        paint: Paint,
    ) {
        skiaCanvas?.drawRRect(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            radii = floatArrayOf(radiusX, radiusY),
            paint = paint.asSkiaPaint(),
        )
    }

    override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        skiaCanvas?.drawOval(SkRect.makeLTRB(left, top, right, bottom), paint.asSkiaPaint())
    }

    override fun drawCircle(center: Offset, radius: Float, paint: Paint) {
        skiaCanvas?.drawCircle(center.x, center.y, radius, paint.asSkiaPaint())
    }

    override fun drawArc(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        startAngle: Float,
        sweepAngle: Float,
        useCenter: Boolean,
        paint: Paint,
    ) {
        skiaCanvas?.drawArc(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            includeCenter = useCenter,
            paint = paint.asSkiaPaint(),
        )
    }

    override fun drawPath(path: Path, paint: Paint) {
        (path as? WinUIPath)?.skiaPath?.use {
            skiaCanvas?.drawPath(it, paint.asSkiaPaint())
        }
    }

    override fun drawImage(image: ImageBitmap, topLeftOffset: Offset, paint: Paint) {
        drawImageRect(
            image = image,
            srcLeft = 0f,
            srcTop = 0f,
            srcRight = image.width.toFloat(),
            srcBottom = image.height.toFloat(),
            dstLeft = topLeftOffset.x,
            dstTop = topLeftOffset.y,
            dstRight = topLeftOffset.x + image.width.toFloat(),
            dstBottom = topLeftOffset.y + image.height.toFloat(),
            paint = paint,
        )
    }

    override fun drawImageRect(
        image: ImageBitmap,
        srcOffset: IntOffset,
        srcSize: IntSize,
        dstOffset: IntOffset,
        dstSize: IntSize,
        paint: Paint,
    ) {
        drawImageRect(
            image = image,
            srcLeft = srcOffset.x.toFloat(),
            srcTop = srcOffset.y.toFloat(),
            srcRight = srcOffset.x.toFloat() + srcSize.width.toFloat(),
            srcBottom = srcOffset.y.toFloat() + srcSize.height.toFloat(),
            dstLeft = dstOffset.x.toFloat(),
            dstTop = dstOffset.y.toFloat(),
            dstRight = dstOffset.x.toFloat() + dstSize.width.toFloat(),
            dstBottom = dstOffset.y.toFloat() + dstSize.height.toFloat(),
            paint = paint,
        )
    }

    override fun drawPoints(pointMode: PointMode, points: List<Offset>, paint: Paint) = Unit
    override fun drawRawPoints(pointMode: PointMode, points: FloatArray, paint: Paint) = Unit
    override fun drawVertices(vertices: Vertices, blendMode: BlendMode, paint: Paint) = Unit
    override fun enableZ() = Unit
    override fun disableZ() = Unit

    private fun Paint.asSkiaPaint(): SkPaint =
        (this as? WinUIPaint)?.asSkiaPaint() ?: SkPaint()

    private fun drawImageRect(
        image: ImageBitmap,
        srcLeft: Float,
        srcTop: Float,
        srcRight: Float,
        srcBottom: Float,
        dstLeft: Float,
        dstTop: Float,
        dstRight: Float,
        dstBottom: Float,
        paint: Paint,
    ) {
        Image.makeFromBitmap(image.asWinUISkiaBitmap()).use { skiaImage ->
            skiaCanvas?.drawImageRect(
                image = skiaImage,
                srcLeft = srcLeft,
                srcTop = srcTop,
                srcRight = srcRight,
                srcBottom = srcBottom,
                dstLeft = dstLeft,
                dstTop = dstTop,
                dstRight = dstRight,
                dstBottom = dstBottom,
                samplingMode = SamplingMode.DEFAULT,
                paint = paint.asSkiaPaint(),
                strict = true,
            )
        }
    }
}

actual fun Path(): Path = WinUIPath()

private class WinUIPath : Path {
    val skiaPath: SkPath
        get() = pathBuilder.snapshot()
    private var pathBuilder = PathBuilder()

    override var fillType: PathFillType = PathFillType.NonZero
    override val isConvex: Boolean get() = skiaPath.use { it.isConvex }
    override val isEmpty: Boolean get() = skiaPath.use { it.isEmpty }

    override fun moveTo(x: Float, y: Float) {
        pathBuilder.moveTo(x, y)
    }

    override fun relativeMoveTo(dx: Float, dy: Float) {
        pathBuilder.rMoveTo(dx, dy)
    }

    override fun lineTo(x: Float, y: Float) {
        pathBuilder.lineTo(x, y)
    }

    override fun relativeLineTo(dx: Float, dy: Float) {
        pathBuilder.rLineTo(dx, dy)
    }

    override fun quadraticBezierTo(x1: Float, y1: Float, x2: Float, y2: Float) {
        pathBuilder.quadTo(x1, y1, x2, y2)
    }

    override fun relativeQuadraticBezierTo(dx1: Float, dy1: Float, dx2: Float, dy2: Float) {
        pathBuilder.rQuadTo(dx1, dy1, dx2, dy2)
    }

    override fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        pathBuilder.cubicTo(x1, y1, x2, y2, x3, y3)
    }

    override fun relativeCubicTo(dx1: Float, dy1: Float, dx2: Float, dy2: Float, dx3: Float, dy3: Float) {
        pathBuilder.rCubicTo(dx1, dy1, dx2, dy2, dx3, dy3)
    }

    override fun arcTo(rect: Rect, startAngleDegrees: Float, sweepAngleDegrees: Float, forceMoveTo: Boolean) {
        pathBuilder.arcTo(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            startAngleDegrees,
            sweepAngleDegrees,
            forceMoveTo,
        )
    }

    @Suppress("DEPRECATION")
    override fun addRect(rect: Rect) = addRect(rect, Path.Direction.CounterClockwise)
    override fun addRect(rect: Rect, direction: Path.Direction) {
        pathBuilder.addRect(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            direction.toSkiaDirection(),
        )
    }

    @Suppress("DEPRECATION")
    override fun addOval(oval: Rect) = addOval(oval, Path.Direction.CounterClockwise)
    override fun addOval(oval: Rect, direction: Path.Direction) {
        pathBuilder.addOval(
            oval.left,
            oval.top,
            oval.right,
            oval.bottom,
            direction.toSkiaDirection(),
        )
    }

    @Suppress("DEPRECATION")
    override fun addRoundRect(roundRect: RoundRect) = addRoundRect(roundRect, Path.Direction.CounterClockwise)
    override fun addRoundRect(roundRect: RoundRect, direction: Path.Direction) {
        pathBuilder.addRRect(
            roundRect.left,
            roundRect.top,
            roundRect.right,
            roundRect.bottom,
            floatArrayOf(
                roundRect.topLeftCornerRadius.x,
                roundRect.topLeftCornerRadius.y,
                roundRect.topRightCornerRadius.x,
                roundRect.topRightCornerRadius.y,
                roundRect.bottomRightCornerRadius.x,
                roundRect.bottomRightCornerRadius.y,
                roundRect.bottomLeftCornerRadius.x,
                roundRect.bottomLeftCornerRadius.y,
            ),
            direction.toSkiaDirection(),
        )
    }

    override fun addArcRad(oval: Rect, startAngleRadians: Float, sweepAngleRadians: Float) {
        addArc(
            oval = oval,
            startAngleDegrees = degrees(startAngleRadians),
            sweepAngleDegrees = degrees(sweepAngleRadians),
        )
    }
    override fun addArc(oval: Rect, startAngleDegrees: Float, sweepAngleDegrees: Float) {
        pathBuilder.addArc(
            oval.left,
            oval.top,
            oval.right,
            oval.bottom,
            startAngleDegrees,
            sweepAngleDegrees,
        )
    }

    override fun addPath(path: Path, offset: Offset) {
        (path as? WinUIPath)?.skiaPath?.use {
            pathBuilder.addPath(it, offset.x, offset.y)
        }
    }

    override fun close() {
        pathBuilder.closePath()
    }

    override fun reset() {
        pathBuilder.close()
        pathBuilder = PathBuilder()
    }

    override fun translate(offset: Offset) = Unit

    override fun getBounds(): Rect {
        val bounds = skiaPath.use { it.bounds }
        return Rect(bounds.left, bounds.top, bounds.right, bounds.bottom)
    }

    override fun op(path1: Path, path2: Path, operation: PathOperation): Boolean = false

    private fun degrees(radians: Float): Float = radians * 180f / kotlin.math.PI.toFloat()

    private fun Path.Direction.toSkiaDirection(): PathDirection =
        when (this) {
            Path.Direction.Clockwise -> PathDirection.CLOCKWISE
            Path.Direction.CounterClockwise -> PathDirection.COUNTER_CLOCKWISE
        }
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
): ImageBitmap {
    require(width > 0 && height > 0) { "width and height must be > 0" }
    val colorInfo = ColorInfo(
        colorType = config.toSkiaColorType(),
        alphaType = if (hasAlpha) ColorAlphaType.PREMUL else ColorAlphaType.OPAQUE,
        colorSpace = colorSpace.toSkiaColorSpace(),
    )
    return WinUIImageBitmap(Bitmap().apply {
        allocPixels(ImageInfo(colorInfo, width, height))
    })
}

private class WinUIImageBitmap(
    val bitmap: Bitmap,
) : ImageBitmap {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height
    override val config: ImageBitmapConfig get() = bitmap.colorType.toComposeConfig()
    override val hasAlpha: Boolean get() = !bitmap.isOpaque
    override val colorSpace: ColorSpace get() = bitmap.colorSpace.toComposeColorSpace()

    override fun readPixels(
        buffer: IntArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        bufferOffset: Int,
        stride: Int,
    ) {
        val lastScanline = bufferOffset + (height - 1) * stride
        require(startX >= 0 && startY >= 0)
        require(width > 0 && startX + width <= this.width)
        require(height > 0 && startY + height <= this.height)
        require(abs(stride) >= width)
        require(bufferOffset >= 0 && bufferOffset + width <= buffer.size)
        require(lastScanline >= 0 && lastScanline + width <= buffer.size)

        val colorInfo = ColorInfo(
            ColorType.BGRA_8888,
            ColorAlphaType.UNPREMUL,
            org.jetbrains.skia.ColorSpace.sRGB,
        )
        val imageInfo = ImageInfo(colorInfo, width, height)
        val bytesPerPixel = 4
        val bytes = bitmap.readPixels(imageInfo, stride * bytesPerPixel, startX, startY)
            ?: return
        ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asIntBuffer()
            .get(buffer, bufferOffset, bytes.size / bytesPerPixel)
    }

    override fun prepareToDraw() = Unit
}

internal actual fun createImageBitmap(bytes: ByteArray): ImageBitmap =
    Image.makeFromEncoded(bytes).use { image ->
        WinUIImageBitmap(image.toBitmap())
    }

private fun Image.toBitmap(): Bitmap {
    val bitmap = Bitmap()
    bitmap.allocPixels(imageInfo)
    readPixels(bitmap, 0, 0)
    return bitmap
}

private fun ImageBitmap.asWinUISkiaBitmap(): Bitmap =
    (this as? WinUIImageBitmap)?.bitmap
        ?: throw UnsupportedOperationException("Unable to obtain org.jetbrains.skia.Bitmap")

private fun ImageBitmapConfig.toSkiaColorType(): ColorType =
    when (this) {
        ImageBitmapConfig.Argb8888 -> ColorType.N32
        ImageBitmapConfig.Alpha8 -> ColorType.ALPHA_8
        ImageBitmapConfig.Rgb565 -> ColorType.RGB_565
        ImageBitmapConfig.F16 -> ColorType.RGBA_F16
        else -> ColorType.N32
    }

private fun ColorType.toComposeConfig(): ImageBitmapConfig =
    when (this) {
        ColorType.N32 -> ImageBitmapConfig.Argb8888
        ColorType.ALPHA_8 -> ImageBitmapConfig.Alpha8
        ColorType.RGB_565 -> ImageBitmapConfig.Rgb565
        ColorType.RGBA_F16 -> ImageBitmapConfig.F16
        else -> ImageBitmapConfig.Argb8888
    }

private fun org.jetbrains.skia.ColorSpace?.toComposeColorSpace(): ColorSpace =
    when (this) {
        org.jetbrains.skia.ColorSpace.sRGB -> ColorSpaces.Srgb
        org.jetbrains.skia.ColorSpace.sRGBLinear -> ColorSpaces.LinearSrgb
        org.jetbrains.skia.ColorSpace.displayP3 -> ColorSpaces.DisplayP3
        else -> ColorSpaces.Srgb
    }

private fun ColorSpace.toSkiaColorSpace(): org.jetbrains.skia.ColorSpace =
    when (this) {
        ColorSpaces.Srgb -> org.jetbrains.skia.ColorSpace.sRGB
        ColorSpaces.LinearSrgb -> org.jetbrains.skia.ColorSpace.sRGBLinear
        ColorSpaces.DisplayP3 -> org.jetbrains.skia.ColorSpace.displayP3
        else -> org.jetbrains.skia.ColorSpace.sRGB
    }

actual class Shader internal constructor(
    val skiaShader: SkShader,
)

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
): Shader {
    validateColorStops(colors, colorStops)
    return Shader(
        SkShader.makeLinearGradient(
            x0 = from.x,
            y0 = from.y,
            x1 = to.x,
            y1 = to.y,
            gradient = colors.toSkiaGradient(colorStops, tileMode),
        )
    )
}

internal actual fun ActualRadialGradientShader(
    center: Offset,
    radius: Float,
    colors: List<Color>,
    colorStops: List<Float>?,
    tileMode: TileMode,
): Shader {
    validateColorStops(colors, colorStops)
    return Shader(
        SkShader.makeRadialGradient(
            x = center.x,
            y = center.y,
            radius = radius,
            gradient = colors.toSkiaGradient(colorStops, tileMode),
        )
    )
}

internal actual fun ActualSweepGradientShader(
    center: Offset,
    colors: List<Color>,
    colorStops: List<Float>?,
): Shader {
    validateColorStops(colors, colorStops)
    return Shader(
        SkShader.makeSweepGradient(
            x = center.x,
            y = center.y,
            gradient = colors.toSkiaGradient(colorStops),
        )
    )
}

internal actual fun ActualImageShader(
    image: ImageBitmap,
    tileModeX: TileMode,
    tileModeY: TileMode,
): Shader = Shader(
    image.asWinUISkiaBitmap().makeShader(
        tmx = tileModeX.toSkiaTileMode(),
        tmy = tileModeY.toSkiaTileMode(),
    )
)

internal actual fun ActualCompositeShader(dst: Shader, src: Shader, blendMode: BlendMode): Shader =
    Shader(
        SkShader.makeBlend(
            mode = blendMode.toSkiaBlendMode(),
            dst = dst.skiaShader,
            src = src.skiaShader,
        )
    )

private fun List<Color>.toSkiaGradient(
    colorStops: List<Float>?,
    tileMode: TileMode = TileMode.Clamp,
): Gradient = Gradient(
    colors = Gradient.Colors(
        colors = Array(size) { index ->
            val color = this[index]
            Color4f(color.red, color.green, color.blue, color.alpha)
        },
        positions = colorStops?.toFloatArray(),
        tileMode = tileMode.toSkiaTileMode(),
    ),
    interpolation = Gradient.Interpolation(
        inPremul = Gradient.Interpolation.InPremul.YES,
    ),
)

private fun validateColorStops(colors: List<Color>, colorStops: List<Float>?) {
    if (colorStops == null) {
        require(colors.size >= 2) {
            "colors must have length of at least 2 if colorStops is omitted."
        }
    } else {
        require(colors.size == colorStops.size) {
            "colors and colorStops arguments must have equal length."
        }
    }
}

private fun TileMode.toSkiaTileMode(): FilterTileMode =
    when (this) {
        TileMode.Clamp -> FilterTileMode.CLAMP
        TileMode.Repeated -> FilterTileMode.REPEAT
        TileMode.Mirror -> FilterTileMode.MIRROR
        TileMode.Decal -> FilterTileMode.DECAL
        else -> FilterTileMode.CLAMP
    }

private fun BlendMode.toSkiaBlendMode(): org.jetbrains.skia.BlendMode =
    when (this) {
        BlendMode.Clear -> org.jetbrains.skia.BlendMode.CLEAR
        BlendMode.Src -> org.jetbrains.skia.BlendMode.SRC
        BlendMode.Dst -> org.jetbrains.skia.BlendMode.DST
        BlendMode.SrcOver -> org.jetbrains.skia.BlendMode.SRC_OVER
        BlendMode.DstOver -> org.jetbrains.skia.BlendMode.DST_OVER
        BlendMode.SrcIn -> org.jetbrains.skia.BlendMode.SRC_IN
        BlendMode.DstIn -> org.jetbrains.skia.BlendMode.DST_IN
        BlendMode.SrcOut -> org.jetbrains.skia.BlendMode.SRC_OUT
        BlendMode.DstOut -> org.jetbrains.skia.BlendMode.DST_OUT
        BlendMode.SrcAtop -> org.jetbrains.skia.BlendMode.SRC_ATOP
        BlendMode.DstAtop -> org.jetbrains.skia.BlendMode.DST_ATOP
        BlendMode.Xor -> org.jetbrains.skia.BlendMode.XOR
        BlendMode.Plus -> org.jetbrains.skia.BlendMode.PLUS
        BlendMode.Modulate -> org.jetbrains.skia.BlendMode.MODULATE
        BlendMode.Screen -> org.jetbrains.skia.BlendMode.SCREEN
        BlendMode.Overlay -> org.jetbrains.skia.BlendMode.OVERLAY
        BlendMode.Darken -> org.jetbrains.skia.BlendMode.DARKEN
        BlendMode.Lighten -> org.jetbrains.skia.BlendMode.LIGHTEN
        BlendMode.ColorDodge -> org.jetbrains.skia.BlendMode.COLOR_DODGE
        BlendMode.ColorBurn -> org.jetbrains.skia.BlendMode.COLOR_BURN
        BlendMode.Hardlight -> org.jetbrains.skia.BlendMode.HARD_LIGHT
        BlendMode.Softlight -> org.jetbrains.skia.BlendMode.SOFT_LIGHT
        BlendMode.Difference -> org.jetbrains.skia.BlendMode.DIFFERENCE
        BlendMode.Exclusion -> org.jetbrains.skia.BlendMode.EXCLUSION
        BlendMode.Multiply -> org.jetbrains.skia.BlendMode.MULTIPLY
        BlendMode.Hue -> org.jetbrains.skia.BlendMode.HUE
        BlendMode.Saturation -> org.jetbrains.skia.BlendMode.SATURATION
        BlendMode.Color -> org.jetbrains.skia.BlendMode.COLOR
        BlendMode.Luminosity -> org.jetbrains.skia.BlendMode.LUMINOSITY
        else -> org.jetbrains.skia.BlendMode.SRC_OVER
    }

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
