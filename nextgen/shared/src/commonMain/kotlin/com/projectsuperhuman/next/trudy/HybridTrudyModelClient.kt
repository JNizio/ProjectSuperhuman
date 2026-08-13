package com.projectsuperhuman.next.trudy

/**
 * Uses deterministic Trudy planning for personal tool selection, then lets a language model turn
 * the bounded tool results into natural language. This keeps health retrieval/math authoritative
 * while making the assistant conversational.
 *
 * Hosted failures always fall back to the deterministic client, so Trudy remains usable offline.
 */
class HybridTrudyModelClient(
    private val languageModel: TrudyModelClient,
    private val deterministic: TrudyModelClient = OfflineDeterministicTrudyModelClient(),
    private val maxEvidenceReferences: Int = 24
) : TrudyModelClient {
    init { require(maxEvidenceReferences > 0) }

    override suspend fun complete(request: TrudyModelRequest): TrudyModelResult {
        if (request.toolResults.isEmpty()) {
            val plan = deterministic.complete(request)
            if (plan.requestedTools.isNotEmpty()) return plan
            return runCatching { languageModel.complete(request) }.getOrElse { plan }
        }

        val deterministicFallback by lazy { deterministic }
        return runCatching {
            val generated = languageModel.complete(request)
            val boundEvidence = request.toolResults
                .filter { it !is TrudyToolResult.Failure }
                .flatMap(::evidenceReferences)
                .distinct()
                .take(maxEvidenceReferences)

            generated.copy(
                // Tool planning stays deterministic in this runtime. A hosted model cannot request
                // arbitrary extra operations after seeing personal evidence.
                requestedTools = emptyList(),
                evidenceReferences = boundEvidence
            )
        }.getOrElse {
            deterministicFallback.complete(request)
        }
    }
}
