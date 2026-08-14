package com.projectsuperhuman.next.trudy

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Deterministic Environmental response synthesis. Numeric relationships arrive already calculated
 * by Trudy's typed tools; this class never derives a correlation from raw rows.
 */
class TrudyEnvironmentalSynthesizer(
    private val config: TrudyEnvironmentalConfig,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() }
) {
    fun current(
        requestedMetrics: List<TrudyEnvironmentalMetricBinding>,
        results: List<TrudyToolResult>
    ): TrudyEnvironmentalSynthesis {
        val measurements = environmentalMeasurements(results)
        val grounding = grounding(measurements, results)
        val latest = requestedMetrics.mapNotNull { binding ->
            measurements.filter { it.metricId == binding.metricId }
                .maxByOrNull { it.timestampEpochMs }
                ?.let { binding to it }
        }
        val text = if (latest.isEmpty()) {
            "I don't have stored environmental readings for right now, so I won't guess the weather."
        } else {
            val values = latest.take(6).joinToString(", ") { (binding, evidence) ->
                "${binding.displayName} ${formatValue(evidence.value)}${unitSuffix(evidence.unit)}"
            }
            val provenance = provenance(grounding)
            val qualityNote = if ((grounding.dataQuality?.score ?: 100) < 50) {
                " Coverage is limited, so I’d treat this cautiously."
            } else ""
            if (grounding.freshness == TrudyEnvironmentalFreshness.CURRENT) {
                "The latest stored environmental readings show $values.${if (provenance.isBlank()) "" else " $provenance."}$qualityNote"
            } else {
                "I don't have fresh enough readings to describe the environment right now. The latest stored values were $values.${if (provenance.isBlank()) "" else " $provenance."}$qualityNote"
            }
        }
        return TrudyEnvironmentalSynthesis(text, grounding.measurementReferences, grounding)
    }

    fun association(
        environmentalMetric: TrudyEnvironmentalMetricBinding,
        target: TrudyEnvironmentalTargetBinding,
        result: TrudyAssociationResult
    ): TrudyEnvironmentalSynthesis {
        val coefficient = result.coefficient
        val grounding = TrudyEnvironmentalGrounding(
            sources = emptyList(),
            latestMeasurementEpochMs = result.evidence.supportingEvidenceReferences
                .filter { it.domain == config.environmentDomain }
                .mapNotNull { it.timestampEpochMs }
                .maxOrNull(),
            freshness = TrudyEnvironmentalFreshness.UNKNOWN,
            dataQuality = null,
            measurementReferences = result.evidence.supportingEvidenceReferences
                .filter { it.domain == config.environmentDomain }
        )
        val text = when {
            coefficient == null -> "I don't have enough aligned environmental and ${target.displayName.lowercase()} measurements to see a useful pattern yet."
            abs(coefficient) < 0.15 -> "I can align the environmental and ${target.displayName.lowercase()} data, but there isn't much of a relationship in it so far."
            else -> {
                val outcome = if (coefficient > 0.0) target.higherOutcomePhrase else target.lowerOutcomePhrase
                val main = "$outcome on ${environmentalMetric.conditionPhrase(target.semantic)}".sentenceStart() + "."
                val confidence = if (result.confidence <= TrudyConfidence.LOW) " The signal is still weak with the data available." else ""
                val quality = if (result.dataQualityStatus != TrudyDataQualityStatus.GOOD) " The data coverage is limited, so the pattern is tentative." else ""
                val causal = " That doesn't show ${environmentalMetric.displayName.lowercase()} is responsible, but it's a useful pattern to keep watching."
                val symptom = if (target.semantic == TrudyEnvironmentalTargetSemantic.HEADACHE) " It can show timing patterns around headache reports, not why a headache occurred." else ""
                main + confidence + quality + causal + symptom
            }
        }
        return TrudyEnvironmentalSynthesis(text, result.evidence.supportingEvidenceReferences, grounding)
    }

    fun unavailableTarget(target: TrudyEnvironmentalTargetSemantic): String =
        "I have the environmental side of that question, but there isn't a domain-qualified ${target.userLabel()} metric wired into Trudy yet. I can't calculate that relationship without guessing."

    private fun grounding(
        measurements: List<TrudyMetricEvidence>,
        results: List<TrudyToolResult>
    ): TrudyEnvironmentalGrounding {
        val quality = results.filterIsInstance<TrudyToolResult.DataQuality>()
            .firstOrNull { it.operation.domain == config.environmentDomain }
            ?.evidence
            ?: measurements.firstNotNullOfOrNull { it.dataQuality }
        val latest = measurements.maxOfOrNull { it.timestampEpochMs }
        return TrudyEnvironmentalGrounding(
            sources = measurements.map { it.source.trim() }.filter { it.isNotBlank() }.distinct(),
            latestMeasurementEpochMs = latest,
            freshness = freshness(latest, quality),
            dataQuality = quality,
            measurementReferences = measurements.map { it.toEnvironmentalReference() }.distinct().take(48)
        )
    }

    private fun environmentalMeasurements(results: List<TrudyToolResult>): List<TrudyMetricEvidence> =
        results.flatMap { result ->
            when (result) {
                is TrudyToolResult.DomainState -> result.evidence
                is TrudyToolResult.MetricHistory -> result.evidence
                is TrudyToolResult.MetricWindow -> result.evidence
                is TrudyToolResult.DomainHistory -> result.evidence
                is TrudyToolResult.Context -> result.context.domains.flatMap { it.currentState + it.history }
                else -> emptyList()
            }
        }.filter { it.domain == config.environmentDomain }
            .distinctBy { Triple(it.metricId, it.timestampEpochMs, it.source) }

    private fun freshness(
        timestampEpochMs: Long?,
        quality: TrudyDataQualityEvidence?
    ): TrudyEnvironmentalFreshness {
        val timestamp = timestampEpochMs ?: return TrudyEnvironmentalFreshness.UNKNOWN
        val now = nowEpochMs()
        if (timestamp < 0L || now < 0L || timestamp > now + 300_000L) return TrudyEnvironmentalFreshness.UNKNOWN
        if (quality?.isStale == true) return TrudyEnvironmentalFreshness.STALE
        val ageHours = (now - timestamp).coerceAtLeast(0L) / 3_600_000.0
        return when {
            ageHours <= config.currentFreshnessHours -> TrudyEnvironmentalFreshness.CURRENT
            ageHours <= config.agingFreshnessHours -> TrudyEnvironmentalFreshness.AGING
            else -> TrudyEnvironmentalFreshness.STALE
        }
    }

    private fun provenance(grounding: TrudyEnvironmentalGrounding): String {
        val age = grounding.latestMeasurementEpochMs?.let(::ageText)
        val source = grounding.sources.takeIf { it.isNotEmpty() }?.take(3)?.joinToString()?.let { "Source: $it" }
        return listOfNotNull(age, source).joinToString(" ")
    }

    private fun ageText(timestampEpochMs: Long): String {
        val hours = (nowEpochMs() - timestampEpochMs).coerceAtLeast(0L) / 3_600_000.0
        return when {
            hours < 1.0 -> "Measured less than an hour ago"
            hours < 36.0 -> "Measured about ${hours.roundToInt()} hours ago"
            else -> "Measured about ${(hours / 24.0).roundToInt()} days ago"
        }
    }
}

private fun TrudyEnvironmentalMetricBinding.conditionPhrase(target: TrudyEnvironmentalTargetSemantic): String = when (semantic) {
    TrudyEnvironmentalMetricSemantic.AIR_TEMPERATURE -> if (target == TrudyEnvironmentalTargetSemantic.SLEEP) "hotter nights" else "hotter periods"
    TrudyEnvironmentalMetricSemantic.RELATIVE_HUMIDITY -> "more humid periods"
    TrudyEnvironmentalMetricSemantic.PRECIPITATION -> "rainier periods"
    TrudyEnvironmentalMetricSemantic.DAYLIGHT_DURATION -> "days with more daylight"
    TrudyEnvironmentalMetricSemantic.AIR_PRESSURE -> "higher-pressure periods"
    TrudyEnvironmentalMetricSemantic.WIND_SPEED -> "windier periods"
}

private fun TrudyEnvironmentalTargetSemantic.userLabel(): String = when (this) {
    TrudyEnvironmentalTargetSemantic.SLEEP -> "sleep"
    TrudyEnvironmentalTargetSemantic.FEELING -> "feeling"
    TrudyEnvironmentalTargetSemantic.EXERCISE -> "exercise"
    TrudyEnvironmentalTargetSemantic.MOOD -> "mood"
    TrudyEnvironmentalTargetSemantic.HEADACHE -> "headache"
}

private fun TrudyMetricEvidence.toEnvironmentalReference() = TrudyEvidenceReference(
    domain = domain,
    metricId = metricId,
    evidenceKind = evidenceKind,
    timestampEpochMs = timestampEpochMs
)

private fun formatValue(value: Double): String {
    if (!value.isFinite()) return "unavailable"
    val rounded = (value * 10.0).roundToInt() / 10.0
    return if (rounded == rounded.roundToInt().toDouble()) rounded.roundToInt().toString() else rounded.toString()
}

private fun unitSuffix(unit: String): String {
    val clean = unit.trim()
    return if (clean.isBlank()) "" else if (clean == "%" || clean.startsWith("°")) clean else " $clean"
}

