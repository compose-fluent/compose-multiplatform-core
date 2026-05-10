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

import io.github.composefluent.winrt.runtime.ActivationFactory
import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.IUnknownReference
import io.github.composefluent.winrt.runtime.PlatformAbi
import io.github.composefluent.winrt.runtime.WinRtSystemProjectionMarshalers
import io.github.composefluent.winrt.runtime.WinRtUri

internal fun createWinUIUriHandler(): UriHandler = WinUIUriHandler

private object WinUIUriHandler : UriHandler {
    override fun openUri(uri: String) {
        require(hasUriScheme(uri)) {
            "URI must include a scheme: $uri"
        }
        try {
            launchUri(uri)
        } catch (e: Exception) {
            throw IllegalArgumentException("Cannot open URI: $uri", e)
        }
    }

    private fun launchUri(uri: String) {
        ActivationFactory.get(
            runtimeClassName = "Windows.System.Launcher",
            interfaceId = launcherStaticsIid,
        ).use { launcherStatics ->
            WinRtSystemProjectionMarshalers.createObjectReference(
                WinRtUri(uri),
                WinRtUri.Metadata.DEFAULT_INTERFACE_IID,
            ).use { uriReference ->
                PlatformAbi.confinedScope().use { scope ->
                    val operationOut = PlatformAbi.allocatePointerSlot(scope)
                    val hr = ComVtableInvoker.invokeArgs(
                        instance = launcherStatics.pointer,
                        slot = launchUriAsyncSlot,
                        arg0 = PlatformAbi.fromRawComPtr(uriReference.pointer),
                        arg1 = operationOut,
                    )
                    HResult(hr).requireSuccess("Windows.System.Launcher.LaunchUriAsync")
                    val operationPointer = PlatformAbi.readPointer(operationOut)
                    if (!PlatformAbi.isNull(operationPointer)) {
                        IUnknownReference(PlatformAbi.toRawComPtr(operationPointer)).close()
                    }
                }
            }
        }
    }
}

private fun hasUriScheme(uri: String): Boolean {
    val colonIndex = uri.indexOf(':')
    if (colonIndex <= 0) return false
    val first = uri[0]
    if (!first.isAsciiLetter()) return false
    for (index in 1 until colonIndex) {
        val char = uri[index]
        if (!char.isAsciiLetterOrDigit() && char != '+' && char != '-' && char != '.') {
            return false
        }
    }
    return true
}

private fun Char.isAsciiLetter(): Boolean =
    this in 'A'..'Z' || this in 'a'..'z'

private fun Char.isAsciiLetterOrDigit(): Boolean =
    isAsciiLetter() || this in '0'..'9'

// KWINRT-005: Declaring type("Windows.System.Launcher") currently pulls invalid
// Windows.ApplicationModel projections. Keep this minimal ABI call until the
// generated Launcher projection is usable.
private val launcherStaticsIid = Guid("277151C3-9E3E-42F6-91A4-5DFDEB232451")
private const val launchUriAsyncSlot = 8
