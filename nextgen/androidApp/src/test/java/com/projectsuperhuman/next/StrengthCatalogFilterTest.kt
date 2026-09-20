package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrengthCatalogFilterTest {
    @Test
    fun removesClearlyCardioOnlyRepDbMovements() {
        assertTrue(isCardioOnlyRepDbExercise("treadmill_run", "Treadmill Running", "Treadmill"))
        assertTrue(isCardioOnlyRepDbExercise("air_bike", "Air Bike", "Machine"))
        assertTrue(isCardioOnlyRepDbExercise("rowing_erg", "Rowing Machine", "Rower"))
        assertTrue(isCardioOnlyRepDbExercise("jump_rope", "Jump Rope", "Rope"))
        assertTrue(isCardioOnlyRepDbExercise("stair_climber", "Stair Climber", "Machine"))
    }

    @Test
    fun preservesStrengthMovementsThatContainCardioAdjacentWords() {
        assertFalse(isCardioOnlyRepDbExercise("seated_cable_row", "Seated Cable Row", "Cable"))
        assertFalse(isCardioOnlyRepDbExercise("barbell_row", "Bent Over Barbell Row", "Barbell"))
        assertFalse(isCardioOnlyRepDbExercise("sled_push", "Sled Push", "Sled"))
    }
}
