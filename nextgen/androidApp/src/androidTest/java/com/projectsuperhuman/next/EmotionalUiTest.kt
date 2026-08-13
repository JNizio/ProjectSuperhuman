package com.projectsuperhuman.next

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class EmotionalUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyStateExposesSixAxisPayloadThroughPresentationBoundary() {
        var submitted: Map<String, Int>? = null

        composeRule.setContent {
            MaterialTheme {
                EmotionalModuleScreen(
                    current = null,
                    onBack = {},
                    onRecord = { submitted = it }
                )
            }
        }

        composeRule.onNodeWithTag("emotional_empty_state").assertExists()
        EmotionalPresentationContract.axisIds.forEach { id ->
            composeRule.onNodeWithTag("emotional_scale_$id").assertExists()
        }

        composeRule
            .onNodeWithTag("emotional_scale_${EmotionalPresentationContract.HAPPY_SAD}")
            .performSemanticsAction(SemanticsActions.SetProgress) { action -> action(-70f) }

        composeRule.onNodeWithTag("emotional_submit").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertNotNull(submitted)
            assertEquals(EmotionalPresentationContract.axisIds.toSet(), submitted!!.keys)
            assertEquals(-70, submitted!![EmotionalPresentationContract.HAPPY_SAD])
            assertEquals(
                true,
                submitted!!.values.all {
                    it in EmotionalPresentationContract.MIN_VALUE..EmotionalPresentationContract.MAX_VALUE &&
                        it % EmotionalPresentationContract.STEP == 0
                }
            )
        }
    }

    @Test
    fun currentStateRendersWithoutPersistenceDependency() {
        composeRule.setContent {
            MaterialTheme {
                EmotionalModuleScreen(
                    current = EmotionalPresentationSnapshot(
                        axisValues = mapOf(
                            EmotionalPresentationContract.CALM_ANXIOUS to -55,
                            EmotionalPresentationContract.FOCUSED_DISTRACTED to -40
                        ),
                        recordedAtLabel = "08:42"
                    ),
                    onBack = {},
                    onRecord = {}
                )
            }
        }

        composeRule.onNodeWithTag("emotional_current_state").assertExists()
    }
}
