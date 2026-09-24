package com.projectsuperhuman.next

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalFoodSearchAliasTest {
    @Test
    fun rocketAliasesResolveToUsdaArugulaSearchIdentity() {
        assertEquals("arugula", LargeLocalFoodDatabase.retrievalQueryFor("rocket"))
        assertEquals("arugula raw", LargeLocalFoodDatabase.retrievalQueryFor("rocket raw"))
        assertEquals("arugula", LargeLocalFoodDatabase.retrievalQueryFor("wild rocket"))
        assertEquals("arugula raw", LargeLocalFoodDatabase.retrievalQueryFor("wild rocket raw"))
    }

    @Test
    fun existingBritishFoodAliasesRemainStable() {
        assertEquals("zucchini", LargeLocalFoodDatabase.retrievalQueryFor("courgette"))
        assertEquals("eggplant", LargeLocalFoodDatabase.retrievalQueryFor("aubergine"))
        assertEquals("rutabaga", LargeLocalFoodDatabase.retrievalQueryFor("swede"))
        assertEquals("cilantro", LargeLocalFoodDatabase.retrievalQueryFor("coriander"))
    }
}
