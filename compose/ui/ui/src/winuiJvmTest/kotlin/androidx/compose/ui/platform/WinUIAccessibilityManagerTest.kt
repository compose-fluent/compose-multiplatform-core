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

class WinUIAccessibilityManagerTest {
    @Test
    fun recommendedTimeoutUsesWindowsMessageDurationForContent() {
        val manager = WinUIAccessibilityManager(messageDurationSeconds = { 7u })

        assertEquals(
            7_000L,
            manager.calculateRecommendedTimeoutMillis(
                originalTimeoutMillis = 2_000L,
                containsIcons = true,
                containsText = false,
                containsControls = false,
            )
        )
    }

    @Test
    fun recommendedTimeoutDoesNotShortenOriginalTimeout() {
        val manager = WinUIAccessibilityManager(messageDurationSeconds = { 3u })

        assertEquals(
            5_000L,
            manager.calculateRecommendedTimeoutMillis(
                originalTimeoutMillis = 5_000L,
                containsIcons = false,
                containsText = true,
                containsControls = false,
            )
        )
    }

    @Test
    fun recommendedTimeoutIgnoresSystemMessageDurationWithoutContent() {
        val manager = WinUIAccessibilityManager(messageDurationSeconds = { 7u })

        assertEquals(
            2_000L,
            manager.calculateRecommendedTimeoutMillis(
                originalTimeoutMillis = 2_000L,
                containsIcons = false,
                containsText = false,
                containsControls = false,
            )
        )
    }
}
