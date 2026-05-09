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
import androidx.compose.ui.viewinterop.WinUIInteropProperties
import androidx.compose.ui.viewinterop.WinUIView
import androidx.compose.ui.window.Application
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowBackdrop
import microsoft.ui.xaml.controls.Button
import microsoft.ui.xaml.controls.ContentControl

@Composable
fun WinUIViewSampleContent(
    content: String = "Hello from Compose WinUI",
    isUserInteractionEnabled: Boolean = true,
    lifecycleProbe: WinUIViewLifecycleProbe? = null,
    onUpdated: ((Button) -> Unit)? = null,
) {
    WinUIView(
        properties = WinUIInteropProperties(
            isUserInteractionEnabled = isUserInteractionEnabled,
        ),
        factory = {
            lifecycleProbe?.let { it.factoryCount += 1 }
            Button().apply {
                this.content = "Hello from WinUIView"
            }
        },
        update = { button ->
            lifecycleProbe?.let {
                it.updateCount += 1
                it.lastButton = button
            }
            button.content = content
            onUpdated?.invoke(button)
        },
        onReset = { button ->
            lifecycleProbe?.let { it.resetCount += 1 }
            button.content = "Reset WinUIView"
        },
        onRelease = { button ->
            lifecycleProbe?.let { it.releaseCount += 1 }
            lifecycleProbe?.lastButton = button
        },
    )
}

class WinUIViewLifecycleProbe {
    var factoryCount: Int = 0
    var updateCount: Int = 0
    var resetCount: Int = 0
    var releaseCount: Int = 0
    var lastButton: Button? = null
}

fun main() {
    println("compose-winui-sample: application starting")
    Application {
        ComposeWinUiSmokeApp.launch(this)
    }
}

private object ComposeWinUiSmokeApp {
    private var composeView: Any? = null

    @Composable
    fun launch(applicationScope: ApplicationScope) {
        println("compose-winui-sample: application created")
        runWinUIViewLifecycleSmoke()
        println("compose-winui-sample: window created")
        val windowProbe = WinUIViewLifecycleProbe()
        with(applicationScope) {
            Window(
                title = "compose-winui sample",
                extendsContentIntoTitleBar = false,
                backdrop = WindowBackdrop.Mica,
            ) {
                check(window.title == "compose-winui sample") {
                    "Window scope did not expose the configured WinUI Window title."
                }
                check(window.systemBackdrop.nativeObject.pointer.value != 0L) {
                    "WindowBackdrop.Mica did not install a readable WinUI SystemBackdrop."
                }
                WinUIViewSampleContent(
                    lifecycleProbe = windowProbe,
                    onUpdated = { button ->
                        composeView = button
                        println(
                            "compose-winui-sample: compose button content=" +
                                (button.content ?: "not-found")
                        )
                        println("compose-winui-sample: window content set")
                        println("compose-winui-sample: window activated")
                        if (java.lang.Boolean.getBoolean("compose.winui.sample.autoExit")) {
                            applicationScope.exitApplication()
                        }
                    },
                )
            }
        }
    }

    private fun runWinUIViewLifecycleSmoke() {
        val lifecycleProbe = WinUIViewLifecycleProbe()
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.root as ContentControl
        currentComposeView.setContent {
            WinUIViewSampleContent(
                isUserInteractionEnabled = false,
                lifecycleProbe = lifecycleProbe,
            )
        }
        val button = checkNotNull(rootHost.content as? Button) {
            "WinUIView lifecycle smoke did not install a Button into the root host."
        }
        check(!button.isHitTestVisible) {
            "WinUIView did not apply isUserInteractionEnabled=false to the native Button."
        }
        check(lifecycleProbe.factoryCount == 1) {
            "Expected one WinUIView factory call, got ${lifecycleProbe.factoryCount}."
        }
        check(lifecycleProbe.updateCount == 1) {
            "Expected one WinUIView update call, got ${lifecycleProbe.updateCount}."
        }
        check(lifecycleProbe.resetCount == 0) {
            "Expected no WinUIView reset calls before reuse/deactivation, got " +
                "${lifecycleProbe.resetCount}."
        }
        println(
            "compose-winui-sample: lifecycle before release factory=" +
                "${lifecycleProbe.factoryCount} update=${lifecycleProbe.updateCount} " +
                "reset=${lifecycleProbe.resetCount} release=${lifecycleProbe.releaseCount}"
        )
        currentComposeView.disposeComposition()
        check(rootHost.content !is Button) {
            "WinUIComposeView did not clear WinUIView content during disposal."
        }
        check(lifecycleProbe.releaseCount == 1) {
            "Expected one WinUIView release call, got ${lifecycleProbe.releaseCount}."
        }
        println(
            "compose-winui-sample: lifecycle after release factory=" +
                "${lifecycleProbe.factoryCount} update=${lifecycleProbe.updateCount} " +
                "reset=${lifecycleProbe.resetCount} release=${lifecycleProbe.releaseCount}"
        )
    }
}
