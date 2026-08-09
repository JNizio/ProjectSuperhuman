plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

android {
    namespace = "com.projectsuperhuman.next"
    compileSdk = 35

    defaultConfig {
        // Keep a separate package for the first 11.0.0 validation APK so it cannot overwrite
        // the mature signed app or its private data by accident.
        applicationId = "com.projectsuperhuman.next"
        minSdk = 23
        targetSdk = 35
        versionCode = 11000
        versionName = "11.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    sourceSets["main"].apply {
        // Compatibility resources/assets remain packaged for deliberate fallback routes during
        // the 11.0.0 validation cycle. Native Compose is still the launcher and primary UI.
        java.srcDir("../../app/src/main/java")
        res.srcDir("../../app/src/main/res")
        assets.srcDir("../../app/src/main/assets")
    }

    // Conservative release candidate: no R8/resource shrinking until on-device parity is signed off.
    buildTypes {
        getByName("release") {
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
