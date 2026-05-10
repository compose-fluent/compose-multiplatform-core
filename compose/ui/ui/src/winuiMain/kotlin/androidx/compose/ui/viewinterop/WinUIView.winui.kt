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

package androidx.compose.ui.viewinterop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReusableComposeNode
import androidx.compose.runtime.Updater
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.materialize
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.UiApplier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.PlatformAbi
import microsoft.ui.xaml.FrameworkElement
import microsoft.ui.xaml.HorizontalAlignment
import microsoft.ui.xaml.Thickness
import microsoft.ui.xaml.UIElement
import microsoft.ui.xaml.VerticalAlignment
import microsoft.ui.xaml.controls.ContentControl
import microsoft.ui.xaml.media.RectangleGeometry
import windows.foundation.Rect
import windows.foundation.Size
import kotlin.math.ceil

/**
 * WinUI-specific interop switches for [WinUIView].
 */
@Immutable
data class WinUIInteropProperties(
    val isNativeAccessibilityEnabled: Boolean = true,
    val isUserInteractionEnabled: Boolean = true,
    val clipToBounds: Boolean = false,
)

/**
 * Composes a WinUI [UIElement] obtained from [factory].
 *
 * The lifecycle mirrors Android `AndroidView`: [factory] creates the native element exactly once,
 * [update] runs after creation and on recomposition, [onReset] opts into reuse, and [onRelease]
 * runs when the element is permanently discarded.
 */
@Composable
@UiComposable
fun <T : UIElement> WinUIView(
    factory: () -> T,
    modifier: Modifier = Modifier,
    properties: WinUIInteropProperties = WinUIInteropProperties(),
    update: (T) -> Unit = WinUIViewNoOpUpdate,
) {
    WinUIView(
        factory = factory,
        modifier = modifier,
        properties = properties,
        onRelease = WinUIViewNoOpUpdate,
        update = update,
    )
}

/**
 * Composes a reusable WinUI [UIElement] obtained from [factory].
 */
@Composable
@UiComposable
fun <T : UIElement> WinUIView(
    factory: () -> T,
    modifier: Modifier = Modifier,
    properties: WinUIInteropProperties = WinUIInteropProperties(),
    onReset: ((T) -> Unit)? = null,
    onRelease: (T) -> Unit = WinUIViewNoOpUpdate,
    update: (T) -> Unit = WinUIViewNoOpUpdate,
) {
    val materializedModifier = currentComposer.materialize(modifier)
    if (onReset != null) {
        ReusableComposeNode<LayoutNode, UiApplier>(
            factory = createWinUIViewNodeFactory(factory),
            update = {
                updateWinUIViewHolderParams<T>(
                    modifier = materializedModifier,
                    properties = properties,
                )
                set(onReset) { requireWinUIViewHolder<T>().resetBlock = it }
                set(update) { requireWinUIViewHolder<T>().updateBlock = it }
                set(onRelease) { requireWinUIViewHolder<T>().releaseBlock = it }
            },
        )
    } else {
        ComposeNode<LayoutNode, UiApplier>(
            factory = createWinUIViewNodeFactory(factory),
            update = {
                updateWinUIViewHolderParams<T>(
                    modifier = materializedModifier,
                    properties = properties,
                )
                set(update) { requireWinUIViewHolder<T>().updateBlock = it }
                set(onRelease) { requireWinUIViewHolder<T>().releaseBlock = it }
            },
        )
    }
}

@Composable
private fun <T : UIElement> createWinUIViewNodeFactory(factory: () -> T): () -> LayoutNode {
    return {
        val holder = WinUIViewHolder(view = factory())
        LayoutNode().also {
            it.interopViewFactoryHolder = holder
            it.measurePolicy = holder.measurePolicy
        }
    }
}

private fun <T : UIElement> Updater<LayoutNode>.updateWinUIViewHolderParams(
    modifier: Modifier,
    properties: WinUIInteropProperties,
) {
    set(modifier) {
        val holder = requireWinUIViewHolder<T>()
        holder.modifier = it
        this.modifier = holder.composeModifier(it)
    }
    set(properties) { requireWinUIViewHolder<T>().properties = it }
}

@Suppress("UNCHECKED_CAST")
private fun <T : UIElement> LayoutNode.requireWinUIViewHolder(): WinUIViewHolder<T> {
    return interopViewFactoryHolder as WinUIViewHolder<T>
}

private val WinUIViewNoOpUpdate: UIElement.() -> Unit = {}

private class WinUIViewHolder<T : UIElement>(
    private val view: T,
) : InteropViewFactoryHolder(), WinUIInteropViewHost {
    private val group = InteropViewGroup(
        ContentControl().also {
            it.content = view
            it.horizontalAlignment = HorizontalAlignment.Left
            it.verticalAlignment = VerticalAlignment.Top
        }
    )
    private var isViewAttachedToGroup = true
    private var width = 0
    private var height = 0
    private var clipGeometry: RectangleGeometry? = null

    override val interopRoot: UIElement
        get() = group.uiElement

    override val isInteropViewActive: Boolean
        get() = isViewAttachedToGroup

    var modifier: Modifier = Modifier

    private val positionModifier = Modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInRoot()
        applyPosition(bounds.left, bounds.top)
    }

    var properties: WinUIInteropProperties = WinUIInteropProperties()
        set(value) {
            field = value
            group.uiElement.isHitTestVisible = value.isUserInteractionEnabled
            view.isHitTestVisible = value.isUserInteractionEnabled
            updateClip()
        }

    var updateBlock: (T) -> Unit = WinUIViewNoOpUpdate
        set(value) {
            field = value
            value(view)
        }

    var resetBlock: (T) -> Unit = WinUIViewNoOpUpdate

    var releaseBlock: (T) -> Unit = WinUIViewNoOpUpdate

    fun composeModifier(modifier: Modifier): Modifier = modifier.then(positionModifier)

    val measurePolicy = MeasurePolicy { _, constraints ->
        val desiredSize = view.measureDesiredSize(constraints)
        val width = constraints.constrainWidth(desiredSize.width)
        val height = constraints.constrainHeight(desiredSize.height)
        layout(width, height) {
            applyLayout(width, height)
        }
    }

    override fun getInteropView(): InteropView = view.asInteropView()

    override fun onReuse() {
        if (!isViewAttachedToGroup) {
            group.uiElement.content = view
            isViewAttachedToGroup = true
        } else {
            resetBlock(view)
        }
    }

    override fun onDeactivate() {
        resetBlock(view)
        group.uiElement.content = null
        isViewAttachedToGroup = false
    }

    override fun onRelease() {
        releaseBlock(view)
        group.uiElement.content = null
        isViewAttachedToGroup = false
    }

    private fun applyLayout(width: Int, height: Int) {
        this.width = width
        this.height = height
        group.uiElement.width = width.toWinUISize()
        group.uiElement.height = height.toWinUISize()
        (view as? FrameworkElement)?.let {
            it.width = width.toWinUISize()
            it.height = height.toWinUISize()
            it.horizontalAlignment = HorizontalAlignment.Left
            it.verticalAlignment = VerticalAlignment.Top
        }
        updateClip()
    }

    private fun applyPosition(x: Float, y: Float) {
        // KWINRT-007: Canvas.Left/Top attached property setters crash in the offscreen smoke host.
        group.uiElement.margin = Thickness(
            left = x.toDouble(),
            top = y.toDouble(),
            right = 0.0,
            bottom = 0.0,
        )
    }

    private fun updateClip() {
        if (!properties.clipToBounds) {
            if (clipGeometry == null) return
            clipGeometry = null
            setClip(group.uiElement, null)
            return
        }
        val clip = clipGeometry ?: RectangleGeometry().also { clipGeometry = it }
        clip.rect = Rect(0f, 0f, width.toFloat(), height.toFloat())
        setClip(group.uiElement, clip)
    }
}

private fun UIElement.measureDesiredSize(constraints: Constraints): IntSize {
    if (constraints.hasFixedWidth && constraints.hasFixedHeight) {
        return IntSize(constraints.maxWidth, constraints.maxHeight)
    }
    runCatching {
        measure(
            Size(
                width = constraints.maxWidth.toWinUIAvailableSize(),
                height = constraints.maxHeight.toWinUIAvailableSize(),
            )
        )
    }.getOrElse {
        return IntSize(constraints.minWidth, constraints.minHeight)
    }
    return IntSize(
        width = desiredSize.width.toComposeLayoutSize(),
        height = desiredSize.height.toComposeLayoutSize(),
    )
}

private fun Int.toWinUISize(): Double =
    if (this > 0) toDouble() else Double.NaN

private fun Int.toWinUIAvailableSize(): Float =
    if (this == Constraints.Infinity) MaxUnboundedWinUISize else toFloat()

private fun Float.toComposeLayoutSize(): Int =
    when {
        !isFinite() || this <= 0f -> 0
        this >= Int.MAX_VALUE.toFloat() -> Int.MAX_VALUE
        else -> ceil(this).toInt()
    }

private fun setClip(element: UIElement, clip: RectangleGeometry?) {
    if (clip != null) {
        element.clip = clip
        return
    }
    // KWINRT-006: generated UIElement.clip setter is non-null, but WinUI uses null to clear Clip.
    element.nativeObject.queryInterface(uiElementIid).getOrThrow().use { uiElement ->
        HResult(
            ComVtableInvoker.invokeArgs(
                instance = uiElement.pointer,
                slot = uiElementClipSetterSlot,
                arg0 = PlatformAbi.nullPointer,
            )
        ).requireSuccess("UIElement.Clip clear")
    }
}

private val uiElementIid = Guid("C3C01020-320C-5CF6-9D24-D396BBFA4D8B")
private const val uiElementClipSetterSlot = 12
private const val MaxUnboundedWinUISize = 1_000_000f

@OptIn(InternalComposeUiApi::class)
internal fun LayoutNode.findWinUIInteropRoot(): UIElement? {
    val interopHolder = interopViewFactoryHolder
    val interopHost = interopHolder as? WinUIInteropViewHost
    if (interopHost != null) {
        return interopHost.takeIf { it.isInteropViewActive }?.interopRoot
    }
    return interopHolder?.getInteropView()?.uiElement
        ?: children.firstNotNullOfOrNull { it.findWinUIInteropRoot() }
}

@OptIn(InternalComposeUiApi::class)
internal fun LayoutNode.collectWinUIInteropRoots(): List<UIElement> {
    val result = mutableListOf<UIElement>()
    collectWinUIInteropRootsInto(result)
    return result
}

@OptIn(InternalComposeUiApi::class)
private fun LayoutNode.collectWinUIInteropRootsInto(result: MutableList<UIElement>) {
    val interopHolder = interopViewFactoryHolder
    val interopHost = interopHolder as? WinUIInteropViewHost
    val interopRoot = if (interopHost != null) {
        interopHost.takeIf { it.isInteropViewActive }?.interopRoot
    } else {
        interopHolder?.getInteropView()?.uiElement
    }
    if (interopRoot != null) {
        result += interopRoot
        return
    }
    children.forEach { it.collectWinUIInteropRootsInto(result) }
}
