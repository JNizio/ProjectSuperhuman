package com.projectsuperhuman.next.trudy.medical

data class MedicalRedFlagRule(
    val id: String,
    val level: MedicalEscalationLevel,
    val phraseGroups: List<Set<String>>,
    val summary: String,
    val sourceIds: List<String>
) {
    init {
        require(level >= MedicalEscalationLevel.PROMPT)
        require(phraseGroups.isNotEmpty() && phraseGroups.none { it.isEmpty() })
        require(summary.isNotBlank() && sourceIds.isNotEmpty())
    }
}

interface MedicalSafetySignalProvider {
    fun assess(question: String): MedicalSafetyAssessment
}

/**
 * High-specificity phrase rules for a small number of time-critical patterns. They intentionally
 * prefer missed generic wording over turning every medical answer into an emergency warning.
 */
class CuratedMedicalSafetySignalProvider(
    private val rules: List<MedicalRedFlagRule> = defaultRules
) : MedicalSafetySignalProvider {
    override fun assess(question: String): MedicalSafetyAssessment {
        val normalized = question.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
        val matches = rules.mapNotNull { rule ->
            val groupMatches = rule.phraseGroups.mapNotNull { alternatives ->
                alternatives.firstOrNull(normalized::contains)
            }
            if (groupMatches.size != rule.phraseGroups.size) null else MedicalSafetySignal(
                id = rule.id,
                level = rule.level,
                summary = rule.summary,
                matchedText = groupMatches,
                sourceIds = rule.sourceIds
            )
        }
        return MedicalSafetyAssessment(
            level = matches.maxOfOrNull { it.level } ?: MedicalEscalationLevel.NONE,
            signals = matches.sortedByDescending { it.level }
        )
    }

    companion object {
        val defaultRules: List<MedicalRedFlagRule> = listOf(
            MedicalRedFlagRule(
                id = "severe_breathing_difficulty",
                level = MedicalEscalationLevel.EMERGENCY,
                phraseGroups = listOf(setOf("gasping", "choking", "cannot breathe", "cant breathe", "unable to breathe", "cannot get words out", "cant get words out", "blue lips")),
                summary = "Severe breathing difficulty or inability to speak normally can require emergency help.",
                sourceIds = listOf("nhs_heart_attack", "nhs_asthma")
            ),
            MedicalRedFlagRule(
                id = "possible_acute_coronary_pattern",
                level = MedicalEscalationLevel.EMERGENCY,
                phraseGroups = listOf(
                    setOf("chest pain", "chest pressure", "chest tightness", "heavy chest"),
                    setOf("spreads to my arm", "spreading to my arm", "spreads to jaw", "spreading to jaw", "sweating", "fainted", "passed out", "severe shortness of breath")
                ),
                summary = "Chest discomfort with spreading pain, sweating, collapse or severe breathlessness needs emergency assessment.",
                sourceIds = listOf("nhs_heart_attack")
            ),
            MedicalRedFlagRule(
                id = "possible_cauda_equina_pattern",
                level = MedicalEscalationLevel.EMERGENCY,
                phraseGroups = listOf(
                    setOf("back pain", "sciatica", "slipped disc", "herniated disc"),
                    setOf("saddle numbness", "numb between my legs", "genital numbness", "cannot pee", "cant pee", "lost bladder control", "lost bowel control", "new incontinence")
                ),
                summary = "Back or sciatic pain with new saddle sensory change or bladder/bowel dysfunction needs emergency assessment.",
                sourceIds = listOf("nhs_cauda_equina")
            ),
            MedicalRedFlagRule(
                id = "gastrointestinal_bleeding",
                level = MedicalEscalationLevel.EMERGENCY,
                phraseGroups = listOf(setOf("vomiting blood", "vomited blood", "coffee ground vomit", "black sticky stool", "black tarry stool")),
                summary = "Possible gastrointestinal bleeding needs emergency assessment.",
                sourceIds = listOf("nhs_vomiting_blood")
            ),
            MedicalRedFlagRule(
                id = "reflux_alarm_features",
                level = MedicalEscalationLevel.PROMPT,
                phraseGroups = listOf(
                    setOf("heartburn", "acid reflux", "hiatus hernia", "hiatal hernia", "indigestion"),
                    setOf("difficulty swallowing", "food getting stuck", "unintentional weight loss", "losing weight without trying", "repeated vomiting", "keep vomiting")
                ),
                summary = "Reflux-type symptoms with swallowing difficulty, unexplained weight loss or repeated vomiting need prompt professional review.",
                sourceIds = listOf("nhs_hiatus_hernia", "nhs_indigestion")
            ),
            MedicalRedFlagRule(
                id = "self_harm_risk",
                level = MedicalEscalationLevel.EMERGENCY,
                phraseGroups = listOf(setOf("going to kill myself", "plan to kill myself", "about to hurt myself", "cannot keep myself safe", "cant keep myself safe")),
                summary = "Immediate self-harm risk requires urgent crisis or emergency support and human contact now.",
                sourceIds = listOf("nhs_urgent_mental_health")
            )
        )

        val sources: List<MedicalSource> = listOf(
            MedicalSource("nhs_heart_attack", "Heart attack", "NHS", "https://www.nhs.uk/conditions/heart-attack/", MedicalEvidenceType.GOVERNMENT_PATIENT_GUIDANCE, reviewedAt = "2026-08-14"),
            MedicalSource("nhs_asthma", "Asthma", "NHS", "https://www.nhs.uk/conditions/asthma/", MedicalEvidenceType.GOVERNMENT_PATIENT_GUIDANCE, reviewedAt = "2026-08-14"),
            MedicalSource("nhs_cauda_equina", "Cauda equina syndrome", "Buckinghamshire Healthcare NHS Trust", "https://www.buckshealthcare.nhs.uk/pifs/cauda-equina-syndrome/", MedicalEvidenceType.GOVERNMENT_PATIENT_GUIDANCE, reviewedAt = "2026-08-14"),
            MedicalSource("nhs_vomiting_blood", "Vomiting blood", "NHS", "https://www.nhs.uk/symptoms/vomiting-blood/", MedicalEvidenceType.GOVERNMENT_PATIENT_GUIDANCE, reviewedAt = "2026-08-14"),
            MedicalSource("nhs_hiatus_hernia", "Hiatus hernia", "NHS", "https://www.nhs.uk/conditions/hiatus-hernia/", MedicalEvidenceType.GOVERNMENT_PATIENT_GUIDANCE, reviewedAt = "2026-08-14"),
            MedicalSource("nhs_indigestion", "Indigestion", "NHS", "https://www.nhs.uk/conditions/indigestion/", MedicalEvidenceType.GOVERNMENT_PATIENT_GUIDANCE, reviewedAt = "2026-08-14"),
            MedicalSource("nhs_urgent_mental_health", "Where to get urgent help for mental health", "NHS", "https://www.nhs.uk/mental-health/get-urgent-help-for-mental-health/", MedicalEvidenceType.GOVERNMENT_PATIENT_GUIDANCE, reviewedAt = "2026-08-14")
        )
    }
}
