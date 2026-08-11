package com.projectsuperhuman.next

import kotlin.math.roundToInt

internal data class HeartRateRawPoint(
    val timestampEpochMs: Long,
    val bpm: Double
)

internal data class HeartRateSmoothedPoint(
    val timestampEpochMs: Long,
    val bpm: Double
)

internal data class HeartRateNativeAnalysis(
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
    val latestSmoothedBpm: Double,
    val smoothed: List<HeartRateSmoothedPoint>,
    val engineVersion: String = "cpp-hr-v1"
)

/**
 * Small JNI boundary around the C++ heart-rate signal engine.
 *
 * Health Connect remains the source of truth. Native code only analyses timestamped BPM samples;
 * it never owns permissions, storage or UI state. This keeps the C++ layer deterministic and
 * testable while the Android layer remains responsible for provenance and user consent.
 */
internal object HeartRateNativeEngine {
    private const val HEADER_SIZE = 16

    private val nativeLoaded: Boolean = runCatching {
        System.loadLibrary("psh_heart_rate")
        true
    }.getOrDefault(false)

    private external fun analyzeNative(timestampsMs: LongArray, bpmValues: DoubleArray): DoubleArray

    fun isAvailable(): Boolean = nativeLoaded

    fun analyse(points: List<HeartRateRawPoint>): HeartRateNativeAnalysis? {
        if (!nativeLoaded) return null

        val clean = points.asSequence()
            .filter { it.timestampEpochMs > 0L && it.bpm.isFinite() && it.bpm in 25.0..260.0 }
            .sortedBy { it.timestampEpochMs }
            .toList()
        if (clean.size < 3) return null

        val native = runCatching {
            analyzeNative(
                timestampsMs = clean.map { it.timestampEpochMs }.toLongArray(),
                bpmValues = clean.map { it.bpm }.toDoubleArray()
            )
        }.getOrNull() ?: return null

        if (native.size < HEADER_SIZE) return null
        val nativeCount = native[14].roundToInt().coerceAtLeast(0)
        val usableCount = minOf(nativeCount, clean.size, native.size - HEADER_SIZE)
        if (usableCount <= 0) return null

        fun finite(index: Int): Double? = native.getOrNull(index)?.takeIf(Double::isFinite)
        val baseline = finite(0) ?: return null
        val mean = finite(1) ?: return null
        val min = finite(2) ?: return null
        val max = finite(3) ?: return null
        val p95 = finite(4) ?: return null
        val stability = finite(5) ?: return null
        val coverage = finite(6) ?: return null
        val latest = finite(15) ?: return null

        val smoothed = buildList {
            repeat(usableCount) { index ->
                val value = native[HEADER_SIZE + index]
                if (value.isFinite()) {
                    add(
                        HeartRateSmoothedPoint(
                            timestampEpochMs = clean[index].timestampEpochMs,
                            bpm = value
                        )
                    )
                }
            }
        }

        return HeartRateNativeAnalysis(
            estimatedBaselineBpm = baseline,
            meanBpm = mean,
            minBpm = min,
            maxBpm = max,
            p95Bpm = p95,
            stabilityScore = stability.coerceIn(0.0, 100.0),
            coveragePct = coverage.coerceIn(0.0, 100.0),
            unusualSampleCount = finite(7)?.roundToInt()?.coerceAtLeast(0) ?: 0,
            recentTrendBpmPerHour = finite(8),
            observedRecoveryDropBpm = finite(9),
            lowBandPct = finite(10)?.coerceIn(0.0, 100.0) ?: 0.0,
            moderateBandPct = finite(11)?.coerceIn(0.0, 100.0) ?: 0.0,
            elevatedBandPct = finite(12)?.coerceIn(0.0, 100.0) ?: 0.0,
            highBandPct = finite(13)?.coerceIn(0.0, 100.0) ?: 0.0,
            sampleCount = usableCount,
            latestSmoothedBpm = latest,
            smoothed = smoothed
        )
    }
}
