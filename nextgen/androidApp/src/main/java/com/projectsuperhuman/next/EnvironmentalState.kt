package com.projectsuperhuman.next

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal sealed interface EnvironmentalRenderState {
    data object Loading : EnvironmentalRenderState
    data class Ready(val result: EnvironmentalLoadResult) : EnvironmentalRenderState
}

@Composable
internal fun rememberEnvironmentalRenderState(
    source: EnvironmentalPresentationSource,
    refreshKey: Int = 0
): EnvironmentalRenderState {
    var state by remember(source) {
        mutableStateOf<EnvironmentalRenderState>(EnvironmentalRenderState.Loading)
    }
    LaunchedEffect(source, refreshKey) {
        state = EnvironmentalRenderState.Loading
        state = EnvironmentalRenderState.Ready(
            runCatching { normalizeEnvironmentalResult(source.loadCurrent()) }
                .getOrElse { EnvironmentalLoadResult.Error() }
        )
    }
    return state
}
