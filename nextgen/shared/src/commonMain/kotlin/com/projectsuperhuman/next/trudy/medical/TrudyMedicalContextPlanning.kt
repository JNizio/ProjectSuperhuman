package com.projectsuperhuman.next.trudy.medical

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.trudy.TrudyContextRequest
import com.projectsuperhuman.next.trudy.TrudyHealthContext

enum class MedicalQuestionIntent {
    MANAGEMENT,
    LIFESTYLE_SELF_CARE,
    TREATMENT_CATEGORIES,
    MONITORING,
    PREVENTION,
    PROFESSIONAL_EVALUATION,
    SYMPTOM_POSSIBILITIES,
    GENERAL_MEDICAL_INFORMATION
}

data class TrudyMedicalContextPlan(
    val isMedicalQuestion: Boolean,
    val intents: Set<MedicalQuestionIntent>,
    val conditionCandidates: List<MedicalConditionCandidate>,
    val managementEntries: List<MedicalManagementEntry>,
    val safety: MedicalSafetyAssessment,
    val vaultRequest: TrudyContextRequest?,
    val communicationPlan: MedicalCommunicationPlan
)

data class MedicalCommunicationPlan(
    val answerFirst: Boolean = true,
    val explainRelevantPattern: Boolean = true,
    val mentionUncertaintyWhenMaterial: Boolean = true,
    val finishWithUsefulNextSteps: Boolean = true,
    val leadWithEscalation: Boolean = false,
    val prohibitedMoves: Set<String> = setOf(
        "diagnose_from_symptom_overlap",
        "treat_data_vault_observations_as_diagnostic_proof",
        "personalize_prescription_or_dose",
        "tell_user_to_stop_prescribed_medication",
        "offer_false_reassurance_from_missing_data",
        "repeat_generic_causation_disclaimers_mechanically"
    )
)

class TrudyMedicalContextPlanner(
    private val knowledge: MedicalKnowledgeProvider,
    private val conditionCandidates: MedicalConditionCandidateProvider = EmptyMedicalConditionCandidateProvider,
    private val safetySignals: MedicalSafetySignalProvider = CuratedMedicalSafetySignalProvider()
) {
    suspend fun plan(
        question: String,
        explicitlyRecordedConditionIds: Set<String> = emptySet()
    ): TrudyMedicalContextPlan {
        require(question.isNotBlank())
        val directMatches = knowledge.searchManagement(question, MAX_CONDITIONS)
        val corpusCandidates = conditionCandidates.candidates(
            MedicalConditionCandidateRequest(question, explicitlyRecordedConditionIds, MAX_CONDITIONS)
        ).take(MAX_CONDITIONS)
        val allIds = buildSet {
            addAll(explicitlyRecordedConditionIds)
            addAll(directMatches.map { it.conditionId })
            addAll(corpusCandidates.map { it.conditionId })
        }
        val entries = (directMatches.map { it.entry } + knowledge.byConditionIds(allIds))
            .distinctBy { it.conditionId }
            .take(MAX_CONDITIONS)
        val intents = classifyIntents(question)
        val safety = safetySignals.assess(question)
        val isMedical = entries.isNotEmpty() || corpusCandidates.isNotEmpty() || safety.level != MedicalEscalationLevel.NONE ||
            looksMedical(question)
        val vaultRequest = if (!isMedical || safety.level >= MedicalEscalationLevel.URGENT) null else {
            entries.toVaultRequest()
        }
        return TrudyMedicalContextPlan(
            isMedicalQuestion = isMedical,
            intents = intents,
            conditionCandidates = corpusCandidates,
            managementEntries = entries,
            safety = safety,
            vaultRequest = vaultRequest,
            communicationPlan = MedicalCommunicationPlan(
                leadWithEscalation = safety.level >= MedicalEscalationLevel.URGENT
            )
        )
    }

    private fun List<MedicalManagementEntry>.toVaultRequest(): TrudyContextRequest? {
        val specs = flatMap { it.relevantVaultSignals }
        val domains = specs.map { it.domain }.distinct().take(MAX_DOMAINS)
        if (domains.isEmpty()) return null
        val limit = specs.maxOfOrNull { it.historyLimit }?.coerceIn(1, MAX_HISTORY_PER_DOMAIN) ?: 120
        return TrudyContextRequest(
            domains = domains,
            historyLimitPerDomain = limit,
            includeCurrentState = true,
            includeHistory = true,
            includeDerivedFeatures = true,
            includeInsights = true,
            includeDataQuality = true
        )
    }

    private fun classifyIntents(question: String): Set<MedicalQuestionIntent> {
        val text = question.lowercase()
        return buildSet {
            if (containsAny(text, "manage", "management", "help with", "what can i do", "self care")) add(MedicalQuestionIntent.MANAGEMENT)
            if (containsAny(text, "lifestyle", "diet", "exercise", "sleep", "at home", "self care")) add(MedicalQuestionIntent.LIFESTYLE_SELF_CARE)
            if (containsAny(text, "treatment", "therapy", "medicine", "medication", "surgery", "procedure")) add(MedicalQuestionIntent.TREATMENT_CATEGORIES)
            if (containsAny(text, "monitor", "track", "measure", "follow up")) add(MedicalQuestionIntent.MONITORING)
            if (containsAny(text, "prevent", "risk", "avoid getting")) add(MedicalQuestionIntent.PREVENTION)
            if (containsAny(text, "doctor", "gp", "professional", "get checked", "evaluation", "urgent")) add(MedicalQuestionIntent.PROFESSIONAL_EVALUATION)
            if (containsAny(text, "could this be", "what could", "possibilities", "does this fit", "symptom")) add(MedicalQuestionIntent.SYMPTOM_POSSIBILITIES)
            if (isEmpty()) add(MedicalQuestionIntent.GENERAL_MEDICAL_INFORMATION)
        }
    }

    private fun looksMedical(question: String): Boolean {
        val text = question.lowercase()
        return containsAny(
            text,
            "symptom", "condition", "diagnosis", "treatment", "therapy", "medicine", "medication",
            "pain", "numb", "breath", "heart rate", "blood pressure", "vomit", "bleeding", "doctor", "gp"
        )
    }

    private fun containsAny(text: String, vararg terms: String): Boolean = terms.any(text::contains)

    private companion object {
        const val MAX_CONDITIONS = 5
        const val MAX_DOMAINS = 6
        const val MAX_HISTORY_PER_DOMAIN = 180
    }
}

data class TrudyMedicalContext(
    val plan: TrudyMedicalContextPlan,
    val vaultContext: TrudyHealthContext?
)

/** Keeps only the exact, bounded domains selected by the management plan. */
fun TrudyHealthContext.restrictToMedicalPlan(plan: TrudyMedicalContextPlan): TrudyHealthContext? {
    val requested = plan.vaultRequest?.domains ?: return null
    val selected = domains.filter { it.domain in requested }.associateBy { it.domain }
    if (requested.any { it !in selected }) return null
    return TrudyHealthContext(requested, requested.map(selected::getValue))
}
