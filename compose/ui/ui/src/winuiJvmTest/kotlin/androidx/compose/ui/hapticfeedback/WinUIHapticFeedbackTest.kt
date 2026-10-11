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

package androidx.compose.ui.hapticfeedback

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WinUIHapticFeedbackTest {
    @BeforeTest
    fun setUp() {
        WinUIHapticFeedback.resetForTest()
    }

    @AfterTest
    fun tearDown() {
        WinUIHapticFeedback.resetForTest()
    }

    @Test
    fun defaultPerformerRecordsRequestWithoutFailing() {
        WinUIHapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

        val state = WinUIHapticFeedback.stateForTest()
        assertEquals(HapticFeedbackType.LongPress, state.lastFeedbackType)
        assertEquals(1, state.feedbackCount)
    }

    @Test
    fun customPerformerReceivesRequestedFeedbackTypes() {
        val received = mutableListOf<HapticFeedbackType>()
        WinUIHapticFeedback.setPerformerForTest(
            WinUIHapticFeedbackPerformer { received += it }
        )

        WinUIHapticFeedback.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        WinUIHapticFeedback.performHapticFeedback(HapticFeedbackType.ToggleOn)

        assertEquals(
            listOf(HapticFeedbackType.KeyboardTap, HapticFeedbackType.ToggleOn),
            received,
        )
        assertEquals(HapticFeedbackType.ToggleOn, WinUIHapticFeedback.stateForTest().lastFeedbackType)
        assertEquals(2, WinUIHapticFeedback.stateForTest().feedbackCount)
    }

    @Test
    fun resetClearsStateAndRestoresNoOpPerformer() {
        val received = mutableListOf<HapticFeedbackType>()
        WinUIHapticFeedback.setPerformerForTest(
            WinUIHapticFeedbackPerformer { received += it }
        )
        WinUIHapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)

        WinUIHapticFeedback.resetForTest()
        WinUIHapticFeedback.performHapticFeedback(HapticFeedbackType.Reject)

        assertEquals(listOf(HapticFeedbackType.Confirm), received)
        assertEquals(HapticFeedbackType.Reject, WinUIHapticFeedback.stateForTest().lastFeedbackType)
        assertEquals(1, WinUIHapticFeedback.stateForTest().feedbackCount)

        WinUIHapticFeedback.resetForTest()

        assertNull(WinUIHapticFeedback.stateForTest().lastFeedbackType)
        assertEquals(0, WinUIHapticFeedback.stateForTest().feedbackCount)
    }
}
