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
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.focus.FocusEnterExitScope
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusPropertiesModifierNode
import androidx.compose.ui.focus.FocusTargetNode
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.materialize
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.UiApplier
import androidx.compose.ui.node.WinUIOwner
import androidx.compose.ui.node.requireOwner
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import io.github.composefluent.winrt.runtime.EventHandlerCallback
import io.github.composefluent.winrt.runtime.EventRegistrationToken
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.IInspectableReference
import io.github.composefluent.winrt.runtime.IWinRTObject
import microsoft.ui.xaml.FocusState
import microsoft.ui.xaml.FrameworkElement
import microsoft.ui.xaml.HorizontalAlignment
import microsoft.ui.xaml.RoutedEventHandler
import microsoft.ui.xaml.UIElement
import microsoft.ui.xaml.VerticalAlignment
import microsoft.ui.xaml.automation.AutomationProperties
import microsoft.ui.xaml.automation.peers.AccessibilityView
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
    private val interopView = view.asInteropView()
    private val viewControl = view.asWinRtControl()
    private val viewFrameworkElement = view.asWinRtFrameworkElement()
    private val group = InteropViewGroup(
        Canvas().also {
            it.horizontalAlignment = HorizontalAlignment.Left
            it.verticalAlignment = VerticalAlignment.Top
        }
    )
    private var isViewAttachedToGroup = true
    private var isNativeChildAttachedToGroup = false
    private var width = 0
    private var height = 0
    private var nativeWidth = 0
    private var nativeHeight = 0
    private var positionX = 0f
    private var positionY = 0f
    private var clipGeometry: RectangleGeometry? = null
    private val initialGroupHitTestVisible = group.uiElement.isHitTestVisible
    private val initialViewHitTestVisible = view.isHitTestVisible
    private val initialViewTabStop = view.isTabStop
    private val initialControlEnabled = viewControl?.isEnabled
    private var nativeAccessibilityOverrideApplied = false
    private var loadedFocusToken: EventRegistrationToken? = null
    private var layoutUpdatedFocusToken: EventRegistrationToken? = null
    private val releaseCleanups = mutableListOf<() -> Unit>()
    private val pendingNativeUpdates = mutableListOf<WinUIInteropAction>()
    private var lastKnownOwner: WinUIOwner? = null

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
            applyNativeAccessibility(value.isNativeAccessibilityEnabled)
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

    private fun scheduleNativeUpdate(action: WinUIInteropAction) {
        val owner = if (layoutNode.isAttached) {
            (layoutNode.requireOwner() as? WinUIOwner)?.also { lastKnownOwner = it }
        } else {
            lastKnownOwner
        }
        if (owner == null) {
            pendingNativeUpdates += action
            return
        }
        owner.scheduleInteropTransaction(action)
    }

    private fun flushPendingNativeUpdates() {
        if (pendingNativeUpdates.isEmpty()) return
        val owner = if (layoutNode.isAttached) {
            (layoutNode.requireOwner() as? WinUIOwner)?.also { lastKnownOwner = it }
        } else {
            lastKnownOwner
        } ?: return
        val updates = pendingNativeUpdates.toList()
        pendingNativeUpdates.clear()
        owner.scheduleInteropTransaction {
            updates.forEach { it.invoke() }
        }
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

    override fun getInteropView(): InteropView = interopView

    override fun onReuse() {
        if (!isViewAttachedToGroup || !isNativeChildAttachedToGroup) {
            attachViewToGroup()
        } else {
            resetBlock(view)
        }
    }

    override fun onDeactivate() {
        cancelDeferredNativeFocus()
        updateOwnerInteropFocusRect(null)
        updateOwnerInteropBounds(null)
        resetBlock(view)
        scheduleNativeUpdate {
            group.uiElement.requiredChildren.clear()
        }
        isViewAttachedToGroup = false
        isNativeChildAttachedToGroup = false
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
        if (!isNativeChildAttachedToGroup) {
            attachViewToGroup()
        }
        flushPendingNativeUpdates()
        val changed = this.width != width ||
            this.height != height ||
            this.nativeWidth != nativeWidth ||
            this.nativeHeight != nativeHeight
        this.width = width
        this.height = height
        this.nativeWidth = nativeWidth
        this.nativeHeight = nativeHeight
        scheduleNativeUpdate {
            group.uiElement.width = width.toWinUISize()
            group.uiElement.height = height.toWinUISize()
            viewFrameworkElement?.let {
                it.width = nativeWidth.toWinUISize()
                it.height = nativeHeight.toWinUISize()
                it.horizontalAlignment = HorizontalAlignment.Left
                it.verticalAlignment = VerticalAlignment.Top
            }
        }
        updateClip()
        updateOwnerInteropBoundsIfActive()
        updateOwnerInteropFocusRectIfFocused()
        if (changed) {
            notifyInteropLayoutChanged()
        }
    }

    private fun applyPosition(x: Float, y: Float) {
        val changed = positionX != x || positionY != y
        positionX = x
        positionY = y
        scheduleNativeUpdate {
            Canvas.setLeft(group.uiElement, x.toDouble())
            Canvas.setTop(group.uiElement, y.toDouble())
        }
        updateOwnerInteropBoundsIfActive()
        updateOwnerInteropFocusRectIfFocused()
        if (changed) {
            notifyInteropLayoutChanged()
        }
    }

    @OptIn(InternalComposeUiApi::class)
    private fun notifyInteropLayoutChanged() {
        if (!layoutNode.isAttached) return
        layoutNode.requireOwner().onInteropViewLayoutChange(interopView)
    }

    private fun applyInteraction(isUserInteractionEnabled: Boolean) {
        scheduleNativeUpdate {
            group.uiElement.isHitTestVisible = initialGroupHitTestVisible &&
                isUserInteractionEnabled
            view.isHitTestVisible = initialViewHitTestVisible && isUserInteractionEnabled
            view.isTabStop = initialViewTabStop && isUserInteractionEnabled
            viewControl?.let { control ->
                control.isEnabled = (initialControlEnabled ?: control.isEnabled) &&
                    isUserInteractionEnabled
            }
        }
    }

    private fun applyNativeAccessibility(isNativeAccessibilityEnabled: Boolean) {
        if (isNativeAccessibilityEnabled) {
            if (nativeAccessibilityOverrideApplied) {
                scheduleNativeUpdate {
                    view.clearValue(requiredAccessibilityViewProperty)
                }
                nativeAccessibilityOverrideApplied = false
            }
        } else {
            scheduleNativeUpdate {
                AutomationProperties.setAccessibilityView(view, AccessibilityView.Raw)
            }
            nativeAccessibilityOverrideApplied = true
        }
    }

    private fun canRequestFocus(): Boolean =
        isViewAttachedToGroup &&
            isNativeChildAttachedToGroup &&
            properties.isUserInteractionEnabled &&
            initialViewTabStop

    private fun requestNativeFocus(): Boolean {
        if (!canRequestFocus()) {
            cancelDeferredNativeFocus()
            return false
        }
        if (requestNativeFocusNow()) {
            cancelDeferredNativeFocus()
            return true
        }
        val frameworkElement = viewFrameworkElement ?: return false
        requestNativeFocusWhenLayoutReady(frameworkElement)
        return true
    }

    private fun requestNativeFocusNow(): Boolean =
        runCatching {
            canRequestFocus() && view.focus(FocusState.Programmatic)
        }.getOrDefault(false).also { isFocused ->
            if (isFocused) {
                updateOwnerInteropFocusRect(interopFocusRect())
            }
        }

    private fun requestNativeFocusWhenLayoutReady(frameworkElement: FrameworkElement) {
        if (runCatching { frameworkElement.isLoaded }.getOrDefault(false)) {
            requestNativeFocusOnNextLayoutUpdated(frameworkElement)
        } else {
            requestNativeFocusOnLoaded(frameworkElement)
        }
    }

    private fun requestNativeFocusOnLoaded(frameworkElement: FrameworkElement) {
        if (loadedFocusToken != null) return
        val handler = RoutedEventHandler { _, _ ->
            clearLoadedFocusRequest(frameworkElement)
            if (!requestNativeFocusNow()) {
                requestNativeFocusOnNextLayoutUpdated(frameworkElement)
            }
        }
        loadedFocusToken = frameworkElement.loaded.add(handler)
    }

    private fun requestNativeFocusOnNextLayoutUpdated(frameworkElement: FrameworkElement) {
        if (layoutUpdatedFocusToken != null) return
        val handler: EventHandlerCallback<Any?> = { _, _ ->
            clearLayoutUpdatedFocusRequest(frameworkElement)
            requestNativeFocusNow()
        }
        layoutUpdatedFocusToken = frameworkElement.layoutUpdated.add(handler)
    }

    private fun clearNativeFocus() {
        cancelDeferredNativeFocus()
        runCatching {
            if (view.focusState != FocusState.Unfocused) {
                view.focus(FocusState.Unfocused)
            }
        }
        updateOwnerInteropFocusRect(null)
    }

    private fun cancelDeferredNativeFocus() {
        val frameworkElement = viewFrameworkElement ?: return
        clearLoadedFocusRequest(frameworkElement)
        clearLayoutUpdatedFocusRequest(frameworkElement)
    }

    private fun clearLoadedFocusRequest(frameworkElement: FrameworkElement) {
        loadedFocusToken?.let { token ->
            runCatching { frameworkElement.loaded.remove(token) }
            loadedFocusToken = null
        }
    }

    private fun clearLayoutUpdatedFocusRequest(frameworkElement: FrameworkElement) {
        layoutUpdatedFocusToken?.let { token ->
            runCatching { frameworkElement.layoutUpdated.remove(token) }
            layoutUpdatedFocusToken = null
        }
    }

    private fun attachViewToGroup() {
        scheduleNativeUpdate {
            group.uiElement.requiredChildren.add(view)
        }
        isViewAttachedToGroup = true
        isNativeChildAttachedToGroup = true
        updateOwnerInteropBoundsIfActive()
    }

    private fun clearNativeState() {
        cancelDeferredNativeFocus()
        updateOwnerInteropFocusRect(null)
        updateOwnerInteropBounds(null)
        pendingNativeUpdates.clear()
        restoreInteraction()
        restoreNativeAccessibility()
        clearClip()
        scheduleNativeUpdate {
            group.uiElement.requiredChildren.clear()
        }
        isViewAttachedToGroup = false
        isNativeChildAttachedToGroup = false
    }

    private fun restoreInteraction() {
        scheduleNativeUpdate {
            group.uiElement.isHitTestVisible = initialGroupHitTestVisible
            view.isHitTestVisible = initialViewHitTestVisible
            view.isTabStop = initialViewTabStop
            viewControl?.let { control ->
                initialControlEnabled?.let {
                    control.isEnabled = it
                }
            }
        }
    }

    private fun restoreNativeAccessibility() {
        if (!nativeAccessibilityOverrideApplied) return
        scheduleNativeUpdate {
            view.clearValue(requiredAccessibilityViewProperty)
        }
        nativeAccessibilityOverrideApplied = false
    }

    private fun updateClip() {
        if (!properties.clipToBounds) {
            clearClip()
            return
        }
        val clip = clipGeometry ?: RectangleGeometry().also { clipGeometry = it }
        clip.setValue(
            checkNotNull(RectangleGeometry.rectProperty) {
                "WinUI RectangleGeometry.RectProperty is not available."
            },
            Rect(0f, 0f, width.toFloat(), height.toFloat()),
        )
        scheduleNativeUpdate {
            setClip(group.uiElement, clip)
        }
    }

    private fun clearClip() {
        if (clipGeometry == null) return
        clipGeometry = null
        scheduleNativeUpdate {
            setClip(group.uiElement, null)
        }
    }

    private fun interopFocusRect(): ComposeRect =
        ComposeRect(positionX, positionY, positionX + width, positionY + height)

    private fun interopBounds(): ComposeRect =
        ComposeRect(positionX, positionY, positionX + width, positionY + height)

    private fun updateOwnerInteropBoundsIfActive() {
        updateOwnerInteropBounds(if (isViewAttachedToGroup) interopBounds() else null)
    }

    private fun updateOwnerInteropBounds(bounds: ComposeRect?) {
        if (!layoutNode.isAttached) return
        (layoutNode.requireOwner() as? WinUIOwner)?.setInteropViewBounds(interopView, bounds)
    }

    private fun updateOwnerInteropFocusRectIfFocused() {
        if (runCatching { view.focusState != FocusState.Unfocused }.getOrDefault(false)) {
            updateOwnerInteropFocusRect(interopFocusRect())
        }
    }

    private fun updateOwnerInteropFocusRect(rect: ComposeRect?) {
        if (!layoutNode.isAttached) return
        (layoutNode.requireOwner() as? WinUIOwner)?.setInteropViewFocusRect(rect)
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
    val explicitSize = asWinRtFrameworkElement()?.let {
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

private val requiredAccessibilityViewProperty
    get() = checkNotNull(AutomationProperties.accessibilityViewProperty) {
        "WinUI AutomationProperties.AccessibilityViewProperty is not available."
    }

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

private fun Any?.asWinRtControl(): Control? =
    asWinRtRuntimeClass(Control.Metadata.DEFAULT_INTERFACE_IID, Control.Metadata::wrap)

private fun Any?.asWinRtFrameworkElement(): FrameworkElement? =
    asWinRtRuntimeClass(
        FrameworkElement.Metadata.DEFAULT_INTERFACE_IID,
        FrameworkElement.Metadata::wrap,
    )

private inline fun <T> Any?.asWinRtRuntimeClass(
    defaultInterfaceIid: Guid,
    wrap: (IInspectableReference) -> T,
): T? {
    val winRtObject = this as? IWinRTObject ?: return null
    val queriedInterface = winRtObject.nativeObject.tryQueryInterface(defaultInterfaceIid) ?: return null
    queriedInterface.close()
    return wrap(winRtObject.nativeObject.asInspectable())
}

private fun setClip(element: UIElement, clip: RectangleGeometry?) {
    element.clip = clip
}

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
