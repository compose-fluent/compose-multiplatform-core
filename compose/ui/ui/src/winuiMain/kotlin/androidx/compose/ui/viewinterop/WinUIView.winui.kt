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

package androidx.compose.ui.viewinterop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.ReusableComposeNode
import androidx.compose.runtime.Updater
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.platform.WinUIApplier
import androidx.compose.ui.platform.WinUINode
import microsoft.ui.xaml.UIElement

/**
 * WinUI-specific interop switches for [WinUIView].
 */
class WinUIInteropProperties(
    val isNativeAccessibilityEnabled: Boolean = true,
    val isUserInteractionEnabled: Boolean = true,
    val clipToBounds: Boolean = false,
)

/**
 * Composes a WinUI [UIElement] obtained from [factory].
 *
 * The lifecycle mirrors Android `AndroidView`: [factory] creates the native element exactly once,
 * [update] runs after creation and on recomposition, [onReset] opts into reuse, and [onRelease]
 * runs when the element is permanently discarded.
 */
@Composable
@UiComposable
fun <T : UIElement> WinUIView(
    factory: () -> T,
    modifier: Modifier = Modifier,
    properties: WinUIInteropProperties = WinUIInteropProperties(),
    update: (T) -> Unit = WinUIViewNoOpUpdate,
) {
    WinUIView(
        factory = factory,
        modifier = modifier,
        properties = properties,
        onRelease = WinUIViewNoOpUpdate,
        update = update,
    )
}

/**
 * Composes a reusable WinUI [UIElement] obtained from [factory].
 */
@Composable
@UiComposable
fun <T : UIElement> WinUIView(
    factory: () -> T,
    modifier: Modifier = Modifier,
    properties: WinUIInteropProperties = WinUIInteropProperties(),
    onReset: ((T) -> Unit)? = null,
    onRelease: (T) -> Unit = WinUIViewNoOpUpdate,
    update: (T) -> Unit = WinUIViewNoOpUpdate,
) {
    if (onReset != null) {
        ReusableComposeNode<WinUINode, WinUIApplier>(
            factory = createWinUIViewNodeFactory(factory),
            update = {
                updateWinUIViewHolderParams<T>(
                    modifier = modifier,
                    properties = properties,
                )
                set(onReset) { requireWinUIViewNode<T>().resetBlock = it }
                set(update) { requireWinUIViewNode<T>().updateBlock = it }
                set(onRelease) { requireWinUIViewNode<T>().releaseBlock = it }
            },
        )
    } else {
        ComposeNode<WinUINode, WinUIApplier>(
            factory = createWinUIViewNodeFactory(factory),
            update = {
                updateWinUIViewHolderParams<T>(
                    modifier = modifier,
                    properties = properties,
                )
                set(update) { requireWinUIViewNode<T>().updateBlock = it }
                set(onRelease) { requireWinUIViewNode<T>().releaseBlock = it }
            },
        )
    }
}

@Composable
private fun <T : UIElement> createWinUIViewNodeFactory(factory: () -> T): () -> WinUINode {
    return {
        WinUIViewNode(view = factory())
    }
}

private fun <T : UIElement> Updater<WinUINode>.updateWinUIViewHolderParams(
    modifier: Modifier,
    properties: WinUIInteropProperties,
) {
    set(modifier) { requireWinUIViewNode<T>().modifier = it }
    set(properties) { requireWinUIViewNode<T>().properties = it }
}

@Suppress("UNCHECKED_CAST")
private fun <T : UIElement> WinUINode.requireWinUIViewNode(): WinUIViewNode<T> {
    return this as WinUIViewNode<T>
}

private val WinUIViewNoOpUpdate: UIElement.() -> Unit = {}

private class WinUIViewNode<T : UIElement>(
    private val view: T,
) : WinUINode(view) {
    var modifier: Modifier = Modifier

    var properties: WinUIInteropProperties = WinUIInteropProperties()

    var updateBlock: (T) -> Unit = WinUIViewNoOpUpdate
        set(value) {
            field = value
            view.apply(value)
        }

    var resetBlock: (T) -> Unit = WinUIViewNoOpUpdate

    var releaseBlock: (T) -> Unit = WinUIViewNoOpUpdate

    override fun onReuse() {
        view.apply(resetBlock)
    }

    override fun onDeactivate() {
        view.apply(resetBlock)
    }

    override fun onRelease() {
        super.onRelease()
        view.apply(releaseBlock)
    }
}
