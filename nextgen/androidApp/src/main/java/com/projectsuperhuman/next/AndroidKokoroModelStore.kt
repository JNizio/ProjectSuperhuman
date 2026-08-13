package com.projectsuperhuman.next

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Properties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

data class KokoroRequiredAsset(val relativePath: String, val minBytes: Long)

data class KokoroDistributionSpec(
    val logicalModelId: String,
    val runtimeVersion: String,
    val archiveName: String,
    val archiveUrl: String,
    val checksumManifestUrl: String,
    val archiveRoot: String,
    val requiredFiles: List<KokoroRequiredAsset>,
    val minimumEspeakFiles: Int
)

/** Fixed, reviewed production source. UI/model output never supplies these URLs or paths. */
object KokoroAndroidDistribution {
    const val logicalModelId = "onnx-community/Kokoro-82M-v1.0-ONNX"
    const val runtimeVersion = "sherpa-kokoro-multi-lang-v1_0"
    const val archiveName = "kokoro-multi-lang-v1_0.tar.bz2"
    const val archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$archiveName"
    const val checksumManifestUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/checksum.txt"
    const val archiveRoot = "kokoro-multi-lang-v1_0"

    val requiredFiles = listOf(
        KokoroRequiredAsset("model.onnx", 300_000_000L),
        KokoroRequiredAsset("voices.bin", 20_000_000L),
        KokoroRequiredAsset("tokens.txt", 100L),
        KokoroRequiredAsset("lexicon-us-en.txt", 1_000_000L)
    )

    val spec = KokoroDistributionSpec(
        logicalModelId = logicalModelId,
        runtimeVersion = runtimeVersion,
        archiveName = archiveName,
        archiveUrl = archiveUrl,
        checksumManifestUrl = checksumManifestUrl,
        archiveRoot = archiveRoot,
        requiredFiles = requiredFiles,
        minimumEspeakFiles = 20
    )
}

class KokoroInstallException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal interface KokoroArchiveClient {
    suspend fun expectedSha256(distribution: KokoroDistributionSpec): String
    suspend fun download(
        distribution: KokoroDistributionSpec,
        destination: File,
        onProgress: suspend (TrudyVoiceInstallProgress) -> Unit
    )
}

internal class HttpsKokoroArchiveClient : KokoroArchiveClient {
    override suspend fun expectedSha256(distribution: KokoroDistributionSpec): String {
        val connection = openHttps(distribution.checksumManifestUrl)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw KokoroInstallException("Kokoro checksum manifest is unavailable")
            ensureHttps(connection.url)
            val line = connection.inputStream.bufferedReader().useLines { lines ->
                lines.firstOrNull { distribution.archiveName in it }
            }
            val hash = line?.let { Regex("(?i)\\b[0-9a-f]{64}\\b").find(it)?.value }
            return hash ?: throw KokoroInstallException("Publisher checksum for Kokoro archive was not found")
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun download(
        distribution: KokoroDistributionSpec,
        destination: File,
        onProgress: suspend (TrudyVoiceInstallProgress) -> Unit
    ) {
        destination.parentFile?.mkdirs()
        var existing = destination.takeIf { it.isFile }?.length() ?: 0L
        var connection = openHttps(distribution.archiveUrl)
        if (existing > 0L) connection.setRequestProperty("Range", "bytes=$existing-")
        var code = connection.responseCode
        var append = existing > 0L && code == HttpURLConnection.HTTP_PARTIAL
        if (existing > 0L && !append) {
            connection.disconnect()
            destination.delete()
            existing = 0L
            connection = openHttps(distribution.archiveUrl)
            code = connection.responseCode
        }
        try {
            if (code !in 200..299) throw KokoroInstallException("Kokoro download failed with HTTP $code")
            ensureHttps(connection.url)
            val responseBytes = connection.contentLengthLong.takeIf { it >= 0L }
            val total = when {
                append && responseBytes != null -> existing + responseBytes
                responseBytes != null -> responseBytes
                else -> null
            }
            var downloaded = existing
            BufferedInputStream(connection.inputStream, BUFFER_BYTES).use { input ->
                BufferedOutputStream(FileOutputStream(destination, append), BUFFER_BYTES).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(TrudyVoiceInstallProgress(downloaded, total))
                    }
                }
            }
            if (destination.length() <= 0L) throw KokoroInstallException("Kokoro download produced an empty archive")
        } finally {
            connection.disconnect()
        }
    }

    private fun openHttps(value: String): HttpURLConnection {
        val url = URL(value)
        ensureHttps(url)
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ProjectSuperhuman-KokoroInstaller/1")
        }
    }

    private fun ensureHttps(url: URL) {
        if (!url.protocol.equals("https", ignoreCase = true)) throw KokoroInstallException("Kokoro assets require HTTPS")
    }

    private companion object { const val BUFFER_BYTES = 64 * 1024 }
}

/**
 * App-private, restart-safe model storage. Production construction always uses the fixed
 * [KokoroAndroidDistribution] and HTTPS client; the internal constructor exists for module tests.
 */
class AndroidKokoroModelStore internal constructor(
    private val root: File,
    private val distribution: KokoroDistributionSpec,
    private val archiveClient: KokoroArchiveClient
) : KokoroModelStore, TrudyVoiceModelManager {
    constructor(context: Context) : this(
        root = File(context.applicationContext.noBackupFilesDir, "trudy/kokoro"),
        distribution = KokoroAndroidDistribution.spec,
        archiveClient = HttpsKokoroArchiveClient()
    )

    private val activeDir = File(root, "active")
    private val stagingDir = File(root, ".staging")
    private val downloadsDir = File(root, ".downloads")
    private val partialArchive = File(downloadsDir, distribution.archiveName + ".part")
    private val installMutex = Mutex()
    @Volatile private var installing = false
    @Volatile private var progress: TrudyVoiceInstallProgress? = null
    @Volatile private var lastError: String? = null
    @Volatile private var validatedFingerprint: String? = null

    override suspend fun isInstalled(modelId: String): Boolean = withContext(Dispatchers.IO) {
        modelId == distribution.logicalModelId && validateInstalled(deep = false)
    }

    override suspend fun resolve(modelId: String): KokoroModelFiles = withContext(Dispatchers.IO) {
        require(modelId == distribution.logicalModelId) { "Unsupported Kokoro model ID" }
        if (!validateInstalled(deep = true)) throw KokoroInstallException("Kokoro installation failed integrity validation")
        KokoroModelFiles(
            modelPath = File(activeDir, "model.onnx").absolutePath,
            voicesPath = File(activeDir, "voices.bin").absolutePath,
            tokenizerPath = File(activeDir, "tokens.txt").absolutePath,
            phonemizerDataDir = File(activeDir, "espeak-ng-data").absolutePath,
            lexiconPath = File(activeDir, "lexicon-us-en.txt").absolutePath,
            modelVersion = distribution.runtimeVersion,
            modelBytesOnDisk = activeDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        )
    }

    override suspend fun status(): TrudyVoiceStatus = withContext(Dispatchers.IO) {
        when {
            installing -> TrudyVoiceStatus(
                TrudyVoiceRuntimeState.INSTALLING,
                distribution.logicalModelId,
                progress = progress
            )
            validateInstalled(deep = false) -> TrudyVoiceStatus(
                TrudyVoiceRuntimeState.READY,
                distribution.logicalModelId,
                installedVersion = distribution.runtimeVersion
            )
            lastError != null -> TrudyVoiceStatus(
                TrudyVoiceRuntimeState.ERROR,
                distribution.logicalModelId,
                message = lastError
            )
            else -> TrudyVoiceStatus(TrudyVoiceRuntimeState.NOT_INSTALLED, distribution.logicalModelId)
        }
    }

    override suspend fun availableVoices(): List<TrudyVoiceOption> {
        val installed = isInstalled(distribution.logicalModelId)
        return KokoroVoiceCatalog.voices.map {
            TrudyVoiceOption(it.id, it.displayName, it.languageTag, installed)
        }
    }

    override suspend fun cleanupIncomplete() = withContext(Dispatchers.IO) {
        stagingDir.deleteRecursively()
        if (!installing) partialArchive.takeIf { it.exists() && it.length() == 0L }?.delete()
    }

    override suspend fun install(
        onProgress: suspend (TrudyVoiceInstallProgress) -> Unit
    ): TrudyVoiceStatus = installMutex.withLock {
        if (validateInstalled(deep = true)) return@withLock status()
        installing = true
        lastError = null
        try {
            withContext(Dispatchers.IO) {
                root.mkdirs()
                downloadsDir.mkdirs()
                stagingDir.deleteRecursively()
                stagingDir.mkdirs()

                val expectedArchiveSha = archiveClient.expectedSha256(distribution)
                archiveClient.download(distribution, partialArchive) { update ->
                    progress = update
                    onProgress(update)
                }
                val actualArchiveSha = sha256(partialArchive)
                if (!actualArchiveSha.equals(expectedArchiveSha, ignoreCase = true)) {
                    partialArchive.delete()
                    throw KokoroInstallException("Kokoro archive checksum validation failed")
                }

                extractArchive(partialArchive, stagingDir)
                val candidate = File(stagingDir, distribution.archiveRoot)
                validateCandidate(candidate)
                writeInstallManifest(candidate)
                promote(candidate)
                partialArchive.delete()
                stagingDir.deleteRecursively()
                validatedFingerprint = null
                if (!validateInstalled(deep = true)) {
                    activeDir.deleteRecursively()
                    throw KokoroInstallException("Activated Kokoro files failed integrity validation")
                }
            }
        } catch (cancelled: CancellationException) {
            stagingDir.deleteRecursively()
            throw cancelled
        } catch (failure: Throwable) {
            stagingDir.deleteRecursively()
            lastError = failure.message ?: "Kokoro installation failed"
        } finally {
            installing = false
            progress = null
        }
        status()
    }

    private fun extractArchive(archive: File, destination: File) {
        BZip2CompressorInputStream(BufferedInputStream(FileInputStream(archive), BUFFER_BYTES)).use { bzip ->
            TarArchiveInputStream(bzip).use { tar ->
                while (true) {
                    val entry = tar.nextEntry ?: break
                    if (entry.isSymbolicLink || entry.isLink) throw KokoroInstallException("Kokoro archive contains unsupported links")
                    val canonicalDestination = destination.canonicalFile
                    val canonicalTarget = File(destination, entry.name).canonicalFile
                    if (canonicalTarget != canonicalDestination && !canonicalTarget.path.startsWith(canonicalDestination.path + File.separator)) {
                        throw KokoroInstallException("Kokoro archive attempted path traversal")
                    }
                    if (entry.isDirectory) {
                        canonicalTarget.mkdirs()
                    } else {
                        canonicalTarget.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(canonicalTarget), BUFFER_BYTES).use { output ->
                            tar.copyTo(output, BUFFER_BYTES)
                        }
                    }
                }
            }
        }
    }

    private fun validateCandidate(candidate: File) {
        if (!candidate.isDirectory) throw KokoroInstallException("Kokoro archive root is missing")
        distribution.requiredFiles.forEach { required ->
            val file = File(candidate, required.relativePath)
            if (!file.isFile || file.length() < required.minBytes) {
                throw KokoroInstallException("Kokoro asset ${required.relativePath} is missing or truncated")
            }
        }
        val espeak = File(candidate, "espeak-ng-data")
        if (!espeak.isDirectory || espeak.walkTopDown().count { it.isFile } < distribution.minimumEspeakFiles) {
            throw KokoroInstallException("Kokoro eSpeak-ng data is incomplete")
        }
    }

    private fun writeInstallManifest(candidate: File) {
        val properties = Properties().apply {
            setProperty("version", distribution.runtimeVersion)
            setProperty("modelId", distribution.logicalModelId)
            distribution.requiredFiles.forEach { required ->
                val file = File(candidate, required.relativePath)
                setProperty("${required.relativePath}.bytes", file.length().toString())
                setProperty("${required.relativePath}.sha256", sha256(file))
            }
        }
        File(candidate, MANIFEST).outputStream().buffered().use { properties.store(it, "Project Superhuman Kokoro install metadata") }
    }

    private fun promote(candidate: File) {
        val backup = File(root, ".previous")
        backup.deleteRecursively()
        if (activeDir.exists() && !activeDir.renameTo(backup)) {
            throw KokoroInstallException("Could not stage the previous Kokoro installation")
        }
        if (!candidate.renameTo(activeDir)) {
            activeDir.deleteRecursively()
            if (backup.exists()) backup.renameTo(activeDir)
            throw KokoroInstallException("Could not activate the Kokoro installation")
        }
        backup.deleteRecursively()
    }

    private fun validateInstalled(deep: Boolean): Boolean {
        if (!activeDir.isDirectory) return false
        val manifestFile = File(activeDir, MANIFEST)
        if (!manifestFile.isFile) return false
        val properties = runCatching {
            Properties().also { props ->
                manifestFile.inputStream().buffered().use { input -> props.load(input) }
            }
        }.getOrNull() ?: return false
        if (properties.getProperty("version") != distribution.runtimeVersion) return false
        if (properties.getProperty("modelId") != distribution.logicalModelId) return false
        if (distribution.requiredFiles.any { required ->
                val file = File(activeDir, required.relativePath)
                !file.isFile || file.length() < required.minBytes ||
                    properties.getProperty("${required.relativePath}.bytes")?.toLongOrNull() != file.length()
            }) return false
        val espeak = File(activeDir, "espeak-ng-data")
        if (!espeak.isDirectory || espeak.walkTopDown().count { it.isFile } < distribution.minimumEspeakFiles) return false
        if (!deep) return true

        val fingerprint = distribution.requiredFiles.joinToString("|") {
            val file = File(activeDir, it.relativePath)
            "${file.length()}:${file.lastModified()}"
        }
        if (validatedFingerprint == fingerprint) return true
        val hashesMatch = distribution.requiredFiles.all { required ->
            val expected = properties.getProperty("${required.relativePath}.sha256") ?: return@all false
            sha256(File(activeDir, required.relativePath)).equals(expected, ignoreCase = true)
        }
        if (hashesMatch) validatedFingerprint = fingerprint
        return hashesMatch
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(FileInputStream(file), BUFFER_BYTES).use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MANIFEST = "trudy-kokoro.properties"
        const val BUFFER_BYTES = 64 * 1024
    }
}
