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

package androidx.compose.ui.text.platform

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontSlant
import org.jetbrains.skia.FontStyle as SkFontStyle
import org.jetbrains.skia.FontWidth
import org.jetbrains.skia.Typeface as SkTypeface

class LoadedFont internal constructor(
    val identity: String,
    val data: ByteArray,
    override val weight: FontWeight,
    override val style: FontStyle,
    val variationSettings: FontVariation.Settings,
) : Font {
    override val loadingStrategy: FontLoadingStrategy = FontLoadingStrategy.Blocking

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LoadedFont) return false
        return identity == other.identity &&
            data.contentEquals(other.data) &&
            weight == other.weight &&
            style == other.style &&
            variationSettings == other.variationSettings
    }

    override fun hashCode(): Int {
        var result = identity.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + weight.hashCode()
        result = 31 * result + style.hashCode()
        result = 31 * result + variationSettings.hashCode()
        return result
    }

    override fun toString(): String =
        "LoadedFont(identity='$identity', weight=$weight, style=$style)"
}

fun Font(
    identity: String,
    data: ByteArray,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
): Font = LoadedFont(
    identity = identity,
    data = data,
    weight = weight,
    style = style,
    variationSettings = FontVariation.Settings(),
)

fun Font(
    identity: String,
    data: ByteArray,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
    variationSettings: FontVariation.Settings = FontVariation.Settings(weight, style),
): Font = LoadedFont(
    identity = identity,
    data = data,
    weight = weight,
    style = style,
    variationSettings = variationSettings,
)

internal fun LoadedFont.loadTypeface(): SkTypeface {
    val skStyle = SkFontStyle(
        weight = weight.weight,
        width = FontWidth.NORMAL,
        slant = if (style == FontStyle.Italic) FontSlant.ITALIC else FontSlant.UPRIGHT,
    )
    val typeface = FontMgr.default.makeFromData(Data.makeFromBytes(data))
        ?: FontMgr.default.legacyMakeTypeface(identity, skStyle)
        ?: FontMgr.default.matchFamilyStyle("Segoe UI", skStyle)
        ?: FontMgr.default.matchFamilyStyle("Arial", skStyle)
        ?: error("Unable to load WinUI font '$identity'.")
    if (variationSettings.settings.isEmpty()) return typeface
    return typeface.makeClone(
        variationSettings.settings.map { setting ->
            org.jetbrains.skia.FontVariation(setting.axisName, setting.toVariationValue(null))
        }.toTypedArray()
    )
}
