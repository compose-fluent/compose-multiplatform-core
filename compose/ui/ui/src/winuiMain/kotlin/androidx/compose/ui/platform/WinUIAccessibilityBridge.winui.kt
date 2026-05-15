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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.postDelayed as composePostDelayed
import androidx.compose.ui.removePost as composeRemovePost
import androidx.compose.ui.semantics.SemanticsOwner

internal class WinUIAccessibilityBridge(
    private val postDelayed: (Long, () -> Unit) -> Any = { delayMillis, block ->
        composePostDelayed(delayMillis, block)
    },
    private val removePost: (Any?) -> Unit = { token ->
        composeRemovePost(token)
    },
    private val onUpdate: (WinUIAccessibilityUpdate) -> Unit = {},
) {
    private val changedLayoutNodeIds = linkedSetOf<Int>()
    private var pendingPost: Any? = null
    private var pendingSemanticsOwner: SemanticsOwner? = null
    private var hasPendingSemanticsChange = false
    private var hasPendingScrollChange = false
    private var pendingScrollDelta = Offset.Zero
    private var isDisposed = false

    var isAccessibilityForcedForTesting: Boolean = false
        private set

    var accessibilityEventBatchIntervalMillis: Long = DefaultEventBatchIntervalMillis
        private set

    var currentSemanticsNodesInvalidated: Boolean = false
        private set

    fun forceAccessibilityForTesting(enable: Boolean) {
        if (isDisposed || isAccessibilityForcedForTesting == enable) return
        isAccessibilityForcedForTesting = enable
        if (enable) {
            scheduleFlushIfNeeded()
        } else {
            cancelPendingFlush()
        }
    }

    fun setAccessibilityEventBatchIntervalMillis(intervalMillis: Long) {
        accessibilityEventBatchIntervalMillis = intervalMillis.coerceAtLeast(0L)
    }

    fun onSemanticsChange(semanticsOwner: SemanticsOwner) {
        if (isDisposed) return
        currentSemanticsNodesInvalidated = true
        hasPendingSemanticsChange = true
        pendingSemanticsOwner = semanticsOwner
        scheduleFlushIfNeeded()
    }

    fun onLayoutChange(semanticsOwner: SemanticsOwner, semanticsId: Int) {
        if (isDisposed) return
        currentSemanticsNodesInvalidated = true
        changedLayoutNodeIds += semanticsId
        pendingSemanticsOwner = semanticsOwner
        scheduleFlushIfNeeded()
    }

    fun onScrollChanged(delta: Offset) {
        if (isDisposed) return
        hasPendingScrollChange = true
        pendingScrollDelta += delta
        scheduleFlushIfNeeded()
    }

    fun dispose() {
        isDisposed = true
        cancelPendingFlush()
        changedLayoutNodeIds.clear()
        pendingSemanticsOwner = null
        hasPendingSemanticsChange = false
        hasPendingScrollChange = false
        pendingScrollDelta = Offset.Zero
    }

    fun stateForTest(): WinUIAccessibilityBridgeState =
        WinUIAccessibilityBridgeState(
            isAccessibilityForcedForTesting = isAccessibilityForcedForTesting,
            accessibilityEventBatchIntervalMillis = accessibilityEventBatchIntervalMillis,
            currentSemanticsNodesInvalidated = currentSemanticsNodesInvalidated,
            hasPendingFlush = pendingPost != null,
            pendingSemanticsChange = hasPendingSemanticsChange,
            pendingLayoutNodeIds = changedLayoutNodeIds.toList(),
            pendingScrollDelta = pendingScrollDelta,
            hasPendingScrollChange = hasPendingScrollChange,
        )

    private fun scheduleFlushIfNeeded() {
        if (
            !isAccessibilityForcedForTesting ||
            isDisposed ||
            pendingPost != null ||
            !hasPendingAccessibilityUpdate()
        ) {
            return
        }
        pendingPost = postDelayed(accessibilityEventBatchIntervalMillis, ::flush)
    }

    private fun hasPendingAccessibilityUpdate(): Boolean =
        hasPendingSemanticsChange ||
            changedLayoutNodeIds.isNotEmpty() ||
            hasPendingScrollChange

    private fun flush() {
        pendingPost = null
        if (isDisposed || !isAccessibilityForcedForTesting || !hasPendingAccessibilityUpdate()) {
            return
        }
        val update = WinUIAccessibilityUpdate(
            semanticsOwner = pendingSemanticsOwner,
            semanticsChanged = hasPendingSemanticsChange,
            layoutChangedSemanticsIds = changedLayoutNodeIds.toList(),
            scrollDelta = pendingScrollDelta.takeIf { hasPendingScrollChange },
        )
        changedLayoutNodeIds.clear()
        pendingSemanticsOwner = null
        hasPendingSemanticsChange = false
        hasPendingScrollChange = false
        pendingScrollDelta = Offset.Zero
        currentSemanticsNodesInvalidated = false
        onUpdate(update)
    }

    private fun cancelPendingFlush() {
        pendingPost?.let(removePost)
        pendingPost = null
    }

    private companion object {
        const val DefaultEventBatchIntervalMillis = 100L
    }
}

internal data class WinUIAccessibilityUpdate(
    val semanticsOwner: SemanticsOwner?,
    val semanticsChanged: Boolean,
    val layoutChangedSemanticsIds: List<Int>,
    val scrollDelta: Offset?,
)

internal data class WinUIAccessibilityBridgeState(
    val isAccessibilityForcedForTesting: Boolean,
    val accessibilityEventBatchIntervalMillis: Long,
    val currentSemanticsNodesInvalidated: Boolean,
    val hasPendingFlush: Boolean,
    val pendingSemanticsChange: Boolean,
    val pendingLayoutNodeIds: List<Int>,
    val pendingScrollDelta: Offset,
    val hasPendingScrollChange: Boolean,
)
