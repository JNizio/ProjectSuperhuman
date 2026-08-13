package com.projectsuperhuman.next

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Low-conflict overload: adds the Environmental dashboard entry without changing NativeLiveHome's
 * repository/data orchestration while parallel agents are also touching the dashboard.
 */
@Composable
internal fun NativeLiveHome(
    openClinical: () -> Unit,
    openBody: () -> Unit,
    openSleep: () -> Unit,
    openBloodPressure: () -> Unit,
    openHydration: () -> Unit,
    openNutrition: () -> Unit,
    openExercise: () -> Unit,
    openMindfulness: () -> Unit,
    openEnvironment: () -> Unit,
    openMiniMetric: (HomeMiniMetric) -> Unit,
    topContent: @Composable () -> Unit = {}
) {
    NativeLiveHome(
        openClinical = openClinical,
        openBody = openBody,
        openSleep = openSleep,
        openBloodPressure = openBloodPressure,
        openHydration = openHydration,
        openNutrition = openNutrition,
        openExercise = openExercise,
        openMindfulness = openMindfulness,
        openMiniMetric = openMiniMetric,
        topContent = {
            topContent()
            Box(Modifier.fillMaxWidth().padding(horizontal = 17.dp, bottom = 14.dp)) {
                HomeEnvironmentalTile(onClick = openEnvironment)
            }
        }
    )
}
