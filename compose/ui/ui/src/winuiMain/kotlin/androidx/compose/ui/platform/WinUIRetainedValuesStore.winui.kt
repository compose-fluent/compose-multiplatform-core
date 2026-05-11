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

import androidx.compose.runtime.retain.ManagedRetainedValuesStore
import androidx.compose.runtime.retain.RetainedValuesStore

internal class WinUIRetainedValuesStore(
    private val delegate: ManagedRetainedValuesStore = ManagedRetainedValuesStore(),
) : RetainedValuesStore by delegate {

    val isRetainingExitedValues: Boolean
        get() = delegate.isRetainingExitedValues

    init {
        delegate.onContentEnteredComposition()
    }

    fun startRetainingExitedValues() {
        if (!delegate.isRetainingExitedValues) {
            delegate.onContentExitComposition()
        }
    }

    fun stopRetainingExitedValues() {
        if (delegate.isRetainingExitedValues) {
            delegate.onContentEnteredComposition()
        }
    }

    fun dispose() {
        delegate.dispose()
    }

    override fun onContentEnteredComposition() = Unit

    override fun onContentExitComposition() = Unit
}
