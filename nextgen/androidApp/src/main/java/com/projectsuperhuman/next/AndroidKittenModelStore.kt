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

/**
 * Small, CPU-oriented local voice package used by Trudy. The model is the official sherpa-onnx
 * export of KittenTTS Nano v0.8 INT8. It is deliberately stored separately from the existing
 * Kokoro install so upgrading does not destroy the known-good fallback.
 */
object KittenAndroidDistribution {
    const val logicalModelId = "KittenML/kitten-tts-nano-0.8-int8"
    const val runtimeVersion = "kitten-nano-en-v0_8-int8"
    const val archiveName = "kitten-nano-en-v0_8-int8.tar.bz2"
    const val archiveRoot = "kitten-nano-en-v0_8-int8"
    const val archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$archiveName"
    const val checksumManifestUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/checksum.txt"
    const val modelFile = "model.int8.onnx"

    private val required = listOf(
        modelFile to 10_000_000L,
        "voices.bin" to 100_000L,
        "tokens.txt" to 100L
    )

    fun validateFiles(root: File) {
        if (!root.isDirectory) throw KittenInstallException("Kitten voice archive root is missing")
        required.forEach { (path, minBytes) ->
            val file = File(root, path)
            if (!file.isFile || file.length() < minBytes) {
                throw KittenInstallException("Kitten voice asset $path is missing or truncated")
            }
        }
        val espeak = File(root, "espeak-ng-data")
        if (!espeak.isDirectory || espeak.walkTopDown().count { it.isFile } < 20) {
            throw KittenInstallException("Kitten eSpeak-ng data is incomplete")
        }
    }

    fun requiredFiles(): List<String> = required.map { it.first }
}

class KittenInstallException(message: String, cause: Throwable? = null) : Exception(message, cause)

class AndroidKittenModelStore(context: Context) : KokoroModelStore, TrudyVoiceModelManager {
    private val root = File(context.applicationContext.noBackupFilesDir, "trudy/kitten")
    private val activeDir = File(root, "active")
    private val stagingDir = File(root, ".staging")
    private val downloadsDir = File(root, ".downloads")
    private val partialArchive = File(downloadsDir, KittenAndroidDistribution.archiveName + ".part")
    private val installMutex = Mutex()

    @Volatile private var installing = false
    @Volatile private var progress: TrudyVoiceInstallProgress? = null
    @Volatile private var lastError: String? = null
    @Volatile private var validatedFingerprint: String? = null

    override suspend fun isInstalled(modelId: String): Boolean = withContext(Dispatchers.IO) {
        modelId == KittenAndroidDistribution.logicalModelId && validateInstalled(deep = false)
    }

    override suspend fun resolve(modelId: String): KokoroModelFiles = withContext(Dispatchers.IO) {
        require(modelId == KittenAndroidDistribution.logicalModelId) { "Unsupported Kitten voice model ID" }
        if (!validateInstalled(deep = true)) throw KittenInstallException("Kitten voice installation failed integrity validation")
        KokoroModelFiles(
            modelPath = File(activeDir, KittenAndroidDistribution.modelFile).absolutePath,
            voicesPath = File(activeDir, "voices.bin").absolutePath,
            tokenizerPath = File(activeDir, "tokens.txt").absolutePath,
            phonemizerDataDir = File(activeDir, "espeak-ng-data").absolutePath,
            lexiconPath = null,
            modelVersion = KittenAndroidDistribution.runtimeVersion,
            modelBytesOnDisk = activeDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        )
    }

    override suspend fun status(): TrudyVoiceStatus = withContext(Dispatchers.IO) {
        when {
            installing -> TrudyVoiceStatus(
                TrudyVoiceRuntimeState.INSTALLING,
                KittenAndroidDistribution.logicalModelId,
                progress = progress
            )
            validateInstalled(deep = false) -> TrudyVoiceStatus(
                TrudyVoiceRuntimeState.READY,
                KittenAndroidDistribution.logicalModelId,
                installedVersion = KittenAndroidDistribution.runtimeVersion
            )
            lastError != null -> TrudyVoiceStatus(
                TrudyVoiceRuntimeState.ERROR,
                KittenAndroidDistribution.logicalModelId,
                message = lastError
            )
            else -> TrudyVoiceStatus(TrudyVoiceRuntimeState.NOT_INSTALLED, KittenAndroidDistribution.logicalModelId)
        }
    }

    override suspend fun availableVoices(): List<TrudyVoiceOption> {
        val installed = isInstalled(KittenAndroidDistribution.logicalModelId)
        return KittenVoiceCatalog.voices.map { voice ->
            TrudyVoiceOption(voice.id, voice.displayName, "en-US", installed)
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
                root.mkdirs(); downloadsDir.mkdirs(); stagingDir.deleteRecursively(); stagingDir.mkdirs()
                val expectedSha = expectedArchiveSha256()
                downloadArchive { update ->
                    val fraction = update.fraction ?: if (update.downloadedBytes != null && update.totalBytes != null) {
                        update.downloadedBytes.toFloat() / update.totalBytes.toFloat()
                    } else null
                    emitProgress(fraction?.coerceIn(0f, 1f)?.times(0.90f), onProgress)
                }

                emitProgress(0.92f, onProgress)
                val actualSha = sha256(partialArchive)
                if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                    partialArchive.delete()
                    throw KittenInstallException("Kitten voice archive checksum validation failed")
                }

                emitProgress(0.95f, onProgress)
                extractArchive(partialArchive, stagingDir)
                val candidate = File(stagingDir, KittenAndroidDistribution.archiveRoot)
                KittenAndroidDistribution.validateFiles(candidate)
                writeManifest(candidate)

                emitProgress(0.99f, onProgress)
                promote(candidate)
                partialArchive.delete(); stagingDir.deleteRecursively(); validatedFingerprint = null
                if (!validateInstalled(deep = true)) {
                    activeDir.deleteRecursively()
                    throw KittenInstallException("Activated Kitten voice files failed integrity validation")
                }
                emitProgress(1.0f, onProgress)
            }
        } catch (cancelled: CancellationException) {
            stagingDir.deleteRecursively()
            throw cancelled
        } catch (failure: Throwable) {
            stagingDir.deleteRecursively()
            lastError = failure.message ?: "Kitten voice installation failed"
        } finally {
            installing = false
            progress = null
        }
        status()
    }

    private suspend fun emitProgress(fraction: Float?, callback: suspend (TrudyVoiceInstallProgress) -> Unit) {
        val update = TrudyVoiceInstallProgress(fraction = fraction?.coerceIn(0f, 1f))
        progress = update
        callback(update)
    }

    private fun expectedArchiveSha256(): String {
        val connection = openHttps(KittenAndroidDistribution.checksumManifestUrl)
        try {
            if (connection.responseCode !in 200..299) throw KittenInstallException("Kitten checksum manifest is unavailable")
            val line = connection.inputStream.bufferedReader().useLines { lines ->
                lines.firstOrNull { KittenAndroidDistribution.archiveName in it }
            }
            return line?.let { Regex("(?i)\\b[0-9a-f]{64}\\b").find(it)?.value }
                ?: throw KittenInstallException("Publisher checksum for Kitten voice archive was not found")
        } finally { connection.disconnect() }
    }

    private suspend fun downloadArchive(onProgress: suspend (TrudyVoiceInstallProgress) -> Unit) {
        var existing = partialArchive.takeIf { it.isFile }?.length() ?: 0L
        var connection = openHttps(KittenAndroidDistribution.archiveUrl)
        if (existing > 0L) connection.setRequestProperty("Range", "bytes=$existing-")
        var code = connection.responseCode
        var append = existing > 0L && code == HttpURLConnection.HTTP_PARTIAL
        if (existing > 0L && !append) {
            connection.disconnect(); partialArchive.delete(); existing = 0L
            connection = openHttps(KittenAndroidDistribution.archiveUrl); code = connection.responseCode
        }
        try {
            if (code !in 200..299) throw KittenInstallException("Kitten voice download failed with HTTP $code")
            val responseBytes = connection.contentLengthLong.takeIf { it >= 0L }
            val total = when {
                append && responseBytes != null -> existing + responseBytes
                responseBytes != null -> responseBytes
                else -> null
            }
            var downloaded = existing
            partialArchive.parentFile?.mkdirs()
            BufferedInputStream(connection.inputStream, BUFFER_BYTES).use { input ->
                BufferedOutputStream(FileOutputStream(partialArchive, append), BUFFER_BYTES).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count); downloaded += count
                        onProgress(TrudyVoiceInstallProgress(downloaded, total))
                    }
                }
            }
            if (partialArchive.length() <= 0L) throw KittenInstallException("Kitten voice download produced an empty archive")
        } finally { connection.disconnect() }
    }

    private fun openHttps(value: String): HttpURLConnection {
        val url = URL(value)
        if (!url.protocol.equals("https", true)) throw KittenInstallException("Kitten voice assets require HTTPS")
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000; readTimeout = 60_000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ProjectSuperhuman-KittenInstaller/1")
        }
    }

    private fun extractArchive(archive: File, destination: File) {
        BZip2CompressorInputStream(BufferedInputStream(FileInputStream(archive), BUFFER_BYTES)).use { bzip ->
            TarArchiveInputStream(bzip).use { tar ->
                while (true) {
                    val entry = tar.nextEntry ?: break
                    if (entry.isSymbolicLink || entry.isLink) throw KittenInstallException("Kitten archive contains unsupported links")
                    val base = destination.canonicalFile
                    val target = File(destination, entry.name).canonicalFile
                    if (target != base && !target.path.startsWith(base.path + File.separator)) {
                        throw KittenInstallException("Kitten archive attempted path traversal")
                    }
                    if (entry.isDirectory) target.mkdirs() else {
                        target.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(target), BUFFER_BYTES).use { tar.copyTo(it, BUFFER_BYTES) }
                    }
                }
            }
        }
    }

    private fun writeManifest(candidate: File) {
        val props = Properties().apply {
            setProperty("version", KittenAndroidDistribution.runtimeVersion)
            setProperty("modelId", KittenAndroidDistribution.logicalModelId)
            KittenAndroidDistribution.requiredFiles().forEach { path ->
                val file = File(candidate, path)
                setProperty("$path.bytes", file.length().toString())
                setProperty("$path.sha256", sha256(file))
            }
        }
        File(candidate, MANIFEST).outputStream().buffered().use { props.store(it, "Project Superhuman Kitten voice metadata") }
    }

    private fun promote(candidate: File) {
        val backup = File(root, ".previous")
        backup.deleteRecursively()
        if (activeDir.exists() && !activeDir.renameTo(backup)) throw KittenInstallException("Could not stage previous Kitten voice install")
        if (!candidate.renameTo(activeDir)) {
            activeDir.deleteRecursively(); if (backup.exists()) backup.renameTo(activeDir)
            throw KittenInstallException("Could not activate Kitten voice install")
        }
        backup.deleteRecursively()
    }

    private fun validateInstalled(deep: Boolean): Boolean {
        if (!activeDir.isDirectory) return false
        val manifest = File(activeDir, MANIFEST)
        if (!manifest.isFile) return false
        val props = runCatching { Properties().also { p -> manifest.inputStream().buffered().use(p::load) } }.getOrNull() ?: return false
        if (props.getProperty("version") != KittenAndroidDistribution.runtimeVersion ||
            props.getProperty("modelId") != KittenAndroidDistribution.logicalModelId) return false
        if (runCatching { KittenAndroidDistribution.validateFiles(activeDir) }.isFailure) return false
        if (KittenAndroidDistribution.requiredFiles().any { path ->
                val file = File(activeDir, path)
                props.getProperty("$path.bytes")?.toLongOrNull() != file.length()
            }) return false
        if (!deep) return true

        val fingerprint = KittenAndroidDistribution.requiredFiles().joinToString("|") { path ->
            File(activeDir, path).let { "${it.length()}:${it.lastModified()}" }
        }
        if (validatedFingerprint == fingerprint) return true
        val hashesMatch = KittenAndroidDistribution.requiredFiles().all { path ->
            val expected = props.getProperty("$path.sha256") ?: return@all false
            sha256(File(activeDir, path)).equals(expected, true)
        }
        if (hashesMatch) validatedFingerprint = fingerprint
        return hashesMatch
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        BufferedInputStream(FileInputStream(file), BUFFER_BYTES).use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MANIFEST = "trudy-kitten.properties"
        const val BUFFER_BYTES = 64 * 1024
    }
}
