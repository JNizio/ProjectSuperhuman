package com.projectsuperhuman.next.environment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnvironmentalRequestTest {
    @Test
    fun open_meteo_request_uses_coarse_coordinates_and_canonical_units() {
        val request = OpenMeteoRequestBuilder.weather(EnvironmentalCoordinates(10.1234, 20.5678))
        assertEquals("10.12", request.query["latitude"])
        assertEquals("20.57", request.query["longitude"])
        assertEquals("ms", request.query["wind_speed_unit"])
        assertEquals("mm", request.query["precipitation_unit"])
        assertEquals("unixtime", request.query["timeformat"])
        assertTrue(request.query.getValue("current").contains("surface_pressure"))
    }
}
