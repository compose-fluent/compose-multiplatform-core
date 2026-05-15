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

package androidx.compose.ui.viewinterop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WinUIInteropTransactionQueueTest {
    private val performed = mutableListOf<String>()

    private fun transaction(name: String) = object : WinUIInteropTransaction {
        override val actions: List<WinUIInteropAction> = listOf { performed += name }
        override val isInteropActive: Boolean = false
    }

    private fun emptyTransaction() = object : WinUIInteropTransaction {
        override val actions: List<WinUIInteropAction> = emptyList()
        override val isInteropActive: Boolean = false
    }

    @Test
    fun performsSingleTransactionWhenFrameCompletes() {
        val queue = WinUIInteropTransactionQueue()
        val index = queue.scheduleTransaction(transaction("t0"))

        assertTrue(performed.isEmpty())

        queue.performScheduledTransactions(index)

        assertEquals(listOf("t0"), performed)
    }

    @Test
    fun droppedFrameCompletionPerformsAllPendingTransactionsInOrder() {
        val queue = WinUIInteropTransactionQueue()
        queue.scheduleTransaction(transaction("t0"))
        queue.scheduleTransaction(transaction("t1"))
        val index = queue.scheduleTransaction(transaction("t2"))

        queue.performScheduledTransactions(index)

        assertEquals(listOf("t0", "t1", "t2"), performed)
    }

    @Test
    fun lateOlderFrameCompletionDoesNotRepeatTransactions() {
        val queue = WinUIInteropTransactionQueue()
        val first = queue.scheduleTransaction(transaction("t0"))
        val second = queue.scheduleTransaction(transaction("t1"))

        queue.performScheduledTransactions(second)
        queue.performScheduledTransactions(first)

        assertEquals(listOf("t0", "t1"), performed)
    }

    @Test
    fun emptyTransactionCompletionDrainsPendingRealTransactions() {
        val queue = WinUIInteropTransactionQueue()
        queue.scheduleTransaction(transaction("t0"))
        queue.scheduleTransaction(transaction("t1"))
        val emptyIndex = queue.scheduleTransaction(emptyTransaction())

        queue.performScheduledTransactions(emptyIndex)

        assertEquals(listOf("t0", "t1"), performed)
    }

    @Test
    fun emptyTransactionDoesNotConsumeBufferSlot() {
        val queue = WinUIInteropTransactionQueue()
        repeat(16) { index ->
            queue.scheduleTransaction(transaction("t$index"))
        }

        queue.scheduleTransaction(emptyTransaction())

        assertTrue(performed.isEmpty())
    }

    @Test
    fun bufferOverflowPerformsOldestTransactionsInOrder() {
        val queue = WinUIInteropTransactionQueue()

        repeat(21) { index ->
            queue.scheduleTransaction(transaction("t$index"))
        }

        assertEquals(listOf("t0", "t1", "t2", "t3", "t4"), performed)
    }

    @Test
    fun ringBufferWrapsOverManyFrames() {
        val queue = WinUIInteropTransactionQueue()

        repeat(64) { index ->
            val frameIndex = queue.scheduleTransaction(transaction("t$index"))
            queue.performScheduledTransactions(frameIndex)
        }

        assertEquals((0 until 64).map { "t$it" }, performed)
    }

    @Test
    fun mergedTransactionPerformsActionsInOrderAndCarriesInteropActiveState() {
        val merged = WinUIInteropTransaction.merge(
            listOf(
                transaction("t0"),
                emptyTransaction(),
                object : WinUIInteropTransaction {
                    override val actions: List<WinUIInteropAction> = listOf { performed += "t1" }
                    override val isInteropActive: Boolean = true
                }
            )
        )

        assertTrue(merged.isInteropActive)

        merged.performTransaction()

        assertEquals(listOf("t0", "t1"), performed)
    }
}
