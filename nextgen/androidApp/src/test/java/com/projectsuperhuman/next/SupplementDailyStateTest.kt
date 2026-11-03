package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupplementDailyStateTest {
    @Test
    fun reflectsRoutinePillCountsWithoutAssumingTaken() {
        val supplements = listOf(
            SupplementRoutineItem("a", "Vitamin D", pillCount = 1, time = "Morning"),
            SupplementRoutineItem("b", "Omega-3", pillCount = 2, time = "Evening"),
            SupplementRoutineItem("c", "Magnesium", pillCount = 1, time = "Anytime")
        )
        val empty = SupplementDailyState("2026-11-03", supplements)
        assertEquals(4, empty.totalPills)
        assertEquals(0, empty.takenPills)

        val logged = empty.copy(taken = setOf(
            SupplementDailyState.key("a", 0),
            SupplementDailyState.key("b", 1)
        ))
        assertEquals(2, logged.takenPills)
        assertEquals(4, logged.totalPills)
    }

    @Test
    fun ignoresUnknownAndOutOfRangeCapsulesInDisplayCount() {
        val state = SupplementDailyState(
            "2026-11-03",
            listOf(SupplementRoutineItem("daily", "Vitamin C", pillCount = 1)),
            taken = setOf("daily:0", "daily:10", "removed:0")
        )
        assertEquals(1, state.takenPills)
    }

    @Test
    fun nextDateHasAnIndependentIntakeState() {
        val routine = listOf(SupplementRoutineItem("fish-oil", "Omega-3", pillCount = 2))
        val dayOne = SupplementDailyState(
            "2026-11-03", routine, taken = setOf("fish-oil:0"))
        val dayTwo = SupplementDailyState("2026-11-04", routine)
        assertEquals(1, dayOne.takenPills)
        assertEquals(0, dayTwo.takenPills)
        assertTrue(dayTwo.routine == dayOne.routine)
    }
}
