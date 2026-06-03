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
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinUISourceSetIsolationTest {
    @Test
    fun winuiSourceSetsDoNotReferenceDesktopAwtSwingOrSkiaLayer() {
        val moduleRoot = findUiModuleRoot()
        val forbiddenReferences = listOf(
            "java.awt.",
            "javax.swing.",
            "androidx.compose.ui.awt.",
            "org.jetbrains.skiko.SkiaLayer",
        )
        val offenders = listOf("winuiMain", "winuiJvmMain").flatMap { sourceSet ->
            kotlinFiles(moduleRoot.resolve("src/$sourceSet/kotlin")).flatMap { file ->
                val text = file.readText()
                forbiddenReferences.mapNotNull { reference ->
                    if (text.contains(reference)) {
                        "${moduleRoot.relativize(file)} references $reference"
                    } else {
                        null
                    }
                }
            }
        }

        assertTrue(
            offenders.isEmpty(),
            "WinUI source sets must stay independent from Desktop/AWT/Swing/SkiaLayer:\n" +
                offenders.joinToString(separator = "\n"),
        )
    }

    @Test
    fun winuiSourceSetsDoNotDependOnDesktopMain() {
        val moduleRoot = findUiModuleRoot()
        val buildScript = moduleRoot.resolve("build.gradle").readText()
        val forbiddenSourceSets = listOf("desktopMain", "desktopJvmMain")

        listOf("winuiMain", "winuiJvmMain").forEach { sourceSet ->
            val block = checkNotNull(sourceSetBlock(buildScript, sourceSet)) {
                "Could not find $sourceSet in compose/ui/ui/build.gradle."
            }
            forbiddenSourceSets.forEach { forbidden ->
                assertFalse(
                    block.contains("dependsOn($forbidden)"),
                    "WinUI source sets must not depend on desktop source sets: " +
                        "$sourceSet depends on $forbidden.",
                )
            }
        }
        assertTrue(
            checkNotNull(sourceSetBlock(buildScript, "winuiMain"))
                .contains("dependsOn(skikoMain)"),
            "WinUI should remain below skikoMain until skiko-winui provides the shared rendering sources.",
        )
    }

    @Test
    fun winuiJvmCompileSourceBridgeDoesNotCompileDesktopOrGenericSkikoSources() {
        val moduleRoot = findUiModuleRoot()
        val buildScript = moduleRoot.resolve("build.gradle").readText()
        val bridgeBlock = checkNotNull(compileKotlinWinuiJvmBridgeBlock(buildScript)) {
            "Could not find the compileKotlinWinuiJvm source bridge in compose/ui/ui/build.gradle."
        }
        val requiredRoots = listOf(
            "src/commonMain/kotlin",
            "src/jvmAndAndroidMain/kotlin",
            "src/winuiMain/kotlin",
            "src/winuiJvmMain/kotlin",
            "generated/kotlin-winrt/src/main/kotlin",
            "generatedWinRtAuthoringSources",
        )
        val forbiddenRoots = listOf(
            "src/desktopMain/kotlin",
            "src/skikoMain/kotlin",
        )

        requiredRoots.forEach { root ->
            assertTrue(
                bridgeBlock.contains(root),
                "WinUI JVM compile source bridge should include $root.",
            )
        }
        assertTrue(
            buildScript.contains("generated/kotlin-winrt-authoring/src/main/kotlin"),
            "WinUI JVM compile source bridge should define generatedWinRtAuthoringSources.",
        )
        forbiddenRoots.forEach { root ->
            assertFalse(
                bridgeBlock.contains(root),
                "WinUI JVM compile source bridge must not compile $root.",
            )
        }
    }

    @Test
    fun winuiJvmRuntimeUsesSkikoWinuiWithoutSkikoAwtNativeRuntime() {
        val classpath = System.getProperty("java.class.path")
            .split(System.getProperty("path.separator"))
            .map { it.lowercase() }

        assertTrue(
            classpath.any { it.contains("skiko-winui") },
            "WinUI JVM runtime classpath should include skiko-winui.",
        )

        // SKIKO-006: the current JVM Skiko API jar is still named skiko-awt,
        // so keep the guard focused on Desktop/AWT native runtime artifacts.
        val forbiddenArtifacts = listOf(
            "skiko-awt-runtime",
        )
        val offenders = classpath.filter { entry ->
            forbiddenArtifacts.any { artifact -> entry.contains(artifact) }
        }

        assertTrue(
            offenders.isEmpty(),
            "WinUI JVM runtime classpath must not include Skiko AWT/Desktop native runtime artifacts:\n" +
                offenders.joinToString(separator = "\n"),
        )
    }

    private fun kotlinFiles(root: Path): List<Path> {
        if (!root.exists()) return emptyList()
        Files.walk(root).use { paths ->
            return paths
                .filter { it.extension == "kt" }
                .toList()
        }
    }

    private fun findUiModuleRoot(): Path {
        val start = Paths.get("").toAbsolutePath()
        generateSequence(start) { it.parent }.forEach { candidate ->
            val direct = candidate.resolve("src/winuiMain/kotlin")
            if (direct.exists() && candidate.name == "ui") return candidate

            val fromRepoRoot = candidate.resolve("compose/ui/ui/src/winuiMain/kotlin")
            if (fromRepoRoot.exists()) return candidate.resolve("compose/ui/ui")
        }
        error("Could not find compose/ui/ui module root from $start.")
    }

    private fun sourceSetBlock(buildScript: String, sourceSet: String): String? {
        val start = buildScript.indexOf("$sourceSet {")
        if (start < 0) return null
        var depth = 0
        for (index in start until buildScript.length) {
            when (buildScript[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return buildScript.substring(start, index + 1)
                }
            }
        }
        return null
    }

    private fun compileKotlinWinuiJvmBridgeBlock(buildScript: String): String? {
        val start = buildScript.indexOf("task.name == \"compileKotlinWinuiJvm\"")
        if (start < 0) return null
        val blockStart = buildScript.indexOf('{', start)
        if (blockStart < 0) return null
        var depth = 0
        for (index in blockStart until buildScript.length) {
            when (buildScript[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return buildScript.substring(start, index + 1)
                }
            }
        }
        return null
    }
}
