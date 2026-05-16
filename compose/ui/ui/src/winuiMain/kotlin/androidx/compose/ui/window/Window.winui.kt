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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.Stable
import androidx.compose.ui.platform.WinUIComposeView
import androidx.compose.ui.unit.IntSize
import io.github.composefluent.winrt.runtime.BooleanMarshaller
import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.EventRegistrationToken
import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.PlatformAbi
import microsoft.ui.composition.Compositor
import microsoft.ui.dispatching.DispatcherQueue
import microsoft.ui.windowing.AppWindow
import microsoft.ui.windowing.AppWindowChangedEventArgs
import microsoft.ui.windowing.AppWindowClosingEventArgs
import microsoft.ui.xaml.IWindow
import microsoft.ui.xaml.IWindow2
import microsoft.ui.xaml.WindowActivatedEventArgs
import microsoft.ui.xaml.WindowActivationState
import microsoft.ui.xaml.media.DesktopAcrylicBackdrop
import microsoft.ui.xaml.media.MicaBackdrop
import microsoft.ui.xaml.media.SystemBackdrop
import windows.foundation.TypedEventHandler
import microsoft.ui.xaml.Window as XamlWindow

sealed class WindowBackdrop {
    internal abstract fun createSystemBackdrop(): SystemBackdrop?

    object None : WindowBackdrop() {
        override fun createSystemBackdrop(): SystemBackdrop? = null
    }

    object Mica : WindowBackdrop() {
        override fun createSystemBackdrop(): SystemBackdrop = MicaBackdrop()
    }

    object DesktopAcrylic : WindowBackdrop() {
        override fun createSystemBackdrop(): SystemBackdrop = DesktopAcrylicBackdrop()
    }

    class Custom(
        private val systemBackdrop: SystemBackdrop,
    ) : WindowBackdrop() {
        override fun createSystemBackdrop(): SystemBackdrop = systemBackdrop
    }
}

@Stable
interface WindowScope {
    val window: XamlWindow
    val appWindow: AppWindow
    val compositor: Compositor
    val dispatcherQueue: DispatcherQueue
}

@Composable
fun ApplicationScope.Window(
    onCloseRequest: () -> Unit = { exitApplication() },
    title: String = "Untitled",
    extendsContentIntoTitleBar: Boolean = false,
    backdrop: WindowBackdrop = WindowBackdrop.None,
    content: @Composable WindowScope.() -> Unit,
) {
    val applicationContext = this as? WinUIApplicationContext
    ComposeNode<WinUIWindowNode, WinUIApplicationApplier>(
        factory = {
            WinUIWindowNode(applicationContext)
        },
        update = {
            set(onCloseRequest) { this.onCloseRequest = it }
            set(title) { this.title = it }
            set(extendsContentIntoTitleBar) { this.extendsContentIntoTitleBar = it }
            set(backdrop) { this.backdrop = it }
            set(content) { this.content = it }
        },
    )
}

private class WinUIWindowNode(
    applicationContext: WinUIApplicationContext?,
) : WinUIApplicationNode(), WindowScope {
    override val window: XamlWindow = XamlWindow()
    override val appWindow: AppWindow
        get() = checkNotNull(window.appWindow) {
            "WinUI Window AppWindow is not available."
        }
    override val compositor: Compositor
        get() = checkNotNull(window.compositor) {
            "WinUI Window Compositor is not available."
        }
    override val dispatcherQueue: DispatcherQueue
        get() = checkNotNull(window.dispatcherQueue) {
            "WinUI Window DispatcherQueue is not available."
        }

    private var composeView: WinUIComposeView? = null
    private var hasActivated = false
    private var isWindowFocused = false
    private var isReleased = false
    private var isClosingFromRelease = false
    private var activatedHandler: TypedEventHandler<Any?, WindowActivatedEventArgs>? =
        TypedEventHandler { _, args -> handleActivated(args.windowActivationState) }
    private var activatedToken: EventRegistrationToken? =
        window.activated.add(requireNotNull(activatedHandler))
    private var closedHandler: TypedEventHandler<Any?, microsoft.ui.xaml.WindowEventArgs>? =
        TypedEventHandler { _, _ -> handleClosed() }
    private var closedToken: EventRegistrationToken? = window.closed.add(requireNotNull(closedHandler))
    private var appWindowChangedHandler: TypedEventHandler<AppWindow, AppWindowChangedEventArgs>? =
        TypedEventHandler { _, _ -> updateWindowInfo() }
    private var appWindowChangedToken: EventRegistrationToken? = null
    private var appWindowClosingHandler: TypedEventHandler<AppWindow, AppWindowClosingEventArgs>? =
        TypedEventHandler { _, args -> handleClosing(args) }
    private var appWindowClosingToken: EventRegistrationToken? = null
    private var isCaptureProtected = false

    var onCloseRequest: () -> Unit = {}

    init {
        applicationContext?.attachWindow(dispatcherQueue)
    }

    var title: String
        get() = window.title
        set(value) {
            window.title = value
            appWindow.title = value
        }

    var extendsContentIntoTitleBar: Boolean
        get() = window.extendsContentIntoTitleBar
        set(value) {
            window.extendsContentIntoTitleBar = value
        }

    var backdrop: WindowBackdrop = WindowBackdrop.None
        set(value) {
            field = value
            val systemBackdrop = value.createSystemBackdrop()
            if (systemBackdrop != null) {
                window.systemBackdrop = systemBackdrop
            } else {
                clearWindowSystemBackdrop(window)
            }
        }

    var content: @Composable WindowScope.() -> Unit = {}
        set(value) {
            field = value
            if (isReleased) return
            val view = composeView ?: WinUIComposeView(::updateCaptureProtection).also {
                composeView = it
                window.content = it.root
                registerAppWindowChangedHandler()
                registerAppWindowClosingHandler()
            }
            if (!hasActivated) {
                window.activate()
                hasActivated = true
                updateWindowFocus(true)
            }
            view.setWindowFocused(isWindowFocused)
            updateWindowInfo()
            view.setContent {
                this@WinUIWindowNode.field()
            }
        }

    override fun onRelease() {
        if (!isReleased) {
            isReleased = true
            isClosingFromRelease = true
            disposeContent()
            removeAppWindowClosingHandler()
            removeAppWindowChangedHandler()
            removeActivatedHandler()
            removeClosedHandler()
            if (hasActivated) {
                runCatching { window.close() }
            }
        }
        super.onRelease()
    }

    private fun handleClosed() {
        if (isReleased) return
        isReleased = true
        disposeContent()
        removeAppWindowClosingHandler()
        removeAppWindowChangedHandler()
        removeActivatedHandler()
        removeClosedHandler()
        if (!isClosingFromRelease) {
            onCloseRequest()
        }
    }

    private fun handleClosing(args: AppWindowClosingEventArgs) {
        if (isReleased || isClosingFromRelease) return
        args.cancel = true
        onCloseRequest()
    }

    private fun disposeContent() {
        updateCaptureProtection(false)
        composeView?.dispose()
        composeView = null
    }

    private fun handleActivated(activationState: WindowActivationState) {
        if (isReleased) return
        hasActivated = true
        updateWindowFocus(activationState != WindowActivationState.Deactivated)
    }

    private fun updateWindowFocus(isFocused: Boolean) {
        isWindowFocused = isFocused
        composeView?.setWindowFocused(isFocused)
    }

    private fun updateWindowInfo() {
        val view = composeView ?: return
        val appWindowSize = appWindow.size
        view.setWindowContainerSize(
            IntSize(
                width = appWindowSize.width,
                height = appWindowSize.height,
            )
        )
    }

    private fun updateCaptureProtection(isProtected: Boolean) {
        if (isCaptureProtected == isProtected) return
        if (setWindowCaptureProtection(window, isProtected)) {
            isCaptureProtected = isProtected
        }
    }

    private fun registerAppWindowChangedHandler() {
        if (appWindowChangedToken != null) return
        appWindowChangedToken = appWindow.changed.add(requireNotNull(appWindowChangedHandler))
    }

    private fun registerAppWindowClosingHandler() {
        if (appWindowClosingToken != null) return
        appWindowClosingToken = appWindow.closing.add(requireNotNull(appWindowClosingHandler))
    }

    private fun removeAppWindowClosingHandler() {
        val token = appWindowClosingToken ?: return
        appWindowClosingToken = null
        appWindowClosingHandler = null
        runCatching { appWindow.closing.remove(token) }
    }

    private fun removeAppWindowChangedHandler() {
        val token = appWindowChangedToken ?: return
        appWindowChangedToken = null
        appWindowChangedHandler = null
        runCatching { appWindow.changed.remove(token) }
    }

    private fun removeActivatedHandler() {
        val token = activatedToken ?: return
        activatedToken = null
        activatedHandler = null
        runCatching { window.activated.remove(token) }
    }

    private fun removeClosedHandler() {
        val token = closedToken ?: return
        closedToken = null
        closedHandler = null
        runCatching { window.closed.remove(token) }
    }
}

internal expect fun setWindowCaptureProtection(window: XamlWindow, isProtected: Boolean): Boolean

private fun clearWindowSystemBackdrop(window: XamlWindow) {
    // KWINRT-004: generated Window.systemBackdrop cannot currently be set to null.
    window.nativeObject.queryInterface(IWindow2.Metadata.IID).getOrThrow().use { windowInterface ->
        HResult(
            ComVtableInvoker.invokeArgs(
                instance = windowInterface.pointer,
                slot = IWindow2.Metadata.SYSTEMBACKDROP_SETTER_SLOT,
                arg0 = PlatformAbi.nullPointer,
            ),
        ).requireSuccess("Window.SystemBackdrop clear")
    }
}
