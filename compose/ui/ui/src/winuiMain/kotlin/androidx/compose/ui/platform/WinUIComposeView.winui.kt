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
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LocalHostDefaultProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.retain.LocalRetainedValuesStoreProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.focus.WinUIPlatformFocusOwner
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.WinUIPointerIconService
import androidx.compose.ui.layout.RootMeasurePolicy
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.UiApplier
import androidx.compose.ui.node.WinUICoordinateMapper
import androidx.compose.ui.node.WinUIOwner
import androidx.compose.ui.skiko.RecordDrawRectRenderDecorator
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.WinUIInteropAction
import androidx.compose.ui.viewinterop.WinUIInteropTransaction
import androidx.compose.ui.viewinterop.WinUIRootContentHost
import androidx.compose.ui.viewinterop.WinUIRootContentControl
import androidx.compose.ui.viewinterop.collectWinUIInteropRoots
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.enableSavedStateHandles
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import io.github.composefluent.winrt.runtime.EventRegistrationToken
import microsoft.ui.xaml.UIElement
import microsoft.ui.xaml.Window
import microsoft.ui.xaml.RoutedEventHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.skia.Canvas
import org.jetbrains.skiko.GraphicsApi
import org.jetbrains.skiko.SkikoRenderDelegate
import org.jetbrains.skiko.winui.WinUIAccessibilityActionRequest
import org.jetbrains.skiko.winui.WinUIAccessibilitySnapshot

/**
 * Root host for a Compose hierarchy embedded in a WinUI tree.
 *
 * This is intentionally independent from Skiko/Desktop/AWT. Rendering, scheduling, and Owner
 * integration are filled in by the WinUI target rather than delegated to the desktop backend.
 */
class WinUIComposeView internal constructor(
    private val rootContentControl: WinUIRootContentControl,
    private val setRenderContent: (UIElement) -> Unit,
    private val setRootContent: (List<UIElement>) -> Unit,
    private val scheduleInteropUpdate: (WinUIInteropAction) -> Unit,
    private val retrieveInteropTransaction: () -> WinUIInteropTransaction,
    private val onSensitiveContentChanged: (Boolean) -> Unit = {},
) {
    constructor() : this(WinUIRootContentHost())

    internal constructor(
        onSensitiveContentChanged: (Boolean) -> Unit,
    ) : this(WinUIRootContentHost(), onSensitiveContentChanged)

    internal val rootNode = LayoutNode().also {
        it.measurePolicy = RootMeasurePolicy
    }
    val root: UIElement
        get() = rootContentControl

    @InternalComposeUiApi
    val renderVersionForTest: Long
        get() = renderHost.renderVersionForTest

    @InternalComposeUiApi
    val renderApiForTest: GraphicsApi
        get() = renderHost.renderApiForTest

    @InternalComposeUiApi
    val lastRenderSizeForTest: IntSize?
        get() = renderHost.lastRenderSizeForTest

    @InternalComposeUiApi
    val lastRenderedStateSizeForTest: IntSize?
        get() = renderHost.lastRenderedStateSizeForTest

    @InternalComposeUiApi
    val pendingRenderStateSizeForTest: IntSize?
        get() = renderHost.pendingRenderStateSizeForTest

    @InternalComposeUiApi
    val renderFailureForTest: String?
        get() = renderHost.renderFailureForTest

    @InternalComposeUiApi
    val lastDrawRectForTest: Rect
        get() = lastDrawRect

    @InternalComposeUiApi
    val isRenderSchedulerStartedForTest: Boolean
        get() = renderHost.isFrameSchedulerStartedForTest

    @InternalComposeUiApi
    val isLoadedRenderSchedulerRegistrationPendingForTest: Boolean
        get() = loadedRenderSchedulerToken != null

    @InternalComposeUiApi
    val accessibilitySnapshotForTest: WinUIAccessibilitySnapshot?
        get() = owner.accessibilityProvider.snapshot()

    @InternalComposeUiApi
    fun performAccessibilityActionForTest(request: WinUIAccessibilityActionRequest): Boolean =
        owner.accessibilityProvider.performAction(request)

    private val architectureComponentsOwner = DefaultArchitectureComponentsOwner(
        enforceMainThread = false,
    ).apply {
        enableSavedStateHandles()
        setLifecycleState(Lifecycle.State.RESUMED)
    }
    private val hostDefaultProvider = WinUIHostDefaultProvider(architectureComponentsOwner)
    private val retainedValuesStore = WinUIRetainedValuesStore()
    private val displayRequestController = WinUIDisplayRequestController()
    private val pointerCursorAdapter = WinUIPointerCursorAdapter(rootContentControl)
    private val pointerIconService = WinUIPointerIconService(pointerCursorAdapter::setIcon)
    private var lastDrawRect = Rect.Zero
    private val renderDelegate = RecordDrawRectRenderDecorator(
        object : SkikoRenderDelegate {
            override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
                render(canvas)
            }
        },
        onDrawRectChange = { lastDrawRect = it },
    )
    private val renderHost = WinUISkikoRenderHost(
        renderDelegate = renderDelegate,
        beforeDrawSubmission = ::drainPendingInteropTransactions,
    )
    init {
        setRenderContent(renderHost.component)
    }

    internal val owner = WinUIOwner(
        root = rootNode,
        platformFocusOwner = WinUIPlatformFocusOwner(root),
        retainedValuesStore = retainedValuesStore,
        onMeasureAndLayoutRequested = ::scheduleRootContentSync,
        onInteropTreeChanged = ::syncRootContent,
        onRootInvalidated = ::invalidateRootLayer,
        onInteropTransactionScheduled = ::scheduleInteropTransaction,
        onAccessibilityUpdate = renderHost::notifyAccessibilityChanged,
        onKeepScreenOnChanged = displayRequestController::setKeepScreenOn,
        onSensitiveContentChanged = onSensitiveContentChanged,
        scheduleOutOfFrame = ::scheduleOutOfFrame,
        coordinateMapper = WinUICoordinateMapper.forRoot(root),
        textToolbar = WinUITextToolbar { root },
        pointerIconService = pointerIconService,
    )
    init {
        renderHost.setAccessibilityProvider(owner.accessibilityProvider)
    }

    private var recomposer: Recomposer? = null
    private var recomposerJob: Job? = null
    private var frameClock: WinUIFrameClock? = null
    private var composition: Composition? = null
    private var saveableState: Map<String, List<Any?>>? = null
    private var saveableStateRegistry: SaveableStateRegistry? = null
    private var content: (@Composable () -> Unit)? = null
    private var currentInteropRoots: List<UIElement> = emptyList()
    private var isRootContentSyncScheduled = false
    private var isDisposed = false
    private var loadedRenderSchedulerHandler: RoutedEventHandler? = null
    private var loadedRenderSchedulerToken: EventRegistrationToken? = null
    private val keyInputAdapter = WinUIKeyInputAdapter(root, owner)
    private val pointerInputAdapter = WinUIPointerInputAdapter(root, owner)
    private val dragAndDropAdapter = WinUIDragAndDropAdapter(root, owner.winUIDragAndDropManager)

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
                androidx.lifecycle.compose.LocalLifecycleOwner provides
                    architectureComponentsOwner.lifecycleOwner,
                LocalSavedStateRegistryOwner provides
                    architectureComponentsOwner.savedStateRegistryOwner,
                LocalSaveableStateRegistry provides registry,
                LocalHostDefaultProvider provides hostDefaultProvider,
            ) {
                LocalRetainedValuesStoreProvider(retainedValuesStore) {
                    ProvideCommonCompositionLocals(
                        owner = owner,
                        uriHandler = createWinUIUriHandler(),
                        content = content,
                    )
                }
            }
        }
        startRenderSchedulerWhenLoaded()
        syncRootContent()
        requestRender()
    }

    fun disposeComposition() {
        val currentComposition = composition
        if (currentComposition != null) {
            saveableState = saveableStateRegistry?.performSave()
            saveableStateRegistry = null
            currentComposition.dispose()
        }
        composition = null
        recomposer?.close()
        recomposer = null
        recomposerJob?.cancel()
        recomposerJob = null
        frameClock?.cancel()
        frameClock = null
        content = null
        clearLoadedRenderSchedulerRequest()
        renderHost.detachSurface()
        updateRootContent(emptyList())
        rootNode.removeAll()
        displayRequestController.setKeepScreenOn(false)
        requestRender()
    }

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        disposeComposition()
        keyInputAdapter.dispose()
        pointerInputAdapter.dispose()
        dragAndDropAdapter.dispose()
        pointerCursorAdapter.dispose()
        retainedValuesStore.dispose()
        architectureComponentsOwner.setLifecycleState(Lifecycle.State.DESTROYED)
        owner.dispose()
        clearLoadedRenderSchedulerRequest()
        renderHost.close()
    }

    internal fun setWindowFocused(isWindowFocused: Boolean) {
        owner.setWindowFocused(isWindowFocused)
    }

    internal fun setWindowContainerSize(size: IntSize) {
        owner.setWindowContainerSize(size)
        renderHost.setSize(size)
        requestRender()
    }

    @InternalComposeUiApi
    fun setWindowContainerSizeForTest(size: IntSize) {
        setWindowContainerSize(size)
    }

    private fun createComposition(): Composition {
        val dispatcherQueue = requireRootDispatcherQueue()
        WinUIScheduler.register(dispatcherQueue)
        GlobalSnapshotManager.ensureStarted(dispatcherQueue)
        val dispatcher = WinUIDispatcher(dispatcherQueue)
        val currentFrameClock = WinUIFrameClock(dispatcherQueue)
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
        requestRender()
    }

    private fun render(canvas: Canvas) {
        renderHost.performDrawSubmission {
            if (isDisposed) return@performDrawSubmission
            owner.measureAndLayout(sendPointerUpdate = false)
            updateRootContent(rootNode.collectWinUIInteropRoots())
            rootNode.draw(canvas.asComposeCanvas(), graphicsLayer = null)
        }
    }

    private fun invalidateRootLayer() {
        scheduleRootContentSync()
        requestRender()
    }

    private fun startRenderSchedulerWhenLoaded() {
        if (runCatching { rootContentControl.isLoaded }.getOrDefault(false)) {
            startRenderScheduler()
        } else if (loadedRenderSchedulerToken == null) {
            val handler = RoutedEventHandler { _, _ ->
                clearLoadedRenderSchedulerRequest()
                if (!isDisposed) {
                    startRenderScheduler()
                    requestRender()
                }
            }
            loadedRenderSchedulerHandler = handler
            loadedRenderSchedulerToken = rootContentControl.loaded.add(handler)
        }
    }

    private fun startRenderScheduler() {
        if (!isDisposed && content != null) {
            renderHost.attachSurface()
            renderHost.startFrameScheduler()
        }
    }

    private fun clearLoadedRenderSchedulerRequest() {
        loadedRenderSchedulerToken?.let { token ->
            runCatching { rootContentControl.loaded.remove(token) }
            loadedRenderSchedulerToken = null
        }
        loadedRenderSchedulerHandler = null
    }

    private fun requestRender() {
        if (!isDisposed) {
            renderHost.requestRender()
        }
    }

    private fun scheduleRootContentSync() {
        if (isDisposed || isRootContentSyncScheduled) return
        isRootContentSyncScheduled = true
        if (!requireRootDispatcherQueue().tryEnqueue {
                isRootContentSyncScheduled = false
                if (!isDisposed) {
                    syncRootContent()
                }
            }
        ) {
            isRootContentSyncScheduled = false
            if (!isDisposed) {
                syncRootContent()
            }
        }
    }

    private fun scheduleOutOfFrame(block: () -> Unit) {
        if (!requireRootDispatcherQueue().tryEnqueue { block() }) {
            block()
        }
    }

    private fun requireRootDispatcherQueue() = checkNotNull(root.dispatcherQueue) {
        "WinUI root DispatcherQueue is not available."
    }

    private fun updateRootContent(content: List<UIElement>) {
        val contentChanged = !currentInteropRoots.hasSameIdentityOrder(content)
        if (contentChanged) {
            currentInteropRoots = content
            setRootContent(content)
        }
        val transaction = retrieveInteropTransaction()
        if (contentChanged || transaction.actions.isNotEmpty()) {
            transaction.performTransaction()
        }
        drainPendingInteropTransactions()
    }

    private fun drainPendingInteropTransactions() {
        while (true) {
            val transaction = retrieveInteropTransaction()
            if (transaction.actions.isEmpty()) return
            transaction.performTransaction()
        }
    }

    private fun scheduleInteropTransaction(action: WinUIInteropAction) {
        scheduleInteropUpdate(action)
        scheduleRootContentSync()
    }

    private constructor(host: WinUIRootContentHost) : this(
        host.root,
        host::setRenderContent,
        host::setRootContent,
        host::scheduleUpdate,
        host::retrieveTransaction,
    )

    private constructor(
        host: WinUIRootContentHost,
        onSensitiveContentChanged: (Boolean) -> Unit,
    ) : this(
        host.root,
        host::setRenderContent,
        host::setRootContent,
        host::scheduleUpdate,
        host::retrieveTransaction,
        onSensitiveContentChanged,
    )
}

fun Window.setContent(content: @Composable () -> Unit): WinUIComposeView {
    val composeView = WinUIComposeView()
    this.content = composeView.root
    composeView.setContent(content)
    return composeView
}

@InternalComposeUiApi
fun WinUIComposeView.sendPointerEventForTest(
    eventType: PointerEventType,
    position: Offset,
    uptimeMillis: Long,
    pointerId: Long = 0L,
    down: Boolean = eventType != PointerEventType.Release && eventType != PointerEventType.Exit,
    type: PointerType = PointerType.Touch,
    buttons: PointerButtons = PointerButtons(),
    keyboardModifiers: PointerKeyboardModifiers = PointerKeyboardModifiers(),
    button: PointerButton? = null,
    scrollDelta: Offset = Offset.Zero,
    isInBounds: Boolean = eventType != PointerEventType.Exit,
): Boolean = owner.sendPointerEventForTest(
    eventType = eventType,
    position = position,
    uptimeMillis = uptimeMillis,
    pointerId = pointerId,
    down = down,
    type = type,
    buttons = buttons,
    keyboardModifiers = keyboardModifiers,
    button = button,
    scrollDelta = scrollDelta,
    isInBounds = isInBounds,
)

private fun List<UIElement>.hasSameIdentityOrder(other: List<UIElement>): Boolean {
    if (size != other.size) return false
    return indices.all { index ->
        this[index].nativeObject.sameIdentity(other[index].nativeObject)
    }
}
