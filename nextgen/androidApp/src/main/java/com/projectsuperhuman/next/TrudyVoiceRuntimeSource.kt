package com.projectsuperhuman.next

/** Optional installer seam. Instance A can implement this alongside the concrete model store. */
interface TrudyVoiceModelInstaller {
    suspend fun inspect(modelId: String): TrudyVoiceModelInfo
    suspend fun install(
        modelId: String,
        onProgress: (TrudyVoiceInstallProgress) -> Unit
    ): TrudyVoiceModelInfo
    suspend fun remove(modelId: String): TrudyVoiceModelInfo
}

data class TrudyVoiceRuntimeDependencies(
    val modelStore: KokoroModelStore? = null,
    val kokoroBackend: KokoroInferenceBackend? = null,
    val installer: TrudyVoiceModelInstaller? = null
)

/**
 * Bridges the already-existing voice factory to the presentation controller. Heavy inference is
 * still lazy because create() is only called after a user actually requests speech.
 */
object TrudyVoiceRuntimeSourceFactory {
    fun create(
        dependencies: TrudyVoiceRuntimeDependencies = TrudyVoiceRuntimeDependencies()
    ): TrudyVoiceRuntimeSource = object : TrudyVoiceRuntimeSource {
        override suspend fun inspect(config: TrudyVoiceConfig): TrudyVoiceModelInfo {
            dependencies.installer?.let { return it.inspect(config.modelId) }
            val store = dependencies.modelStore
            val backend = dependencies.kokoroBackend
            if (store == null || backend == null) {
                return TrudyVoiceModelInfo(
                    installation = TrudyVoiceModelInstallation.UNAVAILABLE,
                    reason = "Local voice runtime is not available in this build."
                )
            }
            return if (store.isInstalled(config.modelId)) {
                TrudyVoiceModelInfo(
                    installation = TrudyVoiceModelInstallation.INSTALLED,
                    availableVoices = emptyList(),
                    canInstall = false,
                    canRemove = false
                )
            } else {
                TrudyVoiceModelInfo(
                    installation = TrudyVoiceModelInstallation.NOT_INSTALLED,
                    canInstall = false,
                    reason = "Local voice model is not installed and no installer is registered."
                )
            }
        }

        override suspend fun create(config: TrudyVoiceConfig): TrudyVoiceRuntime =
            TrudyVoiceRuntimeFactory.create(
                config = config,
                modelStore = dependencies.modelStore,
                kokoroBackend = dependencies.kokoroBackend
            )

        override suspend fun install(
            config: TrudyVoiceConfig,
            onProgress: (TrudyVoiceInstallProgress) -> Unit
        ): TrudyVoiceModelInfo = dependencies.installer?.install(config.modelId, onProgress)
            ?: throw UnsupportedOperationException("Voice model installer is not registered")

        override suspend fun remove(config: TrudyVoiceConfig): TrudyVoiceModelInfo =
            dependencies.installer?.remove(config.modelId)
                ?: throw UnsupportedOperationException("Voice model removal is not supported")
    }
}
