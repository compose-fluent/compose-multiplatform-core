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

import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import windows.system.VirtualKeyModifiers

class WinUIPointerKeyboardModifiersTest {
    @Test
    fun virtualKeyModifierFlagsMapIndividually() {
        assertTrue(winUIPointerKeyboardModifiersFromWinUI(VirtualKeyModifiers.Control).isCtrlPressed)
        assertTrue(winUIPointerKeyboardModifiersFromWinUI(VirtualKeyModifiers.Menu).isAltPressed)
        assertTrue(winUIPointerKeyboardModifiersFromWinUI(VirtualKeyModifiers.Shift).isShiftPressed)
        assertTrue(winUIPointerKeyboardModifiersFromWinUI(VirtualKeyModifiers.Windows).isMetaPressed)
    }

    @Test
    fun virtualKeyModifierFlagsMapCombinations() {
        val modifiers = winUIPointerKeyboardModifiersFromWinUI(
            VirtualKeyModifiers.Control or
                VirtualKeyModifiers.Menu or
                VirtualKeyModifiers.Shift or
                VirtualKeyModifiers.Windows
        )

        assertTrue(modifiers.isCtrlPressed)
        assertTrue(modifiers.isAltPressed)
        assertTrue(modifiers.isShiftPressed)
        assertTrue(modifiers.isMetaPressed)
    }

    @Test
    fun virtualKeyModifierFlagsIgnoreUnknownBits() {
        val modifiers = winUIPointerKeyboardModifiersFromWinUI(VirtualKeyModifiers(16u))

        assertFalse(modifiers.isCtrlPressed)
        assertFalse(modifiers.isAltPressed)
        assertFalse(modifiers.isShiftPressed)
        assertFalse(modifiers.isMetaPressed)
    }
}
