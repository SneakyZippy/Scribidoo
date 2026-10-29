plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            kotlin.srcDirs(
                "src/desktopMain/kotlin",
                "../shared/src/commonMain/kotlin"
            )
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.scribidoo.desktop.MainKt"
        val customJdk = file("C:/Program Files/Java/jdk-23")
        if (customJdk.exists()) {
            javaHome = customJdk.absolutePath
        }
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe
            )
            packageName = "Scribidoo"
            packageVersion = "1.0.0"
            description = "Scribidoo - Handwritten note-taking and sketching app"
            copyright = "© 2026 Scribidoo"
            vendor = "Scribidoo"

            windows {
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
                menuGroup = "Scribidoo"
                upgradeUuid = "8c6b7501-143d-4c31-97b7-6f6f9661fef4"
                iconFile.set(project.file("src/desktopMain/resources/icon.ico"))
            }
        }
    }
}

val copyDistributableToDist = tasks.register<Copy>("copyDistributableToDist") {
    group = "distribution"
    description = "Copies the portable application folder directly to root dist/Scribidoo-Portable"
    dependsOn("createDistributable")
    from(layout.buildDirectory.dir("compose/binaries/main/app/Scribidoo"))
    into(rootProject.layout.projectDirectory.dir("dist/Scribidoo-Portable"))
}

tasks.register<org.gradle.api.tasks.bundling.Zip>("packagePortableZip") {
    group = "distribution"
    description = "Packages the standalone portable desktop application into a zip archive in dist/"
    dependsOn("createDistributable", copyDistributableToDist)
    from(layout.buildDirectory.dir("compose/binaries/main/app/Scribidoo"))
    archiveFileName.set("Scribidoo-Portable.zip")
    destinationDirectory.set(rootProject.layout.projectDirectory.dir("dist"))
}
