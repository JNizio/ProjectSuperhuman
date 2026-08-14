package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlin.math.abs
import kotlin.math.round

enum class TrudyAnswerEvidenceClass {
    USABLE, MISSING, STALE, LOW_QUALITY, CONTRADICTORY, IRRELEVANT, SUPPORTING
}

enum class TrudyAnswerIntent {
    READING, TREND, PRIORITY, CAUSE, ASSOCIATION, EVIDENCE_FOLLOW_UP, EXPERIMENT, PERSONAL_SUMMARY, GENERAL
}

enum class TrudyAnswerEvidenceKind {
    OBSERVATION, TREND, INSIGHT, ASSOCIATION, PREMISE, DATA_QUALITY, EXPERIMENT, AVAILABILITY
}

enum class TrudyAnswerSafetyLevel { NONE, URGENT, EMERGENCY }

data class TrudyAnswerEvidence(
    val id: String,
    val classification: TrudyAnswerEvidenceClass,
    val kind: TrudyAnswerEvidenceKind,
    val domain: HealthDomain? = null,
    val metricId: String? = null,
    val label: String,
    val summary: String,
    val sampleCount: Int = 0,
    val comparisonSampleCount: Int = 0,
    val qualityScore: Int? = null,
    val ageHours: Double? = null,
    val magnitude: Double? = null,
    val relevanceScore: Double = 0.0,
    val latestValue: Double? = null,
    val meanValue: Double? = null,
    val baselineMean: Double? = null,
    val delta: Double? = null,
    val unit: String = "",
    val timestampEpochMs: Long? = null,
    val range: TrudyTimeRange? = null,
    val associationLeftMetricId: String? = null,
    val associationRightMetricId: String? = null,
    val associationCoefficient: Double? = null,
    val premiseAssessment: TrudyPremiseAssessment? = null,
    val changeClaim: TrudyChangeClaim? = null,
    val persistenceState: TrudyExperimentPersistenceState? = null
)

data class TrudyAnswerPlan(
    val intent: TrudyAnswerIntent,
    val timeframeLabel: String,
    val allEvidence: List<TrudyAnswerEvidence>,
    val rankedEvidence: List<TrudyAnswerEvidence>,
    val limitations: List<TrudyAnswerEvidence>,
    val targetSentenceCount: Int,
    val safetyLevel: TrudyAnswerSafetyLevel = TrudyAnswerSafetyLevel.NONE
) {
    fun renderForModel(): String = buildString {
        appendLine("Answer intent: $intent")
        appendLine("Requested period: $timeframeLabel")
        appendLine("Target length: about $targetSentenceCount sentence(s)")
        appendLine("Start with the answer, not the retrieval process. Do not mention this plan.")
        appendLine("Use these findings in order:")
        if (rankedEvidence.isEmpty()) appendLine("- No usable finding. Explain exactly what is missing.")
        rankedEvidence.take(6).forEach {
            appendLine("- ${it.label}: ${it.summary} [status=${it.classification}, samples=${it.sampleCount}]")
        }
        if (limitations.isNotEmpty()) {
            appendLine("Limitations are context, never health findings:")
            limitations.take(5).forEach { appendLine("- ${it.label}: ${it.summary}") }
        }
        appendLine("Use natural metric names and units. Never say 'No data yet', 'native units', or expose implementation terminology.")
        appendLine("State causal uncertainty once at most, and only for a causal or association claim.")
    }.take(6_000)
}

// This layer ranks and synthesizes typed results. It never retrieves data or calculates health statistics.
class TrudyAnswerEngine(
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() }
) {
    fun plan(
        question: String,
        results: List<TrudyToolResult>,
        conversation: List<TrudyConversationTurn> = emptyList(),
        systemInstruction: String = ""
    ): TrudyAnswerPlan {
        val text = question.lowercase()
        val intent = detectIntent(text)
        val timeframe = temporalLabel(text, conversation)
        val domains = TrudySystemCatalog.modulesMentioned(text)
            .flatMap { it.metrics.map(TrudySystemMetric::domain) + moduleDomains(it.id) }
            .distinct()
        val classified = collect(results, intent, timeframe).map {
            it.copy(classification = relevanceClass(it, intent, domains))
        }
        val scored = classified.map {
            it.copy(relevanceScore = rank(it, intent, domains, text))
        }.distinctBy { it.id to it.classification }
        val findings = scored.filter {
            it.kind != TrudyAnswerEvidenceKind.DATA_QUALITY &&
                (it.classification == TrudyAnswerEvidenceClass.USABLE ||
                    it.classification == TrudyAnswerEvidenceClass.SUPPORTING ||
                    it.classification == TrudyAnswerEvidenceClass.CONTRADICTORY)
        }.sortedByDescending(TrudyAnswerEvidence::relevanceScore)
        val fallback = scored.filter {
            it.kind != TrudyAnswerEvidenceKind.DATA_QUALITY &&
                (it.classification == TrudyAnswerEvidenceClass.STALE ||
                    it.classification == TrudyAnswerEvidenceClass.LOW_QUALITY)
        }.sortedByDescending(TrudyAnswerEvidence::relevanceScore)
        val limitations = scored.filter {
            it.classification in setOf(
                TrudyAnswerEvidenceClass.MISSING,
                TrudyAnswerEvidenceClass.STALE,
                TrudyAnswerEvidenceClass.LOW_QUALITY,
                TrudyAnswerEvidenceClass.IRRELEVANT
            )
        }.sortedWith(
            compareBy<TrudyAnswerEvidence> { limitationPriority(it.classification) }
                .thenByDescending(TrudyAnswerEvidence::relevanceScore)
        ).distinctBy { it.domain to it.metricId }.take(8)
        return TrudyAnswerPlan(
            intent = intent,
            timeframeLabel = timeframe,
            allEvidence = scored,
            rankedEvidence = (if (findings.isNotEmpty()) findings else fallback).take(8),
            limitations = limitations,
            targetSentenceCount = targetLength(intent, scored),
            safetyLevel = safetyLevel(systemInstruction)
        )
    }

    fun synthesize(plan: TrudyAnswerPlan): String = when {
        plan.safetyLevel == TrudyAnswerSafetyLevel.EMERGENCY ->
            "This could need emergency assessment now. Call your local emergency number or get urgent in-person help immediately, especially if the symptoms are severe, new, or worsening."
        plan.safetyLevel == TrudyAnswerSafetyLevel.URGENT ->
            "Please seek urgent medical assessment today. If the symptoms become severe or rapidly worsen, use emergency services."
        plan.intent == TrudyAnswerIntent.READING -> reading(plan)
        plan.intent == TrudyAnswerIntent.TREND -> trend(plan)
        plan.intent == TrudyAnswerIntent.PRIORITY -> priority(plan)
        plan.intent == TrudyAnswerIntent.CAUSE -> cause(plan)
        plan.intent == TrudyAnswerIntent.ASSOCIATION -> association(plan)
        plan.intent == TrudyAnswerIntent.EVIDENCE_FOLLOW_UP -> evidenceFollowUp(plan)
        plan.intent == TrudyAnswerIntent.EXPERIMENT -> experiment(plan)
        plan.intent == TrudyAnswerIntent.PERSONAL_SUMMARY -> personalSummary(plan)
        else -> plan.rankedEvidence.firstOrNull()?.summary
            ?: "I don't have enough relevant personal data to answer that reliably."
    }.normalizeAnswer()

    fun finalize(plan: TrudyAnswerPlan, proposedAnswer: String?): String {
        val proposed = proposedAnswer?.trim().orEmpty()
        if (proposed.isBlank()) return synthesize(plan)
        if (plan.safetyLevel != TrudyAnswerSafetyLevel.NONE && !hasSafetyAction(proposed)) return synthesize(plan)
        if (hasInternalLanguage(proposed) || hasUglyUnit(proposed) || repeatsCausalDisclaimer(proposed)) {
            return if (hasSafetyAction(proposed)) safetyRewrite(proposed) else synthesize(plan)
        }
        if (plan.intent == TrudyAnswerIntent.READING &&
            plan.rankedEvidence.none { it.kind == TrudyAnswerEvidenceKind.OBSERVATION }
        ) return synthesize(plan)
        if (plan.intent == TrudyAnswerIntent.CAUSE &&
            plan.allEvidence.any { it.classification == TrudyAnswerEvidenceClass.CONTRADICTORY }
        ) return synthesize(plan)
        if (plan.intent == TrudyAnswerIntent.PRIORITY &&
            plan.rankedEvidence.none {
                it.classification == TrudyAnswerEvidenceClass.USABLE ||
                    it.classification == TrudyAnswerEvidenceClass.SUPPORTING
            }
        ) return synthesize(plan)
        return proposed.normalizeAnswer()
    }

    private fun collect(
        results: List<TrudyToolResult>,
        intent: TrudyAnswerIntent,
        timeframe: String
    ): List<TrudyAnswerEvidence> = buildList {
        results.forEach { result ->
            when (result) {
                is TrudyToolResult.DomainState ->
                    addRows("state", result.operation.domain, null, result.evidence, intent, timeframe)
                is TrudyToolResult.MetricHistory ->
                    addRows("history", result.operation.domain, result.operation.metricId, result.evidence, intent, timeframe)
                is TrudyToolResult.MetricWindow ->
                    addRows("window", result.operation.domain, result.operation.metricId, result.evidence, intent, timeframe, result.operation.range)
                is TrudyToolResult.DomainHistory ->
                    addRows("domain-history", result.operation.domain, null, result.evidence, intent, timeframe)
                is TrudyToolResult.DerivedFeatures -> result.evidence.forEach { add(derived(it, intent)) }
                is TrudyToolResult.Insights -> result.evidence.forEach { add(insight(it, intent)) }
                is TrudyToolResult.DataQuality -> add(quality(result.evidence))
                is TrudyToolResult.Context -> result.context.domains.forEach { domain ->
                    addRows("context", domain.domain, null, domain.currentState, intent, timeframe)
                    domain.derivedFeatures.forEach { add(derived(it, intent)) }
                    domain.insights.forEach { add(insight(it, intent)) }
                    domain.dataQuality?.let { add(quality(it)) }
                    if (domain.currentState.isEmpty() && domain.history.isEmpty() &&
                        domain.derivedFeatures.isEmpty() && domain.insights.isEmpty()
                    ) add(missing(domain.domain, null, domain.domain.displayName(), timeframe))
                }
                is PersonalTrendResult -> add(comparison(result.comparison, true))
                is BaselineComparisonResult -> add(comparison(result.comparison, true))
                is AssociationToolResult -> add(associationEvidence(result.association))
                is ExperimentHypothesisResult -> add(
                    TrudyAnswerEvidence(
                        id = "experiment:${result.hypothesis.id}",
                        classification = TrudyAnswerEvidenceClass.USABLE,
                        kind = TrudyAnswerEvidenceKind.EXPERIMENT,
                        domain = result.hypothesis.targetDomain,
                        metricId = result.hypothesis.targetMetricId,
                        label = result.hypothesis.hypothesis,
                        summary = result.hypothesis.intervention
                    )
                )
                is ExperimentEvaluationResult -> add(
                    TrudyAnswerEvidence(
                        id = "experiment-result:${result.result.hypothesisId}",
                        classification = confidenceClass(result.result.confidence, result.result.evidence.dataQualityStatus),
                        kind = TrudyAnswerEvidenceKind.EXPERIMENT,
                        domain = result.result.targetDomain,
                        metricId = result.result.targetMetricId,
                        label = "Experiment result",
                        summary = result.result.summary,
                        sampleCount = result.result.baselineSampleCount + result.result.interventionSampleCount,
                        magnitude = result.result.relativeChange?.let { abs(it) / 100.0 }
                    )
                )
                is ChangeInvestigationResult -> addInvestigation(result)
                is CanonicalExperimentsResult -> {
                    if (result.experiments.isEmpty()) {
                        add(
                            TrudyAnswerEvidence(
                                id = "saved-experiment:missing",
                                classification = TrudyAnswerEvidenceClass.MISSING,
                                kind = TrudyAnswerEvidenceKind.EXPERIMENT,
                                label = "Saved experiment",
                                summary = if (result.persistenceState == TrudyExperimentPersistenceState.NOT_CONNECTED) {
                                    "Canonical experiment history is not connected."
                                } else "No saved experiment matched the request.",
                                persistenceState = result.persistenceState
                            )
                        )
                    } else result.experiments.forEach {
                        add(
                            TrudyAnswerEvidence(
                                id = "saved-experiment:${it.id}",
                                classification = TrudyAnswerEvidenceClass.USABLE,
                                kind = TrudyAnswerEvidenceKind.EXPERIMENT,
                                domain = it.targetDomain,
                                metricId = it.targetMetricId,
                                label = it.title,
                                summary = "${it.intervention}; measuring ${metricLabel(it.targetMetricId)}",
                                persistenceState = result.persistenceState
                            )
                        )
                    }
                }
                is CanonicalExperimentEvaluationResult -> {
                    val evaluation = result.evaluation
                    if (evaluation == null) add(
                        TrudyAnswerEvidence(
                            id = "saved-experiment-result:missing",
                            classification = TrudyAnswerEvidenceClass.MISSING,
                            kind = TrudyAnswerEvidenceKind.EXPERIMENT,
                            domain = result.experiment?.targetDomain,
                            metricId = result.experiment?.targetMetricId,
                            label = "Experiment comparison",
                            summary = "No saved experiment with usable baseline and intervention periods was available.",
                            persistenceState = result.persistenceState
                        )
                    ) else add(
                        TrudyAnswerEvidence(
                            id = "saved-experiment-result:${evaluation.hypothesisId}",
                            classification = confidenceClass(evaluation.confidence, evaluation.evidence.dataQualityStatus),
                            kind = TrudyAnswerEvidenceKind.EXPERIMENT,
                            domain = evaluation.targetDomain,
                            metricId = evaluation.targetMetricId,
                            label = result.experiment?.title ?: "Experiment comparison",
                            summary = evaluation.summary,
                            sampleCount = evaluation.baselineSampleCount + evaluation.interventionSampleCount,
                            persistenceState = result.persistenceState
                        )
                    )
                }
                is SystemAvailabilityResult -> result.unavailableReasons.forEach { (module, reason) ->
                    add(
                        TrudyAnswerEvidence(
                            id = "availability:$module",
                            classification = TrudyAnswerEvidenceClass.MISSING,
                            kind = TrudyAnswerEvidenceKind.AVAILABILITY,
                            label = module.replace('_', ' '),
                            summary = reason
                        )
                    )
                }
                is TrudyToolResult.Failure -> add(
                    TrudyAnswerEvidence(
                        id = "failure:${result.operation}:${result.code}",
                        classification = TrudyAnswerEvidenceClass.MISSING,
                        kind = TrudyAnswerEvidenceKind.AVAILABILITY,
                        domain = result.operation.domains.firstOrNull(),
                        metricId = operationMetric(result.operation),
                        label = "Requested data",
                        summary = "The requested data could not be accessed reliably."
                    )
                )
            }
        }
    }

    private fun MutableList<TrudyAnswerEvidence>.addRows(
        prefix: String,
        domain: HealthDomain,
        requestedMetric: String?,
        rows: List<TrudyMetricEvidence>,
        intent: TrudyAnswerIntent,
        timeframe: String,
        requestedRange: TrudyTimeRange? = null
    ) {
        val matching = requestedMetric?.let { id -> rows.filter { it.metricId == id } } ?: rows
        if (matching.isEmpty()) {
            add(missing(domain, requestedMetric, requestedMetric?.let(::metricLabel) ?: domain.displayName(), timeframe))
            return
        }
        matching.groupBy { it.domain to it.metricId }.forEach { (key, series) ->
            val usable = series.filter { it.value.isFinite() && it.timestampEpochMs >= 0L }
            if (usable.isEmpty()) {
                add(missing(key.first, key.second, metricLabel(key.second), timeframe))
                return@forEach
            }
            val latest = usable.maxBy(TrudyMetricEvidence::timestampEpochMs)
            val quality = latest.dataQuality ?: usable.mapNotNull(TrudyMetricEvidence::dataQuality).minByOrNull(TrudyDataQualityEvidence::score)
            val age = quality?.ageHours ?: ageHours(latest.timestampEpochMs)
            val range = requestedRange ?: TrudyTimeRange(
                usable.minOf(TrudyMetricEvidence::timestampEpochMs),
                usable.maxOf(TrudyMetricEvidence::timestampEpochMs)
            )
            add(
                TrudyAnswerEvidence(
                    id = "$prefix:${key.first}:${key.second}:${range.fromEpochMs}",
                    classification = metricClass(quality, usable.size, age, intent),
                    kind = TrudyAnswerEvidenceKind.OBSERVATION,
                    domain = key.first,
                    metricId = key.second,
                    label = metricLabel(key.second),
                    summary = "${usable.size} ${recordNoun(key.second, usable.size)}; latest ${formatValue(key.second, latest.value, latest.unit)}",
                    sampleCount = usable.sumOf { it.sampleCount.coerceAtLeast(1) },
                    qualityScore = quality?.score,
                    ageHours = age,
                    latestValue = latest.value,
                    meanValue = usable.map(TrudyMetricEvidence::value).average(),
                    unit = latest.unit,
                    timestampEpochMs = latest.timestampEpochMs,
                    range = range
                )
            )
        }
    }

    private fun derived(e: TrudyDerivedMetricEvidence, intent: TrudyAnswerIntent): TrudyAnswerEvidence {
        val age = e.dataQuality?.ageHours ?: ageHours(e.range.toEpochMs)
        val classification = when {
            !e.latest.isFinite() || e.sampleCount <= 0 -> TrudyAnswerEvidenceClass.MISSING
            e.dataQuality?.isStale == true || intent == TrudyAnswerIntent.PRIORITY && age != null && age > 36 ->
                TrudyAnswerEvidenceClass.STALE
            e.sampleCount < 3 || e.confidence != null && e.confidence < .45 ||
                e.dataQuality != null && e.dataQuality.score < 50 -> TrudyAnswerEvidenceClass.LOW_QUALITY
            else -> TrudyAnswerEvidenceClass.USABLE
        }
        val change = e.change?.takeIf(Double::isFinite)
        return TrudyAnswerEvidence(
            id = "derived:${e.domain}:${e.metricId}:${e.range.fromEpochMs}",
            classification = classification,
            kind = TrudyAnswerEvidenceKind.TREND,
            domain = e.domain,
            metricId = e.metricId,
            label = metricLabel(e.metricId),
            summary = change?.let {
                "${if (it >= 0) "up" else "down"} ${formatDelta(e.metricId, abs(it), e.unit)} across ${e.sampleCount} records"
            } ?: "No reliable change estimate across ${e.sampleCount} records.",
            sampleCount = e.sampleCount,
            qualityScore = e.dataQuality?.score,
            ageHours = age,
            magnitude = change?.let { abs(it) / (abs(e.mean) + 1) },
            latestValue = e.latest,
            meanValue = e.mean,
            delta = change,
            unit = e.unit,
            range = e.range
        )
    }

    private fun insight(e: TrudyInsightEvidence, intent: TrudyAnswerIntent): TrudyAnswerEvidence {
        val missing = listOf("no data", "not enough data", "insufficient data", "no observations", "no records")
            .any { it in "${e.title} ${e.explanation}".lowercase() }
        val age = e.dataQuality?.ageHours
        val classification = when {
            missing -> TrudyAnswerEvidenceClass.MISSING
            e.dataQuality?.isStale == true || intent == TrudyAnswerIntent.PRIORITY && age != null && age > 36 ->
                TrudyAnswerEvidenceClass.STALE
            e.confidence != null && e.confidence < .45 || e.dataQuality != null && e.dataQuality.score < 50 ->
                TrudyAnswerEvidenceClass.LOW_QUALITY
            else -> TrudyAnswerEvidenceClass.SUPPORTING
        }
        return TrudyAnswerEvidence(
            id = "insight:${e.domain}:${e.id}",
            classification = classification,
            kind = TrudyAnswerEvidenceKind.INSIGHT,
            domain = e.domain,
            metricId = e.evidenceMetricIds.firstOrNull(),
            label = e.title.ifBlank { e.domain.displayName() },
            summary = e.explanation.ifBlank { e.title },
            sampleCount = e.dataQuality?.recordCount?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0,
            qualityScore = e.dataQuality?.score,
            ageHours = age
        )
    }

    private fun quality(q: TrudyDataQualityEvidence) = TrudyAnswerEvidence(
        id = "quality:${q.domain}",
        classification = when {
            q.recordCount == 0L -> TrudyAnswerEvidenceClass.MISSING
            q.isStale -> TrudyAnswerEvidenceClass.STALE
            q.score < 50 -> TrudyAnswerEvidenceClass.LOW_QUALITY
            else -> TrudyAnswerEvidenceClass.SUPPORTING
        },
        kind = TrudyAnswerEvidenceKind.DATA_QUALITY,
        domain = q.domain,
        label = "${q.domain.displayName()} data coverage",
        summary = when {
            q.recordCount == 0L -> "No recorded observations are available."
            q.isStale -> "The latest record is ${naturalAge(q.ageHours)} old."
            q.score < 50 -> "Coverage is limited."
            else -> "${q.recordCount} records are available."
        },
        sampleCount = q.recordCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        qualityScore = q.score,
        ageHours = q.ageHours
    )

    private fun comparison(c: TrudyBaselineComparison, primary: Boolean): TrudyAnswerEvidence {
        val classification = when {
            c.absoluteDelta == null || c.observationMean == null || c.baselineMean == null ->
                TrudyAnswerEvidenceClass.MISSING
            c.dataQualityStatus == TrudyDataQualityStatus.STALE -> TrudyAnswerEvidenceClass.STALE
            c.dataQualityStatus != TrudyDataQualityStatus.GOOD || c.confidence == TrudyConfidence.INSUFFICIENT ->
                TrudyAnswerEvidenceClass.LOW_QUALITY
            primary -> TrudyAnswerEvidenceClass.USABLE
            else -> TrudyAnswerEvidenceClass.SUPPORTING
        }
        return TrudyAnswerEvidence(
            id = "comparison:${c.domain}:${c.metricId}:${c.observationWindow.fromEpochMs}",
            classification = classification,
            kind = TrudyAnswerEvidenceKind.TREND,
            domain = c.domain,
            metricId = c.metricId,
            label = metricLabel(c.metricId),
            summary = c.absoluteDelta?.let {
                "${if (it >= 0) "up" else "down"} ${formatDelta(c.metricId, abs(it), canonicalUnit(c.metricId))}; " +
                    "${c.observationSampleCount} recent and ${c.baselineSampleCount} comparison records"
            } ?: "Missing records in one or both comparison periods.",
            sampleCount = c.observationSampleCount,
            comparisonSampleCount = c.baselineSampleCount,
            magnitude = c.percentDelta?.let { abs(it) / 100 },
            meanValue = c.observationMean,
            baselineMean = c.baselineMean,
            delta = c.absoluteDelta,
            unit = canonicalUnit(c.metricId),
            range = c.observationWindow
        )
    }

    private fun associationEvidence(a: TrudyAssociationResult): TrudyAnswerEvidence {
        val classification = when {
            a.coefficient == null || a.sampleCount < 5 -> TrudyAnswerEvidenceClass.MISSING
            a.dataQualityStatus == TrudyDataQualityStatus.STALE -> TrudyAnswerEvidenceClass.STALE
            a.dataQualityStatus != TrudyDataQualityStatus.GOOD || a.confidence == TrudyConfidence.INSUFFICIENT ->
                TrudyAnswerEvidenceClass.LOW_QUALITY
            else -> TrudyAnswerEvidenceClass.SUPPORTING
        }
        return TrudyAnswerEvidence(
            id = "association:${a.leftDomain}:${a.leftMetricId}:${a.rightDomain}:${a.rightMetricId}",
            classification = classification,
            kind = TrudyAnswerEvidenceKind.ASSOCIATION,
            domain = a.rightDomain,
            metricId = a.rightMetricId,
            label = "${metricLabel(a.leftMetricId)} and ${metricLabel(a.rightMetricId)}",
            summary = a.coefficient?.let {
                "${associationStrength(it)} pattern across ${a.sampleCount} aligned records"
            } ?: "Too few aligned records to estimate a relationship.",
            sampleCount = a.sampleCount,
            magnitude = a.coefficient?.let(::abs),
            associationLeftMetricId = a.leftMetricId,
            associationRightMetricId = a.rightMetricId,
            associationCoefficient = a.coefficient
        )
    }

    private fun MutableList<TrudyAnswerEvidence>.addInvestigation(result: ChangeInvestigationResult) {
        val i = result.investigation
        add(
            TrudyAnswerEvidence(
                id = "premise:${i.observationWindow.fromEpochMs}",
                classification = when (i.premiseAssessment) {
                    TrudyPremiseAssessment.NOT_SUPPORTED, TrudyPremiseAssessment.MIXED ->
                        TrudyAnswerEvidenceClass.CONTRADICTORY
                    TrudyPremiseAssessment.INSUFFICIENT -> TrudyAnswerEvidenceClass.MISSING
                    TrudyPremiseAssessment.SUPPORTED -> TrudyAnswerEvidenceClass.USABLE
                },
                kind = TrudyAnswerEvidenceKind.PREMISE,
                domain = result.operation.targets.firstOrNull()?.domain,
                metricId = result.operation.targets.firstOrNull()?.metricId,
                label = "Claim check",
                summary = when (i.premiseAssessment) {
                    TrudyPremiseAssessment.NOT_SUPPORTED -> "The recorded target metrics do not support the claimed direction."
                    TrudyPremiseAssessment.MIXED -> "The recorded target metrics moved in mixed directions."
                    TrudyPremiseAssessment.INSUFFICIENT -> "There are not enough records in both periods to verify the claim."
                    TrudyPremiseAssessment.SUPPORTED -> "The recorded target metrics support the claimed change."
                },
                premiseAssessment = i.premiseAssessment,
                changeClaim = result.operation.claim
            )
        )
        i.targetComparisons.forEachIndexed { index, c -> add(comparison(c, index == 0)) }
        i.relatedAssociations.forEach { add(associationEvidence(it)) }
        i.missingMetrics.forEach { (domain, metric) ->
            add(missing(domain, metric, metricLabel(metric), "the investigated period"))
        }
    }

    private fun missing(domain: HealthDomain?, metric: String?, label: String, timeframe: String) =
        TrudyAnswerEvidence(
            id = "missing:${domain?.name}:$metric:$timeframe",
            classification = TrudyAnswerEvidenceClass.MISSING,
            kind = TrudyAnswerEvidenceKind.OBSERVATION,
            domain = domain,
            metricId = metric,
            label = label,
            summary = "No recorded measurement was available for $timeframe."
        )

    private fun rank(
        e: TrudyAnswerEvidence,
        intent: TrudyAnswerIntent,
        domains: List<HealthDomain>,
        question: String
    ): Double {
        var score = when {
            e.domain == null -> 4.0
            domains.isEmpty() -> 18.0
            e.domain in domains -> 38.0
            else -> -28.0
        }
        if (e.metricId.orEmpty().replace('_', ' ').split(' ').any { it.length >= 4 && it in question }) score += 10
        score += when (intent) {
            TrudyAnswerIntent.READING -> if (e.kind == TrudyAnswerEvidenceKind.OBSERVATION) 34.0 else 0.0
            TrudyAnswerIntent.TREND -> if (e.kind == TrudyAnswerEvidenceKind.TREND) 34.0 else 0.0
            TrudyAnswerIntent.PRIORITY -> when (e.kind) {
                TrudyAnswerEvidenceKind.INSIGHT -> 32.0
                TrudyAnswerEvidenceKind.TREND -> 25.0
                TrudyAnswerEvidenceKind.OBSERVATION -> 8.0
                TrudyAnswerEvidenceKind.DATA_QUALITY -> -12.0
                else -> 0.0
            }
            TrudyAnswerIntent.CAUSE -> when (e.kind) {
                TrudyAnswerEvidenceKind.PREMISE -> 45.0
                TrudyAnswerEvidenceKind.TREND -> 30.0
                TrudyAnswerEvidenceKind.ASSOCIATION -> 24.0
                else -> 0.0
            }
            TrudyAnswerIntent.ASSOCIATION -> if (e.kind == TrudyAnswerEvidenceKind.ASSOCIATION) 40.0 else 0.0
            TrudyAnswerIntent.EVIDENCE_FOLLOW_UP -> if (e.kind == TrudyAnswerEvidenceKind.DATA_QUALITY) 5.0 else 30.0
            TrudyAnswerIntent.EXPERIMENT -> if (e.kind == TrudyAnswerEvidenceKind.EXPERIMENT) 40.0 else 0.0
            TrudyAnswerIntent.PERSONAL_SUMMARY -> if (e.kind in setOf(TrudyAnswerEvidenceKind.INSIGHT, TrudyAnswerEvidenceKind.TREND)) 24.0 else 8.0
            TrudyAnswerIntent.GENERAL -> 0.0
        }
        score += when {
            e.ageHours == null -> 2.0
            e.ageHours <= 24 -> 15.0
            e.ageHours <= 72 -> 10.0
            e.ageHours <= 168 -> 5.0
            else -> -minOf(20.0, e.ageHours / 168)
        }
        score += ((e.qualityScore ?: 70) - 50).coerceIn(-30, 50) / 5.0
        score += minOf(10.0, e.sampleCount / 3.0)
        score += minOf(10.0, (e.magnitude ?: 0.0) * 20)
        score += when (e.classification) {
            TrudyAnswerEvidenceClass.USABLE -> 14.0
            TrudyAnswerEvidenceClass.SUPPORTING -> 8.0
            TrudyAnswerEvidenceClass.CONTRADICTORY -> 20.0
            TrudyAnswerEvidenceClass.STALE -> -48.0
            TrudyAnswerEvidenceClass.LOW_QUALITY -> -32.0
            TrudyAnswerEvidenceClass.MISSING -> -120.0
            TrudyAnswerEvidenceClass.IRRELEVANT -> -200.0
        }
        return score
    }

    private fun reading(plan: TrudyAnswerPlan): String {
        val observations = plan.rankedEvidence.filter {
            it.kind == TrudyAnswerEvidenceKind.OBSERVATION &&
                it.classification == TrudyAnswerEvidenceClass.USABLE
        }
        val systolic = observations.firstOrNull { it.metricId == "blood_pressure_systolic_mmhg" }
        val diastolic = observations.firstOrNull { it.metricId == "blood_pressure_diastolic_mmhg" }
        val bpAsked = plan.allEvidence.any {
            it.metricId == "blood_pressure_systolic_mmhg" || it.metricId == "blood_pressure_diastolic_mmhg"
        }
        if (bpAsked) {
            if (systolic == null && diastolic == null) {
                return "I don't have a blood-pressure reading from ${plan.timeframeLabel}."
            }
            if (systolic?.latestValue != null && diastolic?.latestValue != null) {
                return "Your blood pressure ${timeframePreposition(plan.timeframeLabel)} was ${number(systolic.latestValue)}/${number(diastolic.latestValue)} mmHg."
            }
            val available = systolic ?: diastolic!!
            val missingPart = if (systolic == null) "systolic" else "diastolic"
            return "I only have the ${available.label} reading from ${plan.timeframeLabel}: ${formatValue(available.metricId.orEmpty(), available.latestValue!!, available.unit)}. The $missingPart value is missing."
        }
        val top = observations.firstOrNull()
            ?: return missingAnswer(plan, "recorded measurement")
        return "Your ${top.label} ${timeframePreposition(plan.timeframeLabel)} was ${formatValue(top.metricId.orEmpty(), top.latestValue!!, top.unit)}."
    }

    private fun trend(plan: TrudyAnswerPlan): String {
        val comparisons = plan.allEvidence.filter {
            it.kind == TrudyAnswerEvidenceKind.TREND && it.meanValue != null
        }.sortedByDescending(TrudyAnswerEvidence::relevanceScore)
        val top = comparisons.firstOrNull()
        if (top == null) {
            val observation = plan.rankedEvidence.firstOrNull { it.kind == TrudyAnswerEvidenceKind.OBSERVATION }
                ?: return missingAnswer(plan, "records")
            return "I only have ${observation.sampleCount.asWords()} ${recordNoun(observation.metricId, observation.sampleCount)} from ${plan.timeframeLabel}, so I can't judge the trend reliably yet."
        }
        if (top.sampleCount < 3 || top.comparisonSampleCount < 3 ||
            top.meanValue == null || top.baselineMean == null || top.delta == null
        ) {
            val noun = if (top.domain == HealthDomain.SLEEP) "recorded nights" else recordNoun(top.metricId, top.sampleCount)
            return "I only have ${top.sampleCount.asWords()} $noun from ${plan.timeframeLabel}, so that's too little to judge the trend reliably yet."
        }
        val subject = if (top.domain == HealthDomain.SLEEP) "Your sleep" else "Your ${top.label}"
        val opening = if (smallChange(top.metricId, top.delta, top.baselineMean)) {
            "$subject looks fairly stable over ${plan.timeframeLabel}."
        } else "$subject has been ${if (top.delta > 0) "higher" else "lower"} over ${plan.timeframeLabel}."
        val support = "${top.label.sentenceCase()} averaged ${formatValue(top.metricId.orEmpty(), top.meanValue, top.unit)}, compared with ${formatValue(top.metricId.orEmpty(), top.baselineMean, top.unit)} in the previous comparable period."
        return "$opening $support"
    }

    private fun priority(plan: TrudyAnswerPlan): String {
        val fresh = plan.rankedEvidence.filter {
            it.classification in setOf(
                TrudyAnswerEvidenceClass.USABLE,
                TrudyAnswerEvidenceClass.SUPPORTING,
                TrudyAnswerEvidenceClass.CONTRADICTORY
            ) && it.kind != TrudyAnswerEvidenceKind.DATA_QUALITY
        }
        if (fresh.isEmpty()) {
            val stale = plan.allEvidence.filter { it.classification == TrudyAnswerEvidenceClass.STALE }
                .maxByOrNull(TrudyAnswerEvidence::relevanceScore)
            return if (stale == null) {
                "I don't have enough fresh health data from today to identify a priority."
            } else {
                "I don't have enough fresh health data from today to identify a priority. The latest ${stale.label} information is ${naturalAge(stale.ageHours)} old, so I wouldn't use it to judge today."
            }
        }
        val top = fresh.first()
        return when (top.kind) {
            TrudyAnswerEvidenceKind.INSIGHT ->
                "The main thing worth paying attention to today is ${top.summary.trim().trimEnd('.').lowercaseFirst()}."
            TrudyAnswerEvidenceKind.TREND -> top.delta?.let {
                "The clearest change worth watching is ${top.label}, which is ${if (it >= 0) "higher" else "lower"} than its comparison period by ${formatDelta(top.metricId.orEmpty(), abs(it), top.unit)}."
            } ?: "Nothing reliable stands out as a priority in today's available data."
            TrudyAnswerEvidenceKind.OBSERVATION ->
                "Nothing in the available fresh readings clearly stands out as a priority today. Your latest ${top.label} is ${formatValue(top.metricId.orEmpty(), top.latestValue!!, top.unit)}."
            else -> "Nothing reliable stands out as a priority in today's available data."
        }
    }

    private fun cause(plan: TrudyAnswerPlan): String {
        val premise = plan.allEvidence.firstOrNull { it.kind == TrudyAnswerEvidenceKind.PREMISE }
        val comparisons = plan.allEvidence.filter {
            it.kind == TrudyAnswerEvidenceKind.TREND && it.delta != null
        }.sortedByDescending(TrudyAnswerEvidence::relevanceScore)
        val subject = if (comparisons.firstOrNull()?.domain == HealthDomain.SLEEP) "sleep" else comparisons.firstOrNull()?.label ?: "target metric"
        if (premise?.premiseAssessment == null ||
            premise.premiseAssessment == TrudyPremiseAssessment.INSUFFICIENT ||
            comparisons.isEmpty()
        ) {
            val best = plan.allEvidence.filter { it.kind == TrudyAnswerEvidenceKind.TREND }
                .maxByOrNull { it.sampleCount + it.comparisonSampleCount }
            val detail = best?.let {
                " I have ${it.sampleCount} records in ${plan.timeframeLabel} and ${it.comparisonSampleCount} in the comparison period."
            }.orEmpty()
            return "I can't verify that your $subject changed because there isn't enough data in both periods.$detail Without confirming the change first, it wouldn't be reliable to assign a reason."
        }
        val evidence = comparisons.take(3).joinToString(" ") {
            "${it.label.sentenceCase()} ${if (it.delta!! >= 0) "rose" else "fell"} by ${formatDelta(it.metricId.orEmpty(), abs(it.delta), it.unit)}."
        }
        if (premise.premiseAssessment == TrudyPremiseAssessment.NOT_SUPPORTED) {
            val actualDirection = when (premise.changeClaim) {
                TrudyChangeClaim.WORSENED -> "improved"
                TrudyChangeClaim.IMPROVED -> "worsened"
                else -> "didn't move in the claimed direction"
            }
            val experience = when (premise.changeClaim) {
                TrudyChangeClaim.WORSENED -> "That doesn't mean it didn't feel worse; "
                TrudyChangeClaim.IMPROVED -> "That doesn't mean it didn't feel better; "
                else -> ""
            }
            return "Your recorded $subject data doesn't support that: the tracked measures $actualDirection over ${plan.timeframeLabel}. $evidence ${experience}it means your experience and the recorded metrics don't currently match."
        }
        if (premise.premiseAssessment == TrudyPremiseAssessment.MIXED) {
            return "Your recorded $subject picture is mixed rather than clearly moving in one direction over ${plan.timeframeLabel}. $evidence Because the target measures disagree, there isn't one confirmed change to explain."
        }
        val direction = when (premise.changeClaim) {
            TrudyChangeClaim.WORSENED -> "did look worse"
            TrudyChangeClaim.IMPROVED -> "did improve"
            else -> "did change"
        }
        val related = plan.allEvidence.filter {
            it.kind == TrudyAnswerEvidenceKind.ASSOCIATION &&
                it.classification == TrudyAnswerEvidenceClass.SUPPORTING
        }.maxByOrNull(TrudyAnswerEvidence::relevanceScore)
        if (related == null) {
            val missing = plan.limitations.filter { it.classification == TrudyAnswerEvidenceClass.MISSING }
                .map(TrudyAnswerEvidence::label).distinct().take(2)
            val ending = if (missing.isEmpty()) {
                "I couldn't identify a related pattern with enough aligned data to explain it."
            } else "I couldn't test ${missing.joinToString(" or ")} well enough to identify a plausible related pattern."
            return "Your recorded $subject $direction over ${plan.timeframeLabel}. $evidence $ending"
        }
        val left = related.associationLeftMetricId!!
        val right = related.associationRightMetricId!!
        val movement = if (related.associationCoefficient!! >= 0) "rose and fell together" else "tended to move in opposite directions"
        return "Your recorded $subject $direction over ${plan.timeframeLabel}. $evidence ${metricLabel(left).sentenceCase()} and ${metricLabel(right)} $movement across ${related.sampleCount} aligned records. That's worth watching, but it doesn't show that ${metricLabel(left)} caused the change."
    }

    private fun association(plan: TrudyAnswerPlan): String {
        val top = plan.rankedEvidence.firstOrNull { it.kind == TrudyAnswerEvidenceKind.ASSOCIATION }
            ?: return "I don't have enough aligned records from ${plan.timeframeLabel} to judge that relationship reliably."
        val coefficient = top.associationCoefficient
            ?: return "I don't have enough aligned records from ${plan.timeframeLabel} to judge that relationship reliably."
        val movement = if (coefficient >= 0) "tended to move together" else "tended to move in opposite directions"
        return "${metricLabel(top.associationLeftMetricId!!).sentenceCase()} and ${metricLabel(top.associationRightMetricId!!)} $movement across ${top.sampleCount} aligned records. It's a pattern in your data, but it doesn't show that one caused the other."
    }

    private fun evidenceFollowUp(plan: TrudyAnswerPlan): String {
        val top = plan.rankedEvidence.firstOrNull()
            ?: return "I don't have a recorded measurement behind that answer, so I can't give you a stronger evidence basis."
        return when (top.kind) {
            TrudyAnswerEvidenceKind.TREND ->
                "I'm basing that on ${top.sampleCount} ${recordNoun(top.metricId, top.sampleCount)} from ${plan.timeframeLabel}, compared with ${top.comparisonSampleCount} from the previous period. ${top.label.sentenceCase()} averaged ${top.meanValue?.let { formatValue(top.metricId.orEmpty(), it, top.unit) } ?: "not enough data"} versus ${top.baselineMean?.let { formatValue(top.metricId.orEmpty(), it, top.unit) } ?: "not enough data"}."
            TrudyAnswerEvidenceKind.OBSERVATION ->
                "I'm basing that on ${top.sampleCount} ${recordNoun(top.metricId, top.sampleCount)} from ${plan.timeframeLabel}. The latest ${top.label} was ${formatValue(top.metricId.orEmpty(), top.latestValue!!, top.unit)}."
            TrudyAnswerEvidenceKind.ASSOCIATION ->
                "I'm basing that on ${top.sampleCount} aligned ${metricLabel(top.associationLeftMetricId!!)} and ${metricLabel(top.associationRightMetricId!!)} records from ${plan.timeframeLabel}."
            else -> "I'm basing that on ${top.summary.trim().trimEnd('.').lowercaseFirst()}."
        }
    }

    private fun experiment(plan: TrudyAnswerPlan): String {
        val top = plan.rankedEvidence.firstOrNull { it.kind == TrudyAnswerEvidenceKind.EXPERIMENT }
        if (top == null) {
            val missing = plan.allEvidence.firstOrNull { it.kind == TrudyAnswerEvidenceKind.EXPERIMENT }
            return if (missing?.persistenceState == TrudyExperimentPersistenceState.NOT_CONNECTED) {
                "I can't see a saved experiment yet because canonical experiment history isn't connected. I won't treat the preview protocols as experiments you've run."
            } else "I don't have a saved experiment matching that request."
        }
        return "${top.label}. ${top.summary.trim().trimEnd('.')}."
    }

    private fun personalSummary(plan: TrudyAnswerPlan): String {
        val top = plan.rankedEvidence.firstOrNull() ?: return missingAnswer(plan, "recent records")
        return when (top.kind) {
            TrudyAnswerEvidenceKind.INSIGHT -> top.summary
            TrudyAnswerEvidenceKind.TREND -> top.delta?.let {
                "The main change in ${plan.timeframeLabel} is ${top.label}, which is ${if (it >= 0) "higher" else "lower"} by ${formatDelta(top.metricId.orEmpty(), abs(it), top.unit)}."
            } ?: missingAnswer(plan, "comparison records")
            TrudyAnswerEvidenceKind.OBSERVATION ->
                "Your latest ${top.label} is ${formatValue(top.metricId.orEmpty(), top.latestValue!!, top.unit)}."
            else -> top.summary
        }
    }

    private fun missingAnswer(plan: TrudyAnswerPlan, noun: String): String {
        val specific = plan.limitations.firstOrNull { it.metricId != null }
        return if (specific == null) {
            "I don't have enough $noun from ${plan.timeframeLabel} to answer that reliably."
        } else "I don't have enough ${specific.label} $noun from ${plan.timeframeLabel} to answer that reliably."
    }

    private fun detectIntent(text: String) = when {
        listOf("what data", "basing that on", "based on what", "where did that come from").any { it in text } ->
            TrudyAnswerIntent.EVIDENCE_FOLLOW_UP
        "experiment" in text || "baseline" in text && "intervention" in text -> TrudyAnswerIntent.EXPERIMENT
        listOf("pay attention", "priority", "stand out", "focus on today").any { it in text } -> TrudyAnswerIntent.PRIORITY
        listOf("why", "what caused", "explain why", "suffered", "terrible", "awful", "worsened", "worse lately").any { it in text } ->
            TrudyAnswerIntent.CAUSE
        listOf("associated", "association", "linked", "connected", "relationship", "affect").any { it in text } ->
            TrudyAnswerIntent.ASSOCIATION
        listOf("what was my", "what is my", "what's my", "reading", "how much").any { it in text } ->
            TrudyAnswerIntent.READING
        listOf("how's my", "how is my", "how has my", "doing", "trend").any { it in text } ->
            TrudyAnswerIntent.TREND
        TrudySystemCatalog.modulesMentioned(text).isNotEmpty() -> TrudyAnswerIntent.PERSONAL_SUMMARY
        else -> TrudyAnswerIntent.GENERAL
    }

    private fun temporalLabel(text: String, conversation: List<TrudyConversationTurn>): String {
        val source = if (hasTimePhrase(text)) text else conversation.asReversed()
            .firstOrNull { hasTimePhrase(it.text.lowercase()) }?.text?.lowercase().orEmpty()
        return when {
            "last week or so" in source || "past week or so" in source -> "roughly the past week"
            "yesterday" in source -> "yesterday"
            "today" in source -> "today"
            "this week" in source -> "this week"
            "last week" in source -> "last week"
            "few weeks" in source -> "the last few weeks"
            "this month" in source -> "this month"
            "last month" in source -> "last month"
            "recent" in source || "lately" in source -> "recently"
            else -> "the recent period"
        }
    }

    private fun hasTimePhrase(text: String) = listOf(
        "today", "yesterday", "this week", "last week", "past week", "recent", "lately", "few weeks", "this month", "last month"
    ).any { it in text }

    private fun metricClass(q: TrudyDataQualityEvidence?, count: Int, age: Double?, intent: TrudyAnswerIntent) = when {
        count == 0 -> TrudyAnswerEvidenceClass.MISSING
        q?.isStale == true || intent == TrudyAnswerIntent.PRIORITY && age != null && age > 36 ->
            TrudyAnswerEvidenceClass.STALE
        q != null && q.score < 50 -> TrudyAnswerEvidenceClass.LOW_QUALITY
        else -> TrudyAnswerEvidenceClass.USABLE
    }

    private fun confidenceClass(c: TrudyConfidence, q: TrudyDataQualityStatus) = when {
        q == TrudyDataQualityStatus.STALE -> TrudyAnswerEvidenceClass.STALE
        q != TrudyDataQualityStatus.GOOD || c == TrudyConfidence.INSUFFICIENT -> TrudyAnswerEvidenceClass.LOW_QUALITY
        else -> TrudyAnswerEvidenceClass.USABLE
    }

    private fun limitationPriority(c: TrudyAnswerEvidenceClass) = when (c) {
        TrudyAnswerEvidenceClass.MISSING -> 0
        TrudyAnswerEvidenceClass.STALE -> 1
        TrudyAnswerEvidenceClass.LOW_QUALITY -> 2
        TrudyAnswerEvidenceClass.IRRELEVANT -> 3
        else -> 4
    }

    private fun relevanceClass(
        evidence: TrudyAnswerEvidence,
        intent: TrudyAnswerIntent,
        mentionedDomains: List<HealthDomain>
    ): TrudyAnswerEvidenceClass {
        if (evidence.classification in setOf(
                TrudyAnswerEvidenceClass.MISSING,
                TrudyAnswerEvidenceClass.STALE,
                TrudyAnswerEvidenceClass.LOW_QUALITY,
                TrudyAnswerEvidenceClass.CONTRADICTORY
            )
        ) return evidence.classification
        if (mentionedDomains.isEmpty() || evidence.domain == null || evidence.domain in mentionedDomains) {
            return evidence.classification
        }
        if (intent in setOf(TrudyAnswerIntent.CAUSE, TrudyAnswerIntent.ASSOCIATION) &&
            evidence.kind == TrudyAnswerEvidenceKind.ASSOCIATION
        ) return TrudyAnswerEvidenceClass.SUPPORTING
        return TrudyAnswerEvidenceClass.IRRELEVANT
    }

    private fun targetLength(intent: TrudyAnswerIntent, evidence: List<TrudyAnswerEvidence>) = when (intent) {
        TrudyAnswerIntent.READING -> 1
        TrudyAnswerIntent.PRIORITY, TrudyAnswerIntent.EVIDENCE_FOLLOW_UP, TrudyAnswerIntent.ASSOCIATION -> 2
        TrudyAnswerIntent.TREND -> if (evidence.count { it.kind == TrudyAnswerEvidenceKind.TREND } > 1) 3 else 2
        TrudyAnswerIntent.CAUSE -> 4
        TrudyAnswerIntent.EXPERIMENT, TrudyAnswerIntent.GENERAL -> 3
        TrudyAnswerIntent.PERSONAL_SUMMARY -> 2
    }

    private fun safetyLevel(instruction: String) = when {
        instruction.contains("Safety level: EMERGENCY", true) -> TrudyAnswerSafetyLevel.EMERGENCY
        instruction.contains("Safety level: URGENT", true) -> TrudyAnswerSafetyLevel.URGENT
        else -> TrudyAnswerSafetyLevel.NONE
    }

    private fun hasInternalLanguage(answer: String): Boolean {
        val text = answer.lowercase()
        return listOf(
            "bounded context", "structured evidence", "tool execution", "tool result",
            "preflight", "context bundle", "model provider", "provider response",
            "schema terminology", "no data yet", "answer intent:", "status=usable",
            "status=missing", "descriptive personal trend", "recent structured comparison",
            "structured calculation", "another useful signal", "the clearest thing to pay attention to is"
        ).any { it in text }
    }

    private fun hasUglyUnit(answer: String) =
        Regex("""\b\d+(?:\.\d+)?\s+(?:score|index|native units?|schema units?)\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(answer)

    private fun repeatsCausalDisclaimer(answer: String): Boolean {
        val text = answer.lowercase()
        return listOf(
            "association is not causation", "does not prove causation", "not evidence of causation",
            "doesn't show that", "does not show that"
        ).sumOf { phrase -> text.windowed(phrase.length).count { it == phrase } } > 1
    }

    private fun hasSafetyAction(answer: String): Boolean {
        val text = answer.lowercase()
        return listOf("emergency", "urgent", "call", "seek", "medical assessment").any { it in text }
    }

    private fun safetyRewrite(answer: String): String {
        val text = answer.lowercase()
        return if (listOf("emergency", "immediately", "call 999", "call 911", "emergency number").any { it in text }) {
            "This could need emergency assessment now. Call your local emergency number or get urgent in-person help immediately."
        } else {
            "Please seek urgent medical assessment today. If the symptoms become severe or rapidly worsen, use emergency services."
        }
    }

    private fun ageHours(timestamp: Long): Double? {
        val now = nowEpochMs()
        if (timestamp < 0 || timestamp > now) return null
        return (now - timestamp) / 3_600_000.0
    }

    private fun naturalAge(hours: Double?) = when {
        hours == null -> "an unknown amount of time"
        hours < 1.5 -> "about an hour"
        hours < 36 -> "about ${round(hours).toInt()} hours"
        hours < 336 -> "about ${round(hours / 24).toInt()} days"
        else -> "about ${round(hours / 168).toInt()} weeks"
    }

    private fun smallChange(metric: String?, delta: Double, baseline: Double): Boolean {
        val threshold = when (metric) {
            "sleep_score", "sleep_continuity_score" -> 3.0
            "sleep_total_minutes", "sleep_deep_minutes", "sleep_awake_minutes" -> 15.0
            "resting_heart_rate_bpm", "heart_rate_avg_bpm" -> 2.0
            "blood_pressure_systolic_mmhg", "blood_pressure_diastolic_mmhg" -> 3.0
            "body_weight_kg" -> .5
            else -> abs(baseline) * .03
        }
        return abs(delta) <= threshold.coerceAtLeast(.01)
    }

    private fun moduleDomains(id: String) = when (id) {
        "clinical" -> listOf(HealthDomain.CLINICAL)
        "sleep" -> listOf(HealthDomain.SLEEP)
        "blood_pressure" -> listOf(HealthDomain.BLOOD_PRESSURE)
        "nutrition" -> listOf(HealthDomain.NUTRITION)
        "hydration" -> listOf(HealthDomain.HYDRATION)
        "environment" -> listOf(HealthDomain.ENVIRONMENT)
        "emotional" -> listOf(HealthDomain.EMOTIONAL)
        "mindfulness" -> listOf(HealthDomain.MINDFULNESS)
        "body" -> listOf(HealthDomain.BODY)
        "exercise", "steps", "heart_rate" -> listOf(HealthDomain.EXERCISE)
        else -> emptyList()
    }

    private fun operationMetric(op: TrudyToolOperation) = when (op) {
        is TrudyToolOperation.GetMetricHistory -> op.metricId
        is TrudyToolOperation.GetMetricWindow -> op.metricId
        is GetPersonalTrend -> op.metricId
        is CompareBaseline -> op.metricId
        else -> null
    }

    private fun associationStrength(value: Double) = when {
        abs(value) < .15 -> "very weak"
        abs(value) < .35 -> "weak"
        abs(value) < .6 -> "moderate"
        else -> "strong"
    }

    private fun timeframePreposition(label: String) =
        if (label in setOf("today", "yesterday")) label else "during $label"

    private fun metricLabel(id: String) = labels[id]
        ?: id.replace('_', ' ').removeSuffix(" bpm").removeSuffix(" mmhg").removeSuffix(" pct")

    private fun canonicalUnit(id: String?) = when (id) {
        "sleep_total_minutes", "sleep_deep_minutes", "sleep_awake_minutes", "exercise_minutes",
        "mindfulness_session_minutes" -> "min"
        "resting_heart_rate_bpm", "heart_rate_avg_bpm", "heart_rate_max_bpm", "blood_pressure_pulse_bpm" -> "bpm"
        "blood_pressure_systolic_mmhg", "blood_pressure_diastolic_mmhg" -> "mmHg"
        "body_temperature_celsius", "environment_temperature_c" -> "°C"
        "blood_oxygen_percent", "body_fat_pct", "environment_relative_humidity_pct", "environment_cloud_cover_pct" -> "%"
        "body_weight_kg", "body_muscle_mass_kg" -> "kg"
        "body_waist_cm" -> "cm"
        "water_total_l" -> "L"
        "water_intake_ml" -> "mL"
        "food_kcal", "calories_burned_active_kcal" -> "kcal"
        "food_protein", "food_carbs", "food_fat", "food_fibre" -> "g"
        else -> ""
    }

    private fun formatValue(id: String, value: Double, rawUnit: String): String {
        if (id == "sleep_total_minutes" && value >= 60) {
            val minutes = round(value).toInt()
            return "${minutes / 60} h ${minutes % 60} min"
        }
        val unit = naturalUnit(id, rawUnit)
        return if (unit.isBlank()) number(value) else "${number(value)} $unit"
    }

    private fun formatDelta(id: String, value: Double, rawUnit: String): String {
        val unit = naturalUnit(id, rawUnit)
        return if (unit.isBlank()) number(value) else "${number(value)} $unit"
    }

    private fun naturalUnit(id: String, raw: String): String {
        canonicalUnit(id).takeIf(String::isNotBlank)?.let { return it }
        return when (raw.trim().lowercase()) {
            "", "score", "index", "unit", "units", "native unit", "native units" -> ""
            "mmhg" -> "mmHg"
            "c", "°c", "celsius" -> "°C"
            "percent", "pct" -> "%"
            "minutes", "minute" -> "min"
            "millilitres", "milliliters", "ml" -> "mL"
            "litres", "liters", "l" -> "L"
            else -> raw.trim()
        }
    }

    private fun number(value: Double): String {
        if (!value.isFinite()) return "unavailable"
        val oneDecimal = round(value * 10) / 10
        return if (abs(oneDecimal - round(oneDecimal)) < .0001) {
            round(oneDecimal).toLong().toString()
        } else oneDecimal.toString()
    }

    private fun recordNoun(metric: String?, count: Int): String {
        val singular = when {
            metric?.startsWith("sleep_") == true -> "night"
            metric?.startsWith("blood_pressure_") == true -> "reading"
            metric == "steps" -> "daily record"
            else -> "record"
        }
        return if (count == 1) singular else "${singular}s"
    }

    private fun Int.asWords() = when (this) {
        0 -> "no"; 1 -> "one"; 2 -> "two"; 3 -> "three"; 4 -> "four"; else -> toString()
    }

    private fun HealthDomain.displayName() = name.lowercase().replace('_', ' ')
    private fun String.sentenceCase() = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    private fun String.lowercaseFirst() = replaceFirstChar { if (it.isUpperCase()) it.lowercase() else it.toString() }
    private fun String.normalizeAnswer() = trim()
        .replace(Regex("""[ \t]+"""), " ")
        .replace(Regex("""\s+([,.;:!?])"""), "$1")
        .replace(Regex("""([.!?]){2,}"""), "$1")

    private companion object {
        val labels = mapOf(
            "sleep_score" to "sleep score",
            "sleep_total_minutes" to "sleep duration",
            "sleep_deep_minutes" to "deep sleep",
            "sleep_continuity_score" to "sleep continuity",
            "sleep_awake_minutes" to "awake time",
            "resting_heart_rate_bpm" to "resting heart rate",
            "heart_rate_avg_bpm" to "average heart rate",
            "heart_rate_max_bpm" to "maximum heart rate",
            "blood_pressure_systolic_mmhg" to "systolic blood pressure",
            "blood_pressure_diastolic_mmhg" to "diastolic blood pressure",
            "blood_pressure_pulse_bpm" to "pulse",
            "body_temperature_celsius" to "body temperature",
            "blood_oxygen_percent" to "blood oxygen",
            "exercise_minutes" to "exercise time",
            "workout_volume" to "workout volume",
            "calories_burned_active_kcal" to "active calories",
            "steps" to "steps",
            "food_kcal" to "energy intake",
            "food_protein" to "protein",
            "food_carbs" to "carbohydrate",
            "food_fat" to "fat intake",
            "food_fibre" to "fibre",
            "water_total_l" to "water intake",
            "water_intake_ml" to "water intake",
            "emotional_valence" to "mood",
            "emotional_calmness" to "calmness",
            "emotional_energy" to "energy",
            "emotional_focus" to "focus",
            "environment_temperature_c" to "outdoor temperature",
            "environment_relative_humidity_pct" to "humidity",
            "environment_precipitation_mm" to "rainfall",
            "environment_uv_index" to "UV level",
            "environment_cloud_cover_pct" to "cloud cover",
            "environment_european_aqi" to "air quality",
            "body_weight_kg" to "body weight",
            "body_fat_pct" to "body fat",
            "body_muscle_mass_kg" to "muscle mass",
            "body_waist_cm" to "waist measurement",
            "mindfulness_session_minutes" to "mindfulness time"
        )
    }
}
