package com.projectsuperhuman.next.trudy.medical

import com.projectsuperhuman.next.trudy.TrudyDomainContext
import com.projectsuperhuman.next.trudy.TrudyHealthContext
import com.projectsuperhuman.next.trudy.TrudyModelClient
import com.projectsuperhuman.next.trudy.TrudyModelRequest
import com.projectsuperhuman.next.trudy.TrudyModelResult
import com.projectsuperhuman.next.trudy.TrudyToolOperation
import com.projectsuperhuman.next.trudy.TrudyToolResult

/**
 * Augments the existing provider-neutral model. It does not diagnose, calculate health statistics,
 * query SQL, or replace the language model.
 */
class MedicalContextAwareTrudyModelClient(
    private val delegate: TrudyModelClient,
    private val planner: TrudyMedicalContextPlanner,
    private val renderer: TrudyMedicalContextRenderer = TrudyMedicalContextRenderer(),
    private val explicitlyRecordedConditionIds: suspend () -> Set<String> = { emptySet() }
) : TrudyModelClient {
    override suspend fun complete(request: TrudyModelRequest): TrudyModelResult {
        val plan = planner.plan(request.userRequest, explicitlyRecordedConditionIds())
        if (!plan.isMedicalQuestion) return delegate.complete(request)

        val toolContext = request.toolResults.filterIsInstance<TrudyToolResult.Context>().lastOrNull()?.context
        val suppliedContext = request.context ?: toolContext
        val alreadyAttemptedContext = request.toolResults.any { it.operation is TrudyToolOperation.GetContext }
        if (plan.vaultRequest != null && suppliedContext == null && !alreadyAttemptedContext) {
            return TrudyModelResult(requestedTools = listOf(TrudyToolOperation.GetContext(plan.vaultRequest)))
        }

        val boundedContext = suppliedContext?.restrictToMedicalPlan(plan)
        val medicalContext = TrudyMedicalContext(plan, boundedContext)
        return delegate.complete(
            request.copy(
                systemInstruction = request.systemInstruction + "\n\n" + renderer.render(medicalContext),
                context = boundedContext ?: request.context
            )
        )
    }
}

class TrudyMedicalContextRenderer(
    private val maxOptionsPerCondition: Int = 8,
    private val maxVaultRowsPerDomain: Int = 10
) {
    init {
        require(maxOptionsPerCondition in 1..20)
        require(maxVaultRowsPerDomain in 1..30)
    }

    fun render(context: TrudyMedicalContext): String = buildString {
        appendLine("TRUDY MEDICAL MANAGEMENT CONTEXT (structured, non-diagnostic)")
        appendLine("Communication order: answer first; explain only the relevant pattern; state uncertainty when it changes the decision; finish with useful next steps.")
        appendLine("Never convert symptom overlap or Data Vault observations into a diagnosis. Present condition candidates only as possibilities worth considering, including what fits, what does not fit, and what extra information matters.")
        appendLine("Medication content below names common treatment categories only. Never infer an individual prescription, dose, safety clearance, or instruction to stop prescribed medicine.")
        appendLine("Do not recite generic evidence disclaimers mechanically. Use one natural caveat at the point where uncertainty matters.")
        appendLine("Requested intent: ${context.plan.intents.joinToString()}")
        renderSafety(context.plan.safety)
        renderCandidates(context.plan.conditionCandidates)
        renderManagement(context.plan.managementEntries, context.plan.intents)
        renderVault(context.vaultContext, context.plan.managementEntries)
    }.take(MAX_CONTEXT_CHARS)

    private fun StringBuilder.renderSafety(safety: MedicalSafetyAssessment) {
        appendLine("Safety level: ${safety.level}")
        if (safety.signals.isEmpty()) {
            appendLine("No high-specificity red-flag phrase pattern was detected. This is not proof that no red flag exists; do not add alarm language unless the actual question warrants it.")
        } else {
            safety.signals.forEach { signal ->
                appendLine("Safety signal ${signal.id}: ${signal.summary} [sources=${signal.sourceIds.joinToString()}]")
            }
            if (safety.level >= MedicalEscalationLevel.URGENT) {
                appendLine("Lead with the time-sensitive action. Keep supporting explanation brief and do not delay it with Data Vault analysis.")
            }
        }
    }

    private fun StringBuilder.renderCandidates(candidates: List<MedicalConditionCandidate>) {
        if (candidates.isEmpty()) return
        appendLine("Condition retrieval candidates (relevance for retrieval, not diagnostic probability):")
        candidates.take(MAX_CANDIDATES).forEach { candidate ->
            appendLine("- ${candidate.conditionId} / ${candidate.displayName}; relevance=${candidate.relevance}; fits=${candidate.reasonsForFit.joinToString("; ").safe()}; against=${candidate.reasonsAgainstFit.joinToString("; ").ifBlank { "not supplied" }.safe()}; useful_missing=${candidate.informationThatWouldMatter.joinToString("; ").ifBlank { "not supplied" }.safe()}")
        }
    }

    private fun StringBuilder.renderManagement(
        entries: List<MedicalManagementEntry>,
        intents: Set<MedicalQuestionIntent>
    ) {
        entries.take(MAX_CANDIDATES).forEach { entry ->
            appendLine("Management knowledge: ${entry.conditionId} / ${entry.displayName}")
            relevantOptions(entry.options, intents).take(maxOptionsPerCondition).forEach { option ->
                appendLine("- ${option.id}; category=${option.category}; setting=${option.careSetting}; ${option.summary.safe()}; sources=${option.sourceIds.joinToString()}; no_personalized_prescribing=${option.personalizationProhibited}")
            }
            entry.sources.distinctBy { it.id }.forEach { source ->
                appendLine("  source ${source.id}: ${source.organisation}, ${source.title}, ${source.url}, reviewed=${source.reviewedAt}")
            }
        }
    }

    private fun relevantOptions(
        options: List<MedicalManagementOption>,
        intents: Set<MedicalQuestionIntent>
    ): List<MedicalManagementOption> {
        if (MedicalQuestionIntent.GENERAL_MEDICAL_INFORMATION in intents || MedicalQuestionIntent.MANAGEMENT in intents) return options
        val categories = buildSet {
            if (MedicalQuestionIntent.LIFESTYLE_SELF_CARE in intents) addAll(setOf(MedicalManagementCategory.LIFESTYLE, MedicalManagementCategory.BEHAVIOURAL, MedicalManagementCategory.DIETARY, MedicalManagementCategory.PHYSIOTHERAPY_REHABILITATION, MedicalManagementCategory.PSYCHOLOGICAL_THERAPY))
            if (MedicalQuestionIntent.TREATMENT_CATEGORIES in intents) addAll(setOf(MedicalManagementCategory.FIRST_LINE_TREATMENT, MedicalManagementCategory.SECOND_LINE_SPECIALIST, MedicalManagementCategory.PROCEDURE))
            if (MedicalQuestionIntent.MONITORING in intents) add(MedicalManagementCategory.MONITORING)
            if (MedicalQuestionIntent.PREVENTION in intents) add(MedicalManagementCategory.PREVENTION_RISK_REDUCTION)
            if (MedicalQuestionIntent.PROFESSIONAL_EVALUATION in intents || MedicalQuestionIntent.SYMPTOM_POSSIBILITIES in intents) add(MedicalManagementCategory.PROFESSIONAL_EVALUATION)
            add(MedicalManagementCategory.SELF_TREATMENT_LIMIT)
        }
        return options.filter { it.category in categories }.ifEmpty { options }
    }

    private fun StringBuilder.renderVault(
        vault: TrudyHealthContext?,
        entries: List<MedicalManagementEntry>
    ) {
        if (vault == null) {
            appendLine("Data Vault context: unavailable or intentionally skipped. Do not infer missing observations.")
            return
        }
        val relevantMetrics = entries.flatMap { it.relevantVaultSignals }.groupBy { it.domain }
        appendLine("Data Vault context (personal observations; relevance context only, never diagnostic proof):")
        vault.domains.forEach { domain -> renderDomain(domain, relevantMetrics[domain.domain].orEmpty()) }
    }

    private fun StringBuilder.renderDomain(domain: TrudyDomainContext, specs: List<MedicalVaultSignalSpec>) {
        val preferredIds = specs.flatMap { it.metricIds }.toSet()
        val current = domain.currentState
            .filter { preferredIds.isEmpty() || it.metricId in preferredIds }
            .take(maxVaultRowsPerDomain)
        val derived = domain.derivedFeatures
            .filter { preferredIds.isEmpty() || it.metricId in preferredIds }
            .take(maxVaultRowsPerDomain)
        appendLine("- ${domain.domain}: purpose=${specs.joinToString { it.purpose }.safe()}; quality=${domain.dataQuality?.score ?: "unknown"}; stale=${domain.dataQuality?.isStale ?: "unknown"}")
        current.forEach { item -> appendLine("  observed ${item.metricId}=${item.value} ${item.unit} at=${item.timestampEpochMs} source=${item.source.safe()}") }
        derived.forEach { item -> appendLine("  deterministic_summary ${item.metricId}: latest=${item.latest} ${item.unit}, mean=${item.mean}, samples=${item.sampleCount}, range=${item.range.fromEpochMs}-${item.range.toEpochMs}") }
    }

    private fun String.safe(): String = replace(Regex("[\\r\\n]+"), " ").take(MAX_FIELD_CHARS)

    private companion object {
        const val MAX_CANDIDATES = 5
        const val MAX_FIELD_CHARS = 600
        const val MAX_CONTEXT_CHARS = 24_000
    }
}
