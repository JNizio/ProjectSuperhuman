pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // sherpa-onnx publishes its Android AAR as a versioned GitHub release asset.
        // This repository is deliberately pinned to one reviewed HTTPS release/version.
        ivy {
            name = "SherpaOnnxAndroidRelease"
            url = uri("https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.4")
            patternLayout { artifact("sherpa-onnx-[revision].aar") }
            metadataSources { artifact() }
            content { includeModule("k2-fsa", "sherpa-onnx") }
        }
    }
}

rootProject.name = "ProjectSuperhumanNext"
include(":shared")
include(":androidApp")
