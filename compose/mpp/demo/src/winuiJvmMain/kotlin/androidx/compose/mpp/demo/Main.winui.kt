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

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.WinUIComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.window.Application
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.currentComposeViewForTest
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.skiko.GraphicsApi

@OptIn(InternalComposeUiApi::class)
fun main(args: Array<String>) {
    val validation = WinUIMppSampleValidationReport.fromSystemProperties()
    Application {
        val applicationScope = this
        Window(
            title = "Compose MPP demo",
            onCloseRequest = { exitApplication() },
        ) {
            DisposableEffect(Unit) {
                validation.record("window-content-composed")
                onDispose {
                    validation.record("window-content-disposed")
                }
            }
            SideEffect {
                val size = appWindow.size
                validation.record("window-composed")
                if (size.width > 0 && size.height > 0) {
                    validation.record("window-positive-size")
                }
            }
            val fontFamilyResolver = LocalFontFamilyResolver.current
            val fontsLoaded = remember { mutableStateOf(false) }
            val composeView = currentComposeViewForTest
            val app = remember { App(initialScreenName = args.getOrNull(0)) }
            val navController = rememberNavController()

            if (fontsLoaded.value) {
                SideEffect {
                    validation.record("app-content-composed")
                }
                app.Content(navController)
            }

            LaunchedEffect(Unit) {
                val fontBytes = getResourceBytes("NotoColorEmoji.ttf")
                if (fontBytes != null) {
                    val fontFamily = FontFamily(listOf(Font("NotoColorEmoji", fontBytes)))
                    fontFamilyResolver.preload(fontFamily)
                    validation.record("font-resource-loaded")
                }
                fontsLoaded.value = true
                if (java.lang.Boolean.getBoolean("compose.winui.mpp.sample.autoTraverse")) {
                    runAutoTraversal(
                        navController = navController,
                        composeView = composeView,
                        validation = validation,
                    )
                }
                if (java.lang.Boolean.getBoolean("compose.winui.mpp.sample.autoExit")) {
                    if (composeView != null) {
                        validation.awaitRenderDiagnostics(composeView)
                    }
                    repeat(3) {
                        withFrameNanos { }
                        if (composeView != null) {
                            validation.recordRenderDiagnostics(composeView)
                        }
                    }
                    validation.record("frame-observed")
                    validation.record("exit-requested")
                    applicationScope.exitApplication()
                }
            }
        }
    }
}

@OptIn(InternalComposeUiApi::class)
private suspend fun runAutoTraversal(
    navController: NavHostController,
    composeView: WinUIComposeView?,
    validation: WinUIMppSampleValidationReport,
) {
    val targets = MainScreen.collectTraversalTargets()
    validation.record("autorun-start")
    validation.record("autorun-count:${targets.size}")
    settleAutoTraversalFrame(composeView, validation)
    targets.forEachIndexed { index, target ->
        validation.record("autorun-enter:$index:${target.path}")
        runCatching {
            navController.navigate(target.route) {
                launchSingleTop = true
                popUpTo(MainScreen.title) {
                    inclusive = false
                }
            }
        }.onFailure { throwable ->
            validation.record(
                "autorun-navigation-failed:$index:${target.path}:" +
                    "${throwable::class.qualifiedName}:${throwable.message}"
            )
            return@forEachIndexed
        }
        settleAutoTraversalFrame(composeView, validation)
        exercisePointerPath(composeView)
        settleAutoTraversalFrame(composeView, validation)
        validation.record("autorun-ok:$index:${target.path}")
    }
    validation.record("autorun-complete")
}

@OptIn(InternalComposeUiApi::class)
private suspend fun settleAutoTraversalFrame(
    composeView: WinUIComposeView?,
    validation: WinUIMppSampleValidationReport,
) {
    repeat(4) {
        withFrameNanos { }
        if (composeView != null) {
            validation.recordRenderDiagnostics(composeView)
        }
        delay(50)
    }
}

@OptIn(InternalComposeUiApi::class)
private suspend fun exercisePointerPath(composeView: WinUIComposeView?) {
    if (composeView == null) return
    val size = composeView.lastRenderSizeForTest ?: return
    if (size.width <= 0 || size.height <= 0) return
    val center = Offset(size.width * 0.5f, size.height * 0.62f)
    val dragEnd = Offset(size.width * 0.62f, size.height * 0.62f)

    composeView.sendMouseMoveForTest(center)
    composeView.sendMouseScrollForTest(center, Offset(0f, -3f))
    withFrameNanos { }
    composeView.sendMouseScrollForTest(center, Offset(0f, 3f))
    withFrameNanos { }
    composeView.sendMousePressForTest(center)
    composeView.sendMouseMoveForTest(dragEnd)
    composeView.sendMouseReleaseForTest(dragEnd)
}

private data class AutoTraversalTarget(
    val path: String,
    val route: String,
)

private fun Screen.collectTraversalTargets(
    prefix: List<String> = emptyList(),
): List<AutoTraversalTarget> {
    val path = prefix + title
    return when (this) {
        is Screen.Selection -> screens.flatMap { it.collectTraversalTargets(path) }
        else -> listOf(AutoTraversalTarget(path.joinToString("/"), title))
    }
}

@OptIn(InternalComposeUiApi::class)
private suspend fun WinUIMppSampleValidationReport.awaitRenderDiagnostics(
    composeView: WinUIComposeView,
): Boolean {
    repeat(100) {
        withFrameNanos { }
        if (recordRenderDiagnostics(composeView)) return true
        delay(50)
    }
    return recordRenderDiagnostics(composeView)
}

@OptIn(InternalComposeUiApi::class)
private fun WinUIMppSampleValidationReport.recordRenderDiagnostics(
    composeView: WinUIComposeView,
): Boolean {
    if (composeView.renderApiForTest == GraphicsApi.DIRECT3D) {
        record("render-direct3d")
    }
    val platformSize = composeView.lastRenderSizeForTest
    if (platformSize != null && platformSize.width > 0 && platformSize.height > 0) {
        record("render-positive-size")
    }
    if (platformSize != null && composeView.lastRenderedStateSizeForTest == platformSize) {
        record("render-state-size-matched")
    }
    val drawRect = composeView.lastDrawRectForTest
    if (drawRect.width > 0f && drawRect.height > 0f) {
        record("non-empty-draw-bounds")
    }
    if (composeView.renderFailureForTest != null) {
        record("render-failure")
    }
    return composeView.renderApiForTest == GraphicsApi.DIRECT3D &&
        platformSize != null &&
        platformSize.width > 0 &&
        platformSize.height > 0 &&
        composeView.lastRenderedStateSizeForTest == platformSize &&
        drawRect.width > 0f &&
        drawRect.height > 0f &&
        composeView.renderFailureForTest == null
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

private class WinUIMppSampleValidationReport(
    private val file: File?,
) {
    private val events = linkedSetOf<String>()

    fun record(event: String) {
        if (file == null) return
        synchronized(events) {
            if (!events.add(event)) return
            file.parentFile?.mkdirs()
            file.writeText(events.joinToString(separator = System.lineSeparator()))
        }
    }

    companion object {
        fun fromSystemProperties(): WinUIMppSampleValidationReport =
            WinUIMppSampleValidationReport(
                System.getProperty("compose.winui.mpp.sample.validationReport")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::File)
            )
    }
}
