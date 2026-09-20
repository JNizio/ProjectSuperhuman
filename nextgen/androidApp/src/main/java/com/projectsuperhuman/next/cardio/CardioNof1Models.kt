package com.projectsuperhuman.next

internal const val CARDIO_NOF1_ALGORITHM_VERSION = "cardio-nof1-v1"

internal enum class CardioValueClass {
    MEASURED,
    DERIVED,
    ESTIMATED,
    INFERRED,
    UNAVAILABLE
}

internal enum class CardioObservationQuality {
    ACCEPTED,
    STALE,
    GAP_ADJACENT,
    SUSPECT_OUTLIER,
    FILTERED,
    INTERPOLATED,
    INVALID
}

internal enum class CardioSourceKind {
    DIRECT_BLE,
    GENERIC_BLE,
    H19C,
    DIRECT_WEARABLE,
    HEALTH_CONNECT,
    FIT_IMPORT,
    TCX_IMPORT,
    GPX_IMPORT,
    CSV_IMPORT,
    PHONE_GPS,
    MANUAL,
    DERIVED
}

internal data class CardioObservationProvenance(
    val sourceKind: CardioSourceKind,
    val sourceName: String,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val providerPackage: String? = null,
    val externalRecordId: String? = null,
    val sourceProvider: String? = null,
    val sourceTransport: String? = null,
    val stableSourceId: String? = null
)

internal data class CardioRawObservation(
    val sessionId: String,
    val metricId: String,
    val originalTimestampEpochMs: Long,
    val originalValue: Double,
    val canonicalValue: Double = originalValue,
    val unit: String,
    val ingestionTimestampEpochMs: Long,
    val provenance: CardioObservationProvenance,
    val quality: CardioObservationQuality = CardioObservationQuality.ACCEPTED,
    val exclusionReason: String? = null,
    val interpolated: Boolean = false,
    val resampled: Boolean = false,
    val processingVersion: String = CARDIO_NOF1_ALGORITHM_VERSION
)

internal data class CardioRrIntervalSample(
    val timestampEpochMs: Long,
    val rrMs: Double,
    val source: CardioSensorProvenance,
    val receivedAtEpochMs: Long = timestampEpochMs,
    val quality: CardioObservationQuality = CardioObservationQuality.ACCEPTED,
    val correctionApplied: Boolean = false
) {
    val isPhysiologicallyStorable: Boolean get() = rrMs in 250.0..2_500.0
}

internal enum class CardioZoneModel {
    HRR,
    HR_MAX,
    LTHR,
    THREE_ZONE,
    MANUAL
}

internal enum class CardioHrMaxSource(val priority: Int) {
    VALIDATED_OBSERVED(3),
    MANUAL_CONFIRMED(2),
    FORMULA_ESTIMATE(1)
}

internal data class CardioZoneBoundary(
    val zone: Int,
    val name: String,
    val minBpmInclusive: Int,
    val maxBpmInclusive: Int,
    val purpose: String,
    val calculationMethod: String = ""
)

internal data class CardioPhysiologyProfile(
    val revisionId: String,
    val effectiveFromEpochMs: Long,
    val hrMaxBpm: Int?,
    val hrMaxSource: CardioHrMaxSource?,
    val restingHrBpm: Int?,
    val lactateThresholdHrBpm: Int?,
    val zoneModel: CardioZoneModel,
    val zones: List<CardioZoneBoundary>,
    val sport: CardioActivityType? = null,
    val sportSpecificSettings: Map<String, String> = emptyMap(),
    val createdAtEpochMs: Long,
    val algorithmVersion: String = CARDIO_NOF1_ALGORITHM_VERSION
)

internal enum class CardioConfidence(val label: String) {
    INSUFFICIENT("Insufficient"),
    LOW("Low"),
    MODERATE("Moderate"),
    HIGH("High")
}

internal data class CardioDerivedMetric(
    val metricId: String,
    val value: Double?,
    val unit: String,
    val valueClass: CardioValueClass,
    val confidence: CardioConfidence,
    val algorithmVersion: String,
    val requiredInputs: List<String>,
    val caveat: String? = null,
    val sourceIds: List<String> = emptyList(),
    val generatedAtEpochMs: Long? = null
)

internal data class CardioHrMaxCandidate(
    val candidateBpm: Int,
    val configuredBpm: Int,
    val sustainedDurationMs: Long,
    val sampleCount: Int,
    val confidence: CardioConfidence,
    val sourceSummary: String,
    val reasons: List<String>,
    val coveragePct: Double? = null,
    val requiresConfirmation: Boolean = true
)

internal data class CardioHrMaxEvidence(
    val bpm: Int,
    val source: CardioHrMaxSource,
    val observedAtEpochMs: Long = 0L,
    val note: String? = null
)

internal object CardioHrMaxPolicy {
    fun select(evidence: List<CardioHrMaxEvidence>): CardioHrMaxEvidence? =
        evidence
            .filter { it.bpm in CARDIO_HR_MIN_BPM..CARDIO_HR_MAX_BPM }
            .sortedWith(
                compareByDescending<CardioHrMaxEvidence> { it.source.priority }
                    .thenByDescending { it.observedAtEpochMs }
            )
            .firstOrNull()
}

internal data class CardioTrainingLoadPoint(
    val epochDay: Long,
    val dailyLoad: Double,
    val chronicLoad: Double,
    val acuteLoad: Double,
    val trainingStressBalance: Double,
    val scoredSessionCount: Int
)

internal data class CardioFitnessSnapshot(
    val trendLabel: String,
    val trendDeltaPercent: Double?,
    val confidence: CardioConfidence,
    val basis: String,
    val comparableSessionCount: Int
)

internal data class CardioRecoveryContext(
    val restingHeartRateBpm: Double? = null,
    val restingHeartRateBaselineBpm: Double? = null,
    val hrvRmssdMs: Double? = null,
    val hrvBaselineRmssdMs: Double? = null,
    val sleepScore: Double? = null,
    val trainingStressBalance: Double? = null,
    val sleepDurationMinutes: Double? = null,
    val sleepObservedAtEpochMs: Long? = null,
    val bodyWeightKg: Double? = null,
    val bodyWeightObservedAtEpochMs: Long? = null,
    val stressScore0To10: Double? = null,
    val stressObservedAtEpochMs: Long? = null,
    val environmentTemperatureC: Double? = null,
    val environmentRelativeHumidityPct: Double? = null,
    val environmentObservedAtEpochMs: Long? = null
)

internal data class CardioReadinessSnapshot(
    val status: String,
    val score: Double?,
    val confidence: CardioConfidence,
    val availableSignals: Int,
    val totalSignals: Int,
    val explanation: String,
    val disclaimer: String = "Training/recovery signal only — not medical clearance."
)

internal data class CardioWeekIntentSnapshot(
    val minutes: Int,
    val sessions: Int,
    val zone2Minutes: Int,
    val targetMinutes: Int?,
    val progressFraction: Double?,
    val label: String
)

internal data class CardioNof1OverviewSnapshot(
    val fitness: CardioFitnessSnapshot,
    val readiness: CardioReadinessSnapshot,
    val week: CardioWeekIntentSnapshot,
    val generatedAtEpochMs: Long
)
