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

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNodeLifecycleCallback
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.Stable
import io.github.composefluent.winrt.runtime.RuntimeScope
import io.github.composefluent.winrt.runtime.WinRtWinUiResourceManagerBootstrap
import io.github.composefluent.winrt.runtime.WinRtWindowsAppSdkBootstrap
import microsoft.ui.dispatching.DispatcherQueue
import microsoft.ui.xaml.Application as XamlApplication
import kotlin.coroutines.EmptyCoroutineContext

fun Application(
    content: @Composable ApplicationScope.() -> Unit,
) {
    WinRtWindowsAppSdkBootstrap.initialize().use {
        RuntimeScope.initializeSingleThreaded().use {
            XamlApplication.start {
                WinUIApplicationRuntime(
                    application = XamlApplication(),
                ).setContent(content)
            }
        }
    }
}

@Stable
interface ApplicationScope {
    fun exitApplication()
}

internal interface WinUIApplicationContext : ApplicationScope {
    fun attachWindow(dispatcherQueue: DispatcherQueue)
}

private class WinUIApplicationRuntime(
    private val application: XamlApplication,
) : WinUIApplicationContext {
    private val resourceRegistration =
        WinRtWinUiResourceManagerBootstrap.registerForApplication(application)
    private val recomposer = Recomposer(EmptyCoroutineContext)
    private val root = WinUIApplicationNode()
    private val composition = Composition(
        applier = WinUIApplicationApplier(root),
        parent = recomposer,
    )

    private var dispatcherQueue: DispatcherQueue? = null
    private var isDisposeRequested = false
    private var isDisposed = false

    fun setContent(content: @Composable ApplicationScope.() -> Unit) {
        composition.setContent {
            content()
        }
    }

    override fun exitApplication() {
        if (isDisposeRequested) return
        isDisposeRequested = true
        val queue = dispatcherQueue
        if (queue != null) {
            val enqueued = queue.tryEnqueue {
                dispose()
                application.exit()
            }
            if (enqueued) return
        }
        dispose()
        application.exit()
    }

    override fun attachWindow(dispatcherQueue: DispatcherQueue) {
        if (this.dispatcherQueue == null) {
            this.dispatcherQueue = dispatcherQueue
        }
    }

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        composition.dispose()
        recomposer.close()
        root.removeAll()
        resourceRegistration?.close()
    }
}

internal open class WinUIApplicationNode : ComposeNodeLifecycleCallback {
    private val children = mutableListOf<WinUIApplicationNode>()

    open fun insertAt(index: Int, instance: WinUIApplicationNode) {
        children.add(index, instance)
    }

    open fun removeAt(index: Int, count: Int) {
        repeat(count) {
            children.removeAt(index).onRelease()
        }
    }

    open fun move(from: Int, to: Int, count: Int) {
        val moved = ArrayList<WinUIApplicationNode>(count)
        repeat(count) {
            moved += children.removeAt(from)
        }
        children.addAll(if (to > from) to - count else to, moved)
    }

    fun removeAll() {
        children.asReversed().forEach { it.onRelease() }
        children.clear()
    }

    override fun onReuse() = Unit

    override fun onDeactivate() = Unit

    override fun onRelease() {
        removeAll()
    }
}

internal class WinUIApplicationApplier(
    root: WinUIApplicationNode,
) : AbstractApplier<WinUIApplicationNode>(root) {
    override fun insertTopDown(index: Int, instance: WinUIApplicationNode) = Unit

    override fun insertBottomUp(index: Int, instance: WinUIApplicationNode) {
        current.insertAt(index, instance)
    }

    override fun remove(index: Int, count: Int) {
        current.removeAt(index, count)
    }

    override fun move(from: Int, to: Int, count: Int) {
        current.move(from, to, count)
    }

    override fun onClear() {
        root.removeAll()
    }

    override fun onEndChanges() = Unit
}
