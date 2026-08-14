package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InsightsPresentationTest {
    @Test
    fun mockInsightsHaveStableUniqueIdsAndBoundedStrength() {
        val insights = MockInsightsPresentationProvider.load().insights

        assertEquals(insights.size, insights.map { it.id }.distinct().size)
        assertTrue(insights.all { it.strength in 0f..1f })
        assertTrue(insights.all { it.sampleCount > 0 })
    }

    @Test
    fun timelineValuesRemainPresentationNormalized() {
        val points = MockInsightsPresentationProvider.load().insights.flatMap { it.timeline }

        assertTrue(points.isNotEmpty())
        assertTrue(points.all { it.sourceLevel in 0f..1f && it.targetLevel in 0f..1f })
    }

    @Test
    fun providerContractCanBeReplacedWithoutScreenModelChanges() {
        val replacement = InsightsPresentationProvider {
            InsightsPresentationState("No patterns yet", "More history is needed.", emptyList())
        }

        assertTrue(replacement.load().insights.isEmpty())
    }
}
