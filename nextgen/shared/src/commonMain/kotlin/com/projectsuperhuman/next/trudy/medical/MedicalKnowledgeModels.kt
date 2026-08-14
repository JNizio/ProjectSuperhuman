package com.projectsuperhuman.next.trudy.medical

import com.projectsuperhuman.next.core.HealthDomain

private val CONDITION_ID_PATTERN = Regex("^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$")

fun requireStableConditionId(conditionId: String) {
    require(CONDITION_ID_PATTERN.matches(conditionId)) {
        "Condition IDs must be stable lowercase snake_case strings: $conditionId"
    }
}

enum class MedicalManagementCategory {
    LIFESTYLE,
    BEHAVIOURAL,
    PHYSIOTHERAPY_REHABILITATION,
    DIETARY,
    PSYCHOLOGICAL_THERAPY,
    MONITORING,
    FIRST_LINE_TREATMENT,
    SECOND_LINE_SPECIALIST,
    PROCEDURE,
    PREVENTION_RISK_REDUCTION,
    PROFESSIONAL_EVALUATION,
    SELF_TREATMENT_LIMIT
}

enum class MedicalCareSetting { SELF_CARE, PHARMACIST, PRIMARY_CARE, CLINICIAN_LED, SPECIALIST_ONLY }

enum class MedicalEvidenceType {
    CLINICAL_GUIDELINE,
    PROFESSIONAL_SOCIETY_GUIDELINE,
    GOVERNMENT_PATIENT_GUIDANCE,
    PUBLIC_HEALTH_GUIDANCE,
    PEER_REVIEWED_REVIEW
}

data class MedicalSource(
    val id: String,
    val title: String,
    val organisation: String,
    val url: String,
    val evidenceType: MedicalEvidenceType,
    val publishedOrUpdated: String? = null,
    val reviewedAt: String
) {
    init {
        require(id.isNotBlank() && title.isNotBlank() && organisation.isNotBlank())
        require(url.startsWith("https://"))
        require(reviewedAt.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")))
    }
}

/** A short, paraphrased management statement. It is not a prescription or a dosing rule. */
data class MedicalManagementOption(
    val id: String,
    val category: MedicalManagementCategory,
    val summary: String,
    val careSetting: MedicalCareSetting,
    val sourceIds: List<String>,
    val applicability: String? = null,
    val personalizationProhibited: Boolean = careSetting == MedicalCareSetting.CLINICIAN_LED ||
        careSetting == MedicalCareSetting.SPECIALIST_ONLY
) {
    init {
        require(id.isNotBlank() && summary.isNotBlank())
        require(sourceIds.isNotEmpty() && sourceIds.none(String::isBlank))
        require(!DOSING_PATTERN.containsMatchIn(summary)) {
            "Management knowledge must describe treatment categories, not dose instructions: $id"
        }
    }

    private companion object {
        val DOSING_PATTERN = Regex("\\b\\d+(?:\\.\\d+)?\\s*(?:mg|mcg|micrograms?|milligrams?|puffs?)\\b", RegexOption.IGNORE_CASE)
    }
}

data class MedicalVaultSignalSpec(
    val domain: HealthDomain,
    val metricIds: List<String> = emptyList(),
    val purpose: String,
    val historyLimit: Int = 120
) {
    init {
        require(purpose.isNotBlank())
        require(historyLimit in 1..500)
        require(metricIds.none(String::isBlank))
    }
}

data class MedicalManagementEntry(
    val conditionId: String,
    val displayName: String,
    val aliases: Set<String>,
    val options: List<MedicalManagementOption>,
    val sources: List<MedicalSource>,
    val relevantVaultSignals: List<MedicalVaultSignalSpec> = emptyList(),
    val reviewDue: String
) {
    init {
        requireStableConditionId(conditionId)
        require(displayName.isNotBlank() && aliases.none(String::isBlank))
        require(options.isNotEmpty() && sources.isNotEmpty())
        require(options.map { it.id }.distinct().size == options.size)
        require(sources.map { it.id }.distinct().size == sources.size)
        val knownSources = sources.mapTo(mutableSetOf()) { it.id }
        require(options.flatMap { it.sourceIds }.all { it in knownSources }) {
            "Every management statement must resolve to provenance within its entry"
        }
    }
}

data class MedicalKnowledgeMatch(
    val conditionId: String,
    val relevance: Double,
    val matchedTerms: List<String>,
    val entry: MedicalManagementEntry
) {
    init { require(relevance in 0.0..1.0) }
}

/**
 * Agent 3's management boundary. Agent 2's condition corpus can share the same stable condition IDs
 * without becoming a compile-time dependency of this repository.
 */
interface MedicalKnowledgeProvider {
    suspend fun byConditionIds(conditionIds: Set<String>): List<MedicalManagementEntry>
    suspend fun searchManagement(query: String, limit: Int = 5): List<MedicalKnowledgeMatch>
}

enum class MedicalCandidateRelevance { LOW, MODERATE, HIGH }

data class MedicalConditionCandidate(
    val conditionId: String,
    val displayName: String,
    val relevance: MedicalCandidateRelevance,
    val reasonsForFit: List<String>,
    val reasonsAgainstFit: List<String> = emptyList(),
    val informationThatWouldMatter: List<String> = emptyList(),
    val sourceIds: List<String> = emptyList()
) {
    init {
        requireStableConditionId(conditionId)
        require(displayName.isNotBlank())
        require(reasonsForFit.isNotEmpty())
    }
}

data class MedicalConditionCandidateRequest(
    val question: String,
    val explicitlyRecordedConditionIds: Set<String> = emptySet(),
    val maxCandidates: Int = 5
) {
    init {
        require(question.isNotBlank())
        explicitlyRecordedConditionIds.forEach(::requireStableConditionId)
        require(maxCandidates in 1..10)
    }
}

/** Exact plug-in contract for Agent 2's condition/symptom corpus. */
interface MedicalConditionCandidateProvider {
    suspend fun candidates(request: MedicalConditionCandidateRequest): List<MedicalConditionCandidate>
}

object EmptyMedicalConditionCandidateProvider : MedicalConditionCandidateProvider {
    override suspend fun candidates(request: MedicalConditionCandidateRequest): List<MedicalConditionCandidate> = emptyList()
}

enum class MedicalEscalationLevel { NONE, ROUTINE, PROMPT, URGENT, EMERGENCY }

data class MedicalSafetySignal(
    val id: String,
    val level: MedicalEscalationLevel,
    val summary: String,
    val matchedText: List<String>,
    val sourceIds: List<String>
)

data class MedicalSafetyAssessment(
    val level: MedicalEscalationLevel,
    val signals: List<MedicalSafetySignal>
) {
    init { require(level == (signals.maxOfOrNull { it.level } ?: MedicalEscalationLevel.NONE)) }
}
