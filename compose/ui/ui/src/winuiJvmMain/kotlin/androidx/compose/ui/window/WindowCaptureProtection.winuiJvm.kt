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

private const val WDA_NONE = 0x00000000
private const val WDA_EXCLUDEFROMCAPTURE = 0x00000011
private val IWindowNativeIid = Guid("EECDBF0E-BAE9-4CB6-A68E-9598E1CB57BB")

private val user32Lookup = SymbolLookup.libraryLookup("user32", Arena.global())
private val setWindowDisplayAffinity = Linker.nativeLinker().downcallHandle(
    user32Lookup.find("SetWindowDisplayAffinity").orElseThrow(),
    FunctionDescriptor.of(
        ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS,
        ValueLayout.JAVA_INT,
    ),
)

internal actual fun setWindowCaptureProtection(window: XamlWindow, isProtected: Boolean): Boolean {
    val hwnd = windowHwnd(window)
    if (hwnd == 0L) return false
    val affinity = if (isProtected) WDA_EXCLUDEFROMCAPTURE else WDA_NONE
    return (setWindowDisplayAffinity.invokeWithArguments(
        MemorySegment.ofAddress(hwnd),
        affinity,
    ) as Int) != 0
}

private fun windowHwnd(window: XamlWindow): Long =
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
