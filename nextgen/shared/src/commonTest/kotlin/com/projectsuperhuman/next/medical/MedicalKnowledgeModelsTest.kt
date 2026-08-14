package com.projectsuperhuman.next.medical

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MedicalKnowledgeModelsTest {
    @Test
    fun `symptom links remain candidate references without probabilities`() {
        val link = SymptomConditionLink("migraine", FeatureFrequency.POSSIBLE, "Candidate reference only")
        assertTrue(MedicalKnowledgeSafety.symptomCandidatesAreNotDiagnoses(listOf(link)))
    }

    @Test
    fun `condition IDs must remain stable snake case`() {
        assertFailsWith<IllegalArgumentException> { MedicalKnowledgeSafety.requireValidCondition(fixture("Migraine!")) }
    }

    private fun fixture(id: String) = MedicalConditionKnowledge(
        id, "Migraine", emptyList(), listOf(MedicalBodySystem.NEUROLOGICAL), listOf("primary_headache"),
        "Recurrent neurological headache disorder.", listOf(MedicalFeature("headache", "Headache", FeatureFrequency.COMMON)),
        "Episodic.", emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
        "Clinical assessment is required.", "Context dependent.", emptyList(),
        listOf(MedicalSourceReference("nhs", "NHS", "Migraine", "https://www.nhs.uk/conditions/migraine/", listOf("features"), "2026-08-14")),
        MedicalReviewMetadata("1.0.0", "2026-08-14", ReviewStatus.NEEDS_CLINICAL_REVIEW),
    )
}
