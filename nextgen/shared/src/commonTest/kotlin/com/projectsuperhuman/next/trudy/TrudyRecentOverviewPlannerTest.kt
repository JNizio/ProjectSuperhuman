package com.projectsuperhuman.next.trudy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrudyRecentOverviewPlannerTest {
    private val boundaries = object : TrudyTemporalBoundaryProvider {
        override fun nowEpochMs() = 30L * DAY_MS + 12L * HOUR_MS
        override fun startOfTodayEpochMs() = 30L * DAY_MS
        override fun startOfWeekEpochMs() = 28L * DAY_MS
        override fun startOfMonthEpochMs(monthsAgo: Int) = (30L - monthsAgo * 30L) * DAY_MS
    }

    private val planner = TrudySystemInvestigationPlanner(TrudyTemporalResolver(boundaries))

    @Test
    fun whatShouldIKnowPlansBoundedCrossDomainOverview() {
        val operation = planner.plan(TrudyAskRequest("What should I know recently?"))
            .filterIsInstance<InvestigateChange>()
            .single()

        assertEquals(TrudyInvestigationIntent.WHAT_CHANGED, operation.intent)
        assertEquals("recent health overview", operation.targetLabel)
        assertEquals(8, operation.targets.size)
        assertEquals(operation.targets.size, operation.targets.map { it.domain }.distinct().size)
        assertTrue(operation.related.isEmpty())
        assertEquals(8, operation.budget.maxTargets)
        assertEquals(3, operation.budget.maxImportantFindings)
        assertTrue(operation.budget.maxRowsPerMetric <= 256)
        assertEquals("recently", operation.timeframeLabel)
    }

    @Test
    fun naturalOverviewAliasCanonicalizesIntoSameBoundedPlan() {
        val planningText = TrudyRecentOverviewAnswerQuality.planningText("Give me a health overview this week")
        val operation = planner.plan(TrudyAskRequest(planningText))
            .filterIsInstance<InvestigateChange>()
            .single()

        assertEquals("recent health overview", operation.targetLabel)
        assertEquals("this week", operation.timeframeLabel)
        assertEquals(8, operation.targets.size)
        assertEquals(8, operation.targets.map { it.domain }.distinct().size)
        assertEquals(0, operation.maxAssociations)
        assertTrue(operation.related.isEmpty())
    }

    @Test
    fun anythingUnusualAlsoUsesOverviewWithoutUnboundedAssociationSearch() {
        val operation = planner.plan(TrudyAskRequest("Anything unusual recently?"))
            .filterIsInstance<InvestigateChange>()
            .single()

        assertEquals("recent health overview", operation.targetLabel)
        assertTrue(operation.related.isEmpty())
        assertEquals(0, operation.maxAssociations)
        assertEquals(3, operation.budget.maxImportantFindings)
    }

    @Test
    fun namedSleepChangeKeepsFocusedInvestigation() {
        val operation = planner.plan(TrudyAskRequest("What changed with my sleep this week?"))
            .filterIsInstance<InvestigateChange>()
            .single()

        assertEquals("sleep", operation.targetLabel)
        assertTrue(operation.related.any { it.metricId == "food_caffeine_mg" })
        assertEquals(5, operation.budget.maxTargets)
        assertEquals(5, operation.budget.maxImportantFindings)
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val HOUR_MS = 3_600_000L
    }
}
