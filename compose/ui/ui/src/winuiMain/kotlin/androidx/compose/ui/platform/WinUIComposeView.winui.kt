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
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import microsoft.ui.xaml.UIElement
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Root host for a Compose hierarchy embedded in a WinUI tree.
 *
 * This is intentionally independent from Skiko/Desktop/AWT. Rendering, scheduling, and Owner
 * integration are filled in by the WinUI target rather than delegated to the desktop backend.
 */
class WinUIComposeView(
    val root: UIElement,
    private val setRootContent: (UIElement?) -> Unit,
) {
    internal val rootNode = WinUINode()

    private var recomposer: Recomposer? = null
    private var composition: Composition? = null
    private var content: (@Composable () -> Unit)? = null

    fun setContent(content: @Composable () -> Unit) {
        this.content = content
        val currentComposition = composition ?: createComposition().also {
            composition = it
        }
        currentComposition.setContent(content)
        syncRootContent()
    }

    fun disposeComposition() {
        composition?.dispose()
        composition = null
        recomposer?.close()
        recomposer = null
        content = null
        setRootContent(null)
        rootNode.removeAll()
    }

    private fun createComposition(): Composition {
        val currentRecomposer = Recomposer(EmptyCoroutineContext)
        recomposer = currentRecomposer
        val applier = WinUIApplier(rootNode, ::syncRootContent)
        return Composition(
            applier = applier,
            parent = currentRecomposer,
        )
    }

    private fun syncRootContent() {
        setRootContent(rootNode.firstInteropView())
    }
}
