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
import androidx.compose.ui.focus.FocusEnterExitScope
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusPropertiesModifierNode
import androidx.compose.ui.focus.FocusTargetNode
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.materialize
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.UiApplier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.PlatformAbi
import microsoft.ui.xaml.FocusState
import microsoft.ui.xaml.FrameworkElement
import microsoft.ui.xaml.HorizontalAlignment
import microsoft.ui.xaml.Thickness
import microsoft.ui.xaml.UIElement
import microsoft.ui.xaml.VerticalAlignment
import microsoft.ui.xaml.controls.Canvas
import microsoft.ui.xaml.controls.Control
import microsoft.ui.xaml.media.RectangleGeometry
import windows.foundation.Rect
import windows.foundation.Size
import kotlin.math.ceil
import kotlin.math.max

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
    val density = LocalDensity.current
    if (onReset != null) {
        ReusableComposeNode<LayoutNode, UiApplier>(
            factory = createWinUIViewNodeFactory(factory, density),
            update = {
                updateWinUIViewHolderParams<T>(
                    modifier = materializedModifier,
                    density = density,
                    properties = properties,
                )
                set(onReset) { requireWinUIViewHolder<T>().resetBlock = it }
                set(update) { requireWinUIViewHolder<T>().updateBlock = it }
                set(onRelease) { requireWinUIViewHolder<T>().releaseBlock = it }
            },
        )
    } else {
        ComposeNode<LayoutNode, UiApplier>(
            factory = createWinUIViewNodeFactory(factory, density),
            update = {
                updateWinUIViewHolderParams<T>(
                    modifier = materializedModifier,
                    density = density,
                    properties = properties,
                )
                set(update) { requireWinUIViewHolder<T>().updateBlock = it }
                set(onRelease) { requireWinUIViewHolder<T>().releaseBlock = it }
            },
        )
    }
}

@Composable
private fun <T : UIElement> createWinUIViewNodeFactory(
    factory: () -> T,
    density: Density,
): () -> LayoutNode {
    return {
        WinUIViewHolder(
            view = factory(),
            initialDensity = density,
        ).layoutNode
    }
}

private fun <T : UIElement> Updater<LayoutNode>.updateWinUIViewHolderParams(
    modifier: Modifier,
    density: Density,
    properties: WinUIInteropProperties,
) {
    set(modifier) {
        val holder = requireWinUIViewHolder<T>()
        holder.modifier = it
    }
    set(density) { requireWinUIViewHolder<T>().density = it }
    set(properties) { requireWinUIViewHolder<T>().properties = it }
}

@Suppress("UNCHECKED_CAST")
private fun <T : UIElement> LayoutNode.requireWinUIViewHolder(): WinUIViewHolder<T> {
    return interopViewFactoryHolder as WinUIViewHolder<T>
}

private val WinUIViewNoOpUpdate: UIElement.() -> Unit = {}

private class WinUIViewHolder<T : UIElement>(
    private val view: T,
    initialDensity: Density,
) : InteropViewFactoryHolder(), WinUIInteropViewHost {
    private val group = InteropViewGroup(
        Canvas().also {
            it.children.add(view)
            it.horizontalAlignment = HorizontalAlignment.Left
            it.verticalAlignment = VerticalAlignment.Top
        }
    )
    private var isViewAttachedToGroup = true
    private var width = 0
    private var height = 0
    private var clipGeometry: RectangleGeometry? = null
    private val initialGroupHitTestVisible = group.uiElement.isHitTestVisible
    private val initialViewHitTestVisible = view.isHitTestVisible
    private val initialViewTabStop = view.isTabStop
    private val initialControlEnabled = (view as? Control)?.isEnabled
    private val releaseCleanups = mutableListOf<() -> Unit>()

    override val interopRoot: UIElement
        get() = group.uiElement

    override val isInteropViewActive: Boolean
        get() = isViewAttachedToGroup

    var modifier: Modifier = Modifier
        set(value) {
            field = value
            layoutNode.modifier = composeModifier(value)
        }

    var density: Density = initialDensity
        set(value) {
            if (field == value) return
            field = value
            layoutNode.density = value
        }

    private val positionModifier = Modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInRoot()
        applyPosition(bounds.left, bounds.top)
    }

    var properties: WinUIInteropProperties = WinUIInteropProperties()
        set(value) {
            field = value
            applyInteraction(value.isUserInteractionEnabled)
            updateClip()
        }

    var updateBlock: (T) -> Unit = WinUIViewNoOpUpdate
        set(value) {
            field = value
            value(view)
        }

    var resetBlock: (T) -> Unit = WinUIViewNoOpUpdate

    var releaseBlock: (T) -> Unit = WinUIViewNoOpUpdate

    private fun registerReleaseCleanup(cleanup: () -> Unit) {
        releaseCleanups += cleanup
    }

    private fun composeModifier(modifier: Modifier): Modifier =
        modifier
            .winUIFocusInteropModifier(
                canFocus = ::canRequestFocus,
                requestFocus = ::requestNativeFocus,
                clearFocus = ::clearNativeFocus,
            )
            .then(positionModifier)

    private val measurePolicy = MeasurePolicy { _, constraints ->
        val desiredSize = view.measureUnclippedDesiredSize()
        val width = constraints.constrainWidth(desiredSize.width)
        val height = constraints.constrainHeight(desiredSize.height)
        layout(width, height) {
            applyLayout(
                width = width,
                height = height,
                nativeWidth = max(width, desiredSize.width),
                nativeHeight = max(height, desiredSize.height),
            )
        }
    }

    val layoutNode: LayoutNode = LayoutNode().also {
        it.interopViewFactoryHolder = this
        it.measurePolicy = measurePolicy
    }

    init {
        layoutNode.density = density
        layoutNode.modifier = composeModifier(modifier)
        registerReleaseCleanup(::clearNativeState)
    }

    override fun getInteropView(): InteropView = view.asInteropView()

    override fun onReuse() {
        if (!isViewAttachedToGroup) {
            attachViewToGroup()
        } else {
            resetBlock(view)
        }
    }

    override fun onDeactivate() {
        resetBlock(view)
        group.uiElement.children.clear()
        isViewAttachedToGroup = false
    }

    override fun onRelease() {
        try {
            releaseBlock(view)
        } finally {
            releaseCleanups.asReversed().forEach { it.invoke() }
            releaseCleanups.clear()
        }
    }

    private fun applyLayout(width: Int, height: Int, nativeWidth: Int, nativeHeight: Int) {
        this.width = width
        this.height = height
        group.uiElement.width = width.toWinUISize()
        group.uiElement.height = height.toWinUISize()
        (view as? FrameworkElement)?.let {
            it.width = nativeWidth.toWinUISize()
            it.height = nativeHeight.toWinUISize()
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

    private fun applyInteraction(isUserInteractionEnabled: Boolean) {
        group.uiElement.isHitTestVisible = initialGroupHitTestVisible && isUserInteractionEnabled
        view.isHitTestVisible = initialViewHitTestVisible && isUserInteractionEnabled
        view.isTabStop = initialViewTabStop && isUserInteractionEnabled
        (view as? Control)?.let { control ->
            control.isEnabled = (initialControlEnabled ?: control.isEnabled) &&
                isUserInteractionEnabled
        }
    }

    private fun canRequestFocus(): Boolean =
        isViewAttachedToGroup && properties.isUserInteractionEnabled && view.isTabStop

    private fun requestNativeFocus(): Boolean =
        runCatching {
            canRequestFocus() && view.focus(FocusState.Programmatic)
        }.getOrDefault(false)

    private fun clearNativeFocus() {
        runCatching {
            if (view.focusState != FocusState.Unfocused) {
                view.focus(FocusState.Unfocused)
            }
        }
    }

    private fun attachViewToGroup() {
        group.uiElement.children.add(view)
        isViewAttachedToGroup = true
    }

    private fun clearNativeState() {
        restoreInteraction()
        clearClip()
        group.uiElement.children.clear()
        isViewAttachedToGroup = false
    }

    private fun restoreInteraction() {
        group.uiElement.isHitTestVisible = initialGroupHitTestVisible
        view.isHitTestVisible = initialViewHitTestVisible
        view.isTabStop = initialViewTabStop
        (view as? Control)?.let { control ->
            initialControlEnabled?.let {
                control.isEnabled = it
            }
        }
    }

    private fun updateClip() {
        if (!properties.clipToBounds) {
            clearClip()
            return
        }
        val clip = clipGeometry ?: RectangleGeometry().also { clipGeometry = it }
        clip.rect = Rect(0f, 0f, width.toFloat(), height.toFloat())
        setClip(group.uiElement, clip)
    }

    private fun clearClip() {
        if (clipGeometry == null) return
        clipGeometry = null
        setClip(group.uiElement, null)
    }
}

private fun Modifier.winUIFocusInteropModifier(
    canFocus: () -> Boolean,
    requestFocus: () -> Boolean,
    clearFocus: () -> Unit,
): Modifier =
    this
        .then(WinUIFocusGroupPropertiesElement(requestFocus))
        .focusTarget()
        .then(WinUIFocusTargetPropertiesElement(canFocus))
        .then(WinUIFocusTargetInteropElement(clearFocus))

private data class WinUIFocusGroupPropertiesElement(
    val requestFocus: () -> Boolean,
) : ModifierNodeElement<WinUIFocusGroupPropertiesNode>() {
    override fun create(): WinUIFocusGroupPropertiesNode =
        WinUIFocusGroupPropertiesNode(requestFocus)

    override fun update(node: WinUIFocusGroupPropertiesNode) {
        node.requestFocus = requestFocus
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "winUIFocusGroupProperties"
    }
}

private class WinUIFocusGroupPropertiesNode(
    var requestFocus: () -> Boolean,
) : Modifier.Node(), FocusPropertiesModifierNode {
    private val onEnter: FocusEnterExitScope.() -> Unit = {
        if (!requestFocus()) {
            cancelFocusChange()
        }
    }

    override fun applyFocusProperties(focusProperties: FocusProperties) {
        focusProperties.canFocus = false
        focusProperties.onEnter = onEnter
    }
}

private data class WinUIFocusTargetPropertiesElement(
    val canFocus: () -> Boolean,
) : ModifierNodeElement<WinUIFocusTargetPropertiesNode>() {
    override fun create(): WinUIFocusTargetPropertiesNode = WinUIFocusTargetPropertiesNode(canFocus)

    override fun update(node: WinUIFocusTargetPropertiesNode) {
        node.canFocus = canFocus
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "winUIFocusTargetProperties"
    }
}

private class WinUIFocusTargetPropertiesNode(
    var canFocus: () -> Boolean,
) : Modifier.Node(), FocusPropertiesModifierNode {
    override fun applyFocusProperties(focusProperties: FocusProperties) {
        focusProperties.canFocus = canFocus()
    }
}

private data class WinUIFocusTargetInteropElement(
    val clearFocus: () -> Unit,
) : ModifierNodeElement<WinUIFocusTargetInteropNode>() {
    override fun create(): WinUIFocusTargetInteropNode =
        WinUIFocusTargetInteropNode(clearFocus)

    override fun update(node: WinUIFocusTargetInteropNode) {
        node.clearFocus = clearFocus
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "winUIFocusTargetInterop"
    }
}

private class WinUIFocusTargetInteropNode(
    var clearFocus: () -> Unit,
) : DelegatingNode() {
    init {
        delegate(FocusTargetNode(isInteropViewHost = true, onFocusChange = ::onFocusStateChange))
    }

    private fun onFocusStateChange(
        previousState: androidx.compose.ui.focus.FocusState,
        currentState: androidx.compose.ui.focus.FocusState,
    ) {
        if (!isAttached) return
        val wasFocused = previousState.isFocused
        val isFocused = currentState.isFocused
        if (wasFocused == isFocused) return
        if (!isFocused) {
            clearFocus()
        }
    }
}

private fun UIElement.measureUnclippedDesiredSize(): IntSize {
    val measuredSize = runCatching {
        measure(
            Size(
                width = MaxUnboundedWinUISize,
                height = MaxUnboundedWinUISize,
            )
        )
        IntSize(
            width = desiredSize.width.toComposeLayoutSize(),
            height = desiredSize.height.toComposeLayoutSize(),
        )
    }.getOrElse {
        IntSize.Zero
    }
    val explicitSize = (this as? FrameworkElement)?.let {
        IntSize(
            width = it.width.toComposeLayoutSize(),
            height = it.height.toComposeLayoutSize(),
        )
    } ?: IntSize.Zero
    return IntSize(
        width = max(measuredSize.width, explicitSize.width),
        height = max(measuredSize.height, explicitSize.height),
    )
}

private fun Int.toWinUISize(): Double =
    if (this > 0) toDouble() else Double.NaN

private fun Float.toComposeLayoutSize(): Int =
    when {
        !isFinite() || this <= 0f -> 0
        this >= Int.MAX_VALUE.toFloat() -> Int.MAX_VALUE
        else -> ceil(this).toInt()
    }

private fun Double.toComposeLayoutSize(): Int =
    when {
        !isFinite() || this <= 0.0 -> 0
        this >= Int.MAX_VALUE.toDouble() -> Int.MAX_VALUE
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
    if (!isPlaced) return null
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
    if (!isPlaced) return
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
