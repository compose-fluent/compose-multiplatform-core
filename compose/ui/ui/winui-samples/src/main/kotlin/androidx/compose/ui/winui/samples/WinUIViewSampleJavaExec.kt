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

import io.github.composefluent.winrt.runtime.WinRtWindowsAppSdkBootstrap

/**
 * JavaExec-only launcher. The generated native host performs Windows App SDK
 * bootstrap before creating the JVM; repository-local JavaExec tasks need to
 * do that explicitly before entering the same sample main.
 */
fun main(args: Array<String>) {
    WinRtWindowsAppSdkBootstrap.initialize().use {
        Class.forName("androidx.compose.ui.winui.samples.WinUIViewSampleKt")
            .getMethod("main", Array<String>::class.java)
            .invoke(null, args as Any)
    }
}
