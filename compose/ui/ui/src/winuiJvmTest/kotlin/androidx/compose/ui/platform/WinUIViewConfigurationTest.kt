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

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class WinUIViewConfigurationTest {
    @Test
    fun usesWinUIDoubleClickTime() {
        val configuration = DefaultWinUIViewConfiguration(
            doubleClickTimeMillis = { 620L },
        )

        assertEquals(620L, configuration.doubleTapTimeoutMillis)
    }

    @Test
    fun usesWinUIInteractionDefaults() {
        val configuration = DefaultWinUIViewConfiguration(
            doubleClickTimeMillis = { 500L },
        )

        assertEquals(500L, configuration.longPressTimeoutMillis)
        assertEquals(40L, configuration.doubleTapMinTimeMillis)
        assertEquals(8f, configuration.touchSlop)
        assertEquals(DpSize(40.dp, 40.dp), configuration.minimumTouchTargetSize)
    }
}
