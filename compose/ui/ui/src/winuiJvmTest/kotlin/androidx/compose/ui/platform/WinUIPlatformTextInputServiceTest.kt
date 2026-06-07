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

import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.BackspaceCommand
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.DeleteSurroundingTextCommand
import androidx.compose.ui.text.input.FinishComposingTextCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.SetComposingRegionCommand
import androidx.compose.ui.text.input.SetComposingTextCommand
import androidx.compose.ui.text.input.SetSelectionCommand
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WinUIPlatformTextInputServiceTest {
    @AfterTest
    fun tearDown() {
        WinUIPlatformTextInputService.resetForTest()
    }

    @Test
    fun startUpdateAndStopInputTracksCurrentSessionState() {
        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue(
                text = "initial",
                selection = TextRange(1, 3),
                composition = TextRange(0, 2),
            ),
            imeOptions = ImeOptions.Default,
            onEditCommand = {},
            onImeActionPerformed = {},
        )

        assertTrue(WinUIPlatformTextInputService.isInputActive)
        assertEquals(
            TextFieldValue(
                text = "initial",
                selection = TextRange(1, 3),
                composition = TextRange(0, 2),
            ),
            WinUIPlatformTextInputService.currentValue,
        )

        WinUIPlatformTextInputService.updateState(
            oldValue = TextFieldValue(
                text = "initial",
                selection = TextRange(1, 3),
                composition = TextRange(0, 2),
            ),
            newValue = TextFieldValue(
                text = "updated",
                selection = TextRange(2),
                composition = TextRange(1, 4),
            ),
        )

        assertEquals(
            TextFieldValue(
                text = "initial",
                selection = TextRange(1, 3),
                composition = TextRange(0, 2),
            ),
            WinUIPlatformTextInputService.previousValue,
        )
        assertEquals(
            TextFieldValue(
                text = "updated",
                selection = TextRange(2),
                composition = TextRange(1, 4),
            ),
            WinUIPlatformTextInputService.currentValue,
        )

        WinUIPlatformTextInputService.stopInput()

        assertFalse(WinUIPlatformTextInputService.isInputActive)
        assertEquals(null, WinUIPlatformTextInputService.currentValue)
        assertEquals(null, WinUIPlatformTextInputService.previousValue)
    }

    @Test
    fun editCommandsAndImeActionsDelegateToActiveSessionCallbacks() {
        val editCommandBatches = mutableListOf<List<androidx.compose.ui.text.input.EditCommand>>()
        val imeActions = mutableListOf<ImeAction>()
        val commit = CommitTextCommand("hello", 1)
        val selection = SetSelectionCommand(1, 3)

        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue(""),
            imeOptions = ImeOptions.Default,
            onEditCommand = { editCommandBatches += it },
            onImeActionPerformed = { imeActions += it },
        )

        assertTrue(WinUIPlatformTextInputService.sendEditCommands(listOf(commit, selection)))
        assertTrue(WinUIPlatformTextInputService.performImeAction(ImeAction.Done))

        assertEquals(1, editCommandBatches.size)
        assertSame(commit, editCommandBatches.single()[0])
        assertSame(selection, editCommandBatches.single()[1])
        assertEquals(listOf(ImeAction.Done), imeActions)

        WinUIPlatformTextInputService.stopInput()

        assertFalse(WinUIPlatformTextInputService.sendEditCommands(listOf(commit)))
        assertFalse(WinUIPlatformTextInputService.performImeAction(ImeAction.Search))
    }

    @Test
    fun nativeTextInputHelpersDelegateStandardEditCommands() {
        val editCommandBatches = mutableListOf<List<androidx.compose.ui.text.input.EditCommand>>()

        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue(""),
            imeOptions = ImeOptions.Default,
            onEditCommand = { editCommandBatches += it },
            onImeActionPerformed = {},
        )

        assertTrue(WinUIPlatformTextInputService.setComposingText("draft"))
        assertTrue(WinUIPlatformTextInputService.setComposingRegion(1, 4))
        assertTrue(WinUIPlatformTextInputService.commitText("done"))
        assertTrue(WinUIPlatformTextInputService.setSelection(2, 5))
        assertTrue(WinUIPlatformTextInputService.deleteSurroundingText(1, 2))
        assertTrue(WinUIPlatformTextInputService.backspace())
        assertTrue(WinUIPlatformTextInputService.finishComposingText())

        assertEquals(
            listOf(
                listOf(SetComposingTextCommand("draft", 1)),
                listOf(SetComposingRegionCommand(1, 4)),
                listOf(CommitTextCommand("done", 1)),
                listOf(SetSelectionCommand(2, 5)),
                listOf(DeleteSurroundingTextCommand(1, 2)),
                listOf(BackspaceCommand()),
                listOf(FinishComposingTextCommand()),
            ),
            editCommandBatches,
        )

        WinUIPlatformTextInputService.stopInput()

        assertFalse(WinUIPlatformTextInputService.commitText("ignored"))
        assertFalse(WinUIPlatformTextInputService.setComposingText("ignored"))
        assertFalse(WinUIPlatformTextInputService.setComposingRegion(0, 1))
        assertFalse(WinUIPlatformTextInputService.finishComposingText())
        assertFalse(WinUIPlatformTextInputService.setSelection(0, 1))
        assertFalse(WinUIPlatformTextInputService.deleteSurroundingText(1, 0))
        assertFalse(WinUIPlatformTextInputService.backspace())
    }

    @Test
    fun startInputReplacesPreviousSessionCallbacks() {
        val firstCommands = mutableListOf<List<androidx.compose.ui.text.input.EditCommand>>()
        val secondCommands = mutableListOf<List<androidx.compose.ui.text.input.EditCommand>>()

        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue("first"),
            imeOptions = ImeOptions.Default,
            onEditCommand = { firstCommands += it },
            onImeActionPerformed = {},
        )
        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue("second"),
            imeOptions = ImeOptions.Default,
            onEditCommand = { secondCommands += it },
            onImeActionPerformed = {},
        )

        assertTrue(WinUIPlatformTextInputService.commitText("new"))

        assertEquals(emptyList(), firstCommands)
        assertEquals(1, secondCommands.size)
        assertEquals(listOf(CommitTextCommand("new", 1)), secondCommands.single())
        assertEquals(TextFieldValue("second"), WinUIPlatformTextInputService.currentValue)
    }

    @Test
    fun softwareKeyboardControllerDelegatesToActiveTextInputSession() {
        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue(""),
            imeOptions = ImeOptions.Default,
            onEditCommand = {},
            onImeActionPerformed = {},
        )

        assertFalse(WinUIPlatformTextInputService.isSoftwareKeyboardVisible)

        WinUISoftwareKeyboardController.show()

        assertTrue(WinUIPlatformTextInputService.isSoftwareKeyboardVisible)

        WinUISoftwareKeyboardController.hide()

        assertFalse(WinUIPlatformTextInputService.isSoftwareKeyboardVisible)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun startInputMethodTracksActiveRequestUntilCancelled() = runTest {
        val session = WinUIPlatformTextInputSession(this)
        val request = TestPlatformTextInputMethodRequest()

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            session.startInputMethod(request)
        }

        assertTrue(WinUIPlatformTextInputService.isInputMethodActive)
        assertTrue(WinUIPlatformTextInputService.isSoftwareKeyboardVisible)
        assertEquals(request, WinUIPlatformTextInputService.currentInputMethodRequest)

        WinUISoftwareKeyboardController.hide()

        assertFalse(WinUIPlatformTextInputService.isSoftwareKeyboardVisible)

        job.cancelAndJoin()

        assertFalse(WinUIPlatformTextInputService.isInputMethodActive)
        assertEquals(null, WinUIPlatformTextInputService.currentInputMethodRequest)
        assertFalse(WinUIPlatformTextInputService.isSoftwareKeyboardVisible)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun startInputMethodCancelsPreviousRequestBeforeStartingNext() = runTest {
        val session = WinUIPlatformTextInputSession(this)
        val firstRequest = TestPlatformTextInputMethodRequest()
        val secondRequest = TestPlatformTextInputMethodRequest()

        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            session.startInputMethod(firstRequest)
        }

        assertEquals(firstRequest, WinUIPlatformTextInputService.currentInputMethodRequest)

        val second = launch(start = CoroutineStart.UNDISPATCHED) {
            session.startInputMethod(secondRequest)
        }
        runCurrent()

        assertFalse(first.isActive)
        assertTrue(second.isActive)
        assertTrue(WinUIPlatformTextInputService.isInputMethodActive)
        assertEquals(secondRequest, WinUIPlatformTextInputService.currentInputMethodRequest)

        second.cancelAndJoin()

        assertFalse(WinUIPlatformTextInputService.isInputMethodActive)
    }
}

private class TestPlatformTextInputMethodRequest : PlatformTextInputMethodRequest
