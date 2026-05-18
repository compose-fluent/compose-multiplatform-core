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

import androidx.compose.ui.graphics.ReusableGraphicsLayerScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinUIOwnerLayerTest {
    @Test
    fun updateDisplayListClearsDirtyStateUntilInvalidated() {
        var parentInvalidations = 0
        val layer = WinUIOwnerLayer(
            drawBlock = { _, _ -> },
            invalidateParentLayer = { parentInvalidations++ },
        )

        assertTrue(layer.stateForTest().isDirty)
        assertEquals(0, layer.stateForTest().displayListUpdateCount)

        layer.updateDisplayList()

        assertFalse(layer.stateForTest().isDirty)
        assertEquals(1, layer.stateForTest().displayListUpdateCount)
        assertEquals(0, parentInvalidations)

        layer.updateDisplayList()

        assertEquals(1, layer.stateForTest().displayListUpdateCount)

        layer.invalidate()

        assertTrue(layer.stateForTest().isDirty)
        assertEquals(1, parentInvalidations)

        layer.updateDisplayList()

        assertFalse(layer.stateForTest().isDirty)
        assertEquals(2, layer.stateForTest().displayListUpdateCount)
    }

    @Test
    fun resizeAndPropertyUpdatesDirtyDisplayListButMoveOnlyInvalidatesParent() {
        var parentInvalidations = 0
        val layer = WinUIOwnerLayer(
            drawBlock = { _, _ -> },
            invalidateParentLayer = { parentInvalidations++ },
        )
        layer.updateDisplayList()

        layer.resize(IntSize(20, 30))

        assertTrue(layer.stateForTest().isDirty)
        assertEquals(IntSize(20, 30), layer.stateForTest().size)
        assertEquals(1, parentInvalidations)

        layer.updateDisplayList()
        layer.move(IntOffset(4, 5))

        assertFalse(layer.stateForTest().isDirty)
        assertEquals(IntOffset(4, 5), layer.stateForTest().position)
        assertEquals(2, parentInvalidations)

        val scope = ReusableGraphicsLayerScope()
        scope.translationX = 12f
        layer.updateLayerProperties(scope)

        assertTrue(layer.stateForTest().isDirty)
        assertEquals(3, parentInvalidations)
    }

    @Test
    fun destroySuppressesInvalidationAndReuseResetsLayerState() {
        var oldParentInvalidations = 0
        var newParentInvalidations = 0
        val layer = WinUIOwnerLayer(
            drawBlock = { _, _ -> },
            invalidateParentLayer = { oldParentInvalidations++ },
        )
        layer.resize(IntSize(20, 30))
        layer.move(IntOffset(4, 5))
        layer.updateDisplayList()

        layer.destroy()

        assertTrue(layer.stateForTest().isDestroyed)
        assertFalse(layer.stateForTest().isDirty)

        layer.invalidate()

        assertFalse(layer.stateForTest().isDirty)
        assertEquals(2, oldParentInvalidations)

        layer.reuseLayer(
            drawBlock = { _, _ -> },
            invalidateParentLayer = { newParentInvalidations++ },
        )

        val state = layer.stateForTest()
        assertFalse(state.isDestroyed)
        assertTrue(state.isDirty)
        assertEquals(0, state.displayListUpdateCount)
        assertEquals(IntSize.Zero, state.size)
        assertEquals(IntOffset.Zero, state.position)
        assertEquals(1, newParentInvalidations)
    }
}
