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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.MultiParagraph
import androidx.compose.ui.text.TextLayoutInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.BackspaceCommand
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.DeleteSurroundingTextCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.FinishComposingTextCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.SetComposingRegionCommand
import androidx.compose.ui.text.input.SetComposingTextCommand
import androidx.compose.ui.text.input.SetSelectionCommand
import androidx.compose.ui.text.input.TextEditingScope
import androidx.compose.ui.text.input.TextEditorState
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
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
import windows.ui.text.core.CoreTextFormatUpdatingResult
import windows.ui.text.core.CoreTextInputPaneDisplayPolicy
import windows.ui.text.core.CoreTextInputScope
import windows.ui.text.core.CoreTextRange
import windows.ui.text.core.CoreTextSelectionUpdatingResult
import windows.ui.text.core.CoreTextTextUpdatingResult

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
    fun nativeTextInputBridgeTracksFocusAndDelegatesEditingEvents() {
        val editCommandBatches = mutableListOf<List<androidx.compose.ui.text.input.EditCommand>>()
        val imeActions = mutableListOf<ImeAction>()
        val bridge = WinUIPlatformTextInputService.nativeBridge

        assertFalse(bridge.enterFocus())
        assertFalse(bridge.commitText("ignored"))
        assertFalse(bridge.performImeAction(ImeAction.Done))

        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue(""),
            imeOptions = ImeOptions.Default,
            onEditCommand = { editCommandBatches += it },
            onImeActionPerformed = { imeActions += it },
        )

        assertFalse(bridge.isFocused)
        assertTrue(bridge.enterFocus())
        assertTrue(bridge.isFocused)
        assertTrue(WinUIPlatformTextInputService.isNativeTextInputFocused)

        assertTrue(bridge.setComposingText("draft", newCursorPosition = 2))
        assertTrue(bridge.setComposingRegion(0, 5))
        assertTrue(bridge.commitText("done"))
        assertTrue(bridge.setSelection(1, 3))
        assertTrue(bridge.deleteSurroundingText(1, 0))
        assertTrue(bridge.backspace())
        assertTrue(bridge.finishComposingText())
        assertTrue(bridge.performImeAction(ImeAction.Search))

        assertEquals(
            listOf(
                listOf(SetComposingTextCommand("draft", 2)),
                listOf(SetComposingRegionCommand(0, 5)),
                listOf(CommitTextCommand("done", 1)),
                listOf(SetSelectionCommand(1, 3)),
                listOf(DeleteSurroundingTextCommand(1, 0)),
                listOf(BackspaceCommand()),
                listOf(FinishComposingTextCommand()),
            ),
            editCommandBatches,
        )
        assertEquals(listOf(ImeAction.Search), imeActions)

        WinUISoftwareKeyboardController.show()
        assertTrue(WinUIPlatformTextInputService.isSoftwareKeyboardVisible)
        assertTrue(bridge.exitFocus())
        assertFalse(bridge.isFocused)
        assertFalse(WinUIPlatformTextInputService.isSoftwareKeyboardVisible)
    }

    @Test
    fun startInputReplacesNativeTextInputBridgeFocusState() {
        val bridge = WinUIPlatformTextInputService.nativeBridge

        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue("first"),
            imeOptions = ImeOptions.Default,
            onEditCommand = {},
            onImeActionPerformed = {},
        )
        assertTrue(bridge.enterFocus())
        assertTrue(bridge.isFocused)

        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue("second"),
            imeOptions = ImeOptions.Default,
            onEditCommand = {},
            onImeActionPerformed = {},
        )

        assertFalse(bridge.isFocused)
        assertEquals(TextFieldValue("second"), WinUIPlatformTextInputService.currentValue)
    }

    @Test
    fun coreTextBridgeHandlesRequestAndUpdateEvents() {
        val editCommandBatches = mutableListOf<List<androidx.compose.ui.text.input.EditCommand>>()
        val bridge = WinUIPlatformTextInputService.nativeBridge
        val editContext = FakeCoreTextEditContext()

        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue("hello", selection = TextRange(1, 4)),
            imeOptions = ImeOptions.Default,
            onEditCommand = { editCommandBatches += it },
            onImeActionPerformed = {},
        )

        assertTrue(bridge.attachCoreTextForCurrentInput(editContext))
        assertTrue(bridge.isCoreTextSessionActive)
        assertTrue(bridge.isFocused)
        assertEquals("Compose WinUI text input", editContext.name)
        assertEquals(CoreTextInputScope.Default, editContext.inputScope)
        assertEquals(CoreTextInputPaneDisplayPolicy.Automatic, editContext.inputPaneDisplayPolicy)
        assertFalse(editContext.didNotifyFocusEnter)
        assertEquals(1, editContext.textChanges.size)
        editContext.textChanges.single().let { initialChange ->
            assertCoreTextRangeEquals(CoreTextRange(0, 0), initialChange.modifiedRange)
            assertEquals(5, initialChange.newLength)
            assertCoreTextRangeEquals(CoreTextRange(1, 4), initialChange.newSelection)
        }

        val textRequest = FakeCoreTextTextRequest(CoreTextRange(1, 4))
        editContext.dispatchTextRequested(textRequest)
        assertEquals("ell", textRequest.text)

        val selectionRequest = FakeCoreTextSelectionRequest()
        editContext.dispatchSelectionRequested(selectionRequest)
        assertCoreTextRangeEquals(CoreTextRange(1, 4), selectionRequest.selection)

        val textUpdate = FakeCoreTextTextUpdatingEvent(
            range = CoreTextRange(1, 4),
            text = "abc",
            newSelection = CoreTextRange(4, 4),
        )
        editContext.dispatchTextUpdating(textUpdate)
        assertEquals(CoreTextTextUpdatingResult.Succeeded, textUpdate.result)
        assertEquals(
            listOf(
                SetSelectionCommand(1, 4),
                CommitTextCommand("abc", 1),
                SetSelectionCommand(4, 4),
            ),
            editCommandBatches.single(),
        )

        val selectionUpdate = FakeCoreTextSelectionUpdatingEvent(CoreTextRange(0, 2))
        editContext.dispatchSelectionUpdating(selectionUpdate)
        assertEquals(CoreTextSelectionUpdatingResult.Succeeded, selectionUpdate.result)
        assertEquals(listOf(SetSelectionCommand(0, 2)), editCommandBatches.last())

        val formatUpdate = FakeCoreTextFormatUpdatingEvent()
        editContext.dispatchFormatUpdating(formatUpdate)
        assertEquals(CoreTextFormatUpdatingResult.Failed, formatUpdate.result)

        val deleteUpdate = FakeCoreTextTextUpdatingEvent(
            range = CoreTextRange(1, 4),
            text = "",
            newSelection = CoreTextRange(1, 1),
        )
        editContext.dispatchTextUpdating(deleteUpdate)
        assertEquals(CoreTextTextUpdatingResult.Succeeded, deleteUpdate.result)
        assertEquals(
            listOf(
                SetSelectionCommand(1, 4),
                CommitTextCommand("", 1),
                SetSelectionCommand(1, 1),
            ),
            editCommandBatches.last(),
        )
    }

    @Test
    fun coreTextBridgeDispatchesComposingUpdatesUntilCompositionCompletes() {
        val editCommandBatches = mutableListOf<List<androidx.compose.ui.text.input.EditCommand>>()
        val bridge = WinUIPlatformTextInputService.nativeBridge
        val editContext = FakeCoreTextEditContext()

        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue("input", selection = TextRange(2)),
            imeOptions = ImeOptions.Default,
            onEditCommand = { editCommandBatches += it },
            onImeActionPerformed = {},
        )

        assertTrue(bridge.attachCoreTextForCurrentInput(editContext))

        editContext.dispatchCompositionStarted()
        val composingUpdate = FakeCoreTextTextUpdatingEvent(
            range = CoreTextRange(2, 2),
            text = "draft",
            newSelection = CoreTextRange(7, 7),
        )
        editContext.dispatchTextUpdating(composingUpdate)

        assertEquals(CoreTextTextUpdatingResult.Succeeded, composingUpdate.result)
        assertEquals(
            listOf(
                SetSelectionCommand(2, 2),
                SetComposingTextCommand("draft", 1),
                SetSelectionCommand(7, 7),
            ),
            editCommandBatches.single(),
        )

        editContext.dispatchCompositionCompleted()

        assertEquals(listOf(FinishComposingTextCommand()), editCommandBatches.last())

        val committedUpdate = FakeCoreTextTextUpdatingEvent(
            range = CoreTextRange(0, 5),
            text = "final",
            newSelection = CoreTextRange(5, 5),
        )
        editContext.dispatchTextUpdating(committedUpdate)

        assertEquals(CoreTextTextUpdatingResult.Succeeded, committedUpdate.result)
        assertEquals(
            listOf(
                SetSelectionCommand(0, 5),
                CommitTextCommand("final", 1),
                SetSelectionCommand(5, 5),
            ),
            editCommandBatches.last(),
        )
    }

    @Test
    fun coreTextBridgeNotifiesNativeStateChangesAfterAttach() {
        val bridge = WinUIPlatformTextInputService.nativeBridge
        val editContext = FakeCoreTextEditContext()

        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue("hello", selection = TextRange(1)),
            imeOptions = ImeOptions.Default,
            onEditCommand = {},
            onImeActionPerformed = {},
        )
        assertTrue(bridge.attachCoreTextForCurrentInput(editContext))

        editContext.textChanges.clear()
        WinUIPlatformTextInputService.updateState(
            oldValue = TextFieldValue("hello", selection = TextRange(1)),
            newValue = TextFieldValue("heLlo", selection = TextRange(3)),
        )

        val textChange = editContext.textChanges.single()
        assertCoreTextRangeEquals(CoreTextRange(2, 3), textChange.modifiedRange)
        assertEquals(5, textChange.newLength)
        assertCoreTextRangeEquals(CoreTextRange(3, 3), textChange.newSelection)

        WinUIPlatformTextInputService.updateState(
            oldValue = TextFieldValue("heLlo", selection = TextRange(3)),
            newValue = TextFieldValue("heLlo", selection = TextRange(0, 2)),
        )

        assertCoreTextRangeEquals(CoreTextRange(0, 2), editContext.selectionChanges.single())

        WinUIPlatformTextInputService.stopInput()

        assertFalse(bridge.isCoreTextSessionActive)
        assertEquals(8, editContext.removedHandlerCount)
    }

    @Test
    fun updateTextLayoutResultStoresRootTransformedBoundsAndNotifiesCoreTextLayout() {
        val bridge = WinUIPlatformTextInputService.nativeBridge
        val editContext = FakeCoreTextEditContext()

        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue("hello", selection = TextRange(1)),
            imeOptions = ImeOptions.Default,
            onEditCommand = {},
            onImeActionPerformed = {},
        )
        assertTrue(bridge.attachCoreTextForCurrentInput(editContext))

        WinUIPlatformTextInputService.updateTextLayoutResult(
            textFieldValue = TextFieldValue("hello", selection = TextRange(1)),
            offsetMapping = OffsetMapping.Identity,
            textLayoutResult = testTextLayoutResult("hello"),
            textFieldToRootTransform = { matrix -> matrix.setTranslate(Offset(20f, 30f)) },
            innerTextFieldBounds = Rect(1f, 2f, 11f, 12f),
            decorationBoxBounds = Rect(0f, 1f, 12f, 13f),
        )

        assertEquals(
            WinUITextLayoutBounds(
                innerTextFieldBounds = Rect(21f, 32f, 31f, 42f),
                decorationBoxBounds = Rect(20f, 31f, 32f, 43f),
            ),
            WinUIPlatformTextInputService.currentTextLayoutBoundsInRoot,
        )
        assertEquals(1, editContext.layoutChangedCount)

        val layoutRequest = FakeCoreTextLayoutRequest()
        editContext.dispatchLayoutRequested(layoutRequest)

        assertEquals(Rect(21f, 32f, 31f, 42f), layoutRequest.textBounds)
        assertEquals(Rect(20f, 31f, 32f, 43f), layoutRequest.controlBounds)
        assertEquals(Rect(21f, 32f, 31f, 42f), layoutRequest.visualPixelsTextBounds)
        assertEquals(Rect(20f, 31f, 32f, 43f), layoutRequest.visualPixelsControlBounds)
    }

    @Test
    fun coreTextLayoutRequestSkipsZeroSizedBounds() {
        val bridge = WinUIPlatformTextInputService.nativeBridge
        val editContext = FakeCoreTextEditContext()

        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue("hello"),
            imeOptions = ImeOptions.Default,
            onEditCommand = {},
            onImeActionPerformed = {},
        )
        assertTrue(bridge.attachCoreTextForCurrentInput(editContext))

        WinUIPlatformTextInputService.updateTextLayoutResult(
            textFieldValue = TextFieldValue("hello"),
            offsetMapping = OffsetMapping.Identity,
            textLayoutResult = testTextLayoutResult("hello"),
            textFieldToRootTransform = {},
            innerTextFieldBounds = Rect.Zero,
            decorationBoxBounds = Rect.Zero,
        )

        val layoutRequest = FakeCoreTextLayoutRequest()
        editContext.dispatchLayoutRequested(layoutRequest)

        assertEquals(null, layoutRequest.textBounds)
        assertEquals(null, layoutRequest.controlBounds)
        assertEquals(null, layoutRequest.visualPixelsTextBounds)
        assertEquals(null, layoutRequest.visualPixelsControlBounds)
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
    fun startInputMethodUsesPlatformTextInputMethodRequestAsPrimarySession() = runTest {
        val editCommandBatches = mutableListOf<List<EditCommand>>()
        val imeActions = mutableListOf<ImeAction>()
        val request = TestPlatformTextInputMethodRequest(
            textValue = TextFieldValue("request text"),
            imeOptions = ImeOptions.Default.copy(imeAction = ImeAction.Search),
            onEditCommand = { editCommandBatches += it },
            onImeAction = { imeActions += it },
            focusedRectInRoot = { Rect(1f, 2f, 3f, 4f) },
            textFieldRectInRoot = { Rect(0f, 1f, 4f, 5f) },
        )
        val session = WinUIPlatformTextInputSession(this)

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            session.startInputMethod(request)
        }

        assertEquals(TextFieldValue("request text"), WinUIPlatformTextInputService.currentValue)
        assertEquals(
            ImeOptions.Default.copy(imeAction = ImeAction.Search),
            WinUIPlatformTextInputService.currentImeOptions,
        )
        assertEquals(
            WinUITextLayoutBounds(
                innerTextFieldBounds = Rect(1f, 2f, 3f, 4f),
                decorationBoxBounds = Rect(0f, 1f, 4f, 5f),
            ),
            WinUIPlatformTextInputService.currentTextLayoutBoundsInRoot,
        )

        assertTrue(WinUIPlatformTextInputService.commitText("new"))
        assertTrue(WinUIPlatformTextInputService.performImeAction(ImeAction.Search))

        assertEquals(1, editCommandBatches.size)
        assertEquals(listOf(CommitTextCommand("new", 1)), editCommandBatches.single())
        assertEquals(listOf(ImeAction.Search), imeActions)

        job.cancelAndJoin()
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

@OptIn(ExperimentalComposeUiApi::class)
private class TestPlatformTextInputMethodRequest(
    private val textValue: TextFieldValue = TextFieldValue(""),
    override val imeOptions: ImeOptions = ImeOptions.Default,
    override val onEditCommand: (List<EditCommand>) -> Unit = {},
    override val onImeAction: ((ImeAction) -> Unit)? = null,
    override val focusedRectInRoot: () -> Rect? = { null },
    override val textFieldRectInRoot: () -> Rect? = { null },
    override val textClippingRectInRoot: () -> Rect? = { null },
) : PlatformTextInputMethodRequest {
    override val value: () -> TextFieldValue = { textValue }
    override val state: TextEditorState = object : TextEditorState {
        override val text: String = ""
        override val selection: TextRange = TextRange.Zero
        override val composition: TextRange? = null
        override val length: Int = 0
        override fun get(index: Int): Char = throw IndexOutOfBoundsException(index)
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = ""
        override fun toString(): String = ""
    }
    override val textLayoutResult: () -> TextLayoutResult? = { null }
    override val unclippedTextOffsetInRoot: () -> Offset? = { null }
    override val editText: (TextEditingScope.() -> Unit) -> Unit = {}
}

private fun testTextLayoutResult(text: String): TextLayoutResult {
    val annotatedString = AnnotatedString(text)
    val density = Density(1f)
    val constraints = Constraints(maxWidth = 100)
    val fontFamilyResolver = createFontFamilyResolver()
    val layoutInput = TextLayoutInput(
        text = annotatedString,
        style = TextStyle.Default,
        placeholders = emptyList(),
        maxLines = Int.MAX_VALUE,
        softWrap = true,
        overflow = TextOverflow.Clip,
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        fontFamilyResolver = fontFamilyResolver,
        constraints = constraints,
    )
    val multiParagraph = MultiParagraph(
        annotatedString = annotatedString,
        style = TextStyle.Default,
        constraints = constraints,
        density = density,
        fontFamilyResolver = fontFamilyResolver,
    )
    return TextLayoutResult(layoutInput, multiParagraph, IntSize(100, multiParagraph.height.toInt()))
}

private fun Matrix.setTranslate(offset: Offset) {
    reset()
    translate(offset.x, offset.y)
}

private fun assertCoreTextRangeEquals(expected: CoreTextRange, actual: CoreTextRange) {
    assertEquals(expected.startCaretPosition, actual.startCaretPosition)
    assertEquals(expected.endCaretPosition, actual.endCaretPosition)
}

private class FakeCoreTextEditContext : WinUICoreTextEditContext {
    override var name: String = ""
    override var inputScope: CoreTextInputScope = CoreTextInputScope.Default
    override var inputPaneDisplayPolicy: CoreTextInputPaneDisplayPolicy =
        CoreTextInputPaneDisplayPolicy.Manual

    var didNotifyFocusEnter = false
    var removedHandlerCount = 0
    var layoutChangedCount = 0
    val textChanges = mutableListOf<FakeTextChange>()
    val selectionChanges = mutableListOf<CoreTextRange>()

    private var textRequested: ((WinUICoreTextTextRequest) -> Unit)? = null
    private var selectionRequested: ((WinUICoreTextSelectionRequest) -> Unit)? = null
    private var layoutRequested: ((WinUICoreTextLayoutRequest) -> Unit)? = null
    private var textUpdating: ((WinUICoreTextTextUpdatingEvent) -> Unit)? = null
    private var selectionUpdating: ((WinUICoreTextSelectionUpdatingEvent) -> Unit)? = null
    private var formatUpdating: ((WinUICoreTextFormatUpdatingEvent) -> Unit)? = null
    private var compositionStarted: (() -> Unit)? = null
    private var compositionCompleted: (() -> Unit)? = null

    override fun addTextRequested(
        handler: (WinUICoreTextTextRequest) -> Unit,
    ): WinUICoreTextEventToken {
        textRequested = handler
        return token()
    }

    override fun addSelectionRequested(
        handler: (WinUICoreTextSelectionRequest) -> Unit,
    ): WinUICoreTextEventToken {
        selectionRequested = handler
        return token()
    }

    override fun addLayoutRequested(
        handler: (WinUICoreTextLayoutRequest) -> Unit,
    ): WinUICoreTextEventToken {
        layoutRequested = handler
        return token()
    }

    override fun addTextUpdating(
        handler: (WinUICoreTextTextUpdatingEvent) -> Unit,
    ): WinUICoreTextEventToken {
        textUpdating = handler
        return token()
    }

    override fun addSelectionUpdating(
        handler: (WinUICoreTextSelectionUpdatingEvent) -> Unit,
    ): WinUICoreTextEventToken {
        selectionUpdating = handler
        return token()
    }

    override fun addFormatUpdating(
        handler: (WinUICoreTextFormatUpdatingEvent) -> Unit,
    ): WinUICoreTextEventToken {
        formatUpdating = handler
        return token()
    }

    override fun addCompositionStarted(handler: () -> Unit): WinUICoreTextEventToken {
        compositionStarted = handler
        return token()
    }

    override fun addCompositionCompleted(handler: () -> Unit): WinUICoreTextEventToken {
        compositionCompleted = handler
        return token()
    }

    override fun removeEventHandler(token: WinUICoreTextEventToken) {
        token.remove()
    }

    override fun notifyFocusEnter() {
        didNotifyFocusEnter = true
    }

    override fun notifyFocusLeave() = Unit

    override fun notifyTextChanged(
        modifiedRange: CoreTextRange,
        newLength: Int,
        newSelection: CoreTextRange,
    ) {
        textChanges += FakeTextChange(modifiedRange, newLength, newSelection)
    }

    override fun notifySelectionChanged(selection: CoreTextRange) {
        selectionChanges += selection
    }

    override fun notifyLayoutChanged() {
        layoutChangedCount++
    }

    fun dispatchTextRequested(request: WinUICoreTextTextRequest) {
        textRequested?.invoke(request)
    }

    fun dispatchSelectionRequested(request: WinUICoreTextSelectionRequest) {
        selectionRequested?.invoke(request)
    }

    fun dispatchLayoutRequested(request: WinUICoreTextLayoutRequest) {
        layoutRequested?.invoke(request)
    }

    fun dispatchTextUpdating(event: WinUICoreTextTextUpdatingEvent) {
        textUpdating?.invoke(event)
    }

    fun dispatchSelectionUpdating(event: WinUICoreTextSelectionUpdatingEvent) {
        selectionUpdating?.invoke(event)
    }

    fun dispatchFormatUpdating(event: WinUICoreTextFormatUpdatingEvent) {
        formatUpdating?.invoke(event)
    }

    fun dispatchCompositionStarted() {
        compositionStarted?.invoke()
    }

    fun dispatchCompositionCompleted() {
        compositionCompleted?.invoke()
    }

    private fun token(): WinUICoreTextEventToken =
        WinUICoreTextEventToken { removedHandlerCount++ }
}

private data class FakeTextChange(
    val modifiedRange: CoreTextRange,
    val newLength: Int,
    val newSelection: CoreTextRange,
)

private class FakeCoreTextTextRequest(
    override val range: CoreTextRange,
) : WinUICoreTextTextRequest {
    override var text: String = ""
}

private class FakeCoreTextSelectionRequest : WinUICoreTextSelectionRequest {
    override var selection: CoreTextRange = CoreTextRange(0, 0)
}

private class FakeCoreTextLayoutRequest(
    override val isCanceled: Boolean = false,
) : WinUICoreTextLayoutRequest {
    var textBounds: Rect? = null
        private set
    var controlBounds: Rect? = null
        private set
    var visualPixelsTextBounds: Rect? = null
        private set
    var visualPixelsControlBounds: Rect? = null
        private set

    override fun setLayoutBounds(bounds: WinUITextLayoutBounds) {
        textBounds = bounds.innerTextFieldBounds
        controlBounds = bounds.decorationBoxBounds
        visualPixelsTextBounds = bounds.innerTextFieldBounds
        visualPixelsControlBounds = bounds.decorationBoxBounds
    }
}

private class FakeCoreTextTextUpdatingEvent(
    override val range: CoreTextRange,
    override val text: String,
    override val newSelection: CoreTextRange,
    override val isCanceled: Boolean = false,
) : WinUICoreTextTextUpdatingEvent {
    override var result: CoreTextTextUpdatingResult = CoreTextTextUpdatingResult.Failed
}

private class FakeCoreTextSelectionUpdatingEvent(
    override val selection: CoreTextRange,
    override val isCanceled: Boolean = false,
) : WinUICoreTextSelectionUpdatingEvent {
    override var result: CoreTextSelectionUpdatingResult = CoreTextSelectionUpdatingResult.Failed
}

private class FakeCoreTextFormatUpdatingEvent(
    override val isCanceled: Boolean = false,
) : WinUICoreTextFormatUpdatingEvent {
    override var result: CoreTextFormatUpdatingResult = CoreTextFormatUpdatingResult.Succeeded
}
