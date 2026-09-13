import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    kotlin("plugin.compose") version "2.3.21"
    id("org.jetbrains.compose") version "1.7.3"
    id("com.github.node-gradle.node") version "7.0.2"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")
    implementation("org.apache.poi:poi:5.5.1")
    implementation("org.apache.poi:poi-ooxml:5.5.1")
    implementation("commons-io:commons-io:2.21.0")
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation("com.fasterxml.jackson.core:jackson-core:2.21.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.4.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.13.4")

    testImplementation(kotlin("test"))
}

java {
    toolchain{
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}
kotlin {
    jvmToolchain(25)
}

compose.desktop {
    application {
        mainClass = "MainKt"
        jvmArgs("--enable-native-access=ALL-UNNAMED")
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Genshin-Analyzer-NEXT"
            packageVersion = "1.0.0"
        }
    }
}

node {
    version.set("18.16.0")
    download.set(true)
    distBaseUrl.set("https://npmmirror.com/mirrors/node")
    nodeProjectDir.set(file("${projectDir}/proxy"))
}

tasks.named<com.github.gradle.node.npm.task.NpmInstallTask>("npmInstall") {
    environment.set(mapOf("npm_config_registry" to "https://registry.npmmirror.com"))
}

tasks.named("processResources") {
    dependsOn("npmInstall")
}

tasks.withType<JavaExec> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "MainKt"
    }

    from({
        configurations.runtimeClasspath.get().filter { it.exists() }.map {
            if (it.isDirectory) it else zipTree(it)
        }
    })


    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")

    archiveFileName.set("Genshin-Analyzer-NEXT-${version}.jar")
}



