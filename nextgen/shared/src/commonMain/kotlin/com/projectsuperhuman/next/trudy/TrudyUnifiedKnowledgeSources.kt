package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.trudy.medical.CuratedMedicalManagementRepository
import com.projectsuperhuman.next.trudy.medical.MedicalKnowledgeProvider
import com.projectsuperhuman.next.trudy.nutrition.CuratedNutritionBodyKnowledgeRepository
import com.projectsuperhuman.next.trudy.nutrition.NutritionKnowledgeContext
import com.projectsuperhuman.next.trudy.nutrition.NutritionQuestionIntent
import com.projectsuperhuman.next.trudy.nutrition.TrudyNutritionKnowledgeProvider
import com.projectsuperhuman.next.trudy.nutrition.TrudyNutritionMetricBinding
import com.projectsuperhuman.next.trudy.performance.PerformanceEvidenceCatalog
import com.projectsuperhuman.next.trudy.performance.PerformanceExperimentBlueprint
import com.projectsuperhuman.next.trudy.performance.PerformanceKnowledgeDomain
import com.projectsuperhuman.next.trudy.performance.PerformanceMetricBinding
import com.projectsuperhuman.next.trudy.performance.PerformanceMetricRole
import com.projectsuperhuman.next.trudy.performance.PerformanceSafetyBoundary
import com.projectsuperhuman.next.trudy.performance.TrudyPerformanceKnowledge

/** Built-ins are read-only and contain no personal observations. */
fun defaultTrudyKnowledgeSources(): List<TrudyKnowledgeSource> = listOf(
    TrudyMedicalManagementKnowledgeSource(),
    TrudyNutritionBodyKnowledgeSource(),
    TrudyPerformanceKnowledgeSource()
)

/**
 * Adapter for the reviewed medical-management seed. The much larger condition/symptom corpus stays
 * behind MedicalContextAwareTrudyModelClient where red-flag and candidate safeguards are enforced.
 */
class TrudyMedicalManagementKnowledgeSource(
    private val repository: MedicalKnowledgeProvider = CuratedMedicalManagementRepository()
) : TrudyKnowledgeSource {
    override val sourceId: String = "medical-management-reviewed"
    override val kind: TrudyKnowledgeKind = TrudyKnowledgeKind.MEDICAL

    override suspend fun retrieve(query: TrudyKnowledgeQuery): List<TrudyKnowledgeItem> =
        repository.searchManagement(query.userText, minOf(query.maxItems, MAX_MATCHES)).map { match ->
            val entry = match.entry
            val domains = entry.relevantVaultSignals.map { it.domain }.distinct()
            val hints = entry.relevantVaultSignals.flatMap { signal ->
                signal.metricIds.map { metricId ->
                    TrudyKnowledgeMetricHint(signal.domain, metricId, TrudyKnowledgeMetricRole.CONTEXT)
                }
            }.distinct()
            val references = entry.sources.map { "${it.organisation}: ${it.url}" }.distinct()
            TrudyKnowledgeItem(
                stableId = "medical:${entry.conditionId}",
                kind = kind,
                title = entry.displayName,
                summary = "Reviewed management context exists for ${entry.displayName}. Use the dedicated medical candidate and safety layer for symptom interpretation; this reference is not a diagnosis.",
                sourceId = sourceId,
                sourceReferences = references,
                relevantDomains = domains,
                relevantMetricIds = hints.map { it.metricId }.distinct(),
                metricHints = hints,
                uncertainty = "Reference relevance is not diagnostic probability and personal Data Vault observations are not diagnostic proof.",
                safetyNotes = listOf(
                    "Do not diagnose from symptom overlap.",
                    "Do not personalize prescriptions or doses from this knowledge.",
                    "Red-flag escalation is owned by the structured medical safety layer."
                ),
                version = "medical-management-v1",
                lastReviewed = entry.sources.maxOfOrNull { it.reviewedAt } ?: FALLBACK_REVIEW_DATE
            )
        }

    private companion object { const val MAX_MATCHES = 5 }
}

class TrudyNutritionBodyKnowledgeSource(
    private val repository: TrudyNutritionKnowledgeProvider = CuratedNutritionBodyKnowledgeRepository()
) : TrudyKnowledgeSource {
    override val sourceId: String = "nutrition-body-curated"
    override val kind: TrudyKnowledgeKind = TrudyKnowledgeKind.NUTRITION

    override suspend fun retrieve(query: TrudyKnowledgeQuery): List<TrudyKnowledgeItem> {
        val context = runCatching { repository.contextFor(query.userText) }.getOrElse { return emptyList() }
        val references = context.sources.map { "${it.organisation}: ${it.url}" }.distinct().take(MAX_REFERENCES)
        if (references.isEmpty()) return emptyList()
        val hints = context.metricBindings.flatMap { it.toHints(context.intent) }.distinct()
        val topicText = context.topics.take(3).joinToString(" ") { "${it.displayName}: ${it.summary}" }
        val nutrientText = context.nutrients.take(3).joinToString(" ") { "${it.displayName}: ${it.role}" }
        val summary = listOf(topicText, nutrientText).filter(String::isNotBlank).joinToString(" ").take(MAX_SUMMARY_CHARS)
        if (summary.isBlank()) return emptyList()
        return listOf(
            TrudyKnowledgeItem(
                stableId = "nutrition:${context.intent.name.lowercase()}",
                kind = kind,
                title = context.intent.displayTitle(),
                summary = summary,
                sourceId = sourceId,
                sourceReferences = references,
                relevantDomains = hints.map { it.domain }.distinct(),
                relevantMetricIds = hints.map { it.metricId }.distinct(),
                metricHints = hints,
                uncertainty = nutritionUncertainty(context),
                safetyNotes = context.safetyInstructions.take(MAX_SAFETY_NOTES),
                version = "nutrition-body-v1",
                lastReviewed = context.sources.maxOfOrNull { it.reviewedAt } ?: FALLBACK_REVIEW_DATE
            )
        )
    }

    private fun TrudyNutritionMetricBinding.toHints(intent: NutritionQuestionIntent): List<TrudyKnowledgeMetricHint> =
        metricIds.map { metricId -> TrudyKnowledgeMetricHint(domain, metricId, nutritionRole(intent, this)) }

    private fun nutritionRole(
        intent: NutritionQuestionIntent,
        binding: TrudyNutritionMetricBinding
    ): TrudyKnowledgeMetricRole = when {
        intent == NutritionQuestionIntent.OVERNIGHT_WEIGHT_CHANGE && binding.id == "body_weight" -> TrudyKnowledgeMetricRole.PRIMARY_OUTCOME
        intent == NutritionQuestionIntent.BODY_COMPOSITION_CHANGE && binding.domain == HealthDomain.BODY -> TrudyKnowledgeMetricRole.PRIMARY_OUTCOME
        intent == NutritionQuestionIntent.HYDRATION_ADEQUACY && binding.domain == HealthDomain.HYDRATION -> TrudyKnowledgeMetricRole.PRIMARY_OUTCOME
        intent == NutritionQuestionIntent.PROTEIN_ADEQUACY && binding.id == "protein_intake" -> TrudyKnowledgeMetricRole.PRIMARY_OUTCOME
        binding.domain == HealthDomain.ENVIRONMENT || binding.domain == HealthDomain.EXERCISE -> TrudyKnowledgeMetricRole.CONTEXT
        else -> TrudyKnowledgeMetricRole.EXPOSURE
    }

    private fun nutritionUncertainty(context: NutritionKnowledgeContext): String = when (context.intent) {
        NutritionQuestionIntent.DIETARY_NUTRIENT_GAP -> "Food-log coverage can be incomplete; missing micronutrient values are unknown rather than zero and cannot diagnose deficiency."
        NutritionQuestionIntent.OVERNIGHT_WEIGHT_CHANGE -> "A one-day scale change can reflect fluid, glycogen, gut contents, sodium and measurement conditions rather than fat change."
        else -> "General nutrition knowledge must be separated from the user's recorded intake and from clinical evidence."
    }
}

class TrudyPerformanceKnowledgeSource(
    private val knowledge: TrudyPerformanceKnowledge = TrudyPerformanceKnowledge()
) : TrudyKnowledgeSource {
    override val sourceId: String = "performance-curated"
    override val kind: TrudyKnowledgeKind = TrudyKnowledgeKind.SLEEP_AND_PERFORMANCE
    override val kinds: Set<TrudyKnowledgeKind> = setOf(
        TrudyKnowledgeKind.SLEEP_AND_PERFORMANCE,
        TrudyKnowledgeKind.ENVIRONMENT,
        TrudyKnowledgeKind.EMOTIONAL_WELLBEING,
        TrudyKnowledgeKind.EXPERIMENT_METHODOLOGY
    )
    private val sourcesById = PerformanceEvidenceCatalog.sources.associateBy { it.id }

    override suspend fun retrieve(query: TrudyKnowledgeQuery): List<TrudyKnowledgeItem> {
        val bundle = knowledge.resolve(query.userText, maxTopics = minOf(MAX_TOPICS, query.maxItems))
        if (bundle.matches.isEmpty()) return emptyList()
        val safety = bundle.globalSafetyBoundaries.map(::performanceSafetyNote)
        val topicItems = bundle.matches.mapNotNull { match ->
            val topic = match.topic
            val claims = bundle.claims.filter { it.id in topic.claimIds }
            if (claims.isEmpty()) return@mapNotNull null
            val sourceIds = claims.flatMap { it.evidenceSourceIds }.distinct()
            val references = sourceIds.mapNotNull(sourcesById::get)
                .map { "${it.organisation}: ${it.url}" }
                .distinct()
            if (references.isEmpty()) return@mapNotNull null
            val hints = topic.metricBindings.map(::performanceHint).distinct()
            TrudyKnowledgeItem(
                stableId = "performance:${topic.id}",
                kind = performanceKind(topic.primaryDomain),
                title = topic.displayName,
                summary = claims.take(3).joinToString(" ") { it.summary }.take(MAX_SUMMARY_CHARS),
                sourceId = sourceId,
                sourceReferences = references.take(MAX_REFERENCES),
                relevantDomains = hints.map { it.domain }.distinct(),
                relevantMetricIds = hints.map { it.metricId }.distinct(),
                metricHints = hints,
                uncertainty = claims.flatMap { it.limitations }.take(2).joinToString(" ").ifBlank { null },
                safetyNotes = (safety + claims.flatMap { claim -> claim.safetyBoundaries.map(::performanceSafetyNote) })
                    .distinct().take(MAX_SAFETY_NOTES),
                version = "performance-v1",
                lastReviewed = FALLBACK_REVIEW_DATE
            )
        }
        val experimentItems = bundle.experimentBlueprints.mapNotNull { blueprint -> experimentItem(blueprint, safety) }
        return (topicItems + experimentItems)
            .distinctBy { it.kind to it.stableId }
            .take(query.maxItems)
    }

    private fun experimentItem(
        blueprint: PerformanceExperimentBlueprint,
        safety: List<String>
    ): TrudyKnowledgeItem? {
        val references = blueprint.evidenceSourceIds.mapNotNull(sourcesById::get)
            .map { "${it.organisation}: ${it.url}" }
            .distinct()
        if (references.isEmpty()) return null
        val bindings = listOf(blueprint.primaryOutcome) + blueprint.secondaryOutcomes
        val hints = bindings.map(::performanceHint).distinct()
        val summary = buildString {
            append(blueprint.hypothesis)
            append(" Baseline: ${blueprint.baselineDays} days; intervention: ${blueprint.interventionDays} days. ")
            append("Primary outcome: ${blueprint.primaryOutcome.metricId}. ")
            append("Important confounders: ${blueprint.confounders.take(4).joinToString()}.")
        }.take(MAX_SUMMARY_CHARS)
        return TrudyKnowledgeItem(
            stableId = "experiment:${blueprint.id}",
            kind = TrudyKnowledgeKind.EXPERIMENT_METHODOLOGY,
            title = "Personal experiment: ${blueprint.id.replace('_', ' ')}",
            summary = summary,
            sourceId = sourceId,
            sourceReferences = references.take(MAX_REFERENCES),
            relevantDomains = hints.map { it.domain }.distinct(),
            relevantMetricIds = hints.map { it.metricId }.distinct(),
            metricHints = hints,
            uncertainty = blueprint.interpretationLimitations.take(2).joinToString(" "),
            safetyNotes = (safety + blueprint.safetyNotes).distinct().take(MAX_SAFETY_NOTES),
            version = "performance-experiments-v1",
            lastReviewed = FALLBACK_REVIEW_DATE
        )
    }

    private fun performanceHint(binding: PerformanceMetricBinding): TrudyKnowledgeMetricHint =
        TrudyKnowledgeMetricHint(binding.domain, binding.metricId, binding.role.toUnifiedRole())

    private fun PerformanceMetricRole.toUnifiedRole(): TrudyKnowledgeMetricRole = when (this) {
        PerformanceMetricRole.PRIMARY_OUTCOME -> TrudyKnowledgeMetricRole.PRIMARY_OUTCOME
        PerformanceMetricRole.SECONDARY_OUTCOME -> TrudyKnowledgeMetricRole.SECONDARY_OUTCOME
        PerformanceMetricRole.EXPOSURE -> TrudyKnowledgeMetricRole.EXPOSURE
        PerformanceMetricRole.CONFOUNDER -> TrudyKnowledgeMetricRole.CONFOUNDER
        PerformanceMetricRole.CONTEXT -> TrudyKnowledgeMetricRole.CONTEXT
        PerformanceMetricRole.DATA_QUALITY -> TrudyKnowledgeMetricRole.DATA_QUALITY
    }

    private fun performanceKind(domain: PerformanceKnowledgeDomain): TrudyKnowledgeKind = when (domain) {
        PerformanceKnowledgeDomain.ENVIRONMENT -> TrudyKnowledgeKind.ENVIRONMENT
        PerformanceKnowledgeDomain.EMOTIONAL_WELLBEING -> TrudyKnowledgeKind.EMOTIONAL_WELLBEING
        PerformanceKnowledgeDomain.EXPERIMENTATION -> TrudyKnowledgeKind.EXPERIMENT_METHODOLOGY
        else -> TrudyKnowledgeKind.SLEEP_AND_PERFORMANCE
    }

    private fun performanceSafetyNote(boundary: PerformanceSafetyBoundary): String = when (boundary) {
        PerformanceSafetyBoundary.PERSONAL_ASSOCIATION_NOT_CAUSATION -> "Personal associations are not established causation."
        PerformanceSafetyBoundary.WEARABLE_ESTIMATE_NOT_CLINICAL_MEASUREMENT -> "Consumer wearable estimates are not equivalent to clinical measurement."
        PerformanceSafetyBoundary.AVOID_AUTONOMOUS_EXERCISE_PRESCRIPTION -> "Do not autonomously prescribe exercise intensity from this knowledge."
        PerformanceSafetyBoundary.AVOID_SINGLE_SCORE_READINESS_CLAIM -> "Do not treat one score as proof of readiness or recovery."
        PerformanceSafetyBoundary.MENTAL_WELLBEING_NOT_DIAGNOSIS -> "Emotional wellbeing context is not a mental-health diagnosis."
        PerformanceSafetyBoundary.STOP_FOR_CONCERNING_SYMPTOMS -> "Concerning symptoms take priority over performance optimization."
        PerformanceSafetyBoundary.RESPECT_CLINICIAN_RESTRICTIONS -> "Clinician restrictions override general performance guidance."
        PerformanceSafetyBoundary.EXPERIMENT_NOT_UNIVERSAL_PROOF -> "A personal experiment is not universal or causal proof."
        PerformanceSafetyBoundary.NON_DIAGNOSTIC -> "Performance knowledge is non-diagnostic."
    }

    private companion object { const val MAX_TOPICS = 6 }
}

private fun NutritionQuestionIntent.displayTitle(): String = name.lowercase().split('_')
    .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

private const val MAX_REFERENCES = 8
private const val MAX_SUMMARY_CHARS = 1_500
private const val MAX_SAFETY_NOTES = 8
private const val FALLBACK_REVIEW_DATE = "2026-08-14"
