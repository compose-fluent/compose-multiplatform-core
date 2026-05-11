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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WinUIComposeView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.WinUIInteropProperties
import androidx.compose.ui.viewinterop.WinUIView
import androidx.compose.ui.window.Application
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowBackdrop
import microsoft.ui.xaml.controls.Button
import microsoft.ui.xaml.controls.Canvas
import microsoft.ui.xaml.controls.ContentControl
import microsoft.ui.xaml.controls.TextBox
import microsoft.ui.xaml.controls.ToggleSwitch
import microsoft.ui.xaml.UIElement
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
    ValidateWinUIClipboard()
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

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun ValidateWinUIClipboard() {
    val clipboardManager = LocalClipboardManager.current
    if (!WinUIClipboardSmokeState.managerPassed) {
        clipboardManager.setText(AnnotatedString("compose-winui clipboard manager"))
        val clipboardManagerText = clipboardManager.getText()?.text
        check(clipboardManagerText == "compose-winui clipboard manager") {
            "WinUI LocalClipboardManager did not round-trip text: actual=$clipboardManagerText."
        }
        WinUIClipboardSmokeState.managerPassed = true
    }
    val clipboard = LocalClipboard.current
    if (!WinUIClipboardSmokeState.clipboardPassed) {
        LaunchedEffect(clipboard) {
            if (WinUIClipboardSmokeState.clipboardPassed) return@LaunchedEffect
            // KWINRT-012: keep the WinRT clipboard smoke to one write per sample
            // run; repeated offscreen compositions can hit transient
            // OpenClipboard failures while still validating the Compose locals.
            WinUIClipboardSmokeState.clipboardPassed = true
            clipboard.setClipEntry(ClipEntry.withPlainText("compose-winui clipboard"))
            val clipboardText = clipboard.getClipEntry()?.getPlainText()
            check(clipboardText == "compose-winui clipboard") {
                "WinUI LocalClipboard did not round-trip plain text: actual=$clipboardText."
            }
        }
    }
}

private object WinUIClipboardSmokeState {
    var managerPassed: Boolean = false
    var clipboardPassed: Boolean = false
}

class WinUIViewLifecycleProbe {
    var factoryCount: Int = 0
    var updateCount: Int = 0
    var resetCount: Int = 0
    var releaseCount: Int = 0
    var lastButton: Button? = null
}

@Composable
private fun WinUIViewWindowIntegrationContent(
    buttonContent: String,
    toggleOn: Boolean,
    expectWindowFocus: Boolean,
    lifecycleProbe: WinUIViewLifecycleProbe,
    onButtonUpdated: (Button) -> Unit,
    onToggleSwitchUpdated: (ToggleSwitch) -> Unit,
) {
    ValidateWinUICompositionLocals(expectWindowFocus)
    WinUIView(
        modifier = fixedSizeAndPositionModifier(
            width = 160,
            height = 40,
            x = 0,
            y = 0,
        ),
        factory = {
            lifecycleProbe.factoryCount += 1
            Button()
        },
        update = { button ->
            lifecycleProbe.updateCount += 1
            lifecycleProbe.lastButton = button
            button.content = buttonContent
            onButtonUpdated(button)
        },
        onReset = { button ->
            lifecycleProbe.resetCount += 1
            button.content = "Reset WinUIView"
        },
        onRelease = { button ->
            lifecycleProbe.releaseCount += 1
            lifecycleProbe.lastButton = button
        },
    )
    // KWINRT-008: live TextBox resource setup is not stable yet; keep it in offscreen smoke.
    WinUIView(
        modifier = fixedSizeAndPositionModifier(
            width = 220,
            height = 40,
            x = 0,
            y = 48,
        ),
        factory = { ToggleSwitch() },
        update = { toggleSwitch ->
            toggleSwitch.isOn = toggleOn
            onToggleSwitchUpdated(toggleSwitch)
        },
    )
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
            runWinUIViewUnclippedBoundsSmoke()
            runWinUIViewControlVarietySmoke()
            true
        }
        LaunchedEffect(Unit) {
            runWinUIViewPlacementSmoke()
            runWinUIViewDensitySmoke()
            runWinUIViewReuseSmoke()
            runWinUIViewStateUpdateSmoke()
            runWinUIViewRelayoutSmoke()
            runWinUIViewPropertiesUpdateSmoke()
            runWinUIViewContainerSyncSmoke()
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
                var lastToggleSwitch by remember { mutableStateOf<ToggleSwitch?>(null) }
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
                LaunchedEffect(
                    title,
                    extendsContentIntoTitleBar,
                    backdrop,
                    lastButton,
                    lastToggleSwitch,
                ) {
                    val button = lastButton ?: return@LaunchedEffect
                    val toggleSwitch = lastToggleSwitch ?: return@LaunchedEffect
                    if (
                        windowProbe.updateCount >= 2 &&
                        button.content == "Hello from Compose WinUI updated" &&
                        toggleSwitch.isOn &&
                        title == "compose-winui sample updated" &&
                        extendsContentIntoTitleBar &&
                        backdrop == WindowBackdrop.DesktopAcrylic
                    ) {
                        windowSmokePassed = true
                    }
                }
                WinUIViewWindowIntegrationContent(
                    buttonContent = content,
                    toggleOn = content.endsWith("updated"),
                    expectWindowFocus = true,
                    lifecycleProbe = windowProbe,
                    onButtonUpdated = { button ->
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
                    onToggleSwitchUpdated = { toggleSwitch ->
                        lastToggleSwitch = toggleSwitch
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
        val rootCanvas = checkNotNull(rootHost.content as? Canvas) {
            "WinUIView lifecycle smoke did not install an interop Canvas into the root host."
        }
        val wrapper = checkNotNull(rootCanvas.children.singleOrNull()) {
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
        check(!button.isTabStop) {
            "WinUIView did not remove the native Button from Tab focus when disabled."
        }
        check(!button.isEnabled) {
            "WinUIView did not disable the native Button control when interaction was disabled."
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
        check(rootHost.content !is Canvas) {
            "WinUIComposeView did not clear WinUIView content after it left composition."
        }
        check(lifecycleProbe.releaseCount == 1) {
            "Expected one WinUIView release call, got ${lifecycleProbe.releaseCount}."
        }
        check(wrapper.isHitTestVisible) {
            "WinUIView did not restore wrapper hit testing on release."
        }
        check(button.isHitTestVisible && button.isTabStop && button.isEnabled) {
            "WinUIView did not restore native Button interaction state on release."
        }
        check(wrapper.readClipRectOrNull() == null) {
            "WinUIView did not clear the native wrapper clip on release."
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
        val recreatedRootCanvas = checkNotNull(rootHost.content as? Canvas) {
            "WinUIView lifecycle smoke did not reinstall an interop Canvas after re-entering composition."
        }
        val recreatedWrapper = checkNotNull(recreatedRootCanvas.children.singleOrNull()) {
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
        val rootCanvas = checkNotNull(rootHost.content as? Canvas) {
            "WinUIView z-order smoke did not install an interop Canvas into the root host."
        }
        check(rootCanvas.children.size == 2) {
            "WinUIView z-order smoke expected two native children, got ${rootCanvas.children.size}."
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

    private fun runWinUIViewUnclippedBoundsSmoke() {
        var lastNativeCanvas: Canvas? = null
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.root as ContentControl
        currentComposeView.setContent {
            WinUIView(
                modifier = fixedSizeAndPositionModifier(
                    width = 80,
                    height = 30,
                    x = 0,
                    y = 0,
                ),
                properties = WinUIInteropProperties(clipToBounds = true),
                factory = {
                    Canvas().apply {
                        width = 200.0
                        height = 90.0
                    }
                },
                update = {
                    lastNativeCanvas = it
                },
            )
        }
        val rootCanvas = checkNotNull(rootHost.content as? Canvas) {
            "WinUIView unclipped bounds smoke did not install an interop Canvas."
        }
        val wrapper = checkNotNull(rootCanvas.children.singleOrNull()) {
            "WinUIView unclipped bounds smoke did not install a wrapper."
        }
        val nativeCanvas = checkNotNull(lastNativeCanvas) {
            "WinUIView unclipped bounds smoke did not update the native Canvas."
        }
        check(nativeCanvas.width == 200.0 && nativeCanvas.height == 90.0) {
            "WinUIView child did not keep an unclipped native width: " +
                "${nativeCanvas.width}x${nativeCanvas.height}."
        }
        check(wrapper.clip.rect.width == 80f && wrapper.clip.rect.height == 30f) {
            "WinUIView wrapper clip did not match clipped Compose bounds: " +
                "${wrapper.clip.rect.width}x${wrapper.clip.rect.height}."
        }
        currentComposeView.dispose()
    }

    private suspend fun runWinUIViewContainerSyncSmoke() {
        val firstProbe = WinUIViewLifecycleProbe()
        val secondProbe = WinUIViewLifecycleProbe()
        val thirdProbe = WinUIViewLifecycleProbe()
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.root as ContentControl
        val includeSecond: MutableState<Boolean> = mutableStateOf(true)

        currentComposeView.setContent {
            WinUIViewSampleContent(
                content = "first",
                lifecycleProbe = firstProbe,
            )
            if (includeSecond.value) {
                WinUIViewSampleContent(
                    content = "second",
                    lifecycleProbe = secondProbe,
                )
            }
            WinUIViewSampleContent(
                content = "third",
                lifecycleProbe = thirdProbe,
            )
        }
        awaitCondition("WinUIView container initial order") {
            (rootHost.content as? Canvas)?.children?.size == 3
        }
        val rootCanvas = checkNotNull(rootHost.content as? Canvas) {
            "WinUIView container sync smoke did not install an interop Canvas."
        }
        val firstWrapper = rootCanvas.children[0]
        val secondWrapper = rootCanvas.children[1]
        val thirdWrapper = rootCanvas.children[2]
        assertInteropRootOrder(
            rootCanvas = rootCanvas,
            expected = listOf(firstWrapper, secondWrapper, thirdWrapper),
            label = "initial order",
        )

        includeSecond.value = false
        awaitCondition("WinUIView container middle removal") {
            secondProbe.releaseCount == 1 &&
                hasInteropRootOrder(rootCanvas, listOf(firstWrapper, thirdWrapper))
        }
        assertInteropRootOrder(
            rootCanvas = rootCanvas,
            expected = listOf(firstWrapper, thirdWrapper),
            label = "middle removal order",
        )
        check(secondProbe.releaseCount == 1) {
            "WinUIView container sync smoke did not release the removed child, got " +
                "${secondProbe.releaseCount}."
        }

        includeSecond.value = true
        awaitCondition("WinUIView container middle insertion") {
            rootCanvas.children.size == 3 &&
                secondProbe.factoryCount == 2 &&
                secondProbe.releaseCount == 1
        }
        val reinsertedSecondWrapper = rootCanvas.children[1]
        assertInteropRootOrder(
            rootCanvas = rootCanvas,
            expected = listOf(firstWrapper, reinsertedSecondWrapper, thirdWrapper),
            label = "middle insertion order",
        )
        check(!reinsertedSecondWrapper.nativeObject.sameIdentity(secondWrapper.nativeObject)) {
            "WinUIView container sync smoke reused a released middle wrapper."
        }

        currentComposeView.dispose()
        check(
            firstProbe.releaseCount == 1 &&
                secondProbe.releaseCount == 2 &&
                thirdProbe.releaseCount == 1
        ) {
            "WinUIView container sync smoke did not release remaining children: " +
                "first=${firstProbe.releaseCount} second=${secondProbe.releaseCount} " +
                "third=${thirdProbe.releaseCount}."
        }
    }

    private fun runWinUIViewControlVarietySmoke() {
        var buttonFactoryCount = 0
        var textBoxFactoryCount = 0
        var toggleFactoryCount = 0
        var releaseCount = 0
        var lastButton: Button? = null
        var lastTextBox: TextBox? = null
        var lastToggleSwitch: ToggleSwitch? = null
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.root as ContentControl
        currentComposeView.setContent {
            WinUIView(
                modifier = fixedSizeAndPositionModifier(
                    width = 120,
                    height = 40,
                    x = 0,
                    y = 0,
                ),
                factory = {
                    buttonFactoryCount += 1
                    Button()
                },
                onRelease = {
                    releaseCount += 1
                },
                update = {
                    it.content = "button"
                    lastButton = it
                },
            )
            WinUIView(
                modifier = fixedSizeAndPositionModifier(
                    width = 180,
                    height = 40,
                    x = 0,
                    y = 48,
                ),
                factory = {
                    textBoxFactoryCount += 1
                    TextBox()
                },
                onRelease = {
                    releaseCount += 1
                },
                update = {
                    it.text = "text box"
                    lastTextBox = it
                },
            )
            WinUIView(
                modifier = fixedSizeAndPositionModifier(
                    width = 180,
                    height = 40,
                    x = 0,
                    y = 96,
                ),
                factory = {
                    toggleFactoryCount += 1
                    ToggleSwitch()
                },
                onRelease = {
                    releaseCount += 1
                },
                update = {
                    it.isOn = true
                    lastToggleSwitch = it
                },
            )
        }
        val rootCanvas = checkNotNull(rootHost.content as? Canvas) {
            "WinUIView control variety smoke did not install an interop Canvas into the root host."
        }
        check(rootCanvas.children.size == 3) {
            "WinUIView control variety smoke expected three native children, got " +
                "${rootCanvas.children.size}."
        }
        check(buttonFactoryCount == 1 && textBoxFactoryCount == 1 && toggleFactoryCount == 1) {
            "WinUIView control variety smoke did not create each WinUI control exactly once: " +
                "button=$buttonFactoryCount textBox=$textBoxFactoryCount toggle=$toggleFactoryCount."
        }
        check(lastButton?.content == "button") {
            "WinUIView control variety smoke did not update Button content."
        }
        check(lastTextBox?.text == "text box") {
            "WinUIView control variety smoke did not update TextBox text."
        }
        check(lastToggleSwitch?.isOn == true) {
            "WinUIView control variety smoke did not update ToggleSwitch isOn."
        }
        currentComposeView.dispose()
        check(releaseCount == 3) {
            "WinUIView control variety smoke did not release all controls, got $releaseCount."
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
        val rootCanvas = checkNotNull(rootHost.content as? Canvas) {
            "WinUIView reuse smoke did not install an interop Canvas into the root host."
        }
        val wrapper = checkNotNull(rootCanvas.children.singleOrNull()) {
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
            lifecycleProbe.resetCount == 1 && rootHost.content !is Canvas
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
            (rootHost.content as? Canvas)?.children?.singleOrNull()?.nativeObject
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

    private suspend fun runWinUIViewStateUpdateSmoke() {
        val lifecycleProbe = WinUIViewLifecycleProbe()
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.root as ContentControl
        val content: MutableState<String> = mutableStateOf("state update initial")
        currentComposeView.setContent {
            WinUIViewSampleContent(
                content = content.value,
                lifecycleProbe = lifecycleProbe,
            )
        }
        awaitCondition("WinUIView initial state update") {
            lifecycleProbe.updateCount == 1 &&
                lifecycleProbe.lastButton?.content == "state update initial" &&
                (rootHost.content as? Canvas)?.children?.size == 1
        }
        val button = checkNotNull(lifecycleProbe.lastButton) {
            "WinUIView state update smoke did not install a Button."
        }

        content.value = "state update changed"
        awaitCondition("WinUIView repeated state update") {
            lifecycleProbe.updateCount == 2 &&
                lifecycleProbe.lastButton === button &&
                button.content == "state update changed"
        }
        check(lifecycleProbe.factoryCount == 1) {
            "WinUIView state update smoke recreated the Button, factory=" +
                "${lifecycleProbe.factoryCount}."
        }
        check(lifecycleProbe.releaseCount == 0) {
            "WinUIView state update smoke released the Button during recomposition, release=" +
                "${lifecycleProbe.releaseCount}."
        }

        currentComposeView.dispose()
        check(lifecycleProbe.releaseCount == 1) {
            "WinUIView state update smoke did not release on host disposal, release=" +
                "${lifecycleProbe.releaseCount}."
        }
    }

    private suspend fun runWinUIViewRelayoutSmoke() {
        val lifecycleProbe = WinUIViewLifecycleProbe()
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.root as ContentControl
        val width: MutableState<Int> = mutableStateOf(80)
        val height: MutableState<Int> = mutableStateOf(30)
        val x: MutableState<Int> = mutableStateOf(4)
        val y: MutableState<Int> = mutableStateOf(6)
        currentComposeView.setContent {
            WinUIViewSampleContent(
                modifier = fixedSizeAndPositionModifier(
                    width = width.value,
                    height = height.value,
                    x = x.value,
                    y = y.value,
                ),
                clipToBounds = true,
                lifecycleProbe = lifecycleProbe,
            )
        }
        awaitCondition("WinUIView initial relayout bounds") {
            val rootCanvas = rootHost.content as? Canvas
            // KWINRT-009: collection-returned UIElement wrappers cannot be publicly rewrapped
            // as FrameworkElement/Canvas, so this smoke validates clip plus the user view size.
            val wrapper = rootCanvas?.children?.singleOrNull()
            wrapper?.clip?.rect?.width == 80f &&
                wrapper.clip.rect.height == 30f &&
                lifecycleProbe.lastButton?.width == 80.0 &&
                lifecycleProbe.lastButton?.height == 30.0
        }
        val rootCanvas = checkNotNull(rootHost.content as? Canvas) {
            "WinUIView relayout smoke did not install an interop Canvas."
        }
        val wrapper = checkNotNull(rootCanvas.children.singleOrNull()) {
            "WinUIView relayout smoke did not install a wrapper."
        }
        val button = checkNotNull(lifecycleProbe.lastButton) {
            "WinUIView relayout smoke did not install a Button."
        }

        width.value = 140
        height.value = 55
        x.value = 11
        y.value = 17
        awaitCondition("WinUIView updated relayout bounds") {
            val currentWrapper = (rootHost.content as? Canvas)?.children?.singleOrNull()
            currentWrapper?.nativeObject?.sameIdentity(wrapper.nativeObject) == true &&
                lifecycleProbe.lastButton === button &&
                currentWrapper.clip.rect.width == 140f &&
                currentWrapper.clip.rect.height == 55f &&
                button.width == 140.0 &&
                button.height == 55.0
        }
        check(lifecycleProbe.factoryCount == 1) {
            "WinUIView relayout smoke recreated the Button, factory=" +
                "${lifecycleProbe.factoryCount}."
        }
        check(lifecycleProbe.releaseCount == 0) {
            "WinUIView relayout smoke released the Button during relayout, release=" +
                "${lifecycleProbe.releaseCount}."
        }

        currentComposeView.dispose()
        check(lifecycleProbe.releaseCount == 1) {
            "WinUIView relayout smoke did not release on host disposal, release=" +
                "${lifecycleProbe.releaseCount}."
        }
    }

    private suspend fun runWinUIViewPlacementSmoke() {
        val lifecycleProbe = WinUIViewLifecycleProbe()
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.root as ContentControl
        val isPlaced: MutableState<Boolean> = mutableStateOf(true)
        currentComposeView.setContent {
            ConditionalPlacement(isPlaced.value) {
                WinUIViewSampleContent(
                    lifecycleProbe = lifecycleProbe,
                )
            }
        }
        awaitCondition("WinUIView initial placement") {
            lifecycleProbe.lastButton != null &&
                (rootHost.content as? Canvas)?.children?.size == 1
        }
        val rootCanvas = checkNotNull(rootHost.content as? Canvas) {
            "WinUIView placement smoke did not install an interop Canvas."
        }
        val wrapper = checkNotNull(rootCanvas.children.singleOrNull()) {
            "WinUIView placement smoke did not install a wrapper."
        }
        val button = checkNotNull(lifecycleProbe.lastButton) {
            "WinUIView placement smoke did not install a Button."
        }

        isPlaced.value = false
        awaitCondition("WinUIView unplacement") {
            rootHost.content !is Canvas
        }
        check(lifecycleProbe.factoryCount == 1) {
            "WinUIView placement smoke recreated the Button during unplacement, factory=" +
                "${lifecycleProbe.factoryCount}."
        }
        check(lifecycleProbe.releaseCount == 0) {
            "WinUIView placement smoke released the Button during unplacement, release=" +
                "${lifecycleProbe.releaseCount}."
        }

        isPlaced.value = true
        awaitCondition("WinUIView replacement") {
            (rootHost.content as? Canvas)?.children?.singleOrNull()?.nativeObject
                ?.sameIdentity(wrapper.nativeObject) == true &&
                lifecycleProbe.lastButton === button
        }
        check(lifecycleProbe.factoryCount == 1) {
            "WinUIView placement smoke recreated the Button during replacement, factory=" +
                "${lifecycleProbe.factoryCount}."
        }
        check(lifecycleProbe.releaseCount == 0) {
            "WinUIView placement smoke released the Button before disposal, release=" +
                "${lifecycleProbe.releaseCount}."
        }

        currentComposeView.dispose()
        check(lifecycleProbe.releaseCount == 1) {
            "WinUIView placement smoke did not release on host disposal, release=" +
                "${lifecycleProbe.releaseCount}."
        }
        println(
            "compose-winui-sample: placement toggle factory=" +
                "${lifecycleProbe.factoryCount} update=${lifecycleProbe.updateCount} " +
                "release=${lifecycleProbe.releaseCount}"
        )
    }

    private suspend fun runWinUIViewDensitySmoke() {
        val lifecycleProbe = WinUIViewLifecycleProbe()
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.root as ContentControl
        val localDensity: MutableState<Float> = mutableStateOf(1f)
        var observedDensity = 0f
        currentComposeView.setContent {
            CompositionLocalProvider(LocalDensity provides Density(localDensity.value)) {
                WinUIView(
                    modifier = Modifier.layout { measurable, _ ->
                        observedDensity = this.density
                        val placeable = measurable.measure(
                            Constraints.fixed(
                                width = maxOf(1, (40f * this.density).toInt()),
                                height = maxOf(1, (20f * this.density).toInt()),
                            )
                        )
                        layout(placeable.width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    },
                    properties = WinUIInteropProperties(clipToBounds = true),
                    factory = {
                        lifecycleProbe.factoryCount += 1
                        Button().apply {
                            width = 1.0
                            height = 1.0
                        }
                    },
                    update = { button ->
                        lifecycleProbe.updateCount += 1
                        lifecycleProbe.lastButton = button
                    },
                    onReset = { lifecycleProbe.resetCount += 1 },
                    onRelease = { button ->
                        lifecycleProbe.releaseCount += 1
                        lifecycleProbe.lastButton = button
                    },
                )
            }
        }
        awaitCondition("WinUIView initial density") {
            val wrapper = (rootHost.content as? Canvas)?.children?.singleOrNull()
            val clip = wrapper?.readClipRectOrNull()
            observedDensity == 1f &&
                lifecycleProbe.lastButton != null &&
                clip?.width == 40f &&
                clip?.height == 20f &&
                lifecycleProbe.lastButton?.width == 40.0 &&
                lifecycleProbe.lastButton?.height == 20.0
        }
        val wrapper = checkNotNull((rootHost.content as? Canvas)?.children?.singleOrNull()) {
            "WinUIView density smoke did not install a wrapper."
        }
        val button = checkNotNull(lifecycleProbe.lastButton) {
            "WinUIView density smoke did not install a Button."
        }

        localDensity.value = 2f
        awaitCondition("WinUIView updated density") {
            val currentWrapper = (rootHost.content as? Canvas)?.children?.singleOrNull()
            val clip = currentWrapper?.readClipRectOrNull()
            currentWrapper?.nativeObject?.sameIdentity(wrapper.nativeObject) == true &&
                observedDensity == 2f &&
                lifecycleProbe.lastButton === button &&
                clip?.width == 80f &&
                clip?.height == 40f &&
                button.width == 80.0 &&
                button.height == 40.0
        }
        check(lifecycleProbe.factoryCount == 1) {
            "WinUIView density smoke recreated the Button, factory=" +
                "${lifecycleProbe.factoryCount}."
        }
        check(lifecycleProbe.releaseCount == 0) {
            "WinUIView density smoke released the Button during density update, release=" +
                "${lifecycleProbe.releaseCount}."
        }

        currentComposeView.dispose()
        check(lifecycleProbe.releaseCount == 1) {
            "WinUIView density smoke did not release on host disposal, release=" +
                "${lifecycleProbe.releaseCount}."
        }
        println(
            "compose-winui-sample: density update factory=" +
                "${lifecycleProbe.factoryCount} update=${lifecycleProbe.updateCount} " +
                "release=${lifecycleProbe.releaseCount}"
        )
    }

    private suspend fun runWinUIViewPropertiesUpdateSmoke() {
        val lifecycleProbe = WinUIViewLifecycleProbe()
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.root as ContentControl
        val clipToBounds: MutableState<Boolean> = mutableStateOf(true)
        val isUserInteractionEnabled: MutableState<Boolean> = mutableStateOf(false)
        currentComposeView.setContent {
            WinUIViewSampleContent(
                modifier = fixedSizeAndPositionModifier(
                    width = 90,
                    height = 35,
                    x = 0,
                    y = 0,
                ),
                isUserInteractionEnabled = isUserInteractionEnabled.value,
                clipToBounds = clipToBounds.value,
                lifecycleProbe = lifecycleProbe,
            )
        }
        awaitCondition("WinUIView initial properties") {
            val wrapper = (rootHost.content as? Canvas)?.children?.singleOrNull()
            val clip = wrapper?.readClipRectOrNull()
            wrapper?.isHitTestVisible == false &&
                lifecycleProbe.lastButton?.isHitTestVisible == false &&
                lifecycleProbe.lastButton?.isTabStop == false &&
                lifecycleProbe.lastButton?.isEnabled == false &&
                clip?.width == 90f &&
                clip.height == 35f
        }
        val wrapper = checkNotNull((rootHost.content as? Canvas)?.children?.singleOrNull()) {
            "WinUIView properties smoke did not install a wrapper."
        }
        val button = checkNotNull(lifecycleProbe.lastButton) {
            "WinUIView properties smoke did not install a Button."
        }

        clipToBounds.value = false
        isUserInteractionEnabled.value = true
        awaitCondition("WinUIView updated properties") {
            val currentWrapper = (rootHost.content as? Canvas)?.children?.singleOrNull()
            currentWrapper?.nativeObject?.sameIdentity(wrapper.nativeObject) == true &&
                lifecycleProbe.lastButton === button &&
                currentWrapper.isHitTestVisible &&
                button.isHitTestVisible &&
                button.isTabStop &&
                button.isEnabled &&
                currentWrapper.readClipRectOrNull() == null
        }
        check(lifecycleProbe.factoryCount == 1) {
            "WinUIView properties smoke recreated the Button, factory=" +
                "${lifecycleProbe.factoryCount}."
        }
        check(lifecycleProbe.releaseCount == 0) {
            "WinUIView properties smoke released the Button during property update, release=" +
                "${lifecycleProbe.releaseCount}."
        }

        currentComposeView.dispose()
        check(lifecycleProbe.releaseCount == 1) {
            "WinUIView properties smoke did not release on host disposal, release=" +
                "${lifecycleProbe.releaseCount}."
        }
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

private fun assertInteropRootOrder(
    rootCanvas: Canvas,
    expected: List<UIElement>,
    label: String,
) {
    check(rootCanvas.children.size == expected.size) {
        "WinUIView container sync smoke expected ${expected.size} children for $label, got " +
            "${rootCanvas.children.size}."
    }
    check(hasInteropRootOrder(rootCanvas, expected)) {
        "WinUIView container sync smoke had incorrect child order for $label."
    }
}

private fun hasInteropRootOrder(rootCanvas: Canvas, expected: List<UIElement>): Boolean {
    if (rootCanvas.children.size != expected.size) return false
    return expected.indices.all { index ->
        rootCanvas.children[index].nativeObject.sameIdentity(expected[index].nativeObject)
    }
}

private fun UIElement.readClipRectOrNull() =
    runCatching { clip.rect }.getOrNull()

private fun fixedSizeAndPositionModifier(width: Int, height: Int, x: Int, y: Int): Modifier =
    Modifier.layout { measurable, _ ->
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(x + width, y + height) {
            placeable.place(x, y)
        }
    }

@Composable
private fun ConditionalPlacement(
    isPlaced: Boolean,
    content: @Composable () -> Unit,
) {
    Layout(content = content) { measurables, constraints ->
        val placeable = measurables.single().measure(constraints)
        layout(placeable.width, placeable.height) {
            if (isPlaced) {
                placeable.place(0, 0)
            }
        }
    }
}
