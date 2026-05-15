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

package androidx.compose.ui.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WinUIPlatformFocusOwnerTest {
    @Test
    fun requestOwnerFocusReturnsTrueAfterNativeFocusAttempt() {
        var acceptedRequests = 0
        var rejectedRequests = 0
        val acceptedOwner = WinUIPlatformFocusOwner(
            requestNativeFocus = {
                acceptedRequests += 1
                true
            },
            clearNativeFocus = {},
        )
        val rejectedOwner = WinUIPlatformFocusOwner(
            requestNativeFocus = {
                rejectedRequests += 1
                false
            },
            clearNativeFocus = {},
        )

        assertTrue(acceptedOwner.requestOwnerFocus(FocusDirection.Next, null))
        assertTrue(rejectedOwner.requestOwnerFocus(FocusDirection.Next, null))
        assertEquals(1, acceptedRequests)
        assertEquals(1, rejectedRequests)
    }

    @Test
    fun requestOwnerFocusReturnsFalseWhenNativeFocusThrows() {
        val owner = WinUIPlatformFocusOwner(
            requestNativeFocus = { error("native focus failed") },
            clearNativeFocus = {},
        )

        assertFalse(owner.requestOwnerFocus(FocusDirection.Next, null))
    }

    @Test
    fun clearOwnerFocusDelegatesAndSuppressesNativeFailure() {
        var clearRequests = 0
        val owner = WinUIPlatformFocusOwner(
            requestNativeFocus = { true },
            clearNativeFocus = { clearRequests += 1 },
        )
        val failingOwner = WinUIPlatformFocusOwner(
            requestNativeFocus = { true },
            clearNativeFocus = { error("native clear failed") },
        )

        owner.clearOwnerFocus()
        failingOwner.clearOwnerFocus()

        assertEquals(1, clearRequests)
    }

    @Test
    fun embeddedFocusAndChildFocusMovementAreDeferred() {
        val owner = WinUIPlatformFocusOwner(
            requestNativeFocus = { true },
            clearNativeFocus = {},
        )

        assertFalse(owner.moveFocusInChildren(FocusDirection.Next))
        assertNull(owner.getEmbeddedViewFocusRect())
    }
}
