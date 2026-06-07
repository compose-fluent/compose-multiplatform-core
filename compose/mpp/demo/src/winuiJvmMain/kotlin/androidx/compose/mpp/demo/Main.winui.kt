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

package androidx.compose.mpp.demo

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Application
import androidx.compose.ui.window.Window
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun main(args: Array<String>) {
    Application {
        val applicationScope = this
        Window(
            title = "Compose MPP demo",
            onCloseRequest = { exitApplication() },
        ) {
            val fontFamilyResolver = LocalFontFamilyResolver.current
            val fontsLoaded = remember { mutableStateOf(false) }

            if (fontsLoaded.value) {
                ImageViewer()
            }

            LaunchedEffect(Unit) {
                val fontBytes = getResourceBytes("NotoColorEmoji.ttf")
                if (fontBytes != null) {
                    val fontFamily = FontFamily(listOf(Font("NotoColorEmoji", fontBytes)))
                    fontFamilyResolver.preload(fontFamily)
                }
                fontsLoaded.value = true
                if (java.lang.Boolean.getBoolean("compose.winui.mpp.sample.autoExit")) {
                    withFrameNanos { }
                    applicationScope.exitApplication()
                }
            }
        }
    }
}

suspend fun getResourceBytes(resourceName: String): ByteArray? = withContext(Dispatchers.IO) {
    val classLoader = Thread.currentThread().contextClassLoader
    try {
        classLoader.getResourceAsStream(resourceName).use { inputStream ->
            return@withContext inputStream?.readBytes()
        }
    } catch (e: IOException) {
        e.printStackTrace()
        return@withContext null
    }
}
