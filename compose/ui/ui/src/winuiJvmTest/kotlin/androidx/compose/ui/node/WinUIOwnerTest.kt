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

import androidx.compose.runtime.retain.ForgetfulRetainedValuesStore
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.PlatformFocusOwner
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.RootMeasurePolicy
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.WinUITextToolbar
import androidx.compose.ui.sensitiveContent
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WinUIOwnerTest {
    @Test
    fun ownerRecordsPlatformStateHooks() {
        val events = OwnerEvents()
        val owner = createOwner(events)
        try {
            assertFalse(owner.ownerStateForTest().accessibility.isAccessibilityForcedForTesting)

            owner.rootForTest.forceAccessibilityForTesting(true)
            owner.rootForTest.setAccessibilityEventBatchIntervalMillis(37L)
            owner.onSemanticsChange()
            owner.onLayoutChange(owner.root)
            owner.voteFrameRate(60f)
            owner.dispatchOnScrollChanged(Offset(3f, 4f))
            owner.invalidateRootLayer()

            val state = owner.ownerStateForTest()
            assertTrue(state.accessibility.isAccessibilityForcedForTesting)
            assertEquals(37L, state.accessibility.accessibilityEventBatchIntervalMillis)
            assertTrue(state.accessibility.currentSemanticsNodesInvalidated)
            assertTrue(state.accessibility.pendingSemanticsChange)
            assertEquals(listOf(owner.root.semanticsId), state.accessibility.pendingLayoutNodeIds)
            assertTrue(state.accessibility.hasPendingScrollChange)
            assertEquals(Offset(3f, 4f), state.accessibility.pendingScrollDelta)
            assertEquals(1, state.semanticsChangeCount)
            assertEquals(1, state.layoutChangeCount)
            assertEquals(owner.root.semanticsId, state.lastLayoutChangedSemanticsId)
            assertEquals(60f, state.lastFrameRateVote)
            assertEquals(1, state.scrollChangeCount)
            assertEquals(Offset(3f, 4f), state.lastScrollDelta)
            assertEquals(1, state.rootInvalidationCount)
            assertEquals(1, events.semanticsChanged)
            assertEquals(owner.root.semanticsId, events.lastLayoutChangedSemanticsId)
            assertEquals(Offset(3f, 4f), events.lastScrollDelta)
            assertEquals(1, events.rootInvalidated)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun accessibilityBridgeDefersEventsUntilAccessibilityIsForcedForTesting() {
        val owner = createOwner()
        try {
            owner.rootForTest.setAccessibilityEventBatchIntervalMillis(10_000L)
            owner.onSemanticsChange()

            var state = owner.ownerStateForTest().accessibility
            assertFalse(state.isAccessibilityForcedForTesting)
            assertTrue(state.currentSemanticsNodesInvalidated)
            assertTrue(state.pendingSemanticsChange)
            assertFalse(state.hasPendingFlush)

            owner.rootForTest.forceAccessibilityForTesting(true)

            state = owner.ownerStateForTest().accessibility
            assertTrue(state.isAccessibilityForcedForTesting)
            assertTrue(state.hasPendingFlush)

            owner.rootForTest.forceAccessibilityForTesting(false)

            state = owner.ownerStateForTest().accessibility
            assertFalse(state.isAccessibilityForcedForTesting)
            assertFalse(state.hasPendingFlush)
            assertTrue(state.currentSemanticsNodesInvalidated)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun keepScreenOnModifierUpdatesOwnerCount() {
        val events = OwnerEvents()
        val owner = createOwner(events)
        try {
            val node = LayoutNode().also {
                it.modifier = Modifier.keepScreenOn()
                it.measurePolicy = RootMeasurePolicy
            }

            owner.root.insertAt(0, node)

            assertEquals(1, owner.ownerStateForTest().keepScreenOnCount)
            assertEquals(listOf(true), events.keepScreenOnValues)

            owner.root.removeAt(0, 1)

            assertEquals(0, owner.ownerStateForTest().keepScreenOnCount)
            assertEquals(listOf(true, false), events.keepScreenOnValues)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun sensitiveContentModifierUpdatesOwnerCount() {
        val events = OwnerEvents()
        val owner = createOwner(events)
        try {
            val node = LayoutNode().also {
                it.modifier = Modifier.sensitiveContent()
                it.measurePolicy = RootMeasurePolicy
            }

            owner.root.insertAt(0, node)

            assertEquals(1, owner.ownerStateForTest().sensitiveContentCount)
            assertEquals(listOf(true), events.sensitiveContentValues)

            owner.root.removeAt(0, 1)

            assertEquals(0, owner.ownerStateForTest().sensitiveContentCount)
            assertEquals(listOf(true, false), events.sensitiveContentValues)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun outOfFrameExecutorSchedulesAndDrainsForTests() {
        val scheduled = mutableListOf<() -> Unit>()
        val owner = createOwner(
            scheduleOutOfFrame = { scheduled += it }
        )
        try {
            val executor = assertNotNull(owner.outOfFrameExecutor)
            val events = mutableListOf<String>()

            executor.schedule { events += "first" }
            executor.schedule { events += "second" }

            assertEquals(1, scheduled.size)
            assertEquals(emptyList(), events)

            owner.rootForTest.measureAndLayoutForTest()

            assertEquals(listOf("second", "first"), events)

            scheduled.single().invoke()
            assertEquals(listOf("second", "first"), events)
        } finally {
            owner.dispose()
        }

        assertNull(owner.outOfFrameExecutor)
    }

    @Test
    fun disposeSuppressesPendingAndFutureOwnerCallbacks() {
        val events = OwnerEvents()
        var measureRequests = 0
        val scheduledOutOfFrame = mutableListOf<() -> Unit>()
        val owner = createOwner(
            events = events,
            onMeasureAndLayoutRequested = { measureRequests += 1 },
            scheduleOutOfFrame = { scheduledOutOfFrame += it },
        )
        val executor = assertNotNull(owner.outOfFrameExecutor)
        val outOfFrameEvents = mutableListOf<String>()

        executor.schedule { outOfFrameEvents += "late" }
        assertEquals(1, scheduledOutOfFrame.size)

        owner.dispose()
        val measureRequestsAfterDispose = measureRequests

        scheduledOutOfFrame.single().invoke()
        owner.onRequestMeasure(
            layoutNode = owner.root,
            affectsLookahead = false,
            forceRequest = true,
            scheduleMeasureAndLayout = true,
        )
        owner.onRequestRelayout(
            layoutNode = owner.root,
            affectsLookahead = false,
            forceRequest = true,
        )
        owner.requestOnPositionedCallback(owner.root)
        owner.registerOnLayoutCompletedListener(
            object : Owner.OnLayoutCompletedListener {
                override fun onLayoutComplete() {
                    measureRequests += 100
                }
            }
        )
        owner.onSemanticsChange()
        owner.onLayoutChange(owner.root)
        owner.dispatchOnScrollChanged(Offset(1f, 2f))
        owner.invalidateRootLayer()

        assertEquals(emptyList(), outOfFrameEvents)
        assertEquals(measureRequestsAfterDispose, measureRequests)
        assertEquals(0, events.semanticsChanged)
        assertEquals(0, events.rootInvalidated)
        assertEquals(Offset.Unspecified, events.lastScrollDelta)
        assertNull(owner.outOfFrameExecutor)
    }

    @Test
    fun coordinateMappingDelegatesToMapper() {
        val owner = createOwner(
            coordinateMapper = WinUICoordinateMapper(
                calculatePositionInWindow = { it + Offset(10f, 20f) },
                calculateLocalPosition = { it - Offset(10f, 20f) },
                localToScreen = { it + Offset(30f, 40f) },
                screenToLocal = { it - Offset(30f, 40f) },
            )
        )
        try {
            assertEquals(
                Offset(11f, 22f),
                owner.calculatePositionInWindow(Offset(1f, 2f))
            )
            assertEquals(
                Offset(1f, 2f),
                owner.calculateLocalPosition(Offset(11f, 22f))
            )
            assertEquals(
                Offset(31f, 42f),
                owner.localToScreen(Offset(1f, 2f))
            )
            assertEquals(
                Offset(1f, 2f),
                owner.screenToLocal(Offset(31f, 42f))
            )

            val matrix = Matrix()
            owner.localToScreen(matrix)

            assertEquals(Offset(30f, 40f), matrix.map(Offset.Zero))
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun textToolbarTracksMenuState() {
        val owner = createOwner()
        try {
            val toolbar = owner.textToolbar as WinUITextToolbar
            var copyRequests = 0
            var pasteRequests = 0

            assertEquals(TextToolbarStatus.Hidden, toolbar.status)

            toolbar.showMenu(
                rect = Rect(1f, 2f, 3f, 4f),
                onCopyRequested = { copyRequests += 1 },
                onPasteRequested = { pasteRequests += 1 },
                onCutRequested = null,
                onSelectAllRequested = null,
                onAutofillRequested = null,
            )

            assertEquals(TextToolbarStatus.Shown, toolbar.status)
            assertEquals(Rect(1f, 2f, 3f, 4f), toolbar.menuForTest()?.rect)
            assertEquals(listOf("Copy", "Paste"), toolbar.menuForTest()?.itemLabels)

            toolbar.menuForTest()?.onCopyRequested?.invoke()
            toolbar.menuForTest()?.onPasteRequested?.invoke()
            assertEquals(1, copyRequests)
            assertEquals(1, pasteRequests)

            toolbar.hide()

            assertEquals(TextToolbarStatus.Hidden, toolbar.status)
            assertNull(toolbar.menuForTest())
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun ownerTracksInteropViewFocusRect() {
        val owner = createOwner()
        try {
            val rect = Rect(1f, 2f, 30f, 40f)

            owner.setInteropViewFocusRect(rect)

            assertEquals(rect, owner.ownerStateForTest().interopViewFocusRect)

            owner.setInteropViewFocusRect(null)

            assertNull(owner.ownerStateForTest().interopViewFocusRect)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun ownerTracksInteropViewBounds() {
        val owner = createOwner()
        try {
            val key = Any()
            val bounds = Rect(1f, 2f, 30f, 40f)

            owner.setInteropViewBounds(key, bounds)

            assertEquals(listOf(bounds), owner.ownerStateForTest().interopViewBounds)

            owner.setInteropViewBounds(key, null)

            assertEquals(emptyList(), owner.ownerStateForTest().interopViewBounds)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun pointerEventsInsideInteropViewBoundsAreNotDispatchedToCompose() {
        val owner = createOwner()
        val events = mutableListOf<PointerEventType>()
        try {
            val pointerNode = LayoutNode().also {
                it.modifier = PointerRecorderElement(events)
                it.measurePolicy = fixedMeasurePolicy(100, 100)
            }
            owner.root.insertAt(0, pointerNode)
            owner.setWindowContainerSize(IntSize(100, 100))
            owner.measureAndLayout()

            assertTrue(
                owner.sendPointerEventForTest(
                    eventType = PointerEventType.Press,
                    position = Offset(60f, 60f),
                    uptimeMillis = 1L,
                    pointerId = 1L,
                    down = true,
                    type = PointerType.Mouse,
                    buttons = PointerButtons(isPrimaryPressed = true),
                    keyboardModifiers = PointerKeyboardModifiers(),
                    button = PointerButton.Primary,
                )
            )
            assertEquals(listOf(PointerEventType.Press), events)

            owner.setInteropViewBounds(Any(), Rect(0f, 0f, 50f, 50f))

            assertFalse(
                owner.sendPointerEventForTest(
                    eventType = PointerEventType.Press,
                    position = Offset(10f, 10f),
                    uptimeMillis = 2L,
                    pointerId = 2L,
                    down = true,
                    type = PointerType.Mouse,
                    buttons = PointerButtons(isPrimaryPressed = true),
                    keyboardModifiers = PointerKeyboardModifiers(),
                    button = PointerButton.Primary,
                )
            )
            assertEquals(listOf(PointerEventType.Press), events)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun inputModeManagerUpdatesFromRequestsAndOwnerInputEvents() {
        val owner = createOwner()
        try {
            assertEquals(InputMode.Keyboard, owner.inputModeManager.inputMode)

            assertTrue(owner.inputModeManager.requestInputMode(InputMode.Touch))
            assertEquals(InputMode.Touch, owner.inputModeManager.inputMode)

            owner.sendKeyEvent(KeyEvent(key = Key.A, type = KeyEventType.KeyDown))
            assertEquals(InputMode.Keyboard, owner.inputModeManager.inputMode)

            owner.sendPointerEventForTest(
                eventType = PointerEventType.Press,
                position = Offset(1f, 2f),
                uptimeMillis = 3L,
                pointerId = 4L,
                down = true,
                type = PointerType.Mouse,
                buttons = PointerButtons(isPrimaryPressed = true),
                keyboardModifiers = PointerKeyboardModifiers(),
                button = PointerButton.Primary,
            )
            assertEquals(InputMode.Touch, owner.inputModeManager.inputMode)
        } finally {
            owner.dispose()
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun textInputSessionCancelsPreviousSessionAndActiveInputMethod() = runTest {
        val owner = createOwner()
        val events = mutableListOf<String>()
        try {
            val first = launch(start = CoroutineStart.UNDISPATCHED) {
                owner.textInputSession {
                    events += "first-session"
                    try {
                        startInputMethod(WinUITestInputMethodRequest)
                    } finally {
                        events += "first-cancelled"
                    }
                }
            }

            assertEquals(listOf("first-session"), events)

            val second = launch(start = CoroutineStart.UNDISPATCHED) {
                owner.textInputSession {
                    events += "second-session"
                    try {
                        startInputMethod(WinUITestInputMethodRequest)
                    } finally {
                        events += "second-cancelled"
                    }
                }
            }
            runCurrent()

            assertEquals(
                listOf("first-session", "first-cancelled", "second-session"),
                events,
            )

            second.cancelAndJoin()

            assertEquals(
                listOf(
                    "first-session",
                    "first-cancelled",
                    "second-session",
                    "second-cancelled",
                ),
                events,
            )
            assertTrue(first.isCancelled)
        } finally {
            owner.dispose()
        }
    }

    private fun createOwner(
        events: OwnerEvents = OwnerEvents(),
        onMeasureAndLayoutRequested: () -> Unit = {},
        scheduleOutOfFrame: (() -> Unit) -> Unit = { it() },
        coordinateMapper: WinUICoordinateMapper = WinUICoordinateMapper(),
    ): WinUIOwner {
        val root = LayoutNode().also {
            it.measurePolicy = RootMeasurePolicy
        }
        return WinUIOwner(
            root = root,
            platformFocusOwner = TestPlatformFocusOwner,
            retainedValuesStore = ForgetfulRetainedValuesStore,
            onMeasureAndLayoutRequested = onMeasureAndLayoutRequested,
            onRootInvalidated = { events.rootInvalidated += 1 },
            onSemanticsChanged = { events.semanticsChanged += 1 },
            onLayoutChanged = { _, semanticsId ->
                events.lastLayoutChangedSemanticsId = semanticsId
            },
            onScrollChanged = { events.lastScrollDelta = it },
            onKeepScreenOnChanged = { events.keepScreenOnValues += it },
            onSensitiveContentChanged = { events.sensitiveContentValues += it },
            scheduleOutOfFrame = scheduleOutOfFrame,
            coordinateMapper = coordinateMapper,
        )
    }
}

private class OwnerEvents {
    var rootInvalidated = 0
    var semanticsChanged = 0
    var lastLayoutChangedSemanticsId = -1
    var lastScrollDelta = Offset.Unspecified
    val keepScreenOnValues = mutableListOf<Boolean>()
    val sensitiveContentValues = mutableListOf<Boolean>()
}

private fun fixedMeasurePolicy(width: Int, height: Int) = MeasurePolicy { _, _ ->
    layout(width, height) {}
}

private data class PointerRecorderElement(
    val events: MutableList<PointerEventType>,
) : ModifierNodeElement<PointerRecorderNode>() {
    override fun create(): PointerRecorderNode = PointerRecorderNode(events)

    override fun update(node: PointerRecorderNode) {
        node.events = events
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "pointerRecorder"
    }
}

private class PointerRecorderNode(
    var events: MutableList<PointerEventType>,
) : Modifier.Node(), PointerInputModifierNode {
    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        if (pass == PointerEventPass.Main) {
            events += pointerEvent.type
        }
    }

    override fun onCancelPointerInput() = Unit
}

private object WinUITestInputMethodRequest : PlatformTextInputMethodRequest

private object TestPlatformFocusOwner : PlatformFocusOwner {
    override fun requestOwnerFocus(
        focusDirection: FocusDirection?,
        previouslyFocusedRect: Rect?,
    ): Boolean = true

    override fun clearOwnerFocus() = Unit

    override fun moveFocusInChildren(focusDirection: FocusDirection): Boolean = false

    override fun getEmbeddedViewFocusRect(): Rect? = null
}
