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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.autoSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.retain.ForgetfulRetainedValuesStore
import androidx.compose.runtime.retain.LocalRetainedValuesStore
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.FillableData
import androidx.compose.ui.autofill.createFromBoolean
import androidx.compose.ui.autofill.createFromDateMillis
import androidx.compose.ui.autofill.createFromListIndex
import androidx.compose.ui.autofill.createFromText
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.indirect.IndirectPointerEvent
import androidx.compose.ui.input.indirect.IndirectPointerEventPrimaryDirectionalMotionAxis
import androidx.compose.ui.input.indirect.IndirectPointerEventType
import androidx.compose.ui.input.indirect.IndirectPointerInputChange
import androidx.compose.ui.input.indirect.IndirectPointerInputModifierNode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.OnPlacedModifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputModifierNode
import androidx.compose.ui.platform.WinUIComposeView
import androidx.compose.ui.platform.establishTextInputSession
import androidx.compose.ui.platform.sendPointerEventForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.sensitiveContent
import androidx.compose.ui.spatial.RelativeLayoutBounds
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.WinUIInteropProperties
import androidx.compose.ui.viewinterop.WinUIView
import androidx.compose.ui.window.Application
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowBackdrop
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import microsoft.ui.xaml.controls.Button
import microsoft.ui.xaml.controls.Canvas
import microsoft.ui.xaml.controls.ContentControl
import microsoft.ui.xaml.controls.TextBox
import microsoft.ui.xaml.controls.ToggleSwitch
import microsoft.ui.xaml.UIElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
            lifecycleProbe?.lastContent = button.content as? String
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
private fun ValidateWinUICompositionLocals(
    expectWindowFocus: Boolean,
    allowWindowFocusChanges: Boolean = false,
) {
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
    check(LocalLifecycleOwner.current.lifecycle.currentState == Lifecycle.State.RESUMED) {
        "WinUI LocalLifecycleOwner was not provided in a resumed state."
    }
    check(LocalSavedStateRegistryOwner.current != null) {
        "WinUI LocalSavedStateRegistryOwner was not provided."
    }
    check(LocalViewModelStoreOwner.current != null) {
        "WinUI LocalViewModelStoreOwner was not provided."
    }
    check(LocalSaveableStateRegistry.current != null) {
        "WinUI LocalSaveableStateRegistry was not provided."
    }
    check(LocalRetainedValuesStore.current !== ForgetfulRetainedValuesStore) {
        "WinUI LocalRetainedValuesStore was not provided."
    }
    val uriHandler = checkNotNull(LocalUriHandler.current) {
        "WinUI LocalUriHandler was not provided by WinUIComposeView."
    }
    check(runCatching { uriHandler.openUri("missing-scheme") }.isFailure) {
        "WinUI LocalUriHandler did not reject a URI without a scheme."
    }
    ValidateWinUIClipboard()
    ValidateWinUIFillableData()
    if (expectWindowFocus) {
        val windowInfo = LocalWindowInfo.current
        if (!allowWindowFocusChanges) {
            check(windowInfo.isWindowFocused) {
                "WinUI LocalWindowInfo did not reflect the active WinUI window."
            }
        }
        check(windowInfo.containerSize.width > 0) {
            "WinUI LocalWindowInfo did not expose a positive container width."
        }
        check(windowInfo.containerDpSize.width.value > 0f) {
            "WinUI LocalWindowInfo did not expose a positive container dp width."
        }
    }
}

private fun ValidateWinUIFillableData() {
    val textData = checkNotNull(FillableData.createFromText("compose-winui autofill")) {
        "WinUI FillableData did not create text data."
    }
    check(textData.textValue?.toString() == "compose-winui autofill") {
        "WinUI FillableData did not expose text value."
    }
    val booleanData = checkNotNull(FillableData.createFromBoolean(true)) {
        "WinUI FillableData did not create boolean data."
    }
    check(booleanData.booleanValue == true) {
        "WinUI FillableData did not expose boolean value."
    }
    val listData = checkNotNull(FillableData.createFromListIndex(3)) {
        "WinUI FillableData did not create list index data."
    }
    check(listData.getListIndexOrDefault(-1) == 3) {
        "WinUI FillableData did not expose list index value."
    }
    val dateData = checkNotNull(FillableData.createFromDateMillis(1234L)) {
        "WinUI FillableData did not create date millis data."
    }
    check(dateData.getDateMillisOrDefault(-1L) == 1234L) {
        "WinUI FillableData did not expose date millis value."
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
            // Keep the WinRT clipboard smoke to one write per sample run; clipboard ownership can
            // be transiently locked by another process while still validating the Compose locals.
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
    var lastContent: String? = null
}

@Composable
private fun WinUIViewWindowIntegrationContent(
    buttonContent: String,
    toggleOn: Boolean,
    expectWindowFocus: Boolean,
    lifecycleProbe: WinUIViewLifecycleProbe,
    onButtonUpdated: (Button) -> Unit,
    onTextBoxUpdated: (TextBox) -> Unit,
    onToggleSwitchUpdated: (ToggleSwitch) -> Unit,
    onWindowFocusChanged: ((Boolean) -> Unit)? = null,
) {
    ValidateWinUICompositionLocals(
        expectWindowFocus = expectWindowFocus,
        allowWindowFocusChanges = onWindowFocusChanged != null,
    )
    if (expectWindowFocus && onWindowFocusChanged != null) {
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        DisposableEffect(windowFocused) {
            onWindowFocusChanged(windowFocused)
            onDispose {}
        }
    }
    ValidateWinUIOwnerFocus()
    Layout(
        modifier = Modifier
            .keepScreenOn()
            .sensitiveContent(),
        content = {},
    ) { _, _ ->
        layout(1, 1) {}
    }
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
            lifecycleProbe.lastContent = button.content as? String
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
    WinUIView(
        modifier = fixedSizeAndPositionModifier(
            width = 220,
            height = 40,
            x = 0,
            y = 48,
        ),
        factory = { TextBox() },
        update = { textBox ->
            textBox.text = buttonContent
            onTextBoxUpdated(textBox)
        },
    )
    WinUIView(
        modifier = fixedSizeAndPositionModifier(
            width = 220,
            height = 40,
            x = 0,
            y = 96,
        ),
        factory = { ToggleSwitch() },
        update = { toggleSwitch ->
            toggleSwitch.isOn = toggleOn
            onToggleSwitchUpdated(toggleSwitch)
        },
    )
}

@Composable
private fun ValidateWinUIOwnerFocus() {
    val focusRequester = remember { FocusRequester() }
    var focusObserved by remember { mutableStateOf(false) }
    Layout(
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focusObserved = it.isFocused }
            .focusTarget(),
        content = {},
    ) { _, _ ->
        layout(1, 1) {}
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        check(focusRequester.requestFocus()) {
            "WinUI Compose focus requester could not accept owner focus request."
        }
        withFrameNanos { }
        check(focusObserved) {
            "WinUI Compose focus requester did not observe focused state."
        }
        println("compose-winui-sample: owner focus request accepted")
    }
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
        var secondaryWindowVisible by remember { mutableStateOf(true) }
        var secondaryWindowClosePassed by remember { mutableStateOf(false) }
        var secondaryWindowCloseRequested by remember { mutableStateOf(false) }
        var secondaryWindowCloseReturnedWhileComposed by remember { mutableStateOf(false) }
        var secondaryWindowCloseRequestCount by remember { mutableStateOf(0) }
        var mainWindowFocusedOnce by remember { mutableStateOf(false) }
        var mainWindowDeactivatedPassed by remember { mutableStateOf(false) }
        remember {
            println("compose-winui-sample: application created")
            runWinUILifecycleOwnerSmoke()
            runWinUIViewModelOwnerSmoke()
            runWinUINavigationEventOwnerSmoke()
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
            runWinUILayoutSnapshotInvalidationSmoke()
            runWinUILayoutCompletedListenerSmoke()
            runWinUILayoutRectChangedSmoke()
            runWinUIOwnerLayerTransformSmoke()
            runWinUIRootKeyEventSmoke()
            runWinUIRootFocusTraversalKeySmoke()
            runWinUIRootSemanticsSmoke()
            runWinUIOwnerEndApplyChangesSmoke()
            runWinUIRootUncaughtExceptionHandlerSmoke()
            runWinUIRootIndirectPointerSmoke()
            runWinUIPointerInputSmoke()
            runWinUIPointerMoveSmoke()
            runWinUIPointerEnterExitSmoke()
            runWinUIPointerScrollSmoke()
            runWinUIPointerCancelOnDisposeSmoke()
            runWinUIViewRelayoutSmoke()
            runWinUIViewPropertiesUpdateSmoke()
            runWinUIViewContainerSyncSmoke()
            runWinUISaveableStateSmoke()
            runWinUIRetainedValuesSmoke()
            runWinUITextInputSessionSmoke()
            reuseSmokePassed = true
        }
        LaunchedEffect(reuseSmokePassed, windowSmokePassed, secondaryWindowClosePassed) {
            if (
                reuseSmokePassed &&
                windowSmokePassed &&
                secondaryWindowClosePassed &&
                java.lang.Boolean.getBoolean("compose.winui.sample.autoExit")
            ) {
                check(secondaryWindowCloseRequestCount == 1) {
                    "Secondary WinUI window close request count was " +
                        "$secondaryWindowCloseRequestCount."
                }
                applicationScope.exitApplication()
            }
        }
        LaunchedEffect(secondaryWindowCloseRequested) {
            if (secondaryWindowCloseRequested) {
                awaitCondition("secondary WinUI pre-close cancellation") {
                    secondaryWindowCloseReturnedWhileComposed
                }
                secondaryWindowClosePassed = true
                secondaryWindowVisible = false
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
                var backdropSmokePassed by remember { mutableStateOf(false) }
                var backdropClearSmokePassed by remember { mutableStateOf(false) }
                var lastButton by remember { mutableStateOf<Button?>(null) }
                var lastTextBox by remember { mutableStateOf<TextBox?>(null) }
                var lastToggleSwitch by remember { mutableStateOf<ToggleSwitch?>(null) }
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    content = "Hello from Compose WinUI updated"
                }
                if (backdrop != WindowBackdrop.None) {
                    val backdropPointer = window.systemBackdrop.nativeObject.pointer.value
                    check(backdropPointer != 0L) {
                        "WindowBackdrop did not install a readable WinUI SystemBackdrop."
                    }
                    if (initialBackdropPointer == null) {
                        initialBackdropPointer = backdropPointer
                    }
                }
                LaunchedEffect(backdrop) {
                    if (backdrop == WindowBackdrop.DesktopAcrylic) {
                        val initialPointer = checkNotNull(initialBackdropPointer)
                        awaitCondition("WindowBackdrop.DesktopAcrylic replacement") {
                            val currentPointer = window.systemBackdrop.nativeObject.pointer.value
                            currentPointer != 0L && currentPointer != initialPointer
                        }
                        backdropSmokePassed = true
                        backdrop = WindowBackdrop.None
                    } else if (backdrop == WindowBackdrop.None && backdropSmokePassed) {
                        awaitCondition("WindowBackdrop.None clear") {
                            runCatching { window.systemBackdrop }.isFailure
                        }
                        backdropClearSmokePassed = true
                        println("compose-winui-sample: window backdrop cleared")
                    }
                }
                LaunchedEffect(
                    title,
                    extendsContentIntoTitleBar,
                    backdrop,
                    backdropSmokePassed,
                    backdropClearSmokePassed,
                    lastButton,
                    lastTextBox,
                    lastToggleSwitch,
                ) {
                    lastButton ?: return@LaunchedEffect
                    val textBox = lastTextBox ?: return@LaunchedEffect
                    val toggleSwitch = lastToggleSwitch ?: return@LaunchedEffect
                    if (
                        windowProbe.updateCount >= 2 &&
                        windowProbe.lastContent == "Hello from Compose WinUI updated" &&
                        textBox.text == "Hello from Compose WinUI updated" &&
                        toggleSwitch.isOn &&
                        title == "compose-winui sample updated" &&
                        extendsContentIntoTitleBar &&
                        backdrop == WindowBackdrop.None &&
                        backdropSmokePassed &&
                        backdropClearSmokePassed
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
                                (windowProbe.lastContent ?: "not-found")
                        )
                        println("compose-winui-sample: window title=${window.title}")
                        println("compose-winui-sample: window content set")
                        println("compose-winui-sample: window activated")
                    },
                    onTextBoxUpdated = { textBox ->
                        lastTextBox = textBox
                    },
                    onToggleSwitchUpdated = { toggleSwitch ->
                        lastToggleSwitch = toggleSwitch
                    },
                    onWindowFocusChanged = { focused ->
                        if (focused && !mainWindowFocusedOnce) {
                            mainWindowFocusedOnce = true
                            println("compose-winui-sample: main window activated")
                        }
                        if (!focused && !mainWindowDeactivatedPassed) {
                            mainWindowDeactivatedPassed = true
                            println("compose-winui-sample: main window deactivated")
                        }
                    },
                )
            }
            if (secondaryWindowVisible) {
                Window(
                    onCloseRequest = {
                        secondaryWindowCloseRequestCount += 1
                        check(mainWindowFocusedOnce) {
                            "Primary WinUI window did not report an activated state."
                        }
                        check(mainWindowDeactivatedPassed) {
                            "Primary WinUI window did not report deactivation for secondary activation."
                        }
                        secondaryWindowCloseRequested = true
                        println("compose-winui-sample: secondary window close request")
                    },
                    title = "compose-winui secondary",
                ) {
                    check(window.title == "compose-winui secondary") {
                        "Secondary WinUI window title was not applied."
                    }
                    LaunchedEffect(Unit) {
                        withFrameNanos { }
                        awaitCondition("primary WinUI window activation") {
                            mainWindowFocusedOnce
                        }
                        awaitCondition("primary WinUI window deactivation") {
                            mainWindowDeactivatedPassed
                        }
                        window.close()
                        secondaryWindowCloseReturnedWhileComposed = true
                        println("compose-winui-sample: secondary window pre-close canceled")
                    }
                }
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
        val wrapperClip = checkNotNull(wrapper.readClipRectOrNull()) {
            "WinUIView did not apply clipToBounds to the native wrapper."
        }
        check(wrapperClip.width == 123f && wrapperClip.height == 45f) {
            "WinUIView did not apply clipToBounds to the native wrapper: " +
                "${wrapperClip.width}x${wrapperClip.height}."
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

    private fun runWinUILifecycleOwnerSmoke() {
        val currentComposeView = WinUIComposeView()
        var lifecycle: Lifecycle? = null
        currentComposeView.setContent {
            lifecycle = LocalLifecycleOwner.current.lifecycle
        }
        check(lifecycle?.currentState == Lifecycle.State.RESUMED) {
            "WinUI lifecycle owner did not enter RESUMED state."
        }
        currentComposeView.dispose()
        check(lifecycle?.currentState == Lifecycle.State.DESTROYED) {
            "WinUI lifecycle owner did not enter DESTROYED state on dispose."
        }
        println("compose-winui-sample: lifecycle owner resumed and destroyed")
    }

    private fun runWinUIViewModelOwnerSmoke() {
        val currentComposeView = WinUIComposeView()
        var viewModelStoreOwnerProvided = false
        var firstViewModel: WinUISavedStateViewModel? = null
        currentComposeView.setContent {
            viewModelStoreOwnerProvided = LocalViewModelStoreOwner.current != null
            firstViewModel = viewModel(key = "winui-saved-state-view-model") {
                WinUISavedStateViewModel(createSavedStateHandle())
            }
        }
        val initialViewModel = checkNotNull(firstViewModel) {
            "WinUI viewModel() did not create a ViewModel."
        }
        check(viewModelStoreOwnerProvided) {
            "WinUI LocalViewModelStoreOwner was not provided through host defaults."
        }
        check(initialViewModel.savedStateHandle.get<String>("value") == "initial") {
            "WinUI SavedStateHandle did not expose its initial value."
        }
        initialViewModel.savedStateHandle["value"] = "updated"
        currentComposeView.disposeComposition()

        var restoredViewModel: WinUISavedStateViewModel? = null
        currentComposeView.setContent {
            restoredViewModel = viewModel(key = "winui-saved-state-view-model") {
                WinUISavedStateViewModel(createSavedStateHandle())
            }
        }
        check(restoredViewModel === initialViewModel) {
            "WinUI ViewModelStore did not retain the ViewModel across disposeComposition."
        }
        check(restoredViewModel?.savedStateHandle?.get<String>("value") == "updated") {
            "WinUI SavedStateHandle did not preserve the updated value."
        }
        currentComposeView.dispose()
        check(initialViewModel.cleared) {
            "WinUI ViewModelStore was not cleared when the lifecycle reached DESTROYED."
        }
        println("compose-winui-sample: viewmodel owner saved state and cleared")
    }

    private fun runWinUINavigationEventOwnerSmoke() {
        val currentComposeView = WinUIComposeView()
        var dispatcherOwnerProvided = false
        var dispatcherEnabled = false
        currentComposeView.setContent {
            val dispatcherOwner = LocalNavigationEventDispatcherOwner.current
            dispatcherOwnerProvided = dispatcherOwner != null
            dispatcherEnabled = dispatcherOwner?.navigationEventDispatcher?.isEnabled == true
        }
        currentComposeView.dispose()
        check(dispatcherOwnerProvided) {
            "WinUI LocalNavigationEventDispatcherOwner was not provided through host defaults."
        }
        check(dispatcherEnabled) {
            "WinUI NavigationEventDispatcher was not enabled by default."
        }
        println("compose-winui-sample: navigation event dispatcher owner provided")
    }

    private suspend fun runWinUISaveableStateSmoke() {
        val currentComposeView = WinUIComposeView()
        var savedState: MutableState<String>? = null
        var observedValue: String? = null
        currentComposeView.setSaveableStateSmokeContent { state ->
            savedState = state
            observedValue = state.value
        }
        check(savedState?.value == "initial") {
            "WinUI rememberSaveable did not create its initial value."
        }
        savedState?.value = "updated"
        awaitCondition("WinUI rememberSaveable state update") {
            observedValue == "updated"
        }
        currentComposeView.disposeComposition()

        var restoredValue: String? = null
        currentComposeView.setSaveableStateSmokeContent { state ->
            restoredValue = state.value
        }
        check(restoredValue == "updated") {
            "WinUI rememberSaveable did not restore across disposeComposition: $restoredValue."
        }
        currentComposeView.dispose()
        println("compose-winui-sample: saveable state restored")
    }

    private fun runWinUIRetainedValuesSmoke() {
        val currentComposeView = WinUIComposeView()
        var factoryCalls = 0
        var firstValue: Any? = null
        currentComposeView.setRetainedValuesSmokeContent(
            createValue = { Any().also { factoryCalls++ } },
            onValue = { firstValue = it },
        )
        check(firstValue != null) {
            "WinUI retain did not create its initial value."
        }
        check(factoryCalls == 1) {
            "WinUI retain factory was called $factoryCalls times for initial content."
        }
        currentComposeView.disposeComposition()

        var restoredValue: Any? = null
        currentComposeView.setRetainedValuesSmokeContent(
            createValue = { Any().also { factoryCalls++ } },
            onValue = { restoredValue = it },
        )
        check(restoredValue === firstValue) {
            "WinUI retain did not restore across disposeComposition."
        }
        check(factoryCalls == 1) {
            "WinUI retain factory was called again while restoring."
        }
        currentComposeView.dispose()
        println("compose-winui-sample: retained value restored")
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
        check(firstProbe.lastContent == "first" && secondProbe.lastContent == "second") {
            "WinUIView z-order smoke did not update both Buttons: " +
                "first=${firstProbe.lastContent} second=${secondProbe.lastContent}."
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
        val wrapperClip = checkNotNull(wrapper.readClipRectOrNull()) {
            "WinUIView wrapper clip was not set."
        }
        check(wrapperClip.width == 80f && wrapperClip.height == 30f) {
            "WinUIView wrapper clip did not match clipped Compose bounds: " +
                "${wrapperClip.width}x${wrapperClip.height}."
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
        var lastButtonContent: String? = null
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
                    val content = "button"
                    it.content = content
                    lastButtonContent = it.content as? String
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
        check(lastButton != null && lastButtonContent == "button") {
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
                lifecycleProbe.lastContent == "state update initial" &&
                (rootHost.content as? Canvas)?.children?.size == 1
        }
        val button = checkNotNull(lifecycleProbe.lastButton) {
            "WinUIView state update smoke did not install a Button."
        }

        content.value = "state update changed"
        awaitCondition("WinUIView repeated state update") {
            lifecycleProbe.updateCount == 2 &&
                lifecycleProbe.lastButton === button &&
                lifecycleProbe.lastContent == "state update changed"
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
            val wrapper = rootCanvas?.children?.singleOrNull() as? Canvas
            val clip = wrapper?.readClipRectOrNull()
            clip?.width == 80f &&
                clip.height == 30f &&
                wrapper.width == 80.0 &&
                wrapper.height == 30.0 &&
                wrapper.margin.left == 4.0 &&
                wrapper.margin.top == 6.0 &&
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
            val currentWrapper = (rootHost.content as? Canvas)?.children?.singleOrNull() as? Canvas
            val clip = currentWrapper?.readClipRectOrNull()
            currentWrapper?.nativeObject?.sameIdentity(wrapper.nativeObject) == true &&
                lifecycleProbe.lastButton === button &&
                clip?.width == 140f &&
                clip.height == 55f &&
                currentWrapper.width == 140.0 &&
                currentWrapper.height == 55.0 &&
                currentWrapper.margin.left == 11.0 &&
                currentWrapper.margin.top == 17.0 &&
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

    private suspend fun runWinUILayoutSnapshotInvalidationSmoke() {
        val lifecycleProbe = WinUIViewLifecycleProbe()
        val currentComposeView = WinUIComposeView()
        val rootHost = currentComposeView.root as ContentControl
        val width: MutableState<Int> = mutableStateOf(70)
        val height: MutableState<Int> = mutableStateOf(25)
        currentComposeView.setContent {
            WinUIViewSampleContent(
                modifier = snapshotDrivenSizeModifier(width, height),
                clipToBounds = true,
                lifecycleProbe = lifecycleProbe,
            )
        }
        awaitCondition("WinUI layout snapshot initial bounds") {
            val wrapper = (rootHost.content as? Canvas)?.children?.singleOrNull()
            val clip = wrapper?.readClipRectOrNull()
            clip?.width == 70f &&
                clip.height == 25f &&
                lifecycleProbe.lastButton?.width == 70.0 &&
                lifecycleProbe.lastButton?.height == 25.0
        }
        val button = checkNotNull(lifecycleProbe.lastButton) {
            "WinUI layout snapshot smoke did not install a Button."
        }
        check(lifecycleProbe.updateCount == 1) {
            "WinUI layout snapshot smoke unexpectedly recomposed before state change."
        }

        width.value = 115
        height.value = 45
        awaitCondition("WinUI layout snapshot updated bounds") {
            val wrapper = (rootHost.content as? Canvas)?.children?.singleOrNull()
            val clip = wrapper?.readClipRectOrNull()
            clip?.width == 115f &&
                clip.height == 45f &&
                lifecycleProbe.lastButton === button &&
                button.width == 115.0 &&
                button.height == 45.0
        }
        check(lifecycleProbe.updateCount == 1) {
            "WinUI layout snapshot invalidation should relayout without recomposition, " +
                "update=${lifecycleProbe.updateCount}."
        }
        currentComposeView.dispose()
        println("compose-winui-sample: layout snapshot invalidation")
    }

    private suspend fun runWinUILayoutCompletedListenerSmoke() {
        val currentComposeView = WinUIComposeView()
        val includeOnPlaced: MutableState<Boolean> = mutableStateOf(false)
        var onPlacedCount = 0
        currentComposeView.setContent {
            Layout(
                modifier = if (includeOnPlaced.value) {
                    legacyOnPlacedModifier { onPlacedCount += 1 }
                } else {
                    Modifier
                },
                content = {},
            ) { _, _ ->
                layout(1, 1) {}
            }
        }
        withFrameNanos { }
        check(onPlacedCount == 0) {
            "WinUI layout completed listener smoke fired before onPlaced was installed."
        }
        includeOnPlaced.value = true
        awaitCondition("WinUI layout completed listener") {
            onPlacedCount >= 1
        }
        currentComposeView.dispose()
        println("compose-winui-sample: layout completed listener")
    }

    private suspend fun runWinUILayoutRectChangedSmoke() {
        val currentComposeView = WinUIComposeView()
        val showTarget: MutableState<Boolean> = mutableStateOf(true)
        var targetBounds: RelativeLayoutBounds? = null
        currentComposeView.setContent {
            Layout(
                content = {
                    if (showTarget.value) {
                        Layout(
                            modifier = Modifier.onLayoutRectChanged(
                                throttleMillis = 0,
                                debounceMillis = 0,
                            ) {
                                targetBounds = it
                            },
                            content = {},
                        ) { _, _ ->
                            layout(40, 40) {}
                        }
                    }
                    Layout(content = {}) { _, _ ->
                        layout(40, 40) {}
                    }
                },
            ) { measurables, _ ->
                val placeables = measurables.map {
                    it.measure(Constraints.fixed(40, 40))
                }
                layout(40, 40) {
                    placeables.forEach { it.place(0, 0) }
                }
            }
        }
        awaitCondition("WinUI layout rect changed occlusion") {
            targetBounds?.calculateOcclusions()?.isNotEmpty() == true
        }
        val removedBounds = checkNotNull(targetBounds) {
            "WinUI layout rect changed smoke did not receive bounds."
        }
        showTarget.value = false
        awaitCondition("WinUI layout rect changed detach cleanup") {
            runCatching {
                removedBounds.calculateOcclusions().isEmpty()
            }.getOrDefault(false)
        }
        currentComposeView.dispose()
        println("compose-winui-sample: layout rect changed")
    }

    private suspend fun runWinUIOwnerLayerTransformSmoke() {
        val currentComposeView = WinUIComposeView()
        val translationX: MutableState<Float> = mutableStateOf(0f)
        var bounds: RelativeLayoutBounds? = null
        currentComposeView.setContent {
            Layout(
                modifier = Modifier
                    .graphicsLayer {
                        this.translationX = translationX.value
                        clip = true
                    }
                    .onLayoutRectChanged(
                        throttleMillis = 0,
                        debounceMillis = 0,
                    ) {
                        bounds = it
                    },
                content = {},
            ) { _, _ ->
                layout(20, 10) {}
            }
        }
        awaitCondition("WinUI owner layer initial transform bounds") {
            val currentBounds = bounds
            currentBounds?.positionInRoot?.x == 0 &&
                currentBounds.positionInRoot.y == 0 &&
                currentBounds.width == 20 &&
                currentBounds.height == 10
        }
        translationX.value = 12f
        awaitCondition("WinUI owner layer updated transform bounds") {
            val currentBounds = bounds
            currentBounds?.positionInRoot?.x == 12 &&
                currentBounds.positionInRoot.y == 0 &&
                currentBounds.width == 20 &&
                currentBounds.height == 10
        }
        currentComposeView.dispose()
        println("compose-winui-sample: owner layer transform")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIRootKeyEventSmoke() {
        val currentComposeView = WinUIComposeView()
        val focusRequester = FocusRequester()
        var focused = false
        val receivedEvents = mutableListOf<String>()
        currentComposeView.setContent {
            Layout(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        focused = it.isFocused
                    }
                    .focusTarget()
                    .onKeyEvent {
                        receivedEvents += "key:${it.key.keyCode}"
                        true
                    }
                    .onPreviewKeyEvent {
                        receivedEvents += "preview:${it.key.keyCode}"
                        false
                    },
                content = {},
            ) { _, _ ->
                layout(1, 1) {}
            }
        }
        withFrameNanos { }
        check(focusRequester.requestFocus()) {
            "WinUI root key event smoke could not focus the target."
        }
        awaitCondition("WinUI root key event target focused") {
            focused
        }
        val consumed = currentComposeView.rootForTest().sendKeyEvent(
            KeyEvent(
                key = Key.A,
                type = KeyEventType.KeyDown,
                codePoint = 'a'.code,
            )
        )
        check(consumed) {
            "WinUI RootForTest did not report the key event as consumed."
        }
        check(receivedEvents == listOf("preview:${Key.A.keyCode}", "key:${Key.A.keyCode}")) {
            "WinUI RootForTest dispatched key events out of order: $receivedEvents."
        }
        currentComposeView.dispose()
        println("compose-winui-sample: root key event")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIRootFocusTraversalKeySmoke() {
        val currentComposeView = WinUIComposeView()
        val firstRequester = FocusRequester()
        val secondRequester = FocusRequester()
        var firstFocused = false
        var secondFocused = false
        currentComposeView.setContent {
            Layout(
                content = {
                    Layout(
                        modifier = Modifier
                            .focusRequester(firstRequester)
                            .onFocusChanged {
                                firstFocused = it.isFocused
                            }
                            .focusTarget(),
                        content = {},
                    ) { _, _ ->
                        layout(1, 1) {}
                    }
                    Layout(
                        modifier = Modifier
                            .focusRequester(secondRequester)
                            .onFocusChanged {
                                secondFocused = it.isFocused
                            }
                            .focusTarget(),
                        content = {},
                    ) { _, _ ->
                        layout(1, 1) {}
                    }
                },
            ) { measurables, _ ->
                val first = measurables[0].measure(Constraints.fixed(1, 1))
                val second = measurables[1].measure(Constraints.fixed(1, 1))
                layout(2, 1) {
                    first.place(0, 0)
                    second.place(1, 0)
                }
            }
        }
        withFrameNanos { }
        check(firstRequester.requestFocus()) {
            "WinUI root focus traversal smoke could not focus the first target."
        }
        awaitCondition("WinUI root focus traversal first target focused") {
            firstFocused && !secondFocused
        }
        check(
            currentComposeView.rootForTest().sendKeyEvent(
                KeyEvent(key = Key.Tab, type = KeyEventType.KeyDown)
            )
        ) {
            "WinUI RootForTest did not consume Tab focus traversal."
        }
        awaitCondition("WinUI root focus traversal second target focused") {
            !firstFocused && secondFocused
        }
        check(
            currentComposeView.rootForTest().sendKeyEvent(
                KeyEvent(key = Key.Tab, type = KeyEventType.KeyDown, isShiftPressed = true)
            )
        ) {
            "WinUI RootForTest did not consume Shift+Tab focus traversal."
        }
        awaitCondition("WinUI root focus traversal returned to first target") {
            firstFocused && !secondFocused
        }
        currentComposeView.dispose()
        println("compose-winui-sample: root focus traversal key event")
    }

    private fun runWinUIRootSemanticsSmoke() {
        val currentComposeView = WinUIComposeView()
        currentComposeView.setContent {
            Layout(
                modifier = Modifier.semantics {
                    testTag = "winui-semantics"
                    contentDescription = "WinUI semantics node"
                },
                content = {},
            ) { _, _ ->
                layout(1, 1) {}
            }
        }
        currentComposeView.rootForTest().measureAndLayoutForTest()
        val semanticsNode = currentComposeView.rootForTest()
            .semanticsOwner
            .getAllSemanticsNodes(mergingEnabled = false)
            .singleOrNull {
                it.config.getOrNull(SemanticsProperties.TestTag) == "winui-semantics"
            } ?: error("WinUI RootForTest did not expose the test semantics node.")
        check(
            semanticsNode.config.getOrNull(SemanticsProperties.ContentDescription) ==
                listOf("WinUI semantics node")
        ) {
            "WinUI RootForTest exposed incorrect semantics content: ${semanticsNode.config}."
        }
        currentComposeView.dispose()
        println("compose-winui-sample: root semantics")
    }

    private suspend fun runWinUIOwnerEndApplyChangesSmoke() {
        val currentComposeView = WinUIComposeView()
        val probe = WinUIOwnerEndApplyChangesProbe()
        currentComposeView.setContent {
            Layout(
                modifier = Modifier.winUIOwnerEndApplyChangesSmoke(probe),
                content = {},
            ) { _, _ ->
                layout(1, 1) {}
            }
        }
        awaitCondition("WinUI owner end-apply listener drain") {
            probe.events == listOf("repeat", "first", "second")
        }
        currentComposeView.dispose()
        println("compose-winui-sample: owner end apply changes")
    }

    private suspend fun runWinUIRootUncaughtExceptionHandlerSmoke() {
        val currentComposeView = WinUIComposeView()
        val shouldThrow = mutableStateOf(false)
        val expected = IllegalStateException("WinUI layout exception handler smoke")
        var captured: Throwable? = null
        currentComposeView.setContent {
            Layout(content = {}) { _, _ ->
                if (shouldThrow.value) throw expected
                layout(1, 1) {}
            }
        }
        currentComposeView.rootForTest().setUncaughtExceptionHandler(
            object : RootForTest.UncaughtExceptionHandler {
                override fun onUncaughtException(t: Throwable) {
                    captured = t
                }
            }
        )
        shouldThrow.value = true
        currentComposeView.rootForTest().measureAndLayoutForTest()
        awaitCondition("WinUI root uncaught exception handler") {
            captured === expected
        }
        currentComposeView.rootForTest().setUncaughtExceptionHandler(null)
        currentComposeView.dispose()
        println("compose-winui-sample: root uncaught exception handler")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIRootIndirectPointerSmoke() {
        val currentComposeView = WinUIComposeView()
        val focusRequester = FocusRequester()
        val probe = WinUIIndirectPointerInputSmokeProbe()
        currentComposeView.setContent {
            Layout(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        probe.focused = it.isFocused
                    }
                    .winUIIndirectPointerInputSmoke(probe)
                    .focusTarget(),
                content = {},
            ) { _, _ ->
                layout(1, 1) {}
            }
        }
        withFrameNanos { }
        check(focusRequester.requestFocus()) {
            "WinUI root indirect pointer smoke could not focus the target."
        }
        awaitCondition("WinUI root indirect pointer target focused") {
            probe.focused
        }
        val event = IndirectPointerEvent(
            changes = listOf(
                IndirectPointerInputChange(
                    id = PointerId(0),
                    uptimeMillis = 2L,
                    position = Offset(4f, 6f),
                    pressed = true,
                    pressure = 1f,
                    previousUptimeMillis = 1L,
                    previousPosition = Offset(3f, 5f),
                    previousPressed = true,
                )
            ),
            type = IndirectPointerEventType.Move,
            primaryDirectionalMotionAxis = IndirectPointerEventPrimaryDirectionalMotionAxis.X,
        )
        check(currentComposeView.rootForTest().sendIndirectPointerEvent(event)) {
            "WinUI RootForTest did not report the indirect pointer event as consumed."
        }
        check(probe.events == listOf("Initial:Move", "Main:Move", "Final:Move")) {
            "WinUI RootForTest dispatched indirect pointer events out of order: " +
                "${probe.events}."
        }
        currentComposeView.dispose()
        println("compose-winui-sample: root indirect pointer event")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIPointerInputSmoke() {
        val currentComposeView = WinUIComposeView()
        val probe = WinUIPointerInputSmokeProbe()
        currentComposeView.setContent {
            Layout(
                modifier = Modifier.winUIPointerInputSmoke(probe),
                content = {},
            ) { _, _ ->
                layout(20, 20) {}
            }
        }
        withFrameNanos { }
        check(
            currentComposeView.sendPointerEventForTest(
                eventType = PointerEventType.Press,
                position = Offset(5f, 5f),
                uptimeMillis = 1L,
                down = true,
            )
        ) {
            "WinUI pointer input smoke did not dispatch the press event."
        }
        awaitCondition("WinUI pointer input press received") {
            probe.pressCount == 1 && probe.releaseCount == 0
        }
        check(
            currentComposeView.sendPointerEventForTest(
                eventType = PointerEventType.Release,
                position = Offset(5f, 5f),
                uptimeMillis = 2L,
                down = false,
            )
        ) {
            "WinUI pointer input smoke did not dispatch the release event."
        }
        awaitCondition("WinUI pointer input release received") {
            probe.pressCount == 1 && probe.releaseCount == 1
        }
        currentComposeView.dispose()
        println("compose-winui-sample: pointer input")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIPointerMoveSmoke() {
        val currentComposeView = WinUIComposeView()
        val probe = WinUIPointerInputSmokeProbe()
        currentComposeView.setContent {
            Layout(
                modifier = Modifier.winUIPointerInputSmoke(probe),
                content = {},
            ) { _, _ ->
                layout(30, 30) {}
            }
        }
        withFrameNanos { }
        check(
            currentComposeView.sendPointerEventForTest(
                eventType = PointerEventType.Press,
                position = Offset(4f, 4f),
                uptimeMillis = 1L,
                down = true,
            )
        ) {
            "WinUI pointer input move smoke did not dispatch the press event."
        }
        check(
            currentComposeView.sendPointerEventForTest(
                eventType = PointerEventType.Move,
                position = Offset(12f, 9f),
                uptimeMillis = 2L,
                down = true,
            )
        ) {
            "WinUI pointer input move smoke did not dispatch the move event."
        }
        check(
            currentComposeView.sendPointerEventForTest(
                eventType = PointerEventType.Release,
                position = Offset(12f, 9f),
                uptimeMillis = 3L,
                down = false,
            )
        ) {
            "WinUI pointer input move smoke did not dispatch the release event."
        }
        awaitCondition("WinUI pointer input move sequence received") {
            probe.pressCount == 1 &&
                probe.moveCount >= 1 &&
                probe.releaseCount == 1 &&
                probe.lastPosition == Offset(12f, 9f)
        }
        currentComposeView.dispose()
        println("compose-winui-sample: pointer input move")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIPointerEnterExitSmoke() {
        val currentComposeView = WinUIComposeView()
        val probe = WinUIPointerInputSmokeProbe()
        currentComposeView.setContent {
            Layout(
                modifier = Modifier.winUIPointerInputSmoke(probe),
                content = {},
            ) { _, _ ->
                layout(30, 30) {}
            }
        }
        withFrameNanos { }
        check(
            currentComposeView.sendPointerEventForTest(
                eventType = PointerEventType.Enter,
                position = Offset(6f, 8f),
                uptimeMillis = 1L,
                down = false,
                type = PointerType.Mouse,
            )
        ) {
            "WinUI pointer input enter smoke did not dispatch the enter event."
        }
        check(
            currentComposeView.sendPointerEventForTest(
                eventType = PointerEventType.Exit,
                position = Offset(6f, 8f),
                uptimeMillis = 2L,
                down = false,
                type = PointerType.Mouse,
            )
        ) {
            "WinUI pointer input exit smoke did not dispatch the exit event."
        }
        awaitCondition("WinUI pointer input enter/exit received") {
            probe.enterCount == 1 &&
                probe.exitCount == 1 &&
                probe.lastPosition == Offset(6f, 8f)
        }
        currentComposeView.dispose()
        println("compose-winui-sample: pointer input enter exit")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIPointerScrollSmoke() {
        val currentComposeView = WinUIComposeView()
        val probe = WinUIPointerInputSmokeProbe()
        currentComposeView.setContent {
            Layout(
                modifier = Modifier.winUIPointerInputSmoke(probe),
                content = {},
            ) { _, _ ->
                layout(30, 30) {}
            }
        }
        withFrameNanos { }
        check(
            currentComposeView.sendPointerEventForTest(
                eventType = PointerEventType.Scroll,
                position = Offset(12f, 9f),
                uptimeMillis = 1L,
                down = false,
                type = PointerType.Mouse,
                scrollDelta = Offset(0f, -1f),
            )
        ) {
            "WinUI pointer input scroll smoke did not dispatch the scroll event."
        }
        awaitCondition("WinUI pointer input scroll received") {
            probe.scrollCount == 1 &&
                probe.lastPosition == Offset(12f, 9f) &&
                probe.lastScrollDelta == Offset(0f, -1f)
        }
        currentComposeView.dispose()
        println("compose-winui-sample: pointer input scroll")
    }

    @OptIn(InternalComposeUiApi::class)
    private suspend fun runWinUIPointerCancelOnDisposeSmoke() {
        val currentComposeView = WinUIComposeView()
        val probe = WinUIPointerInputSmokeProbe()
        currentComposeView.setContent {
            Layout(
                modifier = Modifier.winUIPointerInputSmoke(probe),
                content = {},
            ) { _, _ ->
                layout(20, 20) {}
            }
        }
        withFrameNanos { }
        check(
            currentComposeView.sendPointerEventForTest(
                eventType = PointerEventType.Press,
                position = Offset(5f, 5f),
                uptimeMillis = 1L,
                down = true,
            )
        ) {
            "WinUI pointer input cancel smoke did not dispatch the press event."
        }
        awaitCondition("WinUI pointer input cancel smoke press received") {
            probe.pressCount == 1
        }
        currentComposeView.dispose()
        check(probe.cancelCount >= 1) {
            "WinUI pointer input did not receive cancellation on owner disposal."
        }
        println("compose-winui-sample: pointer input cancel on dispose")
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

    private suspend fun runWinUITextInputSessionSmoke() {
        val probe = WinUITextInputSessionSmokeProbe()
        val currentComposeView = WinUIComposeView()
        currentComposeView.setContent {
            Layout(
                modifier = Modifier.winUITextInputSessionSmoke(probe),
                content = {},
            ) { _, _ ->
                layout(1, 1) {}
            }
        }
        awaitCondition("WinUI text input first session cancellation") {
            probe.firstInputStarted &&
                probe.firstInputCancelled &&
                probe.secondInputStarted &&
                !probe.secondInputCancelled
        }
        currentComposeView.dispose()
        awaitCondition("WinUI text input disposal cancellation") {
            probe.secondInputCancelled
        }
        println("compose-winui-sample: text input session cancellation")
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

@Composable
private fun WinUISaveableStateSmokeContent(
    onState: (MutableState<String>) -> Unit,
) {
    val state = rememberSaveable(stateSaver = autoSaver()) { mutableStateOf("initial") }
    onState(state)
}

private fun WinUIComposeView.setSaveableStateSmokeContent(
    onState: (MutableState<String>) -> Unit,
) {
    setContent {
        WinUISaveableStateSmokeContent(onState)
    }
}

@Composable
private fun WinUIRetainedValuesSmokeContent(
    createValue: () -> Any,
    onValue: (Any) -> Unit,
) {
    val value = retain { createValue() }
    onValue(value)
}

private fun WinUIComposeView.setRetainedValuesSmokeContent(
    createValue: () -> Any,
    onValue: (Any) -> Unit,
) {
    setContent {
        WinUIRetainedValuesSmokeContent(createValue, onValue)
    }
}

private class WinUITextInputSessionSmokeProbe {
    var firstInputStarted: Boolean = false
    var firstInputCancelled: Boolean = false
    var secondInputStarted: Boolean = false
    var secondInputCancelled: Boolean = false
}

private object WinUITextInputSmokeRequest : PlatformTextInputMethodRequest

private class WinUISavedStateViewModel(
    val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    var cleared = false
        private set

    init {
        if (!savedStateHandle.contains("value")) {
            savedStateHandle["value"] = "initial"
        }
    }

    override fun onCleared() {
        cleared = true
    }
}

private class WinUIPointerInputSmokeProbe {
    var pressCount = 0
    var enterCount = 0
    var moveCount = 0
    var exitCount = 0
    var releaseCount = 0
    var scrollCount = 0
    var cancelCount = 0
    var lastPosition: Offset? = null
    var lastScrollDelta: Offset? = null
}

private class WinUIOwnerEndApplyChangesProbe {
    val events = mutableListOf<String>()
}

private fun Modifier.winUIOwnerEndApplyChangesSmoke(
    probe: WinUIOwnerEndApplyChangesProbe
): Modifier = this then WinUIOwnerEndApplyChangesElement(probe)

private data class WinUIOwnerEndApplyChangesElement(
    private val probe: WinUIOwnerEndApplyChangesProbe,
) : ModifierNodeElement<WinUIOwnerEndApplyChangesNode>() {
    override fun create(): WinUIOwnerEndApplyChangesNode =
        WinUIOwnerEndApplyChangesNode(probe)

    override fun update(node: WinUIOwnerEndApplyChangesNode) {
        node.probe = probe
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "winUIOwnerEndApplyChangesSmoke"
    }
}

private class WinUIOwnerEndApplyChangesNode(
    var probe: WinUIOwnerEndApplyChangesProbe,
) : Modifier.Node() {
    private val repeatedEffect: () -> Unit = {
        probe.events += "repeat"
    }

    override fun onAttach() {
        sideEffect(repeatedEffect)
        sideEffect(repeatedEffect)
        sideEffect {
            probe.events += "first"
            sideEffect {
                probe.events += "second"
            }
        }
    }
}

private class WinUIIndirectPointerInputSmokeProbe {
    val events = mutableListOf<String>()
    var focused = false
}

private fun Modifier.winUIIndirectPointerInputSmoke(
    probe: WinUIIndirectPointerInputSmokeProbe
): Modifier = this then WinUIIndirectPointerInputSmokeElement(probe)

private data class WinUIIndirectPointerInputSmokeElement(
    private val probe: WinUIIndirectPointerInputSmokeProbe,
) : ModifierNodeElement<WinUIIndirectPointerInputSmokeNode>() {
    override fun create(): WinUIIndirectPointerInputSmokeNode =
        WinUIIndirectPointerInputSmokeNode(probe)

    override fun update(node: WinUIIndirectPointerInputSmokeNode) {
        node.probe = probe
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "winUIIndirectPointerInputSmoke"
    }
}

private class WinUIIndirectPointerInputSmokeNode(
    var probe: WinUIIndirectPointerInputSmokeProbe,
) : Modifier.Node(), IndirectPointerInputModifierNode {
    override fun onIndirectPointerEvent(
        event: IndirectPointerEvent,
        pass: PointerEventPass,
    ) {
        probe.events += "$pass:${event.type}"
        if (pass == PointerEventPass.Main) {
            event.changes.forEach { it.consume() }
        }
    }

    override fun onCancelIndirectPointerInput() = Unit
}

private fun Modifier.winUIPointerInputSmoke(
    probe: WinUIPointerInputSmokeProbe
): Modifier = this then WinUIPointerInputSmokeElement(probe)

private data class WinUIPointerInputSmokeElement(
    private val probe: WinUIPointerInputSmokeProbe,
) : ModifierNodeElement<WinUIPointerInputSmokeNode>() {
    override fun create(): WinUIPointerInputSmokeNode =
        WinUIPointerInputSmokeNode(probe)

    override fun update(node: WinUIPointerInputSmokeNode) {
        node.probe = probe
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "winUIPointerInputSmoke"
    }
}

private class WinUIPointerInputSmokeNode(
    var probe: WinUIPointerInputSmokeProbe,
) : Modifier.Node(), PointerInputModifierNode {
    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        if (pass != PointerEventPass.Main) return
        val change = pointerEvent.changes.firstOrNull()
        change?.let {
            probe.lastPosition = it.position
            probe.lastScrollDelta = it.scrollDelta
        }
        when (pointerEvent.type) {
            PointerEventType.Press -> probe.pressCount += 1
            PointerEventType.Enter -> probe.enterCount += 1
            PointerEventType.Move -> probe.moveCount += 1
            PointerEventType.Exit -> probe.exitCount += 1
            PointerEventType.Release -> probe.releaseCount += 1
            PointerEventType.Scroll -> probe.scrollCount += 1
        }
        change?.consume()
    }

    override fun onCancelPointerInput() {
        probe.cancelCount += 1
    }
}

private fun Modifier.winUITextInputSessionSmoke(
    probe: WinUITextInputSessionSmokeProbe
): Modifier = this then WinUITextInputSessionSmokeElement(probe)

private data class WinUITextInputSessionSmokeElement(
    private val probe: WinUITextInputSessionSmokeProbe,
) : ModifierNodeElement<WinUITextInputSessionSmokeNode>() {
    override fun create(): WinUITextInputSessionSmokeNode =
        WinUITextInputSessionSmokeNode(probe)

    override fun update(node: WinUITextInputSessionSmokeNode) {
        node.probe = probe
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "winUITextInputSessionSmoke"
    }
}

private class WinUITextInputSessionSmokeNode(
    var probe: WinUITextInputSessionSmokeProbe,
) : Modifier.Node(), PlatformTextInputModifierNode {
    override fun onAttach() {
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runTextInputSession(isFirstSession = true)
        }
        coroutineScope.launch {
            while (!probe.firstInputStarted) {
                delay(1)
            }
            runTextInputSession(isFirstSession = false)
        }
    }

    private suspend fun runTextInputSession(isFirstSession: Boolean) {
        try {
            establishTextInputSession {
                if (isFirstSession) {
                    probe.firstInputStarted = true
                } else {
                    probe.secondInputStarted = true
                }
                try {
                    startInputMethod(WinUITextInputSmokeRequest)
                } finally {
                    if (isFirstSession) {
                        probe.firstInputCancelled = true
                    } else {
                        probe.secondInputCancelled = true
                    }
                }
            }
        } catch (_: CancellationException) {
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
    runCatching { clip?.rect }.getOrNull()

private fun WinUIComposeView.rootForTest(): RootForTest {
    val owner = javaClass.getDeclaredField("owner").also {
        it.isAccessible = true
    }.get(this)
    return owner.javaClass.getMethod("getRootForTest").invoke(owner) as RootForTest
}

private fun fixedSizeAndPositionModifier(width: Int, height: Int, x: Int, y: Int): Modifier =
    Modifier.layout { measurable, _ ->
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(x + width, y + height) {
            placeable.place(x, y)
        }
    }

private fun snapshotDrivenSizeModifier(
    width: MutableState<Int>,
    height: MutableState<Int>,
): Modifier =
    Modifier.layout { measurable, _ ->
        val currentWidth = width.value
        val currentHeight = height.value
        val placeable = measurable.measure(Constraints.fixed(currentWidth, currentHeight))
        layout(currentWidth, currentHeight) {
            placeable.place(0, 0)
        }
    }

@Suppress("DEPRECATION")
private fun legacyOnPlacedModifier(onPlaced: () -> Unit): Modifier =
    Modifier.then(
        object : OnPlacedModifier {
            override fun onPlaced(coordinates: LayoutCoordinates) {
                onPlaced()
            }
        }
    )

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
