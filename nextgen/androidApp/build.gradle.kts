import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    compilerOptions {
        // Compile Kotlin bytecode for Java 17 while allowing Android Studio to run Gradle on its
        // bundled JDK. Do not request a separate JDK 17 toolchain on developer machines.
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.projectsuperhuman.next"
    compileSdk = 35

    signingConfigs {
        create("projectSuperhuman") {
            storeFile = file("../../signing/project-superhuman-v97.keystore")
            storePassword = "PSH970-LocalFirst-2026-KeepSafe"
            keyAlias = "project-superhuman"
            keyPassword = "PSH970-LocalFirst-2026-KeepSafe"
        }
    }

    defaultConfig {
        // Separate package remains intentional during native parity validation so the mature app
        // and its private data cannot be overwritten by a test build.
        applicationId = "com.projectsuperhuman.next"
        minSdk = 23
        targetSdk = 35
        versionCode = 11006
        versionName = "11.0.6"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    sourceSets["main"].apply {
        // Native screens deliberately reuse the mature visual asset library while parity work is
        // underway; compatibility Java/resources also remain available for explicit fallbacks.
        java.srcDir("../../app/src/main/java")
        res.srcDir("../../app/src/main/res")
        assets.srcDir("../../app/src/main/assets")
    }

    buildTypes {
        getByName("debug") {
            // Local Android Studio runs must use the same permanent identity as checkpoint APKs so
            // they can install over the existing native validation app without wiping its data.
            signingConfig = signingConfigs.getByName("projectSuperhuman")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("projectSuperhuman")
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(compose.components.resources)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("org.opencv:opencv:4.13.0")
}
