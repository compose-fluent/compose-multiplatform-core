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

package androidx.compose.ui.platform

import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.FinishComposingTextCommand
import androidx.compose.ui.text.input.SetComposingTextCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinUIWindowsImeInputProcessorTest {
    @Test
    fun eventDispatcherDeliversImeEventsOnlyWhenQueuedTaskRuns() {
        val events = mutableListOf<String>()
        val queuedTasks = ArrayDeque<() -> Unit>()
        val dispatcher = WinUIWindowsImeEventDispatcher(
            dispatchAsync = { task ->
                queuedTasks += task
                true
            },
            onStartComposition = { events += "start" },
            onComposition = { composingText, resultText ->
                events += "composition:$composingText:$resultText"
                true
            },
            onEndComposition = {
                events += "end"
                true
            },
        )

        assertTrue(dispatcher.enqueueStartComposition())
        assertTrue(dispatcher.enqueueComposition(composingText = "zhong", resultText = "中"))
        assertTrue(dispatcher.enqueueEndComposition())

        assertEquals(emptyList(), events)

        while (queuedTasks.isNotEmpty()) {
            queuedTasks.removeFirst()()
        }

        assertEquals(
            listOf(
                "start",
                "composition:zhong:中",
                "end",
            ),
            events,
        )
    }

    @Test
    fun eventDispatcherDropsQueuedImeEventsAfterDispose() {
        val events = mutableListOf<String>()
        val queuedTasks = ArrayDeque<() -> Unit>()
        val dispatcher = WinUIWindowsImeEventDispatcher(
            dispatchAsync = { task ->
                queuedTasks += task
                true
            },
            onStartComposition = { events += "start" },
            onComposition = { _, _ ->
                events += "composition"
                true
            },
            onEndComposition = {
                events += "end"
                true
            },
        )

        assertTrue(dispatcher.enqueueStartComposition())
        assertTrue(dispatcher.enqueueComposition(composingText = "", resultText = "中"))
        dispatcher.dispose()

        while (queuedTasks.isNotEmpty()) {
            queuedTasks.removeFirst()()
        }

        assertEquals(emptyList(), events)
        assertFalse(dispatcher.enqueueEndComposition())
    }

    @Test
    fun eventDispatcherReturnsFalseWhenAsyncDispatchFails() {
        var didCallBridge = false
        var loggedFailure: Throwable? = null
        val failure = IllegalStateException("dispatcher closed")
        val dispatcher = WinUIWindowsImeEventDispatcher(
            dispatchAsync = { throw failure },
            onStartComposition = { didCallBridge = true },
            onComposition = { _, _ ->
                didCallBridge = true
                true
            },
            onEndComposition = {
                didCallBridge = true
                true
            },
            logFailure = { loggedFailure = it },
        )

        assertFalse(dispatcher.enqueueStartComposition())

        assertFalse(didCallBridge)
        assertEquals(failure, loggedFailure)
    }

    @Test
    fun compositionStringUpdatesComposeComposition() {
        val commands = mutableListOf<List<EditCommand>>()
        val processor = WinUIWindowsImeInputProcessor { batch ->
            commands += batch
            true
        }

        assertTrue(processor.onImeComposition(composingText = "zhong", resultText = ""))

        assertEquals(
            listOf<List<EditCommand>>(listOf(SetComposingTextCommand("zhong", 1))),
            commands,
        )
        assertTrue(processor.isCompositionActive)
    }

    @Test
    fun resultStringCommitsBeforeNextCompositionText() {
        val commands = mutableListOf<List<EditCommand>>()
        val processor = WinUIWindowsImeInputProcessor { batch ->
            commands += batch
            true
        }

        processor.onImeStartComposition()

        assertTrue(processor.onImeComposition(composingText = "wen", resultText = "中"))

        assertEquals(
            listOf(listOf(CommitTextCommand("中", 1), SetComposingTextCommand("wen", 1))),
            commands,
        )
        assertTrue(processor.isCompositionActive)
        assertTrue(processor.shouldSuppressCharacterFallback("中"))
        assertFalse(processor.shouldSuppressCharacterFallback("中"))
    }

    @Test
    fun endCompositionFinishesComposeComposition() {
        val commands = mutableListOf<List<EditCommand>>()
        val processor = WinUIWindowsImeInputProcessor { batch ->
            commands += batch
            true
        }

        processor.onImeStartComposition()

        assertTrue(processor.onImeEndComposition())

        assertEquals(
            listOf<List<EditCommand>>(listOf(FinishComposingTextCommand())),
            commands,
        )
        assertFalse(processor.isCompositionActive)
    }

    @Test
    fun duplicateCharacterFallbackQueueTracksMultiCharacterCommit() {
        val processor = WinUIWindowsImeInputProcessor { true }

        processor.onImeComposition(composingText = "", resultText = "中文")

        assertTrue(processor.shouldSuppressCharacterFallback("中"))
        assertTrue(processor.shouldSuppressCharacterFallback("文"))
        assertFalse(processor.shouldSuppressCharacterFallback("文"))
    }

    @Test
    fun mismatchedCharacterFallbackClearsSuppressionQueue() {
        val processor = WinUIWindowsImeInputProcessor { true }

        processor.onImeComposition(composingText = "", resultText = "中")

        assertFalse(processor.shouldSuppressCharacterFallback("x"))
        assertFalse(processor.shouldSuppressCharacterFallback("中"))
    }
}
