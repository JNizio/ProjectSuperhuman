package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlin.math.abs

enum class TrudyMetricPreference { HIGHER_IS_FAVOURABLE, LOWER_IS_FAVOURABLE, CONTEXT_DEPENDENT }
enum class TrudyInvestigationRole { PRIMARY, SUPPORTING }
enum class TrudyChangeClaim { WORSENED, IMPROVED, CHANGED, UNSPECIFIED }
enum class TrudyPremiseAssessment { SUPPORTED, NOT_SUPPORTED, MIXED, INSUFFICIENT }

data class TrudySystemMetric(
    val domain: HealthDomain,
    val metricId: String,
    val aliases: Set<String>,
    val preference: TrudyMetricPreference = TrudyMetricPreference.CONTEXT_DEPENDENT
)

data class TrudySystemModule(
    val id: String,
    val aliases: Set<String>,
    val metrics: List<TrudySystemMetric>,
    val canonicalDataAvailable: Boolean = true,
    val unavailableReason: String? = null
)

/**
 * One semantic map between user language and canonical Data Vault coordinates.
 * It contains identifiers only; scientific or medical claims belong to the knowledge providers.
 */
object TrudySystemCatalog {
    val modules: List<TrudySystemModule> = listOf(
        module("sleep", setOf("sleep", "slept", "bedtime", "night"),
            metric(HealthDomain.SLEEP, "sleep_score", setOf("sleep quality", "sleep score"), TrudyMetricPreference.HIGHER_IS_FAVOURABLE),
            metric(HealthDomain.SLEEP, "sleep_total_minutes", setOf("sleep duration", "time asleep"), TrudyMetricPreference.HIGHER_IS_FAVOURABLE),
            metric(HealthDomain.SLEEP, "sleep_deep_minutes", setOf("deep sleep"), TrudyMetricPreference.HIGHER_IS_FAVOURABLE),
            metric(HealthDomain.SLEEP, "sleep_continuity_score", setOf("sleep continuity"), TrudyMetricPreference.HIGHER_IS_FAVOURABLE),
            metric(HealthDomain.SLEEP, "sleep_awake_minutes", setOf("awake time", "wakefulness"), TrudyMetricPreference.LOWER_IS_FAVOURABLE)),
        module("vitals", setOf("vitals", "vital signs"),
            metric(HealthDomain.EXERCISE, "resting_heart_rate_bpm", setOf("resting heart rate")),
            metric(HealthDomain.BLOOD_PRESSURE, "blood_pressure_systolic_mmhg", setOf("systolic")),
            metric(HealthDomain.BLOOD_PRESSURE, "blood_pressure_diastolic_mmhg", setOf("diastolic")),
            metric(HealthDomain.BODY, "body_temperature_celsius", setOf("body temperature")),
            metric(HealthDomain.BODY, "blood_oxygen_percent", setOf("blood oxygen", "spo2"))),
        module("heart_rate", setOf("heart rate", "pulse", "resting hr", "bpm"),
            metric(HealthDomain.EXERCISE, "resting_heart_rate_bpm", setOf("resting heart rate", "resting hr")),
            metric(HealthDomain.EXERCISE, "heart_rate_avg_bpm", setOf("average heart rate", "heart rate")),
            metric(HealthDomain.EXERCISE, "heart_rate_max_bpm", setOf("maximum heart rate", "max heart rate"))),
        module("blood_pressure", setOf("blood pressure", "systolic", "diastolic", "bp"),
            metric(HealthDomain.BLOOD_PRESSURE, "blood_pressure_systolic_mmhg", setOf("systolic")),
            metric(HealthDomain.BLOOD_PRESSURE, "blood_pressure_diastolic_mmhg", setOf("diastolic")),
            metric(HealthDomain.BLOOD_PRESSURE, "blood_pressure_pulse_bpm", setOf("blood pressure pulse"))),
        module("body_temperature", setOf("body temperature", "temperature reading", "fever"),
            metric(HealthDomain.BODY, "body_temperature_celsius", setOf("body temperature", "temperature"))),
        module("exercise", setOf("exercise", "workout", "training", "gym", "activity"),
            metric(HealthDomain.EXERCISE, "exercise_minutes", setOf("exercise minutes", "activity"), TrudyMetricPreference.HIGHER_IS_FAVOURABLE),
            metric(HealthDomain.EXERCISE, "workout_volume", setOf("training volume", "workout volume")),
            metric(HealthDomain.EXERCISE, "calories_burned_active_kcal", setOf("active calories"))),
        module("steps", setOf("steps", "walking", "walked"),
            metric(HealthDomain.EXERCISE, "steps", setOf("steps", "step count"), TrudyMetricPreference.HIGHER_IS_FAVOURABLE)),
        module("nutrition", setOf("nutrition", "food", "diet", "meal", "calories", "protein", "caffeine"),
            metric(HealthDomain.NUTRITION, "food_kcal", setOf("calories", "energy intake")),
            metric(HealthDomain.NUTRITION, "food_protein", setOf("protein")),
            metric(HealthDomain.NUTRITION, "food_carbs", setOf("carbs", "carbohydrate")),
            metric(HealthDomain.NUTRITION, "food_fat", setOf("fat intake")),
            metric(HealthDomain.NUTRITION, "food_fibre", setOf("fibre", "fiber")),
            metric(HealthDomain.NUTRITION, "food_caffeine_mg", setOf("caffeine", "caffeine intake", "coffee"), TrudyMetricPreference.LOWER_IS_FAVOURABLE)),
        module("hydration", setOf("hydration", "water", "fluids", "drank"),
            metric(HealthDomain.HYDRATION, "water_total_l", setOf("water total", "hydration"), TrudyMetricPreference.HIGHER_IS_FAVOURABLE),
            metric(HealthDomain.HYDRATION, "water_intake_ml", setOf("water intake"), TrudyMetricPreference.HIGHER_IS_FAVOURABLE)),
        module("emotional", setOf("mood", "stress", "anxiety", "anxious", "calm", "energy", "tired", "focus", "emotional", "felt"),
            metric(HealthDomain.EMOTIONAL, "emotional_valence", setOf("mood", "valence"), TrudyMetricPreference.HIGHER_IS_FAVOURABLE),
            metric(HealthDomain.EMOTIONAL, "emotional_calmness", setOf("stress", "anxiety", "calmness"), TrudyMetricPreference.HIGHER_IS_FAVOURABLE),
            metric(HealthDomain.EMOTIONAL, "emotional_energy", setOf("energy", "tired", "fatigue"), TrudyMetricPreference.HIGHER_IS_FAVOURABLE),
            metric(HealthDomain.EMOTIONAL, "emotional_focus", setOf("focus", "concentration"), TrudyMetricPreference.HIGHER_IS_FAVOURABLE)),
        module("environment", setOf("environment", "weather", "temperature outside", "humidity", "rain", "bright", "daylight", "air quality"),
            metric(HealthDomain.ENVIRONMENT, "environment_temperature_c", setOf("weather", "temperature", "hot", "cold")),
            metric(HealthDomain.ENVIRONMENT, "environment_relative_humidity_pct", setOf("humidity", "humid")),
            metric(HealthDomain.ENVIRONMENT, "environment_precipitation_mm", setOf("rain", "precipitation")),
            metric(HealthDomain.ENVIRONMENT, "environment_uv_index", setOf("uv", "bright", "sunlight")),
            metric(HealthDomain.ENVIRONMENT, "environment_cloud_cover_pct", setOf("cloud", "cloudy")),
            metric(HealthDomain.ENVIRONMENT, "environment_european_aqi", setOf("air quality", "aqi"))),
        module("body", setOf("body composition", "weight", "body fat", "muscle", "bmi", "waist"),
            metric(HealthDomain.BODY, "body_weight_kg", setOf("weight")),
            metric(HealthDomain.BODY, "body_fat_pct", setOf("body fat"), TrudyMetricPreference.LOWER_IS_FAVOURABLE),
            metric(HealthDomain.BODY, "body_muscle_mass_kg", setOf("muscle mass"), TrudyMetricPreference.HIGHER_IS_FAVOURABLE),
            metric(HealthDomain.BODY, "body_waist_cm", setOf("waist"), TrudyMetricPreference.LOWER_IS_FAVOURABLE)),
        module("clinical", setOf("clinical", "blood test", "lab", "marker", "result")),
        module("mindfulness", setOf("mindfulness", "meditation", "meditated"),
            metric(HealthDomain.MINDFULNESS, "mindfulness_session_minutes", setOf("mindfulness", "meditation"), TrudyMetricPreference.HIGHER_IS_FAVOURABLE),
            metric(HealthDomain.MINDFULNESS, "stress_before", setOf("stress before"), TrudyMetricPreference.LOWER_IS_FAVOURABLE),
            metric(HealthDomain.MINDFULNESS, "stress_after", setOf("stress after"), TrudyMetricPreference.LOWER_IS_FAVOURABLE)),
        TrudySystemModule(
            id = "breathwork",
            aliases = setOf("breathwork", "breathing exercise", "deep breathing"),
            metrics = emptyList(),
            canonicalDataAvailable = false,
            unavailableReason = "Breathwork has no canonical persisted Data Vault metric yet."
        ),
        TrudySystemModule(
            id = "symptoms",
            aliases = setOf("symptom", "symptoms", "pain", "headache", "nausea"),
            metrics = emptyList(),
            canonicalDataAvailable = false,
            unavailableReason = "No canonical personal symptom-history port is connected yet."
        )
    )

    fun modulesMentioned(text: String): List<TrudySystemModule> {
        val normalized = text.lowercase()
        return modules.filter { module -> module.aliases.any { alias -> alias in normalized } }
    }

    fun metricsMentioned(text: String, modules: List<TrudySystemModule> = modulesMentioned(text)): List<TrudySystemMetric> {
        val normalized = text.lowercase()
        return modules.flatMap { module ->
            module.metrics.filter { metric -> metric.aliases.any { it in normalized } }
                .ifEmpty { module.metrics.take(DEFAULT_METRICS_PER_MODULE) }
        }.distinct().take(MAX_METRICS_PER_REQUEST)
    }

    fun metric(domain: HealthDomain, metricId: String): TrudySystemMetric? =
        modules.asSequence().flatMap { it.metrics.asSequence() }
            .firstOrNull { it.domain == domain && it.metricId == metricId }

    private fun metric(
        domain: HealthDomain,
        id: String,
        aliases: Set<String>,
        preference: TrudyMetricPreference = TrudyMetricPreference.CONTEXT_DEPENDENT
    ) = TrudySystemMetric(domain, id, aliases, preference)

    private fun module(id: String, aliases: Set<String>, vararg metrics: TrudySystemMetric) =
        TrudySystemModule(id, aliases, metrics.toList())

    private const val DEFAULT_METRICS_PER_MODULE = 4
    private const val MAX_METRICS_PER_REQUEST = 10
}

interface TrudyTemporalBoundaryProvider {
    fun nowEpochMs(): Long
    fun startOfTodayEpochMs(): Long
    fun startOfWeekEpochMs(): Long
    fun startOfMonthEpochMs(monthsAgo: Int = 0): Long
}

/** UTC fallback for shared tests/offline wiring. Android injects device-local calendar boundaries. */
class UtcTrudyTemporalBoundaryProvider(
    private val now: () -> Long = { System.currentTimeMillis() }
) : TrudyTemporalBoundaryProvider {
    override fun nowEpochMs(): Long = now().coerceAtLeast(0L)
    override fun startOfTodayEpochMs(): Long = nowEpochMs().floorDiv(DAY_MS) * DAY_MS
    override fun startOfWeekEpochMs(): Long {
        val day = startOfTodayEpochMs().floorDiv(DAY_MS)
        val daysSinceMonday = (day + 3L).mod(7L)
        return (day - daysSinceMonday) * DAY_MS
    }
    override fun startOfMonthEpochMs(monthsAgo: Int): Long {
        val current = civilFromEpochDay(startOfTodayEpochMs().floorDiv(DAY_MS))
        val totalMonths = current.year.toLong() * 12L + current.month - 1L - monthsAgo.coerceAtLeast(0)
        val year = totalMonths.floorDiv(12L).toInt()
        val month = totalMonths.mod(12L).toInt() + 1
        return epochDayFromCivil(year, month, 1) * DAY_MS
    }

    private fun civilFromEpochDay(epochDay: Long): CivilDate {
        val shifted = epochDay + 719_468L
        val era = shifted.floorDiv(146_097L)
        val dayOfEra = shifted - era * 146_097L
        val yearOfEra = (dayOfEra - dayOfEra / 1_460L + dayOfEra / 36_524L - dayOfEra / 146_096L) / 365L
        var year = yearOfEra + era * 400L
        val dayOfYear = dayOfEra - (365L * yearOfEra + yearOfEra / 4L - yearOfEra / 100L)
        val monthPrime = (5L * dayOfYear + 2L) / 153L
        val month = monthPrime + if (monthPrime < 10L) 3L else -9L
        year += if (month <= 2L) 1L else 0L
        return CivilDate(year.toInt(), month.toInt())
    }

    private fun epochDayFromCivil(yearValue: Int, month: Int, day: Int): Long {
        var year = yearValue.toLong()
        year -= if (month <= 2) 1L else 0L
        val era = year.floorDiv(400L)
        val yearOfEra = year - era * 400L
        val monthPrime = month + if (month > 2) -3 else 9
        val dayOfYear = (153L * monthPrime + 2L) / 5L + day - 1L
        val dayOfEra = yearOfEra * 365L + yearOfEra / 4L - yearOfEra / 100L + dayOfYear
        return era * 146_097L + dayOfEra - 719_468L
    }

    private data class CivilDate(val year: Int, val month: Int)

    private companion object { const val DAY_MS = 86_400_000L }
}

data class TrudyResolvedTimeframe(
    val observation: TrudyTimeRange,
    val baseline: TrudyTimeRange,
    val label: String,
    val explicit: Boolean
)

class TrudyTemporalResolver(private val boundaries: TrudyTemporalBoundaryProvider) {
    fun resolve(message: String, conversation: List<TrudyConversationTurn> = emptyList()): TrudyResolvedTimeframe {
        val current = message.lowercase()
        val inherited = if (containsTemporalPhrase(current)) current else conversation.asReversed()
            .asSequence().map { it.text.lowercase() }.firstOrNull(::containsTemporalPhrase).orEmpty()
        val text = if (containsTemporalPhrase(current)) current else inherited
        val now = boundaries.nowEpochMs()
        val today = boundaries.startOfTodayEpochMs()
        val week = boundaries.startOfWeekEpochMs()
        val month = boundaries.startOfMonthEpochMs()

        fun prior(range: TrudyTimeRange): TrudyTimeRange {
            val duration = (range.toEpochMs - range.fromEpochMs + 1L).coerceAtLeast(1L)
            val end = (range.fromEpochMs - 1L).coerceAtLeast(0L)
            return TrudyTimeRange((end - duration + 1L).coerceAtLeast(0L), end)
        }
        val observation = when {
            "since yesterday" in text -> TrudyTimeRange((today - DAY_MS).coerceAtLeast(0L), now)
            "yesterday" in text -> TrudyTimeRange((today - DAY_MS).coerceAtLeast(0L), (today - 1L).coerceAtLeast(0L))
            "last week" in text -> TrudyTimeRange((week - 7L * DAY_MS).coerceAtLeast(0L), (week - 1L).coerceAtLeast(0L))
            "this week" in text -> TrudyTimeRange(week.coerceAtLeast(0L), now)
            "last month" in text -> TrudyTimeRange(boundaries.startOfMonthEpochMs(1), (month - 1L).coerceAtLeast(0L))
            "this month" in text -> TrudyTimeRange(month.coerceAtLeast(0L), now)
            "last few weeks" in text || "few weeks" in text -> TrudyTimeRange((now - 21L * DAY_MS).coerceAtLeast(0L), now)
            "recently" in text || "recent" in text -> TrudyTimeRange((now - 7L * DAY_MS).coerceAtLeast(0L), now)
            "today" in text -> TrudyTimeRange(today.coerceAtLeast(0L), now)
            else -> TrudyTimeRange((now - 7L * DAY_MS).coerceAtLeast(0L), now)
        }
        val baseline = when {
            "last month" in text -> TrudyTimeRange(boundaries.startOfMonthEpochMs(2), (boundaries.startOfMonthEpochMs(1) - 1L).coerceAtLeast(0L))
            else -> prior(observation)
        }
        return TrudyResolvedTimeframe(
            observation = observation,
            baseline = baseline,
            label = when {
                "yesterday" in text -> "yesterday"
                "last week" in text -> "last week"
                "this week" in text -> "this week"
                "last month" in text -> "last month"
                "this month" in text -> "this month"
                "few weeks" in text -> "the last few weeks"
                "recent" in text -> "recently"
                "today" in text -> "today"
                else -> "the last 7 days"
            },
            explicit = containsTemporalPhrase(current)
        )
    }

    private fun containsTemporalPhrase(text: String): Boolean = TEMPORAL_PHRASES.any { it in text }

    private companion object {
        const val DAY_MS = 86_400_000L
        val TEMPORAL_PHRASES = listOf("today", "yesterday", "this week", "last week", "recently", "recent", "few weeks", "this month", "last month", "since yesterday")
    }
}

data class TrudyInvestigationMetric(
    val domain: HealthDomain,
    val metricId: String,
    val role: TrudyInvestigationRole = TrudyInvestigationRole.SUPPORTING,
    val preference: TrudyMetricPreference = TrudyMetricPreference.CONTEXT_DEPENDENT,
    val temporalAlignment: TrudyTemporalAlignment = TrudyTemporalAlignment.SAME_DAY,
    val relevanceWeight: Double = 0.5
) {
    init {
        require(metricId.isNotBlank())
        require(relevanceWeight in 0.0..1.0)
    }
}

data class InvestigateChange(
    val targets: List<TrudyInvestigationMetric>,
    val related: List<TrudyInvestigationMetric>,
    val observationWindow: TrudyTimeRange,
    val baselineWindow: TrudyTimeRange,
    val claim: TrudyChangeClaim = TrudyChangeClaim.UNSPECIFIED,
    val maxAssociations: Int = 6,
    val intent: TrudyInvestigationIntent = TrudyInvestigationIntent.WHY,
    val targetLabel: String? = null,
    val includesSubjectiveClaim: Boolean = false,
    val timeframeLabel: String = "requested period",
    val timeframeExplicit: Boolean = false,
    val budget: TrudyInvestigationBudget = TrudyInvestigationBudget()
) : TrudyToolOperation {
    override val domains: List<HealthDomain> = (targets + related).map { it.domain }.distinct()
}

data class TrudyChangeInvestigation(
    val premiseAssessment: TrudyPremiseAssessment,
    val observationWindow: TrudyTimeRange,
    val baselineWindow: TrudyTimeRange,
    val targetComparisons: List<TrudyBaselineComparison>,
    val relatedAssociations: List<TrudyAssociationResult>,
    val missingMetrics: List<Pair<HealthDomain, String>>,
    val caveats: List<String>,
    val structuredResult: TrudyInvestigationResult
)

data class ChangeInvestigationResult(
    override val operation: InvestigateChange,
    val investigation: TrudyChangeInvestigation
) : TrudyToolResult

class TrudyCrossDomainInvestigator(
    private val library: TrudyPersonalEvidenceLibrary,
    private val source: TrudyPersonalEvidenceSource? = null
) {
    suspend fun investigate(operation: InvestigateChange): TrudyChangeInvestigation {
        require(operation.targets.isNotEmpty())
        require(operation.maxAssociations in 0..MAX_ASSOCIATIONS)
        require(operation.targets.size <= operation.budget.maxTargets)
        require(operation.related.size <= operation.budget.maxRelatedSignals)
        val lookbackMs = operation.observationWindow.toEpochMs - operation.baselineWindow.fromEpochMs
        require(lookbackMs >= 0L && lookbackMs <= operation.budget.maxLookbackDays.toLong() * DAY_MS) {
            "Investigation exceeds its bounded lookback"
        }

        val comparisons = operation.targets.distinctBy { it.domain to it.metricId }.map { target ->
            library.compareBaseline(
                target.domain,
                target.metricId,
                operation.observationWindow,
                operation.baselineWindow,
                operation.budget.maxRowsPerMetric
            )
        }
        val primary = operation.targets.firstOrNull { it.role == TrudyInvestigationRole.PRIMARY }
            ?: operation.targets.first()
        val combinedWindow = TrudyTimeRange(operation.baselineWindow.fromEpochMs, operation.observationWindow.toEpochMs)
        val evaluatedAssociations = operation.related.distinctBy { it.domain to it.metricId }
            .filterNot { it.domain == primary.domain && it.metricId == primary.metricId }
            .take(minOf(operation.maxAssociations, operation.budget.maxRelatedSignals))
            .map { related ->
                related to library.association(
                    leftDomain = related.domain,
                    leftMetricId = related.metricId,
                    rightDomain = primary.domain,
                    rightMetricId = primary.metricId,
                    lagMs = related.temporalAlignment.lagMs,
                    alignmentWindowMs = related.temporalAlignment.toleranceMs,
                    limit = operation.budget.maxRowsPerMetric,
                    window = combinedWindow,
                    rollingWindowMs = if (related.temporalAlignment.kind == TrudyTemporalAlignmentKind.ROLLING_AVERAGE) {
                        related.temporalAlignment.rollingDays.toLong() * DAY_MS
                    } else null
                )
            }
        val associations = evaluatedAssociations.map { it.second }
        val missing = buildList {
            comparisons.filter { it.observationMean == null || it.baselineMean == null }
                .forEach { add(it.domain to it.metricId) }
            evaluatedAssociations.filter { it.second.coefficient == null }
                .forEach { add(it.first.domain to it.first.metricId) }
        }.distinct()
        val premise = assessPremise(operation, comparisons)
        val structured = TrudyInvestigationResultAssembler(source).assemble(
            operation = operation,
            premise = premise,
            comparisons = comparisons,
            associations = evaluatedAssociations
        )
        return TrudyChangeInvestigation(
            premiseAssessment = premise,
            observationWindow = operation.observationWindow,
            baselineWindow = operation.baselineWindow,
            targetComparisons = comparisons,
            relatedAssociations = associations,
            missingMetrics = missing,
            caveats = structured.caveats,
            structuredResult = structured
        )
    }

    private fun assessPremise(
        operation: InvestigateChange,
        comparisons: List<TrudyBaselineComparison>
    ): TrudyPremiseAssessment {
        val directional = comparisons.mapNotNull { comparison ->
            val metric = operation.targets.firstOrNull {
                it.domain == comparison.domain && it.metricId == comparison.metricId
            } ?: return@mapNotNull null
            val delta = comparison.absoluteDelta?.takeIf { it.isFinite() && abs(it) > EPSILON }
                ?: return@mapNotNull null
            when (metric.preference) {
                TrudyMetricPreference.HIGHER_IS_FAVOURABLE -> if (delta > 0) 1 else -1
                TrudyMetricPreference.LOWER_IS_FAVOURABLE -> if (delta < 0) 1 else -1
                TrudyMetricPreference.CONTEXT_DEPENDENT -> return@mapNotNull null
            }
        }
        if (directional.isEmpty()) return TrudyPremiseAssessment.INSUFFICIENT
        if (operation.claim == TrudyChangeClaim.CHANGED || operation.claim == TrudyChangeClaim.UNSPECIFIED) {
            return TrudyPremiseAssessment.SUPPORTED
        }
        val favourable = directional.count { it > 0 }
        val adverse = directional.count { it < 0 }
        if (favourable > 0 && adverse > 0) return TrudyPremiseAssessment.MIXED
        return when (operation.claim) {
            TrudyChangeClaim.WORSENED -> if (adverse > 0) TrudyPremiseAssessment.SUPPORTED else TrudyPremiseAssessment.NOT_SUPPORTED
            TrudyChangeClaim.IMPROVED -> if (favourable > 0) TrudyPremiseAssessment.SUPPORTED else TrudyPremiseAssessment.NOT_SUPPORTED
            else -> TrudyPremiseAssessment.SUPPORTED
        }
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val MAX_ASSOCIATIONS = 8
        const val EPSILON = 0.0001
    }
}

interface TrudyPreflightPlanner {
    fun plan(request: TrudyAskRequest): List<TrudyToolOperation>
}

object NoOpTrudyPreflightPlanner : TrudyPreflightPlanner {
    override fun plan(request: TrudyAskRequest): List<TrudyToolOperation> = emptyList()
}

/** Deterministic intent-to-evidence plan. The model synthesizes results but cannot skip retrieval. */
class TrudySystemInvestigationPlanner(
    private val temporal: TrudyTemporalResolver,
    private val maxOperations: Int = 10
) : TrudyPreflightPlanner {
    init { require(maxOperations in 1..20) }

    override fun plan(request: TrudyAskRequest): List<TrudyToolOperation> {
        val text = request.userMessage.lowercase()
        val timeframe = temporal.resolve(text, request.conversationContext)
        experimentPlan(text)?.let { return listOf(it) }

        val currentModules = TrudySystemCatalog.modulesMentioned(text)
        val inheritedModules = if (looksLikeFollowUp(text)) {
            request.conversationContext.asReversed().asSequence()
                .map { TrudySystemCatalog.modulesMentioned(it.text) }
                .firstOrNull { it.isNotEmpty() }.orEmpty()
        } else emptyList()
        val modules = (currentModules + inheritedModules).distinctBy { it.id }
        if ("insight" in text) {
            val domains = modules.flatMap { it.metrics }.map { it.domain }.distinct()
                .ifEmpty { HealthDomain.entries }
            return domains.take(maxOperations).map { TrudyToolOperation.GetInsights(it) }
        }
        val evidenceMetrics = if (asksForEvidence(text)) metricsFromConversationEvidence(request.conversationContext) else emptyList()
        val selected = if (asksForEvidence(text) && evidenceMetrics.isNotEmpty()) {
            evidenceMetrics
        } else {
            evidenceMetrics +
                TrudySystemCatalog.metricsMentioned(text, currentModules.ifEmpty { modules }) +
                inheritedModules.flatMap { it.metrics.take(1) }
        }.distinctBy { it.domain to it.metricId }

        if (!requiresChangeInvestigation(text)) {
            directAssociation(text, timeframe, selected)?.let { return listOf(it) }
        }
        if (asksForEvidence(text) && selected.isNotEmpty()) {
            val range = TrudyTimeRange(timeframe.baseline.fromEpochMs, timeframe.observation.toEpochMs)
            return selected.take(MAX_TARGETS).map {
                TrudyToolOperation.GetMetricWindow(it.domain, it.metricId, range, METRIC_WINDOW_LIMIT)
            }
        }
        investigationIntent(text)?.let { intent ->
            val relevance = TrudyInvestigationRelevanceGraph.plan(text, selected, intent)
            val observation = timeframe.observation
            val baseline = if (intent == TrudyInvestigationIntent.WHAT_TO_WATCH_TODAY) {
                val end = (observation.fromEpochMs - 1L).coerceAtLeast(0L)
                TrudyTimeRange((end - 7L * DAY_MS + 1L).coerceAtLeast(0L), end)
            } else timeframe.baseline
            val investigation = InvestigateChange(
                targets = relevance.targets,
                related = relevance.related,
                observationWindow = observation,
                baselineWindow = baseline,
                claim = claim(text),
                maxAssociations = relevance.related.size.coerceAtMost(MAX_RELATED),
                intent = intent,
                targetLabel = relevance.targetLabel,
                includesSubjectiveClaim = listOf("felt", "feel", "suffer", "tired", "refreshed").any { it in text },
                timeframeLabel = timeframe.label,
                timeframeExplicit = timeframe.explicit,
                budget = TrudyInvestigationBudget(
                    maxTargets = if (intent == TrudyInvestigationIntent.WHAT_TO_WATCH_TODAY) 8 else 5,
                    maxRelatedSignals = MAX_RELATED,
                    maxLookbackDays = (
                        (observation.toEpochMs - baseline.fromEpochMs).coerceAtLeast(0L) / DAY_MS + 1L
                    ).toInt().coerceIn(7, 90)
                )
            )
            return if (intent == TrudyInvestigationIntent.WHAT_TO_WATCH_TODAY) {
                listOf(investigation, GetCanonicalExperiments(TrudyCanonicalExperimentStatus.ACTIVE))
            } else listOf(investigation)
        }
        if (selected.isEmpty()) {
            val unavailable = modules.filterNot { it.canonicalDataAvailable }
            if (unavailable.isNotEmpty()) return listOf(GetSystemAvailability(unavailable.map { it.id }))
            if (modules.any { it.id == "clinical" }) return listOf(TrudyToolOperation.GetDomainState(HealthDomain.CLINICAL))
            return emptyList()
        }

        val asksForReading = listOf("what was", "show", "reading", "how much", "today", "yesterday").any { it in text }
        val operations = if (asksForReading) {
            selected.map { TrudyToolOperation.GetMetricWindow(it.domain, it.metricId, timeframe.observation, METRIC_WINDOW_LIMIT) }
        } else {
            selected.take(MAX_TARGETS).map {
                CompareBaseline(it.domain, it.metricId, timeframe.observation, timeframe.baseline)
            }
        }
        return operations.distinct().take(maxOperations)
    }

    private fun directAssociation(
        text: String,
        timeframe: TrudyResolvedTimeframe,
        selected: List<TrudySystemMetric>
    ): TrudyToolOperation? {
        val pair = when {
            ("sleep" in text || "slept" in text || "night" in text) && "caffeine" in text ->
                return GetLaggedAssociation(
                    leftDomain = HealthDomain.NUTRITION,
                    leftMetricId = "food_caffeine_mg",
                    rightDomain = HealthDomain.SLEEP,
                    rightMetricId = "sleep_score",
                    lagMs = TrudyTemporalAlignment.PREVIOUS_EVENING_TO_FOLLOWING_SLEEP.lagMs,
                    alignmentWindowMs = TrudyTemporalAlignment.PREVIOUS_EVENING_TO_FOLLOWING_SLEEP.toleranceMs,
                    window = TrudyTimeRange(timeframe.baseline.fromEpochMs, timeframe.observation.toEpochMs)
                )
            ("sleep" in text || "slept" in text || "night" in text) && ("stress" in text || "anxious" in text || "calm" in text) ->
                TrudySystemMetric(HealthDomain.EMOTIONAL, "emotional_calmness", emptySet()) to
                    TrudySystemMetric(HealthDomain.SLEEP, "sleep_score", emptySet())
            "heart rate" in text && ("exercise" in text || "workout" in text || "activity" in text) ->
                TrudySystemMetric(HealthDomain.EXERCISE, "exercise_minutes", emptySet()) to
                    TrudySystemMetric(HealthDomain.EXERCISE, "heart_rate_avg_bpm", emptySet())
            ("bright" in text || "sunlight" in text || "uv" in text) && "mood" in text ->
                TrudySystemMetric(HealthDomain.ENVIRONMENT, "environment_uv_index", emptySet()) to
                    TrudySystemMetric(HealthDomain.EMOTIONAL, "emotional_valence", emptySet())
            looksLikeFollowUp(text) && listOf("affect", "linked", "related", "connection").any { it in text } && selected.size >= 2 ->
                selected[1] to selected[0]
            else -> return null
        }
        return GetAssociation(
            leftDomain = pair.first.domain,
            leftMetricId = pair.first.metricId,
            rightDomain = pair.second.domain,
            rightMetricId = pair.second.metricId,
            alignmentWindowMs = 43_200_000L,
            window = TrudyTimeRange(timeframe.baseline.fromEpochMs, timeframe.observation.toEpochMs)
        )
    }

    private fun experimentPlan(text: String): TrudyToolOperation? {
        val experimentContext = "experiment" in text || "baseline" in text || "intervention" in text ||
            "protocol" in text || "what am i measuring" in text || "what am i testing" in text
        if (!experimentContext) return null
        if (listOf("what experiment", "running", "measuring", "what am i testing", "active experiment").any { it in text }) {
            return GetCanonicalExperiments(TrudyCanonicalExperimentStatus.ACTIVE)
        }
        if (listOf("what changed", "compare", "baseline", "intervention", "result").any { it in text }) {
            return EvaluateCanonicalExperiment()
        }
        if (listOf("design", "create", "try", "could we").any { it in text }) {
            val kind = when {
                "caffeine" in text -> TrudyExperimentKind.EARLIER_CAFFEINE_CUTOFF
                "hydration" in text || "water" in text -> TrudyExperimentKind.HYDRATION_CONSISTENCY
                "exercise" in text -> TrudyExperimentKind.EXERCISE_TIMING
                "mindful" in text || "meditation" in text -> TrudyExperimentKind.MINDFULNESS_ROUTINE
                else -> TrudyExperimentKind.SLEEP_SCHEDULE_CONSISTENCY
            }
            val target = TrudySystemCatalog.metricsMentioned(text).firstOrNull()
                ?: TrudySystemCatalog.metric(HealthDomain.SLEEP, "sleep_score")!!
            return GenerateExperimentHypothesis(kind, target.domain, target.metricId)
        }
        return GetCanonicalExperiments()
    }

    private fun relatedMetrics(targets: List<TrudyInvestigationMetric>): List<TrudySystemMetric> {
        val targetDomains = targets.map { it.domain }.toSet()
        return defaultOverviewMetrics().filterNot { metric ->
            targets.any { it.domain == metric.domain && it.metricId == metric.metricId }
        }.sortedBy { if (it.domain in targetDomains) 1 else 0 }
    }

    private fun metricsFromConversationEvidence(turns: List<TrudyConversationTurn>): List<TrudySystemMetric> =
        turns.asReversed().asSequence().filter { it.role == TrudyConversationRole.ASSISTANT }
            .flatMap { it.evidenceKeys.asSequence() }
            .mapNotNull { key ->
                val parts = key.split(':')
                val domain = parts.firstOrNull()?.let { runCatching { HealthDomain.valueOf(it) }.getOrNull() }
                    ?: return@mapNotNull null
                val metricId = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                TrudySystemCatalog.metric(domain, metricId) ?: TrudySystemMetric(domain, metricId, emptySet())
            }.take(MAX_TARGETS).toList()

    private fun defaultOverviewMetrics() = listOfNotNull(
        TrudySystemCatalog.metric(HealthDomain.SLEEP, "sleep_score"),
        TrudySystemCatalog.metric(HealthDomain.EXERCISE, "resting_heart_rate_bpm"),
        TrudySystemCatalog.metric(HealthDomain.EXERCISE, "steps"),
        TrudySystemCatalog.metric(HealthDomain.HYDRATION, "water_total_l"),
        TrudySystemCatalog.metric(HealthDomain.EMOTIONAL, "emotional_valence"),
        TrudySystemCatalog.metric(HealthDomain.EMOTIONAL, "emotional_energy"),
        TrudySystemCatalog.metric(HealthDomain.BODY, "body_weight_kg"),
        TrudySystemCatalog.metric(HealthDomain.ENVIRONMENT, "environment_temperature_c")
    )

    private fun TrudySystemMetric.toInvestigation(role: TrudyInvestigationRole = TrudyInvestigationRole.SUPPORTING) =
        TrudyInvestigationMetric(domain, metricId, role, preference)

    private fun investigationIntent(text: String): TrudyInvestigationIntent? = when {
        "what should i pay attention to today" in text || "what matters today" in text || "prioritise today" in text ||
            "prioritize today" in text -> TrudyInvestigationIntent.WHAT_TO_WATCH_TODAY
        "why" in text -> TrudyInvestigationIntent.WHY
        "what might explain" in text || "what could explain" in text || "explain" in text ->
            TrudyInvestigationIntent.WHAT_MIGHT_EXPLAIN
        "what was different" in text -> TrudyInvestigationIntent.WHAT_WAS_DIFFERENT
        "what changed" in text || "changed this" in text -> TrudyInvestigationIntent.WHAT_CHANGED
        "anything connected" in text || "seem connected" in text -> TrudyInvestigationIntent.IS_ANYTHING_CONNECTED
        else -> null
    }

    private fun looksInvestigative(text: String) = investigationIntent(text) != null

    private fun requiresChangeInvestigation(text: String) = looksInvestigative(text)

    private fun looksLikeFollowUp(text: String) = listOf("what about", "could that", "and last", "how about", "basing that on", "based on").any { it in text }
    private fun asksForEvidence(text: String) = "basing that on" in text || "based on" in text || "what data" in text
    private fun claim(text: String) = when {
        listOf("worse", "worsened", "lower", "declined", "more tired").any { it in text } -> TrudyChangeClaim.WORSENED
        listOf("better", "improved", "higher").any { it in text } -> TrudyChangeClaim.IMPROVED
        "changed" in text || "different" in text -> TrudyChangeClaim.CHANGED
        else -> TrudyChangeClaim.UNSPECIFIED
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val MAX_TARGETS = 8
        const val MAX_RELATED = 8
        const val METRIC_WINDOW_LIMIT = 250
    }
}

data class GetSystemAvailability(val moduleIds: List<String>) : TrudyToolOperation {
    override val domains: List<HealthDomain> = emptyList()
}

data class SystemAvailabilityResult(
    override val operation: GetSystemAvailability,
    val unavailableReasons: Map<String, String>
) : TrudyToolResult
