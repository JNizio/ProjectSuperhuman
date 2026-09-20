package com.projectsuperhuman.next

internal const val CARDIO_INTELLIGENCE_ALGORITHM_VERSION = "cardio-intelligence-v1"

internal enum class CardioAnalyticState {
    AVAILABLE,
    BUILDING_BASELINE,
    REQUIRES_INPUT,
    INSUFFICIENT_DATA,
    STALE,
    INCOMPATIBLE
}

internal data class CardioSessionEvidence(
    val session: CardioSession,
    val samples: List<CardioTimeSeriesSample> = emptyList(),
    val rrIntervals: List<CardioRrIntervalSample> = emptyList(),
    val postEffortHeartRate: List<CardioPostEffortHrSample> = emptyList(),
    val ambientTemperatureC: Double? = null,
    val humidityPercent: Double? = null,
    val bodyWeightKg: Double? = null,
    val thresholdPowerWatts: Double? = null,
    val verifiedPerformance: Boolean = false,
    val confounders: Set<String> = emptySet()
)

internal enum class CardioComparability {
    COMPARABLE,
    PARTIALLY_COMPARABLE,
    UNSUITABLE
}

internal data class CardioComparabilityResult(
    val verdict: CardioComparability,
    val score: Double,
    val reasons: List<String>,
    val qualityWarnings: List<String>
)

internal enum class CardioIntelligenceLoadMethod(val scaleKey: String) {
    HR_ZONE_WEIGHTED("zone-weighted-minutes"),
    POWER_STRESS("power-stress"),
    SESSION_RPE("session-rpe-minutes"),
    UNAVAILABLE("unavailable")
}

internal data class CardioSessionLoadEstimate(
    val sessionId: String,
    val value: Double?,
    val method: CardioIntelligenceLoadMethod,
    val confidence: CardioConfidence,
    val coveragePercent: Double,
    val formula: String,
    val algorithmVersion: String = CARDIO_INTELLIGENCE_ALGORITHM_VERSION,
    val caveat: String? = null
)

internal data class CardioLongitudinalLoadPoint(
    val epochDay: Long,
    val dailyLoad: Double?,
    val chronicLoad: Double?,
    val acuteLoad: Double?,
    val trainingStressBalance: Double?,
    val scoredSessionCount: Int,
    val totalSessionCount: Int,
    val methods: Set<CardioIntelligenceLoadMethod>,
    val confidence: CardioConfidence
)

internal data class CardioTrainingLoadSeries(
    val points: List<CardioLongitudinalLoadPoint>,
    val methodCounts: Map<CardioIntelligenceLoadMethod, Int>,
    val scoredSessionFraction: Double,
    val mixedScaleWarning: Boolean,
    val algorithmVersion: String = CARDIO_INTELLIGENCE_ALGORITHM_VERSION
)

internal data class CardioHrBandEstimate(
    val value: Double?,
    val unit: String,
    val target: Double,
    val tolerance: Double,
    val usableSamples: Int,
    val totalSamples: Int,
    val coveragePercent: Double,
    val confidence: CardioConfidence,
    val state: CardioAnalyticState,
    val formula: String,
    val algorithmVersion: String = CARDIO_INTELLIGENCE_ALGORITHM_VERSION
)

internal data class CardioEfficiencyEstimate(
    val value: Double?,
    val unit: String,
    val input: String,
    val usableSamples: Int,
    val coveragePercent: Double,
    val confidence: CardioConfidence,
    val state: CardioAnalyticState,
    val formula: String,
    val algorithmVersion: String = CARDIO_INTELLIGENCE_ALGORITHM_VERSION
)

internal data class CardioDecouplingEstimate(
    val percent: Double?,
    val input: String?,
    val usableSamples: Int,
    val coveragePercent: Double,
    val confidence: CardioConfidence,
    val state: CardioAnalyticState,
    val caveat: String,
    val formula: String = "100 * (EF_first_half - EF_second_half) / EF_first_half",
    val algorithmVersion: String = CARDIO_INTELLIGENCE_ALGORITHM_VERSION
)

internal data class CardioPostEffortHrSample(
    val secondsAfterExerciseEnd: Double,
    val bpm: Double
)

internal data class CardioHeartRateRecoveryEstimate(
    val endHeartRateBpm: Double?,
    val hrr1MinuteBpm: Double?,
    val hrr2MinuteBpm: Double?,
    val usableSamples: Int,
    val confidence: CardioConfidence,
    val state: CardioAnalyticState,
    val caveat: String,
    val algorithmVersion: String = CARDIO_INTELLIGENCE_ALGORITHM_VERSION
)

internal data class CardioHrvEstimate(
    val rmssdMs: Double?,
    val validIntervals: Int,
    val correctedIntervals: Int,
    val rejectedIntervals: Int,
    val coveragePercent: Double,
    val confidence: CardioConfidence,
    val state: CardioAnalyticState,
    val artifactPolicy: String,
    val algorithmVersion: String = CARDIO_INTELLIGENCE_ALGORITHM_VERSION
)

internal enum class CardioVo2Method {
    COOPER_12_MINUTE
}

internal data class CardioVo2Estimate(
    val mlKgMin: Double?,
    val method: CardioVo2Method,
    val confidence: CardioConfidence,
    val state: CardioAnalyticState,
    val formula: String,
    val assumptions: List<String>,
    val caveat: String,
    val algorithmVersion: String = CARDIO_INTELLIGENCE_ALGORITHM_VERSION
)

internal data class CardioPerformanceEffort(
    val durationSeconds: Double,
    val distanceMeters: Double,
    val verified: Boolean,
    val sessionId: String? = null
)

internal data class CardioCriticalSpeedEstimate(
    val criticalSpeedMetersPerSecond: Double?,
    val dPrimeMeters: Double?,
    val rSquared: Double?,
    val effortCount: Int,
    val confidence: CardioConfidence,
    val state: CardioAnalyticState,
    val formula: String = "distance = criticalSpeed * time + D-prime",
    val caveat: String,
    val algorithmVersion: String = CARDIO_INTELLIGENCE_ALGORITHM_VERSION
)

internal data class CardioZoneDistributionByProfile(
    val secondsByProfileAndZone: Map<String, Map<Int, Int>>,
    val unclassifiedSeconds: Int,
    val compatibleForCombinedAnalysis: Boolean,
    val state: CardioAnalyticState,
    val caveat: String
)

internal data class CardioBaselineStats(
    val median: Double?,
    val mean: Double?,
    val mad: Double?,
    val robustStandardDeviation: Double?,
    val sampleCount: Int,
    val firstTimestampEpochMs: Long?,
    val lastTimestampEpochMs: Long?,
    val confidence: CardioConfidence,
    val state: CardioAnalyticState,
    val stale: Boolean
)

internal enum class CardioMeaningfulChangeState {
    LIKELY_SIGNAL,
    ORDINARY_VARIATION,
    INSUFFICIENT_EVIDENCE
}

internal data class CardioMeaningfulChange(
    val state: CardioMeaningfulChangeState,
    val absoluteChange: Double?,
    val percentChange: Double?,
    val standardizedEffect: Double?,
    val baselineSamples: Int,
    val recentSamples: Int,
    val repeatedDirectionFraction: Double?,
    val confidence: CardioConfidence,
    val caveats: List<String>
)

internal data class CardioChangePoint(
    val timestampEpochMs: Long?,
    val beforeMedian: Double?,
    val afterMedian: Double?,
    val standardizedShift: Double?,
    val beforeSamples: Int,
    val afterSamples: Int,
    val confidence: CardioConfidence,
    val state: CardioAnalyticState,
    val caveat: String
)

internal data class CardioTimedValue(
    val timestampEpochMs: Long,
    val value: Double
)

internal enum class CardioCorrelationMethod {
    PEARSON,
    SPEARMAN
}

internal data class CardioCorrelationResult(
    val coefficient: Double?,
    val method: CardioCorrelationMethod,
    val sampleCount: Int,
    val matchedFraction: Double,
    val lagMs: Long,
    val confidence: CardioConfidence,
    val state: CardioAnalyticState,
    val caveat: String = "Association does not establish causation."
)

internal data class CardioNof1ExperimentSpec(
    val id: String,
    val hypothesis: String,
    val intervention: String,
    val outcomeMetricId: String,
    val baselineStartEpochMs: Long,
    val baselineEndEpochMs: Long,
    val interventionStartEpochMs: Long,
    val interventionEndEpochMs: Long,
    val expectedDirection: Int = 0,
    val confounders: Set<String> = emptySet()
)

internal data class CardioNof1ExperimentObservation(
    val timestampEpochMs: Long,
    val value: Double,
    val phase: String,
    val adherent: Boolean = true,
    val confounders: Set<String> = emptySet(),
    val evidenceReference: String? = null
)

internal data class CardioNof1ExperimentResult(
    val experimentId: String,
    val baselineMedian: Double?,
    val interventionMedian: Double?,
    val absoluteChange: Double?,
    val percentChange: Double?,
    val standardizedEffect: Double?,
    val baselineSamples: Int,
    val interventionSamples: Int,
    val adherenceFraction: Double,
    val confidence: CardioConfidence,
    val conclusion: String,
    val uncertainty: String,
    val evidenceReferences: List<String>
)

internal enum class CardioFitnessSignalType {
    PACE_AT_HEART_RATE,
    HEART_RATE_AT_PACE,
    AEROBIC_EFFICIENCY,
    DECOUPLING,
    HEART_RATE_RECOVERY,
    VERIFIED_PERFORMANCE,
    POWER_AT_HEART_RATE,
    RPE_AT_OUTPUT
}

internal data class CardioFitnessSignal(
    val type: CardioFitnessSignalType,
    val deltaPercent: Double?,
    val direction: Int,
    val sampleCount: Int,
    val confidence: CardioConfidence,
    val explanation: String
)

internal data class CardioSportFitnessSnapshot(
    val activity: CardioActivityType,
    val label: String,
    val signals: List<CardioFitnessSignal>,
    val comparableSessionCount: Int,
    val confidence: CardioConfidence,
    val state: CardioAnalyticState,
    val caveat: String
)

internal data class CardioInsight(
    val id: String,
    val title: String,
    val body: String,
    val metricId: String,
    val sampleCount: Int,
    val confidence: CardioConfidence,
    val comparisonWindow: String,
    val confounders: List<String>,
    val definition: String
)

internal enum class CardioSummaryPeriod {
    WEEKLY,
    MONTHLY
}

internal data class CardioPeriodSummary(
    val period: CardioSummaryPeriod,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val sessionCount: Int,
    val minutes: Int,
    val distanceKm: Double?,
    val load: Double?,
    val zoneSeconds: Map<Int, Int>,
    val fitnessSignals: List<CardioFitnessSignal>,
    val records: List<String>,
    val dataQualityNotes: List<String>,
    val baselineStatus: String,
    val missingData: List<String>
)
