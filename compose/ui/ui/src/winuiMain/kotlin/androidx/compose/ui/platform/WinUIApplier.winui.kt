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

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.ComposeNodeLifecycleCallback
import microsoft.ui.xaml.UIElement

internal open class WinUINode(
    open val interopView: UIElement? = null,
) : ComposeNodeLifecycleCallback {
    private val children = mutableListOf<WinUINode>()

    fun insertAt(index: Int, instance: WinUINode) {
        children.add(index, instance)
    }

    fun removeAt(index: Int, count: Int) {
        repeat(count) {
            children.removeAt(index).onRelease()
        }
    }

    fun move(from: Int, to: Int, count: Int) {
        val moved = ArrayList<WinUINode>(count)
        repeat(count) {
            moved += children.removeAt(from)
        }
        children.addAll(if (to > from) to - count else to, moved)
    }

    fun removeAll() {
        children.asReversed().forEach { it.onRelease() }
        children.clear()
    }

    fun firstInteropView(): UIElement? {
        return interopView ?: children.firstNotNullOfOrNull { it.firstInteropView() }
    }

    override fun onReuse() = Unit

    override fun onDeactivate() = Unit

    override fun onRelease() {
        removeAll()
    }
}

internal class WinUIApplier(
    root: WinUINode,
    private val onEndChangesCallback: () -> Unit,
) : AbstractApplier<WinUINode>(root) {
    override fun insertTopDown(index: Int, instance: WinUINode) = Unit

    override fun insertBottomUp(index: Int, instance: WinUINode) {
        current.insertAt(index, instance)
    }

    override fun remove(index: Int, count: Int) {
        current.removeAt(index, count)
    }

    override fun move(from: Int, to: Int, count: Int) {
        current.move(from, to, count)
    }

    override fun onClear() {
        root.removeAll()
    }

    override fun onEndChanges() {
        onEndChangesCallback()
    }

    override fun reuse() {
        current.onReuse()
    }
}
