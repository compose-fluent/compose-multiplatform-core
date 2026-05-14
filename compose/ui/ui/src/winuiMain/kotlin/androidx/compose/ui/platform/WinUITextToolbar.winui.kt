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

import androidx.compose.ui.geometry.Rect
import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.EventRegistrationToken
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.ParameterizedInterfaceId
import io.github.composefluent.winrt.runtime.PlatformAbi
import io.github.composefluent.winrt.runtime.WinRtDelegateBridge
import io.github.composefluent.winrt.runtime.WinRtDelegateHandle
import io.github.composefluent.winrt.runtime.WinRtDelegateValueKind
import io.github.composefluent.winrt.runtime.WinRtTypeSignature
import microsoft.ui.xaml.RoutedEventHandler
import microsoft.ui.xaml.UIElement
import microsoft.ui.xaml.controls.IMenuFlyoutItem
import microsoft.ui.xaml.controls.MenuFlyout
import microsoft.ui.xaml.controls.MenuFlyoutItem
import microsoft.ui.xaml.controls.primitives.IFlyoutBase
import windows.foundation.Point

internal class WinUITextToolbar(
    private val hostProvider: () -> UIElement? = { null },
) : TextToolbar {
    private var currentMenu: WinUITextToolbarMenu? = null

    override val status: TextToolbarStatus
        get() = if (currentMenu == null) {
            TextToolbarStatus.Hidden
        } else {
            TextToolbarStatus.Shown
        }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
        onAutofillRequested: (() -> Unit)?,
    ) {
        hide()
        val requests = buildList {
            addRequest("Copy", onCopyRequested)
            addRequest("Paste", onPasteRequested)
            addRequest("Cut", onCutRequested)
            addRequest("Select all", onSelectAllRequested)
            addRequest("Autofill", onAutofillRequested)
        }
        val host = hostProvider()
        val nativeMenu = if (host != null && requests.isNotEmpty()) {
            createNativeMenu(requests)
        } else {
            null
        }
        val menu = WinUITextToolbarMenu(
            rect = rect,
            onCopyRequested = onCopyRequested,
            onPasteRequested = onPasteRequested,
            onCutRequested = onCutRequested,
            onSelectAllRequested = onSelectAllRequested,
            onAutofillRequested = onAutofillRequested,
            itemLabels = requests.map { it.label },
            nativeMenu = nativeMenu?.flyout,
            clickRegistrations = nativeMenu?.clickRegistrations.orEmpty(),
            closedRegistration = nativeMenu?.closedRegistration,
        )
        currentMenu = menu
        if (host != null && nativeMenu != null) {
            try {
                nativeMenu.flyout.showAt(host, Point(rect.left, rect.bottom))
            } catch (throwable: Throwable) {
                currentMenu = null
                clearNativeRegistrations(menu)
                throw throwable
            }
        }
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        showMenu(
            rect = rect,
            onCopyRequested = onCopyRequested,
            onPasteRequested = onPasteRequested,
            onCutRequested = onCutRequested,
            onSelectAllRequested = onSelectAllRequested,
            onAutofillRequested = null,
        )
    }

    override fun hide() {
        val menu = currentMenu ?: return
        currentMenu = null
        clearNativeRegistrations(menu)
        menu.nativeMenu?.hide()
    }

    internal fun menuForTest(): WinUITextToolbarMenu? = currentMenu

    private fun createNativeMenu(
        requests: List<WinUITextToolbarRequest>,
    ): NativeMenu {
        val flyout = MenuFlyout()
        val clickRegistrations = mutableListOf<WinUITextToolbarClickRegistration>()
        requests.forEach { request ->
            val item = MenuFlyoutItem()
            item.text = request.label
            val delegate = RoutedEventHandler { _, _ ->
                request.callback()
                hide()
            }.createWinRtDelegateHandle()
            val token = try {
                addMenuFlyoutItemClickHandler(item, delegate)
            } catch (throwable: Throwable) {
                clickRegistrations.forEach(::clearClickRegistration)
                delegate.close()
                throw throwable
            }
            clickRegistrations += WinUITextToolbarClickRegistration(item, token, delegate)
            flyout.items.add(item)
        }
        val closedDelegate = createMenuFlyoutClosedDelegate {
            val menu = currentMenu
            if (menu?.nativeMenu == flyout) {
                currentMenu = null
                clearNativeRegistrations(menu)
            }
        }
        val closedRegistration = try {
            addMenuFlyoutClosedHandler(flyout, closedDelegate)
        } catch (throwable: Throwable) {
            clickRegistrations.forEach(::clearClickRegistration)
            closedDelegate.close()
            throw throwable
        }
        return NativeMenu(flyout, clickRegistrations, closedRegistration)
    }

    private fun clearNativeRegistrations(menu: WinUITextToolbarMenu) {
        menu.clickRegistrations.forEach(::clearClickRegistration)
        val closedRegistration = menu.closedRegistration
        if (closedRegistration != null) {
            runCatching {
                menu.nativeMenu?.let {
                    removeMenuFlyoutClosedHandler(it, closedRegistration.token)
                }
            }
            closedRegistration.delegate.close()
        }
    }

    private fun clearClickRegistration(registration: WinUITextToolbarClickRegistration) {
        runCatching {
            removeMenuFlyoutItemClickHandler(registration.item, registration.token)
        }
        registration.delegate.close()
    }
}

internal data class WinUITextToolbarMenu(
    val rect: Rect,
    val onCopyRequested: (() -> Unit)?,
    val onPasteRequested: (() -> Unit)?,
    val onCutRequested: (() -> Unit)?,
    val onSelectAllRequested: (() -> Unit)?,
    val onAutofillRequested: (() -> Unit)?,
    val itemLabels: List<String>,
    val nativeMenu: MenuFlyout?,
    val clickRegistrations: List<WinUITextToolbarClickRegistration>,
    val closedRegistration: WinUITextToolbarClosedRegistration?,
)

internal data class WinUITextToolbarClickRegistration(
    val item: MenuFlyoutItem,
    val token: EventRegistrationToken,
    val delegate: WinRtDelegateHandle,
)

internal data class WinUITextToolbarClosedRegistration(
    val token: EventRegistrationToken,
    val delegate: WinRtDelegateHandle,
)

private data class WinUITextToolbarRequest(
    val label: String,
    val callback: () -> Unit,
)

private data class NativeMenu(
    val flyout: MenuFlyout,
    val clickRegistrations: List<WinUITextToolbarClickRegistration>,
    val closedRegistration: WinUITextToolbarClosedRegistration,
)

private fun MutableList<WinUITextToolbarRequest>.addRequest(label: String, callback: (() -> Unit)?) {
    if (callback != null) {
        add(WinUITextToolbarRequest(label, callback))
    }
}

private fun addMenuFlyoutItemClickHandler(
    item: MenuFlyoutItem,
    delegate: WinRtDelegateHandle,
): EventRegistrationToken =
    // KWINRT-001: use direct IMenuFlyoutItem ABI registration until generated event sources are stable.
    item.nativeObject.queryInterface(IMenuFlyoutItem.Metadata.IID).getOrThrow().use { itemInterface ->
        delegate.createReference().use { delegateReference ->
            PlatformAbi.confinedScope().use { scope ->
                val tokenOut = PlatformAbi.allocateBytes(scope, EventRegistrationToken.BYTE_SIZE.toLong())
                HResult(
                    ComVtableInvoker.invokeArgs(
                        instance = itemInterface.pointer,
                        slot = IMenuFlyoutItem.Metadata.CLICK_ADD_SLOT,
                        arg0 = PlatformAbi.fromRawComPtr(delegateReference.pointer),
                        arg1 = tokenOut,
                    ),
                ).requireSuccess("MenuFlyoutItem.Click add handler")
                EventRegistrationToken.fromAbi(tokenOut)
            }
        }
    }

private fun removeMenuFlyoutItemClickHandler(
    item: MenuFlyoutItem,
    token: EventRegistrationToken,
) {
    // KWINRT-001: pair with the manual MenuFlyoutItem.Click registration above.
    item.nativeObject.queryInterface(IMenuFlyoutItem.Metadata.IID).getOrThrow().use { itemInterface ->
        PlatformAbi.confinedScope().use { scope ->
            val tokenAbi = PlatformAbi.allocateBytes(scope, EventRegistrationToken.BYTE_SIZE.toLong())
            EventRegistrationToken.copyTo(token, tokenAbi)
            HResult(
                ComVtableInvoker.invokeArgs(
                    instance = itemInterface.pointer,
                    slot = IMenuFlyoutItem.Metadata.CLICK_REMOVE_SLOT,
                    arg0 = tokenAbi,
                ),
            ).requireSuccess("MenuFlyoutItem.Click remove handler")
        }
    }
}

private fun createMenuFlyoutClosedDelegate(
    onClosed: () -> Unit,
): WinRtDelegateHandle =
    WinRtDelegateBridge.createUnitDelegate(
        iid = ParameterizedInterfaceId.createFromParameterizedInterface(
            Guid("9DE1C534-6AE1-11E0-84E1-18A905BCC53F"),
            WinRtTypeSignature.object_(),
        ),
        parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.OBJECT),
    ) {
        onClosed()
    }

private fun addMenuFlyoutClosedHandler(
    flyout: MenuFlyout,
    delegate: WinRtDelegateHandle,
): WinUITextToolbarClosedRegistration =
    // KWINRT-001: use direct IFlyoutBase ABI registration until generated event sources are stable.
    flyout.nativeObject.queryInterface(IFlyoutBase.Metadata.IID).getOrThrow().use { flyoutInterface ->
        delegate.createReference().use { delegateReference ->
            PlatformAbi.confinedScope().use { scope ->
                val tokenOut = PlatformAbi.allocateBytes(scope, EventRegistrationToken.BYTE_SIZE.toLong())
                HResult(
                    ComVtableInvoker.invokeArgs(
                        instance = flyoutInterface.pointer,
                        slot = IFlyoutBase.Metadata.CLOSED_ADD_SLOT,
                        arg0 = PlatformAbi.fromRawComPtr(delegateReference.pointer),
                        arg1 = tokenOut,
                    ),
                ).requireSuccess("MenuFlyout.Closed add handler")
                WinUITextToolbarClosedRegistration(EventRegistrationToken.fromAbi(tokenOut), delegate)
            }
        }
    }

private fun removeMenuFlyoutClosedHandler(
    flyout: MenuFlyout,
    token: EventRegistrationToken,
) {
    // KWINRT-001: pair with the manual MenuFlyout.Closed registration above.
    flyout.nativeObject.queryInterface(IFlyoutBase.Metadata.IID).getOrThrow().use { flyoutInterface ->
        PlatformAbi.confinedScope().use { scope ->
            val tokenAbi = PlatformAbi.allocateBytes(scope, EventRegistrationToken.BYTE_SIZE.toLong())
            EventRegistrationToken.copyTo(token, tokenAbi)
            HResult(
                ComVtableInvoker.invokeArgs(
                    instance = flyoutInterface.pointer,
                    slot = IFlyoutBase.Metadata.CLOSED_REMOVE_SLOT,
                    arg0 = tokenAbi,
                ),
            ).requireSuccess("MenuFlyout.Closed remove handler")
        }
    }
}
