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
    override fun loadBlocking(font: Font): Any? = resourceLoader?.load(font) ?: Any()

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
    ): TypefaceResult? = TypefaceResult.Immutable(Any())
}

internal actual fun FontSynthesis.synthesizeTypeface(
    typeface: Any,
    font: Font,
    requestedWeight: FontWeight,
    requestedStyle: FontStyle,
): Any = typeface
