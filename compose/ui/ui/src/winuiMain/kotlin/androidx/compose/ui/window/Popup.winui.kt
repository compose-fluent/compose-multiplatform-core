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

package androidx.compose.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.ComposeFeatureFlags
import androidx.compose.ui.LayerType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWinUIWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WinUIComposeView
import androidx.compose.ui.semantics.popup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.round
import io.github.composefluent.winrt.runtime.EventRegistrationToken
import microsoft.ui.windowing.AppWindow
import microsoft.ui.windowing.AppWindowChangedEventArgs
import microsoft.ui.windowing.OverlappedPresenter
import microsoft.ui.xaml.WindowActivatedEventArgs
import microsoft.ui.xaml.WindowActivationState
import windows.foundation.TypedEventHandler
import windows.graphics.RectInt32
import microsoft.ui.xaml.Window as XamlWindow

@Immutable
actual class PopupProperties private constructor(
    actual val focusable: Boolean,
    actual val dismissOnBackPress: Boolean,
    actual val dismissOnClickOutside: Boolean,
    actual val clippingEnabled: Boolean,
    actual val usePlatformDefaultWidth: Boolean,
    internal val layerType: LayerType,
) {
    actual constructor(
        focusable: Boolean,
        dismissOnBackPress: Boolean,
        dismissOnClickOutside: Boolean,
        clippingEnabled: Boolean,
        usePlatformDefaultWidth: Boolean,
    ) : this(
        focusable = focusable,
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = dismissOnClickOutside,
        clippingEnabled = clippingEnabled,
        usePlatformDefaultWidth = usePlatformDefaultWidth,
        layerType = ComposeFeatureFlags.layerType.value,
    )

    @Deprecated("Maintained for binary compatibility", level = DeprecationLevel.HIDDEN)
    actual constructor(
        focusable: Boolean,
        dismissOnBackPress: Boolean,
        dismissOnClickOutside: Boolean,
        clippingEnabled: Boolean,
    ) : this(
        focusable = focusable,
        dismissOnBackPress = dismissOnBackPress,
        dismissOnClickOutside = dismissOnClickOutside,
        clippingEnabled = clippingEnabled,
        usePlatformDefaultWidth = false,
        layerType = ComposeFeatureFlags.layerType.value,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PopupProperties) return false

        if (focusable != other.focusable) return false
        if (dismissOnBackPress != other.dismissOnBackPress) return false
        if (dismissOnClickOutside != other.dismissOnClickOutside) return false
        if (clippingEnabled != other.clippingEnabled) return false
        if (usePlatformDefaultWidth != other.usePlatformDefaultWidth) return false
        if (layerType != other.layerType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = focusable.hashCode()
        result = 31 * result + dismissOnBackPress.hashCode()
        result = 31 * result + dismissOnClickOutside.hashCode()
        result = 31 * result + clippingEnabled.hashCode()
        result = 31 * result + usePlatformDefaultWidth.hashCode()
        result = 31 * result + layerType.hashCode()
        return result
    }
}

@Composable
actual fun Popup(
    alignment: Alignment,
    offset: IntOffset,
    onDismissRequest: (() -> Unit)?,
    properties: PopupProperties,
    content: @Composable () -> Unit,
) {
    val popupPositionProvider = remember(alignment, offset) {
        AlignmentOffsetPositionProvider(alignment, offset)
    }
    Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = onDismissRequest,
        properties = properties,
        content = content,
    )
}

@Composable
actual fun Popup(
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: (() -> Unit)?,
    properties: PopupProperties,
    content: @Composable () -> Unit,
) {
    if (properties.layerType == LayerType.OnWindow) {
        WinUIWindowPopupLayout(
            popupPositionProvider = popupPositionProvider,
            properties = properties,
            content = content,
        )
    } else {
        WinUICanvasPopupLayout(
            popupPositionProvider = popupPositionProvider,
            properties = properties,
            content = content,
        )
    }
}

@Composable
private fun WinUICanvasPopupLayout(
    popupPositionProvider: PopupPositionProvider,
    properties: PopupProperties,
    content: @Composable () -> Unit,
) {
    var parentBoundsInWindow by remember { mutableStateOf(IntRect.Zero) }

    Layout(
        content = {},
        modifier = Modifier.onPlaced { coordinates ->
            val parentCoordinates = coordinates.parentCoordinates ?: return@onPlaced
            parentBoundsInWindow = IntRect(
                offset = parentCoordinates.positionInWindow().round(),
                size = parentCoordinates.size,
            )
        },
    ) { _, _ ->
        layout(0, 0) {}
    }

    val currentContent by rememberUpdatedState(content)
    val containerSize = LocalWindowInfo.current.containerSize
    val layoutDirection = LocalLayoutDirection.current
    Layout(
        content = currentContent,
        modifier = Modifier.semantics { popup() },
    ) { measurables, constraints ->
        val windowSize = containerSize.takeIf { it != IntSize.Zero }
            ?: constraints.finiteMaxSizeOr(IntSize.Zero)
        val looseConstraints = constraints.copy(
            minWidth = 0,
            minHeight = 0,
            maxWidth = constraints.finiteMaxWidthOr(windowSize.width),
            maxHeight = constraints.finiteMaxHeightOr(windowSize.height),
        )
        val placeables = measurables.map { measurable ->
            measurable.measure(looseConstraints)
        }
        val contentSize = IntSize(
            width = placeables.maxOfOrNull { it.width } ?: 0,
            height = placeables.maxOfOrNull { it.height } ?: 0,
        )
        val effectiveWindowSize = windowSize.takeIf { it != IntSize.Zero }
            ?: constraints.finiteMaxSizeOr(contentSize)
        val popupPosition = popupPositionProvider.calculatePosition(
            anchorBounds = parentBoundsInWindow,
            windowSize = effectiveWindowSize,
            layoutDirection = layoutDirection,
            popupContentSize = contentSize,
        ).let { position ->
            if (properties.clippingEnabled) {
                position.clipToWindow(contentSize, effectiveWindowSize)
            } else {
                position
            }
        }
        val localPosition = popupPosition - parentBoundsInWindow.topLeft

        layout(0, 0) {
            placeables.forEach { placeable ->
                placeable.placeRelative(localPosition)
            }
        }
    }
}

@OptIn(InternalComposeUiApi::class)
@Composable
private fun WinUIWindowPopupLayout(
    popupPositionProvider: PopupPositionProvider,
    properties: PopupProperties,
    content: @Composable () -> Unit,
) {
    val parentBoundsInWindow = remember { mutableStateOf(IntRect.Zero) }
    Layout(
        content = {},
        modifier = Modifier.onPlaced { coordinates ->
            val parentCoordinates = coordinates.parentCoordinates ?: return@onPlaced
            parentBoundsInWindow.value = IntRect(
                offset = parentCoordinates.positionInWindow().round(),
                size = parentCoordinates.size,
            )
        },
    ) { _, _ ->
        layout(0, 0) {}
    }

    val parentWindow = LocalWinUIWindow.current
    val containerSize = LocalWindowInfo.current.containerSize
    val layoutDirection = LocalLayoutDirection.current
    val currentContent by rememberUpdatedState(content)
    val popupHost = rememberNativePopupHost(parentWindow)

    SideEffect {
        popupHost.update(
            popupPositionProvider = popupPositionProvider,
            properties = properties,
            parentBoundsInWindow = parentBoundsInWindow.value,
            windowSize = containerSize,
            layoutDirection = layoutDirection,
            content = currentContent,
        )
    }
    DisposableEffect(popupHost) {
        popupHost.setContent {
            popupHost.Content()
        }
        popupHost.open()
        onDispose {
            popupHost.close()
        }
    }
}

@Composable
private fun rememberNativePopupHost(parentWindow: XamlWindow?): WinUIWindowPopupHost =
    remember(parentWindow) {
        WinUIWindowPopupHost(parentWindow)
    }

private class WinUIWindowPopupHost(
    private val parentWindow: XamlWindow?,
) {
    private val popupWindow = XamlWindow()
    private val composeView = WinUIComposeView(popupWindow) {}
    private var shouldBeOpen = false
    private var isOpen = false
    private var contentSize = IntSize.Zero
    private var popupPositionProvider: PopupPositionProvider? = null
    private var properties: PopupProperties by mutableStateOf(PopupProperties())
    private var parentBoundsInWindow: IntRect = IntRect.Zero
    private var windowSize: IntSize by mutableStateOf(IntSize.Zero)
    private var layoutDirection: androidx.compose.ui.unit.LayoutDirection by mutableStateOf(
        androidx.compose.ui.unit.LayoutDirection.Ltr
    )
    private var currentContent: @Composable () -> Unit by mutableStateOf({})
    private var parentWindowActivatedHandler: TypedEventHandler<Any?, WindowActivatedEventArgs>? = null
    private var parentWindowActivatedToken: EventRegistrationToken? = null
    private var parentAppWindowChangedHandler: TypedEventHandler<AppWindow, AppWindowChangedEventArgs>? = null
    private var parentAppWindowChangedToken: EventRegistrationToken? = null
    private var isOwnerApplied = false

    init {
        popupWindow.content = composeView.root
        configurePopupWindow()
        applyPopupOwner()
        registerParentWindowHandlers()
    }

    fun setContent(content: @Composable () -> Unit) {
        composeView.setContent(content)
    }

    @Composable
    fun Content() {
        val content = currentContent
        val parentWindowSize = windowSize
        Layout(
            content = content,
            modifier = Modifier
                .onPlaced { coordinates -> updateContentSize(coordinates.size) }
                .semantics { popup() },
        ) { measurables, constraints ->
            val windowSize = parentWindowSize.takeIf { it != IntSize.Zero }
                ?: constraints.finiteMaxSizeOr(IntSize.Zero)
            val looseConstraints = constraints.copy(
                minWidth = 0,
                minHeight = 0,
                maxWidth = constraints.finiteMaxWidthOr(windowSize.width),
                maxHeight = constraints.finiteMaxHeightOr(windowSize.height),
            )
            val placeables = measurables.map { measurable ->
                measurable.measure(looseConstraints)
            }
            val contentSize = IntSize(
                width = placeables.maxOfOrNull { it.width } ?: 0,
                height = placeables.maxOfOrNull { it.height } ?: 0,
            )
            layout(contentSize.width, contentSize.height) {
                placeables.forEach { placeable ->
                    placeable.placeRelative(0, 0)
                }
            }
        }
    }

    fun open() {
        shouldBeOpen = true
        updateWindow()
    }

    fun close() {
        shouldBeOpen = false
        isOpen = false
        removeParentWindowHandlers()
        composeView.dispose()
        popupWindow.content = null
        runCatching { popupWindow.close() }
    }

    fun update(
        popupPositionProvider: PopupPositionProvider,
        properties: PopupProperties,
        parentBoundsInWindow: IntRect,
        windowSize: IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        content: @Composable () -> Unit,
    ) {
        this.popupPositionProvider = popupPositionProvider
        this.properties = properties
        this.parentBoundsInWindow = parentBoundsInWindow
        this.windowSize = windowSize
        this.layoutDirection = layoutDirection
        this.currentContent = content
        if (windowSize != IntSize.Zero) {
            // SKIKO-010: keep the Skiko root size stable; resize only the native popup window.
            composeView.setWindowContainerSize(windowSize)
            composeView.rootFrameworkElement.width = windowSize.width.toDouble()
            composeView.rootFrameworkElement.height = windowSize.height.toDouble()
        }
        updateWindow()
    }

    private fun configurePopupWindow() {
        val appWindow = popupWindow.appWindow ?: return
        runCatching { popupWindow.extendsContentIntoTitleBar = true }
        appWindow.title = ""
        appWindow.isShownInSwitchers = false
        val presenter = runCatching { OverlappedPresenter.createForContextMenu() }
            .getOrElse { OverlappedPresenter.createForToolWindow() }
        runCatching { presenter.setBorderAndTitleBar(hasBorder = false, hasTitleBar = false) }
        presenter.isResizable = false
        presenter.isMaximizable = false
        presenter.isMinimizable = false
        presenter.isAlwaysOnTop = false
        runCatching { appWindow.setPresenter(presenter) }
    }

    private fun applyPopupOwner() {
        if (isOwnerApplied) return
        val window = parentWindow ?: return
        isOwnerApplied = setWindowPopupOwner(
            popupWindow = popupWindow,
            parentWindow = window,
        )
    }

    private fun registerParentWindowHandlers() {
        val window = parentWindow ?: return
        if (parentWindowActivatedToken == null) {
            val handler = TypedEventHandler<Any?, WindowActivatedEventArgs> { _, args ->
                if (args.windowActivationState != WindowActivationState.Deactivated) {
                    updateWindow()
                    bringToFront()
                }
            }
            parentWindowActivatedHandler = handler
            parentWindowActivatedToken = window.activated.add(handler)
        }
        val appWindow = window.appWindow ?: return
        if (parentAppWindowChangedToken == null) {
            val handler = TypedEventHandler<AppWindow, AppWindowChangedEventArgs> { _, args ->
                if (args.didPositionChange || args.didSizeChange || args.didVisibilityChange) {
                    updateWindow()
                    bringToFront()
                }
            }
            parentAppWindowChangedHandler = handler
            parentAppWindowChangedToken = appWindow.changed.add(handler)
        }
    }

    private fun removeParentWindowHandlers() {
        parentWindowActivatedToken?.let { token ->
            runCatching { parentWindow?.activated?.remove(token) }
        }
        parentWindowActivatedToken = null
        parentWindowActivatedHandler = null
        parentAppWindowChangedToken?.let { token ->
            runCatching { parentWindow?.appWindow?.changed?.remove(token) }
        }
        parentAppWindowChangedToken = null
        parentAppWindowChangedHandler = null
    }

    fun updateContentSize(size: IntSize) {
        if (contentSize == size) return
        contentSize = size
        updateWindow()
    }

    private fun bringToFront() {
        if (!isOpen) return
        runCatching { popupWindow.appWindow?.moveInZOrderAtTop() }
    }

    private fun updateWindow() {
        val parentAppWindow = parentWindow?.appWindow ?: return
        val popupAppWindow = popupWindow.appWindow ?: return
        val provider = popupPositionProvider ?: return
        applyPopupOwner()
        val effectiveWindowSize = windowSize.takeIf { it != IntSize.Zero }
            ?: contentSize
        if (effectiveWindowSize == IntSize.Zero) return
        val effectiveContentSize = contentSize.takeIf { it != IntSize.Zero } ?: return
        val popupPosition = provider.calculatePosition(
            anchorBounds = parentBoundsInWindow,
            windowSize = effectiveWindowSize,
            layoutDirection = layoutDirection,
            popupContentSize = effectiveContentSize,
        ).let { position ->
            if (properties.clippingEnabled) {
                position.clipToWindow(effectiveContentSize, effectiveWindowSize)
            } else {
                position
            }
        }
        val parentPosition = parentAppWindow.position
        popupAppWindow.moveAndResize(
            RectInt32(
                x = parentPosition.x + popupPosition.x,
                y = parentPosition.y + popupPosition.y,
                width = effectiveContentSize.width.coerceAtLeast(1),
                height = effectiveContentSize.height.coerceAtLeast(1),
            )
        )
        if (shouldBeOpen && !isOpen) {
            isOpen = true
            runCatching { popupAppWindow.show(properties.focusable) }
                .getOrElse { popupWindow.activate() }
        }
        bringToFront()
    }
}

private fun Constraints.finiteMaxSizeOr(fallback: IntSize): IntSize =
    IntSize(
        width = if (hasBoundedWidth) maxWidth else fallback.width,
        height = if (hasBoundedHeight) maxHeight else fallback.height,
    )

private fun Constraints.finiteMaxWidthOr(fallback: Int): Int =
    if (hasBoundedWidth) maxWidth else fallback.takeIf { it > 0 } ?: Constraints.Infinity

private fun Constraints.finiteMaxHeightOr(fallback: Int): Int =
    if (hasBoundedHeight) maxHeight else fallback.takeIf { it > 0 } ?: Constraints.Infinity

private fun IntOffset.clipToWindow(contentSize: IntSize, windowSize: IntSize): IntOffset =
    IntOffset(
        x = if (contentSize.width < windowSize.width) {
            x.coerceIn(0, windowSize.width - contentSize.width)
        } else {
            0
        },
        y = if (contentSize.height < windowSize.height) {
            y.coerceIn(0, windowSize.height - contentSize.height)
        } else {
            0
        },
    )

internal expect fun setWindowPopupOwner(popupWindow: XamlWindow, parentWindow: XamlWindow): Boolean
