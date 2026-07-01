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
import windows.foundation.Rect as WinRTRect
import windows.ui.text.core.CoreTextEditContext
import windows.ui.text.core.CoreTextFormatUpdatingEventArgs
import windows.ui.text.core.CoreTextFormatUpdatingResult
import windows.ui.text.core.CoreTextInputPaneDisplayPolicy
import windows.ui.text.core.CoreTextInputScope
import windows.ui.text.core.CoreTextLayoutRequest
import windows.ui.text.core.CoreTextRange
import windows.ui.text.core.CoreTextSelectionRequest
import windows.ui.text.core.CoreTextSelectionUpdatingEventArgs
import windows.ui.text.core.CoreTextSelectionUpdatingResult
import windows.ui.text.core.CoreTextServicesManager
import windows.ui.text.core.CoreTextTextRequest
import windows.ui.text.core.CoreTextTextUpdatingEventArgs
import windows.ui.text.core.CoreTextTextUpdatingResult

internal class WinUICoreTextInputSession private constructor(
    initialValue: TextFieldValue,
    imeOptions: ImeOptions,
    private val editContext: WinUICoreTextEditContext,
    private val currentValue: () -> TextFieldValue?,
    private val currentLayoutBounds: () -> WinUICoreTextLayoutSnapshot?,
    private val dispatchEditCommands: (List<EditCommand>) -> Boolean,
) {
    private val eventTokens = mutableListOf<WinUICoreTextEventToken>()
    private var value: TextFieldValue = initialValue
    private var compositionActive = false
    private var coreTextUpdateDepth = 0
    private var isDisposed = false
    private var isFocused = false

    val isCompositionActive: Boolean
        get() = compositionActive

    private val isHandlingCoreTextUpdate: Boolean
        get() = coreTextUpdateDepth > 0

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

    fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) {
        if (isDisposed) return
        val previousValue = value
        value = newValue

        if (isHandlingCoreTextUpdate) {
            debugCoreTextInput {
                "updateState skip native notification during CoreText update " +
                    "old=${oldValue?.debugString()} new=${newValue.debugString()}"
            }
            return
        }

        if (oldValue != null && oldValue.text != newValue.text) {
            debugCoreTextInput {
                "updateState notifyTextChanged old=${oldValue.debugString()} " +
                    "new=${newValue.debugString()}"
            }
            val textChange = commonTextChange(oldValue.text, newValue.text)
            runCoreTextCallback("NotifyTextChanged") {
                editContext.notifyTextChangedByValueForWinUI(
                    modifiedRange = textChange.modifiedRange,
                    newLength = textChange.newLength,
                    newSelection = newValue.selection.toCoreTextRange(),
                )
            }
        } else if (previousValue.selection != newValue.selection) {
            debugCoreTextInput {
                "updateState notifySelectionChanged previous=${previousValue.selection} " +
                    "new=${newValue.selection}"
            }
            runCoreTextCallback("NotifySelectionChanged") {
                editContext.notifySelectionChangedByValueForWinUI(newValue.selection.toCoreTextRange())
            }
        }
    }

    fun notifyLayoutChanged() {
        debugCoreTextInput { "notifyLayoutChanged bounds=${currentLayoutBounds()?.debugString()}" }
        runCoreTextCallback("NotifyLayoutChanged") {
            editContext.notifyLayoutChanged()
        }
    }

    fun notifyFocusEnter() {
        if (!isDisposed && !isFocused) {
            debugCoreTextInput { "notifyFocusEnter" }
            runCoreTextCallback("NotifyFocusEnter") {
                editContext.notifyFocusEnter()
                isFocused = true
            }
        }
    }

    fun notifyFocusLeave() {
        if (!isDisposed && isFocused) {
            debugCoreTextInput { "notifyFocusLeave" }
            runCoreTextCallback("NotifyFocusLeave") {
                editContext.notifyFocusLeave()
                isFocused = false
            }
        }
    }

    fun dispose() {
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
                // TODO(KWINRT-050): Write CoreTextRange by value; generated
                // struct setters currently pass the native buffer pointer.
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
                    if (bounds != null && bounds.hasUsableBounds) {
                        val normalizedBounds = bounds
                            .withNonEmptyTextBounds()
                            .preferVisualBounds()
                        debugCoreTextInput {
                            "LayoutRequested write normalizedBounds=${normalizedBounds.debugString()}"
                        }
                        request.setLayoutBounds(normalizedBounds)
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
                editContext = WinUIRealCoreTextEditContext.create(),
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
    fun notifyTextChangedByValueForWinUI(
        modifiedRange: CoreTextRange,
        newLength: Int,
        newSelection: CoreTextRange,
    ) {
        notifyTextChanged(modifiedRange, newLength, newSelection)
    }
    fun notifySelectionChanged(selection: CoreTextRange)
    fun notifySelectionChangedByValueForWinUI(selection: CoreTextRange) {
        notifySelectionChanged(selection)
    }
    fun notifyLayoutChanged()
}

internal interface WinUICoreTextTextRequest {
    val range: CoreTextRange
    var text: String
}

internal interface WinUICoreTextSelectionRequest {
    var selection: CoreTextRange
    fun setSelectionByValueForWinUI(selection: CoreTextRange)
}

internal interface WinUICoreTextLayoutRequest {
    val isCanceled: Boolean
    fun setLayoutBounds(bounds: WinUICoreTextLayoutSnapshot)
}

internal data class WinUICoreTextLayoutSnapshot(
    val layoutBounds: WinUITextLayoutBounds?,
    val visualBounds: WinUITextLayoutBounds?,
)

internal val WinUICoreTextLayoutSnapshot.hasUsableBounds: Boolean
    get() = visualBounds?.hasUsableBounds == true || layoutBounds?.hasUsableBounds == true

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
        editContext.addTextRequested { _, args ->
            runRealCoreTextCallback("TextRequested") {
                args.request?.let { handler(WinUIRealCoreTextTextRequest(it)) }
            }
        }.let { token -> WinUICoreTextEventToken { editContext.removeTextRequested(token) } }

    override fun addSelectionRequested(
        handler: (WinUICoreTextSelectionRequest) -> Unit,
    ): WinUICoreTextEventToken =
        editContext.addSelectionRequested { _, args ->
            runRealCoreTextCallback("SelectionRequested") {
                args.request?.let { handler(WinUIRealCoreTextSelectionRequest(it)) }
            }
        }.let { token -> WinUICoreTextEventToken { editContext.removeSelectionRequested(token) } }

    override fun addLayoutRequested(
        handler: (WinUICoreTextLayoutRequest) -> Unit,
    ): WinUICoreTextEventToken =
        editContext.addLayoutRequested { _, args ->
            runRealCoreTextCallback("LayoutRequested") {
                args.request?.let { handler(WinUIRealCoreTextLayoutRequest(it)) }
            }
        }.let { token -> WinUICoreTextEventToken { editContext.removeLayoutRequested(token) } }

    override fun addTextUpdating(
        handler: (WinUICoreTextTextUpdatingEvent) -> Unit,
    ): WinUICoreTextEventToken =
        editContext.addTextUpdating { _, args ->
            runRealCoreTextCallback("TextUpdating") {
                handler(WinUIRealCoreTextTextUpdatingEvent(args))
            }
        }.let { token -> WinUICoreTextEventToken { editContext.removeTextUpdating(token) } }

    override fun addSelectionUpdating(
        handler: (WinUICoreTextSelectionUpdatingEvent) -> Unit,
    ): WinUICoreTextEventToken =
        editContext.addSelectionUpdating { _, args ->
            runRealCoreTextCallback("SelectionUpdating") {
                handler(WinUIRealCoreTextSelectionUpdatingEvent(args))
            }
        }.let { token -> WinUICoreTextEventToken { editContext.removeSelectionUpdating(token) } }

    override fun addFormatUpdating(
        handler: (WinUICoreTextFormatUpdatingEvent) -> Unit,
    ): WinUICoreTextEventToken =
        editContext.addFormatUpdating { _, args ->
            runRealCoreTextCallback("FormatUpdating") {
                handler(WinUIRealCoreTextFormatUpdatingEvent(args))
            }
        }.let { token -> WinUICoreTextEventToken { editContext.removeFormatUpdating(token) } }

    override fun addCompositionStarted(handler: () -> Unit): WinUICoreTextEventToken =
        editContext.addCompositionStarted { _, args ->
            runRealCoreTextCallback("CompositionStarted") {
                if (!args.isCanceled) {
                    handler()
                }
            }
        }.let { token -> WinUICoreTextEventToken { editContext.removeCompositionStarted(token) } }

    override fun addCompositionCompleted(handler: () -> Unit): WinUICoreTextEventToken =
        editContext.addCompositionCompleted { _, _ ->
            runRealCoreTextCallback("CompositionCompleted", handler)
        }.let { token -> WinUICoreTextEventToken { editContext.removeCompositionCompleted(token) } }

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

    override fun notifyTextChangedByValueForWinUI(
        modifiedRange: CoreTextRange,
        newLength: Int,
        newSelection: CoreTextRange,
    ) {
        editContext.notifyTextChangedByValueForWinUI(modifiedRange, newLength, newSelection)
    }

    override fun notifySelectionChanged(selection: CoreTextRange) {
        editContext.notifySelectionChanged(selection)
    }

    override fun notifySelectionChangedByValueForWinUI(selection: CoreTextRange) {
        editContext.notifySelectionChangedByValueForWinUI(selection)
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

    override fun setSelectionByValueForWinUI(selection: CoreTextRange) {
        request.setSelectionByValueForWinUI(selection)
    }
}

private class WinUIRealCoreTextLayoutRequest(
    private val request: CoreTextLayoutRequest,
) : WinUICoreTextLayoutRequest {
    override val isCanceled: Boolean
        get() = request.isCanceled

    override fun setLayoutBounds(bounds: WinUICoreTextLayoutSnapshot) {
        val layoutBounds = bounds.layoutBounds
        if (layoutBounds != null && layoutBounds.hasUsableBounds) {
            debugCoreTextInput { "setLayoutBounds layout=${layoutBounds.debugString()}" }
            request.layoutBounds?.setFrom(layoutBounds)
        }
        val visualBounds = bounds.visualBounds
        if (visualBounds != null && visualBounds.hasUsableBounds) {
            debugCoreTextInput { "setLayoutBounds visual=${visualBounds.debugString()}" }
            request.layoutBoundsVisualPixels?.setFrom(visualBounds)
        }
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

private fun CoreTextRange.debugString(): String =
    "CoreTextRange(start=$startCaretPosition, end=$endCaretPosition)"

private fun WinUICoreTextLayoutSnapshot.debugString(): String =
    "WinUICoreTextLayoutSnapshot(layout=${layoutBounds?.debugString()}, " +
        "visual=${visualBounds?.debugString()})"

private fun windows.ui.text.core.CoreTextLayoutBounds.setFrom(bounds: WinUITextLayoutBounds) {
    // TODO(KWINRT-050): Generated struct setters pass a pointer instead of the
    // struct by value. Use a narrow CoreText workaround until kotlin-winrt fixes it.
    setTextBoundsByValueForWinUI(bounds.innerTextFieldBounds.toWinRTRect())
    setControlBoundsByValueForWinUI(bounds.decorationBoxBounds.toWinRTRect())
}

private fun WinUICoreTextLayoutSnapshot.withNonEmptyTextBounds(): WinUICoreTextLayoutSnapshot =
    copy(
        layoutBounds = layoutBounds?.withNonEmptyTextBounds(),
        visualBounds = visualBounds?.withNonEmptyTextBounds(),
    )

private fun WinUICoreTextLayoutSnapshot.preferVisualBounds(): WinUICoreTextLayoutSnapshot =
    if (visualBounds?.hasUsableBounds == true) {
        copy(layoutBounds = null)
    } else {
        copy(visualBounds = null)
    }

private fun WinUITextLayoutBounds.withNonEmptyTextBounds(): WinUITextLayoutBounds =
    copy(innerTextFieldBounds = innerTextFieldBounds.withMinimumWidth())

private fun Rect.withMinimumWidth(): Rect =
    if (width > 0f) {
        this
    } else {
        Rect(left, top, left + MinimumCoreTextTextBoundsWidth, bottom)
    }

private fun Rect.toWinRTRect(): WinRTRect =
    WinRTRect(left, top, width, height)

private const val MinimumCoreTextTextBoundsWidth = 1f

internal expect fun CoreTextSelectionRequest.setSelectionByValueForWinUI(range: CoreTextRange)

internal expect fun CoreTextEditContext.notifyTextChangedByValueForWinUI(
    modifiedRange: CoreTextRange,
    newLength: Int,
    newSelection: CoreTextRange,
)

internal expect fun CoreTextEditContext.notifySelectionChangedByValueForWinUI(
    selection: CoreTextRange,
)

internal expect fun windows.ui.text.core.CoreTextLayoutBounds.setTextBoundsByValueForWinUI(
    bounds: WinRTRect,
)

internal expect fun windows.ui.text.core.CoreTextLayoutBounds.setControlBoundsByValueForWinUI(
    bounds: WinRTRect,
)

private fun String.sliceCoreTextRange(startCaretPosition: Int, endCaretPosition: Int): String {
    val start = startCaretPosition.coerceIn(0, length)
    val end = endCaretPosition.coerceIn(start, length)
    return substring(start, end)
}

private fun commonTextChange(oldText: String, newText: String): CoreTextChange {
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
    return CoreTextChange(
        modifiedRange = CoreTextRange(prefix, oldSuffix),
        newLength = newSuffix - prefix,
    )
}

private data class CoreTextChange(
    val modifiedRange: CoreTextRange,
    val newLength: Int,
)

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

private inline fun runRealCoreTextCallback(
    name: String,
    block: () -> Unit,
) {
    try {
        block()
    } catch (throwable: Throwable) {
        debugCoreTextInput {
            "CoreText $name WinRT callback failed: ${throwable.stackTraceToString()}"
        }
    }
}

private inline fun debugCoreTextInput(message: () -> String) {
    if (winUISystemBooleanProperty("compose.winui.textInput.debug")) {
        println("[compose-winui:core-text] ${message()}")
    }
}
