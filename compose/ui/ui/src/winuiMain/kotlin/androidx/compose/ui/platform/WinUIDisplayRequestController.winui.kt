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

import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.HResult

internal class WinUIDisplayRequestController {
    private var displayRequest: windows.system.display.DisplayRequest? = null
    private var isActive = false

    fun setKeepScreenOn(enabled: Boolean) {
        if (enabled == isActive) return
        if (enabled) {
            val request = displayRequest ?: windows.system.display.DisplayRequest().also {
                displayRequest = it
            }
            request.invokeDisplayRequestSlot(
                windows.system.display.IDisplayRequest.Metadata.REQUESTACTIVE_SLOT
            )
            isActive = true
        } else {
            val request = displayRequest ?: return
            request.invokeDisplayRequestSlot(
                windows.system.display.IDisplayRequest.Metadata.REQUESTRELEASE_SLOT
            )
            isActive = false
        }
    }

    private fun windows.system.display.DisplayRequest.invokeDisplayRequestSlot(slot: Int) {
        // KWINRT-020: the generated IDisplayRequest projection factory is not registered.
        nativeObject.queryInterface(windows.system.display.IDisplayRequest.Metadata.IID)
            .getOrThrow()
            .use { displayRequest ->
                HResult(
                    ComVtableInvoker.invoke(
                        instance = displayRequest.pointer,
                        slot = slot,
                    ),
                ).requireSuccess("DisplayRequest")
            }
    }
}
