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
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinUIPlatformTextInputServiceTest {
    @AfterTest
    fun tearDown() {
        WinUIPlatformTextInputService.resetForTest()
    }

    @Test
    fun startUpdateAndStopInputTracksCurrentSessionState() {
        WinUIPlatformTextInputService.startInput(
            value = TextFieldValue("initial"),
            imeOptions = ImeOptions.Default,
            onEditCommand = {},
            onImeActionPerformed = {},
        )

        assertTrue(WinUIPlatformTextInputService.isInputActive)
        assertEquals(TextFieldValue("initial"), WinUIPlatformTextInputService.currentValue)

        WinUIPlatformTextInputService.updateState(
            oldValue = TextFieldValue("initial"),
            newValue = TextFieldValue("updated"),
        )

        assertEquals(TextFieldValue("updated"), WinUIPlatformTextInputService.currentValue)

        WinUIPlatformTextInputService.stopInput()

        assertFalse(WinUIPlatformTextInputService.isInputActive)
        assertEquals(null, WinUIPlatformTextInputService.currentValue)
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
}
