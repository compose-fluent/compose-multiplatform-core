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
import windows.ui.viewmanagement.UISettings

internal val WinUIViewConfiguration: ViewConfiguration = DefaultWinUIViewConfiguration()

internal class DefaultWinUIViewConfiguration(
    private val doubleClickTimeMillis: () -> Long = ::winUIDoubleClickTimeMillis,
) : ViewConfiguration {

    // Windows press-and-hold gestures use a 500 ms hold threshold.
    override val longPressTimeoutMillis: Long = 500L

    // Windows.UI.ViewManagement.UISettings exposes the user's system double-click time in ms.
    override val doubleTapTimeoutMillis: Long
        get() = doubleClickTimeMillis()

    // Compose requires a lower bound before accepting a second tap; WinUI does not expose one.
    override val doubleTapMinTimeMillis: Long = 40L

    // Windows gesture recognition uses an 8 effective pixel movement tolerance for touch holds.
    override val touchSlop: Float = 8f

    // WinUI Standard sizing aligns interactive items to 40x40 effective pixels.
    override val minimumTouchTargetSize: DpSize = DpSize(40.dp, 40.dp)
}

private val uiSettings by lazy(LazyThreadSafetyMode.PUBLICATION) { UISettings() }

private fun winUIDoubleClickTimeMillis(): Long =
    uiSettings.doubleClickTime.toLong()
