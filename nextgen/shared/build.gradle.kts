plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.library")
    id("app.cash.sqldelight")
}

kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation("app.cash.sqldelight:runtime:2.1.0")
            implementation("app.cash.sqldelight:coroutines-extensions:2.1.0")
        }
        androidMain.dependencies {
            implementation("app.cash.sqldelight:android-driver:2.1.0")
        }
        iosMain.dependencies {
            implementation("app.cash.sqldelight:native-driver:2.1.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

sqldelight {
    databases {
        create("SuperhumanDatabase") {
            packageName.set("com.projectsuperhuman.next.db")
            verifyMigrations.set(true)
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
        }
    }
}

android {
    namespace = "com.projectsuperhuman.next.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 23
    }
    // Keep javac and Kotlin on the same JVM target. Android Studio validates this strictly and
    // refuses local builds when Java defaults to 1.8 while Kotlin compiles for JVM 17.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
