package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.trudy.conversation.TrudyAnswerEngineConversationContext
import com.projectsuperhuman.next.trudy.conversation.TrudyConversationEvidenceRecord
import com.projectsuperhuman.next.trudy.conversation.TrudyEvidenceReuseAction
import com.projectsuperhuman.next.trudy.conversation.TrudyEvidenceRole
import com.projectsuperhuman.next.trudy.conversation.TrudyFollowUpKind
import com.projectsuperhuman.next.trudy.conversation.TrudyMissingDataFinding
import com.projectsuperhuman.next.trudy.conversation.TrudyConversationTimeframe

/**
 * Final quality-swarm glue. These adapters deliberately preserve the existing authoritative owners:
 * language routing does lexical interpretation, the system planner/investigator owns retrieval and
 * statistics, and TrudyAnswerEngine owns final user-facing synthesis.
 */
class TrudyLanguageAwarePreflightPlanner(
    private val delegate: TrudyPreflightPlanner
) : TrudyPreflightPlanner {
    override fun plan(request: TrudyAskRequest): List<TrudyToolOperation> {
        val routing = runCatching { TrudyLanguageRouter.route(request.userMessage) }.getOrNull()
            ?: return delegate.plan(request)
        if (routing.productOnlyIntent) return emptyList()
        if (routing.matches.isEmpty()) return delegate.plan(request)

        val anchors = routing.matches.asSequence()
            .mapNotNull { match ->
                when (match.phraseClass) {
                    TrudyPhraseClass.SLEEP -> "sleep"
                    TrudyPhraseClass.FATIGUE_AND_FOCUS -> "tired focus"
                    TrudyPhraseClass.EXERCISE -> "exercise"
                    TrudyPhraseClass.CARDIOVASCULAR_LANGUAGE -> "heart rate"
                    TrudyPhraseClass.NUTRITION_AND_FOOD -> "nutrition"
                    TrudyPhraseClass.HYDRATION -> "hydration"
                    TrudyPhraseClass.BODY_WEIGHT -> "weight"
                    TrudyPhraseClass.EMOTIONAL_LANGUAGE -> "stress mood"
                    TrudyPhraseClass.ENVIRONMENT -> "environment"
                    TrudyPhraseClass.EXPERIMENT_LANGUAGE -> "experiment"
                    TrudyPhraseClass.ABDOMINAL_LANGUAGE,
                    TrudyPhraseClass.BREATHING_LANGUAGE,
                    TrudyPhraseClass.BALANCE_LANGUAGE,
                    TrudyPhraseClass.FORMAL_MEDICAL_LANGUAGE -> null
                }
            }
            .flatMap { it.split(' ').asSequence() }
            .distinct()
            .take(MAX_ANCHORS)
            .toMutableList()
        val subjectiveSleepWorsening = routing.matches.any { it.phraseClass == TrudyPhraseClass.SLEEP } &&
            SUBJECTIVE_WORSENING_TERMS.any { it in routing.normalizedText }
        if (subjectiveSleepWorsening) anchors += listOf("what", "changed", "worse")

        val canonical = routing.matches.asSequence()
            .flatMap { it.canonicalSearchTerms.asSequence() }
            .distinct()
            .take(MAX_CANONICAL_TERMS)
            .toList()
        if (anchors.isEmpty() && canonical.isEmpty()) return delegate.plan(request)

        val planningText = buildString {
            append(request.userMessage.trim())
            append(". ")
            append((anchors + canonical).distinct().joinToString(" "))
        }.take(MAX_PLANNING_TEXT_CHARS)
        return delegate.plan(request.copy(userMessage = planningText))
    }

    private companion object {
        const val MAX_ANCHORS = 10
        const val MAX_CANONICAL_TERMS = 8
        const val MAX_PLANNING_TEXT_CHARS = 1_200
        val SUBJECTIVE_WORSENING_TERMS = listOf(
            "terrible", "awful", "suffered", "suffering", "worse", "bad lately", "slept like crap", "sleep like crap"
        )
    }
}

/** One-turn handoff from the bounded conversation resolver into the existing preflight planner. */
class TrudyConversationPlanningContextHolder {
    var current: TrudyAnswerEngineConversationContext? = null
        private set

    fun set(context: TrudyAnswerEngineConversationContext) {
        current = context
    }

    fun clear() {
        current = null
    }
}

class TrudyConversationAwarePreflightPlanner(
    private val delegate: TrudyPreflightPlanner,
    private val holder: TrudyConversationPlanningContextHolder
) : TrudyPreflightPlanner {
    override fun plan(request: TrudyAskRequest): List<TrudyToolOperation> {
        val context = holder.current ?: return delegate.plan(request)
        val resolved = context.resolvedRequest
        val rawForPlanning = if (resolved.timeframe.label == "roughly the past week") {
            request.userMessage
                .replace("last week or so", "recently", ignoreCase = true)
                .replace("past week or so", "recently", ignoreCase = true)
        } else {
            request.userMessage
        }
        val planningText = buildString {
            append(rawForPlanning.trim())
            resolved.topic?.let { append(". topic ").append(it.replace('_', ' ')) }
            if (resolved.metrics.isNotEmpty()) {
                append(". metrics ")
                append(resolved.metrics.take(MAX_METRICS).joinToString(" ") { it.metricId.replace('_', ' ') })
            }
            resolved.referent.topic?.takeIf { it != resolved.topic }?.let {
                append(". related to ").append(it.replace('_', ' '))
            }
            when (resolved.followUpKind) {
                TrudyFollowUpKind.CROSS_DOMAIN_FOLLOW_UP -> append(". what could explain this")
                TrudyFollowUpKind.MISSING_DATA_FOLLOW_UP -> append(". reading")
                TrudyFollowUpKind.EVIDENCE_EXPLANATION -> append(". what data")
                else -> Unit
            }
            if (resolved.timeframe.label.isNotBlank()) append(". ").append(resolved.timeframe.label)
        }.take(MAX_PLANNING_TEXT_CHARS)

        val planned = delegate.plan(request.copy(userMessage = planningText))
        if (resolved.followUpKind != TrudyFollowUpKind.MISSING_DATA_FOLLOW_UP) return planned

        // "the day before" is resolved structurally by Agent 5 even though the legacy temporal
        // resolver does not understand that phrase. Retarget only this exact missing-data follow-up.
        val exact = resolved.timeframe.range
        return planned.map { operation ->
            when (operation) {
                is TrudyToolOperation.GetMetricWindow -> operation.copy(range = exact)
                is CompareBaseline -> operation.copy(
                    observationWindow = exact,
                    baselineWindow = priorRange(exact)
                )
                else -> operation
            }
        }
    }

    private fun priorRange(range: TrudyTimeRange): TrudyTimeRange {
        val duration = (range.toEpochMs - range.fromEpochMs + 1L).coerceAtLeast(1L)
        val end = (range.fromEpochMs - 1L).coerceAtLeast(0L)
        return TrudyTimeRange((end - duration + 1L).coerceAtLeast(0L), end)
    }

    private companion object {
        const val MAX_METRICS = 12
        const val MAX_PLANNING_TEXT_CHARS = 1_400
    }
}

data class TrudySelectedAnswerEvidence(
    val reference: TrudyEvidenceReference,
    val label: String,
    val summary: String
)

data class TrudyAnswerTurnTrace(
    val plan: TrudyAnswerPlan,
    val usedEvidence: List<TrudySelectedAnswerEvidence>,
    val retrievedRecordCount: Int,
    val investigation: TrudyInvestigationResult?
)

/** Single latest completed model turn only; consumed immediately by TrudyConversationService. */
class TrudyAnswerTurnRegistry {
    private var latest: TrudyAnswerTurnTrace? = null

    fun record(trace: TrudyAnswerTurnTrace) {
        latest = trace
    }

    fun consume(): TrudyAnswerTurnTrace? = latest.also { latest = null }

    fun clear() {
        latest = null
    }
}

/**
 * Agent 2's Answer Engine becomes the sole final synthesis gate without replacing Agent 3's
 * orchestrator. Put this client inside the Emotional/Environmental/Medical decorators so it sees
 * the final safety-enriched system instruction.
 */
class TrudyAnswerEngineModelClient(
    private val delegate: TrudyModelClient,
    private val registry: TrudyAnswerTurnRegistry,
    private val answerEngine: TrudyAnswerEngine = TrudyAnswerEngine()
) : TrudyModelClient {
    override suspend fun complete(request: TrudyModelRequest): TrudyModelResult {
        val basePlan = answerEngine.plan(
            question = request.userRequest,
            results = request.toolResults,
            conversation = request.conversationContext,
            systemInstruction = request.systemInstruction
        )
        val plan = TrudyStructuredInvestigationAnswerBridge.refine(basePlan, request.toolResults)
        val result = delegate.complete(request.copy(answerPlan = plan))
        if (result.requestedTools.isNotEmpty() || result.responseText.isNullOrBlank()) return result

        val answer = answerEngine.finalize(plan, result.responseText)
        val available = request.toolResults.flatMap(::evidenceReferences).distinct()
        val used = TrudyUsedAnswerEvidenceSelector.select(
            plan = plan,
            proposed = result.evidenceReferences,
            available = available
        )
        registry.record(
            TrudyAnswerTurnTrace(
                plan = plan,
                usedEvidence = used,
                retrievedRecordCount = available.size,
                investigation = request.toolResults.filterIsInstance<ChangeInvestigationResult>()
                    .lastOrNull()?.investigation?.structuredResult
            )
        )
        return result.copy(
            responseText = answer,
            evidenceReferences = used.map { it.reference }
        )
    }
}

object TrudyStructuredInvestigationAnswerBridge {
    fun refine(plan: TrudyAnswerPlan, results: List<TrudyToolResult>): TrudyAnswerPlan {
        val structured = results.filterIsInstance<ChangeInvestigationResult>()
            .lastOrNull()?.investigation?.structuredResult ?: return plan

        val findings = structured.importantFindings.associateBy { it.domain to it.metricId }
        val gaps = structured.missingEvidence.associateBy { it.domain to it.metricId }

        fun refineEvidence(evidence: TrudyAnswerEvidence): TrudyAnswerEvidence {
            if (evidence.kind == TrudyAnswerEvidenceKind.PREMISE) {
                val classification = when (structured.premiseStatus) {
                    TrudyPremiseStatus.PREMISE_SUPPORTED -> TrudyAnswerEvidenceClass.USABLE
                    TrudyPremiseStatus.PREMISE_NOT_SUPPORTED,
                    TrudyPremiseStatus.PREMISE_MIXED -> TrudyAnswerEvidenceClass.CONTRADICTORY
                    TrudyPremiseStatus.PREMISE_UNVERIFIABLE -> TrudyAnswerEvidenceClass.MISSING
                    TrudyPremiseStatus.NOT_APPLICABLE -> TrudyAnswerEvidenceClass.IRRELEVANT
                }
                val summary = when (structured.premiseStatus) {
                    TrudyPremiseStatus.PREMISE_SUPPORTED -> "The recorded target metrics support the stated change."
                    TrudyPremiseStatus.PREMISE_NOT_SUPPORTED -> "The recorded target metrics do not support the stated deterioration."
                    TrudyPremiseStatus.PREMISE_MIXED -> "The recorded target metrics moved in mixed directions."
                    TrudyPremiseStatus.PREMISE_UNVERIFIABLE -> "There are not enough comparable records to verify the stated change."
                    TrudyPremiseStatus.NOT_APPLICABLE -> "No directional premise needed verification."
                }
                return evidence.copy(classification = classification, summary = summary, relevanceScore = evidence.relevanceScore + 25.0)
            }

            val key = evidence.domain to evidence.metricId
            gaps[key]?.let { gap ->
                return evidence.copy(
                    classification = gap.reason.toAnswerClass(),
                    relevanceScore = evidence.relevanceScore - 80.0
                )
            }
            findings[key]?.let { finding ->
                return evidence.copy(
                    classification = finding.classification.toAnswerClass(),
                    sampleCount = finding.quality.sampleCount,
                    qualityScore = (finding.quality.score * 100.0).toInt().coerceIn(0, 100),
                    magnitude = finding.percentDelta?.let { kotlin.math.abs(it) / 100.0 }
                        ?: finding.standardizedEffect?.let { kotlin.math.abs(it) },
                    relevanceScore = evidence.relevanceScore + finding.priorityScore * 24.0
                )
            }

            if (evidence.kind == TrudyAnswerEvidenceKind.ASSOCIATION) {
                val related = structured.relatedSignals.firstOrNull { signal ->
                    val left = evidence.associationLeftMetricId
                    val right = evidence.associationRightMetricId
                    (signal.metricId == left && signal.targetMetricId == right) ||
                        (signal.metricId == right && signal.targetMetricId == left)
                }
                if (related != null) {
                    return evidence.copy(
                        classification = related.classification.toAnswerClass(),
                        sampleCount = related.sampleCount,
                        qualityScore = (related.quality.score * 100.0).toInt().coerceIn(0, 100),
                        magnitude = related.coefficient?.let { kotlin.math.abs(it) },
                        relevanceScore = evidence.relevanceScore + related.relationshipScore * 20.0
                    )
                }
            }
            return evidence
        }

        val all = plan.allEvidence.map(::refineEvidence).toMutableList()
        val gapEvidence = structured.missingEvidence.map { gap ->
            TrudyAnswerEvidence(
                id = "structured-gap:${gap.domain}:${gap.metricId}:${gap.reason}",
                classification = gap.reason.toAnswerClass(),
                kind = TrudyAnswerEvidenceKind.AVAILABILITY,
                domain = gap.domain,
                metricId = gap.metricId,
                label = gap.label,
                summary = gap.reason.toNaturalLimitation(),
                relevanceScore = -40.0
            )
        }
        all += gapEvidence

        val ranked = plan.rankedEvidence.map(::refineEvidence)
            .filter { it.classification in RANKABLE_CLASSES }
            .sortedByDescending { it.relevanceScore }
            .take(8)
        val limitations = (plan.limitations.map(::refineEvidence) + gapEvidence)
            .filter { it.classification !in RANKABLE_CLASSES }
            .distinctBy { Triple(it.domain, it.metricId, it.classification) }
            .take(8)

        return plan.copy(
            timeframeLabel = structured.timeframe.label,
            allEvidence = all.distinctBy { it.id },
            rankedEvidence = ranked,
            limitations = limitations
        )
    }

    private val RANKABLE_CLASSES = setOf(
        TrudyAnswerEvidenceClass.USABLE,
        TrudyAnswerEvidenceClass.SUPPORTING,
        TrudyAnswerEvidenceClass.CONTRADICTORY
    )

    private fun TrudyFindingClassification.toAnswerClass(): TrudyAnswerEvidenceClass = when (this) {
        TrudyFindingClassification.OBSERVED_CHANGE -> TrudyAnswerEvidenceClass.USABLE
        TrudyFindingClassification.POSSIBLE_ASSOCIATION -> TrudyAnswerEvidenceClass.SUPPORTING
        TrudyFindingClassification.NO_CLEAR_ASSOCIATION -> TrudyAnswerEvidenceClass.LOW_QUALITY
        TrudyFindingClassification.INSUFFICIENT_EVIDENCE -> TrudyAnswerEvidenceClass.MISSING
        TrudyFindingClassification.CONTRADICTORY_EVIDENCE -> TrudyAnswerEvidenceClass.CONTRADICTORY
    }

    private fun TrudyEvidenceGapReason.toAnswerClass(): TrudyAnswerEvidenceClass = when (this) {
        TrudyEvidenceGapReason.NO_DATA,
        TrudyEvidenceGapReason.NOT_CONNECTED -> TrudyAnswerEvidenceClass.MISSING
        TrudyEvidenceGapReason.STALE_DATA -> TrudyAnswerEvidenceClass.STALE
        TrudyEvidenceGapReason.SPARSE_DATA,
        TrudyEvidenceGapReason.LOW_ALIGNMENT,
        TrudyEvidenceGapReason.LOW_VARIANCE,
        TrudyEvidenceGapReason.UNMEASURED_CONFOUNDER -> TrudyAnswerEvidenceClass.LOW_QUALITY
    }

    private fun TrudyEvidenceGapReason.toNaturalLimitation(): String = when (this) {
        TrudyEvidenceGapReason.NO_DATA -> "No matching measurement was recorded for this period."
        TrudyEvidenceGapReason.SPARSE_DATA -> "There are too few measurements in this period to rely on this signal."
        TrudyEvidenceGapReason.STALE_DATA -> "The available reading is too old to judge the requested period."
        TrudyEvidenceGapReason.LOW_ALIGNMENT -> "Too few measurements line up at the relevant times."
        TrudyEvidenceGapReason.LOW_VARIANCE -> "The signal changed too little to estimate a useful relationship."
        TrudyEvidenceGapReason.UNMEASURED_CONFOUNDER -> "An important possible influence was not measured."
        TrudyEvidenceGapReason.NOT_CONNECTED -> "That data source is not connected."
    }
}

object TrudyUsedAnswerEvidenceSelector {
    fun select(
        plan: TrudyAnswerPlan,
        proposed: List<TrudyEvidenceReference>,
        available: List<TrudyEvidenceReference>
    ): List<TrudySelectedAnswerEvidence> {
        if (available.isEmpty()) return emptyList()
        val exactProposed = proposed.mapNotNull { requested ->
            available.firstOrNull { it.exactlyMatches(requested) }
        }.distinct()
        val candidatePool = exactProposed.ifEmpty { available }
        val selected = mutableListOf<TrudySelectedAnswerEvidence>()

        plan.rankedEvidence.take(MAX_FINDINGS).forEach { finding ->
            if (finding.classification !in USED_CLASSES) return@forEach
            val matching = candidatePool.asSequence()
                .filter { reference -> reference.supports(finding) }
                .sortedByDescending { it.timestampEpochMs ?: it.range?.toEpochMs ?: Long.MIN_VALUE }
                .take(MAX_REFERENCES_PER_FINDING)
                .toList()
            matching.forEach { reference ->
                if (selected.none { it.reference == reference }) {
                    selected += TrudySelectedAnswerEvidence(reference, finding.label, finding.summary)
                }
            }
        }

        if (selected.isEmpty() && exactProposed.isNotEmpty()) {
            exactProposed.take(MAX_TOTAL_REFERENCES).forEach { reference ->
                selected += TrudySelectedAnswerEvidence(
                    reference = reference,
                    label = reference.metricId?.let(::humanMetricLabel) ?: reference.insightId.orEmpty().replace('_', ' '),
                    summary = "Used to support the answer."
                )
            }
        }
        return selected.take(MAX_TOTAL_REFERENCES)
    }

    private fun TrudyEvidenceReference.supports(finding: TrudyAnswerEvidence): Boolean {
        if (finding.domain != null && domain != finding.domain &&
            metricId != finding.associationLeftMetricId && metricId != finding.associationRightMetricId
        ) return false
        if (finding.metricId != null && metricId == finding.metricId) return true
        if (finding.associationLeftMetricId != null && metricId == finding.associationLeftMetricId) return true
        if (finding.associationRightMetricId != null && metricId == finding.associationRightMetricId) return true
        if (finding.kind == TrudyAnswerEvidenceKind.INSIGHT && insightId != null && finding.id.endsWith(insightId)) return true
        return false
    }

    private fun TrudyEvidenceReference.exactlyMatches(other: TrudyEvidenceReference): Boolean =
        domain == other.domain && metricId == other.metricId && insightId == other.insightId &&
            evidenceKind == other.evidenceKind && timestampEpochMs == other.timestampEpochMs && range == other.range

    private val USED_CLASSES = setOf(
        TrudyAnswerEvidenceClass.USABLE,
        TrudyAnswerEvidenceClass.SUPPORTING,
        TrudyAnswerEvidenceClass.CONTRADICTORY
    )
    private const val MAX_FINDINGS = 4
    private const val MAX_REFERENCES_PER_FINDING = 5
    private const val MAX_TOTAL_REFERENCES = 12
}

/** Build an Answer Engine plan from answer-selected, already-computed in-session evidence only. */
object TrudyConversationReuseAnswerPlanner {
    fun plan(context: TrudyAnswerEngineConversationContext): TrudyAnswerPlan {
        val reusable = context.retrieval.reusableEvidence.map { record ->
            TrudyAnswerEvidence(
                id = "cached:${record.id}",
                classification = if (record.role == TrudyEvidenceRole.DATA_GAP) {
                    TrudyAnswerEvidenceClass.MISSING
                } else {
                    TrudyAnswerEvidenceClass.SUPPORTING
                },
                kind = TrudyAnswerEvidenceKind.OBSERVATION,
                domain = record.identity.domain,
                metricId = record.identity.metricId,
                label = record.identity.metricId?.let(::humanMetricLabel)
                    ?: record.identity.insightId.orEmpty().replace('_', ' '),
                summary = record.displayValue ?: "Previously used in this conversation.",
                sampleCount = 1,
                relevanceScore = 80.0
            )
        }
        val missing = context.retrieval.missingData.flatMap { finding ->
            finding.metricIds.map { metricId ->
                TrudyAnswerEvidence(
                    id = "cached-missing:${finding.domain}:$metricId:${finding.timeframe.range.fromEpochMs}",
                    classification = TrudyAnswerEvidenceClass.MISSING,
                    kind = TrudyAnswerEvidenceKind.OBSERVATION,
                    domain = finding.domain,
                    metricId = metricId,
                    label = humanMetricLabel(metricId),
                    summary = "No recorded measurement was available for ${finding.timeframe.label}."
                )
            }
        }
        val intent = when (context.resolvedRequest.followUpKind) {
            TrudyFollowUpKind.EVIDENCE_EXPLANATION -> TrudyAnswerIntent.EVIDENCE_FOLLOW_UP
            TrudyFollowUpKind.MISSING_DATA_FOLLOW_UP -> TrudyAnswerIntent.READING
            TrudyFollowUpKind.SAME_SCOPE -> TrudyAnswerIntent.PERSONAL_SUMMARY
            else -> TrudyAnswerIntent.GENERAL
        }
        return TrudyAnswerPlan(
            intent = intent,
            timeframeLabel = context.resolvedRequest.timeframe.label,
            allEvidence = reusable + missing,
            rankedEvidence = reusable.filter { it.classification == TrudyAnswerEvidenceClass.SUPPORTING },
            limitations = missing,
            targetSentenceCount = if (intent == TrudyAnswerIntent.EVIDENCE_FOLLOW_UP) 3 else 2
        )
    }
}

fun TrudyAnswerEngine.synthesizeReusedConversation(context: TrudyAnswerEngineConversationContext): String =
    synthesize(TrudyConversationReuseAnswerPlanner.plan(context))

fun TrudyAnswerEngineConversationContext.shouldReuseWithoutRetrieval(): Boolean =
    retrieval.action == TrudyEvidenceReuseAction.REUSE || retrieval.action == TrudyEvidenceReuseAction.REUSE_MISSING_DATA

fun TrudyConversationEvidenceRecord.toEvidenceReference(): TrudyEvidenceReference = TrudyEvidenceReference(
    domain = identity.domain,
    metricId = identity.metricId,
    insightId = identity.insightId,
    evidenceKind = identity.evidenceKind,
    timestampEpochMs = identity.timestampEpochMs,
    range = identity.range
)

fun TrudyEvidenceReference.toConversationIdentity() =
    com.projectsuperhuman.next.trudy.conversation.TrudyConversationEvidenceIdentity(
        domain = domain,
        metricId = metricId,
        insightId = insightId,
        evidenceKind = evidenceKind,
        timestampEpochMs = timestampEpochMs,
        range = range
    )

fun humanMetricLabel(metricId: String): String = metricId
    .removePrefix("environment_")
    .removePrefix("emotional_")
    .removePrefix("body_")
    .removePrefix("food_")
    .replace("_bpm", "")
    .replace("_mmhg", "")
    .replace("_kg", "")
    .replace("_pct", "")
    .replace("_minutes", " time")
    .replace('_', ' ')
    .trim()
    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

fun missingFinding(
    domain: HealthDomain,
    metricIds: List<String>,
    timeframe: TrudyConversationTimeframe,
    checkedAtEpochMs: Long
) = TrudyMissingDataFinding(domain, metricIds.distinct(), timeframe, checkedAtEpochMs)
