package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TrudyLongitudinalRoutingRegressionTest {
    private val day = 86_400_000L
    private val now = 2_000_000_000_000L
    private val boundaries = object : TrudyTemporalBoundaryProvider {
        override fun nowEpochMs(): Long = now
        override fun startOfTodayEpochMs(): Long = now.floorDiv(day) * day
        override fun startOfWeekEpochMs(): Long = startOfTodayEpochMs() - 2 * day
        override fun startOfMonthEpochMs(monthsAgo: Int): Long =
            startOfTodayEpochMs() - (8L + monthsAgo * 30L) * day
    }

    private fun planner(): TrudyPreflightPlanner = TrudyLongitudinalPreflightPlanner(
        TrudyTemporalPlanningDecorator(
            TrudyLanguageAwarePreflightPlanner(
                TrudySystemInvestigationPlanner(TrudyTemporalResolver(boundaries))
            ),
            boundaries
        )
    )

    @Test
    fun tiredLatelyTriggersBoundedMultiSignalInvestigation() {
        val operation = assertIs<InvestigateChange>(
            planner().plan(TrudyAskRequest("Why am I more tired lately?")).single()
        )
        assertTrue(operation.targets.any { it.domain == HealthDomain.SLEEP })
        assertTrue(operation.related.size >= 4)
        assertTrue(operation.related.any { it.domain == HealthDomain.EMOTIONAL })
        assertTrue(operation.related.any { it.domain == HealthDomain.EXERCISE })
    }

    @Test
    fun caffeineSleepLastMonthKeepsFollowingSleepAlignment() {
        val operation = assertIs<GetLaggedAssociation>(
            planner().plan(TrudyAskRequest("Did caffeine affect my sleep last month?")).single()
        )
        assertEquals(HealthDomain.NUTRITION, operation.leftDomain)
        assertEquals("food_caffeine_mg", operation.leftMetricId)
        assertEquals(HealthDomain.SLEEP, operation.rightDomain)
        assertEquals("sleep_score", operation.rightMetricId)
        assertEquals(TrudyTemporalAlignment.PREVIOUS_EVENING_TO_FOLLOWING_SLEEP.lagMs, operation.lagMs)
        assertTrue(operation.window != null)
    }

    @Test
    fun hotterWeatherHeartRateQuestionIncludesTemperatureWithoutCausalClaim() {
        val operation = assertIs<InvestigateChange>(
            planner().plan(TrudyAskRequest("Could the hotter weather explain my heart rate?")).single()
        )
        assertTrue(operation.targets.any { it.metricId == "heart_rate_avg_bpm" || it.metricId == "resting_heart_rate_bpm" })
        assertTrue(operation.related.any {
            it.domain == HealthDomain.ENVIRONMENT && it.metricId == "environment_temperature_c"
        })
        assertTrue(operation.intent == TrudyInvestigationIntent.WHAT_MIGHT_EXPLAIN)
    }
}
