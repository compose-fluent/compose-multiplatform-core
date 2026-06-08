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

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.FinishComposingTextCommand
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.SetComposingTextCommand
import androidx.compose.ui.text.input.SetSelectionCommand
import androidx.compose.ui.text.input.TextFieldValue
import windows.foundation.TypedEventHandler
import windows.foundation.Rect as WinRtRect
import windows.ui.text.core.CoreTextEditContext
import windows.ui.text.core.CoreTextFormatUpdatingEventArgs
import windows.ui.text.core.CoreTextFormatUpdatingResult
import windows.ui.text.core.CoreTextInputPaneDisplayPolicy
import windows.ui.text.core.CoreTextInputScope
import windows.ui.text.core.CoreTextLayoutRequest
import windows.ui.text.core.CoreTextRange
import windows.ui.text.core.CoreTextSelectionRequest
import windows.ui.text.core.CoreTextSelectionRequestedEventArgs
import windows.ui.text.core.CoreTextSelectionUpdatingEventArgs
import windows.ui.text.core.CoreTextSelectionUpdatingResult
import windows.ui.text.core.CoreTextServicesManager
import windows.ui.text.core.CoreTextTextRequest
import windows.ui.text.core.CoreTextTextRequestedEventArgs
import windows.ui.text.core.CoreTextTextUpdatingEventArgs
import windows.ui.text.core.CoreTextTextUpdatingResult

internal class WinUICoreTextInputSession private constructor(
    initialValue: TextFieldValue,
    imeOptions: ImeOptions,
    private val editContext: WinUICoreTextEditContext,
    private val currentLayoutBounds: () -> WinUITextLayoutBounds?,
    private val dispatchEditCommands: (List<EditCommand>) -> Boolean,
) {
    private val eventTokens = mutableListOf<WinUICoreTextEventToken>()
    private var value: TextFieldValue = initialValue
    private var compositionActive = false

    init {
        editContext.name = "Compose WinUI text input"
        editContext.inputScope = imeOptions.toCoreTextInputScope()
        editContext.inputPaneDisplayPolicy = CoreTextInputPaneDisplayPolicy.Automatic
        registerEventHandlers()
    }

    fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) {
        val previousValue = value
        value = newValue

        if (oldValue != null && oldValue.text != newValue.text) {
            editContext.notifyTextChanged(
                modifiedRange = commonChangedRange(oldValue.text, newValue.text),
                newLength = newValue.text.length,
                newSelection = newValue.selection.toCoreTextRange(),
            )
        } else if (previousValue.selection != newValue.selection) {
            editContext.notifySelectionChanged(newValue.selection.toCoreTextRange())
        }
    }

    fun notifyLayoutChanged() {
        editContext.notifyLayoutChanged()
    }

    fun notifyFocusEnter() {
        // KWINRT-026: keep this opt-in until CoreText focus registration is stable.
        editContext.notifyFocusEnter()
    }

    fun notifyFocusLeave() {
        editContext.notifyFocusLeave()
    }

    fun dispose() {
        eventTokens.asReversed().forEach(editContext::removeEventHandler)
        eventTokens.clear()
    }

    private fun registerEventHandlers() {
        eventTokens += editContext.addTextRequested { request ->
            request.text = value.text.sliceCoreTextRange(request.range)
        }
        eventTokens += editContext.addSelectionRequested { request ->
            request.selection = value.selection.toCoreTextRange()
        }
        eventTokens += editContext.addLayoutRequested { request ->
            if (!request.isCanceled) {
                currentLayoutBounds()?.let(request::setLayoutBounds)
            }
        }
        eventTokens += editContext.addTextUpdating { event ->
            if (event.isCanceled) {
                return@addTextUpdating
            }
            val commands = buildList {
                val range = event.range
                add(SetSelectionCommand(range.startCaretPosition, range.endCaretPosition))
                if (compositionActive) {
                    add(SetComposingTextCommand(event.text, 1))
                } else if (event.text.isNotEmpty() || !range.isCollapsed) {
                    add(CommitTextCommand(event.text, 1))
                }
                add(SetSelectionCommand(
                    event.newSelection.startCaretPosition,
                    event.newSelection.endCaretPosition,
                ))
            }
            event.result = if (dispatchEditCommands(commands)) {
                CoreTextTextUpdatingResult.Succeeded
            } else {
                CoreTextTextUpdatingResult.Failed
            }
        }
        eventTokens += editContext.addSelectionUpdating { event ->
            if (event.isCanceled) {
                return@addSelectionUpdating
            }
            val selection = event.selection
            event.result = if (dispatchEditCommands(listOf(
                    SetSelectionCommand(selection.startCaretPosition, selection.endCaretPosition)
                ))
            ) {
                CoreTextSelectionUpdatingResult.Succeeded
            } else {
                CoreTextSelectionUpdatingResult.Failed
            }
        }
        eventTokens += editContext.addFormatUpdating { event ->
            if (!event.isCanceled) {
                event.result = CoreTextFormatUpdatingResult.Failed
            }
        }
        eventTokens += editContext.addCompositionStarted {
            compositionActive = true
        }
        eventTokens += editContext.addCompositionCompleted {
            compositionActive = false
            dispatchEditCommands(listOf(FinishComposingTextCommand()))
        }
    }

    internal companion object {
        fun create(
            initialValue: TextFieldValue,
            imeOptions: ImeOptions,
            currentLayoutBounds: () -> WinUITextLayoutBounds?,
            dispatchEditCommands: (List<EditCommand>) -> Boolean,
        ): WinUICoreTextInputSession =
            create(
                initialValue = initialValue,
                imeOptions = imeOptions,
                editContext = WinUIRealCoreTextEditContext.create(),
                currentLayoutBounds = currentLayoutBounds,
                dispatchEditCommands = dispatchEditCommands,
            )

        fun create(
            initialValue: TextFieldValue,
            imeOptions: ImeOptions,
            editContext: WinUICoreTextEditContext,
            currentLayoutBounds: () -> WinUITextLayoutBounds?,
            dispatchEditCommands: (List<EditCommand>) -> Boolean,
        ): WinUICoreTextInputSession =
            WinUICoreTextInputSession(
                initialValue = initialValue,
                imeOptions = imeOptions,
                editContext = editContext,
                currentLayoutBounds = currentLayoutBounds,
                dispatchEditCommands = dispatchEditCommands,
            )
    }
}

internal interface WinUICoreTextEditContext {
    var name: String
    var inputScope: CoreTextInputScope
    var inputPaneDisplayPolicy: CoreTextInputPaneDisplayPolicy

    fun addTextRequested(handler: (WinUICoreTextTextRequest) -> Unit): WinUICoreTextEventToken
    fun addSelectionRequested(handler: (WinUICoreTextSelectionRequest) -> Unit): WinUICoreTextEventToken
    fun addLayoutRequested(handler: (WinUICoreTextLayoutRequest) -> Unit): WinUICoreTextEventToken
    fun addTextUpdating(handler: (WinUICoreTextTextUpdatingEvent) -> Unit): WinUICoreTextEventToken
    fun addSelectionUpdating(handler: (WinUICoreTextSelectionUpdatingEvent) -> Unit): WinUICoreTextEventToken
    fun addFormatUpdating(handler: (WinUICoreTextFormatUpdatingEvent) -> Unit): WinUICoreTextEventToken
    fun addCompositionStarted(handler: () -> Unit): WinUICoreTextEventToken
    fun addCompositionCompleted(handler: () -> Unit): WinUICoreTextEventToken
    fun removeEventHandler(token: WinUICoreTextEventToken)
    fun notifyFocusEnter()
    fun notifyFocusLeave()
    fun notifyTextChanged(
        modifiedRange: CoreTextRange,
        newLength: Int,
        newSelection: CoreTextRange,
    )
    fun notifySelectionChanged(selection: CoreTextRange)
    fun notifyLayoutChanged()
}

internal interface WinUICoreTextTextRequest {
    val range: CoreTextRange
    var text: String
}

internal interface WinUICoreTextSelectionRequest {
    var selection: CoreTextRange
}

internal interface WinUICoreTextLayoutRequest {
    val isCanceled: Boolean
    fun setLayoutBounds(bounds: WinUITextLayoutBounds)
}

internal interface WinUICoreTextTextUpdatingEvent {
    val range: CoreTextRange
    val text: String
    val newSelection: CoreTextRange
    val isCanceled: Boolean
    var result: CoreTextTextUpdatingResult
}

internal interface WinUICoreTextSelectionUpdatingEvent {
    val selection: CoreTextRange
    val isCanceled: Boolean
    var result: CoreTextSelectionUpdatingResult
}

internal interface WinUICoreTextFormatUpdatingEvent {
    val isCanceled: Boolean
    var result: CoreTextFormatUpdatingResult
}

internal data class WinUICoreTextEventToken(
    val remove: () -> Unit,
)

private class WinUIRealCoreTextEditContext(
    private val editContext: CoreTextEditContext,
) : WinUICoreTextEditContext {
    override var name: String
        get() = editContext.name
        set(value) {
            editContext.name = value
        }

    override var inputScope: CoreTextInputScope
        get() = editContext.inputScope
        set(value) {
            editContext.inputScope = value
        }

    override var inputPaneDisplayPolicy: CoreTextInputPaneDisplayPolicy
        get() = editContext.inputPaneDisplayPolicy
        set(value) {
            editContext.inputPaneDisplayPolicy = value
        }

    override fun addTextRequested(handler: (WinUICoreTextTextRequest) -> Unit): WinUICoreTextEventToken =
        editContext.addTextRequested(TypedEventHandler { _, args ->
                args.request?.let { handler(WinUIRealCoreTextTextRequest(it)) }
            }).let { token -> WinUICoreTextEventToken { editContext.removeTextRequested(token) } }

    override fun addSelectionRequested(
        handler: (WinUICoreTextSelectionRequest) -> Unit,
    ): WinUICoreTextEventToken =
        editContext.addSelectionRequested(TypedEventHandler { _, args ->
                args.request?.let { handler(WinUIRealCoreTextSelectionRequest(it)) }
            }).let { token -> WinUICoreTextEventToken { editContext.removeSelectionRequested(token) } }

    override fun addLayoutRequested(
        handler: (WinUICoreTextLayoutRequest) -> Unit,
    ): WinUICoreTextEventToken =
        editContext.addLayoutRequested(TypedEventHandler { _, args ->
                args.request?.let { handler(WinUIRealCoreTextLayoutRequest(it)) }
            }).let { token -> WinUICoreTextEventToken { editContext.removeLayoutRequested(token) } }

    override fun addTextUpdating(
        handler: (WinUICoreTextTextUpdatingEvent) -> Unit,
    ): WinUICoreTextEventToken =
        editContext.addTextUpdating(TypedEventHandler { _, args ->
                handler(WinUIRealCoreTextTextUpdatingEvent(args))
            }).let { token -> WinUICoreTextEventToken { editContext.removeTextUpdating(token) } }

    override fun addSelectionUpdating(
        handler: (WinUICoreTextSelectionUpdatingEvent) -> Unit,
    ): WinUICoreTextEventToken =
        editContext.addSelectionUpdating(TypedEventHandler { _, args ->
                handler(WinUIRealCoreTextSelectionUpdatingEvent(args))
            }).let { token -> WinUICoreTextEventToken { editContext.removeSelectionUpdating(token) } }

    override fun addFormatUpdating(
        handler: (WinUICoreTextFormatUpdatingEvent) -> Unit,
    ): WinUICoreTextEventToken =
        editContext.addFormatUpdating(TypedEventHandler { _, args ->
                handler(WinUIRealCoreTextFormatUpdatingEvent(args))
            }).let { token -> WinUICoreTextEventToken { editContext.removeFormatUpdating(token) } }

    override fun addCompositionStarted(handler: () -> Unit): WinUICoreTextEventToken =
        editContext.addCompositionStarted(TypedEventHandler { _, args ->
                if (!args.isCanceled) {
                    handler()
                }
            }).let { token -> WinUICoreTextEventToken { editContext.removeCompositionStarted(token) } }

    override fun addCompositionCompleted(handler: () -> Unit): WinUICoreTextEventToken =
        editContext.addCompositionCompleted(TypedEventHandler { _, _ -> handler() })
            .let { token -> WinUICoreTextEventToken { editContext.removeCompositionCompleted(token) } }

    override fun removeEventHandler(token: WinUICoreTextEventToken) {
        token.remove()
    }

    override fun notifyFocusEnter() {
        editContext.notifyFocusEnter()
    }

    override fun notifyFocusLeave() {
        editContext.notifyFocusLeave()
    }

    override fun notifyTextChanged(
        modifiedRange: CoreTextRange,
        newLength: Int,
        newSelection: CoreTextRange,
    ) {
        editContext.notifyTextChanged(modifiedRange, newLength, newSelection)
    }

    override fun notifySelectionChanged(selection: CoreTextRange) {
        editContext.notifySelectionChanged(selection)
    }

    override fun notifyLayoutChanged() {
        editContext.notifyLayoutChanged()
    }

    companion object {
        fun create(): WinUIRealCoreTextEditContext =
            WinUIRealCoreTextEditContext(
                CoreTextServicesManager.getForCurrentView().createEditContext()
            )
    }
}

private class WinUIRealCoreTextTextRequest(
    private val request: CoreTextTextRequest,
) : WinUICoreTextTextRequest {
    override val range: CoreTextRange
        get() = request.range

    override var text: String
        get() = request.text
        set(value) {
            request.text = value
        }
}

private class WinUIRealCoreTextSelectionRequest(
    private val request: CoreTextSelectionRequest,
) : WinUICoreTextSelectionRequest {
    override var selection: CoreTextRange
        get() = request.selection
        set(value) {
            request.selection = value
        }
}

private class WinUIRealCoreTextLayoutRequest(
    private val request: CoreTextLayoutRequest,
) : WinUICoreTextLayoutRequest {
    override val isCanceled: Boolean
        get() = request.isCanceled

    override fun setLayoutBounds(bounds: WinUITextLayoutBounds) {
        request.layoutBounds?.setFrom(bounds)
        request.layoutBoundsVisualPixels?.setFrom(bounds)
    }
}

private class WinUIRealCoreTextTextUpdatingEvent(
    private val event: CoreTextTextUpdatingEventArgs,
) : WinUICoreTextTextUpdatingEvent {
    override val range: CoreTextRange
        get() = event.range
    override val text: String
        get() = event.text
    override val newSelection: CoreTextRange
        get() = event.newSelection
    override val isCanceled: Boolean
        get() = event.isCanceled
    override var result: CoreTextTextUpdatingResult
        get() = event.result
        set(value) {
            event.result = value
        }
}

private class WinUIRealCoreTextSelectionUpdatingEvent(
    private val event: CoreTextSelectionUpdatingEventArgs,
) : WinUICoreTextSelectionUpdatingEvent {
    override val selection: CoreTextRange
        get() = event.selection
    override val isCanceled: Boolean
        get() = event.isCanceled
    override var result: CoreTextSelectionUpdatingResult
        get() = event.result
        set(value) {
            event.result = value
        }
}

private class WinUIRealCoreTextFormatUpdatingEvent(
    private val event: CoreTextFormatUpdatingEventArgs,
) : WinUICoreTextFormatUpdatingEvent {
    override val isCanceled: Boolean
        get() = event.isCanceled
    override var result: CoreTextFormatUpdatingResult
        get() = event.result
        set(value) {
            event.result = value
        }
}

private fun TextRange.toCoreTextRange(): CoreTextRange =
    CoreTextRange(start, end)

private val CoreTextRange.isCollapsed: Boolean
    get() = startCaretPosition == endCaretPosition

private fun windows.ui.text.core.CoreTextLayoutBounds.setFrom(bounds: WinUITextLayoutBounds) {
    textBounds = bounds.innerTextFieldBounds.toWinRtRect()
    controlBounds = bounds.decorationBoxBounds.toWinRtRect()
}

private fun androidx.compose.ui.geometry.Rect.toWinRtRect(): WinRtRect =
    WinRtRect(left, top, width, height)

private fun String.sliceCoreTextRange(range: CoreTextRange): String {
    val start = range.startCaretPosition.coerceIn(0, length)
    val end = range.endCaretPosition.coerceIn(start, length)
    return substring(start, end)
}

private fun commonChangedRange(oldText: String, newText: String): CoreTextRange {
    var prefix = 0
    val minLength = minOf(oldText.length, newText.length)
    while (prefix < minLength && oldText[prefix] == newText[prefix]) {
        prefix++
    }
    var oldSuffix = oldText.length
    var newSuffix = newText.length
    while (oldSuffix > prefix && newSuffix > prefix &&
        oldText[oldSuffix - 1] == newText[newSuffix - 1]
    ) {
        oldSuffix--
        newSuffix--
    }
    return CoreTextRange(prefix, oldSuffix)
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
        else -> CoreTextInputScope.Default
    }
