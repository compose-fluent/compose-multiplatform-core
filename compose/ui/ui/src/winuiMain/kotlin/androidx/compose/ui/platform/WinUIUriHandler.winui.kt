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

import io.github.composefluent.winrt.runtime.WinRtUri
import windows.foundation.collections.ValueSet
import windows.system.Launcher
import windows.system.LauncherOptions

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
        Launcher.launchUriAsync(
            uri = WinRtUri(uri),
            options = LauncherOptions(),
            inputData = ValueSet(),
        ).use {
            // Fire-and-forget, matching UriHandler's synchronous contract.
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
