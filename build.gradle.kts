plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("org.jetbrains.intellij") version "1.17.2"
}

group = "com.shaoYe.reader"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.positiondev.epublib:epublib-core:3.1")
    implementation("net.sf.kxml:kxml2:2.3.0")
    // Jsoup for robust HTML manipulation
    implementation("org.jsoup:jsoup:1.17.2")
}

intellij {
    version.set("2023.3.3")
    type.set("IC")
    plugins.set(listOf("com.intellij.java"))
}

kotlin {
    jvmToolchain(17)
}

tasks {
    patchPluginXml {
        sinceBuild.set("231")
        untilBuild.set(provider { null })
    }
}