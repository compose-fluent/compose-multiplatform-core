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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WinUIUriHandlerTest {
    @Test
    fun opensUriWithSchemeThroughLauncher() {
        val launchedUris = mutableListOf<String>()
        val handler = WinUIUriHandler { launchedUris += it }

        handler.openUri("https://developer.microsoft.com/windows/apps")

        assertEquals(listOf("https://developer.microsoft.com/windows/apps"), launchedUris)
    }

    @Test
    fun acceptsValidUriSchemeCharacters() {
        val launchedUris = mutableListOf<String>()
        val handler = WinUIUriHandler { launchedUris += it }

        handler.openUri("ms-appx-web://assets/index.html")
        handler.openUri("web+compose.winui://open")

        assertEquals(
            listOf(
                "ms-appx-web://assets/index.html",
                "web+compose.winui://open",
            ),
            launchedUris,
        )
    }

    @Test
    fun rejectsUriWithoutSchemeBeforeLaunching() {
        val launchedUris = mutableListOf<String>()
        val handler = WinUIUriHandler { launchedUris += it }

        assertFailsWith<IllegalArgumentException> {
            handler.openUri("www.example.com")
        }

        assertEquals(emptyList(), launchedUris)
    }

    @Test
    fun wrapsLauncherFailureAsIllegalArgumentException() {
        val handler = WinUIUriHandler {
            error("launcher failed")
        }

        assertFailsWith<IllegalArgumentException> {
            handler.openUri("https://example.com")
        }
    }
}
