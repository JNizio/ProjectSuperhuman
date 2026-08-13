package com.projectsuperhuman.next

import java.io.File
import java.security.MessageDigest
import java.util.Properties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KokoroRuntimeHardeningTest {
    @Test
    fun missingAndPartialInstallAreNeverReady() = runTest {
        val root = tempDir("missing")
        val store = testStore(root)
        assertFalse(store.isInstalled(TEST_MODEL_ID))
        assertEquals(TrudyVoiceRuntimeState.NOT_INSTALLED, store.status().state)

        val partial = File(root, ".downloads/${TEST_ARCHIVE}.part")
        partial.parentFile!!.mkdirs()
        partial.writeBytes(byteArrayOf(1, 2, 3))
        assertFalse(store.isInstalled(TEST_MODEL_ID))
        assertEquals(TrudyVoiceRuntimeState.NOT_INSTALLED, store.status().state)
        root.deleteRecursively()
    }

    @Test
    fun validInstalledModelResolvesOnlyAfterManifestAndHashesMatch() = runTest {
        val root = tempDir("valid")
        writeInstalledFixture(root)
        val store = testStore(root)
        assertTrue(store.isInstalled(TEST_MODEL_ID))
        val files = store.resolve(TEST_MODEL_ID)
        assertEquals(TEST_VERSION, files.modelVersion)
        assertTrue(File(requireNotNull(files.voicesPath)).isFile)
        assertTrue(store.availableVoices().first { it.id == "af_heart" }.installed)
        root.deleteRecursively()
    }

    @Test
    fun versionMismatchIsRejected() = runTest {
        val root = tempDir("version")
        writeInstalledFixture(root, version = "old-version")
        val store = testStore(root)
        assertFalse(store.isInstalled(TEST_MODEL_ID))
        assertFailsWith<KokoroInstallException> { store.resolve(TEST_MODEL_ID) }
        root.deleteRecursively()
    }

    @Test
    fun voiceAssetIntegrityMismatchIsRejectedOnResolve() = runTest {
        val root = tempDir("voice")
        writeInstalledFixture(root)
        val voices = File(root, "active/voices.bin")
        voices.writeText("VOICE-B") // same byte length as fixture, so the hash check is what rejects it
        val store = testStore(root)
        assertTrue(store.isInstalled(TEST_MODEL_ID))
        assertFailsWith<KokoroInstallException> { store.resolve(TEST_MODEL_ID) }
        root.deleteRecursively()
    }

    @Test
    fun truncatedRequiredAssetIsRejectedEvenBeforeDeepHashing() = runTest {
        val root = tempDir("truncated")
        writeInstalledFixture(root)
        File(root, "active/model.onnx").writeBytes(byteArrayOf())
        val store = testStore(root)
        assertFalse(store.isInstalled(TEST_MODEL_ID))
        root.deleteRecursively()
    }

    @Test
    fun installFailureReturnsProviderNeutralErrorState() = runTest {
        val root = tempDir("install-failure")
        val client = object : KokoroArchiveClient {
            override suspend fun expectedSha256(distribution: KokoroDistributionSpec): String =
                throw KokoroInstallException("network unavailable")
            override suspend fun download(
                distribution: KokoroDistributionSpec,
                destination: File,
                onProgress: suspend (TrudyVoiceInstallProgress) -> Unit
            ) = Unit
        }
        val store = AndroidKokoroModelStore(root, TEST_SPEC, client)
        val status = store.install()
        assertEquals(TrudyVoiceRuntimeState.ERROR, status.state)
        assertFalse(store.isInstalled(TEST_MODEL_ID))
        root.deleteRecursively()
    }

    @Test
    fun lazyInitializationHappensExactlyOnceAcrossConcurrentSpeech() = runTest {
        val backend = CountingBackend()
        val engine = KokoroTrudySpeechEngine(localConfig(), InstalledStore, backend)
        assertEquals(0, backend.initializeCalls)
        coroutineScope {
            (1..6).map { n -> async { engine.synthesize(TrudySpeechRequest("utterance $n", "af_heart")) } }.awaitAll()
        }
        assertEquals(1, backend.initializeCalls)
        assertEquals(6, backend.synthesisCalls)
    }

    @Test
    fun chunkingIsDeterministicBoundedAndDoesNotSplitWords() {
        val text = (1..50).joinToString(" ") { "word$it" } + ". Second sentence should stay readable."
        val first = KokoroTextChunker.chunk(text, maxChars = 80)
        val second = KokoroTextChunker.chunk(text, maxChars = 80)
        assertEquals(first, second)
        assertTrue(first.size > 1)
        assertTrue(first.all { it.length <= 80 })
        assertEquals(text.replace(Regex("\\s+"), " ").trim(), first.joinToString(" "))
        assertTrue(first.none { it.startsWith("ord") })
    }

    @Test
    fun frontendPreparationIsDeterministicAndLeavesG2pToSherpa() {
        val frontend = SherpaEspeakKokoroTextFrontend(maxChunkChars = 100)
        val raw = "## Sleep **looks** better. [Details](https://invalid.example)"
        assertEquals("sherpa-espeak-ng", frontend.phonemizerId)
        assertEquals(frontend.prepare(raw), frontend.prepare(raw))
        assertEquals("Sleep looks better. Details", frontend.prepare(raw))
    }

    @Test
    fun audioSanitizationAndPcm16ConversionHandleNonFiniteAndClipping() {
        val safe = KokoroAudioSanitizer.sanitize(
            floatArrayOf(Float.NaN, Float.NEGATIVE_INFINITY, -2f, -0.5f, 0f, 0.5f, 2f, Float.POSITIVE_INFINITY)
        )
        assertEquals(listOf(0f, -1f, -1f, -0.5f, 0f, 0.5f, 1f, 1f), safe.toList())
        val pcm = TrudyPcm16.convert(safe)
        assertEquals(0, pcm[0].toInt())
        assertEquals(Short.MIN_VALUE, pcm[1])
        assertEquals(Short.MAX_VALUE, pcm.last())
    }

    @Test
    fun emptySynthesisOutputIsRejected() = runTest {
        val backend = object : KokoroInferenceBackend {
            override val backendId = "empty"
            override suspend fun initialize(files: KokoroModelFiles) = Unit
            override suspend fun synthesize(request: KokoroInferenceRequest) = KokoroInferenceOutput(floatArrayOf())
            override suspend fun close() = Unit
        }
        val engine = KokoroTrudySpeechEngine(localConfig(), InstalledStore, backend)
        assertFailsWith<IllegalArgumentException> {
            engine.synthesize(TrudySpeechRequest("Hello", "af_heart"))
        }
    }

    @Test
    fun inferenceExceptionDoesNotBecomeFabricatedAudio() = runTest {
        val backend = object : KokoroInferenceBackend {
            override val backendId = "throwing"
            override suspend fun initialize(files: KokoroModelFiles) = Unit
            override suspend fun synthesize(request: KokoroInferenceRequest): KokoroInferenceOutput =
                throw KokoroInferenceException("bad native output")
            override suspend fun close() = Unit
        }
        val engine = KokoroTrudySpeechEngine(localConfig(), InstalledStore, backend)
        assertFailsWith<KokoroInferenceException> {
            engine.synthesize(TrudySpeechRequest("Hello", "af_heart"))
        }
    }

    @Test
    fun stopCancelsQueuedSynthesisAndPlaybackAfterCurrentChunk() = runTest {
        val engine = BlockingStreamingEngine()
        val sink = CountingSink()
        val service = TrudyVoiceService(localConfig(), engine, sink)
        var cancelled = false
        val job = launch {
            try {
                service.speak("A long answer")
            } catch (_: CancellationException) {
                cancelled = true
            }
        }
        engine.firstChunkDelivered.await()
        service.stop()
        job.join()
        assertTrue(cancelled)
        assertTrue(engine.cancelled)
        assertEquals(1, sink.playCalls)
        assertEquals(TrudyVoiceRuntimeState.READY, service.runtimeState())
    }

    @Test
    fun unsupportedVoiceNeverFallsThroughToAnotherSpeaker() {
        assertEquals(3, KokoroVoiceCatalog.requireVoice("af_heart").speakerId)
        assertFailsWith<IllegalArgumentException> { KokoroVoiceCatalog.requireVoice("made_up_voice") }
    }

    private class CountingBackend : KokoroInferenceBackend {
        override val backendId = "counting"
        var initializeCalls = 0
        var synthesisCalls = 0
        override suspend fun initialize(files: KokoroModelFiles) { initializeCalls++ }
        override suspend fun synthesize(request: KokoroInferenceRequest): KokoroInferenceOutput {
            synthesisCalls++
            return KokoroInferenceOutput(floatArrayOf(0f, 0.1f, -0.1f))
        }
        override suspend fun close() = Unit
    }

    private class BlockingStreamingEngine : TrudySpeechEngine {
        override val engineId = "blocking"
        val firstChunkDelivered = CompletableDeferred<Unit>()
        private val continueSignal = CompletableDeferred<Unit>()
        @Volatile var cancelled = false
        override suspend fun isAvailable() = true
        override suspend fun synthesize(request: TrudySpeechRequest): TrudySpeechResult = error("streaming only")
        override suspend fun synthesizeStreaming(
            request: TrudySpeechRequest,
            onChunk: suspend (TrudySpeechResult) -> Unit
        ): TrudySpeechDiagnostics {
            val diagnostics = TrudySpeechDiagnostics(engineId, "model", request.voiceId, 24_000)
            onChunk(TrudySpeechResult(TrudyPcmAudio(floatArrayOf(0f), 24_000), diagnostics))
            firstChunkDelivered.complete(Unit)
            continueSignal.await()
            if (cancelled) throw CancellationException("stopped")
            onChunk(TrudySpeechResult(TrudyPcmAudio(floatArrayOf(0f), 24_000), diagnostics))
            return diagnostics
        }
        override fun cancelCurrent() {
            cancelled = true
            continueSignal.complete(Unit)
        }
    }

    private class CountingSink : TrudyAudioSink {
        var playCalls = 0
        override suspend fun play(audio: TrudyPcmAudio) { playCalls++ }
        override fun stop() = Unit
    }

    private object InstalledStore : KokoroModelStore {
        override suspend fun isInstalled(modelId: String) = true
        override suspend fun resolve(modelId: String) = KokoroModelFiles(
            modelPath = "/private/model.onnx",
            voicesPath = "/private/voices.bin",
            tokenizerPath = "/private/tokens.txt",
            phonemizerDataDir = "/private/espeak-ng-data",
            lexiconPath = "/private/lexicon-us-en.txt",
            modelVersion = TEST_VERSION
        )
    }

    private fun localConfig() = TrudyVoiceConfig(
        mode = TrudyVoiceMode.KOKORO_LOCAL,
        modelId = TEST_MODEL_ID,
        voiceId = "af_heart"
    )

    private fun testStore(root: File) = AndroidKokoroModelStore(
        root,
        TEST_SPEC,
        object : KokoroArchiveClient {
            override suspend fun expectedSha256(distribution: KokoroDistributionSpec) = "0".repeat(64)
            override suspend fun download(
                distribution: KokoroDistributionSpec,
                destination: File,
                onProgress: suspend (TrudyVoiceInstallProgress) -> Unit
            ) = error("not used")
        }
    )

    private fun writeInstalledFixture(root: File, version: String = TEST_VERSION) {
        val active = File(root, "active").apply { mkdirs() }
        val contents = mapOf(
            "model.onnx" to "MODEL-A",
            "voices.bin" to "VOICE-A",
            "tokens.txt" to "TOKEN-A",
            "lexicon-us-en.txt" to "LEXICON"
        )
        contents.forEach { (name, value) -> File(active, name).writeText(value) }
        File(active, "espeak-ng-data").mkdirs()
        File(active, "espeak-ng-data/fixture").writeText("data")
        val properties = Properties().apply {
            setProperty("version", version)
            setProperty("modelId", TEST_MODEL_ID)
            TEST_SPEC.requiredFiles.forEach { required ->
                val file = File(active, required.relativePath)
                setProperty("${required.relativePath}.bytes", file.length().toString())
                setProperty("${required.relativePath}.sha256", sha256(file))
            }
        }
        File(active, "trudy-kokoro.properties").outputStream().use { properties.store(it, "test") }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun tempDir(name: String): File =
        File(System.getProperty("java.io.tmpdir"), "trudy-kokoro-$name-${System.nanoTime()}").apply { mkdirs() }

    companion object {
        private const val TEST_MODEL_ID = "test/kokoro"
        private const val TEST_VERSION = "test-v1"
        private const val TEST_ARCHIVE = "test.tar.bz2"
        private val TEST_SPEC = KokoroDistributionSpec(
            logicalModelId = TEST_MODEL_ID,
            runtimeVersion = TEST_VERSION,
            archiveName = TEST_ARCHIVE,
            archiveUrl = "https://example.invalid/$TEST_ARCHIVE",
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
    }
}
