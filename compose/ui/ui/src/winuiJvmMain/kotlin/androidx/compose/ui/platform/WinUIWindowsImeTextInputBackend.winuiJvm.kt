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

import androidx.compose.ui.window.user32Lookup
import androidx.compose.ui.window.winuiWindowHwnd
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import microsoft.ui.xaml.Window

private const val GWLP_WNDPROC = -4
private const val WM_IME_STARTCOMPOSITION = 0x010D
private const val WM_IME_ENDCOMPOSITION = 0x010E
private const val WM_IME_COMPOSITION = 0x010F
private const val GCS_COMPSTR = 0x0008
private const val GCS_RESULTSTR = 0x0800

private val nativeLinker = Linker.nativeLinker()
private val imm32Lookup: SymbolLookup = SymbolLookup.libraryLookup("imm32", Arena.global())

private val setWindowLongPtrW = nativeLinker.downcallHandle(
    user32Lookup.find("SetWindowLongPtrW").orElseThrow(),
    FunctionDescriptor.of(
        ValueLayout.ADDRESS,
        ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
    ),
)

private val callWindowProcW = nativeLinker.downcallHandle(
    user32Lookup.find("CallWindowProcW").orElseThrow(),
    FunctionDescriptor.of(
        ValueLayout.JAVA_LONG,
        ValueLayout.ADDRESS,
        ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_LONG,
    ),
)

private val immGetContext = nativeLinker.downcallHandle(
    imm32Lookup.find("ImmGetContext").orElseThrow(),
    FunctionDescriptor.of(
        ValueLayout.ADDRESS,
        ValueLayout.ADDRESS,
    ),
)

private val immReleaseContext = nativeLinker.downcallHandle(
    imm32Lookup.find("ImmReleaseContext").orElseThrow(),
    FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
        ValueLayout.ADDRESS,
    ),
)

private val immGetCompositionStringW = nativeLinker.downcallHandle(
    imm32Lookup.find("ImmGetCompositionStringW").orElseThrow(),
    FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT,
    ),
)

private val wndProcDescriptor = FunctionDescriptor.of(
    ValueLayout.JAVA_LONG,
    ValueLayout.ADDRESS,
    ValueLayout.JAVA_INT,
    ValueLayout.JAVA_LONG,
    ValueLayout.JAVA_LONG,
)

internal actual fun createWinUIWindowsImeTextInputBackend(
    window: Window?,
    bridge: WinUINativeTextInputBridge,
    dispatchAsync: (() -> Unit) -> Boolean,
): WinUIWindowsImeTextInputBackend {
    if (!winUISystemBooleanProperty(WindowsImeBackendEnabledProperty)) {
        debugWindowsImeInput {
            "WndProc IME backend skipped: not enabled by system property " +
                WindowsImeBackendEnabledProperty
        }
        return WinUINoOpWindowsImeTextInputBackend
    }
    if (window == null) return WinUINoOpWindowsImeTextInputBackend
    val hwnd = runCatching { winuiWindowHwnd(window) }
        .onFailure { throwable ->
            debugWindowsImeInput {
                "HWND lookup failed: ${throwable.stackTraceToString()}"
            }
        }
        .getOrDefault(0L)
    if (hwnd == 0L) return WinUINoOpWindowsImeTextInputBackend

    return WinUIJvmWindowsImeTextInputBackend.create(
        hwnd = hwnd,
        bridge = bridge,
        dispatchAsync = dispatchAsync,
    )
}

private class WinUIJvmWindowsImeTextInputBackend private constructor(
    private val hwnd: Long,
    bridge: WinUINativeTextInputBridge,
    dispatchAsync: (() -> Unit) -> Boolean,
    private val arena: Arena,
) : WinUIWindowsImeTextInputBackend {
    private val hwndSegment = MemorySegment.ofAddress(hwnd)
    private val eventDispatcher = WinUIWindowsImeEventDispatcher(
        dispatchAsync = dispatchAsync,
        onStartComposition = bridge::onWindowsImeStartComposition,
        onComposition = bridge::onWindowsImeComposition,
        onEndComposition = bridge::onWindowsImeEndComposition,
        logFailure = { throwable ->
            debugWindowsImeInput {
                "dispatched IME handling failed hwnd=0x${hwnd.toString(16)}: " +
                    throwable.stackTraceToString()
            }
        },
    )
    private var oldWndProc: MemorySegment = MemorySegment.NULL
    private var wndProcStub: MemorySegment = MemorySegment.NULL
    private var isDisposed = false

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        eventDispatcher.dispose()
        if (oldWndProc != MemorySegment.NULL) {
            runCatching {
                setWindowLongPtrW.invokeWithArguments(
                    hwndSegment,
                    GWLP_WNDPROC,
                    oldWndProc,
                )
            }.onFailure { throwable ->
                debugWindowsImeInput {
                    "restore WndProc failed hwnd=0x${hwnd.toString(16)}: " +
                        throwable.stackTraceToString()
                }
            }
            oldWndProc = MemorySegment.NULL
        }
        runCatching { arena.close() }
    }

    @Suppress("unused")
    private fun wndProc(
        messageHwnd: MemorySegment,
        message: Int,
        wParam: Long,
        lParam: Long,
    ): Long {
        val event = runCatching {
            when (message) {
                WM_IME_STARTCOMPOSITION -> WinUIWindowsImeEvent.StartComposition
                WM_IME_COMPOSITION -> captureImeComposition(messageHwnd, lParam)
                WM_IME_ENDCOMPOSITION -> WinUIWindowsImeEvent.EndComposition
                else -> null
            }
        }.onFailure { throwable ->
            debugWindowsImeInput {
                "WndProc IME capture failed message=0x${message.toString(16)} " +
                    "hwnd=0x${hwnd.toString(16)}: ${throwable.stackTraceToString()}"
            }
        }.getOrNull()

        val result = callOldWindowProc(messageHwnd, message, wParam, lParam)
        if (event != null) {
            dispatchImeEvent(event)
        }
        return result
    }

    private fun captureImeComposition(
        messageHwnd: MemorySegment,
        lParam: Long,
    ): WinUIWindowsImeEvent.Composition? {
        val flags = lParam.toInt()
        val resultText = if ((flags and GCS_RESULTSTR) != 0) {
            readImeCompositionString(messageHwnd, GCS_RESULTSTR)
        } else {
            ""
        }
        val composingText = if ((flags and GCS_COMPSTR) != 0) {
            readImeCompositionString(messageHwnd, GCS_COMPSTR)
        } else {
            ""
        }
        return if (resultText.isNotEmpty() || composingText.isNotEmpty()) {
            WinUIWindowsImeEvent.Composition(
                composingText = composingText,
                resultText = resultText,
            )
        } else {
            null
        }
    }

    private fun dispatchImeEvent(event: WinUIWindowsImeEvent) {
        val delivered = when (event) {
            WinUIWindowsImeEvent.StartComposition ->
                eventDispatcher.enqueueStartComposition()
            is WinUIWindowsImeEvent.Composition ->
                eventDispatcher.enqueueComposition(
                    composingText = event.composingText,
                    resultText = event.resultText,
                )
            WinUIWindowsImeEvent.EndComposition ->
                eventDispatcher.enqueueEndComposition()
        }
        if (!delivered) {
            debugWindowsImeInput {
                "WndProc IME event dropped after capture event=$event " +
                    "hwnd=0x${hwnd.toString(16)}"
            }
        }
    }

    private fun readImeCompositionString(
        messageHwnd: MemorySegment,
        index: Int,
    ): String {
        val inputContext = immGetContext.invokeWithArguments(messageHwnd) as MemorySegment
        if (inputContext == MemorySegment.NULL) return ""
        return try {
            val byteCount = immGetCompositionStringW.invokeWithArguments(
                inputContext,
                index,
                MemorySegment.NULL,
                0,
            ) as Int
            if (byteCount <= 0) {
                ""
            } else {
                Arena.ofConfined().use { scope ->
                    val buffer = scope.allocate(byteCount.toLong())
                    val copiedByteCount = immGetCompositionStringW.invokeWithArguments(
                        inputContext,
                        index,
                        buffer,
                        byteCount,
                    ) as Int
                    if (copiedByteCount <= 0) {
                        ""
                    } else {
                        val bytes = ByteArray(copiedByteCount)
                        for (indexInBuffer in 0 until copiedByteCount) {
                            bytes[indexInBuffer] = buffer.get(
                                ValueLayout.JAVA_BYTE,
                                indexInBuffer.toLong(),
                            )
                        }
                        String(bytes, Charsets.UTF_16LE)
                    }
                }
            }
        } finally {
            runCatching {
                immReleaseContext.invokeWithArguments(messageHwnd, inputContext)
            }
        }
    }

    private fun callOldWindowProc(
        messageHwnd: MemorySegment,
        message: Int,
        wParam: Long,
        lParam: Long,
    ): Long {
        val oldProc = oldWndProc
        if (oldProc == MemorySegment.NULL) return 0L
        return callWindowProcW.invokeWithArguments(
            oldProc,
            messageHwnd,
            message,
            wParam,
            lParam,
        ) as Long
    }

    private fun install(): Boolean {
        wndProcStub = nativeLinker.upcallStub(
            wndProcHandle().bindTo(this),
            wndProcDescriptor,
            arena,
        )
        val previousWndProc = setWindowLongPtrW.invokeWithArguments(
            hwndSegment,
            GWLP_WNDPROC,
            wndProcStub,
        ) as MemorySegment
        if (previousWndProc == MemorySegment.NULL) {
            debugWindowsImeInput {
                "WndProc install failed hwnd=0x${hwnd.toString(16)}"
            }
            return false
        }
        oldWndProc = previousWndProc
        debugWindowsImeInput {
            "WndProc installed hwnd=0x${hwnd.toString(16)}"
        }
        return true
    }

    companion object {
        fun create(
            hwnd: Long,
            bridge: WinUINativeTextInputBridge,
            dispatchAsync: (() -> Unit) -> Boolean,
        ): WinUIWindowsImeTextInputBackend {
            val backend = WinUIJvmWindowsImeTextInputBackend(
                hwnd = hwnd,
                bridge = bridge,
                dispatchAsync = dispatchAsync,
                arena = Arena.ofShared(),
            )
            return if (runCatching { backend.install() }.getOrDefault(false)) {
                backend
            } else {
                backend.dispose()
                WinUINoOpWindowsImeTextInputBackend
            }
        }

        private fun wndProcHandle() =
            MethodHandles.lookup().unreflect(
                WinUIJvmWindowsImeTextInputBackend::class.java.getDeclaredMethod(
                "wndProc",
                    MemorySegment::class.java,
                    Int::class.javaPrimitiveType,
                    Long::class.javaPrimitiveType,
                    Long::class.javaPrimitiveType,
                ).also { method ->
                    method.isAccessible = true
                }
            )
    }
}

private sealed interface WinUIWindowsImeEvent {
    data object StartComposition : WinUIWindowsImeEvent
    data class Composition(
        val composingText: String,
        val resultText: String,
    ) : WinUIWindowsImeEvent
    data object EndComposition : WinUIWindowsImeEvent
}

private inline fun debugWindowsImeInput(message: () -> String) {
    if (winUISystemBooleanProperty("compose.winui.textInput.debug")) {
        winUIDebugLog("windows-ime", message())
    }
}

private const val WindowsImeBackendEnabledProperty =
    "compose.winui.textInput.windowsIme.enabled"
