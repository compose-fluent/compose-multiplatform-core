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
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.RootMeasurePolicy
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.WinUITextToolbar
import androidx.compose.ui.sensitiveContent
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
            assertFalse(owner.ownerStateForTest().isAccessibilityForcedForTesting)

            owner.rootForTest.forceAccessibilityForTesting(true)
            owner.rootForTest.setAccessibilityEventBatchIntervalMillis(37L)
            owner.onSemanticsChange()
            owner.onLayoutChange(owner.root)
            owner.voteFrameRate(60f)
            owner.dispatchOnScrollChanged(Offset(3f, 4f))
            owner.invalidateRootLayer()

            val state = owner.ownerStateForTest()
            assertTrue(state.isAccessibilityForcedForTesting)
            assertEquals(37L, state.accessibilityEventBatchIntervalMillis)
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

    private fun createOwner(
        events: OwnerEvents = OwnerEvents(),
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

private object TestPlatformFocusOwner : PlatformFocusOwner {
    override fun requestOwnerFocus(
        focusDirection: FocusDirection?,
        previouslyFocusedRect: Rect?,
    ): Boolean = true

    override fun clearOwnerFocus() = Unit

    override fun moveFocusInChildren(focusDirection: FocusDirection): Boolean = false

    override fun getEmbeddedViewFocusRect(): Rect? = null
}
