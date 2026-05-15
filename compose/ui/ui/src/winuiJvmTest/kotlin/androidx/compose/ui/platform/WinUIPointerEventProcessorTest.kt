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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WinUIPointerEventProcessorTest {
    @Test
    fun skipsAlreadyHandledNativeEvents() {
        val processor = WinUIPointerEventProcessor()
        var dispatchCount = 0

        val handled = processor.process(
            event = samplePointerEvent(),
            isHandled = true,
        ) { _, _, _, _, _, _, _, _, _, _, _, _ ->
            dispatchCount += 1
            true
        }

        assertNull(handled)
        assertEquals(0, dispatchCount)
    }

    @Test
    fun dispatchesUnhandledNativeEvents() {
        val processor = WinUIPointerEventProcessor()
        var dispatchedEvent: WinUIPointerEvent? = null
        val event = samplePointerEvent()

        val handled = processor.process(
            event = event,
            isHandled = false,
        ) { eventType, position, uptimeMillis, pointerId, down, type, buttons,
                keyboardModifiers, button, scrollDelta, isInBounds, nativeEvent ->
            dispatchedEvent = WinUIPointerEvent(
                eventType = eventType,
                position = position,
                uptimeMillis = uptimeMillis,
                pointerId = pointerId,
                down = down,
                type = type,
                buttons = buttons,
                keyboardModifiers = keyboardModifiers,
                button = button,
                scrollDelta = scrollDelta,
                isInBounds = isInBounds,
                nativeEvent = nativeEvent,
            )
            true
        }

        assertEquals(true, handled)
        assertEquals(event, dispatchedEvent)
    }
}

private fun samplePointerEvent() = WinUIPointerEvent(
    eventType = PointerEventType.Press,
    position = Offset(3f, 4f),
    uptimeMillis = 17L,
    pointerId = 23L,
    down = true,
    type = PointerType.Mouse,
    buttons = PointerButtons(isPrimaryPressed = true),
    keyboardModifiers = PointerKeyboardModifiers(isCtrlPressed = true),
    button = PointerButton.Primary,
    scrollDelta = Offset.Zero,
    isInBounds = true,
    nativeEvent = "native",
)
