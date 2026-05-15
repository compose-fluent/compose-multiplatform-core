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
import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.EventRegistrationToken
import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.PlatformAbi
import io.github.composefluent.winrt.runtime.WinRtDelegateHandle
import microsoft.ui.input.PointerDeviceType
import microsoft.ui.input.PointerPoint
import microsoft.ui.input.PointerUpdateKind
import microsoft.ui.xaml.IUIElement
import microsoft.ui.xaml.UIElement
import microsoft.ui.xaml.input.PointerEventHandler
import microsoft.ui.xaml.input.PointerRoutedEventArgs
import windows.system.VirtualKeyModifiers

internal class WinUIPointerInputAdapter(
    private val root: UIElement,
    private val owner: WinUIOwner,
) {
    private var isDisposed = false
    private val registrations = listOf(
        register(PointerEventType.Press, IUIElement.Metadata.POINTERPRESSED_ADD_SLOT) {
            IUIElement.Metadata.POINTERPRESSED_REMOVE_SLOT
        },
        register(PointerEventType.Move, IUIElement.Metadata.POINTERMOVED_ADD_SLOT) {
            IUIElement.Metadata.POINTERMOVED_REMOVE_SLOT
        },
        register(PointerEventType.Release, IUIElement.Metadata.POINTERRELEASED_ADD_SLOT) {
            IUIElement.Metadata.POINTERRELEASED_REMOVE_SLOT
        },
        register(PointerEventType.Enter, IUIElement.Metadata.POINTERENTERED_ADD_SLOT) {
            IUIElement.Metadata.POINTERENTERED_REMOVE_SLOT
        },
        register(PointerEventType.Exit, IUIElement.Metadata.POINTEREXITED_ADD_SLOT) {
            IUIElement.Metadata.POINTEREXITED_REMOVE_SLOT
        },
        register(PointerEventType.Scroll, IUIElement.Metadata.POINTERWHEELCHANGED_ADD_SLOT) {
            IUIElement.Metadata.POINTERWHEELCHANGED_REMOVE_SLOT
        },
        registerCancel(IUIElement.Metadata.POINTERCANCELED_ADD_SLOT) {
            IUIElement.Metadata.POINTERCANCELED_REMOVE_SLOT
        },
        registerCancel(IUIElement.Metadata.POINTERCAPTURELOST_ADD_SLOT) {
            IUIElement.Metadata.POINTERCAPTURELOST_REMOVE_SLOT
        },
    )

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        registrations.forEach { registration ->
            runCatching {
                removePointerHandler(root, registration.removeSlot, registration.token)
            }
            registration.delegate.close()
        }
        owner.cancelPointerInput()
    }

    private fun register(
        eventType: PointerEventType,
        addSlot: Int,
        removeSlot: () -> Int,
    ): WinUIPointerEventRegistration {
        val delegate = PointerEventHandler { _, args ->
            if (!isDisposed) {
                args.handled = dispatchPointerEvent(eventType, args)
            }
        }.createWinRtDelegateHandle()
        return registerDelegate(addSlot, removeSlot(), delegate)
    }

    private fun registerCancel(
        addSlot: Int,
        removeSlot: () -> Int,
    ): WinUIPointerEventRegistration {
        val delegate = PointerEventHandler { _, _ ->
            if (!isDisposed) {
                owner.cancelPointerInput()
            }
        }.createWinRtDelegateHandle()
        return registerDelegate(addSlot, removeSlot(), delegate)
    }

    private fun registerDelegate(
        addSlot: Int,
        removeSlot: Int,
        delegate: WinRtDelegateHandle,
    ): WinUIPointerEventRegistration {
        val token = try {
            addPointerHandler(root, addSlot, delegate)
        } catch (throwable: Throwable) {
            delegate.close()
            throw throwable
        }
        return WinUIPointerEventRegistration(removeSlot, token, delegate)
    }

    private fun dispatchPointerEvent(
        eventType: PointerEventType,
        args: PointerRoutedEventArgs,
    ): Boolean {
        val point = args.getCurrentPoint(root)
        val position = point.position
        return owner.sendPointerEvent(
            eventType = eventType,
            position = Offset(position.x, position.y),
            uptimeMillis = point.timestamp.toLong() / MicrosecondsPerMillisecond,
            pointerId = point.pointerId.toLong(),
            down = eventType != PointerEventType.Exit &&
                eventType != PointerEventType.Scroll &&
                point.isInContact,
            type = point.toComposePointerType(),
            buttons = point.toComposeButtons(),
            keyboardModifiers = args.toComposeKeyboardModifiers(),
            button = point.properties.pointerUpdateKind.toComposeButton(),
            scrollDelta = if (eventType == PointerEventType.Scroll) {
                point.properties.toComposeScrollDelta()
            } else {
                Offset.Zero
            },
            isInBounds = eventType != PointerEventType.Exit,
            nativeEvent = args,
        )
    }
}

private data class WinUIPointerEventRegistration(
    val removeSlot: Int,
    val token: EventRegistrationToken,
    val delegate: WinRtDelegateHandle,
)

private const val MicrosecondsPerMillisecond = 1_000L

private fun PointerPoint.toComposePointerType(): PointerType =
    when (pointerDeviceType) {
        PointerDeviceType.Mouse -> PointerType.Mouse
        PointerDeviceType.Pen -> PointerType.Stylus
        PointerDeviceType.Touch,
        PointerDeviceType.Touchpad -> PointerType.Touch
    }

private fun PointerPoint.toComposeButtons(): PointerButtons {
    val properties = properties
    return PointerButtons(
        isPrimaryPressed = properties.isLeftButtonPressed,
        isSecondaryPressed = properties.isRightButtonPressed,
        isTertiaryPressed = properties.isMiddleButtonPressed,
        isBackPressed = properties.isXButton1Pressed,
        isForwardPressed = properties.isXButton2Pressed,
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
    runCatching {
        val modifier = keyModifiers
        PointerKeyboardModifiers(
            isCtrlPressed = modifier == VirtualKeyModifiers.Control,
            isMetaPressed = modifier == VirtualKeyModifiers.Windows,
            isAltPressed = modifier == VirtualKeyModifiers.Menu,
            isShiftPressed = modifier == VirtualKeyModifiers.Shift,
        )
    }.getOrDefault(PointerKeyboardModifiers())

private fun addPointerHandler(
    element: UIElement,
    slot: Int,
    delegate: WinRtDelegateHandle,
): EventRegistrationToken =
    // KWINRT-001: use direct IUIElement ABI registration until generated event sources are stable.
    element.nativeObject.queryInterface(IUIElement.Metadata.IID).getOrThrow().use { elementInterface ->
        delegate.createReference().use { delegateReference ->
            PlatformAbi.confinedScope().use { scope ->
                val tokenOut = PlatformAbi.allocateBytes(scope, EventRegistrationToken.BYTE_SIZE.toLong())
                HResult(
                    ComVtableInvoker.invokeArgs(
                        instance = elementInterface.pointer,
                        slot = slot,
                        arg0 = PlatformAbi.fromRawComPtr(delegateReference.pointer),
                        arg1 = tokenOut,
                    ),
                ).requireSuccess("UIElement pointer add handler")
                EventRegistrationToken.fromAbi(tokenOut)
            }
        }
    }

private fun removePointerHandler(
    element: UIElement,
    slot: Int,
    token: EventRegistrationToken,
) {
    // KWINRT-001: pair with the manual UIElement pointer registration above.
    element.nativeObject.queryInterface(IUIElement.Metadata.IID).getOrThrow().use { elementInterface ->
        PlatformAbi.confinedScope().use { scope ->
            val tokenAbi = PlatformAbi.allocateBytes(scope, EventRegistrationToken.BYTE_SIZE.toLong())
            EventRegistrationToken.copyTo(token, tokenAbi)
            HResult(
                ComVtableInvoker.invokeArgs(
                    instance = elementInterface.pointer,
                    slot = slot,
                    arg0 = tokenAbi,
                ),
            ).requireSuccess("UIElement pointer remove handler")
        }
    }
}
