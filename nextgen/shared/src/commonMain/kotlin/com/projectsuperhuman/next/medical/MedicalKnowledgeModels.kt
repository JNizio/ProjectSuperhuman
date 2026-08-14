package com.projectsuperhuman.next.medical

/** Stable, non-diagnostic reference knowledge for Trudy and other local consumers. */
data class MedicalConditionKnowledge(
    val id: String,
    val displayName: String,
    val aliases: List<String>,
    val bodySystems: List<MedicalBodySystem>,
    val categories: List<String>,
    val description: String,
    val features: List<MedicalFeature>,
    val typicalOnsetAndCourse: String,
    val riskFactors: List<String>,
    val commonAssociations: List<String>,
    val differentiatingFeatures: List<MedicalDifferentiator>,
    val redFlags: List<MedicalRedFlag>,
    val commonInvestigations: List<MedicalInvestigation>,
    val diagnosticContext: String,
    val severityAndEmergencyNotes: String,
    val populationContext: List<String>,
    val provenance: List<MedicalSourceReference>,
    val review: MedicalReviewMetadata,
)

enum class MedicalBodySystem {
    GASTROINTESTINAL, RESPIRATORY, CARDIOVASCULAR, NEUROLOGICAL,
    ENDOCRINE_METABOLIC, MUSCULOSKELETAL, DERMATOLOGICAL, INFECTIOUS,
    ALLERGY_IMMUNOLOGY, URINARY_RENAL, MENTAL_HEALTH, ENT, EYE,
    REPRODUCTIVE, HAEMATOLOGICAL_NUTRITIONAL, PAIN,
}

enum class FeatureFrequency { COMMON, POSSIBLE, UNCOMMON }

data class MedicalFeature(
    val symptomId: String,
    val label: String,
    val frequency: FeatureFrequency,
    val context: String? = null,
)

data class MedicalDifferentiator(
    val statement: String,
    val kind: DifferentiatorKind = DifferentiatorKind.CONTEXT,
)

enum class DifferentiatorKind { CHARACTERISTIC, IMPORTANT_NEGATIVE, ALTERNATIVE_CONTEXT }

data class MedicalRedFlag(
    val id: String,
    val description: String,
    val urgency: MedicalUrgency,
    val context: String,
)

enum class MedicalUrgency { EMERGENCY, URGENT_SAME_DAY, PROMPT_CLINICAL_REVIEW }

data class MedicalInvestigation(
    val name: String,
    val purpose: String,
    val context: String? = null,
)

data class MedicalSourceReference(
    val sourceId: String,
    val organisation: String,
    val title: String,
    val url: String,
    val evidenceScope: List<String>,
    val accessedOn: String,
)

data class MedicalReviewMetadata(
    val corpusVersion: String,
    val lastReviewedOn: String,
    val reviewStatus: ReviewStatus,
)

enum class ReviewStatus { CURATED, NEEDS_CLINICAL_REVIEW }

data class SymptomIndexEntry(
    val symptomId: String,
    val displayName: String,
    val aliases: List<String>,
    val conditionLinks: List<SymptomConditionLink>,
)

data class SymptomConditionLink(
    val conditionId: String,
    val frequency: FeatureFrequency,
    val context: String? = null,
)

/**
 * Retrieval boundary deliberately returns candidate reference records, never diagnoses or
 * probabilities. Ranking is lexical/metadata relevance only.
 */
interface MedicalKnowledgeRepository {
    suspend fun condition(id: String): MedicalConditionKnowledge?
    suspend fun searchConditions(query: String, limit: Int = 20): List<MedicalConditionKnowledge>
    suspend fun conditionsForSymptoms(symptomIds: Set<String>, limit: Int = 20): List<MedicalConditionKnowledge>
    suspend fun symptom(id: String): SymptomIndexEntry?
}
