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

/**
 * Preserves [WinUIInteropTransaction.performTransaction] order relative to future presented
 * frames, even if a frame is dropped or an older frame completion arrives late.
 */
internal class WinUIInteropTransactionQueue {
    private val bufferLength = 16
    private var firstScheduledIndex: Long = 0
    private var lastScheduledIndex: Long = 0
    private val scheduledTransactions = arrayOfNulls<WinUIInteropTransaction>(bufferLength)

    fun scheduleTransaction(transaction: WinUIInteropTransaction): Long {
        if (transaction.actions.isEmpty()) {
            return lastScheduledIndex - 1
        }
        val index = lastScheduledIndex
        lastScheduledIndex++
        if (index - firstScheduledIndex >= bufferLength) {
            performScheduledTransactions(firstScheduledIndex)
        }
        scheduledTransactions[(index % bufferLength).toInt()] = transaction
        return index
    }

    fun performScheduledTransactions(index: Long) {
        while (firstScheduledIndex <= index && firstScheduledIndex < lastScheduledIndex) {
            val arrayIndex = (firstScheduledIndex % bufferLength).toInt()
            firstScheduledIndex++
            scheduledTransactions[arrayIndex]?.performTransaction()
            scheduledTransactions[arrayIndex] = null
        }
    }
}
