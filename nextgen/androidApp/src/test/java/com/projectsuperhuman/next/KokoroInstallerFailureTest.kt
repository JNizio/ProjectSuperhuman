package com.projectsuperhuman.next

import java.io.File
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class KokoroInstallerFailureTest {
    @Test
    fun checksumMismatchNeverActivatesDownloadedBytes() = runTest {
        val root = tempDir("checksum")
        val spec = tinySpec()
        val store = AndroidKokoroModelStore(
            root,
            spec,
            object : KokoroArchiveClient {
                override suspend fun expectedSha256(distribution: KokoroDistributionSpec) = "0".repeat(64)
                override suspend fun download(
                    distribution: KokoroDistributionSpec,
                    destination: File,
                    onProgress: suspend (TrudyVoiceInstallProgress) -> Unit
                ) {
                    destination.parentFile!!.mkdirs()
                    destination.writeText("definitely-not-the-publisher-archive")
                    onProgress(TrudyVoiceInstallProgress(destination.length(), destination.length()))
                }
            }
        )

        val status = store.install()

        assertEquals(TrudyVoiceRuntimeState.ERROR, status.state)
        assertFalse(store.isInstalled(spec.logicalModelId))
        assertFalse(File(root, "active").exists())
        assertFalse(File(root, ".downloads/${spec.archiveName}.part").exists())
        root.deleteRecursively()
    }

    @Test
    fun missingModelKeepsRuntimeTextOnlyWithoutInitializingInference() = runTest {
        var initialized = false
        val runtime = TrudyVoiceRuntimeFactory.create(
            config = TrudyVoiceConfig(mode = TrudyVoiceMode.KOKORO_LOCAL, modelId = "missing/model"),
            modelStore = object : KokoroModelStore {
                override suspend fun isInstalled(modelId: String) = false
                override suspend fun resolve(modelId: String) = error("must not resolve")
            },
            kokoroBackend = object : KokoroInferenceBackend {
                override val backendId = "must-stay-lazy"
                override suspend fun initialize(files: KokoroModelFiles) { initialized = true }
                override suspend fun synthesize(request: KokoroInferenceRequest) = error("must not synthesize")
                override suspend fun close() = Unit
            },
            audioSink = object : TrudyAudioSink {
                override suspend fun play(audio: TrudyPcmAudio) = Unit
                override fun stop() = Unit
            }
        )

        assertFalse(runtime.diagnostics.available)
        assertEquals(TrudyVoiceMode.OFF, runtime.diagnostics.activeMode)
        assertEquals(null, runtime.service)
        assertFalse(initialized)
    }

    private fun tinySpec() = KokoroDistributionSpec(
        logicalModelId = "test/kokoro",
        runtimeVersion = "test-v1",
        archiveName = "test.tar.bz2",
        archiveUrl = "https://example.invalid/test.tar.bz2",
        checksumManifestUrl = "https://example.invalid/checksum.txt",
        archiveRoot = "test-root",
        requiredFiles = listOf(
            KokoroRequiredAsset("model.onnx", 1),
            KokoroRequiredAsset("voices.bin", 1),
            KokoroRequiredAsset("tokens.txt", 1),
            KokoroRequiredAsset("lexicon-us-en.txt", 1)
        ),
        minimumEspeakFiles = 1
    )

    private fun tempDir(name: String): File =
        File(System.getProperty("java.io.tmpdir"), "trudy-kokoro-$name-${System.nanoTime()}").apply { mkdirs() }
}
