import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.projectsuperhuman.next"
    compileSdk = 36

    signingConfigs {
        create("projectSuperhuman") {
            storeFile = file("../../signing/project-superhuman-v97.keystore")
            storePassword = "PSH970-LocalFirst-2026-KeepSafe"
            keyAlias = "project-superhuman"
            keyPassword = "PSH970-LocalFirst-2026-KeepSafe"
        }
    }

    defaultConfig {
        applicationId = "com.projectsuperhuman.next"
        minSdk = 26
        targetSdk = 35
        versionCode = 11017
        versionName = "11.0.17"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    sourceSets["main"].apply {
        java.srcDir("../../app/src/main/java")
        res.srcDir("../../app/src/main/res")
        assets.srcDir("../../app/src/main/assets")
    }

    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("projectSuperhuman") }
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
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("org.opencv:opencv:4.13.0")
}
