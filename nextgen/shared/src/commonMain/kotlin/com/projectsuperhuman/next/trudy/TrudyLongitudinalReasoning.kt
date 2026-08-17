package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Bounded longitudinal extensions around Trudy's existing planner, deterministic tools and Answer
 * Engine. No second reasoning engine is introduced here.
 */
enum class TrudyEventReferenceKind { PERSONAL_EVENT, CANONICAL_EXPERIMENT }

data class TrudyEventReference(
    val kind: TrudyEventReferenceKind,
    val label: String,
    val trigger: String
)

object TrudyEventReferenceParser {
    fun parse(text: String): TrudyEventReference? {
        val normalized = normalize(text)
        if (normalized.isBlank()) return null
        val trigger = EVENT_TRIGGERS.firstOrNull { it in normalized } ?: return null
        val tail = normalized.substringAfter(trigger).trim()
        val label = cleanEventLabel(tail).ifBlank { fallbackLabel(trigger, normalized) }
        val kind = if ("experiment" in label || "experiment" in normalized) {
            TrudyEventReferenceKind.CANONICAL_EXPERIMENT
        } else TrudyEventReferenceKind.PERSONAL_EVENT
        return TrudyEventReference(kind, label, trigger)
    }

    private fun fallbackLabel(trigger: String, normalized: String): String = when {
        "experiment" in normalized -> "the experiment"
        "cut" in trigger -> "the cut"
        "bulk" in trigger -> "the bulk"
        "got sick" in trigger -> "getting sick"
        "moved" in trigger -> "moving"
        else -> "that change"
    }

    private fun cleanEventLabel(value: String): String {
        var result = value.substringBefore('?').substringBefore('.').substringBefore(',').trim()
        STOP_AFTER.forEach { marker ->
            val index = result.indexOf(marker)
            if (index > 0) result = result.substring(0, index).trim()
        }
        return result.take(MAX_EVENT_LABEL_CHARS)
    }

    internal fun normalize(text: String): String = text.lowercase()
        .replace('’', '\'')
        .replace(Regex("[^a-z0-9' -]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private val EVENT_TRIGGERS = listOf(
        "since i started ", "since i've started ", "since i began ",
        "after i started ", "after i began ", "before i started ", "before i began ",
        "since i stopped ", "after i stopped ", "when i stopped ", "before i stopped ",
        "since i changed ", "after i changed ", "before i changed ",
        "since i got sick", "after i got sick", "before i got sick",
        "since i moved", "after i moved", "before i moved",
        "during the cut", "during my cut", "during the bulk", "during my bulk",
        "after the experiment", "before the experiment", "during the experiment"
    )
    private val STOP_AFTER = listOf(" and my ", " and the ", " but ", " because ", " while ", " when my ")
    private const val MAX_EVENT_LABEL_CHARS = 100
}

/** Prevents an undated event from being silently converted to a generic recent window. */
class TrudyLongitudinalPreflightPlanner(
    private val delegate: TrudyPreflightPlanner
) : TrudyPreflightPlanner {
    override fun plan(request: TrudyAskRequest): List<TrudyToolOperation> {
        if (TrudyEventReferenceParser.parse(request.userMessage) != null) {
            // This inexpensive lookup is also useful for non-explicit "experiment" wording: a
            // personal event can bind to a genuinely matching saved protocol by title/intervention.
            return listOf(GetCanonicalExperiments(TrudyCanonicalExperimentStatus.ANY, EVENT_LOOKUP_LIMIT))
        }

        if (isBareWhy(request.userMessage)) {
            val priorUser = request.conversationContext.asReversed().firstOrNull {
                it.role == TrudyConversationRole.USER && it.text.isNotBlank()
            }
            if (priorUser != null) {
                val inherited = "What might explain the previous finding? Previous question: ${priorUser.text}"
                    .take(MAX_REWRITTEN_QUERY_CHARS)
                return delegate.plan(request.copy(userMessage = inherited))
            }
        }

        val planned = delegate.plan(request)
        if (!asksForPhaseTiming(request.userMessage)) return planned

        // Keep the authoritative comparison/investigation, adding at most three bounded raw series
        // so the final response guard can run deterministic phase-shift detection.
        val raw = planned.asSequence().flatMap { operation ->
            when (operation) {
                is CompareBaseline -> sequenceOf(
                    TrudyToolOperation.GetMetricWindow(
                        operation.domain,
                        operation.metricId,
                        TrudyTimeRange(operation.baselineWindow.fromEpochMs, operation.observationWindow.toEpochMs),
                        PHASE_WINDOW_LIMIT
                    )
                )
                is InvestigateChange -> operation.targets.asSequence().take(MAX_PHASE_METRICS).map { target ->
                    TrudyToolOperation.GetMetricWindow(
                        target.domain,
                        target.metricId,
                        TrudyTimeRange(operation.baselineWindow.fromEpochMs, operation.observationWindow.toEpochMs),
                        PHASE_WINDOW_LIMIT
                    )
                }
                else -> emptySequence()
            }
        }.take(MAX_PHASE_METRICS).toList()
        return (planned + raw).distinct().take(MAX_PLANNED_OPERATIONS)
    }

    private fun isBareWhy(text: String): Boolean =
        TrudyEventReferenceParser.normalize(text) in setOf("why", "why is that", "why would that be", "how come", "and why")

    private fun asksForPhaseTiming(text: String): Boolean {
        val normalized = TrudyEventReferenceParser.normalize(text)
        return PHASE_TERMS.any { it in normalized }
    }

    private companion object {
        const val EVENT_LOOKUP_LIMIT = 20
        const val MAX_REWRITTEN_QUERY_CHARS = 1_000
        const val PHASE_WINDOW_LIMIT = 365
        const val MAX_PHASE_METRICS = 3
        const val MAX_PLANNED_OPERATIONS = 10
        val PHASE_TERMS = listOf(
            "when did", "when has", "when was the shift", "change point", "shifted", "started rising",
            "started falling", "started dropping", "started increasing", "started decreasing", "suddenly changed"
        )
    }
}

/**
 * Sits inside the existing Answer Engine boundary. It may request existing deterministic tools when
 * a saved event is resolved, but never calculates the final health conclusion itself.
 */
class TrudyLongitudinalModelClient(
    private val delegate: TrudyModelClient
) : TrudyModelClient {
    override suspend fun complete(request: TrudyModelRequest): TrudyModelResult {
        val event = TrudyEventReferenceParser.parse(request.userRequest)
        if (event != null && !hasCompletedEventAnalysis(request.toolResults)) {
            val canonical = request.toolResults.filterIsInstance<CanonicalExperimentsResult>().lastOrNull()
            if (canonical != null) {
                val experiment = TrudyCanonicalEventMatcher.match(event, canonical.experiments)
                if (experiment == null) {
                    return TrudyModelResult(
                        responseText = missingEventDateAnswer(event, canonical.persistenceState),
                        metadata = TrudyModelMetadata(
                            provider = "longitudinal-boundary",
                            model = "event-date-guard",
                            attributes = mapOf("eventDateResolved" to "false")
                        )
                    )
                }
                return TrudyModelResult(
                    requestedTools = listOf(buildEventAnalysis(request.userRequest, experiment)),
                    metadata = TrudyModelMetadata(
                        provider = "longitudinal-boundary",
                        model = "canonical-event-router",
                        attributes = mapOf("eventDateResolved" to "true", "experimentId" to experiment.id)
                    )
                )
            }
        }

        val hints = TrudyLongitudinalContextHints.build(request)
        return delegate.complete(
            if (hints.isBlank()) request else request.copy(
                systemInstruction = request.systemInstruction + "\n\n" + hints
            )
        )
    }

    private fun hasCompletedEventAnalysis(results: List<TrudyToolResult>): Boolean = results.any { result ->
        result is ChangeInvestigationResult || result is BaselineComparisonResult ||
            result is CanonicalExperimentEvaluationResult ||
            result is TrudyToolResult.Failure &&
            (result.operation is InvestigateChange || result.operation is CompareBaseline || result.operation is EvaluateCanonicalExperiment)
    }

    private fun buildEventAnalysis(text: String, experiment: TrudyCanonicalExperiment): TrudyToolOperation {
        val selected = TrudySystemCatalog.metricsMentioned(text)
        val intent = if (listOf("why", "explain", "could", "affect").any { it in text.lowercase() }) {
            TrudyInvestigationIntent.WHAT_MIGHT_EXPLAIN
        } else TrudyInvestigationIntent.WHAT_CHANGED
        val relevance = TrudyInvestigationRelevanceGraph.plan(text, selected, intent)
        val fallbackMetric = selected.firstOrNull()
            ?: TrudySystemCatalog.metric(experiment.targetDomain, experiment.targetMetricId)
        val targets = relevance.targets.ifEmpty {
            listOf(
                TrudyInvestigationMetric(
                    domain = experiment.targetDomain,
                    metricId = experiment.targetMetricId,
                    role = TrudyInvestigationRole.PRIMARY,
                    preference = fallbackMetric?.preference ?: TrudyMetricPreference.CONTEXT_DEPENDENT,
                    relevanceWeight = 1.0
                )
            )
        }
        val related = relevance.related.ifEmpty {
            experiment.secondaryMetrics.take(MAX_EVENT_RELATED).map { (domain, metric) ->
                TrudyInvestigationMetric(domain, metric, relevanceWeight = 0.65)
            }
        }
        val lookbackDays = daysSpanned(experiment.baselineWindow, experiment.interventionWindow)
        if (lookbackDays > MAX_INVESTIGATION_LOOKBACK_DAYS) {
            // The cross-domain investigator is intentionally capped at 90 days. Preserve the exact
            // event windows with a single deterministic comparison rather than widening that cap.
            val target = targets.first()
            return CompareBaseline(target.domain, target.metricId, experiment.interventionWindow, experiment.baselineWindow)
        }
        return InvestigateChange(
            targets = targets.take(MAX_EVENT_TARGETS),
            related = related.take(MAX_EVENT_RELATED),
            observationWindow = experiment.interventionWindow,
            baselineWindow = experiment.baselineWindow,
            claim = changeClaim(text),
            maxAssociations = related.size.coerceAtMost(MAX_EVENT_RELATED),
            intent = intent,
            targetLabel = "${relevance.targetLabel.ifBlank { humanMetricLabel(targets.first().metricId) }} around ${experiment.title}",
            includesSubjectiveClaim = SUBJECTIVE_TERMS.any { it in text.lowercase() },
            timeframeLabel = "after ${experiment.title}",
            timeframeExplicit = true,
            budget = TrudyInvestigationBudget(
                maxTargets = MAX_EVENT_TARGETS,
                maxRelatedSignals = MAX_EVENT_RELATED,
                maxRowsPerMetric = 256,
                maxLookbackDays = lookbackDays.coerceIn(7, MAX_INVESTIGATION_LOOKBACK_DAYS),
                maxImportantFindings = 5
            )
        )
    }

    private fun changeClaim(text: String): TrudyChangeClaim {
        val t = text.lowercase()
        return when {
            listOf("worse", "worsened", "declined", "more tired").any { it in t } -> TrudyChangeClaim.WORSENED
            listOf("better", "improved").any { it in t } -> TrudyChangeClaim.IMPROVED
            listOf("changed", "higher", "lower", "rose", "risen", "fell", "dropped", "increased", "decreased").any { it in t } ->
                TrudyChangeClaim.CHANGED
            else -> TrudyChangeClaim.UNSPECIFIED
        }
    }

    private fun daysSpanned(baseline: TrudyTimeRange, intervention: TrudyTimeRange): Int =
        (((max(baseline.toEpochMs, intervention.toEpochMs) - minOf(baseline.fromEpochMs, intervention.fromEpochMs))
            .coerceAtLeast(0L) / DAY_MS) + 1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private fun missingEventDateAnswer(
        event: TrudyEventReference,
        persistence: TrudyExperimentPersistenceState
    ): String {
        val repoNote = if (persistence == TrudyExperimentPersistenceState.NOT_CONNECTED) {
            " The Experiments screen is still presentation-only, so there isn't a canonical experiment timeline I can use instead."
        } else ""
        return "I can compare your data around ${event.label}, but Project Superhuman doesn't have a stored date for that event. " +
            "I won't substitute a generic recent window and pretend it's the same thing.$repoNote"
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val MAX_EVENT_TARGETS = 5
        const val MAX_EVENT_RELATED = 6
        const val MAX_INVESTIGATION_LOOKBACK_DAYS = 90
        val SUBJECTIVE_TERMS = listOf("feel", "felt", "awful", "terrible", "tired", "refreshed", "symptom")
    }
}

/** Matches event wording only against genuine canonical experiment fields. */
object TrudyCanonicalEventMatcher {
    fun match(event: TrudyEventReference, experiments: List<TrudyCanonicalExperiment>): TrudyCanonicalExperiment? {
        if (experiments.isEmpty()) return null
        val active = experiments.filter { it.status == TrudyCanonicalExperimentStatus.ACTIVE }
        if (event.kind == TrudyEventReferenceKind.CANONICAL_EXPERIMENT &&
            (event.label == "the experiment" || tokenise(event.label).isEmpty())
        ) return (active.ifEmpty { experiments }).maxByOrNull { it.updatedEpochMs }

        val eventTokens = tokenise(event.label)
        if (eventTokens.isEmpty()) return null
        return experiments.map { experiment ->
            val candidate = tokenise("${experiment.title} ${experiment.hypothesis} ${experiment.intervention}")
            val score = eventTokens.intersect(candidate).size.toDouble() / eventTokens.size.toDouble().coerceAtLeast(1.0)
            experiment to score
        }.sortedWith(
            compareByDescending<Pair<TrudyCanonicalExperiment, Double>> { it.second }
                .thenByDescending { it.first.status == TrudyCanonicalExperimentStatus.ACTIVE }
                .thenByDescending { it.first.updatedEpochMs }
        ).firstOrNull { it.second >= MIN_EVENT_MATCH }?.first
    }

    private fun tokenise(text: String): Set<String> = TrudyEventReferenceParser.normalize(text)
        .split(' ').asSequence().map(::stem)
        .filter { it.length >= 3 && it !in STOPWORDS }.toSet()

    private fun stem(token: String): String {
        val raw = when {
            token.length > 5 && token.endsWith("ing") -> token.dropLast(3)
            token.length > 4 && token.endsWith("ed") -> token.dropLast(2)
            token.length > 4 && token.endsWith("s") -> token.dropLast(1)
            else -> token
        }
        return if (raw.length >= 4 && raw.takeLast(2).let { it[0] == it[1] }) raw.dropLast(1) else raw
    }

    private val STOPWORDS = setOf(
        "the", "that", "this", "every", "daily", "started", "start", "began", "begin", "after", "before",
        "since", "during", "changed", "change", "experiment", "routine", "when"
    )
    private const val MIN_EVENT_MATCH = 0.34
}

data class TrudyDetectedPhaseShift(
    val domain: HealthDomain,
    val metricId: String,
    val shiftEpochMs: Long,
    val beforeMean: Double,
    val afterMean: Double,
    val relativeChange: Double,
    val standardizedChange: Double,
    val dailySampleCount: Int,
    val score: Double
)

/** Simple bounded daily change-point candidate detector; descriptive only. */
object TrudyPhaseShiftDetector {
    fun detect(rows: List<TrudyMetricEvidence>): TrudyDetectedPhaseShift? {
        val usable = rows.filter { it.value.isFinite() && it.timestampEpochMs >= 0L }
        val first = usable.firstOrNull() ?: return null
        if (usable.any { it.domain != first.domain || it.metricId != first.metricId }) return null
        val daily = usable.groupBy { it.timestampEpochMs / DAY_MS }.map { (_, dayRows) ->
            DayPoint(dayRows.maxOf { it.timestampEpochMs }, dayRows.map { it.value }.average())
        }.sortedBy { it.epochMs }
        if (daily.size < MIN_DAILY_POINTS) return null

        val overallSd = standardDeviation(daily.map { it.value })
        var best: TrudyDetectedPhaseShift? = null
        for (split in MIN_SIDE_POINTS..(daily.size - MIN_SIDE_POINTS)) {
            val before = daily.take(split)
            val after = daily.drop(split)
            val beforeMean = before.map { it.value }.average()
            val afterMean = after.map { it.value }.average()
            val delta = afterMean - beforeMean
            if (!delta.isFinite() || abs(delta) <= EPSILON) continue
            val scale = max(abs(beforeMean), MIN_SCALE)
            val relative = abs(delta) / scale
            val noiseFloor = max(overallSd, scale * MIN_NOISE_FRACTION).coerceAtLeast(MIN_SCALE)
            val standardized = abs(delta) / noiseFloor
            if (relative < MIN_RELATIVE_CHANGE && standardized < MIN_STANDARDIZED_CHANGE) continue
            val stability = postShiftStability(after.map { it.value }, noiseFloor)
            val score = (relative * 3.0).coerceAtMost(3.0) + standardized.coerceAtMost(3.0) + stability
            if (score < MIN_SCORE) continue
            val candidate = TrudyDetectedPhaseShift(
                first.domain, first.metricId, after.first().epochMs, beforeMean, afterMean,
                delta / scale, delta / noiseFloor, daily.size, score
            )
            if (best == null || candidate.score > best.score) best = candidate
        }
        return best
    }

    private fun postShiftStability(values: List<Double>, noiseFloor: Double): Double {
        if (values.size < MIN_SIDE_POINTS) return 0.0
        return (1.0 - (standardDeviation(values) / (noiseFloor * 2.0)).coerceIn(0.0, 1.0)) * 0.5
    }

    private fun standardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1).toDouble()
        return sqrt(variance.coerceAtLeast(0.0))
    }

    private data class DayPoint(val epochMs: Long, val value: Double)
    private const val DAY_MS = 86_400_000L
    private const val MIN_DAILY_POINTS = 8
    private const val MIN_SIDE_POINTS = 4
    private const val MIN_RELATIVE_CHANGE = 0.05
    private const val MIN_STANDARDIZED_CHANGE = 0.9
    private const val MIN_NOISE_FRACTION = 0.02
    private const val MIN_SCALE = 0.0001
    private const val MIN_SCORE = 1.25
    private const val EPSILON = 0.000001
}

/** High-signal guidance for hosted/local synthesis; all inputs were already deterministically read. */
object TrudyLongitudinalContextHints {
    fun build(request: TrudyModelRequest): String {
        val notes = mutableListOf<String>()
        request.toolResults.filterIsInstance<ChangeInvestigationResult>().lastOrNull()?.let { result ->
            val structured = result.investigation.structuredResult
            if (structured.importantFindings.size >= 2) {
                val changes = structured.importantFindings.sortedByDescending { it.priorityScore }.take(3)
                    .joinToString { "${humanMetricLabel(it.metricId)} ${if (it.absoluteDelta >= 0) "moved higher" else "moved lower"}" }
                notes += "Several tracked signals changed together: $changes. Synthesize them as one state where useful instead of dumping separate findings."
            }
            prioritizedMissing(result).take(2).takeIf { it.isNotEmpty() }?.let { missing ->
                notes += "The highest-value missing context is ${missing.joinToString { humanMetricLabel(it.second) }}. Mention only these missing pieces if they would change the conclusion."
            }
            if (isSubjectiveSleepConflict(request.userRequest, result)) {
                notes += "The user's subjective sleep experience conflicts with the tracked sleep direction. Preserve both: a wearable can estimate sleep patterns but cannot measure how restorative the night felt."
            }
        }
        sourceDisagreement(request.toolResults)?.let { notes += it.modelHint }
        clinicalBoundaryHint(request.toolResults)?.let(notes::add)
        if (notes.isEmpty()) return ""
        return buildString {
            append("LONGITUDINAL SYNTHESIS NOTES — internal guidance, never repeat this heading to the user:\n")
            notes.take(MAX_HINTS).forEach { append("- ").append(it).append('\n') }
        }.trim()
    }

    internal fun prioritizedMissing(result: ChangeInvestigationResult): List<Pair<HealthDomain, String>> {
        val weights = result.operation.related.associate { (it.domain to it.metricId) to it.relevanceWeight }
        return result.investigation.missingMetrics.distinct().sortedByDescending { weights[it] ?: 0.25 }
    }

    internal fun sourceDisagreement(results: List<TrudyToolResult>): SourceDisagreement? {
        val rows = results.flatMap(::metricRows)
        return rows.groupBy { it.domain to it.metricId }.values.asSequence().mapNotNull { series ->
            val latestBySource = series.filter { it.source.isNotBlank() }.groupBy { it.source }
                .mapValues { (_, values) -> values.maxByOrNull { it.timestampEpochMs }!! }
            if (latestBySource.size < 2) return@mapNotNull null
            val points = latestBySource.values.sortedByDescending { it.timestampEpochMs }.take(3)
            if (points.maxOf { it.timestampEpochMs } - points.minOf { it.timestampEpochMs } > SOURCE_WINDOW_MS) return@mapNotNull null
            val min = points.minOf { it.value }
            val max = points.maxOf { it.value }
            val scale = max(abs(points.map { it.value }.average()), 1.0)
            if (max - min <= max(absoluteTolerance(points.first().metricId), scale * 0.03)) return@mapNotNull null
            SourceDisagreement(
                points.first().domain,
                points.first().metricId,
                points.map { friendlySource(it.source) }.distinct(),
                "Multiple sources disagree for ${humanMetricLabel(points.first().metricId)} within roughly the same day (${points.joinToString { "${friendlySource(it.source)}=${compactNumber(it.value)}" }}). Preserve the source distinction and do not silently average them into one fact."
            )
        }.firstOrNull()
    }

    private fun clinicalBoundaryHint(results: List<TrudyToolResult>): String? {
        val clinical = results.flatMap(::metricRows).filter { it.domain == HealthDomain.CLINICAL }
        if (clinical.isEmpty()) return null
        if (clinical.any { it.metricId.startsWith("clinical.condition.") || it.metadata["profileType"] == "chronic-condition" }) {
            return "Clinical condition records here are recorded Project Superhuman conditions. Do not call them clinician-confirmed unless explicit provenance says so, and do not convert symptoms or lab values into diagnoses."
        }
        if (clinical.any { row -> row.metadata.keys.any { it in setOf("rangeLow", "rangeHigh", "status", "rangeSource") } }) {
            return "Clinical values here are recorded lab results. An out-of-range marker is a measurement finding, not a diagnosis."
        }
        return null
    }

    private fun isSubjectiveSleepConflict(text: String, result: ChangeInvestigationResult): Boolean {
        val t = text.lowercase()
        return listOf("awful", "terrible", "unrested", "not refreshed", "felt worse", "feel worse").any { it in t } &&
            listOf("sleep", "slept", "night").any { it in t } &&
            (result.investigation.premiseAssessment == TrudyPremiseAssessment.NOT_SUPPORTED ||
                result.investigation.premiseAssessment == TrudyPremiseAssessment.MIXED)
    }

    internal fun metricRows(result: TrudyToolResult): List<TrudyMetricEvidence> = when (result) {
        is TrudyToolResult.DomainState -> result.evidence
        is TrudyToolResult.MetricHistory -> result.evidence
        is TrudyToolResult.MetricWindow -> result.evidence
        is TrudyToolResult.DomainHistory -> result.evidence
        is TrudyToolResult.Context -> result.context.domains.flatMap { it.currentState + it.history }
        else -> emptyList()
    }

    private fun absoluteTolerance(metric: String): Double = when {
        "heart_rate" in metric || metric.endsWith("_bpm") -> 4.0
        "oxygen" in metric || "spo2" in metric -> 2.0
        "blood_pressure" in metric || metric.endsWith("_mmhg") -> 5.0
        "body_weight" in metric || metric.endsWith("_kg") -> 0.5
        "body_fat" in metric -> 1.0
        "sleep_score" in metric -> 4.0
        metric == "steps" || metric.endsWith("_steps") -> 500.0
        else -> 0.01
    }

    internal fun friendlySource(source: String): String {
        val s = source.lowercase()
        return when {
            "h19c" in s -> "H19C"
            "health-connect" in s || "health connect" in s || "healthconnect" in s -> "Health Connect"
            "samsung" in s -> "Samsung wearable"
            "okok" in s || "scale" in s -> "smart scale"
            "manual" in s -> "manual entry"
            else -> source.take(40)
        }
    }

    internal fun compactNumber(value: Double): String =
        if (abs(value) >= 100.0) value.toInt().toString() else ((value * 10.0).toInt() / 10.0).toString()

    data class SourceDisagreement(
        val domain: HealthDomain,
        val metricId: String,
        val sources: List<String>,
        val modelHint: String
    )

    private const val SOURCE_WINDOW_MS = 24L * 3_600_000L
    private const val MAX_HINTS = 5
}

/**
 * Final constrained wording guard. It naturalizes a small set of internal phrases and can append
 * deterministic conflict/provenance/missing-context notes; it does not replace answer synthesis.
 */
class TrudyLongitudinalResponseGuard(
    private val delegate: TrudyModelClient,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() }
) : TrudyModelClient {
    override suspend fun complete(request: TrudyModelRequest): TrudyModelResult {
        val result = delegate.complete(request)
        if (result.requestedTools.isNotEmpty() || result.responseText.isNullOrBlank()) return result
        var answer = naturalize(result.responseText.trim())
        if (looksLikeEmergency(answer)) return result.copy(responseText = answer)

        val additions = mutableListOf<String>()
        val investigation = request.toolResults.filterIsInstance<ChangeInvestigationResult>().lastOrNull()
        if (asksForPhaseTiming(request.userRequest)) {
            bestShift(request)?.let { shift ->
                val daysAgo = ((nowEpochMs() - shift.shiftEpochMs).coerceAtLeast(0L) / DAY_MS).toInt()
                additions += "The clearest shift I can see is about ${daysAgo.coerceAtLeast(0)} days ago: ${humanMetricLabel(shift.metricId).lowercase()} moved from roughly ${TrudyLongitudinalContextHints.compactNumber(shift.beforeMean)} to ${TrudyLongitudinalContextHints.compactNumber(shift.afterMean)}."
            } ?: run {
                additions += "I don't see a clean single break point in the available measurements; the change may be gradual or too noisy to date confidently."
            }
        }
        if (investigation != null && subjectiveSleepConflict(request.userRequest, investigation) && "wearable" !in answer.lowercase()) {
            additions += "Your tracked sleep metrics and how you felt don't fully line up here; a wearable can estimate sleep patterns, but it can't measure how restorative the night felt."
        }
        if (investigation != null && shouldExplainMissing(request.userRequest)) {
            val missing = TrudyLongitudinalContextHints.prioritizedMissing(investigation).take(2)
            if (missing.isNotEmpty() && missing.none { humanMetricLabel(it.second).lowercase() in answer.lowercase() }) {
                additions += "The most useful missing context is ${naturalList(missing.map { humanMetricLabel(it.second).lowercase() })}; that would help separate the leading explanations."
            }
        }
        TrudyLongitudinalContextHints.sourceDisagreement(request.toolResults)?.let { disagreement ->
            if (disagreement.sources.none { it.lowercase() in answer.lowercase() }) {
                additions += "I also have different same-day ${humanMetricLabel(disagreement.metricId).lowercase()} readings from ${naturalList(disagreement.sources)}; I wouldn't collapse those into one number."
            }
        }
        provenanceBoundary(request)?.let { boundary ->
            if (boundary.keywords.none { it in answer.lowercase() }) additions += boundary.text
        }
        if (investigation != null && investigation.investigation.structuredResult.importantFindings.size >= 3 &&
            listOf("why", "what changed", "explain").any { it in request.userRequest.lowercase() } &&
            "changed together" !in answer.lowercase()
        ) {
            val grouped = investigation.investigation.structuredResult.importantFindings
                .sortedByDescending { it.priorityScore }.take(3)
                .map { "${humanMetricLabel(it.metricId).lowercase()} moved ${if (it.absoluteDelta >= 0) "higher" else "lower"}" }
            additions += "Several signals changed together: ${naturalList(grouped)}."
        }
        additions.distinct().take(MAX_ADDITIONS).forEach { sentence ->
            if (sentence.lowercase() !in answer.lowercase()) answer += " $sentence"
        }
        return result.copy(responseText = answer.trim())
    }

    private fun bestShift(request: TrudyModelRequest): TrudyDetectedPhaseShift? {
        val preferred = TrudySystemCatalog.metricsMentioned(request.userRequest).map { it.domain to it.metricId }.toSet()
        val candidates = request.toolResults.flatMap(TrudyLongitudinalContextHints::metricRows)
            .groupBy { it.domain to it.metricId }.mapNotNull { (_, rows) -> TrudyPhaseShiftDetector.detect(rows) }
        return candidates.sortedWith(
            compareByDescending<TrudyDetectedPhaseShift> { (it.domain to it.metricId) in preferred }
                .thenByDescending { it.score }
        ).firstOrNull()
    }

    private fun provenanceBoundary(request: TrudyModelRequest): ProvenanceBoundary? {
        if (!asksForDirectReading(request.userRequest)) return null
        val row = request.toolResults.flatMap(TrudyLongitudinalContextHints::metricRows)
            .maxByOrNull { it.timestampEpochMs } ?: return null
        val source = row.source.lowercase()
        val capture = row.metadata["trudyCaptureKind"]?.lowercase()
        return when {
            row.metricId.startsWith("clinical.condition.") || row.metadata["profileType"] == "chronic-condition" ->
                ProvenanceBoundary(
                    "I'm treating that as a condition recorded in Project Superhuman, not as proof of clinician confirmation.",
                    listOf("recorded condition", "clinician-confirmed", "clinician confirmed")
                )
            row.domain == HealthDomain.CLINICAL && row.metadata.keys.any { it in setOf("rangeLow", "rangeHigh", "status") } ->
                ProvenanceBoundary(
                    "That is a recorded lab result; being outside its recorded reference range would not by itself establish a diagnosis.",
                    listOf("lab result", "reference range")
                )
            (capture == "device" || "scale" in source || "okok" in source) && "body_fat" in row.metricId ->
                ProvenanceBoundary(
                    "That body-fat value is a scale estimate rather than a direct clinical measurement.",
                    listOf("scale estimate", "estimated")
                )
            capture == "wearable" || "h19c" in source || "health-connect" in source || "samsung" in source ->
                ProvenanceBoundary(
                    "That came from a wearable source rather than a clinical measurement.",
                    listOf("watch", "wearable")
                )
            else -> null
        }
    }

    private fun naturalize(text: String): String = text
        .replace("No clear association was detected.", "I don't see a consistent link between the two in your data.", ignoreCase = true)
        .replace("no clear association was detected", "I don't see a consistent link between the two in your data", ignoreCase = true)
        .replace("insufficient aligned samples", "not enough days with both measurements", ignoreCase = true)
        .replace("bounded evidence", "available data", ignoreCase = true)
        .replace("structured investigation", "comparison", ignoreCase = true)
        .replace("data quality status", "data coverage", ignoreCase = true)
        .replace("the observation window", "that period", ignoreCase = true)
        .replace("the target metric", "the main measure", ignoreCase = true)
        .replace("records indicate", "your data suggests", ignoreCase = true)

    private fun subjectiveSleepConflict(text: String, result: ChangeInvestigationResult): Boolean {
        val t = text.lowercase()
        return listOf("sleep", "slept", "night").any { it in t } &&
            listOf("awful", "terrible", "unrested", "not refreshed", "felt worse", "feel worse").any { it in t } &&
            (result.investigation.premiseAssessment == TrudyPremiseAssessment.NOT_SUPPORTED ||
                result.investigation.premiseAssessment == TrudyPremiseAssessment.MIXED)
    }

    private fun shouldExplainMissing(text: String): Boolean {
        val t = text.lowercase()
        return listOf("why", "explain", "missing", "what information", "what data", "what would help").any { it in t }
    }

    private fun asksForPhaseTiming(text: String): Boolean {
        val t = TrudyEventReferenceParser.normalize(text)
        return listOf("when did", "when has", "change point", "started rising", "started falling", "started dropping", "started increasing", "started decreasing", "suddenly changed").any { it in t }
    }

    private fun asksForDirectReading(text: String): Boolean {
        val t = text.lowercase()
        return listOf("what was my", "what is my", "reading", "show me my", "how high was", "how low was").any { it in t }
    }

    private fun looksLikeEmergency(text: String): Boolean {
        val t = text.lowercase()
        return "emergency" in t || "call 112" in t || "call 999" in t || "call 911" in t
    }

    private fun naturalList(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        2 -> "${items[0]} and ${items[1]}"
        else -> items.dropLast(1).joinToString(", ") + ", and " + items.last()
    }

    private data class ProvenanceBoundary(val text: String, val keywords: List<String>)
    private companion object {
        const val DAY_MS = 86_400_000L
        const val MAX_ADDITIONS = 3
    }
}
