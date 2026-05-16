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

package androidx.compose.ui.draganddrop

import androidx.compose.ui.Modifier
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WinUIDragAndDropManagerTest {
    @AfterTest
    fun tearDown() {
        WinUIDragAndDropManager.clearTargetInterestForTest()
    }

    @Test
    fun exposesRootDragAndDropModifier() {
        assertNotEquals(Modifier, WinUIDragAndDropManager.modifier)
    }

    @Test
    fun tracksInterestedTargetsForCurrentSession() {
        val target = TestDragAndDropTarget()

        assertFalse(WinUIDragAndDropManager.isInterestedTarget(target))

        WinUIDragAndDropManager.registerTargetInterest(target)

        assertTrue(WinUIDragAndDropManager.isInterestedTarget(target))

        WinUIDragAndDropManager.clearTargetInterestForTest()

        assertFalse(WinUIDragAndDropManager.isInterestedTarget(target))
    }
}

private class TestDragAndDropTarget : DragAndDropTarget {
    override fun onDrop(event: DragAndDropEvent): Boolean = true
}
