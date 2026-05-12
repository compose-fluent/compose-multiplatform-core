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

package androidx.compose.ui.node

import androidx.collection.IntObjectMap
import androidx.collection.MutableIntObjectMap
import androidx.collection.mutableIntObjectMapOf
import androidx.compose.runtime.retain.RetainedValuesStore
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.SessionMutex
import androidx.compose.ui.autofill.Autofill
import androidx.compose.ui.autofill.AutofillManager
import androidx.compose.ui.autofill.AutofillTree
import androidx.compose.ui.draganddrop.DragAndDropManager
import androidx.compose.ui.draganddrop.DragAndDropNode
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusOwner
import androidx.compose.ui.focus.FocusOwnerImpl
import androidx.compose.ui.focus.PlatformFocusOwner
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeChangeRequester
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.InputModeManagerImpl
import androidx.compose.ui.input.indirect.IndirectPointerEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIconService
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputEvent
import androidx.compose.ui.input.pointer.PointerInputEventData
import androidx.compose.ui.input.pointer.PointerInputEventProcessor
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.WinUIPointerIconService
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.modifier.ModifierLocalManager
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.PlatformTextInputSessionScope
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WinUIClipboard
import androidx.compose.ui.platform.WinUIClipboardManager
import androidx.compose.ui.platform.WinUIPlatformTextInputSession
import androidx.compose.ui.platform.WinUIViewConfiguration
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.WindowInfoImpl
import androidx.compose.ui.semantics.EmptySemanticsModifier
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.spatial.RectManager
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PlatformTextInputService
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextInputService
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.InteropView
import microsoft.ui.xaml.FocusState
import microsoft.ui.xaml.UIElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal class WinUIOwner(
    override val root: LayoutNode,
    private val focusRoot: UIElement,
    override val retainedValuesStore: RetainedValuesStore,
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext,
    private val onMeasureAndLayoutRequested: () -> Unit = {},
    private val onInteropTreeChanged: () -> Unit = {},
) : Owner {
    private val onEndApplyChangesListeners = mutableListOf<() -> Unit>()
    private var hasPendingLayoutCompletedListener = false

    override val sharedDrawScope = LayoutNodeDrawScope()
    override val layoutNodes: MutableIntObjectMap<LayoutNode> = mutableIntObjectMapOf()
    override val rootForTest: RootForTest = WinUIRootForTest()
    override val hapticFeedBack: HapticFeedback = NoOpHapticFeedback
    override val inputModeManager: InputModeManager =
        InputModeManagerImpl(InputMode.Keyboard, InputModeChangeRequester { true })
    private val winUIClipboard = WinUIClipboard()
    @Suppress("DEPRECATION")
    override val clipboardManager: ClipboardManager = WinUIClipboardManager(winUIClipboard)
    override val clipboard: Clipboard = winUIClipboard
    override val accessibilityManager: AccessibilityManager = NoOpAccessibilityManager
    override val graphicsContext: GraphicsContext = UnsupportedGraphicsContext
    override val textToolbar: TextToolbar = NoOpTextToolbar
    @Suppress("DEPRECATION")
    override val autofillTree: AutofillTree = AutofillTree()
    @Suppress("DEPRECATION")
    override val autofill: Autofill? = null
    override val autofillManager: AutofillManager? = null
    override val density: Density = Density(1f)
    @Suppress("DEPRECATION")
    override val textInputService: TextInputService = TextInputService(NoOpPlatformTextInputService)
    override val softwareKeyboardController: SoftwareKeyboardController = NoOpSoftwareKeyboardController
    override val pointerIconService: PointerIconService = WinUIPointerIconService()
    override val semanticsOwner: SemanticsOwner =
        SemanticsOwner(root, EmptySemanticsModifier(), layoutNodes)
    override val focusOwner: FocusOwner = FocusOwnerImpl(WinUIPlatformFocusOwner(focusRoot), this)
    private val mutableWindowInfo = WindowInfoImpl()
    override val windowInfo: WindowInfo = mutableWindowInfo
    override val rectManager: RectManager = RectManager(layoutNodes)
    private val textInputSessionMutex = SessionMutex<WinUIPlatformTextInputSession>()
    private val pointerInputEventProcessor = PointerInputEventProcessor(root)
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override val fontLoader: Font.ResourceLoader = WinUIFontResourceLoader
    override val fontFamilyResolver: FontFamily.Resolver = createFontFamilyResolver()
    override val layoutDirection: LayoutDirection = LayoutDirection.Ltr
    override val localeList: LocaleList = LocaleList.current
    override val snapshotObserver = OwnerSnapshotObserver { it.invoke() }
    override val modifierLocalManager: ModifierLocalManager = ModifierLocalManager(this)
    override val dragAndDropManager: DragAndDropManager = NoOpDragAndDropManager
    private val measureAndLayoutDelegate = MeasureAndLayoutDelegate(root)
    override val measureIteration: Long
        get() = measureAndLayoutDelegate.measureIteration
    override val viewConfiguration: ViewConfiguration = WinUIViewConfiguration

    @InternalCoreApi
    override var showLayoutBounds: Boolean = false

    init {
        root.layoutDirection = layoutDirection
        root.viewConfiguration = viewConfiguration
        root.modifier = focusOwner.modifier
        snapshotObserver.startObserving()
        root.attach(this)
        measureAndLayoutDelegate.updateRootConstraints(Constraints())
    }

    fun dispose() {
        if (root.isAttached) {
            root.detach()
        }
        pointerInputEventProcessor.processCancel()
        snapshotObserver.stopObserving()
        rectManager.removeScheduledCallback()
    }

    fun setWindowFocused(isWindowFocused: Boolean) {
        mutableWindowInfo.isWindowFocused = isWindowFocused
    }

    fun setWindowContainerSize(size: IntSize) {
        mutableWindowInfo.containerSize = size
        mutableWindowInfo.containerDpSize = with(density) {
            DpSize(size.width.toDp(), size.height.toDp())
        }
        if (size.width > 0 && size.height > 0) {
            measureAndLayoutDelegate.updateRootConstraints(
                Constraints(maxWidth = size.width, maxHeight = size.height)
            )
            onMeasureAndLayoutRequested()
        }
    }

    override fun onRequestMeasure(
        layoutNode: LayoutNode,
        affectsLookahead: Boolean,
        forceRequest: Boolean,
        scheduleMeasureAndLayout: Boolean,
    ) {
        if (affectsLookahead) {
            if (
                measureAndLayoutDelegate.requestLookaheadRemeasure(layoutNode, forceRequest) &&
                    scheduleMeasureAndLayout
            ) {
                onMeasureAndLayoutRequested()
            }
        } else if (
            measureAndLayoutDelegate.requestRemeasure(layoutNode, forceRequest) &&
                scheduleMeasureAndLayout
        ) {
            onMeasureAndLayoutRequested()
        }
    }

    override fun onRequestRelayout(
        layoutNode: LayoutNode,
        affectsLookahead: Boolean,
        forceRequest: Boolean,
    ) {
        if (affectsLookahead) {
            if (measureAndLayoutDelegate.requestLookaheadRelayout(layoutNode, forceRequest)) {
                onMeasureAndLayoutRequested()
            }
        } else {
            if (measureAndLayoutDelegate.requestRelayout(layoutNode, forceRequest)) {
                onMeasureAndLayoutRequested()
            }
        }
    }

    override fun requestOnPositionedCallback(layoutNode: LayoutNode) {
        measureAndLayoutDelegate.requestOnPositionedCallback(layoutNode)
        onMeasureAndLayoutRequested()
    }

    override fun onPreAttach(node: LayoutNode) {
        layoutNodes[node.semanticsId] = node
    }

    override fun onPostAttach(node: LayoutNode) = Unit

    override fun onDetach(node: LayoutNode) {
        layoutNodes.remove(node.semanticsId)
        measureAndLayoutDelegate.onNodeDetached(node)
        snapshotObserver.clear(node)
        rectManager.remove(node)
    }

    override fun calculatePositionInWindow(localPosition: Offset): Offset = localPosition

    override fun calculateLocalPosition(positionInWindow: Offset): Offset = positionInWindow

    override fun requestAutofill(node: LayoutNode) = Unit

    override fun measureAndLayout(sendPointerUpdate: Boolean) {
        if (
            measureAndLayoutDelegate.hasPendingMeasureOrLayout ||
            measureAndLayoutDelegate.hasPendingOnPositionedCallbacks ||
            hasPendingLayoutCompletedListener
        ) {
            hasPendingLayoutCompletedListener = false
            measureAndLayoutDelegate.measureAndLayout()
            measureAndLayoutDelegate.dispatchOnPositionedCallbacks()
            rectManager.dispatchCallbacks()
        }
    }

    override fun measureAndLayout(layoutNode: LayoutNode, constraints: Constraints) {
        hasPendingLayoutCompletedListener = false
        measureAndLayoutDelegate.measureAndLayout(layoutNode, constraints)
        if (!measureAndLayoutDelegate.hasPendingMeasureOrLayout) {
            measureAndLayoutDelegate.dispatchOnPositionedCallbacks()
        }
        rectManager.dispatchCallbacks()
    }

    override fun forceMeasureTheSubtree(layoutNode: LayoutNode, affectsLookahead: Boolean) {
        measureAndLayoutDelegate.forceMeasureTheSubtree(layoutNode, affectsLookahead)
    }

    override fun createLayer(
        drawBlock: (canvas: Canvas, parentLayer: GraphicsLayer?) -> Unit,
        invalidateParentLayer: () -> Unit,
        explicitLayer: GraphicsLayer?,
    ): OwnedLayer = WinUIOwnerLayer(drawBlock, invalidateParentLayer)

    override fun onSemanticsChange() = Unit

    override fun onLayoutChange(layoutNode: LayoutNode) = Unit

    override fun onLayoutNodeDeactivated(layoutNode: LayoutNode) {
        rectManager.remove(layoutNode)
        notifyInteropTreeChanged()
    }

    override fun onPreLayoutNodeReused(layoutNode: LayoutNode, oldSemanticsId: Int) {
        layoutNodes.remove(oldSemanticsId)
        layoutNodes[layoutNode.semanticsId] = layoutNode
    }

    override fun onPostLayoutNodeReused(layoutNode: LayoutNode, oldSemanticsId: Int) {
        notifyInteropTreeChanged()
    }

    @InternalComposeUiApi
    override fun onInteropViewLayoutChange(view: InteropView) = Unit

    private fun notifyInteropTreeChanged() {
        onInteropTreeChanged()
        registerOnEndApplyChangesListener(onInteropTreeChanged)
    }

    override fun registerOnEndApplyChangesListener(listener: () -> Unit) {
        onEndApplyChangesListeners += listener
    }

    override fun onEndApplyChanges() {
        val listeners = onEndApplyChangesListeners.toList()
        onEndApplyChangesListeners.clear()
        listeners.forEach { it.invoke() }
    }

    override fun registerOnLayoutCompletedListener(listener: Owner.OnLayoutCompletedListener) {
        hasPendingLayoutCompletedListener = true
        measureAndLayoutDelegate.registerOnLayoutCompletedListener(listener)
        onMeasureAndLayoutRequested()
    }

    override suspend fun textInputSession(
        session: suspend PlatformTextInputSessionScope.() -> Nothing
    ): Nothing =
        textInputSessionMutex.withSessionCancellingPrevious(
            sessionInitializer = ::WinUIPlatformTextInputSession,
            session = session,
        )

    override fun screenToLocal(positionOnScreen: Offset): Offset = positionOnScreen

    override fun localToScreen(localPosition: Offset): Offset = localPosition

    @OptIn(InternalCoreApi::class)
    fun sendPointerEventForTest(
        eventType: PointerEventType,
        position: Offset,
        uptimeMillis: Long,
        pointerId: Long,
        down: Boolean,
        type: PointerType,
        buttons: PointerButtons,
        keyboardModifiers: PointerKeyboardModifiers,
        button: PointerButton?,
    ): Boolean {
        val event = PointerInputEvent(
            eventType = eventType,
            uptime = uptimeMillis,
            pointers = listOf(
                PointerInputEventData(
                    id = PointerId(pointerId),
                    uptime = uptimeMillis,
                    positionOnScreen = position,
                    position = position,
                    down = down,
                    pressure = 1f,
                    type = type,
                    activeHover = type == PointerType.Mouse,
                    scaleGestureFactor = 1f,
                    panGestureOffset = Offset.Zero,
                    originalEventPosition = position,
                )
            ),
            buttons = buttons,
            keyboardModifiers = keyboardModifiers,
            button = button,
        )
        val result = pointerInputEventProcessor.process(
            pointerEvent = event,
            positionCalculator = this,
            isInBounds = true,
        )
        return result.dispatchedToAPointerInputModifier || result.anyChangeConsumed
    }

    private inner class WinUIRootForTest : RootForTest {
        override val density: Density get() = this@WinUIOwner.density
        override val semanticsOwner: SemanticsOwner get() = this@WinUIOwner.semanticsOwner
        @Suppress("DEPRECATION")
        override val textInputService: TextInputService get() = this@WinUIOwner.textInputService

        override fun sendKeyEvent(keyEvent: KeyEvent): Boolean =
            focusOwner.dispatchKeyEvent(keyEvent) ||
                handleFocusKeys(keyEvent)

        override fun sendIndirectPointerEvent(indirectPointerEvent: IndirectPointerEvent): Boolean =
            false

        override fun measureAndLayoutForTest() {
            measureAndLayout()
        }
    }

    private fun handleFocusKeys(keyEvent: KeyEvent): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false
        val focusDirection = when (keyEvent.key) {
            Key.Tab -> if (keyEvent.isShiftPressed) FocusDirection.Previous else FocusDirection.Next
            Key.DirectionCenter -> FocusDirection.Enter
            Key.Back -> FocusDirection.Exit
            else -> return false
        }
        inputModeManager.requestInputMode(InputMode.Keyboard)
        return focusOwner.moveFocus(focusDirection)
    }
}

private object NoOpHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

@Suppress("DEPRECATION")
private object NoOpAccessibilityManager : AccessibilityManager {
    override fun calculateRecommendedTimeoutMillis(
        originalTimeoutMillis: Long,
        containsIcons: Boolean,
        containsText: Boolean,
        containsControls: Boolean,
    ): Long = originalTimeoutMillis
}

private object UnsupportedGraphicsContext : GraphicsContext {
    override fun createGraphicsLayer(): GraphicsLayer =
        error("WinUI GraphicsLayer is not implemented yet.")

    override fun releaseGraphicsLayer(layer: GraphicsLayer) = Unit
}

private object NoOpTextToolbar : TextToolbar {
    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = Unit

    override fun hide() = Unit

    override val status: TextToolbarStatus = TextToolbarStatus.Hidden
}

private object NoOpSoftwareKeyboardController : SoftwareKeyboardController {
    override fun show() = Unit

    override fun hide() = Unit
}

private object NoOpPlatformTextInputService : PlatformTextInputService {
    override fun startInput(
        value: TextFieldValue,
        imeOptions: ImeOptions,
        onEditCommand: (List<EditCommand>) -> Unit,
        onImeActionPerformed: (ImeAction) -> Unit,
    ) = Unit

    override fun stopInput() = Unit

    override fun showSoftwareKeyboard() = Unit

    override fun hideSoftwareKeyboard() = Unit

    override fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) = Unit

    override fun updateTextLayoutResult(
        textFieldValue: TextFieldValue,
        offsetMapping: OffsetMapping,
        textLayoutResult: TextLayoutResult,
        textFieldToRootTransform: (Matrix) -> Unit,
        innerTextFieldBounds: Rect,
        decorationBoxBounds: Rect,
    ) = Unit
}

private class WinUIPlatformFocusOwner(
    private val focusRoot: UIElement,
) : PlatformFocusOwner {
    override fun requestOwnerFocus(
        focusDirection: FocusDirection?,
        previouslyFocusedRect: Rect?,
    ): Boolean {
        runCatching {
            focusRoot.isTabStop = true
            focusRoot.focus(FocusState.Programmatic)
        }
        return true
    }

    override fun clearOwnerFocus() {
        runCatching {
            if (focusRoot.focusState != FocusState.Unfocused) {
                focusRoot.focus(FocusState.Unfocused)
            }
        }
    }

    override fun moveFocusInChildren(focusDirection: FocusDirection): Boolean = false

    override fun getEmbeddedViewFocusRect(): Rect? = null
}

private object NoOpDragAndDropManager : DragAndDropManager {
    override val modifier: Modifier = Modifier
    override val isRequestDragAndDropTransferRequired: Boolean = false

    override fun requestDragAndDropTransfer(node: DragAndDropNode, offset: Offset) = Unit

    override fun registerTargetInterest(target: DragAndDropTarget) = Unit

    override fun isInterestedTarget(target: DragAndDropTarget): Boolean = false
}

@Suppress("DEPRECATION")
private object WinUIFontResourceLoader : Font.ResourceLoader {
    override fun load(font: Font): Any = Any()
}
