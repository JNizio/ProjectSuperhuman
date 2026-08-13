package com.projectsuperhuman.next.trudy

/**
 * Narrow decorator that adds Emotional planning and response semantics without owning a model
 * provider. Non-Emotional requests pass through unchanged.
 */
class TrudyEmotionalModelClient(
    private val delegate: TrudyModelClient,
    private val maxEvidenceReferences: Int = 24
) : TrudyModelClient {
    init { require(maxEvidenceReferences > 0) }

    override suspend fun complete(request: TrudyModelRequest): TrudyModelResult {
        if (!TrudyEmotionalSemantics.looksEmotionalQuestion(request.userRequest)) {
            return delegate.complete(request)
        }

        if (request.toolResults.isEmpty()) {
            val plan = TrudyEmotionalToolPlanner.initialPlan(request.userRequest).orEmpty()
            if (plan.isNotEmpty()) return planned(plan)
            return delegate.complete(request)
        }

        val successful = request.toolResults.filter { it !is TrudyToolResult.Failure }
        if (successful.isEmpty()) {
            return TrudyModelResult(
                responseText = "I couldn't access reliable Emotional check-ins for that request, so I can't tell from your data right now."
            )
        }

        val followUp = TrudyEmotionalToolPlanner.followUp(request.userRequest, successful)
        if (followUp.isNotEmpty()) return planned(followUp)

        val response = successful.filterIsInstance<AssociationToolResult>().firstOrNull()
            ?.let { TrudyEmotionalResponseComposer.association(request.userRequest, it.association) }
            ?: successful.filterIsInstance<PersonalTrendResult>()
                .mapNotNull { TrudyEmotionalResponseComposer.trend(request.userRequest, it.comparison) }
                .takeIf { it.isNotEmpty() }
                ?.joinToString(" ")
            ?: successful.filterIsInstance<BaselineComparisonResult>().firstOrNull()
                ?.let { TrudyEmotionalResponseComposer.trend(request.userRequest, it.comparison) }

        if (response == null && successful.any { it is TrudyToolResult.Context }) {
            return TrudyModelResult(responseText = "I don't have enough Emotional check-ins yet to answer that from your data.")
        }
        if (response == null) return delegate.complete(request)
        return TrudyModelResult(
            responseText = response,
            evidenceReferences = successful.flatMap(::evidenceReferences).distinct().take(maxEvidenceReferences)
        )
    }

    private fun planned(operations: List<TrudyToolOperation>) = TrudyModelResult(
        requestedTools = operations
    )
}

fun TrudyModelClient.withEmotionalReasoning(): TrudyModelClient =
    if (this is TrudyEmotionalModelClient) this else TrudyEmotionalModelClient(this)
