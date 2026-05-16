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
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PlatformTextInputService
import androidx.compose.ui.text.input.TextFieldValue

internal object WinUIPlatformTextInputService : PlatformTextInputService {
    private var activeInputSession: WinUITextInputSessionState? = null

    internal val isInputActive: Boolean
        get() = activeInputSession != null

    internal val isSoftwareKeyboardVisible: Boolean
        get() = activeInputSession?.isSoftwareKeyboardVisible == true

    internal val currentValue: TextFieldValue?
        get() = activeInputSession?.value

    override fun startInput(
        value: TextFieldValue,
        imeOptions: ImeOptions,
        onEditCommand: (List<EditCommand>) -> Unit,
        onImeActionPerformed: (ImeAction) -> Unit,
    ) {
        activeInputSession = WinUITextInputSessionState(
            value = value,
            imeOptions = imeOptions,
            onEditCommand = onEditCommand,
            onImeActionPerformed = onImeActionPerformed,
        )
    }

    override fun stopInput() {
        activeInputSession = null
    }

    override fun showSoftwareKeyboard() {
        activeInputSession = activeInputSession?.copy(isSoftwareKeyboardVisible = true)
    }

    override fun hideSoftwareKeyboard() {
        activeInputSession = activeInputSession?.copy(isSoftwareKeyboardVisible = false)
    }

    override fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) {
        activeInputSession = activeInputSession?.copy(
            oldValue = oldValue,
            value = newValue,
        )
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
    }

    internal fun resetForTest() {
        activeInputSession = null
    }
}

private data class WinUITextInputSessionState(
    val value: TextFieldValue,
    val imeOptions: ImeOptions,
    val onEditCommand: (List<EditCommand>) -> Unit,
    val onImeActionPerformed: (ImeAction) -> Unit,
    val oldValue: TextFieldValue? = null,
    val isSoftwareKeyboardVisible: Boolean = false,
    val textFieldValue: TextFieldValue? = null,
    val offsetMapping: OffsetMapping? = null,
    val textLayoutResult: TextLayoutResult? = null,
    val innerTextFieldBounds: Rect? = null,
    val decorationBoxBounds: Rect? = null,
)
