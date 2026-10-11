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

package androidx.compose.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.node.ModifierNodeElement

@InternalComposeUiApi
val LocalPlatformWindowInsets = staticCompositionLocalOf<PlatformWindowInsets> {
    error("CompositionLocal LocalPlatformWindowInsets not present")
}

@Composable
internal fun PlatformWindowInsets.exclude(
    safeInsets: Boolean,
    ime: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalPlatformWindowInsets provides excluding(safeInsets, ime),
        content = content,
    )
}

internal data class WinUIWindowInsetsProviderModifierElement(
    val windowInsets: PlatformWindowInsets,
) : ModifierNodeElement<WinUIRootWindowInsetsProviderNode>() {
    override fun create(): WinUIRootWindowInsetsProviderNode =
        WinUIRootWindowInsetsProviderNode(windowInsets)

    override fun update(node: WinUIRootWindowInsetsProviderNode) = node.update(windowInsets)
}

internal class WinUIRootWindowInsetsProviderNode(
    private var insets: PlatformWindowInsets,
) : PlatformWindowInsetsProviderNode(insets) {
    override fun calculatePlatformInsets(ancestorWindowInsets: PlatformWindowInsets):
        PlatformWindowInsets = insets

    fun update(windowInsets: PlatformWindowInsets) {
        if (insets != windowInsets) {
            insets = windowInsets
            windowInsetsInvalidated()
        }
    }
}

internal data class WinUIPlatformWindowInsets(
    private val captionBarHeight: Int = 0,
    override val titleBarLeftInset: PlatformInsets = PlatformInsets.Zero,
    override val titleBarRightInset: PlatformInsets = PlatformInsets.Zero,
    private val imeBottomInset: Int = 0,
) : PlatformWindowInsets {
    override val captionBar: PlatformInsets = PlatformInsets(
        top = captionBarHeight,
    )
    override val ime: PlatformInsets = PlatformInsets(
        bottom = imeBottomInset,
    )
    override val systemBars: PlatformInsets = captionBar

    override fun excluding(
        safeInsets: Boolean,
        ime: Boolean,
    ): PlatformWindowInsets {
        if (!safeInsets && !ime) return this
        return WinUIPlatformWindowInsets(
            captionBarHeight = if (safeInsets) 0 else captionBarHeight,
            titleBarLeftInset = if (safeInsets) PlatformInsets.Zero else titleBarLeftInset,
            titleBarRightInset = if (safeInsets) PlatformInsets.Zero else titleBarRightInset,
            imeBottomInset = if (ime) 0 else imeBottomInset,
        )
    }
}
