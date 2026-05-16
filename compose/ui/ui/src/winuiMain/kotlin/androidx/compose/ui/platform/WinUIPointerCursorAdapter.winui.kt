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

import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.WinUIPointerIcon
import androidx.compose.ui.viewinterop.WinUIRootContentControl
import microsoft.ui.input.InputSystemCursor
import microsoft.ui.input.InputSystemCursorShape
import windows.ui.core.CoreCursorType

internal class WinUIPointerCursorAdapter(
    private val root: WinUIRootContentControl,
) {
    private val cursors = mutableMapOf<InputSystemCursorShape, InputSystemCursor>()

    fun setIcon(icon: PointerIcon) {
        root.setComposePointerCursor(cursor(icon.toInputSystemCursorShape()))
    }

    fun dispose() {
        if (cursors.isNotEmpty()) {
            root.setComposePointerCursor(cursor(InputSystemCursorShape.Arrow))
            cursors.values.forEach(InputSystemCursor::close)
            cursors.clear()
        }
    }

    private fun cursor(shape: InputSystemCursorShape): InputSystemCursor {
        return cursors.getOrPut(shape) {
            InputSystemCursor.create(shape)
        }
    }
}

private fun PointerIcon.toInputSystemCursorShape(): InputSystemCursorShape {
    val cursorType = (this as? WinUIPointerIcon)?.cursorType ?: CoreCursorType.Arrow
    return when (cursorType) {
        CoreCursorType.Cross -> InputSystemCursorShape.Cross
        CoreCursorType.Hand -> InputSystemCursorShape.Hand
        CoreCursorType.IBeam -> InputSystemCursorShape.IBeam
        else -> InputSystemCursorShape.Arrow
    }
}
