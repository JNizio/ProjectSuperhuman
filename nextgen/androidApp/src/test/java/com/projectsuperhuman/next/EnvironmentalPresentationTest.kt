package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertEquals

class EnvironmentalPresentationTest {
    @Test
    fun emptyDataBecomesNoData() {
        assertEquals(
            EnvironmentalLoadResult.NoData,
            normalizeEnvironmentalResult(EnvironmentalLoadResult.Data(EnvironmentalConditionsUi()))
        )
    }

    @Test
    fun homeTileCapsSupportingValuesAtTwo() {
        val conditions = EnvironmentalConditionsUi(metrics = listOf(
            EnvironmentalMetricUi(EnvironmentalMetricKind.TEMPERATURE, "Temperature", "22", "°C"),
            EnvironmentalMetricUi(EnvironmentalMetricKind.HUMIDITY, "Humidity", "55", "%"),
            EnvironmentalMetricUi(EnvironmentalMetricKind.UV, "UV", "3"),
            EnvironmentalMetricUi(EnvironmentalMetricKind.WIND, "Wind", "9", "km/h")
        ))
        assertEquals(2, conditions.homeSupportingMetrics().size)
    }
}
