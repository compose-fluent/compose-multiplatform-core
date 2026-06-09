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

package androidx.compose.foundation

import androidx.compose.foundation.text.contextmenu.data.TextContextMenuComponent
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItemWithComposableLeadingIcon
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSeparator
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWinUIRoot
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.PopupPositionProvider
import microsoft.ui.xaml.RoutedEventHandler
import microsoft.ui.xaml.controls.MenuFlyout
import microsoft.ui.xaml.controls.MenuFlyoutItem
import microsoft.ui.xaml.controls.MenuFlyoutSeparator
import windows.foundation.Point

@OptIn(InternalComposeUiApi::class)
@Composable
internal fun DefaultOpenContextMenu(
    session: TextContextMenuSession,
    components: List<TextContextMenuComponent>,
    popupPositionProvider: PopupPositionProvider,
    colors: ContextMenuColors = DefaultContextMenuColors,
) {
    val root = LocalWinUIRoot.current ?: return
    val windowSize = LocalWindowInfo.current.containerSize.takeIf { it != IntSize.Zero }
        ?: IntSize.Zero
    val layoutDirection = LocalLayoutDirection.current
    val flyout = remember(session, components) {
        createMenuFlyout(session, components)
    }
    LaunchedEffect(root, flyout, windowSize, layoutDirection, popupPositionProvider) {
        val position = popupPositionProvider.calculatePosition(
            anchorBounds = IntRect.Zero,
            windowSize = windowSize,
            layoutDirection = layoutDirection,
            popupContentSize = IntSize.Zero,
        )
        flyout.showAt(root, Point(position.x.toFloat(), position.y.toFloat()))
    }
}

private fun createMenuFlyout(
    session: TextContextMenuSession,
    components: List<TextContextMenuComponent>,
): MenuFlyout {
    val flyout = MenuFlyout()
    components.forEach { component ->
        when (component) {
            is TextContextMenuSeparator -> flyout.items.add(MenuFlyoutSeparator())
            is TextContextMenuItemWithComposableLeadingIcon -> {
                val item = MenuFlyoutItem()
                item.text = component.label
                item.isEnabled = component.enabled
                item.click.add(RoutedEventHandler { _, _ ->
                    component.onClick(session)
                })
                flyout.items.add(item)
            }
        }
    }
    return flyout
}

private const val DisabledAlpha = 0.38f

internal val DefaultContextMenuColors =
    ContextMenuColors(
        backgroundColor = androidx.compose.ui.graphics.Color.White,
        textColor = androidx.compose.ui.graphics.Color.Black,
        iconColor = androidx.compose.ui.graphics.Color.Black,
        disabledTextColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = DisabledAlpha),
        disabledIconColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = DisabledAlpha),
        hoverColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.04f),
    )

internal class ContextMenuColors(
    val backgroundColor: androidx.compose.ui.graphics.Color,
    val textColor: androidx.compose.ui.graphics.Color,
    val iconColor: androidx.compose.ui.graphics.Color,
    val disabledTextColor: androidx.compose.ui.graphics.Color,
    val disabledIconColor: androidx.compose.ui.graphics.Color,
    val hoverColor: androidx.compose.ui.graphics.Color,
)
