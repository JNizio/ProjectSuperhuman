package com.projectsuperhuman.next.trudy.performance

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.trudy.TrudyExperimentKind

/** Non-diagnostic performance areas understood by Trudy. */
enum class PerformanceKnowledgeDomain {
    SLEEP,
    EXERCISE,
    RECOVERY,
    PHYSICAL_ACTIVITY,
    HEART_RATE_CONTEXT,
    ENVIRONMENT,
    MINDFULNESS,
    BREATHING,
    EMOTIONAL_WELLBEING,
    EXPERIMENTATION
}

enum class PerformanceEvidenceKind {
    GUIDELINE,
    CONSENSUS_STATEMENT,
    POSITION_STATEMENT,
    SYSTEMATIC_REVIEW,
    CONTROLLED_TRIAL,
    METHODS_STANDARD
}

enum class PerformanceSafetyBoundary {
    NON_DIAGNOSTIC,
    PERSONAL_ASSOCIATION_NOT_CAUSATION,
    WEARABLE_ESTIMATE_NOT_CLINICAL_MEASUREMENT,
    AVOID_AUTONOMOUS_EXERCISE_PRESCRIPTION,
    AVOID_SINGLE_SCORE_READINESS_CLAIM,
    MENTAL_WELLBEING_NOT_DIAGNOSIS,
    STOP_FOR_CONCERNING_SYMPTOMS,
    RESPECT_CLINICIAN_RESTRICTIONS,
    EXPERIMENT_NOT_UNIVERSAL_PROOF
}

enum class PerformanceMetricRole {
    PRIMARY_OUTCOME,
    SECONDARY_OUTCOME,
    EXPOSURE,
    CONFOUNDER,
    CONTEXT,
    DATA_QUALITY
}

/** Identifies where an exact persisted metric ID is owned. */
enum class PerformanceMetricOrigin {
    CORE_METRIC_REGISTRY,
    ENVIRONMENTAL_DOMAIN,
    EMOTIONAL_DOMAIN
}

data class PerformanceEvidenceSource(
    val id: String,
    val title: String,
    val organisation: String,
    val year: Int,
    val kind: PerformanceEvidenceKind,
    val url: String,
    val scopeNote: String
) {
    init {
        require(id.matches(ID_PATTERN))
        require(title.isNotBlank() && organisation.isNotBlank())
        require(year in 1990..2100)
        require(url.startsWith("https://"))
        require(scopeNote.isNotBlank())
    }
}

data class PerformanceMetricBinding(
    val domain: HealthDomain,
    val metricId: String,
    val role: PerformanceMetricRole,
    val origin: PerformanceMetricOrigin,
    val rationale: String
) {
    init {
        require(metricId.matches(ID_PATTERN))
        require(rationale.isNotBlank())
    }
}

/** One bounded factual unit. It describes interpretation, not a diagnosis or user-specific result. */
data class PerformanceKnowledgeClaim(
    val id: String,
    val domains: Set<PerformanceKnowledgeDomain>,
    val summary: String,
    val interpretationRules: List<String>,
    val limitations: List<String>,
    val evidenceSourceIds: Set<String>,
    val safetyBoundaries: Set<PerformanceSafetyBoundary> = emptySet()
) {
    init {
        require(id.matches(ID_PATTERN))
        require(domains.isNotEmpty())
        require(summary.isNotBlank())
        require(interpretationRules.isNotEmpty() && interpretationRules.none(String::isBlank))
        require(limitations.none(String::isBlank))
        require(evidenceSourceIds.isNotEmpty())
    }
}

data class PerformanceTopic(
    val id: String,
    val displayName: String,
    val primaryDomain: PerformanceKnowledgeDomain,
    val relatedDomains: Set<PerformanceKnowledgeDomain> = emptySet(),
    val claimIds: Set<String>,
    val metricBindings: List<PerformanceMetricBinding> = emptyList(),
    val dataAvailabilityNote: String? = null
) {
    init {
        require(id.matches(ID_PATTERN))
        require(displayName.isNotBlank())
        require(claimIds.isNotEmpty())
        require(dataAvailabilityNote?.isBlank() != true)
    }
}

/** A semantic alias group may deliberately route one everyday phrase to several related topics. */
data class PerformancePhraseGroup(
    val id: String,
    val topicIds: Set<String>,
    val aliases: Set<String>,
    val priority: Int = 0
) {
    init {
        require(id.matches(ID_PATTERN))
        require(topicIds.isNotEmpty())
        require(aliases.isNotEmpty() && aliases.none(String::isBlank))
        require(priority in 0..10)
    }
}

data class PerformanceIntentTopicMatch(
    val topic: PerformanceTopic,
    val score: Int,
    val matchedAliasGroups: Set<String>,
    val matchedAliases: Set<String>
)

data class PerformanceKnowledgeBundle(
    val normalizedMessage: String,
    val matches: List<PerformanceIntentTopicMatch>,
    val claims: List<PerformanceKnowledgeClaim>,
    val sources: List<PerformanceEvidenceSource>,
    val metricBindings: List<PerformanceMetricBinding>,
    val experimentBlueprints: List<PerformanceExperimentBlueprint>,
    val globalSafetyBoundaries: Set<PerformanceSafetyBoundary>
)

data class PerformanceExperimentPrinciple(
    val id: String,
    val name: String,
    val guidance: String,
    val limitation: String
) {
    init {
        require(id.matches(ID_PATTERN))
        require(name.isNotBlank() && guidance.isNotBlank() && limitation.isNotBlank())
    }
}

data class PerformanceExperimentBlueprint(
    val id: String,
    val topicIds: Set<String>,
    val question: String,
    val hypothesis: String,
    val baselineDays: Int,
    val interventionDays: Int,
    val primaryOutcome: PerformanceMetricBinding,
    val secondaryOutcomes: List<PerformanceMetricBinding>,
    val exposureOrIntervention: String,
    val consistencyInstructions: List<String>,
    val confounders: List<String>,
    val interpretationLimitations: List<String>,
    val safetyNotes: List<String>,
    val evidenceSourceIds: Set<String>,
    val existingExperimentKind: TrudyExperimentKind? = null
) {
    init {
        require(id.matches(ID_PATTERN))
        require(topicIds.isNotEmpty())
        require(question.isNotBlank() && hypothesis.isNotBlank())
        require(baselineDays > 0 && interventionDays > 0)
        require(exposureOrIntervention.isNotBlank())
        require(consistencyInstructions.isNotEmpty())
        require(confounders.isNotEmpty())
        require(interpretationLimitations.isNotEmpty())
        require(safetyNotes.isNotEmpty())
        require(evidenceSourceIds.isNotEmpty())
    }
}

internal val ID_PATTERN = Regex("^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$")
