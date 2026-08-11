package com.projectsuperhuman.next

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

internal data class StoredHeartRateAdvancedAnalysis(
    val latestSmoothedBpm: Double,
    val estimatedBaselineBpm: Double,
    val meanBpm: Double,
    val minBpm: Double,
    val maxBpm: Double,
    val p95Bpm: Double,
    val stabilityScore: Double,
    val coveragePct: Double,
    val unusualSampleCount: Int,
    val recentTrendBpmPerHour: Double?,
    val observedRecoveryDropBpm: Double?,
    val lowBandPct: Double,
    val moderateBandPct: Double,
    val elevatedBandPct: Double,
    val highBandPct: Double,
    val sampleCount: Int,
    val smoothed: List<HeartRateSmoothedPoint>,
    val analysedAtEpochMs: Long,
    val engineVersion: String
)

internal data class HeartRateAdvancedSyncResult(
    val success: Boolean,
    val sampleCount: Int,
    val message: String
)

/**
 * Reads Samsung Health heart-rate samples from Health Connect, runs the deterministic native C++
 * analysis engine, then stores only compact derived evidence in the Data Vault.
 *
 * Raw wearable samples remain owned by Health Connect; Project Superhuman avoids duplicating a
 * potentially huge sensor archive. A downsampled smoothed trace is retained as metadata so the
 * UI remains useful offline after a successful sync.
 */
internal object HeartRateAdvancedHealthConnect {
    const val SOURCE = "heart-rate-cpp-v1"
    private const val ANALYSIS_METRIC = "heart_rate_cpp_analysis"
    private const val SAMSUNG_HEALTH_PACKAGE = "com.sec.android.app.shealth"
    private const val MAX_TIMELINE_POINTS = 96

    private val samsungFilter = setOf(DataOrigin(SAMSUNG_HEALTH_PACKAGE))

    suspend fun sync(context: Context): HeartRateAdvancedSyncResult {
        if (MiniMetricsHealthConnect.availability(context) != HealthConnectClient.SDK_AVAILABLE) {
            return HeartRateAdvancedSyncResult(false, 0, "Health Connect isn’t available")
        }
        if (!MiniMetricsHealthConnect.hasPermission(context, HomeMiniMetric.HEART_RATE)) {
            return HeartRateAdvancedSyncResult(false, 0, "Heart-rate access isn’t enabled")
        }
        if (!HeartRateNativeEngine.isAvailable()) {
            return HeartRateAdvancedSyncResult(false, 0, "Native heart-rate engine isn’t available")
        }

        return try {
            withTimeout(18_000L) {
                val client = HealthConnectClient.getOrCreate(context)
                val end = Instant.now()
                val start = end.minus(Duration.ofHours(24))
                val rawPoints = readSamsungHeartRatePoints(client, start, end)
                if (rawPoints.size < 3) {
                    return@withTimeout HeartRateAdvancedSyncResult(
                        true,
                        rawPoints.size,
                        "Connected — not enough heart-rate samples for native analysis yet"
                    )
                }

                val analysis = HeartRateNativeEngine.analyse(rawPoints)
                    ?: return@withTimeout HeartRateAdvancedSyncResult(
                        false,
                        rawPoints.size,
                        "Native heart-rate analysis couldn’t run"
                    )

                persistAnalysis(analysis)
                HeartRateAdvancedSyncResult(
                    true,
                    analysis.sampleCount,
                    "${analysis.sampleCount} Samsung heart-rate samples analysed in C++"
                )
            }
        } catch (_: TimeoutCancellationException) {
            HeartRateAdvancedSyncResult(false, 0, "Advanced heart-rate analysis timed out")
        } catch (t: Throwable) {
            HeartRateAdvancedSyncResult(false, 0, "Advanced analysis couldn’t finish: ${t.javaClass.simpleName}")
        }
    }

    suspend fun loadLatestStored(): StoredHeartRateAdvancedAnalysis? {
        val now = System.currentTimeMillis()
        val from = now - Duration.ofDays(3).toMillis()
        val row = NativeDataHub.between(HealthDomain.EXERCISE, ANALYSIS_METRIC, from, now)
            .filter { it.source == SOURCE }
            .maxByOrNull { it.timestampEpochMs }
            ?: return null

        val meta = row.metadata
        fun number(key: String): Double? = meta[key]?.toDoubleOrNull()?.takeIf(Double::isFinite)
        val baseline = number("estimatedBaselineBpm") ?: return null
        val mean = number("meanBpm") ?: return null
        val min = number("minBpm") ?: return null
        val max = number("maxBpm") ?: return null
        val p95 = number("p95Bpm") ?: return null
        val stability = number("stabilityScore") ?: return null
        val coverage = number("coveragePct") ?: return null
        val sampleCount = meta["sampleCount"]?.toIntOrNull() ?: return null
        val smoothed = decodeTimeline(meta["smoothedTimeline"].orEmpty())

        return StoredHeartRateAdvancedAnalysis(
            latestSmoothedBpm = row.value,
            estimatedBaselineBpm = baseline,
            meanBpm = mean,
            minBpm = min,
            maxBpm = max,
            p95Bpm = p95,
            stabilityScore = stability,
            coveragePct = coverage,
            unusualSampleCount = meta["unusualSampleCount"]?.toIntOrNull() ?: 0,
            recentTrendBpmPerHour = number("recentTrendBpmPerHour"),
            observedRecoveryDropBpm = number("observedRecoveryDropBpm"),
            lowBandPct = number("lowBandPct") ?: 0.0,
            moderateBandPct = number("moderateBandPct") ?: 0.0,
            elevatedBandPct = number("elevatedBandPct") ?: 0.0,
            highBandPct = number("highBandPct") ?: 0.0,
            sampleCount = sampleCount,
            smoothed = smoothed,
            analysedAtEpochMs = row.timestampEpochMs,
            engineVersion = meta["engineVersion"] ?: "cpp-hr-v1"
        )
    }

    private suspend fun readSamsungHeartRatePoints(
        client: HealthConnectClient,
        start: Instant,
        end: Instant
    ): List<HeartRateRawPoint> {
        val points = mutableListOf<HeartRateRawPoint>()
        var pageToken: String? = null
        var pages = 0
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    dataOriginFilter = samsungFilter,
                    ascendingOrder = true,
                    pageSize = 5000,
                    pageToken = pageToken
                )
            )
            response.records.forEach { record ->
                record.samples.forEach { sample ->
                    if (!sample.time.isBefore(start) && !sample.time.isAfter(end)) {
                        points += HeartRateRawPoint(
                            timestampEpochMs = sample.time.toEpochMilli(),
                            bpm = sample.beatsPerMinute.toDouble()
                        )
                    }
                }
            }
            pageToken = response.pageToken
            pages += 1
        } while (pageToken != null && pages < 8)

        return points.distinctBy { it.timestampEpochMs to it.bpm }.sortedBy { it.timestampEpochMs }
    }

    private suspend fun persistAnalysis(analysis: HeartRateNativeAnalysis) {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L

        val old = NativeDataHub.between(HealthDomain.EXERCISE, ANALYSIS_METRIC, start, end)
            .filter { it.source == SOURCE }
        if (old.isNotEmpty()) NativeDataHub.deleteValues(old)

        val timeline = encodeTimeline(analysis.smoothed)
        val metadata = buildMap {
            put("sourceRecordId", "cpp-heart-analysis:$today")
            put("summaryDate", today.toString())
            put("sourcePackage", SAMSUNG_HEALTH_PACKAGE)
            put("engineVersion", analysis.engineVersion)
            put("sampleCount", analysis.sampleCount.toString())
            put("estimatedBaselineBpm", analysis.estimatedBaselineBpm.toString())
            put("meanBpm", analysis.meanBpm.toString())
            put("minBpm", analysis.minBpm.toString())
            put("maxBpm", analysis.maxBpm.toString())
            put("p95Bpm", analysis.p95Bpm.toString())
            put("stabilityScore", analysis.stabilityScore.toString())
            put("coveragePct", analysis.coveragePct.toString())
            put("unusualSampleCount", analysis.unusualSampleCount.toString())
            analysis.recentTrendBpmPerHour?.takeIf(Double::isFinite)?.let {
                put("recentTrendBpmPerHour", it.toString())
            }
            analysis.observedRecoveryDropBpm?.takeIf(Double::isFinite)?.let {
                put("observedRecoveryDropBpm", it.toString())
            }
            put("lowBandPct", analysis.lowBandPct.toString())
            put("moderateBandPct", analysis.moderateBandPct.toString())
            put("elevatedBandPct", analysis.elevatedBandPct.toString())
            put("highBandPct", analysis.highBandPct.toString())
            put("smoothedTimeline", timeline)
            put("interpretationScope", "signal-analysis-not-medical-diagnosis")
        }

        NativeDataHub.saveValues(
            listOf(
                HealthValue(
                    domain = HealthDomain.EXERCISE,
                    metric = ANALYSIS_METRIC,
                    value = analysis.latestSmoothedBpm,
                    unit = "bpm",
                    timestampEpochMs = now,
                    source = SOURCE,
                    metadata = metadata
                )
            )
        )
    }

    private fun encodeTimeline(points: List<HeartRateSmoothedPoint>): String {
        if (points.isEmpty()) return ""
        val stride = maxOf(1, ceil(points.size / MAX_TIMELINE_POINTS.toDouble()).roundToInt())
        return points.chunked(stride).joinToString(";") { chunk ->
            val time = chunk[chunk.size / 2].timestampEpochMs
            val bpm = chunk.map { it.bpm }.average()
            "$time,${"%.2f".format(java.util.Locale.US, bpm)}"
        }
    }

    private fun decodeTimeline(encoded: String): List<HeartRateSmoothedPoint> =
        encoded.split(';').mapNotNull { token ->
            val parts = token.split(',')
            if (parts.size != 2) return@mapNotNull null
            val time = parts[0].toLongOrNull() ?: return@mapNotNull null
            val bpm = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            HeartRateSmoothedPoint(time, bpm)
        }
}
