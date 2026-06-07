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

import io.github.composefluent.winrt.gradle.GenerateWinRtProjectionsTask
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("AndroidXComposePlugin")
    id("kotlin-multiplatform")
    id("io.github.composefluent.winrt")
    alias(libs.plugins.kotlinSerialization)
}

val composeWinUiWindowsSdkVersion = providers
    .gradleProperty("composeWinUi.windowsSdkVersion")
    .orElse("10.0.26100.0")
val composeWinUiWindowsAppSdkVersion = providers
    .gradleProperty("composeWinUi.windowsAppSdkVersion")
    .orElse(providers.gradleProperty("kotlinWinRt.samples.windowsAppSdkVersion"))
    .orElse("2.1.3")
val kotlinWinRtVersion = providers
    .gradleProperty("kotlinWinRt.version")
    .orElse("0.1.0-SNAPSHOT")
val composeWinUiSkikoWinUiVersion = providers
    .gradleProperty("composeWinUi.skikoWinUiVersion")
    .orElse("0.0.0-SNAPSHOT")
val lifecycleVersion = providers
    .gradleProperty("artifactRedirection.version.androidx.lifecycle")
    .orElse("2.11.0-beta01")
val navigationEventVersion = providers
    .gradleProperty("artifactRedirection.version.androidx.navigationevent")
    .orElse("1.1.0-alpha01")
val composeVersion = providers
    .gradleProperty("artifactRedirection.version.androidx.compose")
    .orElse("1.12.0-alpha02")

val localWinUiJarProjects = listOf(
    ":compose:ui:ui",
    ":compose:ui:ui-graphics",
    ":compose:ui:ui-text",
)
val navigationWinUiCompileTasks = listOf(
    ":navigation:navigation-compose:compileKotlinWinuiJvm",
    ":navigation3:navigation3-ui:compileKotlinWinuiJvm",
)
val gradleWrapper = rootProject.layout.projectDirectory.file(
    if (System.getProperty("os.name").startsWith("Windows")) {
        "gradlew.bat"
    } else {
        "gradlew"
    }
)
val winUiMppSampleResourcesDir = layout.buildDirectory.dir("winui-mpp-sample-resources")
val winUiMppSampleResourceFiles = listOf(
    project.file("../demo/src/commonMain/resources/RobotoFlex-VariableFont.ttf"),
    project.file("../demo/src/desktopMain/resources/NotoColorEmoji.ttf"),
)
val winUiMppSampleSourceFiles = listOf(
    project.file("../demo/src/commonMain/kotlin/androidx/compose/mpp/demo/ImageViewer.kt"),
    project.file("../demo/src/winuiJvmMain/kotlin/androidx/compose/mpp/demo/Main.winui.kt"),
    project.file("../demo/src/winuiJvmMain/kotlin/androidx/compose/mpp/demo/MainJavaExec.winui.kt"),
)
val winUiMppSampleForbiddenSourceTokens = listOf(
    "androidx.compose.ui.awt",
    "java.awt.",
    "javax.swing.",
    "kotlinx.coroutines.swing",
    "org.jetbrains.skiko.SkiaLayer",
    "org.jetbrains.skiko.awt",
)
val winUiMppSampleApiSurface = linkedMapOf(
    "graphics" to listOf(
        "ImageBitmap(",
        "Canvas(",
        "LinearGradientShader",
        "drawIntoCanvas",
        "drawImage(image)",
        "readPixels(",
    ),
    "text" to listOf(
        "LocalFontFamilyResolver",
        "FontFamily",
        "Font(\"NotoColorEmoji\"",
        "fontFamilyResolver.preload",
    ),
    "pointer" to listOf(
        "pointerInput(Unit)",
        "detectTransformGestures",
        "onPointerEvent(PointerEventType.Scroll)",
        "detectTapGestures",
    ),
    "window-info" to listOf(
        "Window(",
        "appWindow.size",
        "currentComposeViewForTest",
    ),
    "density" to listOf(
        "LocalDensity.current",
        "maxWidth.toPx()",
        "maxHeight.toPx()",
    ),
)
val winUiMppSampleGuardedApiSurface = linkedMapOf(
    "keyboard" to listOf(
        "onPreviewKeyEvent",
        "onKeyEvent",
        "KeyEvent",
    ),
    "clipboard" to listOf(
        "LocalClipboard",
        "LocalClipboardManager",
        "ClipEntry",
    ),
    "uri" to listOf(
        "LocalUriHandler",
        "openUri",
    ),
    "focus" to listOf(
        "FocusRequester",
        "focusTarget",
        "focusable(",
    ),
    "popup" to listOf(
        "Popup(",
        "PopupProperties",
    ),
    "dialog" to listOf(
        "Dialog(",
        "DialogProperties",
    ),
    "drag-and-drop" to listOf(
        "dragAndDrop",
        "DragAndDrop",
    ),
    "accessibility" to listOf(
        "semantics",
        "contentDescription",
        "testTag",
    ),
)

fun localWinUiJar(path: String) = rootProject.project(path).provider {
    rootProject.project(path).tasks.named("winuiJvmJar", Jar::class).get().archiveFile.get().asFile
}

kotlin {
    jvmToolchain(25)
    jvm("winuiJvm")

    sourceSets {
        commonMain {
            kotlin.srcDir("../demo/src/commonMain/kotlin")
            kotlin.include("androidx/compose/mpp/demo/ImageViewer.kt")
            resources.srcDir("../demo/src/commonMain/resources")
            dependencies {
                implementation(libs.skiko)

                implementation(project(":compose:runtime:runtime"))
                implementation(project(":compose:ui:ui-geometry"))
                implementation(project(":compose:ui:ui-unit"))
                implementation(project(":compose:ui:ui-util"))
                implementation(files(localWinUiJarProjects.map(::localWinUiJar)))
                implementation("org.jetbrains.compose.foundation:foundation:1.10.0") {
                    exclude(group = "org.jetbrains.compose.runtime")
                    exclude(group = "org.jetbrains.compose.ui")
                }
                implementation("org.jetbrains.compose.foundation:foundation-layout:1.10.0") {
                    exclude(group = "org.jetbrains.compose.runtime")
                    exclude(group = "org.jetbrains.compose.ui")
                }
                implementation("androidx.compose.runtime:runtime-retain:${composeVersion.get()}")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.6")
                implementation("androidx.lifecycle:lifecycle-viewmodel-compose:${lifecycleVersion.get()}")
                implementation("androidx.savedstate:savedstate-compose:1.4.0")
                implementation("androidx.navigationevent:navigationevent-compose:${navigationEventVersion.get()}")
                implementation("io.github.compose-fluent:winrt-runtime:${kotlinWinRtVersion.get()}")
                implementation("io.github.compose-fluent:skiko-winui:${composeWinUiSkikoWinUiVersion.get()}")
            }
        }

        named("winuiJvmMain") {
            kotlin.srcDir("../demo/src/winuiJvmMain/kotlin")
            kotlin.include("androidx/compose/mpp/demo/Main.winui.kt")
            kotlin.include("androidx/compose/mpp/demo/MainJavaExec.winui.kt")
            resources.srcDir("../demo/src/desktopMain/resources")
            dependencies {
                runtimeOnly("io.github.compose-fluent:skiko-winui-windows:${composeWinUiSkikoWinUiVersion.get()}")
            }
        }
    }
}

winRt {
    application {}
    windowsSdk(composeWinUiWindowsSdkVersion.get(), includeExtensions = false)
    nugetPackage("Microsoft.WindowsAppSDK", composeWinUiWindowsAppSdkVersion.get())
    type("Windows.Foundation.Uri")
}

tasks.named<GenerateWinRtProjectionsTask>("generateWinRtProjections") {
    sourceRoots.setFrom(project.file("../demo/src/winuiJvmMain/kotlin"))
}

tasks.named("compileKotlinWinuiJvm") {
    dependsOn(localWinUiJarProjects.map { path -> "$path:winuiJvmJar" })
}

tasks.withType<KotlinCompile>().configureEach {
    if (name.contains("Winui")) {
        dependsOn("generateWinRtProjections")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_25)
            freeCompilerArgs.add("-Xjdk-release=25")
        }
    }
}

// SKIKO-007: keep the sample runtime classpath single-owner for WinRT projections
// while the published skiko-winui snapshot still bundles projection classes.
val stripSkikoWinUiProjectionClasses = tasks.register("stripSkikoWinUiProjectionClasses") {
    val runtimeClasspath = configurations.named("winuiJvmRuntimeClasspath")
    val skikoWinUiJar = runtimeClasspath.map { classpath ->
        classpath.filter { file ->
            file.name.startsWith("skiko-winui-") &&
                file.name.endsWith(".jar") &&
                !file.name.startsWith("skiko-winui-windows-")
        }
    }
    val outputFile = layout.buildDirectory.file(
        "skiko-winui-projection-free/skiko-winui-projection-free.jar"
    )
    inputs.files(skikoWinUiJar)
    outputs.file(outputFile)

    doLast {
        val sourceJar = skikoWinUiJar.get().files.singleOrNull()
            ?: error("Unable to find skiko-winui jar on WinUI MPP sample runtime classpath.")
        val externalProjectionEntries = projectionClassEntries(
            runtimeClasspath.get().files.filter { it != sourceJar }
        )
        val targetJar = outputFile.get().asFile
        targetJar.parentFile.mkdirs()
        ZipFile(sourceJar).use { input ->
            ZipOutputStream(targetJar.outputStream().buffered()).use { output ->
                val seen = mutableSetOf<String>()
                input.entries().asSequence()
                    .filter { entry ->
                        !isWinRtProjectionClass(entry.name) ||
                            entry.name !in externalProjectionEntries
                    }
                    .forEach { entry ->
                        if (seen.add(entry.name)) {
                            output.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })
                            if (!entry.isDirectory) {
                                input.getInputStream(entry).use { it.copyTo(output) }
                            }
                            output.closeEntry()
                        }
                    }
            }
        }
    }
}

fun isWinRtProjectionClass(name: String): Boolean =
    name.endsWith(".class") &&
        (name.startsWith("microsoft/") || name.startsWith("windows/"))

fun projectionClassEntries(files: Iterable<File>): Set<String> =
    buildSet {
        files.asSequence()
            .filter { it.isFile && it.extension == "jar" }
            .forEach { jar ->
                ZipFile(jar).use { zip ->
                    zip.entries().asSequence()
                        .filter { !it.isDirectory }
                        .map { it.name }
                        .filter(::isWinRtProjectionClass)
                        .forEach(::add)
                }
            }
    }

fun JavaExec.configureWinUIMppSampleJavaExec(
    taskDescription: String,
    reportName: String,
    requiredEvents: List<String>,
) {
    group = "verification"
    description = taskDescription
    dependsOn("compileKotlinWinuiJvm")
    dependsOn("stageWinRtRuntimeAssets")
    dependsOn("buildWinRtAuthoringHost")
    dependsOn("validateWinUIMppSamplePackaging")
    dependsOn(stripSkikoWinUiProjectionClasses)
    mainClass.set("androidx.compose.mpp.demo.MainJavaExec_winuiKt")
    classpath(
        winUiMppSampleResourcesDir,
        layout.buildDirectory.dir("classes/kotlin/winuiJvm/main"),
        configurations.named("winuiJvmRuntimeClasspath").map { runtimeClasspath ->
            runtimeClasspath.filter { file ->
                !file.name.startsWith("skiko-winui-") ||
                    file.name.startsWith("skiko-winui-windows-")
            }
        },
        stripSkikoWinUiProjectionClasses.flatMap {
            layout.buildDirectory.file("skiko-winui-projection-free/skiko-winui-projection-free.jar")
        },
    )
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("compose.winui.mpp.sample.autoExit", "true")
    val reportFile = layout.buildDirectory.file("validation/$reportName-events.txt")
    outputs.file(reportFile)
    doFirst {
        val report = reportFile.get().asFile
        report.delete()
        systemProperty("compose.winui.mpp.sample.validationReport", report.absolutePath)

        val classpathNames = classpath.files.map { it.name }
        winUiMppSampleResourceFiles.forEach { resource ->
            check(classpath.files.any { file ->
                file.isDirectory && file.resolve(resource.name).isFile
            }) {
                "WinUI MPP sample runtime classpath did not include staged resource ${resource.name}."
            }
        }
        check(classpathNames.any { it.contains("skiko-winui") }) {
            "WinUI MPP sample runtime classpath did not include skiko-winui."
        }
        check(classpathNames.none { it.contains("skiko-awt-runtime") }) {
            "WinUI MPP sample runtime classpath must not include Skiko AWT runtime artifacts: $classpathNames"
        }
        val projectionOwners = linkedMapMapOfProjectionOwners(classpath.files)
        val duplicates = projectionOwners
            .filterValues { it.size > 1 }
            .entries
            .sortedBy { it.key }
        check(duplicates.isEmpty()) {
            val sample = duplicates.take(50).joinToString(separator = "\n") { (entry, owners) ->
                "  $entry: ${owners.joinToString()}"
            }
            "WinUI MPP sample runtime classpath contains ${duplicates.size} duplicate WinRT projection classes:\n$sample"
        }
    }
    doLast {
        val report = reportFile.get().asFile
        check(report.isFile) {
            "WinUI MPP sample did not write validation report: ${report.absolutePath}"
        }
        val events = report.readLines().toSet()
        val missing = requiredEvents.filterNot(events::contains)
        check(missing.isEmpty()) {
            "WinUI MPP sample validation report ${report.absolutePath} is missing events: $missing. " +
                "Observed events: ${events.sorted()}"
        }
    }
}

val stageWinUIMppSampleResources = tasks.register<Copy>("stageWinUIMppSampleResources") {
    from(project.file("../demo/src/commonMain/resources"))
    from(project.file("../demo/src/desktopMain/resources"))
    into(winUiMppSampleResourcesDir)
}

tasks.register("validateWinUIMppSamplePackaging") {
    group = "verification"
    description = "Validates WinUI MPP sample resources and Windows App SDK runtime packaging."
    dependsOn(stageWinUIMppSampleResources)
    mustRunAfter("stageWinRtRuntimeAssets")
    inputs.files(winUiMppSampleResourceFiles)
    outputs.file(layout.buildDirectory.file("validation/winui-mpp-sample-packaging.txt"))

    doLast {
        fun requireFile(file: File, label: String) {
            check(file.isFile && file.length() > 0L) {
                "Missing or empty $label: ${file.absolutePath}"
            }
        }

        winUiMppSampleResourceFiles.forEach { resource ->
            requireFile(resource, "source sample resource")
            requireFile(
                winUiMppSampleResourcesDir.get().asFile.resolve(resource.name),
                "staged sample resource"
            )
        }

        val runtimeAssets = layout.buildDirectory.dir("kotlin-winrt/runtime-assets").get().asFile
        requireFile(runtimeAssets.resolve("resources.pri"), "application resources.pri")
        requireFile(runtimeAssets.resolve("Microsoft.UI.pri"), "Microsoft.UI component PRI")
        requireFile(
            runtimeAssets.resolve("Microsoft.UI.Xaml.Controls.pri"),
            "WinUI controls component PRI"
        )
        requireFile(
            runtimeAssets.resolve("WindowsAppSDK-SelfContained.manifest"),
            "Windows App SDK activation manifest"
        )
        requireFile(
            runtimeAssets.resolve("kotlin-winrt-process.manifest"),
            "process compatibility manifest"
        )
        requireFile(runtimeAssets.resolve("Microsoft.ui.xaml.dll"), "WinUI runtime DLL")
        requireFile(runtimeAssets.resolve("Microsoft.UI.Xaml.Controls.dll"), "WinUI controls DLL")
        requireFile(
            runtimeAssets.resolve("en-us/Microsoft.ui.xaml.dll.mui"),
            "default language WinUI MUI asset"
        )

        val report = outputs.files.singleFile
        report.parentFile.mkdirs()
        report.writeText(
            buildString {
                appendLine("WinUI MPP sample packaging validation passed.")
                appendLine("fonts=${winUiMppSampleResourceFiles.joinToString { it.name }}")
                appendLine("images=none in selected WinUI sample resource roots")
                appendLine("strings=none in selected WinUI sample resource roots")
                appendLine("runtimeAssets=${runtimeAssets.absolutePath}")
                appendLine("defaultLanguage=en-us")
            }
        )
    }
}

tasks.register("validateWinUIMppSampleSourceIsolation") {
    group = "verification"
    description = "Validates the WinUI MPP sample does not use Desktop/AWT/Swing-only sources or runtimes."
    inputs.files(winUiMppSampleSourceFiles)
    outputs.file(layout.buildDirectory.file("validation/winui-mpp-sample-source-isolation.txt"))

    doLast {
        winUiMppSampleSourceFiles.forEach { sourceFile ->
            check(sourceFile.isFile) {
                "Missing WinUI MPP sample source file: ${sourceFile.absolutePath}"
            }
            val source = sourceFile.readText()
            val forbiddenTokens = winUiMppSampleForbiddenSourceTokens.filter(source::contains)
            check(forbiddenTokens.isEmpty()) {
                "WinUI MPP sample source ${sourceFile.absolutePath} uses Desktop/AWT/Swing-only APIs: " +
                    forbiddenTokens
            }
        }

        val runtimeArtifacts = configurations.named("winuiJvmRuntimeClasspath").get().files
        val forbiddenRuntimeArtifacts = runtimeArtifacts
            .map { it.name }
            .filter { name ->
                name.startsWith("skiko-awt-runtime") ||
                    name.startsWith("skiko-awt-runtime-windows") ||
                    name.startsWith("kotlinx-coroutines-swing")
            }
        check(forbiddenRuntimeArtifacts.isEmpty()) {
            "WinUI MPP sample runtime classpath contains Desktop/AWT/Swing-only runtime artifacts: " +
                forbiddenRuntimeArtifacts
        }

        val report = outputs.files.singleFile
        report.parentFile.mkdirs()
        report.writeText(
            buildString {
                appendLine("WinUI MPP sample source isolation validation passed.")
                appendLine("sources=${winUiMppSampleSourceFiles.joinToString { it.name }}")
                appendLine("forbiddenSourceTokens=${winUiMppSampleForbiddenSourceTokens.joinToString()}")
                appendLine("forbiddenRuntimeArtifacts=skiko-awt-runtime*, kotlinx-coroutines-swing*")
            }
        )
    }
}

tasks.register("validateWinUIMppSampleApiSurface") {
    group = "verification"
    description = "Inventories the selected original MPP sample Compose UI API surface used by WinUI."
    inputs.files(winUiMppSampleSourceFiles)
    outputs.file(layout.buildDirectory.file("validation/winui-mpp-sample-api-surface.txt"))

    doLast {
        val sourceByFile = winUiMppSampleSourceFiles.associateWith { sourceFile ->
            check(sourceFile.isFile) {
                "Missing WinUI MPP sample source file: ${sourceFile.absolutePath}"
            }
            sourceFile.readText()
        }
        val combinedSource = sourceByFile.values.joinToString(separator = "\n")
        val missingRequiredTokens = winUiMppSampleApiSurface.flatMap { (category, tokens) ->
            tokens.filterNot(combinedSource::contains).map { token -> "$category: $token" }
        }
        check(missingRequiredTokens.isEmpty()) {
            "WinUI MPP sample API surface inventory is missing expected source tokens: " +
                missingRequiredTokens
        }

        val unguardedOptionalTokens = winUiMppSampleGuardedApiSurface.flatMap { (category, tokens) ->
            tokens.filter(combinedSource::contains).map { token -> "$category: $token" }
        }
        check(unguardedOptionalTokens.isEmpty()) {
            "WinUI MPP sample uses API categories that are not part of the selected WinUI " +
                "sample scope without adding focused validation: $unguardedOptionalTokens"
        }

        val report = outputs.files.singleFile
        report.parentFile.mkdirs()
        report.writeText(
            buildString {
                appendLine("WinUI MPP sample API surface inventory passed.")
                appendLine("sourceFiles=${winUiMppSampleSourceFiles.joinToString { it.name }}")
                appendLine("requiredCategories=${winUiMppSampleApiSurface.keys.joinToString()}")
                appendLine("guardedAbsentCategories=${winUiMppSampleGuardedApiSurface.keys.joinToString()}")
                winUiMppSampleApiSurface.forEach { (category, tokens) ->
                    appendLine("$category=${tokens.joinToString()}")
                }
            }
        )
    }
}

tasks.register("validateWinUiKotlinWinRtKmpGraphBaseline") {
    group = "verification"
    description = "Validates the compose-winui kotlin-winrt KMP graph baseline."
    dependsOn("compileKotlinWinuiJvm")
    dependsOn(localWinUiJarProjects.map { path -> "$path:winuiJvmJar" })
    inputs.file(layout.projectDirectory.file("build.gradle.kts"))
    inputs.files(localWinUiJarProjects.map(::localWinUiJar))
    outputs.file(layout.buildDirectory.file("validation/winui-kotlin-winrt-kmp-graph.txt"))

    doLast {
        val buildScript = layout.projectDirectory.file("build.gradle.kts").asFile.readText()
        val requiredBuildScriptTokens = listOf(
            "jvm(\"winuiJvm\")",
            "kotlin.srcDir(\"../demo/src/commonMain/kotlin\")",
            "kotlin.srcDir(\"../demo/src/winuiJvmMain/kotlin\")",
            "dependsOn(localWinUiJarProjects.map { path -> \"${'$'}path:winuiJvmJar\" })",
        )
        val missingBuildScriptTokens = requiredBuildScriptTokens.filterNot(buildScript::contains)
        check(missingBuildScriptTokens.isEmpty()) {
            "WinUI MPP sample KMP graph is missing expected source-set/task wiring: " +
                missingBuildScriptTokens
        }

        val uiBuildDir = rootProject.project(":compose:ui:ui").layout.buildDirectory.get().asFile
        val uiIdentity = uiBuildDir.resolve("generated/kotlin-winrt/identity/kotlin-winrt.json")
        check(uiIdentity.isFile && uiIdentity.length() > 0L) {
            "Missing compose-ui WinRT identity: ${uiIdentity.absolutePath}"
        }
        val uiIdentityText = uiIdentity.readText()
        val requiredIdentityTokens = listOf(
            "\"model\": \"library\"",
            "\"Microsoft.UI.Xaml.Application\"",
            "\"Microsoft.UI.Xaml.Controls.Canvas\"",
            "\"authoredHostManifests\"",
            "\"compilerSupportManifests\"",
            "ui.host.json",
        )
        val missingIdentityTokens = requiredIdentityTokens.filterNot(uiIdentityText::contains)
        check(missingIdentityTokens.isEmpty()) {
            "compose-ui WinRT identity is missing expected transitive identity data: " +
                missingIdentityTokens
        }

        val localJars = localWinUiJarProjects.associateWith { path -> localWinUiJar(path).get() }
        localJars.forEach { (path, jar) ->
            check(jar.isFile && jar.length() > 0L) {
                "Missing local WinUI jar for $path: ${jar.absolutePath}"
            }
        }
        val uiJar = localJars.getValue(":compose:ui:ui")
        val uiJarEntries = ZipFile(uiJar).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .map { it.name }
                .toSet()
        }
        val requiredUiJarEntries = listOf(
            "io/github/composefluent/winrt/projections/support/WinRTCompilerSupportManifest.class",
            "io/github/composefluent/winrt/projections/support/WinRTAuthoringTypeDetailsRegistrar_ui.class",
            "kotlin-winrt/type-index.tsv",
            "kotlin-winrt-authoring/ui.host.json",
            "kotlin-winrt-authoring/ui.winmd",
        )
        val missingUiJarEntries = requiredUiJarEntries.filterNot(uiJarEntries::contains)
        check(missingUiJarEntries.isEmpty()) {
            "compose-ui WinUI jar is missing kotlin-winrt support artifacts: " +
                missingUiJarEntries
        }

        val localProjectionOwners = linkedMapMapOfProjectionOwners(localJars.values.toSet())
            .filterValues { it.size > 1 }
        check(localProjectionOwners.isEmpty()) {
            val sample = localProjectionOwners.entries.take(50).joinToString(separator = "\n") {
                    (entry, owners) ->
                "  $entry: ${owners.joinToString()}"
            }
            "Local WinUI jars contain duplicate generated projection owners:\n$sample"
        }

        val demoClassesDir = layout.buildDirectory
            .dir("classes/kotlin/winuiJvm/main")
            .get()
            .asFile
        val requiredDemoFiles = listOf(
            demoClassesDir.resolve(
                "io/github/composefluent/winrt/projections/support/" +
                    "WinRTCompilerSupportManifest.class"
            ),
            demoClassesDir.resolve("kotlin-winrt/type-index.tsv"),
            demoClassesDir.resolve("kotlin-winrt/authored-candidates.tsv"),
            demoClassesDir.resolve("kotlin-winrt-authoring/demo-winui.host.json"),
            demoClassesDir.resolve("kotlin-winrt-authoring/demo-winui.winmd"),
        )
        requiredDemoFiles.forEach { output ->
            check(output.isFile) {
                "Missing demo-winui kotlin-winrt output: ${output.absolutePath}"
            }
        }
        val requiredNonEmptyDemoOutputs = requiredDemoFiles.filterNot {
            val path = it.path.replace(File.separatorChar, '/')
            path.endsWith("/kotlin-winrt/type-index.tsv") ||
                path.endsWith("/kotlin-winrt/authored-candidates.tsv")
        }
        requiredNonEmptyDemoOutputs.forEach { output ->
            check(output.length() > 0L) {
                "Empty demo-winui kotlin-winrt output: ${output.absolutePath}"
            }
        }

        val report = outputs.files.singleFile
        report.parentFile.mkdirs()
        report.writeText(
            buildString {
                appendLine("compose-winui kotlin-winrt KMP graph baseline validation passed.")
                appendLine("customizedSourceSets=winuiJvmMain includes selected common and WinUI sources")
                appendLine("transitiveWinRtIdentity=${uiIdentity.absolutePath}")
                appendLine("supportArtifactJar=${uiJar.name}")
                appendLine("localProjectionOwnerJars=${localJars.values.joinToString { it.name }}")
                appendLine("duplicateLocalProjectionOwners=0")
                appendLine("demoWinRtOutputs=${requiredDemoFiles.joinToString { it.name }}")
            }
        )
    }
}

val validateWinUINavigationCompileOnly = tasks.register<Exec>("validateWinUINavigationCompileOnly") {
    group = "verification"
    description = "Compiles the Navigation Compose and Navigation3 UI WinUI JVM targets."
    commandLine(
        gradleWrapper.asFile.absolutePath,
        *navigationWinUiCompileTasks.toTypedArray(),
        "-PcomposeWinUi.enableJvmTarget=true",
        "--no-configuration-cache",
        "--no-configure-on-demand",
        "--no-build-cache",
        "--console=plain",
    )
}

tasks.register("validateWinUIMppSampleCompileOnly") {
    group = "verification"
    description = "Compiles the original MPP demo through the compose-winui JVM target."
    dependsOn("compileKotlinWinuiJvm")
    dependsOn(validateWinUINavigationCompileOnly)
    dependsOn("validateWinUiKotlinWinRtKmpGraphBaseline")
}

val smokeWinUIMppSampleLaunchWindow = tasks.register<JavaExec>("smokeWinUIMppSampleLaunchWindow") {
    configureWinUIMppSampleJavaExec(
        taskDescription = "Runs the WinUI MPP sample until the application window composes.",
        reportName = "winui-mpp-sample-launch-window",
        requiredEvents = listOf(
            "window-content-composed",
            "window-composed",
        ),
    )
}

val smokeWinUIMppSampleRenderOutput = tasks.register<JavaExec>("smokeWinUIMppSampleRenderOutput") {
    configureWinUIMppSampleJavaExec(
        taskDescription = "Runs the WinUI MPP sample until the image viewer reaches a laid-out frame.",
        reportName = "winui-mpp-sample-render-output",
        requiredEvents = listOf(
            "image-bitmap-drawn",
            "positive-layout-size",
            "image-viewer-composed",
            "render-direct3d",
            "render-positive-size",
            "render-state-size-matched",
            "non-empty-draw-bounds",
            "frame-observed",
        ),
    )
}

val smokeWinUIMppSampleInputFocus = tasks.register<JavaExec>("smokeWinUIMppSampleInputFocus") {
    configureWinUIMppSampleJavaExec(
        taskDescription = "Runs the WinUI MPP sample until pointer/input handlers compose on a live window.",
        reportName = "winui-mpp-sample-input-focus",
        requiredEvents = listOf(
            "window-positive-size",
            "input-handlers-composed",
        ),
    )
}

val smokeWinUIMppSampleResourceLoading = tasks.register<JavaExec>("smokeWinUIMppSampleResourceLoading") {
    configureWinUIMppSampleJavaExec(
        taskDescription = "Runs the WinUI MPP sample until bundled resources load through the runtime classpath.",
        reportName = "winui-mpp-sample-resource-loading",
        requiredEvents = listOf("font-resource-loaded"),
    )
}

val smokeWinUIMppSampleShutdownDisposal = tasks.register<JavaExec>("smokeWinUIMppSampleShutdownDisposal") {
    configureWinUIMppSampleJavaExec(
        taskDescription = "Runs the WinUI MPP sample until auto-exit disposes the window content.",
        reportName = "winui-mpp-sample-shutdown-disposal",
        requiredEvents = listOf(
            "exit-requested",
            "window-content-disposed",
        ),
    )
}

tasks.register<JavaExec>("runWinUIMppSample") {
    dependsOn("validateWinUIMppSampleCompileOnly")
    dependsOn("validateWinUIMppSampleSourceIsolation")
    dependsOn("validateWinUIMppSampleApiSurface")
    dependsOn(smokeWinUIMppSampleLaunchWindow)
    dependsOn(smokeWinUIMppSampleRenderOutput)
    dependsOn(smokeWinUIMppSampleInputFocus)
    dependsOn(smokeWinUIMppSampleResourceLoading)
    dependsOn(smokeWinUIMppSampleShutdownDisposal)
    configureWinUIMppSampleJavaExec(
        taskDescription = "Runs the original MPP demo through the compose-winui JVM target.",
        reportName = "winui-mpp-sample",
        requiredEvents = listOf(
            "window-content-composed",
            "window-composed",
            "window-positive-size",
            "font-resource-loaded",
            "image-bitmap-drawn",
            "positive-layout-size",
            "image-viewer-composed",
            "render-direct3d",
            "render-positive-size",
            "render-state-size-matched",
            "non-empty-draw-bounds",
            "input-handlers-composed",
            "frame-observed",
            "exit-requested",
            "window-content-disposed",
        ),
    )
}

fun linkedMapMapOfProjectionOwners(files: Set<File>): Map<String, Set<String>> {
    val owners = linkedMapOf<String, MutableSet<String>>()
    files.asSequence()
        .filter { it.isFile && it.extension == "jar" }
        .forEach { jar ->
            ZipFile(jar).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory }
                    .map { it.name }
                    .filter {
                        it.endsWith(".class") &&
                            (it.startsWith("microsoft/") || it.startsWith("windows/"))
                    }
                    .forEach { entry ->
                        owners.getOrPut(entry) { linkedSetOf() }.add(jar.name)
                    }
            }
        }
    return owners
}
