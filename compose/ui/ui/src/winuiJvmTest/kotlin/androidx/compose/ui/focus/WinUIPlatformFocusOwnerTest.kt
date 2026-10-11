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
import microsoft.ui.xaml.input.FocusNavigationDirection

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
            moveNativeFocus = { false },
        )
        val rejectedOwner = WinUIPlatformFocusOwner(
            requestNativeFocus = {
                rejectedRequests += 1
                false
            },
            clearNativeFocus = {},
            moveNativeFocus = { false },
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
            moveNativeFocus = { false },
        )

        assertFalse(owner.requestOwnerFocus(FocusDirection.Next, null))
    }

    @Test
    fun clearOwnerFocusDelegatesAndSuppressesNativeFailure() {
        var clearRequests = 0
        val owner = WinUIPlatformFocusOwner(
            requestNativeFocus = { true },
            clearNativeFocus = { clearRequests += 1 },
            moveNativeFocus = { false },
        )
        val failingOwner = WinUIPlatformFocusOwner(
            requestNativeFocus = { true },
            clearNativeFocus = { error("native clear failed") },
            moveNativeFocus = { false },
        )

        owner.clearOwnerFocus()
        failingOwner.clearOwnerFocus()

        assertEquals(1, clearRequests)
    }

    @Test
    fun moveFocusInChildrenDelegatesSupportedDirectionsToWinUI() {
        val requestedDirections = mutableListOf<FocusNavigationDirection>()
        val owner = WinUIPlatformFocusOwner(
            requestNativeFocus = { true },
            clearNativeFocus = {},
            moveNativeFocus = {
                requestedDirections += it
                true
            },
        )

        assertTrue(owner.moveFocusInChildren(FocusDirection.Next))
        assertTrue(owner.moveFocusInChildren(FocusDirection.Previous))
        assertTrue(owner.moveFocusInChildren(FocusDirection.Up))
        assertTrue(owner.moveFocusInChildren(FocusDirection.Down))
        assertTrue(owner.moveFocusInChildren(FocusDirection.Left))
        assertTrue(owner.moveFocusInChildren(FocusDirection.Right))

        assertEquals(
            listOf(
                FocusNavigationDirection.Next,
                FocusNavigationDirection.Previous,
                FocusNavigationDirection.Up,
                FocusNavigationDirection.Down,
                FocusNavigationDirection.Left,
                FocusNavigationDirection.Right,
            ),
            requestedDirections,
        )
    }

    @Test
    fun moveFocusInChildrenDoesNotDelegateUnsupportedDirections() {
        var requestedDirections = 0
        val owner = WinUIPlatformFocusOwner(
            requestNativeFocus = { true },
            clearNativeFocus = {},
            moveNativeFocus = {
                requestedDirections += 1
                true
            },
        )

        assertFalse(owner.moveFocusInChildren(FocusDirection.Enter))
        assertFalse(owner.moveFocusInChildren(FocusDirection.Exit))
        assertEquals(0, requestedDirections)
    }

    @Test
    fun moveFocusInChildrenReturnsFalseWhenWinUIRejectsOrThrows() {
        val owner = WinUIPlatformFocusOwner(
            requestNativeFocus = { true },
            clearNativeFocus = {},
            moveNativeFocus = { false },
        )
        val failingOwner = WinUIPlatformFocusOwner(
            requestNativeFocus = { true },
            clearNativeFocus = {},
            moveNativeFocus = { error("native focus move failed") },
        )

        assertFalse(owner.moveFocusInChildren(FocusDirection.Next))
        assertFalse(failingOwner.moveFocusInChildren(FocusDirection.Next))
    }

    @Test
    fun embeddedFocusRectIsDeferred() {
        val owner = WinUIPlatformFocusOwner(
            requestNativeFocus = { true },
            clearNativeFocus = {},
            moveNativeFocus = { false },
        )

        assertNull(owner.getEmbeddedViewFocusRect())
    }
}
