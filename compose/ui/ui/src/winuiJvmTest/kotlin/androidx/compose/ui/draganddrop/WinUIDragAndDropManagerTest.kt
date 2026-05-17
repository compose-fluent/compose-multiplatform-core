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

import androidx.compose.runtime.retain.ForgetfulRetainedValuesStore
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.PlatformFocusOwner
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.RootMeasurePolicy
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.WinUIOwner
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WinUIDragAndDropManagerTest {
    @AfterTest
    fun tearDown() {
        WinUIDragAndDropManager.clearTargetInterestForTest()
        WinUIDragAndDropManager.setStarterForTest(null)
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

    @Test
    @OptIn(ExperimentalComposeUiApi::class)
    fun requestTransferRunsAttachedSourceThroughStarter() {
        var sourceOffset: Offset? = null
        var starterTransferData: DragAndDropTransferData? = null
        var starterDecorationSize: Size? = null
        var starterCalls = 0
        val sourceNode = DragAndDropNode(
            onStartTransfer = { offset ->
                sourceOffset = offset
                startDragAndDropTransfer(
                    transferData = DragAndDropTransferData("payload"),
                    decorationSize = Size(10f, 20f),
                    drawDragDecoration = {},
                )
            }
        )
        val owner = createOwner()
        try {
            val child = LayoutNode().also {
                it.modifier = SourceDragAndDropElement(sourceNode)
                it.measurePolicy = RootMeasurePolicy
            }
            owner.root.insertAt(0, child)
            owner.setWindowContainerSize(IntSize(100, 100))
            owner.measureAndLayout()

            assertFalse(WinUIDragAndDropManager.isRequestDragAndDropTransferRequired)

            sourceNode.requestDragAndDropTransfer(Offset.Unspecified)

            assertNull(sourceOffset)
            assertEquals(0, starterCalls)

            WinUIDragAndDropManager.setStarterForTest(
                WinUIDragAndDropStarter { transferData, decorationSize, _ ->
                    starterCalls += 1
                    starterTransferData = transferData
                    starterDecorationSize = decorationSize
                    true
                }
            )

            assertTrue(WinUIDragAndDropManager.isRequestDragAndDropTransferRequired)

            sourceNode.requestDragAndDropTransfer(Offset.Unspecified)

            assertEquals(Offset.Unspecified, sourceOffset)
            assertEquals("payload", starterTransferData?.nativeTransferData)
            assertEquals(Size(10f, 20f), starterDecorationSize)
            assertEquals(1, starterCalls)
        } finally {
            owner.dispose()
        }
    }
}

private class TestDragAndDropTarget : DragAndDropTarget {
    override fun onDrop(event: DragAndDropEvent): Boolean = true
}

private class SourceDragAndDropElement(
    private val node: DragAndDropNode,
) : ModifierNodeElement<DragAndDropNode>() {
    override fun create(): DragAndDropNode = node

    override fun update(node: DragAndDropNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "SourceDragAndDropNode"
    }

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = node.hashCode()
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

private object TestPlatformFocusOwner : PlatformFocusOwner {
    override fun requestOwnerFocus(
        focusDirection: FocusDirection?,
        previouslyFocusedRect: Rect?,
    ): Boolean = true

    override fun clearOwnerFocus() = Unit

    override fun moveFocusInChildren(focusDirection: FocusDirection): Boolean = false

    override fun getEmbeddedViewFocusRect(): Rect? = null
}
