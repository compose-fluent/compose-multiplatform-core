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

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.layout.RootMeasurePolicy
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.UiApplier
import androidx.compose.ui.node.WinUIOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.collectWinUIInteropRoots
import microsoft.ui.dispatching.DispatcherQueue
import microsoft.ui.xaml.UIElement
import microsoft.ui.xaml.Window
import microsoft.ui.xaml.controls.Canvas
import microsoft.ui.xaml.controls.ContentControl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Root host for a Compose hierarchy embedded in a WinUI tree.
 *
 * This is intentionally independent from Skiko/Desktop/AWT. Rendering, scheduling, and Owner
 * integration are filled in by the WinUI target rather than delegated to the desktop backend.
 */
class WinUIComposeView(
    val root: UIElement,
    private val setRootContent: (List<UIElement>) -> Unit,
) {
    constructor() : this(WinUIRootContentHost())

    internal val rootNode = LayoutNode().also {
        it.measurePolicy = RootMeasurePolicy
    }
    internal val owner = WinUIOwner(
        root = rootNode,
        focusRoot = root,
        onInteropTreeChanged = ::syncRootContent,
    )

    private var recomposer: Recomposer? = null
    private var recomposerJob: Job? = null
    private var frameClock: WinUIFrameClock? = null
    private var composition: Composition? = null
    private var saveableState: Map<String, List<Any?>>? = null
    private var saveableStateRegistry: SaveableStateRegistry? = null
    private var content: (@Composable () -> Unit)? = null
    private var currentInteropRoots: List<UIElement> = emptyList()
    private var isDisposed = false

    fun setContent(content: @Composable () -> Unit) {
        check(!isDisposed) {
            "Cannot set content on a disposed WinUIComposeView."
        }
        this.content = content
        val currentComposition = composition ?: createComposition().also {
            composition = it
        }
        currentComposition.setContent {
            val registry = remember {
                SaveableStateRegistry(saveableState) { true }.also {
                    saveableStateRegistry = it
                }
            }
            CompositionLocalProvider(
                androidx.lifecycle.compose.LocalLifecycleOwner provides owner.lifecycleOwner,
                LocalSaveableStateRegistry provides registry,
            ) {
                ProvideCommonCompositionLocals(
                    owner = owner,
                    uriHandler = createWinUIUriHandler(),
                    content = content,
                )
            }
        }
        syncRootContent()
    }

    fun disposeComposition() {
        saveableState = saveableStateRegistry?.performSave()
        saveableStateRegistry = null
        composition?.dispose()
        composition = null
        recomposer?.close()
        recomposer = null
        recomposerJob?.cancel()
        recomposerJob = null
        frameClock?.cancel()
        frameClock = null
        content = null
        updateRootContent(emptyList())
        rootNode.removeAll()
    }

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        disposeComposition()
        owner.dispose()
    }

    internal fun setWindowFocused(isWindowFocused: Boolean) {
        owner.setWindowFocused(isWindowFocused)
    }

    internal fun setWindowContainerSize(size: IntSize) {
        owner.setWindowContainerSize(size)
    }

    private fun createComposition(): Composition {
        WinUIScheduler.register(root.dispatcherQueue)
        GlobalSnapshotManager.ensureStarted(root.dispatcherQueue)
        val dispatcher = WinUIDispatcher(root.dispatcherQueue)
        val currentFrameClock = WinUIFrameClock(root.dispatcherQueue)
        val recomposerParentJob = SupervisorJob()
        val recomposerContext = dispatcher + currentFrameClock + recomposerParentJob
        val currentRecomposer = Recomposer(recomposerContext)
        recomposer = currentRecomposer
        recomposerJob = CoroutineScope(recomposerContext).launch {
            currentRecomposer.runRecomposeAndApplyChanges()
        }
        frameClock = currentFrameClock
        val applier = UiApplier(rootNode, ::syncRootContent)
        return Composition(
            applier = applier,
            parent = currentRecomposer,
        )
    }

    private fun syncRootContent() {
        updateRootContent(rootNode.collectWinUIInteropRoots())
        owner.measureAndLayout(sendPointerUpdate = false)
        updateRootContent(rootNode.collectWinUIInteropRoots())
    }

    private fun updateRootContent(content: List<UIElement>) {
        if (currentInteropRoots.hasSameIdentityOrder(content)) return
        currentInteropRoots = content
        setRootContent(content)
    }

    private constructor(host: WinUIRootContentHost) : this(host.root, host::setRootContent)
}

internal class WinUIDispatcher(
    private val dispatcherQueue: DispatcherQueue,
) : CoroutineDispatcher() {
    init {
        // KWINRT-002: force generated interface projection registry loading before hasThreadAccess.
        runCatching {
            Class.forName(
                "io.github.composefluent.winrt.projections.support.WinRTInterfaceProjectionRegistry"
            ).getDeclaredMethod("register").invoke(null)
        }
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean =
        runCatching { !dispatcherQueue.hasThreadAccess }.getOrDefault(true)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (!dispatcherQueue.tryEnqueue { block.run() }) {
            block.run()
        }
    }
}

internal class WinUIFrameClock(
    private val dispatcherQueue: DispatcherQueue,
) : MonotonicFrameClock {
    private var isFrameScheduled = false
    private var isCancelled = false
    private val frameClock = BroadcastFrameClock(::scheduleFrame)

    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
        frameClock.withFrameNanos(onFrame)

    fun cancel() {
        isCancelled = true
        frameClock.cancel(CancellationException("WinUIComposeView disposed"))
    }

    private fun scheduleFrame() {
        if (isCancelled || isFrameScheduled) return
        isFrameScheduled = true
        if (!dispatcherQueue.tryEnqueue {
                isFrameScheduled = false
                if (!isCancelled) {
                    frameClock.sendFrame(System.nanoTime())
                }
            }
        ) {
            isFrameScheduled = false
        }
    }
}

fun Window.setContent(content: @Composable () -> Unit): WinUIComposeView {
    val composeView = WinUIComposeView()
    composeView.setContent(content)
    this.content = composeView.root
    return composeView
}

private class WinUIRootContentHost {
    val root = ContentControl()
    private val interopContainer = WinUIInteropRootContainer()
    private val emptyContent = ContentControl()
    private var isContainerInstalled = false

    fun setRootContent(content: List<UIElement>) {
        if (content.isEmpty()) {
            interopContainer.clear()
            root.content = emptyContent
            isContainerInstalled = false
            return
        }
        if (!isContainerInstalled) {
            root.content = interopContainer.root
            isContainerInstalled = true
        }
        interopContainer.setChildren(content)
    }
}

private class WinUIInteropRootContainer {
    val root = Canvas()
    private val children = mutableListOf<UIElement>()

    fun setChildren(content: List<UIElement>) {
        removeStaleChildren(content)
        content.forEachIndexed { targetIndex, child ->
            if (children.getOrNull(targetIndex) === child) return@forEachIndexed
            val existingIndex = children.indexOfIdentity(child)
            if (existingIndex >= 0) {
                move(existingIndex, targetIndex)
            } else {
                insert(targetIndex, child)
            }
        }
        while (children.size > content.size) {
            removeAt(children.lastIndex)
        }
    }

    fun clear() {
        children.clear()
        root.children.clear()
    }

    private fun removeStaleChildren(content: List<UIElement>) {
        var index = 0
        while (index < children.size) {
            if (content.indexOfIdentity(children[index]) < 0) {
                removeAt(index)
            } else {
                index += 1
            }
        }
    }

    private fun move(from: Int, to: Int) {
        val child = children.removeAt(from)
        children.add(to, child)
        root.children.move(from.toUInt(), to.toUInt())
    }

    private fun insert(index: Int, child: UIElement) {
        children.add(index, child)
        root.children.add(index, child)
    }

    private fun removeAt(index: Int) {
        children.removeAt(index)
        root.children.removeAt(index)
    }
}

private fun List<UIElement>.indexOfIdentity(element: UIElement): Int {
    for (index in indices) {
        if (this[index].nativeObject.sameIdentity(element.nativeObject)) return index
    }
    return -1
}

private fun List<UIElement>.hasSameIdentityOrder(other: List<UIElement>): Boolean {
    if (size != other.size) return false
    return indices.all { index ->
        this[index].nativeObject.sameIdentity(other[index].nativeObject)
    }
}
