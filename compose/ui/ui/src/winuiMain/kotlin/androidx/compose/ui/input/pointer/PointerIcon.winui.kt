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

package androidx.compose.ui.input.pointer

import microsoft.ui.input.InputSystemCursorShape

internal class WinUIPointerIcon(
    private val name: String,
    internal val cursorShape: InputSystemCursorShape,
) : PointerIcon {
    override fun toString(): String = "WinUiPointerIcon($name)"
}

internal actual val pointerIconDefault: PointerIcon =
    WinUIPointerIcon("Default", InputSystemCursorShape.Arrow)
internal actual val pointerIconCrosshair: PointerIcon =
    WinUIPointerIcon("Crosshair", InputSystemCursorShape.Cross)
internal actual val pointerIconText: PointerIcon =
    WinUIPointerIcon("Text", InputSystemCursorShape.IBeam)
internal actual val pointerIconHand: PointerIcon =
    WinUIPointerIcon("Hand", InputSystemCursorShape.Hand)
