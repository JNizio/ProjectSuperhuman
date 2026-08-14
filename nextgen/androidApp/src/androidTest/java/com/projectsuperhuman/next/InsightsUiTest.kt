package com.projectsuperhuman.next

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class InsightsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyProviderShowsHistoryBuildingState() {
        composeRule.setContent {
            MaterialTheme {
                InsightsScreen(
                    state = InsightsPresentationState("No patterns yet", "More history is needed.", emptyList()),
                    onBack = {},
                    onOpenInsight = {}
                )
            }
        }

        composeRule.onNodeWithTag("insights_empty_state").assertExists()
    }

    @Test
    fun tappingInsightOpensPairedTimelineDetail() {
        composeRule.setContent {
            MaterialTheme { NativeInsightsPage(onBack = {}) }
        }

        composeRule.onNodeWithTag("insight_card_sleep-evening-stress").performClick()
        composeRule.onNodeWithTag("insight_detail_timeline").assertExists()
    }
}
