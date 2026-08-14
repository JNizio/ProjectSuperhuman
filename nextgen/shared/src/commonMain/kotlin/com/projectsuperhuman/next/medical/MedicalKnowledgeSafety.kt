package com.projectsuperhuman.next.medical

/** Guardrails shared by future corpus loaders and Trudy retrieval adapters. */
object MedicalKnowledgeSafety {
    const val DISCLAIMER =
        "Reference information only. Symptom links are not diagnoses or estimates of probability."

    fun requireValidCondition(record: MedicalConditionKnowledge) {
        require(record.id.matches(Regex("^[a-z][a-z0-9_]*$"))) { "Condition ID must be stable snake_case" }
        require(record.displayName.isNotBlank() && record.description.isNotBlank())
        require(record.bodySystems.isNotEmpty())
        require(record.features.any { it.frequency == FeatureFrequency.COMMON })
        require(record.diagnosticContext.isNotBlank())
        require(record.provenance.isNotEmpty())
        require(record.provenance.all { it.url.startsWith("https://") && it.evidenceScope.isNotEmpty() })
        require(record.redFlags.distinctBy { it.id }.size == record.redFlags.size)
        require(record.review.corpusVersion.isNotBlank() && record.review.lastReviewedOn.isNotBlank())
    }

    fun symptomCandidatesAreNotDiagnoses(links: List<SymptomConditionLink>): Boolean =
        links.all { it.conditionId.isNotBlank() } // Contract intentionally contains no probability.
}
