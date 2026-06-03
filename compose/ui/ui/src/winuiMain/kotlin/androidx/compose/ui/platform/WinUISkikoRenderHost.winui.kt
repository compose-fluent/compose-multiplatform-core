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
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skiko.GraphicsApi
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
    private val layer: WinUISkikoLayerAdapter,
    private val renderDelegateCloseable: AutoCloseable? = null,
) : AutoCloseable {
    constructor(renderDelegate: SkikoRenderDelegate? = null) : this(
        DefaultWinUISkikoLayerAdapter(renderDelegate),
        renderDelegate as? AutoCloseable,
    )

    private var frameScheduler: AutoCloseable? = null
    private var isClosed = false

    val component: FrameworkElement
        get() = layer.component

    val renderApiForTest: GraphicsApi
        get() = layer.renderApi

    val renderVersionForTest: Long
        get() = layer.renderVersion

    val lastRenderSizeForTest: IntSize?
        get() = layer.lastRenderSize

    val lastRenderedStateSizeForTest: IntSize?
        get() = layer.lastRenderedStateSize

    val pendingRenderStateSizeForTest: IntSize?
        get() = layer.pendingRenderStateSize

    val renderFailureForTest: String?
        get() = layer.renderFailure

    val isFrameSchedulerStartedForTest: Boolean
        get() = frameScheduler != null

    fun requestRender(throttledToVsync: Boolean = true) {
        if (!isClosed) {
            layer.requestRender(throttledToVsync)
        }
    }

    fun setSize(size: IntSize) {
        if (!isClosed) {
            layer.setSize(size)
        }
    }

    fun startFrameScheduler(): AutoCloseable {
        check(!isClosed) {
            "Cannot start a WinUI Skiko frame scheduler after the render host is closed."
        }
        return frameScheduler ?: layer.startFrameScheduler().also { frameScheduler = it }
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        val scheduler = frameScheduler
        frameScheduler = null
        var failure: Throwable? = null
        failure = scheduler.closePreserving(failure)
        failure = layer.closePreserving(failure)
        failure = renderDelegateCloseable.closePreserving(failure)
        failure?.let { throw it }
    }

    private fun AutoCloseable?.closePreserving(previousFailure: Throwable?): Throwable? {
        if (this == null) return previousFailure
        var failure = previousFailure
        try {
            close()
        } catch (e: Throwable) {
            failure?.addSuppressed(e) ?: run { failure = e }
        }
        return failure
    }
}

internal interface WinUISkikoLayerAdapter : AutoCloseable {
    val component: FrameworkElement

    val renderApi: GraphicsApi

    val renderVersion: Long

    val lastRenderSize: IntSize?

    val lastRenderedStateSize: IntSize?

    val pendingRenderStateSize: IntSize?

    val renderFailure: String?

    fun requestRender(throttledToVsync: Boolean)

    fun setSize(size: IntSize)

    fun startFrameScheduler(): AutoCloseable
}

private class DefaultWinUISkikoLayerAdapter(
    renderDelegate: SkikoRenderDelegate?,
) : WinUISkikoLayerAdapter {
    private val layer = WinUISkiaLayer(renderDelegate)

    override val component: FrameworkElement
        get() = layer.component

    override val renderApi: GraphicsApi
        get() = layer.renderApi

    override val renderVersion: Long
        get() = layer.renderDiagnostics.renderVersion

    override val lastRenderSize: IntSize?
        get() = layer.renderDiagnostics.lastPlatformResult?.let {
            IntSize(width = it.width, height = it.height)
        }

    override val lastRenderedStateSize: IntSize?
        get() = layer.renderDiagnostics.lastRenderedState?.let {
            IntSize(width = it.scaledWidth, height = it.scaledHeight)
        }

    override val pendingRenderStateSize: IntSize?
        get() = layer.renderDiagnostics.pendingInvalidatedState?.let {
            IntSize(width = it.scaledWidth, height = it.scaledHeight)
        }

    override val renderFailure: String?
        get() = layer.renderDiagnostics.lastFailure?.let {
            listOfNotNull(
                it.exceptionClass,
                it.message,
                it.causeClass,
                it.causeMessage,
            )
                .joinToString(separator = ": ")
        }

    override fun requestRender(throttledToVsync: Boolean) {
        layer.needRender(throttledToVsync)
    }

    override fun setSize(size: IntSize) {
        component.width = size.width.toDouble()
        component.height = size.height.toDouble()
    }

    override fun startFrameScheduler(): WinUIFrameScheduler =
        layer.startFrameScheduler()

    override fun close() {
        layer.close()
    }
}
