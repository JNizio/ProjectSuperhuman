package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertEquals

class CardioHubPreferencesTest {
    @Test
    fun defaultQuickStartsUseSwimmingAsFourthActivity() {
        assertEquals(
            listOf(
                CardioActivityType.RUNNING,
                CardioActivityType.WALKING,
                CardioActivityType.CYCLING,
                CardioActivityType.SWIMMING
            ),
            CardioHubPreferences.DEFAULT_QUICK_ACTIVITIES
        )
    }

    @Test
    fun replacingQuickStartSwapsExistingActivityInsteadOfDuplicatingIt() {
        val current = CardioHubPreferences.DEFAULT_QUICK_ACTIVITIES
        val updated = replaceCardioQuickActivity(
            current = current,
            slot = 0,
            replacement = CardioActivityType.SWIMMING
        )

        assertEquals(CardioActivityType.SWIMMING, updated[0])
        assertEquals(CardioActivityType.RUNNING, updated[3])
        assertEquals(4, updated.distinct().size)
    }

    @Test
    fun replacingQuickStartAcceptsNewActivity() {
        val updated = replaceCardioQuickActivity(
            current = CardioHubPreferences.DEFAULT_QUICK_ACTIVITIES,
            slot = 3,
            replacement = CardioActivityType.ROWING
        )

        assertEquals(CardioActivityType.ROWING, updated[3])
        assertEquals(4, updated.distinct().size)
    }
}
