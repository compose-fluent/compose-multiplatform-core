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

import androidx.compose.ui.unit.IntSize
import microsoft.ui.xaml.FrameworkElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WinUISkikoRenderHostTest {
    @Test
    fun delegatesRenderRequestsAndResizeToLayer() {
        val layer = FakeWinUISkikoLayerAdapter()
        val host = WinUISkikoRenderHost(layer)

        host.requestRender()
        host.requestRender(throttledToVsync = false)
        host.setSize(IntSize(30, 40))

        assertEquals(listOf(true, false), layer.renderRequests)
        assertEquals(listOf(IntSize(30, 40)), layer.sizes)
    }

    @Test
    fun exposesRenderDiagnosticsFromLayer() {
        val layer = FakeWinUISkikoLayerAdapter()
        val host = WinUISkikoRenderHost(layer)

        layer.renderVersion = 7L
        layer.lastRenderSize = IntSize(80, 60)
        layer.lastRenderedStateSize = IntSize(80, 60)
        layer.renderFailure = "render failed"

        assertEquals(7L, host.renderVersionForTest)
        assertEquals(IntSize(80, 60), host.lastRenderSizeForTest)
        assertEquals(IntSize(80, 60), host.lastRenderedStateSizeForTest)
        assertEquals("render failed", host.renderFailureForTest)
    }

    @Test
    fun startsFrameSchedulerOnlyOnceAndClosesItWithLayer() {
        val layer = FakeWinUISkikoLayerAdapter()
        val host = WinUISkikoRenderHost(layer)

        assertFalse(host.isFrameSchedulerStartedForTest)
        val firstScheduler = host.startFrameScheduler()
        val secondScheduler = host.startFrameScheduler()

        assertTrue(host.isFrameSchedulerStartedForTest)
        assertSame(firstScheduler, secondScheduler)
        assertEquals(1, layer.startFrameSchedulerCount)

        host.close()
        host.close()

        assertFalse(host.isFrameSchedulerStartedForTest)
        assertEquals(listOf("startFrameScheduler", "closeFrameScheduler", "closeLayer"), layer.events)
        assertEquals(1, layer.scheduler.closeCount)
        assertEquals(1, layer.closeCount)
    }

    @Test
    fun ignoresRenderAndResizeAfterClose() {
        val layer = FakeWinUISkikoLayerAdapter()
        val host = WinUISkikoRenderHost(layer)

        host.close()
        host.requestRender()
        host.setSize(IntSize(10, 20))

        assertEquals(emptyList(), layer.renderRequests)
        assertEquals(emptyList(), layer.sizes)
        assertEquals(1, layer.closeCount)
    }

    @Test
    fun rejectsStartingFrameSchedulerAfterClose() {
        val layer = FakeWinUISkikoLayerAdapter()
        val host = WinUISkikoRenderHost(layer)

        host.close()

        assertFailsWith<IllegalStateException> {
            host.startFrameScheduler()
        }
        assertEquals(0, layer.startFrameSchedulerCount)
    }
}

private class FakeWinUISkikoLayerAdapter : WinUISkikoLayerAdapter {
    val events = mutableListOf<String>()
    val renderRequests = mutableListOf<Boolean>()
    val sizes = mutableListOf<IntSize>()
    val scheduler = FakeFrameScheduler(events)
    var startFrameSchedulerCount = 0
    var closeCount = 0
    override var renderVersion: Long = 0L
    override var lastRenderSize: IntSize? = null
    override var lastRenderedStateSize: IntSize? = null
    override var renderFailure: String? = null

    override val component: FrameworkElement
        get() = error("Fake layer does not expose a WinUI component.")

    override fun requestRender(throttledToVsync: Boolean) {
        renderRequests += throttledToVsync
    }

    override fun setSize(size: IntSize) {
        sizes += size
    }

    override fun startFrameScheduler(): AutoCloseable {
        startFrameSchedulerCount += 1
        events += "startFrameScheduler"
        return scheduler
    }

    override fun close() {
        closeCount += 1
        events += "closeLayer"
    }
}

private class FakeFrameScheduler(
    private val events: MutableList<String>,
) : AutoCloseable {
    var closeCount = 0

    override fun close() {
        closeCount += 1
        events += "closeFrameScheduler"
    }
}
