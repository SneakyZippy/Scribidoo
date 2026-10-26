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
        javaHome = "C:/Program Files/Java/jdk-23"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe
            )
            packageName = "Scribidoo"
            packageVersion = "1.0.0"
        }
    }
}
