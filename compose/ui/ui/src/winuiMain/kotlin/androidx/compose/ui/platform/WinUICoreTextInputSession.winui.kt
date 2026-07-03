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
import androidx.compose.ui.text.input.EditCommand
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

internal interface WinUICoreTextInputSessionHandle {
    val isCompositionActive: Boolean
    fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue)
    fun notifyLayoutChanged()
    fun notifyFocusEnter()
    fun notifyFocusLeave()
    fun dispose()
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

internal fun createWinUICoreTextEditContextForWinUI(): WinUICoreTextEditContext =
    WinUIRealCoreTextEditContext.create()

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

private fun windows.ui.text.core.CoreTextLayoutBounds.setFrom(bounds: WinUITextLayoutBounds) {
    // The current CoreText projection comes from skiko-winui and routes struct
    // setters through the generic object downcall path. Keep this narrow ABI
    // call until the CoreText projection path is generated and verified here.
    setTextBoundsByValueForWinUI(bounds.innerTextFieldBounds.toWinRTRect())
    setControlBoundsByValueForWinUI(bounds.decorationBoxBounds.toWinRTRect())
}

private fun Rect.toWinRTRect(): WinRTRect =
    WinRTRect(left, top, width, height)

internal expect fun CoreTextSelectionRequest.setSelectionByValueForWinUI(range: CoreTextRange)

internal expect fun CoreTextEditContext.notifySelectionChangedByValueForWinUI(
    selection: CoreTextRange,
)

internal expect fun windows.ui.text.core.CoreTextLayoutBounds.setTextBoundsByValueForWinUI(
    bounds: WinRTRect,
)

internal expect fun windows.ui.text.core.CoreTextLayoutBounds.setControlBoundsByValueForWinUI(
    bounds: WinRTRect,
)

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
        winUIDebugLog("core-text", message())
    }
}
