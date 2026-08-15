package com.projectsuperhuman.next.trudy

/**
 * Resolve answer-visible evidence from the exact structured findings that survived overview
 * selection. This avoids depending on a model's pre-rewrite citation choices after the deterministic
 * overview briefing has replaced its prose.
 */
fun TrudyRecentOverviewBriefing.structuredSupportingReferences(
    investigation: TrudyInvestigationResult
): List<TrudyEvidenceReference> = investigation.importantFindings
    .asSequence()
    .filter { finding -> (finding.domain to finding.metricId) in selectedMetrics }
    .flatMap { it.evidence.supportingEvidenceReferences.asSequence() }
    .filter { reference -> TrudyRecentOverviewAnswerQuality.shouldKeepReference(this, reference) }
    .distinct()
    .toList()
