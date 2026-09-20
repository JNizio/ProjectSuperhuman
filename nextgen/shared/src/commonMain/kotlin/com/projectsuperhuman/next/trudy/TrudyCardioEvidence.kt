package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthValue
import kotlin.math.round

/**
 * Factual cross-module assembly for Cardio. It intentionally does not calculate physiology:
 * analytical engines publish those metrics and this layer only groups canonical evidence.
 */
data class TrudyCardioPeriodSummary(
    val id: String,
    val title: String,
    val range: TrudyTimeRange,
    val sessions: Int,
    val minutes: Double,
    val distanceKm: Double?,
    val zone2Minutes: Double?,
    val meanHeartRateCoveragePct: Double?,
    val fitnessEfficiencyDeltaPct: Double?,
    val readinessScore: Double?,
    val acuteTrainingLoad: Double?,
    val chronicTrainingLoad: Double?,
    val trainingStressBalance: Double?,
    val dataGaps: List<String>,
    val evidenceMetricIds: List<String>,
    val dataQuality: TrudyDataQualityEvidence?
) {
    fun toInsightEvidence(): TrudyInsightEvidence = TrudyInsightEvidence(
        id = id,
        domain = com.projectsuperhuman.next.core.HealthDomain.EXERCISE,
        evidenceKind = TrudyEvidenceKind.DERIVED_PERSONAL_TREND,
        title = title,
        explanation = buildString {
            append(sessions).append(" sessions · ").append(minutes.compact()).append(" min")
            distanceKm?.let { append(" · ").append(it.compact()).append(" km") }
            zone2Minutes?.let { append(" · Zone 2 ").append(it.compact()).append(" min") }
            fitnessEfficiencyDeltaPct?.let { append(" · fitness efficiency ").append(it.signedCompact()).append("%") }
            readinessScore?.let { append(" · readiness ").append(it.compact()) }
            if (dataGaps.isNotEmpty()) {
                append(". Data gaps: ").append(dataGaps.joinToString("; "))
            }
        },
        evidenceMetricIds = evidenceMetricIds,
        confidence = dataQuality?.score?.coerceIn(0, 100)?.div(100.0),
        source = "cardio-cross-module-summary-v1",
        dataQuality = dataQuality
    )
}

object TrudyCardioEvidenceAssembler {
    private val derivedMetricIds = setOf(
        "cardio_fitness_efficiency_delta_pct",
        "cardio_training_readiness_score",
        "cardio_acute_training_load",
        "cardio_chronic_training_load",
        "cardio_training_stress_balance"
    )

    fun summarise(
        id: String,
        title: String,
        range: TrudyTimeRange,
        rows: List<HealthValue>,
        dataQuality: TrudyDataQualityEvidence?
    ): TrudyCardioPeriodSummary? {
        val inWindow = rows.filter { it.timestampEpochMs in range.fromEpochMs..range.toEpochMs }
        val sessions = inWindow.filter { it.metric == "cardio_session" }
        val derived = inWindow.filter { it.metric in derivedMetricIds }
        if (sessions.isEmpty() && derived.isEmpty()) return null

        val distances = sessions.mapNotNull { it.metadata["distanceKm"]?.toDoubleOrNull()?.takeIf { value -> value.isFinite() } }
        val zone2Seconds = sessions.mapNotNull { it.metadata["zone2Seconds"]?.toDoubleOrNull()?.takeIf(Double::isFinite) }
        val hrCoverage = sessions.mapNotNull {
            (it.metadata["heartRateCoveragePct"] ?: it.metadata["ext.heartRateCoveragePct"])
                ?.toDoubleOrNull()
                ?.takeIf { value -> value.isFinite() }
        }

        fun latest(metric: String): Double? = derived
            .asSequence()
            .filter { it.metric == metric && it.value.isFinite() }
            .maxByOrNull { it.timestampEpochMs }
            ?.value

        val gaps = buildList {
            if (sessions.isEmpty()) add("no completed Cardio session in this period")
            if (sessions.isNotEmpty() && distances.size < sessions.size) {
                add("distance missing for ${sessions.size - distances.size}/${sessions.size} sessions")
            }
            if (sessions.isNotEmpty() && zone2Seconds.isEmpty()) add("Zone 2 duration unavailable")
            if (sessions.isNotEmpty() && hrCoverage.size < sessions.size) {
                add("heart-rate coverage missing for ${sessions.size - hrCoverage.size}/${sessions.size} sessions")
            }
            if (latest("cardio_fitness_efficiency_delta_pct") == null) add("fitness-efficiency baseline unavailable")
        }

        val metricIds = inWindow.map { it.metric }
            .filter { it == "cardio_session" || it in derivedMetricIds }
            .distinct()

        return TrudyCardioPeriodSummary(
            id = id,
            title = title,
            range = range,
            sessions = sessions.size,
            minutes = sessions.sumOf { it.value.takeIf(Double::isFinite)?.coerceAtLeast(0.0) ?: 0.0 },
            distanceKm = distances.takeIf { it.isNotEmpty() }?.sum(),
            zone2Minutes = zone2Seconds.takeIf { it.isNotEmpty() }?.sum()?.div(60.0),
            meanHeartRateCoveragePct = hrCoverage.takeIf { it.isNotEmpty() }?.average(),
            fitnessEfficiencyDeltaPct = latest("cardio_fitness_efficiency_delta_pct"),
            readinessScore = latest("cardio_training_readiness_score"),
            acuteTrainingLoad = latest("cardio_acute_training_load"),
            chronicTrainingLoad = latest("cardio_chronic_training_load"),
            trainingStressBalance = latest("cardio_training_stress_balance"),
            dataGaps = gaps,
            evidenceMetricIds = metricIds,
            dataQuality = dataQuality
        )
    }
}

private fun Double.compact(): String {
    val rounded = round(this * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}

private fun Double.signedCompact(): String = (if (this > 0.0) "+" else "") + compact()
