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

package androidx.compose.ui.platform

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal actual fun winUISystemBooleanProperty(name: String): Boolean =
    System.getProperty(name)?.toBooleanStrictOrNull() ?: false

internal actual fun winUIDebugLog(
    tag: String,
    message: String,
) {
    val line = "[compose-winui:$tag] $message"
    println(line)

    val logFile = System.getProperty("compose.winui.debug.logFile") ?: return
    runCatching {
        Files.writeString(
            Path.of(logFile),
            line + System.lineSeparator(),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }
}
