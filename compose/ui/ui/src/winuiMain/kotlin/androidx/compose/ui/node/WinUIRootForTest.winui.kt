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

package androidx.compose.ui.node

import androidx.compose.ui.input.indirect.IndirectPointerEvent
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.text.input.TextInputService
import androidx.compose.ui.unit.Density

internal class WinUIRootForTest(
    private val densityProvider: () -> Density,
    private val semanticsOwnerProvider: () -> SemanticsOwner,
    private val textInputServiceProvider: () -> TextInputService,
    private val sendKeyEvent: (KeyEvent) -> Boolean,
    private val sendIndirectPointerEvent: (IndirectPointerEvent) -> Boolean,
    private val measureAndLayout: () -> Unit,
    private val setUncaughtExceptionHandler: (RootForTest.UncaughtExceptionHandler?) -> Unit,
    private val forceAccessibilityForTesting: (Boolean) -> Unit,
    private val setAccessibilityEventBatchIntervalMillis: (Long) -> Unit,
) : RootForTest {
    override val density: Density get() = densityProvider()
    override val semanticsOwner: SemanticsOwner get() = semanticsOwnerProvider()
    @Suppress("DEPRECATION")
    override val textInputService: TextInputService get() = textInputServiceProvider()

    override fun sendKeyEvent(keyEvent: KeyEvent): Boolean = sendKeyEvent.invoke(keyEvent)

    override fun sendIndirectPointerEvent(indirectPointerEvent: IndirectPointerEvent): Boolean =
        sendIndirectPointerEvent.invoke(indirectPointerEvent)

    override fun measureAndLayoutForTest() {
        measureAndLayout()
    }

    override fun setUncaughtExceptionHandler(handler: RootForTest.UncaughtExceptionHandler?) {
        setUncaughtExceptionHandler.invoke(handler)
    }

    override fun forceAccessibilityForTesting(enable: Boolean) {
        forceAccessibilityForTesting.invoke(enable)
    }

    override fun setAccessibilityEventBatchIntervalMillis(intervalMillis: Long) {
        setAccessibilityEventBatchIntervalMillis.invoke(intervalMillis)
    }
}
