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
import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.EventRegistrationToken
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.IInspectableReference
import io.github.composefluent.winrt.runtime.ParameterizedInterfaceId
import io.github.composefluent.winrt.runtime.PlatformAbi
import io.github.composefluent.winrt.runtime.WinRtDelegateBridge
import io.github.composefluent.winrt.runtime.WinRtDelegateHandle
import io.github.composefluent.winrt.runtime.WinRtDelegateValueKind
import io.github.composefluent.winrt.runtime.WinRtTypeSignature
import microsoft.ui.composition.Compositor
import microsoft.ui.dispatching.DispatcherQueue
import microsoft.ui.windowing.AppWindow
import microsoft.ui.windowing.AppWindowChangedEventArgs
import microsoft.ui.windowing.IAppWindow
import microsoft.ui.xaml.IWindow
import microsoft.ui.xaml.IWindowActivatedEventArgs
import microsoft.ui.xaml.WindowActivatedEventArgs
import microsoft.ui.xaml.WindowActivationState
import microsoft.ui.xaml.WindowEventArgs
import microsoft.ui.xaml.media.DesktopAcrylicBackdrop
import microsoft.ui.xaml.media.MicaBackdrop
import microsoft.ui.xaml.media.SystemBackdrop
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
        get() = window.appWindow
    override val compositor: Compositor
        get() = window.compositor
    override val dispatcherQueue: DispatcherQueue
        get() = window.dispatcherQueue

    private var composeView: WinUIComposeView? = null
    private var hasActivated = false
    private var isWindowFocused = false
    private var isReleased = false
    private var isClosingFromRelease = false
    private var activatedDelegate: WinRtDelegateHandle? =
        createWindowActivatedDelegate(::handleActivated)
    private var activatedToken: EventRegistrationToken? =
        addWindowActivatedHandler(window, requireNotNull(activatedDelegate))
    private var closedDelegate: WinRtDelegateHandle? = createWindowClosedDelegate(::handleClosed)
    private var closedToken: EventRegistrationToken? =
        addWindowClosedHandler(window, requireNotNull(closedDelegate))
    private var appWindowChangedDelegate: WinRtDelegateHandle? =
        createAppWindowChangedDelegate(::updateWindowInfo)
    private var appWindowChangedToken: EventRegistrationToken? = null

    var onCloseRequest: () -> Unit = {}

    init {
        applicationContext?.attachWindow(window.dispatcherQueue)
    }

    var title: String
        get() = window.title
        set(value) {
            window.title = value
            window.appWindow.title = value
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
            // KWINRT-004: generated Window.systemBackdrop cannot currently be set to null.
            if (systemBackdrop == null) return
            window.systemBackdrop = systemBackdrop
        }

    var content: @Composable WindowScope.() -> Unit = {}
        set(value) {
            field = value
            if (isReleased) return
            val view = composeView ?: WinUIComposeView().also {
                composeView = it
                window.content = it.root
                registerAppWindowChangedHandler()
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
        if (isReleased) return
        isReleased = true
        isClosingFromRelease = true
        disposeContent()
        removeAppWindowChangedHandler()
        removeActivatedHandler()
        removeClosedHandler()
        if (hasActivated) {
            runCatching { window.close() }
        }
        super.onRelease()
    }

    private fun handleClosed() {
        if (isReleased) return
        isReleased = true
        disposeContent()
        removeAppWindowChangedHandler()
        removeActivatedHandler()
        removeClosedHandler()
        if (!isClosingFromRelease) {
            onCloseRequest()
        }
    }

    private fun disposeContent() {
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
        val appWindowSize = window.appWindow.size
        view.setWindowContainerSize(
            IntSize(
                width = appWindowSize.width,
                height = appWindowSize.height,
            )
        )
    }

    private fun registerAppWindowChangedHandler() {
        if (appWindowChangedToken != null) return
        appWindowChangedToken =
            addAppWindowChangedHandler(window.appWindow, requireNotNull(appWindowChangedDelegate))
    }

    private fun removeAppWindowChangedHandler() {
        val token = appWindowChangedToken ?: return
        val delegate = appWindowChangedDelegate
        appWindowChangedToken = null
        appWindowChangedDelegate = null
        runCatching { removeAppWindowChangedHandler(window.appWindow, token) }
        delegate?.close()
    }

    private fun removeActivatedHandler() {
        val token = activatedToken ?: return
        val delegate = activatedDelegate
        activatedToken = null
        activatedDelegate = null
        runCatching { removeWindowActivatedHandler(window, token) }
        delegate?.close()
    }

    private fun removeClosedHandler() {
        val token = closedToken ?: return
        val delegate = closedDelegate
        closedToken = null
        closedDelegate = null
        runCatching { removeWindowClosedHandler(window, token) }
        delegate?.close()
    }
}

private fun createWindowActivatedDelegate(
    onActivated: (WindowActivationState) -> Unit,
): WinRtDelegateHandle =
    WinRtDelegateBridge.createUnitDelegate(
        iid = ParameterizedInterfaceId.createFromParameterizedInterface(
            Guid("9DE1C534-6AE1-11E0-84E1-18A905BCC53F"),
            WinRtTypeSignature.object_(),
            WinRtTypeSignature.runtimeClass(
                WindowActivatedEventArgs.Metadata.TYPE_NAME,
                WinRtTypeSignature.guid(WindowActivatedEventArgs.Metadata.DEFAULT_INTERFACE_IID),
            ),
        ),
        parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.IINSPECTABLE),
    ) { arguments ->
        val args = arguments.getOrNull(1) as? IInspectableReference ?: return@createUnitDelegate
        val activationState = try {
            readWindowActivationState(args)
        } finally {
            args.close()
        }
        onActivated(activationState)
    }

private fun readWindowActivationState(args: IInspectableReference): WindowActivationState =
    args.queryInterface(IWindowActivatedEventArgs.Metadata.IID).getOrThrow().use { eventArgsInterface ->
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocateInt32Slot(scope)
            HResult(
                ComVtableInvoker.invokeArgs(
                    instance = eventArgsInterface.pointer,
                    slot = IWindowActivatedEventArgs.Metadata.WINDOWACTIVATIONSTATE_GETTER_SLOT,
                    arg0 = resultOut,
                ),
            ).requireSuccess("WindowActivatedEventArgs.WindowActivationState")
            // KWINRT-011: do not call generated internal WindowActivationState.Metadata.fromAbi;
            // duplicate sample projections can load a class with a different module-mangled name.
            when (PlatformAbi.readInt32(resultOut)) {
                0 -> WindowActivationState.CodeActivated
                1 -> WindowActivationState.Deactivated
                2 -> WindowActivationState.PointerActivated
                else -> WindowActivationState.CodeActivated
            }
        }
    }

private fun addWindowActivatedHandler(
    window: XamlWindow,
    delegate: WinRtDelegateHandle,
): EventRegistrationToken =
    // KWINRT-001: use direct IWindow ABI registration until generated event sources are stable.
    window.nativeObject.queryInterface(IWindow.Metadata.IID).getOrThrow().use { windowInterface ->
        delegate.createReference().use { delegateReference ->
            PlatformAbi.confinedScope().use { scope ->
                val tokenOut = PlatformAbi.allocateBytes(scope, EventRegistrationToken.BYTE_SIZE.toLong())
                HResult(
                    ComVtableInvoker.invokeArgs(
                        instance = windowInterface.pointer,
                        slot = IWindow.Metadata.ACTIVATED_ADD_SLOT,
                        arg0 = PlatformAbi.fromRawComPtr(delegateReference.pointer),
                        arg1 = tokenOut,
                    ),
                ).requireSuccess("Window.Activated add handler")
                EventRegistrationToken.fromAbi(tokenOut)
            }
        }
    }

private fun removeWindowActivatedHandler(
    window: XamlWindow,
    token: EventRegistrationToken,
) {
    // KWINRT-001: pair with the manual Window.Activated registration above.
    window.nativeObject.queryInterface(IWindow.Metadata.IID).getOrThrow().use { windowInterface ->
        PlatformAbi.confinedScope().use { scope ->
            val tokenAbi = PlatformAbi.allocateBytes(scope, EventRegistrationToken.BYTE_SIZE.toLong())
            EventRegistrationToken.copyTo(token, tokenAbi)
            HResult(
                ComVtableInvoker.invokeArgs(
                    instance = windowInterface.pointer,
                    slot = IWindow.Metadata.ACTIVATED_REMOVE_SLOT,
                    arg0 = tokenAbi,
                ),
            ).requireSuccess("Window.Activated remove handler")
        }
    }
}

private fun createAppWindowChangedDelegate(
    onChanged: () -> Unit,
): WinRtDelegateHandle =
    WinRtDelegateBridge.createUnitDelegate(
        iid = ParameterizedInterfaceId.createFromParameterizedInterface(
            Guid("9DE1C534-6AE1-11E0-84E1-18A905BCC53F"),
            WinRtTypeSignature.runtimeClass(
                AppWindow.Metadata.TYPE_NAME,
                WinRtTypeSignature.guid(AppWindow.Metadata.DEFAULT_INTERFACE_IID),
            ),
            WinRtTypeSignature.runtimeClass(
                AppWindowChangedEventArgs.Metadata.TYPE_NAME,
                WinRtTypeSignature.guid(AppWindowChangedEventArgs.Metadata.DEFAULT_INTERFACE_IID),
            ),
        ),
        parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.IINSPECTABLE),
    ) { arguments ->
        (arguments.getOrNull(1) as? IInspectableReference)?.close()
        onChanged()
    }

private fun addAppWindowChangedHandler(
    appWindow: AppWindow,
    delegate: WinRtDelegateHandle,
): EventRegistrationToken =
    // KWINRT-001: use direct IAppWindow ABI registration until generated event sources are stable.
    appWindow.nativeObject.queryInterface(IAppWindow.Metadata.IID).getOrThrow().use { appWindowInterface ->
        delegate.createReference().use { delegateReference ->
            PlatformAbi.confinedScope().use { scope ->
                val tokenOut = PlatformAbi.allocateBytes(scope, EventRegistrationToken.BYTE_SIZE.toLong())
                HResult(
                    ComVtableInvoker.invokeArgs(
                        instance = appWindowInterface.pointer,
                        slot = IAppWindow.Metadata.CHANGED_ADD_SLOT,
                        arg0 = PlatformAbi.fromRawComPtr(delegateReference.pointer),
                        arg1 = tokenOut,
                    ),
                ).requireSuccess("AppWindow.Changed add handler")
                EventRegistrationToken.fromAbi(tokenOut)
            }
        }
    }

private fun removeAppWindowChangedHandler(
    appWindow: AppWindow,
    token: EventRegistrationToken,
) {
    // KWINRT-001: pair with the manual AppWindow.Changed registration above.
    appWindow.nativeObject.queryInterface(IAppWindow.Metadata.IID).getOrThrow().use { appWindowInterface ->
        PlatformAbi.confinedScope().use { scope ->
            val tokenAbi = PlatformAbi.allocateBytes(scope, EventRegistrationToken.BYTE_SIZE.toLong())
            EventRegistrationToken.copyTo(token, tokenAbi)
            HResult(
                ComVtableInvoker.invokeArgs(
                    instance = appWindowInterface.pointer,
                    slot = IAppWindow.Metadata.CHANGED_REMOVE_SLOT,
                    arg0 = tokenAbi,
                ),
            ).requireSuccess("AppWindow.Changed remove handler")
        }
    }
}

private fun createWindowClosedDelegate(
    onClosed: () -> Unit,
): WinRtDelegateHandle =
    WinRtDelegateBridge.createUnitDelegate(
        iid = ParameterizedInterfaceId.createFromParameterizedInterface(
            Guid("9DE1C534-6AE1-11E0-84E1-18A905BCC53F"),
            WinRtTypeSignature.object_(),
            WinRtTypeSignature.runtimeClass(
                WindowEventArgs.Metadata.TYPE_NAME,
                WinRtTypeSignature.guid(WindowEventArgs.Metadata.DEFAULT_INTERFACE_IID),
            ),
        ),
        parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.IINSPECTABLE),
    ) { arguments ->
        (arguments.getOrNull(1) as? IInspectableReference)?.close()
        onClosed()
    }

private fun addWindowClosedHandler(
    window: XamlWindow,
    delegate: WinRtDelegateHandle,
): EventRegistrationToken =
    // KWINRT-001: use direct IWindow ABI registration until generated event sources are stable.
    window.nativeObject.queryInterface(IWindow.Metadata.IID).getOrThrow().use { windowInterface ->
        delegate.createReference().use { delegateReference ->
            PlatformAbi.confinedScope().use { scope ->
                val tokenOut = PlatformAbi.allocateBytes(scope, EventRegistrationToken.BYTE_SIZE.toLong())
                HResult(
                    ComVtableInvoker.invokeArgs(
                        instance = windowInterface.pointer,
                        slot = IWindow.Metadata.CLOSED_ADD_SLOT,
                        arg0 = PlatformAbi.fromRawComPtr(delegateReference.pointer),
                        arg1 = tokenOut,
                    ),
                ).requireSuccess("Window.Closed add handler")
                EventRegistrationToken.fromAbi(tokenOut)
            }
        }
    }

private fun removeWindowClosedHandler(
    window: XamlWindow,
    token: EventRegistrationToken,
) {
    // KWINRT-001: pair with the manual Window.Closed registration above.
    window.nativeObject.queryInterface(IWindow.Metadata.IID).getOrThrow().use { windowInterface ->
        PlatformAbi.confinedScope().use { scope ->
            val tokenAbi = PlatformAbi.allocateBytes(scope, EventRegistrationToken.BYTE_SIZE.toLong())
            EventRegistrationToken.copyTo(token, tokenAbi)
            HResult(
                ComVtableInvoker.invokeArgs(
                    instance = windowInterface.pointer,
                    slot = IWindow.Metadata.CLOSED_REMOVE_SLOT,
                    arg0 = tokenAbi,
                ),
            ).requireSuccess("Window.Closed remove handler")
        }
    }
}
