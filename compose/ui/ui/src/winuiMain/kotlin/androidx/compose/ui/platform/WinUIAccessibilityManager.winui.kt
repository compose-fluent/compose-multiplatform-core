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

import windows.ui.viewmanagement.UISettings

@Suppress("DEPRECATION")
internal class WinUIAccessibilityManager(
    private val messageDurationSeconds: () -> UInt? = {
        runCatching { UISettings().messageDuration }.getOrNull()
    },
) : AccessibilityManager {
    override fun calculateRecommendedTimeoutMillis(
        originalTimeoutMillis: Long,
        containsIcons: Boolean,
        containsText: Boolean,
        containsControls: Boolean,
    ): Long {
        if (!containsIcons && !containsText && !containsControls) {
            return originalTimeoutMillis
        }
        val recommendedMillis = messageDurationSeconds()
            ?.takeIf { it > 0u }
            ?.let { seconds ->
                val maxSafeSeconds = Long.MAX_VALUE / MillisecondsPerSecond
                val secondsAsLong = seconds.toLong()
                if (secondsAsLong >= maxSafeSeconds) {
                    Long.MAX_VALUE
                } else {
                    secondsAsLong * MillisecondsPerSecond
                }
            }
            ?: return originalTimeoutMillis
        return maxOf(originalTimeoutMillis, recommendedMillis)
    }

    private companion object {
        const val MillisecondsPerSecond = 1_000L
    }
}
