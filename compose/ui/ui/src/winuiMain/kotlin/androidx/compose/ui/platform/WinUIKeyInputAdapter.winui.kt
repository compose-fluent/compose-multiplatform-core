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

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.node.WinUIOwner
import io.github.composefluent.winrt.runtime.EventRegistrationToken
import io.github.composefluent.winrt.runtime.WinRTEvent
import microsoft.ui.xaml.UIElement
import microsoft.ui.xaml.input.KeyEventHandler
import windows.system.VirtualKey

internal class WinUIKeyInputAdapter(
    private val root: UIElement,
    private val owner: WinUIOwner,
) {
    private var isDisposed = false
    private val keyEventProcessor = WinUIKeyEventProcessor()
    private val registrations = listOf(
        register(KeyEventType.KeyDown, root.keyDown),
        register(KeyEventType.KeyUp, root.keyUp),
    )

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        registrations.forEach { registration ->
            runCatching { registration.event.remove(registration.token) }
        }
    }

    @OptIn(InternalComposeUiApi::class)
    private fun register(
        eventType: KeyEventType,
        event: WinRTEvent<KeyEventHandler>,
    ): WinUIKeyEventRegistration {
        val handler = KeyEventHandler { _, args ->
            if (!isDisposed) {
                keyEventProcessor.process(
                    eventType = eventType,
                    key = args.key,
                    isHandled = args.handled,
                    nativeEvent = args,
                    shouldDispatchEvent = { args.originalSource.isComposeRootSource(root) },
                    sendKeyEvent = owner::sendKeyEvent,
                )?.let { handled ->
                    args.handled = handled
                }
            }
        }
        return WinUIKeyEventRegistration(event, event.add(handler), handler)
    }
}

internal class WinUIKeyEventProcessor {
    private val modifierState = WinUIKeyModifierState()

    @OptIn(InternalComposeUiApi::class)
    fun process(
        eventType: KeyEventType,
        key: VirtualKey,
        isHandled: Boolean,
        nativeEvent: Any?,
        shouldDispatchEvent: () -> Boolean = { true },
        sendKeyEvent: (KeyEvent) -> Boolean,
    ): Boolean? {
        if (isHandled) return null
        if (!shouldDispatchEvent()) return null
        if (eventType == KeyEventType.KeyDown) {
            modifierState.update(key, isPressed = true)
        }
        val handled = sendKeyEvent(key.toComposeKeyEvent(eventType, modifierState, nativeEvent))
        if (eventType == KeyEventType.KeyUp) {
            modifierState.update(key, isPressed = false)
        }
        return handled
    }
}

private fun Any?.isComposeRootSource(root: UIElement): Boolean =
    this == null || this == root

private data class WinUIKeyEventRegistration(
    val event: WinRTEvent<KeyEventHandler>,
    val token: EventRegistrationToken,
    val handler: KeyEventHandler,
)

private class WinUIKeyModifierState {
    var isCtrlPressed = false
        private set
    var isMetaPressed = false
        private set
    var isAltPressed = false
        private set
    var isShiftPressed = false
        private set

    fun update(key: VirtualKey, isPressed: Boolean) {
        when (key) {
            VirtualKey.Control,
            VirtualKey.LeftControl,
            VirtualKey.RightControl -> isCtrlPressed = isPressed
            VirtualKey.LeftWindows,
            VirtualKey.RightWindows -> isMetaPressed = isPressed
            VirtualKey.Menu,
            VirtualKey.LeftMenu,
            VirtualKey.RightMenu -> isAltPressed = isPressed
            VirtualKey.Shift,
            VirtualKey.LeftShift,
            VirtualKey.RightShift -> isShiftPressed = isPressed
            else -> Unit
        }
    }
}

@OptIn(InternalComposeUiApi::class)
private fun VirtualKey.toComposeKeyEvent(
    eventType: KeyEventType,
    modifierState: WinUIKeyModifierState,
    nativeEvent: Any?,
) = KeyEvent(
    key = toComposeKey(),
    type = eventType,
    codePoint = toUtf16CodePoint(),
    isCtrlPressed = modifierState.isCtrlPressed,
    isMetaPressed = modifierState.isMetaPressed,
    isAltPressed = modifierState.isAltPressed,
    isShiftPressed = modifierState.isShiftPressed,
    nativeEvent = nativeEvent,
)

private fun VirtualKey.toComposeKey(): Key =
    when (this) {
        VirtualKey.Back -> Key.Backspace
        VirtualKey.Tab -> Key.Tab
        VirtualKey.Clear -> Key.Clear
        VirtualKey.Enter -> Key.Enter
        VirtualKey.Shift,
        VirtualKey.LeftShift -> Key.ShiftLeft
        VirtualKey.RightShift -> Key.ShiftRight
        VirtualKey.Control,
        VirtualKey.LeftControl -> Key.CtrlLeft
        VirtualKey.RightControl -> Key.CtrlRight
        VirtualKey.Menu,
        VirtualKey.LeftMenu -> Key.AltLeft
        VirtualKey.RightMenu -> Key.AltRight
        VirtualKey.Pause -> Key.Break
        VirtualKey.CapitalLock -> Key.CapsLock
        VirtualKey.Escape -> Key.Escape
        VirtualKey.Space -> Key.Spacebar
        VirtualKey.PageUp -> Key.PageUp
        VirtualKey.PageDown -> Key.PageDown
        VirtualKey.End -> Key.MoveEnd
        VirtualKey.Home -> Key.MoveHome
        VirtualKey.Left -> Key.DirectionLeft
        VirtualKey.Up -> Key.DirectionUp
        VirtualKey.Right -> Key.DirectionRight
        VirtualKey.Down -> Key.DirectionDown
        VirtualKey.Snapshot -> Key.PrintScreen
        VirtualKey.Insert -> Key.Insert
        VirtualKey.Delete -> Key.Delete
        VirtualKey.Help -> Key.Help
        VirtualKey.Number0 -> Key.Zero
        VirtualKey.Number1 -> Key.One
        VirtualKey.Number2 -> Key.Two
        VirtualKey.Number3 -> Key.Three
        VirtualKey.Number4 -> Key.Four
        VirtualKey.Number5 -> Key.Five
        VirtualKey.Number6 -> Key.Six
        VirtualKey.Number7 -> Key.Seven
        VirtualKey.Number8 -> Key.Eight
        VirtualKey.Number9 -> Key.Nine
        VirtualKey.A -> Key.A
        VirtualKey.B -> Key.B
        VirtualKey.C -> Key.C
        VirtualKey.D -> Key.D
        VirtualKey.E -> Key.E
        VirtualKey.F -> Key.F
        VirtualKey.G -> Key.G
        VirtualKey.H -> Key.H
        VirtualKey.I -> Key.I
        VirtualKey.J -> Key.J
        VirtualKey.K -> Key.K
        VirtualKey.L -> Key.L
        VirtualKey.M -> Key.M
        VirtualKey.N -> Key.N
        VirtualKey.O -> Key.O
        VirtualKey.P -> Key.P
        VirtualKey.Q -> Key.Q
        VirtualKey.R -> Key.R
        VirtualKey.S -> Key.S
        VirtualKey.T -> Key.T
        VirtualKey.U -> Key.U
        VirtualKey.V -> Key.V
        VirtualKey.W -> Key.W
        VirtualKey.X -> Key.X
        VirtualKey.Y -> Key.Y
        VirtualKey.Z -> Key.Z
        VirtualKey.LeftWindows -> Key.MetaLeft
        VirtualKey.RightWindows -> Key.MetaRight
        VirtualKey.Application -> Key.Menu
        VirtualKey.NumberPad0 -> Key.NumPad0
        VirtualKey.NumberPad1 -> Key.NumPad1
        VirtualKey.NumberPad2 -> Key.NumPad2
        VirtualKey.NumberPad3 -> Key.NumPad3
        VirtualKey.NumberPad4 -> Key.NumPad4
        VirtualKey.NumberPad5 -> Key.NumPad5
        VirtualKey.NumberPad6 -> Key.NumPad6
        VirtualKey.NumberPad7 -> Key.NumPad7
        VirtualKey.NumberPad8 -> Key.NumPad8
        VirtualKey.NumberPad9 -> Key.NumPad9
        VirtualKey.Multiply -> Key.NumPadMultiply
        VirtualKey.Add -> Key.NumPadAdd
        VirtualKey.Separator -> Key.NumPadComma
        VirtualKey.Subtract -> Key.NumPadSubtract
        VirtualKey.Decimal -> Key.NumPadDot
        VirtualKey.Divide -> Key.NumPadDivide
        VirtualKey.F1 -> Key.F1
        VirtualKey.F2 -> Key.F2
        VirtualKey.F3 -> Key.F3
        VirtualKey.F4 -> Key.F4
        VirtualKey.F5 -> Key.F5
        VirtualKey.F6 -> Key.F6
        VirtualKey.F7 -> Key.F7
        VirtualKey.F8 -> Key.F8
        VirtualKey.F9 -> Key.F9
        VirtualKey.F10 -> Key.F10
        VirtualKey.F11 -> Key.F11
        VirtualKey.F12 -> Key.F12
        VirtualKey.NumberKeyLock -> Key.NumLock
        VirtualKey.Scroll -> Key.ScrollLock
        VirtualKey.GoBack,
        VirtualKey.NavigationCancel -> Key.Back
        VirtualKey.GoForward -> Key.Forward
        VirtualKey.Search -> Key.Search
        VirtualKey.GoHome -> Key.SystemHome
        VirtualKey.GamepadA -> Key.ButtonA
        VirtualKey.GamepadB -> Key.ButtonB
        VirtualKey.GamepadX -> Key.ButtonX
        VirtualKey.GamepadY -> Key.ButtonY
        VirtualKey.GamepadLeftShoulder -> Key.ButtonL1
        VirtualKey.GamepadRightShoulder -> Key.ButtonR1
        VirtualKey.GamepadLeftTrigger -> Key.ButtonL2
        VirtualKey.GamepadRightTrigger -> Key.ButtonR2
        VirtualKey.GamepadDPadUp -> Key.DirectionUp
        VirtualKey.GamepadDPadDown -> Key.DirectionDown
        VirtualKey.GamepadDPadLeft -> Key.DirectionLeft
        VirtualKey.GamepadDPadRight -> Key.DirectionRight
        VirtualKey.GamepadMenu -> Key.ButtonStart
        VirtualKey.GamepadView -> Key.ButtonSelect
        VirtualKey.GamepadLeftThumbstickButton -> Key.ButtonThumbLeft
        VirtualKey.GamepadRightThumbstickButton -> Key.ButtonThumbRight
        else -> Key.Unknown
    }

private fun VirtualKey.toUtf16CodePoint(): Int =
    when (this) {
        VirtualKey.Number0 -> '0'.code
        VirtualKey.Number1 -> '1'.code
        VirtualKey.Number2 -> '2'.code
        VirtualKey.Number3 -> '3'.code
        VirtualKey.Number4 -> '4'.code
        VirtualKey.Number5 -> '5'.code
        VirtualKey.Number6 -> '6'.code
        VirtualKey.Number7 -> '7'.code
        VirtualKey.Number8 -> '8'.code
        VirtualKey.Number9 -> '9'.code
        VirtualKey.A -> 'A'.code
        VirtualKey.B -> 'B'.code
        VirtualKey.C -> 'C'.code
        VirtualKey.D -> 'D'.code
        VirtualKey.E -> 'E'.code
        VirtualKey.F -> 'F'.code
        VirtualKey.G -> 'G'.code
        VirtualKey.H -> 'H'.code
        VirtualKey.I -> 'I'.code
        VirtualKey.J -> 'J'.code
        VirtualKey.K -> 'K'.code
        VirtualKey.L -> 'L'.code
        VirtualKey.M -> 'M'.code
        VirtualKey.N -> 'N'.code
        VirtualKey.O -> 'O'.code
        VirtualKey.P -> 'P'.code
        VirtualKey.Q -> 'Q'.code
        VirtualKey.R -> 'R'.code
        VirtualKey.S -> 'S'.code
        VirtualKey.T -> 'T'.code
        VirtualKey.U -> 'U'.code
        VirtualKey.V -> 'V'.code
        VirtualKey.W -> 'W'.code
        VirtualKey.X -> 'X'.code
        VirtualKey.Y -> 'Y'.code
        VirtualKey.Z -> 'Z'.code
        VirtualKey.Space -> ' '.code
        else -> 0
    }
