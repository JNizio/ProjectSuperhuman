package com.projectsuperhuman.next

/** Audited native-runtime compatibility metadata for the production Kokoro Android adapter. */
object KokoroAndroidRuntimeContract {
    const val SHERPA_ONNX_VERSION = "1.13.4"
    const val ONNX_RUNTIME_VERSION = "1.27.0"
    const val SHERPA_ANDROID_AAR_NAME = "sherpa-onnx-1.13.4.aar"
    const val SHERPA_ANDROID_AAR_SHA256 = "03f9c4df965f21c71269365a7951a7f23b5696fddd093fa318c80d65550ab780"
    const val PROVIDER = "cpu"

    // Must match androidApp defaultConfig.ndk.abiFilters.
    val supportedAbis = setOf("arm64-v8a", "armeabi-v7a", "x86_64")
}
