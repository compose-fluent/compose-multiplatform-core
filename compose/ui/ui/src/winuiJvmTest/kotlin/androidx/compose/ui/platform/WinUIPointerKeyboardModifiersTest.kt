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

class WinUIPointerKeyboardModifiersTest {
    @Test
    fun rawVirtualKeyModifierFlagsMapIndividually() {
        assertTrue(winUIPointerKeyboardModifiersFromRawBits(1u).isCtrlPressed)
        assertTrue(winUIPointerKeyboardModifiersFromRawBits(2u).isAltPressed)
        assertTrue(winUIPointerKeyboardModifiersFromRawBits(4u).isShiftPressed)
        assertTrue(winUIPointerKeyboardModifiersFromRawBits(8u).isMetaPressed)
    }

    @Test
    fun rawVirtualKeyModifierFlagsMapCombinations() {
        val modifiers = winUIPointerKeyboardModifiersFromRawBits(1u or 2u or 4u or 8u)

        assertTrue(modifiers.isCtrlPressed)
        assertTrue(modifiers.isAltPressed)
        assertTrue(modifiers.isShiftPressed)
        assertTrue(modifiers.isMetaPressed)
    }

    @Test
    fun rawVirtualKeyModifierFlagsIgnoreUnknownBits() {
        val modifiers = winUIPointerKeyboardModifiersFromRawBits(16u)

        assertFalse(modifiers.isCtrlPressed)
        assertFalse(modifiers.isAltPressed)
        assertFalse(modifiers.isShiftPressed)
        assertFalse(modifiers.isMetaPressed)
    }
}
