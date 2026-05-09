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

import androidx.compose.runtime.Composable
import androidx.compose.ui.node.LayoutNode
import microsoft.ui.xaml.controls.ContentControl

/**
 * Root host for a Compose hierarchy embedded in a WinUI tree.
 *
 * This is intentionally independent from Skiko/Desktop/AWT. Rendering, scheduling, and Owner
 * integration are filled in by the WinUI target rather than delegated to the desktop backend.
 */
class WinUIComposeView {
    val root = ContentControl()

    internal val rootLayoutNode = LayoutNode()

    private var content: (@Composable () -> Unit)? = null

    fun setContent(content: @Composable () -> Unit) {
        this.content = content
        TODO("WinUI Owner, recomposer, and frame scheduling are not implemented yet")
    }

    fun disposeComposition() {
        content = null
    }
}
