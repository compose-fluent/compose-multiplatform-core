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

import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.FinishComposingTextCommand
import androidx.compose.ui.text.input.SetComposingTextCommand

internal class WinUIWindowsImeInputProcessor(
    private val dispatchEditCommands: (List<EditCommand>) -> Boolean,
) {
    private val characterFallbackSuppressionQueue = ArrayDeque<String>()

    var isCompositionActive: Boolean = false
        private set

    fun onImeStartComposition() {
        isCompositionActive = true
    }

    fun reset() {
        isCompositionActive = false
        characterFallbackSuppressionQueue.clear()
    }

    fun onImeComposition(
        composingText: String,
        resultText: String,
    ): Boolean {
        val commands = buildList {
            if (resultText.isNotEmpty()) {
                add(CommitTextCommand(resultText, 1))
            }
            if (composingText.isNotEmpty()) {
                add(SetComposingTextCommand(composingText, 1))
            }
        }
        if (commands.isEmpty()) {
            return true
        }

        val delivered = dispatchEditCommands(commands)
        if (delivered) {
            isCompositionActive = composingText.isNotEmpty() || isCompositionActive
            enqueueCommittedCharactersForFallbackSuppression(resultText)
        }
        return delivered
    }

    fun onImeEndComposition(): Boolean {
        if (!isCompositionActive) {
            return true
        }

        val delivered = dispatchEditCommands(listOf(FinishComposingTextCommand()))
        if (delivered) {
            isCompositionActive = false
        }
        return delivered
    }

    fun shouldSuppressCharacterFallback(text: String): Boolean {
        val expected = characterFallbackSuppressionQueue.removeFirstOrNull()
        if (expected == text) {
            return true
        }

        characterFallbackSuppressionQueue.clear()
        return false
    }

    private fun enqueueCommittedCharactersForFallbackSuppression(text: String) {
        text.codePoints().forEach { codePoint ->
            characterFallbackSuppressionQueue += String(Character.toChars(codePoint))
        }
    }
}
