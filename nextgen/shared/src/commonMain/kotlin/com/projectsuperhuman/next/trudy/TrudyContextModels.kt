package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain

enum class TrudyEvidenceKind {
    DIRECT_PERSONAL_OBSERVATION,
    DERIVED_PERSONAL_TREND,
    INTERPRETATION,
    EXTERNAL_SCIENTIFIC_EVIDENCE,
    UNCERTAINTY_OR_DATA_GAP
}

/** Provenance class for a stored value. This is deliberately separate from confidence/quality. */
enum class TrudyValueClass {
    MEASURED,
    DERIVED,
    ESTIMATED,
    INFERRED,
    UNKNOWN
}

data class TrudyTimeRange(
    val fromEpochMs: Long,
    val toEpochMs: Long
) {
    init {
        require(fromEpochMs <= toEpochMs) { "fromEpochMs must be <= toEpochMs" }
    }
}

data class TrudyDataQualityEvidence(
    val domain: HealthDomain,
    val score: Int,
    val recordCount: Long,
    val distinctMetricCount: Int,
    val latestTimestampEpochMs: Long?,
    val ageHours: Double?,
    val isStale: Boolean,
    val notes: List<String>
)

data class TrudyMetricEvidence(
    val domain: HealthDomain,
    val metricId: String,
    val value: Double,
    val unit: String,
    val timestampEpochMs: Long,
    val sampleCount: Int = 1,
    val source: String,
    val confidence: Double? = null,
    val dataQuality: TrudyDataQualityEvidence? = null,
    val evidenceKind: TrudyEvidenceKind = TrudyEvidenceKind.DIRECT_PERSONAL_OBSERVATION,
    val metadata: Map<String, String> = emptyMap(),
    val valueClass: TrudyValueClass = TrudyValueClass.MEASURED,
    val confidenceLabel: String? = null,
    val algorithmVersion: String? = null,
    val sessionId: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val coverageFraction: Double? = null,
    val caveat: String? = null
) {
    init {
        require(sampleCount >= 0)
        require(confidence == null || confidence.isFinite())
        require(coverageFraction == null || coverageFraction.isFinite() && coverageFraction in 0.0..1.0)
    }
}

data class TrudyDerivedMetricEvidence(
    val domain: HealthDomain,
    val metricId: String,
    val unit: String,
    val sampleCount: Int,
    val latest: Double,
    val mean: Double,
    val minimum: Double,
    val maximum: Double,
    val change: Double?,
    val range: TrudyTimeRange,
    val source: String,
    val confidence: Double? = null,
    val dataQuality: TrudyDataQualityEvidence? = null,
    val evidenceKind: TrudyEvidenceKind = TrudyEvidenceKind.DERIVED_PERSONAL_TREND
)

data class TrudyInsightEvidence(
    val id: String,
    val domain: HealthDomain,
    val evidenceKind: TrudyEvidenceKind,
    val title: String,
    val explanation: String,
    val evidenceMetricIds: List<String>,
    val confidence: Double?,
    val source: String,
    val dataQuality: TrudyDataQualityEvidence? = null
)

data class TrudyContextRequest(
    val domains: List<HealthDomain>,
    val historyLimitPerDomain: Int = 250,
    val historyOffsetPerDomain: Int = 0,
    val includeCurrentState: Boolean = true,
    val includeHistory: Boolean = true,
    val includeDerivedFeatures: Boolean = true,
    val includeInsights: Boolean = true,
    val includeDataQuality: Boolean = true
) {
    init {
        require(domains.isNotEmpty()) { "At least one HealthDomain must be requested" }
        require(domains.distinct().size == domains.size) { "Requested domains must be unique" }
        require(historyLimitPerDomain > 0) { "historyLimitPerDomain must be > 0" }
        require(historyOffsetPerDomain >= 0) { "historyOffsetPerDomain must be >= 0" }
    }
}

data class TrudyDomainContext(
    val domain: HealthDomain,
    val currentState: List<TrudyMetricEvidence>,
    val history: List<TrudyMetricEvidence>,
    val derivedFeatures: List<TrudyDerivedMetricEvidence>,
    val insights: List<TrudyInsightEvidence>,
    val dataQuality: TrudyDataQualityEvidence?
)

data class TrudyHealthContext(
    val requestedDomains: List<HealthDomain>,
    val domains: List<TrudyDomainContext>
) {
    init {
        require(domains.map { it.domain } == requestedDomains) {
            "Context domains must exactly match explicitly requested domains"
        }
    }
}
