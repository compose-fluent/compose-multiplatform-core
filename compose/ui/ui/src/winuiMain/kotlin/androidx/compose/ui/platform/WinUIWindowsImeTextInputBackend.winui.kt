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

import microsoft.ui.xaml.Window

internal interface WinUIWindowsImeTextInputBackend {
    fun dispose()
}

internal object WinUINoOpWindowsImeTextInputBackend : WinUIWindowsImeTextInputBackend {
    override fun dispose() = Unit
}

internal expect fun createWinUIWindowsImeTextInputBackend(
    window: Window?,
    bridge: WinUINativeTextInputBridge,
    dispatchAsync: (() -> Unit) -> Boolean,
): WinUIWindowsImeTextInputBackend

internal class WinUIWindowsImeEventDispatcher(
    private val dispatchAsync: (() -> Unit) -> Boolean,
    private val onStartComposition: () -> Unit,
    private val onComposition: (composingText: String, resultText: String) -> Boolean,
    private val onEndComposition: () -> Boolean,
    private val logFailure: (Throwable) -> Unit = {},
) {
    @Volatile
    private var isDisposed = false

    fun enqueueStartComposition(): Boolean =
        enqueue {
            onStartComposition()
        }

    fun enqueueComposition(
        composingText: String,
        resultText: String,
    ): Boolean =
        enqueue {
            onComposition(composingText, resultText)
        }

    fun enqueueEndComposition(): Boolean =
        enqueue {
            onEndComposition()
        }

    fun dispose() {
        isDisposed = true
    }

    private fun enqueue(block: () -> Unit): Boolean {
        if (isDisposed) return false
        return runCatching {
            dispatchAsync {
                if (isDisposed) return@dispatchAsync
                runCatching {
                    block()
                }.onFailure(logFailure)
            }
        }.getOrElse { throwable ->
            logFailure(throwable)
            false
        }
    }
}
