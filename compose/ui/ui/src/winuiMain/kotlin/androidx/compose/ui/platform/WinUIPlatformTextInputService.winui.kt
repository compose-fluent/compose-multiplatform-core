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
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.BackspaceCommand
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.DeleteSurroundingTextCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.FinishComposingTextCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PlatformTextInputService
import androidx.compose.ui.text.input.SetComposingRegionCommand
import androidx.compose.ui.text.input.SetComposingTextCommand
import androidx.compose.ui.text.input.SetSelectionCommand
import androidx.compose.ui.text.input.TextFieldValue

internal object WinUIPlatformTextInputService : PlatformTextInputService {
    private var activeInputSession: WinUITextInputSessionState? = null
    private var activeInputMethodSession: WinUITextInputMethodSessionState? = null
    internal val nativeBridge: WinUINativeTextInputBridge = WinUINativeTextInputBridge(this)

    internal val isInputActive: Boolean
        get() = activeInputSession != null

    internal val isInputMethodActive: Boolean
        get() = activeInputMethodSession != null

    internal val isSoftwareKeyboardVisible: Boolean
        get() = activeInputSession?.isSoftwareKeyboardVisible == true ||
            activeInputMethodSession?.isSoftwareKeyboardVisible == true

    internal val isNativeTextInputFocused: Boolean
        get() = activeInputSession?.isNativeTextInputFocused == true

    internal val currentValue: TextFieldValue?
        get() = activeInputSession?.value

    internal val currentImeOptions: ImeOptions?
        get() = activeInputSession?.imeOptions

    internal val previousValue: TextFieldValue?
        get() = activeInputSession?.oldValue

    internal val currentInputMethodRequest: PlatformTextInputMethodRequest?
        get() = activeInputMethodSession?.request

    override fun startInput(
        value: TextFieldValue,
        imeOptions: ImeOptions,
        onEditCommand: (List<EditCommand>) -> Unit,
        onImeActionPerformed: (ImeAction) -> Unit,
    ) {
        nativeBridge.disposeCoreTextSession()
        activeInputSession = WinUITextInputSessionState(
            value = value,
            imeOptions = imeOptions,
            onEditCommand = onEditCommand,
            onImeActionPerformed = onImeActionPerformed,
        )
    }

    override fun stopInput() {
        nativeBridge.disposeCoreTextSession()
        activeInputSession = null
    }

    override fun showSoftwareKeyboard() {
        activeInputSession = activeInputSession?.copy(isSoftwareKeyboardVisible = true)
        activeInputMethodSession =
            activeInputMethodSession?.copy(isSoftwareKeyboardVisible = true)
    }

    override fun hideSoftwareKeyboard() {
        activeInputSession = activeInputSession?.copy(isSoftwareKeyboardVisible = false)
        activeInputMethodSession =
            activeInputMethodSession?.copy(isSoftwareKeyboardVisible = false)
    }

    override fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) {
        activeInputSession = activeInputSession?.copy(
            oldValue = oldValue,
            value = newValue,
        )
        nativeBridge.updateCoreTextState(oldValue, newValue)
    }

    override fun updateTextLayoutResult(
        textFieldValue: TextFieldValue,
        offsetMapping: OffsetMapping,
        textLayoutResult: TextLayoutResult,
        textFieldToRootTransform: (Matrix) -> Unit,
        innerTextFieldBounds: Rect,
        decorationBoxBounds: Rect,
    ) {
        activeInputSession = activeInputSession?.copy(
            textFieldValue = textFieldValue,
            offsetMapping = offsetMapping,
            textLayoutResult = textLayoutResult,
            innerTextFieldBounds = innerTextFieldBounds,
            decorationBoxBounds = decorationBoxBounds,
        )
        nativeBridge.notifyCoreTextLayoutChanged()
    }

    internal fun enterNativeTextInputFocus(): Boolean {
        val session = activeInputSession ?: return false
        activeInputSession = session.copy(isNativeTextInputFocused = true)
        return true
    }

    internal fun exitNativeTextInputFocus(): Boolean {
        val session = activeInputSession ?: return false
        activeInputSession = session.copy(
            isNativeTextInputFocused = false,
            isSoftwareKeyboardVisible = false,
        )
        return true
    }

    internal fun sendEditCommands(commands: List<EditCommand>): Boolean {
        val session = activeInputSession ?: return false
        session.onEditCommand(commands)
        return true
    }

    internal fun commitText(text: String, newCursorPosition: Int = 1): Boolean =
        sendEditCommands(listOf(CommitTextCommand(text, newCursorPosition)))

    internal fun setComposingText(text: String, newCursorPosition: Int = 1): Boolean =
        sendEditCommands(listOf(SetComposingTextCommand(text, newCursorPosition)))

    internal fun setComposingRegion(start: Int, end: Int): Boolean =
        sendEditCommands(listOf(SetComposingRegionCommand(start, end)))

    internal fun finishComposingText(): Boolean =
        sendEditCommands(listOf(FinishComposingTextCommand()))

    internal fun setSelection(start: Int, end: Int): Boolean =
        sendEditCommands(listOf(SetSelectionCommand(start, end)))

    internal fun deleteSurroundingText(lengthBeforeCursor: Int, lengthAfterCursor: Int): Boolean =
        sendEditCommands(
            listOf(DeleteSurroundingTextCommand(lengthBeforeCursor, lengthAfterCursor))
        )

    internal fun backspace(): Boolean =
        sendEditCommands(listOf(BackspaceCommand()))

    internal fun performImeAction(action: ImeAction): Boolean {
        val session = activeInputSession ?: return false
        session.onImeActionPerformed(action)
        return true
    }

    internal fun resetForTest() {
        nativeBridge.disposeCoreTextSession()
        activeInputSession = null
        activeInputMethodSession = null
    }

    internal fun startInputMethod(request: PlatformTextInputMethodRequest) {
        activeInputMethodSession = WinUITextInputMethodSessionState(
            request = request,
            isSoftwareKeyboardVisible = true,
        )
    }

    internal fun stopInputMethod(request: PlatformTextInputMethodRequest) {
        if (activeInputMethodSession?.request === request) {
            activeInputMethodSession = null
        }
    }
}

internal class WinUINativeTextInputBridge(
    private val textInputService: WinUIPlatformTextInputService,
) {
    private var coreTextSession: WinUICoreTextInputSession? = null

    val isFocused: Boolean
        get() = textInputService.isNativeTextInputFocused

    val isCoreTextSessionActive: Boolean
        get() = coreTextSession != null

    fun enterFocus(): Boolean =
        textInputService.enterNativeTextInputFocus()

    fun exitFocus(): Boolean =
        textInputService.exitNativeTextInputFocus().also { didExit ->
            if (didExit) {
                coreTextSession?.notifyFocusLeave()
            }
        }

    fun attachCoreTextForCurrentInput(
        notifyNativeFocus: Boolean = false,
    ): Boolean {
        val value = textInputService.currentValue ?: return false
        val imeOptions = textInputService.currentImeOptions ?: return false
        return attachCoreTextForCurrentInput(
            editContext = null,
            initialValue = value,
            imeOptions = imeOptions,
            notifyNativeFocus = notifyNativeFocus,
        )
    }

    internal fun attachCoreTextForCurrentInput(
        editContext: WinUICoreTextEditContext,
        notifyNativeFocus: Boolean = false,
    ): Boolean {
        val value = textInputService.currentValue ?: return false
        val imeOptions = textInputService.currentImeOptions ?: return false
        return attachCoreTextForCurrentInput(
            editContext = editContext,
            initialValue = value,
            imeOptions = imeOptions,
            notifyNativeFocus = notifyNativeFocus,
        )
    }

    private fun attachCoreTextForCurrentInput(
        editContext: WinUICoreTextEditContext?,
        initialValue: TextFieldValue,
        imeOptions: ImeOptions,
        notifyNativeFocus: Boolean,
    ): Boolean {
        disposeCoreTextSession()
        coreTextSession = if (editContext != null) {
            WinUICoreTextInputSession.create(
                initialValue = initialValue,
                imeOptions = imeOptions,
                editContext = editContext,
                dispatchEditCommands = textInputService::sendEditCommands,
            )
        } else {
            WinUICoreTextInputSession.create(
                initialValue = initialValue,
                imeOptions = imeOptions,
                dispatchEditCommands = textInputService::sendEditCommands,
            )
        }
        textInputService.enterNativeTextInputFocus()
        if (notifyNativeFocus) {
            coreTextSession?.notifyFocusEnter()
        }
        return true
    }

    internal fun updateCoreTextState(oldValue: TextFieldValue?, newValue: TextFieldValue) {
        coreTextSession?.updateState(oldValue, newValue)
    }

    internal fun notifyCoreTextLayoutChanged() {
        coreTextSession?.notifyLayoutChanged()
    }

    internal fun disposeCoreTextSession() {
        coreTextSession?.dispose()
        coreTextSession = null
    }

    fun commitText(text: String, newCursorPosition: Int = 1): Boolean =
        textInputService.commitText(text, newCursorPosition)

    fun setComposingText(text: String, newCursorPosition: Int = 1): Boolean =
        textInputService.setComposingText(text, newCursorPosition)

    fun setComposingRegion(start: Int, end: Int): Boolean =
        textInputService.setComposingRegion(start, end)

    fun finishComposingText(): Boolean =
        textInputService.finishComposingText()

    fun setSelection(start: Int, end: Int): Boolean =
        textInputService.setSelection(start, end)

    fun deleteSurroundingText(lengthBeforeCursor: Int, lengthAfterCursor: Int): Boolean =
        textInputService.deleteSurroundingText(lengthBeforeCursor, lengthAfterCursor)

    fun backspace(): Boolean =
        textInputService.backspace()

    fun performImeAction(action: ImeAction): Boolean =
        textInputService.performImeAction(action)
}

private data class WinUITextInputSessionState(
    val value: TextFieldValue,
    val imeOptions: ImeOptions,
    val onEditCommand: (List<EditCommand>) -> Unit,
    val onImeActionPerformed: (ImeAction) -> Unit,
    val oldValue: TextFieldValue? = null,
    val isSoftwareKeyboardVisible: Boolean = false,
    val isNativeTextInputFocused: Boolean = false,
    val textFieldValue: TextFieldValue? = null,
    val offsetMapping: OffsetMapping? = null,
    val textLayoutResult: TextLayoutResult? = null,
    val innerTextFieldBounds: Rect? = null,
    val decorationBoxBounds: Rect? = null,
)

private data class WinUITextInputMethodSessionState(
    val request: PlatformTextInputMethodRequest,
    val isSoftwareKeyboardVisible: Boolean,
)
