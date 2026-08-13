package com.projectsuperhuman.next

import android.content.Context

/**
 * Bridges the Kokoro runtime/model manager to presentation state. Heavy inference remains lazy:
 * constructing this source creates no native session and performs no network request.
 */
object TrudyVoiceRuntimeSourceFactory {
    fun createAndroid(context: Context): TrudyVoiceRuntimeSource {
        val store = AndroidKokoroModelStore(context.applicationContext)
        return create(
            modelStore = store,
            kokoroBackend = SherpaKokoroInferenceBackend(threads = 2),
            modelManager = store
        )
    }

    fun create(
        modelStore: KokoroModelStore? = null,
        kokoroBackend: KokoroInferenceBackend? = null,
        modelManager: TrudyVoiceModelManager? = modelStore as? TrudyVoiceModelManager
    ): TrudyVoiceRuntimeSource = object : TrudyVoiceRuntimeSource {
        override suspend fun inspect(config: TrudyVoiceConfig): TrudyVoiceModelInfo {
            if (config.mode == TrudyVoiceMode.OFF) {
                return TrudyVoiceModelInfo(
                    installation = TrudyVoiceModelInstallation.UNAVAILABLE,
                    reason = "Voice is disabled."
                )
            }
            if (modelStore == null || kokoroBackend == null) {
                return TrudyVoiceModelInfo(
                    installation = TrudyVoiceModelInstallation.UNAVAILABLE,
                    reason = "Local Kokoro runtime is unavailable in this build."
                )
            }

            val installed = runCatching { modelStore.isInstalled(config.modelId) }.getOrDefault(false)
            val status = modelManager?.let { runCatching { it.status() }.getOrNull() }
            val voices = modelManager?.let { manager ->
                runCatching { manager.availableVoices() }.getOrDefault(emptyList())
            }.orEmpty()

            return TrudyVoiceModelInfo(
                installation = if (installed) {
                    TrudyVoiceModelInstallation.INSTALLED
                } else {
                    TrudyVoiceModelInstallation.NOT_INSTALLED
                },
                modelVersion = status?.installedVersion,
                availableVoices = voices,
                canInstall = modelManager != null && !installed,
                canRemove = false,
                reason = status?.message
            )
        }

        override suspend fun create(config: TrudyVoiceConfig): TrudyVoiceRuntime =
            TrudyVoiceRuntimeFactory.create(
                config = config,
                modelStore = modelStore,
                kokoroBackend = kokoroBackend,
                modelManager = modelManager
            )

        override suspend fun install(
            config: TrudyVoiceConfig,
            onProgress: (TrudyVoiceInstallProgress) -> Unit
        ): TrudyVoiceModelInfo {
            val manager = modelManager
                ?: throw UnsupportedOperationException("Voice model installation is unavailable")
            val result = manager.install { progress -> onProgress(progress) }
            val installed = result.state == TrudyVoiceRuntimeState.READY &&
                runCatching { modelStore?.isInstalled(config.modelId) == true }.getOrDefault(false)
            val voices = runCatching { manager.availableVoices() }.getOrDefault(emptyList())
            if (!installed) {
                throw IllegalStateException(result.message ?: "Kokoro model installation did not complete")
            }
            return TrudyVoiceModelInfo(
                installation = TrudyVoiceModelInstallation.INSTALLED,
                modelVersion = result.installedVersion,
                availableVoices = voices,
                canInstall = false,
                canRemove = false
            )
        }

        override suspend fun remove(config: TrudyVoiceConfig): TrudyVoiceModelInfo =
            throw UnsupportedOperationException("Voice model removal is not supported yet")
    }
}
