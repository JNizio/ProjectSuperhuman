import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.util.zip.ZipInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

val repDbAssets = layout.buildDirectory.dir("generated/repdbAssets")
val syncRepDb by tasks.registering {
    outputs.dir(repDbAssets)
    doLast {
        val out = repDbAssets.get().asFile
        val marker = File(out, "repdb/exercises.json")
        if (marker.exists()) return@doLast
        out.deleteRecursively(); out.mkdirs()
        val zip = File(temporaryDir, "repdb.zip")
        URI("https://codeload.github.com/RepDB/exercise-dataset/zip/refs/heads/main").toURL().openStream().use { input -> zip.outputStream().use { input.copyTo(it) } }
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                val root = "exercise-dataset-main/"
                val relative = e.name.removePrefix(root)
                val wanted = relative == "exercises.json" || relative.startsWith("images/flat/")
                if (wanted && !e.isDirectory) {
                    val target = File(out, "repdb/$relative")
                    target.parentFile.mkdirs()
                    target.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
            }
        }
        check(marker.exists()) { "RepDB exercises.json was not extracted" }
    }
}

android {
    namespace = "com.projectsuperhuman.next"
    compileSdk = 36
    ndkVersion = "27.3.13750724"
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
        minSdk = 26; targetSdk = 35
        versionCode = 11221; versionName = "11.2.21"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
    sourceSets["main"].apply {
        java.srcDir("../../app/src/main/java")
        res.srcDir("../../app/src/main/res")
        assets.srcDir("../../app/src/main/assets")
        assets.srcDir(repDbAssets)
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("projectSuperhuman") }
        getByName("release") { signingConfig = signingConfigs.getByName("projectSuperhuman"); isMinifyEnabled = false; isShrinkResources = false }
    }
}

tasks.named("preBuild").configure { dependsOn(syncRepDb) }

dependencies {
    implementation(project(":shared"))
    implementation(compose.runtime); implementation(compose.foundation); implementation(compose.material3); implementation(compose.ui); implementation(compose.components.resources)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("org.opencv:opencv:4.13.0")
}
