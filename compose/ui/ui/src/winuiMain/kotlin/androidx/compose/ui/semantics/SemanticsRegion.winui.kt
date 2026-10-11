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

package androidx.compose.ui.semantics

import androidx.compose.ui.unit.IntRect

private class WinUiSemanticsRegion : SemanticsRegion {
    private var rects: List<IntRect> = emptyList()

    override fun set(rect: IntRect) {
        rects = if (rect.isEmpty) emptyList() else listOf(rect)
    }

    override fun intersect(region: SemanticsRegion): Boolean {
        val other = (region as? WinUiSemanticsRegion)?.rects ?: listOf(region.bounds)
        rects = rects.flatMap { rect ->
            other.mapNotNull { otherRect ->
                rect.intersect(otherRect).takeUnless(IntRect::isEmpty)
            }
        }
        return !isEmpty
    }

    override fun difference(rect: IntRect): Boolean {
        if (rect.isEmpty || rects.isEmpty()) return !isEmpty
        rects = rects.flatMap { it.subtract(rect) }
        return !isEmpty
    }

    override val bounds: IntRect
        get() = rects.bounds()

    override val isEmpty: Boolean
        get() = rects.isEmpty()
}

internal actual fun SemanticsRegion(): SemanticsRegion = WinUiSemanticsRegion()

private fun IntRect.subtract(rect: IntRect): List<IntRect> {
    val intersection = intersect(rect)
    if (intersection.isEmpty) return listOf(this)
    return listOf(
        IntRect(left, top, right, intersection.top),
        IntRect(left, intersection.bottom, right, bottom),
        IntRect(left, intersection.top, intersection.left, intersection.bottom),
        IntRect(intersection.right, intersection.top, right, intersection.bottom),
    ).filterNot(IntRect::isEmpty)
}

private fun List<IntRect>.bounds(): IntRect {
    if (isEmpty()) return IntRect.Zero
    var left = first().left
    var top = first().top
    var right = first().right
    var bottom = first().bottom
    for (index in 1 until size) {
        val rect = this[index]
        left = minOf(left, rect.left)
        top = minOf(top, rect.top)
        right = maxOf(right, rect.right)
        bottom = maxOf(bottom, rect.bottom)
    }
    return IntRect(left, top, right, bottom)
}
