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

import io.github.composefluent.winrt.runtime.ComObjectReference
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.IWinRTObject
import io.github.composefluent.winrt.runtime.NativeAbiLayout
import io.github.composefluent.winrt.runtime.WinRTJvmFfmDowncallHandles
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import windows.foundation.Rect as WinRTRect
import windows.ui.text.core.CoreTextEditContext
import windows.ui.text.core.CoreTextLayoutBounds
import windows.ui.text.core.CoreTextRange
import windows.ui.text.core.CoreTextSelectionRequest

private val CoreTextEditContextIid = Guid("BF6608AF-4041-47C3-B263-A918EB5EAEF2")
private val CoreTextSelectionRequestIid = Guid("F0A70403-208B-4301-883C-74CA7485FD8D")
private val CoreTextLayoutBoundsIid = Guid("E972C974-4436-4917-80D0-A525E4CA6780")

private val CoreTextRangeAbiLayout = NativeAbiLayout(byteSize = 8, byteAlignment = 4)
private val WinRTRectAbiLayout = NativeAbiLayout(byteSize = 16, byteAlignment = 4)

// Raw COM vtable slots include the six IInspectable/IUnknown entries.
internal const val CoreTextNotifySelectionChangedSlotForWinUI = 35

private val CoreTextRangeSetterHandle: MethodHandle =
    WinRTJvmFfmDowncallHandles.hResult("Struct8_4")
private val CoreTextNotifySelectionChangedHandle: MethodHandle =
    WinRTJvmFfmDowncallHandles.hResult("Struct8_4")
private val WinRTRectSetterHandle: MethodHandle =
    WinRTJvmFfmDowncallHandles.hResult("Struct16_4")

internal actual fun CoreTextSelectionRequest.setSelectionByValueForWinUI(range: CoreTextRange) {
    invokeCoreTextStructSetter(
        interfaceId = CoreTextSelectionRequestIid,
        slot = 7,
        layout = CoreTextRangeAbiLayout,
        handle = CoreTextRangeSetterHandle,
        debugName = "ICoreTextSelectionRequest.Selection",
    ) { segment ->
        segment.writeCoreTextRange(range)
    }
}

internal actual fun CoreTextLayoutBounds.setTextBoundsByValueForWinUI(bounds: WinRTRect) {
    invokeCoreTextStructSetter(
        interfaceId = CoreTextLayoutBoundsIid,
        slot = 7,
        layout = WinRTRectAbiLayout,
        handle = WinRTRectSetterHandle,
        debugName = "ICoreTextLayoutBounds.TextBounds",
    ) { segment ->
        segment.writeWinRTRect(bounds)
    }
}

internal actual fun CoreTextLayoutBounds.setControlBoundsByValueForWinUI(bounds: WinRTRect) {
    invokeCoreTextStructSetter(
        interfaceId = CoreTextLayoutBoundsIid,
        slot = 9,
        layout = WinRTRectAbiLayout,
        handle = WinRTRectSetterHandle,
        debugName = "ICoreTextLayoutBounds.ControlBounds",
    ) { segment ->
        segment.writeWinRTRect(bounds)
    }
}

internal actual fun CoreTextEditContext.notifySelectionChangedByValueForWinUI(
    selection: CoreTextRange,
) {
    // TODO(KWINRT-050): Generated CoreText methods with struct-by-value
    // parameters pass native buffer pointers. Use a narrow ABI workaround until
    // kotlin-winrt lowers struct method parameters by value.
    nativeObject.queryInterface(CoreTextEditContextIid).getOrThrow().use { defaultInterface ->
        Arena.ofConfined().use { arena ->
            val selectionSegment = arena.allocate(
                CoreTextRangeAbiLayout.byteSize,
                CoreTextRangeAbiLayout.byteAlignment,
            )
            selectionSegment.writeCoreTextRange(selection)
            HResult(
                CoreTextNotifySelectionChangedHandle.invoke(
                    defaultInterface.vtableEntry(CoreTextNotifySelectionChangedSlotForWinUI),
                    defaultInterface.instanceSegment(),
                    selectionSegment,
                ) as Int
            ).requireSuccess("ICoreTextEditContext.NotifySelectionChanged")
        }
    }
}

private fun IWinRTObject.invokeCoreTextStructSetter(
    interfaceId: Guid,
    slot: Int,
    layout: NativeAbiLayout,
    handle: MethodHandle,
    debugName: String,
    writeStruct: (MemorySegment) -> Unit,
) {
    nativeObject.queryInterface(interfaceId).getOrThrow().use { defaultInterface ->
        Arena.ofConfined().use { arena ->
            val segment = arena.allocate(layout.byteSize, layout.byteAlignment)
            writeStruct(segment)
            HResult(
                handle.invoke(
                    defaultInterface.vtableEntry(slot),
                    defaultInterface.instanceSegment(),
                    segment,
                ) as Int
            ).requireSuccess(debugName)
        }
    }
}

private fun MemorySegment.writeCoreTextRange(range: CoreTextRange) {
    set(ValueLayout.JAVA_INT, 0L, range.startCaretPosition)
    set(ValueLayout.JAVA_INT, 4L, range.endCaretPosition)
}

private fun ComObjectReference.instanceSegment(): MemorySegment =
    MemorySegment.ofAddress(pointer.value).reinterpret(Long.MAX_VALUE)

private fun ComObjectReference.vtableEntry(slot: Int): MemorySegment {
    val instance = instanceSegment()
    val vtable = instance.reinterpret(ValueLayout.ADDRESS.byteSize())
        .get(ValueLayout.ADDRESS, 0L)
        .reinterpret(Long.MAX_VALUE)
    return vtable.getAtIndex(ValueLayout.ADDRESS, slot.toLong())
}

private fun MemorySegment.writeWinRTRect(bounds: WinRTRect) {
    set(ValueLayout.JAVA_FLOAT, 0L, bounds.x)
    set(ValueLayout.JAVA_FLOAT, 4L, bounds.y)
    set(ValueLayout.JAVA_FLOAT, 8L, bounds.width)
    set(ValueLayout.JAVA_FLOAT, 12L, bounds.height)
}
