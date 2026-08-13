package com.projectsuperhuman.next

import android.content.Context

/**
 * Presentation bridge for Trudy's local voice. The production Android path prefers the small
 * KittenTTS Nano INT8 package; Kokoro remains available in-tree as a fallback.
 */
object TrudyVoiceRuntimeSourceFactory {
    fun createAndroid(context: Context): TrudyVoiceRuntimeSource {
        val store = AndroidKittenModelStore(context.applicationContext)
        return create(
            modelStore = store,
            kokoroBackend = SherpaKittenInferenceBackend(threads = 4),
            modelManager = store,
            normalizeForKitten = true
        )
    }

    fun create(
        modelStore: KokoroModelStore? = null,
        kokoroBackend: KokoroInferenceBackend? = null,
        modelManager: TrudyVoiceModelManager? = modelStore as? TrudyVoiceModelManager,
        normalizeForKitten: Boolean = false
    ): TrudyVoiceRuntimeSource = object : TrudyVoiceRuntimeSource {
        private fun runtimeConfig(config: TrudyVoiceConfig): TrudyVoiceConfig {
            if (!normalizeForKitten || config.mode == TrudyVoiceMode.OFF) return config
            return config.copy(
                modelId = KittenAndroidDistribution.logicalModelId,
                voiceId = KittenVoiceCatalog.resolve(config.voiceId).id
            )
        }

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
                    reason = "Local voice runtime is unavailable in this build."
                )
            }

            val activeConfig = runtimeConfig(config)
            val installed = runCatching { modelStore.isInstalled(activeConfig.modelId) }.getOrDefault(false)
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
                approximateDownloadBytes = if (!installed && normalizeForKitten) 30_000_000L else null,
                availableVoices = voices,
                canInstall = modelManager != null && !installed,
                canRemove = false,
                reason = status?.message
            )
        }

        override suspend fun create(config: TrudyVoiceConfig): TrudyVoiceRuntime =
            TrudyVoiceRuntimeFactory.create(
                config = runtimeConfig(config),
                modelStore = modelStore,
                kokoroBackend = kokoroBackend,
                modelManager = modelManager,
                textFrontend = if (normalizeForKitten) SherpaEspeakKittenTextFrontend() else SherpaEspeakKokoroTextFrontend()
            )

        override suspend fun install(
            config: TrudyVoiceConfig,
            onProgress: (TrudyVoiceInstallProgress) -> Unit
        ): TrudyVoiceModelInfo {
            val manager = modelManager
                ?: throw UnsupportedOperationException("Voice model installation is unavailable")
            val activeConfig = runtimeConfig(config)
            val result = manager.install { progress -> onProgress(progress) }
            val installed = result.state == TrudyVoiceRuntimeState.READY &&
                runCatching { modelStore?.isInstalled(activeConfig.modelId) == true }.getOrDefault(false)
            val voices = runCatching { manager.availableVoices() }.getOrDefault(emptyList())
            if (!installed) {
                throw IllegalStateException(result.message ?: "Local voice model installation did not complete")
            }
            return TrudyVoiceModelInfo(
                installation = TrudyVoiceModelInstallation.INSTALLED,
                modelVersion = result.installedVersion,
                approximateDownloadBytes = null,
                availableVoices = voices,
                canInstall = false,
                canRemove = false
            )
        }

        override suspend fun remove(config: TrudyVoiceConfig): TrudyVoiceModelInfo =
            throw UnsupportedOperationException("Voice model removal is not supported yet")
    }
}
