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

package androidx.compose.ui.text.font

import androidx.compose.ui.text.platform.LoadedFont
import androidx.compose.ui.text.platform.loadTypeface
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontSlant
import org.jetbrains.skia.FontStyle as SkFontStyle
import org.jetbrains.skia.FontWidth
import org.jetbrains.skia.Typeface as SkTypeface

/**
 * Create a WinUI font family resolver for use by WinUI compose owners.
 */
fun createFontFamilyResolver(): FontFamily.Resolver =
    FontFamilyResolverImpl(WinUIPlatformFontLoader())

@Suppress("DEPRECATION", "KmpDeprecationMismatch")
@Deprecated(
    "This exists to bridge existing Font.ResourceLoader APIs, and should be removed with them",
    replaceWith = ReplaceWith("createFontFamilyResolver()"),
)
internal actual fun createFontFamilyResolver(
    fontResourceLoader: Font.ResourceLoader
): FontFamily.Resolver = FontFamilyResolverImpl(WinUIPlatformFontLoader(fontResourceLoader))

private class WinUIPlatformFontLoader(
    private val resourceLoader: Font.ResourceLoader? = null,
) : PlatformFontLoader {
    @Suppress("DEPRECATION")
    override fun loadBlocking(font: Font): Any? =
        when (font) {
            is LoadedFont -> font.loadTypeface()
            else -> resourceLoader?.load(font) ?: defaultTypeface()
        }

    override suspend fun awaitLoad(font: Font): Any? = loadBlocking(font)

    override val cacheKey: Any? = resourceLoader
}

internal actual class PlatformFontFamilyTypefaceAdapter actual constructor() :
    FontFamilyTypefaceAdapter {
    actual override fun resolve(
        typefaceRequest: TypefaceRequest,
        platformFontLoader: PlatformFontLoader,
        onAsyncCompletion: (TypefaceResult.Immutable) -> Unit,
        createDefaultTypeface: (TypefaceRequest) -> Any,
    ): TypefaceResult? =
        TypefaceResult.Immutable(
            defaultTypeface(
                family = typefaceRequest.fontFamily,
                weight = typefaceRequest.fontWeight,
                style = typefaceRequest.fontStyle,
            )
        )
}

internal actual fun FontSynthesis.synthesizeTypeface(
    typeface: Any,
    font: Font,
    requestedWeight: FontWeight,
    requestedStyle: FontStyle,
): Any = typeface

private fun defaultTypeface(
    family: FontFamily? = null,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal,
): SkTypeface {
    val familyName = when (family) {
        FontFamily.Serif -> "Times New Roman"
        FontFamily.Monospace -> "Consolas"
        FontFamily.Cursive -> "Comic Sans MS"
        else -> "Segoe UI"
    }
    val skStyle = SkFontStyle(
        weight = weight.weight,
        width = FontWidth.NORMAL,
        slant = if (style == FontStyle.Italic) FontSlant.ITALIC else FontSlant.UPRIGHT,
    )
    return FontMgr.default.matchFamilyStyle(familyName, skStyle)
        ?: FontMgr.default.legacyMakeTypeface(familyName, skStyle)
        ?: FontMgr.default.matchFamilyStyle("Segoe UI", skStyle)
        ?: FontMgr.default.matchFamilyStyle("Arial", skStyle)
        ?: error("Unable to load WinUI platform font '$familyName'.")
}
