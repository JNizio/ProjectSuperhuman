package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlin.math.abs
import kotlin.math.round

/**
 * Final presentation policy for broad recent-health overviews.
 *
 * This deliberately consumes the already-bounded, already-computed investigation result. It does
 * not retrieve data, calculate correlations, diagnose conditions, or introduce population norms.
 */
data class TrudyRecentOverviewBriefing(
    val answerText: String,
    val selectedMetrics: Set<Pair<HealthDomain, String>>,
    val stableWithUsableData: Boolean
)

object TrudyRecentOverviewAnswerQuality {
    fun compose(
        plan: TrudyAnswerPlan,
        investigation: TrudyInvestigationResult
    ): TrudyRecentOverviewBriefing? {
        if (plan.safetyLevel != TrudyAnswerSafetyLevel.NONE) return null
        if (investigation.target.label != RECENT_OVERVIEW_LABEL) return null

        val findings = investigation.importantFindings
            .asSequence()
            .filter { it.classification == TrudyFindingClassification.OBSERVED_CHANGE }
            .filter { it.quality.status != TrudyDataQualityStatus.INSUFFICIENT }
            .filterNot { it.quality.stale }
            .distinctBy { it.domain }
            .sortedByDescending { it.priorityScore }
            .take(MAX_HEADLINES)
            .toList()

        val usableComparisons = plan.allEvidence.filter { evidence ->
            evidence.kind == TrudyAnswerEvidenceKind.TREND &&
                evidence.domain != null && evidence.metricId != null &&
                evidence.meanValue != null && evidence.baselineMean != null && evidence.delta != null &&
                evidence.sampleCount >= MIN_PERIOD_SAMPLES &&
                evidence.comparisonSampleCount >= MIN_PERIOD_SAMPLES &&
                evidence.classification !in setOf(
                    TrudyAnswerEvidenceClass.MISSING,
                    TrudyAnswerEvidenceClass.STALE,
                    TrudyAnswerEvidenceClass.LOW_QUALITY,
                    TrudyAnswerEvidenceClass.IRRELEVANT
                )
        }
        val stableWithUsableData = findings.isEmpty() && usableComparisons.size >= MIN_STABLE_SIGNALS

        val selectedKeys = findings.map { it.domain to it.metricId }.toSet()
        val answer = when {
            findings.isNotEmpty() -> findingsAnswer(plan, findings)
            stableWithUsableData -> stableAnswer(plan, usableComparisons)
            else -> insufficientAnswer(investigation)
        }

        return TrudyRecentOverviewBriefing(
            answerText = answer,
            selectedMetrics = selectedKeys,
            stableWithUsableData = stableWithUsableData
        )
    }

    private fun findingsAnswer(
        plan: TrudyAnswerPlan,
        findings: List<TrudyInvestigationFinding>
    ): String {
        val sentences = findings.mapIndexed { index, finding ->
            val evidence = plan.allEvidence.firstOrNull {
                it.kind == TrudyAnswerEvidenceKind.TREND &&
                    it.domain == finding.domain && it.metricId == finding.metricId
            }
            findingSentence(finding, evidence, index == 0)
        }.toMutableList()

        limitationSentence(plan)?.let(sentences::add)
        return sentences.joinToString(" ").normalizeOverviewText()
    }

    private fun findingSentence(
        finding: TrudyInvestigationFinding,
        evidence: TrudyAnswerEvidence?,
        first: Boolean
    ): String {
        val subject = naturalSubject(finding.domain, finding.metricId)
        val recent = evidence?.meanValue ?: finding.recentMean
        val baseline = evidence?.baselineMean ?: finding.baselineMean
        val unit = evidence?.unit.orEmpty()
        val direction = if (finding.absoluteDelta >= 0.0) "up" else "down"
        val comparison = if (recent.isFinite() && baseline.isFinite()) {
            "averaged ${formatOverviewValue(recent, unit)}, $direction from ${formatOverviewValue(baseline, unit)} in the previous period"
        } else {
            "was ${if (finding.absoluteDelta >= 0.0) "higher" else "lower"} than the previous period"
        }
        return if (first) {
            "$subject is the clearest recent change — it $comparison."
        } else {
            "$subject also $comparison."
        }
    }

    private fun stableAnswer(
        plan: TrudyAnswerPlan,
        usableComparisons: List<TrudyAnswerEvidence>
    ): String {
        val labels = usableComparisons
            .distinctBy { it.domain }
            .take(3)
            .map { naturalSubject(it.domain, it.metricId).removePrefix("Your ").lowercaseFirstChar() }
        val checked = when (labels.size) {
            0 -> "the signals I could assess"
            1 -> labels.first()
            2 -> "${labels[0]} and ${labels[1]}"
            else -> "${labels[0]}, ${labels[1]} and the other signals I could assess"
        }
        val sentences = mutableListOf(
            "Nothing major stands out ${naturalTimeframe(plan.timeframeLabel)}. $checked stayed fairly close to your recent baseline."
        )
        limitationSentence(plan)?.let(sentences::add)
        return sentences.joinToString(" ").normalizeOverviewText()
    }

    private fun insufficientAnswer(investigation: TrudyInvestigationResult): String {
        val missing = investigation.missingEvidence
            .filter { it.reason in setOf(TrudyEvidenceGapReason.NO_DATA, TrudyEvidenceGapReason.SPARSE_DATA, TrudyEvidenceGapReason.STALE_DATA, TrudyEvidenceGapReason.NOT_CONNECTED) }
            .map { naturalSubject(it.domain, it.metricId).removePrefix("Your ").lowercaseFirstChar() }
            .distinct()
            .take(2)
        return if (missing.isEmpty()) {
            "I don't have enough reliable recent data to tell what has meaningfully changed yet."
        } else {
            "I don't have enough reliable recent data to give you a useful overall change summary yet. I couldn't assess ${missing.joinToString(" or ")} well enough for this period."
        }
    }

    private fun limitationSentence(plan: TrudyAnswerPlan): String? {
        val missing = plan.limitations
            .asSequence()
            .filter { it.classification in setOf(TrudyAnswerEvidenceClass.MISSING, TrudyAnswerEvidenceClass.STALE, TrudyAnswerEvidenceClass.LOW_QUALITY) }
            .filter { it.domain != null || it.metricId != null }
            .map { naturalSubject(it.domain, it.metricId).removePrefix("Your ").lowercaseFirstChar() }
            .distinct()
            .take(2)
            .toList()
        if (missing.isEmpty()) return null
        return "I couldn't assess ${missing.joinToString(" or ")} as confidently because the recent data there is limited."
    }

    fun shouldKeepReference(
        briefing: TrudyRecentOverviewBriefing,
        reference: TrudyEvidenceReference
    ): Boolean {
        if (briefing.stableWithUsableData) return true
        val metric = reference.metricId ?: return false
        return briefing.selectedMetrics.any { (domain, metricId) ->
            reference.domain == domain && metric == metricId
        }
    }

    private fun naturalSubject(domain: HealthDomain?, metricId: String?): String = when {
        domain == HealthDomain.SLEEP && metricId == "sleep_score" -> "Your sleep"
        metricId == "resting_heart_rate_bpm" -> "Your resting heart rate"
        metricId == "water_total_l" || metricId == "water_intake_ml" -> "Your hydration"
        metricId == "emotional_valence" -> "Your mood"
        metricId == "emotional_energy" -> "Your energy"
        metricId == "body_weight_kg" -> "Your weight"
        metricId == "environment_temperature_c" -> "Outdoor temperature"
        metricId == "environment_relative_humidity_pct" -> "Humidity"
        metricId == "food_kcal" -> "Your calorie intake"
        metricId == "mindfulness_session_minutes" -> "Your mindfulness time"
        metricId != null -> "Your ${humanMetricLabel(metricId).lowercaseFirstChar()}"
        domain != null -> "Your ${domain.name.lowercase().replace('_', ' ')} data"
        else -> "That signal"
    }

    private fun formatOverviewValue(value: Double, unit: String): String {
        val rounded = if (abs(value) >= 100.0) round(value).toLong().toString() else {
            val oneDecimal = round(value * 10.0) / 10.0
            if (oneDecimal == round(oneDecimal)) round(oneDecimal).toLong().toString() else oneDecimal.toString()
        }
        val cleanUnit = when (unit.trim().lowercase()) {
            "bpm" -> " bpm"
            "kg" -> " kg"
            "l", "litre", "liter", "litres", "liters" -> " L"
            "ml" -> " mL"
            "kcal" -> " kcal"
            "min", "mins", "minute", "minutes" -> " min"
            "%", "percent" -> "%"
            "c", "°c" -> "°C"
            else -> unit.trim().takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        }
        return rounded + cleanUnit
    }

    private fun naturalTimeframe(label: String): String = when (label.trim().lowercase()) {
        "recently" -> "recently"
        "this week" -> "this week"
        "last week" -> "last week"
        "this month" -> "this month"
        "the last few weeks" -> "over the last few weeks"
        "the last 7 days" -> "over the last 7 days"
        else -> "in ${label.trim().ifBlank { "the recent period" }}"
    }

    private fun String.lowercaseFirstChar(): String =
        replaceFirstChar { if (it.isUpperCase()) it.lowercase() else it.toString() }

    private fun String.normalizeOverviewText(): String =
        replace(Regex("\\s+"), " ").trim()

    private const val RECENT_OVERVIEW_LABEL = "recent health overview"
    private const val MAX_HEADLINES = 3
    private const val MIN_PERIOD_SAMPLES = 3
    private const val MIN_STABLE_SIGNALS = 2
}
