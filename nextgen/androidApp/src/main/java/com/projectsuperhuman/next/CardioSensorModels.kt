package com.projectsuperhuman.next

import com.projectsuperhuman.next.core.ObservationTimeBasis
import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

internal const val CARDIO_HR_MIN_BPM = 20
internal const val CARDIO_HR_MAX_BPM = 260
internal const val CARDIO_HR_STALE_AFTER_MS = 10_000L
internal const val CARDIO_HR_TIMELINE_MAX_POINTS = 720

internal enum class CardioSensorProviderType {
    NONE,
    H19C,
    BLE_HEART_RATE,
    HEALTH_CONNECT
}

internal enum class CardioSensorTransport {
    NONE,
    LIVE_BLE,
    IMPORTED_HEALTH_CONNECT
}

internal enum class CardioSensorConnectionState {
    NO_SENSOR,
    SCANNING,
    CONNECTING,
    CONNECTED,
    STALE,
    RECONNECTING,
    DISCONNECTED,
    ERROR
}

internal data class CardioSensorProvenance(
    val providerType: CardioSensorProviderType,
    val sourceName: String,
    val transport: CardioSensorTransport,
    val deviceName: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val sourcePackage: String? = null,
    val anonymousSensorId: String? = null,
    val externalRecordId: String? = null
) {
    fun toMetadata(prefix: String = "sensor"): Map<String, String> = buildMap {
        put("${prefix}ProviderType", providerType.name)
        put("${prefix}SourceName", sourceName)
        put("${prefix}Transport", transport.name)
        deviceName?.takeIf { it.isNotBlank() }?.let { put("${prefix}DeviceName", it) }
        manufacturer?.takeIf { it.isNotBlank() }?.let { put("${prefix}Manufacturer", it) }
        model?.takeIf { it.isNotBlank() }?.let { put("${prefix}Model", it) }
        sourcePackage?.takeIf { it.isNotBlank() }?.let { put("${prefix}SourcePackage", it) }
        anonymousSensorId?.takeIf { it.isNotBlank() }?.let { put("${prefix}Id", it) }
        externalRecordId?.takeIf { it.isNotBlank() }?.let { put("${prefix}ExternalRecordId", it) }
    }
}

internal object CardioSensorIds {
    fun anonymous(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}

internal data class CardioHeartRateSample(
    /** Physiological timeline time. */
    val timestampEpochMs: Long,
    val bpm: Int,
    val source: CardioSensorProvenance,
    /** When Project Superhuman received the packet/record. */
    val receivedAtEpochMs: Long = timestampEpochMs,
    /** When a delayed/imported record entered Project Superhuman. Null for live packets. */
    val importedAtEpochMs: Long? = null,
    val timeBasis: ObservationTimeBasis = ObservationTimeBasis.PHONE_RECEIVE_TIME
) {
    val isPhysiologicallyStorable: Boolean get() = bpm in CARDIO_HR_MIN_BPM..CARDIO_HR_MAX_BPM
}

internal data class CardioSensorState(
    val providerType: CardioSensorProviderType = CardioSensorProviderType.NONE,
    val connection: CardioSensorConnectionState = CardioSensorConnectionState.NO_SENSOR,
    val currentHeartRateBpm: Int? = null,
    val lastSampleEpochMs: Long? = null,
    val provenance: CardioSensorProvenance? = null,
    val message: String = "No cardio sensor selected",
    val reconnectAttempt: Int = 0
) {
    fun sampleAgeMs(nowEpochMs: Long): Long? =
        lastSampleEpochMs?.let { (nowEpochMs - it).coerceAtLeast(0L) }

    fun withFreshness(nowEpochMs: Long): CardioSensorState {
        if (connection != CardioSensorConnectionState.CONNECTED) return this
        val age = sampleAgeMs(nowEpochMs) ?: return this
        return if (age > CARDIO_HR_STALE_AFTER_MS) {
            copy(connection = CardioSensorConnectionState.STALE)
        } else this
    }

    fun freshHeartRate(nowEpochMs: Long): Int? {
        if (connection != CardioSensorConnectionState.CONNECTED) return null
        val age = sampleAgeMs(nowEpochMs) ?: return null
        return currentHeartRateBpm?.takeIf { age <= CARDIO_HR_STALE_AFTER_MS }
    }
}

internal interface CardioSensorProvider {
    val providerType: CardioSensorProviderType
    val state: StateFlow<CardioSensorState>
    val heartRateSamples: SharedFlow<CardioHeartRateSample>

    suspend fun connect()
    suspend fun disconnect()

    fun startSession(sessionId: String, startedAtEpochMs: Long)
    fun stopSession(endedAtEpochMs: Long)
}

internal data class CardioHrZoneDefinition(
    val zone: Int,
    val label: String,
    val minBpmInclusive: Int,
    val maxBpmInclusive: Int
) {
    init {
        require(zone > 0)
        require(minBpmInclusive <= maxBpmInclusive)
    }

    fun contains(bpm: Int): Boolean = bpm in minBpmInclusive..maxBpmInclusive
}

/**
 * A storage/display scheme, not a medical or training prescription. Engineer-owned settings can
 * replace it later without changing the sample processor.
 */
internal data class CardioHrZoneScheme(
    val id: String,
    val zones: List<CardioHrZoneDefinition>
) {
    init {
        require(zones.isNotEmpty())
        require(zones.map { it.zone }.distinct().size == zones.size)
    }

    fun zoneFor(bpm: Int): Int? = zones.firstOrNull { it.contains(bpm) }?.zone

    companion object {
        fun neutralFiveBand(): CardioHrZoneScheme = CardioHrZoneScheme(
            id = "neutral-five-band-v1",
            zones = listOf(
                CardioHrZoneDefinition(1, "Band 1", 20, 99),
                CardioHrZoneDefinition(2, "Band 2", 100, 119),
                CardioHrZoneDefinition(3, "Band 3", 120, 139),
                CardioHrZoneDefinition(4, "Band 4", 140, 159),
                CardioHrZoneDefinition(5, "Band 5", 160, 260)
            )
        )
    }
}

internal data class CardioActiveWindow(
    val startEpochMs: Long,
    val endEpochMs: Long
) {
    init {
        require(endEpochMs >= startEpochMs)
    }

    val durationMs: Long get() = (endEpochMs - startEpochMs).coerceAtLeast(0L)
}

internal data class CardioHeartRateSummary(
    val averageBpm: Int? = null,
    val minBpm: Int? = null,
    val maxBpm: Int? = null,
    val currentBpm: Int? = null,
    val lastSampleEpochMs: Long? = null,
    val sampleCount: Int = 0,
    val heartRateCoveragePct: Double = 0.0,
    val measuredSeconds: Int = 0,
    val unclassifiedSeconds: Int = 0,
    val zoneSeconds: Map<Int, Int> = emptyMap(),
    val currentZone: Int? = null,
    val zoneSchemeId: String? = null,
    val timeline: List<CardioHeartRateSample> = emptyList(),
    val primaryProvenance: CardioSensorProvenance? = null
) {
    fun toMetadata(): Map<String, String> = buildMap {
        averageBpm?.let { put("avgHeartRate", it.toString()) }
        minBpm?.let { put("minHeartRate", it.toString()) }
        maxBpm?.let { put("maxHeartRate", it.toString()) }
        lastSampleEpochMs?.let { put("heartRateLastSampleEpochMs", it.toString()) }
        put("heartRateSampleCount", sampleCount.toString())
        put("heartRateCoveragePct", "%.2f".format(java.util.Locale.US, heartRateCoveragePct))
        put("heartRateMeasuredSeconds", measuredSeconds.toString())
        put("heartRateUnclassifiedSeconds", unclassifiedSeconds.toString())
        zoneSchemeId?.let { put("heartRateZoneScheme", it) }
        zoneSeconds.forEach { (zone, seconds) -> put("zone${zone}Seconds", seconds.toString()) }
        if (timeline.isNotEmpty()) put("heartRateTimeline", CardioHeartRateTimeline.encode(timeline))
        primaryProvenance?.toMetadata()?.let { putAll(it) }
    }
}

internal object CardioHeartRateTimeline {
    fun downsample(
        samples: List<CardioHeartRateSample>,
        maxPoints: Int = CARDIO_HR_TIMELINE_MAX_POINTS
    ): List<CardioHeartRateSample> {
        val clean = samples
            .asSequence()
            .filter { it.isPhysiologicallyStorable }
            .distinctBy { it.timestampEpochMs to it.bpm }
            .sortedBy { it.timestampEpochMs }
            .toList()
        if (maxPoints <= 0 || clean.isEmpty()) return emptyList()
        if (clean.size <= maxPoints) return clean
        if (maxPoints == 1) return listOf(clean.last())
        if (maxPoints == 2) return listOf(clean.first(), clean.last())

        val interior = clean.subList(1, clean.lastIndex)
        val bucketCount = ((maxPoints - 2) / 2).coerceAtLeast(1)
        val chunkSize = ceil(interior.size.toDouble() / bucketCount.toDouble()).toInt().coerceAtLeast(1)
        val output = ArrayList<CardioHeartRateSample>(maxPoints)
        output += clean.first()
        interior.chunked(chunkSize).forEach { bucket ->
            if (output.size >= maxPoints - 1) return@forEach
            val low = bucket.minByOrNull { it.bpm }
            val high = bucket.maxByOrNull { it.bpm }
            listOfNotNull(low, high)
                .distinctBy { it.timestampEpochMs to it.bpm }
                .sortedBy { it.timestampEpochMs }
                .forEach {
                    if (output.size < maxPoints - 1) output += it
                }
        }
        output += clean.last()
        return output.distinctBy { it.timestampEpochMs to it.bpm }.sortedBy { it.timestampEpochMs }.take(maxPoints)
    }

    fun encode(samples: List<CardioHeartRateSample>): String =
        samples.joinToString(";") { sample ->
            val pkg = sample.source.sourcePackage.orEmpty().replace(";", "").replace(":", "")
            val device = sample.source.anonymousSensorId.orEmpty().replace(";", "").replace(":", "")
            "${sample.timestampEpochMs}:${sample.receivedAtEpochMs}:${sample.importedAtEpochMs ?: 0L}:${sample.timeBasis.name}:${sample.bpm}:${sample.source.providerType.name}:$device:$pkg"
        }
}

internal object CardioHeartRateProcessor {
    fun summarise(
        samples: List<CardioHeartRateSample>,
        activeWindows: List<CardioActiveWindow>,
        zoneScheme: CardioHrZoneScheme = CardioHrZoneScheme.neutralFiveBand()
    ): CardioHeartRateSummary {
        val windows = activeWindows.filter { it.durationMs > 0L }.sortedBy { it.startEpochMs }
        val totalActiveMs = windows.sumOf { it.durationMs }
        if (totalActiveMs <= 0L) return CardioHeartRateSummary(zoneSchemeId = zoneScheme.id)

        val minStart = windows.first().startEpochMs
        val maxEnd = windows.last().endEpochMs
        val clean = samples.asSequence()
            .filter { it.isPhysiologicallyStorable }
            .filter { it.timestampEpochMs in minStart..maxEnd }
            .filter { sample -> windows.any { sample.timestampEpochMs >= it.startEpochMs && sample.timestampEpochMs < it.endEpochMs } }
            .distinctBy { it.timestampEpochMs to it.bpm }
            .sortedBy { it.timestampEpochMs }
            .toList()

        if (clean.isEmpty()) {
            return CardioHeartRateSummary(
                heartRateCoveragePct = 0.0,
                measuredSeconds = 0,
                unclassifiedSeconds = (totalActiveMs / 1000L).toInt(),
                zoneSchemeId = zoneScheme.id
            )
        }

        var measuredMs = 0L
        val zoneMs = mutableMapOf<Int, Long>()
        windows.forEach { window ->
            val inWindow = clean.filter { it.timestampEpochMs >= window.startEpochMs && it.timestampEpochMs < window.endEpochMs }
            inWindow.forEachIndexed { index, sample ->
                val nextTimestamp = inWindow.getOrNull(index + 1)?.timestampEpochMs ?: window.endEpochMs
                val segmentEnd = minOf(
                    nextTimestamp,
                    sample.timestampEpochMs + CARDIO_HR_STALE_AFTER_MS,
                    window.endEpochMs
                )
                val segmentMs = (segmentEnd - sample.timestampEpochMs).coerceAtLeast(0L)
                if (segmentMs > 0L) {
                    measuredMs += segmentMs
                    zoneScheme.zoneFor(sample.bpm)?.let { zone ->
                        zoneMs[zone] = (zoneMs[zone] ?: 0L) + segmentMs
                    }
                }
            }
        }

        measuredMs = measuredMs.coerceAtMost(totalActiveMs)
        val measuredSeconds = (measuredMs / 1000.0).roundToInt()
        val totalSeconds = (totalActiveMs / 1000.0).roundToInt()
        val coverage = (measuredMs.toDouble() / totalActiveMs.toDouble() * 100.0).coerceIn(0.0, 100.0)
        val zoneSeconds = zoneMs.mapValues { (_, ms) -> (ms / 1000.0).roundToInt() }
        val primary = clean.groupingBy { it.source }.eachCount().maxByOrNull { it.value }?.key
        val latest = clean.last()

        return CardioHeartRateSummary(
            averageBpm = clean.map { it.bpm }.average().roundToInt(),
            minBpm = clean.minOfOrNull { it.bpm },
            maxBpm = clean.maxOfOrNull { it.bpm },
            currentBpm = latest.bpm,
            lastSampleEpochMs = latest.timestampEpochMs,
            sampleCount = clean.size,
            heartRateCoveragePct = coverage,
            measuredSeconds = measuredSeconds,
            unclassifiedSeconds = (totalSeconds - measuredSeconds).coerceAtLeast(0),
            zoneSeconds = zoneSeconds,
            currentZone = zoneScheme.zoneFor(latest.bpm),
            zoneSchemeId = zoneScheme.id,
            timeline = CardioHeartRateTimeline.downsample(clean),
            primaryProvenance = primary
        )
    }
}

internal class CardioSessionHeartRateCollector(
    private var zoneScheme: CardioHrZoneScheme = CardioHrZoneScheme.neutralFiveBand()
) {
    private var sessionId: String? = null
    private val samples = mutableListOf<CardioHeartRateSample>()
    private val windows = mutableListOf<CardioActiveWindow>()
    private var openWindowStart: Long? = null
    private var stoppedAt: Long? = null

    fun start(id: String, startedAtEpochMs: Long) {
        sessionId = id
        samples.clear()
        windows.clear()
        openWindowStart = startedAtEpochMs
        stoppedAt = null
    }

    fun pause(atEpochMs: Long) {
        val start = openWindowStart ?: return
        if (atEpochMs > start) windows += CardioActiveWindow(start, atEpochMs)
        openWindowStart = null
    }

    fun resume(atEpochMs: Long) {
        if (sessionId == null || stoppedAt != null || openWindowStart != null) return
        openWindowStart = atEpochMs
    }

    fun accept(sample: CardioHeartRateSample): Boolean {
        if (sessionId == null || stoppedAt != null || openWindowStart == null) return false
        if (!sample.isPhysiologicallyStorable) return false
        val start = openWindowStart ?: return false
        if (sample.timestampEpochMs < start) return false
        if (samples.lastOrNull()?.let { it.timestampEpochMs == sample.timestampEpochMs && it.bpm == sample.bpm } == true) return false
        samples += sample
        return true
    }

    fun snapshot(nowEpochMs: Long): CardioHeartRateSummary {
        if (sessionId == null) return CardioHeartRateSummary(zoneSchemeId = zoneScheme.id)
        val effectiveWindows = buildList {
            addAll(windows)
            openWindowStart?.let { start ->
                if (nowEpochMs > start) add(CardioActiveWindow(start, nowEpochMs))
            }
        }
        return CardioHeartRateProcessor.summarise(samples, effectiveWindows, zoneScheme)
    }

    fun stop(endedAtEpochMs: Long): CardioHeartRateSummary {
        if (sessionId == null) return CardioHeartRateSummary(zoneSchemeId = zoneScheme.id)
        if (stoppedAt == null) {
            openWindowStart?.let { start ->
                if (endedAtEpochMs > start) windows += CardioActiveWindow(start, endedAtEpochMs)
            }
            openWindowStart = null
            stoppedAt = endedAtEpochMs
        }
        return CardioHeartRateProcessor.summarise(samples, windows, zoneScheme)
    }

    fun setZoneScheme(value: CardioHrZoneScheme) {
        zoneScheme = value
    }

    fun isCollecting(): Boolean = sessionId != null && stoppedAt == null
}
