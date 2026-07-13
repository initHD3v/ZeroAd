pluginManagement {
    val flutterSdkPath = run {
        val localProps = file("local.properties")
        if (localProps.exists()) {
            val properties = java.util.Properties()
            localProps.inputStream().use { properties.load(it) }
            properties.getProperty("flutter.sdk")
        } else {
            // Fallback for CI: derive from Flutter SDK's Gradle include
            System.getenv("FLUTTER_ROOT")
        }
    } ?: throw GradleException("flutter.sdk not set in local.properties and FLUTTER_ROOT not set")

    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
}

include(":app")
