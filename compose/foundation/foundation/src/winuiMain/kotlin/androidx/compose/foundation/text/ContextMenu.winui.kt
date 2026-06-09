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

package androidx.compose.foundation.text

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.contextmenu.internal.ProvideDefaultPlatformTextContextMenuProviders
import androidx.compose.foundation.text.contextmenu.modifier.showTextContextMenuOnSecondaryClick
import androidx.compose.foundation.text.input.internal.selection.TextFieldSelectionState
import androidx.compose.foundation.text.input.internal.selection.addBasicTextFieldTextContextMenuComponents as addTextFieldStateContextMenuComponents
import androidx.compose.foundation.text.selection.SelectionManager
import androidx.compose.foundation.text.selection.TextFieldSelectionManager
import androidx.compose.foundation.text.selection.addBasicTextFieldTextContextMenuComponents as addTextFieldManagerContextMenuComponents
import androidx.compose.foundation.text.selection.addSelectionContainerTextContextMenuComponents
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal actual fun ContextMenuArea(
    manager: TextFieldSelectionManager,
    content: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    ProvideDefaultPlatformTextContextMenuProviders(
        Modifier
            .addTextFieldManagerContextMenuComponents(manager, coroutineScope)
            .then(manager.contextMenuAreaModifier),
        content,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal actual fun ContextMenuArea(
    selectionState: TextFieldSelectionState,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val modifier =
        if (enabled) {
            Modifier.showTextContextMenuOnSecondaryClick(
                onPreShowContextMenu = { clickLocation ->
                    selectionState.updateClipboardEntry()
                    selectionState.platformSelectionBehaviors?.onShowContextMenu(
                        text = selectionState.textFieldState.visualText.text,
                        selection = selectionState.textFieldState.visualText.selection,
                        secondaryClickLocation = clickLocation,
                    )
                }
            )
        } else {
            Modifier
        }
    ProvideDefaultPlatformTextContextMenuProviders(
        Modifier
            .addTextFieldStateContextMenuComponents(selectionState, coroutineScope)
            .then(modifier),
        content,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal actual fun ContextMenuArea(manager: SelectionManager, content: @Composable () -> Unit) {
    ProvideDefaultPlatformTextContextMenuProviders(
        Modifier
            .addSelectionContainerTextContextMenuComponents(manager)
            .then(manager.contextMenuAreaModifier),
        content,
    )
}
