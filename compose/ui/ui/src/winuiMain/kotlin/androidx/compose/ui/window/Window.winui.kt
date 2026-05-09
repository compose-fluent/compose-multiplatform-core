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
import microsoft.ui.xaml.IWindow
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
    private var isActivated = false
    private var isReleased = false
    private var isClosingFromRelease = false
    private var closedDelegate: WinRtDelegateHandle? = createWindowClosedDelegate(::handleClosed)
    private var closedToken: EventRegistrationToken? =
        addWindowClosedHandler(window, requireNotNull(closedDelegate))

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
            if (systemBackdrop != null) {
                window.systemBackdrop = systemBackdrop
            }
        }

    var content: @Composable WindowScope.() -> Unit = {}
        set(value) {
            field = value
            if (isReleased) return
            val view = composeView ?: WinUIComposeView().also {
                composeView = it
                window.content = it.root
            }
            if (!isActivated) {
                window.activate()
                isActivated = true
            }
            view.setContent {
                this@WinUIWindowNode.field()
            }
        }

    override fun onRelease() {
        if (isReleased) return
        isReleased = true
        isClosingFromRelease = true
        disposeContent()
        removeClosedHandler()
        if (isActivated) {
            runCatching { window.close() }
        }
        super.onRelease()
    }

    private fun handleClosed() {
        if (isReleased) return
        isReleased = true
        disposeContent()
        removeClosedHandler()
        if (!isClosingFromRelease) {
            onCloseRequest()
        }
    }

    private fun disposeContent() {
        composeView?.disposeComposition()
        composeView = null
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
