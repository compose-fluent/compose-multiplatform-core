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

internal object WinUIHapticFeedback : HapticFeedback {
    private var performer: WinUIHapticFeedbackPerformer = WinUIHapticFeedbackPerformer.NoOp
    private var lastFeedbackType: HapticFeedbackType? = null
    private var feedbackCount = 0

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        lastFeedbackType = hapticFeedbackType
        feedbackCount += 1
        performer.performHapticFeedback(hapticFeedbackType)
    }

    internal fun setPerformerForTest(performer: WinUIHapticFeedbackPerformer) {
        this.performer = performer
    }

    internal fun resetForTest() {
        performer = WinUIHapticFeedbackPerformer.NoOp
        lastFeedbackType = null
        feedbackCount = 0
    }

    internal fun stateForTest(): WinUIHapticFeedbackState =
        WinUIHapticFeedbackState(
            lastFeedbackType = lastFeedbackType,
            feedbackCount = feedbackCount,
        )
}

internal fun interface WinUIHapticFeedbackPerformer {
    fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType)

    companion object {
        val NoOp = WinUIHapticFeedbackPerformer {}
    }
}

internal data class WinUIHapticFeedbackState(
    val lastFeedbackType: HapticFeedbackType?,
    val feedbackCount: Int,
)
