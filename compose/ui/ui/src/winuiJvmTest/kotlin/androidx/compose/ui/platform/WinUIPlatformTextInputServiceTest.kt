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
