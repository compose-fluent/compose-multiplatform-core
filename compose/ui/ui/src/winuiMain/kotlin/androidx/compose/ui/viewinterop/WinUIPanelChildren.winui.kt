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

import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.IID
import io.github.composefluent.winrt.runtime.IUnknownReference
import io.github.composefluent.winrt.runtime.PlatformAbi
import microsoft.ui.xaml.controls.Panel
import microsoft.ui.xaml.controls.UIElementCollection

internal val Panel.requiredChildren: UIElementCollection
    get() {
        val projected = runCatching { children }.getOrNull()
        if (projected != null) return projected

        // KWINRT-008: Panel.children is public, but the generated JVM projection
        // currently cannot wrap this object-returning interface member reliably.
        nativeObject.queryInterface(Panel.Metadata.DEFAULT_INTERFACE_IID).getOrThrow().use { panel ->
            PlatformAbi.confinedScope().use { scope ->
                val result = PlatformAbi.allocatePointerSlot(scope)
                HResult(
                    ComVtableInvoker.invokeArgs(
                        instance = panel.pointer,
                        slot = Panel.Metadata.CHILDREN_GETTER_SLOT,
                        arg0 = result,
                    ),
                ).requireSuccess("Panel.Children get")
                val children = PlatformAbi.readPointer(result)
                check(!PlatformAbi.isNull(children)) {
                    "WinUI Panel children collection is not available."
                }
                return UIElementCollection.Metadata.wrap(
                    IUnknownReference(
                        PlatformAbi.toRawComPtr(children),
                        IID.IUnknown,
                        preventReleaseOnDispose = false,
                    ),
                )
            }
        }
    }
