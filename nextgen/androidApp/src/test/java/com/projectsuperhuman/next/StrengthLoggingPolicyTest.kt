package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertEquals

class StrengthLoggingPolicyTest {
    @Test
    fun rapidSetsGetDistinctTimestamps() {
        val first = nextStrengthSetTimestamp(1000L, emptyList())
        val second = nextStrengthSetTimestamp(1000L, listOf(first))
        val third = nextStrengthSetTimestamp(999L, listOf(first, second))
        assertEquals(1000L, first)
        assertEquals(1001L, second)
        assertEquals(1002L, third)
    }

    @Test
    fun clockAdvancesNormallyWithNoRecentCollision() {
        assertEquals(5000L, nextStrengthSetTimestamp(5000L, listOf(100L, 300L)))
    }
}
