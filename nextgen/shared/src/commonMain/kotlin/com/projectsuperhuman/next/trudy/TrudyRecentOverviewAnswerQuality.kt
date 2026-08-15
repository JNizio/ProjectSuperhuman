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
    val stableWithUsableData: Boolean,
    val assessedMetricCount: Int,
    val requestedMetricCount: Int
)

object TrudyRecentOverviewAnswerQuality {
    /**
     * Canonicalise natural broad-overview wording only when the user did not name a specific health
     * domain. This keeps focused questions focused while allowing phrases such as "give me an
     * overview" or "what stands out lately?" to reach the existing bounded overview planner.
     */
    fun planningText(userText: String): String {
        val clean = userText.trim()
        if (clean.isEmpty()) return userText
        val text = clean.lowercase()
        if (TrudySystemCatalog.modulesMentioned(text).isNotEmpty()) return userText
        if (PLANNER_NATIVE_OVERVIEW_PHRASES.any { it in text }) return userText
        if (BROAD_OVERVIEW_ALIASES.none { it in text }) return userText
        return "$clean What should I know?"
    }

    fun compose(
        plan: TrudyAnswerPlan,
        investigation: TrudyInvestigationResult
    ): TrudyRecentOverviewBriefing? {
        if (plan.safetyLevel != TrudyAnswerSafetyLevel.NONE) return null
        if (investigation.target.label != RECENT_OVERVIEW_LABEL) return null

        val trendEvidence = plan.allEvidence.filter { evidence ->
            evidence.kind == TrudyAnswerEvidenceKind.TREND &&
                evidence.domain != null && evidence.metricId != null
        }
        val plannedKeys = trendEvidence.mapNotNull(::evidenceKey).toSet()
        val blockingGaps = investigation.missingEvidence
            .mapNotNull { gap ->
                val domain = gap.domain ?: return@mapNotNull null
                val metricId = gap.metricId ?: return@mapNotNull null
                if (gap.reason !in BLOCKING_HEADLINE_GAPS) return@mapNotNull null
                (domain to metricId) to gap.reason
            }.toMap()

        val findings = investigation.importantFindings
            .asSequence()
            .filter { it.classification == TrudyFindingClassification.OBSERVED_CHANGE }
            .mapNotNull { finding ->
                val evidence = trendEvidence.firstOrNull {
                    it.domain == finding.domain && it.metricId == finding.metricId
                } ?: return@mapNotNull null
                if (!isReportableHeadline(finding, evidence, blockingGaps)) return@mapNotNull null
                finding to evidence
            }
            .sortedByDescending { (finding, _) -> finding.priorityScore }
            .distinctBy { (finding, _) -> finding.domain }
            .take(MAX_HEADLINES)
            .toList()

        val usableComparisons = trendEvidence
            .asSequence()
            .filter(::isUsableComparison)
            .filter { evidence -> evidenceKey(evidence) !in blockingGaps.keys }
            .distinctBy(::evidenceKey)
            .toList()
        val assessedMetricCount = usableComparisons.mapNotNull(::evidenceKey).distinct().size
        val requestedMetricCount = investigation.execution.requestedMetricCount.coerceAtLeast(1)
        val coverage = assessedMetricCount.toDouble() / requestedMetricCount.toDouble()
        val stableWithUsableData = findings.isEmpty() &&
            assessedMetricCount >= MIN_STABLE_SIGNALS && coverage >= MIN_STABLE_COVERAGE

        val selectedKeys = when {
            findings.isNotEmpty() -> findings.map { (finding, _) -> finding.domain to finding.metricId }.toSet()
            stableWithUsableData -> usableComparisons
                .distinctBy { it.domain }
                .take(MAX_STABLE_EVIDENCE_METRICS)
                .mapNotNull(::evidenceKey)
                .toSet()
            else -> emptySet()
        }

        val answer = when {
            findings.isNotEmpty() -> findingsAnswer(plan, findings, usableComparisons, plannedKeys)
            stableWithUsableData -> stableAnswer(plan, usableComparisons, plannedKeys)
            else -> insufficientAnswer(plan, usableComparisons, investigation, plannedKeys)
        }

        return TrudyRecentOverviewBriefing(
            answerText = answer,
            selectedMetrics = selectedKeys,
            stableWithUsableData = stableWithUsableData,
            assessedMetricCount = assessedMetricCount,
            requestedMetricCount = requestedMetricCount
        )
    }

    private fun isReportableHeadline(
        finding: TrudyInvestigationFinding,
        evidence: TrudyAnswerEvidence,
        blockingGaps: Map<Pair<HealthDomain, String>, TrudyEvidenceGapReason>
    ): Boolean {
        val key = finding.domain to finding.metricId
        if (key in blockingGaps) return false
        if (finding.quality.stale) return false
        if (finding.quality.status !in setOf(TrudyDataQualityStatus.GOOD, TrudyDataQualityStatus.LIMITED)) return false
        if (evidence.classification !in setOf(TrudyAnswerEvidenceClass.USABLE, TrudyAnswerEvidenceClass.SUPPORTING)) return false
        if (evidence.sampleCount < MIN_PERIOD_SAMPLES || evidence.comparisonSampleCount < MIN_PERIOD_SAMPLES) return false
        if (finding.priorityScore < MIN_HEADLINE_PRIORITY) return false

        // Celsius is an interval scale: percentage change around an arbitrary zero is not useful.
        // Require a visible absolute shift as well as a non-trivial standardized effect instead.
        if (finding.metricId in INTERVAL_TEMPERATURE_METRICS) {
            return abs(finding.absoluteDelta) >= MIN_TEMPERATURE_HEADLINE_DELTA_C &&
                abs(finding.standardizedEffect ?: 0.0) >= MIN_STANDARDIZED_HEADLINE_EFFECT
        }
        return true
    }

    private fun isUsableComparison(evidence: TrudyAnswerEvidence): Boolean =
        evidence.meanValue != null && evidence.baselineMean != null && evidence.delta != null &&
            evidence.sampleCount >= MIN_PERIOD_SAMPLES &&
            evidence.comparisonSampleCount >= MIN_PERIOD_SAMPLES &&
            evidence.classification in setOf(TrudyAnswerEvidenceClass.USABLE, TrudyAnswerEvidenceClass.SUPPORTING)

    private fun findingsAnswer(
        plan: TrudyAnswerPlan,
        findings: List<Pair<TrudyInvestigationFinding, TrudyAnswerEvidence>>,
        usableComparisons: List<TrudyAnswerEvidence>,
        plannedKeys: Set<Pair<HealthDomain, String>>
    ): String {
        val sentences = findings.mapIndexed { index, (finding, evidence) ->
            findingSentence(plan.timeframeLabel, finding, evidence, index == 0)
        }.toMutableList()

        val selected = findings.map { (finding, _) -> finding.domain to finding.metricId }.toSet()
        val otherUsable = usableComparisons.filter { evidenceKey(it) !in selected }
        if (otherUsable.size >= 2) {
            sentences += "The other signals I could assess were closer to their recent baselines."
        }
        limitationSentence(plan, plannedKeys)?.let(sentences::add)
        return sentences.joinToString(" ").normalizeOverviewText()
    }

    private fun findingSentence(
        timeframeLabel: String,
        finding: TrudyInvestigationFinding,
        evidence: TrudyAnswerEvidence,
        first: Boolean
    ): String {
        val recent = evidence.meanValue ?: finding.recentMean
        val baseline = evidence.baselineMean ?: finding.baselineMean
        val range = formatChangeRange(baseline, recent, evidence.unit)
        val verb = if (finding.absoluteDelta >= 0.0) "rose" else "fell"
        val leadTime = naturalTimeframe(timeframeLabel)

        return when (finding.metricId) {
            "sleep_score" -> if (first) {
                "Sleep is the clearest ${changeTimePhrase(leadTime)}: your average sleep score $verb $range."
            } else {
                "Your average sleep score also $verb $range."
            }
            "resting_heart_rate_bpm" -> if (first) {
                "Resting heart rate is the clearest ${changeTimePhrase(leadTime)}: it $verb $range."
            } else {
                "Your resting heart rate also $verb $range."
            }
            "water_total_l", "water_intake_ml" -> if (first) {
                "Hydration is the clearest ${changeTimePhrase(leadTime)}: your recorded average $verb $range."
            } else {
                "Your recorded hydration average also $verb $range."
            }
            "emotional_valence" -> if (first) {
                "Mood is the clearest ${changeTimePhrase(leadTime)}: your average mood score $verb $range."
            } else {
                "Your average mood score also $verb $range."
            }
            "body_weight_kg" -> if (first) {
                "Weight is the clearest ${changeTimePhrase(leadTime)}: your average $verb $range."
            } else {
                "Your average weight also $verb $range."
            }
            "environment_temperature_c" -> if (first) {
                "Outdoor temperature is the clearest ${changeTimePhrase(leadTime)}: the average $verb $range."
            } else {
                "Average outdoor temperature also $verb $range."
            }
            "food_kcal" -> if (first) {
                "Calorie intake is the clearest ${changeTimePhrase(leadTime)}: your recorded average $verb $range."
            } else {
                "Your recorded calorie intake also $verb $range."
            }
            "mindfulness_session_minutes" -> if (first) {
                "Mindfulness time is the clearest ${changeTimePhrase(leadTime)}: your recorded average $verb $range."
            } else {
                "Your recorded mindfulness time also $verb $range."
            }
            else -> {
                val subject = naturalSubject(finding.domain, finding.metricId)
                if (first) "$subject is the clearest ${changeTimePhrase(leadTime)}: it $verb $range."
                else "$subject also $verb $range."
            }
        }
    }

    private fun stableAnswer(
        plan: TrudyAnswerPlan,
        usableComparisons: List<TrudyAnswerEvidence>,
        plannedKeys: Set<Pair<HealthDomain, String>>
    ): String {
        val labels = usableComparisons
            .distinctBy { it.domain }
            .take(MAX_STABLE_EVIDENCE_METRICS)
            .map { naturalSubject(it.domain, it.metricId).removePrefix("Your ").lowercaseFirst() }
        val checked = joinNaturally(labels)
        val sentences = mutableListOf(
            "Nothing major stands out ${naturalTimeframe(plan.timeframeLabel)}. ${checked.replaceFirstChar { it.uppercase() }} ${if (labels.size == 1) "is" else "are"} fairly close to ${if (labels.size == 1) "its" else "their"} recent baseline${if (labels.size == 1) "" else "s"}."
        )
        limitationSentence(plan, plannedKeys)?.let(sentences::add)
        return sentences.joinToString(" ").normalizeOverviewText()
    }

    private fun insufficientAnswer(
        plan: TrudyAnswerPlan,
        usableComparisons: List<TrudyAnswerEvidence>,
        investigation: TrudyInvestigationResult,
        plannedKeys: Set<Pair<HealthDomain, String>>
    ): String {
        val usableLabels = usableComparisons
            .distinctBy { it.domain }
            .take(2)
            .map { naturalSubject(it.domain, it.metricId).removePrefix("Your ").lowercaseFirst() }
        val missingLabels = overviewLimitations(plan, plannedKeys)
            .map { naturalSubject(it.domain, it.metricId).removePrefix("Your ").lowercaseFirst() }
            .distinct()
            .take(2)

        return when {
            usableLabels.isNotEmpty() -> buildString {
                append("I can compare ").append(joinNaturally(usableLabels))
                append(", but too much of the rest is missing, sparse or stale for a reliable overall summary ")
                append(naturalTimeframe(plan.timeframeLabel)).append('.')
                if (missingLabels.isNotEmpty()) {
                    append(" I couldn't assess ").append(joinNaturally(missingLabels)).append(" well enough for this period.")
                }
            }
            missingLabels.isNotEmpty() ->
                "I don't have enough reliable recent data to give you a useful overall change summary yet. I couldn't assess ${joinNaturally(missingLabels)} well enough for this period."
            investigation.quality.status == TrudyDataQualityStatus.STALE ->
                "The available health data is too old to give you a reliable recent overview."
            else -> "I don't have enough reliable recent data to tell what has meaningfully changed yet."
        }.normalizeOverviewText()
    }

    private fun limitationSentence(
        plan: TrudyAnswerPlan,
        plannedKeys: Set<Pair<HealthDomain, String>>
    ): String? {
        val limitations = overviewLimitations(plan, plannedKeys)
        if (limitations.isEmpty()) return null
        val labels = limitations
            .map { naturalSubject(it.domain, it.metricId).removePrefix("Your ").lowercaseFirst() }
            .distinct()
        val shown = labels.take(2)
        val extra = labels.size - shown.size
        return buildString {
            append("I couldn't assess ").append(joinNaturally(shown))
            append(" as confidently because the recent data there is limited.")
            if (extra > 0) append(" ${if (extra == 1) "One other area was" else "$extra other areas were"} also too sparse or stale to rely on.")
        }
    }

    private fun overviewLimitations(
        plan: TrudyAnswerPlan,
        plannedKeys: Set<Pair<HealthDomain, String>>
    ): List<TrudyAnswerEvidence> = plan.limitations
        .asSequence()
        .filter { it.classification in setOf(TrudyAnswerEvidenceClass.MISSING, TrudyAnswerEvidenceClass.STALE, TrudyAnswerEvidenceClass.LOW_QUALITY) }
        .filter { evidence -> evidenceKey(evidence)?.let { it in plannedKeys } == true }
        .distinctBy(::evidenceKey)
        .toList()

    fun shouldKeepReference(
        briefing: TrudyRecentOverviewBriefing,
        reference: TrudyEvidenceReference
    ): Boolean {
        val metric = reference.metricId ?: return false
        return briefing.selectedMetrics.any { (domain, metricId) ->
            reference.domain == domain && metric == metricId
        }
    }

    private fun evidenceKey(evidence: TrudyAnswerEvidence): Pair<HealthDomain, String>? {
        val domain = evidence.domain ?: return null
        val metricId = evidence.metricId ?: return null
        return domain to metricId
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
        metricId != null -> "Your ${humanMetricLabel(metricId).lowercaseFirst()}"
        domain != null -> "Your ${domain.name.lowercase().replace('_', ' ')} data"
        else -> "That signal"
    }

    private fun formatChangeRange(baseline: Double, recent: Double, unit: String): String =
        "from ${formatOverviewNumber(baseline)} to ${formatOverviewValue(recent, unit)}"

    private fun formatOverviewValue(value: Double, unit: String): String =
        formatOverviewNumber(value) + unitSuffix(unit)

    private fun formatOverviewNumber(value: Double): String = if (abs(value) >= 100.0) {
        round(value).toLong().toString()
    } else {
        val oneDecimal = round(value * 10.0) / 10.0
        if (oneDecimal == round(oneDecimal)) round(oneDecimal).toLong().toString() else oneDecimal.toString()
    }

    private fun unitSuffix(unit: String): String = when (unit.trim().lowercase()) {
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

    private fun naturalTimeframe(label: String): String = when (label.trim().lowercase()) {
        "recently" -> "recently"
        "this week" -> "this week"
        "last week" -> "last week"
        "this month" -> "this month"
        "last month" -> "last month"
        "the last few weeks" -> "over the last few weeks"
        "the last 7 days" -> "over the last 7 days"
        else -> "in ${label.trim().ifBlank { "the recent period" }}"
    }

    private fun changeTimePhrase(naturalTimeframe: String): String =
        if (naturalTimeframe == "recently") "recent change" else "change $naturalTimeframe"

    private fun joinNaturally(values: List<String>): String = when (values.size) {
        0 -> "the signals I could assess"
        1 -> values[0]
        2 -> "${values[0]} and ${values[1]}"
        else -> values.dropLast(1).joinToString(", ") + " and " + values.last()
    }

    private fun String.lowercaseFirst(): String = replaceFirstChar { if (it.isUpperCase()) it.lowercase() else it.toString() }

    private fun String.normalizeOverviewText(): String =
        replace(Regex("\\s+"), " ").trim()

    private const val RECENT_OVERVIEW_LABEL = "recent health overview"
    private const val MAX_HEADLINES = 3
    private const val MAX_STABLE_EVIDENCE_METRICS = 3
    private const val MIN_PERIOD_SAMPLES = 3
    private const val MIN_STABLE_SIGNALS = 3
    private const val MIN_STABLE_COVERAGE = 0.50
    private const val MIN_HEADLINE_PRIORITY = 0.08
    private const val MIN_TEMPERATURE_HEADLINE_DELTA_C = 1.0
    private const val MIN_STANDARDIZED_HEADLINE_EFFECT = 0.50

    private val BLOCKING_HEADLINE_GAPS = setOf(
        TrudyEvidenceGapReason.NO_DATA,
        TrudyEvidenceGapReason.SPARSE_DATA,
        TrudyEvidenceGapReason.STALE_DATA,
        TrudyEvidenceGapReason.LOW_VARIANCE,
        TrudyEvidenceGapReason.NOT_CONNECTED
    )
    private val INTERVAL_TEMPERATURE_METRICS = setOf(
        "environment_temperature_c",
        "body_temperature_celsius"
    )
    private val PLANNER_NATIVE_OVERVIEW_PHRASES = listOf(
        "what should i know",
        "anything important",
        "anything unusual",
        "what's changed",
        "what has changed",
        "what changed",
        "what's been happening",
        "what has been happening"
    )
    private val BROAD_OVERVIEW_ALIASES = listOf(
        "give me an overview",
        "health overview",
        "health summary",
        "overall health",
        "how am i doing overall",
        "how have i been doing overall",
        "anything i should know",
        "anything worth knowing",
        "what stands out",
        "what's new with my health",
        "what is new with my health",
        "how have things been lately"
    )
}
