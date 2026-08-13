package com.projectsuperhuman.next.environment

import kotlin.test.Test
import kotlin.test.assertEquals

class EnvironmentalCatalogTest {
    @Test
    fun stable_units_and_condition_mapping() {
        assertEquals(EnvironmentalUnit.CELSIUS, EnvironmentalMetricCatalog.definition(EnvironmentalMetricIds.TEMPERATURE_C)?.unit)
        assertEquals(EnvironmentalUnit.MICROGRAMS_PER_CUBIC_METER, EnvironmentalMetricCatalog.definition(EnvironmentalMetricIds.PM2_5_UG_M3)?.unit)
        assertEquals(EnvironmentalCondition.RAIN, EnvironmentalCondition.fromWmoCode(63))
        assertEquals(EnvironmentalCondition.THUNDERSTORM, EnvironmentalCondition.fromWmoCode(99))
    }
}
