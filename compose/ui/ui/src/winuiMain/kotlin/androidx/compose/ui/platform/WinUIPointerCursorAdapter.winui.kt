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
import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.winRtProjectionMarshaler
import microsoft.ui.input.InputCursor
import microsoft.ui.xaml.IUIElementProtected
import microsoft.ui.xaml.UIElement
import windows.ui.core.CoreCursor
import windows.ui.core.CoreCursorType

internal class WinUIPointerCursorAdapter(
    private val root: UIElement,
) {
    private var currentCursor: InputCursor? = null

    fun setIcon(icon: PointerIcon) {
        val cursorType = (icon as? WinUIPointerIcon)?.cursorType ?: CoreCursorType.Arrow
        val cursor = InputCursor.createFromCoreCursor(CoreCursor(cursorType, 0u))
        try {
            setProtectedCursor(cursor)
        } catch (throwable: Throwable) {
            cursor.close()
            throw throwable
        }
        currentCursor?.close()
        currentCursor = cursor
    }

    fun dispose() {
        currentCursor?.close()
        currentCursor = null
    }

    private fun setProtectedCursor(cursor: InputCursor) {
        // KWINRT-021: compose-winui cannot author a projected UIElement subclass to set ProtectedCursor.
        root.nativeObject.queryInterface(IUIElementProtected.Metadata.IID).getOrThrow()
            .use { protectedElement ->
                winRtProjectionMarshaler(
                    cursor,
                    "Microsoft.UI.Input.InputCursor",
                    Guid("359B15F9-19C2-5714-8432-75176826406B"),
                ).use { cursorMarshaler ->
                    HResult(
                        ComVtableInvoker.invokeArgs(
                            instance = protectedElement.pointer,
                            slot = IUIElementProtected.Metadata.PROTECTEDCURSOR_SETTER_SLOT,
                            arg0 = cursorMarshaler.abi,
                        ),
                    ).requireSuccess("UIElement.ProtectedCursor setter")
                }
            }
    }
}
