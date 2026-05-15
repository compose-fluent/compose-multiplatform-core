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
import microsoft.ui.xaml.controls.Canvas

internal class WinUIInteropRootContainer(
    private val scheduleUpdate: (() -> Unit) -> Unit,
) {
    val root = Canvas()
    private val children = mutableListOf<UIElement>()

    fun setChildren(content: List<UIElement>) {
        removeStaleChildren(content)
        content.forEachIndexed { targetIndex, child ->
            if (children.getOrNull(targetIndex) === child) return@forEachIndexed
            val existingIndex = children.indexOfIdentity(child)
            if (existingIndex >= 0) {
                move(existingIndex, targetIndex)
            } else {
                insert(targetIndex, child)
            }
        }
        while (children.size > content.size) {
            removeAt(children.lastIndex)
        }
    }

    fun clear() {
        if (children.isEmpty()) return
        children.clear()
        scheduleUpdate {
            root.children.clear()
        }
    }

    private fun removeStaleChildren(content: List<UIElement>) {
        var index = 0
        while (index < children.size) {
            if (content.indexOfIdentity(children[index]) < 0) {
                removeAt(index)
            } else {
                index += 1
            }
        }
    }

    private fun move(from: Int, to: Int) {
        val child = children.removeAt(from)
        children.add(to, child)
        scheduleUpdate {
            root.children.move(from.toUInt(), to.toUInt())
        }
    }

    private fun insert(index: Int, child: UIElement) {
        children.add(index, child)
        scheduleUpdate {
            root.children.add(index, child)
        }
    }

    private fun removeAt(index: Int) {
        children.removeAt(index)
        scheduleUpdate {
            root.children.removeAt(index)
        }
    }
}
