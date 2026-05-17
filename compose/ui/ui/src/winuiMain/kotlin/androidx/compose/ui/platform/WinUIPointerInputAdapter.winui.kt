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
import androidx.compose.ui.node.WinUIOwner
import io.github.composefluent.winrt.runtime.EventRegistrationToken
import io.github.composefluent.winrt.runtime.WinRtEvent
import microsoft.ui.input.PointerDeviceType
import microsoft.ui.input.PointerPoint
import microsoft.ui.input.PointerUpdateKind
import microsoft.ui.xaml.UIElement
import microsoft.ui.xaml.input.PointerEventHandler
import microsoft.ui.xaml.input.PointerRoutedEventArgs

internal class WinUIPointerInputAdapter(
    private val root: UIElement,
    private val owner: WinUIOwner,
) {
    private var isDisposed = false
    private val pointerEventProcessor = WinUIPointerEventProcessor()
    private val registrations = listOf(
        register(PointerEventType.Press, root.pointerPressed),
        register(PointerEventType.Move, root.pointerMoved),
        register(PointerEventType.Release, root.pointerReleased),
        register(PointerEventType.Enter, root.pointerEntered),
        register(PointerEventType.Exit, root.pointerExited),
        register(PointerEventType.Scroll, root.pointerWheelChanged),
        registerCancel(root.pointerCanceled),
        registerCancel(root.pointerCaptureLost),
    )

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        registrations.forEach { registration ->
            runCatching { registration.event.remove(registration.token) }
        }
        owner.cancelPointerInput()
    }

    private fun register(
        eventType: PointerEventType,
        event: WinRtEvent<PointerEventHandler>,
    ): WinUIPointerEventRegistration {
        val handler = PointerEventHandler { _, args ->
            if (!isDisposed) {
                pointerEventProcessor.process(
                    event = createPointerEvent(eventType, args),
                    isHandled = args.handled,
                    sendPointerEvent = { eventType, position, uptimeMillis, pointerId, down, type,
                            buttons, keyboardModifiers, button, scrollDelta, isInBounds,
                            nativeEvent ->
                        owner.sendPointerEvent(
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
                    },
                )?.let { handled ->
                    args.handled = handled
                }
            }
        }
        return WinUIPointerEventRegistration(event, event.add(handler), handler)
    }

    private fun registerCancel(
        event: WinRtEvent<PointerEventHandler>,
    ): WinUIPointerEventRegistration {
        val handler = PointerEventHandler { _, _ ->
            if (!isDisposed) {
                owner.cancelPointerInput()
            }
        }
        return WinUIPointerEventRegistration(event, event.add(handler), handler)
    }

    private fun createPointerEvent(
        eventType: PointerEventType,
        args: PointerRoutedEventArgs,
    ): WinUIPointerEvent {
        val point = args.getCurrentPoint(root)
        val position = point.position
        val properties = checkNotNull(point.properties) {
            "WinUI pointer properties are not available."
        }
        return WinUIPointerEvent(
            eventType = eventType,
            position = Offset(position.x, position.y),
            uptimeMillis = point.timestamp.toLong() / MicrosecondsPerMillisecond,
            pointerId = point.pointerId.toLong(),
            down = eventType != PointerEventType.Exit &&
                eventType != PointerEventType.Scroll &&
                point.isInContact,
            type = point.toComposePointerType(),
            buttons = properties.toComposeButtons(),
            keyboardModifiers = args.toComposeKeyboardModifiers(),
            button = properties.pointerUpdateKind.toComposeButton(),
            scrollDelta = if (eventType == PointerEventType.Scroll) {
                properties.toComposeScrollDelta()
            } else {
                Offset.Zero
            },
            isInBounds = eventType != PointerEventType.Exit,
            nativeEvent = args,
        )
    }
}

internal class WinUIPointerEventProcessor {
    fun process(
        event: WinUIPointerEvent,
        isHandled: Boolean,
        sendPointerEvent: (
            eventType: PointerEventType,
            position: Offset,
            uptimeMillis: Long,
            pointerId: Long,
            down: Boolean,
            type: PointerType,
            buttons: PointerButtons,
            keyboardModifiers: PointerKeyboardModifiers,
            button: PointerButton?,
            scrollDelta: Offset,
            isInBounds: Boolean,
            nativeEvent: Any?,
        ) -> Boolean,
    ): Boolean? {
        if (isHandled) return null
        return sendPointerEvent(
            event.eventType,
            event.position,
            event.uptimeMillis,
            event.pointerId,
            event.down,
            event.type,
            event.buttons,
            event.keyboardModifiers,
            event.button,
            event.scrollDelta,
            event.isInBounds,
            event.nativeEvent,
        )
    }
}

internal data class WinUIPointerEvent(
    val eventType: PointerEventType,
    val position: Offset,
    val uptimeMillis: Long,
    val pointerId: Long,
    val down: Boolean,
    val type: PointerType,
    val buttons: PointerButtons,
    val keyboardModifiers: PointerKeyboardModifiers,
    val button: PointerButton?,
    val scrollDelta: Offset,
    val isInBounds: Boolean,
    val nativeEvent: Any?,
)

private data class WinUIPointerEventRegistration(
    val event: WinRtEvent<PointerEventHandler>,
    val token: EventRegistrationToken,
    val handler: PointerEventHandler,
)

private const val MicrosecondsPerMillisecond = 1_000L

private fun PointerPoint.toComposePointerType(): PointerType =
    when (pointerDeviceType) {
        PointerDeviceType.Mouse -> PointerType.Mouse
        PointerDeviceType.Pen -> PointerType.Stylus
        PointerDeviceType.Touch,
        PointerDeviceType.Touchpad -> PointerType.Touch
    }

private fun microsoft.ui.input.PointerPointProperties.toComposeButtons(): PointerButtons {
    return PointerButtons(
        isPrimaryPressed = isLeftButtonPressed,
        isSecondaryPressed = isRightButtonPressed,
        isTertiaryPressed = isMiddleButtonPressed,
        isBackPressed = isXButton1Pressed,
        isForwardPressed = isXButton2Pressed,
    )
}

private fun PointerUpdateKind.toComposeButton(): PointerButton? =
    when (this) {
        PointerUpdateKind.LeftButtonPressed,
        PointerUpdateKind.LeftButtonReleased -> PointerButton.Primary
        PointerUpdateKind.RightButtonPressed,
        PointerUpdateKind.RightButtonReleased -> PointerButton.Secondary
        PointerUpdateKind.MiddleButtonPressed,
        PointerUpdateKind.MiddleButtonReleased -> PointerButton.Tertiary
        PointerUpdateKind.XButton1Pressed,
        PointerUpdateKind.XButton1Released -> PointerButton.Back
        PointerUpdateKind.XButton2Pressed,
        PointerUpdateKind.XButton2Released -> PointerButton.Forward
        PointerUpdateKind.Other -> null
    }

private fun microsoft.ui.input.PointerPointProperties.toComposeScrollDelta(): Offset {
    val wheelTicks = mouseWheelDelta.toFloat() / MouseWheelDeltaPerTick
    return if (isHorizontalMouseWheel) {
        Offset(wheelTicks, 0f)
    } else {
        Offset(0f, -wheelTicks)
    }
}

private const val MouseWheelDeltaPerTick = 120f

private fun PointerRoutedEventArgs.toComposeKeyboardModifiers(): PointerKeyboardModifiers =
    runCatching { winUIPointerKeyboardModifiersFromWinUI(keyModifiers) }
        .getOrDefault(PointerKeyboardModifiers())
