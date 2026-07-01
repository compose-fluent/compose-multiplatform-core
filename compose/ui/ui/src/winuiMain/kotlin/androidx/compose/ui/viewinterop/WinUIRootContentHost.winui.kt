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

package androidx.compose.ui.viewinterop

import microsoft.ui.xaml.UIElement

internal class WinUIRootContentHost {
    val root = WinUIRootContentControl()
    private var transaction = WinUIInteropMutableTransaction(isInteropActive = false)
    private val interopContainer = WinUIInteropRootContainer(::scheduleUpdate)
    private var currentInteropContent: List<UIElement> = emptyList()

    init {
        root.content = interopContainer.root
    }

    fun setBaseContent(content: List<UIElement>) {
        interopContainer.setBaseChildren(content)
    }

    fun setRootContent(content: List<UIElement>) {
        currentInteropContent = content
        interopContainer.setOverlayChildren(content)
        transaction.isInteropActive = content.isNotEmpty()
    }

    fun scheduleUpdate(action: () -> Unit) {
        transaction.add(action)
    }

    fun retrieveTransaction(): WinUIInteropTransaction {
        val result = transaction
        transaction = WinUIInteropMutableTransaction(isInteropActive = currentInteropContent.isNotEmpty())
        return result
    }
}
