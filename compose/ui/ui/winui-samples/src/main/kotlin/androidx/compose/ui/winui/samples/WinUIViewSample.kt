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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WinUIComposeView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.WinUIInteropProperties
import androidx.compose.ui.viewinterop.WinUIView
import androidx.compose.ui.window.Application
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowBackdrop
import microsoft.ui.xaml.controls.Button
import microsoft.ui.xaml.controls.ContentControl
import microsoft.ui.xaml.controls.Grid
import kotlinx.coroutines.delay

@Composable
fun WinUIViewSampleContent(
    modifier: Modifier = Modifier,
    content: String = "Hello from Compose WinUI",
    isUserInteractionEnabled: Boolean = true,
    clipToBounds: Boolean = false,
    expectWindowFocus: Boolean = false,
    lifecycleProbe: WinUIViewLifecycleProbe? = null,
    onUpdated: ((Button) -> Unit)? = null,
) {
    ValidateWinUICompositionLocals(expectWindowFocus)
    WinUIView(
        modifier = modifier,
        properties = WinUIInteropProperties(
            isUserInteractionEnabled = isUserInteractionEnabled,
            clipToBounds = clipToBounds,
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

@Composable
private fun ValidateWinUICompositionLocals(expectWindowFocus: Boolean) {
    check(LocalDensity.current.density == 1f) {
        "WinUI LocalDensity was not provided by WinUIComposeView."
    }
    check(LocalLayoutDirection.current == LayoutDirection.Ltr) {
        "WinUI LocalLayoutDirection was not provided by WinUIComposeView."
    }
    check(LocalViewConfiguration.current.longPressTimeoutMillis > 0L) {
        "WinUI LocalViewConfiguration was not provided by WinUIComposeView."
    }
    check(LocalFontFamilyResolver.current.resolve().value != null) {
        "WinUI LocalFontFamilyResolver was not provided by WinUIComposeView."
    }
    val uriHandler = checkNotNull(LocalUriHandler.current) {
        "WinUI LocalUriHandler was not provided by WinUIComposeView."
    }
    check(runCatching { uriHandler.openUri("missing-scheme") }.isFailure) {
        "WinUI LocalUriHandler did not reject a URI without a scheme."
    }
    if (expectWindowFocus) {
        check(LocalWindowInfo.current.isWindowFocused) {
            "WinUI LocalWindowInfo did not reflect the active WinUI window."
        }
        check(LocalWindowInfo.current.containerSize.width > 0) {
            "WinUI LocalWindowInfo did not expose a positive container width."
        }
        check(LocalWindowInfo.current.containerDpSize.width.value > 0f) {
            "WinUI LocalWindowInfo did not expose a positive container dp width."
        }
    }
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
        var reuseSmokePassed by remember { mutableStateOf(false) }
        var windowSmokePassed by remember { mutableStateOf(false) }
        remember {
            println("compose-winui-sample: application created")
            runWinUIViewLifecycleSmoke()
            runWinUIViewZOrderSmoke()
            true
        }
        LaunchedEffect(Unit) {
            runWinUIViewReuseSmoke()
            reuseSmokePassed = true
        }
        LaunchedEffect(reuseSmokePassed, windowSmokePassed) {
            if (
                reuseSmokePassed &&
                windowSmokePassed &&
                java.lang.Boolean.getBoolean("compose.winui.sample.autoExit")
            ) {
                applicationScope.exitApplication()
            }
        }
        val windowProbe = remember {
            println("compose-winui-sample: window created")
            WinUIViewLifecycleProbe()
        }
        var title by remember { mutableStateOf("compose-winui sample") }
        var extendsContentIntoTitleBar by remember { mutableStateOf(false) }
        var backdrop: WindowBackdrop by remember { mutableStateOf(WindowBackdrop.Mica) }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            title = "compose-winui sample updated"
            extendsContentIntoTitleBar = true
            backdrop = WindowBackdrop.DesktopAcrylic
        }
        with(applicationScope) {
            Window(
                title = title,
                extendsContentIntoTitleBar = extendsContentIntoTitleBar,
                backdrop = backdrop,
            ) {
                var content by remember { mutableStateOf("Hello from Compose WinUI") }
                var initialBackdropPointer by remember { mutableStateOf<Long?>(null) }
                var lastButton by remember { mutableStateOf<Button?>(null) }
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    content = "Hello from Compose WinUI updated"
                }
                val backdropPointer = window.systemBackdrop.nativeObject.pointer.value
                check(backdropPointer != 0L) {
                    "WindowBackdrop.Mica did not install a readable WinUI SystemBackdrop."
                }
                if (initialBackdropPointer == null) {
                    initialBackdropPointer = backdropPointer
                }
                if (backdrop == WindowBackdrop.DesktopAcrylic) {
                    check(backdropPointer != initialBackdropPointer) {
                        "WindowBackdrop.DesktopAcrylic did not replace the initial backdrop."
                    }
                }
                LaunchedEffect(title, extendsContentIntoTitleBar, backdrop, lastButton) {
                    val button = lastButton ?: return@LaunchedEffect
                    if (
                        windowProbe.updateCount >= 2 &&
                        title == "compose-winui sample updated" &&
                        extendsContentIntoTitleBar &&
                        backdrop == WindowBackdrop.DesktopAcrylic
                    ) {
                        val appWindowWidth = window.appWindow.size.width
                        awaitCondition("unconstrained WinUIView natural width") {
                            button.actualWidth > 0.0 &&
                                button.actualWidth < appWindowWidth.toDouble()
                        }
                        windowSmokePassed = true
                    }
                }
                WinUIViewSampleContent(
                    content = content,
                    expectWindowFocus = true,
                    lifecycleProbe = windowProbe,
                    onUpdated = { button ->
                        composeView = button
                        lastButton = button
                        println(
                            "compose-winui-sample: compose button content=" +
                                (button.content ?: "not-found")
                        )
                        println("compose-winui-sample: window title=${window.title}")
                        println("compose-winui-sample: window content set")
                        println("compose-winui-sample: window activated")
                    },
                )
            }
        }
    }

    private fun runWinUIViewLifecycleSmoke() {
        val lifecycleProbe = WinUIViewLifecycleProbe()
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.root as ContentControl
        var showInterop = true
        currentComposeView.setContent {
            if (showInterop) {
                WinUIViewSampleContent(
                    modifier = fixedSizeAndPositionModifier(
                        width = 123,
                        height = 45,
                        x = 17,
                        y = 23,
                    ),
                    isUserInteractionEnabled = false,
                    clipToBounds = true,
                    lifecycleProbe = lifecycleProbe,
                )
            }
        }
        val rootGrid = checkNotNull(rootHost.content as? Grid) {
            "WinUIView lifecycle smoke did not install an interop Grid into the root host."
        }
        val wrapper = checkNotNull(rootGrid.children.singleOrNull()) {
            "WinUIView lifecycle smoke did not install a wrapper into the root host."
        }
        val button = checkNotNull(lifecycleProbe.lastButton) {
            "WinUIView lifecycle smoke did not install a Button into the wrapper."
        }
        check(!wrapper.isHitTestVisible) {
            "WinUIView did not apply isUserInteractionEnabled=false to the native wrapper."
        }
        check(!button.isHitTestVisible) {
            "WinUIView did not apply isUserInteractionEnabled=false to the native Button."
        }
        check(button.width == 123.0 && button.height == 45.0) {
            "WinUIView did not apply Compose size to the native Button: " +
                "${button.width}x${button.height}."
        }
        check(wrapper.clip.rect.width == 123f && wrapper.clip.rect.height == 45f) {
            "WinUIView did not apply clipToBounds to the native wrapper: " +
                "${wrapper.clip.rect.width}x${wrapper.clip.rect.height}."
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
        showInterop = false
        currentComposeView.setContent {
            if (showInterop) {
                WinUIViewSampleContent(
                    isUserInteractionEnabled = false,
                    lifecycleProbe = lifecycleProbe,
                )
            }
        }
        check(rootHost.content !is Grid) {
            "WinUIComposeView did not clear WinUIView content after it left composition."
        }
        check(lifecycleProbe.releaseCount == 1) {
            "Expected one WinUIView release call, got ${lifecycleProbe.releaseCount}."
        }
        showInterop = true
        currentComposeView.setContent {
            if (showInterop) {
                WinUIViewSampleContent(
                    isUserInteractionEnabled = false,
                    lifecycleProbe = lifecycleProbe,
                )
            }
        }
        val recreatedRootGrid = checkNotNull(rootHost.content as? Grid) {
            "WinUIView lifecycle smoke did not reinstall an interop Grid after re-entering composition."
        }
        val recreatedWrapper = checkNotNull(recreatedRootGrid.children.singleOrNull()) {
            "WinUIView lifecycle smoke did not recreate a wrapper after re-entering composition."
        }
        val recreatedButton = checkNotNull(lifecycleProbe.lastButton) {
            "WinUIView lifecycle smoke did not recreate a Button after re-entering composition."
        }
        check(!recreatedWrapper.nativeObject.sameIdentity(wrapper.nativeObject)) {
            "WinUIView reused a released wrapper without an explicit reusable group."
        }
        check(recreatedButton !== button) {
            "WinUIView reused a released Button without an explicit reusable group."
        }
        check(lifecycleProbe.factoryCount == 2) {
            "Expected a second WinUIView factory call after re-entering composition, got " +
                "${lifecycleProbe.factoryCount}."
        }
        check(lifecycleProbe.updateCount == 2) {
            "Expected a second WinUIView update call after re-entering composition, got " +
                "${lifecycleProbe.updateCount}."
        }
        currentComposeView.dispose()
        check(lifecycleProbe.releaseCount == 2) {
            "Expected the recreated WinUIView to release during host disposal, got " +
                "${lifecycleProbe.releaseCount}."
        }
        println(
            "compose-winui-sample: lifecycle after release factory=" +
                "${lifecycleProbe.factoryCount} update=${lifecycleProbe.updateCount} " +
                "reset=${lifecycleProbe.resetCount} release=${lifecycleProbe.releaseCount}"
        )
    }

    private fun runWinUIViewZOrderSmoke() {
        val firstProbe = WinUIViewLifecycleProbe()
        val secondProbe = WinUIViewLifecycleProbe()
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.root as ContentControl
        currentComposeView.setContent {
            WinUIViewSampleContent(
                content = "first",
                lifecycleProbe = firstProbe,
            )
            WinUIViewSampleContent(
                content = "second",
                lifecycleProbe = secondProbe,
            )
        }
        val rootGrid = checkNotNull(rootHost.content as? Grid) {
            "WinUIView z-order smoke did not install an interop Grid into the root host."
        }
        check(rootGrid.children.size == 2) {
            "WinUIView z-order smoke expected two native children, got ${rootGrid.children.size}."
        }
        val firstButton = checkNotNull(firstProbe.lastButton) {
            "WinUIView z-order smoke first factory did not create a Button."
        }
        val secondButton = checkNotNull(secondProbe.lastButton) {
            "WinUIView z-order smoke second factory did not create a Button."
        }
        check(firstButton.content == "first" && secondButton.content == "second") {
            "WinUIView z-order smoke did not update both Buttons."
        }
        currentComposeView.dispose()
        check(firstProbe.releaseCount == 1 && secondProbe.releaseCount == 1) {
            "WinUIView z-order smoke did not release both children: " +
                "first=${firstProbe.releaseCount} second=${secondProbe.releaseCount}."
        }
    }

    private suspend fun runWinUIViewReuseSmoke() {
        val lifecycleProbe = WinUIViewLifecycleProbe()
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.root as ContentControl
        val active: MutableState<Boolean> = mutableStateOf(true)
        currentComposeView.setContent {
            ReusableContentHost(active.value) {
                WinUIViewSampleContent(
                    isUserInteractionEnabled = false,
                    lifecycleProbe = lifecycleProbe,
                )
            }
        }
        val rootGrid = checkNotNull(rootHost.content as? Grid) {
            "WinUIView reuse smoke did not install an interop Grid into the root host."
        }
        val wrapper = checkNotNull(rootGrid.children.singleOrNull()) {
            "WinUIView reuse smoke did not install a wrapper into the root host."
        }
        val button = checkNotNull(lifecycleProbe.lastButton) {
            "WinUIView reuse smoke did not install a Button into the wrapper."
        }
        check(lifecycleProbe.factoryCount == 1) {
            "Expected one reusable WinUIView factory call, got ${lifecycleProbe.factoryCount}."
        }
        check(lifecycleProbe.updateCount == 1) {
            "Expected one reusable WinUIView update call, got ${lifecycleProbe.updateCount}."
        }
        active.value = false
        awaitCondition("reusable WinUIView deactivation") {
            lifecycleProbe.resetCount == 1 && rootHost.content !is Grid
        }
        check(lifecycleProbe.factoryCount == 1) {
            "Reusable WinUIView should not recreate while deactivating, got " +
                "${lifecycleProbe.factoryCount}."
        }
        check(lifecycleProbe.resetCount == 1) {
            "Expected one reusable WinUIView reset while deactivating, got " +
                "${lifecycleProbe.resetCount}."
        }
        check(lifecycleProbe.releaseCount == 0) {
            "Reusable WinUIView should not release while merely inactive, got " +
                "${lifecycleProbe.releaseCount}."
        }
        active.value = true
        awaitCondition("reusable WinUIView reactivation") {
            lifecycleProbe.updateCount == 2
        }
        check(
            (rootHost.content as? Grid)?.children?.singleOrNull()?.nativeObject
                ?.sameIdentity(wrapper.nativeObject) == true
        ) {
            "Reusable WinUIView did not keep its wrapper across deactivation."
        }
        val reattachedButton = checkNotNull(lifecycleProbe.lastButton) {
            "WinUIView reuse smoke did not reattach the Button after becoming active."
        }
        check(reattachedButton === button) {
            "Reusable WinUIView recreated the Button instead of reusing it."
        }
        check(lifecycleProbe.factoryCount == 1) {
            "Reusable WinUIView factory should still be one after reactivation, got " +
                "${lifecycleProbe.factoryCount}."
        }
        check(lifecycleProbe.updateCount == 2) {
            "Expected reusable WinUIView update after reactivation, got " +
                "${lifecycleProbe.updateCount}."
        }
        currentComposeView.dispose()
        check(lifecycleProbe.releaseCount == 1) {
            "Expected reusable WinUIView release during host disposal, got " +
                "${lifecycleProbe.releaseCount}."
        }
        println(
            "compose-winui-sample: reuse after release factory=" +
                "${lifecycleProbe.factoryCount} update=${lifecycleProbe.updateCount} " +
                "reset=${lifecycleProbe.resetCount} release=${lifecycleProbe.releaseCount}"
        )
    }

    private suspend fun awaitCondition(label: String, condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            delay(10)
        }
        check(condition()) {
            "Timed out waiting for $label."
        }
    }
}

private fun fixedSizeAndPositionModifier(width: Int, height: Int, x: Int, y: Int): Modifier =
    Modifier.layout { measurable, _ ->
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(x + width, y + height) {
            placeable.place(x, y)
        }
    }
