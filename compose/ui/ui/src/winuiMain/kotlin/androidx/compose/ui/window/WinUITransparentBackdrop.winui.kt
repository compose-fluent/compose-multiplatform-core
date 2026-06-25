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

import microsoft.ui.composition.ICompositionSupportsSystemBackdrop
import microsoft.ui.xaml.XamlRoot
import microsoft.ui.xaml.media.SystemBackdrop
import windows.ui.Color
import windows.ui.composition.Compositor
import microsoft.ui.xaml.Window as XamlWindow

internal class WinUITransparentBackdrop(
    private val window: XamlWindow,
    private val enableWindowTransparentBackdrop: Boolean = true,
) : SystemBackdrop() {
    private var isConnected = false
    private var compositor: Compositor? = null

    override fun onTargetConnected(
        connectedTarget: ICompositionSupportsSystemBackdrop,
        xamlRoot: XamlRoot,
    ) {
        isConnected = true
        val compositor = compositor ?: Compositor().also { compositor = it }
        connectedTarget.systemBackdrop = compositor.createColorBrush(
            Color(a = 0u, r = 0u, g = 0u, b = 0u)
        )
        if (enableWindowTransparentBackdrop) {
            setWindowTransparentBackdrop(window, xamlRoot, enabled = true)
        }
        super.onTargetConnected(connectedTarget, xamlRoot)
    }

    override fun onTargetDisconnected(disconnectedTarget: ICompositionSupportsSystemBackdrop) {
        disconnectedTarget.systemBackdrop = null
        compositor?.close()
        compositor = null
        isConnected = false
        super.onTargetDisconnected(disconnectedTarget)
    }
}

internal expect fun setWindowTransparentBackdrop(
    window: XamlWindow,
    xamlRoot: XamlRoot?,
    enabled: Boolean,
): Boolean

internal expect fun setWindowTransparentBackdropDirect(
    window: XamlWindow,
    enabled: Boolean,
): Boolean
