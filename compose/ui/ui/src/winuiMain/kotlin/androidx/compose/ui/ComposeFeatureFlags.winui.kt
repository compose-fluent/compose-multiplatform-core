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

package androidx.compose.ui

import androidx.compose.ui.window.Popup

internal enum class LayerType {
    OnSameCanvas,
    OnWindow;

    companion object {
        fun parse(property: String?): LayerType =
            when (property) {
                "WINDOW" -> OnWindow
                else -> OnSameCanvas
            }
    }
}

internal object ComposeFeatureFlags {
    /**
     * Indicates how popup layers are created.
     *
     * The default value is `OnSameCanvas`, which renders [Popup] content inside the Compose
     * root. `WINDOW` creates a WinUI native popup host so the popup is not clipped by the
     * Compose Skia surface.
     */
    val layerType = FeatureFlag {
        LayerType.parse(composeLayerTypeProperty())
    }
}

internal expect fun composeLayerTypeProperty(): String?

internal class FeatureFlag<T : Any>(defaultValueGetter: () -> T) {
    private val defaultValue: T by lazy(defaultValueGetter)
    private var override: T? = null
    val value: T
        get() = override ?: defaultValue

    inline fun withOverride(value: T, block: () -> Unit) {
        override = value
        try {
            block()
        } finally {
            override = null
        }
    }
}
