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
import org.jetbrains.skiko.winui.WinUIAccessibilityProvider
import org.jetbrains.skiko.winui.WinUIFrameScheduler
import org.jetbrains.skiko.winui.WinUISkiaLayer

internal class WinUISkikoRenderHost(
    private val layer: WinUISkikoLayerAdapter,
    private val renderDelegateCloseable: AutoCloseable? = null,
    private val beforeDrawSubmission: () -> Unit = {},
) : AutoCloseable {
    constructor(
        renderDelegate: SkikoRenderDelegate? = null,
        beforeDrawSubmission: () -> Unit = {},
    ) : this(
        layer = DefaultWinUISkikoLayerAdapter(renderDelegate),
        renderDelegateCloseable = renderDelegate as? AutoCloseable,
        beforeDrawSubmission = beforeDrawSubmission,
    )

    private var frameScheduler: AutoCloseable? = null
    private var isClosed = false
    private var isSurfaceAttached = false
    private var requestedSurfaceSize: IntSize? = null
    private var appliedSurfaceSize: IntSize? = null
    private var pendingRenderInvalidation = false
    private var renderInvalidationCount = 0
    private var delegatedRenderInvalidationCount = 0
    private var drawSubmissionCount = 0
    private var interopTransactionDrainCount = 0

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

    val diagnosticsForTest: WinUISkikoRenderHostDiagnostics
        get() = diagnostics()

    fun setAccessibilityProvider(provider: WinUIAccessibilityProvider) {
        if (!isClosed) {
            layer.setAccessibilityProvider(provider)
        }
    }

    fun notifyAccessibilityChanged(update: WinUIAccessibilityUpdate) {
        if (!isClosed) {
            layer.notifyAccessibilityChanged(update)
        }
    }

    fun requestRender(throttledToVsync: Boolean = true) {
        if (!isClosed) {
            renderInvalidationCount += 1
            if (!pendingRenderInvalidation) {
                pendingRenderInvalidation = true
                delegatedRenderInvalidationCount += 1
                layer.requestRender(throttledToVsync)
            }
        }
    }

    fun setSize(size: IntSize) {
        if (!isClosed) {
            requestedSurfaceSize = size
            applyRequestedSurfaceSize()
        }
    }

    fun attachSurface() {
        check(!isClosed) {
            "Cannot attach a WinUI Skiko surface after the render host is closed."
        }
        if (isSurfaceAttached) return
        isSurfaceAttached = true
        applyRequestedSurfaceSize()
    }

    fun detachSurface() {
        if (isClosed || !isSurfaceAttached) return
        isSurfaceAttached = false
        closeFrameScheduler()
    }

    fun startFrameScheduler(): AutoCloseable {
        check(!isClosed) {
            "Cannot start a WinUI Skiko frame scheduler after the render host is closed."
        }
        attachSurface()
        return frameScheduler ?: layer.startFrameScheduler().also { frameScheduler = it }
    }

    fun performDrawSubmission(draw: () -> Unit) {
        if (isClosed) return
        drawSubmissionCount += 1
        interopTransactionDrainCount += 1
        beforeDrawSubmission()
        pendingRenderInvalidation = false
        draw()
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        isSurfaceAttached = false
        var failure: Throwable? = null
        failure = closeFrameSchedulerPreserving(failure)
        failure = layer.closePreserving(failure)
        failure = renderDelegateCloseable.closePreserving(failure)
        failure?.let { throw it }
    }

    private fun applyRequestedSurfaceSize() {
        val size = requestedSurfaceSize
        if (size != null && size != appliedSurfaceSize) {
            appliedSurfaceSize = size
            layer.setSize(size)
        }
    }

    private fun closeFrameScheduler() {
        closeFrameSchedulerPreserving(null)?.let { throw it }
    }

    private fun closeFrameSchedulerPreserving(previousFailure: Throwable?): Throwable? {
        val scheduler = frameScheduler
        frameScheduler = null
        return scheduler.closePreserving(previousFailure)
    }

    private fun diagnostics(): WinUISkikoRenderHostDiagnostics =
        WinUISkikoRenderHostDiagnostics(
            isClosed = isClosed,
            isSurfaceAttached = isSurfaceAttached,
            isFrameSchedulerStarted = frameScheduler != null,
            requestedSurfaceSize = requestedSurfaceSize,
            appliedSurfaceSize = appliedSurfaceSize,
            pendingRenderInvalidation = pendingRenderInvalidation,
            renderInvalidationCount = renderInvalidationCount,
            delegatedRenderInvalidationCount = delegatedRenderInvalidationCount,
            drawSubmissionCount = drawSubmissionCount,
            interopTransactionDrainCount = interopTransactionDrainCount,
            renderApi = layer.renderApi,
            renderVersion = layer.renderVersion,
            lastRenderSize = layer.lastRenderSize,
            lastRenderedStateSize = layer.lastRenderedStateSize,
            pendingRenderStateSize = layer.pendingRenderStateSize,
            renderFailure = layer.renderFailure,
        )

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

internal data class WinUISkikoRenderHostDiagnostics(
    val isClosed: Boolean,
    val isSurfaceAttached: Boolean,
    val isFrameSchedulerStarted: Boolean,
    val requestedSurfaceSize: IntSize?,
    val appliedSurfaceSize: IntSize?,
    val pendingRenderInvalidation: Boolean,
    val renderInvalidationCount: Int,
    val delegatedRenderInvalidationCount: Int,
    val drawSubmissionCount: Int,
    val interopTransactionDrainCount: Int,
    val renderApi: GraphicsApi,
    val renderVersion: Long,
    val lastRenderSize: IntSize?,
    val lastRenderedStateSize: IntSize?,
    val pendingRenderStateSize: IntSize?,
    val renderFailure: String?,
)

internal interface WinUISkikoLayerAdapter : AutoCloseable {
    val component: FrameworkElement

    val renderApi: GraphicsApi

    val renderVersion: Long

    val lastRenderSize: IntSize?

    val lastRenderedStateSize: IntSize?

    val pendingRenderStateSize: IntSize?

    val renderFailure: String?

    fun setAccessibilityProvider(provider: WinUIAccessibilityProvider)

    fun notifyAccessibilityChanged(update: WinUIAccessibilityUpdate)

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

    override fun setAccessibilityProvider(provider: WinUIAccessibilityProvider) {
        layer.accessibilityProvider = provider
    }

    override fun notifyAccessibilityChanged(update: WinUIAccessibilityUpdate) {
        layer.notifyAccessibilityChanged(update.change)
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
