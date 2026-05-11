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

package androidx.compose.ui.draganddrop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset

actual class DragAndDropTransferData @ExperimentalComposeUiApi constructor(
    val nativeTransferData: Any? = null,
)

actual class DragAndDropEvent @ExperimentalComposeUiApi constructor(
    val nativeEvent: Any? = null,
    internal val positionInRootImpl: Offset = Offset.Zero,
)

internal actual val DragAndDropEvent.positionInRoot: Offset
    get() = positionInRootImpl
