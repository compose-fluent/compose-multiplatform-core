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

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.FinishComposingTextCommand
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.SetComposingTextCommand
import androidx.compose.ui.text.input.SetSelectionCommand
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import windows.ui.text.core.CoreTextFormatUpdatingResult
import windows.ui.text.core.CoreTextInputPaneDisplayPolicy
import windows.ui.text.core.CoreTextInputScope
import windows.ui.text.core.CoreTextRange
import windows.ui.text.core.CoreTextSelectionUpdatingResult
import windows.ui.text.core.CoreTextTextUpdatingResult

class WinUICoreTextInputSessionTest {
    @Test
    fun initializesEditContextWithoutNotifyingInitialText() {
        val editContext = FakeCoreTextEditContext()

        WinUICoreTextInputSession.create(
            initialValue = TextFieldValue("hello", selection = TextRange(1, 4)),
            imeOptions = ImeOptions.Default,
            editContext = editContext,
            currentLayoutBounds = { null },
            dispatchEditCommands = { true },
        )

        assertEquals("Compose WinUI text input", editContext.name)
        assertEquals(CoreTextInputScope.Text, editContext.inputScope)
        assertEquals(CoreTextInputPaneDisplayPolicy.Automatic, editContext.inputPaneDisplayPolicy)
    }

    @Test
    fun coreTextObservesTextAndNotifiesExternalSelectionChanges() {
        val editContext = FakeCoreTextEditContext()
        val session = WinUICoreTextInputSession.create(
            initialValue = TextFieldValue("hello", selection = TextRange(1)),
            imeOptions = ImeOptions.Default,
            editContext = editContext,
            currentLayoutBounds = { null },
            dispatchEditCommands = { true },
        )

        session.updateState(
            oldValue = TextFieldValue("hello", selection = TextRange(1)),
            newValue = TextFieldValue("heLlo", selection = TextRange(3)),
        )
        session.updateState(
            oldValue = TextFieldValue("heLlo", selection = TextRange(3)),
            newValue = TextFieldValue("heLlo", selection = TextRange(0, 2)),
        )

        assertCoreTextRangeEquals(
            CoreTextRange(0, 2),
            editContext.selectionChanges.single(),
        )
    }

    @Test
    fun coreTextStillHandlesCompositionUpdatingEvents() {
        val editContext = FakeCoreTextEditContext()
        val editCommandBatches = mutableListOf<List<EditCommand>>()

        WinUICoreTextInputSession.create(
            initialValue = TextFieldValue("input", selection = TextRange(2)),
            imeOptions = ImeOptions.Default,
            editContext = editContext,
            currentLayoutBounds = { null },
            dispatchEditCommands = { commands ->
                editCommandBatches += commands
                true
            },
        )

        editContext.dispatchCompositionStarted()
        editContext.dispatchTextUpdating(
            FakeCoreTextTextUpdatingEvent(
                range = CoreTextRange(2, 2),
                text = "draft",
                newSelection = CoreTextRange(7, 7),
            )
        )
        editContext.dispatchCompositionCompleted()

        assertEquals(
            listOf(
                SetSelectionCommand(2, 2),
                SetComposingTextCommand("draft", 1),
                SetSelectionCommand(7, 7),
            ),
            editCommandBatches.first(),
        )
        assertEquals(listOf(FinishComposingTextCommand()), editCommandBatches.last())
    }

    @Test
    fun coreTextLayoutRequestUsesScreenLayoutBoundsOnly() {
        val editContext = FakeCoreTextEditContext()
        WinUICoreTextInputSession.create(
            initialValue = TextFieldValue("hello", selection = TextRange(1)),
            imeOptions = ImeOptions.Default,
            editContext = editContext,
            currentLayoutBounds = {
                WinUICoreTextLayoutSnapshot(
                    layoutBounds = WinUITextLayoutBounds(
                        innerTextFieldBounds = Rect(110f, 120f, 110f, 140f),
                        decorationBoxBounds = Rect(100f, 100f, 200f, 200f),
                    ),
                    visualBounds = WinUITextLayoutBounds(
                        innerTextFieldBounds = Rect(10f, 20f, 10f, 40f),
                        decorationBoxBounds = Rect(0f, 0f, 100f, 100f),
                    ),
                )
            },
            dispatchEditCommands = { true },
        )

        val layoutRequest = FakeCoreTextLayoutRequest()
        editContext.dispatchLayoutRequested(layoutRequest)

        assertEquals(Rect(110f, 120f, 111f, 140f), layoutRequest.layoutTextBounds)
        assertEquals(Rect(100f, 100f, 200f, 200f), layoutRequest.layoutControlBounds)
        assertEquals(null, layoutRequest.visualTextBounds)
        assertEquals(null, layoutRequest.visualControlBounds)
    }

    @Test
    fun coreTextLayoutRequestSkipsVisualOnlyBounds() {
        val editContext = FakeCoreTextEditContext()
        WinUICoreTextInputSession.create(
            initialValue = TextFieldValue("hello", selection = TextRange(1)),
            imeOptions = ImeOptions.Default,
            editContext = editContext,
            currentLayoutBounds = {
                WinUICoreTextLayoutSnapshot(
                    layoutBounds = null,
                    visualBounds = WinUITextLayoutBounds(
                        innerTextFieldBounds = Rect(10f, 20f, 10f, 40f),
                        decorationBoxBounds = Rect(0f, 0f, 100f, 100f),
                    ),
                )
            },
            dispatchEditCommands = { true },
        )

        val layoutRequest = FakeCoreTextLayoutRequest()
        editContext.dispatchLayoutRequested(layoutRequest)

        assertEquals(null, layoutRequest.layoutTextBounds)
        assertEquals(null, layoutRequest.layoutControlBounds)
        assertEquals(null, layoutRequest.visualTextBounds)
        assertEquals(null, layoutRequest.visualControlBounds)
    }

    @Test
    fun textAndSelectionRequestsUseLatestCurrentValue() {
        val editContext = FakeCoreTextEditContext()
        var currentValue = TextFieldValue("latest", selection = TextRange(2, 5))

        WinUICoreTextInputSession.create(
            initialValue = TextFieldValue("stale", selection = TextRange.Zero),
            imeOptions = ImeOptions.Default,
            editContext = editContext,
            currentValue = { currentValue },
            currentLayoutBounds = { null },
            dispatchEditCommands = { true },
        )

        val textRequest = FakeCoreTextTextRequest(CoreTextRange(1, 4))
        editContext.dispatchTextRequested(textRequest)
        assertEquals("ate", textRequest.text)

        currentValue = TextFieldValue("updated", selection = TextRange(1, 3))
        val selectionRequest = FakeCoreTextSelectionRequest()
        editContext.dispatchSelectionRequested(selectionRequest)
        assertCoreTextRangeEquals(CoreTextRange(1, 3), selectionRequest.selection)
    }

    @Test
    fun textUpdatingCommitsReplacementRangeAndSelection() {
        val editContext = FakeCoreTextEditContext()
        val editCommandBatches = mutableListOf<List<EditCommand>>()

        WinUICoreTextInputSession.create(
            initialValue = TextFieldValue("hello", selection = TextRange(1, 4)),
            imeOptions = ImeOptions.Default,
            editContext = editContext,
            currentLayoutBounds = { null },
            dispatchEditCommands = { commands ->
                editCommandBatches += commands
                true
            },
        )

        val update = FakeCoreTextTextUpdatingEvent(
            range = CoreTextRange(1, 4),
            text = "abc",
            newSelection = CoreTextRange(4, 4),
        )
        editContext.dispatchTextUpdating(update)

        assertEquals(CoreTextTextUpdatingResult.Succeeded, update.result)
        assertEquals(
            listOf(
                SetSelectionCommand(1, 4),
                CommitTextCommand("abc", 1),
                SetSelectionCommand(4, 4),
            ),
            editCommandBatches.single(),
        )
    }

    @Test
    fun compositionUpdatingUsesComposingTextUntilCompositionCompletes() {
        val editContext = FakeCoreTextEditContext()
        val editCommandBatches = mutableListOf<List<EditCommand>>()

        WinUICoreTextInputSession.create(
            initialValue = TextFieldValue("input", selection = TextRange(2)),
            imeOptions = ImeOptions.Default,
            editContext = editContext,
            currentLayoutBounds = { null },
            dispatchEditCommands = { commands ->
                editCommandBatches += commands
                true
            },
        )

        editContext.dispatchCompositionStarted()
        val update = FakeCoreTextTextUpdatingEvent(
            range = CoreTextRange(2, 2),
            text = "draft",
            newSelection = CoreTextRange(7, 7),
        )
        editContext.dispatchTextUpdating(update)

        assertEquals(CoreTextTextUpdatingResult.Succeeded, update.result)
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
    }

    @Test
    fun textUpdatingStateEchoDoesNotNotifyCoreText() {
        val editContext = FakeCoreTextEditContext()
        lateinit var session: WinUICoreTextInputSession
        var currentValue = TextFieldValue("hello", selection = TextRange(1))

        session = WinUICoreTextInputSession.create(
            initialValue = currentValue,
            imeOptions = ImeOptions.Default,
            editContext = editContext,
            currentValue = { currentValue },
            currentLayoutBounds = { null },
            dispatchEditCommands = {
                val newValue = TextFieldValue("hXo", selection = TextRange(2))
                session.updateState(currentValue, newValue)
                currentValue = newValue
                true
            },
        )

        val update = FakeCoreTextTextUpdatingEvent(
            range = CoreTextRange(1, 4),
            text = "X",
            newSelection = CoreTextRange(2, 2),
        )
        editContext.dispatchTextUpdating(update)

        assertEquals(CoreTextTextUpdatingResult.Succeeded, update.result)
        assertEquals(emptyList(), editContext.selectionChanges)
    }

    @Test
    fun selectionUpdatingStateEchoDoesNotNotifyCoreText() {
        val editContext = FakeCoreTextEditContext()
        lateinit var session: WinUICoreTextInputSession
        var currentValue = TextFieldValue("hello", selection = TextRange(1))

        session = WinUICoreTextInputSession.create(
            initialValue = currentValue,
            imeOptions = ImeOptions.Default,
            editContext = editContext,
            currentValue = { currentValue },
            currentLayoutBounds = { null },
            dispatchEditCommands = {
                val newValue = TextFieldValue("hello", selection = TextRange(3))
                session.updateState(currentValue, newValue)
                currentValue = newValue
                true
            },
        )

        val update = FakeCoreTextSelectionUpdatingEvent(CoreTextRange(3, 3))
        editContext.dispatchSelectionUpdating(update)

        assertEquals(CoreTextSelectionUpdatingResult.Succeeded, update.result)
        assertEquals(emptyList(), editContext.selectionChanges)
    }

    @Test
    fun selectionAndFormatUpdatingReturnCoreTextResults() {
        val editContext = FakeCoreTextEditContext()
        val editCommandBatches = mutableListOf<List<EditCommand>>()

        WinUICoreTextInputSession.create(
            initialValue = TextFieldValue("hello"),
            imeOptions = ImeOptions.Default,
            editContext = editContext,
            currentLayoutBounds = { null },
            dispatchEditCommands = { commands ->
                editCommandBatches += commands
                true
            },
        )

        val selectionUpdate = FakeCoreTextSelectionUpdatingEvent(CoreTextRange(0, 2))
        editContext.dispatchSelectionUpdating(selectionUpdate)
        assertEquals(CoreTextSelectionUpdatingResult.Succeeded, selectionUpdate.result)
        assertEquals(listOf(SetSelectionCommand(0, 2)), editCommandBatches.single())

        val formatUpdate = FakeCoreTextFormatUpdatingEvent()
        editContext.dispatchFormatUpdating(formatUpdate)
        assertEquals(CoreTextFormatUpdatingResult.Failed, formatUpdate.result)
    }

    @Test
    fun appStateUpdatesNotifyCoreTextTextSelectionAndLayoutChanges() {
        val editContext = FakeCoreTextEditContext()
        val session = WinUICoreTextInputSession.create(
            initialValue = TextFieldValue("hello", selection = TextRange(1)),
            imeOptions = ImeOptions.Default,
            editContext = editContext,
            currentLayoutBounds = {
                WinUICoreTextLayoutSnapshot(
                    layoutBounds = WinUITextLayoutBounds(
                        innerTextFieldBounds = Rect(101f, 202f, 111f, 212f),
                        decorationBoxBounds = Rect(100f, 201f, 112f, 213f),
                    ),
                    visualBounds = WinUITextLayoutBounds(
                        innerTextFieldBounds = Rect(1f, 2f, 11f, 12f),
                        decorationBoxBounds = Rect(0f, 1f, 12f, 13f),
                    ),
                )
            },
            dispatchEditCommands = { true },
        )
        session.updateState(
            oldValue = TextFieldValue("hello", selection = TextRange(1)),
            newValue = TextFieldValue("heLlo", selection = TextRange(3)),
        )

        session.updateState(
            oldValue = TextFieldValue("heLlo", selection = TextRange(5)),
            newValue = TextFieldValue("heLlo!", selection = TextRange(6)),
        )

        session.updateState(
            oldValue = TextFieldValue("heLlo!", selection = TextRange(6)),
            newValue = TextFieldValue("heLlo!", selection = TextRange(0, 2)),
        )
        assertCoreTextRangeEquals(
            CoreTextRange(0, 2),
            editContext.selectionChanges.single(),
        )

        session.notifyLayoutChanged()
        assertEquals(1, editContext.layoutChangedCount)

        val layoutRequest = FakeCoreTextLayoutRequest()
        editContext.dispatchLayoutRequested(layoutRequest)
        assertEquals(Rect(101f, 202f, 111f, 212f), layoutRequest.layoutTextBounds)
        assertEquals(Rect(100f, 201f, 112f, 213f), layoutRequest.layoutControlBounds)
        assertEquals(null, layoutRequest.visualTextBounds)
        assertEquals(null, layoutRequest.visualControlBounds)
    }

    @Test
    fun layoutRequestExpandsCollapsedCaretBounds() {
        val editContext = FakeCoreTextEditContext()
        WinUICoreTextInputSession.create(
            initialValue = TextFieldValue("hello", selection = TextRange(1)),
            imeOptions = ImeOptions.Default,
            editContext = editContext,
            currentLayoutBounds = {
                WinUICoreTextLayoutSnapshot(
                    layoutBounds = WinUITextLayoutBounds(
                        innerTextFieldBounds = Rect(110f, 120f, 110f, 140f),
                        decorationBoxBounds = Rect(100f, 100f, 200f, 200f),
                    ),
                    visualBounds = WinUITextLayoutBounds(
                        innerTextFieldBounds = Rect(10f, 20f, 10f, 40f),
                        decorationBoxBounds = Rect(0f, 0f, 100f, 100f),
                    ),
                )
            },
            dispatchEditCommands = { true },
        )

        val layoutRequest = FakeCoreTextLayoutRequest()
        editContext.dispatchLayoutRequested(layoutRequest)

        assertEquals(Rect(110f, 120f, 111f, 140f), layoutRequest.layoutTextBounds)
        assertEquals(Rect(100f, 100f, 200f, 200f), layoutRequest.layoutControlBounds)
        assertEquals(null, layoutRequest.visualTextBounds)
        assertEquals(null, layoutRequest.visualControlBounds)
    }

    @Test
    fun layoutRequestUsesScreenBoundsWhenVisualBoundsAreAlsoAvailable() {
        val editContext = FakeCoreTextEditContext()
        WinUICoreTextInputSession.create(
            initialValue = TextFieldValue("hello", selection = TextRange(1)),
            imeOptions = ImeOptions.Default,
            editContext = editContext,
            currentLayoutBounds = {
                WinUICoreTextLayoutSnapshot(
                    layoutBounds = WinUITextLayoutBounds(
                        innerTextFieldBounds = Rect(101f, 202f, 101f, 222f),
                        decorationBoxBounds = Rect(100f, 200f, 300f, 260f),
                    ),
                    visualBounds = WinUITextLayoutBounds(
                        innerTextFieldBounds = Rect(1f, 2f, 1f, 22f),
                        decorationBoxBounds = Rect(0f, 0f, 200f, 60f),
                    ),
                )
            },
            dispatchEditCommands = { true },
        )

        val layoutRequest = FakeCoreTextLayoutRequest()
        editContext.dispatchLayoutRequested(layoutRequest)

        assertEquals(Rect(101f, 202f, 102f, 222f), layoutRequest.layoutTextBounds)
        assertEquals(Rect(100f, 200f, 300f, 260f), layoutRequest.layoutControlBounds)
        assertEquals(null, layoutRequest.visualTextBounds)
        assertEquals(null, layoutRequest.visualControlBounds)
    }

    @Test
    fun layoutRequestFallsBackToScreenBoundsWhenVisualBoundsAreUnavailable() {
        val editContext = FakeCoreTextEditContext()
        WinUICoreTextInputSession.create(
            initialValue = TextFieldValue("hello", selection = TextRange(1)),
            imeOptions = ImeOptions.Default,
            editContext = editContext,
            currentLayoutBounds = {
                WinUICoreTextLayoutSnapshot(
                    layoutBounds = WinUITextLayoutBounds(
                        innerTextFieldBounds = Rect(110f, 120f, 110f, 140f),
                        decorationBoxBounds = Rect(100f, 100f, 200f, 200f),
                    ),
                    visualBounds = null,
                )
            },
            dispatchEditCommands = { true },
        )

        val layoutRequest = FakeCoreTextLayoutRequest()
        editContext.dispatchLayoutRequested(layoutRequest)

        assertEquals(Rect(110f, 120f, 111f, 140f), layoutRequest.layoutTextBounds)
        assertEquals(Rect(100f, 100f, 200f, 200f), layoutRequest.layoutControlBounds)
        assertEquals(null, layoutRequest.visualTextBounds)
        assertEquals(null, layoutRequest.visualControlBounds)
    }

    @Test
    fun layoutRequestSkipsSnapshotWithoutUsableBounds() {
        val editContext = FakeCoreTextEditContext()
        WinUICoreTextInputSession.create(
            initialValue = TextFieldValue("hello", selection = TextRange(1)),
            imeOptions = ImeOptions.Default,
            editContext = editContext,
            currentLayoutBounds = {
                WinUICoreTextLayoutSnapshot(
                    layoutBounds = null,
                    visualBounds = null,
                )
            },
            dispatchEditCommands = { true },
        )

        val layoutRequest = FakeCoreTextLayoutRequest()
        editContext.dispatchLayoutRequested(layoutRequest)

        assertEquals(null, layoutRequest.layoutTextBounds)
        assertEquals(null, layoutRequest.layoutControlBounds)
        assertEquals(null, layoutRequest.visualTextBounds)
        assertEquals(null, layoutRequest.visualControlBounds)
    }

    @Test
    fun failedEditDispatchMarksUpdatingFailedWithoutThrowing() {
        val editContext = FakeCoreTextEditContext()

        WinUICoreTextInputSession.create(
            initialValue = TextFieldValue("hello"),
            imeOptions = ImeOptions.Default,
            editContext = editContext,
            currentLayoutBounds = { null },
            dispatchEditCommands = { error("dispatch failed") },
        )

        val update = FakeCoreTextTextUpdatingEvent(
            range = CoreTextRange(0, 5),
            text = "boom",
            newSelection = CoreTextRange(4, 4),
        )
        editContext.dispatchTextUpdating(update)

        assertEquals(CoreTextTextUpdatingResult.Failed, update.result)

        val selectionUpdate = FakeCoreTextSelectionUpdatingEvent(CoreTextRange(0, 1))
        editContext.dispatchSelectionUpdating(selectionUpdate)

        assertEquals(CoreTextSelectionUpdatingResult.Failed, selectionUpdate.result)
    }

    @Test
    fun focusNotificationsAreIdempotentAndDisposeRemovesHandlers() {
        val editContext = FakeCoreTextEditContext()
        val session = WinUICoreTextInputSession.create(
            initialValue = TextFieldValue(""),
            imeOptions = ImeOptions.Default,
            editContext = editContext,
            currentLayoutBounds = { null },
            dispatchEditCommands = { true },
        )

        session.notifyFocusEnter()
        session.notifyFocusEnter()
        assertEquals(1, editContext.focusEnterCount)

        session.notifyFocusLeave()
        session.notifyFocusLeave()
        assertEquals(1, editContext.focusLeaveCount)

        session.dispose()

        assertEquals(8, editContext.removedHandlerCount)
    }
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

    var focusEnterCount = 0
    var focusLeaveCount = 0
    var removedHandlerCount = 0
    var layoutChangedCount = 0
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
        focusEnterCount++
    }

    override fun notifyFocusLeave() {
        focusLeaveCount++
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
    var layoutTextBounds: Rect? = null
        private set
    var layoutControlBounds: Rect? = null
        private set
    var visualTextBounds: Rect? = null
        private set
    var visualControlBounds: Rect? = null
        private set

    override fun setLayoutBounds(bounds: WinUICoreTextLayoutSnapshot) {
        layoutTextBounds = bounds.layoutBounds?.innerTextFieldBounds
        layoutControlBounds = bounds.layoutBounds?.decorationBoxBounds
        visualTextBounds = bounds.visualBounds?.innerTextFieldBounds
        visualControlBounds = bounds.visualBounds?.decorationBoxBounds
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
