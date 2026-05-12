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

package androidx.compose.ui.focus

import androidx.compose.ui.geometry.Rect
import microsoft.ui.xaml.FocusState
import microsoft.ui.xaml.UIElement

internal class WinUIPlatformFocusOwner(
    private val focusRoot: UIElement,
) : PlatformFocusOwner {
    override fun requestOwnerFocus(
        focusDirection: FocusDirection?,
        previouslyFocusedRect: Rect?,
    ): Boolean {
        runCatching {
            focusRoot.isTabStop = true
            focusRoot.focus(FocusState.Programmatic)
        }
        return true
    }

    override fun clearOwnerFocus() {
        runCatching {
            if (focusRoot.focusState != FocusState.Unfocused) {
                focusRoot.focus(FocusState.Unfocused)
            }
        }
    }

    override fun moveFocusInChildren(focusDirection: FocusDirection): Boolean = false

    override fun getEmbeddedViewFocusRect(): Rect? = null
}
