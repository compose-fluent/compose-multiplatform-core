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

package androidx.compose.ui.winui.samples

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.WinUIComposeView
import androidx.compose.ui.viewinterop.WinUIView
import io.github.composefluent.winrt.runtime.RuntimeScope
import io.github.composefluent.winrt.runtime.WinRtWinUiResourceManagerBootstrap
import io.github.composefluent.winrt.runtime.WinRtWindowsAppSdkBootstrap
import microsoft.ui.xaml.Application
import microsoft.ui.xaml.Window
import microsoft.ui.xaml.controls.Button

@Composable
fun WinUIViewSampleContent() {
    WinUIView(
        factory = {
            Button().apply {
                content = "Hello from WinUIView"
            }
        },
        update = { button ->
            button.content = "Hello from Compose WinUI"
        },
        onReset = { button ->
            button.content = "Reset WinUIView"
        },
        onRelease = { button ->
            button.content = null
        },
    )
}

fun main() {
    WinRtWindowsAppSdkBootstrap.initialize().use { bootstrap ->
        println("compose-winui-sample: WindowsAppSDK bootstrap=${bootstrap?.bootstrapDll ?: "not-found"}")
        RuntimeScope.initializeSingleThreaded().use {
            Application.start {
                println("compose-winui-sample: application callback invoked")
                ComposeWinUiSmokeApp.launch()
            }
        }
    }
}

private object ComposeWinUiSmokeApp {
    private var application: Application? = null
    private var window: Window? = null
    private var composeView: Any? = null
    private var resourceManagerRegistration: WinRtWinUiResourceManagerBootstrap.Registration? = null

    fun launch() {
        application = Application()
        println("compose-winui-sample: application created")
        val app = requireNotNull(application)
        resourceManagerRegistration = WinRtWinUiResourceManagerBootstrap.registerForApplication(app)
        println(
            "compose-winui-sample: resource manager registered=" +
                (resourceManagerRegistration != null)
        )
        val currentWindow = Window()
        println("compose-winui-sample: window created")
        currentWindow.title = "compose-winui sample"
        val rootButton = createSampleButtonHost()
        val currentComposeView = WinUIComposeView(rootButton) { content ->
            rootButton.content = content
        }
        composeView = currentComposeView
        currentComposeView.setContent {
            WinUIViewSampleContent()
        }
        println(
            "compose-winui-sample: compose button content=" +
                ((rootButton.content as? Button)?.content ?: "not-found")
        )
        currentWindow.content = currentComposeView.root
        window = currentWindow
        println("compose-winui-sample: window content set")
        currentWindow.activate()
        println("compose-winui-sample: window activated")
        if (java.lang.Boolean.getBoolean("compose.winui.sample.autoExit")) {
            app.exit()
        }
    }
}

private fun createSampleButtonHost(): Button {
    return Button()
}
