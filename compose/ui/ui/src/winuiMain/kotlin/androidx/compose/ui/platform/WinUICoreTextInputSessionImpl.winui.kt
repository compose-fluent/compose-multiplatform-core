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
import windows.ui.text.core.CoreTextFormatUpdatingResult
import windows.ui.text.core.CoreTextInputPaneDisplayPolicy
import windows.ui.text.core.CoreTextInputScope
import windows.ui.text.core.CoreTextRange
import windows.ui.text.core.CoreTextSelectionUpdatingResult
import windows.ui.text.core.CoreTextTextUpdatingResult

internal class WinUICoreTextInputSession private constructor(
    initialValue: TextFieldValue,
    imeOptions: ImeOptions,
    private val editContext: WinUICoreTextEditContext,
    private val currentValue: () -> TextFieldValue?,
    private val currentLayoutBounds: () -> WinUICoreTextLayoutSnapshot?,
    private val dispatchEditCommands: (List<EditCommand>) -> Boolean,
) : WinUICoreTextInputSessionHandle {
    private val eventTokens = mutableListOf<WinUICoreTextEventToken>()
    private var value: TextFieldValue = initialValue
    private var compositionActive = false
    private var coreTextUpdateDepth = 0
    private var isDisposed = false
    private var isFocused = false

    override val isCompositionActive: Boolean
        get() = compositionActive

    init {
        editContext.name = "Compose WinUI text input"
        editContext.inputScope = imeOptions.toCoreTextInputScope()
        editContext.inputPaneDisplayPolicy = CoreTextInputPaneDisplayPolicy.Automatic
        debugCoreTextInput {
            "session init value=${initialValue.debugString()} " +
                "imeOptions=${imeOptions.debugString()} inputScope=${editContext.inputScope}"
        }
        registerEventHandlers()
    }

    override fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) {
        if (isDisposed) return
        val previousValue = value
        value = newValue
        debugCoreTextInput {
            "updateState observeOnly coreTextUpdateDepth=$coreTextUpdateDepth " +
                "old=${oldValue?.debugString()} new=${newValue.debugString()}"
        }
        if (coreTextUpdateDepth == 0 &&
            previousValue.text == newValue.text &&
            previousValue.selection != newValue.selection
        ) {
            debugCoreTextInput {
                "updateState notifySelectionChanged previous=${previousValue.selection} " +
                    "new=${newValue.selection}"
            }
            runCoreTextCallback("NotifySelectionChanged") {
                editContext.notifySelectionChangedByValueForWinUI(newValue.selection.toCoreTextRange())
            }
        }
    }

    override fun notifyLayoutChanged() {
        if (isDisposed) return
        debugCoreTextInput { "notifyLayoutChanged bounds=${currentLayoutBounds()?.debugString()}" }
        runCoreTextCallback("NotifyLayoutChanged") {
            editContext.notifyLayoutChanged()
        }
    }

    override fun notifyFocusEnter() {
        if (!isDisposed && !isFocused) {
            debugCoreTextInput { "notifyFocusEnter" }
            runCoreTextCallback("NotifyFocusEnter") {
                editContext.notifyFocusEnter()
                isFocused = true
            }
        }
    }

    override fun notifyFocusLeave() {
        if (!isDisposed && isFocused) {
            debugCoreTextInput { "notifyFocusLeave" }
            runCoreTextCallback("NotifyFocusLeave") {
                editContext.notifyFocusLeave()
                isFocused = false
            }
        }
    }

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        eventTokens.asReversed().forEach { token ->
            runCatching { editContext.removeEventHandler(token) }
        }
        eventTokens.clear()
    }

    private fun registerEventHandlers() {
        eventTokens += editContext.addTextRequested { request ->
            runCoreTextCallback("TextRequested") {
                val range = request.range
                request.text = latestValue().text.sliceCoreTextRange(
                    range.startCaretPosition,
                    range.endCaretPosition,
                )
                debugCoreTextInput {
                    "TextRequested range=${range.debugString()} text=${request.text.debugForLog()}"
                }
            }
        }
        eventTokens += editContext.addSelectionRequested { request ->
            runCoreTextCallback("SelectionRequested") {
                val selection = latestValue().selection.toCoreTextRange()
                request.setSelectionByValueForWinUI(selection)
                debugCoreTextInput {
                    "SelectionRequested set=${selection.debugString()} " +
                        "readBack=${request.selection.debugString()}"
                }
            }
        }
        eventTokens += editContext.addLayoutRequested { request ->
            runCoreTextCallback("LayoutRequested") {
                if (!request.isCanceled) {
                    val bounds = currentLayoutBounds()
                    debugCoreTextInput {
                        "LayoutRequested canceled=false bounds=${bounds?.debugString()} " +
                            "usable=${bounds?.hasUsableBounds}"
                    }
                    val normalizedBounds = bounds?.normalizedForCoreText()
                    if (normalizedBounds != null) {
                        debugCoreTextInput {
                            "LayoutRequested write normalizedBounds=${normalizedBounds.debugString()}"
                        }
                        request.setLayoutBounds(normalizedBounds)
                    } else {
                        debugCoreTextInput {
                            "LayoutRequested skipped: no usable screen layout bounds"
                        }
                    }
                } else {
                    debugCoreTextInput { "LayoutRequested canceled=true" }
                }
            }
        }
        eventTokens += editContext.addTextUpdating { event ->
            runCoreTextUpdateCallback("TextUpdating", onFailure = {
                event.result = CoreTextTextUpdatingResult.Failed
            }) {
                if (!event.isCanceled) {
                    debugCoreTextInput {
                        "TextUpdating range=${event.range.debugString()} " +
                            "text=${event.text.debugForLog()} " +
                            "newSelection=${event.newSelection.debugString()} " +
                            "compositionActive=$compositionActive"
                    }
                    val commands = buildTextUpdatingCommands(event)
                    event.result = if (runAsCoreTextUpdate { dispatchEditCommands(commands) }) {
                        currentValue()?.let { value = it }
                        CoreTextTextUpdatingResult.Succeeded
                    } else {
                        CoreTextTextUpdatingResult.Failed
                    }
                    debugCoreTextInput { "TextUpdating result=${event.result}" }
                } else {
                    debugCoreTextInput { "TextUpdating canceled=true" }
                }
            }
        }
        eventTokens += editContext.addSelectionUpdating { event ->
            runCoreTextUpdateCallback("SelectionUpdating", onFailure = {
                event.result = CoreTextSelectionUpdatingResult.Failed
            }) {
                if (!event.isCanceled) {
                    val selection = event.selection
                    debugCoreTextInput {
                        "SelectionUpdating selection=${selection.debugString()}"
                    }
                    val commands = listOf(
                        SetSelectionCommand(
                            selection.startCaretPosition,
                            selection.endCaretPosition,
                        )
                    )
                    event.result = if (runAsCoreTextUpdate { dispatchEditCommands(commands) }) {
                        currentValue()?.let { value = it }
                        CoreTextSelectionUpdatingResult.Succeeded
                    } else {
                        CoreTextSelectionUpdatingResult.Failed
                    }
                    debugCoreTextInput { "SelectionUpdating result=${event.result}" }
                } else {
                    debugCoreTextInput { "SelectionUpdating canceled=true" }
                }
            }
        }
        eventTokens += editContext.addFormatUpdating { event ->
            runCoreTextCallback("FormatUpdating") {
                if (!event.isCanceled) {
                    debugCoreTextInput { "FormatUpdating result=Failed" }
                    event.result = CoreTextFormatUpdatingResult.Failed
                } else {
                    debugCoreTextInput { "FormatUpdating canceled=true" }
                }
            }
        }
        eventTokens += editContext.addCompositionStarted {
            runCoreTextCallback("CompositionStarted") {
                debugCoreTextInput { "CompositionStarted" }
                compositionActive = true
            }
        }
        eventTokens += editContext.addCompositionCompleted {
            runCoreTextCallback("CompositionCompleted") {
                debugCoreTextInput { "CompositionCompleted" }
                compositionActive = false
                runAsCoreTextUpdate {
                    dispatchEditCommands(listOf(FinishComposingTextCommand()))
                }
                currentValue()?.let { value = it }
            }
        }
    }

    private inline fun <T> runAsCoreTextUpdate(block: () -> T): T {
        coreTextUpdateDepth++
        return try {
            block()
        } finally {
            coreTextUpdateDepth--
        }
    }

    private fun buildTextUpdatingCommands(
        event: WinUICoreTextTextUpdatingEvent,
    ): List<EditCommand> =
        buildList {
            val range = event.range
            add(SetSelectionCommand(range.startCaretPosition, range.endCaretPosition))
            if (compositionActive) {
                add(SetComposingTextCommand(event.text, 1))
            } else if (event.text.isNotEmpty() || !range.isCollapsed) {
                add(CommitTextCommand(event.text, 1))
            }
            add(
                SetSelectionCommand(
                    event.newSelection.startCaretPosition,
                    event.newSelection.endCaretPosition,
                )
            )
        }

    private fun latestValue(): TextFieldValue {
        currentValue()?.let { latest ->
            value = latest
            return latest
        }
        return value
    }

    private inline fun runCoreTextCallback(
        name: String,
        block: () -> Unit,
    ) {
        if (isDisposed) return
        try {
            block()
        } catch (throwable: Throwable) {
            debugCoreTextInput {
                "CoreText $name callback failed: ${throwable.stackTraceToString()}"
            }
        }
    }

    private inline fun runCoreTextUpdateCallback(
        name: String,
        onFailure: () -> Unit,
        block: () -> Unit,
    ) {
        if (isDisposed) return
        try {
            block()
        } catch (throwable: Throwable) {
            runCatching { onFailure() }
            debugCoreTextInput {
                "CoreText $name callback failed: ${throwable.stackTraceToString()}"
            }
        }
    }

    internal companion object {
        fun create(
            initialValue: TextFieldValue,
            imeOptions: ImeOptions,
            currentValue: () -> TextFieldValue? = { null },
            currentLayoutBounds: () -> WinUICoreTextLayoutSnapshot?,
            dispatchEditCommands: (List<EditCommand>) -> Boolean,
        ): WinUICoreTextInputSession =
            create(
                initialValue = initialValue,
                imeOptions = imeOptions,
                editContext = createWinUICoreTextEditContextForWinUI(),
                currentValue = currentValue,
                currentLayoutBounds = currentLayoutBounds,
                dispatchEditCommands = dispatchEditCommands,
            )

        fun create(
            initialValue: TextFieldValue,
            imeOptions: ImeOptions,
            editContext: WinUICoreTextEditContext,
            currentValue: () -> TextFieldValue? = { null },
            currentLayoutBounds: () -> WinUICoreTextLayoutSnapshot?,
            dispatchEditCommands: (List<EditCommand>) -> Boolean,
        ): WinUICoreTextInputSession =
            WinUICoreTextInputSession(
                initialValue = initialValue,
                imeOptions = imeOptions,
                editContext = editContext,
                currentValue = currentValue,
                currentLayoutBounds = currentLayoutBounds,
                dispatchEditCommands = dispatchEditCommands,
            )
    }
}

private fun TextRange.toCoreTextRange(): CoreTextRange =
    CoreTextRange(start, end)

private val CoreTextRange.isCollapsed: Boolean
    get() = startCaretPosition == endCaretPosition

private fun CoreTextRange.debugString(): String =
    "CoreTextRange(start=$startCaretPosition, end=$endCaretPosition)"

private fun WinUICoreTextLayoutSnapshot.debugString(): String =
    "WinUICoreTextLayoutSnapshot(layout=${layoutBounds?.debugString()}, " +
        "visual=${visualBounds?.debugString()})"

private fun WinUICoreTextLayoutSnapshot.normalizedForCoreText(): WinUICoreTextLayoutSnapshot? =
    layoutBounds?.takeIf { it.hasUsableBounds }?.let { bounds ->
        WinUICoreTextLayoutSnapshot(
            layoutBounds = bounds.withNonEmptyTextBounds(),
            visualBounds = null,
        )
    }

private fun WinUITextLayoutBounds.withNonEmptyTextBounds(): WinUITextLayoutBounds =
    copy(innerTextFieldBounds = innerTextFieldBounds.withCoreTextMinimumWidth())

private fun Rect.withCoreTextMinimumWidth(): Rect =
    if (width > 0f) {
        this
    } else {
        Rect(left, top, left + MinimumCoreTextTextBoundsWidth, bottom)
    }

private fun String.sliceCoreTextRange(startCaretPosition: Int, endCaretPosition: Int): String {
    val start = startCaretPosition.coerceIn(0, length)
    val end = endCaretPosition.coerceIn(start, length)
    return substring(start, end)
}

private fun ImeOptions.toCoreTextInputScope(): CoreTextInputScope =
    when (keyboardType) {
        androidx.compose.ui.text.input.KeyboardType.Number,
        androidx.compose.ui.text.input.KeyboardType.Decimal -> CoreTextInputScope.Number
        androidx.compose.ui.text.input.KeyboardType.Phone -> CoreTextInputScope.TelephoneNumber
        androidx.compose.ui.text.input.KeyboardType.Uri -> CoreTextInputScope.Url
        androidx.compose.ui.text.input.KeyboardType.Email -> CoreTextInputScope.EmailAddress
        androidx.compose.ui.text.input.KeyboardType.Password,
        androidx.compose.ui.text.input.KeyboardType.NumberPassword -> CoreTextInputScope.Password
        else -> CoreTextInputScope.Text
    }

private inline fun debugCoreTextInput(message: () -> String) {
    if (winUISystemBooleanProperty("compose.winui.textInput.debug")) {
        winUIDebugLog("core-text", message())
    }
}

private const val MinimumCoreTextTextBoundsWidth = 1f
