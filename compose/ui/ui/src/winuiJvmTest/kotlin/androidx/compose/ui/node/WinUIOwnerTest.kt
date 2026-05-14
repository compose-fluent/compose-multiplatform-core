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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinUIOwnerTest {
    @Test
    fun ownerRecordsPlatformStateHooks() {
        val owner = createOwner()
        try {
            assertFalse(owner.ownerStateForTest().isAccessibilityForcedForTesting)

            owner.rootForTest.forceAccessibilityForTesting(true)
            owner.rootForTest.setAccessibilityEventBatchIntervalMillis(37L)
            owner.voteFrameRate(60f)
            owner.dispatchOnScrollChanged(Offset(3f, 4f))
            owner.invalidateRootLayer()

            val state = owner.ownerStateForTest()
            assertTrue(state.isAccessibilityForcedForTesting)
            assertEquals(37L, state.accessibilityEventBatchIntervalMillis)
            assertEquals(60f, state.lastFrameRateVote)
            assertEquals(1, state.scrollChangeCount)
            assertEquals(Offset(3f, 4f), state.lastScrollDelta)
            assertEquals(1, state.rootInvalidationCount)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun keepScreenOnModifierUpdatesOwnerCount() {
        val owner = createOwner()
        try {
            val node = LayoutNode().also {
                it.modifier = Modifier.keepScreenOn()
                it.measurePolicy = RootMeasurePolicy
            }

            owner.root.insertAt(0, node)

            assertEquals(1, owner.ownerStateForTest().keepScreenOnCount)

            owner.root.removeAt(0, 1)

            assertEquals(0, owner.ownerStateForTest().keepScreenOnCount)
        } finally {
            owner.dispose()
        }
    }

    private fun createOwner(): WinUIOwner {
        val root = LayoutNode().also {
            it.measurePolicy = RootMeasurePolicy
        }
        return WinUIOwner(
            root = root,
            platformFocusOwner = TestPlatformFocusOwner,
            retainedValuesStore = ForgetfulRetainedValuesStore,
        )
    }
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
