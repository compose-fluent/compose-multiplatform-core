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

package androidx.compose.ui.window

import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.PlatformAbi
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import microsoft.ui.xaml.Window as XamlWindow

private const val GWLP_HWNDPARENT = -8
private const val SWP_NOSIZE = 0x0001
private const val SWP_NOMOVE = 0x0002
private const val SWP_NOACTIVATE = 0x0010
private val HwndTop = MemorySegment.ofAddress(0L)
private val IWindowNativeIid = Guid("EECDBF0E-BAE9-4CB6-A68E-9598E1CB57BB")

internal val user32Lookup: SymbolLookup = SymbolLookup.libraryLookup("user32", Arena.global())

private val setWindowLongPtr = Linker.nativeLinker().downcallHandle(
    user32Lookup.find("SetWindowLongPtrW").orElseThrow(),
    FunctionDescriptor.of(
        ValueLayout.ADDRESS,
        ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
    ),
)

private val setWindowPos = Linker.nativeLinker().downcallHandle(
    user32Lookup.find("SetWindowPos").orElseThrow(),
    FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
        ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT,
    ),
)

internal fun winuiWindowHwnd(window: XamlWindow): Long =
    window.nativeObject.queryInterface(IWindowNativeIid).getOrThrow().use { windowNative ->
        PlatformAbi.confinedScope().use { scope ->
            val hwndOut = PlatformAbi.allocatePointerSlot(scope)
            HResult(
                ComVtableInvoker.invokeArgs(
                    instance = windowNative.pointer,
                    slot = 3,
                    arg0 = hwndOut,
                ),
            ).requireSuccess("IWindowNative.WindowHandle")
            PlatformAbi.readPointer(hwndOut).value
        }
    }

internal actual fun setWindowPopupOwner(popupWindow: XamlWindow, parentWindow: XamlWindow): Boolean {
    val popupHwnd = winuiWindowHwnd(popupWindow)
    val parentHwnd = winuiWindowHwnd(parentWindow)
    if (popupHwnd == 0L || parentHwnd == 0L) return false
    return runCatching {
        setWindowLongPtr.invokeWithArguments(
            MemorySegment.ofAddress(popupHwnd),
            GWLP_HWNDPARENT,
            MemorySegment.ofAddress(parentHwnd),
        )
        (setWindowPos.invokeWithArguments(
            MemorySegment.ofAddress(popupHwnd),
            HwndTop,
            0,
            0,
            0,
            0,
            SWP_NOMOVE or SWP_NOSIZE or SWP_NOACTIVATE,
        ) as Int) != 0
    }.getOrDefault(false)
}
