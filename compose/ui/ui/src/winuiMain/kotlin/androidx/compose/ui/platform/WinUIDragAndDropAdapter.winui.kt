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

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.WinUIDragAndDropManager
import androidx.compose.ui.geometry.Offset
import io.github.composefluent.winrt.runtime.EventRegistrationToken
import io.github.composefluent.winrt.runtime.WinRtEvent
import microsoft.ui.xaml.DragEventArgs
import microsoft.ui.xaml.DragEventHandler
import microsoft.ui.xaml.UIElement

internal class WinUIDragAndDropAdapter(
    private val root: UIElement,
    private val dragAndDropManager: WinUIDragAndDropManager,
) {
    private var isDisposed = false
    private var isDragSessionActive = false
    private val registrations = listOf(
        register(root.dragEnter, ::handleDragEnter),
        register(root.dragOver, ::handleDragOver),
        register(root.dragLeave, ::handleDragLeave),
        register(root.drop, ::handleDrop),
    )

    init {
        root.allowDrop = true
    }

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        registrations.forEach { registration ->
            runCatching { registration.event.remove(registration.token) }
        }
        if (isDragSessionActive) {
            dragAndDropManager.onDragEnded(DragAndDropEvent())
            isDragSessionActive = false
        }
        root.allowDrop = false
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun handleDragEnter(args: DragEventArgs): Boolean {
        val event = args.toComposeDragAndDropEvent()
        val accepted = ensureDragSessionStarted(event)
        dragAndDropManager.onDragEntered(event)
        return accepted
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun handleDragOver(args: DragEventArgs): Boolean {
        val event = args.toComposeDragAndDropEvent()
        val accepted = ensureDragSessionStarted(event)
        dragAndDropManager.onDragMoved(event)
        return accepted
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun handleDragLeave(args: DragEventArgs): Boolean {
        val event = args.toComposeDragAndDropEvent()
        if (isDragSessionActive) {
            dragAndDropManager.onDragExited(event)
            dragAndDropManager.onDragEnded(event)
            isDragSessionActive = false
        }
        return false
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun handleDrop(args: DragEventArgs): Boolean {
        val event = args.toComposeDragAndDropEvent()
        ensureDragSessionStarted(event)
        val handled = dragAndDropManager.onDrop(event)
        dragAndDropManager.onDragEnded(event)
        isDragSessionActive = false
        return handled
    }

    private fun ensureDragSessionStarted(event: DragAndDropEvent): Boolean {
        if (isDragSessionActive) return true
        isDragSessionActive = dragAndDropManager.onDragStarted(event)
        return isDragSessionActive
    }

    private fun register(
        event: WinRtEvent<DragEventHandler>,
        dispatch: (DragEventArgs) -> Boolean,
    ): WinUIDragAndDropEventRegistration {
        val handler = DragEventHandler { _, args ->
            if (!isDisposed && !args.handled) {
                args.handled = dispatch(args)
            }
        }
        return WinUIDragAndDropEventRegistration(event, event.add(handler), handler)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun DragEventArgs.toComposeDragAndDropEvent(): DragAndDropEvent {
        val position = getPosition(root)
        return DragAndDropEvent(
            nativeEvent = this,
            positionInRootImpl = Offset(position.x, position.y),
        )
    }
}

private data class WinUIDragAndDropEventRegistration(
    val event: WinRtEvent<DragEventHandler>,
    val token: EventRegistrationToken,
    val handler: DragEventHandler,
)
