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
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.RootMeasurePolicy
import androidx.compose.ui.sensitiveContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private fun createOwner(events: OwnerEvents = OwnerEvents()): WinUIOwner {
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
