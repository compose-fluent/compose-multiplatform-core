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

import microsoft.ui.xaml.FrameworkElement
import org.jetbrains.skiko.SkikoRenderDelegate
import org.jetbrains.skiko.winui.WinUIFrameScheduler
import org.jetbrains.skiko.winui.WinUISkiaLayer

/**
 * Narrow compose-winui adapter for the AWT-free skiko-winui layer.
 *
 * The current WinUI owner still uses the compile-source bridge. This adapter
 * keeps the new Maven dependency wired through main sources while the rendering
 * host is split out and connected to [WinUIComposeView].
 */
internal class WinUISkikoRenderHost(
    renderDelegate: SkikoRenderDelegate? = null,
) : AutoCloseable {
    private val layer = WinUISkiaLayer(renderDelegate)
    private var frameScheduler: WinUIFrameScheduler? = null

    val component: FrameworkElement
        get() = layer.component

    fun requestRender(throttledToVsync: Boolean = true) {
        layer.needRender(throttledToVsync)
    }

    fun startFrameScheduler(): WinUIFrameScheduler =
        (frameScheduler ?: layer.startFrameScheduler().also { frameScheduler = it })

    override fun close() {
        frameScheduler?.close()
        frameScheduler = null
        layer.close()
    }
}
