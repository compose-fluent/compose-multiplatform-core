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

tasks.register<JavaExec>("runWinUIMppSample") {
    group = "verification"
    description = "Runs the original MPP demo through the compose-winui JVM target."
    dependsOn("compileKotlinWinuiJvm")
    dependsOn("stageWinRtRuntimeAssets")
    dependsOn("buildWinRtAuthoringHost")
    dependsOn(stripSkikoWinUiProjectionClasses)
    mainClass.set("androidx.compose.mpp.demo.MainJavaExec_winuiKt")
    classpath(
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
    doFirst {
        val classpathNames = classpath.files.map { it.name }
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
