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

package androidx.compose.ui.input.pointer

import windows.ui.core.CoreCursorType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WinUIPointerIconServiceTest {
    @Test
    fun pointerIconsMapToWinUICoreCursorTypes() {
        assertEquals(CoreCursorType.Arrow, (PointerIcon.Default as WinUIPointerIcon).cursorType)
        assertEquals(CoreCursorType.Cross, (PointerIcon.Crosshair as WinUIPointerIcon).cursorType)
        assertEquals(CoreCursorType.IBeam, (PointerIcon.Text as WinUIPointerIcon).cursorType)
        assertEquals(CoreCursorType.Hand, (PointerIcon.Hand as WinUIPointerIcon).cursorType)
    }

    @Test
    fun setIconAppliesResolvedPointerIcon() {
        val applied = mutableListOf<PointerIcon>()
        val service = WinUIPointerIconService { applied += it }

        service.setIcon(PointerIcon.Hand)
        service.setIcon(null)
        service.setIcon(PointerIcon.Text)

        assertEquals(PointerIcon.Text, service.getIcon())
        assertEquals(
            listOf(PointerIcon.Hand, PointerIcon.Default, PointerIcon.Text),
            applied,
        )
    }

    @Test
    fun stylusHoverIconIsStoredWithoutChangingRootPointerIcon() {
        val applied = mutableListOf<PointerIcon>()
        val service = WinUIPointerIconService { applied += it }

        service.setStylusHoverIcon(PointerIcon.Crosshair)

        assertEquals(PointerIcon.Crosshair, service.getStylusHoverIcon())
        assertNull(service.getIcon().takeIf { it != PointerIcon.Default })
        assertEquals(emptyList(), applied)
    }
}
