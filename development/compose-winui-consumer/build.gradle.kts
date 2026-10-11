import java.util.zip.ZipFile

plugins {
    kotlin("multiplatform") version "2.4.0"
    kotlin("plugin.compose") version "2.4.0"
}

kotlin {
    jvmToolchain(25)
    jvm("winuiJvm")
    mingwX64("winuiMingw")
    iosArm64()
    sourceSets.commonMain.dependencies {
        implementation("io.github.compose-fluent.compose.material3:material3:1.12.0-alpha01")
        implementation("io.github.compose-fluent.compose.material3.adaptive:adaptive-navigation:1.3.0-alpha01")
        implementation("io.github.compose-fluent.androidx.navigation:navigation-compose:2.10.0-alpha01")
        implementation("io.github.compose-fluent.androidx.navigation3:navigation3-ui:1.1.0-alpha01")
        implementation("io.github.compose-fluent.androidx.window:window-core:1.5.0-alpha01")
    }
}

tasks.register("verifyWinUiJvmClasspath") {
    doLast {
        val forbidden = listOf("java/awt/", "javax/swing/", "java.awt.", "javax.swing.", "org/jetbrains/skiko/awt/")
        val jars = configurations.getByName("winuiJvmRuntimeClasspath").resolve().filter { it.extension == "jar" }
        val violations = mutableListOf<String>()
        jars.forEach { jar ->
            ZipFile(jar).use { zip ->
                zip.entries().asSequence().filter { it.name.endsWith(".class") }.forEach { entry ->
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    val constants = bytes.toString(Charsets.ISO_8859_1)
                    if (forbidden.any(constants::contains)) violations += "${jar.name}: ${entry.name}"
                }
            }
        }
        check(violations.isEmpty()) { "AWT/Swing references in WinUI runtime:\n${violations.joinToString("\n")}" }
        println("Verified ${jars.size} JVM runtime jars without AWT/Swing references")
    }
}

tasks.register("verifyUpstreamRedirects") {
    doLast {
        val files = configurations.getByName("iosArm64CompileKlibraries").resolve()
        check(files.isNotEmpty())
        println("Resolved ${files.size} iOS dependencies through upstream redirects; no iOS binaries are built here")
    }
}
