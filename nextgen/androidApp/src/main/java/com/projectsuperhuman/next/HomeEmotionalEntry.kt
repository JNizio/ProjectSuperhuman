package com.projectsuperhuman.next

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.projectsuperhuman.next.core.HealthDomain

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
    openEmotional: () -> Unit,
    openMiniMetric: (HomeMiniMetric) -> Unit,
    topContent: @Composable () -> Unit = {}
) {
    val current by produceState<EmotionalPresentationSnapshot?>(null) {
        val rows = NativeDomainData.forDomain(HealthDomain.EMOTIONAL).latestState()
        value = EmotionalPresentationContract.fromCanonicalValues(rows, rows.latestEmotionalLabel())
    }
    NativeLiveHome(
        openClinical, openBody, openSleep, openBloodPressure, openHydration, openNutrition,
        openExercise, openMindfulness, openEnvironment, openMiniMetric,
        topContent = {
            topContent()
            Box(Modifier.fillMaxWidth().padding(horizontal = 17.dp, bottom = 14.dp)) {
                HomeEmotionalTile(current, openEmotional)
            }
        }
    )
}
