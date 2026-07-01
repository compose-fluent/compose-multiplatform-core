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
import androidx.compose.ui.SessionMutex
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@OptIn(ExperimentalComposeUiApi::class)
internal class WinUIPlatformTextInputSession(
    coroutineScope: CoroutineScope,
) : PlatformTextInputSessionScope, CoroutineScope by coroutineScope {
    private val inputMethodSessionMutex = SessionMutex<PlatformTextInputMethodRequest>()

    override suspend fun startInputMethod(request: PlatformTextInputMethodRequest): Nothing =
        inputMethodSessionMutex.withSessionCancellingPrevious(
            sessionInitializer = { request },
        ) { activeRequest ->
            coroutineScope {
                launch {
                    snapshotFlow { activeRequest.value() }.collect { value ->
                        WinUIPlatformTextInputService.updateInputMethodState(activeRequest, value)
                    }
                }
                launch {
                    snapshotFlow {
                        WinUIPlatformTextInputService.requestTextLayoutBoundsInRoot(activeRequest)
                    }.collect { bounds ->
                        WinUIPlatformTextInputService.updateInputMethodLayout(activeRequest, bounds)
                    }
                }
                @Suppress("RemoveExplicitTypeArguments")
                suspendCancellableCoroutine<Nothing> { continuation ->
                    WinUIPlatformTextInputService.startInputMethod(activeRequest)
                    continuation.invokeOnCancellation {
                        WinUIPlatformTextInputService.stopInputMethod(activeRequest)
                    }
                }
            }
        }
}
