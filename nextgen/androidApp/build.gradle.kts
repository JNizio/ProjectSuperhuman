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

fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
fun secretProperty(name: String) = providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull?.takeIf { it.isNotBlank() }

val trudyGeminiApiKey = secretProperty("TRUDY_GEMINI_API_KEY").orEmpty()
val trudyRuntimeMode = providers.gradleProperty("TRUDY_RUNTIME_MODE")
    .orElse(if (trudyGeminiApiKey.isNotBlank()) "HOSTED" else "DETERMINISTIC")
val trudyHostedProviderId = providers.gradleProperty("TRUDY_HOSTED_PROVIDER_ID").orElse("gemini")
val trudyHostedModelId = providers.gradleProperty("TRUDY_HOSTED_MODEL_ID").orElse("gemini-3.5-flash")
val trudyHostedEndpoint = providers.gradleProperty("TRUDY_HOSTED_ENDPOINT")
    .orElse("https://generativelanguage.googleapis.com/v1beta/models")
val trudyLocalModelId = providers.gradleProperty("TRUDY_LOCAL_MODEL_ID").orElse("local")

val signingStoreFile = secretProperty("SUPERHUMAN_SIGNING_STORE_FILE")
val signingStorePassword = secretProperty("SUPERHUMAN_SIGNING_STORE_PASSWORD")
val signingKeyAlias = secretProperty("SUPERHUMAN_SIGNING_KEY_ALIAS")
val signingKeyPassword = secretProperty("SUPERHUMAN_SIGNING_KEY_PASSWORD")
val configuredSigning = listOf(signingStoreFile, signingStorePassword, signingKeyAlias, signingKeyPassword).all { it != null }

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
        if (configuredSigning) {
            create("projectSuperhuman") {
                storeFile = file(requireNotNull(signingStoreFile))
                storePassword = requireNotNull(signingStorePassword)
                keyAlias = requireNotNull(signingKeyAlias)
                keyPassword = requireNotNull(signingKeyPassword)
            }
        }
    }
    defaultConfig {
        applicationId = "com.projectsuperhuman.next"
        minSdk = 26; targetSdk = 35
        versionCode = 11301; versionName = "11.3.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        buildConfigField("String", "TRUDY_RUNTIME_MODE", trudyRuntimeMode.get().asBuildConfigString())
        buildConfigField("String", "TRUDY_HOSTED_PROVIDER_ID", trudyHostedProviderId.get().asBuildConfigString())
        buildConfigField("String", "TRUDY_HOSTED_MODEL_ID", trudyHostedModelId.get().asBuildConfigString())
        buildConfigField("String", "TRUDY_HOSTED_ENDPOINT", trudyHostedEndpoint.get().asBuildConfigString())
        buildConfigField("String", "TRUDY_LOCAL_MODEL_ID", trudyLocalModelId.get().asBuildConfigString())
        buildConfigField("String", "TRUDY_GEMINI_API_KEY", trudyGeminiApiKey.asBuildConfigString())
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets["main"].apply {
        java.srcDir("../../app/src/main/java")
        res.srcDir("../../app/src/main/res")
        assets.srcDir("../../app/src/main/assets")
        assets.srcDir(repDbAssets)
        // Canonical offline condition/symptom corpus used by Trudy's Android retrieval adapter.
        assets.srcDir("../medical-knowledge")
    }
    buildTypes {
        getByName("debug") {
            if (configuredSigning) signingConfig = signingConfigs.getByName("projectSuperhuman")
        }
        getByName("release") {
            if (configuredSigning) signingConfig = signingConfigs.getByName("projectSuperhuman")
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}

tasks.named("preBuild").configure { dependsOn(syncRepDb) }

dependencies {
    implementation(project(":shared"))
    implementation(compose.runtime); implementation(compose.foundation); implementation(compose.material3); implementation(compose.ui); implementation(compose.components.resources)
    implementation("org.jetbrains.compose.ui:ui-tooling-preview:1.11.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("k2-fsa:sherpa-onnx:1.13.4@aar")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("org.opencv:opencv:4.13.0")
    debugImplementation("org.jetbrains.compose.ui:ui-tooling:1.11.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4-android:1.11.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.11.2")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
