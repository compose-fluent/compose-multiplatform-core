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

import androidx.compose.ui.input.pointer.PointerKeyboardModifiers

internal fun winUIPointerKeyboardModifiersFromRawBits(rawBits: UInt): PointerKeyboardModifiers =
    PointerKeyboardModifiers(
        isCtrlPressed = rawBits hasWinUIKeyModifier WinUIVirtualKeyModifier.Control,
        isAltPressed = rawBits hasWinUIKeyModifier WinUIVirtualKeyModifier.Menu,
        isShiftPressed = rawBits hasWinUIKeyModifier WinUIVirtualKeyModifier.Shift,
        isMetaPressed = rawBits hasWinUIKeyModifier WinUIVirtualKeyModifier.Windows,
    )

private infix fun UInt.hasWinUIKeyModifier(modifier: UInt): Boolean =
    this and modifier != 0u

private object WinUIVirtualKeyModifier {
    const val Control: UInt = 1u
    const val Menu: UInt = 2u
    const val Shift: UInt = 4u
    const val Windows: UInt = 8u
}
