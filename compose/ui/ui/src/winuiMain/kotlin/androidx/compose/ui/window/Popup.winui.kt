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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.popup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.round

@Immutable
actual class PopupProperties actual constructor(
    actual val focusable: Boolean,
    actual val dismissOnBackPress: Boolean,
    actual val dismissOnClickOutside: Boolean,
    actual val clippingEnabled: Boolean,
    actual val usePlatformDefaultWidth: Boolean,
) {
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
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PopupProperties) return false

        if (focusable != other.focusable) return false
        if (dismissOnBackPress != other.dismissOnBackPress) return false
        if (dismissOnClickOutside != other.dismissOnClickOutside) return false
        if (clippingEnabled != other.clippingEnabled) return false
        if (usePlatformDefaultWidth != other.usePlatformDefaultWidth) return false

        return true
    }

    override fun hashCode(): Int {
        var result = focusable.hashCode()
        result = 31 * result + dismissOnBackPress.hashCode()
        result = 31 * result + dismissOnClickOutside.hashCode()
        result = 31 * result + clippingEnabled.hashCode()
        result = 31 * result + usePlatformDefaultWidth.hashCode()
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
    WinUIPopupLayout(
        popupPositionProvider = popupPositionProvider,
        properties = properties,
        content = content,
    )
}

@Composable
private fun WinUIPopupLayout(
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
